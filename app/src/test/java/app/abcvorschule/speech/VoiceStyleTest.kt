package app.abcvorschule.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStyleTest {
    @Test
    fun normalIsExactlyOne() {
        assertEquals(1f, VoiceStyle.Normal.pitch)
    }

    @Test
    fun theTwoMonstersSitOnEitherSideOfNormal() {
        assertTrue(VoiceStyle.MonsterLow.pitch < 1f)
        assertTrue(VoiceStyle.MonsterHigh.pitch > 1f)
        // Android akzeptiert Tonhöhen in [0.5, 2.0]; darüber hinaus klingt nichts mehr wie Sprache.
        VoiceStyle.entries.forEach { assertTrue(it.pitch in 0.5f..2f) }
    }

    @Test
    fun aSpokenPartDefaultsToTheNormalVoice() {
        assertEquals(VoiceStyle.Normal, SpokenPart("Sonne").voice)
    }
}
