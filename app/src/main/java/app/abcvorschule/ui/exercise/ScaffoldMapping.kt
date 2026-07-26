package app.abcvorschule.ui.exercise

import app.abcvorschule.progress.ScaffoldLevel

data class GapSlot(
    val atomId: String,
    val display: String,
    val emoji: String,
    val scaffold: ScaffoldLevel,
)

object ScaffoldMapping {
    fun gaps(
        atomIds: List<String>,
        displays: Map<String, String>,
        emojis: Map<String, String>,
        scaffolds: Map<String, ScaffoldLevel>,
    ): List<GapSlot> = atomIds.map { id ->
        GapSlot(
            atomId = id,
            display = displays[id] ?: id,
            emoji = emojis[id] ?: "🔤",
            scaffold = scaffolds[id] ?: ScaffoldLevel.Beginner,
        )
    }

    fun showsSilhouette(level: ScaffoldLevel): Boolean = level == ScaffoldLevel.Beginner

    fun tileLabels(gaps: List<GapSlot>): List<String> = gaps.map { it.display }
}
