package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SentencePictureRound
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SilboEmoji
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.world.IslandCream
import app.abcvorschule.ui.world.lightIsland

/**
 * Trainer 6 — Satz-Versteher. Ein Satz mit schwieriger Grammatik wird
 * vorgelesen; das Kind tippt eine von zwei Bildkarten. Tippen ist die Antwort —
 * die Karten tragen keine Wörter, also gibt es kein Vorlese-Echo. Ein Miss liest den Satz erneut (missCueForCurrent).
 */
@Composable
fun SentencePictureTrainer(
    round: SentencePictureRound,
    roundIndex: Int,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.promptTts}"
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    var solvedCorrect by remember(roundKey) { mutableStateOf(false) }
    // Welche Karte zuletzt falsch getippt wurde und wie oft überhaupt schon
    // falsch getippt wurde. Der Zähler ist der Auslöser der Schüttel-Animation:
    // ein Bool wäre beim zweiten Fehltipp auf dieselbe Karte schon true und
    // würde keine neue Runde starten.
    var wrongTick by remember(roundKey) { mutableIntStateOf(0) }
    var wrongOnLeft by remember(roundKey) { mutableStateOf(false) }
    val haptics = LocalAbcHaptics.current
    val scoredIds = remember(roundKey) { round.correctAtomIds.distinct() }
    val correctOnLeft = remember(roundKey) {
        SentencePictureSides.correctOnLeft(round.promptTts.hashCode())
    }
    // Ruhen statt dimmen (PromptRest): kaum gedämpft, die Ansage-Sperre hält die Taps.
    val interactionOpacity = rememberRestOpacity()

    fun choose(correct: Boolean, tappedLeft: Boolean) {
        if (resolved || solvedCorrect) return
        if (correct) {
            solvedCorrect = true
            haptics.success()
            onResult(true, false, scoredIds)
        } else {
            misses += 1
            wrongOnLeft = tappedLeft
            wrongTick += 1
            haptics.nudge()
            onResult(false, false, scoredIds)
        }
    }

    ExerciseStage(
        modifier = modifier,
        // Der Aufgabenblock trägt hier nur den Speaker — am unteren Rand
        // verdeckt die tippende Hand sonst genau die Bildkarten, die die
        // ganze Aufgabe sind.
        answerAnchor = AnswerAnchor.BelowCenter,
        promptChrome = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
        },
        prompt = {
            if (!ttsAvailable) {
                // Ohne deutsches TTS liest ein Erwachsener vor — die eine
                // Situation, in der der Satz als Text erscheinen muss.
                Text(
                    text = round.promptTts,
                    style = MaterialTheme.typography.headlineSmall,
                    color = WarmInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.lightIsland().testTag("sentence_picture_fallback_text"),
                )
            }
        },
        answers = {
            Row(
                // 8dp statt 14dp: die Lücke ist reines Breitenbudget, das den
                // Emojis fehlt. Zwei Karten mit deutlichem Rahmen brauchen
                // keinen breiten Graben, um auseinandergehalten zu werden.
                horizontalArrangement = Arrangement.spacedBy(CardGapDp.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    // Die Bühnenbretter des Puppentheaters liegen genau unter den Karten
                    // und reichen über die Bühne hinaus bis an den Rand.
                    .drawBehind { drawStageBoards() }
                    .testTag("sentence_picture_cards"),
            ) {
                val leftIsCorrect = correctOnLeft
                // Ein Fortschritt für beide Karten: die richtige wächst, die falsche
                // verschwindet. Bei fast null Gewicht schrumpft der Slot der
                // Verliererkarte mit; die 8dp Lücke bleibt, die Gewinnerkarte landet
                // also 4dp neben der optischen Mitte — unter der Wahrnehmungsschwelle
                // und billiger als eine zusätzlich animierte Arrangement-Lücke.
                //
                // key(roundKey) statt nur remember: dieselbe Runden-Reset-Disziplin wie
                // beim remember(roundKey) der Zustände oben. Ohne den Key überlebt das
                // Animatable den Rundenwechsel, der Zielwert springt beim neuen
                // roundIndex von 1f auf 0f zurück und animiert sichtbar ab — die neue
                // Runde würde mit vergrößerter Antwortkarte eröffnen, bevor der Satz
                // überhaupt vorgelesen wurde.
                val celebrateProgress by key(roundKey) {
                    animateFloatAsState(
                        targetValue = if (solvedCorrect) 1f else 0f,
                        animationSpec = tween(durationMillis = AbcMotion.StandardMs, easing = AbcMotion.Enter),
                        label = "sentence_picture_celebrate",
                    )
                }
                val correctWeight = 1f + 2f * celebrateProgress
                val wrongWeight = (1f - celebrateProgress).coerceAtLeast(0.001f)
                PictureCard(
                    atomIds = if (leftIsCorrect) round.correctAtomIds else round.wrongAtomIds,
                    pack = pack,
                    highlight = (solvedCorrect || resolved) && leftIsCorrect,
                    celebrateProgress = if (leftIsCorrect) celebrateProgress else 0f,
                    enabled = !interactionLocked && !solvedCorrect && !resolved,
                    opacity = interactionOpacity *
                        if (leftIsCorrect) 1f else (1f - celebrateProgress),
                    shakeTick = if (wrongOnLeft) wrongTick else 0,
                    onTap = { choose(leftIsCorrect, tappedLeft = true) },
                    testTag = if (leftIsCorrect) "sentence_picture_card_correct" else "sentence_picture_card_wrong",
                    modifier = Modifier.weight(if (leftIsCorrect) correctWeight else wrongWeight),
                )
                PictureCard(
                    atomIds = if (leftIsCorrect) round.wrongAtomIds else round.correctAtomIds,
                    pack = pack,
                    highlight = (solvedCorrect || resolved) && !leftIsCorrect,
                    celebrateProgress = if (leftIsCorrect) 0f else celebrateProgress,
                    enabled = !interactionLocked && !solvedCorrect && !resolved,
                    opacity = interactionOpacity *
                        if (leftIsCorrect) (1f - celebrateProgress) else 1f,
                    shakeTick = if (!wrongOnLeft) wrongTick else 0,
                    onTap = { choose(!leftIsCorrect, tappedLeft = false) },
                    testTag = if (leftIsCorrect) "sentence_picture_card_wrong" else "sentence_picture_card_correct",
                    modifier = Modifier.weight(if (leftIsCorrect) wrongWeight else correctWeight),
                )
            }
            if (misses >= 2 && !resolved && !solvedCorrect) {
                AbcResolveButton(
                    onClick = {
                        resolved = true
                        onResult(false, true, scoredIds)
                    },
                )
            }
        },
    )
}

