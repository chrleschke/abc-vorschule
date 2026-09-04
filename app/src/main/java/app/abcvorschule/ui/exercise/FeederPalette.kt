package app.abcvorschule.ui.exercise

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Die vier Töne, die ein Laut-Fresser aus seiner Körperfarbe zieht (design doc §5).
 *
 * Der Bauch war früher `Cream` und der Glyph `WarmInk` — zwei Fremdfarben auf einer
 * Figur, die dadurch aufgeklebt wirkten. Jetzt ist alles eine Stufe derselben Farbe:
 * heller Bauch, dunkler Glyph, Licht oben links, Schatten unten rechts. Das Kind
 * sieht einen Körper statt einer Collage, und der Glyph „verschmilzt" mit dem
 * Fresser, statt als weißes Schild darauf zu liegen.
 *
 * Die Grenze ist die Lesbarkeit: der Glyph muss auf dem Bauch mindestens 3:1
 * erreichen (`FeederPaletteTest`) — er ist Aufgabe, nicht Dekoration.
 */
object FeederPalette {
    /** Bauchfleck: die helle Stufe, auf der der Glyph steht. */
    fun belly(body: Color): Color = lerp(body, Color.White, 0.78f)

    /** Der Laut auf dem Bauch — dunkle Stufe derselben Farbe, ≥ 3:1 auf [belly]. */
    fun glyph(body: Color): Color = lerp(body, Color.Black, 0.45f)

    /** Lichtseite des Körperverlaufs (oben links). */
    fun highlight(body: Color): Color = lerp(body, Color.White, 0.30f)

    /** Schattenseite des Körperverlaufs (unten rechts) und Grundton des Mauls. */
    fun shade(body: Color): Color = lerp(body, Color.Black, 0.25f)
}
