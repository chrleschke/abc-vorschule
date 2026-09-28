package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.theme.SilboUi
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.world.IslandCream
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Die Geometrie der Zahlentürme (PRODUCT_PRINCIPLES §8), Compose-frei und testbar.
 *
 * Eine Zahl ist eine Figur aus so vielen Blöcken, gestapelt in **Säulen zu je fünf** —
 * sieben ist „eine volle Säule und zwei", zehn sind zwei volle Säulen (der Zehner). So
 * sieht ein Kind die Menge, statt sie einzeln abzuzählen.
 */
object NumberTowerGeometry {
    const val ColumnHeight = 5

    /** Abstand zweier Säulen einer Figur, als Anteil der Blockkante. */
    const val ColumnGapFraction = 0.18f

    fun columns(n: Int): Int = max(1, ceil(n / ColumnHeight.toFloat()).toInt())

    /** Platz von Block [i]: Säulen von links nach rechts, in jeder von unten nach oben. */
    fun slot(i: Int, block: Float, gap: Float): Offset {
        val col = i / ColumnHeight
        val row = i % ColumnHeight
        return Offset(col * (block + gap), -row * block)
    }

    fun width(n: Int, block: Float, gap: Float): Float {
        val cols = columns(n)
        return cols * block + (cols - 1) * gap
    }

    /**
     * Wo das große Gesicht der fertigen Figur sitzt: auf dem größten voll gefüllten
     * Rechteck unten links, bei mehr als einer Säule mindestens zwei Säulen breit. Bei 7
     * (Säulen 5 und 2) sind das die unteren zwei Reihen über beide Säulen — sonst schwebte
     * ein Auge über leerer Fläche. Liefert (Säulen, Reihen).
     */
    fun faceRect(n: Int): Pair<Int, Int> {
        val cols = columns(n)
        val heights = List(cols) { min(ColumnHeight, n - it * ColumnHeight) }
        var best = 1 to heights[0]
        for (c in 2..cols) {
            val rows = heights.take(c).min()
            if (best.first == 1 || c * rows > best.first * best.second) best = c to rows
        }
        return best
    }

    /**
     * Wie viele Blockkanten die Aufgabe nebeneinander braucht: bei Plus und Minus beide
     * Figuren und die Lücke dazwischen, beim Malnehmen alle Türme mit ihren Lücken.
     */
    fun spanInBlocks(operation: MathOperation, left: Int, right: Int): Float = when (operation) {
        MathOperation.Multiply -> left * columns(right) * (1f + ColumnGapFraction) + (left - 1) * TowerGapBlocks
        MathOperation.Add -> columns(left + right) * (1f + ColumnGapFraction) + PairGapBlocks + columns(right) * (1f + ColumnGapFraction)
        MathOperation.Subtract -> columns(left) * (1f + ColumnGapFraction) + PairGapBlocks + columns(right) * (1f + ColumnGapFraction)
    }

    /** Blockkante, mit der die Aufgabe in [width] × [towerHeight] passt, gedeckelt bei [max]. */
    fun blockSize(operation: MathOperation, left: Int, right: Int, width: Float, towerHeight: Float, max: Float): Float =
        minOf(max, width * 0.9f / spanInBlocks(operation, left, right), towerHeight / (ColumnHeight + JumpHeadroomBlocks))

    /** Lücke zwischen den beiden Figuren bei Plus und Minus, in Blockkanten. */
    const val PairGapBlocks = 1.6f

    /** Lücke zwischen den Türmen beim Malnehmen, bevor sie zusammenrücken. */
    const val TowerGapBlocks = 0.7f

    /** Luft über dem höchsten Turm für die Sprungbögen, in Blockkanten. */
    const val JumpHeadroomBlocks = 0.6f
}

