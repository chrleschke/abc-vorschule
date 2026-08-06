package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolHuntSpeechTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun letterHuntSpeaksPromptThenPhonemeLemma() {
        val round = SymbolHuntRound(
            promptTts = SymbolHuntDerivation.PromptLetter,
            targetAtomId = "letter-a",
            mode = SymbolHuntMode.letter,
            distractorPool = listOf("letter-m"),
        )
        val parts = SymbolHuntSpeech.promptParts(round, pack.atom("letter-a"))
        assertEquals(listOf("Finde alle Buchstaben", "A"), parts)
    }

    @Test
    fun lautHuntSpeaksPromptThenPhonemeLemma() {
        val round = SymbolHuntRound(
            promptTts = SymbolHuntDerivation.PromptDigraph,
            targetAtomId = "letter-sch",
            mode = SymbolHuntMode.letter,
            distractorPool = listOf("letter-a"),
        )
        val parts = SymbolHuntSpeech.promptParts(round, pack.atom("letter-sch"))
        assertEquals(listOf("Finde alle Laute", "Sch"), parts)
    }

    @Test
    fun syllableHuntSpeaksPromptThenPhonemeLemma() {
        val round = SymbolHuntRound(
            promptTts = SymbolHuntDerivation.PromptSyllable,
            targetAtomId = "ma",
            mode = SymbolHuntMode.syllable,
            distractorPool = listOf("mi"),
        )
        val parts = SymbolHuntSpeech.promptParts(round, pack.atom("ma"))
        assertEquals(listOf("Finde alle Silben", "ma"), parts)
    }
}
