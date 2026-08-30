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
