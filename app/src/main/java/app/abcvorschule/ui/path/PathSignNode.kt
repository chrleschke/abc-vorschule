package app.abcvorschule.ui.path

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.R
import app.abcvorschule.content.LessonSign
import app.abcvorschule.content.SignBlock
import app.abcvorschule.progress.LessonState
import app.abcvorschule.ui.components.IconLock
import app.abcvorschule.ui.components.IconRepeat
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.LeafGreenLight
import app.abcvorschule.ui.theme.SignBlockLocked
import app.abcvorschule.ui.theme.SignBlockTone
import app.abcvorschule.ui.theme.SignBlockTones
import app.abcvorschule.ui.theme.SilboEmoji
import app.abcvorschule.ui.theme.SilboFibel
import app.abcvorschule.ui.theme.SkyBlueLight
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.WarmMuted
import app.abcvorschule.ui.theme.WoodDark
import app.abcvorschule.ui.theme.WoodDarkShade
import app.abcvorschule.ui.theme.WoodMid
import app.abcvorschule.ui.theme.WoodMidShade
import app.abcvorschule.ui.theme.WoodWarm
import app.abcvorschule.ui.theme.WoodWarmShade
import app.abcvorschule.ui.world.rememberReduceMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.sin

/**
 * Sizes of a path sign, in dp. Pure numbers, so geometry and tests can reason about
 * them without Compose. A sign is a small tower of ABC blocks on a plank on a post;
 * its height depends on how many rows the tower has, its layout box does not.
 *
 * The blocks are a painted object in the landscape and do not grow with the
 * system font scale (dp-stable lettering on a pictorial object is the sanctioned
 * exception; TalkBack announces the sounds).
 */
internal object PathSignLayout {
    /** Edge of a block, and the depth of every block (the side the picture is on). */
    const val BlockDp = 52f

    /** Horizontal room between the letters and a wide block's edge ("Sch"). */
    const val BlockPadDp = 8f

    /** Gap between neighbouring blocks: a turning cube is wider than it stands. */
    const val BlockGapDp = 7f
    const val RowGapDp = 3f
    const val GlyphDp = 30f
    const val PictureDp = 28f
    const val ShelfHeightDp = 12f

    /** The plank reaches this far past the tower on each side. */
    const val ShelfOverhangDp = 6f
    const val PostHeightDp = 26f
    const val PerRow = 2

    /**
     * The layout box every sign is centred in. Wide enough for the widest pair the
     * pack has ("Sch Ch", ~141dp), asserted in PathSignLayoutTest; PathGeometry's
     * 192dp swing still clears it (PathGeometryTest).
     */
    const val WidthDp = 148f

    /**
     * Advance in em of the learning font's bold cut (silbo_fibel_bold.ttf, read out
     * with fontTools), for the characters a block can carry. Unknown characters
     * count as the widest capital, so a new grapheme errs on the wide side.
     */
    private val advanceEm = mapOf(
        'A' to 0.725f, 'B' to 0.676f, 'C' to 0.676f, 'D' to 0.725f, 'E' to 0.592f, 'F' to 0.586f,
        'G' to 0.719f, 'H' to 0.74f, 'I' to 0.286f, 'J' to 0.503f, 'K' to 0.698f, 'L' to 0.552f,
        'M' to 0.906f, 'N' to 0.754f, 'O' to 0.732f, 'P' to 0.623f, 'Q' to 0.754f, 'R' to 0.674f,
        'S' to 0.607f, 'T' to 0.618f, 'U' to 0.728f, 'V' to 0.715f, 'W' to 1.042f, 'X' to 0.667f,
        'Y' to 0.666f, 'Z' to 0.61f, 'Ä' to 0.725f, 'Ö' to 0.732f, 'Ü' to 0.728f,
        'c' to 0.499f, 'f' to 0.391f, 'h' to 0.603f, 'i' to 0.312f, 'k' to 0.556f, 'p' to 0.603f,
        'u' to 0.608f, 'ß' to 0.694f,
    )

    fun glyphWidthDp(glyph: String): Float = glyph.sumOf { (advanceEm[it] ?: 1.042f).toDouble() }.toFloat() * GlyphDp

    fun blockWidthDp(glyph: String): Float = max(BlockDp, glyphWidthDp(glyph) + 2 * BlockPadDp)

