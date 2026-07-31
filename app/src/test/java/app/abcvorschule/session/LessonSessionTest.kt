package app.abcvorschule.session

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.CountAddSpec
import app.abcvorschule.content.roundCount
import app.abcvorschule.progress.LearnerProgress
import app.abcvorschule.progress.ProgressionEngine
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.progress.SkillStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonSessionTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun lessonSessionIsItsAuthoredTasksInOrder() {
        val lesson = pack.authoredLessons.first()
        assertEquals(lesson.taskIds, pack.tasksOf(lesson).map { it.id })
        assertTrue(pack.tasksOf(lesson).isNotEmpty())
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
    fun resumeSafeRestartsWhenTrainerShapeChanged() {
        assertEquals(
            SessionStep(0, 0),
            SessionProgression.resumeSafe(expectedCount = 6, actualCount = 8, trainerIndex = 4, roundIndex = 1),
        )
    }

    @Test
    fun resumeSafeKeepsPositionWhenShapeMatches() {
        assertEquals(
            SessionStep(4, 1),
            SessionProgression.resumeSafe(expectedCount = 8, actualCount = 8, trainerIndex = 4, roundIndex = 1),
        )
    }

    @Test
    fun resumeSafeIgnoresShapeCheckWhenExpectedCountIsNull() {
        assertEquals(
            SessionStep(2, 0),
            SessionProgression.resumeSafe(expectedCount = null, actualCount = 8, trainerIndex = 2, roundIndex = 0),
        )
    }

    @Test
    fun resumeSafeClampsOutOfBoundsTrainerIndex() {
        assertEquals(
            SessionStep(7, 0),
            SessionProgression.resumeSafe(expectedCount = null, actualCount = 8, trainerIndex = 99, roundIndex = 0),
        )
    }

    @Test
    fun roundCountsMatchTheAuthoredPack() {
        val lesson = pack.authoredLessons.first()
        val counts = pack.tasksOf(lesson).map { it.roundCount }
        assertEquals(lesson.taskIds.size, counts.size)
        assertEquals(emptyList<Int>(), counts.filter { it <= 0 })
    }

    @Test
    fun mathScaffoldsAreIndependentPerFactAcrossACountAddTrainer() {
        // A lesson's count_add rounds carry several arithmetic facts (lesson 1 has
        // 1+1 and 2+1, split across two count_add tasks in the expanded pack); each
        // fact must carry its own scaffold instead of sharing a single one computed
        // from the first round. Flattened across every count_add task in the
        // lesson rather than just the first, since ProgressionEngine.scaffoldForMath
        // is a pure function over rounds regardless of which task they came from.
        val rounds = pack.tasksOf(pack.authoredLessons.first())
            .filterIsInstance<CountAddSpec>()
            .flatMap { it.rounds }
        assertTrue("need at least two count_add rounds to prove independence", rounds.size >= 2)
        val firstKey = ProgressionEngine.mathKey(rounds[0])
        val secondKey = ProgressionEngine.mathKey(rounds[1])
        assertNotEquals(firstKey, secondKey)

        val progress = LearnerProgress(
            mathStats = mapOf(secondKey to SkillStats(autoScaffold = ScaffoldLevel.Advanced)),
        )
        val mathScaffolds = rounds.associate { round ->
            val key = ProgressionEngine.mathKey(round)
            key to ProgressionEngine.scaffoldForMath(progress, key)
        }
        assertEquals(ScaffoldLevel.Beginner, mathScaffolds.getValue(firstKey))
        assertEquals(ScaffoldLevel.Advanced, mathScaffolds.getValue(secondKey))
    }

    @Test
    fun everyAuthoredLessonYieldsItsFinaleId() {
        pack.authoredLessons.forEach { lesson ->
            assertEquals(
                "lesson ${lesson.id}",
                lesson.finaleId,
                pack.finaleIdOf(lesson.id),
            )
        }
    }

    @Test
    fun finaleIdOfAnUnknownLessonIsNull() {
        // A stale resume snapshot must not crash the finish transition.
        assertNull(pack.finaleIdOf("l99"))
    }

    @Test
    fun repeatLessonYieldsTheFinaleOfItsBaseLesson() {
        assertEquals("f-l01", pack.finaleIdOf("l19"))
    }
}
