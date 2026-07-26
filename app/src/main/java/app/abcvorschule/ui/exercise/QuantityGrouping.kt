package app.abcvorschule.ui.exercise

/**
 * Preschool-friendly subitizing groups: prefer pairs, leftover singleton.
 * Example: 5 -> [2, 2, 1] rendered as two-by-two plus one.
 */
object QuantityGrouping {
    fun clusters(count: Int): List<Int> {
        if (count <= 0) return emptyList()
        val pairs = count / 2
        val rem = count % 2
        return buildList {
            repeat(pairs) { add(2) }
            if (rem == 1) add(1)
        }
    }
}
