import json
import shutil
from pathlib import Path

import numpy as np
import soundfile as sf

from ttskit.cli import load_context
from ttskit.export import asset_name, export_to_app
from ttskit.paths import Paths
from ttskit.plan import fingerprint


def make_paths(tmp_path: Path, content_dir: Path) -> Paths:
    return Paths(root=tmp_path, content_dir=content_dir,
                 app_audio_dir=tmp_path / "app-audio")


def write_wav(path: Path, seconds: float = 0.2, sr: int = 24000) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    t = np.linspace(0, seconds, int(sr * seconds), endpoint=False)
    sf.write(path, (0.3 * np.sin(2 * np.pi * 440 * t)).astype(np.float32),
             sr, subtype="PCM_16")


def lock_and_render(paths: Paths, key: str) -> None:
    """Lockt `key` und legt die passende WAV unter out/audio/ an."""
    locks_file = paths.locks
    data = (json.loads(locks_file.read_text()) if locks_file.exists()
            else {"version": 1, "locks": {}})
    data["locks"][key] = {"seed": 1}
    locks_file.write_text(json.dumps(data), encoding="utf-8")

    write_wav(paths.audio / f"{key}.wav")


def clip_key_for_text(paths: Paths, text: str) -> str:
    ctx = load_context(paths)
    return next(c.key for c in ctx.clips if c.source_text == text)


def test_asset_name_replaces_colon():
    assert asset_name("sentence:0620b64d3955") == "sentence_0620b64d3955.ogg"


def test_exports_locked_rendered_clip_as_ogg(tmp_path, content_dir):
    paths = make_paths(tmp_path, content_dir)
    key = clip_key_for_text(paths, "Mama.")
    lock_and_render(paths, key)

    report = export_to_app(paths)

    assert report.exported == [key]
    ogg = paths.app_audio_dir / asset_name(key)
    assert ogg.exists()
    data, sr = sf.read(ogg)
    assert sr == 24000 and len(data) > 0
    index = json.loads((paths.app_audio_dir / "index.json").read_text())
    entry = index["clips"]["Mama."]
    assert entry["file"] == asset_name(key)
    assert entry["profile"] == "sentence"
    assert isinstance(entry["fingerprint"], str) and len(entry["fingerprint"]) == 16


def test_skips_unlocked_and_missing_locked_clips(tmp_path, content_dir):
    paths = make_paths(tmp_path, content_dir)
    missing_key = clip_key_for_text(paths, "Maus")
    lock_and_render(paths, missing_key)
    (paths.audio / f"{missing_key}.wav").unlink()

    report = export_to_app(paths)

    assert report.exported == []
    reasons = dict(report.skipped)
    assert "missing" in reasons[missing_key]
    # Ungelockte Clips tauchen gar nicht erst im Bericht auf:
    assert len(report.skipped) == 1
    index = json.loads((paths.app_audio_dir / "index.json").read_text())
    assert index["clips"] == {}


def test_orphan_lock_is_reported_not_exported(tmp_path, content_dir):
    paths = make_paths(tmp_path, content_dir)
    paths.locks.write_text(json.dumps(
        {"version": 1, "locks": {"sentence:deadbeef0000": {"seed": 1}}}),
        encoding="utf-8")

    report = export_to_app(paths)

    assert report.exported == []
    assert any(key == "sentence:deadbeef0000" and "verwaist" in reason
               for key, reason in report.skipped)


def test_sync_removes_orphaned_ogg_but_keeps_foreign_files(tmp_path, content_dir):
    paths = make_paths(tmp_path, content_dir)
    paths.app_audio_dir.mkdir(parents=True)
    (paths.app_audio_dir / "word_000000000000.ogg").write_bytes(b"old")
    (paths.app_audio_dir / "notes.txt").write_text("bleibt")

    key = clip_key_for_text(paths, "Mama.")
    lock_and_render(paths, key)
    report = export_to_app(paths)

    assert not (paths.app_audio_dir / "word_000000000000.ogg").exists()
    assert (paths.app_audio_dir / "notes.txt").exists()
    assert report.removed == ["word_000000000000.ogg"]


def test_collision_prefers_word_over_phoneme(tmp_path, content_dir):
    # "M" existiert als lemma (word) und als phonemeTts/stretchTts (phoneme).
    paths = make_paths(tmp_path, content_dir)
    ctx = load_context(paths)
    word_key = next(c.key for c in ctx.clips
                    if c.source_text == "M" and c.profile == "word")
    phoneme_key = next(c.key for c in ctx.clips
                       if c.source_text == "M" and c.profile == "phoneme")
    lock_and_render(paths, word_key)
    lock_and_render(paths, phoneme_key)

    report = export_to_app(paths)

    index = json.loads((paths.app_audio_dir / "index.json").read_text())
    assert index["clips"]["M"]["profile"] == "word"
    assert any("M" in w for w in report.warnings)
    # Beide OGGs liegen trotzdem da — nur der Index-Eintrag ist eindeutig.
    assert (paths.app_audio_dir / asset_name(phoneme_key)).exists()


