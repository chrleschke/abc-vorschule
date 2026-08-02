# Qwen-TTS App-Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Approvete (gelockte + gerenderte) Qwen-TTS-Clips als OGG/Opus in die App-Assets exportieren und dort abspielen, mit Android-TTS als Fallback; Export als CLI-Befehl, API-Endpoint und Web-UI-Button.

**Architecture:** Neues Tooling-Modul `ttskit/export.py` konvertiert `out/audio/<key>.wav` → `app/src/main/assets/audio/<profile>_<hash>.ogg` plus text-basierten `index.json` (Sync-Semantik). App-seitig lädt `ClipIndex` den Index über den `openAsset`-Seam, `SpeechController` versucht Clip-Playback per `MediaPlayer`, fällt sonst auf Android-TTS zurück. Öffentliche API von `SpeechController` bleibt unverändert.

**Tech Stack:** Python (soundfile für OGG/Opus, FastAPI, pytest), Kotlin/Compose (MediaPlayer, kotlinx.serialization, JUnit).

Spec: `docs/superpowers/specs/2026-08-02-qwen-tts-app-integration-design.md`

## Global Constraints

- Exportiert wird nur `locked` **und** Status `rendered`; alles andere wird mit Grund im Bericht übersprungen, Überspringen ist kein Fehler (Exit 0).
- OGG/Opus via `soundfile` (`format="OGG"`, `subtype="OPUS"`), 24 kHz bleibt erhalten. Kein ffmpeg.
- Dateiname: clipKey mit `:` → `_`, Endung `.ogg` (z. B. `sentence_0620b64d3955.ogg`).
- Index-Schlüssel ist der **Quelltext** (`clip.source_text`), nicht der TextOverride.
- Kollisionspriorität bei gleichem Text in mehreren Profilen: `word > phoneme > prompt > miss > reward > sentence > finale > ui` + Warnung.
- Export besitzt `assets/audio/` vollständig: nicht mehr exportierte `.ogg` werden gelöscht, `index.json` wird immer neu geschrieben, deterministisch sortiert, keine Zeitstempel.
- App: öffentliche Oberfläche von `SpeechController` (`speak`, `speakAndAwait`, `stop`, `shutdown`, `available`, `speaking`) unverändert; Flush-Semantik (jeder Aufruf stoppt Clip **und** TTS); Clips spielen auch bei `available == false`.
- Python-Tests laufen mit `~/qwen-tts-test/.venv/bin/python -m pytest` aus `tools/tts/` (venv hat soundfile; System-Python evtl. nicht).
- Commits auf diesem Branch, Messages auf Deutsch im Stil `feat(tts): …` / `feat(speech): …`.

---

### Task 1: Export-Modul im Tooling

**Files:**
- Create: `tools/tts/ttskit/export.py`
- Create: `tools/tts/tests/test_export.py`
- Modify: `tools/tts/ttskit/paths.py` (Feld `app_audio_dir`)
- Modify: `tools/tts/ttskit/cli.py` (Befehl `export`)

**Interfaces:**
- Consumes: `load_context` aus `ttskit.cli`, `status_of` aus `ttskit.plan`, `Paths` aus `ttskit.paths`.
- Produces: `export_to_app(paths: Paths) -> ExportReport` mit `ExportReport(exported: list[str], skipped: list[tuple[str, str]], removed: list[str], warnings: list[str])` und Methode `as_dict() -> dict`. Dateiname-Helfer `asset_name(key: str) -> str`. Task 2 (Server/UI) ruft `export_to_app` auf und serialisiert `as_dict()`.

- [ ] **Step 1: `app_audio_dir` in Paths ergänzen**

In `tools/tts/ttskit/paths.py` der Dataclass ein zweites relokierbares Feld geben:

```python
@dataclass
class Paths:
    root: Path = TOOL_ROOT
    content_dir: Path = REPO_ROOT / "app" / "src" / "main" / "assets" / "content"
    app_audio_dir: Path = REPO_ROOT / "app" / "src" / "main" / "assets" / "audio"
```

- [ ] **Step 2: Failing Tests schreiben**

`tools/tts/tests/test_export.py`. Die bestehende Fixture `content_dir` aus `conftest.py` liefert ein Mini-Content-Pack; `Paths(root=tmp_path, content_dir=content_dir, app_audio_dir=tmp_path / "app-audio")` relokiert alles. Ein gelockter + gerenderter Clip braucht: Lock in `locks.json`, WAV unter `out/audio/<key>.wav`, passenden Fingerprint in `out/render-state.json`. Baue dafür einen Helper, der `load_context` benutzt, um den echten Fingerprint zu errechnen:

