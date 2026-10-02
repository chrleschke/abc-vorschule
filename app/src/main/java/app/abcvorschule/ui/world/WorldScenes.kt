package app.abcvorschule.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.rewards.drawGlint
import kotlin.math.PI
import kotlin.math.sin

/**
 * Drei gezeichnete Nachtwelten (PRODUCT_PRINCIPLES §10): keine Bilddateien, alles aus
 * Verläufen und wenigen Formen — 0 KB im Paket, und die ruhige Mitte ist eingebaut.
 * Bewegung nur ganz langsam und kontrastarm (Zyklen ≥ 8 s; nur die wenigen Sterne des
 * Sternenhimmels funkeln ab 4 s, wie seine Spec es festlegt), bei „Bewegung reduzieren"
 * steht alles.
 */

/** Rechnen: eine Wiese am Waldrand bei Nacht — Sterne, Baumsilhouetten unten. */
@Composable
internal fun ForestNightBackground(modifier: Modifier) {
    val still = rememberReduceMotion()
    val seconds by rememberWorldSeconds(still)
    Canvas(modifier.graphicsLayer()) {
        drawRect(Brush.verticalGradient(0f to NightTop, 0.55f to NightMid, 1f to NightLow))
        // Kein Mond: eine helle Scheibe neben dem Lautsprecher las sich wie ein zweiter
        // Knopf (Nutzer-Feedback). Der Himmel trägt die Nacht allein mit Sternen.
        // Sterne: wenige funkeln ganz langsam (8–14 s), die meisten stehen still.
        NightStars.forEach { (fx, fy, r, a, period) ->
            val twinkle = if (period > 0f) 0.75f + 0.25f * sin(seconds / period * 2f * PI.toFloat() + fx * 20f) else 1f
            drawCircle(Star, alpha = a * twinkle, radius = r.dp.toPx(), center = Offset(fx * size.width, fy * size.height))
        }
        // Hügel und Bäume ganz unten, links und rechts — die Mitte bleibt frei für Aufgabe und Ziffern.
        val base = size.height * 0.9f
        val hill = Path().apply {
            moveTo(0f, size.height)
            lineTo(0f, base)
            for (i in 0..24) {
                val fx = i / 24f
                lineTo(size.width * fx, base - 26.dp.toPx() * sin(fx * 3.1f))
            }
            lineTo(size.width, size.height)
            close()
        }
        listOf(0.06f, 0.16f, 0.84f, 0.95f).forEachIndexed { i, fx ->
            val crown = (16 + (i % 2) * 6).dp.toPx()
            val tx = size.width * fx
            val ty = base - 26.dp.toPx() * sin(fx * 3.1f)
            drawRect(TreeDark, topLeft = Offset(tx - 3.dp.toPx(), ty - crown), size = Size(6.dp.toPx(), crown * 1.3f))
            drawCircle(TreeDark, radius = crown, center = Offset(tx, ty - crown * 1.4f))
            drawCircle(TreeDark, radius = crown * 0.7f, center = Offset(tx + crown * 0.6f, ty - crown * 0.9f))
        }
        drawPath(hill, HillDark)
    }
}

/**
 * Wo das Teleskop des Sternenhimmels steht — unten links, und dort gibt es **keinen
 * Stern** (Nutzerentscheidung): weder Himmelssterne noch Sternbild-Linien noch das
 * Funkeln beim Antippen, und der Wort-Detektiv hält Silhouetten-Reihe und Flugbahn
 * davon fern (`WordDetectiveLayout.silhouetteRow`, Flug-Bogen nach rechts). In dp vom
 * linken und unteren Rand der Welt.
 */
object StarsScene {
    const val TelescopeZoneWidthDp = 92f
    const val TelescopeZoneHeightDp = 170f
}

/**
 * Wort-Detektiv: Sternenhimmel mit Deko-Teleskop (Spec
 * `2026-10-02-wort-detektiv-sternenhimmel-design.md`) — „Finden" heißt hier: einen Stern
 * am Himmel entdecken. Nachtblau → Violett, eine zarte Milchstraße als diagonales Band,
 * wenige Sterne, die ganz ruhig funkeln (4–7 s), zwei schwache Sternbild-Linien, dunkler
 * Horizont, unten links ein Teleskop auf Dreibein als Silhouette (ohne Funktion). Das
 * Wort selbst liegt auf einer ruhigen dunklen Zone (`calmPool`), die zeichnet der Trainer.
 */
