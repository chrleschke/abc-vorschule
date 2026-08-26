package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/** Deckkraft, auf die der Puls-Hinweis herunterblendet. */
private const val PulseLowAlpha = 0.35f
private const val PulseMillis = 700

/**
 * Die Zähl-Hilfe: die Aufgabenmenge, antippbar. Tritt nach
 * [MathHinting.CountingAidFromMisses] Fehlversuchen **an die Stelle** der
 * Aufgabenvisualisierung — nicht als zusätzlicher Block darunter, sonst stünde
 * dieselbe Aufgabe zweimal auf dem Schirm (PRODUCT_PRINCIPLES §9) und auf einem
 * Telefon wäre für beides ohnehin kein Platz.
 *
 * Beide Operanden liegen in einem Feld; der zweite ist gerahmt. Über dem Feld
 * steht die Aufgabe als Ziffernzeile — ohne sie verlöre besonders Minus seine
 * Aufgabe ganz, sobald der gesprochene Prompt verklungen ist. Dasselbe tut die
 * Multiplikationsmatrix längst für sich selbst.
 *
 * Reine Darstellung von [state]; jede Regel darüber, was ein Tipp bewirkt, lebt
 * in [CountingState], jede Größenrechnung in [CountingField].
 */
@Composable
fun CountingAid(
    emoji: String,
    left: Int,
    right: Int,
    operation: MathOperation,
    state: CountingState,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Der Puls läuft dem nächsten offenen Objekt hinterher und zeigt dem Kind
    // durchgehend, wo es weitergeht.
    val pulse by rememberInfiniteTransition(label = "counting_pulse").animateFloat(
        initialValue = 1f,
        targetValue = PulseLowAlpha,
        animationSpec = infiniteRepeatable(tween(PulseMillis), RepeatMode.Reverse),
        label = "counting_pulse_alpha",
    )

    if (operation == MathOperation.Multiply) {
        MultiplicationMatrixGrid(
            emoji = emoji,
            rows = left,
            columns = right,
            modifier = modifier.testTag("counting_aid"),
            counting = state,
            onTapCell = onTap,
            pulseAlpha = pulse,
        )
        return
    }

    val sizeSp = CountingField.emojiSizeSp(operation, left, right)
    var index = 0
    Column(
        modifier = modifier.testTag("counting_aid"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp),
    ) {
        Text(
            text = "$left ${operation.symbol} $right = ?",
            style = MaterialTheme.typography.headlineMedium,
            color = WarmInk,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .testTag("counting_equation"),
        )
        CountingField.rows(state.objectCount).forEach { rowLength ->
            Row(horizontalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp)) {
                repeat(rowLength) {
                    CountingCell(
                        emoji = emoji,
                        index = index++,
                        sizeSp = sizeSp,
                        state = state,
                        pulse = pulse,
                        onTap = onTap,
                    )
                }
            }
        }
    }
}

/**
 * Eine Zelle des Feldes. Der Rahmen sitzt direkt in der Bildmatrix statt in einer
 * eigenen Zone daneben: Objekte, die in eine zweite Zone wandern, sind für ein
 * Vorschulkind zu viel Bewegung auf einmal, und die Zone brauchte eine eigene
 * Ziffer, die sich mit allen anderen Zahlen im Bild stapelte.
 */
@Composable
private fun CountingCell(
    emoji: String,
    index: Int,
    sizeSp: Int,
    state: CountingState,
    pulse: Float,
    onTap: (Int) -> Unit,
) {
    val used = state.isTapped(index)
    val framed = state.isFramed(index)
    // Minus startet mit allen Objekten stehend und nimmt weg; Plus sammelt ein.
    // "Angetippt" heißt je nach Rechenart das Gegenteil — verblasst ist es in
    // beiden Fällen, weil beide Male "damit bin ich durch" gemeint ist.
    val alpha = when {
        used -> CountedAlpha
        state.nextIndex == index -> pulse
        else -> 1f
    }
    Box(
        modifier = Modifier
            .then(
                if (framed) {
                    Modifier
                        .background(CreamElevated, RoundedCornerShape(8.dp))
                        .border(2.dp, WarmMuted, RoundedCornerShape(8.dp))
                } else {
                    Modifier
                },
            )
            .clickable(enabled = state.isTappable(index)) { onTap(index) }
            .padding(CountingField.CellPadDp.dp)
            .testTag("counting_object_$index"),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = emoji, fontSize = sizeSp.sp, modifier = Modifier.alpha(alpha))
    }
}
