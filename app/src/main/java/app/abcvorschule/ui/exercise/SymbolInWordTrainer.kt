package app.abcvorschule.ui.exercise

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.speech.SpeechClipText
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.StarFlight
import app.abcvorschule.ui.rewards.drawGlint
import app.abcvorschule.ui.rewards.drawGlowStar
import app.abcvorschule.ui.rewards.roundedStarPath
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.FoundGold
import app.abcvorschule.ui.theme.SilboFibel
import app.abcvorschule.ui.theme.StarlightCream
import app.abcvorschule.ui.theme.StarlineGold
import app.abcvorschule.ui.world.StarsScene
import app.abcvorschule.ui.world.calmPool
import app.abcvorschule.ui.world.rememberReduceMotion
import app.abcvorschule.ui.world.rememberWorldSeconds
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin

/** How long a wrong segment spins around its own centre. */
private const val SpinMs = AbcMotion.LongMs

/**
 * How long the star travels from the word into its silhouette. LongMs rather than the
 * StandardMs of a straight A→B move: the path is an arc, and the child has to be able
 * to follow it — the flight is the causal link "I found that, and that star is mine".
 */
private const val FlightMs = AbcMotion.LongMs

/** Pop-in of the framing stars and the landing flash of a silhouette, in seconds. */
private const val PopS = AbcMotion.ShortMs / 1000f
private const val FlashS = AbcMotion.LongMs / 1000f

/** Langsames Funkeln der Rahmen-Sterne — ruhig, ≥ 4 s wie der Himmel dahinter. */
private const val SparkleS = 4f

/** Schimmer der Leuchtschrift: dezent, damit warmes Licht nicht überstrahlt (Spec „Kontrast"). */
private const val CreamGlowAlpha = 0.35f
private const val GoldGlowAlpha = 0.7f

/** One found star in transit: which segment it leaves, which silhouette it fills. */
private data class SymbolFlight(val segmentIndex: Int, val slotOrdinal: Int)

/**
 * Wort-Detektiv unter dem Sternenhimmel (Spec
 * `2026-10-02-wort-detektiv-sternenhimmel-design.md`, ergänzt
 * `2026-07-31-wort-detektiv-design.md`): find the hunted letter or syllable inside a
 * word the lesson just built.
 *
 * The word stands as **one word** in warm cream light on a calm dark spot; each
 * segment is still its own tap target, but the target grows in height, not width
 * ([WordDetectiveLayout]). A hit turns gold, two or three stars frame it like a tiny
 * constellation, and one star flies in an arc into the next empty star silhouette
 * below the word. No "Zeig mir" (user decision, October 2026): the word is the whole
 * choice, every miss speaks its segment, and the child keeps looking.
 *
 * All decisions (segmentation, targets, hit indices, tap outcomes, line breaking,
 * hit widths, star placement) are made in the unit-tested pure layers; this file only
 * draws and animates.
 */
