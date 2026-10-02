package app.abcvorschule.ui.path

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Größe und Lage des Nebelrings relativ zum Turm (PRODUCT_PRINCIPLES §5). Rein, damit
 * sie ohne Compose testbar ist; alle Längen in derselben Einheit wie die Eingabe.
 *
 * Der Ring ist Boden, kein Rahmen: eine flache Ellipse, deren Mitte an der Unterkante
 * der Klötze liegt. Die hintere Hälfte verschwindet hinter dem Turm, die vordere liegt
 * unter ihm — so stehen die Klötze im Nebel, statt von ihm eingefasst zu werden.
 */
internal object FogRingGeometry {
    /** So weit reicht das Nebelband auf jeder Seite über den Turm hinaus. */
    const val ReachDp = 20f

    /** Breite : Höhe. Flacher läse sich als Strich, runder als Rahmen um die Klötze. */
    const val Aspect = 6.5f

    /** Ein einzelner Klotz bekäme sonst einen Ring, der kaum unter ihm hervorschaut. */
    const val MinRadiusXDp = 56f

    /**
     * Die Mitte liegt eine Spur unter der Unterkante: die Würfel stehen leicht schief
     * (`PathNoise`, Salz 13), und ihre tiefste Ecke soll im Nebel stehen, nicht darunter.
     */
    const val DropDp = 2f

    /** Der Dunst ist etwas größer als das Band. */
    const val HazeScaleX = 1.36f
    const val HazeScaleY = 1.75f

    /** So weit läuft die Nebelwelle aus, bevor sie verblasst ist. */
    const val PulseMaxScale = 1.5f

    data class Ellipse(val cx: Float, val cy: Float, val rx: Float, val ry: Float) {
        val left get() = cx - rx
        val right get() = cx + rx
        val top get() = cy - ry
        val bottom get() = cy + ry

        fun scaled(s: Float) = copy(rx = rx * s, ry = ry * s)
    }

    /**
     * Das Nebelband unter einem Turm, der [towerWidth] breit ist und mit der Mitte
     * seiner Unterkante auf [groundCenter] steht. [density] rechnet die dp-Konstanten
     * in die Einheit der Eingabe um (1 = dp).
     */
    fun band(towerWidth: Float, groundCenter: PathPoint, density: Float = 1f): Ellipse {
        val rx = max(towerWidth / 2f + ReachDp * density, MinRadiusXDp * density)
        return Ellipse(groundCenter.x, groundCenter.y + DropDp * density, rx, rx / Aspect)
    }

    fun haze(band: Ellipse) = band.copy(rx = band.rx * HazeScaleX, ry = band.ry * HazeScaleY)
}

/**
 * Wechsel des aktuellen Schilds: der Ring verblasst am alten und wächst am neuen auf.
 * Taktgeber ist die Marker-Index-Animation des Pfads ([marker] läuft von [from] nach
 * [to]); beide Hälften überlappen ein wenig, damit zwischendurch nie gar kein Ring steht.
 */
internal object FogRingTransfer {
    private const val FadeEnd = 0.65f
    private const val GrowStart = 0.35f

    /** Wie viel Ring an Schild [sign] steht, 0 … 1. Jedes andere Schild als [from]/[to]: 0. */
    fun presence(sign: Int, marker: Float, from: Int, to: Int): Float {
        if (sign != from && sign != to) return 0f
        if (from == to) return 1f
        val t = ((marker - from) / (to - from)).coerceIn(0f, 1f)
        return if (sign == to) smooth((t - GrowStart) / (1f - GrowStart)) else 1f - smooth(t / FadeEnd)
    }

    /** Der neue Ring wächst aus der Mitte auf, der alte verweht nach außen. */
    fun scale(presence: Float, grows: Boolean): Float =
        if (grows) 0.55f + 0.45f * presence else 1f + 0.2f * (1f - presence)

    private fun smooth(x: Float): Float {
        val c = x.coerceIn(0f, 1f)
        return c * c * (3f - 2f * c)
    }
}

