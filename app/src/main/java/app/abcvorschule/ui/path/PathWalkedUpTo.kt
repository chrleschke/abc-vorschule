package app.abcvorschule.ui.path

import app.abcvorschule.content.Lesson
import app.abcvorschule.progress.LessonState

/**
 * Index of the last lesson the child has actually reached — Mastered or
 * InProgress — or -1 if none. [PathScreen] feeds this straight into
 * [PathTrail.dots]'s `walkedUpTo`, which draws everything up to and including
 * this index as walked.
 *
 * Pulled out of the PathScreen composable so it is a pure, JVM-unit-testable
 * function rather than logic buried in a Composable body.
 *
 * Deliberately `indexOfLast`, not "the last lesson before the first gap":
 * under today's strictly linear [app.abcvorschule.progress.LessonGating], a
 * lesson can only become Mastered/InProgress once every earlier lesson has
 * already been unlocked, so there is no gap between the start of the path and
 * the furthest-reached lesson. If gating ever stops being linear (e.g.
 * branching paths or skip-ahead), this would warm the whole trail up to the
 * furthest reached lesson even across an actual gap before it — that would
 * need revisiting then. It is a deliberate simplification given today's
 * gating rules, not an oversight.
 */
internal fun walkedUpToIndex(lessons: List<Lesson>, states: Map<String, LessonState>): Int =
    lessons.indexOfLast {
        val state = states[it.id]
        state == LessonState.Mastered || state == LessonState.InProgress
    }
