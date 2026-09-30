package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalTextStyle
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SilboEmoji
import app.abcvorschule.ui.theme.SilboFibel
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/** Deckkraft eines bereits gezählten Objekts. Deutlich sichtbarer als ein
 * Geister-Platzhalter ([MultiplicationMatrix.GhostAlpha]) — „schon gezählt" darf
 * nicht wie „gar nicht da" aussehen. */
const val CountedAlpha = 0.45f

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
        animationSpec = tween(durationMillis = AbcMotion.QuickMs),
        label = "math_choice_lock_opacity",
    )
    // Die Kacheln müssen ihre Größe aus der Bühne beziehen, nicht aus einer festen
    // Zahl: drei 28sp-Kacheln passen auf einem 360dp-Telefon nicht nebeneinander,
    // und was umbricht, verdoppelt den Antwortblock und drückt den Aufgabenblock
    // auf null ([MathBoardSizing]). `BoxWithConstraints` liegt außen um die Bühne,
    // weil `ExerciseStage` seinem Antwortslot keine Maße mitgibt.
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val stageWidthDp = maxWidth.value
        val stageHeightDp = maxHeight.value
        val tileGap = MathBoardSizing.tileGapDp(stageWidthDp).dp
        // Gerendert gemessen statt aus sp und `fontScale` gerechnet: Androids
        // Schriftskalierung ist nichtlinear, die Umrechnung kennt nur `Density`
        // ([MathBoardSizing], „Warum dp und nicht sp").
        val numeralLineDp = with(density) {
            MaterialTheme.typography.headlineMedium.lineHeight.toDp().value
        }
        val operatorWidthDp = with(density) {
            MaterialTheme.typography.displayMedium.fontSize.toDp().value
        } * WordFrameSizing.GlyphAspect
        // §8: gleiche Dimensionen der Buttons. Shorter clusters pad up to the
        // tallest choice with invisible ghost rows, so tile size never hints at
        // the answer and the numerals share one baseline. Damit zahlt jede Kachel
        // die Höhe der größten Menge — genau die Zeilenzahl, gegen die gerechnet wird.
        val tallestCluster = choices.maxOf { QuantityGrouping.clusters(it).size }
        val choiceLayout = MathBoardSizing.solveChoices(
            stageWidthDp = stageWidthDp,
            stageHeightDp = stageHeightDp,
            rows = tallestCluster,
            numeralLineDp = numeralLineDp,
        )
        // Solutions must match the prompt's representation: once either operand or
        // any choice is symbolic, every answer tile shows a single icon too, never
        // a mix of "one icon" and "nine icons" for the same round. Dasselbe gilt,
        // wenn erst die Bühne die Kacheln symbolisch macht — dann geht die Aufgabe
        // mit, sonst stünde eine Mengengruppe neben einem einzelnen Symbol.
        val forceSymbolic = QuantityRepresentation.forceSymbolicForChoices(left, right, choices) ||
            choiceLayout.symbolic
        val equalRows = if (forceSymbolic) 0 else tallestCluster
        // Der Aufgabenblock bekommt, was der Antwortblock übrig lässt — und der ist
        // in `ExerciseStage` das ungewichtete Kind, also derjenige, der zuerst nimmt.
        val promptRows = if (forceSymbolic) {
            1
        } else {
            maxOf(QuantityGrouping.clusters(left).size, QuantityGrouping.clusters(right).size)
        }
        val promptCells = if (forceSymbolic) {
            2
        } else {
            minOf(left, 2) + minOf(right, 2)
        }
        val promptBaseDp = with(density) {
            QuantityGrouping.promptEmojiSizeSp(44, left, right).sp.toDp().value
        } * MathBoardSizing.EmojiAspect
        val promptEmojiDp = MathBoardSizing.solvePromptEmojiDp(
            baseDp = promptBaseDp,
            stageWidthDp = stageWidthDp,
            stageHeightDp = stageHeightDp,
            rows = promptRows,
            cells = promptCells,
            answersHeightDp = MathBoardSizing.answersHeightDp(
                layout = choiceLayout,
                rows = tallestCluster,
                numeralLineDp = numeralLineDp,
            ),
            operatorWidthDp = operatorWidthDp,
            numeralLineDp = numeralLineDp,
        )

        ExerciseStage(
            modifier = Modifier.fillMaxSize(),
            promptChrome = {
                TaskPromptChrome(
                    title = null,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeakPrompt = onSpeakPrompt,
                )
            },
            prompt = {
                Box(modifier = Modifier.testTag("math_prompt")) {
                    MathQuantityPrompt(
                        emoji, left, right, operation,
                        emojiSize = promptEmojiDp.dp,
                        forceSymbolic = forceSymbolic,
                    )
                }
            },
            answers = {
                // `Row`, nicht `FlowRow`: drei Optionen sind Produktregel (§8), und ein
                // Umbruch ist hier nie die richtige Antwort — er macht aus einer Reihe
                // gleicher Kacheln zwei ungleiche Zeilen. Passt es nicht, schrumpfen
                // die Emojis (oben gelöst), nicht die Reihe.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        tileGap,
                        Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .alpha(answerOpacity)
                        .testTag("math_choices"),
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
                                emojiSize = choiceLayout.emojiDp.dp,
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
}

@Composable
fun QuantityCluster(
    emoji: String,
    count: Int,
    /** Gerenderte Kantenlänge eines Emojis — dp, nicht sp ([MathBoardSizing]). */
    emojiSize: Dp,
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
            EmojiGlyph(emoji = emoji, size = emojiSize)
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
                    EmojiGlyph(emoji = emoji, size = emojiSize)
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
                repeat(2) { EmojiGlyph(emoji = emoji, size = emojiSize) }
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
 * Ein Emoji in gerenderter Größe — mit **eigener** Zeilenhöhe. Ohne sie erbt der
 * Text die 28sp aus `bodyLarge`, und dann ist jede Emoji-Zeile mindestens 28sp
 * hoch, wie klein das Bild auch wird: die Mengengruppe spart beim Schrumpfen keine
 * Höhe, und [MathBoardSizing] rechnet an der Wirklichkeit vorbei. Gemessen bleiben
 * Breite und Höhe damit unter [MathBoardSizing.EmojiAspect] mal Schriftgröße, also
 * innerhalb von [size].
 */
@Composable
internal fun EmojiGlyph(emoji: String, size: Dp, modifier: Modifier = Modifier) {
    val fontSize = with(LocalDensity.current) { (size / MathBoardSizing.EmojiAspect).toSp() }
    Text(
        text = emoji,
        fontFamily = SilboEmoji,
        fontSize = fontSize,
        lineHeight = fontSize,
        style = LocalTextStyle.current.copy(
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
        ),
        modifier = modifier,
    )
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
    /** Gerenderte Kantenlänge eines Emojis — dp, nicht sp ([MathBoardSizing]). */
    emojiSize: Dp,
    /** Von außen erzwungen, wenn schon die Antwort-Kacheln symbolisch sind: eine
     * Mengengruppe neben einem einzelnen Symbol ist die verwirrende Mischung, die
     * [QuantityRepresentation.forceSymbolicForChoices] gerade verhindern soll. */
    forceSymbolic: Boolean = false,
) {
    if (operation == MathOperation.Multiply) {
        // Die Matrix lebt von der Fläche — ohne Bildwort tut es das Zählplättchen.
        MultiplicationMatrixGrid(emoji = emoji.ifBlank { NeutralCountingToken }, rows = left, columns = right)
        return
    }
    val symbolic = forceSymbolic || QuantityRepresentation.forceSymbolicFor(left, right)
    Row(
        horizontalArrangement = Arrangement.spacedBy(MathBoardSizing.PromptGapDp.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QuantityCluster(emoji = emoji, count = left, emojiSize = emojiSize, forceSymbolic = symbolic)
        Text(operation.symbol, style = MaterialTheme.typography.displayMedium, color = WarmInk)
        QuantityCluster(emoji = emoji, count = right, emojiSize = emojiSize, forceSymbolic = symbolic)
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
                    // Zeilennummern sind Ziffern, also Lerninhalt (§8): Lernschrift.
                    style = MaterialTheme.typography.labelLarge.copy(fontFamily = SilboFibel),
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
                        fontFamily = SilboEmoji,
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
