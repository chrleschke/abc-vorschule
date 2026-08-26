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
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.theme.AbcTheme
import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Dieselbe Macke wie im Wort-Bauer (siehe `WordBuildRoundSwitchTest`), hier nur
 * latent: `AnimatedContent` merkt sich seinen Zustand in einem ungekeyten
 * `remember`, und der Aufrufort überlebt einen Rundenwechsel. Folgen zwei
 * Satz-Architekten aufeinander, steht die Transition beim Laden noch auf „fertig",
 * während der neue Rundenzustand schon „leer" ist — die Bühne spielt dann den
 * Eintritt der leeren Pegs ab, und das sieht nach einem Fehler aus.
 *
 * Im ausgelieferten Content-Pack tritt das heute nicht auf: jeder
 * `sentence_order`-Task hat genau eine Runde, und zwei davon liegen nie direkt
 * hintereinander. Der Test hält die Stelle fest, damit die Reihenfolge im Content
 * frei bleibt — ein Trainer, der gerade geladen wird, animiert nichts.
 */
@RunWith(AndroidJUnit4::class)
class SentenceOrderRoundSwitchTest {
    @get:Rule
    val rule = createComposeRule()

    private val firstWords = listOf("der", "Fisch")
    private val secondWords = listOf("die", "Katze")

    private val firstRound = SentenceOrderRound(promptTts = "", sentenceId = "fisch")
    private val secondRound = SentenceOrderRound(promptTts = "", sentenceId = "katze")

    @Test
    fun theNextRoundsPegsDoNotAnimateIn() {
        var round by mutableStateOf(firstRound)
        var words by mutableStateOf(firstWords)

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
                        SentenceOrderTrainer(
                            // Zwei aufeinanderfolgende Satz-Architekten: beide
                            // beginnen bei roundIndex 0, der Aufrufort bleibt
                            // derselbe — genau die Lage, die im Content entstehen
                            // kann.
                            round = round,
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

        // Ersten Satz zu Ende bauen, damit der Trainer im Zustand „fertig" steht.
        firstWords.forEachIndexed { index, word ->
            rule.onNodeWithTag("card_$word").performClick()
            rule.onNodeWithTag("peg_$index").performClick()
        }
        rule.waitForIdle()
        rule.onNodeWithTag("completed_sentence").assertExists()

        // Ab hier zählt jedes Bild einzeln.
        rule.mainClock.autoAdvance = false
        rule.runOnUiThread {
            round = secondRound
            words = secondWords
        }
        rule.mainClock.advanceTimeBy(16)

        // Der fertige Satz der alten Runde darf nicht mehr mitlaufen: hängt er noch
        // im Baum, blendet er gerade aus — und die Pegs blenden ein.
        rule.onAllNodesWithTag("completed_sentence").assertCountEquals(0)
        val firstPegAtOnce = rule.onNodeWithTag("peg_0").getUnclippedBoundsInRoot()

        // Und die Pegs dürfen auch nicht wandern: nach einer halben Sekunde müssen
        // sie genau dort stehen, wo sie im ersten Bild standen.
        rule.mainClock.advanceTimeBy(500)
        val firstPegSettled = rule.onNodeWithTag("peg_0").getUnclippedBoundsInRoot()

        assertTrue(
            "Peg wandert von ${firstPegAtOnce.top.value}dp/${firstPegAtOnce.height.value}dp " +
                "auf ${firstPegSettled.top.value}dp/${firstPegSettled.height.value}dp",
            abs(firstPegAtOnce.top.value - firstPegSettled.top.value) <= 1f &&
                abs(firstPegAtOnce.height.value - firstPegSettled.height.value) <= 1f,
        )
    }
}
