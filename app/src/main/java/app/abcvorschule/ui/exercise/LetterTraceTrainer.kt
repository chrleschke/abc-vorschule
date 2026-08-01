package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.Atom
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.playStarBlip
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted
import kotlinx.coroutines.delay

private val GlyphBox = 260.dp

/**
 * How long the finished glyph stays on screen before the reward page replaces it.
 * Long enough for the last bar's fill to land, so the child sees the letter complete
 * rather than the screen cutting away mid-animation.
 */
private const val RewardHoldMs = 500L

/**
 * Trainer 2 — Visueller Spurensucher. The glyph is a hollow road built from the
 * atom's authored strokes; the vehicle only advances while the finger stays in
 * the corridor, so the writing direction is what is actually practiced.
 */
@Composable
fun LetterTraceTrainer(
    round: LetterTraceRound,
    roundIndex: Int,
    atom: Atom,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.atomId}"
    var state by remember(roundKey) { mutableStateOf(TraceState()) }
    var vehicle by remember(roundKey) { mutableStateOf<TracePoint?>(null) }
    var starsCollected by remember(roundKey) { mutableIntStateOf(0) }
    var offRoadCount by remember(roundKey) { mutableIntStateOf(0) }
    var wasOffCorridor by remember(roundKey) { mutableStateOf(false) }
    var done by remember(roundKey) { mutableStateOf(false) }
    var reward by remember(roundKey) { mutableStateOf(false) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    val haptics = LocalAbcHaptics.current

    val morph by animateFloatAsState(
        targetValue = if (reward || resolved) 1f else 0f,
        label = "glyph_morph",
    )

    // The completed glyph holds for a beat before the reward page takes over. The
    // delay has to sit in front of onResult: reporting the result starts the spoken
    // success phase, so calling it first would talk over the still-animating glyph.
    LaunchedEffect(done) {
        if (!done) return@LaunchedEffect
        delay(RewardHoldMs)
        reward = true
        onResult(true, false, listOf(atom.id))
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
            Box(
                modifier = Modifier.size(GlyphBox),
                contentAlignment = Alignment.Center,
            ) {
                if (morph < 1f) {
                    // Keyed per round so a new glyph starts its fill animation from scratch
                    // instead of animating the previous round's bars back to empty.
                    key(roundKey) {
                        TraceCanvas(
                            atom = atom,
                            state = state,
                            vehicle = vehicle,
                            onFinger = { finger, boxSize, strokes, stars ->
                                if (done || resolved) return@TraceCanvas
                                val update = TraceProgress.update(state, finger, strokes, stars, boxSize)
                                if (update.offCorridor) {
                                    // Edge-triggered: one short nudge per excursion, never one per
                                    // pointer sample. Otherwise the device buzzes continuously and a
                                    // single stray drag exhausts the resolve threshold at once.
                                    if (!wasOffCorridor) {
                                        wasOffCorridor = true
                                        offRoadCount += 1
                                        haptics.nudge()
                                    }
                                    return@TraceCanvas
                                }
                                wasOffCorridor = false
                                vehicle = finger
                                if (update.collectedStar) {
                                    // Read before the state write below, which is visible immediately.
                                    val barFinished = update.state.strokeIndex != state.strokeIndex
                                    playStarBlip(starsCollected)
                                    // A distinct short tick per star: the reward must not feel
                                    // like the long buzz that means "off the road".
                                    haptics.tick()
                                    starsCollected += 1
                                    state = update.state
                                    // A finished bar hands the vehicle over to the next one, so the
                                    // child can see where the next stroke starts instead of hunting
                                    // for it with the dot left behind at the previous bar's end.
                                    if (barFinished) {
                                        strokes.getOrNull(update.state.strokeIndex)?.firstOrNull()
                                            ?.let { vehicle = it }
                                    }
                                }
                                if (update.glyphDone) {
                                    done = true
                                }
                            },
                            modifier = Modifier
                                .size(GlyphBox)
                                .testTag("trace_canvas_${atom.id}"),
                        )
                    }
                } else {
                    TraceRewardCard(round = round)
                }
            }
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

/**
 * Reward page for a finished glyph: the object the letter stands for, and under it the
 * letter-word link the trainer is actually teaching — graphem in bold so the eye lands
 * on it first.
 */
@Composable
private fun TraceRewardCard(
    round: LetterTraceRound,
    modifier: Modifier = Modifier,
) {
    val word = TraceReward.wordOf(round.rewardTts)
    Column(
        modifier = modifier.testTag("trace_reward_${round.atomId}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = round.rewardEmoji, fontSize = 96.sp)
        Text(
            text = buildAnnotatedString {
                if (word == null) {
                    // An authored line that breaks the "<glyph> wie <word>" pattern is still
                    // shown rather than swallowed.
                    append(round.rewardTts)
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(round.glyph) }
                    append(" wie $word")
                }
            },
            style = MaterialTheme.typography.headlineMedium,
            color = WarmInk,
        )
    }
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
    val stars = strokes.map {
        TraceGeometry.starPositions(it, TraceProgress.starCountFor(TraceGeometry.polylineLength(it), boxSize))
    }
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

    // One animation for the whole glyph instead of one per stroke: animating the
    // stroke *index* keeps the number of animation calls independent of how many
    // strokes a letter has, and each bar's fill is the animated index passing it.
    val filled by animateFloatAsState(
        targetValue = state.strokeIndex.toFloat(),
        animationSpec = tween(durationMillis = 360, easing = EaseIn),
        label = "stroke_fill",
    )

    Canvas(
        modifier = modifier
            .pointerInput(atom.id) {
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
            }
            // Tap alternative to the drag (R15): a child who taps instead of dragging
            // must still make progress. Each tap collects exactly the next expected
            // star, so repeated taps trace the glyph in stroke order. Keyed on `state`
            // so a fresh gesture recognizer always sees the current stroke/star index.
            .pointerInput(atom.id, state) {
                detectTapGestures(
                    onTap = {
                        val target = layout.stars.getOrNull(state.strokeIndex)
                            ?.getOrNull(state.starIndex)
                            ?: return@detectTapGestures
                        onFinger(target, layout.boxSize, layout.strokes, layout.stars)
                    },
                )
            },
    ) {
        val corridor = layout.boxSize * TraceProgress.CorridorFraction

        val order = TraceGeometry.strokeDrawOrder(layout.strokes.size, state.strokeIndex)

        // Roads first, all of them, with the active stroke last. Drawing a stroke's stars
        // right after its own road would let the *next* stroke's band cover them where the
        // two overlap, which is precisely where the child has to aim.
        order.forEach { index ->
            val stroke = layout.strokes[index]
            val path = Path().apply {
                moveTo(stroke.first().x, stroke.first().y)
                stroke.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val active = index == state.strokeIndex
            val fill = (filled - index).coerceIn(0f, 1f)
            val outer = if (fill > 0f) SkyBlue else WarmMuted
            // Hollow road: a wide band with a dark inner lane. On the cream background
            // the band needs more opacity than the old dark-theme calibration to stay
            // legible, hence the higher alphas below.
            drawPath(
                path = path,
                color = outer.copy(alpha = if (active) 0.45f else 0.22f),
                style = Stroke(
                    width = corridor * 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            // A finished bar's lane eases from the elevated cream to the fill colour, so
            // completion reads as "this one is done" without any text.
            drawPath(
                path = path,
                color = lerp(CreamElevated, LeafGreen, fill),
                style = Stroke(
                    width = corridor * 1.25f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
        // Stars on top of every road, active stroke last again so the star to aim at
        // stays the topmost thing on the glyph.
        order.forEach { index ->
            val active = index == state.strokeIndex
            layout.stars.getOrNull(index)?.forEachIndexed { starIndex, star ->
                val collected = index < state.strokeIndex ||
                    (index == state.strokeIndex && starIndex < state.starIndex)
                // Collected stars are gone — the filled bar carries the progress from
                // there on, so the road does not stay cluttered with spent markers.
                if (collected) return@forEachIndexed
                val next = active && starIndex == state.starIndex
                drawStar(
                    center = star,
                    // Only the active bar's stars are lit; the ones still to come stay
                    // faint so the next stroke announces itself without competing.
                    color = if (active) StarGold else StarGold.copy(alpha = 0.35f),
                    outerRadius = layout.boxSize * if (next) 0.055f else 0.042f,
                )
            }
        }
        val car = vehicle ?: layout.strokes.firstOrNull()?.firstOrNull()
        if (car != null) {
            drawCircle(
                color = SunCoral,
                radius = layout.boxSize * 0.055f,
                center = Offset(car.x, car.y),
            )
        }
    }
}

/** Five-pointed collectible star, filled. */
private fun DrawScope.drawStar(
    center: TracePoint,
    color: Color,
    outerRadius: Float,
) {
    val points = TraceGeometry.starPoints(
        center = center,
        outerRadius = outerRadius,
        innerRadius = outerRadius * 0.45f,
    )
    if (points.isEmpty()) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path = path, color = color)
}
