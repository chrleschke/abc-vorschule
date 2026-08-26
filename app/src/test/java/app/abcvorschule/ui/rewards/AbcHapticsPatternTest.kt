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
    fun `success ist ein einzelner kurzer puls`() {
        assertEquals(1, HapticPatterns.timingsFor(HapticVerb.Success).size)
        assertTrue(HapticPatterns.timingsFor(HapticVerb.Success).first() in 30L..60L)
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

    /**
     * Der Fallback ohne Amplitudensteuerung lief über
     * `createOneShot(timings.sum(), …)` und zählte damit die PAUSEN als
     * Vibrationszeit mit: aus dem Dreifachpuls wurde ein 360-ms-Dauerbrummen.
     * Die Waveform-Form hält An und Aus getrennt.
     */
    @Test
    fun `waveform-timings trennen puls und pause statt sie zu summieren`() {
        val w = HapticPatterns.waveformTimingsFor(HapticVerb.Celebrate)
        assertEquals(6, w.size) // fuehrende 0 (Delay-first) + puls,pause,puls,pause,puls
        assertEquals(0L, w.first())
        // Nur die Puls-Zellen vibrieren: 45+45+90, nicht die Summe aller 360.
        val vibrating = w.filterIndexed { i, _ -> i % 2 == 1 }.sum()
        assertEquals(180L, vibrating)
        assertEquals(360L, HapticPatterns.timingsFor(HapticVerb.Celebrate).sum())
    }

    @Test
    fun `waveform-timings sind fuer jedes verb delay-first`() {
        HapticVerb.entries.forEach { v ->
            val w = HapticPatterns.waveformTimingsFor(v)
            assertEquals(0L, w.first())
            assertEquals(HapticPatterns.timingsFor(v).size + 1, w.size)
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
