package app.abcvorschule.ui.exercise

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.WordBlock
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.exercise.drag.DragCard
import app.abcvorschule.ui.exercise.drag.DropZone
import app.abcvorschule.ui.exercise.drag.rememberDragFieldState
import app.abcvorschule.ui.rewards.AbcSfx
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.Sfx
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SilboEmoji
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted
import app.abcvorschule.ui.world.lightIsland
import app.abcvorschule.ui.world.lightPlate
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

object SentenceOrderTray {
    /** A sentence can need more cards than a word, but the tray stays scannable. */
    const val MaxTrayTiles = 6

    fun cards(
        words: List<String>,
        atomIds: List<String>,
        distractors: List<WordBlock>,
        placedDisplays: List<String>,
        seed: Int,
    ): List<WordBlock> = slottedCards(words, atomIds, distractors, placedDisplays, seed).map { it.value }

    /** Wie [cards], mit festem Platz im vollen Tray — Begründung bei `WordBuildTray.slottedTiles`. */
    fun slottedCards(
        words: List<String>,
        atomIds: List<String>,
        distractors: List<WordBlock>,
        placedDisplays: List<String>,
        seed: Int,
    ): List<IndexedValue<WordBlock>> {
        val solution = words.mapIndexed { index, word ->
            WordBlock(atomId = atomIds.getOrElse(index) { word }, display = word)
        }
        val capped = (solution + distractors).take(MaxTrayTiles)
        val arranged = TrayOrder.arrange(capped, seed) { it.display }
        val remaining = arranged.withIndex().toMutableList()
        placedDisplays.forEach { display ->
            val hit = remaining.indexOfFirst { it.value.display == display }
            if (hit >= 0) remaining.removeAt(hit)
        }
        return if (remaining.none { (_, card) -> words.any { it == card.display } }) {
            emptyList()
        } else {
            remaining
        }
    }

    fun pegKey(index: Int): String = "peg-$index"

    fun pegIndex(key: String): Int? =
        if (key.startsWith("peg-")) key.removePrefix("peg-").toIntOrNull() else null
}

