package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NumberPadInputTest {
    @Test
    fun sanitizeKeepsDigitsOnly() {
        assertEquals("12", NumberPadInput.sanitize("1a2"))
        assertEquals("", NumberPadInput.sanitize("-,."))
    }

    @Test
    fun sanitizeCapsAtMaxDigits() {
        assertEquals("123", NumberPadInput.sanitize("123456"))
        assertEquals(3, NumberPadInput.MaxDigits)
    }

    @Test
    fun tokenChangesOnEveryMissSoTheFieldClears() {
        val first = NumberPadInput.resetToken("r1", 0)
        val afterMiss = NumberPadInput.resetToken("r1", 1)
        assertNotEquals(first, afterMiss)
    }

    @Test
    fun tokenChangesOnANewRound() {
        assertNotEquals(
            NumberPadInput.resetToken("r1", 0),
            NumberPadInput.resetToken("r2", 0),
        )
    }

    @Test
    fun tokenIsStableWhileNothingChanged() {
        // A correct answer leaves roundKey and misses untouched, so the green
        // confirmation keeps showing the number the child actually typed.
        assertEquals(
            NumberPadInput.resetToken("r1", 2),
            NumberPadInput.resetToken("r1", 2),
        )
    }

    // --- Spiegelung der Zähl-Hilfe ---------------------------------------------

    @Test
    fun theCountedValueIsMirroredIntoTheField() {
        assertEquals("7", NumberPadInput.mirroredValue(7))
        assertEquals("0", NumberPadInput.mirroredValue(0))
    }

    @Test
    fun takingEveryTapBackClearsTheField() {
        // Der Fehler, den das festnagelt: der Spiegel-Effect schrieb nur bei
        // Nicht-Null, also blieb die zuletzt gezählte Zahl im Antwortfeld stehen,
        // nachdem das Kind alle Tipps in der Zähl-Hilfe zurückgenommen hatte —
        // absendbar, ohne dass sie noch irgendetwas zählte.
        assertEquals("", NumberPadInput.mirroredValue(null))
    }

    // --- field width vs. system font scale -------------------------------------

    /** displayLarge from ui/theme/Theme.kt — the style the field renders in. */
    private val displayLargeSp = 40f

    @Test
    fun theFieldKeepsItsShippedWidthUpToTheTestDeviceScale() {
        // Nothing may shift at 1.0 or on the font_scale-1.3 test device: the
        // derived minimum only overtakes the 140dp floor once it actually must.
        assertEquals(140f, NumberPadInput.fieldWidthDp(displayLargeSp, 1f), 0.01f)
        assertEquals(140f, NumberPadInput.fieldWidthDp(displayLargeSp, 1.3f), 0.01f)
    }

    @Test
    fun theFieldHoldsTheLongestAnswerAtEveryFontScale() {
        // The regression this pins: the fixed 140dp field could not show two
        // displayLarge digits at font_scale 2.0, let alone MaxDigits.
        listOf(1f, 1.3f, 2f).forEach { scale ->
            val width = NumberPadInput.fieldWidthDp(displayLargeSp, scale)
            val digits = NumberPadInput.MaxDigits * displayLargeSp * scale * NumberPadInput.DigitAspect
            assertTrue(
                "at scale $scale ${digits}dp of digits must fit ${width}dp",
                digits <= width - NumberPadInput.FieldPaddingDp + 0.01f,
            )
        }
    }

    @Test
    fun theFieldWidthGrowsMonotonicallyWithTheScale() {
        assertTrue(
            NumberPadInput.fieldWidthDp(displayLargeSp, 2f) >
                NumberPadInput.fieldWidthDp(displayLargeSp, 1.3f) - 0.01f,
        )
    }
}
