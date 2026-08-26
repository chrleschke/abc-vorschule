package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.session.ScheduledTrainer
import app.abcvorschule.speech.GermanNumberWord
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.WarmInk

/**
 * Trainer 7 — Rechnen. Pure quantity arithmetic: emoji groups and numerals only,
 * never words to read or build. Singular/plural lives in the spoken prompt.
 */
@Composable
fun MathExercise(
    trainer: ScheduledTrainer,
    round: CountAddRound,
    roundIndex: Int,
    icon: String,
    input: MathInputMode,
    showSymbolPrompt: Boolean,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeakFeedback: (String) -> Unit,
    onSpeakCounting: (String) -> Unit,
    onResult: (distance: Int?, resolved: Boolean, correct: Boolean, guess: Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAbcHaptics.current
    val operation = MathOperation.fromWireName(round.operation) ?: MathOperation.Add
    val roundKey = "${trainer.spec.id}#$roundIndex-${round.operation}-${round.left}-${round.right}"
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var locked by remember(roundKey) { mutableStateOf(false) }
    // Tracked apart from `locked`, which a resolve also sets: giving up must not
    // light up the green confirmation meant for a correct answer.
    var solved by remember(roundKey) { mutableStateOf<Int?>(null) }
    val usePad = input == MathInputMode.Typed
    var counting by remember(roundKey) {
        mutableStateOf(CountingState.forRound(operation, round.left, round.right))
    }
    // Die Hilfe klappt bei der Schwelle auf und bleibt danach offen: sie wieder
    // zuzuziehen, während das Kind mittendrin zählt, wäre die schlechteste aller
    // Optionen.
    val countingOpen = usePad && misses >= MathHinting.CountingAidFromMisses

    // Kinder lesen nicht: dass sich der Aufgabenbereich gerade in etwas
    // Antippbares verwandelt hat, muss gesagt werden. Feedback-Kanal, damit ein
    // noch laufender Miss-Hinweis nicht abgewürgt wird.
    LaunchedEffect(countingOpen) {
        if (countingOpen) onSpeakFeedback(MathHinting.countingAidCue(operation))
    }
    // Seeded wie TrayOrder: die Kachel-Reihenfolge muss beim Rück-Chevron in eine
    // besuchte Runde (und nach Recreation) dieselbe sein wie beim ersten Besuch.
    val choices = remember(roundKey) {
        MathHinting.threeChoices(round.answer).shuffled(kotlin.random.Random(roundKey.hashCode()))
    }

    fun handleGuess(guess: Int) {
        if (locked) return
        if (guess == round.answer) {
            locked = true
            solved = guess
            onResult(0, false, true, guess)
        } else {
            // Kein lokales Echo mehr: ein zweiter Primary-speak (der Miss-Hinweis
            // aus dem ViewModel) flusht die Engine und würde die Zahl mitten im
            // Wort abschneiden. Der Tipp wandert stattdessen mit ins Cue —
            // "Sieben. Du bist nah dran …" als eine Äußerung.
            haptics.nudge()
            misses += 1
            onResult(MathHinting.distance(round.answer, guess), false, false, guess)
        }
    }

    fun resolve() {
        if (locked) return
        locked = true
        onResult(null, true, false, null)
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
                // The multiplication matrix writes "3 × 4" above itself, and the
                // counting aid writes its own equation line — a second symbolic line
                // here would show the same task twice (Layout §9).
                if (showSymbolPrompt && !countingOpen && operation != MathOperation.Multiply) {
                    Text(
                        text = "${round.left} ${operation.symbol} ${round.right} = ?",
                        style = MaterialTheme.typography.displayLarge,
                        color = WarmInk,
                    )
                }
                if (countingOpen) {
                    CountingAid(
                        emoji = icon,
                        left = round.left,
                        right = round.right,
                        operation = operation,
                        state = counting,
                        onTap = { index ->
                            if (locked) return@CountingAid
                            val next = counting.tap(index)
                            if (next == counting) {
                                // Deckel der Weg-Zone erreicht: kein Fehler, keine
                                // Meldung, nur ein spürbares "das war's".
                                haptics.nudge()
                            } else {
                                haptics.tick()
                                counting = next
                                // Mitzählen bei jedem Tipp — auf dem eigenen
                                // Zählkanal, damit die Zahl eine laufende Ansage
                                // überlagert, statt sie abzuwürgen oder von ihr
                                // abgewürgt zu werden. Als Wort, nicht als Ziffer:
                                // "8." ist im Deutschen die Ordinalzahl und würde
                                // "achte" gelesen (GermanNumberWord).
                                next.counted?.let { onSpeakCounting(GermanNumberWord.of(it)) }
                            }
                        },
                    )
                } else {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MathQuantityPrompt(icon, round.left, round.right, operation, emojiSizeSp = 40)
                    }
                }
            },
            answers = {
                NumberPad(
                    onSubmit = { handleGuess(it) },
                    resetToken = NumberPadInput.resetToken(roundKey, misses),
                    solved = solved != null,
                    enabled = !interactionLocked,
                    countedValue = counting.counted.takeIf { countingOpen },
                    hideKeyboard = countingOpen,
                )
                if (misses >= MathHinting.ResolveFromMissesTyped && !locked) {
                    AbcResolveButton(onClick = ::resolve)
                }
            },
        )
    } else {
        VisualQuantityBoard(
            emoji = icon,
            left = round.left,
            right = round.right,
            operation = operation,
            choices = choices,
            onChoose = { handleGuess(it) },
            solved = solved,
            missCount = misses,
            locked = locked,
            interactionLocked = interactionLocked,
            onResolve = ::resolve,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = onSpeakPrompt,
            modifier = modifier.fillMaxSize(),
        )
    }
}
