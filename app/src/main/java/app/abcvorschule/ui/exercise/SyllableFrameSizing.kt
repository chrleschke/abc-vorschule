package app.abcvorschule.ui.exercise

/**
 * Keeps compound syllables legible instead of clipping them into a letter-sized
 * square — and, seit [mergeLayout], die ganze Verschmelzer-Zeile auf der Bühne.
 *
 * Compose-frei wie [WordFrameSizing], damit die Rechnung unit-testbar bleibt.
 *
 * Warum eine Layout-Rechnung statt fester Breiten: die alte Zeile aus
 * `widthDp(links) + 120dp Spur + widthDp(rechts)` brauchte für „sch" + „u"
 * schon 138 + 120 + 108 = 366dp — auf einem 360dp-Gerät sind aber nur ~296dp
 * Bühne nutzbar, die rechte Kachel wurde beschnitten. Und weil die Spur als
 * 120dp-Box in der *Bühnenmitte* lag, die Kacheln sich aber in der Mitte ihrer
 * (bei ungleichen Breiten unsymmetrischen) Lücke treffen, saß die Spur neben
 * dem Treffpunkt — gegen die Magnet-Symmetrie aus §3.3. [MergeLayout] leitet
 * deshalb Breiten, Lücke UND Spur-Versatz aus einer einzigen Rechnung ab.
 */
object SyllableFrameSizing {
    const val MinWidthDp = 108f
    private const val HorizontalPaddingDp = 36f
    private const val GlyphAdvanceDp = 34f

    /** Basisgröße des Kachel-Glyphen: AbcDimens.letterSp bei `fontScale = 1.0`. */
    const val MaxGlyphSp = 54f

    /**
     * Vorschub eines Glyphen als Anteil seiner Schriftgröße. Bewusst der bisher
     * ausgelieferte Wert [GlyphAdvanceDp]/[MaxGlyphSp] (≈ 0.63), nicht
     * [WordFrameSizing.GlyphAspect]: die Silben sind überwiegend Kleinbuchstaben,
     * und der Wert hat sich live bewährt — eine großzügigere Schätzung würde die
     * Kacheln auf schmalen Geräten grundlos schrumpfen.
     */
    const val GlyphAspect = GlyphAdvanceDp / MaxGlyphSp

    /** Die komfortable Schiebespur — der bisherige feste `FloeGap`. */
    const val MaxGapDp = 120f

    /**
     * Härtester Boden der Spur: darunter trägt weder die gepunktete Lichtwelle
     * noch die Schiebegeste (jede Kachel reist nur `gap / 2`) ihre Bedeutung.
     */
    const val MinGapDp = 24f

    /** Eine Kachel-Breite für einen einzeln stehenden Glyphen (Laut-Waggons u. ä.). */
    fun widthDp(label: String): Float =
        tileWidthDp(label.length, MaxGlyphSp, fontScale = 1f)

    /** Breiten, Lücke, Glyph und Spur-Versatz der Verschmelzer-Zeile — eine Quelle. */
    data class MergeLayout(
        val leftWidthDp: Float,
        val rightWidthDp: Float,
        val gapDp: Float,
        val glyphSp: Float,
        /**
         * Horizontaler Versatz der Spur-Mitte gegenüber der Zeilen-Mitte:
         * die Kacheln treffen sich in der Mitte ihrer Lücke, und die liegt bei
         * ungleich breiten Kacheln um `(links − rechts) / 2` neben der
         * Zeilen-Mitte. Die Spur bekommt denselben Versatz, damit die
         * einwärts laufende Lichtwelle exakt auf den Treffpunkt zeigt (§3.3).
         */
        val trackOffsetDp: Float,
    )

