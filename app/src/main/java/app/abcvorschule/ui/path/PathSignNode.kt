package app.abcvorschule.ui.path

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.R
import app.abcvorschule.progress.LessonState
import app.abcvorschule.ui.components.IconLock
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.theme.LeafGreenLight
import app.abcvorschule.ui.theme.SkyBlueLight
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.WarmMuted
import app.abcvorschule.ui.theme.WoodDark
import app.abcvorschule.ui.theme.WoodDarkShade
import app.abcvorschule.ui.theme.WoodMid
import app.abcvorschule.ui.theme.WoodMidShade
import app.abcvorschule.ui.theme.WoodWarm
import app.abcvorschule.ui.theme.WoodWarmShade

/** Deliberately not named PathSignNode — a sibling object and composable with the
 *  same name compiles, but reads like a typo at every call site. */
object PathSignDimens {
    val BoardWidth = 136.dp
    val BoardHeight = 86.dp
    val PostHeight = 30.dp

    /** Board plus post — the whole thing is one touch target. */
    val TotalHeight = BoardHeight + PostHeight
}

private val BoardShape = RoundedCornerShape(14.dp)
private val RingWidth = 4.dp

/**
 * Effective text sizes on the sign's board.
 *
 * The board is a painted object in the landscape, fixed at 136×86dp — it does not
 * grow with the system font scale the way a reading surface would (dp-stable
 * lettering on a pictorial object is the sanctioned exception; the label is not
 * something the child reads, TalkBack announces it). Left alone, titleLarge at
 * font scale 2.0 renders "C y x qu" some 175dp wide — clipped mid-glyph by
 * maxLines=1 — and stacks label plus emoji row ~90dp tall on the 86dp board.
 *
 * So both text roles are capped: the rendered size stops growing at
 * [MaxBoardFontScale] (the vertical budget), and the label additionally shrinks
 * until its estimated width fits [MaxLabelWidthDp]. Font scale 1.3 — the test
 * device — sits below both caps for every authored label and renders exactly as
 * before.
 *
 * Pure math (estimated glyph advances, no Compose), so it is JVM-unit-testable.
 */
internal object PathSignLabel {
    /** titleLarge's authored size — the label never exceeds this in sp. */
    const val BaseLabelSp = 22f

    /** The emoji row's authored size. */
    const val BaseEmojiSp = 16f

    /**
     * The corner glyph's column, measured from the board's edge: 8dp of padding
     * plus a 16dp star or lock ([PathSignNode]'s TopEnd slot). The nail that stands
     * in for both on an ordinary sign is smaller (10dp padding, 6dp head = 16dp), so
     * this is the widest of the three — and it has to be, because one gutter has to
     * serve every state: a label that resized as the sign went Locked -> Available
     * -> Mastered would change size under the child as it progressed.
     */
    const val CornerGlyphGutterDp = 24f

    /**
     * Usable label width: the 136dp board minus [CornerGlyphGutterDp] on *both*
     * sides, because the label column is centred on the board — what it may not
     * enter on the right it must give up on the left as well.
     *
     * Was 120dp, which only subtracted the ring and the rounded corners and ignored
     * the corner glyph entirely. 120dp centred spans x = 8..128, and the glyph's box
     * is x = 112..128: at font scale 1.3 "C y x qu" renders ~116dp wide and reaches
     * x ≈ 126, at [MaxBoardFontScale] it fills the full 120dp and runs under the
     * star or the lock. 88dp keeps the label's line box clear of that column at
     * every scale (its right edge lands exactly on x = 112). The ring and the corner
     * radius are covered on the way — 8dp of padding is more than the 4dp ring.
     *
     * The price is that the two longest authored labels ("C y x qu", "Sch ch+", both
     * ~4.05em) now shrink slightly even at font scale 1.0: 88 / 4.05 = 21.7dp
     * instead of the authored 22dp, ~1%. Past 1.0 they hold that rendered size
     * instead of growing. Short labels are untouched at every scale.
     */
    const val MaxLabelWidthDp = 136f - 2f * CornerGlyphGutterDp

