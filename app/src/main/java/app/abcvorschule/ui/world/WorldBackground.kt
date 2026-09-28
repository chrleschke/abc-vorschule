package app.abcvorschule.ui.world

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.withInfiniteAnimationFrameMillis
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.abcvorschule.R

/** Die Welt hinter einem Trainer. [TrainerWorld.Paper] zeichnet nichts — dort bleibt der Papiergrund. */
@Composable
fun WorldBackground(world: TrainerWorld, modifier: Modifier = Modifier) {
    when (world) {
        TrainerWorld.Paper -> Unit
        TrainerWorld.DeepSea -> DeepSeaBackground(modifier)
        TrainerWorld.Jungle -> PaintedBackground(R.drawable.world_jungle, JungleFallback, modifier)
        TrainerWorld.ForestNight -> ForestNightBackground(modifier)
        TrainerWorld.Attic -> AtticBackground(modifier)
        TrainerWorld.Workshop -> WorkshopBackground(modifier)
        TrainerWorld.Garden -> GardenBackground(modifier)
        TrainerWorld.Theater -> TheaterBackground(modifier)
        TrainerWorld.Cave -> Box(modifier) {
            PaintedBackground(R.drawable.world_cave, CaveFallback, Modifier.fillMaxSize())
            CaveGlowMotes(Modifier.fillMaxSize())
        }
        TrainerWorld.Clearing -> ClearingBackground(modifier)
    }
}

/**
 * „Bewegung reduzieren" (Animationen aus in den Entwickler-/Bedienungshilfe-Optionen):
 * dann steht die Welt still. Gelesen einmal pro Komposition des Hintergrunds.
 */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
}

/**
 * Sekunden seit dem ersten Frame, für ruhige Dauerbewegung. Gelesen wird der Wert nur in
 * der Zeichenphase — ein Frame zeichnet neu, rekomponiert aber nichts.
 */
@Composable
fun rememberWorldSeconds(still: Boolean): androidx.compose.runtime.State<Float> =
    produceState(0f, still) {
        if (still) return@produceState
        var start = -1L
        while (true) {
            // Die Endlos-Variante: sie meldet Compose eine Daueranimation, damit Tests
            // (InfiniteAnimationPolicy) nicht ewig auf Ruhe warten.
            withInfiniteAnimationFrameMillis { now ->
                if (start < 0) start = now
                value = (now - start) / 1000f
            }
        }
    }

/** Tiefes Meer, gezeichnet statt als Bild: Verlauf, wandernde Lichtstrahlen, langsame Blasen. */
@Composable
private fun DeepSeaBackground(modifier: Modifier) {
    val still = rememberReduceMotion()
    val seconds by rememberWorldSeconds(still)
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to SeaSurface,
                0.35f to SeaUpper,
                0.7f to SeaLower,
                1f to SeaFloor,
            ),
        ),
    ) {
        Canvas(
            Modifier
                .fillMaxSize()
                // Offscreen: die Strahlen blenden per DstIn nach unten aus, und das darf
                // nur ihre eigene Ebene treffen, nicht den Verlauf darunter.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
        ) {
            val t = seconds
            val w = size.width
            val h = size.height
            DeepSeaLight.rays(t).forEach { ray ->
                val origin = Offset(w * ray.x, -4f)
                val half = w * ray.halfWidth
                val length = h * ray.length
                val spread = half * 2.6f
                rotate(degrees = -ray.angleDeg, pivot = origin) {
                    // Im gedrehten Raum steht der Strahl senkrecht: ein Trapez von der
                    // Oberkante nach unten, quer mit wanderndem Lichtkern gefüllt …
                    val left = origin.x - spread
                    val right = origin.x + spread
                    // Jeder Strahl in seiner eigenen Ebene: das Ausblenden unten (DstIn)
                    // darf nur ihn treffen — in einer geteilten Ebene schnitte es auch
                    // den Nachbarstrahl ab und hinterließe harte Kanten.
                    drawContext.canvas.saveLayer(Rect(left, origin.y, right, origin.y + length), Paint())
                    val core = ray.core
                    val light = RayLight.copy(alpha = ray.alpha)
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(origin.x - half, origin.y)
                        lineTo(origin.x + half, origin.y)
                        lineTo(origin.x + spread, origin.y + length)
                        lineTo(origin.x - spread, origin.y + length)
                        close()
                    }
                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(
                            0f to Color.Transparent,
                            (core - 0.3f).coerceAtLeast(0.02f) to light.copy(alpha = ray.alpha * 0.35f),
                            core to light,
                            (core + 0.3f).coerceAtMost(0.98f) to light.copy(alpha = ray.alpha * 0.35f),
                            1f to Color.Transparent,
                            startX = left,
                            endX = right,
                        ),
                    )
                    // … und nach unten weich ausgeblendet, statt mit einer Kante zu enden.
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Black,
                            0.55f to Color.Black,
                            1f to Color.Transparent,
                            startY = origin.y,
                            endY = origin.y + length,
                        ),
                        topLeft = Offset(left, origin.y),
                        size = androidx.compose.ui.geometry.Size(right - left, length),
                        blendMode = BlendMode.DstIn,
                    )
                    drawContext.canvas.restore()
                }
            }
            val stroke = Stroke(width = 1.dp.toPx())
            DeepSeaLight.bubbles(t).forEach { b ->
                drawCircle(
                    color = BubbleRim,
                    radius = b.radiusDp.dp.toPx(),
                    center = Offset(w * b.x, h * b.y),
                    style = stroke,
                )
            }
        }
    }
}

/**
 * Eine gemalte Welt: Dschungel (WebP, 57 KB) oder Pilzhöhle (WebP, 30 KB), beide
 * abgedunkelt und mit beruhigter Mitte eingebacken.
 */
@Composable
private fun PaintedBackground(@DrawableRes image: Int, fallback: Color, modifier: Modifier) {
    Box(modifier = modifier.background(fallback)) {
        Image(
            painter = painterResource(image),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Kopf-Verlauf: Pfeil, Punkte und Fortschritt stehen auf Dunkel, auch wenn das
        // Bild oben helle Blätter oder Lichtstrahlen hat.
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to HeadShade, 0.2f to Color.Transparent)),
        )
    }
}

private val SeaSurface = Color(0xFF0F5068)
private val SeaUpper = Color(0xFF0B3A52)
private val SeaLower = Color(0xFF072838)
private val SeaFloor = Color(0xFF041A26)
private val RayLight = Color(0xFFBEEBF5)
private val BubbleRim = Color(0x47C8EBF5)
private val JungleFallback = Color(0xFF0A1410)
private val CaveFallback = Color(0xFF071A1D)
private val HeadShade = Color(0x8C060812)
