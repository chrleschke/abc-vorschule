# Rechnen ohne Raten — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bei Rechenergebnissen über 10 tippt das Kind die Antwort ein statt aus drei Kacheln zu wählen, und bekommt nach zwei Fehlversuchen eine Zähl-Hilfe, in der es die Rechnung mit dem Finger ausführt.

**Architecture:** Die Entscheidung „Kacheln oder Tippen" wandert aus der UI in eine reine Funktion (`MathHinting.inputFor`) und wird beim Trainer-Scheduling pro Runde gecacht — die UI bekommt `MathInputMode`, nicht mehr rohes `ScaffoldLevel`. Die Zähl-Hilfe wird in zwei Compose-freie Einheiten zerlegt (`CountingField` = Layout, `CountingState` = Tipp-Zustand), damit sie vollständig als JVM-Unit-Test prüfbar bleibt; `CountingAid` ist eine dünne Darstellung ohne eigenen Fachzustand.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), JUnit 4 (`org.junit.Assert.*`), Gradle.

**Spec:** [`docs/superpowers/specs/2026-08-26-rechnen-ohne-raten-design.md`](../specs/2026-08-26-rechnen-ohne-raten-design.md)

## Global Constraints

- **Keine Strafen.** Keine roten Markierungen, kein Punktabzug, keine Versuchsbegrenzung (PRODUCT_PRINCIPLES §8). Grün bedeutet ausschließlich „richtig".
- **Audio-First.** Kinder lesen nicht. Jeder neue Zustand, den das Kind verstehen muss, braucht einen gesprochenen Cue. Sichtbare Ziffern sind erlaubt — sie sind selbst Lerninhalt.
- **Kein Sprechen pro Tipp.** `SpeechController.speak` ruft auf jedem Kanal vor dem Enqueue `stopOutput(channel)`; schnell aufeinanderfolgende Äußerungen würgen sich ab. Pro Tipp nur Haptik.
- **Neue gesprochene Strings** müssen nach `tools/tts/extra-strings.json`, sonst existiert kein kuratierter Clip. Feste Strings, keine Interpolation — interpolierte Texte finden nie einen Clip.
- **font_scale 1.3** ist das Testgerät. Jede Größenrechnung wird dagegen geprüft, nicht gegen 1.0.
- **Deckel bleiben:** `MaxMathQuantity` = 30, Multiplikationsmatrix max. 5 Zeilen × 6 Spalten.
- **Keine Instrumentierungstests.** Das Projekt hat keinen `androidTest`-Source-Set. Prüfbare Logik gehört in Compose-freie Objekte unter `app/src/test/`. Kein neuer Source-Set in diesem Plan.
- **Build/Test:** `./gradlew :app:testDebugUnitTest` und `./gradlew :app:assembleDebug`.
- Kommentare auf Englisch oder Deutsch — beides ist im Repo etabliert; die Begründung („warum"), nicht die Wiederholung des Codes.

## File Structure

| Datei | Verantwortung |
| ----- | ------------- |
| `ui/exercise/MathHinting.kt` *(ändern)* | Reine Regeln des Rechen-Trainers: Miss-Feedback, Kachelwahl — **neu** die Eingabeart und die Miss-Schwellen der Eskalationsleiter. Beherbergt `MathInputMode`. |
| `session/SessionModels.kt` *(ändern)* | `ScheduledTrainer.mathScaffolds` → `mathInputs` |
| `session/SessionViewModel.kt` *(ändern)* | Füllt `mathInputs` beim Scheduling |
| `ui/exercise/TrainerHost.kt` *(ändern)* | Reicht `input` statt `scaffold` an `MathExercise` |
| `ui/exercise/CountingField.kt` *(neu)* | Reine Layout-Logik der Zähl-Hilfe: Fünferzeilen, Gruppen, Emoji-Größe, Weg-Zonen-Plätze |
| `ui/exercise/CountingState.kt` *(neu)* | Reiner Tipp-Zustand: welcher Tipp was mit dem Zähler macht, wann fertig |
| `ui/exercise/CountingAid.kt` *(neu)* | Compose-Darstellung der Zähl-Hilfe. Kein eigener Fachzustand. |
| `ui/exercise/VisualQuantityBoard.kt` *(ändern)* | `MultiplicationMatrixGrid` wird antippbar (optionaler Parameter) |
| `ui/exercise/NumberPad.kt` *(ändern)* | Spiegelt einen von außen gezählten Wert ins Feld |
| `ui/exercise/MathExercise.kt` *(ändern)* | Verdrahtet Eskalationsleiter, Zähl-Hilfe, Tastatur, Audio |
| `tools/tts/extra-strings.json` *(ändern)* | Zwei neue gesprochene Cues |
| `docs/PRODUCT_PRINCIPLES.md`, `AGENTS.md` *(ändern)* | Regeln an den Ist-Stand |

Tests: `app/src/test/java/app/abcvorschule/ui/exercise/MathHintingTest.kt` *(ändern)*, `CountingFieldTest.kt` *(neu)*, `CountingStateTest.kt` *(neu)*, `app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt` *(ändern)*.

---

### Task 1: Die Eingabeart als reine Regel

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/MathHinting.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/MathHintingTest.kt:20-23`

**Interfaces:**
- Consumes: `ScaffoldLevel` und `ParentMode` aus `app.abcvorschule.progress`
- Produces: `enum class MathInputMode { Tiles, Typed }`; `MathHinting.inputFor(scaffold: ScaffoldLevel, parentMode: ParentMode, answer: Int): MathInputMode`; `MathHinting.TypedAnswerFrom: Int`, `MathHinting.CountingAidFromMisses: Int`, `MathHinting.ResolveFromMissesTyped: Int`. Ersetzt `MathHinting.usesNumberPad(ScaffoldLevel)` ersatzlos.

- [ ] **Step 1: Den bestehenden Test ersetzen**

In `MathHintingTest.kt` den Test `numberPadOnlyOnAdvancedScaffold` (Zeilen 20–23) durch die folgenden drei Tests ersetzen und den Import `app.abcvorschule.progress.ParentMode` ergänzen:

```kotlin
    @Test
    fun advancedScaffoldTypesRegardlessOfHowSmallTheAnswerIs() {
        assertEquals(
            MathInputMode.Typed,
            MathHinting.inputFor(ScaffoldLevel.Advanced, ParentMode.Auto, answer = 2),
        )
    }

    @Test
    fun answersOverTenTypeEvenWhenTheDerivedScaffoldIsStillBeginner() {
        // Der Default ist ParentMode.Auto, und dort startet ein frisches Kind auf
        // ScaffoldLevel.Beginner. Griffe die Regel gegen das Scaffold statt gegen den
        // Eltern-Modus, liefe sie beim Normalnutzer ins Leere — das ist der Kern.
        assertEquals(
            MathInputMode.Tiles,
            MathHinting.inputFor(ScaffoldLevel.Beginner, ParentMode.Auto, answer = 10),
        )
        assertEquals(
            MathInputMode.Typed,
            MathHinting.inputFor(ScaffoldLevel.Beginner, ParentMode.Auto, answer = 11),
        )
    }

    @Test
    fun explicitBeginnerParentModeKeepsTilesEvenForTheHardestAnswer() {
        assertEquals(
            MathInputMode.Tiles,
            MathHinting.inputFor(ScaffoldLevel.Beginner, ParentMode.Beginner, answer = 30),
        )
        // Ein ausdrücklich fortgeschrittenes Scaffold bleibt davon unberührt:
        // die Ausnahme gilt der Schwere-Regel, nicht dem Scaffold.
        assertEquals(
            MathInputMode.Typed,
            MathHinting.inputFor(ScaffoldLevel.Advanced, ParentMode.Beginner, answer = 30),
        )
    }

    @Test
    fun theCountingAidOpensBeforeTheResolveButtonAppears() {
        assertTrue(MathHinting.CountingAidFromMisses < MathHinting.ResolveFromMissesTyped)
    }
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests '*MathHintingTest*'
```

Erwartet: Kompilierfehler — `MathInputMode` und `MathHinting.inputFor` existieren nicht.

- [ ] **Step 3: Regel implementieren**

In `MathHinting.kt` den Import `app.abcvorschule.progress.ParentMode` ergänzen, die Funktion `usesNumberPad` samt ihrem KDoc-Kommentar löschen und stattdessen einfügen:

```kotlin
/** Wie die Antwort einer Rechenrunde eingegeben wird. */
enum class MathInputMode { Tiles, Typed }
```

(auf Dateiebene, außerhalb von `object MathHinting`), sowie innerhalb von `object MathHinting`:

```kotlin
    /**
     * Ab diesem Ergebnis wird getippt statt gewählt — das Band `hard`
     * (ProgressionEngine.bandFor) beginnt bei 11. Eigene Konstante, obwohl
     * [QuantityRepresentation.SymbolicFrom] denselben Wert trägt: die eine Regel
     * entscheidet über die Eingabeart, die andere über die Darstellung einer Menge.
     * Sie dürfen sich unabhängig voneinander bewegen.
     */
    const val TypedAnswerFrom = 11

    /** Fehlversuche, nach denen die Zähl-Hilfe aufklappt. */
    const val CountingAidFromMisses = 2

    /**
     * Fehlversuche, nach denen im Tipp-Modus der Auflösen-Knopf erscheint — später
     * als im Kachel-Modus (dort weiterhin 2), damit die Zähl-Hilfe nicht
     * übersprungen werden kann. Ein echter Ausweg bleibt sie trotzdem.
     */
    const val ResolveFromMissesTyped = 4

    /**
     * Getippt wird bei fortgeschrittenem Scaffold — oder sobald das Ergebnis über
     * zehn liegt. Die zweite Hälfte prüft den *Eltern-Modus*, nicht das abgeleitete
     * Scaffold: der Default ist [ParentMode.Auto], und dort startet ein frisches Kind
     * auf [ScaffoldLevel.Beginner]. Gegen das Scaffold geprüft würde die Regel beim
     * Normalnutzer also nie greifen. Nur ein ausdrücklich gesetztes
     * [ParentMode.Beginner] behält überall die Kacheln — Elternentscheidung schlägt
     * Aufgabenschwere.
     */
    fun inputFor(scaffold: ScaffoldLevel, parentMode: ParentMode, answer: Int): MathInputMode {
        val typed = scaffold == ScaffoldLevel.Advanced ||
            (parentMode != ParentMode.Beginner && answer >= TypedAnswerFrom)
        return if (typed) MathInputMode.Typed else MathInputMode.Tiles
    }
```

- [ ] **Step 4: Test laufen lassen**

```bash
./gradlew :app:testDebugUnitTest --tests '*MathHintingTest*'
```

Erwartet: `MathHintingTest` grün. `MathExercise.kt` bricht jetzt die Kompilierung — das ist erwartet und wird in Task 2 behoben; `testDebugUnitTest` kompiliert `main` mit, also ist dieser Schritt erst nach Task 2 wirklich grün. **Falls der Build an `MathExercise.kt:51` scheitert: das ist der erwartete Zustand, direkt mit Task 2 weitermachen und dort gemeinsam committen.**

- [ ] **Step 5: Commit (zusammen mit Task 2)**

Task 1 und 2 sind ein Commit, weil die Signaturänderung den Aufrufer zwingend mitzieht. Siehe Task 2, Step 6.

---

### Task 2: `MathInputMode` bis in die UI durchreichen

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/session/SessionModels.kt:84`
- Modify: `app/src/main/java/app/abcvorschule/session/SessionViewModel.kt:238-241`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt:140-141`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt:34,51`
- Test: `app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt:94-119`

**Interfaces:**
- Consumes: `MathHinting.inputFor`, `MathInputMode` (Task 1)
- Produces: `ScheduledTrainer.mathInputs: Map<String, MathInputMode>` (ersetzt `mathScaffolds`); `MathExercise(..., input: MathInputMode, ...)` (ersetzt den Parameter `scaffold: ScaffoldLevel`)

- [ ] **Step 1: Den bestehenden Session-Test umschreiben**

In `LessonSessionTest.kt` den Test `mathScaffoldsAreIndependentPerFactAcrossACountAddTrainer` (Zeilen 94–119) ersetzen. Der Import `app.abcvorschule.ui.exercise.MathInputMode` und `app.abcvorschule.ui.exercise.MathHinting` kommt dazu:

```kotlin
    @Test
    fun mathInputsAreIndependentPerFactAcrossACountAddTrainer() {
        // A lesson's count_add rounds carry several arithmetic facts (lesson 1 has
        // 1+1 and 2+1, split across two count_add tasks in the expanded pack); each
        // fact must carry its own input mode instead of sharing a single one computed
        // from the first round. Flattened across every count_add task in the lesson
        // rather than just the first, since the decision is a pure function over
        // rounds regardless of which task they came from.
        val rounds = pack.tasksOf(pack.authoredLessons.first())
            .filterIsInstance<CountAddSpec>()
            .flatMap { it.rounds }
        assertTrue("need at least two count_add rounds to prove independence", rounds.size >= 2)
        val firstKey = ProgressionEngine.mathKey(rounds[0])
        val secondKey = ProgressionEngine.mathKey(rounds[1])
        assertNotEquals(firstKey, secondKey)

        val progress = LearnerProgress(
            mathStats = mapOf(secondKey to SkillStats(autoScaffold = ScaffoldLevel.Advanced)),
        )
        val mathInputs = rounds.associate { round ->
            val key = ProgressionEngine.mathKey(round)
            key to MathHinting.inputFor(
                scaffold = ProgressionEngine.scaffoldForMath(progress, key),
                parentMode = progress.parentMode,
                answer = round.answer,
            )
        }
        // Lektion 1 rechnet im Zahlenraum bis 5 — hier entscheidet also allein das
        // Scaffold, und die Ergebnis-Regel darf nichts daran verändern.
        assertTrue("lesson 1 answers must stay below the typed threshold", rounds.all { it.answer < MathHinting.TypedAnswerFrom })
        assertEquals(MathInputMode.Tiles, mathInputs.getValue(firstKey))
        assertEquals(MathInputMode.Typed, mathInputs.getValue(secondKey))
    }
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests '*LessonSessionTest*'
```

Erwartet: Kompilierfehler in `MathExercise.kt` (nutzt noch `usesNumberPad`) und im Test.

- [ ] **Step 3: Das Modell umstellen**

In `SessionModels.kt` Zeile 84 ersetzen — den darüberstehenden Kommentarblock (Zeilen 78–83) inhaltlich beibehalten und nur den letzten Satz anpassen:

```kotlin
    val mathInputs: Map<String, MathInputMode> = emptyMap(),
```

Import `app.abcvorschule.ui.exercise.MathInputMode` ergänzen. Den Kommentar darüber so ergänzen, dass er die neue Bedeutung trägt:

```kotlin
    /**
     * Die Eingabeart pro Rechen-Fakt der Runde. Ein Trainer-weiter Wert wäre
     * falsch: die Runden eines count_add-Trainers tragen verschiedene Fakten mit
     * verschiedenen Ergebnissen, und die Ergebnis-Regel entscheidet pro Runde.
     * Cached at schedule time: [SessionViewModel.advance] re-schedules the
     * trainer on every round transition, so a mid-round parent-mode change
     * takes effect from the very next round on — even within the same
     * trainer, not only once the next trainer starts (F7).
     */
```

- [ ] **Step 4: Das Scheduling umstellen**

In `SessionViewModel.kt` die Zeilen 238–241 ersetzen:

```kotlin
            mathInputs = spec.rounds.filterIsInstance<CountAddRound>().associate { round ->
                val key = ProgressionEngine.mathKey(round)
                key to MathHinting.inputFor(
                    scaffold = ProgressionEngine.scaffoldForMath(progress, key),
                    parentMode = progress.parentMode,
                    answer = round.answer,
                )
            },
```

Imports `app.abcvorschule.ui.exercise.MathHinting` und `app.abcvorschule.ui.exercise.MathInputMode` ergänzen, falls nicht vorhanden. (`ProgressionEngine` referenziert bereits `app.abcvorschule.ui.exercise.MathOperation` — die Richtung ist im Repo etabliert.)

- [ ] **Step 5: UI-Aufrufer umstellen**

In `TrainerHost.kt` die Zeilen 140–141 ersetzen:

```kotlin
            input = trainer.mathInputs[ProgressionEngine.mathKey(round)]
                ?: MathInputMode.Tiles,
```

In `MathExercise.kt` den Parameter in Zeile 34 ersetzen:

```kotlin
    input: MathInputMode,
```

den Import `app.abcvorschule.progress.ScaffoldLevel` entfernen und Zeile 51 ersetzen:

```kotlin
    val usePad = input == MathInputMode.Typed
```

- [ ] **Step 6: Volle Testsuite laufen lassen und committen**

```bash
./gradlew :app:testDebugUnitTest
```

Erwartet: alles grün.

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/MathHinting.kt app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt app/src/main/java/app/abcvorschule/session/SessionModels.kt app/src/main/java/app/abcvorschule/session/SessionViewModel.kt app/src/test/java/app/abcvorschule/ui/exercise/MathHintingTest.kt app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt
git commit -m "feat(rechnen): Ergebnisse über 10 werden eingetippt statt gewählt

Die Eingabeart hing bisher allein am ScaffoldLevel und griff damit nie dort, wo
Raten am meisten schadet. Neu entscheidet die Aufgabe mit: ab Ergebnis 11 wird
getippt, außer die Eltern haben ausdrücklich 'Mit Hilfe' gewählt. Die Regel
prüft den Eltern-Modus, nicht das abgeleitete Scaffold — im Default Auto startet
ein frisches Kind auf Beginner, gegen das Scaffold geprüft liefe sie ins Leere.

Die UI bekommt dafür MathInputMode statt rohem ScaffoldLevel."
```

---

### Task 3: `CountingField` — Layout der Zähl-Hilfe

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/CountingField.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/CountingFieldTest.kt`

**Interfaces:**
- Consumes: `MathOperation` (`ui/exercise/MathOperation.kt`), `MultiplicationMatrix.emojiSizeSp(columns: Int): Int`
- Produces: `object CountingField` mit `RowSize: Int`, `rows(count: Int): List<Int>`, `groupSizes(operation: MathOperation, left: Int, right: Int): List<Int>`, `objectCount(operation: MathOperation, left: Int, right: Int): Int`, `removeSlots(operation: MathOperation, right: Int): Int`, `totalRows(operation: MathOperation, left: Int, right: Int): Int`, `emojiSizeSp(operation: MathOperation, left: Int, right: Int): Int`

- [ ] **Step 1: Den Test schreiben**

Neue Datei `app/src/test/java/app/abcvorschule/ui/exercise/CountingFieldTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountingFieldTest {
    @Test
    fun quantitiesBreakIntoRowsOfFive() {
        assertEquals(emptyList<Int>(), CountingField.rows(0))
        assertEquals(listOf(3), CountingField.rows(3))
        assertEquals(listOf(5), CountingField.rows(5))
        assertEquals(listOf(5, 1), CountingField.rows(6))
        assertEquals(listOf(5, 5, 5, 5, 5, 5), CountingField.rows(30))
    }

    @Test
    fun everyQuantityUpToThirtyKeepsItsTotalAcrossTheRows() {
        (0..30).forEach { count ->
            assertEquals("count $count", count, CountingField.rows(count).sum())
        }
    }

    @Test
    fun noRowIsEverWiderThanFive() {
        (0..30).forEach { count ->
            assertTrue("count $count", CountingField.rows(count).all { it <= CountingField.RowSize })
        }
    }

    @Test
    fun plusKeepsItsTwoGroupsWhileTheOtherOperationsShowOne() {
        assertEquals(listOf(7, 8), CountingField.groupSizes(MathOperation.Add, 7, 8))
        // Minus zeigt nur die Ausgangsmenge; die weggenommenen wandern in die Weg-Zone.
        assertEquals(listOf(15), CountingField.groupSizes(MathOperation.Subtract, 15, 6))
        // Malnehmen rendert die Matrix, nicht Fünferzeilen — eine Gruppe aus allen Zellen.
        assertEquals(listOf(20), CountingField.groupSizes(MathOperation.Multiply, 4, 5))
    }

    @Test
    fun objectCountMatchesWhatIsActuallyDrawn() {
        assertEquals(15, CountingField.objectCount(MathOperation.Add, 7, 8))
        assertEquals(15, CountingField.objectCount(MathOperation.Subtract, 15, 6))
        assertEquals(20, CountingField.objectCount(MathOperation.Multiply, 4, 5))
    }

    @Test
    fun onlySubtractionHasATakeAwayZoneAndItHoldsExactlyTheRightOperand() {
        assertEquals(6, CountingField.removeSlots(MathOperation.Subtract, 6))
        assertEquals(0, CountingField.removeSlots(MathOperation.Add, 6))
        assertEquals(0, CountingField.removeSlots(MathOperation.Multiply, 6))
    }

    @Test
    fun emojiShrinkAsTheFieldGrowsTaller() {
        // 3 + 4: eine Zeile je Gruppe — volle Größe.
        val small = CountingField.emojiSizeSp(MathOperation.Add, 3, 4)
        // 16 + 11: vier plus drei Zeilen — der Turm muss schrumpfen, sonst läuft er
        // bei font_scale 1.3 aus dem Aufgabenblock.
        val large = CountingField.emojiSizeSp(MathOperation.Add, 16, 11)
        assertTrue("small=$small large=$large", small > large)
        assertEquals(CountingField.MaxEmojiSp, small)
    }

    @Test
    fun everyRoundTheCurriculumCanProduceFitsTheTaskBlockAndStaysReadable() {
        // Stichproben reichen hier nicht: der höchste Fall ist "30 − 26" (zwölf
        // Zeilen), und der entsteht nur, wenn ein fortgeschrittenes Scaffold die
        // Zahlen-Eingabe auch bei kleinem Ergebnis anschaltet. Deshalb über alle
        // Operandenpaare, die der Validator zulässt (MaxMathQuantity = 30).
        val fontScale = CountingField.LayoutFontScale
        var worst = 0f
        forEveryValidRound { operation, left, right ->
            val size = CountingField.emojiSizeSp(operation, left, right)
            val rows = CountingField.totalRows(operation, left, right)
            val height = rows * (size * fontScale + CountingField.RowGapDp)
            worst = maxOf(worst, height)
            assertTrue(
                "$operation $left/$right -> ${height}dp",
                height <= CountingField.TaskBlockDp,
            )
            assertTrue(
                "$operation $left/$right -> ${size}sp is too small to tap or read",
                size >= CountingField.MinEmojiSp,
            )
        }
        // Beweist, dass der Test überhaupt nah an die Schranke kommt — sonst wäre er
        // ein Test, der nichts prüft. Der höchste Fall ist "26 − 26" mit zwölf
        // Zeilen und landet rechnerisch bei ~297.6dp.
        assertTrue("worst case only reached ${worst}dp", worst > CountingField.TaskBlockDp * 0.9f)
    }

    /** Jede Runde, die der Validator zulässt: Operanden und Ergebnis bis 30, bei
     * Minus kein negatives Ergebnis, bei Malnehmen die Rasterdeckel. */
    private fun forEveryValidRound(body: (MathOperation, Int, Int) -> Unit) {
        (1..30).forEach { left ->
            (1..30).forEach { right ->
                if (left + right <= 30) body(MathOperation.Add, left, right)
                if (right <= left) body(MathOperation.Subtract, left, right)
            }
        }
        (1..MultiplicationMatrix.MaxRows).forEach { rows ->
            (1..MultiplicationMatrix.MaxColumns).forEach { columns ->
                if (rows * columns <= 30) body(MathOperation.Multiply, rows, columns)
            }
        }
    }

    @Test
    fun multiplicationDefersItsSizeToTheMatrixItReuses() {
        assertEquals(
            MultiplicationMatrix.emojiSizeSp(6),
            CountingField.emojiSizeSp(MathOperation.Multiply, 5, 6),
        )
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests '*CountingFieldTest*'
```

Erwartet: Kompilierfehler — `CountingField` existiert nicht.

- [ ] **Step 3: Implementieren**

Neue Datei `app/src/main/java/app/abcvorschule/ui/exercise/CountingField.kt`:

```kotlin
package app.abcvorschule.ui.exercise

/**
 * Layout-Regeln der Zähl-Hilfe — der antippbaren Menge, die nach zwei
 * Fehlversuchen an die Stelle der Aufgabenvisualisierung tritt (design doc
 * 2026-08-26-rechnen-ohne-raten).
 *
 * Zwei Dinge unterscheiden sie vom Aufgaben-Prompt:
 *
 * 1. Mengen ab 11 werden hier **ausgeschrieben** statt als Symbol + Ziffer
 *    ([QuantityRepresentation]). §8 verbietet die Emoji-Wand als *Aufgabe*;
 *    hier ist die Menge Werkzeug, und ohne Objekte gäbe es nichts anzutippen.
 * 2. Gebündelt wird in **Fünfern**, nicht in Paaren wie [QuantityGrouping].
 *    Die Fünferbündelung ist die Struktur, die das Kind für den Zahlenraum
 *    20/30 ohnehin braucht.
 *
 * Compose-frei, damit die Rechnungen als JVM-Test prüfbar bleiben.
 */
object CountingField {
    /** Objekte pro Zeile. */
    const val RowSize = 5

    /** Vertikaler Abstand zwischen zwei Zeilen, in dp — muss zu [CountingAid] passen. */
    const val RowGapDp = 4f

    /** Schriftskalierung, gegen die ausgelegt wird: das Testgerät steht auf 1.3,
     * und gegen 1.0 gerechnete Größen laufen dort über. */
    const val LayoutFontScale = 1.3f

    /** Volle Größe, wenn Platz ist. */
    const val MaxEmojiSp = 34

    /** Untergrenze: kleiner wird ein Emoji weder erkennbar noch sicher treffbar.
     * Der entartetste Fall ("26 − 26", zwölf Zeilen) landet bei 16sp, bleibt also
     * darüber. */
    const val MinEmojiSp = 14

    /**
     * Reserve auf die Höhenschranke. Zwei Gründe: die Rechnung zählt einen
     * Zeilenabstand zu viel (unter der letzten Zeile sitzt keiner), und ohne
     * Reserve landet der höchste Fall auf exakt [TaskBlockDp] — wo
     * Float-Rundung („20 × 1.3f") die Schranke kippen lässt.
     */
    const val SafetyDp = 2f

    /**
     * Höhe, die der Aufgabenblock der Zähl-Hilfe zugesteht. Grobe, bewusst
     * konservative Schranke für ein Telefon in Hochkant — sie deckelt die
     * Emoji-Größe, statt eine echte Messung zu ersetzen.
     */
    const val TaskBlockDp = 300f

    /** Fünferzeilen einer Menge; die letzte Zeile ist kürzer. */
    fun rows(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val full = count / RowSize
        val rest = count % RowSize
        return buildList {
            repeat(full) { add(RowSize) }
            if (rest > 0) add(rest)
        }
    }

    /**
     * Die Objektgruppen in Anzeigereihenfolge.
     *
     * Plus behält seine zwei Gruppen — der Zähler läuft über beide durch, und die
     * Aufgabe bleibt als Bild erkennbar. Minus zeigt nur die Ausgangsmenge; die
     * weggenommenen Objekte wandern in die Weg-Zone, statt Teil dieses Feldes zu
     * sein. Malnehmen liefert alle Matrixzellen als eine Gruppe — gerendert wird
     * es ohnehin als Raster, nicht in Fünferzeilen.
     */
    fun groupSizes(operation: MathOperation, left: Int, right: Int): List<Int> =
        when (operation) {
            MathOperation.Add -> listOf(left, right)
            MathOperation.Subtract -> listOf(left)
            MathOperation.Multiply -> listOf(left * right)
        }

    /** Wie viele Objekte insgesamt antippbar auf dem Schirm stehen. */
    fun objectCount(operation: MathOperation, left: Int, right: Int): Int =
        groupSizes(operation, left, right).sum()

    /**
     * Leere Plätze der Weg-Zone. Nur Minus hat eine: sie ist der Grund, warum das
     * Kind nicht mitzählen muss, wie viele es schon weggenommen hat — die Struktur
     * trägt die Zahl, und volle Zone heißt fertig.
     */
    fun removeSlots(operation: MathOperation, right: Int): Int =
        if (operation == MathOperation.Subtract) right else 0

    /**
     * Gerenderte Zeilen insgesamt. Bei Minus zählt die Weg-Zone mit — sie steht
     * unter dem Hauptfeld, damit alles fünf Spalten breit bleibt und nicht neben
     * dem Feld in die Breite läuft.
     */
    fun totalRows(operation: MathOperation, left: Int, right: Int): Int =
        when (operation) {
            MathOperation.Multiply -> left
            MathOperation.Add -> rows(left).size + rows(right).size
            MathOperation.Subtract -> rows(left).size + rows(right).size
        }

    /**
     * Emoji-Größe in sp. Malnehmen erbt die Größe der Matrix, die es ohnehin
     * wiederverwendet; sonst wird die Größe aus der verfügbaren Höhe *hergeleitet*
     * statt gestuft. Eine Stufentabelle deckt den entartetsten Fall nicht ab —
     * "30 − 26" ergibt zwölf Zeilen und entsteht, sobald ein fortgeschrittenes
     * Scaffold die Zahlen-Eingabe auch bei kleinem Ergebnis anschaltet. Hergeleitet
     * gilt die Höhenschranke per Konstruktion, für jede Runde, die der Validator
     * zulässt.
     *
     * Eine Zeile belegt `sizeSp × LayoutFontScale + RowGapDp`; bei `rows` Zeilen
     * bleiben also `TaskBlockDp / rows` je Zeile.
     */
    fun emojiSizeSp(operation: MathOperation, left: Int, right: Int): Int {
        if (operation == MathOperation.Multiply) return MultiplicationMatrix.emojiSizeSp(right)
        val rows = totalRows(operation, left, right)
        if (rows <= 0) return MaxEmojiSp
        val perRowDp = (TaskBlockDp - SafetyDp) / rows
        val fitting = ((perRowDp - RowGapDp) / LayoutFontScale).toInt()
        return fitting.coerceIn(MinEmojiSp, MaxEmojiSp)
    }
}
```

- [ ] **Step 4: Test laufen lassen**

```bash
./gradlew :app:testDebugUnitTest --tests '*CountingFieldTest*'
```

Erwartet: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/CountingField.kt app/src/test/java/app/abcvorschule/ui/exercise/CountingFieldTest.kt
git commit -m "feat(rechnen): Layout-Regeln der Zähl-Hilfe

Fünferzeilen statt Paarbündelung, Mengen ab 11 ausgeschrieben statt symbolisch,
und eine Emoji-Größe, die den höchsten Fall bei font_scale 1.3 im Aufgabenblock
hält. Compose-frei und damit als JVM-Test prüfbar."
```

---

### Task 4: `CountingState` — was ein Tipp bewirkt

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/CountingState.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/CountingStateTest.kt`

**Interfaces:**
- Consumes: `MathOperation`, `CountingField` (Task 3)
- Produces: `data class CountingState(val operation: MathOperation, val objectCount: Int, val removeSlots: Int, val tapped: Set<Int>)` mit `counted: Int?`, `complete: Boolean`, `tap(index: Int): CountingState`, `isTapped(index: Int): Boolean` und `CountingState.Companion.forRound(operation: MathOperation, left: Int, right: Int): CountingState`

- [ ] **Step 1: Den Test schreiben**

Neue Datei `app/src/test/java/app/abcvorschule/ui/exercise/CountingStateTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountingStateTest {
    private fun tapAll(start: CountingState, count: Int): CountingState =
        (0 until count).fold(start) { state, index -> state.tap(index) }

    @Test
    fun nothingIsMirroredIntoTheAnswerFieldBeforeTheFirstTap() {
        // Sonst stünde bei Plus sofort eine 0 im Feld und bei Minus sofort der linke
        // Operand — das Kind könnte die Startzahl absenden, ohne etwas getan zu haben.
        assertNull(CountingState.forRound(MathOperation.Add, 7, 8).counted)
        assertNull(CountingState.forRound(MathOperation.Subtract, 15, 6).counted)
        assertNull(CountingState.forRound(MathOperation.Multiply, 4, 5).counted)
    }

    @Test
    fun plusCountsUpAcrossBothGroups() {
        val start = CountingState.forRound(MathOperation.Add, 7, 8)
        assertEquals(1, start.tap(0).counted)
        assertEquals(7, tapAll(start, 7).counted)
        val done = tapAll(start, 15)
        assertEquals(15, done.counted)
        assertTrue(done.complete)
    }

    @Test
    fun minusCountsDownFromTheStartingQuantity() {
        val start = CountingState.forRound(MathOperation.Subtract, 15, 6)
        assertEquals(14, start.tap(0).counted)
        assertEquals(9, tapAll(start, 6).counted)
    }

    @Test
    fun minusStopsAtTheTakeAwayTargetSoTheChildCannotOvershoot() {
        val start = CountingState.forRound(MathOperation.Subtract, 15, 6)
        val full = tapAll(start, 6)
        assertTrue(full.complete)
        // Der siebte Tipp auf ein noch stehendes Objekt ändert nichts.
        val overshoot = full.tap(6)
        assertEquals(9, overshoot.counted)
        assertEquals(full.tapped, overshoot.tapped)
    }

    @Test
    fun tappingAnAlreadyTappedObjectTakesTheTapBack() {
        val plus = CountingState.forRound(MathOperation.Add, 7, 8).tap(0).tap(1)
        assertEquals(2, plus.counted)
        assertEquals(1, plus.tap(1).counted)
        assertFalse(plus.tap(1).isTapped(1))

        // Auch am Deckel: sonst wäre eine Fehltipp-Serie bei Minus eine Sackgasse.
        val minus = (0 until 6).fold(CountingState.forRound(MathOperation.Subtract, 15, 6)) { s, i -> s.tap(i) }
        assertEquals(10, minus.tap(5).counted)
        assertFalse(minus.tap(5).complete)
    }

    @Test
    fun tapsOutsideTheFieldAreIgnored() {
        val start = CountingState.forRound(MathOperation.Add, 7, 8)
        assertEquals(start, start.tap(-1))
        assertEquals(start, start.tap(15))
    }

    @Test
    fun theCompletedFieldAlwaysHoldsTheArithmeticAnswer() {
        listOf(
            Triple(MathOperation.Add, 7, 8),
            Triple(MathOperation.Add, 15, 15),
            Triple(MathOperation.Subtract, 15, 6),
            Triple(MathOperation.Subtract, 30, 12),
            Triple(MathOperation.Multiply, 4, 5),
            Triple(MathOperation.Multiply, 5, 6),
        ).forEach { (operation, left, right) ->
            val start = CountingState.forRound(operation, left, right)
            val taps = if (operation == MathOperation.Subtract) right else start.objectCount
            val done = tapAll(start, taps)
            assertTrue("$operation $left/$right not complete", done.complete)
            assertEquals("$operation $left/$right", operation.answer(left, right), done.counted)
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests '*CountingStateTest*'
```

Erwartet: Kompilierfehler — `CountingState` existiert nicht.

- [ ] **Step 3: Implementieren**

Neue Datei `app/src/main/java/app/abcvorschule/ui/exercise/CountingState.kt`:

```kotlin
package app.abcvorschule.ui.exercise

/**
 * Tipp-Zustand der Zähl-Hilfe. Die Geste **ist** die Rechenart: Plus und
 * Malnehmen sammeln von null aufwärts ein, Minus nimmt von der Ausgangsmenge
 * abwärts weg. Genau darum führt das Kind die Rechnung aus, statt ein Ergebnis
 * abzuzählen, das die App schon hergestellt hat.
 *
 * Compose-frei; [CountingAid] rendert diesen Zustand nur.
 */
data class CountingState(
    val operation: MathOperation,
    /** Antippbare Objekte im Hauptfeld. */
    val objectCount: Int,
    /** Plätze der Weg-Zone; nur Minus hat welche, sonst 0. */
    val removeSlots: Int,
    val tapped: Set<Int> = emptySet(),
) {
    /**
     * Der Wert, der ins Antwortfeld gespiegelt wird — `null`, solange nichts
     * angetippt ist. Ohne dieses `null` stünde bei Plus sofort eine 0 im Feld und
     * bei Minus sofort der linke Operand, und das Kind könnte absenden, ohne
     * etwas getan zu haben.
     */
    val counted: Int?
        get() = when {
            tapped.isEmpty() -> null
            operation == MathOperation.Subtract -> objectCount - tapped.size
            else -> tapped.size
        }

    /** Alles eingesammelt bzw. die Weg-Zone voll. */
    val complete: Boolean
        get() = tapped.size == if (operation == MathOperation.Subtract) removeSlots else objectCount

    fun isTapped(index: Int): Boolean = index in tapped

    /**
     * Ein Tipp. Ein zweiter Tipp auf dasselbe Objekt nimmt ihn zurück — ein
     * Verzähler bleibt korrigierbar, und keine Fehltipp-Serie wird zur Sackgasse.
     * Am Deckel der Weg-Zone tut ein neuer Tipp nichts: die sechs Plätze sind die
     * ganze Information, die das Kind über „wie viele weg" braucht.
     */
    fun tap(index: Int): CountingState {
        if (index !in 0 until objectCount) return this
        if (isTapped(index)) return copy(tapped = tapped - index)
        if (complete) return this
        return copy(tapped = tapped + index)
    }

    companion object {
        fun forRound(operation: MathOperation, left: Int, right: Int): CountingState =
            CountingState(
                operation = operation,
                objectCount = CountingField.objectCount(operation, left, right),
                removeSlots = CountingField.removeSlots(operation, right),
            )
    }
}
```

- [ ] **Step 4: Test laufen lassen**

```bash
./gradlew :app:testDebugUnitTest --tests '*CountingStateTest*'
```

Erwartet: PASS. Achte besonders auf `theCompletedFieldAlwaysHoldsTheArithmeticAnswer` — dieser Test ist die eigentliche Zusicherung: wer korrekt zu Ende zählt, bekommt das richtige Ergebnis.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/CountingState.kt app/src/test/java/app/abcvorschule/ui/exercise/CountingStateTest.kt
git commit -m "feat(rechnen): Tipp-Zustand der Zähl-Hilfe

Plus und Malnehmen sammeln aufwärts ein, Minus nimmt abwärts weg — die Geste ist
die Rechenart. Der Deckel der Weg-Zone verhindert, dass das Kind zu viel
wegnimmt, ein zweiter Tipp nimmt einen Fehltipp zurück, und vor dem ersten Tipp
wird nichts ins Antwortfeld gespiegelt."
```

---

### Task 5: `NumberPad` spiegelt den gezählten Wert

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/NumberPad.kt:50-90`

**Interfaces:**
- Consumes: nichts Neues
- Produces: `NumberPad(..., countedValue: Int? = null, hideKeyboard: Boolean = false, ...)`

- [ ] **Step 1: Die zwei Parameter ergänzen**

In `NumberPad.kt` die Parameterliste um zwei Einträge erweitern (nach `enabled`):

```kotlin
    /** Von der Zähl-Hilfe hochgezählter Wert; `null` heißt „noch nichts angetippt"
     * und lässt das Feld in Ruhe. */
    countedValue: Int? = null,
    /** True, solange die Zähl-Hilfe offen ist: die System-Tastatur würde das
     * Zählfeld verdecken, also bleibt sie zu, bis das Kind das Feld antippt. */
    hideKeyboard: Boolean = false,
```

- [ ] **Step 2: Fokus/Tastatur nicht mehr erzwingen, solange die Hilfe offen ist**

Den bestehenden `LaunchedEffect(enabled)`-Block ersetzen:

```kotlin
    LaunchedEffect(enabled, hideKeyboard) {
        // Deferred rather than Unit-keyed: while locked the keyboard must not pop
        // up before the child is allowed to type (design doc). Und solange die
        // Zähl-Hilfe offen ist, verdeckt die Tastatur genau das Feld, auf dem das
        // Kind zählen soll — ein Tipp ins Eingabefeld holt sie zurück.
        if (!enabled) return@LaunchedEffect
        if (hideKeyboard) {
            keyboardController?.hide()
            return@LaunchedEffect
        }
        focusRequester.requestFocus()
        keyboardController?.show()
    }
```

- [ ] **Step 3: Den gezählten Wert ins Feld spiegeln**

Direkt hinter dem `LaunchedEffect(solved)`-Block einfügen:

```kotlin
    LaunchedEffect(countedValue, resetToken) {
        // Auch auf resetToken gekeyed: der Token wechselt bei jedem Fehlversuch und
        // leert das Feld. Ohne dieses Re-Spiegeln stünde das Feld nach einem Miss
        // leer da, während die Haken in der Zähl-Hilfe noch gesetzt sind.
        countedValue?.let { value = it.toString() }
    }
```

- [ ] **Step 4: Kompilieren**

```bash
./gradlew :app:assembleDebug
```

Erwartet: BUILD SUCCESSFUL. (Reine Compose-Änderung ohne eigene Fachlogik — die Zusicherungen dazu stehen im manuellen Smoke-Test der Spec.)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/NumberPad.kt
git commit -m "feat(rechnen): Antwortfeld spiegelt den gezählten Wert

Die Zähl-Hilfe schreibt ihren Zählerstand ins Feld, und die System-Tastatur
bleibt zu, solange sie offen ist — sonst verdeckt sie genau das Feld, auf dem
das Kind zählen soll."
```

---

### Task 6: Die Multiplikationsmatrix wird antippbar

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/VisualQuantityBoard.kt:227-290`

**Interfaces:**
- Consumes: `MultiplicationMatrix`, `CountingState` (Task 4)
- Produces: `MultiplicationMatrixGrid(emoji: String, rows: Int, columns: Int, modifier: Modifier = Modifier, counting: CountingState? = null, onTapCell: (Int) -> Unit = {})`

- [ ] **Step 1: Die zwei Parameter ergänzen**

In `MultiplicationMatrixGrid` die Signatur erweitern:

```kotlin
@Composable
fun MultiplicationMatrixGrid(
    emoji: String,
    rows: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    /** Gesetzt, sobald die Zähl-Hilfe offen ist: dann sind alle Zellen echt und
     * antippbar — auch die sonst geisterhaften Reihen. Genau der Schritt, den das
     * Kind vorher im Kopf nicht geschafft hat. */
    counting: CountingState? = null,
    onTapCell: (Int) -> Unit = {},
) {
```

- [ ] **Step 2: Die Zell-Darstellung umstellen**

Innerhalb der `repeat(rows) { row -> ... Row { ... } }`-Schleife die Zellen so rendern, dass die Zähl-Hilfe die Geisterreihen aufhebt. Die bestehende Zeilennummer-`Text`-Komposition bleibt unverändert; ersetze nur die Emoji-Zellen der Zeile durch:

```kotlin
                repeat(columns) { column ->
                    val index = row * columns + column
                    // Ohne Zähl-Hilfe bleibt es beim Bild aus §8: nur die erste Reihe
                    // ist echt, der Rest ist Platzhalter. Mit Zähl-Hilfe sind alle
                    // Zellen echt — sonst gäbe es in den Geisterreihen nichts zu zählen.
                    val ghost = counting == null && !MultiplicationMatrix.isConcreteRow(row)
                    val done = counting?.isTapped(index) == true
                    Text(
                        text = emoji,
                        fontSize = sizeSp.sp,
                        modifier = Modifier
                            .alpha(
                                when {
                                    ghost -> MultiplicationMatrix.GhostAlpha
                                    done -> CountedAlpha
                                    else -> 1f
                                },
                            )
                            .then(
                                if (counting == null) {
                                    Modifier
                                } else {
                                    Modifier.clickable { onTapCell(index) }
                                },
                            )
                            .testTag("counting_cell_$index"),
                    )
                }
```

Und auf Dateiebene in `VisualQuantityBoard.kt` die gemeinsame Konstante ergänzen (direkt unter den Imports):

```kotlin
/** Deckkraft eines bereits gezählten Objekts. Deutlich sichtbarer als ein
 * Geister-Platzhalter ([MultiplicationMatrix.GhostAlpha]) — „schon gezählt" darf
 * nicht wie „gar nicht da" aussehen. */
const val CountedAlpha = 0.45f
```

- [ ] **Step 3: Kompilieren**

```bash
./gradlew :app:assembleDebug
```

Erwartet: BUILD SUCCESSFUL. Bestehende Aufrufer übergeben `counting` nicht und sehen unverändert Geisterreihen.

- [ ] **Step 4: Bestehende Tests laufen lassen**

```bash
./gradlew :app:testDebugUnitTest
```

Erwartet: alles grün — insbesondere die Matrix-Tests, deren Verhalten ohne `counting` unverändert bleiben muss.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/VisualQuantityBoard.kt
git commit -m "feat(rechnen): Multiplikationsmatrix wird in der Zähl-Hilfe antippbar

Ohne Zähl-Hilfe bleibt das Bild aus §8 — nur die erste Reihe ist echt. Mit
Zähl-Hilfe werden die Geisterreihen echt und alle Zellen antippbar; in
Platzhaltern gäbe es sonst nichts zu zählen."
```

---

### Task 7: `CountingAid` — die Darstellung

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/CountingAid.kt`

**Interfaces:**
- Consumes: `CountingField` (Task 3), `CountingState` (Task 4), `MultiplicationMatrixGrid(..., counting, onTapCell)` (Task 6), `CountedAlpha` (Task 6), `QuantityCluster`-Umfeld (`ui/theme`: `WarmInk`, `WarmMuted`, `CreamElevated`), `MathOperation.symbol`
- Produces: `@Composable fun CountingAid(emoji: String, left: Int, right: Int, operation: MathOperation, state: CountingState, onTap: (Int) -> Unit, modifier: Modifier = Modifier)`

- [ ] **Step 1: Die Datei schreiben**

Neue Datei `app/src/main/java/app/abcvorschule/ui/exercise/CountingAid.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/**
 * Die Zähl-Hilfe: die Aufgabenmenge, antippbar. Tritt nach
 * [MathHinting.CountingAidFromMisses] Fehlversuchen **an die Stelle** der
 * Aufgabenvisualisierung — nicht als zusätzlicher Block darunter, sonst stünde
 * dieselbe Aufgabe zweimal auf dem Schirm (PRODUCT_PRINCIPLES §9) und auf einem
 * Telefon wäre für beides ohnehin kein Platz.
 *
 * Reine Darstellung von [state]; jede Regel darüber, was ein Tipp bewirkt, lebt
 * in [CountingState], jede Größenrechnung in [CountingField].
 */
@Composable
fun CountingAid(
    emoji: String,
    left: Int,
    right: Int,
    operation: MathOperation,
    state: CountingState,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sizeSp = CountingField.emojiSizeSp(operation, left, right)

    if (operation == MathOperation.Multiply) {
        MultiplicationMatrixGrid(
            emoji = emoji,
            rows = left,
            columns = right,
            modifier = modifier.testTag("counting_aid"),
            counting = state,
            onTapCell = onTap,
        )
        return
    }

    Column(
        modifier = modifier.testTag("counting_aid"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp * 2),
    ) {
        // Plus stapelt seine zwei Gruppen übereinander statt nebeneinander: fünf
        // Spalten je Gruppe wären nebeneinander zehn, und zehn Objekte quer passen
        // bei font_scale 1.3 auf kein Telefon.
        var offset = 0
        CountingField.groupSizes(operation, left, right).forEachIndexed { groupIndex, size ->
            if (groupIndex > 0) {
                Text(
                    text = operation.symbol,
                    style = MaterialTheme.typography.headlineMedium,
                    color = WarmInk,
                )
            }
            CountingGroup(
                emoji = emoji,
                size = size,
                indexOffset = offset,
                sizeSp = sizeSp,
                state = state,
                onTap = onTap,
            )
            offset += size
        }

        if (state.removeSlots > 0) {
            TakeAwayZone(
                emoji = emoji,
                slots = state.removeSlots,
                filled = state.tapped.size,
                sizeSp = sizeSp,
            )
        }

        Text(
            text = state.counted?.toString() ?: "",
            style = MaterialTheme.typography.displaySmall,
            color = WarmInk,
            modifier = Modifier.testTag("counting_total"),
        )
    }
}

/** Eine Objektgruppe in Fünferzeilen. [indexOffset] hält die Objektindizes über
 * beide Plus-Gruppen hinweg fortlaufend, damit der Zähler durchläuft. */
@Composable
private fun CountingGroup(
    emoji: String,
    size: Int,
    indexOffset: Int,
    sizeSp: Int,
    state: CountingState,
    onTap: (Int) -> Unit,
) {
    var index = indexOffset
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp),
    ) {
        CountingField.rows(size).forEach { rowSize ->
            Row(horizontalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp)) {
                repeat(rowSize) {
                    val objectIndex = index++
                    // Minus startet mit allen Objekten angehakt und nimmt weg; Plus und
                    // Malnehmen starten leer und sammeln ein. Deshalb heißt "angetippt"
                    // je nach Rechenart das Gegenteil.
                    val gone = state.operation == MathOperation.Subtract &&
                        state.isTapped(objectIndex)
                    val collected = state.operation != MathOperation.Subtract &&
                        state.isTapped(objectIndex)
                    Text(
                        text = emoji,
                        fontSize = sizeSp.sp,
                        modifier = Modifier
                            .alpha(if (gone || collected) CountedAlpha else 1f)
                            .clickable { onTap(objectIndex) }
                            .testTag("counting_object_$objectIndex"),
                    )
                }
            }
        }
    }
}

/**
 * Die Weg-Zone: genau so viele leere Plätze, wie weggenommen werden soll. Sie ist
 * der Grund, warum das Kind nicht mitzählen muss, wie viele es schon weggenommen
 * hat — volle Zone heißt fertig. Unter dem Hauptfeld statt daneben, damit alles
 * fünf Spalten breit bleibt.
 */
@Composable
private fun TakeAwayZone(emoji: String, slots: Int, filled: Int, sizeSp: Int) {
    val slotSize = (sizeSp * LocalDensity.current.fontScale).dp + 8.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp),
        modifier = Modifier.testTag("take_away_zone"),
    ) {
        var placed = 0
        CountingField.rows(slots).forEach { rowSize ->
            Row(horizontalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp)) {
                repeat(rowSize) {
                    val occupied = placed++ < filled
                    Box(
                        modifier = Modifier
                            .size(slotSize)
                            .background(CreamElevated, RoundedCornerShape(10.dp))
                            .border(2.dp, WarmMuted, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (occupied) Text(text = emoji, fontSize = sizeSp.sp)
                    }
                }
            }
        }
        Text(
            text = slots.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = WarmMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
```

- [ ] **Step 2: Kompilieren**

```bash
./gradlew :app:assembleDebug
```

Erwartet: BUILD SUCCESSFUL. Falls `displaySmall` im Theme nicht definiert ist, stattdessen `headlineLarge` verwenden — prüfe `app/src/main/java/app/abcvorschule/ui/theme/Theme.kt`.

- [ ] **Step 3: Tests laufen lassen**

```bash
./gradlew :app:testDebugUnitTest
```

Erwartet: alles grün (nichts Bestehendes berührt).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/CountingAid.kt
git commit -m "feat(rechnen): Darstellung der Zähl-Hilfe

Plus stapelt seine zwei Gruppen übereinander (zehn Objekte quer passen auf kein
Telefon), Minus bekommt eine Weg-Zone mit genau so vielen leeren Plätzen wie
wegzunehmen sind, Malnehmen erbt die Matrix. Reine Darstellung — die Regeln
liegen in CountingState und CountingField."
```

---

### Task 8: Verdrahtung in `MathExercise`

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt:135-149`

**Interfaces:**
- Consumes: alles aus Task 1–7, `TrainerCallbacks.onSpeakFeedback: (String) -> Unit` (`TrainerHost.kt:30`), `LocalAbcHaptics.current` mit `tick()`, `nudge()` (`ui/rewards/AbcHaptics.kt`)
- Produces: `MathExercise(..., onSpeakFeedback: (String) -> Unit, ...)`

- [ ] **Step 1: Die Cue-Texte als Konstanten anlegen**

In `MathHinting.kt` innerhalb von `object MathHinting` ergänzen:

```kotlin
    /**
     * Gesprochene Cues der Zähl-Hilfe. Feste Strings ohne Interpolation, sonst
     * findet die Clip-Suche nie einen kuratierten Clip (`ClipIndex.lookup`) und es
     * bliebe bei der TTS-Stimme. Gepflegt in `tools/tts/extra-strings.json`.
     */
    const val CountingAidCueCollect = "Zähl mit! Tippe jedes Bild an."
    const val CountingAidCueTakeAway = "Nimm sie weg! Tippe an, was weggeht."

    fun countingAidCue(operation: MathOperation): String =
        if (operation == MathOperation.Subtract) CountingAidCueTakeAway else CountingAidCueCollect
```

- [ ] **Step 2: Einen Test dafür schreiben und laufen lassen**

In `MathHintingTest.kt` ergänzen:

```kotlin
    @Test
    fun theCountingCueMatchesTheGestureTheOperationAsks() {
        assertEquals(MathHinting.CountingAidCueTakeAway, MathHinting.countingAidCue(MathOperation.Subtract))
        assertEquals(MathHinting.CountingAidCueCollect, MathHinting.countingAidCue(MathOperation.Add))
        assertEquals(MathHinting.CountingAidCueCollect, MathHinting.countingAidCue(MathOperation.Multiply))
    }
```

```bash
./gradlew :app:testDebugUnitTest --tests '*MathHintingTest*'
```

Erwartet: PASS.

- [ ] **Step 3: `MathExercise` umbauen**

Im `usePad`-Zweig von `MathExercise.kt` die Zähl-Hilfe verdrahten. Das sind vier gezielte Änderungen, keine Komplettersetzung der Funktion — der `else`-Zweig mit `VisualQuantityBoard` bleibt unangetastet.

**(3a)** Direkt hinter der bestehenden `var solved by remember(roundKey) { ... }`-Zeile den Zustand ergänzen (und die bestehende `val usePad = ...`-Zeile ersetzen):

```kotlin
    var solved by remember(roundKey) { mutableStateOf<Int?>(null) }
    val usePad = input == MathInputMode.Typed
    var counting by remember(roundKey) {
        mutableStateOf(CountingState.forRound(operation, round.left, round.right))
    }
    // Die Hilfe klappt bei der Schwelle auf und bleibt danach offen: sie wieder
    // zuzuziehen, während das Kind mittendrin zählt, wäre die schlechteste aller
    // Optionen.
    val countingOpen = usePad && misses >= MathHinting.CountingAidFromMisses
```

**(3b)** Den `answers`-Block des `usePad`-Zweigs ersetzen:

```kotlin
            answers = {
                NumberPad(
                    onSubmit = { handleGuess(it) },
                    resetToken = NumberPadInput.resetToken(roundKey, misses),
                    solved = solved != null,
                    enabled = !interactionLocked,
                    countedValue = counting.counted.takeIf { countingOpen },
                    hideKeyboard = countingOpen,
                )
                if (misses >= MathHinting.ResolveFromMissesTyped && !locked) {
                    AbcResolveButton(onClick = ::resolve)
                }
            },
```

**(3c)** Im `prompt`-Block des `usePad`-Zweigs die abschließende `Row { MathQuantityPrompt(...) }` ersetzen durch:

```kotlin
                if (countingOpen) {
                    CountingAid(
                        emoji = icon,
                        left = round.left,
                        right = round.right,
                        operation = operation,
                        state = counting,
                        onTap = { index ->
                            if (locked) return@CountingAid
                            val next = counting.tap(index)
                            if (next == counting) {
                                // Deckel der Weg-Zone erreicht: kein Fehler, keine
                                // Meldung, nur ein spürbares "das war's".
                                haptics.nudge()
                            } else {
                                haptics.tick()
                                counting = next
                                // Beim letzten Objekt einmal die Gesamtzahl — eine
                                // einzelne Äußerung, das ist der Payoff-Moment. Pro
                                // Tipp zu sprechen würgt sich gegenseitig ab
                                // (SpeechController.stopOutput vor jedem Enqueue).
                                if (next.complete) {
                                    next.counted?.let { onSpeakFeedback("$it.") }
                                }
                            }
                        },
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MathQuantityPrompt(icon, round.left, round.right, operation, emojiSizeSp = 40)
                    }
                }
```

**(3d)** Direkt nach der `countingOpen`-Deklaration aus (3a) den Cue anstoßen:

```kotlin
    LaunchedEffect(countingOpen) {
        // Kinder lesen nicht: dass sich der Aufgabenbereich gerade in etwas
        // Antippbares verwandelt hat, muss gesagt werden. Feedback-Kanal, damit
        // ein noch laufender Miss-Hinweis nicht abgewürgt wird.
        if (countingOpen) onSpeakFeedback(MathHinting.countingAidCue(operation))
    }
```

Neue Parameter in der Signatur von `MathExercise` (nach `onSpeakPrompt`):

```kotlin
    onSpeakFeedback: (String) -> Unit,
```

Neue Imports: `androidx.compose.runtime.LaunchedEffect`.

- [ ] **Step 4: Aufrufer nachziehen**

In `TrainerHost.kt` im `is CountAddRound ->`-Zweig ergänzen (nach `onSpeakPrompt = callbacks.onSpeakPrompt,`):

```kotlin
            onSpeakFeedback = callbacks.onSpeakFeedback,
```

- [ ] **Step 5: Bauen und testen**

```bash
./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug
```

Erwartet: beides grün.

- [ ] **Step 6: Manueller Smoke-Test**

```bash
./gradlew :app:installDebug
```

Gerät auf font_scale 1.3. Die sechs Punkte aus dem Abschnitt „Testplan" der Spec durchgehen. Befunde, die nicht sofort behoben werden, gehören nach `docs/residual-review-findings/`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt app/src/main/java/app/abcvorschule/ui/exercise/MathHinting.kt app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt app/src/test/java/app/abcvorschule/ui/exercise/MathHintingTest.kt
git commit -m "feat(rechnen): Zähl-Hilfe nach zwei Fehlversuchen

Die Eskalationsleiter im Tipp-Modus: zwei Fehlversuche öffnen die Zähl-Hilfe,
erst vier bringen den Auflösen-Knopf. Ein gesprochener Cue beim Aufklappen, pro
Tipp nur Haptik, und beim letzten Objekt einmal die Gesamtzahl."
```

---

### Task 9: Sprachclips und Dokumentation

**Files:**
- Modify: `tools/tts/extra-strings.json`
- Modify: `docs/PRODUCT_PRINCIPLES.md:296-307`
- Modify: `AGENTS.md` (Zeile mit „**Rechnen**: Icons (keine Wörter), 3 Optionen …")

**Interfaces:**
- Consumes: `MathHinting.CountingAidCueCollect`, `MathHinting.CountingAidCueTakeAway` (Task 8)
- Produces: nichts für Code

- [ ] **Step 1: Die zwei Cues in die TTS-Pipeline eintragen**

In `tools/tts/extra-strings.json` im Array `strings` ergänzen (Format exakt wie die bestehenden Einträge):

```json
    {
      "id": "countingAidCueCollect",
      "text": "Zähl mit! Tippe jedes Bild an.",
      "note": "MathHinting.CountingAidCueCollect — Zähl-Hilfe Plus/Malnehmen"
    },
    {
      "id": "countingAidCueTakeAway",
      "text": "Nimm sie weg! Tippe an, was weggeht.",
      "note": "MathHinting.CountingAidCueTakeAway — Zähl-Hilfe Minus"
    }
```

Die Texte müssen **zeichengleich** mit den Konstanten in `MathHinting.kt` sein, sonst findet `ClipIndex.lookup` nichts und es bleibt bei der TTS-Stimme.

- [ ] **Step 2: Prüfen, dass die Pipeline die neuen Strings sieht**

```bash
./start-tts-ui.sh
```

Im Web-Interface unter „fehlt" müssen die beiden neuen Einträge auftauchen. Rendern und exportieren ist **nicht** Teil dieses Plans — die App fällt bis dahin auf die TTS-Stimme zurück und bleibt nicht stumm (`speechAvailable`).

- [ ] **Step 3: PRODUCT_PRINCIPLES §8 nachziehen**

Im Abschnitt „## 8. Mathematik-Visuals" die Zeile zu „Rechnen ‚Ohne Hilfe' (Zahlen-Eingabe)" ersetzen durch:

```markdown
- **Eingabeart:** Zahlen-Eingabe bei fortgeschrittenem Scaffold **oder** sobald das
  Ergebnis über 10 liegt (Band `hard`/`expert`) — außer die Eltern haben
  ausdrücklich „Mit Hilfe" (`ParentMode.Beginner`) gewählt, dann bleiben überall
  die drei Kacheln. Die Regel prüft den Eltern-Modus, nicht das abgeleitete
  Scaffold: im Default `Auto` startet ein frisches Kind auf `Beginner`, gegen das
  Scaffold geprüft liefe sie beim Normalnutzer ins Leere. Grund: drei Kacheln mit
  Nachbar-Distraktoren machen Raten zur billigsten Strategie. Regel in
  `MathHinting.inputFor`.
- Das Antwortfeld nutzt die **System-Tastatur im Zahlenmodus** (kein Custom-Nummernblock)
  plus einen CTA-Absenden-Button mit Pfeil-Icon.
- **Zähl-Hilfe (nur Tipp-Modus):** nach 2 Fehlversuchen wird der Aufgabenbereich
  antippbar und **ersetzt** die Aufgabenvisualisierung (§9: Aufgabe nie zweimal).
  Die Geste ist die Rechenart — Plus sammelt aufwärts ein, Minus nimmt in eine
  Weg-Zone mit genau `right` leeren Plätzen weg (Zähler läuft rückwärts),
  Malnehmen füllt das Raster und macht dafür die Geisterreihen echt. Der Zähler
  wird ab dem ersten Tipp ins Antwortfeld gespiegelt; die System-Tastatur klappt
  ein, solange die Hilfe offen ist. **In der Zähl-Hilfe werden Mengen ab 11
  ausgeschrieben** (Fünferzeilen) — die einzige Ausnahme zur Symbol-ab-11-Regel
  oben, und sie gilt nie im Aufgaben-Prompt. Der „Auflösen"-Knopf erscheint im
  Tipp-Modus erst nach 4 Fehlversuchen (Kachel-Modus unverändert 2).
```

- [ ] **Step 4: AGENTS.md-Kurzfassung nachziehen**

Die Zeile

```markdown
- **Rechnen**: Icons (keine Wörter), 3 Optionen (visuell) oder System-Zahlentastatur; Erfolg vorgesprochen (kein sichtbarer Text), Miss gesprochenes Feedback. Ab 11 Mengen nur als Symbol + Ziffer; Progression Plus → Wegnehmen → gleiche Gruppen.
```

ersetzen durch:

```markdown
- **Rechnen**: Icons (keine Wörter). 3 Optionen (visuell) **oder** System-Zahlentastatur — getippt wird ab Ergebnis 11, außer im ausdrücklichen Eltern-Modus „Mit Hilfe" (`MathHinting.inputFor`). Im Tipp-Modus nach 2 Fehlversuchen die **Zähl-Hilfe**: die Menge wird antippbar, das Kind führt die Rechnung mit dem Finger aus, „Auflösen" erst nach 4. Erfolg vorgesprochen (kein sichtbarer Text), Miss gesprochenes Feedback. Ab 11 Mengen nur als Symbol + Ziffer — **außer** in der Zähl-Hilfe, die sie ausschreibt. Progression Plus → Wegnehmen → gleiche Gruppen.
```

- [ ] **Step 5: Commit**

```bash
git add tools/tts/extra-strings.json docs/PRODUCT_PRINCIPLES.md AGENTS.md
git commit -m "docs(rechnen): Eingabeart und Zähl-Hilfe in den verbindlichen Quellen

PRODUCT_PRINCIPLES §8 und die AGENTS-Kurzfassung sagten noch '3 Optionen oder
System-Zahlentastatur', ohne dass eine Regel dahinterstand. Dazu die zwei neuen
gesprochenen Cues in der TTS-Pipeline."
```

---

## Abschluss

Nach Task 9:

```bash
./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug
```

Dann `superpowers:requesting-code-review`, danach `superpowers:finishing-a-development-branch`.
