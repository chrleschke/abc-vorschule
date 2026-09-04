package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SoundFeederAtomsTest {
    private val pack = ContentRepository.fromClasspath().load()

    private val expected = mapOf(
        "turm" to Triple("Turm", "🗼", Gender.m),
        "wurm" to Triple("Wurm", "🪱", Gender.m),
        "kanne" to Triple("Kanne", "🫖", Gender.f),
        "tanne" to Triple("Tanne", "🌲", Gender.f),
        "wanne" to Triple("Wanne", "🛁", Gender.f),
        "moehre" to Triple("Möhre", "🥕", Gender.f),
        "muetze" to Triple("Mütze", "🧢", Gender.f),
        "pfanne" to Triple("Pfanne", "🍳", Gender.f),
        "pfeil" to Triple("Pfeil", "🏹", Gender.m),
        "ziege" to Triple("Ziege", "🐐", Gender.f),
    )

    @Test
    fun theTenFeederAtomsExistAsPictureNouns() {
        expected.forEach { (id, triple) ->
            val (display, emoji, gender) = triple
            val atom = pack.atoms[id]
            assertNotNull("atom $id missing", atom)
            assertEquals(display, atom!!.display)
            assertEquals(display, atom.lemma)
            assertEquals(emoji, atom.emoji)
            assertEquals(AtomKind.other, atom.kind)
            assertEquals(NounClass.thing, atom.nounClass)
            assertEquals(gender, atom.gender)
            assertNotNull("atom $id needs a pluralDisplay", atom.pluralDisplay)
        }
    }
}
