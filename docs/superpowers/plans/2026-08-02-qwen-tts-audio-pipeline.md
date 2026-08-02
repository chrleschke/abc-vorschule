# Qwen-TTS Audio-Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Offline-Tooling unter `tools/tts/`, das aus dem Content-Pack der App mit lokalem Qwen3-TTS ein reproduzierbares Audio-Paket erzeugt — steuerbar über Use-Case-Profile, kuratierte Seed-Pools und ein Web-Interface.

**Architecture:** Sechs Python-Module mit einer scharfen Trennlinie: `extract`, `store`, `plan`, `audio` kennen das TTS-Modell nicht und sind ohne GPU und ohne 4-GB-Checkpoint testbar; nur `engine`, `render` und `server` laden es. Der Zustand liegt in drei versionierten JSON-Dateien (`profiles.json`, `locks.json`, `extra-strings.json`) plus einem ableitbaren `out/`-Verzeichnis. Gerendert wird inkrementell über einen Fingerprint pro Clip.

**Tech Stack:** Python 3.14 im bestehenden venv `~/qwen-tts-test/.venv`, `qwen_tts` 0.1.1, PyTorch 2.13 auf MPS, `soundfile` für WAV-I/O, FastAPI + uvicorn + Vanilla HTML/JS für die UI, pytest für Tests.

**Spec:** `docs/superpowers/specs/2026-08-02-qwen-tts-audio-pipeline-design.md`

## Global Constraints

- **Interpreter:** Alle Kommandos laufen mit `~/qwen-tts-test/.venv/bin/python`. Kein neues venv, keine Installation ins System-Python.
- **Arbeitsverzeichnis:** Alle Pfade in diesem Plan sind relativ zum Repo-Root. Tests und CLI werden aus `tools/tts/` heraus gestartet.
- **Kein `bin/`-Verzeichnis.** `.gitignore` blockt global `bin/` und `out/`. Der CLI-Einstiegspunkt heißt `tools/tts/tts` (Datei, kein Verzeichnis). Das `out/`-Pattern ist erwünscht und deckt `tools/tts/out/` ab.
- **Die App wird nicht angefasst.** Kein Schreibzugriff auf `app/`. Das Content-Pack unter `app/src/main/assets/content/` wird ausschließlich gelesen.
- **Modell:** `Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice`, Speaker `sohee`, Sprache `german`. Beide Werte sind kleingeschrieben und werden von der Library case-insensitiv validiert.
- **Batchgröße ist immer 1.** Batching zerstört die Zuordnung Seed↔Clip und damit die Reproduzierbarkeit.
- **Seed wird über `torch.manual_seed(seed)` unmittelbar vor `generate_custom_voice` gesetzt.** Die `qwen_tts`-API hat keinen `seed`-Parameter.
- **Ausgabeformat:** 24 000 Hz, mono, 16-bit PCM WAV.
- **JSON-Dateien mit menschlichen Entscheidungen** (`profiles.json`, `locks.json`, `extra-strings.json`) werden mit `indent=2`, `ensure_ascii=False` und Zeilenumbruch am Ende geschrieben, damit Git-Diffs lesbar bleiben. Ihre Schlüssel sind camelCase.
- **Sprache im Code:** Bezeichner und Kommentare auf Englisch (wie im übrigen Repo), Instruktionstexte und UI-Beschriftungen auf Deutsch.

---

## Dateistruktur

| Datei | Verantwortung |
| --- | --- |
| `tools/tts/tts` | Ausführbarer CLI-Einstiegspunkt, delegiert an `ttskit.cli` |
| `tools/tts/pytest.ini` | `pythonpath = .`, damit `ttskit` ohne Installation importierbar ist |
| `tools/tts/profiles.json` | Versioniert: `poolSalt` + Profile mit Instruktion, Sampling, Seed-Pool |
| `tools/tts/locks.json` | Versioniert: pro-Clip festgenagelte Seeds und Text-Overrides |
| `tools/tts/extra-strings.json` | Versioniert: hartkodierte Kotlin-Strings und (später) Templates |
| `tools/tts/ttskit/models.py` | Dataclasses `Item` und `Clip` — das gemeinsame Vokabular |
| `tools/tts/ttskit/extract.py` | Content-JSON → `list[Item]`; kennt das ID-Schema |
| `tools/tts/ttskit/store.py` | Laden/Speichern von Profilen, Locks, Render-State |
| `tools/tts/ttskit/plan.py` | `clipKey`, Dedup, Seed-Auflösung, Fingerprint, Status, verwaiste Locks |
| `tools/tts/ttskit/audio.py` | Trim, Normalisierung, WAV schreiben |
| `tools/tts/ttskit/engine.py` | Modell laden, validieren, `generate(text, profile, seed)` |
| `tools/tts/ttskit/render.py` | Batch-Lauf über den Plan, Kandidaten-Sampling |
| `tools/tts/ttskit/cli.py` | argparse-Subkommandos |
| `tools/tts/ttskit/server.py` | FastAPI-Endpunkte, Job-Queue, SSE |
| `tools/tts/ttskit/static/` | `index.html`, `app.js`, `style.css` |
| `tools/tts/tests/` | pytest, spiegelt die Modulstruktur |
| `tools/tts/out/` | gitignored: `manifest.json`, `render-state.json`, `audio/`, `candidates/` |

---

## Task 1: Skelett, Datenmodell und Extraktion

**Files:**
- Create: `tools/tts/pytest.ini`, `tools/tts/ttskit/__init__.py`, `tools/tts/ttskit/models.py`, `tools/tts/ttskit/extract.py`
- Create: `tools/tts/tests/__init__.py`, `tools/tts/tests/conftest.py`, `tools/tts/tests/test_extract.py`

**Interfaces:**
- Consumes: nichts
- Produces:
  - `models.Item(id: str, text: str, field: str, source: str, lesson: str | None, label: str)` — frozen dataclass
  - `extract.FIELD_TO_PROFILE: dict[str, str]`
  - `extract.extract_items(content_dir: Path, extra_strings: dict | None = None) -> list[Item]`

**Kontext für den Umsetzenden:**

Das ID-Schema muss exakt `app/src/main/java/app/abcvorschule/debug/TtsDebugEntry.kt` spiegeln, damit eine spätere App-Integration ohne Übersetzungsschicht auskommt. Dort gibt es `finales.json` noch nicht — das `finale:`-Präfix wird hier neu definiert.

Das Feld `field` ist ein **logischer** Schlüssel, der über alle Quellen hinweg eindeutig ist (`sentenceTts` vs. `finaleTts`), nicht der rohe JSON-Feldname. Nur so lässt sich `FIELD_TO_PROFILE` als flaches Dict schreiben.

- [ ] **Step 1: pytest installieren und Verzeichnisse anlegen**

```bash
~/qwen-tts-test/.venv/bin/pip install pytest
mkdir -p tools/tts/ttskit tools/tts/tests
touch tools/tts/ttskit/__init__.py tools/tts/tests/__init__.py
```

`tools/tts/pytest.ini`:

```ini
[pytest]
pythonpath = .
testpaths = tests
```

- [ ] **Step 2: Den failing test schreiben**

`tools/tts/tests/conftest.py`:

```python
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
        {"trainer": "sound_position", "id": "l01-t1", "phonemeTts": "M", "rounds": [
            {"promptTts": "Wo hörst du M?", "atomId": "maus", "slot": "start",
             "missTts": "Maus. Am Anfang.", "blocks": []},
            {"promptTts": "Wo hörst du M?", "atomId": "baum", "slot": "end",
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
```

`tools/tts/tests/test_extract.py`:

```python
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
```

- [ ] **Step 3: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_extract.py -v`
Expected: FAIL mit `ModuleNotFoundError: No module named 'ttskit.extract'`

- [ ] **Step 4: `models.py` implementieren**

```python
"""Shared vocabulary for the TTS tooling."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class Item:
    """One spot in the app that speaks.

    `id` mirrors the scheme in TtsDebugEntry.kt so a later app integration
    needs no translation layer. `field` is a *logical* key that is unique
    across sources — sentences.json and finales.json both use a JSON field
    called "tts", but they map to different profiles.
    """

    id: str
    text: str
    field: str
    source: str
    lesson: str | None
    label: str
```

- [ ] **Step 5: `extract.py` implementieren**

```python
"""Turn the app content pack into a flat list of speakable items."""

from __future__ import annotations

import json
from pathlib import Path

from .models import Item

FIELD_TO_PROFILE: dict[str, str] = {
    "lemma": "word",
    "phonemeTts": "phoneme",
    # stretchTts holds nothing but graphemes (A, M, Sch, Pf, ...) and is a
    # strict subset of phonemeTts. Same treatment, same profile — otherwise
    # the same 20 sounds get rendered and curated twice.
    "stretchTts": "phoneme",
    "promptTts": "prompt",
    "missTts": "miss",
    "rewardTts": "reward",
    "sentenceTts": "sentence",
    "finaleTts": "finale",
    "uiText": "ui",
}

# Order matters: it decides the order of items within a round.
ROUND_FIELDS = ("promptTts", "missTts", "rewardTts", "stretchTts")


def _load(content_dir: Path, name: str, key: str) -> list[dict]:
    path = content_dir / name
    return json.loads(path.read_text(encoding="utf-8"))[key]


def _lesson_index(lessons: list[dict]) -> tuple[dict[str, str], dict[str, str]]:
    """Return (taskId -> lessonId, finaleId -> lessonId)."""
    by_task: dict[str, str] = {}
    by_finale: dict[str, str] = {}
    for lesson in lessons:
        for task_id in lesson.get("taskIds", []):
            by_task[task_id] = lesson["id"]
        finale_id = lesson.get("finaleId")
        if finale_id:
            by_finale[finale_id] = lesson["id"]
    return by_task, by_finale


def extract_items(content_dir: Path, extra_strings: dict | None = None) -> list[Item]:
    """Collect every authored TTS string from the content pack.

    Blank strings are skipped — they would produce empty audio.
    """
    content_dir = Path(content_dir)
    atoms = _load(content_dir, "atoms.json", "atoms")
    sentences = _load(content_dir, "sentences.json", "sentences")
    finales = _load(content_dir, "finales.json", "finales")
    tasks = _load(content_dir, "tasks.json", "tasks")
    lessons = _load(content_dir, "lessons.json", "lessons")

    lesson_by_task, lesson_by_finale = _lesson_index(lessons)
    items: list[Item] = []

    def add(item_id: str, text: str, field: str, source: str,
            lesson: str | None, label: str) -> None:
        if not text or not text.strip():
            return
        items.append(Item(id=item_id, text=text, field=field, source=source,
                          lesson=lesson, label=label))

    for atom in sorted(atoms, key=lambda a: a["id"]):
        add(f"atom:{atom['id']}:lemma", atom.get("lemma", ""), "lemma",
            "atoms.json", None, f"{atom.get('display', atom['id'])} ({atom.get('kind', '?')})")

    for sentence in sorted(sentences, key=lambda s: s["id"]):
        add(f"sentence:{sentence['id']}:tts", sentence.get("tts", ""), "sentenceTts",
            "sentences.json", None, sentence["id"])

    for finale in sorted(finales, key=lambda f: f["id"]):
        add(f"finale:{finale['id']}:tts", finale.get("tts", ""), "finaleTts",
            "finales.json", lesson_by_finale.get(finale["id"]), finale["id"])

    for task in sorted(tasks, key=lambda t: t["id"]):
        task_id = task["id"]
        lesson = lesson_by_task.get(task_id)
        if "phonemeTts" in task:
            add(f"task:{task_id}:phonemeTts", task["phonemeTts"], "phonemeTts",
                "tasks.json", lesson, f"{task_id} · phonemeTts")
        for index, round_ in enumerate(task.get("rounds", [])):
            for field in ROUND_FIELDS:
                if field not in round_:
                    continue
                add(f"task:{task_id}:round:{index}:{field}", round_[field], field,
                    "tasks.json", lesson, f"{task_id} · Runde {index + 1} · {field}")

    if extra_strings:
        for entry in extra_strings.get("strings", []):
            add(f"ui:{entry['id']}", entry.get("text", ""), "uiText",
                "extra-strings.json", None, entry.get("note") or entry["id"])

    return items
```

- [ ] **Step 6: Tests laufen lassen und Erfolg bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_extract.py -v`
Expected: PASS, 8 Tests

- [ ] **Step 7: Gegen das echte Content-Pack gegenprüfen**

Run:
```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -c "
from pathlib import Path
from ttskit.extract import extract_items, FIELD_TO_PROFILE
import collections
items = extract_items(Path('../../app/src/main/assets/content'))
print('items:', len(items))
print(collections.Counter(i.field for i in items))
assert len(items) == len({i.id for i in items})
assert all(i.field in FIELD_TO_PROFILE for i in items)
"
```
Expected: 891 Items, Feldverteilung `lemma 261 · promptTts 338 · missTts 103 · rewardTts 55 · stretchTts 39 · phonemeTts 51 · sentenceTts 26 · finaleTts 18`, keine Assertion-Fehler.

- [ ] **Step 8: Commit**

```bash
git add tools/tts/
git commit -m "feat(tts): Content-Extraktion mit TtsDebugEntry-kompatiblem ID-Schema"
```

---

## Task 2: Store — Profile, Locks, Render-State

**Files:**
- Create: `tools/tts/ttskit/store.py`, `tools/tts/profiles.json`, `tools/tts/locks.json`, `tools/tts/extra-strings.json`
- Create: `tools/tts/tests/test_store.py`

**Interfaces:**
- Consumes: nichts
- Produces:
  - `store.Profile(label, speaker, language, instruct, sampling: dict, seed_pool: list[int], trim: bool, normalize: bool)`
  - `store.Profiles(pool_salt: str, profiles: dict[str, Profile])` mit `load(path)` / `save(path)`
  - `store.Lock(seed, profile, text_override, note, source_text)`
  - `store.Locks(locks: dict[str, Lock])` mit `load(path)` / `save(path)` / `get(key)` / `set(key, lock)` / `remove(key)`
  - `store.RenderState(entries: dict[str, str])` mit `load(path)` / `save(path)` — Mapping `clipKey -> fingerprint`
  - `store.DEFAULT_PROFILES: dict` — der Inhalt der ausgelieferten `profiles.json`

**Kontext für den Umsetzenden:**

Die JSON-Schlüssel sind camelCase (`seedPool`, `textOverride`, `sourceText`, `poolSalt`), die Python-Attribute snake_case. Die Umwandlung passiert explizit in `from_dict`/`to_dict` — kein automatisches Mapping, damit ein Tippfehler im JSON auffällt statt still ignoriert zu werden.

Fehlende Dateien sind kein Fehler: `Profiles.load` liefert die Defaults, `Locks.load` und `RenderState.load` liefern leere Container. Das macht einen Erstlauf ohne Setup möglich.

- [ ] **Step 1: Den failing test schreiben**

`tools/tts/tests/test_store.py`:

```python
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
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_store.py -v`
Expected: FAIL mit `ModuleNotFoundError: No module named 'ttskit.store'`

- [ ] **Step 3: `store.py` implementieren**

```python
"""Persistence for the human decisions and the derived render state.

