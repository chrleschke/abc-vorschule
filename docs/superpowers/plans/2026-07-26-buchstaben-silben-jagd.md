# Buchstaben-/Silben-Jagd Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reconcile `ContentValidator`/test fixtures with the expanded 18-lesson content pack, then ship a "Finde alle Buchstaben/Silben X" search-and-collect trainer that runs (up to) twice per lesson with zero new authored content.

**Architecture:** Phase 0 (Tasks 1-4) fixes drift between `app/src/main/assets/content/` (expanded, 18 lessons, variable per-lesson trainer counts) and `app/src/test/resources/content/` (stale copy) plus `ContentValidator`'s now-wrong exact-6-trainer rule, replacing it with a monotonic-rank rule. Phase 1 (Tasks 5-12) adds a new `symbol_hunt` `TrainerRound`/`TaskSpec` that is never authored in JSON — its rounds are derived at runtime from each lesson's existing `letter_trace`/`syllable_merge` rounds and spliced into the scheduled trainer list by a pure `SymbolHuntInsertion.insertSymbolHunts` function.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, JUnit4.

**Spec:** `docs/superpowers/specs/2026-07-26-buchstaben-silben-jagd-design.md` — read it once for full rationale; this plan carries every concrete value forward.

## Global Constraints

- Never weaken a test assertion just to make it pass — adapt it to genuinely test the new, correct behavior (spec §0, step 3).
- `symbol_hunt` never appears in authored JSON (`tasks.json`/`lessons.json`) — it is synthesized at runtime only (spec §2).
- Distractor pool sourcing is narrow: only `letter_trace` rounds feed the letter pool, only `syllable_merge` rounds feed the syllable pool — not every `scoredAtomIds()` in the lesson (spec §3).
- Degeneration table: 0 unique distractors → skip the round; 1-2 → 3 hits + (unique × 2) distractor tiles; ≥3 → 5 hits + 6 distractor tiles (spec §3).
- Exactly one miss report per hunt round; further wrong taps reshuffle only (spec §5).
- Resolve threshold is 6 **consecutive** misses, resetting on any correct tap (spec §5) — this differs from the Spurensucher's cumulative off-road counter; only the numeric threshold is reused.
- Battery-full is a local gate: the trainer waits for a tap on `AbcContinueButton` before calling `onResult(true, false, ...)` — it does not auto-advance like other trainers (spec §5).
- Colors only from the existing palette (`SoftMint`, `SoftCoral`, `SoftSky`, `SoftGold`, `SoftSand`) — no new theme colors (spec §4).
- `./gradlew :app:testDebugUnitTest` must stay green after every task.

---

## Task 1: Fix two isolated content-authoring bugs in the expanded pack

The pack expansion (commit `588cf1f`) introduced two small, isolated data bugs that block later tasks and violate an explicit product principle. Both are one-line JSON corrections, not new content authoring.

**Files:**
- Modify: `app/src/main/assets/content/tasks.json`
- Modify: `app/src/main/assets/content/atoms.json`

**Interfaces:** None — pure data files, no code depends on this task beyond later tasks reading the corrected JSON.

- [ ] **Step 1: Remove the stray distractor from `l01-t6`**

In `app/src/main/assets/content/tasks.json`, find the task with `"id": "l01-t6"` (a `word_build` task whose `targetAtomId` is `"mama"`). Its round currently has:

```json
"distractors": [
  {
    "atomId": "ma",
    "display": "mi"
  }
]
```

This is lesson 1's very first `word_build` encounter — Prinzip 2 requires "Die erste Begegnung mit neuem Stoff bleibt distraktorfrei" (the first encounter with new material stays distractor-free), and the tile's `atomId` ("ma") doesn't even match its own `display` ("mi") — a leftover authoring mistake. Delete the `"distractors"` key (and its array) from that round entirely, so the round object becomes:

```json
{
  "promptTts": "Kannst du das Wort Mama bauen? Suche die passenden Bausteine.",
  "targetAtomId": "mama",
  "blocks": [
    {
      "atomId": "ma",
      "display": "Ma"
    },
    {
      "atomId": "ma",
      "display": "ma"
    }
  ]
}
```

- [ ] **Step 2: Fix the "ma" atom's kind**

In `app/src/main/assets/content/atoms.json`, find the atom with `"id": "ma"`. It currently has `"kind": "word"`. Every other merged-syllable atom in the pack (`"mi"`, `"mo"`, `"pa"`, `"to"`, `"la"`, `"ho"`, `"ro"`, `"ru"`, etc.) is tagged `"kind": "syllable"` — "ma" is the sole inconsistent one, and it is exactly the syllable `syllable_merge` produces in lesson 1 (`m` + `a` → `ma`). Change its `"kind"` field from `"word"` to `"syllable"`. No other field on this atom changes.

- [ ] **Step 3: Verify the pack still parses and validates against the OLD validator rule (sanity check only)**

Run:
```bash
python3 -c "
import json
tasks = json.load(open('app/src/main/assets/content/tasks.json'))['tasks']
l01t6 = next(t for t in tasks if t['id'] == 'l01-t6')
assert 'distractors' not in l01t6, l01t6
atoms = json.load(open('app/src/main/assets/content/atoms.json'))['atoms']
ma = next(a for a in atoms if a['id'] == 'ma')
assert ma['kind'] == 'syllable', ma
print('OK')
"
```
Expected: prints `OK`. This is a plain JSON sanity check, not a JVM test — the Kotlin test suite doesn't see these files yet (they get synced to `app/src/test/resources/content/` in Task 3).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/assets/content/tasks.json app/src/main/assets/content/atoms.json
git commit -m "fix(content): remove stray l01-t6 distractor and correct ma atom kind

l01-t6 is lesson 1's first word_build encounter and must stay distractor-
free (Prinzip 2); its one distractor also had a mismatched atomId/display.
The ma atom was tagged kind=word while every other syllable_merge result
atom is kind=syllable — an isolated inconsistency from the pack expansion."
```

---

## Task 2: Replace the exact-6-trainer validator rule with a monotonic-rank rule

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/ContentValidator.kt`
- Modify: `app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt`

**Interfaces:**
- Produces: `ContentValidator.TrainerOrder` stays the same public `List<TrainerKind>` (six entries, still the rank source other code and tests reference) — only the per-lesson validation rule against it changes.

- [ ] **Step 1: Replace the authored-lesson check in `ContentValidator.kt`**

Open `app/src/main/java/app/abcvorschule/content/ContentValidator.kt`. Add a rank map right after the `TrainerOrder` declaration (inside the `ContentValidator` object, before `MinSoundPositionRounds`):

```kotlin
    /** Rank of each kind within [TrainerOrder] — the source of truth for "does this
     * sequence ever go backward", now that authored lessons may repeat or skip a
     * kind instead of holding exactly one of each. */
    private val TrainerRank: Map<TrainerKind, Int> =
        TrainerOrder.withIndex().associate { (index, kind) -> kind to index }
```

Then replace the `LessonStatus.authored ->` branch (currently):

```kotlin
                LessonStatus.authored -> {
                    val kinds = lesson.taskIds.mapNotNull { pack.tasks[it]?.kind }
                    if (kinds != TrainerOrder) {
                        issues += ValidationIssue(
                            "authored lesson ${lesson.id} must hold $TrainerOrder but holds $kinds",
                        )
                    }
                    if (lesson.focusAtomIds.isEmpty()) {
                        issues += ValidationIssue("authored lesson ${lesson.id} needs focusAtomIds")
                    }
                }
```

with:

```kotlin
                LessonStatus.authored -> {
                    val kinds = lesson.taskIds.mapNotNull { pack.tasks[it]?.kind }
                    val ranks = kinds.map { TrainerRank.getValue(it) }
                    val monotonic = ranks.zipWithNext().all { (a, b) -> a <= b }
                    val startsAndEndsRight = kinds.firstOrNull() == TrainerKind.sound_position &&
                        kinds.lastOrNull() == TrainerKind.count_add
                    if (kinds.isEmpty() || !monotonic || !startsAndEndsRight) {
                        issues += ValidationIssue(
                            "authored lesson ${lesson.id} must hold trainer kinds in " +
                                "non-decreasing $TrainerOrder rank, starting with sound_position " +
                                "and ending with count_add, but holds $kinds",
                        )
                    }
                    if (lesson.focusAtomIds.isEmpty()) {
                        issues += ValidationIssue("authored lesson ${lesson.id} needs focusAtomIds")
                    }
                }
```

- [ ] **Step 2: Rewrite the exact-match test and add negative cases in `ContentValidatorTest.kt`**

Replace this test:

```kotlin
    @Test
    fun everyAuthoredLessonHasAllSixTrainersInOrder() {
        pack.authoredLessons.forEach { lesson ->
            assertEquals(
                "lesson ${lesson.id}",
                ContentValidator.TrainerOrder,
                pack.tasksOf(lesson).map { it.kind },
            )
        }
    }
```

with:

```kotlin
    @Test
    fun everyAuthoredLessonHoldsTrainerKindsInNonDecreasingRank() {
        val rank = ContentValidator.TrainerOrder.withIndex().associate { (i, k) -> k to i }
        pack.authoredLessons.forEach { lesson ->
            val kinds = pack.tasksOf(lesson).map { it.kind }
            val ranks = kinds.map { rank.getValue(it) }
            assertTrue("lesson ${lesson.id} kinds $kinds must be non-decreasing rank", ranks.zipWithNext().all { (a, b) -> a <= b })
            assertEquals("lesson ${lesson.id} must start with sound_position", TrainerKind.sound_position, kinds.first())
            assertEquals("lesson ${lesson.id} must end with count_add", TrainerKind.count_add, kinds.last())
        }
    }

    @Test
    fun monotonicOrderAcceptsRepeatedAndSkippedKinds() {
        val lesson = pack.authoredLessons.first()
        val repeatedAndSkipped = listOf(
            TrainerKind.sound_position,
            TrainerKind.sound_position,
            TrainerKind.letter_trace,
            TrainerKind.word_build,
            TrainerKind.count_add,
        )
        val fakeTaskIds = repeatedAndSkipped.mapIndexed { i, kind ->
            val original = pack.tasksOf(lesson).first { it.kind == kind }
            "fake-$i" to original
        }
        val issues = issuesOf {
            it.copy(
                tasks = it.tasks + fakeTaskIds,
                lessons = it.lessons.map { l ->
                    if (l.id == lesson.id) l.copy(taskIds = fakeTaskIds.map { (id, _) -> id }) else l
                },
            )
        }
        assertTrue(issues.none { it.contains("must hold trainer kinds") })
    }

    @Test
    fun backwardJumpInTrainerKindsIsRejected() {
        val lesson = pack.authoredLessons.first()
        val backward = listOf(TrainerKind.word_build, TrainerKind.sound_position, TrainerKind.count_add)
        val fakeTaskIds = backward.mapIndexed { i, kind ->
            val original = pack.tasksOf(lesson).first { it.kind == kind }
            "fake-$i" to original
        }
        val issues = issuesOf {
            it.copy(
                tasks = it.tasks + fakeTaskIds,
                lessons = it.lessons.map { l ->
                    if (l.id == lesson.id) l.copy(taskIds = fakeTaskIds.map { (id, _) -> id }) else l
                },
            )
        }
        assertTrue(issues.any { it.contains("must hold trainer kinds") })
    }
```

