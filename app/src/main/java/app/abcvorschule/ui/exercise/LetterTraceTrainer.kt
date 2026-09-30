package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
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
import androidx.compose.ui.unit.isFinite
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.Atom
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.BurstGeometry
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.playStarBlip
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.SilboEmoji
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.world.rememberReduceMotion
import app.abcvorschule.ui.world.rememberWorldSeconds
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin
import kotlinx.coroutines.delay

/** Obergrenze für den Glyph-Kasten; enger wird er, wenn Kasten plus Straßenband
 * (siehe `bandOverhang` unten) sonst nicht in den gemessenen Platz passen. */
private val GlyphBoxMax = 350.dp

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
    // Taps that collected nothing — the signal that this child is tapping instead of
    // drawing, and needs the resolve button rather than another silent tap.
    var tapsWithoutTrace by remember(roundKey) { mutableIntStateOf(0) }
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
        promptChrome = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
        },
        prompt = {
            // Der gezeichnete Glyph ist größer als sein Kasten: die Straße ist ein
            // Band von `corridorFraction × boxSize` Halbbreite um die Bahn, und die
            // Bahn selbst reicht bis an die Kanten des Einheitsquadrats (y ab 0.02).
            // Gemessen wird aber nur der Kasten — mit den vollen 350dp stand das Band
            // 49dp über dessen Oberkante und lag damit im Speaker (live gesehen).
            // Der Kasten wird deshalb so gedeckelt, dass Kasten *plus* Band in den
            // gemessenen Platz passt; Compose beschneidet ein Canvas nicht auf seine
            // eigene Größe, ein Überstand fiele also weiterhin niemandem auf, bevor
            // er in einem Nachbarn landet.
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val bandOverhang = 1f + 2f * TraceProgress.fitFor(atom.lemma).corridorFraction
                val glyphSide = minOf(
                    GlyphBoxMax,
                    maxWidth / bandOverhang,
                    // Unbeschränkt (kein aktueller Fall, siehe ExerciseStage) bliebe
                    // nur der Deckel GlyphBoxMax übrig.
                    if (maxHeight.isFinite) maxHeight / bandOverhang else GlyphBoxMax,
                )
                Box(
                    modifier = Modifier.size(glyphSide),
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
                                        // The child is tracing after all — the tap tally
                                        // must not carry over into the rest of the glyph.
                                        tapsWithoutTrace = 0
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
                                onTapWithoutTrace = { strokeStart ->
                                    if (!done && !resolved) {
                                        tapsWithoutTrace += 1
                                        // Park the start dot where the stroke begins: the
                                        // tap gets an answer ("start here"), just not a star.
                                        strokeStart?.let { vehicle = it }
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .testTag("trace_canvas_${atom.id}"),
                            )
                        }
                        TraceStarSpark(spark = spark)
                    } else {
                        TraceRewardCard(round = round)
                    }
                }
            }
        },
        answers = {
            // Repeated off-road nudges make the resolve available (R10) — and so do
            // repeated fruitless taps, which is how a child who cannot drag gets on (R15).
            if (TraceProgress.resolveAvailable(offRoadCount, tapsWithoutTrace) && !done && !resolved) {
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
    // Auf einer hellen Karte: im Dschungel ist der Grund dunkel, und Bild und Wort sind
    // Lerninhalt — der steht immer auf einer Licht-Insel (§10, „Nachtwelten").
    Column(
        modifier = modifier
            .testTag("trace_reward_${round.atomId}")
            .background(Cream.copy(alpha = 0.95f), RoundedCornerShape(28.dp))
            .padding(horizontal = 32.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = round.rewardEmoji, fontSize = 96.sp, fontFamily = SilboEmoji)
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
    /** A tap made no progress; [strokeStart] is where the child should start instead. */
    onTapWithoutTrace: (strokeStart: TracePoint?) -> Unit,
    modifier: Modifier = Modifier,
) {
    // The glyph box *requests* up to GlyphBoxMax dp, but narrow screens squeeze it
    // (360dp device minus shell/stage padding leaves ~296dp). Geometry and hit-testing
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
    // Der Käfer lebt im Stand leise weiter (Fühler, Beine, Leuchten); gelesen wird die
    // Uhr erst im Zeichnen. Bei „Bewegung reduzieren" steht sie.
    val beetleClock = rememberWorldSeconds(rememberReduceMotion())
    val gait = remember { BeetleGait() }
    val filled by animateFloatAsState(
        targetValue = state.strokeIndex.toFloat(),
        animationSpec = tween(durationMillis = AbcMotion.StandardMs, easing = AbcMotion.Fill),
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
            // A tap collects nothing — this trainer practices the writing movement, and
            // a tap has none. Letting a tap stand in for a touch on the star meant the
            // whole glyph could be tapped star to star without ever drawing. A tap is
            // answered with guidance instead: the start dot jumps to the beginning of
            // the current stroke, and after a few fruitless taps the resolve button
            // appears, which is the non-drag way on that R15 asks for.
            // Keyed on `state` so a fresh recognizer always sees the current stroke.
            .pointerInput(atom.id, state, layout) {
                detectTapGestures(
                    onTap = {
                        val current = layout ?: return@detectTapGestures
                        // Every tap ends any gesture: never bridge from a tap into a
                        // later stretch of road nobody traced.
                        onDragFinished()
                        onTapWithoutTrace(TraceProgress.tapGuidance(state, current.strokes))
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
            // Ein heller Weg durch den nächtlichen Dschungel (PRODUCT_PRINCIPLES §10,
            // „Nachtwelten"): außen ein dunkler Schattensaum, der den Weg vom
            // unruhigen Bild trennt, innen die Licht-Insel, auf der die Sterne liegen.
            drawPath(
                path = path,
                color = RoadShade.copy(alpha = if (active) 0.6f else 0.4f),
                style = Stroke(
                    width = corridor * 2.0f * widthScale,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            // Ein fertiger Balken wird vom hellen Weg zu hellem Grün — „das ist
            // geschafft", ohne Text. Noch nicht dran: etwas gedämpft.
            drawPath(
                path = path,
                color = lerp(RoadLight, RoadDone, fill).copy(alpha = if (active || fill > 0f) 1f else 0.72f),
                style = Stroke(
                    width = corridor * 1.45f * widthScale,
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
            // Der Leuchtkäfer schaut zum nächsten Stern — so zeigt er ohne Pfeil,
            // in welche Richtung es weitergeht.
            val target = layout.stars.getOrNull(state.strokeIndex)?.getOrNull(state.starIndex)
            val heading = if (target != null && (target.x != car.x || target.y != car.y)) {
                atan2(target.y - car.y, target.x - car.x) * 180f / PI.toFloat() + 90f
            } else {
                0f
            }
            drawFirefly(
                center = Offset(car.x, car.y),
                size = layout.boxSize * 0.055f * chromeScale,
                headingDeg = heading,
                seconds = beetleClock.value,
                gait = gait,
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
        progress.animateTo(1f, tween(AbcMotion.LongMs))
    }
    val point = spark?.first ?: return
    // Dieselbe Fläche wie das TraceCanvas daneben — der Funkenpunkt kommt aus dessen
    // Pixelkoordinaten, ein eigener Kasten anderer Größe verschöbe ihn.
    Canvas(modifier = modifier.fillMaxSize()) {
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

/**
 * Das Fahrzeug des Spurensuchers: ein Leuchtkäfer (Glühwürmchen sind Käfer und leben im
 * Regenwald). Von oben gesehen, Kopf in Fahrtrichtung, der Hinterleib leuchtet —
 * dasselbe Licht wie die Spur. [size] ist der frühere Radius des Punkts, der Käfer ist
 * rund dreimal so lang.
 */
/**
 * Gangzustand des Leuchtkäfers, nur im Zeichnen fortgeschrieben (kein State): wie weit
 * er seit dem letzten Bild gefahren ist, treibt seine Beine an.
 */
private class BeetleGait {
    var last: Offset? = null
    var phase = 0f
}

/**
 * Der Leuchtkäfer, von oben (PRODUCT_PRINCIPLES §10, Dschungel): sechs Beine mit Knie,
 * ein orangefarbener Halsschild mit dunklem Fleck, zwei Flügeldecken mit hellem Rand,
 * lange Fühler und ein leuchtender Hinterleib, der hinten herausschaut. Vorher fehlten
 * Beine und Halsschild; auf dem Kopf stehend las sich die Figur wie ein Mensch von hinten.
 *
 * Im Stand lebt er leise: die Fühler tasten, die Beine treten ein wenig, der Hinterleib
 * glimmt (Perioden 1,8–3 s). Fährt er, laufen die Beine im Dreifußgang mit der Strecke.
 */
private fun DrawScope.drawFirefly(center: Offset, size: Float, headingDeg: Float, seconds: Float, gait: BeetleGait) {
    val s = size
    gait.last?.let { gait.phase += (center - it).getDistance() / (s * 1.6f) }
    gait.last = center
    val walk = gait.phase * 2f * PI.toFloat()
    val idle = seconds * 2f * PI.toFloat()
    val glowPulse = 0.85f + 0.15f * sin(idle / 3f)
    rotate(degrees = headingDeg, pivot = center) {
        fun at(x: Float, y: Float) = center + Offset(x * s, y * s)
        // Leuchten um den Hinterleib.
        val tail = at(0f, 1.2f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to FireflyLight.copy(alpha = glowPulse),
                0.45f to FireflyLight.copy(alpha = 0.5f * glowPulse),
                1f to FireflyLight.copy(alpha = 0f),
                center = tail,
                radius = s * 2.3f,
            ),
            radius = s * 2.3f,
            center = tail,
        )
        // Sechs Beine, je zwei Glieder mit Knie. Dreifußgang: vorn links, Mitte rechts,
        // hinten links schwingen gemeinsam, die anderen drei gegengleich.
        val leg = Stroke(width = s * 0.16f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        listOf(-1f, 1f).forEach { side ->
            listOf(
                Triple(-0.38f, -55f, 0),
                Triple(-0.02f, -5f, 1),
                Triple(0.34f, 40f, 2),
            ).forEach { (y, baseDeg, index) ->
                val group = if ((index % 2 == 0) == (side < 0f)) 0f else PI.toFloat()
                val swing = 16f * sin(walk + group) + 3f * sin(idle / 1.8f + index + side)
                val a = (baseDeg + swing) * PI.toFloat() / 180f
                val hip = at(side * 0.42f, y)
                val knee = hip + Offset(side * kotlin.math.cos(a) * s * 0.5f, kotlin.math.sin(a) * s * 0.5f)
                val bend = a + (if (index == 0) -0.7f else if (index == 2) 0.7f else 0.35f)
                val foot = knee + Offset(side * kotlin.math.cos(bend) * s * 0.4f, kotlin.math.sin(bend) * s * 0.4f)
                drawPath(Path().apply { moveTo(hip.x, hip.y); lineTo(knee.x, knee.y); lineTo(foot.x, foot.y) }, FireflyDark, style = leg)
            }
        }
        // Leuchtender Hinterleib, hinten unter den Flügeldecken hervor.
        drawOval(FireflyGlow, topLeft = at(-0.4f, 0.6f), size = Size(s * 0.8f, s * 0.98f))
        drawOval(FireflyGlowEdge, topLeft = at(-0.4f, 0.6f), size = Size(s * 0.8f, s * 0.98f), style = Stroke(width = s * 0.07f))
        // Flügeldecken: zwei Hälften, dunkelbraun mit hellem Rand, in der Mitte geteilt.
        listOf(-1f, 1f).forEach { side ->
            val half = Path().apply {
                moveTo(center.x, center.y - s * 0.5f)
                cubicTo(
                    center.x + side * s * 0.62f, center.y - s * 0.52f,
                    center.x + side * s * 0.66f, center.y + s * 0.35f,
                    center.x + side * s * 0.26f, center.y + s * 0.86f,
                )
                lineTo(center.x, center.y + s * 0.8f)
                close()
            }
            drawPath(half, FireflyWing)
            drawPath(half, FireflyWingRim, style = Stroke(width = s * 0.09f))
        }
        drawLine(FireflyDark, at(0f, -0.5f), at(0f, 0.82f), strokeWidth = s * 0.07f)
        // Halsschild: orange mit dunklem Fleck — das Kennzeichen des Leuchtkäfers, und
        // was den Kopf klar vom Körper trennt.
        drawOval(FireflyShield, topLeft = at(-0.52f, -0.98f), size = Size(s * 1.04f, s * 0.58f))
        drawOval(FireflyDark, topLeft = at(-0.2f, -0.86f), size = Size(s * 0.4f, s * 0.32f))
        // Kopf mit hellen Augen, darüber die tastenden Fühler.
        drawOval(FireflyDark, topLeft = at(-0.3f, -1.28f), size = Size(s * 0.6f, s * 0.42f))
        drawCircle(Color.White, radius = s * 0.09f, center = at(-0.2f, -1.1f))
        drawCircle(Color.White, radius = s * 0.09f, center = at(0.2f, -1.1f))
        val antenna = Stroke(width = s * 0.09f, cap = StrokeCap.Round)
        listOf(-1f, 1f).forEach { side ->
            val twitch = 0.12f * sin(idle / 2.2f + side * 1.3f)
            drawPath(
                Path().apply {
                    moveTo(center.x + side * s * 0.14f, center.y - s * 1.24f)
                    quadraticTo(
                        center.x + side * s * (0.3f + twitch), center.y - s * 1.9f,
                        center.x + side * s * (0.78f + twitch), center.y - s * (2.15f - twitch),
                    )
                },
                FireflyDark,
                style = antenna,
            )
        }
    }
}

/** Heller Weg (Licht-Insel), fertiger Weg und der Schattensaum gegen das Dschungelbild. */
private val RoadLight = Color(0xFFF3ECDB)
private val RoadDone = Color(0xFFBFE6C9)
private val RoadShade = Color(0xFF060A08)
private val FireflyLight = Color(0xFFE8FB8A)
private val FireflyGlow = Color(0xFFEAFB9A)
private val FireflyWing = Color(0xFF3A2A1C)
private val FireflyWingRim = Color(0xFFB59A6E)
private val FireflyShield = Color(0xFFE0874E)
private val FireflyGlowEdge = Color(0xFFB9C94A)
private val FireflyDark = Color(0xFF241A12)