/**
 * Innenabstand der Karte je Seite; zugleich der Abzug für die Emoji-
 * Breitenrechnung. 4dp statt vormals 10dp: ohne Füllfläche muss der Rahmen keine
 * Fläche mehr einfassen, und jedes eingesparte dp landet direkt im Breitendeckel
 * der Emoji-Reihe — bei drei Emojis ist die Breite die bindende Grenze.
 */
private const val CardPaddingHorizontalDp = 4f

/** Abstand der beiden Karten in der Reihe, ebenfalls Breitenbudget der Emojis. */
private const val CardGapDp = 8f

/** Holzrahmen der Bildkarten im Puppentheater. */
private val FrameWood = Color(0xFF8A6440)
private val BoardTop = Color(0xFF6B4A30)
private val BoardBottom = Color(0xFF3B2819)

/**
 * Die Bühnenbretter: ein Holzstreifen am unteren Rand der Kartenreihe, 40dp über die
 * Reihe hinaus. Liegt hinter den Karten; die Karten stehen darauf.
 */
private fun DrawScope.drawStageBoards() {
    val over = 40.dp.toPx()
    val top = size.height - 10.dp.toPx()
    val h = 30.dp.toPx()
    drawRoundRect(
        brush = Brush.verticalGradient(0f to BoardTop, 1f to BoardBottom, startY = top, endY = top + h),
        topLeft = Offset(-over, top),
        size = Size(size.width + 2 * over, h),
        cornerRadius = CornerRadius(4.dp.toPx()),
    )
    val seam = 1.dp.toPx()
    var x = -over + 46.dp.toPx()
    while (x < size.width + over) {
        drawLine(BoardBottom, Offset(x, top + 4.dp.toPx()), Offset(x, top + h - 4.dp.toPx()), strokeWidth = seam)
        x += 58.dp.toPx()
    }
}

/** Zuwachs der Emoji-Basisgröße, wenn die richtige Karte in die Mitte wächst. */
private const val CelebrateBaseScaleGain = 0.6f

