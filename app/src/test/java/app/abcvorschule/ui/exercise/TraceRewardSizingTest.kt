package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceRewardSizingTest {
    @Test
    fun fullSizeWhenItFits() {
        assertEquals(1f, TraceRewardSizing.largestFitting { true })
    }

    @Test
    fun findsTheLargestFittingScaleWithoutOvershooting() {
        // Ein Bild, das bei Faktor 1 genau 300px hoch ist, in 180px Restplatz.
        val scale = TraceRewardSizing.largestFitting { 300f * it <= 180f }
        requireNotNull(scale)
        assertTrue("überschießt: $scale", 300f * scale <= 180f)
        assertTrue("verschenkt Platz: $scale", scale > 0.59f)
    }

    @Test
    fun nullWhenNotEvenTheFloorFits() {
        // Weniger Platz, als ein Viertel-Bild braucht: das Bild fällt weg.
        assertNull(TraceRewardSizing.largestFitting { 300f * it <= 50f })
    }

    @Test
    fun theFloorItselfCounts() {
        val floor = TraceRewardSizing.MinScale
        assertEquals(floor, TraceRewardSizing.largestFitting { it <= floor })
    }
}
