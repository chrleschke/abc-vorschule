import os
from pathlib import Path

import numpy as np
import pytest

from ttskit.engine import Engine, pick_device
from ttskit.store import Profiles

SMOKE = os.environ.get("TTS_SMOKE") == "1"
smoke = pytest.mark.skipif(not SMOKE, reason="needs the model; set TTS_SMOKE=1")


def test_pick_device_returns_something_torch_understands():
    assert pick_device() in {"mps", "cuda", "cpu"}


def test_validate_reports_a_bad_speaker_without_loading():
    class FakeModel:
        def get_supported_speakers(self):
            return ["sohee", "ryan"]

        def get_supported_languages(self):
            return ["german", "english"]

    engine = Engine()
    engine._model = FakeModel()
    engine.loaded = True

    profiles = Profiles.load(Path("nope.json"))
    profiles.profiles["prompt"].speaker = "gandalf"
    errors = engine.validate(profiles)
    assert len(errors) == 1
    assert "gandalf" in errors[0]
    assert "sohee" in errors[0], "the error must list the valid options"


def test_validate_reports_a_bad_language():
    class FakeModel:
        def get_supported_speakers(self):
            return ["sohee"]

        def get_supported_languages(self):
            return ["german"]

    engine = Engine()
    engine._model = FakeModel()
    engine.loaded = True

    profiles = Profiles.load(Path("nope.json"))
    profiles.profiles["word"].language = "klingon"
    errors = engine.validate(profiles)
    assert any("klingon" in e for e in errors)


def test_validate_passes_for_the_defaults():
    class FakeModel:
        def get_supported_speakers(self):
            return ["sohee"]

        def get_supported_languages(self):
            return ["german"]

    engine = Engine()
    engine._model = FakeModel()
    engine.loaded = True
    assert engine.validate(Profiles.load(Path("nope.json"))) == []


def test_generate_before_load_raises():
    profiles = Profiles.load(Path("nope.json"))
    with pytest.raises(RuntimeError, match="not loaded"):
        Engine().generate("Hallo", profiles.profiles["word"], 42)


@smoke
def test_same_seed_gives_bit_identical_audio():
    """The assumption the entire seed-locking design rests on."""
    engine = Engine()
    engine.load()
    assert engine.loaded, engine.load_error
    profile = Profiles.load(Path("profiles.json")).profiles["prompt"]

    first, sr_a = engine.generate("Wo hörst du den Buchstaben M?", profile, 42)
    second, sr_b = engine.generate("Wo hörst du den Buchstaben M?", profile, 42)
    third, _ = engine.generate("Wo hörst du den Buchstaben M?", profile, 99)

    assert sr_a == sr_b == 24000
    assert np.array_equal(first, second)
    assert not np.array_equal(first, third)
    assert np.max(np.abs(first)) > 0.01, "must not be silence"