```python
import json
import wave
from pathlib import Path

import numpy as np
import pytest
import soundfile as sf

from ttskit.cli import load_context
from ttskit.export import ExportReport, asset_name, export_to_app
from ttskit.paths import Paths
from ttskit.plan import fingerprint


def make_paths(tmp_path: Path, content_dir: Path) -> Paths:
    return Paths(root=tmp_path, content_dir=content_dir,
                 app_audio_dir=tmp_path / "app-audio")


def write_wav(path: Path, seconds: float = 0.2, sr: int = 24000) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    t = np.linspace(0, seconds, int(sr * seconds), endpoint=False)
    sf.write(path, (0.3 * np.sin(2 * np.pi * 440 * t)).astype(np.float32),
             sr, subtype="PCM_16")


def lock_and_render(paths: Paths, key: str, fresh: bool = True) -> None:
    """Lockt `key` und legt WAV + (optional aktuellen) Fingerprint an."""
    locks_file = paths.locks
    data = (json.loads(locks_file.read_text()) if locks_file.exists()
            else {"version": 1, "locks": {}})
    data["locks"][key] = {"seed": 1}
    locks_file.write_text(json.dumps(data), encoding="utf-8")

    write_wav(paths.audio / f"{key}.wav")

    ctx = load_context(paths)
    clip = next(c for c in ctx.clips if c.key == key)
    fp = fingerprint(clip, ctx.profiles.profiles[clip.profile]) if fresh else "stale00"
    state = (json.loads(paths.render_state.read_text())
             if paths.render_state.exists() else {"version": 1, "entries": {}})
    state["entries"][key] = fp
    paths.render_state.parent.mkdir(parents=True, exist_ok=True)
    paths.render_state.write_text(json.dumps(state), encoding="utf-8")


def clip_key_for_text(paths: Paths, text: str) -> str:
    ctx = load_context(paths)
    return next(c.key for c in ctx.clips if c.source_text == text)


def test_asset_name_replaces_colon():
    assert asset_name("sentence:0620b64d3955") == "sentence_0620b64d3955.ogg"


def test_exports_locked_rendered_clip_as_ogg(tmp_path, content_dir):
    paths = make_paths(tmp_path, content_dir)
    key = clip_key_for_text(paths, "Mama.")
    lock_and_render(paths, key)

    report = export_to_app(paths)

    assert report.exported == [key]
    ogg = paths.app_audio_dir / asset_name(key)
    assert ogg.exists()
    data, sr = sf.read(ogg)
    assert sr == 24000 and len(data) > 0
    index = json.loads((paths.app_audio_dir / "index.json").read_text())
    assert index["clips"]["Mama."] == {
        "file": asset_name(key), "profile": "sentence"}


def test_skips_unlocked_stale_and_missing(tmp_path, content_dir):
    paths = make_paths(tmp_path, content_dir)
    stale_key = clip_key_for_text(paths, "Mama.")
    lock_and_render(paths, stale_key, fresh=False)

    missing_key = clip_key_for_text(paths, "Maus")
    lock_and_render(paths, missing_key)
    (paths.audio / f"{missing_key}.wav").unlink()

    report = export_to_app(paths)

    assert report.exported == []
    reasons = dict(report.skipped)
    assert "stale" in reasons[stale_key]
    assert "missing" in reasons[missing_key]
    # Ungelockte Clips tauchen gar nicht erst im Bericht auf:
    assert len(report.skipped) == 2
    index = json.loads((paths.app_audio_dir / "index.json").read_text())
    assert index["clips"] == {}


def test_orphan_lock_is_reported_not_exported(tmp_path, content_dir):
    paths = make_paths(tmp_path, content_dir)
    paths.locks.write_text(json.dumps(
        {"version": 1, "locks": {"sentence:deadbeef0000": {"seed": 1}}}),
        encoding="utf-8")

    report = export_to_app(paths)

    assert report.exported == []
    assert any(key == "sentence:deadbeef0000" and "verwaist" in reason
               for key, reason in report.skipped)


def test_sync_removes_stale_ogg_but_keeps_foreign_files(tmp_path, content_dir):
    paths = make_paths(tmp_path, content_dir)
    paths.app_audio_dir.mkdir(parents=True)
    (paths.app_audio_dir / "word_000000000000.ogg").write_bytes(b"old")
    (paths.app_audio_dir / "notes.txt").write_text("bleibt")

    key = clip_key_for_text(paths, "Mama.")
    lock_and_render(paths, key)
    report = export_to_app(paths)

    assert not (paths.app_audio_dir / "word_000000000000.ogg").exists()
    assert (paths.app_audio_dir / "notes.txt").exists()
    assert report.removed == ["word_000000000000.ogg"]


def test_collision_prefers_word_over_phoneme(tmp_path, content_dir):
    # "M" existiert als lemma (word) und als phonemeTts/stretchTts (phoneme).
    paths = make_paths(tmp_path, content_dir)
    ctx = load_context(paths)
    word_key = next(c.key for c in ctx.clips
                    if c.source_text == "M" and c.profile == "word")
    phoneme_key = next(c.key for c in ctx.clips
                       if c.source_text == "M" and c.profile == "phoneme")
    lock_and_render(paths, word_key)
    lock_and_render(paths, phoneme_key)

    report = export_to_app(paths)

    index = json.loads((paths.app_audio_dir / "index.json").read_text())
    assert index["clips"]["M"]["profile"] == "word"
    assert any("M" in w for w in report.warnings)
    # Beide OGGs liegen trotzdem da — nur der Index-Eintrag ist eindeutig.
    assert (paths.app_audio_dir / asset_name(phoneme_key)).exists()


def test_export_is_deterministic(tmp_path, content_dir):
    paths = make_paths(tmp_path, content_dir)
    key = clip_key_for_text(paths, "Mama.")
    lock_and_render(paths, key)
    export_to_app(paths)
    first = (paths.app_audio_dir / "index.json").read_bytes()
    export_to_app(paths)
    assert (paths.app_audio_dir / "index.json").read_bytes() == first
```

