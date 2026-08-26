package app.abcvorschule.ui.exercise

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.theme.AbcTheme
import app.abcvorschule.ui.theme.Cream
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Kein Assert, sondern ein Blick: rendert den Satz-Architekten in den Breiten und
 * Systemschriftgrößen, die zählen, und legt PNGs im externen Cache ab (`adb pull`).
 * Zusicherungen stehen in [SentenceOrderPegBoundsTest] — dieser Lauf ist dafür da,
 * dass ein Mensch Silhouette, Abstände und den Fill-Morph beurteilen kann, ohne
 * sich durch fünf Trainer bis Lektion 13 zu spielen.
 */
@RunWith(AndroidJUnit4::class)
class SentenceOrderPegShotTest {
    @get:Rule
    val rule = createComposeRule()

    private val cases = listOf(
        Triple(listOf("Oma", "hat", "einen", "Hut"), 420.dp, 1.3f),
        Triple(listOf("Oma", "hat", "einen", "Hut"), 360.dp, 1.3f),
        Triple(listOf("der", "Fisch", "schwimmt"), 420.dp, 1.3f),
        Triple(listOf("der", "Fisch", "schwimmt"), 360.dp, 1.3f),
        Triple(listOf("der", "Fisch", "schwimmt"), 320.dp, 2f),
        Triple(listOf("hier", "sind", "Häuser"), 360.dp, 1.3f),
        Triple(listOf("Mama"), 360.dp, 1.3f),
    )

    @Test
    fun captureThePegRow() {
        var words by mutableStateOf(cases.first().first)
        var stageWidth by mutableStateOf(cases.first().second)
        var fontScale by mutableStateOf(cases.first().third)
        var filled by mutableStateOf(false)

        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = fontScale),
            ) {
                AbcTheme {
                    Box(
                        modifier = Modifier
                            .size(width = stageWidth, height = 640.dp)
                            .background(Cream)
                            .testTag("stage"),
                    ) {
                        SentenceOrderTrainer(
                            round = SentenceOrderRound(promptTts = "", sentenceId = "shot"),
                            roundIndex = 0,
                            words = words,
                            atomIds = words,
                            illustrationEmoji = null,
                            // Beginner zeigt die Ghost-Wörter, damit auf dem Bild zu
                            // sehen ist, wie das Wort im Peg sitzt.
                            scaffoldFor = { if (filled) ScaffoldLevel.Beginner else ScaffoldLevel.Advanced },
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

        // filesDir, nicht externalCacheDir: auf einem Emulator ohne eingerichteten
        // externen Speicher ist der null, und die PNGs landen im Nichts.
        // Abholen mit: adb exec-out run-as app.abcvorschule cat files/pegshots/<name>
        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "pegshots",
        ).apply { mkdirs() }

        cases.forEachIndexed { index, (sentence, width, scale) ->
            listOf(false, true).forEach { ghost ->
                rule.runOnUiThread {
                    words = sentence
                    stageWidth = width
                    fontScale = scale
                    filled = ghost
                }
                rule.waitForIdle()
                val bitmap = rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap()
                val name = "%02d-%s-%s-fs%s-%s.png".format(
                    index,
                    sentence.size,
                    width.value.toInt(),
                    scale.toString().replace('.', '_'),
                    if (ghost) "ghost" else "empty",
                )
                File(dir, name).outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
