"""Mikrofon-Aufnahmen: laden, analysieren, schneiden, pitchen.

Reine Funktionen über numpy-Arrays — kein Modell, keine Dateien. Die
Dateiseite (Rohdatei, Sidecar, Produktion) liegt in `recordings.py`.
"""

from __future__ import annotations

import hashlib
import io
import math
from dataclasses import dataclass
from typing import Any

import numpy as np
import soundfile as sf
from scipy.signal import resample_poly

from .audio import normalize_peak

#: Ausgaberate — dieselbe wie Qwen, damit Aufnahmen und Synthesen im selben
#: Format nebeneinander liegen.
TARGET_SR = 24000

#: Pseudo-Seeds für Aufnahmen. Kandidaten heißen nach ihrem Seed; eine
#: Aufnahme hat keinen, bekommt aber einen aus diesem reservierten Bereich,
#: damit Lock, Promote und Export sie wie jeden Kandidaten behandeln.
#: `render.random_seeds` schließt den Bereich aus.
MIC_SEED_MIN = 1_900_000_000
MIC_SEED_MAX = 2**31 - 1

MAX_UPLOAD_BYTES = 16 * 1024 * 1024
MAX_RECORDING_SECONDS = 30.0
PITCH_MIN = -12
PITCH_MAX = 12

#: Laufzeit-Tonhöhe der App je Fresser — Spiegel von `VoiceStyle.MonsterLow`
#: (0.75) und `VoiceStyle.MonsterHigh` (1.3) in
#: app/src/main/java/app/abcvorschule/speech/VoiceStyle.kt. Wer dort ändert,
#: ändert hier mit; der Editor legt diese Faktoren zum Abhören obendrauf.
APP_MONSTER_PITCH: dict[str, float] = {"left": 0.75, "right": 1.3}

#: Ein-/Ausblende gegen Klicks am Schnitt.
FADE_MS = 5


def semitones_for_factor(factor: float) -> float:
    """MediaPlayer-Pitch-Faktor → Halbtöne (tempoerhaltend, wie in der App)."""
    return 12.0 * math.log2(factor)


@dataclass(frozen=True)
class Edit:
    start: float
    end: float
    pitch_semitones: int = 0
    normalize: bool = True

    @classmethod
    def from_dict(cls, raw: dict[str, Any], *, duration: float) -> "Edit":
        if not isinstance(raw, dict):
            raise ValueError("edit muss ein Objekt sein")
        try:
            start = float(raw["start"])
            end = float(raw["end"])
        except (KeyError, TypeError, ValueError) as exc:
            raise ValueError("edit braucht 'start' und 'end' als Sekunden") from exc
        if not (0.0 <= start < end <= duration + 1e-6):
            raise ValueError(
                f"edit: 0 ≤ start < end ≤ {duration:.3f} s verletzt (start={start}, end={end})")
        pitch_raw = raw.get("pitchSemitones", 0)
        if isinstance(pitch_raw, bool) or not isinstance(pitch_raw, (int, float)) \
                or float(pitch_raw) != int(pitch_raw):
            raise ValueError("edit: pitchSemitones muss eine Ganzzahl sein")
        pitch = int(pitch_raw)
        if not PITCH_MIN <= pitch <= PITCH_MAX:
            raise ValueError(f"edit: pitchSemitones muss zwischen {PITCH_MIN} und {PITCH_MAX} liegen")
        return cls(start=start, end=min(end, duration), pitch_semitones=pitch,
                   normalize=bool(raw.get("normalize", True)))

    def to_dict(self) -> dict[str, Any]:
        return {"start": self.start, "end": self.end,
                "pitchSemitones": self.pitch_semitones, "normalize": self.normalize}


