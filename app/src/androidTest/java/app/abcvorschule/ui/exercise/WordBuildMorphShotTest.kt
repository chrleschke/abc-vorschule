package app.abcvorschule.ui.exercise

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.abcvorschule.content.Atom
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.PackManifest
import app.abcvorschule.content.WordBlock
import app.abcvorschule.content.WordBuildRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.theme.AbcTheme
import app.abcvorschule.ui.theme.Cream
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Filmstreifen des Squish-Settle-Morphs im Wort-Bauer, Schwester von
 * [SentenceOrderMorphShotTest]: die Testuhr wird angehalten und Bild für Bild
 * weitergedreht, damit der Wackler beurteilbar ist. Ein Standbild zeigt eine
 * Feder nicht.
 */
@RunWith(AndroidJUnit4::class)
class WordBuildMorphShotTest {
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

    private val round = WordBuildRound(
        promptTts = "",
        targetAtomId = "mama",
        blocks = listOf(WordBlock("ma", "Ma"), WordBlock("ma", "ma")),
        distractors = listOf(WordBlock("mi", "mi"), WordBlock("mo", "mo")),
    )

    /** Millisekunden nach dem Einrasten. Die Feder läuft bei rund 700ms aus. */
    private val frames = listOf(0L, 40L, 80L, 120L, 180L, 260L, 360L, 520L, 760L)

    @Test
    fun captureTheFillMorph() {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(
                LocalDensity provides Density(density = base.density, fontScale = 1.3f),
            ) {
                AbcTheme {
                    Box(
                        modifier = Modifier
                            .size(width = 360.dp, height = 640.dp)
                            .background(Cream)
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
                            onSpeakAndAwait = {},
                            onResult = { _, _, _ -> },
                        )
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(64)

        // Baustein wählen, dann auf den passenden Rahmen tippen — genau der Weg,
        // den ein Kind ohne Ziehen nimmt. Der *erste* Rahmen, damit der Morph
        // nicht vom fertigen Wort überdeckt wird.
        rule.onNodeWithTag("block_Ma").performClick()
        rule.mainClock.advanceTimeBy(16)
        rule.onNodeWithTag("frame_0").performClick()

        val dir = File(
            InstrumentationRegistry.getInstrumentation().targetContext.filesDir,
            "wordbuildmorphshots",
        ).apply { mkdirs() }

        var elapsed = 0L
        frames.forEach { at ->
            rule.mainClock.advanceTimeBy(at - elapsed)
            elapsed = at
            val bitmap = rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap()
            File(dir, "morph-%04d.png".format(at)).outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        }
    }
}
