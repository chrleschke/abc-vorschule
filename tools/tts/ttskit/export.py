"""Approvete Clips als OGG/Opus in die App-Assets exportieren.

Exportiert wird genau ein Clip, wenn er gelockt ist und sein Render-Stand
aktuell ('rendered'). Der Export besitzt das Zielverzeichnis: nicht mehr
exportierte .ogg-Dateien werden entfernt, index.json wird immer neu
geschrieben — deterministisch, ohne Zeitstempel, für saubere Diffs.

Determinismus heißt hier: ein wiederholter Lauf ohne geänderte Eingaben fasst
keine Datei an. Die OGG-Bytes selbst sind pro Encode NICHT reproduzierbar —
`soundfile`/libsndfile schreibt eine zufällige Ogg-Bitstream-Seriennummer, also
erzeugt derselbe WAV-Input bei jedem Aufruf ein anderes .ogg. Deshalb merkt
sich `index.json` pro Clip den Render-Fingerprint (denselben 16-stelligen Wert
wie render-state.json) und ein Clip wird nur neu encodiert, wenn sich sein
Fingerprint geändert hat oder die Zieldatei fehlt.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

import soundfile as sf

from .paths import Paths
from .plan import fingerprint, orphan_locks, status_of

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
    #: Clips, deren Fingerprint sich seit dem letzten Export nicht geändert
    #: hat — die vorhandene .ogg-Datei wurde unangetastet gelassen.
    unchanged: list[str] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {
            "exported": self.exported,
            "skipped": [{"key": k, "reason": r} for k, r in self.skipped],
            "removed": self.removed,
            "warnings": self.warnings,
            "unchanged": self.unchanged,
        }


def _previous_fingerprints(index_path: Path) -> dict[str, str]:
    """asset-Dateiname → zuletzt exportierter Fingerprint, aus dem alten Index.

    Fehlt die Datei oder ist sie kaputt, ist das kein Fehler — dann wird
    einfach alles neu encodiert, wie beim allerersten Export.
    """
    if not index_path.exists():
        return {}
    try:
        payload = json.loads(index_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}
    clips = payload.get("clips") if isinstance(payload, dict) else None
    if not isinstance(clips, dict):
        return {}
    result: dict[str, str] = {}
    for entry in clips.values():
        if not isinstance(entry, dict):
            continue
        file = entry.get("file")
        fp = entry.get("fingerprint")
        if isinstance(file, str) and isinstance(fp, str):
            result[file] = fp
    return result


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

    previous = _previous_fingerprints(target / "index.json")

    index: dict[str, dict] = {}
    for clip in sorted(exportable, key=lambda c: c.key):
        profile = ctx.profiles.profiles[clip.profile]
        fp = fingerprint(clip, profile)
        name = asset_name(clip.key)
        dest = target / name

        if previous.get(name) == fp and dest.exists():
            report.unchanged.append(clip.key)
        else:
            data, sr = sf.read(paths.audio / f"{clip.key}.wav", dtype="float32")
            sf.write(dest, data, sr, format="OGG", subtype="OPUS")
            report.exported.append(clip.key)

        text = clip.source_text
        existing = index.get(text)
        if existing is None:
            index[text] = {"file": name, "profile": clip.profile, "fingerprint": fp}
            continue
        old = PROFILE_PRIORITY.index(existing["profile"])
        new = PROFILE_PRIORITY.index(clip.profile)
        winner = existing["profile"] if old <= new else clip.profile
        if old > new:
            index[text] = {"file": name, "profile": clip.profile, "fingerprint": fp}
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
