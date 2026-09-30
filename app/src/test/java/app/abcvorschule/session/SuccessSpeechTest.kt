package app.abcvorschule.session

import app.abcvorschule.content.Atom
import app.abcvorschule.content.AtomKind
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.content.CountAddSpec
import app.abcvorschule.content.Gender
import app.abcvorschule.content.NounClass
import app.abcvorschule.content.SentencePictureRound
import app.abcvorschule.content.SymbolHuntMode
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.content.SymbolInWordMode
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.content.WordBlock
import app.abcvorschule.content.WordBuildRound
import app.abcvorschule.speech.ClipIndex
import app.abcvorschule.speech.GermanNumberWord
import app.abcvorschule.ui.rewards.PraisePhrases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SuccessSpeechTest {
    private val pack: ContentPack = ContentRepository.fromClasspath().load()

    private val countRound = CountAddRound(
        promptTts = "Wie viel ist drei plus zwei?",
        left = 3,
        right = 2,
        answer = 5,
    )

    @Test
    fun countAddSuccessSpeaksPraiseBeforeAnswer() {
        // §7: das Lob steht vor der Antwort, damit die Zahl das Letzte ist,
        // was das Kind hört ("Ausgezeichnet! fünf").
        val parts = SuccessSpeech.partsForRound(countRound, pack, praise = true)
        assertEquals(2, parts.size)
        assertTrue(parts[0] in PraisePhrases.All)
        assertEquals("fünf", parts[1])
    }

    @Test
    fun countAddWithoutPraiseSpeaksOnlyTheNumberWord() {
        val parts = SuccessSpeech.partsForRound(countRound, pack, praise = false)
        assertEquals(listOf("fünf"), parts)
    }

    @Test
    fun everyShippedCountAddAnswerIsSpokenAsANumberWordWithAClip() {
        // Die Ziffer ("5") hat keinen Clip — nach dem kuratierten Lob sprang sonst
        // Android-TTS ein. Das Zahlwort ist der Clip des Zählkanals.
        val index = ClipIndex.load { path ->
            javaClass.classLoader!!.getResourceAsStream(path)
                ?: throw java.io.FileNotFoundException(path)
        }
        pack.tasks.values.filterIsInstance<CountAddSpec>().forEach { spec ->
            spec.rounds.forEach { round ->
                val parts = SuccessSpeech.partsForRound(round, pack, praise = false)
                assertEquals(listOf(GermanNumberWord.of(round.answer)), parts)
                assertNotNull("${spec.id}: no clip for ${parts[0]}", index.lookup(parts[0]))
            }
        }
    }

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

    /** Kontrolliertes Substantiv-Atom: Task 4 liefert Genus/NounClass erst später. */
    private val haus = Atom(
        id = "test-haus",
        lemma = "Haus",
        display = "Haus",
        emoji = "🏠",
        kind = AtomKind.word,
        gender = Gender.n,
        nounClass = NounClass.thing,
    )

    private fun packWithHaus(): ContentPack = pack.copy(
        atoms = pack.atoms + (haus.id to haus),
    )

    @Test
    fun wordBuildSuccessSpeaksArticleWithTargetWord() {
        val round = WordBuildRound(
            promptTts = "Baue das Wort Haus",
            targetAtomId = haus.id,
            blocks = listOf(WordBlock(atomId = haus.id, display = "Haus")),
        )
        assertEquals(
            listOf("das Haus"),
            SuccessSpeech.partsForRound(round, packWithHaus(), praise = false),
        )
    }

    @Test
    fun symbolInWordSuccessSpeaksArticleWithWord() {
        val round = SymbolInWordRound(
            promptTts = "Finde H",
            wordAtomId = haus.id,
            targetAtomId = "letter-h",
            mode = SymbolInWordMode.letter,
            segments = listOf("H", "a", "u", "s"),
            targetIndices = listOf(0),
        )
        assertEquals(
            listOf("das Haus"),
            SuccessSpeech.partsForRound(round, packWithHaus(), praise = false),
        )
    }

    @Test
    fun wordBuildSuccessKeepsBareDisplayForUnclassifiedTarget() {
        // "ich" trägt im ausgelieferten Pack keine NounClass — bleibt unverändert nackt.
        val round = WordBuildRound(
            promptTts = "Baue das Wort ich",
            targetAtomId = "ich",
            blocks = listOf(WordBlock(atomId = "ich", display = "ich")),
        )
        assertEquals(listOf("ich"), SuccessSpeech.partsForRound(round, pack, praise = false))
    }

    @Test
    fun symbolHuntSuccessSpeaksBareGraphemeWithoutArticle() {
        // §7: Trainer 2 spricht das Graphem, nie einen Artikel — dieser Zweig bleibt unangetastet.
        val round = SymbolHuntRound(
            promptTts = "Finde M",
            targetAtomId = "letter-m",
            mode = SymbolHuntMode.letter,
            distractorPool = listOf("N", "W"),
        )
        assertEquals(listOf("M"), SuccessSpeech.partsForRound(round, pack, praise = false))
    }
}
