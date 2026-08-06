package app.abcvorschule.content

import app.abcvorschule.ui.exercise.TraceGeometry
import app.abcvorschule.ui.exercise.TracePoint
import app.abcvorschule.ui.exercise.TraceProgress
import app.abcvorschule.ui.exercise.TraceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Letterform rules the authored glyphs have to obey, checked against the pack rather
 * than against a copy of the numbers.
 */
class GlyphLetterformTest {
    private val pack = ContentRepository.fromClasspath().load()
    private val boxSize = 260f // GlyphBox in LetterTraceTrainer

    private fun strokesOf(atomId: String) = TraceGeometry.toPixels(
        requireNotNull(pack.atoms[atomId]) { "missing atom $atomId" }.strokes,
        boxSize,
        TracePoint(0f, 0f),
    )

    @Test
    fun strokesOfAGlyphEitherMeetExactlyOrStayApart() {
        // A bar that stops 0.02 short of the spine it caps reads as a wobble, not as a
        // letter: that is how the E's top bar sat below the spine's cap in E/Ei/Eu/F/Pf.
        // Endpoints either coincide or keep a visible distance.
        val minimumGap = boxSize * 0.07f
        val offenders = mutableListOf<String>()

        pack.atoms.values.filter { it.strokes.isNotEmpty() }.forEach { atom ->
            val ends = strokesOf(atom.id).mapIndexed { index, stroke ->
                index to listOf(stroke.first(), stroke.last())
            }
            ends.forEach { (i, a) ->
                ends.filter { it.first > i }.forEach { (j, b) ->
                    a.forEach { p ->
                        b.forEach { q ->
                            val gap = hypot(p.x - q.x, p.y - q.y)
                            if (gap > 0f && gap < minimumGap) {
                                offenders += "${atom.id}: stroke $i endpoint $p and " +
                                    "stroke $j endpoint $q are only $gap apart"
                            }
                        }
                    }
                }
            }
        }

        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }

    @Test
    fun theIofEiIsEnteredAtItsHeadNotAtItsFoot() {
        val strokes = strokesOf("letter-ei")
        val stars = strokes.map {
            TraceGeometry.starPositions(
                it,
                TraceProgress.starCountFor(TraceGeometry.polylineLength(it), boxSize),
            )
        }
        val iStroke = strokes.size - 1
        val fresh = TraceState(iStroke, 0)
        val foot = strokes[iStroke].last()
        val head = strokes[iStroke].first()

        // Dragging on from the E's bottom bar slides into the i's corridor at its foot —
        // in the road, but at the wrong end, so it must not move the vehicle there.
        val atFoot = TraceProgress.update(fresh, foot, strokes, stars, boxSize)
        assertTrue(atFoot.ahead)
        assertFalse(atFoot.offCorridor)
        assertEquals(fresh, atFoot.state)

        // Starting at the head of the i is what the trainer wants, and it is accepted.
        val atHead = TraceProgress.update(fresh, head, strokes, stars, boxSize)
        assertFalse(atHead.ahead)
        assertFalse(atHead.offCorridor)
    }

    @Test
    fun theUBowlIsADenseCurveNotACoarsePolygon() {
        // The road is drawn with lineTo between authored points; six bowl vertices left
        // visible corners. O uses ~17 samples — U/Ü should be in that ballpark.
        val u = strokesOf("letter-u").single()
        val ue = strokesOf("letter-ue").first()
        assertTrue("U has only ${u.size} points", u.size >= 14)
        assertTrue("Ü body has only ${ue.size} points", ue.size >= 14)
    }

    @Test
    fun compoundGlyphsCarryADenseUNotACoarsePolygon() {
        listOf("letter-au", "letter-eu", "letter-qu").forEach { id ->
            val uStroke = strokesOf(id).last()
            assertTrue("$id U has only ${uStroke.size} points", uStroke.size >= 14)
        }
    }

    @Test
    fun theJHookIsDenselySampled() {
        assertTrue(strokesOf("letter-j").single().size >= 7)
    }

    @Test
    fun umlautTicksAreShortVerticalAndClearOfTheLetterBody() {
        // Diagonal ticks under a full-width road bled into the Ü/Ö/Ä body. Vertical short
        // ticks higher up stay clear once ShortStrokeWidthScale thins the drawing.
        listOf("letter-ae", "letter-oe", "letter-ue").forEach { id ->
            val strokes = strokesOf(id)
            val ticks = strokes.takeLast(2)
            ticks.forEach { tick ->
                assertEquals(2, tick.size)
                assertEquals(tick.first().x, tick.last().x, 0.01f)
                val length = hypot(tick.last().x - tick.first().x, tick.last().y - tick.first().y)
                assertTrue("$id tick length $length", length < boxSize * TraceProgress.ShortStrokeFraction)
                assertTrue("$id tick too low (${tick.last().y})", tick.last().y < boxSize * 0.12f)
            }
            val bodyTop = strokes.first().minOf { it.y }
            val tickBottom = ticks.maxOf { it.maxOf { p -> p.y } }
            assertTrue(
                "$id: tick bottom $tickBottom collides with body top $bodyTop",
                tickBottom < bodyTop - boxSize * 0.05f,
            )
        }
    }
}
