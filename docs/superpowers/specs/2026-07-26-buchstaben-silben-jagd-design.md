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
   lückenloser) Reihenfolge, mit variabler Wiederholung pro Lektion. (Alleinige
   Ownership für diese Korrekturen: dieser §0-Schritt — §8 ergänzt nur
   Jagd-spezifisches.)

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
  tatsächliche `pack.tasksOf(lesson).size` der geladenen Lektion ersetzt
  (**Reconciliation-Phase**). Nach dem Jagd-Feature müssen Session-Längen-Assertions
  die synthetischen Inserts mitzählen: erwartet =
  `pack.tasksOf(lesson).size + insertSymbolHuntsCount` (0–2), oder ein eigener
  Session-Test deckt post-Insertion ab und `LessonSessionTest` bleibt
  pack-authored-only.
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
Batterie zurückzusetzen. Läuft **bis zu zweimal pro Lektion** — je ein
Jagd-Schritt nach dem Spurensucher-Block bzw. nach dem Verschmelzer-Block,
sofern `letter_trace` bzw. `syllable_merge` in der Lektion vorkommt; fehlt der
Kind, entfällt die entsprechende Jagd ersatzlos.

Jeder eingefügte Jagd-Trainer kann **mehrere Runden** haben (eine pro Quell-Runde
der zugehörigen `letter_trace`- bzw. `syllable_merge`-Tasks). „Bis zu zweimal“
zählt Trainer-Einfügungen, nicht Einzelrunden.

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

Dadurch benötigt `ContentValidator` keine zusätzlichen `@SerialName`-Regeln für
autorierten Content (synthetische Tasks erscheinen nie in `lesson.taskIds`); die
Reconciliation-Änderungen aus §0 bleiben Voraussetzung. Die Jagd funktioniert
automatisch für alle Lektionen, die den jeweiligen Quell-Kind führen — ohne
Autorenaufwand. Pack-Zeit-Invarianten für inserierbare Runden (nicht-leerer
Distraktor-Pool bzw. dokumentierter Skip) laufen über die reine Ableitungsfunktion
und einen Gate-Test `insertSymbolHunts × alle Lektionen` (§6 / §7).

### Datenmodell (weiterhin ein echter `TaskSpec`/`TrainerRound`, nur nicht persistiert)

```kotlin
enum class SymbolHuntMode { letter, syllable }

// TrainerKind bekommt einen Eintrag `symbol_hunt` (für when-Exhaustiveness in
// TrainerHost/kind-Property), erscheint aber nie in autoriertem Content.
@Serializable
@SerialName("symbol_hunt")
data class SymbolHuntSpec(override val id: String, val rounds: List<SymbolHuntRound>) : TaskSpec

@Serializable
data class SymbolHuntRound(
    override val promptTts: String,
    val targetAtomId: String,
    val mode: SymbolHuntMode,
) : TrainerRound
```

`scoredAtomIds()` → `listOf(targetAtomId)`. `@Serializable` / `@SerialName` sind
nötig, weil `TaskSpec` eine kotlinx.serialization-sealed-Hierarchie ist —
polymorphe Codegen bricht sonst. Autorierter JSON-Content enthält dennoch nie
`symbol_hunt`; die Specs entstehen nur zur Laufzeit.

**Stable IDs:** `SymbolHuntSpec.id` =
`{lessonId}:symbol_hunt:{letter|syllable}` (deterministisch).

**Resume:** Wenn `insertSymbolHunts` die Trainerliste gegenüber einem
`SessionSnapshot` erweitert/ändert, Snapshot invalidieren (wie bei Pack-Drift) —
kein Resume auf verschobene `trainerIndex`-Werte. Mid-round-State
(Batteriefüllung, collected tile ids, scatter seed, consecutive misses) gehört
zum Session-Snapshot der aktuellen Runde oder wird beim Verlassen der Runde
zurückgesetzt; Vor/Zurück zwischen **abgeschlossenen** Runden bleibt aktiv.

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

**Konvention:** Autorierte JSON-Einträge sind Tasks; Laufzeit-Einheiten in der
Session-Liste sind Trainers (`ScheduledTrainer`). `insertSymbolHunts` arbeitet auf
der Trainer-Liste und liest Runden aus den darunterliegenden `TaskSpec`s.

### Runden-State-Maschine (multi-round)

Jeder `SymbolHuntSpec` mit `rounds.size > 1` folgt dem bestehenden
Rundenmodell (`RoundProgressDots`, Chevrons):

