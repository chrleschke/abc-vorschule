package app.abcvorschule.ui.path

import kotlin.math.PI
import kotlin.math.sin

/** Node center in path-content pixels, y growing downwards. */
data class PathPoint(val x: Float, val y: Float)

/**
 * Calm winding S-curve: nodes stack top-down at constant spacing while x swings
 * center → right → center → left with a period of four nodes. Pure math so the
 * layout is unit-testable without Compose.
 */
object PathGeometry {
    const val DefaultSpacing = 140f
    const val DefaultMargin = 96f

    fun points(
        count: Int,
        width: Float,
        spacing: Float = DefaultSpacing,
        margin: Float = DefaultMargin,
    ): List<PathPoint> {
        if (count <= 0) return emptyList()
        val center = width / 2f
        val amplitude = (center - margin).coerceAtLeast(0f)
        return (0 until count).map { index ->
            PathPoint(
                x = center + amplitude * sin(index * PI / 2.0).toFloat(),
                y = margin + index * spacing,
            )
        }
    }

    fun contentHeight(
        count: Int,
        spacing: Float = DefaultSpacing,
        margin: Float = DefaultMargin,
    ): Float = if (count <= 0) 0f else 2 * margin + (count - 1) * spacing
}
