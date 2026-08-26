package app.abcvorschule.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.WordBlock
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.exercise.drag.DragCard
import app.abcvorschule.ui.exercise.drag.DropZone
import app.abcvorschule.ui.exercise.drag.rememberDragFieldState
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

object SentenceOrderTray {
    /** A sentence can need more cards than a word, but the tray stays scannable. */
    const val MaxTrayTiles = 6

    fun cards(
        words: List<String>,
        atomIds: List<String>,
        distractors: List<WordBlock>,
        placedDisplays: List<String>,
        seed: Int,
    ): List<WordBlock> {
        val solution = words.mapIndexed { index, word ->
            WordBlock(atomId = atomIds.getOrElse(index) { word }, display = word)
        }
        val capped = (solution + distractors).take(MaxTrayTiles)
        val arranged = TrayOrder.arrange(capped, seed) { it.display }
        val remaining = arranged.toMutableList()
        placedDisplays.forEach { display ->
            val hit = remaining.indexOfFirst { it.display == display }
            if (hit >= 0) remaining.removeAt(hit)
        }
        return if (remaining.none { card -> words.any { it == card.display } }) {
            emptyList()
        } else {
            remaining
        }
    }

    fun pegKey(index: Int): String = "peg-$index"

    fun pegIndex(key: String): Int? =
        if (key.startsWith("peg-")) key.removePrefix("peg-").toIntOrNull() else null
}

/**
 * Trainer 5 — Satz-Architekt. Word cards are hung on a clothesline in reading
 * order. A one-word round is the same mechanic with a single peg, which is how the
 * curriculum introduces word-to-picture matching in the first lessons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceOrderTrainer(
    round: SentenceOrderRound,
    roundIndex: Int,
    words: List<String>,
    atomIds: List<String>,
    illustrationEmoji: String?,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.sentenceId}"
    val field = rememberDragFieldState(roundKey)
    val placed = remember(roundKey) { mutableStateMapOf<Int, String>() }
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    var completed by remember(roundKey) { mutableStateOf(false) }
    val scoredIds = remember(roundKey) { atomIds.distinct() }
    val cards = SentenceOrderTray.cards(
        words,
        atomIds,
        round.distractors,
        placed.values.toList(),
        seed = round.sentenceId.hashCode(),
    )
    val haptics = LocalAbcHaptics.current
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "sentence_order_lock_opacity",
    )

    fun place(index: Int, card: WordBlock) {
        if (resolved || placed[index] != null) return
        field.select(null)
        if (OrderedPlacement.isCorrectPlacement(index, card.display, words)) {
            placed[index] = card.display
            haptics.tick()
            onSpeak(card.display)
            if (OrderedPlacement.isSolved(placed.toMap(), words)) {
                completed = true
                onResult(true, false, scoredIds)
            }
        } else {
            misses += 1
            // Score against the peg being practiced, not the card the child grabbed —
            // misplacing a distractor must not downgrade the distractor's own scaffold.
            onResult(false, false, listOf(atomIds.getOrElse(index) { card.atomId }))
        }
    }

    ExerciseStage(
        modifier = modifier,
        promptChrome = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
        },
        prompt = {
            if (!illustrationEmoji.isNullOrBlank()) {
                Text(text = illustrationEmoji, fontSize = 84.sp)
            }
            AnimatedContent(
                targetState = completed,
                transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(140)) },
                label = "sentence_complete",
            ) { isComplete -> if (isComplete) {
                Text(words.joinToString(" "), style = MaterialTheme.typography.headlineSmall, color = WarmInk, modifier = Modifier.testTag("completed_sentence"))
            } else BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val longest = words.maxOfOrNull { it.length } ?: 1
                val shareWidth = WordFrameSizing.frameWidthDp(maxWidth.value, words.size)
                val gap = WordFrameSizing.gapDp(maxWidth.value, words.size)
                // Mit fontScale, sonst wächst der gerenderte Glyph aus dem festen
                // Peg (live: Einwort-Runde zeigte „Mam" statt „Mama" bei
                // font_scale 1.3); gewinnt trotzdem der MinGlyphSp-Floor, weitet
                // fittedFrameWidthDp den Peg, statt dass maxLines = 1 clippt.
                val glyphSp = WordFrameSizing.glyphSp(shareWidth, longest, fontScale)
                val pegWidth =
                    WordFrameSizing.fittedFrameWidthDp(shareWidth, glyphSp, longest, fontScale)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(gap.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Top,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    words.forEachIndexed { index, expected ->
                        val filled = if (resolved) expected else placed[index]
                        val atomId = atomIds.getOrElse(index) { expected }
                        Peg(
                            index = index,
                            expected = expected,
                            filled = filled,
                            showGhost = scaffoldFor(atomId) == ScaffoldLevel.Beginner,
                            armed = field.selectedKey != null && filled == null,
                            enabled = !interactionLocked,
                            opacity = interactionOpacity,
                            onTap = {
                                val selected = field.selectedKey
                                val card = cards.withIndex()
                                    .firstOrNull { (i, c) -> cardKey(i, c) == selected }
                                    ?.value
                                if (card != null) place(index, card)
                                if (filled != null) onSpeak(filled)
                            },
                            registerWith = field,
                            pegWidthDp = pegWidth,
                            glyphSp = glyphSp,
                        )
                    }
                }
            }}
        },
        answers = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("sentence_tray"),
            ) {
                if (!resolved && !completed) {
                    cards.forEachIndexed { cardIndex, card ->
                        val key = cardKey(cardIndex, card)
                        DragCard(
                            state = field,
                            key = key,
                            enabled = !interactionLocked,
                            onTap = {
                                field.select(key)
                                onSpeak(card.display)
                            },
                            onDropped = { zoneKey ->
                                SentenceOrderTray.pegIndex(zoneKey ?: "")?.let { place(it, card) }
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = AbcDimens.kidTouch - 8.dp)
                                .alpha(interactionOpacity)
                                .background(
                                    // SkyBlue, nicht LeafGreen: die Auswahl ist ein
                                    // unvalidierter Aktiv-Zustand, kein "richtig" —
                                    // Grün ist für gefüllte Pegs reserviert (§10:
                                    // eine Bedeutung pro Farbe).
                                    color = if (field.selectedKey == key) SkyBlue else CreamElevated,
                                    shape = RoundedCornerShape(18.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("card_${card.display}"),
                        ) {
                            Text(
                                text = card.display,
                                style = MaterialTheme.typography.headlineSmall,
                                // Cream on SkyBlue ~3.88:1 (large glyph; Herleitung wie
                                // SymbolHuntTrainer's TilePalette, see Color.kt);
                                // WarmInk on CreamElevated ~8.9:1.
                                color = if (field.selectedKey == key) Cream else WarmInk,
                            )
                        }
                    }
                }
            }
            if (misses >= 2 && !resolved) {
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

// Mit Tray-Index wie WordBuildTray.tileKey: zwei Karten mit gleichem Wort teilen
// sich sonst selectedKey/draggingKey/Bounds — "dragging one moves both".
private fun cardKey(index: Int, card: WordBlock): String =
    "card-$index-${card.atomId}-${card.display}"

/**
 * Filled-peg border colour, dedicated and darker than [LeafGreen] — same fix as
 * WordBuildTrainer's `SlotBorderGreen` (identical situation: the border sits flush
 * against the peg's own [CreamElevated] fill, where full-opacity [LeafGreen] only
 * reaches 2.87:1). 3.79:1 against CreamElevated, 4.71:1 against the page's Cream.
 */
