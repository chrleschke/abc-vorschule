package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Test

class SyllableMergeSpeechTest {
    private val roAtom = Atom(
        id = "ro",
        lemma = "roh",
        display = "ro",
        emoji = "",
        kind = AtomKind.syllable,
    )
    private val naAtom = Atom(
        id = "na",
        lemma = "na",
        display = "na",
        emoji = "",
        kind = AtomKind.syllable,
    )

    private fun round(resultAtomId: String, resultDisplay: String) = SyllableMergeRound(
        promptTts = "Schiebe r und o zusammen.",
        leftAtomId = "r",
        leftDisplay = "r",
        rightAtomId = "o",
        rightDisplay = "o",
        resultAtomId = resultAtomId,
        resultDisplay = resultDisplay,
        stretchTts = "rrrrr",
    )

    @Test
    fun resultSpeechUsesLemmaWhenDisplayDiffers() {
        assertEquals("roh", SyllableMergeSpeech.resultSpeech(round("ro", "ro"), roAtom))
    }

    @Test
    fun resultSpeechUsesLemmaWhenItMatchesDisplay() {
        assertEquals("na", SyllableMergeSpeech.resultSpeech(round("na", "na"), naAtom))
    }

    @Test
    fun resultSpeechFallsBackToDisplayWithoutAtom() {
        assertEquals("ro", SyllableMergeSpeech.resultSpeech(round("ro", "ro"), null))
    }
}
