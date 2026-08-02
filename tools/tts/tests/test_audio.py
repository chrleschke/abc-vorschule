import numpy as np
import soundfile as sf

from ttskit.audio import normalize_peak, postprocess, trim_silence, write_wav

# The production code never names a rate — it always uses whatever the engine
# returned alongside the samples. This is a fixture value for the tests only.
SAMPLE_RATE = 24000


def tone(n: int, amplitude: float = 0.5) -> np.ndarray:
    t = np.arange(n, dtype=np.float32) / SAMPLE_RATE
    return (amplitude * np.sin(2 * np.pi * 440 * t)).astype(np.float32)


def test_trim_removes_leading_and_trailing_silence():
    wav = np.concatenate([np.zeros(12000, np.float32), tone(24000),
                          np.zeros(12000, np.float32)])
    trimmed = trim_silence(wav, SAMPLE_RATE, pad_ms=0)
    assert len(trimmed) < len(wav)
    assert abs(len(trimmed) - 24000) < 500


def test_trim_keeps_a_safety_pad():
    wav = np.concatenate([np.zeros(12000, np.float32), tone(24000),
                          np.zeros(12000, np.float32)])
    padded = trim_silence(wav, SAMPLE_RATE, pad_ms=30)
    tight = trim_silence(wav, SAMPLE_RATE, pad_ms=0)
    # 30 ms of pad on both ends = 2 * 720 samples
    assert len(padded) - len(tight) == 1440


def test_trim_never_pads_beyond_the_original():
    wav = tone(1000)
    assert len(trim_silence(wav, SAMPLE_RATE, pad_ms=500)) == 1000


def test_trim_leaves_an_all_silent_clip_alone():
    wav = np.zeros(5000, np.float32)
    assert len(trim_silence(wav, SAMPLE_RATE)) == 5000


def test_normalize_lifts_peak_to_target():
    quiet = tone(2400, amplitude=0.05)
    loud = normalize_peak(quiet, peak_dbfs=-1.0)
    expected = 10 ** (-1.0 / 20)
    assert np.isclose(np.max(np.abs(loud)), expected, atol=1e-3)


def test_normalize_lowers_a_hot_signal():
    hot = tone(2400, amplitude=0.99)
    out = normalize_peak(hot, peak_dbfs=-1.0)
    assert np.max(np.abs(out)) < np.max(np.abs(hot))


def test_normalize_leaves_silence_alone():
    silence = np.zeros(1000, np.float32)
    assert np.max(np.abs(normalize_peak(silence))) == 0.0


def test_postprocess_can_be_switched_off():
    wav = np.concatenate([np.zeros(6000, np.float32), tone(6000, 0.05)])
    assert np.array_equal(postprocess(wav, SAMPLE_RATE, trim=False, normalize=False), wav)


def test_write_wav_is_24k_mono_16bit(tmp_path):
    path = tmp_path / "clip.wav"
    write_wav(path, tone(2400), SAMPLE_RATE)
    data, sr = sf.read(path, dtype="int16")
    assert sr == SAMPLE_RATE
    assert data.ndim == 1
    info = sf.info(path)
    assert info.subtype == "PCM_16"


def test_write_wav_creates_parent_directories(tmp_path):
    path = tmp_path / "deep" / "nested" / "clip.wav"
    write_wav(path, tone(240), SAMPLE_RATE)
    assert path.exists()
