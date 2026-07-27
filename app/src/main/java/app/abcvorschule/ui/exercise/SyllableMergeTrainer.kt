package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky
import kotlin.math.roundToInt

object MergeProgress {
    /** How close to the vowel the consonant must get before the floes freeze together. */
    const val CommitFraction = 0.88f

    fun fraction(currentX: Float, startX: Float, targetX: Float): Float {
        val travel = targetX - startX
        if (travel == 0f) return 0f
        return ((currentX - startX) / travel).coerceIn(0f, 1f)
    }

    fun isMerged(fraction: Float): Boolean = fraction >= CommitFraction

    /** Visual stand-in for the intensifying sound: 0.25 at rest, 1.0 on contact. */
    fun glow(fraction: Float): Float = (0.25f + 0.75f * fraction.coerceIn(0f, 1f))
}

private val FloeGap = 96.dp

/**
 * Trainer 3 — Silben-Verschmelzer. Dragging the consonant towards the vowel ramps
 * up the glow and merges both tiles into one syllable. System TTS cannot stretch a
 * phoneme continuously, so the stretched sound plays once on drag start and the
 * intensification is carried visually.
 */
@Composable
fun SyllableMergeTrainer(
    round: SyllableMergeRound,
    roundIndex: Int,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.leftAtomId}-${round.rightAtomId}"
    val density = LocalDensity.current
    val travelPx = with(density) { FloeGap.toPx() }
    var dragX by remember(roundKey) { mutableFloatStateOf(0f) }
    var merged by remember(roundKey) { mutableStateOf(false) }
    val fraction = MergeProgress.fraction(dragX, 0f, travelPx)
    val glow by animateFloatAsState(
        targetValue = if (merged) 1f else MergeProgress.glow(fraction),
        label = "merge_glow",
    )
    val scoredIds = remember(roundKey) {
        listOf(round.leftAtomId, round.rightAtomId, round.resultAtomId).distinct()
    }

    fun commit() {
        if (merged) return
        merged = true
        onResult(true, false, scoredIds)
    }

    ExerciseStage(
        modifier = modifier,
        prompt = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
            if (merged) {
                Floe(
                    label = round.resultDisplay,
                    glow = 1f,
                    frozen = true,
                    onTap = { onSpeak(round.resultDisplay) },
                    modifier = Modifier.testTag("merge_result"),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Floe(
                        label = round.leftDisplay,
                        glow = glow,
                        frozen = false,
                        onTap = { onSpeak(round.stretchTts) },
                        modifier = Modifier
                            .offset { IntOffset(dragX.roundToInt(), 0) }
                            .pointerInput(roundKey) {
                                detectDragGestures(
                                    onDragStart = { onSpeak(round.stretchTts) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragX = (dragX + amount.x).coerceIn(0f, travelPx)
                                    },
                                    onDragEnd = {
                                        if (MergeProgress.isMerged(
                                                MergeProgress.fraction(dragX, 0f, travelPx),
                                            )
                                        ) {
                                            commit()
                                        } else {
                                            // No penalty: a short pull just slides back.
                                            dragX = 0f
                                        }
                                    },
                                    onDragCancel = { dragX = 0f },
                                )
                            }
                            .testTag("merge_left"),
                    )
                    Spacer(Modifier.width(FloeGap))
                    Floe(
                        label = round.rightDisplay,
                        glow = glow,
                        frozen = false,
                        onTap = { onSpeak(round.rightDisplay) },
                        modifier = Modifier.testTag("merge_right"),
                    )
                }
            }
        },
        answers = {
            // Tap-to-place alternative to the drag (R15): tapping the vowel pulls
            // the consonant across without a gesture.
            if (!merged) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = AbcDimens.kidTouch, minHeight = AbcDimens.kidTouch)
                        .background(NightElevated, RoundedCornerShape(22.dp))
                        .clickable {
                            dragX = travelPx
                            commit()
                        }
                        .testTag("merge_join"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "→|",
                        style = MaterialTheme.typography.headlineMedium,
                        color = SoftSand,
                    )
                }
            }
        },
    )
}

@Composable
private fun Floe(
    label: String,
    glow: Float,
    frozen: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(SyllableFrameSizing.widthDp(label).dp)
            .height(AbcDimens.letterFrame)
            .background(
                color = if (frozen) SoftMint.copy(alpha = 0.22f) else NightElevated,
                shape = RoundedCornerShape(26.dp),
            )
            .border(
                width = 4.dp,
                color = (if (frozen) SoftMint else SoftSky).copy(alpha = glow),
                shape = RoundedCornerShape(26.dp),
            )
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = AbcDimens.letterSp,
            color = if (frozen) SoftMint else SoftSand,
        )
    }
}
