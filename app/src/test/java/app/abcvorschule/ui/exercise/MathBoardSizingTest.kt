package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MathBoardSizingTest {
    /** Drei volle Kacheln passen auf jede unterstützte Bühne in eine Reihe. */
    @Test
    fun threeTilesAlwaysFitOneRow() {
        listOf(280f, 296f, 320f, 348f, 396f, 420f).forEach { stage ->
            val gap = MathBoardSizing.tileGapDp(stage)
            assertTrue("$stage", 3 * MathBoardSizing.MinTileDp + 2 * gap <= MathBoardSizing.availableWidthDp(stage))
        }
    }

    @Test
    fun theGapGivesWayBeforeTheTiles() {
        assertEquals(MathBoardSizing.TileGapDp, MathBoardSizing.tileGapDp(396f), 0.001f)
        assertEquals(MathBoardSizing.MinTileGapDp, MathBoardSizing.tileGapDp(280f), 0.001f)
    }
}
