package app.abcvorschule.ui.path

import app.abcvorschule.content.Lesson
import app.abcvorschule.content.LessonStatus
import app.abcvorschule.progress.LessonState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathFocusTest {
    private fun lesson(id: String, index: Int) = Lesson(
        id = id,
        index = index,
        phase = 1,
        title = id,
        nodeLabel = id,
        status = LessonStatus.authored,
    )

    private val lessons = (0 until 5).map { lesson("l$it", it + 1) }

    @Test
    fun headFollowsTheHighlightedLesson() {
        val states = mapOf("l0" to LessonState.Mastered, "l1" to LessonState.Available)
        assertEquals(1, PathFocus.headIndex(lessons, states, "l1"))
    }

    @Test
    fun headFallsBackToTheFurthestReachedLessonWithoutAHighlight() {
        val states = mapOf("l0" to LessonState.Mastered, "l1" to LessonState.InProgress)
        assertEquals(1, PathFocus.headIndex(lessons, states, null))
        assertEquals(1, PathFocus.headIndex(lessons, states, "not-in-this-pack"))
    }

    @Test
    fun headIsMinusOneOnAnUntouchedPathWithoutAHighlight() {
        assertEquals(-1, PathFocus.headIndex(lessons, emptyMap(), null))
    }

    @Test
    fun headStaysOnTheFibelOrderWhenALaterLessonWasFinishedOutOfOrder() {
        // Free order: l3 is mastered while l0 is still the highlight. The marker — and
        // with it the warm stretch of trail — must not jump ahead to l3; that lesson
        // shows its own progress on its own sign.
        val states = mapOf(
            "l0" to LessonState.Available,
            "l3" to LessonState.Mastered,
        )
        assertEquals(0, PathFocus.headIndex(lessons, states, "l0"))
    }

    @Test
    fun indexOfResolvesLessonIdsAndNulls() {
        assertEquals(2, PathFocus.indexOf(lessons, "l2"))
        assertNull(PathFocus.indexOf(lessons, null))
        assertNull(PathFocus.indexOf(lessons, "nope"))
    }

    @Test
    fun hopIsLongerForAFurtherJumpButAlwaysClamped() {
        val one = PathFocus.hopMillis(2f, 3f)
        val three = PathFocus.hopMillis(2f, 5f)
        assertTrue("a 3-node hop must not be shorter than a 1-node one", three >= one)
        assertEquals(PathFocus.MinHopMillis, PathFocus.hopMillis(2f, 2f))
        assertEquals(PathFocus.MaxHopMillis, PathFocus.hopMillis(0f, 25f))
        assertEquals(one, PathFocus.hopMillis(3f, 2f))
    }

    @Test
    fun scrollTargetParksTheNodeAboveTheMiddleOfTheViewport() {
        val target = PathFocus.scrollTarget(nodeY = 2000f, viewportHeight = 1000, maxScroll = 5000)
        // node lands at 2000 - target inside the viewport
        val onScreen = 2000f - target
        assertTrue("node at $onScreen must be inside the viewport", onScreen in 0f..1000f)
        assertTrue("node at $onScreen must sit above the middle", onScreen < 500f)
    }

    @Test
    fun scrollTargetStaysInsideTheScrollableRange() {
        assertEquals(0, PathFocus.scrollTarget(nodeY = 100f, viewportHeight = 1000, maxScroll = 5000))
        assertEquals(5000, PathFocus.scrollTarget(nodeY = 9000f, viewportHeight = 1000, maxScroll = 5000))
        // A path shorter than the viewport does not scroll at all.
        assertEquals(0, PathFocus.scrollTarget(nodeY = 400f, viewportHeight = 1000, maxScroll = 0))
    }

    @Test
    fun markerStartsOnTheFinishedLessonForAForwardHop() {
        assertEquals(2, PathFocus.markerStartIndex(fromIndex = 2, headIndex = 3))
        // Free order can also skip signs forwards; still a forward hop.
        assertEquals(1, PathFocus.markerStartIndex(fromIndex = 1, headIndex = 4))
    }

    @Test
    fun markerSkipsTheHopAfterAFreeOrderDetourAhead() {
        // l4 was finished out of order while the highlight stayed on l1: hopping
        // from 4 back to 1 would warm the whole trail on the first frame and walk
        // the marker visibly backwards — progress being taken away. The marker
        // starts directly on the head instead.
        assertEquals(1, PathFocus.markerStartIndex(fromIndex = 4, headIndex = 1))
    }

    @Test
    fun markerStartsOnTheHeadWithoutAUsableFromLesson() {
        assertEquals(3, PathFocus.markerStartIndex(fromIndex = null, headIndex = 3))
        assertEquals(3, PathFocus.markerStartIndex(fromIndex = 3, headIndex = 3))
        // Untouched path: no head, nothing to hop to.
        assertEquals(-1, PathFocus.markerStartIndex(fromIndex = 2, headIndex = -1))
    }

    // ---- entryScrollTarget ----
    //
    // All in dp-as-px (density 1). The hop headroom is a sign's full height plus
    // the marker column above it, and the from-node sits one default spacing
    // (168dp) above the head node — the geometry the finding was verified against.

    private val hopHeadroom =
        (PathSignDimens.TotalHeight + PathMarkerDimens.Headroom).value // 166dp
    private val fromY = 1000f
    private val headY = fromY + PathGeometry.DefaultSpacing // 1168

    @Test
    fun entryScrollShowsTheHopStartOnSmallViewports() {
        // 470dp (a 640dp phone minus chrome) and 640dp: the plain head target
        // would put the hop's start above the top edge — off screen entirely
        // below ~690dp — so the entry target must pull back up.
        for (viewport in listOf(470, 640)) {
            val headTarget = PathFocus.scrollTarget(headY, viewport, maxScroll = 5000)
            val entry = PathFocus.entryScrollTarget(fromY, headY, hopHeadroom, viewport, maxScroll = 5000)
            val hopStartTop = fromY - hopHeadroom
            assertTrue(
                "plain target $headTarget must cut off the hop start at $hopStartTop (viewport $viewport)",
                hopStartTop - headTarget < 0f,
            )
            assertTrue(
                "hop start at ${hopStartTop - entry} must be on screen (viewport $viewport)",
                hopStartTop - entry >= 0f,
            )
            assertTrue(
                "head node at ${headY - entry} must stay inside the $viewport viewport",
                headY - entry <= viewport,
            )
        }
    }

    @Test
    fun entryScrollIsThePlainTargetOnceTheHopStartFitsAnyway() {
        // At 800dp the anchor leaves enough room above the head for the whole
        // hop; the entry target degenerates to the plain one and the follow-up
        // animation is a no-op.
        val viewport = 800
        val headTarget = PathFocus.scrollTarget(headY, viewport, maxScroll = 5000)
        val entry = PathFocus.entryScrollTarget(fromY, headY, hopHeadroom, viewport, maxScroll = 5000)
        assertEquals(headTarget, entry)
        assertTrue("hop start must be on screen", fromY - hopHeadroom - entry >= 0f)
    }

    @Test
    fun entryScrollStaysInsideTheScrollableRange() {
        // Hop start so close to the top that its headroom pokes past the content
        // edge: clamps at 0 instead of scrolling to a negative offset.
        assertEquals(
            0,
            PathFocus.entryScrollTarget(100f, 268f, hopHeadroom, 640, maxScroll = 5000),
        )
        // A path shorter than the viewport does not scroll at all.
        assertEquals(0, PathFocus.entryScrollTarget(fromY, headY, hopHeadroom, 640, maxScroll = 0))
    }
}
