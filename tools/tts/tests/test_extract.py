from ttskit.extract import FIELD_TO_PROFILE, extract_items, profile_for_item
from ttskit.models import Item
from ttskit.paths import Paths

#: Der echte Content-Pack der App — für Tests, die gegen die tatsächlich
#: reichweitigen Atome prüfen, statt gegen die Mini-Fixture.
CONTENT_DIR = Paths().content_dir


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


def test_letter_and_syllable_lemmas_use_phoneme_profile(content_dir):
    by_id = {i.id: i for i in extract_items(content_dir)}
    assert profile_for_item(by_id["atom:letter-m:lemma"]) == "phoneme"
    assert by_id["atom:letter-m:lemma"].atom_kind == "letter"
    assert profile_for_item(by_id["atom:maus:lemma"]) == "word"
    assert by_id["atom:maus:lemma"].atom_kind == "word"


def test_extra_strings_support_prompt_field(content_dir):
    extra = {"version": 1, "strings": [
        {"id": "huntPromptLetter", "text": "Finde alle Buchstaben",
         "field": "promptTts", "note": "SymbolHuntDerivation.PromptLetter"},
    ]}
    by_id = {i.id: i for i in extract_items(content_dir, extra_strings=extra)}
    assert by_id["ui:huntPromptLetter"].field == "promptTts"
    assert profile_for_item(by_id["ui:huntPromptLetter"]) == "prompt"


def test_extra_strings_include_detective_intro_phrases(content_dir):
    from ttskit.paths import Paths
    import json

    extra = json.loads(Paths().extra_strings.read_text())
    by_id = {i.id: i for i in extract_items(content_dir, extra_strings=extra)}
    assert by_id["ui:detectivePromptLetter"].text == "Finde den Buchstaben"
    assert by_id["ui:detectivePromptLaut"].text == "Finde den Laut"
    assert by_id["ui:detectivePromptSilbe"].text == "Finde die Silbe"
    for key in ("ui:detectivePromptLetter", "ui:detectivePromptLaut",
                "ui:detectivePromptSilbe"):
        assert by_id[key].field == "promptTts"
        assert profile_for_item(by_id[key]) == "prompt"


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


def test_extracts_count_add_spoken_answers(tmp_path):
    import json

    d = tmp_path / "content"
    d.mkdir()
    (d / "atoms.json").write_text(json.dumps({"atoms": [
        {"id": "ameise", "lemma": "Ameise", "display": "Ameise", "emoji": "🐜",
         "kind": "other", "pluralDisplay": "Ameisen"},
    ]}), encoding="utf-8")
    (d / "tasks.json").write_text(json.dumps({"tasks": [
        {"trainer": "count_add", "id": "l01-t6", "rounds": [
            {"promptTts": "Wie viele?", "iconAtomId": "ameise",
             "left": 1, "right": 1, "answer": 2, "operation": "add"},
        ]},
    ]}), encoding="utf-8")
    for name, key in (("sentences.json", "sentences"), ("finales.json", "finales"),
                      ("lessons.json", "lessons")):
        (d / name).write_text(json.dumps({key: []}), encoding="utf-8")

    by_id = {i.id: i for i in extract_items(d)}
    item = by_id["task:l01-t6:round:0:spokenAnswer"]
    assert item.field == "spokenAnswerTts"
    assert item.text == "2 Ameisen"
    assert profile_for_item(item) == "word"


def test_instruction_tts_is_extracted_with_prompt_profile(tmp_path):
    import json

    d = tmp_path / "content"
    d.mkdir()
    (d / "atoms.json").write_text(json.dumps({"atoms": [
        {"id": "oma", "lemma": "Oma", "display": "Oma", "emoji": "👵", "kind": "word"},
        {"id": "mama", "lemma": "Mama", "display": "Mama", "emoji": "👩", "kind": "word"},
    ]}), encoding="utf-8")
    (d / "tasks.json").write_text(json.dumps({"tasks": [
        {"trainer": "sentence_picture", "id": "l01-sp1",
         "instructionTts": "Ordne das richtige Bild zu.",
         "rounds": [{"sentenceTts": "Oma hat Mama gerufen.",
                     "correctAtomIds": ["oma"], "wrongAtomIds": ["mama"]}]},
    ]}), encoding="utf-8")
    for name, key in (("sentences.json", "sentences"), ("finales.json", "finales"),
                      ("lessons.json", "lessons")):
        (d / name).write_text(json.dumps({key: []}), encoding="utf-8")

    items = extract_items(d)
    by_id = {item.id: item for item in items}
    instruction = by_id["task:l01-sp1:instructionTts"]
    assert instruction.text == "Ordne das richtige Bild zu."
    assert profile_for_item(instruction) == "prompt"

    # Der Satz selbst ist eine Aussage, keine Aufgaben-Frage: das prompt-Profil
    # weist ausdrücklich eine fragende Betonung am Satzende an.
    sentence = by_id["task:l01-sp1:round:0:sentenceTts"]
    assert sentence.text == "Oma hat Mama gerufen."
    assert profile_for_item(sentence) == "sentence"
    assert "task:l01-sp1:round:0:promptTts" not in by_id


