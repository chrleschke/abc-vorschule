package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderedPlacementTest {
    private val mama = listOf("Ma", "ma")
    private val sentence = listOf("Oma", "ist", "da")

    @Test
    fun blockIsCorrectOnlyAtItsOwnIndex() {
        assertTrue(OrderedPlacement.isCorrectPlacement(0, "Ma", mama))
        assertFalse(OrderedPlacement.isCorrectPlacement(1, "Ma", mama))
        assertTrue(OrderedPlacement.isCorrectPlacement(1, "ma", mama))
    }

    @Test
    fun indexOutsideTheSolutionIsNeverCorrect() {
        assertFalse(OrderedPlacement.isCorrectPlacement(5, "Ma", mama))
        assertFalse(OrderedPlacement.isCorrectPlacement(-1, "Ma", mama))
    }

    @Test
    fun repeatedBlocksAreAcceptedAtEveryMatchingIndex() {
        // Mama needs "ma" twice; a repeated syllable must not confuse the check.
        val placed = mapOf(0 to "Ma", 1 to "ma")
        assertTrue(OrderedPlacement.isSolved(placed, mama))
    }

    @Test
    fun partialPlacementIsNotSolved() {
        assertFalse(OrderedPlacement.isSolved(mapOf(0 to "Oma"), sentence))
        assertFalse(OrderedPlacement.isSolved(mapOf(0 to "Oma", 2 to "da"), sentence))
        assertTrue(OrderedPlacement.isSolved(mapOf(0 to "Oma", 1 to "ist", 2 to "da"), sentence))
    }

    @Test
    fun wrongContentIsNotSolvedEvenWhenAllSlotsAreFull() {
        assertFalse(OrderedPlacement.isSolved(mapOf(0 to "ma", 1 to "Ma"), mama))
    }

    @Test
    fun nextEmptyIndexWalksLeftToRight() {
        assertEquals(0, OrderedPlacement.nextEmptyIndex(emptyMap(), 3))
        assertEquals(1, OrderedPlacement.nextEmptyIndex(mapOf(0 to "Oma"), 3))
        assertEquals(2, OrderedPlacement.nextEmptyIndex(mapOf(0 to "Oma", 1 to "ist"), 3))
        assertNull(OrderedPlacement.nextEmptyIndex(mapOf(0 to "a", 1 to "b", 2 to "c"), 3))
    }
}
