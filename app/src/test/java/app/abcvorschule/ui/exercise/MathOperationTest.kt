package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathOperationTest {
    @Test fun operationsProduceTheirOwnAnswers() {
        assertEquals(5, MathOperation.Subtract.answer(8, 3))
        assertEquals(12, MathOperation.Multiply.answer(3, 4))
    }

    @Test fun quantitiesAboveTenUseTheCompactSymbol() {
        assertFalse(QuantityRepresentation.isSymbolic(10))
        assertTrue(QuantityRepresentation.isSymbolic(11))
    }
}
