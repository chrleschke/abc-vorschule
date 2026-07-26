package app.abcvorschule.progress

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Lesson
import app.abcvorschule.content.LessonStatus

enum class LessonState {
    /** Declared in the Fibel order, content not authored yet. */
    Planned,

    /** Previous lesson not mastered yet. */
    Locked,

    /** Unlocked, never attempted. */
    Available,

    /** Attempted, at least one trainer still unsolved. */
    InProgress,

    /** Every trainer solved at least once. Stays replayable for review. */
    Mastered,
}

/**
 * Lesson unlocking derived entirely from stored task stats — no extra persistence.
 * An authored lesson opens once the previous authored lesson is mastered.
 */
object LessonGating {
    fun isPlayable(state: LessonState): Boolean =
        state == LessonState.Available || state == LessonState.InProgress ||
            state == LessonState.Mastered

    fun isMastered(lesson: Lesson, progress: LearnerProgress): Boolean =
        lesson.taskIds.isNotEmpty() &&
            lesson.taskIds.all { (progress.taskStats[it]?.correct ?: 0) > 0 }

    fun isTouched(lesson: Lesson, progress: LearnerProgress): Boolean =
        lesson.taskIds.any { (progress.taskStats[it]?.attempts ?: 0) > 0 }

    fun stateOf(pack: ContentPack, progress: LearnerProgress, lessonId: String): LessonState =
        states(pack, progress).getValue(lessonId)

    fun states(pack: ContentPack, progress: LearnerProgress): Map<String, LessonState> {
        var previousMastered = true
        return pack.lessons.associate { lesson ->
            val state = when {
                lesson.status == LessonStatus.planned -> LessonState.Planned
                !previousMastered -> LessonState.Locked
                isMastered(lesson, progress) -> LessonState.Mastered
                isTouched(lesson, progress) -> LessonState.InProgress
                else -> LessonState.Available
            }
            if (lesson.status == LessonStatus.authored) {
                previousMastered = state == LessonState.Mastered
            }
            lesson.id to state
        }
    }

    /** The lesson the path should highlight: first authored lesson not yet mastered. */
    fun nextPlayable(pack: ContentPack, progress: LearnerProgress): Lesson? {
        val states = states(pack, progress)
        return pack.authoredLessons.firstOrNull {
            states[it.id] == LessonState.Available || states[it.id] == LessonState.InProgress
        } ?: pack.authoredLessons.lastOrNull { isPlayable(states.getValue(it.id)) }
    }
}
