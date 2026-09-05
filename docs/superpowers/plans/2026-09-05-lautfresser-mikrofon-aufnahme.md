# Laut-Fresser Mikrofon-Aufnahme — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Laut-Fresser spricht die echten Laute seiner Grapheme aus von Hand aufgenommenen Clips; das Qwen-Web-Interface bekommt dafür Mikrofon-Aufnahme, Wellenform-Editor (Schnitt, Pitch, Normalisierung) und der App-Index eine Variante `monster`.

**Architecture:** `soundTts` fällt weg; für jedes SoundPairs-Graphem entsteht ein Clip `monster:<sha(lemma)>` mit Text = Lemma. Mikrofon-Aufnahmen sind gewöhnliche Kandidaten mit Pseudo-Seed ≥ 1 900 000 000, Rohdatei `<seed>.raw.wav`, bearbeiteter Datei `<seed>.wav` und Sidecar mit `source: "mic"`. Der Export schreibt `monster`-Clips in `index.json → variants.monster`; die App schlägt bei Monster-Stimme zuerst dort nach und pitcht weiterhin zur Laufzeit (0.75/1.3).

**Tech Stack:** Python 3.12 (FastAPI, numpy, scipy, soundfile, librosa 1.0) im venv `~/qwen-tts-test/.venv`; Vanilla-JS-Web-UI (AudioWorklet, Canvas); Kotlin/Compose-App mit kotlinx.serialization.

**Spec:** `docs/superpowers/specs/2026-09-05-lautfresser-mikrofon-aufnahme-design.md`

## Global Constraints

- Python-Tests: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q` (nie System-Python).
- App-Tests: `./gradlew :app:testDebugUnitTest` (vom Repo-Root; im Worktree `ANDROID_HOME` gesetzt, siehe README).
- `profiles.json` und `locks.json` sind kuratiert: nur die im Plan genannten Einträge ändern, nie automatisiert überschreiben.
- `out/` ist gitignored und wird in diesem Worktree nicht angelegt; niemals `out/` des Haupt-Checkouts anfassen.
- Ausgabeformat aller Clips: 24 kHz, mono, PCM16 (`audio.write_wav`).
- Pseudo-Seeds für Aufnahmen: `MIC_SEED_MIN = 1_900_000_000` bis `2**31 - 1`; `random_seeds` darf diesen Bereich nie liefern.
- Pitch in Halbtönen, Bereich −12…+12; App-Laufzeit-Pitch `{"left": 0.75, "right": 1.3}` (Spiegel von `VoiceStyle`).
- Text im UI und in Kommentaren auf Deutsch, wie im Bestand; Commit-Messages im Stil `feat(tts): …`, mit Trailer `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`.
- Kein neuer Pip-Abhängigkeitswunsch: librosa, scipy, soundfile, numpy sind im venv vorhanden.

---

## Dateiübersicht

| Datei | Verantwortung |
| --- | --- |
| `tools/tts/ttskit/mic.py` (neu) | Reine Signalfunktionen: Upload laden/resampeln, Auto-Trim, Peaks, Render (Schnitt/Pitch/Fade/Normalisierung), Fingerprint, Konstanten |
| `tools/tts/ttskit/recordings.py` (neu) | Dateiseite der Aufnahmen: Pseudo-Seed, Rohdatei/Sidecar schreiben, Edit anwenden, Vorschau |
| `tools/tts/ttskit/store.py` | Profilfelder `source`, `micPitchSemitones` |
| `tools/tts/ttskit/render.py` | `random_seeds` ohne Mic-Bereich, Sidecar-Felder `mic`/`fresh`, Rohdatei löschen, `production_fingerprint` |
| `tools/tts/ttskit/extract.py` | `sound_pair_graphemes`, Monster-Items, `monsterSoundTts` |
| `tools/tts/ttskit/cli.py`, `paths.py` | SoundPairs.kt-Pfad, Grapheme in `load_context` |
| `tools/tts/ttskit/export.py` | `variants.monster` im Index, Mic-Fingerprint |
| `tools/tts/ttskit/server.py` | Recording-Routen, Profil-PUT, `/api/state` |
| `tools/tts/ttskit/static/{app.js,style.css,index.html,recorder-worklet.js}` | Quelle-Umschalter, Aufnahme, Editor, Kandidaten-Zeile |
| `tools/tts/profiles.json`, `locks.json` | monster → mic/−4; fünf soundTts-Locks entfernen |
| `app/src/main/assets/content/atoms.json`, `ContentModels.kt`, `SoundFeederSpeech.kt` | `soundTts` weg, Lemma sprechen |
| `app/src/main/java/app/abcvorschule/speech/{ClipIndex.kt,SpeechController.kt}` | Varianten-Lookup |
| Doku: `tools/tts/README.md`, `docs/PRODUCT_PRINCIPLES.md`, `AGENTS.md`, zwei Design-Docs | Ist-Stand |

---

### Task 1: Signalkette `mic.py`

**Files:**
- Create: `tools/tts/ttskit/mic.py`
- Test: `tools/tts/tests/test_mic.py`

**Interfaces:**
- Produces:
  - `TARGET_SR = 24000`, `MIC_SEED_MIN = 1_900_000_000`, `MIC_SEED_MAX = 2**31 - 1`, `MAX_UPLOAD_BYTES = 16 * 1024 * 1024`, `MAX_RECORDING_SECONDS = 30.0`, `PITCH_MIN = -12`, `PITCH_MAX = 12`, `APP_MONSTER_PITCH = {"left": 0.75, "right": 1.3}`
  - `@dataclass(frozen=True) Edit(start: float, end: float, pitch_semitones: int = 0, normalize: bool = True)` mit `Edit.from_dict(raw: dict, *, duration: float) -> Edit` (ValueError bei Fehlern) und `to_dict() -> dict` (`{"start","end","pitchSemitones","normalize"}`)
  - `load_upload(data: bytes) -> tuple[np.ndarray, int]` — float32 mono bei `TARGET_SR`; ValueError bei unlesbar/leer/länger als `MAX_RECORDING_SECONDS`
  - `auto_trim(wav, sr, *, window_ms=10, pad_ms=40) -> tuple[float, float]`
  - `peaks(wav, buckets=600) -> list[float]`
  - `render(raw, sr, edit: Edit, extra_semitones: float = 0.0) -> np.ndarray`
  - `fingerprint_of(wav) -> str` (`"mic:" + 16 Hex`)
  - `semitones_for_factor(factor: float) -> float`

- [ ] **Step 1: Failing tests schreiben**

```python
# tools/tts/tests/test_mic.py
import io
import numpy as np
import pytest
import soundfile as sf

from ttskit import mic
from ttskit.mic import Edit


def _sine(freq, seconds, sr, amp=0.5):
    t = np.arange(int(seconds * sr)) / sr
    return (amp * np.sin(2 * np.pi * freq * t)).astype(np.float32)


def _wav_bytes(wav, sr, subtype="FLOAT"):
    buf = io.BytesIO()
    sf.write(buf, wav, sr, format="WAV", subtype=subtype)
    return buf.getvalue()


def test_load_upload_mixes_to_mono_and_resamples_to_24k():
    stereo = np.stack([_sine(440, 1.0, 48000), _sine(440, 1.0, 48000)], axis=1)
    wav, sr = mic.load_upload(_wav_bytes(stereo, 48000))
    assert sr == mic.TARGET_SR
    assert wav.ndim == 1 and wav.dtype == np.float32
    assert abs(len(wav) - mic.TARGET_SR) <= 2


def test_load_upload_rejects_garbage_and_empty_and_overlong():
    with pytest.raises(ValueError):
        mic.load_upload(b"nicht wav")
    with pytest.raises(ValueError):
        mic.load_upload(_wav_bytes(np.zeros(0, dtype=np.float32), 48000))
    too_long = np.zeros(int((mic.MAX_RECORDING_SECONDS + 1) * 8000), dtype=np.float32)
    with pytest.raises(ValueError):
        mic.load_upload(_wav_bytes(too_long, 8000))


def test_auto_trim_finds_the_signal_between_noise():
    sr = 24000
    rng = np.random.default_rng(1)
    noise = (rng.standard_normal(sr) * 0.003).astype(np.float32)  # 1 s Rauschboden
    tone = _sine(300, 0.5, sr)
    wav = np.concatenate([noise, tone, noise])
    start, end = mic.auto_trim(wav, sr)
    assert 0.9 <= start <= 1.0            # 40 ms Polster vor 1,0 s
    assert 1.5 <= end <= 1.6              # 40 ms Polster nach 1,5 s


def test_auto_trim_of_silence_is_the_whole_take():
    wav = np.zeros(24000, dtype=np.float32)
    assert mic.auto_trim(wav, 24000) == (0.0, 1.0)


def test_peaks_have_the_requested_length_and_reflect_amplitude():
    wav = np.concatenate([np.zeros(12000, dtype=np.float32), _sine(200, 0.5, 24000, amp=0.8)])
    p = mic.peaks(wav, buckets=100)
    assert len(p) == 100
    assert max(p[:50]) == 0.0
    assert 0.7 <= max(p[50:]) <= 0.8


def test_render_cuts_normalizes_and_fades():
    sr = 24000
    raw = np.concatenate([np.zeros(sr, dtype=np.float32), _sine(300, 1.0, sr, amp=0.2), np.zeros(sr, dtype=np.float32)])
    out = mic.render(raw, sr, Edit(start=1.0, end=2.0, pitch_semitones=0, normalize=True))
    assert abs(len(out) - sr) <= 1
    assert 0.88 <= float(np.max(np.abs(out))) <= 0.9   # −1 dBFS
    assert abs(out[0]) < 0.05 and abs(out[-1]) < 0.05   # Ein-/Ausblende


def test_render_without_normalize_keeps_the_level():
    sr = 24000
    raw = _sine(300, 1.0, sr, amp=0.2)
    out = mic.render(raw, sr, Edit(start=0.0, end=1.0, normalize=False))
    assert 0.19 <= float(np.max(np.abs(out))) <= 0.2


def _dominant_hz(wav, sr):
    spectrum = np.abs(np.fft.rfft(wav * np.hanning(len(wav))))
    return float(np.fft.rfftfreq(len(wav), 1 / sr)[np.argmax(spectrum)])


def test_render_pitch_shift_lowers_frequency_but_keeps_duration():
    sr = 24000
    raw = _sine(440, 1.0, sr)
    out = mic.render(raw, sr, Edit(start=0.0, end=1.0, pitch_semitones=-12, normalize=False))
    assert abs(len(out) - sr) <= sr // 100
    assert 200 <= _dominant_hz(out, sr) <= 240


def test_render_extra_semitones_add_to_the_edit():
    sr = 24000
    raw = _sine(440, 1.0, sr)
    out = mic.render(raw, sr, Edit(start=0.0, end=1.0, pitch_semitones=0, normalize=False),
                     extra_semitones=mic.semitones_for_factor(0.5))
    assert 200 <= _dominant_hz(out, sr) <= 240


def test_semitones_for_factor():
    assert mic.semitones_for_factor(2.0) == pytest.approx(12.0)
    assert mic.semitones_for_factor(0.75) == pytest.approx(-4.98, abs=0.01)


