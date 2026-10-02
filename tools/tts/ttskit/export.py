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

Aufgeräumt wird gegen den Index: eine .ogg im Zielverzeichnis, auf die kein
Index-Eintrag zeigt, kann die App nie abspielen und wandert nur ins APK. Das
betrifft vor allem Kollisions-Verlierer — teilen sich zwei gelockte Clips
denselben gesprochenen Text, bekommt nur einer den Eintrag (siehe
`_collision_winner`), und der andere wird deshalb gar nicht erst encodiert.

Determinismus heißt hier: ein wiederholter Lauf ohne geänderte Eingaben fasst
keine Datei an. Die OGG-Bytes selbst sind pro Encode NICHT reproduzierbar —
`soundfile`/libsndfile schreibt eine zufällige Ogg-Bitstream-Seriennummer, also
erzeugt derselbe WAV-Input bei jedem Aufruf ein anderes .ogg. Deshalb merkt
sich `index.json` pro Clip einen Fingerprint der **Produktions-Audio selbst**
(siehe `export_fingerprint`) und ein Clip wird nur neu encodiert, wenn sich
dieser Fingerprint geändert hat oder die Zieldatei fehlt:

* Qwen-Clips: `wav:<sha>` über Samples und Rate von `out/audio/<key>.wav`.
  Neuer Wurf übernommen, Schnitt in der Wellenform (`render.trim_candidate`
  schreibt die Produktions-WAV mit) — beides ändert die Datei und damit den
  Fingerprint. Profil-Einstellungen (Instruktion, Sampling, …) stehen bewusst
  NICHT darin: ein Profil-Update ist eine Verbesserung für künftige Renders,
  die vorhandene Aufnahme klingt danach genau wie vorher.
* Mikrofon-Aufnahmen: `mic:<sha>` aus dem Sidecar (`mic.fingerprint_of`).

Bis Oktober 2026 stand im Index der Render-Fingerprint aus `plan.fingerprint`
(Text, Seed, Stimme, *aktuelle* Profil-Instruktion und -Sampling). Ein Eintrag
in diesem Altformat wird nicht blind neu encodiert: `_matches_export`
vergleicht die committete .ogg mit der Produktions-WAV, und klingen beide
gleich, bleibt die Datei liegen und nur der Fingerprint im Index wechselt
(`ExportReport.migrated`).
"""

from __future__ import annotations

import hashlib
import io
import json
from dataclasses import dataclass, field
from pathlib import Path

import numpy as np
import soundfile as sf

from .extract import reads_as_bare_sentence
from .models import Clip
from .paths import Paths
from .plan import orphan_locks, status_of
from .render import candidate_meta

#: Bei gleichem Quelltext in mehreren Profilen gewinnt das frühere Profil —
#: nach verified-Audio (Fingerprint stimmt mit dem letzten Export überein).
#: phoneme vor word, damit Buchstaben-/Silben-Laute nicht von Wort-Clips
#: verdrängt werden. Die App kennt am Call-Site nur den Text — der Index
#: muss eindeutig sein.
#: `monster` steht bewusst nicht drin: es ist eine Variante (siehe
#: VARIANT_PROFILES), landet unter `variants.monster` und erreicht
#: `_collision_winner` nie.
#: `math` (Rechenaufgaben, „drei plus zwei") kollidiert heute mit keinem anderen
#: Text; es steht hier nur, damit eine künftige Kollision nicht an `.index` scheitert.
PROFILE_PRIORITY = ("phoneme", "word", "article_word", "prompt", "math", "miss",
                    "reward", "sentence", "finale", "ui")

#: Profile, deren Clips als *Variante* eines Textes gelten: die App sucht sie
#: unter `variants.<name>.<text>` (ClipIndex.lookup(text, variant)). Sie
#: kollidieren nicht mit dem normalen Clip desselben Textes — „S" darf als
#: phoneme in `clips` und als monster in `variants.monster` stehen.
VARIANT_PROFILES: dict[str, str] = {"monster": "monster"}

def _pedagogical_winner(text: str, prof_a: str, prof_b: str) -> str | None:
    """Preferred index profile when the same text appears in two profiles."""
    pair = {prof_a, prof_b}
    if pair == {"phoneme", "word"}:
        return "phoneme"
    if pair == {"miss", "sentence"}:
        return "sentence"
    if pair == {"prompt", "sentence"}:
        # Seit `extract.profile_for_item` einen Satz-Prompt gleich als `sentence`
        # ausgibt, kann diese Kollision aus dem Pack nicht mehr entstehen. Die
        # Regel bleibt für Altbestand in locks.json — und als eine Wahrheit,
        # geteilt mit dem Extractor.
        return "sentence" if reads_as_bare_sentence(text) else "prompt"
    return None


def _clip_verified(asset_file: str, fingerprint: str,
                   previous: dict[str, tuple[str, dict]]) -> bool:
    """True when this clip's fingerprint matches the last committed export."""
    prev = previous.get(asset_file)
    return prev is not None and prev[1].get("fingerprint") == fingerprint


