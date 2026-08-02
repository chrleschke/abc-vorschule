"""Batch rendering and candidate sampling.

The engine is injected so the whole batch logic is testable with a fake —
loading 4 GB of weights to check a loop would be absurd.
"""

from __future__ import annotations

import fnmatch
import json
import secrets
from dataclasses import dataclass, field, replace
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Iterable, Protocol

import numpy as np

from .audio import postprocess, write_wav
from .models import Clip
from .paths import Paths
from .plan import effective_profile, fingerprint, status_of
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

    todo: list[tuple[Clip, Profile]] = []
    for clip in selected:
        prof = profiles.profiles[clip.profile]
        # `status_of` owns the "already rendered" predicate — re-deriving it
        # here once made `status` and `render` two expressions for one truth.
        if status_of(clip, paths.audio) == "rendered" and not force:
            report.skipped += 1
            continue
        todo.append((clip, prof))

    if dry_run:
        report.rendered = len(todo)
        return report

    # `engine` is legitimately None for a dry run, which returned above. Assert
    # rather than trust the ordering: a future reordering must fail here and not
    # as an AttributeError deep inside the loop.
    assert engine is not None, "render_clips needs an engine unless dry_run=True"

    total = len(todo)
    for index, (clip, prof) in enumerate(todo, start=1):
        if cancel is not None and cancel():
            break
        try:
            wav, sample_rate = engine.generate(
                clip.text, effective_profile(clip, prof), clip.seed)
            wav = postprocess(wav, sample_rate, trim=prof.trim, normalize=prof.normalize)
            write_wav(paths.audio / f"{clip.key}.wav", wav, sample_rate)
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


def render_batch_candidates(
    clips: Iterable[Clip],
    profiles: Profiles,
    engine: SupportsGenerate | None,
    state: RenderState,
    paths: Paths,
    *,
    count: int,
    force: bool = False,
    only: str | None = None,
    profile: str | None = None,
    dry_run: bool = False,
    progress: Callable[[Progress], None] | None = None,
    cancel: Callable[[], bool] | None = None,
) -> RenderReport:
    """Batch-Lauf im Web-Interface: erzeugt pro Clip `count` Kandidaten statt
    direkt eine Produktions-Datei zu schreiben.

    Anders als `render_clips` (der finale, inkrementelle CLI-Lauf, der
    weiterhin unverändert direkt in die Produktions-Datei schreibt) bleibt
    Produktion hier immer ein bewusster Schritt in der Kandidaten-Liste: ein
    unbestätigter Treffer würde sonst beim nächsten Profil- oder Pool-Wechsel
    stillschweigend durch einen anderen Seed ersetzt, ohne dass ihn je jemand
    gehört hat.
    """
    selected = _select(clips, only, profile)
    report = RenderReport()

    todo: list[Clip] = []
    for clip in selected:
        if status_of(clip, paths.audio) == "rendered" and not force:
            report.skipped += 1
            continue
        todo.append(clip)

    if dry_run:
        report.rendered = len(todo)
        return report

    # `engine` is legitimately None for a dry run, which returned above. Assert
    # rather than trust the ordering: a future reordering must fail here and not
    # as an AttributeError deep inside the loop.
    assert engine is not None, "render_batch_candidates needs an engine unless dry_run=True"

    total = len(todo)
    units_done = 0
    total_units = total * count
    for index, clip in enumerate(todo, start=1):
        if cancel is not None and cancel():
            break
        prof = profiles.profiles[clip.profile]
        existing = set(candidate_seeds(paths, clip.key)) | set(prof.seed_pool)
        seeds = random_seeds(count, exclude=existing)

        base = units_done

        def on_candidate(p: Progress, clip: Clip = clip, base: int = base) -> None:
            if progress is not None:
                progress(Progress(index=base + p.index, total=total_units,
                                  clip_key=clip.key, status=p.status, message=p.message))

        written = sample_candidates(clip, prof, engine, paths, seeds,
                                    progress=on_candidate, cancel=cancel)
        units_done += len(seeds)
        if len(written) == len(seeds):
            report.rendered += 1
        else:
            report.failed.append((
                clip.key,
                f"{len(seeds) - len(written)} von {len(seeds)} Kandidaten fehlgeschlagen"))
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
            wav, sample_rate = engine.generate(
                clip.text, effective_profile(clip, profile), seed)
            wav = postprocess(wav, sample_rate, trim=profile.trim,
                              normalize=profile.normalize)
            write_wav(paths.candidates / clip.key / f"{seed}.wav", wav, sample_rate)
            # Das Sidecar hält fest, WOMIT die Probeaufnahme entstand. Ohne
            # Zeitpunkt, Stimme und Text mischen sich in der UI die Batches
            # verschiedener Sessions zu einer unentwirrbaren Liste.
            meta_path = paths.candidates / clip.key / f"{seed}.json"
            meta_path.write_text(json.dumps({
                "fingerprint": fingerprint(replace(clip, seed=seed), profile),
                "createdAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                "speaker": clip.speaker,
                "text": clip.text,
            }, ensure_ascii=False) + "\n", encoding="utf-8")
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


