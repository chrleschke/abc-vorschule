package app.abcvorschule.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DragSlotBoard(
    gaps: List<GapSlot>,
    onCorrect: () -> Unit,
    onMiss: (atomId: String) -> Unit,
    onResolve: () -> Unit,
    missCount: Int,
    modifier: Modifier = Modifier,
) {
    val filled = remember(gaps) { mutableStateMapOf<String, String>() }
    var selectedTile by remember { mutableStateOf<String?>(null) }
    var dragId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }

    val remaining = gaps.filter { filled[it.atomId] == null }

    fun place(tileDisplay: String, gap: GapSlot) {
        if (filled[gap.atomId] != null) return
        if (tileDisplay == gap.display) {
            filled[gap.atomId] = tileDisplay
            selectedTile = null
            if (filled.size == gaps.size) onCorrect()
        } else {
            selectedTile = null
            onMiss(gap.atomId)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            gaps.forEach { gap ->
                val value = filled[gap.atomId]
                SlotTarget(
                    gap = gap,
                    filledDisplay = value,
                    selected = selectedTile != null && value == null,
                    onTapPlace = {
                        val tile = selectedTile
                        if (tile != null) place(tile, gap)
                    },
                )
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.testTag("tile_tray"),
        ) {
            remaining.forEach { gap ->
                DraggableTile(
                    label = gap.display,
                    emoji = gap.emoji,
                    selected = selectedTile == gap.display,
                    dragging = dragId == gap.atomId,
                    dragOffset = if (dragId == gap.atomId) dragOffset else Offset.Zero,
                    onSelect = { selectedTile = gap.display },
                    onDragStart = {
                        dragId = gap.atomId
                        dragOffset = Offset.Zero
                        selectedTile = gap.display
                    },
                    onDrag = { dragOffset += it },
                    onDragEnd = {
                        // Commit via tap-to-place semantics after a meaningful drag:
                        // place into the first empty matching slot, else miss if moved far.
                        val empty = gaps.filter { filled[it.atomId] == null }
                        if (dragOffset.getDistance() > 48f) {
                            val match = empty.firstOrNull { it.display == gap.display }
                            if (match != null) {
                                place(gap.display, match)
                            } else {
                                onMiss(empty.firstOrNull()?.atomId ?: gap.atomId)
                            }
                        }
                        dragId = null
                        dragOffset = Offset.Zero
                    },
                )
            }
        }

        if (missCount >= 2) {
            TextButton(onClick = onResolve) {
                Text(stringResource(R.string.resolve))
            }
        }
    }
}

@Composable
private fun SlotTarget(
    gap: GapSlot,
    filledDisplay: String?,
    selected: Boolean,
    onTapPlace: () -> Unit,
) {
    val silhouette = ScaffoldMapping.showsSilhouette(gap.scaffold)
    Box(
        modifier = Modifier
            .height(64.dp)
            .background(
                color = if (selected) SoftMint.copy(alpha = 0.25f) else NightElevated,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 2.dp,
                color = if (silhouette && filledDisplay == null) {
                    SoftSand.copy(alpha = 0.35f)
                } else {
                    SoftMint.copy(alpha = 0.5f)
                },
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clickable(enabled = filledDisplay == null) { onTapPlace() }
            .testTag("slot_${gap.atomId}"),
        contentAlignment = Alignment.Center,
    ) {
        when {
            filledDisplay != null -> Text(
                filledDisplay,
                style = MaterialTheme.typography.titleLarge,
                color = SoftSand,
            )
            silhouette -> Text(
                text = gap.display,
                style = MaterialTheme.typography.titleLarge,
                color = SoftSand,
                modifier = Modifier.alpha(0.22f),
            )
            gap.scaffold == ScaffoldLevel.Advanced -> Text(
                text = "____",
                style = MaterialTheme.typography.titleLarge,
                color = SoftSand.copy(alpha = 0.5f),
            )
        }
    }
}

@Composable
private fun DraggableTile(
    label: String,
    emoji: String,
    selected: Boolean,
    dragging: Boolean,
    dragOffset: Offset,
    onSelect: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
) {
    Box(
        modifier = Modifier
            .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
            .background(
                color = if (selected || dragging) SoftMint else NightElevated,
                shape = RoundedCornerShape(18.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .pointerInput(label) {
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
            text = "$emoji $label",
            style = MaterialTheme.typography.titleLarge,
            color = if (selected || dragging) NightInk else SoftSand,
        )
    }
}
