package app.abcvorschule.ui.rewards

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/** Radiale Funken-Positionen für den Erfolgs-Burst; rein und testbar. */
object BurstGeometry {
    fun sparkOffsets(count: Int, progress: Float, radiusPx: Float): List<Offset> {
        val r = radiusPx * progress.coerceIn(0f, 1f)
        return List(count) { i ->
            // -90° Start, damit der erste Funke nach oben fliegt.
            val angle = -Math.PI / 2 + 2 * Math.PI * i / count
            Offset((cos(angle) * r).toFloat(), (sin(angle) * r).toFloat())
        }
    }
}
