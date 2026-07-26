package app.abcvorschule.ui.exercise

/**
 * Shared placement rules for the ordered trainers (word_build frames and the
 * sentence_order clothesline). Compares displays, so a repeated syllable such as
 * "ma" in Mama is accepted at every index where the solution expects it.
 */
object OrderedPlacement {
    fun isCorrectPlacement(index: Int, display: String, solution: List<String>): Boolean =
        solution.getOrNull(index) == display

    fun isSolved(placed: Map<Int, String>, solution: List<String>): Boolean =
        solution.isNotEmpty() &&
            solution.indices.all { placed[it] == solution[it] }

    fun nextEmptyIndex(placed: Map<Int, String>, size: Int): Int? =
        (0 until size).firstOrNull { placed[it] == null }
}
