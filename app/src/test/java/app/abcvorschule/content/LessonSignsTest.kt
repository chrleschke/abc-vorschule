package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonSignsTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun sign(id: String) = LessonSigns.forLesson(pack, pack.lesson(id))

    @Test
    fun firstLessonShowsItsSoundsWithTheirTracePictures() {
        // M wie Mond, A wie Ampel — die Belohnungen des Spurensuchers.
        val blocks = sign("l01").blocks
        assertEquals(listOf("M", "A"), blocks.map { it.glyph })
        assertEquals(listOf("🌙", "🚦"), blocks.map { it.emoji })
    }

    @Test
    fun soundsStandInTheirCapitalFormOnEverySign() {
        // Bis Oktober 2026 stand „M a" auf dem Schild: der zweite Laut klein. Jetzt
        // trägt jeder Laut seine Großform; nur ck und ß haben keine.
        pack.authoredLessons.forEach { lesson ->
            LessonSigns.forLesson(pack, lesson).blocks.forEach { block ->
                if (block.glyph != "ck" && block.glyph != "ß") {
                    assertTrue("${lesson.id}: ${block.glyph} is not capitalised", block.glyph.first().isUpperCase())
                }
            }
        }
        assertEquals(listOf("ck", "Pf"), sign("l16").blocks.map { it.glyph })
    }

    @Test
    fun everyAuthoredSoundHasAPicture() {
        // Jede autorierte Lektion zeichnet jeden ihrer Laute nach — sonst bliebe ein
        // Würfel ohne Bild und drehte sich nicht.
        pack.authoredLessons.forEach { lesson ->
            val sign = LessonSigns.forLesson(pack, lesson)
            assertEquals("${lesson.id} blocks", lesson.focusAtomIds.size, sign.blocks.size)
            sign.blocks.forEach { assertNotNull("${lesson.id}: ${it.glyph} has no picture", it.emoji) }
        }
    }

    @Test
    fun reviewLessonsCarryTheBadgeAndNothingElseDoes() {
        val reviews = pack.authoredLessons.filter { LessonSigns.forLesson(pack, it).review }.map { it.id }
        assertEquals((19..26).map { "l$it" }, reviews)
    }

    @Test
    fun aSoundWearsTheSameToneOnEverySign() {
        // l01 und l19 sind beide M & A.
        assertEquals(sign("l01").blocks.map { it.tone }, sign("l19").blocks.map { it.tone })
        assertEquals(sign("l13").blocks.single().tone, sign("l23").blocks.first().tone)
    }

    @Test
    fun soundsOfOneLessonNeverShareATone() {
        pack.authoredLessons.forEach { lesson ->
            // Fünf Klotzfarben; eine Lektion führt höchstens vier Laute ein.
            val tones = LessonSigns.forLesson(pack, lesson).blocks.map { Math.floorMod(it.tone, 5) }
            if (lesson.id in (1..18).map { "l%02d".format(it) }) {
                assertEquals("${lesson.id} repeats a tone", tones.size, tones.distinct().size)
            }
        }
    }

    @Test
    fun resultIsStableAcrossCalls() {
        pack.authoredLessons.forEach { lesson ->
            assertEquals(LessonSigns.forLesson(pack, lesson), LessonSigns.forLesson(pack, lesson))
        }
    }

    @Test
    fun plannedLessonFallsBackToItsLabel() {
        val planned = Lesson(
            id = "l99",
            index = 99,
            phase = 7,
            title = "Noch nicht geschrieben",
            nodeLabel = "Ng nk+",
            status = LessonStatus.planned,
        )
        val sign = LessonSigns.forLesson(pack, planned)
        assertEquals(listOf("Ng", "Nk"), sign.blocks.map { it.glyph })
        assertTrue(sign.blocks.all { it.emoji == null })
        assertTrue(sign.review)
    }

    @Test
    fun unknownTaskIdsLeaveTheSoundsWithoutPicturesInsteadOfThrowing() {
        val broken = pack.lesson("l01").copy(taskIds = listOf("does-not-exist"))
        val sign = LessonSigns.forLesson(pack, broken)
        assertEquals(listOf("M", "A"), sign.blocks.map { it.glyph })
        assertTrue(sign.blocks.all { it.emoji == null })
        assertFalse(sign.review)
    }
}
