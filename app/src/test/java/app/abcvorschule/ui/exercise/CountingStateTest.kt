package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CountingStateTest {
    /** Tippt die ersten [count] antippbaren Objekte der Reihe nach an — also genau
     * das, was der Puls-Hinweis dem Kind vorschlägt. */
    private fun tapAlong(start: CountingState, count: Int): CountingState =
        (0 until count).fold(start) { state, _ ->
            state.nextIndex?.let(state::tap) ?: state
        }

    @Test
    fun nothingIsMirroredIntoTheAnswerFieldBeforeTheFirstTap() {
        // Sonst stünde bei Plus sofort eine 0 im Feld und bei Minus sofort der linke
        // Operand — das Kind könnte die Startzahl absenden, ohne etwas getan zu haben.
        assertNull(CountingState.forRound(MathOperation.Add, 7, 8).counted)
        assertNull(CountingState.forRound(MathOperation.Subtract, 15, 6).counted)
        assertNull(CountingState.forRound(MathOperation.Multiply, 4, 5).counted)
    }

    @Test
    fun plusCountsUpAcrossBothOperands() {
        val start = CountingState.forRound(MathOperation.Add, 7, 8)
        assertEquals(1, start.tap(0).counted)
        assertEquals(7, tapAlong(start, 7).counted)
        val done = tapAlong(start, 15)
        assertEquals(15, done.counted)
        assertTrue(done.complete)
    }

    @Test
    fun minusCountsDownFromTheStartingQuantity() {
        val start = CountingState.forRound(MathOperation.Subtract, 15, 6)
        assertEquals(14, start.tap(start.framedFrom!!).counted)
        assertEquals(9, tapAlong(start, 6).counted)
    }

    @Test
    fun minusOnlyLetsTheChildTouchWhatIsSupposedToGoAway() {
        // Der Deckel ist keine Regel mehr, sondern die Struktur: es gibt schlicht nur
        // sechs antippbare Objekte, also kann das Kind nicht zu viel wegnehmen.
        val start = CountingState.forRound(MathOperation.Subtract, 15, 6)
        assertEquals(9, start.framedFrom)
        (0 until 9).forEach { assertFalse("index $it", start.isTappable(it)) }
        (9 until 15).forEach { assertTrue("index $it", start.isTappable(it)) }

        // Ein Tipp auf ein bleibendes Objekt tut nichts — kein Fehler, keine Meldung.
        assertEquals(start, start.tap(0))
        assertTrue(tapAlong(start, 6).complete)
    }

    @Test
    fun plusFramesTheSecondOperandButLetsTheChildTapEverything() {
        val start = CountingState.forRound(MathOperation.Add, 7, 8)
        assertEquals(7, start.framedFrom)
        assertFalse(start.isFramed(6))
        assertTrue(start.isFramed(7))
        // Anders als bei Minus ist hier alles antippbar: eingesammelt wird beides.
        (0 until 15).forEach { assertTrue("index $it", start.isTappable(it)) }
    }

    @Test
    fun thePulseFollowsTheNextOpenObject() {
        val plus = CountingState.forRound(MathOperation.Add, 7, 8)
        assertEquals(0, plus.nextIndex)
        assertEquals(1, plus.tap(0).nextIndex)
        // Bei Minus startet der Puls auf dem ersten gerahmten Objekt, nicht auf dem
        // ersten überhaupt — dort ist ja nichts zu tun.
        val minus = CountingState.forRound(MathOperation.Subtract, 15, 6)
        assertEquals(9, minus.nextIndex)
        assertNull(tapAlong(minus, 6).nextIndex)
    }

    @Test
    fun tappingAnAlreadyTappedObjectTakesTheTapBack() {
        val plus = CountingState.forRound(MathOperation.Add, 7, 8).tap(0).tap(1)
        assertEquals(2, plus.counted)
        assertEquals(1, plus.tap(1).counted)
        assertFalse(plus.tap(1).isTapped(1))

        // Auch wenn schon alles weg ist: sonst wäre ein Fehltipp eine Sackgasse.
        val minus = tapAlong(CountingState.forRound(MathOperation.Subtract, 15, 6), 6)
        assertEquals(10, minus.tap(14).counted)
        assertFalse(minus.tap(14).complete)
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
            val done = tapAlong(start, taps)
            assertTrue("$operation $left/$right not complete", done.complete)
            assertEquals("$operation $left/$right", operation.answer(left, right), done.counted)
        }
    }
}
