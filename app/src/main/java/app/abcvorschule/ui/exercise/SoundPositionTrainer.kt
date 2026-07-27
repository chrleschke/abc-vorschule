package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.SoftCoral
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky

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
 * drops the picture into the wagon matching the sound's position. Only the
 * picture is shown; the word itself is never written.
 */
@Composable
fun SoundPositionTrainer(
    round: SoundPositionRound,
    roundIndex: Int,
    atom: Atom,
    targetPhoneme: String,
    ttsAvailable: Boolean,
    speaking: Boolean,
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
        prompt = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
            Text(
                text = targetPhoneme,
                fontSize = 42.sp,
                color = SoftSand,
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
                    onTap = {
                        field.select(cardKey)
                        onSpeak(atom.lemma)
                    },
                    onDropped = { zoneKey ->
                        SoundPositionLogic.slotFromKey(zoneKey ?: "")?.let(::place)
                    },
                    modifier = Modifier
                        .size(AbcDimens.kidTouch + 20.dp)
                        .background(
                            color = if (field.selectedKey == cardKey) SoftMint.copy(alpha = 0.3f) else NightElevated,
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

private fun wagonColor(index: Int): Color = when (index % 3) {
    0 -> SoftCoral
    1 -> SoftSand
    else -> SoftSky
}

private fun colouredWord(word: String): AnnotatedString = buildAnnotatedString {
    SoundWordSegments.split(word).forEachIndexed { index, segment ->
        pushStyle(SpanStyle(color = wagonColor(index)))
        append(segment)
        pop()
    }
}

@Composable
private fun Wagon(
    slot: SoundSlot,
    filledEmoji: String?,
    revealed: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: DragFieldState,
) {
    val accent = wagonColor(SoundPositionLogic.SlotOrder.indexOf(slot))
    val desc = stringResource(
        when (slot) {
            SoundSlot.start -> R.string.wagon_start
            SoundSlot.middle -> R.string.wagon_middle
            SoundSlot.end -> R.string.wagon_end
        },
    )
    val border = when {
        filledEmoji != null || revealed -> SoftMint
        armed -> accent
        else -> accent.copy(alpha = 0.55f)
    }
    DropZone(
        state = registerWith,
        key = SoundPositionLogic.slotKey(slot),
        onTap = onTap,
        modifier = Modifier
            .size(WagonSize)
            .background(
                color = if (filledEmoji != null) SoftMint.copy(alpha = 0.18f) else NightElevated,
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
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(SoundPositionLogic.SlotOrder.indexOf(slot) + 1) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(accent.copy(alpha = 0.7f), RoundedCornerShape(5.dp)),
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
            color = SoftCoral,
            topLeft = Offset(w * 0.05f, h * 0.35f),
            size = Size(w * 0.9f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
        )
        // Cab
        drawRoundRect(
            color = SoftCoral.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.45f, h * 0.15f),
            size = Size(w * 0.45f, h * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
        )
        // Chimney
        drawRoundRect(
            color = SoftCoral,
            topLeft = Offset(w * 0.14f, h * 0.18f),
            size = Size(w * 0.16f, h * 0.2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f),
        )
        // Wheels
        drawCircle(color = SoftSand, radius = w * 0.14f, center = Offset(w * 0.28f, h * 0.88f))
        drawCircle(color = SoftSand, radius = w * 0.14f, center = Offset(w * 0.7f, h * 0.88f))
        // Steam puffs grow when the child got it right.
        if (steam > 0f) {
            listOf(0.34f, 0.22f, 0.1f).forEachIndexed { i, y ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f * steam),
                    radius = w * (0.09f + i * 0.03f) * steam,
                    center = Offset(w * (0.22f - i * 0.03f), h * y),
                )
            }
        }
    }
}