/**
 * Die Rechenaufgabe als Zahlentürme (PRODUCT_PRINCIPLES §8, nach dem Prinzip von
 * Numberblocks, aber mit Silbos eigenen Figuren). Zwei Schritte:
 *
 * 1. **Tippen:** Tippt das Kind auf die Aufgabe, springen bei Plus die blauen Blöcke auf
 *    den Honig-Turm (sie bleiben blau), bei Minus hüpfen die weggenommenen oben herunter
 *    auf die gestrichelten blauen Plätze über der rechten Zahl; ihre alten Plätze bleiben
 *    als Geisterblöcke. Ein Tipp auf einen Turm sagt außerdem seine Zahl.
 * 2. **Richtige Antwort** ([solved]): erst jetzt verschmelzen die Figuren — Blau wird Honig,
 *    Geister und Weggenommenes gehen, beim Malnehmen rücken die Türme zusammen —, und die
 *    fertige Figur bekommt ein großes Gesicht. Hat das Kind nicht getippt, läuft erst der
 *    Sprung, dann das Verschmelzen. [revealed] (Auflösen) zeigt den Sprung ohne Feier.
 *
 * Farben nach Rolle, nicht nach Zahl: die erste Zahl ist Honig, die zweite Himmelblau.
 * Alles wird in der Zeichenphase gelesen; die Uhren sind Animatables.
 */
