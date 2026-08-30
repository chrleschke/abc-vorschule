# Zehnerfeld für die Addition — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Additionsaufgaben im Tipp-Modus zeigen von Anfang an ein Zehnerfeld, in dem das Kind den zweiten Summanden Bild für Bild setzt, und beantworten es in getrennten Zehner-/Einerfeldern.

**Architecture:** Vier neue Dateien neben den bestehenden. Alle Regeln (Layout-Maße, Tipp-Zustand, Eingaberegeln) leben Compose-frei in `object`s bzw. `data class`es und sind JVM-testbar — dasselbe Muster wie `CountingField`/`CountingState`. Die Compose-Schicht (`TenFrameBoard`, `PlaceValueAnswer`) rendert nur. `MathExercise` verzweigt an genau einer Stelle: `usePad && operation == Add`. Minus, Malnehmen und der Kachel-Modus laufen unverändert über `CountingAid`/`NumberPad`/`VisualQuantityBoard`.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), JUnit4 (`:app:testDebugUnitTest`), Compose UI-Test (`:app:connectedDebugAndroidTest`).

## Global Constraints

- **Spec:** `docs/superpowers/specs/2026-08-30-zehnerfeld-addition-design.md` — sie ist die Wahrheit; dieser Plan setzt sie um.
- **Nur Addition im Tipp-Modus.** `MathOperation.Subtract` und `MathOperation.Multiply` sowie `MathInputMode.Tiles` behalten exakt ihr heutiges Verhalten. Keine Änderung an `CountingField`, `CountingState`, `CountingAid`, `NumberPad`, `NumberPadInput`, `VisualQuantityBoard`.
- **Zahlenraum:** Operanden und Ergebnis ≤ 30 (`MaxMathQuantity`, validator-geprüft). Jede Größenrechnung muss für jede Summe 0…30 innerhalb ihrer Schranken bleiben.
- **Schriftskalierung:** Ausgelegt wird gegen `font_scale 1.3` (Testgerät), Konstante `TenFrame.LayoutFontScale = 1.3f`. Nichts darf bei 1.3 überlaufen.
- **Compose-frei bleibt Compose-frei:** `TenFrame`, `TenFrameState`, `PlaceValueInput` importieren nichts aus `androidx.compose.*`.
- **Kommentarstil:** Deutsch, erklärt das *Warum*, nicht das *Was* — wie in den Nachbardateien. Kein Kommentar, der nur den Code nacherzählt.
- **Farben** kommen aus `app.abcvorschule.ui.theme`: `Cream`, `CreamElevated`, `LeafGreen`, `SkyBlue`, `SunCoral`, `WarmInk`, `WarmMuted`. Keine Literale.
- **Grün heißt „richtig", Rot gibt es nicht** (§8).
- **Tests laufen mit:** `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest` — der Worktree hat keine `local.properties`, ohne das Präfix findet Gradle das SDK nicht.
- **Commit-Sprache:** Deutsch, Präsens, wie `git log` es zeigt. Jeder Commit endet mit:
  ```
  Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
  ```

---

### Task 1: `TenFrame` — die Layout-Regeln

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/TenFrame.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/TenFrameTest.kt`

**Interfaces:**
- Consumes: nichts.
- Produces:
  - `TenFrame.RowSize: Int` (= 10), `TenFrame.FiveGroup: Int` (= 5)
  - `TenFrame.CellGapDp: Float`, `TenFrame.FiveGapDp: Float`, `TenFrame.CellPadDp: Float`, `TenFrame.RowGapDp: Float`
  - `TenFrame.LayoutFontScale: Float`, `TenFrame.MinEmojiSp: Int`, `TenFrame.MaxEmojiSp: Int`, `TenFrame.MinHitTargetDp: Float`, `TenFrame.FallbackFieldWidthDp: Float`
  - `TenFrame.rows(count: Int): List<Int>`
  - `TenFrame.emojiSizeSp(fieldWidthDp: Float): Int`
  - `TenFrame.cellSizeDp(emojiSizeSp: Int): Float`
  - `TenFrame.hitTargetDp(emojiSizeSp: Int): Float`
  - `TenFrame.fullRowCount(realCount: Int): Int`
  - `TenFrame.isRowFull(rowIndex: Int, realCount: Int): Boolean`
  - `TenFrame.hasFiveGapAfter(columnIndex: Int): Boolean`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/app/abcvorschule/ui/exercise/TenFrameTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TenFrameTest {
    @Test
    fun quantitiesBreakIntoRowsOfTen() {
        assertEquals(emptyList<Int>(), TenFrame.rows(0))
        assertEquals(listOf(4), TenFrame.rows(4))
        assertEquals(listOf(10), TenFrame.rows(10))
        assertEquals(listOf(10, 6), TenFrame.rows(16))
        assertEquals(listOf(10, 10, 4), TenFrame.rows(24))
        assertEquals(listOf(10, 10, 10), TenFrame.rows(30))
    }

    @Test
    fun everyQuantityUpToThirtyKeepsItsTotalAcrossTheRows() {
        (0..30).forEach { count ->
            assertEquals("count $count", count, TenFrame.rows(count).sum())
        }
    }

    @Test
    fun noQuantityEverNeedsMoreThanThreeRows() {
        // Der ganze Grund für Zehnerzeilen: 30 Objekte sind drei Zeilen statt sechs,
        // und die Höhe ist damit nie die enge Schranke.
        (0..30).forEach { count ->
            assertTrue("count $count", TenFrame.rows(count).size <= 3)
        }
    }

    @Test
    fun theFiveGapSitsAfterTheFifthObjectOnly() {
        assertTrue(TenFrame.hasFiveGapAfter(4))
        assertFalse(TenFrame.hasFiveGapAfter(0))
        assertFalse(TenFrame.hasFiveGapAfter(5))
        assertFalse(TenFrame.hasFiveGapAfter(9))
    }

    @Test
    fun emojiStaysWithinItsBoundsOnEveryPlausibleWidth() {
        // 320dp ist das schmale Telefon in Hochkant, 420dp der Deckel der Bühne.
        (280..420 step 4).forEach { width ->
            val size = TenFrame.emojiSizeSp(width.toFloat())
            assertTrue("width $width -> $size", size >= TenFrame.MinEmojiSp)
            assertTrue("width $width -> $size", size <= TenFrame.MaxEmojiSp)
        }
    }

    @Test
    fun tenObjectsAndTheirGapsFitTheMeasuredWidth() {
        // Die Zeile darf nicht über den gemessenen Platz hinauslaufen — sonst
        // schneidet Compose rechts ab, und das zehnte Objekt fehlt genau dem Kind,
        // das den Zehner sehen soll.
        (320..420 step 4).forEach { width ->
            val size = TenFrame.emojiSizeSp(width.toFloat())
            val rowWidth = TenFrame.RowSize * TenFrame.cellSizeDp(size) +
                TenFrame.CellGapDp * (TenFrame.RowSize - 2) + TenFrame.FiveGapDp
            assertTrue("width $width -> $rowWidth", rowWidth <= width)
        }
    }

    @Test
    fun theActiveCellIsAlwaysBigEnoughForAChildsFinger() {
        // Zehn Objekte nebeneinander sind kleiner als ein Kinderfinger. Weil immer
        // nur eine Zelle antippbar ist, darf ihre Trefferfläche über die Nachbarn
        // ragen.
        (TenFrame.MinEmojiSp..TenFrame.MaxEmojiSp).forEach { size ->
            assertTrue("size $size", TenFrame.hitTargetDp(size) >= TenFrame.MinHitTargetDp)
            assertTrue("size $size", TenFrame.hitTargetDp(size) >= TenFrame.cellSizeDp(size))
        }
    }

    @Test
    fun onlyWholeRowsCountAsTens() {
        assertEquals(0, TenFrame.fullRowCount(9))
        assertEquals(1, TenFrame.fullRowCount(10))
        assertEquals(1, TenFrame.fullRowCount(16))
        assertEquals(2, TenFrame.fullRowCount(24))
        assertEquals(3, TenFrame.fullRowCount(30))
        assertTrue(TenFrame.isRowFull(0, 16))
        assertFalse(TenFrame.isRowFull(1, 16))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*TenFrameTest'`
