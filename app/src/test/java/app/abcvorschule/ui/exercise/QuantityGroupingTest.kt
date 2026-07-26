package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Test

class QuantityGroupingTest {
    @Test
    fun fiveIsTwoByTwoPlusOne() {
        assertEquals(listOf(2, 2, 1), QuantityGrouping.clusters(5))
    }

    @Test
    fun fourIsTwoPairs() {
        assertEquals(listOf(2, 2), QuantityGrouping.clusters(4))
    }

    @Test
    fun threeIsPairPlusOne() {
        assertEquals(listOf(2, 1), QuantityGrouping.clusters(3))
    }
}
