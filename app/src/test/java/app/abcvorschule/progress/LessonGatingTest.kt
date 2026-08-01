package app.abcvorschule.progress

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.Lesson
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
        // The shipped pack has zero planned lessons at the moment (all 26 are
        // authored) — this business rule still needs coverage independent of the
        // current curriculum state, so it builds its own synthetic planned lesson
        // rather than relying on one existing in the real pack.
        val planned = Lesson(
            id = "l-synthetic-planned",
            index = pack.lessons.size + 1,
            phase = 5,
            title = "Synthetic Planned",
            nodeLabel = "?",
            status = LessonStatus.planned,
        )
        val syntheticPack = pack.copy(lessons = pack.lessons + planned)
        assertEquals(
            LessonState.Planned,
            LessonGating.stateOf(syntheticPack, mastering(first.id), planned.id),
        )
        assertFalse(LessonGating.isPlayable(LessonState.Planned))
    }

    @Test
    fun touchedButUnfinishedLessonIsInProgress() {
        val progress = LearnerProgress(
            taskStats = mapOf(
                pack.playableTasksOf(first).first().id to SkillStats(attempts = 2, correct = 0),
            ),
        )
        assertEquals(LessonState.InProgress, LessonGating.stateOf(pack, progress, first.id))
    }

    @Test
    fun touchingOnlyAPausedTrainerDoesNotUnlockProgress() {
        // sound_position (Trainer 1) is paused — attempts on it must not count
        // toward touched/mastered, since the child can no longer play it.
        val pausedTaskId = pack.tasksOf(first).first { it !in pack.playableTasksOf(first) }.id
        val progress = LearnerProgress(
            taskStats = mapOf(pausedTaskId to SkillStats(attempts = 3, correct = 1)),
        )
        assertEquals(LessonState.Available, LessonGating.stateOf(pack, progress, first.id))
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
    fun unlockAllOpensLockedLessonsButNeverPlannedOnes() {
        assertTrue(LessonGating.isPlayable(LessonState.Locked, unlockAll = true))
        // A planned lesson has no taskIds, so opening it would run into an empty
        // trainer list — the parent switch lifts the progress gate, not missing content.
        assertFalse(LessonGating.isPlayable(LessonState.Planned, unlockAll = true))
        assertFalse(LessonGating.isPlayable(LessonState.Locked))
    }

    @Test
    fun statesKeepReportingLockedWhileUnlockAllIsSet() {
        // The dimmed sign look hangs off LessonState, so the override must not
        // rewrite Locked into Available — only the padlock disappears.
        val second = pack.authoredLessons.getOrNull(1) ?: return
        val states = LessonGating.states(pack, LearnerProgress(unlockAllLessons = true))
        assertEquals(LessonState.Locked, states[second.id])
    }

    @Test
    fun nextPlayableIgnoresUnlockAll() {
        // The pulsing sign stays the "you are here" anchor: it follows real progress,
        // not the override.
        val progress = mastering(first.id)
        assertEquals(
            LessonGating.nextPlayable(pack, progress)?.id,
            LessonGating.nextPlayable(pack, progress.copy(unlockAllLessons = true))?.id,
        )
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
