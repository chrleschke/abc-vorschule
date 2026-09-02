"""The layer the operator actually types.

Everything here is monkeypatched or faked — no model is ever loaded.
"""

import json
import shutil
from pathlib import Path

import pytest

from ttskit import cli
from ttskit.paths import Paths
from ttskit.render import RenderReport


def make_root(tmp_path: Path, content_dir: Path) -> Paths:
    root = tmp_path / "ttsroot"
    (root / "content").mkdir(parents=True)
    for f in content_dir.iterdir():
        shutil.copy(f, root / "content" / f.name)
    (root / "extra-strings.json").write_text(
        json.dumps({"version": 1, "strings": [], "templates": []}), encoding="utf-8")
    return Paths(root=root, content_dir=root / "content")


class FakeEngine:
    loaded = True
    load_error = None
    device = "fake"

    def __init__(self, errors=None):
        self.errors = errors or []

    def load(self):
        pass

    def validate(self, profiles):
        return self.errors


# --- build_parser + main dispatch -------------------------------------------


def test_parser_maps_every_render_flag_to_its_own_argument():
    args = cli.build_parser().parse_args(
        ["render", "--only", "task:l01-t1:*", "--profile", "phoneme",
         "--force", "--dry-run"])
    assert args.command == "render"
    assert args.only == "task:l01-t1:*"
    assert args.profile == "phoneme"
    assert args.force is True
    assert args.dry_run is True


@pytest.mark.parametrize("dry_run", [True, False])
def test_main_render_passes_only_and_profile_through_unswapped(
    tmp_path, content_dir, monkeypatch, capsys, dry_run,
):
    """Guards both call sites against `only` and `profile` being swapped."""
    paths = make_root(tmp_path, content_dir)
    monkeypatch.setattr(cli, "Paths", lambda: paths)
    monkeypatch.setattr(cli, "_engine_or_exit", lambda profiles: FakeEngine())
    seen = {}

    def fake_render_clips(clips, profiles, engine, state, path_arg, **kwargs):
        kwargs.pop("progress", None)
        seen.update(kwargs)
        return RenderReport(rendered=1, skipped=2)

    import ttskit.render
    monkeypatch.setattr(ttskit.render, "render_clips", fake_render_clips)

    argv = ["render", "--only", "task:l01-t1:*", "--profile", "phoneme", "--force"]
    if dry_run:
        argv.append("--dry-run")
    assert cli.main(argv) == 0
    expected = {"force": True, "only": "task:l01-t1:*", "profile": "phoneme"}
    if dry_run:
        expected["dry_run"] = True
    assert seen == expected
    capsys.readouterr()


def test_main_dispatches_each_subcommand(tmp_path, content_dir, monkeypatch):
    paths = make_root(tmp_path, content_dir)
    monkeypatch.setattr(cli, "Paths", lambda: paths)
    called = []
    monkeypatch.setattr(cli, "cmd_extract", lambda p: called.append("extract") or 0)
    monkeypatch.setattr(cli, "cmd_status", lambda p: called.append("status") or 0)
    monkeypatch.setattr(cli, "cmd_render", lambda p, a: called.append("render") or 0)
    monkeypatch.setattr(cli, "cmd_sample", lambda p, a: called.append("sample") or 0)
    monkeypatch.setattr(cli, "cmd_web", lambda p, a: called.append("web") or 0)

    assert cli.main(["extract"]) == 0
    assert cli.main(["status"]) == 0
    assert cli.main(["render"]) == 0
    assert cli.main(["sample", "--profile", "phoneme"]) == 0
    assert cli.main(["web"]) == 0
    assert called == ["extract", "status", "render", "sample", "web"]


def test_main_rejects_a_missing_subcommand():
    with pytest.raises(SystemExit):
        cli.main([])


# --- cmd_render --------------------------------------------------------------


def test_dry_run_never_touches_the_engine(tmp_path, content_dir, monkeypatch, capsys):
    """The one line that makes `engine=None` safe in render_clips."""
    paths = make_root(tmp_path, content_dir)

    def boom(profiles):
        raise AssertionError("a dry run must not load a model")

    monkeypatch.setattr(cli, "_engine_or_exit", boom)
    args = cli.build_parser().parse_args(["render", "--dry-run"])
    assert cli.cmd_render(paths, args) == 0
    assert "würden gerendert" in capsys.readouterr().out
    assert not paths.audio.exists()


