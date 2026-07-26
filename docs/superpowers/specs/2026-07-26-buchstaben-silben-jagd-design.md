# Buchstaben-/Silben-Jagd — Design

Status: approved for planning
Datum: 2026-07-26

## 0. Kontext: Content-Pack-Reconciliation (Vorbedingung)

Commit `588cf1f` hat `app/src/main/assets/content/{atoms,sentences,lessons,tasks}.json`
massiv erweitert (18 Lektionen statt 16, **alle** `authored`, variable Aufgabenzahl
pro Lektion statt exakt sechs), aber `app/src/test/resources/content/` (die
Classpath-Kopie, die JVM-Unit-Tests laden) nicht mitgezogen. Zusätzlich erzwingt
`ContentValidator.kt` noch die alte Regel: eine autorierte Lektion muss exakt die
sechs `TrainerOrder`-Kinds in dieser Reihenfolge halten. Gegen den neuen Pack
geprüft, verletzen das aktuell alle 18 Lektionen.

Geprüft über alle 18 Lektionen: die tatsächliche Reihenfolge ist **monoton**, nicht
exakt fix — jeder Task-Kind-Rang (`sound_position < letter_trace < syllable_merge <
word_build < sentence_order < count_add`) darf sich wiederholen oder ganz fehlen
(z. B. `l03`/`l12` ohne `sentence_order`, `l12` ohne `syllable_merge`), darf aber nie
zurückspringen. Jede Lektion beginnt mit `sound_position` und endet mit `count_add`.

**Das muss vor dem Feature gelöst werden**, sonst baut die Jagd auf einem
Fundament, das Validator und Tests widerspricht:

1. `ContentValidator.kt`: die Exact-Match-Prüfung (`kinds != TrainerOrder`) wird durch
   eine Monotonie-Prüfung ersetzt: für jede autorierte Lektion muss die Folge der
   Task-Kind-Ränge nicht-fallend sein, der erste Kind muss `sound_position`, der
   letzte `count_add` sein. Alle anderen Validator-Regeln (Atom-Referenzen,
   Distraktor-Budgets, Silben-Rechtschreibung etc.) bleiben unverändert.
2. `app/src/test/resources/content/*.json` wird 1:1 durch die aktuellen
   `app/src/main/assets/content/*.json` ersetzt (wie vor Commit `588cf1f`, wo beide
   identisch waren).
3. Betroffene Tests werden an das neue, variable Modell angepasst (siehe
   `Betroffene Tests` unten) — nicht durch Aufweichen der Assertions, sondern durch
   dynamische Prüfungen, die mit wiederholten/fehlenden Kinds umgehen.
