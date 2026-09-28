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
}
