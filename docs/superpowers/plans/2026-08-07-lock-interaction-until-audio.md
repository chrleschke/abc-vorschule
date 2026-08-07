# Interaktion sperren, bis die Aufgabenansage vorgelesen wurde — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sperre die tippbaren Elemente in 7 der 8 Trainer, bis die Aufgabenansage (ganz oder bis zu ihrem "Kernteil") vorgelesen wurde, damit ein früher Tap die Ansage nicht mehr abbricht — sichtbar über eine sanft animierte 50%-Deckkraft statt eines Lock-Icons.

**Architecture:** Ein neuer `interactionLocked`-State lebt in `TaskShell.kt`, wird pro Runde zurückgesetzt und über `TrainerHost`/`TrainerCallbacks` an die 7 betroffenen Trainer durchgereicht. Ein neuer `onPartComplete`-Callback in `SpeechController.speakAndAwaitSequence` meldet, welcher Ansage-Teil gerade fertig gesprochen wurde; ein neues pures `PromptUnlock`-Objekt bestimmt pro Rundentyp, ab welchem Teil-Index entsperrt wird (Standard: letzter Teil: Ausnahme Wort-Detektiv: Teil-Index 1). Für den Sonderfall Wort-Detektiv bekommt `SpeechController` einen zweiten, unabhängigen `ClipPlayer`-Kanal (`SpeechChannel.Feedback`), damit das Tap-Echo eines Segments die noch laufende Ansage (Konnektor + Wort) nicht abwürgt.

**Tech Stack:** Kotlin, Jetpack Compose, JUnit4 (JVM-Unit-Tests unter `app/src/test`).

## Global Constraints

- Nur diese 7 Trainer bekommen die Sperre: `SymbolInWordTrainer`, `SymbolHuntTrainer`, `SentenceOrderTrainer`, `SoundPositionTrainer`, `SyllableMergeTrainer`, `WordBuildTrainer`, `MathExercise`. `LetterTraceTrainer` bleibt unangetastet.
- Deckkraft der gesperrten interaktiven Elemente: `0.5f`, animiert über `tween(durationMillis = 200)`. Nichts anderes (kein Icon, kein Overlay).
- Der Lautsprecher-Button (`TaskPromptChrome`/`AbcSpeakerButton`) bleibt immer aktiv — nie Teil der Sperre.
- Tippen während der Sperre wird komplett ignoriert (`enabled = false` reicht, kein zusätzliches Feedback).
- Freigabe-Index: Standard = letzter Teil der Ansage-Liste; Ausnahme `SymbolInWordRound` = Index 1 (Ziel-Graphem/-Phonem).
- Bestehende lokale "schon beantwortet"-Sperren (z.B. `locked` in `MathExercise`) bleiben ein eigenständiges Konzept — nur zusätzlich mit der neuen Sperre UND-verknüpft, nicht umbenannt oder zusammengelegt.
- Jede Task muss für sich kompilieren; wo eine Task Parameter einführt, die erst eine spätere Task konsumiert (z.B. `TrainerHost`s `interactionLocked`-Parameter), ist das ausdrücklich vorgesehen (Kotlin lässt unbenutzte Funktionsparameter zu, kein Compile-Fehler).

---

### Task 1: `SpeechController` — Kanäle + `onPartComplete`

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/speech/SpeechController.kt`

**Interfaces:**
- Produces: `enum class SpeechChannel { Primary, Feedback }`; `fun speak(text: String, channel: SpeechChannel = SpeechChannel.Primary)`; `suspend fun speakAndAwait(text: String, channel: SpeechChannel = SpeechChannel.Primary, timeoutMs: Long = 10_000L)`; `suspend fun speakAndAwaitSequence(texts: List<String>, timeoutMs: Long = 10_000L, onPartComplete: ((index: Int) -> Unit)? = null)`.
- Consumes: nichts Neues — `ClipPlayer` (unverändert, siehe `app/src/main/java/app/abcvorschule/speech/ClipPlayer.kt`).

Kein Unit-Test möglich/nötig: `SpeechController` kapselt echte `MediaPlayer`/`TextToSpeech`-Instanzen und hat schon heute keine Testdatei (JVM-Tests im Projekt decken nur reine Kotlin-Logik ab, z.B. `ClipIndexTest.kt` für `ClipIndex`, nie `SpeechController`/`ClipPlayer` selbst). Verifikation erfolgt manuell auf dem Gerät in Task 12/13.

- [ ] **Step 1: Enum + zweiter ClipPlayer**

In `SpeechController.kt`, direkt vor der Klasse:

```kotlin
/** Primary trägt die Rundenansage; Feedback trägt Tap-Echos, die die Ansage nicht
 * abwürgen dürfen (Wort-Detektiv, siehe design doc). Getrennte ClipPlayer-Instanzen,
 * damit ein Aufruf auf dem einen Kanal den anderen nicht flusht — die TTS-Engine
 * bleibt geteilt, siehe design doc "Nicht im Scope". */
enum class SpeechChannel { Primary, Feedback }
```

Ersetze das Feld `private val clipPlayer = ClipPlayer(appContext)` durch:

```kotlin
private val clipPlayers: Map<SpeechChannel, ClipPlayer> = mapOf(
    SpeechChannel.Primary to ClipPlayer(appContext),
    SpeechChannel.Feedback to ClipPlayer(appContext),
)
```

- [ ] **Step 2: `speak`/`speakAndAwait` bekommen einen `channel`-Parameter**

Ersetze:

```kotlin
fun speak(text: String) {
    if (text.isBlank() || blockedForBackground) return
    clearWaiters()
    stopOutput()
    if (playClip(text, onComplete = {})) return
    val engine = tts ?: return
    if (!_available.value) return
    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
}
```

durch:

```kotlin
fun speak(text: String, channel: SpeechChannel = SpeechChannel.Primary) {
    if (text.isBlank() || blockedForBackground) return
    clearWaiters()
    stopOutput(channel)
    if (playClip(text, channel, onComplete = {})) return
    val engine = tts ?: return
    if (!_available.value) return
    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
}
```

Ersetze:

```kotlin
suspend fun speakAndAwait(text: String, timeoutMs: Long = 10_000L) {
    if (text.isBlank() || blockedForBackground) return
    clearWaiters()
    stopOutput()
    val deferred = CompletableDeferred<Unit>()
    if (playClip(text, onComplete = { deferred.complete(Unit) })) {
        withTimeoutOrNull(timeoutMs) { deferred.await() }
        return
    }
    val engine = tts ?: return
    if (!_available.value) return
    val id = UUID.randomUUID().toString()
    utteranceWaiters[id] = deferred
    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    withTimeoutOrNull(timeoutMs) { deferred.await() }
    utteranceWaiters.remove(id)
}
```

durch:

```kotlin
suspend fun speakAndAwait(
    text: String,
    channel: SpeechChannel = SpeechChannel.Primary,
    timeoutMs: Long = 10_000L,
) {
    if (text.isBlank() || blockedForBackground) return
    clearWaiters()
    stopOutput(channel)
    val deferred = CompletableDeferred<Unit>()
    if (playClip(text, channel, onComplete = { deferred.complete(Unit) })) {
        withTimeoutOrNull(timeoutMs) { deferred.await() }
        return
    }
    val engine = tts ?: return
    if (!_available.value) return
    val id = UUID.randomUUID().toString()
    utteranceWaiters[id] = deferred
    engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, id)
    withTimeoutOrNull(timeoutMs) { deferred.await() }
    utteranceWaiters.remove(id)
}
```

- [ ] **Step 3: `speakAndAwaitSequence` bekommt `onPartComplete`**

Ersetze:

```kotlin
/** Speaks each part in order — prompt clip then atom clip, etc. */
suspend fun speakAndAwaitSequence(texts: List<String>, timeoutMs: Long = 10_000L) {
    texts.filter { it.isNotBlank() }.forEach { speakAndAwait(it, timeoutMs) }
}
```

durch:

```kotlin
/**
 * Speaks each part in order — prompt clip then atom clip, etc. [onPartComplete]
 * fires with each part's ORIGINAL index in [texts] (blank parts are skipped but
 * don't shift later indices) right after that part finishes — callers use this to
 * unlock interaction before the whole sequence is done (design doc: Wort-Detektiv).
 */
suspend fun speakAndAwaitSequence(
    texts: List<String>,
    timeoutMs: Long = 10_000L,
    onPartComplete: ((index: Int) -> Unit)? = null,
) {
    texts.withIndex().filter { it.value.isNotBlank() }.forEach { (index, text) ->
        speakAndAwait(text, timeoutMs = timeoutMs)
        onPartComplete?.invoke(index)
    }
}
```

- [ ] **Step 4: `stop`/`stopOutput`/`playClip` werden kanalbewusst**

Ersetze:

```kotlin
fun stop() {
    stopOutput()
    clearWaiters()
}

fun shutdown() {
    stopOutput()
    tts?.shutdown()
    tts = null
    _available.value = false
    clearWaiters()
}

/** Clip gefunden und gestartet? Setzt `speaking` passend. */
private fun playClip(text: String, onComplete: () -> Unit): Boolean {
    val entry = clips.lookup(text) ?: return false
    val started = clipPlayer.play(entry.file) {
        _speaking.value = false
        onComplete()
    }
    if (started) _speaking.value = true
    return started
}

