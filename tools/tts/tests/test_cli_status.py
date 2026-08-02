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
