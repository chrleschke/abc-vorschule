package app.abcvorschule.session

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.roundCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonSessionTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun lessonSessionIsTheSixTrainersInAuthoredOrder() {
        val lesson = pack.authoredLessons.first()
        assertEquals(lesson.taskIds, pack.tasksOf(lesson).map { it.id })
        assertEquals(6, pack.tasksOf(lesson).size)
    }

    @Test
    fun progressionWalksEveryRoundOfEveryTrainerInOrder() {
        val counts = listOf(3, 2, 1, 1, 1, 2)
        val visited = mutableListOf<Pair<Int, Int>>()
        var step: SessionStep? = SessionStep(0, 0)
        while (step != null) {
            visited += step.trainerIndex to step.roundIndex
            step = SessionProgression.next(step.trainerIndex, step.roundIndex, counts)
        }
        assertEquals(counts.sum(), visited.size)
        assertEquals(0 to 0, visited.first())
        assertEquals(0 to 1, visited[1])
        assertEquals(0 to 2, visited[2])
        assertEquals(1 to 0, visited[3])
        assertEquals(5 to 1, visited.last())
    }

    @Test
    fun progressionEndsAfterTheLastRoundOfTheLastTrainer() {
        assertNull(SessionProgression.next(5, 1, listOf(1, 1, 1, 1, 1, 2)))
    }

    @Test
    fun progressionSkipsEmptyTrainers() {
        // Defensive: a trainer with zero rounds must not stall the session.
        assertEquals(SessionStep(2, 0), SessionProgression.next(0, 0, listOf(1, 0, 1)))
    }

    @Test
    fun roundCountsMatchTheAuthoredPack() {
        val counts = pack.tasksOf(pack.authoredLessons.first()).map { it.roundCount }
        assertEquals(6, counts.size)
        assertEquals(emptyList<Int>(), counts.filter { it <= 0 })
    }
}
