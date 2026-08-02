"""Turn items plus stored decisions into concrete render units."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

from .extract import FIELD_TO_PROFILE
from .models import Clip, Item
from .store import Locks, Profile, Profiles, RenderState


def clip_key(profile: str, source_text: str) -> str:
    """Stable identity of a render unit.

    Always built from the *default* profile and the *original* text. A lock may
    override which profile is used for synthesis, but it must not move the key —
    the lock is looked up by this very key.
    """
    digest = hashlib.sha256(source_text.encode("utf-8")).hexdigest()[:12]
    return f"{profile}:{digest}"


def resolve_seed(key: str, profile_name: str, profiles: Profiles, locks: Locks) -> int:
    lock = locks.get(key)
    if lock is not None:
        return lock.seed
    pool = profiles.profiles[profile_name].seed_pool
    digest = hashlib.sha256((key + profiles.pool_salt).encode("utf-8")).hexdigest()
    value = int(digest, 16)
    if pool:
        return pool[value % len(pool)]
    return value % (2 ** 31)


def build_clips(items: list[Item], profiles: Profiles, locks: Locks) -> list[Clip]:
    grouped: dict[str, dict] = {}
    for item in items:
        default_profile = FIELD_TO_PROFILE[item.field]
        key = clip_key(default_profile, item.text)
        bucket = grouped.setdefault(key, {
            "default_profile": default_profile,
            "source_text": item.text,
            "item_ids": set(),
            "fields": set(),
            "lessons": set(),
        })
        bucket["item_ids"].add(item.id)
        bucket["fields"].add(item.field)
        if item.lesson:
            bucket["lessons"].add(item.lesson)

    clips: list[Clip] = []
    for key, bucket in sorted(grouped.items()):
        lock = locks.get(key)
        profile_name = (lock.profile if lock and lock.profile else bucket["default_profile"])
        text = lock.text_override if lock and lock.text_override else bucket["source_text"]
        clips.append(Clip(
            key=key,
            profile=profile_name,
            text=text,
            source_text=bucket["source_text"],
            seed=resolve_seed(key, profile_name, profiles, locks),
            locked=lock is not None,
            item_ids=tuple(sorted(bucket["item_ids"])),
            fields=tuple(sorted(bucket["fields"])),
            lessons=tuple(sorted(bucket["lessons"])),
        ))
    return clips


def fingerprint(clip: Clip, profile: Profile) -> str:
    """Everything that changes the audio bytes — and nothing else.

    Deliberately excludes item_ids, fields and lessons: a new lesson reusing an
    existing prompt must not force a re-render.
    """
    payload = {
        "text": clip.text,
        "profile": clip.profile,
        "seed": clip.seed,
        "speaker": profile.speaker,
        "language": profile.language,
        "instruct": profile.instruct,
        "sampling": dict(sorted(profile.sampling.items())),
        "trim": profile.trim,
        "normalize": profile.normalize,
    }
    blob = json.dumps(payload, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(blob.encode("utf-8")).hexdigest()[:16]


def status_of(clip: Clip, profile: Profile, state: RenderState, audio_dir: Path) -> str:
    if not (Path(audio_dir) / f"{clip.key}.wav").exists():
        return "missing"
    if state.entries.get(clip.key) != fingerprint(clip, profile):
        return "stale"
    return "rendered"


def orphan_locks(locks: Locks, clips: list[Clip]) -> list[str]:
    """Locks whose clip no longer exists — usually because the text changed.

    Reported, never removed: dropping a curated decision is the user's call.
    """
    live = {c.key for c in clips}
    return sorted(k for k in locks.locks if k not in live)
