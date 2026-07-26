package app.abcvorschule.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import kotlin.math.roundToInt

/** One tappable/draggable answer tile in the tray. */
data class TrayTile(
    val key: String,
    val display: String,
    val atomId: String,
    val isDistractor: Boolean = false,
)

private const val MinDragCommitPx = 24f

/** Lets [DragSlotBoard]'s prompt content place gap targets inline (e.g. within sentence text). */
interface DragSlotBoardScope {
    @Composable
    fun GapTarget(gap: GapSlot)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DragSlotBoard(
    gaps: List<GapSlot>,
    onCorrect: () -> Unit,
    onMiss: (atomId: String) -> Unit,
    onResolve: () -> Unit,
    missCount: Int,
    modifier: Modifier = Modifier,
    distractors: List<TrayTile> = emptyList(),
    showSyllableDots: Boolean = false,
    arrangeSlotsInRow: Boolean = false,
    largeTypography: Boolean = false,
    /** False when [prompt] places every gap itself (e.g. inline in sentence text). */
    showDefaultGapRow: Boolean = true,
    onSpeakText: ((String) -> Unit)? = null,
    prompt: (@Composable DragSlotBoardScope.() -> Unit)? = null,
) {
    val filled = remember(gaps) { mutableStateMapOf<String, String>() }
    var selectedTileKey by remember(gaps) { mutableStateOf<String?>(null) }
    var dragKey by remember(gaps) { mutableStateOf<String?>(null) }
    var dragOffset by remember(gaps) { mutableStateOf(Offset.Zero) }
    // Plain maps: bounds are only read on drag end, never drive recomposition.
    val slotBounds = remember(gaps) { mutableMapOf<String, Rect>() }
    val tileBounds = remember(gaps) { mutableMapOf<String, Rect>() }
    val compactSpell = largeTypography && gaps.size >= 4
    val textSize = when {
        compactSpell -> AbcDimens.syllableSp
        largeTypography -> AbcDimens.letterSp
        else -> AbcDimens.answerTileSp
    }
    val frameMin = when {
        compactSpell -> 72.dp
        largeTypography -> AbcDimens.letterFrame
        else -> 64.dp
    }
    val useRow = arrangeSlotsInRow || showSyllableDots || gaps.size in 2..4

    val remaining = gaps.filter { filled[it.slotKey] == null }
    val trayTiles = remaining.map { gap ->
        TrayTile(key = gap.slotKey, display = gap.display, atomId = gap.atomId)
    } + if (remaining.isEmpty()) emptyList() else distractors
    val selectedTile = trayTiles.firstOrNull { it.key == selectedTileKey }

    fun place(tile: TrayTile, gap: GapSlot) {
        if (filled[gap.slotKey] != null) return
        selectedTileKey = null
        if (!tile.isDistractor && tile.display == gap.display) {
            filled[gap.slotKey] = tile.display
            if (filled.size == gaps.size) onCorrect()
        } else {
            onMiss(gap.atomId)
        }
    }

    fun commitDragEnd(tile: TrayTile) {
        val bounds = tileBounds[tile.key]
        if (bounds != null && dragOffset.getDistance() > MinDragCommitPx) {
            // Real hit-testing: the tile lands where the child dropped it —
            // a wrong slot is simply wrong, dropping nowhere snaps back.
            val hit = gaps.firstOrNull { gap ->
                filled[gap.slotKey] == null &&
                    slotBounds[gap.slotKey]?.overlaps(bounds) == true
            }
            if (hit != null) place(tile, hit)
        }
        dragKey = null
        dragOffset = Offset.Zero
    }

    val scope = remember(gaps) {
        object : DragSlotBoardScope {
            @Composable
            override fun GapTarget(gap: GapSlot) {
                SlotTarget(
                    gap = gap,
                    filledDisplay = filled[gap.slotKey],
                    selected = selectedTileKey != null && filled[gap.slotKey] == null,
                    minSize = frameMin,
                    textSize = textSize,
                    onTapPlace = {
                        val tile = selectedTile
                        if (tile != null) place(tile, gap)
                    },
                    onSpeak = onSpeakText,
                    onPositioned = { slotBounds[gap.slotKey] = it },
                )
            }
        }
    }

    ExerciseStage(
        modifier = modifier,
        prompt = {
            prompt?.invoke(scope)
            if (showDefaultGapRow) {
                if (useRow) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        gaps.forEachIndexed { index, gap ->
                            if (showSyllableDots && index > 0) {
                                Text(
                                    text = "·",
                                    style = MaterialTheme.typography.displayLarge,
                                    color = SoftSand.copy(alpha = 0.55f),
                                )
                            }
                            scope.GapTarget(gap)
                        }
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        gaps.forEach { gap -> scope.GapTarget(gap) }
                    }
                }
            }
        },
        answers = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.testTag("tile_tray"),
            ) {
                trayTiles.forEach { tile ->
                    DraggableTile(
                        label = tile.display,
                        selected = selectedTileKey == tile.key,
                        dragging = dragKey == tile.key,
                        dragOffset = if (dragKey == tile.key) dragOffset else Offset.Zero,
                        textSize = textSize,
                        tileKey = tile.key,
                        onSelect = {
                            selectedTileKey = tile.key
                            onSpeakText?.invoke(tile.display)
                        },
                        onDragStart = {
                            dragKey = tile.key
                            dragOffset = Offset.Zero
                            selectedTileKey = tile.key
                        },
                        onDrag = { dragOffset += it },
                        onDragEnd = { commitDragEnd(tile) },
                        onPositioned = { tileBounds[tile.key] = it },
                    )
                }
            }
            if (missCount >= 2) {
                AbcResolveButton(onClick = onResolve)
            }
        },
    )
}

