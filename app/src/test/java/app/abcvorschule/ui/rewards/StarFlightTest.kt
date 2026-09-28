package app.abcvorschule.ui.rewards

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StarFlightTest {
    private val start = Offset(540f, 700f)
    private val counter = Offset(540f, 120f)

    @Test
    fun theStarStartsWhereItPoppedAndLandsOnTheCounter() {
        assertEquals(Offset.Zero, StarFlight.offset(start, counter, 0f))
        val landed = start + StarFlight.offset(start, counter, 1f)
        assertEquals(counter.x, landed.x, 1e-3f)
        assertEquals(counter.y, landed.y, 1e-3f)
    }

    /** Ein Bogen, kein Aufzug: in der Flugmitte schwingt der Stern seitlich aus. */
    @Test
    fun midFlightTheStarSwaysOffTheStraightLine() {
        val mid = StarFlight.offset(start, counter, 0.5f)
        assertTrue("mid=$mid", kotlin.math.abs(mid.x) > 10f)
    }

    @Test
    fun theStarShrinksToTheSizeOfTheCounterStar() {
        assertEquals(1.3f, StarFlight.scale(from = 1.3f, t = 0f), 1e-6f)
        assertEquals(StarFlight.LandedScale, StarFlight.scale(from = 1.3f, t = 1f), 1e-6f)
    }

    @Test
    fun aStarWithNowhereToFlyStaysPut() {
        assertEquals(Offset.Zero, StarFlight.offset(start, start, 0.5f))
    }
}
