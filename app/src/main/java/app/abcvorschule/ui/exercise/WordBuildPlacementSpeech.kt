package app.abcvorschule.ui.exercise

/**
 * Speech timing for Wort-Bauer block placements.
 *
 * Each correct tile speaks its atom. On the final tile, success speech (the whole
 * word) must not start until that last atom clip finishes — otherwise
 * [app.abcvorschule.speech.SpeechController.speakAndAwait] flushes it.
 */
object WordBuildPlacementSpeech {
    enum class BlockSpeechMode {
        /** Non-final placement — fire-and-forget via [onSpeak]. */
        Immediate,
        /** Final placement — await block clip before [onResult] triggers success speech. */
        AwaitBeforeSuccess,
    }

    fun blockSpeechMode(
        placedBefore: Map<Int, String>,
        index: Int,
        display: String,
        solution: List<String>,
    ): BlockSpeechMode {
        val after = placedBefore + (index to display)
        return if (OrderedPlacement.isSolved(after, solution)) {
            BlockSpeechMode.AwaitBeforeSuccess
        } else {
            BlockSpeechMode.Immediate
        }
    }
}
