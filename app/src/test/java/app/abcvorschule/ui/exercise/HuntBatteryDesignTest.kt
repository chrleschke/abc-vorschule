package app.abcvorschule.ui.exercise

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import app.abcvorschule.ui.theme.ChargeHigh
import app.abcvorschule.ui.theme.ChargeLow
import app.abcvorschule.ui.theme.WarmInk
import kotlin.math.pow
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
    fun boltStaysInsideItsBox() {
        // Der Blitz wird in eine Box der Größe (Breite × Höhe) skaliert; Punkte
        // außerhalb 0..1 ragen aus ihr heraus und damit über die Balken hinaus.
        assertTrue(HuntBatteryDesign.BoltPath.size >= 5)
        HuntBatteryDesign.BoltPath.forEach { (x, y) ->
            assertTrue(x in 0f..1f && y in 0f..1f)
        }
    }

    @Test
    fun everyBarClearsThreeToOneAgainstTheWell() {
        // Die Zahl, auf der die ganze Farbwahl steht (Color.kt, ChargeLow/Mid/High):
        // ein Balken muss sich von der Wanne abheben, sonst ist der Ladestand nicht
        // ablesbar. Als Test statt nur als Kommentar, damit ein späterer Farbdreh
        // hier auffliegt und nicht erst am Kind.
        for (total in listOf(3, 5)) {
            for (i in 0 until total) {
                val shade = HuntBatteryDesign.shadeFor(i, total)
                val ratio = contrast(shade, WarmInk)
                assertTrue("Balken $i von $total: $ratio:1 gegen die Wanne", ratio >= 3.0)
                // Der Verlauf hellt nur auf — die flache Farbe bleibt die Untergrenze.
                assertTrue(contrast(HuntBatteryDesign.cellHighlight(shade), WarmInk) > ratio)
            }
        }
        // Blitz auf dem Vollzustand: dunkles Zeichen auf hellem Grün.
        assertTrue(contrast(WarmInk, ChargeHigh) >= 4.5)
    }

    @Test
    fun theGlowStaysInsideTheWell() {
        // Der Lichtsaum wird um jeden Balken herum gezeichnet. Wird er breiter als
        // die Luft zwischen Balkenfeld und Wannenrand, leuchtet Grün auf das
        // Gehäuse und die Batterie verliert ihre Kante.
        assertTrue(HuntBatteryDesign.GlowBleed <= HuntBatteryDesign.WellPadding)
        assertEquals(HuntBatteryDesign.CasingThickness + HuntBatteryDesign.WellPadding, HuntBatteryDesign.Rim)
        // Das Balkenfeld muss in das Gehäuse passen, das die Batterie dafür aufspannt.
        for (total in listOf(3, 5)) {
            val field = HuntBatteryDesign.CellWidth * total +
                HuntBatteryDesign.CellGap * (total - 1)
            assertTrue(field < HuntBatteryDesign.bodyWidth(total))
            assertTrue(HuntBatteryDesign.totalWidth(total) > HuntBatteryDesign.bodyWidth(total))
        }
    }

    /** WCAG-2.x-Kontrast; die Werte in Color.kt sind mit derselben Formel gerechnet. */
    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double {
        fun channel(value: Float): Double {
            val c = value.toDouble()
            return if (c <= 0.04045) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
        }
        return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
    }
}
