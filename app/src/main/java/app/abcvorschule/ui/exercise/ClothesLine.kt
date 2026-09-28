package app.abcvorschule.ui.exercise

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.dp

/**
 * Die Wäscheleine des Satz-Architekten (Garten-Welt, PRODUCT_PRINCIPLES §10). Die
 * Leine spannt sich über die ganze Bildschirmbreite zwischen zwei Pfosten und hängt
 * in der Mitte durch. Jeder Peg hängt an seinem Punkt der Leine, also etwas tiefer,
 * je näher er der Mitte ist.
 *
 * Die Rechnung ist Compose-frei ([sagAt]), damit sie testbar bleibt; [ClothesLineState]
 * hält nur, wo die Reihe auf dem Bildschirm liegt.
 */
object ClothesLineGeometry {
    /** Wie tief die Leine in der Mitte durchhängt. */
    const val SagDp = 14f

    /** Abstand der Pfosten vom Bildschirmrand. */
    const val PoleInsetDp = 10f

    /** Platz über den Pegs für Leine und Klammern. */
    const val PinRiseDp = 14f

    /** Wo die Leine im Rahmen der Reihe verläuft (ohne Durchhang), von oben gemessen. */
    const val RopeYDp = 7f

    /**
     * Durchhang an [xScreen] auf einer Leine von [inset] bis [screenWidth] − [inset]:
     * eine Parabel, 0 an den Pfosten, [sag] in der Mitte. Außerhalb der Pfosten 0.
     */
    fun sagAt(xScreen: Float, screenWidth: Float, inset: Float, sag: Float): Float {
        val half = (screenWidth / 2f - inset).coerceAtLeast(1f)
        val t = ((xScreen - screenWidth / 2f) / half).coerceIn(-1f, 1f)
        return sag * (1f - t * t)
    }
}

/** Wo die Reihe liegt — gelesen nur in der Zeichenphase. */
@Stable
class ClothesLineState {
    var leftInRoot by mutableFloatStateOf(0f)
    var topInRoot by mutableFloatStateOf(0f)
    var screenWidth by mutableFloatStateOf(0f)
    var screenHeight by mutableFloatStateOf(0f)

    /** Durchhang für einen Punkt, der [xInLine] px vom linken Rand der Reihe liegt. */
    fun sagAt(xInLine: Float, inset: Float, sag: Float): Float =
        if (screenWidth <= 0f) 0f else ClothesLineGeometry.sagAt(leftInRoot + xInLine, screenWidth, inset, sag)
}

/**
 * Zeichnet Leine und Pfosten hinter die Reihe, über ihre eigenen Grenzen hinaus bis an
 * den Bildschirmrand und die Pfosten bis zum Boden. Die Maße des Bildschirms kommen
 * vom Aufrufer (`LocalWindowInfo`), die Lage der Reihe misst der Modifier selbst.
 */
fun Modifier.clothesLine(state: ClothesLineState, screenWidthPx: Float, screenHeightPx: Float): Modifier =
    onGloballyPositioned { coords ->
        val p = coords.positionInRoot()
        state.leftInRoot = p.x
        state.topInRoot = p.y
        state.screenWidth = screenWidthPx
        state.screenHeight = screenHeightPx
    }.drawBehind {
        if (state.screenWidth <= 0f) return@drawBehind
        val inset = ClothesLineGeometry.PoleInsetDp.dp.toPx()
        val sag = ClothesLineGeometry.SagDp.dp.toPx()
        val ropeY = ClothesLineGeometry.RopeYDp.dp.toPx()
        val left = -state.leftInRoot + inset
        val right = -state.leftInRoot + state.screenWidth - inset
        val groundY = state.screenHeight - state.topInRoot
        drawPole(left, ropeY, groundY)
        drawPole(right, ropeY, groundY)
        val rope = Path().apply {
            moveTo(left, ropeY)
            val steps = 32
            for (i in 1..steps) {
                val x = left + (right - left) * i / steps
                lineTo(x, ropeY + state.sagAt(x, inset, sag))
            }
        }
        drawPath(rope, RopeShadow, style = Stroke(width = 3.dp.toPx()), alpha = 0.5f)
        drawPath(rope, Rope, style = Stroke(width = 1.6.dp.toPx()))
    }

private fun DrawScope.drawPole(x: Float, top: Float, bottom: Float) {
    val w = 5.dp.toPx()
    drawRect(PoleWood, topLeft = Offset(x - w / 2f, top - 3.dp.toPx()), size = Size(w, bottom - top))
    drawRoundRect(
        PoleWood,
        topLeft = Offset(x - 7.dp.toPx(), top - 5.dp.toPx()),
        size = Size(14.dp.toPx(), 4.dp.toPx()),
        cornerRadius = CornerRadius(1.5.dp.toPx()),
    )
}

/** Eine Holzklammer oben mittig am Peg, über dessen Oberkante hinaus. */
internal fun DrawScope.drawClothesPin() {
    val w = 7.dp.toPx()
    val h = 17.dp.toPx()
    val top = -(ClothesLineGeometry.PinRiseDp - ClothesLineGeometry.RopeYDp + 3f).dp.toPx()
    drawRoundRect(
        brush = Brush.horizontalGradient(
            0f to PinDark,
            0.5f to PinLight,
            1f to PinDark,
            startX = size.width / 2f - w / 2f,
            endX = size.width / 2f + w / 2f,
        ),
        topLeft = Offset(size.width / 2f - w / 2f, top),
        size = Size(w, h),
        cornerRadius = CornerRadius(2.dp.toPx()),
    )
    drawLine(PinDark, Offset(size.width / 2f, top + 2.dp.toPx()), Offset(size.width / 2f, top + h - 2.dp.toPx()), strokeWidth = 1.dp.toPx())
}

private val Rope = Color(0xFFD8CDB7)
private val RopeShadow = Color(0xFF0A0C16)
private val PoleWood = Color(0xFF3A2A2C)
private val PinLight = Color(0xFFE8C79D)
private val PinDark = Color(0xFFB88A5C)
