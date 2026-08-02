"""FastAPI layer over the pipeline.

MPS cannot be used from several threads at once, so every model-touching
request goes through a single worker. Endpoints enqueue and return 202;
progress arrives over SSE.
"""

from __future__ import annotations

import json
import os
import queue
import tempfile
import threading
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Any, Callable

from fastapi import Body, FastAPI, HTTPException
from fastapi.responses import (
    FileResponse, HTMLResponse, JSONResponse, StreamingResponse,
)

from . import voices
from .cli import load_context
from .paths import Paths
from .plan import fingerprint, orphan_locks, status_of
from .render import (
    candidate_fingerprint, candidate_meta, candidate_seeds, clip_audio_list,
    random_seeds, render_batch_candidates, sample_candidates, update_candidate_meta,
)
from .store import BASE_SAMPLING, Lock, Locks, Profiles, RenderState

STATIC = Path(__file__).resolve().parent / "static"

#: Upper bound for one candidate batch — used both for a single clip's
#: "Kandidaten würfeln" and per clip in a Batch-Lauf. Each seed is a full
#: model generation and `cancel` does not drain the queue, so an unbounded
#: `n` would be a self-inflicted denial of service on a single-worker tool.
MAX_CANDIDATES = 16

#: Default für "wie viele Beispiele pro Clip" im Batch-Lauf. Klein gehalten,
#: weil er über mehrere ausgewählte Clips hinweg multipliziert — anders als
#: das Kandidaten-Würfeln, das nur einen einzigen Clip trifft.
DEFAULT_BATCH_COUNT = 2


def _checked_speaker(name: Any) -> str:
    """Eine Stimme, die es im Modell wirklich gibt — sonst 422.

    Ungeprüft landet ein Tippfehler in der git-verwalteten profiles.json bzw.
    locks.json und bricht danach jeden Einstiegspunkt, der `build_clips`
    aufruft: `status`, `render` und ausgerechnet das /api/state, mit dem man
    den Fehler wieder wegklicken müsste.
    """
    if voices.voice(name) is None:
        raise HTTPException(
            status_code=422,
            detail=f"unbekannte Stimme {name!r}. Bekannt: "
                   f"{', '.join(voices.speaker_names())}")
    return name


def _copy_atomic(src: Path, dst: Path) -> None:
    """Wie store._write_json: die App-Seite darf nie eine halbe WAV sehen,
    falls parallel ein Render-Lauf oder ein zweiter Promote schreibt."""
    dst.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(dir=dst.parent, prefix=f".{dst.name}.", suffix=".tmp")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(src.read_bytes())
        os.replace(tmp_name, dst)
    except BaseException:
        try:
            os.unlink(tmp_name)
        except OSError:
            pass
        raise


@dataclass
class JobQueue:
    """One worker, one job at a time."""

    _queue: queue.Queue = field(default_factory=queue.Queue)
    _subscribers: list[queue.Queue] = field(default_factory=list)
    _lock: threading.Lock = field(default_factory=threading.Lock)
    running: str | None = None
    last_progress: dict[str, Any] = field(default_factory=dict)
    _cancel: threading.Event = field(default_factory=threading.Event)
    _worker: threading.Thread | None = None

    def start(self) -> None:
        self._worker = threading.Thread(target=self._run, daemon=True)
        self._worker.start()

    def submit(self, name: str, fn: Callable[[Callable[[], bool]], None]) -> None:
        self._queue.put((name, fn))

    def cancel(self) -> None:
        self._cancel.set()

    def status(self) -> dict[str, Any]:
        return {"running": self.running, "progress": self.last_progress,
                "queued": self._queue.qsize()}

    def publish(self, event: dict[str, Any]) -> None:
        self.last_progress = event
        with self._lock:
            for sub in list(self._subscribers):
                sub.put(event)

    def subscribe(self) -> queue.Queue:
        sub: queue.Queue = queue.Queue()
        with self._lock:
            self._subscribers.append(sub)
        return sub

    def unsubscribe(self, sub: queue.Queue) -> None:
        with self._lock:
            if sub in self._subscribers:
                self._subscribers.remove(sub)

    def _run(self) -> None:
        while True:
            name, fn = self._queue.get()
            self.running = name
            self._cancel.clear()
            self.publish({"type": "job-start", "job": name})
            try:
                fn(self._cancel.is_set)
                self.publish({"type": "job-done", "job": name})
            except Exception as exc:  # noqa: BLE001 - surfaced in the UI
                self.publish({"type": "job-error", "job": name,
                              "message": f"{type(exc).__name__}: {exc}"})
            finally:
                self.running = None
                self._queue.task_done()


