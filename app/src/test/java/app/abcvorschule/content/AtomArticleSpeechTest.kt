package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AtomArticleSpeechTest {

    private fun atom(
        display: String,
        gender: Gender? = null,
        nounClass: NounClass? = null,
        override: String? = null,
    ) = Atom(
        id = display.lowercase(),
        lemma = display,
        display = display,
        emoji = "",
        kind = AtomKind.word,
        gender = gender,
        nounClass = nounClass,
        articleSpeechOverride = override,
    )

    @Test
    fun `thing takes the definite article`() {
        assertEquals("das Haus", AtomArticleSpeech.forAtom(atom("Haus", Gender.n, NounClass.thing)))
        assertEquals("die Maus", AtomArticleSpeech.forAtom(atom("Maus", Gender.f, NounClass.thing)))
        assertEquals("der Baum", AtomArticleSpeech.forAtom(atom("Baum", Gender.m, NounClass.thing)))
    }

    @Test
    fun `person takes the indefinite article for m and f`() {
        assertEquals("eine Oma", AtomArticleSpeech.forAtom(atom("Oma", Gender.f, NounClass.person)))
        assertEquals("ein Opa", AtomArticleSpeech.forAtom(atom("Opa", Gender.m, NounClass.person)))
    }

    @Test
    fun `neuter person takes the definite article`() {
        // "ein Opa" und "ein Kind" klingen gleich — beim Neutrum trägt nur "das"
        // das Genus eindeutig. Siehe Spec, Abschnitt 1.
        assertEquals("das Kind", AtomArticleSpeech.forAtom(atom("Kind", Gender.n, NounClass.person)))
    }

    @Test
    fun `a name is spoken bare`() {
        assertEquals("Tom", AtomArticleSpeech.forAtom(atom("Tom", nounClass = NounClass.properName)))
    }

    @Test
    fun `the override wins over the derived form`() {
        val haeuser = atom("Häuser", Gender.n, NounClass.thing, override = "die Häuser")
        assertEquals("die Häuser", AtomArticleSpeech.forAtom(haeuser))
    }

    // Die folgenden drei Fälle spiegeln test_article_speech_mirrors_the_kotlin_rule in
    // tools/tts/tests/test_extract.py wörtlich — Python pinnt dieselben Randfälle mit
    // Verweis auf Kotlin-Verhalten. Beide Seiten müssen zusammen geändert werden.

    @Test
    fun `the override is passed through untrimmed`() {
        // takeIf { it.isNotBlank() } filtert nur — es trimmt nicht.
        val haeuser = atom("Häuser", Gender.n, NounClass.thing, override = " die Häuser ")
        assertEquals(" die Häuser ", AtomArticleSpeech.forAtom(haeuser))
    }

    @Test
    fun `a blank override is no override`() {
        // Nur-Leerzeichen ist blank, also kein Override — Fallback auf die Ableitung.
        val kind = atom("Kind", Gender.n, NounClass.person, override = "   ")
        assertEquals("das Kind", AtomArticleSpeech.forAtom(kind))
    }

    @Test
    fun `display is not trimmed in the composed speech`() {
        // display selbst wird nicht getrimmt: Rand-Leerzeichen bleiben erhalten,
        // daher das doppelte Leerzeichen zwischen Artikel und display.
        val haus = atom(" Haus ", Gender.n, NounClass.thing)
        assertEquals("das  Haus ", AtomArticleSpeech.forAtom(haus))
    }

    @Test
    fun `an unclassified atom has no article speech`() {
        assertNull(AtomArticleSpeech.forAtom(atom("ist")))
        assertNull(AtomArticleSpeech.forAtom(null))
    }

    @Test
    fun `a classified atom without gender has no article speech`() {
        // Der Validator verhindert diesen Zustand im Pack; die Ableitung
        // darf daran trotzdem nicht abstürzen oder "null Haus" liefern.
        assertNull(AtomArticleSpeech.forAtom(atom("Haus", gender = null, nounClass = NounClass.thing)))
    }

    @Test
    fun `the shipped pack derives the expected article forms`() {
        val pack = ContentRepository.fromClasspath().load()
        fun speech(id: String) = AtomArticleSpeech.forAtom(pack.atoms[id])

        assertEquals("das Haus", speech("haus"))
        assertEquals("die Maus", speech("maus"))
        assertEquals("der Baum", speech("baum"))
        assertEquals("das Lama", speech("lama"))
        assertEquals("das Pony", speech("pony"))
        assertEquals("die Häuser", speech("haeusser"))
        assertEquals("die Bäume", speech("baeume"))
        assertEquals("eine Oma", speech("oma"))
        assertEquals("ein Opa", speech("opa"))
        assertEquals("Tom", speech("tom"))
        assertNull(speech("ich"))
        assertNull(speech("letter-m"))
    }
}
