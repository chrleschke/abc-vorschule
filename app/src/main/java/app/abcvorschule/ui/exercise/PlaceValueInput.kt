package app.abcvorschule.ui.exercise

/**
 * Eingaberegeln der Stellenwert-Antwort: ein Feld für die Zehner, eines für die
 * Einer. Sie beantworten die Frage, an der ein Kind bei „20" hängenbleibt —
 * *welche Ziffer kommt zuerst?* — indem die Felder in denselben Spalten stehen
 * wie die Ziffern der Aufgabe darüber.
 *
 * Compose-frei, damit die Regeln als JVM-Test prüfbar bleiben — wie
 * [NumberPadInput], das weiterhin das einzelne Feld von Minus und Malnehmen
 * trägt.
 */
object PlaceValueInput {
    /** Ab diesem Ergebnis gibt es ein Zehnerfeld. */
    const val TensFrom = 10

    /** Die bisherige Feldbreite als Boden — dieselbe Rolle wie
     * [NumberPadInput.BaseFieldWidthDp], nur für eine einzelne Ziffer. */
    const val MinSlotWidthDp = 64f

    /** Vorschub einer Ziffer als Anteil der Schriftgröße —
     * [NumberPadInput.DigitAspect], damit beide Felder gleich rechnen. */
    const val DigitAspect = 0.6f

    /** Innenabstand eines Ziffernkastens, in dp. */
    const val SlotPaddingDp = 24f

    /**
     * Wie viele Ziffernfelder die Antwort bekommt. Das verrät die Größenordnung
     * der Antwort — und darf es: die Objekte stehen alle im Bild, das Kind sieht
     * ohnehin, dass es mehr als neun sind. Die Felderzahl folgt nur dem Bild.
     */
    fun fieldCount(answer: Int): Int = if (answer >= TensFrom) 2 else 1

    /**
     * Ein Feld hält genau eine Ziffer, und die zuletzt getippte gewinnt. Damit
     * überschreibt ein Tipp ins gefüllte Feld den alten Wert, statt sich
     * anzuhängen — ein Vorschulkind soll nicht erst löschen müssen.
     */
    fun lastDigit(raw: String): String = raw.filter(Char::isDigit).takeLast(1)

    /**
     * Beide Felder zusammen als Zahl; `null`, solange die Eingabe nach
     * [isComplete] nicht vollständig ist. Die Feldzahl gehört mit hinein, sonst
     * beantworten [combine] und [isComplete] dieselbe Frage verschieden: bei
     * einem einzigen Feld ist ein Rest im Zehnerfeld kein Zehner, sondern Müll
     * aus einer früheren Runde.
     */
    fun combine(tens: String, ones: String, fieldCount: Int): Int? {
        if (!isComplete(tens, ones, fieldCount)) return null
        return (if (fieldCount == 1) ones else "$tens$ones").toIntOrNull()
    }

    fun isComplete(tens: String, ones: String, fieldCount: Int): Boolean =
        ones.isNotEmpty() && (fieldCount == 1 || tens.isNotEmpty())

    /** Eine fertige Zahl zurück in ihre Felder — für das Auflösen. */
    fun digitsOf(value: Int, fieldCount: Int): Pair<String, String> =
        if (fieldCount == 1) "" to value.toString() else (value / 10).toString() to (value % 10).toString()

    /**
     * Breite eines Ziffernkastens aus der *effektiven* Textgröße. Gleiche
     * Begründung wie [NumberPadInput.fieldWidthDp]: ein Kind, das seine getippte
     * Zahl nicht sieht, kann sie nicht prüfen.
     */
    fun slotWidthDp(textSp: Float, fontScale: Float): Float =
        (textSp * fontScale * DigitAspect + SlotPaddingDp).coerceAtLeast(MinSlotWidthDp)

    /** Wechselt bei neuer Runde und bei jedem Fehlversuch — und leert damit die
     * Felder, genau wie [NumberPadInput.resetToken] es für Minus tut. */
    fun resetToken(roundKey: String, misses: Int): String = "$roundKey#$misses"
}
