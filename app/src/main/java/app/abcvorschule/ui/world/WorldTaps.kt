package app.abcvorschule.ui.world

import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.geometry.Offset
import kotlin.math.exp
import kotlin.math.min

/**
 * Tipps auf die Welt selbst (PRODUCT_PRINCIPLES §10, „Antippen macht Freude"): ein Tipp,
 * den kein Bauteil der Aufgabe verbraucht hat, geht an den Hintergrund, und der antwortet
 * mit einer kleinen, weichen Bewegung — Blasen steigen, Glühwürmchen weichen aus, der
 * Vorhang bauscht sich. Kein Stern, kein Ton, keine Wirkung auf die Aufgabe.
 *
 * Die Zeit ([now]) schreibt das Zeichnen des Hintergrunds hinein, als einfaches Feld —
 * derselbe Weg wie bei den Laternen-Loopings auf dem Pfad.
 */
@Stable
class WorldTaps {
    internal val taps = mutableStateListOf<WorldTap>()
    internal var now = 0f
    internal var still = false

    /** Ein Tipp an [at], in Koordinaten des Hintergrunds. */
    fun add(at: Offset) {
        if (still) return
        taps.removeAll { now - it.time > MaxAgeS }
        if (taps.size >= MaxTaps) taps.removeAt(0)
        taps += WorldTap(at, now, taps.size + (taps.lastOrNull()?.seed ?: 0) + 1)
    }

    /** Die noch wirkenden Tipps mit ihrem Alter in Sekunden (0 … [maxAge]). */
    internal inline fun forEachRecent(seconds: Float, maxAge: Float, block: (WorldTap, Float) -> Unit) {
        taps.forEach { tap ->
            val age = seconds - tap.time
            if (age in 0f..maxAge) block(tap, age)
        }
    }

    companion object {
        const val MaxAgeS = 5f
        const val MaxTaps = 6
    }
}

/** [seed] macht jeden Tipp ein wenig anders (Richtung der Blasen, Drehsinn des Staubs). */
data class WorldTap(val at: Offset, val time: Float, val seed: Int)

/** Ein fester Zufallswert 0…1 je Tipp und Teilchen — gleich über alle Frames. */
internal fun tapNoise(seed: Int, k: Int): Float {
    var h = seed * 374761393 + k * 668265263
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777215f
}

/** Weich ansteigen über [rise] s, dann mit der Zeitkonstante [decay] s ausklingen. */
internal fun tapEnvelope(age: Float, rise: Float, decay: Float): Float =
    min(1f, age / rise) * exp(-age / decay)
