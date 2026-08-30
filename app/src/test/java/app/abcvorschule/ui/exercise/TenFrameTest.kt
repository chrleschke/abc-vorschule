package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TenFrameTest {
    /**
     * Die Zeilenbreite, wie sie tatsächlich gezeichnet wird: zehn Zellen und
     * neun Zwischenräume, deren Breite [TenFrame.hasFiveGapAfter] bestimmt.
     */
    private fun rowWidthDp(emojiSizeSp: Int): Float =
        TenFrame.RowSize * TenFrame.cellSizeDp(emojiSizeSp) +
            (0 until TenFrame.RowSize - 1).fold(0f) { acc, column ->
                acc + (if (TenFrame.hasFiveGapAfter(column)) TenFrame.FiveGapDp
                else TenFrame.CellGapDp)
            }
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
        // Hier wird bewusst **unter** die angenommene Breite des schmalen Telefons
        // (320dp) gemessen, bis 280dp, damit die Schranken auch dort halten.
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
            assertTrue("width $width -> ${rowWidthDp(size)}", rowWidthDp(size) <= width)
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