/** Beendet beide Ausgabewege — die Flush-Semantik jedes speak-Aufrufs. */
private fun stopOutput() {
    clipPlayer.stop()
    tts?.stop()
    _speaking.value = false
}
```

durch:

```kotlin
fun stop() {
    SpeechChannel.entries.forEach { stopOutput(it) }
    clearWaiters()
}

fun shutdown() {
    SpeechChannel.entries.forEach { stopOutput(it) }
    tts?.shutdown()
    tts = null
    _available.value = false
    clearWaiters()
}

/** Clip gefunden und gestartet? `speaking` bildet nur den Primary-Kanal ab — die
 * Rundenansage, nicht ein gleichzeitig laufendes Feedback-Echo (design doc). */
private fun playClip(text: String, channel: SpeechChannel, onComplete: () -> Unit): Boolean {
    val entry = clips.lookup(text) ?: return false
    val started = clipPlayers.getValue(channel).play(entry.file) {
        if (channel == SpeechChannel.Primary) _speaking.value = false
        onComplete()
    }
    if (started && channel == SpeechChannel.Primary) _speaking.value = true
    return started
}

/**
 * Beendet den Ausgabeweg des gegebenen Kanals. Die TTS-Engine bleibt geteilt und
 * wird nur gestoppt, wenn der Primary-Kanal gestoppt wird — ein Feedback-Stop darf
 * eine noch laufende Primary-Ansage nicht abwürgen (design doc).
 */
private fun stopOutput(channel: SpeechChannel) {
    clipPlayers.getValue(channel).stop()
    if (channel == SpeechChannel.Primary) {
        tts?.stop()
        _speaking.value = false
    }
}
```

- [ ] **Step 5: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. Wenn nicht: Fehlermeldung lesen — vermutlich ein Aufrufer, der `stopOutput()` ohne Argument nutzt (`onBackground()` ruft nur `stop()` auf, sollte unverändert kompilieren).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/speech/SpeechController.kt
git commit -m "feat(speech): add SpeechChannel + onPartComplete to SpeechController

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 2: `PromptUnlock` — pure Freigabe-Index-Logik

**Files:**
- Create: `app/src/main/java/app/abcvorschule/content/PromptUnlock.kt`
- Create: `app/src/test/java/app/abcvorschule/content/PromptUnlockTest.kt`
- Modify: `app/src/main/java/app/abcvorschule/session/SessionViewModel.kt:307-321` (nach `currentPromptParts()`)

**Interfaces:**
- Produces: `object PromptUnlock { fun unlockIndex(round: TrainerRound, parts: List<String>): Int }`; `SessionViewModel.currentPromptUnlockIndex(): Int`.
- Consumes: `TrainerRound`, `SymbolInWordRound` (aus `app.abcvorschule.content`, siehe `TaskSpecs.kt`).

- [ ] **Step 1: Failing test schreiben**

`app/src/test/java/app/abcvorschule/content/PromptUnlockTest.kt`:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Test

class PromptUnlockTest {

    @Test
    fun symbolInWordUnlocksAfterTargetPart_evenWithTrailingParts() {
        val round = SymbolInWordRound(
            promptTts = "Finde den Buchstaben",
            wordAtomId = "word-mama",
            targetAtomId = "letter-m",
            mode = SymbolInWordMode.letter,
            segments = listOf("M", "a", "m", "a"),
            targetIndices = listOf(0, 2),
        )
        val parts = listOf("Finde den Buchstaben", "M", "...im Wort...", "Mama")
        assertEquals(1, PromptUnlock.unlockIndex(round, parts))
    }

    @Test
    fun symbolInWordUnlocksAtLastPart_whenShorterThanFour() {
        val round = SymbolInWordRound(
            promptTts = "Finde den Buchstaben",
            wordAtomId = "word-a",
            targetAtomId = "letter-a",
            mode = SymbolInWordMode.letter,
            segments = listOf("A"),
            targetIndices = listOf(0),
        )
        val parts = listOf("Finde den Buchstaben", "A")
        assertEquals(1, PromptUnlock.unlockIndex(round, parts))
    }

    @Test
    fun symbolHuntUnlocksAtLastPart() {
        val round = SymbolHuntRound(
            promptTts = "Finde alle Buchstaben",
            targetAtomId = "letter-a",
            mode = SymbolHuntMode.letter,
            distractorPool = listOf("letter-m"),
        )
        val parts = listOf("Finde alle Buchstaben", "A")
        assertEquals(1, PromptUnlock.unlockIndex(round, parts))
    }

    @Test
    fun singlePartRoundUnlocksAtItsOnlyPart() {
        val round = WordBuildRound(
            promptTts = "Baue das Wort Mama.",
            targetAtomId = "word-mama",
            blocks = listOf(WordBlock(atomId = "letter-m", display = "M")),
        )
        val parts = listOf("Baue das Wort Mama.")
        assertEquals(0, PromptUnlock.unlockIndex(round, parts))
    }

    @Test
    fun emptyPartsUnlockAtZero() {
        val round = WordBuildRound(
            promptTts = "",
            targetAtomId = "word-mama",
            blocks = emptyList(),
        )
        assertEquals(0, PromptUnlock.unlockIndex(round, emptyList()))
    }
}
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen (Klasse existiert nicht)**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.content.PromptUnlockTest"`
Expected: FAIL — `unresolved reference: PromptUnlock`.

- [ ] **Step 3: `PromptUnlock` implementieren**

`app/src/main/java/app/abcvorschule/content/PromptUnlock.kt`:

```kotlin
package app.abcvorschule.content

/**
 * Welcher Index von [round]s gesprochenen Prompt-Teilen (siehe
 * SessionViewModel.currentPromptParts) die Interaktion freigibt — nachfolgende
 * Teile laufen unabhängig davon zu Ende weiter.
 *
 * Wort-Detektiv ist die einzige Runde, deren Ansage nach dem eigentlichen Inhalt
 * (Ziel-Buchstabe/-Laut, Index 1) noch weiterläuft: Konnektor + Wort folgen. Bei
 * jeder anderen Runde IST die ganze Ansage der eigentliche Inhalt, ihr letzter Teil
 * ist also zugleich ihr Freigabe-Punkt.
 */
object PromptUnlock {
    fun unlockIndex(round: TrainerRound, parts: List<String>): Int {
        if (parts.isEmpty()) return 0
        return when (round) {
            is SymbolInWordRound -> 1.coerceAtMost(parts.lastIndex)
            else -> parts.lastIndex
        }
    }
}
```

- [ ] **Step 4: Test laufen lassen — muss bestehen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.content.PromptUnlockTest"`
Expected: PASS (5 Tests).

- [ ] **Step 5: `SessionViewModel.currentPromptUnlockIndex()` hinzufügen**

In `SessionViewModel.kt`, direkt nach der bestehenden `currentPromptParts()`-Funktion (Zeile 307-321):

```kotlin
/** Freigabe-Index für die aktuelle Runde — siehe [PromptUnlock]. */
fun currentPromptUnlockIndex(): Int {
    val round = _ui.value.currentRound ?: return 0
    return PromptUnlock.unlockIndex(round, currentPromptParts())
}
```

- [ ] **Step 6: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/PromptUnlock.kt \
        app/src/test/java/app/abcvorschule/content/PromptUnlockTest.kt \
        app/src/main/java/app/abcvorschule/session/SessionViewModel.kt
git commit -m "feat(content): add PromptUnlock — per-round unlock index for the intro sequence

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 3: `TaskShell` — `interactionLocked`-State + Verdrahtung

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/ExerciseStage.kt:23-29` (`TrainerCallbacks`)
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt:33-43` (Signatur, `when`-Branches folgen in späteren Tasks)
- Modify: `app/src/main/java/app/abcvorschule/MainActivity.kt`

**Interfaces:**
- Consumes: `SessionViewModel.currentPromptUnlockIndex()` (Task 2), `SpeechController.speakAndAwaitSequence(texts, timeoutMs, onPartComplete)` und `SpeechController.speak(text, channel)` (Task 1).
- Produces: `TrainerCallbacks.onSpeakFeedback: (String) -> Unit`; `TrainerHost(..., interactionLocked: Boolean = false, ...)`. Beide werden erst ab Task 4/12 tatsächlich konsumiert — das ist hier bewusst so (siehe Global Constraints).

- [ ] **Step 1: `TrainerCallbacks` um `onSpeakFeedback` erweitern**

In `ExerciseStage.kt`, ersetze:

```kotlin
data class TrainerCallbacks(
    val onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    val onMathResult: (distance: Int?, resolved: Boolean, correct: Boolean) -> Unit,
    val onSpeak: (String) -> Unit,
    val onSpeakAndAwait: suspend (String) -> Unit,
    val onSpeakPrompt: () -> Unit,
)
```

durch:

```kotlin
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
```

- [ ] **Step 2: `TrainerHost` bekommt den `interactionLocked`-Parameter**

In `TrainerHost.kt`, ersetze die Funktionssignatur:

```kotlin
@Composable
fun TrainerHost(
    trainer: ScheduledTrainer,
    round: TrainerRound,
    roundIndex: Int,
    pack: ContentPack,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    callbacks: TrainerCallbacks,
    modifier: Modifier = Modifier,
) {
```

