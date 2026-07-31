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
        // synthetically — the sign then shows the lock glyph and an empty (but
        // still space-reserving) emoji row.
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

    @Test
    fun duplicateGlyphFromADifferentAtomIsSkippedInFavorOfAThirdPicture() {
        // No lesson in the shipped pack currently has two different atoms sharing
        // one glyph, so the dedup-by-glyph rule has nothing to bite on there. This
        // builds that collision by hand: atoms "twin-a" and "twin-b" both carry the
        // duplicate glyph and are listed first; a correct implementation must skip
        // the second twin (same glyph, different atom id) and keep reaching down
        // the round order until a third, genuinely different picture turns up.
        val duplicateGlyph = "🟢"
        val atoms = listOf(
            Atom(id = "twin-a", lemma = "twin-a", display = "A", emoji = duplicateGlyph),
            Atom(id = "twin-b", lemma = "twin-b", display = "B", emoji = duplicateGlyph),
            Atom(id = "other-c", lemma = "other-c", display = "C", emoji = "🔵"),
            Atom(id = "other-d", lemma = "other-d", display = "D", emoji = "🟡"),
        ).associateBy { it.id }

        fun round(atomId: String) = SoundPositionRound(
            promptTts = "prompt",
            atomId = atomId,
            slot = SoundSlot.start,
            missTts = "miss",
        )

        val task = SoundPositionSpec(
            id = "t-collision",
            phonemeTts = "X",
            // Duplicate pair first, on purpose — the dedup must fire on the second one.
            rounds = listOf(round("twin-a"), round("twin-b"), round("other-c"), round("other-d")),
        )

        val lesson = Lesson(
            id = "l-collision",
            index = 1,
            phase = 1,
            title = "Fixture",
            nodeLabel = "X",
            status = LessonStatus.authored,
            taskIds = listOf(task.id),
        )

        val pack = ContentPack(
            manifest = PackManifest(schemaVersion = 1, packId = "test", title = "Test Pack"),
            atoms = atoms,
            sentences = emptyMap(),
            tasks = mapOf(task.id to task),
            // Dieser Test prüft nur die Emoji-Ableitung für die Pfad-Schilder;
            // Finale-Sätze spielen dabei keine Rolle.
            finales = emptyMap(),
            lessons = listOf(lesson),
        )

        val emojis = LessonEmojis.forLesson(pack, lesson)

        assertEquals(listOf(duplicateGlyph, "🔵", "🟡"), emojis)
        assertEquals(3, emojis.distinct().size)
    }
}
