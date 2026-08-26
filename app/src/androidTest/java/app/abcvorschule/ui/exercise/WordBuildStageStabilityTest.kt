package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.abcvorschule.content.Atom
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.PackManifest
import app.abcvorschule.content.WordBlock
import app.abcvorschule.content.WordBuildRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.theme.AbcTheme
import kotlin.math.abs
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Der gemeldete Fehler, gegen das echte Layout gemessen: der Aufgabenblock des
 * Wort-Bauers wanderte im Lauf einer Runde durch **drei** senkrechte Positionen —
 * Tray voll, alle Glypheme platziert (Tray leer), Wort fertig (Rahmen werden zu
 * einer Textzeile). Ursache ist die Bühne selbst: der Aufgabenblock hat
 * `weight(1f)` und zentriert seinen Inhalt, also verschiebt ihn jede
 * Höhenänderung des Antwortblocks.
 *
 * Gemessen wird das Bild über dem Wort — es trägt die Aufgabe und ist der
 * auffälligste Wanderer. Steht es still, steht der ganze Block still.
 */
@RunWith(AndroidJUnit4::class)
class WordBuildStageStabilityTest {
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

    private val target = Atom(id = "mama", lemma = "Mama", display = "Mama", emoji = "👩")

    /**
     * Zwei Lösungsbausteine und drei Ablenker, also der volle Tray
     * ([WordBuildTray.MaxTrayTiles]) — der Fall, in dem der Antwortblock am
     * meisten Höhe hält und beim Leerlaufen am weitesten einbricht.
     */
    private val round = WordBuildRound(
        promptTts = "",
        targetAtomId = "mama",
        blocks = listOf(WordBlock("ma", "Ma"), WordBlock("ma", "ma")),
        distractors = listOf(WordBlock("mi", "mi"), WordBlock("mo", "mo"), WordBlock("mu", "mu")),
    )

    @Test
    fun thePictureStaysPutAtSystemFontScale() = assertStagePositionHolds(fontScale = 1.3f)

    @Test
    fun thePictureStaysPutAtDefaultFontScale() = assertStagePositionHolds(fontScale = 1f)

    private fun assertStagePositionHolds(fontScale: Float) {
        // Der letzte Baustein spricht zu Ende, bevor der Erfolg kommt. Das Gatter
        // hält den Trainer im Zustand „alle platziert, noch nicht fertig" an —
        // genau die mittlere der drei gemeldeten Positionen.
        val finalSpeech = CompletableDeferred<Unit>()

        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AbcTheme {
                    Box(
                        modifier = Modifier
                            .size(width = 360.dp, height = 640.dp)
                            .testTag("stage"),
                    ) {
                        WordBuildTrainer(
                            round = round,
                            roundIndex = 0,
                            pack = pack,
                            target = target,
                            scaffoldFor = { ScaffoldLevel.Advanced },
                            ttsAvailable = true,
                            speaking = false,
                            onSpeakPrompt = {},
                            onSpeak = {},
                            onSpeakAndAwait = { finalSpeech.await() },
                            onResult = { _, _, _ -> },
                        )
                    }
                }
            }
        }

        val withFullTray = pictureTop()
        rule.onAllNodesWithTag("word_tray").assertCountEquals(1)

        placeBlock(display = "Ma", frame = 0)
        placeBlock(display = "ma", frame = 1)

        // Tray leer, Rahmen noch da.
        rule.onNodeWithTag("frame_0").assertExists()
        val withEmptyTray = pictureTop()

        finalSpeech.complete(Unit)
        rule.waitForIdle()
        rule.onNodeWithTag("completed_word").assertExists()
        val whenComplete = pictureTop()

        assertSameTop("Tray voll → Tray leer", withFullTray, withEmptyTray)
        assertSameTop("Tray leer → Wort fertig", withEmptyTray, whenComplete)
    }

    private fun placeBlock(display: String, frame: Int) {
        rule.onNodeWithTag("block_$display").performClick()
        rule.onNodeWithTag("frame_$frame").performClick()
        rule.waitForIdle()
    }

    private fun pictureTop(): Dp =
        rule.onNodeWithTag("word_picture").getUnclippedBoundsInRoot().top

    /** 1dp Spiel für Rundung beim Messen — mehr ist ein Sprung. */
    private fun assertSameTop(step: String, before: Dp, after: Dp) {
        assertTrue(
            "$step: Bild springt von ${before.value}dp auf ${after.value}dp",
            abs(before.value - after.value) <= 1f,
        )
    }
}