durch:

```kotlin
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
```

(Kein `when`-Branch wird in diesem Task geändert — das passiert trainer-weise in Task 6–12.)

- [ ] **Step 3: `TaskShell`/`PracticeBody` — neue Parameter + State**

In `TaskShell.kt`, ersetze die `TaskShell`-Signatur:

```kotlin
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
```

durch:

```kotlin
@Composable
fun TaskShell(
    state: SessionUiState,
    pack: ContentPack?,
    viewModel: SessionViewModel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onSpeakFeedback: (String) -> Unit,
    onSpeakAndAwait: suspend (String) -> Unit,
    onSpeakPromptSequence: suspend (List<String>) -> Unit,
    onSpeakIntroSequence: suspend (List<String>, onPartComplete: (Int) -> Unit) -> Unit,
    onStopSpeak: () -> Unit,
    onOpenTtsDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

Im `else -> PracticeBody(...)`-Aufruf innerhalb `TaskShell`, ersetze:

```kotlin
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
```

durch:

```kotlin
            else -> PracticeBody(
                state = state,
                pack = pack,
                viewModel = viewModel,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeak = onSpeak,
                onSpeakFeedback = onSpeakFeedback,
                onSpeakAndAwait = onSpeakAndAwait,
                onSpeakPromptSequence = onSpeakPromptSequence,
                onSpeakIntroSequence = onSpeakIntroSequence,
                onStopSpeak = onStopSpeak,
            )
```

- [ ] **Step 4: `PracticeBody` — Signatur, State, Effekt**

`TaskShell.kt` importiert bisher kein `remember`/`mutableStateOf`/`getValue`/`setValue` (nur `Composable`, `LaunchedEffect`, `rememberCoroutineScope`). Füge oben hinzu:

```kotlin
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
```

Ersetze die `PracticeBody`-Signatur:

```kotlin
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
```

durch:

```kotlin
@Composable
private fun PracticeBody(
    state: SessionUiState,
    pack: ContentPack,
    viewModel: SessionViewModel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onSpeakFeedback: (String) -> Unit,
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
```

- [ ] **Step 5: `TrainerHost`-Aufruf in `PracticeBody` erweitern**

Ersetze:

```kotlin
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
                        onSpeakAndAwait = onSpeakAndAwait,
                        onSpeakPrompt = speakPrompt,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
```

durch:

```kotlin
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
                        onSpeakAndAwait = onSpeakAndAwait,
                        onSpeakPrompt = speakPrompt,
                    ),
                    modifier = Modifier.fillMaxSize(),
                )
```

- [ ] **Step 6: `MainActivity.kt`/`AbcApp` verdrahten**

Ersetze in `AbcApp`:

```kotlin
        TaskShell(
            state = state,
            pack = pack,
            viewModel = viewModel,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeak = speech::speak,
            onSpeakAndAwait = speech::speakAndAwait,
            onSpeakPromptSequence = speech::speakAndAwaitSequence,
            onStopSpeak = speech::stop,
            onOpenTtsDebug = { showTtsDebug = true },
        )
```

durch:

```kotlin
        TaskShell(
            state = state,
            pack = pack,
            viewModel = viewModel,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeak = speech::speak,
            onSpeakFeedback = { text -> speech.speak(text, channel = SpeechChannel.Feedback) },
            onSpeakAndAwait = speech::speakAndAwait,
            onSpeakPromptSequence = speech::speakAndAwaitSequence,
            onSpeakIntroSequence = { texts, onPartComplete ->
                speech.speakAndAwaitSequence(texts, onPartComplete = onPartComplete)
            },
            onStopSpeak = speech::stop,
            onOpenTtsDebug = { showTtsDebug = true },
        )
```

Füge den Import hinzu (neben dem bestehenden `import app.abcvorschule.speech.SpeechController`):

```kotlin
import app.abcvorschule.speech.SpeechChannel
```

- [ ] **Step 7: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Manuell verifizieren — keine Verhaltensänderung sichtbar**

App starten, eine beliebige Übung öffnen. Da noch kein Trainer `interactionLocked` liest, muss sich alles exakt wie vor diesem Task verhalten (keine sichtbare Sperre, keine Abdunklung). Das ist der Regressions-Check für diesen Task.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt \
        app/src/main/java/app/abcvorschule/ui/exercise/ExerciseStage.kt \
        app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt \
        app/src/main/java/app/abcvorschule/MainActivity.kt
git commit -m "feat(shell): wire interactionLocked state and onSpeakIntroSequence

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 4: Drag-Grundbausteine — `enabled` in `DragCard`/`DropZone`

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/drag/DragField.kt`

**Interfaces:**
- Produces: `DragCard(..., enabled: Boolean = true, content: ...)`; `DropZone(..., enabled: Boolean = true, content: ...)`. Beide Defaults sind `true`, also ändert sich für alle 3 bestehenden Aufrufer (SentenceOrder/SoundPosition/WordBuild) an dieser Stelle noch nichts — sie werden trainer-weise in Task 7/8/10 auf `!interactionLocked` gesetzt.

`DragFieldStateTest.kt` prüft nur die reine `DragFieldState`-Klasse (unverändert) — kein neuer Test nötig, diese Änderung ist reines Compose-UI-Verhalten ohne bestehende Testabdeckung dafür (Konvention im Projekt: Composables selbst werden nicht unit-getestet).

- [ ] **Step 1: `DragCard` — `enabled` gate für Klick + Drag**

Ersetze:

```kotlin
@Composable
fun DragCard(
    state: DragFieldState,
    key: String,
    onTap: () -> Unit,
    onDropped: (zoneKey: String?) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val dragging = state.draggingKey == key
    DisposableEffect(key) {
        onDispose { state.removeCard(key) }
    }
    Box(
        // zIndex/offset/scale sit BEFORE the caller's modifier on purpose: a later
        // `offset` would only move the content, leaving the caller's background and
        // border painted at the tile's resting position — which made the dragged
        // tile look like bare (near-black) text floating over the board.
        modifier = Modifier
            .zIndex(if (dragging) 1f else 0f)
            .offset {
                val o = if (dragging) state.dragOffset else Offset.Zero
                IntOffset(o.x.roundToInt(), o.y.roundToInt())
            }
            .graphicsLayer {
                val scale = if (dragging) DragLiftScale else 1f
                scaleX = scale
                scaleY = scale
            }
            .then(modifier)
            .onGloballyPositioned { state.putCard(key, it.boundsInRoot()) }
            .pointerInput(key) {
                detectDragGestures(
                    onDragStart = { state.startDrag(key) },
                    onDrag = { change, amount ->
                        change.consume()
                        state.drag(amount)
                    },
                    onDragEnd = { onDropped(state.endDrag(key)) },
                    onDragCancel = { onDropped(state.endDrag(key)) },
                )
            }
            .clickable { onTap() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
```

durch:

```kotlin
@Composable
fun DragCard(
    state: DragFieldState,
    key: String,
    onTap: () -> Unit,
    onDropped: (zoneKey: String?) -> Unit,
    modifier: Modifier = Modifier,
    /** False während der Aufgaben-Sperre — weder Tap noch Drag lösen dann etwas
     * aus (design doc). */
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val dragging = state.draggingKey == key
    DisposableEffect(key) {
        onDispose { state.removeCard(key) }
    }
    Box(
        modifier = Modifier
            .zIndex(if (dragging) 1f else 0f)
            .offset {
                val o = if (dragging) state.dragOffset else Offset.Zero
                IntOffset(o.x.roundToInt(), o.y.roundToInt())
            }
            .graphicsLayer {
                val scale = if (dragging) DragLiftScale else 1f
                scaleX = scale
                scaleY = scale
            }
            .then(modifier)
            .onGloballyPositioned { state.putCard(key, it.boundsInRoot()) }
            .then(
                if (enabled) {
                    Modifier.pointerInput(key) {
                        detectDragGestures(
                            onDragStart = { state.startDrag(key) },
                            onDrag = { change, amount ->
                                change.consume()
                                state.drag(amount)
                            },
                            onDragEnd = { onDropped(state.endDrag(key)) },
                            onDragCancel = { onDropped(state.endDrag(key)) },
                        )
                    }
                } else {
                    Modifier
                },
            )
            .clickable(enabled = enabled) { onTap() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
```

- [ ] **Step 2: `DropZone` — `enabled` gate für Klick**

Ersetze:

```kotlin
/** A drop target. Tapping it places the currently selected tile. */
@Composable
fun DropZone(
    state: DragFieldState,
    key: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    DisposableEffect(key) {
        onDispose { state.removeZone(key) }
    }
    Box(
        modifier = Modifier
            .onGloballyPositioned { state.putZone(key, it.boundsInRoot()) }
            .clickable { onTap() }
            .then(modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
```

durch:

```kotlin
/** A drop target. Tapping it places the currently selected tile. */
@Composable
fun DropZone(
    state: DragFieldState,
    key: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    DisposableEffect(key) {
        onDispose { state.removeZone(key) }
    }
    Box(
        modifier = Modifier
            .onGloballyPositioned { state.putZone(key, it.boundsInRoot()) }
            .clickable(enabled = enabled) { onTap() }
            .then(modifier),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
```

