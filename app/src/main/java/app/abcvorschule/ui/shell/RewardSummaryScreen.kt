package app.abcvorschule.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.R
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Lesson
import app.abcvorschule.content.LessonFinale
import app.abcvorschule.ui.components.AbcContinueButton
import app.abcvorschule.ui.components.AbcSpeakerButton
import app.abcvorschule.ui.path.LanternLoops
import app.abcvorschule.ui.path.PathBackground
import app.abcvorschule.ui.rewards.AbcSfx
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.Sfx
import app.abcvorschule.ui.rewards.drawGlint
import app.abcvorschule.ui.rewards.drawGlowStar
import app.abcvorschule.ui.rewards.playStarBlip
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.SilboEmoji
import app.abcvorschule.ui.theme.SilboUi
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.world.LocalChromeColors
import app.abcvorschule.ui.world.NightChrome
import app.abcvorschule.ui.world.lightIsland
import app.abcvorschule.ui.world.rememberReduceMotion
import app.abcvorschule.ui.world.rememberWorldSeconds
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay

// Zusätzliches horizontales Polster für den Satz, oben auf das 24dp der Spalte drauf
// (macht 44dp insgesamt pro Seite): 20dp liegt in der von der Produktseite gewünschten
// Spanne von 16–24dp — die Mitte der Spanne, weil der Satz spürbar mehr Luft braucht,
// ohne zu einem schmalen Textband zu werden. Reine Optik, keine Font-Scale-Entscheidung,
// bleibt darum hier statt in FinaleLayout.
private val SentenceExtraHorizontalPadding = 20.dp

/**
 * Mindest-Trefferfläche eines antippbaren Finale-Bildes. Vorher war die Fläche die
 * Glyphenbox selbst — effektiv 64dp bei zwei oder drei Bildern, 52dp bei vier
 * ([FinaleLayout.pictureSizeSp] deckelt die gerenderte Größe, sie ist also auf dem
 * Testgerät bei font_scale 1.3 dieselbe wie bei 1.0). Beides liegt unter
 * [AbcDimens.kidTouch], und getippt wird hier vom Kind (§7: antippbare Items werden
 * vorgelesen).
 *
 * Die vollen 80dp bekommen nur zwei oder drei Bilder. Vier bräuchten
 * 4×80 + 3×[FinaleLayout.PictureRowGapDp] = 368dp, die schmalste unterstützte
 * Inhaltsbreite ist aber 272dp (320dp-Gerät minus 24dp Spaltenpolster je Seite,
 * festgehalten in FinaleLayoutTest). Für vier Bilder ist der Boden deshalb genau das,
 * was dort noch in *eine* Reihe passt — (272 − 3×16) / 4 = 56dp —, und das ist der
 * Hitbox-Boden, auf den §9 auch die Satz-Pegs stellt, wenn kidTouch physikalisch nicht
 * passt. Drei Bilder gehen mit 3×80 + 2×16 = 272dp genau auf.
 */
private fun pictureTouchSize(count: Int): Dp =
    if (count >= 4) 56.dp else AbcDimens.kidTouch

/**
 * Der End-Screen einer Lektion (PRODUCT_PRINCIPLES §10, „Lektions-Ende", Variante A).
 *
 * Er spielt am Abendhimmel des Pfads ([PathBackground]) — der Weg zurück zum Pfad ist
 * dann ein Schritt, kein Wechsel. Die Sterne der Lektion fliegen von oben an den Himmel
 * und setzen sich zum **Sternbild eines Buchstabens** zusammen, der im Finale-Satz steht
 * und in der Lektion geübt wurde ([FinaleConstellation]); Linien verbinden sie, das
 * Sternbild leuchtet einmal auf. Darunter steht der Finale-Satz mit seinen Bildern auf
 * einer hellen Karte (Licht-Insel), die Bilder hüpfen einmal, während er gesprochen wird.
 *
 * Kein Konfetti mehr: die fallenden Quadrate in vier Rollenfarben verwässerten Grün
 * (richtig) und Gold (Belohnung). Der Satztext richtet sich an den mitlesenden
 * Erwachsenen — die einzige bewusste Ausnahme von „das Kind kann nicht lesen" (§12).
 *
 * Header, mittlerer Block und Weiter-Button liegen in einer Spalte; Himmel und Karte
 * teilen sich den Platz über `weight`, der Weiter-Button rutscht also nie vom Bildschirm.
 * Zeigt bewusst **keine** Punktezahl: die steht im Übungs-Chrome und auf dem Pfad.
 */
