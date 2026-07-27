package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class SymbolHuntLayoutTest {
    @Test
    fun sameSeedProducesTheSameLayout() {
        val a = SymbolHuntLayout.scatter(seed = 42L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        val b = SymbolHuntLayout.scatter(seed = 42L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        assertEquals(a, b)
    }

    @Test
    fun differentSeedsUsuallyProduceDifferentLayouts() {
        val a = SymbolHuntLayout.scatter(seed = 1L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        val b = SymbolHuntLayout.scatter(seed = 2L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        assertTrue(a != b)
    }

    @Test
    fun returnsOnePositionPerTile() {
        val positions = SymbolHuntLayout.scatter(seed = 7L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        assertEquals(11, positions.size)
    }

    @Test
    fun zeroTileCountReturnsEmpty() {
        assertEquals(emptyList<HuntTilePosition>(), SymbolHuntLayout.scatter(seed = 1L, tileCount = 0, boundsWidth = 360f, boundsHeight = 500f))
    }

    @Test
    fun tilesRespectTheMinimumSpacingForARealisticFieldSize() {
        val positions = SymbolHuntLayout.scatter(seed = 99L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        val minDistance = minOf(360f, 500f) * SymbolHuntLayout.MinCenterDistanceFraction
        for (i in positions.indices) {
            for (j in i + 1 until positions.size) {
                val distance = hypot(positions[i].x - positions[j].x, positions[i].y - positions[j].y)
                assertTrue("tiles $i and $j are too close: $distance < $minDistance", distance >= minDistance)
            }
        }
    }

    @Test
    fun terminatesEvenWhenSpacingCannotBeSatisfied() {
        // 11 tiles in a tiny field can't possibly satisfy the spacing constraint —
        // the retry loop must still terminate (bounded by MaxAttempts) rather than
        // looping forever.
        val positions = SymbolHuntLayout.scatter(seed = 5L, tileCount = 11, boundsWidth = 10f, boundsHeight = 10f)
        assertEquals(11, positions.size)
    }
}