def _collision_winner(text: str, existing: dict, existing_file: str,
                      new_profile: str, new_file: str, new_fp: str,
                      previous: dict[str, tuple[str, dict]]) -> dict:
    """Pick the index entry when two locked clips share the same spoken text."""
    preferred = _pedagogical_winner(text, existing["profile"], new_profile)
    if preferred == existing["profile"]:
        return existing
    if preferred == new_profile:
        return {"file": new_file, "profile": new_profile, "fingerprint": new_fp}

    existing_verified = _clip_verified(existing_file, existing["fingerprint"], previous)
    new_verified = _clip_verified(new_file, new_fp, previous)
    if new_verified and not existing_verified:
        return {"file": new_file, "profile": new_profile, "fingerprint": new_fp}
    if existing_verified and not new_verified:
        return existing
    old_pri = PROFILE_PRIORITY.index(existing["profile"])
    new_pri = PROFILE_PRIORITY.index(new_profile)
    if new_pri < old_pri:
        return {"file": new_file, "profile": new_profile, "fingerprint": new_fp}
    return existing


def asset_name(key: str) -> str:
    """clipKey → Asset-Dateiname. Doppelpunkte sind in Zip-Einträgen riskant
    und auf Windows verboten; Assets brauchen einen portablen Namen."""
    return key.replace(":", "_") + ".ogg"


#: Präfixe der Fingerprints, die nach dem Inhalt der Audio gehen. Alles andere
#: im Index ist ein Render-Fingerprint aus der Zeit vor `export_fingerprint`.
CONTENT_FINGERPRINT_PREFIXES = ("wav:", "mic:")

#: Toleranzen für den Abgleich Alt-Eintrag ↔ Produktions-WAV (`_matches_export`).
#: Gemessen am echten Bestand: dieselbe WAV, zweimal encodiert, decodiert
#: bit-gleich (Korrelation 1.000); ein echter neuer Wurf liegt bei ≤ 0.2.
MATCH_MAX_LENGTH_DIFF_SECONDS = 0.03
MATCH_MIN_CORRELATION = 0.98


def wav_fingerprint(path: Path) -> str:
    """Hash über Samples und Rate einer WAV — gleiche Audio, gleicher Wert."""
    data, sr = sf.read(path, dtype="float32", always_2d=True)
    digest = hashlib.sha256(f"{sr}:{data.shape[1]}:".encode("ascii"))
    digest.update(np.ascontiguousarray(data).tobytes())
    return "wav:" + digest.hexdigest()[:16]


