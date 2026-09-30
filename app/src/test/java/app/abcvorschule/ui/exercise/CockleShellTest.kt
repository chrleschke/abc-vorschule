package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Flugziele und Deckel der Herzmuschel ([CockleGeometry]) — Nachfolger des Batterie-Tests. */
class CockleShellTest {
    private val eps = 1e-4f

    /** Die äußeren Mulden liegen genau am Rand der Spanne, symmetrisch um die Mitte. */
    @Test
    fun outerSlotsSitAtTheEdgesOfTheSpan() {
        listOf(3 to 0.44f, 5 to 0.58f).forEach { (count, span) ->
            val first = CockleGeometry.slotCenter(0, count)
            val last = CockleGeometry.slotCenter(count - 1, count)
            assertEquals("count=$count links", 0.5f - span / 2f, first.x, eps)
            assertEquals("count=$count rechts", 0.5f + span / 2f, last.x, eps)
            assertEquals("count=$count gleich hoch", first.y, last.y, eps)
        }
    }

    /** Lächel-Kurve: die mittlere Mulde liegt tiefer als die äußeren. */
    @Test
    fun theMiddleSlotSitsLowest() {
        listOf(3, 5).forEach { count ->
            val middle = CockleGeometry.slotCenter(count / 2, count)
            val edge = CockleGeometry.slotCenter(0, count)
            assertEquals(0.5f, middle.x, eps)
            assertTrue("count=$count", middle.y > edge.y)
        }
        assertEquals(0.5f, CockleGeometry.slotCenter(0, 1).x, eps)
    }

    /** Wenige Perlen bekommen größere Mulden, und keine Mulde überlappt ihre Nachbarin. */
    @Test
    fun fewerSlotsAreLargerAndNeverOverlap() {
        assertTrue(CockleGeometry.slotRadius(3) > CockleGeometry.slotRadius(5))
        listOf(3, 5).forEach { count ->
            val r = CockleGeometry.slotRadius(count)
            (1 until count).forEach { i ->
                val a = CockleGeometry.slotCenter(i - 1, count)
                val b = CockleGeometry.slotCenter(i, count)
                assertTrue("count=$count, Mulde $i", abs(b.x - a.x) > 2f * r)
            }
        }
    }

    /** Zu deckt der Deckel alles, ganz auf nichts; nach hinten klappt er erst ab halb offen. */
    @Test
    fun theLidOpensInTwoHalves() {
        assertEquals(1f, CockleGeometry.lidCover(0f), eps)
        assertEquals(0f, CockleGeometry.lidCover(1f), eps)
        assertEquals(0f, CockleGeometry.lidBack(0.5f), eps)
        assertEquals(1f, CockleGeometry.lidBack(1f), eps)
        // Beim Lugen bleibt die Muschel halb bedeckt, und der Deckel steht noch nicht hinten.
        val peek = CockleGeometry.PeekOpenness
        assertTrue(CockleGeometry.lidCover(peek) in 0.3f..0.4f)
        assertEquals(0f, CockleGeometry.lidBack(peek), eps)
    }

    @Test
    fun lidValuesAreMonotonicAndClamped() {
        val steps = (0..20).map { it / 20f }
        steps.zipWithNext().forEach { (a, b) ->
            assertTrue("lidCover $a->$b", CockleGeometry.lidCover(b) <= CockleGeometry.lidCover(a))
            assertTrue("lidBack $a->$b", CockleGeometry.lidBack(b) >= CockleGeometry.lidBack(a))
        }
        listOf(-1f, -0.2f, 1.3f, 5f).forEach { o ->
            assertTrue("lidCover($o)", CockleGeometry.lidCover(o) in 0f..1f)
            assertTrue("lidBack($o)", CockleGeometry.lidBack(o) in 0f..1f)
        }
        assertEquals(1f, CockleGeometry.lidCover(-1f), eps)
        assertEquals(1f, CockleGeometry.lidBack(5f), eps)
    }
}
