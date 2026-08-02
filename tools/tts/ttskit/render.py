"""Batch rendering and candidate sampling.

The engine is injected so the whole batch logic is testable with a fake —
loading 4 GB of weights to check a loop would be absurd.
"""

from __future__ import annotations

import fnmatch
import secrets
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Iterable, Protocol

import numpy as np

from .audio import postprocess, write_wav
from .models import Clip
from .paths import Paths
from .plan import fingerprint
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
    dry_run: bool = False


def _select(clips: Iterable[Clip], only: str | None, profile: str | None) -> list[Clip]:
    out = list(clips)
    if profile:
        out = [c for c in out if c.profile == profile]
    if only:
        out = [c for c in out if fnmatch.fnmatch(c.key, only)
               or any(fnmatch.fnmatch(i, only) for i in c.item_ids)]
    return out


def render_clips(
    clips: Iterable[Clip],
    profiles: Profiles,
    engine: SupportsGenerate,
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
    report = RenderReport(dry_run=dry_run)

    todo: list[tuple[Clip, Profile, str]] = []
    for clip in selected:
        prof = profiles.profiles[clip.profile]
        stamp = fingerprint(clip, prof)
        up_to_date = (state.entries.get(clip.key) == stamp
                      and (paths.audio / f"{clip.key}.wav").exists())
        if up_to_date and not force:
            report.skipped += 1
            continue
        todo.append((clip, prof, stamp))

    if dry_run:
        report.rendered = len(todo)
        return report

    total = len(todo)
    for index, (clip, prof, stamp) in enumerate(todo, start=1):
        if cancel is not None and cancel():
            break
        try:
            wav, sample_rate = engine.generate(clip.text, prof, clip.seed)
            wav = postprocess(wav, sample_rate, trim=prof.trim, normalize=prof.normalize)
            write_wav(paths.audio / f"{clip.key}.wav", wav, sample_rate)
            state.entries[clip.key] = stamp
            # Written per clip, not at the end: an aborted 25-minute run
            # must not throw away the work it already did.
            state.save(paths.render_state)
            report.rendered += 1
            status = "ok"
            message = ""
        except Exception as exc:  # noqa: BLE001 - reported, batch continues
            message = f"{type(exc).__name__}: {exc}"
            report.failed.append((clip.key, message))
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
