package app.abcvorschule.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.Atom
import app.abcvorschule.content.WordBlock
import app.abcvorschule.content.WordBuildRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.exercise.drag.DragCard
import app.abcvorschule.ui.exercise.drag.DragFieldState
import app.abcvorschule.ui.exercise.drag.DropZone
import app.abcvorschule.ui.exercise.drag.rememberDragFieldState
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand

/**
 * Frames sit in one row and a word can need four of them (N·e·s·t), so they are
 * deliberately narrower than [AbcDimens.letterFrame], which is sized for a single
 * standalone glyph and would overflow a narrow screen here.
 */
private val WordFrameMin = 84.dp

object WordBuildTray {
    /** Preschoolers must be able to scan the whole tray at a glance. */
    const val MaxTrayTiles = 5

    fun tiles(round: WordBuildRound, placedDisplays: List<String>): List<WordBlock> {
        val remaining = round.blocks.toMutableList()
        placedDisplays.forEach { display ->
            val hit = remaining.indexOfFirst { it.display == display }
            if (hit >= 0) remaining.removeAt(hit)
        }
        if (remaining.isEmpty()) return emptyList()
        return (remaining + round.distractors).take(MaxTrayTiles)
    }

    fun frameKey(index: Int): String = "frame-$index"

    fun frameIndex(key: String): Int? = key.removePrefix("frame-").toIntOrNull()
        ?.takeIf { key.startsWith("frame-") }
}

/**
 * Trainer 4 — Wort-Bauer. The picture anchors the meaning, the frames carry the
 * per-atom scaffold (silhouette vs. empty), and only authored blocks are offered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordBuildTrainer(
    round: WordBuildRound,
    target: Atom,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "${round.targetAtomId}-${round.blocks.size}"
    val field = rememberDragFieldState(roundKey)
    val placed = remember(roundKey) { mutableStateMapOf<Int, String>() }
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    val solution = remember(roundKey) { round.blocks.map { it.display } }
    val scoredIds = remember(roundKey) {
        (round.blocks.map { it.atomId } + round.targetAtomId).distinct()
    }
    val tiles = WordBuildTray.tiles(round, placed.values.toList())

    fun place(index: Int, block: WordBlock) {
        if (resolved || placed[index] != null) return
        field.select(null)
        if (OrderedPlacement.isCorrectPlacement(index, block.display, solution)) {
            placed[index] = block.display
            onSpeak(block.display)
            if (OrderedPlacement.isSolved(placed.toMap(), solution)) {
                onResult(true, false, scoredIds)
            }
        } else {
            misses += 1
            onResult(false, false, listOf(block.atomId))
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
            Text(text = target.emoji, fontSize = 84.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                solution.forEachIndexed { index, expected ->
                    if (index > 0) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.displayLarge,
                            color = SoftSand.copy(alpha = 0.55f),
                        )
                    }
                    val filled = if (resolved) expected else placed[index]
                    val atomId = round.blocks[index].atomId
                    Frame(
                        expected = expected,
                        filled = filled,
                        showSilhouette = scaffoldFor(atomId) == ScaffoldLevel.Beginner,
                        armed = field.selectedKey != null && filled == null,
                        onTap = {
                            val selected = field.selectedKey
                            val block = tiles.firstOrNull { blockKey(it) == selected }
                            if (block != null) place(index, block)
                            if (filled != null) onSpeak(filled)
                        },
                        registerWith = field,
                        index = index,
                    )
                }
            }
        },
        answers = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.testTag("word_tray"),
            ) {
                if (!resolved) {
                    tiles.forEach { block ->
                        val key = blockKey(block)
                        DragCard(
                            state = field,
                            key = key,
                            onTap = {
                                field.select(key)
                                onSpeak(block.display)
                            },
                            onDropped = { zoneKey ->
                                WordBuildTray.frameIndex(zoneKey ?: "")?.let { place(it, block) }
                            },
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = AbcDimens.tileMinWidth,
                                    minHeight = AbcDimens.kidTouch,
                                )
                                .background(
                                    color = if (field.selectedKey == key) SoftMint else NightElevated,
                                    shape = RoundedCornerShape(22.dp),
                                )
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                                .testTag("block_${block.display}"),
                        ) {
                            Text(
                                text = block.display,
                                fontSize = AbcDimens.syllableSp,
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

private fun blockKey(block: WordBlock): String = "block-${block.atomId}-${block.display}"

@Composable
private fun Frame(
    expected: String,
    filled: String?,
    showSilhouette: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: DragFieldState,
    index: Int,
) {
    DropZone(
        state = registerWith,
        key = WordBuildTray.frameKey(index),
        onTap = onTap,
        modifier = Modifier
            .defaultMinSize(minWidth = WordFrameMin, minHeight = WordFrameMin)
            .background(
                color = if (armed) SoftMint.copy(alpha = 0.22f) else NightElevated,
                shape = RoundedCornerShape(22.dp),
            )
            .border(
                width = 3.dp,
                color = if (filled != null) SoftMint.copy(alpha = 0.7f) else SoftSand.copy(alpha = 0.35f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("frame_$index"),
    ) {
        when {
            filled != null -> Text(text = filled, fontSize = AbcDimens.syllableSp, color = SoftSand)
            showSilhouette -> Text(
                text = expected,
                fontSize = AbcDimens.syllableSp,
                color = SoftSand,
                modifier = Modifier.alpha(0.22f),
            )
            else -> Text(
                text = "_",
                fontSize = AbcDimens.syllableSp,
                color = SoftSand.copy(alpha = 0.45f),
            )
        }
    }
}
