"""Wie stabil ist die Stimm-Identität in der VoiceDesign-Probe, und wie oft geht sie schief?

Das Ohr entscheidet, aber es entscheidet über 42 Aufnahmen langsam und ohne
Zahlen. Hier stehen die Zahlen. Gemessen wird dreierlei:

* **Deckel-Quote** — wie oft die Aufnahme in `max_new_tokens` läuft. Das ist
  der Ausrutscher, um den es eigentlich geht: das Modell erfindet nach dem
  Wort weiter und wird mitten drin gekappt. Genau diese Clips fliegen beim
  Kuratieren raus, und genau ihre Quote entscheidet, ob man ein Wort fünfmal
  oder fünfzigmal würfeln muss.
* **Tonhöhe (F0)**, Median je Clip über Autokorrelation, verglichen in
  Halbtönen — das Ohr hört Verhältnisse, nicht Hz-Abstände.
* **Klangfarbe**, als Energieverteilung über acht logarithmische Frequenz-
  bänder, verglichen als euklidischer Abstand zum Gruppen-Median. Grob, aber
  es fängt, was die Tonhöhe nicht sieht.

Nur `numpy` und die Standardbibliothek. Das ist Absicht: die erste Fassung
hing an `librosa` aus `~/qwen-tts-test/.venv`, und als das venv während der
Arbeit verschwand, war die Auswertung der schon erzeugten Aufnahmen nicht
mehr möglich. Die Messung soll die Aufnahmen überleben, nicht umgekehrt.

Zwei Fallen, die die erste Fassung nicht gesehen hat:

1. **Kaputte Clips verderben die Nulllinie.** `sohee`s „Sack" landete bei
   74 Hz — nicht weil die Stimme tief ist, sondern weil der Clip in den
   Deckel lief und der Pitch-Schätzer an seinem unteren Anschlag klebte. Eine
   einzige solche Aufnahme blähte die Referenzstreuung auf 16,9 Halbtöne und
   ließ VoiceDesign glänzen. Clips am Anschlag zählen deshalb nicht in die
   Streuung, sondern werden getrennt ausgewiesen.
2. **Kosinus-Distanz taugt für MFCC-Mittel nicht.** Vorzeichenbehaftete Werte,
   Schwerpunkt nahe null, Distanzen über 1 — mathematisch „entgegengesetzt",
   akustisch bedeutungslos. Jetzt Bandenergien, die nicht negativ werden.

Der aussagekräftigste Block ist **B**: gleicher Text, gleiche Seeds, einmal
festes Embedding (`sohee`) und einmal beschriebene Stimme. Was VoiceDesign
dort mehr streut als `sohee`, ist Drift der Identität — sonst nichts.

Aufruf (nach `voicedesign_probe.py`):

    python3 tools/tts/experiments/voicedesign_measure.py
"""

from __future__ import annotations

import json
import wave
from pathlib import Path

import numpy as np

OUT = Path(__file__).resolve().parent.parent / "out" / "voicedesign-probe"

#: Sprechstimmen liegen zwischen ~70 Hz und ~500 Hz. Ein Median auf einem der
#: Anschläge ist kein Messwert, sondern ein aufgegebener Schätzer.
F0_MIN, F0_MAX = 70.0, 500.0
RAIL = 6.0

#: 1 Token = 80 ms (12-Hz-Tokenizer, decode_upsample_rate 1920 bei 24 kHz).
#: Getrimmt wird nach der Generierung, die Datei ist also kürzer als der
#: Deckel — wer trotzdem nah dran liegt, wurde gekappt.
SECONDS_PER_TOKEN = 0.08
CAP_RATIO = 0.90

MAX_TOKENS = {"sack": 25, "sonne": 25, "zaun": 25, "schuh": 25,
              "erdbeere": 35, "laut_m": 25, "laut_s": 25, "satz": 60}


def read_wav(path: Path) -> tuple[np.ndarray, int]:
    with wave.open(str(path)) as w:
        sr, frames = w.getframerate(), w.getnframes()
        raw = np.frombuffer(w.readframes(frames), dtype="<i2").astype(np.float64)
        if w.getnchannels() > 1:
            raw = raw.reshape(-1, w.getnchannels()).mean(axis=1)
    return raw / 32768.0, sr


def estimate_f0(wav: np.ndarray, sr: int) -> float | None:
    """Median-F0 über Autokorrelation je Frame.

    Kein pyin, aber für „liegt diese Stimme bei 200 oder bei 400 Hz" genau
    genug — und es hängt an nichts als numpy.
    """
    frame = int(0.05 * sr)
    hop = int(0.02 * sr)
    if wav.size < frame:
        return None
    lo, hi = int(sr / F0_MAX), int(sr / F0_MIN)

    picks: list[float] = []
    for start in range(0, wav.size - frame + 1, hop):
        seg = wav[start:start + frame]
        seg = seg - seg.mean()
        energy = float(seg @ seg)
        if energy < 1e-5:            # Stille — keine Tonhöhe zu holen
            continue
        corr = np.correlate(seg, seg, mode="full")[frame - 1:]
        window = corr[lo:hi + 1]
        if window.size == 0:
            continue
        lag = int(np.argmax(window)) + lo
        # Stimmhaft heißt: der Peak trägt einen nennenswerten Teil der
        # Energie. Ohne diese Schwelle liefert Rauschen beliebige Lags.
        if corr[lag] / corr[0] < 0.3:
            continue
        picks.append(sr / lag)

    return float(np.median(picks)) if picks else None


