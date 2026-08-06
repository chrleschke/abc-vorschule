package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Test

class WordBuildPlacementSpeechTest {
    private val mama = listOf("Ma", "ma")

    @Test
    fun nonFinalPlacementSpeaksImmediately() {
        assertEquals(
            WordBuildPlacementSpeech.BlockSpeechMode.Immediate,
            WordBuildPlacementSpeech.blockSpeechMode(emptyMap(), 0, "Ma", mama),
        )
        assertEquals(
            WordBuildPlacementSpeech.BlockSpeechMode.Immediate,
            WordBuildPlacementSpeech.blockSpeechMode(mapOf(0 to "Ma"), 1, "x", mama),
        )
    }

    @Test
    fun finalPlacementMustAwaitBlockBeforeSuccessSpeech() {
        assertEquals(
            WordBuildPlacementSpeech.BlockSpeechMode.AwaitBeforeSuccess,
            WordBuildPlacementSpeech.blockSpeechMode(mapOf(0 to "Ma"), 1, "ma", mama),
        )
        assertEquals(
            WordBuildPlacementSpeech.BlockSpeechMode.AwaitBeforeSuccess,
            WordBuildPlacementSpeech.blockSpeechMode(emptyMap(), 0, "Ma", listOf("Ma")),
        )
    }
}
