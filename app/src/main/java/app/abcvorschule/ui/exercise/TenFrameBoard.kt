package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
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
 * je Zeile machte aus zwei Zehnern zwei Dinge statt eines Stapels. An jeder
 * vollen Zeile steht rechts unter ihr der laufende Stand (`10`, `20`, `30`).
 *
 * **Der Block steht still.** Rahmen, Innenabstand und Standmarken kosten Höhe;
 * entstünde diese Höhe erst beim Einrasten des ersten Zehners, wanderte der
 * zentrierte Aufgabenblock um gut 20dp — genau in dem Moment, in dem das Kind
 * hinsieht und den nächsten Platzhalter treffen will. Darum reserviert das Feld
 * die Höhe seines **Endzustands** von Anfang an: alle Zeilen, die je voll werden
 * können, liegen samt Innenabstand und Markenslot schon in der Rahmengruppe, und
 * der Rahmen selbst wird nur noch *gezeichnet* (`drawBehind`) — er wächst über
 * die verdienten Zeilen, ohne irgendetwas zu verschieben. Dasselbe Prinzip wie
 * `holdTallest` im Wort-Bauer, nur gerechnet statt gemessen: hier ist der
 * Endzustand bekannt.
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
    /**
     * False während des Audio-Locks und nach der Entscheidung der Runde: keine
     * Zelle ist antippbar, keine pulsiert, und das Feld blendet ab. Ein voll
     * leuchtender Platzhalter, der jeden Tipp lautlos schluckt, wäre ein stummer
     * No-Op — und nach dem Lösen pulsierte er neben der grünen Bestätigung weiter.
     */
    enabled: Boolean = true,
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
    // Derselbe Zielwert und dieselbe Dauer wie im Antwortblock: bei der gemeinsamen
    // Sperre (Audio-Lock zum Rundenstart) blenden beide Blöcke miteinander ab
    // statt gegeneinander.
    val opacity by animateFloatAsState(
        targetValue = if (enabled) 1f else StageLockedAlpha,
        animationSpec = tween(durationMillis = StageLockFadeMillis),
        label = "ten_frame_lock_opacity",
    )

    BoxWithConstraints(modifier = modifier.alpha(opacity).testTag("ten_frame")) {
        // Gemessen statt geschätzt: bei zehn Objekten pro Zeile ist die Breite die
        // einzige enge Schranke, und eine konservative Konstante verschenkte auf
        // einem normalen Telefon spürbar Bildgröße.
        val available = if (maxWidth.value > 0f) maxWidth.value else TenFrame.FallbackFieldWidthDp
        val sizeSp = TenFrame.emojiSizeSp(available)
        val fontScale = LocalDensity.current.fontScale
        val renderedSp = TenFrame.renderedEmojiSp(sizeSp, fontScale)
        val rows = TenFrame.rows(state.total)
        // Zeilen, die am Ende der Runde voll sein werden — sie bekommen ihre
        // Rahmen-Chrome von Anfang an, auch solange sie noch Platzhalter tragen.
        val endFullRows = TenFrame.fullRowCount(state.total)
        val fullRows = TenFrame.fullRowCount(state.realCount)
        val cellDp = TenFrame.cellSizeDp(sizeSp)
        val markSlotDp = TenFrame.markSlotDp(
            markSizeSp = MaterialTheme.typography.labelLarge.fontSize.value,
            fontScale = fontScale,
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(TenFrame.RowGapDp.dp),
        ) {
            var index = 0
            if (endFullRows > 0) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(TenFrame.RowGapDp.dp),
                    horizontalAlignment = Alignment.End,
                    modifier = Modifier
                        .drawBehind {
                            if (fullRows <= 0) return@drawBehind
                            val stroke = TenFrame.FrameBorderDp.dp.toPx()
                            val height = TenFrame
                                .framedBlockHeightDp(fullRows, cellDp, markSlotDp)
                                .dp.toPx()
                            drawRoundRect(
                                color = SkyBlue,
                                topLeft = Offset(stroke / 2f, stroke / 2f),
                                size = Size(size.width - stroke, height - stroke),
                                cornerRadius = CornerRadius(TenFrame.FrameCornerDp.dp.toPx()),
                                style = Stroke(width = stroke),
                            )
                        }
                        .padding(TenFrame.FrameInsetDp.dp),
                ) {
                    repeat(endFullRows) { row ->
                        Column(horizontalAlignment = Alignment.End) {
                            TenFrameRow(
                                emoji, rows[row], index, sizeSp, renderedSp,
                                state, enabled, pulse, onTap,
                            )
                            // Der Slot steht immer, die Zahl erscheint erst mit dem
                            // Zehner — dieselbe Zahl, die beim Einrasten gesprochen
                            // wird.
                            Box(
                                modifier = Modifier.height(markSlotDp.dp),
                                contentAlignment = Alignment.CenterEnd,
                            ) {
                                if (row < fullRows) {
                                    Text(
                                        text = ((row + 1) * TenFrame.RowSize).toString(),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = SkyBlue,
                                        modifier = Modifier.testTag("ten_frame_total_$row"),
                                    )
                                }
                            }
                        }
                        index += rows[row]
                    }
                }
            }
            (endFullRows until rows.size).forEach { row ->
                TenFrameRow(
                    emoji, rows[row], index, sizeSp, renderedSp,
                    state, enabled, pulse, onTap,
                )
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
    renderedSp: Float,
    state: TenFrameState,
    enabled: Boolean,
    pulse: State<Float>,
    onTap: (Int) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(TenFrame.CellGapDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(length) { column ->
            TenFrameCell(emoji, startIndex + column, sizeSp, renderedSp, state, enabled, pulse, onTap)
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
    renderedSp: Float,
    state: TenFrameState,
    enabled: Boolean,
    pulse: State<Float>,
    onTap: (Int) -> Unit,
) {
    val real = state.isReal(index)
    // Gesperrt ist gesperrt: keine Trefferfläche, kein Klick-Handler, kein Puls.
    val tappable = enabled && state.isTappable(index)
    val cell = TenFrame.cellSizeDp(sizeSp).dp
    val hit = TenFrame.hitTargetDp(sizeSp).dp
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier.size(cell),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                // `requiredSize`, nicht `size`: die äußere Box reicht höchstens
                // [cell] als Constraint weiter, ein `size(hit)` würde daran
                // festgeklemmt und die Trefferfläche wäre auf jedem Gerät wieder
                // nur zellgroß (~27dp auf 320dp Breite). `requiredSize` ignoriert
                // die einlaufenden Constraints und darf über die Elternbox
                // hinausragen — genau das ist hier gewollt: die Trefferfläche ragt
                // über die Nachbarzellen, was folgenlos ist, weil zu jedem
                // Zeitpunkt genau eine Zelle auf Tipps reagiert. Die *Layout*-Größe
                // bleibt die der äußeren Box, sonst schöbe die aktive Zelle ihre
                // Nachbarn beiseite und das Feld zappelte bei jedem Tipp.
                .requiredSize(if (tappable) hit else cell)
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
                // Gedeckelt statt roh: die Zelle ist gegen TenFrame.LayoutFontScale
                // gerechnet, der Text renderte sonst mit der echten fontScale und
                // liefe ab ~1.4 aus seiner Zelle (TenFrame.renderedEmojiSp).
                fontSize = renderedSp.sp,
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