@Composable
fun NumberTowers(
    left: Int,
    right: Int,
    operation: MathOperation,
    solved: Boolean,
    revealed: Boolean,
    enabled: Boolean,
    onSpeakNumber: (Int) -> Unit,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val key = "$left-$operation-$right"
    val intro = remember(key) { Animatable(0f) }
    val jump = remember(key) { Animatable(-1f) }
    val merge = remember(key) { Animatable(-1f) }
    val nudge = remember(key) { Animatable(0f) }
    val hopLeft = remember(key) { Animatable(0f) }
    val hopRight = remember(key) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val measurer = rememberTextMeasurer()
    val speak = rememberUpdatedState(onSpeakNumber)
    val digitStyle = MaterialTheme.typography.headlineMedium.copy(fontFamily = SilboUi, fontWeight = FontWeight.Bold)
    val jumpTotal = TowerTiming.jumpTotal(operation, right)

    fun startJump() {
        if (operation == MathOperation.Multiply || jump.value >= 0f) return
        scope.launch {
            jump.snapTo(0f)
            jump.animateTo(jumpTotal, tween((jumpTotal * 1000).toInt(), easing = LinearEasing))
        }
    }

    LaunchedEffect(key) {
        intro.animateTo(TowerTiming.IntroS, tween((TowerTiming.IntroS * 1000).toInt(), easing = LinearEasing))
    }
    // Einladung zum Tippen: nach der Ansage hüpfen die Blöcke, die springen werden, ab und
    // zu kurz — bis das Kind getippt hat.
    LaunchedEffect(key, enabled, solved, revealed) {
        if (!enabled || solved || revealed || operation == MathOperation.Multiply) return@LaunchedEffect
        delay(TowerTiming.NudgeFirstMs)
        while (jump.value < 0f) {
            nudge.animateTo(1f, tween(TowerTiming.NudgeUpMs))
            nudge.animateTo(0f, tween(TowerTiming.NudgeDownMs))
            delay(TowerTiming.NudgeRepeatMs)
        }
    }
    LaunchedEffect(key, solved, revealed) {
        if (!solved && !revealed) return@LaunchedEffect
        if (operation != MathOperation.Multiply && jump.value < 0f) {
            jump.snapTo(0f)
            jump.animateTo(jumpTotal, tween((jumpTotal * 1000).toInt(), easing = LinearEasing))
        } else if (operation != MathOperation.Multiply && jump.value < jumpTotal) {
            jump.animateTo(jumpTotal, tween(((jumpTotal - jump.value) * 1000).toInt().coerceAtLeast(1), easing = LinearEasing))
        }
        if (!solved) return@LaunchedEffect
        merge.snapTo(0f)
        merge.animateTo(TowerTiming.MergeTotalS, tween((TowerTiming.MergeTotalS * 1000).toInt(), easing = LinearEasing))
    }

    val blinkTransition = rememberInfiniteTransition(label = "tower_blink")
    val blink = blinkTransition.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 3_700
                0f at 3_540
                1f at 3_620
                0f at 3_700
            },
            RepeatMode.Restart,
        ),
        label = "tower_blink_value",
    )

    val density = LocalDensity.current
    val plateHeightPx = with(density) { PlateHeight.toPx() }
    val maxBlockPx = with(density) { MaxBlock.toPx() }

    fun layoutFor(size: Size): TowerLayout {
        val towerHeight = size.height - plateHeightPx
        val b = NumberTowerGeometry.blockSize(operation, left, right, size.width, towerHeight, maxBlockPx)
        return TowerLayout.of(operation, left, right, b, size.width, towerHeight)
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .testTag("number_towers")
            .pointerInput(key, enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { tap ->
                    val lay = layoutFor(Size(size.width.toFloat(), size.height.toFloat()))
                    val onLeft = tap.x in lay.leftX - 8f..lay.leftX + lay.leftWidth + 8f
                    val onRight = lay.rightWidth > 0f && tap.x in lay.rightX - 8f..lay.rightX + lay.rightWidth + 8f
                    val mergedDone = merge.value >= 0f
                    val jumped = jump.value >= jumpTotal
                    when {
                        onLeft -> {
                            scope.launch { hop(hopLeft) }
                            speak.value(TowerTiming.leftNumber(operation, left, right, jumped, mergedDone))
                        }
                        onRight && !(operation == MathOperation.Add && jumped) -> {
                            scope.launch { hop(hopRight) }
                            speak.value(right)
                        }
                    }
                    startJump()
                }
            },
    ) {
        val lay = layoutFor(size)
        val b = lay.block
        val gap = b * NumberTowerGeometry.ColumnGapFraction
        val ground = lay.ground
        val hopL = hopLeft.value * HopPx * density.density
        val hopR = hopRight.value * HopPx * density.density
        val nudgePx = nudge.value * 8.dp.toPx()
        val j = jump.value
        val m = merge.value
        val mergeP = if (m < 0f) 0f else ease((m / 0.6f).coerceIn(0f, 1f))
        val grow = if (m < 0f) 0f else ease(((m - 0.4f) / 0.5f).coerceIn(0f, 1f))
        val happy = if (m > 0.9f) max(0f, sin(((m - 0.9f) / 0.6f).coerceIn(0f, 1f) * PI.toFloat())) else 0f
        val lift = happy * 6.dp.toPx()

        // Die Bühne: eine helle Platte mit den Zahlen der Aufgabe (Licht-Insel, §10).
        val plateTop = size.height - plateHeightPx + 4.dp.toPx()
        drawRoundRect(Color(0x55000000), topLeft = Offset(size.width * 0.04f, plateTop + 5.dp.toPx()), size = Size(size.width * 0.92f, plateHeightPx - 8.dp.toPx()), cornerRadius = CornerRadius(16.dp.toPx()))
        drawRoundRect(IslandCream, topLeft = Offset(size.width * 0.04f, plateTop), size = Size(size.width * 0.92f, plateHeightPx - 8.dp.toPx()), cornerRadius = CornerRadius(16.dp.toPx()))
        val plateMid = plateTop + (plateHeightPx - 8.dp.toPx()) / 2f
        fun label(text: String, cx: Float, color: Color) {
            val layout = measurer.measure(text, digitStyle.copy(color = color))
            drawText(layout, topLeft = Offset(cx - layout.size.width / 2f, plateMid - layout.size.height / 2f))
        }

        if (operation == MathOperation.Multiply) {
            drawTimes(lay, left, right, intro.value, mergeP, grow, happy, blink.value, hopL, lift)
            label("$left ${operation.symbol} $right", size.width / 2f, WarmInk)
            return@Canvas
        }
        label("$left", lay.leftX + NumberTowerGeometry.width(left, b, gap) / 2f, Honey.rim)
        label(operation.symbol, lay.leftX + NumberTowerGeometry.width(left, b, gap) + lay.pairGap / 2f, WarmInk)
        label("$right", lay.rightX + lay.rightWidth / 2f, Sky.rim)

        if (operation == MathOperation.Add) {
            for (i in 0 until left) {
                val s = NumberTowerGeometry.slot(i, b, gap)
                drawBlock(Offset(lay.leftX + s.x, ground + s.y - hopL - lift), b, Honey)
            }
            var moved = 0
            for (k in 0 until right) {
                val p = if (j < 0f) 0f else ((j - 0.1f - k * TowerTiming.AddStepS) / TowerTiming.AddDurS).coerceIn(0f, 1f)
                val from = NumberTowerGeometry.slot(k, b, gap)
                val to = NumberTowerGeometry.slot(left + k, b, gap)
                val pe = ease(p)
                val waiting = if (j < 0f) nudgePx else 0f
                val x = lerpF(lay.rightX + from.x, lay.leftX + to.x, pe)
                val y = lerpF(ground + from.y - hopR - waiting, ground + to.y - hopL - lift, pe) - sin(p * PI.toFloat()) * b * 2.2f
                drawBlock(Offset(x, y), b, Sky.mix(Honey, mergeP), rotationDeg = if (p in 0.001f..0.999f) sin(p * PI.toFloat()) * 28f else 0f)
                if (p >= 1f) moved++
            }
            val result = left + right
            if (left >= 10 || (mergeP > 0f && result >= 10)) drawTenMark(Offset(lay.leftX, ground - lift), b, gap)
            val top = NumberTowerGeometry.slot(min(4, left - 1), b, gap)
            drawSmallFace(Offset(lay.leftX + top.x, ground + top.y - hopL - lift), b, blink.value, alpha = 1f - grow)
            if (j < 0.1f) {
                val topR = NumberTowerGeometry.slot(min(4, right - 1), b, gap)
                drawSmallFace(Offset(lay.rightX + topR.x, ground + topR.y - hopR - (if (j < 0f) nudgePx else 0f)), b, blink.value)
            }
            if (grow > 0f) drawBigFaceFor(result, Offset(lay.leftX, ground - lift - hopL), b, gap, blink.value, happy, grow)
        } else {
            var departed = 0
            for (i in 0 until left - right) {
                val s = NumberTowerGeometry.slot(i, b, gap)
                drawBlock(Offset(lay.leftX + s.x, ground + s.y - hopL - lift), b, Honey)
            }
            for (k in 0 until right) {
                val idx = left - 1 - k
                val s = NumberTowerGeometry.slot(idx, b, gap)
                val to = NumberTowerGeometry.slot(k, b, gap)
                val p = if (j < 0f) 0f else ((j - 0.1f - k * TowerTiming.SubStepS) / TowerTiming.SubDurS).coerceIn(0f, 1f)
                if (p > 0f) departed++
                // Die blauen Plätze über der rechten Zahl stehen von Anfang an da.
                if (p < 1f) drawGhost(Offset(lay.rightX + to.x, ground + to.y - hopR), b, 0.85f * (1f - mergeP), SkyGhost)
                if (p > 0f) drawGhost(Offset(lay.leftX + s.x, ground + s.y - hopL), b, 0.7f * (1f - mergeP), CreamGhost)
                val pe = ease(p)
                val waiting = if (j < 0f) nudgePx else 0f
                val x = lerpF(lay.leftX + s.x, lay.rightX + to.x, pe)
                val y = lerpF(ground + s.y - hopL - waiting, ground + to.y - hopR, pe) - sin(p * PI.toFloat()) * b * 2f - mergeP * 30.dp.toPx()
                drawBlock(Offset(x, y), b, if (p < 0.5f) Honey else Sky, alpha = 1f - mergeP, rotationDeg = if (p in 0.001f..0.999f) -sin(p * PI.toFloat()) * 28f else 0f)
            }
            val result = left - departed
            if (result >= 10) drawTenMark(Offset(lay.leftX, ground - lift), b, gap)
            val top = NumberTowerGeometry.slot(min(4, max(0, result - 1)), b, gap)
            drawSmallFace(Offset(lay.leftX + top.x, ground + top.y - hopL - lift - (if (j < 0f) 0f else 0f)), b, blink.value, alpha = 1f - grow)
            if (grow > 0f) drawBigFaceFor(result, Offset(lay.leftX, ground - lift - hopL), b, gap, blink.value, happy, grow)
        }
    }
}