    /** Bottom row first: two sounds stand side by side, the next two go on top. */
    fun <T> rows(blocks: List<T>): List<List<T>> = blocks.chunked(PerRow).ifEmpty { listOf(emptyList()) }

    fun rowCount(blockCount: Int): Int = max(1, (blockCount + PerRow - 1) / PerRow)

    fun towerHeightDp(blockCount: Int): Float {
        val rows = rowCount(blockCount)
        return rows * BlockDp + (rows - 1) * RowGapDp
    }

    /** From the foot of the post to the top of the tower. */
    fun totalHeightDp(blockCount: Int): Float = towerHeightDp(blockCount) + ShelfHeightDp + PostHeightDp

    fun shelfWidthDp(glyphs: List<String>): Float {
        val widest = rows(glyphs).maxOf { row ->
            row.sumOf { blockWidthDp(it).toDouble() }.toFloat() + (row.size - 1).coerceAtLeast(0) * BlockGapDp
        }
        return max(widest, BlockDp) + 2 * ShelfOverhangDp
    }
}

/** Deliberately not named PathSignNode — a sibling object and composable with the
 *  same name compiles, but reads like a typo at every call site. */
object PathSignDimens {
    val Width = PathSignLayout.WidthDp.dp

    /** The tallest sign the layout can produce: two rows of blocks. Used where one
     *  number has to cover every sign (the path's top margin, the scroll headroom). */
    val MaxTotalHeight = PathSignLayout.totalHeightDp(PathSignLayout.PerRow * 2).dp

    fun totalHeight(blockCount: Int) = PathSignLayout.totalHeightDp(blockCount).dp
}

/**
 * The cube's turn, in the cube's own terms. Pure math so the projection is testable.
 *
 * A real cube turning about its vertical axis brings its right side to the front.
 * Compose has no z-translation, so each face is rotated about its own centre and
 * shifted sideways to where that centre would project: the front's centre sits
 * depth/2 in front of the axis and swings left, the side's sits width/2 to the
 * right and swings to the middle.
 */
internal object CubeTurn {
    /**
     * The turn sense of Compose's rotationY that brings the right side forward:
     * mid-turn the shared edge is the nearest one, so it must draw tallest. Checked
     * on the emulator — with -1 the two outer edges came out taller instead.
     */
    const val Sense = 1f

    fun angleDeg(progress: Float): Float = 90f * progress.coerceIn(-0.15f, 1.15f)

    fun frontTranslationX(angleDeg: Float, depthPx: Float): Float = -depthPx / 2f * sin(angleDeg.rad())

    fun sideTranslationX(angleDeg: Float, widthPx: Float): Float = widthPx / 2f * kotlin.math.cos(angleDeg.rad())

    /**
     * A face turned past edge-on would show its mirrored back, and one a degree or two
     * short of it is a stray hairline while the spring settles.
     */
    fun frontVisible(angleDeg: Float) = angleDeg < 88f

    fun sideVisible(angleDeg: Float) = angleDeg > 2f

    /** Shrinks a little mid-turn, as if lifted, so it clears its neighbours. */
    fun lift(angleDeg: Float): Float = 1f - 0.12f * sin(2f * angleDeg.rad()).coerceAtLeast(0f)

    private fun Float.rad() = this / 180f * PI.toFloat()
}

/**
 * Choreography of the current sign: its cubes turn one after another to show the
 * picture on their side ("M ... wie Mond"), hold, and turn back; then the sign rests.
 * Own numbers under AbcMotion's rule 3 (Figurenspiel) — and the one idle motion on the
 * path besides the marker's bob. It replaces the ring pulse the current sign had.
 */
internal object SignTurnChoreo {
    /** After the path appears, so the child has found the pin first. */
    const val FirstDelayMs = 1400L
    const val StaggerMs = AbcMotion.ShortMs.toLong()
    const val HoldMs = 1600L

    /** Rest between two turns. Long on purpose: a hint, not a fidget. */
    const val PauseMs = 6500L
}

