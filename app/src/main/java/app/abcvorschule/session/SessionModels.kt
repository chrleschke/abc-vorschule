package app.abcvorschule.session

import app.abcvorschule.content.TaskSpec
import app.abcvorschule.content.TrainerRound
import app.abcvorschule.content.round
import app.abcvorschule.content.rounds
import app.abcvorschule.progress.ParentMode
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.exercise.MathInputMode

sealed interface AppScreen {
    /** Fibel path — the app's entry screen. */
    data object Path : AppScreen
    data object Practice : AppScreen
    data object RewardSummary : AppScreen
}

enum class SuccessPhase {
    Idle,
    SpeakAnswer,
    ShowBurst,
    /** Resolve was tapped: briefly reveal the answer, award nothing, then advance. */
    RevealAnswer,
}

/** Position inside a lesson session: which trainer, which round. */
data class SessionStep(val trainerIndex: Int, val roundIndex: Int)

/** Pure walk through a lesson's trainers and their rounds. */
object SessionProgression {
    fun next(trainerIndex: Int, roundIndex: Int, roundCounts: List<Int>): SessionStep? {
        val current = roundCounts.getOrNull(trainerIndex) ?: return null
        if (roundIndex + 1 < current) return SessionStep(trainerIndex, roundIndex + 1)
        var t = trainerIndex + 1
        while (t < roundCounts.size) {
            if (roundCounts[t] > 0) return SessionStep(t, 0)
            t++
        }
        return null
    }

    fun previous(trainerIndex: Int, roundIndex: Int, roundCounts: List<Int>): SessionStep? {
        if (roundIndex > 0) return SessionStep(trainerIndex, roundIndex - 1)
        var t = trainerIndex - 1
        while (t >= 0) {
            if (roundCounts[t] > 0) return SessionStep(t, roundCounts[t] - 1)
            t--
        }
        return null
    }

    /**
     * Guards against resuming into a shifted position when the scheduled trainer
     * list's shape changed since the snapshot was saved, even though the content
     * pack's id did not (e.g. this app version starts inserting synthetic hunt
     * trainers a previous version didn't). [expectedCount] is the trainer count
     * recorded at snapshot time; null means "no resume in progress, don't shape-check"
     * (a fresh lesson open always starts at trainer 0 anyway).
     */
    fun resumeSafe(
        expectedCount: Int?,
        actualCount: Int,
        trainerIndex: Int,
        roundIndex: Int,
    ): SessionStep {
        if (expectedCount != null && expectedCount != actualCount) return SessionStep(0, 0)
        val safeTrainer = trainerIndex.coerceIn(0, (actualCount - 1).coerceAtLeast(0))
        return SessionStep(safeTrainer, roundIndex.coerceAtLeast(0))
    }
}

data class ScheduledTrainer(
    val spec: TaskSpec,
    /** Per-atom scaffold for slot-based trainers (word_build, sentence_order). */
    val scaffolds: Map<String, ScaffoldLevel> = emptyMap(),
    /**
     * Rechnen scaffold per arithmetic fact, keyed by [ProgressionEngine.mathKey].
     * A count_add trainer holds several facts with independent mastery, so one
     * scaffold for the whole trainer would drive the wrong UI for later rounds.
     * Cached at schedule time: [SessionViewModel.advance] re-schedules the
     * trainer on every round transition, so a mid-round parent-mode change
     * takes effect from the very next round on — even within the same
     * trainer, not only once the next trainer starts (F7).
     */
    val mathInputs: Map<String, MathInputMode> = emptyMap(),
)

data class SessionUiState(
    val screen: AppScreen = AppScreen.Path,
    val lessonId: String? = null,
    val trainers: List<ScheduledTrainer> = emptyList(),
    val trainerIndex: Int = 0,
    val roundIndex: Int = 0,
    val points: Int = 0,
    val sessionPoints: Int = 0,
    /**
     * Das Finale der abgeschlossenen Lektion — gesetzt **nur** beim echten Abschluss.
     * Bleibt es null, greift die schlanke End-Screen-Variante — ein Defensivpfad für
     * nicht auflösbare Finales; Abbrüche gehen direkt zum Pfad und erreichen den
     * End-Screen nie (PRODUCT_PRINCIPLES.md Abschnitt 5).
     */
    val completedFinaleId: String? = null,
    val ready: Boolean = false,
    val showDifficultySheet: Boolean = false,
    /**
     * Mirrored out of `LearnerProgress` the way [points] already is (spec 5.1): the
     * parent sheet now stays open after a selection, so it has to observe the state.
     * Reading the repository through a viewModel getter only ever recomposed because
     * the setter touched `_ui` in passing — and on the Path, where [trainers] is
     * empty, that `copy` yields an equal state that `MutableStateFlow` conflates away.
     */
    val parentMode: ParentMode = ParentMode.Auto,
    val unlockAllLessons: Boolean = false,
    /**
     * Die Lektion, aus der das Kind gerade zum Pfad zurückgekommen ist — Startpunkt
     * der „Du bist hier“-Animation auf dem Pfad. Muss durch den State laufen und
     * nicht im Pfad-Screen bleiben: der Screen wird beim Verlassen des Pfades
     * verworfen, also ist das hier das Einzige, was „woher“ über die Lektion hinweg
     * trägt. Der Pfad meldet mit `onPathAdvanceAnimated()` zurück, sobald der Marker
     * seine Startposition hat — sonst würde der Hüpfer bei jeder Rückkehr erneut
     * laufen.
     */
    val pathAdvanceFromLessonId: String? = null,
    /** Spoken-only miss/hint text — never rendered as chrome. */
    val speakCue: String? = null,
    val successPhase: SuccessPhase = SuccessPhase.Idle,
    /** Ordered success speech — praise first, answer last for Rechnen (§7: die Menge bleibt das Letzte). */
    val successSpeakParts: List<String> = emptyList(),
    val error: String? = null,
) {
    val current: ScheduledTrainer? = trainers.getOrNull(trainerIndex)
    val currentRound: TrainerRound? = current?.spec?.round(roundIndex)
    private val roundCounts: List<Int> = trainers.map { it.spec.rounds.size }

    /** Rounds inside the current trainer; füllt das laufende Segment der Fortschrittskette. */
    val roundCount: Int = roundCounts.getOrElse(trainerIndex) { 0 }

    val canGoPrevious: Boolean =
        SessionProgression.previous(trainerIndex, roundIndex, roundCounts) != null
    val canGoNext: Boolean =
        SessionProgression.next(trainerIndex, roundIndex, roundCounts) != null
}
