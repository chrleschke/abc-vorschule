import json
from pathlib import Path

import pytest


@pytest.fixture
def content_dir(tmp_path: Path) -> Path:
    """A miniature content pack with one of every TTS-bearing shape."""
    d = tmp_path / "content"
    d.mkdir()
    (d / "atoms.json").write_text(json.dumps({"atoms": [
        {"id": "maus", "lemma": "Maus", "display": "Maus", "emoji": "🐭", "kind": "word"},
        {"id": "letter-m", "lemma": "M", "display": "M", "emoji": "", "kind": "letter"},
    ]}), encoding="utf-8")
    (d / "sentences.json").write_text(json.dumps({"sentences": [
        {"id": "s-mama", "atomIds": ["mama"], "tts": "Mama."},
    ]}), encoding="utf-8")
    (d / "finales.json").write_text(json.dumps({"finales": [
        {"id": "f-l01", "text": "Mama Maus!", "tts": "Mama Maus!", "pictureAtomIds": ["mama"]},
    ]}), encoding="utf-8")
    (d / "tasks.json").write_text(json.dumps({"tasks": [
        # Kein realer Trainer-Typ: der Extractor liest Felder, nicht Trainer —
        # dieser Eintrag deckt task-level phonemeTts und rundenweises missTts ab.
        {"id": "l01-t1", "phonemeTts": "M", "rounds": [
            {"promptTts": "Hörst du M?", "atomId": "maus",
             "missTts": "Maus. Am Anfang.", "blocks": []},
            {"promptTts": "Hörst du M?", "atomId": "baum",
             "missTts": "Baum. Am Ende.", "blocks": []},
        ]},
        {"trainer": "letter_trace", "id": "l01-t2", "rounds": [
            {"promptTts": "Zeichne M nach.", "atomId": "letter-m", "glyph": "M",
             "rewardTts": "M - wie Mond.", "rewardEmoji": "🌙", "blocks": []},
        ]},
        {"trainer": "syllable_merge", "id": "l01-t3", "rounds": [
            {"promptTts": "Schiebe m und a zusammen.", "leftAtomId": "letter-m",
             "leftDisplay": "m", "rightAtomId": "letter-a", "rightDisplay": "a",
             "resultAtomId": "ma", "resultDisplay": "ma", "stretchTts": "M", "blocks": []},
        ]},
    ]}), encoding="utf-8")
    (d / "lessons.json").write_text(json.dumps({"lessons": [
        {"id": "l01", "index": 1, "phase": 1, "title": "M & A", "nodeLabel": "M a",
         "status": "authored", "finaleId": "f-l01", "focusAtomIds": ["letter-m"],
         "taskIds": ["l01-t1", "l01-t2", "l01-t3"]},
    ]}), encoding="utf-8")
    return d