/**
 * A lesson as a small tower of ABC blocks on a plank: one cube per sound, in its
 * capital form, with the sound's Anlaut picture on the cube's side. Only the
 * current sign turns its cubes to show the pictures; every other sign shows the
 * letters alone, which keeps the path calm. Locked signs carry a lock and dark,
 * dimmed blocks — the letters stay legible, so the path shows what is ahead.
 *
 * @param playable Whether a tap opens the lesson. The caller owns this because the
 * parent's "free order" switch feeds into it — a sign can be [LessonState.Locked]
 * and still playable.
 * @param tag Stable id for tests.
 */
@Composable
fun PathSignNode(
    sign: LessonSign,
    tag: String,
    state: LessonState,
    playable: Boolean,
    highlighted: Boolean,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Deliberately not `!playable`: a parent-unlocked sign keeps the dimmed look of the
    // lesson the child has not reached yet, so the path still shows where it stands.
    val dimmed = state == LessonState.Locked || state == LessonState.Planned
    val shelf = when (state) {
        LessonState.Mastered -> WoodWarm
        LessonState.Available, LessonState.InProgress -> WoodMid
        LessonState.Locked, LessonState.Planned -> WoodDark
    }
    // The post stands behind the plank, so it takes the plank's own shade instead of
    // one global post tone — a single tone would sit above WoodDark and flip the depth
    // on every locked sign.
    val shade = when (state) {
        LessonState.Mastered -> WoodWarmShade
        LessonState.Available, LessonState.InProgress -> WoodMidShade
        LessonState.Locked, LessonState.Planned -> WoodDarkShade
    }
    // The light variants on dark wood (LeafGreenLight on WoodMid 5.65:1, on WoodWarm
    // 3.82:1; SkyBlueLight on WoodMid 5.37:1) — LeafGreen/SkyBlue themselves are
    // calibrated against Cream and vanish on wood.
    val ring: Color = when (state) {
        LessonState.Mastered, LessonState.Available -> LeafGreenLight
        LessonState.InProgress -> SkyBlueLight
        LessonState.Locked, LessonState.Planned -> WarmMuted.copy(alpha = 0.55f)
    }
    val stateDesc = stringResource(
        when {
            // What TalkBack has to convey is whether the sign opens, so a
            // parent-unlocked one announces itself as available despite its state.
            state == LessonState.Mastered -> R.string.lesson_mastered
            playable -> R.string.lesson_available
            else -> R.string.lesson_locked
        },
    )
    val nodeDesc = stringResource(R.string.path_node)
    // The marker above the sign is decorative for TalkBack, so "this is the one" has
    // to reach a screen-reader user here, on the sign itself.
    val currentDesc = if (highlighted) ", ${stringResource(R.string.lesson_current)}" else ""
    val spoken = sign.blocks.joinToString(", ") { it.glyph }

    val still = rememberReduceMotion()
    val turns = remember(sign) { sign.blocks.map { Animatable(0f) } }
    LaunchedEffect(sign, highlighted, still) {
        if (!highlighted || still) {
            turns.forEach { it.snapTo(0f) }
            return@LaunchedEffect
        }
        delay(SignTurnChoreo.FirstDelayMs)
        while (true) {
            coroutineScope {
                var order = 0
                sign.blocks.forEachIndexed { i, block ->
                    if (block.emoji == null) return@forEachIndexed
                    val wait = order++ * SignTurnChoreo.StaggerMs
                    launch {
                        delay(wait)
                        turns[i].animateTo(1f, AbcMotion.Soft.spec())
                        delay(SignTurnChoreo.HoldMs)
                        turns[i].animateTo(0f, AbcMotion.Soft.spec())
                    }
                }
            }
            delay(SignTurnChoreo.PauseMs)
        }
    }

    Box(
        modifier = modifier
            .width(PathSignDimens.Width)
            .height(PathSignDimens.totalHeight(sign.blocks.size))
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$nodeDesc $spoken, $stateDesc$currentDesc" }
            .testTag("path_node_$tag"),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                // A hand-built sign is never perfectly straight. Deterministic, so it
                // does not re-tilt on recomposition.
                .graphicsLayer { rotationZ = 1.5f * PathNoise.signed(index, salt = 5) }
                .width(IntrinsicSize.Max),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                Modifier
                    .padding(horizontal = PathSignLayout.ShelfOverhangDp.dp)
                    // Am Abendhimmel: erreichbare Schilder leuchten warm, als hinge eine
                    // Laterne daneben — sonst verschwände das Brett im Dunkel.
                    .drawBehind {
                        if (!dimmed) {
                            val r = size.maxDimension * 0.85f
                            drawCircle(
                                brush = Brush.radialGradient(
                                    0.35f to SignGlow.copy(alpha = if (highlighted) 0.55f else 0.34f),
                                    1f to Color.Transparent,
                                    center = center,
                                    radius = r,
                                ),
                                radius = r,
                                center = center,
                            )
                        }
                    },
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(PathSignLayout.RowGapDp.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val rows = PathSignLayout.rows(sign.blocks.withIndex().toList())
                    // Rows are listed bottom first; a Column lays out top first.
                    rows.asReversed().forEachIndexed { level, row ->
                        // Badges hang on the top row, not on the tower's box: with three
                        // sounds the top row is one block wide, and the box's corner
                        // above the lower row's second block is empty air.
                        Box {
                            Row(horizontalArrangement = Arrangement.spacedBy(PathSignLayout.BlockGapDp.dp)) {
                                if (row.isEmpty()) {
                                    // A planned lesson without sounds: one blank block, so
                                    // the sign is not an empty plank.
                                    SignCube(SignBlock("", "", null, 0), SignBlockLocked, dimmed = true, turn = { 0f }, tilt = 0f)
                                }
                                row.forEach { (i, block) ->
                                    SignCube(
                                        block = block,
                                        tone = if (dimmed) SignBlockLocked else toneFor(block),
                                        dimmed = dimmed,
                                        turn = { turns[i].value },
                                        tilt = 3f * PathNoise.signed(index * 8 + i, salt = 13),
                                    )
                                }
                            }
                            if (level == 0) {
                                SignBadges(
                                    state = state,
                                    playable = playable,
                                    review = sign.review,
                                    modifier = Modifier.matchParentSize(),
                                )
                            }
                        }
                    }
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(PathSignLayout.ShelfHeightDp.dp)
                    .background(shelf, PlankShape)
                    .border(2.dp, ring, PlankShape),
            )
            Box(
                Modifier
                    .width(10.dp)
                    .height(PathSignLayout.PostHeightDp.dp)
                    .background(shade, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)),
            )
        }
    }
}

