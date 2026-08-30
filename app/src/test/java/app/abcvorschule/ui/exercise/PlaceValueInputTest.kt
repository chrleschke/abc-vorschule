package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaceValueInputTest {
    @Test
    fun twoFieldsOnceTheAnswerHasATensDigit() {
        // Verrät nichts: wie viele Objekte es sind, steht ohnehin im Bild — die
        // Felderzahl folgt nur dem, was das Kind schon sieht.
        assertEquals(1, PlaceValueInput.fieldCount(5))
        assertEquals(1, PlaceValueInput.fieldCount(9))
        assertEquals(2, PlaceValueInput.fieldCount(10))
        assertEquals(2, PlaceValueInput.fieldCount(24))
        assertEquals(2, PlaceValueInput.fieldCount(30))
    }

    @Test
    fun aFieldHoldsExactlyOneDigitAndTheNewestWins() {
        // Tippt das Kind in ein gefülltes Feld, überschreibt die neue Ziffer die
        // alte, statt sich anzuhängen — Löschen wäre ein zweiter Handgriff.
        assertEquals("", PlaceValueInput.lastDigit(""))
        assertEquals("4", PlaceValueInput.lastDigit("4"))
        assertEquals("5", PlaceValueInput.lastDigit("25"))
        assertEquals("7", PlaceValueInput.lastDigit("2a7"))
        assertEquals("", PlaceValueInput.lastDigit("abc"))
    }

    @Test
    fun bothFieldsTogetherAreTheAnswer() {
        assertEquals(24, PlaceValueInput.combine("2", "4"))
        assertEquals(30, PlaceValueInput.combine("3", "0"))
        assertEquals(7, PlaceValueInput.combine("", "7"))
        assertNull(PlaceValueInput.combine("2", ""))
        assertNull(PlaceValueInput.combine("", ""))
    }

    @Test
    fun sendingWaitsUntilEveryFieldIsFilled() {
        assertFalse(PlaceValueInput.isComplete("", "", 2))
        assertFalse(PlaceValueInput.isComplete("2", "", 2))
        assertFalse(PlaceValueInput.isComplete("", "4", 2))
        assertTrue(PlaceValueInput.isComplete("2", "4", 2))
        assertTrue(PlaceValueInput.isComplete("", "7", 1))
        assertFalse(PlaceValueInput.isComplete("", "", 1))
    }

    @Test
    fun aResolvedAnswerSplitsBackIntoItsFields() {
        assertEquals("2" to "4", PlaceValueInput.digitsOf(24, 2))
        assertEquals("3" to "0", PlaceValueInput.digitsOf(30, 2))
        assertEquals("" to "7", PlaceValueInput.digitsOf(7, 1))
    }

    @Test
    fun theSlotGrowsWithTheSystemFontSoADigitNeverClips() {
        val small = PlaceValueInput.slotWidthDp(textSp = 40f, fontScale = 1f)
        val large = PlaceValueInput.slotWidthDp(textSp = 40f, fontScale = 2f)
        assertTrue("$small / $large", large > small)
        assertTrue(small >= PlaceValueInput.MinSlotWidthDp)
    }

    @Test
    fun everyWrongTryClearsTheFields() {
        val round = "t7#0-add-16-8"
        assertEquals(PlaceValueInput.resetToken(round, 0), PlaceValueInput.resetToken(round, 0))
        assertTrue(PlaceValueInput.resetToken(round, 0) != PlaceValueInput.resetToken(round, 1))
    }
}
