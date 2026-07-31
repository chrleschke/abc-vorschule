package app.abcvorschule.ui.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathGeometryTest {
    private val width = 1000f
    private val spacing = 168f
    private val margin = 132f
    private val horizontalMargin = 84f

    private fun points(count: Int) = PathGeometry.points(count, width, spacing, margin, horizontalMargin)

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
        // The swing's bound is the horizontal inset, not the (larger) vertical
        // margin — they were split apart specifically so the swing can be wider
        // than the vertical gap allows.
        points(26).forEach {
            assertTrue("x=${it.x} left of horizontal margin", it.x >= horizontalMargin - 0.01f)
            assertTrue("x=${it.x} right of horizontal margin", it.x <= width - horizontalMargin + 0.01f)
        }
    }

    @Test
    fun defaultHorizontalMarginClearsTheSignOnTheNarrowestPhone() {
        // Unlike the other geometry constants, DefaultHorizontalMargin encodes a
        // requirement rather than a taste judgement: on a 360dp screen the swing
        // has to be wider than a signpost, or the trail runs behind the boards for
        // roughly half of every step (which is exactly what reusing the 132dp
        // vertical margin did). 360 - 2 * 84 = 192dp of swing against a 136dp
        // board. Asserted on the shipped defaults, not on this class's fixture.
        val screenWidth = 360f
        val boardWidth = PathSignDimens.BoardWidth.value
        val nominalSwing = screenWidth - 2 * PathGeometry.DefaultHorizontalMargin
        assertTrue(
            "swing $nominalSwing dp at 360dp width must exceed the ${boardWidth}dp sign",
            nominalSwing > boardWidth,
        )

        // And the points must actually use that room, not just be allowed to.
        val xs = PathGeometry.points(26, screenWidth).map { it.x }
        val actualSwing = xs.maxOrNull()!! - xs.minOrNull()!!
        assertTrue(
            "nodes only spread $actualSwing dp at 360dp width, less than the " +
                "${boardWidth}dp sign they have to clear",
            actualSwing > boardWidth,
        )
    }

    @Test
    fun narrowScreenCollapsesToAStraightLine() {
        // Amplitude 0 must swallow the organic jitter too, otherwise nodes would
        // wander off a screen that has no room to swing. A screen has no room to
        // swing once it is narrower than 2x the horizontal margin, regardless of
        // the (separate, larger) vertical margin.
        val p = PathGeometry.points(
            4,
            width = 120f,
            spacing = spacing,
            margin = margin,
            horizontalMargin = horizontalMargin,
        )
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

    @Test
    fun periodIsNotAWholeNumberOfNodes() {
        // If Period were a whole number of nodes (e.g. the old value of 4),
        // sin(index * 2*PI / Period) repeats exactly every `Period` nodes, so
        // for ANY index the raw swing angle at `index` and `index + 4` would
        // be identical. The only thing that could still separate their x
        // values is the index-dependent x-jitter, and that is bounded: each
        // point's jitter is independently clamped to +-6% of amplitude, so
        // two such points can differ by at most 2 * 0.06 * amplitude (~50px
        // for this fixture's 1000px width / 84px horizontal margin). With a
        // non-integer period like 3.7 the swing angle is not periodic over a
        // 4-node gap, so most pairs differ far beyond that jitter-only
        // ceiling. Checking many pairs (not just one) rules out a single
        // lucky/unlucky jitter cancellation from deciding the test either way.
        val p = points(16)
        val amplitude = width / 2f - horizontalMargin
        val jitterOnlyCeiling = 2 * 0.06f * amplitude
        val pairsBeyondJitter = (0 until 12).count { i ->
            kotlin.math.abs(p[i + 4].x - p[i].x) > jitterOnlyCeiling + 1f
        }
        assertTrue(
            "expected most index/index+4 pairs to differ beyond what jitter " +
                "alone explains ($jitterOnlyCeiling px); only $pairsBeyondJitter " +
                "of 12 did — looks like the swing period rasters onto whole-node " +
                "cycles again",
            pairsBeyondJitter >= 6,
        )
    }
}