/** Star (mastered) or lock (not playable) at the top right, ↻ for a review at the top left. */
@Composable
private fun SignBadges(state: LessonState, playable: Boolean, review: Boolean, modifier: Modifier) {
    Box(modifier.clearAndSetSemantics {}) {
        val corner = Modifier.size(BadgeSize)
        when {
            state == LessonState.Mastered -> Box(
                corner
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-10).dp)
                    .background(WoodDarkShade, CircleShape)
                    .border(1.5.dp, StarGold, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                // Gold star on dark wood, like every other star in the app; the flat
                // silhouette, because the deep outline only eats a glyph this small.
                IconStar(tint = StarGold, outline = StarGold, size = 15.dp)
            }
            // The child cannot read, so "not yet" must not rest on the dark blocks
            // alone. Vector lock, not the 🔒 emoji: the emoji renders vendor-gold and
            // collides with the StarGold reward role (§10: UI chrome is vector/ASCII).
            !playable -> Box(
                corner
                    .align(Alignment.TopEnd)
                    .offset(x = 8.dp, y = (-10).dp)
                    .background(WoodDarkShade, CircleShape)
                    .border(1.5.dp, SoftSand.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconLock(tint = SoftSand.copy(alpha = 0.6f), size = 16.dp)
            }
        }
        if (review) {
            Box(
                corner
                    .align(Alignment.TopStart)
                    .offset(x = (-8).dp, y = (-10).dp)
                    .background(SkyBlueLight, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconRepeat(tint = WoodDark, size = 18.dp)
            }
        }
    }
}

/**
 * One ABC block. Front: the sound. Side: its picture on a cream disc, seen only while
 * [turn] runs from 0 (letter facing) to 1 (picture facing).
 *
 * @param turn Read in the draw phase, so a turn frame repaints the cube without
 * recomposing the sign.
 */
@Composable
private fun SignCube(block: SignBlock, tone: SignBlockTone, dimmed: Boolean, turn: () -> Float, tilt: Float) {
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val depthPx = with(density) { PathSignLayout.BlockDp.dp.toPx() }
    val camera = 12f * density.density
    var widthPx by remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .graphicsLayer {
                rotationZ = tilt
                val s = CubeTurn.lift(CubeTurn.angleDeg(turn()))
                scaleX = s
                scaleY = s
            }
            .onSizeChanged { widthPx = it.width },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    val a = CubeTurn.angleDeg(turn())
                    cameraDistance = camera
                    rotationY = CubeTurn.Sense * a.coerceAtLeast(-12f)
                    translationX = CubeTurn.frontTranslationX(a, depthPx)
                    alpha = if (CubeTurn.frontVisible(a)) 1f else 0f
                }
                .drawBehind {
                    drawBlockFace(tone, sheen = !dimmed)
                    // The face turning away loses light.
                    val a = CubeTurn.angleDeg(turn()).coerceIn(0f, 90f)
                    drawFaceShade(0.32f * sin(a / 180f * PI.toFloat()))
                }
                .height(PathSignLayout.BlockDp.dp)
                .widthIn(min = PathSignLayout.BlockDp.dp)
                .padding(horizontal = PathSignLayout.BlockPadDp.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = block.glyph,
                fontFamily = SilboFibel,
                fontWeight = FontWeight.Bold,
                fontSize = (PathSignLayout.GlyphDp / fontScale).sp,
                color = if (dimmed) SoftSand.copy(alpha = 0.6f) else WoodDark,
                maxLines = 1,
                softWrap = false,
                // Optical centre: the font's line box sits a touch low on capitals.
                modifier = Modifier.offset(y = (-1).dp),
            )
        }
        val emoji = block.emoji
        if (emoji != null && !dimmed) {
            Box(
                Modifier
                    .size(PathSignLayout.BlockDp.dp)
                    .graphicsLayer {
                        val a = CubeTurn.angleDeg(turn())
                        cameraDistance = camera
                        rotationY = CubeTurn.Sense * (a - 90f)
                        translationX = CubeTurn.sideTranslationX(a, widthPx.toFloat())
                        alpha = if (CubeTurn.sideVisible(a)) 1f else 0f
                    }
                    .drawBehind {
                        drawBlockFace(tone)
                        val a = CubeTurn.angleDeg(turn()).coerceIn(0f, 90f)
                        drawFaceShade(0.32f * kotlin.math.cos(a / 180f * PI.toFloat()))
                    }
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(Cream, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = emoji,
                        fontFamily = SilboEmoji,
                        fontSize = (PathSignLayout.PictureDp / fontScale).sp,
                        color = Color.Unspecified,
                    )
                }
            }
        }
    }
}

