package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeProgressTest {
    @Test
    fun draggingTheConsonantRightwardsClosesTheGap() {
        assertEquals(0.5f, MergeProgress.applyDrag(0f, 50f, 100f, fromRightTile = false), 0.001f)
        assertEquals(0.75f, MergeProgress.applyDrag(0.5f, 25f, 100f, fromRightTile = false), 0.001f)
    }

    @Test
    fun draggingTheVowelLeftwardsClosesTheGapToo() {
        assertEquals(0.5f, MergeProgress.applyDrag(0f, -50f, 100f, fromRightTile = true), 0.001f)
    }

    @Test
    fun draggingAwayFromTheMiddleOpensTheGapAndClamps() {
        assertEquals(0.25f, MergeProgress.applyDrag(0.5f, -25f, 100f, fromRightTile = false), 0.001f)
        assertEquals(0f, MergeProgress.applyDrag(0.2f, -80f, 100f, fromRightTile = false), 0.001f)
        assertEquals(1f, MergeProgress.applyDrag(0.9f, 80f, 100f, fromRightTile = false), 0.001f)
    }

    @Test
    fun zeroTravelDoesNotDivideByZero() {
        assertEquals(0.4f, MergeProgress.applyDrag(0.4f, 30f, 0f, fromRightTile = false), 0.001f)
    }

    @Test
    fun twoTapsReachTheMagnetZone() {
        val afterFirst = MergeProgress.stepped(0f)
        assertFalse(MergeProgress.shouldAttract(afterFirst))
        val afterSecond = MergeProgress.stepped(afterFirst)
        assertTrue(MergeProgress.shouldAttract(afterSecond))
    }

    @Test
    fun steppingNeverOvershoots() {
        assertEquals(1f, MergeProgress.stepped(0.9f), 0.001f)
    }

    @Test
    fun releaseAttractsOnlyInsideTheMagnetZone() {
        assertFalse(MergeProgress.shouldAttract(0f))
        assertFalse(MergeProgress.shouldAttract(0.59f))
        assertTrue(MergeProgress.shouldAttract(MergeProgress.AttractFraction))
        assertTrue(MergeProgress.shouldAttract(1f))
    }

    @Test
    fun contactRequiresTheTilesToActuallyTouch() {
        assertFalse(MergeProgress.isContact(0.9f))
        assertTrue(MergeProgress.isContact(MergeProgress.CommitFraction))
        assertTrue(MergeProgress.isContact(1f))
    }

    @Test
    fun glowRampsUpButNeverExceedsFull() {
        assertTrue(MergeProgress.glow(0f) < MergeProgress.glow(0.5f))
        assertTrue(MergeProgress.glow(0.5f) < MergeProgress.glow(1f))
        assertEquals(1f, MergeProgress.glow(1f), 0.001f)
        assertTrue(MergeProgress.glow(0f) >= 0f)
    }
}
