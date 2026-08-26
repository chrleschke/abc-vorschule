package app.abcvorschule.ui.shell

import androidx.compose.foundation.background
import androidx.compose.foundation.systemGestureExclusion
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
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import app.abcvorschule.ui.components.AbcNavChevron
import app.abcvorschule.ui.components.AbcSegmentedProgress
import app.abcvorschule.ui.exercise.TrainerCallbacks
import app.abcvorschule.ui.exercise.TrainerHost
import app.abcvorschule.ui.path.PathScreen
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.SuccessBurst
import app.abcvorschule.ui.rewards.playBlockedBlip
import app.abcvorschule.ui.theme.AbcDimens
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
    onSpeakFeedback: (String) -> Unit,
    onSpeakCounting: (String) -> Unit,
    onSpeakAndAwait: suspend (String) -> Unit,
    onSpeakPromptSequence: suspend (List<String>) -> Unit,
    onSpeakIntroSequence: suspend (List<String>, onPartComplete: (Int) -> Unit) -> Unit,
    onStopSpeak: () -> Unit,
    onOpenTtsDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAbcHaptics.current
    // Bewusst OHNE globales safeDrawing-Padding: sonst liegt über und unter dem
    // Inhalt ein Cream-Band statt der Landschaft. Die Schutzbereiche sind
    // durchsichtig, jedes Element konsumiert seinen Inset selbst.
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            state.error != null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }
            }
            !state.ready || pack == null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
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
                    modifier = Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(bottom = AbcDimens.screenBottomExtra),
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
                onSpeakFeedback = onSpeakFeedback,
                onSpeakCounting = onSpeakCounting,
                onSpeakAndAwait = onSpeakAndAwait,
                onSpeakPromptSequence = onSpeakPromptSequence,
                onSpeakIntroSequence = onSpeakIntroSequence,
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
                onOpenTtsDebug = {
                    viewModel.dismissDifficultySheet()
                    onOpenTtsDebug()
                },
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
    onSpeakFeedback: (String) -> Unit,
    onSpeakCounting: (String) -> Unit,
    onSpeakAndAwait: suspend (String) -> Unit,
    onSpeakPromptSequence: suspend (List<String>) -> Unit,
    onSpeakIntroSequence: suspend (List<String>, onPartComplete: (Int) -> Unit) -> Unit,
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

    // Zurückgesetzt auf true, sobald Runde/Task wechseln — sofort in derselben
    // Composition, damit kein Frame lang die neue Runde fälschlich entsperrt
    // aussieht, bevor der Effekt unten läuft (siehe design doc).
    var interactionLocked by remember(task?.spec?.id, state.roundIndex) { mutableStateOf(true) }

    LaunchedEffect(task?.spec?.id, state.roundIndex, ttsAvailable) {
        if (state.successPhase != SuccessPhase.Idle) return@LaunchedEffect
        onStopSpeak()
        // Auch nötig, nicht nur der `remember` oben: deckt den Fall ab, dass
        // `ttsAvailable` MITTEN in der Runde von false auf true kippt (TTS-Engine
        // wird erst nach dem Rundenstart bereit) — dann muss re-gesperrt werden,
        // obwohl Task/Runde sich nicht geändert haben.
        interactionLocked = true
        if (ttsAvailable && task != null) {
            val parts = viewModel.currentPromptParts()
            if (parts.isEmpty()) {
                interactionLocked = false
            } else {
                val unlockIndex = viewModel.currentPromptUnlockIndex()
                onSpeakIntroSequence(parts) { index ->
                    if (index == unlockIndex) interactionLocked = false
                }
                interactionLocked = false
            }
        } else {
            interactionLocked = false
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

    val lessonTitle = state.lessonId?.let { id -> pack.lessons.firstOrNull { it.id == id }?.title }

    Column(modifier = Modifier.fillMaxSize()) {
        AbcTopBar(
            points = state.points,
            title = lessonTitle,
            onClose = viewModel::exitLesson,
        )

        // Fortschritt und die beiden Rückfall-Chevrons teilen sich eine Zeile
        // direkt unter der Kopfzeile: die Chevrons an den Rändern, gedämpft und
        // ohne Gehäuse, die Segmentkette dazwischen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AbcNavChevron(
                forward = false,
                enabled = state.canGoPrevious && state.successPhase == SuccessPhase.Idle,
                onClick = viewModel::goPreviousRound,
                contentDescription = stringResource(R.string.nav_back),
            )
            AbcSegmentedProgress(
                index = state.trainerIndex,
                total = state.trainers.size,
                roundIndex = state.roundIndex,
                roundCount = state.roundCount,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            )
            AbcNavChevron(
                forward = true,
                enabled = state.canGoNext && state.successPhase == SuccessPhase.Idle,
                onClick = viewModel::goNextRound,
                contentDescription = stringResource(R.string.nav_forward),
            )
        }

        Spacer(Modifier.height(AbcDimens.chromeGap))

        if (task != null && round != null) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = AbcDimens.screenHorizontal)
                    // Die ganze Übungsfläche gehört der App: ein Ziehen, das nah am
                    // Bildschirmrand beginnt — der Silben-Verschmelzer verlangt genau
                    // das — darf nicht als System-Zurück-Geste enden und die Lektion
                    // abbrechen. Ohne den Vollbildmodus aus MainActivity griffe hier
                    // Androids 200-dp-Deckel je Kante und die Fläche bliebe nur zum
                    // Teil geschützt.
                    .systemGestureExclusion()
                    // Nur die Unterkante: oben hat die Kopfzeile den Status-Bar-Inset
                    // schon verbraucht. safeDrawing statt navigationBars, weil hier
                    // auch die System-Zahlentastatur hochkommt (§8) — sie muss den
                    // Aufgabenbereich weiterhin nach oben schieben.
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom))
                    .padding(bottom = AbcDimens.screenBottomExtra),
            ) {
                TrainerHost(
                    trainer = task,
                    round = round,
                    roundIndex = state.roundIndex,
                    pack = pack,
                    scaffoldFor = viewModel::scaffoldFor,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    interactionLocked = interactionLocked,
                    callbacks = TrainerCallbacks(
                        onResult = viewModel::submitRoundResult,
                        onMathResult = viewModel::submitMathResult,
                        onSpeak = onSpeak,
                        onSpeakFeedback = onSpeakFeedback,
                        onSpeakCounting = onSpeakCounting,
                        onSpeakAndAwait = onSpeakAndAwait,
                        onSpeakPrompt = speakPrompt,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
