package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskPromptSizingTest {
    @Test
    fun fontScaleOneKeepsTheShippedSizes() {
        assertEquals(34, TaskPromptSizing.titleSp(muted = false, fontScale = 1f))
        assertEquals(28, TaskPromptSizing.titleSp(muted = true, fontScale = 1f))
        assertEquals(34, TaskPromptSizing.titleLineHeightSp(1f))
    }

    @Test
    fun theEffectiveTitleNeverOutgrowsItsBaseSize() {
        // The regression this pins: an uncapped 34sp title rendered as a 68dp line
        // at font_scale 2.0, inside a prompt block that neither scrolls nor clips.
        listOf(1f, 1.3f, 2f).forEach { scale ->
            assertTrue(
                "title at scale $scale",
                TaskPromptSizing.titleSp(muted = false, fontScale = scale) * scale <=
                    TaskPromptSizing.TitleSp + 0.01f,
            )
            assertTrue(
                "muted title at scale $scale",
                TaskPromptSizing.titleSp(muted = true, fontScale = scale) * scale <=
                    TaskPromptSizing.MutedTitleSp + 0.01f,
            )
            assertTrue(
                "line height at scale $scale",
                TaskPromptSizing.titleLineHeightSp(scale) * scale <=
                    TaskPromptSizing.TitleLineHeightSp + 0.01f,
            )
        }
    }

    @Test
    fun theTaskPictureKeepsItsShippedSize() {
        assertEquals(84, TaskPromptSizing.pictureSp(1f))
        assertEquals(TaskPromptSizing.PictureSp, TaskPromptSizing.pictureSp(1f))
    }

    @Test
    fun theTaskPictureNeverOutgrowsItsBaseSize() {
        // Die Regression: 84sp ungedeckelt rendern bei font_scale 2.0 als ~168dp
        // und schieben den Aufgabenblock (der weder scrollt noch clippt) auf.
        listOf(1f, 1.3f, 2f).forEach { scale ->
            assertTrue(
                "picture at scale $scale",
                TaskPromptSizing.pictureSp(scale) * scale <= TaskPromptSizing.PictureSp + 0.01f,
            )
        }
    }

    @Test
    fun aSmallerSystemScaleIsLeftAlone() {
        // Below 1.0 the system asked for smaller text — capping is not zooming.
        assertEquals(34, TaskPromptSizing.titleSp(muted = false, fontScale = 0.85f))
    }

    @Test
    fun anAbsurdScaleStillReturnsADrawableSize() {
        assertTrue(TaskPromptSizing.titleSp(muted = false, fontScale = 100f) >= 1)
    }
}
