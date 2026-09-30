package app.abcvorschule.ui.rewards

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset

/**
 * Wo der Punktestand-Stern gerade steht, in Root-Koordinaten — das Ziel, in das der
 * Erfolgs-Stern fliegt. Gesetzt von [app.abcvorschule.ui.components.AbcStarCount],
 * gelesen von [SuccessBurst]. `null`, solange noch kein Punktestand gelegt wurde; dann
 * schrumpft der Stern an Ort und Stelle wie früher.
 */
class StarCounterAnchor {
    var centerInRoot: Offset? by mutableStateOf(null)
}

/**
 * Der Flug des Erfolgs-Sterns in den Punktestand (PRODUCT_PRINCIPLES §10).
 *
 * Bis September 2026 poppte der große Stern im oberen Drittel auf und verschwand an
 * Ort und Stelle, während die Zahl in der Kopfzeile schon still um eins gewachsen war.
 * Beide standen auf derselben Achse, aber nichts verband sie — für ein Kind, das nicht
 * zählt, war der Punkt damit unsichtbar. Jetzt fliegt der Stern hinauf, schrumpft
 * dabei auf die Größe des kleinen Sterns, und erst beim Einschlag springt die Zahl
 * und der Zähler hüpft: „mein Stern ist jetzt da oben".
 *
 * Compose-frei, damit Bahn und Größe als Rechnung testbar sind.
 */
object StarFlight {
    /** Wie lange der Stern nach dem Aufpoppen steht, bevor er losfliegt. */
    const val HoldMs = 450L

    /** Der große Stern ist 84 dp, der im Punktestand 22 dp. */
    const val LandedScale = 22f / 84f

    /**
     * Seitlicher Schwung als Anteil der Flugstrecke: eine gerade Linie liest sich wie
     * ein Aufzug, ein leichter Bogen wie ein geworfener Stern.
     */
    const val SwayFraction = 0.12f

    /** Versatz vom Startpunkt bei Fortschritt [t] ∈ 0…1. */
    fun offset(start: Offset, target: Offset, t: Float): Offset {
        val p = t.coerceIn(0f, 1f)
        val delta = target - start
        // Senkrecht zur Flugrichtung ausschwingen, am Start und am Ziel null.
        val length = delta.getDistance()
        val sway = if (length == 0f) {
            Offset.Zero
        } else {
            Offset(-delta.y, delta.x) / length * (length * SwayFraction * kotlin.math.sin(kotlin.math.PI.toFloat() * p))
        }
        return delta * p + sway
    }

    /** Maßstab relativ zum großen Stern: von [from] (nach dem Aufpoppen) auf [LandedScale]. */
    fun scale(from: Float, t: Float): Float = from + (LandedScale - from) * t.coerceIn(0f, 1f)
}
