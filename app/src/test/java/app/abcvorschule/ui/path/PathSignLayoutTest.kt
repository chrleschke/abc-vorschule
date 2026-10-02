package app.abcvorschule.ui.path

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.LessonSigns
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PathSignLayoutTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun everyShippedSignFitsItsLayoutBox() {
        // The swing in PathGeometry is sized against this box (PathGeometryTest), so
        // a tower wider than it would run into the trail.
        pack.lessons.forEach { lesson ->
            val glyphs = LessonSigns.forLesson(pack, lesson).blocks.map { it.glyph }
            val width = PathSignLayout.towerWidthDp(glyphs)
            assertTrue("${lesson.id} $glyphs is ${width}dp wide", width <= PathSignLayout.WidthDp)
        }
    }

    @Test
    fun wideSoundsGetAWiderBlockAndShortOnesAreSquare() {
        assertEquals(PathSignLayout.BlockDp, PathSignLayout.blockWidthDp("M"), 0f)
        assertEquals(PathSignLayout.BlockDp, PathSignLayout.blockWidthDp("Ei"), 0f)
        assertTrue(PathSignLayout.blockWidthDp("Sch") > PathSignLayout.BlockDp)
    }

    @Test
    fun threeAndFourSoundsStackIntoASecondRow() {
        assertEquals(listOf(listOf("M", "A")), PathSignLayout.rows(listOf("M", "A")))
        assertEquals(listOf(listOf("J", "Z"), listOf("Eu")), PathSignLayout.rows(listOf("J", "Z", "Eu")))
        assertEquals(1, PathSignLayout.rowCount(1))
        assertEquals(1, PathSignLayout.rowCount(2))
        assertEquals(2, PathSignLayout.rowCount(4))
        assertTrue(PathSignLayout.towerHeightDp(3) > PathSignLayout.towerHeightDp(2))
        assertEquals(PathSignLayout.towerHeightDp(4), PathSignDimens.MaxHeight.value, 0f)
    }

    @Test
    fun noShippedLessonNeedsAThirdRow() {
        pack.lessons.forEach { lesson ->
            val n = LessonSigns.forLesson(pack, lesson).blocks.size
            assertTrue("${lesson.id} has $n sounds", PathSignLayout.rowCount(n) <= 2)
        }
    }

    @Test
    fun cubeRestsWithTheLetterFacingAndEndsWithThePictureFacing() {
        val depth = 44f
        val width = 60f
        // At rest the front sits centred, the side edge-on at the right edge.
        assertEquals(0f, CubeTurn.frontTranslationX(0f, depth), 0.001f)
        assertEquals(width / 2f, CubeTurn.sideTranslationX(0f, width), 0.001f)
        assertTrue(CubeTurn.frontVisible(0f))
        assertFalse(CubeTurn.sideVisible(0f))
        // Turned: the side is centred, the front has swung off to the left.
        assertEquals(0f, CubeTurn.sideTranslationX(90f, width), 0.001f)
        assertEquals(-depth / 2f, CubeTurn.frontTranslationX(90f, depth), 0.001f)
        assertFalse(CubeTurn.frontVisible(90f))
        assertTrue(CubeTurn.sideVisible(90f))
        // Lifted mid-turn, flat at both ends.
        assertEquals(1f, CubeTurn.lift(0f), 0.001f)
        assertEquals(1f, CubeTurn.lift(90f), 0.001f)
        assertTrue(CubeTurn.lift(45f) < 1f)
    }
}
