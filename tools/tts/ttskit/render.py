"""Batch rendering and candidate sampling.

The engine is injected so the whole batch logic is testable with a fake —
loading 4 GB of weights to check a loop would be absurd.
"""

from __future__ import annotations

import fnmatch
import json
import secrets
from dataclasses import dataclass, field, replace
from pathlib import Path
from typing import Callable, Iterable, Protocol

import numpy as np

from .audio import postprocess, write_wav
from .models import Clip
from .paths import Paths
from .plan import fingerprint, status_of
from .store import Profile, Profiles, RenderState


class SupportsGenerate(Protocol):
    def generate(self, text: str, profile: Profile, seed: int) -> tuple[np.ndarray, int]: ...


@dataclass
class Progress:
    index: int
    total: int
    clip_key: str
    status: str
    message: str = ""


@dataclass
class RenderReport:
    rendered: int = 0
    skipped: int = 0
    failed: list[tuple[str, str]] = field(default_factory=list)


def _select(clips: Iterable[Clip], only: str | None, profile: str | None) -> list[Clip]:
    out = list(clips)
    if profile:
        out = [c for c in out if c.profile == profile]
    if only:
        # fnmatchcase, not fnmatch: clip keys and item ids are case-sensitive
        # identifiers, and fnmatch would normalise them on case-insensitive
        # filesystems like the default macOS one.
        out = [c for c in out if fnmatch.fnmatchcase(c.key, only)
               or any(fnmatch.fnmatchcase(i, only) for i in c.item_ids)]
    return out


def render_clips(
    clips: Iterable[Clip],
    profiles: Profiles,
    engine: SupportsGenerate | None,
    state: RenderState,
    paths: Paths,
    *,
    force: bool = False,
    only: str | None = None,
    profile: str | None = None,
    dry_run: bool = False,
    progress: Callable[[Progress], None] | None = None,
    cancel: Callable[[], bool] | None = None,
) -> RenderReport:
    selected = _select(clips, only, profile)
    report = RenderReport()

    todo: list[tuple[Clip, Profile, str]] = []
    for clip in selected:
        prof = profiles.profiles[clip.profile]
        stamp = fingerprint(clip, prof)
        # `status_of` owns the staleness predicate — re-deriving it here once
        # made `status` and `render` two expressions for one truth.
        if status_of(clip, prof, state, paths.audio) == "rendered" and not force:
            report.skipped += 1
            continue
        todo.append((clip, prof, stamp))

    if dry_run:
        report.rendered = len(todo)
        return report

    # `engine` is legitimately None for a dry run, which returned above. Assert
    # rather than trust the ordering: a future reordering must fail here and not
    # as an AttributeError deep inside the loop.
    assert engine is not None, "render_clips needs an engine unless dry_run=True"

    total = len(todo)
    for index, (clip, prof, stamp) in enumerate(todo, start=1):
        if cancel is not None and cancel():
            break
        try:
            wav, sample_rate = engine.generate(clip.text, prof, clip.seed)
            wav = postprocess(wav, sample_rate, trim=prof.trim, normalize=prof.normalize)
            write_wav(paths.audio / f"{clip.key}.wav", wav, sample_rate)
            state.entries[clip.key] = stamp
            state.failures.pop(clip.key, None)
            # Written per clip, not at the end: an aborted half-hour run
            # must not throw away the work it already did.
            state.save(paths.render_state)
            report.rendered += 1
            status = "ok"
            message = ""
        except Exception as exc:  # noqa: BLE001 - reported, batch continues
            message = f"{type(exc).__name__}: {exc}"
            report.failed.append((clip.key, message))
            # Persisted too, so `tts status` can still name the failure after
            # the process is gone — an in-memory report helps nobody tomorrow.
            state.failures[clip.key] = message
            state.save(paths.render_state)
            status = "failed"
        if progress is not None:
            progress(Progress(index=index, total=total, clip_key=clip.key,
                              status=status, message=message))
    return report


def random_seeds(n: int, exclude: set[int] | None = None) -> list[int]:
    blocked = set(exclude or ())
    out: list[int] = []
    while len(out) < n:
        candidate = secrets.randbelow(2 ** 31)
        if candidate in blocked:
            continue
        blocked.add(candidate)
        out.append(candidate)
    return out


def sample_candidates(
    clip: Clip,
    profile: Profile,
    engine: SupportsGenerate,
    paths: Paths,
    seeds: list[int],
    progress: Callable[[Progress], None] | None = None,
    cancel: Callable[[], bool] | None = None,
) -> list[int]:
    written: list[int] = []
    for index, seed in enumerate(seeds, start=1):
        if cancel is not None and cancel():
            break
        try:
            wav, sample_rate = engine.generate(clip.text, profile, seed)
            wav = postprocess(wav, sample_rate, trim=profile.trim,
                              normalize=profile.normalize)
            write_wav(paths.candidates / clip.key / f"{seed}.wav", wav, sample_rate)
            meta_path = paths.candidates / clip.key / f"{seed}.json"
            meta_path.write_text(json.dumps(
                {"fingerprint": fingerprint(replace(clip, seed=seed), profile)},
            ) + "\n", encoding="utf-8")
            written.append(seed)
            status, message = "ok", ""
        except Exception as exc:  # noqa: BLE001
            status, message = "failed", f"{type(exc).__name__}: {exc}"
        if progress is not None:
            progress(Progress(index=index, total=len(seeds), clip_key=clip.key,
                              status=status, message=message))
    return written


def candidate_seeds(paths: Paths, clip_key: str) -> list[int]:
    directory = Path(paths.candidates) / clip_key
    if not directory.exists():
        return []
    return sorted(int(p.stem) for p in directory.glob("*.wav") if p.stem.isdigit())


def candidate_fingerprint(paths: Paths, clip_key: str, seed: int) -> str | None:
    """Fingerprint, unter dem ein Kandidat erzeugt wurde — None, wenn unbekannt.

    Kandidaten aus der Zeit vor den Sidecars haben keine Metadatei; eine
    kaputte Datei behandeln wir genauso, statt die ganze State-Antwort zu
    reißen: 'unbekannt' ist hier eine legitime Antwort.
    """
    path = Path(paths.candidates) / clip_key / f"{seed}.json"
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    value = raw.get("fingerprint") if isinstance(raw, dict) else None
    return value if isinstance(value, str) else None


def candidate_infos(paths: Paths, clip: Clip, profile: Profile) -> list[dict]:
    """Kandidaten-Seeds plus Frische: entspricht der Sidecar-Fingerprint noch
    den aktuellen Einstellungen? None = Alt-Kandidat ohne Sidecar."""
    infos = []
    for seed in candidate_seeds(paths, clip.key):
        recorded = candidate_fingerprint(paths, clip.key, seed)
        current = fingerprint(replace(clip, seed=seed), profile)
        infos.append({"seed": seed,
                      "fresh": None if recorded is None else recorded == current})
    return infos
