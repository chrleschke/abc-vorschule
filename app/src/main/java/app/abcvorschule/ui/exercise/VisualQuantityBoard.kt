package app.abcvorschule.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.WarmInk

@Composable
fun VisualQuantityBoard(
    left: Int,
    right: Int,
    operation: MathOperation,
    choices: List<Int>,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The chosen value once it turned out to be correct — that tile turns green. */
    solved: Int? = null,
    /** False during the audio lock: it gates the initial listen-first window (design doc). */
    interactionLocked: Boolean = false,
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
                        revealed = false,
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
            },
        )
    }
}

/** Anteil der Bühnenhöhe für die Zahlentürme, mit Boden und Deckel in dp. */
private const val TowersShareOfStage = 0.42f
private const val MinTowersDp = 150f
private const val MaxTowersDp = 340f