def export_fingerprint(paths: Paths, clip: Clip) -> str:
    """Fingerprint, der entscheidet, ob der Export einen Clip neu encodiert.

    Geht nur nach der Produktions-Audio, nie nach dem heutigen Profil: wer die
    Instruktion oder das Sampling eines Profils ändert, ändert keine einzige
    schon gerenderte Aufnahme (siehe `plan.status_of`) — und darf deshalb auch
    keinen Re-Encode auslösen. Für Mikrofon-Aufnahmen gilt der Hash der
    bearbeiteten Datei aus dem Sidecar (`mic.fingerprint_of`).

    Nicht zu verwechseln mit `plan.fingerprint`: der beschreibt, WOMIT eine
    Aufnahme entstand, und treibt „⚠️ alt" und `verified` beim Promote.
    """
    meta = candidate_meta(paths, clip.key, clip.seed)
    if meta.get("source") == "mic" and isinstance(meta.get("fingerprint"), str):
        return meta["fingerprint"]
    return wav_fingerprint(paths.audio / f"{clip.key}.wav")


def _is_legacy_fingerprint(value: object) -> bool:
    return isinstance(value, str) and not value.startswith(CONTENT_FINGERPRINT_PREFIXES)


def _encode_ogg(data: np.ndarray, sr: int, dest) -> None:
    sf.write(dest, data, sr, format="OGG", subtype="OPUS")


def _mono(data: np.ndarray) -> np.ndarray:
    return data.mean(axis=1) if data.ndim == 2 else data


def _matches_export(wav_path: Path, ogg_path: Path) -> bool:
    """Klingt die schon exportierte .ogg wie die heutige Produktions-WAV?

    Verglichen wird nicht WAV gegen .ogg — Opus ist verlustbehaftet, gerade bei
    Zischlauten fällt die Korrelation einer unveränderten Aufnahme so unter
    0.98 (am echten Bestand bei vier von fünf Clips, teils unter 0.9). Stattdessen wird die WAV genauso encodiert wie beim Export (im
    Speicher, ohne Datei) und *decodiert gegen decodiert* verglichen: dieselbe
    WAV ergibt dieselben Samples, nur die Bitstream-Seriennummer unterscheidet
    sich. Länge und Korrelation statt Bit-Gleichheit, damit eine andere
    libopus-Version die Migration nicht in einen Massen-Re-Encode kippt.
    """
    try:
        old, old_sr = sf.read(ogg_path, dtype="float32", always_2d=True)
    except (RuntimeError, sf.LibsndfileError):
        return False
    data, sr = sf.read(wav_path, dtype="float32")
    buf = io.BytesIO()
    _encode_ogg(data, sr, buf)
    buf.seek(0)
    fresh, fresh_sr = sf.read(buf, dtype="float32", always_2d=True)
    a, b = _mono(fresh), _mono(old)
    if old_sr != fresh_sr:
        # Lokaler Import: scipy braucht der Export sonst nie.
        from math import gcd

        from scipy.signal import resample_poly

        g = gcd(int(fresh_sr), int(old_sr))
        b = resample_poly(b, int(fresh_sr) // g, int(old_sr) // g).astype(np.float32)
    if abs(len(a) - len(b)) > MATCH_MAX_LENGTH_DIFF_SECONDS * fresh_sr:
        return False
    n = min(len(a), len(b))
    a, b = a[:n].astype(np.float64), b[:n].astype(np.float64)
    norm = float(np.linalg.norm(a) * np.linalg.norm(b))
    if norm == 0.0:
        # Beides Stille ist gleich; nur eine Seite still ist es nicht.
        return not np.any(a) and not np.any(b)
    return float(np.dot(a, b)) / norm >= MATCH_MIN_CORRELATION


@dataclass
class ExportReport:
    exported: list[str] = field(default_factory=list)
    skipped: list[tuple[str, str]] = field(default_factory=list)
    removed: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)
    #: Clips, deren Fingerprint sich seit dem letzten Export nicht geändert
    #: hat — die vorhandene .ogg-Datei wurde unangetastet gelassen.
    unchanged: list[str] = field(default_factory=list)
    #: Teilmenge von `unchanged`: der Index trug noch einen Alt-Fingerprint
    #: (Render-Einstellungen statt Audio-Inhalt), die .ogg klang aber wie die
    #: Produktions-WAV — nur der Fingerprint im Index wurde umgeschrieben.
    migrated: list[str] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {
            "exported": self.exported,
            "skipped": [{"key": k, "reason": r} for k, r in self.skipped],
            "removed": self.removed,
            "warnings": self.warnings,
            "unchanged": self.unchanged,
            "migrated": self.migrated,
        }