1. Rundenstart: leere Batterie, frischer Scatter-Seed, consecutive-miss = 0.
2. Rundenabschluss (natürlicher Erfolg oder Resolve): bestehende globale
   Erfolgs-/Reveal-Pipeline; danach Auto-Advance zur nächsten Hunt-Runde.
3. Letzte Runde abgeschlossen → nächster Trainer in der Session.
4. Vor/Zurück: zwischen abgeschlossenen Runden immer möglich; innerhalb einer
   unfertigen Runde entweder exakten Mid-round-State wiederherstellen oder die
   Runde beim Verlassen resetten (Implementierung wählt eine Variante und hält
   sie konsistent — Default: Mid-round-State im Snapshot).

## 3. Ableitung von Treffern und Ablenkern

- **Treffer:** das Zielatom, exakt **5** Kacheln (eigenständige Instanzen mit
  stabiler Instance-Id).
- **Pool-Klasse nach Mode (nicht roh `target.kind`):**
  - `letter`: Atome mit `AtomKind.letter` oder `AtomKind.digraph` (Digraphen, die
    per `letter_trace` geübt werden, mischen untereinander und mit Letters).
  - `syllable`: Atome mit `AtomKind.syllable` **nur**. `resultAtomId`s, die nicht
    `syllable` sind (aktuell z. B. `ma` als `word` in l01), sind keine gültigen
    Silben-Hunt-Ziele — Content korrigieren (`kind=syllable`) oder die Runde
    überspringen.
- **Distraktor-Pool-Quellen (enger als alle `scoredAtomIds`):**
  - Buchstaben-Jagd: `atomId`s aus `letter_trace`-Runden in Lektionen
    `1..currentLessonIndex`, abzüglich des Zielatoms; zusätzlich nur Atome, die
    vor dem Insert-Punkt der aktuellen Lektion bereits als Trace-Ziel
    vorkamen (nicht spätere `word_build`-Blöcke derselben Lektion).
  - Silben-Jagd: `resultAtomId`s aus `syllable_merge`-Runden derselben
    Quellenregel, gefiltert auf `AtomKind.syllable`.
- **Distraktor-Kachelzahl / Degeneration:**
  - 0 eindeutige Distraktoren → Hunt-Runde **überspringen** (kein
    distraktorfreies 5-von-5-Feld, keine erfundenen Symbole).
  - 1–2 eindeutige Distraktoren → Feldgröße anpassen: 3 Treffer + so viele
    Distraktor-Kacheln wie eindeutige Atome × höchstens 2 Wiederholungen
    (max. sinnvoll ≤ 7 Kacheln gesamt); nicht blind auf 6 Kopien auffüllen.
  - ≥3 eindeutige Distraktoren → 6 Distraktor-Kacheln (Wiederholung erlaubt bis
    6), 5 Treffer, Feld = 11.
- **Schreibform:** wie im Content eingeführt — Buchstaben-Jagd zeigt Versalien
  (`Atom.display` der Letter/Digraph-Atome), Silben-Jagd zeigt Kleinschreibung
  (`Atom.display` der Silben-Atome).

**Prinzip-2 / Tray-Ausnahme:** Die Jagd weicht bewusst vom Distraktor-Budget
(max. 2) und Tray ≤ 5 ab — Streusuche braucht mehr Ablenker-Kacheln. Die Ausnahme
wird in `docs/PRODUCT_PRINCIPLES.md` parallel zur Prinzip-9-Ausnahme vermerkt.

Diese Ableitung ist eine reine Funktion von `(ContentPack, currentLessonIndex,
targetAtomId, mode)` — deterministisch, ohne Live-Progress-Abhängigkeit, unit-
testbar unabhängig vom UI. Runden, die der Degenerationsregel nach skippen,
werden von `insertSymbolHunts` gar nicht erst in `rounds` aufgenommen.

## 4. Layout — bewusste Abweichung von Prinzip 9

Prinzip 9 verlangt "Aufgabe oben, Antworten unten". Diese Übung weicht davon
**absichtlich** ab (Nutzerentscheidung): die Kacheln verstreuen sich über den
**Aufgabenbereich unter einem festen Speaker-Streifen**, die Batterie sitzt im
Antwortbereich unten. Diese Ausnahme wird hier dokumentiert statt still zu
brechen.

- Fester Top-Streifen im Aufgabenbereich: `AbcSpeakerButton` wired an
  `round.promptTts` (kein lesbarer Instruction-Headline). `TaskShell`
  auto-spricht beim Rundeneintritt wie bei anderen Trainern; der Speaker ist die
  Replay-Affordance.
