package app.abcvorschule.session

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.Domain
import app.abcvorschule.content.TaskType
import app.abcvorschule.progress.AtomStats
import app.abcvorschule.progress.LearnerProgress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SessionSchedulerTest {
    private val pack = ContentRepository.fromClasspath().load()
    private val scheduler = SessionScheduler(Random(1))

    @Test
    fun sentenceNotDrawnBeforeSyllablePractice() {
        val fresh = LearnerProgress()
        val session = scheduler.buildSession(pack, fresh, size = 5)
        assertFalse(session.any { it.type == TaskType.sentence_cloze })
    }

    @Test
    fun sentenceStillIneligibleAfterOnlySyllablePractice() {
        val onlyMa = LearnerProgress(
            atomStats = mapOf("ma" to AtomStats(attempts = 1, correct = 1)),
        )
        assertFalse(
            scheduler.isEligible(
                pack.tasks.first { it.id == "r-sent-mama-haus" },
                pack,
                onlyMa,
            ),
        )
        val session = scheduler.buildSession(pack, onlyMa, size = 5)
        assertFalse(session.any { it.type == TaskType.sentence_cloze })
    }

    @Test
    fun fiveDrawsSpanDomainsWhenPossible() {
        val practiced = LearnerProgress(
            atomStats = mapOf(
                "ma" to AtomStats(attempts = 1, correct = 1),
                "mama" to AtomStats(attempts = 1, correct = 1),
                "haus" to AtomStats(attempts = 1, correct = 1),
                "ist" to AtomStats(attempts = 1, correct = 1),
                "im" to AtomStats(attempts = 1, correct = 1),
                "gehen" to AtomStats(attempts = 1, correct = 1),
                "gegangen" to AtomStats(attempts = 1, correct = 1),
            ),
        )
        val session = scheduler.buildSession(pack, practiced, size = 5)
        val domains = session.map { it.domain }.toSet()
        assertTrue(domains.contains(Domain.reading))
        assertTrue(domains.contains(Domain.speech))
        assertTrue(domains.contains(Domain.math))
    }

    @Test
    fun unfinishedSessionIdsRemainResolvable() {
        val progress = LearnerProgress(
            unfinishedSession = app.abcvorschule.progress.SessionSnapshot(
                taskIds = listOf("r-syll-ma", "m-blumen-1-3", "sp-haus"),
                index = 1,
                pointsEarned = 1,
                packId = pack.manifest.packId,
            ),
        )
        val ids = progress.unfinishedSession!!.taskIds
        assertTrue(ids.all { id -> pack.tasks.any { it.id == id } })
        assertTrue(progress.unfinishedSession!!.index == 1)
    }

    @Test
    fun continuePrefersLowMasteryAfterIntro() {
        val progress = LearnerProgress(
            packIntroCompleted = true,
            atomStats = mapOf(
                "ma" to AtomStats(attempts = 10, correct = 10),
                "mama" to AtomStats(attempts = 1, correct = 0),
                "haus" to AtomStats(attempts = 1, correct = 0),
            ),
        )
        val session = scheduler.buildSession(pack, progress, size = 5, preferLowMastery = true)
        assertTrue(session.isNotEmpty())
    }
}
