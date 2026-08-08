package app.abcvorschule.ui.exercise

/** The three concrete quantity actions that can appear in the last trainer. */
enum class MathOperation(val wireName: String, val symbol: String) {
    Add("add", "+"),
    Subtract("subtract", "−"),
    Multiply("multiply", "×"),
    ;

    fun answer(left: Int, right: Int): Int = when (this) {
        Add -> left + right
        Subtract -> left - right
        Multiply -> left * right
    }

    companion object {
        fun fromWireName(value: String): MathOperation? = entries.firstOrNull { it.wireName == value }
    }
}

/** Amounts above ten stay countable as a number, but are no longer a wall of emoji. */
object QuantityRepresentation {
    const val SymbolicFrom = 11

    fun isSymbolic(count: Int): Boolean = count >= SymbolicFrom

    /**
     * Once either side of a round reaches the symbolic range, the *other* side
     * must switch too — otherwise "16 − 9" renders as one icon versus nine
     * icons, which reads as an inconsistent, confusing mix. This decides the
     * round-wide mode; it is not a per-number check.
     */
    fun forceSymbolicFor(left: Int, right: Int): Boolean = isSymbolic(left) || isSymbolic(right)

    /**
     * Same rule for the answer tiles: if any of the three choices is symbolic,
     * all three render symbolic. Otherwise a round like "5 + 5" (choices 9/10/11)
     * would put a single icon + numeral next to two emoji clusters — unequal
     * tiles that leak the answer's magnitude (§8: gleiche Dimensionen).
     */
    fun forceSymbolicForChoices(left: Int, right: Int, choices: List<Int>): Boolean =
        forceSymbolicFor(left, right) || choices.any { isSymbolic(it) }
}