Hinweis: prüfe vorher per `grep -n "lemma" tools/tts/tests/conftest.py`, ob das Mini-Pack wirklich `M` als lemma **und** phonemeTts enthält (laut conftest: atom `letter-m` mit lemma `M`, task `l01-t1` mit `phonemeTts: "M"` — ja). Falls sich Details unterscheiden, Testdaten an die Fixture anpassen, nicht die Fixture ändern.

- [ ] **Step 3: Tests laufen lassen — sie müssen fehlschlagen**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_export.py -x -q
```

Erwartet: ImportError / ModuleNotFoundError für `ttskit.export`.

- [ ] **Step 4: `ttskit/export.py` implementieren**

```python
"""Approvete Clips als OGG/Opus in die App-Assets exportieren.

Exportiert wird genau ein Clip, wenn er gelockt ist und sein Render-Stand
aktuell ('rendered'). Der Export besitzt das Zielverzeichnis: nicht mehr
exportierte .ogg-Dateien werden entfernt, index.json wird immer neu
geschrieben — deterministisch, ohne Zeitstempel, für saubere Diffs.
"""

from __future__ import annotations

import json
from dataclasses import dataclass, field
from pathlib import Path

import soundfile as sf

from .paths import Paths
from .plan import orphan_locks, status_of

#: Bei gleichem Quelltext in mehreren Profilen gewinnt das frühere Profil.
#: Die App kennt am Call-Site nur den Text — der Index muss eindeutig sein.
PROFILE_PRIORITY = ("word", "phoneme", "prompt", "miss", "reward",
                    "sentence", "finale", "ui")


def asset_name(key: str) -> str:
    """clipKey → Asset-Dateiname. Doppelpunkte sind in Zip-Einträgen riskant
    und auf Windows verboten; Assets brauchen einen portablen Namen."""
    return key.replace(":", "_") + ".ogg"


@dataclass
class ExportReport:
    exported: list[str] = field(default_factory=list)
    skipped: list[tuple[str, str]] = field(default_factory=list)
    removed: list[str] = field(default_factory=list)
    warnings: list[str] = field(default_factory=list)

    def as_dict(self) -> dict:
        return {
            "exported": self.exported,
            "skipped": [{"key": k, "reason": r} for k, r in self.skipped],
            "removed": self.removed,
            "warnings": self.warnings,
        }


