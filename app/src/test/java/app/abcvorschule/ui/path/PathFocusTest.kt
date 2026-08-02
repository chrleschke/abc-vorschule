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
}
