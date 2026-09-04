package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.FeederSide
import app.abcvorschule.content.SoundFeederRound
import app.abcvorschule.content.SoundFeederSpeech
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.content.SymbolInWordMode
import app.abcvorschule.speech.SpokenPart
import app.abcvorschule.ui.exercise.drag.DragCard
import app.abcvorschule.ui.exercise.drag.DropZone
import app.abcvorschule.ui.exercise.drag.rememberDragFieldState
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/** Feste Farben je Seite — in jeder Lektion dieselben zwei Figuren (design doc §5).
 * Weder LeafGreen (richtig) noch StarGold (Belohnung): keine Seite darf „richtig" aussehen. */
private val LeftCreatureColor = SkyBlue
private val RightCreatureColor = SunCoral

private const val ZoneLeft = "feeder_left"
private const val ZoneRight = "feeder_right"
private const val CardKey = "feeder_card"

private enum class FeederPhase { Intro, Playing, Busy, Done }

/**
 * Laut-Fresser (design doc §5/§6): eine Bildkarte oben, zwei Fresser unten. Alle
 * Entscheidungen fallen in [SoundFeederProgress]; dieser Screen zeichnet, bewegt
 * und spricht — und zwar selbst, samt Ansage, damit Wackeln und Laut zusammenfallen.
 */