/** Block grammar of the Zahlentürme: light edge top left, shadow bottom right, rim, sheen. */
/**
 * @param sheen The glossy bar top left. Only lit blocks carry it: on a locked block's
 * dark wood it read as a mysterious stripe, not as light.
 */
private fun DrawScope.drawBlockFace(tone: SignBlockTone, sheen: Boolean = true) {
    val b = size.height
    val r = CornerRadius(b * 0.18f)
    drawRoundRect(
        Brush.linearGradient(0f to tone.hi, 0.45f to tone.face, 1f to tone.lo, start = Offset.Zero, end = Offset(size.width, size.height)),
        cornerRadius = r,
    )
    drawRoundRect(tone.rim, cornerRadius = r, alpha = 0.55f, style = Stroke(width = max(1f, b * 0.05f)))
    if (!sheen) return
    drawRoundRect(
        Color.White,
        topLeft = Offset(b * 0.12f, b * 0.09f),
        size = Size(b * 0.42f, b * 0.12f),
        cornerRadius = CornerRadius(b * 0.06f),
        alpha = 0.32f,
    )
}

private fun DrawScope.drawFaceShade(alpha: Float) {
    if (alpha <= 0.005f) return
    drawRoundRect(Color.Black, cornerRadius = CornerRadius(size.height * 0.18f), alpha = alpha)
}

/** Same sound, same tone, everywhere on the path (see [SignBlock.tone]). */
private fun toneFor(block: SignBlock): SignBlockTone = SignBlockTones[Math.floorMod(block.tone, SignBlockTones.size)]

private val PlankShape = RoundedCornerShape(4.dp)
private val BadgeSize = 24.dp

/** Warmes Laternenlicht hinter erreichbaren Schildern. */
private val SignGlow = Color(0xFFFFC878)
