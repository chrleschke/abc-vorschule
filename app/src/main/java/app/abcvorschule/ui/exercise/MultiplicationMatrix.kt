package app.abcvorschule.ui.exercise

import kotlin.math.ceil

/**
 * Layout rules for the multiplication matrix: "4 mal 5" is drawn as 4 rows of
 * 5 slots. Only the first row shows the real objects; every further row holds
 * ghost placeholders of the same shape. The child reads multiplication as
 * "one row, taken this many times" — a two-dimensional quantity instead of a
 * memorized fact.
 */
object MultiplicationMatrix {
    /** Content caps keeping the grid countable on a phone (validator-enforced). */
    const val MaxRows = 5
    const val MaxColumns = 6

    /** Ghost rows repeat the first row's shape without competing with it. */
    const val GhostAlpha = 0.25f

    /** The concrete row is the first one; it carries the "je N" group. */
    fun isConcreteRow(rowIndex: Int): Boolean = rowIndex == 0

    /** Wide grids shrink so six columns still fit beside their siblings at font scale 1.3. */
    fun emojiSizeSp(columns: Int): Int = if (columns >= 5) 26 else 34

    /**
     * Rows are numbered 1..rows in a gutter left of the grid. Counting rows is
     * the whole point of the picture, and the ghost rows give the eye nothing to
     * hold on to — the numeral does. Digits the child can already read, so this
     * stays inside the audio-first rule.
     */
    fun rowLabel(rowIndex: Int): String = (rowIndex + 1).toString()

    /** Schriftgröße der Zeilennummern: `labelLarge` aus `ui/theme/Theme.kt` —
     * dokumentierte Kopie nach dem Muster der M3-Basen in `FinaleLayout`. */
    const val RowLabelSp = 16f

    /** Größte System-Schriftskalierung, die Android anbietet und gegen die die
     * Rinne ausgelegt sein muss. */
    const val MaxFontScale = 2f

    /** Gerenderter Vorschub einer Zeilennummer bei [fontScale] — eine Ziffer,
     * [MaxRows] < 10 hält das test-gesichert so. Aspekt mit Headroom aus
     * [WordFrameSizing.GlyphAspect]. */
    fun rowLabelAdvanceDp(fontScale: Float): Float =
        RowLabelSp * fontScale * WordFrameSizing.GlyphAspect

    /**
     * Fixed gutter so every row starts at the same x. Kein Literal mehr, sondern
     * gegen [MaxFontScale] abgeleitet (16sp × 2.0 × 0.72 ≈ 23dp, aufgerundet und
     * nie unter den gewohnten 24dp): eine Theme- oder Deckelanpassung verbreitert
     * die Rinne dann von selbst, statt die Ziffer bei font_scale 2.0 zu clippen.
     */
    val RowLabelGutterDp: Int = maxOf(24, ceil(rowLabelAdvanceDp(MaxFontScale)).toInt())

    /**
     * The task itself, written above the grid: "3 × 4". Without it the picture
     * alone leaves the two factors implicit once the spoken prompt has faded.
     */
    fun equationLabel(rows: Int, columns: Int): String =
        "$rows ${MathOperation.Multiply.symbol} $columns"
}
