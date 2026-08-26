package app.abcvorschule.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.Atom
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.WordBlock
import app.abcvorschule.content.WordBuildRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.speech.SpeechClipText
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.exercise.drag.DragCard
import app.abcvorschule.ui.exercise.drag.DragFieldState
import app.abcvorschule.ui.exercise.drag.DropZone
import app.abcvorschule.ui.exercise.drag.rememberDragFieldState
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

object WordBuildTray {
    /** Preschoolers must be able to scan the whole tray at a glance. */
    const val MaxTrayTiles = 5

    fun tiles(round: WordBuildRound, placedDisplays: List<String>, seed: Int): List<WordBlock> {
        val capped = (round.blocks + round.distractors).take(MaxTrayTiles)
        val arranged = TrayOrder.arrange(capped, seed) { it.display }
        val remaining = arranged.toMutableList()
        placedDisplays.forEach { display ->
            val hit = remaining.indexOfFirst { it.display == display }
            if (hit >= 0) remaining.removeAt(hit)
        }
        return if (remaining.none { block -> round.blocks.any { it.display == block.display } }) {
            emptyList()
        } else {
            remaining
        }
    }

    fun frameKey(index: Int): String = "frame-$index"

    fun frameIndex(key: String): Int? = key.removePrefix("frame-").toIntOrNull()
        ?.takeIf { key.startsWith("frame-") }

    /**
     * Tray tiles are keyed by their position, not just atomId+display: a word like
     * "Hallo" offers two blocks with the identical atomId ("letter-l") and display
     * ("l"), and a key collision there makes drag state (bounds, dragOffset,
     * selectedKey) shared between them — dragging one moves both.
     */
    fun tileKey(index: Int, block: WordBlock): String = "block-$index-${block.atomId}-${block.display}"
}

