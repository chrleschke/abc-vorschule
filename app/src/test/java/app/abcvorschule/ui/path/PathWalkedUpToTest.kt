package app.abcvorschule.ui.path

import app.abcvorschule.content.Lesson
import app.abcvorschule.content.LessonStatus
import app.abcvorschule.progress.LessonState
import org.junit.Assert.assertEquals
import org.junit.Test

class PathWalkedUpToTest {
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
    fun nothingReachedYieldsMinusOne() {
        val states = lessons.associate { it.id to LessonState.Locked }
        assertEquals(-1, walkedUpToIndex(lessons, states))
    }

    @Test
    fun missingStatesAreTreatedAsNotReached() {
        // A states map without an entry for every lesson id (e.g. right after a
        // fresh install) must not be misread as "reached".
        assertEquals(-1, walkedUpToIndex(lessons, emptyMap()))
    }

    @Test
    fun someMasteredOrInProgressYieldsIndexOfTheLastOne() {
        val states = mapOf(
            "l0" to LessonState.Mastered,
            "l1" to LessonState.InProgress,
            "l2" to LessonState.Available,
            "l3" to LessonState.Locked,
            "l4" to LessonState.Locked,
        )
        assertEquals(1, walkedUpToIndex(lessons, states))
    }

    @Test
    fun onlyInProgressStillCountsAsReached() {
        val states = mapOf(
            "l0" to LessonState.Available,
            "l1" to LessonState.InProgress,
            "l2" to LessonState.Locked,
            "l3" to LessonState.Locked,
            "l4" to LessonState.Locked,
        )
        assertEquals(1, walkedUpToIndex(lessons, states))
    }

    @Test
    fun everythingMasteredYieldsTheLastIndex() {
        val states = lessons.associate { it.id to LessonState.Mastered }
        assertEquals(lessons.lastIndex, walkedUpToIndex(lessons, states))
    }
}
