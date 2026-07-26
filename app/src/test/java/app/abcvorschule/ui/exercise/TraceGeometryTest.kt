package app.abcvorschule.ui.exercise

import app.abcvorschule.content.GlyphStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class TraceGeometryTest {
    private val horizontal = listOf(TracePoint(0f, 0f), TracePoint(100f, 0f))
    private val elbow = listOf(TracePoint(0f, 0f), TracePoint(0f, 100f), TracePoint(100f, 100f))

    @Test
    fun normalizedStrokesScaleIntoTheGlyphBox() {
        val strokes = TraceGeometry.toPixels(
            strokes = listOf(GlyphStroke(listOf(listOf(0.0, 0.0), listOf(1.0, 0.5)))),
            boxSize = 200f,
            origin = TracePoint(10f, 20f),
        )
        assertEquals(1, strokes.size)
        assertEquals(TracePoint(10f, 20f), strokes[0][0])
        assertEquals(TracePoint(210f, 120f), strokes[0][1])
    }

    @Test
    fun polylineLengthSumsEverySegment() {
        assertEquals(100f, TraceGeometry.polylineLength(horizontal), 0.01f)
        assertEquals(200f, TraceGeometry.polylineLength(elbow), 0.01f)
        assertEquals(0f, TraceGeometry.polylineLength(listOf(TracePoint(1f, 1f))), 0.01f)
    }

    @Test
    fun pointAtFractionWalksTheWholePolyline() {
        assertEquals(TracePoint(0f, 0f), TraceGeometry.pointAtFraction(elbow, 0f))
        assertEquals(TracePoint(0f, 100f), TraceGeometry.pointAtFraction(elbow, 0.5f))
        assertEquals(TracePoint(100f, 100f), TraceGeometry.pointAtFraction(elbow, 1f))
        // Out-of-range fractions clamp instead of throwing.
        assertEquals(TracePoint(0f, 0f), TraceGeometry.pointAtFraction(elbow, -1f))
        assertEquals(TracePoint(100f, 100f), TraceGeometry.pointAtFraction(elbow, 2f))
    }

    @Test
    fun starsAreEvenlySpacedAndEndAtTheStrokeEnd() {
        val stars = TraceGeometry.starPositions(horizontal, 4)
        assertEquals(4, stars.size)
        assertEquals(25f, stars[0].x, 0.5f)
        assertEquals(100f, stars.last().x, 0.5f)
        assertTrue(stars.zipWithNext().all { (a, b) -> b.x > a.x })
    }

    @Test
    fun starCountBelowOneYieldsTheStrokeEndOnly() {
        assertEquals(listOf(TracePoint(100f, 0f)), TraceGeometry.starPositions(horizontal, 0))
    }

    @Test
    fun starOutlineAlternatesRadiiAndStartsAtTheTop() {
        val center = TracePoint(50f, 50f)
        val points = TraceGeometry.starPoints(center, outerRadius = 10f, innerRadius = 4f)
        assertEquals(10, points.size)
        // First spike points straight up, so it sits directly above the centre.
        assertEquals(center.x, points[0].x, 0.01f)
        assertEquals(center.y - 10f, points[0].y, 0.01f)
        points.forEachIndexed { i, p ->
            val expected = if (i % 2 == 0) 10f else 4f
            val radius = hypot(p.x - center.x, p.y - center.y)
            assertEquals("vertex $i", expected, radius, 0.01f)
        }
    }

    @Test
    fun starWithTooFewSpikesHasNoOutline() {
        assertTrue(TraceGeometry.starPoints(TracePoint(0f, 0f), 10f, 4f, spikes = 1).isEmpty())
    }

    @Test
    fun distanceToSegmentClampsToTheEndpoints() {
        val a = TracePoint(0f, 0f)
        val b = TracePoint(100f, 0f)
        assertEquals(0f, TraceGeometry.distanceToSegment(TracePoint(50f, 0f), a, b), 0.01f)
        assertEquals(10f, TraceGeometry.distanceToSegment(TracePoint(50f, 10f), a, b), 0.01f)
        assertEquals(20f, TraceGeometry.distanceToSegment(TracePoint(-20f, 0f), a, b), 0.01f)
        assertEquals(30f, TraceGeometry.distanceToSegment(TracePoint(130f, 0f), a, b), 0.01f)
    }

    @Test
    fun degenerateSegmentFallsBackToPointDistance() {
        val a = TracePoint(5f, 5f)
        assertEquals(5f, TraceGeometry.distanceToSegment(TracePoint(5f, 10f), a, a), 0.01f)
    }

    @Test
    fun distanceToPolylineTakesTheNearestSegment() {
        assertEquals(2f, TraceGeometry.distanceToPolyline(TracePoint(98f, 98f), elbow), 0.01f)
        assertEquals(3f, TraceGeometry.distanceToPolyline(TracePoint(3f, 40f), elbow), 0.01f)
    }
}