/** Die Zeitpunkte der Türme, in Sekunden. */
internal object TowerTiming {
    const val IntroS = 3f
    const val AddStepS = 0.32f
    const val AddDurS = 0.55f
    const val SubStepS = 0.34f
    const val SubDurS = 0.6f
    const val MergeTotalS = 1.6f
    const val NudgeFirstMs = 1_200L
    const val NudgeUpMs = 160
    const val NudgeDownMs = 220
    const val NudgeRepeatMs = 4_500L

    fun jumpTotal(operation: MathOperation, right: Int): Float = when (operation) {
        MathOperation.Add -> 0.1f + right * AddStepS + AddDurS
        MathOperation.Subtract -> 0.1f + right * SubStepS + SubDurS
        MathOperation.Multiply -> 0f
    }

    /** Welche Zahl der linke Turm gerade ist — das sagt er, wenn man ihn antippt. */
    fun leftNumber(operation: MathOperation, left: Int, right: Int, jumped: Boolean, merged: Boolean): Int = when (operation) {
        MathOperation.Add -> if (jumped) left + right else left
        MathOperation.Subtract -> if (jumped) left - right else left
        MathOperation.Multiply -> if (merged) left * right else right
    }
}

/** Wo die Figuren stehen. */
private data class TowerLayout(
    val block: Float,
    val leftX: Float,
    val leftWidth: Float,
    val rightX: Float,
    val rightWidth: Float,
    val pairGap: Float,
    val ground: Float,
) {
    companion object {
        fun of(operation: MathOperation, left: Int, right: Int, b: Float, width: Float, towerHeight: Float): TowerLayout {
            val gap = b * NumberTowerGeometry.ColumnGapFraction
            val ground = towerHeight - 2f
            if (operation == MathOperation.Multiply) {
                val wT = NumberTowerGeometry.width(right, b, gap)
                val all = left * wT + (left - 1) * b * NumberTowerGeometry.TowerGapBlocks
                return TowerLayout(b, width / 2f - all / 2f, all, 0f, 0f, 0f, ground)
            }
            val wL = NumberTowerGeometry.width(left, b, gap)
            val wR = NumberTowerGeometry.width(right, b, gap)
            val pairGap = b * NumberTowerGeometry.PairGapBlocks
            val leftX = width / 2f - (wL + pairGap + wR) / 2f
            val leftWidth = if (operation == MathOperation.Add) NumberTowerGeometry.width(left + right, b, gap) else wL
            return TowerLayout(b, leftX, leftWidth, leftX + wL + pairGap, wR, pairGap, ground)
        }
    }
}

