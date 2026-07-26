package app.abcvorschule.session

import app.abcvorschule.content.Domain
import app.abcvorschule.content.TaskTemplate
import app.abcvorschule.progress.ScaffoldLevel

sealed interface AppScreen {
    data object Practice : AppScreen
    data object RewardSummary : AppScreen
    data object Pause : AppScreen
}

data class ScheduledTask(
    val template: TaskTemplate,
    val scaffolds: Map<String, ScaffoldLevel> = emptyMap(),
)

data class SessionUiState(
    val screen: AppScreen = AppScreen.Practice,
    val tasks: List<ScheduledTask> = emptyList(),
    val index: Int = 0,
    val points: Int = 0,
    val sessionPoints: Int = 0,
    val ready: Boolean = false,
    val showDifficultySheet: Boolean = false,
    val feedback: String? = null,
    val lastSuccess: Boolean = false,
    val speechUnlocked: Boolean = false,
    val error: String? = null,
    val packTitle: String = "",
) {
    val current: ScheduledTask? = tasks.getOrNull(index)
    val progressLabel: String = if (tasks.isEmpty()) "" else "${index + 1}/${tasks.size}"
    val domain: Domain? = current?.template?.domain
}
