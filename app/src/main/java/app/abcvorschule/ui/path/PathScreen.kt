package app.abcvorschule.ui.path

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ScrollState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
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
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.WarmInk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * Fibel path: the app's start screen. A dotted trail winds through a sunny
 * landscape from signpost to signpost. Node labels stay minimal (a letter, a
 * syllable, a grapheme) — never an instruction the child would have to read.
 *
 * @param advanceFromLessonId The lesson the child just came back from. When the
 * highlight has moved on since, the marker starts its hop there instead of appearing
 * on the new sign out of nowhere — that is what makes "I finished this one, that one
 * is next" readable without a word of text. [onAdvanceAnimated] hands the flag back
 * so the hop plays once and not again on the next recomposition.
 */
@Composable
fun PathScreen(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    unlockAllLessons: Boolean,
    emojisByLessonId: Map<String, List<String>>,
    highlightedLessonId: String?,
    advanceFromLessonId: String?,
    points: Int,
    onOpenLesson: (String) -> Unit,
    onLockedTap: () -> Unit,
    onAdvanceAnimated: () -> Unit,
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
                    // PathTrail.dots() maps each dot onto the node chain by dividing
                    // its polyline sample index by samplesPerSegment, which is only
                    // correct if polyline() and dots() were built with the SAME one.
                    // Neither call below passes one, so both fall back to
                    // PathTrail.SamplesPerSegment — do not give one of them a custom
                    // value without giving the other the matching one, or the
                    // warm/cold boundary silently drifts off the actual node.
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
                    ) {
                        PathTrail.dots(
                            polyline = PathTrail.polyline(nodePoints),
                            spacing = dotSpacingPx,
                            radius = dotRadiusPx,
                        )
                    }

                    val headIndex = PathFocus.headIndex(lessons, states, highlightedLessonId)
                    // Starts on the sign the child came back from, so the very first
                    // frame after a finished lesson still shows the old position; the
                    // effect below then walks it to the new one. Deliberately
                    // key-less remember: leaving the path disposes this composable, so
                    // the flag from the session state is the only thing that can carry
                    // "where we came from" across that gap.
                    val markerIndex = remember {
                        Animatable(
                            (PathFocus.indexOf(lessons, advanceFromLessonId) ?: headIndex)
                                .coerceAtLeast(0).toFloat(),
                        )
                    }
                    LaunchedEffect(headIndex) {
                        if (headIndex < 0) return@LaunchedEffect
                        val target = headIndex.toFloat()
                        if (markerIndex.value == target) return@LaunchedEffect
                        delay(PathFocus.HopStartDelayMillis)
                        markerIndex.animateTo(
                            targetValue = target,
                            animationSpec = tween(
                                durationMillis = PathFocus.hopMillis(markerIndex.value, target),
                                easing = FastOutSlowInEasing,
                            ),
                        )
                    }
                    // Consumed as soon as the marker has taken its start position: the
                    // hop above is driven by the Animatable from here on, so clearing
                    // the flag cannot cut it short — and a recomposition (or a return
                    // to the path later on) must not replay it.
                    LaunchedEffect(Unit) {
                        if (advanceFromLessonId != null) onAdvanceAnimated()
                    }
                    AutoScrollToHead(scrollState, nodePoints, headIndex)

                    // Walked footprints are warm gold and nearly opaque, the ones
                    // still ahead are a faint warm grey — on a light landscape the
                    // "already done" half has to be the *stronger* mark, where over
                    // the night sky it was the brighter one. Both are decoration:
                    // what the trail says is said again by the signs it connects.
                    Canvas(Modifier.fillMaxSize()) {
                        // Read inside the draw lambda, so a hop frame repaints the
                        // dots without recomposing the path.
                        val head = markerIndex.value
                        dots.forEach { dot ->
                            drawCircle(
                                color = if (dot.nodeProgress <= head) {
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

                    // Last, so the pin sits over the signs it hops between.
                    if (headIndex >= 0) {
                        PathHereMarker(nodePoints = nodePoints, index = { markerIndex.value })
                    }
                }
            }
        }
    }
}

/**
 * Keeps the marker's sign in view. Without this the current lesson is simply off
 * screen for most of the Fibel — 26 signs are some 4400dp of trail — and both the
 * indicator and its hop would happen where nobody is looking.
 *
 * Only ever scrolls when the marker's node changes (or the layout itself did): a
 * child who scrolled ahead to look at the later signs must not be yanked back.
 */
@Composable
private fun AutoScrollToHead(
    scrollState: ScrollState,
    nodePoints: List<PathPoint>,
    headIndex: Int,
) {
    var lastScrolledHead by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(headIndex, nodePoints) {
        val nodeY = nodePoints.getOrNull(headIndex)?.y ?: return@LaunchedEffect
        // The scroll container does not know its size (nor its maxValue) until the
        // first layout pass, and this effect can run before it — a target computed
        // from a zero viewport would park the sign at the very top.
        val viewport = snapshotFlow { scrollState.viewportSize }.first { it > 0 }
        val target = PathFocus.scrollTarget(nodeY, viewport, scrollState.maxValue)
        // Animated only for an actual move to a new sign, so it reads as following
        // the hop. Entering the path (or re-laying it out) jumps straight there.
        if (lastScrolledHead == null || lastScrolledHead == headIndex) {
            scrollState.scrollTo(target)
        } else {
            scrollState.animateScrollTo(target)
        }
        lastScrolledHead = headIndex
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