profiles.json, locks.json and extra-strings.json hold decisions a person made
and belong in git. render-state.json is derivable and lives under out/.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any

BASE_SAMPLING = {
    "temperature": 0.6,
    "top_k": 30,
    "top_p": 0.9,
    "repetition_penalty": 1.05,
}


def _profile(label: str, instruct: str) -> dict[str, Any]:
    return {
        "label": label,
        "speaker": "sohee",
        "language": "german",
        "instruct": instruct,
        "sampling": dict(BASE_SAMPLING),
        "seedPool": [],
        "trim": True,
        "normalize": True,
    }


DEFAULT_PROFILES: dict[str, Any] = {
    "poolSalt": "v1",
    "profiles": {
        "word": _profile(
            "Einzelwort",
            "Sprich das einzelne Wort klar und freundlich, in ruhigem Tempo, "
            "mit neutraler Betonung. Keine Übertreibung, keine Frage-Melodie.",
        ),
        "phoneme": _profile(
            "Laut / Buchstabe",
            "Sprich ausschließlich den Lautwert des Buchstabens, deutlich gedehnt "
            "und langsam — nicht den Buchstabennamen. Also 'mmmmm', nicht 'Em'. "
            "Kein Satz, kein Zusatz, nur der Laut.",
        ),
        "prompt": _profile(
            "Aufgaben-Frage",
            "Sprich wie eine freundliche Kindergärtnerin zu einem fünfjährigen Kind: "
            "warm, deutlich, ruhiges Tempo, leicht fragende Betonung am Satzende. "
            "Freundlich zugewandt, nicht übertrieben fröhlich.",
        ),
        "miss": _profile(
            "Sanftes Feedback",
            "Sprich ruhig und aufmunternd zu einem Kind, das gerade danebenlag. "
            "Kein Tadel, keine Enttäuschung — freundlich erklärend, warm, geduldig.",
        ),
        "reward": _profile(
            "Belohnung",
            "Sprich fröhlich und feiernd zu einem Kind, das etwas geschafft hat. "
            "Lebendig und mit Schwung, aber nicht schrill und nicht zu laut.",
        ),
        "sentence": _profile(
            "Einfacher Satz",
            "Sprich den kurzen Satz klar und einfach, in ruhigem Tempo, "
            "mit natürlicher Satzmelodie. Für ein Kind, das zuhört und mitliest.",
        ),
        "finale": _profile(
            "Lektions-Finale",
            "Sprich den lustigen Satz verspielt und pointiert, mit Schwung und "
            "einem Lächeln in der Stimme. Wie eine kleine Pointe am Ende einer Geschichte.",
        ),
        "ui": _profile(
            "Oberflächen-Ansage",
            "Sprich ruhig, freundlich und neutral. Kurze Ansage, keine Betonung "
            "auf einzelnen Wörtern, kein Drama.",
        ),
    },
}


def _read_json(path: Path) -> dict[str, Any] | None:
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"{path} is not valid JSON: {exc}") from exc


def _write_json(path: Path, payload: dict[str, Any]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8"
    )


@dataclass
class Profile:
    label: str
    speaker: str
    language: str
    instruct: str
    sampling: dict[str, Any]
    seed_pool: list[int]
    trim: bool = True
    normalize: bool = True

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "Profile":
        return cls(
            label=raw["label"],
            speaker=raw["speaker"],
            language=raw["language"],
            instruct=raw["instruct"],
            sampling=dict(raw.get("sampling", BASE_SAMPLING)),
            seed_pool=list(raw.get("seedPool", [])),
            trim=bool(raw.get("trim", True)),
            normalize=bool(raw.get("normalize", True)),
        )

    def to_dict(self) -> dict[str, Any]:
        return {
            "label": self.label,
            "speaker": self.speaker,
            "language": self.language,
            "instruct": self.instruct,
            "sampling": self.sampling,
            "seedPool": self.seed_pool,
            "trim": self.trim,
            "normalize": self.normalize,
        }


@dataclass
class Profiles:
    pool_salt: str
    profiles: dict[str, Profile]

    @classmethod
    def load(cls, path: Path) -> "Profiles":
        raw = _read_json(Path(path)) or DEFAULT_PROFILES
        return cls(
            pool_salt=raw.get("poolSalt", "v1"),
            profiles={n: Profile.from_dict(p) for n, p in raw["profiles"].items()},
        )

    def save(self, path: Path) -> None:
        _write_json(Path(path), {
            "poolSalt": self.pool_salt,
            "profiles": {n: p.to_dict() for n, p in self.profiles.items()},
        })


@dataclass
class Lock:
    seed: int
    profile: str | None = None
    text_override: str | None = None
    note: str | None = None
    source_text: str | None = None

    @classmethod
    def from_dict(cls, raw: dict[str, Any]) -> "Lock":
        return cls(
            seed=int(raw["seed"]),
            profile=raw.get("profile"),
            text_override=raw.get("textOverride"),
            note=raw.get("note"),
            source_text=raw.get("sourceText"),
        )

    def to_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {"seed": self.seed}
        for key, value in (("profile", self.profile),
                           ("textOverride", self.text_override),
                           ("note", self.note),
                           ("sourceText", self.source_text)):
            if value is not None:
                out[key] = value
        return out


@dataclass
class Locks:
    locks: dict[str, Lock] = field(default_factory=dict)

    @classmethod
    def load(cls, path: Path) -> "Locks":
        raw = _read_json(Path(path)) or {"version": 1, "locks": {}}
        return cls({k: Lock.from_dict(v) for k, v in raw.get("locks", {}).items()})

    def save(self, path: Path) -> None:
        _write_json(Path(path), {
            "version": 1,
            "locks": {k: v.to_dict() for k, v in sorted(self.locks.items())},
        })

    def get(self, key: str) -> Lock | None:
        return self.locks.get(key)

    def set(self, key: str, lock: Lock) -> None:
        self.locks[key] = lock

    def remove(self, key: str) -> None:
        self.locks.pop(key, None)


@dataclass
class RenderState:
    """Maps clipKey -> render fingerprint of the file currently on disk."""

    entries: dict[str, str] = field(default_factory=dict)

    @classmethod
    def load(cls, path: Path) -> "RenderState":
        raw = _read_json(Path(path)) or {"version": 1, "entries": {}}
        return cls(dict(raw.get("entries", {})))

    def save(self, path: Path) -> None:
        _write_json(Path(path), {"version": 1, "entries": dict(sorted(self.entries.items()))})
```

- [ ] **Step 4: Tests laufen lassen und Erfolg bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_store.py -v`
Expected: PASS, 8 Tests

- [ ] **Step 5: Die versionierten JSON-Dateien erzeugen**

Run:
```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -c "
from pathlib import Path
from ttskit.store import Profiles, Locks
Profiles.load(Path('profiles.json')).save(Path('profiles.json'))
Locks.load(Path('locks.json')).save(Path('locks.json'))
"
```

`tools/tts/extra-strings.json` von Hand anlegen:

```json
{
  "version": 1,
  "strings": [
    {
      "id": "lockedLessonCue",
      "text": "Das üben wir später.",
      "note": "SessionViewModel.lockedLessonCue()"
    },
    {
      "id": "genericMiss",
      "text": "Probiere eine andere Antwort",
      "note": "SessionViewModel — generisches Miss-Feedback"
    }
  ],
  "templates": []
}
```

Der leere `templates`-Block ist Absicht: er hält den Platz für die Sprechtexte der
abgeleiteten Trainer (Symbol-Jagd, Wort-Detektiv), die laut Spec §2 außerhalb dieses
Scopes liegen.

- [ ] **Step 6: Commit**

```bash
git add tools/tts/
git commit -m "feat(tts): Store für Profile, Locks und Render-State"
```

---

## Task 3: Planung — clipKey, Dedup, Seed-Auflösung, Status

**Files:**
- Create: `tools/tts/ttskit/plan.py`
- Create: `tools/tts/tests/test_plan.py`

**Interfaces:**
- Consumes: `models.Item`, `extract.FIELD_TO_PROFILE`, `store.Profiles`, `store.Locks`, `store.RenderState`
- Produces:
  - `models.Clip(key, profile, text, source_text, seed, locked, item_ids: list[str], fields: list[str], lessons: list[str])`
  - `plan.clip_key(profile: str, source_text: str) -> str`
  - `plan.resolve_seed(key: str, profile_name: str, profiles: Profiles, locks: Locks) -> int`
  - `plan.build_clips(items: list[Item], profiles: Profiles, locks: Locks) -> list[Clip]`
  - `plan.fingerprint(clip: Clip, profile: Profile) -> str`
  - `plan.status_of(clip: Clip, profile: Profile, state: RenderState, audio_dir: Path) -> str` — einer von `missing`, `stale`, `rendered`
  - `plan.orphan_locks(locks: Locks, clips: list[Clip]) -> list[str]`

**Kontext für den Umsetzenden — hier steckt die ganze Subtilität:**

1. **`clip_key` benutzt immer das Default-Profil aus `FIELD_TO_PROFILE`, nie das per Lock überschriebene.** Sonst entsteht ein Zirkelschluss: der Lock wird über den `clipKey` gefunden, aber der `clipKey` hinge vom Lock ab. Ein `lock.profile` ändert also nur, welche Instruktion und welches Sampling benutzt werden — nicht den Schlüssel und nicht den Dateinamen. Der Dateiname trägt damit weiterhin das Default-Profil im Präfix; das ist gewollt und stabil.

2. **`clip_key` benutzt den Originaltext, nie `text_override`.** Der Schlüssel muss an der App-Stelle hängen, nicht am gesprochenen Text — sonst verliert man beim Setzen eines Overrides die Zuordnung.

3. **Ein Clip kann mehrere Items haben** (derselbe `promptTts` in drei Runden). `item_ids` ist sortiert, damit der Fingerprint stabil bleibt.

4. **Leerer Seed-Pool** heißt nicht „Fehler", sondern „Hash direkt als Seed". Ein Lauf funktioniert damit vor jeder Kuratierung und bleibt reproduzierbar.

- [ ] **Step 1: Den failing test schreiben**

`tools/tts/tests/test_plan.py`:

```python
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
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_plan.py -v`
Expected: FAIL mit `ModuleNotFoundError: No module named 'ttskit.plan'`

- [ ] **Step 3: `Clip` zu `models.py` hinzufügen**

An `tools/tts/ttskit/models.py` anhängen:

```python
@dataclass(frozen=True)
class Clip:
    """One render unit. Several items can share it when text and profile match."""

    key: str
    profile: str
    text: str
    source_text: str
    seed: int
    locked: bool
    item_ids: tuple[str, ...]
    fields: tuple[str, ...]
    lessons: tuple[str, ...]
```

Tupel statt Listen, damit `Clip` hashbar bleibt und `dataclasses.replace` in den Tests
funktioniert.

- [ ] **Step 4: `plan.py` implementieren**

