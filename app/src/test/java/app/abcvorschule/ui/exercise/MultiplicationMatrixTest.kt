package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiplicationMatrixTest {
    @Test
    fun onlyTheFirstRowIsConcrete() {
        assertTrue(MultiplicationMatrix.isConcreteRow(0))
        (1 until MultiplicationMatrix.MaxRows).forEach { row ->
            assertFalse("row $row must be a ghost placeholder", MultiplicationMatrix.isConcreteRow(row))
        }
    }

    @Test
    fun ghostRowsStayVisibleButClearlySecondary() {
        assertTrue(MultiplicationMatrix.GhostAlpha > 0f)
        assertTrue(MultiplicationMatrix.GhostAlpha < 0.5f)
    }

    @Test
    fun wideGridsShrinkTheirEmoji() {
        assertTrue(
            MultiplicationMatrix.emojiSizeSp(MultiplicationMatrix.MaxColumns) <
                MultiplicationMatrix.emojiSizeSp(2),
        )
    }

    @Test
    fun gridCapsAllowTheFullCurriculumRangeUpToThirty() {
        assertEquals(30, MultiplicationMatrix.MaxRows * MultiplicationMatrix.MaxColumns)
    }
}