def test_fingerprint_is_stable_and_changes_with_the_audio():
    a = _sine(300, 0.2, 24000)
    assert mic.fingerprint_of(a) == mic.fingerprint_of(a.copy())
    assert mic.fingerprint_of(a).startswith("mic:") and len(mic.fingerprint_of(a)) == 20
    assert mic.fingerprint_of(a) != mic.fingerprint_of(a * 0.5)


def test_edit_from_dict_validates():
    edit = Edit.from_dict({"start": 0.1, "end": 0.5, "pitchSemitones": -4, "normalize": False}, duration=1.0)
    assert edit == Edit(0.1, 0.5, -4, False)
    assert edit.to_dict() == {"start": 0.1, "end": 0.5, "pitchSemitones": -4, "normalize": False}
    for bad in ({"start": 0.5, "end": 0.5}, {"start": 0.0, "end": 1.5},
                {"start": -0.1, "end": 0.5}, {"start": 0.0, "end": 0.5, "pitchSemitones": 13},
                {"start": 0.0, "end": 0.5, "pitchSemitones": 1.5}, {"start": "a", "end": 0.5}):
        with pytest.raises(ValueError):
            Edit.from_dict(bad, duration=1.0)


def test_edit_defaults_pitch_and_normalize():
    assert Edit.from_dict({"start": 0.0, "end": 0.5}, duration=1.0) == Edit(0.0, 0.5, 0, True)
```

- [ ] **Step 2: Tests laufen lassen — müssen fehlschlagen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_mic.py -q`
Expected: `ModuleNotFoundError: No module named 'ttskit.mic'`

- [ ] **Step 3: `mic.py` schreiben**

```python
# tools/tts/ttskit/mic.py
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
        import librosa  # lokal: Import dauert, und ohne Pitch braucht es keiner

        cut = librosa.effects.pitch_shift(cut, sr=sr, n_steps=float(semitones)).astype(np.float32)
    cut = _fade(cut, sr)
    if edit.normalize:
        cut = normalize_peak(cut)
    return cut


def fingerprint_of(wav: np.ndarray) -> str:
    """Hash des PCM16-Inhalts — so ändert der Export nur nach echter Bearbeitung."""
    pcm = (np.clip(np.asarray(wav, dtype=np.float32), -1.0, 1.0) * 32767).astype("<i2")
    return "mic:" + hashlib.sha256(pcm.tobytes()).hexdigest()[:16]
```

- [ ] **Step 4: Tests laufen lassen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_mic.py -q`
Expected: alle PASS (librosa gibt FutureWarnings aus — unschädlich).

- [ ] **Step 5: Commit**

```bash
git add tools/tts/ttskit/mic.py tools/tts/tests/test_mic.py
git commit -m "feat(tts): Signalkette für Mikrofon-Aufnahmen (Laden, Auto-Trim, Peaks, Pitch, Fingerprint)"
```

---

### Task 2: Profilfelder `source` / `micPitchSemitones`, Mic-Seed-Bereich

**Files:**
- Modify: `tools/tts/ttskit/store.py` (Klasse `Profile`, `_profile()`)
- Modify: `tools/tts/ttskit/render.py` (`random_seeds`)
- Modify: `tools/tts/ttskit/server.py` (`api_update_profile`)
- Modify: `tools/tts/profiles.json` (nur Profil `monster`)
- Test: `tools/tts/tests/test_store.py`, `tools/tts/tests/test_render.py`, `tools/tts/tests/test_server.py`

**Interfaces:**
- Produces: `Profile.source: str` (`"tts"` | `"mic"`), `Profile.mic_pitch_semitones: int`; JSON-Schlüssel `source`, `micPitchSemitones`; `store.PROFILE_SOURCES = ("tts", "mic")`.

- [ ] **Step 1: Failing tests**

```python
# tests/test_store.py — anhängen
def test_profile_source_defaults_to_tts_and_roundtrips(tmp_path):
    from ttskit.store import Profile
    p = Profile.from_dict({"label": "x", "speaker": "sohee", "language": "german", "instruct": ""})
    assert p.source == "tts" and p.mic_pitch_semitones == 0
    d = p.to_dict()
    assert d["source"] == "tts" and d["micPitchSemitones"] == 0
    q = Profile.from_dict({**d, "source": "mic", "micPitchSemitones": -4})
    assert q.source == "mic" and q.mic_pitch_semitones == -4


def test_profile_source_and_pitch_are_validated_at_load():
    import pytest
    from ttskit.store import Profile
    base = {"label": "x", "speaker": "sohee", "language": "german", "instruct": ""}
    with pytest.raises(ValueError, match="source"):
        Profile.from_dict({**base, "source": "tape"}, name="p")
    with pytest.raises(ValueError, match="micPitchSemitones"):
        Profile.from_dict({**base, "micPitchSemitones": 13}, name="p")
    with pytest.raises(ValueError, match="micPitchSemitones"):
        Profile.from_dict({**base, "micPitchSemitones": 1.5}, name="p")


def test_shipped_monster_profile_records_by_microphone():
    import json
    from ttskit.paths import Paths
    monster = json.loads(Paths().profiles.read_text())["profiles"]["monster"]
    assert monster["source"] == "mic"
    assert monster["micPitchSemitones"] == -4
```

```python
# tests/test_render.py — anhängen
def test_random_seeds_never_enter_the_microphone_range():
    from ttskit.mic import MIC_SEED_MIN
    from ttskit.render import random_seeds
    seeds = random_seeds(2000)
    assert all(0 <= s < MIC_SEED_MIN for s in seeds)
```

```python
# tests/test_server.py — anhängen
def test_profile_source_and_pitch_are_editable_and_validated(client):
    assert client.put("/api/profiles/phoneme", json={"source": "mic", "micPitchSemitones": -3}).status_code == 200
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    assert raw["profiles"]["phoneme"]["source"] == "mic"
    assert raw["profiles"]["phoneme"]["micPitchSemitones"] == -3
    assert client.put("/api/profiles/phoneme", json={"source": "tape"}).status_code == 422
    assert client.put("/api/profiles/phoneme", json={"micPitchSemitones": 20}).status_code == 422
    state = client.get("/api/state").json()
    assert state["profiles"]["phoneme"]["source"] == "mic"
```

- [ ] **Step 2: Tests laufen — fehlschlagen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_store.py tests/test_render.py tests/test_server.py -q -k "source or microphone or monster_profile_records"`
Expected: FAIL (`AttributeError: source`, Bereichsverletzung, 422 fehlt).

- [ ] **Step 3: `store.py`**

In `store.py` nach `MAX_RANDOM_SEED`:

```python
#: Woher die Aufnahmen eines Profils standardmäßig kommen: Qwen („tts") oder
#: Mikrofon („mic"). Der Umschalter im UI ist damit nur vorbelegt, nicht festgelegt.
PROFILE_SOURCES = ("tts", "mic")
```

`Profile` erweitern:

```python
@dataclass
class Profile:
    label: str
    speaker: str
    language: str
    instruct: str
    sampling: dict[str, Any]
    seed_pool: list[int]
    trim: bool = True
    normalize: bool = True
    #: Default-Quelle neuer Kandidaten (siehe PROFILE_SOURCES). Nicht Teil des
    #: Fingerprints — Mikrofon-Aufnahmen tragen ihren eigenen (mic.fingerprint_of).
    source: str = "tts"
    #: Default-Tonhöhe des Aufnahme-Editors in Halbtönen (mic.PITCH_MIN…PITCH_MAX).
    mic_pitch_semitones: int = 0
```

In `from_dict` vor dem `return cls(...)`:

```python
        source = raw.get("source", "tts")
        if source not in PROFILE_SOURCES:
            raise ValueError(f"{where}profile {name!r} has unknown source {source!r} "
                             f"— allowed: {', '.join(PROFILE_SOURCES)}")
        pitch_raw = raw.get("micPitchSemitones", 0)
        if isinstance(pitch_raw, bool) or not isinstance(pitch_raw, (int, float)) \
                or float(pitch_raw) != int(pitch_raw) or not -12 <= int(pitch_raw) <= 12:
            raise ValueError(f"{where}profile {name!r} has invalid micPitchSemitones "
                             f"{pitch_raw!r} — allowed: whole numbers from -12 to 12")
```

und im `cls(...)`-Aufruf `source=source, mic_pitch_semitones=int(pitch_raw),`. In `to_dict` zwei Zeilen: `"source": self.source, "micPitchSemitones": self.mic_pitch_semitones,`. In `_profile(...)` (Defaults) `"source": "tts", "micPitchSemitones": 0,` ergänzen. `-12`/`12` hier bewusst als Literale, damit `store` nicht `mic` importiert (mic importiert `audio`, nicht `store` — ein Import wäre möglich, aber `store` soll frei von numpy-Abhängigkeiten bleiben).

- [ ] **Step 4: `render.py` — `random_seeds`**

```python
def random_seeds(n: int, exclude: set[int] | None = None) -> list[int]:
    # Der Bereich ab MIC_SEED_MIN gehört den Mikrofon-Aufnahmen (mic.py) —
    # ein Qwen-Kandidat darf nie mit einer Aufnahme kollidieren.
    from .mic import MIC_SEED_MIN

    blocked = set(exclude or ())
    out: list[int] = []
    while len(out) < n:
        candidate = secrets.randbelow(MIC_SEED_MIN)
        if candidate in blocked:
            continue
        blocked.add(candidate)
        out.append(candidate)
    return out
```

- [ ] **Step 5: `server.py` — `api_update_profile`** (vor `if "trim" in body:`)

```python
        if "source" in body:
            if body["source"] not in PROFILE_SOURCES:
                raise HTTPException(
                    status_code=422,
                    detail=f"unbekannte Quelle {body['source']!r}. Erlaubt: "
                           f"{', '.join(PROFILE_SOURCES)}")
            profile.source = body["source"]
        if "micPitchSemitones" in body:
            value = body["micPitchSemitones"]
            if isinstance(value, bool) or not isinstance(value, (int, float)) \
                    or float(value) != int(value) or not PITCH_MIN <= int(value) <= PITCH_MAX:
                raise HTTPException(
                    status_code=422,
                    detail=f"micPitchSemitones muss eine Ganzzahl zwischen {PITCH_MIN} "
                           f"und {PITCH_MAX} sein, nicht {value!r}")
            profile.mic_pitch_semitones = int(value)
```

Imports ergänzen: `from .store import (..., PROFILE_SOURCES, ...)` und `from .mic import PITCH_MIN, PITCH_MAX`.

- [ ] **Step 6: `profiles.json`** — im Profil `monster` nach `"normalize": true` zwei Zeilen: `"source": "mic",` und `"micPitchSemitones": -4`. Nichts anderes anfassen.

