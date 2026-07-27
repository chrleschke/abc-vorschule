package app.abcvorschule.ui.exercise

import app.abcvorschule.content.SymbolHuntMode
import app.abcvorschule.content.SymbolHuntRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolHuntProgressTest {
    private val roundWithThreeDistractors = SymbolHuntRound(
        promptTts = "Finde alle Buchstaben A!",
        targetAtomId = "letter-a",
        mode = SymbolHuntMode.letter,
        distractorPool = listOf("letter-m", "letter-i", "letter-o"),
    )

    @Test
    fun initialStateHasFiveHitsAndSixDistractorTiles() {
        val state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        assertEquals(5, state.targetHitCount)
        assertEquals(5, state.tiles.count { it.isTarget })
        assertEquals(6, state.tiles.count { !it.isTarget })
    }

    @Test
    fun initialStateRepeatsASmallPoolToFillDistractorTiles() {
        val round = roundWithThreeDistractors.copy(distractorPool = listOf("letter-m"))
        val state = SymbolHuntProgress.initialState(round, seed = 1L)
        assertEquals(3, state.targetHitCount)
        assertEquals(2, state.tiles.count { !it.isTarget })
        assertTrue(state.tiles.filter { !it.isTarget }.all { it.atomId == "letter-m" })
    }

    @Test
    fun collectingAHitRemovesItAndIncrementsBatteryWithoutReportingAResult() {
        val state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val hitId = state.tiles.first { it.isTarget }.instanceId
        val result = SymbolHuntProgress.tap(state, hitId)
        assertEquals(SymbolHuntTapOutcome.Collected, result.outcome)
        assertEquals(1, result.state.collected)
        assertTrue(result.state.tiles.none { it.instanceId == hitId })
    }

    @Test
    fun collectingTheLastHitReportsRoundComplete() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        repeat(4) {
            val hitId = state.tiles.first { it.isTarget }.instanceId
            state = SymbolHuntProgress.tap(state, hitId).state
        }
        val lastHitId = state.tiles.first { it.isTarget }.instanceId
        val result = SymbolHuntProgress.tap(state, lastHitId)
        assertEquals(SymbolHuntTapOutcome.RoundComplete, result.outcome)
        assertEquals(5, result.state.collected)
    }

    @Test
    fun wrongTapReshufflesWithoutLosingBatteryProgress() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val hitId = state.tiles.first { it.isTarget }.instanceId
        state = SymbolHuntProgress.tap(state, hitId).state
        assertEquals(1, state.collected)
        val distractorId = state.tiles.first { !it.isTarget }.instanceId
        val result = SymbolHuntProgress.tap(state, distractorId)
        assertEquals(1, result.state.collected)
        assertEquals(state.seed + 1, result.state.seed)
    }

    @Test
    fun onlyTheFirstMissOfARoundReports() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val distractorId = state.tiles.first { !it.isTarget }.instanceId
        val first = SymbolHuntProgress.tap(state, distractorId)
        assertEquals(SymbolHuntTapOutcome.Miss, first.outcome)
        val second = SymbolHuntProgress.tap(first.state, distractorId)
        assertEquals(SymbolHuntTapOutcome.MissAlreadyReported, second.outcome)
    }

    @Test
    fun tappingAnAlreadyCollectedInstanceIsIgnored() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val hitId = state.tiles.first { it.isTarget }.instanceId
        state = SymbolHuntProgress.tap(state, hitId).state
        val result = SymbolHuntProgress.tap(state, hitId)
        assertEquals(SymbolHuntTapOutcome.Ignored, result.outcome)
        assertEquals(state, result.state)
    }

    @Test
    fun resolveAvailableAfterSixConsecutiveMisses() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val distractorId = state.tiles.first { !it.isTarget }.instanceId
        repeat(5) { state = SymbolHuntProgress.tap(state, distractorId).state }
        assertFalse(SymbolHuntProgress.resolveAvailable(state))
        state = SymbolHuntProgress.tap(state, distractorId).state
        assertTrue(SymbolHuntProgress.resolveAvailable(state))
    }

    @Test
    fun aCorrectTapResetsTheConsecutiveMissCounter() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val distractorId = state.tiles.first { !it.isTarget }.instanceId
        repeat(5) { state = SymbolHuntProgress.tap(state, distractorId).state }
        assertEquals(5, state.consecutiveMisses)
        val hitId = state.tiles.first { it.isTarget }.instanceId
        state = SymbolHuntProgress.tap(state, hitId).state
        assertEquals(0, state.consecutiveMisses)
    }

    @Test
    fun resolveFillsTheBatteryAndClearsTheField() {
        val state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val resolved = SymbolHuntProgress.resolve(state)
        assertEquals(5, resolved.collected)
        assertTrue(resolved.tiles.isEmpty())
    }

    // A pool bigger than the fixed 6-tile distractor budget (distractorCount for any
    // pool > 2 is always 6, per SymbolHuntDerivation.tileCounts), so only a subset of
    // the pool ever becomes tiles — the case where "which subset" can actually vary.
    private val roundWithLargeDistractorPool = SymbolHuntRound(
        promptTts = "Finde alle Buchstaben A!",
        targetAtomId = "letter-a",
        mode = SymbolHuntMode.letter,
        distractorPool = listOf(
            "letter-m", "letter-i", "letter-o", "letter-t", "letter-l",
            "letter-e", "letter-s", "letter-u", "letter-r", "letter-n",
        ),
    )

    @Test
    fun distractorSelectionIsDeterministicForAFixedSeed() {
        val first = SymbolHuntProgress.initialState(roundWithLargeDistractorPool, seed = 42L)
        val second = SymbolHuntProgress.initialState(roundWithLargeDistractorPool, seed = 42L)
        assertEquals(
            first.tiles.filter { !it.isTarget }.map { it.atomId },
            second.tiles.filter { !it.isTarget }.map { it.atomId },
        )
    }

    @Test
    fun distractorSelectionVariesAcrossDifferentSeedsWhenThePoolIsLargeEnough() {
        val a = SymbolHuntProgress.initialState(roundWithLargeDistractorPool, seed = 1L)
        val b = SymbolHuntProgress.initialState(roundWithLargeDistractorPool, seed = 2L)
        val distractorsA = a.tiles.filter { !it.isTarget }.map { it.atomId }.toSet()
        val distractorsB = b.tiles.filter { !it.isTarget }.map { it.atomId }.toSet()
        assertTrue(
            "expected different seeds to select different distractor subsets from a pool " +
                "larger than the distractor tile budget",
            distractorsA != distractorsB,
        )
    }

    @Test
    fun distractorSelectionIsNotAlwaysTheFirstNPoolEntriesInOrder() {
        // Regression guard for the original bug: taking round.distractorPool[i %
        // size] always yielded the pool's first `distractorCount` entries in
        // curriculum-introduction order. At least one seed must diverge from that.
        val firstSixInOrder = roundWithLargeDistractorPool.distractorPool.take(6)
        val seeds = listOf(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L)
        val anyDiffers = seeds.any { seed ->
            val state = SymbolHuntProgress.initialState(roundWithLargeDistractorPool, seed = seed)
            val distractorIds = state.tiles.filter { !it.isTarget }.map { it.atomId }
            distractorIds != firstSixInOrder
        }
        assertTrue("expected at least one seed to pick a different subset than the first six", anyDiffers)
    }
}
