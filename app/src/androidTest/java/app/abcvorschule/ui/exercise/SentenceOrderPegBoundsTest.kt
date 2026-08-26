package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.theme.AbcTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Der gemeldete Fehler, gegen das echte Layout gemessen statt gegen die Rechnung:
 * beim Satz-Architekten lag das letzte Wort außerhalb der Bühne und war damit nicht
 * antippbar — die Runde war unlösbar. `SentencePegSizingTest` prüft die Geometrie,
 * dieser Test prüft, dass die gerenderten Pegs auch wirklich dort landen. Der
 * Unterschied ist nicht akademisch: [SentencePegSizing.GlyphAspect] ist eine
 * *Schätzung* der Zeichenbreite, und nur ein gemessener Lauf zeigt, ob sie reicht.
 */
@RunWith(AndroidJUnit4::class)
class SentenceOrderPegBoundsTest {
    @get:Rule
    val rule = createComposeRule()

    /**
     * Die längsten Sätze, die der Satz-Architekt im ausgelieferten Pack wirklich
     * stellt (`s-oma-hat-hut`, `s-fisch-schwimmt`, `s-viele-haeuser`) plus die
     * Einwort-Runde aus L01.
     */
    private val sentences = listOf(
        listOf("Oma", "hat", "einen", "Hut"),
        listOf("der", "Fisch", "schwimmt"),
        listOf("hier", "sind", "Häuser"),
        listOf("Mama"),
    )

    /** Bühnenbreiten: Deckel von ExerciseStage, ein 360dp-Telefon, ein 320dp-Telefon. */
    private val stageWidths = listOf(420.dp, 360.dp, 320.dp)

    /** 1.3 ist die Systemschriftgröße des Testgeräts, 2.0 der Härtefall. */
    private val fontScales = listOf(1f, 1.3f, 2f)

    @Test
    fun everyPegStaysInsideTheStageAndKeepsATappableWidth() {
        // setContent darf pro Test nur einmal laufen, also treiben State-Objekte die
        // Fälle statt einer Schleife um setContent.
        var words by mutableStateOf(sentences.first())
        var stageWidth by mutableStateOf(stageWidths.first())
        var fontScale by mutableStateOf(fontScales.first())

        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AbcTheme {
                    Box(
                        modifier = Modifier
                            .size(width = stageWidth, height = 640.dp)
                            .testTag("stage"),
                    ) {
                        SentenceOrderTrainer(
                            round = SentenceOrderRound(promptTts = "", sentenceId = "test"),
                            roundIndex = 0,
                            words = words,
                            atomIds = words,
                            illustrationEmoji = null,
                            scaffoldFor = { ScaffoldLevel.Advanced },
                            ttsAvailable = true,
                            speaking = false,
                            onSpeakPrompt = {},
                            onSpeak = {},
                            onResult = { _, _, _ -> },
                        )
                    }
                }
            }
        }

        sentences.forEach { sentence ->
            stageWidths.forEach { width ->
                fontScales.forEach { scale ->
                    rule.runOnUiThread {
                        words = sentence
                        stageWidth = width
                        fontScale = scale
                    }
                    rule.waitForIdle()
                    assertPegsFit(sentence, width, scale)
                }
            }
        }
    }

    private fun assertPegsFit(words: List<String>, stageWidth: Dp, fontScale: Float) {
        val case = "„${words.joinToString(" ")}\" auf $stageWidth bei font_scale $fontScale"
        val stage = rule.onAllNodesWithTag("stage", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first()
            .boundsInRoot
        // Toleranz von 1px: Compose rundet dp auf ganze Pixel, ein halbes Pixel
        // Überstand ist kein unerreichbarer Peg.
        val slack = 1f
        val touchFloorPx = with(rule.density) { 44.dp.toPx() }

        words.indices.forEach { index ->
            val peg = rule.onAllNodesWithTag("peg_$index", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()
            requireNotNull(peg) { "$case: peg_$index fehlt" }
            val bounds = peg.boundsInRoot
            assertTrue(
                "$case: peg_$index läuft links raus (${bounds.left} < ${stage.left})",
                bounds.left >= stage.left - slack,
            )
            assertTrue(
                "$case: peg_$index läuft rechts raus (${bounds.right} > ${stage.right})",
                bounds.right <= stage.right + slack,
            )
            assertTrue(
                "$case: peg_$index ist nur ${bounds.width}px breit, Boden $touchFloorPx",
                bounds.width >= touchFloorPx - slack,
            )
        }
    }
}