```python
"""Turn items plus stored decisions into concrete render units."""

from __future__ import annotations

import hashlib
import json
from pathlib import Path

from .extract import FIELD_TO_PROFILE
from .models import Clip, Item
from .store import Locks, Profile, Profiles, RenderState


def clip_key(profile: str, source_text: str) -> str:
    """Stable identity of a render unit.

    Always built from the *default* profile and the *original* text. A lock may
    override which profile is used for synthesis, but it must not move the key —
    the lock is looked up by this very key.
    """
    digest = hashlib.sha256(source_text.encode("utf-8")).hexdigest()[:12]
    return f"{profile}:{digest}"


def resolve_seed(key: str, profile_name: str, profiles: Profiles, locks: Locks) -> int:
    lock = locks.get(key)
    if lock is not None:
        return lock.seed
    pool = profiles.profiles[profile_name].seed_pool
    digest = hashlib.sha256((key + profiles.pool_salt).encode("utf-8")).hexdigest()
    value = int(digest, 16)
    if pool:
        return pool[value % len(pool)]
    return value % (2 ** 31)


def build_clips(items: list[Item], profiles: Profiles, locks: Locks) -> list[Clip]:
    grouped: dict[str, dict] = {}
    for item in items:
        default_profile = FIELD_TO_PROFILE[item.field]
        key = clip_key(default_profile, item.text)
        bucket = grouped.setdefault(key, {
            "default_profile": default_profile,
            "source_text": item.text,
            "item_ids": set(),
            "fields": set(),
            "lessons": set(),
        })
        bucket["item_ids"].add(item.id)
        bucket["fields"].add(item.field)
        if item.lesson:
            bucket["lessons"].add(item.lesson)

    clips: list[Clip] = []
    for key, bucket in sorted(grouped.items()):
        lock = locks.get(key)
        profile_name = (lock.profile if lock and lock.profile else bucket["default_profile"])
        text = lock.text_override if lock and lock.text_override else bucket["source_text"]
        clips.append(Clip(
            key=key,
            profile=profile_name,
            text=text,
            source_text=bucket["source_text"],
            seed=resolve_seed(key, profile_name, profiles, locks),
            locked=lock is not None,
            item_ids=tuple(sorted(bucket["item_ids"])),
            fields=tuple(sorted(bucket["fields"])),
            lessons=tuple(sorted(bucket["lessons"])),
        ))
    return clips


def fingerprint(clip: Clip, profile: Profile) -> str:
    """Everything that changes the audio bytes — and nothing else.

    Deliberately excludes item_ids, fields and lessons: a new lesson reusing an
    existing prompt must not force a re-render.
    """
    payload = {
        "text": clip.text,
        "profile": clip.profile,
        "seed": clip.seed,
        "speaker": profile.speaker,
        "language": profile.language,
        "instruct": profile.instruct,
        "sampling": dict(sorted(profile.sampling.items())),
        "trim": profile.trim,
        "normalize": profile.normalize,
    }
    blob = json.dumps(payload, sort_keys=True, ensure_ascii=False)
    return hashlib.sha256(blob.encode("utf-8")).hexdigest()[:16]


def status_of(clip: Clip, profile: Profile, state: RenderState, audio_dir: Path) -> str:
    if not (Path(audio_dir) / f"{clip.key}.wav").exists():
        return "missing"
    if state.entries.get(clip.key) != fingerprint(clip, profile):
        return "stale"
    return "rendered"


def orphan_locks(locks: Locks, clips: list[Clip]) -> list[str]:
    """Locks whose clip no longer exists — usually because the text changed.

    Reported, never removed: dropping a curated decision is the user's call.
    """
    live = {c.key for c in clips}
    return sorted(k for k in locks.locks if k not in live)
```

- [ ] **Step 5: Tests laufen lassen und Erfolg bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_plan.py -v`
Expected: PASS, 17 Tests

- [ ] **Step 6: Gegen das echte Content-Pack gegenprüfen**

Run:
```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -c "
from pathlib import Path
import collections
from ttskit.extract import extract_items
from ttskit.plan import build_clips
from ttskit.store import Profiles, Locks
items = extract_items(Path('../../app/src/main/assets/content'))
clips = build_clips(items, Profiles.load(Path('profiles.json')), Locks.load(Path('locks.json')))
print('items', len(items), '-> clips', len(clips))
print(collections.Counter(c.profile for c in clips))
"
```
Expected: 891 Items (ohne `extra-strings.json`) werden zu 692 Clips; das `phoneme`-Profil hat 37 Clips (nicht 57 — `stretchTts` und `phonemeTts` überschneiden sich). Dedupliziert wird pro `(Profil, Text)`, nicht über alle Texte hinweg — deshalb ist die Clip-Zahl die Summe der Profil-Uniques: word 260 · prompt 223 · miss 81 · reward 47 · phoneme 37 · sentence 26 · finale 18.

- [ ] **Step 7: Commit**

```bash
git add tools/tts/
git commit -m "feat(tts): Clip-Planung mit Dedup, Seed-Pool-Auflösung und Stale-Erkennung"
```

---

## Task 4: Audio-Nachbearbeitung

**Files:**
- Create: `tools/tts/ttskit/audio.py`
- Create: `tools/tts/tests/test_audio.py`

**Interfaces:**
- Consumes: nichts
- Produces:
  - `audio.trim_silence(wav: np.ndarray, sr: int, threshold: float = 0.01, pad_ms: int = 30) -> np.ndarray`
  - `audio.normalize_peak(wav: np.ndarray, peak_dbfs: float = -1.0) -> np.ndarray`
  - `audio.postprocess(wav: np.ndarray, sr: int, trim: bool, normalize: bool) -> np.ndarray`
  - `audio.write_wav(path: Path, wav: np.ndarray, sr: int) -> None` — 16-bit PCM
  - `audio.SAMPLE_RATE: int = 24000`

**Kontext für den Umsetzenden:**

Der Sicherheitspolster (`pad_ms`) ist der Grund, warum getrimmt und nicht hart geschnitten wird: stimmlose Konsonanten am Wortanfang („Pf", „Sch") liegen knapp unter dem Schwellwert und würden sonst abgeschnitten. 30 ms bei 24 kHz sind 720 Samples.

- [ ] **Step 1: Den failing test schreiben**

`tools/tts/tests/test_audio.py`:

```python
import numpy as np
import soundfile as sf

from ttskit.audio import (
    SAMPLE_RATE, normalize_peak, postprocess, trim_silence, write_wav,
)


def tone(n: int, amplitude: float = 0.5) -> np.ndarray:
    t = np.arange(n, dtype=np.float32) / SAMPLE_RATE
    return (amplitude * np.sin(2 * np.pi * 440 * t)).astype(np.float32)


def test_trim_removes_leading_and_trailing_silence():
    wav = np.concatenate([np.zeros(12000, np.float32), tone(24000),
                          np.zeros(12000, np.float32)])
    trimmed = trim_silence(wav, SAMPLE_RATE, pad_ms=0)
    assert len(trimmed) < len(wav)
    assert abs(len(trimmed) - 24000) < 500


def test_trim_keeps_a_safety_pad():
    wav = np.concatenate([np.zeros(12000, np.float32), tone(24000),
                          np.zeros(12000, np.float32)])
    padded = trim_silence(wav, SAMPLE_RATE, pad_ms=30)
    tight = trim_silence(wav, SAMPLE_RATE, pad_ms=0)
    # 30 ms of pad on both ends = 2 * 720 samples
    assert len(padded) - len(tight) == 1440


def test_trim_never_pads_beyond_the_original():
    wav = tone(1000)
    assert len(trim_silence(wav, SAMPLE_RATE, pad_ms=500)) == 1000


def test_trim_leaves_an_all_silent_clip_alone():
    wav = np.zeros(5000, np.float32)
    assert len(trim_silence(wav, SAMPLE_RATE)) == 5000


def test_normalize_lifts_peak_to_target():
    quiet = tone(2400, amplitude=0.05)
    loud = normalize_peak(quiet, peak_dbfs=-1.0)
    expected = 10 ** (-1.0 / 20)
    assert np.isclose(np.max(np.abs(loud)), expected, atol=1e-3)


def test_normalize_lowers_a_hot_signal():
    hot = tone(2400, amplitude=0.99)
    out = normalize_peak(hot, peak_dbfs=-1.0)
    assert np.max(np.abs(out)) < np.max(np.abs(hot))


def test_normalize_leaves_silence_alone():
    silence = np.zeros(1000, np.float32)
    assert np.max(np.abs(normalize_peak(silence))) == 0.0


def test_postprocess_can_be_switched_off():
    wav = np.concatenate([np.zeros(6000, np.float32), tone(6000, 0.05)])
    assert np.array_equal(postprocess(wav, SAMPLE_RATE, trim=False, normalize=False), wav)


def test_write_wav_is_24k_mono_16bit(tmp_path):
    path = tmp_path / "clip.wav"
    write_wav(path, tone(2400), SAMPLE_RATE)
    data, sr = sf.read(path, dtype="int16")
    assert sr == SAMPLE_RATE
    assert data.ndim == 1
    info = sf.info(path)
    assert info.subtype == "PCM_16"


def test_write_wav_creates_parent_directories(tmp_path):
    path = tmp_path / "deep" / "nested" / "clip.wav"
    write_wav(path, tone(240), SAMPLE_RATE)
    assert path.exists()
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_audio.py -v`
Expected: FAIL mit `ModuleNotFoundError: No module named 'ttskit.audio'`

- [ ] **Step 3: `audio.py` implementieren**

```python
"""Post-processing between the model output and the file on disk."""

from __future__ import annotations

from pathlib import Path

import numpy as np
import soundfile as sf

SAMPLE_RATE = 24000


def trim_silence(wav: np.ndarray, sr: int, threshold: float = 0.01,
                 pad_ms: int = 30) -> np.ndarray:
    """Cut leading and trailing silence, keeping a safety pad.

    The pad matters: unvoiced onsets ("Pf", "Sch") sit just below the threshold
    and would otherwise lose their first few milliseconds.
    """
    loud = np.where(np.abs(wav) >= threshold)[0]
    if loud.size == 0:
        return wav
    pad = int(sr * pad_ms / 1000)
    start = max(0, int(loud[0]) - pad)
    end = min(len(wav), int(loud[-1]) + 1 + pad)
    return wav[start:end]


def normalize_peak(wav: np.ndarray, peak_dbfs: float = -1.0) -> np.ndarray:
    """Scale so the loudest sample sits at `peak_dbfs`."""
    peak = float(np.max(np.abs(wav))) if wav.size else 0.0
    if peak == 0.0:
        return wav
    target = 10 ** (peak_dbfs / 20)
    return (wav * (target / peak)).astype(np.float32)


def postprocess(wav: np.ndarray, sr: int, trim: bool, normalize: bool) -> np.ndarray:
    out = np.asarray(wav, dtype=np.float32)
    if trim:
        out = trim_silence(out, sr)
    if normalize:
        out = normalize_peak(out)
    return out


def write_wav(path: Path, wav: np.ndarray, sr: int) -> None:
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    clipped = np.clip(np.asarray(wav, dtype=np.float32), -1.0, 1.0)
    sf.write(path, clipped, sr, subtype="PCM_16")
```

- [ ] **Step 4: Tests laufen lassen und Erfolg bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_audio.py -v`
Expected: PASS, 10 Tests

- [ ] **Step 5: Commit**

```bash
git add tools/tts/
git commit -m "feat(tts): Silence-Trim, Peak-Normalisierung und WAV-Ausgabe"
```

---

## Task 5: CLI-Gerüst mit `extract` und `status`

**Files:**
- Create: `tools/tts/ttskit/paths.py`, `tools/tts/ttskit/cli.py`, `tools/tts/tts`
- Create: `tools/tts/tests/test_cli_status.py`

**Interfaces:**
- Consumes: `extract`, `store`, `plan`
- Produces:
  - `paths.Paths(root: Path)` mit Properties `content_dir`, `profiles`, `locks`, `extra_strings`, `out`, `manifest`, `render_state`, `audio`, `candidates`
  - `cli.load_context(paths: Paths) -> Context` — `Context(items, profiles, locks, clips, state)`
  - `cli.main(argv: list[str] | None = None) -> int`
  - `cli.cmd_extract(paths) -> int`, `cli.cmd_status(paths) -> int`

**Kontext für den Umsetzenden:**

Dies ist das erste nutzbare Deliverable: `tools/tts/tts status` zeigt die Lage, ohne dass ein Modell geladen wird. `paths.py` existiert, damit Tests mit `tmp_path` arbeiten können, statt auf feste Pfade angewiesen zu sein.

`load_context` darf `engine` **nicht** importieren — der Import von `torch` kostet Sekunden, und `status` soll sofort antworten.

- [ ] **Step 1: Den failing test schreiben**

`tools/tts/tests/test_cli_status.py`:

```python
import json
import shutil
from pathlib import Path

from ttskit.cli import cmd_extract, cmd_status, load_context
from ttskit.paths import Paths


def make_root(tmp_path: Path, content_dir: Path) -> Paths:
    root = tmp_path / "ttsroot"
    (root / "content").mkdir(parents=True)
    for f in content_dir.iterdir():
        shutil.copy(f, root / "content" / f.name)
    (root / "extra-strings.json").write_text(
        json.dumps({"version": 1, "strings": [], "templates": []}), encoding="utf-8")
    return Paths(root=root, content_dir=root / "content")


def test_extract_writes_a_manifest(tmp_path, content_dir):
    paths = make_root(tmp_path, content_dir)
    assert cmd_extract(paths) == 0
    manifest = json.loads(paths.manifest.read_text(encoding="utf-8"))
    assert manifest["itemCount"] == len(manifest["items"])
    assert any(i["id"] == "atom:maus:lemma" for i in manifest["items"])
    assert manifest["items"][0]["clipKey"].count(":") == 1


def test_status_runs_without_a_model_and_reports_missing(tmp_path, content_dir, capsys):
    paths = make_root(tmp_path, content_dir)
    assert cmd_status(paths) == 0
    out = capsys.readouterr().out
    assert "missing" in out
    assert "Seed-Pool leer" in out, "empty pools must be warned about"


def test_status_reports_orphan_locks(tmp_path, content_dir, capsys):
    paths = make_root(tmp_path, content_dir)
    paths.locks.write_text(json.dumps({"version": 1, "locks": {
        "prompt:000000000000": {"seed": 5, "sourceText": "Ein alter Satz"},
    }}), encoding="utf-8")
    cmd_status(paths)
    out = capsys.readouterr().out
    assert "verwaist" in out
    assert "Ein alter Satz" in out, "orphans must be readable, not just a hash"


def test_load_context_does_not_import_torch(tmp_path, content_dir):
    import sys
    sys.modules.pop("torch", None)
    paths = make_root(tmp_path, content_dir)
    load_context(paths)
    assert "torch" not in sys.modules, "status must stay instant"
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_cli_status.py -v`
Expected: FAIL mit `ModuleNotFoundError: No module named 'ttskit.paths'`