@Composable
fun SoundFeederTrainer(
    round: SoundFeederRound,
    roundIndex: Int,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakParts: suspend (List<SpokenPart>) -> Unit,
    onSpeakPartsSequenced: suspend (List<SpokenPart>, onPartComplete: (Int) -> Unit) -> Unit,
    onSpeakFeedback: (String) -> Unit,
    onSpeakFeedbackVoiced: (SpokenPart) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.leftAtomId}-${round.rightAtomId}"
    var state by remember(roundKey) { mutableStateOf(SoundFeederProgress.initialState(round)) }
    var phase by remember(roundKey) { mutableStateOf(FeederPhase.Intro) }
    val scope = rememberCoroutineScope()
    val haptics = LocalAbcHaptics.current
    val scoredIds = remember(roundKey) { listOf(round.leftAtomId, round.rightAtomId) }
    val leftAnimator = rememberFeederCreatureAnimator(roundKey + "L")
    val rightAnimator = rememberFeederCreatureAnimator(roundKey + "R")
    // Nur auf die Runde gekeyt (wie im Satz-Ordner und im Wort-Bauer): eine je Karte
    // frische Instanz verlöre die Zonen-Bounds der beiden Fresser, denn die
    // registrieren sich nur, wenn der Antwortblock neu platziert wird — und das tut
    // er beim Kartenwechsel nicht. Der Zustand trägt nichts über die Karte hinaus:
    // endDrag/cancelDrag setzen Drag-Offset und -Key ohnehin zurück.
    val dragState = rememberDragFieldState(roundKey)
    // Der Stapel liegt bei jedem neuen Spiel anders da (Nutzerwunsch): ein Seed je
    // Runde, gewürfelt statt aus der Lektion gesät. Innerhalb des Spiels bleibt er
    // stehen, sonst zappelte der Haufen bei jeder Neukomposition. Welche Karten
    // kommen und in welcher Reihenfolge, bleibt deterministisch (design doc §4) —
    // zufällig ist nur, wie der Haufen daneben aussieht.
    val pileSeed = remember(roundKey) { kotlin.random.Random.nextInt() }
    // Nur Vokalpaare zeigen beide Formen ("Ei / ei"). Konsonantenpaare stehen immer
    // am Anlaut eines Substantivs — dort gibt es die Kleinform gar nicht zu sehen, und
    // "S / s" auf dem Bauch wäre fachliches Beiwerk statt Aufgabe.
    fun creatureLabel(atomId: String) = if (round.anywhere) {
        SymbolInWordDerivation.targetLabel(pack.atom(atomId), SymbolInWordMode.letter)
    } else {
        SymbolInWordDerivation.TargetLabel(pack.atom(atomId).display, null)
    }
    val leftLabel = remember(roundKey) { creatureLabel(round.leftAtomId) }
    val rightLabel = remember(roundKey) { creatureLabel(round.rightAtomId) }
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val cardSize = SoundFeederSizing.cardSizeDp(fontScale)
    // Ohne deutsche Stimme steht das Wort unter dem Emoji — und braucht eigene Höhe.
    // cardSizeDp budgetiert nur das Bild; sonst schöbe der Rahmen das Wort heraus,
    // ausgerechnet das, was ein Erwachsener dann vorlesen muss (PRODUCT_PRINCIPLES §7).
    val showWord = !ttsAvailable
    val cardHeight = SoundFeederSizing.cardHeightDp(fontScale, showWord)
    val cardSizePx = with(density) { cardSize.dp.toPx() }
    // Pro Karte ein frisches Animatable bei 0: die nächste Karte ist unsichtbar, bis
    // presentCard() sie aufploppt — die gefressene verschwindet damit im selben
    // Frame, in dem der Zustand weiterrückt, ohne eigene Ausblend-Animation.
    val cardPop = remember(roundKey, state.nextIndex) { Animatable(0f) }
    // presentCard() läuft in einer Coroutine, die *vor* dem Kartenwechsel gestartet
    // wurde und daher das alte Animatable eingefangen hätte — sie muss das ploppen,
    // das die neue Komposition auch zeichnet, sonst bliebe ab Karte zwei alles bei
    // Skalierung 0 stehen.
    val currentCardPop = rememberUpdatedState(cardPop)
    val cardBounce = remember(roundKey) { Animatable(0f) }
    val enabled = phase == FeederPhase.Playing && !interactionLocked
    // handleDrop wird aus einer Gesten-Closure gerufen, die beim Anfassen der Karte
    // entstand — ein direkt eingefangenes `enabled` wäre dort immer der Wert von
    // damals. Über den State liest der Drop den Stand von jetzt.
    val currentEnabled = rememberUpdatedState(enabled)
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(200),
        label = "feeder_lock_opacity",
    )

    fun animatorFor(side: FeederSide) = if (side == FeederSide.left) leftAnimator else rightAnimator

    // Die ganze Vorstellung: Ansage, dann wackelt links und spricht, dann rechts.
    // Der Trainer spricht selbst (design doc §5), weil nur er weiß, welche Figur
    // gerade dran ist. Auch der Speaker-Tipp spielt genau diese Sequenz.
    //
    // Das Wackeln hängt an den Sprechteilen, nicht an einer Uhr: Teil 0 ist der
    // Ansagesatz, sein Ende ist genau der Moment, in dem der linke Laut anfängt;
    // Teil 1 ist der linke Laut, sein Ende der Beginn des rechten. Vorher rieten
    // zwei `delay()`-Werte diese Zeitpunkte — je nach Satzlänge und Stimme wackelte
    // die Figur mitten im Intro-Satz statt zu ihrem eigenen Geräusch.
    suspend fun introduce() {
        val parts = SoundFeederSpeech.introParts(round, pack)
        if (ttsAvailable) {
            onSpeakPartsSequenced(parts) { index ->
                when (index) {
                    0 -> scope.launch { leftAnimator.wiggle() }
                    1 -> scope.launch { rightAnimator.wiggle() }
                }
            }
        } else {
            leftAnimator.wiggle()
            rightAnimator.wiggle()
        }
    }

    suspend fun presentCard() {
        val pop = currentCardPop.value
        pop.snapTo(0f)
        pop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
        val card = state.current ?: return
        if (ttsAvailable) onSpeakParts(listOf(SoundFeederSpeech.wordPart(card, pack)))
    }

    LaunchedEffect(roundKey) {
        phase = FeederPhase.Intro
        introduce()
        presentCard()
        phase = FeederPhase.Playing
    }

    // Der Hinweis nach dem zweiten Fehlgriff: der richtige Fresser summt seinen Laut.
    LaunchedEffect(roundKey, state.hintActive, state.nextIndex) {
        val card = state.current ?: return@LaunchedEffect
        if (state.hintActive && ttsAvailable) onSpeakFeedbackVoiced(SoundFeederSpeech.soundPart(round, card.side, pack))
    }

    fun handleDrop(zoneKey: String?) {
        if (!currentEnabled.value) return
        val side = when (zoneKey) {
            ZoneLeft -> FeederSide.left
            ZoneRight -> FeederSide.right
            else -> null
        }
        val card = state.current ?: return
        val result = SoundFeederProgress.drop(state, side)
        if (result.outcome == SoundFeederDropOutcome.Ignored) return
        phase = FeederPhase.Busy
        state = result.state
        scope.launch {
            when (result.outcome) {
                SoundFeederDropOutcome.Eaten, SoundFeederDropOutcome.RoundComplete -> {
                    haptics.tick()
                    // Die Karte ist mit dem Zustandswechsel schon weg (siehe cardPop);
                    // der Fresser kaut und spricht Laut + Wort.
                    val chewing = launch { animatorFor(card.side).chew() }
                    if (ttsAvailable) onSpeakParts(SoundFeederSpeech.eatParts(round, card, pack))
                    chewing.join()
                    if (result.outcome == SoundFeederDropOutcome.RoundComplete) {
                        phase = FeederPhase.Done
                        haptics.celebrate()
                        // Beide werden gleichzeitig satt (design doc §6) — nacheinander
                        // sähe aus, als hätte einer mehr gefressen.
                        val filling = listOf(
                            launch { leftAnimator.fill() },
                            launch { rightAnimator.fill() },
                        )
                        if (ttsAvailable) onSpeakParts(SoundFeederSpeech.finishParts(round, pack))
                        filling.joinAll()
                        delay(HuntCelebration.HoldMs)
                        onResult(true, false, scoredIds)
                    } else {
                        presentCard()
                        phase = FeederPhase.Playing
                    }
                }
                SoundFeederDropOutcome.Miss, SoundFeederDropOutcome.MissAlreadyReported -> {
                    haptics.nudge()
                    if (result.outcome == SoundFeederDropOutcome.Miss) onResult(false, false, scoredIds)
                    val wrong = side ?: FeederSide.left
                    val spitting = launch { animatorFor(wrong).spit() }
                    launch {
                        cardBounce.snapTo(1f)
                        cardBounce.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                    }
                    if (ttsAvailable) onSpeakParts(SoundFeederSpeech.missParts(round, card, wrong, pack))
                    spitting.join()
                    phase = FeederPhase.Playing
                }
                SoundFeederDropOutcome.Ignored -> phase = FeederPhase.Playing
            }
        }
    }

    // Maul folgt dem Finger: die Karte liegt über einer Zone → dieser Fresser öffnet.
    val hoverSide = dragState.draggingKey?.let { key ->
        // DragFieldState kennt die Zonen nur intern; die Nähe wird über den Drag-Offset
        // gegen die Kartenposition geschätzt: links der Mitte → links, sonst rechts,
        // sobald die Karte nach unten in den Antwortblock gezogen wurde.
        if (key != CardKey) null else dragState.dragOffset.takeIf { it.y > cardSizePx }?.let { o ->
            if (o.x < 0f) FeederSide.left else FeederSide.right
        }
    }
    // Nur während des Spielens: Kauen, Spucken und Sattwerden animieren dasselbe Maul,
    // und das Zurücksetzen nach dem Loslassen liefe genau in ihre Sequenz hinein
    // (Animatable bricht die laufende Animation ab). Beim Rückweg nach Playing steht
    // hoverSide auf null, das Maul geht also zuverlässig wieder in Ruhe.
    LaunchedEffect(hoverSide, phase) {
        if (phase != FeederPhase.Playing) return@LaunchedEffect
        leftAnimator.hover(hoverSide == FeederSide.left)
        rightAnimator.hover(hoverSide == FeederSide.right)
    }

    ExerciseStage(
        modifier = modifier.testTag("feeder_stage"),
        // Die Karte wird nach unten in den Antwortblock gezogen — ohne diesen Schalter
        // zeichnete er sie hinter die Fresser (siehe KDoc an ExerciseStage).
        promptAboveAnswers = true,
        promptChrome = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = {
                    // Phase noch im Klick setzen, nicht erst in der Coroutine: zwei
                    // schnelle Tipps sähen sonst beide „Playing" und ließen zwei
                    // Vorstellungen übereinander laufen.
                    if (phase == FeederPhase.Playing) {
                        phase = FeederPhase.Intro
                        scope.launch { introduce(); phase = FeederPhase.Playing }
                    }
                },
            )
        },
        prompt = {
            // Hält seine größte Höhe: nach der letzten Karte bleibt die Fläche stehen,
            // die Fresser rücken nicht nach oben (§9).
            Box(
                modifier = Modifier.fillMaxWidth().height((cardHeight + 24f).dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val card = state.current
                    if (card != null && phase != FeederPhase.Done) {
                        DragCard(
                            state = dragState,
                            key = CardKey,
                            enabled = enabled,
                            onTap = { onSpeakFeedback(pack.atom(card.atomId).lemma) },
                            onDropped = ::handleDrop,
                            modifier = Modifier
                                .graphicsLayer {
                                    val pop = cardPop.value
                                    scaleX = pop
                                    scaleY = pop
                                    translationX = cardBounce.value * 14f
                                }
                                .alpha(interactionOpacity),
                        ) {
                            FeederCard(
                                emoji = pack.atom(card.atomId).emoji,
                                wordText = if (showWord) pack.atom(card.atomId).display else null,
                                minWidthDp = cardSize,
                                minHeightDp = cardHeight,
                                fontScale = fontScale,
                            )
                        }
                    } else {
                        Spacer(Modifier.size(cardSize.dp, cardHeight.dp))
                    }
                    Spacer(Modifier.width(18.dp))
                    FoodPile(remaining = (state.remaining - 1).coerceAtLeast(0), seed = pileSeed)
                }
            }
        },
        answers = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val creatureWidth = SoundFeederSizing.creatureWidthDp(maxWidth.value)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SoundFeederSizing.CreatureGapDp.dp, Alignment.CenterHorizontally),
                ) {
                    listOf(
                        Triple(FeederSide.left, ZoneLeft, leftLabel),
                        Triple(FeederSide.right, ZoneRight, rightLabel),
                    ).forEach { (side, zone, label) ->
                        // Kein Tipp-zum-Setzen (bewusste Ausnahme von DragField R15):
                        // ein Tipp auf den Fresser spricht seinen Laut, ein Tipp auf die
                        // Karte ihr Wort — beides Hilfen, keine Antwort. Gefüttert wird
                        // nur durch Ziehen; ein Tipp dürfte also nichts setzen.
                        DropZone(state = dragState, key = zone, enabled = enabled, onTap = {}) {
                            FeederCreature(
                                label = label,
                                color = if (side == FeederSide.left) LeftCreatureColor else RightCreatureColor,
                                animator = animatorFor(side),
                                hint = state.hintActive && state.current?.side == side,
                                widthDp = creatureWidth,
                                // Dieselbe Sperre wie für die Karte: ein Tipp während
                                // Kauen/Spucken (Busy) oder während die Bühne gesperrt
                                // ist, würde wiggle() auf dasselbe Animatable legen und
                                // die laufende Fress-Animation abbrechen.
                                enabled = enabled,
                                onTap = {
                                    scope.launch { animatorFor(side).wiggle() }
                                    onSpeakFeedbackVoiced(SoundFeederSpeech.soundPart(round, side, pack))
                                },
                                testTag = zone,
                            )
                        }
                    }
                }
            }
        },
    )
}

