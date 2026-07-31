# TTS Debug Page

## Problem

Every trainer speaks German text authored in the content pack (`atoms.json`,
`sentences.json`, `tasks.json`), plus a few hardcoded Kotlin phrases. There is no
way to browse, hear, or trial-edit these strings without navigating to the exact
lesson/round in the app and hoping the TTS voice pronounces them the way the
content author intended (`677cb5a` already fixed one such German-vs-English
mispronunciation). Testing every string means playing through the whole
curriculum by hand.

## Goal

- A debug-only screen listing every content-authored TTS string in the app.
- Each string has a speaker button to hear it played immediately.
- Tapping a string's text turns it into an editable field; edits play back
  immediately through the same speaker button (no separate save step).
- Edits persist locally on the device across app restarts, and are written to an
  export file that a developer can pull off the device via `adb` and merge back
  into the content-pack JSON files in this repo.
- Entry point: a plain, always-visible (no long-press gate) button at the very
  bottom of the Path (home) screen, present only in debug builds.

## Out of scope

- Hardcoded Kotlin TTS strings (`PraisePhrases`, `MathHinting` miss feedback,
  `SessionViewModel.lockedLessonCue`/`missCueForCurrent` fallback) — only
  content-JSON-authored strings (`Atom.lemma`, `Sentence.tts`, and the
  `TrainerRound`/`TaskSpec` `*Tts` fields) are listed.
- Any effect on real lessons/trainers — edits are only ever read by the debug
  screen itself. `ContentRepository`/`ContentPack` used by actual gameplay is
  untouched.
- A separate content repository — there isn't one; edits are meant to land back
  in this same repo's `app/src/main/assets/content/*.json` files.
- Parent-gate long-press protection on the entry button.
- Automating the on-device → repo transfer end-to-end — the export file is
  written on-device; pulling it and applying it to the JSON assets is a manual,
  developer-assisted step (see "Transfer workflow" below), not a button in the
  app.
- Release-build availability — the feature does not exist outside debug builds.

## Design

### Identifying every TTS string

Each spoken string gets a stable string ID built from where it lives in the
content pack:

- `atom:<atomId>:lemma`
- `sentence:<sentenceId>:tts`
- `task:<taskId>:round:<index>:promptTts` (every round, every trainer type)
- `task:<taskId>:phonemeTts` (spec-level, `SoundPositionSpec` only)
- `task:<taskId>:round:<index>:missTts` (`SoundPositionRound`)
- `task:<taskId>:round:<index>:rewardTts` (`LetterTraceRound`)
- `task:<taskId>:round:<index>:stretchTts` (`SyllableMergeRound`)

`CountAddRound.spokenAnswer(...)` is derived at runtime (not authored text) and
is excluded.

### `debug/TtsDebugEntry.kt` (new)

Pure function(s) over an already-loaded `ContentPack` that walk `atoms`,
`sentences`, and `tasks` (using the existing `TaskSpec.kind`/`.rounds` helpers in
`TaskSpecs.kt` to stay exhaustive across trainer types) and produce a flat
`List<TtsDebugEntry>`:

```kotlin
data class TtsDebugEntry(
    val id: String,
    val group: TtsDebugGroup, // Atom, Sentence, Task
    val label: String,        // short human context, e.g. "M (letter)" or "sound_position-3 · round 2 · missTts"
    val originalText: String,
    val sourceFile: String,   // "atoms.json" | "sentences.json" | "tasks.json"
)
```

Recomputed from the live `ContentPack` each time the debug screen opens, so
`originalText` always reflects the current repo content.

### `debug/TtsDebugRepository.kt` (new)

Follows the `ProgressRepository` DataStore pattern:

- New DataStore (`preferencesDataStore(name = "tts_debug")`), single
  `stringPreferencesKey("tts_debug_overrides_v1")` holding a JSON-encoded
  `Map<String, String>` (id → edited text).
- `overridesFlow: Flow<Map<String, String>>`
- `suspend fun setOverride(id: String, text: String)` — updates the map, then
  calls `writeExportFile(...)`.
- `suspend fun clearOverride(id: String)` — removes one entry, rewrites export.
- `suspend fun clearAll()` — empties the map and deletes/empties the export file.
- `private fun writeExportFile(entries: List<TtsDebugEntry>, overrides: Map<String, String>)`
  — writes `context.filesDir/tts_debug_export.json`, a JSON array of only the
  *edited* entries:
  ```json
  [
    {
      "id": "atom:letter-m:lemma",
      "sourceFile": "atoms.json",
      "originalText": "M",
      "editedText": "Emm"
    }
  ]
  ```
  This file is what gets pulled off the device (see "Transfer workflow").
