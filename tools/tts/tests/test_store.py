import json
from pathlib import Path

import pytest

from ttskit import store
from ttskit.store import DEFAULT_PROFILES, Lock, Locks, Profiles, RenderState


def test_profiles_load_falls_back_to_defaults(tmp_path):
    profiles = Profiles.load(tmp_path / "missing.json")
    assert set(profiles.profiles) == {
        "word", "phoneme", "prompt", "miss", "reward", "sentence", "finale", "ui",
    }
    assert profiles.pool_salt == "v1"


def test_every_default_profile_is_complete():
    for name, raw in DEFAULT_PROFILES["profiles"].items():
        assert raw["speaker"] == "sohee", name
        assert raw["language"] == "german", name
        assert raw["instruct"].strip(), name
        assert raw["sampling"] == {
            "temperature": 0.6, "top_k": 30, "top_p": 0.9, "repetition_penalty": 1.05,
        }, name
        assert raw["seedPool"] == [], name


def test_profiles_roundtrip(tmp_path):
    path = tmp_path / "profiles.json"
    original = Profiles.load(path)
    original.profiles["prompt"].seed_pool = [42, 1337]
    original.pool_salt = "v2"
    original.save(path)

    reloaded = Profiles.load(path)
    assert reloaded.profiles["prompt"].seed_pool == [42, 1337]
    assert reloaded.pool_salt == "v2"
    assert reloaded.profiles["prompt"].instruct == original.profiles["prompt"].instruct


def test_profiles_save_is_git_friendly(tmp_path):
    path = tmp_path / "profiles.json"
    Profiles.load(path).save(path)
    raw = path.read_text(encoding="utf-8")
    assert raw.endswith("\n")
    assert "\n  " in raw, "expected indent=2"
    assert "\\u" not in raw, "expected ensure_ascii=False so umlauts stay readable"


def test_locks_roundtrip_with_optional_fields(tmp_path):
    path = tmp_path / "locks.json"
    locks = Locks.load(path)
    locks.set("phoneme:9f2c1a7b4e08", Lock(
        seed=991, profile=None, text_override="mmmmm",
        note="sprach sonst 'Em'", source_text="M"))
    locks.set("prompt:aaaabbbbcccc", Lock(
        seed=7, profile=None, text_override=None, note=None, source_text="Wo?"))
    locks.save(path)

    reloaded = Locks.load(path)
    first = reloaded.get("phoneme:9f2c1a7b4e08")
    assert first.seed == 991
    assert first.text_override == "mmmmm"
    assert first.source_text == "M"

    second = reloaded.get("prompt:aaaabbbbcccc")
    assert second.seed == 7
    assert second.text_override is None
    # Absent optional fields must not be written out as nulls.
    raw = json.loads(path.read_text(encoding="utf-8"))
    assert raw["locks"]["prompt:aaaabbbbcccc"] == {"seed": 7, "sourceText": "Wo?"}


def test_locks_remove(tmp_path):
    locks = Locks.load(tmp_path / "locks.json")
    locks.set("a:1", Lock(seed=1, profile=None, text_override=None, note=None, source_text=None))
    locks.remove("a:1")
    assert locks.get("a:1") is None
    locks.remove("a:1")  # removing twice must not raise


def test_corrupt_json_raises_with_the_path(tmp_path):
    path = tmp_path / "locks.json"
    path.write_text("{ not json", encoding="utf-8")
    with pytest.raises(ValueError, match="locks.json"):
        Locks.load(path)


def test_failed_write_leaves_the_original_file_intact_and_no_stray_temp_file(
    tmp_path, monkeypatch,
):
    path = tmp_path / "profiles.json"
    profiles = Profiles.load(path)
    profiles.save(path)
    original = path.read_text(encoding="utf-8")

    def boom(*args, **kwargs):
        raise RuntimeError("boom")

    monkeypatch.setattr(store.json, "dumps", boom)
    profiles.profiles["prompt"].instruct = "würde nie geschrieben"
    with pytest.raises(RuntimeError, match="boom"):
        profiles.save(path)

    assert path.read_text(encoding="utf-8") == original, "original file untouched"
    assert list(tmp_path.iterdir()) == [path], "no stray temp file left behind"


