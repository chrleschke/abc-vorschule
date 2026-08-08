package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordFrameSizingTest {
    /** ExerciseStage caps content at 420.dp and pads 12.dp per side. */
    private val stageWidth = 396f

    /** A 360.dp phone, minus AbcDimens.screenHorizontal and the stage padding. */
    private val narrowPhone = 296f

    /** The longest authored word: Häuser = Hä | u | s | e | r. */
    private val longestWordFrames = 5

    private fun rowWidth(available: Float, frameCount: Int): Float =
        WordFrameSizing.frameWidthDp(available, frameCount) * frameCount +
            WordFrameSizing.gapDp(available, frameCount) * (frameCount - 1)

    @Test
    fun fiveFramesFitTheStageInsteadOfOverflowing() {
        val used = rowWidth(stageWidth, longestWordFrames)
        assertTrue("row needs $used dp of $stageWidth dp", used <= stageWidth)
    }

    @Test
    fun fiveFramesAlsoFitANarrowPhone() {
        val used = rowWidth(narrowPhone, longestWordFrames)
        assertTrue("row needs $used dp of $narrowPhone dp", used <= narrowPhone)
    }

    @Test
    fun everyAuthoredWordLengthFitsBothWidths() {
        (1..longestWordFrames).forEach { count ->
            listOf(stageWidth, narrowPhone).forEach { available ->
                val used = rowWidth(available, count)
                assertTrue("$count frames need $used dp of $available dp", used <= available)
            }
        }
    }

    @Test
    fun theGapOnlyTightensWhenTheFramesWouldBreachTheFloor() {
        // Roomy: keep the comfortable gap.
        assertEquals(WordFrameSizing.MaxGapDp, WordFrameSizing.gapDp(stageWidth, 3), 0.01f)
        // Tight: give the space to the frames instead.
        assertEquals(WordFrameSizing.MinGapDp, WordFrameSizing.gapDp(narrowPhone, 5), 0.01f)
    }

    @Test
    fun shortWordsKeepTheComfortableMaximumWidth() {
        assertEquals(WordFrameSizing.MaxFrameDp, WordFrameSizing.frameWidthDp(stageWidth, 2), 0.01f)
    }

    @Test
    fun framesNeverShrinkBelowTheTouchTargetFloor() {
        // Absurdly narrow: we clamp at the hit-box minimum and accept the overflow
        // rather than rendering frames a child cannot hit.
        val width = WordFrameSizing.frameWidthDp(available = 100f, frameCount = 5)
        assertEquals(WordFrameSizing.MinFrameDp, width, 0.01f)
        assertTrue("floor must clear the 56dp spec minimum", WordFrameSizing.MinFrameDp >= 56f)
    }

    @Test
    fun aDegenerateFrameCountFallsBackToTheMaximum() {
        assertEquals(WordFrameSizing.MaxFrameDp, WordFrameSizing.frameWidthDp(stageWidth, 0), 0.01f)
    }

    @Test
    fun glyphShrinksForAMultiCharacterBlockInANarrowFrame() {
        val wide = WordFrameSizing.glyphSp(WordFrameSizing.MaxFrameDp, longestDisplayChars = 1)
        val narrow = WordFrameSizing.glyphSp(50f, longestDisplayChars = 3)
        assertTrue("$narrow should be smaller than $wide", narrow < wide)
    }

    @Test
    fun glyphNeverLeavesTheLegibleRange() {
        val tiny = WordFrameSizing.glyphSp(WordFrameSizing.MinFrameDp, longestDisplayChars = 3)
        assertTrue(tiny >= WordFrameSizing.MinGlyphSp)
        val huge = WordFrameSizing.glyphSp(400f, longestDisplayChars = 1)
        assertEquals(WordFrameSizing.MaxGlyphSp, huge, 0.01f)
    }

    // --- glyphs must fit their frame at every system font scale -----------------

    /** Rendered advance of the longest block inside one frame, in dp. */
    private fun renderedGlyphWidth(glyphSp: Float, chars: Int, scale: Float): Float =
        glyphSp * scale * WordFrameSizing.GlyphAspect * chars

    @Test
    fun theRenderedGlyphFitsItsFittedFrameAtEveryFontScale() {
        // The live regression this pins: the one-word Satz-Architekt rendered its
        // "Mama" silhouette as "Mam" at font_scale 1.3, because glyphSp ignored the
        // scale and the MinGlyphSp floor overflowed the fixed frame width.
        // Shapes: 4-char one-word peg ("Mama"), 2-char Wort-Bauer block ("Hä"),
        // 1-char block — each against every real device stage width.
        val shapes = listOf(1 to 4, 5 to 2, 5 to 1, 2 to 3)
        listOf(1f, 1.3f, 2f).forEach { scale ->
            (deviceStageWidths + stageWidth).forEach { available ->
                shapes.forEach { (frames, chars) ->
                    val share = WordFrameSizing.frameWidthDp(available, frames)
                    val glyph = WordFrameSizing.glyphSp(share, chars, scale)
                    val fitted = WordFrameSizing.fittedFrameWidthDp(share, glyph, chars, scale)
                    assertTrue(
                        "$chars chars in $frames frames at scale $scale on ${available}dp: " +
                            "${renderedGlyphWidth(glyph, chars, scale)}dp glyph in ${fitted}dp frame",
                        renderedGlyphWidth(glyph, chars, scale) <=
                            fitted - 2 * WordFrameSizing.FramePaddingDp + 0.01f,
                    )
                    assertTrue(
                        "frame must stay hittable",
                        fitted >= WordFrameSizing.MinFrameDp,
                    )
                    assertTrue("glyph must stay legible", glyph >= WordFrameSizing.MinGlyphSp)
                }
            }
        }
    }

    @Test
    fun theFittedFrameOnlyWidensWhenTheLegibilityFloorWins() {
        // Comfortable case: glyph was solved against the share, frame unchanged.
        val share = WordFrameSizing.frameWidthDp(stageWidth, 2)
        val glyph = WordFrameSizing.glyphSp(share, 1, 1f)
        assertEquals(
            share,
            WordFrameSizing.fittedFrameWidthDp(share, glyph, 1, 1f),
            0.01f,
        )
        // Floor case: "Mama" in one 84dp peg at scale 1.3 — 20sp floor renders
        // ~75dp of glyph, so the frame must widen past the share instead of clipping.
        val floored = WordFrameSizing.glyphSp(WordFrameSizing.MaxFrameDp, 4, 1.3f)
        assertEquals(WordFrameSizing.MinGlyphSp, floored, 0.01f)
        assertTrue(
            WordFrameSizing.fittedFrameWidthDp(WordFrameSizing.MaxFrameDp, floored, 4, 1.3f) >
                WordFrameSizing.MaxFrameDp,
        )
    }

    @Test
    fun glyphSpKeepsItsPreScaleValuesAtFontScaleOne() {
        // fontScale defaults to 1f, so every existing call site keeps its numbers.
        assertEquals(
            WordFrameSizing.glyphSp(WordFrameSizing.MaxFrameDp, 2),
            WordFrameSizing.glyphSp(WordFrameSizing.MaxFrameDp, 2, 1f),
            0.001f,
        )
    }

    // --- row wrapping for long words ----------------------------------------
    // Reuses the `stageWidth` (396dp usable stage width) declared above.

    @Test
    fun sixSegmentsStayOnOneRow() {
        // "Häuser" -> H·ä·u·s·e·r, the longest word in the current content.
        assertEquals(1, WordFrameSizing.rowCount(stageWidth, 6))
        assertEquals(6, WordFrameSizing.segmentsPerRow(stageWidth, 6))
    }

    @Test
    fun eightSegmentsWrapIntoTwoBalancedRows() {
        // "Xylophon" -> X·y·l·o·p·h·o·n. Tappability beats staying on one line.
        assertEquals(2, WordFrameSizing.rowCount(stageWidth, 8))
        assertEquals(4, WordFrameSizing.segmentsPerRow(stageWidth, 8))
    }

    @Test
    fun sevenSegmentsWrapAndTheFirstRowTakesTheExtra() {
        assertEquals(2, WordFrameSizing.rowCount(stageWidth, 7))
        assertEquals(4, WordFrameSizing.segmentsPerRow(stageWidth, 7))
    }

    @Test
    fun frameWidthNeverDropsBelowTheTouchFloorAfterWrapping() {
        val perRow = WordFrameSizing.segmentsPerRow(stageWidth, 8)
        assertTrue(WordFrameSizing.frameWidthDp(stageWidth, perRow) >= WordFrameSizing.MinFrameDp)
    }

    @Test
    fun aSingleSegmentNeedsOneRow() {
        assertEquals(1, WordFrameSizing.rowCount(stageWidth, 1))
        assertEquals(1, WordFrameSizing.segmentsPerRow(stageWidth, 1))
    }

    @Test
    fun anEmptyWordDoesNotDivideByZero() {
        assertEquals(1, WordFrameSizing.rowCount(stageWidth, 0))
        assertEquals(1, WordFrameSizing.segmentsPerRow(stageWidth, 0))
    }

    @Test
    fun aVeryNarrowStageStillFitsOneSegmentPerRow() {
        assertEquals(1, WordFrameSizing.maxPerRow(20f))
        assertEquals(4, WordFrameSizing.rowCount(20f, 4))
    }

    // --- real device widths, real segment counts ------------------------------
    // The wrap threshold is width-derived, not count-derived, so testing only the
    // 420dp-and-wider stage hid that "Häuser" already wraps on a Pixel 7. Measured
    // through TaskShell (20dp/side) into ExerciseStage (widthIn 420 + 12dp/side).

    /** 360dp -> 296, Pixel 7/8 393dp -> 329, 412dp -> 348, >=420dp -> 396. */
    private val deviceStageWidths = listOf(296f, 329f, 348f, 396f)

    /** Six segments need 6 x 56 + 5 x 4 = 356dp, so they only fit the widest stage. */
    private fun expectedMaxPerRow(available: Float): Int = if (available >= 356f) 6 else 5

    @Test
    fun everyRealDeviceWidthKeepsTheTouchFloorForEveryRealSegmentCount() {
        deviceStageWidths.forEach { available ->
            // 2..6 is what the authored content produces: "m·a" up to "H·ä·u·s·e·r".
            (2..6).forEach { segments ->
                val perRow = WordFrameSizing.segmentsPerRow(available, segments)
                val width = WordFrameSizing.frameWidthDp(available, perRow)
                assertTrue(
                    "$segments segments on ${available}dp give ${width}dp frames",
                    width >= WordFrameSizing.MinFrameDp,
                )
                val expectedRows = if (segments <= expectedMaxPerRow(available)) 1 else 2
                assertEquals(
                    "$segments segments on ${available}dp",
                    expectedRows,
                    WordFrameSizing.rowCount(available, segments),
                )
            }
        }
    }

    @Test
    fun sixSegmentsWrapOnEveryPhoneNarrowerThanTheFullStage() {
        // "Häuser" -> H·ä·u·s·e·r on a Pixel 7: 356dp of frames do not fit 329dp,
        // and clickability beats staying on one line.
        assertEquals(5, WordFrameSizing.maxPerRow(329f))
        assertEquals(2, WordFrameSizing.rowCount(329f, 6))
        assertEquals(3, WordFrameSizing.segmentsPerRow(329f, 6))
        // ... and only stays on one row once the stage reaches the 420dp cap.
        assertEquals(6, WordFrameSizing.maxPerRow(396f))
        assertEquals(1, WordFrameSizing.rowCount(396f, 6))
    }

    // --- row height ----------------------------------------------------------

    @Test
    fun oneRowKeepsTheFullTouchHeight() {
        assertEquals(
            WordFrameSizing.MaxRowHeightDp,
            WordFrameSizing.rowHeightDp(segmentCount = 6, segmentsPerRow = 6),
            0.01f,
        )
    }

    @Test
    fun aWrappedWordUsesTheReducedRowHeight() {
        // Two 80dp rows overflow the prompt block on a short 360x640dp device, and
        // ExerciseStage neither scrolls nor clips.
        assertEquals(
            WordFrameSizing.WrappedRowHeightDp,
            WordFrameSizing.rowHeightDp(segmentCount = 6, segmentsPerRow = 3),
            0.01f,
        )
        assertTrue(WordFrameSizing.WrappedRowHeightDp < WordFrameSizing.MaxRowHeightDp)
    }

    @Test
    fun everyRowHeightStillClearsTheTouchFloor() {
        deviceStageWidths.forEach { available ->
            (2..6).forEach { segments ->
                val perRow = WordFrameSizing.segmentsPerRow(available, segments)
                val height = WordFrameSizing.rowHeightDp(segments, perRow)
                assertTrue(
                    "$segments segments on ${available}dp give ${height}dp rows",
                    height >= WordFrameSizing.MinFrameDp,
                )
            }
        }
    }

    @Test
    fun aDegenerateSegmentCountKeepsTheFullRowHeight() {
        assertEquals(
            WordFrameSizing.MaxRowHeightDp,
            WordFrameSizing.rowHeightDp(segmentCount = 0, segmentsPerRow = 1),
            0.01f,
        )
    }

    // --- Wort-Detektiv sizing: the word must read as a word --------------------

    /** What one Wort-Detektiv row costs for the given segments. */
    private fun wordRowWidth(glyphSp: Float, segments: List<String>): Float =
        segments.map { WordFrameSizing.wordSegmentWidthDp(glyphSp, it.length) }.sum() +
            WordFrameSizing.WordSegmentGapDp * (segments.size - 1)

    private fun wordGlyphSp(available: Float, segments: List<String>): Float {
        val perRow = WordFrameSizing.segmentsPerRow(available, segments.size)
        return WordFrameSizing.wordGlyphSp(
            available = available,
            segmentsPerRow = perRow,
            longestDisplayChars = segments.maxOf { it.length },
            rowHeightDp = WordFrameSizing.rowHeightDp(segments.size, perRow),
        )
    }

    @Test
    fun theWordSitsTighterThanTheWortBauersSlots() {
        // The bug: "Mama" was drawn as 84dp frames 12dp apart, so 33dp glyphs stood
        // 96dp from each other and read as loose letters. The hit boxes now hug.
        val mama = listOf("M", "a", "m", "a")
        val perRow = WordFrameSizing.segmentsPerRow(stageWidth, mama.size)
        val tight = WordFrameSizing.wordSegmentWidthDp(wordGlyphSp(stageWidth, mama), 1) +
            WordFrameSizing.WordSegmentGapDp
        val loose = WordFrameSizing.frameWidthDp(stageWidth, perRow) +
            WordFrameSizing.gapDp(stageWidth, perRow)
        assertTrue("pitch ${tight}dp must beat the Wort-Bauer's ${loose}dp", tight < loose * 0.7f)
    }

    @Test
    fun theWordGetsABiggerGlyphThanTheWortBauersTiles() {
        val mama = listOf("M", "a", "m", "a")
        assertTrue(
            "the word must outgrow the ${WordFrameSizing.MaxGlyphSp}sp tile glyph",
            wordGlyphSp(stageWidth, mama) > WordFrameSizing.MaxGlyphSp,
        )
    }

    @Test
    fun aMultiCharacterSegmentIsWiderThanASingleOneInTheSameWord() {
        // "Sch·u·h": a uniform width would give `u` and `h` the box `Sch` needs and
        // scatter the word all over again.
        val schuh = listOf("Sch", "u", "h")
        val glyph = wordGlyphSp(stageWidth, schuh)
        assertTrue(
            WordFrameSizing.wordSegmentWidthDp(glyph, 3) >
                WordFrameSizing.wordSegmentWidthDp(glyph, 1),
        )
    }

    @Test
    fun everyRealWordFitsEveryRealDeviceWidth() {
        // Every segment shape the authored content produces, longest segment first.
        val words = listOf(
            listOf("M", "a", "m", "a"),
            listOf("a", "m"),
            listOf("Sch", "u", "h"),
            listOf("A", "pf", "e", "l"),
            listOf("K", "l", "ei", "d"),
            listOf("H", "ä", "u", "s", "e", "r"),
            listOf("X", "y", "l", "o", "p", "h", "o", "n"),
        )
        deviceStageWidths.forEach { available ->
            words.forEach { word ->
                val perRow = WordFrameSizing.segmentsPerRow(available, word.size)
                val glyph = wordGlyphSp(available, word)
                word.chunked(perRow).forEach { row ->
                    val used = wordRowWidth(glyph, row)
                    assertTrue(
                        "$row at ${glyph}sp needs ${used}dp of ${available}dp",
                        used <= available,
                    )
                }
                word.forEach { segment ->
                    assertTrue(
                        "segment '$segment' must stay hittable",
                        WordFrameSizing.wordSegmentWidthDp(glyph, segment.length) >=
                            WordFrameSizing.MinFrameDp,
                    )
                }
            }
        }
    }

    @Test
    fun aWrappedRowShrinksItsGlyphInsteadOfClippingIt() {
        val full = WordFrameSizing.wordGlyphSp(
            available = stageWidth,
            segmentsPerRow = 3,
            longestDisplayChars = 1,
            rowHeightDp = WordFrameSizing.MaxRowHeightDp,
        )
        val wrapped = WordFrameSizing.wordGlyphSp(
            available = stageWidth,
            segmentsPerRow = 3,
            longestDisplayChars = 1,
            rowHeightDp = WordFrameSizing.WrappedRowHeightDp,
        )
        assertTrue("$wrapped must fit the shorter row", wrapped < full)
        assertTrue(wrapped <= WordFrameSizing.WrappedRowHeightDp)
    }

    @Test
    fun aLargeSystemFontScaleStillRendersInsideTheRow() {
        // sp grows with the system font scale, dp does not — an uncapped 54sp glyph
        // at scale 1.3 renders ~71dp tall and breaks out of the 80dp row.
        listOf(1f, 1.3f, 2f).forEach { scale ->
            val glyph = WordFrameSizing.wordGlyphSp(
                available = stageWidth,
                segmentsPerRow = 4,
                longestDisplayChars = 1,
                rowHeightDp = WordFrameSizing.MaxRowHeightDp,
                fontScale = scale,
            )
            val rendered = glyph * scale
            assertTrue(
                "at scale $scale the glyph renders ${rendered}dp",
                rendered <= WordFrameSizing.MaxRowHeightDp * WordFrameSizing.WordGlyphHeightFraction + 0.01f,
            )
            val width = WordFrameSizing.wordSegmentWidthDp(glyph, 1, scale)
            assertTrue("hit box must stay hittable at scale $scale", width >= WordFrameSizing.MinFrameDp)
        }
    }

    @Test
    fun theTargetLabelStaysSmallerThanTheWordAtEverySystemFontScale() {
        // The regression this pins: the word was capped against fontScale and the
        // label was not, so on a device at font_scale 1.3 a 38sp label and a 42sp
        // word both rendered ~50dp and the hierarchy collapsed.
        val mama = listOf("M", "a", "m", "a")
        val perRow = WordFrameSizing.segmentsPerRow(stageWidth, mama.size)
        listOf(0.85f, 1f, 1.15f, 1.3f, 1.5f, 2f).forEach { scale ->
            val word = WordFrameSizing.wordGlyphSp(
                available = stageWidth,
                segmentsPerRow = perRow,
                longestDisplayChars = 1,
                rowHeightDp = WordFrameSizing.MaxRowHeightDp,
                fontScale = scale,
            ) * scale
            val label = WordFrameSizing.targetLabelSp(scale) * scale
            assertEquals(
                "at scale $scale the label must stay at ${WordFrameSizing.TargetLabelFraction} of the word",
                WordFrameSizing.TargetLabelFraction,
                label / word,
                0.01f,
            )
        }
    }

    @Test
    fun anAbsurdlyNarrowStageStopsAtTheLegibleMinimum() {
        assertEquals(
            WordFrameSizing.MinGlyphSp,
            WordFrameSizing.wordGlyphSp(
                available = 40f,
                segmentsPerRow = 3,
                longestDisplayChars = 3,
                rowHeightDp = WordFrameSizing.WrappedRowHeightDp,
            ),
            0.01f,
        )
    }
}
