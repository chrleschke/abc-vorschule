package app.abcvorschule.ui.world

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tipps auf die Welt ([WorldTaps]) — gleicher Zuschnitt wie die Laternen-Loopings in SkyLanternsTest. */
class WorldTapsTest {
    /** Bei „Bewegung reduzieren" antwortet die Welt nicht auf Tipps. */
    @Test
    fun reduceMotionIgnoresTaps() {
        val taps = WorldTaps()
        taps.still = true
        taps.add(Offset(10f, 10f))
        assertTrue(taps.taps.isEmpty())
    }

    /** Alte Tipps fallen weg, bevor ein neuer dazukommt. */
    @Test
    fun oldTapsArePrunedOnTheNextTap() {
        val taps = WorldTaps()
        taps.now = 1f
        taps.add(Offset(1f, 1f))
        taps.now = 1f + WorldTaps.MaxAgeS + 0.5f
        taps.add(Offset(2f, 2f))
        assertEquals(listOf(Offset(2f, 2f)), taps.taps.map { it.at })
    }

    /** Wildes Trommeln hält die Liste klein; es bleiben die jüngsten Tipps. */
    @Test
    fun neverMoreThanMaxTaps() {
        val taps = WorldTaps()
        repeat(20) { i ->
            taps.now = i * 0.1f
            taps.add(Offset(i.toFloat(), 0f))
            assertTrue(taps.taps.size <= WorldTaps.MaxTaps)
        }
        assertEquals(WorldTaps.MaxTaps, taps.taps.size)
        assertEquals(19f, taps.taps.last().at.x)
        assertEquals(taps.taps.size, taps.taps.map { it.seed }.distinct().size)
    }

    /** Gezeichnet wird nur, was schon begonnen hat und noch nicht ausgeklungen ist. */
    @Test
    fun forEachRecentYieldsOnlyTapsWithinTheirAge() {
        val taps = WorldTaps()
        taps.now = 2f
        taps.add(Offset(1f, 0f))
        taps.now = 4f
        taps.add(Offset(2f, 0f))
        val seen = mutableListOf<Pair<Float, Float>>()
        taps.forEachRecent(seconds = 5f, maxAge = 2f) { tap, age -> seen += tap.at.x to age }
        assertEquals(listOf(2f to 1f), seen)
        val none = mutableListOf<Float>()
        taps.forEachRecent(seconds = 1f, maxAge = 5f) { _, age -> none += age }
        assertTrue("Tipps mit Zeitstempel in der Zukunft werden nicht gezeichnet: $none", none.isEmpty())
    }

    @Test
    fun envelopeRisesThenDecays() {
        assertEquals(0f, tapEnvelope(0f, rise = 0.3f, decay = 1f), 1e-6f)
        val peak = tapEnvelope(0.3f, rise = 0.3f, decay = 1f)
        assertTrue(tapEnvelope(0.1f, 0.3f, 1f) < peak)
        assertTrue(tapEnvelope(2f, 0.3f, 1f) < peak)
        assertTrue(tapEnvelope(5f, 0.3f, 1f) < 0.01f)
    }

    @Test
    fun noiseIsStablePerSeedAndInRange() {
        (0 until 50).forEach { seed ->
            (0 until 8).forEach { k ->
                val n = tapNoise(seed, k)
                assertEquals(n, tapNoise(seed, k))
                assertTrue("seed=$seed k=$k: $n", n >= 0f && n <= 1f)
            }
        }
        assertTrue(tapNoise(1, 0) != tapNoise(2, 0))
    }
}