private fun DrawScope.drawTimes(
    lay: TowerLayout, left: Int, right: Int, intro: Float, join: Float, grow: Float, happy: Float, blink: Float, hop: Float, lift: Float,
) {
    val b = lay.block
    val gapCol = b * NumberTowerGeometry.ColumnGapFraction
    val wT = NumberTowerGeometry.width(right, b, gapCol)
    val gap = lerpF(b * NumberTowerGeometry.TowerGapBlocks, gapCol, join)
    val all = left * wT + (left - 1) * gap
    val x0 = size.width / 2f - all / 2f
    val rows = min(NumberTowerGeometry.ColumnHeight, right)
    for (t in 0 until left) {
        val start = 0.4f + t * 0.45f
        val p = ((intro - start) / 0.5f).coerceIn(0f, 1f)
        if (p <= 0f) continue
        val drop = (1f - springish(intro - start)) * 70.dp.toPx()
        val x = x0 + t * (wT + gap)
        for (i in 0 until right) {
            val s = NumberTowerGeometry.slot(i, b, gapCol)
            drawBlock(Offset(x + s.x, lay.ground + s.y - drop - hop - lift), b, Honey, alpha = (p * 3f).coerceAtMost(1f))
        }
        // Jeder Turm sein Gesicht — bis sie zusammenrücken.
        val top = NumberTowerGeometry.slot(min(4, right - 1), b, gapCol)
        drawSmallFace(Offset(x + top.x, lay.ground + top.y - drop - hop - lift), b, blink, alpha = (p * 3f).coerceAtMost(1f) * (1f - join))
    }
    if (grow > 0f) {
        val box = Offset(x0, lay.ground - rows * b - hop - lift) to Size(all, rows * b)
        drawBigFace(box.first, box.second, b, blink, happy, grow)
    }
}

// ---- Zeichnen ----

private data class BlockPalette(val face: Color, val hi: Color, val lo: Color, val rim: Color) {
    fun mix(other: BlockPalette, t: Float): BlockPalette =
        if (t <= 0f) this else if (t >= 1f) other else BlockPalette(lerp(face, other.face, t), lerp(hi, other.hi, t), lerp(lo, other.lo, t), lerp(rim, other.rim, t))
}

/** Rollenfarben: Honig für die erste Zahl, Himmelblau für die zweite. Weder Gold noch Grün. */
private val Honey = BlockPalette(Color(0xFFF2B35E), Color(0xFFFFD89A), Color(0xFFC9853A), Color(0xFFA86A2A))
private val Sky = BlockPalette(Color(0xFF6FB2E3), Color(0xFFA9D5F4), Color(0xFF3F82B8), Color(0xFF2F6694))
private val SkyGhost = Color(0xE6A9D5F4)
private val CreamGhost = Color(0xCCF8F4EA)
private val PupilInk = Color(0xFF2A1D18)
private val MouthInk = Color(0xFF3A1420)
private val Tongue = Color(0xFFE7788A)
private val Cheek = Color(0x80FF9AA0)

