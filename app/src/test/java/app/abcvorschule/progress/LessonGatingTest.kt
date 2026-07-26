package app.abcvorschule.progress

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.LessonStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonGatingTest {
    private val pack: ContentPack = ContentRepository.fromClasspath().load()
    private val first = pack.authoredLessons.first()

    private fun mastering(lessonId: String): LearnerProgress {
        val lesson = pack.lesson(lessonId)
        return LearnerProgress(
            taskStats = lesson.taskIds.associateWith { SkillStats(attempts = 1, correct = 1) },
        )
    }

    @Test
    fun firstLessonIsAvailableOnAFreshInstall() {
        assertEquals(
            LessonState.Available,
            LessonGating.stateOf(pack, LearnerProgress(), first.id),
        )
    }

    @Test
    fun plannedLessonsReportPlannedRegardlessOfProgress() {
        val planned = pack.lessons.first { it.status == LessonStatus.planned }
        assertEquals(
            LessonState.Planned,
            LessonGating.stateOf(pack, mastering(first.id), planned.id),
        )
        assertFalse(LessonGating.isPlayable(LessonState.Planned))
    }

    @Test
    fun touchedButUnfinishedLessonIsInProgress() {
        val progress = LearnerProgress(
            taskStats = mapOf(first.taskIds.first() to SkillStats(attempts = 2, correct = 0)),
        )
        assertEquals(LessonState.InProgress, LessonGating.stateOf(pack, progress, first.id))
    }

    @Test
    fun lessonIsMasteredOnlyWhenEveryTrainerWasSolvedOnce() {
        val partial = LearnerProgress(
            taskStats = first.taskIds.dropLast(1)
                .associateWith { SkillStats(attempts = 1, correct = 1) },
        )
        assertEquals(LessonState.InProgress, LessonGating.stateOf(pack, partial, first.id))
        assertEquals(LessonState.Mastered, LessonGating.stateOf(pack, mastering(first.id), first.id))
    }

    @Test
    fun nextAuthoredLessonUnlocksOnlyAfterThePreviousIsMastered() {
        val second = pack.authoredLessons.getOrNull(1) ?: return
        assertEquals(
            LessonState.Locked,
            LessonGating.stateOf(pack, LearnerProgress(), second.id),
        )
        assertEquals(
            LessonState.Available,
            LessonGating.stateOf(pack, mastering(first.id), second.id),
        )
    }

    @Test
    fun masteredLessonStaysPlayableForReview() {
        assertTrue(LessonGating.isPlayable(LessonState.Mastered))
        assertTrue(LessonGating.isPlayable(LessonState.Available))
        assertTrue(LessonGating.isPlayable(LessonState.InProgress))
        assertFalse(LessonGating.isPlayable(LessonState.Locked))
    }

    @Test
    fun nextPlayableIsTheFirstUnmasteredAuthoredLesson() {
        assertEquals(first.id, LessonGating.nextPlayable(pack, LearnerProgress())?.id)
        val second = pack.authoredLessons.getOrNull(1)
        if (second != null) {
            assertEquals(second.id, LessonGating.nextPlayable(pack, mastering(first.id))?.id)
        }
    }

    @Test
    fun statesCoverEveryLesson() {
        val states = LessonGating.states(pack, LearnerProgress())
        assertEquals(pack.lessons.size, states.size)
    }
}
