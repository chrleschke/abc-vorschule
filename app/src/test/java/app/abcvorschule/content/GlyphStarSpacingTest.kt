package app.abcvorschule.content

import app.abcvorschule.ui.exercise.TraceGeometry
import app.abcvorschule.ui.exercise.TracePoint
import app.abcvorschule.ui.exercise.TraceProgress
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Every authored glyph must be traceable: no two consecutive stars may sit inside
 * one another's pick-up radius, or the child cannot aim at them one by one.
 */
class GlyphStarSpacingTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun everyAuthoredStrokeKeepsItsStarsApart() {
        val boxSize = 260f // GlyphBox in LetterTraceTrainer
        val radius = boxSize * TraceProgress.StarHitFraction
        val offenders = mutableListOf<String>()

        pack.atoms.values.filter { it.strokes.isNotEmpty() }.forEach { atom ->
            TraceGeometry.toPixels(atom.strokes, boxSize, TracePoint(0f, 0f))
                .forEachIndexed { index, stroke ->
                    val count = TraceProgress.starCountFor(
                        TraceGeometry.polylineLength(stroke),
                        boxSize,
                    )
                    TraceGeometry.starPositions(stroke, count)
                        .zipWithNext()
                        .forEach { (a, b) ->
                            val gap = hypot(b.x - a.x, b.y - a.y)
                            if (gap <= radius) {
                                offenders += "${atom.id} stroke $index: gap $gap <= radius $radius"
                            }
                        }
                }
        }

        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }
}
