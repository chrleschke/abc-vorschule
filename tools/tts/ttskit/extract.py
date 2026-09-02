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
    "instructionTts": "prompt",
    "missTts": "miss",
    "rewardTts": "reward",
    "sentenceTts": "sentence",
    "finaleTts": "finale",
    "uiText": "ui",
    "spokenAnswerTts": "word",
    "articleTts": "article_word",
}

# Order matters: it decides the order of items within a round.
# sentenceTts is the Satz-Versteher's round text: an assertion the child has to
# understand, so it takes the `sentence` profile rather than the fragende
# Betonung of `prompt`. Its task-level instructionTts stays a prompt.
ROUND_FIELDS = ("promptTts", "sentenceTts", "missTts", "rewardTts", "stretchTts")

_PHONEME_LEMMA_KINDS = frozenset({"letter", "syllable"})

#: Felder, die *normalerweise* eine Aufgabenansage tragen — aber nicht immer,
#: siehe `reads_as_bare_sentence`.
_PROMPT_FIELDS = frozenset({"promptTts", "instructionTts"})

#: Textstücke, die eine Ansage an das Kind markieren, auch wenn der String wie
#: ein Satz auf einen Punkt endet. Einzige Wahrheit für `reads_as_bare_sentence`
#: und für die Kollisionsauflösung im Export.
#: Die Rechenaufgabe. Sie ist die einzige Aufgabe der Fibel, die *fragt* — und
#: sie fragt nicht nur: „Sechs Ameisen krabbeln im Bau. Drei krabbeln hinaus.
#: Wie viele Ameisen bleiben." trägt Erzählung und Frage in einem String. Beides
#: will eine andere Melodie als eine reine Aufforderung („Baue das Wort Mama."),
#: also bekommt es ein eigenes Profil mit eigenem Seed-Pool.
#:
#: Am Satzende ist die Frage nicht zu erkennen: die Fibel schreibt sie mit Punkt
#: (PRODUCT_PRINCIPLES, „Letzte Frage ohne Fragezeichen" — ein „?" am Stringende
#: lässt die System-TTS die Stimme hochziehen, was bei Vorschulkindern unruhig
#: klingt). An ihrem Anfang schon.
MATH_MARKER = "Wie viele"

#: Woran eine Aufgabenansage erkennbar ist. Alles Imperative — bis auf
#: [MATH_MARKER], der sein eigenes Profil hat und hier nur mitläuft, damit eine
#: Rechenaufgabe niemals als erzählender Satz durchgeht.
INSTRUCTION_MARKERS = (
    "Baue das Wort", "Ordne ", "Finde den", "Finde alle", "Finde die",
    "Schiebe ", "Zeichne den", MATH_MARKER,
)


def reads_as_bare_sentence(text: str) -> bool:
    """Ist dieser Text ein Satz für das Kind statt einer Aufgabenansage?

    Der Satz-Architekt stellt seinen Satz als `promptTts` in die Runde — derselbe
    Wortlaut steht ein zweites Mal als `tts` am Satz in sentences.json. Ohne
    diese Unterscheidung landete „Tom singt." einmal im Profil `prompt` und
    einmal in `sentence`: zwei Renders, zwei Kuratierungen, und in der App kann
    nur einer gewinnen (`export._collision_winner` nimmt den Satz). Der Prompt
    einer Satzrunde *ist* der Satz, also gehört ihm auch dessen Betonung — die
    fragende Prompt-Melodie wäre dort ohnehin falsch.

    Ein **Fragezeichen zählt nicht als Satzende**. Eine Frage ist kein
    erzählender Satz; sie im Satz-Profil aufzunehmen wäre derselbe Fehler wie
    „Tom singt." im Prompt-Profil, nur umgekehrt. Bis September 2026 stand „?"
    hier mit in der Liste und schob jede Frage, die keinen Marker trug, ins
    Satz-Profil. Getroffen hat es nie eine echte Aufnahme — die Hausregel oben
    verbietet „?" am Stringende, im Pack endet kein Text darauf —, aber sechs
    Tests fielen darüber, weil das Testpaket noch „Hörst du M?" trug.
    """
    stripped = text.strip()
    if any(marker in stripped for marker in INSTRUCTION_MARKERS):
        return False
    return stripped.endswith((".", "!"))


def reads_as_math_task(text: str) -> bool:
    """Trägt dieser Text eine Rechenaufgabe? Siehe [MATH_MARKER]."""
    return MATH_MARKER in text


