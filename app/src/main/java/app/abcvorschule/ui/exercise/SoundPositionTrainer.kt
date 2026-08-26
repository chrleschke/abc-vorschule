package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.R
import app.abcvorschule.content.Atom
import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SoundSlot
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.exercise.drag.DragCard
import app.abcvorschule.ui.exercise.drag.DragFieldState
import app.abcvorschule.ui.exercise.drag.DropZone
import app.abcvorschule.ui.exercise.drag.rememberDragFieldState
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.StarGoldDeep
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

object SoundPositionLogic {
    /** Front to back: locomotive head, middle wagon, last wagon. */
    val SlotOrder: List<SoundSlot> = listOf(SoundSlot.start, SoundSlot.middle, SoundSlot.end)

    fun isCorrect(round: SoundPositionRound, slot: SoundSlot): Boolean = round.slot == slot

    fun slotKey(slot: SoundSlot): String = "wagon-${slot.name}"

    fun slotFromKey(key: String): SoundSlot? =
        SoundSlot.entries.firstOrNull { slotKey(it) == key }
}

private val WagonSize = 96.dp

/**
 * Trainer 1 — Auditiver Finder. The child hears a phoneme and a picture word and
 * drops the picture into the wagon matching the sound's position. Below the
 * picture the word is rendered split into its grapheme groups, tinted in the
 * three wagon colours ([colouredWord]) — a colour hint, not a reading task.
 */
@Composable
fun SoundPositionTrainer(
    round: SoundPositionRound,
    roundIndex: Int,
    atom: Atom,
    targetPhoneme: String,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.atomId}-${round.slot}"
    val field = rememberDragFieldState(roundKey)
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var landedSlot by remember(roundKey) { mutableStateOf<SoundSlot?>(null) }
    var revealed by remember(roundKey) { mutableStateOf(false) }
    val cardKey = "picture-${round.atomId}"

    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "sound_position_lock_opacity",
    )

    fun place(slot: SoundSlot) {
        if (landedSlot != null || revealed) return
        field.select(null)
        if (SoundPositionLogic.isCorrect(round, slot)) {
            landedSlot = slot
            onResult(true, false, listOf(round.atomId))
        } else {
            misses += 1
            onResult(false, false, listOf(round.atomId))
        }
    }

    val steam by animateFloatAsState(
        targetValue = if (landedSlot != null) 1f else 0f,
        label = "steam",
    )

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
            Text(
                text = targetPhoneme,
                fontSize = 42.sp,
                color = WarmInk,
                modifier = Modifier.testTag("sound_target"),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                LocomotiveHead(steam = steam)
                SoundPositionLogic.SlotOrder.forEach { slot ->
                    val armed = field.selectedKey != null && landedSlot == null && !revealed
                    Wagon(
                        slot = slot,
                        filledEmoji = if (landedSlot == slot) atom.emoji else null,
                        revealed = revealed && round.slot == slot,
                        armed = armed,
                        enabled = !interactionLocked,
                        opacity = interactionOpacity,
                        // An exploratory tap must never burn a miss. Only a wagon tap
                        // that follows picking the picture up counts as a placement.
                        onTap = { if (armed) place(slot) },
                        registerWith = field,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(SyllableFrameSizing.widthDp(atom.display).dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = colouredWord(atom.display),
                    fontSize = 34.sp,
                    modifier = Modifier.testTag("sound_word"),
                )
            }
        },
        answers = {
            if (landedSlot == null && !revealed) {
                DragCard(
                    state = field,
                    key = cardKey,
                    enabled = !interactionLocked,
                    onTap = {
                        field.select(cardKey)
                        onSpeak(atom.lemma)
                    },
                    onDropped = { zoneKey ->
                        SoundPositionLogic.slotFromKey(zoneKey ?: "")?.let(::place)
                    },
                    modifier = Modifier
                        .size(AbcDimens.kidTouch + 20.dp)
                        .alpha(interactionOpacity)
                        .background(
                            color = if (field.selectedKey == cardKey) LeafGreen.copy(alpha = 0.25f) else CreamElevated,
                            shape = RoundedCornerShape(24.dp),
                        )
                        .testTag("sound_card"),
                ) {
                    Text(text = atom.emoji, fontSize = 56.sp)
                }
            }
            if (misses >= 2 && landedSlot == null && !revealed) {
                AbcResolveButton(
                    onClick = {
                        revealed = true
                        onResult(false, true, listOf(round.atomId))
                    },
                )
            }
        },
    )
}

// index 1 uses StarGoldDeep rather than StarGold: this value is also drawn as word-segment
// text and as a UI-component border/fill on Cream, where plain StarGold only reaches
// ~1.85:1 (see Color.kt) — well short of the required 3:1 for large glyphs/UI components.
private fun wagonColor(index: Int): Color = when (index % 3) {
    0 -> SunCoral
    1 -> StarGoldDeep
    else -> SkyBlue
}

private fun colouredWord(word: String): AnnotatedString = buildAnnotatedString {
    SoundWordSegments.split(word).forEachIndexed { index, segment ->
        pushStyle(SpanStyle(color = wagonColor(index)))
        append(segment)
        pop()
    }
}

