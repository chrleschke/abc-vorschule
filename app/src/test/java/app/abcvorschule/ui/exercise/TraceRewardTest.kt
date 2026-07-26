package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TraceRewardTest {
    @Test
    fun readsTheWordFromAnAuthoredRewardLine() {
        assertEquals("Tomate", TraceReward.wordOf("T wie Tomate."))
    }

    @Test
    fun toleratesMissingPunctuationAndStrayWhitespace() {
        assertEquals("Igel", TraceReward.wordOf("I wie  Igel "))
    }

    @Test
    fun keepsMultiWordObjects() {
        assertEquals("rote Rose", TraceReward.wordOf("R wie rote Rose."))
    }

    @Test
    fun returnsNullWhenTheLineDoesNotFollowThePattern() {
        // The trainer falls back to showing the raw line rather than an empty label.
        assertNull(TraceReward.wordOf("Das ist ein T."))
        assertNull(TraceReward.wordOf("T wie "))
        assertNull(TraceReward.wordOf(""))
    }
}
