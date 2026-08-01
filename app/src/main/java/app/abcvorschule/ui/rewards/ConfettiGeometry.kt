package app.abcvorschule.ui.rewards

import kotlin.random.Random

data class ConfettiPiece(
    val xFraction: Float,
    val delayFraction: Float,
    val fallSpeed: Float,
    val drift: Float,
    val colorIndex: Int,
    val sizeFraction: Float,
)

/**
 * Deterministische Konfetti-Verteilung (seed-basiert, damit testbar und
 * resume-stabil). progress 0..1 überstreicht die gesamte Animationsdauer;
 * yFraction < 0 heißt "noch über dem Screen", > 1 "unten raus".
 */
object ConfettiGeometry {
    fun pieces(count: Int, seed: Long): List<ConfettiPiece> {
        val rnd = Random(seed)
        return List(count) {
            ConfettiPiece(
                xFraction = rnd.nextFloat(),
                delayFraction = rnd.nextFloat() * 0.5f,
                fallSpeed = 1.1f + rnd.nextFloat() * 0.9f,
                drift = (rnd.nextFloat() - 0.5f) * 0.25f,
                colorIndex = rnd.nextInt(4),
                sizeFraction = 0.6f + rnd.nextFloat() * 0.8f,
            )
        }
    }

    fun yFraction(piece: ConfettiPiece, progress: Float): Float {
        val local = ((progress - piece.delayFraction) / (1f - piece.delayFraction))
        // Start knapp über dem Screen (-0.1), Ende sicher darunter.
        return -0.1f + local * piece.fallSpeed * 1.3f
    }
}
