package app.abcvorschule.ui.shell

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.systemGestureExclusion
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.content.ContentPack
import app.abcvorschule.session.AppScreen
import app.abcvorschule.session.SessionUiState
import app.abcvorschule.session.SessionViewModel
import app.abcvorschule.session.SuccessPhase
import app.abcvorschule.speech.SpokenPart
import app.abcvorschule.ui.components.AbcNavChevron
import app.abcvorschule.ui.components.AbcSegmentedProgress
import app.abcvorschule.ui.components.abcStarCountHeight
import app.abcvorschule.ui.exercise.LocalEarlyTaps
import app.abcvorschule.ui.exercise.LocalPromptNudge
import app.abcvorschule.ui.exercise.LocalPromptRest
import app.abcvorschule.ui.exercise.LocalSpeakerBounds
import app.abcvorschule.ui.exercise.SpeakerBounds
import app.abcvorschule.ui.exercise.TrainerCallbacks
import app.abcvorschule.ui.exercise.TrainerHost
import app.abcvorschule.ui.exercise.rememberPromptRest
import app.abcvorschule.ui.path.PathScreen
import app.abcvorschule.ui.rewards.AbcSfx
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.rewards.Sfx
import app.abcvorschule.ui.rewards.StarCounterAnchor
import app.abcvorschule.ui.rewards.SuccessBurst
import app.abcvorschule.ui.rewards.playBlockedBlip
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.AbcMotion
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.PaperCenter
import app.abcvorschule.ui.theme.PaperEdge
import app.abcvorschule.ui.world.LocalChromeColors
import app.abcvorschule.ui.world.TrainerWorld
import app.abcvorschule.ui.world.WorldBackground
import app.abcvorschule.ui.world.WorldTaps
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
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
    onSpeakParts: suspend (List<SpokenPart>) -> Unit = {},
    onSpeakPartsSequenced: suspend (List<SpokenPart>, onPartComplete: (Int) -> Unit) -> Unit = { _, _ -> },
    onSpeakFeedbackVoiced: (SpokenPart) -> Unit = {},
    onStopSpeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalAbcHaptics.current
    // Der Erfolgs-Stern fliegt in den Punktestand (StarFlight); die Zahl dort springt
    // erst beim Einschlag. Bis dahin zeigt die Kopfzeile den alten Stand, obwohl der
    // Punkt schon verbucht ist — sonst wäre er da, bevor der Stern ankommt.
    val counterAnchor = remember { StarCounterAnchor() }
    var shownPoints by remember { mutableIntStateOf(state.points) }
    LaunchedEffect(state.points, state.successPhase) {
        val starUnderway = state.successPhase == SuccessPhase.SpeakAnswer ||
            state.successPhase == SuccessPhase.ShowBurst
        if (!starUnderway) shownPoints = state.points
    }
    // Wer die Lektion verlässt, verlässt auch ihre Stimme. Die Sprech-Effekte
    // hängen an der Übungs-Composition und werden beim Wechsel zwar gecancelt,
    // der bereits laufende Clip lief aber weiter — die Antwort der verlassenen
    // Runde wurde über dem Lernpfad zu Ende gesprochen.
    LaunchedEffect(state.screen) {
        if (state.screen != AppScreen.Practice) onStopSpeak()
    }
    // Bewusst OHNE globales safeDrawing-Padding: sonst liegt über und unter dem
    // Inhalt ein Papierband statt der Landschaft. Die Schutzbereiche sind
    // durchsichtig, jedes Element konsumiert seinen Inset selbst.
    //
    // Der Grund ist ein radialer Verlauf, keine Fläche ([PaperCenter] → [PaperEdge],
    // Herleitung und Messwerte in Color.kt). Der Lichtpunkt sitzt auf 42 % der Höhe
    // statt in der Mitte: dort liegt das Spielfeld der Trainer, während das obere
    // Fünftel Kopfzeile und Fortschrittsband trägt. Radius 78 % der Höhe, damit der
    // dunkelste Ton erst in den Ecken erreicht wird und nicht schon an den Längsseiten.
    //
    // `drawBehind` statt `background(Brush)`: nur hier ist die tatsächliche Größe
    // bekannt, und ohne sie ließen sich Mitte und Radius nicht relativ setzen —
    // `Brush.radialGradient` würde auf die halbe *kürzere* Kante zurückfallen und den
    // Verlauf schon an den Seitenrändern auslaufen lassen.
    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(PaperCenter, PaperEdge),
                        center = Offset(size.width / 2f, size.height * 0.42f),
                        radius = size.height * 0.78f,
                    ),
                )
            },
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
                // Absichtlich leer. Über dieser Fläche liegt noch der Splash —
                // MainActivity hält ihn, bis Pack oder Fehlermeldung stehen, und
                // beides führt in einen anderen Zweig. Sichtbar würde hier also
                // nur der Papierverlauf der umschließenden Box — PaperCenter am
                // Lichtpunkt (42 % Höhe) bis PaperEdge (#C5CDC9) an den Rändern.
                // Hex-gleich mit dem Splash-Grund im Hellen ist nur der
                // Lichtpunkt selbst, nicht die Fläche. Ein Platzhalter
                // („Silbo …", bis 2026-09) konnte nur noch als Aufblitzen
                // erscheinen.
            }
            state.screen == AppScreen.RewardSummary -> {
                RewardSummaryScreen(
                    finale = state.completedFinaleId?.let { pack.finales[it] },
                    lesson = state.completedFinaleId?.let { id -> pack.lessons.firstOrNull { it.finaleId == id } },
                    pack = pack,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeak = onSpeak,
                    onContinue = viewModel::continueAfterSummary,
                    // Randlos: der Abendhimmel läuft unter die Systemleisten, die Insets
                    // hält der Screen für seinen Inhalt selbst frei.
                    modifier = Modifier.fillMaxSize(),
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
                onSpeakParts = onSpeakParts,
                onSpeakPartsSequenced = onSpeakPartsSequenced,
                onSpeakFeedbackVoiced = onSpeakFeedbackVoiced,
                onStopSpeak = onStopSpeak,
                shownPoints = shownPoints,
                counterAnchor = counterAnchor,
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
            target = counterAnchor.takeIf { state.screen == AppScreen.Practice },
            onLanded = { shownPoints = state.points },
        )
    }
}

