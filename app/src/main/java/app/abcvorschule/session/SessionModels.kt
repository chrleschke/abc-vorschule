package app.abcvorschule.session

import app.abcvorschule.content.Domain
import app.abcvorschule.content.TaskTemplate
import app.abcvorschule.progress.ScaffoldLevel

sealed interface AppScreen {
    data object Practice : AppScreen
    data object RewardSummary : AppScreen
    data object Pause : AppScreen
}

enum class SuccessPhase {
    Idle,
    SpeakAnswer,
    ShowBurst,
}

data class ScheduledTask(
    val template: TaskTemplate,
    val scaffolds: Map<String, ScaffoldLevel> = emptyMap(),
    /** Known-atom distractor tiles mixed into the answer tray. */
    val distractors: List<DistractorTile> = emptyList(),
)

data class SessionUiState(
    val screen: AppScreen = AppScreen.Practice,
    val tasks: List<ScheduledTask> = emptyList(),
    val index: Int = 0,
    val points: Int = 0,
    val sessionPoints: Int = 0,
    val ready: Boolean = false,
    val showDifficultySheet: Boolean = false,
    /** Spoken-only miss/hint text — never shown as chrome. */
    val speakCue: String? = null,
    /** Correct-answer celebration pipeline: speak → star → advance. */
    val successPhase: SuccessPhase = SuccessPhase.Idle,
    val successSpeakText: String? = null,
    val error: String? = null,
) {
    val current: ScheduledTask? = tasks.getOrNull(index)
    val progressLabel: String = if (tasks.isEmpty()) "" else "${index + 1}/${tasks.size}"
    val domain: Domain? = current?.template?.domain
    /** Free navigation in both directions — never gated on scoring/attempts. */
    val canGoPrevious: Boolean = index > 0
    val canGoNext: Boolean = index < tasks.lastIndex.coerceAtLeast(0)
}
