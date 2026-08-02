from pathlib import Path

from ttskit.models import Item
from ttskit.plan import (
    build_clips, clip_key, fingerprint, orphan_locks, resolve_seed, status_of,
)
from ttskit.store import Lock, Locks, Profiles, RenderState


def profiles() -> Profiles:
    return Profiles.load(Path("does-not-exist.json"))


def item(item_id: str, text: str, field: str, lesson: str | None = None) -> Item:
    return Item(id=item_id, text=text, field=field, source="tasks.json",
                lesson=lesson, label=item_id)


def test_clip_key_is_profile_plus_text_hash():
    key = clip_key("prompt", "Wo hörst du M?")
    assert key.startswith("prompt:")
    assert len(key.split(":")[1]) == 12
    assert key == clip_key("prompt", "Wo hörst du M?"), "must be stable"
    assert key != clip_key("prompt", "Wo hörst du A?")
    assert key != clip_key("word", "Wo hörst du M?"), "profile is part of the key"


def test_identical_text_and_profile_collapse_into_one_clip():
    items = [
        item("task:t1:round:0:promptTts", "Wo hörst du M?", "promptTts"),
        item("task:t1:round:1:promptTts", "Wo hörst du M?", "promptTts"),
        item("task:t1:round:2:promptTts", "Wo hörst du A?", "promptTts"),
    ]
    clips = build_clips(items, profiles(), Locks())
    assert len(clips) == 2
    merged = next(c for c in clips if c.source_text == "Wo hörst du M?")
    assert merged.item_ids == (
        "task:t1:round:0:promptTts", "task:t1:round:1:promptTts",
    )


def test_stretch_and_phoneme_with_the_same_text_collapse():
    items = [
        item("task:t1:phonemeTts", "M", "phonemeTts"),
        item("task:t2:round:0:stretchTts", "M", "stretchTts"),
    ]
    clips = build_clips(items, profiles(), Locks())
    assert len(clips) == 1, "both map to the phoneme profile"
    assert clips[0].profile == "phoneme"
    assert len(clips[0].item_ids) == 2


def test_same_text_different_profile_stays_separate():
    items = [
        item("atom:m:lemma", "M", "lemma"),
        item("task:t1:phonemeTts", "M", "phonemeTts"),
    ]
    clips = build_clips(items, profiles(), Locks())
    assert len(clips) == 2
    assert {c.profile for c in clips} == {"word", "phoneme"}


def test_lock_seed_beats_pool():
    prof = profiles()
    prof.profiles["prompt"].seed_pool = [1, 2, 3]
    key = clip_key("prompt", "Hallo")
    locks = Locks()
    locks.set(key, Lock(seed=999))
    assert resolve_seed(key, "prompt", prof, locks) == 999


def test_pool_choice_is_deterministic_and_within_the_pool():
    prof = profiles()
    prof.profiles["prompt"].seed_pool = [10, 20, 30]
    key = clip_key("prompt", "Hallo")
    first = resolve_seed(key, "prompt", prof, Locks())
    assert first in (10, 20, 30)
    assert first == resolve_seed(key, "prompt", prof, Locks())


def test_pool_choice_spreads_across_the_pool():
    prof = profiles()
    prof.profiles["prompt"].seed_pool = [10, 20, 30]
    keys = [clip_key("prompt", f"Satz Nummer {n}") for n in range(60)]
    chosen = {resolve_seed(k, "prompt", prof, Locks()) for k in keys}
    assert chosen == {10, 20, 30}, "all pool seeds should get used"


def test_pool_salt_reshuffles_the_choice():
    prof = profiles()
    prof.profiles["prompt"].seed_pool = [10, 20, 30]
    keys = [clip_key("prompt", f"Satz {n}") for n in range(40)]
    before = [resolve_seed(k, "prompt", prof, Locks()) for k in keys]
    prof.pool_salt = "v2"
    after = [resolve_seed(k, "prompt", prof, Locks()) for k in keys]
    assert before != after