@Composable
internal fun StarsBackground(modifier: Modifier, taps: WorldTaps?) {
    val still = rememberReduceMotion()
    val seconds by rememberWorldSeconds(still)
    Canvas(modifier.graphicsLayer()) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(0f to SkyTop, 0.65f to SkyMid, 1f to SkyLow))
        val zone = Rect(
            left = 0f,
            top = h - StarsScene.TelescopeZoneHeightDp.dp.toPx(),
            right = StarsScene.TelescopeZoneWidthDp.dp.toPx(),
            bottom = h,
        ).inflate(8.dp.toPx())
        fun free(c: Offset) = !zone.contains(c)
        // Die Milchstraße: ein weiches Band von links oben nach rechts, im oberen Drittel
        // weit weg vom Teleskop. Gestapelte Striche abnehmender Breite statt einer Fläche:
        // so läuft das Band an seinen Rändern weich aus, statt mit einer Kante zu enden,
        // und an beiden Enden blendet es quer aus.
        val band = Path().apply {
            moveTo(-0.1f * w, 0.25f * h)
            cubicTo(0.31f * w, 0.37f * h, 0.67f * w, 0.2f * h, 1.1f * w, 0.33f * h)
        }
        val fade = Brush.horizontalGradient(
            0f to Milky.copy(alpha = 0f),
            0.5f to Milky.copy(alpha = 0.018f),
            1f to Milky.copy(alpha = 0f),
        )
        for (i in 0 until MilkyLayers) {
            val width = 0.13f * h * (1f - i / MilkyLayers.toFloat())
            drawPath(band, fade, style = Stroke(width = width, cap = StrokeCap.Round))
        }
        // Sternbild-Linien zuerst, damit ihre Sterne darüber liegen.
        Constellations.forEach { line ->
            line.zipWithNext().forEach { (a, b) ->
                val from = Offset(SkyStars[a][0] * w, SkyStars[a][1] * h)
                val to = Offset(SkyStars[b][0] * w, SkyStars[b][1] * h)
                if (free(from) && free(to)) drawLine(Milky.copy(alpha = 0.35f), from, to, strokeWidth = 1.dp.toPx())
            }
        }
        SkyStars.forEach { (fx, fy, r, a, period) ->
            val c = Offset(fx * w, fy * h)
            if (!free(c)) return@forEach
            // Funkeln zwischen rund 30 % und 90 % wie im Mockup, nur die wenigen mit Periode.
            val twinkle = if (period > 0f) 0.65f + 0.35f * sin(seconds / period * 2f * PI.toFloat() + fx * 17f) else 1f
            drawCircle(SkyStar, alpha = a * twinkle, radius = r.dp.toPx(), center = c)
        }
        // Horizont: ein flacher dunkler Hügelzug.
        val hill = Path().apply {
            moveTo(0f, h - 84.dp.toPx())
            cubicTo(0.23f * w, h - 114.dp.toPx(), 0.46f * w, h - 100.dp.toPx(), 0.67f * w, h - 84.dp.toPx())
            cubicTo(0.88f * w, h - 68.dp.toPx(), 0.92f * w, h - 74.dp.toPx(), w, h - 88.dp.toPx())
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        drawPath(hill, Horizon)
        drawTelescope(Offset(40.dp.toPx(), h - 120.dp.toPx()))
        // Angetippt funkeln die nächsten paar Sterne kurz auf, leicht nacheinander. Kein
        // Stern in der Nähe: ein kleines Glanzlicht am Finger — nur nie am Teleskop.
        taps?.let { tt ->
            tt.now = seconds
            tt.still = still
            tt.forEachRecent(seconds, StarFlashS) { tap, age ->
                val reach = 150.dp.toPx()
                val near = SkyStars.asSequence()
                    .map { Offset(it[0] * w, it[1] * h) }
                    .filter { free(it) && (it - tap.at).getDistance() < reach }
                    .sortedBy { (it - tap.at).getDistance() }
                    .take(4)
                    .toList()
                val targets = near.ifEmpty { if (free(tap.at)) listOf(tap.at) else emptyList() }
                targets.forEachIndexed { k, c ->
                    val env = tapEnvelope((age - k * 0.12f).coerceAtLeast(0f), rise = 0.15f, decay = 0.55f)
                    drawGlint(c, size = (5f + 2f * tapNoise(tap.seed, k)).dp.toPx() * env, alpha = env)
                }
            }
        }
    }
}

