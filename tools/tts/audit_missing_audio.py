#!/usr/bin/env python3
"""One-off audit: which speakable strings lack OGG clips in the shipped app pack."""

from __future__ import annotations

import json
import sys
from collections import Counter, defaultdict
from dataclasses import dataclass
from pathlib import Path

TOOL_ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(TOOL_ROOT))

from ttskit.cli import load_context
from ttskit.extract import profile_for_item
from ttskit.models import Item
from ttskit.paths import Paths
from ttskit.plan import clip_key, status_of
from ttskit.store import read_json

PROMPT_LETTER_ONE = "Finde den Buchstaben - %s - im Wort - %s."
PROMPT_LETTER_MANY = "Finde alle Buchstaben - %s - im Wort - %s."
PROMPT_DIGRAPH_ONE = "Finde den Laut - %s - im Wort - %s."
PROMPT_DIGRAPH_MANY = "Finde alle Laute - %s - im Wort - %s."
PROMPT_SYLLABLE_ONE = "Finde die Silbe - %s - im Wort - %s."
PROMPT_SYLLABLE_MANY = "Finde alle Silben - %s - im Wort - %s."
MIN_SEGMENTS = 2

MATH_MISS = [
    ("math:miss:null", "Versuch es noch einmal", "MathHinting.missFeedback(null)"),
    ("math:miss:near", "Du bist nah dran, denk noch einmal nach", "MathHinting.missFeedback(near)"),
    ("math:miss:far", "Schau noch einmal genau hin", "MathHinting.missFeedback(far)"),
]

PRAISE_PHRASES = [
    "Super", "Gut gemacht", "Ausgezeichnet", "Klasse", "Genau richtig",
    "Toll gemacht", "Perfekt", "Stark", "Bravo", "Wunderbar",
    "Spitze", "Sehr gut", "Prima", "Fantastisch", "Großartig",
    "Weiter so", "Richtig gut", "Genau so", "Klasse gemacht",
    "Das hast du toll gemacht",
]

HUNT_PROMPTS = [
    ("huntPromptLetter", "Finde alle Buchstaben"),
    ("huntPromptLaut", "Finde alle Laute"),
    ("huntPromptSilbe", "Finde alle Silben"),
]


def load_content(content_dir: Path) -> dict:
    return {name.removesuffix(".json"): read_json(content_dir / name)
            for name in ("atoms.json", "sentences.json", "finales.json",
                         "tasks.json", "lessons.json")}


def grapheme_table(content: dict, lesson_index: int) -> list[str]:
    atoms = {a["id"]: a for a in content["atoms"]["atoms"]}
    table: list[str] = []
    seen: set[str] = set()
    for lesson in sorted(content["lessons"]["lessons"], key=lambda l: l["index"]):
        if lesson["index"] > lesson_index:
            break
        for task_id in lesson.get("taskIds", []):
            task = next((t for t in content["tasks"]["tasks"] if t["id"] == task_id), None)
            if not task or task.get("trainer") != "letter_trace":
                continue
            for rnd in task.get("rounds", []):
                atom = atoms.get(rnd["atomId"])
                if atom and atom.get("kind") == "letter" and len(atom["display"]) > 1:
                    disp = atom["display"]
                    if disp not in seen:
                        seen.add(disp)
                        table.append(disp)
    return sorted(table, key=len, reverse=True)


def split_graphemes(word: str, table: list[str]) -> list[str]:
    result: list[str] = []
    i = 0
    while i < len(word):
        matched = None
        for candidate in table:
            if word[i:i + len(candidate)].lower() == candidate.lower():
                matched = word[i:i + len(candidate)]
                break
        if matched is None:
            result.append(word[i])
            i += 1
        else:
            result.append(matched)
            i += len(matched)
    return result


def _letter_round(atoms, word_atom, graphemes, focus_letters, focus_cursor):
    if not focus_letters:
        return None
    rotated = [(focus_cursor + i) % len(focus_letters) for i in range(len(focus_letters))]
    focus_index = None
    for idx in rotated:
        display = atoms.get(focus_letters[idx], {}).get("display")
        if display and any(g.lower() == display.lower() for g in graphemes):
            focus_index = idx
            break
    if focus_index is None:
        return None
    target_id = focus_letters[focus_index]
    display = atoms[target_id]["display"]
    hits = [i for i, g in enumerate(graphemes) if g.lower() == display.lower()]
    if len(hits) > 1 and len(display) > 1:
        template = PROMPT_DIGRAPH_MANY
    elif len(hits) > 1:
        template = PROMPT_LETTER_MANY
    elif len(display) > 1:
        template = PROMPT_DIGRAPH_ONE
    else:
        template = PROMPT_LETTER_ONE
    rnd = {
        "promptTts": template % (display, word_atom["display"]),
        "segments": graphemes,
        "targetIndices": hits,
    }
    return rnd, focus_index


