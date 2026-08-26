package app.abcvorschule.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.WarmMuted

/**
 * Füllstand der Segmente einer Lektion — ein Segment je Trainer.
 *
 * Reine Mathematik, damit die Regel testbar ist, ohne eine Composable zu
 * rendern.
 */
object SegmentedProgress {
    /**
     * Füllanteil 0..1 des Segments [segment], während das Kind in Trainer
     * [currentIndex] auf Runde [roundIndex] von [roundCount] steht.
     *
     * Ein Trainer ohne Runden ([roundCount] <= 0) zählt als voll: dort ist
     * nichts zu tun, und ein dauerhaft leeres Segment sähe aus wie ein
     * hängengebliebener Fortschritt.
     */
    fun fillOf(segment: Int, currentIndex: Int, roundIndex: Int, roundCount: Int): Float = when {
        segment < currentIndex -> 1f
        segment > currentIndex -> 0f
        roundCount <= 0 -> 1f
        else -> ((roundIndex + 1).toFloat() / roundCount.toFloat()).coerceIn(0f, 1f)
    }
}

private val SegmentGap = 4.dp

/**
 * Fortschritt der Lektion als Segmentkette: erledigte Trainer voll, der laufende
 * nach Runden-Anteil gefüllt, kommende leer. Ersetzt Balken + Textlabel +
 * Runden-Punkte — das Kind liest kein „3/8", und die Segmentzahl sagt dasselbe.
 */
@Composable
fun AbcSegmentedProgress(
    index: Int,
    total: Int,
    roundIndex: Int,
    roundCount: Int,
    modifier: Modifier = Modifier,
) {
    if (total <= 0) return

    // Innerhalb eines Trainers wächst die Füllung animiert; beim Trainerwechsel
    // springt sie, sonst liefe das neue (fast leere) Segment sichtbar rückwärts
    // aus dem Füllstand des alten heraus.
    val fill = remember { Animatable(SegmentedProgress.fillOf(index, index, roundIndex, roundCount)) }
    var animatedIndex by remember { mutableIntStateOf(index) }
    LaunchedEffect(index, roundIndex, roundCount) {
        val target = SegmentedProgress.fillOf(index, index, roundIndex, roundCount)
        if (index != animatedIndex) {
            animatedIndex = index
            fill.snapTo(target)
        } else {
            fill.animateTo(target, animationSpec = tween(450))
        }
    }

    // Kurzer Gold-Puls an der Segmentgrenze — der Puls markiert das Ereignis
    // „Trainer geschafft", nicht den Zustand. Der Merker startet auf dem zuerst
    // gesehenen Index (kein Puls bei Erstkomposition) und pulst nur, wenn der
    // Index tatsächlich steigt — ein Rücksprung per Zurück-Chevron pulst nicht.
    val pulse = remember { Animatable(0f) }
    var pulsedIndex by remember { mutableIntStateOf(index) }
    LaunchedEffect(index) {
        if (index > pulsedIndex) {
            pulse.snapTo(1f)
            pulse.animateTo(0f, animationSpec = tween(500))
        }
        pulsedIndex = index
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(AbcDimens.progressBarHeight),
    ) {
        val gap = SegmentGap.toPx()
        val segmentWidth = ((size.width - gap * (total - 1)) / total).coerceAtLeast(1f)
        val corner = CornerRadius(size.height / 2f)
        val stride = segmentWidth + gap
        repeat(total) { segment ->
            val left = segment * stride
            // Die Kante gefüllt/ungefüllt IST hier die Information — sie sagt, wie
            // weit die Lektion ist —, also gilt für sie die 3:1-Grenze für
            // UI-Bauteile. Bei 0.35 komponierte die Spur über Cream zu #CFC5B4 und
            // SkyBlue kam darauf nur auf 2.50:1. 0.18 ergibt #E4DBCB: SkyBlue
            // dagegen 3.11:1. Die Füllfarbe konnte nicht wandern — SkyBlue ist die
            // Rolle „Fortschritt/aktiv" (§10) —, also musste die Spur heller werden.
            // Sie bleibt dabei sichtbar: 1.25:1 gegen Cream ist dieselbe Stufe, mit
            // der sich die Hügelbänder der Pfad-Landschaft voneinander trennen
            // (Color.kt: 1.24:1 und 1.33:1), und die gefüllte Strecke selbst steht
            // mit 3.88:1 auf Cream ohnehin für sich.
            drawRoundRect(
                color = WarmMuted.copy(alpha = 0.18f),
                topLeft = Offset(left, 0f),
                size = Size(segmentWidth, size.height),
                cornerRadius = corner,
            )
            val fraction = if (segment == index) {
                fill.value
            } else {
                SegmentedProgress.fillOf(segment, index, roundIndex, roundCount)
            }
            if (fraction > 0f) {
                drawRoundRect(
                    color = SkyBlue,
                    topLeft = Offset(left, 0f),
                    size = Size(segmentWidth * fraction, size.height),
                    cornerRadius = corner,
                )
            }
        }
        val pulseValue = pulse.value
        if (pulseValue > 0f && index > 0) {
            drawCircle(
                color = StarGold.copy(alpha = 0.6f * pulseValue),
                radius = size.height * (0.8f + 0.6f * (1f - pulseValue)),
                center = Offset((index - 1) * stride + segmentWidth, size.height / 2f),
            )
        }
    }
}
