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

    /**
     * Emoji size for a prompt that stacks pair rows vertically. A 9 needs five
     * rows; at the base size that tower (~340dp at fontScale 1.3) starves the
     * task block and runs over the answer row on small screens. Symbolic rounds
     * (single icon + numeral) keep the base size — their cluster row count is
     * meaningless. Mirrors [MultiplicationMatrix.emojiSizeSp], which shrinks by
     * column count for the same reason.
     */
    fun promptEmojiSizeSp(base: Int, left: Int, right: Int): Int {
        if (QuantityRepresentation.forceSymbolicFor(left, right)) return base
        val rows = maxOf(clusters(left).size, clusters(right).size)
        return when {
            rows <= 3 -> base
            rows == 4 -> base * 4 / 5
            else -> base * 2 / 3
        }
    }
}