Expected: FAIL — `Unresolved reference: TenFrame`

- [ ] **Step 3: Write the implementation**

`app/src/main/java/app/abcvorschule/ui/exercise/TenFrame.kt`:

```kotlin
package app.abcvorschule.ui.exercise

/**
 * Layout-Regeln des Zehnerfeldes — der stehenden Darstellung einer
 * **Additionsaufgabe** im Tipp-Modus (design doc 2026-08-30-zehnerfeld-addition).
 *
 * Drei Dinge unterscheiden es von [CountingField], das Minus und Malnehmen
 * weiterträgt:
 *
 * 1. Zehn statt fünf Objekte pro Zeile. Der Zehner ist die Einheit, an der der
 *    Zehnerübergang hängt; Fünferzeilen zeigen ihn nie.
 * 2. Es steht **von Anfang an** da, nicht erst nach zwei Fehlversuchen — wer
 *    keine Strategie hat, soll nicht erst zweimal raten dürfen.
 * 3. Die Größe kommt aus der **gemessenen** Breite statt aus einer konservativen
 *    Konstanten. Bei zehn Objekten pro Zeile ist Breite die einzige enge
 *    Schranke (30 Objekte sind nur drei Zeilen), und geschätzte 320dp
 *    verschenkten auf einem normalen Telefon spürbar Bildgröße.
 *
 * Compose-frei, damit die Rechnungen als JVM-Test prüfbar bleiben.
 */
object TenFrame {
    /** Objekte pro Zeile. */
    const val RowSize = 10

    /** Nach so vielen Objekten sitzt die breitere Lücke — die „Kraft der Fünf". */
    const val FiveGroup = 5

    /** Abstand zwischen zwei Objekten derselben Fünfergruppe, in dp. */
    const val CellGapDp = 2f

    /** Abstand zwischen den beiden Fünfergruppen, in dp. Deutlich größer als
     * [CellGapDp], sonst ist die Gruppierung nicht zu sehen — und genau sie ist
     * das, was ein Kind ohne Abzählen erfassen soll. */
    const val FiveGapDp = 8f

    /** Innenabstand einer Zelle, in dp. */
    const val CellPadDp = 1f

    /** Abstand zwischen zwei Zeilen, in dp. */
    const val RowGapDp = 4f

    /** Schriftskalierung, gegen die ausgelegt wird — wie
     * [CountingField.LayoutFontScale]: das Testgerät steht auf 1.3. */
    const val LayoutFontScale = 1.3f

    /** Volle Größe, wenn Platz ist. */
    const val MaxEmojiSp = 30

    /**
     * Untergrenze. Niedriger als [CountingField.MinEmojiSp], und das ist der
     * bewusste Tausch: zehn Objekte nebeneinander sind zwangsläufig kleiner als
     * fünf. Erkennbar bleiben sie, weil ein Emoji eine Silhouette ist und keine
     * Schrift — und **treffbar** bleiben sie unabhängig davon, weil die
     * Trefferfläche der aktiven Zelle über die Nachbarn ragen darf
     * ([hitTargetDp]).
     */
    const val MinEmojiSp = 18

    /**
     * Kleinste Trefferfläche der aktiven Zelle, in dp — die Hälfte von
     * `AbcDimens.kidTouch` (80dp). Dokumentierte Kopie statt Import, damit diese
     * Datei Compose-frei bleibt (Muster: [MultiplicationMatrix.RowLabelSp]).
     */
    const val MinHitTargetDp = 40f

    /** Breite, wenn keine gemessen vorliegt — dieselbe konservative Schätzung
     * wie [CountingField.FieldWidthDp]. */
    const val FallbackFieldWidthDp = 320f

    /** Zehnerzeilen einer Menge; die letzte Zeile ist kürzer. */
    fun rows(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val full = count / RowSize
        val rest = count % RowSize
        return buildList {
            repeat(full) { add(RowSize) }
            if (rest > 0) add(rest)
        }
    }

    /** Sitzt hinter dieser Spalte die Fünfer-Lücke? */
    fun hasFiveGapAfter(columnIndex: Int): Boolean = columnIndex == FiveGroup - 1

    /** Summe aller Lücken einer vollen Zeile, in dp. */
    private const val RowGapsDp = CellGapDp * (RowSize - 2) + FiveGapDp

    /** Kantenlänge einer Zelle in dp bei gegebener Emoji-Größe. */
    fun cellSizeDp(emojiSizeSp: Int): Float = emojiSizeSp * LayoutFontScale + 2 * CellPadDp

    /**
     * Emoji-Größe in sp aus der verfügbaren Breite. Hergeleitet, nicht gestuft —
     * dieselbe Begründung wie bei [CountingField.emojiSizeSp]: eine Stufentabelle
     * deckt den echten Content nicht ab, eine Herleitung gilt per Konstruktion.
     */
    fun emojiSizeSp(fieldWidthDp: Float): Int {
        val byWidth = ((fieldWidthDp - RowGapsDp) / RowSize - 2 * CellPadDp) / LayoutFontScale
        return byWidth.toInt().coerceIn(MinEmojiSp, MaxEmojiSp)
    }

    /**
     * Trefferfläche der **aktiven** Zelle. Sie ragt bewusst über ihre Nachbarn
     * hinaus: antippbar ist zu jedem Zeitpunkt genau eine Zelle, also kann die
     * Überlappung niemanden treffen — und ein Vorschulkind trifft keine 25dp.
     */
    fun hitTargetDp(emojiSizeSp: Int): Float = maxOf(MinHitTargetDp, cellSizeDp(emojiSizeSp))

    /** Wie viele **volle** Zeilen die echten Objekte ergeben — die Zehner. */
    fun fullRowCount(realCount: Int): Int = realCount / RowSize

    /** Ist diese Zeile voll besetzt? Nur volle Zeilen sind Zehner. */
    fun isRowFull(rowIndex: Int, realCount: Int): Boolean = rowIndex < fullRowCount(realCount)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*TenFrameTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/TenFrame.kt app/src/test/java/app/abcvorschule/ui/exercise/TenFrameTest.kt
git commit -m "$(cat <<'EOF'
feat(rechnen): Layout-Regeln des Zehnerfeldes

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: `TenFrameState` — der Tipp-Zustand

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/TenFrameState.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/TenFrameStateTest.kt`

