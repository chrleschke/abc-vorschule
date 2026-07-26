package app.abcvorschule.ui.exercise

import app.abcvorschule.content.ComposePart
import app.abcvorschule.progress.ScaffoldLevel

data class GapSlot(
    val slotKey: String,
    val atomId: String,
    val display: String,
    val emoji: String,
    val scaffold: ScaffoldLevel,
)

object ScaffoldMapping {
    fun gaps(
        parts: List<ComposePart>,
        displays: Map<String, String>,
        emojis: Map<String, String>,
        scaffolds: Map<String, ScaffoldLevel>,
    ): List<GapSlot> = parts.map { part ->
        GapSlot(
            slotKey = part.slotKey,
            atomId = part.atomId,
            display = part.display ?: displays[part.atomId] ?: part.atomId,
            emoji = emojis[part.atomId].orEmpty(),
            scaffold = scaffolds[part.atomId] ?: ScaffoldLevel.Beginner,
        )
    }

    fun showsSilhouette(level: ScaffoldLevel): Boolean = level == ScaffoldLevel.Beginner

    fun tileLabels(gaps: List<GapSlot>): List<String> = gaps.map { it.display }

    fun hasMixedScaffolds(scaffolds: Map<String, ScaffoldLevel>): Boolean =
        scaffolds.values.toSet().size > 1
}
