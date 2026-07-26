package app.abcvorschule.ui.exercise.drag

/** Axis-aligned rectangle in root coordinates. Compose-free so it stays unit-testable. */
data class DragRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
}

/**
 * Drop resolution for preschool drag & drop: a card commits only when it actually
 * lands on a zone, and a card straddling two zones resolves to the one it covers
 * most — so a wrong slot is a real miss and a drop into empty space snaps back.
 */
object DragHitTest {
    /** Below this travel the gesture was a tap, not a drag. */
    const val MinCommitPx = 24f

    fun overlapArea(a: DragRect, b: DragRect): Float {
        val w = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f)
        val h = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
        return w * h
    }

    fun bestZone(card: DragRect, zones: Map<String, DragRect>): String? = zones
        .mapValues { (_, zone) -> overlapArea(card, zone) }
        .filterValues { it > 0f }
        .maxByOrNull { it.value }
        ?.key

    fun shouldCommit(dragDistancePx: Float): Boolean = dragDistancePx > MinCommitPx
}