/**
 * Die Bildkarte: Emoji im Rahmen der Satz-Versteher-Karten; ohne TTS steht das Wort
 * darunter. [minWidthDp] und [minHeightDp] sind *Mindest*maße, keine festen: die
 * Wortzeile darf die Karte wachsen lassen, statt aus ihr herausgedrängt zu werden.
 * Höhe um genau eine Zeile (die budgetiert [SoundFeederSizing.cardHeightDp]), Breite
 * nach Bedarf, damit auch „Taschenlampe" bei font_scale 1.3 in einer Zeile steht statt
 * umzubrechen — ein Umbruch spränge über die budgetierte Höhe. Ohne Wort ist
 * [minHeightDp] == [minWidthDp] und die Karte bleibt quadratisch.
 */
@Composable
private fun FeederCard(emoji: String, wordText: String?, minWidthDp: Float, minHeightDp: Float, fontScale: Float) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier = Modifier
            .widthIn(min = minWidthDp.dp)
            .heightIn(min = minHeightDp.dp)
            // Weiß mit Schatten, nicht durchsichtig: die offene Karte muss sich vom
            // Bühnen-Cream abheben und über dem Futterhaufen liegen — vorher hatte sie
            // dieselbe Farbe wie der Hintergrund und sah wie ein leerer Rahmen aus.
            .shadow(elevation = 4.dp, shape = shape)
            .background(Color.White, shape)
            .border(3.dp, WarmMuted.copy(alpha = 0.9f), shape)
            .testTag("feeder_card"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = emoji, fontSize = TaskPromptSizing.pictureSp(fontScale).sp, textAlign = TextAlign.Center)
        if (wordText != null) {
            Text(
                text = wordText,
                style = MaterialTheme.typography.titleLarge,
                color = WarmInk,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
    }
}

