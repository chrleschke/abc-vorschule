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

    fun scatter(seed: Long, tileCount: Int, boundsWidth: Float, boundsHeight: Float): List<HuntTilePosition> {
        if (tileCount <= 0 || boundsWidth <= 0f || boundsHeight <= 0f) return emptyList()
        val minDistance = minOf(boundsWidth, boundsHeight) * MinCenterDistanceFraction
        var attempt = 0
        var candidate = place(seed, tileCount, boundsWidth, boundsHeight, minDistance)
        while (attempt < MaxAttempts && !isWellSpaced(candidate, minDistance)) {
            attempt += 1
            candidate = place(seed + attempt, tileCount, boundsWidth, boundsHeight, minDistance)
        }
        return candidate
    }

    private fun place(
        seed: Long,
        tileCount: Int,
        boundsWidth: Float,
        boundsHeight: Float,
        minDistance: Float,
    ): List<HuntTilePosition> {
        val random = Random(seed)
        val placed = ArrayList<HuntTilePosition>(tileCount)
        for (index in 0 until tileCount) {
            var candidate = randomTile(random, boundsWidth, boundsHeight, index)
            var attempts = 1
            while (
                attempts < MaxAttemptsPerTile &&
                placed.any { hypot(it.x - candidate.x, it.y - candidate.y) < minDistance }
            ) {
                candidate = randomTile(random, boundsWidth, boundsHeight, index)
                attempts += 1
            }
            placed.add(candidate)
        }
        return placed
    }

    private fun randomTile(random: Random, boundsWidth: Float, boundsHeight: Float, index: Int) = HuntTilePosition(
        x = (0.1f + random.nextFloat() * 0.8f) * boundsWidth,
        y = (0.1f + random.nextFloat() * 0.8f) * boundsHeight,
        scale = 0.8f + random.nextFloat() * 0.5f,
        colorIndex = index,
    )

    private fun isWellSpaced(tiles: List<HuntTilePosition>, minDistance: Float): Boolean {
        for (i in tiles.indices) {
            for (j in i + 1 until tiles.size) {
                if (hypot(tiles[i].x - tiles[j].x, tiles[i].y - tiles[j].y) < minDistance) return false
            }
        }
        return true
    }
}
