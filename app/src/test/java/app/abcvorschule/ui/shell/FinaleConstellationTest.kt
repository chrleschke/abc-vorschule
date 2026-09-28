package app.abcvorschule.ui.shell

import app.abcvorschule.content.AtomKind
import app.abcvorschule.content.ContentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FinaleConstellationTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun theFirstLessonShowsTheM() {
        val lesson = pack.lessons.first { it.id == "l01" }
        val finale = pack.finales.getValue(lesson.finaleId!!)
        assertEquals("M", FinaleConstellation.letterFor(lesson, finale, pack)?.display)
    }

    /**
     * Jede Lektion mit Finale bekommt ein Sternbild. Ist der Buchstabe geübt, steht er auch
     * im Satz; sonst ist er der Anfangsbuchstabe des ersten Bild-Nomens (Nutzerentscheidung).
     */
    @Test
    fun everyLessonGetsALetterThatFitsItsSentence() {
        pack.lessons.filter { it.finaleId != null }.forEach { lesson ->
            val finale = pack.finales.getValue(lesson.finaleId!!)
            val atom = FinaleConstellation.letterFor(lesson, finale, pack)
            assertNotNull("${lesson.id}: kein Buchstabe", atom)
            val letter = atom!!.display
            val practiced = atom.id in lesson.focusAtomIds
            val inSentence = finale.text.contains(letter, ignoreCase = true)
            val nounInitial = finale.pictureAtomIds.firstNotNullOfOrNull { pack.atoms[it] }
                ?.display?.firstOrNull()?.uppercase() == letter
            assertTrue("${lesson.id}: „$letter“ passt nicht zu „${finale.text}“", (practiced && inSentence) || nounInitial || practiced)
        }
    }

    @Test
    fun everySingleLetterMakesAConstellationInsideItsBox() {
        pack.atoms.values
            .filter { it.kind == AtomKind.letter && it.display.length == 1 && it.strokes.isNotEmpty() }
            .forEach { atom ->
                val c = FinaleConstellation.of(atom)
                assertTrue("${atom.display}: ${c.stars.size} Sterne", c.stars.size in 3..24)
                assertTrue("${atom.display}: keine Linien", c.lines.isNotEmpty())
                c.stars.forEach { (x, y) -> assertTrue("${atom.display}: $x/$y", x in -0.01f..1.01f && y in -0.01f..1.01f) }
                c.lines.forEach { (a, b) -> assertTrue(a != b && a in c.stars.indices && b in c.stars.indices) }
            }
    }

    /** Wo zwei Striche sich treffen (Spitze des A), liegt ein Stern, nicht zwei. */
    @Test
    fun sharedStrokeEndsBecomeOneStar() {
        val a = FinaleConstellation.of(pack.atoms.getValue("letter-a"))
        val apex = a.stars.count { (x, y) -> kotlin.math.hypot(x - 0.5f, y - 0.08f) < 0.05f }
        assertEquals(1, apex)
    }

    /** Die M-Ecken bleiben Ecken: jeder Knick der Spur ist ein Stern. */
    @Test
    fun cornersSurviveTheSimplification() {
        val m = FinaleConstellation.of(pack.atoms.getValue("letter-m"))
        listOf(0.12f to 0.92f, 0.12f to 0.08f, 0.5f to 0.62f, 0.88f to 0.08f, 0.88f to 0.92f).forEach { (cx, cy) ->
            assertTrue("Ecke $cx/$cy fehlt", m.stars.any { (x, y) -> kotlin.math.hypot(x - cx, y - cy) < 0.02f })
        }
    }
}
