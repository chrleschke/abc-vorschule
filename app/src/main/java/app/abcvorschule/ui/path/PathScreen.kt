package app.abcvorschule.ui.path

import androidx.compose.foundation.Canvas
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
import app.abcvorschule.BuildConfig
import app.abcvorschule.content.Lesson
import app.abcvorschule.progress.LessonGating
import app.abcvorschule.progress.LessonState
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.shell.ParentGateButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.WarmInk

/**
 * Fibel path: the app's start screen. A dotted trail winds through a sunny
 * landscape from signpost to signpost. Node labels stay minimal (a letter, a
 * syllable, a grapheme) — never an instruction the child would have to read.
 */
@Composable
fun PathScreen(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    unlockAllLessons: Boolean,
    emojisByLessonId: Map<String, List<String>>,
    highlightedLessonId: String?,
    points: Int,
    onOpenLesson: (String) -> Unit,
    onLockedTap: () -> Unit,
    onParentGateUnlocked: () -> Unit,
    onOpenTtsDebug: () -> Unit,
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
                    // Not colorScheme.primary: primary is LeafGreen, the "correct"
                    // colour, and a green star reads as a checkmark. The score is
                    // gold.
                    //
                    // The outline is overridden because this is the one place in the
                    // app where a star is not on Cream. IconStar's StarGoldDeep is
                    // tuned to Cream (3.26:1) and drops to 2.07:1 on DaySkyTop, the
                    // sky directly behind this row — and the StarGold fill itself is
                    // 1.17:1 there, so without a working outline the glyph would be
                    // a gold smudge on blue. WarmInk gives it 6.97:1, the same ink
                    // the number beside it is drawn in.
                    IconStar(tint = StarGold, outline = WarmInk, size = 22.dp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "$points",
                        style = MaterialTheme.typography.titleLarge,
                        color = WarmInk,
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
                    .weight(1f)
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
                    // nodePoints is a key of its own, not just the primitives it was
                    // built from: this remember consumes the list, and the day either
                    // key set gains or loses an entry the two stop lining up and this
                    // one silently serves a stale polyline. 26 equals() beats
                    // rebuilding a 601-point spline.
                    val dots = remember(
                        nodePoints,
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

                    // Walked footprints are warm gold and nearly opaque, the ones
                    // still ahead are a faint warm grey — on a light landscape the
                    // "already done" half has to be the *stronger* mark, where over
                    // the night sky it was the brighter one. Both are decoration:
                    // what the trail says is said again by the signs it connects.
                    Canvas(Modifier.fillMaxSize()) {
                        dots.forEach { dot ->
                            drawCircle(
                                color = if (dot.walked) {
                                    StarGold.copy(alpha = 0.8f)
                                } else {
                                    WarmInk.copy(alpha = 0.18f)
                                },
                                radius = dot.radius,
                                center = Offset(dot.x, dot.y),
                            )
                        }
                    }

                    PathSigns(
                        lessons = lessons,
                        states = states,
                        unlockAllLessons = unlockAllLessons,
                        emojisByLessonId = emojisByLessonId,
                        highlightedLessonId = highlightedLessonId,
                        points = nodePoints,
                        onOpenLesson = onOpenLesson,
                        onLockedTap = onLockedTap,
                    )
                }
            }

            if (BuildConfig.DEBUG) {
                Text(
                    text = "TTS Debug",
                    style = MaterialTheme.typography.labelLarge,
                    // Sits at the very bottom of the screen, i.e. over the front
                    // hill: WarmInk on HillNear is 4.33:1, a hair under the 4.5:1
                    // small-text bar and the darkest the warm palette goes. This is
                    // a debug-build-only affordance for an adult developer, so the
                    // shortfall is accepted rather than papered over with a
                    // background plate.
                    color = WarmInk,
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .clickable(onClick = onOpenTtsDebug)
                        .padding(vertical = 8.dp)
                        .testTag("tts_debug_entry"),
                )
            }
        }
    }
}

@Composable
private fun PathSigns(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    unlockAllLessons: Boolean,
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
        val playable = LessonGating.isPlayable(state, unlockAllLessons)
        PathSignNode(
            label = lesson.nodeLabel,
            emojis = emojisByLessonId[lesson.id].orEmpty(),
            state = state,
            playable = playable,
            highlighted = lesson.id == highlightedLessonId,
            index = index,
            modifier = Modifier.offset(
                x = with(density) { (point.x - halfWidth).toDp() },
                y = with(density) { (point.y - fullHeight).toDp() },
            ),
            onClick = {
                if (playable) onOpenLesson(lesson.id) else onLockedTap()
            },
        )
    }
}