- [ ] **Step 3: `paths.py` implementieren**

```python
"""All filesystem locations in one place, so tests can relocate them."""

from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path

TOOL_ROOT = Path(__file__).resolve().parent.parent
REPO_ROOT = TOOL_ROOT.parent.parent


@dataclass
class Paths:
    root: Path = TOOL_ROOT
    content_dir: Path = REPO_ROOT / "app" / "src" / "main" / "assets" / "content"

    @property
    def profiles(self) -> Path:
        return self.root / "profiles.json"

    @property
    def locks(self) -> Path:
        return self.root / "locks.json"

    @property
    def extra_strings(self) -> Path:
        return self.root / "extra-strings.json"

    @property
    def out(self) -> Path:
        return self.root / "out"

    @property
    def manifest(self) -> Path:
        return self.out / "manifest.json"

    @property
    def render_state(self) -> Path:
        return self.out / "render-state.json"

    @property
    def audio(self) -> Path:
        return self.out / "audio"

    @property
    def candidates(self) -> Path:
        return self.out / "candidates"
```

- [ ] **Step 4: `cli.py` mit `extract` und `status` implementieren**

```python
"""Command line entry point. Imports the model layer lazily."""

from __future__ import annotations

import argparse
import collections
import json
from dataclasses import dataclass

from .extract import extract_items
from .models import Clip, Item
from .paths import Paths
from .plan import build_clips, orphan_locks, status_of
from .store import Locks, Profiles, RenderState


@dataclass
class Context:
    items: list[Item]
    profiles: Profiles
    locks: Locks
    clips: list[Clip]
    state: RenderState


def load_context(paths: Paths) -> Context:
    extra = None
    if paths.extra_strings.exists():
        extra = json.loads(paths.extra_strings.read_text(encoding="utf-8"))
    items = extract_items(paths.content_dir, extra_strings=extra)
    profiles = Profiles.load(paths.profiles)
    locks = Locks.load(paths.locks)
    return Context(
        items=items,
        profiles=profiles,
        locks=locks,
        clips=build_clips(items, profiles, locks),
        state=RenderState.load(paths.render_state),
    )


def cmd_extract(paths: Paths) -> int:
    ctx = load_context(paths)
    by_text_profile = {c.key: c for c in ctx.clips}
    item_clip = {}
    for clip in ctx.clips:
        for item_id in clip.item_ids:
            item_clip[item_id] = clip.key

    payload = {
        "version": 1,
        "itemCount": len(ctx.items),
        "clipCount": len(by_text_profile),
        "items": [{
            "id": i.id, "text": i.text, "field": i.field, "source": i.source,
            "lesson": i.lesson, "label": i.label, "clipKey": item_clip[i.id],
        } for i in ctx.items],
    }
    paths.manifest.parent.mkdir(parents=True, exist_ok=True)
    paths.manifest.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(ctx.items)} Items → {len(by_text_profile)} Clips → {paths.manifest}")
    return 0


def cmd_status(paths: Paths) -> int:
    ctx = load_context(paths)
    counts: dict[str, collections.Counter] = collections.defaultdict(collections.Counter)
    for clip in ctx.clips:
        profile = ctx.profiles.profiles[clip.profile]
        counts[clip.profile][status_of(clip, profile, ctx.state, paths.audio)] += 1

    # Column headers use the technical status vocabulary (missing/stale/rendered), not
    # German labels: the cells hold counts, so these words are the only place the status
    # names appear at all — and a test asserts "missing" shows up in the output.
    print(f"{'Profil':<10} {'gesamt':>7} {'missing':>7} {'stale':>7} {'rendered':>8} {'Pool':>6}")
    for name in sorted(counts):
        c = counts[name]
        total = sum(c.values())
        pool = len(ctx.profiles.profiles[name].seed_pool)
        print(f"{name:<10} {total:>7} {c['missing']:>7} {c['stale']:>7} "
              f"{c['rendered']:>8} {pool:>6}")

    locked = sum(1 for c in ctx.clips if c.locked)
    print(f"\n{len(ctx.clips)} Clips aus {len(ctx.items)} Items, davon {locked} gelockt.")

    empty = [n for n, p in ctx.profiles.profiles.items() if not p.seed_pool]
    if empty:
        print(f"Seed-Pool leer bei: {', '.join(sorted(empty))} "
              f"— Seeds werden aus dem Clip-Hash abgeleitet.")

    orphans = orphan_locks(ctx.locks, ctx.clips)
    if orphans:
        print(f"\n{len(orphans)} verwaiste Locks (Text hat sich geändert?):")
        for key in orphans:
            lock = ctx.locks.get(key)
            shown = lock.source_text if lock and lock.source_text else "(kein Text notiert)"
            print(f"  {key}  seed={lock.seed}  {shown!r}")

    templates = []
    if paths.extra_strings.exists():
        raw = json.loads(paths.extra_strings.read_text(encoding="utf-8"))
        templates = raw.get("templates", [])
    if not templates:
        print("\nHinweis: keine Template-Expansionen erfasst — die Sprechtexte von "
              "Symbol-Jagd und Wort-Detektiv fehlen im Paket (Spec §2).")
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="tts", description="Qwen-TTS Audio-Pipeline")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("extract", help="Content-JSON → out/manifest.json")
    sub.add_parser("status", help="Überblick über Clips, Pools und Locks")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    paths = Paths()
    if args.command == "extract":
        return cmd_extract(paths)
    if args.command == "status":
        return cmd_status(paths)
    return 1
```

- [ ] **Step 5: Den Einstiegspunkt `tools/tts/tts` anlegen**

```python
#!/usr/bin/env python3
"""Entry point for the Qwen-TTS pipeline. Run with the qwen venv interpreter."""

import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from ttskit.cli import main  # noqa: E402

if __name__ == "__main__":
    raise SystemExit(main())
```

Ausführbar machen:

```bash
chmod +x tools/tts/tts
```

- [ ] **Step 6: Tests laufen lassen und Erfolg bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -v`
Expected: PASS, alle Tests aus den Tasks 1–5

- [ ] **Step 7: Gegen das echte Content-Pack laufen lassen**

Run:
```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python ./tts extract && ~/qwen-tts-test/.venv/bin/python ./tts status
```
Expected: „893 Items → 694 Clips" (893 statt 891, weil `extra-strings.json` zwei `ui:`-Items beisteuert), danach eine Tabelle mit acht Profilen, überall `fehlt`, der Hinweis auf leere Seed-Pools und der Hinweis auf fehlende Template-Expansionen.

- [ ] **Step 8: Sicherstellen, dass `out/` nicht im Git landet**

Run: `git status --short tools/tts/`
Expected: `tools/tts/out/` taucht **nicht** auf (globales `out/`-Pattern greift). Falls doch, `tools/tts/.gitignore` mit `out/` anlegen.

- [ ] **Step 9: Commit**

```bash
git add tools/tts/
git commit -m "feat(tts): CLI mit extract und status, ohne Modell-Import"
```

---

## Task 6: Engine — Modell laden und generieren

**Files:**
- Create: `tools/tts/ttskit/engine.py`
- Create: `tools/tts/tests/test_engine.py`

**Interfaces:**
- Consumes: `store.Profile`, `store.Profiles`
- Produces:
  - `engine.CHECKPOINT: str = "Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice"`
  - `engine.pick_device() -> str`
  - `engine.Engine(checkpoint: str = CHECKPOINT, device: str | None = None)`
  - `Engine.load() -> None`, `Engine.loaded: bool`, `Engine.load_error: str | None`
  - `Engine.validate(profiles: Profiles) -> list[str]` — Liste der Fehlermeldungen, leer wenn alles passt
  - `Engine.generate(text: str, profile: Profile, seed: int) -> tuple[np.ndarray, int]`

**Kontext für den Umsetzenden:**

Der Determinismus hängt an drei Dingen, die alle in `generate` zusammenkommen müssen:
`torch.manual_seed(seed)` unmittelbar vor dem Aufruf, genau ein Text pro Aufruf
(Batchgröße 1) und identische Sampling-Parameter. Wird eines davon verletzt, sind alle
kuratierten Seeds wertlos.

`load()` fängt Fehler ab und legt sie in `load_error` — der Server soll auch ohne Modell
starten können, damit bereits gerenderte Clips kuratierbar bleiben.

Die Library validiert Speaker und Sprache case-insensitiv. `validate` prüft vorab und
liefert lesbare Meldungen inklusive der gültigen Werte, statt den Fehler erst mitten im
Batch auftauchen zu lassen.

- [ ] **Step 1: Den failing test schreiben**

`tools/tts/tests/test_engine.py`:

```python
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
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_engine.py -v`
Expected: FAIL mit `ModuleNotFoundError: No module named 'ttskit.engine'`

- [ ] **Step 3: `engine.py` implementieren**

```python
"""Thin wrapper around qwen_tts that makes generation seed-reproducible."""

from __future__ import annotations

import numpy as np

from .store import Profile, Profiles

CHECKPOINT = "Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice"


def pick_device() -> str:
    import torch

    if torch.backends.mps.is_available():
        return "mps"
    if torch.cuda.is_available():
        return "cuda"
    return "cpu"


class Engine:
    def __init__(self, checkpoint: str = CHECKPOINT, device: str | None = None) -> None:
        self.checkpoint = checkpoint
        self.device = device
        self.loaded = False
        self.load_error: str | None = None
        self._model = None

    def load(self) -> None:
        """Load the model. Failures are captured, not raised.

        The server must be able to start without a model so already rendered
        clips stay curatable.
        """
        try:
            import torch
            from qwen_tts import Qwen3TTSModel

            device = self.device or pick_device()
            self._model = Qwen3TTSModel.from_pretrained(
                self.checkpoint,
                device_map=device,
                dtype=torch.bfloat16,
                attn_implementation="sdpa",
            )
            self.device = device
            self.loaded = True
            self.load_error = None
        except Exception as exc:  # noqa: BLE001 - surfaced in the UI
            self.loaded = False
            self.load_error = f"{type(exc).__name__}: {exc}"

    def validate(self, profiles: Profiles) -> list[str]:
        if not self.loaded:
            return ["Modell nicht geladen — Speaker und Sprache ungeprüft."]
        speakers = {s.lower() for s in (self._model.get_supported_speakers() or [])}
        languages = {l.lower() for l in (self._model.get_supported_languages() or [])}
        errors: list[str] = []
        for name, profile in sorted(profiles.profiles.items()):
            if speakers and profile.speaker.lower() not in speakers:
                errors.append(
                    f"Profil {name!r}: Speaker {profile.speaker!r} wird nicht unterstützt. "
                    f"Gültig: {', '.join(sorted(speakers))}")
            if languages and profile.language.lower() not in languages:
                errors.append(
                    f"Profil {name!r}: Sprache {profile.language!r} wird nicht unterstützt. "
                    f"Gültig: {', '.join(sorted(languages))}")
        return errors

    def generate(self, text: str, profile: Profile, seed: int) -> tuple[np.ndarray, int]:
        """Synthesize one clip.

        Reproducibility rests on all three of these together: the seed set
        immediately before the call, exactly one text per call (batch size 1),
        and unchanged sampling parameters.
        """
        if not self.loaded or self._model is None:
            raise RuntimeError("Engine not loaded — call load() first.")

        import torch

        torch.manual_seed(seed)
        wavs, sample_rate = self._model.generate_custom_voice(
            text=text,
            speaker=profile.speaker,
            language=profile.language,
            instruct=profile.instruct or None,
            **profile.sampling,
        )
        return np.asarray(wavs[0], dtype=np.float32), int(sample_rate)
```

- [ ] **Step 4: Tests ohne Modell laufen lassen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_engine.py -v`
Expected: PASS, 5 Tests, 1 skipped (`test_same_seed_gives_bit_identical_audio`)

- [ ] **Step 5: Den Smoke-Test mit Modell laufen lassen**

Run: `cd tools/tts && TTS_SMOKE=1 ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_engine.py -v -s`
Expected: PASS, 6 Tests. Dauer rund eine Minute (Modell-Load + drei Generierungen).

Schlägt `test_same_seed_gives_bit_identical_audio` fehl, **hier stoppen und melden** — dann trägt die Grundannahme des Seed-Konzepts nicht und die Spec muss überarbeitet werden.

- [ ] **Step 6: Commit**

```bash
git add tools/tts/
git commit -m "feat(tts): Engine mit seed-reproduzierbarer Generierung und Profil-Validierung"
```

---

## Task 7: Renderer, `render` und `sample`

**Files:**
- Create: `tools/tts/ttskit/render.py`
- Modify: `tools/tts/ttskit/cli.py` — Subkommandos `render` und `sample`
- Create: `tools/tts/tests/test_render.py`

