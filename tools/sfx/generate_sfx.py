#!/usr/bin/env python3
"""Erzeugt die Geräusche der App (PRODUCT_PRINCIPLES §7, „Geräusche").

Alles ist synthetisiert — keine Aufnahmen, keine Fremdlizenz, und jeder Klang lässt
sich hier nachlesen und nachjustieren. Nur die Python-Standardbibliothek; `ffmpeg`
mit libopus wandelt die WAVs in Ogg/Opus wie die Sprachclips.

    python3 tools/sfx/generate_sfx.py

schreibt nach app/src/main/assets/sfx/. Leitplanken aus dem Design-Review 2026-09:
kurz (≤ 1,3 s, die meisten < 0,3 s), leise gegenüber der Sprache (Spitze ≤ −8 dBFS),
weich — kein Klang darf nach „falsch" klingen, auch der Rückflug nicht. Ein Kind hört
diese Geräusche Hunderte Male.
"""

import math
import os
import random
import struct
import subprocess
import sys
import tempfile
import wave

RATE = 48000
HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.normpath(os.path.join(HERE, "..", "..", "app", "src", "main", "assets", "sfx"))


def silence(seconds):
    return [0.0] * int(RATE * seconds)


def mix(*tracks):
    n = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(n)]


def at(offset_s, track):
    return silence(offset_s) + track


def release(samples, seconds=0.012):
    """Weich auslaufen lassen: ein hart abgeschnittenes Ende knackt hörbar."""
    n = min(len(samples), int(RATE * seconds))
    for i in range(n):
        samples[-1 - i] *= i / n
    return samples


def tone(freq, seconds, decay, amp=1.0, partials=((1.0, 1.0),), attack=0.004, sweep_to=None):
    """Sinus-Ton mit Obertönen und exponentiellem Ausklang; optional Frequenz-Glissando."""
    out = []
    phase = [0.0] * len(partials)
    n = int(RATE * seconds)
    for i in range(n):
        t = i / RATE
        f = freq if sweep_to is None else freq + (sweep_to - freq) * (t / seconds)
        env = min(1.0, t / attack) * math.exp(-t / decay)
        s = 0.0
        for k, (ratio, gain) in enumerate(partials):
            phase[k] += 2 * math.pi * f * ratio / RATE
            s += gain * math.sin(phase[k])
        out.append(amp * env * s)
    return release(out)


def noise(seconds, decay, amp=1.0, cutoff_from=4000.0, cutoff_to=None, seed=7, bell=False):
    """Gefiltertes Rauschen (zweifacher Einpol-Tiefpass, 12 dB/Oktave — einfach gefiltert
    zischt es); `bell` = an- und abschwellend statt Ausklang."""
    rnd = random.Random(seed)
    out, y, z = [], 0.0, 0.0
    n = int(RATE * seconds)
    for i in range(n):
        t = i / RATE
        cut = cutoff_from if cutoff_to is None else cutoff_from + (cutoff_to - cutoff_from) * (t / seconds)
        a = 1.0 - math.exp(-2 * math.pi * cut / RATE)
        y += a * (rnd.uniform(-1, 1) - y)
        z += a * (y - z)
        env = math.sin(math.pi * t / seconds) ** 2 if bell else math.exp(-t / decay)
        out.append(amp * env * z)
    return release(out)


BELL = ((1.0, 1.0), (2.0, 0.35), (3.01, 0.18), (4.2, 0.08))
SOFT = ((1.0, 1.0), (2.0, 0.2))


def pop():
    # Blasen-Plopp: kurzer Anstieg der Tonhöhe — eine Kugel verlässt das Feld.
    return tone(520, 0.09, 0.028, amp=0.9, partials=SOFT, attack=0.002, sweep_to=1500)


def snap():
    # Holz-Klack beim Einrasten: heller Anschlag plus kurzer Körper.
    return mix(
        noise(0.03, 0.006, amp=0.8, cutoff_from=6000),
        tone(1750, 0.04, 0.01, amp=0.35),
        tone(190, 0.08, 0.025, amp=0.8),
    )


def boing():
    # Weiches Zurückfedern — kein Fehlerton: Tonhöhe schwingt gedämpft nach, leicht aufwärts.
    out, phase = [], 0.0
    n = int(RATE * 0.32)
    for i in range(n):
        t = i / RATE
        f = 240 * (1 + 0.12 * t / 0.32) * (1 + 0.18 * math.sin(2 * math.pi * 11 * t) * math.exp(-t / 0.09))
        phase += 2 * math.pi * f / RATE
        env = min(1.0, t / 0.006) * math.exp(-t / 0.09)
        out.append(0.8 * env * (math.sin(phase) + 0.25 * math.sin(2 * phase)))
    return out


