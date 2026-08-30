package app.abcvorschule.ui.exercise

/**
 * Layout-Regeln des Zehnerfeldes — der stehenden Darstellung einer
 * **Additionsaufgabe** im Tipp-Modus (design doc 2026-08-30-zehnerfeld-addition).
 *
 * Drei Dinge unterscheiden es von [CountingField], das Minus und Malnehmen
 * weiterträgt:
 *
 * 1. Zehn statt fünf Objekte pro Zeile. Der Zehner ist die Einheit, an der der
 *    Zehnerübergang hängt; Fünferzeilen zeigen ihn nie.
 * 2. Es steht **von Anfang an** da, nicht erst nach zwei Fehlversuchen — wer
 *    keine Strategie hat, soll nicht erst zweimal raten dürfen.
 * 3. Die Größe kommt aus der **gemessenen** Breite statt aus einer konservativen
 *    Konstanten. Bei zehn Objekten pro Zeile ist Breite die einzige enge
 *    Schranke (30 Objekte sind nur drei Zeilen), und geschätzte 320dp
 *    verschenkten auf einem normalen Telefon spürbar Bildgröße.
 *
 * Compose-frei, damit die Rechnungen als JVM-Test prüfbar bleiben.
 */
object TenFrame {
    /** Objekte pro Zeile. */
    const val RowSize = 10

    /** Nach so vielen Objekten sitzt die breitere Lücke — die „Kraft der Fünf". */
    const val FiveGroup = 5

    /** Abstand zwischen zwei Objekten derselben Fünfergruppe, in dp. */
    const val CellGapDp = 2f

    /** Abstand zwischen den beiden Fünfergruppen, in dp. Deutlich größer als
     * [CellGapDp], sonst ist die Gruppierung nicht zu sehen — und genau sie ist
     * das, was ein Kind ohne Abzählen erfassen soll. */
    const val FiveGapDp = 8f

    /** Innenabstand einer Zelle, in dp. */
    const val CellPadDp = 1f

    /** Abstand zwischen zwei Zeilen, in dp. */
    const val RowGapDp = 4f

    /** Schriftskalierung, gegen die ausgelegt wird — wie
     * [CountingField.LayoutFontScale]: das Testgerät steht auf 1.3. */
    const val LayoutFontScale = 1.3f

    /** Volle Größe, wenn Platz ist. */
    const val MaxEmojiSp = 30

    /**
     * Untergrenze. Niedriger als [CountingField.MinEmojiSp], und das ist der
     * bewusste Tausch: zehn Objekte nebeneinander sind zwangsläufig kleiner als
     * fünf. Erkennbar bleiben sie, weil ein Emoji eine Silhouette ist und keine
     * Schrift — und **treffbar** bleiben sie unabhängig davon, weil die
     * Trefferfläche der aktiven Zelle über die Nachbarn ragen darf
     * ([hitTargetDp]).
     */
    const val MinEmojiSp = 18

    /**
     * Kleinste Trefferfläche der aktiven Zelle, in dp — die Hälfte von
     * `AbcDimens.kidTouch` (80dp). Dokumentierte Kopie statt Import, damit diese
     * Datei Compose-frei bleibt (Muster: [MultiplicationMatrix.RowLabelSp]).
     */
    const val MinHitTargetDp = 40f

    /** Breite, wenn keine gemessen vorliegt — dieselbe konservative Schätzung
     * wie [CountingField.FieldWidthDp]. */
    const val FallbackFieldWidthDp = 320f

    /** Strichstärke des Zehnerrahmens, in dp. */
    const val FrameBorderDp = 2f

    /** Eckenradius des Zehnerrahmens, in dp. */
    const val FrameCornerDp = 10f

    /**
     * Luft zwischen Rahmen und Objekten **plus** Strichstärke, in dp. Dieser
     * Abstand wird von der ersten Sekunde an reserviert, auch solange noch kein
     * Zehner voll ist: entstünde er erst beim Einrasten, wüchse das Feld genau in
     * dem Moment, in dem das Kind den nächsten Platzhalter treffen will.
     */
    const val FrameInsetDp = 8f

    /** Zeilenhöhe der Standmarke als Vielfaches ihrer Schriftgröße — Zuschlag für
     * Ober- und Unterlängen, damit die reservierte Höhe die gezeichnete Zeile
     * sicher fasst. */
    const val MarkLineFactor = 1.3f

    /** Zehnerzeilen einer Menge; die letzte Zeile ist kürzer. */
    fun rows(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val full = count / RowSize
        val rest = count % RowSize
        return buildList {
            repeat(full) { add(RowSize) }
            if (rest > 0) add(rest)
        }
    }

    /** Sitzt hinter dieser Spalte die Fünfer-Lücke? */
    fun hasFiveGapAfter(columnIndex: Int): Boolean = columnIndex == FiveGroup - 1

