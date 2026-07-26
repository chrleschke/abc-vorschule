package app.abcvorschule.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
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
import app.abcvorschule.content.TaskType
import app.abcvorschule.session.AppScreen
import app.abcvorschule.session.SessionUiState
import app.abcvorschule.session.SessionViewModel
import app.abcvorschule.ui.exercise.MathExercise
import app.abcvorschule.ui.exercise.ReadingExercise
import app.abcvorschule.ui.exercise.SpeechExercise
import app.abcvorschule.ui.rewards.SuccessBurst
import app.abcvorschule.ui.theme.NightInk

@Composable
fun TaskShell(
    state: SessionUiState,
    pack: ContentPack?,
    viewModel: SessionViewModel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onStopSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NightInk),
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
                    Text("Lädt…", color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Button(onClick = viewModel::resumeFromPause) {
                        Text(stringResource(R.string.pause_resume))
                    }
                }
            }
            else -> PracticeBody(
                state = state,
                pack = pack,
                viewModel = viewModel,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeak = onSpeak,
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
            trigger = state.lastSuccess,
            onFinished = viewModel::clearFeedback,
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
    onStopSpeak: () -> Unit,
) {
    val task = state.current
    LaunchedEffect(task?.template?.id) {
        onStopSpeak()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = state.progressLabel,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = "⭐ ${state.points}",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            SpeakerButton(
                enabled = ttsAvailable,
                speaking = speaking,
                onClick = {
                    val text = viewModel.currentPromptText(ttsAvailable)
                    onSpeak(text)
                },
            )
            ParentGateButton(onUnlocked = viewModel::openDifficultySheet)
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = state.packTitle.ifBlank { "ABC-Vorschul App" },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        state.feedback?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.padding(vertical = 8.dp),
            )
        }

        Spacer(Modifier.height(8.dp))

        if (task != null) {
            when (task.template.domain) {
                Domain.reading -> ReadingExercise(
                    task = task,
                    atoms = pack.atoms,
                    sentence = task.template.sentenceId?.let { pack.sentences[it] },
                    onResult = viewModel::submitReadingAnswer,
                )
                Domain.speech -> SpeechExercise(
                    task = task,
                    atoms = pack.atoms,
                    unlocked = state.speechUnlocked,
                    onUnlock = {
                        viewModel.unlockSpeech()
                        onSpeak(viewModel.currentPromptText(ttsAvailable))
                    },
                    onResult = viewModel::submitReadingAnswer,
                )
                Domain.math -> MathExercise(
                    task = task,
                    atom = task.template.atomId?.let { pack.atoms[it] },
                    scaffold = viewModel.effectiveMathScaffold(),
                    showSymbolPrompt = !ttsAvailable || task.template.type == TaskType.number_entry,
                    onResult = viewModel::submitMathAnswer,
                )
            }
        }
    }
}
