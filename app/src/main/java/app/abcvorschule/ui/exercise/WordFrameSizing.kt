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

    /**
     * Beide Budgets sind dp, also teilt [fontScale] das Ergebnis wie bei
     * [wordGlyphSp]: ohne die Division wächst der *gerenderte* Glyph (sp ×
     * fontScale) mit der System-Schriftgröße aus dem festen Rahmen heraus —
     * live belegt am Einwort-Satz-Architekten, dessen Silhouette „Mama" bei
     * font_scale 1.3 als „Mam" endete. [MinGlyphSp] gewinnt weiterhin zuletzt
     * (unlesbar ist schlimmer als übergelaufen, gleicher Trade wie
     * [wordGlyphSp]); den Überlauf des Floors fängt [fittedFrameWidthDp].
     */
    fun glyphSp(frameWidthDp: Float, longestDisplayChars: Int, fontScale: Float = 1f): Float {
        val chars = longestDisplayChars.coerceAtLeast(1)
        val usable = (frameWidthDp - 2 * FramePaddingDp).coerceAtLeast(1f)
        val budget = usable / (chars * GlyphAspect)
        val scaled = if (fontScale > 1f) budget / fontScale else budget
        return scaled.coerceIn(MinGlyphSp, MaxGlyphSp)
    }

    /**
     * Rahmenbreite, die [glyphSp] bei [fontScale] wirklich fasst: im Normalfall
     * die gleichverteilte Breite, gegen die der Glyph gelöst wurde — breiter nur,
     * wenn der [MinGlyphSp]-Floor gewonnen hat. Dann wird die Breite aus der
     * gerenderten Glyphbreite abgeleitet (Muster [wordSegmentWidthDp]) statt den
     * Text zu clippen: ein Rahmen, der über die Reihe hinausragt, ist besser als
     * eine Silhouette, aus der der letzte Buchstabe fehlt.
     */
    fun fittedFrameWidthDp(
        frameWidthDp: Float,
        glyphSp: Float,
        longestDisplayChars: Int,
        fontScale: Float = 1f,
    ): Float = maxOf(
        frameWidthDp,
        glyphSp * fontScale * GlyphAspect * longestDisplayChars.coerceAtLeast(1) + 2 * FramePaddingDp,
    )
}