def test_extra_strings_include_praise_phrases(content_dir):
    from ttskit.paths import Paths
    import json

    extra = json.loads(Paths().extra_strings.read_text())
    by_id = {i.id: i for i in extract_items(content_dir, extra_strings=extra)}
    praise = [i for i in by_id.values()
              if i.source == "extra-strings.json" and i.field == "rewardTts"]
    # Mirrors PraisePhrasesTest.offersThirtyNineDistinctPhrases on the app side.
    assert len(praise) == 39
    assert by_id["ui:praiseSuper"].text == "Super"
    assert profile_for_item(by_id["ui:praiseSuper"]) == "reward"


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


def test_article_speech_mirrors_the_kotlin_rule():
    from ttskit.extract import article_speech

    assert article_speech({"display": "Haus", "gender": "n", "nounClass": "thing"}) == "das Haus"
    assert article_speech({"display": "Maus", "gender": "f", "nounClass": "thing"}) == "die Maus"
    assert article_speech({"display": "Baum", "gender": "m", "nounClass": "thing"}) == "der Baum"
    assert article_speech({"display": "Oma", "gender": "f", "nounClass": "person"}) == "eine Oma"
    assert article_speech({"display": "Opa", "gender": "m", "nounClass": "person"}) == "ein Opa"
    assert article_speech({"display": "Kind", "gender": "n", "nounClass": "person"}) == "das Kind"
    assert article_speech({"display": "Tom", "nounClass": "name"}) == "Tom"
    assert article_speech({"display": "Häuser", "gender": "n", "nounClass": "thing",
                           "articleSpeechOverride": "die Häuser"}) == "die Häuser"
    assert article_speech({"display": "ist"}) is None
    assert article_speech({"display": "Haus", "nounClass": "thing"}) is None

    # Kotlins takeIf { it.isNotBlank() } filtert nur — es trimmt nicht. Ein
    # gültiger Override kommt unverändert zurück, auch mit Rand-Leerzeichen.
    assert article_speech({"display": "Häuser", "gender": "n", "nounClass": "thing",
                           "articleSpeechOverride": " die Häuser "}) == " die Häuser "
    # Ein Nur-Leerzeichen-Override ist blank, also kein Override — Fallback auf
    # die normale Ableitung.
    assert article_speech({"display": "Kind", "gender": "n", "nounClass": "person",
                           "articleSpeechOverride": "   "}) == "das Kind"
    # Ein Nur-Leerzeichen-display ist ebenfalls blank, auch wenn nounClass und
    # gender gesetzt sind.
    assert article_speech({"display": "   ", "gender": "n", "nounClass": "thing"}) is None
    # display selbst wird nicht getrimmt: Rand-Leerzeichen bleiben im
    # zusammengesetzten Sprechtext erhalten.
    assert article_speech({"display": " Haus ", "gender": "n",
                           "nounClass": "thing"}) == "das  Haus "


def test_article_override_beats_the_name_duplicate_check(tmp_path):
    """Die Duplikat-Vermeidung vergleicht Text gegen Text, nicht nounClass gegen "name".

    Ein Name mit Override, der vom display abweicht, ist kein Duplikat und muss
    trotz nounClass == "name" einen eigenen Clip bekommen — sonst hätte die
    Prüfung in extract_items klammheimlich zu einer hartkodierten
    nounClass == "name"-Ausnahme verengt statt zum generischen Textvergleich.
    """
    import json

    d = tmp_path / "content"
    d.mkdir()
    (d / "atoms.json").write_text(json.dumps({"atoms": [
        {"id": "kapitaen", "lemma": "Kapitän", "display": "Kapitän", "emoji": "",
         "kind": "word", "nounClass": "name", "articleSpeechOverride": "Herr Kapitän"},
    ]}), encoding="utf-8")
    (d / "tasks.json").write_text(json.dumps({"tasks": [
        {"trainer": "sound_position", "id": "l01-t1", "rounds": [
            {"promptTts": "Wo hörst du K?", "atomId": "kapitaen", "slot": "start",
             "missTts": "Kapitän. Am Anfang.", "blocks": []},
        ]},
    ]}), encoding="utf-8")
    for name, key in (("sentences.json", "sentences"), ("finales.json", "finales"),
                      ("lessons.json", "lessons")):
        (d / name).write_text(json.dumps({key: []}), encoding="utf-8")

    by_id = {i.id: i for i in extract_items(d)}
    item = by_id["atom:kapitaen:articleTts"]
    assert item.text == "Herr Kapitän"


def test_article_items_cover_only_reachable_atoms():
    items = extract_items(CONTENT_DIR)
    by_id = {i.id: i for i in items if i.field == "articleTts"}

    # word_build-Ziel und sound_position-Wort → Clip
    assert by_id["atom:haus:articleTts"].text == "das Haus"
    assert by_id["atom:ameise:articleTts"].text == "die Ameise"
    # klassifiziert, aber nie vorgesprochen → kein Clip
    assert "atom:banane:articleTts" not in by_id
    # Name: Sprechtext == display, wäre ein Duplikat des word-Clips
    assert "atom:tom:articleTts" not in by_id
    # kein Substantiv
    assert "atom:ich:articleTts" not in by_id
    assert len(by_id) == 85


def test_article_items_use_the_article_word_profile():
    from ttskit.extract import profile_for_item

    items = [i for i in extract_items(CONTENT_DIR) if i.field == "articleTts"]
    assert items
    assert all(profile_for_item(i) == "article_word" for i in items)