**Interfaces:**
- Consumes: `engine.Engine`, `plan.fingerprint`, `plan.status_of`, `audio.postprocess`, `audio.write_wav`, `store.RenderState`
- Produces:
  - `render.Progress(index: int, total: int, clip_key: str, status: str, message: str)`
  - `render.render_clips(clips, profiles, engine, state, paths, *, force=False, only=None, profile=None, dry_run=False, progress=None, cancel=None) -> RenderReport`
  - `render.RenderReport(rendered: int, skipped: int, failed: list[tuple[str, str]], dry_run: bool)`
  - `render.sample_candidates(clip, profile, engine, paths, seeds: list[int], progress=None) -> list[int]`
  - `render.random_seeds(n: int, exclude: set[int]) -> list[int]`
  - `render.candidate_seeds(paths, clip_key: str) -> list[int]`
  - `cli.cmd_render(paths, args) -> int`, `cli.cmd_sample(paths, args) -> int`

**Kontext für den Umsetzenden:**

Der Renderer bekommt die Engine **injiziert** — dadurch lässt sich die gesamte Batch-Logik
mit einer Fake-Engine testen, ohne 4 GB zu laden. Das ist der Grund für die Trennung.

Ein Fehlschlag bei einem Clip darf den Batch nicht stoppen: er landet in
`report.failed` und die Schleife läuft weiter.

Der Render-State wird **nach jedem Clip** geschrieben, nicht am Ende. Ein abgebrochener
25-Minuten-Lauf soll nicht die ganze Arbeit verlieren.

- [ ] **Step 1: Den failing test schreiben**

`tools/tts/tests/test_render.py`:

```python
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


def test_candidate_seeds_lists_what_is_on_disk(setup):
    from ttskit.render import candidate_seeds

    paths, profiles, clips, state = setup
    clip = clips[0]
    sample_candidates(clip, profiles.profiles[clip.profile], FakeEngine(),
                      paths, seeds=[9, 7, 8])
    assert candidate_seeds(paths, clip.key) == [7, 8, 9], "sorted"
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_render.py -v`
Expected: FAIL mit `ModuleNotFoundError: No module named 'ttskit.render'`

- [ ] **Step 3: `render.py` implementieren**

```python
"""Batch rendering and candidate sampling.

The engine is injected so the whole batch logic is testable with a fake —
loading 4 GB of weights to check a loop would be absurd.
"""

from __future__ import annotations

import fnmatch
import secrets
from dataclasses import dataclass, field
from pathlib import Path
from typing import Callable, Iterable, Protocol

import numpy as np

from .audio import postprocess, write_wav
from .models import Clip
from .paths import Paths
from .plan import fingerprint
from .store import Profile, Profiles, RenderState


class SupportsGenerate(Protocol):
    def generate(self, text: str, profile: Profile, seed: int) -> tuple[np.ndarray, int]: ...


@dataclass
class Progress:
    index: int
    total: int
    clip_key: str
    status: str
    message: str = ""


@dataclass
class RenderReport:
    rendered: int = 0
    skipped: int = 0
    failed: list[tuple[str, str]] = field(default_factory=list)
    dry_run: bool = False


def _select(clips: Iterable[Clip], only: str | None, profile: str | None) -> list[Clip]:
    out = list(clips)
    if profile:
        out = [c for c in out if c.profile == profile]
    if only:
        out = [c for c in out if fnmatch.fnmatch(c.key, only)
               or any(fnmatch.fnmatch(i, only) for i in c.item_ids)]
    return out


def render_clips(
    clips: Iterable[Clip],
    profiles: Profiles,
    engine: SupportsGenerate,
    state: RenderState,
    paths: Paths,
    *,
    force: bool = False,
    only: str | None = None,
    profile: str | None = None,
    dry_run: bool = False,
    progress: Callable[[Progress], None] | None = None,
    cancel: Callable[[], bool] | None = None,
) -> RenderReport:
    selected = _select(clips, only, profile)
    report = RenderReport(dry_run=dry_run)

    todo: list[tuple[Clip, Profile, str]] = []
    for clip in selected:
        prof = profiles.profiles[clip.profile]
        stamp = fingerprint(clip, prof)
        up_to_date = (state.entries.get(clip.key) == stamp
                      and (paths.audio / f"{clip.key}.wav").exists())
        if up_to_date and not force:
            report.skipped += 1
            continue
        todo.append((clip, prof, stamp))

    if dry_run:
        report.rendered = len(todo)
        return report

    total = len(todo)
    for index, (clip, prof, stamp) in enumerate(todo, start=1):
        if cancel is not None and cancel():
            break
        try:
            wav, sample_rate = engine.generate(clip.text, prof, clip.seed)
            wav = postprocess(wav, sample_rate, trim=prof.trim, normalize=prof.normalize)
            write_wav(paths.audio / f"{clip.key}.wav", wav, sample_rate)
            state.entries[clip.key] = stamp
            # Written per clip, not at the end: an aborted 25-minute run
            # must not throw away the work it already did.
            state.save(paths.render_state)
            report.rendered += 1
            status = "ok"
            message = ""
        except Exception as exc:  # noqa: BLE001 - reported, batch continues
            message = f"{type(exc).__name__}: {exc}"
            report.failed.append((clip.key, message))
            status = "failed"
        if progress is not None:
            progress(Progress(index=index, total=total, clip_key=clip.key,
                              status=status, message=message))
    return report


def random_seeds(n: int, exclude: set[int] | None = None) -> list[int]:
    blocked = set(exclude or ())
    out: list[int] = []
    while len(out) < n:
        candidate = secrets.randbelow(2 ** 31)
        if candidate in blocked:
            continue
        blocked.add(candidate)
        out.append(candidate)
    return out


def sample_candidates(
    clip: Clip,
    profile: Profile,
    engine: SupportsGenerate,
    paths: Paths,
    seeds: list[int],
    progress: Callable[[Progress], None] | None = None,
) -> list[int]:
    written: list[int] = []
    for index, seed in enumerate(seeds, start=1):
        try:
            wav, sample_rate = engine.generate(clip.text, profile, seed)
            wav = postprocess(wav, sample_rate, trim=profile.trim,
                              normalize=profile.normalize)
            write_wav(paths.candidates / clip.key / f"{seed}.wav", wav, sample_rate)
            written.append(seed)
            status, message = "ok", ""
        except Exception as exc:  # noqa: BLE001
            status, message = "failed", f"{type(exc).__name__}: {exc}"
        if progress is not None:
            progress(Progress(index=index, total=len(seeds), clip_key=clip.key,
                              status=status, message=message))
    return written


def candidate_seeds(paths: Paths, clip_key: str) -> list[int]:
    directory = Path(paths.candidates) / clip_key
    if not directory.exists():
        return []
    return sorted(int(p.stem) for p in directory.glob("*.wav") if p.stem.isdigit())
```

- [ ] **Step 4: `cli.py` um `render` und `sample` erweitern**

In `build_parser()` vor dem `return` einfügen:

```python
    render_parser = sub.add_parser("render", help="Finaler Lauf, inkrementell")
    render_parser.add_argument("--profile", help="nur dieses Profil")
    render_parser.add_argument("--only", help="Glob auf clipKey oder itemId")
    render_parser.add_argument("--force", action="store_true", help="alles neu rendern")
    render_parser.add_argument("--dry-run", action="store_true", help="nur zählen")

    sample_parser = sub.add_parser("sample", help="Kandidaten-Seeds würfeln")
    sample_parser.add_argument("--profile", required=True)
    sample_parser.add_argument("-n", type=int, default=8, help="Anzahl Seeds")
    sample_parser.add_argument("--examples", type=int, default=3,
                               help="wie viele Beispiel-Clips des Profils")
```

In `main()` vor dem `return 1` einfügen:

```python
    if args.command == "render":
        return cmd_render(paths, args)
    if args.command == "sample":
        return cmd_sample(paths, args)
```

Und die beiden Kommandos ans Ende von `cli.py`, **vor** `build_parser`:

```python
def _engine_or_exit(profiles: Profiles):
    from .engine import Engine  # local import: keeps torch out of `status`

    engine = Engine()
    print("Lade Modell ...")
    engine.load()
    if not engine.loaded:
        print(f"Modell konnte nicht geladen werden: {engine.load_error}")
        return None
    errors = engine.validate(profiles)
    if errors:
        for error in errors:
            print(error)
        return None
    print(f"Modell geladen auf {engine.device}.")
    return engine


def cmd_render(paths: Paths, args) -> int:
    from .render import render_clips

    ctx = load_context(paths)
    if args.dry_run:
        report = render_clips(ctx.clips, ctx.profiles, None, ctx.state, paths,
                              force=args.force, only=args.only,
                              profile=args.profile, dry_run=True)
        print(f"{report.rendered} Clips würden gerendert, {report.skipped} übersprungen.")
        return 0

    engine = _engine_or_exit(ctx.profiles)
    if engine is None:
        return 1

    def show(p) -> None:
        mark = "!" if p.status == "failed" else "."
        print(f"[{p.index}/{p.total}] {mark} {p.clip_key} {p.message}".rstrip())

    report = render_clips(ctx.clips, ctx.profiles, engine, ctx.state, paths,
                          force=args.force, only=args.only,
                          profile=args.profile, progress=show)
    print(f"\n{report.rendered} gerendert, {report.skipped} übersprungen, "
          f"{len(report.failed)} fehlgeschlagen.")
    for key, message in report.failed:
        print(f"  {key}: {message}")
    return 1 if report.failed else 0


def cmd_sample(paths: Paths, args) -> int:
    from .render import random_seeds, sample_candidates

    ctx = load_context(paths)
    if args.profile not in ctx.profiles.profiles:
        print(f"Unbekanntes Profil {args.profile!r}. "
              f"Bekannt: {', '.join(sorted(ctx.profiles.profiles))}")
        return 1

    clips = [c for c in ctx.clips if c.profile == args.profile][: args.examples]
    if not clips:
        print(f"Keine Clips im Profil {args.profile!r}.")
        return 1

    engine = _engine_or_exit(ctx.profiles)
    if engine is None:
        return 1

    profile = ctx.profiles.profiles[args.profile]
    seeds = random_seeds(args.n, exclude=set(profile.seed_pool))
    print(f"Seeds: {seeds}")
    for clip in clips:
        print(f"\n{clip.key}  {clip.text!r}")
        sample_candidates(clip, profile, engine, paths, seeds,
                          progress=lambda p: print(f"  [{p.index}/{p.total}] "
                                                   f"{p.status} {p.message}".rstrip()))
    print(f"\nKandidaten unter {paths.candidates} — im Web-Interface kuratieren.")
    return 0
```

- [ ] **Step 5: Tests laufen lassen und Erfolg bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -v`
Expected: PASS, alle Tests aus den Tasks 1–7 (Engine-Smoke skipped)

- [ ] **Step 6: Dry-Run gegen das echte Content-Pack**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python ./tts render --dry-run`
Expected: „694 Clips würden gerendert, 0 übersprungen." — ohne Modell-Load, in unter einer Sekunde.

- [ ] **Step 7: Echten Teil-Lauf gegen ein kleines Profil**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python ./tts render --profile finale`
Expected: 18 Clips werden gerendert, Dateien liegen unter `out/audio/finale:*.wav`. Ein zweiter Aufruf meldet „0 gerendert, 18 übersprungen."

Danach zwei Dateien anhören und prüfen, ob Trim und Lautstärke passen.

- [ ] **Step 8: Commit**

```bash
git add tools/tts/
git commit -m "feat(tts): inkrementeller Renderer, Kandidaten-Sampling, render/sample-CLI"
```

---

## Task 8: FastAPI-Server mit Job-Queue

**Files:**
- Create: `tools/tts/ttskit/server.py`
- Modify: `tools/tts/ttskit/cli.py` — Subkommando `web`
- Create: `tools/tts/tests/test_server.py`

**Interfaces:**
- Consumes: alles Bisherige
- Produces:
  - `server.create_app(paths: Paths, engine=None) -> fastapi.FastAPI`
  - `server.JobQueue` mit `start()`, `submit(name, fn)`, `cancel()`, `status()`, `publish(event)`, `subscribe()`, `unsubscribe(sub)`
  - Endpunkte:
    - `GET /api/state` → `{engine: {...}, profiles: {...}, clips: [...], orphans: [...]}`
    - `PUT /api/profiles/{name}` — Body `{instruct?, sampling?, trim?, normalize?}`
    - `POST /api/profiles/{name}/pool` — Body `{seed: int}` → Seed in den Pool
    - `DELETE /api/profiles/{name}/pool/{seed}`
    - `POST /api/clips/{key}/candidates` — Body `{n: int}` → Job
    - `GET /api/clips/{key}/candidates` → `{seeds: [...]}`
    - `POST /api/clips/{key}/lock` — Body `{seed, textOverride?, profile?, note?}`
    - `DELETE /api/clips/{key}/lock`
    - `POST /api/render` — Body `{profile?, force?}` → Job
    - `POST /api/jobs/cancel`
    - `GET /api/jobs` → aktueller Job + Fortschritt
    - `GET /audio/{key}.wav`, `GET /candidates/{key}/{seed}.wav`
    - `GET /app.js`, `GET /style.css` — Task 9 legt die Dateien an; bis dahin 404
    - `GET /events` → SSE-Stream
    - `GET /` → `static/index.html`
  - `cli.cmd_web(paths, args) -> int`

**Kontext für den Umsetzenden:**

MPS verträgt keine parallele Nutzung desselben Modells. Deshalb genau **ein** Worker-Thread,
der eine `queue.Queue` abarbeitet. Alle modellnutzenden Endpunkte legen einen Job ab und
antworten sofort mit `202`; der Fortschritt kommt über SSE.

Der Server nimmt die Engine als Parameter entgegen, damit die Tests eine Fake-Engine
einsetzen können und kein Modell laden.

`clipKey` enthält einen Doppelpunkt (`prompt:a1b2c3d4e5f6`). In URL-Pfaden muss er
URL-kodiert werden (`%3A`); FastAPI dekodiert das automatisch. Der Dateiname auf der
Platte enthält den Doppelpunkt unverändert — auf macOS und Linux ist das zulässig.
Beim Ausliefern von Audio muss gegen Path-Traversal geprüft werden: nur Keys akzeptieren,
die einem bekannten Clip entsprechen.

- [ ] **Step 1: Den failing test schreiben**

`tools/tts/tests/test_server.py`:

```python
import json
import shutil
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient

