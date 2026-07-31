package app.abcvorschule.debug

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.SoundPositionSpec
import app.abcvorschule.content.rounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsDebugEntryTest {
    private val pack = ContentRepository.fromClasspath().load()
    private val entries = pack.ttsDebugEntries()

    @Test
    fun oneEntryPerAtomLemma() {
        val atomEntries = entries.filter { it.group == TtsDebugGroup.Atom }
        assertEquals(pack.atoms.size, atomEntries.size)
        val letterM = atomEntries.first { it.id == "atom:letter-m:lemma" }
        assertEquals("M", letterM.originalText)
        assertEquals("atoms.json", letterM.sourceFile)
    }

    @Test
    fun oneEntryPerSentenceTts() {
        val sentenceEntries = entries.filter { it.group == TtsDebugGroup.Sentence }
        assertEquals(pack.sentences.size, sentenceEntries.size)
        val sentence = pack.sentences.values.first()
        val entry = sentenceEntries.first { it.id == "sentence:${sentence.id}:tts" }
        assertEquals(sentence.tts, entry.originalText)
    }

    @Test
    fun everyRoundHasAPromptTtsEntry() {
        val expectedPromptCount = pack.tasks.values.sumOf { it.rounds.size }
        val promptEntries = entries.count { it.id.endsWith(":promptTts") }
        assertEquals(expectedPromptCount, promptEntries)
    }

    @Test
    fun soundPositionSpecsExposePhonemeTtsAndRoundMissTts() {
        val spec = pack.tasks.values.filterIsInstance<SoundPositionSpec>().first()
        val phonemeEntry = entries.first { it.id == "task:${spec.id}:phonemeTts" }
        assertEquals(spec.phonemeTts, phonemeEntry.originalText)

        val missEntry = entries.first { it.id == "task:${spec.id}:round:0:missTts" }
        assertEquals(spec.rounds.first().missTts, missEntry.originalText)
    }

    @Test
    fun idsAreUnique() {
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
    }
}
