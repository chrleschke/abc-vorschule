# TTS Debug Page Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a debug-build-only screen, reachable from the bottom of the Path
(home) screen, that lists every content-authored TTS string, lets a developer
hear and edit each one, and exports edits to a file that can be pulled off the
device and merged back into the content-pack JSON.

**Architecture:** A pure function walks the already-loaded `ContentPack` into a
flat list of `TtsDebugEntry` (id + original text + source file). A new
`TtsDebugRepository` persists edits (id → text) in its own DataStore
preference and mirrors them into a JSON export file on every change. A new
full-screen Composable renders the entry list (searchable, grouped, inline
editable, speaker button per row) and is toggled on top of `TaskShell` by a
plain local `showTtsDebug` boolean in `AbcApp`, reachable via a
`BuildConfig.DEBUG`-gated button on `PathScreen`.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), kotlinx.serialization,
Jetpack DataStore Preferences, JUnit4 + kotlinx-coroutines-test for JVM unit
tests.

## Global Constraints

- Only content-JSON-authored TTS strings are listed: `Atom.lemma`,
  `Sentence.tts`, and the `*Tts` fields on `TrainerRound`/`TaskSpec`
  (`promptTts`, `phonemeTts`, `missTts`, `rewardTts`, `stretchTts`). Hardcoded
  Kotlin strings (`PraisePhrases`, `MathHinting`, `SessionViewModel` cues) are
  out of scope.
- Edits never affect real lessons/trainers — `ContentRepository`/`ContentPack`
  used by gameplay is untouched; overrides are read only by the debug screen.
- The debug entry point and screen exist only when `BuildConfig.DEBUG` is
  true — absent entirely from release builds.
- No parent-gate / long-press protection on the entry button — it's a plain
  visible button.
- Unit test command: `./gradlew :app:testDebugUnitTest`
- Full build command: `./gradlew :app:assembleDebug`
- Spec: `docs/superpowers/specs/2026-07-31-tts-debug-page-design.md`

---

### Task 1: TTS entry model + builder

**Files:**
- Create: `app/src/main/java/app/abcvorschule/debug/TtsDebugEntry.kt`
- Test: `app/src/test/java/app/abcvorschule/debug/TtsDebugEntryTest.kt`

**Interfaces:**
- Consumes: `app.abcvorschule.content.ContentPack` (fields `atoms: Map<String, Atom>`,
  `sentences: Map<String, Sentence>`, `tasks: Map<String, TaskSpec>`), the
  `TaskSpec.rounds: List<TrainerRound>` extension property from
  `content/TaskSpecs.kt`, and the round subtypes `SoundPositionSpec`,
  `SoundPositionRound`, `LetterTraceRound`, `SyllableMergeRound` (all in
  `app.abcvorschule.content`).
- Produces: `TtsDebugGroup` enum (`Atom`, `Sentence`, `Task`), `TtsDebugEntry`
  data class (`id: String`, `group: TtsDebugGroup`, `label: String`,
  `originalText: String`, `sourceFile: String`), and
  `fun ContentPack.ttsDebugEntries(): List<TtsDebugEntry>` — used by Task 4's
  screen and Task 2's repository tests.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/abcvorschule/debug/TtsDebugEntryTest.kt`:

```kotlin
package app.abcvorschule.debug

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.SoundPositionSpec
import app.abcvorschule.content.rounds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsDebugEntryTest {
    private val pack = ContentRepository.fromClasspath().load()
    private val entries = pack.ttsDebugEntries()

    @Test
    fun oneEntryPerAtomLemma() {
        val atomEntries = entries.filter { it.group == TtsDebugGroup.Atom }
        assertEquals(pack.atoms.size, atomEntries.size)
        val letterM = atomEntries.first { it.id == "atom:letter-m:lemma" }
        assertEquals("M", letterM.originalText)
        assertEquals("atoms.json", letterM.sourceFile)
    }

    @Test
    fun oneEntryPerSentenceTts() {
        val sentenceEntries = entries.filter { it.group == TtsDebugGroup.Sentence }
        assertEquals(pack.sentences.size, sentenceEntries.size)
        val sentence = pack.sentences.values.first()
        val entry = sentenceEntries.first { it.id == "sentence:${sentence.id}:tts" }
        assertEquals(sentence.tts, entry.originalText)
    }

    @Test
    fun everyRoundHasAPromptTtsEntry() {
        val expectedPromptCount = pack.tasks.values.sumOf { it.rounds.size }
        val promptEntries = entries.count { it.id.endsWith(":promptTts") }
        assertEquals(expectedPromptCount, promptEntries)
    }

    @Test
    fun soundPositionSpecsExposePhonemeTtsAndRoundMissTts() {
        val spec = pack.tasks.values.filterIsInstance<SoundPositionSpec>().first()
        val phonemeEntry = entries.first { it.id == "task:${spec.id}:phonemeTts" }
        assertEquals(spec.phonemeTts, phonemeEntry.originalText)

        val missEntry = entries.first { it.id == "task:${spec.id}:round:0:missTts" }
        assertEquals(spec.rounds.first().missTts, missEntry.originalText)
    }

    @Test
    fun idsAreUnique() {
        assertEquals(entries.size, entries.map { it.id }.toSet().size)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.debug.TtsDebugEntryTest"`
