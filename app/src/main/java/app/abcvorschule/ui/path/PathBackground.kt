package app.abcvorschule.ui.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.DuskHorizon
import app.abcvorschule.ui.theme.DuskHorizonLight
import app.abcvorschule.ui.theme.DuskSkyGlow
import app.abcvorschule.ui.theme.DuskSkyTop
import app.abcvorschule.ui.theme.DuskSkyUpper
import app.abcvorschule.ui.theme.DuskSun
import app.abcvorschule.ui.theme.HillFar
import app.abcvorschule.ui.theme.HillMid
import app.abcvorschule.ui.theme.HillNear
import app.abcvorschule.ui.theme.TreeCrown
import app.abcvorschule.ui.theme.TreeTrunk
import app.abcvorschule.ui.world.rememberReduceMotion
import app.abcvorschule.ui.world.rememberWorldSeconds
import kotlin.math.sin

private const val SunFx = 0.74f
private const val SunFy = 0.69f
private val SunRadius = 30.dp
private val SunHaloRadius = 80.dp
private const val SunHaloAlpha = 0.45f

/** Line segments a hill's wave is sampled with — 24 was the old loop's step count. */
private const val HillSegments = 24

private val TreeCrownRadius = 13.dp
private val TreeTrunkWidth = 5.dp

/** Wo Bäume auf den Hügeln stehen, als Anteil der Breite. */
private val MidTrees = listOf(0.08f, 0.22f, 0.57f, 0.93f)
private val NearTrees = listOf(0.14f, 0.31f, 0.68f, 0.86f)

/** Sterne im oberen Himmel: (x, y, Radius dp, Deckkraft), fest verteilt. */
private val Stars: List<FloatArray> = run {
    val rnd = java.util.Random(46)
    List(60) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.55f, 0.6f + rnd.nextFloat(), 0.25f + rnd.nextFloat() * 0.6f) }
}

private val LanternGlow = Color(0xFFFFBE64)
private val LanternPaperTop = Color(0xFFE0703C)
private val LanternPaperMid = Color(0xFFFFB866)
private val LanternFlame = Color(0xFFFFF3C4)
private val LanternRim = Color(0xFFB8612F)

/**
 * Die Abendlandschaft hinter dem Pfad (PRODUCT_PRINCIPLES §5): Himmel, tief stehende
 * Sonne und Sterne stehen fest, die Himmelslaternen steigen hinter den Hügeln auf, und
 * die drei Hügelbänder schieben sich beim Scrollen mit Parallaxe. Die Laternen liegen
 * **zwischen** Himmel und Hügeln — sie tauchen hinter den Bergen auf.
 */
@Composable
fun PathBackground(
    scrollOffset: () -> Int,
    loops: LanternLoops,
    modifier: Modifier = Modifier,
    /**
     * Sonne und Laternen: auf dem Pfad Stimmung, auf dem End-Screen ausgeblendet — dort
     * gehört der Himmel dem Sternbild, und eine helle Scheibe neben dem Lautsprecher läse
     * sich wie ein Knopf (wie vorher der Mond beim Rechnen).
     */
    sunAndLanterns: Boolean = true,
) {
    val still = rememberReduceMotion()
    val seconds by rememberWorldSeconds(still)
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to DuskSkyTop,
                    0.38f to DuskSkyUpper,
                    0.58f to DuskSkyGlow,
                    0.68f to DuskHorizon,
                    0.76f to DuskHorizonLight,
                ),
            )
            Stars.forEach { (fx, fy, r, a) ->
                drawCircle(Cream.copy(alpha = a), radius = r.dp.toPx(), center = Offset(fx * size.width, fy * size.height))
            }
            val sunCenter = Offset(SunFx * size.width, SunFy * size.height)
            val haloRadius = SunHaloRadius.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(DuskSun.copy(alpha = SunHaloAlpha), DuskSun.copy(alpha = 0f)),
                    center = sunCenter,
                    radius = haloRadius,
                ),
                radius = haloRadius,
                center = sunCenter,
            )
            if (sunAndLanterns) drawCircle(color = DuskSun, radius = SunRadius.toPx(), center = sunCenter)
        }

        // Himmelslaternen: nur in der Zeichenphase gelesen, ein Frame rekomponiert nichts.
        // Eigene Ebene, sonst zeichnete jeder Frame Himmel, Hügel und Kopfzeile mit neu.
        if (sunAndLanterns) Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            loops.now = seconds
            loops.still = still
            SkyLanterns.at(seconds).forEach { lantern ->
                val start = loops.starts[lantern.id]
                val progress = if (start == null) -1f else (seconds - start) / SkyLanterns.LoopS
                drawLantern(lantern, loop = progress.takeIf { it in 0f..1f })
            }
        }

        HillBand(color = HillFar, baseFraction = 0.72f, amplitude = 34f, parallax = 0.05f, scrollOffset = scrollOffset)
        HillBand(color = HillMid, baseFraction = 0.82f, amplitude = 46f, parallax = 0.10f, scrollOffset = scrollOffset, trees = MidTrees)
        HillBand(color = HillNear, baseFraction = 0.92f, amplitude = 28f, parallax = 0.15f, scrollOffset = scrollOffset, trees = NearTrees)
    }
}

