package app.abcvorschule.ui.exercise

import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SoundSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundPositionLogicTest {
    private val middleRound = SoundPositionRound(
        promptTts = "Wo versteckt sich das Mmm?",
        atomId = "ameise",
        slot = SoundSlot.middle,
        missTts = "A - Mmm - eise.",
    )

    @Test
    fun wagonsRunFrontToBack() {
        assertEquals(
            listOf(SoundSlot.start, SoundSlot.middle, SoundSlot.end),
            SoundPositionLogic.SlotOrder,
        )
    }

    @Test
    fun onlyTheAuthoredSlotIsCorrect() {
        assertTrue(SoundPositionLogic.isCorrect(middleRound, SoundSlot.middle))
        assertFalse(SoundPositionLogic.isCorrect(middleRound, SoundSlot.start))
        assertFalse(SoundPositionLogic.isCorrect(middleRound, SoundSlot.end))
    }

    @Test
    fun startAndEndRoundsAreDistinguished() {
        val start = middleRound.copy(atomId = "maus", slot = SoundSlot.start)
        val end = middleRound.copy(atomId = "baum", slot = SoundSlot.end)
        assertTrue(SoundPositionLogic.isCorrect(start, SoundSlot.start))
        assertFalse(SoundPositionLogic.isCorrect(start, SoundSlot.end))
        assertTrue(SoundPositionLogic.isCorrect(end, SoundSlot.end))
    }

    @Test
    fun slotKeysRoundTrip() {
        SoundSlot.entries.forEach { slot ->
            assertEquals(slot, SoundPositionLogic.slotFromKey(SoundPositionLogic.slotKey(slot)))
        }
        assertNull(SoundPositionLogic.slotFromKey("not-a-wagon"))
    }
}
