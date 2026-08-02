"""FastAPI layer over the pipeline.

MPS cannot be used from several threads at once, so every model-touching
request goes through a single worker. Endpoints enqueue and return 202;
progress arrives over SSE.
"""

from __future__ import annotations

import json
import queue
import threading
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable

from fastapi import Body, FastAPI, HTTPException
from fastapi.responses import FileResponse, HTMLResponse, StreamingResponse

from .cli import load_context
from .paths import Paths
from .plan import orphan_locks, status_of
from .render import candidate_seeds, random_seeds, render_clips, sample_candidates
from .store import Lock, Locks, Profiles

STATIC = Path(__file__).resolve().parent / "static"


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
                "seed": clip.seed,
                "locked": clip.locked,
                "itemIds": list(clip.item_ids),
                "fields": list(clip.fields),
                "lessons": list(clip.lessons),
                "status": status_of(clip, profile, ctx.state, paths.audio),
                "candidates": candidate_seeds(paths, clip.key),
            })
        return {
            "engine": {"loaded": bool(getattr(engine, "loaded", False)),
                       "error": getattr(engine, "load_error", None),
                       "device": getattr(engine, "device", None)},
            "profiles": {n: p.to_dict() for n, p in ctx.profiles.profiles.items()},
            "poolSalt": ctx.profiles.pool_salt,
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
            profile.instruct = body["instruct"]
        if "sampling" in body:
            profile.sampling.update(body["sampling"])
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
        count = int(body.get("n", 4))
        profile = ctx.profiles.profiles[clip.profile]
        existing = set(candidate_seeds(paths, clip.key)) | set(profile.seed_pool)
        seeds = random_seeds(count, exclude=existing)

        def run(is_cancelled) -> None:
            sample_candidates(clip, profile, engine, paths, seeds,
                              progress=lambda p: jobs.publish({
                                  "type": "candidate", "clipKey": clip.key,
                                  "index": p.index, "total": p.total,
                                  "status": p.status, "message": p.message}))

        jobs.submit(f"candidates:{key}", run)
        return {"ok": "queued"}

    @app.get("/api/clips/{key}/candidates")
    def api_list_candidates(key: str) -> dict[str, list[int]]:
        return {"seeds": candidate_seeds(paths, key)}

    @app.post("/api/clips/{key}/lock")
    def api_lock(key: str, body: dict = Body(...)) -> dict[str, str]:
        _, clip = clip_by_key(key)
        locks = Locks.load(paths.locks)
        locks.set(key, Lock(
            seed=int(body["seed"]),
            profile=body.get("profile"),
            text_override=body.get("textOverride"),
            note=body.get("note"),
            source_text=clip.source_text,
        ))
        locks.save(paths.locks)
        return {"ok": "locked"}

    @app.delete("/api/clips/{key}/lock")
    def api_unlock(key: str) -> dict[str, str]:
        locks = Locks.load(paths.locks)
        locks.remove(key)
        locks.save(paths.locks)
        return {"ok": "unlocked"}

    @app.post("/api/render", status_code=202)
    def api_render(body: dict = Body(default={})) -> dict[str, str]:
        profile = body.get("profile") or None
        force = bool(body.get("force"))

        def run(is_cancelled) -> None:
            ctx = context()
            render_clips(ctx.clips, ctx.profiles, engine, ctx.state, paths,
                         profile=profile, force=force, cancel=is_cancelled,
                         progress=lambda p: jobs.publish({
                             "type": "render", "clipKey": p.clip_key,
                             "index": p.index, "total": p.total,
                             "status": p.status, "message": p.message}))

        jobs.submit(f"render:{profile or 'alle'}", run)
        return {"ok": "queued"}

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
        def stream():
            sub = jobs.subscribe()
            try:
                yield f"data: {json.dumps(jobs.status())}\n\n"
                while True:
                    event = sub.get()
                    yield f"data: {json.dumps(event)}\n\n"
            finally:
                jobs.unsubscribe(sub)

        return StreamingResponse(stream(), media_type="text/event-stream")

    return app
