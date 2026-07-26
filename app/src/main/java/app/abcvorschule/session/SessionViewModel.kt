package app.abcvorschule.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.Domain
import app.abcvorschule.content.TaskTemplate
import app.abcvorschule.content.composePartsFor
import app.abcvorschule.progress.AttemptOutcome
import app.abcvorschule.progress.LearnerProgress
import app.abcvorschule.progress.ParentMode
import app.abcvorschule.progress.ProgressRepository
import app.abcvorschule.progress.ProgressionEngine
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.progress.SessionSnapshot
import app.abcvorschule.ui.exercise.MathHinting
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
    private val scheduler: SessionScheduler = SessionScheduler(),
) : ViewModel() {

    private val _ui = MutableStateFlow(SessionUiState())
    val ui: StateFlow<SessionUiState> = _ui.asStateFlow()

    private lateinit var pack: ContentPack
    private var progress: LearnerProgress = LearnerProgress()

    init {
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        runCatching {
            pack = withContext(Dispatchers.IO) { contentRepository.load() }
            progress = progressRepository.current()
            val snapshot = progress.unfinishedSession
            if (snapshot != null && snapshot.packId == pack.manifest.packId && snapshot.taskIds.isNotEmpty()) {
                restore(snapshot)
            } else {
                startFreshSession()
            }
        }.onFailure { error ->
            _ui.update { it.copy(error = error.message ?: "Inhalt konnte nicht geladen werden", ready = false) }
        }
    }

    private suspend fun startFreshSession() {
        progress = progressRepository.current()
        val templates = scheduler.buildSession(pack, progress)
        val scheduled = templates.map { schedule(it, progress) }
        _ui.value = SessionUiState(
            screen = AppScreen.Practice,
            tasks = scheduled,
            index = 0,
            points = progress.points,
            sessionPoints = 0,
            ready = true,
        )
        persistSnapshot()
    }

    private fun restore(snapshot: SessionSnapshot) {
        val templates = snapshot.taskIds.mapNotNull { id -> pack.tasks.find { it.id == id } }
        val scheduled = templates.map { schedule(it, progress) }
        val idx = snapshot.index.coerceIn(0, scheduled.lastIndex.coerceAtLeast(0))
        _ui.value = SessionUiState(
            screen = AppScreen.Practice,
            tasks = scheduled,
            index = idx,
            points = progress.points,
            sessionPoints = snapshot.pointsEarned,
            ready = true,
        )
    }

    fun contentPack(): ContentPack? = if (this::pack.isInitialized) pack else null

    private fun schedule(template: TaskTemplate, progress: LearnerProgress): ScheduledTask {
        val atom = template.atomId?.let { pack.atoms[it] }
        val parts = template.composePartsFor(atom)
        val scaffolds = parts.map { it.atomId }.distinct()
            .associateWith { ProgressionEngine.scaffoldForAtom(progress, it) }
        val distractors = DistractorPicker.pick(template, parts, pack, progress)
        return ScheduledTask(template, scaffolds, distractors)
    }

    fun goPreviousTask() {
        _ui.update { state ->
            if (!state.canGoPrevious || state.successPhase != SuccessPhase.Idle) state
            else state.copy(
                index = state.index - 1,
                speakCue = null,
                successPhase = SuccessPhase.Idle,
                successSpeakText = null,
            )
        }
    }

    fun goNextTask() {
        _ui.update { state ->
            if (!state.canGoNext || state.successPhase != SuccessPhase.Idle) state
            else state.copy(
                index = state.index + 1,
                speakCue = null,
                successPhase = SuccessPhase.Idle,
                successSpeakText = null,
            )
        }
    }

    fun clearSpeakCue() {
        _ui.update { it.copy(speakCue = null) }
    }

    fun onSpeakerFallbackNeeded(): Boolean {
        val task = _ui.value.current?.template ?: return false
        return task.domain == Domain.math && !task.promptSymbols.isNullOrBlank()
    }

    fun currentPromptText(ttsAvailable: Boolean): String {
        val task = _ui.value.current?.template ?: return ""
        return if (!ttsAvailable && !task.promptSymbols.isNullOrBlank()) {
            task.promptSymbols
        } else {
            task.promptTts
        }
    }

    fun successSpeakTextForCurrent(): String {
        val task = _ui.value.current?.template ?: return ""
        return when (task.domain) {
            Domain.math -> (task.answer ?: ((task.left ?: 0) + (task.right ?: 0))).toString()
            Domain.reading, Domain.speech -> {
                val id = task.atomId ?: task.targetAtomId
                pack.atoms[id]?.display
                    ?: task.composeDisplays.joinToString("").ifBlank { task.promptTts }
            }
        }
    }

    fun onSuccessSpeechFinished() {
        _ui.update {
            if (it.successPhase != SuccessPhase.SpeakAnswer) it
            else it.copy(
                successPhase = SuccessPhase.ShowBurst,
                successSpeakText = null,
            )
        }
    }

    fun onSuccessBurstFinished() {
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    successPhase = SuccessPhase.Idle,
                    successSpeakText = null,
                )
            }
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
                // F7: mid-task difficulty changes apply to subsequent tasks only.
                val updated = state.tasks.mapIndexed { i, scheduled ->
                    if (i <= state.index) scheduled else schedule(scheduled.template, progress)
                }
                state.copy(
                    showDifficultySheet = false,
                    tasks = updated,
                )
            }
        }
    }

    /** @return true when the Activity should finish (Back from summary/pause). */
    fun onBackPressed(): Boolean {
        val state = _ui.value
        return when (state.screen) {
            AppScreen.Practice -> {
                if (state.index == 0 && state.sessionPoints == 0) {
                    _ui.update { it.copy(screen = AppScreen.Pause) }
                } else {
                    _ui.update { it.copy(screen = AppScreen.RewardSummary) }
                    viewModelScope.launch { progressRepository.saveSession(null) }
                }
                false
            }
            AppScreen.RewardSummary, AppScreen.Pause -> true
        }
    }

    fun resumeFromPause() {
        _ui.update { it.copy(screen = AppScreen.Practice) }
    }

    fun continueAfterSummary() {
        viewModelScope.launch {
            progressRepository.markPackIntroCompleted()
            startFreshSession()
        }
    }

    fun submitReadingAnswer(correct: Boolean, resolved: Boolean, atomIds: List<String>) {
        viewModelScope.launch {
            if (_ui.value.successPhase != SuccessPhase.Idle) return@launch
            val outcome = outcomeFor(correct = correct, resolved = resolved)
            progress = progressRepository.update { current ->
                var next = current
                atomIds.forEach { id ->
                    next = ProgressionEngine.recordAtomAttempt(next, id, outcome)
                }
                if (correct && !resolved) {
                    next = ProgressionEngine.awardPoints(next, POINTS_PER_CORRECT)
                }
                next
            }
            afterAttempt(correct = correct && !resolved, resolved = resolved, missHint = !correct && !resolved)
        }
    }

    fun submitMathAnswer(distance: Int?, resolved: Boolean, correct: Boolean) {
        viewModelScope.launch {
            if (_ui.value.successPhase != SuccessPhase.Idle) return@launch
            val task = _ui.value.current?.template ?: return@launch
            val key = ProgressionEngine.mathKey(task)
            val outcome = outcomeFor(correct = correct, resolved = resolved)
            progress = progressRepository.update { current ->
                var next = ProgressionEngine.recordMathAttempt(current, key, outcome)
                if (correct && !resolved) {
                    next = ProgressionEngine.awardPoints(next, POINTS_PER_CORRECT)
                }
                next
            }
            // Preschool: speak miss hints; never show error sentences as readable chrome.
            val spoken = when {
                resolved || correct -> null
                else -> MathHinting.missFeedback(distance)
            }
            afterAttempt(
                correct = correct && !resolved,
                resolved = resolved,
                missHint = !correct && !resolved,
                speakOverride = spoken,
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
            val phrase = successSpeakTextForCurrent()
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
            advance()
            return
        }
        if (missHint) {
            _ui.update {
                it.copy(
                    speakCue = speakOverride ?: "Probiere eine andere Antwort",
                    points = progress.points,
                )
            }
        }
    }

    private suspend fun advance() {
        val state = _ui.value
        val nextIndex = state.index + 1
        if (nextIndex >= state.tasks.size) {
            progressRepository.saveSession(null)
            progressRepository.markPackIntroCompleted()
            _ui.update {
                it.copy(
                    screen = AppScreen.RewardSummary,
                    index = state.tasks.lastIndex.coerceAtLeast(0),
                    speakCue = null,
                    successPhase = SuccessPhase.Idle,
                    successSpeakText = null,
                    points = progress.points,
                )
            }
        } else {
            _ui.update {
                it.copy(
                    index = nextIndex,
                    speakCue = null,
                    successPhase = SuccessPhase.Idle,
                    successSpeakText = null,
                    points = progress.points,
                    tasks = it.tasks.mapIndexed { i, task ->
                        if (i < nextIndex) task else schedule(task.template, progress)
                    },
                )
            }
            persistSnapshot()
        }
    }

    private suspend fun persistSnapshot() {
        val state = _ui.value
        if (state.tasks.isEmpty()) return
        progressRepository.saveSession(
            SessionSnapshot(
                taskIds = state.tasks.map { it.template.id },
                index = state.index,
                pointsEarned = state.sessionPoints,
                packId = pack.manifest.packId,
            ),
        )
    }

    fun effectiveMathScaffold(): ScaffoldLevel {
        val task = _ui.value.current?.template ?: return ScaffoldLevel.Beginner
        return ProgressionEngine.scaffoldForMath(progress, ProgressionEngine.mathKey(task))
    }

    fun parentMode(): ParentMode = progress.parentMode

    companion object {
        const val POINTS_PER_CORRECT = 1

        fun factory(
            contentRepository: ContentRepository,
            progressRepository: ProgressRepository,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SessionViewModel(contentRepository, progressRepository) as T
            }
        }
    }
}
