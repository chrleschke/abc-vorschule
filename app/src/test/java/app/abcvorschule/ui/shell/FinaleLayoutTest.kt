package app.abcvorschule.ui.shell

import app.abcvorschule.content.ContentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinaleLayoutTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun picturesKeepSentenceOrderAndCarryTheSpokenWord() {
        val pictures = FinaleLayout.picturesOf(pack, pack.finale("f-l01"))
        assertEquals(listOf("mama", "maus", "apfel"), pictures.map { it.atomId })
        assertEquals(listOf("👩", "🐭", "🍎"), pictures.map { it.emoji })
        assertEquals(listOf("Mama", "Maus", "Apfel"), pictures.map { it.lemma })
    }

    @Test
    fun picturesSkipAtomsWithoutAnEmoji() {
        // Defensive: the validator rejects such content, but a half-rendered row
        // would be worse than a shorter one.
        val finale = pack.finale("f-l01").copy(pictureAtomIds = listOf("mama", "tisch", "apfel"))
        assertEquals(listOf("mama", "apfel"), FinaleLayout.picturesOf(pack, finale).map { it.atomId })
    }

    @Test
    fun picturesSkipUnknownAtoms() {
        val finale = pack.finale("f-l01").copy(pictureAtomIds = listOf("mama", "ghost"))
        assertEquals(listOf("mama"), FinaleLayout.picturesOf(pack, finale).map { it.atomId })
    }

    @Test
    fun everyShippedFinaleRendersAllItsPictures() {
        assertEquals(18, pack.finales.size)
        pack.finales.values.forEach { finale ->
            assertEquals(
                "finale ${finale.id} loses a picture",
                finale.pictureAtomIds.size,
                FinaleLayout.picturesOf(pack, finale).size,
            )
        }
    }

    @Test
    fun fourPicturesShrinkSoTheRowStillFitsANarrowScreen() {
        assertEquals(64, FinaleLayout.pictureSizeSp(2))
        assertEquals(64, FinaleLayout.pictureSizeSp(3))
        assertEquals(52, FinaleLayout.pictureSizeSp(4))
    }

    @Test
    fun pictureSizeStaysSaneOutsideTheAuthoredRange() {
        assertEquals(64, FinaleLayout.pictureSizeSp(0))
        assertEquals(52, FinaleLayout.pictureSizeSp(9))
    }

    @Test
    fun picturesRevealLeftToRight() {
        assertEquals(0L, FinaleLayout.revealDelayMillis(0))
        assertEquals(180L, FinaleLayout.revealDelayMillis(1))
        assertEquals(360L, FinaleLayout.revealDelayMillis(2))
    }

    @Test
    fun revealDelayNeverGoesNegative() {
        assertTrue(FinaleLayout.revealDelayMillis(-1) >= 0L)
    }
}
