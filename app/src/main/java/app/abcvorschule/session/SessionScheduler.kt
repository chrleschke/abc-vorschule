package app.abcvorschule.session

import app.abcvorschule.content.Atom
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Domain
import app.abcvorschule.content.TaskTemplate
import app.abcvorschule.content.TaskType
import app.abcvorschule.content.tierRank
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
            if (domain == Domain.reading) {
                // Focus the lowest tier that still has unoffered tasks so letters
                // don't starve syllables/compose forever after first practice.
                val focusTier = list.map { it.tierRank() }.distinct().sorted()
                val focus = focusTier.firstOrNull { rank ->
                    list.any { it.tierRank() == rank && !taskOffered(it, progress) }
                }
                if (focus != null) {
                    list.sortWith(
                        compareBy<TaskTemplate> { if (it.tierRank() == focus) 0 else 1 }
                            .thenBy { it.tierRank() }
                            .thenBy { if (preferLowMastery) masteryFor(it, progress) else 0.0 },
                    )
                } else if (preferLowMastery) {
                    list.sortBy { masteryFor(it, progress) }
                } else {
                    list.sortBy { it.tierRank() }
                }
            } else if (preferLowMastery) {
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
                // R4/AE10: every sentence atom must itself have been offered.
                sentence.atomIds.all { offered(it, progress) } &&
                    task.gapAtomIds.all { offered(it, progress) }
            }
            TaskType.speech_cloze -> {
                val atomId = task.targetAtomId ?: task.atomId ?: return false
                atomReady(pack.atom(atomId), progress)
            }
            TaskType.cloze -> {
                val atomId = task.atomId ?: return false
                val atom = pack.atom(atomId)
                when (task.tier) {
                    "letter" -> true
                    "syllable" -> atomReady(atom, progress)
                    "compose" -> atomReady(atom, progress) &&
                        task.composeParts.distinct().all { it == atomId || offered(it, progress) }
                    "word" -> {
                        val hasComposeIntro = pack.tasks.any {
                            it.tier == "compose" && it.atomId == atomId
                        }
                        // Words with a compose intro unlock after that practice; others after prereqs.
                        if (hasComposeIntro) offered(atomId, progress) else atomReady(atom, progress)
                    }
                    else -> atomReady(atom, progress)
                }
            }
            TaskType.visual_add, TaskType.number_entry -> true
        }
    }

    private fun offered(atomId: String, progress: LearnerProgress): Boolean =
        (progress.atomStats[atomId]?.attempts ?: 0) > 0

    private fun taskOffered(task: TaskTemplate, progress: LearnerProgress): Boolean {
        val id = task.atomId ?: task.targetAtomId ?: return false
        return offered(id, progress)
    }

    private fun atomReady(atom: Atom, progress: LearnerProgress): Boolean {
        if (atom.prerequisites.isEmpty()) return true
        return atom.prerequisites.all { offered(it, progress) }
    }

    private fun masteryFor(task: TaskTemplate, progress: LearnerProgress): Double {
        return when (task.domain) {
            Domain.reading, Domain.speech -> {
                val id = task.targetAtomId ?: task.atomId ?: task.gapAtomIds.firstOrNull()
                    ?: task.composeParts.firstOrNull()
                val stats = id?.let { progress.atomStats[it] } ?: return 0.0
                ProgressionEngine.masteryScore(stats)
            }
            Domain.math -> {
                val stats = progress.mathStats[ProgressionEngine.mathKey(task)] ?: return 0.0
                ProgressionEngine.masteryScore(stats)
            }
        }
    }
}