private const val StarFlashS = 3f

/** So viele übereinander gezeichnete Striche ergeben die Milchstraße. */
private const val MilkyLayers = 10

/**
 * Das Teleskop als Silhouette: Rohr 40° schräg nach rechts oben, Dreibein darunter.
 * Ganz innerhalb von [StarsScene]s Zone (rechte Kante bei ~90 dp, oben bei ~163 dp).
 */
private fun DrawScope.drawTelescope(pivot: Offset) {
    val leg = 4.dp.toPx()
    listOf(Offset(-14f, 52f), Offset(16f, 52f), Offset(0f, 54f)).forEach { (dx, dy) ->
        drawLine(TelescopeBody, pivot, pivot + Offset(dx.dp.toPx(), dy.dp.toPx()), strokeWidth = leg, cap = StrokeCap.Round)
    }
    rotate(-40f, pivot = pivot) {
        fun part(x: Float, y: Float, bw: Float, bh: Float, r: Float, color: Color) = drawRoundRect(
            color,
            topLeft = pivot + Offset(x.dp.toPx(), y.dp.toPx()),
            size = Size(bw.dp.toPx(), bh.dp.toPx()),
            cornerRadius = CornerRadius(r.dp.toPx()),
        )
        part(-7f, -7f, 52f, 15f, 4f, TelescopeBody)
        part(44f, -10f, 11f, 21f, 3f, TelescopeRim)
        part(-14f, -4f, 8f, 9f, 2f, TelescopeRim)
        drawCircle(TelescopeLens, alpha = 0.35f, radius = 6.dp.toPx(), center = pivot + Offset(55.dp.toPx(), 0.5f.dp.toPx()))
    }
}

/**
 * Wort-Bauer: eine Werkbank am Abend — dunkles Holz mit Maserung, darüber der warme
 * Lichtkegel einer Lampe, der ganz langsam atmet (9 s).
 */
@Composable
internal fun WorkshopBackground(modifier: Modifier, taps: WorldTaps?) {
    val still = rememberReduceMotion()
    val seconds by rememberWorldSeconds(still)
    Box(modifier.background(Brush.verticalGradient(0f to WoodTop, 0.6f to WoodMid, 1f to WoodLow))) {
        // Maserung in einer eigenen Ebene: sie liest `seconds` nicht, wird also nur bei
        // einer neuen Größe neu gezeichnet. In der Lampen-Ebene entstanden sonst jeden
        // Frame rund 27 neue Paths mit je ~100 Punkten, für ein Bild, das stillsteht.
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            // Maserung: feine, leicht wellige Linien in Längsrichtung.
            val step = 15.dp.toPx()
            var x = step * 0.5f
            var i = 0
            while (x < size.width) {
                val grain = Path().apply {
                    moveTo(x, 0f)
                    var y = 0f
                    while (y <= size.height) {
                        lineTo(x + sin(y / 90f + i) * 3.dp.toPx(), y)
                        y += 24f
                    }
                }
                drawPath(grain, Grain.copy(alpha = if (i % 3 == 0) 0.1f else 0.05f), style = Stroke(width = 1.dp.toPx()))
                x += step * (0.8f + (i % 4) * 0.2f)
                i++
            }
        }
        Canvas(Modifier.fillMaxSize().graphicsLayer()) {
            val breath = 1f + 0.05f * sin(seconds / 9f * 2f * PI.toFloat())
            // Angetippt schwingt die Lampe: ihr Lichtkegel pendelt zur Seite des Tipps
            // hin und klingt aus.
            var swing = 0f
            taps?.let { tt ->
                tt.now = seconds
                tt.still = still
                tt.forEachRecent(seconds, LampSwingS) { tap, age ->
                    val side = if (tap.at.x < size.width / 2f) -1f else 1f
                    swing += side * 30.dp.toPx() * sin(age / 2f * 2f * PI.toFloat()) * kotlin.math.exp(-age / 1.6f)
                }
            }
            val lamp = Offset(size.width * 0.5f + swing, size.height * 0.34f)
            val lr = size.width * 0.75f * breath
            drawCircle(
                Brush.radialGradient(0f to LampLight.copy(alpha = 0.42f), 0.6f to LampLight.copy(alpha = 0.12f), 1f to Color.Transparent, center = lamp, radius = lr),
                radius = lr,
                center = lamp,
            )
            // Oben etwas dunkler, damit die helle Kopfzeile trägt.
            drawRect(Brush.verticalGradient(0f to Color(0x8C060812), 0.18f to Color.Transparent))
        }
    }
}

