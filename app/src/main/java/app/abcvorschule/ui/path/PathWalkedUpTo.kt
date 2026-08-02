package app.abcvorschule.ui.path

import app.abcvorschule.content.Lesson
import app.abcvorschule.progress.LessonState

/**
 * Index of the last lesson the child has actually reached — Mastered or
 * InProgress — or -1 if none.
 *
 * Only the fallback for [PathFocus.headIndex] these days: what the marker stands on
 * and what the trail warms up to is the *highlighted* lesson, so that finishing a
 * lesson moves both to the next sign. This is what is left when there is no
 * highlight at all.
 *
 * Pulled out of the PathScreen composable so it is a pure, JVM-unit-testable
 * function rather than logic buried in a Composable body.
 *
 * Deliberately `indexOfLast`, not "the last lesson before the first gap": with the
 * parent's free-order switch a lesson far ahead can be Mastered while earlier ones
 * are untouched, and this would then report that far index. Harmless where it is
 * used — a path with no highlighted lesson has no ordinary progress left to
 * misrepresent — but not a general-purpose "how far did the child get".
 */
internal fun walkedUpToIndex(lessons: List<Lesson>, states: Map<String, LessonState>): Int =
    lessons.indexOfLast {
        val state = states[it.id]
        state == LessonState.Mastered || state == LessonState.InProgress
    }
