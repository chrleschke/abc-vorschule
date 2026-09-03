package app.abcvorschule.speech

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.SymbolHuntDerivation
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.content.SymbolInWordMode
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.content.SymbolInWordSpeech
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeechClipTextTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun segmentSpeechUsesTargetLemmaForHits() {
        val round = SymbolInWordDerivation.buildRounds(pack, pack.lesson("l13"))
            .first { it.targetAtomId == "letter-sch" && it.wordAtomId == "fisch" }
        val schIndex = round.segments.indexOfFirst { it.equals("sch", ignoreCase = true) }
        assertEquals("Sch", SpeechClipText.forSegment(pack, round, schIndex))
    }

    @Test
    fun segmentSpeechPrefersExactDisplayForSpPairs() {
        val round = SymbolInWordRound(
            promptTts = "Finde den Buchstaben - n - im Wort - Spinne.",
            wordAtomId = "spinne",
            targetAtomId = "letter-n",
            mode = SymbolInWordMode.letter,
            segments = listOf("sp", "i", "n", "n", "e"),
            targetIndices = listOf(2, 3),
        )
        assertEquals("sp", SpeechClipText.forSegment(pack, round, 0))
        assertEquals(
            "Sp",
            SpeechClipText.forSegment(
                pack,
                round.copy(segments = listOf("Sp", "i", "n", "n", "e")),
                0,
            ),
        )
    }

    @Test
    fun huntAndDetectiveGraphemePartsResolveToCommittedClips() {
        val index = ClipIndex.load { path ->
            javaClass.classLoader!!.getResourceAsStream(path)
                ?: throw java.io.FileNotFoundException(path)
        }
        val huntRound = SymbolHuntRound(
            promptTts = SymbolHuntDerivation.PromptDigraph,
            targetAtomId = "letter-sch",
            mode = app.abcvorschule.content.SymbolHuntMode.letter,
            distractorPool = listOf("letter-a"),
        )
        val huntParts = app.abcvorschule.content.SymbolHuntSpeech.promptParts(
            huntRound,
            pack.atom("letter-sch"),
        )
        assertNotNull(index.lookup(huntParts[0]))
        assertNotNull(index.lookup(huntParts[1]))
        assertEquals("Sch", huntParts[1])

        val detectiveRound = SymbolInWordDerivation.buildRounds(pack, pack.lesson("l13"))
            .first { it.targetAtomId == "letter-sch" && it.wordAtomId == "fisch" }
        val detectiveParts = SymbolInWordSpeech.promptParts(
            detectiveRound,
            pack.atom("letter-sch"),
            pack.atom("fisch"),
        )
        assertEquals("Sch", detectiveParts[1])
        assertNotNull(index.lookup(detectiveParts[1]))
        assertNotNull(index.lookup("sch"))
    }
}