def profile_for_item(item: Item) -> str:
    """Map an extracted item to its synthesis profile."""
    if item.field == "lemma" and item.atom_kind in _PHONEME_LEMMA_KINDS:
        return "phoneme"
    if item.field in _PROMPT_FIELDS:
        # Reihenfolge: die Rechenaufgabe zuerst. Sie endet auf einen Punkt und
        # wäre ohne ihren Marker ein „erzählender Satz" — die Frage am Ende
        # bekäme dann Aussage-Melodie.
        if reads_as_math_task(item.text):
            return "math"
        if reads_as_bare_sentence(item.text):
            return "sentence"
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


def _spoken_answer(answer: int, icon: dict) -> str:
    """Mirror CountAddRound.spokenAnswer in TaskSpecs.kt."""
    if not icon:
        return str(answer)
    display = icon.get("display") or ""
    if answer == 1:
        noun = display
    else:
        noun = icon.get("pluralDisplay") or display
    return f"{answer} {noun}".strip()


_DEFINITE = {"m": "der", "f": "die", "n": "das"}
_INDEFINITE = {"m": "ein", "f": "eine"}


def article_speech(atom: dict) -> str | None:
    """Mirror AtomArticleSpeech.forAtom in AtomArticleSpeech.kt.

    Personen bekommen den unbestimmten Artikel — außer im Neutrum, wo nur "das"
    das Genus eindeutig trägt ("ein Opa" und "ein Kind" klingen sonst gleich).
    """
    override = atom.get("articleSpeechOverride") or ""
    if override.strip():
        return override
    noun_class = atom.get("nounClass")
    if not noun_class:
        return None
    display = atom.get("display") or ""
    if not display.strip():
        return None
    if noun_class == "name":
        return display
    gender = atom.get("gender")
    if not gender:
        return None
    if noun_class == "person" and gender in _INDEFINITE:
        return f"{_INDEFINITE[gender]} {display}"
    return f"{_DEFINITE[gender]} {display}"


def _speech_reachable_atom_ids(tasks: list[dict]) -> set[str]:
    """Atome, die SuccessSpeech je mit Artikel spricht.

    Spiegelt SuccessSpeech.partsForRound: der Wort-Bauer. Der Wort-Detektiv
    leitet sich zur Laufzeit aus den word_build-Wörtern ab
    (SymbolInWordDerivation) und ist damit enthalten.
    """
    reachable: set[str] = set()
    for task in tasks:
        trainer = task.get("trainer")
        for round_ in task.get("rounds", []):
            if trainer == "word_build" and round_.get("targetAtomId"):
                reachable.add(round_["targetAtomId"])
    return reachable


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
    atom_by_id = {atom["id"]: atom for atom in atoms}
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

    reachable = _speech_reachable_atom_ids(tasks)

    for atom in sorted(atoms, key=lambda a: a["id"]):
        add(f"atom:{atom['id']}:lemma", atom.get("lemma", ""), "lemma",
            "atoms.json", None, f"{atom.get('display', atom['id'])} ({atom.get('kind', '?')})",
            atom_kind=atom.get("kind"))

        if atom["id"] not in reachable:
            continue
        speech = article_speech(atom)
        # Gleicher Text wie display (Namen) → kein zweiter Clip.
        if not speech or speech == atom.get("display"):
            continue
        add(f"atom:{atom['id']}:articleTts", speech, "articleTts", "atoms.json", None,
            f"{atom.get('display', atom['id'])} (Artikel)")

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
        if "instructionTts" in task:
            add(f"task:{task_id}:instructionTts", task["instructionTts"], "instructionTts",
                "tasks.json", lesson, f"{task_id} · instructionTts")
        for index, round_ in enumerate(task.get("rounds", [])):
            for field in ROUND_FIELDS:
                if field not in round_:
                    continue
                add(f"task:{task_id}:round:{index}:{field}", round_[field], field,
                    "tasks.json", lesson, f"{task_id} · Runde {index + 1} · {field}")
            if task.get("trainer") == "count_add":
                icon_id = round_.get("iconAtomId")
                icon = atom_by_id.get(icon_id, {})
                spoken = _spoken_answer(round_.get("answer", 0), icon)
                add(f"task:{task_id}:round:{index}:spokenAnswer", spoken,
                    "spokenAnswerTts", "tasks.json", lesson,
                    f"{task_id} · Runde {index + 1} · spokenAnswer")

    if extra_strings:
        for entry in extra_strings.get("strings", []):
            field = entry.get("field", "uiText")
            add(f"ui:{entry['id']}", entry.get("text", ""), field,
                "extra-strings.json", None, entry.get("note") or entry["id"])

    return items
