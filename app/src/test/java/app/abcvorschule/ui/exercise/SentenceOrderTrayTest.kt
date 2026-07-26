package app.abcvorschule.ui.exercise

import app.abcvorschule.content.WordBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceOrderTrayTest {
    private val words = listOf("Oma", "ist", "da")
    private val atomIds = listOf("oma", "ist", "da")
    private val seed = "sentence-oma-ist-da".hashCode()

    @Test
    fun trayIsShuffleSafeAndCarriesOneCardPerWord() {
        val cards = SentenceOrderTray.cards(words, atomIds, emptyList(), emptyList(), seed)
        assertEquals(words.toSet(), cards.map { it.display }.toSet())
        assertEquals(atomIds.toSet(), cards.map { it.atomId }.toSet())
    }

    @Test
    fun hungCardsLeaveTheTray() {
        val cards = SentenceOrderTray.cards(words, atomIds, emptyList(), listOf("Oma"), seed)
        // Order is no longer the point once a shuffle is in play — only the remaining
        // multiset matters here.
        assertEquals(listOf("da", "ist"), cards.map { it.display }.sorted())
    }

    @Test
    fun distractorsAreOfferedOnceEveryWordIsStillAvailable() {
        val cards = SentenceOrderTray.cards(
            words,
            atomIds,
            listOf(WordBlock("mama", "Mama")),
            emptyList(),
            seed,
        )
        assertTrue(cards.any { it.display == "Mama" })
        assertTrue(cards.size <= SentenceOrderTray.MaxTrayTiles)
    }

    @Test
    fun singleWordRoundDegeneratesToPictureMatching() {
        val cards = SentenceOrderTray.cards(
            listOf("Mama"),
            listOf("mama"),
            emptyList(),
            emptyList(),
            seed,
        )
        assertEquals(1, cards.size)
        assertEquals("Mama", cards.single().display)
    }

    @Test
    fun repeatedWordStaysAvailableUntilBothCopiesAreHung() {
        val repeated = listOf("Mama", "ist", "Mama")
        val ids = listOf("mama", "ist", "mama")
        val cards = SentenceOrderTray.cards(repeated, ids, emptyList(), listOf("Mama"), seed)
        assertEquals(listOf("Mama", "ist"), cards.map { it.display }.sorted())
    }

    @Test
    fun trayCarriesExactlyTheRightMultisetSoTheRoundStaysSolvable() {
        val cards = SentenceOrderTray.cards(words, atomIds, emptyList(), listOf("ist"), seed)
        assertEquals(listOf("Oma", "da"), cards.map { it.display }.sorted())
        assertEquals(listOf("da", "oma"), cards.map { it.atomId }.sorted())
    }

    @Test
    fun offeredOrderIsNotSolutionOrderForAMultiWordSentence() {
        val cards = SentenceOrderTray.cards(words, atomIds, emptyList(), emptyList(), seed)
        assertNotEquals(words, cards.map { it.display })
    }

    @Test
    fun sameSeedAndPlacementsProduceTheSameOrderEveryTime() {
        val first = SentenceOrderTray.cards(words, atomIds, emptyList(), emptyList(), seed)
        val second = SentenceOrderTray.cards(words, atomIds, emptyList(), emptyList(), seed)
        assertEquals(first.map { it.display }, second.map { it.display })
    }

    @Test
    fun pegKeysRoundTrip() {
        assertEquals(0, SentenceOrderTray.pegIndex(SentenceOrderTray.pegKey(0)))
        assertEquals(2, SentenceOrderTray.pegIndex(SentenceOrderTray.pegKey(2)))
        assertNull(SentenceOrderTray.pegIndex("frame-1"))
    }
}
