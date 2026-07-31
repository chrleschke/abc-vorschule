package app.abcvorschule.ui.path

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathTrailTest {
    private val nodes = PathGeometry.points(count = 8, width = 1000f)

    @Test
    fun polylineOfFewerThanTwoNodesIsReturnedUnchanged() {
        assertEquals(emptyList<PathPoint>(), PathTrail.polyline(emptyList()))
        val single = listOf(PathPoint(10f, 10f))
        assertEquals(single, PathTrail.polyline(single))
    }

    @Test
    fun splinePassesExactlyThroughEveryNode() {
        // Sample s = 0 of segment i is node i by construction; the very last
        // entry is the last node. If this breaks, signs no longer sit on the trail.
        val line = PathTrail.polyline(nodes)
        nodes.forEachIndexed { index, node ->
            val sampled = line[index * PathTrail.SamplesPerSegment]
            assertEquals("node $index x", node.x, sampled.x, 0.01f)
            assertEquals("node $index y", node.y, sampled.y, 0.01f)
        }
        assertEquals(nodes.last(), line.last())
    }

    @Test
    fun splineIsSmootherThanTheStraightPolygon() {
        // A spline detours around the corners, so it must be strictly longer than
        // the straight connection — that is what "rounded" means numerically.
        fun length(p: List<PathPoint>) =
            p.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()
        assertTrue(length(PathTrail.polyline(nodes)) > length(nodes))
    }

    @Test
    fun dotsAreEvenlySpacedAlongTheTrail() {
        val dots = PathTrail.dots(PathTrail.polyline(nodes), walkedUpTo = -1, spacing = 18f)
        assertTrue("expected a good number of dots, got ${dots.size}", dots.size > 40)
        dots.zipWithNext { a, b ->
            val d = hypot(b.x - a.x, b.y - a.y)
            assertTrue("dot gap $d off nominal 18", d in 16.2f..19.8f)
        }
    }

    @Test
    fun dotRadiusVariesButStaysNearNominal() {
        val dots = PathTrail.dots(PathTrail.polyline(nodes), walkedUpTo = -1, radius = 4f)
        assertTrue("radius must vary", dots.map { it.radius }.distinct().size > 1)
        dots.forEach {
            assertTrue("radius ${it.radius} off nominal 4", it.radius in 3.4f..4.6f)
        }
    }

    @Test
    fun dotsBeforeTheReachedNodeAreMarkedWalked() {
        val line = PathTrail.polyline(nodes)
        val dots = PathTrail.dots(line, walkedUpTo = 3)
        assertTrue("walked dots must exist", dots.any { it.walked })
        assertTrue("unwalked dots must exist", dots.any { !it.walked })
        // walked must be a prefix: once the flag flips it never flips back.
        val firstUnwalked = dots.indexOfFirst { !it.walked }
        assertTrue(dots.drop(firstUnwalked).none { it.walked })
    }

    @Test
    fun nothingReachedMeansNothingWalked() {
        val dots = PathTrail.dots(PathTrail.polyline(nodes), walkedUpTo = -1)
        assertTrue(dots.none { it.walked })
        val fromZero = PathTrail.dots(PathTrail.polyline(nodes), walkedUpTo = 0)
        assertTrue(fromZero.none { it.walked })
    }

    @Test
    fun tooFewNodesProduceNoDots() {
        assertEquals(emptyList<TrailDot>(), PathTrail.dots(emptyList(), walkedUpTo = 0))
        assertEquals(
            emptyList<TrailDot>(),
            PathTrail.dots(listOf(PathPoint(1f, 1f)), walkedUpTo = 0),
        )
    }

    @Test
    fun dotsAreDeterministic() {
        val line = PathTrail.polyline(nodes)
        assertEquals(PathTrail.dots(line, walkedUpTo = 2), PathTrail.dots(line, walkedUpTo = 2))
    }
}
