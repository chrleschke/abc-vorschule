package app.abcvorschule.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Domain
import app.abcvorschule.session.AppScreen
import app.abcvorschule.session.SessionUiState
import app.abcvorschule.session.SessionViewModel
import app.abcvorschule.session.SuccessPhase
import app.abcvorschule.ui.components.AbcContinueButton
import app.abcvorschule.ui.components.AbcNavChevron
import app.abcvorschule.ui.components.AbcProgressBar
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.exercise.MathExercise
import app.abcvorschule.ui.exercise.ReadingExercise
import app.abcvorschule.ui.exercise.SpeechExercise
import app.abcvorschule.ui.rewards.SuccessBurst
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.NightInk
import kotlinx.coroutines.delay

@Composable
fun TaskShell(
    state: SessionUiState,
    pack: ContentPack?,
    viewModel: SessionViewModel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onSpeakAndAwait: suspend (String) -> Unit,
    onStopSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NightInk)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = AbcDimens.screenBottomExtra),
    ) {
        when {
            state.error != null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            !state.ready || pack == null -> {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("ABC-Vorschul App", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.screen == AppScreen.RewardSummary -> {
                RewardSummaryScreen(
                    sessionPoints = state.sessionPoints,
                    totalPoints = state.points,
                    onContinue = viewModel::continueAfterSummary,
                )
            }
            state.screen == AppScreen.Pause -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Pause", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(16.dp))
                    AbcContinueButton(
                        onClick = viewModel::resumeFromPause,
                        label = stringResource(R.string.pause_resume),
                        centered = true,
                    )
                }
            }
            else -> PracticeBody(
                state = state,
                pack = pack,
                viewModel = viewModel,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeak = onSpeak,
                onSpeakAndAwait = onSpeakAndAwait,
                onStopSpeak = onStopSpeak,
            )
        }

        if (state.showDifficultySheet) {
            DifficultySheet(
                current = viewModel.parentMode(),
                onSelect = viewModel::setParentMode,
                onDismiss = viewModel::dismissDifficultySheet,
            )
        }

        SuccessBurst(
            trigger = state.successPhase == SuccessPhase.ShowBurst,
            onFinished = viewModel::onSuccessBurstFinished,
        )
    }
}

@Composable
private fun PracticeBody(
    state: SessionUiState,
    pack: ContentPack,
    viewModel: SessionViewModel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onSpeakAndAwait: suspend (String) -> Unit,
    onStopSpeak: () -> Unit,
) {
    val task = state.current
    val speakPrompt = {
        onSpeak(viewModel.currentPromptText(ttsAvailable))
    }

    LaunchedEffect(task?.template?.id, ttsAvailable) {
        if (state.successPhase != SuccessPhase.Idle) return@LaunchedEffect
        onStopSpeak()
        if (ttsAvailable && task != null) {
            onSpeak(viewModel.currentPromptText(ttsAvailable = true))
        }
    }
    LaunchedEffect(state.speakCue) {
        val cue = state.speakCue ?: return@LaunchedEffect
        if (ttsAvailable) {
            onSpeak(cue)
        }
        viewModel.clearSpeakCue()
    }
    LaunchedEffect(state.successPhase, state.successSpeakText) {
        if (state.successPhase != SuccessPhase.SpeakAnswer) return@LaunchedEffect
        val phrase = state.successSpeakText
        if (ttsAvailable && !phrase.isNullOrBlank()) {
            onSpeakAndAwait(phrase)
        } else {
            delay(350)
        }
        viewModel.onSuccessSpeechFinished()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = AbcDimens.screenHorizontal)
            .padding(top = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ParentGateButton(onUnlocked = viewModel::openDifficultySheet)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconStar(tint = MaterialTheme.colorScheme.primary, size = 22.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${state.points}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(48.dp))
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            AbcNavChevron(
                forward = false,
                enabled = state.canGoPrevious && state.successPhase == SuccessPhase.Idle,
                onClick = viewModel::goPreviousTask,
                contentDescription = "Zurueck",
            )
            Spacer(Modifier.width(36.dp))
            AbcNavChevron(
                forward = true,
                enabled = state.canGoNext && state.successPhase == SuccessPhase.Idle,
                onClick = viewModel::goNextTask,
                contentDescription = "Weiter",
            )
        }

        Spacer(Modifier.height(8.dp))
        AbcProgressBar(index = state.index, total = state.tasks.size)
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.progressLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Spacer(Modifier.height(10.dp))

        if (task != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when (task.template.domain) {
                    Domain.reading -> ReadingExercise(
                        task = task,
                        atoms = pack.atoms,
                        sentence = task.template.sentenceId?.let { pack.sentences[it] },
                        ttsAvailable = ttsAvailable,
                        speaking = speaking,
                        onSpeakPrompt = speakPrompt,
                        onResult = viewModel::submitReadingAnswer,
                        onSpeak = onSpeak,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Domain.speech -> SpeechExercise(
                        task = task,
                        atoms = pack.atoms,
                        ttsAvailable = ttsAvailable,
                        speaking = speaking,
                        onSpeakPrompt = speakPrompt,
                        onResult = viewModel::submitReadingAnswer,
                        onSpeak = onSpeak,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Domain.math -> MathExercise(
                        task = task,
                        atom = task.template.atomId?.let { pack.atoms[it] },
                        scaffold = viewModel.effectiveMathScaffold(),
                        showSymbolPrompt = !ttsAvailable,
                        ttsAvailable = ttsAvailable,
                        speaking = speaking,
                        onSpeakPrompt = speakPrompt,
                        onSpeak = onSpeak,
                        onResult = viewModel::submitMathAnswer,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}
