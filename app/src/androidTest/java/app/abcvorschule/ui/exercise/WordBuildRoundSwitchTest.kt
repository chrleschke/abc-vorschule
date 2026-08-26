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
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
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
 * Der gemeldete Fehler: kommt der Wort-Bauer nach einem **abgeschlossenen**
 * Wort-Bauer (im ausgelieferten Pack 36×, z. B. `l02-t7` → `l02-t8`), sieht das
 * Kind die leeren Rahmen erst entstehen. Über den Weiter-Chevron passiert das
 * nicht — dort wird die Runde verlassen, bevor sie fertig ist.
 *
 * Grund: `AnimatedContent` merkt sich seinen Zustand in einem ungekeyten
 * `remember`. Der Aufrufort bleibt beim Rundenwechsel derselbe, also steht die
 * Transition noch auf „fertig", während der neue Rundenzustand schon „leer" ist,
 * und die Bühne spielt den Eintritt der Rahmen ab. Ein Trainer, der gerade
 * geladen wird, animiert nichts — er ist einfach da.
 */
@RunWith(AndroidJUnit4::class)
class WordBuildRoundSwitchTest {
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

    private val mama = Atom(id = "mama", lemma = "Mama", display = "Mama", emoji = "👩")
    private val mimi = Atom(id = "mimi", lemma = "Mimi", display = "Mimi", emoji = "🐱")

    private val firstRound = WordBuildRound(
        promptTts = "",
        targetAtomId = "mama",
        blocks = listOf(WordBlock("ma", "Ma"), WordBlock("ma", "ma")),
    )

    private val secondRound = WordBuildRound(
        promptTts = "",
        targetAtomId = "mimi",
        blocks = listOf(WordBlock("mi", "Mi"), WordBlock("mi", "mi")),
    )

    @Test
    fun theNextRoundsFramesDoNotAnimateIn() {
        val finalSpeech = CompletableDeferred<Unit>()
        var round by mutableStateOf(firstRound)
        var target by mutableStateOf(mama)

        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1.3f),
            ) {
                AbcTheme {
                    Box(
                        modifier = Modifier
                            .size(width = 360.dp, height = 640.dp)
                            .testTag("stage"),
                    ) {
                        WordBuildTrainer(
                            // Zwei aufeinanderfolgende Wort-Bauer-Trainer: beide
                            // beginnen bei roundIndex 0, der Aufrufort bleibt
                            // derselbe — genau die Lage aus dem Report.
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

        // Erste Runde zu Ende bauen, damit der Trainer im Zustand „fertig" steht.
        rule.onNodeWithTag("block_Ma").performClick()
        rule.onNodeWithTag("frame_0").performClick()
        rule.onNodeWithTag("block_ma").performClick()
        rule.onNodeWithTag("frame_1").performClick()
        finalSpeech.complete(Unit)
        rule.waitForIdle()
        rule.onNodeWithTag("completed_word").assertExists()

        // Ab hier zählt jedes Bild einzeln.
        rule.mainClock.autoAdvance = false
        round = secondRound
        target = mimi
        rule.mainClock.advanceTimeBy(16)

        // Das fertige Wort der alten Runde darf nicht mehr mitlaufen: hängt es
        // noch im Baum, blendet es gerade aus — und die Rahmen blenden ein.
        rule.onAllNodesWithTag("completed_word").assertCountEquals(0)
        val firstFrameAtOnce = rule.onNodeWithTag("frame_0").getUnclippedBoundsInRoot()

        // Und die Rahmen dürfen auch nicht wachsen: nach einer halben Sekunde
        // müssen sie genau dort stehen, wo sie im ersten Bild standen.
        rule.mainClock.advanceTimeBy(500)
        val firstFrameSettled = rule.onNodeWithTag("frame_0").getUnclippedBoundsInRoot()

        assertTrue(
            "Rahmen wandert von ${firstFrameAtOnce.top.value}dp/${firstFrameAtOnce.height.value}dp " +
                "auf ${firstFrameSettled.top.value}dp/${firstFrameSettled.height.value}dp",
            abs(firstFrameAtOnce.top.value - firstFrameSettled.top.value) <= 1f &&
                abs(firstFrameAtOnce.height.value - firstFrameSettled.height.value) <= 1f,
        )
    }
}
