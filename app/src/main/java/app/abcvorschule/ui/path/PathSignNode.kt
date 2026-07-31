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
import app.abcvorschule.progress.LessonGating
import app.abcvorschule.progress.LessonState
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.SoftGold
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky
import app.abcvorschule.ui.theme.WoodDark
import app.abcvorschule.ui.theme.WoodMid
import app.abcvorschule.ui.theme.WoodNail
import app.abcvorschule.ui.theme.WoodPost
import app.abcvorschule.ui.theme.WoodWarm

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
 * of the lesson's own picture words below it. Locked signs carry a lock glyph in
 * the corner and keep the emojis as near-invisible silhouettes — enough to make a
 * child curious, not enough to give anything away.
 */
@Composable
fun PathSignNode(
    label: String,
    emojis: List<String>,
    state: LessonState,
    highlighted: Boolean,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playable = LessonGating.isPlayable(state)
    val board = when (state) {
        LessonState.Mastered -> WoodWarm
        LessonState.Available, LessonState.InProgress -> WoodMid
        LessonState.Locked, LessonState.Planned -> WoodDark
    }
    val ring: Color = when (state) {
        LessonState.Mastered, LessonState.Available -> SoftMint
        LessonState.InProgress -> SoftSky
        LessonState.Locked, LessonState.Planned -> MutedText.copy(alpha = 0.28f)
    }
    val labelColor = when (state) {
        LessonState.Mastered, LessonState.Available, LessonState.InProgress -> SoftSand
        LessonState.Locked, LessonState.Planned -> MutedText.copy(alpha = 0.45f)
    }
    val stateDesc = stringResource(
        when (state) {
            LessonState.Mastered -> R.string.lesson_mastered
            LessonState.Available, LessonState.InProgress -> R.string.lesson_available
            LessonState.Locked, LessonState.Planned -> R.string.lesson_locked
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
            Nail(Modifier.align(Alignment.TopStart).padding(10.dp))
            when {
                state == LessonState.Mastered -> IconStar(
                    tint = SoftGold,
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
                else -> Nail(Modifier.align(Alignment.TopEnd).padding(10.dp))
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
                                alpha = if (playable) 1f else 0.18f
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
                .background(WoodPost, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)),
        )
    }
}

@Composable
private fun Nail(modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).background(WoodNail, CircleShape))
}
