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
        assertEquals(24, PlaceValueInput.combine("2", "4", 2))
        assertEquals(30, PlaceValueInput.combine("3", "0", 2))
        assertEquals(7, PlaceValueInput.combine("", "7", 1))
        assertNull(PlaceValueInput.combine("2", "", 2))
        assertNull(PlaceValueInput.combine("", "", 1))
        assertEquals(5, PlaceValueInput.combine("0", "5", 2))
    }

    @Test
    fun combineAndIsCompleteNeverDisagree() {
        // Eine halb gefüllte Zwei-Feld-Antwort ist keine Zahl, und ein Rest im
        // Zehnerfeld darf eine Ein-Feld-Antwort nicht verzehnfachen.
        assertNull(PlaceValueInput.combine("", "4", 2))
        assertEquals(4, PlaceValueInput.combine("2", "4", 1))
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
    fun theSlotGrowsWithTheSystemFontSoADigitNeverClips() {
        val small = PlaceValueInput.slotWidthDp(textSp = 40f, fontScale = 1f)
        val large = PlaceValueInput.slotWidthDp(textSp = 40f, fontScale = 2f)
        assertTrue("$small / $large", large > small)
        assertTrue(small >= PlaceValueInput.MinSlotWidthDp)
    }

    @Test
    fun atDoubleSystemFontTheBoxStillHoldsItsOwnDigit() {
        // Der Fall, an dem die vorherigen 24dp Innenabstand scheiterten: displayLarge
        // (40sp) bei font_scale 2.0. Der Kasten muss den Glyphenvorschub **plus**
        // den M3-Innenabstand fassen, sonst beschneidet er die Ziffer, die das Kind
        // gerade getippt hat — und eine Zahl, die es nicht sieht, kann es nicht
        // prüfen.
        val textSp = 40f
        val fontScale = 2f
        val width = PlaceValueInput.slotWidthDp(textSp = textSp, fontScale = fontScale)
        val glyph = textSp * fontScale * PlaceValueInput.DigitAspect
        assertTrue(
            "Kasten ${width}dp fasst Glyph ${glyph}dp plus Innenabstand nicht",
            width >= glyph + PlaceValueInput.SlotPaddingDp,
        )
        // Und er passt neben seinen Zwilling, das Rechenzeichen und den Pfeil auf
        // die 320dp-Breite des schmalsten angenommenen Geräts: 12dp Bühnenrand je
        // Seite, 32dp Zeichenspalte, 72dp Pfeil, dreimal 8dp Spaltenlücke.
        assertTrue("zwei Kästen zu ${width}dp passen nicht auf 320dp", 2 * width + 128 <= 296)
    }

    @Test
    fun everyWrongTryClearsTheFields() {
        val round = "t7#0-add-16-8"
        assertEquals(PlaceValueInput.resetToken(round, 0), PlaceValueInput.resetToken(round, 0))
        assertTrue(PlaceValueInput.resetToken(round, 0) != PlaceValueInput.resetToken(round, 1))
    }
}
