package app.abcvorschule.ui.exercise

import android.graphics.Matrix
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.world.rememberReduceMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bewegungszustand eines Fressers. Drei Animatables statt animateFloatAsState:
 * Kauen und Schütteln sind Sequenzen, die bei jedem Auslöser von vorn laufen
 * müssen, auch wenn die vorige noch läuft.
 *
 * Die kurzen Tweens (60–150 ms) sind Figurenspiel — der Rhythmus von Kauen,
 * Spucken und Wackeln — und tragen deshalb eigene Zahlen statt der Stufen aus
 * [AbcMotion] (dort Regel 3). Nur das Anschwellen nutzt die gemeinsame Feder.
 */
class FeederCreatureAnimator {
    val mouth = Animatable(IdleMouth)
    val wobble = Animatable(0f)
    val scale = Animatable(1f)

    /** Stauchen beim Antippen: 0 = Ruhe; positiv = breiter und niedriger. */
    val squash = Animatable(0f)

    /**
     * Vorstellen und Antippen: die Figur wird kurz gestaucht und federt dann schaukelnd
     * aus, gedreht um die Füße. Eine gedämpfte Feder mit Anstoß statt harter Tween-
     * Stufen: früher dreimal ±6° in 75 ms, das wirkte ruckig.
     */
    suspend fun wiggle() = coroutineScope {
        launch {
            squash.animateTo(SquashDepth, tween(90, easing = FastOutSlowInEasing))
            squash.animateTo(0f, AbcMotion.Soft.spec())
        }
        wobble.animateTo(0f, AbcMotion.Soft.spec(), initialVelocity = WiggleKick)
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

    /** Spucken: Maul zu, ein schnelles Kopfschütteln, das ausfedert, Maul wieder auf. */
    suspend fun spit() {
        mouth.animateTo(0f, tween(90))
        wobble.animateTo(0f, AbcMotion.Wobble.spec(), initialVelocity = SpitKick)
        delay(120)
        mouth.animateTo(IdleMouth, tween(200))
    }

    /** Satt: kugelrund, leicht federnd. */
    suspend fun fill() {
        scale.animateTo(FullScale, AbcMotion.Settle.spec())
        mouth.animateTo(0.15f, tween(200))
    }

    suspend fun hover(on: Boolean) {
        mouth.animateTo(if (on) HoverMouth else IdleMouth, tween(120))
    }

    companion object {
        const val IdleMouth = 0.35f
        const val HoverMouth = 0.8f
        const val FullScale = 1.15f
        const val SquashDepth = 0.08f

        /** Anstoß in Grad pro Sekunde: auf der weichen Feder rund 6° Ausschlag. */
        const val WiggleKick = 120f

        /** Kräftiger und auf der schnelleren Feder: ein deutliches „nein". */
        const val SpitKick = -320f
    }
}

@Composable
fun rememberFeederCreatureAnimator(key: Any?): FeederCreatureAnimator = remember(key) { FeederCreatureAnimator() }

/**
 * Die zwei Figuren des Laut-Fressers. Beide haben eine eigene Silhouette, damit ein
 * Kind sie auch ohne Farbe auseinanderhält: **Pilli** ist rund und hat zwei Öhrchen,
 * **Kora** ist tropfenförmig und hat einen Blattspross.
 */
enum class FeederShape { Pilli, Kora }

/**
 * Umrisse im Zeichenraum 200 × 220 (Breite × Höhe, [SoundFeederSizing.CreatureAspect]),
 * beim Zeichnen auf die Figurgröße skaliert. Die Füße und der Schatten liegen unter
 * dem Körper im unteren Rand.
 */
private object FeederOutline {
    const val W = 200f
    const val H = 220f

    fun body(shape: FeederShape): android.graphics.Path = android.graphics.Path().apply {
        when (shape) {
            FeederShape.Pilli -> {
                moveTo(100f, 30f)
                cubicTo(146f, 30f, 174f, 64f, 174f, 114f)
                cubicTo(174f, 158f, 144f, 184f, 100f, 184f)
                cubicTo(56f, 184f, 26f, 158f, 26f, 114f)
                cubicTo(26f, 64f, 54f, 30f, 100f, 30f)
            }
            FeederShape.Kora -> {
                moveTo(100f, 20f)
                cubicTo(132f, 30f, 168f, 70f, 170f, 118f)
                cubicTo(172f, 160f, 140f, 184f, 100f, 184f)
                cubicTo(60f, 184f, 28f, 160f, 30f, 118f)
                cubicTo(32f, 70f, 68f, 30f, 100f, 20f)
            }
        }
        close()
    }

