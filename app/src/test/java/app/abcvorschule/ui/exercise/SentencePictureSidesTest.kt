package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentencePictureSidesTest {

    @Test
    fun sideIsDeterministicPerSeed() {
        (-5..5).forEach { seed ->
            assertEquals(
                SentencePictureSides.correctOnLeft(seed),
                SentencePictureSides.correctOnLeft(seed),
            )
        }
    }

    @Test
    fun sidesAreRoughlyBalancedOverManySeeds() {
        // Sätze liefern beliebige String-Hashes; über viele Seeds darf keine
        // Seite dominieren, sonst lernt das Kind "immer links tippen".
        val left = (0 until 1000).count { SentencePictureSides.correctOnLeft("Satz $it".hashCode()) }
        assertTrue("left=$left of 1000", left in 300..700)
    }

    @Test
    fun emojiShrinksWithMoreAtoms() {
        assertTrue(
            SentencePictureCardSizing.emojiSp(1) > SentencePictureCardSizing.emojiSp(2) &&
                SentencePictureCardSizing.emojiSp(2) > SentencePictureCardSizing.emojiSp(3),
        )
    }
}