def test_empty_pool_falls_back_to_the_hash_itself():
    prof = profiles()
    assert prof.profiles["prompt"].seed_pool == []
    key = clip_key("prompt", "Hallo")
    seed = resolve_seed(key, "prompt", prof, Locks())
    assert 0 <= seed < 2 ** 31
    assert seed == resolve_seed(key, "prompt", prof, Locks()), "still reproducible"


def test_lock_can_override_profile_without_changing_the_key():
    items = [item("task:t1:phonemeTts", "M", "phonemeTts")]
    key = clip_key("phoneme", "M")
    locks = Locks()
    locks.set(key, Lock(seed=5, profile="word"))
    clip = build_clips(items, profiles(), locks)[0]
    assert clip.key == key, "key still carries the default profile"
    assert clip.profile == "word", "but the overriding profile is used"


def test_lock_text_override_changes_spoken_text_only():
    items = [item("task:t1:phonemeTts", "M", "phonemeTts")]
    key = clip_key("phoneme", "M")
    locks = Locks()
    locks.set(key, Lock(seed=5, text_override="mmmmm"))
    clip = build_clips(items, profiles(), locks)[0]
    assert clip.key == key
    assert clip.source_text == "M"
    assert clip.text == "mmmmm"
    assert clip.locked is True


def test_fingerprint_changes_with_every_input_that_matters():
    prof = profiles()
    items = [item("task:t1:round:0:promptTts", "Hallo", "promptTts")]
    clip = build_clips(items, prof, Locks())[0]
    profile = prof.profiles["prompt"]
    base = fingerprint(clip, profile)
    original_instruct = profile.instruct  # capture before mutating — profile is the same object

    assert fingerprint(clip, profile) == base, "must be stable"

    profile.instruct = "Anders sprechen."
    assert fingerprint(clip, profile) != base

    profile.instruct = original_instruct
    profile.sampling["temperature"] = 0.9
    assert fingerprint(clip, profile) != base

    profile.sampling["temperature"] = 0.6
    profile.trim = False
    assert fingerprint(clip, profile) != base

    profile.trim = True
    assert fingerprint(clip, profile) == base, "every mutation was undone"


def test_fingerprint_changes_with_seed_and_text():
    prof = profiles()
    clip = build_clips([item("task:t1:round:0:promptTts", "Hallo", "promptTts")],
                       prof, Locks())[0]
    profile = prof.profiles["prompt"]
    base = fingerprint(clip, profile)

    from dataclasses import replace
    assert fingerprint(replace(clip, seed=clip.seed + 1), profile) != base
    assert fingerprint(replace(clip, text="Tschüss"), profile) != base


def test_fingerprint_ignores_which_items_point_at_the_clip():
    """A new lesson reusing the same prompt must not force a re-render."""
    prof = profiles()
    one = build_clips([item("task:t1:round:0:promptTts", "Hallo", "promptTts")],
                      prof, Locks())[0]
    two = build_clips([item("task:t1:round:0:promptTts", "Hallo", "promptTts"),
                       item("task:t9:round:0:promptTts", "Hallo", "promptTts")],
                      prof, Locks())[0]
    assert fingerprint(one, prof.profiles["prompt"]) == fingerprint(two, prof.profiles["prompt"])


def test_status_missing_stale_rendered(tmp_path):
    prof = profiles()
    clip = build_clips([item("task:t1:round:0:promptTts", "Hallo", "promptTts")],
                       prof, Locks())[0]
    profile = prof.profiles["prompt"]
    audio_dir = tmp_path / "audio"
    audio_dir.mkdir()
    state = RenderState()

    assert status_of(clip, profile, state, audio_dir) == "missing"

    (audio_dir / f"{clip.key}.wav").write_bytes(b"RIFF")
    state.entries[clip.key] = fingerprint(clip, profile)
    assert status_of(clip, profile, state, audio_dir) == "rendered"

    profile.instruct = "Ganz anders."
    assert status_of(clip, profile, state, audio_dir) == "stale"


