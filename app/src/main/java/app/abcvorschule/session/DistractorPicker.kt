package app.abcvorschule.session

import app.abcvorschule.content.AtomKind
import app.abcvorschule.content.ComposePart
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Domain
import app.abcvorschule.content.TaskTemplate
import app.abcvorschule.content.casePair
import app.abcvorschule.progress.LearnerProgress
import kotlin.random.Random

data class DistractorTile(val atomId: String, val display: String)

/**
 * Picks distractor tiles for slot boards from atoms the learner has already
 * practiced — never invented content. Without distractors, single-slot tasks
 * are unfailable and the adaptive engine gets no error signal.
 */
object DistractorPicker {
    /** Tray stays small enough for preschoolers to scan. */
    const val MaxTrayTiles = 5
    const val MaxDistractors = 2

    fun pick(
        template: TaskTemplate,
        parts: List<ComposePart>,
        pack: ContentPack,
        progress: LearnerProgress,
        random: Random = Random.Default,
    ): List<DistractorTile> {
        if (template.domain == Domain.math || parts.isEmpty()) return emptyList()
        val budget = (MaxTrayTiles - parts.size).coerceAtMost(MaxDistractors)
        if (budget <= 0) return emptyList()

        val partIds = parts.map { it.atomId }.toSet()
        val kinds = partIds.mapNotNull { pack.atoms[it]?.kind }.toSet()
        if (kinds.isEmpty()) return emptyList()
        val gapDisplays = parts.map { part ->
            part.display ?: pack.atoms[part.atomId]?.display ?: part.atomId
        }.toSet()

        val candidates = pack.atoms.values
            .filter { atom ->
                atom.kind in kinds &&
                    atom.id !in partIds &&
                    (progress.atomStats[atom.id]?.attempts ?: 0) > 0
            }
            .map { atom ->
                val display = if (atom.kind == AtomKind.letter) {
                    val (upper, lower) = atom.casePair()
                    if (random.nextBoolean()) upper else lower
                } else {
                    atom.display
                }
                DistractorTile(atomId = atom.id, display = display)
            }
            .filter { it.display !in gapDisplays }
            .distinctBy { it.display }

        return candidates.shuffled(random).take(budget)
    }
}
