package app.abcvorschule.session

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.SentenceOrderSpec
import app.abcvorschule.content.SymbolInWordSpec
import app.abcvorschule.content.WordBuildSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolInWordInsertionTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun scheduled(lessonId: String) =
        pack.tasksOf(pack.lesson(lessonId)).map { ScheduledTrainer(spec = it) }

    private fun insert(lessonId: String) = SymbolInWordInsertion.insertSymbolInWord(
        scheduled(lessonId),
        pack,
        pack.lesson(lessonId),
    )

    @Test
    fun theDetectiveLandsRightAfterTheLastWordBuilder() {
        val result = insert("l03")
        val detectiveIndex = result.indexOfFirst { it.spec is SymbolInWordSpec }
        val lastWordBuild = result.indexOfLast { it.spec is WordBuildSpec }
        assertTrue("no detective inserted", detectiveIndex >= 0)
        assertEquals(lastWordBuild + 1, detectiveIndex)
    }

    @Test
    fun theDetectiveComesBeforeTheSentenceArchitect() {
        val result = insert("l03")
        val detectiveIndex = result.indexOfFirst { it.spec is SymbolInWordSpec }
        val firstSentence = result.indexOfFirst { it.spec is SentenceOrderSpec }
        if (firstSentence >= 0) {
            assertTrue("detective must precede sentence_order", detectiveIndex < firstSentence)
        }
    }

    @Test
    fun exactlyOneDetectiveIsInserted() {
        assertEquals(1, insert("l03").count { it.spec is SymbolInWordSpec })
    }

    @Test
    fun theOriginalTrainersKeepTheirOrder() {
        val original = scheduled("l03").map { it.spec.id }
        val kept = insert("l03").filter { it.spec !is SymbolInWordSpec }.map { it.spec.id }
        assertEquals(original, kept)
    }

    @Test
    fun theSpecIdIsDerivedFromTheLesson() {
        val detective = insert("l03").first { it.spec is SymbolInWordSpec }
        assertEquals("l03:symbol_in_word", detective.spec.id)
    }

    @Test
    fun aLessonWithoutWordBuildGetsNoDetective() {
        val lesson = pack.lesson("l01").let { base ->
            base.copy(taskIds = base.taskIds.filter { pack.tasks[it] !is WordBuildSpec })
        }
        val trainers = lesson.taskIds.map { ScheduledTrainer(spec = pack.task(it)) }
        val result = SymbolInWordInsertion.insertSymbolInWord(trainers, pack, lesson)
        assertTrue(result.none { it.spec is SymbolInWordSpec })
    }

    @Test
    fun everyAuthoredLessonGetsExactlyOneDetectiveWithAtLeastOneRound() {
        pack.authoredLessons.forEach { lesson ->
            val result = insert(lesson.id)
            val detectives = result.filter { it.spec is SymbolInWordSpec }
            assertEquals("lesson ${lesson.id}", 1, detectives.size)
            assertTrue(
                "lesson ${lesson.id} detective has no rounds",
                (detectives.single().spec as SymbolInWordSpec).rounds.isNotEmpty(),
            )
        }
    }

    @Test
    fun theDetectiveAndTheHuntsCoexistInEitherInsertionOrder() {
        val base = scheduled("l03")
        val huntFirst = SymbolInWordInsertion.insertSymbolInWord(
            SymbolHuntInsertion.insertSymbolHunts(base, pack, "l03", pack.lesson("l03").index),
            pack,
            pack.lesson("l03"),
        )
        val detectiveFirst = SymbolHuntInsertion.insertSymbolHunts(
            SymbolInWordInsertion.insertSymbolInWord(base, pack, pack.lesson("l03")),
            pack,
            "l03",
            pack.lesson("l03").index,
        )
        assertEquals(huntFirst.map { it.spec.id }, detectiveFirst.map { it.spec.id })
    }
}