Note: `fakeTaskIds` pairs a fresh id with an *existing* `TaskSpec` instance (reusing a real task's content under a new id) — this avoids constructing a whole fake `TaskSpec` by hand while still exercising the kind-sequence rule in isolation.

- [ ] **Step 3: Rewrite `plannedLessonWithTasksIsRejected` to not depend on a real planned lesson**

Replace:

```kotlin
    @Test
    fun plannedLessonWithTasksIsRejected() {
        val lessons = pack.lessons.map { lesson ->
            if (lesson.status == LessonStatus.planned) {
                lesson.copy(taskIds = listOf(pack.tasks.keys.first()))
            } else {
                lesson
            }
        }
        val issues = issuesOf { it.copy(lessons = lessons) }
        assertTrue(issues.any { it.contains("planned lesson") })
    }
```

with:

```kotlin
    @Test
    fun plannedLessonWithTasksIsRejected() {
        // The shipped pack may have zero planned lessons at any given time (it does,
        // post-588cf1f) — mutate one authored lesson to planned-with-tasks instead of
        // relying on a real planned lesson existing.
        val target = pack.authoredLessons.first()
        val lessons = pack.lessons.map { lesson ->
            if (lesson.id == target.id) lesson.copy(status = LessonStatus.planned) else lesson
        }
        val issues = issuesOf { it.copy(lessons = lessons) }
        assertTrue(issues.any { it.contains("planned lesson") })
    }
```

- [ ] **Step 4: Run the content test suite**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.content.ContentValidatorTest"
```
Expected: BUILD SUCCESSFUL, all tests pass (this runs against the OLD, still-small test-resources pack — a strict 6-in-order sequence is trivially a valid special case of "non-decreasing rank, starts sound_position, ends count_add", so this passes before Task 3's resource sync too).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/ContentValidator.kt app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt
git commit -m "fix(content): replace exact-6-trainer validator rule with monotonic rank

The expanded pack's authored lessons repeat or skip trainer kinds (e.g. two
letter_trace tasks, no sentence_order in l03/l12) instead of holding exactly
one of each in a fixed 6-item sequence. The new rule requires non-decreasing
TrainerOrder rank, starting with sound_position and ending with count_add,
which the old sequence trivially still satisfies."
```

---

## Task 3: Sync test resources to the expanded pack and fix the tests that assumed the old shape

**Files:**
- Modify: `app/src/test/resources/content/atoms.json` (replace with copy of main assets)
- Modify: `app/src/test/resources/content/sentences.json` (replace with copy of main assets)
- Modify: `app/src/test/resources/content/lessons.json` (replace with copy of main assets)
- Modify: `app/src/test/resources/content/tasks.json` (replace with copy of main assets)
- Modify: `app/src/test/java/app/abcvorschule/content/ContentRepositoryTest.kt`
- Modify: `app/src/test/java/app/abcvorschule/content/LessonCoverageTest.kt`
- Modify: `app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt` (only the two tests that assumed 6 tasks — `roundCountsMatchTheAuthoredPack` gets touched here; the `SessionProgression.resumeSafe` additions come later in Task 11)

**Interfaces:** None new — this task only makes existing tests match the (already reconciled, as of Task 2) validator and the real pack shape.

- [ ] **Step 1: Copy the four content files**

```bash
cp app/src/main/assets/content/atoms.json app/src/test/resources/content/atoms.json
cp app/src/main/assets/content/sentences.json app/src/test/resources/content/sentences.json
cp app/src/main/assets/content/lessons.json app/src/test/resources/content/lessons.json
cp app/src/main/assets/content/tasks.json app/src/test/resources/content/tasks.json
```

- [ ] **Step 2: Confirm they're now identical**

```bash
diff -q app/src/main/assets/content/atoms.json app/src/test/resources/content/atoms.json
diff -q app/src/main/assets/content/sentences.json app/src/test/resources/content/sentences.json
diff -q app/src/main/assets/content/lessons.json app/src/test/resources/content/lessons.json
diff -q app/src/main/assets/content/tasks.json app/src/test/resources/content/tasks.json
```
Expected: no output from any `diff -q` (identical files).

- [ ] **Step 3: Rewrite `ContentRepositoryTest.kt`**

Replace the whole file with:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRepositoryTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun packLoadsEighteenAuthoredLessons() {
        assertEquals(18, pack.lessons.size)
        assertEquals(18, pack.authoredLessons.size)
    }

    @Test
    fun schemaVersionIsTwo() {
        assertEquals(2, pack.manifest.schemaVersion)
    }

    @Test
    fun lessonOneIsMundA() {
        val lesson = pack.lesson("l01")
        assertEquals(1, lesson.index)
        assertEquals(listOf("letter-m", "letter-a"), lesson.focusAtomIds)
        assertTrue(lesson.taskIds.isNotEmpty())
    }

    @Test
    fun polymorphicTasksDeserializeToTheirTrainerType() {
        val tasks = pack.tasksOf(pack.lesson("l01"))
        assertTrue(tasks.first() is SoundPositionSpec)
        assertTrue(tasks.last() is CountAddSpec)
        assertTrue(tasks.any { it is LetterTraceSpec })
    }

    @Test
    fun atomsAreSharedAcrossTrainers() {
        // AE6 in new clothes: one atom, one emoji, reused by several trainers.
        val ma = pack.atom("ma")
        assertEquals("ma", ma.display)
        val merge = pack.tasksOf(pack.lesson("l01")).filterIsInstance<SyllableMergeSpec>().first()
        val build = pack.tasksOf(pack.lesson("l01")).filterIsInstance<WordBuildSpec>()
            .first { spec -> spec.rounds.any { it.blocks.any { block -> block.atomId == "ma" } } }
        assertEquals("ma", merge.rounds.first().resultAtomId)
        assertTrue(build.rounds.any { it.blocks.any { it.atomId == "ma" } })
    }

    @Test
    fun traceRoundsResolveStrokeDataFromAtoms() {
        pack.tasksOf(pack.lesson("l01")).filterIsInstance<LetterTraceSpec>().forEach { trace ->
            trace.rounds.forEach { round ->
                val atom = pack.atom(round.atomId)
                assertTrue("${atom.id} needs strokes", atom.strokes.isNotEmpty())
            }
        }
        assertNotNull(pack.atom("letter-a").strokes.firstOrNull())
    }

    @Test
    fun countAddRoundsUseLessonContextIcons() {
        pack.tasksOf(pack.lesson("l01")).filterIsInstance<CountAddSpec>().forEach { math ->
            math.rounds.forEach { round ->
                assertTrue(round.iconAtomId in pack.atoms.keys)
                assertTrue(pack.atom(round.iconAtomId).emoji.isNotBlank())
            }
        }
    }
}
```

- [ ] **Step 4: Rewrite `LessonCoverageTest.kt`**

Replace the whole file with:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonCoverageTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun allEighteenLessonsAreAuthoredInPhaseOrder() {
        assertEquals(
            (1..18).map { "l%02d".format(it) },
            pack.authoredLessons.map { it.id },
        )
    }

    @Test
    fun noLessonsStayPlannedInTheExpandedPack() {
        assertTrue(pack.lessons.none { it.status == LessonStatus.planned })
    }

    @Test
    fun everyFocusGraphemeHasTraceStrokesAndATraceRound() {
        pack.authoredLessons.forEach { lesson ->
            val traced = pack.tasksOf(lesson).filterIsInstance<LetterTraceSpec>()
                .flatMap { it.rounds }.map { it.atomId }
            assertEquals(
                "lesson ${lesson.id} must trace exactly its focus graphemes",
                lesson.focusAtomIds,
                traced,
            )
            lesson.focusAtomIds.forEach {
                assertTrue("$it needs strokes", pack.atom(it).strokes.isNotEmpty())
            }
        }
    }

    @Test
    fun rechnenIsPresentInEveryAuthoredLesson() {
        // User decision: Rechnen runs in every lesson for variety.
        pack.authoredLessons.forEach { lesson ->
            val math = pack.tasksOf(lesson).filterIsInstance<CountAddSpec>()
            assertTrue("lesson ${lesson.id} needs count_add", math.isNotEmpty())
            assertTrue(
                "lesson ${lesson.id} needs at least two sums",
                math.sumOf { it.rounds.size } >= 2,
            )
        }
    }

    @Test
    fun rechnenIconsComeFromTheLessonsOwnVocabulary() {
        pack.authoredLessons.forEach { lesson ->
            val math = pack.tasksOf(lesson).filterIsInstance<CountAddSpec>()
            val icons = math.flatMap { it.rounds }.map { it.iconAtomId }.distinct()
            assertEquals("lesson ${lesson.id} should stay on one icon", 1, icons.size)
            assertTrue(pack.atom(icons.single()).emoji.isNotBlank())
        }
    }

    @Test
    fun wordBuilderNeverOffersAnUntaughtGrapheme() {
        // A block may only use graphemes/syllables introduced in this or an earlier lesson.
        val introduced = mutableSetOf<String>()
        pack.authoredLessons.forEach { lesson ->
            introduced += lesson.focusAtomIds
            val merges = pack.tasksOf(lesson).filterIsInstance<SyllableMergeSpec>().flatMap { it.rounds }
            introduced += merges.map { it.resultAtomId }
            pack.tasksOf(lesson).filterIsInstance<WordBuildSpec>().forEach { build ->
                build.rounds.forEach { round ->
                    (round.blocks + round.distractors).forEach { block ->
                        assertTrue(
                            "lesson ${lesson.id} offers ${block.atomId} before it is taught",
                            block.atomId in introduced,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun sentenceRoundsOnlyUseWordsThatWereBuiltOrIntroduced() {
        val known = mutableSetOf<String>()
        pack.authoredLessons.forEach { lesson ->
            pack.tasksOf(lesson).filterIsInstance<WordBuildSpec>().forEach { build ->
                known += build.rounds.map { it.targetAtomId }
            }
            pack.tasksOf(lesson).filterIsInstance<SentenceOrderSpec>().forEach { sentences ->
                sentences.rounds.forEach { round ->
                    pack.sentence(round.sentenceId).atomIds.forEach { atomId ->
                        // Lowercase function words (ist, am, da, das, ruft) are introduced by
                        // the sentence itself; anything else must be built or declared holistic.
                        val functionWord = pack.atom(atomId).display.first().isLowerCase()
                        assertTrue(
                            "lesson ${lesson.id} sentence uses unbuilt word $atomId",
                            atomId in known || functionWord || atomId in round.holisticAtomIds,
                        )
                    }
                }
            }
        }
    }

    @Test
    fun firstWordBuildEncounterPerLessonStaysDistractorFree() {
        pack.authoredLessons.forEach { lesson ->
            val first = pack.tasksOf(lesson).filterIsInstance<WordBuildSpec>().firstOrNull() ?: return@forEach
            assertTrue(
                "lesson ${lesson.id} first word_build task must stay distractor-free",
                first.rounds.all { it.distractors.isEmpty() },
            )
        }
    }
}
```