- `SymbolHuntLayout.scatter(seed: Long, tileCount: Int, bounds: Size):
  List<TileLayout>` — reine Funktion, deterministischer PRNG (Seed aus
  `roundIndex` kombiniert mit einem Versuchszähler), liefert nicht-
  überlappende Positionen, Größen (Streuung z. B. 0.8×–1.3× Basisgröße) und
  Farbindizes. **Constraints:** Hit-Box ≥ 56 dp, Mindestabstand zwischen
  Kachelmitten so, dass Finger-Misses unwahrscheinlich sind; Layouts unter dem
  Threshold werden verworfen und neu geseedet. Der Rand, den eine Kachel zum
  Feldrand hält, folgt **ihrem eigenen Radius** (Grundgröße × ihrer Skala ÷ 2,
  plus etwas Luft) — die zurückgegebenen Punkte sind Kachel*mittelpunkte*, ein
  fester prozentualer Rand ist auf Handybreite schmaler als der Radius der
  größten Kachel und schneidet sie am Bildschirmrand ab.
- Farben ausschließlich aus der bestehenden Palette
  (`SoftMint`, `SoftCoral`, `SoftSky`, `SoftGold`, `SoftSand`), rotierend nach
  Kachelindex — bereits für den dunklen Hintergrund kontraststark validiert,
  keine neuen Theme-Farben.
- Jede Kachel: gerahmt (Border), tippbar, zeigt das Symbol in seiner Schreibform.

## 5. Interaktion

- **Jeder Tipp** auf eine **aktive** Kachel (Treffer oder Fehltipp) spricht sofort
  das angetippte Symbol (`onSpeak(atom.lemma)`) — Prinzip 7 gilt auch hier.
- **Spent tiles:** Nach einem Treffer ist die Instanz nicht mehr tippbar
  (entfernt oder sichtbar dimmed); Taps auf spent tiles werden ignoriert (kein
  TTS, kein Miss).
- **Treffer, Batterie noch nicht voll:** Kachel fliegt ins nächste freie
  Batteriesegment (ease-in), kein `onResult`-Aufruf (analog zu den Sternen im
  Spurensucher — kein Fortschritts-Report pro Einzeltreffer). Die Instanz
  verlässt das Streufeld dauerhaft.
- **Fehltipp:** Haptik-Nudge, Feld mischt sich neu (neuer Scatter-Seed,
  animierter Übergang), Batteriestand bleibt unverändert. **Reshuffle**
  repositioniert nur noch ungefundene Treffer-Instanzen plus Distraktoren —
  Feldgröße schrumpft mit der Batterie (z. B. 11 → 10 → …). Pro Runde höchstens
  **ein** Miss-Report: der erste Fehltipp meldet
  `onResult(false, false, listOf(targetAtomId))`; weitere Fehltipps reshufflen
  nur (kein weiteres Adaptivitäts-Update), bis Resolve oder Erfolg.
- **Festgefahren:** nach 6 aufeinanderfolgenden Fehltipps erscheint
  `AbcResolveButton` (gleicher Zahlenwert wie der Spurensucher; bewusste
  Wiederverwendung der Schwelle). Tippen füllt die restlichen Batteriesegmente
  automatisch (kurze Animation), **kein** `AbcContinueButton`, meldet
  `onResult(false, true, listOf(targetAtomId))` und tritt in die bestehende
  RevealAnswer-Pipeline ein (Ziel-Lemma einmal sprechen), analog Spurensucher-
  Resolve.
- **Batterie voll (letzter natürlicher Treffer):** verbleibende Kacheln
  verblassen/verschwinden (ease-out), die Batterie animiert ins Bildzentrum und
  pulsiert/leuchtet in den App-eigenen Erfolgsfarben. Danach erscheint
  `AbcContinueButton` ("Weiter >") im Antwortbereich — **bewusste lokale
  Ausnahme** vom sonst automatischen Fortschritt (Nutzerentscheidung, parallel
  zur Prinzip-9-Ausnahme): dieses eine Mal wartet der Screen auf einen Tipp,
  bevor er `onResult(true, false, listOf(targetAtomId))` auslöst und damit die
  bestehende globale Erfolgs-Pipeline übernimmt (Sprich-Phase, Sternenpop,
  Auto-Weiter — plus `successSpeakTextForCurrent`-Zweig, der
  `pack.atoms[round.targetAtomId]?.lemma` spricht).

## 6. Validator & Content

Keine neuen JSON-Felder für `symbol_hunt`. Die Reconciliation aus Abschnitt 0 ist
die einzige Änderung am ContentValidator-Regelwerk für autorierte Lektionen.

Zusätzlich (Pack-Zeit / Unit-Gate, ohne persistierten TaskSpec):

