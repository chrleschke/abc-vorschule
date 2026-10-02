package app.abcvorschule.ui.exercise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.ui.rewards.roundedStarPath
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.LeafGreenLight
import app.abcvorschule.ui.theme.SilboEmoji
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * Das Bilderbuch des Satz-Verstehers (PRODUCT_PRINCIPLES §9/§10, „Leseecke").
 *
 * Aufgeschlagen, von der linken Seite nur ein schmaler Streifen am Rand, der Rest liegt
 * außerhalb des Bilds; der Einband ist ein dunkelroter Rand, rechts zeigen zwei Linien
 * die Seitendicke. Die rechte Seite ist die Licht-Insel (§10, „Lerninhalt nur auf
 * Licht"): oben ein leerer, gestrichelter Bildrahmen mit blassem Stern, darunter ein
 * Wort-Balken je Wort des Satzes ([SentenceBarSizing]). Ohne deutsches TTS steht dort
 * statt der Balken der Satz selbst, damit ein Erwachsener vorlesen kann (§7).
 *
 * Gezeichnet wird **um** die Seite herum: das Layout der Bühne kennt nur die rechte
 * Seite; Einband, linke Seite und Seitendicke liegen außerhalb ihrer Grenzen.
 */
internal data class BookPageContent(
    val sentence: String,
    /** Die Emojis der richtigen Karte — das Bild, das am Ende im Rahmen steht. */
    val picture: String,
    val pictureCount: Int,
)

/**
 * Das Buch über den Karten. [turningFrom] ist die Seite der vorigen Runde, die gerade
 * umblättert ([turn] 0 → 1); `null`, wenn nichts blättert (Runde 1, Rückwärts-Chevron,
 * fortgesetzte Lektion).
 *
 * Alle `() -> Float` werden erst in der Zeichenphase gelesen: 600 ms Flug und
 * Umblättern sollen nicht 600 ms rekomponieren.
 */
@Composable
internal fun ReadingBook(
    page: BookPageContent,
    turningFrom: BookPageContent?,
    turningFromFilled: Boolean,
    turn: () -> Float,
    still: Boolean,
    ttsAvailable: Boolean,
    landed: () -> Float,
    glow: () -> Float,
    onFrameBounds: (Rect) -> Unit,
    onPictureSp: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            // Die Seite reicht etwas weiter an die Bildschirmränder als der übrige
            // Aufgabenblock — der Falz sitzt nah am linken Rand, damit vom linken
            // Blatt nur ein Streifen bleibt, und der Einband endet knapp vor dem rechten.
            .bleed(start = BleedStartDp.dp, end = BleedEndDp.dp)
            .padding(top = BookTopGapDp.dp + CoverMarginDp.dp, bottom = CoverMarginDp.dp + BookBottomGapDp.dp)
            .drawBehind { drawBookBinding() },
    ) {
        BookPage(
            content = page,
            ttsAvailable = ttsAvailable,
            landed = landed,
            glow = glow,
            onFrameBounds = onFrameBounds,
            onPictureSp = onPictureSp,
            tagged = true,
            modifier = Modifier.fillMaxSize(),
        )
        if (turningFrom != null) {
            // Der Schatten, den das blätternde Blatt auf die neue Seite wirft.
            Canvas(Modifier.fillMaxSize()) {
                if (!still) drawLeafShadow(turn())
            }
            val frontAlpha: () -> Float = {
                val t = turn()
                if (still) 1f - t else if (t < 0.5f) 1f else 0f
            }
            // Vorderseite: die alte Seite mit ihrem Bild klappt um den Falz nach links.
            BookPage(
                content = turningFrom,
                ttsAvailable = ttsAvailable,
                landed = { if (turningFromFilled) 1f else 0f },
                glow = { 0f },
                onFrameBounds = null,
                onPictureSp = {},
                tagged = false,
                modifier = Modifier
                    .fillMaxSize()
                    .leafLayer(turn, still, alpha = frontAlpha)
                    .drawWithContent {
                        drawContent()
                        if (!still) drawLeafShade(turn())
                    },
            )
            if (!still) {
                // Rückseite: leeres Papier, sobald das Blatt über die Senkrechte ist. Sie
                // legt sich über die linke Seite, die fast ganz außerhalb des Bilds liegt.
                Box(
                    Modifier
                        .fillMaxSize()
                        .leafLayer(turn, still = false, alpha = { if (turn() >= 0.5f) 1f else 0f })
                        .drawBehind {
                            drawRect(LeftPaper)
                            drawRect(Color.Black.copy(alpha = 0.16f * sin(turn() * PI.toFloat())))
                        },
                )
            }
        }
    }
}

/** Eine rechte Buchseite: Papier, Falzschatten, Rahmen, Balken oder Satz. */
@Composable
private fun BookPage(
    content: BookPageContent,
    ttsAvailable: Boolean,
    landed: () -> Float,
    glow: () -> Float,
    onFrameBounds: ((Rect) -> Unit)?,
    onPictureSp: (Float) -> Unit,
    tagged: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .drawBehind {
                drawRoundRect(PagePaper, cornerRadius = CornerRadius(4.dp.toPx()))
                // Der Falz: links ein weicher Schatten, der nach 12 % der Seite ausläuft.
                drawRect(
                    Brush.horizontalGradient(
                        0f to PageGutter,
                        1f to PagePaper.copy(alpha = 0f),
                        startX = 0f,
                        endX = size.width * 0.12f,
                    ),
                )
            }
            .padding(start = PagePadStartDp.dp, end = PagePadEndDp.dp, top = PagePadVerticalDp.dp, bottom = PagePadVerticalDp.dp),
        verticalArrangement = Arrangement.spacedBy(FrameToBarsDp.dp),
    ) {
        PictureFrame(
            picture = content.picture,
            pictureCount = content.pictureCount,
            landed = landed,
            onPictureSp = onPictureSp,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .then(
                    if (onFrameBounds != null) {
                        Modifier
                            .onGloballyPositioned { onFrameBounds(it.boundsInRoot()) }
                            .testTag("sentence_picture_frame")
                    } else {
                        Modifier
                    },
                ),
        )
        if (ttsAvailable) {
            WordBars(sentence = content.sentence, glow = glow, modifier = Modifier.fillMaxWidth())
        } else {
            // Ohne deutsches TTS liest ein Erwachsener vor — die eine Situation, in der
            // der Satz als Text auf der Seite steht (§7). Er ersetzt die Balken.
            Text(
                text = content.sentence,
                style = MaterialTheme.typography.headlineSmall,
                color = WarmInk,
                textAlign = TextAlign.Start,
                modifier = Modifier
                    .fillMaxWidth()
                    .then(if (tagged) Modifier.testTag("sentence_picture_fallback_text") else Modifier),
            )
        }
    }
}

