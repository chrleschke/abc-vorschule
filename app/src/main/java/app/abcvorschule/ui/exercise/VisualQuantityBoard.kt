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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand

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
                                color = if (correct) SoftMint else NightElevated,
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
                            numberColor = if (correct) NightInk else SoftSand,
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
    numberColor: Color = SoftSand,
) {
    if (QuantityRepresentation.isSymbolic(count)) {
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

/** One visual equation. Multiplication deliberately shows equal groups, not a giant total. */
@Composable
fun MathQuantityPrompt(
    emoji: String,
    left: Int,
    right: Int,
    operation: MathOperation,
    emojiSizeSp: Int,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (operation == MathOperation.Multiply) {
            QuantityCluster(emoji = emoji, count = right, emojiSizeSp = emojiSizeSp)
        } else {
            QuantityCluster(emoji = emoji, count = left, emojiSizeSp = emojiSizeSp)
        }
        Text(operation.symbol, style = MaterialTheme.typography.displayMedium, color = SoftSand)
        if (operation == MathOperation.Multiply) {
            Text(left.toString(), style = MaterialTheme.typography.headlineMedium, color = SoftSand)
        } else {
            QuantityCluster(emoji = emoji, count = right, emojiSizeSp = emojiSizeSp)
        }
    }
}
