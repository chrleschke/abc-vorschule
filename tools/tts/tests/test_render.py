import numpy as np
import pytest

from ttskit.models import Item
from ttskit.paths import Paths
from ttskit.plan import build_clips
from ttskit.render import (
    random_seeds, render_batch_candidates, render_clips, sample_candidates,
)
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


def test_changing_an_instruct_does_not_force_a_re_render(setup):
    """Bereits gerenderter Content bleibt bestehen — eine geänderte
    Instruktion gilt erst für Clips, die noch gerendert werden müssen."""
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    render_clips(clips, profiles, engine, state, paths)

    profiles.profiles["prompt"].instruct = "Ganz anders sprechen."
    report = render_clips(clips, profiles, engine, state, paths)
    assert report.rendered == 0
    assert report.skipped == 3


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


def test_only_exact_clip_key(setup):
    paths, profiles, clips, state = setup
    reward = next(c for c in clips if c.profile == "reward")
    report = render_clips(clips, profiles, FakeEngine(), state, paths, only=f"{reward.key}")
    assert report.rendered == 1


def test_only_glob_matches_several_clip_keys(setup):
    paths, profiles, clips, state = setup
    report = render_clips(clips, profiles, FakeEngine(), state, paths, only="prompt:*")
    assert report.rendered == 2, "both prompt clips"
    assert report.skipped == 0, "the reward clip is filtered out, not skipped"


def test_only_glob_matches_item_ids(setup):
    """The spec's own example is `--only "task:l01-t1:*"` — an item-id glob."""
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    report = render_clips(clips, profiles, engine, state, paths, only="task:t1:*")
    assert report.rendered == 1
    assert engine.calls == [("Frage eins?", engine.calls[0][1])], \
        "only the clip that task:t1's item points at"


def test_only_item_id_glob_can_span_several_clips(setup):
    paths, profiles, clips, state = setup
    report = render_clips(clips, profiles, FakeEngine(), state, paths,
                          only="task:*:round:0:promptTts")
    assert report.rendered == 2


def test_only_glob_is_case_sensitive(setup):
    paths, profiles, clips, state = setup
    report = render_clips(clips, profiles, FakeEngine(), state, paths,
                          only="PROMPT:*")
    assert report.rendered == 0, "clip keys are identifiers, not filenames"


