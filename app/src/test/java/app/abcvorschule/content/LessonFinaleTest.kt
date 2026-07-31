package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonFinaleTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun everyAuthoredLessonResolvesToAFinale() {
        pack.authoredLessons.forEach { lesson ->
            val id = lesson.finaleId
            assertTrue("lesson ${lesson.id} needs a finaleId", !id.isNullOrBlank())
            assertTrue("lesson ${lesson.id} finaleId $id is unknown", id in pack.finales)
        }
    }

    @Test
    fun eighteenFinalesCoverTwentySixLessons() {
        assertEquals(18, pack.finales.size)
        assertEquals(26, pack.authoredLessons.size)
    }

    @Test
    fun repeatLessonsShareTheFinaleOfTheirBaseLesson() {
        // L19-L26 wiederholen frühere Lektionen und erben deren Satz.
        assertEquals("f-l01", pack.lesson("l19").finaleId)
        assertEquals("f-l02", pack.lesson("l20").finaleId)
        assertEquals("f-l03", pack.lesson("l21").finaleId)
        assertEquals("f-l11", pack.lesson("l22").finaleId)
        assertEquals("f-l13", pack.lesson("l23").finaleId)
        assertEquals("f-l17", pack.lesson("l24").finaleId)
        assertEquals("f-l12", pack.lesson("l25").finaleId)
        assertEquals("f-l18", pack.lesson("l26").finaleId)
    }

    @Test
    fun everySentenceHoldsFourToSevenWords() {
        pack.finales.values.forEach { finale ->
            val words = finale.text.trim().split(Regex("\\s+")).size
            assertTrue(
                "finale ${finale.id} has $words words, expected 4..7: ${finale.text}",
                words in 4..7,
            )
        }
    }

    @Test
    fun everyFinaleHoldsTwoToFourDistinctPictures() {
        pack.finales.values.forEach { finale ->
            val emojis = finale.pictureAtomIds.map { pack.atom(it).emoji }
            assertTrue(
                "finale ${finale.id} has ${emojis.size} pictures, expected 2..4",
                emojis.size in 2..4,
            )
            emojis.forEach {
                assertTrue("finale ${finale.id} has a picture atom without emoji", it.isNotBlank())
            }
            assertEquals(
                "finale ${finale.id} shows the same glyph twice",
                emojis.size,
                emojis.distinct().size,
            )
        }
    }

    @Test
    fun everyFinaleIsReferencedBySomeLesson() {
        val referenced = pack.lessons.mapNotNull { it.finaleId }.toSet()
        assertEquals(emptySet<String>(), pack.finales.keys - referenced)
    }

    @Test
    fun kuchenIsPictureOnlyVocabulary() {
        // "Kuchen" trägt nur ein Bild im Finale von L05 — es wird nie gelesen oder gebaut.
        val kuchen = pack.atom("kuchen")
        assertEquals("🍰", kuchen.emoji)
        assertEquals(AtomKind.other, kuchen.kind)
    }
}
