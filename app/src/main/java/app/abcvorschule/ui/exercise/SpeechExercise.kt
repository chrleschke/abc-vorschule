package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.abcvorschule.content.Atom
import app.abcvorschule.session.ScheduledTask

@Composable
fun SpeechExercise(
    task: ScheduledTask,
    atoms: Map<String, Atom>,
    unlocked: Boolean,
    onUnlock: () -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val template = task.template
    val targetId = template.targetAtomId ?: template.atomId ?: return
    val target = atoms[targetId]
    val gapIds = gapAtomIds(template)
    val gaps = ScaffoldMapping.gaps(
        atomIds = gapIds,
        displays = gapIds.associateWith { atoms[it]?.display ?: it },
        emojis = gapIds.associateWith { atoms[it]?.emoji ?: "🔤" },
        scaffolds = task.scaffolds,
    )
    var misses by remember(template.id) { mutableIntStateOf(0) }

    Column(modifier = modifier.fillMaxWidth()) {
        if (!unlocked) {
            SpeakAloudCue(
                targetDisplay = target?.display ?: targetId,
                onContinue = onUnlock,
            )
        } else {
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
}