@Composable
fun RewardSummaryScreen(
    finale: LessonFinale?,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    /** Die geschaffte Lektion: ihre geübten Buchstaben bestimmen das Sternbild. */
    lesson: Lesson? = null,
) {
    val haptics = LocalAbcHaptics.current
    LaunchedEffect(Unit) {
        haptics.celebrate()
        AbcSfx.play(Sfx.Fanfare)
    }
    val fontScale = LocalDensity.current.fontScale
    val constellation = remember(finale?.id, lesson?.id) {
        FinaleConstellation.letterFor(lesson, finale, pack)?.let(FinaleConstellation::of)
    }

    // Den Satz einmal beim Erscheinen sprechen, wie die Prompt-Ansage in der Übung.
    LaunchedEffect(finale?.id, ttsAvailable) {
        val text = finale?.tts ?: return@LaunchedEffect
        // Erst die Fanfare, dann der Satz: ihre drei Anlauftöne liegen sonst genau
        // über den ersten Wörtern. Der gehaltene Schlusston klingt leise darunter aus.
        delay(FanfareLeadMs)
        if (ttsAvailable) onSpeak(text)
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Der Himmel des Pfads, randlos hinter allem — ohne Scrollen, die Hügel stehen.
        PathBackground(scrollOffset = { 0 }, loops = remember { LanternLoops() }, sunAndLanterns = false)
        CompositionLocalProvider(LocalChromeColors provides NightChrome) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(bottom = AbcDimens.screenBottomExtra)
                    .padding(horizontal = 24.dp)
                    .padding(top = 20.dp, bottom = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.reward_title),
                    // Jubel für den Erwachsenen daneben, kein Lerninhalt: klein und oben.
                    // Das Sternbild spricht für das Kind.
                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = SilboUi),
                    color = Cream.copy(alpha = 0.85f),
                    fontSize = (FinaleLayout.headerSizeSp(fontScale) * TitleShrink).sp,
                    lineHeight = (FinaleLayout.headerLineHeightSp(fontScale) * TitleShrink).sp,
                    maxLines = 1,
                )
                ConstellationSky(
                    constellation = constellation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.85f)
                        .testTag("finale_constellation"),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    if (finale != null) {
                        FinaleBody(
                            finale = finale,
                            pack = pack,
                            ttsAvailable = ttsAvailable,
                            onSpeak = onSpeak,
                            fontScale = fontScale,
                        )
                    }
                }
                // Unten nebeneinander: Satz noch einmal hören, und der große grüne Pfeil.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    if (finale != null) {
                        AbcSpeakerButton(
                            enabled = ttsAvailable,
                            speaking = speaking,
                            onClick = { onSpeak(finale.tts) },
                        )
                    }
                    AbcContinueButton(onClick = onContinue)
                }
            }
        }
    }
}

/**
 * Das Sternbild: jeder Stern fliegt von oben in einem Bogen an seinen Platz und funkelt
 * beim Ankommen, dann zeichnen sich die Linien in Schreibrichtung nach, und das ganze Bild
 * leuchtet einmal auf. Ohne passenden Buchstaben ([constellation] null) bleibt der Himmel
 * leer — der Satz trägt den Moment dann allein. Bei „Bewegung reduzieren" steht das fertige
 * Sternbild sofort da.
 */
