package app.abcvorschule.ui.exercise

import app.abcvorschule.content.GlyphStroke
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** A point in glyph-box pixels, y growing downwards. */
data class TracePoint(val x: Float, val y: Float)

/** Pure polyline maths for the letter road: scaling, star placement, corridor distance. */
object TraceGeometry {
    /**
     * Corners gentler than this (degrees of direction change) stay as authored — they are
     * near-straight and must not be bowed by arc densification.
     */
    const val RefineMinTurnDegrees = 18f

    /**
     * Corners sharper than this stay as authored — M/W/N peaks and K junctions are
     * pedagogical letterforms, not bowls.
     */
    const val RefineMaxTurnDegrees = 72f

    /**
     * A corner is densified only when at least one adjacent chord is longer than this
     * fraction of the glyph box (coarse polygonal bowls).
     */
    const val RefineMinChord = 0.19f

    /**
     * And neither adjacent chord may exceed this — otherwise a long straight stem
     * (J, U uprights) would be bent into a circular arc through the junction.
     */
    const val RefineMaxChord = 0.45f

    /** Target chord length when sampling a densified circular arc, as a box fraction. */
    const val RefineTargetChord = 0.09f

    fun toPixels(
        strokes: List<GlyphStroke>,
        boxSize: Float,
        origin: TracePoint,
        /**
         * Vertical squeeze toward y=0.5 in unit space. Multi-grapheme glyphs use a
         * value below 1 so Au/Sch do not stretch to the full square height.
         */
        heightScale: Float = 1f,
    ): List<List<TracePoint>> = strokes.map { stroke ->
        val normalized = stroke.points.map { p ->
            TracePoint(
                x = (p.getOrElse(0) { 0.0 }).toFloat(),
                y = (p.getOrElse(1) { 0.0 }).toFloat(),
            )
        }
        // Densify coarse bowls in unit space so chord thresholds stay independent of
        // the on-screen glyph box; hit-testing and drawing then share the same road.
        refineStroke(normalized).map { p ->
            val yNorm = 0.5f + (p.y - 0.5f) * heightScale
            TracePoint(
                x = origin.x + p.x * boxSize,
                y = origin.y + yNorm * boxSize,
            )
        }
    }

    /**
     * Replace coarse polygonal corners with circular-arc samples. Straight letters and
     * already-dense curves (O, refined U) are left alone; only corners whose turn and
     * chord lengths look like an undersampled bowl are touched.
     */
    fun refineStroke(points: List<TracePoint>): List<TracePoint> {
        if (points.size < 3) return points
        val out = ArrayList<TracePoint>(points.size * 2)
        out.add(points.first())
        var i = 1
        while (i < points.size - 1) {
            val a = points[i - 1]
            val b = points[i]
            val c = points[i + 1]
            val ab = hypot(b.x - a.x, b.y - a.y)
            val bc = hypot(c.x - b.x, c.y - b.y)
            val turn = turnDegrees(a, b, c)
            val needsRefine = turn in RefineMinTurnDegrees..RefineMaxTurnDegrees &&
                (ab > RefineMinChord || bc > RefineMinChord) &&
                ab <= RefineMaxChord &&
                bc <= RefineMaxChord
            if (needsRefine) {
                val samples = ((ab + bc) / RefineTargetChord).toInt()
                    .coerceIn(3, 6)
                val arc = circularArcThrough(a, b, c, samples)
                if (hypot(out.last().x - a.x, out.last().y - a.y) > 1e-3f) {
                    out.add(a)
                }
                // Drop arc endpoints (exact a/c); keep only interior samples, then the
                // authored corner end so joins between strokes stay bitwise identical.
                for (p in arc.drop(1).dropLast(1)) out.add(p)
                out.add(c)
                // Skip the next vertex — it is the arc's end and must not be a second center.
                i += 2
            } else {
                out.add(b)
                i += 1
            }
        }
        val last = points.last()
        if (out.last() != last) {
            // Replace a near-duplicate float reconstruction with the authored endpoint.
            if (hypot(out.last().x - last.x, out.last().y - last.y) <= 1e-3f) {
                out[out.lastIndex] = last
            } else {
                out.add(last)
            }
        }
        return dedupeConsecutive(out)
    }