- [ ] **Step 3: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/drag/DragField.kt
git commit -m "feat(drag): add enabled gate to DragCard and DropZone

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 5: `NumberPad` + `VisualQuantityBoard` — Sperr-Fähigkeit

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/NumberPad.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/VisualQuantityBoard.kt`

**Interfaces:**
- Produces: `NumberPad(..., enabled: Boolean = true)`; `VisualQuantityBoard(..., interactionLocked: Boolean = false)`.
- Consumes: nichts Neues.

- [ ] **Step 1: `NumberPad` — `enabled` + eigene Opazitäts-Animation**

Ersetze die komplette Datei `NumberPad.kt` mit folgendem Inhalt (nur die markierten Stellen ändern sich: neue Imports, neuer Parameter, deferred focus, `.alpha`):

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.components.IconChevronRight
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.LeafGreen
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk

/**
 * Numeric answer field backed by the device's own keyboard (number mode) —
 * more reliable for kids than a custom on-screen keypad, and it just works
 * with whatever input method/accessibility tooling is installed.
 */
@Composable
fun NumberPad(
    onSubmit: (Int) -> Unit,
    /** Changing this clears the field — a new round, or another wrong try. */
    resetToken: String,
    modifier: Modifier = Modifier,
    /** True once the typed number turned out to be the answer — the field confirms in green. */
    solved: Boolean = false,
    /** False during the audio lock — field and submit button are non-interactive
     * and dimmed, and focus/keyboard are deferred until this turns true. */
    enabled: Boolean = true,
) {
    var value by remember(resetToken) { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val opacity by animateFloatAsState(
        targetValue = if (enabled) 1f else 0.5f,
        animationSpec = tween(durationMillis = 200),
        label = "number_pad_lock_opacity",
    )

    fun submit() {
        value.toIntOrNull()?.let(onSubmit)
    }

    LaunchedEffect(enabled) {
        // Deferred rather than Unit-keyed: while locked the keyboard must not pop
        // up before the child is allowed to type (design doc).
        if (enabled) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    LaunchedEffect(solved) {
        // Without closing the IME the green confirmation sits behind the keyboard —
        // exactly the thing it is supposed to show.
        if (solved) keyboardController?.hide()
    }

    Row(
        modifier = modifier.fillMaxWidth().alpha(opacity),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = { input -> value = NumberPadInput.sanitize(input) },
            modifier = Modifier
                .width(140.dp)
                .focusRequester(focusRequester)
                .testTag("number_input"),
            textStyle = MaterialTheme.typography.displayLarge.copy(textAlign = TextAlign.Center),
            singleLine = true,
            enabled = enabled,
            readOnly = solved,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            // Neutral while typing so that green means one thing only: correct.
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = if (solved) LeafGreen else SkyBlue,
                unfocusedBorderColor = if (solved) LeafGreen else SkyBlue.copy(alpha = 0.5f),
                focusedTextColor = WarmInk,
                unfocusedTextColor = WarmInk,
            ),
        )
        Spacer(Modifier.width(16.dp))
        Surface(
            onClick = { submit() },
            enabled = enabled,
            shape = RoundedCornerShape(20.dp),
            color = SunCoral,
            modifier = Modifier
                .size(AbcDimens.kidTouch - 8.dp)
                .testTag("number_submit"),
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                IconChevronRight(tint = Cream, size = 28.dp)
            }
        }
    }
}
```

- [ ] **Step 2: `VisualQuantityBoard` — `interactionLocked` + Opazität**

Ersetze die Funktionssignatur und den `answers`-Block:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualQuantityBoard(
    emoji: String,
    left: Int,
    right: Int,
    operation: MathOperation,
    choices: List<Int>,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The chosen value once it turned out to be correct — that tile turns green. */
    solved: Int? = null,
    missCount: Int = 0,
    locked: Boolean = false,
    onResolve: (() -> Unit)? = null,
    ttsAvailable: Boolean = false,
    speaking: Boolean = false,
    onSpeakPrompt: () -> Unit = {},
) {
    ExerciseStage(
        modifier = modifier,
        prompt = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MathQuantityPrompt(emoji, left, right, operation, emojiSizeSp = 44)
            }
        },
        answers = {
            // Solutions must match the prompt's representation: once either operand is
            // symbolic, every answer tile shows a single icon too, never a mix of
            // "one icon" and "nine icons" for the same round.
            val forceSymbolic = QuantityRepresentation.forceSymbolicFor(left, right)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                choices.forEach { value ->
                    // The picked tile confirms itself in green, so the child sees *which*
                    // answer was right while it is being spoken. A wrong pick is never
                    // marked red — misses stay spoken-only feedback.
                    val correct = solved == value
                    Column(
                        modifier = Modifier
                            .background(
                                color = if (correct) LeafGreen else CreamElevated,
                                shape = RoundedCornerShape(18.dp),
                            )
                            .clickable { onChoose(value) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("math_choice_$value"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QuantityCluster(
                            emoji = emoji,
                            count = value,
                            emojiSizeSp = 28,
                            showNumber = true,
                            numberColor = if (correct) Cream else WarmInk,
                            forceSymbolic = forceSymbolic,
                        )
                    }
                }
            }
            if (missCount >= 2 && onResolve != null && !locked) {
                AbcResolveButton(onClick = onResolve)
            }
        },
    )
}
```

durch:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualQuantityBoard(
    emoji: String,
    left: Int,
    right: Int,
    operation: MathOperation,
    choices: List<Int>,
    onChoose: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /** The chosen value once it turned out to be correct — that tile turns green. */
    solved: Int? = null,
    missCount: Int = 0,
    locked: Boolean = false,
    /** False during the audio lock — separate from [locked] ("already answered"):
     * this one gates the initial listen-first window (design doc). */
    interactionLocked: Boolean = false,
    onResolve: (() -> Unit)? = null,
    ttsAvailable: Boolean = false,
    speaking: Boolean = false,
    onSpeakPrompt: () -> Unit = {},
) {
    val answerOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "math_choice_lock_opacity",
    )
    ExerciseStage(
        modifier = modifier,
        prompt = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MathQuantityPrompt(emoji, left, right, operation, emojiSizeSp = 44)
            }
        },
        answers = {
            val forceSymbolic = QuantityRepresentation.forceSymbolicFor(left, right)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.alpha(answerOpacity),
            ) {
                choices.forEach { value ->
                    val correct = solved == value
                    Column(
                        modifier = Modifier
                            .background(
                                color = if (correct) LeafGreen else CreamElevated,
                                shape = RoundedCornerShape(18.dp),
                            )
                            .clickable(enabled = !interactionLocked) { onChoose(value) }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("math_choice_$value"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        QuantityCluster(
                            emoji = emoji,
                            count = value,
                            emojiSizeSp = 28,
                            showNumber = true,
                            numberColor = if (correct) Cream else WarmInk,
                            forceSymbolic = forceSymbolic,
                        )
                    }
                }
            }
            if (missCount >= 2 && onResolve != null && !locked) {
                AbcResolveButton(onClick = onResolve)
            }
        },
    )
}
```

Füge die zwei fehlenden Imports oben in `VisualQuantityBoard.kt` hinzu (neben den bestehenden `androidx.compose.foundation.*`-Imports):

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
```

- [ ] **Step 3: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/NumberPad.kt \
        app/src/main/java/app/abcvorschule/ui/exercise/VisualQuantityBoard.kt
git commit -m "feat(math): add lock-aware enabled/opacity to NumberPad and VisualQuantityBoard

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 6: `MathExercise` verdrahten

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt:110-124` (`CountAddRound`-Branch)

**Interfaces:**
- Consumes: `NumberPad(enabled)`, `VisualQuantityBoard(interactionLocked)` (Task 5); `TrainerHost`s `interactionLocked`-Parameter (Task 3).

- [ ] **Step 1: `MathExercise` bekommt den Parameter und reicht ihn durch**

Ersetze die Funktionssignatur:

```kotlin
@Composable
fun MathExercise(
    trainer: ScheduledTrainer,
    round: CountAddRound,
    roundIndex: Int,
    icon: String,
    scaffold: ScaffoldLevel,
    showSymbolPrompt: Boolean,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (distance: Int?, resolved: Boolean, correct: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
```

durch:

```kotlin
@Composable
fun MathExercise(
    trainer: ScheduledTrainer,
    round: CountAddRound,
    roundIndex: Int,
    icon: String,
    scaffold: ScaffoldLevel,
    showSymbolPrompt: Boolean,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (distance: Int?, resolved: Boolean, correct: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
```

- [ ] **Step 2: `NumberPad`/`VisualQuantityBoard`-Aufrufe erweitern**

Ersetze:

```kotlin
                NumberPad(
                    onSubmit = { handleGuess(it) },
                    resetToken = NumberPadInput.resetToken(roundKey, misses),
                    solved = solved != null,
                )
```

durch:

```kotlin
                NumberPad(
                    onSubmit = { handleGuess(it) },
                    resetToken = NumberPadInput.resetToken(roundKey, misses),
                    solved = solved != null,
                    enabled = !interactionLocked,
                )
```

Ersetze:

```kotlin
        VisualQuantityBoard(
            emoji = icon,
            left = round.left,
            right = round.right,
            operation = operation,
            choices = choices,
            onChoose = { handleGuess(it) },
            solved = solved,
            missCount = misses,
            locked = locked,
            onResolve = ::resolve,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = onSpeakPrompt,
            modifier = modifier.fillMaxSize(),
        )
```