/**
 * Anteil der Höhe, die der Punktestand unter dem Fortschritt freigegeben hat
 * (er steht jetzt in der Kopfzeile), um den Speaker und mit ihm die ganze Bühne
 * nach oben rückt. Bewusst nicht 1: die Aufgabe soll höher stehen, aber nicht
 * an der Fortschrittszeile kleben.
 */
private const val SpeakerFollowFraction = 0.66f

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
    onSpeakParts: suspend (List<SpokenPart>) -> Unit = {},
    onSpeakPartsSequenced: suspend (List<SpokenPart>, onPartComplete: (Int) -> Unit) -> Unit = { _, _ -> },
    onSpeakFeedbackVoiced: (SpokenPart) -> Unit = {},
    onStopSpeak: () -> Unit,
    shownPoints: Int = state.points,
    counterAnchor: StarCounterAnchor? = null,
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
    // Ruhen und Aufwachen (PromptRest): die Aufgabe ruht, solange die Ansage läuft, und
    // ein Tipp in dieser Zeit bekommt eine leise Antwort statt zu verpuffen.
    val promptRest = rememberPromptRest(interactionLocked)
    val earlyTaps = remember { MutableSharedFlow<Offset>(extraBufferCapacity = 8) }
    var promptNudge by remember { mutableIntStateOf(0) }
    val earlyRings = remember { mutableStateListOf<EarlyTapRing>() }
    val stageOrigin = remember { StageOrigin() }
    val speakerBounds = remember { SpeakerBounds() }

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

    // Die Welt hinter dem Trainer (PRODUCT_PRINCIPLES §10, „Nachtwelten"): Tiefsee für
    // die Jagd, Dschungel für den Spurensucher, sonst der Papiergrund darunter. Die
    // Kopfzeile nimmt die passenden Farben über LocalChromeColors mit.
    val world = TrainerWorld.of(round)
    val worldTaps = remember { WorldTaps() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // Tipps auf die Welt (§10, „Antippen macht Freude"): was kein Bauteil der
            // Aufgabe verbraucht hat und kein Ziehen war, bekommt der Hintergrund.
            .pointerInput(worldTaps) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                    val up = waitForUpOrCancellation(pass = PointerEventPass.Final) ?: return@awaitEachGesture
                    if (down.isConsumed || up.isConsumed) return@awaitEachGesture
                    if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                    worldTaps.add(up.position)
                }
            },
    ) {
    WorldBackground(world = world, modifier = Modifier.matchParentSize(), taps = worldTaps)
    CompositionLocalProvider(LocalChromeColors provides world.chrome) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Zurück-Pfeil links, Punktestand mittig — kein Lektionstitel
        // (Elterntext an der Stelle, an der das Kind zuerst hinsieht). Der Stern
        // steht mittig statt am Titelende, also weiter auf der Achse, auf der am
        // Ende des Trainers der große Stern hochkommt (`SuccessBurst`), nur eine
        // Etage höher: in der Kopfzeile statt unter dem Fortschritt.
        AbcTopBar(
            points = shownPoints,
            centerPoints = true,
            onBack = viewModel::exitLesson,
            counterAnchor = counterAnchor,
            starOutline = LocalChromeColors.current.starOutline,
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

        // Der Punktestand stand früher hier, zwischen Fortschritt und Bühne. Was
        // er freigegeben hat, bekommt die Bühne nur zu [SpeakerFollowFraction]:
        // rückte der Speaker die volle Höhe nach, klebte er an der
        // Fortschrittszeile. Der Rest bleibt als Luft stehen.
        val freedByPointsInTopBar = abcStarCountHeight() + 6.dp
        Spacer(
            Modifier.height(
                AbcDimens.chromeGap + freedByPointsInTopBar * (1f - SpeakerFollowFraction),
            ),
        )

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
                    .padding(bottom = AbcDimens.screenBottomExtra)
                    .onGloballyPositioned { stageOrigin.topLeft = it.positionInRoot() }
                    // Frühe Tipps: gelesen im Final-Pass, nachdem die Trainer ihren Teil
                    // hatten. Was ein Kind-Element selbst verbraucht hat — der Lautsprecher
                    // spielt die Ansage ja auch während der Sperre —, zählt nicht.
                    .pointerInput(interactionLocked) {
                        if (!interactionLocked) return@pointerInput
                        awaitEachGesture {
                            // Im Final-Pass und ohne auf „verbraucht" zu achten: gesperrte
                            // Bauteile mancher Trainer nehmen Tipps trotzdem an sich.
                            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
                            val up = waitForUpOrCancellation(pass = PointerEventPass.Final) ?: return@awaitEachGesture
                            val at = stageOrigin.topLeft + up.position
                            // Der Lautsprecher spielt die Ansage — ein Tipp auf ihn ist gewollt.
                            if (speakerBounds.rect.inflate(SpeakerSlopPx).contains(at)) return@awaitEachGesture
                            if ((up.position - down.position).getDistance() > viewConfiguration.touchSlop) return@awaitEachGesture
                            val now = System.currentTimeMillis()
                            if (now - stageOrigin.lastEarlyTap < EarlyTapCooldownMs) return@awaitEachGesture
                            stageOrigin.lastEarlyTap = now
                            earlyTaps.tryEmit(at)
                            promptNudge++
                            earlyRings += EarlyTapRing(up.position)
                            // Leise: das „Blubb" darf die Ansage nicht übertönen.
                            AbcSfx.play(Sfx.Blubb, volume = EarlyTapVolume)
                        }
                    },
            ) {
                CompositionLocalProvider(
                    LocalPromptRest provides promptRest,
                    LocalEarlyTaps provides earlyTaps,
                    LocalPromptNudge provides promptNudge,
                    LocalSpeakerBounds provides speakerBounds,
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
                        onSpeakParts = onSpeakParts,
                        onSpeakPartsSequenced = onSpeakPartsSequenced,
                        onSpeakFeedbackVoiced = onSpeakFeedbackVoiced,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
                }
                EarlyTapRings(earlyRings, Modifier.matchParentSize())
            }
        }
    }
}
    }
}

