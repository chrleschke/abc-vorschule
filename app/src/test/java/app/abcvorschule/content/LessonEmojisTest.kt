package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonEmojisTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun firstLessonShowsItsOwnPictureWords() {
        // l01 (M & A) hunts the sound in ameise / maus / baum.
        assertEquals(
            listOf("🐜", "🐭", "🌳"),
            LessonEmojis.forLesson(pack, pack.lesson("l01")),
        )
    }

    @Test
    fun everyAuthoredLessonYieldsThreeEmojis() {
        pack.authoredLessons.forEach { lesson ->
            val emojis = LessonEmojis.forLesson(pack, lesson)
            assertEquals("lesson ${lesson.id}", 3, emojis.size)
            assertTrue("lesson ${lesson.id} has a blank emoji", emojis.none { it.isBlank() })
        }
    }

    @Test
    fun emojisAreDeduplicatedByGlyphNotByAtomId() {
        // dach and haus both carry the same house glyph; a sign must never show
        // the same picture twice even when two different atoms supply it.
        pack.authoredLessons.forEach { lesson ->
            val emojis = LessonEmojis.forLesson(pack, lesson)
            assertEquals("lesson ${lesson.id} repeats a glyph", emojis.size, emojis.distinct().size)
        }
    }

    @Test
    fun theLimitIsHonoured() {
        assertEquals(2, LessonEmojis.forLesson(pack, pack.lesson("l01"), limit = 2).size)
        assertEquals(emptyList<String>(), LessonEmojis.forLesson(pack, pack.lesson("l01"), limit = 0))
    }

    @Test
    fun resultIsStableAcrossCalls() {
        pack.authoredLessons.forEach { lesson ->
            assertEquals(
                LessonEmojis.forLesson(pack, lesson),
                LessonEmojis.forLesson(pack, lesson),
            )
        }
    }

    @Test
    fun plannedLessonWithoutTasksYieldsNothing() {
        // No lesson in the shipped pack is `planned`, so this case only exists
        // synthetically — the sign then shows the lock and no emoji row.
        val planned = Lesson(
            id = "l99",
            index = 99,
            phase = 7,
            title = "Noch nicht geschrieben",
            nodeLabel = "?",
            status = LessonStatus.planned,
        )
        assertEquals(emptyList<String>(), LessonEmojis.forLesson(pack, planned))
    }

    @Test
    fun unknownTaskIdsAreSkippedInsteadOfThrowing() {
        val broken = pack.lesson("l01").copy(taskIds = listOf("does-not-exist"))
        assertEquals(emptyList<String>(), LessonEmojis.forLesson(pack, broken))
    }
}
