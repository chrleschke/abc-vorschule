package app.abcvorschule.ui.exercise

import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.WarmInk
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * Bewegungszustand eines Fressers. Drei Animatables statt animateFloatAsState:
 * Kauen und Schütteln sind Sequenzen, die bei jedem Auslöser von vorn laufen
 * müssen, auch wenn die vorige noch läuft.
 */
class FeederCreatureAnimator {
    val mouth = Animatable(IdleMouth)
    val wobble = Animatable(0f)
    val scale = Animatable(1f)

    /** Vorstellen und Antippen: dreimal hin und her, ±6°. */
    suspend fun wiggle() {
        repeat(3) {
            wobble.animateTo(6f, tween(75))
            wobble.animateTo(-6f, tween(75))
        }
        wobble.animateTo(0f, tween(60))
    }

    /** Fressen: Maul weit auf, zweimal zu und auf, dann Ruhe. Der Bauch wackelt mit. */
    suspend fun chew() {
        mouth.animateTo(1f, tween(120))
        repeat(2) {
            mouth.animateTo(0.1f, tween(110))
            scale.animateTo(1.04f, tween(110))
            mouth.animateTo(0.6f, tween(110))
            scale.animateTo(1f, tween(110))
        }
        mouth.animateTo(IdleMouth, tween(150))
    }

    /** Spucken: Maul zu, schütteln, Maul wieder auf. */
    suspend fun spit() {
        mouth.animateTo(0f, tween(90))
        repeat(2) {
            wobble.animateTo(-8f, tween(60))
            wobble.animateTo(8f, tween(60))
        }
        wobble.animateTo(0f, tween(60))
        delay(120)
        mouth.animateTo(IdleMouth, tween(200))
    }

    /** Satt: kugelrund, leicht federnd. */
    suspend fun fill() {
        scale.animateTo(FullScale, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        mouth.animateTo(0.15f, tween(200))
    }

    suspend fun hover(on: Boolean) {
        mouth.animateTo(if (on) HoverMouth else IdleMouth, tween(120))
    }

    companion object {
        const val IdleMouth = 0.35f
        const val HoverMouth = 0.8f
        const val FullScale = 1.15f
    }
}

@Composable
fun rememberFeederCreatureAnimator(key: Any?): FeederCreatureAnimator = remember(key) { FeederCreatureAnimator() }

/**
 * Der Umriss eines Fressers: eine Geisterform aus dem Material-Formensystem —
 * runde Kuppel oben, gerade Flanken, unten drei Wellen. Auf 0..1 normiert und
 * beim Zeichnen auf die Figurgröße skaliert.
 *
 * `MaterialShapes.Ghostish` wäre der kürzere Weg, existiert aber in material3
 * 1.4.0 (Compose BOM 2025.12.00) noch nicht — die Klasse kommt erst mit
 * 1.5.0-alpha. Also dieselbe Form von Hand aus `androidx.graphics.shapes`, der
 * Bibliothek, aus der `MaterialShapes` selbst besteht.
 */
private val GhostBody: RoundedPolygon by lazy {
    val points = mutableListOf<Float>()
    val rounding = mutableListOf<CornerRounding>()
    fun at(x: Float, y: Float, r: Float) {
        points += x; points += y
        rounding += CornerRounding(radius = r, smoothing = 0.4f)
    }
    // Kuppel: eine halbe Ellipse von links nach rechts. Dreizehn Stützpunkte, nicht
    // neun — mit weniger sieht man die Ecken als Kanten in der Schulter, und der
    // Kopf wirkt wie ein Helm statt wie eine Kuppel.
    val domeSteps = 12
    for (step in 0..domeSteps) {
        val t = PI * step / domeSteps
        at(0.5f - 0.5f * cos(t).toFloat(), 0.46f - 0.46f * sin(t).toFloat(), 0.07f)
    }
    at(1f, 0.80f, 0.09f) // rechte Flanke
    // Drei Wellen als Saum, von rechts nach links: Spitze, Kerbe, Spitze, …
    at(0.833f, 1.00f, 0.10f); at(0.667f, 0.82f, 0.08f)
    at(0.500f, 1.00f, 0.10f); at(0.333f, 0.82f, 0.08f)
    at(0.167f, 1.00f, 0.10f)
    at(0f, 0.80f, 0.09f) // linke Flanke
    RoundedPolygon(
        vertices = points.toFloatArray(),
        perVertexRounding = rounding,
        centerX = 0.5f,
        centerY = 0.5f,
    )
}

/** Einmal gerechnet: der Umriss als Pfad im 0..1-Raum, Vorlage für jede Figur. */
private val GhostPathTemplate: android.graphics.Path by lazy { GhostBody.toPath() }

/**
 * Ein Laut-Fresser (design doc §5): ein Geisterkörper mit Verlauf (Licht oben
 * links, Schatten unten rechts), zwei Augen, ein Halbkreis-Maul, das dem Zug folgt,
 * und ein Bauchfleck mit dem Laut. Canvas statt Emoji oder Bitmap — Buttons und
 * Figuren zeichnet die App selbst (§10), und nur so folgt das Maul dem Drag.
 *
 * Bauch und Glyph sind helle bzw. dunkle Stufen der Körperfarbe ([FeederPalette]),
 * nicht Cream und WarmInk: der Laut soll zur Figur gehören, nicht als weißes Schild
 * darauf liegen.
 *
 * Kein Speaker-Icon mehr: der Tipp auf die ganze Figur spricht den Laut, aber das
 * Symbol sah nach einem eigenen Knopf aus und lenkte vom Füttern ab.
 */
@Composable
fun FeederCreature(
    label: SymbolInWordDerivation.TargetLabel,
    color: Color,
    animator: FeederCreatureAnimator,
    hint: Boolean,
    widthDp: Float,
    enabled: Boolean,
    onTap: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val heightDp = SoundFeederSizing.creatureHeightDp(widthDp)
    val fontScale = LocalDensity.current.fontScale
    val glyphSp = SoundFeederSizing.bellyGlyphSp(
        SoundFeederSizing.labelChars(label.primary, label.alternate),
        widthDp,
        fontScale,
    )
    val belly = FeederPalette.belly(color)
    val glyphColor = FeederPalette.glyph(color)
    val highlight = FeederPalette.highlight(color)
    val shade = FeederPalette.shade(color)
    val mouthColor = lerp(shade, Color.Black, 0.5f)
    // Der Umriss wird je Bild neu skaliert (die Figur wächst mit der Bühne); Pfad und
    // Matrix leben aber über die Frames hinweg, statt sie jedes Mal neu anzulegen.
    val bodyPath = remember { android.graphics.Path() }
    val bodyCompose = remember(bodyPath) { bodyPath.asComposePath() }
    val bodyMatrix = remember { Matrix() }
    // Der Hinweis pulsiert das Maul zwischen weit und ganz weit — nur solange er
    // aktiv ist, sonst tickt hier keine Endlos-Animation.
    val hintMouth = if (hint) {
        val transition = rememberInfiniteTransition(label = "feeder_hint")
        val pulse by transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
            label = "feeder_hint_mouth",
        )
        pulse
    } else {
        null
    }
    val mouthOpen = hintMouth ?: animator.mouth.value

