package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.SoftCoral
import app.abcvorschule.ui.theme.SoftGold
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky
import kotlinx.coroutines.delay

/** Segment colours cycle; the palette only marks boundaries, it carries no meaning,
 * so repeating it on an eight-segment word is harmless (design doc §5). */
private val SegmentPalette = listOf(SoftMint, SoftCoral, SoftSky, SoftGold, SoftSand)

/** A collected segment stays visible but spent — this is the "completed colour"
 * and the "no longer tappable" affordance in one treatment. */
private const val CollectedSegmentAlpha = 0.35f

/** Scaffold "Beginner": the target lies in the stroke as a silhouette (Prinzip 6). */
private const val SilhouetteAlpha = 0.18f

/** How long a wrong segment spins around its own centre. */
private const val SpinMs = 450

/** How long a collected glyph travels from the word down onto its slot. The flight
 * is the causal link for a child who cannot read: "I tapped that, and that moved
 * there." */
private const val FlightMs = 350

/** Celebration before handing off, matching SymbolHuntTrainer's battery hold so
 * both hunts feel the same. */
private const val CelebrationHoldMs = 900L

/** ExerciseStage caps its content at 420dp and pads 12dp per side; used only as an
 * upper bound, the real width is measured so a narrow phone shrinks correctly. */
private const val StageContentDp = 396f

/** Design doc §5: the stroke is at least this wide even for a one-glyph target. */
private const val MinSlotWidthDp = 40f

/** Height of the area above a stroke that holds the landed glyph. Also the flight's
 * landing box, so the glyph arrives exactly where it comes to rest. */
private val SlotGlyphHeight = 56.dp

/** Design doc §5: a bare 3dp stroke, no frame, no fill. */
private val SlotStrokeHeight = 3.dp

/** One collected glyph in transit: which segment was tapped, and which stroke it is
 * heading for. Held outside the state machine because it is pure presentation. */
private data class SymbolFlight(val segmentIndex: Int, val slotOrdinal: Int)

/**
 * Wort-Detektiv: find the hunted letter or syllable inside a word the lesson just
 * built (design doc §5/§6).
 *
 * The word is rendered as plain coloured glyphs without frames — it must read as a
 * word, not as a tray. The placeholder strokes underneath collect the hits; they
 * are receipts, not answer options, which is why they are bare strokes rather than
 * the Wort-Bauer's rounded 22dp slots.
 *
 * All decisions (segmentation, targets, hit indices, tap outcomes, row wrapping)
 * are made in the unit-tested pure layers; this file only draws and animates.
 */
