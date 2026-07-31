package app.abcvorschule.ui.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathGeometryTest {
    private val width = 1000f
    private val spacing = 168f
    private val margin = 132f

    private fun points(count: Int) = PathGeometry.points(count, width, spacing, margin)

    @Test
    fun emptyPathHasNoPoints() {
        assertEquals(emptyList<PathPoint>(), points(0))
    }

    @Test
    fun yGrowsStrictlyMonotonically() {
        val p = points(26)
        assertEquals(margin, p[0].y, 0.01f)
        p.zipWithNext { a, b ->
            assertTrue("y must grow: ${a.y} -> ${b.y}", b.y > a.y)
        }
    }

    @Test
    fun verticalGapsStayWithinEightPercentOfNominalSpacing() {
        points(26).zipWithNext { a, b ->
            val gap = b.y - a.y
            assertTrue("gap $gap too small", gap >= spacing * 0.92f - 0.01f)
            assertTrue("gap $gap too large", gap <= spacing * 1.08f + 0.01f)
        }
    }

    @Test
    fun curveSwingsToBothSidesOfCenter() {
        val center = width / 2f
        val xs = points(16).map { it.x }
        assertTrue("must swing left", xs.any { it < center - 10f })
        assertTrue("must swing right", xs.any { it > center + 10f })
    }

    @Test
    fun pointsAreDeterministic() {
        assertEquals(points(26), points(26))
    }

    @Test
    fun amplitudeStaysInsideTheMargins() {
        points(26).forEach {
            assertTrue("x=${it.x} left of margin", it.x >= margin - 0.01f)
            assertTrue("x=${it.x} right of margin", it.x <= width - margin + 0.01f)
        }
    }

    @Test
    fun narrowScreenCollapsesToAStraightLine() {
        // Amplitude 0 must swallow the organic jitter too, otherwise nodes would
        // wander off a screen that has no room to swing.
        val p = PathGeometry.points(4, width = 120f, spacing = spacing, margin = margin)
        assertEquals(1, p.map { it.x }.distinct().size)
    }

    @Test
    fun contentHeightIsLastNodePlusMargin() {
        assertEquals(2 * margin, PathGeometry.contentHeight(1, spacing, margin), 0.01f)
        assertEquals(0f, PathGeometry.contentHeight(0, spacing, margin), 0.01f)
        assertEquals(
            points(26).last().y + margin,
            PathGeometry.contentHeight(26, spacing, margin),
            0.01f,
        )
    }

    @Test
    fun verticalGapsActuallyVary() {
        // Pairs with verticalGapsStayWithinEightPercentOfNominalSpacing: together
        // they pin "varies, but stays within +-8%". A tolerance of 0.5px is far
        // above float noise but far below the ~13px swing +-8% allows, so exactly
        // constant spacing (the old period-4 implementation) cannot pass this.
        val gaps = points(26).zipWithNext { a, b -> b.y - a.y }
        val distinctGaps = mutableListOf<Float>()
        for (gap in gaps) {
            if (distinctGaps.none { kotlin.math.abs(it - gap) < 0.5f }) distinctGaps.add(gap)
        }
        assertTrue(
            "gaps must vary, all were within 0.5px of each other: $gaps",
            distinctGaps.size > 1,
        )
    }

    @Test
    fun signedIsDeterministic() {
        for (index in 0 until 40) {
            for (salt in intArrayOf(3, 7, 11)) {
                val first = PathNoise.signed(index, salt)
                val second = PathNoise.signed(index, salt)
                assertEquals("signed($index, $salt) must be stable", first, second, 0f)
            }
        }
    }

    @Test
    fun signedStaysStrictlyInsideUnitRange() {
        for (index in 0 until 200) {
            for (salt in intArrayOf(0, 1, 3, 7, 11, 42, -5)) {
                val value = PathNoise.signed(index, salt)
                assertTrue(
                    "signed($index, $salt)=$value must be > -1f",
                    value > -1f,
                )
                assertTrue(
                    "signed($index, $salt)=$value must be < 1f",
                    value < 1f,
                )
            }
        }
    }

    @Test
    fun signedValuesAreNotAllIdentical() {
        // A constant PathNoise would silently disable every organic effect in
        // this package (x-jitter, y-jitter, and whatever Task 2/5 add later).
        val values = (0 until 30).map { PathNoise.signed(it, salt = 3) }
        assertTrue(
            "PathNoise.signed must vary across indices, got constant $values",
            values.distinct().size > 1,
        )
    }
}