    /** Wo der Bauchfleck sitzt (Mitte y), abgestimmt auf die breiteste Stelle der Form. */
    fun bellyCenterY(shape: FeederShape): Float = if (shape == FeederShape.Pilli) 148f else 146f
}

/** Einmal gerechnet: die Umrisse als Vorlage, je Figur und Frame nur skaliert. */
private val PilliTemplate: android.graphics.Path by lazy { FeederOutline.body(FeederShape.Pilli) }
private val KoraTemplate: android.graphics.Path by lazy { FeederOutline.body(FeederShape.Kora) }

/** Blinzeln: alle paar Sekunden einmal kurz zu. Die beiden Figuren nie im Gleichtakt. */
private fun blinkPeriodMs(shape: FeederShape) = if (shape == FeederShape.Pilli) 5_500 else 6_700

/**
 * Ein Laut-Fresser (design doc §5, neu gezeichnet für die Pilzhöhle): ein Körper mit
 * Volumen (Licht oben links, Schatten unten rechts, Lichtkante am Rand), Augen mit
 * Lidern, die blinzeln und zur Karte hinaufschauen, Wangen, ein Maul mit Zunge, das
 * dem Zug folgt, kleine Füße und Ärmchen. Die Figur atmet ganz leicht.
 *
 * Canvas statt Emoji oder Bitmap — Buttons und Figuren zeichnet die App selbst (§10),
 * und nur so folgt das Maul dem Drag.
 *
 * Bauch und Glyph sind helle bzw. dunkle Stufen der Körperfarbe ([FeederPalette]),
 * nicht Cream und WarmInk: der Laut soll zur Figur gehören, nicht als weißes Schild
 * darauf liegen.
 */
@Composable
fun FeederCreature(
    label: SymbolInWordDerivation.TargetLabel,
    color: Color,
    shape: FeederShape,
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
    val deep = lerp(shade, Color.Black, 0.2f)
    val rim = lerp(color, Color.White, 0.6f)
    val cheek = lerp(color, CheekPink, 0.7f)
    val template = if (shape == FeederShape.Pilli) PilliTemplate else KoraTemplate
    val bellyY = FeederOutline.bellyCenterY(shape)
    // Der Umriss wird je Bild neu skaliert (die Figur wächst mit der Bühne); Pfad und
    // Matrix leben aber über die Frames hinweg, statt sie jedes Mal neu anzulegen.
    val bodyPath = remember { android.graphics.Path() }
    val bodyCompose = remember(bodyPath) { bodyPath.asComposePath() }
    val bodyMatrix = remember { Matrix() }
    // Der Hinweis pulsiert das Maul zwischen weit und ganz weit — nur solange er
    // aktiv ist, sonst tickt hier keine Endlos-Animation.
    //
    // Als `State` weitergereicht, nicht als Wert ausgelesen: gelesen wird erst im
    // Zeichnen-Lambda unten. Ein `by` an dieser Stelle hängte jeden Frame der
    // Maul-Animation an die *Komposition* der ganzen Figur.
    val hintMouth: State<Float>? = if (hint) {
        val transition = rememberInfiniteTransition(label = "feeder_hint")
        transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
            label = "feeder_hint_mouth",
        )
    } else {
        null
    }
    // Leben im Ruhezustand: langsames Atmen und seltenes Blinzeln. Beides wird nur in
    // der Zeichenphase gelesen; bei „Bewegung reduzieren" steht die Figur still.
    val still = rememberReduceMotion()
    val idle = rememberInfiniteTransition(label = "feeder_idle")
    val breath = idle.animateFloat(
        initialValue = 0f,
        targetValue = if (still) 0f else 1f,
        animationSpec = infiniteRepeatable(tween(BreathMs, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "feeder_breath",
    )
    val blink = idle.animateFloat(
        initialValue = 0f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = blinkPeriodMs(shape)
                0f at 0
                0f at durationMillis - 260
                if (!still) 1f at durationMillis - 170
                0f at durationMillis - 60
            },
        ),
        label = "feeder_blink",
    )

