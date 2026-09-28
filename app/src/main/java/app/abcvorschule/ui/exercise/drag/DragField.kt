package app.abcvorschule.ui.exercise.drag

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.runtime.rememberCoroutineScope
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
import app.abcvorschule.ui.rewards.AbcSfx
import app.abcvorschule.ui.rewards.Sfx
import app.abcvorschule.ui.theme.AbcMotion
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

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
    fun startDrag(key: String, from: Offset = Offset.Zero): Boolean {
        if (draggingKey != null && draggingKey != key) return false
        draggingKey = key
        selectedKey = key
        dragOffset = from
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

/** Ab dieser Strecke ist ein Rückflug zu sehen und bekommt seinen Klang; ein Zittern nicht. */
private const val AudibleReturnPx = 24f

/** Slight enlargement while a tile is airborne, so it reads as lifted off the board. */
private const val DragLiftScale = 1.08f

/**
 * Wo die Karte gerade gezeichnet wird: unter dem Finger der Drag-Versatz, danach der
 * Rückflug. Reine Funktion, damit die Übergabe zwischen beiden ohne Compose testbar ist.
 */
internal fun DragFieldState.renderOffset(key: String, flyBack: Offset): Offset =
    if (draggingKey == key) dragOffset else flyBack

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
    // Gekeyt auf die Karte: rückt an diese Stelle der Reihe eine andere Karte, beginnt
    // sie ohne Lift und ohne Rückflug der vorigen (Tray-Nachrücken nach einem Treffer).
    androidx.compose.runtime.key(state, key) {
        DragCardBody(state, key, onTap, onDropped, modifier, enabled, content)
    }
}

@Composable
private fun DragCardBody(
    state: DragFieldState,
    key: String,
    onTap: () -> Unit,
    onDropped: (zoneKey: String?) -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    val dragging = state.draggingKey == key
    // Der Rückflug: eine losgelassene Karte springt nicht an ihren Platz, sie
    // fliegt federnd dorthin zurück (PRODUCT_PRINCIPLES §2, Snap-back). Gekeyt auf
    // state UND key — rückt nach einem Treffer eine andere Karte an diese Stelle der
    // Reihe, darf sie den Flug der eingesetzten nicht erben.
    val flyBack = remember(state, key) { Animatable(Offset.Zero, Offset.VectorConverter) }
    val scope = rememberCoroutineScope()
    val lift by animateFloatAsState(
        targetValue = if (dragging) DragLiftScale else 1f,
        animationSpec = AbcMotion.Bouncy.spec(),
        label = "drag_card_lift",
    )
    fun releaseFrom(offset: Offset) {
        // UNDISPATCHED: der Startwert muss sitzen, bevor der nächste Frame die
        // Karte ohne Drag-Versatz zeichnet — sonst blitzt sie einen Frame lang an
        // ihrem Platz auf und fliegt dann erst los.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            flyBack.snapTo(offset)
            flyBack.animateTo(Offset.Zero, AbcMotion.Soft.spec(visibilityThreshold = Offset(0.5f, 0.5f)))
        }
    }
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
            // Auch im Rückflug oben: die Karte fliegt über ihre Nachbarn heim,
            // nicht unter ihnen hindurch.
            .zIndex(if (dragging || flyBack.isRunning) 1f else 0f)
            .offset {
                val o = state.renderOffset(key, flyBack.value)
                IntOffset(o.x.roundToInt(), o.y.roundToInt())
            }
            .graphicsLayer {
                scaleX = lift
                scaleY = lift
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
                            onDragStart = {
                                // Greift das Kind die Karte im Flug, übernimmt der
                                // Finger sie dort, wo sie gerade ist.
                                val airborne = flyBack.value
                                owns = state.startDrag(key, from = airborne)
                                if (owns) scope.launch { flyBack.snapTo(Offset.Zero) }
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                if (owns) state.drag(key, amount)
                            },
                            onDragEnd = {
                                if (owns) {
                                    val releasedAt = state.dragOffset
                                    val zone = state.endDrag(key)
                                    // Immer vom Loslass-Punkt heim fliegen: ein Treffer
                                    // entfernt die Karte aus dem Tray (dann ist der Flug
                                    // unsichtbar), ein falscher Slot lässt sie liegen —
                                    // dann soll sie sichtbar zurück, nicht teleportieren.
                                    releaseFrom(releasedAt)
                                    // Nirgends gelandet: hörbar zurückfedern. Einen
                                    // falschen Slot vertont der Trainer selbst, er
                                    // allein weiß, ob der Slot falsch war.
                                    if (zone == null && releasedAt.getDistance() > AudibleReturnPx) {
                                        AbcSfx.play(Sfx.Boing)
                                    }
                                    onDropped(zone)
                                }
                                owns = false
                            },
                            onDragCancel = {
                                // Snap-back ohne Zonen-Auflösung — ein Cancel ist kein Drop.
                                if (owns) {
                                    val releasedAt = state.dragOffset
                                    state.cancelDrag(key)
                                    releaseFrom(releasedAt)
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