@Composable
fun SymbolInWordTrainer(
    round: SymbolInWordRound,
    roundIndex: Int,
    pack: ContentPack,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.wordAtomId}-${round.targetAtomId}"
    var state by remember(roundKey) { mutableStateOf(SymbolInWordProgress.initialState(round)) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    var complete by remember(roundKey) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val target = pack.atoms[round.targetAtomId]
    val label = target?.let { SymbolInWordDerivation.targetLabel(it, round.mode) }
    val scaffold = scaffoldFor(round.targetAtomId)
    val slotWidth = slotWidthDp(label)

    // Positions are captured in window space and differenced against the wrapping
    // Box, because a flight crosses ExerciseStage's two separate Columns and there
    // is no shared layout node to animate inside (design doc §6).
    var rootOffset by remember(roundKey) { mutableStateOf(Offset.Zero) }
    val segmentCenters = remember(roundKey) { mutableMapOf<Int, Offset>() }
    val slotCenters = remember(roundKey) { mutableMapOf<Int, Offset>() }

    // An Animatable driven from a LaunchedEffect rather than animateFloatAsState:
    // the flight's target value never changes (it is always "go to the slot"), and
    // animateFloatAsState initialises its internal Animatable *at* the target, so a
    // constant target would land the glyph instantly and never replay per hit.
    var flight by remember(roundKey) { mutableStateOf<SymbolFlight?>(null) }
    val flightProgress = remember(roundKey) { Animatable(0f) }

    // Reported up from WordSegments, which is where the row sizing is computed, so
    // the flight can start at the size the segment is actually drawn at instead of
    // popping to the stroke's size at take-off. Identical to the stroke size for
    // every word in the current content; it only diverges once a word is long
    // enough for WordFrameSizing to shrink the glyphs.
    var segmentGlyphSp by remember(roundKey) { mutableFloatStateOf(WordFrameSizing.MaxGlyphSp) }

    // A stroke only fills once its glyph has actually landed on it — while a copy is
    // in the air the slot stays empty, so the child sees one glyph, not two.
    val landedCount = state.collected.size - if (flight != null) 1 else 0

    fun handleTap(index: Int) {
        if (resolved || complete) return
        val segment = round.segments.getOrNull(index) ?: return
        val result = SymbolInWordProgress.tap(state, index)
        // A tap on an already collected segment does nothing at all — not even
        // speech, so "already done" reads as inert rather than half-alive.
        if (result.outcome == SymbolInWordTapOutcome.Ignored) return
        onSpeak(segment)
        state = result.state
        when (result.outcome) {
            SymbolInWordTapOutcome.Miss -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onResult(false, false, listOf(round.targetAtomId))
            }
            SymbolInWordTapOutcome.MissAlreadyReported ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            SymbolInWordTapOutcome.Collected ->
                flight = SymbolFlight(index, result.state.collected.size - 1)
            SymbolInWordTapOutcome.RoundComplete -> {
                flight = SymbolFlight(index, result.state.collected.size - 1)
                complete = true
            }
            SymbolInWordTapOutcome.Ignored -> Unit
        }
    }

    LaunchedEffect(flight) {
        if (flight == null) return@LaunchedEffect
        flightProgress.snapTo(0f)
        flightProgress.animateTo(1f, tween(durationMillis = FlightMs, easing = FastOutSlowInEasing))
        // Clearing the flight is what fills the stroke, so the hand-off from the
        // flying copy to the resting glyph happens in one frame.
        flight = null
    }

    // The full set of slots IS the success signal, so a "Weiter" tap would only add
    // a dead end for a child who cannot read the button. The delay sits in front of
    // onResult because reporting starts the spoken success phase, which must not
    // talk over the celebration.
    LaunchedEffect(complete) {
        if (!complete) return@LaunchedEffect
        delay(CelebrationHoldMs)
        onResult(true, false, listOf(round.targetAtomId))
    }

    Box(modifier = modifier.onGloballyPositioned { rootOffset = it.positionInWindow() }) {
        ExerciseStage(
            prompt = {
                TaskPromptChrome(
                    title = null,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeakPrompt = onSpeakPrompt,
                )
                // A non-null label implies a non-null target — it is derived from it —
                // which is why `target.display` needs no second null check here.
                if (label != null) {
                    TargetLabelRow(
                        label = label,
                        onClick = { onSpeak(target.display) },
                    )
                }
                WordSegments(
                    round = round,
                    state = state,
                    enabled = !complete && !resolved,
                    onTap = ::handleTap,
                    onSegmentPlaced = { index, center -> segmentCenters[index] = center },
                    onGlyphSpMeasured = { segmentGlyphSp = it },
                )
            },
            answers = {
                SlotRow(
                    round = round,
                    label = label,
                    slotWidth = slotWidth,
                    landedCount = landedCount,
                    resolved = resolved,
                    showSilhouette = scaffold == ScaffoldLevel.Beginner,
                    celebrate = complete,
                    onSlotPlaced = { ordinal, center -> slotCenters[ordinal] = center },
                )
                if (SymbolInWordProgress.resolveAvailable(state) && !resolved && !complete) {
                    AbcResolveButton(
                        onClick = {
                            resolved = true
                            flight = null
                            state = SymbolInWordProgress.resolve(state)
                            onResult(false, true, listOf(round.targetAtomId))
                        },
                    )
                }
            },
        )

        // The collected glyph travels from its place in the word onto its stroke. It
        // is drawn here, above ExerciseStage, because the two endpoints live in the
        // stage's two separate Columns and no layout node contains both.
        val active = flight
        val from = active?.let { segmentCenters[it.segmentIndex] }
        val to = active?.let { slotCenters[it.slotOrdinal] }
        if (active != null && from != null && to != null && label != null && !resolved) {
            val progress = flightProgress.value
            val current = Offset(
                x = from.x + (to.x - from.x) * progress,
                y = from.y + (to.y - from.y) * progress,
            ) - rootOffset
            // Grows (or shrinks) from the size the word is drawn at into the size the
            // stroke holds, so the copy leaves the word at the size the child just
            // touched rather than jumping to the stroke's size in mid-air.
            val fontSp = segmentGlyphSp + (AbcDimens.syllableSp.value - segmentGlyphSp) * progress
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { current.x.toDp() } - slotWidth / 2,
                        y = with(density) { current.y.toDp() } - SlotGlyphHeight / 2,
                    )
                    .width(slotWidth)
                    .height(SlotGlyphHeight)
                    .testTag("detective_flight"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label.primary,
                    fontSize = fontSp.sp,
                    color = SoftGold,
                    modifier = Modifier.alpha(1f - progress * 0.15f),
                )
            }
        }
    }
}

