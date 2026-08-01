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

    /** Matches AbcDimens.kidTouch — the comfortable row height for a single row. */
    const val MaxRowHeightDp = 80f

    /**
     * Row height once a word wraps. Still above [MinFrameDp], so a segment stays
     * hittable, but low enough that two rows fit the task block: the wrap threshold
     * is width-derived, so "Häuser" (6 segments) already wraps on a Pixel 7 (329dp
     * of usable stage), and two [MaxRowHeightDp] rows would push the prompt block
     * past its space on a short 360x640dp device — [ExerciseStage] neither scrolls
     * nor clips, so the overflow would overdraw the chrome above and the strokes
     * below.
     */
    const val WrappedRowHeightDp = 64f

    /**
     * Height of one segment row. Full height while the word stays on one line, the
     * reduced [WrappedRowHeightDp] as soon as it wraps — height is the only budget
     * a second row spends, and the touch target survives either way because both
     * values clear [MinFrameDp].
     */
    fun rowHeightDp(segmentCount: Int, segmentsPerRow: Int): Float =
        if (segmentsPerRow in 1 until segmentCount) WrappedRowHeightDp else MaxRowHeightDp

    // --- Wort-Detektiv: a word, not a tray ------------------------------------
    //
    // The Wort-Bauer spreads its frames over the whole stage because they are slots
    // to drop tiles into — the whitespace is the target. The Wort-Detektiv has no
    // slots up there; the segments are invisible hit boxes around a word the child
    // has to *read*. Sharing the stage equally put 96dp between the centres of two
    // 33dp glyphs in "Mama", which reads as loose letters rather than as a word.
    // These three functions instead hug each glyph and spend the freed width on
    // making the glyph itself bigger.

    /** Just enough air that two adjacent hit boxes are still separate targets. */
    const val WordSegmentGapDp = 2f

    /** Air per side between a glyph and the edge of its hit box. */
    const val WordSegmentPaddingDp = 3f

    /**
     * How much of a row's height one glyph may claim. The rest is the ascender and
     * descender space [Text] needs; deriving the cap instead of fixing it is what
     * lets the wrapped 64dp row shrink its glyphs by itself rather than clip them.
     */
    const val WordGlyphHeightFraction = 0.68f

    /**
     * Glyph size for the Wort-Detektiv's segments: as large as the row height
     * allows, stepped down only when the widest segment would not fit its share of
     * [available]. Larger than [MaxGlyphSp] on purpose — the word is the thing the
     * child is looking at, so it gets the app's biggest glyph, not the Wort-Bauer's
     * tile size.
     *
     * Both budgets are dp, so [fontScale] divides the result the same way
     * `FinaleLayout.capEffectiveSize` does: the *rendered* glyph stays inside the
     * row a system font scale above 1.0 too, instead of growing out of the 80dp
     * row the way a raw sp value would. [MinGlyphSp] still wins in the end — an
     * illegible glyph is worse than an overflowing one, same trade as [glyphSp].
     */
    fun wordGlyphSp(
        available: Float,
        segmentsPerRow: Int,
        longestDisplayChars: Int,
        rowHeightDp: Float,
        fontScale: Float = 1f,
    ): Float {
        val perRow = segmentsPerRow.coerceAtLeast(1)
        val chars = longestDisplayChars.coerceAtLeast(1)
        val share = (available - WordSegmentGapDp * (perRow - 1)) / perRow
        val widthCap = (share - 2 * WordSegmentPaddingDp) / (chars * GlyphAspect)
        val heightCap = rowHeightDp * WordGlyphHeightFraction
        val budget = minOf(widthCap, heightCap)
        val scaled = if (fontScale > 1f) budget / fontScale else budget
        return scaled.coerceAtLeast(MinGlyphSp)
    }

    /**
     * Hit box around one segment: its own glyph's width plus padding, never below
     * the touch-target floor. Per segment rather than uniform, so "Sch·u·h" gives
     * `Sch` the width it needs without granting `u` and `h` the same — a uniform
     * width is the other half of what made the word look scattered.
     *
     * Fits by construction when [glyphSp] came from [wordGlyphSp] for the same row
     * and [fontScale]: every segment is at most as wide as the equal share that size
     * was solved against, and the [MinFrameDp] floor stays under that share because
     * [segmentsPerRow] is derived from a wider per-segment budget ([MinGapDp]).
     */
    fun wordSegmentWidthDp(glyphSp: Float, displayChars: Int, fontScale: Float = 1f): Float =
        (glyphSp * fontScale * GlyphAspect * displayChars.coerceAtLeast(1) + 2 * WordSegmentPaddingDp)
            .coerceAtLeast(MinFrameDp)
}
