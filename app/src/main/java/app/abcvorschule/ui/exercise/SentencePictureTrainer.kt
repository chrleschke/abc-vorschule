package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SentencePictureRound
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SilboEmoji
import app.abcvorschule.ui.world.IslandCream
import app.abcvorschule.ui.world.rememberReduceMotion
import app.abcvorschule.ui.world.rememberWorldSeconds
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Trainer 6 — Satz-Versteher. Ein Satz mit schwieriger Grammatik wird
 * vorgelesen; das Kind tippt eine von zwei Bildkarten. Tippen ist die Antwort —
 * die Karten tragen keine Wörter, also gibt es kein Vorlese-Echo. Ein Miss liest den Satz erneut (missCueForCurrent).
 *
 * Über den Karten liegt ein Bilderbuch ([ReadingBook], PRODUCT_PRINCIPLES §9/§10): ein
 * leerer Bildrahmen und ein Wort-Balken je Wort. Die richtige Karte fliegt in den
 * Rahmen, zur nächsten Runde blättert die Seite um. Bis Oktober 2026 standen die Karten
 * auf einer Puppentheater-Bühne — ein Tester wollte sie dort ablegen.
 */
@Composable
fun SentencePictureTrainer(
    round: SentencePictureRound,
    roundIndex: Int,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.promptTts}"
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    var solvedCorrect by remember(roundKey) { mutableStateOf(false) }
    // Welche Karte zuletzt falsch getippt wurde und wie oft überhaupt schon
    // falsch getippt wurde. Der Zähler ist der Auslöser der Schüttel-Animation:
    // ein Bool wäre beim zweiten Fehltipp auf dieselbe Karte schon true und
    // würde keine neue Runde starten.
    var wrongTick by remember(roundKey) { mutableIntStateOf(0) }
    var wrongOnLeft by remember(roundKey) { mutableStateOf(false) }
    val haptics = LocalAbcHaptics.current
    val scoredIds = remember(roundKey) { round.correctAtomIds.distinct() }
    val correctOnLeft = remember(roundKey) {
        SentencePictureSides.correctOnLeft(round.promptTts.hashCode())
    }
    // Ruhen statt dimmen (PromptRest): kaum gedämpft, die Ansage-Sperre hält die Taps.
    val interactionOpacity = rememberRestOpacity()
    val still = rememberReduceMotion()
    val answered = solvedCorrect || resolved
    val page = remember(roundKey) {
        BookPageContent(
            sentence = round.promptTts,
            picture = emojisOf(round.correctAtomIds, pack),
            pictureCount = round.correctAtomIds.size,
        )
    }

    // Umblättern. Die vorige Seite muss den Rundenwechsel überleben — sie ist es, die
    // umblättert —, darum ist der Speicher bewusst *nicht* gekeyt; alles, was zur Runde
    // gehört, ist es (key(roundKey)-Disziplin, siehe unten). Geblättert wird nur einen
    // Schritt vorwärts: Runde 1, eine fortgesetzte Lektion und der Rückwärts-Chevron
    // zeigen die Seite einfach (§10: ein Trainer, der geladen wird, animiert nichts).
    val memory = remember { BookMemory() }
    val turningFrom = remember(roundKey) { memory.pageBefore(roundIndex) }
    SideEffect { memory.keep(roundIndex, page, filled = answered) }
    val turn = remember(roundKey) { Animatable(if (turningFrom != null) 0f else 1f) }
    // Die neuen Karten erscheinen erst, wenn die Seite fast liegt. Beim Öffnen sind sie da.
    val cardsIn = remember(roundKey) { Animatable(if (turningFrom != null) 0f else 1f) }
    val cardsReady by remember(roundKey) { derivedStateOf { cardsIn.value >= 1f } }
    LaunchedEffect(roundKey) {
        if (turningFrom == null) return@LaunchedEffect
        // Langsam los, schnell weg: das Blatt hebt sich gemächlich, und die Hälfte, die man
        // sieht (bis zur Senkrechten), bekommt den Großteil der Zeit. Mit „schnell los"
        // war sie nach 200 ms vorbei, der Rest fiel unsichtbar links aus dem Bild.
        launch { turn.animateTo(1f, tween(PageTurnMs, easing = AbcMotion.Exit)) }
        delay(CardsInDelayMs.toLong())
        cardsIn.animateTo(1f, tween(AbcMotion.ShortMs, easing = AbcMotion.Enter))
    }

    // Der Flug der richtigen Karte in den Rahmen (auch nach „Zeig mir"), danach geht sie
    // im Rahmen auf. Gekeyt wie alles hier: ein ungekeytes Animatable stünde in der neuen
    // Runde auf 1 und die Karte wäre von Anfang an im Rahmen verschwunden.
    val flight = remember(roundKey) { Animatable(0f) }
    val land = remember(roundKey) { Animatable(0f) }
    LaunchedEffect(roundKey, answered) {
        if (!answered) return@LaunchedEffect
        if (still) {
            // Bewegung reduziert: kein Flug, die Karte blendet in den Rahmen über.
            flight.animateTo(1f, tween(AbcMotion.LongMs, easing = AbcMotion.Linger))
        } else {
            flight.animateTo(1f, tween(FlightMs, easing = AbcMotion.Enter))
            land.animateTo(1f, tween(AbcMotion.ShortMs, easing = AbcMotion.Linger))
        }
    }
    val landed: () -> Float = { if (still) flight.value else land.value }
    val targets = remember { FlightTargets() }

    // Vorlesen: ohne Clip-Dauer im Audio-Layer keine Wort-für-Wort-Synchronisation, die
    // ganze Balkengruppe glimmt ruhig, solange gesprochen wird (Ansage, Miss-Wiederholung,
    // Erfolgs-Vorsprechen — alle drei sind der Satz; in Runde 1 auch die Instruktion davor).
    val glowLevel by animateFloatAsState(
        targetValue = if (speaking) 1f else 0f,
        animationSpec = tween(durationMillis = AbcMotion.ShortMs, easing = AbcMotion.Linger),
        label = "sentence_picture_bars",
    )
    val seconds by rememberWorldSeconds(still = still || !speaking)
    val glow: () -> Float = {
        val breath = if (still) 1f else 0.78f + 0.22f * sin(seconds / BarBreathS * 2f * PI.toFloat())
        glowLevel * breath
    }

    fun choose(correct: Boolean, tappedLeft: Boolean) {
        if (resolved || solvedCorrect) return
        if (correct) {
            solvedCorrect = true
            haptics.success()
            onResult(true, false, scoredIds)
        } else {
            misses += 1
            wrongOnLeft = tappedLeft
            wrongTick += 1
            haptics.nudge()
            onResult(false, false, scoredIds)
        }
    }

    ExerciseStage(
        modifier = modifier,
        // Der Aufgabenblock trägt das Buch — am unteren Rand verdeckt die tippende
        // Hand sonst genau die Bildkarten, die die ganze Aufgabe sind.
        answerAnchor = AnswerAnchor.BelowCenter,
        // Der Lesetisch liegt außerhalb des Ruhens: er gehört zur Welt, nicht zur Aufgabe.
        answersBackdrop = { drawReadingTable() },
        promptChrome = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
        },
        prompt = {
            ReadingBook(
                page = page,
                turningFrom = turningFrom?.content,
                turningFromFilled = turningFrom?.filled == true,
                turn = { turn.value },
                still = still,
                ttsAvailable = ttsAvailable,
                landed = landed,
                glow = glow,
                onFrameBounds = { targets.frame = it },
                onPictureSp = { targets.frameSp = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        },
        answers = {
            Row(
                // 8dp statt 14dp: die Lücke ist reines Breitenbudget, das den
                // Emojis fehlt. Zwei Karten mit deutlichem Rahmen brauchen
                // keinen breiten Graben, um auseinandergehalten zu werden.
                horizontalArrangement = Arrangement.spacedBy(CardGapDp.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sentence_picture_cards"),
            ) {
                val enabled = !interactionLocked && !answered && cardsReady
                listOf(true, false).forEach { isLeft ->
                    val isCorrect = isLeft == correctOnLeft
                    val side = if (isLeft) -1f else 1f
                    PictureCard(
                        atomIds = if (isCorrect) round.correctAtomIds else round.wrongAtomIds,
                        pack = pack,
                        highlight = answered && isCorrect,
                        enabled = enabled,
                        opacity = interactionOpacity,
                        shakeTick = if (wrongOnLeft == isLeft) wrongTick else 0,
                        onPlaced = { if (isLeft) targets.left = it else targets.right = it },
                        onEmojiSp = { if (isCorrect) targets.cardSp = it },
                        layer = {
                            val appear = cardsIn.value
                            translationY = (1f - appear) * CardsInRiseDp.dp.toPx()
                            alpha = appear
                            val p = flight.value
                            if (isCorrect) {
                                val from = if (isLeft) targets.left else targets.right
                                if (!still) flyInto(p, from, targets, side)
                                alpha *= 1f - landed()
                            } else {
                                // Die falsche Karte blendet aus, während die richtige abhebt.
                                alpha *= 1f - (p / WrongFadeShare).coerceAtMost(1f)
                            }
                        },
                        onTap = { choose(isCorrect, tappedLeft = isLeft) },
                        testTag = if (isCorrect) "sentence_picture_card_correct" else "sentence_picture_card_wrong",
                        // Die fliegende Karte liegt über ihrer Nachbarin.
                        modifier = Modifier
                            .weight(1f)
                            .zIndex(if (isCorrect) 1f else 0f),
                    )
                }
            }
            if (misses >= 2 && !answered) {
                AbcResolveButton(
                    onClick = {
                        resolved = true
                        onResult(false, true, scoredIds)
                    },
                )
            }
        },
    )
}

private fun emojisOf(atomIds: List<String>, pack: ContentPack): String =
    atomIds.joinToString("") { pack.atoms[it]?.emoji.orEmpty() }

/** Die Seite der vorigen Runde — das, was beim nächsten Schritt umblättert. Kein State. */
private class BookMemory {
    private var roundIndex = -1
    private var content: BookPageContent? = null
    private var filled = false

    class Turning(val content: BookPageContent, val filled: Boolean)

    fun pageBefore(currentRound: Int): Turning? {
        val previous = content ?: return null
        return if (roundIndex == currentRound - 1) Turning(previous, filled) else null
    }

    fun keep(round: Int, page: BookPageContent, filled: Boolean) {
        roundIndex = round
        content = page
        this.filled = filled
    }
}

/**
 * Wo Karten und Rahmen liegen (Root), gemessen beim Platzieren, und wie groß ihre Emojis
 * gesetzt sind. Kein State.
 */
private class FlightTargets {
    var frame: Rect = Rect.Zero
    var left: Rect = Rect.Zero
    var right: Rect = Rect.Zero
    var cardSp: Float = 0f
    var frameSp: Float = 0f
}

/**
 * Die Karte fliegt in einem Bogen in den Rahmen: erst nach außen und hoch, dann zur
 * Mitte, dabei leicht gekippt, und schrumpft auf Rahmengröße. Quadratische Kurve mit dem
 * Kontrollpunkt neben der Karte, auf ihrer Außenseite ([side] −1 links, +1 rechts).
 *
 * Sie landet in der Größe, in der der Rahmen das Bild zeigt: ihre Emojis sind dann so
 * groß wie die des Rahmens, und das Aufgehen im Rahmen ist eine Überblendung zwischen
 * zwei deckungsgleichen Bildern statt eines Größensprungs. Größer als der Rahmen wird
 * sie dabei nie.
 */
private fun GraphicsLayerScope.flyInto(p: Float, from: Rect, targets: FlightTargets, side: Float) {
    val to = targets.frame
    if (from.isEmpty || to.isEmpty || p <= 0f) return
    val dx = to.center.x - from.center.x
    val dy = to.center.y - from.center.y
    val cx = side * FlightBowDp.dp.toPx()
    val cy = dy * 0.6f
    val u = 1f - p
    translationX += 2f * u * p * cx + p * p * dx
    translationY += 2f * u * p * cy + p * p * dy
    val inset = 2 * FlightLandInsetDp.dp.toPx()
    val fit = minOf((to.width - inset) / from.width, (to.height - inset) / from.height)
    val match = if (targets.cardSp > 0f && targets.frameSp > 0f) targets.frameSp / targets.cardSp else fit
    val target = minOf(match, fit)
    val s = 1f + (target - 1f) * p
    scaleX = s
    scaleY = s
    rotationZ = side * FlightTiltDeg * sin(p * PI.toFloat())
}

/** Ein Flug in den Rahmen; die Dauerstufe „Feiern", denn er *ist* die Feier der Runde. */
private const val FlightMs = AbcMotion.CelebrateMs

/** So schnell blendet die falsche Karte aus, als Anteil des Flugs. */
private const val WrongFadeShare = 0.4f

/** Wie weit der Bogen nach außen ausholt. */
private const val FlightBowDp = 48f

/** Größte Neigung unterwegs (Mitte des Flugs). */
private const val FlightTiltDeg = 7f

/** Abstand der gelandeten Karte zum Rahmenrand. */
private const val FlightLandInsetDp = 10f

/** Das Umblättern um den Falz, ≈ 600 ms (Stufe „Feiern"). */
private const val PageTurnMs = AbcMotion.CelebrateMs

/** Die neuen Karten kommen, wenn die alte Seite den Großteil ihres Wegs hinter sich hat. */
private const val CardsInDelayMs = 420

/** Die neuen Karten steigen beim Erscheinen um so viel auf. */
private const val CardsInRiseDp = 14f

/** Periode des ruhigen Glimmens der Balken beim Vorlesen. */
private const val BarBreathS = 1.8f

/**
 * Innenabstand der Karte je Seite; zugleich der Abzug für die Emoji-
 * Breitenrechnung. 4dp statt vormals 10dp: ohne Füllfläche muss der Rahmen keine
 * Fläche mehr einfassen, und jedes eingesparte dp landet direkt im Breitendeckel
 * der Emoji-Reihe — bei drei Emojis ist die Breite die bindende Grenze.
 */
private const val CardPaddingHorizontalDp = 4f

/** Abstand der beiden Karten in der Reihe, ebenfalls Breitenbudget der Emojis. */
private const val CardGapDp = 8f

/** Holzrahmen der Bildkarten. */
private val FrameWood = Color(0xFF8A6440)

@Composable
private fun PictureCard(
    atomIds: List<String>,
    pack: ContentPack,
    highlight: Boolean,
    enabled: Boolean,
    opacity: Float,
    shakeTick: Int,
    onPlaced: (Rect) -> Unit,
    onEmojiSp: (Float) -> Unit,
    layer: GraphicsLayerScope.() -> Unit,
    onTap: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val emojis = emojisOf(atomIds, pack)
    val fontScale = LocalDensity.current.fontScale
    // Ein Animatable statt animateFloatAsState: die Schüttelrunde muss bei jedem
    // neuen Tick von vorn beginnen, auch wenn die vorige noch läuft.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeTick) {
        // Erst zurücksetzen, dann der Frühausstieg — nicht zu „wenn 0, einfach
        // return" vereinfachen: beim Rundenwechsel fällt shakeTick von 1 auf 0, der
        // Effekt startet neu und bricht dabei ein noch laufendes animateTo ab, dessen
        // abschließendes snapTo unten also nie mehr läuft. Die Chevrons wechseln die
        // Runde ohne Verzögerung (SessionViewModel.goNextRound) und sind nach einem
        // Fehltipp aktiv — falsche Karte tippen, innerhalb der 420ms den Chevron: die
        // nächste Runde eröffnete sonst mit einer um bis zu 12dp verschobenen Karte,
        // und die bliebe für alle folgenden Runden schief stehen.
        shake.snapTo(0f)
        if (shakeTick == 0) return@LaunchedEffect
        shake.animateTo(1f, tween(durationMillis = SentencePictureCardShake.DurationMs))
        shake.snapTo(0f)
    }
    // Die Emoji-Größe hängt an der real gemessenen Kartenbreite, nicht an einer
    // festen Staffelung: sonst überläuft die Reihe auf schmalen Geräten (siehe
    // SentencePictureCardSizing). BoxWithConstraints außen, Padding innen, damit
    // maxWidth die volle Kartenbreite ist und der Abzug hier sichtbar bleibt.
    BoxWithConstraints(modifier = modifier) {
        val contentWidthDp = (maxWidth.value - 2 * CardPaddingHorizontalDp).coerceAtLeast(1f)
        val emojiSp = SentencePictureCardSizing.emojiSp(
            atomCount = atomIds.size,
            contentWidthDp = contentWidthDp,
            fontScale = fontScale,
        )
        SideEffect { onEmojiSp(emojiSp) }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = AbcDimens.kidTouch * 2)
                // Vor dem graphicsLayer gemessen: die Lage der Karte im Layout, nicht
                // die gerade geflogene — von dort startet der Flug.
                .onGloballyPositioned { onPlaced(it.boundsInRoot()) }
                // graphicsLayer statt offset: eine reine Zeichenoperation, die
                // kein Neu-Layout der Reihe auslöst und die Nachbarkarte
                // deshalb nicht mitverschiebt. Schütteln, Erscheinen und Flug lesen
                // ihre Werte erst hier, in der Zeichenphase.
                .graphicsLayer {
                    layer()
                    translationX += SentencePictureCardShake.offsetDp(shake.value).dp.toPx()
                    alpha *= opacity
                }
                // Ein gerahmtes Bild: helle Bildfläche (Licht-Insel, §10), darum ein
                // Holzrahmen. Auf dem dunklen Tisch trägt der Rahmen die Kartengrenze,
                // die Fläche hält die Emojis hell.
                .shadow(elevation = 8.dp, shape = RoundedCornerShape(22.dp))
                .background(IslandCream, RoundedCornerShape(22.dp))
                // Der Rahmen ist 5dp stark, damit das Holz als Rahmen liest; LeafGreen
                // markiert weiter die richtige Karte (§10), auf IslandCream mit gut 3:1.
                .border(
                    width = 5.dp,
                    color = if (highlight) LeafGreen else FrameWood,
                    shape = RoundedCornerShape(22.dp),
                )
                .clickable(enabled = enabled, onClick = onTap)
                .padding(horizontal = CardPaddingHorizontalDp.dp, vertical = 18.dp)
                .testTag(testTag),
        ) {
            Text(
                text = emojis,
                fontFamily = SilboEmoji,
                fontSize = emojiSp.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                // Zweiter Riegel gegen den Überlauf: sollte die Breitenschätzung doch
                // einmal danebenliegen, wird die Reihe angeschnitten statt umgebrochen.
                // Mit maxLines = 1 fällt eine umgebrochene zweite Zeile komplett weg —
                // das letzte Emoji wäre unsichtbar, und die beiden Karten sähen bei
                // 16 der 72 Runden identisch aus. Angeschnitten ist harmloser.
                softWrap = false,
            )
        }
    }
}