def export_to_app(paths: Paths) -> ExportReport:
    from .cli import load_context  # lokaler Import: cli importiert nicht zurück

    ctx = load_context(paths)
    report = ExportReport()

    for key in orphan_locks(ctx.locks, ctx.clips):
        report.skipped.append((key, "Lock ist verwaist — Quelltext existiert nicht mehr"))

    exportable = []
    for clip in ctx.clips:
        if not clip.locked:
            continue
        profile = ctx.profiles.profiles[clip.profile]
        status = status_of(clip, profile, ctx.state, paths.audio)
        if status != "rendered":
            report.skipped.append((clip.key, f"Status ist {status}, nicht rendered"))
            continue
        exportable.append(clip)

    target = Path(paths.app_audio_dir)
    target.mkdir(parents=True, exist_ok=True)

    index: dict[str, dict] = {}
    for clip in sorted(exportable, key=lambda c: c.key):
        data, sr = sf.read(paths.audio / f"{clip.key}.wav", dtype="float32")
        sf.write(target / asset_name(clip.key), data, sr,
                 format="OGG", subtype="OPUS")
        report.exported.append(clip.key)

        text = clip.source_text
        existing = index.get(text)
        if existing is None:
            index[text] = {"file": asset_name(clip.key), "profile": clip.profile}
            continue
        old = PROFILE_PRIORITY.index(existing["profile"])
        new = PROFILE_PRIORITY.index(clip.profile)
        winner = existing["profile"] if old <= new else clip.profile
        if old > new:
            index[text] = {"file": asset_name(clip.key), "profile": clip.profile}
        report.warnings.append(
            f"Text {text!r} existiert in mehreren Profilen — "
            f"App spielt {winner!r}")

    keep = {asset_name(c.key) for c in exportable} | {"index.json"}
    for path in sorted(target.glob("*.ogg")):
        if path.name not in keep:
            path.unlink()
            report.removed.append(path.name)

    payload = {"version": 1,
               "clips": {t: index[t] for t in sorted(index)}}
    (target / "index.json").write_text(
        json.dumps(payload, indent=2, ensure_ascii=False, sort_keys=True) + "\n",
        encoding="utf-8")
    return report
```

- [ ] **Step 5: Tests laufen lassen — grün**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_export.py -q
```

Erwartet: alle PASS. Danach die komplette Suite: `~/qwen-tts-test/.venv/bin/python -m pytest -q` — keine Regressionen.

- [ ] **Step 6: CLI-Befehl `export` anschließen**

In `tools/tts/ttskit/cli.py`:

```python
def cmd_export(paths: Paths) -> int:
    from .export import export_to_app

    report = export_to_app(paths)
    print(f"{len(report.exported)} Clips exportiert → {paths.app_audio_dir}")
    if report.removed:
        print(f"{len(report.removed)} veraltete Dateien entfernt: "
              f"{', '.join(report.removed)}")
    for key, reason in report.skipped:
        print(f"  übersprungen {key}: {reason}")
    for warning in report.warnings:
        print(f"  Achtung: {warning}")
    return 0
```

In `build_parser()` nach dem `status`-Parser:

```python
    sub.add_parser("export", help="Approvete Clips als OGG in die App-Assets")
```

In `main()`:

```python
    if args.command == "export":
        return cmd_export(paths)
```

Smoke-Test von Hand (nutzt echte Repo-Paths, im Worktree ist `out/` leer — es darf einfach `0 Clips exportiert` erscheinen und ein leerer Index entstehen; danach `git status` prüfen und die dabei entstandene `app/src/main/assets/audio/index.json` wieder löschen, Task 5 erzeugt sie richtig):

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python ./tts export && rm -rf ../../app/src/main/assets/audio
```

- [ ] **Step 7: Commit**

```bash
git add tools/tts/ttskit/export.py tools/tts/tests/test_export.py tools/tts/ttskit/paths.py tools/tts/ttskit/cli.py
git commit -m "feat(tts): Export approveter Clips als OGG/Opus in die App-Assets"
```

---

### Task 2: Export im Server und Web-UI

**Files:**
- Modify: `tools/tts/ttskit/server.py` (Endpoint `POST /api/export`)
- Modify: `tools/tts/ttskit/static/index.html` (Button im Header)
- Modify: `tools/tts/ttskit/static/app.js` (Click-Handler)
- Modify: `tools/tts/tests/test_server.py` (Endpoint-Test)
- Modify: `tools/tts/README.md` (Abschnitt zum Export)

**Interfaces:**
- Consumes: `export_to_app(paths) -> ExportReport` und `asset_name` aus Task 1.
- Produces: `POST /api/export` → `{"exported": [...], "skipped": [{"key","reason"}], "removed": [...], "warnings": [...]}`.

- [ ] **Step 1: Failing Server-Test schreiben**

Zuerst `tools/tts/tests/test_server.py` lesen und das bestehende Fixture-Muster übernehmen (dort existiert bereits ein TestClient-Setup mit relokierten Paths — dasselbe verwenden, inklusive `app_audio_dir=tmp_path / "app-audio"` falls das Fixture `Paths` selbst baut). Test ergänzen:

```python
def test_export_endpoint_returns_report(client):
    response = client.post("/api/export")
    assert response.status_code == 200
    body = response.json()
    assert set(body) == {"exported", "skipped", "removed", "warnings"}