/** Unterkante links von [bottomLeft]; der Block reicht eine Kante nach oben. */
private fun DrawScope.drawBlock(bottomLeft: Offset, b: Float, pal: BlockPalette, alpha: Float = 1f, rotationDeg: Float = 0f) {
    if (alpha <= 0f) return
    val center = Offset(bottomLeft.x + b / 2f, bottomLeft.y - b / 2f)
    rotate(rotationDeg, pivot = center) {
        val tl = Offset(center.x - b / 2f + 0.5f, center.y - b / 2f + 0.5f)
        val sz = Size(b - 1f, b - 1f)
        val r = CornerRadius(b * 0.16f)
        drawRoundRect(
            Brush.linearGradient(0f to pal.hi, 0.45f to pal.face, 1f to pal.lo, start = tl, end = tl + Offset(b, b)),
            topLeft = tl, size = sz, cornerRadius = r, alpha = alpha,
        )
        drawRoundRect(pal.rim, topLeft = tl, size = sz, cornerRadius = r, alpha = alpha * 0.55f, style = Stroke(width = max(1f, b * 0.05f)))
        drawRoundRect(Color.White, topLeft = tl + Offset(b * 0.12f, b * 0.1f), size = Size(b * 0.5f, b * 0.14f), cornerRadius = CornerRadius(b * 0.07f), alpha = alpha * 0.35f)
    }
}

private fun DrawScope.drawGhost(bottomLeft: Offset, b: Float, alpha: Float, color: Color) {
    if (alpha <= 0f) return
    drawRoundRect(
        color,
        topLeft = Offset(bottomLeft.x + 2f, bottomLeft.y - b + 2f),
        size = Size(b - 4f, b - 4f),
        cornerRadius = CornerRadius(b * 0.16f),
        alpha = alpha,
        style = Stroke(width = 1.6.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx()))),
    )
}

/** Das kleine Gesicht auf dem obersten Block der ersten Säule. */
private fun DrawScope.drawSmallFace(bottomLeft: Offset, b: Float, blink: Float, alpha: Float = 1f) {
    if (alpha <= 0f) return
    val c = Offset(bottomLeft.x + b / 2f, bottomLeft.y - b / 2f)
    val eyeH = b * 0.17f * (1f - 0.9f * blink)
    listOf(-1f, 1f).forEach { s ->
        val e = c + Offset(s * b * 0.19f, -b * 0.06f)
        drawOval(Color.White, topLeft = e - Offset(b * 0.15f, eyeH), size = Size(b * 0.3f, max(1f, eyeH * 2f)), alpha = alpha)
        if (blink < 0.6f) {
            drawCircle(PupilInk, radius = b * 0.085f, center = e + Offset(b * 0.02f, b * 0.02f), alpha = alpha)
            drawCircle(Color.White, radius = b * 0.03f, center = e + Offset(b * 0.05f, -b * 0.02f), alpha = alpha)
        }
    }
    drawArc(MouthInk, startAngle = 27f, sweepAngle = 126f, useCenter = false, topLeft = c + Offset(-b * 0.13f, -b * 0.03f), size = Size(b * 0.26f, b * 0.26f), alpha = alpha, style = Stroke(width = max(1.4f, b * 0.07f), cap = StrokeCap.Round))
    listOf(-1f, 1f).forEach { s -> drawOval(Cheek, topLeft = c + Offset(s * b * 0.33f - b * 0.07f, b * 0.055f), size = Size(b * 0.14f, b * 0.09f), alpha = alpha) }
}

private fun DrawScope.drawBigFaceFor(n: Int, bottomLeft: Offset, b: Float, gap: Float, blink: Float, happy: Float, grow: Float) {
    val (cols, rows) = NumberTowerGeometry.faceRect(n)
    val w = cols * b + (cols - 1) * gap
    drawBigFace(Offset(bottomLeft.x, bottomLeft.y - rows * b), Size(w, rows * b), b, blink, happy, grow)
}

