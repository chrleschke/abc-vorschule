package app.abcvorschule.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Drei gezeichnete Nachtwelten (PRODUCT_PRINCIPLES §10): keine Bilddateien, alles aus
 * Verläufen und wenigen Formen — 0 KB im Paket, und die ruhige Mitte ist eingebaut.
 * Bewegung nur ganz langsam und kontrastarm (Zyklen ≥ 8 s), bei „Bewegung reduzieren"
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
 * Wort-Detektiv: ein Dachboden bei Nacht. Durch ein rundes Fenster oben rechts fällt ein
 * Mondstrahl schräg in den Raum, in ihm schweben langsam Staubkörner. Das Wort selbst
 * liegt in einem hellen Lichtfleck ([lightPool]) — die Taschenlampe findet es.
 */
@Composable
internal fun AtticBackground(modifier: Modifier, taps: WorldTaps?) {
    val still = rememberReduceMotion()
    val seconds by rememberWorldSeconds(still)
    Canvas(modifier.graphicsLayer()) {
        drawRect(Brush.linearGradient(0f to AtticTop, 0.6f to AtticMid, 1f to AtticLow, start = Offset.Zero, end = Offset(size.width, size.height)))
        // Dachbalken als dunkle Schrägen links und rechts.
        drawBeam(from = Offset(-size.width * 0.1f, size.height * 0.02f), to = Offset(size.width * 0.35f, size.height * 0.28f))
        drawBeam(from = Offset(size.width * 1.1f, size.height * 0.02f), to = Offset(size.width * 0.66f, size.height * 0.28f))
        val window = Offset(size.width * 0.82f, size.height * 0.23f)
        val wr = 24.dp.toPx()
        // Der Mondstrahl: vom Fenster schräg zur Mitte, weich ausgeblendet.
        val target = Offset(size.width * 0.5f, size.height * 0.5f)
        val beam = Path().apply {
            moveTo(window.x - wr * 0.7f, window.y - wr * 0.4f)
            lineTo(window.x + wr * 0.7f, window.y + wr * 0.4f)
            lineTo(target.x + size.width * 0.3f, target.y + size.height * 0.08f)
            lineTo(target.x - size.width * 0.34f, target.y - size.height * 0.02f)
            close()
        }
        drawPath(beam, Brush.linearGradient(0f to BeamLight.copy(alpha = 0.22f), 1f to BeamLight.copy(alpha = 0f), start = window, end = target + Offset(0f, size.height * 0.12f)))
        drawCircle(Brush.radialGradient(0f to BeamLight.copy(alpha = 0.5f), 1f to Color.Transparent, center = window, radius = wr * 2.6f), radius = wr * 2.6f, center = window)
        drawCircle(WindowGlass, radius = wr, center = window)
        val bar = Stroke(width = 3.dp.toPx())
        drawCircle(WindowFrame, radius = wr, center = window, style = Stroke(width = 5.dp.toPx()))
        drawLine(WindowFrame, window - Offset(wr, 0f), window + Offset(wr, 0f), strokeWidth = bar.width)
        drawLine(WindowFrame, window - Offset(0f, wr), window + Offset(0f, wr), strokeWidth = bar.width)
        // Staub im Strahl: treibt ganz langsam (Perioden 17–31 s).
        Dust.forEach { (t0, side, period, r) ->
            val p = ((seconds / period + t0) % 1f)
            val along = window + (target - window) * p
            val drift = sin(seconds / (period * 0.7f) * 2f * PI.toFloat() + t0 * 9f) * 18.dp.toPx()
            val pos = along + Offset(side * 40.dp.toPx() * p + drift * 0.3f, drift * 0.5f)
            val a = 0.45f * sin(PI.toFloat() * p)
            drawCircle(BeamLight, alpha = a, radius = r.dp.toPx(), center = pos)
        }
        // Angetippt wirbelt Staub auf: eine Handvoll Körner dreht sich spiralförmig vom
        // Finger weg, steigt ein wenig und sinkt verblassend wieder ab.
        taps?.let { tt ->
            tt.now = seconds
            tt.still = still
            tt.forEachRecent(seconds, DustSwirlS) { tap, age ->
                val p = age / DustSwirlS
                val turn = if (tap.seed % 2 == 0) 1f else -1f
                for (k in 0 until 14) {
                    val angle = k / 14f * 2f * PI.toFloat() + turn * age * 1.6f
                    val reach = (8 + 56 * (1f - (1f - p) * (1f - p)) * (0.6f + 0.4f * tapNoise(tap.seed, k))).dp.toPx()
                    val lift = -18.dp.toPx() * sin(PI.toFloat() * p)
                    val c = tap.at + Offset(kotlin.math.cos(angle) * reach, sin(angle) * reach * 0.6f + lift)
                    drawCircle(BeamLight, alpha = 0.55f * (1f - p), radius = (0.9f + tapNoise(tap.seed, k + 30)).dp.toPx(), center = c)
                }
            }
        }
    }
}

private const val DustSwirlS = 2.6f

private fun DrawScope.drawBeam(from: Offset, to: Offset) {
    drawLine(AtticBeam, from, to, strokeWidth = 26.dp.toPx())
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

private val AtticTop = Color(0xFF221B2E)
private val AtticMid = Color(0xFF15111D)
private val AtticLow = Color(0xFF0C0A12)
private val AtticBeam = Color(0xFF0A0810)
private val BeamLight = Color(0xFFFFF1D2)
private val WindowGlass = Color(0xFF3B4466)
private val WindowFrame = Color(0xFF2A2030)

/** (Startphase, Seitenversatz −1…1, Periode s, Radius dp) */
private val Dust: List<FloatArray> = run {
    val rnd = java.util.Random(19)
    List(16) { floatArrayOf(rnd.nextFloat(), rnd.nextFloat() * 2f - 1f, 17f + rnd.nextFloat() * 14f, 0.8f + rnd.nextFloat() * 1.2f) }
}

private val WoodTop = Color(0xFF3A271B)
private val WoodMid = Color(0xFF24170F)
private val WoodLow = Color(0xFF1A100A)
private val Grain = Color(0xFF000000)
private val LampLight = Color(0xFFFFC478)
