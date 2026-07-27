package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRepositoryTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun packLoadsTwentySixAuthoredLessons() {
        assertEquals(26, pack.lessons.size)
        assertEquals(26, pack.authoredLessons.size)
    }

    @Test
    fun schemaVersionIsTwo() {
        assertEquals(2, pack.manifest.schemaVersion)
    }

    @Test
    fun lessonOneIsMundA() {
        val lesson = pack.lesson("l01")
        assertEquals(1, lesson.index)
        assertEquals(listOf("letter-m", "letter-a"), lesson.focusAtomIds)
        assertTrue(lesson.taskIds.isNotEmpty())
    }

    @Test
    fun polymorphicTasksDeserializeToTheirTrainerType() {
        val tasks = pack.tasksOf(pack.lesson("l01"))
        assertTrue(tasks.first() is SoundPositionSpec)
        assertTrue(tasks.last() is CountAddSpec)
        assertTrue(tasks.any { it is LetterTraceSpec })
    }

    @Test
    fun atomsAreSharedAcrossTrainers() {
        // AE6 in new clothes: one atom, one emoji, reused by several trainers.
        val ma = pack.atom("ma")
        assertEquals("ma", ma.display)
        val merge = pack.tasksOf(pack.lesson("l01")).filterIsInstance<SyllableMergeSpec>().first()
        val build = pack.tasksOf(pack.lesson("l01")).filterIsInstance<WordBuildSpec>()
            .first { spec -> spec.rounds.any { it.blocks.any { block -> block.atomId == "ma" } } }
        assertEquals("ma", merge.rounds.first().resultAtomId)
        assertTrue(build.rounds.any { it.blocks.any { it.atomId == "ma" } })
    }

    @Test
    fun traceRoundsResolveStrokeDataFromAtoms() {
        pack.tasksOf(pack.lesson("l01")).filterIsInstance<LetterTraceSpec>().forEach { trace ->
            trace.rounds.forEach { round ->
                val atom = pack.atom(round.atomId)
                assertTrue("${atom.id} needs strokes", atom.strokes.isNotEmpty())
            }
        }
        assertNotNull(pack.atom("letter-a").strokes.firstOrNull())
    }

    @Test
    fun countAddRoundsUseLessonContextIcons() {
        pack.tasksOf(pack.lesson("l01")).filterIsInstance<CountAddSpec>().forEach { math ->
            math.rounds.forEach { round ->
                assertTrue(round.iconAtomId in pack.atoms.keys)
                assertTrue(pack.atom(round.iconAtomId).emoji.isNotBlank())
            }
        }
    }
}