@Composable
private fun PictureCard(
    atomIds: List<String>,
    pack: ContentPack,
    highlight: Boolean,
    celebrateProgress: Float,
    enabled: Boolean,
    opacity: Float,
    shakeTick: Int,
    onTap: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val emojis = atomIds.joinToString("") { pack.atoms[it]?.emoji.orEmpty() }
    val fontScale = LocalDensity.current.fontScale
    // Ein Animatable statt animateFloatAsState: die Schüttelrunde muss bei jedem
    // neuen Tick von vorn beginnen, auch wenn die vorige noch läuft.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeTick) {
        // Erst zurücksetzen, dann der Frühausstieg — nicht zu „wenn 0, einfach
        // return" vereinfachen: beim Rundenwechsel fällt shakeTick von 1 auf 0, der
        // Effekt startet neu und bricht dabei ein noch laufendes animateTo ab, dessen
        // abschließendes snapTo unten also nie mehr läuft. Die Chevrons wechseln die
        // Runde ohne Verzögerung (SessionViewModel.goNextRound) und sind nach einem
        // Fehltipp aktiv — falsche Karte tippen, innerhalb der 420ms den Chevron: die
        // nächste Runde eröffnete sonst mit einer um bis zu 12dp verschobenen Karte,
        // und die bliebe für alle folgenden Runden schief stehen.
        shake.snapTo(0f)
        if (shakeTick == 0) return@LaunchedEffect
        shake.animateTo(1f, tween(durationMillis = SentencePictureCardShake.DurationMs))
        shake.snapTo(0f)
    }
    // Die Emoji-Größe hängt an der real gemessenen Kartenbreite, nicht an einer
    // festen Staffelung: sonst überläuft die Reihe auf schmalen Geräten (siehe
    // SentencePictureCardSizing). BoxWithConstraints außen, Padding innen, damit
    // maxWidth die volle Kartenbreite ist und der Abzug hier sichtbar bleibt.
    BoxWithConstraints(modifier = modifier) {
        val contentWidthDp = (maxWidth.value - 2 * CardPaddingHorizontalDp).coerceAtLeast(1f)
        // baseScale statt graphicsLayer-Skalierung: eine hochgezogene Bitmap wäre
        // bei einem Glyphen, der die halbe Bühne füllt und dort mehrere hundert
        // Millisekunden steht, sichtbar weich. Über das Row-Gewicht wird die Karte
        // echt breiter gemessen, und dieselbe Funktion rechnet die Emoji-Größe für
        // die neue Breite — der Glyph wird in Endgröße gerastert.
        //
        // Die Verbreiterung allein reicht dafür nicht: auf der breiten Karte
        // bindet weiter die Basisgröße, nicht der Deckel. Erst baseScale hebt sie.
        val emojiSp = SentencePictureCardSizing.emojiSp(
            atomCount = atomIds.size,
            contentWidthDp = contentWidthDp,
            fontScale = fontScale,
            baseScale = 1f + CelebrateBaseScaleGain * celebrateProgress,
        )
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AbcDimens.kidTouch * 2)
                // graphicsLayer statt offset: eine reine Zeichenoperation, die
                // kein Neu-Layout der Reihe auslöst und die Nachbarkarte
                // deshalb nicht mitverschiebt.
                .graphicsLayer {
                    translationX = SentencePictureCardShake.offsetDp(shake.value).dp.toPx()
                }
                .alpha(opacity)
                // Im Puppentheater ein gerahmtes Bild: helle Bildfläche (Licht-Insel,
                // §10), darum ein Holzrahmen. Auf der dunklen Bühne trägt der Rahmen
                // die Kartengrenze, die Fläche hält die Emojis hell.
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(22.dp))
                .background(IslandCream, RoundedCornerShape(22.dp))
                // Der Rahmen ist 5dp stark, damit das Holz als Rahmen liest; LeafGreen
                // markiert weiter die richtige Karte (§10), auf IslandCream mit gut 3:1.
                .border(
                    width = 5.dp,
                    color = if (highlight) LeafGreen else FrameWood,
                    shape = RoundedCornerShape(22.dp),
                )
                .clickable(enabled = enabled, onClick = onTap)
                .padding(horizontal = CardPaddingHorizontalDp.dp, vertical = 18.dp)
                .testTag(testTag),
        ) {
            Text(
                text = emojis,
                fontFamily = SilboEmoji,
                fontSize = emojiSp.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                // Zweiter Riegel gegen den Überlauf: sollte die Breitenschätzung doch
                // einmal danebenliegen, wird die Reihe angeschnitten statt umgebrochen.
                // Mit maxLines = 1 fällt eine umgebrochene zweite Zeile komplett weg —
                // das letzte Emoji wäre unsichtbar, und die beiden Karten sähen bei
                // 16 der 72 Runden identisch aus. Angeschnitten ist harmloser.
                softWrap = false,
            )
        }
    }
}
