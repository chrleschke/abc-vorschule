package app.abcvorschule.ui.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbcHapticsPatternTest {
    @Test
    fun `tick ist ein einzelner kurzer puls`() {
        assertEquals(1, HapticPatterns.timingsFor(HapticVerb.Tick).size)
        assertTrue(HapticPatterns.timingsFor(HapticVerb.Tick).first() in 15L..40L)
    }

    @Test
    fun `success ist ein doppelpuls`() {
        // waveform: [puls, pause, puls]
        assertEquals(3, HapticPatterns.timingsFor(HapticVerb.Success).size)
    }

    @Test
    fun `celebrate ist ein dreifachpuls unter 500ms gesamt`() {
        val t = HapticPatterns.timingsFor(HapticVerb.Celebrate)
        assertEquals(5, t.size) // puls,pause,puls,pause,puls
        assertTrue(t.sum() <= 500L)
    }

    @Test
    fun `amplituden passen zur laenge der timings`() {
        HapticVerb.entries.forEach { v ->
            assertEquals(
                HapticPatterns.timingsFor(v).size,
                HapticPatterns.amplitudesFor(v).size,
            )
        }
    }

    @Test
    fun `nudge ist schwaecher als tick`() {
        assertTrue(
            HapticPatterns.amplitudesFor(HapticVerb.Nudge).max() <
                HapticPatterns.amplitudesFor(HapticVerb.Tick).max(),
        )
    }
}
