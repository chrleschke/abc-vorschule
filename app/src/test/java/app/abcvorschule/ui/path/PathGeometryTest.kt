package app.abcvorschule.ui.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathGeometryTest {
    private val width = 1000f
    private val spacing = 140f
    private val margin = 96f

    private fun points(count: Int) = PathGeometry.points(count, width, spacing, margin)

    @Test
    fun emptyPathHasNoPoints() {
        assertEquals(emptyList<PathPoint>(), points(0))
    }

    @Test
    fun nodesAreStackedTopDownAtConstantSpacing() {
        val p = points(4)
        assertEquals(margin, p[0].y, 0.01f)
        assertEquals(margin + spacing, p[1].y, 0.01f)
        assertEquals(margin + 3 * spacing, p[3].y, 0.01f)
    }

    @Test
    fun curveStartsCenteredAndSwingsRightThenLeft() {
        val p = points(5)
        val center = width / 2f
        assertEquals(center, p[0].x, 0.01f)
        assertTrue("node 1 swings right", p[1].x > center)
        assertEquals(center, p[2].x, 0.01f)
        assertTrue("node 3 swings left", p[3].x < center)
        assertEquals(center, p[4].x, 0.01f)
    }

    @Test
    fun amplitudeStaysInsideTheMargins() {
        points(16).forEach {
            assertTrue("x=${it.x} left of margin", it.x >= margin - 0.01f)
            assertTrue("x=${it.x} right of margin", it.x <= width - margin + 0.01f)
        }
    }

    @Test
    fun narrowScreenCollapsesToAStraightLine() {
        val p = PathGeometry.points(4, width = 120f, spacing = spacing, margin = margin)
        assertEquals(p.map { it.x }.distinct().size, 1)
    }

    @Test
    fun contentHeightLeavesMarginAtBothEnds() {
        assertEquals(2 * margin, PathGeometry.contentHeight(1, spacing, margin), 0.01f)
        assertEquals(
            2 * margin + 15 * spacing,
            PathGeometry.contentHeight(16, spacing, margin),
            0.01f,
        )
        assertEquals(0f, PathGeometry.contentHeight(0, spacing, margin), 0.01f)
    }
}
