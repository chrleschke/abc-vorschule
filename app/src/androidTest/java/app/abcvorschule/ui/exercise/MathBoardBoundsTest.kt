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
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.abcvorschule.ui.theme.AbcTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Der gemeldete Fehler, gegen das echte Layout gemessen statt gegen die Rechnung
 * (gleiche Bauart wie [SymbolHuntTileBoundsTest]): in der Rechen-Mengenwahl brachen
 * die drei Antwort-Kacheln auf einem Telefon in zwei Zeilen um. Weil [ExerciseStage]
 * den Antwortblock ungewichtet gegen die volle Höhe misst, blieb dem Aufgabenblock
 * mit seinem `weight(1f)` nichts übrig — die Rechnung war abgeschnitten und nicht
 * mehr lesbar. `MathBoardSizingTest` prüft die Geometrie, dieser Test prüft, dass
 * das Gerenderte auch wirklich auf die Bühne passt.
 */
@RunWith(AndroidJUnit4::class)
class MathBoardBoundsTest {
    @get:Rule
    val rule = createComposeRule()

    /**
     * Bühne = Bildschirmbreite minus `AbcDimens.screenHorizontal` je Seite: ein
     * 320dp-Gerät, ein 360dp-Gerät, ein 412dp-Gerät.
     */
    private val stageWidths = listOf(280.dp, 320.dp, 372.dp)

    /** Kurzes Gerät, Telefon, Tablet — nach Abzug von Kopfzeile und Fortschritt. */
    private val stageHeights = listOf(480.dp, 600.dp, 780.dp)

    /** 1.0, die 1.3 des Testgeräts und der Härtefall 2.0. */
    private val fontScales = listOf(1f, 1.3f, 2f)

    /**
     * Runden mit Bildwort, also Mengen bis 10 (§8). „4 + 3" ist die gemeldete Runde
     * aus Lektion 1, „5 + 5" der höchste Kachelstapel, den es dort geben kann,
     * „1 + 1" die schmalste Kachel.
     */
    private val rounds = listOf(
        Triple(4, 3, listOf(6, 7, 8)),
        Triple(5, 5, listOf(8, 9, 10)),
        Triple(3, 2, listOf(4, 5, 6)),
        Triple(1, 1, listOf(1, 2, 3)),
    )

    @Test
    fun taskAndChoicesBothStayInsideTheStage() {
        // setContent darf pro Test nur einmal laufen, also treiben State-Objekte die
        // Fälle statt einer Schleife um setContent.
        var stageWidth by mutableStateOf(stageWidths.first())
        var stageHeight by mutableStateOf(stageHeights.first())
        var fontScale by mutableStateOf(fontScales.first())
        var round by mutableStateOf(rounds.first())

        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AbcTheme {
                    Box(
                        modifier = Modifier
                            .size(width = stageWidth, height = stageHeight)
                            .testTag("math_stage"),
                    ) {
                        VisualQuantityBoard(
                            emoji = "🐜",
                            left = round.first,
                            right = round.second,
                            operation = MathOperation.Add,
                            choices = round.third,
                            onChoose = {},
                            // Nach zwei Fehlversuchen kommt der Auflösen-Knopf dazu —
                            // der Fall, für den [MathBoardSizing.ResolveReserveDp] von
                            // Anfang an Platz hält (§9: der Aufgabenblock steht still).
                            missCount = 2,
                            onResolve = {},
                            ttsAvailable = true,
                        )
                    }
                }
            }
        }

        stageWidths.forEach { width ->
            stageHeights.forEach { height ->
                fontScales.forEach { scale ->
                    rounds.forEach { current ->
                        stageWidth = width
                        stageHeight = height
                        fontScale = scale
                        round = current
                        rule.waitForIdle()

                        val case = "${width.value.toInt()}x${height.value.toInt()}dp, " +
                            "font_scale $scale, ${current.first} + ${current.second}"
                        val stage = rule.onNodeWithTag("math_stage").getUnclippedBoundsInRoot()
                        val prompt = rule.onNodeWithTag("math_prompt").getUnclippedBoundsInRoot()
                        val choices = rule.onNodeWithTag("math_choices").getUnclippedBoundsInRoot()

                        assertTrue(
                            "Aufgabenblock ragt oben aus der Bühne ($case): $prompt in $stage",
                            prompt.top >= stage.top - Slack,
                        )
                        assertTrue(
                            "Aufgabenblock ragt unten aus der Bühne ($case): $prompt in $stage",
                            prompt.bottom <= stage.bottom + Slack,
                        )
                        assertTrue(
                            "Aufgabenblock ragt seitlich aus der Bühne ($case): $prompt in $stage",
                            prompt.left >= stage.left - Slack && prompt.right <= stage.right + Slack,
                        )
                        assertTrue(
                            "Antwortblock ragt seitlich aus der Bühne ($case): $choices in $stage",
                            choices.left >= stage.left - Slack && choices.right <= stage.right + Slack,
                        )
                        assertTrue(
                            "Aufgabe und Antworten überlappen ($case): $prompt / $choices",
                            prompt.bottom <= choices.top + Slack,
                        )

                        // Rangfolge 1: eine Reihe. Bräche sie um, stünde die erste
                        // Kachel eine Zeilenhöhe über der letzten.
                        val tops = current.third.map {
                            rule.onNodeWithTag("math_choice_$it").getUnclippedBoundsInRoot().top
                        }
                        assertTrue(
                            "Die drei Kacheln stehen nicht in einer Reihe ($case): $tops",
                            tops.all { (it - tops.first()).value in -Slack.value..Slack.value },
                        )
                    }
                }
            }
        }
    }

    private companion object {
        /** Rundungsluft der Layout-Messung, nicht der erlaubte Überstand. */
        val Slack: Dp = 1.dp
    }
}
