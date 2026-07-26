package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRepositoryTest {
    @Test
    fun loadsAndResolvesSentenceAndSpeechAtoms() {
        val pack = ContentRepository.fromClasspath().load()
        val sentence = pack.sentence("s-mama-haus")
        assertEquals(listOf("mama", "ist", "im", "haus"), sentence.atomIds)
        sentence.atomIds.forEach { id ->
            val atom = pack.atom(id)
            assertTrue(atom.emoji.isNotBlank())
            assertTrue(atom.display.isNotBlank())
        }
        val speech = pack.tasks.first { it.id == "sp-gegangen" }
        assertNotNull(pack.atom(speech.targetAtomId!!))
        assertEquals(listOf("letter-m", "letter-a"), pack.atom("ma").prerequisites)
        assertEquals(listOf("ma"), pack.atom("mama").prerequisites)
        assertTrue(pack.tasks.any { it.id == "r-compose-mama" && it.composeParts == listOf("ma", "ma") })
    }
}
