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

#: Ein Codec-Token des Talkers entspricht so vielen Sekunden Audio.
#: Hergeleitet aus dem 12-Hz-Tokenizer: decode_upsample_rate 1920 bei
#: 24 kHz Ausgabe, also 1920 / 24000. Steht hier und nur hier — das UI
#: bekommt den Wert über /api/state, damit die Kopplung an genau einer
#: Stelle gepflegt wird.
SECONDS_PER_TOKEN = 0.08

#: Obergrenze für max_new_tokens: der Default aus generation_config.json
#: des Checkpoints. Mehr anzubieten wäre unbelegt.
MAX_NEW_TOKENS_CEILING = 8192

#: Obergrenze für Zufalls-Seeds — entspricht `secrets.randbelow(2**31)`.
MAX_RANDOM_SEED = 2 ** 31


def parse_seed(value: Any) -> int:
    """Ganzzahl im Bereich, den `random_seeds` erzeugt."""
    try:
        seed = int(value)
    except (TypeError, ValueError) as exc:
        raise ValueError(f"Seed muss eine Ganzzahl sein, nicht {value!r}") from exc
    if seed < 0 or seed >= MAX_RANDOM_SEED:
        raise ValueError(
            f"Seed muss zwischen 0 und {MAX_RANDOM_SEED - 1} liegen, nicht {seed}")
    return seed


@dataclass(frozen=True)
class SamplingParam:
    """Ein Sampling-Parameter samt allem, was UI und Prüfung brauchen.

    Vorher war `BASE_SAMPLING` ein nacktes Wert-Dict. Damit konnte das UI nur
    die Schlüssel anzeigen, die in einem Profil schon standen — ein neuer
    Parameter blieb an bestehenden Profilen für immer unsichtbar. Und geprüft
    wurde nur „ist eine Zahl", sodass ein `top_p: 3` in der git-verwalteten
    profiles.json landete und danach still unbrauchbare Audios erzeugte.
    """

    key: str
    label: str
    #: "duration" | "talker" | "subtalker" — die Gruppierung im ⚙️-Panel.
    group: str
    minimum: float
    maximum: float
    step: float
    help: str
    #: None heißt „kein globaler Default" — der Wert ist profilabhängig und
    #: sein Fehlen in profiles.json bedeutet „Modell-Default".
    default: float | int | None = None
    integer: bool = False
    #: Darf per `null` gelöscht werden. Nur für max_new_tokens.
    nullable: bool = False

    def to_dict(self) -> dict[str, Any]:
        return {
            "key": self.key,
            "label": self.label,
            "group": self.group,
            "minimum": self.minimum,
            "maximum": self.maximum,
            "step": self.step,
            "help": self.help,
            "default": self.default,
            "integer": self.integer,
            "nullable": self.nullable,
        }


