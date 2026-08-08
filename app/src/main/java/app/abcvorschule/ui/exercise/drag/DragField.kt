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

    /**
     * @return true when this card now owns the drag. Ein zweiter Finger (bei
     * Vorschulkindern der Normalfall: Handballen, zweite Hand) darf den laufenden
     * Drag nicht übernehmen — sonst überschreibt er draggingKey/dragOffset, die
     * erste Karte springt zurück und beide Finger addieren in denselben Offset.
     */
    fun startDrag(key: String): Boolean {
        if (draggingKey != null && draggingKey != key) return false
        draggingKey = key
        selectedKey = key
        dragOffset = Offset.Zero
        return true
    }

    fun drag(key: String, delta: Offset) {
        if (draggingKey != key) return
        dragOffset += delta
    }

    /** @return the zone the card landed on, or null when it should snap back. */
    fun endDrag(key: String): String? {
        if (draggingKey != key) return null
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

    /**
     * Abgebrochene Geste (System-Gesture, Palm-Rejection): reiner Snap-back.
     * Ein Cancel darf nie wie ein Loslassen committen — die Karte hängt sonst
     * zufällig über einer falschen Zone und der nie beendete Zug wird gewertet.
     */
    fun cancelDrag(key: String) {
        if (draggingKey != key) return
        draggingKey = null
        dragOffset = Offset.Zero
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
    /** False während der Aufgaben-Sperre — weder Tap noch Drag lösen dann etwas
     * aus (design doc). */
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val dragging = state.draggingKey == key
    // Auch auf `state` gekeyt: liefert rememberDragFieldState nach einem
    // Rundenwechsel eine neue Instanz bei gleichem Karten-Key, würde ein nur
    // key-gekeyter Effect/Gesture-Block sonst im verwaisten Alt-State schreiben.
    DisposableEffect(state, key) {
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
            .then(
                if (enabled) {
                    Modifier.pointerInput(state, key) {
                        // Ob dieser Finger den Drag besitzt: ein zweiter Finger, den
                        // startDrag abweist, darf beim Loslassen weder droppen noch
                        // den laufenden Drag der ersten Karte beenden.
                        var owns = false
                        detectDragGestures(
                            onDragStart = { owns = state.startDrag(key) },
                            onDrag = { change, amount ->
                                change.consume()
                                if (owns) state.drag(key, amount)
                            },
                            onDragEnd = {
                                if (owns) onDropped(state.endDrag(key))
                                owns = false
                            },
                            onDragCancel = {
                                // Snap-back ohne Zonen-Auflösung — ein Cancel ist kein Drop.
                                if (owns) {
                                    state.cancelDrag(key)
                                    onDropped(null)
                                }
                                owns = false
                            },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled) { onTap() },
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
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    DisposableEffect(state, key) {
        onDispose { state.removeZone(key) }
    }
    Box(
        // onGloballyPositioned/clickable wrap the caller's styled modifier chain
        // (background/border/padding) so the registered bounds and the tappable
        // area are the FULL frame box, not just the padded-in content area —
        // otherwise a frame at the 56dp touch-target floor with 8dp padding on
        // each side only had a ~40dp tappable center, silently dropping taps in
        // an 8dp dead ring around every frame.
        modifier = Modifier
            .onGloballyPositioned { state.putZone(key, it.boundsInRoot()) }
            .clickable(enabled = enabled) { onTap() }
            .then(modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