/**
 * Was ein Schild braucht, um seinen Ring zu zeichnen. Beide Lambdas werden nur in der
 * Zeichenphase gelesen: ein Frame zeichnet den Ring neu, rekomponiert aber nichts.
 *
 * @param seconds Die eine Uhr aller Ringe ([app.abcvorschule.ui.world.rememberWorldSeconds]).
 * @param presence Siehe [FogRingTransfer.presence].
 * @param grows Ob dies das neue Schild ist (wächst auf) oder das alte (verblasst).
 * @param still „Bewegung reduzieren": stehender Dunst und stehende Körner, kein Puls.
 */
@Stable
internal class FogRing(
    val seconds: () -> Float,
    val presence: () -> Float,
    val grows: Boolean,
    val still: Boolean,
)

/**
 * Eigene Zahlen nach AbcMotions Regel 3 (Figurenspiel): der Ring ist Landschaft und
 * bewegt sich langsamer als jede Antwort auf eine Tat. Er ersetzt die wippende
 * Stecknadel, er kommt nicht zu ihr hinzu.
 */
internal object FogRingMotion {
    /** Eine Nebelwelle; das Band atmet in derselben Periode. */
    const val PulseS = 3.2f

    /** Umlauf der beiden Fleckengruppen, gegenläufig. */
    const val DriftAS = 30f
    const val DriftBS = -44f

    /** Umlauf der Körner: innere und äußere Bahn gleichsinnig, die mittlere gegen. */
    val GrainOrbitS = floatArrayOf(24f, -36f, 24f)
}

/**
 * Hintere Hälfte: Dunst, Band, Welle, hintere Flecken und Körner — gezeichnet vor den
 * Klötzen, also hinter ihnen. Eigene Ebene, sonst zeichnete jeder Frame den ganzen
 * Pfad samt 600 Trittspuren neu.
 */
@Composable
internal fun FogRingBack(ring: FogRing, towerWidthDp: Float, modifier: Modifier = Modifier) {
    val painter = rememberFogRingPainter()
    Canvas(modifier.graphicsLayer()) { painter.draw(this, ring, towerWidthDp, front = false) }
}

/** Vordere Hälfte: Flecken und Körner vor dem Turm, über den Füßen der Klötze. */
@Composable
internal fun FogRingFront(ring: FogRing, towerWidthDp: Float, modifier: Modifier = Modifier) {
    val painter = rememberFogRingPainter()
    Canvas(modifier.graphicsLayer()) { painter.draw(this, ring, towerWidthDp, front = true) }
}

@Composable
private fun rememberFogRingPainter(): FogRingPainter {
    val density = LocalDensity.current.density
    return remember(density) { FogRingPainter(density) }
}

/**
 * Zeichnet eine Hälfte des Rings in ein Feld, dessen Unterkante die Unterkante des Turms
 * ist (Mitte unten = Fuß des Turms). Hält seine Puffer selbst, damit ein Frame nichts
 * anlegt: die Körner gehen gesammelt in **einen** `drawPoints`-Aufruf pro Hälfte.
 * Weichheit kommt nur aus Verläufen, kein RenderEffect/Blur.
 */
