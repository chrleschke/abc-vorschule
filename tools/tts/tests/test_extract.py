from ttskit.extract import FIELD_TO_PROFILE, extract_items


def test_extracts_every_authored_string(content_dir):
    items = extract_items(content_dir)
    ids = [i.id for i in items]

    # atoms.json, sentences.json, finales.json
    assert "atom:maus:lemma" in ids
    assert "atom:letter-m:lemma" in ids
    assert "sentence:s-mama:tts" in ids
    assert "finale:f-l01:tts" in ids

    # tasks.json: task-level phonemeTts, then per-round fields
    assert "task:l01-t1:phonemeTts" in ids
    assert "task:l01-t1:round:0:promptTts" in ids
    assert "task:l01-t1:round:0:missTts" in ids
    assert "task:l01-t1:round:1:promptTts" in ids
    assert "task:l01-t2:round:0:rewardTts" in ids
    assert "task:l01-t3:round:0:stretchTts" in ids

    assert len(ids) == len(set(ids)), "item ids must be unique"


def test_carries_text_field_and_source(content_dir):
    by_id = {i.id: i for i in extract_items(content_dir)}

    maus = by_id["atom:maus:lemma"]
    assert maus.text == "Maus"
    assert maus.field == "lemma"
    assert maus.source == "atoms.json"

    prompt = by_id["task:l01-t1:round:0:promptTts"]
    assert prompt.text == "Wo hörst du M?"
    assert prompt.field == "promptTts"
    assert prompt.source == "tasks.json"


def test_distinguishes_sentence_and_finale_fields(content_dir):
    by_id = {i.id: i for i in extract_items(content_dir)}
    # Both live in a JSON field literally named "tts", but they need
    # different profiles, so the logical field name must differ.
    assert by_id["sentence:s-mama:tts"].field == "sentenceTts"
    assert by_id["finale:f-l01:tts"].field == "finaleTts"


def test_maps_lesson_via_task_ids_and_finale_id(content_dir):
    by_id = {i.id: i for i in extract_items(content_dir)}
    assert by_id["task:l01-t1:round:0:promptTts"].lesson == "l01"
    assert by_id["finale:f-l01:tts"].lesson == "l01"
    # Atoms and sentences are shared across lessons — no single owner.
    assert by_id["atom:maus:lemma"].lesson is None
    assert by_id["sentence:s-mama:tts"].lesson is None


def test_every_field_has_a_profile(content_dir):
    for item in extract_items(content_dir):
        assert item.field in FIELD_TO_PROFILE, f"no profile for field {item.field}"


def test_stretch_and_phoneme_share_the_phoneme_profile():
    assert FIELD_TO_PROFILE["stretchTts"] == "phoneme"
    assert FIELD_TO_PROFILE["phonemeTts"] == "phoneme"


def test_extra_strings_become_ui_items(content_dir):
    extra = {"version": 1, "strings": [
        {"id": "lockedLessonCue", "text": "Das üben wir später.",
         "note": "SessionViewModel.lockedLessonCue()"},
    ]}
    by_id = {i.id: i for i in extract_items(content_dir, extra_strings=extra)}
    assert by_id["ui:lockedLessonCue"].text == "Das üben wir später."
    assert by_id["ui:lockedLessonCue"].field == "uiText"
    assert by_id["ui:lockedLessonCue"].source == "extra-strings.json"


def test_blank_text_is_skipped(tmp_path):
    d = tmp_path / "content"
    d.mkdir()
    (d / "atoms.json").write_text(
        '{"atoms": [{"id": "empty", "lemma": "   ", "display": "", "emoji": "", "kind": "word"}]}',
        encoding="utf-8")
    for name in ("sentences.json", "finales.json", "tasks.json", "lessons.json"):
        key = name.removesuffix(".json")
        (d / name).write_text('{"%s": []}' % key, encoding="utf-8")
    assert extract_items(d) == []


def test_blank_text_can_be_collected_for_reporting(tmp_path):
    d = tmp_path / "content"
    d.mkdir()
    (d / "atoms.json").write_text(
        '{"atoms": [{"id": "empty", "lemma": "   ", "display": "", "kind": "word"},'
        ' {"id": "maus", "lemma": "Maus", "display": "Maus", "kind": "word"}]}',
        encoding="utf-8")
    for name in ("sentences.json", "finales.json", "tasks.json", "lessons.json"):
        (d / name).write_text('{"%s": []}' % name.removesuffix(".json"), encoding="utf-8")

    blanks = []
    items = extract_items(d, blanks=blanks)
    assert [i.id for i in items] == ["atom:maus:lemma"]
    assert blanks == ["atom:empty:lemma"]


def test_a_missing_content_file_names_the_file(tmp_path):
    import pytest

    d = tmp_path / "content"
    d.mkdir()
    with pytest.raises(ValueError) as excinfo:
        extract_items(d)
    assert "atoms.json" in str(excinfo.value)


def test_a_content_file_without_its_key_names_the_file_and_the_key(tmp_path):
    import pytest

    d = tmp_path / "content"
    d.mkdir()
    (d / "atoms.json").write_text('{"nope": []}', encoding="utf-8")
    for name in ("sentences.json", "finales.json", "tasks.json", "lessons.json"):
        (d / name).write_text('{"%s": []}' % name.removesuffix(".json"), encoding="utf-8")
    with pytest.raises(ValueError) as excinfo:
        extract_items(d)
    assert "atoms.json" in str(excinfo.value)
    assert "atoms" in str(excinfo.value)


def test_malformed_json_in_a_content_file_names_the_file(tmp_path):
    import pytest

    d = tmp_path / "content"
    d.mkdir()
    (d / "atoms.json").write_text("{ kaputt", encoding="utf-8")
    with pytest.raises(ValueError, match="atoms.json"):
        extract_items(d)
