package app.abcvorschule.ui.theme

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbcMotionTest {
    private val steps = listOf(
        AbcMotion.MicroMs,
        AbcMotion.QuickMs,
        AbcMotion.ShortMs,
        AbcMotion.StandardMs,
        AbcMotion.LongMs,
        AbcMotion.CelebrateMs,
    )

    private val springs = mapOf(
        "Settle" to AbcMotion.Settle,
        "Soft" to AbcMotion.Soft,
        "Bouncy" to AbcMotion.Bouncy,
        "Glide" to AbcMotion.Glide,
        "Pop" to AbcMotion.Pop,
        "Wobble" to AbcMotion.Wobble,
        "Snap" to AbcMotion.Snap,
    )

    /** Jede Stufe ist spürbar länger als die vorige, aber kein Sprung ins Leere. */
    @Test
    fun durationStepsGrowByAPerceptibleButModestFactor() {
        steps.zipWithNext().forEach { (shorter, longer) ->
            val factor = longer.toFloat() / shorter
            assertTrue("$shorter → $longer ist Faktor $factor", factor in 1.25f..2.0f)
        }
    }

    /** Eine Feder, die gar nicht dämpft, schwingt ewig — das ist kein Einrasten. */
    @Test
    fun everySpringComesToRest() {
        springs.forEach { (name, spring) ->
            assertTrue("$name: dampingRatio ${spring.dampingRatio}", spring.dampingRatio in 0.3f..1f)
            assertTrue("$name: stiffness ${spring.stiffness}", spring.stiffness > 0f)
        }
    }

    /** Die Squish-Feder des Einrastens ist die eine, die der Morph dokumentiert. */
    @Test
    fun slotFillMorphUsesTheSettleSpring() {
        assertEquals(
            AbcMotion.Settle.dampingRatio,
            app.abcvorschule.ui.exercise.SlotFillMorph.Damping,
            0f,
        )
    }

    /**
     * Keine Feder wird mehr am Aufrufort erfunden. Eine neue Bewegung wählt aus der
     * Palette — sonst läuft sie wieder auseinander (PRODUCT_PRINCIPLES §10).
     * Tweens sind ausgenommen: Figurenspiel darf eigene Dauern tragen.
     */
    @Test
    fun noSpringIsDefinedOutsideThePalette() {
        val ui = File("src/main/java/app/abcvorschule/ui")
        assertTrue("UI-Quellen nicht gefunden unter ${ui.absolutePath}", ui.isDirectory)
        val offenders = ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" && it.name != "Motion.kt" }
            .flatMap { file ->
                file.readLines().mapIndexedNotNull { index, line ->
                    val code = line.substringBefore("//")
                    if (Regex("""\bspring\(|dampingRatio\s*=""").containsMatchIn(code)) {
                        "${file.name}:${index + 1}: ${line.trim()}"
                    } else {
                        null
                    }
                }
            }
            .toList()
        assertTrue("Federn außerhalb von AbcMotion:\n" + offenders.joinToString("\n"), offenders.isEmpty())
    }
}
