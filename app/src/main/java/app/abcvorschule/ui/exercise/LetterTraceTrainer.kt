package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.Atom
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.BurstGeometry
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.playStarBlip
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted
import kotlinx.coroutines.delay

private val GlyphBox = 350.dp

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
    var sparkSeq by remember(roundKey) { mutableLongStateOf(0L) }
    var spark by remember(roundKey) { mutableStateOf<Pair<TracePoint, Long>?>(null) }
    // Last accepted on-road sample — bridges fast swipes that jump past a star.
    var lastFinger by remember(roundKey) { mutableStateOf<TracePoint?>(null) }
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
                            onFinger = { finger, boxSize, corridorFraction, strokes, stars ->
                                if (done || resolved) return@TraceCanvas
                                val update = TraceProgress.update(
                                    state = state,
                                    finger = finger,
                                    strokes = strokes,
                                    stars = stars,
                                    boxSize = boxSize,
                                    previousFinger = lastFinger,
                                    corridorFraction = corridorFraction,
                                )
                                if (update.offCorridor) {
                                    // Edge-triggered: one short nudge per excursion, never one per
                                    // pointer sample. Otherwise the device buzzes continuously and a
                                    // single stray drag exhausts the resolve threshold at once.
                                    if (!wasOffCorridor) {
                                        wasOffCorridor = true
                                        offRoadCount += 1
                                        haptics.nudge()
                                    }
                                    // Drop the bridge so an off-road hop cannot "tunnel" through
                                    // a star when the finger comes back onto a later stretch.
                                    lastFinger = null
                                    return@TraceCanvas
                                }
                                wasOffCorridor = false
                                // On the road but past the next star: the vehicle stays
                                // where it is, so the start dot of a fresh bar keeps
                                // marking that bar's beginning instead of following the
                                // finger to wherever it entered the road.
                                if (update.ahead) {
                                    lastFinger = finger
                                    return@TraceCanvas
                                }
                                vehicle = finger
                                lastFinger = finger
                                if (update.collectedStar) {
                                    // Read before the state write below, which is visible immediately.
                                    val barFinished = update.state.strokeIndex != state.strokeIndex
                                    stars.getOrNull(state.strokeIndex)?.getOrNull(state.starIndex)
                                        ?.let { collectedAt ->
                                            sparkSeq += 1
                                            spark = collectedAt to sparkSeq
                                        }
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
                                            ?.let {
                                                vehicle = it
                                                // Fresh bar: do not bridge from the previous
                                                // stroke's end into this one's star window.
                                                lastFinger = it
                                            }
                                    }
                                }
                                if (update.glyphDone) {
                                    done = true
                                }
                            },
                            onDragFinished = {
                                // The bridge only spans samples of ONE drag. Without
                                // this reset, lifting the finger and re-planting it
                                // further along bridges the untraced gap and collects
                                // the next star — exactly what the ahead-gate exists
                                // to prevent.
                                lastFinger = null
                            },
                            modifier = Modifier
                                .size(GlyphBox)
                                .testTag("trace_canvas_${atom.id}"),
                        )
                    }
                    TraceStarSpark(spark = spark)
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
    val corridorFraction: Float,
    val strokes: List<List<TracePoint>>,
    val stars: List<List<TracePoint>>,
)

private fun buildTraceLayout(atom: Atom, boxSize: Float, origin: TracePoint): TraceLayout {
    val fit = TraceProgress.fitFor(atom.lemma)
    val strokes = TraceGeometry.toPixels(
        strokes = atom.strokes,
        boxSize = boxSize,
        origin = origin,
        heightScale = fit.heightScale,
    )
    val stars = strokes.map {
        TraceGeometry.starPositions(it, TraceProgress.starCountFor(TraceGeometry.polylineLength(it), boxSize))
    }
    return TraceLayout(boxSize, fit.corridorFraction, strokes, stars)
}

