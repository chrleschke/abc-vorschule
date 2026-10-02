package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.abcvorschule.R
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.SentencePictureSpec
import app.abcvorschule.ui.theme.AbcTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Satz-Versteher: Antworten per Tipp **oder** per Ziehen in den Bildrahmen des Buchs
 * (PRODUCT_PRINCIPLES §3/§9). Gezählt wird nur ein echter Treffer auf dem Rahmen; die
 * falsche Karte dort zählt wie ein Tipp auf sie, Loslassen anderswo zählt gar nicht.
 * Und es gibt kein „Zeig mir" mehr, auch nicht nach vielen Fehlversuchen.
 */
@RunWith(AndroidJUnit4::class)
class SentencePictureDragTest {
    @get:Rule
    val rule = createComposeRule()

    private val pack by lazy {
        ContentRepository.fromContext(InstrumentationRegistry.getInstrumentation().targetContext).load()
    }

    /** (correct, resolved) je gemeldetem Ergebnis. */
    private val results = mutableListOf<Pair<Boolean, Boolean>>()

    private fun show() {
        val spec = pack.tasks.getValue("l01-sp1") as SentencePictureSpec
        rule.setContent {
            AbcTheme {
                Box(Modifier.fillMaxSize().padding(top = 112.dp, start = 20.dp, end = 20.dp, bottom = 32.dp)) {
                    SentencePictureTrainer(
                        round = spec.rounds[0],
                        roundIndex = 0,
                        pack = pack,
                        ttsAvailable = true,
                        speaking = false,
                        onSpeakPrompt = {},
                        onResult = { correct, resolved, _ -> results += correct to resolved },
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    private fun centerOf(tag: String): Offset = rule.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot.center

    /** Zieht die Karte in kleinen Schritten um [delta] (px) und lässt los. */
    private fun drag(tag: String, delta: Offset) {
        rule.onNodeWithTag(tag).performTouchInput {
            down(center)
            repeat(12) { moveBy(delta / 12f) }
            up()
        }
        rule.waitForIdle()
    }

    private fun dragOntoFrame(tag: String) = drag(tag, centerOf("sentence_picture_frame") - centerOf(tag))

    @Test
    fun tappingTheCorrectCardStillAnswers() {
        show()
        rule.onNodeWithTag("sentence_picture_card_correct").performClick()
        rule.waitForIdle()
        assertEquals(listOf(true to false), results)
    }

    @Test
    fun correctCardDroppedOnTheFrameIsCorrect() {
        show()
        dragOntoFrame("sentence_picture_card_correct")
        assertEquals(listOf(true to false), results)
    }

    @Test
    fun wrongCardDroppedOnTheFrameCountsLikeTappingIt() {
        show()
        dragOntoFrame("sentence_picture_card_wrong")
        assertEquals(listOf(false to false), results)
        // Danach geht es weiter wie nach einem Fehltipp: die richtige Karte bleibt möglich.
        dragOntoFrame("sentence_picture_card_correct")
        assertEquals(listOf(false to false, true to false), results)
    }

    @Test
    fun droppingAnywhereElseCountsNothing() {
        show()
        drag("sentence_picture_card_correct", Offset(0f, 260f))
        drag("sentence_picture_card_wrong", Offset(-40f, 220f))
        assertEquals(emptyList<Pair<Boolean, Boolean>>(), results)
    }

    @Test
    fun noResolveButtonEvenAfterManyMisses() {
        show()
        repeat(4) {
            rule.onNodeWithTag("sentence_picture_card_wrong").performClick()
            rule.waitForIdle()
        }
        val label = InstrumentationRegistry.getInstrumentation().targetContext.getString(R.string.resolve)
        rule.onNodeWithText(label).assertDoesNotExist()
        assertEquals(List(4) { false to false }, results)
    }
}
