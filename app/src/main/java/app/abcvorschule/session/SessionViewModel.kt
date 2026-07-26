package app.abcvorschule.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.Domain
import app.abcvorschule.content.TaskTemplate
import app.abcvorschule.content.TaskType
import app.abcvorschule.progress.AttemptOutcome
import app.abcvorschule.progress.LearnerProgress
import app.abcvorschule.progress.ParentMode
import app.abcvorschule.progress.ProgressRepository
import app.abcvorschule.progress.ProgressionEngine
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.progress.SessionSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
            pack = contentRepository.load()
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
            speechUnlocked = false,
            packTitle = pack.manifest.title,
        )
        persistSnapshot()
    }

    private fun restore(snapshot: SessionSnapshot) {
        val templates = snapshot.taskIds.mapNotNull { id -> pack.tasks.find { it.id == id } }
        val scheduled = templates.map { schedule(it, progress) }
        _ui.value = SessionUiState(
            screen = AppScreen.Practice,
            tasks = scheduled,
            index = snapshot.index.coerceIn(0, scheduled.lastIndex.coerceAtLeast(0)),
            points = progress.points,
            sessionPoints = snapshot.pointsEarned,
            ready = true,
            speechUnlocked = false,
            packTitle = pack.manifest.title,
        )
    }

    fun contentPack(): ContentPack? = if (this::pack.isInitialized) pack else null

    private fun schedule(template: TaskTemplate, progress: LearnerProgress): ScheduledTask {
        val gapIds = when (template.type) {
            TaskType.sentence_cloze -> template.gapAtomIds
            TaskType.cloze, TaskType.speech_cloze -> template.slots.ifEmpty {
                listOfNotNull(template.targetAtomId ?: template.atomId)
            }
            else -> emptyList()
        }
        val scaffolds = gapIds.associateWith { ProgressionEngine.scaffoldForAtom(progress, it) }
        return ScheduledTask(template, scaffolds)
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

    fun unlockSpeech() {
        _ui.update { it.copy(speechUnlocked = true) }
    }

    fun openDifficultySheet() {
        _ui.update { it.copy(showDifficultySheet = true) }
    }

    fun dismissDifficultySheet() {
        _ui.update { it.copy(showDifficultySheet = false) }
    }

    fun setParentMode(mode: ParentMode) {
        viewModelScope.launch {
            progressRepository.setParentMode(mode)
            progress = progressRepository.current()
            _ui.update { state ->
                state.copy(
                    showDifficultySheet = false,
                    tasks = state.tasks.map { schedule(it.template, progress) },
                )
            }
        }
    }

    fun onBackPressed() {
        val state = _ui.value
        when (state.screen) {
            AppScreen.Practice -> {
                if (state.index == 0 && state.sessionPoints == 0) {
                    _ui.update { it.copy(screen = AppScreen.Pause) }
                } else {
                    _ui.update { it.copy(screen = AppScreen.RewardSummary) }
                    viewModelScope.launch { progressRepository.saveSession(null) }
                }
            }
            AppScreen.RewardSummary, AppScreen.Pause -> {
                // stay; UI handles finish
            }
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

    fun clearFeedback() {
        _ui.update { it.copy(feedback = null, lastSuccess = false) }
    }

    fun submitReadingAnswer(correct: Boolean, resolved: Boolean, atomIds: List<String>) {
        viewModelScope.launch {
            val outcome = when {
                resolved -> AttemptOutcome.Resolve
                correct -> AttemptOutcome.Correct
                else -> AttemptOutcome.Miss
            }
            progressRepository.update { current ->
                var next = current
                atomIds.forEach { id ->
                    next = ProgressionEngine.recordAtomAttempt(next, id, outcome)
                }
                if (correct && !resolved) {
                    next = ProgressionEngine.awardPoints(next, POINTS_PER_CORRECT)
                }
                next
            }
            progress = progressRepository.current()
            afterAttempt(correct = correct && !resolved, resolved = resolved, missHint = !correct && !resolved)
        }
    }

    fun submitMathAnswer(distance: Int?, resolved: Boolean, correct: Boolean) {
        viewModelScope.launch {
            val task = _ui.value.current?.template ?: return@launch
            val key = ProgressionEngine.mathKey(
                operation = task.operation ?: "add",
                left = task.left ?: 0,
                right = task.right ?: 0,
                band = task.difficultyBand,
            )
            val outcome = when {
                resolved -> AttemptOutcome.Resolve
                correct -> AttemptOutcome.Correct
                else -> AttemptOutcome.Miss
            }
            progressRepository.update { current ->
                var next = ProgressionEngine.recordMathAttempt(current, key, outcome)
                if (correct && !resolved) {
                    next = ProgressionEngine.awardPoints(next, POINTS_PER_CORRECT)
                }
                next
            }
            progress = progressRepository.current()
            val feedback = when {
                resolved -> null
                correct -> null
                distance == null -> "Versuch es noch einmal"
                distance in 1..2 -> "Du bist nah dran, denk noch einmal nach"
                else -> "Schau noch einmal genau hin"
            }
            afterAttempt(
                correct = correct && !resolved,
                resolved = resolved,
                missHint = !correct && !resolved,
                feedbackOverride = feedback,
            )
        }
    }

    private suspend fun afterAttempt(
        correct: Boolean,
        resolved: Boolean,
        missHint: Boolean,
        feedbackOverride: String? = null,
    ) {
        if (correct) {
            _ui.update {
                it.copy(
                    points = progress.points,
                    sessionPoints = it.sessionPoints + POINTS_PER_CORRECT,
                    feedback = null,
                )
            }
            advance(showSuccess = true)
            return
        }
        if (resolved) {
            advance(showSuccess = false)
            return
        }
        if (missHint) {
            _ui.update {
                it.copy(
                    feedback = feedbackOverride ?: "Probiere eine andere Antwort",
                    lastSuccess = false,
                    points = progress.points,
                )
            }
        }
    }

    private suspend fun advance(showSuccess: Boolean) {
        val state = _ui.value
        val nextIndex = state.index + 1
        if (nextIndex >= state.tasks.size) {
            progressRepository.saveSession(null)
            progressRepository.markPackIntroCompleted()
            _ui.update {
                it.copy(
                    screen = AppScreen.RewardSummary,
                    index = state.tasks.lastIndex.coerceAtLeast(0),
                    speechUnlocked = false,
                    feedback = null,
                    lastSuccess = showSuccess,
                    points = progress.points,
                )
            }
        } else {
            _ui.update {
                it.copy(
                    index = nextIndex,
                    speechUnlocked = false,
                    feedback = null,
                    lastSuccess = showSuccess,
                    points = progress.points,
                    tasks = it.tasks.map { task -> schedule(task.template, progress) },
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
        val key = ProgressionEngine.mathKey(
            operation = task.operation ?: "add",
            left = task.left ?: 0,
            right = task.right ?: 0,
            band = task.difficultyBand,
        )
        return ProgressionEngine.scaffoldForMath(progress, key)
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
