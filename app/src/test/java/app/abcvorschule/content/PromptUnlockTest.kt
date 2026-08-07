package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptUnlockTest {

    @Test
    fun symbolInWordUnlocksAfterTargetPart_evenWithTrailingParts() {
        val round = SymbolInWordRound(
            promptTts = "Finde den Buchstaben",
            wordAtomId = "word-mama",
            targetAtomId = "letter-m",
            mode = SymbolInWordMode.letter,
            segments = listOf("M", "a", "m", "a"),
            targetIndices = listOf(0, 2),
        )
        val parts = listOf("Finde den Buchstaben", "M", "...im Wort...", "Mama")
        assertEquals(1, PromptUnlock.unlockIndex(round, parts))
    }

    @Test
    fun symbolInWordUnlocksAtLastPart_whenShorterThanFour() {
        val round = SymbolInWordRound(
            promptTts = "Finde den Buchstaben",
            wordAtomId = "word-a",
            targetAtomId = "letter-a",
            mode = SymbolInWordMode.letter,
            segments = listOf("A"),
            targetIndices = listOf(0),
        )
        val parts = listOf("Finde den Buchstaben", "A")
        assertEquals(1, PromptUnlock.unlockIndex(round, parts))
    }

    @Test
    fun symbolHuntUnlocksAtLastPart() {
        val round = SymbolHuntRound(
            promptTts = "Finde alle Buchstaben",
            targetAtomId = "letter-a",
            mode = SymbolHuntMode.letter,
            distractorPool = listOf("letter-m"),
        )
        val parts = listOf("Finde alle Buchstaben", "A")
        assertEquals(1, PromptUnlock.unlockIndex(round, parts))
    }

    @Test
    fun singlePartRoundUnlocksAtItsOnlyPart() {
        val round = WordBuildRound(
            promptTts = "Baue das Wort Mama.",
            targetAtomId = "word-mama",
            blocks = listOf(WordBlock(atomId = "letter-m", display = "M")),
        )
        val parts = listOf("Baue das Wort Mama.")
        assertEquals(0, PromptUnlock.unlockIndex(round, parts))
    }

    @Test
    fun emptyPartsUnlockAtZero() {
        val round = WordBuildRound(
            promptTts = "",
            targetAtomId = "word-mama",
            blocks = emptyList(),
        )
        assertEquals(0, PromptUnlock.unlockIndex(round, emptyList()))
    }
}
