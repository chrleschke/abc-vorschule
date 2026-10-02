package app.abcvorschule.ui.exercise

/**
 * Die Wort-Balken auf der Buchseite des Satz-Verstehers (PRODUCT_PRINCIPLES §9/§10,
 * „Leseecke"): ein abgerundeter Balken je Wort des vorgelesenen Satzes, ohne
 * Buchstaben. Das Kind sieht, dass da ein Satz steht und wie lang seine Wörter sind —
 * eine Vorlese-Vorstufe wie die Silhouetten der leeren Pegs im Satz-Architekten —,
 * lesen muss es nichts.
 *
 * Compose-frei, damit das Layout prüfbar ist (`SentenceBarSizingTest`): Wörter →
 * Balkenlängen → Zeilen bei gegebener Seitenbreite. Alle Maße in dp.
 */
object SentenceBarSizing {
    /** Höhe eines Balkens; halbe Höhe ist der Eckradius, die Enden sind rund. */
    const val BarHeightDp = 14f

    /** Abstand zweier Balken in einer Zeile — ein Wortzwischenraum. */
    const val GapDp = 8f

    /**
     * Abstand zwischen zwei Zeilen; mit der Balkenhöhe ergibt das 38dp Zeilenabstand.
     * 18dp (32dp Abstand) standen zu dicht und lasen sich als ein Block statt als Text.
     */
    const val RowGapDp = 24f

    /**
     * Mindestens so viele Zeilen. Eine einzelne Balkenreihe las sich als Leiste, nicht
     * als Text (Nutzerentscheidung Oktober 2026): ein Satz, der in eine Zeile passt,
     * wird trotzdem auf zwei verteilt.
     */
    const val MinRows = 2

    /** Beim Verteilen auf zwei Zeilen trägt die erste etwa so viel — wie im Buch, wo die letzte Zeile kürzer ausläuft. */
    const val FirstRowShare = 0.6f

    /** Länge je Buchstabe. Gewählt nach dem freigegebenen Entwurf (Board „Bilderbuch 1"). */
    const val DpPerCharDp = 12f

    /**
     * Kürzester Balken. Ein „in" oder „zu" bliebe sonst ein Punkt, der neben den
     * anderen nicht mehr als Wort liest.
     */
    const val MinBarDp = 28f

    /**
     * Längster Balken. Ohne Deckel nähme „Schneemann" eine halbe Zeile und drückte
     * den Rest des Satzes in eine dritte; das Verhältnis der Längen bleibt auch
     * geklemmt lesbar (lang ist lang).
     */
    const val MaxBarDp = 96f

    /** Ein Balken: Zeile (ab 0), linke Kante und Länge, links bündig wie Text im Buch. */
    data class Bar(val row: Int, val xDp: Float, val widthDp: Float)

    data class Layout(val bars: List<Bar>, val rows: Int) {
        val heightDp: Float
            get() = if (rows == 0) 0f else rows * BarHeightDp + (rows - 1) * RowGapDp
    }

    /**
     * Die Wörter des Satzes. Satzzeichen zählen nicht: „Ball." ist ein Wort mit vier
     * Buchstaben, und ein freistehender Gedankenstrich ist gar keins. Was nach dem
     * Abzug leer bleibt, fällt weg.
     */
    fun words(sentence: String): List<String> =
        sentence.split(Whitespace)
            .map { token -> token.filter { it.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }

    /** Länge des Balkens für [word], geklemmt — und nie breiter als die Seite. */
    fun barWidthDp(word: String, pageWidthDp: Float): Float =
        (word.length * DpPerCharDp)
            .coerceIn(MinBarDp, MaxBarDp)
            .coerceAtMost(pageWidthDp.coerceAtLeast(0f))

    /**
     * Bricht die Balken greedy in Zeilen um: ein Balken, der nicht mehr in die Zeile
     * passt, beginnt die nächste. Kein Balken ragt über [pageWidthDp]; ein einzelnes
     * Wort, das allein schon breiter wäre als die Seite, wird auf sie gekürzt.
     *
     * Passt alles in eine Zeile, wird auf [MinRows] Zeilen verteilt: die erste nimmt
     * die Wörter, bis sie etwa [FirstRowShare] der Gesamtlänge trägt (mindestens eines),
     * der Rest geht in die zweite. Ein Satz aus nur einem Wort bleibt eine Zeile — einen
     * erfundenen Balken gibt es nicht, ein Balken ist immer ein Wort.
     */
    fun layout(sentence: String, pageWidthDp: Float): Layout {
        val words = words(sentence)
        if (words.isEmpty() || pageWidthDp <= 0f) return Layout(emptyList(), 0)
        val widths = words.map { barWidthDp(it, pageWidthDp) }
        val greedy = place(widths) { _, x, width -> x > 0f && x + width > pageWidthDp }
        if (greedy.rows >= MinRows || widths.size < 2) return greedy
        val total = widths.sum()
        var firstRowWords = 1
        var acc = widths[0]
        while (firstRowWords < widths.size - 1 && acc + widths[firstRowWords] <= total * FirstRowShare) {
            acc += widths[firstRowWords]
            firstRowWords++
        }
        return place(widths) { index, _, _ -> index == firstRowWords }
    }

    /** Legt die Balken der Reihe nach; [breakBefore] entscheidet, wo eine neue Zeile beginnt. */
    private inline fun place(widths: List<Float>, breakBefore: (index: Int, x: Float, width: Float) -> Boolean): Layout {
        val bars = ArrayList<Bar>(widths.size)
        var row = 0
        var x = 0f
        widths.forEachIndexed { index, width ->
            if (index > 0 && breakBefore(index, x, width)) {
                row += 1
                x = 0f
            }
            bars += Bar(row = row, xDp = x, widthDp = width)
            x += width + GapDp
        }
        return Layout(bars, row + 1)
    }

    private val Whitespace = Regex("\\s+")
}