def test_render_exits_0_when_nothing_failed(tmp_path, content_dir, monkeypatch, capsys):
    paths = make_root(tmp_path, content_dir)
    monkeypatch.setattr(cli, "_engine_or_exit", lambda profiles: FakeEngine())

    import ttskit.render
    monkeypatch.setattr(ttskit.render, "render_clips",
                        lambda *a, **k: RenderReport(rendered=3, skipped=1))

    args = cli.build_parser().parse_args(["render"])
    assert cli.cmd_render(paths, args) == 0
    assert "3 gerendert, 1 übersprungen, 0 fehlgeschlagen" in capsys.readouterr().out


def test_render_exits_1_and_names_every_failure(tmp_path, content_dir, monkeypatch,
                                                capsys):
    paths = make_root(tmp_path, content_dir)
    monkeypatch.setattr(cli, "_engine_or_exit", lambda profiles: FakeEngine())

    import ttskit.render
    monkeypatch.setattr(ttskit.render, "render_clips", lambda *a, **k: RenderReport(
        rendered=1, skipped=0, failed=[("prompt:abc", "RuntimeError: kaputt")]))

    args = cli.build_parser().parse_args(["render"])
    assert cli.cmd_render(paths, args) == 1
    out = capsys.readouterr().out
    assert "1 fehlgeschlagen" in out
    assert "prompt:abc: RuntimeError: kaputt" in out


def test_render_exits_1_when_the_engine_is_unavailable(tmp_path, content_dir,
                                                       monkeypatch):
    paths = make_root(tmp_path, content_dir)
    monkeypatch.setattr(cli, "_engine_or_exit", lambda profiles: None)
    args = cli.build_parser().parse_args(["render"])
    assert cli.cmd_render(paths, args) == 1


def test_render_progress_line_carries_an_eta(tmp_path, content_dir, monkeypatch,
                                             capsys):
    paths = make_root(tmp_path, content_dir)
    monkeypatch.setattr(cli, "_engine_or_exit", lambda profiles: FakeEngine())

    from ttskit.render import Progress

    def fake_render_clips(*a, progress=None, **k):
        progress(Progress(index=1, total=4, clip_key="prompt:abc", status="ok"))
        progress(Progress(index=2, total=4, clip_key="prompt:def", status="failed",
                          message="RuntimeError: kaputt"))
        return RenderReport(rendered=1, failed=[("prompt:def", "kaputt")])

    import ttskit.render
    monkeypatch.setattr(ttskit.render, "render_clips", fake_render_clips)
    cli.cmd_render(paths, cli.build_parser().parse_args(["render"]))
    out = capsys.readouterr().out
    assert "[1/4] . prompt:abc (noch ~" in out
    assert "[2/4] ! prompt:def RuntimeError: kaputt (noch ~" in out


def test_human_duration_reads_as_minutes_past_a_minute():
    assert cli._human_duration(0) == "0 s"
    assert cli._human_duration(42.4) == "42 s"
    assert cli._human_duration(65) == "1 min 05 s"
    assert cli._human_duration(1680) == "28 min 00 s"


# --- _engine_or_exit ---------------------------------------------------------


def test_engine_or_exit_aborts_when_validation_reports_errors(monkeypatch, capsys):
    """A rejected speaker must stop the run, not render 694 broken clips."""
    engine = FakeEngine(errors=["Profil 'word': Speaker 'sohee' wird nicht unterstützt."])
    monkeypatch.setitem(
        __import__("sys").modules, "ttskit.engine",
        type("M", (), {"Engine": lambda: engine}))
    from ttskit.store import Profiles

    assert cli._engine_or_exit(Profiles.load(Path("nope.json"))) is None
    assert "wird nicht unterstützt" in capsys.readouterr().out


def test_engine_or_exit_returns_the_engine_when_valid(monkeypatch, capsys):
    engine = FakeEngine()
    monkeypatch.setitem(
        __import__("sys").modules, "ttskit.engine",
        type("M", (), {"Engine": lambda: engine}))
    from ttskit.store import Profiles

    assert cli._engine_or_exit(Profiles.load(Path("nope.json"))) is engine
    assert "Modell geladen auf fake" in capsys.readouterr().out


def test_engine_or_exit_aborts_when_the_model_will_not_load(monkeypatch, capsys):
    class Dead(FakeEngine):
        loaded = False
        load_error = "ImportError: kein qwen_tts"

    monkeypatch.setitem(
        __import__("sys").modules, "ttskit.engine",
        type("M", (), {"Engine": Dead}))
    from ttskit.store import Profiles

    assert cli._engine_or_exit(Profiles.load(Path("nope.json"))) is None
    assert "kein qwen_tts" in capsys.readouterr().out


# --- cmd_sample --------------------------------------------------------------


