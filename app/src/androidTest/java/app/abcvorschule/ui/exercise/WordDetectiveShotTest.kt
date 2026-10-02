package app.abcvorschule.ui.exercise

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.AbcTheme
import app.abcvorschule.ui.world.LocalChromeColors
import app.abcvorschule.ui.world.TrainerWorld
import app.abcvorschule.ui.world.WorldBackground
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Standbilder des Wort-Detektivs unter dem Sternenhimmel (Spec
 * `2026-10-02-wort-detektiv-sternenhimmel-design.md`): kurzes und langes Wort, ein
 * gefundener Buchstabe, ein gefundenes „Sch", der Stern im Flug, alle gefunden, und
 * dasselbe auf einer 360-dp-Breite, wo „Taschenlampe" an der Wortfuge trennt und die
 * Silhouetten vom Teleskop wegbleiben müssen. Kein Assert, wie [NightWorldShotTest]:
 * die Bilder sind die Prüfung. Runden aus dem ausgelieferten Pack, ohne eine Lektion
 * zu spielen. Abholen mit
 * `adb exec-out run-as app.silbo.abcvorschule cat files/detectiveshots/<name>`.
 */
@RunWith(AndroidJUnit4::class)
class WordDetectiveShotTest {
    @get:Rule
    val rule = createComposeRule()

    private val pack by lazy {
        ContentRepository.fromContext(InstrumentationRegistry.getInstrumentation().targetContext).load()
    }

    private val dir: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "detectiveshots").apply { mkdirs() }
    }

    private fun round(lessonId: String, wordAtomId: String): SymbolInWordRound =
        SymbolInWordDerivation.buildRounds(pack, pack.lesson(lessonId)).first { it.wordAtomId == wordAtomId }

    @Composable
    private fun InWorld(width: Dp?, height: Dp?, content: @Composable () -> Unit) {
        val world = TrainerWorld.Stars
        AbcTheme {
            val box = Modifier
                .then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth())
                .then(if (height != null) Modifier.height(height) else Modifier.fillMaxHeight())
            Box(box.testTag("shot")) {
                WorldBackground(world, Modifier.fillMaxSize())
                CompositionLocalProvider(LocalChromeColors provides world.chrome) {
                    // Die Kopfzeile der TaskShell fehlt hier; ihr Platz bleibt frei.
                    Box(
                        Modifier
                            .padding(top = 112.dp, bottom = AbcDimens.screenBottomExtra)
                            .padding(horizontal = AbcDimens.screenHorizontal),
                    ) {
                        content()
                    }
                }
            }
        }
    }

    private fun save(name: String) {
        val bitmap = rule.onNodeWithTag("shot").captureToImage().asAndroidBitmap()
        File(dir, name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun show(
        round: SymbolInWordRound,
        width: Dp? = null,
        height: Dp? = null,
        scaffold: ScaffoldLevel = ScaffoldLevel.Advanced,
    ) {
        rule.mainClock.autoAdvance = false
        rule.setContent {
            InWorld(width, height) {
                SymbolInWordTrainer(
                    round = round,
                    roundIndex = 0,
                    pack = pack,
                    scaffoldFor = { scaffold },
                    ttsAvailable = true,
                    speaking = false,
                    onSpeakPrompt = {},
                    onSpeak = {},
                    onSpeakFeedback = {},
                    onResult = { _, _, _ -> },
                )
            }
        }
        rule.mainClock.advanceTimeBy(1_500)
    }

    private fun tap(index: Int) {
        rule.onNodeWithTag("detective_segment_$index").performClick()
        rule.mainClock.advanceTimeBy(32)
    }

    /** „Papa": kurzes Wort, P gefunden (zwei Sterne), der Stern unterwegs, dann gelandet. */
    @Test
    fun shortWordWithALetterFound() {
        val papa = round("l03", "papa")
        show(papa)
        save("short-start.png")
        tap(papa.targetIndices.first())
        rule.mainClock.advanceTimeBy(200)
        save("short-flight.png")
        rule.mainClock.advanceTimeBy(1_000)
        save("short-found.png")
        // Ein Fehltipp mitten in der Drehung.
        tap(1)
        rule.mainClock.advanceTimeBy(150)
        save("short-miss.png")
        rule.mainClock.advanceTimeBy(1_000)
        tap(papa.targetIndices.last())
        rule.mainClock.advanceTimeBy(700)
        save("short-complete.png")
    }

    /** „Schneemann": „Sch" gefunden (drei Sterne, gestricheltes Dreieck), eine Silhouette gefüllt. */
    @Test
    fun longWordWithSchFound() {
        val word = round("l31", "schneemann")
        show(word)
        save("sch-start.png")
        tap(word.targetIndices.single())
        rule.mainClock.advanceTimeBy(220)
        save("sch-flight.png")
        rule.mainClock.advanceTimeBy(1_200)
        save("sch-found.png")
    }

    /** Das längste Wort des Packs auf voller Breite. */
    @Test
    fun longestWordOnTheTestPhone() {
        val word = round("l34", "taschenlampe")
        show(word, scaffold = ScaffoldLevel.Beginner)
        save("longest-start.png")
        tap(word.targetIndices.single())
        rule.mainClock.advanceTimeBy(1_200)
        save("longest-found.png")
    }

    /** 360 dp: „Taschen-/lampe" trennt an der Fuge; Silhouetten und Flug bleiben vom Teleskop weg. */
    @Test
    fun narrowPhoneBreaksAtTheJoint() {
        val word = round("l34", "taschenlampe")
        show(word, width = 360.dp)
        save("narrow-start.png")
        tap(word.targetIndices.single())
        rule.mainClock.advanceTimeBy(220)
        save("narrow-flight.png")
        rule.mainClock.advanceTimeBy(1_200)
        save("narrow-found.png")
    }

    @Test
    fun narrowPhoneTwoSilhouettes() {
        val papa = round("l03", "papa")
        show(papa, width = 360.dp)
        tap(papa.targetIndices.first())
        rule.mainClock.advanceTimeBy(200)
        save("narrow-papa-flight.png")
        rule.mainClock.advanceTimeBy(1_000)
        save("narrow-papa-found.png")
    }

    /** 360×640 dp, die kurze Gerätequelle: die Gruppe gibt erst Luft her, dann Größe, und passt. */
    @Test
    fun shortPhoneFitsTheBrokenWord() {
        val word = round("l34", "taschenlampe")
        show(word, width = 360.dp, height = 640.dp)
        tap(word.targetIndices.single())
        rule.mainClock.advanceTimeBy(1_200)
        save("short-phone-found.png")
    }
}
