package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.abcvorschule.content.Atom
import app.abcvorschule.content.TaskType
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.session.ScheduledTask
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.SoftSand

@Composable
fun MathExercise(
    task: ScheduledTask,
    atom: Atom?,
    scaffold: ScaffoldLevel,
    showSymbolPrompt: Boolean,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (distance: Int?, resolved: Boolean, correct: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = task.template
    val left = template.left ?: 0
    val right = template.right ?: 0
    val answer = template.answer ?: (left + right)
    val emoji = atom?.emoji ?: "*"
    var misses by remember(template.id) { mutableIntStateOf(0) }
    var locked by remember(template.id) { mutableStateOf(false) }

    val forceBeginner = scaffold == ScaffoldLevel.Beginner
    val usePad = template.type == TaskType.number_entry &&
        MathHinting.usesNumberPad(scaffoldBeginnerForced = forceBeginner, preferVisual = false)

    val choices = remember(template.id) {
        MathHinting.threeChoices(answer).shuffled()
    }

    fun handleGuess(guess: Int) {
        if (locked) return
        if (guess == answer) {
            locked = true
            // Success pipeline speaks the answer once, then shows the star.
            onResult(0, false, true)
        } else {
            onSpeak(guess.toString())
            misses += 1
            onResult(MathHinting.distance(answer, guess), false, false)
        }
    }

    if (usePad) {
        ExerciseStage(
            modifier = modifier.fillMaxSize(),
            prompt = {
                TaskPromptChrome(
                    title = null,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeakPrompt = onSpeakPrompt,
                )
                if (showSymbolPrompt) {
                    Text(
                        text = template.promptSymbols ?: "$left + $right = ?",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuantityCluster(emoji = emoji, count = left, emojiSizeSp = 40)
                    Text("+", style = MaterialTheme.typography.displayMedium, color = SoftSand)
                    QuantityCluster(emoji = emoji, count = right, emojiSizeSp = 40)
                }
            },
            answers = {
                NumberPad(onSubmit = { handleGuess(it) })
                if (misses >= 2) {
                    AbcResolveButton(onClick = {
                        if (!locked) {
                            locked = true
                            onResult(null, true, false)
                        }
                    })
                }
            },
        )
    } else {
        VisualQuantityBoard(
            emoji = emoji,
            left = left,
            right = right,
            choices = choices,
            onChoose = { handleGuess(it) },
            missCount = misses,
            onResolve = {
                if (!locked) {
                    locked = true
                    onResult(null, true, false)
                }
            },
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = onSpeakPrompt,
            modifier = modifier.fillMaxSize(),
        )
    }
}
