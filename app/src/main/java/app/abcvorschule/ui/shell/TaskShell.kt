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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.content.ContentPack
import app.abcvorschule.session.AppScreen
import app.abcvorschule.session.SessionUiState
import app.abcvorschule.session.SessionViewModel
import app.abcvorschule.session.SuccessPhase
import app.abcvorschule.ui.components.AbcCloseButton
import app.abcvorschule.ui.components.AbcNavChevron
import app.abcvorschule.ui.components.AbcProgressBar
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.exercise.TrainerCallbacks
import app.abcvorschule.ui.exercise.TrainerHost
import app.abcvorschule.ui.path.PathScreen
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.SuccessBurst
import app.abcvorschule.ui.rewards.playBlockedBlip
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.StarGold
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TaskShell(
    state: SessionUiState,
    pack: ContentPack?,
    viewModel: SessionViewModel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onSpeakAndAwait: suspend (String) -> Unit,
    onSpeakPromptSequence: suspend (List<String>) -> Unit,
    onStopSpeak: () -> Unit,
    onOpenTtsDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAbcHaptics.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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
                    Text(state.error, color = MaterialTheme.colorScheme.error)
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
                    finale = state.completedFinaleId?.let { pack.finales[it] },
                    pack = pack,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeak = onSpeak,
                    onContinue = viewModel::continueAfterSummary,
                )
            }
            state.screen == AppScreen.Path -> {
                PathScreen(
                    lessons = viewModel.pathLessons(),
                    states = viewModel.lessonStates(),
                    unlockAllLessons = state.unlockAllLessons,
                    emojisByLessonId = viewModel.lessonEmojis(),
                    highlightedLessonId = viewModel.highlightedLessonId(),
                    advanceFromLessonId = state.pathAdvanceFromLessonId,
                    points = state.points,
                    onOpenLesson = { viewModel.openLesson(it) },
                    onLockedTap = {
                        // A tap must never be a silent no-op, with or without TTS.
                        haptics.nudge()
                        playBlockedBlip()
                        if (ttsAvailable) onSpeak(viewModel.lockedLessonCue())
                    },
                    onAdvanceAnimated = viewModel::onPathAdvanceAnimated,
                    onParentGateUnlocked = viewModel::openDifficultySheet,
                    onOpenTtsDebug = onOpenTtsDebug,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> PracticeBody(
                state = state,
                pack = pack,
                viewModel = viewModel,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeak = onSpeak,
                onSpeakAndAwait = onSpeakAndAwait,
                onSpeakPromptSequence = onSpeakPromptSequence,
                onStopSpeak = onStopSpeak,
            )
        }

        if (state.showDifficultySheet) {
            ParentSheet(
                currentMode = state.parentMode,
                unlockAllLessons = state.unlockAllLessons,
                onSelectMode = viewModel::setParentMode,
                onToggleUnlockAll = viewModel::setUnlockAllLessons,
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
    onSpeakPromptSequence: suspend (List<String>) -> Unit,
    onStopSpeak: () -> Unit,
) {
    val task = state.current
    val round = state.currentRound
    val haptics = LocalAbcHaptics.current
    val scope = rememberCoroutineScope()
    val speakPrompt = {
        scope.launch {
            onSpeakPromptSequence(viewModel.currentPromptParts())
        }
        Unit
    }

    LaunchedEffect(task?.spec?.id, state.roundIndex, ttsAvailable) {
        if (state.successPhase != SuccessPhase.Idle) return@LaunchedEffect
        onStopSpeak()
        if (ttsAvailable && task != null) {
            onSpeakPromptSequence(viewModel.currentPromptParts())
        }
    }
    LaunchedEffect(state.speakCue) {
        val cue = state.speakCue ?: return@LaunchedEffect
        if (ttsAvailable) {
            onSpeak(cue)
        } else {
            // No German voice: a miss must still be perceivable.
            haptics.nudge()
            playBlockedBlip()
        }
        viewModel.clearSpeakCue()
    }
    LaunchedEffect(state.successPhase, state.successSpeakParts) {
        if (state.successPhase != SuccessPhase.SpeakAnswer) return@LaunchedEffect
        val parts = state.successSpeakParts
        if (ttsAvailable && parts.isNotEmpty()) {
            onSpeakPromptSequence(parts)
        } else {
            delay(350)
        }
        viewModel.onSuccessSpeechFinished()
    }
    LaunchedEffect(state.successPhase, state.successSpeakParts) {
        if (state.successPhase != SuccessPhase.RevealAnswer) return@LaunchedEffect
        val parts = state.successSpeakParts
        if (ttsAvailable && parts.isNotEmpty()) {
            onSpeakPromptSequence(parts)
            delay(900)
        } else {
            delay(1400)
        }
        viewModel.onRevealFinished()
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
                IconStar(tint = StarGold, size = 22.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "${state.points}",
                    style = MaterialTheme.typography.titleLarge,
                    color = WarmInk,
                )
            }
            AbcCloseButton(onClick = viewModel::exitLesson)
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
                onClick = viewModel::goPreviousRound,
                contentDescription = stringResource(R.string.nav_back),
            )
            Spacer(Modifier.width(36.dp))
            AbcNavChevron(
                forward = true,
                enabled = state.canGoNext && state.successPhase == SuccessPhase.Idle,
                onClick = viewModel::goNextRound,
                contentDescription = stringResource(R.string.nav_forward),
            )
        }

        Spacer(Modifier.height(8.dp))
        AbcProgressBar(index = state.trainerIndex, total = state.trainers.size)
        Spacer(Modifier.height(4.dp))
        Text(
            text = state.trainerProgressLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        if (state.roundCount > 1) {
            Spacer(Modifier.height(6.dp))
            RoundProgressDots(
                roundCount = state.roundCount,
                roundIndex = state.roundIndex,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        Spacer(Modifier.height(10.dp))

        if (task != null && round != null) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                TrainerHost(
                    trainer = task,
                    round = round,
                    roundIndex = state.roundIndex,
                    pack = pack,
                    scaffoldFor = viewModel::scaffoldFor,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    callbacks = TrainerCallbacks(
                        onResult = viewModel::submitRoundResult,
                        onMathResult = viewModel::submitMathResult,
                        onSpeak = onSpeak,
                        onSpeakPrompt = speakPrompt,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

private val RoundDotSize = 8.dp

/**
 * Sub-progress within the current trainer: one dot per round, filled up to and
 * including the current one. Shapes only — no text, no emoji.
 */
@Composable
private fun RoundProgressDots(
    roundCount: Int,
    roundIndex: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(roundCount) { i ->
            val filled = i <= roundIndex
            Box(
                modifier = Modifier
                    .size(RoundDotSize)
                    .background(
                        color = if (filled) LeafGreen else WarmMuted.copy(alpha = 0.35f),
                        shape = CircleShape,
                    ),
            )
        }
    }
}
