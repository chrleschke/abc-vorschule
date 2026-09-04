package app.abcvorschule.ui.exercise

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.ui.components.IconSpeaker
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.WarmInk
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
 * Ein Laut-Fresser (design doc §5): runder Körper, offenes Maul oben, zwei Augen,
 * Bauchfleck mit dem Laut in beiden Formen, Speaker-Icon in der Ecke. Canvas statt
 * Emoji oder Bitmap — Buttons und Figuren zeichnet die App selbst (§10), und nur
 * so folgt das Maul dem Drag.
 *
 * Tipp auf die ganze Figur spricht den Laut; das kleine Speaker-Icon ist nur der
 * Hinweis darauf, kein eigener Knopf mit eigener Trefferfläche.
 */
@Composable
fun FeederCreature(
    label: SymbolInWordDerivation.TargetLabel,
    color: Color,
    animator: FeederCreatureAnimator,
    hint: Boolean,
    speaking: Boolean,
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
            // Körper: Ellipse, unten etwas breiter — sitzt, statt zu schweben.
            drawOval(color = color, topLeft = Offset(0f, h * 0.12f), size = Size(w, h * 0.88f))
            // Maul: dunkle Ellipse am Kopf, Höhe nach Öffnungsgrad.
            val mouthW = w * 0.62f
            val mouthH = h * (0.06f + 0.26f * mouthOpen)
            drawOval(
                color = WarmInk,
                topLeft = Offset((w - mouthW) / 2f, h * 0.30f - mouthH / 2f),
                size = Size(mouthW, mouthH),
            )
            // Augen: zwei weiße Kreise mit Pupille, über dem Maul.
            listOf(0.34f, 0.66f).forEach { cx ->
                drawCircle(color = Cream, radius = w * 0.085f, center = Offset(w * cx, h * 0.17f))
                drawCircle(color = WarmInk, radius = w * 0.04f, center = Offset(w * cx, h * 0.18f))
            }
            // Bauchfleck: helle Ellipse, darauf steht der Laut (Text unten).
            val bellyW = w * SoundFeederSizing.BellyWidthFraction
            drawOval(
                color = Cream,
                topLeft = Offset((w - bellyW) / 2f, h * 0.50f),
                size = Size(bellyW, h * 0.40f),
            )
        }
        // Der Laut in beiden Formen, wie beim Detektiv: "S / s".
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (heightDp * 0.14f).dp)
                .height((heightDp * 0.32f).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buildString {
                    append(label.primary)
                    label.alternate?.let { append(" / ").append(it) }
                },
                fontSize = glyphSp.sp,
                fontWeight = FontWeight.SemiBold,
                color = WarmInk,
                maxLines = 1,
                softWrap = false,
            )
        }
        IconSpeaker(
            tint = if (enabled) Cream else Cream.copy(alpha = 0.5f),
            speaking = speaking,
            size = 22.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-6).dp, y = (heightDp * 0.30f).dp),
        )
    }
}
