"""Die Stimmtabelle ist von Hand gepflegt — hier steht, was sie garantieren muss."""

import json
from pathlib import Path

import pytest

from ttskit import voices
from ttskit.store import DEFAULT_PROFILES

#: Die Modell-Config, aus der Namen und Sprachen wirklich stammen. Nur da, wo
#: sie im HF-Cache liegt — sonst wird der Abgleich übersprungen statt geraten.
CONFIG = Path.home() / (
    ".cache/huggingface/hub/models--Qwen--Qwen3-TTS-12Hz-1.7B-CustomVoice"
    "/snapshots/0c0e3051f131929182e2c023b9537f8b1c68adfe/config.json")


def test_every_default_profile_names_a_known_voice_and_language():
    for name, profile in DEFAULT_PROFILES["profiles"].items():
        assert voices.voice(profile["speaker"]) is not None, \
            f"Profil {name!r} nennt eine Stimme, die die Tabelle nicht kennt"
        assert profile["language"] in voices.LANGUAGES


def test_the_default_voice_is_flagged_as_an_accent_risk_for_german():
    """Der Auslöser für diese Tabelle: alle acht Profile stehen auf `sohee`,
    einer koreanischen Stimme, und einzelne Laute klingen entsprechend. Die
    Stimme zu wechseln ist eine Hörentscheidung und passiert im UI — was der
    Code schuldet, ist der sichtbare Hinweis darauf."""
    for name, profile in DEFAULT_PROFILES["profiles"].items():
        assert profile["language"] == "german", name
    assert voices.accent_risk(
        DEFAULT_PROFILES["profiles"]["phoneme"]["speaker"], "german")


def test_accent_risk_only_fires_when_voice_and_text_disagree():
    assert voices.accent_risk("sohee", "german") is True
    assert voices.accent_risk("sohee", "korean") is False
    assert voices.accent_risk("serena", "german") is True, \
        "serena ist chinesisch, nicht westlich — siehe Modul-Docstring"
    assert voices.accent_risk("serena", "chinese") is False
    assert voices.accent_risk("ryan", "german") is False
    assert voices.accent_risk("gibtsnicht", "german") is False, \
        "über eine unbekannte Stimme lässt sich nichts behaupten"


def test_no_european_voice_is_female():
    """Der teuerste Irrtum dieser Tabelle, festgenagelt.

    `serena` und `vivian` standen hier als „westlich, weiblich" — beide sind
    chinesisch. Damit gibt es für deutschen Text überhaupt keine akzentfreie
    Frauenstimme, und jede Runde „warum klingt das asiatisch" endet wieder
    hier. Der Abgleich gegen die Modell-Config kann das nicht prüfen: dort
    stehen nur die Namen. Also prüft es dieser Test."""
    european = [v.name for v in voices.VOICES if v.european]
    assert european == ["ryan", "aiden"], european
    for name in european:
        assert "männlich" in voices.origin_of(name)


def test_origin_is_stated_for_every_voice_and_guessed_for_none():
    for voice in voices.VOICES:
        assert voice.origin and voice.origin != "unbekannte Herkunft"
    assert voices.origin_of("gibtsnicht") == "unbekannte Herkunft"


def test_european_voices_come_first_so_the_dropdown_starts_usable():
    european = [i for i, v in enumerate(voices.VOICES) if v.european]
    other = [i for i, v in enumerate(voices.VOICES) if not v.european]
    assert european and other
    assert max(european) < min(other)


@pytest.mark.skipif(not CONFIG.exists(), reason="Modell-Config nicht im Cache")
def test_the_table_matches_the_model_config():
    """Eine Stimme, die es im Modell nicht gibt, wird von `build_clips`
    akzeptiert und schlägt erst beim Rendern fehl — Clip für Clip."""
    talker = json.loads(CONFIG.read_text(encoding="utf-8"))["talker_config"]
    assert set(voices.speaker_names()) == set(talker["spk_id"])
    assert set(voices.LANGUAGES) == {
        lang for lang in talker["codec_language_id"] if "dialect" not in lang}


@pytest.mark.skipif(not CONFIG.exists(), reason="Modell-Config nicht im Cache")
def test_the_chinese_dialect_voices_are_marked_as_such():
    talker = json.loads(CONFIG.read_text(encoding="utf-8"))["talker_config"]
    for name, dialect in talker["spk_is_dialect"].items():
        if dialect:
            assert not voices.voice(name).european
            # Die Herkunft steht auf Deutsch da ("Peking-Dialekt"), der
            # Config-Schlüssel auf Englisch ("beijing_dialect") — geprüft wird
            # deshalb nur, dass die Herkunft den Dialekt überhaupt benennt.
            origin = voices.origin_of(name).lower()
            assert "chinesisch" in origin and "dialekt" in origin, origin
