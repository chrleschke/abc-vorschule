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
        // Stichproben reichen hier nicht: der höchste Fall ist "26 − 26" (zwölf
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

    @Test
    fun multiplicationDefersItsSizeToTheMatrixItReuses() {
        assertEquals(
            MultiplicationMatrix.emojiSizeSp(6),
            CountingField.emojiSizeSp(MathOperation.Multiply, 5, 6),
        )
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
}