def _syllable_round(atoms, word, word_atom, focus_syllables):
    if len(word["blocks"]) < MIN_SEGMENTS:
        return None
    syllable_blocks = []
    for block in word["blocks"]:
        atom = atoms.get(block["atomId"])
        if atom and atom.get("kind") == "syllable" and block["display"].lower() == atom["display"].lower():
            syllable_blocks.append(block)
    if not syllable_blocks:
        return None
    target_block = next((b for b in syllable_blocks if b["atomId"] in focus_syllables), syllable_blocks[0])
    target = atoms[target_block["atomId"]]
    segments = [b["display"] for b in word["blocks"]]
    hits = [i for i, b in enumerate(word["blocks"]) if b["atomId"] == target_block["atomId"]]
    template = PROMPT_SYLLABLE_MANY if len(hits) > 1 else PROMPT_SYLLABLE_ONE
    rnd = {
        "promptTts": template % (target["display"], word_atom["display"]),
        "segments": segments,
        "targetIndices": hits,
    }
    return rnd, None


def symbol_in_word_items(content: dict) -> list[Item]:
    atoms = {a["id"]: a for a in content["atoms"]["atoms"]}
    tasks = {t["id"]: t for t in content["tasks"]["tasks"]}
    items: list[Item] = []

    for lesson in sorted(content["lessons"]["lessons"], key=lambda l: l["index"]):
        specs = [tasks[tid] for tid in lesson.get("taskIds", []) if tid in tasks]
        focus_letters = [
            rnd["atomId"]
            for spec in specs if spec.get("trainer") == "letter_trace"
            for rnd in spec.get("rounds", [])
        ]
        focus_syllables = {
            rnd["resultAtomId"]
            for spec in specs if spec.get("trainer") == "syllable_merge"
            for rnd in spec.get("rounds", [])
        }
        words = [
            rnd for spec in specs if spec.get("trainer") == "word_build"
            for rnd in spec.get("rounds", [])
        ]
        seen_words: set[str] = set()
        word_rounds = []
        for rnd in words:
            if rnd["targetAtomId"] not in seen_words:
                seen_words.add(rnd["targetAtomId"])
                word_rounds.append(rnd)

        rounds_built = 0
        focus_cursor = 0
        table = grapheme_table(content, lesson["index"])

        for word in word_rounds:
            word_atom = atoms.get(word["targetAtomId"])
            if not word_atom or word_atom.get("kind") == "syllable":
                continue
            graphemes = split_graphemes(word_atom["display"], table)
            if len(graphemes) < MIN_SEGMENTS:
                continue
            wants_syllable = rounds_built % 2 == 1
            built = _syllable_round(atoms, word, word_atom, focus_syllables) if wants_syllable else None
            if built is None:
                built = _letter_round(atoms, word_atom, graphemes, focus_letters, focus_cursor)
            if built is None:
                continue
            rnd, used_focus = built
            if len(rnd["targetIndices"]) >= len(rnd["segments"]):
                continue
            items.append(Item(
                id=f"derived:{lesson['id']}:{word_atom['id']}:promptTts",
                text=rnd["promptTts"],
                field="promptTts",
                source="SymbolInWordDerivation.kt",
                lesson=lesson["id"],
                label=f"{lesson['id']} · Wort-Detektiv · {word_atom['display']}",
            ))
            rounds_built += 1
            if used_focus is not None and focus_letters:
                focus_cursor = (used_focus + 1) % len(focus_letters)
    return items


