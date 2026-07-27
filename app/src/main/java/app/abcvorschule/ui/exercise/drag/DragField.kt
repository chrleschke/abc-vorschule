package app.abcvorschule.ui.exercise.drag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Drag/tap state for one exercise board. Bounds live in plain maps because they
 * are only read when a gesture ends — they must never drive recomposition.
 */
class DragFieldState {
    var selectedKey by mutableStateOf<String?>(null)
        private set
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    private val cards = mutableMapOf<String, Rect>()
    private val zones = mutableMapOf<String, Rect>()

    fun putCard(key: String, bounds: Rect) {
        cards[key] = bounds
    }

    fun putZone(key: String, bounds: Rect) {
        zones[key] = bounds
    }

    /**
     * Bounds must not outlive their composable. A trainer that stops composing a
     * filled slot or a placed tile would otherwise leave a phantom landing zone
     * behind, and a later drop would resolve against something nobody can see.
     */
    fun removeCard(key: String) {
        cards.remove(key)
    }

    fun removeZone(key: String) {
        zones.remove(key)
    }

    fun select(key: String?) {
        selectedKey = key
    }

    fun startDrag(key: String) {
        draggingKey = key
        selectedKey = key
        dragOffset = Offset.Zero
    }

    fun drag(delta: Offset) {
        dragOffset += delta
    }

    /** @return the zone the card landed on, or null when it should snap back. */
    fun endDrag(key: String): String? {
        val travelled = dragOffset.getDistance()
        val bounds = cards[key]
        val hit = if (bounds != null && DragHitTest.shouldCommit(travelled)) {
            DragHitTest.bestZone(bounds.toDragRect(), zones.mapValues { it.value.toDragRect() })
        } else {
            null
        }
        draggingKey = null
        dragOffset = Offset.Zero
        return hit
    }

    fun reset() {
        selectedKey = null
        draggingKey = null
        dragOffset = Offset.Zero
        cards.clear()
        zones.clear()
    }
}

private fun Rect.toDragRect() = DragRect(left, top, right, bottom)

@Composable
fun rememberDragFieldState(vararg keys: Any?): DragFieldState =
    remember(*keys) { DragFieldState() }

/** Slight enlargement while a tile is airborne, so it reads as lifted off the board. */
private const val DragLiftScale = 1.08f

/**
 * A draggable answer tile with a mandatory tap-to-place alternative (R15).
 * [onDropped] receives the resolved zone key, or null when the tile snapped back.
 */
@Composable
fun DragCard(
    state: DragFieldState,
    key: String,
    onTap: () -> Unit,
    onDropped: (zoneKey: String?) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dragging = state.draggingKey == key
    DisposableEffect(key) {
        onDispose { state.removeCard(key) }
    }
    Box(
        // zIndex/offset/scale sit BEFORE the caller's modifier on purpose: a later
        // `offset` would only move the content, leaving the caller's background and
        // border painted at the tile's resting position — which made the dragged
        // tile look like bare (near-black) text floating over the board.
        modifier = Modifier
            .zIndex(if (dragging) 1f else 0f)
            .offset {
                val o = if (dragging) state.dragOffset else Offset.Zero
                IntOffset(o.x.roundToInt(), o.y.roundToInt())
            }
            .graphicsLayer {
                val scale = if (dragging) DragLiftScale else 1f
                scaleX = scale
                scaleY = scale
            }
            .then(modifier)
            .onGloballyPositioned { state.putCard(key, it.boundsInRoot()) }
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { state.startDrag(key) },
                    onDrag = { change, amount ->
                        change.consume()
                        state.drag(amount)
                    },
                    onDragEnd = { onDropped(state.endDrag(key)) },
                    onDragCancel = { onDropped(state.endDrag(key)) },
                )
            }
            .clickable { onTap() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** A drop target. Tapping it places the currently selected tile. */
@Composable
fun DropZone(
    state: DragFieldState,
    key: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    DisposableEffect(key) {
        onDispose { state.removeZone(key) }
    }
    Box(
        modifier = modifier
            .onGloballyPositioned { state.putZone(key, it.boundsInRoot()) }
            .clickable { onTap() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
