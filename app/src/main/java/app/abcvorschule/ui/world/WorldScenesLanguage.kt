package app.abcvorschule.ui.world

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Die Welten der Sprach-Trainer (PRODUCT_PRINCIPLES §10), gezeichnet wie
 * [ForestNightBackground]: keine Bilddatei, Bewegung nur in langen Zyklen (≥ 7 s),
 * bei „Bewegung reduzieren" steht alles.
 */

private val Tau = (2 * PI).toFloat()

/**
 * Satz-Architekt: ein Garten in der blauen Stunde. Oben Sterne, am Horizont ein
 * letzter warmer Streifen, davor Hügel mit runden Baumgruppen. Leine und Pfosten
 * zeichnet der Trainer selbst ([app.abcvorschule.ui.exercise.ClothesLine]), weil
 * sie an der gemessenen Peg-Reihe hängen müssen.
 */
@Composable
internal fun GardenBackground(modifier: Modifier) {
    val still = rememberReduceMotion()
    val seconds by rememberWorldSeconds(still)
    Canvas(modifier.graphicsLayer()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.verticalGradient(
                0f to DuskTop,
                0.34f to DuskUpper,
                0.58f to DuskViolet,
                0.74f to DuskGlow,
                1f to DuskGlow,
            ),
        )
        GardenStars.forEach { (fx, fy, r, a, period) ->
            val twinkle = if (period > 0f) 0.7f + 0.3f * sin(seconds / period * Tau + fx * 20f) else 1f
            drawCircle(StarLight, alpha = a * twinkle, radius = r.dp.toPx(), center = Offset(fx * w, fy * h * 0.6f))
        }
        // Baumgruppen hinter dem Hügelkamm, links höher als rechts (keine Spiegelung).
        val ridge = h * 0.74f
        drawTreeGroup(Offset(w * 0.2f, ridge), 1f, GardenTreeFar)
        drawTreeGroup(Offset(w * 0.83f, ridge + 4.dp.toPx()), 0.75f, GardenTreeFar)
        val hill = Path().apply {
            moveTo(0f, h)
            lineTo(0f, ridge)
            for (i in 0..24) {
                val fx = i / 24f
                lineTo(w * fx, ridge + 8.dp.toPx() * sin(fx * 5.2f + 0.6f))
            }
            lineTo(w, h)
            close()
        }
        drawPath(hill, GardenHill)
        val lawn = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.82f)
            quadraticTo(w * 0.5f, h * 0.79f, w, h * 0.82f)
            lineTo(w, h)
            close()
        }
        drawPath(lawn, GardenLawn)
    }
}

private fun DrawScope.drawTreeGroup(foot: Offset, scale: Float, color: Color) {
    val r = 26.dp.toPx() * scale
    drawRect(color, topLeft = Offset(foot.x - 2.dp.toPx(), foot.y - r * 1.6f), size = Size(4.dp.toPx(), r * 1.7f))
    drawCircle(color, radius = r, center = Offset(foot.x, foot.y - r * 1.5f))
    drawCircle(color, radius = r * 0.75f, center = Offset(foot.x - r * 0.95f, foot.y - r * 0.9f))
    drawCircle(color, radius = r * 0.62f, center = Offset(foot.x + r * 0.95f, foot.y - r * 0.8f))
}

/**
 * Satz-Versteher: ein Puppentheater. Dunkler Bühnenraum, links und rechts roter
 * Samtvorhang, der ganz leicht atmet (7 s), oben ein Lambrequin und eine Lichterkette,
 * deren Birnen langsam glimmen. Die Bühnenbretter unter den Bildkarten zeichnet der
 * Trainer, damit sie genau unter den Karten liegen.
 */
@Composable
internal fun TheaterBackground(modifier: Modifier) {
    val still = rememberReduceMotion()
    val seconds by rememberWorldSeconds(still)
    Canvas(modifier.graphicsLayer()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                0f to StageBack,
                0.6f to StageMid,
                1f to StageDark,
                center = Offset(w * 0.5f, h * 0.45f),
                radius = h * 0.6f,
            ),
        )
        val top = h * 0.3f
        val bottom = h * 0.8f
        val sway = sin(seconds / 7f * Tau) * 3.dp.toPx()
        drawCurtain(left = true, x = 0f, width = w * 0.2f, top = top, bottom = bottom, hemShift = sway)
        drawCurtain(left = false, x = w * 0.8f, width = w * 0.2f, top = top, bottom = bottom, hemShift = -sway)
        // Lambrequin: eine Reihe Samtbögen über die ganze Breite.
        // Schmal gehalten und unter dem Lautsprecher: nur ein Saum, kein Balken.
        val valanceTop = h * 0.285f
        val scallop = w / 11f
        val band = 6.dp.toPx()
        drawRect(CurtainMid, topLeft = Offset(0f, valanceTop - band), size = Size(w, band * 1.6f))
        for (i in 0 until 11) {
            val c = Offset(scallop * (i + 0.5f), valanceTop)
            drawArc(
                brush = Brush.verticalGradient(0f to CurtainLight, 1f to CurtainMid, startY = valanceTop, endY = valanceTop + scallop * 0.45f),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset(c.x - scallop * 0.5f, c.y - scallop * 0.4f),
                size = Size(scallop, scallop * 0.8f),
            )
        }
        // Lichterkette knapp über dem Lambrequin, leicht durchhängend.
        val bulbs = 9
        for (i in 0 until bulbs) {
            val t = i / (bulbs - 1f)
            val x = w * (0.05f + 0.9f * t)
            val y = valanceTop - band - 4.dp.toPx() + sin(t * PI.toFloat()) * 8.dp.toPx()
            val glow = 0.75f + 0.25f * sin(seconds / 6f * Tau + i * 0.9f)
            val c = Offset(x, y)
            val gr = 12.dp.toPx()
            drawCircle(Brush.radialGradient(0f to BulbGlow.copy(alpha = 0.45f * glow), 1f to Color.Transparent, center = c, radius = gr), radius = gr, center = c)
            drawCircle(BulbLight, alpha = glow, radius = 3.dp.toPx(), center = c)
        }
    }
}

