package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.Atom
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.playStarBlip
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.SoftCoral
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand

private val GlyphBox = 260.dp

/**
 * Trainer 2 — Visueller Spurensucher. The glyph is a hollow road built from the
 * atom's authored strokes; the vehicle only advances while the finger stays in
 * the corridor, so the writing direction is what is actually practiced.
 */
@Composable
fun LetterTraceTrainer(
    round: LetterTraceRound,
    atom: Atom,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = round.atomId
    var state by remember(roundKey) { mutableStateOf(TraceState()) }
    var vehicle by remember(roundKey) { mutableStateOf<TracePoint?>(null) }
    var starsCollected by remember(roundKey) { mutableIntStateOf(0) }
    var offRoadCount by remember(roundKey) { mutableIntStateOf(0) }
    var done by remember(roundKey) { mutableStateOf(false) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val morph by animateFloatAsState(
        targetValue = if (done || resolved) 1f else 0f,
        label = "glyph_morph",
    )

    ExerciseStage(
        modifier = modifier,
        prompt = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
            Box(
                modifier = Modifier.size(GlyphBox),
                contentAlignment = Alignment.Center,
            ) {
                if (morph < 1f) {
                    TraceCanvas(
                        atom = atom,
                        state = state,
                        vehicle = vehicle,
                        onFinger = { finger, boxSize, strokes, stars ->
                            if (done || resolved) return@TraceCanvas
                            val update = TraceProgress.update(state, finger, strokes, stars, boxSize)
                            if (update.offCorridor) {
                                offRoadCount += 1
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                return@TraceCanvas
                            }
                            vehicle = finger
                            if (update.collectedStar) {
                                playStarBlip(starsCollected)
                                starsCollected += 1
                                state = update.state
                            }
                            if (update.glyphDone) {
                                done = true
                                onResult(true, false, listOf(atom.id))
                            }
                        },
                        modifier = Modifier
                            .size(GlyphBox)
                            .testTag("trace_canvas_${atom.id}"),
                    )
                } else {
                    // The road briefly becomes the object the letter stands for.
                    Text(text = round.rewardEmoji, fontSize = 108.sp)
                }
            }
            Text(
                text = round.glyph,
                fontSize = 28.sp,
                color = MutedText.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        answers = {
            // Repeated off-road nudges make the resolve available, matching R10.
            if (offRoadCount >= 6 && !done && !resolved) {
                AbcResolveButton(
                    onClick = {
                        resolved = true
                        onResult(false, true, listOf(atom.id))
                    },
                )
            }
        },
    )
}

/** Strokes and star positions scaled once per glyph box size, shared by the drag
 * handler and the draw scope so hit-testing and rendering never drift apart. */
private data class TraceLayout(
    val boxSize: Float,
    val strokes: List<List<TracePoint>>,
    val stars: List<List<TracePoint>>,
)

private fun buildTraceLayout(atom: Atom, boxSize: Float): TraceLayout {
    val strokes = TraceGeometry.toPixels(atom.strokes, boxSize, TracePoint(0f, 0f))
    val stars = strokes.map { TraceGeometry.starPositions(it, TraceProgress.StarsPerStroke) }
    return TraceLayout(boxSize, strokes, stars)
}

@Composable
private fun TraceCanvas(
    atom: Atom,
    state: TraceState,
    vehicle: TracePoint?,
    onFinger: (
        finger: TracePoint,
        boxSize: Float,
        strokes: List<List<TracePoint>>,
        stars: List<List<TracePoint>>,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // The glyph box is a fixed dp size, so its pixel size is known up front —
    // computed once and shared by both the gesture handler and the draw scope,
    // instead of each recomputing strokes/stars from their own size source.
    val boxSizePx = remember(density) { with(density) { GlyphBox.toPx() } }
    val layout = remember(atom.id, boxSizePx) { buildTraceLayout(atom, boxSizePx) }

    Canvas(
        modifier = modifier.pointerInput(atom.id) {
            detectDragGestures(
                onDrag = { change, _ ->
                    change.consume()
                    onFinger(
                        TracePoint(change.position.x, change.position.y),
                        layout.boxSize,
                        layout.strokes,
                        layout.stars,
                    )
                },
            )
        },
    ) {
        val corridor = layout.boxSize * TraceProgress.CorridorFraction

        layout.strokes.forEachIndexed { index, stroke ->
            val path = Path().apply {
                moveTo(stroke.first().x, stroke.first().y)
                stroke.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val active = index == state.strokeIndex
            val outer = if (index < state.strokeIndex) SoftMint else SoftSand
            // Hollow road: a wide light band with a dark inner lane.
            drawPath(
                path = path,
                color = outer.copy(alpha = if (active) 0.30f else 0.16f),
                style = Stroke(
                    width = corridor * 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawPath(
                path = path,
                color = NightInk,
                style = Stroke(
                    width = corridor * 1.25f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            layout.stars.getOrNull(index)?.forEachIndexed { starIndex, star ->
                val collected = index < state.strokeIndex ||
                    (index == state.strokeIndex && starIndex < state.starIndex)
                val next = index == state.strokeIndex && starIndex == state.starIndex
                drawCircle(
                    color = when {
                        collected -> SoftMint
                        next -> SoftSand
                        else -> SoftSand.copy(alpha = 0.35f)
                    },
                    radius = if (next) layout.boxSize * 0.035f else layout.boxSize * 0.025f,
                    center = Offset(star.x, star.y),
                )
            }
        }
        val car = vehicle ?: layout.strokes.firstOrNull()?.firstOrNull()
        if (car != null) {
            drawCircle(
                color = SoftCoral,
                radius = layout.boxSize * 0.055f,
                center = Offset(car.x, car.y),
            )
        }
    }
}
