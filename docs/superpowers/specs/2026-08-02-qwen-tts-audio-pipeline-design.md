# Qwen-TTS Audio-Pipeline — Offline-Tooling für Sprachaufnahmen

Status: `design-approved`
Datum: 2026-08-02

Die App spricht heute über Android-TTS (`SpeechController`). Klangqualität und Betonung
sind damit geräteabhängig und nicht steuerbar — für eine Audio-First-App, deren Nutzer
nicht lesen können (Prinzipien §2), ist die Stimme kein Detail, sondern das Interface.
Lokal installiertes Qwen3-TTS kann deutlich bessere, konsistente Aufnahmen erzeugen.

Dieses Dokument spezifiziert **ausschließlich die Tooling-Seite**: Scripts und ein
Web-Interface, die aus dem Content-Pack ein Audio-Paket erzeugen. Die App wird nicht
angefasst. Ob und wie der `SpeechController` später auf vorgerenderte Clips umgestellt
wird, ist Gegenstand einer eigenen Spec.

## 1. Ausgangslage (verifiziert)

**Installation** — `~/qwen-tts-test/.venv`, Paket `qwen_tts` 0.1.1, Checkpoint
`Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice` im HF-Cache.

Das mitgelieferte `qwen-tts-demo` ist **nur ein Gradio-Demo-Launcher**, keine Batch-CLI.
Für Massenkonvertierung wird die Python-API direkt angesprochen:
`Qwen3TTSModel.generate_custom_voice(text, speaker, language, instruct, **sampling)`.

Empirisch auf M3 Pro / 18 GB / MPS / bfloat16 gemessen:

| Eigenschaft | Wert |
| --- | --- |
| Modell-Ladezeit | ~8 s |
| Generierung, kurzer Satz | ~2,4 s |
| Ausgabe | 24 000 Hz, mono, float32 |
| Unterstützte Speaker | `aiden`, `dylan`, `eric`, `ono_anna`, `ryan`, `serena`, `sohee`, `uncle_fu`, `vivian` |
| Sprachen | u. a. `german` |
| `instruct`-Parameter | vorhanden (nur beim 1.7B-Modell; 0.6B ignoriert ihn) |
| `seed`-Parameter | **nicht vorhanden** |

**Determinismus ist gegeben.** Ein Probelauf mit `torch.manual_seed(seed)` vor dem
Generate-Aufruf lieferte für Seed 42 zweimal bit-identische Wellenformen
(`sha256[:16] = 1475070a8ff031a7`), für Seed 99 eine andere. Das gesamte Seed-Konzept
dieser Spec steht und fällt mit dieser Eigenschaft; ein Smoke-Test sichert sie ab
(§9). Voraussetzung ist Batchgröße 1 — Batching würde die Zuordnung Seed↔Clip zerstören.

Hochrechnung: ein vollständiger Lauf über ~530 Clips dauert rund **25 Minuten**.

## 2. Textbestand

891 autorierte Sprech-Strings im Content-Pack:

| Quelle | Anzahl | Felder |
| --- | --- | --- |
| `atoms.json` | 261 | `lemma` |
| `tasks.json` | 586 | `promptTts`, `missTts`, `rewardTts`, `stretchTts`, `phonemeTts` |
| `sentences.json` | 26 | `tts` |
| `finales.json` | 18 | `tts` |

Nach Deduplizierung bleiben ~530 eindeutige Texte — `promptTts` wiederholt sich innerhalb
einer Aufgabe über alle Runden.

