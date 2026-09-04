package app.abcvorschule.ui.exercise

import app.abcvorschule.content.FeederSide
import app.abcvorschule.content.SoundFeederCard
import app.abcvorschule.content.SoundFeederRound

/**
 * Zustand einer Fresser-Runde (design doc §6). Die Runde selbst ist unveränderlich;
 * hier steht nur, wie weit das Kind ist und wie oft es bei der aktuellen Karte
 * danebenlag.
 */
data class SoundFeederState(
    val cards: List<SoundFeederCard>,
    val nextIndex: Int = 0,
    val missesOnCard: Int = 0,
    val reportedMissThisRound: Boolean = false,
    /** Fresser, der zuletzt gespuckt hat — der Trainer lässt genau den sich schütteln. */
    val wrongSide: FeederSide? = null,
) {
    val current: SoundFeederCard? get() = cards.getOrNull(nextIndex)
    val remaining: Int get() = cards.size - nextIndex
    val eaten: Int get() = nextIndex
    val hintActive: Boolean get() = missesOnCard >= SoundFeederProgress.HintAfterMisses
}

enum class SoundFeederDropOutcome { Eaten, RoundComplete, Miss, MissAlreadyReported, Ignored }

data class SoundFeederDropResult(val state: SoundFeederState, val outcome: SoundFeederDropOutcome)

/**
 * Ein Fehlgriff kostet nichts; der erste der Runde wird einmal gemeldet
 * (`reportedMissThisRound`, wie in der Jagd), damit ein Kind, das rät, die
 * Statistik beider Laute nicht ruiniert. Der Hinweis hängt an der Karte, nicht an
 * der Runde: beim zweiten Fehlgriff bei *derselben* Karte reißt der richtige
 * Fresser das Maul auf. Kein „Zeig mir" — bei zwei Zielen ist das die Auflösung.
 */
object SoundFeederProgress {
    const val HintAfterMisses = 2

    fun initialState(round: SoundFeederRound): SoundFeederState = SoundFeederState(cards = round.cards)

    fun drop(state: SoundFeederState, side: FeederSide?): SoundFeederDropResult {
        val card = state.current ?: return SoundFeederDropResult(state, SoundFeederDropOutcome.Ignored)
        if (side == null) return SoundFeederDropResult(state, SoundFeederDropOutcome.Ignored)
        if (side == card.side) {
            val next = state.copy(nextIndex = state.nextIndex + 1, missesOnCard = 0, wrongSide = null)
            val outcome = if (next.current == null) SoundFeederDropOutcome.RoundComplete else SoundFeederDropOutcome.Eaten
            return SoundFeederDropResult(next, outcome)
        }
        val alreadyReported = state.reportedMissThisRound
        val next = state.copy(
            missesOnCard = state.missesOnCard + 1,
            reportedMissThisRound = true,
            wrongSide = side,
        )
        val outcome = if (alreadyReported) SoundFeederDropOutcome.MissAlreadyReported else SoundFeederDropOutcome.Miss
        return SoundFeederDropResult(next, outcome)
    }
}
