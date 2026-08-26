package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Grenzen und Richtung des Jagd-Kachel-Morphs — die Kurvenzeiten steckt Compose,
 * die Werte darf ein Test festhalten. */
class HuntTileMorphTest {
    @Test
    fun tileNeverGrowsBeyondTenPercent() {
        // Das ist die eigentliche Zusage des Extremfalls: Halten wächst weiter,
        // aber der Deckel liegt bei +10 %.
        assertEquals(1.10f, HuntTileMorph.scale(HuntTileMorph.MaxInflate, exit = 0f), 0.0001f)
        assertTrue(HuntTileMorph.PressPuff < HuntTileMorph.MaxInflate)
        assertTrue(HuntTileMorph.MaxInflate <= 0.10f)
    }

    @Test
    fun releaseCollapsesBelowRestSize() {
        val collapsed = HuntTileMorph.scale(-HuntTileMorph.CollapseUndershoot, exit = 0f)
        assertTrue("Kollaps muss unter Ruhegröße gehen, sonst kein Plopp", collapsed < 1f)
        assertTrue(collapsed > 0.9f)
    }

    @Test
    fun popAwayShrinksAndFadesToNothing() {
        assertEquals(0f, HuntTileMorph.scale(HuntTileMorph.MaxInflate, exit = 1f), 0.0001f)
        assertEquals(0f, HuntTileMorph.alpha(1f), 0.0001f)
        assertEquals(1f, HuntTileMorph.alpha(0f), 0.0001f)
        // Halbwegs weggeploppt heißt halbwegs klein und halbwegs durchsichtig.
        assertTrue(HuntTileMorph.scale(0f, exit = 0.5f) in 0.49f..0.51f)
    }

    @Test
    fun scaleStaysPositiveForOutOfRangeInput() {
        assertTrue(HuntTileMorph.scale(inflate = -2f, exit = 0f) >= 0f)
        assertTrue(HuntTileMorph.scale(inflate = 0f, exit = 1.4f) >= 0f)
        assertEquals(1f, HuntTileMorph.alpha(-0.3f), 0.0001f)
    }

    @Test
    fun pressProgressTracksInflationAndIgnoresTheUndershoot() {
        assertEquals(0f, HuntTileMorph.pressProgress(0f), 0.0001f)
        assertEquals(0.6f, HuntTileMorph.pressProgress(HuntTileMorph.PressPuff), 0.0001f)
        assertEquals(1f, HuntTileMorph.pressProgress(HuntTileMorph.MaxInflate), 0.0001f)
        // Der Unterschwinger beim Loslassen ist Ruhe, keine negative Schattierung.
        assertEquals(0f, HuntTileMorph.pressProgress(-HuntTileMorph.CollapseUndershoot), 0.0001f)
    }

    @Test
    fun shadingDeepensWithPressAndTheGlossGivesWay() {
        val rest = 0f
        val full = 1f
        // Rand wird satter, Innenschatten stärker: Tiefe, nicht Zoom.
        assertTrue(HuntTileMorph.rimAlpha(full) > HuntTileMorph.rimAlpha(rest))
        assertTrue(HuntTileMorph.shadeAlpha(full) > HuntTileMorph.shadeAlpha(rest))
        // Kern hellt leicht ab, Glanzpunkt wird schwächer und kleiner.
        assertTrue(HuntTileMorph.coreAlpha(full) < HuntTileMorph.coreAlpha(rest))
        assertTrue(HuntTileMorph.glossAlpha(full) < HuntTileMorph.glossAlpha(rest))
        assertTrue(HuntTileMorph.glossRadiusFactor(full) < HuntTileMorph.glossRadiusFactor(rest))
    }

    @Test
    fun washStaysInsideTheContrastBudget() {
        // Der Glyph (WarmInk) sitzt auf dieser Wäsche; der Rand der Kachel bleibt
        // volldeckend und trägt die 3:1-Grenze. Deshalb darf die Wäsche nirgends
        // über 0,40 gehen, und im Mittel bleibt sie bei den früheren 0,22.
        listOf(0f, 0.5f, 1f).forEach { p ->
            assertTrue(HuntTileMorph.rimAlpha(p) <= 0.40f)
            assertTrue(HuntTileMorph.coreAlpha(p) >= 0.10f)
        }
        val meanAtRest = (HuntTileMorph.coreAlpha(0f) + HuntTileMorph.rimAlpha(0f)) / 2f
        assertEquals(0.21f, meanAtRest, 0.02f)
    }
}