def _previous_index(index_path: Path) -> dict[str, tuple[str, dict]]:
    """asset-Dateiname → (Text, Eintrag) aus dem zuletzt geschriebenen Index.

    Fehlt die Datei oder ist sie kaputt, ist das kein Fehler — dann wird
    einfach alles neu encodiert, wie beim allerersten Export, und nichts kann
    für einen gelockten, aber lokal nicht gerenderten Clip erhalten werden.

    Liest `clips` und `variants` gleichermaßen — ein zurückbehaltener
    Monster-Clip (lokal nicht gerendert, aber schon committet) muss die
    Variante genauso überleben wie ein normaler Clip.
    """
    if not index_path.exists():
        return {}
    try:
        payload = json.loads(index_path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        return {}
    if not isinstance(payload, dict):
        return {}
    result: dict[str, tuple[str, dict]] = {}
    clips = payload.get("clips")
    if isinstance(clips, dict):
        for text, entry in clips.items():
            if not isinstance(text, str) or not isinstance(entry, dict):
                continue
            file = entry.get("file")
            if isinstance(file, str):
                result[file] = (text, entry)
    variants = payload.get("variants")
    if isinstance(variants, dict):
        for by_text in variants.values():
            if not isinstance(by_text, dict):
                continue
            for text, entry in by_text.items():
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
    #: Dasselbe für Varianten-Clips (z. B. monster) — separat, weil sie unter
    #: `variants.<name>.<text>` stehen, nicht unter `clips.<text>`.
    retained_variants: dict[str, dict[str, dict]] = {}
    retained_files: set[str] = set()
    for clip in ctx.clips:
        # Auch ein Lock mit `cleared` („Keine Produktion") zählt hier als nicht
        # gelockt (plan.build_clips): weder Export noch Zurückbehalten — die
        # committete .ogg fällt beim Aufräumen unten weg.
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
                    variant = VARIANT_PROFILES.get(prev_entry.get("profile"))
                    if variant is not None:
                        retained_variants.setdefault(variant, {})[prev_text] = prev_entry
                    else:
                        retained_entries[prev_text] = prev_entry
            else:
                report.skipped.append((clip.key, f"Lokal nicht gerendert (status {status})"))
            continue
        exportable.append(clip)

    # Der Index entscheidet zuerst, welche Datei die App überhaupt erreichen
    # kann — erst danach wird encodiert. Bei einer Textkollision landet nur der
    # Gewinner im Index; den Verlierer trotzdem zu schreiben, hinterlässt eine
    # Datei, die im APK liegt, aber von keinem Call-Site gefunden wird.
    planned = []
    for clip in sorted(exportable, key=lambda c: c.key):
        planned.append((clip, asset_name(clip.key), export_fingerprint(paths, clip)))

    # Einmal-Migration vom Render- auf den Inhalts-Fingerprint, noch vor der
    # Kollisionsauflösung: `_clip_verified` vergleicht mit `previous`, und ein
    # Alt-Eintrag darf einen Clip dort nicht plötzlich „unbestätigt" machen.
    # Klingt die committete .ogg wie die Produktions-WAV, gilt der Eintrag als
    # mit dem neuen Fingerprint geschrieben — die Datei bleibt liegen.
    migrated: set[str] = set()
    for clip, name, fp in planned:
        prev = previous.get(name)
        if prev is None or not _is_legacy_fingerprint(prev[1].get("fingerprint")):
            continue
        dest = target / name
        if dest.exists() and _matches_export(paths.audio / f"{clip.key}.wav", dest):
            previous[name] = (prev[0], {**prev[1], "fingerprint": fp})
            migrated.add(clip.key)

    index: dict[str, dict] = {}
    variants: dict[str, dict[str, dict]] = {}
    for clip, name, fp in planned:
        text = clip.source_text.strip()
        entry = {"file": name, "profile": clip.profile, "fingerprint": fp}
        variant = VARIANT_PROFILES.get(clip.profile)
        if variant is not None:
            variants.setdefault(variant, {})[text] = entry
            continue
        existing = index.get(text)
        if existing is None:
            index[text] = entry
            continue
        winner_entry = _collision_winner(
            text, existing, existing["file"], clip.profile, name, fp, previous)
        winner = winner_entry["profile"]
        if winner_entry is not existing:
            index[text] = winner_entry
        preferred = _pedagogical_winner(text, existing["profile"], clip.profile)
        if preferred is None or preferred != winner:
            report.warnings.append(
                f"Text {text!r} existiert in mehreren Profilen — "
                f"App spielt {winner!r}")

    # Zurückbehaltene Einträge dürfen frische (exportierte/unveränderte)
    # Einträge nie überschreiben — bei gleichem Text gewinnt der frische.
    for text, entry in retained_entries.items():
        index.setdefault(text, entry)
    for variant, by_text in retained_variants.items():
        for text, entry in by_text.items():
            variants.setdefault(variant, {}).setdefault(text, entry)

    # Varianten-Clips stehen zusätzlich in `clips`, wenn dort kein anderes
    # Profil den Text trägt — „Bäh!" muss auch für eine Ansage in
    # Normalstimme auffindbar sein. Ein phoneme-„S" gewinnt dagegen immer.
    for by_text in variants.values():
        for text, entry in by_text.items():
            index.setdefault(text, entry)

    indexed_files = {e["file"] for e in index.values()} | {
        e["file"] for by_text in variants.values() for e in by_text.values()}

    for clip, name, fp in planned:
        if name not in indexed_files:
            report.skipped.append(
                (clip.key,
                 "Text wird von einem anderen Profil abgedeckt — "
                 "kein eigener Clip im Index"))
            continue
        dest = target / name
        prev = previous.get(name)
        if prev is not None and prev[1].get("fingerprint") == fp and dest.exists():
            report.unchanged.append(clip.key)
            if clip.key in migrated:
                report.migrated.append(clip.key)
        else:
            data, sr = sf.read(paths.audio / f"{clip.key}.wav", dtype="float32")
            _encode_ogg(data, sr, dest)
            report.exported.append(clip.key)

    # Aufgeräumt wird gegen den Index, nicht gegen die Menge der gelockten
    # Clips: eine .ogg, auf die kein Eintrag zeigt, kann die App nie abspielen.
    # `retained_files` bleibt die eine Ausnahme — diese Dateien sind lokal
    # nicht neu encodierbar (out/ ist gitignored, die WAV fehlt), also wird
    # eine ohne Index-Eintrag zwar behalten, aber gemeldet statt still
    # mitgeschleppt.
    keep = indexed_files | retained_files | {"index.json"}
    for path in sorted(target.glob("*.ogg")):
        if path.name not in keep:
            path.unlink()
            report.removed.append(path.name)
    for name in sorted(retained_files - indexed_files):
        report.warnings.append(
            f"{name} bleibt erhalten, hat aber keinen Index-Eintrag — "
            "die App kann diesen Clip nicht abspielen")

    payload = {"version": 1,
               "clips": {t: index[t] for t in sorted(index)},
               "variants": {v: {t: by_text[t] for t in sorted(by_text)}
                            for v, by_text in sorted(variants.items())}}
    (target / "index.json").write_text(
        json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=True) + "\n",
        encoding="utf-8")
    return report
