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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.R
import app.abcvorschule.progress.LessonState
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
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
    // The ring is drawn inside the board's bounds, so its inner edge is always
    // the board and its outer edge is whatever the sign happens to scroll over.
    // Inner edge: LeafGreen 2.86:1 on WoodMid, 1.93:1 on WoodWarm; SkyBlue
    // 2.63:1 on WoodMid. Outer edge against sky: 2.78:1 and 3.02:1 on DaySkyMid.
    // Worst case is a green ring over a green hill — LeafGreen on HillNear is
    // 1.39:1 and that edge all but vanishes on signs in the bottom quarter of
    // the screen.
    //
    // All of that is accepted rather than tuned away, because the ring is not
    // what tells a child whether a sign opens: the board tone and the lock glyph
    // do, and the board itself is 3.4:1 against even the front hill, so the sign
    // never loses its shape. Recolouring the ring to survive both a dark board
    // and a mid-green hill would mean giving up green-for-open, which is the one
    // association the whole app is built on.
    val ring: Color = when (state) {
        LessonState.Mastered, LessonState.Available -> LeafGreen
        LessonState.InProgress -> SkyBlue
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

    Column(
        modifier = modifier
            // A hand-nailed sign is never perfectly straight. Deterministic, so it
            // does not re-tilt on recomposition.
            .graphicsLayer { rotationZ = 3f * PathNoise.signed(index, salt = 5) }
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$nodeDesc $label, $stateDesc" }
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
                // colour alone. Decorative for TalkBack — the sign's
                // contentDescription already announces the locked state.
                !playable -> Text(
                    text = "🔒",
                    fontSize = 16.sp,
                    color = Color.Unspecified,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clearAndSetSemantics {},
                )
                else -> Nail(shade, Modifier.align(Alignment.TopEnd).padding(10.dp))
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = labelColor,
                    // Labels run up to 8 characters ("C y x qu", "Sch ch+"). At a
                    // large font scale a wrapped second line spills past the board's
                    // rounded corner, which is not clipped.
                    maxLines = 1,
                )
                // Reserved height even when empty, so authored and planned signs stay
                // the same size and the path does not jump. A min, not a fixed
                // height: height() caps as well as floors, and 16sp emojis need
                // ~21.5dp at font scale 1.15 and ~24.3dp at 1.3 — they would silently
                // crop, and the three pictures are the whole sign for a child who
                // cannot read the letter above them. The 86dp board absorbs it.
                Row(
                    modifier = Modifier
                        .heightIn(min = 22.dp)
                        // Without this TalkBack reads "mouse tree ant" into the
                        // middle of the state announcement.
                        .clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    emojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 16.sp,
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
