package app.abcvorschule.session

import app.abcvorschule.content.Atom
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Domain
import app.abcvorschule.content.TaskTemplate
import app.abcvorschule.content.TaskType
import app.abcvorschule.progress.LearnerProgress
import app.abcvorschule.progress.ProgressionEngine
import kotlin.random.Random

class SessionScheduler(
    private val random: Random = Random.Default,
) {
    fun buildSession(
        pack: ContentPack,
        progress: LearnerProgress,
        size: Int = 5,
        preferLowMastery: Boolean = progress.packIntroCompleted,
    ): List<TaskTemplate> {
        val eligible = pack.tasks.filter { isEligible(it, pack, progress) }
        if (eligible.isEmpty()) return emptyList()

        val byDomain = Domain.entries.associateWith { domain ->
            val list = eligible.filter { it.domain == domain }.toMutableList()
            list.shuffle(random)
            if (preferLowMastery) {
                list.sortBy { masteryFor(it, progress) }
            }
            list
        }

        val picks = mutableListOf<TaskTemplate>()
        var domainCursor = 0
        var guard = 0
        while (picks.size < size && guard < size * 12) {
            guard++
            val domainOrder = Domain.entries
            val rotated = domainOrder.drop(domainCursor % domainOrder.size) +
                domainOrder.take(domainCursor % domainOrder.size)
            var chosen: TaskTemplate? = null
            for (domain in rotated) {
                val pool = byDomain.getValue(domain)
                if (pool.isEmpty()) continue
                val lastDomain = picks.lastOrNull()?.domain
                val otherAvailable = Domain.entries.any { d ->
                    d != domain && byDomain.getValue(d).isNotEmpty()
                }
                if (lastDomain == domain && otherAvailable) continue
                chosen = pool.removeAt(0)
                break
            }
            if (chosen == null) {
                chosen = byDomain.values.firstOrNull { it.isNotEmpty() }?.removeAt(0)
            }
            if (chosen == null) break
            picks += chosen
            domainCursor++
        }
        return picks
    }

    fun isEligible(task: TaskTemplate, pack: ContentPack, progress: LearnerProgress): Boolean {
        return when (task.type) {
            TaskType.sentence_cloze -> {
                val sentence = pack.sentences[task.sentenceId] ?: return false
                sentence.atomIds.all { atomReady(pack.atom(it), progress) }
            }
            TaskType.speech_cloze -> {
                val atomId = task.targetAtomId ?: task.atomId ?: return false
                atomReady(pack.atom(atomId), progress)
            }
            TaskType.cloze -> {
                val atomId = task.atomId ?: return false
                atomReady(pack.atom(atomId), progress)
            }
            TaskType.visual_add, TaskType.number_entry -> true
        }
    }

    /**
     * An atom is ready when every prerequisite has been practiced at least once,
     * or the prerequisite itself has no prerequisites (bootstrapping Fibel intros).
     */
    private fun atomReady(atom: Atom, progress: LearnerProgress): Boolean {
        if (atom.prerequisites.isEmpty()) return true
        return atom.prerequisites.all { preId ->
            val practiced = (progress.atomStats[preId]?.attempts ?: 0) > 0
            practiced
        }
    }

    private fun masteryFor(task: TaskTemplate, progress: LearnerProgress): Double {
        return when (task.domain) {
            Domain.reading, Domain.speech -> {
                val id = task.targetAtomId ?: task.atomId ?: task.gapAtomIds.firstOrNull()
                val stats = id?.let { progress.atomStats[it] } ?: return 0.0
                ProgressionEngine.masteryScore(stats)
            }
            Domain.math -> {
                val key = ProgressionEngine.mathKey(
                    operation = task.operation ?: "add",
                    left = task.left ?: 0,
                    right = task.right ?: 0,
                    band = task.difficultyBand,
                )
                val stats = progress.mathStats[key] ?: return 0.0
                if (stats.attempts == 0) 0.0 else stats.correct.toDouble() / stats.attempts
            }
        }
    }
}