- Jede von `insertSymbolHunts` erzeugte Runde erfüllt die Pool-/Degenerationsregeln
  aus §3, oder die Runde wird weggelassen.
- Gate-Test: für jede Lektion des Packs `insertSymbolHunts` ausführen und
  ableiten — keine Runde mit 0-Distraktor-Pool in der finalen Liste; Silben-Ziele
  haben `AtomKind.syllable`.

## 7. Tests

- `SymbolHuntLayoutTest` — Determinismus, Nicht-Überlappung, Feldgröße, Min-
  Hit-Box / Re-Seed (analog `TraceGeometryTest`).
- Distraktor-Ableitungstest (reine Funktion) — Mode-Pool (letter/digraph vs
  syllable), enge Quellen (trace/merge only), Degeneration bei 0 / 1–2 /
  ≥3 Distraktoren, Skip von non-syllable `resultAtomId`s.
- `insertSymbolHunts`-Test — korrekte Platzierung, korrektes Überspringen bei
  fehlendem `syllable_merge` (z. B. `l12`), korrekte Rundenanzahl aus mehreren
  `letter_trace`-Tasks, stabile IDs, Gate über alle 18 Lektionen.
- SessionViewModel: Snapshot-Invalidierung nach Insertion; Progress-Bar-Total =
  erweiterte Trainerliste; optional Mid-round-Resume.
- Rundenverhalten: Fehltipp mischt ohne Batterieverlust und ohne Re-Tap auf
  spent tiles; ein Miss-Report pro Runde; Resolve-Schwelle nach 6 Fehltipps
  ohne Continue-Gate; multi-round Advance.
- Reconciliation-Tests (Abschnitt 0) grün nach dem Sync.

## 8. Dokumentation

`AGENTS.md` "Kind-UI-Regeln (Kurz)" bekommt eine neue Zeile für die Jagd
(Kurzbeschreibung, Platzierung, "kein autorierter Content").
`docs/PRODUCT_PRINCIPLES.md` Abschnitt 3 wird um bis zu zwei synthetische
Jagd-Schritte (bedingt auf vorhandene `letter_trace`/`syllable_merge`-Blöcke)
ergänzt; die Ausnahme zu Prinzip 9 (Streufeld/Batterie-Platzierung) und die
Ausnahme zu Prinzip 2 / Tray-Budget (bis 6 Ablenker-Kacheln im Streufeld) werden
dort vermerkt.

Die Korrektur der „Sechs Trainer“-Zeile auf das monotone Modell liegt
ausschließlich bei §0 Schritt 4 — nicht nochmals hier.

## Deferred / Open Questions

### From 2026-07-26 review

- **Goal is a solution shape, not a child literacy outcome** — §1 Ziel des Features (P0, product-lens, confidence 75)

  Without a falsifiable child outcome (e.g. faster grapheme recognition in clutter), the team can ship a polished hunt that never answers whether preschoolers needed visual search at all.

- **No-new-content synthesis under-justified against a finite authored pack** — §2 Kein neuer Content (P1, product-lens, confidence 75)

  Hunt volume and duration silently track every letter_trace/syllable_merge round authors add for other reasons; authors cannot QA a child-visible exercise in the content pack.

- **Monotone model may cement pack gaps without pedagogical intent** — §0 Content-Pack-Reconciliation (P1, product-lens, adversarial, confidence 75)

  Approving hunt also rewrites product identity from exactly-six trainers to variable monotone sequences; missing kinds (e.g. no syllable_merge in l12) may be authorship drift rather than intentional curriculum.

- **Reconciliation as separate workstream** — §0 Content-Pack-Reconciliation (P2, scope-guardian, confidence 75)

  Validator/test/doc monotone work is pre-existing pack debt; packaging it in the hunt spec couples review and rollback blast radius.

- **One hunt round per mode vs per trace/merge round** — §2 Einfügen (P2, adversarial, confidence 75)

  Round-per-source-round multiplies session length (e.g. l12: three letter-hunt rounds); alternatives are one letter + one syllable hunt per lesson or a multi-target round.

- **Resolve-after-6 equates corridor exits with wrong-glyph taps** — §5 Interaktion (P2, adversarial, confidence 75)

  Same numeric threshold as Spurensucher means different failure modes; may make Resolve too easy under reshuffle-without-battery-reset.

- **Digraph vs single-letter pool stratification** — §3 Ableitung (P2, adversarial, confidence 75)

  Mixing digraph targets with single-letter distractors assumes preschoolers can separate category and form; may need digraph-only pools or a pilot criterion.
