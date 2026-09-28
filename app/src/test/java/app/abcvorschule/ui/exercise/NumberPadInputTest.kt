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

    // --- field width vs. system font scale -------------------------------------

    /** displayLarge from ui/theme/Theme.kt — the style the field renders in. */
    private val displayLargeSp = 40f

    @Test
    fun theFieldKeepsItsShippedWidthAtNormalScaleAndGrowsOnlyALittleOnTheTestDevice() {
        // Bei 1.0 verschiebt sich nichts. Auf dem font_scale-1.3-Testgerät wächst das
        // Feld seit der Lernschrift um wenige dp: ihre fetten Ziffern sind breiter als
        // die früheren Serif-Ziffern (NumberPadInput.DigitAspect), und drei davon müssen
        // hineinpassen.
        assertEquals(140f, NumberPadInput.fieldWidthDp(displayLargeSp, 1f), 0.01f)
        val onTestDevice = NumberPadInput.fieldWidthDp(displayLargeSp, 1.3f)
        assertTrue("1.3: $onTestDevice", onTestDevice in 140f..150f)
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

    @Test
    fun keysAppendDigitsUpToTheMaximum() {
        assertEquals("1", NumberPadInput.append("", 1))
        assertEquals("12", NumberPadInput.append("1", 2))
        assertEquals("123", NumberPadInput.append("12", 3))
        assertEquals("123", NumberPadInput.append("123", 4))
    }

    /** „07" ist keine Zahl, die ein Kind schreiben soll. */
    @Test
    fun aLeadingZeroIsReplacedNotPrepended() {
        assertEquals("0", NumberPadInput.append("", 0))
        assertEquals("7", NumberPadInput.append("0", 7))
        assertEquals("10", NumberPadInput.append("1", 0))
    }

    @Test
    fun backspaceRemovesTheLastDigitAndStopsAtEmpty() {
        assertEquals("1", NumberPadInput.backspace("12"))
        assertEquals("", NumberPadInput.backspace("1"))
        assertEquals("", NumberPadInput.backspace(""))
    }

    @Test
    fun theKeyRowsHoldEveryDigitOnceInTwoRowsOfFive() {
        assertEquals((0..9).toSet(), NumberPadInput.KeyRows.flatten().toSet())
        assertEquals(listOf(5, 5), NumberPadInput.KeyRows.map { it.size })
    }

    /** Fünf Tasten passen auf ein 320-dp-Telefon, ohne unter den Kinder-Boden zu fallen. */
    @Test
    fun fiveKeysFitANarrowPhoneAndStayTappable() {
        val narrow = NumberPadInput.keySizeDp(320f)
        assertTrue(narrow >= NumberPadInput.MinKeyDp)
        assertTrue(5 * narrow + 4 * NumberPadInput.KeyGapDp <= 320f + 0.01f)
        assertEquals(NumberPadInput.MaxKeyDp, NumberPadInput.keySizeDp(900f), 0f)
    }
}
