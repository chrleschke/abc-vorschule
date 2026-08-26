package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlotFillMorphTest {
    @Test
    fun atRestNothingIsTransformed() {
        assertEquals(1f, SlotFillMorph.scaleX(SlotFillMorph.AtRest), 1e-6f)
        assertEquals(1f, SlotFillMorph.scaleY(SlotFillMorph.AtRest), 1e-6f)
        assertEquals(0f, SlotFillMorph.wobble(SlotFillMorph.AtRest), 1e-6f)
    }

    @Test
    fun theMomentOfSnappingInSquishesHorizontally() {
        // Federstart: settle = 0, also wobble = -1.
        assertEquals(1f - SlotFillMorph.SquishX, SlotFillMorph.scaleX(0f), 1e-6f)
        assertEquals(1f + SlotFillMorph.StretchY, SlotFillMorph.scaleY(0f), 1e-6f)
    }

    /**
     * Gegenläufig ist Pflicht, sonst liest der Morph als Zoom statt als
     * Quetschung (PRODUCT_PRINCIPLES §10).
     */
    @Test
    fun yAlwaysRunsAgainstX() {
        listOf(0f, 0.3f, 0.75f, 1f, 1.2f).forEach { settle ->
            val x = SlotFillMorph.scaleX(settle) - 1f
            val y = SlotFillMorph.scaleY(settle) - 1f
            assertTrue("settle=$settle: x=$x y=$y", x * y <= 0f)
        }
    }

    /** Der Überschwinger der Feder dehnt in X — die Quetschung federt hinaus. */
    @Test
    fun theOvershootStretchesHorizontally() {
        assertTrue(SlotFillMorph.scaleX(1.2f) > 1f)
        assertTrue(SlotFillMorph.scaleY(1.2f) < 1f)
    }

    @Test
    fun theCornerSoftensOnFillAndReturnsToTheComponentsRadius() {
        val resting = 22f
        assertEquals(
            resting + SlotFillMorph.CornerGainDp,
            SlotFillMorph.cornerRadius(
                settle = 0f,
                resting = resting,
                gain = SlotFillMorph.CornerGainDp,
                min = SlotFillMorph.MinCornerRadiusDp,
            ),
            1e-6f,
        )
        assertEquals(
            resting,
            SlotFillMorph.cornerRadius(
                settle = SlotFillMorph.AtRest,
                resting = resting,
                gain = SlotFillMorph.CornerGainDp,
                min = SlotFillMorph.MinCornerRadiusDp,
            ),
            1e-6f,
        )
    }

    /** Der Überschwinger darf die Ecke nicht unter die Untergrenze ziehen. */
    @Test
    fun theCornerNeverGoesBelowItsFloor() {
        val tightest = SlotFillMorph.cornerRadius(
            settle = 2f,
            resting = 16f,
            gain = SlotFillMorph.CornerGainDp,
            min = SlotFillMorph.MinCornerRadiusDp,
        )
        assertEquals(SlotFillMorph.MinCornerRadiusDp, tightest, 1e-6f)
    }
}