/**
 * Der Futterhaufen: die ausstehenden Karten liegen als Rückseiten übereinander auf
 * demselben Fleck, jede ein wenig verrutscht und verdreht ([SoundFeederSizing.pileOffset]).
 * Vorher war es eine Treppe aus leeren Rahmen — durchsichtig und langweilig; jetzt
 * ist es ein Stapel, dem man ansieht, dass noch etwas darin steckt.
 *
 * [seed] kommt aus dem Trainer und wechselt mit jedem Spiel: derselbe Haufen liegt
 * nie zweimal gleich da.
 */
@Composable
private fun FoodPile(remaining: Int, seed: Int) {
    val back = lerp(Cream, WarmMuted, 0.55f)
    val shape = RoundedCornerShape(8.dp)
    // Der Platz wird **immer** für eine Karte reserviert, auch wenn der Haufen leer ist
    // (`pileWidthDp(0) == 0f`): sonst fiele die Box beim letzten Zug von 56dp auf nichts
    // zusammen und schöbe die zentrierte Bildkarte daneben um ~27dp zur Seite. Ist
    // `remaining == 0`, zeichnet `repeat` einfach nichts in die reservierte Fläche.
    Box(
        modifier = Modifier
            .width(SoundFeederSizing.pileWidthDp(1).dp)
            .height((SoundFeederSizing.PileCardHeightDp + 2 * SoundFeederSizing.PileJitterDp).dp)
            .testTag("feeder_pile"),
        contentAlignment = Alignment.Center,
    ) {
        repeat(remaining) { index ->
            val (dx, dy, rot) = SoundFeederSizing.pileOffset(index, seed)
            Box(
                modifier = Modifier
                    .offset(x = dx.dp, y = dy.dp)
                    .rotate(rot)
                    .size(SoundFeederSizing.PileCardWidthDp.dp, SoundFeederSizing.PileCardHeightDp.dp)
                    .background(back, shape)
                    .border(2.dp, WarmMuted, shape),
            )
        }
    }
}