def test_a_truncated_profiles_file_is_an_error_not_a_silent_reset(tmp_path):
    """The worst possible outcome for this file is losing every curated seed
    pool without a word — so `{}` must raise, not fall back to the defaults."""
    path = tmp_path / "profiles.json"
    path.write_text("{}", encoding="utf-8")
    with pytest.raises(ValueError) as excinfo:
        Profiles.load(path)
    assert "profiles.json" in str(excinfo.value)
    assert "profiles" in str(excinfo.value)


def test_a_profile_missing_a_required_key_names_the_file_and_the_profile(tmp_path):
    path = tmp_path / "profiles.json"
    path.write_text(json.dumps({"poolSalt": "v1", "profiles": {
        "word": {"speaker": "sohee", "language": "german", "instruct": "x"},
    }}), encoding="utf-8")
    with pytest.raises(ValueError) as excinfo:
        Profiles.load(path)
    assert "profiles.json" in str(excinfo.value)
    assert "'word'" in str(excinfo.value)
    assert "label" in str(excinfo.value)


def test_profiles_must_be_an_object(tmp_path):
    path = tmp_path / "profiles.json"
    path.write_text(json.dumps({"profiles": []}), encoding="utf-8")
    with pytest.raises(ValueError, match="profiles.json"):
        Profiles.load(path)


def test_an_empty_locks_file_is_an_error_not_a_silent_reset(tmp_path):
    path = tmp_path / "locks.json"
    path.write_text("[]", encoding="utf-8")
    with pytest.raises(ValueError, match="locks.json"):
        Locks.load(path)


def test_a_locks_file_that_parses_to_an_empty_object_is_simply_empty(tmp_path):
    path = tmp_path / "locks.json"
    path.write_text("{}", encoding="utf-8")
    assert Locks.load(path).locks == {}


def test_a_lock_without_a_seed_names_the_file_and_the_key(tmp_path):
    path = tmp_path / "locks.json"
    path.write_text(json.dumps({"version": 1, "locks": {
        "prompt:aaaabbbbcccc": {"profile": "word"},
    }}), encoding="utf-8")
    with pytest.raises(ValueError) as excinfo:
        Locks.load(path)
    assert "locks.json" in str(excinfo.value)
    assert "prompt:aaaabbbbcccc" in str(excinfo.value)
    assert "seed" in str(excinfo.value)


def test_a_lock_with_a_non_numeric_seed_names_the_file_and_the_key(tmp_path):
    path = tmp_path / "locks.json"
    path.write_text(json.dumps({"version": 1, "locks": {
        "prompt:aaaabbbbcccc": {"seed": "gleich wie vorher"},
    }}), encoding="utf-8")
    with pytest.raises(ValueError, match="prompt:aaaabbbbcccc"):
        Locks.load(path)


def test_locks_remember_where_they_came_from(tmp_path):
    path = tmp_path / "locks.json"
    assert Locks.load(path).source == path


def test_a_malformed_render_state_is_an_error_not_a_silent_reset(tmp_path):
    path = tmp_path / "render-state.json"
    path.write_text(json.dumps({"failures": []}), encoding="utf-8")
    with pytest.raises(ValueError, match="render-state.json"):
        RenderState.load(path)


def test_render_state_roundtrips_failures(tmp_path):
    path = tmp_path / "render-state.json"
    state = RenderState.load(path)
    assert state.failures == {}
    state.failures["prompt:def456def456"] = "RuntimeError: kaputt"
    state.save(path)

    reloaded = RenderState.load(path)
    assert reloaded.failures == {"prompt:def456def456": "RuntimeError: kaputt"}


def test_an_old_render_state_with_a_legacy_entries_key_still_loads(tmp_path):
    """`entries` (Render-Fingerprints) gibt es nicht mehr — eine alte Datei mit
    diesem Schlüssel darf trotzdem laden, statt mit einem Fehler abzubrechen."""
    path = tmp_path / "render-state.json"
    path.write_text(json.dumps({"version": 1, "entries": {"a:1": "fp"}}), encoding="utf-8")
    assert RenderState.load(path).failures == {}
