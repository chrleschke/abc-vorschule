package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.fillMaxSize
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
        is SoundPositionRound -> SoundPositionTrainer(
            round = round,
            atom = pack.atom(round.atomId),
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
        is LetterTraceRound -> LetterTraceTrainer(
            round = round,
            atom = pack.atom(round.atomId),
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
        is SyllableMergeRound -> SyllableMergeTrainer(
            round = round,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
        is WordBuildRound -> WordBuildTrainer(
            round = round,
            target = pack.atom(round.targetAtomId),
            scaffoldFor = scaffoldFor,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
        is SentenceOrderRound -> {
            val sentence = pack.sentence(round.sentenceId)
            SentenceOrderTrainer(
                round = round,
                words = pack.sentenceWords(sentence),
                atomIds = sentence.atomIds,
                illustrationEmoji = round.illustrationAtomId?.let { pack.atom(it).emoji },
                scaffoldFor = scaffoldFor,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = callbacks.onSpeakPrompt,
                onSpeak = callbacks.onSpeak,
                onResult = callbacks.onResult,
                modifier = modifier.fillMaxSize(),
            )
        }
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
    }
}