SAMPLING_SPEC: tuple[SamplingParam, ...] = (
    SamplingParam(
        key="max_new_tokens", label="Maximale Dauer", group="duration",
        minimum=2, maximum=MAX_NEW_TOKENS_CEILING, step=1,
        integer=True, nullable=True, default=None,
        help="Deckelt, wie lang die Aufnahme werden darf. 1 Token = 80 ms, "
             "der Wert gilt vor dem Wegschneiden der Stille. Harter Schnitt: "
             "erfindet das Modell einen Satz dazu, bricht die Aufnahme mitten "
             "drin ab — sie ist dann hörbar kaputt statt unauffällig falsch. "
             "Leer = unbegrenzt (655 s).",
    ),
    SamplingParam(
        key="temperature", label="temperature", group="talker",
        minimum=0.1, maximum=2.0, step=0.05, default=0.6,
        help="Wie stark das Modell vom wahrscheinlichsten Klang abweicht. "
             "Niedrig (0,3–0,6) = gleichmäßig und vorhersagbar, Seeds klingen "
             "ähnlich. Hoch (1,0–1,5) = mehr Variation zwischen Seeds, aber "
             "auch mehr Ausrutscher. Über 1,5 wird es unbrauchbar.",
    ),
    SamplingParam(
        key="top_k", label="top_k", group="talker",
        minimum=1, maximum=100, step=1, integer=True, default=30,
        help="Nur die k wahrscheinlichsten Fortsetzungen kommen überhaupt in "
             "Frage. Klein (10–30) = enger und sicherer, groß (50–100) = mehr "
             "Spielraum. 1 macht die Generierung deterministisch und den Seed "
             "damit wirkungslos.",
    ),
    SamplingParam(
        key="top_p", label="top_p", group="talker",
        minimum=0.05, maximum=1.0, step=0.05, default=0.9,
        help="Nucleus-Sampling: es werden nur so viele Fortsetzungen "
             "betrachtet, wie zusammen diesen Anteil der Wahrscheinlichkeit "
             "ausmachen. 1,0 = keine Begrenzung, 0,9 = das unwahrscheinlichste "
             "Zehntel fällt weg. Wirkt in dieselbe Richtung wie top_k, nur "
             "relativ statt als feste Anzahl.",
    ),
    SamplingParam(
        key="repetition_penalty", label="repetition_penalty", group="talker",
        minimum=1.0, maximum=2.0, step=0.01, default=1.05,
        help="Bestraft schon verwendete Klang-Tokens. 1,0 = keine Strafe. "
             "Über 1,0 verringert Stottern und hängende Silben, zu hoch "
             "(über ~1,3) macht die Sprechmelodie unruhig, weil das Modell "
             "natürliche Wiederholungen vermeidet.",
    ),
    SamplingParam(
        key="subtalker_temperature", label="subtalker_temperature",
        group="subtalker", minimum=0.1, maximum=2.0, step=0.05, default=0.9,
        help="Wie temperature, aber für die akustische Feinstruktur (Timbre, "
             "Rauschen) statt für den Sprachinhalt. Niedriger = sauberere, "
             "gleichmäßigere Stimme; höher = lebendiger, aber mit mehr "
             "Artefakten. Ändert nicht, was gesagt wird.",
    ),
    SamplingParam(
        key="subtalker_top_k", label="subtalker_top_k", group="subtalker",
        minimum=1, maximum=100, step=1, integer=True, default=50,
        help="Auswahlbreite für die Feinstruktur. Kleinere Werte glätten "
             "Artefakte, gehen aber auf Kosten der Klangfülle.",
    ),
    SamplingParam(
        key="subtalker_top_p", label="subtalker_top_p", group="subtalker",
        minimum=0.05, maximum=1.0, step=0.05, default=1.0,
        help="Nucleus-Sampling für die Feinstruktur. 1,0 ist der "
             "Checkpoint-Default; absenken vor allem dann, wenn Aufnahmen rau "
             "oder verrauscht klingen.",
    ),
)

SAMPLING_PARAMS: dict[str, SamplingParam] = {p.key: p for p in SAMPLING_SPEC}

#: Die Werte für ein frisch angelegtes Profil. Bewusst ohne max_new_tokens:
#: das Limit ist profilabhängig und wird in DEFAULT_PROFILES gesetzt.
BASE_SAMPLING: dict[str, Any] = {
    p.key: p.default for p in SAMPLING_SPEC if p.default is not None}


def _profile(label: str, instruct: str, max_tokens: int) -> dict[str, Any]:
    return {
        "label": label,
        "speaker": "sohee",
        "language": "german",
        "instruct": instruct,
        "sampling": {**BASE_SAMPLING, "max_new_tokens": max_tokens},
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
            38,  # 3,04 s
        ),
        "phoneme": _profile(
            "Laut / Buchstabe",
            "Sprich ausschließlich den Lautwert des Buchstabens, deutlich gedehnt "
            "und langsam — nicht den Buchstabennamen. Also 'mmmmm', nicht 'Em'. "
            "Kein Satz, kein Zusatz, nur der Laut.",
            25,  # 2,00 s — 48 der 50 validierten Aufnahmen liegen unter 1,0 s
        ),
        "prompt": _profile(
            "Aufgaben-Frage",
            "Sprich wie eine freundliche Kindergärtnerin zu einem fünfjährigen Kind: "
            "warm, deutlich, ruhiges Tempo, leicht fragende Betonung am Satzende. "
            "Freundlich zugewandt, nicht übertrieben fröhlich.",
            125,  # 10,00 s
        ),
        "miss": _profile(
            "Sanftes Feedback",
            "Sprich ruhig und aufmunternd zu einem Kind, das gerade danebenlag. "
            "Kein Tadel, keine Enttäuschung — freundlich erklärend, warm, geduldig.",
            75,  # 6,00 s
        ),
        "reward": _profile(
            "Belohnung",
            "Sprich fröhlich und feiernd zu einem Kind, das etwas geschafft hat. "
            "Lebendig und mit Schwung, aber nicht schrill und nicht zu laut.",
            63,  # 5,04 s
        ),
        "sentence": _profile(
            "Einfacher Satz",
            "Sprich den kurzen Satz klar und einfach, in ruhigem Tempo, "
            "mit natürlicher Satzmelodie. Für ein Kind, das zuhört und mitliest.",
            50,  # 4,00 s
        ),
        "finale": _profile(
            "Lektions-Finale",
            "Sprich den lustigen Satz verspielt und pointiert, mit Schwung und "
            "einem Lächeln in der Stimme. Wie eine kleine Pointe am Ende einer Geschichte.",
            63,  # 5,04 s
        ),
        "ui": _profile(
            "Oberflächen-Ansage",
            "Sprich ruhig, freundlich und neutral. Kurze Ansage, keine Betonung "
            "auf einzelnen Wörtern, kein Drama.",
            75,  # 6,00 s
        ),
    },
}


