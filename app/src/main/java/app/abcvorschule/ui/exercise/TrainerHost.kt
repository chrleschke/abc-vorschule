package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SoundPositionSpec
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.content.SyllableMergeSpeech
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
    /** Wie [onSpeak], aber auf einem eigenen Audio-Kanal — für Tap-Echos, die eine
     * noch laufende Rundenansage nicht abwürgen dürfen (Wort-Detektiv, design doc). */
    val onSpeakFeedback: (String) -> Unit,
    val onSpeakAndAwait: suspend (String) -> Unit,
    val onSpeakPrompt: () -> Unit,
)

/** Dispatches a scheduled trainer's current round to its screen. */
@Composable
fun TrainerHost(
    trainer: ScheduledTrainer,
    round: TrainerRound,
    roundIndex: Int,
    pack: ContentPack,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    /** True, solange die Rundenansage (noch) nicht bis zu ihrem Freigabe-Index
     * gelaufen ist — siehe design doc. LetterTraceTrainer liest das nicht, da seine
     * Interaktion nie eigene Audio auslöst und die Ansage daher nie unterbrechen
     * kann. Die restlichen 7 Trainer bekommen den Wert erst in ihren eigenen Tasks
     * angeschlossen. */
    interactionLocked: Boolean = false,
    callbacks: TrainerCallbacks,
    modifier: Modifier = Modifier,
) {
    when (round) {
        is SoundPositionRound -> SoundPositionTrainer(
            round = round,
            roundIndex = roundIndex,
            atom = pack.atom(round.atomId),
            targetPhoneme = (trainer.spec as? SoundPositionSpec)?.phonemeTts.orEmpty(),
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            interactionLocked = interactionLocked,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
        is LetterTraceRound -> LetterTraceTrainer(
            round = round,
            roundIndex = roundIndex,
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
            roundIndex = roundIndex,
            resultSpeech = SyllableMergeSpeech.resultSpeech(round, pack.atoms[round.resultAtomId]),
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            interactionLocked = interactionLocked,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
        is WordBuildRound -> WordBuildTrainer(
            round = round,
            roundIndex = roundIndex,
            pack = pack,
            target = pack.atom(round.targetAtomId),
            scaffoldFor = scaffoldFor,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            interactionLocked = interactionLocked,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onSpeakAndAwait = callbacks.onSpeakAndAwait,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
        is SentenceOrderRound -> {
            val sentence = pack.sentence(round.sentenceId)
            SentenceOrderTrainer(
                round = round,
                roundIndex = roundIndex,
                words = pack.sentenceWords(sentence),
                atomIds = sentence.atomIds,
                illustrationEmoji = round.illustrationAtomId?.let { pack.atom(it).emoji },
                scaffoldFor = scaffoldFor,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                interactionLocked = interactionLocked,
                onSpeakPrompt = callbacks.onSpeakPrompt,
                onSpeak = callbacks.onSpeak,
                onResult = callbacks.onResult,
                modifier = modifier.fillMaxSize(),
            )
        }
        is CountAddRound -> MathExercise(
            trainer = trainer,
            round = round,
            roundIndex = roundIndex,
            icon = pack.atom(round.iconAtomId).emoji,
            scaffold = trainer.mathScaffolds[ProgressionEngine.mathKey(round)]
                ?: ScaffoldLevel.Beginner,
            showSymbolPrompt = !ttsAvailable,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            interactionLocked = interactionLocked,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onMathResult,
            modifier = modifier.fillMaxSize(),
        )
        is SymbolHuntRound -> SymbolHuntTrainer(
            round = round,
            roundIndex = roundIndex,
            pack = pack,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            interactionLocked = interactionLocked,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
        is SymbolInWordRound -> SymbolInWordTrainer(
            round = round,
            roundIndex = roundIndex,
            pack = pack,
            scaffoldFor = scaffoldFor,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            interactionLocked = interactionLocked,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onSpeakFeedback = callbacks.onSpeakFeedback,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
    }
}