def test_status_is_missing_when_the_file_was_deleted(tmp_path):
    prof = profiles()
    clip = build_clips([item("task:t1:round:0:promptTts", "Hallo", "promptTts")],
                       prof, Locks())[0]
    profile = prof.profiles["prompt"]
    audio_dir = tmp_path / "audio"
    audio_dir.mkdir()
    state = RenderState({clip.key: fingerprint(clip, profile)})
    assert status_of(clip, profile, state, audio_dir) == "missing"


def test_orphan_locks_are_reported_not_deleted():
    prof = profiles()
    items = [item("task:t1:round:0:promptTts", "Hallo", "promptTts")]
    clips = build_clips(items, prof, Locks())
    locks = Locks()
    locks.set(clips[0].key, Lock(seed=1))
    locks.set("prompt:deadbeef1234", Lock(seed=2, source_text="Alter Text"))

    orphans = orphan_locks(locks, clips)
    assert orphans == ["prompt:deadbeef1234"]
    assert locks.get("prompt:deadbeef1234") is not None, "must not be removed"


def test_fingerprint_tracks_the_postprocessing_version():
    """Bumping POSTPROCESS_VERSION must invalidate every clip — otherwise a
    changed trim pad leaves out/audio/ holding two generations of audio."""
    import ttskit.plan as plan_module
    from ttskit.audio import POSTPROCESS_VERSION

    prof = profiles()
    clip = build_clips([item("task:t1:round:0:promptTts", "Hallo", "promptTts")],
                       prof, Locks())[0]
    profile = prof.profiles["prompt"]
    base = fingerprint(clip, profile)

    original = plan_module.POSTPROCESS_VERSION
    try:
        plan_module.POSTPROCESS_VERSION = original + 1
        assert fingerprint(clip, profile) != base
    finally:
        plan_module.POSTPROCESS_VERSION = original
    assert fingerprint(clip, profile) == base
    assert plan_module.POSTPROCESS_VERSION == POSTPROCESS_VERSION


def test_bumping_the_postprocessing_version_makes_rendered_clips_stale(tmp_path):
    import ttskit.plan as plan_module

    prof = profiles()
    clip = build_clips([item("task:t1:round:0:promptTts", "Hallo", "promptTts")],
                       prof, Locks())[0]
    profile = prof.profiles["prompt"]
    audio_dir = tmp_path / "audio"
    audio_dir.mkdir()
    (audio_dir / f"{clip.key}.wav").write_bytes(b"RIFF")
    state = RenderState({clip.key: fingerprint(clip, profile)})
    assert status_of(clip, profile, state, audio_dir) == "rendered"

    original = plan_module.POSTPROCESS_VERSION
    try:
        plan_module.POSTPROCESS_VERSION = original + 1
        assert status_of(clip, profile, state, audio_dir) == "stale"
    finally:
        plan_module.POSTPROCESS_VERSION = original


def test_a_lock_naming_an_unknown_profile_is_rejected_by_name(tmp_path):
    """It used to escape build_clips and raise a bare KeyError in `status`,
    `render` and /api/state alike, naming neither the file nor the key."""
    import pytest

    items = [item("task:t1:phonemeTts", "M", "phonemeTts")]
    key = clip_key("phoneme", "M")
    locks = Locks({key: Lock(seed=5, profile="tippfehler")},
                  source=tmp_path / "locks.json")
    with pytest.raises(ValueError) as excinfo:
        build_clips(items, profiles(), locks)
    message = str(excinfo.value)
    assert "locks.json" in message
    assert key in message
    assert "tippfehler" in message
    assert "phoneme" in message, "the valid options must be listed"


def test_a_missing_default_profile_is_rejected_without_blaming_the_lock():
    import pytest

    prof = profiles()
    del prof.profiles["phoneme"]
    with pytest.raises(ValueError, match="defaults to the unknown profile 'phoneme'"):
        build_clips([item("task:t1:phonemeTts", "M", "phonemeTts")], prof, Locks())


def test_clip_speaker_defaults_to_the_profile_voice():
    prof = profiles()
    clip = build_clips([item("task:t1:phonemeTts", "M", "phonemeTts")], prof, Locks())[0]
    assert clip.speaker == prof.profiles["phoneme"].speaker


