package app.abcvorschule.ui.exercise

/**
 * Presentation order for answer tiles.
 *
 * A tray that hands the child the solution already in reading order removes the
 * whole task — the sentence can be dragged left to right without reading it. But
 * the order must also stay stable across recompositions, so it is derived from a
 * per-round seed rather than from [kotlin.random.Random].
 */
object TrayOrder {
    fun <T> arrange(items: List<T>, seed: Int, displayOf: (T) -> String): List<T> {
        if (items.size < 2) return items
        val ordered = items.sortedBy { mix(displayOf(it).hashCode(), seed) }
        // A seeded sort can legitimately reproduce the input order; swapping the first
        // two guarantees the offered order is never exactly the solution order.
        if (ordered.map(displayOf) != items.map(displayOf)) return ordered
        return ordered.toMutableList().apply {
            val first = this[0]
            this[0] = this[1]
            this[1] = first
        }
    }

    private fun mix(hash: Int, seed: Int): Int {
        var h = hash * 31 + seed
        h = h xor (h ushr 15)
        h *= 0x2545F491
        return h xor (h ushr 13)
    }
}

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
