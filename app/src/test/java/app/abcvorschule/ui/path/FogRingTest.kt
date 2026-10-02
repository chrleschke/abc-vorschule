package app.abcvorschule.ui.path

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.LessonSigns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FogRingTest {
    private val pack = ContentRepository.fromClasspath().load()
    private val foot = PathPoint(200f, 1000f)

    @Test
    fun ringSitsCentredUnderTheTowerAndReachesPastItOnBothSides() {
        val tower = PathSignLayout.towerWidthDp(listOf("S", "E"))
        val band = FogRingGeometry.band(tower, foot)
        assertEquals(foot.x, band.cx, 0.001f)
        assertEquals(foot.x - tower / 2f - FogRingGeometry.ReachDp, band.left, 0.001f)
        assertEquals(foot.x + tower / 2f + FogRingGeometry.ReachDp, band.right, 0.001f)
        // Just under the blocks' bottom edge, not floating somewhere below the sign.
        assertTrue(band.cy >= foot.y && band.cy <= foot.y + FogRingGeometry.DropDp)
    }

    @Test
    fun ringIsFlatGroundNotAFrameAroundTheBlocks() {
        pack.lessons.forEach { lesson ->
            val glyphs = LessonSigns.forLesson(pack, lesson).blocks.map { it.glyph }
            val band = FogRingGeometry.band(PathSignLayout.towerWidthDp(glyphs), foot)
            assertEquals(FogRingGeometry.Aspect, band.rx / band.ry, 0.001f)
            // Even the haze and the fully spread pulse stay in the lower half of the
            // bottom block: behind the tower, the ring never climbs up its sides.
            val reachUp = foot.y - minOf(FogRingGeometry.haze(band).top, band.scaled(FogRingGeometry.PulseMaxScale).top)
            assertTrue("${lesson.id}: ring reaches ${reachUp}dp up the tower", reachUp < PathSignLayout.BlockDp / 2f)
        }
    }

    @Test
    fun aSingleBlockStillGetsARingThatShowsUnderIt() {
        val band = FogRingGeometry.band(PathSignLayout.BlockDp, foot)
        assertEquals(FogRingGeometry.MinRadiusXDp, band.rx, 0.001f)
        assertTrue(band.rx - PathSignLayout.BlockDp / 2f >= FogRingGeometry.ReachDp)
    }

    @Test
    fun densityScalesTheWholeRing() {
        val dp = FogRingGeometry.band(111f, PathPoint(0f, 0f))
        val px = FogRingGeometry.band(111f * 2.5f, PathPoint(0f, 0f), density = 2.5f)
        assertEquals(dp.rx * 2.5f, px.rx, 0.01f)
        assertEquals(dp.ry * 2.5f, px.ry, 0.01f)
        assertEquals(dp.cy * 2.5f, px.cy, 0.01f)
    }

    @Test
    fun withoutAMoveOnlyTheHeadHasARing() {
        assertEquals(1f, FogRingTransfer.presence(sign = 3, marker = 3f, from = 3, to = 3), 0f)
        assertEquals(0f, FogRingTransfer.presence(sign = 2, marker = 3f, from = 3, to = 3), 0f)
        assertEquals(0f, FogRingTransfer.presence(sign = 4, marker = 3f, from = 3, to = 3), 0f)
    }

    @Test
    fun onAMoveTheOldRingFadesAndTheNewOneGrowsIn() {
        val from = 2
        val to = 3
        fun at(m: Float) = FogRingTransfer.presence(from, m, from, to) to FogRingTransfer.presence(to, m, from, to)
        assertEquals(1f to 0f, at(2f))
        assertEquals(0f to 1f, at(3f))
        val (oldMid, newMid) = at(2.5f)
        assertTrue("never no ring at all mid-move", oldMid + newMid > 0.2f)
        // Monotone both ways: the old ring never comes back, the new one never shrinks.
        var lastOld = 1f
        var lastNew = 0f
        for (k in 0..20) {
            val (o, n) = at(2f + k / 20f)
            assertTrue(o <= lastOld + 1e-6f && n >= lastNew - 1e-6f)
            lastOld = o
            lastNew = n
        }
    }

    @Test
    fun aLongMoveSkipsTheSignsInBetween() {
        // A jump over several signs (free order, several lessons at once) lights
        // only the two ends — the signs passed on the way never flash a ring.
        for (k in 0..10) {
            val m = 1f + 3f * k / 10f
            assertEquals(0f, FogRingTransfer.presence(sign = 2, marker = m, from = 1, to = 4), 0f)
            assertEquals(0f, FogRingTransfer.presence(sign = 3, marker = m, from = 1, to = 4), 0f)
        }
    }

    @Test
    fun theNewRingGrowsFromSmallAndTheOldOneDriftsApart() {
        assertEquals(1f, FogRingTransfer.scale(1f, grows = true), 0f)
        assertEquals(1f, FogRingTransfer.scale(1f, grows = false), 0f)
        assertTrue(FogRingTransfer.scale(0f, grows = true) < 1f)
        assertTrue(FogRingTransfer.scale(0f, grows = false) > 1f)
    }
}
