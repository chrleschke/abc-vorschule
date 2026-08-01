package app.abcvorschule.ui.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfettiGeometryTest {
    @Test
    fun `gleicher seed liefert identische stuecke`() {
        assertEquals(
            ConfettiGeometry.pieces(count = 40, seed = 7L),
            ConfettiGeometry.pieces(count = 40, seed = 7L),
        )
    }

    @Test
    fun `stuecke starten oberhalb des screens und fallen durch`() {
        val p = ConfettiGeometry.pieces(count = 20, seed = 1L)
        p.forEach { piece ->
            assertTrue(ConfettiGeometry.yFraction(piece, progress = 0f) < 0f)
            assertTrue(ConfettiGeometry.yFraction(piece, progress = 1f) > 1f)
        }
    }

    @Test
    fun `werte liegen in gueltigen bereichen`() {
        ConfettiGeometry.pieces(count = 30, seed = 3L).forEach { piece ->
            assertTrue(piece.xFraction in 0f..1f)
            assertTrue(piece.colorIndex in 0..3)
            assertTrue(piece.delayFraction in 0f..0.5f)
        }
    }
}