    private fun turnDegrees(a: TracePoint, b: TracePoint, c: TracePoint): Float {
        val ax = b.x - a.x
        val ay = b.y - a.y
        val bx = c.x - b.x
        val by = c.y - b.y
        val la = hypot(ax, ay)
        val lb = hypot(bx, by)
        if (la <= 1e-6f || lb <= 1e-6f) return 0f
        val cos = ((ax * bx + ay * by) / (la * lb)).coerceIn(-1f, 1f)
        return (acos(cos) * 180f / PI).toFloat()
    }

    /**
     * Sample the unique circle through [a], [b], [c] along the arc from [a] to [c] that
     * passes near [b]. Endpoints are preserved so compound glyphs keep their joins.
     */
    private fun circularArcThrough(
        a: TracePoint,
        b: TracePoint,
        c: TracePoint,
        samples: Int,
    ): List<TracePoint> {
        val det = 2f * (a.x * (b.y - c.y) + b.x * (c.y - a.y) + c.x * (a.y - b.y))
        if (kotlin.math.abs(det) < 1e-6f) return listOf(a, b, c)
        val a2 = a.x * a.x + a.y * a.y
        val b2 = b.x * b.x + b.y * b.y
        val c2 = c.x * c.x + c.y * c.y
        val cx = (a2 * (b.y - c.y) + b2 * (c.y - a.y) + c2 * (a.y - b.y)) / det
        val cy = (a2 * (c.x - b.x) + b2 * (a.x - c.x) + c2 * (b.x - a.x)) / det
        val radius = hypot(a.x - cx, a.y - cy)
        fun angle(p: TracePoint) = atan2(p.y - cy, p.x - cx)
        val start = angle(a)
        val mid = angle(b)
        val endTarget = angle(c)
        var best: List<Float>? = null
        var bestScore = Float.MAX_VALUE
        for (direction in intArrayOf(1, -1)) {
            var end = endTarget
            if (direction > 0) {
                while (end < start) end += (2f * PI).toFloat()
            } else {
                while (end > start) end -= (2f * PI).toFloat()
            }
            val seq = (0..samples).map { i -> start + (end - start) * i / samples }
            val err = seq.minOf { abs(normalizeAngle(it - mid)) }
            val score = err + 0.001f * abs(end - start)
            if (score < bestScore) {
                bestScore = score
                best = seq
            }
        }
        return best!!.map { ang ->
            TracePoint(
                x = cx + radius * cos(ang),
                y = cy + radius * sin(ang),
            )
        }
    }

    private fun normalizeAngle(angle: Float): Float {
        var a = angle
        val tau = (2f * PI).toFloat()
        while (a <= -PI) a += tau
        while (a > PI) a -= tau
        return a
    }

