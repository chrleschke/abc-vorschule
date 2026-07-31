package app.abcvorschule.ui.exercise

/**
 * When "Zeig mir" appears in the two symbol-hunting trainers: after this many
 * *consecutive* misses, reset by any correct tap. Shared so the two trainers
 * cannot drift apart — a child who learns the button appears "after a while"
 * should meet the same patience everywhere.
 *
 * Deliberately not shared with the Spurensucher, which counts cumulative
 * off-road excursions rather than consecutive misses and only reuses the number.
 */
object ResolveGate {
    const val Threshold = 6
}
