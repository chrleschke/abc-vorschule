package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TenFrameStateTest {
    /** Tippt [count] mal auf das, was der Puls gerade vorschlägt. */
    private fun tapAlong(start: TenFrameState, count: Int): TenFrameState =
        (0 until count).fold(start) { state, _ ->
            state.nextIndex?.let(state::tap) ?: state
        }

    @Test
    fun theFirstSummandIsRealAndTheSecondIsAPlaceholder() {
        val start = TenFrameState.forRound(16, 8)
        assertEquals(24, start.total)
        assertEquals(16, start.realCount)
        assertTrue(start.isReal(15))
        assertFalse(start.isReal(16))
        assertEquals(16, start.nextIndex)
    }

    @Test
    fun onlyTheNextPlaceholderIsTappable() {
        // Damit kann sich das Kind nicht verzählen — und es gibt keinen Fehltipp,
        // der einen Rückweg bräuchte.
        val start = TenFrameState.forRound(16, 8)
        assertFalse(start.isTappable(0))
        assertFalse(start.isTappable(15))
        assertTrue(start.isTappable(16))
        assertFalse(start.isTappable(17))
    }

    @Test
    fun tappingAnythingElseDoesNothing() {
        val start = TenFrameState.forRound(16, 8)
        assertEquals(start, start.tap(0))
        assertEquals(start, start.tap(17))
        assertEquals(start, start.tap(99))
    }

    @Test
    fun aSecondTapOnAFilledObjectDoesNotTakeItBack() {
        // Kein Widerruf: das Bild wächst nur in eine Richtung, wie die Rechnung.
        val one = TenFrameState.forRound(16, 8).tap(16)
        assertEquals(17, one.realCount)
        assertEquals(one, one.tap(16))
    }

    @Test
    fun tappingEveryPlaceholderCompletesTheField() {
        val start = TenFrameState.forRound(16, 8)
        assertFalse(start.complete)
        val done = tapAlong(start, 8)
        assertEquals(24, done.realCount)
        assertTrue(done.complete)
        assertNull(done.nextIndex)
        assertEquals(done, done.tap(23))
    }

    @Test
    fun onlyWholeRowsCountAsTens() {
        val start = TenFrameState.forRound(16, 8)
        assertEquals(10, start.fullTens)
        assertEquals(20, tapAlong(start, 4).fullTens)
        assertEquals(20, tapAlong(start, 8).fullTens)
    }

    @Test
    fun onlyTheTapThatFillsARowReportsATen() {
        // Nur dieser Tipp wird gesprochen — alles andere sieht das Kind.
        // 16 + 8: der erste Tipp macht 17, der vierte macht 20 voll.
        val start = TenFrameState.forRound(16, 8)
        (0 until 8).forEach { done ->
            val before = tapAlong(start, done)
            val after = before.tap(before.nextIndex!!)
            val reported = before.completedTenAfter(after)
            if (done == 3) {
                assertEquals("Tipp ${done + 1} füllt den Zehner", 20, reported)
            } else {
                assertNull("Tipp ${done + 1} darf schweigen", reported)
            }
        }
    }

    @Test
    fun everyRoundReportsExactlyAsManyTensAsItActuallyFills() {
        (1..30).forEach { left ->
            (1..30 - left).forEach { right ->
                var state = TenFrameState.forRound(left, right)
                var announced = 0
                repeat(right) {
                    val next = state.tap(state.nextIndex!!)
                    if (state.completedTenAfter(next) != null) announced++
                    state = next
                }
                val expected = TenFrame.fullRowCount(left + right) - TenFrame.fullRowCount(left)
                assertEquals("$left + $right", expected, announced)
            }
        }
    }
}
