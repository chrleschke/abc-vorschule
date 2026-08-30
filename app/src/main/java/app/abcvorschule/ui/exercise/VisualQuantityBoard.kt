package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/** Deckkraft eines bereits gezählten Objekts. Deutlich sichtbarer als ein
 * Geister-Platzhalter ([MultiplicationMatrix.GhostAlpha]) — „schon gezählt" darf
 * nicht wie „gar nicht da" aussehen. */
const val CountedAlpha = 0.45f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualQuantityBoard(
    emoji: String,
    left: Int,
    right: Int,
    operation: MathOperation,
    choices: List<Int>,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The chosen value once it turned out to be correct — that tile turns green. */
    solved: Int? = null,
    missCount: Int = 0,
    locked: Boolean = false,
    /** False during the audio lock — separate from [locked] ("already answered"):
     * this one gates the initial listen-first window (design doc). */
    interactionLocked: Boolean = false,
    onResolve: (() -> Unit)? = null,
    ttsAvailable: Boolean = false,
    speaking: Boolean = false,
    onSpeakPrompt: () -> Unit = {},
) {
    val answerOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "math_choice_lock_opacity",
    )
    ExerciseStage(
        modifier = modifier,
        promptChrome = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
        },
        prompt = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MathQuantityPrompt(
                    emoji, left, right, operation,
                    emojiSizeSp = QuantityGrouping.promptEmojiSizeSp(44, left, right),
                )
            }
        },
        answers = {
            // Solutions must match the prompt's representation: once either operand or
            // any choice is symbolic, every answer tile shows a single icon too, never
            // a mix of "one icon" and "nine icons" for the same round.
            val forceSymbolic = QuantityRepresentation.forceSymbolicForChoices(left, right, choices)
            // §8: gleiche Dimensionen der Buttons. Shorter clusters pad up to the
            // tallest choice with invisible ghost rows, so tile size never hints at
            // the answer and the numerals share one baseline.
            val equalRows = if (forceSymbolic) 0 else choices.maxOf { QuantityGrouping.clusters(it).size }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.alpha(answerOpacity),
            ) {
                choices.forEach { value ->
                    // The picked tile confirms itself in green, so the child sees *which*
                    // answer was right while it is being spoken. A wrong pick is never
                    // marked red — misses stay spoken-only feedback.
                    val correct = solved == value
                    Column(
                        modifier = Modifier
                            .background(
                                color = if (correct) LeafGreen else CreamElevated,
                                shape = RoundedCornerShape(18.dp),
                            )
                            .clickable(enabled = !interactionLocked) { onChoose(value) }
                            .defaultMinSize(minWidth = AbcDimens.kidTouch, minHeight = AbcDimens.kidTouch)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("math_choice_$value"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                    ) {
                        QuantityCluster(
                            emoji = emoji,
                            count = value,
                            emojiSizeSp = 28,
                            showNumber = true,
                            numberColor = if (correct) Cream else WarmInk,
                            forceSymbolic = forceSymbolic,
                            minClusterRows = equalRows,
                        )
                    }
                }
            }
            if (missCount >= 2 && onResolve != null && !locked) {
                AbcResolveButton(onClick = onResolve)
            }
        },
    )
}

@Composable
fun QuantityCluster(
    emoji: String,
    count: Int,
    emojiSizeSp: Int,
    modifier: Modifier = Modifier,
    showNumber: Boolean = true,
    numberColor: Color = WarmInk,
    /** Set when the other number in this round is already symbolic, so both
     * sides of the equation stay visually consistent. */
    forceSymbolic: Boolean = false,
    /** Pad up to this many emoji rows with invisible pair rows, so sibling
     * tiles keep equal height and width regardless of their count (§8). */
    minClusterRows: Int = 0,
) {
    // Ohne Bildwort zeigt die Menge nur ihre Ziffer. Ein Emoji hier wäre bei den
    // Zahlen, um die es dann geht, ohnehin ein einzelnes Symbol neben der Zahl —
    // also Dekoration, die eine Szene behauptet, die es nicht gibt.
    if (emoji.isBlank()) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.displaySmall,
                color = numberColor,
            )
        }
        return
    }
    if (forceSymbolic || QuantityRepresentation.isSymbolic(count)) {
        Column(
            modifier = modifier,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(text = emoji, fontSize = emojiSizeSp.sp)
            if (showNumber) Text(text = count.toString(), style = MaterialTheme.typography.headlineMedium, color = numberColor)
        }
        return
    }
    val clusters = QuantityGrouping.clusters(count)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        clusters.forEach { size ->
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(size) {
                    Text(text = emoji, fontSize = emojiSizeSp.sp)
                }
            }
        }
        // Ghost rows between the emojis and the numeral: sizes with fewer rows
        // grow to match their tallest sibling, and all numerals line up.
        repeat((minClusterRows - clusters.size).coerceAtLeast(0)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.alpha(0f),
            ) {
                repeat(2) { Text(text = emoji, fontSize = emojiSizeSp.sp) }
            }
        }
        if (showNumber) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.headlineMedium,
                color = numberColor,
            )
        }
    }
}

/**
 * One visual equation. Multiplication shows the two-dimensional matrix — "left
 * Reihen mit je right Stück" — instead of a symbol row, so both factors stay
 * visible as rows × columns.
 */
