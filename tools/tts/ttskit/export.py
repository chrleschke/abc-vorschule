"""Approvete Clips als OGG/Opus in die App-Assets exportieren.

Exportiert wird genau ein Clip, wenn er gelockt ist und sein Render-Stand
aktuell ('rendered'). Der Export besitzt das Zielverzeichnis: nicht mehr
exportierte .ogg-Dateien werden entfernt, index.json wird immer neu
geschrieben — deterministisch, ohne Zeitstempel, für saubere Diffs.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

import soundfile as sf

from .paths import Paths
from .plan import orphan_locks, status_of

#: Bei gleichem Quelltext in mehreren Profilen gewinnt das frühere Profil.
#: Die App kennt am Call-Site nur den Text — der Index muss eindeutig sein.
PROFILE_PRIORITY = ("word", "phoneme", "prompt", "miss", "reward",
                    "sentence", "finale", "ui")


def asset_name(key: str) -> str:
    """clipKey → Asset-Dateiname. Doppelpunkte sind in Zip-Einträgen riskant
    und auf Windows verboten; Assets brauchen einen portablen Namen."""
    return key.replace(":", "_") + ".ogg"


@dataclass
class ExportReport:
    exported: list[str] = field(default_factory=list)
    skipped: list[tuple[str, str]] = field(default_factory=list)
    removed: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {
            "exported": self.exported,
            "skipped": [{"key": k, "reason": r} for k, r in self.skipped],
            "removed": self.removed,
            "warnings": self.warnings,
        }


def export_to_app(paths: Paths) -> ExportReport:
    from .cli import load_context  # lokaler Import: cli importiert nicht zurück

    ctx = load_context(paths)
    report = ExportReport()

    for key in orphan_locks(ctx.locks, ctx.clips):
        report.skipped.append((key, "Lock ist verwaist — Quelltext existiert nicht mehr"))

    exportable = []
    for clip in ctx.clips:
        if not clip.locked:
            continue
        profile = ctx.profiles.profiles[clip.profile]
        status = status_of(clip, profile, ctx.state, paths.audio)
        if status != "rendered":
            report.skipped.append((clip.key, f"Status ist {status}, nicht rendered"))
            continue
        exportable.append(clip)

    target = Path(paths.app_audio_dir)
    target.mkdir(parents=True, exist_ok=True)

    index: dict[str, dict] = {}
    for clip in sorted(exportable, key=lambda c: c.key):
        data, sr = sf.read(paths.audio / f"{clip.key}.wav", dtype="float32")
        sf.write(target / asset_name(clip.key), data, sr,
                 format="OGG", subtype="OPUS")
        report.exported.append(clip.key)

        text = clip.source_text
        existing = index.get(text)
        if existing is None:
            index[text] = {"file": asset_name(clip.key), "profile": clip.profile}
            continue
        old = PROFILE_PRIORITY.index(existing["profile"])
        new = PROFILE_PRIORITY.index(clip.profile)
        winner = existing["profile"] if old <= new else clip.profile
        if old > new:
            index[text] = {"file": asset_name(clip.key), "profile": clip.profile}
        report.warnings.append(
            f"Text {text!r} existiert in mehreren Profilen — "
            f"App spielt {winner!r}")

    keep = {asset_name(c.key) for c in exportable} | {"index.json"}
    for path in sorted(target.glob("*.ogg")):
        if path.name not in keep:
            path.unlink()
            report.removed.append(path.name)

    payload = {"version": 1,
               "clips": {t: index[t] for t in sorted(index)}}
    (target / "index.json").write_text(
        json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=True) + "\n",
        encoding="utf-8")
    return report
