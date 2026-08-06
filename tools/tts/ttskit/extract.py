"""Turn the app content pack into a flat list of speakable items."""

from __future__ import annotations

from pathlib import Path

from .models import Item
from .store import read_json

#: Logical field → default profile. Lemma is special: letter/syllable atoms use
#: the phoneme profile (Lautwert); see profile_for_item().
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

_PHONEME_LEMMA_KINDS = frozenset({"letter", "syllable"})


def profile_for_item(item: Item) -> str:
    """Map an extracted item to its synthesis profile."""
    if item.field == "lemma" and item.atom_kind in _PHONEME_LEMMA_KINDS:
        return "phoneme"
    return FIELD_TO_PROFILE[item.field]


def _load(content_dir: Path, name: str, key: str) -> list[dict]:
    """Read one content file, naming it in every failure mode.

    A raw FileNotFoundError or KeyError('atoms') here tells the operator
    nothing about which of the five files is at fault.
    """
    path = content_dir / name
    raw = read_json(path)
    if raw is None:
        raise ValueError(f"{path} does not exist — is the content pack path right?")
    if not isinstance(raw, dict):
        raise ValueError(f"{path} must contain an object, got {type(raw).__name__}")
    if key not in raw:
        raise ValueError(f"{path} has no {key!r} key")
    entries = raw[key]
    if not isinstance(entries, list):
        raise ValueError(f"{path}: {key!r} must be a list, got {type(entries).__name__}")
    return entries


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


def extract_items(content_dir: Path, extra_strings: dict | None = None,
                  blanks: list[str] | None = None) -> list[Item]:
    """Collect every authored TTS string from the content pack.

    Blank strings are skipped — they would produce empty audio. Pass a list as
    `blanks` to collect the ids that were skipped, so `status` can report them
    instead of silently swallowing an authoring mistake.
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
            lesson: str | None, label: str,
            atom_kind: str | None = None) -> None:
        if not text or not text.strip():
            if blanks is not None:
                blanks.append(item_id)
            return
        items.append(Item(id=item_id, text=text, field=field, source=source,
                          lesson=lesson, label=label, atom_kind=atom_kind))

    for atom in sorted(atoms, key=lambda a: a["id"]):
        add(f"atom:{atom['id']}:lemma", atom.get("lemma", ""), "lemma",
            "atoms.json", None, f"{atom.get('display', atom['id'])} ({atom.get('kind', '?')})",
            atom_kind=atom.get("kind"))

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
            field = entry.get("field", "uiText")
            add(f"ui:{entry['id']}", entry.get("text", ""), field,
                "extra-strings.json", None, entry.get("note") or entry["id"])

    return items