/** Schleifenradius als Vielfaches der Laternenbreite: nahe Laternen fliegen größere Loopings. */
private const val LoopRadiusFactor = 2.2f

/** Eine Papierlaterne: oben schmal, unten rund, innen hell — und ein Leuchten drumherum. */
private fun DrawScope.drawLantern(l: SkyLanterns.Lantern, loop: Float?) {
    val w = size.width * l.width
    val h = w * 1.33f
    // Beim Looping: Versatz auf der Schleife und eine volle Drehung um die Mitte.
    val (loopX, loopY, loopTurn) = loop?.let { SkyLanterns.loop(it) } ?: Triple(0f, 0f, 0f)
    val radius = w * LoopRadiusFactor
    val x = size.width * l.x + loopX * radius
    val y = size.height * l.y + loopY * radius
    rotate(degrees = loopTurn, pivot = Offset(x, y + h * 0.45f)) {
        drawLanternBody(l, x, y, w, h)
    }
}

private fun DrawScope.drawLanternBody(l: SkyLanterns.Lantern, x: Float, y: Float, w: Float, h: Float) {
    val glowRadius = w * (1.2f + l.depth * 0.9f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to LanternGlow.copy(alpha = (0.35f + l.depth * 0.4f) * l.alpha),
            1f to LanternGlow.copy(alpha = 0f),
            center = Offset(x, y + h * 0.55f),
            radius = glowRadius,
        ),
        radius = glowRadius,
        center = Offset(x, y + h * 0.55f),
    )
    rotate(degrees = l.rotationDeg, pivot = Offset(x, y)) {
        val body = Path().apply {
            moveTo(x - w * 0.3f, y)
            lineTo(x + w * 0.3f, y)
            lineTo(x + w * 0.5f, y + h * 0.78f)
            quadraticTo(x, y + h * 1.02f, x - w * 0.5f, y + h * 0.78f)
            close()
        }
        drawPath(
            body,
            brush = Brush.verticalGradient(
                0f to LanternPaperTop.copy(alpha = l.alpha),
                0.55f to LanternPaperMid.copy(alpha = l.alpha),
                1f to LanternFlame.copy(alpha = l.alpha),
                startY = y,
                endY = y + h,
            ),
        )
        drawRect(LanternRim.copy(alpha = l.alpha), topLeft = Offset(x - w * 0.32f, y - h * 0.05f), size = Size(w * 0.64f, h * 0.08f))
    }
}

@Composable
private fun HillBand(
    color: Color,
    baseFraction: Float,
    amplitude: Float,
    parallax: Float,
    scrollOffset: () -> Int,
    trees: List<Float> = emptyList(),
) {
    val hill = remember { Path() }
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                val base = size.height * baseFraction - scrollOffset() * parallax
                hill.rewind()
                hill.apply {
                    moveTo(0f, size.height)
                    lineTo(0f, base)
                    for (i in 0..HillSegments) {
                        val fx = i.toFloat() / HillSegments
                        lineTo(size.width * fx, base - amplitude * sin(fx * 3.4f))
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                // Bäume vor dem Hügel gezeichnet heißt: hinter ihm stehend — der
                // Hügel schneidet ihre Stämme ab, die Kronen ragen darüber.
                val crown = TreeCrownRadius.toPx()
                val trunkHalf = TreeTrunkWidth.toPx() / 2f
                trees.forEach { fx ->
                    val tx = size.width * fx
                    val ty = base - amplitude * sin(fx * 3.4f)
                    drawRect(TreeTrunk, topLeft = Offset(tx - trunkHalf, ty - crown), size = Size(trunkHalf * 2f, crown * 1.4f))
                    drawCircle(TreeCrown, radius = crown, center = Offset(tx, ty - crown * 1.3f))
                    drawCircle(TreeCrown, radius = crown * 0.72f, center = Offset(tx - crown * 0.6f, ty - crown * 0.9f))
                }
                drawPath(hill, color = color)
            },
    )
}
