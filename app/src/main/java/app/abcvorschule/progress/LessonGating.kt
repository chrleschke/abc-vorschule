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
 * An authored lesson opens once the previous authored lesson is **completed**
 * ([isCompleted]) — durchgespielt, nicht zwingend fehlerfrei. [LessonState.Mastered]
 * ist davon getrennt: es sagt „alles allein gelöst" und färbt das Schild, hält den
 * Pfad aber nicht mehr auf.
 */
object LessonGating {
    /**
     * [unlockAll] is the parent override: it lifts the progress lock, not the absence of
     * content — a [LessonState.Planned] lesson has no taskIds and would open an empty
     * trainer list, so it stays unplayable.
     */
    fun isPlayable(state: LessonState, unlockAll: Boolean = false): Boolean =
        state == LessonState.Available || state == LessonState.InProgress ||
            state == LessonState.Mastered || (unlockAll && state == LessonState.Locked)

    /**
     * Gates on [ContentPack.playableTasksOf], not the lesson's raw taskIds — a
     * paused trainer (see [app.abcvorschule.content.PausedTrainerKinds]) must never
     * block mastery, since the child can no longer play it to earn a correct.
     */
    fun isMastered(pack: ContentPack, lesson: Lesson, progress: LearnerProgress): Boolean {
        val playableIds = pack.playableTasksOf(lesson).map { it.id }
        return playableIds.isNotEmpty() &&
            playableIds.all { (progress.taskStats[it]?.correct ?: 0) > 0 }
    }

    /**
     * Durchgespielt: jeder spielbare Trainer wurde zu Ende gebracht — richtig gelöst
     * **oder** über „Zeig mir" aufgelöst.
     *
     * Das ist die Bedingung, an der die Kette hängt, [isMastered] ist es
     * ausdrücklich nicht. 183 der 226 spielbaren Trainer im ausgelieferten Pack
     * haben genau eine Runde; ein einziges „Zeig mir" ließ dort `correct` für immer
     * auf 0 und sperrte die Folgelektion, obwohl das Kind die Lektion samt Feier zu
     * Ende gespielt hat — eine unsichtbare Strafe genau für das Kind, das die Hilfe
     * gebraucht hat, und ohne jeden Hinweis, wie sie aufzuheben wäre (PRODUCT_PRINCIPLES §8:
     * keine Strafen).
     *
     * Der Unterschied bleibt trotzdem sichtbar: das Schild wird erst
     * [LessonState.Mastered], wenn jeder Trainer wirklich allein gelöst wurde —
     * aufgelöst heißt weiterhin [LessonState.InProgress].
     */
    fun isCompleted(pack: ContentPack, lesson: Lesson, progress: LearnerProgress): Boolean {
        val playableIds = pack.playableTasksOf(lesson).map { it.id }
        return playableIds.isNotEmpty() &&
            playableIds.all {
                val stats = progress.taskStats[it]
                ((stats?.correct ?: 0) + (stats?.resolves ?: 0)) > 0
            }
    }

    fun isTouched(pack: ContentPack, lesson: Lesson, progress: LearnerProgress): Boolean =
        pack.playableTasksOf(lesson).map { it.id }.any { (progress.taskStats[it]?.attempts ?: 0) > 0 }

    fun stateOf(pack: ContentPack, progress: LearnerProgress, lessonId: String): LessonState =
        states(pack, progress).getValue(lessonId)

    /**
     * Deliberately checks the lesson's own progress *before* the order lock: with the
     * parent's "Reihenfolge frei wählbar" the child can play a lesson whose predecessor
     * is untouched, and what it did there is the stronger fact. Locked first meant a
     * finished lesson kept reporting [LessonState.Locked] — a dimmed sign with faint
     * silhouettes that never acknowledged the completion.
     *
     * [LessonState.Locked] therefore means "not reached in the Fibel order *and*
     * nothing done here yet". Two consequences, both intended:
     * - mastering a lesson out of order unlocks the one after it, since the chain
     *   below reads the same state;
     * - a lesson touched out of order stays playable after the parent switch goes
     *   off again. The child has already seen it; re-locking it would read as the
     *   app taking something away.
     */
    fun states(pack: ContentPack, progress: LearnerProgress): Map<String, LessonState> {
        var previousCompleted = true
        return pack.lessons.associate { lesson ->
            val completed = isCompleted(pack, lesson, progress)
            val state = when {
                lesson.status == LessonStatus.planned -> LessonState.Planned
                isMastered(pack, lesson, progress) -> LessonState.Mastered
                isTouched(pack, lesson, progress) -> LessonState.InProgress
                !previousCompleted -> LessonState.Locked
                else -> LessonState.Available
            }
            if (lesson.status == LessonStatus.authored) {
                // Eine autorierte Lektion ohne einen einzigen spielbaren Trainer
                // (alle Kinds pausiert) reicht den Stand ihres Vorgängers durch,
                // statt die Kette zu kappen: sie ist nicht zu schaffen, also darf
                // sie auch nichts blockieren — sonst bliebe der ganze restliche
                // Pfad für immer zu.
                if (pack.playableTasksOf(lesson).isNotEmpty()) {
                    previousCompleted = completed
                }
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
