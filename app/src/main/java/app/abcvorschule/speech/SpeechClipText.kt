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

    /**
     * Baustein des Wort-Bauers. Gesprochen wird, was auf der Kachel steht — das
     * Lemma des Atoms nur dann, wenn die Kachel dieses Atom auch zeigt (bis auf
     * Groß-/Kleinschreibung, `sch` im Wort gegen Lemma `Sch`).
     *
     * 23 Bausteine im Pack tragen einen Silbenschnipsel als Beschriftung, hängen
     * aber an einem Einzelbuchstaben-Atom: „gel" (Vogel) am Atom G, „Ster"
     * (Stern) an St, „Hä" (Häuser) an Ä. Ohne die Prüfung sprach die Kachel „G",
     * „S t", „Ä" — in kuratierter Stimme, also besonders glaubwürdig, und in
     * einer Lautier-App die falsche Laut-Zeichen-Zuordnung. Dass es anders
     * gemeint ist, zeigt derselbe Pack: wo ein Silben-Atom existiert, hängt der
     * Baustein daran („se" in Rose spricht „seh").
     *
     * Für die Schnipsel gibt es keinen Clip, sie laufen also über Android-TTS.
     * Die richtige Silbe in fremder Stimme ist besser als der falsche Laut in
     * der eigenen; die fehlenden Clips stehen in
     * docs/residual-review-findings/.
     */
    fun forWordBlock(pack: ContentPack, atomId: String, display: String): String {
        val atom = pack.atoms[atomId] ?: return display
        val lemma = atom.lemma.takeIf { it.isNotBlank() } ?: return display
        val tileShowsThisAtom = display.equals(atom.display, ignoreCase = true) ||
            display.equals(lemma, ignoreCase = true)
        return if (tileShowsThisAtom) lemma else display
    }

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
