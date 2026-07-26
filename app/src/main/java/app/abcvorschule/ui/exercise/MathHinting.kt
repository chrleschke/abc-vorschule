package app.abcvorschule.ui.exercise

import app.abcvorschule.progress.ScaffoldLevel

object MathHinting {
    const val NearDistanceMax = 2

    /** Exactly three numeric choices including the answer (near distractors only). */
    fun threeChoices(answer: Int): List<Int> {
        val opts = linkedSetOf(answer)
        if (answer > 1) opts += answer - 1
        var next = answer + 1
        while (opts.size < 3) {
            opts += next
            next++
        }
        return opts.toList()
    }

    fun distance(answer: Int, guess: Int): Int = kotlin.math.abs(answer - guess)

    fun isNear(answer: Int, guess: Int): Boolean {
        val d = distance(answer, guess)
        return d in 1..NearDistanceMax
    }

    fun isNear(distance: Int): Boolean = distance in 1..NearDistanceMax

    fun hintKey(answer: Int, guess: Int): String =
        if (isNear(answer, guess)) "near" else "far"

    fun hintText(answer: Int, guess: Int): String =
        missFeedback(distance(answer, guess))

    /** Feedback shown after a miss; [distance] null means the miss has no numeric distance. */
    fun missFeedback(distance: Int?): String = when {
        distance == null -> "Versuch es noch einmal"
        isNear(distance) -> "Du bist nah dran, denk noch einmal nach"
        else -> "Schau noch einmal genau hin"
    }

    /** Advanced = type the result; Beginner = pick from three labeled quantities. */
    fun usesNumberPad(scaffold: ScaffoldLevel): Boolean = scaffold == ScaffoldLevel.Advanced
}