Expected: FAIL to compile — `TtsDebugGroup`, `TtsDebugEntry`, and
`ContentPack.ttsDebugEntries()` don't exist yet.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/app/abcvorschule/debug/TtsDebugEntry.kt`:

```kotlin
package app.abcvorschule.debug

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SoundPositionSpec
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.content.rounds

enum class TtsDebugGroup { Atom, Sentence, Task }

/** One content-authored string the app can pass to `SpeechController.speak`. */
data class TtsDebugEntry(
    val id: String,
    val group: TtsDebugGroup,
    val label: String,
    val originalText: String,
    val sourceFile: String,
)

fun ContentPack.ttsDebugEntries(): List<TtsDebugEntry> {
    val entries = mutableListOf<TtsDebugEntry>()

    atoms.values.sortedBy { it.id }.forEach { atom ->
        entries += TtsDebugEntry(
            id = "atom:${atom.id}:lemma",
            group = TtsDebugGroup.Atom,
            label = "${atom.display} (${atom.kind})",
            originalText = atom.lemma,
            sourceFile = "atoms.json",
        )
    }

    sentences.values.sortedBy { it.id }.forEach { sentence ->
        entries += TtsDebugEntry(
            id = "sentence:${sentence.id}:tts",
            group = TtsDebugGroup.Sentence,
            label = sentence.id,
            originalText = sentence.tts,
            sourceFile = "sentences.json",
        )
    }

    tasks.values.sortedBy { it.id }.forEach { task ->
        if (task is SoundPositionSpec) {
            entries += TtsDebugEntry(
                id = "task:${task.id}:phonemeTts",
                group = TtsDebugGroup.Task,
                label = "${task.id} · phonemeTts",
                originalText = task.phonemeTts,
                sourceFile = "tasks.json",
            )
        }
        task.rounds.forEachIndexed { index, round ->
            val roundNumber = index + 1
            entries += TtsDebugEntry(
                id = "task:${task.id}:round:$index:promptTts",
                group = TtsDebugGroup.Task,
                label = "${task.id} · round $roundNumber · promptTts",
                originalText = round.promptTts,
                sourceFile = "tasks.json",
            )
            when (round) {
                is SoundPositionRound -> entries += TtsDebugEntry(
                    id = "task:${task.id}:round:$index:missTts",
                    group = TtsDebugGroup.Task,
                    label = "${task.id} · round $roundNumber · missTts",
                    originalText = round.missTts,
                    sourceFile = "tasks.json",
                )
                is LetterTraceRound -> entries += TtsDebugEntry(
                    id = "task:${task.id}:round:$index:rewardTts",
                    group = TtsDebugGroup.Task,
                    label = "${task.id} · round $roundNumber · rewardTts",
                    originalText = round.rewardTts,
                    sourceFile = "tasks.json",
                )
                is SyllableMergeRound -> entries += TtsDebugEntry(
                    id = "task:${task.id}:round:$index:stretchTts",
                    group = TtsDebugGroup.Task,
                    label = "${task.id} · round $roundNumber · stretchTts",
                    originalText = round.stretchTts,
                    sourceFile = "tasks.json",
                )
                else -> Unit
            }
        }
    }

    return entries
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.debug.TtsDebugEntryTest"`
Expected: PASS (5 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/debug/TtsDebugEntry.kt \
        app/src/test/java/app/abcvorschule/debug/TtsDebugEntryTest.kt
git commit -m "feat(debug): add TTS debug entry model and content-pack walker"
```

---

### Task 2: Override persistence + export file repository

**Files:**
- Create: `app/src/main/java/app/abcvorschule/debug/TtsDebugRepository.kt`
- Test: `app/src/test/java/app/abcvorschule/debug/TtsDebugRepositoryTest.kt`

**Interfaces:**
- Consumes: `TtsDebugEntry` from Task 1 (`id`, `originalText`, `sourceFile`).
- Produces: `TtsDebugRepository(dataStore: DataStore<Preferences>, exportFile: File)`,
  `overridesFlow: Flow<Map<String, String>>`, `suspend fun current(): Map<String, String>`,
  `suspend fun setOverride(id: String, text: String, entries: List<TtsDebugEntry>)`,
  `suspend fun clearOverride(id: String, entries: List<TtsDebugEntry>)`,
  `suspend fun clearAll()`, `companion object { fun fromContext(context: Context): TtsDebugRepository }`.
  Used by Task 3 (`AbcApplication`) and Task 4 (`TtsDebugScreen`).

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/abcvorschule/debug/TtsDebugRepositoryTest.kt`:

```kotlin
package app.abcvorschule.debug

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same in-memory fake used by ProgressRepositoryTest, duplicated locally. */
private class FakePreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    override val data: Flow<Preferences> = state.asStateFlow()

    override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
        val updated = transform(state.value)
        state.value = updated
        return updated
    }
}

class TtsDebugRepositoryTest {
    private val sampleEntries = listOf(
        TtsDebugEntry(
            id = "atom:letter-m:lemma",
            group = TtsDebugGroup.Atom,
            label = "M (letter)",
            originalText = "M",
            sourceFile = "atoms.json",
        ),
    )

    private fun newRepository(exportFile: File) =
        TtsDebugRepository(FakePreferencesDataStore(), exportFile)

    private fun tempExportFile(): File {
        val file = File.createTempFile("tts_debug_export", ".json")
        file.deleteOnExit()
        return file
    }

    @Test
    fun setOverridePersistsAndWritesExportFile() = runTest {
        val exportFile = tempExportFile()
        val repository = newRepository(exportFile)

        repository.setOverride("atom:letter-m:lemma", "Emm", sampleEntries)

        assertEquals(mapOf("atom:letter-m:lemma" to "Emm"), repository.current())
        val exported = exportFile.readText()
        assertTrue(exported.contains("\"editedText\":\"Emm\""))
        assertTrue(exported.contains("\"originalText\":\"M\""))
        assertTrue(exported.contains("\"sourceFile\":\"atoms.json\""))
    }

    @Test
    fun clearOverrideRemovesEntryAndUpdatesExportFile() = runTest {
        val exportFile = tempExportFile()
        val repository = newRepository(exportFile)
        repository.setOverride("atom:letter-m:lemma", "Emm", sampleEntries)

        repository.clearOverride("atom:letter-m:lemma", sampleEntries)

        assertTrue(repository.current().isEmpty())
        assertEquals("[]", exportFile.readText())
    }

    @Test
    fun clearAllEmptiesOverridesAndExportFile() = runTest {
        val exportFile = tempExportFile()
        val repository = newRepository(exportFile)
        repository.setOverride("atom:letter-m:lemma", "Emm", sampleEntries)

        repository.clearAll()

        assertTrue(repository.current().isEmpty())
        assertEquals("[]", exportFile.readText())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.debug.TtsDebugRepositoryTest"`
Expected: FAIL to compile — `TtsDebugRepository` doesn't exist yet.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/app/abcvorschule/debug/TtsDebugRepository.kt`:

```kotlin
package app.abcvorschule.debug

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.ttsDebugDataStore: DataStore<Preferences> by preferencesDataStore(name = "tts_debug")

@Serializable
data class TtsDebugExportEntry(
    val id: String,
    val sourceFile: String,
    val originalText: String,
    val editedText: String,
)

class TtsDebugRepository(
    private val dataStore: DataStore<Preferences>,
    private val exportFile: File,
) {
    private val json = Json { encodeDefaults = true }
    private val key = stringPreferencesKey("tts_debug_overrides_v1")

    val overridesFlow: Flow<Map<String, String>> = dataStore.data.map { prefs -> currentFrom(prefs) }

    suspend fun current(): Map<String, String> = overridesFlow.first()

    suspend fun setOverride(id: String, text: String, entries: List<TtsDebugEntry>) {
        val updated = updateOverrides { it + (id to text) }
        writeExportFile(entries, updated)
    }

    suspend fun clearOverride(id: String, entries: List<TtsDebugEntry>) {
        val updated = updateOverrides { it - id }
        writeExportFile(entries, updated)
    }

    suspend fun clearAll() {
        updateOverrides { emptyMap() }
        exportFile.writeText("[]")
    }

    private suspend fun updateOverrides(
        transform: (Map<String, String>) -> Map<String, String>,
    ): Map<String, String> {
        var result = emptyMap<String, String>()
        dataStore.edit { prefs ->
            result = transform(currentFrom(prefs))
            prefs[key] = json.encodeToString(result)
        }
        return result
    }

    private fun writeExportFile(entries: List<TtsDebugEntry>, overrides: Map<String, String>) {
        val byId = entries.associateBy { it.id }
        val export = overrides.mapNotNull { (id, editedText) ->
            byId[id]?.let { entry ->
                TtsDebugExportEntry(
                    id = id,
                    sourceFile = entry.sourceFile,
                    originalText = entry.originalText,
                    editedText = editedText,
                )
            }
        }
        exportFile.writeText(json.encodeToString(export))
    }

    private fun currentFrom(prefs: Preferences): Map<String, String> =
        prefs[key]?.let { decode(it) } ?: emptyMap()

    private fun decode(raw: String): Map<String, String> =
        runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())

    companion object {
        fun fromContext(context: Context): TtsDebugRepository = TtsDebugRepository(
            dataStore = context.ttsDebugDataStore,
            exportFile = File(context.filesDir, "tts_debug_export.json"),
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.debug.TtsDebugRepositoryTest"`
Expected: PASS (3 tests).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/debug/TtsDebugRepository.kt \
        app/src/test/java/app/abcvorschule/debug/TtsDebugRepositoryTest.kt
git commit -m "feat(debug): add TTS debug override repository with export file"
```

---

### Task 3: Wire repository into AbcApplication

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/AbcApplication.kt`

**Interfaces:**
- Consumes: `TtsDebugRepository.fromContext(context)` from Task 2.
- Produces: `AbcApplication.ttsDebugRepository: TtsDebugRepository` — consumed
  by Task 5 (`MainActivity.kt`'s `AbcApp`).

No automated test — this file has no existing unit test coverage (it's a thin
`Application` subclass wiring three repositories; `ContentRepository`/
`ProgressRepository` aren't tested here either, only through their own
classes). Verify by compiling.

- [ ] **Step 1: Modify `AbcApplication.kt`**

```kotlin
package app.abcvorschule

import android.app.Application
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.debug.TtsDebugRepository
import app.abcvorschule.progress.ProgressRepository

class AbcApplication : Application() {
    lateinit var contentRepository: ContentRepository
        private set
    lateinit var progressRepository: ProgressRepository
        private set
    lateinit var ttsDebugRepository: TtsDebugRepository
        private set

    override fun onCreate() {
        super.onCreate()
        contentRepository = ContentRepository.fromContext(this)
        progressRepository = ProgressRepository.fromContext(this)
        ttsDebugRepository = TtsDebugRepository.fromContext(this)
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/abcvorschule/AbcApplication.kt
git commit -m "feat(debug): instantiate TtsDebugRepository in AbcApplication"
```

---

### Task 4: TTS debug screen UI

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/debug/TtsDebugScreen.kt`

**Interfaces:**
- Consumes: `ContentPack` (Task 1's `ttsDebugEntries()` extension),
  `TtsDebugRepository` (Task 2: `overridesFlow`, `setOverride`,
  `clearOverride`, `clearAll`), `AbcCloseButton(onClick: () -> Unit)` and
  `AbcSpeakerButton(enabled: Boolean, speaking: Boolean, onClick: () -> Unit)`
  from `app.abcvorschule.ui.components.AbcButtons.kt`.
- Produces: `@Composable fun TtsDebugScreen(pack: ContentPack, repository: TtsDebugRepository, ttsAvailable: Boolean, speaking: Boolean, onSpeak: (String) -> Unit, onClose: () -> Unit, modifier: Modifier = Modifier)`
  — consumed by Task 5 (`MainActivity.kt`).

No automated test — this codebase has no Compose UI test harness for JVM unit
tests (only `androidTest`/Espresso, which needs a connected device/emulator
and isn't wired into `testDebugUnitTest`). Verify by compiling, then manually
in Task 5's end-to-end check once wired up.

- [ ] **Step 1: Create the screen**

Create `app/src/main/java/app/abcvorschule/ui/debug/TtsDebugScreen.kt`:

```kotlin
package app.abcvorschule.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.abcvorschule.content.ContentPack
import app.abcvorschule.debug.TtsDebugEntry
import app.abcvorschule.debug.TtsDebugGroup
import app.abcvorschule.debug.TtsDebugRepository
import app.abcvorschule.debug.ttsDebugEntries
import app.abcvorschule.ui.components.AbcCloseButton
import app.abcvorschule.ui.components.AbcSpeakerButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.NightPanel
import app.abcvorschule.ui.theme.SoftGold
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky
import kotlinx.coroutines.launch

@Composable
fun TtsDebugScreen(
    pack: ContentPack,
    repository: TtsDebugRepository,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val entries = remember(pack) { pack.ttsDebugEntries() }
    val overrides by repository.overridesFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    var query by remember { mutableStateOf("") }

    val filtered = remember(entries, query) {
        if (query.isBlank()) {
            entries
        } else {
            entries.filter {
                it.id.contains(query, ignoreCase = true) ||
                    it.label.contains(query, ignoreCase = true) ||
                    it.originalText.contains(query, ignoreCase = true)
            }
        }
    }
    val grouped = remember(filtered) { filtered.groupBy { it.group } }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(NightInk)
            .padding(horizontal = AbcDimens.screenHorizontal, vertical = 8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = "TTS Debug", style = MaterialTheme.typography.titleLarge, color = SoftSand)
            AbcCloseButton(onClick = onClose)
        }

        Spacer(Modifier.height(AbcDimens.chromeGap))

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("tts_debug_search"),
            singleLine = true,
            placeholder = { Text("Suche…") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = SoftSky,
                unfocusedBorderColor = SoftSky.copy(alpha = 0.5f),
                focusedTextColor = SoftSand,
                unfocusedTextColor = SoftSand,
            ),
        )

        Spacer(Modifier.height(AbcDimens.chromeGap))

        TextButton(onClick = { scope.launch { repository.clearAll() } }) {
            Text("Alles zurücksetzen", color = MutedText)
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = AbcDimens.screenBottomExtra),
        ) {
            TtsDebugGroup.entries.forEach { group ->
                val groupEntries = grouped[group].orEmpty()
                if (groupEntries.isEmpty()) return@forEach
                item(key = "header_$group") {
                    Text(
                        text = groupTitle(group),
                        style = MaterialTheme.typography.labelLarge,
                        color = MutedText,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(groupEntries, key = { it.id }) { entry ->
                    TtsDebugRow(
                        entry = entry,
                        overrideText = overrides[entry.id],
                        ttsAvailable = ttsAvailable,
                        speaking = speaking,
                        onSpeak = onSpeak,
                        onEdit = { newText ->
                            scope.launch { repository.setOverride(entry.id, newText, entries) }
                        },
                        onReset = {
                            scope.launch { repository.clearOverride(entry.id, entries) }
                        },
                    )
                }
            }
        }
    }
}