/**
 * Stroke width from the target glyph's estimated advance, using the same fraction
 * WordFrameSizing uses, so a three-letter target like "Sch" gets a visibly wider
 * stroke than "e" — the stroke's width is what tells a child "Sch is one thing,
 * not three" (design doc §5).
 */
private fun slotWidthDp(label: SymbolInWordDerivation.TargetLabel?): Dp =
    (AbcDimens.syllableSp.value * WordFrameSizing.GlyphAspect * (label?.primary?.length ?: 1))
        .coerceAtLeast(MinSlotWidthDp).dp

/** The hunted symbol, as a case pair ("P / p") for letters and a single lowercase
 * form for syllables (design doc §2). */
@Composable
private fun TargetLabelRow(
    label: SymbolInWordDerivation.TargetLabel,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable { onClick() }.testTag("detective_target"),
    ) {
        Text(text = label.primary, fontSize = AbcDimens.letterSp, color = SoftSand)
        if (label.alternate != null) {
            // A separator, not something to read: half size and dimmed so the two
            // letters dominate (design doc §2).
            Text(
                text = "/",
                fontSize = AbcDimens.letterSp / 2,
                color = MutedText.copy(alpha = 0.45f),
            )
            Text(text = label.alternate, fontSize = AbcDimens.letterSp, color = SoftSand)
        }
    }
}

/** The word as tappable coloured glyphs, wrapped into balanced rows when a word is
 * too long to keep 56dp targets on one line (design doc §5). */