def test_only_matching_nothing_renders_nothing(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    report = render_clips(clips, profiles, engine, state, paths, only="gibt:es:nicht")
    assert report.rendered == 0
    assert engine.calls == []


def test_a_failure_is_persisted_so_status_can_report_it(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine(fail_on={"Frage eins?"})
    render_clips(clips, profiles, engine, state, paths)

    reloaded = RenderState.load(paths.render_state)
    failed_key = next(c.key for c in clips if c.text == "Frage eins?")
    assert failed_key in reloaded.failures
    assert "model exploded" in reloaded.failures[failed_key]


def test_a_later_success_clears_the_persisted_failure(setup):
    paths, profiles, clips, state = setup
    render_clips(clips, profiles, FakeEngine(fail_on={"Frage eins?"}), state, paths)
    render_clips(clips, profiles, FakeEngine(), state, paths)
    assert RenderState.load(paths.render_state).failures == {}


def test_a_dry_run_without_an_engine_is_fine_but_a_real_run_is_not(setup):
    paths, profiles, clips, state = setup
    assert render_clips(clips, profiles, None, state, paths, dry_run=True).rendered == 3
    with pytest.raises(AssertionError, match="needs an engine"):
        render_clips(clips, profiles, None, state, paths)


def test_dry_run_writes_nothing(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    report = render_clips(clips, profiles, engine, state, paths, dry_run=True)
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


def test_audio_is_written_after_each_clip(setup):
    paths, profiles, clips, state = setup

    seen: list[int] = []

    def progress(p):
        seen.append(len(list(paths.audio.glob("*.wav"))))

    render_clips(clips, profiles, FakeEngine(), state, paths, progress=progress)
    assert seen == [1, 2, 3], "audio must land per clip, not at the end"


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


def test_render_batch_candidates_writes_n_candidates_per_missing_clip(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    report = render_batch_candidates(clips, profiles, engine, state, paths, count=2)
    assert report.rendered == 3
    assert report.skipped == 0
    assert report.failed == []
    for clip in clips:
        assert not (paths.audio / f"{clip.key}.wav").exists(), \
            "Batch-Kandidaten dürfen nicht direkt in Produktion landen"
        from ttskit.render import candidate_seeds
        assert len(candidate_seeds(paths, clip.key)) == 2


def test_render_batch_candidates_skips_already_rendered_clips(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    render_clips(clips, profiles, engine, state, paths)  # simulates a prior `tts render`
    report = render_batch_candidates(clips, profiles, engine, state, paths, count=2)
    assert report.rendered == 0
    assert report.skipped == 3


def test_render_batch_candidates_force_ignores_rendered_status(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine()
    render_clips(clips, profiles, engine, state, paths)
    report = render_batch_candidates(clips, profiles, engine, state, paths,
                                     count=2, force=True)
    assert report.rendered == 3
    assert report.skipped == 0


def test_render_batch_candidates_dry_run_writes_nothing(setup):
    paths, profiles, clips, state = setup
    report = render_batch_candidates(clips, profiles, None, state, paths,
                                     count=2, dry_run=True)
    assert report.rendered == 3
    assert not paths.candidates.exists() or list(paths.candidates.iterdir()) == []


def test_render_batch_candidates_needs_an_engine_unless_dry_run(setup):
    paths, profiles, clips, state = setup
    with pytest.raises(AssertionError, match="needs an engine"):
        render_batch_candidates(clips, profiles, None, state, paths, count=2)


def test_render_batch_candidates_cancel_stops_the_run(setup):
    paths, profiles, clips, state = setup
    calls = {"n": 0}

    def cancel():
        calls["n"] += 1
        return calls["n"] > 1

    report = render_batch_candidates(clips, profiles, FakeEngine(), state, paths,
                                     count=2, cancel=cancel)
    assert report.rendered < 3


def test_render_batch_candidates_progress_spans_the_whole_batch(setup):
    paths, profiles, clips, state = setup
    seen = []
    render_batch_candidates(clips, profiles, FakeEngine(), state, paths, count=2,
                            progress=lambda p: seen.append((p.index, p.total)))
    assert seen == [(1, 6), (2, 6), (3, 6), (4, 6), (5, 6), (6, 6)]


def test_render_batch_candidates_reports_a_failing_clip(setup):
    paths, profiles, clips, state = setup
    engine = FakeEngine(fail_on={"Frage eins?"})
    report = render_batch_candidates(clips, profiles, engine, state, paths, count=2)
    assert report.rendered == 2
    assert len(report.failed) == 1
    failed_key, message = report.failed[0]
    assert failed_key == next(c.key for c in clips if c.text == "Frage eins?")
    assert "fehlgeschlagen" in message


def test_clip_audio_list_adds_a_synthetic_entry_for_unmirrored_production(setup):
    from ttskit.render import clip_audio_list

    paths, profiles, clips, state = setup
    clip = clips[0]
    profile = profiles.profiles[clip.profile]
    render_clips([clip], profiles, FakeEngine(), state, paths)

    infos = clip_audio_list(paths, clip, profile)
    assert len(infos) == 1
    entry = infos[0]
    assert entry["seed"] == clip.seed
    assert entry["isProductionOnly"] is True
    assert entry["createdAt"], "muss aus der Datei-mtime kommen"


def test_clip_audio_list_synthetic_entry_is_not_invalidated_by_settings_changes(setup):
    """Bereits bestätigter Content wird nie durch ein späteres Profil-Update
    als veraltet markiert — der Eintrag trägt deshalb gar kein `fresh`-Feld."""
    from ttskit.render import clip_audio_list

    paths, profiles, clips, state = setup
    clip = clips[0]
    profile = profiles.profiles[clip.profile]
    render_clips([clip], profiles, FakeEngine(), state, paths)

    profile.instruct = "Ganz anders."
    entry = clip_audio_list(paths, clip, profile)[0]
    assert "fresh" not in entry


def test_clip_audio_list_skips_synthetic_entry_when_a_candidate_already_matches(setup):
    from ttskit.render import clip_audio_list, sample_candidates

    paths, profiles, clips, state = setup
    clip = clips[0]
    profile = profiles.profiles[clip.profile]
    render_clips([clip], profiles, FakeEngine(), state, paths)
    # Ein echter Kandidat mit demselben Seed wie die Produktion — z. B. weil
    # er genau daraus per promote entstand.
    sample_candidates(clip, profile, FakeEngine(), paths, seeds=[clip.seed])

    infos = clip_audio_list(paths, clip, profile)
    assert len(infos) == 1
    assert "isProductionOnly" not in infos[0]


def test_clip_audio_list_without_any_audio_is_empty(setup):
    from ttskit.render import clip_audio_list

    paths, profiles, clips, state = setup
    clip = clips[0]
    profile = profiles.profiles[clip.profile]
    assert clip_audio_list(paths, clip, profile) == []


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


def test_sample_candidates_writes_fingerprint_sidecar(setup):
    from dataclasses import replace
    import json as jsonlib
    from ttskit.plan import fingerprint

    paths, profiles, clips, state = setup
    clip = clips[0]
    profile = profiles.profiles[clip.profile]
    written = sample_candidates(clip, profile, FakeEngine(), paths, seeds=[11, 22])
    assert written == [11, 22]
    for seed in (11, 22):
        meta = jsonlib.loads(
            (paths.candidates / clip.key / f"{seed}.json").read_text(encoding="utf-8"))
        assert meta["fingerprint"] == fingerprint(replace(clip, seed=seed), profile)


def test_candidate_infos_reports_freshness(setup):
    from ttskit.render import candidate_infos

    paths, profiles, clips, state = setup
    clip = clips[0]
    profile = profiles.profiles[clip.profile]
    sample_candidates(clip, profile, FakeEngine(), paths, seeds=[11])
    # Alt-Kandidat ohne Sidecar (Bestand aus der Zeit vor den Sidecars):
    (paths.candidates / clip.key / "99.wav").write_bytes(b"RIFF")

    infos = candidate_infos(paths, clip, profile)
    assert {i["seed"]: i["fresh"] for i in infos} == {11: True, 99: None}

    profile.instruct = "Ganz anders."
    infos = candidate_infos(paths, clip, profile)
    assert {i["seed"]: i["fresh"] for i in infos} == {11: False, 99: None}


def test_sidecar_records_when_voice_and_text(setup):
    """Ohne diese Metadaten mischen sich in der UI die Würfel-Runden
    verschiedener Sessions zu einer unentwirrbaren Liste."""
    from ttskit.render import candidate_infos

    paths, profiles, clips, state = setup
    clip = clips[0]
    profile = profiles.profiles[clip.profile]
    sample_candidates(clip, profile, FakeEngine(), paths, seeds=[11])

    info = candidate_infos(paths, clip, profile)[0]
    assert info["speaker"] == clip.speaker
    assert info["text"] == clip.text
    assert info["createdAt"], "Erzeugungszeitpunkt fehlt im Sidecar"
    assert info["good"] is False


def test_candidate_infos_sorts_newest_first_legacy_last(setup):
    import json as jsonlib
    from ttskit.render import candidate_infos

    paths, profiles, clips, state = setup
    clip = clips[0]
    profile = profiles.profiles[clip.profile]
    sample_candidates(clip, profile, FakeEngine(), paths, seeds=[11, 22])
    # Zeitstempel von Hand auseinanderziehen — sample_candidates schreibt beide
    # in derselben Sekunde.
    for seed, when in ((11, "2026-01-01T10:00:00+00:00"),
                       (22, "2026-01-02T10:00:00+00:00")):
        path = paths.candidates / clip.key / f"{seed}.json"
        meta = jsonlib.loads(path.read_text(encoding="utf-8"))
        meta["createdAt"] = when
        path.write_text(jsonlib.dumps(meta), encoding="utf-8")
    # Alt-Kandidat ohne Sidecar muss ganz ans Ende:
    (paths.candidates / clip.key / "99.wav").write_bytes(b"RIFF")

    infos = candidate_infos(paths, clip, profile)
    assert [i["seed"] for i in infos] == [22, 11, 99]


def test_update_candidate_meta_sets_and_clears_rating_only(setup):
    import json as jsonlib
    from ttskit.render import update_candidate_meta

    paths, profiles, clips, state = setup
    clip = clips[0]
    profile = profiles.profiles[clip.profile]
    sample_candidates(clip, profile, FakeEngine(), paths, seeds=[11])
    path = paths.candidates / clip.key / "11.json"
    before = jsonlib.loads(path.read_text(encoding="utf-8"))

    update_candidate_meta(paths, clip.key, 11, rating="good")
    after = jsonlib.loads(path.read_text(encoding="utf-8"))
    assert after["rating"] == "good"
    assert after["fingerprint"] == before["fingerprint"], \
        "eine Bewertung darf die Frische nicht verändern"

    update_candidate_meta(paths, clip.key, 11, rating=None)
    assert "rating" not in jsonlib.loads(path.read_text(encoding="utf-8"))


def test_the_clip_voice_reaches_the_engine_not_the_profile_default(tmp_path):
    """Ein Stimm-Override im Lock ist nur dann etwas wert, wenn er auch am
    Modell ankommt — der Fingerprint allein ändert am erzeugten Audio nichts."""
    from ttskit.plan import clip_key
    from ttskit.store import Lock

    class VoiceRecordingEngine(FakeEngine):
        def __init__(self):
            super().__init__()
            self.speakers = []

        def generate(self, text, profile, seed):
            self.speakers.append(profile.speaker)
            return super().generate(text, profile, seed)

    paths = Paths(root=tmp_path, content_dir=tmp_path / "content")
    profiles = Profiles.load(tmp_path / "nope.json")
    items = [
        Item("task:t1:round:0:promptTts", "Frage eins?", "promptTts",
             "tasks.json", "l01", "a"),
        Item("task:t2:round:0:promptTts", "Frage zwei?", "promptTts",
             "tasks.json", "l01", "b"),
    ]
    locks = Locks()
    locks.set(clip_key("prompt", "Frage eins?"), Lock(seed=7, speaker="serena"))
    clips = build_clips(items, profiles, locks)

    engine = VoiceRecordingEngine()
    render_clips(clips, profiles, engine, RenderState(), paths)

    spoken = dict(zip([text for text, _ in engine.calls], engine.speakers))
    assert spoken["Frage eins?"] == "serena"
    assert spoken["Frage zwei?"] == profiles.profiles["prompt"].speaker
    assert profiles.profiles["prompt"].speaker != "serena", \
        "the shared profile must survive the override untouched"


def test_candidates_use_the_clip_voice_too(tmp_path):
    """Sonst hört man beim Kuratieren eine andere Stimme als die, die der
    finale Lauf später erzeugt."""
    from ttskit.plan import clip_key
    from ttskit.store import Lock

    class VoiceRecordingEngine(FakeEngine):
        def __init__(self):
            super().__init__()
            self.speakers = []

        def generate(self, text, profile, seed):
            self.speakers.append(profile.speaker)
            return super().generate(text, profile, seed)

    paths = Paths(root=tmp_path, content_dir=tmp_path / "content")
    profiles = Profiles.load(tmp_path / "nope.json")
    items = [Item("task:t1:phonemeTts", "M", "phonemeTts", "tasks.json", "l01", "m")]
    locks = Locks()
    locks.set(clip_key("phoneme", "M"), Lock(seed=7, speaker="ryan"))
    clip = build_clips(items, profiles, locks)[0]

    engine = VoiceRecordingEngine()
    sample_candidates(clip, profiles.profiles["phoneme"], engine, paths, [1, 2])
    assert engine.speakers == ["ryan", "ryan"]
