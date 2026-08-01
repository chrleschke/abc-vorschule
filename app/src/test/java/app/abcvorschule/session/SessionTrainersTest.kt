package app.abcvorschule.session

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.SymbolInWordSpec
import app.abcvorschule.content.TaskSpec
import app.abcvorschule.content.TrainerRound
import app.abcvorschule.content.rounds
import app.abcvorschule.content.scoredAtomIds
import app.abcvorschule.progress.LearnerProgress
import app.abcvorschule.progress.ParentMode
import app.abcvorschule.progress.ProgressionEngine
import app.abcvorschule.progress.ScaffoldLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionTrainersTest {
    private val pack = ContentRepository.fromClasspath().load()

    /**
     * "Ohne Hilfe" — the setting the missing scaffolds silently overrode. With the
     * default Auto mode an unseen atom scaffolds to Beginner, which is exactly what
     * [SessionViewModel.scaffoldFor] falls back to, so an empty scaffold map would
     * be indistinguishable from a correct one.
     */
    private val progress = LearnerProgress(parentMode = ParentMode.Advanced)

    /** Mirrors SessionViewModel.schedule — the same pure mapping of spec + progress. */
    private fun schedule(spec: TaskSpec): ScheduledTrainer {
        val atomIds = spec.rounds.flatMap { it.scoredAtomIds() + sentenceAtomIds(it) }.distinct()
        return ScheduledTrainer(
            spec = spec,
            scaffolds = atomIds.associateWith { ProgressionEngine.scaffoldForAtom(progress, it) },
        )
    }

    private fun sentenceAtomIds(round: TrainerRound): List<String> =
        if (round is SentenceOrderRound) pack.sentence(round.sentenceId).atomIds else emptyList()

    private fun assemble(lessonId: String) =
        SessionTrainers.assemble(pack, pack.lesson(lessonId), ::schedule)

    @Test
    fun everyTrainerCarriesAScaffoldForEveryAtomItScores() {
        // The scheduler has to run over the *assembled* list, not over the authored
        // specs: the runtime insertions add trainers afterwards, and a synthetic
        // trainer without scaffolds falls back to Beginner in
        // SessionViewModel.scaffoldFor — showing the Wort-Detektiv's silhouette to a
        // child whose parents chose "Ohne Hilfe". Asserted for every trainer, so the
        // next synthetic trainer is covered too, not just this one.
        pack.authoredLessons.forEach { lesson ->
            assemble(lesson.id).forEach { trainer ->
                trainer.spec.rounds.flatMap { it.scoredAtomIds() }.distinct().forEach { atomId ->
                    assertTrue(
                        "lesson ${lesson.id}: ${trainer.spec.id} has no scaffold for $atomId",
                        trainer.scaffolds.containsKey(atomId),
                    )
                }
            }
        }
    }

    @Test
    fun theSplicedDetectiveCarriesTheParentsChosenScaffold() {
        // The regression in one assertion: "Ohne Hilfe" has to reach the synthetic
        // trainer, not just the authored ones.
        val assembled = assemble("l02")
        val detective = assembled.single { it.spec is SymbolInWordSpec }
        val targetAtomIds = (detective.spec as SymbolInWordSpec).rounds.map { it.targetAtomId }.distinct()
        assertTrue(targetAtomIds.isNotEmpty())
        targetAtomIds.forEach { atomId ->
            assertEquals(
                "detective scaffold for $atomId",
                ScaffoldLevel.Advanced,
                detective.scaffolds[atomId],
            )
        }
    }

    @Test
    fun theAssembledListKeepsTheAuthoredTasksInOrderAroundTheInsertions() {
        val lesson = pack.lesson("l02")
        // playableTasksOf, not lesson.taskIds: a paused trainer kind (e.g. sound_position)
        // never reaches the assembled list, so it can't be part of this ordering check.
        val playableIds = pack.playableTasksOf(lesson).map { it.id }
        val assembled = assemble(lesson.id).map { it.spec.id }
        assertEquals(playableIds, assembled.filter { it in playableIds })
        assertTrue("insertions must actually add trainers", assembled.size > playableIds.size)
    }

    @Test
    fun schedulingAnAlreadyScheduledSpecIsIdempotent() {
        // What makes it safe to run the scheduler over the assembled list: it is a
        // pure function of spec and progress, so the authored trainers come out of a
        // second pass exactly as they went into the first.
        val once = assemble("l03")
        assertEquals(once, once.map { schedule(it.spec) })
    }
}
