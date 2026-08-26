package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CountingFieldTest {
    @Test
    fun smallQuantitiesBreakIntoRowsOfFive() {
        assertEquals(emptyList<Int>(), CountingField.rows(0, CountingField.RowSize))
        assertEquals(listOf(3), CountingField.rows(3, CountingField.RowSize))
        assertEquals(listOf(5), CountingField.rows(5, CountingField.RowSize))
        assertEquals(listOf(5, 1), CountingField.rows(6, CountingField.RowSize))
    }

    @Test
    fun largeQuantitiesSwitchToRowsOfTenSoTheFieldDoesNotTowerUp() {
        // Fünferzeilen überall türmen den echten Content unbrauchbar hoch:
        // "30 − 17" wären zehn Zeilen und das Emoji müsste auf 14sp schrumpfen.
        assertEquals(CountingField.RowSize, CountingField.rowSize(MathOperation.Add, 3, 4))
        assertEquals(CountingField.WideRowSize, CountingField.rowSize(MathOperation.Subtract, 30, 17))
        assertEquals(listOf(10, 10, 10), CountingField.rows(30, CountingField.WideRowSize))
    }

    @Test
    fun theRowWidthIsDecidedForTheWholeRoundNotPerGroup() {
        // Sonst stünde bei "15 + 4" eine Zehnerzeile über einer Fünferzeile und die
        // beiden Gruppen wären nicht mehr vergleichbar.
        assertEquals(CountingField.WideRowSize, CountingField.rowSize(MathOperation.Add, 15, 4))
    }

    @Test
    fun aWideRowStillReadsAsTwoFivesForSubitizing() {
        assertEquals(listOf(5, 5), CountingField.fiveChunks(10))
        assertEquals(listOf(5, 2), CountingField.fiveChunks(7))
        assertEquals(listOf(3), CountingField.fiveChunks(3))
    }

    @Test
    fun everyQuantityUpToThirtyKeepsItsTotalAcrossTheRows() {
        listOf(CountingField.RowSize, CountingField.WideRowSize).forEach { rowSize ->
            (0..30).forEach { count ->
                assertEquals("count $count / $rowSize", count, CountingField.rows(count, rowSize).sum())
            }
        }
    }

    @Test
    fun noRowIsEverWiderThanItsRowSize() {
        listOf(CountingField.RowSize, CountingField.WideRowSize).forEach { rowSize ->
            (0..30).forEach { count ->
                assertTrue(
                    "count $count / $rowSize",
                    CountingField.rows(count, rowSize).all { it <= rowSize },
                )
            }
        }
    }

    @Test
    fun plusKeepsItsTwoGroupsWhileTheOtherOperationsShowOne() {
        assertEquals(listOf(7, 8), CountingField.groupSizes(MathOperation.Add, 7, 8))
        // Minus zeigt nur die Ausgangsmenge; die weggenommenen wandern in die Weg-Zone.
        assertEquals(listOf(15), CountingField.groupSizes(MathOperation.Subtract, 15, 6))
        // Malnehmen rendert die Matrix, nicht Zeilen dieser Breite — eine Gruppe.
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
    fun theCommonCaseKeepsFullSizedEmojiAndOnlyTheCrowdedOnesShrink() {
        // "7 + 8" ist die typische Runde über zehn — sie darf nichts einbüßen.
        assertEquals(CountingField.MaxEmojiSp, CountingField.emojiSizeSp(MathOperation.Add, 7, 8))
        assertTrue(
            CountingField.emojiSizeSp(MathOperation.Subtract, 30, 17) < CountingField.MaxEmojiSp,
        )
    }

    @Test
    fun everyRoundTheCurriculumCanProduceFitsTheTaskBlockAndStaysReadable() {
        // Stichproben reichen hier nicht: der echte Content enthält "30 − 17", und
        // der höchste denkbare Fall ist "30 − 30". Deshalb über alle Operandenpaare,
        // die der Validator zulässt (MaxMathQuantity = 30).
        var worstHeight = 0f
        var worstWidth = 0f
        var worstRows = 0
        forEveryValidRound { operation, left, right ->
            val size = CountingField.emojiSizeSp(operation, left, right)
            assertTrue(
                "$operation $left/$right -> ${size}sp is too small to tap or read",
                size >= CountingField.MinEmojiSp,
            )
            if (operation == MathOperation.Multiply) return@forEveryValidRound
            val rows = CountingField.totalRows(operation, left, right)
            val height = CountingField.fieldHeightDp(rows, size)
            val width = CountingField.rowWidthDp(
                CountingField.rowSize(operation, left, right),
                size,
            )
            worstHeight = maxOf(worstHeight, height)
            worstWidth = maxOf(worstWidth, width)
            worstRows = maxOf(worstRows, rows)
            assertTrue("$operation $left/$right -> ${height}dp high", height <= CountingField.TaskBlockDp)
            assertTrue("$operation $left/$right -> ${width}dp wide", width <= CountingField.FieldWidthDp)
        }
        // Es bindet die *Breite*, nicht die Höhe: der Zehnerzeilen-Umbruch deckelt
        // die Zeilenzahl bei sechs, und sechs Zeilen passen mühelos. Deshalb prüft
        // die Gegenprobe hier die Breite — sonst wäre der Test einer, der nichts
        // prüft, weil beide Schranken mit Luft eingehalten würden.
        assertTrue("worst width only reached ${worstWidth}dp", worstWidth > CountingField.FieldWidthDp * 0.9f)
        assertTrue("worst case needs $worstRows rows", worstRows <= 6)
        assertTrue("height has headroom, was ${worstHeight}dp", worstHeight < CountingField.TaskBlockDp * 0.9f)
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