private class FogRingPainter(private val density: Float) {
    private val points = FloatArray(Grains.size * 2)
    private val grainPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeWidth = GrainDp * density
        color = Grain.toArgb()
    }

    fun draw(scope: DrawScope, ring: FogRing, towerWidthDp: Float, front: Boolean) = with(scope) {
        val presence = ring.presence()
        if (presence <= 0.003f) return
        val t = if (ring.still) 0f else ring.seconds()
        val ground = PathPoint(size.width / 2f, size.height)
        val band = FogRingGeometry.band(towerWidthDp * density, ground, density)
        val s = FogRingTransfer.scale(presence, ring.grows)
        // Das Band atmet mit der Welle: am hellsten, wenn sie losläuft.
        val phase = (t % FogRingMotion.PulseS) / FogRingMotion.PulseS
        val breath = 0.85f + 0.15f * cos(2f * PI.toFloat() * phase)

        withTransform({ scale(s, s, pivot = Offset(band.cx, band.cy)) }) {
            if (!front) {
                ellipse(HazeBrush, FogRingGeometry.haze(band), presence)
                // Durchgehender Grund unter den Flecken, damit das Band nie reißt. Gefüllt
                // statt als Ring: die Mitte liegt ohnehin hinter dem Turm, und vorn ist
                // der Nebel zwischen Klötzen und Band so dicht wie im Band selbst.
                ellipse(PoolBrush, band.copy(rx = band.rx * PoolScaleX, ry = band.ry * PoolScaleY), presence * breath * PoolAlpha)
                // Darauf der Ring selbst: am Band am hellsten, sonst läse es sich als Scheibe.
                ellipse(RingBrush, band.scaled(RingOuter), presence * breath * RingAlpha)
                if (!ring.still) {
                    // Ausrollend: schnell los, weich aus — wie eine Welle, die sich legt.
                    val out = 1f - (1f - phase) * (1f - phase)
                    val wave = band.scaled((1f + (FogRingGeometry.PulseMaxScale - 1f) * out) * RingOuter)
                    ellipse(RingBrush, wave, presence * 0.5f * (1f - phase))
                }
            }
            Blobs.forEach { blob ->
                val a = blob.angle + 2f * PI.toFloat() * t / blob.periodS
                val depth = sin(a)
                if ((depth >= 0f) != front) return@forEach
                val x = band.cx + band.rx * blob.radius * cos(a)
                val y = band.cy + band.ry * blob.radius * depth
                // Hinten etwas blasser: Tiefe ohne Perspektive zu rechnen.
                val alpha = blob.alpha * breath * presence * (0.8f + 0.2f * depth)
                ellipse(blob.brush, FogRingGeometry.Ellipse(x, y, band.rx * blob.width, max(band.ry * blob.height, BlobMinHeightDp * density)), alpha)
            }
            var n = 0
            Grains.forEach { grain ->
                val orbit = GrainOrbits[grain.orbit]
                val a = grain.angle + 2f * PI.toFloat() * t / FogRingMotion.GrainOrbitS[grain.orbit]
                val depth = sin(a)
                if ((depth >= 0f) != front) return@forEach
                points[n++] = band.cx + band.rx * orbit * cos(a)
                points[n++] = band.cy + band.ry * orbit * depth
            }
            if (n > 0) {
                grainPaint.alpha = (255 * presence * if (front) GrainFrontAlpha else GrainBackAlpha).toInt().coerceIn(0, 255)
                drawIntoCanvas { it.nativeCanvas.drawPoints(points, 0, n, grainPaint) }
            }
        }
    }

    /** Ein Fleck: der eine Einheitskreis-Verlauf, verschoben und gestaucht. Kein Shader pro Frame. */
    private fun DrawScope.ellipse(brush: Brush, e: FogRingGeometry.Ellipse, alpha: Float) {
        if (alpha <= 0.003f) return
        withTransform({
            translate(e.cx, e.cy)
            scale(e.rx / UnitRadius, e.ry / UnitRadius, pivot = Offset.Zero)
        }) {
            drawCircle(brush, radius = UnitRadius, center = Offset.Zero, alpha = alpha.coerceAtMost(1f))
        }
    }
}

// Warmes Laternenlicht, keine Farbrolle: der Ring ist Landschaft wie das Schildleuchten.
private val Mist = Color(0xFFFFE2A8)
private val MistLight = Color(0xFFFFE7B8)
private val MistGold = Color(0xFFFFD27A)
private val Grain = Color(0xFFFFF6DC)

private val HazeBrush = Brush.radialGradient(
    0.35f to Mist.copy(alpha = 0.2f),
    1f to Mist.copy(alpha = 0f),
    center = Offset.Zero,
    radius = UnitRadius,
)

