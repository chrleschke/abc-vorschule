# tools/tts/ttskit/recordings.py
"""Dateiseite der Mikrofon-Aufnahmen: Rohdatei, bearbeitete Fassung, Sidecar.

Eine Aufnahme ist ein Kandidat wie jede Probeaufnahme (Radio „Produktion",
👍/👎, Export, Lock) — nur dass ihr „Seed" ein Pseudo-Seed aus dem reservierten
Bereich ist und ihre Bytes vom Menschen statt vom Modell kommen.
"""

from __future__ import annotations

import io
import json
import secrets
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np
import soundfile as sf

from . import mic
from .audio import write_wav
from .models import Clip
from .paths import Paths
from .render import candidate_meta, candidate_seeds, update_candidate_meta
from .store import Profile


def _raw_path(paths: Paths, key: str, seed: int) -> Path:
    return Path(paths.candidates) / key / f"{seed}.raw.wav"


def _load_raw(paths: Paths, key: str, seed: int) -> tuple[np.ndarray, int]:
    path = _raw_path(paths, key, seed)
    if not path.exists():
        raise FileNotFoundError(seed)
    wav, sr = sf.read(path, dtype="float32")
    return np.asarray(wav, dtype=np.float32), int(sr)


def new_recording_seed(paths: Paths, key: str) -> int:
    taken = set(candidate_seeds(paths, key))
    while True:
        seed = mic.MIC_SEED_MIN + secrets.randbelow(mic.MIC_SEED_MAX - mic.MIC_SEED_MIN + 1)
        if seed not in taken:
            return seed


def _write_edit(paths: Paths, clip: Clip, seed: int, raw: np.ndarray, sr: int,
                 edit: mic.Edit) -> tuple[np.ndarray, str]:
    out = mic.render(raw, sr, edit)
    write_wav(Path(paths.candidates) / clip.key / f"{seed}.wav", out, sr)
    return out, mic.fingerprint_of(out)


def store_recording(paths: Paths, clip: Clip, profile: Profile, data: bytes) -> dict[str, Any]:
    """Upload → Rohdatei + Default-Edit (Auto-Trim, Profil-Pitch) + Kandidat."""
    raw, sr = mic.load_upload(data)
    seed = new_recording_seed(paths, clip.key)
    write_wav(_raw_path(paths, clip.key, seed), raw, sr)
    start, end = mic.auto_trim(raw, sr)
    edit = mic.Edit(start=start, end=end, pitch_semitones=profile.mic_pitch_semitones,
                    normalize=True)
    _, fp = _write_edit(paths, clip, seed, raw, sr, edit)
    meta = {
        "source": "mic",
        "createdAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
        "speaker": "mic",
        "text": clip.text,
        "raw": f"{seed}.raw.wav",
        "edit": edit.to_dict(),
        "autoTrim": {"start": start, "end": end},
        "fingerprint": fp,
    }
    (Path(paths.candidates) / clip.key / f"{seed}.json").write_text(
        json.dumps(meta, ensure_ascii=False) + "\n", encoding="utf-8")
    return {"seed": seed, "analysis": recording_info(paths, clip.key, seed)}


def recording_info(paths: Paths, key: str, seed: int) -> dict[str, Any]:
    raw, sr = _load_raw(paths, key, seed)
    meta = candidate_meta(paths, key, seed)
    duration = len(raw) / sr
    return {
        "seed": seed,
        "peaks": mic.peaks(raw),
        "duration": round(duration, 3),
        "sampleRate": sr,
        "edit": meta.get("edit") or mic.Edit(0.0, duration).to_dict(),
        "autoTrim": meta.get("autoTrim") or {"start": 0.0, "end": round(duration, 3)},
    }


def apply_edit(paths: Paths, clip: Clip, seed: int, edit_raw: dict[str, Any]) -> dict[str, Any]:
    """Bearbeitung speichern; ist die Aufnahme Produktion, zieht die mit."""
    raw, sr = _load_raw(paths, clip.key, seed)
    edit = mic.Edit.from_dict(edit_raw, duration=len(raw) / sr)
    out, fp = _write_edit(paths, clip, seed, raw, sr, edit)
    meta = update_candidate_meta(paths, clip.key, seed, edit=edit.to_dict(), fingerprint=fp)
    production = Path(paths.audio) / f"{clip.key}.wav"
    if clip.seed == seed and production.exists():
        write_wav(production, out, sr)
    return meta


def preview_bytes(paths: Paths, key: str, seed: int, edit_raw: dict[str, Any],
                   app_pitch: float | None) -> bytes:
    raw, sr = _load_raw(paths, key, seed)
    edit = mic.Edit.from_dict(edit_raw, duration=len(raw) / sr)
    extra = mic.semitones_for_factor(app_pitch) if app_pitch else 0.0
    out = mic.render(raw, sr, edit, extra_semitones=extra)
    buf = io.BytesIO()
    sf.write(buf, np.clip(out, -1.0, 1.0), sr, format="WAV", subtype="PCM_16")
    return buf.getvalue()
