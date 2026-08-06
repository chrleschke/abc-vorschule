package app.abcvorschule.speech

import app.abcvorschule.content.Atom
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SymbolInWordRound

/**
 * Maps runtime display strings to the text keys used in [ClipIndex] / audio/index.json.
 *
 * Clip keys follow authored [Atom.lemma] values (phoneme profile for letters and
 * syllables). UI often shows a different casing (`sch` in a word vs lemma `Sch`).
 */
object SpeechClipText {
    fun forAtom(atom: Atom?): String? = atom?.lemma?.takeIf { it.isNotBlank() }

    fun forAtomId(pack: ContentPack, atomId: String, fallback: String = atomId): String =
        pack.atoms[atomId]?.lemma?.takeIf { it.isNotBlank() } ?: fallback

    /** Grapheme or syllable tapped inside a Wort-Detektiv word. */
    fun forSegment(pack: ContentPack, round: SymbolInWordRound, segmentIndex: Int): String {
        val segment = round.segments.getOrNull(segmentIndex) ?: return ""
        if (segmentIndex in round.targetIndices) {
            return forAtomId(pack, round.targetAtomId, segment)
        }
        return pack.atoms.values.firstOrNull { it.display == segment }?.lemma?.takeIf { it.isNotBlank() }
            ?: pack.atoms.values.firstOrNull { it.display.equals(segment, ignoreCase = true) }
                ?.lemma?.takeIf { it.isNotBlank() }
            ?: segment
    }
}