- [ ] **Step 7: Alle Python-Tests**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q`
Expected: PASS. Falls `test_every_default_profile_is_complete` o. ä. über die neuen Schlüssel stolpert, den Test um die zwei Felder ergänzen.

- [ ] **Step 8: Commit**

```bash
git add tools/tts/ttskit/store.py tools/tts/ttskit/render.py tools/tts/ttskit/server.py tools/tts/profiles.json tools/tts/tests/
git commit -m "feat(tts): Profile kennen Quelle (tts/mic) und Editor-Pitch; monster nimmt per Mikrofon auf"
```

---

### Task 3: Extract — Monster-Items aus SoundPairs, `soundTts` raus

**Files:**
- Modify: `tools/tts/ttskit/extract.py`, `tools/tts/ttskit/paths.py`, `tools/tts/ttskit/cli.py`
- Test: `tools/tts/tests/test_extract.py`

**Interfaces:**
- Produces: `extract.sound_pair_graphemes(path: Path) -> frozenset[str]`; `extract_items(content_dir, extra_strings=None, blanks=None, monster_graphemes: Iterable[str] | None = None)`; Feld `"monsterSoundTts"` → `"monster"`; Item-ID `atom:<id>:monsterSound`; `Paths.sound_pairs_kt`.

- [ ] **Step 1: Failing tests** (in `tests/test_extract.py`; die alten `soundTts`-Tests bei Zeile ~70–76 ersetzen)

```python
def test_sound_pair_graphemes_are_parsed_from_the_kotlin_table():
    from ttskit.extract import sound_pair_graphemes
    from ttskit.paths import Paths

    graphemes = sound_pair_graphemes(Paths().sound_pairs_kt)
    assert len(graphemes) >= 20
    assert {"S", "Sch", "St", "Sp", "Ei", "Ö"} <= graphemes
    displays = {a["display"] for a in json.loads((CONTENT_DIR / "atoms.json").read_text())["atoms"]
                if a.get("kind") == "letter"}
    assert graphemes <= displays, graphemes - displays


def test_sound_pair_graphemes_reject_an_unparseable_file(tmp_path):
    import pytest
    from ttskit.extract import sound_pair_graphemes

    broken = tmp_path / "SoundPairs.kt"
    broken.write_text("object SoundPairs {}", encoding="utf-8")
    with pytest.raises(ValueError, match="SoundPair"):
        sound_pair_graphemes(broken)


def test_monster_items_carry_the_lemma_for_sound_pair_graphemes_only(content_dir):
    from ttskit.extract import FIELD_TO_PROFILE, profile_for_item

    by_id = {i.id: i for i in extract_items(content_dir, monster_graphemes={"M"})}
    item = by_id["atom:letter-m:monsterSound"]
    assert item.text == "M"
    assert item.field == "monsterSoundTts"
    assert item.label == "M (Monster-Laut)"
    assert profile_for_item(item) == "monster"
    assert FIELD_TO_PROFILE["monsterSoundTts"] == "monster"
    assert "soundTts" not in FIELD_TO_PROFILE
    # Ohne Grapheme kein Monster-Item — und Wort-Atome nie.
    assert "atom:letter-m:monsterSound" not in {i.id for i in extract_items(content_dir)}
    assert "atom:maus:monsterSound" not in {
        i.id for i in extract_items(content_dir, monster_graphemes={"M", "Maus"})}
```

`test_no_text_is_rendered_under_two_profiles_by_accident` anpassen — der Kontext-Lader liefert die Grapheme mit, und `monster` ist eine gewollte Variante:

```python
def test_no_text_is_rendered_under_two_profiles_by_accident():
    """Ein Text, zwei Profile heißt: doppelt rendern, doppelt kuratieren, einer fliegt raus.

    Erlaubt bleibt genau eine Paarung: der Laut `Ei` und das Wort „Ei" klingen
    gleich, ein Clip trägt beide. `monster` ist eine gewollte *Variante* desselben
    Textes (der Fresser spricht das Lemma in eigener Aufnahme) und landet im
    Index unter `variants.monster` — es zählt hier nicht als Kollision.
    """
    from collections import defaultdict

    from ttskit.extract import profile_for_item, sound_pair_graphemes
    from ttskit.paths import Paths

    profiles_by_text = defaultdict(set)
    graphemes = sound_pair_graphemes(Paths().sound_pairs_kt)
    for item in extract_items(CONTENT_DIR, monster_graphemes=graphemes):
        profiles_by_text[item.text].add(profile_for_item(item))
    collisions = {t: sorted(p - {"monster"}) for t, p in profiles_by_text.items()
                  if len(p - {"monster"}) > 1}
    assert collisions == {"Ei": ["phoneme", "word"]}, collisions
```

- [ ] **Step 2: Tests laufen — fehlschlagen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_extract.py -q`
Expected: FAIL (`ImportError: sound_pair_graphemes`).

- [ ] **Step 3: `paths.py`**

```python
    #: Die Paar-Tabelle des Laut-Fressers liegt im Kotlin-Code — kuratiertes
    #: Wissen, bewusst nicht im Content-Pack. Der Extractor liest sie mit.
    sound_pairs_kt: Path = (REPO_ROOT / "app" / "src" / "main" / "java" / "app"
                            / "abcvorschule" / "content" / "SoundPairs.kt")
```

(als weiteres Feld der `Paths`-Dataclass, nach `app_audio_dir`).

- [ ] **Step 4: `extract.py`**

In `FIELD_TO_PROFILE` den Eintrag `"soundTts": "monster"` samt Kommentar ersetzen durch:

```python
    # Der Laut eines Graphems in der Stimme des Laut-Fressers — Text ist das
    # Lemma („S", „Sch"), aufgenommen per Mikrofon (Profil monster, source mic).
    # Bis September 2026 stand hier `soundTts` mit Fake-Aussprachen („sss"),
    # die weder Qwen noch Android-TTS brauchbar sprachen.
    "monsterSoundTts": "monster",
```

Neue Funktion (nach `reads_as_math_task`):

```python
import re

_SOUND_PAIR_RE = re.compile(r'SoundPair\(\s*"([^"]+)"\s*,\s*"([^"]+)"')


def sound_pair_graphemes(path: Path) -> frozenset[str]:
    """Grapheme der Paar-Tabelle aus `SoundPairs.kt` (`SoundPair("S", "Sch", …)`).

    Regex statt Kotlin-Parser: die Tabelle ist eine Liste von Konstruktoraufrufen
    und soll es bleiben. Findet der Ausdruck nichts, hat sich das Format geändert
    — dann lieber laut scheitern, als still ohne Monster-Clips weiterzulaufen.
    """
    text = Path(path).read_text(encoding="utf-8")
    pairs = _SOUND_PAIR_RE.findall(text)
    if not pairs:
        raise ValueError(f"{path}: kein SoundPair(\"…\", \"…\") gefunden — Tabellenformat geändert?")
    return frozenset(g for pair in pairs for g in pair)
```

`extract_items` bekommt den Parameter `monster_graphemes: Iterable[str] | None = None`; in der Atom-Schleife den `soundTts`-Block ersetzen:

```python
    graphemes = frozenset(monster_graphemes or ())
    ...
        if atom.get("kind") == "letter" and atom.get("display") in graphemes:
            add(f"atom:{atom['id']}:monsterSound", atom.get("lemma", ""), "monsterSoundTts",
                "atoms.json", None, f"{atom.get('display', atom['id'])} (Monster-Laut)")
```

(`from typing import Iterable` ergänzen.)

- [ ] **Step 5: `cli.py` — `load_context`**

```python
def load_context(paths: Paths) -> Context:
    extra = read_json(paths.extra_strings)
    blanks: list[str] = []
    graphemes = sound_pair_graphemes(paths.sound_pairs_kt) if paths.sound_pairs_kt.exists() else ()
    items = extract_items(paths.content_dir, extra_strings=extra, blanks=blanks,
                          monster_graphemes=graphemes)
```

(`from .extract import extract_items, sound_pair_graphemes`). Die Existenzprüfung erlaubt Test-Fixtures ohne Kotlin-Datei (Server-Tests bauen einen eigenen `root`).

- [ ] **Step 6: Alle Tests**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add tools/tts/ttskit/extract.py tools/tts/ttskit/paths.py tools/tts/ttskit/cli.py tools/tts/tests/test_extract.py
git commit -m "feat(tts): Monster-Laute sind das Lemma der SoundPairs-Grapheme, kein soundTts mehr"
```

---

### Task 4: Locks bereinigen

**Files:**
- Modify: `tools/tts/locks.json`

- [ ] **Step 1: Die fünf verwaisten Locks entfernen** (per Python, damit Formatierung und Sortierung wie `Locks.save` bleiben):

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python - <<'EOF'
from pathlib import Path
from ttskit.store import Locks
p = Path("locks.json")
locks = Locks.load(p)
for key in ("monster:0e07cf830957", "monster:103c54b6c5b1", "monster:12b0f0dcaefb",
            "monster:1cf0cca6ed0c", "monster:a871c47a7f48"):
    assert locks.get(key) is not None, key
    locks.remove(key)
assert locks.get("monster:64f570f6b0e3").source_text == "Bäh!"
assert locks.get("monster:c7497ed84146").source_text == "Mmmmh!"
locks.save(p)
EOF
git diff --stat tools/tts/locks.json
```

Expected: nur Löschungen dieser fünf Blöcke; `Bäh!`/`Mmmmh!` unverändert. Prüfen mit `git diff tools/tts/locks.json | grep '^[-+]' | grep -c '^-  *"monster'` → 5 Schlüsselzeilen.

- [ ] **Step 2: Commit**

```bash
git add tools/tts/locks.json
git commit -m "chore(tts): verwaiste soundTts-Locks des Profils monster entfernen (Bäh!/Mmmmh! bleiben)"
```

---

### Task 5: Aufnahmen speichern und bearbeiten — `recordings.py` + Server-Routen

**Files:**
- Create: `tools/tts/ttskit/recordings.py`
- Modify: `tools/tts/ttskit/render.py` (`candidate_infos`, `delete_candidate_wav`, neu `production_fingerprint`)
- Modify: `tools/tts/ttskit/server.py` (Routen, `api_promote`, `/api/state`)
- Test: `tools/tts/tests/test_server.py`, `tools/tts/tests/test_render.py`

**Interfaces:**
- Produces (`recordings.py`):
  - `new_recording_seed(paths, key) -> int`
  - `store_recording(paths, clip, profile, data: bytes) -> dict` → `{"seed", "analysis"}`; legt `<seed>.raw.wav`, `<seed>.wav`, `<seed>.json` an
  - `recording_info(paths, key, seed) -> dict` → `{"seed","peaks","duration","sampleRate","edit","autoTrim"}`; `FileNotFoundError` ohne Rohdatei
  - `apply_edit(paths, clip, seed, edit_raw: dict) -> dict` (Sidecar), zieht Produktion mit
  - `preview_bytes(paths, key, seed, edit_raw: dict, app_pitch: float | None) -> bytes`
- Produces (`render.py`): `production_fingerprint(paths, clip, profile) -> str`; Kandidaten-Infos mit `"mic": bool`, `fresh: True` bei mic.
- Sidecar-Schema: siehe Spec §3.3 (`source`, `createdAt`, `speaker: "mic"`, `text`, `edit`, `autoTrim`, `fingerprint`, `raw`).

- [ ] **Step 1: Failing tests** (`tests/test_server.py` anhängen)