**Interfaces:**
- Consumes: `TenFrame.RowSize`, `TenFrame.fullRowCount`.
- Produces:
  - `TenFrameState(given: Int, added: Int, filled: Int = 0)`
  - `TenFrameState.forRound(left: Int, right: Int): TenFrameState` (companion)
  - `.total: Int`, `.realCount: Int`, `.nextIndex: Int?`, `.complete: Boolean`, `.fullTens: Int`
  - `.isReal(index: Int): Boolean`, `.isTappable(index: Int): Boolean`
  - `.tap(index: Int): TenFrameState`
  - `.completedTenAfter(next: TenFrameState): Int?`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/app/abcvorschule/ui/exercise/TenFrameStateTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TenFrameStateTest {
    /** Tippt [count] mal auf das, was der Puls gerade vorschlägt. */
    private fun tapAlong(start: TenFrameState, count: Int): TenFrameState =
        (0 until count).fold(start) { state, _ ->
            state.nextIndex?.let(state::tap) ?: state
        }

    @Test
    fun theFirstSummandIsRealAndTheSecondIsAPlaceholder() {
        val start = TenFrameState.forRound(16, 8)
        assertEquals(24, start.total)
        assertEquals(16, start.realCount)
        assertTrue(start.isReal(15))
        assertFalse(start.isReal(16))
        assertEquals(16, start.nextIndex)
    }

    @Test
    fun onlyTheNextPlaceholderIsTappable() {
        // Damit kann sich das Kind nicht verzählen — und es gibt keinen Fehltipp,
        // der einen Rückweg bräuchte.
        val start = TenFrameState.forRound(16, 8)
        assertFalse(start.isTappable(0))
        assertFalse(start.isTappable(15))
        assertTrue(start.isTappable(16))
        assertFalse(start.isTappable(17))
    }

    @Test
    fun tappingAnythingElseDoesNothing() {
        val start = TenFrameState.forRound(16, 8)
        assertEquals(start, start.tap(0))
        assertEquals(start, start.tap(17))
        assertEquals(start, start.tap(99))
    }

    @Test
    fun aSecondTapOnAFilledObjectDoesNotTakeItBack() {
        // Kein Widerruf: das Bild wächst nur in eine Richtung, wie die Rechnung.
        val one = TenFrameState.forRound(16, 8).tap(16)
        assertEquals(17, one.realCount)
        assertEquals(one, one.tap(16))
    }

    @Test
    fun tappingEveryPlaceholderCompletesTheField() {
        val start = TenFrameState.forRound(16, 8)
        assertFalse(start.complete)
        val done = tapAlong(start, 8)
        assertEquals(24, done.realCount)
        assertTrue(done.complete)
        assertNull(done.nextIndex)
        assertEquals(done, done.tap(23))
    }

    @Test
    fun onlyWholeRowsCountAsTens() {
        val start = TenFrameState.forRound(16, 8)
        assertEquals(10, start.fullTens)
        assertEquals(20, tapAlong(start, 4).fullTens)
        assertEquals(20, tapAlong(start, 8).fullTens)
    }

    @Test
    fun onlyTheTapThatFillsARowReportsATen() {
        // Nur dieser Tipp wird gesprochen — alles andere sieht das Kind.
        // 16 + 8: der erste Tipp macht 17, der vierte macht 20 voll.
        val start = TenFrameState.forRound(16, 8)
        (0 until 8).forEach { done ->
            val before = tapAlong(start, done)
            val after = before.tap(before.nextIndex!!)
            val reported = before.completedTenAfter(after)
            if (done == 3) {
                assertEquals("Tipp ${done + 1} füllt den Zehner", 20, reported)
            } else {
                assertNull("Tipp ${done + 1} darf schweigen", reported)
            }
        }
    }

    @Test
    fun everyRoundReportsExactlyAsManyTensAsItActuallyFills() {
        (1..30).forEach { left ->
            (1..30 - left).forEach { right ->
                var state = TenFrameState.forRound(left, right)
                var announced = 0
                repeat(right) {
                    val next = state.tap(state.nextIndex!!)
                    if (state.completedTenAfter(next) != null) announced++
                    state = next
                }
                val expected = TenFrame.fullRowCount(left + right) - TenFrame.fullRowCount(left)
                assertEquals("$left + $right", expected, announced)
            }
        }
    }
}
```

Hinweis für den Implementierenden: der Test `theTapThatFillsARowReportsTheTenItReached` prüft `16 + 8` — nach drei Tipps steht 19, der vierte Tipp macht 20 voll.

- [ ] **Step 2: Run test to verify it fails**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*TenFrameStateTest'`
Expected: FAIL — `Unresolved reference: TenFrameState`

- [ ] **Step 3: Write the implementation**

`app/src/main/java/app/abcvorschule/ui/exercise/TenFrameState.kt`:

```kotlin
package app.abcvorschule.ui.exercise

/**
 * Tipp-Zustand des Zehnerfeldes. Die erste Zahl liegt als echte Bilder da, die
 * zweite als Platzhalter — und das Kind macht sie einen nach dem anderen echt.
 * Die Geste **ist** die Addition: dazulegen, bis die Reihe voll ist.
 *
 * Zwei Regeln, die [CountingState] bewusst anders hat:
 *
 * - **Antippbar ist nur [nextIndex].** So kann sich das Kind nicht verzählen und
 *   die Reihenfolge ist die des Zählens.
 * - **Kein Widerruf.** Ein zweiter Tipp nimmt nichts zurück. Das ist nur
 *   zusammen mit der Regel darüber vertretbar: wo kein Fehltipp möglich ist,
 *   braucht es keinen Rückweg. Widerruf zu sperren, während man überall
 *   hintippen kann, wäre eine Falle.
 *
 * Compose-frei; [TenFrameBoard] rendert diesen Zustand nur.
 */
data class TenFrameState(
    /** Erster Summand — steht von Anfang an als echte Bilder da. */
    val given: Int,
    /** Zweiter Summand — liegt als Platzhalter bereit. */
    val added: Int,
    /** Wie viele Platzhalter das Kind schon gesetzt hat. */
    val filled: Int = 0,
) {
    /** Alle Plätze im Feld, echte wie leere. */
    val total: Int get() = given + added

    /** Objekte, die gerade als echt gezeichnet werden. */
    val realCount: Int get() = given + filled

    /** Der nächste offene Platzhalter — er pulsiert und ist der einzige, der auf
     * einen Tipp reagiert. `null`, wenn das Feld voll ist. */
    val nextIndex: Int? get() = if (filled < added) given + filled else null

    val complete: Boolean get() = filled == added

    /** Der Stand in vollen Zehnern — nur ganze Zeilen zählen. Genau diese Zahl
     * steht als Marke am Rahmen und wird beim Einrasten gesprochen. */
    val fullTens: Int get() = TenFrame.fullRowCount(realCount) * TenFrame.RowSize

    fun isReal(index: Int): Boolean = index in 0 until realCount

    fun isTappable(index: Int): Boolean = nextIndex != null && index == nextIndex

    /** Ein Tipp. Alles außer [nextIndex] tut nichts — auch ein zweiter Tipp auf
     * ein schon gesetztes Bild. */
    fun tap(index: Int): TenFrameState =
        if (isTappable(index)) copy(filled = filled + 1) else this

    /**
     * Der Zehner, den der Übergang zu [next] voll gemacht hat — sonst `null`.
     * Der einzige Moment, in dem die Stimme etwas sagt.
     */
    fun completedTenAfter(next: TenFrameState): Int? =
        if (next.fullTens > fullTens) next.fullTens else null

    companion object {
        fun forRound(left: Int, right: Int): TenFrameState =
            TenFrameState(given = left, added = right)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*TenFrameStateTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/TenFrameState.kt app/src/test/java/app/abcvorschule/ui/exercise/TenFrameStateTest.kt
git commit -m "$(cat <<'EOF'
feat(rechnen): Tipp-Zustand des Zehnerfeldes

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: `PlaceValueInput` — die Eingaberegeln

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/PlaceValueInput.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/PlaceValueInputTest.kt`

**Interfaces:**
- Consumes: nichts.
- Produces:
  - `PlaceValueInput.TensFrom: Int` (= 10)
  - `PlaceValueInput.fieldCount(answer: Int): Int`
  - `PlaceValueInput.lastDigit(raw: String): String`
  - `PlaceValueInput.combine(tens: String, ones: String): Int?`
  - `PlaceValueInput.isComplete(tens: String, ones: String, fieldCount: Int): Boolean`
  - `PlaceValueInput.digitsOf(value: Int, fieldCount: Int): Pair<String, String>`
  - `PlaceValueInput.MinSlotWidthDp: Float`, `PlaceValueInput.slotWidthDp(textSp: Float, fontScale: Float): Float`
  - `PlaceValueInput.resetToken(roundKey: String, misses: Int): String`

