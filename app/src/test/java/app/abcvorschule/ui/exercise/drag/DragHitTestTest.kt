package app.abcvorschule.ui.exercise.drag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragHitTestTest {
    private fun rect(l: Float, t: Float, r: Float, b: Float) = DragRect(l, t, r, b)

    @Test
    fun disjointRectsDoNotOverlap() {
        assertEquals(0f, DragHitTest.overlapArea(rect(0f, 0f, 10f, 10f), rect(20f, 20f, 30f, 30f)), 0.01f)
    }

    @Test
    fun overlapAreaIsTheIntersectionArea() {
        val a = rect(0f, 0f, 10f, 10f)
        val b = rect(5f, 5f, 15f, 15f)
        assertEquals(25f, DragHitTest.overlapArea(a, b), 0.01f)
    }

    @Test
    fun droppingNowhereHitsNoZone() {
        val zones = mapOf("z1" to rect(100f, 100f, 150f, 150f))
        assertNull(DragHitTest.bestZone(rect(0f, 0f, 40f, 40f), zones))
    }

    @Test
    fun straddlingTwoZonesPicksTheLargerOverlap() {
        // A wrong slot must still be a real miss, so we resolve to exactly one zone.
        val zones = mapOf(
            "left" to rect(0f, 0f, 100f, 100f),
            "right" to rect(100f, 0f, 200f, 100f),
        )
        assertEquals("right", DragHitTest.bestZone(rect(70f, 10f, 170f, 90f), zones))
        assertEquals("left", DragHitTest.bestZone(rect(30f, 10f, 130f, 90f), zones))
    }

    @Test
    fun tinyDragsDoNotCommit() {
        assertFalse(DragHitTest.shouldCommit(0f))
        assertFalse(DragHitTest.shouldCommit(DragHitTest.MinCommitPx))
        assertTrue(DragHitTest.shouldCommit(DragHitTest.MinCommitPx + 1f))
    }

    @Test
    fun emptyZoneMapIsSafe() {
        assertNull(DragHitTest.bestZone(rect(0f, 0f, 10f, 10f), emptyMap()))
    }
}
