"""One-shot migration: orphan word:* locks for letter/syllable lemmas → phoneme:*.

Run after Option B (profile_for_item) lands. Preserves seed/speaker/textOverride;
when index.json already commits word_* audio, that production WAV wins on disk.
"""

from __future__ import annotations

import hashlib
import json
import shutil
from dataclasses import dataclass, field
from pathlib import Path

import soundfile as sf

from .extract import extract_items
from .export import asset_name
from .paths import Paths
from .plan import build_clips, orphan_locks, resolve_seed, status_of
from .store import Lock, Locks, Profiles, RenderState, read_json


def _load_context(paths: Paths):
    blanks: list[str] = []
    extra = read_json(paths.extra_strings)
    items = extract_items(paths.content_dir, extra_strings=extra, blanks=blanks)
    profiles = Profiles.load(paths.profiles)
    locks = Locks.load(paths.locks)
    clips = build_clips(items, profiles, locks)
    return items, profiles, locks, clips


@dataclass
class MigrateReport:
    locks_moved: list[tuple[str, str]] = field(default_factory=list)
    locks_dropped: list[str] = field(default_factory=list)
    locks_replaced: list[tuple[str, str]] = field(default_factory=list)
    audio_copied: list[tuple[str, str]] = field(default_factory=list)
    candidate_dirs_merged: list[tuple[str, str]] = field(default_factory=list)
    render_state_renamed: list[tuple[str, str]] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {
            "locksMoved": self.locks_moved,
            "locksDropped": self.locks_dropped,
            "locksReplaced": self.locks_replaced,
            "audioCopied": self.audio_copied,
            "candidateDirsMerged": self.candidate_dirs_merged,
            "renderStateRenamed": self.render_state_renamed,
        }


def _index_by_file(index_path: Path) -> dict[str, dict]:
    if not index_path.exists():
        return {}
    try:
        payload = json.loads(index_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}
    clips = payload.get("clips") if isinstance(payload, dict) else None
    if not isinstance(clips, dict):
        return {}
    by_file: dict[str, dict] = {}
    for _text, entry in clips.items():
        if isinstance(entry, dict) and isinstance(entry.get("file"), str):
            by_file[entry["file"]] = entry
    return by_file


def _merge_candidate_dir(src: Path, dest: Path, report: MigrateReport,
                         word_key: str, phoneme_key: str) -> None:
    if not src.is_dir():
        return
    dest.mkdir(parents=True, exist_ok=True)
    for path in src.iterdir():
        target = dest / path.name
        if target.exists():
            continue
        shutil.move(str(path), str(target))
    if not any(src.iterdir()):
        src.rmdir()
    report.candidate_dirs_merged.append((word_key, phoneme_key))


def _copy_production_audio(word_key: str, phoneme_key: str, paths: Paths,
                           index_by_file: dict[str, dict],
                           report: MigrateReport) -> None:
    word_wav = paths.audio / f"{word_key}.wav"
    phoneme_wav = paths.audio / f"{phoneme_key}.wav"
    word_asset = asset_name(word_key)
    phoneme_asset = asset_name(phoneme_key)

    index_prefers_word = word_asset in index_by_file
    index_prefers_phoneme = phoneme_asset in index_by_file

    if index_prefers_word and word_wav.exists():
        shutil.copy2(word_wav, phoneme_wav)
        report.audio_copied.append((word_key, phoneme_key))
        return
    if index_prefers_phoneme and phoneme_wav.exists():
        return
    if phoneme_wav.exists():
        return
    if word_wav.exists():
        shutil.copy2(word_wav, phoneme_wav)
        report.audio_copied.append((word_key, phoneme_key))