def whoosh():
    # Luftzug, mit dem der Stern losfliegt: Rauschen, dessen Helligkeit ansteigt.
    return noise(0.34, 1.0, amp=1.4, cutoff_from=300, cutoff_to=1800, bell=True, seed=3)


def ding():
    # Der Stern schlägt im Punktestand ein: eine helle Glocke.
    return tone(1318.5, 0.7, 0.18, amp=0.55, partials=BELL)


def chime():
    # Erfolgs-Arpeggio C–E–G–C als Glockenspiel, die Töne klingen ineinander.
    notes = [1046.5, 1318.5, 1568.0, 2093.0]
    return mix(*[at(0.085 * i, tone(f, 0.55, 0.16, amp=0.42 if i < 3 else 0.55, partials=BELL)) for i, f in enumerate(notes)])


def fanfare():
    # Lektions-Ende: G–C–E, dann ein gehaltenes G mit Glocke obendrauf.
    horn = ((1.0, 1.0), (2.0, 0.45), (3.0, 0.22), (4.0, 0.1))
    parts = [
        at(0.00, tone(392.0, 0.18, 0.12, amp=0.34, partials=horn, attack=0.015)),
        at(0.15, tone(523.3, 0.18, 0.12, amp=0.34, partials=horn, attack=0.015)),
        at(0.30, tone(659.3, 0.18, 0.12, amp=0.34, partials=horn, attack=0.015)),
        at(0.45, tone(784.0, 0.85, 0.45, amp=0.38, partials=horn, attack=0.02)),
        at(0.45, tone(1568.0, 0.8, 0.25, amp=0.22, partials=BELL)),
        at(0.45, tone(2093.0, 0.8, 0.2, amp=0.12, partials=BELL)),
    ]
    return mix(*parts)


def tap():
    # Taste des Ziffernblocks: ein weicher, kurzer Tupfer.
    return mix(noise(0.02, 0.004, amp=0.5, cutoff_from=3500, seed=11), tone(620, 0.04, 0.012, amp=0.45))


def blip():
    # Stern im Spurensucher. Tonhöhe setzt die App per Abspielrate (Tonleiter).
    return tone(784.0, 0.12, 0.045, amp=0.8, partials=((1.0, 1.0), (2.0, 0.3), (3.0, 0.1)))


def shuffle():
    # Das Feld mischt: fünf winzige Plopps, aufsteigend, flatternd.
    return mix(*[at(0.045 * i, tone(500 + 140 * i, 0.06, 0.018, amp=0.45, sweep_to=800 + 180 * i)) for i in range(5)])


def blocked():
    # Gesperrt / kein Weiterkommen: ein freundliches, tiefes „dum", kein Summer.
    return tone(175.0, 0.2, 0.06, amp=0.9, partials=((1.0, 1.0), (2.0, 0.25)), attack=0.006)


SOUNDS = {
    "pop": pop,
    "snap": snap,
    "boing": boing,
    "whoosh": whoosh,
    "ding": ding,
    "chime": chime,
    "fanfare": fanfare,
    "tap": tap,
    "blip": blip,
    "shuffle": shuffle,
    "blocked": blocked,
}

PEAK_DBFS = -8.0


def normalize(samples):
    peak = max(abs(s) for s in samples) or 1.0
    target = 10 ** (PEAK_DBFS / 20)
    fade = int(RATE * 0.004)
    out = [s * target / peak for s in samples]
    for i in range(min(fade, len(out))):  # kein Knacken am Ende
        out[-1 - i] *= i / fade
    return out


def write_wav(path, samples):
    with wave.open(path, "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1, min(1, s)) * 32767)) for s in samples))


def main():
    os.makedirs(OUT, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for name, make in SOUNDS.items():
            wav = os.path.join(tmp, name + ".wav")
            write_wav(wav, normalize(make()))
            ogg = os.path.join(OUT, name + ".ogg")
            subprocess.run(
                ["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-c:a", "libopus", "-b:a", "40k", ogg],
                check=True,
            )
            print(f"{name:8s} {os.path.getsize(ogg):6d} B")
    return 0


if __name__ == "__main__":
    sys.exit(main())