```

(Ohne Locks ist alles leer — der Test prüft Contract und Verdrahtung, die Export-Logik selbst ist in Task 1 getestet.)

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_server.py -q -k export
```

Erwartet: 404/405-Assert-Fehler.

- [ ] **Step 3: Endpoint implementieren**

In `server.py`, neben `api_render` (Zeile ~492). Kein Model-Zugriff, keine JobQueue — der Export liest nur Dateien und schreibt winzige OGGs, das ist ein synchroner Request:

```python
    @app.post("/api/export")
    def api_export() -> dict[str, Any]:
        from .export import export_to_app

        return export_to_app(paths).as_dict()
```

- [ ] **Step 4: Tests laufen lassen — grün**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_server.py -q
```

- [ ] **Step 5: UI-Button einbauen**

`index.html`, im `<header>` direkt nach dem `batch-count`-Input (`… Stück`):

```html
      <button id="btn-export"
              title="Konvertiert alle bestätigten, fertig gerenderten Clips nach OGG und aktualisiert die App-Assets (app/src/main/assets/audio/).">
        📦 In App exportieren</button>
```

`app.js`: neben dem bestehenden `el("btn-render").onclick`-Handler (Zeile ~886), im selben Stil (`guard`, `api`, `showBanner`):

```javascript
el("btn-export").onclick = guard(async () => {
  const report = await api("/api/export", { method: "POST" });
  const parts = [`${report.exported.length} Clips in die App exportiert`];
  if (report.removed.length) parts.push(`${report.removed.length} veraltete entfernt`);
  if (report.skipped.length) {
    parts.push(`${report.skipped.length} übersprungen: ` +
      report.skipped.map((s) => `${s.key} (${s.reason})`).join(", "));
  }
  parts.push(...report.warnings);
  showBanner(parts.join(" — "), report.skipped.length || report.warnings.length
    ? "warn" : "info");
});
```

Vorher in `app.js`/`style.css` prüfen, welche Banner-Klassen existieren (`showBanner(text, kind)` setzt `el("banner").className = kind` — nachsehen, ob `warn`/`info` in `style.css` definiert sind, sonst die dort vorhandenen Klassennamen verwenden).

- [ ] **Step 6: README ergänzen**

In `tools/tts/README.md`: beim CLI-Abschnitt `export` erwähnen und den Satz am Ende („bis dahin fällt die App für fehlende Clips auf …", Zeile ~241) an den neuen Stand anpassen: Export-Button/`tts export` schreibt `app/src/main/assets/audio/` (OGG/Opus + `index.json`), die App spielt Clips und fällt für alles andere auf Android-TTS zurück.

- [ ] **Step 7: Commit**

```bash
git add tools/tts/ttskit/server.py tools/tts/ttskit/static/index.html tools/tts/ttskit/static/app.js tools/tts/tests/test_server.py tools/tts/README.md
git commit -m "feat(tts-ui): Export-Button schreibt approvete Clips in die App-Assets"
```

---

### Task 3: ClipIndex in der App

**Files:**
- Create: `app/src/main/java/app/abcvorschule/speech/ClipIndex.kt`
- Create: `app/src/test/java/app/abcvorschule/speech/ClipIndexTest.kt`

**Interfaces:**
- Consumes: nichts Neues (kotlinx.serialization ist vorhanden).
- Produces: `class ClipIndex` mit `fun lookup(text: String): ClipEntry?`, `data class ClipEntry(val file: String, val profile: String)`, `companion object { fun empty(): ClipIndex; fun parse(json: String): ClipIndex; fun load(openAsset: (String) -> InputStream): ClipIndex }`. Task 4 (SpeechController) nutzt `lookup` und `ClipEntry.file`.

- [ ] **Step 1: Failing Test schreiben**

`app/src/test/java/app/abcvorschule/speech/ClipIndexTest.kt`:

```kotlin
package app.abcvorschule.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ClipIndexTest {

    private val sample = """
        {
          "version": 1,
          "clips": {
            "Mama mag Mais.": { "file": "sentence_0620b64d3955.ogg", "profile": "sentence" },
            "M": { "file": "word_1a2b3c4d5e6f.ogg", "profile": "word" }
          }
        }
    """.trimIndent()

    @Test
    fun `findet Clip per exaktem Text`() {
        val index = ClipIndex.parse(sample)
        assertEquals(
            ClipEntry(file = "sentence_0620b64d3955.ogg", profile = "sentence"),
            index.lookup("Mama mag Mais."),
        )
    }

    @Test
    fun `trimmt den gesuchten Text`() {
        val index = ClipIndex.parse(sample)
        assertEquals("word_1a2b3c4d5e6f.ogg", index.lookup(" M ")?.file)
    }

    @Test
    fun `unbekannter Text liefert null`() {
        assertNull(ClipIndex.parse(sample).lookup("Papa"))
    }

    @Test
    fun `unbekannte JSON-Felder stoeren nicht`() {
        val withExtra = sample.replace("\"version\": 1", "\"version\": 1, \"neu\": true")
        assertEquals(2, ClipIndex.parse(withExtra).size)
    }

    @Test
    fun `fehlender oder kaputter Index ergibt leeren Index`() {
        val missing = ClipIndex.load { throw java.io.FileNotFoundException(it) }
        assertNull(missing.lookup("Mama mag Mais."))
        val broken = ClipIndex.load { "kein json".byteInputStream() }
        assertNull(broken.lookup("Mama mag Mais."))
    }
}
```

Vorher per `ls app/src/test/java/app/abcvorschule/` und Blick in einen bestehenden Test prüfen, welches Assertion-Framework benutzt wird (kotlin.test vs JUnit-Assert) und den Stil übernehmen; die Testinhalte oben beibehalten.

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.speech.ClipIndexTest" 2>&1 | tail -20
```

