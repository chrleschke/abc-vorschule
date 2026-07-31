package app.abcvorschule.ui.path

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.abcvorschule.content.Lesson
import app.abcvorschule.progress.LessonGating
import app.abcvorschule.progress.LessonState
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.shell.ParentGateButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.SoftSand

/**
 * Fibel path: the app's start screen. A dotted trail winds through a night
 * landscape from signpost to signpost. Node labels stay minimal (a letter, a
 * syllable, a grapheme) — never an instruction the child would have to read.
 */
@Composable
fun PathScreen(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    emojisByLessonId: Map<String, List<String>>,
    highlightedLessonId: String?,
    points: Int,
    onOpenLesson: (String) -> Unit,
    onLockedTap: () -> Unit,
    onParentGateUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(modifier.fillMaxSize()) {
        PathBackground(scrollOffset = { scrollState.value })

        Column(Modifier.fillMaxSize()) {
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
            val horizontalMarginPx = with(density) { PathGeometry.DefaultHorizontalMargin.dp.toPx() }
            val dotSpacingPx = with(density) { PathTrail.DefaultDotSpacing.dp.toPx() }
            val dotRadiusPx = with(density) { PathTrail.DefaultDotRadius.dp.toPx() }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
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
                    // PathGeometry.points and PathTrail.polyline are pure-math but not
                    // cheap (a 26-node path yields a 601-point polyline), so both are
                    // memoized on exactly the primitives they depend on — recomputing
                    // them on every recomposition (e.g. one triggered by an unrelated
                    // state change elsewhere on screen) would be pointless churn.
                    val nodePoints = remember(lessons.size, widthPx, spacingPx, marginPx, horizontalMarginPx) {
                        PathGeometry.points(lessons.size, widthPx, spacingPx, marginPx, horizontalMarginPx)
                    }
                    val walkedUpTo = walkedUpToIndex(lessons, states)
                    // PathTrail.dots() infers where each node falls in the polyline as
                    // walkedUpTo * samplesPerSegment, which is only correct if polyline()
                    // and dots() were built with the SAME samplesPerSegment. Neither call
                    // below passes one, so both fall back to PathTrail.SamplesPerSegment —
                    // do not give one of them a custom value without giving the other the
                    // matching one, or the "walked" boundary silently drifts off the
                    // actual node.
                    val dots = remember(
                        lessons.size,
                        widthPx,
                        spacingPx,
                        marginPx,
                        horizontalMarginPx,
                        walkedUpTo,
                    ) {
                        PathTrail.dots(
                            polyline = PathTrail.polyline(nodePoints),
                            walkedUpTo = walkedUpTo,
                            spacing = dotSpacingPx,
                            radius = dotRadiusPx,
                        )
                    }

                    Canvas(Modifier.fillMaxSize()) {
                        dots.forEach { dot ->
                            drawCircle(
                                color = if (dot.walked) {
                                    SoftSand.copy(alpha = 0.45f)
                                } else {
                                    MutedText.copy(alpha = 0.16f)
                                },
                                radius = dot.radius,
                                center = Offset(dot.x, dot.y),
                            )
                        }
                    }

                    PathSigns(
                        lessons = lessons,
                        states = states,
                        emojisByLessonId = emojisByLessonId,
                        highlightedLessonId = highlightedLessonId,
                        points = nodePoints,
                        onOpenLesson = onOpenLesson,
                        onLockedTap = onLockedTap,
                    )
                }
            }
        }
    }
}

@Composable
private fun PathSigns(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    emojisByLessonId: Map<String, List<String>>,
    highlightedLessonId: String?,
    points: List<PathPoint>,
    onOpenLesson: (String) -> Unit,
    onLockedTap: () -> Unit,
) {
    val density = LocalDensity.current
    val halfWidth = with(density) { (PathSignDimens.BoardWidth / 2).toPx() }
    // The geometry point is where the post meets the ground, so the sign is drawn
    // fully above it and the trail passes below the board instead of through it.
    val fullHeight = with(density) { PathSignDimens.TotalHeight.toPx() }

    lessons.forEachIndexed { index, lesson ->
        val point = points.getOrNull(index) ?: return@forEachIndexed
        val state = states[lesson.id] ?: LessonState.Locked
        PathSignNode(
            label = lesson.nodeLabel,
            emojis = emojisByLessonId[lesson.id].orEmpty(),
            state = state,
            highlighted = lesson.id == highlightedLessonId,
            index = index,
            modifier = Modifier.offset(
                x = with(density) { (point.x - halfWidth).toDp() },
                y = with(density) { (point.y - fullHeight).toDp() },
            ),
            onClick = {
                if (LessonGating.isPlayable(state)) onOpenLesson(lesson.id) else onLockedTap()
            },
        )
    }
}