```python
def _upload_wav(seconds=1.0, sr=48000, freq=330.0):
    import io
    import soundfile as sf
    t = np.arange(int(seconds * sr)) / sr
    tone = (0.4 * np.sin(2 * np.pi * freq * t)).astype(np.float32)
    pad = np.zeros(int(0.3 * sr), dtype=np.float32)
    buf = io.BytesIO()
    sf.write(buf, np.concatenate([pad, tone, pad]), sr, format="WAV", subtype="FLOAT")
    return buf.getvalue()


def _first_key(client):
    return client.get("/api/state").json()["clips"][0]["key"]


def _upload(client, key, **kw):
    r = client.post(f"/api/clips/{key}/recordings", content=_upload_wav(**kw),
                    headers={"Content-Type": "audio/wav"})
    assert r.status_code == 201, r.text
    return r.json()


def test_uploading_a_recording_creates_a_microphone_candidate(client):
    from ttskit.mic import MIC_SEED_MIN
    key = _first_key(client)
    body = _upload(client, key)
    seed = body["seed"]
    assert seed >= MIC_SEED_MIN
    folder = client.paths.candidates / key
    assert (folder / f"{seed}.raw.wav").exists()
    assert (folder / f"{seed}.wav").exists()
    meta = json.loads((folder / f"{seed}.json").read_text())
    assert meta["source"] == "mic" and meta["speaker"] == "mic"
    assert meta["fingerprint"].startswith("mic:")
    assert 0.2 <= meta["edit"]["start"] <= 0.3 and meta["edit"] == {
        **meta["edit"], "normalize": True}
    assert meta["autoTrim"] == {"start": meta["edit"]["start"], "end": meta["edit"]["end"]}
    clip = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    cand = next(c for c in clip["candidates"] if c["seed"] == seed)
    assert cand["mic"] is True and cand["fresh"] is True and cand["speaker"] == "mic"
    assert client.get(f"/candidates/{key}/{seed}.wav").status_code == 200


def test_recording_default_pitch_comes_from_the_profile(client):
    key = _first_key(client)
    profile = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)["profile"]
    client.put(f"/api/profiles/{profile}", json={"micPitchSemitones": -4})
    seed = _upload(client, key)["seed"]
    meta = json.loads((client.paths.candidates / key / f"{seed}.json").read_text())
    assert meta["edit"]["pitchSemitones"] == -4


def test_recording_info_ships_peaks_and_edit(client):
    key = _first_key(client)
    seed = _upload(client, key)["seed"]
    info = client.get(f"/api/clips/{key}/recordings/{seed}").json()
    assert len(info["peaks"]) == 600
    assert info["sampleRate"] == 24000
    assert 1.5 <= info["duration"] <= 1.65
    assert set(info["edit"]) == {"start", "end", "pitchSemitones", "normalize"}
    assert client.get(f"/api/clips/{key}/recordings/424242").status_code == 404


def test_editing_a_recording_rerenders_and_follows_into_production(client):
    import soundfile as sf
    key = _first_key(client)
    seed = _upload(client, key)["seed"]
    folder = client.paths.candidates / key
    before = json.loads((folder / f"{seed}.json").read_text())["fingerprint"]
    assert client.post(f"/api/clips/{key}/promote", json={"seed": seed}).json()["verified"] is True
    r = client.put(f"/api/clips/{key}/recordings/{seed}",
                   json={"start": 0.3, "end": 0.8, "pitchSemitones": 0, "normalize": False})
    assert r.status_code == 200, r.text
    meta = json.loads((folder / f"{seed}.json").read_text())
    assert meta["edit"]["end"] == 0.8 and meta["fingerprint"] != before
    data, sr = sf.read(folder / f"{seed}.wav")
    assert abs(len(data) / sr - 0.5) < 0.02
    prod, _ = sf.read(client.paths.audio / f"{key}.wav")
    assert len(prod) == len(data)


def test_invalid_edits_are_422(client):
    key = _first_key(client)
    seed = _upload(client, key)["seed"]
    for bad in ({"start": 0.9, "end": 0.2}, {"start": 0.0, "end": 99.0},
                {"start": 0.0, "end": 0.5, "pitchSemitones": 13}):
        assert client.put(f"/api/clips/{key}/recordings/{seed}", json=bad).status_code == 422


def test_preview_returns_wav_and_persists_nothing(client):
    key = _first_key(client)
    seed = _upload(client, key)["seed"]
    folder = client.paths.candidates / key
    before = (folder / f"{seed}.json").read_text()
    r = client.post(f"/api/clips/{key}/recordings/{seed}/preview",
                    json={"edit": {"start": 0.3, "end": 0.6}, "appPitch": 0.75})
    assert r.status_code == 200 and r.content[:4] == b"RIFF"
    assert (folder / f"{seed}.json").read_text() == before


def test_bad_uploads_are_422(client):
    key = _first_key(client)
    r = client.post(f"/api/clips/{key}/recordings", content=b"kein wav",
                    headers={"Content-Type": "audio/wav"})
    assert r.status_code == 422


def test_deleting_a_recording_removes_the_raw_take_too(client):
    key = _first_key(client)
    seed = _upload(client, key)["seed"]
    assert client.delete(f"/api/clips/{key}/candidates/{seed}").status_code == 200
    folder = client.paths.candidates / key
    assert not (folder / f"{seed}.raw.wav").exists()
    assert not (folder / f"{seed}.json").exists()


def test_state_ships_the_app_monster_pitch(client):
    assert client.get("/api/state").json()["appMonsterPitch"] == {"left": 0.75, "right": 1.3}
```

`tests/test_render.py` anhängen:

```python
def test_production_fingerprint_prefers_the_microphone_sidecar(setup):
    import json
    from dataclasses import replace
    from ttskit.plan import fingerprint
    from ttskit.render import production_fingerprint
    paths, clips, profiles = setup.paths, setup.clips, setup.profiles
    clip = clips[0]
    profile = profiles.profiles[clip.profile]
    assert production_fingerprint(paths, clip, profile) == fingerprint(clip, profile)
    folder = paths.candidates / clip.key
    folder.mkdir(parents=True, exist_ok=True)
    (folder / f"{clip.seed}.json").write_text(json.dumps(
        {"source": "mic", "fingerprint": "mic:abc"}), encoding="utf-8")
    assert production_fingerprint(paths, clip, profile) == "mic:abc"
```

(Die `setup`-Fixture in `test_render.py` heißt so — vorher prüfen, welche Attribute sie trägt, und den Test daran anpassen: `grep -n "def setup" -A 25 tests/test_render.py`.)

- [ ] **Step 2: Tests laufen — fehlschlagen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_server.py tests/test_render.py -q -k "recording or upload or preview or app_monster or production_fingerprint"`
Expected: 404/AttributeError.

- [ ] **Step 3: `render.py` ergänzen**

In `candidate_infos` das Dict erweitern:

```python
        is_mic = meta.get("source") == "mic"
        infos.append({
            "seed": seed,
            # Eine Aufnahme kann nicht „veraltet" sein — sie hängt an keiner
            # Profil-Einstellung. Immer frisch, damit kein „⚠️ alt" erscheint.
            "fresh": True if is_mic else (None if recorded is None else recorded == current),
            "createdAt": meta.get("createdAt"),
            "speaker": meta.get("speaker"),
            "text": meta.get("text"),
            "good": meta.get("rating") == "good",
            "mic": is_mic,
        })
```

In `clip_audio_list` beim Nachbau-Eintrag `"mic": False` ergänzen. In `delete_candidate_wav` nach `wav.unlink()`:

```python
    (paths.candidates / clip.key / f"{seed}.raw.wav").unlink(missing_ok=True)
```

Neue Funktion (nach `candidate_fingerprint`):

```python
def production_fingerprint(paths: Paths, clip: Clip, profile: Profile) -> str:
    """Fingerprint der Produktion für Export und Promote.

    Für Qwen-Clips `plan.fingerprint`; für eine Mikrofon-Aufnahme der Hash der
    bearbeiteten Audiodatei aus dem Sidecar — nur der ändert sich, wenn jemand
    neu schneidet oder pitcht, und nur dann soll der Export neu encodieren.
    """
    meta = candidate_meta(paths, clip.key, clip.seed)
    if meta.get("source") == "mic" and isinstance(meta.get("fingerprint"), str):
        return meta["fingerprint"]
    return fingerprint(clip, profile)
```

- [ ] **Step 4: `recordings.py`**

```python
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
```

- [ ] **Step 5: Server-Routen** (in `create_app`, vor `@app.put("/api/clips/{key}/candidates/{seed}/rating")`)

```python
    @app.post("/api/clips/{key}/recordings", status_code=201)
    async def api_upload_recording(key: str, request: Request) -> dict[str, Any]:
        """Mikrofon-Aufnahme aus dem Browser — WAV-Bytes im Body, kein Modell nötig."""
        ctx, clip = clip_by_key(key)
        data = await request.body()
        try:
            return store_recording(paths, clip, ctx.profiles.profiles[clip.profile], data)
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc

    @app.get("/api/clips/{key}/recordings/{seed}")
    def api_recording_info(key: str, seed: int) -> dict[str, Any]:
        clip_by_key(key)
        try:
            return recording_info(paths, key, seed)
        except FileNotFoundError:
            raise HTTPException(status_code=404, detail=f"keine Rohaufnahme {seed} für {key!r}")

    @app.put("/api/clips/{key}/recordings/{seed}")
    def api_edit_recording(key: str, seed: int, body: dict = Body(...)) -> dict[str, Any]:
        ctx, clip = clip_by_key(key)
        try:
            meta = apply_edit(paths, clip, seed, body)
        except FileNotFoundError:
            raise HTTPException(status_code=404, detail=f"keine Rohaufnahme {seed} für {key!r}")
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        return {"ok": "edited", "edit": meta["edit"], "fingerprint": meta["fingerprint"]}

    @app.post("/api/clips/{key}/recordings/{seed}/preview")
    def api_preview_recording(key: str, seed: int, body: dict = Body(...)) -> Response:
        clip_by_key(key)
        app_pitch = body.get("appPitch")
        if app_pitch is not None and (isinstance(app_pitch, bool)
                                      or not isinstance(app_pitch, (int, float))
                                      or not 0.25 <= app_pitch <= 4.0):
            raise HTTPException(status_code=422, detail="appPitch muss ein Faktor 0,25–4 sein")
        try:
            data = preview_bytes(paths, key, seed, body.get("edit") or {}, app_pitch)
        except FileNotFoundError:
            raise HTTPException(status_code=404, detail=f"keine Rohaufnahme {seed} für {key!r}")
        except ValueError as exc:
            raise HTTPException(status_code=422, detail=str(exc)) from exc
        return Response(content=data, media_type="audio/wav")
```

Imports: `from fastapi import Body, FastAPI, HTTPException, Request` und `from fastapi.responses import (..., Response)`; `from .recordings import apply_edit, preview_bytes, recording_info, store_recording`; `from .mic import APP_MONSTER_PITCH, PITCH_MIN, PITCH_MAX`; `from .render import (..., production_fingerprint)`.

In `api_state` im Rückgabe-Dict: `"appMonsterPitch": dict(APP_MONSTER_PITCH),`.

In `api_promote` den `verified`-Block ersetzen:

```python
        if source.exists():
            profile = ctx.profiles.profiles[clip.profile]
            meta = candidate_meta(paths, key, seed)
            if meta.get("source") == "mic":
                # Eine Aufnahme hängt an keiner Profil-Einstellung — nichts zu prüfen.
                verified = True
            else:
                target = fingerprint(replace(clip, seed=seed), profile)
                verified = candidate_fingerprint(paths, key, seed) == target
        else:
            verified = False
