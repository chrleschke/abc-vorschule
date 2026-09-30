package app.abcvorschule.ui.path

import org.junit.Assert.assertTrue
import org.junit.Test

class SkyLanternsTest {
    /** Schon beim Öffnen stehen Laternen am Himmel, nicht erst nach Minuten. */
    @Test
    fun theSkyIsNeverEmpty() {
        listOf(0f, 37f, 400f, 5_000f).forEach { t ->
            val n = SkyLanterns.at(t).size
            assertTrue("t=$t: $n Laternen", n in 6..16)
        }
    }

    /** Sie steigen langsam: in einer Sekunde höchstens gut 1 % der Höhe. */
    @Test
    fun lanternsRiseSlowly() {
        val a = SkyLanterns.at(200f).associateBy { it.x.toBits() }
        val b = SkyLanterns.at(201f)
        b.forEach { l ->
            val prev = SkyLanterns.at(200f).minByOrNull { kotlin.math.abs(it.x - l.x) + kotlin.math.abs(it.y - l.y) } ?: return@forEach
            val dy = prev.y - l.y
            assertTrue("dy=$dy", dy < 0.012f)
        }
        assertTrue(a.isNotEmpty())
    }

    /** Ferne Laternen sind kleiner und blasser als nahe. */
    @Test
    fun distanceShowsInSizeAndBrightness() {
        val all = (0 until 50).flatMap { SkyLanterns.at(it * 13f) }.filter { it.y > 0.2f }
        val far = all.filter { it.depth < 0.25f }
        val near = all.filter { it.depth > 0.75f }
        assertTrue(far.isNotEmpty() && near.isNotEmpty())
        assertTrue(far.maxOf { it.width } < near.minOf { it.width })
        assertTrue(far.map { it.alpha }.average() < near.map { it.alpha }.average())
    }

    @Test
    fun lanternsStartBehindTheHillsAndLeaveAtTheTop() {
        (0 until 30).flatMap { SkyLanterns.at(it * 7f) }.forEach { l ->
            assertTrue(l.y <= SkyLanterns.StartY + 1e-4f && l.y >= SkyLanterns.EndY - 1e-4f)
        }
    }

    /** Der Looping startet und endet am Platz der Laterne, oben liegt sein höchster Punkt. */
    @Test
    fun theLoopReturnsHomeAndTurnsOnce() {
        val (x0, y0, r0) = SkyLanterns.loop(0f)
        val (x1, y1, r1) = SkyLanterns.loop(1f)
        val (_, yMid, _) = SkyLanterns.loop(0.5f)
        assertTrue(kotlin.math.abs(x0) < 1e-4f && kotlin.math.abs(y0) < 1e-4f && r0 == 0f)
        assertTrue(kotlin.math.abs(x1) < 1e-3f && kotlin.math.abs(y1) < 1e-3f)
        assertTrue(kotlin.math.abs(r1 - 360f) < 1e-3f)
        assertTrue("oben ist negativ: $yMid", yMid < -1.9f)
    }

    /** Ein Tipp neben jede Laterne fliegt nichts; einer auf eine Laterne startet ihren Looping. */
    @Test
    fun tappingALanternStartsItsLoop() {
        val loops = app.abcvorschule.ui.path.LanternLoops()
        loops.now = 200f
        val size = androidx.compose.ui.geometry.Size(1000f, 2000f)
        val target = SkyLanterns.at(200f).last()
        val w = size.width * target.width
        val at = androidx.compose.ui.geometry.Offset(size.width * target.x, size.height * target.y + w * 1.33f * 0.45f)
        assertTrue(loops.tap(at, size, minRadiusPx = 10f))
        // Derselbe Tipp während des Loopings startet ihn nicht neu.
        assertTrue(!loops.tap(at, size, minRadiusPx = 10f))
        loops.still = true
        loops.now = 300f
        assertTrue("bei „Bewegung reduzieren“ kein Looping", !loops.tap(at, size, minRadiusPx = 10f))
    }
}