/**
 * Der Bildrahmen: leer gestrichelt mit blassem Stern; mit [landed] → 1 wird er
 * durchgezogen `LeafGreen` und das Bild steht darin.
 */
@Composable
private fun PictureFrame(
    picture: String,
    pictureCount: Int,
    landed: () -> Float,
    onPictureSp: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.drawBehind { drawFrame(landed().coerceIn(0f, 1f)) },
        contentAlignment = Alignment.Center,
    ) {
        val fontScale = LocalDensity.current.fontScale
        // Dieselbe Breitenrechnung wie auf der Karte, dazu ein Deckel aus der Höhe:
        // der Rahmen ist breiter als hoch, die Karte nicht.
        val byWidth = SentencePictureCardSizing.emojiSp(
            atomCount = pictureCount,
            contentWidthDp = (maxWidth.value - 2 * FramePictureInsetDp).coerceAtLeast(1f),
            fontScale = fontScale,
        )
        val byHeight = (maxHeight.value - 2 * FramePictureInsetDp) / (EmojiLineEm * maxOf(fontScale, 1f))
        val pictureSp = minOf(byWidth, byHeight).coerceAtLeast(SentencePictureCardSizing.MinEmojiSp)
        SideEffect { onPictureSp(pictureSp) }
        Text(
            text = picture,
            fontFamily = SilboEmoji,
            fontSize = pictureSp.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.graphicsLayer { alpha = landed().coerceIn(0f, 1f) },
        )
    }
}

/** Die Wort-Balken, ein Balken je Wort; sie leuchten in `SunCoral`, solange vorgelesen wird. */
@Composable
private fun WordBars(sentence: String, glow: () -> Float, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier) {
        val layout = remember(sentence, maxWidth) { SentenceBarSizing.layout(sentence, maxWidth.value) }
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(layout.heightDp.dp),
        ) {
            val color = lerp(BarIdle, SunCoral, glow().coerceIn(0f, 1f))
            val h = SentenceBarSizing.BarHeightDp.dp.toPx()
            val pitch = (SentenceBarSizing.BarHeightDp + SentenceBarSizing.RowGapDp).dp.toPx()
            layout.bars.forEach { bar ->
                drawRoundRect(
                    color = color,
                    topLeft = Offset(bar.xDp.dp.toPx(), bar.row * pitch),
                    size = Size(bar.widthDp.dp.toPx(), h),
                    cornerRadius = CornerRadius(h / 2f),
                )
            }
        }
    }
}

