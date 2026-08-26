package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.ChargeHigh
import app.abcvorschule.ui.theme.CloudWhite
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/**
 * Die Batterie der Buchstaben-/Silben-Jagd: ein Gehäuse mit Pluspol, darin eine
 * dunkle Wanne mit [total] Ladebalken. Jeder Treffer füllt einen Balken, voll
 * heißt hell leuchten plus Blitz — und das ist zugleich das Erfolgssignal der
 * Runde, es gibt keinen „Weiter"-Knopf (PRODUCT_PRINCIPLES §10).
 *
 * Gezeichnet statt aus Boxen gebaut: Verlauf im Gehäuse, Lichtkante oben,
 * Glasglanz über der oberen Hälfte, ein Lichtsaum um jeden gefüllten Balken.
 * Genau diese Kleinigkeiten unterscheiden eine Batterie von einer Reihe
 * Rechtecke, und keine davon lässt sich mit `background()` und `border()`
 * sauber stapeln.
 */
@Composable
fun SymbolHuntBattery(
    collected: Int,
    total: Int,
    celebrate: Boolean,
    modifier: Modifier = Modifier,
) {
    // Der Puls existiert nur während der Feier am Rundenende — sonst liefe eine
    // Endlos-Animation die ganze Runde über ohne sichtbare Wirkung.
    val glow = if (celebrate) {
        val infiniteTransition = rememberInfiniteTransition(label = "battery_glow")
        val animatedGlow by infiniteTransition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "battery_glow_value",
        )
        animatedGlow
    } else {
        0f
    }
    Box(
        modifier = modifier.fillMaxWidth().testTag("hunt_battery"),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(
            modifier = Modifier.size(
                width = HuntBatteryDesign.totalWidth(total) + HuntBatteryDesign.HaloWidth * 2,
                height = HuntBatteryDesign.BodyHeight + HuntBatteryDesign.HaloWidth * 2,
            ),
        ) {
            val halo = HuntBatteryDesign.HaloWidth.toPx()
            // Der Halo-Rand liegt außen herum, damit der leuchtende Ring beim
            // Vollzustand Platz hat, ohne dass das Gehäuse dafür wandert.
            translate(left = halo, top = halo) {
                drawBattery(collected = collected, total = total, celebrate = celebrate, glow = glow)
            }
        }
    }
}

private fun DrawScope.drawBattery(collected: Int, total: Int, celebrate: Boolean, glow: Float) {
    val bodyWidth = HuntBatteryDesign.bodyWidth(total).toPx()
    val bodyHeight = HuntBatteryDesign.BodyHeight.toPx()
    val bodyCorner = CornerRadius(HuntBatteryDesign.BodyCorner.toPx())
    val body = Rect(Offset.Zero, Size(bodyWidth, bodyHeight))

    if (celebrate) {
        // Lichtsaum um das ganze Gehäuse, pulsierend — die Feier liegt im Licht,
        // nicht in der Farbe: die Balken bleiben grün und wechseln nicht ins Gold.
        drawRoundRect(
            color = ChargeHigh.copy(alpha = 0.10f + 0.35f * glow),
            topLeft = Offset(-HuntBatteryDesign.HaloWidth.toPx() / 2f, -HuntBatteryDesign.HaloWidth.toPx() / 2f),
            size = Size(bodyWidth + HuntBatteryDesign.HaloWidth.toPx(), bodyHeight + HuntBatteryDesign.HaloWidth.toPx()),
            cornerRadius = CornerRadius(HuntBatteryDesign.BodyCorner.toPx() + HuntBatteryDesign.HaloWidth.toPx() / 2f),
            style = Stroke(width = HuntBatteryDesign.HaloWidth.toPx()),
        )
    }

    drawNub(bodyWidth = bodyWidth, bodyHeight = bodyHeight)

    // Gehäuse: Verlauf von heller Oberkante zu dunkler Unterkante — Plastik im
    // Licht von oben, dieselbe Lichtrichtung wie bei den Jagd-Kacheln.
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(WarmMuted, WarmInk), startY = 0f, endY = bodyHeight),
        size = body.size,
        cornerRadius = bodyCorner,
    )
    // Lichtkante: ein Haarstrich innen an der Gehäusekante.
    val rim = 1.dp.toPx()
    drawRoundRect(
        color = Cream.copy(alpha = 0.22f),
        topLeft = Offset(rim / 2f, rim / 2f),
        size = Size(bodyWidth - rim, bodyHeight - rim),
        cornerRadius = bodyCorner,
        style = Stroke(width = rim),
    )

    drawWell(bodyWidth = bodyWidth, bodyHeight = bodyHeight)
    drawCells(collected = collected, total = total, celebrate = celebrate, bodyHeight = bodyHeight)
    drawGloss(body = body, cornerRadius = bodyCorner)

    if (celebrate) {
        drawBolt(bodyWidth = bodyWidth, bodyHeight = bodyHeight)
    }
}

/** Pluspol: sitzt rechts, ragt aus dem Gehäuse und schiebt sich ein Stück darunter. */
private fun DrawScope.drawNub(bodyWidth: Float, bodyHeight: Float) {
    val nubHeight = HuntBatteryDesign.NubHeight.toPx()
    val overlap = HuntBatteryDesign.NubCorner.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(WarmMuted, WarmInk)),
        topLeft = Offset(bodyWidth - overlap, (bodyHeight - nubHeight) / 2f),
        size = Size(HuntBatteryDesign.NubWidth.toPx() + overlap, nubHeight),
        cornerRadius = CornerRadius(HuntBatteryDesign.NubCorner.toPx()),
    )
}

