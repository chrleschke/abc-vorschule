package app.abcvorschule.content

/**
 * How Wort-Detektiv speaks its task intro: a short prompt phrase (Android TTS when
 * no clip exists) followed by the target grapheme's phoneme lemma clip and the
 * word's lemma clip — same sequenced pattern as [SymbolHuntSpeech].
 */
object SymbolInWordSpeech {
    /** Spoken connector between grapheme and word — e.g. „Finde den Buchstaben“ → „A“ → „…im Wort…“ → „Mama“. */
    const val IN_CONNECTOR = "...im Wort..."

    /** Ordered strings for [app.abcvorschule.speech.SpeechController]. */
    fun promptParts(round: SymbolInWordRound, target: Atom?, word: Atom?): List<String> {
        val graphemeSpeech = target?.lemma?.takeIf { it.isNotBlank() }
            ?: target?.display?.takeIf { it.isNotBlank() }
        val intro = introPhrase(round.promptTts)
        val wordSpeech = word?.lemma?.takeIf { it.isNotBlank() }
            ?: word?.display?.takeIf { it.isNotBlank() }
        return when {
            intro != null && graphemeSpeech != null && wordSpeech != null ->
                listOf(intro, graphemeSpeech, IN_CONNECTOR, wordSpeech)
            intro != null && graphemeSpeech != null ->
                listOf(intro, graphemeSpeech)
            graphemeSpeech != null ->
                listOfNotNull(round.promptTts.takeIf { it.isNotBlank() }, graphemeSpeech)
            else ->
                listOfNotNull(round.promptTts.takeIf { it.isNotBlank() })
        }
    }

    /** Leading phrase before " - {target} - im Wort - …" in derived promptTts strings. */
    internal fun introPhrase(promptTts: String): String? = when {
        promptTts.startsWith("Finde den Buchstaben - ") -> "Finde den Buchstaben"
        promptTts.startsWith("Finde alle Buchstaben - ") -> "Finde alle Buchstaben"
        promptTts.startsWith("Finde den Laut - ") -> "Finde den Laut"
        promptTts.startsWith("Finde alle Laute - ") -> "Finde alle Laute"
        promptTts.startsWith("Finde die Silbe - ") -> "Finde die Silbe"
        promptTts.startsWith("Finde alle Silben - ") -> "Finde alle Silben"
        else -> null
    }
}
