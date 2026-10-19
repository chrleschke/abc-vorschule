package app.abcvorschule.ui.path

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.rewards.drawGlowStar
import app.abcvorschule.ui.theme.StarGold
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Die Feier am Schild, das das Kind gerade zu Ende gespielt hat: es springt einmal
 * hoch und wirft einen Kranz Sterne ab, dann hüpft der Pfeil zum nächsten Schild.
 * „Das hier ist geschafft, dort geht es weiter" — ohne ein Wort, und egal, ob die
 * Lektion allein, mit Hilfe oder als Wiederholung zu Ende ging.
 *
 * Eigene Zahlen nach AbcMotions Regel 3 (Figurenspiel). Bei „Bewegung reduzieren"
 * spielt sie nicht; der Pfeil wechselt trotzdem das Schild.
 */
internal object PathCelebration {
    /** Erst einen Moment das alte Schild zeigen, dann feiern. */
    const val DelayMillis = 250L
    const val DurationMillis = 1100

    const val SignJumpDp = 22f

    private const val StarCount = 10
    private const val StarReachDp = 116f
    private const val StarRadiusDp = 13f
    private const val RingReachDp = 140f

    /** Anteil der Feier, in dem das Schild springt (der Rest gehört den Sternen). */
    private const val JumpShare = 0.42f

    /** Sprunghöhe des Schilds, 0 … 1. */
    fun signLift(progress: Float): Float {
        if (progress <= 0f || progress >= JumpShare) return 0f
        return sin(PI * progress / JumpShare).toFloat()
    }

    internal fun easeOut(t: Float): Float {
        val c = t.coerceIn(0f, 1f)
        return 1f - (1f - c) * (1f - c) * (1f - c)
    }

    internal fun fadeOut(t: Float, from: Float): Float =
        if (t <= from) 1f else (1f - (t - from) / (1f - from)).coerceIn(0f, 1f)

    fun DrawScope.drawCelebrationAt(center: Offset, progress: Float) {
        if (progress <= 0f || progress >= 1f) return
        // Die Sterne fliegen erst ab, wenn das Schild oben ist.
        val t = ((progress - JumpShare * 0.5f) / (1f - JumpShare * 0.5f)).coerceIn(0f, 1f)
        if (t <= 0f) return
        val spread = easeOut(t)
        val ringRadius = 24.dp.toPx() + spread * (RingReachDp.dp.toPx() - 24.dp.toPx())
        drawCircle(
            color = StarGold.copy(alpha = 0.55f * (1f - t)),
            radius = ringRadius,
            center = center,
            style = Stroke(width = 5.dp.toPx() * (1f - t) + 1f),
        )
        val fade = fadeOut(t, from = 0.55f)
        repeat(StarCount) { i ->
            val angle = (i * 2.0 * PI / StarCount) - PI / 2
            // Abwechselnd weiter und näher, damit es ein Kranz ist und kein Rad.
            val reach = StarReachDp.dp.toPx() * (if (i % 2 == 0) 1f else 0.72f)
            val d = spread * reach
            drawGlowStar(
                center = Offset(center.x + cos(angle).toFloat() * d, center.y + sin(angle).toFloat() * d),
                radius = StarRadiusDp.dp.toPx() * (1f - 0.45f * t) * min(1f, t * 6f),
                rotationDeg = 160f * t + i * 20f,
                alpha = fade,
                halo = 0.7f,
            )
        }
    }
}

internal fun DrawScope.drawCelebration(center: Offset, progress: Float) =
    with(PathCelebration) { drawCelebrationAt(center, progress) }