private val RingBrush = Brush.radialGradient(
    0.55f to Mist.copy(alpha = 0f),
    0.8f to Mist,
    1f to Mist.copy(alpha = 0f),
    center = Offset.Zero,
    radius = UnitRadius,
)

private val PoolBrush = Brush.radialGradient(
    0f to MistLight,
    0.55f to MistLight.copy(alpha = 0.85f),
    0.8f to MistLight.copy(alpha = 0.35f),
    1f to MistLight.copy(alpha = 0f),
    center = Offset.Zero,
    radius = UnitRadius,
)

private fun blobBrush(c: Color) = Brush.radialGradient(
    0f to c,
    0.35f to c.copy(alpha = 0.72f),
    0.7f to c.copy(alpha = 0.26f),
    1f to c.copy(alpha = 0f),
    center = Offset.Zero,
    radius = UnitRadius,
)

private val BlobBrushA = blobBrush(MistLight)
private val BlobBrushB = blobBrush(MistGold)

private class Blob(val angle: Float, val radius: Float, val width: Float, val height: Float, val alpha: Float, val periodS: Float, val brush: Brush)

private class GrainSpec(val orbit: Int, val angle: Float)

/**
 * Elf Flecken in zwei gegenläufigen Gruppen. Lage, Größe und Stärke aus [PathNoise]
 * (Salz 17): derselbe Nebel bei jedem Öffnen, kein Zufall zwischen zwei Frames.
 */
private val Blobs: List<Blob> = run {
    val groupA = 6
    val all = 11
    (0 until all).map { i ->
        val inA = i < groupA
        val count = if (inA) groupA else all - groupA
        val k = if (inA) i else i - groupA
        val n = PathNoise.signed(i, salt = 17)
        val m = PathNoise.signed(i + 40, salt = 17)
        Blob(
            angle = 2f * PI.toFloat() * (k + 0.3f * n + if (inA) 0f else 0.5f) / count,
            radius = 1f + 0.04f * m,
            width = 0.3f + 0.06f * n,
            height = 0.9f + 0.2f * m,
            alpha = 0.27f + 0.08f * n,
            periodS = if (inA) FogRingMotion.DriftAS else FogRingMotion.DriftBS,
            brush = if (inA) BlobBrushA else BlobBrushB,
        )
    }
}

/** Drei Bahnen für die Körner, leicht innerhalb, auf und außerhalb des Bands. */
private val GrainOrbits = floatArrayOf(0.94f, 1.03f, 1.12f)

/** 16 + 14 + 10 Körner; Abstände unregelmäßig (Salz 19), sonst läse sich die Bahn als Perlenkette. */
private val Grains: List<GrainSpec> = listOf(16, 14, 10).flatMapIndexed { orbit, count ->
    (0 until count).map { k ->
        GrainSpec(orbit, 2f * PI.toFloat() * (k + 0.42f * PathNoise.signed(orbit * 50 + k, salt = 19)) / count)
    }
}

private const val UnitRadius = 100f
private const val GrainDp = 1.6f
private const val GrainBackAlpha = 0.5f
private const val GrainFrontAlpha = 0.8f

/** Untergrenze der Fleckenhöhe: unter einem einzelnen Klotz wäre das Band sonst ein Strich. */
private const val BlobMinHeightDp = 7f

/**
 * Der Ringverlauf hat sein Hellstes bei 0,8 des Radius; so weit wird er über das Band
 * hinaus gezogen, damit das Hellste genau auf dem Band liegt. Gestaucht wie das Band,
 * ist er an den Seiten breit und vorn schmal — ein Streifen Nebel am Boden.
 */
private const val RingOuter = 1f / 0.8f

/** Der Grund unter dem Band: etwas breiter und deutlich höher, damit es vorn Tiefe hat. */
private const val PoolScaleX = 1.02f
private const val PoolScaleY = 1.45f
private const val PoolAlpha = 0.32f
private const val RingAlpha = 0.4f