def candidate_meta(paths: Paths, clip_key: str, seed: int) -> dict[str, Any]:
    """Sidecar-Metadaten eines Kandidaten — {} wenn unbekannt.

    Kandidaten aus der Zeit vor den Sidecars haben keine Metadatei; eine
    kaputte Datei behandeln wir genauso, statt die ganze State-Antwort zu
    reißen: 'unbekannt' ist hier eine legitime Antwort.
    """
    path = Path(paths.candidates) / clip_key / f"{seed}.json"
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return raw if isinstance(raw, dict) else {}


def candidate_fingerprint(paths: Paths, clip_key: str, seed: int) -> str | None:
    value = candidate_meta(paths, clip_key, seed).get("fingerprint")
    return value if isinstance(value, str) else None


def update_candidate_meta(paths: Paths, clip_key: str, seed: int,
                          **changes: Any) -> dict[str, Any]:
    """Einzelne Sidecar-Felder setzen (None löscht ein Feld ausdrücklich).

    Der Rest der Metadaten — allen voran der Erzeugungs-Fingerprint — bleibt
    unangetastet: eine Bewertung darf einen Kandidaten nicht "frisch" oder
    "veraltet" machen.
    """
    meta = candidate_meta(paths, clip_key, seed)
    for key, value in changes.items():
        if value is None:
            meta.pop(key, None)
        else:
            meta[key] = value
    path = Path(paths.candidates) / clip_key / f"{seed}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(meta, ensure_ascii=False) + "\n", encoding="utf-8")
    return meta


def candidate_infos(paths: Paths, clip: Clip, profile: Profile) -> list[dict]:
    """Kandidaten mit allem, was die UI zum Auseinanderhalten braucht:
    Frische (fresh: None = Alt-Kandidat ohne Sidecar), Erzeugungszeitpunkt,
    Stimme und Text zur Erzeugungszeit sowie die gespeicherte Bewertung.

    Neueste zuerst — genau deshalb steht der Zeitpunkt im Sidecar. Kandidaten
    ohne Zeitstempel (vor den Metadaten erzeugt) landen am Ende.
    """
    infos = []
    for seed in candidate_seeds(paths, clip.key):
        meta = candidate_meta(paths, clip.key, seed)
        recorded = meta.get("fingerprint")
        recorded = recorded if isinstance(recorded, str) else None
        current = fingerprint(replace(clip, seed=seed), profile)
        infos.append({
            "seed": seed,
            "fresh": None if recorded is None else recorded == current,
            "createdAt": meta.get("createdAt"),
            "speaker": meta.get("speaker"),
            "text": meta.get("text"),
            "good": meta.get("rating") == "good",
        })
    infos.sort(key=lambda info: (info["createdAt"] or "", info["seed"]), reverse=True)
    return infos


def clip_audio_list(paths: Paths, clip: Clip, profile: Profile) -> list[dict]:
    """Kandidaten UND — falls keiner von ihnen der aktuellen Produktion
    entspricht — ein Nachbau-Eintrag für die Produktions-Datei selbst.

    Vor dem Umbau auf Batch-Kandidaten schrieb ein Batch-Lauf direkt in die
    Produktions-Datei, ohne je einen Kandidaten anzulegen; dasselbe gilt für
    den finalen `tts render` auf der Kommandozeile. Ohne diesen Nachbau
    verschwände so eine Aufnahme aus der Web-UI, sobald die eigene
    Anzeige über der Kandidaten-Tabelle wegfällt — dabei ist die Tabelle
    jetzt die einzige Stelle, an der man sie noch anhören und bewusst
    festlegen kann.

    Kein `fresh`-Feld für diesen Eintrag: er IST die aktuelle Produktion,
    ein späteres Profil-Update darf sie nicht nachträglich als veraltet
    zeigen (siehe `plan.status_of`).
    """
    infos = candidate_infos(paths, clip, profile)
    if any(info["seed"] == clip.seed for info in infos):
        return infos
    audio_path = Path(paths.audio) / f"{clip.key}.wav"
    if not audio_path.exists():
        return infos
    created = datetime.fromtimestamp(
        audio_path.stat().st_mtime, timezone.utc).isoformat(timespec="seconds")
    infos.append({
        "seed": clip.seed,
        "createdAt": created,
        "speaker": clip.speaker,
        "text": clip.text,
        "good": False,
        "isProductionOnly": True,
    })
    infos.sort(key=lambda info: (info["createdAt"] or "", info["seed"]), reverse=True)
    return infos