@Composable
private fun TraceCanvas(
    atom: Atom,
    state: TraceState,
    vehicle: TracePoint?,
    onFinger: (
        finger: TracePoint,
        boxSize: Float,
        corridorFraction: Float,
        strokes: List<List<TracePoint>>,
        stars: List<List<TracePoint>>,
    ) -> Unit,
    onDragFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // The glyph box *requests* GlyphBox dp, but narrow screens squeeze it (360dp
    // device minus shell/stage padding leaves ~296dp). Geometry and hit-testing
    // must follow the measured size, not the requested one — otherwise strokes
    // near the right edge are drawn outside the canvas and their start points sit
    // in a dead zone the pointerInput never sees (letter-ch/-sch/-y).
    var measured by remember { mutableStateOf(IntSize.Zero) }
    val boxSizePx = minOf(measured.width, measured.height).toFloat()
    val layout = remember(atom.id, atom.lemma, boxSizePx, measured) {
        if (boxSizePx <= 0f) {
            null
        } else {
            buildTraceLayout(
                atom = atom,
                boxSize = boxSizePx,
                // Center the (square) glyph box inside the possibly non-square canvas.
                origin = TracePoint(
                    (measured.width - boxSizePx) / 2f,
                    (measured.height - boxSizePx) / 2f,
                ),
            )
        }
    }

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
            .onSizeChanged { measured = it }
            // Keyed on the layout, not just the atom: a size/density change rebuilds
            // the strokes, and a gesture block holding the old layout would hit-test
            // against pixels the canvas no longer draws.
            .pointerInput(atom.id, layout) {
                detectDragGestures(
                    onDrag = { change, _ ->
                        change.consume()
                        val current = layout ?: return@detectDragGestures
                        onFinger(
                            TracePoint(change.position.x, change.position.y),
                            current.boxSize,
                            current.corridorFraction,
                            current.strokes,
                            current.stars,
                        )
                    },
                    onDragEnd = { onDragFinished() },
                    onDragCancel = { onDragFinished() },
                )
            }
            // Tap alternative to the drag (R15): a child who taps instead of dragging
            // must still make progress. Each tap collects exactly the next expected
            // star, so repeated taps trace the glyph in stroke order. Keyed on `state`
            // so a fresh gesture recognizer always sees the current stroke/star index.
            .pointerInput(atom.id, state, layout) {
                detectTapGestures(
                    onTap = {
                        val current = layout ?: return@detectTapGestures
                        val target = current.stars.getOrNull(state.strokeIndex)
                            ?.getOrNull(state.starIndex)
                            ?: return@detectTapGestures
                        onFinger(
                            target,
                            current.boxSize,
                            current.corridorFraction,
                            current.strokes,
                            current.stars,
                        )
                    },
                )
            },
    ) {
        val layout = layout ?: return@Canvas
        val corridor = layout.boxSize * layout.corridorFraction
        // Keep the red vehicle and stars in proportion to the (possibly thinner) road.
        val chromeScale = layout.corridorFraction / TraceProgress.CorridorFraction

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
            // Umlaut ticks and other diacritics are tiny; the full road width with round
            // caps turns them into overlapping blobs that collide with the letter body.
            val widthScale = if (
                TraceProgress.isShortStroke(
                    TraceGeometry.polylineLength(stroke),
                    layout.boxSize,
                )
            ) {
                TraceProgress.ShortStrokeWidthScale
            } else {
                1f
            }
            // Hollow road: a wide band with a dark inner lane. On the cream background
            // the band needs more opacity than the old dark-theme calibration to stay
            // legible, hence the higher alphas below.
            drawPath(
                path = path,
                color = outer.copy(alpha = if (active) 0.45f else 0.22f),
                style = Stroke(
                    width = corridor * 2f * widthScale,
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
                    width = corridor * 1.25f * widthScale,
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
                    // StarGold alone sits on the CreamElevated lane at only ~1.6:1 — well
                    // under the 3:1 floor for UI glyphs. A StarGoldDeep contour (same
                    // treatment as IconStar) restores that margin without changing the
                    // "reward" hue. The inactive stars keep the same faded alpha on both
                    // fill and outline so they read as one dimmed shape, not two layers.
                    outline = if (active) StarGoldDeep else StarGoldDeep.copy(alpha = 0.35f),
                    outerRadius = layout.boxSize * chromeScale * if (next) 0.055f else 0.042f,
                )
            }
        }
        val car = vehicle ?: layout.strokes.firstOrNull()?.firstOrNull()
        if (car != null) {
            drawCircle(
                color = SunCoral,
                radius = layout.boxSize * 0.055f * chromeScale,
                center = Offset(car.x, car.y),
            )
        }
    }
}

/**
 * Small spark burst at the spot a trace star was just collected (Spec §5.2). Pure
 * draw overlay — same box size as [TraceCanvas], no layout impact — so it neither
 * measures nor shifts anything underneath it.
 */
@Composable
private fun TraceStarSpark(
    spark: Pair<TracePoint, Long>?,
    modifier: Modifier = Modifier,
) {
    val progress = remember { Animatable(1f) }
    LaunchedEffect(spark?.second) {
        if (spark == null) return@LaunchedEffect
        progress.snapTo(0f)
        progress.animateTo(1f, tween(400))
    }
    val point = spark?.first ?: return
    Canvas(modifier = modifier.size(GlyphBox)) {
        val offsets = BurstGeometry.sparkOffsets(
            count = 5,
            progress = progress.value,
            radiusPx = 18.dp.toPx(),
        )
        val center = Offset(point.x, point.y)
        offsets.forEach { offset ->
            drawCircle(
                color = StarGold,
                radius = 3.dp.toPx() * (1f - progress.value),
                center = center + offset,
                alpha = 1f - progress.value,
            )
        }
    }
}

/** Five-pointed collectible star, filled, with a deep-gold contour for contrast. */
private fun DrawScope.drawStar(
    center: TracePoint,
    color: Color,
    outline: Color,
    outerRadius: Float,
) {
    // The stroke is centered on the path, so it grows outward by half its width at
    // the star's tips. Insetting the path radius by that half-width keeps the
    // contoured star within the same footprint the plain fill used before.
    val strokeWidth = outerRadius / 6f
    val insetOuterRadius = outerRadius - strokeWidth / 2f
    val points = TraceGeometry.starPoints(
        center = center,
        outerRadius = insetOuterRadius,
        innerRadius = insetOuterRadius * 0.45f,
    )
    if (points.isEmpty()) return
    val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        points.drop(1).forEach { lineTo(it.x, it.y) }
        close()
    }
    drawPath(path = path, color = color)
    drawPath(
        path = path,
        color = outline,
        style = Stroke(width = strokeWidth, join = StrokeJoin.Round),
    )
}