/** Wo die Übungsfläche im Root liegt, und wann zuletzt früh getippt wurde. Kein State. */
private class StageOrigin {
    var topLeft: Offset = Offset.Zero
    var lastEarlyTap: Long = 0L
}

/** Ein kleiner, heller Ring an der Stelle eines frühen Tipps — ein Bläschen. */
private class EarlyTapRing(val at: Offset) {
    val progress = Animatable(0f)
}

@Composable
private fun EarlyTapRings(rings: SnapshotStateList<EarlyTapRing>, modifier: Modifier) {
    rings.forEach { ring ->
        key(ring) {
            LaunchedEffect(ring) {
                ring.progress.animateTo(1f, tween(EarlyTapRingMs, easing = AbcMotion.Exit))
                rings.remove(ring)
            }
        }
    }
    Canvas(modifier) {
        rings.forEach { ring ->
            val p = ring.progress.value
            drawCircle(
                color = Cream.copy(alpha = 0.55f * (1f - p)),
                radius = (10f + 26f * p).dp.toPx(),
                center = ring.at,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
    }
}

/** Frühe Tipps antworten höchstens so oft — ein Trommeln bleibt ein leises Blubbern. */
private const val EarlyTapCooldownMs = 220L
private const val SpeakerSlopPx = 24f
private const val EarlyTapVolume = 0.6f
private const val EarlyTapRingMs = 600
