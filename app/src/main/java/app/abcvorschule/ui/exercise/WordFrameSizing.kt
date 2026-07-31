package app.abcvorschule.ui.exercise

/**
 * Frame and glyph sizing for the Wort-Bauer, in plain dp/sp magnitudes so the
 * maths stays unit-testable (same Compose-free convention as [TraceGeometry]).
 *
 * A fixed frame width overflowed the stage as soon as a word needed five blocks
 * ("Häuser": 5 x 84 + 4 x 12 = 468 dp against 396 dp of usable width), and worse
 * on a narrow phone. Frames therefore shrink to fit, down to a floor that still
 * clears the 56 dp hit-box minimum from the design spec.
 */
object WordFrameSizing {
    /** Comfortable width when the word is short enough to afford it. */
    const val MaxFrameDp = 84f

    /** Floor: the design spec's hard hit-box minimum. Never go below this. */
    const val MinFrameDp = 56f

    /** Preferred horizontal gap between two frames. */
    const val MaxGapDp = 12f

    /** Tightened gap, used only to keep the frames above [MinFrameDp]. */
    const val MinGapDp = 4f

    /** Padding inside a frame, per side. */
    const val FramePaddingDp = 8f

    /** Matches AbcDimens.syllableSp — the size a single glyph gets when there is room. */
    const val MaxGlyphSp = 46f

    /** Below this a preschooler cannot read the block reliably. */
    const val MinGlyphSp = 20f

    /** Rough advance width of one glyph, as a fraction of its font size — a little
     * over the typical Roboto average-case advance, so real-font rendering has
     * some headroom against the estimate rather than sitting right at the edge. */
    const val GlyphAspect = 0.72f

    /**
     * Frames win over whitespace: the gap only tightens once the comfortable gap
     * would squeeze the frames below the touch-target floor.
     */
    fun gapDp(available: Float, frameCount: Int): Float {
        if (frameCount <= 1) return MaxGapDp
        val perFrameAtMaxGap = (available - MaxGapDp * (frameCount - 1)) / frameCount
        return if (perFrameAtMaxGap >= MinFrameDp) MaxGapDp else MinGapDp
    }

    fun frameWidthDp(available: Float, frameCount: Int): Float {
        if (frameCount <= 0) return MaxFrameDp
        val gaps = gapDp(available, frameCount) * (frameCount - 1)
        val perFrame = (available - gaps) / frameCount
        return perFrame.coerceIn(MinFrameDp, MaxFrameDp)
    }

    fun glyphSp(frameWidthDp: Float, longestDisplayChars: Int): Float {
        val chars = longestDisplayChars.coerceAtLeast(1)
        val usable = (frameWidthDp - 2 * FramePaddingDp).coerceAtLeast(1f)
        return (usable / (chars * GlyphAspect)).coerceIn(MinGlyphSp, MaxGlyphSp)
    }

    /**
     * How many segments fit in one row at the touch-target floor. At least one, so
     * an absurdly narrow stage degrades to one segment per row instead of zero.
     */
    fun maxPerRow(available: Float): Int {
        val perSegment = MinFrameDp + MinGapDp
        return (((available + MinGapDp) / perSegment).toInt()).coerceAtLeast(1)
    }

    /**
     * Rows needed for [segmentCount] segments. The Wort-Detektiv wraps long words
     * ("Xylophon" -> X·y·l·o·p·h·o·n) instead of shrinking below [MinFrameDp]:
     * a preschooler has to be able to hit the segment, and a word on two lines is
     * still readable while a 40dp target is not hittable.
     */
    fun rowCount(available: Float, segmentCount: Int): Int {
        if (segmentCount <= 0) return 1
        val perRow = maxPerRow(available)
        return ((segmentCount + perRow - 1) / perRow).coerceAtLeast(1)
    }

    /**
     * Segments per row, balanced across [rowCount] rows so two rows read as one
     * word broken in half rather than a full row plus an orphan. An uneven count
     * puts the extra segment in the earlier row.
     */
    fun segmentsPerRow(available: Float, segmentCount: Int): Int {
        if (segmentCount <= 0) return 1
        val rows = rowCount(available, segmentCount)
        return ((segmentCount + rows - 1) / rows).coerceAtLeast(1)
    }
}
