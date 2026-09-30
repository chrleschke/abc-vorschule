package app.abcvorschule.ui.exercise

import app.abcvorschule.ui.theme.AbcMotion
import kotlin.math.PI
import kotlin.math.sin

/**
 * Das Neu-Mischen der Jagd als Bewegung statt als Teleport (PRODUCT_PRINCIPLES §3/§10).
 *
 * Gemischt wird nach jedem Fehltipp **mit Absicht**: ohne das tippt ein Kind einfach
 * alle Kugeln der Reihe nach an, und die Jagd misst nichts mehr. Bis September 2026
 * sprangen die Kugeln dabei im selben Frame an ihre neuen Plätze — das Feld sah aus,
 * als sei es neu geladen, und das Kind verstand nicht, was passiert war.
 *
 * Jetzt **hüpft** jede Kugel in einem kleinen Bogen an ihren neuen Platz, leicht
 * versetzt nach ihrer Nummer, damit es nach Durcheinanderwirbeln aussieht und nicht
 * nach einem starren Verschieben. Während das Feld fliegt, nimmt es keine Tipps an
 * ([LockMs]): die Bewegung ist zugleich die Bremse gegen Durchtippen, ohne dass es
 * eine Strafe gibt — kein Punkt geht verloren, nichts wird rot.
 *
 * Compose-frei und damit als Rechnung testbar, gleiche Bauart wie [HuntTileMorph].
 */
object HuntShuffleHop {
    /** Flugdauer einer Kugel. */
    const val FlightMs = AbcMotion.LongMs

    /** Versatz je Kugel — sechs bis acht Kugeln starten so binnen ~200 ms. */
    const val StaggerMs = 25

    /** Höchster Versatz; darüber wartet eine späte Kugel spürbar auf ihren Start. */
    const val MaxStaggerMs = 200

    /** Scheitelhöhe des Bogens als Anteil der Kugelgröße. */
    const val HopHeightFraction = 0.35f

    /**
     * Wie lange das Feld nach einem Fehltipp gesperrt ist: bis die letzte Kugel
     * gelandet ist.
     */
    const val LockMs = FlightMs + MaxStaggerMs

    fun staggerMs(instanceId: Int): Int = (instanceId * StaggerMs).coerceAtMost(MaxStaggerMs)

    /** Waagerecht linear von [fromX] nach [toX]. */
    fun x(fromX: Float, toX: Float, t: Float): Float = lerp(fromX, toX, t)

    /**
     * Senkrecht linear plus Bogen: in der Mitte des Flugs [hopPx] über der Geraden,
     * an Start und Ziel exakt auf ihr (y wächst nach unten, der Bogen geht nach oben).
     */
    fun y(fromY: Float, toY: Float, t: Float, hopPx: Float): Float =
        lerp(fromY, toY, t) - hopPx * sin(PI.toFloat() * t.coerceIn(0f, 1f))

    /** Die Streugröße einer Kugel wechselt beim Mischen mit — sie wächst mit. */
    fun scale(fromScale: Float, toScale: Float, t: Float): Float = lerp(fromScale, toScale, t)

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
}
