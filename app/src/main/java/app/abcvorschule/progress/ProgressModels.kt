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
    val lessonId: String = "",
    /** Index into the lesson's scheduled trainers (varies per lesson). */
    val trainerIndex: Int = 0,
    /** Index into the current trainer's rounds. */
    val roundIndex: Int = 0,
    val pointsEarned: Int = 0,
    val packId: String = "",
    /**
     * Trainer count at snapshot time. packId alone doesn't catch a *code* change
     * that reshapes the scheduled trainer list without touching content (e.g. this
     * feature inserting hunt steps) — a mismatch here means trainerIndex would
     * point at a different trainer than the one the child left off on, so the
     * lesson restarts from the top instead of resuming into a shifted position.
     * Defaults to 0, which never matches a real trainers.size, so pre-existing
     * stored snapshots safely fail the check the first time they're loaded after
     * this field was introduced.
     */
    val trainerCount: Int = 0,
)

@Serializable
data class LearnerProgress(
    val parentMode: ParentMode = ParentMode.Auto,
    val points: Int = 0,
    /** Per-atom stats drive per-slot scaffolds. */
    val atomStats: Map<String, SkillStats> = emptyMap(),
    /** Per-fact stats drive the Rechnen scaffold (visual choices vs. number entry). */
    val mathStats: Map<String, SkillStats> = emptyMap(),
    /** Per-trainer stats drive lesson state on the path. */
    val taskStats: Map<String, SkillStats> = emptyMap(),
    val unfinishedSession: SessionSnapshot? = null,
)

sealed interface AttemptOutcome {
    data object Correct : AttemptOutcome
    data object Miss : AttemptOutcome
    data object Resolve : AttemptOutcome
}
