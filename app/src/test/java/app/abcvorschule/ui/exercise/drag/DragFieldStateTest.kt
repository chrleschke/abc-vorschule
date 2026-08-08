package app.abcvorschule.ui.exercise.drag

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragFieldStateTest {
    private val zoneA = Rect(0f, 0f, 100f, 100f)
    private val zoneB = Rect(100f, 0f, 200f, 100f)

    @Test
    fun endDragReturnsHitZoneWhenDraggedFarEnoughOntoIt() {
        val state = DragFieldState()
        state.putCard("card", Rect(0f, 0f, 50f, 50f))
        state.putZone("zoneA", zoneA)
        state.startDrag("card")
        state.drag("card", Offset(40f, 0f))

        assertEquals("zoneA", state.endDrag("card"))
    }

    @Test
    fun endDragReturnsNullWhenItOverlapsNoZone() {
        val state = DragFieldState()
        state.putCard("card", Rect(0f, 0f, 50f, 50f))
        state.putZone("zoneB", zoneB)
        state.startDrag("card")
        state.drag("card", Offset(40f, 0f))

        assertNull(state.endDrag("card"))
    }

    @Test
    fun endDragReturnsNullWhenTravelIsAtOrBelowMinCommitPxEvenOnAZone() {
        val state = DragFieldState()
        // The card already sits squarely on zoneA without moving.
        state.putCard("card", Rect(0f, 0f, 50f, 50f))
        state.putZone("zoneA", zoneA)
        state.startDrag("card")
        state.drag("card", Offset(DragHitTest.MinCommitPx, 0f))

        assertNull(state.endDrag("card"))
    }

    @Test
    fun endDragResetsDraggingKeyAndOffsetOnHitPath() {
        val state = DragFieldState()
        state.putCard("card", Rect(0f, 0f, 50f, 50f))
        state.putZone("zoneA", zoneA)
        state.startDrag("card")
        state.drag("card", Offset(40f, 0f))
        state.endDrag("card")

        assertNull(state.draggingKey)
        assertEquals(Offset.Zero, state.dragOffset)
    }

    @Test
    fun endDragResetsDraggingKeyAndOffsetOnNoHitPath() {
        val state = DragFieldState()
        state.putCard("card", Rect(0f, 0f, 50f, 50f))
        state.putZone("zoneB", zoneB)
        state.startDrag("card")
        state.drag("card", Offset(40f, 0f))
        state.endDrag("card")

        assertNull(state.draggingKey)
        assertEquals(Offset.Zero, state.dragOffset)
    }

    @Test
    fun endDragResetsDraggingKeyAndOffsetBelowThreshold() {
        val state = DragFieldState()
        state.putCard("card", Rect(0f, 0f, 50f, 50f))
        state.putZone("zoneA", zoneA)
        state.startDrag("card")
        state.drag("card", Offset(1f, 0f))
        state.endDrag("card")

        assertNull(state.draggingKey)
        assertEquals(Offset.Zero, state.dragOffset)
    }

    @Test
    fun snappedBackCardCannotAccumulateOffsetIntoNextGesture() {
        val state = DragFieldState()
        state.putCard("card", Rect(0f, 0f, 50f, 50f))
        state.putZone("zoneB", zoneB)
        state.startDrag("card")
        state.drag("card", Offset(40f, 0f))
        state.endDrag("card") // misses, snaps back

        // A fresh gesture on the same card must start from a clean offset.
        state.startDrag("card")
        assertEquals(Offset.Zero, state.dragOffset)
    }

    @Test
    fun startDragSetsDraggingAndSelectedKeyAndZeroesOffsetEvenWhenPreviouslyNonZero() {
        val state = DragFieldState()
        state.putCard("card", Rect(0f, 0f, 50f, 50f))
        state.startDrag("card")
        state.drag("card", Offset(15f, 15f))
        assertTrue(state.dragOffset != Offset.Zero)

        state.startDrag("card")

        assertEquals("card", state.draggingKey)
        assertEquals("card", state.selectedKey)
        assertEquals(Offset.Zero, state.dragOffset)
    }

    @Test
    fun selectNullClearsSelection() {
        val state = DragFieldState()
        state.select("tile")
        assertEquals("tile", state.selectedKey)

        state.select(null)

        assertNull(state.selectedKey)
    }

    @Test
    fun resetClearsSelectionDragStateAndBothBoundsMaps() {
        val state = DragFieldState()
        state.putCard("card", Rect(0f, 0f, 50f, 50f))
        state.putZone("zoneA", zoneA)
        state.select("card")
        state.startDrag("card")
        state.drag("card", Offset(40f, 0f))

        state.reset()

        assertNull(state.selectedKey)
        assertNull(state.draggingKey)
        assertEquals(Offset.Zero, state.dragOffset)

        // Bounds maps were cleared too: even a drag that would otherwise hit
        // zoneA now resolves to nothing because the zone is gone.
        state.putCard("card", Rect(0f, 0f, 50f, 50f))
        state.startDrag("card")
        state.drag("card", Offset(40f, 0f))
        assertNull(state.endDrag("card"))
    }

    @Test
    fun removeZoneMakesAPreviouslyRegisteredZoneUnreachable() {
        val state = DragFieldState()
        // Straddles both zones but overlaps zoneB more, so it resolves to "zoneB" first.
        val straddling = Rect(70f, 10f, 170f, 90f)
        state.putCard("card", straddling)
        state.putZone("zoneA", zoneA)
        state.putZone("zoneB", zoneB)
        state.startDrag("card")
        state.drag("card", Offset(100f, 0f))
        assertEquals("zoneB", state.endDrag("card"))

        state.removeZone("zoneB")

        state.startDrag("card")
        state.drag("card", Offset(100f, 0f))
        // Same bounds, but zoneB is gone: the drop must now resolve to zoneA, never zoneB.
        assertEquals("zoneA", state.endDrag("card"))
    }

    @Test
    fun removeCardMakesEndDragReturnNullForThatKey() {
        val state = DragFieldState()
        state.putCard("card", Rect(0f, 0f, 50f, 50f))
        state.putZone("zoneA", zoneA)
        state.startDrag("card")
        state.drag("card", Offset(40f, 0f))

        state.removeCard("card")

        assertNull(state.endDrag("card"))
    }
}