private fun groupTitle(group: TtsDebugGroup): String = when (group) {
    TtsDebugGroup.Atom -> "Atome"
    TtsDebugGroup.Sentence -> "Sätze"
    TtsDebugGroup.Task -> "Aufgaben"
}

@Composable
private fun TtsDebugRow(
    entry: TtsDebugEntry,
    overrideText: String?,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onEdit: (String) -> Unit,
    onReset: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentText = overrideText ?: entry.originalText
    var editing by remember(entry.id) { mutableStateOf(false) }
    var draft by remember(entry.id, currentText) { mutableStateOf(currentText) }

    Surface(
        modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = NightPanel,
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = entry.label, style = MaterialTheme.typography.labelMedium, color = MutedText)
                    if (overrideText != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(text = "bearbeitet", style = MaterialTheme.typography.labelSmall, color = SoftGold)
                    }
                }
                Spacer(Modifier.height(2.dp))
                if (editing) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { focus ->
                                if (!focus.isFocused) {
                                    editing = false
                                    if (draft != currentText) onEdit(draft)
                                }
                            },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = SoftSky,
                            unfocusedBorderColor = SoftSky.copy(alpha = 0.5f),
                            focusedTextColor = SoftSand,
                            unfocusedTextColor = SoftSand,
                        ),
                    )
                } else {
                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = SoftSand,
                        modifier = Modifier.clickable {
                            draft = currentText
                            editing = true
                        },
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            if (overrideText != null) {
                FilledTonalIconButton(
                    onClick = onReset,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    ),
                ) {
                    Text("↺", color = SoftSand)
                }
                Spacer(Modifier.width(4.dp))
            }

            AbcSpeakerButton(
                enabled = ttsAvailable,
                speaking = speaking,
                onClick = { onSpeak(currentText) },
            )
        }
    }
}
```

- [ ] **Step 2: Compile**

Run: `./gradlew :app:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/debug/TtsDebugScreen.kt
git commit -m "feat(debug): add TTS debug screen with search, inline edit, playback"
```

---

### Task 5: Entry point wiring (Path screen → TaskShell → AbcApp) + BuildConfig gating

**Files:**
- Modify: `app/build.gradle.kts` (enable `buildConfig`)
- Modify: `app/src/main/java/app/abcvorschule/ui/path/PathScreen.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt`
- Modify: `app/src/main/java/app/abcvorschule/MainActivity.kt`

**Interfaces:**
- Consumes: `TtsDebugScreen` (Task 4), `AbcApplication.ttsDebugRepository`
  (Task 3), `PathScreen`'s existing params, `TaskShell`'s existing params.
- Produces: fully wired feature — no further tasks depend on this one.

No automated test (Compose UI wiring, same rationale as Task 4). Verify by
building and manually exercising the flow on a device/emulator.

- [ ] **Step 1: Enable BuildConfig generation**

In `app/build.gradle.kts`, change:

```kotlin
    buildFeatures {
        compose = true
    }
