package app.abcvorschule.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/**
 * Die Zähl-Hilfe: die Aufgabenmenge, antippbar. Tritt nach
 * [MathHinting.CountingAidFromMisses] Fehlversuchen **an die Stelle** der
 * Aufgabenvisualisierung — nicht als zusätzlicher Block darunter, sonst stünde
 * dieselbe Aufgabe zweimal auf dem Schirm (PRODUCT_PRINCIPLES §9) und auf einem
 * Telefon wäre für beides ohnehin kein Platz.
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
    val sizeSp = CountingField.emojiSizeSp(operation, left, right)

    if (operation == MathOperation.Multiply) {
        MultiplicationMatrixGrid(
            emoji = emoji,
            rows = left,
            columns = right,
            modifier = modifier.testTag("counting_aid"),
            counting = state,
            onTapCell = onTap,
        )
        return
    }

    Column(
        modifier = modifier.testTag("counting_aid"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp * 2),
    ) {
        // Plus stapelt seine zwei Gruppen übereinander statt nebeneinander: fünf
        // Spalten je Gruppe wären nebeneinander zehn, und zehn Objekte quer passen
        // bei font_scale 1.3 auf kein Telefon.
        var offset = 0
        CountingField.groupSizes(operation, left, right).forEachIndexed { groupIndex, size ->
            if (groupIndex > 0) {
                Text(
                    text = operation.symbol,
                    style = MaterialTheme.typography.headlineMedium,
                    color = WarmInk,
                )
            }
            CountingGroup(
                emoji = emoji,
                size = size,
                indexOffset = offset,
                sizeSp = sizeSp,
                state = state,
                onTap = onTap,
            )
            offset += size
        }

        if (state.removeSlots > 0) {
            TakeAwayZone(
                emoji = emoji,
                slots = state.removeSlots,
                filled = state.tapped.size,
                sizeSp = sizeSp,
            )
        }

        Text(
            text = state.counted?.toString() ?: "",
            style = MaterialTheme.typography.displayLarge,
            color = WarmInk,
            modifier = Modifier.testTag("counting_total"),
        )
    }
}

/** Eine Objektgruppe in Fünferzeilen. [indexOffset] hält die Objektindizes über
 * beide Plus-Gruppen hinweg fortlaufend, damit der Zähler durchläuft. */
@Composable
private fun CountingGroup(
    emoji: String,
    size: Int,
    indexOffset: Int,
    sizeSp: Int,
    state: CountingState,
    onTap: (Int) -> Unit,
) {
    var index = indexOffset
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp),
    ) {
        CountingField.rows(size).forEach { rowSize ->
            Row(horizontalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp)) {
                repeat(rowSize) {
                    val objectIndex = index++
                    // Minus startet mit allen Objekten angehakt und nimmt weg; Plus und
                    // Malnehmen starten leer und sammeln ein. "Angetippt" heißt also je
                    // nach Rechenart das Gegenteil — verblasst ist es in beiden Fällen.
                    val used = state.isTapped(objectIndex)
                    Text(
                        text = emoji,
                        fontSize = sizeSp.sp,
                        modifier = Modifier
                            .alpha(if (used) CountedAlpha else 1f)
                            .clickable { onTap(objectIndex) }
                            .testTag("counting_object_$objectIndex"),
                    )
                }
            }
        }
    }
}

/**
 * Die Weg-Zone: genau so viele leere Plätze, wie weggenommen werden soll. Sie ist
 * der Grund, warum das Kind nicht mitzählen muss, wie viele es schon weggenommen
 * hat — volle Zone heißt fertig. Unter dem Hauptfeld statt daneben, damit alles
 * fünf Spalten breit bleibt.
 */
@Composable
private fun TakeAwayZone(emoji: String, slots: Int, filled: Int, sizeSp: Int) {
    val slotSize = (sizeSp * LocalDensity.current.fontScale).dp + 8.dp
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp),
        modifier = Modifier.testTag("take_away_zone"),
    ) {
        var placed = 0
        CountingField.rows(slots).forEach { rowSize ->
            Row(horizontalArrangement = Arrangement.spacedBy(CountingField.RowGapDp.dp)) {
                repeat(rowSize) {
                    val occupied = placed++ < filled
                    Box(
                        modifier = Modifier
                            .size(slotSize)
                            .background(CreamElevated, RoundedCornerShape(10.dp))
                            .border(2.dp, WarmMuted, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (occupied) Text(text = emoji, fontSize = sizeSp.sp)
                    }
                }
            }
        }
        Text(
            text = slots.toString(),
            style = MaterialTheme.typography.headlineMedium,
            color = WarmMuted,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
