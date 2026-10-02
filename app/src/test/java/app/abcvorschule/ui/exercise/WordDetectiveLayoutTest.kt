package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordDetectiveLayoutTest {
    /** Vorschübe von Silbo Fibel Bold in em (hmtx / unitsPerEm), damit die Tests echte Wörter rechnen. */
    private val em = mapOf(
        'a' to 0.613f, 'b' to 0.593f, 'c' to 0.499f, 'd' to 0.618f, 'e' to 0.542f, 'f' to 0.391f,
        'g' to 0.579f, 'h' to 0.603f, 'i' to 0.312f, 'j' to 0.327f, 'k' to 0.556f, 'l' to 0.312f,
        'm' to 0.859f, 'n' to 0.608f, 'o' to 0.566f, 'p' to 0.603f, 'r' to 0.486f, 's' to 0.505f,
        't' to 0.432f, 'u' to 0.608f, 'ü' to 0.608f, 'A' to 0.725f, 'M' to 0.906f, 'S' to 0.607f,
        'T' to 0.618f,
    )
    private val hyphen = 0.437f

    private fun advances(segments: List<String>) = segments.map { s -> s.fold(0f) { sum, c -> sum + em.getValue(c) } }

    private fun layout(segments: List<String>, available: Float, breaks: Set<Int> = emptySet()) =
        WordDetectiveLayout.layout(advances(segments), hyphen, breaks, available)

    /** Usable stage on the Moto edge 60 pro (434dp − 2×20 − 2×12). */
    private val moto = 370f

    /** Usable stage on a 360dp phone. */
    private val narrow = 296f

    private val mama = listOf("M", "a", "m", "a")
    private val schneemann = listOf("Sch", "n", "e", "e", "m", "a", "n", "n")
    private val taschenlampe = listOf("T", "a", "sch", "e", "n", "l", "a", "m", "p", "e")
    private val sonnenblume = listOf("S", "o", "n", "n", "e", "n", "b", "l", "u", "m", "e")

    @Test
    fun aShortWordGetsTheLargestGlyphOnOneLine() {
        val result = layout(mama, moto)
        assertEquals(1, result.lines.size)
        assertEquals(WordDetectiveLayout.MaxGlyphDp, result.glyphDp, 0.01f)
    }

    @Test
    fun schneemannFitsOneLineOnTheTestPhone() {
        val result = layout(schneemann, moto, breaks = setOf(4))
        assertEquals(1, result.lines.size)
        assertTrue(result.glyphDp >= WordDetectiveLayout.MinGlyphDp)
        assertNull(result.breakBefore)
    }

    @Test
    fun theLongestWordInThePackStaysOnOneLineOnTheTestPhone() {
        val result = layout(taschenlampe, moto, breaks = setOf(5))
        assertEquals(1, result.lines.size)
        assertTrue(result.glyphDp >= WordDetectiveLayout.MinGlyphDp)
    }

    @Test
    fun schIsOneSegmentWithOneHitBox() {
        val result = layout(schneemann, moto)
        val boxes = result.lines.single().segments
        assertEquals(schneemann.size, boxes.size)
        // "Sch" is wider than "n": the grapheme keeps its own glyph width.
        assertTrue(boxes[0].glyphWidth > boxes[1].glyphWidth * 2f)
        assertTrue(boxes[0].hitWidth > boxes[1].hitWidth * 2f)
    }

    @Test
    fun theVisibleWordKeepsItsNaturalSpacing() {
        // No gap and no stretch between glyphs: each starts where the previous ends.
        layout(taschenlampe, moto).lines.single().segments.zipWithNext().forEach { (a, b) ->
            assertEquals(a.glyphX + a.glyphWidth, b.glyphX, 0.01f)
        }
    }

    @Test
    fun theVisibleWordIsCentred() {
        val line = layout(mama, moto).lines.single().segments
        val left = line.first().glyphX
        val right = line.last().glyphX + line.last().glyphWidth
        assertEquals(moto - right, left, 0.01f)
    }

    @Test
    fun narrowSegmentsGetInvisibleExtraHitWidth() {
        // "Müll" at full size: both l are ~22dp wide, under the 36dp floor.
        val segments = listOf("M", "ü", "l", "l")
        val result = layout(segments, moto)
        val boxes = result.lines.single().segments
        assertTrue(boxes[2].glyphWidth < WordDetectiveLayout.MinHitWidthDp)
        boxes.forEach { assertTrue("${it.index}: ${it.hitWidth}", it.hitWidth >= WordDetectiveLayout.MinHitWidthDp - 0.01f) }
    }

    @Test
    fun hitBoxesTileWithoutOverlapAndStayOnTheStage() {
        listOf(mama, schneemann, taschenlampe, listOf("i", "ch"), listOf("M", "ü", "l", "l")).forEach { word ->
            listOf(moto, narrow).forEach { available ->
                layout(word, available).lines.forEach { line ->
                    line.segments.zipWithNext().forEach { (a, b) ->
                        assertEquals("$word", a.hitX + a.hitWidth, b.hitX, 0.01f)
                    }
                    assertTrue(line.segments.first().hitX >= -0.01f)
                    val last = line.segments.last()
                    assertTrue(last.hitX + last.hitWidth <= available + 0.01f)
                }
            }
        }
    }

    @Test
    fun everyHitBoxContainsItsGlyphCentre() {
        listOf(mama, schneemann, taschenlampe, sonnenblume, listOf("i", "ch")).forEach { word ->
            listOf(moto, narrow).flatMap { layout(word, it, breaks = setOf(5)).lines }.flatMap { it.segments }.forEach { box ->
                assertTrue("$word ${box.index}", box.glyphCenterX in box.hitX..(box.hitX + box.hitWidth))
            }
        }
    }

    @Test
    fun aWideSegmentWithoutNarrowNeighboursKeepsExactlyItsGlyphWidth() {
        val boxes = layout(mama, moto).lines.single().segments
        // M (65dp) next to a (44dp): nothing to lend or borrow.
        assertEquals(boxes[0].glyphWidth, boxes[0].hitWidth, 0.01f)
    }

    @Test
    fun theRowIsAtLeastTheTouchFloorTall() {
        val result = layout(taschenlampe, narrow, breaks = setOf(5))
        assertTrue(result.rowHeightDp >= WordDetectiveLayout.MinHitHeightDp)
        assertTrue(result.rowHeightDp >= result.lineHeightDp)
    }

    @Test
    fun aLongWordBreaksOnlyAtTheSyllableBoundaryWithAHyphen() {
        // 10 segments × 36dp = 360dp of hit boxes do not fit 296dp: break Taschen-/lampe.
        val result = layout(taschenlampe, narrow, breaks = setOf(5))
        assertEquals(2, result.lines.size)
        assertEquals(5, result.breakBefore)
        assertNotNull(result.lines[0].hyphenX)
        assertNull(result.lines[1].hyphenX)
        assertTrue(result.glyphDp >= WordDetectiveLayout.MinGlyphDp)
    }

    @Test
    fun theBreakLandsAtTheAllowedBoundaryEvenIfAnotherWouldBalanceBetter() {
        val result = layout(sonnenblume, narrow, breaks = setOf(6))
        assertEquals(6, result.breakBefore)
    }

    @Test
    fun theMostBalancedAllowedBreakWins() {
        // Both are allowed; after "Taschen" the two lines are close to even, after "T" not.
        val result = layout(taschenlampe, narrow, breaks = setOf(1, 5))
        assertEquals(5, result.breakBefore)
    }

    @Test
    fun withoutAReliableBoundaryTheWordShrinksInsteadOfBreaking() {
        val result = layout(sonnenblume, 240f, breaks = emptySet())
        assertEquals(1, result.lines.size)
        assertTrue(result.glyphDp < WordDetectiveLayout.MinGlyphDp)
        assertTrue(result.glyphDp >= WordDetectiveLayout.FloorGlyphDp)
    }

    @Test
    fun tooManyHitBoxesForTheLineShareItWithoutLeavingTheStage() {
        // 11 × 36dp do not fit 296dp, and without a boundary there is no second line:
        // the boxes get narrower, but still tile the stage and hold their own glyph.
        val line = layout(sonnenblume, narrow, breaks = emptySet()).lines.single().segments
        assertTrue(line.first().hitX >= -0.01f)
        assertTrue(line.last().hitX + line.last().hitWidth <= narrow + 0.01f)
        line.forEach { assertTrue(it.glyphCenterX in it.hitX..(it.hitX + it.hitWidth)) }
    }

    @Test
    fun aBreakOutsideTheWordIsIgnored() {
        assertEquals(1, layout(sonnenblume, narrow, breaks = setOf(0, 11, 42)).lines.size)
    }

    @Test
    fun twoStarsFrameASingleLetterThreeAGrapheme() {
        val result = layout(schneemann, moto)
        val boxes = result.lines.single().segments
        assertEquals(3, WordDetectiveLayout.frameStars(boxes[0], 3, result, upperOfTwo = false).size)
        assertEquals(2, WordDetectiveLayout.frameStars(boxes[1], 1, result, upperOfTwo = false).size)
    }

    @Test
    fun framingStarsSitOutsideTheGlyphCorners() {
        val result = layout(listOf("T", "o", "m", "a", "t", "e"), moto)
        val t = result.lines.single().segments[0]
        val (topLeft, bottomRight) = WordDetectiveLayout.frameStars(t, 1, result, upperOfTwo = false)
        assertTrue(topLeft.x < t.glyphX)
        assertTrue(topLeft.y < result.baselineDp - WordDetectiveLayout.CapHeightFactor * result.glyphDp)
        assertTrue(bottomRight.x > t.glyphX + t.glyphWidth)
        assertTrue(bottomRight.y > result.baselineDp)
    }

    @Test
    fun onTheUpperOfTwoLinesTheStarsMirrorAwayFromTheLowerLine() {
        val result = layout(taschenlampe, narrow, breaks = setOf(5))
        val t = result.lines[0].segments[0]
        val normal = WordDetectiveLayout.frameStars(t, 1, result, upperOfTwo = false)
        val mirrored = WordDetectiveLayout.frameStars(t, 1, result, upperOfTwo = true)
        // The star that sat under the baseline now sits above the glyph.
        assertTrue(normal[1].y > result.baselineDp)
        assertTrue(mirrored[1].y < result.baselineDp - WordDetectiveLayout.XHeightFactor * result.glyphDp)
    }

    @Test
    fun silhouettesKeepFullSizeClearOfTheTelescopeOnASmallPhone() {
        // 320dp screen, telescope zone 92dp: two full silhouettes still fit between.
        val row = WordDetectiveLayout.silhouetteRow(2, widthDp = 320f, keepOutDp = 92f)
        assertEquals(WordDetectiveLayout.SilhouetteDp, row.sizeDp, 0.01f)
        assertTrue(2 * row.sizeDp + row.gapDp <= 320f - 2 * 92f)
    }

    @Test
    fun manySilhouettesShrinkRatherThanReachTheTelescope() {
        val row = WordDetectiveLayout.silhouetteRow(4, widthDp = 320f, keepOutDp = 92f)
        assertTrue(row.sizeDp < WordDetectiveLayout.SilhouetteDp)
        assertTrue(row.sizeDp >= WordDetectiveLayout.MinSilhouetteDp)
        assertTrue(4 * row.sizeDp + 3 * row.gapDp <= 320f - 2 * 92f + 0.01f)
    }

    // --- Höhe ----------------------------------------------------------------

    private val label = 56f

    private fun groupHeight(fit: WordDetectiveLayout.VerticalFit, lines: Int, glyph: Float) =
        label + fit.labelToWordDp + lines * maxOf(glyph * WordDetectiveLayout.LineHeightFactor, 56f) +
            fit.wordToStarsDp + fit.silhouetteCapDp

    @Test
    fun aTallPhoneKeepsTheMockupProportions() {
        val fit = WordDetectiveLayout.verticalFit(availableHeightDp = 600f, labelHeightDp = label, lineCount = 1)
        assertEquals(WordDetectiveLayout.LabelToWordDp, fit.labelToWordDp, 0.01f)
        assertEquals(WordDetectiveLayout.WordToStarsDp, fit.wordToStarsDp, 0.01f)
        assertEquals(WordDetectiveLayout.MaxGlyphDp, fit.glyphCapDp, 0.01f)
    }

    @Test
    fun aShortPhoneGivesUpAirBeforeTheWord() {
        // 360×640dp: roughly 256dp for the whole group.
        val fit = WordDetectiveLayout.verticalFit(256f, label, 1)
        assertEquals(WordDetectiveLayout.MaxGlyphDp, fit.glyphCapDp, 0.01f)
        assertTrue(fit.labelToWordDp < WordDetectiveLayout.LabelToWordDp)
        assertTrue(groupHeight(fit, 1, fit.glyphCapDp) <= 256.01f)
    }

    @Test
    fun aBrokenWordOnAShortPhoneStillFitsAndKeepsTheTouchHeight() {
        val fit = WordDetectiveLayout.verticalFit(256f, label, 2)
        assertTrue(groupHeight(fit, 2, fit.glyphCapDp) <= 256.01f)
        assertTrue(fit.labelToWordDp >= WordDetectiveLayout.MinLabelToWordDp - 0.01f)
        assertTrue(fit.silhouetteCapDp >= WordDetectiveLayout.CompactSilhouetteDp - 0.01f)
    }

    @Test
    fun theHeightCapShrinksTheGlyphButNeverChangesTheLineChoiceTowardsMoreLines() {
        val capped = WordDetectiveLayout.layout(advances(schneemann), hyphen, setOf(4), moto, maxGlyphDp = 36f)
        assertEquals(1, capped.lines.size)
        assertEquals(36f, capped.glyphDp, 0.01f)
    }
}
