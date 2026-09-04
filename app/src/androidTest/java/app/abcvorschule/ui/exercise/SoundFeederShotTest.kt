package app.abcvorschule.ui.exercise

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
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
import app.abcvorschule.content.Atom
import app.abcvorschule.content.AtomKind
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.FeederSide
import app.abcvorschule.content.Gender
import app.abcvorschule.content.NounClass
import app.abcvorschule.content.PackManifest
import app.abcvorschule.content.SoundFeederCard
import app.abcvorschule.content.SoundFeederRound
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.AbcTheme
import app.abcvorschule.ui.theme.Cream
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Standbilder des Laut-Fressers in drei Breiten und zwei Systemschriftgrößen. Kein
 * Assertion-Test: er zeigt, ob der Bauch-Glyph „Sch / sch" bei font_scale 1.3 in
 * der Figur bleibt und ob Karte, Haufen und Fresser zusammen auf 320dp passen.
 *
 * `createComposeRule` erlaubt `setContent` nur einmal pro Test — deshalb eine
 * eigene `@Test`-Methode pro Breite × Schriftgröße, gemeinsam über [capture].
 */
@RunWith(AndroidJUnit4::class)
class SoundFeederShotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun noun(id: String, display: String, emoji: String) = Atom(
        id = id, lemma = display, display = display, emoji = emoji, kind = AtomKind.other,
        gender = Gender.f, nounClass = NounClass.thing,
    )

    private val pack = ContentPack(
        manifest = PackManifest(schemaVersion = 1, packId = "test", title = "Test Pack"),
        atoms = listOf(
            Atom(id = "letter-s", lemma = "S", display = "S", emoji = "", kind = AtomKind.letter),
            Atom(id = "letter-sch", lemma = "Sch", display = "Sch", emoji = "", kind = AtomKind.letter),
            noun("sonne", "Sonne", "☀️"), noun("salat", "Salat", "🥗"),
            noun("schuh", "Schuh", "👟"), noun("schaf", "Schaf", "🐑"),
        ).associateBy { it.id },
        sentences = emptyMap(),
        tasks = emptyMap(),
        finales = emptyMap(),
        lessons = emptyList(),
    )

    private val round = SoundFeederRound(
        promptTts = "Füttere die Laut-Fresser.",
        leftAtomId = "letter-s",
        rightAtomId = "letter-sch",
        cards = listOf(
            SoundFeederCard("sonne", FeederSide.left), SoundFeederCard("schuh", FeederSide.right),
            SoundFeederCard("salat", FeederSide.left), SoundFeederCard("schaf", FeederSide.right),
        ),
    )

    private val dir: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "feedershots").apply { mkdirs() }
    }

    private fun capture(width: Int, scale: Float, ttsAvailable: Boolean = false) {
        rule.setContent {
            val base = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = scale)) {
                AbcTheme {
                    Box(Modifier.size(width.dp, 640.dp).background(Cream).testTag("shot")) {
                        // Dieselbe Polsterung, die TaskShell um jeden Trainer legt: ohne
                        // sie rechnete das Bild mit 296dp Bühne, das Gerät aber mit 256 —
                        // und der Glyph sah auf dem Standbild breiter aus, als er darf.
                        Box(Modifier.padding(horizontal = AbcDimens.screenHorizontal)) {
                            SoundFeederTrainer(
                                round = round, roundIndex = 0, pack = pack,
                                ttsAvailable = ttsAvailable, speaking = false,
                                onSpeakParts = {}, onSpeakPartsSequenced = { _, _ -> },
                                onSpeakFeedback = {},
                                onSpeakFeedbackVoiced = {}, onResult = { _, _, _ -> },
                            )
                        }
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        val bitmap = rule.onNodeWithTag("shot").captureToImage().asAndroidBitmap()
        val suffix = if (ttsAvailable) "-tts" else ""
        File(dir, "feeder-${width}dp-${scale}${suffix}.png").outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }

    @Test
    fun width320Scale1_0() = capture(320, 1.0f)

    @Test
    fun width320Scale1_3() = capture(320, 1.3f)

    @Test
    fun width360Scale1_0() = capture(360, 1.0f)

    @Test
    fun width360Scale1_3() = capture(360, 1.3f)

    @Test
    fun width411Scale1_0() = capture(411, 1.0f)

    @Test
    fun width411Scale1_3() = capture(411, 1.3f)

    /**
     * Der Auslieferungsfall: mit deutscher Stimme steht kein Wort unter dem Emoji, die
     * Karte bleibt quadratisch. Die anderen Bilder zeigen den Fallback ohne TTS — das
     * ist die höhere Karte, nicht die, die das Kind normalerweise sieht.
     */
    @Test
    fun width360Scale1_3WithVoice() = capture(360, 1.3f, ttsAvailable = true)
}
