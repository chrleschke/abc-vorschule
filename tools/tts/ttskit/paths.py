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
