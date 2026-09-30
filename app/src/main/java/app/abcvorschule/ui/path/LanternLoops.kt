package app.abcvorschule.ui.path

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size

/**
 * Wer eine Himmelslaterne antippt, lässt sie einen Looping fliegen (PRODUCT_PRINCIPLES §5).
 * Reine Freude ohne Aufgabe: kein Stern, kein Ton. Die Uhr ([now]) und die Bildgröße
 * schreibt das Zeichnen der Laternen hier hinein — als einfache Felder, nicht als
 * State, sonst löste jeder Frame ein neues Zeichnen aus.
 */
@Stable
class LanternLoops {
    /** Laternen-Nummer → Weltzeit, zu der ihr Looping begann. */
    internal val starts = mutableStateMapOf<Int, Float>()
    internal var now = 0f
    internal var still = false

    /**
     * Tipp an [at] (px) auf einem Himmel der Größe [size]: trifft er eine Laterne, fliegt
     * die vorderste getroffene einen Looping. [minRadiusPx] hält auch ferne, kleine
     * Laternen mit dem Finger treffbar.
     */
    fun tap(at: Offset, size: Size, minRadiusPx: Float): Boolean {
        if (still || size.width <= 0f) return false
        val hit = SkyLanterns.at(now).asReversed().firstOrNull { l ->
            val w = size.width * l.width
            val center = Offset(size.width * l.x, size.height * l.y + w * 1.33f * 0.45f)
            (at - center).getDistance() <= maxOf(w * 0.9f, minRadiusPx)
        } ?: return false
        val running = starts[hit.id]
        if (running != null && now - running < SkyLanterns.LoopS) return false
        // Alte Einträge wegräumen: eine Laterne, deren Looping vorbei ist, braucht keinen mehr.
        starts.keys.filter { now - (starts[it] ?: 0f) > SkyLanterns.LoopS }.forEach { starts.remove(it) }
        starts[hit.id] = now
        return true
    }
}
