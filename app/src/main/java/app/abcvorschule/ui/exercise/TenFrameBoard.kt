package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.theme.SkyBlue

/** Deckkraft, auf die der Puls-Hinweis herunterblendet — wie in [CountingAid]. */
private const val TenFramePulseLowAlpha = 0.35f
private const val TenFramePulseMillis = 700

/**
 * Das Zehnerfeld: die Additionsaufgabe als Bild, von der ersten Sekunde an
 * antippbar (design doc 2026-08-30-zehnerfeld-addition).
 *
 * Der erste Summand steht als echte Bilder da, der zweite als Platzhalter im
 * selben Feld — die Menge läuft also **weiter**, statt in einem zweiten Block
 * daneben zu beginnen. Genau daran wird der Zehnerübergang sichtbar: bei „16 + 8"
 * hat die zweite Zeile noch vier Plätze frei, und das ist die Zerlegung
 * 8 = 4 + 4, bevor ein Wort darüber gesagt ist.
 *
 * Volle Zeilen liegen in **einem** Rahmen — sie sind die Zehner, und ein Rahmen
 * je Zeile machte aus zwei Zehnern zwei Dinge statt eines Stapels. Der laufende
 * Stand steht als Marke an der unteren rechten Ecke dieses Rahmens, nicht in
 * einer Rinne daneben: eine zweistellige Rinne kostete gut 40dp Breite, und bei
 * zehn Objekten pro Zeile ist Breite das, wovon am wenigsten da ist.
 *
 * Reine Darstellung von [state]; jede Regel darüber, was ein Tipp bewirkt, lebt
 * in [TenFrameState], jede Größenrechnung in [TenFrame].
 */
@Composable
fun TenFrameBoard(
    emoji: String,
    state: TenFrameState,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Bewusst ohne `by`: der Wert wird durchgereicht und erst in der Zeichenphase
    // gelesen (§10, wie CountingAid). Als Float gelesen rekomponierten sonst bis
    // zu 30 Zellen mit jedem Animationsframe.
    val pulse = rememberInfiniteTransition(label = "ten_frame_pulse").animateFloat(
        initialValue = 1f,
        targetValue = TenFramePulseLowAlpha,
        animationSpec = infiniteRepeatable(tween(TenFramePulseMillis), RepeatMode.Reverse),
        label = "ten_frame_pulse_alpha",
    )

    BoxWithConstraints(modifier = modifier.testTag("ten_frame")) {
        // Gemessen statt geschätzt: bei zehn Objekten pro Zeile ist die Breite die
        // einzige enge Schranke, und eine konservative Konstante verschenkte auf
        // einem normalen Telefon spürbar Bildgröße.
        val available = if (maxWidth.value > 0f) maxWidth.value else TenFrame.FallbackFieldWidthDp
        val sizeSp = TenFrame.emojiSizeSp(available)
        val rows = TenFrame.rows(state.total)
        val fullRows = TenFrame.fullRowCount(state.realCount)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TenFrame.RowGapDp.dp),
        ) {
            var index = 0
            if (fullRows > 0) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(TenFrame.RowGapDp.dp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .border(2.dp, SkyBlue, RoundedCornerShape(10.dp))
                        .padding(6.dp),
                ) {
                    repeat(fullRows) { row ->
                        TenFrameRow(emoji, rows[row], index, sizeSp, state, pulse, onTap)
                        index += rows[row]
                    }
                    // Der Stand steht innen an der unteren Kante — dieselbe Zahl,
                    // die beim Einrasten gesprochen wird.
                    Text(
                        text = state.fullTens.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        color = SkyBlue,
                        modifier = Modifier.testTag("ten_frame_total"),
                    )
                }
            }
            (fullRows until rows.size).forEach { row ->
                TenFrameRow(emoji, rows[row], index, sizeSp, state, pulse, onTap)
                index += rows[row]
            }
        }
    }
}

/** Eine Zeile des Feldes, mit der Fünfer-Lücke in der Mitte. */
@Composable
private fun TenFrameRow(
    emoji: String,
    length: Int,
    startIndex: Int,
    sizeSp: Int,
    state: TenFrameState,
    pulse: State<Float>,
    onTap: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TenFrame.CellGapDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(length) { column ->
            TenFrameCell(emoji, startIndex + column, sizeSp, state, pulse, onTap)
            // Die breitere Lücke ist die „Kraft der Fünf" — ohne sie sind zehn
            // Objekte eine Reihe, die man abzählen muss, statt zweier Hände.
            if (TenFrame.hasFiveGapAfter(column) && column < length - 1) {
                // Der Spacer ist selbst ein Kind der Row, `spacedBy` legt also
                // links und rechts je [TenFrame.CellGapDp] dazu — abziehen, sonst
                // ist die Lücke breiter als gerechnet und die Zeile läuft über.
                Spacer(Modifier.width((TenFrame.FiveGapDp - 2 * TenFrame.CellGapDp).dp))
            }
        }
    }
}

/**
 * Eine Zelle. Gesetzte Objekte sind echt, offene sind Platzhalter — dieselbe
 * Geister-Logik, mit der [MultiplicationMatrixGrid] seine Reihen zeigt: das Bild
 * ist schon da, das Kind macht es wahr.
 */
@Composable
private fun TenFrameCell(
    emoji: String,
    index: Int,
    sizeSp: Int,
    state: TenFrameState,
    pulse: State<Float>,
    onTap: (Int) -> Unit,
) {
    val real = state.isReal(index)
    val tappable = state.isTappable(index)
    val cell = TenFrame.cellSizeDp(sizeSp).dp
    val hit = TenFrame.hitTargetDp(sizeSp).dp
    // Die Trefferfläche ragt über die Zelle hinaus und damit über ihre Nachbarn.
    // Folgenlos, weil zu jedem Zeitpunkt genau eine Zelle auf Tipps reagiert —
    // und ein Vorschulkind trifft keine 25dp.
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.size(cell),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(if (tappable) hit else cell)
                .then(
                    if (tappable) {
                        Modifier.clickable(
                            interactionSource = interaction,
                            indication = null,
                        ) { onTap(index) }
                    } else {
                        Modifier
                    },
                )
                .testTag("ten_frame_cell_$index"),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = emoji,
                fontSize = sizeSp.sp,
                modifier = Modifier.graphicsLayer {
                    // Der Puls wird hier in der Zeichenphase gelesen, nicht in der
                    // Komposition — derselbe Layer, den `Modifier.alpha(…)` aufmacht.
                    alpha = when {
                        tappable -> pulse.value
                        real -> 1f
                        else -> MultiplicationMatrix.GhostAlpha
                    }
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
            )
        }
    }
}
