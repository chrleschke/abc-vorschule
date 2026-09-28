package app.abcvorschule.ui.path

import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * Die Himmelslaternen über dem Pfad (PRODUCT_PRINCIPLES §5, „Laternen") — reine
 * Stimmung, ohne Aufgabe. Compose-frei und damit als Rechnung testbar.
 *
 * Laternen steigen **ständig neu** hinter den Hügeln auf: im Mittel alle [MeanIntervalS]
 * Sekunden eine, jede braucht 90–150 s bis über den oberen Rand und pendelt dabei leicht.
 * Die Entfernung steckt in einer Zahl `depth` (0 = fern, 1 = nah): ferne Laternen sind
 * kleiner, langsamer, blasser und leuchten schwächer.
 *
 * Aus der Zeit gerechnet statt als Liste mitgeführt: Laterne `i` startet zu einem
 * festen Zeitpunkt, und zu jeder Zeit `t` sind genau die Laternen am Himmel, deren
 * Aufstieg gerade läuft. Kein Zustand, der driftet — und das Standbild bei „Bewegung
 * reduzieren" ist ein voller Himmel, kein leerer.
 */
object SkyLanterns {
    const val MeanIntervalS = 10f
    const val IntervalJitterS = 3f
    const val MinRiseS = 90f
    const val MaxRiseS = 150f

    /** Die Uhr startet so weit „in der Vergangenheit", dass beim Öffnen schon Laternen oben sind. */
    const val PrefillS = MaxRiseS

    /** Start hinter den Hügeln (Anteil der Höhe) und Ende über dem oberen Rand. */
    const val StartY = 0.72f
    const val EndY = -0.08f

    data class Lantern(
        val x: Float,
        val y: Float,
        /** Breite als Anteil der Bildschirmbreite. */
        val width: Float,
        val rotationDeg: Float,
        val alpha: Float,
        val depth: Float,
    )

    private fun hash(i: Int, salt: Int): Float {
        var h = i * 374761393 + salt * 668265263
        h = (h xor (h ushr 13)) * 1274126177
        return ((h xor (h ushr 16)) and 0xFFFFFF) / 16777215f
    }

    private fun startOf(i: Int): Float = i * MeanIntervalS + (hash(i, 1) - 0.5f) * 2f * IntervalJitterS
    private fun depthOf(i: Int): Float = hash(i, 2)
    private fun riseOf(i: Int): Float = MaxRiseS - depthOf(i) * (MaxRiseS - MinRiseS)

    /** Alle Laternen am Himmel zur Zeit [seconds] (ab Öffnen des Screens). */
    fun at(seconds: Float): List<Lantern> {
        val t = seconds + PrefillS
        val first = floor((t - MaxRiseS - IntervalJitterS) / MeanIntervalS).toInt() - 1
        val last = floor((t + IntervalJitterS) / MeanIntervalS).toInt() + 1
        return (first..last).mapNotNull { i ->
            val progress = (t - startOf(i)) / riseOf(i)
            if (progress !in 0f..1f) return@mapNotNull null
            val depth = depthOf(i)
            val swayPeriod = 9f + hash(i, 3) * 6f
            val sway = sin(t / swayPeriod * 2f * PI.toFloat() + hash(i, 4) * 6.28f)
            val width = 0.03f + depth * 0.065f
            Lantern(
                x = 0.06f + hash(i, 5) * 0.88f + sway * width * 0.4f,
                y = StartY + (EndY - StartY) * progress,
                width = width,
                rotationDeg = sway * (3f + depth * 5f),
                // Oben blenden sie aus, statt am Rand abgeschnitten zu werden.
                alpha = (0.45f + depth * 0.55f) * ((1f - progress) / 0.15f).coerceAtMost(1f),
                depth = depth,
            )
        }.sortedBy { it.depth }
    }
}
