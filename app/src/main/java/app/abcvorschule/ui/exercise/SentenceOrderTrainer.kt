package app.abcvorschule.ui.exercise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
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
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand

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
    words: List<String>,
    atomIds: List<String>,
    illustrationEmoji: String?,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = round.sentenceId
    val field = rememberDragFieldState(roundKey)
    val placed = remember(roundKey) { mutableStateMapOf<Int, String>() }
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    val scoredIds = remember(roundKey) { atomIds.distinct() }
    val cards = SentenceOrderTray.cards(
        words,
        atomIds,
        round.distractors,
        placed.values.toList(),
        seed = round.sentenceId.hashCode(),
    )

    fun place(index: Int, card: WordBlock) {
        if (resolved || placed[index] != null) return
        field.select(null)
        if (OrderedPlacement.isCorrectPlacement(index, card.display, words)) {
            placed[index] = card.display
            onSpeak(card.display)
            if (OrderedPlacement.isSolved(placed.toMap(), words)) {
                onResult(true, false, scoredIds)
            }
        } else {
            misses += 1
            onResult(false, false, listOf(card.atomId))
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
            if (!illustrationEmoji.isNullOrBlank()) {
                Text(text = illustrationEmoji, fontSize = 84.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                ) {
                    // A gently sagging line, drawn rather than iconified.
                    drawLine(
                        color = MutedText.copy(alpha = 0.5f),
                        start = Offset(0f, size.height * 0.2f),
                        end = Offset(size.width, size.height * 0.2f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
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
                            onTap = {
                                val selected = field.selectedKey
                                val card = cards.firstOrNull { cardKey(it) == selected }
                                if (card != null) place(index, card)
                                if (filled != null) onSpeak(filled)
                            },
                            registerWith = field,
                        )
                    }
                }
            }
        },
        answers = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("sentence_tray"),
            ) {
                if (!resolved) {
                    cards.forEach { card ->
                        val key = cardKey(card)
                        DragCard(
                            state = field,
                            key = key,
                            onTap = {
                                field.select(key)
                                onSpeak(card.display)
                            },
                            onDropped = { zoneKey ->
                                SentenceOrderTray.pegIndex(zoneKey ?: "")?.let { place(it, card) }
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = AbcDimens.kidTouch - 8.dp)
                                .background(
                                    color = if (field.selectedKey == key) SoftMint else NightElevated,
                                    shape = RoundedCornerShape(18.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("card_${card.display}"),
                        ) {
                            Text(
                                text = card.display,
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (field.selectedKey == key) NightInk else SoftSand,
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

private fun cardKey(card: WordBlock): String = "card-${card.atomId}-${card.display}"

@Composable
private fun Peg(
    index: Int,
    expected: String,
    filled: String?,
    showGhost: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: app.abcvorschule.ui.exercise.drag.DragFieldState,
) {
    DropZone(
        state = registerWith,
        key = SentenceOrderTray.pegKey(index),
        onTap = onTap,
        modifier = Modifier
            .defaultMinSize(minWidth = 76.dp, minHeight = 64.dp)
            .background(
                color = if (armed) SoftMint.copy(alpha = 0.22f) else NightElevated,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 3.dp,
                color = if (filled != null) SoftMint.copy(alpha = 0.7f) else SoftSand.copy(alpha = 0.32f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("peg_$index"),
    ) {
        when {
            filled != null -> Text(
                text = filled,
                style = MaterialTheme.typography.headlineSmall,
                color = SoftSand,
            )
            showGhost -> Text(
                text = expected,
                style = MaterialTheme.typography.headlineSmall,
                color = SoftSand,
                modifier = Modifier.alpha(0.22f),
            )
            else -> Text(
                text = "_",
                style = MaterialTheme.typography.headlineSmall,
                color = SoftSand.copy(alpha = 0.45f),
            )
        }
    }
}