durch:

```kotlin
        VisualQuantityBoard(
            emoji = icon,
            left = round.left,
            right = round.right,
            operation = operation,
            choices = choices,
            onChoose = { handleGuess(it) },
            solved = solved,
            missCount = misses,
            locked = locked,
            interactionLocked = interactionLocked,
            onResolve = ::resolve,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = onSpeakPrompt,
            modifier = modifier.fillMaxSize(),
        )
```

- [ ] **Step 3: `TrainerHost`s `CountAddRound`-Branch**

Ersetze:

```kotlin
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
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onMathResult,
            modifier = modifier.fillMaxSize(),
        )
```

durch:

```kotlin
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
```

- [ ] **Step 4: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Manuell verifizieren**

App starten, ein Rechnen-Runde öffnen (mit und ohne Zahlen-Pad-Scaffold, falls im aktuellen Testcontent beide Fälle erreichbar sind). Während die Ansage läuft: Zahlenfeld/Wahlkacheln bei 50% Deckkraft, Tap tut nichts. Nach Ansage-Ende: normal nutzbar, Tastatur poppt erst jetzt auf (nicht schon während der Sperre).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt \
        app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt
git commit -m "feat(math): lock MathExercise interaction until intro audio finishes

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 7: `SentenceOrderTrainer` verdrahten

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SentenceOrderTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt:93-109` (`SentenceOrderRound`-Branch)

**Interfaces:**
- Consumes: `DragCard(enabled)`, `DropZone(enabled)` (Task 4); `TrainerHost`s `interactionLocked` (Task 3).

- [ ] **Step 1: Imports + Funktionssignatur**

Füge oben in `SentenceOrderTrainer.kt` hinzu (neben den bestehenden `androidx.compose.animation.core.*`-Imports):

```kotlin
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.alpha
```

Ersetze die Signatur:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceOrderTrainer(
    round: SentenceOrderRound,
    roundIndex: Int,
    words: List<String>,
    atomIds: List<String>,
    illustrationEmoji: String?,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

durch:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceOrderTrainer(
    round: SentenceOrderRound,
    roundIndex: Int,
    words: List<String>,
    atomIds: List<String>,
    illustrationEmoji: String?,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

- [ ] **Step 2: Opazität berechnen**

Nach der Zeile `val haptics = LocalAbcHaptics.current` (innerhalb `SentenceOrderTrainer`, vor `fun place(...)`), füge ein:

```kotlin
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "sentence_order_lock_opacity",
    )
