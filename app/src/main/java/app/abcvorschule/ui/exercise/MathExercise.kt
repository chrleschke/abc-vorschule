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
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.session.ScheduledTrainer
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.SoftSand

/**
 * Trainer 6 — Rechnen. Pure quantity arithmetic: emoji groups and numerals only,
 * never words to read or build. Singular/plural lives in the spoken prompt.
 */
@Composable
fun MathExercise(
    trainer: ScheduledTrainer,
    round: CountAddRound,
    roundIndex: Int,
    icon: String,
    scaffold: ScaffoldLevel,
    showSymbolPrompt: Boolean,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (distance: Int?, resolved: Boolean, correct: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "${trainer.spec.id}#$roundIndex-${round.left}+${round.right}"
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var locked by remember(roundKey) { mutableStateOf(false) }
    // Tracked apart from `locked`, which a resolve also sets: giving up must not
    // light up the green confirmation meant for a correct answer.
    var solved by remember(roundKey) { mutableStateOf<Int?>(null) }
    val usePad = MathHinting.usesNumberPad(scaffold)
    val choices = remember(roundKey) { MathHinting.threeChoices(round.answer).shuffled() }

    fun handleGuess(guess: Int) {
        if (locked) return
        if (guess == round.answer) {
            locked = true
            solved = guess
            onResult(0, false, true)
        } else {
            onSpeak(guess.toString())
            misses += 1
            onResult(MathHinting.distance(round.answer, guess), false, false)
        }
    }

    fun resolve() {
        if (locked) return
        locked = true
        onResult(null, true, false)
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
                        text = "${round.left} + ${round.right} = ?",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuantityCluster(emoji = icon, count = round.left, emojiSizeSp = 40)
                    Text("+", style = MaterialTheme.typography.displayMedium, color = SoftSand)
                    QuantityCluster(emoji = icon, count = round.right, emojiSizeSp = 40)
                }
            },
            answers = {
                NumberPad(
                    onSubmit = { handleGuess(it) },
                    resetToken = NumberPadInput.resetToken(roundKey, misses),
                    solved = solved != null,
                )
                if (misses >= 2 && !locked) {
                    AbcResolveButton(onClick = ::resolve)
                }
            },
        )
    } else {
        VisualQuantityBoard(
            emoji = icon,
            left = round.left,
            right = round.right,
            choices = choices,
            onChoose = { handleGuess(it) },
            solved = solved,
            missCount = misses,
            locked = locked,
            onResolve = ::resolve,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = onSpeakPrompt,
            modifier = modifier.fillMaxSize(),
        )
    }
}