def test_lock_can_swap_the_voice_for_one_clip_without_moving_the_key():
    items = [item("task:t1:phonemeTts", "M", "phonemeTts")]
    key = clip_key("phoneme", "M")
    locks = Locks()
    locks.set(key, Lock(seed=5, speaker="serena"))
    clip = build_clips(items, profiles(), locks)[0]
    assert clip.key == key, "the key must not move — the lock is found by it"
    assert clip.speaker == "serena"


def test_a_voice_override_makes_only_that_clip_stale():
    """The profile is shared; the override is not. Both clips of the profile
    must not go stale because one of them got a different voice."""
    prof = profiles()
    items = [item("task:t1:phonemeTts", "M", "phonemeTts"),
             item("task:t2:phonemeTts", "A", "phonemeTts")]
    profile = prof.profiles["phoneme"]
    before = {c.source_text: fingerprint(c, profile)
              for c in build_clips(items, prof, Locks())}

    locks = Locks()
    locks.set(clip_key("phoneme", "M"), Lock(seed=5, speaker="serena"))
    after = {c.source_text: fingerprint(c, profile)
             for c in build_clips(items, prof, locks)}

    assert after["A"] == before["A"], "the untouched clip must stay rendered"
    assert after["M"] != before["M"]


def test_fingerprint_follows_the_profile_voice_when_nothing_is_overridden():
    prof = profiles()
    clip = build_clips([item("task:t1:phonemeTts", "M", "phonemeTts")], prof, Locks())[0]
    profile = prof.profiles["phoneme"]
    base = fingerprint(clip, profile)
    profile.speaker = "serena"
    # The clip still carries the old voice, so re-deriving it is what changes
    # the fingerprint — exactly what a profile-wide voice switch must do.
    switched = build_clips([item("task:t1:phonemeTts", "M", "phonemeTts")], prof, Locks())[0]
    assert fingerprint(switched, profile) != base


def test_fingerprint_follows_the_profile_language():
    prof = profiles()
    clip = build_clips([item("task:t1:phonemeTts", "M", "phonemeTts")], prof, Locks())[0]
    profile = prof.profiles["phoneme"]
    base = fingerprint(clip, profile)
    profile.language = "english"
    assert fingerprint(clip, profile) != base


def test_effective_profile_swaps_the_voice_and_nothing_else():
    from ttskit.plan import effective_profile

    prof = profiles()
    profile = prof.profiles["phoneme"]
    plain = build_clips([item("task:t1:phonemeTts", "M", "phonemeTts")],
                        prof, Locks())[0]
    assert effective_profile(plain, profile) is profile, "no override, no copy"

    locks = Locks()
    locks.set(clip_key("phoneme", "M"), Lock(seed=5, speaker="serena"))
    overridden = build_clips([item("task:t1:phonemeTts", "M", "phonemeTts")],
                             prof, locks)[0]
    swapped = effective_profile(overridden, profile)
    assert swapped.speaker == "serena"
    assert swapped.instruct == profile.instruct
    assert swapped.language == profile.language
    assert swapped.sampling == profile.sampling
    assert profile.speaker != "serena", "the shared profile must not be mutated"


def test_a_lock_naming_an_unknown_voice_is_rejected_by_name(tmp_path):
    import pytest

    items = [item("task:t1:phonemeTts", "M", "phonemeTts")]
    key = clip_key("phoneme", "M")
    locks = Locks({key: Lock(seed=5, speaker="brunhilde")},
                  source=tmp_path / "locks.json")
    with pytest.raises(ValueError) as excinfo:
        build_clips(items, profiles(), locks)
    message = str(excinfo.value)
    assert "locks.json" in message
    assert key in message
    assert "brunhilde" in message
    assert "serena" in message, "the valid options must be listed"


def test_a_profile_naming_an_unknown_voice_is_rejected_without_blaming_the_lock():
    import pytest

    prof = profiles()
    prof.profiles["phoneme"].speaker = "brunhilde"
    with pytest.raises(ValueError, match="profile 'phoneme' names the unknown speaker"):
        build_clips([item("task:t1:phonemeTts", "M", "phonemeTts")], prof, Locks())