/** Ein Vorhangflügel: senkrechte Falten aus hellen und dunklen Bahnen, unten schräg gerafft. */
private fun DrawScope.drawCurtain(left: Boolean, x: Float, width: Float, top: Float, bottom: Float, hemShift: Float) {
    val path = Path().apply {
        if (left) {
            moveTo(x - 10f, top)
            lineTo(x + width, top)
            quadraticTo(x + width * 0.7f + hemShift, (top + bottom) * 0.55f, x + width * 0.25f + hemShift, bottom)
            lineTo(x - 10f, bottom + 30f)
        } else {
            moveTo(x, top)
            lineTo(x + width + 10f, top)
            lineTo(x + width + 10f, bottom + 30f)
            lineTo(x + width * 0.75f + hemShift, bottom)
            quadraticTo(x + width * 0.3f + hemShift, (top + bottom) * 0.55f, x, top)
        }
        close()
    }
    val fold = width / 3.5f
    drawPath(
        path,
        Brush.horizontalGradient(
            0f to CurtainDark, 0.18f to CurtainLight, 0.36f to CurtainMid,
            0.55f to CurtainLight, 0.75f to CurtainDark, 1f to CurtainMid,
            startX = x, endX = x + fold * 3.5f,
        ),
    )
    // Unten dunkler: der Samt fällt aus dem Licht der Lichterkette.
    drawPath(path, Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color(0x99000000), startY = top, endY = bottom))
}

/**
 * Silben-Verschmelzer: eine Waldlichtung bei Nacht. Glühwürmchen treiben auf langen,
 * weichen Bahnen (20–40 s) und glimmen langsam auf und ab; unten steht Gras als
 * Silhouette. Die Silben selbst sind die zwei hellsten Lichter der Szene.
 */
@Composable
internal fun ClearingBackground(modifier: Modifier) {
    val still = rememberReduceMotion()
    val seconds by rememberWorldSeconds(still)
    Canvas(modifier.graphicsLayer()) {
        val w = size.width
        val h = size.height
        drawRect(
            Brush.radialGradient(
                0f to ClearingCenter,
                0.55f to ClearingMid,
                1f to ClearingEdge,
                center = Offset(w * 0.5f, h * 0.45f),
                radius = h * 0.62f,
            ),
        )
        Fireflies.forEach { (fx, fy, period, phase, r) ->
            val a = seconds / period * Tau + phase
            val pos = Offset(
                w * (fx + 0.08f * sin(a) + 0.03f * sin(a * 2.3f)),
                h * (fy + 0.04f * cos(a * 0.8f)),
            )
            val pulse = 0.35f + 0.65f * (0.5f + 0.5f * sin(seconds / (period * 0.25f) * Tau + phase * 3f))
            val gr = r.dp.toPx() * 6f
            drawCircle(Brush.radialGradient(0f to FireflyLight.copy(alpha = 0.55f * pulse), 1f to Color.Transparent, center = pos, radius = gr), radius = gr, center = pos)
            drawCircle(FireflyCore, alpha = pulse, radius = r.dp.toPx(), center = pos)
        }
        val base = h * 0.9f
        val grass = Path().apply {
            moveTo(0f, h)
            lineTo(0f, base)
            var x = 0f
            var i = 0
            val step = 7.dp.toPx()
            while (x < w) {
                val tall = (18 + (GrassHeights[i % GrassHeights.size] * 38)).dp.toPx()
                quadraticTo(x + step * 0.45f, base - tall, x + step, base)
                x += step
                i++
            }
            lineTo(w, h)
            close()
        }
        drawPath(grass, ClearingGrass)
    }
}

/**
 * Laut-Fresser: Leuchtpunkte, die langsam durch die Pilzhöhle schweben — Sporen der
 * Leuchtpilze, türkis und wenige bernsteinfarben. Jeder zieht auf einer eigenen weiten
 * Bahn (Perioden 28–52 s) und glimmt langsam auf und ab; die meisten bleiben an den
 * Rändern und unten bei den Pilzen, die Mitte mit der Karte bleibt ruhig.
 */
