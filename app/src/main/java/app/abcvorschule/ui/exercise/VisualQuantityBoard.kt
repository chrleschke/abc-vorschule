package app.abcvorschule.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.WarmInk

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
    onResolve: (() -> Unit)? = null,
    ttsAvailable: Boolean = false,
    speaking: Boolean = false,
    onSpeakPrompt: () -> Unit = {},
) {
    ExerciseStage(
        modifier = modifier,
        prompt = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MathQuantityPrompt(emoji, left, right, operation, emojiSizeSp = 44)
            }
        },
        answers = {
            // Solutions must match the prompt's representation: once either operand is
            // symbolic, every answer tile shows a single icon too, never a mix of
            // "one icon" and "nine icons" for the same round.
            val forceSymbolic = QuantityRepresentation.forceSymbolicFor(left, right)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
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
                            .clickable { onChoose(value) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("math_choice_$value"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QuantityCluster(
                            emoji = emoji,
                            count = value,
                            emojiSizeSp = 28,
                            showNumber = true,
                            numberColor = if (correct) Cream else WarmInk,
                            forceSymbolic = forceSymbolic,
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
) {
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
        MultiplicationMatrixGrid(emoji = emoji, rows = left, columns = right)
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
 * learns multiplication as area, not as a chain of additions.
 */
@Composable
fun MultiplicationMatrixGrid(
    emoji: String,
    rows: Int,
    columns: Int,
    modifier: Modifier = Modifier,
) {
    val sizeSp = MultiplicationMatrix.emojiSizeSp(columns)
    Column(
        modifier = modifier.testTag("multiplication_matrix"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        repeat(rows) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                repeat(columns) {
                    Text(
                        text = emoji,
                        fontSize = sizeSp.sp,
                        modifier = if (MultiplicationMatrix.isConcreteRow(row)) {
                            Modifier
                        } else {
                            Modifier.alpha(MultiplicationMatrix.GhostAlpha)
                        },
                    )
                }
            }
        }
    }
}
