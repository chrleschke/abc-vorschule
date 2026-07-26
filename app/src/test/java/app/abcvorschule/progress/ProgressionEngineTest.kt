package app.abcvorschule.progress

import app.abcvorschule.content.CountAddRound
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionEngineTest {
    private fun repeatOutcome(
        start: LearnerProgress,
        atomId: String,
        outcome: AttemptOutcome,
        times: Int,
    ): LearnerProgress {
        var progress = start
        repeat(times) { progress = ProgressionEngine.recordAtomAttempt(progress, atomId, outcome) }
        return progress
    }

    @Test
    fun autoUpgradesOneAtomWithoutTouchingAnother() {
        // AE2 equivalent: per-atom scaffolds, not one global flag.
        val progress = repeatOutcome(LearnerProgress(), "haus", AttemptOutcome.Correct, 3)
        assertEquals(ScaffoldLevel.Advanced, ProgressionEngine.scaffoldForAtom(progress, "haus"))
        assertEquals(ScaffoldLevel.Beginner, ProgressionEngine.scaffoldForAtom(progress, "mama"))
    }

    @Test
    fun firstTimeAutoDefaultsToBeginner() {
        assertEquals(
            ScaffoldLevel.Beginner,
            ProgressionEngine.scaffoldForAtom(LearnerProgress(), "unseen"),
        )
    }

    @Test
    fun forcedParentModeFreezesAutoStreaks() {
        // AE3: forced Advanced ignores a miss streak until Auto returns.
        val forced = LearnerProgress(parentMode = ParentMode.Advanced)
        val missed = repeatOutcome(forced, "haus", AttemptOutcome.Miss, 5)
        assertEquals(ScaffoldLevel.Advanced, ProgressionEngine.scaffoldForAtom(missed, "haus"))
        assertEquals(0, missed.atomStats.getValue("haus").consecutiveMiss)

        // AE8: back on Auto, progression resumes from the stored stats.
        val backOnAuto = missed.copy(parentMode = ParentMode.Auto)
        assertEquals(ScaffoldLevel.Beginner, ProgressionEngine.scaffoldForAtom(backOnAuto, "haus"))
    }

    @Test
    fun resolveCountsTowardDownshiftButNeverTowardMastery() {
        // AE5: resolve is a miss for the streak, never a success.
        val advanced = repeatOutcome(LearnerProgress(), "haus", AttemptOutcome.Correct, 3)
        val resolved = repeatOutcome(advanced, "haus", AttemptOutcome.Resolve, 3)
        val stats = resolved.atomStats.getValue("haus")
        assertEquals(3, stats.correct)
        assertEquals(3, stats.resolves)
        assertEquals(0, stats.consecutiveCorrect)
        assertEquals(ScaffoldLevel.Beginner, ProgressionEngine.scaffoldForAtom(resolved, "haus"))
    }

    @Test
    fun taskStatsTrackTrainerCompletionSeparatelyFromAtoms() {
        val progress = ProgressionEngine.recordTaskAttempt(
            LearnerProgress(),
            "l01-t4",
            AttemptOutcome.Correct,
        )
        assertEquals(1, progress.taskStats.getValue("l01-t4").correct)
        assertEquals(emptyMap<String, SkillStats>(), progress.atomStats)
    }

    @Test
    fun mathKeyIsDerivedFromOperationBandAndOperands() {
        val round = CountAddRound(
            promptTts = "x",
            iconAtomId = "ameise",
            left = 2,
            right = 1,
            answer = 3,
        )
        assertEquals("add|easy|2+1", ProgressionEngine.mathKey(round))
        assertEquals(
            "add|hard|9+8",
            ProgressionEngine.mathKey(round.copy(left = 9, right = 8, answer = 17)),
        )
        assertEquals(
            "add|custom|2+1",
            ProgressionEngine.mathKey(round.copy(difficultyBand = "custom")),
        )
    }

    @Test
    fun pointsNeverGoNegative() {
        assertEquals(0, ProgressionEngine.awardPoints(LearnerProgress(), -5).points)
    }
}
