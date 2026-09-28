package app.abcvorschule.ui.exercise

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
import androidx.compose.ui.draw.shadow
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
import app.abcvorschule.ui.world.lightIsland

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
    /** Ein angetippter Turm sagt seine Zahl. */
    onSpeakNumber: (Int) -> Unit = {},
) {
    // Ruhen statt dimmen (PromptRest): kaum gedämpft, die Ansage-Sperre hält die Taps.
    val answerOpacity = rememberRestOpacity()
    // Die Kacheln müssen ihre Größe aus der Bühne beziehen, nicht aus einer festen
    // Zahl: drei 28sp-Kacheln passen auf einem 360dp-Telefon nicht nebeneinander,
    // und was umbricht, verdoppelt den Antwortblock und drückt den Aufgabenblock
    // auf null ([MathBoardSizing]). `BoxWithConstraints` liegt außen um die Bühne,
    // weil `ExerciseStage` seinem Antwortslot keine Maße mitgibt.
    BoxWithConstraints(modifier = modifier) {
        val stageWidthDp = maxWidth.value
        val stageHeightDp = maxHeight.value
        val tileGap = MathBoardSizing.tileGapDp(stageWidthDp).dp
        // Die Zahlentürme nehmen gut zwei Fünftel der Bühne; der Rest bleibt für den
        // Lautsprecher, die Kacheln und den Auflösen-Knopf (§9: nichts rückt nach).
        val towersHeight = (stageHeightDp * TowersShareOfStage).coerceIn(MinTowersDp, MaxTowersDp).dp

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
                // Die Aufgabe als Zahlentürme (§8); die Platte mit den Zahlen ist die
                // Licht-Insel der Aufgabe.
                Box(modifier = Modifier.testTag("math_prompt")) {
                    NumberTowers(
                        left = left,
                        right = right,
                        operation = operation,
                        solved = solved != null,
                        revealed = locked,
                        enabled = !interactionLocked,
                        onSpeakNumber = onSpeakNumber,
                        height = towersHeight,
                    )
                }
            },
            answers = {
                // `Row`, nicht `FlowRow`: drei Optionen sind Produktregel (§8), und ein
                // Umbruch ist hier nie die richtige Antwort.
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
                        // Nur die Zahl: kleine Türme in den Kacheln machten es zu leicht,
                        // brachten eine dritte Farbe und mehr Unruhe (Nutzerentscheidung).
                        Box(
                            modifier = Modifier
                                .shadow(6.dp, RoundedCornerShape(18.dp))
                                .background(
                                    color = if (correct) LeafGreen else CreamElevated,
                                    shape = RoundedCornerShape(18.dp),
                                )
                                .clickable(enabled = !interactionLocked) { onChoose(value) }
                                .defaultMinSize(minWidth = AbcDimens.kidTouch, minHeight = AbcDimens.kidTouch)
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                                .testTag("math_choice_$value"),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = value.toString(),
                                style = MaterialTheme.typography.displaySmall,
                                color = if (correct) Cream else WarmInk,
                                maxLines = 1,
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

/** Anteil der Bühnenhöhe für die Zahlentürme, mit Boden und Deckel in dp. */
private const val TowersShareOfStage = 0.42f
private const val MinTowersDp = 150f
private const val MaxTowersDp = 340f

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
