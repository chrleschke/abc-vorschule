package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceProgressTest {
    private val boxSize = 200f

    // Two strokes shaped like a T: horizontal bar, then the vertical stem.
    private val strokes = listOf(
        listOf(TracePoint(0f, 0f), TracePoint(100f, 0f)),
        listOf(TracePoint(50f, 0f), TracePoint(50f, 100f)),
    )
    private val stars = strokes.map { TraceGeometry.starPositions(it, 2) }

    private fun update(state: TraceState, p: TracePoint) =
        TraceProgress.update(state, p, strokes, stars, boxSize)

    @Test
    fun stayingInTheCorridorIsNotFlaggedOffRoad() {
        val result = update(TraceState(), TracePoint(10f, 4f))
        assertFalse(result.offCorridor)
        assertFalse(result.collectedStar)
    }

    @Test
    fun leavingTheCorridorIsFlaggedAndDoesNotAdvance() {
        val result = update(TraceState(), TracePoint(10f, boxSize))
        assertTrue(result.offCorridor)
        assertEquals(TraceState(0, 0), result.state)
    }

    @Test
    fun touchingTheNextStarCollectsItInOrder() {
        val first = update(TraceState(), stars[0][0])
        assertTrue(first.collectedStar)
        assertEquals(TraceState(0, 1), first.state)
    }

    @Test
    fun skippingAheadToALaterStarDoesNotAdvance() {
        // Only the next star counts, so the child cannot shortcut the stroke.
        val result = update(TraceState(), stars[0][1])
        assertFalse(result.collectedStar)
        assertEquals(TraceState(0, 0), result.state)
    }

    @Test
    fun finishingAStrokeMovesToTheNextStrokeAtStarZero() {
        var state = TraceState(0, 1)
        val result = update(state, stars[0][1])
        assertTrue(result.collectedStar)
        assertEquals(TraceState(1, 0), result.state)
        assertFalse(result.glyphDone)
    }

    @Test
    fun collectingTheLastStarOfTheLastStrokeCompletesTheGlyph() {
        val result = update(TraceState(1, 1), stars[1][1])
        assertTrue(result.collectedStar)
        assertTrue(result.glyphDone)
    }

    @Test
    fun updatesAfterCompletionAreInert() {
        val done = TraceState(strokes.size, 0)
        val result = update(done, TracePoint(0f, 0f))
        assertTrue(result.glyphDone)
        assertFalse(result.collectedStar)
        assertFalse(result.offCorridor)
    }

    @Test
    fun corridorScalesWithTheGlyphBox() {
        // The same finger offset is inside a big glyph and outside a small one.
        val offset = TracePoint(10f, 20f)
        assertFalse(TraceProgress.update(TraceState(), offset, strokes, stars, 400f).offCorridor)
        assertTrue(TraceProgress.update(TraceState(), offset, strokes, stars, 60f).offCorridor)
    }
}
