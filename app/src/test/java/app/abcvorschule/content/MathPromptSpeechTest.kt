package app.abcvorschule.content

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MathPromptSpeechTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun round(left: Int, right: Int, operation: String) = CountAddRound(
        promptTts = "Wie viel ist …?",
        left = left,
        right = right,
        answer = 0,
        operation = operation,
    )

    @Test
    fun thePromptIsTheIntroThenTheWholeTaskAsOneClip() {
        assertEquals(
            listOf("Wie viel ist", "drei plus zwei"),
            MathPromptSpeech.promptParts(round(3, 2, "add")),
        )
        assertEquals(
            listOf("Wie viel ist", "neun minus sechs"),
            MathPromptSpeech.promptParts(round(9, 6, "subtract")),
        )
        assertEquals(
            listOf("Wie viel ist", "drei mal vier"),
            MathPromptSpeech.promptParts(round(3, 4, "multiply")),
        )
        assertEquals("fünf plus zwanzig", MathPromptSpeech.taskText(round(5, 20, "add")))
    }

    @Test
    fun anUnknownOperationFallsBackToThePromptText() {
        val odd = round(3, 2, "divide")
        assertNull(MathPromptSpeech.taskText(odd))
        assertEquals(listOf(odd.promptTts), MathPromptSpeech.promptParts(odd))
    }

    @Test
    fun everyShippedRoundSaysWhatItsPromptTextSays() {
        // Die Teile kommen aus left/right/operation, der promptTts bleibt als lesbare
        // Fassung im Content. Laufen beide auseinander, hört das Kind eine andere
        // Aufgabe, als im Content steht.
        val rounds = shippedRounds()
        assertTrue(rounds.isNotEmpty())
        rounds.forEach { round ->
            val spoken = MathPromptSpeech.INTRO + " " + MathPromptSpeech.taskText(round) + "?"
            assertEquals(round.promptTts, spoken)
        }
    }

    @Test
    fun everyDistinctTaskHasItsOwnText() {
        // Jeder Text ist ein von Hand kuratierter Clip (tools/tts, Profil `math`): gleiche
        // Aufgabe → derselbe Clip, verschiedene Aufgaben → nie derselbe Text.
        val byTask = shippedRounds().groupBy { Triple(it.left, it.operation, it.right) }
            .mapValues { (_, rounds) -> rounds.map { MathPromptSpeech.taskText(it) }.toSet() }
        byTask.forEach { (task, texts) -> assertEquals("$task", 1, texts.size) }
        val texts = byTask.values.map { it.single() }
        assertEquals(texts.size, texts.toSet().size)
        assertEquals(48, texts.toSet().size)
    }

    @Test
    fun theIntroIsCuratedInTheMathProfile() {
        // Ohne Eintrag in extra-strings.json rendert tools/tts die Einleitung nie, und
        // jede Rechenansage begänne mit Android-TTS.
        val root = Json.parseToJsonElement(extraStringsFile().readText()).jsonObject
        val mathTexts = root.getValue("strings").jsonArray
            .map { it.jsonObject }
            .filter { it["field"]?.jsonPrimitive?.content == "mathTaskTts" }
            .map { it.getValue("text").jsonPrimitive.content }
            .toSet()
        assertEquals(setOf(MathPromptSpeech.INTRO), mathTexts)
    }

    private fun shippedRounds(): List<CountAddRound> =
        pack.tasks.values.filterIsInstance<CountAddSpec>().flatMap { it.rounds }

    /** Wie in PraisePhrasesTest: vom Arbeitsverzeichnis aus nach oben suchen. */
    private fun extraStringsFile(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "tools/tts/extra-strings.json")
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        throw AssertionError(
            "tools/tts/extra-strings.json nicht gefunden, gestartet in ${File("").absolutePath}",
        )
    }
}
