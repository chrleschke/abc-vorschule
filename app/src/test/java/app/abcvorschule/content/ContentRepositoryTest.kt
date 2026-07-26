package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRepositoryTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun packLoadsAllSixteenLessons() {
        assertEquals(16, pack.lessons.size)
        // Lessons 1-6 (phase 1 + 2) are authored; 7-16 stay planned until later tasks.
        assertEquals(6, pack.authoredLessons.size)
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
        assertEquals(6, lesson.taskIds.size)
    }

    @Test
    fun polymorphicTasksDeserializeToTheirTrainerType() {
        val kinds = pack.tasksOf(pack.lesson("l01")).map { it.kind }
        assertEquals(ContentValidator.TrainerOrder, kinds)
        assertTrue(pack.task("l01-t1") is SoundPositionSpec)
        assertTrue(pack.task("l01-t6") is CountAddSpec)
    }

    @Test
    fun atomsAreSharedAcrossTrainers() {
        // AE6 in new clothes: one atom, one emoji, reused by several trainers.
        val ma = pack.atom("ma")
        assertEquals("ma", ma.display)
        val merge = pack.task("l01-t3") as SyllableMergeSpec
        val build = pack.task("l01-t4") as WordBuildSpec
        assertEquals("ma", merge.rounds.first().resultAtomId)
        assertTrue(build.rounds.first().blocks.any { it.atomId == "ma" })
    }

    @Test
    fun traceRoundsResolveStrokeDataFromAtoms() {
        val trace = pack.task("l01-t2") as LetterTraceSpec
        trace.rounds.forEach { round ->
            val atom = pack.atom(round.atomId)
            assertTrue("${atom.id} needs strokes", atom.strokes.isNotEmpty())
        }
        assertNotNull(pack.atom("letter-a").strokes.firstOrNull())
    }

    @Test
    fun countAddRoundsUseLessonContextIcons() {
        val math = pack.task("l01-t6") as CountAddSpec
        math.rounds.forEach { round ->
            assertTrue(round.iconAtomId in pack.atoms.keys)
            assertTrue(pack.atom(round.iconAtomId).emoji.isNotBlank())
        }
    }
}