```

- [ ] **Step 6: Tests**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add tools/tts/ttskit/recordings.py tools/tts/ttskit/render.py tools/tts/ttskit/server.py tools/tts/tests/
git commit -m "feat(tts): Mikrofon-Aufnahmen als Kandidaten — Upload, Analyse, Schnitt/Pitch, Vorschau"
```

---

### Task 6: Export — `variants.monster` und Mic-Fingerprint

**Files:**
- Modify: `tools/tts/ttskit/export.py`
- Test: `tools/tts/tests/test_export.py`

**Interfaces:**
- Produces: `index.json` mit `"variants": {"monster": {text: entry}}`; `VARIANT_PROFILES = {"monster": "monster"}` (Profil → Variantenname).

- [ ] **Step 1: Failing tests** (Vorbild für Fixture-Aufbau: `test_exports_locked_rendered_clip_as_ogg`, `test_collision_prefers_phoneme_over_word` in derselben Datei — Helfer von dort wiederverwenden, hier `_render_and_lock(paths, clip)` genannt; falls der Helfer anders heißt, den Namen übernehmen.)

```python
def test_monster_clips_go_to_the_variants_block_and_letters_stay_in_clips(tmp_path, content_dir):
    """„S" liegt als phoneme in `clips` UND als monster in `variants.monster`; „Bäh!"
    hat kein anderes Profil und steht zusätzlich in `clips`."""
    paths = _paths(tmp_path, content_dir)  # bestehender Helfer der Datei
    extra = {"version": 1, "strings": [
        {"id": "feederYuck", "text": "Bäh!", "field": "monsterTts"}]}
    paths.extra_strings.write_text(json.dumps(extra), encoding="utf-8")
    # Der Server-Kontext liest SoundPairs.kt aus dem Repo; für den Test eine
    # Mini-Datei mit dem Fixture-Buchstaben M.
    kt = tmp_path / "SoundPairs.kt"
    kt.write_text('SoundPair("M", "A", SoundPairTier.Contrast),', encoding="utf-8")
    paths.sound_pairs_kt = kt
    ctx = load_context(paths)
    for clip in ctx.clips:
        if clip.profile in ("monster", "phoneme") and clip.source_text in ("M", "Bäh!"):
            _render_and_lock(paths, clip)
    export_to_app(paths)
    index = json.loads((paths.app_audio_dir / "index.json").read_text())
    assert index["clips"]["M"]["profile"] == "phoneme"
    assert index["variants"]["monster"]["M"]["profile"] == "monster"
    assert index["variants"]["monster"]["M"]["file"].startswith("monster_")
    assert index["variants"]["monster"]["Bäh!"]["profile"] == "monster"
    assert index["clips"]["Bäh!"]["profile"] == "monster"
    files = {p.name for p in paths.app_audio_dir.glob("*.ogg")}
    assert index["variants"]["monster"]["M"]["file"] in files


def test_microphone_fingerprint_drives_reencoding(tmp_path, content_dir):
    paths = _paths(tmp_path, content_dir)
    ctx = load_context(paths)
    clip = ctx.clips[0]
    _render_and_lock(paths, clip)
    folder = paths.candidates / clip.key
    folder.mkdir(parents=True)
    (folder / f"{clip.seed}.json").write_text(json.dumps(
        {"source": "mic", "fingerprint": "mic:0001"}), encoding="utf-8")
    export_to_app(paths)
    index = json.loads((paths.app_audio_dir / "index.json").read_text())
    entry = next(e for e in index["clips"].values() if e["file"].startswith(clip.profile))
    assert entry["fingerprint"] == "mic:0001"
    assert export_to_app(paths).unchanged == [clip.key]
    (folder / f"{clip.seed}.json").write_text(json.dumps(
        {"source": "mic", "fingerprint": "mic:0002"}), encoding="utf-8")
    assert export_to_app(paths).exported == [clip.key]
```

- [ ] **Step 2: Tests laufen — fehlschlagen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_export.py -q`
Expected: `KeyError: 'variants'`, Fingerprint-Assertion.

- [ ] **Step 3: `export.py`**

Nach `PROFILE_PRIORITY`:

```python
#: Profile, deren Clips als *Variante* eines Textes gelten: die App sucht sie
#: unter `variants.<name>.<text>` (ClipIndex.lookup(text, variant)). Sie
#: kollidieren nicht mit dem normalen Clip desselben Textes — „S" darf als
#: phoneme in `clips` und als monster in `variants.monster` stehen.
VARIANT_PROFILES: dict[str, str] = {"monster": "monster"}
```

Import: `from .render import production_fingerprint` (und `fingerprint` aus `plan` entfernen, falls nicht mehr genutzt). In `export_to_app`:

```python
    planned = []
    for clip in sorted(exportable, key=lambda c: c.key):
        profile = ctx.profiles.profiles[clip.profile]
        planned.append((clip, asset_name(clip.key), production_fingerprint(paths, clip, profile)))

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
        ... (bestehende Kollisionslogik unverändert)
```

Nach der Schleife über `retained_entries`:

```python
    # Varianten-Clips stehen zusätzlich in `clips`, wenn dort kein anderes Profil
    # den Text trägt — „Bäh!" muss auch für eine Ansage in Normalstimme
    # auffindbar sein. Ein phoneme-„S" gewinnt dagegen immer.
    for by_text in variants.values():
        for text, entry in by_text.items():
            index.setdefault(text, entry)

    indexed_files = {e["file"] for e in index.values()} | {
        e["file"] for by_text in variants.values() for e in by_text.values()}
```

Beim Zurückbehalten (`retained_entries`) auch Varianten aus dem alten Index übernehmen: `_previous_index` liest zusätzlich `payload.get("variants", {})` und liefert deren Dateien mit (gleiche Struktur `file → (text, entry)`); beim Schreiben:

```python
    payload = {"version": 1,
               "clips": {t: index[t] for t in sorted(index)},
               "variants": {v: {t: by_text[t] for t in sorted(by_text)}
                            for v, by_text in sorted(variants.items())}}
```

- [ ] **Step 4: Tests**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q`
Expected: PASS (bestehende Export-Tests vergleichen ggf. den ganzen Index — dort `"variants": {}` ergänzen).

- [ ] **Step 5: Commit**

```bash
git add tools/tts/ttskit/export.py tools/tts/tests/test_export.py
git commit -m "feat(tts): Export schreibt monster-Clips als Variante in index.json und nutzt den Aufnahme-Fingerprint"
```

---

### Task 7: Web-UI — Quelle umschalten und aufnehmen

**Files:**
- Create: `tools/tts/ttskit/static/recorder-worklet.js`
- Modify: `tools/tts/ttskit/server.py` (Route `/recorder-worklet.js`)
- Modify: `tools/tts/ttskit/static/app.js`, `style.css`
- Test: `tools/tts/tests/test_server.py` (Route), manuelle Prüfung im Browser

**Interfaces:**
- Produces (app.js): `clipSource(clip) -> "tts"|"mic"`, `setClipSource(clip, source)`, `recorderPanelHtml(clip)`, `startRecording(clip)`, `stopRecording()`, `encodeWav(chunks, sampleRate) -> Blob`; `state.recorder`, `state.editor` (Task 8 füllt ihn).

- [ ] **Step 1: Route-Test**

```python
def test_recorder_worklet_is_served(client):
    r = client.get("/recorder-worklet.js")
    assert r.status_code == 200
    assert "registerProcessor" in r.text
```

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_server.py -q -k worklet` → FAIL 404.

- [ ] **Step 2: Worklet und Route**

```js
// tools/tts/ttskit/static/recorder-worklet.js
// Sammelt Float32-PCM des ersten Kanals und schickt jeden Block an den
// Hauptthread. Kein MediaRecorder: der liefert Opus/WebM, das der Server nur
// mit ffmpeg lesen könnte — WAV kann er direkt.
class RecorderProcessor extends AudioWorkletProcessor {
  process(inputs) {
    const channel = inputs[0] && inputs[0][0];
    if (channel) this.port.postMessage(channel.slice());
    return true;
  }
}
registerProcessor("recorder", RecorderProcessor);
```

`server.py` neben `/app.js`:

```python
    @app.get("/recorder-worklet.js")
    def recorder_worklet() -> FileResponse:
        return FileResponse(STATIC / "recorder-worklet.js", media_type="application/javascript")
```

- [ ] **Step 3: app.js — Quelle und Aufnahme**

In `state` ergänzen: `recorder: null, editor: null, appMonsterPitch: { left: 0.75, right: 1.3 }`. In `refresh()` nach dem State-Laden: `state.appMonsterPitch = body.appMonsterPitch || state.appMonsterPitch;`.

Neue Helfer (nach `useTopSeeds`):

```js
// Quelle neuer Kandidaten: Qwen („tts") oder Mikrofon („mic"). Vorbelegt aus
// dem Profil, pro Clip im Browser umschaltbar und gemerkt.
const clipSource = (clip) =>
  readLocal(`ttsSource:${clip.key}`, null) || state.profiles[clip.profile].source || "tts";
const setClipSource = (clip, source) => writeLocal(`ttsSource:${clip.key}`, source);

function sourceSwitchHtml(clip) {
  const source = clipSource(clip);
  return `
    <p class="source-switch">Quelle:
      <label class="inline"><input type="radio" name="source" value="tts"
        ${source === "tts" ? "checked" : ""} /> 🎲 TTS</label>
      <label class="inline"><input type="radio" name="source" value="mic"
        ${source === "mic" ? "checked" : ""} /> 🎙 Mikrofon</label>
      <span class="muted small">(Profil „${escapeHtml(clip.profile)}“ steht auf
        ${state.profiles[clip.profile].source === "mic" ? "Mikrofon" : "TTS"})</span>
    </p>`;
}