- [ ] **Step 1: Write the failing test**

`app/src/test/java/app/abcvorschule/ui/exercise/PlaceValueInputTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceValueInputTest {
    @Test
    fun twoFieldsOnceTheAnswerHasATensDigit() {
        // Verrät nichts: wie viele Objekte es sind, steht ohnehin im Bild — die
        // Felderzahl folgt nur dem, was das Kind schon sieht.
        assertEquals(1, PlaceValueInput.fieldCount(5))
        assertEquals(1, PlaceValueInput.fieldCount(9))
        assertEquals(2, PlaceValueInput.fieldCount(10))
        assertEquals(2, PlaceValueInput.fieldCount(24))
        assertEquals(2, PlaceValueInput.fieldCount(30))
    }

    @Test
    fun aFieldHoldsExactlyOneDigitAndTheNewestWins() {
        // Tippt das Kind in ein gefülltes Feld, überschreibt die neue Ziffer die
        // alte, statt sich anzuhängen — Löschen wäre ein zweiter Handgriff.
        assertEquals("", PlaceValueInput.lastDigit(""))
        assertEquals("4", PlaceValueInput.lastDigit("4"))
        assertEquals("5", PlaceValueInput.lastDigit("25"))
        assertEquals("7", PlaceValueInput.lastDigit("2a7"))
        assertEquals("", PlaceValueInput.lastDigit("abc"))
    }

    @Test
    fun bothFieldsTogetherAreTheAnswer() {
        assertEquals(24, PlaceValueInput.combine("2", "4"))
        assertEquals(30, PlaceValueInput.combine("3", "0"))
        assertEquals(7, PlaceValueInput.combine("", "7"))
        assertNull(PlaceValueInput.combine("2", ""))
        assertNull(PlaceValueInput.combine("", ""))
    }

    @Test
    fun sendingWaitsUntilEveryFieldIsFilled() {
        assertFalse(PlaceValueInput.isComplete("", "", 2))
        assertFalse(PlaceValueInput.isComplete("2", "", 2))
        assertFalse(PlaceValueInput.isComplete("", "4", 2))
        assertTrue(PlaceValueInput.isComplete("2", "4", 2))
        assertTrue(PlaceValueInput.isComplete("", "7", 1))
        assertFalse(PlaceValueInput.isComplete("", "", 1))
    }

    @Test
    fun aResolvedAnswerSplitsBackIntoItsFields() {
        assertEquals("2" to "4", PlaceValueInput.digitsOf(24, 2))
        assertEquals("3" to "0", PlaceValueInput.digitsOf(30, 2))
        assertEquals("" to "7", PlaceValueInput.digitsOf(7, 1))
    }

    @Test
    fun theSlotGrowsWithTheSystemFontSoADigitNeverClips() {
        val small = PlaceValueInput.slotWidthDp(textSp = 40f, fontScale = 1f)
        val large = PlaceValueInput.slotWidthDp(textSp = 40f, fontScale = 2f)
        assertTrue("$small / $large", large > small)
        assertTrue(small >= PlaceValueInput.MinSlotWidthDp)
    }

    @Test
    fun everyWrongTryClearsTheFields() {
        val round = "t7#0-add-16-8"
        assertEquals(PlaceValueInput.resetToken(round, 0), PlaceValueInput.resetToken(round, 0))
        assertTrue(PlaceValueInput.resetToken(round, 0) != PlaceValueInput.resetToken(round, 1))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*PlaceValueInputTest'`
Expected: FAIL — `Unresolved reference: PlaceValueInput`

- [ ] **Step 3: Write the implementation**

`app/src/main/java/app/abcvorschule/ui/exercise/PlaceValueInput.kt`:

```kotlin
package app.abcvorschule.ui.exercise

/**
 * Eingaberegeln der Stellenwert-Antwort: ein Feld für die Zehner, eines für die
 * Einer. Sie beantworten die Frage, an der ein Kind bei „20" hängenbleibt —
 * *welche Ziffer kommt zuerst?* — indem die Felder in denselben Spalten stehen
 * wie die Ziffern der Aufgabe darüber.
 *
 * Compose-frei, damit die Regeln als JVM-Test prüfbar bleiben — wie
 * [NumberPadInput], das weiterhin das einzelne Feld von Minus und Malnehmen
 * trägt.
 */
object PlaceValueInput {
    /** Ab diesem Ergebnis gibt es ein Zehnerfeld. */
    const val TensFrom = 10

    /** Die bisherige Feldbreite als Boden — dieselbe Rolle wie
     * [NumberPadInput.BaseFieldWidthDp], nur für eine einzelne Ziffer. */
    const val MinSlotWidthDp = 64f

    /** Vorschub einer Ziffer als Anteil der Schriftgröße —
     * [NumberPadInput.DigitAspect], damit beide Felder gleich rechnen. */
    const val DigitAspect = 0.6f

    /** Innenabstand eines Ziffernkastens, in dp. */
    const val SlotPaddingDp = 24f

    /**
     * Wie viele Ziffernfelder die Antwort bekommt. Das verrät die Größenordnung
     * der Antwort — und darf es: die Objekte stehen alle im Bild, das Kind sieht
     * ohnehin, dass es mehr als neun sind. Die Felderzahl folgt nur dem Bild.
     */
    fun fieldCount(answer: Int): Int = if (answer >= TensFrom) 2 else 1

    /**
     * Ein Feld hält genau eine Ziffer, und die zuletzt getippte gewinnt. Damit
     * überschreibt ein Tipp ins gefüllte Feld den alten Wert, statt sich
     * anzuhängen — ein Vorschulkind soll nicht erst löschen müssen.
     */
    fun lastDigit(raw: String): String = raw.filter(Char::isDigit).takeLast(1)

    /** Beide Felder zusammen als Zahl; `null`, solange etwas fehlt. */
    fun combine(tens: String, ones: String): Int? {
        if (ones.isEmpty()) return null
        return "${tens}$ones".toIntOrNull()
    }

    fun isComplete(tens: String, ones: String, fieldCount: Int): Boolean =
        ones.isNotEmpty() && (fieldCount == 1 || tens.isNotEmpty())

    /** Eine fertige Zahl zurück in ihre Felder — für das Auflösen. */
    fun digitsOf(value: Int, fieldCount: Int): Pair<String, String> =
        if (fieldCount == 1) "" to value.toString() else (value / 10).toString() to (value % 10).toString()

    /**
     * Breite eines Ziffernkastens aus der *effektiven* Textgröße. Gleiche
     * Begründung wie [NumberPadInput.fieldWidthDp]: ein Kind, das seine getippte
     * Zahl nicht sieht, kann sie nicht prüfen.
     */
    fun slotWidthDp(textSp: Float, fontScale: Float): Float =
        (textSp * fontScale * DigitAspect + SlotPaddingDp).coerceAtLeast(MinSlotWidthDp)

    /** Wechselt bei neuer Runde und bei jedem Fehlversuch — und leert damit die
     * Felder, genau wie [NumberPadInput.resetToken] es für Minus tut. */
    fun resetToken(roundKey: String, misses: Int): String = "$roundKey#$misses"
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*PlaceValueInputTest'`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/PlaceValueInput.kt app/src/test/java/app/abcvorschule/ui/exercise/PlaceValueInputTest.kt
git commit -m "$(cat <<'EOF'
feat(rechnen): Eingaberegeln der Stellenwert-Antwort

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: `TenFrameBoard` — das Bild

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/TenFrameBoard.kt`

**Interfaces:**
- Consumes: `TenFrame.*` (Task 1), `TenFrameState` (Task 2), `CountedAlpha` aus `VisualQuantityBoard.kt`, `MultiplicationMatrix.GhostAlpha`.
- Produces:
  ```kotlin
  @Composable
  fun TenFrameBoard(
      emoji: String,
      state: TenFrameState,
      onTap: (Int) -> Unit,
      modifier: Modifier = Modifier,
  )
  ```
  Test-Tags, auf die Task 7 sich stützt: `ten_frame`, `ten_frame_cell_$index`, `ten_frame_total`.

Kein eigener Unit-Test — es ist reine Darstellung, die Regeln liegen in Task 1/2. Der Bounds-Test kommt in Task 7.

- [ ] **Step 1: Write the implementation**

`app/src/main/java/app/abcvorschule/ui/exercise/TenFrameBoard.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.WarmInk