@Composable
fun SymbolInWordTrainer(
    round: SymbolInWordRound,
    roundIndex: Int,
    pack: ContentPack,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    /** Wie [onSpeak], aber auf dem Feedback-Kanal — läuft parallel zur noch
     * laufenden Ansage (Konnektor + Wort), statt sie abzuwürgen (design doc). */
    onSpeakFeedback: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.wordAtomId}-${round.targetAtomId}"
    var state by remember(roundKey) { mutableStateOf(SymbolInWordProgress.initialState(round)) }
    var complete by remember(roundKey) { mutableStateOf(false) }
    val haptics = LocalAbcHaptics.current
    // Ruhen statt dimmen (PromptRest): kaum gedämpft, die Ansage-Sperre hält die Taps.
    val interactionOpacity = rememberRestOpacity()
    // „Bewegung reduzieren": kein Flug, kein Funkeln — die Silhouette füllt sich direkt.
    val still = rememberReduceMotion()
    // Dieselbe Uhr wie die Welt dahinter; gelesen nur beim Zeichnen.
    val seconds = rememberWorldSeconds(still)

    val target = pack.atoms[round.targetAtomId]
    val label = target?.let { SymbolInWordDerivation.targetLabel(it, round.mode) }
    val scaffold = scaffoldFor(round.targetAtomId)

    // Positions are captured in window space and differenced against the wrapping
    // Box, because a flight crosses ExerciseStage's two separate Columns and there
    // is no shared layout node to animate inside. Observable maps: written from
    // onGloballyPositioned, read during composition to gate the flight — a late
    // write must invalidate, or a geometrically identical next round (L06 r·o·t →
    // T·o·r) would drop its flight silently.
    var rootOffset by remember(roundKey) { mutableStateOf(Offset.Zero) }
    val segmentCenters = remember(roundKey) { mutableStateMapOf<Int, Offset>() }
    val slotCenters = remember(roundKey) { mutableStateMapOf<Int, Offset>() }
    var glyphDp by remember(roundKey) { mutableFloatStateOf(WordDetectiveLayout.MaxGlyphDp) }
    var silhouetteDp by remember(roundKey) { mutableFloatStateOf(WordDetectiveLayout.SilhouetteDp) }

    // When each hit was found and each silhouette filled, on the world clock — drives
    // the stars' pop-in and the landing flash in the draw phase without recomposing.
    val foundAt = remember(roundKey) { mutableStateMapOf<Int, Float>() }
    val landedAt = remember(roundKey) { mutableStateMapOf<Int, Float>() }

    var flight by remember(roundKey) { mutableStateOf<SymbolFlight?>(null) }
    // Keyed on the flight, not the round: a fresh Animatable starts at 0f by
    // construction, so the second hit of "Papa" never draws its star already landed
    // for one frame before the LaunchedEffect resets it.
    val flightProgress = remember(flight) { Animatable(0f) }

    // A silhouette only fills once its star has landed — while a star is in the air
    // the slot stays empty, so the child sees one star, not two.
    val landedCount = state.collected.size - if (flight != null) 1 else 0

    fun handleTap(index: Int) {
        if (complete) return
        if (index !in round.segments.indices) return
        val result = SymbolInWordProgress.tap(state, index)
        // A tap on an already found segment does nothing at all — not even speech,
        // so "already done" reads as inert rather than half-alive.
        if (result.outcome == SymbolInWordTapOutcome.Ignored) return
        onSpeakFeedback(SpeechClipText.forSegment(pack, round, index))
        state = result.state
        val ordinal = result.state.collected.size - 1
        fun launchStar() {
            foundAt[index] = seconds.value
            if (still) landedAt[ordinal] = seconds.value else flight = SymbolFlight(index, ordinal)
        }
        when (result.outcome) {
            SymbolInWordTapOutcome.Miss -> {
                haptics.nudge()
                onResult(false, false, listOf(round.targetAtomId))
            }
            SymbolInWordTapOutcome.MissAlreadyReported ->
                haptics.nudge()
            SymbolInWordTapOutcome.Collected -> {
                haptics.tick()
                launchStar()
            }
            SymbolInWordTapOutcome.RoundComplete -> {
                // celebrate, nicht tick: alle Silhouetten voll ist der Batterie-voll-Moment
                // des Detektivs, und die Jagd feiert ihren mit celebrate (§10).
                haptics.celebrate()
                launchStar()
                complete = true
            }
            SymbolInWordTapOutcome.Ignored -> Unit
        }
    }

    LaunchedEffect(flight) {
        val active = flight ?: return@LaunchedEffect
        flightProgress.animateTo(1f, tween(durationMillis = FlightMs, easing = AbcMotion.Enter))
        // Clearing the flight is what fills the silhouette, so the hand-off from the
        // flying star to the resting one happens in one frame — with a small flash.
        landedAt[active.slotOrdinal] = seconds.value
        flight = null
    }

    // The full row of stars IS the success signal, so a "Weiter" tap would only add a
    // dead end for a child who cannot read the button. The delay sits in front of
    // onResult because reporting starts the spoken success phase, which must not talk
    // over the last landing.
    LaunchedEffect(complete) {
        if (!complete) return@LaunchedEffect
        delay(HuntCelebration.HoldMs)
        onResult(true, false, listOf(round.targetAtomId))
    }

    Box(modifier = modifier.onGloballyPositioned { rootOffset = it.positionInWindow() }) {
        ExerciseStage(
            promptChrome = {
                TaskPromptChrome(
                    title = null,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeakPrompt = onSpeakPrompt,
                )
            },
            prompt = {
                // Symbol, Wort und Silhouetten sind eine Gruppe, die als Ganzes in den
                // Aufgabenblock passen muss: ExerciseStage scrollt und clippt nicht.
                // Breite und Höhe kommen hier zusammen, die Rechnung steht in
                // WordDetectiveLayout (verticalFit, layout).
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val density = LocalDensity.current
                    val availableWidth = maxWidth.value
                    val availableHeight = if (maxHeight.value.isFinite()) maxHeight.value else Float.MAX_VALUE
                    val labelHeight = if (label != null) WordDetectiveLayout.MinHitHeightDp else 0f
                    // Gemessen, nicht geschätzt: Silbo Fibel ist proportional, und die
                    // Layout-Rechnung braucht die echten Vorschübe je Segment (in em).
                    val measurer = rememberTextMeasurer()
                    val advances = remember(round.segments, density) {
                        val refStyle = TextStyle(fontFamily = SilboFibel, fontWeight = FontWeight.Bold, fontSize = density.glyphSize(100f))
                        val refPx = with(density) { 100.dp.toPx() }
                        (round.segments + "-").map { measurer.measure(it, refStyle).size.width / refPx }
                    }
                    val (layout, fit) = remember(advances, round.breakBefore, availableWidth, availableHeight, labelHeight) {
                        fun lay(cap: Float) = WordDetectiveLayout.layout(
                            advances = advances.dropLast(1),
                            hyphenAdvance = advances.last(),
                            breakBefore = round.breakBefore,
                            availableDp = availableWidth,
                            maxGlyphDp = cap,
                        )
                        val wide = lay(WordDetectiveLayout.MaxGlyphDp)
                        val fit = WordDetectiveLayout.verticalFit(availableHeight, labelHeight, wide.lines.size)
                        (if (fit.glyphCapDp < wide.glyphDp) lay(fit.glyphCapDp) else wide) to fit
                    }
                    LaunchedEffect(layout.glyphDp) { glyphDp = layout.glyphDp }
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (label != null) {
                            TargetLabelRow(
                                label = label,
                                onClick = { target?.let { onSpeakFeedback(it.lemma) } },
                                interactionLocked = interactionLocked,
                                // Ohne eigene ruhige Zone: ein zweiter dunkler Fleck über dem
                                // des Worts las sich als Schatten-Säule. Hinter dem Symbol
                                // stehen nur blasse Sterne, Creme liegt dort auf dem Himmel
                                // bei 14:1.
                                modifier = Modifier.alpha(interactionOpacity),
                            )
                        }
                        // Luft zwischen Frage und Arbeit: das Symbol ist die Frage, das Wort
                        // die Arbeit, und der Blick soll nach unten wandern.
                        Spacer(modifier = Modifier.height(fit.labelToWordDp.dp))
                        DetectiveWord(
                            round = round,
                            layout = layout,
                            state = state,
                            enabled = !complete && !interactionLocked,
                            still = still,
                            seconds = seconds,
                            foundAt = foundAt,
                            onTap = ::handleTap,
                            onSegmentPlaced = { index, center -> segmentCenters[index] = center },
                            // Ruhige Zone vor der Deckkraft: sonst zeichnete sie in deren
                            // Ebene und würde an den Kanten des Bauteils abgeschnitten (§10).
                            modifier = Modifier.calmPool().alpha(interactionOpacity),
                        )
                        // Die Silhouetten stehen direkt unter dem Wort, nicht unten im
                        // Antwortblock (Mockup): am unteren Rand lagen sie auf dem Horizont,
                        // weit weg vom Wort, und der Stern hatte einen halben Bildschirm zu
                        // fliegen. Die ganze Gruppe liegt so mittig im Himmel.
                        Spacer(modifier = Modifier.height(fit.wordToStarsDp.dp))
                        StarSilhouettes(
                            round = round,
                            maxSizeDp = fit.silhouetteCapDp,
                            landedCount = landedCount,
                            showGlyphs = scaffold == ScaffoldLevel.Beginner && label != null,
                            celebrate = complete,
                            still = still,
                            seconds = seconds,
                            landedAt = landedAt,
                            onSlotPlaced = { ordinal, center -> slotCenters[ordinal] = center },
                            onSize = { silhouetteDp = it },
                        )
                    }
                }
            },
            // Kein Antwortblock: es gibt nichts zu wählen außer dem Wort selbst, und seit
            // „Zeig mir" entfallen ist, auch keinen Knopf (PRODUCT_PRINCIPLES §9).
            answers = {},
        )

        // The star travels from the found segment into its silhouette. Drawn here,
        // above ExerciseStage, because the two endpoints live in the stage's two
        // separate Columns and no layout node contains both.
        val active = flight
        val from = active?.let { segmentCenters[it.segmentIndex] }
        val to = active?.let { slotCenters[it.slotOrdinal] }
        if (active != null && from != null && to != null) {
            val density = LocalDensity.current
            val start = from - rootOffset
            val end = to - rootOffset
            val startRadius = WordDetectiveLayout.frameStarRadiusDp(glyphDp)
            val endRadius = silhouetteDp * SilhouetteStarFraction
            // Dotted trail behind the star, fading towards its tail (Mockup „Tomate").
            Canvas(Modifier.matchParentSize()) {
                val p = flightProgress.value
                val steps = 22
                for (k in 0..steps) {
                    val t = p * k / steps
                    val c = start + StarFlight.offset(start, end, t, swaySign = -1f)
                    drawCircle(StarlineGold, alpha = 0.55f * (k / steps.toFloat()), radius = 1.2.dp.toPx(), center = c)
                }
            }
            val p = flightProgress.value
            val current = start + StarFlight.offset(start, end, p, swaySign = -1f)
            val radiusDp = startRadius + (endRadius - startRadius) * p
            val boxDp = (endRadius * 3f).dp
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { current.x.toDp() } - boxDp / 2,
                        y = with(density) { current.y.toDp() } - boxDp / 2,
                    )
                    .size(boxDp)
                    .testTag("detective_flight"),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    drawGlowStar(center = center, radius = radiusDp.dp.toPx(), rotationDeg = -30f + 30f * p)
                }
            }
        }
    }
}

