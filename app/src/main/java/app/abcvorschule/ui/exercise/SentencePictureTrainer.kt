package app.abcvorschule.ui.exercise

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/**
 * Trainer 6 — Satz-Versteher. Ein Satz mit schwieriger Grammatik wird
 * vorgelesen; das Kind tippt eine von zwei Bildkarten. Tippen ist die Antwort
 * (wie beim Auditiven Finder) — die Karten tragen keine Wörter, also gibt es
 * kein Vorlese-Echo. Ein Miss liest den Satz erneut (missCueForCurrent).
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
    val haptics = LocalAbcHaptics.current
    val scoredIds = remember(roundKey) { round.correctAtomIds.distinct() }
    val correctOnLeft = remember(roundKey) {
        SentencePictureSides.correctOnLeft(round.promptTts.hashCode())
    }
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "sentence_picture_lock_opacity",
    )

    fun choose(correct: Boolean) {
        if (resolved || solvedCorrect) return
        if (correct) {
            solvedCorrect = true
            haptics.success()
            onResult(true, false, scoredIds)
        } else {
            misses += 1
            haptics.nudge()
            onResult(false, false, scoredIds)
        }
    }

    ExerciseStage(
        modifier = modifier,
        prompt = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
            if (!ttsAvailable) {
                // Ohne deutsches TTS liest ein Erwachsener vor — die eine
                // Situation, in der der Satz als Text erscheinen muss.
                Text(
                    text = round.promptTts,
                    style = MaterialTheme.typography.headlineSmall,
                    color = WarmInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("sentence_picture_fallback_text"),
                )
            }
        },
        answers = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sentence_picture_cards"),
            ) {
                val leftIsCorrect = correctOnLeft
                PictureCard(
                    atomIds = if (leftIsCorrect) round.correctAtomIds else round.wrongAtomIds,
                    pack = pack,
                    highlight = (solvedCorrect || resolved) && leftIsCorrect,
                    enabled = !interactionLocked && !solvedCorrect && !resolved,
                    opacity = interactionOpacity,
                    onTap = { choose(leftIsCorrect) },
                    testTag = if (leftIsCorrect) "sentence_picture_card_correct" else "sentence_picture_card_wrong",
                    modifier = Modifier.weight(1f),
                )
                PictureCard(
                    atomIds = if (leftIsCorrect) round.wrongAtomIds else round.correctAtomIds,
                    pack = pack,
                    highlight = (solvedCorrect || resolved) && !leftIsCorrect,
                    enabled = !interactionLocked && !solvedCorrect && !resolved,
                    opacity = interactionOpacity,
                    onTap = { choose(!leftIsCorrect) },
                    testTag = if (leftIsCorrect) "sentence_picture_card_wrong" else "sentence_picture_card_correct",
                    modifier = Modifier.weight(1f),
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
 * Bestätigungs-Grün der gewählten Karte — dieselbe dunklere LeafGreen-Variante
 * wie SentenceOrderTrainer.PegBorderGreen (voll-opakes LeafGreen erreicht auf
 * CreamElevated nur 2.87:1).
 */
private val CardBorderGreen = Color(0xFF3A7A44)

/** Innenabstand der Karte je Seite; zugleich der Abzug für die Emoji-Breitenrechnung. */
private const val CardPaddingHorizontalDp = 10f

@Composable
private fun PictureCard(
    atomIds: List<String>,
    pack: ContentPack,
    highlight: Boolean,
    enabled: Boolean,
    opacity: Float,
    onTap: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val emojis = atomIds.joinToString("") { pack.atoms[it]?.emoji.orEmpty() }
    val fontScale = LocalDensity.current.fontScale
    // Die Emoji-Größe hängt an der real gemessenen Kartenbreite, nicht an einer
    // festen Staffelung: sonst überläuft die Reihe auf schmalen Geräten (siehe
    // SentencePictureCardSizing). BoxWithConstraints außen, Padding innen, damit
    // maxWidth die volle Kartenbreite ist und der Abzug hier sichtbar bleibt.
    BoxWithConstraints(modifier = modifier) {
        val contentWidthDp = (maxWidth.value - 2 * CardPaddingHorizontalDp).coerceAtLeast(1f)
        val emojiSp = SentencePictureCardSizing.emojiSp(atomIds.size, contentWidthDp, fontScale)
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AbcDimens.kidTouch * 2)
                .alpha(opacity)
                .background(color = CreamElevated, shape = RoundedCornerShape(22.dp))
                .border(
                    width = if (highlight) 4.dp else 3.dp,
                    color = if (highlight) CardBorderGreen else WarmMuted.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(22.dp),
                )
                .clickable(enabled = enabled, onClick = onTap)
                .padding(horizontal = CardPaddingHorizontalDp.dp, vertical = 18.dp)
                .testTag(testTag),
        ) {
            Text(
                text = emojis,
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
