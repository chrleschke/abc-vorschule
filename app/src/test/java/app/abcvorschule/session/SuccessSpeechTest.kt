package app.abcvorschule.session

import app.abcvorschule.content.Atom
import app.abcvorschule.content.AtomKind
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.content.CountAddSpec
import app.abcvorschule.content.SentencePictureRound
import app.abcvorschule.ui.rewards.PraisePhrases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SuccessSpeechTest {
    private val pack: ContentPack = ContentRepository.fromClasspath().load()

    private val ameise = Atom(
        id = "ameise",
        lemma = "Ameise",
        display = "Ameise",
        emoji = "🐜",
        kind = AtomKind.other,
        pluralDisplay = "Ameisen",
    )

    private val countRound = CountAddRound(
        promptTts = "Wie viele?",
        iconAtomId = ameise.id,
        left = 1,
        right = 1,
        answer = 2,
    )

    @Test
    fun countAddSuccessSpeaksAnswerBeforePraise() {
        val parts = SuccessSpeech.partsForRound(countRound, packWithAmeise(), praise = true)
        assertEquals(2, parts.size)
        assertEquals("2 Ameisen", parts[0])
        assertTrue(parts[1] in PraisePhrases.All)
    }

    @Test
    fun countAddResolveSpeaksOnlyAnswer() {
        val parts = SuccessSpeech.partsForRound(countRound, packWithAmeise(), praise = false)
        assertEquals(listOf("2 Ameisen"), parts)
    }

    @Test
    fun everyShippedCountAddRoundHasExtractableSpokenAnswer() {
        pack.tasks.values.filterIsInstance<CountAddSpec>().forEach { spec ->
            spec.rounds.forEach { round ->
                val parts = SuccessSpeech.partsForRound(round, pack, praise = false)
                assertEquals(1, parts.size)
                assertTrue(parts[0].isNotBlank())
            }
        }
    }

    private fun packWithAmeise(): ContentPack = pack.copy(
        atoms = pack.atoms + (ameise.id to ameise),
    )

    @Test
    fun sentencePictureSuccessRepeatsTheSentence() {
        val round = SentencePictureRound(
            promptTts = "Tom hat Opa gerufen.",
            correctAtomIds = listOf("tom", "opa"),
            wrongAtomIds = listOf("tom", "oma"),
        )
        assertEquals(
            listOf("Tom hat Opa gerufen."),
            SuccessSpeech.partsForRound(round, pack, praise = false),
        )
    }
}
