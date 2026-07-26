package app.abcvorschule.ui.exercise

import app.abcvorschule.progress.ScaffoldLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathHintingTest {
    @Test
    fun nearVersusFarHintsDiffer() {
        assertEquals("near", MathHinting.hintKey(5, 4))
        assertEquals("far", MathHinting.hintKey(5, 1))
        assertTrue(MathHinting.hintText(5, 4) != MathHinting.hintText(5, 1))
        assertEquals("Versuch es noch einmal", MathHinting.missFeedback(null))
        assertEquals(MathHinting.hintText(5, 4), MathHinting.missFeedback(1))
    }

    @Test
    fun numberPadOnlyOnAdvancedScaffold() {
        assertTrue(MathHinting.usesNumberPad(ScaffoldLevel.Advanced))
        assertFalse(MathHinting.usesNumberPad(ScaffoldLevel.Beginner))
    }

    @Test
    fun threeChoicesAlwaysExactlyThreeIncludingAnswer() {
        val choices = MathHinting.threeChoices(4)
        assertEquals(3, choices.size)
        assertTrue(choices.contains(4))
        assertEquals(choices.toSet().size, choices.size)
    }
}