Erwartet: Kompilierfehler (unresolved reference ClipIndex).

- [ ] **Step 3: Implementierung**

`app/src/main/java/app/abcvorschule/speech/ClipIndex.kt`:

```kotlin
package app.abcvorschule.speech

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.InputStream

@Serializable
data class ClipEntry(val file: String, val profile: String)

@Serializable
private data class ClipIndexFile(
    val version: Int = 1,
    val clips: Map<String, ClipEntry> = emptyMap(),
)

/**
 * Text → vorproduzierter Audio-Clip, gespeist aus assets/audio/index.json.
 *
 * Schlüssel ist der Quelltext aus dem Content-Pack — exakt der String, den
 * die Sprech-Call-Sites übergeben. Fehlt der Index oder ist er kaputt,
 * verhält sich die App wie ohne Clips: alles spricht Android-TTS.
 */
class ClipIndex private constructor(private val clips: Map<String, ClipEntry>) {

    val size: Int get() = clips.size

    fun lookup(text: String): ClipEntry? = clips[text.trim()]

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun empty(): ClipIndex = ClipIndex(emptyMap())

        fun parse(raw: String): ClipIndex =
            ClipIndex(json.decodeFromString<ClipIndexFile>(raw).clips)

        fun load(openAsset: (String) -> InputStream): ClipIndex = try {
            parse(openAsset("audio/index.json").bufferedReader().use { it.readText() })
        } catch (_: Exception) {
            empty()
        }
    }
}
```

- [ ] **Step 4: Tests laufen lassen — grün**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.speech.ClipIndexTest" 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/speech/ClipIndex.kt app/src/test/java/app/abcvorschule/speech/ClipIndexTest.kt
git commit -m "feat(speech): ClipIndex mappt Sprechtexte auf produzierte Audio-Clips"
```

---

### Task 4: Clip-Playback im SpeechController

**Files:**
- Create: `app/src/main/java/app/abcvorschule/speech/ClipPlayer.kt`
- Modify: `app/src/main/java/app/abcvorschule/speech/SpeechController.kt`
- Modify: `app/src/main/java/app/abcvorschule/MainActivity.kt:57`

**Interfaces:**
- Consumes: `ClipIndex.lookup(text) -> ClipEntry?` aus Task 3.
- Produces: unveränderte öffentliche API von `SpeechController`; neuer Konstruktor `SpeechController(context: Context, clips: ClipIndex = ClipIndex.empty())`.

Kein neuer Unit-Test: `ClipPlayer` und die Verzweigung in `SpeechController` hängen an `MediaPlayer`/`TextToSpeech` (Android-Framework, im JVM-Test nicht verfügbar); die entscheidbare Logik (Text→Clip) ist in Task 3 getestet. Verifikation hier: Kompilieren + bestehende Tests + Task 5 baut die Debug-APK.

- [ ] **Step 1: ClipPlayer schreiben**

`app/src/main/java/app/abcvorschule/speech/ClipPlayer.kt`:

```kotlin
package app.abcvorschule.speech

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer

/**
 * Spielt produzierte Clips aus assets/audio/. Ein Player zur Zeit —
 * dieselbe Flush-Semantik wie die TTS-Ausgabe: neuer Clip stoppt den alten.
 *
 * .ogg steht in AAPTs Default-noCompress-Liste, die Assets liegen also
 * unkomprimiert im APK und openFd() funktioniert.
 */
