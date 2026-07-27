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
}