from ttskit.paths import Paths
from ttskit.server import create_app


class FakeEngine:
    def __init__(self):
        self.loaded = True
        self.load_error = None
        self.device = "fake"
        self.calls = []

    def load(self):
        pass

    def validate(self, profiles):
        return []

    def generate(self, text, profile, seed):
        self.calls.append((text, seed))
        rng = np.random.default_rng(abs(hash((text, seed))) % (2 ** 32))
        return rng.standard_normal(2400).astype(np.float32) * 0.5, 24000


@pytest.fixture
def client(tmp_path, content_dir):
    root = tmp_path / "ttsroot"
    (root / "content").mkdir(parents=True)
    for f in content_dir.iterdir():
        shutil.copy(f, root / "content" / f.name)
    (root / "extra-strings.json").write_text(
        json.dumps({"version": 1, "strings": [], "templates": []}), encoding="utf-8")
    paths = Paths(root=root, content_dir=root / "content")
    app = create_app(paths, engine=FakeEngine())
    with TestClient(app) as c:
        c.paths = paths
        yield c


def wait_for_idle(client, timeout=10.0):
    import time
    deadline = time.time() + timeout
    while time.time() < deadline:
        if client.get("/api/jobs").json()["running"] is None:
            return
        time.sleep(0.02)
    raise AssertionError("job did not finish")


def test_state_lists_clips_with_status(client):
    body = client.get("/api/state").json()
    assert body["engine"]["loaded"] is True
    assert set(body["profiles"]) >= {"word", "phoneme", "prompt"}
    assert body["clips"], "expected clips from the fixture content"
    first = body["clips"][0]
    assert set(first) >= {"key", "profile", "text", "status", "seed", "locked", "itemIds"}
    assert first["status"] == "missing"


def test_updating_a_profile_persists_to_disk(client):
    response = client.put("/api/profiles/prompt", json={"instruct": "Neu und anders."})
    assert response.status_code == 200
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    assert raw["profiles"]["prompt"]["instruct"] == "Neu und anders."


def test_updating_an_unknown_profile_is_404(client):
    assert client.put("/api/profiles/nope", json={"instruct": "x"}).status_code == 404


def test_adding_and_removing_pool_seeds(client):
    assert client.post("/api/profiles/prompt/pool", json={"seed": 4242}).status_code == 200
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    assert 4242 in raw["profiles"]["prompt"]["seedPool"]

    # adding twice must not duplicate
    client.post("/api/profiles/prompt/pool", json={"seed": 4242})
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    assert raw["profiles"]["prompt"]["seedPool"].count(4242) == 1

    assert client.delete("/api/profiles/prompt/pool/4242").status_code == 200
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    assert 4242 not in raw["profiles"]["prompt"]["seedPool"]


def test_candidates_are_generated_and_listed(client):
    key = client.get("/api/state").json()["clips"][0]["key"]
    assert client.post(f"/api/clips/{key}/candidates", json={"n": 3}).status_code == 202
    wait_for_idle(client)
    seeds = client.get(f"/api/clips/{key}/candidates").json()["seeds"]
    assert len(seeds) == 3
    audio = client.get(f"/candidates/{key}/{seeds[0]}.wav")
    assert audio.status_code == 200
    assert audio.content[:4] == b"RIFF"


def test_locking_a_clip_changes_its_seed(client):
    clip = client.get("/api/state").json()["clips"][0]
    key, before = clip["key"], clip["seed"]
    assert client.post(f"/api/clips/{key}/lock", json={"seed": before + 1}).status_code == 200

    after = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert after["seed"] == before + 1
    assert after["locked"] is True

    raw = json.loads(client.paths.locks.read_text(encoding="utf-8"))
    assert raw["locks"][key]["sourceText"] == clip["text"]

    assert client.delete(f"/api/clips/{key}/lock").status_code == 200
    restored = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert restored["locked"] is False


def test_lock_with_text_override_changes_spoken_text_not_key(client):
    clip = client.get("/api/state").json()["clips"][0]
    key = clip["key"]
    client.post(f"/api/clips/{key}/lock",
                json={"seed": 5, "textOverride": "anders gesprochen"})
    after = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert after["key"] == key
    assert after["text"] == "anders gesprochen"
    assert after["sourceText"] == clip["text"]


def test_render_job_produces_audio(client):
    assert client.post("/api/render", json={"profile": "finale"}).status_code == 202
    wait_for_idle(client)
    clips = [c for c in client.get("/api/state").json()["clips"] if c["profile"] == "finale"]
    assert clips and all(c["status"] == "rendered" for c in clips)
    audio = client.get(f"/audio/{clips[0]['key']}.wav")
    assert audio.status_code == 200
    assert audio.content[:4] == b"RIFF"


def test_unknown_clip_audio_is_404(client):
    assert client.get("/audio/prompt:ffffffffffff.wav").status_code == 404


def test_audio_path_traversal_is_rejected(client):
    assert client.get("/audio/..%2F..%2Fprofiles.json.wav").status_code == 404


def test_only_one_job_runs_at_a_time(client):
    key = client.get("/api/state").json()["clips"][0]["key"]
    client.post(f"/api/clips/{key}/candidates", json={"n": 2})
    second = client.post("/api/render", json={})
    assert second.status_code in (202, 409)
    wait_for_idle(client, timeout=20)


def test_index_is_served(client):
    response = client.get("/")
    assert response.status_code == 200
    assert "<html" in response.text.lower()
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_server.py -v`
Expected: FAIL mit `ModuleNotFoundError: No module named 'ttskit.server'`

- [ ] **Step 3: `server.py` implementieren**

```python
"""FastAPI layer over the pipeline.

MPS cannot be used from several threads at once, so every model-touching
request goes through a single worker. Endpoints enqueue and return 202;
progress arrives over SSE.
"""

from __future__ import annotations

import json
import queue
import threading
from dataclasses import dataclass, field
from pathlib import Path
from typing import Any, Callable

from fastapi import Body, FastAPI, HTTPException
from fastapi.responses import FileResponse, HTMLResponse, StreamingResponse

from .cli import load_context
from .paths import Paths
from .plan import orphan_locks, status_of
from .render import candidate_seeds, random_seeds, render_clips, sample_candidates
from .store import Lock, Locks, Profiles

STATIC = Path(__file__).resolve().parent / "static"


@dataclass
class JobQueue:
    """One worker, one job at a time."""

    _queue: queue.Queue = field(default_factory=queue.Queue)
    _subscribers: list[queue.Queue] = field(default_factory=list)
    _lock: threading.Lock = field(default_factory=threading.Lock)
    running: str | None = None
    last_progress: dict[str, Any] = field(default_factory=dict)
    _cancel: threading.Event = field(default_factory=threading.Event)
    _worker: threading.Thread | None = None

    def start(self) -> None:
        self._worker = threading.Thread(target=self._run, daemon=True)
        self._worker.start()

    def submit(self, name: str, fn: Callable[[Callable[[], bool]], None]) -> None:
        self._queue.put((name, fn))

    def cancel(self) -> None:
        self._cancel.set()

    def status(self) -> dict[str, Any]:
        return {"running": self.running, "progress": self.last_progress,
                "queued": self._queue.qsize()}

    def publish(self, event: dict[str, Any]) -> None:
        self.last_progress = event
        with self._lock:
            for sub in list(self._subscribers):
                sub.put(event)

    def subscribe(self) -> queue.Queue:
        sub: queue.Queue = queue.Queue()
        with self._lock:
            self._subscribers.append(sub)
        return sub

    def unsubscribe(self, sub: queue.Queue) -> None:
        with self._lock:
            if sub in self._subscribers:
                self._subscribers.remove(sub)

    def _run(self) -> None:
        while True:
            name, fn = self._queue.get()
            self.running = name
            self._cancel.clear()
            self.publish({"type": "job-start", "job": name})
            try:
                fn(self._cancel.is_set)
                self.publish({"type": "job-done", "job": name})
            except Exception as exc:  # noqa: BLE001 - surfaced in the UI
                self.publish({"type": "job-error", "job": name,
                              "message": f"{type(exc).__name__}: {exc}"})
            finally:
                self.running = None
                self._queue.task_done()


def create_app(paths: Paths, engine=None) -> FastAPI:
    app = FastAPI(title="Qwen-TTS Pipeline")
    jobs = JobQueue()
    jobs.start()

    if engine is None:
        from .engine import Engine

        engine = Engine()
        engine.load()

    app.state.paths = paths
    app.state.engine = engine
    app.state.jobs = jobs

    def context():
        return load_context(paths)

    def clip_by_key(key: str):
        ctx = context()
        for clip in ctx.clips:
            if clip.key == key:
                return ctx, clip
        raise HTTPException(status_code=404, detail=f"unbekannter Clip {key!r}")

    @app.get("/", response_class=HTMLResponse)
    def index() -> str:
        return (STATIC / "index.html").read_text(encoding="utf-8")

    @app.get("/app.js")
    def app_js() -> FileResponse:
        return FileResponse(STATIC / "app.js", media_type="application/javascript")

    @app.get("/style.css")
    def style_css() -> FileResponse:
        return FileResponse(STATIC / "style.css", media_type="text/css")

    @app.get("/api/state")
    def api_state() -> dict[str, Any]:
        ctx = context()
        clips = []
        for clip in ctx.clips:
            profile = ctx.profiles.profiles[clip.profile]
            clips.append({
                "key": clip.key,
                "profile": clip.profile,
                "text": clip.text,
                "sourceText": clip.source_text,
                "seed": clip.seed,
                "locked": clip.locked,
                "itemIds": list(clip.item_ids),
                "fields": list(clip.fields),
                "lessons": list(clip.lessons),
                "status": status_of(clip, profile, ctx.state, paths.audio),
                "candidates": candidate_seeds(paths, clip.key),
            })
        return {
            "engine": {"loaded": bool(getattr(engine, "loaded", False)),
                       "error": getattr(engine, "load_error", None),
                       "device": getattr(engine, "device", None)},
            "profiles": {n: p.to_dict() for n, p in ctx.profiles.profiles.items()},
            "poolSalt": ctx.profiles.pool_salt,
            "clips": clips,
            "orphans": [
                {"key": k, "seed": ctx.locks.get(k).seed,
                 "sourceText": ctx.locks.get(k).source_text}
                for k in orphan_locks(ctx.locks, ctx.clips)
            ],
            "jobs": jobs.status(),
        }

    @app.put("/api/profiles/{name}")
    def api_update_profile(name: str, body: dict = Body(...)) -> dict[str, str]:
        profiles = Profiles.load(paths.profiles)
        if name not in profiles.profiles:
            raise HTTPException(status_code=404, detail=f"unbekanntes Profil {name!r}")
        profile = profiles.profiles[name]
        if "instruct" in body:
            profile.instruct = body["instruct"]
        if "sampling" in body:
            profile.sampling.update(body["sampling"])
        if "trim" in body:
            profile.trim = bool(body["trim"])
        if "normalize" in body:
            profile.normalize = bool(body["normalize"])
        profiles.save(paths.profiles)
        return {"ok": "updated"}

    @app.post("/api/profiles/{name}/pool")
    def api_add_seed(name: str, body: dict = Body(...)) -> dict[str, str]:
        profiles = Profiles.load(paths.profiles)
        if name not in profiles.profiles:
            raise HTTPException(status_code=404, detail=f"unbekanntes Profil {name!r}")
        seed = int(body["seed"])
        pool = profiles.profiles[name].seed_pool
        if seed not in pool:
            pool.append(seed)
            pool.sort()
        profiles.save(paths.profiles)
        return {"ok": "added"}

    @app.delete("/api/profiles/{name}/pool/{seed}")
    def api_remove_seed(name: str, seed: int) -> dict[str, str]:
        profiles = Profiles.load(paths.profiles)
        if name not in profiles.profiles:
            raise HTTPException(status_code=404, detail=f"unbekanntes Profil {name!r}")
        profiles.profiles[name].seed_pool = [
            s for s in profiles.profiles[name].seed_pool if s != seed
        ]
        profiles.save(paths.profiles)
        return {"ok": "removed"}

    @app.post("/api/clips/{key}/candidates", status_code=202)
    def api_candidates(key: str, body: dict = Body(default={})) -> dict[str, str]:
        ctx, clip = clip_by_key(key)
        count = int(body.get("n", 4))
        profile = ctx.profiles.profiles[clip.profile]
        existing = set(candidate_seeds(paths, clip.key)) | set(profile.seed_pool)
        seeds = random_seeds(count, exclude=existing)

        def run(is_cancelled) -> None:
            sample_candidates(clip, profile, engine, paths, seeds,
                              progress=lambda p: jobs.publish({
                                  "type": "candidate", "clipKey": clip.key,
                                  "index": p.index, "total": p.total,
                                  "status": p.status, "message": p.message}))

        jobs.submit(f"candidates:{key}", run)
        return {"ok": "queued"}

    @app.get("/api/clips/{key}/candidates")
    def api_list_candidates(key: str) -> dict[str, list[int]]:
        return {"seeds": candidate_seeds(paths, key)}

    @app.post("/api/clips/{key}/lock")
    def api_lock(key: str, body: dict = Body(...)) -> dict[str, str]:
        _, clip = clip_by_key(key)
        locks = Locks.load(paths.locks)
        locks.set(key, Lock(
            seed=int(body["seed"]),
            profile=body.get("profile"),
            text_override=body.get("textOverride"),
            note=body.get("note"),
            source_text=clip.source_text,
        ))
        locks.save(paths.locks)
        return {"ok": "locked"}

    @app.delete("/api/clips/{key}/lock")
    def api_unlock(key: str) -> dict[str, str]:
        locks = Locks.load(paths.locks)
        locks.remove(key)
        locks.save(paths.locks)
        return {"ok": "unlocked"}

    @app.post("/api/render", status_code=202)
    def api_render(body: dict = Body(default={})) -> dict[str, str]:
        profile = body.get("profile") or None
        force = bool(body.get("force"))

        def run(is_cancelled) -> None:
            ctx = context()
            render_clips(ctx.clips, ctx.profiles, engine, ctx.state, paths,
                         profile=profile, force=force, cancel=is_cancelled,
                         progress=lambda p: jobs.publish({
                             "type": "render", "clipKey": p.clip_key,
                             "index": p.index, "total": p.total,
                             "status": p.status, "message": p.message}))

        jobs.submit(f"render:{profile or 'alle'}", run)
        return {"ok": "queued"}

    @app.post("/api/jobs/cancel")
    def api_cancel() -> dict[str, str]:
        jobs.cancel()
        return {"ok": "cancelling"}

    @app.get("/api/jobs")
    def api_jobs() -> dict[str, Any]:
        return jobs.status()

    @app.get("/audio/{key}.wav")
    def api_audio(key: str) -> FileResponse:
        # Only serve keys that belong to a known clip — no path traversal.
        clip_by_key(key)
        path = paths.audio / f"{key}.wav"
        if not path.exists():
            raise HTTPException(status_code=404, detail="noch nicht gerendert")
        return FileResponse(path, media_type="audio/wav")

    @app.get("/candidates/{key}/{seed}.wav")
    def api_candidate_audio(key: str, seed: int) -> FileResponse:
        clip_by_key(key)
        path = paths.candidates / key / f"{seed}.wav"
        if not path.exists():
            raise HTTPException(status_code=404, detail="kein Kandidat")
        return FileResponse(path, media_type="audio/wav")

    @app.get("/events")
    def api_events() -> StreamingResponse:
        def stream():
            sub = jobs.subscribe()
            try:
                yield f"data: {json.dumps(jobs.status())}\n\n"
                while True:
                    event = sub.get()
                    yield f"data: {json.dumps(event)}\n\n"
            finally:
                jobs.unsubscribe(sub)

        return StreamingResponse(stream(), media_type="text/event-stream")

    return app