class ClipPlayer(context: Context) {
    private val appContext = context.applicationContext
    private var player: MediaPlayer? = null

    /**
     * Startet [file]; ruft [onComplete] genau einmal auf, wenn die Wiedergabe
     * endet oder scheitert. Liefert false, wenn sie gar nicht erst startet —
     * dann wurde [onComplete] nicht aufgerufen und der Aufrufer übernimmt
     * (Fallback auf Android-TTS).
     */
    fun play(file: String, onComplete: () -> Unit): Boolean {
        stop()
        return try {
            val mp = MediaPlayer()
            appContext.assets.openFd("audio/$file").use { afd ->
                mp.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
            }
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setOnCompletionListener {
                release()
                onComplete()
            }
            mp.setOnErrorListener { _, _, _ ->
                release()
                onComplete()
                true
            }
            mp.prepare()
            player = mp
            mp.start()
            true
        } catch (_: Exception) {
            release()
            false
        }
    }

    fun stop() {
        release()
    }

    private fun release() {
        player?.let { runCatching { it.release() } }
        player = null
    }
}
```

- [ ] **Step 2: SpeechController umbauen**

Die drei Stellen, an denen bisher `engine.stop()` das Flush erledigt (`speak`, `speakAndAwait`, `stop`), stoppen künftig beide Quellen; vor dem TTS-Fallback wird der Index befragt. Kompletter Umbau von `SpeechController.kt`:

```kotlin
class SpeechController(
    context: Context,
    private val clips: ClipIndex = ClipIndex.empty(),
) : TextToSpeech.OnInitListener {
    private val appContext = context.applicationContext
    private var tts: TextToSpeech? = TextToSpeech(appContext, this)
    private val clipPlayer = ClipPlayer(appContext)
    // ... _available, _speaking, utteranceWaiters und onInit bleiben unverändert ...

    fun speak(text: String) {
        if (text.isBlank()) return
        clearWaiters()
        stopOutput()
        if (playClip(text, onComplete = {})) return
        val engine = tts ?: return
        if (!_available.value) return
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    /** Speaks [text] and suspends until the utterance finishes (or times out). */
    suspend fun speakAndAwait(text: String, timeoutMs: Long = 10_000L) {
        if (text.isBlank()) return
        clearWaiters()
        stopOutput()
        val deferred = CompletableDeferred<Unit>()
        if (playClip(text, onComplete = { deferred.complete(Unit) })) {
            withTimeoutOrNull(timeoutMs) { deferred.await() }
            return
        }
        val engine = tts ?: return
        if (!_available.value) return
        val id = UUID.randomUUID().toString()
        utteranceWaiters[id] = deferred
        engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
        withTimeoutOrNull(timeoutMs) { deferred.await() }
        utteranceWaiters.remove(id)
    }

    fun stop() {
        stopOutput()
        clearWaiters()
    }

    fun shutdown() {
        stopOutput()
        tts?.shutdown()
        tts = null
        _available.value = false
        clearWaiters()
    }

    /** Clip gefunden und gestartet? Setzt `speaking` passend. */
    private fun playClip(text: String, onComplete: () -> Unit): Boolean {
        val entry = clips.lookup(text) ?: return false
        val started = clipPlayer.play(entry.file) {
            _speaking.value = false
            onComplete()
        }
        if (started) _speaking.value = true
        return started
    }

    /** Beendet beide Ausgabewege — die Flush-Semantik jedes speak-Aufrufs. */
    private fun stopOutput() {
        clipPlayer.stop()
        tts?.stop()
        _speaking.value = false
    }

    // ... completeWaiter, clearWaiters unverändert ...
}
```

Wichtig gegenüber dem alten Code: `speak`/`speakAndAwait` kehren **nicht** mehr sofort um, wenn `tts == null` oder `!available` — Clips spielen auch dann. Die Guards gelten nur noch für den TTS-Zweig. `playClip` startet gescheitert (`false`) ⇒ TTS-Zweig übernimmt; `clipPlayer.play` hat dann `onComplete` nicht aufgerufen, es hängt kein Waiter.

- [ ] **Step 3: MainActivity verdrahten**

`MainActivity.kt:57`:

```kotlin
    val speech = remember {
        SpeechController(context, ClipIndex.load { path -> context.assets.open(path) })
    }
```

- [ ] **Step 4: Kompilieren und bestehende Tests**

```bash
./gradlew :app:testDebugUnitTest 2>&1 | tail -5
```

Erwartet: BUILD SUCCESSFUL, keine Regressionen.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/speech/ClipPlayer.kt app/src/main/java/app/abcvorschule/speech/SpeechController.kt app/src/main/java/app/abcvorschule/MainActivity.kt
git commit -m "feat(speech): produzierte Clips spielen vor Android-TTS, TTS bleibt Fallback"
```

---

### Task 5: Einmalige Konvertierung der bestehenden approveten Clips

Dieser Task läuft im Worktree, holt aber die live kuratierten Daten aus dem Haupt-Checkout `/Users/cleschke/projects/abc-vorschul-app` (dort sind `locks.json`/`profiles.json` uncommitted geändert und `out/` ist gitignored).

**Files:**
- Modify: `tools/tts/locks.json`, `tools/tts/profiles.json` (Übernahme aus Haupt-Checkout)
- Create: `app/src/main/assets/audio/*.ogg` + `app/src/main/assets/audio/index.json` (generiert)

**Interfaces:**
- Consumes: `tts export` aus Task 1.
- Produces: committete App-Assets; Erwartung laut Spec: 22 exportiert, 2 übersprungen (`phoneme:5c62e091b8c0`, `word:006a933c950f` — Status missing).

- [ ] **Step 1: Kuratierte Dateien und Render-Ausgabe übernehmen**

```bash
cp /Users/cleschke/projects/abc-vorschul-app/tools/tts/locks.json tools/tts/locks.json
cp /Users/cleschke/projects/abc-vorschul-app/tools/tts/profiles.json tools/tts/profiles.json
mkdir -p tools/tts/out
cp /Users/cleschke/projects/abc-vorschul-app/tools/tts/out/render-state.json tools/tts/out/
cp -R /Users/cleschke/projects/abc-vorschul-app/tools/tts/out/audio tools/tts/out/audio
```

- [ ] **Step 2: Export ausführen**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python ./tts export
```

Erwartet: `22 Clips exportiert`, zwei Übersprungen-Zeilen für `phoneme:5c62e091b8c0` und `word:006a933c950f` (Status missing). Weicht die Zahl ab, stimmt etwas an Status/Fingerprints — nicht raten, `./tts status` ansehen und den Grund im Bericht lesen.

- [ ] **Step 3: Ergebnis prüfen**

```bash
ls app/src/main/assets/audio/ | head; ls app/src/main/assets/audio/*.ogg | wc -l
~/qwen-tts-test/.venv/bin/python - <<'EOF'
import json, soundfile as sf, pathlib
d = pathlib.Path("app/src/main/assets/audio")
index = json.loads((d / "index.json").read_text())
assert len(index["clips"]) > 0
for entry in index["clips"].values():
    data, sr = sf.read(d / entry["file"])
    assert sr == 24000 and len(data) > 0, entry
print(f"{len(index['clips'])} Index-Einträge, alle Dateien dekodierbar")
EOF
```

Erwartet: 22 `.ogg`-Dateien, alle dekodierbar.

- [ ] **Step 4: App bauen (Assets landen im APK, Index lädt)**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug 2>&1 | tail -5
```

Erwartet: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add tools/tts/locks.json tools/tts/profiles.json app/src/main/assets/audio/
git commit -m "feat(audio): 22 approvete Qwen-TTS-Clips als OGG/Opus in den App-Assets

locks.json/profiles.json: Übernahme der live kuratierten Entscheidungen
aus dem Arbeits-Checkout — sie gehören laut Tooling-Doku in git."
```

---

### Task 6: Abschluss-Verifikation

- [ ] **Step 1: Beide Testsuiten komplett**

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest -q
cd ../.. && ./gradlew :app:testDebugUnitTest 2>&1 | tail -3
```

Erwartet: alles grün.

- [ ] **Step 2: End-to-End-Probe des Web-Exports (ohne Modell)**

Der Server lädt beim Start das Modell — das ist für den Export unnötig schwer. Stattdessen den Endpoint direkt mit TestClient gegen die echten Paths prüfen (nur wenn Schritt „Task 5" gelaufen ist, sonst überspringen):

```bash
cd tools/tts && ~/qwen-tts-test/.venv/bin/python - <<'EOF'
from ttskit.export import export_to_app
from ttskit.paths import Paths
report = export_to_app(Paths())
print(report.as_dict()["exported"][:3], "…", len(report.exported), "exportiert,",
      len(report.skipped), "übersprungen")
assert len(report.exported) == 22
EOF
git status --short  # zweiter Lauf muss diff-frei sein (Determinismus)
```

Erwartet: 22 exportiert, `git status` zeigt keine geänderten Assets.