- [ ] **Step 5: Fix the two hardcoded `6`s in `LessonSessionTest.kt`**

In `app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt`, replace:

```kotlin
    @Test
    fun lessonSessionIsTheSixTrainersInAuthoredOrder() {
        val lesson = pack.authoredLessons.first()
        assertEquals(lesson.taskIds, pack.tasksOf(lesson).map { it.id })
        assertEquals(6, pack.tasksOf(lesson).size)
    }
```

with:

```kotlin
    @Test
    fun lessonSessionIsItsAuthoredTasksInOrder() {
        val lesson = pack.authoredLessons.first()
        assertEquals(lesson.taskIds, pack.tasksOf(lesson).map { it.id })
        assertTrue(pack.tasksOf(lesson).isNotEmpty())
    }
```

(this needs `org.junit.Assert.assertTrue` — already imported in this file) and replace:

```kotlin
    @Test
    fun roundCountsMatchTheAuthoredPack() {
        val counts = pack.tasksOf(pack.authoredLessons.first()).map { it.roundCount }
        assertEquals(6, counts.size)
        assertEquals(emptyList<Int>(), counts.filter { it <= 0 })
    }
```

with:

```kotlin
    @Test
    fun roundCountsMatchTheAuthoredPack() {
        val lesson = pack.authoredLessons.first()
        val counts = pack.tasksOf(lesson).map { it.roundCount }
        assertEquals(lesson.taskIds.size, counts.size)
        assertEquals(emptyList<Int>(), counts.filter { it <= 0 })
    }
```

Leave `mathScaffoldsAreIndependentPerFactWithinOneCountAddTrainer` as-is — it already uses `.filterIsInstance<CountAddSpec>().first()`, which is safe with multiple `count_add` tasks (it just needs the first one to have ≥2 rounds, which lesson 1 does).

- [ ] **Step 6: Fix `LessonGatingTest.kt`'s planned-lesson test**

In `app/src/test/java/app/abcvorschule/progress/LessonGatingTest.kt`, add this import:

```kotlin
import app.abcvorschule.content.Lesson
```

Replace:

```kotlin
    @Test
    fun plannedLessonsReportPlannedRegardlessOfProgress() {
        val planned = pack.lessons.first { it.status == LessonStatus.planned }
        assertEquals(
            LessonState.Planned,
            LessonGating.stateOf(pack, mastering(first.id), planned.id),
        )
        assertFalse(LessonGating.isPlayable(LessonState.Planned))
    }
```

with:

```kotlin
    @Test
    fun plannedLessonsReportPlannedRegardlessOfProgress() {
        // The shipped pack has zero planned lessons at the moment (all 18 are
        // authored) — this business rule still needs coverage independent of the
        // current curriculum state, so it builds its own synthetic planned lesson
        // rather than relying on one existing in the real pack.
        val planned = Lesson(
            id = "l-synthetic-planned",
            index = pack.lessons.size + 1,
            phase = 5,
            title = "Synthetic Planned",
            nodeLabel = "?",
            status = LessonStatus.planned,
        )
        val syntheticPack = pack.copy(lessons = pack.lessons + planned)
        assertEquals(
            LessonState.Planned,
            LessonGating.stateOf(syntheticPack, mastering(first.id), planned.id),
        )
        assertFalse(LessonGating.isPlayable(LessonState.Planned))
    }
```

- [ ] **Step 7: Run the full test suite**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/test/resources/content app/src/test/java/app/abcvorschule/content/ContentRepositoryTest.kt app/src/test/java/app/abcvorschule/content/LessonCoverageTest.kt app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt app/src/test/java/app/abcvorschule/progress/LessonGatingTest.kt
git commit -m "test(content): sync test resources to the expanded pack, fix drifted assertions

app/src/test/resources/content/ was never updated when 588cf1f expanded the
shipped pack to 18 authored lessons with variable per-lesson trainer counts.
Tests that assumed a fixed 6-tasks-per-lesson shape or a specific t1..t6
trainer-type mapping now use kind-based filtering instead of positional ids,
so they hold regardless of how many tasks of a given kind a lesson has."
```

---

## Task 4: Correct the "six trainer" documentation to the monotonic model

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/LessonModels.kt`
- Modify: `docs/PRODUCT_PRINCIPLES.md`
- Modify: `AGENTS.md`

**Interfaces:** None — documentation/comment only.

- [ ] **Step 1: Fix the `Lesson.taskIds` comment**

In `app/src/main/java/app/abcvorschule/content/LessonModels.kt`, replace:

```kotlin
    /** Exactly the six trainers in ContentValidator.TrainerOrder when authored. */
    val taskIds: List<String> = emptyList(),
```

with:

```kotlin
    /** Trainer kinds in non-decreasing ContentValidator.TrainerOrder rank when
     * authored — a kind may repeat or be skipped, but the sequence never goes
     * backward, always starts with sound_position, and always ends with count_add. */
    val taskIds: List<String> = emptyList(),
```

- [ ] **Step 2: Fix `docs/PRODUCT_PRINCIPLES.md` §3**

Replace this sentence in section 3 ("Lernprogression (Fibel-Lernpfad)"):

> Der Lehrplan besteht aus 16 Lektionen in fünf Phasen (Fibel-Reihenfolge). Jede Lektion führt **genau sechs Trainer in fester Reihenfolge** durch:

with:

> Der Lehrplan besteht aus 18 Lektionen in fünf Phasen (Fibel-Reihenfolge). Jede Lektion führt die sechs Trainer-**Typen** unten in fester Rangfolge durch — ein Typ kann sich wiederholen oder ganz fehlen (z. B. keine Satzrunde in einer Lektion), die Reihenfolge geht aber nie zurück; jede Lektion beginnt mit dem Auditiven Finder und endet mit Rechnen:

- [ ] **Step 3: Fix `AGENTS.md`'s "Kind-UI-Regeln (Kurz)" line**

Replace:

> - Sechs Trainer pro Lektion in fester Reihenfolge: Auditiver Finder · Spurensucher · Verschmelzer · Wort-Bauer · Satz-Architekt · Rechnen.

with:

> - Sechs Trainer-**Typen** pro Lektion in fester Rangfolge: Auditiver Finder · Spurensucher · Verschmelzer · Wort-Bauer · Satz-Architekt · Rechnen. Ein Typ kann sich wiederholen oder fehlen, die Reihenfolge geht nie zurück; jede Lektion startet mit Auditiver Finder und endet mit Rechnen (`ContentValidator` erzwingt das).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/LessonModels.kt docs/PRODUCT_PRINCIPLES.md AGENTS.md
git commit -m "docs: correct six-trainer documentation to the monotonic-rank model"
```

---

## Task 5: Add the `symbol_hunt` data model

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/TaskSpecs.kt`
- Modify: `app/src/main/java/app/abcvorschule/content/ContentValidator.kt`
- Create: `app/src/test/java/app/abcvorschule/content/SymbolHuntSpecTest.kt`

**Interfaces:**
- Produces: `SymbolHuntMode` (enum: `letter`, `syllable`), `SymbolHuntSpec(id, rounds)`, `SymbolHuntRound(promptTts, targetAtomId, mode, distractorPool)`, `TrainerKind.symbol_hunt`. These are real `@Serializable` sealed subtypes of `TaskSpec`/`TrainerRound` (kotlinx.serialization requires every sealed-hierarchy member to be `@Serializable`/`@SerialName` even though `symbol_hunt` never appears in authored JSON — see spec §2).

- [ ] **Step 1: Add the new types to `TaskSpecs.kt`**

Add `symbol_hunt` to the `TrainerKind` enum:

```kotlin
enum class TrainerKind {
    sound_position,
    letter_trace,
    syllable_merge,
    word_build,
    sentence_order,
    count_add,
    symbol_hunt,
}
```

Add the new spec/round types at the end of the file, right before `TasksFile`:

```kotlin
// --- Buchstaben-/Silben-Jagd — derived at runtime, never authored --------------

enum class SymbolHuntMode { letter, syllable }

/**
 * Never appears in authored JSON — [SessionViewModel]'s SymbolHuntInsertion
 * derives instances at runtime from a lesson's own letter_trace/syllable_merge
 * rounds (design doc §2). Still `@Serializable`/`@SerialName` because TaskSpec
 * is a kotlinx.serialization sealed hierarchy — every member needs both for the
 * polymorphic parent to compile, even members that are never deserialized.
 */
@Serializable
@SerialName("symbol_hunt")
data class SymbolHuntSpec(override val id: String, val rounds: List<SymbolHuntRound>) : TaskSpec

@Serializable
data class SymbolHuntRound(
    override val promptTts: String,
    val targetAtomId: String,
    val mode: SymbolHuntMode,
    /** Resolved once at derivation time — see SymbolHuntDerivation.distractorPool. */
    val distractorPool: List<String>,
) : TrainerRound
```

Update the `TaskSpec.kind` extension:

```kotlin
val TaskSpec.kind: TrainerKind
    get() = when (this) {
        is SoundPositionSpec -> TrainerKind.sound_position
        is LetterTraceSpec -> TrainerKind.letter_trace
        is SyllableMergeSpec -> TrainerKind.syllable_merge
        is WordBuildSpec -> TrainerKind.word_build
        is SentenceOrderSpec -> TrainerKind.sentence_order
        is CountAddSpec -> TrainerKind.count_add
        is SymbolHuntSpec -> TrainerKind.symbol_hunt
    }
```

Update the `TaskSpec.rounds` extension:

```kotlin
val TaskSpec.rounds: List<TrainerRound>
    get() = when (this) {
        is SoundPositionSpec -> rounds
        is LetterTraceSpec -> rounds
        is SyllableMergeSpec -> rounds
        is WordBuildSpec -> rounds
        is SentenceOrderSpec -> rounds
        is CountAddSpec -> rounds
        is SymbolHuntSpec -> rounds
    }
```

Update `TrainerRound.scoredAtomIds()`:

