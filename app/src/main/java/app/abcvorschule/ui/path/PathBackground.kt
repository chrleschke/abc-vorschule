package app.abcvorschule.ui.path

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.NightDeep
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightHorizon
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.NightPanel
import app.abcvorschule.ui.theme.SoftSand
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private const val StarCount = 40
private const val StarSeed = 42

/**
 * A second, finer tier of stars: the sky read as too empty on the device. They
 * are appended after the 40 main stars rather than mixed in, so the existing
 * ones draw the same values out of Random(42) as before and keep their exact
 * positions — only the dust around them is new. Radius stays below 1dp and the
 * twinkle alpha is the same 0.10..0.25 as the main tier, so no dot gets
 * brighter than one that is on screen today.
 */
private const val SmallStarCount = 34

/** Line segments a hill's wave is sampled with — 24 was the old loop's step count. */
private const val HillSegments = 24

/**
 * Tree silhouettes on the front hill band. In dp, not raw px: the old 42f/4f/16f
 * were unconverted DrawScope pixels, so the trees shrank with rising density —
 * ~15dp tall on an xxhdpi phone, ~46dp on an mdpi tablet. The star field in the
 * same file already converts (star.radius.dp.toPx()), so this was an oversight,
 * not a style. Ratio kept at the old 1.44:1 height-to-width so the silhouette
 * reads the same, only bigger.
 */
private val TreeApexHeight = 26.dp
private val TreeBaseDepth = 3.dp
private val TreeHalfWidth = 10.dp

/**
 * The trees stand ON the front band — NightElevated at alpha 0.9 over the
 * gradient, effectively ~#213146 — so they must stay darker than that or they
 * stop reading as silhouette. NightInk at 0.65 composites to ~#151F30: 1.23:1
 * against the band, one visible step up from the old 0.85 (~#111A29, 1.31:1),
 * and still strictly darker than the ground they stand on.
 */
private const val TreeAlpha = 0.65f

/** Star position in 0..1 screen fractions plus its twinkle phase. */
private data class Star(val fx: Float, val fy: Float, val radius: Float, val phase: Float)

/**
 * The night landscape behind the path: a vertical gradient, a fixed star field
 * and three layers of hills that drift slowly as the child scrolls.
 *
 * [scrollOffset] is passed as a lambda, not a value: it is read inside each
 * hill band's drawBehind block, so scrolling moves the hills without
 * recomposing anything — only redrawing.
 */
@Composable
fun PathBackground(scrollOffset: () -> Int, modifier: Modifier = Modifier) {
    // Fixed seed: the sky must look scattered but must not re-scatter itself on
    // every recomposition.
    val stars = remember {
        val random = Random(StarSeed)
        List(StarCount) { index ->
            Star(
                fx = random.nextFloat(),
                // Stars only in the upper two thirds — below that are the hills.
                fy = random.nextFloat() * 0.66f,
                radius = 1f + random.nextFloat(),
                phase = index * 0.37f,
            )
        } + List(SmallStarCount) { index ->
            Star(
                fx = random.nextFloat(),
                fy = random.nextFloat() * 0.66f,
                radius = 0.5f + random.nextFloat() * 0.4f,
                // The phase series simply continues, so the small stars do not
                // blink in lockstep with the big ones above them.
                phase = (StarCount + index) * 0.37f,
            )
        }
    }

    // One transition drives all stars; each star reads it at its own phase offset
    // instead of owning an animation of its own.
    val transition = rememberInfiniteTransition(label = "sky")
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Reverse),
        label = "sky_twinkle",
    )

    // Everything is wrapped in a Box of its own: the layers must stack on top of
    // each other, not depend on the caller happening to be a Box.
    Box(modifier.fillMaxSize()) {
        // The gradient gets a Canvas of its own, drawn first and therefore behind
        // the stars. Sharing one with the star field would rebuild its shader on
        // every twinkle frame, because that lambda reads the animation and is
        // invalidated ~60x a second for the life of the screen.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to NightDeep,
                    0.55f to NightInk,
                    1f to NightHorizon,
                ),
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            stars.forEach { star ->
                val twinkleAlpha =
                    0.10f + 0.15f * abs(sin((twinkle + star.phase) * PI.toFloat()))
                drawCircle(
                    color = SoftSand.copy(alpha = twinkleAlpha),
                    radius = star.radius.dp.toPx(),
                    center = Offset(star.fx * size.width, star.fy * size.height),
                )
            }
        }

        // Hills sit in their own layers so each can drift at its own parallax factor.
        HillBand(color = NightPanel, alpha = 0.5f, baseFraction = 0.72f, amplitude = 34f, parallax = 0.05f, scrollOffset = scrollOffset)
        HillBand(color = NightPanel, alpha = 0.7f, baseFraction = 0.82f, amplitude = 46f, parallax = 0.10f, scrollOffset = scrollOffset)
        HillBand(color = NightElevated, alpha = 0.9f, baseFraction = 0.92f, amplitude = 28f, parallax = 0.15f, scrollOffset = scrollOffset, trees = true)
    }
}

@Composable
private fun HillBand(
    color: Color,
    alpha: Float,
    baseFraction: Float,
    amplitude: Float,
    parallax: Float,
    scrollOffset: () -> Int,
    trees: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxSize()
            .drawBehind {
                // Parallax is folded into the wave's own vertical position rather
                // than a graphicsLayer translation on the whole box: a translated
                // box is viewport-sized and does not grow, so shifting it opens an
                // undrawn gap at the trailing edge once it has moved far enough.
                // Recomputing base here instead means the fill always runs from
                // the (shifted) wave down to size.height, so the bottom of the
                // viewport is covered at any scroll offset, positive or negative.
                val base = size.height * baseFraction - scrollOffset() * parallax
                // Indexed, and the wave is sampled on the 0..1 fraction rather than
                // on x/size.width: an accumulating `while (x <= size.width)` loop
                // never terminates at width 0 (the increment is 0 too) and grows the
                // Path until the app dies, and both divisions by size.width are gone
                // with it. A draw pass at zero width is reachable through a
                // freeform/split-screen resize.
                val hill = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(0f, base)
                    for (i in 0..HillSegments) {
                        val fx = i.toFloat() / HillSegments
                        lineTo(size.width * fx, base - amplitude * sin(fx * 3.4f))
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(hill, color = color.copy(alpha = alpha))
                if (trees) {
                    // Converted once per draw pass, not per tree.
                    val apex = TreeApexHeight.toPx()
                    val skirt = TreeBaseDepth.toPx()
                    val halfWidth = TreeHalfWidth.toPx()
                    listOf(0.14f, 0.31f, 0.68f, 0.86f).forEach { fx ->
                        val tx = size.width * fx
                        // Same wave the band is sampled on, so the trunk sits on the
                        // crest. The four fractions fall between the 24 polyline
                        // samples, but the interpolation error is under 0.03px at
                        // amplitude 28f — the skirt below ty absorbs it.
                        val ty = base - amplitude * sin(fx * 3.4f)
                        val tree = Path().apply {
                            moveTo(tx, ty - apex)
                            lineTo(tx - halfWidth, ty + skirt)
                            lineTo(tx + halfWidth, ty + skirt)
                            close()
                        }
                        drawPath(tree, color = NightInk.copy(alpha = TreeAlpha))
                    }
                }
            },
    )
}
