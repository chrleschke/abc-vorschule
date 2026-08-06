package app.abcvorschule.content

/**
 * Maps atoms to the text key used for phoneme / syllable audio clips.
 * Grapheme display (e.g. "ro") may differ from the authored lemma ("roh").
 */
object AtomSpeech {
    fun phonemeClipText(atom: Atom?): String? =
        atom?.lemma?.takeIf { it.isNotBlank() }
            ?: atom?.display?.takeIf { it.isNotBlank() }
}