```kotlin
fun TrainerRound.scoredAtomIds(): List<String> = when (this) {
    is SoundPositionRound -> listOf(atomId)
    is LetterTraceRound -> listOf(atomId)
    is SyllableMergeRound -> listOf(leftAtomId, rightAtomId, resultAtomId).distinct()
    is WordBuildRound -> (blocks.map { it.atomId } + targetAtomId).distinct()
    // Sentence atom ids are only resolvable via the pack, so SessionViewModel fills
    // them in; count_add scores against a math key, not against atoms.
    is SentenceOrderRound -> emptyList()
    is CountAddRound -> emptyList()
    is SymbolHuntRound -> listOf(targetAtomId)
}
```

- [ ] **Step 2: Add a no-op validator branch (never fires, but keeps the `when` explicit)**

In `app/src/main/java/app/abcvorschule/content/ContentValidator.kt`, inside `pack.tasks.forEach { (id, spec) -> ... when (spec) { ... } }`, add a final branch right after the `is CountAddSpec ->` branch:

```kotlin
                is SymbolHuntSpec -> Unit // synthetic-only; never appears in authored content
```

- [ ] **Step 3: Write `SymbolHuntSpecTest.kt`**

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Test

class SymbolHuntSpecTest {
    private val round = SymbolHuntRound(
        promptTts = "Finde alle Buchstaben M!",
        targetAtomId = "letter-m",
        mode = SymbolHuntMode.letter,
        distractorPool = listOf("letter-a"),
    )
    private val spec = SymbolHuntSpec(id = "l01:symbol_hunt:letter", rounds = listOf(round))

    @Test
    fun kindIsSymbolHunt() {
        assertEquals(TrainerKind.symbol_hunt, spec.kind)
    }

    @Test
    fun roundsExposeTheSingleRound() {
        assertEquals(listOf(round), spec.rounds)
    }

    @Test
    fun scoredAtomIdsIsJustTheTarget() {
        assertEquals(listOf("letter-m"), round.scoredAtomIds())
    }
}
```

- [ ] **Step 4: Run and verify**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.content.SymbolHuntSpecTest" --tests "app.abcvorschule.content.ContentValidatorTest"
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/TaskSpecs.kt app/src/main/java/app/abcvorschule/content/ContentValidator.kt app/src/test/java/app/abcvorschule/content/SymbolHuntSpecTest.kt
git commit -m "feat(content): add symbol_hunt TaskSpec/TrainerRound (derived-only, never authored)"
```

---

## Task 6: `SymbolHuntDerivation` — pool, degeneration, and round-building

**Files:**
- Create: `app/src/main/java/app/abcvorschule/content/SymbolHuntDerivation.kt`
- Create: `app/src/test/java/app/abcvorschule/content/SymbolHuntDerivationTest.kt`

**Interfaces:**
- Consumes: `ContentPack`, `Atom`, `AtomKind`, `LetterTraceSpec`, `SyllableMergeSpec`, `SymbolHuntMode`, `SymbolHuntRound` (all from Tasks 5 and pre-existing content types).
- Produces: `SymbolHuntDerivation.distractorPool(pack, currentLessonIndex, mode, targetAtomId): List<String>`, `SymbolHuntDerivation.tileCounts(poolSize: Int): Pair<Int, Int>?` (null = skip the round), `SymbolHuntDerivation.buildRound(pack, currentLessonIndex, mode, targetAtomId): SymbolHuntRound?` (null = skip). Task 7 (`SymbolHuntInsertion`) calls `buildRound`; Task 9 (`SymbolHuntProgress`) calls `tileCounts`.

- [ ] **Step 1: Write `SymbolHuntDerivation.kt`**

```kotlin
package app.abcvorschule.content

/**
 * Pure derivation for the Buchstaben-/Silben-Jagd trainer: which atoms count as
 * distractors, how many hit/distractor tiles a round gets, and the fully-resolved
 * [SymbolHuntRound] itself. No JSON is read here beyond what [ContentPack] already
 * parsed — see design doc §3 for the rules this implements.
 */
object SymbolHuntDerivation {
    private const val PromptLetterTemplate = "Finde alle Buchstaben %s!"
    private const val PromptSyllableTemplate = "Finde alle Silben %s!"

    /**
     * Distractor pool for [targetAtomId]: atoms of the eligible kind for [mode],
     * sourced *only* from letter_trace rounds (mode = letter) or syllable_merge
     * result atoms (mode = syllable) in lessons `1..currentLessonIndex` — not
     * every atom the lesson has touched. Excludes the target itself.
     */
    fun distractorPool(
        pack: ContentPack,
        currentLessonIndex: Int,
        mode: SymbolHuntMode,
        targetAtomId: String,
    ): List<String> {
        val eligibleLessons = pack.lessons.filter { it.index <= currentLessonIndex }
        val sourceAtomIds = eligibleLessons.flatMap { lesson ->
            pack.tasksOf(lesson).flatMap { spec ->
                when (mode) {
                    SymbolHuntMode.letter ->
                        (spec as? LetterTraceSpec)?.rounds?.map { it.atomId } ?: emptyList()
                    SymbolHuntMode.syllable ->
                        (spec as? SyllableMergeSpec)?.rounds?.map { it.resultAtomId } ?: emptyList()
                }
            }
        }
        val eligibleKinds = when (mode) {
            SymbolHuntMode.letter -> setOf(AtomKind.letter, AtomKind.digraph)
            SymbolHuntMode.syllable -> setOf(AtomKind.syllable)
        }
        return sourceAtomIds.distinct()
            .filter { it != targetAtomId }
            .filter { pack.atoms[it]?.kind in eligibleKinds }
    }

    /**
     * (hitCount, distractorTileCount) for a given unique-distractor pool size, or
     * null when the round must be skipped entirely (no pool at all — see design
     * doc §3's degeneration table).
     */
    fun tileCounts(poolSize: Int): Pair<Int, Int>? = when {
        poolSize <= 0 -> null
        poolSize <= 2 -> 3 to (poolSize * 2)
        else -> 5 to 6
    }

    /**
     * Builds a fully-resolved [SymbolHuntRound] for [targetAtomId], or null if the
     * round must be skipped: the target atom doesn't exist, a syllable-mode target
     * isn't actually [AtomKind.syllable], or the distractor pool is empty.
     */
    fun buildRound(
        pack: ContentPack,
        currentLessonIndex: Int,
        mode: SymbolHuntMode,
        targetAtomId: String,
    ): SymbolHuntRound? {
        val target = pack.atoms[targetAtomId] ?: return null
        if (mode == SymbolHuntMode.syllable && target.kind != AtomKind.syllable) return null
        val pool = distractorPool(pack, currentLessonIndex, mode, targetAtomId)
        if (pool.isEmpty()) return null
        val template = if (mode == SymbolHuntMode.letter) PromptLetterTemplate else PromptSyllableTemplate
        return SymbolHuntRound(
            promptTts = template.format(target.display),
            targetAtomId = targetAtomId,
            mode = mode,
            distractorPool = pool,
        )
    }
}
```

- [ ] **Step 2: Write `SymbolHuntDerivationTest.kt`**

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolHuntDerivationTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun tileCountsDegenerationTable() {
        assertNull(SymbolHuntDerivation.tileCounts(0))
        assertEquals(3 to 2, SymbolHuntDerivation.tileCounts(1))
        assertEquals(3 to 4, SymbolHuntDerivation.tileCounts(2))
        assertEquals(5 to 6, SymbolHuntDerivation.tileCounts(3))
        assertEquals(5 to 6, SymbolHuntDerivation.tileCounts(10))
    }

    @Test
    fun letterPoolForLessonOneHasExactlyTheOtherFocusLetter() {
        // Lesson 1 (M & A): hunting "letter-a" should offer "letter-m" as the only
        // known distractor letter, and vice versa.
        val poolForA = SymbolHuntDerivation.distractorPool(
            pack, currentLessonIndex = 1, mode = SymbolHuntMode.letter, targetAtomId = "letter-a",
        )
        assertEquals(listOf("letter-m"), poolForA)
    }

    @Test
    fun syllablePoolForLessonOneIsEmptyBecauseNoOtherSyllableExistsYet() {
        // "ma" is l01's only syllable_merge result — its own pool (excluding
        // itself) has nothing else to offer yet, so the round must be skipped.
        val pool = SymbolHuntDerivation.distractorPool(
            pack, currentLessonIndex = 1, mode = SymbolHuntMode.syllable, targetAtomId = "ma",
        )
        assertTrue(pool.isEmpty())
        assertNull(SymbolHuntDerivation.buildRound(pack, currentLessonIndex = 1, mode = SymbolHuntMode.syllable, targetAtomId = "ma"))
    }

    @Test
    fun syllablePoolGrowsByLessonTwo() {
        val secondLessonIndex = pack.lesson("l02").index
        val pool = SymbolHuntDerivation.distractorPool(
            pack, currentLessonIndex = secondLessonIndex, mode = SymbolHuntMode.syllable, targetAtomId = "ma",
        )
        assertTrue("l02 must introduce at least one new syllable", pool.isNotEmpty())
    }

    @Test
    fun nonSyllableKindTargetIsSkippedForSyllableMode() {
        // "ameise" is picture-only vocabulary (AtomKind.other), never a valid
        // syllable-hunt target even if something tried to pass it in.
        assertNull(
            SymbolHuntDerivation.buildRound(pack, currentLessonIndex = 1, mode = SymbolHuntMode.syllable, targetAtomId = "ameise"),
        )
    }

    @Test
    fun buildRoundProducesATemplatedPromptAndEmbedsThePool() {
        val round = SymbolHuntDerivation.buildRound(
            pack, currentLessonIndex = 1, mode = SymbolHuntMode.letter, targetAtomId = "letter-a",
        )
        assertEquals("Finde alle Buchstaben A!", round?.promptTts)
        assertEquals(listOf("letter-m"), round?.distractorPool)
    }

    @Test
    fun everyBuildableRoundAcrossTheWholePackHasANonEmptyPool() {
        // Gate: no round that insertSymbolHunts would keep ever has an empty pool
        // (buildRound already filters those out by returning null).
        pack.authoredLessons.forEach { lesson ->
            pack.tasksOf(lesson).filterIsInstance<LetterTraceSpec>().flatMap { it.rounds }.forEach { round ->
                val hunt = SymbolHuntDerivation.buildRound(pack, lesson.index, SymbolHuntMode.letter, round.atomId)
                if (hunt != null) assertTrue(hunt.distractorPool.isNotEmpty())
            }
            pack.tasksOf(lesson).filterIsInstance<SyllableMergeSpec>().flatMap { it.rounds }.forEach { round ->
                val hunt = SymbolHuntDerivation.buildRound(pack, lesson.index, SymbolHuntMode.syllable, round.resultAtomId)
                if (hunt != null) {
                    assertTrue(hunt.distractorPool.isNotEmpty())
                    assertEquals(AtomKind.syllable, pack.atom(round.resultAtomId).kind)
                }
            }
        }
    }
}
```

- [ ] **Step 3: Run and verify**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.content.SymbolHuntDerivationTest"
```
Expected: BUILD SUCCESSFUL, all tests pass. If `letterPoolForLessonOneHasExactlyTheOtherFocusLetter` or `syllablePoolGrowsByLessonTwo` fail because the real pack's content differs slightly from what this plan assumed, adjust the assertion to match the actual (already-verified-correct) pack content rather than the content-derivation logic — the logic itself was validated against the real pack while writing this plan.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/SymbolHuntDerivation.kt app/src/test/java/app/abcvorschule/content/SymbolHuntDerivationTest.kt
git commit -m "feat(content): add SymbolHuntDerivation — pool, degeneration table, round builder"
```

---

## Task 7: `SymbolHuntInsertion` — splice the hunt steps into a lesson's trainer list

**Files:**
- Create: `app/src/main/java/app/abcvorschule/session/SymbolHuntInsertion.kt`
- Create: `app/src/test/java/app/abcvorschule/session/SymbolHuntInsertionTest.kt`

**Interfaces:**
- Consumes: `ScheduledTrainer` (from `SessionModels.kt`), `ContentPack`, `SymbolHuntDerivation.buildRound`, `LetterTraceSpec`, `SyllableMergeSpec`, `SymbolHuntMode`, `SymbolHuntSpec`.
- Produces: `SymbolHuntInsertion.insertSymbolHunts(trainers: List<ScheduledTrainer>, pack: ContentPack, lessonId: String, currentLessonIndex: Int): List<ScheduledTrainer>`. Task 11 calls this from `SessionViewModel.openLesson`.

- [ ] **Step 1: Write `SymbolHuntInsertion.kt`**

```kotlin
package app.abcvorschule.session

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.LetterTraceSpec
import app.abcvorschule.content.SymbolHuntDerivation
import app.abcvorschule.content.SymbolHuntMode
import app.abcvorschule.content.SymbolHuntSpec
import app.abcvorschule.content.SyllableMergeSpec