/** Outer radius of the five-pointed star inside its silhouette box. */
private const val SilhouetteStarFraction = 0.46f

/** Text size for a glyph that must *render* at [dp], whatever the system font scale. */
private fun Density.glyphSize(dp: Float): TextUnit = dp.dp.toSp()

private fun glowStyle(sizeDp: Float, density: Density, color: Color, glowAlpha: Float, blurDp: Float) = TextStyle(
    fontFamily = SilboFibel,
    fontWeight = FontWeight.Bold,
    fontSize = density.glyphSize(sizeDp),
    color = color,
    shadow = Shadow(color = color.copy(alpha = glowAlpha), offset = Offset.Zero, blurRadius = with(density) { blurDp.dp.toPx() }),
)

/** The hunted symbol, as a case pair ("P / p") for letters and a single lowercase
 * form for syllables (design doc §2) — as warm light on the dark sky, the second named
 * exception to "content only on light" next to the word. Tapping speaks it. */
@Composable
private fun TargetLabelRow(
    label: SymbolInWordDerivation.TargetLabel,
    onClick: () -> Unit,
    interactionLocked: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val size = WordDetectiveLayout.TargetLabelDp
    val style = glowStyle(size, density, StarlightCream, CreamGlowAlpha, blurDp = 10f)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        modifier = modifier
            // A single-form single-glyph target ("ß") is barely one glyph wide, under
            // the hit-box floor every touch target in this app has to clear.
            .sizeIn(minWidth = WordDetectiveLayout.MinHitHeightDp.dp, minHeight = WordDetectiveLayout.MinHitHeightDp.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = !interactionLocked,
            ) { onClick() }
            .testTag("detective_target"),
    ) {
        Text(text = label.primary, style = style)
        if (label.alternate != null) {
            // A separator, not something to read: dimmed so the two letters dominate
            // (design doc §2). Decorative, not held to a contrast floor. Ein Mittelpunkt
            // statt des Schrägstrichs (Nutzerwunsch Oktober 2026): wirkt freundlicher und
            // liest sich nicht als „oder"/„geteilt". Der Punkt ist von sich aus klein,
            // darum in voller Schriftgröße — halb so groß wie der alte Strich verschwände er.
            Text(
                text = "·",
                style = style.copy(fontSize = density.glyphSize(size), fontWeight = FontWeight.Normal, shadow = null),
                color = StarlightCream.copy(alpha = 0.45f),
            )
            Text(text = label.alternate, style = style)
        }
    }
}

