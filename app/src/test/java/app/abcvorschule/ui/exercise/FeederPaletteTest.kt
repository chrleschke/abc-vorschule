package app.abcvorschule.ui.exercise

import androidx.compose.ui.graphics.Color
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import kotlin.math.pow
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Zusagen der Fresser-Farben: Bauch und Glyph sind eine helle bzw. dunkle Stufe
 * der Körperfarbe (kein Weiß, kein Tinte-Braun), der Glyph bleibt auf dem Bauch
 * lesbar, und Licht/Schatten liegen wirklich über und unter dem Körperton.
 */
class FeederPaletteTest {
    @Test
    fun theGlyphClearsThreeToOneOnTheBellyForBothCreatures() {
        listOf(SkyBlue, SunCoral).forEach { body ->
            val ratio = contrast(FeederPalette.glyph(body), FeederPalette.belly(body))
            assertTrue("$body: $ratio", ratio >= 3.0)
        }
    }

    @Test
    fun bellyAndGlyphStayInTheBodyHue() {
        // Kein reines Weiß, kein reines Schwarz: der Bauch ist eine helle, der Glyph eine
        // dunkle Stufe der Körperfarbe.
        listOf(SkyBlue, SunCoral).forEach { body ->
            assertNotEquals(Color.White, FeederPalette.belly(body))
            assertNotEquals(WarmInk, FeederPalette.glyph(body))
            assertTrue(relativeLuminance(FeederPalette.highlight(body)) > relativeLuminance(body))
            assertTrue(relativeLuminance(FeederPalette.shade(body)) < relativeLuminance(body))
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
