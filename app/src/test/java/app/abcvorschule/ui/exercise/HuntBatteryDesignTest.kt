package app.abcvorschule.ui.exercise

import androidx.compose.ui.graphics.luminance
import app.abcvorschule.ui.theme.ChargeHigh
import app.abcvorschule.ui.theme.ChargeLow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Die Zusagen der Ladebalken-Töne: eigener Ton pro Balken, nach rechts heller,
 * Enden auf den Palettenfarben. Die Kontrastwerte selbst stehen in Color.kt. */
class HuntBatteryDesignTest {
    @Test
    fun rampSpansTheFullPaletteAtBothRoundSizes() {
        // 3 und 5 sind die einzigen Batteriegrößen (SymbolHuntDerivation.tileCounts).
        for (total in listOf(3, 5)) {
            assertEquals(ChargeLow, HuntBatteryDesign.shadeFor(0, total))
            assertEquals(ChargeHigh, HuntBatteryDesign.shadeFor(total - 1, total))
        }
    }

    @Test
    fun everyBarHasItsOwnShade() {
        for (total in listOf(3, 5)) {
            val shades = (0 until total).map { HuntBatteryDesign.shadeFor(it, total) }
            assertEquals(total, shades.toSet().size)
            // Monoton heller: die Batterie erzählt das Laden über Helligkeit.
            shades.zipWithNext { a, b -> assertTrue(a.luminance() < b.luminance()) }
        }
    }

    @Test
    fun highlightOnlyBrightens() {
        // Color.kt misst die flache Balkenfarbe gegen die Wanne — der Verlauf
        // darf sie nur aufhellen, sonst gilt die dokumentierte Untergrenze nicht.
        for (total in listOf(3, 5)) {
            for (i in 0 until total) {
                val shade = HuntBatteryDesign.shadeFor(i, total)
                assertTrue(HuntBatteryDesign.cellHighlight(shade).luminance() > shade.luminance())
            }
        }
    }

    @Test
    fun indexOutsideTheBatteryClampsInsteadOfExtrapolating() {
        assertEquals(ChargeLow, HuntBatteryDesign.shadeFor(-1, 5))
        assertEquals(ChargeHigh, HuntBatteryDesign.shadeFor(9, 5))
        assertNotEquals(ChargeLow, HuntBatteryDesign.shadeFor(1, 5))
    }

    @Test
    fun boltIsAClosedShapeInsideItsBox() {
        assertTrue(HuntBatteryDesign.BoltPath.size >= 5)
        HuntBatteryDesign.BoltPath.forEach { (x, y) ->
            assertTrue(x in 0f..1f && y in 0f..1f)
        }
    }
}