/**
 * Border/indicator-dot variants of the wagon colours. These sit directly on
 * CreamElevated — the wagon's own idle fill — rather than on Cream, where
 * [wagonColor] is read instead as word-segment text. At full strength there, SunCoral
 * only reaches ~2.91:1 and StarGoldDeep ~2.65:1 against CreamElevated — both short of
 * the 3:1 floor for UI components — so both are darkened further here to ~3.35:1
 * (checked via WCAG relative luminance, no alpha involved). SkyBlue already clears
 * 3:1 at full opacity (~3.12:1) and is used unchanged. Because none of the three has
 * headroom to spare below full opacity, this colour is always drawn solid (alpha 1f);
 * see the `armed` handling in [Wagon] for how the idle/armed distinction is carried
 * instead.
 */
private fun wagonBorderColor(index: Int): Color = when (index % 3) {
    0 -> Color(0xFFC15429) // darkened SunCoral
    1 -> Color(0xFF9A6D09) // darkened StarGoldDeep
    else -> SkyBlue
}

@Composable
private fun Wagon(
    slot: SoundSlot,
    filledEmoji: String?,
    revealed: Boolean,
    armed: Boolean,
    enabled: Boolean,
    opacity: Float,
    onTap: () -> Unit,
    registerWith: DragFieldState,
) {
    val index = SoundPositionLogic.SlotOrder.indexOf(slot)
    val borderAccent = wagonBorderColor(index)
    val desc = stringResource(
        when (slot) {
            SoundSlot.start -> R.string.wagon_start
            SoundSlot.middle -> R.string.wagon_middle
            SoundSlot.end -> R.string.wagon_end
        },
    )
    // The border itself no longer dims for the idle state — at reduced alpha none of
    // the three wagon colours clears 3:1 against CreamElevated (~1.67–2.18:1 measured
    // at the old 0.55/0.7 alphas). The idle/armed distinction now lives in the fill
    // below instead.
    val border = if (filledEmoji != null || revealed) LeafGreen else borderAccent
    DropZone(
        state = registerWith,
        key = SoundPositionLogic.slotKey(slot),
        onTap = onTap,
        enabled = enabled,
        modifier = Modifier
            .size(WagonSize)
            // During interactionLocked (opacity 0.5f) this still dips the border under 3:1
            // against CreamElevated again — an accepted, temporary trade-off: dimming ALL
            // interactive elements to 50% while locked is the feature's whole visual signal
            // (design doc), not specific to this wagon.
            .alpha(opacity)
            .background(
                // Alpha raised from the old dark-theme 0.18f: a light wash needs more
                // coverage to read as "filled" against Cream than it did on night ink.
                color = when {
                    filledEmoji != null -> LeafGreen.copy(alpha = 0.25f)
                    armed -> borderAccent.copy(alpha = 0.18f)
                    else -> CreamElevated
                },
                shape = RoundedCornerShape(18.dp),
            )
            .border(4.dp, border, RoundedCornerShape(18.dp))
            .semantics { contentDescription = desc }
            .testTag("wagon_${slot.name}"),
    ) {
        if (filledEmoji != null) {
            Text(text = filledEmoji, fontSize = 44.sp)
        } else {
            // Position cue without text: one, two or three dots along the wagon.
            // Solid fill, not the old 0.7f alpha wash: at that alpha none of the three
            // wagon colours clears 3:1 against CreamElevated either (~1.94–2.17:1).
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(index + 1) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(borderAccent, RoundedCornerShape(5.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocomotiveHead(steam: Float) {
    Canvas(
        Modifier
            .width(72.dp)
            .height(WagonSize),
    ) {
        val w = size.width
        val h = size.height
        // Body
        drawRoundRect(
            color = SunCoral,
            topLeft = Offset(w * 0.05f, h * 0.35f),
            size = Size(w * 0.9f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
        )
        // Cab
        drawRoundRect(
            color = SunCoral.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.45f, h * 0.15f),
            size = Size(w * 0.45f, h * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
        )
        // Chimney
        drawRoundRect(
            color = SunCoral,
            topLeft = Offset(w * 0.14f, h * 0.18f),
            size = Size(w * 0.16f, h * 0.2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f),
        )
        // Wheels
        drawCircle(color = WarmInk, radius = w * 0.14f, center = Offset(w * 0.28f, h * 0.88f))
        drawCircle(color = WarmInk, radius = w * 0.14f, center = Offset(w * 0.7f, h * 0.88f))
        // Steam puffs grow when the child got it right. Warm-grey rather than white so
        // the puffs stay visible against the cream sky instead of vanishing into it.
        if (steam > 0f) {
            listOf(0.34f, 0.22f, 0.1f).forEachIndexed { i, y ->
                drawCircle(
                    color = WarmMuted.copy(alpha = 0.4f * steam),
                    radius = w * (0.09f + i * 0.03f) * steam,
                    center = Offset(w * (0.22f - i * 0.03f), h * y),
                )
            }
        }
    }
}
