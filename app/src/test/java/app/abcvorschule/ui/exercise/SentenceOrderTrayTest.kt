package app.abcvorschule.ui.exercise

import app.abcvorschule.content.WordBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceOrderTrayTest {
    private val words = listOf("Oma", "ist", "da")
    private val atomIds = listOf("oma", "ist", "da")

    @Test
    fun trayIsShuffleSafeAndCarriesOneCardPerWord() {
        val cards = SentenceOrderTray.cards(words, atomIds, emptyList(), emptyList())
        assertEquals(words.toSet(), cards.map { it.display }.toSet())
        assertEquals(atomIds.toSet(), cards.map { it.atomId }.toSet())
    }

    @Test
    fun hungCardsLeaveTheTray() {
        val cards = SentenceOrderTray.cards(words, atomIds, emptyList(), listOf("Oma"))
        assertEquals(listOf("ist", "da"), cards.map { it.display })
    }

    @Test
    fun distractorsAreOfferedOnceEveryWordIsStillAvailable() {
        val cards = SentenceOrderTray.cards(
            words,
            atomIds,
            listOf(WordBlock("mama", "Mama")),
            emptyList(),
        )
        assertTrue(cards.any { it.display == "Mama" })
        assertTrue(cards.size <= SentenceOrderTray.MaxTrayTiles)
    }

    @Test
    fun singleWordRoundDegeneratesToPictureMatching() {
        val cards = SentenceOrderTray.cards(listOf("Mama"), listOf("mama"), emptyList(), emptyList())
        assertEquals(1, cards.size)
        assertEquals("Mama", cards.single().display)
    }

    @Test
    fun repeatedWordStaysAvailableUntilBothCopiesAreHung() {
        val repeated = listOf("Mama", "ist", "Mama")
        val ids = listOf("mama", "ist", "mama")
        val cards = SentenceOrderTray.cards(repeated, ids, emptyList(), listOf("Mama"))
        assertEquals(listOf("ist", "Mama"), cards.map { it.display })
    }

    @Test
    fun pegKeysRoundTrip() {
        assertEquals(0, SentenceOrderTray.pegIndex(SentenceOrderTray.pegKey(0)))
        assertEquals(2, SentenceOrderTray.pegIndex(SentenceOrderTray.pegKey(2)))
        assertNull(SentenceOrderTray.pegIndex("frame-1"))
    }
}
