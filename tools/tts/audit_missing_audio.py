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

DETECTIVE_PROMPTS = [
    ("detectivePromptLetter", "Finde den Buchstaben"),
    ("detectivePromptLaut", "Finde den Laut"),
    ("detectivePromptSilbe", "Finde die Silbe"),
]


def runtime_only_items() -> list[Item]:
    """Speakable strings that `tts extract` does not see.

    Rechnen and Wort-Detektiv are no longer listed: Wort-Detektiv speaks its prompt
    as parts that each have their own clip (SymbolInWordSpeech), Rechnen speaks the
    intro plus the task as one clip — both extracted as `mathTaskTts`, profile
    `math` — and a Rechnen miss is a sound, not speech. Their whole promptTts
    sentences are never spoken, so they are not gaps.
    """
    items = []
    for i, phrase in enumerate(PRAISE_PHRASES):
        items.append(Item(id=f"praise:{i}", text=phrase, field="uiText",
                          source="PraisePhrases.kt", lesson=None,
                          label="Rechnen praise phrase"))
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

    clip_by_item: dict[str, object] = {}
    for clip in ctx.clips:
        for iid in clip.item_ids:
            clip_by_item[iid] = clip
    text_to_clip = {c.source_text.strip(): c for c in ctx.clips}
    extracted_ids = {i.id for i in ctx.items}

    index = json.loads((paths.app_audio_dir / "index.json").read_text())["clips"]
    runtime_items = runtime_only_items()

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

    print("## Hunt intro prompts (SymbolHuntSpeech)\n")
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

    print("## Wort-Detektiv intro prompts (SymbolInWordSpeech)\n")
    for _id, dt in DETECTIVE_PROMPTS:
        entry = index.get(dt)
        if entry and (paths.app_audio_dir / entry["file"]).exists():
            print(f"- `{dt}` — **shipped** (`{entry['file']}`)")
        else:
            locked = text_to_clip.get(dt)
            st = "missing"
            if locked and locked.locked:
                st = f"locked ({status_of(locked, paths.audio)}) but not exported"
            print(f"- `{dt}` — **{st.upper()}**")
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
