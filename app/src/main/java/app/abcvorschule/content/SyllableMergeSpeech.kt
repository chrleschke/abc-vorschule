package app.abcvorschule.content

/** How Verschmelzer speaks the merged syllable — lemma clip when display differs. */
object SyllableMergeSpeech {
    fun resultSpeech(round: SyllableMergeRound, resultAtom: Atom?): String =
        AtomSpeech.phonemeClipText(resultAtom) ?: round.resultDisplay
}
