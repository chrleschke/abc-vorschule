package app.abcvorschule.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.content.LessonEmojis
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.Lesson
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.content.TaskSpec
import app.abcvorschule.content.WordBuildRound
import app.abcvorschule.content.rounds
import app.abcvorschule.content.scoredAtomIds
import app.abcvorschule.content.spokenAnswer
import app.abcvorschule.progress.AttemptOutcome
import app.abcvorschule.progress.LearnerProgress
import app.abcvorschule.progress.LessonGating
import app.abcvorschule.progress.LessonState
import app.abcvorschule.progress.ParentMode
import app.abcvorschule.progress.ProgressRepository
import app.abcvorschule.progress.ProgressionEngine
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.progress.SessionSnapshot
import app.abcvorschule.ui.exercise.MathHinting
import app.abcvorschule.ui.rewards.PraisePhrases
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SessionViewModel(
    private val contentRepository: ContentRepository,
    private val progressRepository: ProgressRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(SessionUiState())
    val ui: StateFlow<SessionUiState> = _ui.asStateFlow()

    private lateinit var pack: ContentPack
    private var progress: LearnerProgress = LearnerProgress()

    /** Backs [lessonEmojis] — computed once when [pack] loads, not on every call. */
    private var lessonEmojisByLessonId: Map<String, List<String>> = emptyMap()

    init {
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        runCatching {
            pack = withContext(Dispatchers.IO) { contentRepository.load() }
            lessonEmojisByLessonId = pack.lessons.associate { it.id to LessonEmojis.forLesson(pack, it) }
            progress = progressRepository.current()
            val snapshot = progress.unfinishedSession
            val resumable = snapshot != null &&
                snapshot.packId == pack.manifest.packId &&
                pack.lessons.any { it.id == snapshot.lessonId } &&
                LessonGating.isPlayable(LessonGating.stateOf(pack, progress, snapshot.lessonId))
            if (resumable) {
                openLesson(
                    snapshot!!.lessonId,
                    snapshot.trainerIndex,
                    snapshot.roundIndex,
                    snapshot.pointsEarned,
                    expectedTrainerCount = snapshot.trainerCount,
                )
            } else {
                // A stale snapshot (e.g. a content edit changed the lesson's taskIds
                // without bumping packId) must not strand the app on the loading
                // screen forever — clear it and fall through to the Path.
                if (snapshot != null) progressRepository.saveSession(null)
                _ui.value = SessionUiState(
                    screen = AppScreen.Path,
                    points = progress.points,
                    ready = true,
                )
            }
        }.onFailure { error ->
            _ui.update {
                it.copy(error = error.message ?: "Inhalt konnte nicht geladen werden", ready = false)
            }
        }
    }

    fun contentPack(): ContentPack? = if (this::pack.isInitialized) pack else null

    fun pathLessons(): List<Lesson> = if (this::pack.isInitialized) pack.lessons else emptyList()

    fun lessonStates(): Map<String, LessonState> =
        if (this::pack.isInitialized) LessonGating.states(pack, progress) else emptyMap()

    fun highlightedLessonId(): String? =
        if (this::pack.isInitialized) LessonGating.nextPlayable(pack, progress)?.id else null

    /**
     * Signpost emojis per lesson id. Computed once in [bootstrap] when the pack
     * loads and cached from then on — not rebuilt on every call — so every call
     * after the pack has loaded returns the same Map instance. That is all this
     * accessor guarantees: whether a Compose caller actually skips also depends on
     * the other arguments it passes alongside this one.
     */
    fun lessonEmojis(): Map<String, List<String>> =
        if (this::pack.isInitialized) lessonEmojisByLessonId else emptyMap()

    /** Spoken cue for a locked/planned node — a tap must always produce feedback. */
    fun lockedLessonCue(): String = "Das üben wir später."

    fun openLesson(
        lessonId: String,
        trainerIndex: Int = 0,
        roundIndex: Int = 0,
        sessionPoints: Int = 0,
        expectedTrainerCount: Int? = null,
    ) {
        viewModelScope.launch {
            runCatching {
                progress = progressRepository.current()
                val lesson = pack.lessons.firstOrNull { it.id == lessonId }
                val playable = lesson != null &&
                    LessonGating.isPlayable(LessonGating.stateOf(pack, progress, lessonId))
                if (lesson == null || !playable) {
                    // Never leave the UI hanging on the loading screen: a lesson that
                    // turned out not to be playable (e.g. a stale resume snapshot)
                    // must fall back to the Path, not a silent no-op.
                    progressRepository.saveSession(null)
                    _ui.value = SessionUiState(
                        screen = AppScreen.Path,
                        points = progress.points,
                        ready = true,
                    )
                    return@runCatching
                }
                val trainers = SymbolHuntInsertion.insertSymbolHunts(
                    pack.tasksOf(lesson).map { schedule(it) },
                    pack,
                    lesson.id,
                    lesson.index,
                )
                val step = SessionProgression.resumeSafe(expectedTrainerCount, trainers.size, trainerIndex, roundIndex)
                val counts = trainers.map { it.spec.rounds.size }
                val safeRound = step.roundIndex.coerceIn(0, (counts.getOrElse(step.trainerIndex) { 1 } - 1).coerceAtLeast(0))
                _ui.value = SessionUiState(
                    screen = AppScreen.Practice,
                    lessonId = lessonId,
                    trainers = trainers,
                    trainerIndex = step.trainerIndex,
                    roundIndex = safeRound,
                    points = progress.points,
                    sessionPoints = sessionPoints,
                    ready = true,
                )
                persistSnapshot()
            }.onFailure { error ->
                _ui.update {
                    it.copy(error = error.message ?: "Inhalt konnte nicht geladen werden", ready = false)
                }
            }
        }
    }

    /**
     * @param clearSnapshot Whether to discard the resume snapshot. An unfinished
     * lesson resumes where the child left off, so a hardware-back exit keeps it;
     * only a genuine finish (already cleared in [advance]) or an explicit
     * [continueAfterSummary] ends the session for good.
     */
    fun backToPath(clearSnapshot: Boolean) {
        viewModelScope.launch {
            if (clearSnapshot) progressRepository.saveSession(null)
            _ui.update {
                it.copy(
                    screen = AppScreen.Path,
                    lessonId = null,
                    trainers = emptyList(),
                    trainerIndex = 0,
                    roundIndex = 0,
                    sessionPoints = 0,
                    speakCue = null,
                    successPhase = SuccessPhase.Idle,
                    successSpeakText = null,
                    points = progress.points,
                )
            }
        }
    }

    private fun schedule(spec: TaskSpec): ScheduledTrainer {
        val atomIds = spec.rounds.flatMap { round ->
            round.scoredAtomIds() + sentenceAtomIds(round)
        }.distinct()
        return ScheduledTrainer(
            spec = spec,
            scaffolds = atomIds.associateWith { ProgressionEngine.scaffoldForAtom(progress, it) },
            mathScaffolds = spec.rounds.filterIsInstance<CountAddRound>().associate { round ->
                val key = ProgressionEngine.mathKey(round)
                key to ProgressionEngine.scaffoldForMath(progress, key)
            },
        )
    }

    private fun sentenceAtomIds(round: app.abcvorschule.content.TrainerRound): List<String> =
        if (round is SentenceOrderRound) pack.sentence(round.sentenceId).atomIds else emptyList()

    fun goPreviousRound() {
        _ui.update { state ->
            val step = SessionProgression.previous(
                state.trainerIndex,
                state.roundIndex,
                state.trainers.map { it.spec.rounds.size },
            )
            if (step == null || state.successPhase != SuccessPhase.Idle) {
                state
            } else {
                state.copy(
                    trainerIndex = step.trainerIndex,
                    roundIndex = step.roundIndex,
                    speakCue = null,
                    successPhase = SuccessPhase.Idle,
                    successSpeakText = null,
                )
            }
        }
    }

    fun goNextRound() {
        _ui.update { state ->
            val step = SessionProgression.next(
                state.trainerIndex,
                state.roundIndex,
                state.trainers.map { it.spec.rounds.size },
            )
            if (step == null || state.successPhase != SuccessPhase.Idle) {
                state
            } else {
                state.copy(
                    trainerIndex = step.trainerIndex,
                    roundIndex = step.roundIndex,
                    speakCue = null,
                    successPhase = SuccessPhase.Idle,
                    successSpeakText = null,
                )
            }
        }
    }

    fun clearSpeakCue() {
        _ui.update { it.copy(speakCue = null) }
    }

    fun currentPromptText(ttsAvailable: Boolean): String {
        val state = _ui.value
        val round = state.currentRound ?: return ""
        if (ttsAvailable) return round.promptTts
        // No German voice: Rechnen falls back to a numeral prompt, others keep the text.
        return if (round is CountAddRound) {
            val symbol = app.abcvorschule.ui.exercise.MathOperation.fromWireName(round.operation)?.symbol ?: "+"
            "${round.left} $symbol ${round.right} = ?"
        } else round.promptTts
    }

    /**
     * @param praise Whether to lead with a random word of praise. Only Rechnen gets it,
     * and only for an answer the child actually found — a resolve is not a success.
     */
    fun successSpeakTextForCurrent(praise: Boolean): String = when (val round = _ui.value.currentRound) {
        // Speak the counted objects, not a bare number: "zwei Ameisen". The praise goes
        // first so the quantity — the didactically relevant part — is heard last.
        is CountAddRound -> {
            val answer = round.spokenAnswer(pack.atoms[round.iconAtomId])
            if (praise) "${PraisePhrases.pick()}! $answer" else answer
        }
        is SyllableMergeRound -> round.resultDisplay
        is WordBuildRound -> pack.atoms[round.targetAtomId]?.display ?: round.promptTts
        is SentenceOrderRound -> pack.sentence(round.sentenceId).tts
        is LetterTraceRound -> round.rewardTts
        is SoundPositionRound -> pack.atoms[round.atomId]?.lemma ?: round.promptTts
        is SymbolHuntRound -> pack.atoms[round.targetAtomId]?.lemma ?: round.promptTts
        else -> ""
    }

    fun onSuccessSpeechFinished() {
        _ui.update {
            if (it.successPhase != SuccessPhase.SpeakAnswer) {
                it
            } else {
                it.copy(successPhase = SuccessPhase.ShowBurst, successSpeakText = null)
            }
        }
    }

    fun onSuccessBurstFinished() {
        viewModelScope.launch {
            _ui.update { it.copy(successPhase = SuccessPhase.Idle, successSpeakText = null) }
            advance()
        }
    }

    fun onRevealFinished() {
        viewModelScope.launch {
            if (_ui.value.successPhase != SuccessPhase.RevealAnswer) return@launch
            _ui.update { it.copy(successPhase = SuccessPhase.Idle, successSpeakText = null) }
            advance()
        }
    }

    fun openDifficultySheet() {
        _ui.update { it.copy(showDifficultySheet = true) }
    }

    fun dismissDifficultySheet() {
        _ui.update { it.copy(showDifficultySheet = false) }
    }

    fun setParentMode(mode: ParentMode) {
        viewModelScope.launch {
            progress = progressRepository.setParentMode(mode)
            _ui.update { state ->
                // F7: a mid-round change applies from the next round on.
                val updated = state.trainers.mapIndexed { i, trainer ->
                    if (i <= state.trainerIndex) trainer else schedule(trainer.spec)
                }
                state.copy(showDifficultySheet = false, trainers = updated)
            }
        }
    }

    /** @return true when the Activity should finish. */
    fun onBackPressed(): Boolean = when (_ui.value.screen) {
        AppScreen.Path -> true
        AppScreen.Practice -> {
            if (_ui.value.sessionPoints > 0) {
                _ui.update { it.copy(screen = AppScreen.RewardSummary) }
            } else {
                // Backing out mid-lesson: keep the resume snapshot.
                backToPath(clearSnapshot = false)
            }
            false
        }
        AppScreen.RewardSummary -> {
            // Backing out of the summary is still a mid-lesson exit unless the lesson
            // actually finished, in which case advance() already cleared the snapshot.
            backToPath(clearSnapshot = false)
            false
        }
    }

    fun continueAfterSummary() {
        backToPath(clearSnapshot = true)
    }

    fun submitRoundResult(correct: Boolean, resolved: Boolean, atomIds: List<String>) {
        viewModelScope.launch {
            if (_ui.value.successPhase != SuccessPhase.Idle) return@launch
            val taskId = _ui.value.current?.spec?.id ?: return@launch
            val outcome = outcomeFor(correct, resolved)
            progress = progressRepository.update { current ->
                var next = current
                atomIds.distinct().forEach { next = ProgressionEngine.recordAtomAttempt(next, it, outcome) }
                next = ProgressionEngine.recordTaskAttempt(next, taskId, outcome)
                if (correct && !resolved) next = ProgressionEngine.awardPoints(next, POINTS_PER_CORRECT)
                next
            }
            afterAttempt(correct && !resolved, resolved, !correct && !resolved)
        }
    }

    fun submitMathResult(distance: Int?, resolved: Boolean, correct: Boolean) {
        viewModelScope.launch {
            if (_ui.value.successPhase != SuccessPhase.Idle) return@launch
            val trainer = _ui.value.current ?: return@launch
            val round = _ui.value.currentRound as? CountAddRound ?: return@launch
            val key = ProgressionEngine.mathKey(round)
            val outcome = outcomeFor(correct, resolved)
            progress = progressRepository.update { current ->
                var next = ProgressionEngine.recordMathAttempt(current, key, outcome)
                next = ProgressionEngine.recordTaskAttempt(next, trainer.spec.id, outcome)
                if (correct && !resolved) next = ProgressionEngine.awardPoints(next, POINTS_PER_CORRECT)
                next
            }
            afterAttempt(
                correct = correct && !resolved,
                resolved = resolved,
                missHint = !correct && !resolved,
                speakOverride = if (resolved || correct) null else MathHinting.missFeedback(distance),
            )
        }
    }

    private fun outcomeFor(correct: Boolean, resolved: Boolean): AttemptOutcome = when {
        resolved -> AttemptOutcome.Resolve
        correct -> AttemptOutcome.Correct
        else -> AttemptOutcome.Miss
    }

    private suspend fun afterAttempt(
        correct: Boolean,
        resolved: Boolean,
        missHint: Boolean,
        speakOverride: String? = null,
    ) {
        if (correct) {
            val phrase = successSpeakTextForCurrent(praise = true)
            _ui.update {
                it.copy(
                    points = progress.points,
                    sessionPoints = it.sessionPoints + POINTS_PER_CORRECT,
                    speakCue = null,
                    successSpeakText = phrase,
                    successPhase = SuccessPhase.SpeakAnswer,
                )
            }
            return
        }
        if (resolved) {
            // Briefly reveal the answer instead of jumping straight to the next round —
            // the one moment a stuck child asks for help must actually teach something.
            // No points are awarded for a resolve.
            _ui.update {
                it.copy(
                    successPhase = SuccessPhase.RevealAnswer,
                    successSpeakText = successSpeakTextForCurrent(praise = false),
                )
            }
            return
        }
        if (missHint) {
            // SymbolHuntTrainer already speaks the tapped tile's lemma synchronously,
            // in the Composable, before onResult ever reaches here. Setting speakCue
            // would queue the generic miss phrase right behind it, and SpeechController
            // flushes its queue on every speak() call — so the tile name would get cut
            // off and replaced before the child ever heard it. Every other round type
            // has no such synchronous speech, so this only special-cases SymbolHuntRound
            // by leaving speakCue untouched; everything else keeps setting it exactly
            // as before.
            val isSymbolHuntMiss = _ui.value.currentRound is SymbolHuntRound
            _ui.update {
                it.copy(
                    speakCue = if (isSymbolHuntMiss) it.speakCue else speakOverride ?: missCueForCurrent(),
                    points = progress.points,
                )
            }
        }
    }

    /** Miss feedback is spoken; content authors supply the didactic re-reading. */
    private fun missCueForCurrent(): String = when (val round = _ui.value.currentRound) {
        is SoundPositionRound -> round.missTts
        else -> "Probiere eine andere Antwort"
    }

    private suspend fun advance() {
        val state = _ui.value
        val step = SessionProgression.next(
            state.trainerIndex,
            state.roundIndex,
            state.trainers.map { it.spec.rounds.size },
        )
        if (step == null) {
            progressRepository.saveSession(null)
            _ui.update {
                it.copy(
                    screen = AppScreen.RewardSummary,
                    speakCue = null,
                    successPhase = SuccessPhase.Idle,
                    successSpeakText = null,
                    points = progress.points,
                )
            }
            return
        }
        _ui.update {
            it.copy(
                trainerIndex = step.trainerIndex,
                roundIndex = step.roundIndex,
                speakCue = null,
                successPhase = SuccessPhase.Idle,
                successSpeakText = null,
                points = progress.points,
                trainers = it.trainers.mapIndexed { i, trainer ->
                    if (i < step.trainerIndex) trainer else schedule(trainer.spec)
                },
            )
        }
        persistSnapshot()
    }

    private suspend fun persistSnapshot() {
        val state = _ui.value
        val lessonId = state.lessonId ?: return
        progressRepository.saveSession(
            SessionSnapshot(
                lessonId = lessonId,
                trainerIndex = state.trainerIndex,
                roundIndex = state.roundIndex,
                pointsEarned = state.sessionPoints,
                packId = pack.manifest.packId,
                trainerCount = state.trainers.size,
            ),
        )
    }

    fun scaffoldFor(atomId: String): ScaffoldLevel =
        _ui.value.current?.scaffolds?.get(atomId) ?: ScaffoldLevel.Beginner

    fun parentMode(): ParentMode = progress.parentMode

    companion object {
        const val POINTS_PER_CORRECT = 1

        fun factory(
            contentRepository: ContentRepository,
            progressRepository: ProgressRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                SessionViewModel(contentRepository, progressRepository) as T
        }
    }
}
