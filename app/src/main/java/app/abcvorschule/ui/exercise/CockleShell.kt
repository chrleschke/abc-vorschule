package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.AbcMotion
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Die Herzmuschel der Tiefsee-Jagd (PRODUCT_PRINCIPLES §10, „Nachtwelten") — der
 * Zähler, der in diese Welt gehört, statt der Batterie.
 *
 * Man schaut **von oben** auf die Muschel. Geschlossen sieht man die gerippte Schale;
 * klappt sie auf, fällt der Deckel nach hinten über das Scharnier, und man sieht das
 * Perlmutt mit je einer Mulde pro gesuchtem Symbol. Jede eingefangene Blase wird zu
 * einer Perle in der Farbe ihres Blasenrings und fliegt hinein — die Farben der Ringe
 * haben damit eine Aufgabe: sie sagen „das ist deine Perle".
 *
 * Öffnung als eine Zahl `openness`:
 * - 0 = zu (Deckel ganz über der Unterschale),
 * - [PeekOpenness] = halb auf, der Deckel deckt nur noch das obere Drittel — so lugt die
 *   Muschel nach längerer Pause, damit das Kind sieht, wie viele Perlen es schon hat,
 * - 1 = ganz auf (Deckel nach hinten geklappt, Innenseite sichtbar über dem Scharnier).
 *
 * Geometrie in einer 200 × 200-Box; die Unterschale liegt in der unteren 150er-Hälfte,
 * darüber ist Platz für den aufgeklappten Deckel. Compose-frei rechenbar in
 * [CockleGeometry], damit die Flugziele der Perlen testbar sind.
 */
object CockleGeometry {
    /** Seitenverhältnis Breite : Höhe der Zeichenfläche. */
    const val Aspect = 1f

    /** Oberkante der Unterschale (Scharnier) in Box-Einheiten. */
    const val HingeY = 68f

    const val PeekOpenness = 0.32f

    /** Mittelpunkt der Mulde [index] von [count] als Anteil der Box (0…1). */
    fun slotCenter(index: Int, count: Int): Offset {
        val span = if (count <= 3) 0.44f else 0.58f
        val fraction = if (count <= 1) 0.5f else index / (count - 1f)
        val x = 0.5f - span / 2f + span * fraction
        // Eine leichte Lächel-Kurve: die mittleren Mulden liegen tiefer.
        val arc = 1f - ((x - 0.5f) / (span / 2f + 0.0001f)).let { it * it }
        val y = (HingeY + 78f + 10f * arc) / 200f
        return Offset(x, y)
    }

    /** Radius einer Mulde als Anteil der Boxbreite. */
    fun slotRadius(count: Int): Float = if (count <= 3) 0.078f else 0.058f

    /** Wie weit der Deckel die Unterschale noch bedeckt (1 = ganz, 0 = gar nicht). */
    fun lidCover(openness: Float): Float = (1f - openness.coerceIn(0f, 1f) * 2f).coerceIn(0f, 1f)

    /** Wie weit die Innenseite des Deckels über dem Scharnier steht (0…1). */
    fun lidBack(openness: Float): Float = ((openness.coerceIn(0f, 1f) - 0.5f) * 2f).coerceIn(0f, 1f)
}

/**
 * Wo die Muschel im Root liegt — für die fliegenden Perlen, die vom getippten
 * Buchstaben aus starten. Gesetzt von [CockleShell], gelesen vom Trainer.
 */
class CockleAnchor {
    var topLeft: Offset = Offset.Zero
    var widthPx: Float = 0f

    fun slotInRoot(index: Int, count: Int): Offset {
        val s = CockleGeometry.slotCenter(index, count)
        return topLeft + Offset(s.x * widthPx, s.y * widthPx / CockleGeometry.Aspect)
    }
}

/**
 * @param pearls die Farben der schon gelandeten Perlen, in Landereihenfolge.
 * @param openness 0 = zu, 1 = ganz auf (siehe [CockleGeometry]).
 * @param celebrate Muschel ist voll: sie bleibt offen und leuchtet.
 */
@Composable
fun CockleShell(
    total: Int,
    pearls: List<Color>,
    openness: State<Float>,
    celebrate: Boolean,
    anchor: CockleAnchor,
    modifier: Modifier = Modifier,
    width: Dp = 150.dp,
) {
    val glow = if (celebrate) {
        rememberInfiniteTransition(label = "cockle_glow").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(AbcMotion.PulseMs, easing = LinearEasing), RepeatMode.Reverse),
            label = "cockle_glow_value",
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }
    Canvas(
        modifier = modifier
            .width(width)
            .aspectRatio(CockleGeometry.Aspect)
            .onGloballyPositioned {
                anchor.topLeft = it.positionInRoot()
                anchor.widthPx = it.size.width.toFloat()
            }
            .semantics { contentDescription = "Muschel, ${pearls.size} von $total Perlen" },
    ) {
        val u = size.width / 200f
        scale(u, pivot = Offset.Zero) {
            drawCockle(total, pearls, openness.value, glow.value)
        }
    }
}

/** Umriss der Unterschale in Box-Einheiten: rund, zum Scharnier hin leicht eingezogen. */
private fun shellOutline(top: Float): Path = Path().apply {
    moveTo(100f, top)
    cubicTo(60f, top, 20f, top + 22f, 20f, top + 60f)
    cubicTo(20f, top + 94f, 56f, top + 120f, 100f, top + 124f)
    cubicTo(144f, top + 120f, 180f, top + 94f, 180f, top + 60f)
    cubicTo(180f, top + 22f, 140f, top, 100f, top)
    close()
}