/** Deckkraft, auf die der Puls-Hinweis herunterblendet — wie in [CountingAid]. */
private const val TenFramePulseLowAlpha = 0.35f
private const val TenFramePulseMillis = 700

/**
 * Das Zehnerfeld: die Additionsaufgabe als Bild, von der ersten Sekunde an
 * antippbar (design doc 2026-08-30-zehnerfeld-addition).
 *
 * Der erste Summand steht als echte Bilder da, der zweite als Platzhalter im
 * selben Feld — die Menge läuft also **weiter**, statt in einem zweiten Block
 * daneben zu beginnen. Genau daran wird der Zehnerübergang sichtbar: bei „16 + 8"
 * hat die zweite Zeile noch vier Plätze frei, und das ist die Zerlegung
 * 8 = 4 + 4, bevor ein Wort darüber gesagt ist.
 *
 * Volle Zeilen liegen in **einem** Rahmen — sie sind die Zehner, und ein Rahmen
 * je Zeile machte aus zwei Zehnern zwei Dinge statt eines Stapels. Der laufende
 * Stand steht als Marke an der unteren rechten Ecke dieses Rahmens, nicht in
 * einer Rinne daneben: eine zweistellige Rinne kostete gut 40dp Breite, und bei
 * zehn Objekten pro Zeile ist Breite das, wovon am wenigsten da ist.
 *
 * Reine Darstellung von [state]; jede Regel darüber, was ein Tipp bewirkt, lebt
 * in [TenFrameState], jede Größenrechnung in [TenFrame].
 */
@Composable
fun TenFrameBoard(
    emoji: String,
    state: TenFrameState,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Bewusst ohne `by`: der Wert wird durchgereicht und erst in der Zeichenphase
    // gelesen (§10, wie CountingAid). Als Float gelesen rekomponierten sonst bis
    // zu 30 Zellen mit jedem Animationsframe.
    val pulse = rememberInfiniteTransition(label = "ten_frame_pulse").animateFloat(
        initialValue = 1f,
        targetValue = TenFramePulseLowAlpha,
        animationSpec = infiniteRepeatable(tween(TenFramePulseMillis), RepeatMode.Reverse),
        label = "ten_frame_pulse_alpha",
    )

    BoxWithConstraints(modifier = modifier.testTag("ten_frame")) {
        // Gemessen statt geschätzt: bei zehn Objekten pro Zeile ist die Breite die
        // einzige enge Schranke, und eine konservative Konstante verschenkte auf
        // einem normalen Telefon spürbar Bildgröße.
        val available = if (maxWidth.value > 0f) maxWidth.value else TenFrame.FallbackFieldWidthDp
        val sizeSp = TenFrame.emojiSizeSp(available)
        val rows = TenFrame.rows(state.total)
        val fullRows = TenFrame.fullRowCount(state.realCount)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TenFrame.RowGapDp.dp),
        ) {
            var index = 0
            if (fullRows > 0) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(TenFrame.RowGapDp.dp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .border(2.dp, SkyBlue, RoundedCornerShape(10.dp))
                        .padding(6.dp),
                ) {
                    repeat(fullRows) { row ->
                        TenFrameRow(emoji, rows[row], index, sizeSp, state, pulse, onTap)
                        index += rows[row]
                    }
                    // Der Stand steht innen an der unteren Kante — dieselbe Zahl,
                    // die beim Einrasten gesprochen wird.
                    Text(
                        text = state.fullTens.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = SkyBlue,
                        modifier = Modifier.testTag("ten_frame_total"),
                    )
                }
            }
            (fullRows until rows.size).forEach { row ->
                TenFrameRow(emoji, rows[row], index, sizeSp, state, pulse, onTap)
                index += rows[row]
            }
        }
    }
}

/** Eine Zeile des Feldes, mit der Fünfer-Lücke in der Mitte. */
@Composable
private fun TenFrameRow(
    emoji: String,
    length: Int,
    startIndex: Int,
    sizeSp: Int,
    state: TenFrameState,
    pulse: State<Float>,
    onTap: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TenFrame.CellGapDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(length) { column ->
            TenFrameCell(emoji, startIndex + column, sizeSp, state, pulse, onTap)
            // Die breitere Lücke ist die „Kraft der Fünf" — ohne sie sind zehn
            // Objekte eine Reihe, die man abzählen muss, statt zweier Hände.
            if (TenFrame.hasFiveGapAfter(column) && column < length - 1) {
                // Der Spacer ist selbst ein Kind der Row, `spacedBy` legt also
                // links und rechts je [TenFrame.CellGapDp] dazu — abziehen, sonst
                // ist die Lücke breiter als gerechnet und die Zeile läuft über.
                Spacer(Modifier.width((TenFrame.FiveGapDp - 2 * TenFrame.CellGapDp).dp))
            }
        }
    }
}

/**
 * Eine Zelle. Gesetzte Objekte sind echt, offene sind Platzhalter — dieselbe
 * Geister-Logik, mit der [MultiplicationMatrixGrid] seine Reihen zeigt: das Bild
 * ist schon da, das Kind macht es wahr.
 */