/**
 * Splices the (up to two) Buchstaben-/Silben-Jagd steps into a lesson's scheduled
 * trainer list at runtime — no JSON authoring, no ContentValidator involvement
 * (design doc §2). The letter hunt is derived from every letter_trace round in
 * the lesson and placed right after the last letter_trace trainer; the syllable
 * hunt is derived from every syllable_merge round and placed right after the
 * last syllable_merge trainer. A lesson missing the source kind, or one whose
 * derived rounds are all degenerate (empty distractor pool), gets no hunt for
 * that mode at all.
 */
object SymbolHuntInsertion {
    fun insertSymbolHunts(
        trainers: List<ScheduledTrainer>,
        pack: ContentPack,
        lessonId: String,
        currentLessonIndex: Int,
    ): List<ScheduledTrainer> {
        val afterLetterHunt = insertHunt(trainers, SymbolHuntMode.letter, lessonId, currentLessonIndex, pack)
        return insertHunt(afterLetterHunt, SymbolHuntMode.syllable, lessonId, currentLessonIndex, pack)
    }

    private fun insertHunt(
        trainers: List<ScheduledTrainer>,
        mode: SymbolHuntMode,
        lessonId: String,
        currentLessonIndex: Int,
        pack: ContentPack,
    ): List<ScheduledTrainer> {
        val targetAtomIds = when (mode) {
            SymbolHuntMode.letter -> trainers.filter { it.spec is LetterTraceSpec }
                .flatMap { (it.spec as LetterTraceSpec).rounds }
                .map { it.atomId }
            SymbolHuntMode.syllable -> trainers.filter { it.spec is SyllableMergeSpec }
                .flatMap { (it.spec as SyllableMergeSpec).rounds }
                .map { it.resultAtomId }
        }
        val lastSourceIndex = trainers.indexOfLast {
            if (mode == SymbolHuntMode.letter) it.spec is LetterTraceSpec else it.spec is SyllableMergeSpec
        }
        if (lastSourceIndex < 0) return trainers
        val rounds = targetAtomIds.mapNotNull { targetAtomId ->
            SymbolHuntDerivation.buildRound(pack, currentLessonIndex, mode, targetAtomId)
        }
        if (rounds.isEmpty()) return trainers
        val hunt = ScheduledTrainer(
            spec = SymbolHuntSpec(id = "$lessonId:symbol_hunt:${mode.name}", rounds = rounds),
        )
        return trainers.toMutableList().apply { add(lastSourceIndex + 1, hunt) }
    }
}
```

- [ ] **Step 2: Write `SymbolHuntInsertionTest.kt`**

```kotlin
package app.abcvorschule.session

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.LetterTraceSpec
import app.abcvorschule.content.SymbolHuntSpec
import app.abcvorschule.content.TrainerKind
import app.abcvorschule.content.kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolHuntInsertionTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun scheduledTrainersFor(lessonId: String): List<ScheduledTrainer> =
        pack.tasksOf(pack.lesson(lessonId)).map { ScheduledTrainer(spec = it) }

    @Test
    fun letterHuntIsPlacedRightAfterTheLastLetterTraceTrainer() {
        val lesson = pack.lesson("l01")
        val trainers = scheduledTrainersFor(lesson.id)
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
        val lastTraceIndex = trainers.indexOfLast { it.spec is LetterTraceSpec }
        val hunt = result.getOrNull(lastTraceIndex + 1)
        assertTrue(hunt?.spec is SymbolHuntSpec)
        assertEquals(TrainerKind.symbol_hunt, hunt!!.spec.kind)
    }

    @Test
    fun letterHuntRoundsMatchEveryLetterTraceRoundInTheLesson() {
        val lesson = pack.lesson("l01")
        val trainers = scheduledTrainersFor(lesson.id)
        val traceAtomIds = trainers.filter { it.spec is LetterTraceSpec }
            .flatMap { (it.spec as LetterTraceSpec).rounds }
            .map { it.atomId }
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
        val letterHunt = result.first { it.spec is SymbolHuntSpec && it.spec.id.endsWith(":letter") }
            .spec as SymbolHuntSpec
        assertEquals(traceAtomIds, letterHunt.rounds.map { it.targetAtomId })
    }

    @Test
    fun stableIdsAreLessonAndModeScoped() {
        val lesson = pack.lesson("l01")
        val trainers = scheduledTrainersFor(lesson.id)
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
        val ids = result.filter { it.spec is SymbolHuntSpec }.map { it.spec.id }
        assertTrue("expected at least the letter hunt for l01", ids.isNotEmpty())
        ids.forEach { assertTrue(it.startsWith("l01:symbol_hunt:")) }
    }

    @Test
    fun lessonWithoutSyllableMergeGetsNoSyllableHunt() {
        val withoutSyllableMerge = pack.authoredLessons.firstOrNull { lesson ->
            pack.tasksOf(lesson).none { it.kind == TrainerKind.syllable_merge }
        }
        requireNotNull(withoutSyllableMerge) { "expected at least one authored lesson without syllable_merge" }
        val trainers = scheduledTrainersFor(withoutSyllableMerge.id)
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, withoutSyllableMerge.id, withoutSyllableMerge.index)
        assertTrue(result.none { it.spec is SymbolHuntSpec && it.spec.id.endsWith(":syllable") })
    }

    @Test
    fun lessonOneGetsNoSyllableHuntBecauseItsPoolIsDegenerate() {
        // Confirmed in SymbolHuntDerivationTest: l01's only syllable is "ma"
        // itself, so its pool (excluding the target) is empty.
        val lesson = pack.lesson("l01")
        val trainers = scheduledTrainersFor(lesson.id)
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
        assertTrue(result.none { it.spec is SymbolHuntSpec && it.spec.id.endsWith(":syllable") })
    }

    @Test
    fun insertingNeverDropsOrReordersTheOriginalTrainers() {
        val lesson = pack.lesson("l06")
        val trainers = scheduledTrainersFor(lesson.id)
        val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
        val resultOriginalOnly = result.filter { it.spec !is SymbolHuntSpec }
        assertEquals(trainers.map { it.spec.id }, resultOriginalOnly.map { it.spec.id })
    }

    @Test
    fun gateEveryAuthoredLessonProducesAValidInsertion() {
        pack.authoredLessons.forEach { lesson ->
            val trainers = scheduledTrainersFor(lesson.id)
            val result = SymbolHuntInsertion.insertSymbolHunts(trainers, pack, lesson.id, lesson.index)
            result.map { it.spec }
                .filterIsInstance<SymbolHuntSpec>()
                .forEach { spec ->
                    assertTrue("lesson ${lesson.id} hunt ${spec.id} must have rounds", spec.rounds.isNotEmpty())
                    spec.rounds.forEach { round ->
                        assertTrue(
                            "lesson ${lesson.id} hunt round ${round.targetAtomId} must have a non-empty pool",
                            round.distractorPool.isNotEmpty(),
                        )
                    }
                }
        }
    }
}
```

Note on Step 2's `stableIdsAreLessonAndModeScoped` test: it's written defensively since some lessons (like l01) legitimately produce zero or one hunt — the assertion only checks that whichever hunt ids *are* present are correctly prefixed, not that both always exist.

- [ ] **Step 3: Run and verify**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.session.SymbolHuntInsertionTest"
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/session/SymbolHuntInsertion.kt app/src/test/java/app/abcvorschule/session/SymbolHuntInsertionTest.kt
git commit -m "feat(session): add SymbolHuntInsertion to splice derived hunt trainers into a lesson"
```

---

## Task 8: `SymbolHuntLayout` — deterministic scatter placement

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntLayout.kt`
- Create: `app/src/test/java/app/abcvorschule/ui/exercise/SymbolHuntLayoutTest.kt`

**Interfaces:**
- Produces: `HuntTilePosition(x: Float, y: Float, scale: Float, colorIndex: Int)`, `SymbolHuntLayout.scatter(seed: Long, tileCount: Int, boundsWidth: Float, boundsHeight: Float): List<HuntTilePosition>`. Task 10 (`SymbolHuntTrainer`) calls this with the field's actual pixel bounds.

- [ ] **Step 1: Write `SymbolHuntLayout.kt`**

```kotlin
package app.abcvorschule.ui.exercise

import kotlin.math.hypot
import kotlin.random.Random

/** One tile's placement in the scatter field: pixel center + a size multiplier
 * (visual variety) + a palette index (rotates through the theme's readable colors). */
data class HuntTilePosition(val x: Float, val y: Float, val scale: Float, val colorIndex: Int)

/**
 * Deterministic scatter placement for the Buchstaben-/Silben-Jagd field (design
 * doc §4 — a deliberate exception to Prinzip 9's "answers at the bottom" default).
 * Same [seed] + [tileCount] + bounds always produce the same layout, so a given
 * round's field is reproducible; a wrong tap advances the seed by one to
 * reshuffle. Retries with a derived seed when tiles land too close together for
 * a small finger to tell them apart.
 */
