package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeProgressTest {
    @Test
    fun fractionIsZeroAtTheStartAndOneAtTheTarget() {
        assertEquals(0f, MergeProgress.fraction(100f, 100f, 400f), 0.001f)
        assertEquals(1f, MergeProgress.fraction(400f, 100f, 400f), 0.001f)
        assertEquals(0.5f, MergeProgress.fraction(250f, 100f, 400f), 0.001f)
    }

    @Test
    fun draggingBackwardsOrPastTheTargetClamps() {
        assertEquals(0f, MergeProgress.fraction(0f, 100f, 400f), 0.001f)
        assertEquals(1f, MergeProgress.fraction(900f, 100f, 400f), 0.001f)
    }

    @Test
    fun zeroLengthTravelDoesNotDivideByZero() {
        assertEquals(0f, MergeProgress.fraction(100f, 100f, 100f), 0.001f)
    }

    @Test
    fun mergeCommitsOnlyCloseToTheVowel() {
        assertFalse(MergeProgress.isMerged(0f))
        assertFalse(MergeProgress.isMerged(0.7f))
        assertTrue(MergeProgress.isMerged(MergeProgress.CommitFraction))
        assertTrue(MergeProgress.isMerged(1f))
    }

    @Test
    fun glowRampsUpButNeverExceedsFull() {
        assertTrue(MergeProgress.glow(0f) < MergeProgress.glow(0.5f))
        assertTrue(MergeProgress.glow(0.5f) < MergeProgress.glow(1f))
        assertEquals(1f, MergeProgress.glow(1f), 0.001f)
        assertTrue(MergeProgress.glow(0f) >= 0f)
    }
}
