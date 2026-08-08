package app.abcvorschule.speech

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verfügbarkeits-Semantik von [speechAvailable]: die App ist sprechfähig,
 * sobald irgendein Ausgabeweg existiert — kuratierte Clips brauchen keine
 * deutsche TTS-Stimme. Nur wenn beides fehlt, greifen die visuellen
 * No-Speech-Fallbacks in TaskShell/TrainerHost.
 */
class SpeechAvailabilityTest {
    @Test
    fun clipsAloneMakeSpeechAvailable() {
        assertTrue(speechAvailable(languageOk = false, clipCount = 1))
    }

    @Test
    fun germanVoiceAloneMakesSpeechAvailable() {
        assertTrue(speechAvailable(languageOk = true, clipCount = 0))
    }

    @Test
    fun withoutClipsAndWithoutGermanVoiceNothingCanSpeak() {
        assertFalse(speechAvailable(languageOk = false, clipCount = 0))
    }

    @Test
    fun emptyClipIndexDoesNotCountAsClips() {
        assertFalse(speechAvailable(languageOk = false, clipCount = ClipIndex.empty().size))
    }
}
