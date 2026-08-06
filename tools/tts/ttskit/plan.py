"""Turn items plus stored decisions into concrete render units."""

from __future__ import annotations

import hashlib
import json
from collections import Counter
from dataclasses import replace
from pathlib import Path

from . import voices
from .audio import POSTPROCESS_VERSION
from .extract import profile_for_item
from .models import Clip, Item
from .store import Locks, Profile, Profiles


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


#: Wie viele Ränge „Top-Seeds" weit reicht. Zehn, weil ein Profil damit auch bei
#: wenigen Locks noch eine Auswahl hat, aus der sich zufällig ziehen lässt.
TOP_SEED_LIMIT = 10


def top_seeds(locks: Locks, profile_name: str, limit: int = TOP_SEED_LIMIT) -> list[int]:
    """Die am häufigsten bestätigten Seeds dieses Profils, häufigste zuerst.

    Ein Lock ist eine getroffene Entscheidung: derselbe Seed unter mehreren
    Locks desselben Profils hat also mehrfach überzeugt und ist ein besserer
    Startpunkt als ein frischer Zufallswert.

    `limit` schneidet bewusst nicht mitten in eine Punktgleichheit: teilen
    mehrere Seeds den Rang an der Grenze, kommen sie alle mit. Zwischen ihnen
    gibt es keinen Grund zu wählen — die Auswahl trifft später der Zufall.

    Der Rückgabewert ist leer, wenn das Profil noch keinen einzigen Lock hat.
    Der Aufrufer fällt dann auf Zufalls-Seeds zurück.
    """
    counts: Counter[int] = Counter()
    for key, lock in locks.locks.items():
        # Ein Lock darf das Profil überschreiben; der Key-Präfix ist nur das
        # Default-Profil. Gezählt wird, womit tatsächlich synthetisiert wird.
        effective = lock.profile or key.split(":", 1)[0]
        if effective == profile_name:
            counts[lock.seed] += 1
    if not counts or limit < 1:
        return []
    # Sekundär nach Seed sortiert, damit die Reihenfolge bei Gleichstand
    # reproduzierbar ist — sonst wäre schon das Abschneiden zufällig.
    ranked = sorted(counts.items(), key=lambda kv: (-kv[1], kv[0]))
    cutoff = ranked[min(limit, len(ranked)) - 1][1]
    return [seed for seed, count in ranked if count >= cutoff]


def build_clips(items: list[Item], profiles: Profiles, locks: Locks) -> list[Clip]:
    grouped: dict[str, dict] = {}
    for item in items:
        default_profile = profile_for_item(item)
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

    where = str(locks.source) if locks.source is not None else "locks.json"
    clips: list[Clip] = []
    for key, bucket in sorted(grouped.items()):
        lock = locks.get(key)
        profile_name = (lock.profile if lock and lock.profile else bucket["default_profile"])
        # A lock naming a profile that does not exist must be caught here, at
        # the single funnel every entry point goes through. Letting it pass
        # would build a Clip that raises a bare KeyError far away — in `status`,
        # in `render` and in the very /api/state the web UI needs to recover.
        if profile_name not in profiles.profiles:
            origin = (f"{where}: lock {key!r} names"
                      if lock and lock.profile == profile_name
                      else f"clip {key!r} defaults to")
            raise ValueError(
                f"{origin} the unknown profile {profile_name!r}. "
                f"Known profiles: {', '.join(sorted(profiles.profiles))}")
        # Dieselbe Begründung wie beim Profil eine Zeile höher: ein Tippfehler
        # in der Stimme würde sonst erst tief in `generate_custom_voice` als
        # NotImplementedError auffallen — pro Clip, mitten im Render-Lauf.
        speaker = lock.speaker if lock and lock.speaker else profiles.profiles[profile_name].speaker
        if voices.voice(speaker) is None:
            origin = (f"{where}: lock {key!r} names"
                      if lock and lock.speaker == speaker
                      else f"profile {profile_name!r} names")
            raise ValueError(
                f"{origin} the unknown speaker {speaker!r}. "
                f"Known speakers: {', '.join(voices.speaker_names())}")
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
            speaker=speaker,
        ))
    return clips


def effective_profile(clip: Clip, profile: Profile) -> Profile:
    """Das Profil, mit dem für genau diesen Clip synthetisiert wird.

    Ein Lock darf die Stimme einzeln austauschen; Instruktion, Sampling und
    Sprache bleiben die des Profils. Ohne Override wird das Profil unverändert
    zurückgegeben, damit die Identität erhalten bleibt — Tests, die ein Profil
    mutieren und danach den Fingerprint vergleichen, hängen daran.
    """
    if profile.speaker == clip.speaker:
        return profile
    return replace(profile, speaker=clip.speaker)


def fingerprint(clip: Clip, profile: Profile) -> str:
    """Everything that changes the audio bytes — and nothing else.

    Deliberately excludes item_ids, fields and lessons: a new lesson reusing an
    existing prompt must not force a re-render.
    """
    payload = {
        "text": clip.text,
        "profile": clip.profile,
        "seed": clip.seed,
        # clip.speaker, nicht profile.speaker: ein Stimm-Override im Lock gilt
        # nur für diesen Clip — für Kandidaten-Frische darf sich nur sein
        # eigener Fingerprint ändern. Ohne Override sind beide gleich, alte
        # Fingerprints bleiben also gültig.
        "speaker": clip.speaker,
        "language": profile.language,
        "instruct": profile.instruct,
        "sampling": dict(sorted(profile.sampling.items())),
        "trim": profile.trim,
        "normalize": profile.normalize,
        # The flags alone are not enough: the trim threshold, the trim pad and
        # the normalisation target all change the bytes too. They are constants
        # in audio.py rather than per-profile settings, so they enter the
        # fingerprint through one version number — bump POSTPROCESS_VERSION
        # whenever any of them changes, or a re-render silently will not happen.
        "postprocess": POSTPROCESS_VERSION,
    }
    blob = json.dumps(payload, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(blob.encode("utf-8")).hexdigest()[:16]


def status_of(clip: Clip, audio_dir: Path) -> str:
    """Missing, oder die Datei liegt schon da.

    Absichtlich keine Fingerprint-Prüfung mehr: ein späteres Profil-Update
    (Instruktion, Sampling, Seed-Pool, Nachbearbeitung) ist eine Verbesserung
    für künftige Renders, invalidiert aber nie bereits gerenderten Content.
    Ein Re-Render ist immer eine bewusste Handlung (`--force` oder Löschen
    der Datei), nie ein stillschweigender Seiteneffekt eines geänderten
    Profils.
    """
    return "rendered" if (Path(audio_dir) / f"{clip.key}.wav").exists() else "missing"


def orphan_locks(locks: Locks, clips: list[Clip]) -> list[str]:
    """Locks whose clip no longer exists — usually because the text changed.

    Reported, never removed: dropping a curated decision is the user's call.
    """
    live = {c.key for c in clips}
    return sorted(k for k in locks.locks if k not in live)
