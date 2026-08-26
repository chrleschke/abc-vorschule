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
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.SymbolHuntDerivation
import app.abcvorschule.content.SymbolHuntMode
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.ui.theme.AbcTheme
import app.abcvorschule.ui.theme.Cream
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Filmstreifen des Jagd-Kachel-Morphs (siehe [HuntTileMorph]). Wie beim
 * Squish-Settle des Satz-Architekten wird die Testuhr angehalten und Bild für
 * Bild weitergedreht — ein Standbild zeigt weder das langsame Wachsen beim
 * Halten noch das Wegploppen.
 *
 * Drei Streifen: Anfassen/Halten (Kachel bläht sich verzögert bis +10 % auf),
 * Loslassen auf einem Distraktor (Kollaps und Plopp zurück in Form) und
 * Loslassen auf der Zielkachel (Kollaps, dann weg).
 */
@RunWith(AndroidJUnit4::class)
class SymbolHuntMorphShotTest {
    @get:Rule
    val rule = createComposeRule()

    private val pack = ContentRepository
        .fromContext(InstrumentationRegistry.getInstrumentation().targetContext)
        .load()

    private val round = SymbolHuntRound(
        promptTts = SymbolHuntDerivation.PromptLetter,
        targetAtomId = "letter-a",
        mode = SymbolHuntMode.letter,
        distractorPool = listOf("letter-m", "letter-i", "letter-o", "letter-t"),
    )

    /** Millisekunden ab Fingerkontakt. Die Halte-Phase läuft bei ~1620ms aus. */
    private val pressFrames = listOf(0L, 40L, 80L, 140L, 260L, 500L, 900L, 1400L, 1700L)

    /** Millisekunden ab Loslassen. Kollaps 90ms, Plopp-Feder danach. */
    private val releaseFrames = listOf(0L, 30L, 60L, 90L, 130L, 180L, 260L, 400L)

    @Test
    fun captureTheMorph() {
        val shots = start()
        // Erst ein Distraktor: die Kachel bleibt liegen, sie kollabiert und
        // ploppt zurück in Form, das Feld mischt sich neu.
        val decoy = tileTag(target = false)
        rule.onNodeWithTag(decoy).performTouchInput { down(center) }
        shots.filmstrip("press", pressFrames)
        rule.onNodeWithTag(decoy).performTouchInput { up() }
        shots.filmstrip("release-miss", releaseFrames)

        // Dann eine Zielkachel: gleicher Kollaps, aber sie verlässt das Feld.
        val hit = tileTag(target = true)
        rule.onNodeWithTag(hit).performTouchInput { down(center) }
        shots.filmstrip("hit-press", listOf(0L, 60L, 200L))
        rule.onNodeWithTag(hit).performTouchInput { up() }
        shots.filmstrip("hit-pop", releaseFrames)
    }

    /** Zielkacheln tragen die instanceIds 0…hitCount−1 (SymbolHuntProgress). */
    private fun tileTag(target: Boolean): String {
        val hitCount = requireNotNull(SymbolHuntDerivation.tileCounts(round.distractorPool.size)).first
        return "hunt_tile_${if (target) 0 else hitCount}"
    }

    private fun start(): Shots {
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
                        SymbolHuntTrainer(
                            round = round,
                            roundIndex = 0,
                            pack = pack,
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
        rule.mainClock.advanceTimeBy(64)
        return Shots()
    }

    private inner class Shots {
        // `additionalTestOutputDir` ist der Ordner, den AGP nach dem Lauf selbst
        // abholt (landet unter
        // app/build/outputs/connected_android_test_additional_output/…) — ohne den
        // kommen die Bilder auf einem nicht gerooteten Gerät nicht heraus:
        // `run-as` ist dort gesperrt und den App-eigenen externen Ordner sieht die
        // adb-Shell wegen Scoped Storage nicht. Fallback ist genau dieser Ordner,
        // falls der Lauf ohne AGP-Argument kommt.
        private val dir = File(
            InstrumentationRegistry.getArguments().getString("additionalTestOutputDir")
                ?: InstrumentationRegistry.getInstrumentation().targetContext
                    .getExternalFilesDir(null)?.path
                ?: error("Kein Ordner für die Filmstreifen"),
            "huntmorphshots",
        ).apply { require(mkdirs() || isDirectory) { "Kein Zielordner für die Filmstreifen: $this" } }

        fun filmstrip(name: String, frames: List<Long>) {
            var elapsed = 0L
            frames.forEach { at ->
                rule.mainClock.advanceTimeBy(at - elapsed)
                elapsed = at
                val bitmap = rule.onNodeWithTag("stage").captureToImage().asAndroidBitmap()
                File(dir, "$name-%04d.png".format(at)).outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