def migrate_word_locks(paths: Paths, *, dry_run: bool = False) -> MigrateReport:
    _items, _profiles, ctx_locks, clips = _load_context(paths)
    orphans = [k for k in orphan_locks(ctx_locks, clips) if k.startswith("word:")]
    index_by_file = _index_by_file(paths.app_audio_dir / "index.json")
    report = MigrateReport()
    locks = Locks({k: v for k, v in ctx_locks.locks.items()}, source=paths.locks)
    state = RenderState.load(paths.render_state)

    for word_key in sorted(orphans):
        suffix = word_key.split(":", 1)[1]
        phoneme_key = f"phoneme:{suffix}"
        word_lock = locks.get(word_key)
        phoneme_lock = locks.get(phoneme_key)
        word_asset = asset_name(word_key)
        index_commits_word = word_asset in index_by_file

        if phoneme_lock is not None:
            if index_commits_word and word_lock is not None:
                locks.set(phoneme_key, word_lock)
                locks.remove(word_key)
                report.locks_replaced.append((word_key, phoneme_key))
            else:
                locks.remove(word_key)
                report.locks_dropped.append(word_key)
        elif word_lock is not None:
            locks.set(phoneme_key, word_lock)
            locks.remove(word_key)
            report.locks_moved.append((word_key, phoneme_key))

        if word_key in state.failures:
            msg = state.failures.pop(word_key)
            state.failures.setdefault(phoneme_key, msg)
            report.render_state_renamed.append((word_key, phoneme_key))

        if dry_run:
            continue

        _copy_production_audio(word_key, phoneme_key, paths, index_by_file, report)
        _merge_candidate_dir(
            paths.candidates / word_key,
            paths.candidates / phoneme_key,
            report, word_key, phoneme_key,
        )

    # Orphan candidate dirs for letter clips that never had a word lock (e.g. "I").
    if not dry_run:
        live_phoneme = {c.key for c in clips if c.key.startswith("phoneme:")}
        for word_dir in sorted(paths.candidates.glob("word:*")):
            suffix = word_dir.name.split(":", 1)[1]
            phoneme_key = f"phoneme:{suffix}"
            if phoneme_key in live_phoneme and word_dir.is_dir():
                _merge_candidate_dir(
                    word_dir, paths.candidates / phoneme_key,
                    report, word_dir.name, phoneme_key,
                )

    if not dry_run:
        locks.save(paths.locks)
        state.save(paths.render_state)

    return report


def _production_wav_hash(path: Path) -> str:
    data, _sr = sf.read(path, dtype="float32")
    return hashlib.sha256(data.tobytes()).hexdigest()


def _production_seed(paths: Paths, clip, profiles: Profiles,
                     locks: Locks) -> int | None:
    """Seed for a clip whose production WAV already exists under out/audio/."""
    production = paths.audio / f"{clip.key}.wav"
    if not production.exists():
        return None
    digest = _production_wav_hash(production)
    cand_dir = paths.candidates / clip.key
    if cand_dir.is_dir():
        for wav in cand_dir.glob("*.wav"):
            if _production_wav_hash(wav) == digest:
                return int(wav.stem)
    return resolve_seed(clip.key, clip.profile, profiles, locks)


@dataclass
class WireReport:
    locked: list[tuple[str, int]] = field(default_factory=list)
    skipped: list[tuple[str, str]] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {"locked": self.locked, "skipped": self.skipped}


def wire_production_locks(paths: Paths, *, dry_run: bool = False,
                          skip_profiles: frozenset[str] = frozenset({"miss"}),
                          ) -> WireReport:
    """Lock rendered clips that have production WAV but no lock yet.

    Batch renders and the CLI ``render`` command can write ``out/audio/*.wav``
    without going through the Web-UI promote step. Those clips show as *fertig*
    locally but never export until a lock exists.
    """
    _items, profiles, locks, clips = _load_context(paths)
    report = WireReport()

    for clip in clips:
        if clip.locked:
            continue
        if clip.profile in skip_profiles:
            continue
        if status_of(clip, paths.audio) != "rendered":
            report.skipped.append((clip.key, "keine Produktions-WAV"))
            continue
        seed = _production_seed(paths, clip, profiles, locks)
        if seed is None:
            report.skipped.append((clip.key, "Seed nicht ermittelbar"))
            continue
        report.locked.append((clip.key, seed))
        if dry_run:
            continue
        locks.set(clip.key, Lock(
            seed=seed,
            source_text=clip.source_text,
        ))

    if not dry_run and report.locked:
        locks.save(paths.locks)

    return report
