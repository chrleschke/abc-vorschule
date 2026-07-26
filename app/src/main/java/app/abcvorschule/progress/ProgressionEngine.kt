package app.abcvorschule.progress

import app.abcvorschule.content.CountAddRound

object ProgressionEngine {
    const val UpThreshold = 3
    const val DownThreshold = 3

    fun scaffoldForAtom(progress: LearnerProgress, atomId: String): ScaffoldLevel =
        scaffoldFor(progress.parentMode, progress.atomStats[atomId])

    fun scaffoldForMath(progress: LearnerProgress, mathKey: String): ScaffoldLevel =
        scaffoldFor(progress.parentMode, progress.mathStats[mathKey])

    private fun scaffoldFor(mode: ParentMode, stats: SkillStats?): ScaffoldLevel =
        when (mode) {
            ParentMode.Beginner -> ScaffoldLevel.Beginner
            ParentMode.Advanced -> ScaffoldLevel.Advanced
            ParentMode.Auto -> stats?.autoScaffold ?: ScaffoldLevel.Beginner
        }

    fun mathKey(operation: String, left: Int, right: Int, band: String?): String {
        val bandPart = band ?: bandFor(left + right)
        return "$operation|$bandPart|$left+$right"
    }

    fun mathKey(round: CountAddRound): String = mathKey(
        operation = round.operation,
        left = round.left,
        right = round.right,
        band = round.difficultyBand,
    )

    fun bandFor(sum: Int): String = when {
        sum <= 5 -> "easy"
        sum <= 10 -> "medium"
        else -> "hard"
    }

    fun recordAtomAttempt(
        progress: LearnerProgress,
        atomId: String,
        outcome: AttemptOutcome,
    ): LearnerProgress {
        val updated = apply(progress.atomStats[atomId] ?: SkillStats(), outcome, progress.parentMode)
        return progress.copy(atomStats = progress.atomStats + (atomId to updated))
    }

    fun recordMathAttempt(
        progress: LearnerProgress,
        mathKey: String,
        outcome: AttemptOutcome,
    ): LearnerProgress {
        val updated = apply(progress.mathStats[mathKey] ?: SkillStats(), outcome, progress.parentMode)
        return progress.copy(mathStats = progress.mathStats + (mathKey to updated))
    }

    /** Trainer-level tally; feeds LessonGating, never scaffold selection. */
    fun recordTaskAttempt(
        progress: LearnerProgress,
        taskId: String,
        outcome: AttemptOutcome,
    ): LearnerProgress {
        val updated = apply(progress.taskStats[taskId] ?: SkillStats(), outcome, progress.parentMode)
        return progress.copy(taskStats = progress.taskStats + (taskId to updated))
    }

    fun awardPoints(progress: LearnerProgress, amount: Int): LearnerProgress =
        progress.copy(points = progress.points + amount.coerceAtLeast(0))

    fun masteryScore(attempts: Int, correct: Int): Double {
        if (attempts == 0) return 0.0
        return correct.toDouble() / attempts.toDouble()
    }

    fun masteryScore(stats: SkillStats): Double = masteryScore(stats.attempts, stats.correct)

    private fun apply(
        stats: SkillStats,
        outcome: AttemptOutcome,
        mode: ParentMode,
    ): SkillStats {
        // Forced parent mode freezes Auto streaks (AE3/AE8).
        if (mode != ParentMode.Auto) {
            return when (outcome) {
                AttemptOutcome.Correct -> stats.copy(
                    attempts = stats.attempts + 1,
                    correct = stats.correct + 1,
                )
                AttemptOutcome.Miss -> stats.copy(attempts = stats.attempts + 1)
                AttemptOutcome.Resolve -> stats.copy(
                    attempts = stats.attempts + 1,
                    resolves = stats.resolves + 1,
                )
            }
        }
        val base = when (outcome) {
            AttemptOutcome.Correct -> stats.copy(
                attempts = stats.attempts + 1,
                correct = stats.correct + 1,
                consecutiveCorrect = stats.consecutiveCorrect + 1,
                consecutiveMiss = 0,
            )
            AttemptOutcome.Miss -> stats.copy(
                attempts = stats.attempts + 1,
                consecutiveCorrect = 0,
                consecutiveMiss = stats.consecutiveMiss + 1,
            )
            AttemptOutcome.Resolve -> stats.copy(
                attempts = stats.attempts + 1,
                resolves = stats.resolves + 1,
                consecutiveCorrect = 0,
                consecutiveMiss = stats.consecutiveMiss + 1,
            )
        }
        return base.copy(
            autoScaffold = nextScaffold(base.autoScaffold, base.consecutiveCorrect, base.consecutiveMiss),
        )
    }

    private fun nextScaffold(
        current: ScaffoldLevel,
        consecutiveCorrect: Int,
        consecutiveMiss: Int,
    ): ScaffoldLevel {
        return when {
            consecutiveCorrect >= UpThreshold -> ScaffoldLevel.Advanced
            consecutiveMiss >= DownThreshold -> ScaffoldLevel.Beginner
            else -> current
        }
    }
}
