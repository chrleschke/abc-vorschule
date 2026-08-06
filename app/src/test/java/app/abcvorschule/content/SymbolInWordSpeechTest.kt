package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolInWordSpeechTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun digraphDetectiveSpeaksIntroThenLemmaThenWord() {
        val round = SymbolInWordDerivation.buildRounds(pack, pack.lesson("l13"))
            .first { it.targetAtomId == "letter-sch" && it.wordAtomId == "schuh" }
        val parts = SymbolInWordSpeech.promptParts(
            round,
            pack.atom("letter-sch"),
            pack.atom("schuh"),
        )
        assertEquals(listOf("Finde den Laut", "Sch", "...im Wort...", "Schuh"), parts)
    }

    @Test
    fun letterDetectiveSpeaksIntroThenLemmaThenWord() {
        val round = SymbolInWordDerivation.buildRounds(pack, pack.lesson("l03"))
            .first { it.targetAtomId == "letter-p" }
        val parts = SymbolInWordSpeech.promptParts(
            round,
            pack.atom("letter-p"),
            pack.atom(round.wordAtomId),
        )
        assertEquals(listOf("Finde alle Buchstaben", "P", "...im Wort...", "Papa"), parts)
    }

    @Test
    fun syllableDetectiveSpeaksIntroThenLemmaThenWord() {
        val round = SymbolInWordDerivation.buildRounds(pack, pack.lesson("l03"))
            .first { it.mode == SymbolInWordMode.syllable }
        val parts = SymbolInWordSpeech.promptParts(
            round,
            pack.atom(round.targetAtomId),
            pack.atom(round.wordAtomId),
        )
        assertEquals(listOf("Finde die Silbe", "pa", "...im Wort...", "Opa"), parts)
    }
}