/** Wanne: die dunkle Innenfläche, gegen die alle Balkenkontraste gemessen sind. */
private fun DrawScope.drawWell(bodyWidth: Float, bodyHeight: Float) {
    val casing = HuntBatteryDesign.CasingThickness.toPx()
    val wellSize = Size(bodyWidth - casing * 2f, bodyHeight - casing * 2f)
    drawRoundRect(
        color = WarmInk,
        topLeft = Offset(casing, casing),
        size = wellSize,
        cornerRadius = CornerRadius(HuntBatteryDesign.WellCorner.toPx()),
    )
    // Innenschatten: oben dunkel auslaufend — die Wanne liegt tiefer als das
    // Gehäuse, das Licht kommt von oben und erreicht ihren oberen Rand nicht.
    drawRoundRect(
        brush = Brush.verticalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.30f), Color.Transparent),
            startY = casing,
            endY = casing + wellSize.height * 0.6f,
        ),
        topLeft = Offset(casing, casing),
        size = wellSize,
        cornerRadius = CornerRadius(HuntBatteryDesign.WellCorner.toPx()),
    )
}

private fun DrawScope.drawCells(collected: Int, total: Int, celebrate: Boolean, bodyHeight: Float) {
    val rim = HuntBatteryDesign.Rim.toPx()
    val cellWidth = HuntBatteryDesign.CellWidth.toPx()
    val cellHeight = bodyHeight - rim * 2f
    val gap = HuntBatteryDesign.CellGap.toPx()
    val corner = CornerRadius(HuntBatteryDesign.CellCorner.toPx())
    val bleed = HuntBatteryDesign.GlowBleed.toPx()
    repeat(total) { i ->
        val left = rim + i * (cellWidth + gap)
        val topLeft = Offset(left, rim)
        val size = Size(cellWidth, cellHeight)
        val filled = celebrate || i < collected
        if (!filled) {
            // Leere Zelle: eine Vertiefung in der Wanne, kein weißes Kästchen —
            // das Kind soll sehen, wie viel noch fehlt, ohne dass die leeren
            // Plätze mit den vollen um Aufmerksamkeit streiten.
            drawRoundRect(
                color = Color.Black.copy(alpha = 0.24f),
                topLeft = topLeft,
                size = size,
                cornerRadius = corner,
            )
            drawRoundRect(
                color = CloudWhite.copy(alpha = 0.10f),
                topLeft = topLeft,
                size = size,
                cornerRadius = corner,
                style = Stroke(width = 1.dp.toPx()),
            )
            return@repeat
        }
        // Voll heißt einheitlich hell: der Verlauf über die Balken erzählt das
        // Laden, der Vollzustand ist ein einziger Ton.
        val shade = if (celebrate) ChargeHigh else HuntBatteryDesign.shadeFor(i, total)
        // Lichtsaum: der Balken leuchtet in die Wanne hinein.
        drawRoundRect(
            color = shade.copy(alpha = 0.22f),
            topLeft = Offset(left - bleed, rim - bleed),
            size = Size(cellWidth + bleed * 2f, cellHeight + bleed * 2f),
            cornerRadius = CornerRadius(HuntBatteryDesign.CellCorner.toPx() + bleed),
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                colors = listOf(HuntBatteryDesign.cellHighlight(shade), shade),
                startY = rim,
                endY = rim + cellHeight,
            ),
            topLeft = topLeft,
            size = size,
            cornerRadius = corner,
        )
        // Glanzstreifen auf dem oberen Drittel der Zelle.
        drawRoundRect(
            color = CloudWhite.copy(alpha = 0.30f),
            topLeft = Offset(left + cellWidth * 0.16f, rim + cellHeight * 0.10f),
            size = Size(cellWidth * 0.32f, cellHeight * 0.34f),
            cornerRadius = CornerRadius(cellWidth * 0.16f),
        )
    }
}

/** Glasglanz über der oberen Hälfte des Gehäuses, an der Gehäuseform beschnitten. */
private fun DrawScope.drawGloss(body: Rect, cornerRadius: CornerRadius) {
    val shape = Path().apply { addRoundRect(RoundRect(body, cornerRadius)) }
    clipPath(shape) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(CloudWhite.copy(alpha = 0.16f), Color.Transparent),
                startY = 0f,
                endY = body.height * 0.55f,
            ),
            size = Size(body.width, body.height * 0.55f),
        )
    }
}

/** Blitz in der Mitte, wenn die Batterie voll ist. WarmInk auf ChargeHigh ≈ 7.96:1. */
private fun DrawScope.drawBolt(bodyWidth: Float, bodyHeight: Float) {
    val height = (bodyHeight - HuntBatteryDesign.Rim.toPx() * 2f) * 0.82f
    val width = height * 0.55f
    val path = Path()
    HuntBatteryDesign.BoltPath.forEachIndexed { i, (x, y) ->
        val px = x * width
        val py = y * height
        if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
    }
    path.close()
    translate(left = (bodyWidth - width) / 2f, top = (bodyHeight - height) / 2f) {
        // Weiche Kante unter dem Blitz, damit er auf dem hellen Grün nicht klebt.
        scale(scaleX = 1.18f, scaleY = 1.12f, pivot = Offset(width / 2f, height / 2f)) {
            drawPath(path, color = CloudWhite.copy(alpha = 0.55f))
        }
        drawPath(path, color = WarmInk)
    }
}
