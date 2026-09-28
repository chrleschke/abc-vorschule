package app.abcvorschule.ui.world

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Eine Licht-Insel hinter Lerninhalt auf einer Nachtwelt (PRODUCT_PRINCIPLES §10,
 * Regel 1: Inhalt nur auf Licht). Gezeichnet **um** das Bauteil herum, nicht als
 * Hülle: das Layout bleibt, wie es gerechnet ist — die Größenrechnungen der Trainer
 * (MathBoardSizing, WordFrameSizing) kennen die Insel nicht und müssen es auch nicht.
 *
 * Tinte (WarmInk) auf [IslandCream] liegt bei über 11:1, die Kontrastrechnungen der
 * Lernschrift gelten unverändert.
 */
fun Modifier.lightIsland(
    padH: Dp = 18.dp,
    padV: Dp = 12.dp,
    corner: Dp = 24.dp,
    color: Color = IslandCream,
): Modifier = drawBehind {
    val ph = padH.toPx()
    val pv = padV.toPx()
    val r = CornerRadius(corner.toPx())
    val boxSize = Size(size.width + 2 * ph, size.height + 2 * pv)
    // Weicher Schatten nach unten: die Insel liegt über der Welt, nicht in ihr.
    drawRoundRect(IslandShadow, topLeft = Offset(-ph, -pv + 6.dp.toPx()), size = boxSize, cornerRadius = r)
    drawRoundRect(color, topLeft = Offset(-ph, -pv), size = boxSize, cornerRadius = r)
}

/** Eine runde Licht-Insel (Teller unter einem Bild). */
fun Modifier.lightPlate(extra: Dp = 14.dp, color: Color = IslandCream): Modifier = drawBehind {
    val radius = max(size.width, size.height) / 2f + extra.toPx()
    drawCircle(IslandShadow, radius = radius, center = center + Offset(0f, 6.dp.toPx()))
    drawCircle(
        Brush.radialGradient(0f to Color.White, 1f to color, center = center - Offset(radius * 0.25f, radius * 0.3f), radius = radius * 1.4f),
        radius = radius,
        center = center,
    )
}

/**
 * Ein Lichtkegel-Fleck: weich auslaufendes Licht statt einer Karte — für den
 * Wort-Detektiv, dessen Wort im Taschenlampenlicht liegt. Innen so hell wie eine
 * Insel, damit die Buchstaben ihren Kontrast behalten.
 */
fun Modifier.lightPool(color: Color = PoolLight): Modifier = drawBehind {
    val rx = size.width * 0.62f + 24.dp.toPx()
    val ry = size.height * 0.95f + 28.dp.toPx()
    val c = center
    // Ein Kreisverlauf, senkrecht gestaucht: so läuft der Fleck an allen Rändern aus,
    // statt oben und unten an der Ellipse abgeschnitten zu werden.
    scale(scaleX = 1f, scaleY = ry / rx, pivot = c) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to color,
                0.55f to color.copy(alpha = 0.92f),
                0.8f to color.copy(alpha = 0.45f),
                1f to Color.Transparent,
                center = c,
                radius = rx,
            ),
            radius = rx,
            center = c,
        )
    }
}

val IslandCream = Color(0xF2F8F4EA)
private val IslandShadow = Color(0x47000000)
private val PoolLight = Color(0xFFFBF2DC)
