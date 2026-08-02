# Qwen-TTS-Audio in der App — Design

Status: design-approved (autonome Session; Kernentscheidungen vom Nutzer vorgegeben,
Detailentscheidungen hier dokumentiert)
Datum: 2026-08-02
Vorgänger: `2026-08-02-qwen-tts-audio-pipeline-design.md` (Tooling, §13 verweist die
App-Integration hierher)

## 1. Ziel

Approvete (= gelockte, fertig gerenderte) Qwen-TTS-Clips werden als OGG-Dateien in
die App ausgeliefert und dort abgespielt. Für alles, wofür (noch) kein Clip
existiert, spricht wie bisher Android-TTS. Ein Export-Schritt im TTS-Tooling
übernimmt die Konvertierung und das Aktualisieren der App-Assets — als CLI-Befehl
und als Button im Web-UI, damit der Weg „kuratieren → in die App bringen" ein
Klick ist.

## 2. Export im Tooling (`tools/tts/ttskit/export.py`)

**Auswahl:** Exportiert wird genau ein Clip, wenn er `locked` ist **und** sein
Status `rendered` (Fingerprint aktuell). Gelockte Clips mit Status `missing` oder
`stale` sowie verwaiste Locks werden übersprungen und im Bericht mit Grund
aufgeführt — ein Export produziert nie stillschweigend Lücken.

**Konvertierung:** WAV (24 kHz mono PCM16) → OGG/Opus via `soundfile`
(`format="OGG"`, `subtype="OPUS"`; 24 kHz ist eine native Opus-Rate, kein
Resampling). Begründung Opus statt Vorbis: minSdk 26 = Android 8.0, ab der
Ogg/Opus offiziell unterstützt ist; für Sprache das kompaktere und bessere Format.
`ffmpeg` wird nicht gebraucht — `soundfile` liegt bereits im venv.

**Ziel und Dateinamen:** `app/src/main/assets/audio/<profile>_<hash12>.ogg` —
der Doppelpunkt des clipKey wird zu `_` (Doppelpunkte sind in Zip-Einträgen
riskant und auf Windows verboten). `.ogg` steht in AAPTs Default-noCompress-Liste,
`AssetFileDescriptor`-Playback funktioniert also ohne Gradle-Sonderkonfiguration.

**Index:** `app/src/main/assets/audio/index.json`:

```json
{
  "version": 1,
  "clips": {
    "<sourceText>": { "file": "sentence_0620b64d3955.ogg", "profile": "sentence" }
  }
}
```

Schlüssel ist der **Quelltext** (nicht der TextOverride): die App-Call-Sites
übergeben genau diesen String, und der clipKey ist aus ihm gehasht. Ein
TextOverride verbessert nur die Aussprache im Audio, ändert aber den Schlüssel
nicht. Kollision (gleicher Text in zwei Profilen, beide exportierbar): feste
Profil-Priorität `word > phoneme > prompt > miss > reward > sentence > finale > ui`,
Warnung im Bericht. Der Index ist deterministisch sortiert, ohne Zeitstempel —
saubere Diffs.

**Sync-Semantik:** Der Export besitzt `assets/audio/` vollständig: `.ogg`-Dateien,
die nicht mehr zum Export-Set gehören, werden entfernt (z. B. nach Unlock).
Andere Dateien im Ordner werden nicht angetastet.

**Einstiegspunkte:**
- CLI: `tts export` (Bericht auf stdout, Exit ≠ 0 nur bei echten Fehlern,
  Überspringen ist kein Fehler)
- Server: `POST /api/export` → Bericht als JSON
- Web-UI: Button „In App exportieren" im Kopfbereich, zeigt danach
  exportiert/übersprungen (mit Gründen) an

## 3. App-Integration (`app/src/main/java/app/abcvorschule/speech/`)

**`ClipIndex`** — lädt `audio/index.json` über denselben injizierbaren
`openAsset`-Seam wie `ContentRepository`; Lookup per exaktem Textvergleich
(getrimmt). Fehlender oder kaputter Index ⇒ leerer Index, App verhält sich wie
heute.

**`ClipPlayer`** — spielt ein Asset über `MediaPlayer` +
`AssetFileDescriptor` (Completion-Callback nötig für `speakAndAwait`;
SoundPool hat keinen). Jeder Abspielfehler fällt auf Android-TTS zurück.

**`SpeechController`** behält seine öffentliche Oberfläche (`speak`,
`speakAndAwait`, `stop`, `shutdown`, `available`, `speaking`) und bekommt intern
den Clip-Pfad davor: `speak(text)` sucht im Index; Treffer ⇒ Clip, sonst ⇒ TTS.
Flush-Semantik bleibt: jeder `speak`-Aufruf stoppt laufende Clips **und**
laufende TTS-Ausgabe. `speaking` spiegelt beide Quellen. `available` bleibt die
TTS-Engine-Verfügbarkeit (steuert weiterhin die bestehende Logik, z. B.
synthetische Mathe-Prompts); Clips spielen auch bei `available == false`.
Konstruktion weiterhin einzig in `MainActivity` — kein neuer DI-Mechanismus.

## 4. Einmalige Konvertierung (Teil dieser Arbeit)

- `locks.json` und `profiles.json` aus dem Haupt-Checkout übernehmen und
  committen (kuratierte Entscheidungen gehören laut Tooling-Doku in git).
