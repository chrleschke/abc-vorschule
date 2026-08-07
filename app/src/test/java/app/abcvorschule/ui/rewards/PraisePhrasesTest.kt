package app.abcvorschule.ui.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PraisePhrasesTest {
    @Test
    fun offersThirtyNineDistinctPhrases() {
        // Mirrored by tools/tts/tests/test_extract.py, which counts the same phrases
        // as rewardTts entries in extra-strings.json.
        assertEquals(39, PraisePhrases.All.size)
        assertEquals(PraisePhrases.All.size, PraisePhrases.All.distinct().size)
    }

    @Test
    fun phrasesStayDistinctIgnoringPunctuationAndCase() {
        // The dedup rule: "Spitze" and "Das war spitze!" are two different cheers, but the
        // same words with different punctuation would be one clip curated and rendered twice.
        val normalized = PraisePhrases.All.map { phrase ->
            phrase.lowercase()
                .map { if (it.isLetterOrDigit()) it else ' ' }
                .joinToString("")
                .split(" ")
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        }
        assertEquals(normalized.toString(), normalized.size, normalized.distinct().size)
    }

    @Test
    fun phrasesEndAsAStatementOrACheer() {
        // Each phrase is spoken as its own utterance, so it ends either bare ("Klasse")
        // or on its own sentence punctuation — never on "?", praise does not ask.
        PraisePhrases.All.forEach { phrase ->
            assertTrue(phrase, phrase.isNotBlank())
            assertTrue(phrase, phrase.last().isLetterOrDigit() || phrase.last() in ".!")
        }
    }

    @Test
    fun pickIsAlwaysOneOfThePhrases() {
        val random = Random(7)
        repeat(200) {
            assertTrue(PraisePhrases.pick(random) in PraisePhrases.All)
        }
    }

    @Test
    fun pickVariesAcrossCalls() {
        val random = Random(7)
        val seen = (1..50).map { PraisePhrases.pick(random) }.toSet()
        assertTrue("praise should vary, saw $seen", seen.size > 1)
    }
}
