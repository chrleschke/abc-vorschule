package app.abcvorschule.ui.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.CloudWhite
import app.abcvorschule.ui.theme.DayHorizon
import app.abcvorschule.ui.theme.DaySkyMid
import app.abcvorschule.ui.theme.DaySkyTop
import app.abcvorschule.ui.theme.HillFar
import app.abcvorschule.ui.theme.HillMid
import app.abcvorschule.ui.theme.HillNear
import app.abcvorschule.ui.theme.SunGlow
import app.abcvorschule.ui.theme.TreeCrown
import app.abcvorschule.ui.theme.TreeTrunk
import kotlin.math.sin

/**
 * The sun, in 0..1 screen fractions. Upper right third, clear of every cloud
 * anchor below, and high enough that the boards of the first signs never run
 * into it.
 */
private const val SunFx = 0.80f
private const val SunFy = 0.12f
private val SunRadius = 30.dp

/**
 * The halo is a radial gradient from SunGlow at alpha 0.35 out to fully
 * transparent, not a second flat disc: a flat one draws a hard ring edge
 * exactly where it is supposed to fade out. It is deliberately soft rather than
 * bright — this is a friendly daytime sun, not a glare source on a screen a
 * four-year-old holds close to their face.
 */
private val SunHaloRadius = 66.dp
private const val SunHaloAlpha = 0.35f

/** A cloud anchor in 0..1 screen fractions, plus the total width of its puffs. */
private data class Cloud(val fx: Float, val fy: Float, val width: Dp)

/**
 * Four clouds, placed by hand rather than drawn from the old star field's
 * Random(42). Forty scattered dots could be random because no single one
 * mattered; four large shapes cannot — a random draw clumps two of them or puts
 * one behind the sun, and there is no parallax on this layer that a fixed seed
 * would have to keep stable. [fy] stays in the upper two thirds, the same band
 * the stars were confined to, because below that are the hills.
 *
 * Bigger and higher, smaller and lower: that is the way a sky reads, clouds
 * near the horizon being further away.
 */
private val Clouds = listOf(
    Cloud(fx = 0.18f, fy = 0.11f, width = 100.dp),
    Cloud(fx = 0.52f, fy = 0.24f, width = 74.dp),
    Cloud(fx = 0.86f, fy = 0.38f, width = 62.dp),
    Cloud(fx = 0.30f, fy = 0.46f, width = 56.dp),
)

/**
 * One puff per entry: horizontal offset from the cloud's centre and radius,
 * both as a fraction of the cloud's width. All three sit with their lower edge
 * on the same baseline, which is what gives a cloud its flat bottom.
 *
 * They overlap, and each is drawn at [CloudAlpha] instead of into a layer, so
 * the overlaps composite to 1 - 0.1² = 0.99 rather than 0.90. Against the sky
 * that is a step of under 6/255 in an already near-white area — invisible, and
 * not worth an offscreen buffer for a layer that is otherwise drawn once.
 */
private val CloudPuffs = listOf(-0.30f to 0.22f, 0.02f to 0.30f, 0.30f to 0.23f)

/** Puffs are squashed circles; a round one reads as a balloon. */
private const val CloudPuffFlatten = 0.72f
private const val CloudAlpha = 0.9f

/** Line segments a hill's wave is sampled with — 24 was the old loop's step count. */
private const val HillSegments = 24

/**
 * Trees on the front hill band. In dp, not raw px: the old 42f/4f/16f were
 * unconverted DrawScope pixels, so the trees shrank with rising density —
 * ~15dp tall on an xxhdpi phone, ~46dp on an mdpi tablet. Ratio kept at the old
 * 1.44:1 height-to-width so the crown reads the same, only bigger.
 */
private val TreeApexHeight = 26.dp
private val TreeBaseDepth = 3.dp
private val TreeHalfWidth = 10.dp

/**
 * The trunk stub below the crown. The crown triangle is unchanged, so the only
 * new shape is the piece of trunk that pokes out under it: at half the apex
 * height the triangle is still ~5.5dp wide either side, so the 2.5dp of trunk
 * hidden behind it never shows a corner.
 *
 * The tree used to be a single darker-than-the-ground silhouette (1.23:1). It
 * is now a two-tone tree in daylight, and the contrast that carries it is the
 * crown against the sky it rises into — TreeCrown on DayHorizon, 3.49:1. The
 * stub itself, TreeTrunk on HillNear, is 2.69:1: below the 3:1 UI-component
 * bar, which does not apply — this is decoration in the sense of WCAG 1.4.11,
 * carries no information, and is still twice the separation the night
 * silhouette had.
 */
private val TreeTrunkWidth = 5.dp
private val TreeTrunkDepth = 7.dp

/**
 * The day landscape behind the path: a vertical sky gradient, a sun with a soft
 * halo, a handful of clouds and three layers of hills that drift slowly as the
 * child scrolls.
 *
 * Nothing up here animates any more. The stars needed their twinkle — a dot at
 * alpha 0.10..0.25 on near-black is only visible as a change — but a cloud
 * breathing between alpha 0.85 and 0.95 against a light sky is a step of a
 * couple of values out of 255, invisible, and it would cost a redraw of the sky
 * every frame for as long as the app's start screen is open.
 *
 * [scrollOffset] is passed as a lambda, not a value: it is read inside each
 * hill band's drawBehind block, so scrolling moves the hills without
 * recomposing anything — only redrawing.
 */