    /**
     * Löst die Zeile gegen die gemessene Bühnenbreite auf, in dieser Rangfolge:
     *
     * 1. Glyph auf effektive Basisgröße gedeckelt (Muster
     *    `FinaleLayout.capEffectiveSize`): oberhalb von `fontScale = 1.0` teilt
     *    die Skala den sp-Wert, die *gerenderte* Kachel bleibt so breit wie bei 1.0.
     * 2. Reicht das nicht, gibt die Spur Breite ab — bis [MinGapDp].
     * 3. Erst dann schrumpft der Glyph unter seine Basisgröße, nie unter
     *    [WordFrameSizing.MinGlyphSp] (unlesbar ist schlimmer als übergelaufen,
     *    derselbe Trade wie dort).
     */
    fun mergeLayout(
        availableDp: Float,
        leftLabel: String,
        rightLabel: String,
        fontScale: Float = 1f,
    ): MergeLayout {
        val fs = fontScale.coerceAtLeast(0.01f)
        var glyph = if (fs > 1f) MaxGlyphSp / fs else MaxGlyphSp
        var left = tileWidthDp(leftLabel.length, glyph, fs)
        var right = tileWidthDp(rightLabel.length, glyph, fs)
        if (left + right + MinGapDp > availableDp) {
            glyph = shrunkGlyphSp(availableDp - MinGapDp, leftLabel.length, rightLabel.length, fs)
            left = tileWidthDp(leftLabel.length, glyph, fs)
            right = tileWidthDp(rightLabel.length, glyph, fs)
        }
        val gap = (availableDp - left - right).coerceIn(MinGapDp, MaxGapDp)
        return MergeLayout(
            leftWidthDp = left,
            rightWidthDp = right,
            gapDp = gap,
            glyphSp = glyph,
            trackOffsetDp = (left - right) / 2f,
        )
    }

    /** Verschmolzene Ergebnis-Kachel: eine Kachel, dasselbe Deckel-/Schrumpf-Schema. */
    data class FloeSpec(val widthDp: Float, val glyphSp: Float)

    fun resultFloe(availableDp: Float, label: String, fontScale: Float = 1f): FloeSpec {
        val fs = fontScale.coerceAtLeast(0.01f)
        var glyph = if (fs > 1f) MaxGlyphSp / fs else MaxGlyphSp
        var width = tileWidthDp(label.length, glyph, fs)
        if (width > availableDp) {
            glyph = ((availableDp - HorizontalPaddingDp) /
                (label.length.coerceAtLeast(1) * GlyphAspect * fs))
                .coerceIn(WordFrameSizing.MinGlyphSp, MaxGlyphSp)
            width = tileWidthDp(label.length, glyph, fs).coerceAtMost(
                availableDp.coerceAtLeast(MinWidthDp),
            )
        }
        return FloeSpec(widthDp = width, glyphSp = glyph)
    }

    /**
     * Kachelbreite aus der *gerenderten* Glyphbreite (sp × fontScale), Muster
     * [WordFrameSizing.wordSegmentWidthDp]. [MinWidthDp] bleibt der Boden: zwei
     * Böden plus [MinGapDp] sind 240dp und passen auf jede unterstützte Bühne.
     */
    fun tileWidthDp(chars: Int, glyphSp: Float, fontScale: Float): Float =
        (chars.coerceAtLeast(1) * glyphSp * fontScale * GlyphAspect + HorizontalPaddingDp)
            .coerceAtLeast(MinWidthDp)

    /**
     * Glyphgröße, wenn beide Kacheln zusammen nur noch [budgetDp] haben. Ein
     * kurzes Label, das am [MinWidthDp]-Boden hängt, verbraucht dort ohnehin
     * seine Bodenbreite — sein Überschuss gehört dem langen Partner, sonst
     * schrumpfte „sch" für ein „u", das davon gar nicht breiter wird.
     */
    private fun shrunkGlyphSp(budgetDp: Float, leftChars: Int, rightChars: Int, fs: Float): Float {
        val lChars = leftChars.coerceAtLeast(1)
        val rChars = rightChars.coerceAtLeast(1)
        var glyph = (budgetDp - 2 * HorizontalPaddingDp) / ((lChars + rChars) * GlyphAspect * fs)
        val leftFloored = lChars * glyph * fs * GlyphAspect + HorizontalPaddingDp < MinWidthDp
        val rightFloored = rChars * glyph * fs * GlyphAspect + HorizontalPaddingDp < MinWidthDp
        if (leftFloored != rightFloored) {
            val freeChars = if (leftFloored) rChars else lChars
            glyph = (budgetDp - MinWidthDp - HorizontalPaddingDp) / (freeChars * GlyphAspect * fs)
        }
        return glyph.coerceIn(WordFrameSizing.MinGlyphSp, MaxGlyphSp)
    }
}
