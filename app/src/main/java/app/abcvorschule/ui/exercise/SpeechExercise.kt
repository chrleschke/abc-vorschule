package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.abcvorschule.content.Atom
import app.abcvorschule.content.composePartsFor
import app.abcvorschule.content.isComposeTask
import app.abcvorschule.content.isSpellTask
import app.abcvorschule.session.ScheduledTask

@Composable
fun SpeechExercise(
    task: ScheduledTask,
    atoms: Map<String, Atom>,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = task.template
    val targetId = template.targetAtomId ?: template.atomId ?: return
    val target = atoms[targetId]
    val parts = template.composePartsFor(target)
    val atomIds = parts.map { it.atomId }
    val gaps = ScaffoldMapping.gaps(
        parts = parts,
        displays = atomIds.associateWith { atoms[it]?.display ?: it },
        emojis = atomIds.associateWith { "" },
        scaffolds = task.scaffolds,
    )
    var misses by remember(template.id) { mutableIntStateOf(0) }
    val wordTitle = target?.display
    val spell = template.isSpellTask()

    DragSlotBoard(
        gaps = gaps,
        distractors = task.distractors.map {
            TrayTile(key = "dx-${it.atomId}-${it.display}", display = it.display, atomId = it.atomId, isDistractor = true)
        },
        missCount = misses,
        showSyllableDots = !spell && (template.isComposeTask() || parts.size > 1),
        arrangeSlotsInRow = parts.size > 1,
        largeTypography = true,
        onSpeakText = onSpeak,
        modifier = modifier.fillMaxSize(),
        prompt = {
            TaskPromptChrome(
                title = wordTitle ?: "ABC",
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
                mutedTitle = wordTitle.isNullOrBlank(),
                onTitleSpeak = { label -> onSpeak(label) },
            )
        },
        onCorrect = { onResult(true, false, (atomIds + targetId).distinct()) },
        onMiss = { atomId ->
            misses += 1
            onResult(false, false, listOf(atomId))
        },
        onResolve = { onResult(false, true, (atomIds + targetId).distinct()) },
    )
}
