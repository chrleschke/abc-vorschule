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
    fun shortLabelsAreUntouchedUpToTheTestDeviceScale() {
        // fontScale 1.3 is the test device. This used to hold for every authored
        // length, back when MaxLabelWidthDp was 120dp — but that width ignored the
        // corner glyph and let the longest labels run under it (see
        // longLabelsGiveUpSizeToStayOutOfTheCornerGlyphColumn). Up to five
        // characters nothing changed: those fit the 88dp band at 1.3 with room to
        // spare, and the emoji row is untouched at every length.
        for (label in labelsByLength.filter { it.length <= 5 }) {
            for (scale in listOf(1.0f, 1.3f)) {
                assertEquals(
                    "\"$label\" at scale $scale must keep its authored size",
                    PathSignLabel.BaseLabelSp,
                    PathSignLabel.labelFontSp(label, scale),
                    0.01f,
                )
            }
        }
        for (scale in listOf(1.0f, 1.3f)) {
            assertEquals(PathSignLabel.BaseEmojiSp, PathSignLabel.emojiFontSp(scale), 0.01f)
        }
    }

    @Test
    fun longLabelsGiveUpSizeToStayOutOfTheCornerGlyphColumn() {
        // The promise this replaces ("nothing changes at 1.0 and 1.3, for any
        // length") rested on MaxLabelWidthDp = 120dp, which subtracted the ring and
        // the rounded corners but not the 24dp column the corner star/lock occupies.
        // Centred, 120dp spans x = 8..128 on the 136dp board and the glyph's box is
        // x = 112..128, so "C y x qu" reached x ~ 126 at scale 1.3 and sat visibly
        // under the glyph at MaxBoardFontScale. The band is 88dp now, and the two
        // longest authored labels pay for it with ~1% of their size at scale 1.0 and
        // more above it. Asserted rather than left implicit, so the trade stays
        // visible instead of being quietly reverted.
        for (label in listOf("Sch ch+", "C y x qu")) {
            assertTrue(
                "\"$label\" is the case the gutter costs size",
                PathSignLabel.labelFontSp(label, 1.0f) < PathSignLabel.BaseLabelSp,
            )
            for (scale in scales) {
                val renderedDp =
                    PathSignLabel.labelFontSp(label, scale) * scale * PathSignLabel.widthEm(label)
                assertEquals(
                    "\"$label\" at scale $scale fills the band exactly",
                    PathSignLabel.MaxLabelWidthDp,
                    renderedDp,
                    0.01f,
                )
            }
        }
    }

    @Test
    fun noLabelReachesIntoTheCornerGlyphColumn() {
        // The label column is centred on the board, so the right edge of the
        // rendered line has to stop where the corner glyph's column begins.
        val boardWidth = PathSignDimens.BoardWidth.value
        val gutterStart = boardWidth - PathSignLabel.CornerGlyphGutterDp
        for (label in labelsByLength) {
            for (scale in scales) {
                val renderedDp =
                    PathSignLabel.labelFontSp(label, scale) * scale * PathSignLabel.widthEm(label)
                val rightEdge = (boardWidth + renderedDp) / 2f
                assertTrue(
                    "\"$label\" at scale $scale ends at x=$rightEdge, the corner " +
                        "glyph starts at x=$gutterStart",
                    rightEdge <= gutterStart + 0.01f,
                )
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
