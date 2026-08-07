package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Test

class SentencePictureSpeechTest {

    private val round = SentencePictureRound(
        promptTts = "Tom hat Opa gerufen.",
        correctAtomIds = listOf("tom", "opa"),
        wrongAtomIds = listOf("tom", "oma"),
    )
    private val spec = SentencePictureSpec(
        id = "l03-sp1",
        instructionTts = "Ordne das richtige Bild zu.",
        rounds = listOf(round),
    )

    @Test
    fun firstRoundSpeaksInstructionThenSentence() {
        assertEquals(
            listOf("Ordne das richtige Bild zu.", "Tom hat Opa gerufen."),
            SentencePictureSpeech.promptParts(spec, round, roundIndex = 0),
        )
    }

    @Test
    fun laterRoundsSpeakOnlyTheSentence() {
        assertEquals(
            listOf("Tom hat Opa gerufen."),
            SentencePictureSpeech.promptParts(spec, round, roundIndex = 1),
        )
    }

    @Test
    fun missingSpecStillSpeaksTheSentence() {
        assertEquals(
            listOf("Tom hat Opa gerufen."),
            SentencePictureSpeech.promptParts(null, round, roundIndex = 0),
        )
    }

    @Test
    fun scoredAtomsAreTheCorrectCardDeduplicated() {
        val plural = round.copy(correctAtomIds = listOf("ei", "ei"))
        assertEquals(listOf("ei"), plural.scoredAtomIds())
    }
}
