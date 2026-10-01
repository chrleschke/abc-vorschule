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
    #: atoms.json only — drives lemma → phoneme vs word (see extract.profile_for_item).
    atom_kind: str | None = None


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
    #: Die Stimme, mit der wirklich synthetisiert wird — normalerweise die des
    #: Profils, per Lock aber für diesen einen Clip austauschbar. Aufgelöst,
    #: nicht optional: wer den Clip in der Hand hat, soll die Stimme nicht noch
    #: einmal aus Profil plus Lock zusammensuchen müssen.
    speaker: str
    #: Die Sprache, mit der synthetisiert wird — `Lock.language` oder die des
    #: Profils, aufgelöst wie `speaker`.
    language: str
    #: Entwurfstext für neue Aufnahmen (`Lock.draft_text`), None = keiner.
    #: `text` bleibt der Text der Produktion — er steckt in deren Fingerprint.
    draft_text: str | None = None

    @property
    def generation_text(self) -> str:
        """Was neue Kandidaten sprechen: Entwurf vor Produktionstext."""
        return self.draft_text or self.text
