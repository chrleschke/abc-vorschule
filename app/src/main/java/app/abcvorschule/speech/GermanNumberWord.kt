package app.abcvorschule.speech

/**
 * Zahlen als ausgeschriebene Wörter für alles, was gesprochen wird.
 *
 * Der Grund ist kein Stil, sondern ein Bug: im Deutschen **ist** „8." die
 * Schreibweise der Ordinalzahl, und jede TTS-Engine liest sie folgerichtig als
 * „achte". Das Rechnen sprach seine Zahlen als `"$n."` — und damit beim
 * Miss-Echo und in der Zähl-Hilfe durchweg Ordinalzahlen vor.
 *
 * Daraus die Regel für allen gesprochenen Text: **nach einer Ziffer nie ein
 * Punkt.** Wo ein Satzende gebraucht wird, trennt ein Komma.
 *
 * Der Zahlenraum des Lehrplans endet bei 30 (`ContentValidator.MaxMathQuantity`);
 * darüber hinaus — eine getippte Antwort darf bis zu drei Ziffern haben — bleibt
 * die Ziffernform, die dann als Kardinalzahl gelesen wird, solange kein Punkt
 * folgt.
 */
object GermanNumberWord {
    private val words = listOf(
        "null", "eins", "zwei", "drei", "vier", "fünf", "sechs", "sieben", "acht",
        "neun", "zehn", "elf", "zwölf", "dreizehn", "vierzehn", "fünfzehn",
        "sechzehn", "siebzehn", "achtzehn", "neunzehn", "zwanzig",
        "einundzwanzig", "zweiundzwanzig", "dreiundzwanzig", "vierundzwanzig",
        "fünfundzwanzig", "sechsundzwanzig", "siebenundzwanzig",
        "achtundzwanzig", "neunundzwanzig", "dreißig",
    )

    /** Größte Zahl, für die ein Wort existiert — der Deckel des Lehrplans. */
    val MaxWord: Int = words.lastIndex

    fun of(value: Int): String = words.getOrElse(value) { value.toString() }
}