/**
 * Trainer 4 — Wort-Bauer. The picture anchors the meaning, the frames carry the
 * per-atom scaffold (silhouette vs. empty), and only authored blocks are offered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordBuildTrainer(
    round: WordBuildRound,
    roundIndex: Int,
    pack: ContentPack,
    target: Atom,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onSpeakAndAwait: suspend (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.targetAtomId}-${round.blocks.size}"
    val field = rememberDragFieldState(roundKey)
    val placed = remember(roundKey) { mutableStateMapOf<Int, String>() }
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    var completed by remember(roundKey) { mutableStateOf(false) }
    val solution = remember(roundKey) { round.blocks.map { it.display } }
    val scoredIds = remember(roundKey) {
        (round.blocks.map { it.atomId } + round.targetAtomId).distinct()
    }
    val tiles = WordBuildTray.tiles(round, placed.values.toList(), seed = round.targetAtomId.hashCode())
    val haptics = LocalAbcHaptics.current
    // Der letzte Baustein spricht erst zu Ende, dann kommt der Erfolg. Das läuft
    // in einem LaunchedEffect mit roundKey statt in scope.launch: ein Chevron-Tap
    // während der Ansage wechselt die Runde, und eine scope-Coroutine überlebte
    // das — SpeechController.stop() completet ihre Waiter, und das verspätete
    // onResult(true) würde der NEUEN Runde gutgeschrieben.
    var finalBlockSpeech by remember(roundKey) { mutableStateOf<String?>(null) }
    LaunchedEffect(roundKey, finalBlockSpeech) {
        val speech = finalBlockSpeech ?: return@LaunchedEffect
        onSpeakAndAwait(speech)
        completed = true
        onResult(true, false, scoredIds)
    }
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "word_build_lock_opacity",
    )

    fun place(index: Int, block: WordBlock) {
        if (resolved || placed[index] != null) return
        field.select(null)
        if (OrderedPlacement.isCorrectPlacement(index, block.display, solution)) {
            val placedBefore = placed.toMap()
            placed[index] = block.display
            haptics.tick()
            val blockSpeech = SpeechClipText.forAtomId(pack, block.atomId, block.display)
            when (
                WordBuildPlacementSpeech.blockSpeechMode(
                    placedBefore,
                    index,
                    block.display,
                    solution,
                )
            ) {
                WordBuildPlacementSpeech.BlockSpeechMode.Immediate -> onSpeak(blockSpeech)
                WordBuildPlacementSpeech.BlockSpeechMode.AwaitBeforeSuccess ->
                    finalBlockSpeech = blockSpeech
            }
        } else {
            misses += 1
            // Score against the slot being practiced, not the tile the child grabbed —
            // misplacing a distractor must not downgrade the distractor's own scaffold.
            onResult(false, false, listOf(round.blocks[index].atomId))
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
            Text(text = target.emoji, fontSize = 84.sp)
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val fontScale = LocalDensity.current.fontScale
                val longest = solution.maxOfOrNull { it.length } ?: 1
                val shareWidth = WordFrameSizing.frameWidthDp(maxWidth.value, solution.size)
                val gap = WordFrameSizing.gapDp(maxWidth.value, solution.size)
                // Mit fontScale, sonst wächst der gerenderte Glyph aus dem festen
                // Rahmen (live: „Mam" statt „Mama" bei font_scale 1.3); gewinnt
                // trotzdem der MinGlyphSp-Floor, weitet fittedFrameWidthDp den
                // Rahmen, statt dass maxLines = 1 den Text clippt.
                val glyphSp = WordFrameSizing.glyphSp(shareWidth, longest, fontScale)
                val frameWidth =
                    WordFrameSizing.fittedFrameWidthDp(shareWidth, glyphSp, longest, fontScale)
                AnimatedContent(
                    targetState = completed,
                    transitionSpec = { fadeIn(tween(260)) togetherWith fadeOut(tween(140)) },
                    label = "word_complete",
                ) { isComplete ->
                    if (isComplete) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(solution.joinToString(""), fontSize = glyphSp.sp, color = WarmInk, modifier = Modifier.testTag("completed_word"))
                        }
                    } else Row(
                        horizontalArrangement = Arrangement.spacedBy(gap.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        solution.forEachIndexed { index, expected ->
                            val filled = if (resolved) expected else placed[index]
                            val atomId = round.blocks[index].atomId
                            Frame(expected, filled, scaffoldFor(atomId) == ScaffoldLevel.Beginner, field.selectedKey != null && filled == null, {
                                val selected = field.selectedKey
                                tiles.withIndex()
                                    .firstOrNull { (i, block) -> WordBuildTray.tileKey(i, block) == selected }
                                    ?.let { (_, block) -> place(index, block) }
                                if (filled != null) {
                                    onSpeak(
                                        SpeechClipText.forAtomId(
                                            pack,
                                            round.blocks[index].atomId,
                                            filled,
                                        ),
                                    )
                                }
                            }, field, index, frameWidth, glyphSp, !interactionLocked, interactionOpacity)
                        }
                    }
                }
            }
        },
        answers = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.testTag("word_tray"),
            ) {
                if (!resolved && !completed) {
                    tiles.forEachIndexed { index, block ->
                        val key = WordBuildTray.tileKey(index, block)
                        DragCard(
                            state = field,
                            key = key,
                            enabled = !interactionLocked,
                            onTap = {
                                field.select(key)
                                onSpeak(SpeechClipText.forAtomId(pack, block.atomId, block.display))
                            },
                            onDropped = { zoneKey ->
                                WordBuildTray.frameIndex(zoneKey ?: "")?.let { place(it, block) }
                            },
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = AbcDimens.tileMinWidth,
                                    minHeight = AbcDimens.kidTouch,
                                )
                                .alpha(interactionOpacity)
                                .background(
                                    // SkyBlue, nicht LeafGreen: die Auswahl ist ein
                                    // unvalidierter Aktiv-Zustand, kein "richtig" —
                                    // Grün ist für erledigte Slots reserviert (§10:
                                    // eine Bedeutung pro Farbe).
                                    color = if (field.selectedKey == key) SkyBlue else CreamElevated,
                                    shape = RoundedCornerShape(22.dp),
                                )
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                                .testTag("block_${block.display}"),
                        ) {
                            Text(
                                text = block.display,
                                fontSize = AbcDimens.syllableSp,
                                // Cream on SkyBlue ~3.88:1 (large glyph; Herleitung wie
                                // SymbolHuntTrainer's TilePalette, see Color.kt);
                                // WarmInk on CreamElevated ~8.9:1.
                                color = if (field.selectedKey == key) Cream else WarmInk,
                            )
                        }
                    }
                }
            }
            if (misses >= 2 && !resolved) {
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

/**
 * Filled-slot border colour, dedicated and darker than [LeafGreen]: the border sits
 * flush against the frame's own [CreamElevated] fill (no page background in between),
 * and [LeafGreen] at full opacity only reaches 2.87:1 there — under the 3:1
 * UI-component floor. This shade clears it at 3.79:1 against CreamElevated (4.71:1
 * against the page's Cream), same fix pattern as SoundPositionTrainer's
 * `wagonBorderColor` in Task 4.
 */
