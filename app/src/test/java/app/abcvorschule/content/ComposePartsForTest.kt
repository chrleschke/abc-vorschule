package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Test

class ComposePartsForTest {
    @Test
    fun letterTasksExposeUpperAndLowerFrames() {
        val atom = Atom(
            id = "letter-o",
            lemma = "o",
            display = "Oo",
            emoji = "",
            kind = AtomKind.letter,
        )
        val task = TaskTemplate(
            id = "r-letter-o",
            domain = Domain.reading,
            type = TaskType.cloze,
            atomId = "letter-o",
            promptTts = "Finde den Buchstaben o.",
            slots = listOf("letter-o"),
            tier = "letter",
        )
        val parts = task.composePartsFor(atom)
        assertEquals(2, parts.size)
        assertEquals("O", parts[0].display)
        assertEquals("o", parts[1].display)
        assertEquals("letter-o#U", parts[0].slotKey)
        assertEquals("letter-o#L", parts[1].slotKey)
    }

    @Test
    fun speechMamaUsesComposePartsNotWholeWordSlot() {
        val task = TaskTemplate(
            id = "sp-mama",
            domain = Domain.speech,
            type = TaskType.speech_cloze,
            atomId = "mama",
            promptTts = "Bilde das Wort Mama.",
            targetAtomId = "mama",
            composeParts = listOf("ma", "ma"),
            composeDisplays = listOf("Ma", "ma"),
        )
        val parts = task.composePartsFor(null)
        assertEquals(listOf("Ma", "ma"), parts.map { it.display })
    }

    @Test
    fun speechHausSpellsIndividualLetters() {
        val task = TaskTemplate(
            id = "sp-haus",
            domain = Domain.speech,
            type = TaskType.speech_cloze,
            atomId = "haus",
            promptTts = "Bilde das Wort Haus.",
            targetAtomId = "haus",
            composeParts = listOf("letter-h", "letter-a", "letter-u", "letter-s"),
            composeDisplays = listOf("H", "a", "u", "s"),
            tier = "spell",
        )
        assertEquals(true, task.isSpellTask())
        val parts = task.composePartsFor(null)
        assertEquals(listOf("H", "a", "u", "s"), parts.map { it.display })
    }
}