/**
 * Der Lesetisch unter den Karten, von der Oberkante des Antwortblocks aus gezeichnet
 * ([ExerciseStage] `answersBackdrop`) und weit über ihn hinaus bis an die Bildränder.
 */
internal fun DrawScope.drawReadingTable() {
    val top = -TableAboveCardsDp.dp.toPx()
    val over = 600.dp.toPx()
    val depth = 1200.dp.toPx()
    drawRect(
        Brush.verticalGradient(0f to TableTop, 1f to TableLow, startY = top, endY = top + 300.dp.toPx()),
        topLeft = Offset(-over, top),
        size = Size(size.width + 2 * over, depth),
    )
    drawRect(TableEdge, topLeft = Offset(-over, top), size = Size(size.width + 2 * over, 3.dp.toPx()))
}

/** Einband, linke Seite und Seitendicke — alles außerhalb der rechten Seite. */
private fun DrawScope.drawBookBinding() {
    val m = CoverMarginDp.dp.toPx()
    val far = 400.dp.toPx()
    drawRoundRect(
        CoverRed,
        topLeft = Offset(-far, -m),
        size = Size(far + size.width + CoverRightDp.dp.toPx(), size.height + 2 * m),
        cornerRadius = CornerRadius(10.dp.toPx()),
    )
    // Die linke Seite, fast ganz außerhalb des Bilds.
    drawRect(LeftPaper, topLeft = Offset(-far, 0f), size = Size(far, size.height))
    // Seitendicke: zwei feine Papierkanten zwischen Seite und Einband.
    val w = 2.dp.toPx()
    drawLine(PageEdgeB, Offset(size.width + 2.dp.toPx(), 8.dp.toPx()), Offset(size.width + 2.dp.toPx(), size.height - 8.dp.toPx()), strokeWidth = w)
    drawLine(PageEdgeA, Offset(size.width + 6.dp.toPx(), 10.dp.toPx()), Offset(size.width + 6.dp.toPx(), size.height - 10.dp.toPx()), strokeWidth = w)
}

private fun DrawScope.drawFrame(landed: Float) {
    val r = CornerRadius(FrameCornerDp.dp.toPx())
    // Leiser grüner Schein um den gefüllten Rahmen — drei Ringe statt Weichzeichner.
    if (landed > 0f) {
        for (i in 1..3) {
            val grow = (3 * i).dp.toPx()
            drawRoundRect(
                LeafGreenLight.copy(alpha = 0.16f * landed / i),
                topLeft = Offset(-grow, -grow),
                size = Size(size.width + 2 * grow, size.height + 2 * grow),
                cornerRadius = CornerRadius(r.x + grow),
                style = Stroke(width = 3.dp.toPx()),
            )
        }
    }
    drawRoundRect(FrameFill, cornerRadius = r)
    if (landed < 1f) {
        val dash = 3.dp.toPx()
        drawRoundRect(
            FrameDash.copy(alpha = 1f - landed),
            topLeft = Offset(dash / 2, dash / 2),
            size = Size(size.width - dash, size.height - dash),
            cornerRadius = CornerRadius(r.x - dash / 2),
            style = Stroke(width = dash, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 8.dp.toPx()))),
        )
        val outer = minOf(18.dp.toPx(), size.minDimension * 0.2f)
        drawPath(roundedStarPath(center, outer, outer * 0.45f), FrameStar.copy(alpha = 1f - landed))
    }
    if (landed > 0f) {
        val s = 4.dp.toPx()
        drawRoundRect(
            LeafGreen.copy(alpha = landed),
            topLeft = Offset(s / 2, s / 2),
            size = Size(size.width - s, size.height - s),
            cornerRadius = CornerRadius(r.x - s / 2),
            style = Stroke(width = s),
        )
    }
}

/**
 * Das Blatt dreht sich um den Falz (linke Kante) nach links: 0 → 90° die Vorderseite,
 * 90 → 180° die Rückseite. Bei „Bewegung reduzieren" dreht nichts, die alte Seite blendet aus.
 */
private fun Modifier.leafLayer(turn: () -> Float, still: Boolean, alpha: () -> Float): Modifier =
    graphicsLayer {
        // Vorder- und Rückseite zeigen je nur ihre Hälfte der Drehung — sonst schiene
        // die (gespiegelte) Rückseite durch die Vorderseite hindurch.
        this.alpha = alpha()
        if (still) return@graphicsLayer
        transformOrigin = TransformOrigin(0f, 0.5f)
        cameraDistance = LeafCameraDistance * density
        rotationY = -180f * turn()
    }

