package app.abcvorschule.ui.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BurstGeometryTest {
    @Test
    fun `liefert count offsets gleichmaessig auf dem kreis`() {
        val offsets = BurstGeometry.sparkOffsets(count = 8, progress = 1f, radiusPx = 100f)
        assertEquals(8, offsets.size)
        offsets.forEach { o ->
            assertEquals(100f, hypot(o.x, o.y), 0.6f)
        }
    }

    @Test
    fun `progress skaliert den radius`() {
        val half = BurstGeometry.sparkOffsets(count = 4, progress = 0.5f, radiusPx = 100f)
        half.forEach { o -> assertEquals(50f, hypot(o.x, o.y), 0.6f) }
    }

    @Test
    fun `progress null haelt alle funken im zentrum`() {
        BurstGeometry.sparkOffsets(count = 6, progress = 0f, radiusPx = 100f)
            .forEach { o -> assertTrue(hypot(o.x, o.y) < 0.001f) }
    }
}
