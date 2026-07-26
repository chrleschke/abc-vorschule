package app.abcvorschule.session

import app.abcvorschule.content.TaskSpec
import app.abcvorschule.content.TrainerRound
import app.abcvorschule.content.round
import app.abcvorschule.content.rounds
import app.abcvorschule.progress.ScaffoldLevel

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
}

data class ScheduledTrainer(
    val spec: TaskSpec,
    /** Per-atom scaffold for slot-based trainers (word_build, sentence_order). */
    val scaffolds: Map<String, ScaffoldLevel> = emptyMap(),
    /** Rechnen: Beginner = visual choices, Advanced = number entry. */
    val mathScaffold: ScaffoldLevel = ScaffoldLevel.Beginner,
)

data class SessionUiState(
    val screen: AppScreen = AppScreen.Path,
    val lessonId: String? = null,
    val trainers: List<ScheduledTrainer> = emptyList(),
    val trainerIndex: Int = 0,
    val roundIndex: Int = 0,
    val points: Int = 0,
    val sessionPoints: Int = 0,
    val ready: Boolean = false,
    val showDifficultySheet: Boolean = false,
    /** Spoken-only miss/hint text — never rendered as chrome. */
    val speakCue: String? = null,
    val successPhase: SuccessPhase = SuccessPhase.Idle,
    val successSpeakText: String? = null,
    val error: String? = null,
) {
    val current: ScheduledTrainer? = trainers.getOrNull(trainerIndex)
    val currentRound: TrainerRound? = current?.spec?.round(roundIndex)
    private val roundCounts: List<Int> = trainers.map { it.spec.rounds.size }

    /** "3/6" — which of the six trainers the child is on. */
    val trainerProgressLabel: String =
        if (trainers.isEmpty()) "" else "${trainerIndex + 1}/${trainers.size}"

    /** Rounds inside the current trainer, for the sub-progress dots. */
    val roundCount: Int = roundCounts.getOrElse(trainerIndex) { 0 }

    val canGoPrevious: Boolean =
        SessionProgression.previous(trainerIndex, roundIndex, roundCounts) != null
    val canGoNext: Boolean =
        SessionProgression.next(trainerIndex, roundIndex, roundCounts) != null
}
