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
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.PackManifest
import app.abcvorschule.content.SymbolHuntDerivation
import app.abcvorschule.content.SymbolHuntMode
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.ui.theme.AbcTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Der gemeldete Fehler, gegen das echte Layout gemessen statt gegen die Rechnung:
 * bei der Buchstabenjagd standen die äußeren Kacheln links und rechts über den
 * Bildschirmrand hinaus und waren angeschnitten. `SymbolHuntLayoutTest` prüft die
 * Geometrie der Streuung, dieser Test prüft, dass die gerenderten Kacheln auch
 * wirklich in der Bühne landen — inklusive Kachelgröße, `offset` und den
 * Innenabständen von `ExerciseStage`, die die reine Streufunktion nicht kennt.
 */
@RunWith(AndroidJUnit4::class)
class SymbolHuntTileBoundsTest {
    @get:Rule
    val rule = createComposeRule()

    private val pack = ContentPack(
        manifest = PackManifest(schemaVersion = 1, packId = "test", title = "Test Pack"),
        atoms = emptyMap(),
        sentences = emptyMap(),
        tasks = emptyMap(),
        finales = emptyMap(),
        lessons = emptyList(),
    )

    private val round = SymbolHuntRound(
        promptTts = SymbolHuntDerivation.PromptLetter,
        targetAtomId = "A",
        mode = SymbolHuntMode.letter,
        // Sechs eigenständige Ablenker → das volle Feld aus 11 Kacheln, also der
        // dichteste und damit ungünstigste Fall.
        distractorPool = listOf("M", "I", "O", "S", "L", "E"),
    )

    /** Bühnenbreiten: Deckel von ExerciseStage, ein 360dp-Telefon, ein 320dp-Telefon. */
    private val stageWidths = listOf(420.dp, 360.dp, 320.dp)

    /** 1.3 ist die Systemschriftgröße des Testgeräts, 2.0 der Härtefall. */
    private val fontScales = listOf(1f, 1.3f, 2f)

    /**
     * Verschiedene Runden-Indizes ergeben verschiedene Seeds und damit verschiedene
     * Streufelder — ein einzelnes Feld würde einen Randfehler leicht verfehlen.
     */
    private val roundIndices = listOf(0, 1, 2, 3, 4, 5, 6, 7)

    @Test
    fun everyTileStaysInsideTheStage() {
        // setContent darf pro Test nur einmal laufen, also treiben State-Objekte die
        // Fälle statt einer Schleife um setContent.
        var stageWidth by mutableStateOf(stageWidths.first())
        var fontScale by mutableStateOf(fontScales.first())
        var roundIndex by mutableStateOf(roundIndices.first())

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
                        SymbolHuntTrainer(
                            round = round,
                            roundIndex = roundIndex,
                            pack = pack,
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

        stageWidths.forEach { width ->
            fontScales.forEach { scale ->
                roundIndices.forEach { index ->
                    rule.runOnUiThread {
                        stageWidth = width
                        fontScale = scale
                        roundIndex = index
                    }
                    rule.waitForIdle()
                    assertTilesFit(width, scale, index)
                }
            }
        }
    }

    private fun assertTilesFit(stageWidth: Dp, fontScale: Float, roundIndex: Int) {
        val case = "Runde $roundIndex auf $stageWidth bei font_scale $fontScale"
        val stage = rule.onAllNodesWithTag("stage", useUnmergedTree = true)
            .fetchSemanticsNodes()
            .first()
            .boundsInRoot
        // Toleranz von 1px: Compose rundet dp auf ganze Pixel, ein halbes Pixel
        // Überstand ist keine sichtbar angeschnittene Kachel.
        val slack = 1f

        val tiles = (0 until 11).mapNotNull { instanceId ->
            rule.onAllNodesWithTag("hunt_tile_$instanceId", useUnmergedTree = true)
                .fetchSemanticsNodes()
                .firstOrNull()
                ?.let { instanceId to it.boundsInRoot }
        }
        assertTrue("$case: keine Kacheln gefunden", tiles.isNotEmpty())

        tiles.forEach { (instanceId, bounds) ->
            assertTrue(
                "$case: hunt_tile_$instanceId läuft links raus (${bounds.left} < ${stage.left})",
                bounds.left >= stage.left - slack,
            )
            assertTrue(
                "$case: hunt_tile_$instanceId läuft rechts raus (${bounds.right} > ${stage.right})",
                bounds.right <= stage.right + slack,
            )
            assertTrue(
                "$case: hunt_tile_$instanceId läuft oben raus (${bounds.top} < ${stage.top})",
                bounds.top >= stage.top - slack,
            )
            assertTrue(
                "$case: hunt_tile_$instanceId läuft unten raus (${bounds.bottom} > ${stage.bottom})",
                bounds.bottom <= stage.bottom + slack,
            )
        }
    }
}