    private fun dedupeConsecutive(points: List<TracePoint>): List<TracePoint> {
        if (points.isEmpty()) return points
        val out = ArrayList<TracePoint>(points.size)
        out.add(points.first())
        for (p in points.drop(1)) {
            if (hypot(p.x - out.last().x, p.y - out.last().y) > 1e-4f) out.add(p)
        }
        return out
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

    /**
     * Outline of a [spikes]-pointed star, alternating outer and inner vertices and
     * starting at the top point. The prompt promises stars, so the collectibles are
     * drawn as stars — this is the geometry the canvas turns into a path.
     */
    fun starPoints(
        center: TracePoint,
        outerRadius: Float,
        innerRadius: Float,
        spikes: Int = 5,
    ): List<TracePoint> {
        if (spikes < 2) return emptyList()
        val step = PI / spikes
        return (0 until spikes * 2).map { i ->
            val radius = if (i % 2 == 0) outerRadius else innerRadius
            // -PI/2 puts the first spike straight up rather than out to the right.
            val angle = -PI / 2 + i * step
            TracePoint(
                x = center.x + (cos(angle) * radius).toFloat(),
                y = center.y + (sin(angle) * radius).toFloat(),
            )
        }
    }

    /**
     * Painter's order for a glyph's strokes: everything else first, the stroke the child
     * is currently on last. The road bands are wide enough to overlap where strokes meet,
     * so a later stroke drawn on top would cover the active one's lane and stars — which
     * is exactly the crossing point the child has to aim at. When the active stroke is
     * finished, the next one takes the top slot.
     */
    fun strokeDrawOrder(strokeCount: Int, activeIndex: Int): List<Int> {
        if (strokeCount <= 0) return emptyList()
        val indices = (0 until strokeCount).toList()
        // A finished glyph has no active stroke left; plain order is fine then.
        if (activeIndex !in indices) return indices
        return indices.filterNot { it == activeIndex } + activeIndex
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

    /**
     * How far along [points] the nearest road position to [p] lies, measured from the
     * stroke start. This is "where on the letter is the finger", the counterpart to
     * [distanceToPolyline]'s "how far off the letter is it".
     *
     * Ties go to the earlier segment, so a closed loop (letter-o) reports the finger at
     * the start rather than at the end when it sits on the seam — the permissive answer,
     * which keeps the gate in [TraceProgress] from blocking a loop that just began.
     */
    fun arcLengthAt(points: List<TracePoint>, p: TracePoint): Float {
        if (points.size < 2) return 0f
        var walked = 0f
        var best = Float.MAX_VALUE
        var bestArc = 0f
        points.zipWithNext().forEach { (a, b) ->
            val dx = b.x - a.x
            val dy = b.y - a.y
            val segment = hypot(dx, dy)
            val lengthSquared = dx * dx + dy * dy
            val t = if (lengthSquared <= 0f) {
                0f
            } else {
                (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
            }
            val distance = hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
            if (distance < best) {
                best = distance
                bestArc = walked + t * segment
            }
            walked += segment
        }
        return bestArc
    }
}

/** Which stroke and which star of that stroke the child is on. */
data class TraceState(val strokeIndex: Int = 0, val starIndex: Int = 0)

data class TraceUpdate(
    val state: TraceState,
    val collectedStar: Boolean,
    val offCorridor: Boolean,
    val glyphDone: Boolean,
    /**
     * The finger is on the road but further along the stroke than the star it is meant
     * to collect next — so it marks no progress and the vehicle must stay put. Not the
     * same as [offCorridor]: the child has not left the letter, so there is no nudge.
     */
    val ahead: Boolean = false,
)

/**
 * Stroke-order enforcement: the finger must stay inside a corridor around the
 * current stroke, may not run ahead of the next star along it, and only the *next*
 * star counts — so the glyph cannot be shortcut and the writing direction is
 * actually practiced.
 */
object TraceProgress {
    /** Even the shortest tick (an umlaut dot) is worth exactly one star. */
    const val MinStars = 1

    /** Upper bound so a long closed loop stays a game, not a chore. */
    const val MaxStars = 10

    /**
     * Nominal gap between two stars, as a fraction of the glyph box. Deliberately
     * larger than [StarHitFraction]: two stars closer than the pick-up radius sit
     * inside one another and cannot be aimed at separately, which is what made the
     * umlaut ticks of Ä/Ö/Ü collect all four of their stars in a single swipe.
     */
    const val StarSpacingFraction = 0.28f

    /** Corridor half-width as a fraction of the glyph box. */
    const val CorridorFraction = 0.16f

    /** Thinner road for two-character graphemes (Au, Ei, Ch, …). */
    const val DigraphCorridorFraction = 0.10f

    /** Even thinner for three-character graphemes (Sch). */
    const val TrigraphCorridorFraction = 0.09f

    /**
     * Vertical squeeze toward the box mid-line for two-character graphemes, so Au/Ei
     * do not fill the full square height and look stretched beside their side-by-side
     * letters.
     */
    const val DigraphHeightScale = 0.72f

    /** Stronger vertical squeeze for Sch (three letters in one square). */
    const val TrigraphHeightScale = 0.62f

    /** Star pick-up radius as a fraction of the glyph box. */
    const val StarHitFraction = 0.12f

    /**
     * Strokes shorter than this fraction of the glyph box are diacritic ticks (umlaut
     * dots). They share the same corridor maths as long bars but are drawn thinner so
     * the round road caps do not turn a 0.04-long tick into a blob that eats the letter.
     */
    const val ShortStrokeFraction = 0.12f

    /** Visual road-width scale for [ShortStrokeFraction] ticks relative to a full bar. */
    const val ShortStrokeWidthScale = 0.35f

    /**
     * How a glyph sits in the square canvas: single letters keep the full square and
     * thick road; multi-grapheme forms (Au, Sch, …) get a shorter height and a thinner
     * corridor so the side-by-side letters have room.
     */
    data class Fit(
        val heightScale: Float = 1f,
        val corridorFraction: Float = CorridorFraction,
    ) {
        val isCompact: Boolean get() = heightScale < 1f || corridorFraction < CorridorFraction
    }

    /**
     * Grapheme width from the atom lemma (spaces ignored so "S t" counts as St).
     * Display is not used: `letter-ae` shows "Äh" but is still one letter (Ä).
     */
    fun graphemeUnits(lemma: String): Int =
        lemma.replace(" ", "").length.coerceAtLeast(1)

    fun fitFor(lemma: String): Fit = when (graphemeUnits(lemma)) {
        1 -> Fit()
        2 -> Fit(heightScale = DigraphHeightScale, corridorFraction = DigraphCorridorFraction)
        else -> Fit(heightScale = TrigraphHeightScale, corridorFraction = TrigraphCorridorFraction)
    }

    /** Stars scale with how much road there actually is to drive. */
    fun starCountFor(strokeLength: Float, boxSize: Float): Int {
        if (boxSize <= 0f) return MinStars
        val spacing = boxSize * StarSpacingFraction
        if (spacing <= 0f) return MinStars
        return (strokeLength / spacing).toInt().coerceIn(MinStars, MaxStars)
    }

    fun isShortStroke(strokeLength: Float, boxSize: Float): Boolean =
        boxSize > 0f && strokeLength < boxSize * ShortStrokeFraction

    /** Arc distance on a closed loop of length [total] — the shorter way around. */
    private fun circularDistance(a: Float, b: Float, total: Float): Float {
        val direct = kotlin.math.abs(a - b)
        return minOf(direct, total - direct)
    }

    fun update(
        state: TraceState,
        finger: TracePoint,
        strokes: List<List<TracePoint>>,
        stars: List<List<TracePoint>>,
        boxSize: Float,
        /**
         * Previous pointer sample on this drag. When the finger jumps past a star between
         * two samples (common on a fast preschool swipe), the segment from here to
         * [finger] still counts as collecting — otherwise the ahead-gate freezes the
         * vehicle and the red dot "runs past" the star.
         */
        previousFinger: TracePoint? = null,
        corridorFraction: Float = CorridorFraction,
    ): TraceUpdate {
        if (state.strokeIndex >= strokes.size) {
            return TraceUpdate(state, collectedStar = false, offCorridor = false, glyphDone = true)
        }
        val stroke = strokes[state.strokeIndex]
        val corridor = boxSize * corridorFraction
        if (TraceGeometry.distanceToPolyline(finger, stroke) > corridor) {
            // A finger still resting on an already *finished* stroke is not "off the
            // road" — after a bar hand-off without lifting (T, E, Ei) the finger sits
            // at the old bar's end, which lies outside the new bar's corridor. That
            // must behave like `ahead` (no nudge, no off-road count), or every
            // continuous multi-bar trace collects a phantom correction per hand-off.
            val onFinishedStroke = strokes.take(state.strokeIndex).any {
                TraceGeometry.distanceToPolyline(finger, it) <= corridor
            }
            return if (onFinishedStroke) {
                TraceUpdate(state, collectedStar = false, offCorridor = false, glyphDone = false, ahead = true)
            } else {
                TraceUpdate(state, collectedStar = false, offCorridor = true, glyphDone = false)
            }
        }
        stars.getOrNull(state.strokeIndex)?.getOrNull(state.starIndex)
            ?: return TraceUpdate(state, collectedStar = false, offCorridor = false, glyphDone = false)
        val starHit = boxSize * StarHitFraction
        // A corridor runs the whole length of its stroke, so being inside it says nothing
        // about *where* along the letter the finger is. Star i sits at fraction (i+1)/count
        // of the stroke by construction in TraceGeometry.starPositions, which gives the
        // target's arc length without projecting a point that a closed stroke may share
        // with its own start.
        val total = TraceGeometry.polylineLength(stroke)
        val targetArc = total *
            (state.starIndex + 1).toFloat() / stars[state.strokeIndex].size
        val fingerArc = TraceGeometry.arcLengthAt(stroke, finger)
        // Closed strokes (O, Ö, Qu-Bogen: first == last) make arcLengthAt bistable at
        // the seam — a finger resting on the start point projects to arc ≈ 0 or ≈ total
        // depending on 1–2 px of touch jitter. All arc comparisons must therefore be
        // wrap-aware, or every jitter flip bridges nearly the whole loop and collects
        // stars the child never traced.
        val closed = stroke.size > 2 && stroke.first() == stroke.last() && total > 0f
        // Collect by along-path proximity, not Euclidean distance to the star centre.
        // The road is wider than the old point-hit radius, so a finger riding the outer
        // edge of the corridor (legal) used to miss stars that sit on the centreline.
        val onStar = if (closed) {
            circularDistance(fingerArc, targetArc, total) <= starHit
        } else {
            kotlin.math.abs(fingerArc - targetArc) <= starHit
        }
        val crossedStar = previousFinger != null &&
            TraceGeometry.distanceToPolyline(previousFinger, stroke) <= corridor &&
            run {
                val prevArc = TraceGeometry.arcLengthAt(stroke, previousFinger)
                if (closed) {
                    // The bridge follows the *shorter* arc between the two samples.
                    // A seam flip (prev ≈ 0, finger ≈ total) spans ~0 and covers
                    // nothing; the legitimate final crossing (0.97·L → 0.02·L) spans
                    // 0.05·L and still collects the last star. Same tolerance as the
                    // open-stroke interval check, expressed circularly.
                    val span = circularDistance(prevArc, fingerArc, total)
                    circularDistance(prevArc, targetArc, total) +
                        circularDistance(targetArc, fingerArc, total) <= span + 2 * starHit
                } else {
                    val lo = minOf(prevArc, fingerArc)
                    val hi = maxOf(prevArc, fingerArc)
                    lo <= targetArc + starHit && hi >= targetArc - starHit
                }
            }
        val hit = onStar || crossedStar
        // Ahead of that star the finger marks no progress — the pick-up radius is the only
        // allowance, so a small overshoot still collects. Without this the drag that leaves
        // the E of "Ei" at the bottom right slides on into the i's road at its foot and
        // takes the vehicle with it, parking the start dot at the foot of the i.
        if (!hit && fingerArc > targetArc + starHit) {
            return TraceUpdate(
                state = state,
                collectedStar = false,
                offCorridor = false,
                glyphDone = false,
                ahead = true,
            )
        }
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