@Composable
fun MathQuantityPrompt(
    emoji: String,
    left: Int,
    right: Int,
    operation: MathOperation,
    emojiSizeSp: Int,
) {
    if (operation == MathOperation.Multiply) {
        // Die Matrix lebt von der Fläche — ohne Bildwort tut es das Zählplättchen.
        MultiplicationMatrixGrid(emoji = emoji.ifBlank { NeutralCountingToken }, rows = left, columns = right)
        return
    }
    val forceSymbolic = QuantityRepresentation.forceSymbolicFor(left, right)
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuantityCluster(emoji = emoji, count = left, emojiSizeSp = emojiSizeSp, forceSymbolic = forceSymbolic)
        Text(operation.symbol, style = MaterialTheme.typography.displayMedium, color = WarmInk)
        QuantityCluster(emoji = emoji, count = right, emojiSizeSp = emojiSizeSp, forceSymbolic = forceSymbolic)
    }
}

/**
 * "rows mal columns" als Matrix: the first row shows real objects, every further
 * row only ghost placeholders — the child completes the picture mentally and
 * learns multiplication as area, not as a chain of additions. The task ("3 × 4")
 * sits above the grid, and each row carries its number in a gutter on the left,
 * so both factors stay readable while the child counts.
 */
@Composable
fun MultiplicationMatrixGrid(
    emoji: String,
    rows: Int,
    columns: Int,
    modifier: Modifier = Modifier,
    /** Gesetzt, sobald die Zähl-Hilfe offen ist: dann sind alle Zellen echt und
     * antippbar — auch die sonst geisterhaften Reihen. Genau der Schritt, den das
     * Kind vorher im Kopf nicht geschafft hat. */
    counting: CountingState? = null,
    onTapCell: (Int) -> Unit = {},
    /** Deckkraft des Puls-Hinweises auf der nächsten offenen Reihe. Als State
     * durchgereicht statt als Float: gelesen wird er unten in der Zeichenphase,
     * sonst rekomponierte der endlose Puls die ganze Matrix Frame für Frame
     * (§10, gleiche Pflicht wie bei SlotFillMorph). `null` heißt „kein Puls" —
     * die Matrix steht dann im Aufgabenblock statt in der Zähl-Hilfe. */
    pulseAlpha: State<Float>? = null,
) {
    // In der Zähl-Hilfe hat die Matrix den Aufgabenblock für sich und darf deutlich
    // größer werden — im Prompt teilt sie ihn mit dem Antwortbereich.
    val sizeSp = if (counting == null) {
        MultiplicationMatrix.emojiSizeSp(columns)
    } else {
        CountingField.matrixEmojiSizeSp(rows, columns)
    }
    Column(
        modifier = modifier.testTag("multiplication_matrix"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = MultiplicationMatrix.equationLabel(rows, columns),
            style = MaterialTheme.typography.headlineMedium,
            color = WarmInk,
            modifier = Modifier
                .padding(bottom = 6.dp)
                .testTag("multiplication_equation"),
        )
        repeat(rows) { row ->
            // In der Zähl-Hilfe ist die **Reihe** die Einheit, nicht die Zelle:
            // zwanzig Objekte einzeln anzutippen trainiert Zählen in Einerschritten,
            // also genau das, was Multiplikation nicht ist. Reihenweise ist es
            // Zählen in Schritten — "je vier: vier, acht, zwölf".
            //
            // Anders als bei Plus und Minus wird eine Reihe beim Antippen **echt**,
            // statt zu verblassen: Malnehmen ist Auffüllen, und die Geisterreihen
            // aus §8 sind genau das, was das Kind vervollständigen soll.
            val pulsing = counting != null && counting.nextIndex == row
            val restingRowAlpha = when {
                counting == null ->
                    if (MultiplicationMatrix.isConcreteRow(row)) 1f else MultiplicationMatrix.GhostAlpha
                counting.isTapped(row) -> 1f
                else -> MultiplicationMatrix.GhostAlpha
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .then(
                        if (counting == null) Modifier else Modifier.clickable { onTapCell(row) },
                    )
                    .testTag("counting_row_$row"),
            ) {
                // Full opacity even beside a ghost row: the numerals are the
                // counting aid, so they must not fade along with the placeholders.
                Text(
                    text = MultiplicationMatrix.rowLabel(row),
                    style = MaterialTheme.typography.labelLarge,
                    color = WarmMuted,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .width(MultiplicationMatrix.RowLabelGutterDp.dp)
                        .testTag("multiplication_row_label_${MultiplicationMatrix.rowLabel(row)}"),
                )
                // Die Deckkraft sitzt auf den Bildern, nicht auf der ganzen Zeile:
                // die Zeilennummer behält volle Deckkraft, auch neben einer
                // Geisterreihe — sie ist die Zählhilfe, kein Teil des Platzhalters (§8).
                repeat(columns) {
                    Text(
                        text = emoji,
                        fontSize = sizeSp.sp,
                        // Derselbe Layer, den `Modifier.alpha(…)` aufmacht — nur
                        // wird der Puls hier in der Zeichenphase gelesen.
                        modifier = Modifier.graphicsLayer {
                            alpha = if (pulsing) pulseAlpha?.value ?: 1f else restingRowAlpha
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        },
                    )
                }
            }
        }
    }
}
