package app.abcvorschule.ui.world

import kotlin.math.PI
import kotlin.math.sin

/**
 * Die Lichtstrahlen und Blasen der Tiefsee als Rechnung — Compose-frei und damit
 * testbar (PRODUCT_PRINCIPLES §10, „Nachtwelten").
 *
 * Jeder Strahl hängt an vier langsamen Sinuswellen mit eigenen Perioden (19–47 s) und
 * Phasen: Winkel, Breite, Länge, Helligkeit — dazu der **Lichtkern**, die hellste
 * Stelle im Querschnitt, die langsam von einer Kante zur anderen wandert. Weil sich die
 * Perioden nicht teilen, wiederholt sich das Bild praktisch nie, und benachbarte Strahlen
 * überlagern sich mal stärker, mal schwächer — wie Licht unter einer leicht bewegten
 * Wasseroberfläche. Alles ist langsam und kontrastarm: die Welt atmet, der Inhalt steht.
 */
object DeepSeaLight {
    const val RayCount = 7

    /** Kürzeste und längste Periode der Strahl-Wellen, in Sekunden. */
    const val MinPeriodS = 19f
    const val MaxPeriodS = 47f

    /** Blasen steigen 0,4–1 % der Höhe pro Sekunde — so ruhig wie die Pfad-Laternen. */
    const val MinBubbleRise = 0.004f
    const val MaxBubbleRise = 0.010f
    const val BubbleCount = 22

    data class Ray(
        /** Ursprung an der Oberkante, Anteil der Breite. */
        val x: Float,
        /** Neigung gegen die Senkrechte in Grad (nach rechts unten positiv). */
        val angleDeg: Float,
        /** Halbe Breite am Ursprung, Anteil der Breite. */
        val halfWidth: Float,
        /** Länge, Anteil der Höhe. */
        val length: Float,
        /** Deckkraft des Lichtkerns. */
        val alpha: Float,
        /** Lage des Lichtkerns im Querschnitt, 0 = linke Kante, 1 = rechte Kante. */
        val core: Float,
    )

    data class Bubble(val x: Float, val y: Float, val radiusDp: Float)

    private class Seed(val x: Float, val periods: FloatArray, val phases: FloatArray)

    private val seeds: List<Seed> = run {
        val rnd = java.util.Random(777)
        List(RayCount) { i ->
            Seed(
                x = 0.12f + i * 0.13f + rnd.nextFloat() * 0.05f,
                periods = FloatArray(5) { MinPeriodS + rnd.nextFloat() * (MaxPeriodS - MinPeriodS) },
                phases = FloatArray(5) { rnd.nextFloat() * 2f * PI.toFloat() },
            )
        }
    }

    private fun wave(seed: Seed, k: Int, t: Float): Float =
        sin(t / seed.periods[k] * 2f * PI.toFloat() + seed.phases[k])

    /** Alle Strahlen zum Zeitpunkt [t] (Sekunden). */
    fun rays(t: Float): List<Ray> = seeds.map { s ->
        Ray(
            x = s.x + wave(s, 1, t * 0.6f) * 0.02f,
            angleDeg = 12f + wave(s, 0, t) * 5f,
            halfWidth = 0.018f + (wave(s, 1, t) + 1f) * 0.012f,
            length = 0.5f + (wave(s, 2, t) + 1f) * 0.14f,
            alpha = 0.07f + (wave(s, 3, t) + 1f) * 0.045f,
            core = 0.5f + wave(s, 4, t) * 0.32f,
        )
    }

    private val bubbleSeeds = run {
        val rnd = java.util.Random(211)
        List(BubbleCount) {
            floatArrayOf(
                rnd.nextFloat(),
                rnd.nextFloat(),
                0.8f + rnd.nextFloat() * 2.6f,
                MinBubbleRise + rnd.nextFloat() * (MaxBubbleRise - MinBubbleRise),
                rnd.nextFloat() * 6.28f,
            )
        }
    }

    /**
     * Die Blasen zum Zeitpunkt [t]. Aus der Zeit gerechnet statt schrittweise
     * verschoben: kein Zustand, der über Frames driftet, und das Standbild bei
     * „Bewegung reduzieren" (t = 0) ist dasselbe wie der erste Frame.
     */
    fun bubbles(t: Float): List<Bubble> = bubbleSeeds.map { (x, y0, r, rise, ph) ->
        val travelled = (y0 + 1.04f - t * rise).mod(1.04f) - 0.02f
        Bubble(x = x + sin(t * 0.6f + ph) * 0.006f, y = travelled, radiusDp = r)
    }
}
