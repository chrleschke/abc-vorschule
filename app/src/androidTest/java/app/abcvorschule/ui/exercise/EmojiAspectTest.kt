package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.abcvorschule.ui.theme.AbcTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Die Zusage, auf der [MathBoardSizing] steht: ein Emoji, das [EmojiGlyph] mit
 * `size` dp bekommt, rendert nie **breiter oder höher** als `size`. Stimmt das
 * nicht, rechnet die ganze Bühnenaufteilung zu klein und die Kachelreihe bricht
 * wieder um.
 *
 * Der Test misst gerendert statt gerechnet — er prüft damit zugleich
 * [MathBoardSizing.EmojiAspect] gegen die Emoji-Schrift des Systems und den
 * dp→sp→dp-Umweg von `EmojiGlyph` gegen Androids nichtlineare Skalierung.
 */
@RunWith(AndroidJUnit4::class)
class EmojiAspectTest {
    @get:Rule
    val rule = createComposeRule()

    /** Von der kleinsten symbolischen Kachel bis zum größten Aufgabenbild. */
    private val sizes = listOf(15.dp, 20.dp, 26.dp, 36.dp, 44.dp, 57.dp)

    /** 1.0, die 1.3 des Testgeräts und der Härtefall 2.0. */
    private val fontScales = listOf(1f, 1.3f, 2f)

    @Test
    fun anEmojiNeverRendersLargerThanItsBox() {
        var size by mutableStateOf(sizes.first())
        var fontScale by mutableStateOf(fontScales.first())

        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AbcTheme {
                    Box {
                        // Die Ameise aus Lektion 1 — ein Emoji mit voller Breite, kein
                        // schmales Symbol, das die Messung schöner machen würde.
                        EmojiGlyph(emoji = "🐜", size = size, modifier = Modifier.testTag("emoji"))
                    }
                }
            }
        }

        sizes.forEach { currentSize ->
            fontScales.forEach { currentScale ->
                size = currentSize
                fontScale = currentScale
                rule.waitForIdle()

                val bounds = rule.onNodeWithTag("emoji").getUnclippedBoundsInRoot()
                val width = (bounds.right - bounds.left).value
                val height = (bounds.bottom - bounds.top).value
                val case = "${currentSize.value}dp bei font_scale $currentScale"
                assertTrue("Emoji ${width}dp breiter als seine Box ($case)", width <= currentSize.value + Slack)
                assertTrue("Emoji ${height}dp höher als seine Box ($case)", height <= currentSize.value + Slack)
            }
        }
    }

    private companion object {
        /** Rundungsluft der Layout-Messung, nicht der erlaubte Überstand. */
        const val Slack = 0.5f
    }
}
