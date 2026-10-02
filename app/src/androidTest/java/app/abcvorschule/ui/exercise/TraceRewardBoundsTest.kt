package app.abcvorschule.ui.exercise

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.abcvorschule.R
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.LetterTraceSpec
import app.abcvorschule.ui.theme.AbcTheme
import app.abcvorschule.ui.world.LocalChromeColors
import app.abcvorschule.ui.world.TrainerWorld
import app.abcvorschule.ui.world.WorldBackground
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Die Belohnungskarte des Spurensuchers, gegen das echte Layout gemessen: „A wie Ampel"
 * war unten nicht lesbar. Die Karte saß im quadratischen Glyph-Kasten, und wo der
 * schmaler war als die Karte hoch (schmales Telefon, große Systemschrift), schnitt die
 * Spalte das Wort unten ab. Geprüft wird: die Karte bleibt in der Bühne, das Wort läuft
 * nicht über und bricht nicht mitten im Wort um.
 *
 * Nebenbei legt der Lauf je Fall ein Standbild unter `filesDir/rewardshots` ab
 * (Abholweg A in der README).
 */
@RunWith(AndroidJUnit4::class)
class TraceRewardBoundsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pack by lazy { ContentRepository.fromContext(context).load() }
    private val dir: File by lazy { File(context.filesDir, "rewardshots").apply { mkdirs() } }

    /** Gemeldet (L01), die längsten Wortzeilen im Pack (L12), und die kürzeste (L01). */
    private val taskIds = listOf("l01-t4", "l12-t5", "l12-t5b", "l01-t3")

    /**
     * Bühnen wie in der `TaskShell` (Breite ohne deren 20dp-Rand, Höhe unter der
     * Kopfzeile): Motorola edge 60 pro, ein 360dp- und ein 320dp-Telefon.
     */
    private val stages = listOf(394.dp to 790.dp, 320.dp to 600.dp, 280.dp to 520.dp)

    /** 1.3 ist die Systemschriftgröße des Testgeräts, 2.0 der Härtefall. */
    private val fontScales = listOf(1f, 1.3f, 2f)

    @Test
    fun rewardWordStaysFullyVisible() {
        val rounds = taskIds.map { id ->
            (pack.tasks.getValue(id) as LetterTraceSpec).rounds.first()
        }
        var round by mutableStateOf(rounds.first())
        var roundIndex by mutableIntStateOf(0)
        var stage by mutableStateOf(stages.first())
        var fontScale by mutableStateOf(fontScales.first())

        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AbcTheme {
                    Box(Modifier.size(width = stage.first, height = stage.second).testTag("stage")) {
                        WorldBackground(TrainerWorld.Jungle, Modifier.fillMaxSize())
                        CompositionLocalProvider(LocalChromeColors provides TrainerWorld.Jungle.chrome) {
                            LetterTraceTrainer(
                                round = round,
                                roundIndex = roundIndex,
                                atom = pack.atom(round.atomId),
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
        }

        val failures = mutableListOf<String>()
        rounds.forEach { r ->
            stages.forEach { s ->
                fontScales.forEach { scale ->
                    rule.runOnUiThread {
                        round = r
                        stage = s
                        fontScale = scale
                        roundIndex += 1
                    }
                    rule.waitForIdle()
                    showReward(r)
                    val name = "${r.atomId}-${s.first.value.toInt()}x${s.second.value.toInt()}-fs$scale"
                    save("$name.png")
                    failures += problems(r, s.first, scale)
                }
            }
        }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    /** Vier Tipps ohne Ziehen schalten „Auflösen" frei (R15), das zeigt die Karte. */
    private fun showReward(round: LetterTraceRound) {
        repeat(TraceProgress.TapsBeforeResolve) {
            rule.onNodeWithTag("trace_canvas_${round.atomId}").performClick()
            rule.waitForIdle()
        }
        rule.onNodeWithText(context.getString(R.string.resolve)).performClick()
        rule.waitForIdle()
    }

    private fun problems(round: LetterTraceRound, stageWidth: Dp, fontScale: Float): List<String> {
        val case = "„${round.rewardTts}\" auf $stageWidth bei font_scale $fontScale"
        val slack = 1f
        val stage = rule.onNodeWithTag("stage").fetchSemanticsNode().boundsInRoot
        val card = rule.onNodeWithTag("trace_reward_${round.atomId}", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val wordNode = rule.onNodeWithTag("trace_reward_word", useUnmergedTree = true)
            .fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        wordNode.config[SemanticsActions.GetTextLayoutResult].action?.invoke(layouts)
        val layout = layouts.single()
        val text = layout.layoutInput.text.text
        val out = mutableListOf<String>()
        if (layout.hasVisualOverflow) out += "$case: Wort läuft über (abgeschnitten)"
        if (card.top < stage.top - slack || card.bottom > stage.bottom + slack ||
            card.left < stage.left - slack || card.right > stage.right + slack
        ) {
            out += "$case: Karte $card ragt aus der Bühne $stage"
        }
        if (wordNode.boundsInRoot.bottom > card.bottom + slack) {
            out += "$case: Wort endet unter der Karte"
        }
        for (line in 0 until layout.lineCount - 1) {
            val end = layout.getLineEnd(line)
            val atBoundary = text.getOrNull(end - 1) == ' ' || text.getOrNull(end) == ' '
            if (!atBoundary) out += "$case: Umbruch mitten im Wort nach „${text.substring(0, end)}\""
        }
        assertFalse(text.isEmpty())
        return out
    }

    private fun save(name: String) {
        val bitmap = rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap()
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
