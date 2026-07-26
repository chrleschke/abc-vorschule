package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import app.abcvorschule.content.Sentence
import app.abcvorschule.content.TaskTemplate
import app.abcvorschule.content.TaskType
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.session.ScheduledTask

@Composable
fun ReadingExercise(
    task: ScheduledTask,
    atoms: Map<String, Atom>,
    sentence: Sentence?,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = task.template
    val gapIds = gapAtomIds(template)
    val gaps = ScaffoldMapping.gaps(
        atomIds = gapIds,
        displays = gapIds.associateWith { atoms[it]?.display ?: it },
        emojis = gapIds.associateWith { atoms[it]?.emoji ?: "🔤" },
        scaffolds = task.scaffolds,
    )
    var misses by remember(template.id) { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (sentence != null) {
            val words = sentence.displayOverride ?: sentence.atomIds.map { atoms[it]?.display ?: it }
            Text(
                text = words.joinToString(" "),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Text(
                text = atoms[template.atomId]?.emoji ?: "📖",
                style = MaterialTheme.typography.displayLarge,
            )
        }
        DragSlotBoard(
            gaps = gaps,
            missCount = misses,
            onCorrect = { onResult(true, false, gapIds) },
            onMiss = {
                misses += 1
                onResult(false, false, gapIds)
            },
            onResolve = { onResult(false, true, gapIds) },
        )
    }
}

fun gapAtomIds(template: TaskTemplate): List<String> = when (template.type) {
    TaskType.sentence_cloze -> template.gapAtomIds
    TaskType.cloze, TaskType.speech_cloze -> template.slots.ifEmpty {
        listOfNotNull(template.targetAtomId ?: template.atomId)
    }
    else -> emptyList()
}

fun mixedScaffoldExample(
    scaffolds: Map<String, ScaffoldLevel>,
): Boolean = scaffolds.values.toSet().size > 1
