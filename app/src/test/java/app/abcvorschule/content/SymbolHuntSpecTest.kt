package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolHuntSpecTest {
    private val round = SymbolHuntRound(
        promptTts = "Finde alle Buchstaben M!",
        targetAtomId = "letter-m",
        mode = SymbolHuntMode.letter,
        distractorPool = listOf("letter-a"),
    )
    private val spec = SymbolHuntSpec(id = "l01:symbol_hunt:letter", rounds = listOf(round))

    @Test
    fun kindIsSymbolHunt() {
        assertEquals(TrainerKind.symbol_hunt, spec.kind)
    }

    @Test
    fun roundsExposeTheSingleRound() {
        assertEquals(listOf(round), spec.rounds)
    }

    @Test
    fun scoredAtomIdsIsJustTheTarget() {
        assertEquals(listOf("letter-m"), round.scoredAtomIds())
    }
}