- `companion object fromContext(context)` factory, instantiated once in
  `AbcApplication.kt` alongside `contentRepository`/`progressRepository`.

### `ui/debug/TtsDebugScreen.kt` (new)

Full-screen Composable, not gated behind a bottom sheet:

- Top row: title, search `TextField` (filters by id/label/text — several
  hundred entries need this), close button (`AbcCloseButton`, same as the
  in-lesson close pattern).
- Body: `LazyColumn` grouped by `TtsDebugGroup` (Atome / Sätze / Aufgaben) with
  sticky section headers.
- Each row:
  - Small muted `id`/`label` text.
  - The text itself: `Text` by default; tapping it swaps in a `TextField`
    (`remember { mutableStateOf(...) }` per row) that commits on focus-loss via
    `repository.setOverride(id, text)`.
  - Speaker `IconButton`: always speaks the current value shown in the row
    (edited text if present, else `originalText`) via the same `onSpeak: (String) -> Unit`
    already threaded through the rest of the app.
  - If an override exists for this id: a small "bearbeitet" badge + a reset
    (↺) `IconButton` calling `repository.clearOverride(id)`.
- Bottom or top-bar action: "Alles zurücksetzen" — confirms, then calls
  `repository.clearAll()`. Useful once edits have been merged into the repo.

### Wiring the entry point

- `PathScreen.kt`: add parameter `onOpenTtsDebug: () -> Unit`. Below the
  existing scrollable path `Box` (after line 139, inside the outer `Column`,
  same non-scrolling chrome area as the top row), add a small text button
  ("TTS Debug") calling `onOpenTtsDebug`. No long-press gate — a plain
  `clickable`/`TextButton`.
- `TaskShell.kt`: add `onOpenTtsDebug: () -> Unit` parameter, pass through to
  the `PathScreen(...)` call.
- `MainActivity.kt` (`AbcApp`): add `var showTtsDebug by remember { mutableStateOf(false) }`.
  Pass `onOpenTtsDebug = { showTtsDebug = true }` into `TaskShell`. Render:
  ```kotlin
  if (showTtsDebug) {
      TtsDebugScreen(
          pack = viewModel.contentPack(),
          repository = app.ttsDebugRepository,
          ttsAvailable = ttsAvailable,
          onSpeak = speech::speak,
          onClose = { showTtsDebug = false },
      )
  } else {
      TaskShell(...)
  }
  ```
  `BackHandler` inside `TtsDebugScreen` (or a second `BackHandler` in `AbcApp`
  scoped to `showTtsDebug`) closes back to `TaskShell` instead of finishing the
  activity.
- Debug-build gating: the button and the `if (showTtsDebug)` branch are both
  wrapped in `if (BuildConfig.DEBUG)`. In release builds neither the button nor
  the screen exist in the composed tree.
- `AbcApplication.kt`: instantiate `ttsDebugRepository = TtsDebugRepository.fromContext(this)`
  alongside the other two repositories.

### Transfer workflow (device → repo)

1. Developer edits/tests strings in `TtsDebugScreen` on a connected device or
   emulator; every edit auto-writes `tts_debug_export.json` on-device.
2. Developer (via Claude Code or manually) runs, for a debuggable build:
   `adb shell run-as app.abcvorschule cat files/tts_debug_export.json` to read
   the file — no root required since the app is debuggable.
3. The exported JSON's `id`/`sourceFile`/`editedText` fields are enough to
   locate and patch the corresponding value in `app/src/main/assets/content/{atoms,sentences,tasks}.json` (by walking the same id scheme back to an atom id / sentence id / task id + round index + field name).
4. Developer reviews the resulting git diff and commits normally. The content
   JSON files are mirrored under `app/src/test/resources/content/` for JVM unit
   tests (`ContentRepositoryTest`, `ContentValidatorTest`, etc.) — the same edit
   must be applied to both copies, or those tests will keep exercising the old
   text.
5. Developer taps "Alles zurücksetzen" in the debug screen to clear overrides
   and the export file once merged, so the next debug session starts clean.

## Testing

- Manual: open the debug screen from the Path screen bottom button (debug
  build only — confirm the button is entirely absent in a release build).
- Manual: tap a speaker icon on an unedited atom/sentence/task string → hears
  the original content-pack text.
- Manual: edit a string's text, tap its speaker icon → hears the edited text
  immediately, no save step.
- Manual: force-close and reopen the app → the edit is still shown (persisted
  via DataStore) and still marked as edited.
- Manual: pull `tts_debug_export.json` via `adb shell run-as` and confirm it
  contains exactly the edited entries with correct `id`/`sourceFile`/`originalText`/`editedText`.
- Manual: "Alles zurücksetzen" → all edits revert to original text and the
  export file empties.
- Manual: search field filters the list by id/label/text across all three
  groups.
