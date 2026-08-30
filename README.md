# ABC-Vorschul App

Kostenlose, werbefreie Android-Vorschul-App (ca. 4–7 Jahre) für Lesen und Rechnen auf Deutsch.
Helles, warmes Cream-UI, offline nach Installation. Ein Fibel-Pfad aus 34 Lektionen; jede Lektion
läuft sechs autorierte Trainer-Typen in fester didaktischer Reihenfolge über einen gemeinsamen
Content-Graphen — ein Typ darf sich wiederholen oder fehlen, zurück geht die Reihenfolge nie.
Zur Laufzeit abgeleitet, nie autoriert: Buchstaben-/Silben-Jagd und Wort-Detektiv als
Übungselemente zwischen den Trainern.

## Dokumentation

| Dokument | Inhalt |
|----------|--------|
| [`docs/PRODUCT_PRINCIPLES.md`](docs/PRODUCT_PRINCIPLES.md) | Produkt- und UX-Grundprinzipien (verbindlich) |
| [`AGENTS.md`](AGENTS.md) | Wie Agents arbeiten sollen (brainstorm → … → doc update) |
| [`docs/plans/`](docs/plans/) | Unified Plans / Product Contracts |
| `.cursor/rules/` | Immer aktive Cursor-Regeln |

## Requirements

- JDK 17+
- Android SDK (compileSdk 36)
- Emulator oder Gerät (deutsche TTS-Stimme empfohlen)

## Build

```bash
./gradlew :app:assembleDebug
./gradlew :app:testDebugUnitTest
```

Die Agent-Worktrees unter `.claude/worktrees/` tragen **keine** `local.properties`,
Gradle findet das SDK dort also nicht. Dort jedem Aufruf das SDK voranstellen, statt
eine `local.properties` anzulegen (die gehört nicht ins Repo, und die Worktrees werden
neu erzeugt):

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest
```

Install:

```bash
./gradlew :app:installDebug
```

### Instrumentierte Tests und Layout-Screenshots

> **Erst prüfen, wer sonst auf dem Emulator ist.** Mehrere Claude-Sessions arbeiten
> parallel in eigenen Worktrees und teilen sich den einen `uxreview`-Emulator; eine
> fremde Installation killt den eigenen Testprozess mitten im Lauf
> (`killDueToPackageUpdate` → „Process crashed" ohne Assertion). Prüfschritte, Lock und
> die restlichen Gegenmaßnahmen stehen in
> [`AGENTS.md` → Geteilter Emulator](AGENTS.md#geteilter-emulator).

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest
```

`SentenceOrderPegBoundsTest` misst die gerenderten Pegs des Satz-Architekten gegen
die Bühnenkante — die Rechnung dahinter prüft `SentencePegSizingTest` in den
Unit-Tests. `SymbolHuntTileBoundsTest` tut dasselbe für die Streukacheln der
Buchstabenjagd, gegen `SymbolHuntLayoutTest` als Geometrie-Gegenstück.
Die Shot-Tests behaupten nichts, sie **rendern**: Layout in mehreren Breiten und
Systemschriftgrößen, und Filmstreifen der Morphs bei angehaltener Testuhr. Sie
legen ihre PNGs an **zwei verschiedenen Orten** ab — welchen Weg man zum Abholen
braucht, hängt also am Test:

| Test | Zielordner | Abholweg |
|------|-----------|----------|
| `SentenceOrderPegShotTest` | `filesDir/pegshots` | `run-as` (A) |
| `SentenceOrderMorphShotTest` | `filesDir/morphshots` | `run-as` (A) |
| `WordBuildMorphShotTest` | `filesDir/wordbuildmorphshots` | `run-as` (A) |
| `SymbolHuntMorphShotTest` | `additionalTestOutputDir/huntmorphshots` | Gradle (B) |
| `SymbolHuntBatteryShotTest` | `additionalTestOutputDir/huntbatteryshots` | Gradle (B) |

**Weg A — `filesDir` + `run-as`.** `filesDir` statt `externalCacheDir`, weil das auf
einem frischen Emulator null ist. Gradle deinstalliert die APK nach dem Lauf, also
erst von Hand installieren und dann direkt instrumentieren:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk && adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

```bash
adb shell am instrument -w -e class app.abcvorschule.ui.exercise.SentenceOrderPegShotTest app.abcvorschule.test/androidx.test.runner.AndroidJUnitRunner
```

```bash
for f in $(adb shell run-as app.abcvorschule ls files/pegshots | tr -d '\r'); do adb exec-out run-as app.abcvorschule cat "files/pegshots/$f" > "$f"; done
```

`run-as` funktioniert nur auf debuggable Builds und **nicht auf einem nicht gerooteten
Gerät** — dort ist es gesperrt. Deshalb Weg B für die Jagd-Shots.

**Weg B — `additionalTestOutputDir`, von Gradle abgeholt.** Diese Tests lesen den
Ordner aus den Instrumentation-Argumenten; AGP setzt ihn selbst und zieht die Dateien
nach dem Lauf herunter. Kein `run-as`, kein manuelles Installieren:

```bash
ANDROID_SERIAL=emulator-5554 ./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.abcvorschule.ui.exercise.SymbolHuntBatteryShotTest
```

Die PNGs liegen danach lokal unter
`app/build/outputs/connected_android_test_additional_output/…/huntbatteryshots/` — AGP legt
darunter noch je einen Ordner pro Variante und Gerät an.
Kommt der Lauf ohne das AGP-Argument (etwa per `adb shell am instrument`), fallen beide
Tests auf `getExternalFilesDir(null)` zurück — den sieht die adb-Shell wegen Scoped
Storage aber nicht zuverlässig, also besser über Gradle laufen lassen.

