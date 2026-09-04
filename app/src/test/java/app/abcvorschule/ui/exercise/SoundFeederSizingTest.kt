package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundFeederSizingTest {
    @Test
    fun twoCreaturesAndTheGapFitTheNarrowestStage() {
        // 320dp-Gerät: ExerciseStage lässt 296dp, zwei Figuren plus 16dp Lücke.
        val width = SoundFeederSizing.creatureWidthDp(296f)
        assertTrue(2 * width + SoundFeederSizing.CreatureGapDp <= 296f)
        assertTrue(width >= SoundFeederSizing.MinCreatureDp)
    }

    @Test
    fun creaturesStopGrowingOnWideStages() {
        assertEquals(SoundFeederSizing.MaxCreatureDp, SoundFeederSizing.creatureWidthDp(396f))
        assertEquals(SoundFeederSizing.MaxCreatureDp, SoundFeederSizing.creatureWidthDp(800f))
    }

    @Test
    fun theBellyGlyphNeverLeavesTheBellyEvenForSchAtFontScaleOnePointThree() {
        val width = SoundFeederSizing.creatureWidthDp(296f)
        val chars = SoundFeederSizing.labelChars("Sch", "sch") // "Sch / sch"
        listOf(1f, 1.3f).forEach { scale ->
            val sp = SoundFeederSizing.bellyGlyphSp(chars, width, scale)
            val renderedWidthDp = chars * SoundFeederSizing.GlyphAdvanceEm * sp * scale
            assertTrue("scale $scale: $renderedWidthDp dp on ${width * SoundFeederSizing.BellyWidthFraction}", renderedWidthDp <= width * SoundFeederSizing.BellyWidthFraction + 0.01f)
            assertTrue(sp >= SoundFeederSizing.MinBellyGlyphSp)
        }
    }

    @Test
    fun aSingleLetterGetsTheFullGlyphSize() {
        assertEquals(SoundFeederSizing.MaxBellyGlyphSp, SoundFeederSizing.bellyGlyphSp(SoundFeederSizing.labelChars("ß", null), 176f, 1f))
    }

    @Test
    fun labelCharsCountsBothFormsAndTheSeparator() {
        assertEquals(5, SoundFeederSizing.labelChars("S", "s")) // "S / s"
        assertEquals(9, SoundFeederSizing.labelChars("Sch", "sch"))
        assertEquals(2, SoundFeederSizing.labelChars("ck", null))
    }

    @Test
    fun theCardStaysAtOneAndAHalfKidTouchAtEverySystemFontScale() {
        // TaskPromptSizing.pictureSp deckelt die *effektive* Bildgröße auf 84dp: die
        // Karte ist bei 1.0, 1.3 und 2.0 praktisch gleich groß (Ganzzahl-Kürzung
        // nimmt bei 1.3 ein paar Zehntel) und nie kleiner als 120dp.
        listOf(1f, 1.3f, 2f).forEach { scale ->
            val size = SoundFeederSizing.cardSizeDp(scale)
            assertTrue("scale $scale: $size", size >= SoundFeederSizing.MinCardDp && size <= 130f)
        }
    }

    @Test
    fun theCardStaysSquareWithAVoiceAndGrowsByOneLineWithout() {
        listOf(1f, 1.3f).forEach { scale ->
            val square = SoundFeederSizing.cardHeightDp(scale, hasWord = false)
            assertEquals("scale $scale", SoundFeederSizing.cardSizeDp(scale), square)
        }
    }

    @Test
    fun theWordUnderTheEmojiGetsItsOwnLineAtEverySystemFontScale() {
        // Ohne deutsche Stimme ist das Wort das Einzige, was ein Erwachsener vorlesen
        // kann — es darf bei 1.0 wie bei 1.3 nicht aus der Karte gedrückt werden.
        listOf(1f, 1.3f, 2f).forEach { scale ->
            val withWord = SoundFeederSizing.cardHeightDp(scale, hasWord = true)
            val emojiBudget = SoundFeederSizing.cardSizeDp(scale)
            val renderedLineDp = SoundFeederSizing.CardWordSp * scale * SoundFeederSizing.CardWordLineEm
            assertTrue(
                "scale $scale: ${withWord - emojiBudget} dp für eine ${renderedLineDp}dp-Zeile",
                withWord - emojiBudget >= renderedLineDp,
            )
        }
    }

    @Test
    fun theWordLineGrowsWithTheSystemFontScale() {
        assertTrue(SoundFeederSizing.cardWordLineDp(1.3f) > SoundFeederSizing.cardWordLineDp(1f))
    }

    @Test
    fun thePileIsAJitteredStackNotARow() {
        assertEquals(0f, SoundFeederSizing.pileWidthDp(0))
        assertEquals(SoundFeederSizing.PileCardWidthDp + 2 * SoundFeederSizing.PileJitterDp, SoundFeederSizing.pileWidthDp(1))
        assertEquals(SoundFeederSizing.pileWidthDp(1), SoundFeederSizing.pileWidthDp(6))
    }

    @Test
    fun pileOffsetsAreDeterministicAndBounded() {
        // Innerhalb eines Spiels (ein Seed) liegt jede Karte fest — sonst zappelte der
        // Haufen bei jeder Neukomposition.
        listOf(0, 4711, -13).forEach { seed ->
            (0 until 7).forEach { index ->
                val (dx, dy, rot) = SoundFeederSizing.pileOffset(index, seed)
                assertEquals(SoundFeederSizing.pileOffset(index, seed), Triple(dx, dy, rot))
                assertTrue(kotlin.math.abs(dx) <= SoundFeederSizing.PileJitterDp)
                assertTrue(kotlin.math.abs(dy) <= SoundFeederSizing.PileJitterDp)
                assertTrue(kotlin.math.abs(rot) <= SoundFeederSizing.PileRotationDeg)
            }
            assertTrue((0 until 7).map { SoundFeederSizing.pileOffset(it, seed) }.toSet().size >= 5)
        }
    }

    @Test
    fun aNewGameLaysThePileOutDifferently() {
        // Nutzerwunsch: „Mache die Karten im Stapel bei jedem neuen Spiel zufällig."
        // Zwei Spiele = zwei Seeds; mindestens eine der sieben Karten muss anders liegen.
        val first = (0 until 7).map { SoundFeederSizing.pileOffset(it, 1) }
        val second = (0 until 7).map { SoundFeederSizing.pileOffset(it, 2) }
        assertTrue(first.indices.any { first[it] != second[it] })
    }
}
