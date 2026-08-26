package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class SymbolHuntLayoutTest {
    @Test
    fun sameSeedProducesTheSameLayout() {
        val a = SymbolHuntLayout.scatter(seed = 42L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f, tileSizePx = 80f)
        val b = SymbolHuntLayout.scatter(seed = 42L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f, tileSizePx = 80f)
        assertEquals(a, b)
    }

    @Test
    fun differentSeedsUsuallyProduceDifferentLayouts() {
        val a = SymbolHuntLayout.scatter(seed = 1L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f, tileSizePx = 80f)
        val b = SymbolHuntLayout.scatter(seed = 2L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f, tileSizePx = 80f)
        assertTrue(a != b)
    }

    @Test
    fun returnsOnePositionPerTile() {
        val positions = SymbolHuntLayout.scatter(seed = 7L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f, tileSizePx = 80f)
        assertEquals(11, positions.size)
    }

    @Test
    fun zeroTileCountReturnsEmpty() {
        assertEquals(emptyList<HuntTilePosition>(), SymbolHuntLayout.scatter(seed = 1L, tileCount = 0, boundsWidth = 360f, boundsHeight = 500f, tileSizePx = 80f))
    }

    @Test
    fun tilesRespectTheMinimumSpacingForARealisticFieldSize() {
        val positions = SymbolHuntLayout.scatter(seed = 99L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f, tileSizePx = 80f)
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
        val positions = SymbolHuntLayout.scatter(seed = 5L, tileCount = 11, boundsWidth = 10f, boundsHeight = 10f, tileSizePx = 80f)
        assertEquals(11, positions.size)
    }

    @Test
    fun tilesStayFullyInsideTheFieldEvenAtTheLargestScale() {
        // Regression: centers used to be drawn from a flat 10..90% band, which is
        // narrower than the largest tile's radius on a phone-width field — the
        // outermost tiles were cut off at the left and right screen edge.
        val tileSizePx = 80f
        for (seed in 1L..200L) {
            val positions = SymbolHuntLayout.scatter(
                seed = seed,
                tileCount = 11,
                boundsWidth = 336f,
                boundsHeight = 420f,
                tileSizePx = tileSizePx,
            )
            positions.forEachIndexed { index, tile ->
                // Der aufgeblähte Radius, nicht der Ruheradius: gedrückt wächst die
                // Kachel um HuntTileMorph.MaxInflate und muss trotzdem drinbleiben.
                val radius = tileSizePx * tile.scale / 2f * (1f + HuntTileMorph.MaxInflate)
                assertTrue("tile $index (seed $seed) juts out left: ${tile.x - radius}", tile.x - radius >= 0f)
                assertTrue("tile $index (seed $seed) juts out right: ${tile.x + radius}", tile.x + radius <= 336f)
                assertTrue("tile $index (seed $seed) juts out top: ${tile.y - radius}", tile.y - radius >= 0f)
                assertTrue("tile $index (seed $seed) juts out bottom: ${tile.y + radius}", tile.y + radius <= 420f)
            }
        }
    }

    @Test
    fun tilesTooLargeForTheFieldAreCenteredRatherThanPlacedOutside() {
        val positions = SymbolHuntLayout.scatter(
            seed = 3L,
            tileCount = 3,
            boundsWidth = 40f,
            boundsHeight = 40f,
            tileSizePx = 80f,
        )
        positions.forEach { tile ->
            assertEquals(20f, tile.x, 0.001f)
            assertEquals(20f, tile.y, 0.001f)
        }
    }
}
