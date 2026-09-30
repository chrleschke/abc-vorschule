package app.abcvorschule.ui.exercise

/**
 * Was der Rechen-Trainer über einen Versuch meldet. Eigener Typ statt Lambda-Parametern:
 * `resolved` und `correct` sind beides Booleans, und gleich aussehende Positionen
 * hintereinander sind eine Fehlerquelle.
 */
data class MathAttempt(
    val resolved: Boolean,
    val correct: Boolean,
)
