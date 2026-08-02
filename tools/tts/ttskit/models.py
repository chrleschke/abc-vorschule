"""Shared vocabulary for the TTS tooling."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Item:
    """One spot in the app that speaks.

    `id` mirrors the scheme in TtsDebugEntry.kt so a later app integration
    needs no translation layer. `field` is a *logical* key that is unique
    across sources — sentences.json and finales.json both use a JSON field
    called "tts", but they map to different profiles.
    """

    id: str
    text: str
    field: str
    source: str
    lesson: str | None
    label: str


@dataclass(frozen=True)
class Clip:
    """One render unit. Several items can share it when text and profile match."""

    key: str
    profile: str
    text: str
    source_text: str
    seed: int
    locked: bool
    item_ids: tuple[str, ...]
    fields: tuple[str, ...]
    lessons: tuple[str, ...]