def read_json(path: Path) -> dict[str, Any] | None:
    """Parse `path`, or return None if it does not exist.

    Every failure is re-raised as a ValueError naming the path: these files are
    hand-edited, so an error that does not say which file is at fault is close
    to useless.
    """
    path = Path(path)
    if not path.exists():
        return None
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError as exc:
        raise ValueError(f"{path} is not valid JSON: {exc}") from exc
    except OSError as exc:
        raise ValueError(f"{path} could not be read: {exc}") from exc


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


def validate_sampling_values(sampling: dict[str, Any], *, name: str = "?",
                             path: Path | None = None) -> None:
    """Check the sampling values that are present against SAMPLING_SPEC.

    Called from `Profile.from_dict`, i.e. on the *read* path. The HTTP handler
    validates writes, but profiles.json is a documented hand-edit surface and
    without this check a hand-typed value reached the model unexamined:

    * `max_new_tokens: 1` against the library's hardcoded `min_new_tokens: 2`
      yields 0–1 codec frames, so every clip of that profile came out as empty
      audio with nothing naming the file.
    * an added `do_sample: false` is invisible in the ⚙️-panel (which renders
      registry keys only), cannot be removed through it, and is still passed on
      by `**profile.sampling` — every seed then produces identical audio and
      the whole seed-pool mechanic collapses.

    An *incomplete* block is explicitly fine: a missing key means "model
    default". Only what is written down is checked. `Lock.from_dict` and
    `build_clips` work the same way, and like them the message names the file,
    the profile and the parameter — these files are edited by hand, so an error
    that does not say what is at fault is close to useless.
    """
    where = f"{path}: " if path is not None else ""
    unknown = sorted(set(sampling) - set(SAMPLING_PARAMS))
    if unknown:
        raise ValueError(
            f"{where}profile {name!r} has unknown sampling parameters: "
            f"{', '.join(unknown)} — allowed: "
            f"{', '.join(sorted(SAMPLING_PARAMS))}")
    for key, spec in SAMPLING_PARAMS.items():
        if key not in sampling:
            continue
        value = sampling[key]
        if value is None:
            if not spec.nullable:
                raise ValueError(
                    f"{where}profile {name!r} has sampling {key!r} set to null "
                    f"— allowed is {spec.minimum} to {spec.maximum}")
            continue
        if isinstance(value, bool) or not isinstance(value, (int, float)):
            raise ValueError(
                f"{where}profile {name!r} has a non-numeric sampling {key!r} "
                f"{value!r}")
        if spec.integer and float(value) != int(value):
            raise ValueError(
                f"{where}profile {name!r} has a fractional sampling {key!r} "
                f"{value!r} — only whole numbers are allowed")
        if not spec.minimum <= value <= spec.maximum:
            raise ValueError(
                f"{where}profile {name!r} has sampling {key!r} at {value!r}, "
                f"outside the allowed range {spec.minimum} to {spec.maximum}")


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
    def from_dict(cls, raw: dict[str, Any], *, name: str = "?",
                  path: Path | None = None) -> "Profile":
        where = f"{path}: " if path is not None else ""
        if not isinstance(raw, dict):
            raise ValueError(f"{where}profile {name!r} must be an object, "
                             f"got {type(raw).__name__}")
        for required in ("label", "speaker", "language", "instruct"):
            if required not in raw:
                raise ValueError(f"{where}profile {name!r} is missing {required!r}")
        raw_sampling = raw.get("sampling", BASE_SAMPLING)
        if not isinstance(raw_sampling, dict):
            raise ValueError(f"{where}profile {name!r}: 'sampling' must be an "
                             f"object, got {type(raw_sampling).__name__}")
        sampling = dict(raw_sampling)
        validate_sampling_values(sampling, name=name, path=path)
        return cls(
            label=raw["label"],
            speaker=raw["speaker"],
            language=raw["language"],
            instruct=raw["instruct"],
            sampling=sampling,
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
        path = Path(path)
        raw = read_json(path)
        # Explicitly `is None`, never truthiness: a truncated file that parses
        # to `{}` must not silently hand back the defaults — that would throw
        # away every curated seed pool without a word.
        if raw is None:
            raw = DEFAULT_PROFILES
        if not isinstance(raw, dict):
            raise ValueError(f"{path} must contain an object, got {type(raw).__name__}")
        if "profiles" not in raw:
            raise ValueError(f"{path} has no 'profiles' key — "
                             f"delete the file to fall back to the defaults")
        entries = raw["profiles"]
        if not isinstance(entries, dict):
            raise ValueError(f"{path}: 'profiles' must be an object, "
                             f"got {type(entries).__name__}")
        return cls(
            pool_salt=raw.get("poolSalt", "v1"),
            profiles={n: Profile.from_dict(p, name=n, path=path)
                      for n, p in entries.items()},
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
    #: Stimme nur für diesen Clip. None heißt „die des Profils" — nicht „keine".
    speaker: str | None = None
    #: Fester Seed für Probeaufnahmen — Generate nutzt nur diesen, solange gesetzt.
    generate_seed: int | None = None

    @classmethod
    def from_dict(cls, raw: dict[str, Any], *, key: str = "?",
                  path: Path | None = None) -> "Lock":
        where = f"{path}: " if path is not None else ""
        if not isinstance(raw, dict):
            raise ValueError(f"{where}lock {key!r} must be an object, "
                             f"got {type(raw).__name__}")
        if "seed" not in raw:
            raise ValueError(f"{where}lock {key!r} is missing 'seed'")
        try:
            seed = int(raw["seed"])
        except (TypeError, ValueError) as exc:
            raise ValueError(f"{where}lock {key!r} has a non-numeric seed "
                             f"{raw['seed']!r}") from exc
        generate_seed = None
        if "generateSeed" in raw and raw["generateSeed"] is not None:
            try:
                generate_seed = parse_seed(raw["generateSeed"])
            except ValueError as exc:
                raise ValueError(
                    f"{where}lock {key!r} has invalid generateSeed: {exc}") from exc
        return cls(
            seed=seed,
            profile=raw.get("profile"),
            text_override=raw.get("textOverride"),
            note=raw.get("note"),
            source_text=raw.get("sourceText"),
            speaker=raw.get("speaker"),
            generate_seed=generate_seed,
        )

    def to_dict(self) -> dict[str, Any]:
        out: dict[str, Any] = {"seed": self.seed}
        for key, value in (("profile", self.profile),
                           ("speaker", self.speaker),
                           ("textOverride", self.text_override),
                           ("note", self.note),
                           ("sourceText", self.source_text),
                           ("generateSeed", self.generate_seed)):
            if value is not None:
                out[key] = value
        return out


@dataclass
class Locks:
    locks: dict[str, Lock] = field(default_factory=dict)
    #: Where these locks came from, so downstream errors can name the file.
    source: Path | None = None

    @classmethod
    def load(cls, path: Path) -> "Locks":
        path = Path(path)
        raw = read_json(path)
        if raw is None:  # explicitly, not truthiness — see Profiles.load
            raw = {"version": 1, "locks": {}}
        if not isinstance(raw, dict):
            raise ValueError(f"{path} must contain an object, got {type(raw).__name__}")
        entries = raw.get("locks", {})
        if not isinstance(entries, dict):
            raise ValueError(f"{path}: 'locks' must be an object, "
                             f"got {type(entries).__name__}")
        return cls({k: Lock.from_dict(v, key=k, path=path) for k, v in entries.items()},
                   source=path)

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
    """Merkt sich pro Clip die letzte Fehlermeldung eines Render-Versuchs.

    `failures` erlaubt `tts status`, einen fehlgeschlagenen Clip von einem nie
    versuchten zu unterscheiden. Es gibt bewusst keine Fingerprint-Ablage mehr:
    ob ein Clip "rendered" ist, entscheidet einzig, ob seine Datei existiert —
    ein Profil-Update darf bereits bestätigten Content nie invalidieren.
    """

    failures: dict[str, str] = field(default_factory=dict)

    @classmethod
    def load(cls, path: Path) -> "RenderState":
        path = Path(path)
        raw = read_json(path)
        if raw is None:  # explicitly, not truthiness — see Profiles.load
            raw = {"version": 1, "failures": {}}
        if not isinstance(raw, dict):
            raise ValueError(f"{path} must contain an object, got {type(raw).__name__}")
        failures = raw.get("failures", {})
        if not isinstance(failures, dict):
            raise ValueError(f"{path}: 'failures' must be an object, "
                             f"got {type(failures).__name__}")
        return cls(dict(failures))

    def save(self, path: Path) -> None:
        _write_json(Path(path), {
            "version": 1,
            "failures": dict(sorted(self.failures.items())),
        })