@Composable
private fun WordSegments(
    round: SymbolInWordRound,
    state: SymbolInWordState,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onSegmentPlaced: (Int, Offset) -> Unit,
    onGlyphSpMeasured: (Float) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Measured rather than assumed: on a phone narrower than ExerciseStage's
        // 420dp cap the usable width is smaller, and overestimating it would push
        // the frames past the edge instead of wrapping them.
        val available = maxWidth.value.coerceAtMost(StageContentDp)
        val perRow = WordFrameSizing.segmentsPerRow(available, round.segments.size)
        val frameWidth = WordFrameSizing.frameWidthDp(available, perRow)
        val glyphSp = WordFrameSizing.glyphSp(frameWidth, round.segments.maxOfOrNull { it.length } ?: 1)
        val gap = WordFrameSizing.gapDp(available, perRow)
        // In a SideEffect, not inline: this writes state the caller reads, and that
        // is only safe once the composition it comes from has succeeded. Writing the
        // same value again is a no-op for recomposition, so it settles immediately.
        SideEffect { onGlyphSpMeasured(glyphSp) }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(gap.dp),
        ) {
            round.segments.withIndex().toList().chunked(perRow).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                    row.forEach { (index, segment) ->
                        SegmentGlyph(
                            segment = segment,
                            index = index,
                            state = state,
                            frameWidthDp = frameWidth,
                            glyphSp = glyphSp,
                            enabled = enabled,
                            onTap = onTap,
                            onPlaced = onSegmentPlaced,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SegmentGlyph(
    segment: String,
    index: Int,
    state: SymbolInWordState,
    frameWidthDp: Float,
    glyphSp: Float,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onPlaced: (Int, Offset) -> Unit,
) {
    val collected = index in state.collected
    val isWrong = state.wrongIndex == index
    // An Animatable driven off the nonce, not animateFloatAsState off a target
    // value: the spin must replay when the child taps the *same* wrong segment
    // twice, and a target value derived from state would be unchanged in that case.
    // snapTo(0f) afterwards leaves the glyph upright rather than at a multiple of
    // 360° that grows all round; the else branch un-rotates a segment whose spin was
    // cut short by a tap on a different segment.
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
    Box(
        modifier = Modifier
            .width(frameWidthDp.dp)
            .height(AbcDimens.kidTouch)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.size
                onPlaced(
                    index,
                    coordinates.positionInWindow() + Offset(bounds.width / 2f, bounds.height / 2f),
                )
            }
            .clickable(enabled = enabled && !collected) { onTap(index) }
            .testTag("detective_segment_$index"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = segment,
            fontSize = glyphSp.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (collected) {
                MutedText.copy(alpha = CollectedSegmentAlpha)
            } else {
                SegmentPalette[index % SegmentPalette.size]
            },
            modifier = Modifier.rotate(rotation.value),
        )
    }
}

/**
 * One bare stroke per hit. A landed glyph rests in SoftGold — the same colour as
 * stars and points, so a filled stroke reads as "earned". After "Zeig mir" the same
 * glyphs arrive dimmed instead: resolving is not a reward (Prinzip 8, design doc §6).
 */
@Composable
private fun SlotRow(
    round: SymbolInWordRound,
    label: SymbolInWordDerivation.TargetLabel?,
    slotWidth: Dp,
    landedCount: Int,
    resolved: Boolean,
    showSilhouette: Boolean,
    celebrate: Boolean,
    onSlotPlaced: (Int, Offset) -> Unit,
) {
    // The infinite pulse only ever runs while celebrating, so it is only created
    // then instead of ticking for the whole round with nothing to show.
    val glow = if (celebrate) {
        val transition = rememberInfiniteTransition(label = "detective_slot_glow")
        val animated by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "detective_slot_glow_value",
        )
        animated
    } else {
        1f
    }
    val landedColor = if (resolved) MutedText else SoftGold
    Row(
        modifier = Modifier.fillMaxWidth().testTag("detective_slots"),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        repeat(round.targetIndices.size) { ordinal ->
            val filled = ordinal < landedCount
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .width(slotWidth)
                        .height(SlotGlyphHeight)
                        .onGloballyPositioned { coordinates ->
                            onSlotPlaced(
                                ordinal,
                                coordinates.positionInWindow() +
                                    Offset(coordinates.size.width / 2f, coordinates.size.height / 2f),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (filled && label != null) {
                        // Always the target's own form, never the tapped segment's: the
                        // strokes are the receipt for one question ("finde alle P"), so
                        // two hits on "Papa" must both read "P", and under the label
                        // "mi" no "Mi" may land (design doc §6). The §4 invariant keeps
                        // the two forms a pure case difference.
                        Text(
                            text = label.primary,
                            fontSize = AbcDimens.syllableSp,
                            color = landedColor,
                            modifier = Modifier.alpha(if (celebrate) glow else 1f),
                        )
                    } else if (showSilhouette && label != null) {
                        // Scaffold "Beginner": the target sits in the stroke as a
                        // silhouette (Prinzip 6 — Silhouette vs. Lücke, per stroke
                        // rather than globally).
                        Text(
                            text = label.primary,
                            fontSize = AbcDimens.syllableSp,
                            color = SoftSand.copy(alpha = SilhouetteAlpha),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .width(slotWidth)
                        .height(SlotStrokeHeight)
                        .background(
                            color = if (filled) landedColor else MutedText,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}
