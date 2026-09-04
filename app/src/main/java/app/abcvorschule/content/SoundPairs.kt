package app.abcvorschule.content

/**
 * Kuratiertes Wissen für den Laut-Fresser (design doc §3/§4): welche Lautpaare
 * Vorschulkinder verwechseln, in welcher Stufe sie gespielt werden, und wie ein
 * geschriebenes Wort für das *Hören* zerlegt wird. Bewusst nicht aus dem Pack
 * abgeleitet — welche Laute sich ähnlich anhören, steht nirgends im Content.
 */
enum class SoundPairTier {
    /** Echte Verwechslungen (Fox-Boyer, H-LAD). Der Kern des Trainers. */
    Contrast,

    /** Leichte Kontraste für L03–L07, damit das Kind Monster und Geste kennt, bevor
     * das erste schwere Paar kommt. */
    WarmUp,

    /** Vokale sind der Silbenkern und überall hörbar — sie werden nicht am Anlaut,
     * sondern irgendwo im Wort gesucht. */
    Vowel,
}

/** Grapheme als [Atom.display] der Buchstaben-Atome (`S`, `Sch`, `Ei`). */
data class SoundPair(val left: String, val right: String, val tier: SoundPairTier) {
    val anywhere: Boolean get() = tier == SoundPairTier.Vowel
    val graphemes: List<String> get() = listOf(left, right)
    override fun toString(): String = "$left/$right"
}

object SoundPairs {
    val table: List<SoundPair> = listOf(
        SoundPair("S", "Sch", SoundPairTier.Contrast),
        SoundPair("S", "Z", SoundPairTier.Contrast),
        SoundPair("F", "W", SoundPairTier.Contrast),
        SoundPair("W", "B", SoundPairTier.Contrast),
        SoundPair("B", "P", SoundPairTier.Contrast),
        SoundPair("D", "T", SoundPairTier.Contrast),
        SoundPair("G", "K", SoundPairTier.Contrast),
        SoundPair("K", "T", SoundPairTier.Contrast),
        SoundPair("M", "N", SoundPairTier.Contrast),
        SoundPair("L", "R", SoundPairTier.Contrast),
        SoundPair("St", "Sp", SoundPairTier.Contrast),
        SoundPair("Pf", "F", SoundPairTier.Contrast),
        SoundPair("P", "T", SoundPairTier.WarmUp),
        SoundPair("L", "H", SoundPairTier.WarmUp),
        SoundPair("F", "T", SoundPairTier.WarmUp),
        SoundPair("S", "T", SoundPairTier.WarmUp),
        SoundPair("Ei", "Au", SoundPairTier.Vowel),
        SoundPair("Ei", "Eu", SoundPairTier.Vowel),
        SoundPair("Ö", "Ü", SoundPairTier.Vowel),
        SoundPair("I", "O", SoundPairTier.Vowel),
    )

    /** „Giraffe" schwankt zwischen [g] und [ʒ] — der Buchstabe auf dem Bauch darf
     * nichts versprechen, was das Ohr nicht hält. */
    val excludedWords: Set<String> = setOf("Giraffe")

    /** Mehrzeichen-Einheiten, längste zuerst; unabhängig von der Lektion, weil hier
     * gehört und nicht gelesen wird (anders als [WordGraphemes]). */
    val units: List<String> = listOf("sch", "äu", "ei", "au", "eu", "ie", "ch", "ck", "pf", "qu", "st", "sp", "ß")

    /** Kleingeschriebene Segmente, longest-match-first. */
    fun segments(word: String): List<String> {
        val lower = word.lowercase()
        val result = mutableListOf<String>()
        var index = 0
        while (index < lower.length) {
            val unit = units.firstOrNull { lower.startsWith(it, index) }
            if (unit == null) {
                result += lower[index].toString()
                index += 1
            } else {
                result += unit
                index += unit.length
            }
        }
        return result
    }

    fun startsWith(word: String, grapheme: String): Boolean =
        segments(word).firstOrNull() == grapheme.lowercase()

    /**
     * Kommt der Laut irgendwo im Wort vor? `S` schließt `ß` (und damit `ss`) ein und
     * ein `st`/`sp`, das **nicht** am Wortanfang steht — dort ist es [ʃt]/[ʃp]
     * („Stern"), mitten im Wort aber [st] („Zahnbürste"). `I` schließt das lange `ie`
     * ein. Diphthonge sind eigene Segmente, also ist das `u` in „Feuer" kein U, und
     * `sch`/`pf`/`ch`/`ck` enthalten weder S noch P noch C.
     */
    fun contains(word: String, grapheme: String): Boolean {
        val segs = segments(word)
        return when (grapheme.lowercase()) {
            "s" -> segs.any { it == "s" || it == "ß" } || segs.drop(1).any { it == "st" || it == "sp" }
            "i" -> segs.any { it == "i" || it == "ie" }
            else -> segs.contains(grapheme.lowercase())
        }
    }

    /** Das Wort ohne sein Anlaut-Segment — gleicher Rest heißt Minimalpaar. */
    fun rest(word: String): String = segments(word).drop(1).joinToString("")

    /**
     * Wie viele Bilder ein Kind sieht. Varianten-Selektoren, Hauttöne und
     * Tastenkappen zählen nicht; ein Zeichen hinter einem ZWJ gehört zum
     * vorigen Bild (🐻‍❄️ ist ein Eisbär, keine zwei Bilder). `BreakIterator` ist
     * hier keine Option — die JVM-Variante der Unit-Tests kennt keine erweiterten
     * Grapheme-Cluster.
     */
    fun emojiClusterCount(emoji: String): Int {
        var count = 0
        var previous = -1
        emoji.codePoints().forEach { cp ->
            val modifier = cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3 ||
                cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F
            if (!modifier && previous != 0x200D) count += 1
            previous = cp
        }
        return count
    }

    /** Substantiv mit genau einem Bild, nicht ausgeschlossen (design doc §4). */
    fun isCardWorthy(atom: Atom): Boolean =
        (atom.kind == AtomKind.word || atom.kind == AtomKind.other) &&
            atom.nounClass != null &&
            emojiClusterCount(atom.emoji) == 1 &&
            atom.display !in excludedWords

    fun letterAtom(pack: ContentPack, grapheme: String): Atom? =
        pack.atoms.values.firstOrNull { it.kind == AtomKind.letter && it.display == grapheme }

    /** Index der ersten Lektion, die das Graphem als Fokus führt; null = nie. */
    fun introductionIndex(pack: ContentPack, grapheme: String): Int? =
        pack.lessons
            .filter { lesson -> lesson.focusAtomIds.any { pack.atoms[it]?.display == grapheme } }
            .minOfOrNull { it.index }
}
