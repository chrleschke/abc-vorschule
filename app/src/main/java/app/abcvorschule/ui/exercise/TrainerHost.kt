package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.content.TrainerRound
import app.abcvorschule.content.WordBuildRound
import app.abcvorschule.progress.ProgressionEngine
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.session.ScheduledTrainer
import app.abcvorschule.ui.components.AbcContinueButton

/** Callbacks every trainer reports through, so the ViewModel owns all sequencing. */
data class TrainerCallbacks(
    val onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    val onMathResult: (distance: Int?, resolved: Boolean, correct: Boolean) -> Unit,
    val onSpeak: (String) -> Unit,
    val onSpeakPrompt: () -> Unit,
)

/** Dispatches a scheduled trainer's current round to its screen. */
@Composable
fun TrainerHost(
    trainer: ScheduledTrainer,
    round: TrainerRound,
    pack: ContentPack,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    callbacks: TrainerCallbacks,
    modifier: Modifier = Modifier,
) {
    when (round) {
        is SoundPositionRound -> TrainerPlaceholder("Trainer 1", modifier, callbacks)
        is LetterTraceRound -> TrainerPlaceholder("Trainer 2", modifier, callbacks)
        is SyllableMergeRound -> TrainerPlaceholder("Trainer 3", modifier, callbacks)
        is WordBuildRound -> TrainerPlaceholder("Trainer 4", modifier, callbacks)
        is SentenceOrderRound -> TrainerPlaceholder("Trainer 5", modifier, callbacks)
        is CountAddRound -> MathExercise(
            trainer = trainer,
            round = round,
            icon = pack.atom(round.iconAtomId).emoji,
            scaffold = trainer.mathScaffolds[ProgressionEngine.mathKey(round)]
                ?: ScaffoldLevel.Beginner,
            showSymbolPrompt = !ttsAvailable,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onMathResult,
            modifier = modifier.fillMaxSize(),
        )
        else -> TrainerPlaceholder("Trainer", modifier, callbacks)
    }
}

/** Temporary scaffolding, replaced trainer-by-trainer in later tasks. */
@Composable
private fun TrainerPlaceholder(
    label: String,
    modifier: Modifier,
    callbacks: TrainerCallbacks,
) {
    ExerciseStage(
        modifier = modifier.fillMaxSize(),
        prompt = {
            Text(label, style = MaterialTheme.typography.headlineMedium)
        },
        answers = {
            AbcContinueButton(onClick = { callbacks.onResult(true, false, emptyList()) })
        },
    )
}
