package app.abcvorschule.ui.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathMarkerGeometryTest {
    private val nodes = listOf(
        PathPoint(100f, 1000f),
        PathPoint(300f, 1200f),
        PathPoint(150f, 1400f),
    )
    private val lift = 120f
    private val hop = 46f

    private fun tip(index: Float) = PathMarkerGeometry.tipFor(nodes, index, lift, hop)!!

    @Test
    fun noNodesMeansNoMarker() {
        assertNull(PathMarkerGeometry.tipFor(emptyList(), 0f, lift, hop))
    }

    @Test
    fun standingOnASignSitsExactlyOverItWithNoArc() {
        nodes.forEachIndexed { index, node ->
            val tip = tip(index.toFloat())
            assertEquals("x at node $index", node.x, tip.x, 0.01f)
            assertEquals("y at node $index", node.y - lift, tip.y, 0.01f)
        }
    }

    @Test
    fun midHopIsAboveTheStraightLineBetweenTwoSigns() {
        val tip = tip(0.5f)
        val straightY = (nodes[0].y + nodes[1].y) / 2f - lift
        assertEquals((nodes[0].x + nodes[1].x) / 2f, tip.x, 0.01f)
        // y grows downwards, so "above" is smaller — by the full hop height at the apex.
        assertEquals(straightY - hop, tip.y, 0.01f)
    }

    @Test
    fun theArcPeaksInTheMiddleOfTheHop() {
        val quarter = tip(0.25f).y - (nodes[0].y + (nodes[1].y - nodes[0].y) * 0.25f - lift)
        val half = tip(0.5f).y - ((nodes[0].y + nodes[1].y) / 2f - lift)
        assertTrue("apex $half must lift more than the quarter point $quarter", half < quarter)
        assertTrue("both must lift at all", quarter < 0f)
    }

    @Test
    fun indicesOutsideThePathAreClampedOntoIt() {
        assertEquals(tip(0f), tip(-3f))
        assertEquals(tip(nodes.lastIndex.toFloat()), tip(nodes.lastIndex + 5f))
    }

    @Test
    fun aSinglenodePathKeepsTheMarkerOnThatNode() {
        val single = listOf(PathPoint(50f, 500f))
        val tip = PathMarkerGeometry.tipFor(single, 0f, lift, hop)!!
        assertEquals(50f, tip.x, 0.01f)
        assertEquals(500f - lift, tip.y, 0.01f)
    }
}
