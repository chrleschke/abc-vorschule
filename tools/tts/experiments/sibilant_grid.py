"""Messreihe: welche Schreibweise/Instruktion/Sampling/Stimme lässt Qwen3-TTS ein hartes S zischen?

Bewertet ohne Ohr: ein /s/ ist unstimmhaftes Rauschen mit fast aller Energie über 4 kHz
(hf ≥ 0.45, voiced ≤ 0.15, lf ≤ 0.2); die Grenze /s/ gegen /ʃ/ zieht `sratio`
(Energie 5–11 kHz zu 2–5 kHz ≥ 1). Ergebnisse vom 2026-09-04 stehen in README.md
(„Zischlaute: der Text entscheidet").

Aufruf: ~/qwen-tts-test/.venv/bin/python experiments/sibilant_grid.py configs.json out_dir [seeds...]
Each config: {"id":..., "text":..., "speaker":..., "language":..., "instruct":..., "sampling":{...}}
Writes out_dir/<id>/<seed>.wav and appends out_dir/results.jsonl; prints a per-config summary.
"""
import json, sys, time, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).resolve().parent.parent))
import numpy as np, soundfile as sf, librosa
from ttskit.engine import Engine
from ttskit.store import Profile
from ttskit.audio import postprocess, write_wav

def feats(y, sr):
    dur = len(y) / sr
    if dur < 0.05:
        return dict(dur=round(dur, 2), hf=0, lf=1, sratio=0, cent=0, voiced=1, segs=0, peak=0)
    S = np.abs(librosa.stft(y, n_fft=1024, hop_length=256)) ** 2
    f = librosa.fft_frequencies(sr=sr, n_fft=1024)
    tot = S.sum() + 1e-12
    hf = S[f >= 4000].sum() / tot
    lf = S[f < 1000].sum() / tot
    e_s = S[(f >= 5000) & (f < 11000)].sum(); e_sh = S[(f >= 2000) & (f < 5000)].sum() + 1e-12
    mean_spec = S.mean(1); mean_spec[f < 500] = 0
    peak = float(f[int(np.argmax(mean_spec))])
    cent = float(librosa.feature.spectral_centroid(S=np.sqrt(S), sr=sr).mean())
    _, vflag, _ = librosa.pyin(y, fmin=60, fmax=400, sr=sr, frame_length=1024)
    voiced = float(np.nanmean(vflag)) if vflag.size else 0.0
    rms = librosa.feature.rms(y=y, frame_length=1024, hop_length=256)[0]
    act = rms > rms.max() * 0.15
    segs = int(np.sum(np.diff(act.astype(int)) == 1) + (1 if act[0] else 0))
    return dict(dur=round(dur, 2), hf=round(float(hf), 2), lf=round(float(lf), 2),
                sratio=round(float(e_s / e_sh), 2), cent=int(cent), voiced=round(voiced, 2),
                segs=segs, peak=int(peak))

def verdict(ft):
    """hiss = broadband unvoiced noise; S = hiss with energy above 5 kHz; ok length."""
    hiss = ft["hf"] >= 0.45 and ft["voiced"] <= 0.15 and ft["lf"] <= 0.2
    s_like = hiss and ft["sratio"] >= 1.0
    good_len = 0.4 <= ft["dur"] <= 1.6
    if s_like and good_len and ft["segs"] <= 2: return "S_OK"
    if s_like: return "S_len"
    if hiss: return "SCH"
    if ft["voiced"] >= 0.4: return "voiced"
    return "noise"

def main():
    cfg_path, out_dir = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    seeds = [int(s) for s in sys.argv[3:]] or [11, 22, 33, 44, 55, 66]
    configs = json.loads(cfg_path.read_text())
    out_dir.mkdir(parents=True, exist_ok=True)
    eng = Engine(); t0 = time.time(); eng.load()
    assert eng.loaded, eng.load_error
    print(f"model loaded in {time.time()-t0:.0f}s on {eng.device}", flush=True)
    res = out_dir / "results.jsonl"
    summary = {}
    for cfg in configs:
        prof = Profile(label=cfg["id"], speaker=cfg["speaker"], language=cfg.get("language", "german"),
                       instruct=cfg.get("instruct", ""), sampling=cfg["sampling"], seed_pool=[])
        verdicts = []
        for seed in seeds:
            t1 = time.time()
            try:
                wav, sr = eng.generate(cfg["text"], prof, seed)
            except Exception as exc:  # noqa
                print(f"{cfg['id']} seed={seed} FAILED {exc}", flush=True); continue
            raw_dur = len(wav) / sr
            wav = postprocess(wav, sr, trim=True, normalize=True)
            p = out_dir / cfg["id"] / f"{seed}.wav"
            write_wav(p, wav, sr)
            ft = feats(wav, sr); v = verdict(ft); verdicts.append(v)
            row = dict(id=cfg["id"], seed=seed, raw_dur=round(raw_dur, 2), verdict=v, path=str(p), **ft)
            with res.open("a") as fh: fh.write(json.dumps(row) + "\n")
            print(f"{cfg['id']:28} seed={seed:<3} {v:7} dur={ft['dur']:<5} hf={ft['hf']:<5} lf={ft['lf']:<5} "
                  f"sr={ft['sratio']:<6} peak={ft['peak']:<5} voiced={ft['voiced']:<5} segs={ft['segs']} ({time.time()-t1:.1f}s)", flush=True)
        n = len(verdicts)
        summary[cfg["id"]] = f"S_OK={verdicts.count('S_OK')}/{n} S_len={verdicts.count('S_len')} SCH={verdicts.count('SCH')} voiced={verdicts.count('voiced')} noise={verdicts.count('noise')}"
    print("\n=== SUMMARY ===")
    for k, v in summary.items(): print(f"{k:28} {v}")

if __name__ == "__main__":
    main()
