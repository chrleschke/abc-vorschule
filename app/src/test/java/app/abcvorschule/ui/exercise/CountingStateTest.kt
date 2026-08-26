package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountingStateTest {
    private fun tapAll(start: CountingState, count: Int): CountingState =
        (0 until count).fold(start) { state, index -> state.tap(index) }

    @Test
    fun nothingIsMirroredIntoTheAnswerFieldBeforeTheFirstTap() {
        // Sonst stünde bei Plus sofort eine 0 im Feld und bei Minus sofort der linke
        // Operand — das Kind könnte die Startzahl absenden, ohne etwas getan zu haben.
        assertNull(CountingState.forRound(MathOperation.Add, 7, 8).counted)
        assertNull(CountingState.forRound(MathOperation.Subtract, 15, 6).counted)
        assertNull(CountingState.forRound(MathOperation.Multiply, 4, 5).counted)
    }

    @Test
    fun plusCountsUpAcrossBothGroups() {
        val start = CountingState.forRound(MathOperation.Add, 7, 8)
        assertEquals(1, start.tap(0).counted)
        assertEquals(7, tapAll(start, 7).counted)
        val done = tapAll(start, 15)
        assertEquals(15, done.counted)
        assertTrue(done.complete)
    }

    @Test
    fun minusCountsDownFromTheStartingQuantity() {
        val start = CountingState.forRound(MathOperation.Subtract, 15, 6)
        assertEquals(14, start.tap(0).counted)
        assertEquals(9, tapAll(start, 6).counted)
    }

    @Test
    fun minusStopsAtTheTakeAwayTargetSoTheChildCannotOvershoot() {
        val start = CountingState.forRound(MathOperation.Subtract, 15, 6)
        val full = tapAll(start, 6)
        assertTrue(full.complete)
        // Der siebte Tipp auf ein noch stehendes Objekt ändert nichts.
        val overshoot = full.tap(6)
        assertEquals(9, overshoot.counted)
        assertEquals(full.tapped, overshoot.tapped)
    }

    @Test
    fun tappingAnAlreadyTappedObjectTakesTheTapBack() {
        val plus = CountingState.forRound(MathOperation.Add, 7, 8).tap(0).tap(1)
        assertEquals(2, plus.counted)
        assertEquals(1, plus.tap(1).counted)
        assertFalse(plus.tap(1).isTapped(1))

        // Auch am Deckel: sonst wäre eine Fehltipp-Serie bei Minus eine Sackgasse.
        val minus = tapAll(CountingState.forRound(MathOperation.Subtract, 15, 6), 6)
        assertEquals(10, minus.tap(5).counted)
        assertFalse(minus.tap(5).complete)
    }

    @Test
    fun tapsOutsideTheFieldAreIgnored() {
        val start = CountingState.forRound(MathOperation.Add, 7, 8)
        assertEquals(start, start.tap(-1))
        assertEquals(start, start.tap(15))
    }

    @Test
    fun theCompletedFieldAlwaysHoldsTheArithmeticAnswer() {
        listOf(
            Triple(MathOperation.Add, 7, 8),
            Triple(MathOperation.Add, 15, 15),
            Triple(MathOperation.Subtract, 15, 6),
            Triple(MathOperation.Subtract, 30, 12),
            Triple(MathOperation.Multiply, 4, 5),
            Triple(MathOperation.Multiply, 5, 6),
        ).forEach { (operation, left, right) ->
            val start = CountingState.forRound(operation, left, right)
            val taps = if (operation == MathOperation.Subtract) right else start.objectCount
            val done = tapAll(start, taps)
            assertTrue("$operation $left/$right not complete", done.complete)
            assertEquals("$operation $left/$right", operation.answer(left, right), done.counted)
        }
    }
}
