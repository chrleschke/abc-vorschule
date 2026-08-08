package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class TraceProgressTest {
    private val boxSize = 200f

    // Two strokes shaped like a T: horizontal bar, then the vertical stem.
    private val strokes = listOf(
        listOf(TracePoint(0f, 0f), TracePoint(100f, 0f)),
        listOf(TracePoint(50f, 0f), TracePoint(50f, 100f)),
    )
    private val stars = strokes.map { TraceGeometry.starPositions(it, 2) }

    private fun update(state: TraceState, p: TracePoint) =
        TraceProgress.update(state, p, strokes, stars, boxSize)

    @Test
    fun stayingInTheCorridorIsNotFlaggedOffRoad() {
        val result = update(TraceState(), TracePoint(10f, 4f))
        assertFalse(result.offCorridor)
        assertFalse(result.collectedStar)
        // Behind the next star, so the vehicle is free to follow the finger.
        assertFalse(result.ahead)
    }

    @Test
    fun leavingTheCorridorIsFlaggedAndDoesNotAdvance() {
        val result = update(TraceState(), TracePoint(10f, boxSize))
        assertTrue(result.offCorridor)
        assertEquals(TraceState(0, 0), result.state)
    }

    @Test
    fun touchingTheNextStarCollectsItInOrder() {
        val first = update(TraceState(), stars[0][0])
        assertTrue(first.collectedStar)
        assertEquals(TraceState(0, 1), first.state)
    }

    @Test
    fun skippingAheadToALaterStarDoesNotAdvance() {
        // Only the next star counts, so the child cannot shortcut the stroke.
        val result = update(TraceState(), stars[0][1])
        assertFalse(result.collectedStar)
        assertEquals(TraceState(0, 0), result.state)
    }

    @Test
    fun runningAheadOfTheNextStarIsFlaggedWithoutAnOffRoadNudge() {
        // The far end of the stem while star 0 (half way down) is still uncollected.
        val result = update(TraceState(1, 0), TracePoint(50f, 100f))
        assertTrue(result.ahead)
        assertFalse(result.offCorridor)
        assertFalse(result.collectedStar)
        assertEquals(TraceState(1, 0), result.state)
    }

    @Test
    fun aSmallOvershootOfTheNextStarStillCounts() {
        // The pick-up radius is the allowance: a finger a hair past the star collects it
        // instead of being frozen out one pixel before the hit.
        val justPast = TracePoint(50f, 50f + boxSize * TraceProgress.StarHitFraction * 0.9f)
        val result = update(TraceState(1, 0), justPast)
        assertFalse(result.ahead)
        assertTrue(result.collectedStar)
    }

    @Test
    fun tappingTheNextStarIsNeverTreatedAsRunningAhead() {
        // The tap shortcut (R15) feeds the star position itself as the finger, on every
        // stroke and star index — the gate must let all of those through.
        strokes.indices.forEach { strokeIndex ->
            stars[strokeIndex].indices.forEach { starIndex ->
                val state = TraceState(strokeIndex, starIndex)
                val result = update(state, stars[strokeIndex][starIndex])
                assertFalse("$state", result.ahead)
                assertTrue("$state", result.collectedStar)
            }
        }
    }

    @Test
    fun finishingAStrokeMovesToTheNextStrokeAtStarZero() {
        var state = TraceState(0, 1)
        val result = update(state, stars[0][1])
        assertTrue(result.collectedStar)
        assertEquals(TraceState(1, 0), result.state)
        assertFalse(result.glyphDone)
    }

    @Test
    fun collectingTheLastStarOfTheLastStrokeCompletesTheGlyph() {
        val result = update(TraceState(1, 1), stars[1][1])
        assertTrue(result.collectedStar)
        assertTrue(result.glyphDone)
    }

    @Test
    fun updatesAfterCompletionAreInert() {
        val done = TraceState(strokes.size, 0)
        val result = update(done, TracePoint(0f, 0f))
        assertTrue(result.glyphDone)
        assertFalse(result.collectedStar)
        assertFalse(result.offCorridor)
    }

    @Test
    fun corridorScalesWithTheGlyphBox() {
        // The same finger offset is inside a big glyph and outside a small one.
        val offset = TracePoint(10f, 20f)
        assertFalse(TraceProgress.update(TraceState(), offset, strokes, stars, 400f).offCorridor)
        assertTrue(TraceProgress.update(TraceState(), offset, strokes, stars, 60f).offCorridor)
    }

    @Test
    fun aFingerOnTheRoadBesideAStarStillCollectsIt() {
        // Stars sit on the centreline; the corridor is wider than the old Euclidean
        // pick-up radius, so riding the outer edge of the road used to miss.
        val lateral = boxSize * TraceProgress.CorridorFraction * 0.9f
        val besideFirst = TracePoint(stars[0][0].x, stars[0][0].y + lateral)
        val result = update(TraceState(), besideFirst)
        assertFalse(result.offCorridor)
        assertTrue(result.collectedStar)
        assertEquals(TraceState(0, 1), result.state)
    }

    @Test
    fun aFastSwipeThatJumpsPastAStarStillCollectsIt() {
        // Pointer samples can skip the pick-up window; the previous sample bridges it.
        val before = TracePoint(50f, 50f - boxSize * TraceProgress.StarHitFraction * 1.5f)
        val after = TracePoint(50f, 50f + boxSize * TraceProgress.StarHitFraction * 1.5f)
        val result = TraceProgress.update(
            state = TraceState(1, 0),
            finger = after,
            strokes = strokes,
            stars = stars,
            boxSize = boxSize,
            previousFinger = before,
        )
        assertTrue(result.collectedStar)
        assertFalse(result.ahead)
        assertEquals(TraceState(1, 1), result.state)
    }

    @Test
    fun aJumpPastAStarWithoutAPreviousSampleIsStillAhead() {
        val after = TracePoint(50f, 50f + boxSize * TraceProgress.StarHitFraction * 1.5f)
        val result = update(TraceState(1, 0), after)
        assertTrue(result.ahead)
        assertFalse(result.collectedStar)
    }

    @Test
    fun shortDiacriticStrokesAreDetectedForThinnerDrawing() {
        // Umlaut ticks are ~0.04 of the glyph box after the geometry fix.
        assertTrue(TraceProgress.isShortStroke(0.04f * 200f, 200f))
        assertFalse(TraceProgress.isShortStroke(0.5f * 200f, 200f))
    }

    @Test
    fun aVeryShortStrokeGetsASingleStar() {
        // The umlaut ticks of Ä/Ö/Ü are ~0.04 of the glyph box. Four stars there
        // would all sit inside one another's pick-up radius.
        val stars = TraceProgress.starCountFor(strokeLength = 0.04f * 200f, boxSize = 200f)
        assertEquals(1, stars)
    }

    @Test
    fun aLongClosedStrokeGetsManyStars() {
        // letter-o is a closed loop roughly 2.2 box lengths around.
        val stars = TraceProgress.starCountFor(strokeLength = 2.2f * 200f, boxSize = 200f)
        assertTrue("expected more than the old fixed 4, was $stars", stars > 4)
        assertTrue("must stay bounded, was $stars", stars <= TraceProgress.MaxStars)
    }

    @Test
    fun starCountNeverDropsBelowOneEvenForADegenerateStroke() {
        assertEquals(TraceProgress.MinStars, TraceProgress.starCountFor(0f, 200f))
    }

    @Test
    fun consecutiveStarsStayFurtherApartThanThePickUpRadius() {
        val stroke = listOf(TracePoint(0f, 0f), TracePoint(200f, 0f))
        val boxSize = 200f
        val length = TraceGeometry.polylineLength(stroke)
        val stars = TraceGeometry.starPositions(stroke, TraceProgress.starCountFor(length, boxSize))
        val radius = boxSize * TraceProgress.StarHitFraction
        stars.zipWithNext().forEach { (a, b) ->
            val gap = hypot(b.x - a.x, b.y - a.y)
            assertTrue("stars $a and $b are only $gap apart (radius $radius)", gap > radius)
        }
    }

    @Test
    fun singleLettersKeepTheFullSquareAndThickRoad() {
        val fit = TraceProgress.fitFor("A")
        assertEquals(1f, fit.heightScale, 0.001f)
        assertEquals(TraceProgress.CorridorFraction, fit.corridorFraction, 0.001f)
        assertFalse(fit.isCompact)
    }

    @Test
    fun digraphsGetAShorterThinnerFit() {
        val fit = TraceProgress.fitFor("Au")
        assertEquals(TraceProgress.DigraphHeightScale, fit.heightScale, 0.001f)
        assertEquals(TraceProgress.DigraphCorridorFraction, fit.corridorFraction, 0.001f)
        assertTrue(fit.isCompact)
        assertTrue(fit.heightScale < 1f)
        assertTrue(fit.corridorFraction < TraceProgress.CorridorFraction)
    }

    @Test
    fun schGetsAnEvenMoreCompactFitThanAu() {
        val au = TraceProgress.fitFor("Au")
        val sch = TraceProgress.fitFor("Sch")
        assertTrue(sch.heightScale < au.heightScale)
        assertTrue(sch.corridorFraction <= au.corridorFraction)
    }

    @Test
    fun lemmaSpacesDoNotInflateGraphemeWidth() {
        // letter-st is authored as lemma "S t" but is still a digraph.
        assertEquals(2, TraceProgress.graphemeUnits("S t"))
        assertEquals(TraceProgress.DigraphHeightScale, TraceProgress.fitFor("S t").heightScale, 0.001f)
    }

    @Test
    fun umlautLettersStayFullSizeDespiteMultiCharDisplay() {
        // letter-ae displays "Äh" but lemma is Ä — one letter, full road.
        assertEquals(1, TraceProgress.graphemeUnits("Ä"))
        assertFalse(TraceProgress.fitFor("Ä").isCompact)
    }

    @Test
    fun aNarrowerCorridorRejectsFingersTheFullRoadWouldAccept() {
        val offset = TracePoint(10f, 26f) // ~0.13 of boxSize 200 — inside 0.16, outside 0.10
        assertFalse(
            TraceProgress.update(
                TraceState(),
                offset,
                strokes,
                stars,
                boxSize,
                corridorFraction = TraceProgress.CorridorFraction,
            ).offCorridor,
        )
        assertTrue(
            TraceProgress.update(
                TraceState(),
                offset,
                strokes,
                stars,
                boxSize,
                corridorFraction = TraceProgress.DigraphCorridorFraction,
            ).offCorridor,
        )
    }

    // --- Geschlossene Striche (O/Ö/Qu): die Naht macht arcLengthAt bistabil -----

    // Quadratische Schleife, erster Punkt == letzter Punkt, Umfang 400.
    private val loop = listOf(
        TracePoint(0f, 0f),
        TracePoint(100f, 0f),
        TracePoint(100f, 100f),
        TracePoint(0f, 100f),
        TracePoint(0f, 0f),
    )
    private val loopStars = listOf(TraceGeometry.starPositions(loop, 4))

    @Test
    fun seamJitterOnAClosedStrokeCollectsNothing() {
        // Der Finger ruht am Startpunkt der Schleife; 1–2 px Touch-Jitter lassen
        // die Projektion zwischen Arc ≈ 0 und Arc ≈ Umfang springen. Die Brücke
        // folgt dem kürzeren Bogen, überspannt also fast nichts — vor dem Fix
        // deckte sie die ganze Schleife ab und sammelte jeden Stern von selbst.
        val onFirstSegment = TracePoint(1f, 0f) // Arc ≈ 1
        val onLastSegment = TracePoint(0f, 1f) // Arc ≈ 399
        val result = TraceProgress.update(
            state = TraceState(),
            finger = onLastSegment,
            strokes = listOf(loop),
            stars = loopStars,
            boxSize = boxSize,
            previousFinger = onFirstSegment,
        )
        assertFalse(result.collectedStar)
        assertFalse(result.offCorridor)
    }

    @Test
    fun crossingTheSeamAtTheEndOfAClosedStrokeStillCollectsTheLastStar() {
        // Der letzte Stern sitzt bei Arc = Umfang, also genau auf der Naht. Ein
        // Finger, der die Schleife zu Ende fährt und knapp über die Naht rutscht,
        // muss ihn weiterhin einsammeln (kurzer Bogen von 388 nach 4 über 400).
        val result = TraceProgress.update(
            state = TraceState(0, 3),
            finger = TracePoint(4f, 0f),
            strokes = listOf(loop),
            stars = loopStars,
            boxSize = boxSize,
            previousFinger = TracePoint(0f, 12f),
        )
        assertTrue(result.collectedStar)
        assertTrue(result.glyphDone)
    }

    @Test
    fun restingOnAFinishedStrokeAfterAHandOffIsNotOffRoad() {
        // Nach dem letzten Stern des Querbalkens rückt der Stroke-Index mitten im
        // Drag weiter; der Finger liegt noch am Ende des fertigen Balkens. Das ist
        // keine Korrektur wert (kein Nudge, kein Off-Road-Zähler) — der Finger ist
        // auf der Straße, nur auf der schon gebauten.
        val result = update(TraceState(1, 0), TracePoint(100f, 0f))
        assertFalse(result.offCorridor)
        assertTrue(result.ahead)
        assertFalse(result.collectedStar)
    }
}
