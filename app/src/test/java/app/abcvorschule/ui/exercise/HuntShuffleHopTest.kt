package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HuntShuffleHopTest {
    @Test
    fun aHopStartsAndLandsExactlyOnItsPlaces() {
        assertEquals(10f, HuntShuffleHop.x(10f, 90f, 0f), 1e-4f)
        assertEquals(90f, HuntShuffleHop.x(10f, 90f, 1f), 1e-4f)
        assertEquals(40f, HuntShuffleHop.y(40f, 200f, 0f, hopPx = 30f), 1e-4f)
        assertEquals(200f, HuntShuffleHop.y(40f, 200f, 1f, hopPx = 30f), 1e-4f)
    }

    /** Ein Bogen, keine Gerade: in der Flugmitte liegt die Kugel über der Verbindung. */
    @Test
    fun midFlightTheBallIsAboveTheStraightLine() {
        val straight = (40f + 200f) / 2f
        assertEquals(straight - 30f, HuntShuffleHop.y(40f, 200f, 0.5f, hopPx = 30f), 1e-3f)
    }

    /** Die Sperre hält, bis auch die zuletzt startende Kugel gelandet ist. */
    @Test
    fun theFieldStaysLockedUntilTheLastBallLands() {
        val lastStart = (0..12).maxOf { HuntShuffleHop.staggerMs(it) }
        assertTrue(lastStart + HuntShuffleHop.FlightMs <= HuntShuffleHop.LockMs)
    }

    /**
     * Die Bremse darf keine Wartestrafe werden: nach gut einer halben Sekunde ist das
     * Feld wieder offen.
     */
    @Test
    fun theLockIsShortEnoughNotToFeelLikeAPenalty() {
        assertTrue("LockMs=${HuntShuffleHop.LockMs}", HuntShuffleHop.LockMs <= 700)
    }
}
