package app.abcvorschule.ui.exercise

/**
 * Input rules for the numeric answer field (Kinder-Ziffernblock), kept Compose-free so they stay
 * unit-testable. The reset token is the whole fix for the "previous answer stays
 * in the field" bug: the field is remembered against this token, so a new round
 * and every wrong try clear it, while a correct answer deliberately does not.
 */
object NumberPadInput {
    /** Answers in this curriculum never exceed three digits. */
    const val MaxDigits = 3

    /** Die bisherige feste Feldbreite — jetzt nur noch der Boden, damit sich bei
     * `fontScale <= 1.3` (dort reicht sie rechnerisch) kein Pixel verschiebt. */
    const val BaseFieldWidthDp = 140f

    /** Vorschub einer Ziffer als Anteil der Schriftgröße. Die fetten Ziffern der
     * Lernschrift (Silbo Fibel Bold, displayLarge) liegen bei ~0.61 em; 0.72 hat
     * denselben Sicherheits-Headroom wie [WordFrameSizing.GlyphAspect]. Die frühere
     * 0.6 war auf Serif-Ziffern (~0.5 em) gerechnet. */
    const val DigitAspect = 0.72f

    /** OutlinedTextField-Innenabstand: M3-Default 16dp je Seite. */
    const val FieldPaddingDp = 32f

    /**
     * Mindestbreite des Antwortfelds aus der *effektiven* Textgröße
     * (`sp × fontScale`): das feste 140dp-Feld fasst bei font_scale 2.0 keine
     * zwei displayLarge-Ziffern mehr (2 × 40sp × 2.0 × 0.6 + 32 = 128dp wären
     * es knapp, drei Ziffern 176dp), und ein Kind, das seine getippte Zahl
     * nicht sieht, kann sie nicht prüfen. Ausgelegt auf [MaxDigits], denn das
     * Feld muss die längste erlaubte Antwort zeigen, nicht die häufigste.
     */
    fun fieldWidthDp(textSp: Float, fontScale: Float): Float =
        (MaxDigits * textSp * fontScale * DigitAspect + FieldPaddingDp)
            .coerceAtLeast(BaseFieldWidthDp)

    
    fun sanitize(raw: String): String = raw.filter(Char::isDigit).take(MaxDigits)

    /**
     * Eine Ziffer des Kinder-Ziffernblocks anhängen. Eine führende Null wird ersetzt
     * statt vorangestellt — „07" ist keine Zahl, die ein Kind je schreiben soll —, und
     * über [MaxDigits] hinaus passiert nichts.
     */
    fun append(value: String, digit: Int): String {
        require(digit in 0..9) { "digit=$digit" }
        val next = if (value == "0") "$digit" else value + digit
        return sanitize(next)
    }

    /** Die letzte Ziffer löschen. */
    fun backspace(value: String): String = value.dropLast(1)

    /**
     * Die Tastenreihen: zwei Fünferreihen wie das Fünfer-Feld der Zähl-Hilfe und
     * der Finger zweier Hände — 1–5 oben, 6–9 und 0 unten.
     */
    val KeyRows: List<List<Int>> = listOf(listOf(1, 2, 3, 4, 5), listOf(6, 7, 8, 9, 0))

    /** Abstand zwischen den Tasten. */
    const val KeyGapDp = 8f

    /** Größte Taste — darüber wird der Block auf Tablets zur Fläche statt zur Tastatur. */
    const val MaxKeyDp = 72f

    /**
     * Unterste Taste: der Boden aller Kinder-Trefferflächen (Satz-Architekt,
     * Wort-Bauer-Rahmen), nicht [app.abcvorschule.ui.theme.AbcDimens.kidTouch] —
     * fünf 80-dp-Tasten passen auf keinem 360-dp-Telefon in eine Reihe.
     */
    const val MinKeyDp = 56f

    /** Kantenlänge einer Taste für die verfügbare Breite: fünf Tasten plus vier Abstände. */
    fun keySizeDp(availableWidthDp: Float): Float =
        ((availableWidthDp - 4 * KeyGapDp) / 5f).coerceIn(MinKeyDp, MaxKeyDp)

    fun resetToken(roundKey: String, misses: Int): String = "$roundKey#$misses"
}
