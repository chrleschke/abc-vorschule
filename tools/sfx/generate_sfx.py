#!/usr/bin/env python3
"""Erzeugt die Geräusche der App (PRODUCT_PRINCIPLES §7, „Geräusche").

Alles ist synthetisiert — keine Aufnahmen, keine Fremdlizenz, und jeder Klang lässt
sich hier nachlesen und nachjustieren. Nur die Python-Standardbibliothek; `ffmpeg`
mit libopus wandelt die WAVs in Ogg/Opus wie die Sprachclips.

    python3 tools/sfx/generate_sfx.py

schreibt nach app/src/main/assets/sfx/. Leitplanken aus dem Design-Review 2026-09:
kurz (≤ 1,3 s, die meisten < 0,3 s), leise gegenüber der Sprache (Spitze ≤ −8 dBFS), tief (Grundtöne 150–800 Hz,
Tiefpass 3 kHz — die erste, hellere Fassung nervte), weich — kein Klang darf nach „falsch" klingen, auch der Rückflug nicht. Ein Kind hört
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


# Klangfarbe (Nutzer-Rückmeldung 2026-09-28: die erste Fassung war zu hoch und
# nervte Eltern schnell): Grundtöne zwischen 150 und 800 Hz, reine Sinus-Töne mit
# höchstens einer leisen Oktave darüber, keine Glocken-Obertöne, Rauschen nur unter
# 2 kHz — und über allem ein Tiefpass (LOWPASS_HZ). Vorbild ist der frühere
# Sinus-Abschlusston, der als `chime` unverändert zurück ist.
SOFT = ((1.0, 1.0), (2.0, 0.15))


def pop():
    # Blasen-Plopp: kurzer, tiefer Anstieg der Tonhöhe — eine Kugel verlässt das Feld.
    return tone(330, 0.09, 0.03, amp=0.9, partials=SOFT, attack=0.003, sweep_to=620)


def snap():
    # Holz-Klack beim Einrasten: weicher Anschlag und kurzer, tiefer Körper.
    return mix(
        noise(0.03, 0.006, amp=0.5, cutoff_from=1600),
        tone(520, 0.04, 0.008, amp=0.3),
        tone(150, 0.08, 0.025, amp=0.9),
    )


def boing():
    # Weiches Zurückfedern — kein Fehlerton: Tonhöhe schwingt gedämpft nach, leicht aufwärts.
    out, phase = [], 0.0
    n = int(RATE * 0.32)
    for i in range(n):
        t = i / RATE
        f = 220 * (1 + 0.12 * t / 0.32) * (1 + 0.18 * math.sin(2 * math.pi * 11 * t) * math.exp(-t / 0.09))
        phase += 2 * math.pi * f / RATE
        env = min(1.0, t / 0.006) * math.exp(-t / 0.09)
        out.append(0.8 * env * (math.sin(phase) + 0.12 * math.sin(2 * phase)))
    return release(out)


def whoosh():
    # Luftzug, mit dem der Stern losfliegt: dunkles Rauschen, das sich leicht aufhellt.
    return noise(0.34, 1.0, amp=1.4, cutoff_from=200, cutoff_to=1000, bell=True, seed=3)


def ding():
    # Der Stern schlägt im Punktestand ein: ein einzelner weicher Ton (E5), kein Glöckchen.
    return tone(659.25, 0.45, 0.14, amp=0.6, partials=SOFT, attack=0.006)


def chime():
    # Runde geschafft: das frühere Sinus-Arpeggio C–E–G–C, Ton für Ton wie vorher
    # (90 ms je Ton, 15 ms Pause, Anstieg 8 %, Ausklang 30 %, letzter Ton lauter).
    notes = [523.25, 659.25, 783.99, 1046.50]
    note_n, gap_n = int(RATE * 0.090), int(RATE * 0.015)
    attack, rel = max(1, int(note_n * 0.08)), max(1, int(note_n * 0.3))
    out = []
    for i, f in enumerate(notes):
        amp = 0.55 if i == len(notes) - 1 else 0.4
        for n in range(note_n):
            env = n / attack if n < attack else (note_n - n) / rel if n > note_n - rel else 1.0
            out.append(amp * env * math.sin(2 * math.pi * f * n / RATE))
        out.extend([0.0] * gap_n)
    return out


def fanfare():
    # Lektions-Ende: G4–C5–E5, dann ein gehaltenes G5 — warm, ohne Glocke obendrauf.
    horn = ((1.0, 1.0), (2.0, 0.25), (3.0, 0.06))
    return mix(
        at(0.00, tone(392.0, 0.2, 0.12, amp=0.4, partials=horn, attack=0.015)),
        at(0.15, tone(523.3, 0.2, 0.12, amp=0.4, partials=horn, attack=0.015)),
        at(0.30, tone(659.3, 0.2, 0.12, amp=0.4, partials=horn, attack=0.015)),
        at(0.45, tone(784.0, 0.85, 0.4, amp=0.45, partials=horn, attack=0.02)),
        at(0.45, tone(392.0, 0.85, 0.4, amp=0.2, partials=SOFT, attack=0.03)),
    )


def tap():
    # Taste des Ziffernblocks: ein weicher, dumpfer Tupfer.
    return mix(noise(0.02, 0.004, amp=0.3, cutoff_from=1400, seed=11), tone(380, 0.05, 0.014, amp=0.6))


def blip():
    # Stern im Spurensucher, wie der frühere Sinus-Blip. Tonhöhe setzt die App per
    # Abspielrate (C5…C6, also genau der alte Tonumfang).
    return tone(784.0, 0.12, 0.045, amp=0.8, partials=SOFT)


def shuffle():
    # Das Feld mischt: fünf winzige, tiefe Plopps, aufsteigend.
    return mix(*[at(0.045 * i, tone(300 + 60 * i, 0.06, 0.02, amp=0.5, sweep_to=480 + 80 * i)) for i in range(5)])


def blocked():
    # Gesperrt / kein Weiterkommen: ein freundliches, tiefes „dum", kein Summer.
    return tone(175.0, 0.2, 0.06, amp=0.9, partials=SOFT, attack=0.006)


def blubb():
    # Tipp während der Ansage: ein kleines, tiefes Blubbern — zwei weiche Bläschen,
    # kurz und dumpf, damit es die Stimme nicht übertönt (gespielt mit 0,35).
    return mix(
        tone(240, 0.07, 0.022, amp=0.9, partials=SOFT, attack=0.003, sweep_to=430),
        at(0.05, tone(300, 0.06, 0.018, amp=0.55, partials=SOFT, attack=0.003, sweep_to=480)),
    )


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
    "blubb": blubb,
}

PEAK_DBFS = -8.0


LOWPASS_HZ = 3000.0


def lowpass(samples, cutoff=LOWPASS_HZ):
    """Zweifacher Einpol-Tiefpass über jeden Klang: nimmt die Schärfe, die Eltern nervt."""
    a = 1.0 - math.exp(-2 * math.pi * cutoff / RATE)
    y = z = 0.0
    out = []
    for s in samples:
        y += a * (s - y)
        z += a * (y - z)
        out.append(z)
    return out


def normalize(samples):
    samples = lowpass(samples)
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
    too_bright = []
    with tempfile.TemporaryDirectory() as tmp:
        for name, make in SOUNDS.items():
            wav = os.path.join(tmp, name + ".wav")
            write_wav(wav, normalize(make()))
            ogg = os.path.join(OUT, name + ".ogg")
            subprocess.run(
                ["ffmpeg", "-loglevel", "error", "-y", "-i", wav, "-c:a", "libopus", "-b:a", "40k", ogg],
                check=True,
            )
            hz = centroid_hz(ogg)
            print(f"{name:8s} {os.path.getsize(ogg):6d} B  Schwerpunkt {hz:5.0f} Hz")
            if hz > MAX_CENTROID_HZ:
                too_bright.append(f"{name} ({hz:.0f} Hz)")
    if too_bright:
        print(f"Zu hell (> {MAX_CENTROID_HZ:.0f} Hz), nervt auf Dauer:", ", ".join(too_bright))
        return 1
    return 0


# Obergrenze für den spektralen Schwerpunkt eines Klangs. Der frühere Sinus-
# Abschlusston liegt bei ~760 Hz; die erste SFX-Fassung lag bis 2,7 kHz und war
# den Eltern zu schrill.
MAX_CENTROID_HZ = 1100.0


def centroid_hz(path):
    """Mittlerer spektraler Schwerpunkt über ffmpeg `aspectralstats`."""
    out = subprocess.run(
        ["ffmpeg", "-hide_banner", "-i", path, "-af",
         "aspectralstats=measure=centroid,ametadata=print:file=-", "-f", "null", "-"],
        capture_output=True, text=True, check=True,
    ).stdout
    values = [float(line.split("=")[1]) for line in out.splitlines() if "centroid=" in line]
    return sum(values) / len(values) if values else 0.0


if __name__ == "__main__":
    sys.exit(main())