- `out/audio` + `render-state.json` (gitignored) in den Worktree kopieren,
  `tts export` ausführen, entstehende OGGs + `index.json` committen.
- Erwartung: 22 Clips exportiert, 2 gelockte ohne Audio übersprungen
  (`phoneme:5c62e091b8c0`, `word:006a933c950f`).

## 5. Tests

- **Tooling:** `tests/test_export.py` — Auswahl (locked+rendered), Skip-Gründe,
  Kollisionspriorität + Warnung, Sync-Löschung, Index-Format, Dateinamens-Mapping.
  Wie bestehende Tests mit relokierten `Paths`; die OGG-Schreibung läuft echt
  über `soundfile` (winzige Dateien).
- **App (JVM):** `ClipIndex`-Parsing und -Lookup; `src/main/assets` ist bereits
  Test-Resource, der Test sieht also den echten Index. `ClipPlayer`/
  `SpeechController`-Playback bleibt ungetestet (Android-Framework), Fallback-
  Entscheidung (`ClipIndex.lookup`) ist als reine Logik testbar.
- **Build:** `./gradlew :app:testDebugUnitTest` und `assembleDebug` müssen grün sein.

## 6. Nicht enthalten

- Template-Strings (Symbol-Jagd/Wort-Detektiv) und zur Laufzeit komponierte
  Lob-Sätze — sprechen weiterhin Android-TTS (Spec-Vorgänger §2).
- `finale:`-Lücke in `TtsDebugEntry.kt`: für die Wiedergabe irrelevant, weil der
  Lookup textbasiert ist — Finale-Clips greifen automatisch, sobald sie approved
  sind. Bleibt ein Tooling-/Debug-Thema (residual-review-findings).
- itemId-basierte Sprech-API (Call-Sites tragen weiterhin nur Text).
- APK-Größen-Optimierung — bei 22 Clips (~32 s Audio) irrelevant.

## 7. Amendments während der Umsetzung

- **Fingerprint im Index:** Jeder Index-Eintrag trägt zusätzlich zu `file` und
  `profile` ein `fingerprint`-Feld (`plan.fingerprint(clip, profile)`, vom
  Export selbst berechnet und im eigenen `index.json` gemerkt — unabhängig von
  `render-state.json`, siehe Amendment weiter unten). Der Export ist dadurch
  inkrementell: die OGG/Opus-Bytes sind pro Encode NICHT reproduzierbar
  (`soundfile`/libsndfile schreibt eine zufällige Ogg-Bitstream-Seriennummer),
  ohne Fingerprint-Vergleich würde jeder Lauf also alle Dateien neu schreiben,
  obwohl sich inhaltlich nichts geändert hat.
- **41 statt 22 Clips exportiert:** Die Kuratierung lief nach diesem Design
  weiter — inzwischen liegen 53 Locks vor, davon 41 gelockt und rendered.
  §4 nennt noch den Stand zum Zeitpunkt des Designs.
- **Lösch-Semantik folgt Unlocks, nicht dem lokalen Render-Stand:** `out/`
  (WAV, `render-state.json`) ist gitignored. Auf einem frischen Checkout hat
  jeder gelockte Clip lokal den Status `missing`, obwohl seine Datei längst in
  `assets/audio/` committet ist. Der Export löscht deshalb nur noch Dateien,
  die zu keinem aktuell gelockten Clip mehr gehören (z. B. nach einem Unlock
  oder einer Textänderung) sowie Dateien ohne jeden zugehörigen Lock. Ein
  gelockter, aber lokal nicht (mehr) gerenderter Clip (`missing`) behält seine
  bereits exportierte Datei und seinen Index-Eintrag unangetastet bei — sonst
  würde ein einzelner Export-Lauf auf einem frischen Checkout ohne `out/`
  sämtliche committeten Clips löschen.
- **Der Status-Wert `stale` wurde vollständig entfernt.** `plan.status_of`
  kannte ursprünglich `missing` / `stale` / `rendered`, wobei `stale` einen
  gelockten, bereits produzierten Clip anzeigte, dessen Render-Fingerprint
  nicht mehr zu Profil-Instruktion, Sampling, Trim/Normalisierung oder der
  Nachbearbeitungs-Version passte. Das invalidierte bereits bestätigten
  (gelockten) Content jedes Mal, wenn jemand nur die Instruktion eines Profils
  verbesserte oder einen Seed in den Pool aufnahm — genau das durfte laut
  Anforderung nicht passieren. `status_of` kennt seither nur noch `missing`
  (keine Datei) und `rendered` (Datei existiert), unabhängig von Profil- oder
  Lock-Änderungen seit dem Render. `RenderState` verlor sein `entries`-Feld
  (Fingerprint pro Clip) komplett — `render-state.json` merkt sich nur noch
  `failures`. Ein Re-Render bereits vorhandener Dateien ist seither immer eine
  bewusste Handlung (`tts render --force`, ggf. mit `--only`, oder Löschen der
  Datei), nie ein stillschweigender Seiteneffekt eines geänderten Profils.
  Betrifft `tools/tts/ttskit/{plan,render,server,cli,export}.py` und die
  Web-UI (kein „veraltet"-Status/-Filter mehr); die App-Integration selbst
  (Kapitel 1–4 dieses Dokuments) ist davon nicht betroffen — der Export-Gate
  bleibt „gelockt und `rendered`", nur wie `rendered` bestimmt wird, hat sich
  vereinfacht.