@Composable
private fun TenFrameCell(
    emoji: String,
    index: Int,
    sizeSp: Int,
    state: TenFrameState,
    pulse: State<Float>,
    onTap: (Int) -> Unit,
) {
    val real = state.isReal(index)
    val tappable = state.isTappable(index)
    val cell = TenFrame.cellSizeDp(sizeSp).dp
    val hit = TenFrame.hitTargetDp(sizeSp).dp
    // Die Trefferfläche ragt über die Zelle hinaus und damit über ihre Nachbarn.
    // Folgenlos, weil zu jedem Zeitpunkt genau eine Zelle auf Tipps reagiert —
    // und ein Vorschulkind trifft keine 25dp.
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.size(cell),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (tappable) hit else cell)
                .then(
                    if (tappable) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onTap(index) }
                    } else {
                        Modifier
                    },
                )
                .testTag("ten_frame_cell_$index"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emoji,
                fontSize = sizeSp.sp,
                modifier = Modifier.graphicsLayer {
                    // Der Puls wird hier in der Zeichenphase gelesen, nicht in der
                    // Komposition — derselbe Layer, den `Modifier.alpha(…)` aufmacht.
                    alpha = when {
                        tappable -> pulse.value
                        real -> 1f
                        else -> MultiplicationMatrix.GhostAlpha
                    }
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            )
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Unbenutzte Importe entfernen, bis `ktlint`/der Compiler keine Warnung mehr zeigt.

- [ ] **Step 3: Run the whole unit suite**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest`
Expected: PASS (nichts Bestehendes darf brechen)

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/TenFrameBoard.kt
git commit -m "$(cat <<'EOF'
feat(rechnen): das Zehnerfeld als Bild

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: `PlaceValueAnswer` — Stellenwert-Notation und Eingabe

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/PlaceValueAnswer.kt`

**Interfaces:**
- Consumes: `PlaceValueInput.*` (Task 3).
- Produces:
  ```kotlin
  @Composable
  fun PlaceValueAnswer(
      left: Int,
      right: Int,
      answer: Int,
      resetToken: String,
      onSubmit: (Int) -> Unit,
      modifier: Modifier = Modifier,
      solved: Boolean = false,
      enabled: Boolean = true,
  )
  ```
  Test-Tags: `place_value_tens`, `place_value_ones`, `place_value_submit`.

- [ ] **Step 1: Write the implementation**

`app/src/main/java/app/abcvorschule/ui/exercise/PlaceValueAnswer.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.components.IconChevronRight
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk

/** Spalte des Rechenzeichens — links vor den Ziffern, in jeder Zeile gleich breit. */
private val OperatorSlot = 32.dp

/** Abstand zwischen zwei Spalten. */
private val ColumnGap = 8.dp

/** Breite des Absenden-Pfeils, als Platzhalter auch in den Ziffernzeilen — sonst
 * stünden die Kästen nicht unter den Ziffern, sondern neben ihnen. */
private val ArrowSlot = AbcDimens.kidTouch - 8.dp

/**
 * Die Antwort in Stellenwert-Schreibweise: die Aufgabe untereinander, darunter
 * ein Kasten für die Zehner und einer für die Einer — in denselben Spalten wie
 * die Ziffern darüber (design doc 2026-08-30-zehnerfeld-addition).
 *
 * Keine „Z/E"-Kopfzeile: die Ausrichtung ist die Erklärung, und eine
 * Beschriftung, die ein Vorschulkind nicht liest, ist Dekoration.
 *
 * Zwei Dinge, die [NumberPad] anders macht und dort auch richtig bleiben:
 *
 * - **Kein Fokus beim Aufbau.** Die System-Tastatur würde genau das Zehnerfeld
 *   verdecken, an dem das Kind gerade rechnet. Sie kommt, wenn das Kind einen
 *   Kasten antippt — dieselbe Regel, die dort für die offene Zähl-Hilfe gilt.
 * - **Nichts wird gespiegelt.** Der Stand aus dem Bild landet nicht im Feld;
 *   die Zahl schreibt das Kind selbst. Genau dieser Schritt vom Bild zur Ziffer
 *   ist der geübte.
 */
@Composable
fun PlaceValueAnswer(
    left: Int,
    right: Int,
    answer: Int,
    resetToken: String,
    onSubmit: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** True, sobald die getippte Zahl die Antwort war — die Kästen bestätigen grün. */
    solved: Boolean = false,
    /** False während des Audio-Locks: Kästen und Pfeil sind blass und stumm. */
    enabled: Boolean = true,
) {
    val fields = PlaceValueInput.fieldCount(answer)
    var tens by remember(resetToken) { mutableStateOf(TextFieldValue("")) }
    var ones by remember(resetToken) { mutableStateOf(TextFieldValue("")) }
    val tensFocus = remember { FocusRequester() }
    val onesFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val opacity by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = tween(durationMillis = 200),
        label = "place_value_lock_opacity",
    )
    val complete = PlaceValueInput.isComplete(tens.text, ones.text, fields)

    fun submit() {
        if (!complete) return
        PlaceValueInput.combine(tens.text, ones.text)?.let(onSubmit)
    }

    LaunchedEffect(solved) {
        // Ohne das Einklappen sitzt die grüne Bestätigung hinter der Tastatur —
        // genau das, was sie zeigen soll.
        if (solved) keyboard?.hide()
    }

    val slot = PlaceValueInput.slotWidthDp(
        textSp = MaterialTheme.typography.displaySmall.fontSize.value,
        fontScale = LocalDensity.current.fontScale,
    ).dp

    Column(
        modifier = modifier.alpha(opacity),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Die Aufgabe untereinander: Zehner über Zehner, Einer über Einer. Das ist
        // der ganze Zweck dieser Zeilen — und der Grund, warum alle drei Zeilen
        // dieselbe Spaltenfolge haben.
        PlaceValueDigits(operator = "", value = left, slot = slot)
        PlaceValueDigits(operator = MathOperation.Add.symbol, value = right, slot = slot)
        Row(horizontalArrangement = Arrangement.spacedBy(ColumnGap)) {
            Spacer(Modifier.width(OperatorSlot))
            Box(
                modifier = Modifier
                    .width(slot * 2 + ColumnGap)
                    .height(3.dp)
                    .background(WarmInk, RoundedCornerShape(2.dp)),
            )
            Spacer(Modifier.width(ArrowSlot))
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(ColumnGap),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(OperatorSlot))
            if (fields == 2) {
                DigitField(
                    value = tens,
                    onValueChange = { raw ->
                        val digit = PlaceValueInput.lastDigit(raw.text)
                        tens = TextFieldValue(digit, TextRange(digit.length))
                        // Der Sprung ist die Antwort auf „welche Ziffer zuerst?":
                        // erst der Zehner, dann wandert der Fokus von selbst weiter.
                        if (digit.isNotEmpty()) onesFocus.requestFocus()
                    },
                    onSelectAll = { tens = tens.copy(selection = TextRange(0, tens.text.length)) },
                    focusRequester = tensFocus,
                    slot = slot,
                    solved = solved,
                    enabled = enabled,
                    imeAction = ImeAction.Next,
                    onImeAction = { onesFocus.requestFocus() },
                    tag = "place_value_tens",
                )
            } else {
                Spacer(Modifier.width(slot))
            }
            DigitField(
                value = ones,
                onValueChange = { raw ->
                    val digit = PlaceValueInput.lastDigit(raw.text)
                    ones = TextFieldValue(digit, TextRange(digit.length))
                },
                onSelectAll = { ones = ones.copy(selection = TextRange(0, ones.text.length)) },
                focusRequester = onesFocus,
                slot = slot,
                solved = solved,
                enabled = enabled,
                imeAction = ImeAction.Done,
                onImeAction = { submit() },
                // Rücktaste im leeren Einerfeld springt zurück in den Zehner —
                // sonst wäre die Zehnerziffer nur über einen genauen Tipp auf einen
                // kleinen Kasten erreichbar.
                onBackspaceWhenEmpty = { if (fields == 2) tensFocus.requestFocus() },
                tag = "place_value_ones",
            )
            Surface(
                onClick = { submit() },
                enabled = enabled && complete && !solved,
                shape = RoundedCornerShape(20.dp),
                color = SunCoral,
                modifier = Modifier
                    .size(ArrowSlot)
                    // Blass, solange eine Ziffer fehlt: der Pfeil zeigt, dass die
                    // Antwort noch nicht vollständig ist, statt eine halbe Zahl
                    // abzuschicken.
                    .alpha(if (complete) 1f else 0.4f)
                    .testTag("place_value_submit"),
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    IconChevronRight(tint = Cream, size = 28.dp)
                }
            }
        }
    }
}

/** Eine Ziffernzeile der Aufgabe, in derselben Spaltenfolge wie die Eingabe. */
@Composable
private fun PlaceValueDigits(operator: String, value: Int, slot: Dp) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(ColumnGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Digit(operator, OperatorSlot)
        Digit(if (value >= PlaceValueInput.TensFrom) (value / 10).toString() else "", slot)
        Digit((value % 10).toString(), slot)
        Spacer(Modifier.width(ArrowSlot))
    }
}

@Composable
private fun Digit(text: String, width: Dp) {
    Text(
        text = text,
        style = MaterialTheme.typography.displaySmall,
        color = WarmInk,
        textAlign = TextAlign.Center,
        modifier = Modifier.width(width),
    )
}

/** Ein Ziffernkasten. Zustandslos — sein Wert liegt in [PlaceValueAnswer]. */
@Composable
private fun DigitField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSelectAll: () -> Unit,
    focusRequester: FocusRequester,
    slot: Dp,
    solved: Boolean,
    enabled: Boolean,
    imeAction: ImeAction,
    onImeAction: () -> Unit,
    tag: String,
    onBackspaceWhenEmpty: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .width(slot)
            .focusRequester(focusRequester)
            .onFocusChanged { focus ->
                // Beim Antippen wird der Inhalt markiert, damit die nächste Ziffer
                // ihn ersetzt — ein Kind soll nicht erst löschen müssen.
                if (focus.isFocused && value.text.isNotEmpty()) onSelectAll()
            }
            .onPreviewKeyEvent { event ->
                if (
                    onBackspaceWhenEmpty != null &&
                    event.type == KeyEventType.KeyDown &&
                    event.key == Key.Backspace &&
                    value.text.isEmpty()
                ) {
                    onBackspaceWhenEmpty()
                    true
                } else {
                    false
                }
            }
            .testTag(tag),
        textStyle = MaterialTheme.typography.displaySmall.copy(textAlign = TextAlign.Center),
        singleLine = true,
        enabled = enabled,
        readOnly = solved,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        keyboardActions = KeyboardActions(
            onNext = { onImeAction() },
            onDone = { onImeAction() },
        ),
        colors = OutlinedTextFieldDefaults.colors(
            // Neutral beim Tippen, damit Grün genau eines heißt: richtig.
            focusedBorderColor = if (solved) LeafGreen else SkyBlue,
            unfocusedBorderColor = if (solved) LeafGreen else SkyBlue.copy(alpha = 0.5f),
            focusedTextColor = WarmInk,
            unfocusedTextColor = WarmInk,
            disabledBorderColor = SkyBlue.copy(alpha = 0.5f),
            disabledTextColor = WarmInk,
        ),
    )
}
```

- [ ] **Step 2: Compile and clean**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Alle unbenutzten Importe entfernen.

- [ ] **Step 3: Run the unit suite**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/PlaceValueAnswer.kt
git commit -m "$(cat <<'EOF'
feat(rechnen): Stellenwert-Antwort mit Zehner- und Einerfeld

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: `MathExercise` verzweigen

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/MathAttempt.kt` (nur Doku)

**Interfaces:**
- Consumes: `TenFrameState` (Task 2), `TenFrameBoard` (Task 4), `PlaceValueAnswer` (Task 5), `PlaceValueInput.resetToken` (Task 3), `GermanNumberWord.of`, `MathHinting.*`.
- Produces: keine neue öffentliche API.

- [ ] **Step 1: Add the ten-frame branch**

In `MathExercise.kt`, direkt nach `val usePad = input == MathInputMode.Typed` einfügen:

```kotlin
    // Addition im Tipp-Modus bekommt das Zehnerfeld: es steht von Anfang an da,
    // ist von Anfang an antippbar, und es gibt darum keine Zähl-Hilfe mehr, die
    // aufklappen müsste (design doc 2026-08-30-zehnerfeld-addition). Minus und
    // Malnehmen laufen unverändert über CountingAid.
    val useTenFrame = usePad && operation == MathOperation.Add
    var frame by remember(roundKey) { mutableStateOf(TenFrameState.forRound(round.left, round.right)) }
```

- [ ] **Step 2: Report the attempt without the aid flag**

In `handleGuess` und `resolve` gilt im Zehnerfeld-Modus `aided = false`. Ersetze in beiden Aufrufen `aided = countingOpen` durch `aided = !useTenFrame && countingOpen`, und in `handleGuess` den `opensAid`-Ausdruck durch:

```kotlin
                    // Im Zehnerfeld klappt nichts auf — der Hinweis zeigt statt
                    // dessen auf das Antippen, das schon die ganze Zeit möglich
                    // ist. `opensAid` heißt hier also „sprich den Tipp-Cue".
                    opensAid = usePad && misses == MathHinting.CountingAidFromMisses,
```

(der Ausdruck bleibt derselbe — nur der Kommentar kommt dazu, weil `useTenFrame` seine Bedeutung verschiebt).

Und `val countingOpen = usePad && misses >= MathHinting.CountingAidFromMisses` wird zu:

```kotlin
    // Nur noch für Minus und Malnehmen: das Zehnerfeld ist von Anfang an offen
    // und ist keine Hilfestufe, sondern die Darstellung der Aufgabe.
    val countingOpen = usePad && !useTenFrame && misses >= MathHinting.CountingAidFromMisses
```

- [ ] **Step 3: Render the new screen**

Ersetze im `if (usePad)`-Zweig den `prompt`-Block durch eine vorgelagerte Verzweigung. Der ganze Zweig sieht danach so aus:

```kotlin
    if (useTenFrame) {
        ExerciseStage(
            modifier = modifier.fillMaxSize(),
            promptChrome = {
                TaskPromptChrome(
                    title = null,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeakPrompt = onSpeakPrompt,
                )
            },
            prompt = {
                // Keine symbolische Zeile hier: die Stellenwert-Notation im
                // Antwortblock trägt die Aufgabe, und zweimal stünde sie sonst
                // auf demselben Schirm (§9).
                TenFrameBoard(
                    emoji = icon,
                    state = frame,
                    onTap = { index ->
                        if (locked || interactionLocked) return@TenFrameBoard
                        val next = frame.tap(index)
                        if (next == frame) return@TenFrameBoard
                        val ten = frame.completedTenAfter(next)
                        frame = next
                        if (ten != null) {
                            // Gesprochen wird nur der volle Zehner — alles andere
                            // sieht das Kind. Als Wort, nicht als Ziffer: „20." wäre
                            // im Deutschen die Ordinalzahl (GermanNumberWord).
                            haptics.nudge()
                            onSpeakCounting(GermanNumberWord.of(ten))
                        } else {
                            haptics.tick()
                        }
                    },
                )
            },
            answers = {
                PlaceValueAnswer(
                    left = round.left,
                    right = round.right,
                    answer = round.answer,
                    resetToken = PlaceValueInput.resetToken(roundKey, misses),
                    onSubmit = { handleGuess(it) },
                    solved = solved != null,
                    enabled = !interactionLocked && !locked,
                )
                if (misses >= MathHinting.ResolveFromMissesTyped && !locked) {
                    AbcResolveButton(onClick = ::resolve)
                }
            },
        )
    } else if (usePad) {
        // ... der bestehende Block, unverändert
    } else {
        // ... VisualQuantityBoard, unverändert
    }
```

- [ ] **Step 4: Compile and run the unit suite**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest`
Expected: PASS. Falls `MathHintingTest` bricht, prüfe zuerst, ob `MathHinting` wirklich unverändert ist — dieser Task ändert es nicht.

- [ ] **Step 5: Document the shifted meaning of `opensAid`**

In `MathAttempt.kt` an das KDoc von `opensAid` anhängen:

```kotlin
    /**
     * … (bestehender Text)
     *
     * Im Zehnerfeld der Addition klappt nichts auf — dort heißt dasselbe Flag
     * „sprich jetzt den Tipp-Cue": das Bild ist von Anfang an antippbar, und der
     * zweite Fehlversuch zeigt darauf, statt eine neue Ansicht zu öffnen.
     */
```

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt app/src/main/java/app/abcvorschule/ui/exercise/MathAttempt.kt
git commit -m "$(cat <<'EOF'
feat(rechnen): Addition im Tipp-Modus läuft über das Zehnerfeld

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Instrumentierter Bounds-Test

**Files:**
- Create: `app/src/androidTest/java/app/abcvorschule/ui/exercise/TenFrameBoundsTest.kt`

**Interfaces:**
- Consumes: `TenFrameBoard` (Task 4), Test-Tags `ten_frame`, `ten_frame_cell_$index`, `ten_frame_total`.
- Produces: nichts.

- [ ] **Step 1: Write the test**

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.abcvorschule.ui.theme.AbcTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Zehn Objekte nebeneinander sind der enge Fall des Zehnerfeldes. Gemessen wird
 * gegen das schmale Telefon bei font_scale 1.3 — das Testgerät —, denn dort
 * entscheidet sich, ob das zehnte Objekt noch im Feld steht.
 */
@RunWith(AndroidJUnit4::class)
class TenFrameBoundsTest {
    @get:Rule
    val rule = createComposeRule()

    private fun stage(left: Int, right: Int, onTapped: (TenFrameState) -> Unit = {}) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1.3f),
            ) {
                AbcTheme {
                    var state by remember { mutableStateOf(TenFrameState.forRound(left, right)) }
                    Box(Modifier.size(width = 328.dp, height = 400.dp)) {
                        TenFrameBoard(
                            emoji = "🍎",
                            state = state,
                            onTap = { index ->
                                state = state.tap(index)
                                onTapped(state)
                            },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theTenthObjectOfARowStaysInsideTheField() {
        stage(left = 16, right = 8)
        val field = rule.onNodeWithTag("ten_frame").getUnclippedBoundsInRoot()
        val tenth = rule.onNodeWithTag("ten_frame_cell_9").getUnclippedBoundsInRoot()
        assertTrue("$tenth vs $field", tenth.right <= field.right)
        assertTrue("$tenth vs $field", tenth.left >= field.left)
    }

    @Test
    fun onlyTheNextPlaceholderReactsAndTheFrameGrowsToTwenty() {
        var latest: TenFrameState? = null
        stage(left = 16, right = 8) { latest = it }
        // Ein Tipp auf ein gesetztes Objekt tut nichts.
        rule.onNodeWithTag("ten_frame_cell_0").performClick()
        rule.waitForIdle()
        assertTrue(latest == null || latest!!.filled == 0)
        // Vier Tipps auf den jeweils nächsten Platzhalter machen den Zehner voll.
        (16..19).forEach { index ->
            rule.onNodeWithTag("ten_frame_cell_$index").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("ten_frame_total").assertExists()
        assertTrue("filled=${latest?.filled}", latest?.filled == 4)
        assertTrue("tens=${latest?.fullTens}", latest?.fullTens == 20)
    }
}
```

- [ ] **Step 2: Run it if a device is attached**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.abcvorschule.ui.exercise.TenFrameBoundsTest
```

Expected: PASS. Ist kein Gerät angeschlossen, halte im Commit fest, dass der Test ungelaufen ist — **nicht** als bestanden melden.

- [ ] **Step 3: Commit**

```bash
git add app/src/androidTest/java/app/abcvorschule/ui/exercise/TenFrameBoundsTest.kt
git commit -m "$(cat <<'EOF'
test(rechnen): Zehnerfeld bleibt auf dem schmalen Gerät im Feld

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Prinzipien und README nachziehen

**Files:**
- Modify: `docs/PRODUCT_PRINCIPLES.md` (§8)
- Modify: `README.md` (Abschnitt zum Rechen-Trainer)

**Interfaces:** keine.

- [ ] **Step 1: §8 ergänzen**

Füge in `docs/PRODUCT_PRINCIPLES.md` §8 einen neuen Aufzählungspunkt **vor** dem Punkt „Zähl-Hilfe (nur Tipp-Modus)" ein:

```markdown
- **Zehnerfeld (Addition im Tipp-Modus):** Eine Additionsaufgabe steht **von Anfang an** als Zehnerfeld auf dem Schirm — zehn Objekte pro Zeile, nach dem fünften eine breitere Lücke („Kraft der Fünf"). Der erste Summand ist echt, der zweite liegt als **Platzhalter** im selben Feld und läuft dort weiter, wo der erste endet; ein Tipp macht einen Platzhalter echt (dieselbe Geister-Logik wie die Multiplikations-Matrix). **Antippbar ist immer nur der nächste Platzhalter**, und er pulsiert — deshalb gibt es **keinen Widerruf**: wo kein Fehltipp möglich ist, braucht es keinen Rückweg. Weil zu jedem Zeitpunkt genau eine Zelle reagiert, darf ihre **Trefferfläche über die Nachbarn ragen** (mind. 40dp), obwohl zehn Objekte pro Zeile das Emoji auf ~20sp drücken. **Volle Zeilen rasten ein:** sie liegen in einem gemeinsamen Rahmen, dessen Marke den laufenden Zehnerstand trägt (10, 20, 30) — und **nur beim Einrasten spricht die App**, den Stand als Wort auf `SpeechChannel.Counting`. Jeder andere Tipp ist stumm: gezählt wird in Zehnern, alles andere sieht das Kind. Die Antwort steht darunter in **Stellenwert-Schreibweise** — die Aufgabe untereinander, Zehner über Zehner, mit **getrenntem Zehner- und Einerfeld** in denselben Spalten. Ohne „Z/E"-Kopfzeile: die Ausrichtung ist die Erklärung. Der Fokus startet im Zehnerfeld und springt nach der Ziffer selbst in den Einer; ein Tipp in ein gefülltes Feld markiert seinen Inhalt zum Überschreiben; die System-Tastatur bleibt zu, bis ein Feld angetippt wird. **Nichts wird ins Antwortfeld gespiegelt** — der Schritt vom Bild zur Ziffer ist der geübte. Das Zehnerfeld ist **keine Hilfestufe**: eine damit gelöste Aufgabe wird gelobt wie jede andere, es klappt nichts auf, und nach zwei Fehlversuchen zeigt der gesprochene Cue nur auf das Antippen. Auflösen unverändert nach vier Fehlversuchen. Gilt **nur für Addition im Tipp-Modus**; Minus und Malnehmen behalten die Fünferzeilen der Zähl-Hilfe.
```

Ergänze außerdem im bestehenden Punkt „Zähl-Hilfe (nur Tipp-Modus)" nach „**Zähl-Hilfe (nur Tipp-Modus)**" die Einschränkung „**, Minus und Malnehmen**".

- [ ] **Step 2: README nachziehen**

Suche im `README.md` den Abschnitt, der Trainer 7 / Rechnen beschreibt (`grep -n "Rechnen" README.md`), und ergänze dort einen Satz im Stil der Nachbarzeilen:

> Additionsaufgaben im Tipp-Modus laufen über das **Zehnerfeld**: zehn Objekte pro Zeile, der zweite Summand als Platzhalter, den das Kind setzt; volle Zehner rasten sichtbar und hörbar ein, geantwortet wird in getrenntem Zehner- und Einerfeld.

- [ ] **Step 3: Commit**

```bash
git add docs/PRODUCT_PRINCIPLES.md README.md
git commit -m "$(cat <<'EOF'
docs: Zehnerfeld der Addition in §8 und README

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Abschluss

- [ ] `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest` — alles grün
- [ ] `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug` — baut
- [ ] Auf dem Gerät ansehen: eine Lektion mit Addition ≥ 11 (z. B. `l04-t10`, `8 + 6`, oder `l09-t10`, `12 + 9`) durchspielen und prüfen: Platzhalter sichtbar, nur der nächste reagiert, „zwanzig" beim Einrasten, Tastatur erst nach Tipp ins Feld, Fokussprung Zehner → Einer.
