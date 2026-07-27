package app.abcvorschule.ui.exercise

/** Keeps compound syllables legible instead of clipping them into a letter-sized square. */
object SyllableFrameSizing {
    const val MinWidthDp = 108f
    private const val HorizontalPaddingDp = 36f
    private const val GlyphAdvanceDp = 34f

    fun widthDp(label: String): Float =
        (label.length.coerceAtLeast(1) * GlyphAdvanceDp + HorizontalPaddingDp).coerceAtLeast(MinWidthDp)
}
