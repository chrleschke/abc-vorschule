"""Approvete Clips als OGG/Opus in die App-Assets exportieren.

Exportiert wird genau ein Clip, wenn er gelockt ist und lokal eine Datei unter
out/audio/ hat. Ein Profil-Update (Instruktion, Sampling, Seed-Pool,
Nachbearbeitung) invalidiert nie bereits bestätigten (gelockten) Content —
siehe `plan.status_of`. Der Export besitzt das Zielverzeichnis, aber die
Lösch-Semantik folgt Unlocks, nicht dem lokalen Render-Stand: `out/` ist
gitignored, auf einem frischen Checkout fehlt die WAV für jeden gelockten
Clip, obwohl die Datei längst committet ist. Ein gelockter Clip ohne lokale
WAV (Status `missing`) behält seine bereits exportierte Datei und seinen
Index-Eintrag — nur ein echter Unlock (oder ein Text, der zu keinem Lock mehr
gehört) entfernt eine Datei. index.json wird immer neu geschrieben —
deterministisch, ohne Zeitstempel, für saubere Diffs.

Determinismus heißt hier: ein wiederholter Lauf ohne geänderte Eingaben fasst
keine Datei an. Die OGG-Bytes selbst sind pro Encode NICHT reproduzierbar —
`soundfile`/libsndfile schreibt eine zufällige Ogg-Bitstream-Seriennummer, also
erzeugt derselbe WAV-Input bei jedem Aufruf ein anderes .ogg. Deshalb merkt
sich `index.json` pro Clip einen eigenen Fingerprint (Text, Profil, Seed,
Stimme, Instruktion, Sampling — siehe `plan.fingerprint`) und ein Clip wird
nur neu encodiert, wenn sich dieser Fingerprint geändert hat oder die
Zieldatei fehlt.
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


def _previous_index(index_path: Path) -> dict[str, tuple[str, dict]]:
    """asset-Dateiname → (Text, Eintrag) aus dem zuletzt geschriebenen Index.

    Fehlt die Datei oder ist sie kaputt, ist das kein Fehler — dann wird
    einfach alles neu encodiert, wie beim allerersten Export, und nichts kann
    für einen gelockten, aber lokal nicht gerenderten Clip erhalten werden.
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
    result: dict[str, tuple[str, dict]] = {}
    for text, entry in clips.items():
        if not isinstance(text, str) or not isinstance(entry, dict):
            continue
        file = entry.get("file")
        if isinstance(file, str):
            result[file] = (text, entry)
    return result


def export_to_app(paths: Paths) -> ExportReport:
    from .cli import load_context  # lokaler Import: cli importiert nicht zurück

    ctx = load_context(paths)
    report = ExportReport()

    for key in orphan_locks(ctx.locks, ctx.clips):
        report.skipped.append((key, "Lock ist verwaist — Quelltext existiert nicht mehr"))

    target = Path(paths.app_audio_dir)
    target.mkdir(parents=True, exist_ok=True)

    previous = _previous_index(target / "index.json")

    exportable = []
    #: Text → Index-Eintrag (verbatim, inkl. altem Fingerprint) für gelockte
    #: Clips, die lokal nicht (mehr) rendered sind, deren Datei aber schon aus
    #: einem früheren Export existiert. Sie bleiben liegen, bis der Lock fällt.
    retained_entries: dict[str, dict] = {}
    retained_files: set[str] = set()
    for clip in ctx.clips:
        if not clip.locked:
            continue
        status = status_of(clip, paths.audio)
        if status != "rendered":
            name = asset_name(clip.key)
            if (target / name).exists():
                report.skipped.append(
                    (clip.key,
                     f"Lokal nicht gerendert (status {status}) — "
                     "vorhandene Datei bleibt erhalten"))
                retained_files.add(name)
                prev = previous.get(name)
                if prev is not None:
                    prev_text, prev_entry = prev
                    retained_entries[prev_text] = prev_entry
            else:
                report.skipped.append((clip.key, f"Lokal nicht gerendert (status {status})"))
            continue
        exportable.append(clip)

    index: dict[str, dict] = {}
    for clip in sorted(exportable, key=lambda c: c.key):
        profile = ctx.profiles.profiles[clip.profile]
        fp = fingerprint(clip, profile)
        name = asset_name(clip.key)
        dest = target / name

        prev = previous.get(name)
        if prev is not None and prev[1].get("fingerprint") == fp and dest.exists():
            report.unchanged.append(clip.key)
        else:
            data, sr = sf.read(paths.audio / f"{clip.key}.wav", dtype="float32")
            sf.write(dest, data, sr, format="OGG", subtype="OPUS")
            report.exported.append(clip.key)

        text = clip.source_text.strip()
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

    # Zurückbehaltene Einträge dürfen frische (exportierte/unveränderte)
    # Einträge nie überschreiben — bei gleichem Text gewinnt der frische.
    for text, entry in retained_entries.items():
        index.setdefault(text, entry)

    keep = {asset_name(c.key) for c in exportable} | retained_files | {"index.json"}
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
