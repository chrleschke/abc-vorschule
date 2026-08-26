package app.abcvorschule.ui.rewards

import java.io.File
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class PraisePhrasesTest {
    @Test
    fun everyPhraseIsCuratedForTheTtsPipelineAndViceVersa() {
        // Beide Seiten haben früher nur *gezählt* — hier 39, in
        // tools/tts/tests/test_extract.py noch einmal 39. Eine umformulierte Phrase
        // ließ damit beide Tests grün, weil die Zahl gleich blieb: die Kachel-Seite
        // sprach dann einen Text, für den kein Clip existiert, und das Kind fiel
        // still auf Android-TTS zurück. Also Mengen vergleichen, nicht Größen.
        val curated = curatedRewardTexts()
        assertEquals(
            "Phrasen ohne rewardTts-Eintrag in tools/tts/extra-strings.json",
            emptySet<String>(),
            PraisePhrases.All.toSet() - curated,
        )
        assertEquals(
            "rewardTts-Einträge in tools/tts/extra-strings.json ohne Phrase in PraisePhrases",
            emptySet<String>(),
            curated - PraisePhrases.All.toSet(),
        )
        assertEquals(PraisePhrases.All.size, PraisePhrases.All.distinct().size)
    }

    /** Die `rewardTts`-Texte aus dem kuratierten Sprach-Paket. */
    private fun curatedRewardTexts(): Set<String> {
        val root = Json.parseToJsonElement(extraStringsFile().readText()).jsonObject
        return root.getValue("strings").jsonArray
            .map { it.jsonObject }
            .filter { it["field"]?.jsonPrimitive?.content == "rewardTts" }
            .map { it.getValue("text").jsonPrimitive.content }
            .toSet()
    }

    /**
     * Der Test läuft im Modulverzeichnis (`app/`), die IDE startet ihn aber auch
     * gern aus dem Repo-Wurzelverzeichnis — also von hier aus nach oben suchen,
     * statt einen der beiden Pfade fest zu verdrahten (wie in ManifestSoftInputTest).
     */
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

    @Test
    fun phrasesStayDistinctIgnoringPunctuationAndCase() {
        // The dedup rule: "Spitze" and "Das war spitze!" are two different cheers, but the
        // same words with different punctuation would be one clip curated and rendered twice.
        val normalized = PraisePhrases.All.map { phrase ->
            phrase.lowercase()
                .map { if (it.isLetterOrDigit()) it else ' ' }
                .joinToString("")
                .split(" ")
                .filter { it.isNotEmpty() }
                .joinToString(" ")
        }
        assertEquals(normalized.toString(), normalized.size, normalized.distinct().size)
    }

    @Test
    fun phrasesEndAsAStatementOrACheer() {
        // Each phrase is spoken as its own utterance, so it ends either bare ("Klasse")
        // or on its own sentence punctuation — never on "?", praise does not ask.
        PraisePhrases.All.forEach { phrase ->
            assertTrue(phrase, phrase.isNotBlank())
            assertTrue(phrase, phrase.last().isLetterOrDigit() || phrase.last() in ".!")
        }
    }

    @Test
    fun pickIsAlwaysOneOfThePhrases() {
        val random = Random(7)
        repeat(200) {
            assertTrue(PraisePhrases.pick(random) in PraisePhrases.All)
        }
    }

    @Test
    fun pickVariesAcrossCalls() {
        val random = Random(7)
        val seen = (1..50).map { PraisePhrases.pick(random) }.toSet()
        assertTrue("praise should vary, saw $seen", seen.size > 1)
    }
}
