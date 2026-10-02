package app.abcvorschule.ui.exercise

import app.abcvorschule.content.SymbolInWordRound

/**
 * Tap state of one Wort-Detektiv round (design doc §6). Indices address
 * [SymbolInWordRound.segments]; the round itself is immutable, so only the
 * bookkeeping lives here.
 */
data class SymbolInWordState(
    val segmentCount: Int,
    val targetIndices: Set<Int>,
    /** Hits in the order the child found them. A Set would lose that order, and the
     * flight animation needs to know which segment was just collected. */
    val collected: List<Int> = emptyList(),
    val reportedMissThisRound: Boolean = false,
    /** Segment that was tapped wrong last, for the spin animation. */
    val wrongIndex: Int? = null,
    /**
     * Bumped on every wrong tap. The animation is keyed on this rather than on
     * [wrongIndex] alone, so tapping the *same* wrong segment twice replays the
     * spin instead of sitting still.
     */
    val wrongNonce: Int = 0,
) {
    val remainingSlots: Int get() = targetIndices.size - collected.size
}

enum class SymbolInWordTapOutcome { Collected, RoundComplete, Miss, MissAlreadyReported, Ignored }

data class SymbolInWordTapResult(val state: SymbolInWordState, val outcome: SymbolInWordTapOutcome)

/**
 * A wrong tap costs nothing — no slot, no points, no reshuffle (unlike the
 * Buchstaben-Jagd, where reshuffling the scatter field is the feedback; here the
 * word must stay put, because its letter order is the whole point).
 *
 * Reporting stops after the first miss of a round so a child tapping through an
 * eight-segment word cannot wreck the atom's statistics.
 *
 * Kein „Zeig mir" mehr (Nutzerentscheidung Oktober 2026, Sternenhimmel-Spec): das Wort
 * ist die ganze Auswahl, jeder Fehltipp spricht sein Segment vor, und das Kind tippt
 * weiter, bis es die Sterne gefunden hat. Deshalb gibt es weder `resolve` noch einen
 * Zähler aufeinanderfolgender Fehltipps — der hat nur den Knopf freigeschaltet.
 */
object SymbolInWordProgress {
    fun initialState(round: SymbolInWordRound): SymbolInWordState = SymbolInWordState(
        segmentCount = round.segments.size,
        targetIndices = round.targetIndices.toSet(),
    )

    fun tap(state: SymbolInWordState, index: Int): SymbolInWordTapResult {
        if (index !in 0 until state.segmentCount || index in state.collected) {
            return SymbolInWordTapResult(state, SymbolInWordTapOutcome.Ignored)
        }
        if (index in state.targetIndices) {
            val collected = state.collected + index
            val next = state.copy(collected = collected, wrongIndex = null)
            val outcome = if (collected.size >= state.targetIndices.size) {
                SymbolInWordTapOutcome.RoundComplete
            } else {
                SymbolInWordTapOutcome.Collected
            }
            return SymbolInWordTapResult(next, outcome)
        }
        val alreadyReported = state.reportedMissThisRound
        val next = state.copy(
            reportedMissThisRound = true,
            wrongIndex = index,
            wrongNonce = state.wrongNonce + 1,
        )
        val outcome = if (alreadyReported) {
            SymbolInWordTapOutcome.MissAlreadyReported
        } else {
            SymbolInWordTapOutcome.Miss
        }
        return SymbolInWordTapResult(next, outcome)
    }
}
