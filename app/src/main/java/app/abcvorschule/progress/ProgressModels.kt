package app.abcvorschule.progress

import kotlinx.serialization.Serializable

@Serializable
enum class ParentMode {
    Auto,
    Beginner,
    Advanced,
}

@Serializable
enum class ScaffoldLevel {
    Beginner,
    Advanced,
}

@Serializable
data class AtomStats(
    val attempts: Int = 0,
    val correct: Int = 0,
    val resolves: Int = 0,
    val consecutiveCorrect: Int = 0,
    val consecutiveMiss: Int = 0,
    val autoScaffold: ScaffoldLevel = ScaffoldLevel.Beginner,
)

@Serializable
data class MathStats(
    val attempts: Int = 0,
    val correct: Int = 0,
    val resolves: Int = 0,
    val consecutiveCorrect: Int = 0,
    val consecutiveMiss: Int = 0,
    val autoScaffold: ScaffoldLevel = ScaffoldLevel.Beginner,
)

@Serializable
data class SessionSnapshot(
    val taskIds: List<String> = emptyList(),
    val index: Int = 0,
    val pointsEarned: Int = 0,
    val packId: String = "",
)

@Serializable
data class LearnerProgress(
    val parentMode: ParentMode = ParentMode.Auto,
    val points: Int = 0,
    val atomStats: Map<String, AtomStats> = emptyMap(),
    val mathStats: Map<String, MathStats> = emptyMap(),
    val unfinishedSession: SessionSnapshot? = null,
    val packIntroCompleted: Boolean = false,
)

sealed interface AttemptOutcome {
    data object Correct : AttemptOutcome
    data object Miss : AttemptOutcome
    data object Resolve : AttemptOutcome
}
