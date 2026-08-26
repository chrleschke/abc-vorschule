package app.abcvorschule.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GermanNumberWordTest {
    @Test
    fun everyQuantityInTheCurriculumHasAWord() {
        assertEquals(30, GermanNumberWord.MaxWord)
        (0..GermanNumberWord.MaxWord).forEach { value ->
            assertFalse("$value", GermanNumberWord.of(value).any(Char::isDigit))
        }
    }

    @Test
    fun theIrregularOnesAreSpelledTheWayGermanActuallySpellsThem() {
        assertEquals("sechzehn", GermanNumberWord.of(16))
        assertEquals("siebzehn", GermanNumberWord.of(17))
        assertEquals("einundzwanzig", GermanNumberWord.of(21))
        assertEquals("siebenundzwanzig", GermanNumberWord.of(27))
        assertEquals("dreißig", GermanNumberWord.of(30))
    }

    @Test
    fun beyondTheCurriculumTheNumeralSurvivesAsItself() {
        // Eine getippte Antwort darf drei Ziffern haben (NumberPadInput.MaxDigits).
        // Ohne folgenden Punkt liest die Engine sie als Kardinalzahl.
        assertEquals("123", GermanNumberWord.of(123))
    }

    @Test
    fun noSpokenNumberEverEndsInADigitFollowedByAPeriod() {
        // Der eigentliche Regressionsschutz: "8." ist im Deutschen die Ordinalzahl
        // und wird als "achte" gelesen. Genau daran krankte das Miss-Echo.
        (0..999).forEach { value ->
            val spoken = GermanNumberWord.of(value)
            assertFalse("$value -> $spoken", spoken.endsWith("."))
        }
        assertTrue(GermanNumberWord.of(8) == "acht")
    }
}
