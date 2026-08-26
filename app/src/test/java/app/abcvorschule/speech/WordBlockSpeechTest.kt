package app.abcvorschule.speech

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.WordBuildSpec
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Eine Kachel des Wort-Bauers spricht, was auf ihr steht. Der Test läuft über den
 * ausgelieferten Pack, weil genau dort die Unstimmigkeit saß: 23 Bausteine tragen
 * einen Silbenschnipsel als Beschriftung, hängen aber an einem
 * Einzelbuchstaben-Atom — die Kachel „gel" sprach „G".
 */
class WordBlockSpeechTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun noTileSpeaksSomethingItDoesNotShow() {
        pack.tasks.values.filterIsInstance<WordBuildSpec>().forEach { spec ->
            spec.rounds.forEach { round ->
                (round.blocks + round.distractors).forEach { block ->
                    val spoken = SpeechClipText.forWordBlock(pack, block.atomId, block.display)
                    val atom = pack.atoms[block.atomId]
                    val tileShowsThisAtom = atom != null && (
                        block.display.equals(atom.display, ignoreCase = true) ||
                            block.display.equals(atom.lemma, ignoreCase = true)
                        )
                    val expected = if (tileShowsThisAtom) atom!!.lemma else block.display
                    assertEquals("Baustein ${block.display} (${block.atomId})", expected, spoken)
                }
            }
        }
    }

    @Test
    fun aSyllableChunkOnALetterAtomSpeaksTheChunk() {
        // Vogel: die Kachel zeigt „gel" am Atom letter-g. Vorher kam „G" — in
        // kuratierter Stimme, also besonders überzeugend falsch.
        assertEquals("gel", SpeechClipText.forWordBlock(pack, "letter-g", "gel"))
        assertEquals("Ster", SpeechClipText.forWordBlock(pack, "letter-st", "Ster"))
    }

    @Test
    fun aTileThatShowsItsOwnAtomKeepsTheCuratedLemma() {
        // Der Normalfall bleibt unberührt: gleiche Kachel wie Atom — auch bei
        // abweichender Groß-/Kleinschreibung — spricht das kuratierte Lemma.
        val sch = requireNotNull(pack.atoms["letter-sch"])
        assertEquals(sch.lemma, SpeechClipText.forWordBlock(pack, "letter-sch", sch.display))
        assertEquals(sch.lemma, SpeechClipText.forWordBlock(pack, "letter-sch", sch.display.lowercase()))
    }
}
