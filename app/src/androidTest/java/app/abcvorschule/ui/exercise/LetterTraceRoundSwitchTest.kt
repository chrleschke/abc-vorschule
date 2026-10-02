package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.abcvorschule.R
import app.abcvorschule.content.Atom
import app.abcvorschule.content.GlyphStroke
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.ui.theme.AbcTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Der gemeldete Fehler: vor „Zeichne den Buchstaben U" blitzte kurz die Karte mit dem
 * Uhu auf — die Belohnung der Runde, die gerade erst anfängt. Zwei Spur-Runden
 * hintereinander (u → U) behalten denselben Aufrufort, und der Übergang zur Karte
 * war ein ungekeyter animateFloatAsState: er stand aus der fertigen Vorrunde noch
 * auf 1 und zeigte im ersten Bild der neuen Runde deren Karte.
 */
@RunWith(AndroidJUnit4::class)
class LetterTraceRoundSwitchTest {
    @get:Rule
    val rule = createComposeRule()

    private val bar = listOf(GlyphStroke(listOf(listOf(0.5, 0.1), listOf(0.5, 0.9))))
    private val lowerU = Atom(id = "u", lemma = "u", display = "u", emoji = "🦉", strokes = bar)
    private val upperU = Atom(id = "U", lemma = "U", display = "U", emoji = "🦉", strokes = bar)

    private fun round(atom: Atom) = LetterTraceRound(
        promptTts = "",
        atomId = atom.id,
        glyph = atom.display,
        rewardTts = "${atom.display} wie Uhu",
        rewardEmoji = "🦉",
    )

    @Test
    fun theNextRoundStartsOnItsGlyphNotOnItsReward() {
        var roundIndex by mutableIntStateOf(0)
        var atom by mutableStateOf(lowerU)

        rule.setContent {
            AbcTheme {
                Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                    LetterTraceTrainer(
                        round = round(atom),
                        roundIndex = roundIndex,
                        atom = atom,
                        ttsAvailable = true,
                        speaking = false,
                        onSpeakPrompt = {},
                        onSpeak = {},
                        onResult = { _, _, _ -> },
                    )
                }
            }
        }

        // Erste Runde beenden: fruchtloses Tippen bietet das Auflösen an, und das
        // Auflösen zeigt die Belohnungskarte.
        repeat(TraceProgress.TapsBeforeResolve) {
            rule.onNodeWithTag("trace_canvas_u").performTouchInput { click(topLeft) }
        }
        val resolve = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.resolve)
        rule.onNodeWithText(resolve).performClick()
        rule.waitForIdle()
        rule.onNodeWithTag("trace_reward_u").assertExists()

        rule.mainClock.autoAdvance = false
        roundIndex = 1
        atom = upperU
        rule.mainClock.advanceTimeBy(16)

        rule.onAllNodesWithTag("trace_reward_U").assertCountEquals(0)
        rule.onNodeWithTag("trace_canvas_U").assertExists()
    }
}
