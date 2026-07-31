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

/**
 * How long a finished round celebrates before it hands off to the shared success
 * pipeline. Covers the Jagd's 400 ms field fade plus roughly one pulse of the
 * golden battery, and the Detektiv's golden pulse of its strokes, so the child
 * sees the win land instead of the screen cutting away mid-animation. Mirrors
 * LetterTraceTrainer's RewardHoldMs, which solves the same problem.
 *
 * Shared for the same reason as [ResolveGate.Threshold]: both hunts must feel the
 * same, and two private copies with "must match the other" comments already drifted
 * once waiting to happen.
 */
object HuntCelebration {
    const val HoldMs = 900L
}
