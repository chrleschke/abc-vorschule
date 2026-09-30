"""Schnitt in der Wellenform: verlustfrei, aufziehbar, zieht Produktion und Export mit."""

import json
from dataclasses import replace

import numpy as np
import pytest
import soundfile as sf

from ttskit.models import Item
from ttskit.paths import Paths
from ttskit.plan import build_clips
from ttskit.render import (
    candidate_infos, candidate_original_path, delete_candidate_wav,
    production_fingerprint, sample_candidates, trim_candidate,
)
from ttskit.store import Locks, Profiles

SR = 24000


class RampEngine:
    """Eine Sekunde Rampe 0 → 1, damit sich jeder Schnitt am Wert ablesen lässt."""

    def generate(self, text, profile, seed):
        return np.linspace(0.0, 1.0, SR, dtype=np.float32) * 0.5, SR


@pytest.fixture
def setup(tmp_path):
    paths = Paths(root=tmp_path, content_dir=tmp_path / "content")
    profiles = Profiles.load(tmp_path / "nope.json")
    profile_name = "prompt"
    items = [Item("task:t1:round:0:promptTts", "Frage eins?", "promptTts", "tasks.json", "l01", "a")]
    clip = build_clips(items, profiles, Locks())[0]
    profile = profiles.profiles[clip.profile]
    # Keine Nachbearbeitung: die Rampe soll unverändert ankommen.
    profile.trim = False
    profile.normalize = False
    sample_candidates(clip, profile, RampEngine(), paths, [7])
    assert profile_name == clip.profile
    return paths, clip, profile


def read(path):
    data, sr = sf.read(path, dtype="float32")
    assert sr == SR
    return data


def test_trim_cuts_both_ends_and_keeps_the_original(setup):
    paths, clip, _ = setup
    wav = paths.candidates / clip.key / "7.wav"
    meta = trim_candidate(paths, clip, 7, 0.25, 0.75)
    assert meta["trim"] == {"start": 0.25, "end": 0.75, "duration": 1.0}
    assert len(read(wav)) == SR // 2
    orig = candidate_original_path(paths, clip.key, 7)
    assert orig is not None and len(read(orig)) == SR


def test_second_trim_starts_from_the_original(setup):
    paths, clip, _ = setup
    trim_candidate(paths, clip, 7, 0.4, 0.6)
    trim_candidate(paths, clip, 7, 0.1, 0.9)   # wieder aufziehen
    assert len(read(paths.candidates / clip.key / "7.wav")) == int(0.8 * SR)


def test_full_length_restores_the_original(setup):
    paths, clip, _ = setup
    trim_candidate(paths, clip, 7, 0.2, 0.8)
    meta = trim_candidate(paths, clip, 7, 0.0, 1.0)
    assert "trim" not in meta
    assert candidate_original_path(paths, clip.key, 7) is None
    assert not (paths.candidates / clip.key / "7.orig.wav").exists()
    assert len(read(paths.candidates / clip.key / "7.wav")) == SR


def test_trim_rewrites_production_and_changes_the_export_fingerprint(setup):
    paths, clip, profile = setup
    production = paths.audio / f"{clip.key}.wav"
    production.parent.mkdir(parents=True, exist_ok=True)
    production.write_bytes((paths.candidates / clip.key / "7.wav").read_bytes())
    locked = replace(clip, seed=7)
    before = production_fingerprint(paths, locked, profile)
    trim_candidate(paths, locked, 7, 0.0, 0.5)
    assert len(read(production)) == SR // 2
    assert production_fingerprint(paths, locked, profile) != before


def test_trim_leaves_foreign_production_alone(setup):
    paths, clip, _ = setup
    production = paths.audio / f"{clip.key}.wav"
    production.parent.mkdir(parents=True, exist_ok=True)
    sf.write(production, np.zeros(100, dtype=np.float32), SR, subtype="PCM_16")
    trim_candidate(paths, replace(clip, seed=99), 7, 0.0, 0.5)
    assert len(read(production)) == 100


@pytest.mark.parametrize("start,end", [(-0.1, 0.5), (0.6, 0.5), (0.0, 1.5), (0.5, 0.52)])
def test_invalid_ranges_are_rejected(setup, start, end):
    paths, clip, _ = setup
    with pytest.raises(ValueError):
        trim_candidate(paths, clip, 7, start, end)


def test_microphone_recordings_are_rejected(setup):
    paths, clip, _ = setup
    meta_path = paths.candidates / clip.key / "7.json"
    meta = json.loads(meta_path.read_text()); meta["source"] = "mic"
    meta_path.write_text(json.dumps(meta))
    with pytest.raises(ValueError):
        trim_candidate(paths, clip, 7, 0.1, 0.9)


def test_new_take_on_the_same_seed_drops_the_old_trim(setup):
    paths, clip, profile = setup
    trim_candidate(paths, clip, 7, 0.2, 0.8)
    sample_candidates(clip, profile, RampEngine(), paths, [7])
    assert candidate_original_path(paths, clip.key, 7) is None
    assert not (paths.candidates / clip.key / "7.orig.wav").exists()
    assert candidate_infos(paths, clip, profile)[0]["trim"] is None


def test_delete_removes_the_original_too(setup):
    paths, clip, _ = setup
    trim_candidate(paths, clip, 7, 0.2, 0.8)
    delete_candidate_wav(paths, clip, 7)
    assert list((paths.candidates / clip.key).glob("7*")) == []


def test_candidate_list_reports_the_trim(setup):
    paths, clip, profile = setup
    trim_candidate(paths, clip, 7, 0.25, 0.75)
    info = candidate_infos(paths, clip, profile)[0]
    assert info["trim"] == {"start": 0.25, "end": 0.75, "duration": 1.0}
    assert [i["seed"] for i in candidate_infos(paths, clip, profile)] == [7]
