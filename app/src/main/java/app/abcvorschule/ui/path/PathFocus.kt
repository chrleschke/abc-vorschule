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

    /**
     * The node the marker takes its very first frame on when the path is entered.
     *
     * Normally the sign the child just came back from ([fromIndex]), so the hop to
     * [headIndex] reads as "I finished this one, that one is next". A from-lesson
     * *ahead* of the head — a free-order detour the parent unlocked — must not play
     * that hop: on the first frame the trail would be warm all the way out to the
     * far sign, and the marker would then visibly walk BACKWARDS along it, which
     * reads as progress being taken away (§2: no punishment language, visual or
     * otherwise). The marker starts directly on the head instead; the detour lesson
     * shows its own progress on its own sign.
     */
    fun markerStartIndex(fromIndex: Int?, headIndex: Int): Int =
        if (fromIndex != null && fromIndex in 0 until headIndex) fromIndex else headIndex

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

    /**
     * Scroll offset for *entering* the path while a hop is still pending.
     *
     * [scrollTarget] alone parks the NEW head sign at [ViewportAnchor] — but the
     * hop starts on the OLD sign, one node spacing (~168dp) further up plus the
     * sign-and-marker column ([hopHeadroom], ~166dp) above its node. At anchor
     * 0.42 that start is cut off on any viewport below ~795dp and completely off
     * screen below ~690dp: the hop would play where nobody can see it, and
     * "where the child came from" (§5) is the whole point of the hop. This target
     * additionally keeps the hop's start inside the viewport; the caller then
     * animates from here to [scrollTarget] in step with the hop, which is still
     * the entry scroll — not a yank back of a child who scrolled ahead.
     *
     * `min`, not a replacement: on viewports tall enough that the plain target
     * already shows the hop's start, this IS the plain target, and the follow-up
     * animation degenerates to nothing.
     */
    fun entryScrollTarget(
        fromNodeY: Float,
        headNodeY: Float,
        hopHeadroom: Float,
        viewportHeight: Int,
        maxScroll: Int,
    ): Int {
        if (maxScroll <= 0) return 0
        val headTarget = scrollTarget(headNodeY, viewportHeight, maxScroll)
        val hopStartTop = (minOf(fromNodeY, headNodeY) - hopHeadroom).roundToInt()
        return minOf(headTarget, hopStartTop).coerceIn(0, maxScroll)
    }
}
