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

    fun sanitize(raw: String): String = raw.filter(Char::isDigit).take(MaxDigits)

    fun resetToken(roundKey: String, misses: Int): String = "$roundKey#$misses"
}