@Composable
private fun ConstellationSky(constellation: FinaleConstellation.Constellation?, modifier: Modifier) {
    val still = rememberReduceMotion()
    val stars = constellation?.stars.orEmpty()
    val flyEnd = SkyStartS + StarGapS * stars.size + StarFlyS
    val linesEnd = flyEnd + LinesS
    val total = linesEnd + FlashS
    val clock = remember(constellation) { Animatable(0f) }
    LaunchedEffect(constellation, still) {
        if (constellation == null) return@LaunchedEffect
        if (still) clock.snapTo(total) else clock.animateTo(total, tween((total * 1000).toInt(), easing = LinearEasing))
    }
    // Nach dem Aufbau lebt das Sternbild weiter: jeder Stern funkelt und dreht sich leicht,
    // und ein angetippter Stern springt kurz größer und schaukelt aus. Uhr und Tipps werden
    // nur im Zeichnen gelesen.
    val idle = rememberWorldSeconds(still)
    val tapped = remember(constellation) { mutableStateMapOf<Int, Float>() }
    val touchRadius = with(LocalDensity.current) { StarTouchRadius.toPx() }
    Canvas(
        modifier.pointerInput(constellation, still) {
            val c = constellation ?: return@pointerInput
            if (still) return@pointerInput
            // Tippen oder Nachzeichnen: jeder Stern, über den der Finger kommt, springt —
            // beim Nachzeichnen mit aufsteigenden Tönen wie die Sterne im Spurensucher.
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                var step = 0
                fun touch(at: Offset) {
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val hit = c.stars.indices
                        .minByOrNull { (starAt(c, it, w, h) - at).getDistance() }
                        ?.takeIf { (starAt(c, it, w, h) - at).getDistance() <= touchRadius }
                        ?: return
                    val last = tapped[hit]
                    if (last != null && idle.value - last < RetouchS) return
                    tapped[hit] = idle.value
                    playStarBlip(step++)
                }
                touch(down.position)
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    touch(change.position)
                    change.consume()
                }
            }
        },
    ) {
        val c = constellation ?: return@Canvas
        val t = clock.value
        val now = idle.value
        fun at(i: Int) = starAt(c, i, size.width, size.height)
        val from = Offset(size.width / 2f, -24.dp.toPx())
        // Linien: in der Reihenfolge, in der der Buchstabe geschrieben wird.
        val drawn = ((t - flyEnd) / LinesS).coerceIn(0f, 1f) * c.lines.size
        c.lines.forEachIndexed { index, (a, b) ->
            val part = (drawn - index).coerceIn(0f, 1f)
            if (part <= 0f) return@forEachIndexed
            val start = at(a)
            val end = start + (at(b) - start) * part
            drawLine(LineGlow, start, end, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round, alpha = 0.35f)
            drawLine(LineLight, start, end, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round, alpha = 0.8f)
        }
        val flash = if (t > linesEnd) kotlin.math.exp(-(t - linesEnd) * 2.2f) else 0f
        c.stars.indices.forEach { i ->
            val start = SkyStartS + i * StarGapS
            val f = ((t - start) / StarFlyS).coerceIn(0f, 1f)
            if (f <= 0f) return@forEach
            val e = f * f * (3f - 2f * f)
            val to = at(i)
            val ctrl = Offset((from.x + to.x) / 2f + (if (i % 2 == 0) -40 else 40).dp.toPx(), from.y + 60.dp.toPx())
            val pos = from * ((1 - e) * (1 - e)) + ctrl * (2 * (1 - e) * e) + to * (e * e)
            val arrived = if (f >= 1f) kotlin.math.exp(-(t - start - StarFlyS) * 4f) else 0f
            // Angekommen: leises Funkeln (Größe ±8 %) und eine leichte Drehung (±10°),
            // jeder Stern mit eigenem Takt; ab und zu blitzt ein Glanzlicht auf.
            val settled = f >= 1f
            val period = 2.4f + (i % 3) * 0.5f
            val twinkle = if (settled) 0.08f * sin(now / period * 2f * PI.toFloat() + i) else 0f
            val sway = if (settled) 10f * sin(now / 5f * 2f * PI.toFloat() + i * 1.3f) else 0f
            val sparkle = if (settled) sin(now / 7f * 2f * PI.toFloat() + i * 2.1f).coerceAtLeast(0f).let { it * it * it * it * it * it * it * it } else 0f
            // Angetippt: größer, dann federnd zurück, mit einem Schaukeln.
            val tapAge = tapped[i]?.let { now - it }?.takeIf { it in 0f..TapBounceS }
            val bounce = tapAge?.let { a -> 0.55f * kotlin.math.exp(-a * 3.2f) * kotlin.math.cos(a * 9f) } ?: 0f
            val tapSpin = tapAge?.let { a -> 30f * kotlin.math.exp(-a * 3f) * sin(a * 12f) } ?: 0f
            val radius = (8f + 4f * arrived + 3f * flash).dp.toPx() * (1f + twinkle + bounce)
            drawGlowStar(pos, radius, rotationDeg = e * 360f + sway + tapSpin, halo = 0.8f + bounce)
            if (sparkle > 0.05f) drawGlint(pos + Offset(radius * 0.7f, -radius * 0.7f), 8.dp.toPx() * sparkle, 0.8f * sparkle)
            if (tapAge != null && tapAge < 0.6f) drawGlint(pos, 18.dp.toPx() * (1f - tapAge / 0.6f), 1f - tapAge / 0.6f)
            if (arrived > 0.05f) drawGlint(pos, 12.dp.toPx() * arrived, arrived)
        }
    }
}