private fun DrawScope.drawCockle(total: Int, pearls: List<Color>, openness: Float, glow: Float) {
    val hinge = CockleGeometry.HingeY
    val outer = shellOutline(hinge)
    if (glow > 0f) {
        drawCircle(
            brush = Brush.radialGradient(
                0.5f to PearlGlow.copy(alpha = 0.5f * glow),
                1f to Color.Transparent,
                center = Offset(100f, hinge + 64f),
                radius = 110f,
            ),
            radius = 110f,
            center = Offset(100f, hinge + 64f),
        )
    }
    // Unterschale mit Perlmutt-Innenseite und den Mulden.
    drawPath(outer, ShellRim)
    val inner = Path().apply {
        moveTo(100f, hinge + 8f)
        cubicTo(66f, hinge + 8f, 30f, hinge + 28f, 30f, hinge + 60f)
        cubicTo(30f, hinge + 89f, 62f, hinge + 112f, 100f, hinge + 116f)
        cubicTo(138f, hinge + 112f, 170f, hinge + 89f, 170f, hinge + 60f)
        cubicTo(170f, hinge + 28f, 134f, hinge + 8f, 100f, hinge + 8f)
        close()
    }
    drawPath(
        inner,
        Brush.radialGradient(
            0f to NacreLight,
            0.6f to NacreMid,
            1f to NacreEdge,
            center = Offset(100f, hinge + 40f),
            radius = 110f,
        ),
    )
    val r = CockleGeometry.slotRadius(total) * 200f
    repeat(total) { i ->
        val c = CockleGeometry.slotCenter(i, total).let { Offset(it.x * 200f, it.y * 200f) }
        drawCircle(SlotShade, radius = r * 1.12f, center = c)
        pearls.getOrNull(i)?.let { tint -> drawPearl(c, r, tint) }
    }
    // Deckel, Außenseite: klappt über das Scharnier nach hinten weg.
    val cover = CockleGeometry.lidCover(openness)
    if (cover > 0f) {
        scale(scaleX = 1f, scaleY = cover, pivot = Offset(100f, hinge)) {
            drawPath(outer, Brush.verticalGradient(0f to LidLight, 1f to LidDark, startY = hinge, endY = hinge + 124f))
            clipPath(outer) {
                // Die Rippen der Herzmuschel: fein und dicht, alle vom Wirbel aus.
                var a = -62f
                while (a <= 62f) {
                    val rad = a * PI.toFloat() / 180f
                    drawLine(
                        LidRib,
                        start = Offset(100f, hinge - 4f),
                        end = Offset(100f + 150f * sin(rad), hinge - 4f + 150f * cos(rad)),
                        strokeWidth = 2.2f,
                    )
                    a += 6f
                }
            }
            drawPath(outer, LidEdge, style = Stroke(width = 3f))
            drawOval(LidUmbo, topLeft = Offset(84f, hinge - 2f), size = Size(32f, 16f))
        }
    }
    // Deckel, Innenseite: steht aufgeklappt hinter dem Scharnier.
    val back = CockleGeometry.lidBack(openness)
    if (back > 0f) {
        scale(scaleX = 1f, scaleY = back, pivot = Offset(100f, hinge)) {
            translate(top = 0f) {
                val lid = Path().apply {
                    moveTo(100f, hinge)
                    cubicTo(60f, hinge, 22f, hinge - 12f, 22f, hinge - 38f)
                    cubicTo(22f, hinge - 58f, 58f, hinge - 66f, 100f, hinge - 66f)
                    cubicTo(142f, hinge - 66f, 178f, hinge - 58f, 178f, hinge - 38f)
                    cubicTo(178f, hinge - 12f, 140f, hinge, 100f, hinge)
                    close()
                }
                drawPath(lid, NacreMid)
                drawPath(lid, ShellRim, style = Stroke(width = 4f))
            }
        }
    }
}

/** Eine Perle in der Tönung ihres Blasenrings: heller Glanz oben links, getönter Rand. */
fun DrawScope.drawPearl(center: Offset, radius: Float, tint: Color) {
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.White,
            0.55f to lerp(Color.White, tint, 0.35f),
            1f to lerp(Color.White, tint, 0.75f),
            center = center + Offset(-radius * 0.3f, -radius * 0.35f),
            radius = radius * 1.4f,
        ),
        radius = radius,
        center = center,
    )
    // Nur ein Hauch Schimmer am Rand — ein breiter Halo ließ die Perle wie eine Lampe wirken.
    drawCircle(PearlGlow.copy(alpha = 0.12f), radius = radius * 1.08f, center = center, style = Stroke(width = radius * 0.08f))
}

/** Perlmutt und Schale — warme Sand- und Rosatöne, bewusst weit weg von Gold (Belohnung) und Grün (richtig). */
private val ShellRim = Color(0xFFCAA183)
private val NacreLight = Color(0xFFFBF1F3)
private val NacreMid = Color(0xFFEAD7E2)
private val NacreEdge = Color(0xFFC9A9BD)
private val SlotShade = Color(0x38785064)
private val LidLight = Color(0xFFF4D7BF)
private val LidDark = Color(0xFFD9A481)
private val LidRib = Color(0xCCBF8663)
private val LidEdge = Color(0xFFB77D5A)
private val LidUmbo = Color(0xFFE8C2A4)
private val PearlGlow = Color(0xFFFFFBF0)

/** Die Öffnung der Muschel als Animatable, damit Trainer und Idle-Blick dieselbe Feder teilen. */
@Composable
fun rememberCockleOpenness(key: Any?): Animatable<Float, AnimationVector1D> = remember(key) { Animatable(0f) }
