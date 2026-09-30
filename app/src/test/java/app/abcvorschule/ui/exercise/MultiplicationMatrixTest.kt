package app.abcvorschule.ui.exercise

import org.junit.Assert.assertTrue
import org.junit.Test

class MultiplicationMatrixTest {
    /** Der Lehrplan geht bis 30: fünf Türme mit je sechs Blöcken reichen dafür. */
    @Test
    fun capsAllowTheFullCurriculumRangeUpToThirty() {
        assertTrue(MultiplicationMatrix.MaxRows * MultiplicationMatrix.MaxColumns >= 30)
    }
}
