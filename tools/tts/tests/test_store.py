import json
from pathlib import Path

import pytest

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


def test_render_state_roundtrip(tmp_path):
    path = tmp_path / "render-state.json"
    state = RenderState.load(path)
    assert state.entries == {}
    state.entries["prompt:abc123abc123"] = "fingerprint-1"
    state.save(path)
    assert RenderState.load(path).entries == {"prompt:abc123abc123": "fingerprint-1"}


def test_corrupt_json_raises_with_the_path(tmp_path):
    path = tmp_path / "locks.json"
    path.write_text("{ not json", encoding="utf-8")
    with pytest.raises(ValueError, match="locks.json"):
        Locks.load(path)
