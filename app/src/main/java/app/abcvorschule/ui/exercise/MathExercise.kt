package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.session.ScheduledTrainer
import app.abcvorschule.speech.GermanNumberWord
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.playBlockedBlip

/**
 * Trainer 7 — Rechnen. Die Aufgabe steht als Zahlentürme ([NumberTowers]), die Antwort
 * kommt über drei Zahlkacheln oder den Kinder-Ziffernblock. Kein Auflösen und keine
 * Zähl-Hilfe mehr (seit September 2026): die Türme lassen sich antippen und springen —
 * das ist die Hilfe, und sie ist immer da.
 */
@Composable
fun MathExercise(
    trainer: ScheduledTrainer,
    round: CountAddRound,
    roundIndex: Int,
    input: MathInputMode,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeakFeedback: (String) -> Unit,
    onSpeakCounting: (String) -> Unit,
    onResult: (MathAttempt) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAbcHaptics.current
    val operation = MathOperation.fromWireName(round.operation) ?: MathOperation.Add
    val roundKey = "${trainer.spec.id}#$roundIndex-${round.operation}-${round.left}-${round.right}"
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var solved by remember(roundKey) { mutableStateOf<Int?>(null) }
    // Seeded wie TrayOrder: die Kachel-Reihenfolge muss beim Rück-Chevron in eine
    // besuchte Runde (und nach Recreation) dieselbe sein wie beim ersten Besuch.
    val choices = remember(roundKey) {
        MathHinting.threeChoices(round.answer).shuffled(kotlin.random.Random(roundKey.hashCode()))
    }
    val speakNumber: (Int) -> Unit = { onSpeakCounting(GermanNumberWord.of(it)) }

    fun handleGuess(guess: Int) {
        if (solved != null) return
        if (guess == round.answer) {
            solved = guess
            onResult(MathAttempt(resolved = false, correct = true))
        } else {
            // Ein Fehlversuch ist nur ein Klang, keine Sprache (PRODUCT_PRINCIPLES §8):
            // das ViewModel spricht für Rechnen keinen Miss-Hinweis, also muss der Tipp
            // hier hörbar werden — mit und ohne deutsche Stimme.
            haptics.nudge()
            playBlockedBlip()
            misses += 1
            onResult(MathAttempt(resolved = false, correct = false))
        }
    }

    if (input == MathInputMode.Typed) {
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            // Die Türme bekommen ihren Anteil der Bühne wie im Kachel-Modus, nicht eine
            // feste kleine Karte; der Ziffernblock nimmt den Rest.
            val towersHeight = (maxHeight.value * TypedTowersShare).coerceIn(MinTypedTowersDp, MaxTypedTowersDp).dp
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
                    NumberTowers(
                        left = round.left,
                        right = round.right,
                        operation = operation,
                        solved = solved != null,
                        revealed = false,
                        enabled = !interactionLocked,
                        onSpeakNumber = speakNumber,
                        height = towersHeight,
                    )
                },
                answers = {
                    NumberPad(
                        onSubmit = { handleGuess(it) },
                        resetToken = NumberPadInput.resetToken(roundKey, misses),
                        solved = solved != null,
                        enabled = !interactionLocked,
                        onSpeakValue = onSpeakCounting,
                    )
                },
            )
        }
    } else {
        VisualQuantityBoard(
            left = round.left,
            right = round.right,
            operation = operation,
            choices = choices,
            onChoose = { handleGuess(it) },
            solved = solved,
            interactionLocked = interactionLocked,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = onSpeakPrompt,
            onSpeakNumber = speakNumber,
            modifier = modifier.fillMaxSize(),
        )
    }
}

/** Im Tipp-Modus teilt sich die Aufgabe die Bühne mit dem Ziffernblock. */
private const val TypedTowersShare = 0.36f
private const val MinTypedTowersDp = 150f
private const val MaxTypedTowersDp = 300f
