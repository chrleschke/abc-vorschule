package app.abcvorschule.ui.exercise

/**
 * Input rules for the numeric answer field, kept Compose-free so they stay
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

    /** Vorschub einer Ziffer als Anteil der Schriftgröße. Serif-Bold-Ziffern
     * (displayLarge) liegen um ~0.5 em; 0.6 hat denselben Sicherheits-Headroom
     * wie [WordFrameSizing.GlyphAspect] gegenüber seiner Schätzbasis. */
    const val DigitAspect = 0.6f

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

    fun resetToken(roundKey: String, misses: Int): String = "$roundKey#$misses"
}