private const val LampSwingS = 5f

private val NightTop = Color(0xFF1B2452)
private val NightMid = Color(0xFF141C40)
private val NightLow = Color(0xFF0B1028)
private val Star = Color(0xFFF7F1E3)
private val TreeDark = Color(0xFF070B1A)
private val HillDark = Color(0xFF0A0F22)

/** (x, y, Radius dp, Deckkraft, Funkelperiode s — 0 = steht still) */
private val NightStars: List<FloatArray> = run {
    val rnd = java.util.Random(52)
    List(70) {
        floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.8f, 0.6f + rnd.nextFloat(), 0.25f + rnd.nextFloat() * 0.55f, if (it % 9 == 0) 8f + rnd.nextFloat() * 6f else 0f)
    }
}

private val SkyTop = Color(0xFF090D24)
private val SkyMid = Color(0xFF171D48)
private val SkyLow = Color(0xFF2A2348)
private val Milky = Color(0xFF8F9BD8)
private val SkyStar = Color(0xFFFFFFFF)
private val Horizon = Color(0xFF070A18)
private val TelescopeBody = Color(0xFF2C3352)
private val TelescopeRim = Color(0xFF3B4570)
private val TelescopeLens = Color(0xFF9FB0E8)

/**
 * Himmelssterne (x, y als Anteil, Radius dp, Deckkraft, Funkelperiode s — 0 = steht
 * still). Die ersten neun sind die Lagen aus dem Mockup, fünf davon tragen die beiden
 * Sternbilder ([Constellations]); dahinter wenige blasse, die stillstehen.
 */
private val SkyStars: List<FloatArray> = run {
    val mock = listOf(
        floatArrayOf(0.10f, 0.166f, 1.4f, 0.9f, 4.0f),
        floatArrayOf(0.28f, 0.130f, 1.0f, 0.8f, 5.5f),
        floatArrayOf(0.46f, 0.201f, 0.9f, 0.8f, 6.6f),
        floatArrayOf(0.77f, 0.273f, 1.0f, 0.8f, 4.0f),
        floatArrayOf(0.87f, 0.178f, 1.5f, 0.9f, 5.5f),
        floatArrayOf(0.15f, 0.355f, 1.2f, 0.8f, 6.6f),
        floatArrayOf(0.92f, 0.664f, 1.1f, 0.8f, 4.6f),
        floatArrayOf(0.08f, 0.711f, 1.2f, 0.8f, 5.1f),
        floatArrayOf(0.64f, 0.770f, 1.0f, 0.7f, 6.1f),
    )
    val rnd = java.util.Random(31)
    mock + List(36) {
        floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 0.86f, 0.5f + rnd.nextFloat() * 0.6f, 0.15f + rnd.nextFloat() * 0.3f, 0f)
    }
}

/** Zwei schwache Sternbilder, als Indizes in [SkyStars]. */
private val Constellations = listOf(listOf(0, 1, 2), listOf(3, 4))

private val WoodTop = Color(0xFF3A271B)
private val WoodMid = Color(0xFF24170F)
private val WoodLow = Color(0xFF1A100A)
private val Grain = Color(0xFF000000)
private val LampLight = Color(0xFFFFC478)