```

to:

```kotlin
    buildFeatures {
        compose = true
        buildConfig = true
    }
```

- [ ] **Step 2: Add the entry-point button to `PathScreen.kt`**

Add one import near the other `app.abcvorschule.*` imports:

```kotlin
import app.abcvorschule.BuildConfig
```

Add a new parameter to the `PathScreen` signature (`PathScreen.kt:66-75`):

```kotlin
@Composable
fun PathScreen(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    highlightedLessonId: String?,
    points: Int,
    onOpenLesson: (String) -> Unit,
    onLockedTap: () -> Unit,
    onParentGateUnlocked: () -> Unit,
    onOpenTtsDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

Insert this block between the closing `}` of the `Box(...)` (currently
`PathScreen.kt:139`) and the closing `}` of the outer `Column` (currently
`PathScreen.kt:140`):

```kotlin
        if (BuildConfig.DEBUG) {
            Text(
                text = "TTS Debug",
                style = MaterialTheme.typography.labelLarge,
                color = MutedText,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 8.dp)
                    .clickable(onClick = onOpenTtsDebug)
                    .testTag("tts_debug_entry"),
            )
        }
```

(`clickable`, `testTag`, `Alignment`, `MaterialTheme`, `MutedText` are already
imported in this file.)

- [ ] **Step 3: Thread the callback through `TaskShell.kt`**

Add a parameter to the `TaskShell` signature (`TaskShell.kt:51-61`), after
`onStopSpeak`:

```kotlin
fun TaskShell(
    state: SessionUiState,
    pack: ContentPack?,
    viewModel: SessionViewModel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onSpeakAndAwait: suspend (String) -> Unit,
    onStopSpeak: () -> Unit,
    onOpenTtsDebug: () -> Unit,
    modifier: Modifier = Modifier,
) {
```

In the `state.screen == AppScreen.Path ->` branch (`TaskShell.kt:100-116`),
add the new argument to the `PathScreen(...)` call:

```kotlin
                PathScreen(
                    lessons = viewModel.pathLessons(),
                    states = viewModel.lessonStates(),
                    highlightedLessonId = viewModel.highlightedLessonId(),
                    points = state.points,
                    onOpenLesson = { viewModel.openLesson(it) },
                    onLockedTap = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        playBlockedBlip()
                        if (ttsAvailable) onSpeak(viewModel.lockedLessonCue())
                    },
                    onParentGateUnlocked = viewModel::openDifficultySheet,
                    onOpenTtsDebug = onOpenTtsDebug,
                    modifier = Modifier.fillMaxSize(),
                )
```

- [ ] **Step 4: Toggle the screen in `MainActivity.kt`**

Replace the file's imports and `AbcApp` body with:

```kotlin
package app.abcvorschule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.abcvorschule.session.SessionViewModel
import app.abcvorschule.speech.SpeechController
import app.abcvorschule.ui.debug.TtsDebugScreen
import app.abcvorschule.ui.shell.TaskShell
import app.abcvorschule.ui.theme.AbcTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AbcTheme {
                AbcApp(onFinish = { finish() })
            }
        }
    }
}

@Composable
fun AbcApp(onFinish: () -> Unit = {}) {
    val context = LocalContext.current
    val app = context.applicationContext as AbcApplication
    val speech = remember { SpeechController(context) }
    DisposableEffect(speech) {
        onDispose { speech.shutdown() }
    }

    val viewModel: SessionViewModel = viewModel(
        factory = SessionViewModel.factory(app.contentRepository, app.progressRepository),
    )
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val ttsAvailable by speech.available.collectAsStateWithLifecycle()
    val speaking by speech.speaking.collectAsStateWithLifecycle()
    var showTtsDebug by remember { mutableStateOf(false) }

    BackHandler {
        if (viewModel.onBackPressed()) {
            onFinish()
        }
    }
    BackHandler(enabled = showTtsDebug) {
        showTtsDebug = false
    }

    val pack = viewModel.contentPack()
    if (showTtsDebug && pack != null) {
        TtsDebugScreen(
            pack = pack,
            repository = app.ttsDebugRepository,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeak = speech::speak,
            onClose = { showTtsDebug = false },
        )
    } else {
        TaskShell(
            state = state,
            pack = pack,
            viewModel = viewModel,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeak = speech::speak,
            onSpeakAndAwait = speech::speakAndAwait,
            onStopSpeak = speech::stop,
            onOpenTtsDebug = { showTtsDebug = true },
        )
    }
}
```

- [ ] **Step 5: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Manual end-to-end verification**

Install the debug build on a device/emulator with a German TTS voice
available, then:

1. Open the app → confirm a "TTS Debug" text button appears below the path
   nodes.
2. Tap it → the TTS Debug screen opens, listing Atome/Sätze/Aufgaben groups.
3. Tap a speaker icon on an unedited entry → hear the original text.
4. Tap an entry's text, edit it, tap elsewhere to commit → the row shows a
   "bearbeitet" badge; tap its speaker icon → hear the edited text
   immediately.
5. Force-close and reopen the app, reopen the debug screen → the edit and its
   badge are still present.
6. Run, from a shell with the device connected:
   `adb shell run-as app.abcvorschule cat files/tts_debug_export.json` →
   confirm it contains the edited entry's `id`/`sourceFile`/`originalText`/`editedText`.
7. Tap the reset (↺) icon on the edited row → it reverts to the original text
   and the badge disappears.
8. Make another edit, then tap "Alles zurücksetzen" → all edits clear;
   re-running the `adb shell run-as` command from step 6 shows `[]`.
9. Use the search field to filter by a known atom id (e.g. `letter-m`) →
   only matching rows remain across all groups.
10. Press the hardware/gesture back button while the debug screen is open →
    it closes back to the Path screen (does not exit the app).
11. Build a release build (`./gradlew :app:assembleRelease`) and confirm (by
    reading the generated APK's resources, or by installing it) that the "TTS
    Debug" button and screen are absent — `BuildConfig.DEBUG` is `false`.

- [ ] **Step 7: Commit**

```bash
git add app/build.gradle.kts \
        app/src/main/java/app/abcvorschule/ui/path/PathScreen.kt \
        app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt \
        app/src/main/java/app/abcvorschule/MainActivity.kt
git commit -m "feat(debug): wire TTS debug screen entry point behind BuildConfig.DEBUG"
```