object SymbolHuntLayout {
    /** Minimum distance between tile centers, as a fraction of the shorter bounds side. */
    const val MinCenterDistanceFraction = 0.22f
    private const val MaxAttempts = 12

    fun scatter(seed: Long, tileCount: Int, boundsWidth: Float, boundsHeight: Float): List<HuntTilePosition> {
        if (tileCount <= 0 || boundsWidth <= 0f || boundsHeight <= 0f) return emptyList()
        val minDistance = minOf(boundsWidth, boundsHeight) * MinCenterDistanceFraction
        var attempt = 0
        var candidate = place(seed, tileCount, boundsWidth, boundsHeight)
        while (attempt < MaxAttempts && !isWellSpaced(candidate, minDistance)) {
            attempt += 1
            candidate = place(seed + attempt, tileCount, boundsWidth, boundsHeight)
        }
        return candidate
    }

    private fun place(seed: Long, tileCount: Int, boundsWidth: Float, boundsHeight: Float): List<HuntTilePosition> {
        val random = Random(seed)
        return (0 until tileCount).map { index ->
            HuntTilePosition(
                x = (0.1f + random.nextFloat() * 0.8f) * boundsWidth,
                y = (0.1f + random.nextFloat() * 0.8f) * boundsHeight,
                scale = 0.8f + random.nextFloat() * 0.5f,
                colorIndex = index,
            )
        }
    }

    private fun isWellSpaced(tiles: List<HuntTilePosition>, minDistance: Float): Boolean {
        for (i in tiles.indices) {
            for (j in i + 1 until tiles.size) {
                if (hypot(tiles[i].x - tiles[j].x, tiles[i].y - tiles[j].y) < minDistance) return false
            }
        }
        return true
    }
}
```

- [ ] **Step 2: Write `SymbolHuntLayoutTest.kt`**

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class SymbolHuntLayoutTest {
    @Test
    fun sameSeedProducesTheSameLayout() {
        val a = SymbolHuntLayout.scatter(seed = 42L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        val b = SymbolHuntLayout.scatter(seed = 42L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        assertEquals(a, b)
    }

    @Test
    fun differentSeedsUsuallyProduceDifferentLayouts() {
        val a = SymbolHuntLayout.scatter(seed = 1L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        val b = SymbolHuntLayout.scatter(seed = 2L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        assertTrue(a != b)
    }

    @Test
    fun returnsOnePositionPerTile() {
        val positions = SymbolHuntLayout.scatter(seed = 7L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        assertEquals(11, positions.size)
    }

    @Test
    fun zeroTileCountReturnsEmpty() {
        assertEquals(emptyList<HuntTilePosition>(), SymbolHuntLayout.scatter(seed = 1L, tileCount = 0, boundsWidth = 360f, boundsHeight = 500f))
    }

    @Test
    fun tilesRespectTheMinimumSpacingForARealisticFieldSize() {
        val positions = SymbolHuntLayout.scatter(seed = 99L, tileCount = 11, boundsWidth = 360f, boundsHeight = 500f)
        val minDistance = minOf(360f, 500f) * SymbolHuntLayout.MinCenterDistanceFraction
        for (i in positions.indices) {
            for (j in i + 1 until positions.size) {
                val distance = hypot(positions[i].x - positions[j].x, positions[i].y - positions[j].y)
                assertTrue("tiles $i and $j are too close: $distance < $minDistance", distance >= minDistance)
            }
        }
    }

    @Test
    fun terminatesEvenWhenSpacingCannotBeSatisfied() {
        // 11 tiles in a tiny field can't possibly satisfy the spacing constraint —
        // the retry loop must still terminate (bounded by MaxAttempts) rather than
        // looping forever.
        val positions = SymbolHuntLayout.scatter(seed = 5L, tileCount = 11, boundsWidth = 10f, boundsHeight = 10f)
        assertEquals(11, positions.size)
    }
}
```

- [ ] **Step 3: Run and verify**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.SymbolHuntLayoutTest"
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntLayout.kt app/src/test/java/app/abcvorschule/ui/exercise/SymbolHuntLayoutTest.kt
git commit -m "feat(exercise): add SymbolHuntLayout — deterministic, min-spaced scatter placement"
```

---

## Task 9: `SymbolHuntProgress` — pure tap/miss/resolve state machine

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntProgress.kt`
- Create: `app/src/test/java/app/abcvorschule/ui/exercise/SymbolHuntProgressTest.kt`

**Interfaces:**
- Consumes: `SymbolHuntRound` (content package), `SymbolHuntDerivation.tileCounts` (Task 6).
- Produces: `SymbolHuntTile(instanceId, atomId, isTarget)`, `SymbolHuntState(tiles, targetHitCount, collected, consecutiveMisses, reportedMissThisRound, seed)`, `SymbolHuntTapOutcome` (enum: `Collected`, `RoundComplete`, `Miss`, `MissAlreadyReported`, `Ignored`), `SymbolHuntTapResult(state, outcome)`, `SymbolHuntProgress.initialState(round, seed): SymbolHuntState`, `SymbolHuntProgress.tap(state, instanceId): SymbolHuntTapResult`, `SymbolHuntProgress.resolveAvailable(state): Boolean`, `SymbolHuntProgress.resolve(state): SymbolHuntState`, `SymbolHuntProgress.ResolveThreshold` (const `6`). Task 10 (`SymbolHuntTrainer`) is the sole consumer of all of these.

- [ ] **Step 1: Write `SymbolHuntProgress.kt`**

```kotlin
package app.abcvorschule.ui.exercise

import app.abcvorschule.content.SymbolHuntDerivation
import app.abcvorschule.content.SymbolHuntRound

/** One collectible/decoy instance in the scatter field. Distinct instances can
 * share the same underlying atom — a distractor letter repeats as several tiles
 * when only one or two other letters are known yet (design doc §3). */
data class SymbolHuntTile(val instanceId: Int, val atomId: String, val isTarget: Boolean)

data class SymbolHuntState(
    val tiles: List<SymbolHuntTile>,
    val targetHitCount: Int,
    val collected: Int = 0,
    val consecutiveMisses: Int = 0,
    val reportedMissThisRound: Boolean = false,
    val seed: Long = 0L,
)

enum class SymbolHuntTapOutcome { Collected, RoundComplete, Miss, MissAlreadyReported, Ignored }

data class SymbolHuntTapResult(val state: SymbolHuntState, val outcome: SymbolHuntTapOutcome)

/**
 * Tap handling for the Buchstaben-/Silben-Jagd battery game (design doc §5): a
 * wrong tap reshuffles the field (bumps [SymbolHuntState.seed]) without losing
 * battery progress, and only the first miss of a round is reported for
 * adaptivity. Resolve unlocks after [ResolveThreshold] *consecutive* misses —
 * resets on any correct tap, unlike the cumulative off-road count in the
 * Spurensucher (LetterTraceTrainer); the design doc explicitly calls for
 * "aufeinanderfolgende" (consecutive) misses here, reusing only the threshold
 * number.
 */
object SymbolHuntProgress {
    const val ResolveThreshold = 6

    fun initialState(round: SymbolHuntRound, seed: Long): SymbolHuntState {
        val (hitCount, distractorCount) = requireNotNull(
            SymbolHuntDerivation.tileCounts(round.distractorPool.size),
        ) { "SymbolHuntRound ${round.targetAtomId} has an empty distractor pool" }
        val hits = (0 until hitCount).map { i ->
            SymbolHuntTile(instanceId = i, atomId = round.targetAtomId, isTarget = true)
        }
        val distractors = (0 until distractorCount).map { i ->
            val atomId = round.distractorPool[i % round.distractorPool.size]
            SymbolHuntTile(instanceId = hitCount + i, atomId = atomId, isTarget = false)
        }
        return SymbolHuntState(tiles = hits + distractors, targetHitCount = hitCount, seed = seed)
    }

    fun tap(state: SymbolHuntState, instanceId: Int): SymbolHuntTapResult {
        val tile = state.tiles.firstOrNull { it.instanceId == instanceId }
            ?: return SymbolHuntTapResult(state, SymbolHuntTapOutcome.Ignored)
        if (tile.isTarget) {
            val remaining = state.tiles.filter { it.instanceId != instanceId }
            val collected = state.collected + 1
            val next = state.copy(tiles = remaining, collected = collected, consecutiveMisses = 0)
            val outcome = if (collected >= state.targetHitCount) {
                SymbolHuntTapOutcome.RoundComplete
            } else {
                SymbolHuntTapOutcome.Collected
            }
            return SymbolHuntTapResult(next, outcome)
        }
        val alreadyReported = state.reportedMissThisRound
        val next = state.copy(
            consecutiveMisses = state.consecutiveMisses + 1,
            reportedMissThisRound = true,
            seed = state.seed + 1,
        )
        val outcome = if (alreadyReported) SymbolHuntTapOutcome.MissAlreadyReported else SymbolHuntTapOutcome.Miss
        return SymbolHuntTapResult(next, outcome)
    }

    fun resolveAvailable(state: SymbolHuntState): Boolean = state.consecutiveMisses >= ResolveThreshold

    /** Resolve: auto-fill the remaining battery segments and clear the field. */
    fun resolve(state: SymbolHuntState): SymbolHuntState =
        state.copy(tiles = emptyList(), collected = state.targetHitCount)
}
```

- [ ] **Step 2: Write `SymbolHuntProgressTest.kt`**

