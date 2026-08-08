package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyllableFrameSizingTest {
    /**
     * Real stage widths, measured through TaskShell (20dp/side) into ExerciseStage
     * (widthIn 420 + 12dp/side): 360dp phone, 352dp, Pixel-class 393dp, 411dp —
     * same convention as WordFrameSizingTest. 296 is the width the old fixed row
     * (min. 108 + 120 + 108 = 336dp, with "sch" 366dp) demonstrably overflowed.
     */
    private val stageWidths = listOf(296f, 320f, 360f, 411f)

    private val fontScales = listOf(1f, 1.3f, 2f)

    /** Every segment shape the shipped syllable_merge content produces, longest first. */
    private val pairs = listOf(
        "sch" to "u",
        "qu" to "a",
        "m" to "ei",
        "st" to "e",
        "a" to "m",
        "m" to "a",
    )

    @Test fun compoundSyllablesReceiveMoreRoomThanSingleLetters() {
        assertTrue(SyllableFrameSizing.widthDp("Schu") > SyllableFrameSizing.widthDp("u"))
    }

    @Test fun widthDpKeepsItsShippedValues() {
        // SoundPositionTrainer still sizes its wagons off this — the shipped
        // numbers (chars x 34 + 36, floor 108) must not drift.
        assertEquals(108f, SyllableFrameSizing.widthDp("u"), 0.01f)
        assertEquals(138f, SyllableFrameSizing.widthDp("sch"), 0.01f)
        assertEquals(172f, SyllableFrameSizing.widthDp("Schu"), 0.01f)
    }

    @Test
    fun everyPairFitsEveryStageWidthAtEveryFontScale() {
        stageWidths.forEach { available ->
            fontScales.forEach { scale ->
                pairs.forEach { (left, right) ->
                    val layout = SyllableFrameSizing.mergeLayout(available, left, right, scale)
                    val used = layout.leftWidthDp + layout.gapDp + layout.rightWidthDp
                    assertTrue(
                        "'$left'+'$right' at scale $scale needs ${used}dp of ${available}dp",
                        used <= available + 0.01f,
                    )
                }
            }
        }
    }

    @Test
    fun tilesKeepTheirComfortableFloorAndTheTrackKeepsItsMinimum() {
        stageWidths.forEach { available ->
            fontScales.forEach { scale ->
                pairs.forEach { (left, right) ->
                    val layout = SyllableFrameSizing.mergeLayout(available, left, right, scale)
                    assertTrue(layout.leftWidthDp >= SyllableFrameSizing.MinWidthDp)
                    assertTrue(layout.rightWidthDp >= SyllableFrameSizing.MinWidthDp)
                    assertTrue(
                        "gap ${layout.gapDp}dp must stay a usable slide track",
                        layout.gapDp >= SyllableFrameSizing.MinGapDp,
                    )
                    assertTrue(layout.gapDp <= SyllableFrameSizing.MaxGapDp)
                }
            }
        }
    }

    @Test
    fun theRenderedGlyphFitsInsideItsTile() {
        stageWidths.forEach { available ->
            fontScales.forEach { scale ->
                pairs.forEach { (left, right) ->
                    val layout = SyllableFrameSizing.mergeLayout(available, left, right, scale)
                    listOf(left to layout.leftWidthDp, right to layout.rightWidthDp)
                        .forEach { (label, width) ->
                            val rendered =
                                label.length * layout.glyphSp * scale * SyllableFrameSizing.GlyphAspect
                            assertTrue(
                                "'$label' at scale $scale renders ${rendered}dp in ${width}dp",
                                rendered <= width + 0.01f,
                            )
                        }
                    assertTrue(layout.glyphSp >= WordFrameSizing.MinGlyphSp)
                }
            }
        }
    }

    @Test
    fun theEffectiveGlyphNeverOutgrowsItsBaseSize() {
        // capEffectiveSize pattern: sp x fontScale stays at the fontScale-1.0 size,
        // so the tiles do not swell on the font_scale-1.3 test device.
        fontScales.forEach { scale ->
            val layout = SyllableFrameSizing.mergeLayout(360f, "m", "a", scale)
            assertTrue(layout.glyphSp * scale <= SyllableFrameSizing.MaxGlyphSp + 0.01f)
        }
    }

    @Test
    fun theTrackCentresOnTheMeetingPointNotTheStage() {
        // Equal tiles: meeting point IS the row centre.
        val even = SyllableFrameSizing.mergeLayout(360f, "m", "a", 1f)
        assertEquals(0f, even.trackOffsetDp, 0.01f)
        // "sch" + "u": the left tile is wider, so the gap's middle sits right of
        // the row's middle by half the width difference — the track must follow,
        // or the inward light wave points beside where the tiles actually meet.
        val uneven = SyllableFrameSizing.mergeLayout(360f, "sch", "u", 1f)
        assertEquals(
            (uneven.leftWidthDp - uneven.rightWidthDp) / 2f,
            uneven.trackOffsetDp,
            0.01f,
        )
        assertTrue(uneven.trackOffsetDp > 0f)
    }

    @Test
    fun aRoomyStageKeepsTheComfortableTrack() {
        val layout = SyllableFrameSizing.mergeLayout(396f, "m", "a", 1f)
        assertEquals(SyllableFrameSizing.MaxGapDp, layout.gapDp, 0.01f)
        assertEquals(SyllableFrameSizing.MaxGlyphSp, layout.glyphSp, 0.01f)
    }

    @Test
    fun theNarrowStageGivesUpTrackBeforeItShrinksTheGlyph() {
        // 296dp with "sch"+"u": comfort widths 138+108 fit once the track yields
        // (gap 50dp) — the glyph must still be at its full base size.
        val layout = SyllableFrameSizing.mergeLayout(296f, "sch", "u", 1f)
        assertEquals(SyllableFrameSizing.MaxGlyphSp, layout.glyphSp, 0.01f)
        assertTrue(layout.gapDp < SyllableFrameSizing.MaxGapDp)
    }

    @Test
    fun anUnauthoredMonsterPairDegradesToTheLegibilityFloorInsteadOfVanishing() {
        // "schwer"+"chen" (10 chars) is beyond anything authored. At 296dp and
        // scale 2.0 even MinGapDp does not save the fit — then MinGlyphSp wins
        // over the fit, the same trade WordFrameSizing.glyphSp makes: an
        // illegible tile is worse than an overflowing one.
        val layout = SyllableFrameSizing.mergeLayout(296f, "schwer", "chen", 2f)
        assertEquals(WordFrameSizing.MinGlyphSp, layout.glyphSp, 0.01f)
        assertTrue(layout.leftWidthDp >= SyllableFrameSizing.MinWidthDp)
        assertTrue(layout.rightWidthDp >= SyllableFrameSizing.MinWidthDp)
        assertEquals(SyllableFrameSizing.MinGapDp, layout.gapDp, 0.01f)
    }

    @Test
    fun theResultTileFitsEveryStageAtEveryFontScale() {
        val results = listOf("schu", "qua", "mei", "am", "ma")
        stageWidths.forEach { available ->
            fontScales.forEach { scale ->
                results.forEach { label ->
                    val spec = SyllableFrameSizing.resultFloe(available, label, scale)
                    assertTrue(
                        "'$label' at scale $scale needs ${spec.widthDp}dp of ${available}dp",
                        spec.widthDp <= available + 0.01f,
                    )
                    assertTrue(
                        label.length * spec.glyphSp * scale * SyllableFrameSizing.GlyphAspect <=
                            spec.widthDp + 0.01f,
                    )
                }
            }
        }
    }
}
