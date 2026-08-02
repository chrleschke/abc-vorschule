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
        val dots = PathTrail.dots(PathTrail.polyline(nodes), spacing = 18f)
        assertTrue("expected a good number of dots, got ${dots.size}", dots.size > 40)
        dots.zipWithNext { a, b ->
            val d = hypot(b.x - a.x, b.y - a.y)
            assertTrue("dot gap $d off nominal 18", d in 16.2f..19.8f)
        }
    }

    @Test
    fun dotRadiusVariesButStaysNearNominal() {
        val dots = PathTrail.dots(PathTrail.polyline(nodes), radius = 4f)
        assertTrue("radius must vary", dots.map { it.radius }.distinct().size > 1)
        dots.forEach {
            assertTrue("radius ${it.radius} off nominal 4", it.radius in 3.4f..4.6f)
        }
    }

    @Test
    fun nodeProgressGrowsAlongTheTrailAndSpansEveryNode() {
        val dots = PathTrail.dots(PathTrail.polyline(nodes))
        dots.zipWithNext { a, b ->
            assertTrue(
                "nodeProgress must grow: ${a.nodeProgress} -> ${b.nodeProgress}",
                b.nodeProgress > a.nodeProgress,
            )
        }
        assertTrue(dots.first().nodeProgress > 0f)
        assertTrue(
            "last dot ${dots.last().nodeProgress} must be near the last node ${nodes.lastIndex}",
            dots.last().nodeProgress > nodes.lastIndex - 1f,
        )
        assertTrue(dots.last().nodeProgress <= nodes.lastIndex.toFloat())
    }

    @Test
    fun nodeProgressLinesUpWithTheNodeItPassesBy() {
        // The whole warm/cold boundary rests on this: a head of 3.0 must warm the dots
        // up to node 3 and no further, so the dots bracketing node 3 must bracket 3.0.
        val line = PathTrail.polyline(nodes)
        val dots = PathTrail.dots(line)
        val node = nodes[3]
        val nearest = dots.minByOrNull { hypot(it.x - node.x, it.y - node.y) }!!
        assertEquals(3f, nearest.nodeProgress, 0.15f)
    }

    @Test
    fun tooFewNodesProduceNoDots() {
        assertEquals(emptyList<TrailDot>(), PathTrail.dots(emptyList()))
        assertEquals(emptyList<TrailDot>(), PathTrail.dots(listOf(PathPoint(1f, 1f))))
    }

    @Test
    fun dotsAreDeterministic() {
        val line = PathTrail.polyline(nodes)
        assertEquals(PathTrail.dots(line), PathTrail.dots(line))
    }
}