```kotlin
package app.abcvorschule.ui.exercise

import app.abcvorschule.content.SymbolHuntMode
import app.abcvorschule.content.SymbolHuntRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolHuntProgressTest {
    private val roundWithThreeDistractors = SymbolHuntRound(
        promptTts = "Finde alle Buchstaben A!",
        targetAtomId = "letter-a",
        mode = SymbolHuntMode.letter,
        distractorPool = listOf("letter-m", "letter-i", "letter-o"),
    )

    @Test
    fun initialStateHasFiveHitsAndSixDistractorTiles() {
        val state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        assertEquals(5, state.targetHitCount)
        assertEquals(5, state.tiles.count { it.isTarget })
        assertEquals(6, state.tiles.count { !it.isTarget })
    }

    @Test
    fun initialStateRepeatsASmallPoolToFillDistractorTiles() {
        val round = roundWithThreeDistractors.copy(distractorPool = listOf("letter-m"))
        val state = SymbolHuntProgress.initialState(round, seed = 1L)
        assertEquals(3, state.targetHitCount)
        assertEquals(2, state.tiles.count { !it.isTarget })
        assertTrue(state.tiles.filter { !it.isTarget }.all { it.atomId == "letter-m" })
    }

    @Test
    fun collectingAHitRemovesItAndIncrementsBatteryWithoutReportingAResult() {
        val state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val hitId = state.tiles.first { it.isTarget }.instanceId
        val result = SymbolHuntProgress.tap(state, hitId)
        assertEquals(SymbolHuntTapOutcome.Collected, result.outcome)
        assertEquals(1, result.state.collected)
        assertTrue(result.state.tiles.none { it.instanceId == hitId })
    }

    @Test
    fun collectingTheLastHitReportsRoundComplete() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        repeat(4) {
            val hitId = state.tiles.first { it.isTarget }.instanceId
            state = SymbolHuntProgress.tap(state, hitId).state
        }
        val lastHitId = state.tiles.first { it.isTarget }.instanceId
        val result = SymbolHuntProgress.tap(state, lastHitId)
        assertEquals(SymbolHuntTapOutcome.RoundComplete, result.outcome)
        assertEquals(5, result.state.collected)
    }

    @Test
    fun wrongTapReshufflesWithoutLosingBatteryProgress() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val hitId = state.tiles.first { it.isTarget }.instanceId
        state = SymbolHuntProgress.tap(state, hitId).state
        assertEquals(1, state.collected)
        val distractorId = state.tiles.first { !it.isTarget }.instanceId
        val result = SymbolHuntProgress.tap(state, distractorId)
        assertEquals(1, result.state.collected)
        assertEquals(state.seed + 1, result.state.seed)
    }

    @Test
    fun onlyTheFirstMissOfARoundReports() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val distractorId = state.tiles.first { !it.isTarget }.instanceId
        val first = SymbolHuntProgress.tap(state, distractorId)
        assertEquals(SymbolHuntTapOutcome.Miss, first.outcome)
        val second = SymbolHuntProgress.tap(first.state, distractorId)
        assertEquals(SymbolHuntTapOutcome.MissAlreadyReported, second.outcome)
    }

    @Test
    fun tappingAnAlreadyCollectedInstanceIsIgnored() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val hitId = state.tiles.first { it.isTarget }.instanceId
        state = SymbolHuntProgress.tap(state, hitId).state
        val result = SymbolHuntProgress.tap(state, hitId)
        assertEquals(SymbolHuntTapOutcome.Ignored, result.outcome)
        assertEquals(state, result.state)
    }

    @Test
    fun resolveAvailableAfterSixConsecutiveMisses() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val distractorId = state.tiles.first { !it.isTarget }.instanceId
        repeat(5) { state = SymbolHuntProgress.tap(state, distractorId).state }
        assertFalse(SymbolHuntProgress.resolveAvailable(state))
        state = SymbolHuntProgress.tap(state, distractorId).state
        assertTrue(SymbolHuntProgress.resolveAvailable(state))
    }

    @Test
    fun aCorrectTapResetsTheConsecutiveMissCounter() {
        var state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val distractorId = state.tiles.first { !it.isTarget }.instanceId
        repeat(5) { state = SymbolHuntProgress.tap(state, distractorId).state }
        assertEquals(5, state.consecutiveMisses)
        val hitId = state.tiles.first { it.isTarget }.instanceId
        state = SymbolHuntProgress.tap(state, hitId).state
        assertEquals(0, state.consecutiveMisses)
    }

    @Test
    fun resolveFillsTheBatteryAndClearsTheField() {
        val state = SymbolHuntProgress.initialState(roundWithThreeDistractors, seed = 1L)
        val resolved = SymbolHuntProgress.resolve(state)
        assertEquals(5, resolved.collected)
        assertTrue(resolved.tiles.isEmpty())
    }
}
```

- [ ] **Step 3: Run and verify**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.SymbolHuntProgressTest"
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntProgress.kt app/src/test/java/app/abcvorschule/ui/exercise/SymbolHuntProgressTest.kt
git commit -m "feat(exercise): add SymbolHuntProgress — pure tap/miss/resolve state machine"
```

---

## Task 10: `SymbolHuntTrainer` composable + `TrainerHost` wiring

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt`

**Interfaces:**
- Consumes: `SymbolHuntRound` (content), `SymbolHuntProgress`/`SymbolHuntState`/`SymbolHuntTapOutcome` (Task 9), `SymbolHuntLayout` (Task 8), `ExerciseStage`, `TaskPromptChrome`, `AbcContinueButton`, `AbcResolveButton` (existing components).
- Produces: `@Composable fun SymbolHuntTrainer(round, roundIndex, pack, ttsAvailable, speaking, onSpeakPrompt, onSpeak, onResult, modifier)` — same `onResult` signature (`(correct, resolved, atomIds) -> Unit`) as every other non-math trainer, so `TrainerHost` wires it exactly like the others.

- [ ] **Step 1: Write `SymbolHuntTrainer.kt`**

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.ui.components.AbcContinueButton
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.SoftCoral
import app.abcvorschule.ui.theme.SoftGold
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky

private val TileSize = 64.dp
private val TilePalette = listOf(SoftMint, SoftCoral, SoftSky, SoftGold, SoftSand)

/**
 * Buchstaben-/Silben-Jagd: tiles scatter across the whole task area under a
 * fixed speaker strip (deliberate exception to Prinzip 9 — design doc §4), the
 * battery lives in the answer area (also an exception). A wrong tap reshuffles
 * without losing battery progress; the battery-full moment gates on a local
 * "Weiter" tap before handing off to the shared success pipeline (design doc §5).
 */
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
    val roundKey = "$roundIndex-${round.targetAtomId}-${round.mode}"
    var state by remember(roundKey) {
        mutableStateOf(SymbolHuntProgress.initialState(round, seed = roundKey.hashCode().toLong()))
    }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    var batteryFull by remember(roundKey) { mutableStateOf(false) }

    fun handleTap(instanceId: Int) {
        if (resolved || batteryFull) return
        val tapped = state.tiles.firstOrNull { it.instanceId == instanceId } ?: return
        onSpeak(pack.atoms[tapped.atomId]?.lemma ?: tapped.atomId)
        val result = SymbolHuntProgress.tap(state, instanceId)
        state = result.state
        when (result.outcome) {
            SymbolHuntTapOutcome.Miss -> onResult(false, false, listOf(round.targetAtomId))
            SymbolHuntTapOutcome.RoundComplete -> batteryFull = true
            SymbolHuntTapOutcome.Collected,
            SymbolHuntTapOutcome.MissAlreadyReported,
            SymbolHuntTapOutcome.Ignored,
            -> Unit
        }
    }

    val fieldAlpha by animateFloatAsState(
        targetValue = if (batteryFull) 0f else 1f,
        animationSpec = tween(durationMillis = 400),
        label = "hunt_field_fade",
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
            if (!resolved) {
                SymbolHuntField(
                    state = state,
                    pack = pack,
                    onTap = ::handleTap,
                    modifier = Modifier.fillMaxSize().alpha(fieldAlpha),
                )
            }
        },
        answers = {
            SymbolHuntBattery(collected = state.collected, total = state.targetHitCount, celebrate = batteryFull)
            if (SymbolHuntProgress.resolveAvailable(state) && !resolved && !batteryFull) {
                AbcResolveButton(
                    onClick = {
                        resolved = true
                        state = SymbolHuntProgress.resolve(state)
                        onResult(false, true, listOf(round.targetAtomId))
                    },
                )
            }
            if (batteryFull) {
                AbcContinueButton(
                    onClick = { onResult(true, false, listOf(round.targetAtomId)) },
                    centered = true,
                )
            }
        },
    )
}

@Composable
private fun SymbolHuntField(
    state: SymbolHuntState,
    pack: ContentPack,
    onTap: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val positions = remember(state.seed, state.tiles.size, widthPx, heightPx) {
            SymbolHuntLayout.scatter(state.seed, state.tiles.size, widthPx, heightPx)
        }
        state.tiles.forEachIndexed { index, tile ->
            val position = positions.getOrNull(index) ?: return@forEachIndexed
            val tileDp = TileSize * position.scale
            val offsetX = with(density) { position.x.toDp() } - tileDp / 2
            val offsetY = with(density) { position.y.toDp() } - tileDp / 2
            val color = TilePalette[index % TilePalette.size]
            Box(
                modifier = Modifier
                    .offset(x = offsetX, y = offsetY)
                    .size(tileDp)
                    .background(color = color.copy(alpha = 0.22f), shape = CircleShape)
                    .border(width = 3.dp, color = color, shape = CircleShape)
                    .clickable { onTap(tile.instanceId) }
                    .testTag("hunt_tile_${tile.instanceId}"),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = pack.atoms[tile.atomId]?.display ?: tile.atomId,
                    fontSize = 28.sp,
                    color = SoftSand,
                )
            }
        }
    }
}

@Composable
private fun SymbolHuntBattery(
    collected: Int,
    total: Int,
    celebrate: Boolean,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "battery_glow")
    val glow by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "battery_glow_value",
    )
    Row(
        modifier = modifier.fillMaxWidth().testTag("hunt_battery"),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(total) { i ->
            val filled = i < collected
            Box(
                modifier = Modifier
                    .size(width = 28.dp, height = 44.dp)
                    .alpha(if (celebrate) glow else 1f)
                    .background(
                        color = when {
                            celebrate -> SoftGold
                            filled -> SoftMint
                            else -> NightElevated
                        },
                        shape = RoundedCornerShape(6.dp),
                    ),
            )
        }
    }
}
```

- [ ] **Step 2: Wire `SymbolHuntRound` into `TrainerHost.kt`**

Add the import:

```kotlin
import app.abcvorschule.content.SymbolHuntRound
```

Add a branch to the `when (round)` block, right after the `is CountAddRound ->` branch (order doesn't matter functionally, but keeping the file's declared-order convention):

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

- [ ] **Step 3: Build to confirm it compiles (no unit test exists for Compose UI in this codebase — visual correctness is verified by running the app)**

```bash
./gradlew :app:compileDebugKotlin
```
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntTrainer.kt app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt
git commit -m "feat(exercise): add SymbolHuntTrainer composable and wire it into TrainerHost"
```

---

