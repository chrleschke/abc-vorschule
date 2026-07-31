package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SymbolInWordSpecTest {
    private val round = SymbolInWordRound(
        promptTts = "Finde alle Buchstaben - P - im Wort - Papa.",
        wordAtomId = "papa",
        targetAtomId = "letter-p",
        mode = SymbolInWordMode.letter,
        segments = listOf("P", "a", "p", "a"),
        targetIndices = listOf(0, 2),
    )

    private val spec = SymbolInWordSpec(id = "l03:symbol_in_word", rounds = listOf(round))

    @Test
    fun kindMapsToTheNewTrainer() {
        assertEquals(TrainerKind.symbol_in_word, spec.kind)
    }

    @Test
    fun roundsAreReachableThroughTheSealedAccessor() {
        assertEquals(1, spec.roundCount)
        assertEquals(round, spec.round(0))
    }

    @Test
    fun scoresAgainstTheHuntedSymbolNotTheWord() {
        // The child practices the symbol; the word is only where it hides.
        assertEquals(listOf("letter-p"), round.scoredAtomIds())
    }

    @Test
    fun theNewKindIsNotPartOfTheAuthoredTrainerOrder() {
        // Runtime-derived like symbol_hunt: it must never be authorable, or the
        // validator's non-decreasing-rank check would have to know about it.
        assertFalse(ContentValidator.TrainerOrder.contains(TrainerKind.symbol_in_word))
    }
}
