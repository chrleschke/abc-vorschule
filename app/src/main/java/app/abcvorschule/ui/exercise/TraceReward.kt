package app.abcvorschule.ui.exercise

/**
 * The reward line of a finished glyph. Content authors write it once as spoken text
 * ("T wie Tomate."), so the printed word is derived from that instead of duplicating
 * it in the schema — one source, no chance for the two to drift apart.
 */
object TraceReward {
    private const val Connector = " wie "

    /** "T wie Tomate." → "Tomate"; null when the authored line does not follow the pattern. */
    fun wordOf(rewardTts: String): String? =
        rewardTts.substringAfter(Connector, "")
            .trim()
            .trimEnd('.', '!', '?')
            .trim()
            .ifEmpty { null }
}