    Box(
        modifier = modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .graphicsLayer {
                rotationZ = animator.wobble.value
                scaleX = animator.scale.value
                scaleY = animator.scale.value
            }
            .clickable(enabled = enabled) { onTap() }
            .testTag(testTag),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Körper: die Geisterform, gefüllt mit einem Radialverlauf — das Licht
            // sitzt oben links, der Schatten läuft nach unten rechts aus. Erst dadurch
            // sieht die Figur nach Körper aus statt nach ausgeschnittenem Papier.
            bodyPath.set(GhostPathTemplate)
            bodyMatrix.setScale(w, h)
            bodyPath.transform(bodyMatrix)
            drawPath(
                path = bodyCompose,
                brush = Brush.radialGradient(
                    0f to highlight,
                    0.55f to color,
                    1f to shade,
                    center = Offset(w * 0.38f, h * 0.30f),
                    radius = w * 0.9f,
                ),
            )
            // Maul: ein nach unten offener Halbkreis mit flacher Oberkante. Beim
            // Öffnen wächst er nach unten, die Kante bleibt stehen — ein Mund, der
            // aufgeht, kein Loch, das größer wird.
            val mouthW = w * 0.58f
            val mouthH = h * (0.05f + 0.22f * mouthOpen)
            drawArc(
                color = mouthColor,
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = true,
                topLeft = Offset((w - mouthW) / 2f, h * 0.34f - mouthH),
                size = Size(mouthW, mouthH * 2f),
            )
            // Augen: weißer Ball, dunkle Pupille, kleiner Glanzpunkt — der Glanz macht
            // aus zwei Punkten einen Blick.
            listOf(0.34f, 0.66f).forEach { cx ->
                drawCircle(color = Color.White, radius = w * 0.085f, center = Offset(w * cx, h * 0.19f))
                drawCircle(color = WarmInk, radius = w * 0.042f, center = Offset(w * cx, h * 0.20f))
                drawCircle(color = Cream, radius = w * 0.017f, center = Offset(w * cx - w * 0.017f, h * 0.182f))
            }
            // Bauchfleck: die helle Stufe der Körperfarbe, darauf steht der Laut
            // (Text unten). Bleibt innerhalb des Wellensaums.
            val bellyW = w * SoundFeederSizing.BellyWidthFraction
            drawOval(
                color = belly,
                topLeft = Offset((w - bellyW) / 2f, h * 0.50f),
                size = Size(bellyW, h * 0.32f),
            )
        }
        // Der Laut auf dem Bauch: bei Anlaut-Paaren nur die Großform ("Sch"), bei
        // Vokalpaaren beide ("Ei / ei") — der Aufrufer entscheidet über [label].
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (heightDp * 0.18f).dp)
                .height((heightDp * 0.32f).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buildString {
                    append(label.primary)
                    label.alternate?.let { append(" / ").append(it) }
                },
                fontSize = glyphSp.sp,
                fontWeight = FontWeight.Bold,
                color = glyphColor,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}
