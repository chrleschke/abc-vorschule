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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

/**
 * A lesson as a wooden signpost standing on the trail: the grapheme large, three
 * of the lesson's own picture words below it. Locked signs keep the emojis as
 * near-invisible silhouettes — enough to make a child curious, not enough to
 * give anything away.
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
    val ringAlpha = if (highlighted) {
        val transition = rememberInfiniteTransition(label = "node_pulse")
        val pulse by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "node_pulse_alpha",
        )
        pulse
    } else {
        1f
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
                .background(board, RoundedCornerShape(14.dp))
                .border(
                    width = 4.dp,
                    color = ring.copy(alpha = ring.alpha * ringAlpha),
                    shape = RoundedCornerShape(14.dp),
                ),
        ) {
            Nail(Modifier.align(Alignment.TopStart).padding(10.dp))
            if (state == LessonState.Mastered) {
                IconStar(
                    tint = SoftGold,
                    size = 16.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            } else {
                Nail(Modifier.align(Alignment.TopEnd).padding(10.dp))
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = labelColor,
                )
                // Fixed height even when empty, so authored and planned signs stay
                // the same size and the path does not jump.
                Row(
                    modifier = Modifier
                        .height(22.dp)
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
    Box(modifier.size(6.dp).background(WoodPost, CircleShape))
}
