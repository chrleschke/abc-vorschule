# TTS-Sampling-Parameter Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das Token-Limit (`max_new_tokens`) und die drei `subtalker_*`-Parameter im TTS-Settings-Screen editierbar machen, mit Erklärungstexten und geprüften Wertebereichen, damit das Modell beim Aussprechen einzelner Buchstaben keine ganzen Sätze mehr unauffällig dazuerfinden kann.

**Architecture:** `BASE_SAMPLING` in `store.py` wird von einem nackten Wert-Dict zu einer deklarativen Registry (`SAMPLING_SPEC`) mit Wertebereich, Typ und Erklärungstext pro Parameter. Der Server liefert diese Registry über `/api/state` aus und prüft eingehende Werte dagegen; das UI rendert das ⚙️-Panel aus der Registry statt aus den Schlüsseln, die ein Profil zufällig schon besitzt. Nur so erscheinen neue Parameter überhaupt an bestehenden Profilen.

**Tech Stack:** Python 3.14, FastAPI, pytest (`tools/tts/`), Vanilla JS ohne Build-Schritt (`ttskit/static/`).

Spec: `docs/superpowers/specs/2026-08-03-tts-sampling-parameters-design.md`

## Global Constraints

- **Arbeitsverzeichnis für alle Kommandos:** `tools/tts/`. Tests laufen mit `~/qwen-tts-test/.venv/bin/python -m pytest`, nie mit dem System-Python — nur das venv hat FastAPI und numpy.
- **1 Token = 80 ms Audio.** Hergeleitet aus `decode_upsample_rate = 1920` bei 24 kHz Ausgabe des 12-Hz-Tokenizers (`qwen_tts/core/tokenizer_12hz/configuration_qwen3_tts_tokenizer_v2.py`): `1920 / 24000 = 0.08`. Der Wert wird **einmal** als `SECONDS_PER_TOKEN = 0.08` in `store.py` definiert und über `/api/state` ans UI geliefert — nirgends im JS hartkodieren.
- **Obergrenze `max_new_tokens` = 8192**, die Untergrenze **2**. 8192 ist der Default aus `generation_config.json` des Checkpoints, 2 entspricht dem in der Library fest verdrahteten `min_new_tokens: 2`.
- **Die Defaults der vier bestehenden Talker-Parameter bleiben unverändert:** `temperature` 0.6, `top_k` 30, `top_p` 0.9, `repetition_penalty` 1.05. Nicht auf die abweichenden Checkpoint-Defaults ziehen — ein neu angelegtes Profil muss wie die bestehenden klingen.
- **`do_sample` und `subtalker_dosample` werden nicht aufgenommen.** Booleans bleiben mit 422 abgelehnt wie bisher.
- **Alle nutzersichtbaren Texte auf Deutsch**, mit typografischen Anführungszeichen („…") wie im Bestand.
- **Keine bestehende Testzusage brechen.** Insbesondere `test_known_sampling_keys_are_merged` prüft `top_k == 30` nach einem `temperature`-Update.

---

### Task 1: Parameter-Registry in `store.py`

**Files:**
- Modify: `tools/tts/ttskit/store.py:16-34` (`BASE_SAMPLING`, `_profile`), `:37-83` (`DEFAULT_PROFILES`)
- Test: `tools/tts/tests/test_store.py`

**Interfaces:**
- Consumes: nichts (erste Aufgabe)
- Produces:
  - `SECONDS_PER_TOKEN: float = 0.08`
  - `MAX_NEW_TOKENS_CEILING: int = 8192`
  - `@dataclass(frozen=True) class SamplingParam` mit den Feldern `key: str`, `label: str`, `group: str`, `minimum: float`, `maximum: float`, `step: float`, `help: str`, `default: float | int | None = None`, `integer: bool = False`, `nullable: bool = False` und der Methode `to_dict() -> dict[str, Any]`
  - `SAMPLING_SPEC: tuple[SamplingParam, ...]` — acht Einträge in der Reihenfolge `max_new_tokens`, `temperature`, `top_k`, `top_p`, `repetition_penalty`, `subtalker_temperature`, `subtalker_top_k`, `subtalker_top_p`
  - `SAMPLING_PARAMS: dict[str, SamplingParam]` — Nachschlagetabelle `key → SamplingParam`
  - `BASE_SAMPLING: dict[str, Any]` — abgeleitet, enthält die sieben Parameter mit `default is not None` (also alles außer `max_new_tokens`)
  - `_profile(label: str, instruct: str, max_tokens: int) -> dict[str, Any]`

- [ ] **Step 1: Write the failing test**

In `tools/tts/tests/test_store.py` erst die bestehende Import-Zeile 7 erweitern (keine zweite Import-Anweisung anhängen):

```python
from ttskit.store import (
    BASE_SAMPLING, DEFAULT_PROFILES, MAX_NEW_TOKENS_CEILING, SAMPLING_PARAMS,
    SAMPLING_SPEC, SECONDS_PER_TOKEN, Lock, Locks, Profiles, RenderState,
)
```

Dann die Tests anhängen:

```python
def test_seconds_per_token_matches_the_12hz_tokenizer():
    # 1920 / 24000 — decode_upsample_rate bei 24 kHz Ausgabe.
    assert SECONDS_PER_TOKEN == 0.08


def test_base_sampling_keeps_the_four_established_defaults():
    # Diese Werte klingen in der App; ein neues Profil muss wie die
    # bestehenden klingen, nicht wie die Checkpoint-Defaults (0.9/50/1.0).
    assert BASE_SAMPLING["temperature"] == 0.6
    assert BASE_SAMPLING["top_k"] == 30
    assert BASE_SAMPLING["top_p"] == 0.9
    assert BASE_SAMPLING["repetition_penalty"] == 1.05


def test_base_sampling_adds_the_subtalker_defaults_from_the_checkpoint():
    assert BASE_SAMPLING["subtalker_temperature"] == 0.9
    assert BASE_SAMPLING["subtalker_top_k"] == 50
    assert BASE_SAMPLING["subtalker_top_p"] == 1.0


def test_base_sampling_omits_max_new_tokens():
    # Das Limit ist profilabhängig. Ein fehlender Schlüssel heißt
    # "unbegrenzt" — es darf hier keinen globalen Default geben.
    assert "max_new_tokens" not in BASE_SAMPLING


def test_base_sampling_is_derived_from_the_registry():
    assert BASE_SAMPLING == {
        p.key: p.default for p in SAMPLING_SPEC if p.default is not None}


def test_registry_covers_exactly_the_eight_supported_parameters():
    assert [p.key for p in SAMPLING_SPEC] == [
        "max_new_tokens", "temperature", "top_k", "top_p", "repetition_penalty",
        "subtalker_temperature", "subtalker_top_k", "subtalker_top_p",
    ]
    assert set(SAMPLING_PARAMS) == {p.key for p in SAMPLING_SPEC}


def test_no_boolean_parameters_are_exposed():
    # do_sample und subtalker_dosample sind bewusst draußen: greedy
    # Generierung macht den Seed wirkungslos und bricht die Kuratierung.
    assert "do_sample" not in SAMPLING_PARAMS
    assert "subtalker_dosample" not in SAMPLING_PARAMS


def test_every_parameter_carries_a_range_and_a_german_explanation():
    for param in SAMPLING_SPEC:
        assert param.minimum < param.maximum, param.key
        assert param.step > 0, param.key
        assert len(param.help) > 60, f"{param.key} braucht einen echten Erklärungstext"
        assert param.group in {"duration", "talker", "subtalker"}, param.key
        if param.default is not None:
            assert param.minimum <= param.default <= param.maximum, param.key


def test_max_new_tokens_is_the_only_nullable_and_bounded_by_the_checkpoint():
    spec = SAMPLING_PARAMS["max_new_tokens"]
    assert spec.nullable is True
    assert spec.integer is True
    assert spec.default is None
    assert spec.minimum == 2, "min_new_tokens: 2 ist in der Library fest verdrahtet"
    assert spec.maximum == MAX_NEW_TOKENS_CEILING == 8192
    assert [p.key for p in SAMPLING_SPEC if p.nullable] == ["max_new_tokens"]


def test_the_two_top_k_parameters_are_integer_only():
    assert [p.key for p in SAMPLING_SPEC if p.integer] == [
        "max_new_tokens", "top_k", "subtalker_top_k"]


def test_param_to_dict_carries_everything_the_ui_needs():
    payload = SAMPLING_PARAMS["temperature"].to_dict()
    assert set(payload) == {"key", "label", "group", "minimum", "maximum",
                            "step", "help", "default", "integer", "nullable"}
    assert payload["key"] == "temperature"


def test_default_profiles_carry_the_measured_duration_limits():
    # Abgeleitet aus den Dauern der validierten Aufnahmen, siehe Spec.
    expected = {"phoneme": 25, "word": 38, "sentence": 50, "finale": 63,
                "prompt": 125, "miss": 75, "reward": 63, "ui": 75}
    actual = {name: profile["sampling"]["max_new_tokens"]
              for name, profile in DEFAULT_PROFILES["profiles"].items()}
    assert actual == expected


def test_default_profiles_also_carry_the_subtalker_parameters(tmp_path):
    profiles = Profiles.load(tmp_path / "absent.json")
    for name, profile in profiles.profiles.items():
        assert profile.sampling["subtalker_temperature"] == 0.9, name
        assert profile.sampling["subtalker_top_k"] == 50, name
        assert profile.sampling["subtalker_top_p"] == 1.0, name


def test_a_profile_without_max_new_tokens_still_loads(tmp_path):
    """Bestandsschutz: eine handgepflegte Datei ohne den neuen Schlüssel.

    Ein fehlender Schlüssel ist kein Fehler, sondern heißt „unbegrenzt" —
    generate_custom_voice nimmt dann den Checkpoint-Default. Ohne diese
    Zusage könnte eine strengere Prüfung später jede älteren Datei brechen.
    """
    path = tmp_path / "profiles.json"
    path.write_text(json.dumps({
        "poolSalt": "v1",
        "profiles": {"word": {
            "label": "Einzelwort", "speaker": "sohee", "language": "german",
            "instruct": "Sprich das Wort.",
            "sampling": {"temperature": 0.6, "top_k": 30},
        }},
    }), encoding="utf-8")

    profile = Profiles.load(path).profiles["word"]
    assert "max_new_tokens" not in profile.sampling
    assert profile.sampling == {"temperature": 0.6, "top_k": 30}
```

`json` ist in `tests/test_store.py:1` bereits importiert.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_store.py -v
```

Erwartet: `ImportError: cannot import name 'SAMPLING_SPEC' from 'ttskit.store'` — die Datei bricht schon beim Import ab.

- [ ] **Step 3: Registry implementieren**

In `tools/tts/ttskit/store.py` den Block `BASE_SAMPLING = {...}` bis einschließlich `_profile(...)` (Zeilen 16–34) ersetzen durch:

```python
#: Ein Codec-Token des Talkers entspricht so vielen Sekunden Audio.
#: Hergeleitet aus dem 12-Hz-Tokenizer: decode_upsample_rate 1920 bei
#: 24 kHz Ausgabe, also 1920 / 24000. Steht hier und nur hier — das UI
#: bekommt den Wert über /api/state, damit die Kopplung an genau einer
#: Stelle gepflegt wird.
SECONDS_PER_TOKEN = 0.08

#: Obergrenze für max_new_tokens: der Default aus generation_config.json
#: des Checkpoints. Mehr anzubieten wäre unbelegt.
MAX_NEW_TOKENS_CEILING = 8192


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
```

- [ ] **Step 4: Die Limits in `DEFAULT_PROFILES` eintragen**

In `tools/tts/ttskit/store.py` jedem der acht `_profile(...)`-Aufrufe in `DEFAULT_PROFILES` das dritte Argument geben. Die Instruktionstexte bleiben unverändert; nur die Aufrufe bekommen den Tokenwert plus einen Kommentar mit der angezeigten Sekundenzahl:

```python
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
```

Und ebenso für die übrigen sechs: `prompt` → `125,  # 10,00 s`, `miss` → `75,  # 6,00 s`, `reward` → `63,  # 5,04 s`, `sentence` → `50,  # 4,00 s`, `finale` → `63,  # 5,04 s`, `ui` → `75,  # 6,00 s`.

- [ ] **Step 5: Die Durchreichung ans Modell absichern**

Ohne diesen Test ist die ganze Aufgabe wirkungslos, falls `**profile.sampling` je umgebaut wird: das Limit stünde in der Datei, käme aber nie am Modell an. An `tools/tts/tests/test_engine.py` anhängen:

```python
def test_generate_forwards_max_new_tokens_to_the_model():
    """Das Limit muss den Weg bis in generate_custom_voice finden.

    Die Zusage hängt an engine.generate's `**profile.sampling`. Wird die je
    auf eine feste Parameterliste umgebaut, fällt max_new_tokens still weg —
    das Limit stünde dann in profiles.json und wirkte trotzdem nicht.
    """
    class CapturingModel:
        def __init__(self):
            self.kwargs = None

        def generate_custom_voice(self, **kwargs):
            self.kwargs = kwargs
            return [np.zeros(240, dtype=np.float32)], 24000

    model = CapturingModel()
    engine = Engine()
    engine._model = model
    engine.loaded = True

    profile = Profiles.load(Path("nope.json")).profiles["phoneme"]
    engine.generate("M", profile, seed=7)

    assert model.kwargs["max_new_tokens"] == 25
    assert model.kwargs["subtalker_temperature"] == 0.9
    assert model.kwargs["temperature"] == 0.6
```

`numpy as np`, `Path`, `Engine` und `Profiles` sind in `tests/test_engine.py:1-8` bereits importiert.

- [ ] **Step 6: Run the tests to verify they pass**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_store.py tests/test_engine.py -v
```

Erwartet: PASS. Die mit `TTS_SMOKE` markierten Tests in `test_engine.py` bleiben übersprungen — der neue Test braucht kein Modell, er stellt ein aufzeichnendes Fake hin.

- [ ] **Step 7: Die gesamte Suite laufen lassen**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest -q
```

Erwartet: alles grün. `test_known_sampling_keys_are_merged` in `test_server.py` prüft `top_k == 30` — das muss weiter halten. Bricht etwas anderes, ist es ein echter Regressionsfund und **nicht** durch Anpassen der Zusage zu „lösen", sondern zu melden.

- [ ] **Step 8: Commit**

```bash
git add tools/tts/ttskit/store.py tools/tts/tests/test_store.py tools/tts/tests/test_engine.py
git commit -m "feat(tts): Sampling-Parameter als deklarative Registry

BASE_SAMPLING war ein nacktes Wert-Dict — das UI konnte nur Schlüssel
zeigen, die in einem Profil schon standen, und geprüft wurde nur \"ist
eine Zahl\". SAMPLING_SPEC traegt jetzt Wertebereich, Typ und deutschen
Erklaerungstext pro Parameter und ergaenzt max_new_tokens sowie die drei
subtalker_*.

Die Dauer-Limits pro Profil sind aus den Dauern der validierten
Aufnahmen abgeleitet, nicht aus allen Dateien: bei phoneme und sentence
liegen die verworfenen 3,5-4x ueber den bestaetigten.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Registry über `/api/state` ausliefern

**Files:**
- Modify: `tools/tts/ttskit/server.py:33` (Import), `:207-224` (`api_state`)
- Test: `tools/tts/tests/test_server.py`

**Interfaces:**
- Consumes: `SAMPLING_SPEC`, `SECONDS_PER_TOKEN` aus `ttskit.store` (Task 1)
- Produces: `/api/state` liefert zusätzlich `samplingSpec: list[dict]` (die acht `to_dict()`-Einträge in Registry-Reihenfolge) und `secondsPerToken: float`

- [ ] **Step 1: Write the failing test**

An `tools/tts/tests/test_server.py` anhängen:

```python
def test_state_ships_the_sampling_registry(client):
    body = client.get("/api/state").json()
    spec = body["samplingSpec"]
    assert [p["key"] for p in spec] == [
        "max_new_tokens", "temperature", "top_k", "top_p", "repetition_penalty",
        "subtalker_temperature", "subtalker_top_k", "subtalker_top_p",
    ]
    # Das UI rendert aus dieser Liste, nicht aus den Schlüsseln eines
    # Profils — sonst bleibt ein neuer Parameter an bestehenden Profilen
    # unsichtbar. Also muss jeder Eintrag alles Nötige tragen.
    for param in spec:
        assert set(param) == {"key", "label", "group", "minimum", "maximum",
                              "step", "help", "default", "integer", "nullable"}
        assert param["help"], param["key"]
        assert param["group"] in {"duration", "talker", "subtalker"}


def test_state_ships_the_seconds_per_token_factor(client):
    # Damit das JS die 80 ms nicht hartkodiert.
    assert client.get("/api/state").json()["secondsPerToken"] == 0.08
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_server.py -k "sampling_registry or seconds_per_token" -v
```

Erwartet: FAIL mit `KeyError: 'samplingSpec'`.

- [ ] **Step 3: Implementieren**

In `tools/tts/ttskit/server.py` den Import in Zeile 33 erweitern:

```python
from .store import (
    BASE_SAMPLING, SAMPLING_PARAMS, SAMPLING_SPEC, SECONDS_PER_TOKEN,
    Lock, Locks, Profiles,
)
```

Im Rückgabe-Dict von `api_state` direkt nach der `"limits"`-Zeile ergänzen:

```python
            "limits": {"maxCandidates": MAX_CANDIDATES},
            # Das ⚙️-Panel rendert aus dieser Deklaration statt aus den
            # Schlüsseln, die ein Profil zufällig schon besitzt.
            "samplingSpec": [p.to_dict() for p in SAMPLING_SPEC],
            "secondsPerToken": SECONDS_PER_TOKEN,
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_server.py -k "sampling_registry or seconds_per_token" -v
```

Erwartet: PASS.

- [ ] **Step 5: Commit**

```bash
git add tools/tts/ttskit/server.py tools/tts/tests/test_server.py
git commit -m "feat(tts): /api/state liefert die Sampling-Registry

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: Bereichsprüfung und Löschen per `null`

**Files:**
- Modify: `tools/tts/ttskit/server.py:251-273` (der `"sampling" in body`-Block in `api_update_profile`)
- Test: `tools/tts/tests/test_server.py`

**Interfaces:**
- Consumes: `SAMPLING_PARAMS` aus `ttskit.store` (Task 1), der erweiterte Import aus Task 2
- Produces: `PUT /api/profiles/{name}` prüft jeden Sampling-Wert gegen `minimum`/`maximum`/`integer` und löscht bei `null` den Schlüssel, wenn der Parameter `nullable` ist

- [ ] **Step 1: Write the failing tests**

An `tools/tts/tests/test_server.py` anhängen:

```python
def test_a_value_below_the_minimum_is_rejected(client):
    response = client.put("/api/profiles/word", json={"sampling": {"top_p": 0.0}})
    assert response.status_code == 422
    detail = response.json()["detail"]
    assert "top_p" in detail
    # Die Meldung muss den erlaubten Bereich nennen — sie landet unverändert
    # als Banner im UI.
    assert "0.05" in detail and "1.0" in detail


def test_a_value_above_the_maximum_is_rejected(client):
    response = client.put("/api/profiles/word",
                          json={"sampling": {"temperature": 50}})
    assert response.status_code == 422
    assert "temperature" in response.json()["detail"]


def test_max_new_tokens_above_the_checkpoint_ceiling_is_rejected(client):
    response = client.put("/api/profiles/word",
                          json={"sampling": {"max_new_tokens": 9000}})
    assert response.status_code == 422
    assert "8192" in response.json()["detail"]


def test_a_fractional_value_for_an_integer_parameter_is_rejected(client):
    response = client.put("/api/profiles/word", json={"sampling": {"top_k": 30.5}})
    assert response.status_code == 422
    assert "Ganzzahl" in response.json()["detail"]


def test_an_integer_parameter_is_stored_as_an_int(client):
    # 40.0 kommt aus JSON als float an; in der git-verwalteten Datei soll
    # kein "40.0" stehen.
    assert client.put("/api/profiles/word",
                      json={"sampling": {"max_new_tokens": 40.0}}).status_code == 200
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    stored = raw["profiles"]["word"]["sampling"]["max_new_tokens"]
    assert stored == 40 and isinstance(stored, int)


def test_null_deletes_max_new_tokens(client):
    assert client.put("/api/profiles/word",
                      json={"sampling": {"max_new_tokens": 40}}).status_code == 200
    assert client.put("/api/profiles/word",
                      json={"sampling": {"max_new_tokens": None}}).status_code == 200
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    sampling = raw["profiles"]["word"]["sampling"]
    assert "max_new_tokens" not in sampling, "leeres Feld heißt unbegrenzt"
    assert sampling["temperature"] == 0.6, "die übrigen Werte bleiben stehen"


def test_null_for_a_non_nullable_parameter_is_rejected(client):
    response = client.put("/api/profiles/word",
                          json={"sampling": {"temperature": None}})
    assert response.status_code == 422
    assert "temperature" in response.json()["detail"]


def test_the_subtalker_parameters_are_accepted(client):
    assert client.put("/api/profiles/word", json={"sampling": {
        "subtalker_temperature": 0.7, "subtalker_top_k": 40,
        "subtalker_top_p": 0.95,
    }}).status_code == 200
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    sampling = raw["profiles"]["word"]["sampling"]
    assert sampling["subtalker_temperature"] == 0.7
    assert sampling["subtalker_top_k"] == 40
    assert sampling["subtalker_top_p"] == 0.95


def test_the_two_sampling_booleans_stay_rejected(client):
    # do_sample: false macht die Generierung greedy — der Seed wird
    # wirkungslos und die ganze Kuratierung bricht.
    for key in ("do_sample", "subtalker_dosample"):
        response = client.put("/api/profiles/word", json={"sampling": {key: True}})
        assert response.status_code == 422, key


def test_one_bad_value_persists_nothing_at_all(client):
    # Ein Sammel-Save aus dem Panel schickt alle Parameter zusammen. Wird
    # einer abgelehnt, darf keiner der anderen durchrutschen.
    response = client.put("/api/profiles/word", json={
        "instruct": "Darf nicht gespeichert werden.",
        "sampling": {"temperature": 0.8, "top_p": 99},
    })
    assert response.status_code == 422
    assert not client.paths.profiles.exists(), "nichts darf geschrieben worden sein"
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_server.py -k "minimum or maximum or ceiling or fractional or integer_parameter or null or subtalker or booleans or bad_value" -v
```

Erwartet: mehrere FAILs — `top_p: 0.0` und `temperature: 50` kommen heute mit 200 durch, `None` wird mit der falschen Begründung („muss eine Zahl sein") abgelehnt.

- [ ] **Step 3: Implementieren**

In `tools/tts/ttskit/server.py` den Block ab `if "sampling" in body:` (Zeile 251) bis `profile.sampling.update(sampling)` (Zeile 273) ersetzen durch:

```python
        if "sampling" in body:
            sampling = body["sampling"]
            if not isinstance(sampling, dict):
                raise HTTPException(
                    status_code=422,
                    detail=f"'sampling' muss ein Objekt sein, nicht "
                           f"{type(sampling).__name__}")
            # Whitelist: an unknown key would reach
            # generate_custom_voice(**sampling) as a TypeError on every future
            # render of this profile.
            unknown = sorted(set(sampling) - set(SAMPLING_PARAMS))
            if unknown:
                raise HTTPException(
                    status_code=422,
                    detail=f"unbekannte Sampling-Parameter: {', '.join(unknown)}. "
                           f"Erlaubt: {', '.join(sorted(SAMPLING_PARAMS))}")
            # Erst alles prüfen, dann alles anwenden. Das ⚙️-Panel schickt
            # sämtliche Parameter in einem Save; würde mitten in der Schleife
            # geschrieben, hinterließe ein einziger schlechter Wert ein halb
            # aktualisiertes Profil im Speicher.
            for param, value in sampling.items():
                spec = SAMPLING_PARAMS[param]
                if value is None:
                    if not spec.nullable:
                        raise HTTPException(
                            status_code=422,
                            detail=f"Sampling-Parameter {param!r} darf nicht leer "
                                   f"sein — erlaubt ist {spec.minimum} bis "
                                   f"{spec.maximum}")
                    continue
                if isinstance(value, bool) or not isinstance(value, (int, float)):
                    raise HTTPException(
                        status_code=422,
                        detail=f"Sampling-Parameter {param!r} muss eine Zahl sein, "
                               f"nicht {type(value).__name__}")
                if spec.integer and float(value) != int(value):
                    raise HTTPException(
                        status_code=422,
                        detail=f"Sampling-Parameter {param!r} muss eine Ganzzahl "
                               f"sein, nicht {value}")
                # Ungeprüft landete ein top_p von 3 oder eine temperature von
                # 50 in der git-verwalteten profiles.json und erzeugte danach
                # still unbrauchbare Audios.
                if not spec.minimum <= value <= spec.maximum:
                    raise HTTPException(
                        status_code=422,
                        detail=f"Sampling-Parameter {param!r} liegt mit {value} "
                               f"außerhalb des erlaubten Bereichs "
                               f"{spec.minimum} bis {spec.maximum}")
            for param, value in sampling.items():
                if value is None:
                    # Fehlender Schlüssel heißt „Modell-Default", bei
                    # max_new_tokens also unbegrenzt.
                    profile.sampling.pop(param, None)
                elif SAMPLING_PARAMS[param].integer:
                    profile.sampling[param] = int(value)
                else:
                    profile.sampling[param] = value
```

`BASE_SAMPLING` wird in `server.py` danach nicht mehr benutzt — den Namen aus dem Import in Zeile 33 entfernen, sonst meckert der Linter.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_server.py -v
```

Erwartet: PASS, inklusive der bestehenden `test_unknown_sampling_keys_are_rejected`, `test_known_sampling_keys_are_merged` und `test_non_numeric_sampling_values_are_rejected`.

- [ ] **Step 5: Die gesamte Suite laufen lassen**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest -q
```

Erwartet: alles grün.

- [ ] **Step 6: Commit**

```bash
git add tools/tts/ttskit/server.py tools/tts/tests/test_server.py
git commit -m "feat(tts): Sampling-Werte werden gegen ihren Wertebereich geprueft

Bisher galt nur \"ist eine Zahl\" — ein top_p von 3 oder eine temperature
von 50 landete in der git-verwalteten profiles.json und erzeugte danach
still unbrauchbare Audios. Dazu: null loescht max_new_tokens wieder
(= unbegrenzt), und Ganzzahl-Parameter werden als int gespeichert.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Die ausgelieferte `profiles.json` nachziehen

**Files:**
- Modify: `tools/tts/profiles.json`
- Test: `tools/tts/tests/test_store.py`

**Interfaces:**
- Consumes: `SAMPLING_PARAMS`, `Profiles` aus `ttskit.store` (Task 1)
- Produces: keine neuen Symbole — die real ausgelieferte Datei trägt für alle acht Profile `max_new_tokens` und die drei `subtalker_*`

**Warum eine eigene Aufgabe:** `Profile.from_dict` greift auf `BASE_SAMPLING` nur zurück, wenn `sampling` **ganz** fehlt (`store.py:157`). Die vorhandene `profiles.json` hat pro Profil ein `sampling` mit genau vier Schlüsseln — die neuen Parameter erscheinen darin also nie von allein. Task 1 ändert nur die Defaults für einen Neuaufsatz.

- [ ] **Step 1: Write the failing test**

An `tools/tts/tests/test_store.py` anhängen:

```python
def test_the_shipped_profiles_json_carries_the_new_parameters():
    """Prüft die echte ausgelieferte Datei, nicht die Defaults.

    Profile.from_dict nimmt BASE_SAMPLING nur, wenn 'sampling' ganz fehlt.
    Die vorhandene profiles.json hat pro Profil vier Schlüssel — ohne diese
    Prüfung erbt sie die neuen Parameter nie und das ⚙️-Panel zeigte
    Felder, die in der Datei fehlen.
    """
    from ttskit.paths import Paths

    profiles = Profiles.load(Paths().profiles)
    expected = {"phoneme": 25, "word": 38, "sentence": 50, "finale": 63,
                "prompt": 125, "miss": 75, "reward": 63, "ui": 75}
    assert set(profiles.profiles) == set(expected)
    for name, profile in profiles.profiles.items():
        sampling = profile.sampling
        assert sampling["max_new_tokens"] == expected[name], name
        assert sampling["subtalker_temperature"] == 0.9, name
        assert sampling["subtalker_top_k"] == 50, name
        assert sampling["subtalker_top_p"] == 1.0, name
        # Die bestehenden Werte dürfen sich nicht mitverändert haben.
        assert sampling["temperature"] == 0.6, name
        assert sampling["top_k"] == 30, name
        assert sampling["top_p"] == 0.9, name
        assert sampling["repetition_penalty"] == 1.05, name


def test_every_shipped_value_is_inside_its_declared_range():
    """Die Datei ist handgepflegt — sie muss die eigene Prüfung überleben."""
    from ttskit.paths import Paths

    for name, profile in Profiles.load(Paths().profiles).profiles.items():
        for key, value in profile.sampling.items():
            spec = SAMPLING_PARAMS[key]
            assert spec.minimum <= value <= spec.maximum, f"{name}.{key} = {value}"
            if spec.integer:
                assert float(value) == int(value), f"{name}.{key} = {value}"
```

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_store.py -k "shipped" -v
```

Erwartet: FAIL mit `KeyError: 'max_new_tokens'`.

- [ ] **Step 3: Die Datei aktualisieren**

Nicht von Hand editieren — die Datei trägt 37 kuratierte Seeds in ihren `seedPool`-Listen, die keinesfalls verloren gehen dürfen. Stattdessen dieses Skript einmalig laufen lassen; es geht über den atomaren Schreibpfad von `Profiles.save` und lässt alle übrigen Felder unberührt:

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python - <<'PY'
from ttskit.paths import Paths
from ttskit.store import BASE_SAMPLING, Profiles

LIMITS = {"phoneme": 25, "word": 38, "sentence": 50, "finale": 63,
          "prompt": 125, "miss": 75, "reward": 63, "ui": 75}
SUBTALKER = {k: v for k, v in BASE_SAMPLING.items() if k.startswith("subtalker_")}
assert len(SUBTALKER) == 3, SUBTALKER

paths = Paths()
profiles = Profiles.load(paths.profiles)
assert set(profiles.profiles) == set(LIMITS), sorted(profiles.profiles)
for name, profile in profiles.profiles.items():
    pools_before = list(profile.seed_pool)
    profile.sampling.update(SUBTALKER)
    profile.sampling["max_new_tokens"] = LIMITS[name]
    assert profile.seed_pool == pools_before, name
profiles.save(paths.profiles)
print("aktualisiert:", ", ".join(f"{n}={LIMITS[n]}" for n in sorted(LIMITS)))
PY
```

- [ ] **Step 4: Prüfen, dass die kuratierten Seed-Pools unangetastet sind**

```bash
cd tools/tts && git diff --stat profiles.json && git diff profiles.json | grep -c '^[-+].*seedPool\|^[-+][0-9 ]*[0-9],$'
```

Erwartet: `profiles.json` ist geändert, und die zweite Zahl ist `0` — keine einzige Zeile innerhalb einer `seedPool`-Liste wurde angefasst. Ist sie nicht 0: `git checkout profiles.json` und die Ursache klären, bevor es weitergeht. `prompt` muss danach weiter 21 und `sentence` weiter 16 Seeds haben:

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -c "
import json; d=json.load(open('profiles.json'))['profiles']
print({n: len(p['seedPool']) for n, p in d.items()})"
```

Erwartet: `prompt` 21, `sentence` 16, alle übrigen 0.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest -q
```

Erwartet: alles grün.

- [ ] **Step 6: Commit**

```bash
git add tools/tts/profiles.json tools/tts/tests/test_store.py
git commit -m "feat(tts): Dauer-Limits und Sub-Talker-Werte in profiles.json

Profile.from_dict nimmt BASE_SAMPLING nur, wenn 'sampling' ganz fehlt —
die ausgelieferte Datei erbt neue Parameter also nie von allein. Ein Test
prueft deshalb die echte Datei, nicht nur die Defaults.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: Das ⚙️-Panel aus der Registry rendern

**Files:**
- Modify: `tools/tts/ttskit/static/app.js:304-365` (`SAMPLING_HINTS`, `profileFormHtml`, `readProfileForm`), `:1-10` (`state`)
- Modify: `tools/tts/ttskit/static/style.css:178-180` (`.params-grid`, `.param`)

**Interfaces:**
- Consumes: `samplingSpec` und `secondsPerToken` aus `/api/state` (Task 2); die Bereichsprüfung des Servers (Task 3) als zweite Verteidigungslinie
- Produces:
  - `state.samplingSpec: Array` und `state.secondsPerToken: number`
  - `specFor(key) -> object|undefined`
  - `paramFieldHtml(profile, spec) -> string`
  - `readProfileForm(container)` liefert in `sampling` für jeden deklarierten Parameter einen Zahlenwert oder `null`

**Kein JS-Test-Harness:** `tools/tts/tests/` enthält nur Python-Tests, und `app.js` ist ein einzelnes Skript ohne Modulsystem. Diese Aufgabe wird deshalb am laufenden Server im Browser verifiziert (Schritte 5–7). Das ist keine Nachlässigkeit, sondern der Verifikationsweg, den dieses Frontend hat.

- [ ] **Step 1: `state` um die Registry erweitern**

In `tools/tts/ttskit/static/app.js` Zeile 6 (`voices: [], languages: [], paramsOpen: false,`) ersetzen durch:

```js
  voices: [], languages: [], paramsOpen: false,
  samplingSpec: [], secondsPerToken: 0.08,
```

Der `0.08`-Startwert wird beim ersten `/api/state` überschrieben; er verhindert nur eine Division durch `undefined`, falls ein Render vor dem ersten Refresh läuft.

- [ ] **Step 2: `SAMPLING_HINTS` durch Registry-Zugriff ersetzen**

In `app.js` den Block `const SAMPLING_HINTS = {...};` (Zeilen 304–309) ersetzen durch:

```js
// Die Erklärungstexte, Wertebereiche und Typen kommen aus der Registry im
// Server (store.SAMPLING_SPEC), nicht aus einer zweiten Liste hier. Vorher
// rendete das Panel aus Object.keys(profile.sampling) — ein Parameter, der
// in profiles.json noch nicht stand, war damit unsichtbar.
const GROUP_LABELS = {
  duration: "Maximale Dauer",
  talker: "Sampling — Haupt-Talker (was gesagt wird)",
  subtalker: "Feinstruktur — Sub-Talker (wie es klingt)",
};

const specFor = (key) => state.samplingSpec.find((p) => p.key === key);

const tokensToSeconds = (tokens) =>
  (Number(tokens) * state.secondsPerToken).toFixed(2);

// Ein Feld pro deklariertem Parameter. Die Dauer wird in Sekunden
// eingegeben und in Tokens gespeichert — data-unit markiert das für
// readProfileForm.
function paramFieldHtml(profile, spec) {
  const stored = profile.sampling[spec.key];
  const missing = stored === undefined || stored === null;
  const seconds = spec.group === "duration";
  const value = missing ? "" : (seconds ? tokensToSeconds(stored) : stored);
  const attrs = seconds
    ? `data-unit="seconds" step="0.1" min="${tokensToSeconds(spec.minimum)}" ` +
      `max="${tokensToSeconds(spec.maximum)}" placeholder="unbegrenzt"`
    : `step="${spec.step}" min="${spec.minimum}" max="${spec.maximum}"`;
  const range = seconds
    ? `${tokensToSeconds(spec.minimum)}–${tokensToSeconds(spec.maximum)} s`
    : `${spec.minimum}–${spec.maximum}`;
  const suffix = seconds
    ? `<span class="muted small" data-tokens-for="${spec.key}">${
        missing ? "unbegrenzt" : `= ${stored} Tokens`}</span>`
    : "";
  return `
    <div class="param-row">
      <label class="param">
        <span>${spec.label}${seconds ? " (Sekunden, vor Trim)" : ""}</span>
        <input type="number" data-param="${spec.key}" ${attrs}
               value="${value}" />
        ${suffix}
      </label>
      <p class="param-help muted small">${escapeHtml(spec.help)}
        <b>Bereich ${range}.</b>${
          spec.default === null ? "" : ` Voreinstellung ${spec.default}.`}</p>
    </div>`;
}
```

- [ ] **Step 3: `profileFormHtml` auf Gruppen umstellen**

In `app.js` in `profileFormHtml` den Block `const sampling = Object.keys(profile.sampling).sort().map(...)` (Zeilen 313–318) ersetzen durch:

```js
  const groups = ["duration", "talker", "subtalker"].map((group) => {
    const fields = state.samplingSpec.filter((p) => p.group === group);
    if (fields.length === 0) return "";
    return `
      <fieldset class="param-group">
        <legend>${GROUP_LABELS[group]}</legend>
        ${fields.map((spec) => paramFieldHtml(profile, spec)).join("")}
      </fieldset>`;
  }).join("");
```

und im zurückgegebenen Template `<div class="params-grid">${sampling}</div>` (Zeile 329) ersetzen durch:

```js
    ${groups}
```

- [ ] **Step 4: `readProfileForm` auf die Registry stützen**

In `app.js` den `sampling`-Block in `readProfileForm` (Zeilen 347–356) ersetzen durch:

```js
  const sampling = {};
  container.querySelectorAll("[data-param]").forEach((input) => {
    const key = input.dataset.param;
    const spec = specFor(key);
    const raw = input.value.trim();
    if (raw === "") {
      // Number("") ist 0 — ein geleertes Feld darf nicht stillschweigend
      // als 0 gespeichert werden. Bei der Dauer heißt leer „unbegrenzt",
      // was der Server als null-Löschung entgegennimmt.
      if (!spec.nullable) {
        throw new Error(`„${spec.label}“ ist leer oder keine Zahl`);
      }
      sampling[key] = null;
      return;
    }
    const entered = Number(raw);
    if (Number.isNaN(entered)) {
      throw new Error(`„${spec.label}“ ist leer oder keine Zahl`);
    }
    // Die Dauer wird in Sekunden eingegeben, gespeichert werden Tokens.
    const value = input.dataset.unit === "seconds"
      ? Math.round(entered / state.secondsPerToken)
      : entered;
    if (value < spec.minimum || value > spec.maximum) {
      const shown = input.dataset.unit === "seconds"
        ? `${tokensToSeconds(spec.minimum)}–${tokensToSeconds(spec.maximum)} s`
        : `${spec.minimum}–${spec.maximum}`;
      throw new Error(`„${spec.label}“ muss zwischen ${shown} liegen`);
    }
    sampling[key] = spec.integer ? Math.round(value) : value;
  });
```

- [ ] **Step 5: Styles ergänzen**

In `tools/tts/ttskit/static/style.css` die Zeilen 178–180 (`.params-grid`, `.param`, `.param span`) ersetzen durch:

```css
.param-group { border: 1px solid var(--line); border-radius: 6px;
  padding: 8px 12px 4px; margin: 10px 0; }
.param-group legend { font-size: 12px; color: var(--muted); padding: 0 4px; }
.param-row { margin-bottom: 8px; }
.param { display: flex; align-items: center; gap: 8px; }
.param span { font-size: 13px; color: var(--muted); min-width: 220px; }
.param input { width: 90px; }
.param-help { margin: 2px 0 0 228px; line-height: 1.4; }
```

`--line` (`#e6ded2`) und `--muted` (`#857a6c`) sind in `style.css:2-3` im `:root`-Block definiert und im Bestand für Rahmen und Hilfstexte in Gebrauch — sie sind hier bewusst wiederverwendet statt neuer Festwerte.

- [ ] **Step 6: Server starten und das Panel öffnen**

```bash
./start-tts-ui.sh
```

Läuft im Vordergrund und lädt zuerst das Modell — das dauert. Ein Ladefehler ist unkritisch: `Engine.load()` fängt ihn ab, der Server startet trotzdem, und das ⚙️-Panel braucht kein Modell.

Dann `http://127.0.0.1:8420` öffnen, auf „⚙️ TTS-Parameter" klicken und für die Karte `phoneme` prüfen:

- Drei Gruppen mit Überschriften „Maximale Dauer", „Sampling — Haupt-Talker …", „Feinstruktur — Sub-Talker …".
- Acht Felder insgesamt. Die drei `subtalker_*` sind da, obwohl sie vor Task 4 nicht in `profiles.json` standen — das ist der eigentliche Beweis, dass aus der Registry gerendert wird.
- Unter jedem Feld eine Erklärungszeile mit „Bereich …" am Ende.
- Das Dauer-Feld zeigt `2.00`, daneben „= 25 Tokens".

- [ ] **Step 7: Speichern und Bereichsprüfung im Browser prüfen**

Immer noch in der `phoneme`-Karte:

1. `temperature` auf `99` setzen, „Speichern" — es muss ein rotes Banner erscheinen, das `temperature` und den Bereich nennt, und `profiles.json` darf sich nicht ändern. Gegenprobe:
   ```bash
   cd tools/tts && git diff --quiet profiles.json && echo "unverändert — gut"
   ```
2. `temperature` zurück auf `0.6`, `subtalker_temperature` auf `0.7`, „Speichern" — grünes Banner, und:
   ```bash
   cd tools/tts && ~/qwen-tts-test/.venv/bin/python -c "
   import json; print(json.load(open('profiles.json'))['profiles']['phoneme']['sampling'])"
   ```
   Erwartet: `subtalker_temperature` ist `0.7`, `max_new_tokens` weiter `25`, `temperature` `0.6`.
3. Das Dauer-Feld leeren, „Speichern" — der Schlüssel `max_new_tokens` muss aus `phoneme` verschwinden und das Feld danach „unbegrenzt" als Platzhalter zeigen. Anschließend wieder `2.0` eintragen und speichern, damit der Ausgangszustand steht:
   ```bash
   cd tools/tts && ~/qwen-tts-test/.venv/bin/python -c "
   import json; print(json.load(open('profiles.json'))['profiles']['phoneme']['sampling'].get('max_new_tokens'))"
   ```
   Erwartet am Ende: `25`.

Danach den Server mit Ctrl-C beenden und sicherstellen, dass `git diff profiles.json` leer ist.

- [ ] **Step 8: Commit**

```bash
git add tools/tts/ttskit/static/app.js tools/tts/ttskit/static/style.css
git commit -m "feat(tts-ui): Parameter-Panel rendert aus der Registry

Drei Gruppen statt eines flachen Grids, eine Erklaerungszeile mit
Wertebereich unter jedem Feld, und die Dauer wird in Sekunden eingegeben
statt in Tokens. Vorher rendete das Panel aus
Object.keys(profile.sampling) — ein Parameter, der in profiles.json noch
nicht stand, blieb dadurch fuer immer unsichtbar.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Live-Tokenanzeige und Kurzfassung in der Detailsicht

**Files:**
- Modify: `tools/tts/ttskit/static/app.js` (`renderParams` Zeilen ~747–787, `profileSummaryCard` Zeilen ~403–415, `saveProfileFrom` Zeile ~378)
- Modify: `tools/tts/ttskit/static/index.html:20-22` (Tooltip des ⚙️-Buttons)

**Interfaces:**
- Consumes: `paramFieldHtml`, `specFor`, `tokensToSeconds`, `state.secondsPerToken` (Task 5)
- Produces: `wireDurationField(container) -> void` — verdrahtet in einem Container jedes `[data-unit="seconds"]`-Feld mit seiner `[data-tokens-for]`-Anzeige

- [ ] **Step 1: Die Live-Anzeige verdrahten**

In `tools/tts/ttskit/static/app.js` direkt vor `function wirePoolLinks(container)` einfügen:

```js
// Die Eingabe steht in Sekunden, gespeichert werden Tokens. Die Ableitung
// muss beim Tippen sichtbar sein, sonst überrascht der Rundungssprung:
// 2,05 s ergeben 26 Tokens und zeigen nach dem Speichern 2,08 s.
function wireDurationField(container) {
  container.querySelectorAll('[data-unit="seconds"]').forEach((input) => {
    const readout = container.querySelector(
      `[data-tokens-for="${input.dataset.param}"]`);
    if (!readout) return;
    const update = () => {
      const raw = input.value.trim();
      if (raw === "") {
        readout.textContent = "unbegrenzt";
        return;
      }
      const seconds = Number(raw);
      if (Number.isNaN(seconds)) {
        readout.textContent = "keine Zahl";
        return;
      }
      const tokens = Math.round(seconds / state.secondsPerToken);
      readout.textContent = `= ${tokens} Tokens (${tokensToSeconds(tokens)} s)`;
    };
    input.oninput = update;
    update();
  });
}
```

- [ ] **Step 2: Sie an beiden Einbauorten des Formulars aufrufen**

`profileFormHtml` wird an zwei Stellen benutzt — im ⚙️-Panel und in der Detailsicht. Beide brauchen den Aufruf, sonst ist die Anzeige an einer Stelle tot.

Es gibt genau zwei Aufrufe von `wirePoolLinks` — `app.js:631` (`wirePoolLinks(form);`, der Aufklapp-Pfad der Detailsicht) und `app.js:777` (`wirePoolLinks(card);` in `renderParams`). Neben **beide** die entsprechende Zeile setzen:

```js
    wirePoolLinks(form);
    wireDurationField(form);
```

```js
    wirePoolLinks(card);
    wireDurationField(card);
```

Zur Kontrolle, dass keiner übersehen wurde:

```bash
cd tools/tts && grep -c "wireDurationField(" ttskit/static/app.js
```

Erwartet: `3` — die Definition plus zwei Aufrufe.

- [ ] **Step 3: Die Kurzfassung um die Dauer erweitern**

In `app.js` in `profileSummaryCard` die Zeile

```js
            · temperature ${profile.sampling.temperature}
```

ersetzen durch:

```js
            · temperature ${profile.sampling.temperature}
            · max ${profile.sampling.max_new_tokens === undefined
                ? "unbegrenzt"
                : tokensToSeconds(profile.sampling.max_new_tokens) + " s"}
```

- [ ] **Step 4: Referenzblock und Tooltip nachziehen**

In `app.js` in `renderParams` den zweiten Erklärungsabsatz (`<p class="muted small">Hinter jeder Stimme steht ihre Herkunft…`) unverändert lassen und **davor** einen weiteren Absatz einfügen:

```js
      <p class="muted small">Die maximale Dauer ist ein harter Schnitt, kein
        Hinweis: läuft das Modell in einen erfundenen Satz, bricht die Aufnahme
        mitten drin ab — hörbar kaputt statt unauffällig falsch. Der Wert gilt
        für die Rohgenerierung, also bevor die Stille am Anfang und Ende
        weggeschnitten wird; die fertige Datei ist entsprechend kürzer.
        Intern zählt das Modell in Tokens à 80 ms.</p>
```

In `tools/tts/ttskit/static/index.html` den `title` des ⚙️-Buttons (Zeile 21) ersetzen durch:

```html
              title="Stimme, Sprache, Instruktion, maximale Dauer und Sampling aller Profile bearbeiten">
```

- [ ] **Step 5: Im Browser verifizieren**

```bash
./start-tts-ui.sh
```

`http://127.0.0.1:8420` öffnen und prüfen:

1. **⚙️-Panel:** Im Dauer-Feld von `phoneme` `1,6` eintippen — die Anzeige daneben muss sofort auf „= 20 Tokens (1.60 s)" springen, **ohne** Speichern. Feld leeren → „unbegrenzt". `2,05` eintippen → „= 26 Tokens (2.08 s)"; genau dieser Sprung soll sichtbar sein. Danach wieder `2,0`.
2. **Referenzblock:** Der Absatz über den harten Schnitt steht oben im Panel.
3. **Detailsicht:** Panel schließen, links einen Clip des Profils `phoneme` wählen. Die Profil-Kurzfassung muss „… · temperature 0.6 · max 2.00 s" zeigen. Auf „Bearbeiten" klicken — auch hier drei Gruppen, und die Live-Anzeige am Dauer-Feld reagiert auf Tippen.
4. **Konsole:** Entwicklertools öffnen, Panel und Detailsicht je einmal auf- und zuklappen. Keine JS-Fehler.

Danach Ctrl-C und `git diff --quiet tools/tts/profiles.json` muss durchgehen — beim Verifizieren darf nichts gespeichert worden sein.

- [ ] **Step 6: Gesamte Suite ein letztes Mal**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest -q
```

Erwartet: alles grün.

- [ ] **Step 7: Commit**

```bash
git add tools/tts/ttskit/static/app.js tools/tts/ttskit/static/index.html
git commit -m "feat(tts-ui): Sekunden-Feld zeigt die Tokenzahl live mit

Die Ableitung muss beim Tippen sichtbar sein, sonst ueberrascht der
Rundungssprung: 2,05 s ergeben 26 Tokens und zeigen danach 2,08 s. Dazu
die maximale Dauer in der Profil-Kurzfassung der Detailsicht und ein
Referenzblock, der den harten Schnitt und die Trim-Reihenfolge erklaert.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 7: Dokumentation nachziehen

**Files:**
- Modify: `tools/tts/README.md`

**Interfaces:**
- Consumes: alles aus den Tasks 1–6
- Produces: keine Symbole

- [ ] **Step 1: Die betroffenen Stellen finden**

```bash
cd tools/tts && grep -n "sampling\|temperature\|Parameter\|profiles.json" README.md
```

- [ ] **Step 2: Abschnitt ergänzen**

In `tools/tts/README.md` an der Stelle, an der die Sampling-Parameter oder `profiles.json` beschrieben werden, diesen Abschnitt einfügen (Überschriftenebene an die Umgebung anpassen):

```markdown
### Maximale Dauer (`max_new_tokens`)

Das Modell erfindet beim Aussprechen einzelner Buchstaben gelegentlich ganze
Sätze dazu. `max_new_tokens` deckelt die Aufnahme-Länge und macht solche
Ausrutscher hörbar kaputt statt unauffällig falsch.

- **1 Token = 80 ms Audio.** Hergeleitet aus dem 12-Hz-Tokenizer:
  `decode_upsample_rate 1920` bei 24 kHz, also `1920 / 24000`.
- Der Wert gilt für die **Rohgenerierung**, bevor `trim_silence` die Stille am
  Anfang und Ende wegschneidet. Die fertige Datei ist entsprechend kürzer.
- Es ist ein **harter Schnitt**: das Modell will weitersprechen und wird mitten
  drin gekappt. Erfundene Sätze werden also nicht verhindert, sondern fallen
  beim Kuratieren sofort auf.
- Fehlt der Schlüssel in `profiles.json`, gilt der Checkpoint-Default von 8192
  Tokens ≈ 655 s, also praktisch unbegrenzt.

Im ⚙️-Panel wird der Wert in Sekunden eingegeben; gespeichert werden Tokens.

### Wertebereiche

Alle Sampling-Parameter samt Grenzen, Typ und Erklärungstext stehen in
`SAMPLING_SPEC` in `ttskit/store.py` — eine Stelle, aus der sowohl die
Server-Prüfung als auch das ⚙️-Panel gespeist werden. Ein neuer Parameter
braucht dort einen Eintrag und sonst nichts.

`do_sample` und `subtalker_dosample` sind bewusst **nicht** editierbar:
greedy Generierung macht den Seed wirkungslos, womit Seed-Pool und
Kandidaten-Kuratierung ihren Sinn verlieren.

### Neustart nötig?

Nein. Das Modell wird einmal beim Serverstart geladen und hängt nur an
Checkpoint und Device. Sampling-Werte reisen pro Aufruf mit, und der Server
liest `profiles.json` bei jedem Request neu — gespeichert heißt ab der
nächsten Generierung wirksam. Ein Neustart ist nur für einen anderen
Checkpoint oder ein anderes Device nötig.
```

- [ ] **Step 3: Commit**

```bash
git add tools/tts/README.md
git commit -m "docs(tts): Dauer-Limit, Wertebereiche und Neustart-Frage im README

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Verifikation zum Abschluss

- [ ] `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest -q` — alles grün
- [ ] `git diff main --stat` zeigt genau: `ttskit/store.py`, `ttskit/server.py`, `ttskit/static/app.js`, `ttskit/static/style.css`, `ttskit/static/index.html`, `profiles.json`, `README.md`, `tests/test_store.py`, `tests/test_server.py`, die Spec und dieser Plan
- [ ] `profiles.json`: `prompt` hat weiter 21 Seeds, `sentence` 16 — kein kuratierter Seed ist verloren gegangen
- [ ] Ein Probelauf mit echtem Modell auf dem `phoneme`-Profil: `./start-tts-ui.sh`, einen Buchstaben-Clip wählen, „Generate" mit 4 Kandidaten. Keiner der vier darf länger als 2 s sein. Das ist die eigentliche Zusage des Vorhabens und der einzige Schritt, der das geladene Modell wirklich braucht.