    /**
     * Rendered text stops growing past this scale: at 1.6 the label line plus the
     * emoji row still fit the 86dp board with margin ([boardColumnHeightDp] ≈76dp,
     * asserted in PathSignLabelTest); at 2.0 they overflow it.
     */
    const val MaxBoardFontScale = 1.6f

    /**
     * Line height ≈ 1.25× the font size — generous for the medium sans the label
     * uses (~1.17×); neither Text sets an explicit lineHeight.
     */
    const val EstimatedLineHeightRatio = 1.25f

    /** The emoji row's reserved minimum height, mirrored by its heightIn. */
    const val MinEmojiRowDp = 22f

    /**
     * Estimated advance of [c] in em for the semi-bold label font. Slightly
     * generous on purpose: over-estimating shrinks a label a touch early,
     * under-estimating clips a glyph.
     */
    private fun advanceEm(c: Char): Float = when {
        c == ' ' -> 0.30f
        c in "iIjl" -> 0.34f
        c in "ftr" -> 0.44f
        c in "mw" -> 0.90f
        c in "MW" -> 0.98f
        c.isUpperCase() -> 0.75f
        else -> 0.60f
    }

    fun widthEm(label: String): Float = label.sumOf { advanceEm(it).toDouble() }.toFloat()

    /**
     * Font size in sp that keeps [label] on one unclipped line of the board at
     * [fontScale]. Up to [MaxBoardFontScale] and [MaxLabelWidthDp] this is
     * [BaseLabelSp] unchanged; past either cap the *rendered* size is held, i.e.
     * the sp value shrinks by exactly the factor the scale grows.
     */
    fun labelFontSp(label: String, fontScale: Float): Float {
        if (fontScale <= 0f) return BaseLabelSp
        val heightCappedDp = BaseLabelSp * minOf(fontScale, MaxBoardFontScale)
        val em = widthEm(label)
        val renderedDp = if (em > 0f) minOf(heightCappedDp, MaxLabelWidthDp / em) else heightCappedDp
        return renderedDp / fontScale
    }

    /** Emoji size in sp: authored size until [MaxBoardFontScale], then held there. */
    fun emojiFontSp(fontScale: Float): Float {
        if (fontScale <= 0f) return BaseEmojiSp
        return BaseEmojiSp * minOf(fontScale, MaxBoardFontScale) / fontScale
    }

    /** Estimated height in dp of the label line plus the emoji row at [fontScale]. */
    fun boardColumnHeightDp(label: String, fontScale: Float): Float =
        labelFontSp(label, fontScale) * fontScale * EstimatedLineHeightRatio +
            maxOf(emojiFontSp(fontScale) * fontScale * EstimatedLineHeightRatio, MinEmojiRowDp)
}

/**
 * A lesson as a wooden signpost standing on the trail: the grapheme large, three
 * of the lesson's own picture words below it. Signs the child cannot open carry a
 * lock glyph in the corner and keep the emojis as faint silhouettes — enough to
 * make a child curious, not enough to give anything away.
 *
 * @param playable Whether a tap opens the lesson. The caller owns this because the
 * parent's "free order" switch feeds into it — a sign can be [LessonState.Locked]
 * and still playable.
 */
