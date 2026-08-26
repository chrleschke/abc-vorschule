package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
    fun bothOperandsShareOneFieldSoTheTowerStaysLow() {
        // Zwei getrennte Blöcke kosteten bis zu sieben Zeilen und drückten das Emoji
        // auf 20sp; gemeinsam sind es höchstens sechs.
        assertEquals(15, CountingField.objectCount(MathOperation.Add, 7, 8))
        assertEquals(15, CountingField.objectCount(MathOperation.Subtract, 15, 6))
        assertEquals(20, CountingField.objectCount(MathOperation.Multiply, 4, 5))
    }

    @Test
    fun theFrameAlwaysMarksTheSecondOperand() {
        // "7 + 8": die letzten acht sind die, die dazukommen.
        assertEquals(7, CountingField.framedFrom(MathOperation.Add, 7, 8))
        // "15 − 6": die letzten sechs sind die, die weggehen.
        assertEquals(9, CountingField.framedFrom(MathOperation.Subtract, 15, 6))
        // Malnehmen trägt seine Struktur in Reihen und Spalten; ein Rahmen darin
        // wäre eine zweite, widersprüchliche Gruppierung.
        assertNull(CountingField.framedFrom(MathOperation.Multiply, 4, 5))
    }

    @Test
    fun theCommonCaseKeepsFullSizedEmojiAndOnlyTheCrowdedOnesShrink() {
        assertEquals(CountingField.MaxEmojiSp, CountingField.emojiSizeSp(MathOperation.Add, 3, 4))
        assertTrue(
            CountingField.emojiSizeSp(MathOperation.Subtract, 30, 17) < CountingField.MaxEmojiSp,
        )
    }

    @Test
    fun everyRoundTheCurriculumCanProduceFitsTheTaskBlockAndStaysReadable() {
        // Stichproben reichen hier nicht: der echte Content enthält "30 − 17".
        // Deshalb über alle Operandenpaare, die der Validator zulässt
        // (MaxMathQuantity = 30).
        var worstHeight = 0f
        var worstWidth = 0f
        var smallest = CountingField.MaxEmojiSp
        forEveryValidRound { operation, left, right ->
            val size = CountingField.emojiSizeSp(operation, left, right)
            smallest = minOf(smallest, size)
            assertTrue(
                "$operation $left/$right -> ${size}sp is too small to tap or read",
                size >= CountingField.MinEmojiSp,
            )
            if (operation == MathOperation.Multiply) return@forEveryValidRound
            val height = CountingField.fieldHeightDp(
                CountingField.totalRows(operation, left, right),
                size,
            )
            val width = CountingField.rowWidthDp(size)
            worstHeight = maxOf(worstHeight, height)
            worstWidth = maxOf(worstWidth, width)
            assertTrue("$operation $left/$right -> ${height}dp high", height <= CountingField.TaskBlockDp)
            assertTrue("$operation $left/$right -> ${width}dp wide", width <= CountingField.FieldWidthDp)
        }
        // Es bindet die *Höhe*, nicht die Breite: fünf Spalten passen immer, sechs
        // Zeilen gerade so. Ohne diese Gegenprobe wäre der Test einer, der nichts
        // prüft, weil beide Schranken mit Luft eingehalten würden.
        assertTrue("worst height only reached ${worstHeight}dp", worstHeight > CountingField.TaskBlockDp * 0.9f)
        assertTrue("width has headroom, was ${worstWidth}dp", worstWidth < CountingField.FieldWidthDp * 0.9f)
        // Die Tippfläche im schlimmsten Fall, dokumentiert statt geschätzt:
        // 24sp × 1.3 + 2 × 3dp Zellpolster ≈ 37dp.
        assertEquals(24, smallest)
        assertTrue(CountingField.cellSizeDp(smallest) > 36f)
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
