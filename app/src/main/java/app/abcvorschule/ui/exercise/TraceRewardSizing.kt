package app.abcvorschule.ui.exercise

/**
 * Maße der Belohnungskarte des Spurensuchers, Compose-frei. Rangfolge, wenn der Platz
 * nicht reicht: (1) die Wortzeile („A wie Ampel") ist ganz zu sehen und bricht nie mitten
 * im Wort um, (2) das Bild nimmt, was an Höhe übrig bleibt. Das Bild ist Belohnung, die
 * Wortzeile ist das, was der Trainer lehrt — schrumpfen muss also zuerst das Bild.
 *
 * Gemessen wird in der Karte selbst (`TextMeasurer`), nicht geschätzt: Zeilenumbruch,
 * Schrift-Metrik und nichtlineare Systemschriftgröße kennt nur das echte Layout.
 */
object TraceRewardSizing {
    const val EmojiSp = 96f
    const val PaddingHorizontalDp = 32f
    const val PaddingVerticalDp = 24f
    const val GapDp = 16f

    /**
     * Kleinster Faktor, den Bild oder Wort bekommen. Ein Emoji unter einem Viertel
     * seiner Größe (24sp) ist kein Belohnungsbild mehr, dann fällt es weg — die
     * Wortzeile bleibt.
     */
    const val MinScale = 0.25f

    /** Genauigkeit der Suche; ein Prozent der Schriftgröße sieht niemand. */
    private const val Precision = 0.01f

    /**
     * Größter Faktor in [[MinScale], 1], für den [fits] gilt — `null`, wenn nicht einmal
     * [MinScale] passt. [fits] muss monoton sein (was bei einem Faktor passt, passt
     * auch bei jedem kleineren); für Schriftgrößen gegen eine feste Box gilt das.
     */
    fun largestFitting(fits: (Float) -> Boolean): Float? {
        if (fits(1f)) return 1f
        if (!fits(MinScale)) return null
        var low = MinScale
        var high = 1f
        while (high - low > Precision) {
            val mid = (low + high) / 2f
            if (fits(mid)) low = mid else high = mid
        }
        return low
    }
}
