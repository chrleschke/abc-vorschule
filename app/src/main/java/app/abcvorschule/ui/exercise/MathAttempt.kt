package app.abcvorschule.ui.exercise

/**
 * Was der Rechen-Trainer über einen Versuch meldet. Eigener Typ statt Lambda-Parametern:
 * `resolved` und `correct` sind beides Booleans, und gleich aussehende Positionen
 * hintereinander sind eine Fehlerquelle.
 */
data class MathAttempt(
    /** Abstand zur richtigen Antwort; `null`, wenn der Versuch keine Zahl trug. */
    val distance: Int?,
    val resolved: Boolean,
    val correct: Boolean,
    val guess: Int?,
)
