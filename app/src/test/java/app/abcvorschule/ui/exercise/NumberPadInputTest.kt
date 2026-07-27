package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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
}
