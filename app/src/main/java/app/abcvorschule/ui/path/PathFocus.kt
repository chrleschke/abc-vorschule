package app.abcvorschule.ui.path

import app.abcvorschule.content.Lesson
import app.abcvorschule.progress.LessonState
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Where the path's attention sits: which node the "you are here" marker stands on,
 * how long it takes to hop to the next one, and how far the trail has to scroll to
 * keep it in view.
 *
 * Pure math on purpose — the whole focus logic is JVM-unit-testable, and nothing
 * here may depend on Compose.
 */
internal object PathFocus {
    /** Millis per node hopped, clamped so a multi-node jump stays a hop, not a trek. */
    private const val HopMillisPerNode = 750f
    const val MinHopMillis = 600
    const val MaxHopMillis = 1500

    /**
     * Beat before the hop starts. The point of the animation is "I came from there,
     * that one is next", and that needs the eye to land on the old sign first — a pin
     * that has already left by the time the path is on screen shows only the second
     * half of the sentence.
     */
    const val HopStartDelayMillis = 400L

    /**
     * Where in the viewport the focused node is parked, as a fraction of its height.
     * Above the middle, because the trail runs downwards: the sign the child is
     * walking *towards* is always the one below, and it has to come into view with
     * the marker.
     */
    private const val ViewportAnchor = 0.42f

    /**
     * The node the marker stands on: the highlighted lesson
     * ([app.abcvorschule.progress.LessonGating.nextPlayable]) when there is one,
     * otherwise the furthest lesson actually reached.
     *
     * The same index doubles as the trail's warm/cold boundary, which is why it is
     * not [walkedUpToIndex] itself: mastering a lesson moves the highlight to the
     * next sign, and the trail lights up along with it. That stretch — the dots
     * between the finished sign and the next one — is exactly "where you came from
     * and what's next".
     *
     * Under free order this also means the trail follows the Fibel order rather than
     * an out-of-order detour: a lesson finished far ahead shows its own progress on
     * its own sign (warm board, star) without claiming the child walked the whole
     * way there.
     */
    fun headIndex(
        lessons: List<Lesson>,
        states: Map<String, LessonState>,
        highlightedLessonId: String?,
    ): Int = indexOf(lessons, highlightedLessonId) ?: walkedUpToIndex(lessons, states)

    fun indexOf(lessons: List<Lesson>, lessonId: String?): Int? =
        lessonId?.let { id -> lessons.indexOfFirst { it.id == id }.takeIf { it >= 0 } }

    fun hopMillis(from: Float, to: Float): Int =
        (abs(to - from) * HopMillisPerNode).roundToInt().coerceIn(MinHopMillis, MaxHopMillis)

    /**
     * Scroll offset that parks [nodeY] at [ViewportAnchor] of the viewport, clamped
     * to what the content actually allows — the first and last nodes simply sit
     * wherever the ends of the trail put them.
     */
    fun scrollTarget(nodeY: Float, viewportHeight: Int, maxScroll: Int): Int {
        if (maxScroll <= 0) return 0
        return (nodeY - viewportHeight * ViewportAnchor).roundToInt().coerceIn(0, maxScroll)
    }
}