Dazu kommen hartkodierte Kotlin-Strings (`SessionViewModel.lockedLessonCue()` =
„Das üben wir später.", generisches Miss-Feedback), die in einer handgepflegten
`tools/tts/extra-strings.json` erfasst werden.

### Bekannte Lücke: Templates der abgeleiteten Trainer

`SymbolHuntDerivation` und `SymbolInWordDerivation` sprechen Format-Templates, die erst
zur Laufzeit befüllt werden:

```
"Finde alle Buchstaben - %s!"
"Finde den Buchstaben - %s - im Wort - %s."
```

Diese Strings lassen sich nicht statisch enumerieren, ohne die Kotlin-Ableitungslogik
(`WordGraphemes`, Lektionsbeschränkung) in Python nachzubauen — eine Duplizierung, die
zwangsläufig auseinanderläuft.

**Entscheidung:** außerhalb des Scopes dieser Spec. `extra-strings.json` sieht dafür einen
Eintragstyp `template` mit expliziter Expansionsliste vor, damit die Datenstruktur später
trägt; befüllt wird sie aber erst, wenn die App-Integrations-Spec entschieden hat, ob die
App Clips zur Laufzeit zusammensetzt oder für abgeleitete Trainer bei Android-TTS bleibt.
Der Extractor meldet die nicht abgedeckten Templates im `status`-Output, damit die Lücke
sichtbar bleibt statt zu verschwinden.

## 3. Betrachtete Ansätze

| Ansatz | Idee | Bewertung |
| --- | --- | --- |
| **A — Eigene Python-Pipeline auf der `qwen_tts`-API** (gewählt) | Extractor + Renderer + FastAPI-UI auf denselben Bausteinen; Modell einmal geladen, Seeds selbst gesteuert | Volle Kontrolle über Seeds, Profile und inkrementelles Rendern; ohne Modell testbar |
| B — Gradio-Demo scripten | Das mitgelieferte `qwen-tts-demo` per HTTP-Client fernsteuern | Kein Seed-Zugriff, keine Batch-Semantik, Modell-Reload pro Prozess; die Demo ist nicht als API gedacht |
| C — Nur CLI, kein Web-Interface | Batch-Rendern und im Dateimanager durchhören | Das Kuratieren von Seeds über ~530 Clips ist genau die Arbeit, die eine UI trägt; ohne A/B-Vergleich unbedienbar |

Innerhalb von A wurde für die UI **FastAPI + Vanilla HTML/JS** gegenüber Gradio gewählt
(beides bereits im venv): eine Review-Queue mit Filtern, Statusspalten, A/B-Vergleich und
Tastatur-Shortcuts ist in Gradio nicht sinnvoll abbildbar.

## 4. Architektur

Alles unter `tools/tts/`, ausgeführt mit dem Interpreter aus `~/qwen-tts-test/.venv`.
Die Trennung verläuft entlang der Frage „braucht das Modul das Modell?" — die
Kernlogik ist damit ohne GPU und ohne 4 GB Checkpoint testbar.

```
tools/tts/
  tts                    # ausführbarer CLI-Einstiegspunkt (kein bin/ — .gitignore blockt das)
  README.md
  profiles.json          # versioniert: Instruktionen, Sampling, Seed-Pools
  locks.json             # versioniert: pro-Clip festgenagelte Seeds
  extra-strings.json     # versioniert: hartkodierte Kotlin-Strings
  ttskit/
    extract.py           # Content-JSON  → Manifest              (modellfrei)
    store.py             # Profile / Locks / Render-State lesen+schreiben (modellfrei)
    plan.py              # Dedup, Seed-Auflösung, Stale-Erkennung (modellfrei)
    audio.py             # Trim, Normalisierung, WAV schreiben    (modellfrei)
    engine.py            # Modell laden, generate(text, profile, seed) → wav
    render.py            # Batch-Lauf über den Plan
    server.py            # FastAPI + Job-Queue
    static/              # index.html, app.js, style.css
  out/                   # gitignored (globales out/-Pattern greift)
    manifest.json
    render-state.json
    audio/<clipKey>.wav
    candidates/<clipKey>/<seed>.wav
```

## 5. Datenmodell

### Item

Eine Stelle in der App, die spricht. Das ID-Schema ist identisch zu
`app/src/main/java/app/abcvorschule/debug/TtsDebugEntry.kt`, damit eine spätere
App-Integration ohne Übersetzungsschicht funktioniert:

```
atom:maus:lemma
sentence:s-oma-hat-hut:tts
task:l01-t1:phonemeTts
task:l01-t1:round:0:promptTts
finale:f-l01:tts        ← neu
ui:lockedLessonCue      ← aus extra-strings.json
```

`TtsDebugEntry.kt` deckt `finales.json` derzeit nicht ab. Das Tooling definiert das
`finale:`-Präfix eigenständig; die Angleichung der Kotlin-Seite ist als Notiz in
`docs/residual-review-findings/` festzuhalten, aber keine Code-Änderung dieser Spec.

### Clip

Die Render-Einheit. Mehrere Items mit identischem Text und Profil teilen sich einen Clip:

```
clipKey = "<profil>:<sha256(text)[:12]>"
```

Das Manifest hält beide Ebenen: `clips[]` als Render-Einheiten und `items[]` mit der
Zuordnung `itemId → clipKey`. So bleibt nachvollziehbar, welche App-Stelle welche Datei
benutzt, ohne denselben Satz dreimal zu rendern.

### Profil

Ein Use-Case mit eigener Instruktion und eigenem Seed-Pool. Das Profil ergibt sich per
Default aus dem Quellfeld; die UI kann es pro Clip überschreiben.

| Profil | Quellfeld | Instruktions-Richtung |
| --- | --- | --- |
| `word` | `atoms.lemma` | Einzelwort, klar, freundlich-neutral |
| `phoneme` | `phonemeTts` | **Lautwert**, gedehnt — nicht der Buchstabenname |
| `prompt` | `promptTts` | Kindergärtnerin: warm, deutlich, ruhiges Tempo, fragende Betonung |
| `miss` | `missTts` | sanft korrigierend, ohne Tadel |
| `reward` | `rewardTts` | fröhlich, feiernd |
| `stretch` | `stretchTts` | sehr langsam, Silben gedehnt ineinander |
| `sentence` | `sentences.tts` | einfacher, klarer Satz |
| `finale` | `finales.tts` | verspielt, pointiert |
| `ui` | `extra-strings.json` | ruhig, neutral |

Struktur in `profiles.json`:

```json
{
  "poolSalt": "v1",
  "profiles": {
    "prompt": {
      "label": "Aufgaben-Frage",
      "speaker": "sohee",
      "language": "german",
      "instruct": "Sprich wie eine freundliche Kindergärtnerin: warm, deutlich, ruhiges Tempo, leicht fragende Betonung am Satzende.",
      "sampling": { "temperature": 0.6, "top_k": 30, "top_p": 0.9, "repetition_penalty": 1.05 },
      "seedPool": [42, 1337, 8891]
    }
  }
}
```

Die Sampling-Defaults entsprechen den vom Nutzer vorgegebenen Basiswerten.

**Risiko `phoneme`:** Ob Qwen bei Eingabe „M" den Lautwert statt des Buchstabennamens
(„Em") spricht, ist offen und per Instruktion nicht garantiert steuerbar. Fallback ist
eine textuelle Umschreibung pro Buchstabe (`"mmmmm"` statt `"M"`) über einen
`textOverride` im Lock-Eintrag. Das klärt sich beim ersten Hörtest; die Datenstruktur
sieht den Override vor, damit kein Umbau nötig wird.

### Lock

Eine bewusste Einzelentscheidung zu genau einem Clip, in `locks.json`:

```json
{
  "version": 1,
  "locks": {
    "phoneme:9f2c1a7b4e08": {
      "seed": 991,
      "profile": "phoneme",
      "textOverride": "mmmmm",
      "note": "sprach sonst 'Em' statt des Lautwerts",
      "sourceText": "M"
    }
  }
}
```

Alle Felder außer `seed` sind optional. `profile` überschreibt die Feld-Zuordnung aus der
Tabelle oben, `textOverride` den zu sprechenden Text (der `clipKey` bleibt am
Originaltext hängen, damit die Zuordnung zur App-Stelle erhalten bleibt). `sourceText`
wird beim Schreiben mitgeführt, damit ein verwaister Lock in der Meldung lesbar ist
statt nur als Hash zu erscheinen.

## 6. Seed-Workflow

Vier Phasen, wiederholbar:

**1. Sampeln** — `tts sample --profile prompt -n 8` würfelt acht Seeds und rendert damit
drei repräsentative Strings des Profils. Ergebnis: 24 Kandidaten zum Durchhören.

**2. Kuratieren** — in der UI pro Kandidat „✓ In Pool" oder „✗ Verwerfen". Aufgenommene
Seeds landen in `profiles.json → seedPool`.

**3. Finaler Lauf** — jeder Clip zieht seinen Seed deterministisch-zufällig aus dem Pool:

```
seed = locks[clipKey].seed
       ?? seedPool[ int(sha256(clipKey + poolSalt), 16) % len(seedPool) ]
```

Das streut über die guten Seeds — nicht alle 530 Clips klingen identisch — bleibt aber
reproduzierbar: derselbe Clip bekommt bei jedem Lauf denselben Seed. Ein Bump von
`poolSalt` würfelt bewusst alles neu.

**4. Iterieren** — einzelne Clips daneben? In der UI „🎲 4 neue Kandidaten", besten wählen,
per 📌 als `locks[clipKey].seed` festnageln. Locks schlagen den Pool.

Ist der Pool eines Profils leer, wird derselbe Hash modulo 2³¹ direkt als Seed benutzt —
ein Lauf funktioniert also auch vor jeder Kuratierung und bleibt dabei reproduzierbar.
`status` weist ungefüllte Pools als Warnung aus, damit dieser Zustand nicht unbemerkt
zum Dauerzustand wird.

### Versionierung

`profiles.json`, `locks.json` und `extra-strings.json` enthalten die menschlichen
Entscheidungen und gehören ins Git. `out/` ist vollständig ableitbar und wird vom
bestehenden globalen `out/`-Pattern in `.gitignore` erfasst.

## 7. Inkrementelles Rendern

`out/render-state.json` speichert pro Clip den Hash über `(text, profil, instruct,
sampling, seed, audio-nachbearbeitung)`. Beim Lauf wird nur gerendert, was fehlt oder
dessen Hash sich geändert hat.

Praktische Folge: eine geänderte Instruktion rendert nur ihr Profil neu, ein geänderter
Satz in `tasks.json` nur den einen Clip. Der Regelfall ist damit ein Lauf von Sekunden,
nicht von 25 Minuten.

## 8. Audio-Nachbearbeitung

Vor dem Schreiben, abschaltbar per Flag und per Profil konfigurierbar:

- **Silence-Trim** — Qwen liefert regelmäßig Stille am Anfang und Ende. Getrimmt wird
  gegen einen Amplitudenschwellwert mit kurzem Sicherheitspolster (Default 30 ms), damit
  keine Konsonanten abgeschnitten werden. Ohne Trim hört das Kind in der App eine
  Verzögerung vor jeder Ansage.
- **Peak-Normalisierung** — auf −1 dBFS, damit `word`- und `reward`-Clips nicht
  unterschiedlich laut sind.

Master-Format: **24 kHz mono WAV, 16 bit PCM**. Verlustfrei, gesamt ~45 MB. Eine
Konvertierung nach OGG/Opus gehört zur App-Integration und ist hier nicht enthalten.

Die Nachbearbeitungsparameter fließen in den Render-State-Hash ein — ein geänderter
Schwellwert löst also korrekt ein Neu-Rendern aus.

## 9. CLI

```bash
tools/tts/tts extract                        # Content-JSON → out/manifest.json
tools/tts/tts status                         # fehlt / gerendert / gelockt / stale / verwaist
tools/tts/tts sample --profile prompt -n 8   # Kandidaten würfeln
tools/tts/tts render                         # finaler Lauf, inkrementell
tools/tts/tts render --profile reward --force
tools/tts/tts render --only "task:l01-t1:*"
tools/tts/tts web                            # UI auf :8420
```

`render` unterstützt `--dry-run` und meldet Fortschritt zeilenweise mit Restzeitschätzung.

## 10. Web-Interface (`:8420`)

Eine Seite, vier Bereiche:

- **Clip-Liste (links)** — alle Clips mit Status-Chip, Filter nach Profil / Lektion /
  Quelle / Status, Volltextsuche. `j`/`k` navigieren, `Space` spielt.
- **Detail (rechts)** — Text (read-only; Quelle der Wahrheit bleibt das App-JSON),
  Profil-Dropdown, aktuelles Audio, Button „4 Kandidaten erzeugen". Kandidaten erscheinen
  als Karten nebeneinander mit Seed-Nummer; `1`–`4` spielen ab, Buttons ✓ Pool /
  📌 Lock / ✗ Verwerfen.
- **Profil-Editor** — Instruktion und Sampling live editieren, Pool einsehen und
  ausmisten, „Auf 3 Beispielen testen".
- **Batch-Leiste** — „Finalen Lauf starten", Live-Fortschritt, Abbrechen.

**Nebenläufigkeit:** Ein einzelner Worker-Thread arbeitet eine FIFO-Queue ab — MPS
verträgt keine parallele Nutzung desselben Modells. Die UI bleibt responsiv und erhält
Fortschritt über Server-Sent Events auf `/events`. Das Modell wird beim Serverstart
einmal geladen.

Die UI schreibt ausschließlich in `profiles.json`, `locks.json` und `out/` — nie in das
Content-Pack der App.

## 11. Fehlerbehandlung

- **Speaker/Sprache ungültig** — Validierung beim Start gegen `get_supported_speakers()`
  und `get_supported_languages()`; schlägt laut fehl mit Auflistung der gültigen Werte,
  statt still danebenzugreifen.
- **Modell nicht ladbar** — der Server startet trotzdem, die UI zeigt „Engine offline"
  mit der Fehlermeldung; Kuratieren bereits gerenderter Clips bleibt möglich.
- **Generierung schlägt fehl** — der Clip wird als `failed` mit Meldung markiert, der
  Batch läuft weiter. `status` listet die Fehlschläge.
- **Leerer Text** — wird übersprungen und in `status` gemeldet.
- **Verwaiste Locks** — ändert sich ein Text im App-JSON, ändert sich sein `clipKey`.
  Das alte Lock wird nicht stillschweigend verworfen, sondern als „verwaist" gemeldet;
  Aufräumen ist eine bewusste Entscheidung des Nutzers.

## 12. Tests

Pytest, ausgeführt mit dem Qwen-venv-Interpreter.

**Ohne Modell** (der Großteil):

- Extraktion gegen ein Fixture-Content-Pack → erwartetes Manifest inkl. ID-Schema
- Dedup und `clipKey`-Bildung, inklusive „gleicher Text, anderes Profil → zwei Clips"
- Seed-Auflösung: Lock schlägt Pool; Pool-Auswahl ist stabil über Läufe hinweg; leerer
  Pool fällt sauber zurück; `poolSalt`-Änderung verschiebt die Auswahl
- Stale-Erkennung: Änderung an Text / Instruct / Sampling / Seed / Audio-Parametern löst
  jeweils ein Neu-Rendern aus, eine irrelevante Änderung nicht
- Verwaiste Locks werden erkannt und nicht gelöscht
- Trim und Normalisierung auf synthetischen Wellenformen
- Store-Roundtrip: schreiben, lesen, unveränderter Inhalt

**Mit Modell**, hinter `TTS_SMOKE=1`:

- Ein Clip wird zweimal mit demselben Seed gerendert → bit-identisch. Das ist der Test,
  der die Grundannahme des gesamten Seed-Konzepts absichert.
- Ausgabe hat 24 000 Hz und ist nicht stumm.

## 13. Nicht enthalten

- Jede Änderung an der App (`SpeechController`, Trainer, Assets)
- OGG/Opus-Konvertierung und APK-Größenbetrachtung
- Sprechtexte der abgeleiteten Trainer (Symbol-Jagd, Wort-Detektiv) — siehe §2
- Voice-Cloning oder VoiceDesign-Modelle; diese Spec nutzt ausschließlich CustomVoice
  mit dem Speaker `sohee`