def count_add_base_items(content: dict) -> list[Item]:
    """Unique spoken-answer strings for Rechnen (without praise prefix)."""
    atoms = {a["id"]: a for a in content["atoms"]["atoms"]}
    seen: set[str] = set()
    items: list[Item] = []
    for task in content["tasks"]["tasks"]:
        if task.get("trainer") != "count_add":
            continue
        for rnd in task.get("rounds", []):
            icon = atoms.get(rnd.get("iconAtomId"))
            answer = rnd["answer"]
            if icon is None:
                noun = ""
            elif answer == 1:
                noun = icon["display"]
            else:
                noun = icon.get("pluralDisplay") or icon["display"]
            text = f"{answer} {noun}".strip()
            if text in seen:
                continue
            seen.add(text)
            items.append(Item(
                id=f"derived:count_add:{answer}:{rnd.get('iconAtomId')}",
                text=text, field="uiText", source="CountAddRound.spokenAnswer",
                lesson=None, label=f"Rechnen answer · {text}",
            ))
    return items


def runtime_only_items(content: dict) -> list[Item]:
    items = []
    for item_id, text, note in MATH_MISS:
        items.append(Item(id=item_id, text=text, field="uiText",
                          source="MathHinting.kt", lesson=None, label=note))
    for i, phrase in enumerate(PRAISE_PHRASES):
        items.append(Item(id=f"praise:{i}", text=phrase, field="uiText",
                          source="PraisePhrases.kt", lesson=None,
                          label="Rechnen praise phrase"))
    items.extend(count_add_base_items(content))
    items.extend(symbol_in_word_items(content))
    return items


@dataclass
class AuditRow:
    text: str
    category: str
    source: str
    status: str
    notes: str


def clip_for_item(item: Item, ctx, clip_by_item: dict, text_to_clip: dict):
    clip = clip_by_item.get(item.id) or text_to_clip.get(item.text.strip())
    return clip


def resolve_status(text: str, clip, paths: Paths, index: dict, in_pipeline: bool) -> tuple[str, str]:
    key = text.strip()
    entry = index.get(key)
    if entry:
        fn = entry.get("file", "")
        if (paths.app_audio_dir / fn).exists():
            return "shipped", entry["profile"]

    if not in_pipeline:
        return "not in pipeline", "runtime-only; Android TTS fallback"

    if clip is None:
        return "not in pipeline", "unexpected — item not grouped into clip"

    if not clip.locked:
        if entry:
            fn = entry.get("file", "")
            if not (paths.app_audio_dir / fn).exists():
                return "unlocked only", f"clip {clip.key}; index stale"
        rs = status_of(clip, paths.audio)
        if rs == "rendered":
            return "rendered, not locked", f"clip {clip.key}; run wire-locks + export"
        return "unlocked only", f"clip {clip.key}; needs lock + render + export"

    if entry:
        fn = entry.get("file", "")
        return "missing file", f"index → {fn} absent on disk"

    rs = status_of(clip, paths.audio)
    if rs == "rendered":
        return "locked, not exported", f"clip {clip.key}; run export"
    return "locked, not rendered", f"clip {clip.key}; status={rs}"


