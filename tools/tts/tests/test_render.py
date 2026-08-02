import numpy as np
import pytest

from ttskit.models import Item
from ttskit.paths import Paths
from ttskit.plan import build_clips
from ttskit.render import random_seeds, render_clips, sample_candidates
from ttskit.store import Locks, Profiles, RenderState


class FakeEngine:
    """Deterministic stand-in: audio depends on text and seed, nothing else."""

    def __init__(self, fail_on: set[str] | None = None) -> None:
        self.loaded = True
        self.calls: list[tuple[str, int]] = []
        self.fail_on = fail_on or set()

    def generate(self, text, profile, seed):
        self.calls.append((text, seed))
        if text in self.fail_on:
            raise RuntimeError("model exploded")
        rng = np.random.default_rng(abs(hash((text, seed))) % (2 ** 32))
        return rng.standard_normal(2400).astype(np.float32) * 0.5, 24000


@pytest.fixture
def setup(tmp_path):
    paths = Paths(root=tmp_path, content_dir=tmp_path / "content")
    profiles = Profiles.load(tmp_path / "nope.json")
    items = [
        Item("task:t1:round:0:promptTts", "Frage eins?", "promptTts", "tasks.json", "l01", "a"),
        Item("task:t2:round:0:promptTts", "Frage zwei?", "promptTts", "tasks.json", "l01", "b"),
        Item("task:t3:round:0:rewardTts", "Super!", "rewardTts", "tasks.json", "l01", "c"),
    ]
    clips = build_clips(items, profiles, Locks())
    return paths, profiles, clips, RenderState()


def test_renders_every_missing_clip(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    report = render_clips(clips, profiles, engine, state, paths)
    assert report.rendered == 3
    assert report.skipped == 0
    assert report.failed == []
    for clip in clips:
        assert (paths.audio / f"{clip.key}.wav").exists()


def test_second_run_skips_everything(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    render_clips(clips, profiles, engine, state, paths)
    report = render_clips(clips, profiles, engine, state, paths)
    assert report.rendered == 0
    assert report.skipped == 3


def test_changing_an_instruct_re_renders_only_that_profile(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    render_clips(clips, profiles, engine, state, paths)

    profiles.profiles["prompt"].instruct = "Ganz anders sprechen."
    report = render_clips(clips, profiles, engine, state, paths)
    assert report.rendered == 2, "the two prompt clips"
    assert report.skipped == 1, "the reward clip is untouched"


def test_force_re_renders_everything(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    render_clips(clips, profiles, engine, state, paths)
    report = render_clips(clips, profiles, engine, state, paths, force=True)
    assert report.rendered == 3


def test_profile_filter(setup):
    paths, profiles, clips, state = setup
    report = render_clips(clips, profiles, FakeEngine(), state, paths, profile="reward")
    assert report.rendered == 1


def test_only_glob_filter(setup):
    paths, profiles, clips, state = setup
    reward = next(c for c in clips if c.profile == "reward")
    report = render_clips(clips, profiles, FakeEngine(), state, paths, only=f"{reward.key}")
    assert report.rendered == 1


def test_dry_run_writes_nothing(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    report = render_clips(clips, profiles, engine, state, paths, dry_run=True)
    assert report.dry_run is True
    assert report.rendered == 3
    assert engine.calls == []
    assert not paths.audio.exists() or list(paths.audio.iterdir()) == []


def test_a_failing_clip_does_not_stop_the_batch(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine(fail_on={"Frage eins?"})
    report = render_clips(clips, profiles, engine, state, paths)
    assert report.rendered == 2
    assert len(report.failed) == 1
    assert "model exploded" in report.failed[0][1]


def test_state_is_written_after_each_clip(setup):
    paths, profiles, clips, state = setup

    seen: list[int] = []

    def progress(p):
        seen.append(len(RenderState.load(paths.render_state).entries))

    render_clips(clips, profiles, FakeEngine(), state, paths, progress=progress)
    assert seen == [1, 2, 3], "state must grow as the run proceeds, not at the end"


def test_cancel_stops_the_run(setup):
    paths, profiles, clips, state = setup
    calls = {"n": 0}

    def cancel():
        calls["n"] += 1
        return calls["n"] > 1

    report = render_clips(clips, profiles, FakeEngine(), state, paths, cancel=cancel)
    assert report.rendered < 3


def test_progress_reports_index_and_total(setup):
    paths, profiles, clips, state = setup
    seen = []
    render_clips(clips, profiles, FakeEngine(), state, paths,
                 progress=lambda p: seen.append((p.index, p.total)))
    assert seen == [(1, 3), (2, 3), (3, 3)]


def test_random_seeds_are_unique_and_avoid_exclusions():
    seeds = random_seeds(6, exclude={1, 2, 3})
    assert len(seeds) == len(set(seeds)) == 6
    assert not ({1, 2, 3} & set(seeds))
    assert all(0 <= s < 2 ** 31 for s in seeds)


def test_sample_candidates_writes_one_file_per_seed(setup):
    paths, profiles, clips, state = setup
    clip = clips[0]
    written = sample_candidates(clip, profiles.profiles[clip.profile], FakeEngine(),
                                paths, seeds=[7, 8, 9])
    assert written == [7, 8, 9]
    for seed in written:
        assert (paths.candidates / clip.key / f"{seed}.wav").exists()


def test_sample_candidates_cancel_stops_the_run(setup):
    paths, profiles, clips, state = setup
    clip = clips[0]
    calls = {"n": 0}

    def cancel():
        calls["n"] += 1
        return calls["n"] > 1

    written = sample_candidates(clip, profiles.profiles[clip.profile], FakeEngine(),
                                paths, seeds=[7, 8, 9], cancel=cancel)
    assert len(written) < 3


def test_candidate_seeds_lists_what_is_on_disk(setup):
    from ttskit.render import candidate_seeds

    paths, profiles, clips, state = setup
    clip = clips[0]
    sample_candidates(clip, profiles.profiles[clip.profile], FakeEngine(),
                      paths, seeds=[9, 7, 8])
    assert candidate_seeds(paths, clip.key) == [7, 8, 9], "sorted"
