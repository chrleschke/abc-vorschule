package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `soundTts` ist der Laut, den der Laut-Fresser rülpst („sss"), nicht der
 * Buchstabenname („Es"). Der ClipIndex löst nach Text auf und ignoriert dabei
 * Groß-/Kleinschreibung — ein `soundTts`, das wie ein `lemma`/`display` aussieht,
 * würde still den Buchstabennamen-Clip ziehen.
 */
class AtomSoundTtsTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun everyGraphemeTheFeederPlaysCarriesItsSound() {
        val played = SoundPairs.table.flatMap { it.graphemes }.toSet()
        val missing = pack.atoms.values
            .filter { it.kind == AtomKind.letter && it.display in played }
            .filter { it.soundTts.isNullOrBlank() }
            .map { it.id }
        assertEquals(emptyList<String>(), missing.sorted())
    }

    @Test
    fun noSoundCollidesWithALetterName() {
        val names = pack.atoms.values.flatMap { listOf(it.lemma, it.display) }
            .map { it.lowercase() }.toSet()
        val colliding = pack.atoms.values
            .mapNotNull { it.soundTts }
            .filter { it.lowercase() in names }
        assertEquals(emptyList<String>(), colliding.sorted())
    }

    @Test
    fun soundsAreLowercaseAndWithoutSpaces() {
        for (atom in pack.atoms.values) {
            val sound = atom.soundTts ?: continue
            assertTrue(atom.id, sound.isNotBlank())
            assertEquals(atom.id, sound.lowercase(), sound)
            assertTrue(atom.id, sound.none { it.isWhitespace() })
        }
    }
}