/** Das große Gesicht der fertigen Figur: Augen und Mund über mehrere Blöcke, symmetrisch. */
private fun DrawScope.drawBigFace(topLeft: Offset, box: Size, b: Float, blink: Float, happy: Float, grow: Float) {
    val center = topLeft + Offset(box.width / 2f, box.height / 2f)
    val s = 0.6f + 0.4f * grow
    withTransform({ scale(s, s, pivot = center) }) {
        val cx = center.x
        val rx = min(box.width * 0.13f, box.height * 0.15f).coerceAtLeast(b * 0.14f)
        val ry = rx * 1.15f * (1f - 0.9f * blink)
        val ey = topLeft.y + box.height * 0.32f
        listOf(-1f, 1f).forEach { side ->
            val ex = cx + side * max(box.width * 0.22f, rx * 1.15f)
            drawOval(Color(0x2E000000), topLeft = Offset(ex - rx * 1.08f, ey + rx * 0.12f - ry * 1.08f), size = Size(rx * 2.16f, max(1f, ry * 2.16f)), alpha = grow)
            drawOval(Color.White, topLeft = Offset(ex - rx, ey - ry), size = Size(rx * 2f, max(1f, ry * 2f)), alpha = grow)
            if (blink < 0.6f) {
                val pupil = Offset(ex - side * rx * 0.12f, ey + rx * 0.12f)
                drawCircle(PupilInk, radius = rx * 0.5f, center = pupil, alpha = grow)
                drawCircle(Color.White, radius = rx * 0.17f, center = pupil + Offset(rx * 0.18f, -rx * 0.2f), alpha = grow)
            }
        }
        val my = topLeft.y + box.height * 0.62f
        val mr = max(box.width * (0.2f + 0.04f * happy), b * 0.2f)
        if (happy > 0.05f) {
            val mouth = Path().apply {
                moveTo(cx - mr, my)
                quadraticTo(cx, my + mr * (1.1f + 0.3f * happy), cx + mr, my)
                close()
            }
            drawPath(mouth, MouthInk, alpha = grow)
            drawOval(Tongue, topLeft = Offset(cx - mr * 0.45f, my + mr * 0.45f - (mr * 0.18f * happy + 1f)), size = Size(mr * 0.9f, (mr * 0.18f * happy + 1f) * 2f), alpha = grow)
        } else {
            val mouth = Path().apply {
                moveTo(cx - mr, my)
                quadraticTo(cx, my + mr * 0.7f, cx + mr, my)
            }
            drawPath(mouth, MouthInk, alpha = grow, style = Stroke(width = max(2f, b * 0.16f), cap = StrokeCap.Round))
        }
        listOf(-1f, 1f).forEach { side ->
            drawOval(Cheek, topLeft = Offset(cx + side * box.width * 0.36f - rx * 0.5f, my - box.height * 0.02f - rx * 0.3f), size = Size(rx, rx * 0.6f), alpha = grow)
        }
    }
}

/** Ein Zehner (zwei volle Säulen) bekommt einen hellen Rahmen: „das ist zehn". */
private fun DrawScope.drawTenMark(bottomLeft: Offset, b: Float, gap: Float) {
    drawRoundRect(
        IslandCream,
        topLeft = Offset(bottomLeft.x - 4.dp.toPx(), bottomLeft.y - 5 * b - 4.dp.toPx()),
        size = Size(2 * b + gap + 8.dp.toPx(), 5 * b + 8.dp.toPx()),
        cornerRadius = CornerRadius(8.dp.toPx()),
        alpha = 0.7f,
        style = Stroke(width = 2.dp.toPx()),
    )
}

private suspend fun hop(a: Animatable<Float, *>) {
    a.snapTo(0f)
    a.animateTo(1f, tween(TowerHopUpMs))
    a.animateTo(0f, tween(TowerHopDownMs))
}

private fun ease(t: Float): Float = t * t * (3f - 2f * t)
private fun lerpF(a: Float, b: Float, t: Float): Float = a + (b - a) * t

/** Eine gedämpfte Schwingung 0 → 1 fürs Hereinfallen der Mal-Türme (keine Compose-Feder: die Uhr ist linear). */
private fun springish(t: Float): Float {
    if (t <= 0f) return 0f
    val w = 2f * PI.toFloat() * 2f
    val z = 0.45f
    val wd = w * kotlin.math.sqrt(1f - z * z)
    return 1f - exp(-z * w * t) * (cos(wd * t) + z * w / wd * sin(wd * t))
}

private val PlateHeight = 54.dp
private val MaxBlock = 48.dp
private const val HopPx = 22f
private const val TowerHopUpMs = 160
private const val TowerHopDownMs = 260
