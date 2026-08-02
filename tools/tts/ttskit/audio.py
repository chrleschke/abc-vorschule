"""Post-processing between the model output and the file on disk."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import soundfile as sf

#: Version of the post-processing chain below. It is part of the render
#: fingerprint (see plan.fingerprint), so **bump it whenever any constant in
#: this module changes** — the trim threshold, the trim pad, the normalisation
#: target. Without a bump, `render` considers every existing clip up to date
#: and out/audio/ ends up holding a mix of two post-processing generations that
#: can only be told apart by ear.
POSTPROCESS_VERSION = 1


def trim_silence(wav: np.ndarray, sr: int, threshold: float = 0.01,
                 pad_ms: int = 30) -> np.ndarray:
    """Cut leading and trailing silence, keeping a safety pad.

    The pad matters: unvoiced onsets ("Pf", "Sch") sit just below the threshold
    and would otherwise lose their first few milliseconds.
    """
    loud = np.where(np.abs(wav) >= threshold)[0]
    if loud.size == 0:
        return wav
    pad = int(sr * pad_ms / 1000)
    start = max(0, int(loud[0]) - pad)
    end = min(len(wav), int(loud[-1]) + 1 + pad)
    return wav[start:end]


def normalize_peak(wav: np.ndarray, peak_dbfs: float = -1.0) -> np.ndarray:
    """Scale so the loudest sample sits at `peak_dbfs`."""
    peak = float(np.max(np.abs(wav))) if wav.size else 0.0
    if peak == 0.0:
        return wav
    target = 10 ** (peak_dbfs / 20)
    return (wav * (target / peak)).astype(np.float32)


def postprocess(wav: np.ndarray, sr: int, trim: bool, normalize: bool) -> np.ndarray:
    out = np.asarray(wav, dtype=np.float32)
    if trim:
        out = trim_silence(out, sr)
    if normalize:
        out = normalize_peak(out)
    return out


def write_wav(path: Path, wav: np.ndarray, sr: int) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    clipped = np.clip(np.asarray(wav, dtype=np.float32), -1.0, 1.0)
    sf.write(path, clipped, sr, subtype="PCM_16")