@Composable
fun PathSignNode(
    label: String,
    emojis: List<String>,
    state: LessonState,
    playable: Boolean,
    highlighted: Boolean,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Deliberately not `!playable`: a parent-unlocked sign keeps the dimmed look of the
    // lesson the child has not reached yet, so the path still shows where it stands.
    val dimmed = state == LessonState.Locked || state == LessonState.Planned
    val board = when (state) {
        LessonState.Mastered -> WoodWarm
        LessonState.Available, LessonState.InProgress -> WoodMid
        LessonState.Locked, LessonState.Planned -> WoodDark
    }
    // The post stands behind the board and the nail heads are pressed into it, so
    // both take this board's own shade instead of one global post tone. A single
    // tone would sit above WoodDark and flip the depth on every locked sign.
    val shade = when (state) {
        LessonState.Mastered -> WoodWarmShade
        LessonState.Available, LessonState.InProgress -> WoodMidShade
        LessonState.Locked, LessonState.Planned -> WoodDarkShade
    }
    // The light variants, not LeafGreen/SkyBlue themselves. The ring is drawn
    // inside the board's bounds, so the edge that has to work is the inner one,
    // against dark wood — and LeafGreen/SkyBlue are calibrated against Cream,
    // i.e. they are the dark half of a light pair (2.86:1 and 2.63:1 on WoodMid,
    // a dark accent on dark wood). Turned around:
    //   LeafGreenLight on WoodMid  5.65:1   (Available)
    //   LeafGreenLight on WoodWarm 3.82:1   (Mastered — the lighter board, so
    //                                        the weakest of the three, still
    //                                        clear of the 3:1 bar)
    //   SkyBlueLight   on WoodMid  5.37:1   (InProgress)
    //
    // The outer edge is now the weak one (LeafGreenLight is 1.41:1 against
    // DaySkyMid and 1.42:1 against HillNear), and that is the right way round:
    // the ring's outer edge coincides with the board's own, and the board is
    // 7.9:1 against the sky and 3.4:1 against even the front hill. The sign's
    // silhouette is carried by the board; the ring only has to read as a
    // coloured band on it.
    val ring: Color = when (state) {
        LessonState.Mastered, LessonState.Available -> LeafGreenLight
        LessonState.InProgress -> SkyBlueLight
        // WarmMuted is the palette's dim tone, but it is a dark tone on a dark
        // board: 0.28 (the old MutedText alpha) composites to 1.35:1 on WoodDark
        // and disappears. 0.55 gives 1.88:1, which is where the night ring
        // actually sat (~1.8:1) — inert, still an edge.
        LessonState.Locked, LessonState.Planned -> WarmMuted.copy(alpha = 0.55f)
    }
    // Deliberately still SoftSand, dimmed, and not WarmMuted: the label sits on
    // the board, and WarmMuted tops out at 3.23:1 on WoodDark even at full
    // opacity — a dim *warm-on-warm* grapheme no adult could check. SoftSand at
    // 0.55 composites to 4.94:1 there, clearing the small-text bar and still
    // reading as clearly quieter than the 13.06:1 of an open sign. (The old
    // MutedText at 0.45 was 2.92:1.)
    val labelColor = when (state) {
        LessonState.Mastered, LessonState.Available, LessonState.InProgress -> SoftSand
        LessonState.Locked, LessonState.Planned -> SoftSand.copy(alpha = 0.55f)
    }
    val stateDesc = stringResource(
        when {
            // What TalkBack has to convey is whether the sign opens, so a
            // parent-unlocked one announces itself as available despite its state.
            state == LessonState.Mastered -> R.string.lesson_mastered
            playable -> R.string.lesson_available
            else -> R.string.lesson_locked
        },
    )
    val nodeDesc = stringResource(R.string.path_node)
    // The marker above the board is decorative for TalkBack, so "this is the one" has
    // to reach a screen-reader user here, on the sign itself.
    val currentDesc = if (highlighted) ", ${stringResource(R.string.lesson_current)}" else ""

    Column(
        modifier = modifier
            // A hand-nailed sign is never perfectly straight. Deterministic, so it
            // does not re-tilt on recomposition.
            .graphicsLayer { rotationZ = 3f * PathNoise.signed(index, salt = 5) }
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$nodeDesc $label, $stateDesc$currentDesc" }
            .testTag("path_node_$label"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(PathSignDimens.BoardWidth)
                .height(PathSignDimens.BoardHeight)
                .background(board, BoardShape)
                // A steady ring is a plain border here; the pulsing one is drawn by
                // the overlay below instead, so a pulse frame cannot recompose the
                // sign's whole content. Both draw the same 4dp border on the same
                // shape.
                .then(if (highlighted) Modifier else Modifier.border(RingWidth, ring, BoardShape)),
        ) {
            if (highlighted) {
                val transition = rememberInfiniteTransition(label = "node_pulse")
                // Deliberately not `by`: the State is read inside the graphicsLayer
                // block, which runs in the draw phase. Reading `pulse.value` up here
                // would recompose this Column, its Box, the emoji Row and every glyph
                // in it on every one of the pulse's frames.
                val pulse = transition.animateFloat(
                    initialValue = 0.45f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
                    label = "node_pulse_alpha",
                )
                Box(
                    Modifier
                        .matchParentSize()
                        .graphicsLayer {
                            // Layer alpha multiplies the border's own alpha exactly
                            // the way ring.copy(alpha = ring.alpha * pulse) did, and
                            // the ring is a single non-overlapping stroke, so
                            // ModulateAlpha is enough — no offscreen buffer.
                            alpha = pulse.value
                            compositingStrategy = CompositingStrategy.ModulateAlpha
                        }
                        .border(RingWidth, ring, BoardShape),
                )
            }
            Nail(shade, Modifier.align(Alignment.TopStart).padding(10.dp))
            when {
                state == LessonState.Mastered -> IconStar(
                    tint = StarGold,
                    // IconStar's StarGoldDeep outline exists to lift the glyph over
                    // *light* surfaces; here it sits on WoodWarm, where the fill
                    // alone is already 3.72:1. On a 16dp star the stroke is 1.33dp,
                    // so keeping it would only eat a tenth of the glyph for a
                    // boundary it does not need — the flat silhouette the outline
                    // parameter was added for.
                    outline = StarGold,
                    size = 16.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
                // The child cannot read, and WoodMid against WoodDark is only a
                // 1.41:1 board difference, so "not yet" must not rest on the ring
                // colour alone. Vector lock, not the 🔒 emoji: the emoji renders
                // vendor-gold and collides with the StarGold reward role right
                // next to real stars (§10: UI chrome is vector/ASCII). Decorative
                // for TalkBack — the sign's contentDescription already announces
                // the locked state.
                !playable -> IconLock(
                    tint = SoftSand.copy(alpha = 0.55f),
                    size = 16.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clearAndSetSemantics {},
                )
                else -> Nail(shade, Modifier.align(Alignment.TopEnd).padding(10.dp))
            }

            val fontScale = LocalDensity.current.fontScale
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    // Labels run up to 8 characters ("C y x qu", "Sch ch+"). At a
                    // large font scale a wrapped second line spills past the board's
                    // rounded corner, which is not clipped — hence maxLines=1, and
                    // hence the capped size: uncapped, font scale 2.0 clips the long
                    // labels mid-glyph instead. See PathSignLabel.
                    fontSize = PathSignLabel.labelFontSp(label, fontScale).sp,
                    color = labelColor,
                    maxLines = 1,
                )
                // Reserved height even when empty, so authored and planned signs stay
                // the same size and the path does not jump. A min, not a fixed
                // height: height() caps as well as floors, and 16sp emojis need
                // ~21.5dp at font scale 1.15 and ~24.3dp at 1.3 — they would silently
                // crop, and the three pictures are the whole sign for a child who
                // cannot read the letter above them. The 86dp board absorbs it up to
                // PathSignLabel.MaxBoardFontScale, where the emoji size is held so
                // the row cannot push the column off the board.
                Row(
                    modifier = Modifier
                        .heightIn(min = PathSignLabel.MinEmojiRowDp.dp)
                        // Without this TalkBack reads "mouse tree ant" into the
                        // middle of the state announcement.
                        .clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    emojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = PathSignLabel.emojiFontSp(fontScale).sp,
                            color = Color.Unspecified,
                            modifier = Modifier.graphicsLayer {
                                // 0.18 was set when the whole screen was a night
                                // sky and the eye was adapted to it. The board is
                                // still WoodDark, but it now sits in a bright
                                // landscape, and against that surround a silhouette
                                // at 0.18 on a near-black board reads as an empty
                                // sign rather than a hidden one. 0.35 puts the
                                // pictures back at "there is something there" —
                                // still far from recognisable, which is the point.
                                alpha = if (dimmed) 0.35f else 1f
                            },
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .width(10.dp)
                .height(PathSignDimens.PostHeight)
                .background(shade, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)),
        )
    }
}

/** [shade] is the sign's own board pushed down ~4 L*, so the head reads as a
 *  dent on every board rather than as a bead on the darkest one. */
@Composable
private fun Nail(shade: Color, modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).background(shade, CircleShape))
}
