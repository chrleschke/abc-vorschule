package app.abcvorschule.ui.exercise

import kotlin.math.hypot
import kotlin.random.Random

/** One tile's placement in the scatter field: pixel center + a size multiplier
 * (visual variety) + a palette index (rotates through the theme's readable colors). */
data class HuntTilePosition(val x: Float, val y: Float, val scale: Float, val colorIndex: Int)

/**
 * Deterministic scatter placement for the Buchstaben-/Silben-Jagd field (design
 * doc §4 — a deliberate exception to Prinzip 9's "answers at the bottom" default).
 * Same [seed] + [tileCount] + bounds always produce the same layout, so a given
 * round's field is reproducible; a wrong tap advances the seed by one to
 * reshuffle. Retries with a derived seed when tiles land too close together for
 * a small finger to tell them apart.
 */
object SymbolHuntLayout {
    /** Minimum distance between tile centers, as a fraction of the shorter bounds side. */
    const val MinCenterDistanceFraction = 0.22f
    private const val MaxAttempts = 12

    /**
     * Per-tile resample budget inside [place]. Placing all [tileCount] tiles
     * independently and then checking every pair (as a single all-or-nothing draw)
     * makes satisfying [MinCenterDistanceFraction] astronomically unlikely once
     * more than a handful of tiles are in play — 11 tiles at the default fraction
     * essentially never land well-spaced in one draw. Placing tiles one at a time
     * and re-rolling only the tile that collides with what's already down (bounded
     * by this budget) keeps the same deterministic, seed-driven sequence while
     * making success overwhelmingly likely; [scatter]'s outer seed-advance retry
     * remains as the backstop for anything this still misses.
     */
    private const val MaxAttemptsPerTile = 40

    /** Kleiner Atemabstand zwischen Kachelrand und Feldrand, als Anteil der
     * Kachel-Grundgröße — bei 80dp Kacheln also 4dp. */
    private const val EdgePaddingFraction = 0.05f

    /** Skalenband der Kacheln (visuelle Abwechslung, siehe [randomTile]). */
    private const val MinScale = 0.8f
    private const val ScaleSpan = 0.5f

    /**
     * [tileSizePx] ist der Grunddurchmesser einer Kachel vor [HuntTilePosition.scale];
     * die Streuung braucht ihn, weil die zurückgegebenen Punkte Kachel*mittelpunkte*
     * sind. Ohne ihn lag der Rand bei festen 10 % der Feldbreite — schmaler als der
     * Radius der größten Kachel (80dp × 1,3 ÷ 2 = 52dp) auf einem handybreiten Feld,
     * und die äußeren Kacheln wurden am linken/rechten Bildschirmrand abgeschnitten.
     */
    fun scatter(
        seed: Long,
        tileCount: Int,
        boundsWidth: Float,
        boundsHeight: Float,
        tileSizePx: Float,
    ): List<HuntTilePosition> {
        if (tileCount <= 0 || boundsWidth <= 0f || boundsHeight <= 0f) return emptyList()
        val minDistance = minOf(boundsWidth, boundsHeight) * MinCenterDistanceFraction
        var attempt = 0
        var candidate = place(seed, tileCount, boundsWidth, boundsHeight, minDistance, tileSizePx)
        while (attempt < MaxAttempts && !isWellSpaced(candidate, minDistance)) {
            attempt += 1
            candidate = place(seed + attempt, tileCount, boundsWidth, boundsHeight, minDistance, tileSizePx)
        }
        return candidate
    }

    private fun place(
        seed: Long,
        tileCount: Int,
        boundsWidth: Float,
        boundsHeight: Float,
        minDistance: Float,
        tileSizePx: Float,
    ): List<HuntTilePosition> {
        val random = Random(seed)
        val placed = ArrayList<HuntTilePosition>(tileCount)
        for (index in 0 until tileCount) {
            var candidate = randomTile(random, boundsWidth, boundsHeight, tileSizePx, index)
            var attempts = 1
            while (
                attempts < MaxAttemptsPerTile &&
                placed.any { hypot(it.x - candidate.x, it.y - candidate.y) < minDistance }
            ) {
                candidate = randomTile(random, boundsWidth, boundsHeight, tileSizePx, index)
                attempts += 1
            }
            placed.add(candidate)
        }
        return placed
    }

    private fun randomTile(
        random: Random,
        boundsWidth: Float,
        boundsHeight: Float,
        tileSizePx: Float,
        index: Int,
    ): HuntTilePosition {
        // Skala zuerst: der Rand, den diese Kachel braucht, hängt an ihrem eigenen
        // Radius — eine 1,3er-Kachel muss weiter von der Kante weg als eine 0,8er.
        // Gerechnet wird mit dem *aufgeblähten* Radius: unter dem Finger wächst die
        // Kachel um bis zu HuntTileMorph.MaxInflate, und auch gedrückt darf sie
        // nicht über die Feldkante ragen.
        val scale = MinScale + random.nextFloat() * ScaleSpan
        val pressedRadius = tileSizePx * scale / 2f * (1f + HuntTileMorph.MaxInflate)
        val inset = pressedRadius + tileSizePx * EdgePaddingFraction
        return HuntTilePosition(
            x = axisPosition(random, boundsWidth, inset),
            y = axisPosition(random, boundsHeight, inset),
            scale = scale,
            colorIndex = index,
        )
    }

    /**
     * Mittelpunkt auf einer Achse, so gezogen, dass die ganze Kachel im Feld bleibt.
     * Der Zufallswert wird auch dann verbraucht, wenn kein Spielraum bleibt (Kachel
     * breiter als das Feld → mittig), damit die Zufallsfolge — und damit die
     * Reproduzierbarkeit über den Seed — unabhängig von den Feldmaßen ist.
     */
    private fun axisPosition(random: Random, extent: Float, inset: Float): Float {
        val span = extent - 2f * inset
        val roll = random.nextFloat()
        return if (span <= 0f) extent / 2f else inset + roll * span
    }

    private fun isWellSpaced(tiles: List<HuntTilePosition>, minDistance: Float): Boolean {
        for (i in tiles.indices) {
            for (j in i + 1 until tiles.size) {
                if (hypot(tiles[i].x - tiles[j].x, tiles[i].y - tiles[j].y) < minDistance) return false
            }
        }
        return true
    }
}