## Task 11: Wire `SymbolHuntInsertion` into `SessionViewModel` with resume-shape safety

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/progress/ProgressModels.kt`
- Modify: `app/src/main/java/app/abcvorschule/session/SessionModels.kt`
- Modify: `app/src/main/java/app/abcvorschule/session/SessionViewModel.kt`
- Modify: `app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt`

**Interfaces:**
- Produces: `SessionSnapshot.trainerCount: Int` (new field, defaults to `0` so old stored snapshots deserialize safely), `SessionProgression.resumeSafe(expectedCount: Int?, actualCount: Int, trainerIndex: Int, roundIndex: Int): SessionStep`.
- Consumes: `SymbolHuntInsertion.insertSymbolHunts` (Task 7).

- [ ] **Step 1: Add `trainerCount` to `SessionSnapshot`**

In `app/src/main/java/app/abcvorschule/progress/ProgressModels.kt`, replace:

```kotlin
@Serializable
data class SessionSnapshot(
    val lessonId: String = "",
    /** Index into the lesson's six trainers. */
    val trainerIndex: Int = 0,
    /** Index into the current trainer's rounds. */
    val roundIndex: Int = 0,
    val pointsEarned: Int = 0,
    val packId: String = "",
)
```

with:

```kotlin
@Serializable
data class SessionSnapshot(
    val lessonId: String = "",
    /** Index into the lesson's scheduled trainers (varies per lesson). */
    val trainerIndex: Int = 0,
    /** Index into the current trainer's rounds. */
    val roundIndex: Int = 0,
    val pointsEarned: Int = 0,
    val packId: String = "",
    /**
     * Trainer count at snapshot time. packId alone doesn't catch a *code* change
     * that reshapes the scheduled trainer list without touching content (e.g. this
     * feature inserting hunt steps) — a mismatch here means trainerIndex would
     * point at a different trainer than the one the child left off on, so the
     * lesson restarts from the top instead of resuming into a shifted position.
     * Defaults to 0, which never matches a real trainers.size, so pre-existing
     * stored snapshots safely fail the check the first time they're loaded after
     * this field was introduced.
     */
    val trainerCount: Int = 0,
)
```

- [ ] **Step 2: Add `SessionProgression.resumeSafe`**

In `app/src/main/java/app/abcvorschule/session/SessionModels.kt`, add this function inside the `SessionProgression` object (after `previous`):

```kotlin
    /**
     * Guards against resuming into a shifted position when the scheduled trainer
     * list's shape changed since the snapshot was saved, even though the content
     * pack's id did not (e.g. this app version starts inserting synthetic hunt
     * trainers a previous version didn't). [expectedCount] is the trainer count
     * recorded at snapshot time; null means "no resume in progress, don't shape-check"
     * (a fresh lesson open always starts at trainer 0 anyway).
     */
    fun resumeSafe(
        expectedCount: Int?,
        actualCount: Int,
        trainerIndex: Int,
        roundIndex: Int,
    ): SessionStep {
        if (expectedCount != null && expectedCount != actualCount) return SessionStep(0, 0)
        val safeTrainer = trainerIndex.coerceIn(0, (actualCount - 1).coerceAtLeast(0))
        return SessionStep(safeTrainer, roundIndex.coerceAtLeast(0))
    }
```

- [ ] **Step 3: Wire it all into `SessionViewModel.kt`**

Add these imports:

```kotlin
import app.abcvorschule.content.SymbolHuntRound
import app.abcvorschule.session.SymbolHuntInsertion
```

(`SymbolHuntInsertion` is already in the `session` package, so that second import line is only needed if your editor doesn't resolve same-package references automatically — Kotlin does not require importing types from your own package, so omit it; only add the `SymbolHuntRound` import.)

Replace the `openLesson` signature and body:

```kotlin
    fun openLesson(
        lessonId: String,
        trainerIndex: Int = 0,
        roundIndex: Int = 0,
        sessionPoints: Int = 0,
    ) {
```

with:

```kotlin
    fun openLesson(
        lessonId: String,
        trainerIndex: Int = 0,
        roundIndex: Int = 0,
        sessionPoints: Int = 0,
        expectedTrainerCount: Int? = null,
    ) {
```

Replace the body's trainer-building and index-clamping lines:

```kotlin
                val trainers = pack.tasksOf(lesson).map { schedule(it) }
                val counts = trainers.map { it.spec.rounds.size }
                val safeTrainer = trainerIndex.coerceIn(0, (trainers.size - 1).coerceAtLeast(0))
                val safeRound = roundIndex.coerceIn(0, (counts.getOrElse(safeTrainer) { 1 } - 1).coerceAtLeast(0))
                _ui.value = SessionUiState(
                    screen = AppScreen.Practice,
                    lessonId = lessonId,
                    trainers = trainers,
                    trainerIndex = safeTrainer,
                    roundIndex = safeRound,
                    points = progress.points,
                    sessionPoints = sessionPoints,
                    ready = true,
                )
                persistSnapshot()
```

with:

```kotlin
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
```

Update `bootstrap()`'s resume call:

```kotlin
            if (resumable) {
                openLesson(snapshot!!.lessonId, snapshot.trainerIndex, snapshot.roundIndex, snapshot.pointsEarned)
            } else {
```

becomes:

```kotlin
            if (resumable) {
                openLesson(
                    snapshot!!.lessonId,
                    snapshot.trainerIndex,
                    snapshot.roundIndex,
                    snapshot.pointsEarned,
                    expectedTrainerCount = snapshot.trainerCount,
                )
            } else {
```

Update `persistSnapshot()`:

```kotlin
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
            ),
        )
    }
```

becomes:

```kotlin
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
```

Add a branch to `successSpeakTextForCurrent`'s `when (round)`:

```kotlin
    fun successSpeakTextForCurrent(praise: Boolean): String = when (val round = _ui.value.currentRound) {
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
```

Leave `missCueForCurrent()` unchanged — its existing `else -> "Probiere eine andere Antwort"` fallback already covers `SymbolHuntRound` correctly: `SymbolHuntTrainer` already speaks the tapped tile's own lemma directly via `onSpeak` on every tap, so this generic fallback plays right after it on a miss, matching how misses already sound elsewhere in the app (no special-casing needed).

- [ ] **Step 4: Add `SessionProgression.resumeSafe` tests to `LessonSessionTest.kt`**

Add these tests to the `LessonSessionTest` class (after `progressionSkipsEmptyTrainers`):

```kotlin
    @Test
    fun resumeSafeRestartsWhenTrainerShapeChanged() {
        assertEquals(
            SessionStep(0, 0),
            SessionProgression.resumeSafe(expectedCount = 6, actualCount = 8, trainerIndex = 4, roundIndex = 1),
        )
    }

    @Test
    fun resumeSafeKeepsPositionWhenShapeMatches() {
        assertEquals(
            SessionStep(4, 1),
            SessionProgression.resumeSafe(expectedCount = 8, actualCount = 8, trainerIndex = 4, roundIndex = 1),
        )
    }

    @Test
    fun resumeSafeIgnoresShapeCheckWhenExpectedCountIsNull() {
        assertEquals(
            SessionStep(2, 0),
            SessionProgression.resumeSafe(expectedCount = null, actualCount = 8, trainerIndex = 2, roundIndex = 0),
        )
    }

    @Test
    fun resumeSafeClampsOutOfBoundsTrainerIndex() {
        assertEquals(
            SessionStep(7, 0),
            SessionProgression.resumeSafe(expectedCount = null, actualCount = 8, trainerIndex = 99, roundIndex = 0),
        )
    }
```

- [ ] **Step 5: Build and run the full suite**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/progress/ProgressModels.kt app/src/main/java/app/abcvorschule/session/SessionModels.kt app/src/main/java/app/abcvorschule/session/SessionViewModel.kt app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt
git commit -m "feat(session): wire SymbolHuntInsertion into openLesson with resume-shape safety

SessionSnapshot gains trainerCount so a resume can detect a trainer-list
shape change (this feature inserting hunt steps) that packId alone would
miss, and restart the lesson from the top instead of resuming into a
position that now points at a different trainer than the child left."
```

---

## Task 12: Jagd-specific documentation and final full-suite verification

**Files:**
- Modify: `AGENTS.md`
- Modify: `docs/PRODUCT_PRINCIPLES.md`

**Interfaces:** None — documentation only, plus a final verification pass.

- [ ] **Step 1: Add the Jagd bullet to `AGENTS.md`'s "Kind-UI-Regeln (Kurz)"**

Add a new bullet after the Trainer 4/5 bullet (before the Rechnen bullet):

> - Buchstaben-/Silben-Jagd (bis zu zwei Schritte je Lektion, nach Spurensucher bzw. Verschmelzer, sofern die Lektion den jeweiligen Trainer führt): **kein autorierter Content** — Runden werden zur Laufzeit aus den letter_trace-/syllable_merge-Runden derselben Lektion abgeleitet (`SymbolHuntInsertion`). Kacheln verstreuen sich über den Aufgabenbereich (Ausnahme zu Prinzip 9), die 5-Segment-Batterie sitzt im Antwortbereich; Fehltipp mischt neu ohne Batterieverlust, meldet aber genau einmal pro Runde einen Fehlversuch; nach 6 aufeinanderfolgenden Fehltipps: Auflösen. Batterie voll → lokaler „Weiter"-Button, erst danach die normale Erfolgs-Pipeline.

- [ ] **Step 2: Add the Jagd paragraph and exceptions to `docs/PRODUCT_PRINCIPLES.md`**

In section 3 ("Lernprogression"), add a new bullet to the six-trainer list (as item between Spurensucher and Silben-Verschmelzer, reflecting where it plays — but keep it visually distinct from the six *core* trainer types since it's derived, not authored):

> Zusätzlich, bis zu zweimal pro Lektion und ohne eigenen autorierten Content: eine **Buchstaben-Jagd** direkt nach dem Spurensucher und eine **Silben-Jagd** direkt nach dem Silben-Verschmelzer — jeweils nur, wenn die Lektion den entsprechenden Trainer führt und mindestens ein bereits bekanntes Vergleichssymbol existiert. Kind tippt alle Vorkommen des gesuchten Symbols in einem verstreuten Feld an; Treffer füllen eine Batterie, Fehltipp mischt neu ohne Batterieverlust.

In section 2 ("Kind-zentrierte Oberfläche"), add a note after the distractor-budget bullet:

> Ausnahme Buchstaben-/Silben-Jagd: Streufeld statt Distraktor-Budget (bis zu 6 Distraktor-Kacheln, teils wiederholt) — die Übung braucht mehr Ablenker als eine autorierte Tray-Aufgabe.

In section 9 ("Layout-Grundform der Übungen"), add a note after the layout bullets:

> Ausnahme Buchstaben-/Silben-Jagd: Kacheln verstreuen sich über den gesamten Aufgabenbereich statt in einer geordneten Antwortliste; die Batterie bleibt im Antwortbereich unten.

- [ ] **Step 3: Run the full test suite and a debug build**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```
Expected: both BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add AGENTS.md docs/PRODUCT_PRINCIPLES.md
git commit -m "docs: document the Buchstaben-/Silben-Jagd trainer and its two deliberate exceptions"
```

---

## Post-implementation note for whoever runs this plan

Manually run the app (`./gradlew :app:installDebug` or the `run`/simulator workflow already used in this project) and play through lesson 1 and at least one later lesson (e.g. `l06`, which has 4 focus letters) to confirm:
- The letter hunt appears once, right after the last letter-tracing step, with all of that lesson's focus letters as targets across its rounds.
- Lesson 1 has **no** syllable hunt (its only syllable, "ma", has an empty distractor pool at that point — expected, not a bug).
- A later lesson with an established syllable vocabulary **does** get a syllable hunt.
- Wrong taps reshuffle the field, speak the tapped tile, and don't reset the battery; the battery-full moment shows the local "Weiter" button before the shared star-burst plays.