```

- [ ] **Step 4: `cli.py` um `web` erweitern**

In `build_parser()`:

```python
    web_parser = sub.add_parser("web", help="Web-Interface starten")
    web_parser.add_argument("--port", type=int, default=8420)
    web_parser.add_argument("--host", default="127.0.0.1")
```

In `main()`:

```python
    if args.command == "web":
        return cmd_web(paths, args)
```

Und das Kommando:

```python
def cmd_web(paths: Paths, args) -> int:
    import uvicorn

    from .server import create_app

    print(f"Lade Modell — das dauert ein paar Sekunden ...")
    app = create_app(paths)
    print(f"Web-Interface auf http://{args.host}:{args.port}")
    uvicorn.run(app, host=args.host, port=args.port, log_level="warning")
    return 0
```

- [ ] **Step 5: Minimales `index.html` anlegen, damit Task 8 testbar ist**

`tools/tts/ttskit/static/index.html` — wird in Task 9 ersetzt:

```html
<!doctype html>
<html lang="de">
  <head><meta charset="utf-8" /><title>Qwen-TTS Pipeline</title></head>
  <body><p>UI folgt in Task 9.</p></body>
</html>
```

- [ ] **Step 6: Tests laufen lassen und Erfolg bestätigen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_server.py -v`
Expected: PASS, 12 Tests

- [ ] **Step 7: Commit**

```bash
git add tools/tts/
git commit -m "feat(tts): FastAPI-Server mit Single-Worker-Job-Queue und SSE"
```

---

## Task 9: Web-Oberfläche

**Files:**
- Modify: `tools/tts/ttskit/static/index.html`
- Create: `tools/tts/ttskit/static/app.js`, `tools/tts/ttskit/static/style.css`

**Interfaces:**
- Consumes: die Endpunkte aus Task 8
- Produces: keine Python-Schnittstelle

**Kontext für den Umsetzenden:**

Kein Build-Schritt, kein Framework, keine externen Requests — die Datei wird direkt vom
Server ausgeliefert. Der Kern ist die Review-Schleife: Clip wählen, hören, Kandidaten
würfeln, den besten in den Pool oder als Lock. Tastatur-Shortcuts sind kein Luxus, sondern
der Unterschied zwischen einer Stunde und drei Stunden Kuratieren.

Die Liste hat bis zu ~694 Zeilen. Kein Virtual Scrolling nötig, aber die Liste wird bei
Filteränderung komplett neu gebaut, nicht Zeile für Zeile mutiert.

- [ ] **Step 1: `style.css` schreiben**

```css
:root {
  --bg: #fdfaf5; --panel: #fff; --line: #e6ded2; --text: #2f2a24;
  --muted: #857a6c; --accent: #c4622d; --ok: #4f7a3f; --warn: #b8862b;
  --stale: #9a5b8f;
}
* { box-sizing: border-box; }
body {
  margin: 0; font: 15px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  background: var(--bg); color: var(--text); height: 100vh; display: flex;
  flex-direction: column;
}
header {
  display: flex; gap: 12px; align-items: center; padding: 10px 16px;
  border-bottom: 1px solid var(--line); background: var(--panel); flex-wrap: wrap;
}
header h1 { font-size: 16px; margin: 0 12px 0 0; }
main { flex: 1; display: grid; grid-template-columns: minmax(320px, 1fr) 1.4fr; min-height: 0; }
#list { overflow-y: auto; border-right: 1px solid var(--line); }
#detail { overflow-y: auto; padding: 20px; }
.row {
  padding: 8px 14px; border-bottom: 1px solid var(--line); cursor: pointer;
  display: flex; gap: 10px; align-items: baseline;
}
.row:hover { background: #f6f0e7; }
.row.active { background: #f0e5d6; box-shadow: inset 3px 0 0 var(--accent); }
.row .text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chip {
  font-size: 11px; padding: 1px 7px; border-radius: 10px; border: 1px solid var(--line);
  color: var(--muted); white-space: nowrap;
}
.chip.missing { color: var(--muted); }
.chip.rendered { color: var(--ok); border-color: var(--ok); }
.chip.stale { color: var(--stale); border-color: var(--stale); }
.chip.locked { color: var(--accent); border-color: var(--accent); }
.card {
  background: var(--panel); border: 1px solid var(--line); border-radius: 8px;
  padding: 14px; margin-bottom: 14px;
}
.candidates { display: flex; gap: 12px; flex-wrap: wrap; }
.candidate { border: 1px solid var(--line); border-radius: 8px; padding: 10px; min-width: 190px; }
.candidate h4 { margin: 0 0 6px; font-size: 13px; font-family: ui-monospace, monospace; }
.candidate audio { width: 100%; }
button {
  font: inherit; padding: 5px 11px; border-radius: 6px; border: 1px solid var(--line);
  background: var(--panel); cursor: pointer;
}
button:hover { background: #f6f0e7; }
button.primary { background: var(--accent); color: #fff; border-color: var(--accent); }
input, select, textarea {
  font: inherit; padding: 5px 8px; border: 1px solid var(--line); border-radius: 6px;
  background: var(--panel); color: inherit;
}
textarea { width: 100%; min-height: 90px; resize: vertical; }
.muted { color: var(--muted); font-size: 13px; }
.mono { font-family: ui-monospace, SFMono-Regular, monospace; font-size: 12px; }
#progress { flex: 1; text-align: right; color: var(--muted); font-size: 13px; }
.warn { color: var(--warn); }
```

- [ ] **Step 2: `index.html` schreiben**

```html
<!doctype html>
<html lang="de">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Qwen-TTS Pipeline</title>
    <link rel="stylesheet" href="/style.css" />
  </head>
  <body>
    <header>
      <h1>Qwen-TTS</h1>
      <input id="search" type="search" placeholder="Text suchen …" />
      <select id="filter-profile"><option value="">Alle Profile</option></select>
      <select id="filter-status">
        <option value="">Alle Status</option>
        <option value="missing">fehlt</option>
        <option value="stale">stale</option>
        <option value="rendered">fertig</option>
        <option value="locked">gelockt</option>
      </select>
      <button id="btn-render" class="primary">Finalen Lauf starten</button>
      <button id="btn-cancel">Abbrechen</button>
      <span id="progress"></span>
    </header>
    <main>
      <div id="list"></div>
      <div id="detail"><p class="muted">Links einen Clip wählen. Tasten: j/k blättern, Leertaste spielt, 1–4 spielen Kandidaten.</p></div>
    </main>
    <script src="/app.js"></script>
  </body>
</html>
```

- [ ] **Step 3: `app.js` schreiben**