private val SlotBorderGreen = Color(0xFF3A7A44)

@Composable
private fun Frame(
    expected: String,
    filled: String?,
    showSilhouette: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: DragFieldState,
    index: Int,
    frameWidthDp: Float,
    glyphSp: Float,
    enabled: Boolean = true,
    opacity: Float = 1f,
) {
    DropZone(
        state = registerWith,
        key = WordBuildTray.frameKey(index),
        onTap = onTap,
        enabled = enabled,
        modifier = Modifier
            .width(frameWidthDp.dp)
            .defaultMinSize(minHeight = frameWidthDp.dp)
            .alpha(opacity)
            .background(
                // SkyBlue-Wash wie die armierte Kachel im Tray: "hier kann die
                // gewählte Kachel hin" ist ein Aktiv-Signal, kein "richtig" —
                // LeafGreen bleibt dem gefüllten Slot (§10).
                color = if (armed) SkyBlue.copy(alpha = 0.22f) else CreamElevated,
                shape = RoundedCornerShape(22.dp),
            )
            .border(
                width = 3.dp,
                // Deviates from the literal mapping (LeafGreen.copy(0.7f) / WarmMuted.copy(0.5f)):
                // both fail the 3:1 UI-component floor once composited over CreamElevated
                // (2.06:1 / 1.77:1). WarmMuted at alpha 0.9f clears 3:1 against CreamElevated
                // itself (3.11:1). LeafGreen at full opacity still only reaches 2.87:1 against
                // CreamElevated (it's calibrated against Cream, see Color.kt) — SlotBorderGreen
                // is the dedicated darker fix for that case (3.79:1).
                color = if (filled != null) SlotBorderGreen else WarmMuted.copy(alpha = 0.9f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(
                horizontal = WordFrameSizing.FramePaddingDp.dp,
                vertical = WordFrameSizing.FramePaddingDp.dp,
            )
            .testTag("frame_$index"),
    ) {
        when {
            filled != null -> Text(text = filled, fontSize = glyphSp.sp, color = WarmInk, maxLines = 1)
            showSilhouette -> Text(
                text = expected,
                fontSize = glyphSp.sp,
                color = WarmInk,
                maxLines = 1,
                modifier = Modifier.alpha(0.22f),
            )
            else -> Text(
                text = "_",
                fontSize = glyphSp.sp,
                // Decorative empty-slot marker, not reading content — alpha bumped +0.1
                // (0.45f -> 0.55f) over the dark-theme value, same pattern as the other
                // muted alphas in this task, not held to the 3:1/4.5:1 floors.
                color = WarmMuted.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
}