```

- [ ] **Step 3: `Peg` bekommt `enabled`/`opacity`**

Ersetze die `Peg`-Signatur und ihren `DropZone`-Aufruf:

```kotlin
@Composable
private fun Peg(
    index: Int,
    expected: String,
    filled: String?,
    showGhost: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: app.abcvorschule.ui.exercise.drag.DragFieldState,
    pegWidthDp: Float,
    glyphSp: Float,
) {
    DropZone(
        state = registerWith,
        key = SentenceOrderTray.pegKey(index),
        onTap = onTap,
        modifier = Modifier
            .width(pegWidthDp.dp)
            .defaultMinSize(minHeight = 64.dp)
```

durch:

```kotlin
@Composable
private fun Peg(
    index: Int,
    expected: String,
    filled: String?,
    showGhost: Boolean,
    armed: Boolean,
    enabled: Boolean,
    opacity: Float,
    onTap: () -> Unit,
    registerWith: app.abcvorschule.ui.exercise.drag.DragFieldState,
    pegWidthDp: Float,
    glyphSp: Float,
) {
    DropZone(
        state = registerWith,
        key = SentenceOrderTray.pegKey(index),
        onTap = onTap,
        enabled = enabled,
        modifier = Modifier
            .width(pegWidthDp.dp)
            .defaultMinSize(minHeight = 64.dp)
            .alpha(opacity)
```

- [ ] **Step 4: `Peg`-Aufruf erweitern**

Ersetze:

```kotlin
                            Peg(
                                index = index,
                                expected = expected,
                                filled = filled,
                                showGhost = scaffoldFor(atomId) == ScaffoldLevel.Beginner,
                                armed = field.selectedKey != null && filled == null,
                                onTap = {
                                    val selected = field.selectedKey
                                    val card = cards.firstOrNull { cardKey(it) == selected }
                                    if (card != null) place(index, card)
                                    if (filled != null) onSpeak(filled)
                                },
                                registerWith = field,
                                pegWidthDp = pegWidth,
                                glyphSp = glyphSp,
                            )
```

durch:

```kotlin
                            Peg(
                                index = index,
                                expected = expected,
                                filled = filled,
                                showGhost = scaffoldFor(atomId) == ScaffoldLevel.Beginner,
                                armed = field.selectedKey != null && filled == null,
                                enabled = !interactionLocked,
                                opacity = interactionOpacity,
                                onTap = {
                                    val selected = field.selectedKey
                                    val card = cards.firstOrNull { cardKey(it) == selected }
                                    if (card != null) place(index, card)
                                    if (filled != null) onSpeak(filled)
                                },
                                registerWith = field,
                                pegWidthDp = pegWidth,
                                glyphSp = glyphSp,
                            )
```

- [ ] **Step 5: Tray-`DragCard` erweitern**

Ersetze:

```kotlin
                        DragCard(
                            state = field,
                            key = key,
                            onTap = {
                                field.select(key)
                                onSpeak(card.display)
                            },
                            onDropped = { zoneKey ->
                                SentenceOrderTray.pegIndex(zoneKey ?: "")?.let { place(it, card) }
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = AbcDimens.kidTouch - 8.dp)
                                .background(
                                    color = if (field.selectedKey == key) LeafGreen else CreamElevated,
                                    shape = RoundedCornerShape(18.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("card_${card.display}"),
                        ) {
```

durch:

```kotlin
                        DragCard(
                            state = field,
                            key = key,
                            enabled = !interactionLocked,
                            onTap = {
                                field.select(key)
                                onSpeak(card.display)
                            },
                            onDropped = { zoneKey ->
                                SentenceOrderTray.pegIndex(zoneKey ?: "")?.let { place(it, card) }
                            },
                            modifier = Modifier
                                .defaultMinSize(minHeight = AbcDimens.kidTouch - 8.dp)
                                .alpha(interactionOpacity)
                                .background(
                                    color = if (field.selectedKey == key) LeafGreen else CreamElevated,
                                    shape = RoundedCornerShape(18.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("card_${card.display}"),
                        ) {
```

- [ ] **Step 6: `TrainerHost`s `SentenceOrderRound`-Branch**

Ersetze:

```kotlin
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
                onSpeakPrompt = callbacks.onSpeakPrompt,
                onSpeak = callbacks.onSpeak,
                onResult = callbacks.onResult,
                modifier = modifier.fillMaxSize(),
            )
        }
```

durch:

```kotlin
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
```

- [ ] **Step 7: Kompilieren + manuell verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Satzbau-Runde öffnen: während der Ansage sind Klammern (Pegs) und Wortkarten bei 50%, Tap tut nichts; danach normal nutzbar.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SentenceOrderTrainer.kt \
        app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt
git commit -m "feat(sentence-order): lock interaction until intro audio finishes

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 8: `SoundPositionTrainer` verdrahten

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SoundPositionTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt:45-56` (`SoundPositionRound`-Branch)

**Interfaces:**
- Consumes: `DragCard(enabled)`, `DropZone(enabled)` (Task 4); `TrainerHost`s `interactionLocked` (Task 3).

- [ ] **Step 1: Imports + Funktionssignatur**

Füge oben in `SoundPositionTrainer.kt` hinzu:

```kotlin
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
```

(`animateFloatAsState` ist dort schon importiert.)

Ersetze die Signatur:

```kotlin
@Composable
fun SoundPositionTrainer(
    round: SoundPositionRound,
    roundIndex: Int,
    atom: Atom,
    targetPhoneme: String,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

durch:

```kotlin
@Composable
fun SoundPositionTrainer(
    round: SoundPositionRound,
    roundIndex: Int,
    atom: Atom,
    targetPhoneme: String,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

- [ ] **Step 2: Opazität + `Wagon`-Aufrufe erweitern**

Nach `val cardKey = "picture-${round.atomId}"`, füge ein:

```kotlin
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "sound_position_lock_opacity",
    )
```

Ersetze im `prompt`-Block:

```kotlin
                SoundPositionLogic.SlotOrder.forEach { slot ->
                    val armed = field.selectedKey != null && landedSlot == null && !revealed
                    Wagon(
                        slot = slot,
                        filledEmoji = if (landedSlot == slot) atom.emoji else null,
                        revealed = revealed && round.slot == slot,
                        armed = armed,
                        // An exploratory tap must never burn a miss. Only a wagon tap
                        // that follows picking the picture up counts as a placement.
                        onTap = { if (armed) place(slot) },
                        registerWith = field,
                    )
                }
```

durch:

```kotlin
                SoundPositionLogic.SlotOrder.forEach { slot ->
                    val armed = field.selectedKey != null && landedSlot == null && !revealed
                    Wagon(
                        slot = slot,
                        filledEmoji = if (landedSlot == slot) atom.emoji else null,
                        revealed = revealed && round.slot == slot,
                        armed = armed,
                        enabled = !interactionLocked,
                        opacity = interactionOpacity,
                        // An exploratory tap must never burn a miss. Only a wagon tap
                        // that follows picking the picture up counts as a placement.
                        onTap = { if (armed) place(slot) },
                        registerWith = field,
                    )
                }
```

- [ ] **Step 3: Bild-`DragCard` erweitern**

Ersetze:

```kotlin
                DragCard(
                    state = field,
                    key = cardKey,
                    onTap = {
                        field.select(cardKey)
                        onSpeak(atom.lemma)
                    },
                    onDropped = { zoneKey ->
                        SoundPositionLogic.slotFromKey(zoneKey ?: "")?.let(::place)
                    },
                    modifier = Modifier
                        .size(AbcDimens.kidTouch + 20.dp)
                        .background(
                            color = if (field.selectedKey == cardKey) LeafGreen.copy(alpha = 0.25f) else CreamElevated,
                            shape = RoundedCornerShape(24.dp),
                        )
                        .testTag("sound_card"),
                ) {
```

durch:

```kotlin
                DragCard(
                    state = field,
                    key = cardKey,
                    enabled = !interactionLocked,
                    onTap = {
                        field.select(cardKey)
                        onSpeak(atom.lemma)
                    },
                    onDropped = { zoneKey ->
                        SoundPositionLogic.slotFromKey(zoneKey ?: "")?.let(::place)
                    },
                    modifier = Modifier
                        .size(AbcDimens.kidTouch + 20.dp)
                        .alpha(interactionOpacity)
                        .background(
                            color = if (field.selectedKey == cardKey) LeafGreen.copy(alpha = 0.25f) else CreamElevated,
                            shape = RoundedCornerShape(24.dp),
                        )
                        .testTag("sound_card"),
                ) {
```

- [ ] **Step 4: `Wagon` — `enabled`/`opacity`**

Ersetze die `Wagon`-Signatur und ihren `DropZone`-Aufruf:

```kotlin
@Composable
private fun Wagon(
    slot: SoundSlot,
    filledEmoji: String?,
    revealed: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: DragFieldState,
) {
    val index = SoundPositionLogic.SlotOrder.indexOf(slot)
    val borderAccent = wagonBorderColor(index)
    val desc = stringResource(
        when (slot) {
            SoundSlot.start -> R.string.wagon_start
            SoundSlot.middle -> R.string.wagon_middle
            SoundSlot.end -> R.string.wagon_end
        },
    )
    // The border itself no longer dims for the idle state — at reduced alpha none of
    // the three wagon colours clears 3:1 against CreamElevated (~1.67–2.18:1 measured
    // at the old 0.55/0.7 alphas). The idle/armed distinction now lives in the fill
    // below instead.
    val border = if (filledEmoji != null || revealed) LeafGreen else borderAccent
    DropZone(
        state = registerWith,
        key = SoundPositionLogic.slotKey(slot),
        onTap = onTap,
        modifier = Modifier
            .size(WagonSize)
            .background(
```

durch:

```kotlin
@Composable
private fun Wagon(
    slot: SoundSlot,
    filledEmoji: String?,
    revealed: Boolean,
    armed: Boolean,
    enabled: Boolean,
    opacity: Float,
    onTap: () -> Unit,
    registerWith: DragFieldState,
) {
    val index = SoundPositionLogic.SlotOrder.indexOf(slot)
    val borderAccent = wagonBorderColor(index)
    val desc = stringResource(
        when (slot) {
            SoundSlot.start -> R.string.wagon_start
            SoundSlot.middle -> R.string.wagon_middle
            SoundSlot.end -> R.string.wagon_end
        },
    )
    val border = if (filledEmoji != null || revealed) LeafGreen else borderAccent
    DropZone(
        state = registerWith,
        key = SoundPositionLogic.slotKey(slot),
        onTap = onTap,
        enabled = enabled,
        modifier = Modifier
            .size(WagonSize)
            .alpha(opacity)
            .background(
```

- [ ] **Step 5: `TrainerHost`s `SoundPositionRound`-Branch**

Ersetze:

```kotlin
        is SoundPositionRound -> SoundPositionTrainer(
            round = round,
            roundIndex = roundIndex,
            atom = pack.atom(round.atomId),
            targetPhoneme = (trainer.spec as? SoundPositionSpec)?.phonemeTts.orEmpty(),
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
```

durch:

```kotlin
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
```

- [ ] **Step 6: Kompilieren + manuell verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Lautposition-Runde öffnen: Bildkarte + Waggons bei 50% während der Ansage, danach normal nutzbar.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SoundPositionTrainer.kt \
        app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt
git commit -m "feat(sound-position): lock interaction until intro audio finishes

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 9: `SyllableMergeTrainer` verdrahten

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SyllableMergeTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt:68-78` (`SyllableMergeRound`-Branch)

**Interfaces:**
- Consumes: `TrainerHost`s `interactionLocked` (Task 3).

- [ ] **Step 1: Import + Funktionssignatur**

Füge oben in `SyllableMergeTrainer.kt` hinzu:

```kotlin
import androidx.compose.ui.draw.alpha
```

(`animateFloatAsState`/`tween` sind dort schon importiert.)

Ersetze die Signatur:

```kotlin
@Composable
fun SyllableMergeTrainer(
    round: SyllableMergeRound,
    roundIndex: Int,
    resultSpeech: String,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

durch:

```kotlin
@Composable
fun SyllableMergeTrainer(
    round: SyllableMergeRound,
    roundIndex: Int,
    resultSpeech: String,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

- [ ] **Step 2: Opazität berechnen**

Nach `val haptics = LocalAbcHaptics.current`, füge ein:

```kotlin
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "syllable_merge_lock_opacity",
    )
```

- [ ] **Step 3: `mergeDrag` bekommt `enabled`**

Ersetze:

```kotlin
    fun Modifier.mergeDrag(fromRightTile: Boolean): Modifier = pointerInput(roundKey) {
        detectDragGestures(
            onDragStart = {
                dragging = true
                scope.launch { idleNudge.snapTo(0f) }
                speakTile(fromRightTile)
            },
            onDrag = { change, amount ->
                change.consume()
                val target = MergeProgress.applyDrag(fraction.value, amount.x, tileTravelPx, fromRightTile)
                scope.launch { fraction.snapTo(target) }
                if (MergeProgress.isContact(target)) {
                    dragging = false
                    commit()
                }
            },
            onDragEnd = { settle() },
            onDragCancel = { settle() },
        )
    }
```

durch:

```kotlin
    fun Modifier.mergeDrag(fromRightTile: Boolean, enabled: Boolean): Modifier =
        if (!enabled) this else pointerInput(roundKey) {
            detectDragGestures(
                onDragStart = {
                    dragging = true
                    scope.launch { idleNudge.snapTo(0f) }
                    speakTile(fromRightTile)
                },
                onDrag = { change, amount ->
                    change.consume()
                    val target = MergeProgress.applyDrag(fraction.value, amount.x, tileTravelPx, fromRightTile)
                    scope.launch { fraction.snapTo(target) }
                    if (MergeProgress.isContact(target)) {
                        dragging = false
                        commit()
                    }
                },
                onDragEnd = { settle() },
                onDragCancel = { settle() },
            )
        }
```

- [ ] **Step 4: `Floe` bekommt `enabled`/`opacity`**

Ersetze:

```kotlin
@Composable
private fun Floe(
    label: String,
    glow: Float,
    frozen: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(SyllableFrameSizing.widthDp(label).dp)
            .height(AbcDimens.letterFrame)
            .background(
                color = if (frozen) LeafGreen.copy(alpha = 0.25f) else CreamElevated,
                shape = RoundedCornerShape(26.dp),
            )
            .border(
                width = 4.dp,
                color = (if (frozen) LeafGreen else SkyBlue).copy(alpha = glow),
                shape = RoundedCornerShape(26.dp),
            )
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
```

durch:

```kotlin
@Composable
private fun Floe(
    label: String,
    glow: Float,
    frozen: Boolean,
    onTap: () -> Unit,
    enabled: Boolean = true,
    opacity: Float = 1f,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(SyllableFrameSizing.widthDp(label).dp)
            .height(AbcDimens.letterFrame)
            .alpha(opacity)
            .background(
                color = if (frozen) LeafGreen.copy(alpha = 0.25f) else CreamElevated,
                shape = RoundedCornerShape(26.dp),
            )
            .border(
                width = 4.dp,
                color = (if (frozen) LeafGreen else SkyBlue).copy(alpha = glow),
                shape = RoundedCornerShape(26.dp),
            )
            .clickable(enabled = enabled, onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
```

- [ ] **Step 5: Die zwei unverschmolzenen `Floe`-Aufrufe erweitern**

Ersetze:

```kotlin
                        Floe(
                            label = round.leftDisplay,
                            glow = glow,
                            frozen = false,
                            onTap = { nudgeTap(fromRightTile = false) },
                            modifier = Modifier
                                .offset { IntOffset(inwardPx.roundToInt(), 0) }
                                .mergeDrag(fromRightTile = false)
                                .testTag("merge_left"),
                        )
                        Spacer(Modifier.width(FloeGap))
                        Floe(
                            label = round.rightDisplay,
                            glow = glow,
                            frozen = false,
                            onTap = { nudgeTap(fromRightTile = true) },
                            modifier = Modifier
                                .offset { IntOffset(-inwardPx.roundToInt(), 0) }
                                .mergeDrag(fromRightTile = true)
                                .testTag("merge_right"),
                        )
```

durch:

```kotlin
                        Floe(
                            label = round.leftDisplay,
                            glow = glow,
                            frozen = false,
                            onTap = { nudgeTap(fromRightTile = false) },
                            enabled = !interactionLocked,
                            opacity = interactionOpacity,
                            modifier = Modifier
                                .offset { IntOffset(inwardPx.roundToInt(), 0) }
                                .mergeDrag(fromRightTile = false, enabled = !interactionLocked)
                                .testTag("merge_left"),
                        )
                        Spacer(Modifier.width(FloeGap))
                        Floe(
                            label = round.rightDisplay,
                            glow = glow,
                            frozen = false,
                            onTap = { nudgeTap(fromRightTile = true) },
                            enabled = !interactionLocked,
                            opacity = interactionOpacity,
                            modifier = Modifier
                                .offset { IntOffset(-inwardPx.roundToInt(), 0) }
                                .mergeDrag(fromRightTile = true, enabled = !interactionLocked)
                                .testTag("merge_right"),
                        )
```

Die dritte `Floe(...)`-Stelle (das gefrorene Ergebnis, `label = round.resultDisplay, ..., frozen = true, onTap = { onSpeak(resultSpeech) }`) bleibt unverändert — sie erscheint erst, nachdem die Runde bereits gelöst ist, außerhalb des Sperrfensters.

- [ ] **Step 6: `TrainerHost`s `SyllableMergeRound`-Branch**

Ersetze:

```kotlin
        is SyllableMergeRound -> SyllableMergeTrainer(
            round = round,
            roundIndex = roundIndex,
            resultSpeech = SyllableMergeSpeech.resultSpeech(round, pack.atoms[round.resultAtomId]),
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
```

durch:

```kotlin
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
```

- [ ] **Step 7: Kompilieren + manuell verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Silben-Verschmelzung-Runde öffnen: beide Kacheln bei 50% während der Ansage, weder Tap noch Drag lösen etwas aus; danach normal nutzbar.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SyllableMergeTrainer.kt \
        app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt
git commit -m "feat(syllable-merge): lock interaction until intro audio finishes

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 10: `WordBuildTrainer` verdrahten

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/WordBuildTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt:79-92` (`WordBuildRound`-Branch)

**Interfaces:**
- Consumes: `DragCard(enabled)`, `DropZone(enabled)` (Task 4); `TrainerHost`s `interactionLocked` (Task 3).

- [ ] **Step 1: Import + Funktionssignatur**

Füge oben in `WordBuildTrainer.kt` hinzu (neben den bestehenden `androidx.compose.animation.*`-Imports):

```kotlin
import androidx.compose.animation.core.animateFloatAsState
```

(`androidx.compose.animation.core.tween` und `androidx.compose.ui.draw.alpha` sind dort schon importiert.)

Ersetze die Signatur:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordBuildTrainer(
    round: WordBuildRound,
    roundIndex: Int,
    pack: ContentPack,
    target: Atom,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onSpeakAndAwait: suspend (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

durch:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordBuildTrainer(
    round: WordBuildRound,
    roundIndex: Int,
    pack: ContentPack,
    target: Atom,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onSpeakAndAwait: suspend (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

- [ ] **Step 2: Opazität berechnen**

Nach `val scope = rememberCoroutineScope()`, füge ein:

```kotlin
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "word_build_lock_opacity",
    )
```

- [ ] **Step 3: `Frame` bekommt `enabled`/`opacity` (angehängt, Aufruf bleibt positional)**

Ersetze die `Frame`-Signatur und ihren `DropZone`-Aufruf:

```kotlin
@Composable
private fun Frame(
    expected: String,
    filled: String?,
    showSilhouette: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: DragFieldState,
    index: Int,
    frameWidthDp: Float,
    glyphSp: Float,
) {
    DropZone(
        state = registerWith,
        key = WordBuildTray.frameKey(index),
        onTap = onTap,
        modifier = Modifier
            .width(frameWidthDp.dp)
            .defaultMinSize(minHeight = frameWidthDp.dp)
            .background(
```

durch:

```kotlin
@Composable
private fun Frame(
    expected: String,
    filled: String?,
    showSilhouette: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: DragFieldState,
    index: Int,
    frameWidthDp: Float,
    glyphSp: Float,
    enabled: Boolean = true,
    opacity: Float = 1f,
) {
    DropZone(
        state = registerWith,
        key = WordBuildTray.frameKey(index),
        onTap = onTap,
        enabled = enabled,
        modifier = Modifier
            .width(frameWidthDp.dp)
            .defaultMinSize(minHeight = frameWidthDp.dp)
            .alpha(opacity)
            .background(
```

- [ ] **Step 4: `Frame`-Aufruf erweitern (positional, 2 Argumente anhängen)**

Ersetze:

```kotlin
                            Frame(expected, filled, scaffoldFor(atomId) == ScaffoldLevel.Beginner, field.selectedKey != null && filled == null, {
                                val selected = field.selectedKey
                                tiles.withIndex()
                                    .firstOrNull { (i, block) -> WordBuildTray.tileKey(i, block) == selected }
                                    ?.let { (_, block) -> place(index, block) }
                                if (filled != null) {
                                    onSpeak(
                                        SpeechClipText.forAtomId(
                                            pack,
                                            round.blocks[index].atomId,
                                            filled,
                                        ),
                                    )
                                }
                            }, field, index, frameWidth, glyphSp)
```

durch:

```kotlin
                            Frame(expected, filled, scaffoldFor(atomId) == ScaffoldLevel.Beginner, field.selectedKey != null && filled == null, {
                                val selected = field.selectedKey
                                tiles.withIndex()
                                    .firstOrNull { (i, block) -> WordBuildTray.tileKey(i, block) == selected }
                                    ?.let { (_, block) -> place(index, block) }
                                if (filled != null) {
                                    onSpeak(
                                        SpeechClipText.forAtomId(
                                            pack,
                                            round.blocks[index].atomId,
                                            filled,
                                        ),
                                    )
                                }
                            }, field, index, frameWidth, glyphSp, !interactionLocked, interactionOpacity)
```

- [ ] **Step 5: Tray-`DragCard` erweitern**

Ersetze:

```kotlin
                        DragCard(
                            state = field,
                            key = key,
                            onTap = {
                                field.select(key)
                                onSpeak(SpeechClipText.forAtomId(pack, block.atomId, block.display))
                            },
                            onDropped = { zoneKey ->
                                WordBuildTray.frameIndex(zoneKey ?: "")?.let { place(it, block) }
                            },
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = AbcDimens.tileMinWidth,
                                    minHeight = AbcDimens.kidTouch,
                                )
                                .background(
                                    color = if (field.selectedKey == key) LeafGreen else CreamElevated,
                                    shape = RoundedCornerShape(22.dp),
                                )
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                                .testTag("block_${block.display}"),
                        ) {
```

durch:

```kotlin
                        DragCard(
                            state = field,
                            key = key,
                            enabled = !interactionLocked,
                            onTap = {
                                field.select(key)
                                onSpeak(SpeechClipText.forAtomId(pack, block.atomId, block.display))
                            },
                            onDropped = { zoneKey ->
                                WordBuildTray.frameIndex(zoneKey ?: "")?.let { place(it, block) }
                            },
                            modifier = Modifier
                                .defaultMinSize(
                                    minWidth = AbcDimens.tileMinWidth,
                                    minHeight = AbcDimens.kidTouch,
                                )
                                .alpha(interactionOpacity)
                                .background(
                                    color = if (field.selectedKey == key) LeafGreen else CreamElevated,
                                    shape = RoundedCornerShape(22.dp),
                                )
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                                .testTag("block_${block.display}"),
                        ) {
```

- [ ] **Step 6: `TrainerHost`s `WordBuildRound`-Branch**

Ersetze:

```kotlin
        is WordBuildRound -> WordBuildTrainer(
            round = round,
            roundIndex = roundIndex,
            pack = pack,
            target = pack.atom(round.targetAtomId),
            scaffoldFor = scaffoldFor,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onSpeakAndAwait = callbacks.onSpeakAndAwait,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
```

durch:

```kotlin
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
```

- [ ] **Step 7: Kompilieren + manuell verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Wort-Bauer-Runde öffnen: Rahmen + Kacheln bei 50% während der Ansage, danach normal nutzbar.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/WordBuildTrainer.kt \
        app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt
git commit -m "feat(word-build): lock interaction until intro audio finishes

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 11: `SymbolHuntTrainer` verdrahten

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt:125-135` (`SymbolHuntRound`-Branch)

**Interfaces:**
- Consumes: `TrainerHost`s `interactionLocked` (Task 3).

- [ ] **Step 1: Funktionssignatur**

Ersetze:

```kotlin
@Composable
fun SymbolHuntTrainer(
    round: SymbolHuntRound,
    roundIndex: Int,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

durch:

```kotlin
@Composable
fun SymbolHuntTrainer(
    round: SymbolHuntRound,
    roundIndex: Int,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

(`animateFloatAsState`, `tween` und `androidx.compose.ui.draw.alpha` sind in dieser Datei bereits importiert.)

- [ ] **Step 2: Opazität mit der bestehenden `fieldAlpha` kombinieren**

Ersetze:

```kotlin
    val fieldAlpha by animateFloatAsState(
        targetValue = if (batteryFull) 0f else 1f,
        animationSpec = tween(durationMillis = 400),
        label = "hunt_field_fade",
    )
```

durch:

```kotlin
    val fieldAlpha by animateFloatAsState(
        targetValue = if (batteryFull) 0f else 1f,
        animationSpec = tween(durationMillis = 400),
        label = "hunt_field_fade",
    )
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "hunt_lock_opacity",
    )
```

- [ ] **Step 3: `SymbolHuntField` — `enabled` + Opazität kombinieren**

Ersetze:

```kotlin
            if (!resolved) {
                SymbolHuntField(
                    state = state,
                    initialTileCount = initialTileCount,
                    pack = pack,
                    enabled = !batteryFull,
                    onTap = ::handleTap,
                    modifier = Modifier.fillMaxSize().alpha(fieldAlpha),
                )
            }
```

durch:

```kotlin
            if (!resolved) {
                SymbolHuntField(
                    state = state,
                    initialTileCount = initialTileCount,
                    pack = pack,
                    enabled = !batteryFull && !interactionLocked,
                    onTap = ::handleTap,
                    modifier = Modifier.fillMaxSize().alpha(fieldAlpha * interactionOpacity),
                )
            }
```

- [ ] **Step 4: `TrainerHost`s `SymbolHuntRound`-Branch**

Ersetze:

```kotlin
        is SymbolHuntRound -> SymbolHuntTrainer(
            round = round,
            roundIndex = roundIndex,
            pack = pack,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
```

durch:

```kotlin
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
```

- [ ] **Step 5: Kompilieren + manuell verifizieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

Buchstaben-/Silben-Jagd-Runde öffnen: gestreute Kacheln bei 50% während der 2-teiligen Ansage ("Finde alle Buchstaben" + Zielbuchstabe), danach normal nutzbar.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntTrainer.kt \
        app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt
git commit -m "feat(symbol-hunt): lock interaction until intro audio finishes

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 12: `SymbolInWordTrainer` verdrahten (Sonderfall Wort-Detektiv)

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SymbolInWordTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt:136-147` (`SymbolInWordRound`-Branch)

**Interfaces:**
- Consumes: `TrainerHost`s `interactionLocked` (Task 3); `TrainerCallbacks.onSpeakFeedback` (Task 3), das in `MainActivity`/`AbcApp` bereits auf `SpeechChannel.Feedback` verdrahtet ist (Task 3, Step 6).

Dieser Task schließt sowohl die 50%-Sperre als auch das eigentliche "gleichzeitige Audios"-Verhalten für Wort-Detektiv ab — sobald der Zielbuchstabe gesprochen ist (Freigabe-Index 1, siehe `PromptUnlock`), kann das Kind tippen, während Konnektor + Wort noch auf dem Primary-Kanal weiterlaufen; das Tap-Echo läuft auf dem Feedback-Kanal parallel.

- [ ] **Step 1: Import + Funktionssignatur**

Füge oben in `SymbolInWordTrainer.kt` hinzu (neben den bestehenden `androidx.compose.animation.core.*`-Imports):

```kotlin
import androidx.compose.animation.core.animateFloatAsState
```

Ersetze:

```kotlin
@Composable
fun SymbolInWordTrainer(
    round: SymbolInWordRound,
    roundIndex: Int,
    pack: ContentPack,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

durch:

```kotlin
@Composable
fun SymbolInWordTrainer(
    round: SymbolInWordRound,
    roundIndex: Int,
    pack: ContentPack,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    /** Wie [onSpeak], aber auf dem Feedback-Kanal — läuft parallel zur noch
     * laufenden Ansage (Konnektor + Wort), statt sie abzuwürgen (design doc). */
    onSpeakFeedback: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
```

- [ ] **Step 2: Tap-Echo auf den Feedback-Kanal umstellen**

Ersetze innerhalb `handleTap`:

```kotlin
        onSpeak(SpeechClipText.forSegment(pack, round, index))
```

durch:

```kotlin
        onSpeakFeedback(SpeechClipText.forSegment(pack, round, index))
```

- [ ] **Step 3: Opazität berechnen**

Nach `val haptics = LocalAbcHaptics.current`, füge ein:

```kotlin
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "detective_lock_opacity",
    )
```

- [ ] **Step 4: `WordSegments` bekommt einen `modifier`-Parameter**

Ersetze:

```kotlin
@Composable
private fun WordSegments(
    round: SymbolInWordRound,
    state: SymbolInWordState,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onSegmentPlaced: (Int, Offset) -> Unit,
    onGlyphSpMeasured: (Float) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
```

durch:

```kotlin
@Composable
private fun WordSegments(
    round: SymbolInWordRound,
    state: SymbolInWordState,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onSegmentPlaced: (Int, Offset) -> Unit,
    onGlyphSpMeasured: (Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
```

- [ ] **Step 5: `WordSegments`-Aufruf erweitern**

Ersetze:

```kotlin
                WordSegments(
                    round = round,
                    state = state,
                    enabled = !complete && !resolved,
                    onTap = ::handleTap,
                    onSegmentPlaced = { index, center -> segmentCenters[index] = center },
                    onGlyphSpMeasured = { segmentGlyphSp = it },
                )
```

durch:

```kotlin
                WordSegments(
                    round = round,
                    state = state,
                    enabled = !complete && !resolved && !interactionLocked,
                    onTap = ::handleTap,
                    onSegmentPlaced = { index, center -> segmentCenters[index] = center },
                    onGlyphSpMeasured = { segmentGlyphSp = it },
                    modifier = Modifier.alpha(interactionOpacity),
                )
```

- [ ] **Step 6: `TrainerHost`s `SymbolInWordRound`-Branch**

Ersetze:

```kotlin
        is SymbolInWordRound -> SymbolInWordTrainer(
            round = round,
            roundIndex = roundIndex,
            pack = pack,
            scaffoldFor = scaffoldFor,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
```

durch:

```kotlin
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
```

- [ ] **Step 7: Kompilieren**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Manuell verifizieren (Kernfall dieses ganzen Features)**

Wort-Detektiv-Runde mit einem mehrsilbigen Wort öffnen (z.B. "Mama"), auf einem echten Gerät oder Emulator mit Ton an:
1. Runde startet: Wortsegmente bei 50%, nicht tappbar.
2. Nach "Finde den Buchstaben" + Zielbuchstabe ("M"): Segmente werden bei voller Deckkraft tappbar — noch **während** "...im Wort... Mama" weiterläuft.
3. Auf ein Segment tippen, während "Mama" noch zu hören ist: das Tap-Echo (der angetippte Buchstabe) ist **zusätzlich** zu hören, "Mama" bricht **nicht** ab.
4. Rundenwechsel (Zurück-Pfeil) mitten in der Ansage: beide Audiospuren stoppen sauber, neue Runde startet erneut gesperrt.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SymbolInWordTrainer.kt \
        app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt
git commit -m "feat(symbol-in-word): unlock after target letter, echo on feedback channel

Co-Authored-By: Claude Sonnet 5 <noreply@anthropic.com>"
```

---

### Task 13: Vollständiger Testlauf + Regression bei font_scale 1.3

**Files:** keine Code-Änderungen — reine Verifikation.

- [ ] **Step 1: Unit-Tests**

Run: `./gradlew :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle Tests grün (inkl. der 5 neuen `PromptUnlockTest`-Fälle aus Task 2).

- [ ] **Step 2: Gerät auf font_scale 1.3 stellen**

```bash
adb shell settings put system font_scale 1.3
```

- [ ] **Step 3: Jeden der 7 Trainer einmal durchspielen**

Für Wort-Detektiv, Buchstaben-/Silben-Jagd, Satzbau, Lautposition, Silben-Verschmelzung, Wort-Bauer, Rechnen (Zahlen-Pad **und** Kachel-Variante, falls beide im Testcontent erreichbar): Ansage abwarten oder mittendrin antippen — in beiden Fällen darf die Ansage nicht durch einen Tap unterbrechbar sein, die 50%-Deckkraft muss bei der größeren Schrift genauso klar erkennbar bleiben wie bei font_scale 1.0, und der Lautsprecher-Button muss jederzeit tappbar bleiben.

- [ ] **Step 4: Spurensucher gegenprüfen**

Spurensucher-Runde öffnen: Verhalten unverändert (keine Sperre, keine Abdunklung, sofort nachzeichenbar) — bestätigt, dass Task-Scope korrekt eingehalten wurde.

- [ ] **Step 5: font_scale zurücksetzen**

```bash
adb shell settings put system font_scale 1.0
```

- [ ] **Step 6: Finale Zusammenfassung committen (falls noch offene Änderungen)**

Falls bei der manuellen Prüfung noch kleine Korrekturen nötig waren, diese gesondert committen. Andernfalls ist dieser Task rein verifizierend und erzeugt keinen eigenen Commit.
