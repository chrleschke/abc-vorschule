package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.content.Atom
import app.abcvorschule.content.TaskType
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.session.ScheduledTask

@Composable
fun MathExercise(
    task: ScheduledTask,
    atom: Atom?,
    scaffold: ScaffoldLevel,
    showSymbolPrompt: Boolean,
    onResult: (distance: Int?, resolved: Boolean, correct: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = task.template
    val left = template.left ?: 0
    val right = template.right ?: 0
    val answer = template.answer ?: (left + right)
    val emoji = atom?.emoji ?: "⭐"
    var misses by remember(template.id) { mutableIntStateOf(0) }

    val forceBeginner = scaffold == ScaffoldLevel.Beginner
    val usePad = template.type == TaskType.number_entry &&
        MathHinting.usesNumberPad(scaffoldBeginnerForced = forceBeginner, preferVisual = false)

    val choices = remember(answer) {
        val opts = linkedSetOf(answer, (answer - 1).coerceAtLeast(1), answer + 1, (answer - 2).coerceAtLeast(1))
        opts.toList().shuffled()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (showSymbolPrompt) {
            Text(
                text = template.promptSymbols ?: "$left + $right = ?",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        if (usePad) {
            NumberPad(
                onSubmit = { guess ->
                    if (guess == answer) {
                        onResult(0, false, true)
                    } else {
                        misses += 1
                        onResult(MathHinting.distance(answer, guess), false, false)
                    }
                },
            )
        } else {
            VisualQuantityBoard(
                emoji = emoji,
                left = left,
                right = right,
                choices = choices,
                onChoose = { guess ->
                    if (guess == answer) {
                        onResult(0, false, true)
                    } else {
                        misses += 1
                        onResult(MathHinting.distance(answer, guess), false, false)
                    }
                },
            )
        }
        if (misses >= 2) {
            TextButton(onClick = { onResult(null, true, false) }) {
                Text(stringResource(R.string.resolve))
            }
        }
    }
}
