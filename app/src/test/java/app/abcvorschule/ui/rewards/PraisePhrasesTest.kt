package app.abcvorschule.ui.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PraisePhrasesTest {
    @Test
    fun offersTwentyDistinctPhrases() {
        assertEquals(20, PraisePhrases.All.size)
        assertEquals(PraisePhrases.All.size, PraisePhrases.All.distinct().size)
    }

    @Test
    fun phrasesCarryNoTrailingPunctuation() {
        // Praise is spoken as its own clip after the answer — no trailing punctuation.
        PraisePhrases.All.forEach { phrase ->
            assertTrue(phrase, phrase.isNotBlank())
            assertTrue(phrase, phrase.last().isLetter())
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
