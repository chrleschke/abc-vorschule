package app.abcvorschule.ui.exercise

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.abcvorschule.content.Atom
import app.abcvorschule.content.AtomKind
import app.abcvorschule.content.Sentence
import app.abcvorschule.content.composePartsFor
import app.abcvorschule.content.isComposeTask
import app.abcvorschule.content.isLetterTask
import app.abcvorschule.content.isSpellTask
import app.abcvorschule.content.isSyllableTask
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.session.ScheduledTask

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ReadingExercise(
    task: ScheduledTask,
    atoms: Map<String, Atom>,
    sentence: Sentence?,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    onSpeak: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = task.template
    val atom = template.atomId?.let { atoms[it] }
    val parts = template.composePartsFor(atom)
    val atomIds = parts.map { it.atomId }
    // Letter frames map upper/lowercase to screen position, not recall — always show
    // the silhouette so the child knows where each case goes, regardless of mastery.
    val scaffoldsForGaps = if (template.isLetterTask()) {
        task.scaffolds.mapValues { ScaffoldLevel.Beginner }
    } else {
        task.scaffolds
    }
    val gaps = ScaffoldMapping.gaps(
        parts = parts,
        displays = atomIds.associateWith { atoms[it]?.display ?: it },
        emojis = atomIds.associateWith { atoms[it]?.emoji.orEmpty() },
        scaffolds = scaffoldsForGaps,
    )
    var misses by remember(template.id) { mutableIntStateOf(0) }
    val scoredIds = remember(template.id, parts) {
        (atomIds + listOfNotNull(template.atomId)).distinct()
    }
    val literacyFocus = template.isLetterTask() || template.isSyllableTask() ||
        atom?.kind == AtomKind.letter || atom?.kind == AtomKind.syllable
    val spell = template.isSpellTask()
    val wordTitle = atom?.display.takeIf {
        template.isComposeTask() || spell || atom?.kind == AtomKind.word
    }
    val title = when {
        sentence != null -> null
        wordTitle != null -> wordTitle
        literacyFocus -> "ABC"
        else -> atom?.display
    }

    DragSlotBoard(
        gaps = gaps,
        distractors = task.distractors.map {
            TrayTile(key = "dx-${it.atomId}-${it.display}", display = it.display, atomId = it.atomId, isDistractor = true)
        },
        missCount = misses,
        showSyllableDots = template.isComposeTask() && !template.isLetterTask() && !spell,
        arrangeSlotsInRow = literacyFocus || template.isComposeTask() || spell || parts.size > 1,
        largeTypography = literacyFocus || template.isComposeTask() || spell,
        // Sentence gaps render inline in the sentence text below, not as a separate row.
        showDefaultGapRow = sentence == null,
        onSpeakText = onSpeak,
        modifier = modifier.fillMaxSize(),
        prompt = {
            TaskPromptChrome(
                title = title,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
                mutedTitle = title == "ABC",
                onTitleSpeak = { label -> if (label != "ABC") onSpeak(label) else onSpeakPrompt() },
            )
            if (sentence != null) {
                val displays = sentence.displayOverride
                    ?: sentence.atomIds.map { atoms[it]?.display ?: it }
                // Consume gaps in sentence order; supports an atom repeated in one sentence.
                val gapQueue = gaps.groupBy { it.atomId }.mapValues { it.value.toMutableList() }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    sentence.atomIds.forEachIndexed { index, atomId ->
                        val shown = displays.getOrElse(index) { atoms[atomId]?.display ?: atomId }
                        val gap = gapQueue[atomId]?.removeFirstOrNull()
                        if (gap != null) {
                            GapTarget(gap)
                        } else {
                            Text(
                                text = shown,
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.clickable { onSpeak(shown) },
                            )
                        }
                    }
                }
            }
        },
        onCorrect = { onResult(true, false, scoredIds) },
        onMiss = { atomId ->
            misses += 1
            onResult(false, false, listOf(atomId))
        },
        onResolve = { onResult(false, true, scoredIds) },
    )
}