@Composable
private fun SlotTarget(
    gap: GapSlot,
    filledDisplay: String?,
    selected: Boolean,
    minSize: androidx.compose.ui.unit.Dp,
    textSize: TextUnit,
    onTapPlace: () -> Unit,
    onSpeak: ((String) -> Unit)?,
    onPositioned: (Rect) -> Unit,
) {
    val silhouette = ScaffoldMapping.showsSilhouette(gap.scaffold)
    Box(
        modifier = Modifier
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
            .background(
                color = if (selected) SoftMint.copy(alpha = 0.25f) else NightElevated,
                shape = RoundedCornerShape(22.dp),
            )
            .border(
                width = 3.dp,
                color = if (silhouette && filledDisplay == null) {
                    SoftSand.copy(alpha = 0.35f)
                } else {
                    SoftMint.copy(alpha = 0.55f)
                },
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .clickable {
                if (filledDisplay != null) {
                    onSpeak?.invoke(filledDisplay)
                } else {
                    onTapPlace()
                }
            }
            .testTag("slot_${gap.slotKey}"),
        contentAlignment = Alignment.Center,
    ) {
        when {
            filledDisplay != null -> Text(
                filledDisplay,
                fontSize = textSize,
                color = SoftSand,
            )
            silhouette -> Text(
                text = gap.display,
                fontSize = textSize,
                color = SoftSand,
                modifier = Modifier.alpha(0.22f),
            )
            gap.scaffold == ScaffoldLevel.Advanced -> Text(
                text = "_",
                fontSize = textSize,
                color = SoftSand.copy(alpha = 0.45f),
            )
        }
    }
}

@Composable
private fun DraggableTile(
    label: String,
    selected: Boolean,
    dragging: Boolean,
    dragOffset: Offset,
    textSize: TextUnit,
    tileKey: String,
    onSelect: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onPositioned: (Rect) -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .onGloballyPositioned { onPositioned(it.boundsInRoot()) }
            .defaultMinSize(minWidth = AbcDimens.tileMinWidth, minHeight = AbcDimens.kidTouch)
            .background(
                color = if (selected || dragging) SoftMint else NightElevated,
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .pointerInput(tileKey) {
                detectDragGestures(
                    onDragStart = { onDragStart() },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() },
                    onDrag = { change, amount ->
                        change.consume()
                        onDrag(amount)
                    },
                )
            }
            .clickable { onSelect() }
            .testTag("tile_$label"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = textSize,
            color = if (selected || dragging) NightInk else SoftSand,
        )
    }
}
