package app.abcvorschule.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionEngineTest {
    @Test
    fun autoUpgradesAtomAfterThreeCorrect() {
        var progress = LearnerProgress()
        repeat(3) {
            progress = ProgressionEngine.recordAtomAttempt(progress, "haus", AttemptOutcome.Correct)
        }
        assertEquals(ScaffoldLevel.Advanced, ProgressionEngine.scaffoldForAtom(progress, "haus"))
        assertEquals(ScaffoldLevel.Beginner, ProgressionEngine.scaffoldForAtom(progress, "mama"))
    }

    @Test
    fun parentAdvancedFreezesAutoDespiteMissStreak() {
        var progress = LearnerProgress(parentMode = ParentMode.Advanced)
        repeat(5) {
            progress = ProgressionEngine.recordAtomAttempt(progress, "haus", AttemptOutcome.Miss)
        }
        assertEquals(ScaffoldLevel.Advanced, ProgressionEngine.scaffoldForAtom(progress, "haus"))
    }

    @Test
    fun switchingBackToAutoUsesStoredStats() {
        var progress = LearnerProgress()
        repeat(3) {
            progress = ProgressionEngine.recordAtomAttempt(progress, "haus", AttemptOutcome.Correct)
        }
        progress = progress.copy(parentMode = ParentMode.Beginner)
        assertEquals(ScaffoldLevel.Beginner, ProgressionEngine.scaffoldForAtom(progress, "haus"))
        progress = progress.copy(parentMode = ParentMode.Auto)
        assertEquals(ScaffoldLevel.Advanced, ProgressionEngine.scaffoldForAtom(progress, "haus"))
    }

    @Test
    fun resolveCountsAsMissForDownshiftWithoutMastery() {
        var progress = LearnerProgress(
            atomStats = mapOf(
                "haus" to SkillStats(
                    autoScaffold = ScaffoldLevel.Advanced,
                    consecutiveCorrect = 0,
                ),
            ),
        )
        repeat(3) {
            progress = ProgressionEngine.recordAtomAttempt(progress, "haus", AttemptOutcome.Resolve)
        }
        val stats = progress.atomStats.getValue("haus")
        assertEquals(3, stats.resolves)
        assertEquals(0, stats.correct)
        assertEquals(ScaffoldLevel.Beginner, ProgressionEngine.scaffoldForAtom(progress, "haus"))
    }

    @Test
    fun firstTimeAutoDefaultsBeginner() {
        val progress = LearnerProgress()
        assertEquals(ScaffoldLevel.Beginner, ProgressionEngine.scaffoldForAtom(progress, "neu"))
    }
}
