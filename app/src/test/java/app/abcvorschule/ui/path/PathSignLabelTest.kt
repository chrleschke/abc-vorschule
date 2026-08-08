package app.abcvorschule.ui.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathSignLabelTest {
    // One pack-shaped label per length 1..8 — "C y x qu" is the longest authored
    // nodeLabel, "ä ö ü" / "Sch ch+" / "J z eu" are real ones too.
    private val labelsByLength = listOf(
        "M",
        "Ei",
        "Sch",
        "M a+",
        "ä ö ü",
        "J z eu",
        "Sch ch+",
        "C y x qu",
    )

    private val scales = listOf(1.0f, 1.3f, 2.0f)

    @Test
    fun everyLabelLengthRendersInsideTheBoardWidthAtEveryScale() {
        for (label in labelsByLength) {
            for (scale in scales) {
                val renderedDp =
                    PathSignLabel.labelFontSp(label, scale) * scale * PathSignLabel.widthEm(label)
                assertTrue(
                    "\"$label\" at scale $scale renders ~${renderedDp}dp wide, " +
                        "board allows ${PathSignLabel.MaxLabelWidthDp}dp",
                    renderedDp <= PathSignLabel.MaxLabelWidthDp + 0.01f,
                )
            }
        }
    }

    @Test
    fun sizesAreUntouchedUpToTheTestDeviceScale() {
        // fontScale 1.3 is the test device — the cap must not change anything
        // there, for any authored label length.
        for (label in labelsByLength) {
            for (scale in listOf(1.0f, 1.3f)) {
                assertEquals(
                    "\"$label\" at scale $scale must keep its authored size",
                    PathSignLabel.BaseLabelSp,
                    PathSignLabel.labelFontSp(label, scale),
                    0.01f,
                )
                assertEquals(PathSignLabel.BaseEmojiSp, PathSignLabel.emojiFontSp(scale), 0.01f)
            }
        }
    }

    @Test
    fun theLongLabelsShrinkAtScale2InsteadOfClipping() {
        // "C y x qu" uncapped at 2.0 would be ~4em * 44dp ≈ 178dp on a 136dp
        // board — the finding this guards against.
        assertTrue(PathSignLabel.labelFontSp("C y x qu", 2.0f) < PathSignLabel.BaseLabelSp)
        assertTrue(PathSignLabel.labelFontSp("Sch ch+", 2.0f) < PathSignLabel.BaseLabelSp)
        // A single glyph only hits the vertical cap: rendered size holds at the
        // max board scale rather than growing to 44dp.
        assertEquals(
            PathSignLabel.BaseLabelSp * PathSignLabel.MaxBoardFontScale,
            PathSignLabel.labelFontSp("M", 2.0f) * 2.0f,
            0.01f,
        )
    }

    @Test
    fun labelNeverExceedsItsAuthoredSize() {
        for (label in labelsByLength) {
            for (scale in scales) {
                assertTrue(PathSignLabel.labelFontSp(label, scale) <= PathSignLabel.BaseLabelSp + 0.01f)
                assertTrue(PathSignLabel.emojiFontSp(scale) <= PathSignLabel.BaseEmojiSp + 0.01f)
            }
        }
    }

    @Test
    fun labelPlusEmojiRowFitTheBoardHeightAtEveryScale() {
        // Uncapped, scale 2.0 stacks ~90dp of text on the 86dp board; the cap has
        // to keep the whole column inside it — with a little margin, because the
        // line-height estimate is exactly that, an estimate.
        for (label in labelsByLength) {
            for (scale in scales) {
                val height = PathSignLabel.boardColumnHeightDp(label, scale)
                assertTrue(
                    "\"$label\" at scale $scale needs ~${height}dp of the " +
                        "${PathSignDimens.BoardHeight.value}dp board",
                    height <= PathSignDimens.BoardHeight.value - 4f,
                )
            }
        }
    }

    @Test
    fun emojiSizeIsHeldAtTheBoardCapPastIt() {
        // At 2.0 the rendered emoji stays where the 1.6 cap puts it.
        assertEquals(
            PathSignLabel.BaseEmojiSp * PathSignLabel.MaxBoardFontScale,
            PathSignLabel.emojiFontSp(2.0f) * 2.0f,
            0.01f,
        )
    }
}