def load_upload(data: bytes) -> tuple[np.ndarray, int]:
    """WAV-Bytes beliebiger Rate/Kanalzahl → float32 mono bei TARGET_SR."""
    if not data:
        raise ValueError("leerer Upload")
    if len(data) > MAX_UPLOAD_BYTES:
        raise ValueError(f"Upload größer als {MAX_UPLOAD_BYTES // (1024 * 1024)} MB")
    try:
        wav, sr = sf.read(io.BytesIO(data), dtype="float32", always_2d=True)
    except (RuntimeError, sf.LibsndfileError, ValueError) as exc:
        raise ValueError(f"Aufnahme nicht lesbar: {exc}") from exc
    if wav.shape[0] == 0:
        raise ValueError("Aufnahme enthält keine Samples")
    if wav.shape[0] / sr > MAX_RECORDING_SECONDS:
        raise ValueError(f"Aufnahme länger als {MAX_RECORDING_SECONDS:.0f} s")
    mono = wav.mean(axis=1).astype(np.float32)
    if sr != TARGET_SR:
        g = math.gcd(int(sr), TARGET_SR)
        mono = resample_poly(mono, TARGET_SR // g, int(sr) // g).astype(np.float32)
    return mono, TARGET_SR


def auto_trim(wav: np.ndarray, sr: int, *, window_ms: int = 10,
              pad_ms: int = 40) -> tuple[float, float]:
    """Sprechanteil relativ zum Rauschboden — ein Mikrofon ist nie digital still.

    RMS in Fenstern; Rauschboden = Median der leisesten 20 % Fenster; Schwelle =
    max(0.01, 4 × Rauschboden). Kein Fenster darüber → ganze Aufnahme.
    """
    duration = len(wav) / sr
    win = max(1, int(sr * window_ms / 1000))
    n = len(wav) // win
    if n == 0:
        return 0.0, duration
    frames = np.asarray(wav[: n * win], dtype=np.float32).reshape(n, win)
    rms = np.sqrt(np.mean(frames ** 2, axis=1))
    quiet = np.sort(rms)[: max(1, n // 5)]
    threshold = max(0.01, 4.0 * float(np.median(quiet)))
    loud = np.where(rms >= threshold)[0]
    if loud.size == 0:
        return 0.0, duration
    pad = pad_ms / 1000
    start = max(0.0, loud[0] * win / sr - pad)
    end = min(duration, (loud[-1] + 1) * win / sr + pad)
    return round(start, 3), round(end, 3)


def peaks(wav: np.ndarray, buckets: int = 600) -> list[float]:
    """Maximalamplitude je Bucket für die Wellenform-Anzeige."""
    if len(wav) == 0:
        return [0.0] * buckets
    edges = np.linspace(0, len(wav), buckets + 1, dtype=int)
    out = []
    for a, b in zip(edges[:-1], edges[1:]):
        out.append(float(np.max(np.abs(wav[a:b]))) if b > a else 0.0)
    return out


def _fade(wav: np.ndarray, sr: int) -> np.ndarray:
    n = min(int(sr * FADE_MS / 1000), len(wav) // 2)
    if n <= 0:
        return wav
    ramp = np.linspace(0.0, 1.0, n, dtype=np.float32)
    out = wav.copy()
    out[:n] *= ramp
    out[-n:] *= ramp[::-1]
    return out


def render(raw: np.ndarray, sr: int, edit: Edit,
           extra_semitones: float = 0.0) -> np.ndarray:
    """Ausschnitt → Pitch (Tempo bleibt) → Fade → Normalisierung. float32."""
    a = int(round(edit.start * sr))
    b = int(round(edit.end * sr))
    cut = np.asarray(raw[a:b], dtype=np.float32)
    semitones = edit.pitch_semitones + extra_semitones
    if abs(semitones) > 1e-6 and len(cut) > 0:
        try:
            import librosa  # lokal: Import dauert, und ohne Pitch braucht es keiner
        except ImportError as exc:
            raise RuntimeError(
                "Tonhöhen-Verschiebung braucht librosa im Qwen-venv — fehlt es, "
                "Pitch auf 0 lassen") from exc

        cut = librosa.effects.pitch_shift(cut, sr=sr, n_steps=float(semitones)).astype(np.float32)
    cut = _fade(cut, sr)
    if edit.normalize:
        cut = normalize_peak(cut)
    return cut


def fingerprint_of(wav: np.ndarray) -> str:
    """Hash des PCM16-Inhalts — so ändert der Export nur nach echter Bearbeitung."""
    pcm = (np.clip(np.asarray(wav, dtype=np.float32), -1.0, 1.0) * 32767).astype("<i2")
    return "mic:" + hashlib.sha256(pcm.tobytes()).hexdigest()[:16]
