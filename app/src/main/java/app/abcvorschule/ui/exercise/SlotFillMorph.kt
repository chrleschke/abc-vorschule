package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember

/**
 * Squish-Settle beim Einrasten — der Shape-Morph, den Material 3 Expressive für
 * Zustandswechsel vorsieht, mit Bordmitteln statt mit `androidx.graphics.shapes`:
 * eine Feder, drei abgeleitete Eigenschaften. Kein `RoundedPolygon`-Morph, weil
 * eine blobbige Zielform den Inhalt beschneiden würde, und der Inhalt (Wort im
 * Peg, Silbe im Rahmen) ist die Aufgabe (PRODUCT_PRINCIPLES §10).
 *
 * Geteilt vom **Satz-Architekten** (Wort rastet in einen Peg) und vom
 * **Wort-Bauer** (Baustein rastet in einen Rahmen): dieselbe Tat, dieselbe
 * Antwort — zwei Kopien der Feder wären zwei Gelegenheiten, auseinanderzulaufen.
 *
 * Die Feder läuft von 0 nach [AtRest]; [wobble] ist damit ein gedämpfter
 * Wackler, der bei −1 startet, über 0 hinausschwingt und auf 0 ausläuft. Daraus:
 *
 * - **scaleX** startet bei 0,91 und federt über 1,0 hinaus — das horizontale
 *   Wabbeln.
 * - **scaleY** läuft gegenläufig (1,05 → 1,0), damit es als *Quetschung* liest und
 *   nicht als Zoom; der Inhalt behält seine scheinbare Masse.
 * - **Eckradius** wächst im Moment des Einrastens um [CornerGainDp] und geht auf
 *   den Ruheradius des Bauteils zurück — weicher beim Einrasten, ruhig im Sitzen.
 *
 * Alles drei wird in der **Zeichenphase** gelesen (`graphicsLayer` / `drawBehind`),
 * nicht in der Komposition — gleiche Begründung wie in `PathHereMarker`: eine
 * Federphase darf keine 300ms lang rekomponieren, und Layout-Bounds dürfen dabei
 * nicht zittern, sonst wandert die registrierte Drop-Zone unter dem Finger weg.
 *
 * Compose-frei und damit als Rechnung testbar, gleiche Bauart wie [HuntTileMorph];
 * die Feder selbst startet [rememberSlotFillSettle].
 */
object SlotFillMorph {
    /** Ruhewert der Feder. Der Morph startet bei 0 und läuft hierhin. */
    const val AtRest = 1f

    /** Wie weit das Bauteil im Moment des Einrastens horizontal gequetscht wird. */
    const val SquishX = 0.09f

    /** Gegenläufiger Anteil in Y — Pflicht, sonst liest der Morph als Zoom. */
    const val StretchY = 0.05f

    /** Dämpfung der Feder; darunter wackelt es zu lange nach. */
    const val Damping = 0.42f

    /** Zuschlag auf den Eckradius im Moment des Einrastens. */
    const val CornerGainDp = 8f

    /** Untergrenze des Eckradius, damit der Wackler die Ecke nie hart zieht. */
    const val MinCornerRadiusDp = 12f

    /** −1 im Moment des Einrastens, 0 in Ruhe, dazwischen der Überschwinger. */
    fun wobble(settle: Float): Float = settle - AtRest

    fun scaleX(settle: Float): Float = 1f + SquishX * wobble(settle)

    fun scaleY(settle: Float): Float = 1f - StretchY * wobble(settle)

    /**
     * Eckradius zum Federwert. Maßeinheit-frei: alle drei Größen kommen in
     * derselben Einheit herein (die Aufrufer rechnen in px, weil sie in der
     * Zeichenphase sitzen).
     */
    fun cornerRadius(settle: Float, resting: Float, gain: Float, min: Float): Float =
        (resting - gain * wobble(settle)).coerceAtLeast(min)
}

/**
 * Die Feder hinter [SlotFillMorph], einmal je Slot. Bewusst ein [Animatable] und
 * kein `animateFloatAsState`: der Wert wird ausschließlich in der Zeichenphase
 * gelesen, und nur so bleibt er dort auch.
 *
 * @param filled ob der Slot besetzt ist — der Wechsel auf `true` ist der Morph.
 * @param morphOnFill false lässt den Slot still einrasten. Nach „Auflösen"
 * füllen sich alle Slots gleichzeitig, und ein Chor aus Wacklern wäre eine Feier
 * für etwas, das das Kind nicht geschafft hat.
 */
@Composable
fun rememberSlotFillSettle(
    filled: Boolean,
    morphOnFill: Boolean,
): Animatable<Float, AnimationVector1D> {
    val settle = remember { Animatable(SlotFillMorph.AtRest) }
    LaunchedEffect(filled, morphOnFill) {
        if (filled && morphOnFill) {
            settle.snapTo(0f)
            settle.animateTo(
                targetValue = SlotFillMorph.AtRest,
                animationSpec = spring(
                    dampingRatio = SlotFillMorph.Damping,
                    stiffness = Spring.StiffnessMediumLow,
                ),
            )
        } else {
            settle.snapTo(SlotFillMorph.AtRest)
        }
    }
    return settle
}
