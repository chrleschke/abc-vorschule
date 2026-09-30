package app.abcvorschule.ui.rewards

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Der Belohnungsstern der Nachtwelten (PRODUCT_PRINCIPLES §10): gerundete Spitzen, keine
 * Kontur, Volumen von innen heraus (hell in der Mitte, bernstein am Rand), ein Glanzpunkt
 * oben links und ein weicher Schein darum. Auf dunklem Grund trennt ihn sein eigenes
 * Leuchten vom Hintergrund — die dunkle Kontur des alten `IconStar` brauchte er nur auf
 * Papier. [shimmer] 0…1 zieht einen Glanzstreifen von links nach rechts über ihn.
 */
fun DrawScope.drawGlowStar(
    center: Offset,
    radius: Float,
    rotationDeg: Float = 0f,
    alpha: Float = 1f,
    shimmer: Float = -1f,
    halo: Float = 1f,
) {
    if (radius <= 0f || alpha <= 0f) return
    if (halo > 0f) {
        val hr = radius * 2.4f
        drawCircle(
            brush = Brush.radialGradient(
                0f to StarHalo.copy(alpha = 0.55f * halo * alpha),
                0.45f to StarHalo.copy(alpha = 0.16f * halo * alpha),
                1f to Color.Transparent,
                center = center,
                radius = hr,
            ),
            radius = hr,
            center = center,
        )
    }
    rotate(rotationDeg, pivot = center) {
        val path = roundedStarPath(center, radius, radius * 0.5f)
        drawPath(
            path,
            brush = Brush.radialGradient(
                0f to StarCore,
                0.45f to StarBody,
                1f to StarEdge,
                center = center + Offset(-radius * 0.2f, -radius * 0.25f),
                radius = radius * 1.1f,
            ),
            alpha = alpha,
        )
        clipPath(path) {
            if (shimmer in 0f..1f) {
                val x = center.x - radius * 1.6f + shimmer * radius * 3.2f
                drawRect(
                    brush = Brush.linearGradient(
                        0f to Color.Transparent,
                        0.5f to Color.White.copy(alpha = 0.55f * alpha),
                        1f to Color.Transparent,
                        start = Offset(x - radius * 0.35f, center.y - radius),
                        end = Offset(x + radius * 0.35f, center.y + radius),
                    ),
                    topLeft = center - Offset(radius * 2f, radius * 2f),
                    size = Size(radius * 4f, radius * 4f),
                )
            }
            rotate(-35f, pivot = center + Offset(-radius * 0.28f, -radius * 0.32f)) {
                drawOval(
                    Color.White.copy(alpha = 0.55f * alpha),
                    topLeft = center + Offset(-radius * 0.44f, -radius * 0.41f),
                    size = Size(radius * 0.32f, radius * 0.18f),
                )
            }
        }
    }
}

/** Ein vierstrahliges Glanzlicht mit weichem Hof — Funkeln statt Konfetti-Punkten. */
fun DrawScope.drawGlint(center: Offset, size: Float, alpha: Float) {
    if (size <= 0f || alpha <= 0f) return
    val hr = size * 1.4f
    drawCircle(
        brush = Brush.radialGradient(
            0f to GlintHalo.copy(alpha = 0.6f * alpha),
            1f to Color.Transparent,
            center = center,
            radius = hr,
        ),
        radius = hr,
        center = center,
    )
    val path = Path()
    for (i in 0 until 8) {
        val r = if (i % 2 == 0) size else size * 0.18f
        val a = i * PI.toFloat() / 4f
        val p = center + Offset(cos(a) * r, sin(a) * r)
        if (i == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
    }
    path.close()
    drawPath(path, GlintCore, alpha = alpha)
}

/** Fünfzackiger Stern mit gerundeten Spitzen und Kerben (quadratische Bögen an jeder Ecke). */
internal fun roundedStarPath(center: Offset, outer: Float, inner: Float, rounding: Float = 0.28f): Path {
    val pts = List(10) { i ->
        val r = if (i % 2 == 0) outer else inner
        val a = -PI.toFloat() / 2f + i * PI.toFloat() / 5f
        center + Offset(cos(a) * r, sin(a) * r)
    }
    return Path().apply {
        for (i in 0 until 10) {
            val p = pts[i]
            val prev = pts[(i + 9) % 10]
            val next = pts[(i + 1) % 10]
            val k = if (i % 2 == 0) rounding else rounding * 0.4f
            val a = p + (prev - p) * k
            val b = p + (next - p) * k
            if (i == 0) moveTo(a.x, a.y) else lineTo(a.x, a.y)
            quadraticTo(p.x, p.y, b.x, b.y)
        }
        close()
    }
}

private val StarCore = Color(0xFFFFF6C8)
private val StarBody = Color(0xFFFFC94A)
private val StarEdge = Color(0xFFE2891A)
private val StarHalo = Color(0xFFFFD678)
private val GlintHalo = Color(0xFFFFF0BE)
private val GlintCore = Color(0xFFFFF8DC)
