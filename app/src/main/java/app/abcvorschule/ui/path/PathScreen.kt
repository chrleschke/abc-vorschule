package app.abcvorschule.ui.path

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.content.Lesson
import app.abcvorschule.progress.LessonGating
import app.abcvorschule.progress.LessonState
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.shell.ParentGateButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.NightPanel
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky

private val NodeSize = 92.dp

/**
 * Fibel path: the app's start screen. Node labels stay minimal (a letter, a
 * syllable, a grapheme) — never an instruction the child would have to read.
 */
@Composable
fun PathScreen(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    highlightedLessonId: String?,
    points: Int,
    onOpenLesson: (String) -> Unit,
    onLockedTap: () -> Unit,
    onParentGateUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AbcDimens.screenHorizontal, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ParentGateButton(onUnlocked = onParentGateUnlocked)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconStar(tint = MaterialTheme.colorScheme.primary, size = 22.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$points",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(48.dp))
        }

        val density = LocalDensity.current
        val spacingPx = with(density) { PathGeometry.DefaultSpacing.dp.toPx() }
        val marginPx = with(density) { PathGeometry.DefaultMargin.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("path_scroll"),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        with(density) {
                            PathGeometry.contentHeight(lessons.size, spacingPx, marginPx).toDp()
                        },
                    ),
            ) {
                val widthPx = with(density) { maxWidth.toPx() }
                val pts = PathGeometry.points(lessons.size, widthPx, spacingPx, marginPx)

                Canvas(Modifier.fillMaxSize()) {
                    pts.zipWithNext { a, b ->
                        drawLine(
                            color = MutedText.copy(alpha = 0.22f),
                            start = Offset(a.x, a.y),
                            end = Offset(b.x, b.y),
                            strokeWidth = 10f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                PathNodes(
                    lessons = lessons,
                    states = states,
                    highlightedLessonId = highlightedLessonId,
                    points = pts,
                    onOpenLesson = onOpenLesson,
                    onLockedTap = onLockedTap,
                )
            }
        }
    }
}

@Composable
private fun PathNodes(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    highlightedLessonId: String?,
    points: List<PathPoint>,
    onOpenLesson: (String) -> Unit,
    onLockedTap: () -> Unit,
) {
    val density = LocalDensity.current
    val nodeHalf = with(density) { (NodeSize / 2).toPx() }
    lessons.forEachIndexed { index, lesson ->
        val point = points.getOrNull(index) ?: return@forEachIndexed
        val state = states[lesson.id] ?: LessonState.Locked
        PathNode(
            label = lesson.nodeLabel,
            state = state,
            highlighted = lesson.id == highlightedLessonId,
            modifier = Modifier.offset(
                x = with(density) { (point.x - nodeHalf).toDp() },
                y = with(density) { (point.y - nodeHalf).toDp() },
            ),
            onClick = {
                if (LessonGating.isPlayable(state)) onOpenLesson(lesson.id) else onLockedTap()
            },
        )
    }
}

@Composable
private fun PathNode(
    label: String,
    state: LessonState,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playable = LessonGating.isPlayable(state)
    val fill = when (state) {
        LessonState.Mastered -> SoftMint
        LessonState.Available, LessonState.InProgress -> NightElevated
        LessonState.Locked, LessonState.Planned -> NightPanel
    }
    val ring: Color = when (state) {
        LessonState.Mastered -> SoftMint
        LessonState.Available -> SoftMint
        LessonState.InProgress -> SoftSky
        LessonState.Locked, LessonState.Planned -> MutedText.copy(alpha = 0.28f)
    }
    val labelColor = when (state) {
        LessonState.Mastered -> NightInk
        LessonState.Available, LessonState.InProgress -> SoftSand
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

    Box(
        modifier = modifier
            .size(NodeSize)
            .background(fill, CircleShape)
            .border(width = 4.dp, color = ring.copy(alpha = ring.alpha * ringAlpha), shape = CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$nodeDesc $label, $stateDesc" }
            .testTag("path_node_$label"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = labelColor,
                modifier = if (playable) Modifier else Modifier.alpha(0.75f),
            )
            if (state == LessonState.Mastered) {
                IconStar(tint = NightInk, size = 18.dp)
            }
        }
    }
}
