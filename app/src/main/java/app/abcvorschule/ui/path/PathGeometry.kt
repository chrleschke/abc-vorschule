package app.abcvorschule.ui.path

import kotlin.math.PI
import kotlin.math.sin

/** Node center in path-content pixels, y growing downwards. */
data class PathPoint(val x: Float, val y: Float)

/**
 * Deterministic pseudo-noise. The path must look hand-drawn but never move
 * between two recompositions, so nothing here uses Random or any state — the
 * same (index, salt) always yields the same value in (-1f, 1f).
 *
 * The salt is what keeps two effects from moving in lockstep, so it is allocated
 * centrally here. Taken, across three files:
 *
 * - 3 — horizontal node jitter ([PathGeometry.points])
 * - 5 — signpost tilt (`PathSignNode`)
 * - 7 — vertical node jitter ([PathGeometry.yOffsets])
 * - 11 — trail dot radius (`PathTrail.dots`)
 *
 * Pick an unused one for anything new: reusing a salt correlates two effects that
 * are supposed to look independent, and nothing crashes to tell you.
 */
internal object PathNoise {
    fun signed(index: Int, salt: Int): Float {
        var h = index * 374761393 + salt * 668265263
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return (h % 1000) / 1000f
    }
}

/**
 * Winding trail: nodes stack top-down while x swings across the screen. The
 * period is deliberately not a whole number of nodes and both axes carry a small
 * index-derived offset, so the curve never rasters onto a handful of exact
 * positions the way a plain sine does. Pure math so the layout is unit-testable
 * without Compose.
 */
object PathGeometry {
    const val DefaultSpacing = 168f

    /**
     * Vertical margin: the gap before the first node and after the last one.
     * Also fed to [yOffsets]/[contentHeight], so it can't be shrunk to widen the
     * swing — it has a floor of its own (a sign's height, ~116dp) or the first
     * sign clips off the top of the scroll content. See [DefaultHorizontalMargin]
     * for the horizontal inset, which has no such constraint.
     */
    const val DefaultMargin = 132f

    /**
     * Horizontal inset: how far the swing's outer edge stays from each screen
     * edge. Deliberately separate from [DefaultMargin] — on a 360dp phone,
     * reusing the 132dp vertical margin left only a 96dp-wide swing (48dp
     * amplitude), not enough to clear a 136dp-wide signpost, so the trail hid
     * behind the boards for roughly half of every step. 84dp gives a 192dp-wide
     * swing (96dp amplitude) at that width, clearing the sign on both sides.
     */
    const val DefaultHorizontalMargin = 84f

    /** Nodes per full left-right-left swing. Non-integer on purpose. */
    private const val Period = 3.7

    private const val XJitterFraction = 0.06f
    private const val YJitterFraction = 0.08f

    /**
     * y of every node. Shared by [points] and [contentHeight] — with variable
     * spacing the two would otherwise drift apart and the scroll area would cut
     * the last nodes off.
     */
    private fun yOffsets(count: Int, spacing: Float, margin: Float): List<Float> {
        var y = margin
        return (0 until count).map { index ->
            if (index > 0) y += spacing * (1f + YJitterFraction * PathNoise.signed(index, salt = 7))
            y
        }
    }

    fun points(
        count: Int,
        width: Float,
        spacing: Float = DefaultSpacing,
        margin: Float = DefaultMargin,
        horizontalMargin: Float = DefaultHorizontalMargin,
    ): List<PathPoint> {
        if (count <= 0) return emptyList()
        val center = width / 2f
        // Amplitude comes from horizontalMargin, not margin: margin also sets the
        // vertical gap via yOffsets/contentHeight and has a floor it can't drop
        // below, so it can't be shrunk just to widen the swing. horizontalMargin
        // carries no such constraint.
        val amplitude = (center - horizontalMargin).coerceAtLeast(0f)
        val ys = yOffsets(count, spacing, margin)
        return (0 until count).map { index ->
            val swing = sin(index * 2.0 * PI / Period).toFloat()
            val jitter = XJitterFraction * PathNoise.signed(index, salt = 3)
            // Jitter is scaled by amplitude, not added in pixels: on a screen too
            // narrow to swing (amplitude 0) every node must sit dead center.
            PathPoint(
                x = center + amplitude * (swing + jitter).coerceIn(-1f, 1f),
                y = ys[index],
            )
        }
    }

    fun contentHeight(
        count: Int,
        spacing: Float = DefaultSpacing,
        margin: Float = DefaultMargin,
    ): Float = if (count <= 0) 0f else yOffsets(count, spacing, margin).last() + margin
}
