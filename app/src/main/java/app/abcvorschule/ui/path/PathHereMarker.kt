package app.abcvorschule.ui.path

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.SunCoral
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.sin

/** Marker dimensions. Public because [PathGeometry.DefaultMargin] has to leave room. */
object PathMarkerDimens {
    val Width = 30.dp

    /** Head is [Width] across, the rest is the pin's point. */
    val Height = 40.dp

    /** Gap between the marker's tip and the top edge of the board it points at. */
    val TipGap = 6.dp

    /** How far the idle bob lifts the marker. */
    val BobHeight = 4.dp

    /** Apex of the hop arc, above the straight line between two signs. */
    val HopHeight = 46.dp

    /**
     * Space the marker needs above a sign. [PathGeometry.DefaultMargin] is the
     * distance from the top of the scroll content to the first node, and a sign is
     * drawn entirely above its node, so the first marker is clipped away unless the
     * margin covers a sign plus this.
     */
    val Headroom = Height + TipGap + BobHeight
}

/**
 * Position of the marker's tip for a fractional node index. Pure math, so the hop
 * can be unit-tested without Compose.
 */
internal object PathMarkerGeometry {
    /**
     * @param index Node index, fractional while hopping: 2.5 is mid-hop between
     * node 2 and node 3. Values outside the node list are clamped, so a marker never
     * flies off the trail.
     * @param lift Distance from the node (where the post meets the ground) up to the
     * marker's tip — a sign's full height plus the tip gap.
     * @param hopHeight Extra lift at the apex of the hop.
     * @return The tip in path-content pixels, or null when there are no nodes.
     */
    fun tipFor(points: List<PathPoint>, index: Float, lift: Float, hopHeight: Float): PathPoint? {
        if (points.isEmpty()) return null
        val clamped = index.coerceIn(0f, points.lastIndex.toFloat())
        val from = points[floor(clamped).toInt()]
        val to = points[ceil(clamped).toInt()]
        val t = clamped - floor(clamped)
        // sin(pi * t) is zero at both ends: a marker standing on a sign never floats,
        // and the arc peaks exactly halfway between two signs.
        val arc = hopHeight * sin(PI * t).toFloat()
        return PathPoint(
            x = from.x + (to.x - from.x) * t,
            y = from.y + (to.y - from.y) * t - lift - arc,
        )
    }
}

/**
 * The "you are here" marker: a map pin standing above the current lesson's sign,
 * bobbing gently so the eye finds it on a screenful of signposts. When the child
 * finishes a lesson it hops over to the next sign while the trail behind it warms
 * up — the two together say where the child came from and what is next.
 *
 * @param index The node the pin stands on, fractional during the hop. A lambda, not
 * a Float: it is read inside [graphicsLayer], i.e. in the draw phase, so a hop frame
 * moves the pin without recomposing anything.
 */
@Composable
internal fun PathHereMarker(
    nodePoints: List<PathPoint>,
    index: () -> Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val liftPx = with(density) { (PathSignDimens.TotalHeight + PathMarkerDimens.TipGap).toPx() }
    val hopPx = with(density) { PathMarkerDimens.HopHeight.toPx() }
    val bobPx = with(density) { PathMarkerDimens.BobHeight.toPx() }
    val widthPx = with(density) { PathMarkerDimens.Width.toPx() }
    val heightPx = with(density) { PathMarkerDimens.Height.toPx() }

    val transition = rememberInfiniteTransition(label = "here_marker")
    // Same reason as the sign's pulse: not `by`, because the value is read in the
    // draw phase below and reading it here would recompose the marker every frame.
    val bob = transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1100, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "here_marker_bob",
    )

    Box(
        modifier
            .size(PathMarkerDimens.Width, PathMarkerDimens.Height)
            .graphicsLayer {
                val tip = PathMarkerGeometry.tipFor(nodePoints, index(), liftPx, hopPx)
                if (tip == null) {
                    alpha = 0f
                } else {
                    alpha = 1f
                    translationX = tip.x - widthPx / 2f
                    translationY = tip.y - heightPx - bob.value * bobPx
                }
            }
            // Decorative for TalkBack: which sign is current is announced by the sign
            // itself (R.string.lesson_current), where a screen-reader user looks for it.
            .clearAndSetSemantics {}
            .testTag("path_here_marker"),
    ) {
        Canvas(Modifier.matchParentSize()) {
            val radius = size.width / 2f
            val outline = size.width * 0.1f
            // Fill and outline are both load-bearing, in different halves of the
            // landscape: SunCoral carries the silhouette against the light sky
            // (~3.0:1), Cream against the green hills (~4.1:1 on HillNear). Either
            // one alone would dissolve in the other half.
            val pin = Path().apply {
                // Head: 270° of circle, from the lower-left tangent clockwise over
                // the top to the lower-right one. Then down both flanks to the tip,
                // and close() draws the left flank back to the start.
                arcTo(
                    rect = Rect(
                        left = outline / 2f,
                        top = outline / 2f,
                        right = size.width - outline / 2f,
                        bottom = 2f * radius - outline / 2f,
                    ),
                    startAngleDegrees = 135f,
                    sweepAngleDegrees = 270f,
                    forceMoveTo = true,
                )
                lineTo(radius, size.height - outline / 2f)
                close()
            }
            drawPath(pin, SunCoral)
            drawPath(
                pin,
                Cream,
                style = Stroke(width = outline, join = StrokeJoin.Round, cap = StrokeCap.Round),
            )
            drawCircle(Cream, radius = radius * 0.32f, center = Offset(radius, radius))
        }
    }
}