/** Die Vorderseite dunkelt beim Aufstellen ab und bekommt zur Außenkante einen Papierschatten. */
private fun DrawScope.drawLeafShade(t: Float) {
    val lift = sin(t * PI.toFloat()).coerceAtLeast(0f)
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Transparent,
            0.75f to LeafCurl.copy(alpha = 0.35f * lift),
            1f to LeafCurlDeep.copy(alpha = 0.7f * lift),
        ),
    )
    drawRect(Color.Black.copy(alpha = 0.08f * lift))
}

/** Schatten auf der neuen Seite, gleich rechts neben der Außenkante des blätternden Blatts. */
private fun DrawScope.drawLeafShadow(t: Float) {
    if (t >= 0.5f) return
    val angle = t * PI.toFloat()
    val edge = size.width * abs(cos(angle))
    val lift = sin(angle)
    val width = 60.dp.toPx() * lift + 8.dp.toPx()
    drawRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.28f * lift),
            1f to Color.Transparent,
            startX = edge,
            endX = edge + width,
        ),
        topLeft = Offset(edge, 0f),
        size = Size(width, size.height),
    )
}

/**
 * Misst mit zusätzlicher Breite und meldet nach außen die ursprüngliche: der Inhalt ragt
 * um [start] nach links und [end] nach rechts über den Platz hinaus, den das Layout ihm gibt.
 */
private fun Modifier.bleed(start: Dp, end: Dp): Modifier = layout { measurable, constraints ->
    val s = start.roundToPx()
    val e = end.roundToPx()
    if (!constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val placeable = measurable.measure(
        constraints.copy(minWidth = constraints.maxWidth + s + e, maxWidth = constraints.maxWidth + s + e),
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-s, 0) }
}

/** Wie weit die Seite über den Aufgabenblock hinaus nach links bzw. rechts reicht. */
private const val BleedStartDp = 6f
private const val BleedEndDp = 12f

/**
 * Luft zwischen Lautsprecher und Oberkante des Einbands. Ohne sie klebte das Buch am
 * Lautsprecher (Nutzer-Feedback Oktober 2026), mit dem Leuchtring des Nacht-Chromes
 * wirkte der Knopf wie aufs Buch gelegt.
 */
private const val BookTopGapDp = 14f

/** Einband über und unter der Seite. */
private const val CoverMarginDp = 8f

/** Einband rechts neben der Seite (darin die Seitendicke). */
private const val CoverRightDp = 12f

/** Luft unter dem Buch bis zur Tischkante. */
private const val BookBottomGapDp = 22f

/** Die Tischkante liegt so weit über der Oberkante der Kartenreihe. */
private const val TableAboveCardsDp = 16f

// Innenränder der Seite und Luft zwischen Rahmen und Balken. Großzügiger als im ersten
// Wurf (34/26/20/18): mit zwei Zeilen Balken soll die Seite wie eine Buchseite mit Rand
// lesen, nicht wie ein bis an die Kanten gefülltes Formular.
private const val PagePadStartDp = 38f
private const val PagePadEndDp = 30f
private const val PagePadVerticalDp = 26f
private const val FrameToBarsDp = 28f
private const val FrameCornerDp = 16f
private const val FramePictureInsetDp = 14f

/** Zeilenhöhe eines Emoji-Glyphen als Vielfaches der Schriftgröße. */
private const val EmojiLineEm = 1.25f

/**
 * Kameraabstand für das Blatt. Bei 14 wuchs die Außenkante im Aufstellen so weit über
 * das Buch hinaus, dass sie den Lautsprecher verdeckte; 24 hält sie knapp über dem Einband.
 */
private const val LeafCameraDistance = 24f

internal val PagePaper = Color(0xFFFBF6EA)
private val PageGutter = Color(0xFFB8A684)
private val LeftPaper = Color(0xFFEFE6D2)
private val PageEdgeA = Color(0xFFE2D6BD)
private val PageEdgeB = Color(0xFFE8DDC6)
private val CoverRed = Color(0xFF7D3328)
private val FrameFill = Color(0xFFF3EAD6)
private val FrameDash = Color(0xFFC8B796)
private val FrameStar = Color(0xFFD9C9A6)
private val BarIdle = Color(0xFFD8CCB4)
private val LeafCurl = Color(0xFFEFE5CF)
private val LeafCurlDeep = Color(0xFFD6C7A6)
private val TableTop = Color(0xFF5A3F2C)
private val TableLow = Color(0xFF2E2018)
private val TableEdge = Color(0xFF7A5A3E)
