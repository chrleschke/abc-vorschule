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
