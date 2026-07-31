package app.abcvorschule.ui.path

import kotlin.math.hypot

/** One footprint dot on the trail, in path-content pixels. */
data class TrailDot(
    val x: Float,
    val y: Float,
    val radius: Float,
    /** True for dots the child has already walked past — drawn warm, not dimmed. */
    val walked: Boolean,
)

/**
 * The dotted trail between path nodes: a Catmull-Rom spline sampled into a
 * polyline, then covered in evenly spaced footprint dots.
 *
 * Deliberately plain Kotlin — no android.graphics.PathMeasure, which does not
 * exist in JVM unit tests, and no PathEffect dashing, which reads as a technical
 * dashed line rather than as footprints.
 */
object PathTrail {
    const val SamplesPerSegment = 24
    const val DefaultDotSpacing = 18f
    const val DefaultDotRadius = 4f
    private const val RadiusJitterFraction = 0.15f

    /**
     * Catmull-Rom spline through every node. The first and last node are mirrored
     * outwards to give the end segments a tangent, so the trail does not start or
     * stop with a kink.
     */
    fun polyline(
        points: List<PathPoint>,
        samplesPerSegment: Int = SamplesPerSegment,
    ): List<PathPoint> {
        if (points.size < 2) return points
        val out = ArrayList<PathPoint>((points.size - 1) * samplesPerSegment + 1)
        for (i in 0 until points.size - 1) {
            val p0 = points.getOrNull(i - 1) ?: mirror(points[0], points[1])
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points.getOrNull(i + 2)
                ?: mirror(points[points.lastIndex], points[points.lastIndex - 1])
            for (s in 0 until samplesPerSegment) {
                out += interpolate(p0, p1, p2, p3, s.toFloat() / samplesPerSegment)
            }
        }
        out += points.last()
        return out
    }

    /**
     * Footprint dots at a constant arc-length [spacing]. Spacing is measured along
     * the curve, not per sample — otherwise dots would bunch up in the bends.
     *
     * [walkedUpTo] is the index of the last node the child has reached; -1 means
     * none. Dots on earlier segments come back with `walked = true`.
     */
    fun dots(
        polyline: List<PathPoint>,
        walkedUpTo: Int,
        samplesPerSegment: Int = SamplesPerSegment,
        spacing: Float = DefaultDotSpacing,
        radius: Float = DefaultDotRadius,
    ): List<TrailDot> {
        if (polyline.size < 2 || spacing <= 0f) return emptyList()
        val walkedSamples = if (walkedUpTo <= 0) 0 else walkedUpTo * samplesPerSegment
        val out = ArrayList<TrailDot>()
        var carry = 0f
        for (i in 0 until polyline.size - 1) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val segment = hypot(b.x - a.x, b.y - a.y)
            if (segment <= 0f) continue
            var travelled = spacing - carry
            while (travelled <= segment) {
                val t = travelled / segment
                out += TrailDot(
                    x = a.x + (b.x - a.x) * t,
                    y = a.y + (b.y - a.y) * t,
                    radius = radius * (1f + RadiusJitterFraction * PathNoise.signed(out.size, salt = 11)),
                    walked = i < walkedSamples,
                )
                travelled += spacing
            }
            carry = segment - (travelled - spacing)
        }
        return out
    }

    private fun mirror(anchor: PathPoint, other: PathPoint) =
        PathPoint(x = 2 * anchor.x - other.x, y = 2 * anchor.y - other.y)

    private fun interpolate(
        p0: PathPoint,
        p1: PathPoint,
        p2: PathPoint,
        p3: PathPoint,
        t: Float,
    ): PathPoint {
        val t2 = t * t
        val t3 = t2 * t
        fun axis(a: Float, b: Float, c: Float, d: Float) = 0.5f * (
            2f * b +
                (-a + c) * t +
                (2f * a - 5f * b + 4f * c - d) * t2 +
                (-a + 3f * b - 3f * c + d) * t3
            )
        return PathPoint(
            x = axis(p0.x, p1.x, p2.x, p3.x),
            y = axis(p0.y, p1.y, p2.y, p3.y),
        )
    }
}