4. `LessonModels.kt`-Kommentar ("Exactly the six trainers … when authored"),
   `docs/PRODUCT_PRINCIPLES.md` Abschnitt 3 ("führt genau sechs Trainer in fester
   Reihenfolge durch") und `AGENTS.md`s "Kind-UI-Regeln (Kurz)"-Zeile ("Sechs
   Trainer pro Lektion in fester Reihenfolge") werden auf das monotone Modell
   korrigiert: sechs Trainer-**Typen**, in fester (aber nicht zwingend
   lückenloser) Reihenfolge, mit variabler Wiederholung pro Lektion.

### Betroffene Tests (müssen anpasst werden)

- `ContentRepositoryTest.kt` — Lektionsanzahl (18 statt 16), autorierte Anzahl (18
  statt 6), `l01`-spezifische `taskIds.size`/Cast-Annahmen (t1..t6 fix) werden durch
  `filterIsInstance`/`kind`-basierte, positionsunabhängige Prüfungen ersetzt.
- `ContentValidatorTest.kt` — `everyAuthoredLessonHasAllSixTrainersInOrder` wird zu
  einer Monotonie-Prüfung; `plannedLessonWithTasksIsRejected` mutiert eine
  autorierte Lektion künstlich zu `planned` (statt eine reale planned Lektion zu
  suchen, da es im neuen Pack keine mehr gibt).
- `LessonCoverageTest.kt` — jede Stelle mit `.first { kind == X }` / `.single()`
  über Trainer-Kinds wird zu `.filter { ... }` (mehrere Tasks je Kind möglich);
  Prüfungen, die eine Kind zwingend voraussetzen (`sentence_order`,
  `syllable_merge`), werden übersprungen, wenn die Lektion diesen Kind nicht führt.
  Der Plan-Kind-Count "10 planned" entfällt (0 planned im neuen Pack).
- `LessonSessionTest.kt` — hartkodierte `6` (Task-/Rundenanzahl) wird durch die
  tatsächliche `pack.tasksOf(lesson).size` der geladenen Lektion ersetzt.
- `LessonGatingTest.kt` — der Test für gesperrte/`planned` Lektionen baut sich
  einen eigenen kleinen In-Memory-`ContentPack`-Fixture mit einer künstlichen
  `planned`-Lektion, statt sich auf eine reale zu verlassen (Business-Logik
  bleibt testbar, unabhängig vom aktuellen Curriculum-Stand).

Kein Test referenziert die entfernten Alt-Atome (`karotte`, `mond`, `salat`, `uhr`)
oder eine entfernte Satz-ID — das Sync in Schritt 2 ist insofern gefahrlos.

## 1. Ziel des Features

Ein neuer Übungsschritt "Finde alle Buchstaben X" (bzw. "Finde alle Silben X"):
ein Suchfeld mit verstreuten Symbolen in unterschiedlicher Farbe/Größe, das Kind
tippt alle Vorkommen des Zielsymbols an, korrekte Treffer füllen eine 5-Segment-
Batterie unten im Antwortbereich; ein Fehltipp mischt das Feld neu, ohne die
Batterie zurückzusetzen. Läuft **zweimal pro Lektion**: einmal für Buchstaben
(direkt nach dem Spurensucher-Block), einmal für Silben (direkt nach dem
Verschmelzer-Block).

## 2. Kein neuer Content — abgeleitete virtuelle Trainer

Aus der Reconciliation (monotones Modell, "kein neuer Content") folgt eine
bewusste Abweichung vom ursprünglich skizzierten Ansatz: **kein neuer
`TaskSpec` in `tasks.json`/`lessons.json`.** Stattdessen synthetisiert
`SessionViewModel` die Jagd-Schritte zur Laufzeit aus bereits vorhandenen,
autorierten Runden derselben Lektion:

- **Buchstaben-Jagd:** eine Runde pro Runde aller `letter_trace`-Tasks dieser
  Lektion (in Auftrittsreihenfolge), Ziel = `round.atomId`. Gespleißt als ein
  synthetischer Trainer direkt nach dem letzten `letter_trace`-Trainer der
  Lektion.
- **Silben-Jagd:** eine Runde pro Runde aller `syllable_merge`-Tasks dieser
  Lektion, Ziel = `round.resultAtomId`. Gespleißt direkt nach dem letzten
  `syllable_merge`-Trainer.
- Führt eine Lektion einen dieser Kinds gar nicht (z. B. `l12` ohne
  `syllable_merge`), entfällt die entsprechende Jagd für diese Lektion
  ersatzlos — kein Platzhalter, keine leere Batterie.
- `promptTts` wird aus einer festen Vorlage gebaut, nicht autoriert:
  „Finde alle Buchstaben {Atom.display}!" bzw. „Finde alle Silben
  {Atom.display}!".

Dadurch bleibt `ContentValidator` komplett unangetastet für dieses Feature
(die synthetischen Tasks erscheinen nie in `lesson.taskIds`), und die Jagd
funktioniert automatisch für alle 18 (und künftige) Lektionen ohne
Autorenaufwand.

### Datenmodell (weiterhin ein echter `TaskSpec`/`TrainerRound`, nur nicht persistiert)

```kotlin
enum class SymbolHuntMode { letter, syllable }

// TrainerKind bekommt einen Eintrag `symbol_hunt` (für when-Exhaustiveness in
// TrainerHost/kind-Property), erscheint aber nie in autoriertem Content.
data class SymbolHuntSpec(override val id: String, val rounds: List<SymbolHuntRound>) : TaskSpec

data class SymbolHuntRound(
    override val promptTts: String,
    val targetAtomId: String,
    val mode: SymbolHuntMode,
) : TrainerRound
```

`scoredAtomIds()` → `listOf(targetAtomId)`. `SymbolHuntSpec` wird **nicht**
`@Serializable`/`@SerialName` benötigt — sie durchläuft nie die JSON-Deserialisierung.

### Einfügen in die Trainer-Sequenz

`SessionViewModel.openLesson()` baut heute `trainers = pack.tasksOf(lesson).map
{ schedule(it) }`. Ein neuer Nachbearbeitungsschritt
`insertSymbolHunts(trainers, pack): List<ScheduledTrainer>`:

1. Sammelt alle Runden aller `LetterTraceSpec`-Trainer der Liste (in Reihenfolge).
2. Baut daraus — falls nicht leer — einen synthetischen `SymbolHuntSpec(mode =
   letter)` und fügt ihn direkt nach dem letzten `letter_trace`-Trainer ein.
3. Analog für `SyllableMergeSpec` → `SymbolHuntSpec(mode = syllable)`, direkt
   nach dem letzten `syllable_merge`-Trainer.
4. Gibt die erweiterte Liste zurück; `ScheduledTrainer` für die Jagd trägt keine
   Scaffolds (das Feature nutzt keine Gerüst-Stufen).

Dieser Schritt ist reine Kotlin-Logik, unit-testbar ohne Content-Änderung.

## 3. Ableitung von Treffern und Ablenkern

- **Treffer:** das Zielatom, exakt **5** Kacheln.
- **Distraktor-Pool:** alle Atome mit **demselben `AtomKind` wie das Zielatom**
  (nicht hartkodiert "letter"/"syllable" — spätere Lektionen tracen auch
  Digraphen wie "Sch"/"ei" per `letter_trace`, die sollen sich untereinander
  mischen dürfen), die von Tasks in Lektionen `1..currentLessonIndex` bereits
  referenziert wurden (`scoredAtomIds()` aller Tasks dieser Lektionen,
  gefiltert auf `atom.kind == target.kind`), abzüglich des Zielatoms selbst.
- **Distraktor-Kachelzahl:** 6 Kacheln. Existieren weniger als 6 verschiedene
  Distraktor-Atome (z. B. `l01`: nur ein weiterer Buchstabe bekannt), werden
  vorhandene Distraktor-Atome mehrfach als eigenständige Kacheln wiederholt, bis
  6 Kacheln erreicht sind.
- **Feldgröße gesamt:** 11 Kacheln (5 Treffer + 6 Distraktoren).
- **Schreibform:** wie im Content eingeführt — Buchstaben-Jagd zeigt Versalien
  (`Atom.display` der Buchstaben-Atome, bereits großgeschrieben), Silben-Jagd
  zeigt Kleinschreibung (`Atom.display` der Silben-Atome).

Diese Ableitung ist eine reine Funktion von `(ContentPack, currentLessonIndex,
targetAtomId, mode)` — deterministisch, ohne Live-Progress-Abhängigkeit, unit-
testbar unabhängig vom UI.

## 4. Layout — bewusste Abweichung von Prinzip 9

Prinzip 9 verlangt "Aufgabe oben, Antworten unten". Diese Übung weicht davon
**absichtlich** ab (Nutzerentscheidung): die 11 Kacheln verstreuen sich über den
**gesamten Aufgabenbereich** (nicht nur oben), die 5-Segment-Batterie sitzt im
Antwortbereich unten. Diese Ausnahme wird hier dokumentiert statt still zu
brechen.

- `SymbolHuntLayout.scatter(seed: Long, tileCount: Int, bounds: Size):
  List<TileLayout>` — reine Funktion, deterministischer PRNG (Seed aus
  `roundIndex` kombiniert mit einem Versuchszähler), liefert nicht-
  überlappende Positionen, Größen (Streuung z. B. 0.8×–1.3× Basisgröße) und
  Farbindizes.
- Farben ausschließlich aus der bestehenden Palette
  (`SoftMint`, `SoftCoral`, `SoftSky`, `SoftGold`, `SoftSand`), rotierend nach
  Kachelindex — bereits für den dunklen Hintergrund kontraststark validiert,
  keine neuen Theme-Farben.
- Jede Kachel: gerahmt (Border), tippbar, zeigt das Symbol in seiner Schreibform.

## 5. Interaktion

- **Jeder Tipp** (Treffer oder Fehltipp) spricht sofort das angetippte Symbol
  (`onSpeak(atom.lemma)`) — Prinzip 7 gilt auch hier, Tippen liest vor.
- **Treffer, Batterie noch nicht voll:** Kachel fliegt ins nächste freie
  Batteriesegment (ease-in), kein `onResult`-Aufruf (analog zu den Sternen im
  Spurensucher — kein Fortschritts-Report pro Einzeltreffer).
- **Fehltipp:** Haptik-Nudge, Feld mischt sich neu (neuer Scatter-Seed,
  animierter Übergang), Batteriestand bleibt unverändert. Wird als Miss
  gemeldet: `onResult(false, false, listOf(targetAtomId))` — für Adaptivität,
  ohne Fortschrittsverlust.
- **Festgefahren:** nach 6 aufeinanderfolgenden Fehltipps erscheint
  `AbcResolveButton` (gleicher Schwellenwert wie der Spurensucher). Tippen
  füllt die Batterie automatisch und meldet `onResult(false, true,
  listOf(targetAtomId))`.
- **Batterie voll (5. Treffer):** verbleibende Kacheln verblassen/verschwinden
  (ease-out), die Batterie animiert ins Bildzentrum und pulsiert/leuchtet in
  den App-eigenen Erfolgsfarben. Danach erscheint `AbcContinueButton`
  ("Weiter >") im Antwortbereich — **bewusste lokale Ausnahme** vom sonst
  automatischen Fortschritt: dieses eine Mal wartet der Screen auf einen Tipp,
  bevor er `onResult(true, false, listOf(targetAtomId))` auslöst und damit die
  bestehende globale Erfolgs-Pipeline übernimmt (Sprich-Phase, Sternenpop,
  Auto-Weiter — keine neue Logik in `SessionViewModel` nötig außer einem neuen
  `successSpeakTextForCurrent`-Zweig, der `pack.atoms[round.targetAtomId]
  ?.lemma` spricht).

## 6. Validator & Content

Keine Änderungen für `symbol_hunt` selbst nötig (siehe Abschnitt 2) — die
Reconciliation aus Abschnitt 0 ist die einzige Validator-Arbeit in diesem Plan.

## 7. Tests

- `SymbolHuntLayoutTest` — Determinismus, Nicht-Überlappung, Feldgröße (analog
  `TraceGeometryTest`).
- Distraktor-Ableitungstest (reine Funktion) — Pool-Berechnung über mehrere
  Lektionen, Wiederhol-Auffüllung bei `l01` (nur 1 bekannter Distraktor-Buchstabe).
- `insertSymbolHunts`-Test — korrekte Platzierung, korrektes Überspringen bei
  fehlendem `syllable_merge` (z. B. `l12`), korrekte Rundenanzahl aus mehreren
  `letter_trace`-Tasks.
- Rundenverhalten: Fehltipp mischt ohne Batterieverlust, Miss wird gemeldet,
  Resolve-Schwelle greift nach 6 Fehltipps.
- Reconciliation-Tests (Abschnitt 0) grün nach dem Sync.

## 8. Dokumentation

`AGENTS.md` "Kind-UI-Regeln (Kurz)" bekommt eine neue Zeile für die Jagd
(Kurzbeschreibung, Platzierung, "kein autorierter Content"), zusätzlich zur
Korrektur der "Sechs Trainer"-Zeile auf das monotone Modell.
`docs/PRODUCT_PRINCIPLES.md` Abschnitt 3 wird um die zwei Jagd-Schritte
ergänzt und auf das monotone Modell korrigiert; die Ausnahme zu Prinzip 9
(Streufeld/Batterie-Platzierung) wird dort vermerkt.
