package app.abcvorschule.session

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.LetterTraceSpec
import app.abcvorschule.content.SymbolHuntSpec
import app.abcvorschule.content.TrainerKind
import app.abcvorschule.content.kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolHuntInsertionTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun scheduledTrainersFor(lessonId: String): List<ScheduledTrainer> =
        pack.tasksOf(pack.lesson(lessonId)).map { ScheduledTrainer(spec = it) }

    @Test
    fun letterHuntIsPlacedRightAfterTheLastLetterTraceTrainer() {
        val lesson = pack.lesson("l01")
        val trainers = scheduledTrainersFor(lesson.id)
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
        val lastTraceIndex = trainers.indexOfLast { it.spec is LetterTraceSpec }
        val hunt = result.getOrNull(lastTraceIndex + 1)
        assertTrue(hunt?.spec is SymbolHuntSpec)
        assertEquals(TrainerKind.symbol_hunt, hunt!!.spec.kind)
    }

    @Test
    fun letterHuntRoundsMatchEveryLetterTraceRoundInTheLesson() {
        val lesson = pack.lesson("l01")
        val trainers = scheduledTrainersFor(lesson.id)
        val traceAtomIds = trainers.filter { it.spec is LetterTraceSpec }
            .flatMap { (it.spec as LetterTraceSpec).rounds }
            .map { it.atomId }
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
        val letterHunt = result.first { it.spec is SymbolHuntSpec && it.spec.id.endsWith(":letter") }
            .spec as SymbolHuntSpec
        assertEquals(traceAtomIds, letterHunt.rounds.map { it.targetAtomId })
    }

    @Test
    fun stableIdsAreLessonAndModeScoped() {
        val lesson = pack.lesson("l01")
        val trainers = scheduledTrainersFor(lesson.id)
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
        val ids = result.filter { it.spec is SymbolHuntSpec }.map { it.spec.id }
        assertTrue("expected at least the letter hunt for l01", ids.isNotEmpty())
        ids.forEach { assertTrue(it.startsWith("l01:symbol_hunt:")) }
    }

    @Test
    fun lessonWithoutSyllableMergeGetsNoSyllableHunt() {
        val withoutSyllableMerge = pack.authoredLessons.firstOrNull { lesson ->
            pack.tasksOf(lesson).none { it.kind == TrainerKind.syllable_merge }
        }
        requireNotNull(withoutSyllableMerge) { "expected at least one authored lesson without syllable_merge" }
        val trainers = scheduledTrainersFor(withoutSyllableMerge.id)
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, withoutSyllableMerge.id, withoutSyllableMerge.index)
        assertTrue(result.none { it.spec is SymbolHuntSpec && it.spec.id.endsWith(":syllable") })
    }

    @Test
    fun lessonOneGetsNoSyllableHuntBecauseItsPoolIsDegenerate() {
        // Confirmed in SymbolHuntDerivationTest: l01's only syllable is "ma"
        // itself, so its pool (excluding the target) is empty.
        val lesson = pack.lesson("l01")
        val trainers = scheduledTrainersFor(lesson.id)
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
        assertTrue(result.none { it.spec is SymbolHuntSpec && it.spec.id.endsWith(":syllable") })
    }

    @Test
    fun insertingNeverDropsOrReordersTheOriginalTrainers() {
        val lesson = pack.lesson("l06")
        val trainers = scheduledTrainersFor(lesson.id)
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
        val resultOriginalOnly = result.filter { it.spec !is SymbolHuntSpec }
        assertEquals(trainers.map { it.spec.id }, resultOriginalOnly.map { it.spec.id })
    }

    @Test
    fun gateEveryAuthoredLessonProducesAValidInsertion() {
        pack.authoredLessons.forEach { lesson ->
            val trainers = scheduledTrainersFor(lesson.id)
            val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
            result.map { it.spec }
                .filterIsInstance<SymbolHuntSpec>()
                .forEach { spec ->
                    assertTrue("lesson ${lesson.id} hunt ${spec.id} must have rounds", spec.rounds.isNotEmpty())
                    spec.rounds.forEach { round ->
                        assertTrue(
                            "lesson ${lesson.id} hunt round ${round.targetAtomId} must have a non-empty pool",
                            round.distractorPool.isNotEmpty(),
                        )
                    }
                }
        }
    }
}