```javascript
"use strict";

const state = { clips: [], profiles: {}, engine: {}, orphans: [], selected: null };
const el = (id) => document.getElementById(id);

async function api(path, options) {
  const response = await fetch(path, options);
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`${response.status}: ${detail}`);
  }
  return response.status === 204 ? null : response.json();
}

const post = (path, body) =>
  api(path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body || {}),
  });

const put = (path, body) =>
  api(path, {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });

async function refresh() {
  const data = await api("/api/state");
  Object.assign(state, data);

  const select = el("filter-profile");
  if (select.options.length <= 1) {
    Object.keys(state.profiles).sort().forEach((name) => {
      const option = document.createElement("option");
      option.value = name;
      option.textContent = `${name} — ${state.profiles[name].label}`;
      select.appendChild(option);
    });
  }
  if (!state.engine.loaded) {
    el("progress").innerHTML =
      `<span class="warn">Engine offline: ${state.engine.error || "unbekannt"}</span>`;
  }
  renderList();
  if (state.selected) renderDetail(state.selected);
}

function visibleClips() {
  const needle = el("search").value.toLowerCase();
  const profile = el("filter-profile").value;
  const status = el("filter-status").value;
  return state.clips.filter((clip) => {
    if (profile && clip.profile !== profile) return false;
    if (status === "locked" ? !clip.locked : status && clip.status !== status) return false;
    if (needle && !clip.text.toLowerCase().includes(needle)) return false;
    return true;
  });
}

function renderList() {
  const list = el("list");
  const clips = visibleClips();
  list.innerHTML = "";
  clips.forEach((clip) => {
    const row = document.createElement("div");
    row.className = "row" + (clip.key === state.selected ? " active" : "");
    row.dataset.key = clip.key;
    row.innerHTML = `
      <span class="chip">${clip.profile}</span>
      <span class="text">${escapeHtml(clip.text)}</span>
      ${clip.locked ? '<span class="chip locked">📌</span>' : ""}
      <span class="chip ${clip.status}">${clip.status}</span>`;
    row.onclick = () => select(clip.key);
    list.appendChild(row);
  });
  document.title = `Qwen-TTS (${clips.length})`;
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

function select(key) {
  state.selected = key;
  renderList();
  renderDetail(key);
  const active = document.querySelector(".row.active");
  if (active) active.scrollIntoView({ block: "nearest" });
}

function renderDetail(key) {
  const clip = state.clips.find((c) => c.key === key);
  if (!clip) return;
  const profile = state.profiles[clip.profile];
  const encoded = encodeURIComponent(clip.key);

  el("detail").innerHTML = `
    <div class="card">
      <div class="mono muted">${clip.key}</div>
      <h2 style="margin:6px 0">${escapeHtml(clip.text)}</h2>
      ${clip.text !== clip.sourceText
        ? `<p class="muted">Original: ${escapeHtml(clip.sourceText)}</p>` : ""}
      <p class="muted">${clip.itemIds.length} Stelle(n) · Felder: ${clip.fields.join(", ")}
        ${clip.lessons.length ? " · Lektionen: " + clip.lessons.join(", ") : ""}</p>
      <p>Profil:
        <select id="clip-profile">
          ${Object.keys(state.profiles).sort().map((n) =>
            `<option value="${n}" ${n === clip.profile ? "selected" : ""}>${n}</option>`).join("")}
        </select>
        · Seed <span class="mono">${clip.seed}</span>
        ${clip.locked ? '<span class="chip locked">gelockt</span>' : ""}
      </p>
      ${clip.status === "rendered"
        ? `<audio controls src="/audio/${encoded}.wav" id="main-audio"></audio>`
        : '<p class="muted">Noch nicht gerendert.</p>'}
      <p>
        <button id="btn-candidates" class="primary">🎲 4 Kandidaten</button>
        ${clip.locked ? '<button id="btn-unlock">Lock entfernen</button>' : ""}
      </p>
    </div>

    <div class="card">
      <h3 style="margin-top:0">Kandidaten</h3>
      <div class="candidates">
        ${clip.candidates.length === 0 ? '<p class="muted">Noch keine.</p>' : ""}
        ${clip.candidates.map((seed, index) => `
          <div class="candidate">
            <h4>${index < 9 ? `[${index + 1}] ` : ""}Seed ${seed}</h4>
            <audio controls src="/candidates/${encoded}/${seed}.wav"
                   data-index="${index}"></audio>
            <p>
              <button data-pool="${seed}">✓ Pool</button>
              <button data-lock="${seed}">📌 Lock</button>
            </p>
          </div>`).join("")}
      </div>
    </div>

    <div class="card">
      <h3 style="margin-top:0">Profil „${clip.profile}" — ${profile.label}</h3>
      <textarea id="profile-instruct">${escapeHtml(profile.instruct)}</textarea>
      <p>
        <button id="btn-save-profile" class="primary">Instruktion speichern</button>
        <span class="muted">Speichern macht alle Clips dieses Profils stale.</span>
      </p>
      <p class="muted">Seed-Pool:
        ${profile.seedPool.length === 0 ? "leer" : profile.seedPool.map((seed) =>
          `<span class="chip">${seed}
            <a href="#" data-unpool="${seed}" title="entfernen">×</a></span>`).join(" ")}
      </p>
    </div>`;

  el("btn-candidates").onclick = async () => {
    await post(`/api/clips/${encoded}/candidates`, { n: 4 });
  };
  const unlock = el("btn-unlock");
  if (unlock) {
    unlock.onclick = async () => {
      await api(`/api/clips/${encoded}/lock`, { method: "DELETE" });
      await refresh();
    };
  }
  el("clip-profile").onchange = async (event) => {
    await post(`/api/clips/${encoded}/lock`,
               { seed: clip.seed, profile: event.target.value });
    await refresh();
  };
  el("btn-save-profile").onclick = async () => {
    await put(`/api/profiles/${clip.profile}`,
              { instruct: el("profile-instruct").value });
    await refresh();
  };
  el("detail").querySelectorAll("[data-pool]").forEach((button) => {
    button.onclick = async () => {
      await post(`/api/profiles/${clip.profile}/pool`,
                 { seed: Number(button.dataset.pool) });
      await refresh();
    };
  });
  el("detail").querySelectorAll("[data-lock]").forEach((button) => {
    button.onclick = async () => {
      await post(`/api/clips/${encoded}/lock`, { seed: Number(button.dataset.lock) });
      await refresh();
    };
  });
  el("detail").querySelectorAll("[data-unpool]").forEach((link) => {
    link.onclick = async (event) => {
      event.preventDefault();
      await api(`/api/profiles/${clip.profile}/pool/${link.dataset.unpool}`,
                { method: "DELETE" });
      await refresh();
    };
  });
}

function playCandidate(index) {
  const audio = el("detail").querySelector(`audio[data-index="${index}"]`);
  if (audio) {
    audio.currentTime = 0;
    audio.play();
  }
}

document.addEventListener("keydown", (event) => {
  if (["INPUT", "TEXTAREA", "SELECT"].includes(event.target.tagName)) return;
  const clips = visibleClips();
  const current = clips.findIndex((c) => c.key === state.selected);

  if (event.key === "j" && current < clips.length - 1) {
    select(clips[current + 1].key);
  } else if (event.key === "k" && current > 0) {
    select(clips[current - 1].key);
  } else if (event.key === " ") {
    event.preventDefault();
    const audio = el("main-audio");
    if (audio) {
      audio.currentTime = 0;
      audio.play();
    }
  } else if (/^[1-9]$/.test(event.key)) {
    playCandidate(Number(event.key) - 1);
  }
});

["search", "filter-profile", "filter-status"].forEach((id) => {
  el(id).addEventListener("input", renderList);
});

el("btn-render").onclick = async () => {
  const profile = el("filter-profile").value || null;
  const label = profile ? `Profil „${profile}"` : "alle Profile";
  if (!confirm(`Finalen Lauf für ${label} starten?`)) return;
  await post("/api/render", { profile });
};

el("btn-cancel").onclick = () => post("/api/jobs/cancel", {});

const events = new EventSource("/events");
events.onmessage = (message) => {
  const event = JSON.parse(message.data);
  if (event.type === "render" || event.type === "candidate") {
    el("progress").textContent =
      `${event.index}/${event.total} · ${event.status} ${event.message || ""}`;
  } else if (event.type === "job-done") {
    el("progress").textContent = "fertig";
    refresh();
  } else if (event.type === "job-error") {
    el("progress").innerHTML = `<span class="warn">${escapeHtml(event.message)}</span>`;
    refresh();
  } else if (event.type === "job-start") {
    el("progress").textContent = `läuft: ${event.job}`;
  }
};

refresh();
```

- [ ] **Step 4: Bestehende Tests laufen lassen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -v`
Expected: PASS, alles grün (Engine-Smoke skipped). `test_index_is_served` prüft jetzt das echte HTML.

- [ ] **Step 5: Die UI von Hand prüfen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python ./tts web`

Im Browser `http://127.0.0.1:8420` öffnen und durchgehen:
1. Liste zeigt ~694 Clips, Filter nach Profil reduziert korrekt
2. `j`/`k` blättern, die aktive Zeile scrollt mit
3. Bei einem `finale`-Clip (in Task 7 gerendert) spielt die Leertaste das Audio
4. „🎲 4 Kandidaten" erzeugt vier Karten; `1`–`4` spielen sie ab
5. „✓ Pool" fügt den Seed hinzu — der Chip erscheint im Profil-Block
6. „📌 Lock" setzt den Seed; der Clip zeigt „gelockt" und wird `stale`
7. Instruktion ändern und speichern → alle Clips des Profils werden `stale`
8. „Finalen Lauf starten" bei gesetztem Profilfilter rendert nur dieses Profil, der Fortschritt läuft rechts oben

- [ ] **Step 6: Commit**

```bash
git add tools/tts/
git commit -m "feat(tts): Web-Oberfläche für Kuratierung, Kandidaten und Batch-Läufe"
```

---

## Task 10: Dokumentation

**Files:**
- Create: `tools/tts/README.md`
- Create: `docs/residual-review-findings/2026-08-02-tts-tooling.md`
- Modify: `AGENTS.md` — Abschnitt „Technik-Kurzüberblick"

**Kontext für den Umsetzenden:**

`AGENTS.md` verlangt, dass Doku bei neuen Konventionen mitgezogen wird. Das Tooling ist
eine neue Konvention: künftige Sessions müssen wissen, dass es existiert, dass es einen
fremden Interpreter benutzt und dass `profiles.json`/`locks.json` kuratierte Entscheidungen
enthalten, die nicht überschrieben werden dürfen.

- [ ] **Step 1: `tools/tts/README.md` schreiben**

````markdown
# Qwen-TTS Audio-Pipeline

Erzeugt aus dem Content-Pack der App Sprachaufnahmen mit lokalem Qwen3-TTS.
**Die App wird davon nicht berührt** — hier entsteht nur ein Audio-Paket unter `out/`.

Design: `docs/superpowers/specs/2026-08-02-qwen-tts-audio-pipeline-design.md`

## Voraussetzung

Alles läuft mit dem Interpreter aus dem Qwen-venv:

```bash
alias tts="~/qwen-tts-test/.venv/bin/python $(git rev-parse --show-toplevel)/tools/tts/tts"
```

## Ablauf

```bash
tts extract                        # Content-JSON → out/manifest.json
tts status                         # Überblick: fehlt / stale / fertig / Pools / Locks
tts sample --profile prompt -n 8   # 8 Seeds an 3 Beispielen des Profils ausprobieren
tts web                            # Kuratieren unter http://127.0.0.1:8420
tts render                         # finaler Lauf, inkrementell
```

Typisch: einmal `sample` pro Profil, im Web-Interface die guten Seeds mit „✓ Pool"
sammeln, dann `render`. Einzelne schlechte Clips im Web-Interface mit „🎲 4 Kandidaten"
neu würfeln und den besten per „📌 Lock" festnageln.

## Seeds

Die `qwen_tts`-API kennt keinen Seed-Parameter; Reproduzierbarkeit entsteht über
`torch.manual_seed()` unmittelbar vor der Generierung, bei Batchgröße 1. Empirisch
bestätigt: gleicher Seed → bit-identisches Audio (`tests/test_engine.py`, `TTS_SMOKE=1`).

Der Seed eines Clips wird so bestimmt:

```
seed = locks[clipKey].seed
       ?? seedPool[ sha256(clipKey + poolSalt) % len(seedPool) ]
       ?? sha256(clipKey + poolSalt) % 2**31        # Pool leer
```

Damit streuen die Clips über die kuratierten Seeds, bleiben aber über Läufe hinweg
identisch. `poolSalt` in `profiles.json` hochzählen würfelt bewusst alles neu.

## Dateien

| Datei | Im Git? | Inhalt |
| --- | --- | --- |
| `profiles.json` | ja | Instruktionen, Sampling, Seed-Pools — **kuratierte Entscheidungen** |
| `locks.json` | ja | pro Clip festgenagelte Seeds — **kuratierte Entscheidungen** |
| `extra-strings.json` | ja | hartkodierte Kotlin-Strings |
| `out/` | nein | Manifest, Render-State, Audio, Kandidaten — jederzeit neu erzeugbar |

`profiles.json` und `locks.json` nie automatisiert überschreiben: darin steckt Hörarbeit.

## Tests

```bash
cd tools/tts
~/qwen-tts-test/.venv/bin/python -m pytest tests/ -v            # ohne Modell
TTS_SMOKE=1 ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -v # mit Modell
```

## Bekannte Lücke

Die Sprechtexte von Symbol-Jagd und Wort-Detektiv sind Templates
(`"Finde den Buchstaben - %s - im Wort - %s."`), die erst zur Laufzeit befüllt werden.
Sie sind nicht abgedeckt; `tts status` weist darauf hin. Geplant ist, die Kombinationen
vollständig zu rendern, sobald die App-Integration steht — die App fällt für fehlende
Clips auf System-TTS zurück, die Abdeckung muss also nicht vollständig sein.
````

- [ ] **Step 2: Residual-Finding notieren**

`docs/residual-review-findings/2026-08-02-tts-tooling.md`:

```markdown
# Offene Reste — TTS-Tooling (2026-08-02)

## `TtsDebugEntry.kt` kennt `finales.json` nicht

`app/src/main/java/app/abcvorschule/debug/TtsDebugEntry.kt` enumeriert Atome, Sätze und
Tasks, aber nicht die Finale-Sätze aus `finales.json`. Die TTS-Debug-Seite in der App
kann diese 18 Strings daher nicht anzeigen oder überschreiben.

Das Tooling unter `tools/tts/` definiert das Präfix `finale:<id>:tts` bereits. Wird die
Kotlin-Seite nachgezogen, muss sie exakt dieses Schema benutzen, damit Tooling und App
dieselben IDs sprechen.

Bewusst nicht in dieser Session behoben: der Scope war ausdrücklich tooling-only, ohne
App-Änderungen.

## Template-Sprechtexte der abgeleiteten Trainer

Siehe `docs/superpowers/specs/2026-08-02-qwen-tts-audio-pipeline-design.md` §2.
`SymbolHuntDerivation` und `SymbolInWordDerivation` bauen ihre Ansagen zur Laufzeit aus
Format-Templates. Das Tooling erfasst sie nicht; `extra-strings.json` hält mit einem
leeren `templates`-Block den Platz frei.
```

- [ ] **Step 3: `AGENTS.md` ergänzen**

Im Abschnitt „Technik-Kurzüberblick" nach der Zeile zu den Tests einfügen:

```markdown
- Sprachaufnahmen: `tools/tts/` erzeugt mit lokalem Qwen3-TTS ein Audio-Paket aus dem
  Content-Pack (`tools/tts/README.md`). Läuft mit `~/qwen-tts-test/.venv/bin/python`,
  nicht mit dem System-Python. `tools/tts/profiles.json` und `tools/tts/locks.json`
  enthalten kuratierte Entscheidungen — nie automatisiert überschreiben.
```

- [ ] **Step 4: Prüfen, dass die Doku stimmt**

Run:
```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python ./tts --help \
  && ~/qwen-tts-test/.venv/bin/python ./tts status \
  && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q
```
Expected: Die Subkommandos in der Hilfe stimmen mit dem README überein, `status` läuft, alle Tests grün.

- [ ] **Step 5: Commit**

```bash
git add tools/tts/README.md docs/residual-review-findings/ AGENTS.md
git commit -m "docs(tts): README, Residual-Findings und AGENTS-Eintrag zum TTS-Tooling"
```

---

## Abschluss

Nach Task 10 steht:

```bash
cd tools/tts
~/qwen-tts-test/.venv/bin/python ./tts extract
~/qwen-tts-test/.venv/bin/python ./tts sample --profile phoneme -n 8
~/qwen-tts-test/.venv/bin/python ./tts web       # kuratieren
~/qwen-tts-test/.venv/bin/python ./tts render    # ~28 Minuten beim ersten Mal
```

Das `phoneme`-Profil zuerst zu sampeln ist Absicht: es ist mit 37 Clips das kleinste und
zugleich das riskanteste (Lautwert vs. Buchstabenname). Klappt es dort nicht per
Instruktion, greift der `textOverride` im Lock — und das weiß man dann früh, nicht nach
28 Minuten Rendern.