/**
 * Trainer 5 — Satz-Architekt. Word cards are hung on a clothesline in reading
 * order. A one-word round is the same mechanic with a single peg, which is how the
 * curriculum introduces word-to-picture matching in the first lessons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceOrderTrainer(
    round: SentenceOrderRound,
    roundIndex: Int,
    words: List<String>,
    atomIds: List<String>,
    illustrationEmoji: String?,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.sentenceId}"
    val field = rememberDragFieldState(roundKey)
    val placed = remember(roundKey) { mutableStateMapOf<Int, String>() }
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    var completed by remember(roundKey) { mutableStateOf(false) }
    val scoredIds = remember(roundKey) { atomIds.distinct() }
    val cards = SentenceOrderTray.slottedCards(
        words,
        atomIds,
        round.distractors,
        placed.values.toList(),
        seed = round.sentenceId.hashCode(),
    )
    val haptics = LocalAbcHaptics.current
    // Ruhen statt dimmen (PromptRest): kaum gedämpft, die Ansage-Sperre hält die Taps.
    val interactionOpacity = rememberRestOpacity()

    fun place(index: Int, card: WordBlock) {
        if (resolved || placed[index] != null) return
        field.select(null)
        if (OrderedPlacement.isCorrectPlacement(index, card.display, words)) {
            placed[index] = card.display
            haptics.tick()
            onSpeak(card.display)
            if (OrderedPlacement.isSolved(placed.toMap(), words)) {
                completed = true
                onResult(true, false, scoredIds)
            }
        } else {
            misses += 1
            // Wie jeder andere Trainer: ein Fehlgriff ist spürbar, nicht nur hörbar —
            // die Karte fliegt dazu federnd in den Tray zurück (DragCard).
            haptics.nudge()
            AbcSfx.play(Sfx.Boing)
            // Score against the peg being practiced, not the card the child grabbed —
            // misplacing a distractor must not downgrade the distractor's own scaffold.
            onResult(false, false, listOf(atomIds.getOrElse(index) { card.atomId }))
        }
    }

    ExerciseStage(
        modifier = modifier,
        promptChrome = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
        },
        prompt = {
            if (!illustrationEmoji.isNullOrBlank()) {
                Text(
                    text = illustrationEmoji,
                    fontFamily = SilboEmoji,
                    fontSize = TaskPromptSizing.pictureSp(LocalDensity.current.fontScale).sp,
                    // Das Bild steht auf einem hellen Teller über der Leine (Garten-Welt).
                    modifier = Modifier.lightPlate(),
                )
            }
            val density = LocalDensity.current
            val window = LocalWindowInfo.current.containerSize
            val line = remember(roundKey) { ClothesLineState() }
            val sagPx = with(density) { ClothesLineGeometry.SagDp.dp.toPx() }
            val insetPx = with(density) { ClothesLineGeometry.PoleInsetDp.dp.toPx() }
            // `key(roundKey)` bindet die Transition an die Runde. Ohne Schlüssel
            // merkt sich AnimatedContent seinen Zustand in einem ungekeyten
            // `remember`, und der Aufrufort überlebt einen Rundenwechsel: folgen
            // zwei Satz-Architekten aufeinander, stünde die Transition beim Laden
            // noch auf "fertig", während der neue Rundenzustand schon "leer" ist —
            // die Bühne spielte dann den Eintritt der leeren Pegs ab, und das sieht
            // nach einem Fehler aus. Im ausgelieferten Pack liegen heute nie zwei
            // sentence_order-Tasks hintereinander, im Wort-Bauer schon: die
            // ausführliche Herleitung steht dort in WordBuildTrainer.kt.
            key(roundKey) {
                // Die Leine hängt am Rahmen um die Reihe, nicht an der Reihe selbst: sie
                // bleibt stehen, wenn die Pegs dem fertigen Satz weichen.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clothesLine(line, window.width.toFloat(), window.height.toFloat())
                        .padding(top = ClothesLineGeometry.PinRiseDp.dp, bottom = ClothesLineGeometry.SagDp.dp),
                ) {
                AnimatedContent(
                    targetState = completed,
                    // clip = false: Klammern, Schwingen und die Überlänge langer Sätze
                    // ragen über die Grenzen des Inhalts hinaus.
                    transitionSpec = {
                        (fadeIn(tween(AbcMotion.ShortMs)) togetherWith fadeOut(tween(AbcMotion.QuickMs)))
                            .using(SizeTransform(clip = false))
                    },
                    label = "sentence_complete",
                ) { isComplete ->
                    // Die Bühne wird gemessen, nicht geraten: die Peg-Reihe bricht nie um
                    // (Produktentscheidung), also ist die gemessene Breite die einzige
                    // Größe, gegen die Glyph und Peg-Breiten gelöst werden dürfen.
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                        val fontScale = LocalDensity.current.fontScale
                        val widthPx = with(density) { maxWidth.toPx() }
                        if (isComplete) {
                            // Auch der fertige Satz wird gelöst statt in headlineSmall
                            // gesetzt: „Oma hat einen Hut" braucht dort bei font_scale
                            // 1.3 rund 382dp von 296dp.
                            val glyphDp =
                                SentencePegSizing.completedGlyphDp(maxWidth.value, words)
                            Text(
                                text = words.joinToString(" "),
                                style = MaterialTheme.typography.headlineSmall.copy(
                                    fontSize = SentencePegSizing.glyphSp(glyphDp, fontScale).sp,
                                ),
                                color = WarmInk,
                                maxLines = 1,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .graphicsLayer { translationY = line.sagAt(widthPx / 2f, insetPx, sagPx) }
                                    .lightIsland()
                                    .testTag("completed_sentence"),
                            )
                        } else {
                            // Eine Glyphgröße für den ganzen Satz (gemischte Größen lesen
                            // sich nicht als Satz), aber eine eigene Breite je Peg — die
                            // Silhouette des Wortes, und der Grund, warum die Reihe
                            // überhaupt in eine Zeile passt. Herleitung und der alte
                            // Überlauf stehen in SentencePegSizing.
                            // Auf der Leine darf ein langer Satz über die Bühne hinaus
                            // hängen (solveOnLine); requiredWidth zentriert die Reihe dann
                            // über der Bühne, statt die letzten Pegs zu stauchen.
                            val row = SentencePegSizing.solveOnLine(maxWidth.value, words)
                            val glyphSp = SentencePegSizing.glyphSp(row.glyphDp, fontScale)
                            val rowLeftPx = with(density) { (widthPx - row.widthDp.dp.toPx()) / 2f }
                            val pegCentersPx = with(density) {
                                var x = rowLeftPx
                                row.pegWidthsDp.map { w ->
                                    val center = x + w.dp.toPx() / 2f
                                    x += (w + row.gapDp).dp.toPx()
                                    center
                                }
                            }
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(row.gapDp.dp),
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier.requiredWidth(row.widthDp.dp),
                            ) {
                                words.forEachIndexed { index, expected ->
                                    val filled = if (resolved) expected else placed[index]
                                    val atomId = atomIds.getOrElse(index) { expected }
                                    Peg(
                                        index = index,
                                        expected = expected,
                                        filled = filled,
                                        showGhost = scaffoldFor(atomId) == ScaffoldLevel.Beginner,
                                        armed = field.selectedKey != null && filled == null,
                                        enabled = !interactionLocked,
                                        opacity = interactionOpacity,
                                        // Nur die eigene Tat federt. Nach „Auflösen"
                                        // fallen alle Pegs gleichzeitig — fünf Wackler
                                        // im Chor wären eine Feier für etwas, das das
                                        // Kind nicht geschafft hat.
                                        morphOnFill = !resolved,
                                        onTap = {
                                            val selected = field.selectedKey
                                            val card = cards
                                                .firstOrNull { (i, c) -> cardKey(i, c) == selected }
                                                ?.value
                                            if (card != null) place(index, card)
                                            if (filled != null) onSpeak(filled)
                                        },
                                        registerWith = field,
                                        pegWidthDp = row.pegWidthsDp.getOrElse(index) {
                                            SentencePegSizing.MinPegWidthDp
                                        },
                                        glyphSp = glyphSp,
                                        hang = {
                                            line.sagAt(pegCentersPx.getOrElse(index) { 0f }, insetPx, sagPx)
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
        },
        answers = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("sentence_tray"),
            ) {
                if (!resolved && !completed) {
                    cards.forEach { (cardIndex, card) ->
                        val key = cardKey(cardIndex, card)
                        DragCard(
                            state = field,
                            key = key,
                            enabled = !interactionLocked,
                            onTap = {
                                field.select(key)
                                onSpeak(card.display)
                            },
                            onDropped = { zoneKey ->
                                SentenceOrderTray.pegIndex(zoneKey ?: "")?.let { place(it, card) }
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = AbcDimens.kidTouch - 8.dp)
                                .alpha(interactionOpacity)
                                .shadow(6.dp, RoundedCornerShape(18.dp))
                                .background(
                                    // SkyBlue, nicht LeafGreen: die Auswahl ist ein
                                    // unvalidierter Aktiv-Zustand, kein "richtig" —
                                    // Grün ist für gefüllte Pegs reserviert (§10:
                                    // eine Bedeutung pro Farbe).
                                    color = if (field.selectedKey == key) SkyBlue else CreamElevated,
                                    shape = RoundedCornerShape(18.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("card_${card.display}"),
                        ) {
                            Text(
                                text = card.display,
                                style = MaterialTheme.typography.headlineSmall,
                                // Cream on SkyBlue ~3.88:1 (large glyph; Herleitung wie
                                // SymbolHuntTrainer's TilePalette, see Color.kt);
                                // WarmInk on CreamElevated ~8.9:1.
                                color = if (field.selectedKey == key) Cream else WarmInk,
                            )
                        }
                    }
                }
            }
            // `!completed` wie überall sonst: nach dem gelösten Satz bliebe der
            // Knopf sichtbar, und ein Tipp darauf verbuchte die eben richtig
            // gelöste Runde nachträglich als aufgelöst.
            if (misses >= 2 && !resolved && !completed) {
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

// Mit festem Tray-Platz (slottedCards) wie WordBuildTray.tileKey: zwei Karten mit gleichem Wort teilen
// sich sonst selectedKey/draggingKey/Bounds — "dragging one moves both".
private fun cardKey(index: Int, card: WordBlock): String =
    "card-$index-${card.atomId}-${card.display}"

/**
 * Filled-peg border colour, dedicated and darker than [LeafGreen] — same fix as
 * WordBuildTrainer's `SlotBorderGreen` (identical situation: the border sits flush
 * against the peg's own [CreamElevated] fill, where full-opacity [LeafGreen] only
 * reaches 2.87:1). 3.79:1 against CreamElevated, 4.71:1 against the page's Cream.
 */