/**
 * The word as one word: glyphs in their natural spacing, each segment with its own
 * invisible, taller tap target ([WordDetectiveLayout]); found segments in gold with
 * their framing stars on top.
 */
@Composable
private fun DetectiveWord(
    round: SymbolInWordRound,
    layout: WordDetectiveLayout.Layout,
    state: SymbolInWordState,
    enabled: Boolean,
    still: Boolean,
    seconds: State<Float>,
    foundAt: Map<Int, Float>,
    onTap: (Int) -> Unit,
    onSegmentPlaced: (Int, Offset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    Box(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth()) {
            layout.lines.forEachIndexed { lineIndex, line ->
                val upperOfTwo = layout.lines.size > 1 && lineIndex == 0
                Box(modifier = Modifier.fillMaxWidth().height(layout.rowHeightDp.dp)) {
                    line.segments.forEach { box ->
                        SegmentGlyph(
                            segment = round.segments[box.index],
                            box = box,
                            glyphDp = layout.glyphDp,
                            state = state,
                            enabled = enabled,
                            onTap = onTap,
                            onPlaced = onSegmentPlaced,
                        )
                    }
                    line.hyphenX?.let { x ->
                        Text(
                            text = "-",
                            style = glowStyle(layout.glyphDp, density, StarlightCream, CreamGlowAlpha, blurDp = 10f),
                            softWrap = false,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .offset(x = x.dp)
                                .wrapContentWidth(Alignment.Start, unbounded = true),
                        )
                    }
                    // Die Rahmen-Sterne über dem Wort: ein kleines Sternbild je Treffer.
                    Canvas(Modifier.matchParentSize()) {
                        val now = seconds.value
                        line.segments.filter { it.index in state.collected }.forEach { box ->
                            val pop = if (still) 1f else ((now - (foundAt[box.index] ?: now)) / PopS).coerceIn(0f, 1f)
                            val points = WordDetectiveLayout.frameStars(
                                box, round.segments[box.index].length, layout, upperOfTwo,
                            ).map { Offset(it.x.dp.toPx(), it.y.dp.toPx()) }
                            drawFrameConstellation(
                                points = points,
                                radius = WordDetectiveLayout.frameStarRadiusDp(layout.glyphDp).dp.toPx(),
                                pop = pop,
                                now = if (still) 0f else now,
                                seed = box.index,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Zarte gestrichelte Linie zwischen den Sternen eines Treffers (geschlossen beim
 * Dreieck), darauf vierstrahlige Glanzlichter, die leicht versetzt ruhig funkeln.
 */
private fun DrawScope.drawFrameConstellation(points: List<Offset>, radius: Float, pop: Float, now: Float, seed: Int) {
    if (pop <= 0f) return
    val path = Path().apply {
        points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
        if (points.size > 2) close()
    }
    drawPath(
        path,
        color = StarlineGold,
        alpha = 0.45f * pop,
        style = Stroke(
            width = 1.5.dp.toPx(),
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 5.dp.toPx())),
        ),
    )
    points.forEachIndexed { k, p ->
        val phase = now / SparkleS * 2f * PI.toFloat() + k * 2.1f + seed
        val wave = if (now == 0f) 0f else sin(phase)
        val scale = (0.98f + 0.12f * wave) * (0.6f + 0.4f * pop)
        rotate(degrees = 10f + 10f * wave, pivot = p) {
            drawGlint(p, size = radius * scale, alpha = (0.9f + 0.1f * wave) * pop)
        }
    }
}

@Composable
private fun SegmentGlyph(
    segment: String,
    box: WordDetectiveLayout.SegmentBox,
    glyphDp: Float,
    state: SymbolInWordState,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onPlaced: (Int, Offset) -> Unit,
) {
    val index = box.index
    val found = index in state.collected
    val isWrong = state.wrongIndex == index
    val density = LocalDensity.current
    // An Animatable driven off the nonce, not animateFloatAsState off a target
    // value: the spin must replay when the child taps the *same* wrong segment
    // twice. snapTo(0f) afterwards leaves the glyph upright; the else branch
    // un-rotates a segment whose spin was cut short by a tap on a different one.
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(state.wrongNonce) {
        if (isWrong && state.wrongNonce > 0) {
            rotation.snapTo(0f)
            rotation.animateTo(360f, tween(durationMillis = SpinMs))
            rotation.snapTo(0f)
        } else {
            rotation.snapTo(0f)
        }
    }
    val color by animateColorAsState(
        targetValue = if (found) FoundGold else StarlightCream,
        animationSpec = tween(durationMillis = AbcMotion.QuickMs),
        label = "detective_segment_colour",
    )
    val style = if (found) {
        glowStyle(glyphDp, density, color, GoldGlowAlpha, blurDp = 14f)
    } else {
        glowStyle(glyphDp, density, color, CreamGlowAlpha, blurDp = 10f)
    }
    // The hit box may be wider than the glyph (a narrow "i" borrows from its
    // neighbours), so the box sits at hitX and the glyph is offset inside it — the
    // visible spacing stays the word's own.
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .offset(x = box.hitX.dp)
            .width(box.hitWidth.dp)
            .fillMaxHeight()
            .onGloballyPositioned { coordinates ->
                // Flight start: the glyph's centre, not the (possibly off-centre) hit box's.
                val origin = coordinates.positionInWindow()
                onPlaced(
                    index,
                    origin + Offset(
                        with(density) { (box.glyphX - box.hitX + box.glyphWidth / 2f).dp.toPx() },
                        coordinates.size.height / 2f,
                    ),
                )
            }
            // No ripple: the box is invisible and taller than the glyph, a grey slab
            // flashing over the night sky would read as a mistake. Speech, spin and gold
            // are the feedback.
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                enabled = enabled && !found,
            ) { onTap(index) }
            .testTag("detective_segment_$index"),
    ) {
        Text(
            text = segment,
            style = style,
            softWrap = false,
            maxLines = 1,
            modifier = Modifier
                .offset(x = (box.glyphX - box.hitX).dp)
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .graphicsLayer { rotationZ = rotation.value },
        )
    }
}

/**
 * One star silhouette per hit (dashed outline, nearly transparent), centred below the
 * word but never reaching into the telescope's corner ([StarsScene]). A landed star is
 * the reward star itself ([drawGlowStar]) and flashes briefly on landing; when the row
 * is full the halos pulse until the success phase takes over.
 */
@Composable
private fun StarSilhouettes(
    round: SymbolInWordRound,
    /** Höhendeckel aus [WordDetectiveLayout.verticalFit]. */
    maxSizeDp: Float,
    landedCount: Int,
    /** Hilfestufe „Beginner" (Prinzip 6): das gesuchte Segment liegt blass in der Silhouette. */
    showGlyphs: Boolean,
    celebrate: Boolean,
    still: Boolean,
    seconds: State<Float>,
    landedAt: Map<Int, Float>,
    onSlotPlaced: (Int, Offset) -> Unit,
    onSize: (Float) -> Unit,
) {
    val density = LocalDensity.current
    // Where this row starts in the window: the telescope's no-star zone is measured
    // from the world's left edge, which is the window's.
    var leftInWindowDp by remember { mutableFloatStateOf(0f) }
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { leftInWindowDp = with(density) { it.positionInWindow().x.toDp().value } },
    ) {
        val keepOut = (StarsScene.TelescopeZoneWidthDp - leftInWindowDp).coerceAtLeast(0f)
        val row = WordDetectiveLayout.silhouetteRow(round.targetIndices.size, maxWidth.value, keepOut, maxSizeDp)
        LaunchedEffect(row.sizeDp) { onSize(row.sizeDp) }
        Row(
            modifier = Modifier.align(Alignment.Center).testTag("detective_slots"),
            horizontalArrangement = Arrangement.spacedBy(row.gapDp.dp),
        ) {
            repeat(round.targetIndices.size) { ordinal ->
                val filled = ordinal < landedCount
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(row.sizeDp.dp)
                        .onGloballyPositioned { coordinates ->
                            onSlotPlaced(
                                ordinal,
                                coordinates.positionInWindow() +
                                    Offset(coordinates.size.width / 2f, coordinates.size.height / 2f),
                            )
                        }
                        .testTag("detective_star_$ordinal"),
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        val outer = size.minDimension * SilhouetteStarFraction
                        if (filled) {
                            val now = seconds.value
                            val flash = if (still) 0f else 1f - ((now - (landedAt[ordinal] ?: -100f)) / FlashS).coerceIn(0f, 1f)
                            val pulse = if (celebrate && !still) 0.5f + 0.5f * sin(now * PI.toFloat() * 1000f / AbcMotion.PulseMs) else 0f
                            drawGlowStar(
                                center = center,
                                radius = outer * (1f + 0.12f * flash),
                                halo = 0.8f + 1.2f * flash + 0.6f * pulse,
                            )
                        } else {
                            val path = roundedStarPath(center, outer, outer * 0.5f)
                            drawPath(path, StarlightCream.copy(alpha = 0.08f))
                            drawPath(
                                path,
                                StarlightCream.copy(alpha = 0.45f),
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.6.dp.toPx(), 3.2.dp.toPx())),
                                ),
                            )
                        }
                    }
                    if (!filled && showGlyphs) {
                        // In reading order, so the two silhouettes of "Mama" already spell
                        // out `M` and `m` — the scaffold, not a second word.
                        Text(
                            text = round.segments[round.targetIndices[ordinal]],
                            style = TextStyle(
                                fontFamily = SilboFibel,
                                fontWeight = FontWeight.Bold,
                                fontSize = density.glyphSize(row.sizeDp * 0.34f),
                                color = StarlightCream.copy(alpha = 0.3f),
                            ),
                            softWrap = false,
                            modifier = Modifier.offset(y = (row.sizeDp * 0.03f).dp),
                        )
                    }
                }
            }
        }
    }
}