    /** Summe aller Lücken einer vollen Zeile, in dp. */
    private const val RowGapsDp = CellGapDp * (RowSize - 2) + FiveGapDp

    /** Kantenlänge einer Zelle in dp bei gegebener Emoji-Größe. */
    fun cellSizeDp(emojiSizeSp: Int): Float = emojiSizeSp * LayoutFontScale + 2 * CellPadDp

    /**
     * Emoji-Größe in sp aus der verfügbaren Breite. Hergeleitet, nicht gestuft —
     * dieselbe Begründung wie bei [CountingField.emojiSizeSp]: eine Stufentabelle
     * deckt den echten Content nicht ab, eine Herleitung gilt per Konstruktion.
     *
     * Unterhalb von ~278dp Breite gewinnt [MinEmojiSp] gegen die Breitenschranke,
     * die Zeile liefe dann über. Dies ist absichtlich: [MinEmojiSp] hat Vorrang,
     * um Erkennbarkeit zu sichern. Das schmalste angenommene Gerät liegt bei 320dp.
     */
    fun emojiSizeSp(fieldWidthDp: Float): Int {
        val byWidth = ((fieldWidthDp - RowGapsDp) / RowSize - 2 * CellPadDp) / LayoutFontScale
        return byWidth.toInt().coerceIn(MinEmojiSp, MaxEmojiSp)
    }

    /**
     * Schriftgröße, mit der das Emoji tatsächlich gezeichnet wird.
     *
     * [cellSizeDp] und [emojiSizeSp] rechnen bewusst gegen die Konstante
     * [LayoutFontScale]: die Zelle darf nicht mit der System-Schrift wachsen, weil
     * zehn Objekte nebeneinander bei jeder Schriftgröße in dieselbe Breite müssen.
     * Der `Text` darin rendert aber mit der **echten** `fontScale` — ab ~1.4 wäre
     * der Glyph breiter als seine Zelle, bei 2.0 stünden 38dp in 26,7dp.
     *
     * Gewählt ist darum das Deckeln der gerenderten Größe (die zweite der beiden
     * Möglichkeiten): oberhalb von [LayoutFontScale] wird die sp-Zahl so weit
     * heruntergerechnet, dass das Emoji **physisch** genau so groß bleibt wie bei
     * [LayoutFontScale] — es passt damit per Konstruktion in die Zelle. Die
     * Alternative (die Zelle aus der echten `fontScale` herleiten) hätte
     * [MinEmojiSp] als sp-Boden gegen die Breite antreten lassen und die Zeile bei
     * font_scale 2.0 überlaufen. Die Trefferfläche bleibt davon unberührt: sie ist
     * in dp gedeckelt ([MinHitTargetDp]) und schrumpft nie mit.
     */
    fun renderedEmojiSp(emojiSizeSp: Int, fontScale: Float): Float =
        if (fontScale <= LayoutFontScale) emojiSizeSp.toFloat()
        else emojiSizeSp * LayoutFontScale / fontScale

    /** Höhe der Standmarken-Zeile in dp — reserviert, auch wenn dort noch nichts
     * steht. Sie ist Teil der Höhe, die das Feld von Anfang an hält. */
    fun markSlotDp(markSizeSp: Float, fontScale: Float): Float =
        markSizeSp * fontScale * MarkLineFactor

    /**
     * Höhe des gezeichneten Rahmens über [rows] vollen Zeilen, in dp. Der Rahmen
     * wird gezeichnet statt gelayoutet — nur so kann er wachsen, ohne irgendetwas
     * zu verschieben.
     */
    fun framedBlockHeightDp(rows: Int, cellSizeDp: Float, markSlotDp: Float): Float =
        if (rows <= 0) 0f
        else 2 * FrameInsetDp + rows * (cellSizeDp + markSlotDp) + (rows - 1) * RowGapDp

    /**
     * Trefferfläche der **aktiven** Zelle. Sie ragt bewusst über ihre Nachbarn
     * hinaus: antippbar ist zu jedem Zeitpunkt genau eine Zelle, also kann die
     * Überlappung niemanden treffen — und ein Vorschulkind trifft keine 25dp.
     */
    fun hitTargetDp(emojiSizeSp: Int): Float = maxOf(MinHitTargetDp, cellSizeDp(emojiSizeSp))

    /** Wie viele **volle** Zeilen die echten Objekte ergeben — die Zehner. */
    fun fullRowCount(realCount: Int): Int = realCount / RowSize

    /** Ist diese Zeile voll besetzt? Nur volle Zeilen sind Zehner. */
    fun isRowFull(rowIndex: Int, realCount: Int): Boolean = rowIndex < fullRowCount(realCount)
}