def test_fresh_checkout_keeps_existing_assets_when_local_state_missing(tmp_path, content_dir):
    """out/ is gitignored. On a fresh clone locks + committed assets/index
    exist but the WAV and render-state don't — every locked clip reads as
    `missing`. Deletion must track unlocks, not local render state: the
    committed OGG and its index entry have to survive untouched."""
    paths = make_paths(tmp_path, content_dir)
    key = clip_key_for_text(paths, "Mama.")
    lock_and_render(paths, key)
    first = export_to_app(paths)
    assert first.exported == [key]
    ogg = paths.app_audio_dir / asset_name(key)
    ogg_bytes = ogg.read_bytes()
    index_before = json.loads((paths.app_audio_dir / "index.json").read_text())

    # Simulate a fresh checkout: out/ (WAV + render-state) is gone, only the
    # lock and the previously committed assets/index remain.
    shutil.rmtree(paths.out)

    report = export_to_app(paths)

    assert report.exported == []
    assert report.removed == []
    assert ogg.exists()
    assert ogg.read_bytes() == ogg_bytes
    reasons = dict(report.skipped)
    assert "missing" in reasons[key]
    assert "vorhandene Datei bleibt erhalten" in reasons[key]
    index_after = json.loads((paths.app_audio_dir / "index.json").read_text())
    assert index_after == index_before


def test_unlock_deletes_kept_file_and_index_entry(tmp_path, content_dir):
    """Deletion tracks unlocks: once a clip is no longer locked at all, its
    asset and index entry must disappear — even if it was previously kept
    across a run where it was merely locked without a local render."""
    paths = make_paths(tmp_path, content_dir)
    key = clip_key_for_text(paths, "Mama.")
    lock_and_render(paths, key)
    export_to_app(paths)
    ogg = paths.app_audio_dir / asset_name(key)
    assert ogg.exists()

    locks_data = json.loads(paths.locks.read_text())
    del locks_data["locks"][key]
    paths.locks.write_text(json.dumps(locks_data), encoding="utf-8")

    report = export_to_app(paths)

    assert not ogg.exists()
    assert report.removed == [asset_name(key)]
    index = json.loads((paths.app_audio_dir / "index.json").read_text())
    assert index["clips"] == {}


def test_export_is_deterministic(tmp_path, content_dir):
    paths = make_paths(tmp_path, content_dir)
    key = clip_key_for_text(paths, "Mama.")
    lock_and_render(paths, key)
    export_to_app(paths)
    first = (paths.app_audio_dir / "index.json").read_bytes()
    export_to_app(paths)
    assert (paths.app_audio_dir / "index.json").read_bytes() == first


def test_second_export_leaves_ogg_bytes_untouched(tmp_path, content_dir):
    """OGG/Opus embeds a random Ogg-Bitstream-Seriennummer pro Encode — ohne
    den Fingerprint-Vergleich würde jeder Lauf alle Dateien neu schreiben,
    obwohl sich nichts geändert hat."""
    paths = make_paths(tmp_path, content_dir)
    key = clip_key_for_text(paths, "Mama.")
    lock_and_render(paths, key)

    first_report = export_to_app(paths)
    assert first_report.exported == [key]
    assert first_report.unchanged == []
    ogg = paths.app_audio_dir / asset_name(key)
    first_bytes = ogg.read_bytes()

    second_report = export_to_app(paths)

    assert second_report.exported == []
    assert second_report.unchanged == [key]
    assert ogg.read_bytes() == first_bytes


def test_fingerprint_change_forces_reencode(tmp_path, content_dir):
    paths = make_paths(tmp_path, content_dir)
    key = clip_key_for_text(paths, "Mama.")
    lock_and_render(paths, key)
    export_to_app(paths)
    ogg = paths.app_audio_dir / asset_name(key)
    first_bytes = ogg.read_bytes()

    # Ein neuer Seed macht den Clip fachlich zu einer neuen Aufnahme — die WAV
    # bliebe im echten Ablauf durch `tts render` neu erzeugt; hier reicht die
    # bestehende Datei, um den Re-Encode-Pfad über den geänderten Fingerprint
    # zu prüfen (Text/Profil/Stimme/Instruktion/Sampling bleiben gleich, nur
    # der Seed ändert sich).
    locks_data = json.loads(paths.locks.read_text())
    locks_data["locks"][key]["seed"] = 2
    paths.locks.write_text(json.dumps(locks_data), encoding="utf-8")

    ctx = load_context(paths)
    clip = next(c for c in ctx.clips if c.key == key)
    new_fp = fingerprint(clip, ctx.profiles.profiles[clip.profile])

    report = export_to_app(paths)

    assert report.exported == [key]
    assert report.unchanged == []
    assert ogg.read_bytes() != first_bytes
    index = json.loads((paths.app_audio_dir / "index.json").read_text())
    assert index["clips"]["Mama."]["fingerprint"] == new_fp
