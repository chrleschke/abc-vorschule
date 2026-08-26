package app.abcvorschule.ui.exercise

/**
 * Was der Rechen-Trainer über einen Versuch meldet.
 *
 * Eigener Typ statt einer weiteren Stelle in einer Lambda-Signatur: die beiden
 * Zähl-Hilfe-Flags sind Booleans wie `resolved` und `correct` auch, und vier
 * gleich aussehende Positionen hintereinander sind eine Fehlerquelle.
 */
data class MathAttempt(
    /** Abstand zur richtigen Antwort; `null`, wenn der Versuch keine Zahl trug. */
    val distance: Int?,
    val resolved: Boolean,
    val correct: Boolean,
    val guess: Int?,
    /**
     * Die Zähl-Hilfe war offen. Die Antwort ist dann **erarbeitet, nicht gewusst** —
     * ein Lobsatz dafür wäre hohl und würde dem Kind beibringen, dass beides
     * dasselbe ist. Punkte gibt es weiterhin: sie wegzunehmen wäre eine Strafe,
     * und Strafen kennt die App nicht (§8).
     */
    val aided: Boolean,
    /**
     * Dieser Fehlversuch hat die Zähl-Hilfe gerade aufgeklappt. Dann tritt die
     * Zählanweisung an die Stelle des allgemeinen Miss-Hinweises: „probier es noch
     * mal" ist in dem Moment die falsche Auskunft, weil sich die Aufgabe gerade in
     * etwas anderes verwandelt hat.
     */
    val opensAid: Boolean,
)