private val PegBorderGreen = Color(0xFF3A7A44)

@Composable
private fun Peg(
    index: Int,
    expected: String,
    filled: String?,
    showGhost: Boolean,
    armed: Boolean,
    enabled: Boolean,
    opacity: Float,
    onTap: () -> Unit,
    registerWith: app.abcvorschule.ui.exercise.drag.DragFieldState,
    pegWidthDp: Float,
    glyphSp: Float,
) {
    DropZone(
        state = registerWith,
        key = SentenceOrderTray.pegKey(index),
        onTap = onTap,
        enabled = enabled,
        modifier = Modifier
            .width(pegWidthDp.dp)
            .defaultMinSize(minHeight = 64.dp)
            .alpha(opacity)
            .background(
                // SkyBlue-Wash wie die armierte Karte im Tray: "hier kann die
                // gewählte Karte hin" ist ein Aktiv-Signal, kein "richtig" —
                // LeafGreen bleibt dem gefüllten Peg (§10).
                color = if (armed) SkyBlue.copy(alpha = 0.22f) else CreamElevated,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 3.dp,
                // Same deviation as WordBuildTrainer's Frame() border (identical pattern,
                // "wie WordBuild-Slots" per the brief): the literal LeafGreen.copy(0.7f) /
                // WarmMuted.copy(0.32f) fail the 3:1 UI-component floor once composited
                // over CreamElevated. WarmMuted at alpha 0.9f clears 3:1 against
                // CreamElevated itself (3.11:1). LeafGreen at full opacity still only
                // reaches 2.87:1 against CreamElevated — PegBorderGreen is the dedicated
                // darker fix for that case (3.79:1).
                color = if (filled != null) PegBorderGreen else WarmMuted.copy(alpha = 0.9f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(
                horizontal = WordFrameSizing.FramePaddingDp.dp,
                vertical = WordFrameSizing.FramePaddingDp.dp,
            )
            .testTag("peg_$index"),
    ) {
        when {
            filled != null -> Text(
                text = filled,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = glyphSp.sp),
                color = WarmInk,
                maxLines = 1,
            )
            showGhost -> Text(
                text = expected,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = glyphSp.sp),
                color = WarmInk,
                maxLines = 1,
                modifier = Modifier.alpha(0.22f),
            )
            else -> Text(
                text = "_",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = glyphSp.sp),
                // Decorative empty-peg marker, not reading content — alpha bumped +0.1
                // (0.45f -> 0.55f), same pattern as WordBuildTrainer's Frame().
                color = WarmMuted.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
}