def test_sample_rejects_an_unknown_profile(tmp_path, content_dir, capsys):
    paths = make_root(tmp_path, content_dir)
    args = cli.build_parser().parse_args(["sample", "--profile", "tippfehler"])
    assert cli.cmd_sample(paths, args) == 1
    assert "Unbekanntes Profil" in capsys.readouterr().out


def test_sample_reports_a_profile_without_clips(tmp_path, content_dir, capsys):
    paths = make_root(tmp_path, content_dir)
    args = cli.build_parser().parse_args(["sample", "--profile", "ui"])
    assert cli.cmd_sample(paths, args) == 1
    assert "Keine Clips im Profil" in capsys.readouterr().out


def test_sample_writes_one_candidate_per_seed(tmp_path, content_dir, monkeypatch,
                                              capsys):
    import numpy as np

    paths = make_root(tmp_path, content_dir)

    class Generating(FakeEngine):
        def generate(self, text, profile, seed):
            return np.full(2400, 0.5, dtype=np.float32), 24000

    monkeypatch.setattr(cli, "_engine_or_exit", lambda profiles: Generating())
    args = cli.build_parser().parse_args(
        ["sample", "--profile", "phoneme", "-n", "2", "--examples", "1"])
    assert cli.cmd_sample(paths, args) == 0
    written = list(paths.candidates.rglob("*.wav"))
    assert len(written) == 2
    assert "Kandidaten unter" in capsys.readouterr().out


def test_sample_exits_1_when_the_engine_is_unavailable(tmp_path, content_dir,
                                                       monkeypatch):
    paths = make_root(tmp_path, content_dir)
    monkeypatch.setattr(cli, "_engine_or_exit", lambda profiles: None)
    args = cli.build_parser().parse_args(["sample", "--profile", "phoneme"])
    assert cli.cmd_sample(paths, args) == 1


# --- cmd_web -----------------------------------------------------------------


def test_web_builds_the_app_and_serves_it_on_the_requested_address(
    tmp_path, content_dir, monkeypatch, capsys,
):
    paths = make_root(tmp_path, content_dir)
    sentinel = object()
    served = {}

    import ttskit.server
    monkeypatch.setattr(ttskit.server, "create_app", lambda p: sentinel)

    import uvicorn
    monkeypatch.setattr(uvicorn, "run",
                        lambda app, **kwargs: served.update(app=app, **kwargs))

    args = cli.build_parser().parse_args(["web", "--port", "9999", "--host", "0.0.0.0"])
    assert cli.cmd_web(paths, args) == 0
    assert served["app"] is sentinel
    assert served["host"] == "0.0.0.0"
    assert served["port"] == 9999
    assert served["timeout_graceful_shutdown"] == 1
    assert "http://0.0.0.0:9999" in capsys.readouterr().out


def test_export_summarises_instead_of_listing_every_skip(
    tmp_path, content_dir, monkeypatch, capsys,
):
    """Der Bericht war ein Schadensbericht: eine Zeile pro übersprungenem Clip
    und eine Komma-Liste aller entfernten Dateien — bei ~1000 Clips und 800
    Locks zweihundert Zeilen für den Normalfall. Jetzt zählt er nach Grund."""
    from ttskit.export import ExportReport

    paths = make_root(tmp_path, content_dir)
    paths.app_audio_dir.mkdir(parents=True, exist_ok=True)
    report = ExportReport(
        exported=["word:a"],
        unchanged=["word:b", "word:c"],
        removed=[f"stale_{i}.ogg" for i in range(40)],
        skipped=[(f"word:{i}", "Lock ist verwaist — Quelltext existiert nicht mehr")
                 for i in range(120)]
        + [(f"prompt:{i}", "Lokal nicht gerendert (status missing)") for i in range(7)],
    )
    monkeypatch.setattr(cli, "Paths", lambda: paths)
    monkeypatch.setattr("ttskit.export.export_to_app", lambda p: report)

    assert cli.main(["export"]) == 0
    out = capsys.readouterr().out

    assert "127 übersprungen" in out
    assert "120 × Lock ist verwaist" in out
    assert "7 × Lokal nicht gerendert" in out
    assert "40 entfernt" in out
    # Kein einzelner Clip-Key und kein einzelner Dateiname in der Kurzfassung.
    assert "word:42" not in out
    assert "stale_7.ogg" not in out
    assert len(out.splitlines()) < 15

    assert cli.main(["export", "--verbose"]) == 0
    verbose = capsys.readouterr().out
    assert "word:42" in verbose
    assert "stale_7.ogg" in verbose