@Composable
fun PathBackground(scrollOffset: () -> Int, modifier: Modifier = Modifier) {
    // Everything is wrapped in a Box of its own: the layers must stack on top of
    // each other, not depend on the caller happening to be a Box.
    Box(modifier.fillMaxSize()) {
        // The gradient gets a Canvas of its own, drawn first and therefore behind
        // the sun and clouds.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to DaySkyTop,
                    0.55f to DaySkyMid,
                    1f to DayHorizon,
                ),
            )
        }

        Canvas(Modifier.fillMaxSize()) {
            val sunCenter = Offset(SunFx * size.width, SunFy * size.height)
            val haloRadius = SunHaloRadius.toPx()
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(SunGlow.copy(alpha = SunHaloAlpha), SunGlow.copy(alpha = 0f)),
                    center = sunCenter,
                    radius = haloRadius,
                ),
                radius = haloRadius,
                center = sunCenter,
            )
            drawCircle(color = SunGlow, radius = SunRadius.toPx(), center = sunCenter)

            Clouds.forEach { cloud ->
                val width = cloud.width.toPx()
                val centerX = cloud.fx * size.width
                val baseline = cloud.fy * size.height
                CloudPuffs.forEach { (offsetFraction, radiusFraction) ->
                    val radius = width * radiusFraction
                    val puffHeight = radius * 2f * CloudPuffFlatten
                    drawOval(
                        color = CloudWhite.copy(alpha = CloudAlpha),
                        topLeft = Offset(
                            x = centerX + width * offsetFraction - radius,
                            y = baseline - puffHeight,
                        ),
                        size = Size(radius * 2f, puffHeight),
                    )
                }
            }
        }

        // Hills sit in their own layers so each can drift at its own parallax factor.
        // They are drawn opaque: the three tones are what separates them now, where
        // the night bands leaned on transparency over a dark gradient.
        //
        // The amplitudes are raw DrawScope pixels while the trees standing on them
        // are dp — a known inconsistency, left in place on purpose: converting them
        // reshapes the silhouette of the whole landscape at every density, and this
        // one was signed off on a device. What it costs is that the
        // tree-height-to-wave-amplitude ratio is density dependent and swings by 4x
        // between mdpi and xxxhdpi. Whoever closes the gap has to convert all three
        // together with TreeApexHeight, using the density the sign-off happened on as
        // the divisor (on an xxhdpi phone that is 34/46/28px -> ~11/15/9dp), and then
        // look at the screen again: the numbers alone do not say whether the hills
        // still read as hills.
        HillBand(color = HillFar, baseFraction = 0.72f, amplitude = 34f, parallax = 0.05f, scrollOffset = scrollOffset)
        HillBand(color = HillMid, baseFraction = 0.82f, amplitude = 46f, parallax = 0.10f, scrollOffset = scrollOffset)
        HillBand(color = HillNear, baseFraction = 0.92f, amplitude = 28f, parallax = 0.15f, scrollOffset = scrollOffset, trees = true)
    }
}

@Composable
private fun HillBand(
    color: Color,
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
                drawPath(hill, color = color)
                if (trees) {
                    // Converted once per draw pass, not per tree.
                    val apex = TreeApexHeight.toPx()
                    val skirt = TreeBaseDepth.toPx()
                    val halfWidth = TreeHalfWidth.toPx()
                    val trunkHalfWidth = TreeTrunkWidth.toPx() / 2f
                    val trunkDepth = TreeTrunkDepth.toPx()
                    listOf(0.14f, 0.31f, 0.68f, 0.86f).forEach { fx ->
                        val tx = size.width * fx
                        // Same wave the band is sampled on, so the trunk sits on the
                        // crest. The four fractions fall between the 24 polyline
                        // samples, so the exact ty and the drawn edge differ by
                        // 0.030/0.060/0.044/0.015px at amplitude 28f — at most 0.06px,
                        // and the 3dp skirt below ty absorbs it.
                        val ty = base - amplitude * sin(fx * 3.4f)
                        // Trunk first, crown over it: the top half of the trunk is
                        // meant to disappear behind the crown, not to be a seam.
                        val trunkTop = ty - apex * 0.5f
                        drawRect(
                            color = TreeTrunk,
                            topLeft = Offset(tx - trunkHalfWidth, trunkTop),
                            size = Size(trunkHalfWidth * 2f, ty + skirt + trunkDepth - trunkTop),
                        )
                        val crown = Path().apply {
                            moveTo(tx, ty - apex)
                            lineTo(tx - halfWidth, ty + skirt)
                            lineTo(tx + halfWidth, ty + skirt)
                            close()
                        }
                        drawPath(crown, color = TreeCrown)
                    }
                }
            },
    )
}
