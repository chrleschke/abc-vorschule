package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordFrameSizingTest {
    /** ExerciseStage caps content at 420.dp and pads 12.dp per side. */
    private val stageWidth = 396f

    /** A 360.dp phone, minus AbcDimens.screenHorizontal and the stage padding. */
    private val narrowPhone = 296f

    /** The longest authored word: Häuser = Hä | u | s | e | r. */
    private val longestWordFrames = 5

    private fun rowWidth(available: Float, frameCount: Int): Float =
        WordFrameSizing.frameWidthDp(available, frameCount) * frameCount +
            WordFrameSizing.gapDp(available, frameCount) * (frameCount - 1)

    @Test
    fun fiveFramesFitTheStageInsteadOfOverflowing() {
        val used = rowWidth(stageWidth, longestWordFrames)
        assertTrue("row needs $used dp of $stageWidth dp", used <= stageWidth)
    }

    @Test
    fun fiveFramesAlsoFitANarrowPhone() {
        val used = rowWidth(narrowPhone, longestWordFrames)
        assertTrue("row needs $used dp of $narrowPhone dp", used <= narrowPhone)
    }

    @Test
    fun everyAuthoredWordLengthFitsBothWidths() {
        (1..longestWordFrames).forEach { count ->
            listOf(stageWidth, narrowPhone).forEach { available ->
                val used = rowWidth(available, count)
                assertTrue("$count frames need $used dp of $available dp", used <= available)
            }
        }
    }

    @Test
    fun theGapOnlyTightensWhenTheFramesWouldBreachTheFloor() {
        // Roomy: keep the comfortable gap.
        assertEquals(WordFrameSizing.MaxGapDp, WordFrameSizing.gapDp(stageWidth, 3), 0.01f)
        // Tight: give the space to the frames instead.
        assertEquals(WordFrameSizing.MinGapDp, WordFrameSizing.gapDp(narrowPhone, 5), 0.01f)
    }

    @Test
    fun shortWordsKeepTheComfortableMaximumWidth() {
        assertEquals(WordFrameSizing.MaxFrameDp, WordFrameSizing.frameWidthDp(stageWidth, 2), 0.01f)
    }

    @Test
    fun framesNeverShrinkBelowTheTouchTargetFloor() {
        // Absurdly narrow: we clamp at the hit-box minimum and accept the overflow
        // rather than rendering frames a child cannot hit.
        val width = WordFrameSizing.frameWidthDp(available = 100f, frameCount = 5)
        assertEquals(WordFrameSizing.MinFrameDp, width, 0.01f)
        assertTrue("floor must clear the 56dp spec minimum", WordFrameSizing.MinFrameDp >= 56f)
    }

    @Test
    fun aDegenerateFrameCountFallsBackToTheMaximum() {
        assertEquals(WordFrameSizing.MaxFrameDp, WordFrameSizing.frameWidthDp(stageWidth, 0), 0.01f)
    }

    @Test
    fun glyphShrinksForAMultiCharacterBlockInANarrowFrame() {
        val wide = WordFrameSizing.glyphSp(WordFrameSizing.MaxFrameDp, longestDisplayChars = 1)
        val narrow = WordFrameSizing.glyphSp(50f, longestDisplayChars = 3)
        assertTrue("$narrow should be smaller than $wide", narrow < wide)
    }

    @Test
    fun glyphNeverLeavesTheLegibleRange() {
        val tiny = WordFrameSizing.glyphSp(WordFrameSizing.MinFrameDp, longestDisplayChars = 3)
        assertTrue(tiny >= WordFrameSizing.MinGlyphSp)
        val huge = WordFrameSizing.glyphSp(400f, longestDisplayChars = 1)
        assertEquals(WordFrameSizing.MaxGlyphSp, huge, 0.01f)
    }

    // --- row wrapping for long words ----------------------------------------
    // Reuses the `stageWidth` (396dp usable stage width) declared above.

    @Test
    fun sixSegmentsStayOnOneRow() {
        // "Häuser" -> H·ä·u·s·e·r, the longest word in the current content.
        assertEquals(1, WordFrameSizing.rowCount(stageWidth, 6))
        assertEquals(6, WordFrameSizing.segmentsPerRow(stageWidth, 6))
    }

    @Test
    fun eightSegmentsWrapIntoTwoBalancedRows() {
        // "Xylophon" -> X·y·l·o·p·h·o·n. Tappability beats staying on one line.
        assertEquals(2, WordFrameSizing.rowCount(stageWidth, 8))
        assertEquals(4, WordFrameSizing.segmentsPerRow(stageWidth, 8))
    }

    @Test
    fun sevenSegmentsWrapAndTheFirstRowTakesTheExtra() {
        assertEquals(2, WordFrameSizing.rowCount(stageWidth, 7))
        assertEquals(4, WordFrameSizing.segmentsPerRow(stageWidth, 7))
    }

    @Test
    fun frameWidthNeverDropsBelowTheTouchFloorAfterWrapping() {
        val perRow = WordFrameSizing.segmentsPerRow(stageWidth, 8)
        assertTrue(WordFrameSizing.frameWidthDp(stageWidth, perRow) >= WordFrameSizing.MinFrameDp)
    }

    @Test
    fun aSingleSegmentNeedsOneRow() {
        assertEquals(1, WordFrameSizing.rowCount(stageWidth, 1))
        assertEquals(1, WordFrameSizing.segmentsPerRow(stageWidth, 1))
    }

    @Test
    fun anEmptyWordDoesNotDivideByZero() {
        assertEquals(1, WordFrameSizing.rowCount(stageWidth, 0))
        assertEquals(1, WordFrameSizing.segmentsPerRow(stageWidth, 0))
    }

    @Test
    fun aVeryNarrowStageStillFitsOneSegmentPerRow() {
        assertEquals(1, WordFrameSizing.maxPerRow(20f))
        assertEquals(4, WordFrameSizing.rowCount(20f, 4))
    }
}
