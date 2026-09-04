package app.abcvorschule.session

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.LetterTraceSpec
import app.abcvorschule.content.SoundFeederSpec
import app.abcvorschule.content.SymbolHuntMode
import app.abcvorschule.content.SymbolHuntSpec
import app.abcvorschule.content.SyllableMergeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundFeederInsertionTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun scheduled(lessonId: String) =
        pack.tasksOf(pack.lesson(lessonId)).map { ScheduledTrainer(spec = it) }

    private fun withHunts(lessonId: String) =
        SymbolHuntInsertion.insertSymbolHunts(scheduled(lessonId), pack, lessonId, pack.lesson(lessonId).index)

    private fun insert(lessonId: String) =
        SoundFeederInsertion.insertSoundFeeder(withHunts(lessonId), pack, pack.lesson(lessonId))

    private fun isLetterHunt(trainer: ScheduledTrainer) =
        trainer.spec is SymbolHuntSpec && trainer.spec.id.endsWith(":symbol_hunt:${SymbolHuntMode.letter.name}")

    @Test
    fun theFeederLandsRightAfterTheLetterHunt() {
        val result = insert("l13")
        val feeder = result.indexOfFirst { it.spec is SoundFeederSpec }
        val hunt = result.indexOfLast { isLetterHunt(it) }
        assertTrue("no letter hunt in l13", hunt >= 0)
        assertEquals(hunt + 1, feeder)
    }

    @Test
    fun theFeederComesBeforeTheFirstSyllableMergeInEveryLessonThatHasBoth() {
        var checked = 0
        pack.authoredLessons.forEach { lesson ->
            val result = insert(lesson.id)
            val feeder = result.indexOfFirst { it.spec is SoundFeederSpec }
            val merge = result.indexOfFirst { it.spec is SyllableMergeSpec }
            if (feeder >= 0 && merge >= 0) {
                assertTrue("${lesson.id}: feeder $feeder must precede merge $merge", feeder < merge)
                checked++
            }
        }
        assertTrue(checked > 0)
    }

    @Test
    fun withoutAHuntTheFeederFollowsTheLastTrace() {
        val base = scheduled("l13")
        val result = SoundFeederInsertion.insertSoundFeeder(base, pack, pack.lesson("l13"))
        val feeder = result.indexOfFirst { it.spec is SoundFeederSpec }
        assertEquals(result.indexOfLast { it.spec is LetterTraceSpec } + 1, feeder)
    }

    @Test
    fun lessonsOneAndTwoGetNoFeederEveryOtherLessonExactlyOne() {
        pack.authoredLessons.forEach { lesson ->
            val count = insert(lesson.id).count { it.spec is SoundFeederSpec }
            assertEquals(lesson.id, if (lesson.index < 3) 0 else 1, count)
        }
    }

    @Test
    fun theSpecIdIsDerivedFromTheLesson() {
        assertEquals("l13:sound_feeder", insert("l13").first { it.spec is SoundFeederSpec }.spec.id)
    }

    @Test
    fun theOriginalTrainersKeepTheirOrder() {
        val original = withHunts("l13").map { it.spec.id }
        assertEquals(original, insert("l13").filter { it.spec !is SoundFeederSpec }.map { it.spec.id })
    }
}
