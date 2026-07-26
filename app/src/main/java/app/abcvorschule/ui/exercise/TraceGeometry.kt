package app.abcvorschule.ui.exercise

import app.abcvorschule.content.GlyphStroke
import kotlin.math.hypot

/** A point in glyph-box pixels, y growing downwards. */
data class TracePoint(val x: Float, val y: Float)

/** Pure polyline maths for the letter road: scaling, star placement, corridor distance. */
object TraceGeometry {
    fun toPixels(
        strokes: List<GlyphStroke>,
        boxSize: Float,
        origin: TracePoint,
    ): List<List<TracePoint>> = strokes.map { stroke ->
        stroke.points.map { p ->
            TracePoint(
                x = origin.x + (p.getOrElse(0) { 0.0 }).toFloat() * boxSize,
                y = origin.y + (p.getOrElse(1) { 0.0 }).toFloat() * boxSize,
            )
        }
    }

    fun polylineLength(points: List<TracePoint>): Float =
        points.zipWithNext().fold(0f) { acc, (a, b) -> acc + hypot(b.x - a.x, b.y - a.y) }

    fun pointAtFraction(points: List<TracePoint>, fraction: Float): TracePoint {
        if (points.isEmpty()) return TracePoint(0f, 0f)
        if (points.size == 1) return points[0]
        val total = polylineLength(points)
        if (total <= 0f) return points[0]
        val target = (fraction.coerceIn(0f, 1f)) * total
        var walked = 0f
        points.zipWithNext().forEach { (a, b) ->
            val segment = hypot(b.x - a.x, b.y - a.y)
            if (walked + segment >= target) {
                val t = if (segment <= 0f) 0f else (target - walked) / segment
                return TracePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
            walked += segment
        }
        return points.last()
    }

    /** [count] stars spread over the stroke; the last one always sits at the stroke end. */
    fun starPositions(points: List<TracePoint>, count: Int): List<TracePoint> {
        if (count < 1) return listOf(points.lastOrNull() ?: TracePoint(0f, 0f))
        return (1..count).map { i -> pointAtFraction(points, i.toFloat() / count) }
    }

    fun distanceToSegment(p: TracePoint, a: TracePoint, b: TracePoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    fun distanceToPolyline(p: TracePoint, points: List<TracePoint>): Float = when {
        points.isEmpty() -> Float.MAX_VALUE
        points.size == 1 -> hypot(p.x - points[0].x, p.y - points[0].y)
        else -> points.zipWithNext().minOf { (a, b) -> distanceToSegment(p, a, b) }
    }
}

/** Which stroke and which star of that stroke the child is on. */
data class TraceState(val strokeIndex: Int = 0, val starIndex: Int = 0)

data class TraceUpdate(
    val state: TraceState,
    val collectedStar: Boolean,
    val offCorridor: Boolean,
    val glyphDone: Boolean,
)

/**
 * Stroke-order enforcement: the finger must stay inside a corridor around the
 * current stroke, and only the *next* star counts — so the glyph cannot be
 * shortcut and the writing direction is actually practiced.
 */
object TraceProgress {
    const val StarsPerStroke = 4

    /** Corridor half-width as a fraction of the glyph box. */
    const val CorridorFraction = 0.16f

    /** Star pick-up radius as a fraction of the glyph box. */
    const val StarHitFraction = 0.12f

    fun update(
        state: TraceState,
        finger: TracePoint,
        strokes: List<List<TracePoint>>,
        stars: List<List<TracePoint>>,
        boxSize: Float,
    ): TraceUpdate {
        if (state.strokeIndex >= strokes.size) {
            return TraceUpdate(state, collectedStar = false, offCorridor = false, glyphDone = true)
        }
        val stroke = strokes[state.strokeIndex]
        val corridor = boxSize * CorridorFraction
        if (TraceGeometry.distanceToPolyline(finger, stroke) > corridor) {
            return TraceUpdate(state, collectedStar = false, offCorridor = true, glyphDone = false)
        }
        val target = stars.getOrNull(state.strokeIndex)?.getOrNull(state.starIndex)
            ?: return TraceUpdate(state, collectedStar = false, offCorridor = false, glyphDone = false)
        val hit = TraceGeometry.distanceToPolyline(finger, listOf(target)) <= boxSize * StarHitFraction
        if (!hit) {
            return TraceUpdate(state, collectedStar = false, offCorridor = false, glyphDone = false)
        }
        val lastStarOfStroke = state.starIndex + 1 >= (stars[state.strokeIndex].size)
        val next = if (lastStarOfStroke) {
            TraceState(state.strokeIndex + 1, 0)
        } else {
            TraceState(state.strokeIndex, state.starIndex + 1)
        }
        return TraceUpdate(
            state = next,
            collectedStar = true,
            offCorridor = false,
            glyphDone = next.strokeIndex >= strokes.size,
        )
    }
}