## Content-Pack (Schema v2)

`app/src/main/assets/content/`

| Datei | Inhalt |
|-------|--------|
| `pack.manifest.json` | `schemaVersion`, `packId`, Titel, Locale |
| `atoms.json` | Buchstaben (mit Strichdaten für den Spurensucher), Silben, Wörter, Bildwörter |
| `sentences.json` | Sätze als Atom-Folgen |
| `tasks.json` | Ein Eintrag pro Trainer, `trainer`-Feld als Typ-Diskriminator, 1..n Runden |
| `lessons.json` | 34 Lektionen in Fibel-Reihenfolge; `authored` = spielbar, `planned` = Knoten gesperrt |
| `finales.json` | Ein kurzer Satz plus Bildreihe (`pictureAtomIds`) je Lektions-Ende |

Autoriert: alle 34 Lektionen (Phase 1–8) — 18 Basis-Lektionen mit dem Buchstabenpfad, 8
Wiederholungen und 8 Lektionen der Phase 8 für zusammengesetzte Wörter. Derzeit steht keine
Lektion auf `planned`; der Status bleibt im Schema erhalten, damit künftige Lektionen als gesperrte
Pfad-Knoten angelegt werden können, ohne Code zu ändern.

Die sechs autorierten Trainer-Typen in Rangfolge (`ContentValidator.TrainerOrder`):
`letter_trace` · `syllable_merge` · `word_build` · `sentence_order` ·
`sentence_picture` · `count_add`. `symbol_hunt` und `symbol_in_word` stehen nie im Content —
sie entstehen erst zur Laufzeit (`SessionTrainers`).

Der Validator prüft die Reihenfolge über den **Rang**, nicht über Vollständigkeit: eine autorierte
Lektion muss mit `letter_trace` beginnen, mit `count_add` enden und dazwischen nicht-fallend
bleiben. Wiederholte Typen (l01 hat zwei `letter_trace`, zwei `count_add`)
und ausgelassene Typen sind erlaubt, ein Rücksprung nicht. Abgelehnt wird ein Pack außerdem, wenn
eine autorierte Lektion einen abgeleiteten Trainer enthält, Kachelfolgen das Zielwort nicht
buchstabieren, eine Summe nicht stimmt, Strichdaten fehlen oder Referenzen ins Leere zeigen.

## Offline-Smoke-Skript (manuell)

1. `./gradlew :app:installDebug`, Gerät in den Flugmodus.
2. App öffnen → **Pfad-Screen** erscheint, der Pfad ist auf Lektion 1 gescrollt, ihr Schild pulsiert
   und trägt den wippenden „Du bist hier“-Marker; Lektionen 2–26 sind gesperrt (entsperren sich nach Mastery).
3. Gesperrten Knoten antippen → gesprochener Hinweis, kein stummes No-Op.
4. Lektion 1 öffnen und die Trainer der Reihenfolge nach durchspielen:
   Visueller Spurensucher (Buchstaben nachspuren, zweimal) ·
   optional Buchstaben-Jagd (Batterie voll → Feier, automatisch weiter, kein Weiter-Button) ·
   Silben-Verschmelzer · optional Silben-Jagd ·
   Wort-Bauer (Mama bauen) · Wort-Detektiv (Buchstabe im Wort antippen) ·
   Satz-Architekt (Wortschild aufhängen) · Satz-Versteher (Satz hören, Bildkarte tippen) ·
   zwei Rechenaufgaben.
   Bei Wort-Bauer und Satz-Architekt beide Bedienwege prüfen: eine Kachel per Ziehen platzieren
   (die gezogene Kachel liegt sichtbar über den Zielfeldern, nie darunter) und eine per Tippen
   (Tap-Alternative: Kachel antippen, dann Zielfeld antippen).
5. Bei jeder richtigen Antwort: Antwort wird vorgesprochen → Stern oben → dann nächste Runde.
6. Eine Rechenaufgabe zweimal falsch beantworten → gesprochener Hinweis, danach **Auflösen** nutzen:
   keine Punkte, Session läuft weiter.
7. Langer Druck auf ⋯ → Hilfestufe **Ohne Hilfe** erzwingen → nächste Rechenrunde zeigt die
   System-Zahlentastatur; **Mit Hilfe** → drei visuelle Antworten.
8. Mitten in der Lektion App killen und neu öffnen → dieselbe Lektion, dieselbe Runde.
9. Lektion beenden → Belohnungszusammenfassung → Weiter → zurück auf dem Pfad, Lektion 1
   als gemeistert markiert, Lektion 2 freigeschaltet. Der Marker hüpft dabei von Schild 1 zu
   Schild 2, die Trittspuren dazwischen werden warm, der Pfad scrollt mit.
10. Langer Druck auf ⋯ → „Reihenfolge frei wählbar“ an → eine späte Lektion (z. B. 12) öffnen und
    durchspielen → zurück auf dem Pfad ist ihr Schild hell mit Stern (nicht mehr abgedunkelt),
    Lektion 13 ist frei, der Marker bleibt aber auf Lektion 2.
11. Back auf dem Pfad verlässt die App; erneutes Öffnen zeigt den Fortschritt unverändert.

## Product notes

- Keine Werbung, keine Netz-Permission für die Kernpraxis.
- Eltern-Hilfestufe: Auto / Mit Hilfe / Ohne Hilfe hinter Long-Press-Gate; in Debug-Builds zusätzlich „TTS Debug“ im Eltern-Sheet.

