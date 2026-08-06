"""Command line entry point. Imports the model layer lazily."""

from __future__ import annotations

import argparse
import collections
import json
import time
from dataclasses import dataclass, field

from .extract import extract_items
from .models import Clip, Item
from .paths import Paths
from .plan import build_clips, orphan_locks, status_of
from .migrate import migrate_word_locks, wire_production_locks
from .store import Locks, Profiles, RenderState, read_json


@dataclass
class Context:
    items: list[Item]
    profiles: Profiles
    locks: Locks
    clips: list[Clip]
    state: RenderState
    #: Raw extra-strings.json, carried so callers need not re-read it.
    extras: dict | None = None
    #: Item ids whose text was blank and therefore skipped.
    blanks: list[str] = field(default_factory=list)


def load_context(paths: Paths) -> Context:
    extra = read_json(paths.extra_strings)
    blanks: list[str] = []
    items = extract_items(paths.content_dir, extra_strings=extra, blanks=blanks)
    profiles = Profiles.load(paths.profiles)
    locks = Locks.load(paths.locks)
    return Context(
        items=items,
        profiles=profiles,
        locks=locks,
        clips=build_clips(items, profiles, locks),
        state=RenderState.load(paths.render_state),
        extras=extra,
        blanks=blanks,
    )


def cmd_extract(paths: Paths) -> int:
    ctx = load_context(paths)
    item_clip = {}
    for clip in ctx.clips:
        for item_id in clip.item_ids:
            item_clip[item_id] = clip.key

    payload = {
        "version": 1,
        "itemCount": len(ctx.items),
        "clipCount": len(ctx.clips),
        "items": [{
            "id": i.id, "text": i.text, "field": i.field, "source": i.source,
            "lesson": i.lesson, "label": i.label, "clipKey": item_clip[i.id],
        } for i in ctx.items],
    }
    paths.manifest.parent.mkdir(parents=True, exist_ok=True)
    paths.manifest.write_text(
        json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{len(ctx.items)} Items → {len(ctx.clips)} Clips → {paths.manifest}")
    return 0


def cmd_status(paths: Paths) -> int:
    ctx = load_context(paths)
    counts: dict[str, collections.Counter] = collections.defaultdict(collections.Counter)
    for clip in ctx.clips:
        counts[clip.profile][status_of(clip, paths.audio)] += 1

    print(f"{'Profil':<10} {'gesamt':>7} {'missing':>7} {'rendered':>8} {'Pool':>6}")
    for name in sorted(counts):
        c = counts[name]
        total = sum(c.values())
        pool = len(ctx.profiles.profiles[name].seed_pool)
        print(f"{name:<10} {total:>7} {c['missing']:>7} "
              f"{c['rendered']:>8} {pool:>6}")

    locked = sum(1 for c in ctx.clips if c.locked)
    print(f"\n{len(ctx.clips)} Clips aus {len(ctx.items)} Items, davon {locked} gelockt.")

    empty = [n for n, p in ctx.profiles.profiles.items() if not p.seed_pool]
    if empty:
        print(f"Seed-Pool leer bei: {', '.join(sorted(empty))} "
              f"— Seeds werden aus dem Clip-Hash abgeleitet.")

    live = {c.key for c in ctx.clips}
    failures = {k: v for k, v in sorted(ctx.state.failures.items()) if k in live}
    if failures:
        print(f"\n{len(failures)} Clips sind beim letzten Lauf fehlgeschlagen:")
        for key, message in failures.items():
            print(f"  {key}  {message}")

    if ctx.blanks:
        print(f"\n{len(ctx.blanks)} Items mit leerem Text übersprungen:")
        for item_id in ctx.blanks:
            print(f"  {item_id}")

    orphans = orphan_locks(ctx.locks, ctx.clips)
    if orphans:
        print(f"\n{len(orphans)} verwaiste Locks (Text hat sich geändert?):")
        for key in orphans:
            lock = ctx.locks.get(key)
            shown = lock.source_text if lock and lock.source_text else "(kein Text notiert)"
            print(f"  {key}  seed={lock.seed}  {shown!r}")

    templates = (ctx.extras or {}).get("templates", [])
    if not templates:
        print("\nHinweis: keine Template-Expansionen erfasst — die Sprechtexte von "
              "Symbol-Jagd und Wort-Detektiv fehlen im Paket (Spec §2).")
    return 0


def _human_duration(seconds: float) -> str:
    seconds = max(0, int(round(seconds)))
    if seconds < 60:
        return f"{seconds} s"
    return f"{seconds // 60} min {seconds % 60:02d} s"


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

    started = time.monotonic()

    def show(p) -> None:
        mark = "!" if p.status == "failed" else "."
        # Running average, not a per-clip estimate: clip time swings from ~2.4 s
        # for a word to ~3.2 s for a finale sentence, so only the mean over the
        # run so far says anything useful about the remaining minutes.
        average = (time.monotonic() - started) / max(1, p.index)
        parts = [f"[{p.index}/{p.total}]", mark, p.clip_key]
        if p.message:
            parts.append(p.message)
        parts.append(f"(noch ~{_human_duration(average * (p.total - p.index))})")
        print(" ".join(parts))

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


def cmd_migrate_locks(paths: Paths, dry_run: bool = False) -> int:
    report = migrate_word_locks(paths, dry_run=dry_run)
    moved = len(report.locks_moved) + len(report.locks_replaced)
    dropped = len(report.locks_dropped)
    print(f"{'(dry-run) ' if dry_run else ''}"
          f"{moved} locks → phoneme:*, {dropped} redundant word:* removed, "
          f"{len(report.audio_copied)} WAVs copied, "
          f"{len(report.candidate_dirs_merged)} candidate dirs merged")
    for word_key, phoneme_key in report.locks_moved + report.locks_replaced:
        print(f"  {word_key} → {phoneme_key}")
    for key in report.locks_dropped:
        print(f"  dropped {key} (phoneme lock kept)")
    return 0


def cmd_wire_locks(paths: Paths, dry_run: bool = False) -> int:
    report = wire_production_locks(paths, dry_run=dry_run)
    print(f"{'(dry-run) ' if dry_run else ''}"
          f"{len(report.locked)} Clips gesperrt (Produktions-WAV vorhanden, kein Lock)")
    for key, seed in report.locked[:20]:
        print(f"  {key} seed={seed}")
    if len(report.locked) > 20:
        print(f"  … und {len(report.locked) - 20} weitere")
    if report.skipped:
        print(f"{len(report.skipped)} übersprungen")
    return 0


def cmd_export(paths: Paths) -> int:
    from .export import export_to_app

    report = export_to_app(paths)
    print(f"{len(report.exported)} Clips exportiert → {paths.app_audio_dir}")
    if report.unchanged:
        print(f"{len(report.unchanged)} Clips unverändert übersprungen "
              f"(Fingerprint gleich)")
    if report.removed:
        print(f"{len(report.removed)} nicht mehr benötigte Dateien entfernt: "
              f"{', '.join(report.removed)}")
    for key, reason in report.skipped:
        print(f"  übersprungen {key}: {reason}")
    for warning in report.warnings:
        print(f"  Achtung: {warning}")
    return 0


def cmd_web(paths: Paths, args) -> int:
    import uvicorn

    from .server import create_app

    print("Lade Modell — das dauert ein paar Sekunden ...")
    app = create_app(paths)
    print(f"Web-Interface auf http://{args.host}:{args.port}")
    print("Beenden mit Ctrl-C.")
    uvicorn.run(
        app,
        host=args.host,
        port=args.port,
        log_level="warning",
        # Offene Browser-Tabs halten `/events` (SSE) am Leben; ohne kurzes
        # Grace-Timeout wirkt Ctrl-C, als ob nichts passiert.
        timeout_graceful_shutdown=1,
    )
    return 0


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(prog="tts", description="Qwen-TTS Audio-Pipeline")
    sub = parser.add_subparsers(dest="command", required=True)
    sub.add_parser("extract", help="Content-JSON → out/manifest.json")
    sub.add_parser("status", help="Überblick über Clips, Pools und Locks")
    migrate_parser = sub.add_parser(
        "migrate-locks",
        help="word:*-Locks für Buchstaben/Silben → phoneme:* (Option B)",
    )
    migrate_parser.add_argument("--dry-run", action="store_true",
                              help="nur anzeigen, nichts schreiben")
    wire_parser = sub.add_parser(
        "wire-locks",
        help="Produktions-WAV ohne Lock → Lock anlegen (Batch-Render-Nachzügler)",
    )
    wire_parser.add_argument("--dry-run", action="store_true",
                             help="nur anzeigen, nichts schreiben")
    sub.add_parser("export", help="Approvete Clips als OGG in die App-Assets")

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

    web_parser = sub.add_parser("web", help="Web-Interface starten")
    web_parser.add_argument("--port", type=int, default=8420)
    web_parser.add_argument("--host", default="127.0.0.1")
    return parser


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    paths = Paths()
    if args.command == "extract":
        return cmd_extract(paths)
    if args.command == "status":
        return cmd_status(paths)
    if args.command == "migrate-locks":
        return cmd_migrate_locks(paths, dry_run=args.dry_run)
    if args.command == "wire-locks":
        return cmd_wire_locks(paths, dry_run=args.dry_run)
    if args.command == "export":
        return cmd_export(paths)
    if args.command == "render":
        return cmd_render(paths, args)
    if args.command == "sample":
        return cmd_sample(paths, args)
    if args.command == "web":
        return cmd_web(paths, args)
    return 1