    Box(
        modifier = modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .graphicsLayer {
                rotationZ = animator.wobble.value
                scaleX = animator.scale.value * (1f + animator.squash.value)
                scaleY = animator.scale.value * (1f - animator.squash.value)
                // Unten verankert: die Figur wächst beim Atmen nach oben, die Füße bleiben stehen.
                transformOrigin = TransformOrigin(0.5f, 0.92f)
            }
            .clickable(enabled = enabled) { onTap() }
            .testTag(testTag),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            // Erst hier gelesen: die Animationen laden nur das Zeichnen neu,
            // nicht die Komposition der Figur.
            val mouthOpen = hintMouth?.value ?: animator.mouth.value
            val sx = size.width / FeederOutline.W
            val sy = size.height / FeederOutline.H
            fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
            fun sz(w: Float, h: Float) = Size(w * sx, h * sy)

            // Schatten auf dem Boden und Füße: stehen still, atmen nicht mit.
            drawOval(ShadowInk, topLeft = p(38f, 198f), size = sz(124f, 18f))
            drawOval(deep, topLeft = p(54f, 186f), size = sz(38f, 20f))
            drawOval(deep, topLeft = p(108f, 186f), size = sz(38f, 20f))

            val lift = 1f + BreathLift * breath.value
            scale(scaleX = 1f, scaleY = lift, pivot = p(100f, 190f)) {
                // Öhrchen (Pilli) bzw. Blattspross (Kora) hinter dem Kopf.
                if (shape == FeederShape.Pilli) {
                    listOf(-1f, 1f).forEach { side ->
                        rotate(degrees = side * 24f, pivot = p(100f + side * 36f, 44f)) {
                            drawOval(shade, topLeft = p(100f + side * 36f - 12f, 18f), size = sz(24f, 36f))
                            drawOval(highlight, topLeft = p(100f + side * 36f - 6f, 26f), size = sz(12f, 20f), alpha = 0.6f)
                        }
                    }
                } else {
                    drawLeaf(p(100f, 22f), p(122f, 2f), sx, LeafLight)
                    drawLeaf(p(100f, 24f), p(84f, 6f), sx, LeafDark)
                }
                // Ärmchen links und rechts, leicht angewinkelt.
                val arm = Stroke(width = 10f * sx, cap = StrokeCap.Round)
                drawPath(
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(34f * sx, 124f * sy); quadraticTo(16f * sx, 132f * sy, 22f * sx, 150f * sy)
                    },
                    shade,
                    style = arm,
                )
                drawPath(
                    androidx.compose.ui.graphics.Path().apply {
                        moveTo(166f * sx, 124f * sy); quadraticTo(184f * sx, 132f * sy, 178f * sx, 150f * sy)
                    },
                    shade,
                    style = arm,
                )
                // Körper mit Volumen: Licht oben links, Schatten unten rechts …
                bodyPath.set(template)
                bodyMatrix.setScale(sx, sy)
                bodyPath.transform(bodyMatrix)
                drawPath(
                    path = bodyCompose,
                    brush = Brush.radialGradient(
                        0f to highlight,
                        0.5f to color,
                        1f to shade,
                        center = p(72f, 70f),
                        radius = size.width * 0.85f,
                    ),
                )
                // … und eine Lichtkante oben links, die zur Mitte hin ausläuft.
                drawPath(
                    path = bodyCompose,
                    brush = Brush.linearGradient(
                        0f to rim,
                        0.35f to rim.copy(alpha = 0f),
                        start = p(30f, 30f),
                        end = p(120f, 130f),
                    ),
                    style = Stroke(width = 3.5f * sx),
                )
                // Bauchfleck: die helle Stufe der Körperfarbe, darauf steht der Laut.
                val bellyW = FeederOutline.W * SoundFeederSizing.BellyWidthFraction
                drawOval(belly, topLeft = p(100f - bellyW / 2f, bellyY - 26f), size = sz(bellyW, 52f))
                // Wangen.
                drawOval(cheek, topLeft = p(46f, 104f), size = sz(20f, 12f), alpha = 0.5f)
                drawOval(cheek, topLeft = p(134f, 104f), size = sz(20f, 12f), alpha = 0.5f)
                // Maul: flache Oberkante, wächst beim Öffnen nach unten; ab halb offen
                // sieht man die Zunge.
                val mouthW = 50f + 10f * mouthOpen
                val mouthH = 4f + 22f * mouthOpen
                val mouthTop = 108f
                drawArc(
                    color = MouthInk,
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = true,
                    topLeft = p(100f - mouthW / 2f, mouthTop - mouthH),
                    size = sz(mouthW, mouthH * 2f),
                )
                if (mouthOpen > 0.3f) {
                    val tongue = ((mouthOpen - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    val tw = mouthW * 0.5f
                    val th = mouthH * 0.45f * tongue
                    drawOval(Tongue, topLeft = p(100f - tw / 2f, mouthTop + mouthH - th * 1.4f), size = sz(tw, th * 1.4f))
                }
                // Augen: weißer Ball, Pupille mit zwei Glanzpunkten; der Blick geht
                // hinauf zur Karte. Beim Blinzeln schließt das Lid von oben.
                val open = 1f - blink.value
                listOf(76f to 1f, 124f to -1f).forEach { (cx, inward) ->
                    val ry = 17f * (0.12f + 0.88f * open)
                    drawOval(Color.White, topLeft = p(cx - 15f, 88f - ry), size = sz(30f, ry * 2f))
                    if (open > 0.4f) {
                        val pupil = p(cx + 3f * inward, 85f)
                        drawCircle(PupilInk, radius = 8.5f * sx, center = pupil)
                        drawCircle(Color.White, radius = 3f * sx, center = pupil + Offset(2.6f * sx, -3.2f * sy))
                        drawCircle(Color.White, alpha = 0.8f, radius = 1.5f * sx, center = pupil + Offset(-2.4f * sx, 3f * sy))
                    } else {
                        drawLine(glyphColor, p(cx - 13f, 88f), p(cx + 13f, 88f), strokeWidth = 3.5f * sx, cap = StrokeCap.Round)
                    }
                }
            }
        }
        // Der Laut auf dem Bauch: bei Anlaut-Paaren nur die Großform ("Sch"), bei
        // Vokalpaaren beide ("Ei / ei") — der Aufrufer entscheidet über [label].
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = (heightDp * (bellyY - 24f) / FeederOutline.H).dp)
                .height((heightDp * 48f / FeederOutline.H).dp)
                .graphicsLayer {
                    // Der Glyph atmet mit dem Bauch.
                    translationY = -heightDp.dp.toPx() * (190f - bellyY) / FeederOutline.H * BreathLift * breath.value
                },
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

private fun DrawScope.drawLeaf(base: Offset, tip: Offset, sx: Float, color: Color) {
    val mid = (base + tip) / 2f
    val dir = tip - base
    val normal = Offset(-dir.y, dir.x) * 0.35f
    val leaf = androidx.compose.ui.graphics.Path().apply {
        moveTo(base.x, base.y)
        quadraticTo(mid.x + normal.x, mid.y + normal.y, tip.x, tip.y)
        quadraticTo(mid.x - normal.x, mid.y - normal.y, base.x, base.y)
        close()
    }
    drawPath(leaf, color)
    drawLine(LeafVein, base, mid, strokeWidth = 1.5f * sx)
}

/** Atmen: einmal ein, einmal aus in 3,6 s — Figurenspiel, ruhiger als [AbcMotion.BreathMs]. */
private const val BreathMs = 1_800
private const val BreathLift = 0.018f

private val ShadowInk = Color(0x59000000)
private val MouthInk = Color(0xFF3A1420)
private val Tongue = Color(0xFFE7788A)
private val PupilInk = Color(0xFF2A1D18)
private val CheekPink = Color(0xFFFF9AA0)
private val LeafLight = Color(0xFF8FD16A)
private val LeafDark = Color(0xFF6AB04C)
private val LeafVein = Color(0x663A6B2A)
