package app.abcvorschule.content

/**
 * How Buchstaben-/Silben-Jagd speaks its task intro: a fixed Qwen prompt clip
 * ("Finde alle Buchstaben" / Laute / Silben) followed by the target grapheme's
 * phoneme lemma clip — same sequenced pattern as other prompt + atom speech.
 */
object SymbolHuntSpeech {
    /** Ordered strings for [SpeechController] — prompt clip, then grapheme clip. */
    fun promptParts(round: SymbolHuntRound, target: Atom?): List<String> {
        val graphemeSpeech = target?.lemma?.takeIf { it.isNotBlank() }
            ?: target?.display?.takeIf { it.isNotBlank() }
        return if (graphemeSpeech != null) {
            listOf(round.promptTts, graphemeSpeech)
        } else {
            listOf(round.promptTts)
        }
    }
}
