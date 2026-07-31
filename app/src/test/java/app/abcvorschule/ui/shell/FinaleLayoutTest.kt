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
        assertEquals(64, FinaleLayout.pictureSizeSp(2, fontScale = 1f))
        assertEquals(64, FinaleLayout.pictureSizeSp(3, fontScale = 1f))
        assertEquals(52, FinaleLayout.pictureSizeSp(4, fontScale = 1f))
    }

    @Test
    fun pictureSizeStaysSaneOutsideTheAuthoredRange() {
        assertEquals(64, FinaleLayout.pictureSizeSp(0, fontScale = 1f))
        assertEquals(52, FinaleLayout.pictureSizeSp(9, fontScale = 1f))
    }

    @Test
    fun sentenceSizeAtDefaultFontScaleMatchesHeadlineSmall() {
        // Pins the no-regression case together with the picture assertions above:
        // fontScale = 1.0 must render exactly as before the accessibility-scale
        // change was introduced.
        assertEquals(24, FinaleLayout.sentenceSizeSp(fontScale = 1f))
    }

    @Test
    fun sizesBelowFontScaleOneAlsoStayUnchanged() {
        // A user who shrinks system text should not see anything special here either —
        // only fontScale > 1.0 triggers the give-back-enlargement behaviour.
        assertEquals(64, FinaleLayout.pictureSizeSp(2, fontScale = 0.85f))
        assertEquals(24, FinaleLayout.sentenceSizeSp(fontScale = 0.85f))
    }

    @Test
    fun largeFontScaleShrinksBothPicturesAndTheSentence() {
        assertTrue(FinaleLayout.pictureSizeSp(2, fontScale = 2f) < 64)
        assertTrue(FinaleLayout.sentenceSizeSp(fontScale = 2f) < 24)
    }

    @Test
    fun effectiveSizeNeverExceedsTheFontScaleOneBudget() {
        // The whole point of the shrink: returned sp * fontScale (what actually renders)
        // must never exceed what it would have been at fontScale = 1.0.
        listOf(1f, 1.15f, 1.3f, 1.5f, 1.8f, 2f, 3f).forEach { scale ->
            val pictureEffective = FinaleLayout.pictureSizeSp(2, scale) * scale
            assertTrue(
                "picture effective size $pictureEffective exceeds 64 at scale $scale",
                pictureEffective <= 64f,
            )
            val sentenceEffective = FinaleLayout.sentenceSizeSp(scale) * scale
            assertTrue(
                "sentence effective size $sentenceEffective exceeds 24 at scale $scale",
                sentenceEffective <= 24f,
            )
        }
    }

    @Test
    fun sizesAreMonotonicAsFontScaleGrows() {
        // A larger scale must never return a larger sp value than a smaller one.
        val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.8f, 2f, 3f, 5f)
        val pictureSizes = scales.map { FinaleLayout.pictureSizeSp(2, it) }
        val sentenceSizes = scales.map { FinaleLayout.sentenceSizeSp(it) }
        for (i in 1 until scales.size) {
            assertTrue(
                "picture size grew from ${pictureSizes[i - 1]} to ${pictureSizes[i]} " +
                    "between scale ${scales[i - 1]} and ${scales[i]}",
                pictureSizes[i] <= pictureSizes[i - 1],
            )
            assertTrue(
                "sentence size grew from ${sentenceSizes[i - 1]} to ${sentenceSizes[i]} " +
                    "between scale ${scales[i - 1]} and ${scales[i]}",
                sentenceSizes[i] <= sentenceSizes[i - 1],
            )
        }
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