@Composable
internal fun CaveGlowMotes(modifier: Modifier) {
    val still = rememberReduceMotion()
    val seconds by rememberWorldSeconds(still)
    Canvas(modifier.graphicsLayer()) {
        val w = size.width
        val h = size.height
        CaveMotes.forEach { m ->
            // Sechs Werte: Arrays kennen nur component1…5, also von Hand ausgepackt.
            val (fx, fy, period, phase, r) = m
            val warm = m[5]
            val a = seconds / period * Tau + phase
            val pos = Offset(
                w * (fx + 0.12f * sin(a) + 0.04f * sin(a * 2.7f + 1f)),
                h * (fy + 0.07f * cos(a * 0.9f) - 0.02f * sin(a * 1.9f)),
            )
            val pulse = 0.4f + 0.6f * (0.5f + 0.5f * sin(seconds / (period * 0.22f) * Tau + phase * 2f))
            val color = if (warm > 0.5f) MoteWarm else MoteCool
            val gr = r.dp.toPx() * 8f
            drawCircle(Brush.radialGradient(0f to color.copy(alpha = 0.6f * pulse), 0.35f to color.copy(alpha = 0.22f * pulse), 1f to Color.Transparent, center = pos, radius = gr), radius = gr, center = pos)
            drawCircle(MoteCore, alpha = 0.85f * pulse, radius = r.dp.toPx(), center = pos)
        }
    }
}

private val MoteCool = Color(0xFF6FF0D8)
private val MoteWarm = Color(0xFFF0B35A)
private val MoteCore = Color(0xFFE8FFF8)

/** (x, y, Periode s, Phase, Radius dp, warm > 0,5) */
private val CaveMotes: List<FloatArray> = run {
    val rnd = java.util.Random(37)
    List(18) {
        val side = it % 3
        floatArrayOf(
            when (side) {
                0 -> 0.06f + rnd.nextFloat() * 0.22f
                1 -> 0.72f + rnd.nextFloat() * 0.22f
                else -> 0.2f + rnd.nextFloat() * 0.6f
            },
            if (side == 2) 0.62f + rnd.nextFloat() * 0.2f else 0.28f + rnd.nextFloat() * 0.5f,
            28f + rnd.nextFloat() * 24f,
            rnd.nextFloat() * Tau,
            1.5f + rnd.nextFloat() * 1.5f,
            if (it % 6 == 0) 1f else 0f,
        )
    }
}

private val DuskTop = Color(0xFF1C2757)
private val DuskUpper = Color(0xFF2C3874)
private val DuskViolet = Color(0xFF5B4F86)
private val DuskGlow = Color(0xFFB5767C)
private val StarLight = Color(0xFFF7F1E3)
private val GardenTreeFar = Color(0xFF232745)
private val GardenHill = Color(0xFF1A1D2E)
private val GardenLawn = Color(0xFF12141F)

/** (x, y, Radius dp, Deckkraft, Funkelperiode s — 0 = steht still) */
private val GardenStars: List<FloatArray> = run {
    val rnd = java.util.Random(71)
    List(46) {
        floatArrayOf(rnd.nextFloat(), rnd.nextFloat(), 0.6f + rnd.nextFloat(), 0.25f + rnd.nextFloat() * 0.55f, if (it % 8 == 0) 9f + rnd.nextFloat() * 6f else 0f)
    }
}

private val StageBack = Color(0xFF1D2A2A)
private val StageMid = Color(0xFF0F1A1C)
private val StageDark = Color(0xFF070D0F)
private val CurtainDark = Color(0xFF5E121D)
private val CurtainMid = Color(0xFF7C1A27)
private val CurtainLight = Color(0xFF9A2432)
private val BulbLight = Color(0xFFFFE2A0)
private val BulbGlow = Color(0xFFFFC86E)

private val ClearingCenter = Color(0xFF183237)
private val ClearingMid = Color(0xFF0E2024)
private val ClearingEdge = Color(0xFF060F11)
private val ClearingGrass = Color(0xFF06120F)
private val FireflyLight = Color(0xFFE6F596)
private val FireflyCore = Color(0xFFF4FBC8)

/** (x, y, Periode s, Phase, Radius dp) — die Mitte (Silben) bleibt frei. */
private val Fireflies: List<FloatArray> = run {
    val rnd = java.util.Random(23)
    List(14) {
        val low = it % 3 != 0
        floatArrayOf(
            0.08f + rnd.nextFloat() * 0.84f,
            if (low) 0.64f + rnd.nextFloat() * 0.24f else 0.3f + rnd.nextFloat() * 0.08f,
            20f + rnd.nextFloat() * 20f,
            rnd.nextFloat() * Tau,
            1.2f + rnd.nextFloat() * 1.2f,
        )
    }
}

private val GrassHeights: FloatArray = run {
    val rnd = java.util.Random(11)
    FloatArray(40) { rnd.nextFloat() }
}
