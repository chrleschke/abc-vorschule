package app.abcvorschule.content

/**
 * Wie ein Substantiv-Atom beim Erfolgs-Vorsprechen mit Artikel klingt.
 *
 * Einzige Wahrheitsquelle der Regel auf Kotlin-Seite; `tools/tts/ttskit/extract.py`
 * spiegelt sie, damit die vorproduzierten Clips denselben Text tragen.
 *
 * Gibt `null` zurück, wenn das Atom kein Substantiv ist — der Aufrufer bleibt
 * dann bei seinem bisherigen Sprechtext.
 */
object AtomArticleSpeech {

    fun forAtom(atom: Atom?): String? {
        if (atom == null) return null
        atom.articleSpeechOverride?.takeIf { it.isNotBlank() }?.let { return it }
        val nounClass = atom.nounClass ?: return null
        val display = atom.display.takeIf { it.isNotBlank() } ?: return null
        if (nounClass == NounClass.properName) return display
        val gender = atom.gender ?: return null
        return "${article(nounClass, gender)} $display"
    }

    /**
     * Personen bekommen den unbestimmten Artikel — außer im Neutrum: „ein Opa"
     * und „ein Kind" klingen gleich, obwohl das eine maskulin und das andere
     * neutrum ist. Nur „das" trägt das Genus dort eindeutig.
     */
    private fun article(nounClass: NounClass, gender: Gender): String = when {
        nounClass == NounClass.person && gender == Gender.m -> "ein"
        nounClass == NounClass.person && gender == Gender.f -> "eine"
        gender == Gender.m -> "der"
        gender == Gender.f -> "die"
        else -> "das"
    }
}
