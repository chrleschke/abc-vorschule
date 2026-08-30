package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.content.CountAddSpec
import app.abcvorschule.session.ScheduledTrainer
import app.abcvorschule.ui.theme.AbcTheme
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Die gerenderte Bühne einer Additionsrunde im Tipp-Modus. Zwei Befunde des
 * Abschluss-Reviews sind durch jede Prüfung gerutscht, weil sie nur an der
 * gemessenen Bühne sichtbar sind: eine Trefferfläche, die von den Constraints
 * ihrer Elternbox wieder kleingeklemmt wurde, und ein Antwortfeld, das im selben
 * Ereignis gesperrt und „richtig" wurde und darum nie grün werden konnte. Die
 * JVM-Tests daneben prüfen Arithmetik — beides hätten sie nicht gesehen.
 *
 * Gemessen wird bei font_scale 1.3 auf dem schmalen Gerät: das Testgerät.
 */
@RunWith(AndroidJUnit4::class)
class MathTenFrameStageTest {
    @get:Rule
    val rule = createComposeRule()

    /**
     * `7 + 5`: einer der echten Content-Fälle mit `left < 10`. Die erste Zeile ist
     * beim Rundenstart noch nicht voll, der Zehner rastet also mitten in der Runde
     * ein — genau der Übergang, bei dem der Aufgabenblock sprang.
     */
    private val round = CountAddRound(
        promptTts = "",
        iconAtomId = "apfel",
        left = 7,
        right = 5,
        answer = 12,
    )

    private val trainer = ScheduledTrainer(
        spec = CountAddSpec(id = "rechnen_test", rounds = listOf(round)),
    )

    private val results = mutableListOf<MathAttempt>()

    private fun stage(interactionLocked: Boolean = false) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1.3f),
            ) {
                AbcTheme {
                    Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                        MathExercise(
                            trainer = trainer,
                            round = round,
                            roundIndex = 0,
                            icon = "🍎",
                            input = MathInputMode.Typed,
                            showSymbolPrompt = true,
                            ttsAvailable = true,
                            speaking = false,
                            interactionLocked = interactionLocked,
                            onSpeakPrompt = {},
                            onSpeakFeedback = {},
                            onSpeakCounting = {},
                            onResult = { results.add(it) },
                        )
                    }
                }
            }
        }
    }

    @Test
    fun theActiveCellKeepsAChildSizedHitTarget() {
        stage()
        // Platzhalter 7 ist der nächste offene — der einzige, der reagiert.
        val active = rule.onNodeWithTag("ten_frame_cell_7").getUnclippedBoundsInRoot()
        assertTrue(
            "aktive Zelle $active unter ${TenFrame.MinHitTargetDp}dp",
            active.width.value >= TenFrame.MinHitTargetDp &&
                active.height.value >= TenFrame.MinHitTargetDp,
        )
        // Gegenprobe: der Nachbar bleibt zellgroß. Ohne sie wäre auch ein Feld
        // grün, das schlicht jede Zelle auf 40dp aufbläst.
        val quiet = rule.onNodeWithTag("ten_frame_cell_8").getUnclippedBoundsInRoot()
        assertTrue("Nachbar $quiet so groß wie die aktive", quiet.width.value < active.width.value)
    }

    @Test
    fun nothingIsFocusedWhileTheRoundIsBuiltUp() {
        stage()
        // Die System-Tastatur kommt erst, wenn das Kind einen Kasten antippt: sie
        // verdeckte sonst genau das Zehnerfeld, an dem gerechnet wird. Ein
        // fokussiertes Feld beim Aufbau ist die Tastatur.
        rule.onNodeWithTag("place_value_tens").assertIsNotFocused()
        rule.onNodeWithTag("place_value_ones").assertIsNotFocused()
    }

    @Test
    fun theStageHoldsStillWhenTheFirstTenLocksIn() {
        stage()
        val fieldBefore = boardBounds()
        val answerBefore = answerTop()

        // Drei Tipps machen die erste Zeile voll — der Zehner rastet ein, Rahmen
        // und Standmarke erscheinen.
        listOf(7, 8, 9).forEach { index ->
            rule.onNodeWithTag("ten_frame_cell_$index").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithTag("ten_frame_total_0").assertExists()

        val fieldAfter = boardBounds()
        assertSame("Oberkante des Zehnerfeldes", fieldBefore.first, fieldAfter.first)
        assertSame("Höhe des Zehnerfeldes", fieldBefore.second, fieldAfter.second)
        assertSame("Oberkante des Antwortblocks", answerBefore, answerTop())
    }

    @Test
    fun aCorrectAnswerConfirmsWithoutTurningTheFieldOff() {
        stage()
        rule.onNodeWithTag("place_value_tens").performTextInput("1")
        rule.onNodeWithTag("place_value_ones").performTextInput("2")
        rule.onNodeWithTag("place_value_submit").performClick()
        rule.waitForIdle()

        // Positive Kontrolle: ohne sie prüfte alles Weitere einen Screen, auf dem
        // schlicht nichts passiert ist.
        assertEquals(1, results.size)
        assertTrue("Ergebnis=${results.single()}", results.single().correct)

        // Der Befund: `enabled = !interactionLocked && !locked` schaltete das Feld
        // im selben Ereignis ab, das die Antwort als richtig annahm — M3 zeichnete
        // dann seinen Disabled-Zweig, und die grüne Bestätigung aus §8 kam nie
        // zustande. Die Farbe selbst ist nicht abfragbar; abfragbar ist der
        // Zustand, der sie verhindert hat.
        rule.onNodeWithTag("place_value_ones").assertIsEnabled()
        rule.onNodeWithTag("place_value_tens").assertIsEnabled()
    }

    @Test
    fun theAudioLockStillTurnsTheFieldOff() {
        // Gegenprobe zum Test darüber: `assertIsEnabled` wäre wertlos, wenn dieses
        // Feld gar nie abgeschaltet würde. Während des Audio-Locks wird es das.
        stage(interactionLocked = true)
        rule.onNodeWithTag("place_value_ones").assertIsNotEnabled()
        rule.onNodeWithTag("place_value_tens").assertIsNotEnabled()
    }

    /** Oberkante und Höhe des Zehnerfeldes — der Aufgabenblock der Bühne. */
    private fun boardBounds(): Pair<Dp, Dp> =
        rule.onNodeWithTag("ten_frame").getUnclippedBoundsInRoot().let { it.top to it.height }

    private fun answerTop(): Dp =
        rule.onNodeWithTag("place_value_ones").getUnclippedBoundsInRoot().top

    /** 1dp Spiel für Rundung beim Messen — mehr ist ein Sprung. */
    private fun assertSame(what: String, before: Dp, after: Dp) {
        assertTrue(
            "$what springt von ${before.value}dp auf ${after.value}dp",
            abs(before.value - after.value) <= 1f,
        )
    }
}