private val PegBorderGreen = Color(0xFF3A7A44)

/**
 * Ruheradius der Peg-Ecke. Werte und Begründung des Morphs selbst stehen in
 * [SlotFillMorph] — geteilt mit den Rahmen des Wort-Bauers, weil dort dieselbe
 * Tat dieselbe Antwort bekommt.
 */
private val PegCornerRadius = 16.dp
private val PegBorderWidth = 3.dp

/**
 * Ausschlag des Nachschwingens. 8° statt anfangs 4° auf der schnellen `Wobble`-Feder:
 * das las sich wie Zittern, nicht wie eine Karte an der Leine (Nutzer-Feedback). Bei
 * langen Sätzen berühren sich Nachbarn dabei kurz; das ist an der Leine erlaubt.
 */
private const val PegSwingDegrees = 8f

/** Anstoß beim Antippen, in Grad pro Sekunde — auf der `Glide`-Feder rund 7° Ausschlag. */
private const val PegTapKick = 160f

@Composable
private fun Peg(
    index: Int,
    expected: String,
    filled: String?,
    showGhost: Boolean,
    armed: Boolean,
    enabled: Boolean,
    opacity: Float,
    morphOnFill: Boolean,
    onTap: () -> Unit,
    registerWith: app.abcvorschule.ui.exercise.drag.DragFieldState,
    pegWidthDp: Float,
    glyphSp: Float,
    /** Durchhang der Leine an diesem Peg, in px — nur beim Platzieren gelesen, nie in der Komposition. */
    hang: () -> Float,
) {
    // Bewusst nicht `by`: der Wert wird ausschließlich in graphicsLayer und
    // drawBehind gelesen, also in der Zeichenphase.
    val settle = rememberSlotFillSettle(filled = filled != null, morphOnFill = morphOnFill)
    // Frisch aufgehängt schwingt die Karte an ihrer Klammer nach wie ein Pendel:
    // die langsame `Glide`-Feder, nicht das schnelle `Wobble`.
    val swing = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(filled != null) {
        if (filled != null && morphOnFill) {
            swing.snapTo(PegSwingDegrees)
            swing.animateTo(0f, AbcMotion.Glide.spec())
        }
    }

    // SkyBlue-Wash wie die armierte Karte im Tray: "hier kann die gewählte Karte
    // hin" ist ein Aktiv-Signal, kein "richtig" — LeafGreen bleibt dem gefüllten
    // Peg (§10).
    // Auf der dunklen Garten-Welt: ein leerer Platz ist ein gestrichelter, fast
    // durchsichtiger Umriss, erst die aufgehängte Karte ist hell.
    val fill = when {
        armed -> SkyBlue.copy(alpha = 0.35f)
        filled != null -> CreamElevated
        else -> Cream.copy(alpha = 0.12f)
    }
    // Same deviation as WordBuildTrainer's Frame() border (identical pattern, "wie
    // WordBuild-Slots" per the brief): the literal LeafGreen.copy(0.7f) /
    // WarmMuted.copy(0.32f) fail the 3:1 UI-component floor once composited over
    // CreamElevated. WarmMuted at alpha 0.9f clears 3:1 against CreamElevated
    // itself (3.11:1). LeafGreen at full opacity still only reaches 2.87:1 against
    // CreamElevated — PegBorderGreen is the dedicated darker fix for that case
    // (3.79:1).
    val borderColor = if (filled != null) PegBorderGreen else Cream.copy(alpha = 0.6f)
    val dashed = filled == null

    // Der Durchhang ist ein Versatz beim Platzieren, außen um die DropZone: sie meldet
    // ihre Bounds und nimmt Tipps am äußeren Knoten an, vor dem übergebenen Modifier.
    // Als translationY im graphicsLayer hing die Karte bis zu 14dp tiefer,
    // als sie Tipps und Karten annahm — der untere Rand eines mittleren Pegs war taub.
    Box(Modifier.offset { IntOffset(0, hang().roundToInt()) }) {
    DropZone(
        state = registerWith,
        key = SentenceOrderTray.pegKey(index),
        // Jeder Tipp stupst die Karte an der Leine an: sie schaukelt kurz, voll wie leer.
        onTap = {
            scope.launch { swing.animateTo(0f, AbcMotion.Glide.spec(), initialVelocity = PegTapKick) }
            onTap()
        },
        enabled = enabled,
        modifier = Modifier
            .width(pegWidthDp.dp)
            .defaultMinSize(minHeight = 64.dp)
            .graphicsLayer {
                scaleX = SlotFillMorph.scaleX(settle.value)
                scaleY = SlotFillMorph.scaleY(settle.value)
                alpha = opacity
                rotationZ = swing.value
                // An der Klammer aufgehängt: der Drehpunkt sitzt oben in der Mitte.
                transformOrigin = TransformOrigin(0.5f, 0f)
                // Die Klammer ragt über den Peg hinaus — nicht in einer Ebene abschneiden.
                compositingStrategy = CompositingStrategy.ModulateAlpha
            }
            .drawBehind {
                val radius = SlotFillMorph.cornerRadius(
                    settle = settle.value,
                    resting = PegCornerRadius.toPx(),
                    gain = SlotFillMorph.CornerGainDp.dp.toPx(),
                    min = SlotFillMorph.MinCornerRadiusDp.dp.toPx(),
                )
                drawRoundRect(color = fill, cornerRadius = CornerRadius(radius))
                // Der Rand wird um seine halbe Breite eingerückt gezeichnet, damit er
                // wie Modifier.border innen sitzt und nicht halb über die Kante malt.
                val stroke = PegBorderWidth.toPx()
                drawRoundRect(
                    color = borderColor,
                    topLeft = Offset(stroke / 2f, stroke / 2f),
                    size = Size(size.width - stroke, size.height - stroke),
                    cornerRadius = CornerRadius((radius - stroke / 2f).coerceAtLeast(0f)),
                    style = Stroke(
                        width = if (dashed) stroke * 0.7f else stroke,
                        pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(stroke * 2.2f, stroke * 1.6f)) else null,
                    ),
                )
                drawClothesPin()
            }
            .padding(
                horizontal = SentencePegSizing.PegPaddingDp.dp,
                vertical = SentencePegSizing.PegPaddingDp.dp,
            )
            .testTag("peg_$index"),
    ) {
        when {
            filled != null -> Text(
                text = filled,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = glyphSp.sp),
                color = WarmInk,
                maxLines = 1,
            )
            showGhost -> Text(
                text = expected,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = glyphSp.sp),
                color = Cream,
                maxLines = 1,
                modifier = Modifier.alpha(0.4f),
            )
            else -> Text(
                text = "_",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = glyphSp.sp),
                // Decorative empty-peg marker, not reading content — alpha bumped +0.1
                // (0.45f -> 0.55f), same pattern as WordBuildTrainer's Frame().
                color = Cream.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
    }
}
