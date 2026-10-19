package app.abcvorschule.ui.path

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathArrowMotionTest {
    private val tips = listOf(Offset(100f, 0f), Offset(300f, 200f), Offset(120f, 400f))

    @Test
    fun atRestTheArrowStandsExactlyOverItsSign() {
        tips.forEachIndexed { i, tip -> assertEquals(tip, PathArrowMotion.tip(i.toFloat(), tips, arcPx = 50f)) }
    }

    @Test
    fun halfwayItIsAboveTheStraightLine() {
        val mid = PathArrowMotion.tip(0.5f, tips, arcPx = 50f)
        assertEquals(200f, mid.x, 0.01f)
        assertEquals(100f - 50f, mid.y, 0.01f)
    }

    @Test
    fun aMarkerOutsideThePathIsClampedToItsEnds() {
        assertEquals(tips.last(), PathArrowMotion.tip(7f, tips, arcPx = 50f))
        assertEquals(tips.first(), PathArrowMotion.tip(-1f, tips, arcPx = 50f))
    }

    @Test
    fun theBounceStartsOnTheGroundAndSquashesThere() {
        // After a hop the bounce clock restarts at the landing: no jump into mid-air.
        assertEquals(0f, PathArrowMotion.bounce(0f), 0.0001f)
        assertEquals(1f, PathArrowMotion.squash(0f), 0.0001f)
        val top = PathArrowMotion.BouncePeriodS / 2f
        assertEquals(1f, PathArrowMotion.bounce(top), 0.0001f)
        assertEquals(0f, PathArrowMotion.squash(top), 0.0001f)
    }

    @Test
    fun theSignJumpsOnlyAtTheStartOfTheCelebration() {
        assertEquals(0f, PathCelebration.signLift(0f), 0f)
        assertTrue(PathCelebration.signLift(0.2f) > 0.9f)
        assertEquals(0f, PathCelebration.signLift(0.6f), 0f)
        assertEquals(0f, PathCelebration.signLift(1f), 0f)
    }
}
