import json

import numpy as np
import soundfile as sf

from ttskit.cli import load_context
from ttskit.export import asset_name
from ttskit.migrate import migrate_word_locks, wire_production_locks
from ttskit.paths import Paths
from ttskit.plan import clip_key, orphan_locks
from ttskit.store import Lock, Locks


def write_wav(path, seconds: float = 0.2, sr: int = 24000) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    t = np.linspace(0, seconds, int(sr * seconds), endpoint=False)
    sf.write(path, (0.3 * np.sin(2 * np.pi * 440 * t)).astype(np.float32),
             sr, subtype="PCM_16")


def test_migrate_moves_orphan_word_lock_to_phoneme(tmp_path, content_dir):
    paths = Paths(root=tmp_path, content_dir=content_dir,
                  app_audio_dir=tmp_path / "app-audio")
    paths.locks.parent.mkdir(parents=True, exist_ok=True)
    word_key = clip_key("word", "M")
    locks = Locks()
    locks.set(word_key, Lock(seed=42, source_text="M", text_override="mm"))
    locks.save(paths.locks)

    report = migrate_word_locks(paths)

    phoneme_key = clip_key("phoneme", "M")
    assert (word_key, phoneme_key) in report.locks_moved
    reloaded = Locks.load(paths.locks)
    assert reloaded.get(word_key) is None
    assert reloaded.get(phoneme_key) is not None
    assert reloaded.get(phoneme_key).seed == 42
    ctx = load_context(paths)
    assert word_key not in orphan_locks(ctx.locks, ctx.clips)


def test_migrate_prefers_index_committed_word_audio(tmp_path, content_dir):
    paths = Paths(root=tmp_path, content_dir=content_dir,
                  app_audio_dir=tmp_path / "app-audio")
    paths.locks.parent.mkdir(parents=True, exist_ok=True)
    paths.audio.mkdir(parents=True)
    paths.app_audio_dir.mkdir(parents=True)

    word_key = clip_key("word", "M")
    phoneme_key = clip_key("phoneme", "M")
    locks = Locks()
    locks.set(word_key, Lock(seed=1))
    locks.set(phoneme_key, Lock(seed=2))
    locks.save(paths.locks)

    (paths.audio / f"{word_key}.wav").write_bytes(b"word-bytes")
    (paths.audio / f"{phoneme_key}.wav").write_bytes(b"phoneme-bytes")
    (paths.app_audio_dir / "index.json").write_text(json.dumps({
        "version": 1,
        "clips": {"M": {"file": asset_name(word_key), "profile": "word",
                        "fingerprint": "abc"}},
    }), encoding="utf-8")

    migrate_word_locks(paths)

    assert (paths.audio / f"{phoneme_key}.wav").read_bytes() == b"word-bytes"


def test_wire_locks_rendered_clip_without_lock(tmp_path, content_dir):
    paths = Paths(root=tmp_path, content_dir=content_dir,
                  app_audio_dir=tmp_path / "app-audio")
    paths.locks.parent.mkdir(parents=True, exist_ok=True)
    paths.audio.mkdir(parents=True)

    ctx = load_context(paths)
    clip = next(c for c in ctx.clips if c.source_text == "Mama.")
    assert not clip.locked
    write_wav(paths.audio / f"{clip.key}.wav")

    report = wire_production_locks(paths)
    assert (clip.key, report.locked[0][1]) in report.locked
    reloaded = Locks.load(paths.locks)
    assert reloaded.get(clip.key) is not None