def timbre(wav: np.ndarray, sr: int) -> np.ndarray:
    """Energie in acht logarithmischen Bändern, auf Summe 1 normiert."""
    spectrum = np.abs(np.fft.rfft(wav * np.hanning(wav.size))) ** 2
    freqs = np.fft.rfftfreq(wav.size, 1 / sr)
    edges = np.geomspace(100, min(8000, sr / 2), 9)
    bands = np.array([spectrum[(freqs >= a) & (freqs < b)].sum()
                      for a, b in zip(edges[:-1], edges[1:])])
    total = bands.sum()
    return bands / total if total > 0 else bands


def features(path: Path, max_new: int) -> dict:
    wav, sr = read_wav(path)
    duration = wav.size / sr
    f0 = estimate_f0(wav, sr)
    return {
        "duration_s": round(duration, 2),
        "capped": bool(duration >= CAP_RATIO * max_new * SECONDS_PER_TOKEN),
        "f0_hz": f0,
        "railed": bool(f0 is not None and (f0 <= F0_MIN + RAIL or f0 >= F0_MAX - RAIL)),
        "timbre": timbre(wav, sr),
    }


def spread(entries: list[dict]) -> dict | None:
    usable = [e for e in entries if e["f0_hz"] is not None and not e["railed"]]
    if len(usable) < 2:
        return None
    f0 = np.array([e["f0_hz"] for e in usable])
    band = np.stack([e["timbre"] for e in usable])
    semitones = 12 * np.log2(f0 / np.median(f0))
    dist = np.linalg.norm(band - np.median(band, axis=0), axis=1)
    return {
        "n": len(entries),
        "n_usable": len(usable),
        "capped": sum(1 for e in entries if e["capped"]),
        "railed": sum(1 for e in entries if e["railed"]),
        "f0_median_hz": float(np.median(f0)),
        "f0_spread_semitones": float(semitones.max() - semitones.min()),
        "timbre_median_dist": float(np.median(dist)),
    }


def group_of(row: dict) -> str:
    if row["block"] == "B":
        who = "sohee" if row["path"].startswith("B_seeds_referenz") else "warm_de"
        return f"B  {who} über Seeds"
    return f"{row['block']}  {row['group']}"


def main() -> int:
    rows = json.loads((OUT / "rows.json").read_text(encoding="utf-8"))
    groups: dict[str, list[dict]] = {}
    per_clip: list[dict] = []
    for row in rows:
        feat = features(OUT / row["path"], MAX_TOKENS.get(row["slug"], 25))
        per_clip.append({k: v for k, v in {**row, **feat}.items() if k != "timbre"})
        groups.setdefault(group_of(row), []).append(feat)

    print("\n### Deckel-Quote — wie oft die Aufnahme in max_new_tokens läuft\n")
    print(f"{'Gruppe':<32}{'gekappt':>10}{'von':>6}")
    print("-" * 48)
    for name, entries in sorted(groups.items()):
        print(f"{name:<32}{sum(1 for e in entries if e['capped']):>10}{len(entries):>6}")

    print("\n\n### Identität — Streuung innerhalb der Gruppe\n")
    print("Clips am Pitch-Anschlag sind ausgeschlossen: kaputte Aufnahme, kein Messwert.\n")
    header = (f"{'Gruppe':<32}{'nutzbar':>9}{'F0 Median':>11}"
              f"{'F0 Spanne':>11}{'Klangfarbe':>12}")
    print(header)
    print("-" * len(header))

    results: dict[str, dict] = {}
    for name, entries in sorted(groups.items()):
        s = spread(entries)
        if s is None:
            print(f"{name:<32}{'zu wenig verwertbar':>41}")
            continue
        results[name] = s
        print(f"{name:<32}{s['n_usable']:>4}/{s['n']:<4}{s['f0_median_hz']:>10.0f}Hz"
              f"{s['f0_spread_semitones']:>9.1f}HT{s['timbre_median_dist']:>12.3f}")

    ref, vd = results.get("B  sohee über Seeds"), results.get("B  warm_de über Seeds")
    if ref and vd:
        print("\n\n### Der entscheidende Vergleich — Block B\n")
        print("Gleicher Text, gleiche fünf Seeds. Einziger Unterschied: festes")
        print("Embedding gegen beschriebene Stimme. Was VoiceDesign hier mehr")
        print("streut, ist Drift der Identität.\n")
        for label, s in (("sohee  ", ref), ("warm_de", vd)):
            print(f"  {label}  F0 {s['f0_median_hz']:>4.0f}Hz, "
                  f"Spanne {s['f0_spread_semitones']:>4.1f} Halbtöne, "
                  f"Klangfarbe {s['timbre_median_dist']:.3f}, "
                  f"{s['capped']}/{s['n']} gekappt")
        factor = vd["f0_spread_semitones"] / max(ref["f0_spread_semitones"], 1e-6)
        print(f"\n  VoiceDesign streut in der Tonhöhe {factor:.1f}x so weit wie sohee.")

    (OUT / "measurements.json").write_text(
        json.dumps({"groups": results, "clips": per_clip},
                   ensure_ascii=False, indent=2, default=float), encoding="utf-8")
    print(f"\nZahlen je Clip: {OUT / 'measurements.json'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