@Composable
private fun FinaleBody(
    finale: LessonFinale,
    pack: ContentPack,
    ttsAvailable: Boolean,
    onSpeak: (String) -> Unit,
    fontScale: Float,
) {
    val pictures = FinaleLayout.picturesOf(pack, finale)
    val pictureSp = FinaleLayout.pictureSizeSp(pictures.size, fontScale).sp
    val pictureTouch = pictureTouchSize(pictures.size)
    val sentenceSp = FinaleLayout.sentenceSizeSp(fontScale)
    val sentenceLineHeightSp = FinaleLayout.sentenceLineHeightSp(fontScale)

    // Bilder und Satz auf einer hellen Karte (Licht-Insel, §10): Lerninhalt nur auf Licht.
    // Etwas eingerückt, damit die Karte nicht bis an den Rand reicht.
    Column(
        modifier = Modifier
            .padding(horizontal = CardInset)
            .lightIsland(padH = 16.dp, padV = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(
                    FinaleLayout.PictureRowGapDp.dp,
                    Alignment.CenterHorizontally,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pictures.forEachIndexed { index, picture ->
                    var shown by remember(finale.id, picture.atomId) { mutableStateOf(false) }
                    val hop = remember(finale.id, picture.atomId) { Animatable(0f) }
                    LaunchedEffect(finale.id, picture.atomId) {
                        delay(FinaleLayout.revealDelayMillis(index))
                        shown = true
                        // Einmal hüpfen, während der Satz gesprochen wird, nacheinander.
                        delay(FanfareLeadMs + HopDelayMs + index * HopStaggerMs)
                        hop.animateTo(1f, tween(AbcMotion.ShortMs, easing = AbcMotion.Exit))
                        hop.animateTo(0f, AbcMotion.Bouncy.spec())
                    }
                    AnimatedVisibility(
                        visible = shown,
                        enter = fadeIn(tween(AbcMotion.ShortMs)) + scaleIn(tween(AbcMotion.ShortMs), initialScale = 0.6f),
                    ) {
                        // Die Trefferfläche ist die Box, nicht das Glyph: ein Emoji
                        // ist kleiner als der Finger, der es trifft. Siehe
                        // [pictureTouchSize].
                        Box(
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = pictureTouch,
                                    minHeight = pictureTouch,
                                )
                                .graphicsLayer { translationY = -HopHeight.toPx() * hop.value }
                                // Tippen liest das Wort vor (Prinzip 7).
                                .clickable(enabled = ttsAvailable) {
                                    onSpeak(picture.lemma)
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = picture.emoji, fontSize = pictureSp, fontFamily = SilboEmoji)
                        }
                    }
                }
            }

            Text(
                text = finale.text,
                style = MaterialTheme.typography.headlineSmall,
                color = WarmInk,
                textAlign = TextAlign.Center,
                // Wrapping ist das erwartete Verhalten, kein Fehlerfall: die Schriftgröße
                // bleibt unverändert (siehe FinaleLayout.sentenceSizeSp), der Satz darf
                // dafür über mehr Zeilen laufen. 4 Zeilen sind eine sichere Obergrenze
                // für 4–7 Wörter; TextOverflow.Ellipsis bleibt als letzte Absicherung.
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                fontSize = sentenceSp.sp,
                lineHeight = sentenceLineHeightSp.sp,
                modifier = Modifier.padding(horizontal = SentenceExtraHorizontalPadding),
            )
    }
}

/** Wie lange die Fanfare allein spielt, bevor der Finale-Satz beginnt. */
private const val FanfareLeadMs = 700L

/** Der Jubel-Titel steht nur noch klein oben (für den Erwachsenen). */
private const val TitleShrink = 0.85f

private const val SkyStartS = 0.4f
private const val StarGapS = 0.22f
private const val StarFlyS = 0.7f
private const val LinesS = 1.1f
private const val FlashS = 1.4f
private const val HopDelayMs = 400L
private const val HopStaggerMs = 260L
private val HopHeight = 12.dp
private val StarTouchRadius = 32.dp
private const val TapBounceS = 1.4f

/** So lange springt derselbe Stern nicht noch einmal, wenn der Finger auf ihm bleibt. */
private const val RetouchS = 0.6f

/** Wo Stern [i] im Himmel der Größe [w] × [h] steht: das Einheitsquadrat des Buchstabens, mittig. */
private fun starAt(c: FinaleConstellation.Constellation, i: Int, w: Float, h: Float): Offset {
    val side = minOf(w * 0.62f, h * 0.86f)
    val origin = Offset((w - side) / 2f, (h - side) / 2f)
    return origin + Offset(c.stars[i].first * side, c.stars[i].second * side)
}
private val CardInset = 14.dp
private val LineLight = Color(0xFFFFECBE)
private val LineGlow = Color(0xFFFFD678)
