package app.abcvorschule.ui.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.StarGoldDeep
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Wo der Pfeil über dem aktuellen Schild steht und wie er hüpft. Reine Mathematik,
 * damit die Bahn ohne Compose testbar ist.
 *
 * Eigene Zahlen nach AbcMotions Regel 3 (Figurenspiel): der Pfeil ist ein Ball, der
 * auf der Stelle springt, keine Antwort auf eine Tat. Vom Nutzer ausdrücklich gewünscht
 * (Oktober 2026), weil der Nebelring allein auf dem Startbildschirm nicht sagte, was
 * man dort tun soll.
 */
internal object PathArrowMotion {
    /** Ein Sprung: unten, oben, wieder unten. */
    const val BouncePeriodS = 1.1f

    /** So hoch springt er über seinen Aufsetzpunkt. */
    const val BounceDp = 12f

    /** Bogenhöhe beim Hüpfen von einem Schild zum nächsten. */
    const val HopArcDp = 54f

    /** Luft zwischen Pfeilspitze und Abzeichen-Oberkante des Schilds. */
    const val GapDp = 4f

    const val ArrowWidthDp = 46f
    const val ArrowHeightDp = 48f

    /** Unterhalb dieses Sprunganteils wird er gestaucht — er „setzt auf". */
    private const val SquashBand = 0.2f

    /**
     * Höhe über dem Aufsetzpunkt, 0 … 1: ein springender Ball, oben rund, unten
     * spitz — die Spitze tippt das Schild kurz an und federt ab.
     */
    fun bounce(seconds: Float): Float = abs(sin(PI * seconds / BouncePeriodS)).toFloat()

    /** 1 im Aufsetzmoment, 0 in der Luft. */
    fun squash(seconds: Float): Float = (1f - bounce(seconds) / SquashBand).coerceIn(0f, 1f)

    /**
     * Pfeilspitze für eine Marker-Position [marker] (gebrochen während des Hüpfens):
     * zwischen zwei Schildern linear entlang ihrer Spitzenpunkte, plus ein Bogen pro
     * Teilstrecke — über mehrere Schilder springt er von Schild zu Schild, statt in
     * einer Linie zu gleiten.
     *
     * @param tips Ruheposition der Spitze über jedem Schild, in Pfad-Koordinaten.
     */
    fun tip(marker: Float, tips: List<Offset>, arcPx: Float): Offset {
        if (tips.isEmpty()) return Offset.Zero
        val clamped = marker.coerceIn(0f, (tips.size - 1).toFloat())
        val i = floor(clamped).toInt().coerceAtMost(tips.size - 1)
        val j = (i + 1).coerceAtMost(tips.size - 1)
        val f = clamped - i
        val a = tips[i]
        val b = tips[j]
        return Offset(
            x = a.x + (b.x - a.x) * f,
            y = a.y + (b.y - a.y) * f - sin(PI * f).toFloat() * arcPx,
        )
    }
}

/**
 * Der hüpfende Pfeil über dem aktuellen Schild: „hier tippen", ohne ein Wort. Er
 * steht über den Abzeichen und zeigt mit der Spitze auf die Klötze; ein Tipp auf ihn
 * öffnet dieselbe Lektion wie das Schild.
 *
 * Für TalkBack ist er Dekoration — „aktuell" sagt das Schild selbst.
 *
 * @param tip Spitze in Pfad-Koordinaten, gelesen in der Layout-Phase.
 * @param lift Sprunghöhe 0 … 1 und [squash] 0 … 1, beide nur in der Zeichenphase gelesen.
 */
@Composable
internal fun PathArrow(
    tip: () -> Offset,
    lift: () -> Float,
    squash: () -> Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val boxWidth = 72.dp
    val boxHeight = (PathArrowMotion.ArrowHeightDp + PathArrowMotion.BounceDp + 14f).dp
    Box(
        modifier
            .offset {
                val t = tip()
                IntOffset(
                    x = (t.x - boxWidth.toPx() / 2f).roundToInt(),
                    y = (t.y - boxHeight.toPx()).roundToInt(),
                )
            }
            .size(boxWidth, boxHeight)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .clearAndSetSemantics {}
            .testTag("path_arrow"),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val w = PathArrowMotion.ArrowWidthDp.dp.toPx()
            val h = PathArrowMotion.ArrowHeightDp.dp.toPx()
            val s = squash()
            val rise = lift() * PathArrowMotion.BounceDp.dp.toPx()
            val tipPoint = Offset(size.width / 2f, size.height - rise)
            // Schein hinter dem Pfeil — am Abendhimmel braucht Gold Licht, keine Kontur.
            val glowCenter = Offset(tipPoint.x, tipPoint.y - h * 0.55f)
            drawCircle(
                brush = Brush.radialGradient(
                    0f to StarGold.copy(alpha = 0.42f),
                    1f to Color.Transparent,
                    center = glowCenter,
                    radius = h * 0.95f,
                ),
                radius = h * 0.95f,
                center = glowCenter,
            )
            // Stauchen um die Spitze: sie bleibt am Schild, der Körper wird breit.
            scale(scaleX = 1f + 0.12f * s, scaleY = 1f - 0.16f * s, pivot = tipPoint) {
                translate(left = tipPoint.x - w / 2f, top = tipPoint.y - h) {
                    drawArrow(w, h)
                }
            }
        }
    }
}

private val ArrowLight = Color(0xFFFFE7A3)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrow(w: Float, h: Float) {
    // Die runde Kontur (Stroke mit runden Ecken) macht den Umriss um ihre halbe Breite
    // dicker — deshalb wird der Pfad um genau so viel nach innen gesetzt.
    val round = 8.dp.toPx()
    val inset = round / 2f
    val cx = w / 2f
    val shaft = w * 0.2f
    val headY = h * 0.46f
    val path = Path().apply {
        moveTo(cx - shaft + inset * 0.3f, inset)
        lineTo(cx + shaft - inset * 0.3f, inset)
        lineTo(cx + shaft - inset * 0.3f, headY)
        lineTo(w - inset, headY)
        lineTo(cx, h - inset)
        lineTo(inset, headY)
        lineTo(cx - shaft + inset * 0.3f, headY)
        close()
    }
    val fill = Brush.verticalGradient(
        0f to ArrowLight,
        0.55f to StarGold,
        1f to Color(0xFFE08A0C),
        startY = 0f,
        endY = h,
    )
    // Dunkler Saum, damit die Form auch vor hellen Laternen lesbar bleibt.
    drawPath(path, StarGoldDeep, style = Stroke(width = round + 3.dp.toPx(), join = StrokeJoin.Round))
    drawPath(path, fill, style = Stroke(width = round, join = StrokeJoin.Round))
    drawPath(path, fill)
    // Glanzkante links am Schaft.
    drawLine(
        color = Color.White.copy(alpha = 0.55f),
        start = Offset(cx - shaft * 0.45f, h * 0.1f),
        end = Offset(cx - shaft * 0.45f, headY - h * 0.04f),
        strokeWidth = 2.5.dp.toPx(),
        cap = StrokeCap.Round,
    )
}
