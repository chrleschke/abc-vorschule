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
from contextlib import asynccontextmanager
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
from .plan import fingerprint, orphan_locks, status_of, top_seeds
from .render import (
    candidate_fingerprint, candidate_meta, candidate_seeds, clear_production,
    clip_audio_list, deletable_candidate_seeds, delete_candidate_wav,
    render_batch_candidates, sample_candidates, seeds_for_candidates,
    update_candidate_meta,
)
from .store import (
    SAMPLING_PARAMS, SAMPLING_SPEC, SECONDS_PER_TOKEN,
    Lock, Locks, Profiles, parse_seed,
)

STATIC = Path(__file__).resolve().parent / "static"

#: Upper bound for one candidate batch — used both for a single clip's
#: "Generate" and per clip in a Batch-Lauf. Each seed is a full
#: model generation and `cancel` does not drain the queue, so an unbounded
#: `n` would be a self-inflicted denial of service on a single-worker tool.
MAX_CANDIDATES = 16

#: Default für "wie viele Beispiele pro Clip" im Batch-Lauf. Klein gehalten,
#: weil er über mehrere ausgewählte Clips hinweg multipliziert — anders als
#: das Kandidaten-Würfeln, das nur einen einzigen Clip trifft.
DEFAULT_BATCH_COUNT = 2

#: Wie oft `/events` auf neue Job-Events wartet. Kurz gehalten, damit Ctrl-C
#: nicht am offenen Browser-Tab hängen bleibt — der alte 15-s-Timeout ließ
#: uvicorn erst nach dem nächsten Keep-Alive-Zyklus sauber beenden.
SSE_POLL_SECONDS = 1.0


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
    _shutdown: threading.Event = field(default_factory=threading.Event)
    _worker: threading.Thread | None = None

    def start(self) -> None:
        self._worker = threading.Thread(target=self._run, daemon=True)
        self._worker.start()

    def submit(self, name: str, fn: Callable[[Callable[[], bool]], None]) -> None:
        self._queue.put((name, fn))

    def cancel(self) -> None:
        self._cancel.set()

    def shutdown(self) -> None:
        """Stop accepting work and wake blocked SSE subscribers."""
        self._shutdown.set()
        self.cancel()
        with self._lock:
            for sub in list(self._subscribers):
                sub.put({"type": "shutdown"})

    @property
    def shutting_down(self) -> bool:
        return self._shutdown.is_set()

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
        while not self._shutdown.is_set():
            try:
                name, fn = self._queue.get(timeout=SSE_POLL_SECONDS)
            except queue.Empty:
                continue
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


