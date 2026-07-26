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

/** Attempt statistics for one learnable unit (atom or math fact). */
@Serializable
data class SkillStats(
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
    val atomStats: Map<String, SkillStats> = emptyMap(),
    val mathStats: Map<String, SkillStats> = emptyMap(),
    val unfinishedSession: SessionSnapshot? = null,
    val packIntroCompleted: Boolean = false,
)

sealed interface AttemptOutcome {
    data object Correct : AttemptOutcome
    data object Miss : AttemptOutcome
    data object Resolve : AttemptOutcome
}