function recorderPanelHtml(clip) {
  const rec = state.recorder;
  const active = rec && rec.clipKey === clip.key;
  return `
    <div class="generate-row recorder-row">
      <button id="btn-record" class="primary ${active ? "recording" : ""}">
        ${active ? "■ Stopp" : "● Aufnehmen"}</button>
      <span id="rec-level" class="rec-level"><span id="rec-level-bar"></span></span>
      <span id="rec-status" class="muted small">${active
        ? "Aufnahme läuft — Stopp lädt hoch und öffnet den Editor"
        : "Mono, ohne Rauschunterdrückung; automatischer Stopp nach 30 s"}</span>
    </div>
    <div id="editor-slot"></div>`;
}
```

In `candidatesCardHtml` direkt vor `<div class="generate-row">`: `${sourceSwitchHtml(clip)}`; die bestehende Generate-Zeile in `${clipSource(clip) === "tts" ? \`…bestehende generate-row…\` : recorderPanelHtml(clip)}` einbetten. Im Mikrofon-Modus `<select id="clip-speaker" … disabled title="Für Aufnahmen bedeutungslos">`.

Aufnahme-Logik (neuer Abschnitt `// ------------------------------------------------ Mikrofon`):

```js
function encodeWav(chunks, sampleRate) {
  const length = chunks.reduce((n, c) => n + c.length, 0);
  const buffer = new ArrayBuffer(44 + length * 4);
  const view = new DataView(buffer);
  const ascii = (offset, text) => [...text].forEach((ch, i) => view.setUint8(offset + i, ch.charCodeAt(0)));
  ascii(0, "RIFF"); view.setUint32(4, 36 + length * 4, true); ascii(8, "WAVE");
  ascii(12, "fmt "); view.setUint32(16, 16, true); view.setUint16(20, 3, true); // IEEE float
  view.setUint16(22, 1, true); view.setUint32(24, sampleRate, true);
  view.setUint32(28, sampleRate * 4, true); view.setUint16(32, 4, true); view.setUint16(34, 32, true);
  ascii(36, "data"); view.setUint32(40, length * 4, true);
  let offset = 44;
  for (const chunk of chunks) {
    for (const sample of chunk) { view.setFloat32(offset, sample, true); offset += 4; }
  }
  return new Blob([buffer], { type: "audio/wav" });
}

const MAX_RECORDING_SECONDS = 30;

async function startRecording(clip) {
  const stream = await navigator.mediaDevices.getUserMedia({ audio: {
    channelCount: 1, echoCancellation: false, noiseSuppression: false, autoGainControl: false,
  } });
  const ctx = new AudioContext();
  await ctx.audioWorklet.addModule("/recorder-worklet.js");
  const source = ctx.createMediaStreamSource(stream);
  const node = new AudioWorkletNode(ctx, "recorder", { numberOfInputs: 1, numberOfOutputs: 0 });
  const rec = { clipKey: clip.key, stream, ctx, node, chunks: [], samples: 0 };
  node.port.onmessage = (event) => {
    rec.chunks.push(event.data);
    rec.samples += event.data.length;
    let peak = 0;
    for (const s of event.data) peak = Math.max(peak, Math.abs(s));
    const bar = el("rec-level-bar");
    if (bar) bar.style.width = `${Math.min(100, Math.round(peak * 100))}%`;
    if (rec.samples / ctx.sampleRate >= MAX_RECORDING_SECONDS) stopRecording().catch(showError);
  };
  source.connect(node);
  state.recorder = rec;
  redrawDetail();
}

async function stopRecording() {
  const rec = state.recorder;
  if (!rec) return;
  state.recorder = null;
  rec.node.port.onmessage = null;
  rec.node.disconnect();
  rec.stream.getTracks().forEach((t) => t.stop());
  await rec.ctx.close();
  const blob = encodeWav(rec.chunks, rec.ctx.sampleRate);
  const response = await fetch(`/api/clips/${encodeURIComponent(rec.clipKey)}/recordings`, {
    method: "POST", headers: { "Content-Type": "audio/wav" }, body: blob,
  });
  if (!response.ok) {
    const detail = (await response.json().catch(() => ({}))).detail;
    throw new Error(detail || `${response.status} ${response.statusText}`);
  }
  const result = await response.json();
  await refresh({ keepDetail: true });
  await openEditor(rec.clipKey, result.seed);   // Task 8
  showBanner(`Aufnahme gespeichert (Seed ${result.seed}) — jetzt schneiden, dann „Übernehmen“.`, "ok");
}
```

In `renderDetail` nach `wireCandidateHandlers(clip)`:

```js
  el("detail").querySelectorAll('input[name="source"]').forEach((radio) => {
    radio.onchange = () => { setClipSource(clip, radio.value); renderDetail(clip.key); };
  });
  const recordButton = el("btn-record");
  if (recordButton) {
    recordButton.onclick = guard(async () => {
      if (state.recorder) await stopRecording();
      else await startRecording(clip);
    });
  }
  if (state.editor && state.editor.clipKey === clip.key) wireEditor(clip);  // Task 8
```

Vor Task 8 `openEditor` und `wireEditor` als leere Funktionen anlegen (`async function openEditor() {}`, `function wireEditor() {}`), damit die Aufnahme schon funktioniert.

- [ ] **Step 4: style.css**

```css
.source-switch { margin: 8px 0 0; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.recorder-row #btn-record.recording { background: #b23b2a; }
.rec-level {
  display: inline-block; width: 140px; height: 10px; border: 1px solid var(--line);
  border-radius: 5px; overflow: hidden; background: #fff;
}
.rec-level span { display: block; height: 100%; width: 0; background: var(--ok); transition: width 60ms linear; }
```

- [ ] **Step 5: Sichtprüfung**

Server aus dem Haupt-Checkout starten geht nicht (der hat den alten Code); im Worktree: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python ./tts web --no-model 2>/dev/null || ~/qwen-tts-test/.venv/bin/python ./tts web` (CLI-Flag prüfen mit `grep -n "add_argument" ttskit/cli.py`). Im Browser einen `monster`-Clip öffnen: Umschalter steht auf Mikrofon, Aufnehmen → Stopp erzeugt eine Kandidaten-Zeile mit 🎙. `out/` entsteht dabei im Worktree und ist gitignored.

- [ ] **Step 6: Tests + Commit**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q` → PASS.

```bash
git add tools/tts/ttskit/static/ tools/tts/ttskit/server.py tools/tts/tests/test_server.py
git commit -m "feat(tts-ui): Quelle TTS/Mikrofon je Clip, Aufnahme per AudioWorklet mit Pegelanzeige"
```

---

### Task 8: Web-UI — Wellenform-Editor und Kandidaten-Zeile

**Files:**
- Modify: `tools/tts/ttskit/static/app.js`, `style.css`

**Interfaces:**
- Consumes: `GET/PUT /api/clips/{key}/recordings/{seed}`, `POST …/preview`; Kandidaten-Feld `mic`; `state.appMonsterPitch`.
- Produces: `openEditor(clipKey, seed)`, `closeEditor()`, `wireEditor(clip)`, `drawWaveform(canvas, info, edit)`, `editorHtml(clip)`.

- [ ] **Step 1: Editor-Zustand und HTML**

```js
async function openEditor(clipKey, seed) {
  const info = await api(`/api/clips/${encodeURIComponent(clipKey)}/recordings/${seed}`);
  state.editor = { clipKey, seed, info, edit: { ...info.edit }, drag: null, playing: null };
  if (state.selected === clipKey) renderDetail(clipKey);
}

function closeEditor() {
  state.editor = null;
  if (state.selected) renderDetail(state.selected);
}

const PITCH_OPTIONS = Array.from({ length: 25 }, (_, i) => i - 12);

function editorHtml(clip) {
  const ed = state.editor;
  if (!ed || ed.clipKey !== clip.key) return "";
  const monster = clip.profile === "monster";
  const pitch = state.appMonsterPitch;
  return `
    <div class="card editor-card" id="editor">
      <h4 class="card-title">✂ Aufnahme ${ed.seed} schneiden</h4>
      <canvas id="wave" width="900" height="160"></canvas>
      <div class="editor-controls">
        <label>Start <input id="ed-start" type="number" step="0.001" min="0"
          max="${ed.info.duration}" value="${ed.edit.start.toFixed(3)}" /> s</label>
        <label>Ende <input id="ed-end" type="number" step="0.001" min="0"
          max="${ed.info.duration}" value="${ed.edit.end.toFixed(3)}" /> s</label>
        <button id="ed-auto" title="Zurück auf den automatischen Stille-Schnitt">Automatisch</button>
        <label>Tonhöhe
          <select id="ed-pitch">${PITCH_OPTIONS.map((n) =>
            `<option value="${n}" ${n === ed.edit.pitchSemitones ? "selected" : ""}>${n > 0 ? "+" : ""}${n} Halbtöne</option>`).join("")}</select></label>
        <label class="inline"><input id="ed-norm" type="checkbox" ${ed.edit.normalize ? "checked" : ""} /> Normalisieren</label>
      </div>
      <div class="editor-controls">
        <button id="ed-play" data-app-pitch="">▶ Anhören</button>
        ${monster ? `
        <button id="ed-play-left" data-app-pitch="${pitch.left}"
          title="So klingt es in der App beim linken Fresser (×${pitch.left})">▶ Laufzeit links</button>
        <button id="ed-play-right" data-app-pitch="${pitch.right}"
          title="So klingt es in der App beim rechten Fresser (×${pitch.right})">▶ Laufzeit rechts</button>` : ""}
        <span class="muted small" id="ed-status"></span>
        <span class="editor-spacer"></span>
        <button id="ed-discard" title="Editor schließen — die Aufnahme behält den zuletzt gespeicherten Schnitt">Verwerfen</button>
        <button id="ed-save" class="primary">Übernehmen</button>
      </div>
      <p class="muted small">Grau ist weggeschnitten, die dünnen Linien zeigen den automatischen
        Vorschlag. Griffe ziehen oder Zahlen tippen.</p>
    </div>`;
}
```

In `recorderPanelHtml` den `<div id="editor-slot"></div>` durch `${editorHtml(clip)}` ersetzen. Damit der Editor auch im TTS-Modus sichtbar ist (✂ an einer Aufnahme klicken, während die Quelle auf TTS steht): in `candidatesCardHtml` nach der Kandidaten-Tabelle (`<div id="candidates-body">…</div>`) `${clipSource(clip) === "tts" ? editorHtml(clip) : ""}` einfügen.

- [ ] **Step 2: Zeichnen und Verdrahten**

```js
function drawWaveform(canvas, info, edit) {
  const ctx = canvas.getContext("2d");
  const { width: W, height: H } = canvas;
  const x = (s) => (s / info.duration) * W;
  ctx.clearRect(0, 0, W, H);
  ctx.fillStyle = "#e9e3d8";
  ctx.fillRect(0, 0, x(edit.start), H);
  ctx.fillRect(x(edit.end), 0, W - x(edit.end), H);
  ctx.strokeStyle = "#c4622d"; ctx.lineWidth = 1;
  const n = info.peaks.length;
  ctx.beginPath();
  info.peaks.forEach((p, i) => {
    const px = (i / n) * W, h = Math.max(1, p * (H / 2 - 4));
    ctx.moveTo(px, H / 2 - h); ctx.lineTo(px, H / 2 + h);
  });
  ctx.stroke();
  ctx.strokeStyle = "#857a6c"; ctx.setLineDash([3, 3]);
  [info.autoTrim.start, info.autoTrim.end].forEach((s) => {
    ctx.beginPath(); ctx.moveTo(x(s), 0); ctx.lineTo(x(s), H); ctx.stroke();
  });
  ctx.setLineDash([]);
  ctx.fillStyle = "#2f2a24";
  [edit.start, edit.end].forEach((s) => ctx.fillRect(x(s) - 2, 0, 4, H));
}

function wireEditor(clip) {
  const ed = state.editor;
  const canvas = el("wave");
  if (!ed || !canvas) return;
  const encoded = encodeURIComponent(clip.key);
  const MIN_LEN = 0.02;
  const clamp = () => {
    ed.edit.start = Math.min(Math.max(0, ed.edit.start), ed.info.duration - MIN_LEN);
    ed.edit.end = Math.max(Math.min(ed.info.duration, ed.edit.end), ed.edit.start + MIN_LEN);
  };
  const sync = () => {
    clamp();
    el("ed-start").value = ed.edit.start.toFixed(3);
    el("ed-end").value = ed.edit.end.toFixed(3);
    drawWaveform(canvas, ed.info, ed.edit);
  };
  sync();

  const secondsAt = (event) => {
    const rect = canvas.getBoundingClientRect();
    return ((event.clientX - rect.left) / rect.width) * ed.info.duration;
  };
  canvas.onpointerdown = (event) => {
    const s = secondsAt(event);
    const tol = ed.info.duration * (8 / canvas.getBoundingClientRect().width);
    ed.drag = Math.abs(s - ed.edit.start) <= tol ? "start"
      : Math.abs(s - ed.edit.end) <= tol ? "end"
      : (Math.abs(s - ed.edit.start) < Math.abs(s - ed.edit.end) ? "start" : "end");
    canvas.setPointerCapture(event.pointerId);
    ed.edit[ed.drag] = s; sync();
  };
  canvas.onpointermove = (event) => { if (ed.drag) { ed.edit[ed.drag] = secondsAt(event); sync(); } };
  canvas.onpointerup = canvas.onpointercancel = () => { ed.drag = null; };

  el("ed-start").onchange = (e) => { ed.edit.start = Number(e.target.value); sync(); };
  el("ed-end").onchange = (e) => { ed.edit.end = Number(e.target.value); sync(); };
  el("ed-auto").onclick = () => { ed.edit.start = ed.info.autoTrim.start; ed.edit.end = ed.info.autoTrim.end; sync(); };
  el("ed-pitch").onchange = (e) => { ed.edit.pitchSemitones = Number(e.target.value); };
  el("ed-norm").onchange = (e) => { ed.edit.normalize = e.target.checked; };

  el("detail").querySelectorAll("[data-app-pitch]").forEach((button) => {
    button.onclick = guard(async () => {
      el("ed-status").textContent = "rendere Vorschau …";
      const body = { edit: ed.edit };
      if (button.dataset.appPitch) body.appPitch = Number(button.dataset.appPitch);
      const response = await fetch(`/api/clips/${encoded}/recordings/${ed.seed}/preview`, {
        method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(body),
      });
      if (!response.ok) throw new Error((await response.json()).detail || response.statusText);
      const url = URL.createObjectURL(await response.blob());
      if (ed.playing) { ed.playing.pause(); URL.revokeObjectURL(ed.playing.src); }
      ed.playing = new Audio(url);
      ed.playing.onended = () => { el("ed-status").textContent = ""; };
      el("ed-status").textContent = button.dataset.appPitch
        ? `spielt mit App-Pitch ×${button.dataset.appPitch}` : "spielt";
      await ed.playing.play();
    });
  });
  el("ed-discard").onclick = () => closeEditor();
  el("ed-save").onclick = guard(async () => {
    await put(`/api/clips/${encoded}/recordings/${ed.seed}`, ed.edit);
    state.editor = null;
    await refresh({ keepDetail: true });
    showBanner(`Schnitt gespeichert — Aufnahme ${ed.seed} ist als Kandidat aktuell.`, "ok");
  });
}
```

`put()` existiert bereits (Zeile ~118). `redrawDetail`/`refresh` zeichnen den Editor über `renderDetail` → `wireEditor` neu; der Zustand liegt in `state.editor`, geht also nicht verloren.

- [ ] **Step 3: Kandidaten-Zeile und Liste**

In `candidateRow`:
- Stimme-Zelle: `${cand.mic ? '<span title="Mikrofon-Aufnahme">🎙</span>' : cand.speaker ? escapeHtml(cand.speaker) : '<span class="muted">—</span>'}`
- In der Aktions-Zelle nach dem 👎-Knopf: `${cand.mic ? \`<button data-edit-recording="${cand.seed}" class="icon" title="Schnitt und Tonhöhe dieser Aufnahme bearbeiten">✂</button>\` : ""}`
- Der Chip „⚠️ alt" bleibt an `cand.fresh === false` — Aufnahmen liefern `fresh: true`.

In `wireCandidateHandlers`:

```js
  el("detail").querySelectorAll("[data-edit-recording]").forEach((button) => {
    button.onclick = guard(() => openEditor(clip.key, Number(button.dataset.editRecording)));
  });
```

Fehlt die Rohdatei (404 beim Öffnen), landet die Meldung über `guard` im Banner — das genügt (Spec §7).

In `renderList`: Produktion ist eine Aufnahme → `🎙` hinter dem Status:

```js
    const micProduction = clip.status === "rendered"
      && clip.candidates.some((c) => c.seed === clip.seed && c.mic);
    ...
      <span class="chip ${clip.status}">${STATUS_LABELS[clip.status] || clip.status}</span>
      ${micProduction ? '<span class="chip" title="Produktion ist eine Mikrofon-Aufnahme">🎙</span>' : ""}`;
```

- [ ] **Step 4: style.css**

```css
.editor-card { margin-top: 10px; }
#wave { width: 100%; height: 160px; display: block; border: 1px solid var(--line); border-radius: 6px; background: #fff; cursor: col-resize; touch-action: none; }
.editor-controls { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin: 8px 0; }
.editor-controls input[type="number"] { width: 6.5em; }
.editor-spacer { flex: 1; }
```

- [ ] **Step 5: Sichtprüfung im Browser** (Server wie in Task 7): Aufnahme → Editor öffnet mit Wellenform, grauen Rändern, gestrichelten Auto-Linien; Griffe ziehen aktualisiert Zahlen; „▶ Laufzeit links" klingt tiefer als „▶ Anhören"; „Übernehmen" schließt den Editor, die Zeile zeigt 🎙 und ✂; Radio „Produktion" übernimmt; die Liste zeigt 🎙 hinter „fertig"; ✂ öffnet den Editor erneut. Mit `python -m json.tool out/candidates/<key>/<seed>.json` den Sidecar prüfen.

- [ ] **Step 6: Commit**

```bash
git add tools/tts/ttskit/static/
git commit -m "feat(tts-ui): Wellenform-Editor für Aufnahmen — Schnitt, Tonhöhe, Normalisierung, Laufzeit-Vorschau"
```

---

### Task 9: App — `soundTts` entfernen, Fresser spricht das Lemma

**Files:**
- Modify: `app/src/main/assets/content/atoms.json`, `app/src/main/java/app/abcvorschule/content/ContentModels.kt:78-86`, `app/src/main/java/app/abcvorschule/content/SoundFeederSpeech.kt`
- Delete: `app/src/test/java/app/abcvorschule/content/AtomSoundTtsTest.kt`
- Test: `app/src/test/java/app/abcvorschule/content/SoundFeederSpeechTest.kt`

- [ ] **Step 1: Test anpassen** — in `SoundFeederSpeechTest` alle `"sss"` → `"S"`, alle `"schhh"` → `"Sch"` (vier Stellen in drei Tests). Klassen-Kommentar in `SoundFeederSpeech.kt` wird in Step 3 aktualisiert.

- [ ] **Step 2: Test laufen — fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.content.SoundFeederSpeechTest" -q`
Expected: FAIL (`expected S, was sss`).

- [ ] **Step 3: Code**

`atoms.json`: `sed -i '' '/"soundTts":/d' app/src/main/assets/content/atoms.json` — der Schlüssel steht immer vor `"strokes"`, die Kommasetzung bleibt gültig. Prüfen: `grep -c soundTts app/src/main/assets/content/atoms.json` → 0, `python3 -m json.tool app/src/main/assets/content/atoms.json > /dev/null`.

`ContentModels.kt`: das Feld `soundTts` samt Doc-Kommentar (Zeilen ~78–86) löschen.

`SoundFeederSpeech.kt`:

```kotlin
/**
 * Was der Laut-Fresser wann sagt (design doc §5–§7). Wörter sind die kuratierten
 * Lemma-Clips; die Laute sind **dasselbe Lemma** („S", „Sch") in der Variante
 * `monster` des Clip-Index — von Hand aufgenommene Laute
 * (docs/superpowers/specs/2026-09-05-lautfresser-mikrofon-aufnahme-design.md).
 * Die App wählt die Variante über die Stimme ([VoiceStyle.MonsterLow]/[MonsterHigh])
 * und legt ihre Tonhöhe obendrauf. „Bäh!" und „Mmmmh!" sind die einzigen
 * Monster-eigenen Strings (extra-strings.json, Profil `monster`).
 */
object SoundFeederSpeech {
    ...
    /**
     * Der Laut des Graphems: das Lemma in Monster-Stimme. Der Clip-Index liefert
     * dafür die Aufnahme aus `variants.monster`; ohne Aufnahme fällt es auf den
     * Buchstabennamen-Clip bzw. Android-TTS zurück — „S" liest die TTS besser als
     * eine Fake-Aussprache wie „sss".
     */
    fun soundPart(round: SoundFeederRound, side: FeederSide, pack: ContentPack): SpokenPart =
        SpokenPart(lemma(pack, round.atomIdFor(side)), voiceFor(side))
```

`AtomSoundTtsTest.kt` löschen (`git rm`); die Abdeckung „jedes Paar nennt zwei Buchstaben-Atome" liegt in `SoundPairsTest.everyPairInTheTableNamesTwoLetterAtomsOfThePack`.

- [ ] **Step 4: Alle App-Unit-Tests**

Run: `./gradlew :app:testDebugUnitTest -q`
Expected: PASS. Schlägt ein Content-Validator- oder Snapshot-Test an, weil er `soundTts` erwartet: `grep -rn soundTts app/src` und die Stelle entfernen.

- [ ] **Step 5: Commit**

```bash
git add -A app/src/main/assets/content/atoms.json app/src/main/java/app/abcvorschule/content/ app/src/test/java/app/abcvorschule/content/
git commit -m "feat(content+exercise): Laut-Fresser spricht das Lemma in Monster-Variante; soundTts entfällt"
```

---

### Task 10: App — `ClipIndex`-Varianten und Lookup bei Monster-Stimme

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/speech/ClipIndex.kt`, `app/src/main/java/app/abcvorschule/speech/SpeechController.kt:357-365`
- Test: `app/src/test/java/app/abcvorschule/speech/ClipIndexTest.kt`

**Interfaces:**
- Produces: `ClipIndex.lookup(text: String, variant: String? = null): ClipEntry?`, `ClipIndex.MONSTER_VARIANT = "monster"`, `entries()` enthält Varianten.

- [ ] **Step 1: Failing tests** (in `ClipIndexTest`)

```kotlin
    private val withVariants = """
        {
          "version": 1,
          "clips": {
            "S": { "file": "phoneme_s.ogg", "profile": "phoneme" },
            "Bäh!": { "file": "monster_baeh.ogg", "profile": "monster" }
          },
          "variants": {
            "monster": {
              "S": { "file": "monster_s.ogg", "profile": "monster" },
              "Bäh!": { "file": "monster_baeh.ogg", "profile": "monster" }
            }
          }
        }
    """.trimIndent()

    @Test
    fun `Variante monster liefert die Aufnahme des Fressers`() {
        val index = ClipIndex.parse(withVariants)
        assertEquals("monster_s.ogg", index.lookup("S", ClipIndex.MONSTER_VARIANT)?.file)
        assertEquals("phoneme_s.ogg", index.lookup("S")?.file)
    }

    @Test
    fun `ohne Variante faellt der Lookup auf den normalen Clip zurueck`() {
        val index = ClipIndex.parse(withVariants)
        assertEquals("phoneme_s.ogg", index.lookup("S", "geist")?.file)
        assertEquals("monster_baeh.ogg", index.lookup("Bäh!", ClipIndex.MONSTER_VARIANT)?.file)
        assertNull(index.lookup("X", ClipIndex.MONSTER_VARIANT))
    }

    @Test
    fun `Index ohne variants-Block bleibt lesbar`() {
        assertEquals("word_1a2b3c4d5e6f.ogg", ClipIndex.parse(sample).lookup("M", ClipIndex.MONSTER_VARIANT)?.file)
    }

    @Test
    fun `entries enthalten auch die Varianten`() {
        assertEquals(4, ClipIndex.parse(withVariants).entries().size)
    }
```

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.speech.ClipIndexTest" -q` → Kompilierfehler (kein zweiter Parameter).

- [ ] **Step 2: `ClipIndex.kt`**

```kotlin
@Serializable
private data class ClipIndexFile(
    val version: Int = 1,
    val clips: Map<String, ClipEntry> = emptyMap(),
    /** Variante → Text → Clip; heute nur `monster` (Laut-Fresser, eigene Aufnahmen). */
    val variants: Map<String, Map<String, ClipEntry>> = emptyMap(),
)

class ClipIndex private constructor(
    private val clips: Map<String, ClipEntry>,
    private val caseInsensitive: Map<String, String>,
    private val variants: Map<String, Map<String, ClipEntry>>,
) {
    val size: Int get() = clips.size

    /**
     * [variant] zuerst (exakter Text), sonst der normale Clip. Eine unbekannte
     * Variante ist kein Fehler — dann spricht der normale Clip.
     */
    fun lookup(text: String, variant: String? = null): ClipEntry? {
        val trimmed = text.trim()
        if (variant != null) variants[variant]?.get(trimmed)?.let { return it }
        clips[trimmed]?.let { return it }
        val canonical = caseInsensitive[trimmed.lowercase()] ?: return null
        return clips[canonical]
    }

    fun entries(): Collection<ClipEntry> = clips.values + variants.values.flatMap { it.values }

    companion object {
        /** Aufnahmen des Laut-Fressers; Name = Profil in tools/tts (export.VARIANT_PROFILES). */
        const val MONSTER_VARIANT = "monster"
        ...
        fun empty(): ClipIndex = ClipIndex(emptyMap(), emptyMap(), emptyMap())

        fun parse(raw: String): ClipIndex {
            val file = json.decodeFromString<ClipIndexFile>(raw)
            return ClipIndex(file.clips, buildCaseInsensitive(file.clips), file.variants)
        }
```

- [ ] **Step 3: `SpeechController.playClip`**

```kotlin
    private fun playClip(text: String, channel: SpeechChannel, voice: VoiceStyle, onComplete: () -> Unit): Boolean {
        // Monster-Stimme: zuerst die eigene Aufnahme des Fressers, sonst der normale
        // Clip — und in beiden Fällen die Laufzeit-Tonhöhe obendrauf (design doc §7).
        val variant = if (voice == VoiceStyle.Normal) null else ClipIndex.MONSTER_VARIANT
        val entry = clips.lookup(text, variant) ?: return false
```

(Der Rest der Funktion bleibt.)

- [ ] **Step 4: Tests**

Run: `./gradlew :app:testDebugUnitTest -q` → PASS. Danach Build: `./gradlew :app:assembleDebug -q` → BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/speech/ app/src/test/java/app/abcvorschule/speech/ClipIndexTest.kt
git commit -m "feat(speech): ClipIndex kennt Varianten — Monster-Stimme spielt die Aufnahme aus variants.monster"
```

---

### Task 11: Dokumentation und Abschluss

**Files:**
- Modify: `tools/tts/README.md`, `docs/PRODUCT_PRINCIPLES.md` (Z. ~430–436), `AGENTS.md` (Z. 67), `docs/superpowers/specs/2026-09-04-laut-fresser-design.md` (§7), `docs/superpowers/specs/2026-08-02-qwen-tts-app-integration-design.md` (Index-Abschnitt)

- [ ] **Step 1: README `tools/tts/README.md`**

Absatz „Das Profil `monster` …" (Z. 223–234) ersetzen:

```markdown
Das Profil `monster` ist die Stimme des Laut-Fressers. Seine zwei Reaktionen („Bäh!",
„Mmmmh!", Feld `monsterTts` in `extra-strings.json`) sind Qwen-Clips mit `uncle_fu`.
Die *Laute* dagegen — ein Clip pro Graphem der `SoundPairs`-Tabelle
(`app/…/content/SoundPairs.kt`, per Regex gelesen) mit dem **Lemma** als Text („S",
„Sch") — werden **per Mikrofon aufgenommen** (siehe „Mikrofon-Aufnahmen"). Bis
September 2026 trugen die Buchstaben-Atome dafür eine Fake-Aussprache `soundTts`
(„sss", „schhh"); weder Qwen noch die Android-TTS sprachen sie brauchbar, und die
TTS liest „S" ohnehin besser als „sss". Im Index landen `monster`-Clips unter
`variants.monster`; die App sucht dort, sobald sie mit Monster-Stimme spricht, und
legt ihre Laufzeit-Tonhöhe (links 0.75, rechts 1.3) weiterhin obendrauf.
```

Neuer Abschnitt nach „Aussprache und Stimme":

```markdown
## Mikrofon-Aufnahmen

Jeder Clip kann statt aus Qwen aus dem **Mikrofon** kommen: Umschalter „Quelle:
🎲 TTS | 🎙 Mikrofon" in der Detailsicht, vorbelegt aus `source` im Profil (`monster`
steht auf `mic`), pro Clip im Browser gemerkt. „● Aufnehmen" nimmt mono ohne
Rauschunterdrückung auf (AudioWorklet, max. 30 s); „■ Stopp" lädt die Aufnahme hoch
und öffnet den **Editor**: Wellenform der ganzen Aufnahme, weggeschnittene Ränder grau,
der automatische Stille-Schnitt als gestrichelte Linien, zwei ziehbare Griffe,
Tonhöhe in Halbtönen (Default `micPitchSemitones` des Profils, Tempo bleibt —
`librosa.effects.pitch_shift`), Normalisieren auf −1 dBFS. „▶ Anhören" spielt die
Bearbeitung; bei `monster` zusätzlich „▶ Laufzeit links/rechts" mit dem App-Pitch
(`mic.APP_MONSTER_PITCH`, Spiegel von `VoiceStyle`). „Übernehmen" schreibt den Kandidaten.

Eine Aufnahme **ist ein Kandidat**: Radio „Produktion", 👍/👎, „Alle löschen" und Export
funktionieren unverändert. Ihr „Seed" ist ein Pseudo-Seed ≥ 1 900 000 000
(`mic.MIC_SEED_MIN`; Qwen-Zufalls-Seeds bleiben darunter). Dateien unter
`out/candidates/<key>/`: `<seed>.raw.wav` (Rohaufnahme, 24 kHz mono, bleibt),
`<seed>.wav` (bearbeitet), `<seed>.json` mit `source: "mic"`, `edit`, `autoTrim` und
`fingerprint: "mic:<sha>"` der bearbeiteten Datei — nur der steuert das Re-Encoding im
Export. ✂ in der Kandidaten-Zeile öffnet den Editor erneut; ist die Aufnahme gerade
Produktion, zieht `out/audio/<key>.wav` mit. Auto-Trim rechnet relativ zum Rauschboden
(`mic.auto_trim`), anders als `audio.trim_silence` für Qwen-Ausgaben.
```

Im Abschnitt „Dateien"/`index.json`-Erwähnungen ergänzen, dass `index.json` einen Block `variants` trägt.

- [ ] **Step 2: PRODUCT_PRINCIPLES §10 (Z. ~430–436)** — den Satz mit `soundTts` ersetzen durch:

```markdown
  Der Fresser spricht dabei den *Laut*: das Lemma des Graphems („S", „Sch") aus einer
  **von Hand aufgenommenen** Variante `monster` im Clip-Index (Mikrofon-Aufnahme im
  Qwen-Web-Interface, Spec `2026-09-05-lautfresser-mikrofon-aufnahme-design.md`),
  während Jagd, Wort-Detektiv und Spurensucher beim Buchstabennamen-Clip bleiben. Ohne
  Aufnahme fällt er auf denselben Buchstabennamen-Clip bzw. Android-TTS mit dem Lemma
  zurück — keine Fake-Aussprache mehr (`soundTts` entfiel im September 2026).
```

- [ ] **Step 3: AGENTS.md Z. 67** — Schluss des Laut-Fresser-Punkts: `…Reaktionen im TTS-Profil \`monster\`; Laute sind das Lemma in der Index-Variante \`monster\` (Mikrofon-Aufnahmen, kein \`soundTts\`).`

- [ ] **Step 4: Design-Docs** — In `2026-09-04-laut-fresser-design.md` am Anfang von §7 einen Hinweis:

```markdown
> **Stand 2026-09-05:** Die Laute kommen nicht mehr aus `soundTts` per Qwen, sondern als
> Mikrofon-Aufnahmen des Lemmas in der Index-Variante `monster` — siehe
> `2026-09-05-lautfresser-mikrofon-aufnahme-design.md`. Die Laufzeit-Tonhöhe (0.75/1.3)
> und „Bäh!"/„Mmmmh!" bleiben wie hier beschrieben.
```

In `2026-08-02-qwen-tts-app-integration-design.md` beim Index-Beispiel (Z. ~36–42) ergänzen: `"variants": { "monster": { "<sourceText>": { "file": "monster_….ogg", "profile": "monster" } } }` mit einem Satz „Varianten sind Aufnahmen desselben Textes in anderer Stimme; `ClipIndex.lookup(text, variant)`".

- [ ] **Step 5: Gesamtprüfung**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q && cd ../.. && ./gradlew :app:testDebugUnitTest :app:assembleDebug -q
```

Expected: beides grün. `grep -rn soundTts --exclude-dir=.git --exclude-dir=build . | grep -v "docs/superpowers"` darf nur noch erklärende Erwähnungen in README/PRINCIPLES/AGENTS liefern.

- [ ] **Step 6: Commit**

```bash
git add tools/tts/README.md docs/ AGENTS.md
git commit -m "docs: Laut-Fresser-Laute per Mikrofon — README, Prinzipien, Agent-Guide, Design-Verweise"
```

---

## Nach dem Plan

- Merge nach `main` gemäß Nutzer-Memory (Auto-Commit/Merge), danach im Haupt-Checkout `tts extract` und `tts status`: die fünf alten Kandidaten-Ordner `out/candidates/monster:*` der soundTts-Clips sind verwaist und können vom Nutzer gelöscht werden; die 26 neuen `monster`-Clips stehen auf „fehlt", bis sie aufgenommen sind.
- Erst nach den Aufnahmen `tts export` und die OGGs plus `index.json` committen.