def main() -> int:
    paths = Paths()
    ctx = load_context(paths)
    content = load_content(paths.content_dir)

    clip_by_item: dict[str, object] = {}
    for clip in ctx.clips:
        for iid in clip.item_ids:
            clip_by_item[iid] = clip
    text_to_clip = {c.source_text.strip(): c for c in ctx.clips}
    extracted_ids = {i.id for i in ctx.items}

    index = json.loads((paths.app_audio_dir / "index.json").read_text())["clips"]
    runtime_items = runtime_only_items(content)

    rows: list[AuditRow] = []
    seen_missing: set[str] = set()

    def audit_item(item: Item, in_pipeline: bool) -> None:
        text = item.text.strip()
        if not text:
            return
        clip = clip_for_item(item, ctx, clip_by_item, text_to_clip) if in_pipeline else None
        status, notes = resolve_status(text, clip, paths, index, in_pipeline)
        if status == "shipped":
            return
        if text in seen_missing:
            return
        seen_missing.add(text)
        rows.append(AuditRow(
            text=text,
            category=profile_for_item(item),
            source=f"{item.source} · {item.label}",
            status=status,
            notes=notes,
        ))

    for item in ctx.items:
        audit_item(item, in_pipeline=True)
    for item in runtime_items:
        audit_item(item, in_pipeline=False)

    by_status = Counter(r.status for r in rows)
    by_cat = Counter(r.category for r in rows)

    # Pipeline coverage stats
    shipped = sum(1 for i in ctx.items if resolve_status(i.text, clip_for_item(i, ctx, clip_by_item, text_to_clip), paths, index, True)[0] == "shipped")
    pipeline_missing = [r for r in rows if r.status not in (
        "not in pipeline", "rendered, not locked")]

    print("# Audio pack audit\n")
    print("## Summary\n")
    print(f"| Metric | Count |")
    print(f"| --- | ---: |")
    print(f"| TTS-pipeline items (content + extra-strings) | {len(ctx.items)} |")
    print(f"| Unique pipeline clips | {len(ctx.clips)} |")
    print(f"| Locked clips | {sum(1 for c in ctx.clips if c.locked)} |")
    print(f"| **Shipped in index.json** | **{len(index)}** |")
    print(f"| .ogg files on disk | {len(list(paths.app_audio_dir.glob('*.ogg')))} |")
    print(f"| Pipeline items with shipped clip | {shipped} |")
    print(f"| Runtime-only speakable strings (not in extract) | {len(runtime_items)} |")
    print()
    print("### Missing / gap status\n")
    print(f"| Status | Count |")
    print(f"| --- | ---: |")
    for st in ("locked, not exported", "locked, not rendered", "missing file",
               "rendered, not locked", "unlocked only", "not in pipeline"):
        if by_status.get(st):
            print(f"| {st} | {by_status[st]} |")
    true_gaps = [r for r in rows if r.status not in (
        "not in pipeline", "rendered, not locked")]
    print(f"| **True gaps (excl. wire-locks queue + runtime)** | **{len(true_gaps)}** |")
    print(f"| **Total rows** | **{len(rows)}** |")
    print()

    print("## Hunt intro prompts (the three new strings)\n")
    for _id, ht in HUNT_PROMPTS:
        entry = index.get(ht)
        if entry and (paths.app_audio_dir / entry["file"]).exists():
            print(f"- `{ht}` — **shipped** (`{entry['file']}`)")
        else:
            locked = text_to_clip.get(ht)
            st = "missing"
            if locked and locked.locked:
                st = f"locked ({status_of(locked, paths.audio)}) but not exported"
            print(f"- `{ht}` — **{st.upper()}**")
    print()

    # Category breakdown for pipeline gaps only
    pipe_by_cat = Counter(r.category for r in pipeline_missing)
    print("## Pipeline gaps by category\n")
    for cat, n in sorted(pipe_by_cat.items()):
        print(f"- **{cat}**: {n}")
    print()

    runtime_by_cat = Counter(r.category for r in rows if r.status == "not in pipeline")
    if runtime_by_cat:
        print("## Runtime-only (not in TTS extract/export)\n")
        for cat, n in sorted(runtime_by_cat.items()):
            print(f"- **{cat}**: {n}")
        print(f"- Rechnen also speaks `{len(PRAISE_PHRASES)}` praise phrases × N unique count answers as combined strings (e.g. `Super! 5 Ameisen`) — all Android TTS fallback; not enumerated individually.")
        print()

    # Tables by category for pipeline items
    for cat in sorted(pipe_by_cat):
        cat_rows = [r for r in pipeline_missing if r.category == cat]
        if not cat_rows:
            continue
        print(f"## {cat} — pipeline gaps ({len(cat_rows)})\n")
        print("| Text | Source | Status | Notes |")
        print("| --- | --- | --- | --- |")
        for r in sorted(cat_rows, key=lambda x: x.text)[:60]:
            print(f"| {r.text.replace('|', '\\\\|')} | {r.source[:70].replace('|', '\\\\|')} | {r.status} | {r.notes} |")
        if len(cat_rows) > 60:
            print(f"| … | *{len(cat_rows) - 60} more* | | |")
        print()

    # Runtime tables (compact)
    for cat in ("prompt", "ui"):
        cat_rows = [r for r in rows if r.category == cat and r.status == "not in pipeline"]
        if not cat_rows:
            continue
        print(f"## {cat} — runtime-only ({len(cat_rows)})\n")
        print("| Text | Source | Notes |")
        print("| --- | --- | --- |")
        for r in sorted(cat_rows, key=lambda x: x.text)[:40]:
            print(f"| {r.text.replace('|', '\\\\|')} | {r.source[:60].replace('|', '\\\\|')} | Android TTS |")
        if len(cat_rows) > 40:
            print(f"| … | *{len(cat_rows) - 40} more* | |")
        print()

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
