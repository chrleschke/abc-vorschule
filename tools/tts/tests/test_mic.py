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
    assert 0.7 <= max(p[50:]) <= 0.801


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
    assert 0.19 <= float(np.max(np.abs(out))) <= 0.201


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
    assert edit.to_dict() == {"start": 0.1, "end": 0.5, "pitchSemitones": -4, "normalize": False,
                              "highpass": False}
    assert Edit.from_dict({"start": 0.0, "end": 0.5, "highpass": True}, duration=1.0).highpass is True
    for bad in ({"start": 0.5, "end": 0.5}, {"start": 0.0, "end": 1.5},
                {"start": -0.1, "end": 0.5}, {"start": 0.0, "end": 0.5, "pitchSemitones": 13},
                {"start": 0.0, "end": 0.5, "pitchSemitones": 1.5}, {"start": "a", "end": 0.5}):
        with pytest.raises(ValueError):
            Edit.from_dict(bad, duration=1.0)


def test_edit_defaults_pitch_and_normalize():
    assert Edit.from_dict({"start": 0.0, "end": 0.5}, duration=1.0) == Edit(0.0, 0.5, 0, True)


def test_highpass_removes_rumble_but_keeps_the_sound():
    sr = 24000
    rumble = _sine(60, 1.0, sr, amp=0.5)
    hiss = _sine(6000, 1.0, sr, amp=0.2)

    def peaks_at(wav):
        spectrum = np.abs(np.fft.rfft(wav * np.hanning(len(wav))))
        freqs = np.fft.rfftfreq(len(wav), 1 / sr)
        return (spectrum[(freqs > 50) & (freqs < 70)].max(),
                spectrum[(freqs > 5900) & (freqs < 6100)].max())

    plain = mic.render(rumble + hiss, sr, Edit(0.0, 1.0, normalize=False))
    out = mic.render(rumble + hiss, sr, Edit(0.0, 1.0, normalize=False, highpass=True))
    low_before, high_before = peaks_at(plain)
    low_after, high_after = peaks_at(out)
    assert low_after < low_before / 3          # 60 Hz um mehr als 10 dB gedämpft (2. Ordnung, 1 Oktave)
    assert abs(high_after - high_before) < 0.05 * high_before  # 6 kHz unangetastet
    assert abs(len(out) - sr) <= 1             # Länge bleibt, kein Zeitversatz (filtfilt)
    assert mic.fingerprint_of(plain) != mic.fingerprint_of(out)


def test_app_monster_pitch_is_one_semitone_each_way():
    assert mic.APP_MONSTER_PITCH["left"] == pytest.approx(2 ** (-1 / 12), abs=1e-4)
    assert mic.APP_MONSTER_PITCH["right"] == pytest.approx(2 ** (1 / 12), abs=1e-4)
