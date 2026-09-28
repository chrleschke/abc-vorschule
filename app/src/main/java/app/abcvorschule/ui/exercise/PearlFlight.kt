package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.AbcMotion

/**
 * Eine Perle unterwegs vom getippten Buchstaben in die Herzmuschel ([CockleShell]).
 *
 * Der Flug beginnt **im selben Frame wie der Tipp** — das Kind sieht, dass seine Blase
 * zur Perle wird, und wohin sie geht. Die Muschel klappt dabei auf, fängt sie und klappt
 * kurz danach wieder zu; ist sie voll, bleibt sie offen und leuchtet.
 */
class PearlFlight(val from: Offset, val slot: Int, val color: Color, val round: String) {
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f)

    companion object {
        /** Flugdauer: dieselbe Stufe wie der Rückflug einer Karte. */
        const val FlightMs = AbcMotion.LongMs

        /** Wie lange die Muschel nach der Landung offen bleibt, bevor sie zufedert. */
        const val CloseAfterMs = 700L

        /** Nach so langer Pause lugt die Muschel halb auf … */
        const val PeekAfterIdleMs = 6_000L

        /** … und danach seltener: ein Blick, keine Dauerbewegung. */
        const val PeekRepeatMs = 10_000L
        const val PeekOpenMs = 900
        const val PeekHoldMs = 1_600L
        const val PeekCloseMs = 700

        /** Die Perle startet klein im Kern der Blase. */
        val StartRadius = 14.dp
    }
}
