package app.abcvorschule.ui.world

import org.junit.Assert.assertTrue
import org.junit.Test

class DeepSeaLightTest {
    /** Ruhig heißt: in einer Sekunde verändert sich kein Strahl merklich. */
    @Test
    fun raysChangeOnlySlowly() {
        val a = DeepSeaLight.rays(100f)
        val b = DeepSeaLight.rays(101f)
        a.zip(b).forEach { (r0, r1) ->
            assertTrue("Winkel ${r0.angleDeg} → ${r1.angleDeg}", kotlin.math.abs(r0.angleDeg - r1.angleDeg) < 1.8f)
            assertTrue("Kern ${r0.core} → ${r1.core}", kotlin.math.abs(r0.core - r1.core) < 0.12f)
        }
    }

    /** Aber sie verändern sich: nach einer halben Minute sieht das Licht anders aus. */
    @Test
    fun raysDoDriftOverHalfAMinute() {
        val a = DeepSeaLight.rays(0f)
        val b = DeepSeaLight.rays(30f)
        assertTrue(a.zip(b).any { (r0, r1) -> kotlin.math.abs(r0.angleDeg - r1.angleDeg) > 2f })
    }

    @Test
    fun raysStayWithinTheirRanges() {
        listOf(0f, 13f, 77f, 500f).flatMap { DeepSeaLight.rays(it) }.forEach { r ->
            assertTrue(r.angleDeg in 7f..17f)
            assertTrue(r.core in 0.1f..0.9f)
            assertTrue(r.alpha in 0.05f..0.18f)
            assertTrue(r.length in 0.5f..0.79f)
        }
    }

    /** Blasen steigen, und sie steigen langsam: in 10 s höchstens ein Zehntel der Höhe. */
    @Test
    fun bubblesRiseSlowlyAndWrapAround() {
        val a = DeepSeaLight.bubbles(0f)
        val b = DeepSeaLight.bubbles(10f)
        a.zip(b).forEach { (b0, b1) ->
            val dy = b0.y - b1.y
            assertTrue("dy=$dy", dy in 0f..0.11f || dy < -0.9f)
        }
        DeepSeaLight.bubbles(12345f).forEach { assertTrue(it.y in -0.03f..1.03f) }
    }
}
