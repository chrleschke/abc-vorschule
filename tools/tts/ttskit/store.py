"""Persistence for the human decisions and the derived render state.

profiles.json, locks.json and extra-strings.json hold decisions a person made
and belong in git. render-state.json is derivable and lives under out/.
"""

from __future__ import annotations

import json
import os
import tempfile
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
    """Write atomically: readers must never observe a truncated file.

    `render_clips` saves render-state.json after every clip, and the server
    re-reads profiles.json / locks.json / render-state.json on every HTTP
    request, so a naive truncate-then-write has a real reader landing in the
    gap. `os.replace` is atomic on the same filesystem, so a concurrent reader
    always sees either the fully-old or the fully-new file, never a partial
    one. The temp file lives in the same directory (via `dir=`) so it is on
    the same filesystem as the target, and `mkstemp` guarantees a unique name
    per call so concurrent writers never collide on the temp path itself.
    """
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(
        dir=path.parent, prefix=f".{path.name}.", suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(json.dumps(payload, indent=2, ensure_ascii=False) + "\n")
        os.replace(tmp_name, path)
    except BaseException:
        try:
            os.unlink(tmp_name)
        except OSError:
            pass
        raise


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
