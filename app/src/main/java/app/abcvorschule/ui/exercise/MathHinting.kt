package app.abcvorschule.ui.exercise

object MathHinting {
    const val NearDistanceMax = 2

    fun distance(answer: Int, guess: Int): Int = kotlin.math.abs(answer - guess)

    fun isNear(answer: Int, guess: Int): Boolean {
        val d = distance(answer, guess)
        return d in 1..NearDistanceMax
    }

    fun hintKey(answer: Int, guess: Int): String =
        if (isNear(answer, guess)) "near" else "far"

    fun hintText(answer: Int, guess: Int): String =
        if (isNear(answer, guess)) {
            "Du bist nah dran, denk noch einmal nach"
        } else {
            "Schau noch einmal genau hin"
        }

    fun usesNumberPad(scaffoldBeginnerForced: Boolean, preferVisual: Boolean): Boolean =
        !scaffoldBeginnerForced && !preferVisual
}
