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
        val finale = pack.finale("f-l01").copy(pictureAtomIds = listOf("mama", "ist", "apfel"))
        assertEquals(listOf("mama", "apfel"), FinaleLayout.picturesOf(pack, finale).map { it.atomId })
    }

    @Test
    fun picturesSkipUnknownAtoms() {
        val finale = pack.finale("f-l01").copy(pictureAtomIds = listOf("mama", "ghost"))
        assertEquals(listOf("mama"), FinaleLayout.picturesOf(pack, finale).map { it.atomId })
    }

    @Test
    fun everyShippedFinaleRendersAllItsPictures() {
        assertEquals(26, pack.finales.size)
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
    fun headerSizesAtDefaultFontScaleMatchHeadlineMedium() {
        // Pins the no-regression case for the header, added alongside the sentence
        // fix: headlineMedium's own 28sp / 34sp (ui/theme/Theme.kt).
        assertEquals(28, FinaleLayout.headerSizeSp(fontScale = 1f))
        assertEquals(34, FinaleLayout.headerLineHeightSp(fontScale = 1f))
    }

    @Test
    fun sentenceLineHeightAtDefaultFontScaleMatchesHeadlineSmall() {
        assertEquals(32, FinaleLayout.sentenceLineHeightSp(fontScale = 1f))
    }

    @Test
    fun sizesBelowFontScaleOneAlsoStayUnchanged() {
        // A user who shrinks system text should not see anything special here either —
        // only fontScale > 1.0 triggers the give-back-enlargement behaviour.
        assertEquals(64, FinaleLayout.pictureSizeSp(2, fontScale = 0.85f))
        assertEquals(24, FinaleLayout.sentenceSizeSp(fontScale = 0.85f))
        assertEquals(28, FinaleLayout.headerSizeSp(fontScale = 0.85f))
    }

    @Test
    fun zeroOrNegativeFontScaleFallsBackToTheBaseSizeInsteadOfDividingByIt() {
        // Guards the `fontScale <= 1f` branch specifically: a later refactor to
        // `fontScale < 1f` would divide by zero here instead of short-circuiting.
        assertEquals(64, FinaleLayout.pictureSizeSp(2, fontScale = 0f))
        assertEquals(24, FinaleLayout.sentenceSizeSp(fontScale = 0f))
        assertEquals(64, FinaleLayout.pictureSizeSp(2, fontScale = -1f))
        assertEquals(24, FinaleLayout.sentenceSizeSp(fontScale = -1f))
    }

    @Test
    fun largeFontScaleShrinksPicturesSentenceAndHeader() {
        assertTrue(FinaleLayout.pictureSizeSp(2, fontScale = 2f) < 64)
        assertTrue(FinaleLayout.sentenceSizeSp(fontScale = 2f) < 24)
        assertTrue(FinaleLayout.sentenceLineHeightSp(fontScale = 2f) < 32)
        assertTrue(FinaleLayout.headerSizeSp(fontScale = 2f) < 28)
        assertTrue(FinaleLayout.headerLineHeightSp(fontScale = 2f) < 34)
    }

    @Test
    fun crowdedPictureTierAlsoShrinksAboveFontScaleOne() {
        // Every other fontScale assertion in this file exercises the two-or-three
        // tier (base 64sp); this pins the four-or-more tier (base 52sp) too, so a bug
        // that ignored `count` inside the cap path would not slip through.
        assertTrue(FinaleLayout.pictureSizeSp(4, fontScale = 2f) < 52)
        val effective = FinaleLayout.pictureSizeSp(4, fontScale = 2f) * 2f
        assertTrue("crowded effective size $effective exceeds 52", effective <= 52f)
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
            val crowdedEffective = FinaleLayout.pictureSizeSp(4, scale) * scale
            assertTrue(
                "crowded picture effective size $crowdedEffective exceeds 52 at scale $scale",
                crowdedEffective <= 52f,
            )
            val sentenceEffective = FinaleLayout.sentenceSizeSp(scale) * scale
            assertTrue(
                "sentence effective size $sentenceEffective exceeds 24 at scale $scale",
                sentenceEffective <= 24f,
            )
            val sentenceLineHeightEffective = FinaleLayout.sentenceLineHeightSp(scale) * scale
            assertTrue(
                "sentence line height effective size $sentenceLineHeightEffective exceeds 32 at scale $scale",
                sentenceLineHeightEffective <= 32f,
            )
            val headerEffective = FinaleLayout.headerSizeSp(scale) * scale
            assertTrue(
                "header effective size $headerEffective exceeds 28 at scale $scale",
                headerEffective <= 28f,
            )
            val headerLineHeightEffective = FinaleLayout.headerLineHeightSp(scale) * scale
            assertTrue(
                "header line height effective size $headerLineHeightEffective exceeds 34 at scale $scale",
                headerLineHeightEffective <= 34f,
            )
        }
    }

    @Test
    fun sizesAreMonotonicAsFontScaleGrows() {
        // A larger scale must never return a larger sp value than a smaller one.
        val scales = listOf(0.85f, 1f, 1.15f, 1.3f, 1.8f, 2f, 3f, 5f)
        val pictureSizes = scales.map { FinaleLayout.pictureSizeSp(2, it) }
        val crowdedPictureSizes = scales.map { FinaleLayout.pictureSizeSp(4, it) }
        val sentenceSizes = scales.map { FinaleLayout.sentenceSizeSp(it) }
        val sentenceLineHeights = scales.map { FinaleLayout.sentenceLineHeightSp(it) }
        val headerSizes = scales.map { FinaleLayout.headerSizeSp(it) }
        val headerLineHeights = scales.map { FinaleLayout.headerLineHeightSp(it) }
        for (i in 1 until scales.size) {
            assertTrue(
                "picture size grew from ${pictureSizes[i - 1]} to ${pictureSizes[i]} " +
                    "between scale ${scales[i - 1]} and ${scales[i]}",
                pictureSizes[i] <= pictureSizes[i - 1],
            )
            assertTrue(
                "crowded picture size grew from ${crowdedPictureSizes[i - 1]} to " +
                    "${crowdedPictureSizes[i]} between scale ${scales[i - 1]} and ${scales[i]}",
                crowdedPictureSizes[i] <= crowdedPictureSizes[i - 1],
            )
            assertTrue(
                "sentence size grew from ${sentenceSizes[i - 1]} to ${sentenceSizes[i]} " +
                    "between scale ${scales[i - 1]} and ${scales[i]}",
                sentenceSizes[i] <= sentenceSizes[i - 1],
            )
            assertTrue(
                "sentence line height grew from ${sentenceLineHeights[i - 1]} to " +
                    "${sentenceLineHeights[i]} between scale ${scales[i - 1]} and ${scales[i]}",
                sentenceLineHeights[i] <= sentenceLineHeights[i - 1],
            )
            assertTrue(
                "header size grew from ${headerSizes[i - 1]} to ${headerSizes[i]} " +
                    "between scale ${scales[i - 1]} and ${scales[i]}",
                headerSizes[i] <= headerSizes[i - 1],
            )
            assertTrue(
                "header line height grew from ${headerLineHeights[i - 1]} to " +
                    "${headerLineHeights[i]} between scale ${scales[i - 1]} and ${scales[i]}",
                headerLineHeights[i] <= headerLineHeights[i - 1],
            )
        }
    }

    @Test
    fun pictureRowFitsTheNarrowestSupportedContentWidthForEveryValidatorPermittedCount() {
        // 320dp device minus the column's 24dp horizontal padding on each side — the
        // narrowest content width used throughout this task's budget arithmetic. The
        // validator (ContentValidator.MinFinalePictures..MaxFinalePictures) permits
        // 2..4 pictures per finale; a bug that only ever exercised 2-3 in tests would
        // miss a 4-picture finale that doesn't fit.
        val narrowestContentWidthDp = 272
        (2..4).forEach { count ->
            val width = FinaleLayout.pictureRowWidthDp(count, fontScale = 1f)
            assertTrue(
                "row of $count pictures is ${width}dp wide, narrowest supported content " +
                    "is ${narrowestContentWidthDp}dp",
                width <= narrowestContentWidthDp,
            )
        }
    }

    @Test
    fun pictureRowWidthIsZeroForNoPictures() {
        assertEquals(0, FinaleLayout.pictureRowWidthDp(0, fontScale = 1f))
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
