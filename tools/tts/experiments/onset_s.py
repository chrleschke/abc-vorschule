"""Anlaut-S aus einem Wortclip schneiden: vom ersten Zischen bis zum Stimmeinsatz.

cut_onset_s(y, sr) -> (segment, info). Rauschen = Frames mit ≥ 70 % Energie über 4 kHz;
Stimmeinsatz = erster Frame danach, dessen Energie unter 1 kHz über 10 % des Maximums steigt.
"""
import numpy as np, librosa

HOP = 128

def cut_onset_s(y, sr, hf_thr=0.7, lf_rel=0.25):
    S = np.abs(librosa.stft(y, n_fft=1024, hop_length=HOP)) ** 2
    f = librosa.fft_frequencies(sr=sr, n_fft=1024)
    tot = S.sum(0) + 1e-12
    hf_share = S[f >= 4000].sum(0) / tot
    lf = S[f < 1000].sum(0)
    loud = tot > tot.max() * 0.01
    hiss = (hf_share > hf_thr) & loud
    if not hiss.any():
        return None, {"reason": "kein Zischen"}
    start = int(np.argmax(hiss))
    # Ende: erster Frame ab start mit Stimme (tieffrequente Energie) oder Ende des Zischens
    voiced = (lf / tot) > lf_rel          # Anteil am Frame, nicht am Clip-Maximum: ein Clip ohne Stimme hat sonst überall 'Stimme'
    after = np.where(voiced[start:])[0]
    end_v = start + int(after[0]) if after.size else len(hiss)
    not_hiss = np.where(~hiss[start:])[0]
    end_h = start + int(not_hiss[0]) if not_hiss.size else len(hiss)
    end = min(end_v, end_h)
    a, b = start * HOP, end * HOP
    seg = y[a:b]
    info = {"start_ms": int(a / sr * 1000), "len_ms": int((b - a) / sr * 1000),
            "ended_by": "voice" if end_v <= end_h else "hiss-end"}
    return seg, info

def texture(seg, sr):
    """Flatter und ruhiger = sprachähnlicher: mod (Energieflattern) und specvar (Spektrumwechsel)."""
    if len(seg) < 1024: return {}
    S = np.abs(librosa.stft(seg, n_fft=1024, hop_length=256)) ** 2
    f = librosa.fft_frequencies(sr=sr, n_fft=1024)
    band = (f >= 3000) & (f <= 11500)
    e = S.sum(0); L = np.log(S[band] + 1e-9)
    spec = S.mean(1)[band]
    return {"mod": round(float(np.std(e) / (np.mean(e) + 1e-12)), 2),
            "specvar": round(float(np.mean(np.std(L, axis=1))), 2),
            "peak": int(f[band][int(np.argmax(spec))]),
            "hf": round(float(S[f >= 4000].sum() / (S.sum() + 1e-12)), 2)}

def fade(seg, sr, in_ms=10, out_ms=40):
    seg = seg.copy()
    n_in, n_out = int(sr * in_ms / 1000), int(sr * out_ms / 1000)
    if n_in and len(seg) > n_in: seg[:n_in] *= np.linspace(0, 1, n_in)
    if n_out and len(seg) > n_out: seg[-n_out:] *= np.linspace(1, 0, n_out)
    return seg

def extend_noise(seg, sr, target_ms=450, grain_ms=40, seed=0):
    """Rauschsegment granular verlängern: zufällig versetzte Hann-Körner, 50 % Überlappung.

    Für Reibelaute unhörbar, weil das Signal stationäres Rauschen ist — die Körner
    tragen nur das Spektrum weiter. Der natürliche Einsatz (erste 30 ms) bleibt erhalten.
    """
    rng = np.random.default_rng(seed)
    n_target = int(sr * target_ms / 1000)
    if len(seg) >= n_target:
        return seg[:n_target]
    g = int(sr * grain_ms / 1000); hop = g // 2
    win = np.hanning(g).astype(np.float32)
    keep = min(len(seg), int(sr * 0.03))            # natürlicher Einsatz
    out = np.zeros(n_target + g, dtype=np.float32); norm = np.zeros_like(out)
    # Körner nur aus dem stationären Teil (ohne die ersten 15 ms)
    lo = min(int(sr * 0.015), max(0, len(seg) - g - 1))
    pos = keep
    while pos < n_target:
        off = rng.integers(lo, max(lo + 1, len(seg) - g))
        out[pos:pos + g] += seg[off:off + g] * win
        norm[pos:pos + g] += win
        pos += hop
    norm[norm < 1e-3] = 1.0
    out = out / norm
    res = out[:n_target].copy()
    # Einsatz zurück, mit kurzem Crossfade
    xf = min(int(sr * 0.01), keep)
    res[:keep - xf] = seg[:keep - xf]
    ramp = np.linspace(0, 1, xf, dtype=np.float32)
    res[keep - xf:keep] = seg[keep - xf:keep] * (1 - ramp) + res[keep - xf:keep] * ramp
    return res