def create_app(paths: Paths, engine=None, *, load_engine: bool = True) -> FastAPI:
    jobs = JobQueue()
    jobs.start()

    if engine is None:
        from .engine import Engine

        engine = Engine()
        if load_engine:
            engine.load()

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        yield
        jobs.shutdown()

    app = FastAPI(title="Qwen-TTS Pipeline", lifespan=lifespan)
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
            lock = ctx.locks.get(clip.key)
            clips.append({
                "key": clip.key,
                "profile": clip.profile,
                "text": clip.text,
                "sourceText": clip.source_text,
                "speaker": clip.speaker,
                "seed": clip.seed,
                "generateSeed": lock.generate_seed if lock else None,
                "locked": clip.locked,
                "itemIds": list(clip.item_ids),
                "fields": list(clip.fields),
                "lessons": list(clip.lessons),
                "status": status_of(clip, paths.audio),
                "candidates": clip_audio_list(paths, clip, profile),
            })
        return {
            "engine": {"loaded": bool(getattr(engine, "loaded", False)),
                       "error": getattr(engine, "load_error", None),
                       "device": getattr(engine, "device", None)},
            "profiles": {n: p.to_dict() for n, p in ctx.profiles.profiles.items()},
            # Abgeleitet aus den Locks, nicht Teil des Profils — bewusst neben
            # `profiles` und nicht darin, denn `to_dict()` schreibt auch
            # profiles.json, und dort hätte ein errechneter Wert nichts zu suchen.
            "topSeeds": {n: top_seeds(ctx.locks, n) for n in ctx.profiles.profiles},
            "poolSalt": ctx.profiles.pool_salt,
            "voices": [{"name": v.name, "origin": v.origin, "european": v.european}
                       for v in voices.VOICES],
            "languages": list(voices.LANGUAGES),
            "limits": {"maxCandidates": MAX_CANDIDATES},
            # Das ⚙️-Panel rendert aus dieser Deklaration statt aus den
            # Schlüsseln, die ein Profil zufällig schon besitzt.
            "samplingSpec": [p.to_dict() for p in SAMPLING_SPEC],
            "secondsPerToken": SECONDS_PER_TOKEN,
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
            # Whitelist: an unknown key would reach
            # generate_custom_voice(**sampling) as a TypeError on every future
            # render of this profile.
            unknown = sorted(set(sampling) - set(SAMPLING_PARAMS))
            if unknown:
                raise HTTPException(
                    status_code=422,
                    detail=f"unbekannte Sampling-Parameter: {', '.join(unknown)}. "
                           f"Erlaubt: {', '.join(sorted(SAMPLING_PARAMS))}")
            # Erst alles prüfen, dann alles anwenden. Das ⚙️-Panel schickt
            # sämtliche Parameter in einem Save; würde mitten in der Schleife
            # geschrieben, hinterließe ein einziger schlechter Wert ein halb
            # aktualisiertes Profil im Speicher.
            for param, value in sampling.items():
                spec = SAMPLING_PARAMS[param]
                if value is None:
                    if not spec.nullable:
                        raise HTTPException(
                            status_code=422,
                            detail=f"Sampling-Parameter {param!r} darf nicht leer "
                                   f"sein — erlaubt ist {spec.minimum} bis "
                                   f"{spec.maximum}")
                    continue
                if isinstance(value, bool) or not isinstance(value, (int, float)):
                    raise HTTPException(
                        status_code=422,
                        detail=f"Sampling-Parameter {param!r} muss eine Zahl sein, "
                               f"nicht {type(value).__name__}")
                if spec.integer and float(value) != int(value):
                    raise HTTPException(
                        status_code=422,
                        detail=f"Sampling-Parameter {param!r} muss eine Ganzzahl "
                               f"sein, nicht {value}")
                # Ungeprüft landete ein top_p von 3 oder eine temperature von
                # 50 in der git-verwalteten profiles.json und erzeugte danach
                # still unbrauchbare Audios.
                if not spec.minimum <= value <= spec.maximum:
                    raise HTTPException(
                        status_code=422,
                        detail=f"Sampling-Parameter {param!r} liegt mit {value} "
                               f"außerhalb des erlaubten Bereichs "
                               f"{spec.minimum} bis {spec.maximum}")
            for param, value in sampling.items():
                if value is None:
                    # Fehlender Schlüssel heißt „Modell-Default", bei
                    # max_new_tokens also unbegrenzt.
                    profile.sampling.pop(param, None)
                elif SAMPLING_PARAMS[param].integer:
                    profile.sampling[param] = int(value)
                else:
                    profile.sampling[param] = value
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
        try:
            seed = parse_seed(body["seed"])
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
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
        profile = ctx.profiles.profiles[clip.profile]
        fixed = body.get("fixedSeed")
        if fixed is None:
            lock = ctx.locks.get(key)
            if lock and lock.generate_seed is not None:
                fixed = lock.generate_seed
        if fixed is not None:
            try:
                seeds = [parse_seed(fixed)]
            except ValueError as exc:
                raise HTTPException(status_code=422, detail=str(exc)) from exc
        else:
            count = max(1, min(MAX_CANDIDATES, int(body.get("n", 4))))
            seeds = seeds_for_candidates(
                count=count, clip=clip, profile=profile, paths=paths,
                locks=ctx.locks,
                use_top_seeds=bool(body.get("useTopSeeds")),
                use_known_seeds=bool(body.get("useKnownSeeds")),
            )

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

        if "seed" not in body:
            raise HTTPException(status_code=422, detail="'seed' fehlt im Request-Body")
        try:
            lock_seed = int(body["seed"])
        except (TypeError, ValueError) as exc:
            raise HTTPException(
                status_code=422,
                detail=f"'seed' muss eine Ganzzahl sein, nicht {type(body['seed']).__name__}",
            ) from exc

        generate_seed = merged("generateSeed",
                               existing.generate_seed if existing else None)
        if "generateSeed" in body and body["generateSeed"] is not None:
            try:
                generate_seed = parse_seed(body["generateSeed"])
            except ValueError as exc:
                raise HTTPException(status_code=422, detail=str(exc)) from exc
        elif "generateSeed" in body and body["generateSeed"] is None:
            generate_seed = None

        locks.set(key, Lock(
            seed=lock_seed,
            profile=override,
            text_override=merged("textOverride",
                                 existing.text_override if existing else None),
            note=merged("note", existing.note if existing else None),
            source_text=clip.source_text,
            speaker=speaker,
            generate_seed=generate_seed,
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
            generate_seed=existing.generate_seed if existing else None,
        ))
        locks.save(paths.locks)

        if source.exists():
            _copy_atomic(source, production)

        # `verified` ist rein informativ: entstand die übernommene Aufnahme
        # nachweislich mit den aktuellen Profil-Einstellungen? Der Clip gilt
        # so oder so ab jetzt als "rendered" — bestätigter Content wird nie
        # durch ein späteres Profil-Update invalidiert (siehe plan.status_of).
        if source.exists():
            profile = ctx.profiles.profiles[clip.profile]
            target = fingerprint(replace(clip, seed=seed), profile)
            verified = candidate_fingerprint(paths, key, seed) == target
        else:
            # Nachbau-Eintrag ohne Sidecar — es gibt nichts, wogegen sich das
            # verifizieren ließe.
            verified = False
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
        production = paths.audio / f"{key}.wav"
        if seed == clip.seed and production.exists():
            raise HTTPException(
                status_code=409,
                detail=f"Seed {seed} ist die Produktion — „Keine Produktion“ nutzen")
        try:
            delete_candidate_wav(paths, clip, seed)
        except FileNotFoundError:
            raise HTTPException(status_code=404,
                                detail=f"kein Kandidat mit Seed {seed} für {key!r}")
        return {"ok": "deleted"}

    @app.delete("/api/clips/{key}/candidates")
    def api_delete_deletable_candidates(key: str) -> dict[str, Any]:
        """Alle löschbaren Probeaufnahmen — ohne 👍 und ohne Produktion."""
        ctx, clip = clip_by_key(key)
        deletable, skipped = deletable_candidate_seeds(paths, clip)
        deleted: list[int] = []
        for seed in deletable:
            delete_candidate_wav(paths, clip, seed)
            deleted.append(seed)
        return {"ok": "deleted", "deleted": len(deleted), "skipped": skipped,
                "seeds": deleted}

    @app.post("/api/clips/{key}/clear-production")
    def api_clear_production(key: str) -> dict[str, str]:
        """Produktion aufheben — Kandidaten und 👍 bleiben, Lock nur wenn kuratiert."""
        ctx, clip = clip_by_key(key)
        if not clear_production(paths, clip):
            raise HTTPException(status_code=409,
                                detail="keine Produktion zum Aufheben")
        return {"ok": "cleared"}

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
                clips, ctx.profiles, engine, ctx.state, paths, ctx.locks,
                count=count,
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
        while not jobs.shutting_down:
            try:
                event = sub.get(timeout=SSE_POLL_SECONDS)
            except queue.Empty:
                if jobs.shutting_down:
                    break
                yield ": keep-alive\n\n"
                continue
            if event.get("type") == "shutdown":
                break
            yield f"data: {json.dumps(event)}\n\n"
    finally:
        jobs.unsubscribe(sub)
