package app.abcvorschule.ui.exercise

import app.abcvorschule.content.SymbolInWordMode
import app.abcvorschule.content.SymbolInWordRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolInWordProgressTest {
    /** "Papa" hunting P: hits at 0 and 2. */
    private val papa = SymbolInWordRound(
        promptTts = "Finde alle Buchstaben - P - im Wort - Papa.",
        wordAtomId = "papa",
        targetAtomId = "letter-p",
        mode = SymbolInWordMode.letter,
        segments = listOf("P", "a", "p", "a"),
        targetIndices = listOf(0, 2),
    )

    /** "Oma" hunting O: a single hit at 0. */
    private val oma = SymbolInWordRound(
        promptTts = "Finde den Buchstaben - O - im Wort - Oma.",
        wordAtomId = "oma",
        targetAtomId = "letter-o",
        mode = SymbolInWordMode.letter,
        segments = listOf("O", "m", "a"),
        targetIndices = listOf(0),
    )

    @Test
    fun initialStateHasEverySlotEmpty() {
        val state = SymbolInWordProgress.initialState(papa)
        assertEquals(setOf(0, 2), state.targetIndices)
        assertTrue(state.collected.isEmpty())
        assertEquals(2, state.remainingSlots)
    }

    @Test
    fun aCorrectTapCollectsTheSegment() {
        val result = SymbolInWordProgress.tap(SymbolInWordProgress.initialState(papa), 0)
        assertEquals(SymbolInWordTapOutcome.Collected, result.outcome)
        assertEquals(listOf(0), result.state.collected)
        assertEquals(1, result.state.remainingSlots)
    }

    @Test
    fun theLastCorrectTapCompletesTheRound() {
        var state = SymbolInWordProgress.initialState(papa)
        state = SymbolInWordProgress.tap(state, 2).state
        val result = SymbolInWordProgress.tap(state, 0)
        assertEquals(SymbolInWordTapOutcome.RoundComplete, result.outcome)
        assertEquals(0, result.state.remainingSlots)
    }

    @Test
    fun collectedKeepsTapOrderSoTheFlightKnowsWhereItStarted() {
        // Tapping the later hit first must not reorder history: the flight
        // animation starts from collected.last().
        var state = SymbolInWordProgress.initialState(papa)
        state = SymbolInWordProgress.tap(state, 2).state
        assertEquals(2, state.collected.last())
        state = SymbolInWordProgress.tap(state, 0).state
        assertEquals(listOf(2, 0), state.collected)
    }

    @Test
    fun aSingleHitRoundCompletesOnTheFirstCorrectTap() {
        val result = SymbolInWordProgress.tap(SymbolInWordProgress.initialState(oma), 0)
        assertEquals(SymbolInWordTapOutcome.RoundComplete, result.outcome)
    }

    @Test
    fun tappingAnAlreadyCollectedSegmentIsANoOp() {
        var state = SymbolInWordProgress.initialState(papa)
        state = SymbolInWordProgress.tap(state, 0).state
        val result = SymbolInWordProgress.tap(state, 0)
        assertEquals(SymbolInWordTapOutcome.Ignored, result.outcome)
        assertEquals(listOf(0), result.state.collected)
        assertEquals(0, result.state.consecutiveMisses)
    }

    @Test
    fun anOutOfBoundsIndexIsIgnored() {
        val state = SymbolInWordProgress.initialState(papa)
        assertEquals(SymbolInWordTapOutcome.Ignored, SymbolInWordProgress.tap(state, 9).outcome)
        assertEquals(SymbolInWordTapOutcome.Ignored, SymbolInWordProgress.tap(state, -1).outcome)
    }

    @Test
    fun aWrongTapLosesNoProgress() {
        var state = SymbolInWordProgress.initialState(papa)
        state = SymbolInWordProgress.tap(state, 0).state
        val result = SymbolInWordProgress.tap(state, 1)
        assertEquals(SymbolInWordTapOutcome.Miss, result.outcome)
        assertEquals(listOf(0), result.state.collected)
        assertEquals(1, result.state.remainingSlots)
    }

    @Test
    fun onlyTheFirstMissOfARoundIsReported() {
        var state = SymbolInWordProgress.initialState(papa)
        assertEquals(SymbolInWordTapOutcome.Miss, SymbolInWordProgress.tap(state, 1).outcome)
        state = SymbolInWordProgress.tap(state, 1).state
        assertEquals(SymbolInWordTapOutcome.MissAlreadyReported, SymbolInWordProgress.tap(state, 3).outcome)
    }

    @Test
    fun everyWrongTapBumpsTheNonceSoTheSpinRestarts() {
        var state = SymbolInWordProgress.initialState(papa)
        state = SymbolInWordProgress.tap(state, 1).state
        val first = state.wrongNonce
        state = SymbolInWordProgress.tap(state, 1).state
        assertEquals(1, state.wrongNonce - first)
        assertEquals(1, state.wrongIndex)
    }

    @Test
    fun consecutiveMissesKeepCountingAfterReportingStops() {
        // Reporting is for adaptivity, counting is for the resolve gate — two jobs.
        var state = SymbolInWordProgress.initialState(papa)
        repeat(3) { state = SymbolInWordProgress.tap(state, 1).state }
        assertEquals(3, state.consecutiveMisses)
        assertTrue(state.reportedMissThisRound)
    }

    @Test
    fun aCorrectTapResetsTheConsecutiveMissCount() {
        var state = SymbolInWordProgress.initialState(papa)
        repeat(3) { state = SymbolInWordProgress.tap(state, 1).state }
        state = SymbolInWordProgress.tap(state, 0).state
        assertEquals(0, state.consecutiveMisses)
    }

    @Test
    fun resolveUnlocksOnlyAfterTheSharedThreshold() {
        var state = SymbolInWordProgress.initialState(papa)
        repeat(ResolveGate.Threshold - 1) { state = SymbolInWordProgress.tap(state, 1).state }
        assertFalse(SymbolInWordProgress.resolveAvailable(state))
        state = SymbolInWordProgress.tap(state, 1).state
        assertTrue(SymbolInWordProgress.resolveAvailable(state))
    }

    @Test
    fun resolveFillsEverySlot() {
        val resolved = SymbolInWordProgress.resolve(SymbolInWordProgress.initialState(papa))
        assertEquals(listOf(0, 2), resolved.collected)
        assertEquals(0, resolved.remainingSlots)
    }

    @Test
    fun theHuntSharesTheSameResolveThreshold() {
        assertEquals(ResolveGate.Threshold, SymbolHuntProgress.ResolveThreshold)
    }
}
