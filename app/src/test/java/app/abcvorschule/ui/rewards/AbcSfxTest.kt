package app.abcvorschule.ui.rewards

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbcSfxTest {
    /** Jedes Geräusch, das der Code kennt, liegt auch im Paket — und nichts liegt ungenutzt da. */
    @Test
    fun everySoundHasItsClipAndEveryClipItsSound() {
        val dir = File("src/main/assets/sfx")
        val clips = dir.listFiles().orEmpty().filter { it.extension == "ogg" }.map { it.nameWithoutExtension }.toSet()
        assertEquals(Sfx.entries.map { it.asset }.toSet(), clips)
    }

    /** Offline-Budget: die Geräusche sind Beiwerk, sie dürfen die App nicht aufblähen. */
    @Test
    fun theSoundPackStaysTiny() {
        val bytes = File("src/main/assets/sfx").listFiles().orEmpty().sumOf { it.length() }
        assertTrue("SFX-Paket $bytes B", bytes < 100_000)
    }

    @Test
    fun eachTraceStarSoundsOneScaleStepHigherWithinTheSoundPoolRange() {
        val rates = (0 until 8).map { AbcSfx.blipRate(it) }
        rates.zipWithNext().forEach { (a, b) -> assertTrue("$a → $b", b > a) }
        rates.forEach { assertTrue("rate $it", it in 0.5f..2f) }
        assertEquals("nach einer Oktave von vorn", AbcSfx.blipRate(0), AbcSfx.blipRate(8), 1e-6f)
    }
}