def create_app(paths: Paths, engine=None) -> FastAPI:
    app = FastAPI(title="Qwen-TTS Pipeline")
    jobs = JobQueue()
    jobs.start()

    if engine is None:
        from .engine import Engine

        engine = Engine()
        engine.load()

    app.state.paths = paths
    app.state.engine = engine
    app.state.jobs = jobs

    @app.exception_handler(ValueError)
    def on_bad_curated_file(request, exc: ValueError):
        # The curated JSON files are hand-edited. When one of them is
        # inconsistent, the message names the file and the key — show exactly
        # that instead of an opaque 500, so the UI can tell the operator what
        # to fix.
        return JSONResponse(status_code=500, content={"detail": str(exc)})

    def context():
        return load_context(paths)

    def clip_by_key(key: str):
        ctx = context()
        for clip in ctx.clips:
            if clip.key == key:
                return ctx, clip
        raise HTTPException(status_code=404, detail=f"unbekannter Clip {key!r}")

    @app.get("/", response_class=HTMLResponse)
    def index() -> str:
        return (STATIC / "index.html").read_text(encoding="utf-8")

    @app.get("/app.js")
    def app_js() -> FileResponse:
        return FileResponse(STATIC / "app.js", media_type="application/javascript")

    @app.get("/style.css")
    def style_css() -> FileResponse:
        return FileResponse(STATIC / "style.css", media_type="text/css")

    @app.get("/api/state")
    def api_state() -> dict[str, Any]:
        ctx = context()
        clips = []
        for clip in ctx.clips:
            profile = ctx.profiles.profiles[clip.profile]
            clips.append({
                "key": clip.key,
                "profile": clip.profile,
                "text": clip.text,
                "sourceText": clip.source_text,
                "speaker": clip.speaker,
                "seed": clip.seed,
                "locked": clip.locked,
                "itemIds": list(clip.item_ids),
                "fields": list(clip.fields),
                "lessons": list(clip.lessons),
                "status": status_of(clip, profile, ctx.state, paths.audio),
                "candidates": clip_audio_list(paths, clip, profile, ctx.state),
            })
        return {
            "engine": {"loaded": bool(getattr(engine, "loaded", False)),
                       "error": getattr(engine, "load_error", None),
                       "device": getattr(engine, "device", None)},
            "profiles": {n: p.to_dict() for n, p in ctx.profiles.profiles.items()},
            "poolSalt": ctx.profiles.pool_salt,
            "voices": [{"name": v.name, "origin": v.origin, "european": v.european}
                       for v in voices.VOICES],
            "languages": list(voices.LANGUAGES),
            "limits": {"maxCandidates": MAX_CANDIDATES},
            "clips": clips,
            "orphans": [
                {"key": k, "seed": ctx.locks.get(k).seed,
                 "sourceText": ctx.locks.get(k).source_text}
                for k in orphan_locks(ctx.locks, ctx.clips)
            ],
            "jobs": jobs.status(),
        }

    @app.put("/api/profiles/{name}")
    def api_update_profile(name: str, body: dict = Body(...)) -> dict[str, str]:
        profiles = Profiles.load(paths.profiles)
        if name not in profiles.profiles:
            raise HTTPException(status_code=404, detail=f"unbekanntes Profil {name!r}")
        profile = profiles.profiles[name]
        if "instruct" in body:
            # profiles.json is curated and git-tracked; a dict landing in
            # `instruct` would persist and then blow up at generate time.
            if not isinstance(body["instruct"], str):
                raise HTTPException(
                    status_code=422,
                    detail=f"'instruct' muss ein Text sein, nicht "
                           f"{type(body['instruct']).__name__}")
            profile.instruct = body["instruct"]
        if "speaker" in body:
            profile.speaker = _checked_speaker(body["speaker"])
        if "language" in body:
            language = body["language"]
            if language not in voices.LANGUAGES:
                raise HTTPException(
                    status_code=422,
                    detail=f"unbekannte Sprache {language!r}. Bekannt: "
                           f"{', '.join(voices.LANGUAGES)}")
            profile.language = language
        if "sampling" in body:
            sampling = body["sampling"]
            if not isinstance(sampling, dict):
                raise HTTPException(
                    status_code=422,
                    detail=f"'sampling' muss ein Objekt sein, nicht "
                           f"{type(sampling).__name__}")
            # Whitelist: an unknown key would change the fingerprint (every
            # clip of the profile goes stale) and then reach
            # generate_custom_voice(**sampling) as a TypeError on every clip.
            unknown = sorted(set(sampling) - set(BASE_SAMPLING))
            if unknown:
                raise HTTPException(
                    status_code=422,
                    detail=f"unbekannte Sampling-Parameter: {', '.join(unknown)}. "
                           f"Erlaubt: {', '.join(sorted(BASE_SAMPLING))}")
            for param, value in sampling.items():
                if isinstance(value, bool) or not isinstance(value, (int, float)):
                    raise HTTPException(
                        status_code=422,
                        detail=f"Sampling-Parameter {param!r} muss eine Zahl sein, "
                               f"nicht {type(value).__name__}")
            profile.sampling.update(sampling)
        if "trim" in body:
            profile.trim = bool(body["trim"])
        if "normalize" in body:
            profile.normalize = bool(body["normalize"])
        profiles.save(paths.profiles)
        return {"ok": "updated"}

    @app.post("/api/profiles/{name}/pool")
    def api_add_seed(name: str, body: dict = Body(...)) -> dict[str, str]:
        profiles = Profiles.load(paths.profiles)
        if name not in profiles.profiles:
            raise HTTPException(status_code=404, detail=f"unbekanntes Profil {name!r}")
        seed = int(body["seed"])
        pool = profiles.profiles[name].seed_pool
        if seed not in pool:
            pool.append(seed)
            pool.sort()
        profiles.save(paths.profiles)
        return {"ok": "added"}

    @app.delete("/api/profiles/{name}/pool/{seed}")
    def api_remove_seed(name: str, seed: int) -> dict[str, str]:
        profiles = Profiles.load(paths.profiles)
        if name not in profiles.profiles:
            raise HTTPException(status_code=404, detail=f"unbekanntes Profil {name!r}")
        profiles.profiles[name].seed_pool = [
            s for s in profiles.profiles[name].seed_pool if s != seed
        ]
        profiles.save(paths.profiles)
        return {"ok": "removed"}

    @app.post("/api/clips/{key}/candidates", status_code=202)
    def api_candidates(key: str, body: dict = Body(default={})) -> dict[str, str]:
        ctx, clip = clip_by_key(key)
        count = max(1, min(MAX_CANDIDATES, int(body.get("n", 4))))
        profile = ctx.profiles.profiles[clip.profile]
        existing = set(candidate_seeds(paths, clip.key)) | set(profile.seed_pool)
        seeds = random_seeds(count, exclude=existing)

        def run(is_cancelled) -> None:
            written = sample_candidates(
                clip, profile, engine, paths, seeds,
                progress=lambda p: jobs.publish({
                    "type": "candidate", "clipKey": clip.key,
                    "index": p.index, "total": p.total,
                    "status": p.status, "message": p.message}),
                cancel=is_cancelled)
            jobs.publish({"type": "job-summary", "job": f"candidates:{key}",
                          "rendered": len(written), "skipped": 0,
                          "failed": len(seeds) - len(written)})

        jobs.submit(f"candidates:{key}", run)
        return {"ok": "queued"}

    @app.post("/api/clips/{key}/lock")
    def api_lock(key: str, body: dict = Body(...)) -> dict[str, str]:
        ctx, clip = clip_by_key(key)
        locks = Locks.load(paths.locks)
        existing = locks.get(key)

        # Nur benannte Felder werden angefasst; `null` löscht ein Feld
        # ausdrücklich. Die UI bearbeitet Aussprache, Stimme und Profil an drei
        # verschiedenen Stellen — würde jeder dieser Aufrufe den ganzen Lock
        # ersetzen, löschte ein Stimmwechsel stillschweigend die von Hand
        # eingetippte Aussprache. Das gleiche Versprechen gibt schon `promote`.
        def merged(field: str, current):
            return body[field] if field in body else current

        override = merged("profile", existing.profile if existing else None)
        # An unvalidated profile name here used to persist into locks.json and
        # then brick every entry point — `status`, `extract`, `render` and the
        # /api/state the UI needs to fix it again.
        if override is not None and override not in ctx.profiles.profiles:
            raise HTTPException(
                status_code=422,
                detail=f"unbekanntes Profil {override!r}. Gültig: "
                       f"{', '.join(sorted(ctx.profiles.profiles))}")
        speaker = merged("speaker", existing.speaker if existing else None)
        if speaker is not None:
            _checked_speaker(speaker)

        locks.set(key, Lock(
            seed=int(body["seed"]),
            profile=override,
            text_override=merged("textOverride",
                                 existing.text_override if existing else None),
            note=merged("note", existing.note if existing else None),
            source_text=clip.source_text,
            speaker=speaker,
        ))
        locks.save(paths.locks)
        return {"ok": "locked"}

    @app.delete("/api/clips/{key}/lock")
    def api_unlock(key: str) -> dict[str, str]:
        locks = Locks.load(paths.locks)
        locks.remove(key)
        locks.save(paths.locks)
        return {"ok": "unlocked"}

    @app.post("/api/clips/{key}/promote")
    def api_promote(key: str, body: dict = Body(...)) -> dict[str, Any]:
        ctx, clip = clip_by_key(key)
        if "seed" not in body:
            raise HTTPException(status_code=422, detail="'seed' fehlt im Request-Body")
        seed = int(body["seed"])
        source = paths.candidates / key / f"{seed}.wav"
        production = paths.audio / f"{key}.wav"
        # Kein Kandidat unter diesem Seed — es sei denn, es ist genau der Seed,
        # der schon unbestätigt in Produktion liegt (der Nachbau-Eintrag aus
        # clip_audio_list, z. B. von `tts render` auf der Kommandozeile). Dort
        # gibt es nichts zu kopieren, nur festzulegen.
        already_in_place = seed == clip.seed and production.exists()
        if not source.exists() and not already_in_place:
            raise HTTPException(status_code=404,
                                detail=f"kein Kandidat mit Seed {seed} für {key!r}")

        # Lock zuerst: anders als api_lock bleiben vorhandene kuratierte
        # Felder (profile, textOverride, note) erhalten — Promote entscheidet
        # nur über den Seed, nicht über den Rest der Hörarbeit.
        locks = Locks.load(paths.locks)
        existing = locks.get(key)
        locks.set(key, Lock(
            seed=seed,
            profile=existing.profile if existing else None,
            text_override=existing.text_override if existing else None,
            note=existing.note if existing else None,
            source_text=clip.source_text,
            speaker=existing.speaker if existing else None,
        ))
        locks.save(paths.locks)

        if source.exists():
            _copy_atomic(source, production)

        # Nur wenn der Kandidat nachweislich mit den aktuellen Einstellungen
        # erzeugt wurde, gilt der Clip als gerendert. Sonst bleibt er
        # "stale" und der nächste Lauf rendert ihn mit dem gelockten Seed neu.
        profile = ctx.profiles.profiles[clip.profile]
        target = fingerprint(replace(clip, seed=seed), profile)
        if source.exists():
            verified = candidate_fingerprint(paths, key, seed) == target
        else:
            # Der Nachbau-Eintrag hat keinen Sidecar — die Frische steht schon
            # im render-state, aus genau dem Lauf, der die Datei geschrieben hat.
            verified = ctx.state.entries.get(key) == target
        if verified:
            render_state = RenderState.load(paths.render_state)
            render_state.entries[key] = target
            render_state.failures.pop(key, None)
            render_state.save(paths.render_state)
        return {"ok": "promoted", "verified": verified}

    @app.put("/api/clips/{key}/candidates/{seed}/rating")
    def api_rate_candidate(key: str, seed: int, body: dict = Body(...)) -> dict[str, Any]:
        """👍 an einer Probeaufnahme.

        Bewertung und Seed-Pool sind EIN Handgriff: „gut“ heißt, der Seed darf
        künftig auch anderen Clips des Profils automatisch zugeteilt werden.
        Die Bewertung selbst liegt im Sidecar — dateibasiert, damit sie einen
        Server-Neustart überlebt (vorher: nur localStorage im Browser).
        """
        ctx, clip = clip_by_key(key)
        wav = paths.candidates / key / f"{seed}.wav"
        if not wav.exists():
            raise HTTPException(status_code=404,
                                detail=f"kein Kandidat mit Seed {seed} für {key!r}")
        good = bool(body.get("good"))
        update_candidate_meta(paths, key, seed, rating="good" if good else None)

        profiles = Profiles.load(paths.profiles)
        pool = profiles.profiles[clip.profile].seed_pool
        if good and seed not in pool:
            pool.append(seed)
            pool.sort()
        elif not good:
            profiles.profiles[clip.profile].seed_pool = [s for s in pool if s != seed]
        profiles.save(paths.profiles)
        return {"ok": "rated", "good": good}

    @app.delete("/api/clips/{key}/candidates/{seed}")
    def api_delete_candidate(key: str, seed: int) -> dict[str, str]:
        ctx, clip = clip_by_key(key)
        wav = paths.candidates / key / f"{seed}.wav"
        if not wav.exists():
            raise HTTPException(status_code=404,
                                detail=f"kein Kandidat mit Seed {seed} für {key!r}")
        # 👍 hat den Seed in den Pool gelegt — das Löschen der Aufnahme nimmt
        # ihn wieder heraus, sonst verteilt der Pool einen Seed, dessen Klang
        # niemand mehr anhören kann.
        if candidate_meta(paths, key, seed).get("rating") == "good":
            profiles = Profiles.load(paths.profiles)
            profiles.profiles[clip.profile].seed_pool = [
                s for s in profiles.profiles[clip.profile].seed_pool if s != seed
            ]
            profiles.save(paths.profiles)
        wav.unlink()
        (paths.candidates / key / f"{seed}.json").unlink(missing_ok=True)

        production = paths.audio / f"{key}.wav"
        # Der gelöschte Kandidat war die aktuelle Produktion: die Datei, die
        # die App ausliefern würde, darf keinen verworfenen Klang mehr enthalten.
        if clip.seed == seed and production.exists():
            production.unlink()
            render_state = RenderState.load(paths.render_state)
            if render_state.entries.pop(key, None) is not None:
                render_state.save(paths.render_state)

        # Keine Probeaufnahme und keine Produktions-Datei mehr übrig — die
        # Festlegung hat nichts mehr, worauf sie zeigen könnte, und fällt
        # automatisch weg. Ein eigener "Lock entfernen"-Knopf erübrigt sich so.
        if not candidate_seeds(paths, key) and not (paths.audio / f"{key}.wav").exists():
            locks = Locks.load(paths.locks)
            if locks.get(key) is not None:
                locks.remove(key)
                locks.save(paths.locks)

        return {"ok": "deleted"}

    @app.post("/api/render", status_code=202)
    def api_render(body: dict = Body(default={})) -> dict[str, str]:
        profile = body.get("profile") or None
        force = bool(body.get("force"))
        count = max(1, min(MAX_CANDIDATES, int(body.get("n", DEFAULT_BATCH_COUNT))))
        keys = body.get("keys")
        if keys is not None:
            # Batch-Lauf über eine explizite Auswahl. Unbekannte Keys sind ein
            # Fehler und kein stilles Überspringen: die UI hätte sie gar nicht
            # anbieten dürfen, und ein Tippfehler im API-Aufruf soll auffallen.
            if (not isinstance(keys, list)
                    or not all(isinstance(k, str) for k in keys)):
                raise HTTPException(status_code=422,
                                    detail="'keys' muss eine Liste von Clip-Keys sein")
            known = {clip.key for clip in context().clips}
            unknown = sorted(set(keys) - known)
            if unknown:
                raise HTTPException(status_code=422,
                                    detail=f"unbekannte Clips: {', '.join(unknown)}")

        selection = set(keys) if keys is not None else None
        name = (f"render:{len(selection)} ausgewählte" if selection is not None
                else f"render:{profile or 'alle'}")

        def run(is_cancelled) -> None:
            ctx = context()
            clips = (ctx.clips if selection is None
                     else [c for c in ctx.clips if c.key in selection])
            report = render_batch_candidates(
                clips, ctx.profiles, engine, ctx.state, paths, count=count,
                profile=profile, force=force, cancel=is_cancelled,
                progress=lambda p: jobs.publish({
                    "type": "render", "clipKey": p.clip_key,
                    "index": p.index, "total": p.total,
                    "status": p.status, "message": p.message}))
            # render_batch_candidates swallows per-clip failures so the batch
            # continues. Without this summary the job publishes `job-done` and
            # the UI says "fertig" even when every single clip failed.
            jobs.publish({"type": "job-summary", "job": name,
                          "rendered": report.rendered, "skipped": report.skipped,
                          "failed": len(report.failed)})

        jobs.submit(name, run)
        return {"ok": "queued"}

    @app.post("/api/export")
    def api_export() -> dict[str, Any]:
        from .export import export_to_app

        return export_to_app(paths).as_dict()

    @app.post("/api/jobs/cancel")
    def api_cancel() -> dict[str, str]:
        jobs.cancel()
        return {"ok": "cancelling"}

    @app.get("/api/jobs")
    def api_jobs() -> dict[str, Any]:
        return jobs.status()

    @app.get("/audio/{key}.wav")
    def api_audio(key: str) -> FileResponse:
        # Only serve keys that belong to a known clip — no path traversal.
        clip_by_key(key)
        path = paths.audio / f"{key}.wav"
        if not path.exists():
            raise HTTPException(status_code=404, detail="noch nicht gerendert")
        return FileResponse(path, media_type="audio/wav")

    @app.get("/candidates/{key}/{seed}.wav")
    def api_candidate_audio(key: str, seed: int) -> FileResponse:
        clip_by_key(key)
        path = paths.candidates / key / f"{seed}.wav"
        if not path.exists():
            raise HTTPException(status_code=404, detail="kein Kandidat")
        return FileResponse(path, media_type="audio/wav")

    @app.get("/events")
    def api_events() -> StreamingResponse:
        return StreamingResponse(_event_stream(jobs), media_type="text/event-stream")

    return app


def _event_stream(jobs: JobQueue):
    """SSE body for `/events` — a module-level generator so it is directly
    testable without going through the HTTP transport (see the note below on
    why a real client can't exercise this end to end).

    A timeout on `sub.get()` is essential: this generator runs in one of
    anyio's shared threadpool slots. Without it, a client that disconnects
    while idle leaves `sub.get()` blocked forever — the thread never returns,
    `finally` never runs, and the slot is gone for good. The timeout wakes the
    loop periodically so a dead connection's failed `yield`/send is noticed
    and the generator can exit and clean up.
    """
    sub = jobs.subscribe()
    try:
        yield f"data: {json.dumps(jobs.status())}\n\n"
        while True:
            try:
                event = sub.get(timeout=15)
            except queue.Empty:
                yield ": keep-alive\n\n"
                continue
            yield f"data: {json.dumps(event)}\n\n"
    finally:
        jobs.unsubscribe(sub)
