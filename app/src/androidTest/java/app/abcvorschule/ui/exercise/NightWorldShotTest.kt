package app.abcvorschule.ui.exercise

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.abcvorschule.content.Atom
import app.abcvorschule.content.AtomKind
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.FeederSide
import app.abcvorschule.content.Gender
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.NounClass
import app.abcvorschule.content.PackManifest
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.SoundFeederCard
import app.abcvorschule.content.SoundFeederRound
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
 * Standbilder der Trainer in ihrer Nachtwelt, bildschirmfüllend wie in der `TaskShell`
 * (Welt dahinter, Nacht-Chrome, 20dp Rand). Kein Assert: der Lauf zeigt, ob Leine,
 * Pfosten und Figuren dort sitzen, wo sie sollen, ohne sich bis zu einem Laut-Fresser
 * durchzuspielen. Abholen mit
 * `adb exec-out run-as app.silbo.abcvorschule cat files/worldshots/<name>`.
 */
@RunWith(AndroidJUnit4::class)
class NightWorldShotTest {
    @get:Rule
    val rule = createComposeRule()

    private val dir: File by lazy {
        File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "worldshots").apply { mkdirs() }
    }

    private fun noun(id: String, display: String, emoji: String) = Atom(
        id = id, lemma = display, display = display, emoji = emoji, kind = AtomKind.other,
        gender = Gender.f, nounClass = NounClass.thing,
    )

    private val feederPack = ContentPack(
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

    @Composable
    private fun InWorld(world: TrainerWorld, content: @Composable () -> Unit) {
        AbcTheme {
            Box(Modifier.fillMaxSize().testTag("shot")) {
                WorldBackground(world, Modifier.fillMaxSize())
                CompositionLocalProvider(LocalChromeColors provides world.chrome) {
                    // Die Kopfzeile der TaskShell fehlt hier; ihr Platz bleibt frei.
                    Box(Modifier.padding(top = 112.dp).padding(horizontal = AbcDimens.screenHorizontal)) {
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

    @Test
    fun feederInTheCave() {
        rule.setContent {
            InWorld(TrainerWorld.Cave) {
                SoundFeederTrainer(
                    round = SoundFeederRound(
                        promptTts = "Füttere die Laut-Fresser.",
                        leftAtomId = "letter-s",
                        rightAtomId = "letter-sch",
                        cards = listOf(
                            SoundFeederCard("sonne", FeederSide.left),
                            SoundFeederCard("schuh", FeederSide.right),
                            SoundFeederCard("salat", FeederSide.left),
                            SoundFeederCard("schaf", FeederSide.right),
                        ),
                    ),
                    roundIndex = 0, pack = feederPack,
                    ttsAvailable = true, speaking = false,
                    onSpeakParts = {}, onSpeakPartsSequenced = { _, _ -> },
                    onSpeakFeedback = {}, onSpeakFeedbackVoiced = {}, onResult = { _, _, _ -> },
                )
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        save("cave-feeder.png")
    }

    private fun sentence(words: List<String>, emoji: String, place: Int, name: String) {
        rule.setContent {
            InWorld(TrainerWorld.Garden) {
                SentenceOrderTrainer(
                    round = SentenceOrderRound(promptTts = "", sentenceId = name),
                    roundIndex = 0,
                    words = words,
                    atomIds = words,
                    illustrationEmoji = emoji,
                    scaffoldFor = { ScaffoldLevel.Advanced },
                    ttsAvailable = true,
                    speaking = false,
                    onSpeakPrompt = {},
                    onSpeak = {},
                    onResult = { _, _, _ -> },
                )
            }
        }
        rule.waitForIdle()
        // Die ersten Wörter aufhängen: Karte wählen, dann den Peg antippen.
        words.take(place).forEachIndexed { index, word ->
            rule.onNodeWithTag("card_$word").performClick()
            rule.onNodeWithTag("peg_$index").performClick()
        }
        // Mitten im Nachschwingen des zuletzt aufgehängten Wortes.
        rule.mainClock.advanceTimeBy(120)
        save("$name-swing.png")
        rule.mainClock.advanceTimeBy(2_000)
        save("$name.png")
    }

    @Test
    fun shortSentenceOnTheLine() = sentence(listOf("Oma", "ist", "da"), "👵", place = 1, name = "garden-short")

    @Test
    fun longSentenceOnTheLine() =
        sentence(listOf("der", "Schneemann", "ist", "groß"), "⛄", place = 2, name = "garden-long")

    /**
     * Das Bilderbuch des Satz-Verstehers: Vorlesen (Balken glimmen), Flug der richtigen
     * Karte in den Rahmen, gelandet, Umblättern zur zweiten Runde, neue Seite. Mit
     * angehaltener Uhr, damit die Zwischenbilder des Flugs und des Blätterns stehen.
     */
    @Test
    fun bookInTheReadingNook() = book(ttsAvailable = true, name = "nook")

    /** Ohne deutsches TTS: der Satz steht als Text auf der Seite (§7). */
    @Test
    fun bookWithoutGermanVoice() = book(ttsAvailable = false, name = "nook-text")

    private fun book(ttsAvailable: Boolean, name: String) {
        val real = ContentRepository.fromContext(InstrumentationRegistry.getInstrumentation().targetContext).load()
        val spec = real.tasks.getValue("l01-sp1") as app.abcvorschule.content.SentencePictureSpec
        var roundIndex by mutableStateOf(0)
        var speaking by mutableStateOf(true)
        rule.mainClock.autoAdvance = false
        rule.setContent {
            InWorld(TrainerWorld.ReadingNook) {
                // Die TaskShell lässt unten Luft (AbcDimens.screenBottomExtra).
                Box(Modifier.padding(bottom = AbcDimens.screenBottomExtra)) {
                    SentencePictureTrainer(
                        round = spec.rounds[roundIndex],
                        roundIndex = roundIndex,
                        pack = real,
                        ttsAvailable = ttsAvailable,
                        speaking = speaking,
                        onSpeakPrompt = {},
                        onResult = { _, _, _ -> },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_200)
        save("$name-reading.png")
        speaking = false
        rule.mainClock.advanceTimeBy(800)
        save("$name-quiet.png")
        rule.onNodeWithTag("sentence_picture_card_correct").performClick()
        rule.mainClock.advanceTimeBy(200)
        save("$name-flight-1.png")
        rule.mainClock.advanceTimeBy(200)
        save("$name-flight-2.png")
        rule.mainClock.advanceTimeBy(1_000)
        save("$name-landed.png")
        roundIndex = 1
        rule.mainClock.advanceTimeBy(150)
        save("$name-turn-1.png")
        rule.mainClock.advanceTimeBy(150)
        save("$name-turn-2.png")
        rule.mainClock.advanceTimeBy(150)
        save("$name-turn-3.png")
        rule.mainClock.advanceTimeBy(1_500)
        save("$name-next.png")
    }

    /** Die richtige Karte ins Buch gezogen: unterwegs, losgelassen über dem Rahmen, eingerastet. */
    @Test
    fun bookDragIntoFrame() {
        val real = ContentRepository.fromContext(InstrumentationRegistry.getInstrumentation().targetContext).load()
        val spec = real.tasks.getValue("l01-sp1") as app.abcvorschule.content.SentencePictureSpec
        rule.mainClock.autoAdvance = false
        rule.setContent {
            InWorld(TrainerWorld.ReadingNook) {
                Box(Modifier.padding(bottom = AbcDimens.screenBottomExtra)) {
                    SentencePictureTrainer(
                        round = spec.rounds[0], roundIndex = 0, pack = real,
                        ttsAvailable = true, speaking = false,
                        onSpeakPrompt = {}, onResult = { _, _, _ -> },
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        val frame = rule.onNodeWithTag("sentence_picture_frame").fetchSemanticsNode().boundsInRoot.center
        val card = rule.onNodeWithTag("sentence_picture_card_correct").fetchSemanticsNode().boundsInRoot.center
        val delta = frame - card
        rule.onNodeWithTag("sentence_picture_card_correct").performTouchInput {
            down(center)
            repeat(6) { moveBy(delta / 12f) }
        }
        rule.mainClock.advanceTimeBy(300)
        save("nook-drag-half.png")
        rule.onNodeWithTag("sentence_picture_card_correct").performTouchInput {
            // Etwas schräg über dem Rahmen loslassen, wie ein Kind es tut.
            repeat(6) { moveBy(delta / 12f + androidx.compose.ui.geometry.Offset(4f, -3f)) }
        }
        rule.mainClock.advanceTimeBy(200)
        save("nook-drag-over.png")
        rule.onNodeWithTag("sentence_picture_card_correct").performTouchInput { up() }
        rule.mainClock.advanceTimeBy(150)
        save("nook-drag-settle.png")
        rule.mainClock.advanceTimeBy(1_200)
        save("nook-drag-landed.png")
    }

    /** Der End-Screen der ersten Lektion: Sternbild „M" über dem Finale-Satz. */
    @Test
    fun endScreenWithConstellation() {
        val real = app.abcvorschule.content.ContentRepository
            .fromContext(InstrumentationRegistry.getInstrumentation().targetContext).load()
        val lesson = real.lessons.first { it.id == "l01" }
        rule.setContent {
            AbcTheme {
                Box(Modifier.fillMaxSize().testTag("shot")) {
                    app.abcvorschule.ui.shell.RewardSummaryScreen(
                        finale = real.finales.getValue(lesson.finaleId!!),
                        lesson = lesson,
                        pack = real,
                        ttsAvailable = true,
                        speaking = false,
                        onSpeak = {},
                        onContinue = {},
                    )
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_600)
        save("end-flying.png")
        rule.mainClock.advanceTimeBy(5_000)
        save("end-done.png")
    }

    private fun towers(left: Int, right: Int, op: MathOperation, choices: List<Int>, tap: Boolean, solve: Boolean, name: String) {
        var solved by androidx.compose.runtime.mutableStateOf<Int?>(null)
        rule.setContent {
            InWorld(TrainerWorld.ForestNight) {
                VisualQuantityBoard(
                    left = left,
                    right = right,
                    operation = op,
                    choices = choices,
                    onChoose = {},
                    solved = solved,
                    ttsAvailable = true,
                )
            }
        }
        rule.mainClock.advanceTimeBy(3_000)
        save("$name-start.png")
        if (tap) {
            rule.onNodeWithTag("number_towers").performClick()
            rule.mainClock.advanceTimeBy(4_000)
            save("$name-jumped.png")
        }
        if (solve) {
            rule.runOnUiThread { solved = op.answer(left, right) }
            rule.mainClock.advanceTimeBy(4_000)
            save("$name-solved.png")
        }
    }

    @Test
    fun towersPlus() = towers(4, 3, MathOperation.Add, listOf(6, 7, 8), tap = true, solve = true, name = "towers-plus")

    @Test
    fun towersMinus() = towers(9, 6, MathOperation.Subtract, listOf(3, 2, 4), tap = true, solve = true, name = "towers-minus")

    @Test
    fun towersTimes() = towers(3, 4, MathOperation.Multiply, listOf(12, 7, 16), tap = false, solve = true, name = "towers-times")

    @Test
    fun towersBig() = towers(13, 4, MathOperation.Add, listOf(16, 17, 18), tap = false, solve = true, name = "towers-big")

    /**
     * Die runden Buchstaben aus dem ausgelieferten Pack, so wie das Kind sie sieht —
     * zur Sichtprüfung von `tools/glyph_curves.py` (Bögen ohne sichtbare Ecken).
     */
    @Test
    fun roundGlyphsInTheJungle() {
        val pack = ContentRepository.fromContext(
            InstrumentationRegistry.getInstrumentation().targetContext,
        ).load()
        var atomId by mutableStateOf("letter-o")
        rule.setContent {
            InWorld(TrainerWorld.Jungle) {
                val atom = pack.atom(atomId)
                LetterTraceTrainer(
                    round = LetterTraceRound(
                        promptTts = "", atomId = atom.id, glyph = atom.display,
                        rewardTts = "", rewardEmoji = "⭐",
                    ),
                    roundIndex = 0, atom = atom,
                    ttsAvailable = true, speaking = false,
                    onSpeakPrompt = {}, onSpeak = {}, onResult = { _, _, _ -> },
                )
            }
        }
        listOf("letter-o", "letter-oe", "letter-s", "letter-g", "letter-sz", "letter-sch", "letter-qu", "letter-c")
            .forEach { id ->
                atomId = id
                rule.mainClock.advanceTimeBy(1_500)
                save("trace-$id.png")
            }
    }
}
