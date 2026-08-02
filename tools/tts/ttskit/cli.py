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


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="tts", description="Qwen-TTS Audio-Pipeline")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("extract", help="Content-JSON → out/manifest.json")
    sub.add_parser("status", help="Überblick über Clips, Pools und Locks")

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
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    paths = Paths()
    if args.command == "extract":
        return cmd_extract(paths)
    if args.command == "status":
        return cmd_status(paths)
    if args.command == "render":
        return cmd_render(paths, args)
    if args.command == "sample":
        return cmd_sample(paths, args)
    return 1
