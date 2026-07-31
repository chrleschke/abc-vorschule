# Wort-Detektiv Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein siebter Trainer-Typ „Finde den Buchstaben / die Silbe im Wort", zur Laufzeit aus den `word_build`-Rounds einer Lektion abgeleitet und direkt nach dem letzten Wort-Bauer eingefügt.

**Architecture:** Vier Compose-freie Kernstücke (Graphem-Split, Derivation, Insertion, Tap-Logik) plus ein Screen, der keine Entscheidungen trifft. Genau dieselbe Trennung wie `SymbolHuntDerivation`/`SymbolHuntInsertion`/`SymbolHuntProgress`/`SymbolHuntTrainer` — die Buchstaben-Jagd ist in jeder Hinsicht das Vorbild, inklusive „wird nie autoriert, sondern abgeleitet".

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization (polymorphe sealed `TaskSpec`), JUnit 4 ohne Compose-Testrunner.

**Spec:** [`docs/superpowers/specs/2026-07-31-wort-detektiv-design.md`](../specs/2026-07-31-wort-detektiv-design.md) — bei Widerspruch gewinnt das Spec.

## Global Constraints

- **Deutsch in allen Prompts**; TTS-Pacing mit ` - ` als Trenner, wie `SoundPositionRound.missTts` es schon macht.
- **Kein Text, den ein Kind lesen muss, um zu handeln** (Prinzip 2). Buchstaben und Silben sind hier die Aufgabe selbst und daher erlaubt.
- **Keine Emojis in Buttons** (Prinzip 10). Nur Vektor-/ASCII-Icons.
- **Dark-only.** Farben ausschließlich aus `ui/theme/Color.kt` — keine neuen Hex-Werte, keine `Color(0xFF…)`-Literale in Trainer-Dateien.
- **Trefferflächen ≥ 56dp** (`WordFrameSizing.MinFrameDp`). Lieber zweizeilig umbrechen als kleiner werden.
- **Determinismus:** keine `Random` ohne festen Seed, kein `shuffled()` ohne Seed. Die Derivation ist vollständig deterministisch — dieselbe Lektion ergibt immer dieselben Runden.
- **Build:** `./gradlew :app:assembleDebug`
- **Tests:** `./gradlew :app:testDebugUnitTest`
- **Commit-Format:** `feat(trainer): …` / `test(trainer): …` / `docs: …`. Jeder Commit endet mit:
  ```
  Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
  ```
- **Nach jeder Task muss `./gradlew :app:testDebugUnitTest` grün sein.** Kein Task darf den Build oder bestehende Tests brechen — auch nicht „vorübergehend".

## File Structure

| Datei | Verantwortung | Compose-frei |
| --- | --- | --- |
| `content/WordGraphemes.kt` | **Neu.** Pack-abgeleitete Graphem-Tabelle, lektionsbeschränkt, plus `split` | ja |
| `content/SymbolInWordDerivation.kt` | **Neu.** Guard, Modus-Alternierung, Zielwahl, Prompt, Zielsymbol-Label | ja |
| `content/TaskSpecs.kt` | **Ändern.** `SymbolInWordSpec`/`SymbolInWordRound`, Enum-Wert, drei `when`-Zweige | ja |
| `content/ContentValidator.kt` | **Ändern.** Ein `when`-Zweig (synthetic-only) | ja |
| `session/SymbolInWordInsertion.kt` | **Neu.** Einfügen nach dem letzten `word_build` | ja |
| `session/SessionViewModel.kt` | **Ändern.** Insertion-Aufruf, Erfolgs-Sprechtext, Miss-Sprech-Ausnahme | ja |
| `ui/exercise/ResolveGate.kt` | **Neu.** Geteilte Resolve-Schwelle für Jagd und Detektiv | ja |
| `ui/exercise/SymbolHuntProgress.kt` | **Ändern.** Schwelle an `ResolveGate` delegieren | ja |
| `ui/exercise/SymbolInWordProgress.kt` | **Neu.** Tap-Logik, Trefferzustand, Resolve-Freigabe | ja |
| `ui/exercise/WordFrameSizing.kt` | **Ändern.** Zeilenumbruch-Rechnung | ja |
| `ui/exercise/SymbolInWordTrainer.kt` | **Neu.** Screen, Farben, Flug- und Dreh-Animation | nein |
| `ui/exercise/TrainerHost.kt` | **Ändern.** Ein Dispatch-Zweig | nein |
| `docs/PRODUCT_PRINCIPLES.md`, `AGENTS.md` | **Ändern.** §3, §9, Trainer-Kurzfassung | — |

Reihenfolge der Tasks folgt der Abhängigkeitskette: Split → Typen → Derivation → Insertion → Tap-Logik → Sizing → Screen → Doku. Task 2 hält den Build mit einem bewusst minimalen Screen grün; Task 7 ersetzt ihn.

---

### Task 1: WordGraphemes — lektionsbeschränkte Graphem-Tabelle

**Files:**
- Create: `app/src/main/java/app/abcvorschule/content/WordGraphemes.kt`
- Test: `app/src/test/java/app/abcvorschule/content/WordGraphemesTest.kt`

**Interfaces:**
- Consumes: `ContentPack`, `Atom`, `AtomKind`, `LetterTraceSpec`, `Lesson` (alle vorhanden)
- Produces:
  - `WordGraphemes.table(pack: ContentPack, lessonIndex: Int): List<String>`
  - `WordGraphemes.split(word: String, table: List<String>): List<String>`
  - `WordGraphemes.split(pack: ContentPack, lessonIndex: Int, word: String): List<String>`

**Hintergrund für den Implementierer:** Der Content-Pack hat keine `digraph`-Atome — jedes Mehrbuchstaben-Graphem (`Ei`, `Sch`, `ck`, `Pf`, `Qu`, …) ist ein `AtomKind.letter`-Atom, dessen `display` mehr als ein Zeichen hat. Ein Graphem gilt als „eingeführt", sobald ein `letter_trace`-Round einer Lektion mit `index <= lessonIndex` es als `atomId` verwendet. Die Beschränkung ist Korrektheitsbedingung, nicht Kosmetik: ohne sie verschmilzt „Nest" in L07 zu `N·e·st` und das gesuchte `S` wäre nicht mehr antippbar.

- [ ] **Step 1: Write the failing test**

Datei `app/src/test/java/app/abcvorschule/content/WordGraphemesTest.kt`:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordGraphemesTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun indexOf(lessonId: String) = pack.lesson(lessonId).index

    @Test
    fun tableIsEmptyBeforeTheFirstMultiLetterGraphemeIsIntroduced() {
        // "Ei" (l09) is the curriculum's first multi-letter grapheme, so nothing
        // before it may fuse two characters into one segment.
        assertTrue(WordGraphemes.table(pack, indexOf("l07")).isEmpty())
    }

    @Test
    fun tableHoldsEveryIntroducedMultiLetterGraphemeByTheLastLesson() {
        val table = WordGraphemes.table(pack, indexOf("l18"))
        assertTrue(
            "expected all taught digraphs, got $table",
            table.containsAll(listOf("Ei", "Ch", "Au", "Sch", "Eu", "ck", "Pf", "St", "Sp", "Qu")),
        )
    }

    @Test
    fun tableIsSortedLongestFirstSoLongestMatchWins() {
        val table = WordGraphemes.table(pack, indexOf("l18"))
        assertEquals(table.sortedByDescending { it.length }, table)
    }

    @Test
    fun stIsOneSegmentOnlyOnceItHasBeenIntroduced() {
        // The whole reason the table is lesson-scoped: l07 hunts the S in "Nest".
        assertEquals(listOf("N", "e", "s", "t"), WordGraphemes.split(pack, indexOf("l07"), "Nest"))
        assertEquals(listOf("St", "e", "r", "n"), WordGraphemes.split(pack, indexOf("l17"), "Stern"))
    }

    @Test
    fun pfIsASegmentWhichTheHardcodedSoundWordSegmentsTableCannotDo() {
        assertEquals(listOf("A", "pf", "e", "l"), WordGraphemes.split(pack, indexOf("l16"), "Apfel"))
    }

    @Test
    fun doubleVowelsStaySeparateBecauseTheyAreNotAtoms() {
        // "Erdbeere" -> E·r·d·b·e·e·r·e: a child hunting "all E" taps two separate
        // letters, not one fused "ee" block.
        assertEquals(
            listOf("E", "r", "d", "b", "e", "e", "r", "e"),
            WordGraphemes.split(pack, indexOf("l18"), "Erdbeere"),
        )
    }

    @Test
    fun umlautPlusVowelIsNotFusedBecauseAuDoesNotMatchAeu() {
        assertEquals(
            listOf("H", "ä", "u", "s", "e", "r"),
            WordGraphemes.split(pack, indexOf("l12"), "Häuser"),
        )
    }

    @Test
    fun longestMatchWinsOverAShorterPrefix() {
        assertEquals(listOf("Sch", "a", "f"), WordGraphemes.split(pack, indexOf("l13"), "Schaf"))
    }

    @Test
    fun matchingIsCaseInsensitive() {
        assertEquals(listOf("i", "ch"), WordGraphemes.split(pack, indexOf("l10"), "ich"))
    }

    @Test
    fun splitPreservesTheWordsOwnCasing() {
        // The segment carries the word's spelling, not the atom's display form.
        assertEquals(listOf("P", "a", "p", "a"), WordGraphemes.split(pack, indexOf("l03"), "Papa"))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*WordGraphemesTest*'
```

Expected: Compile error — `Unresolved reference: WordGraphemes`.

- [ ] **Step 3: Write minimal implementation**

Datei `app/src/main/java/app/abcvorschule/content/WordGraphemes.kt`:

```kotlin
package app.abcvorschule.content

/**
 * Splits a written word into the grapheme units the curriculum has actually
 * taught so far — the segments the Wort-Detektiv makes tappable (design doc §3).
 *
 * The table is derived from the pack instead of hardcoded (unlike
 * [app.abcvorschule.ui.exercise.SoundWordSegments], which only tints three train
 * carriages and can afford a fixed list). Two reasons:
 *
 * 1. **Correctness.** The table must be lesson-scoped. L07 builds "Nest" and hunts
 *    the `S`; a global table would fuse `st` into one segment (`N·e·st`) and the
 *    `S` would no longer be tappable — an unsolvable round. `St` is introduced in
 *    L17, so L07 correctly yields `N·e·s·t`.
 * 2. **Locale.** The pack owns its language. A Spanish pack that authors `ll` as an
 *    atom gets `ll`-as-one-unit for free, while `l` stays a single letter in words
 *    without it.
 *
 * The pack has no [AtomKind.digraph] atoms: every multi-letter grapheme the Fibel
 * teaches (`Ei`, `Sch`, `ck`, `Pf`, `Qu`, …) is an [AtomKind.letter] atom whose
 * [Atom.display] is longer than one character. "Introduced" means some
 * [LetterTraceSpec] round in a lesson at or before the current index traces it.
 */
object WordGraphemes {
    /**
     * Multi-letter graphemes taught in lessons with `index <= [lessonIndex]`,
     * longest first so that [split] does longest-match.
     */
    fun table(pack: ContentPack, lessonIndex: Int): List<String> =
        pack.lessons
            .filter { it.index <= lessonIndex }
            .flatMap { lesson ->
                lesson.taskIds
                    .mapNotNull { pack.tasks[it] }
                    .filterIsInstance<LetterTraceSpec>()
                    .flatMap { spec -> spec.rounds.map { it.atomId } }
            }
            .mapNotNull { pack.atoms[it] }
            .filter { it.kind == AtomKind.letter && it.display.length > 1 }
            .map { it.display }
            .distinct()
            .sortedByDescending { it.length }

    /**
     * Segments of [word] against an already-resolved [table]. Segments keep the
     * word's own casing — the child sees `P·a·p·a`, not `P·A·P·A` — while matching
     * against the table ignores case.
     */
    fun split(word: String, table: List<String>): List<String> {
        val result = mutableListOf<String>()
        var index = 0
        while (index < word.length) {
            val grapheme = table.firstOrNull { candidate ->
                word.regionMatches(index, candidate, 0, candidate.length, ignoreCase = true)
            }
            if (grapheme == null) {
                result += word[index].toString()
                index += 1
            } else {
                result += word.substring(index, index + grapheme.length)
                index += grapheme.length
            }
        }
        return result
    }

    fun split(pack: ContentPack, lessonIndex: Int, word: String): List<String> =
        split(word, table(pack, lessonIndex))
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*WordGraphemesTest*'
```

Expected: PASS, 10 Tests.

Wenn `tableIsEmptyBeforeTheFirstMultiLetterGraphemeIsIntroduced` fehlschlägt, prüfe, ob `pack.lessons` auch `planned`-Lektionen enthält — `taskIds` ist dort leer, sie können die Tabelle also nicht füllen; der Filter auf `index` genügt.

- [ ] **Step 5: Run the full suite (nothing else may break)**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/WordGraphemes.kt app/src/test/java/app/abcvorschule/content/WordGraphemesTest.kt
git commit -m "$(cat <<'EOF'
feat(content): pack-abgeleitete Graphem-Tabelle, auf eingeführte Lektionen begrenzt

Die Beschränkung ist Korrektheitsbedingung: ohne sie verschmilzt "Nest" in
L07 zu N·e·st und das gesuchte S wäre nicht mehr antippbar. St wird erst in
L17 eingeführt.

Nebenbei fällt "Erdbeere" korrekt in acht Segmente (ee ist kein Atom) und
"Apfel" in A·pf·e·l, was die hartkodierte Liste in SoundWordSegments nicht
kann. Ein spanisches Pack bekommt "ll" ohne Codeänderung.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 2: Spec-Typen, `when`-Zweige und ein minimaler Screen

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/TaskSpecs.kt` (Enum + neue Typen + 3 `when`)
- Modify: `app/src/main/java/app/abcvorschule/content/ContentValidator.kt:239`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt`
- Modify: `app/src/main/java/app/abcvorschule/session/SessionViewModel.kt`
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SymbolInWordTrainer.kt` (bewusst minimal, Task 7 ersetzt ihn)
- Test: `app/src/test/java/app/abcvorschule/content/SymbolInWordSpecTest.kt`

**Interfaces:**
- Consumes: nichts aus Task 1
- Produces:
  - `enum class SymbolInWordMode { letter, syllable }`
  - `data class SymbolInWordSpec(override val id: String, val rounds: List<SymbolInWordRound>) : TaskSpec`
  - `data class SymbolInWordRound(promptTts: String, wordAtomId: String, targetAtomId: String, mode: SymbolInWordMode, segments: List<String>, targetIndices: List<Int>) : TrainerRound`
  - `TrainerKind.symbol_in_word`
  - `@Composable fun SymbolInWordTrainer(round, roundIndex, pack, scaffoldFor, ttsAvailable, speaking, onSpeakPrompt, onSpeak, onResult, modifier)` — exakte Signatur in Step 3

**Warum `segments` und `targetIndices` im Round liegen:** die Derivation löst beides einmal auf, damit der Screen keine Entscheidung mehr trifft und die gesamte Logik ohne Compose-Testrunner prüfbar bleibt. Dasselbe Muster wie `SymbolHuntRound.distractorPool`, das ebenfalls zur Ableitungszeit fertig aufgelöst wird.

- [ ] **Step 1: Write the failing test**

Datei `app/src/test/java/app/abcvorschule/content/SymbolInWordSpecTest.kt`:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SymbolInWordSpecTest {
    private val round = SymbolInWordRound(
        promptTts = "Finde alle Buchstaben - P - im Wort - Papa.",
        wordAtomId = "papa",
        targetAtomId = "letter-p",
        mode = SymbolInWordMode.letter,
        segments = listOf("P", "a", "p", "a"),
        targetIndices = listOf(0, 2),
    )

    private val spec = SymbolInWordSpec(id = "l03:symbol_in_word", rounds = listOf(round))

    @Test
    fun kindMapsToTheNewTrainer() {
        assertEquals(TrainerKind.symbol_in_word, spec.kind)
    }

    @Test
    fun roundsAreReachableThroughTheSealedAccessor() {
        assertEquals(1, spec.roundCount)
        assertEquals(round, spec.round(0))
    }

    @Test
    fun scoresAgainstTheHuntedSymbolNotTheWord() {
        // The child practices the symbol; the word is only where it hides.
        assertEquals(listOf("letter-p"), round.scoredAtomIds())
    }

    @Test
    fun theNewKindIsNotPartOfTheAuthoredTrainerOrder() {
        // Runtime-derived like symbol_hunt: it must never be authorable, or the
        // validator's non-decreasing-rank check would have to know about it.
        assertFalse(ContentValidator.TrainerOrder.contains(TrainerKind.symbol_in_word))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*SymbolInWordSpecTest*'
```

Expected: Compile error — `Unresolved reference: SymbolInWordRound`.

- [ ] **Step 3: Add the types to `TaskSpecs.kt`**

`TrainerKind` erweitern (nach `symbol_hunt`):

```kotlin
enum class TrainerKind {
    sound_position,
    letter_trace,
    syllable_merge,
    word_build,
    sentence_order,
    count_add,
    symbol_hunt,
    symbol_in_word,
}
```

Hinter dem `SymbolHuntRound`-Block, vor `TasksFile`, einfügen:

```kotlin
// --- Wort-Detektiv — derived at runtime, never authored -----------------------

enum class SymbolInWordMode { letter, syllable }

/**
 * Never appears in authored JSON — SymbolInWordInsertion derives instances at
 * runtime from a lesson's own word_build rounds (design doc §1). `@Serializable`
 * for the same reason as [SymbolHuntSpec]: every member of a kotlinx.serialization
 * sealed hierarchy needs it for the polymorphic parent to compile.
 */
@Serializable
@SerialName("symbol_in_word")
data class SymbolInWordSpec(
    override val id: String,
    val rounds: List<SymbolInWordRound>,
) : TaskSpec

@Serializable
data class SymbolInWordRound(
    override val promptTts: String,
    /** Word the child searches; its [Atom.display] is what gets segmented. */
    val wordAtomId: String,
    /** The hunted letter or syllable. Scoring key, and source of the displayed label. */
    val targetAtomId: String,
    val mode: SymbolInWordMode,
    /**
     * The word split into tappable segments in reading order, carrying the word's
     * own casing ("P", "a", "p", "a"). Resolved at derivation time so the screen
     * makes no decisions — same contract as [SymbolHuntRound.distractorPool].
     */
    val segments: List<String>,
    /** Indices into [segments] that are hits. Never empty. */
    val targetIndices: List<Int>,
) : TrainerRound
```

Die drei `when`-Blöcke erweitern:

```kotlin
val TaskSpec.kind: TrainerKind
    get() = when (this) {
        // … bestehende Zweige …
        is SymbolHuntSpec -> TrainerKind.symbol_hunt
        is SymbolInWordSpec -> TrainerKind.symbol_in_word
    }

val TaskSpec.rounds: List<TrainerRound>
    get() = when (this) {
        // … bestehende Zweige …
        is SymbolHuntSpec -> rounds
        is SymbolInWordSpec -> rounds
    }

fun TrainerRound.scoredAtomIds(): List<String> = when (this) {
    // … bestehende Zweige …
    is SymbolHuntRound -> listOf(targetAtomId)
    is SymbolInWordRound -> listOf(targetAtomId)
}
```

- [ ] **Step 4: Add the validator branch**

In `ContentValidator.kt`, direkt unter `is SymbolHuntSpec -> Unit`:

```kotlin
                is SymbolHuntSpec -> Unit // synthetic-only; never appears in authored content
                is SymbolInWordSpec -> Unit // synthetic-only; never appears in authored content
```

- [ ] **Step 5: Add a deliberately minimal screen**

Datei `app/src/main/java/app/abcvorschule/ui/exercise/SymbolInWordTrainer.kt`. Dieser Screen ist **Absicht unfertig** — er hält den Build grün, während Tasks 3–6 die Logik bauen. Task 7 ersetzt den Body vollständig; die Signatur bleibt.

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.theme.SoftSand

/**
 * Wort-Detektiv: find the hunted letter/syllable inside a word the lesson just
 * built (design doc §5/§6).
 *
 * PLACEHOLDER BODY — the real screen (colours, placeholder strokes, fly and spin
 * animations) lands in a later task. Kept compilable so the sealed dispatch in
 * [TrainerHost] stays exhaustive while the pure logic is built underneath.
 */
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
    ExerciseStage(
        modifier = modifier,
        prompt = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
            Text(text = pack.atoms[round.targetAtomId]?.display.orEmpty(), fontSize = 54.sp, color = SoftSand)
            Row(horizontalArrangement = Arrangement.Center) {
                round.segments.forEach { segment ->
                    Text(text = segment, fontSize = 40.sp, color = SoftSand)
                }
            }
        },
        answers = {},
    )
}
```

- [ ] **Step 6: Add the dispatch branch**

In `TrainerHost.kt`, nach dem `is SymbolHuntRound`-Zweig:

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

Import ergänzen: `import app.abcvorschule.content.SymbolInWordRound`.

- [ ] **Step 7: Wire the two SessionViewModel speech sites**

Beide `when`-Blöcke haben ein `else`, brechen also **nicht** den Build — ohne diese Zweige bliebe der Erfolg aber stumm und das Fehlerfeedback würde abgeschnitten. Beide sind Pflicht.

In `successSpeakTextForCurrent`, neben dem `SymbolHuntRound`-Zweig:

```kotlin
        // Speak the *word*, not the symbol: the child already heard the symbol on
        // every tap, so the word is the new information and the didactic payoff.
        is SymbolInWordRound -> pack.atoms[round.wordAtomId]?.display ?: round.promptTts
```

In der Miss-Behandlung (`SessionViewModel.kt:474`) die Ausnahme verallgemeinern — der Detektiv spricht das getippte Segment ebenfalls synchron in der Composable, und `SpeechController` flusht seine Queue bei jedem `speak()`:

```kotlin
            // Both hunt trainers speak the tapped item synchronously in the
            // Composable before onResult arrives here. Setting speakCue would queue
            // the generic miss phrase right behind it, and SpeechController flushes
            // on every speak() — so the item name would get cut off before the child
            // heard it. Every other round type has no such synchronous speech.
            val speaksMissItself = _ui.value.currentRound.let {
                it is SymbolHuntRound || it is SymbolInWordRound
            }
            _ui.update {
                it.copy(
                    speakCue = if (speaksMissItself) it.speakCue else speakOverride ?: missCueForCurrent(),
                    points = progress.points,
                )
            }
```

Import ergänzen: `import app.abcvorschule.content.SymbolInWordRound`.

- [ ] **Step 8: Run the tests**

```bash
./gradlew :app:testDebugUnitTest --tests '*SymbolInWordSpecTest*'
```

Expected: PASS, 4 Tests.

- [ ] **Step 9: Verify the whole build and suite**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: BUILD SUCCESSFUL. Der neue Typ ist noch nirgends abgeleitet, also ändert sich am Verhalten der App nichts.

- [ ] **Step 10: Commit**

```bash
git add -A app/src/main/java/app/abcvorschule app/src/test/java/app/abcvorschule
git commit -m "$(cat <<'EOF'
feat(content): SymbolInWordSpec und die Dispatch-Zweige des Wort-Detektivs

Segments und targetIndices werden im Round mitgeführt, damit die Derivation
sie einmal auflöst und der Screen keine Entscheidung mehr trifft — dasselbe
Muster wie SymbolHuntRound.distractorPool.

Die beiden Sprech-Stellen im SessionViewModel brauchen den Zweig, obwohl ihr
when ein else hat: ohne ihn bleibt der Erfolg stumm, und ohne die
verallgemeinerte Miss-Ausnahme schneidet SpeechController den Namen des
getippten Segments ab, weil speak() die Queue flusht.

Der Screen ist bewusst noch ein Platzhalter.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 3: SymbolInWordDerivation — Zielwahl und Modus-Alternierung

**Files:**
- Create: `app/src/main/java/app/abcvorschule/content/SymbolInWordDerivation.kt`
- Test: `app/src/test/java/app/abcvorschule/content/SymbolInWordDerivationTest.kt`

**Interfaces:**
- Consumes: `WordGraphemes.split(pack, lessonIndex, word)` (Task 1); `SymbolInWordRound`, `SymbolInWordMode` (Task 2)
- Produces:
  - `SymbolInWordDerivation.buildRounds(pack: ContentPack, lesson: Lesson): List<SymbolInWordRound>`
  - `SymbolInWordDerivation.TargetLabel(val primary: String, val alternate: String?)`
  - `SymbolInWordDerivation.targetLabel(target: Atom, mode: SymbolInWordMode): TargetLabel`

**Die Regeln aus Spec §4, kompakt:**
1. Wörter = `word_build`-Rounds der Lektion in Autorierungsreihenfolge, dedupliziert über `targetAtomId`.
2. Guard: Wörter mit weniger als 2 Graphemen fallen weg (L22 baut „Ei" — das Wort *ist* die Antwort).
3. Gerader Rundenindex → Buchstaben-Modus, ungerader → Silben-Modus mit Rückfall auf Buchstabe.
4. Buchstaben-Ziel: nächstes Fokus-Graphem (aus `letter_trace`, zyklisch ab dem Zeiger), das im Wort vorkommt. Keines vorhanden → Runde fällt weg.
5. Silben-Ziel: Block mit `kind: syllable`, bevorzugt die Fokus-Silbe aus `syllable_merge.resultAtomId`.
6. Der Rundenindex zählt **produzierte** Runden, nicht betrachtete Wörter.

- [ ] **Step 1: Write the failing test**

Datei `app/src/test/java/app/abcvorschule/content/SymbolInWordDerivationTest.kt`:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolInWordDerivationTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun rounds(lessonId: String) =
        SymbolInWordDerivation.buildRounds(pack, pack.lesson(lessonId))

    // --- the gate that protects the whole feature ----------------------------

    @Test
    fun everyDerivedRoundOfEveryAuthoredLessonIsSolvable() {
        // A round whose target does not occur as a segment cannot be completed.
        // This is the test that would catch removing the lesson scoping from
        // WordGraphemes (l07 "Nest" would fuse to N·e·st and lose its S).
        pack.authoredLessons.forEach { lesson ->
            rounds(lesson.id).forEach { round ->
                assertTrue(
                    "lesson ${lesson.id}: ${round.promptTts} has no hit in ${round.segments}",
                    round.targetIndices.isNotEmpty(),
                )
                round.targetIndices.forEach { index ->
                    assertTrue(
                        "lesson ${lesson.id}: hit index $index out of bounds for ${round.segments}",
                        index in round.segments.indices,
                    )
                }
            }
        }
    }

    @Test
    fun everyDerivedRoundReferencesRealAtoms() {
        pack.authoredLessons.forEach { lesson ->
            rounds(lesson.id).forEach { round ->
                assertTrue("${round.wordAtomId} missing", pack.atoms.containsKey(round.wordAtomId))
                assertTrue("${round.targetAtomId} missing", pack.atoms.containsKey(round.targetAtomId))
            }
        }
    }

    @Test
    fun derivationIsDeterministic() {
        assertEquals(rounds("l03"), rounds("l03"))
    }

    // --- mode alternation ----------------------------------------------------

    @Test
    fun modesAlternateStartingWithLetter() {
        val l03 = rounds("l03")
        assertEquals(
            listOf(SymbolInWordMode.letter, SymbolInWordMode.syllable, SymbolInWordMode.letter),
            l03.map { it.mode },
        )
        assertEquals(listOf("letter-p", "pa", "letter-t"), l03.map { it.targetAtomId })
    }

    @Test
    fun anOddRoundFallsBackToLetterModeWhenTheWordHasNoSyllableBlock() {
        // l05: "Hut" (H·u·t) and "Ufo" (U·f·o) are built from single letters only.
        val l05 = rounds("l05")
        assertEquals(listOf(SymbolInWordMode.letter, SymbolInWordMode.letter), l05.map { it.mode })
        assertEquals(listOf("letter-u", "letter-f"), l05.map { it.targetAtomId })
    }

    // --- focus rotation ------------------------------------------------------

    @Test
    fun focusRotationAdvancesInsteadOfRepeatingTheSameGrapheme() {
        // l01 traces M then A: "Mama" takes M, "ma" must take A, not M again.
        assertEquals(listOf("letter-m", "letter-a"), rounds("l01").map { it.targetAtomId })
    }

    @Test
    fun rotationSkipsAFocusGraphemeTheWordDoesNotContain() {
        // l06 traces R then N. "Tor" holds no N, so it takes R again.
        assertEquals(listOf("letter-r", "letter-r"), rounds("l06").map { it.targetAtomId })
    }

    // --- multiple hits -------------------------------------------------------

    @Test
    fun allOccurrencesAreHitsAcrossCase() {
        val papa = rounds("l03").first()
        assertEquals(listOf("P", "a", "p", "a"), papa.segments)
        assertEquals(listOf(0, 2), papa.targetIndices)
        assertEquals("Finde alle Buchstaben - P - im Wort - Papa.", papa.promptTts)
    }

    @Test
    fun aRepeatedSyllableYieldsTwoHits() {
        val mimi = rounds("l02").last()
        assertEquals(SymbolInWordMode.syllable, mimi.mode)
        assertEquals(listOf("Mi", "mi"), mimi.segments)
        assertEquals(listOf(0, 1), mimi.targetIndices)
        assertEquals("Finde alle Silben - mi - im Wort - Mimi.", mimi.promptTts)
    }

    @Test
    fun aSingleHitUsesTheSingularPrompt() {
        val oma = rounds("l02").first()
        assertEquals(listOf("O", "m", "a"), oma.segments)
        assertEquals(listOf(0), oma.targetIndices)
        assertEquals("Finde den Buchstaben - O - im Wort - Oma.", oma.promptTts)
    }

    @Test
    fun theSyllableModeUsesTheAuthoredBlocksNotAGraphemeSplit() {
        val opa = rounds("l03")[1]
        assertEquals(listOf("O", "Pa"), opa.segments)
        assertEquals("Finde die Silbe - pa - im Wort - Opa.", opa.promptTts)
    }

    // --- guards --------------------------------------------------------------

    @Test
    fun aSingleGraphemeWordProducesNoRound() {
        // l22 builds "Ei", which is one segment — the word would be the answer.
        val l22 = rounds("l22")
        assertTrue("Ei must not become a round", l22.none { it.wordAtomId == "ei" })
        assertEquals(listOf("letter-au"), l22.map { it.targetAtomId })
    }

    @Test
    fun aWordBuiltTwiceProducesOneRound() {
        // l05 authors "Hut" in two word_build tasks.
        assertEquals(1, rounds("l05").count { it.wordAtomId == "hut" })
    }

    @Test
    fun aLessonWithoutWordBuildProducesNoRounds() {
        val lesson = pack.lesson("l01").copy(
            taskIds = pack.lesson("l01").taskIds.filter { pack.tasks[it] !is WordBuildSpec },
        )
        assertTrue(SymbolInWordDerivation.buildRounds(pack, lesson).isEmpty())
    }

    @Test
    fun aLessonWithoutLetterTraceProducesNoLetterRounds() {
        // No focus grapheme means no scoreable target, so the round falls away
        // rather than inventing an atom-less target.
        val lesson = pack.lesson("l05").copy(
            taskIds = pack.lesson("l05").taskIds.filter { pack.tasks[it] !is LetterTraceSpec },
        )
        assertTrue(SymbolInWordDerivation.buildRounds(pack, lesson).isEmpty())
    }

    // --- target label --------------------------------------------------------

    @Test
    fun aLetterTargetShowsBothCaseFormsAsAPair() {
        assertEquals(
            SymbolInWordDerivation.TargetLabel("P", "p"),
            SymbolInWordDerivation.targetLabel(pack.atom("letter-p"), SymbolInWordMode.letter),
        )
        assertEquals(
            SymbolInWordDerivation.TargetLabel("Sch", "sch"),
            SymbolInWordDerivation.targetLabel(pack.atom("letter-sch"), SymbolInWordMode.letter),
        )
    }

    @Test
    fun aLowercaseOnlyGraphemeShowsOneForm() {
        // "ck" is authored lowercase because no German word starts with it — a
        // form "Ck" does not exist and must not be taught.
        assertEquals(
            SymbolInWordDerivation.TargetLabel("ck", null),
            SymbolInWordDerivation.targetLabel(pack.atom("letter-ck"), SymbolInWordMode.letter),
        )
    }

    @Test
    fun aSyllableTargetShowsOnlyTheLowercaseAtomForm() {
        // An uppercase syllable only exists because it happens to start a word;
        // it is not a second learnable glyph.
        assertEquals(
            SymbolInWordDerivation.TargetLabel("mi", null),
            SymbolInWordDerivation.targetLabel(pack.atom("mi"), SymbolInWordMode.syllable),
        )
    }
}
```

**Hinweis für den Implementierer:** Die Atom-IDs in den Assertions (`letter-p`, `letter-sch`, `letter-ck`, `letter-au`, `mi`, `pa`) sind aus `atoms.json` zu verifizieren, **bevor** du implementierst:

```bash
python3 -c "
import json
a=json.load(open('app/src/main/assets/content/atoms.json'))['atoms']
for x in a:
    if x['display'] in ('P','Sch','ck','Au','Ei') or x['id'] in ('mi','pa','ma','hut','ei'):
        print(x['id'], '|', x['display'], '|', x['kind'])
"
```

Stimmt eine ID nicht, korrigiere den **Test**, nicht die Implementierung — und nur die ID, nicht die erwartete Semantik.

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*SymbolInWordDerivationTest*'
```

Expected: Compile error — `Unresolved reference: SymbolInWordDerivation`.

- [ ] **Step 3: Write the implementation**

Datei `app/src/main/java/app/abcvorschule/content/SymbolInWordDerivation.kt`:

```kotlin
package app.abcvorschule.content

/**
 * Pure derivation for the Wort-Detektiv (design doc §4): which symbol each word
 * hunts, in which mode, where its hits sit, and how the target is labelled. No
 * JSON is read beyond what [ContentPack] already parsed, and nothing here is
 * random — the same lesson always yields the same rounds.
 */
object SymbolInWordDerivation {
    private const val PromptLetterOne = "Finde den Buchstaben - %s - im Wort - %s."
    private const val PromptLetterMany = "Finde alle Buchstaben - %s - im Wort - %s."
    private const val PromptSyllableOne = "Finde die Silbe - %s - im Wort - %s."
    private const val PromptSyllableMany = "Finde alle Silben - %s - im Wort - %s."

    /**
     * Below this the word *is* the answer: L22 builds "Ei", a single grapheme, and
     * a round with one segment cannot be tapped wrong.
     */
    private const val MinSegments = 2

    /**
     * How the hunted symbol is shown. [alternate] carries the other case form so a
     * letter round can display "P / p" — the child learns the pair in the moment it
     * needs it, instead of having to bring the equivalence along. Null for
     * syllables and for graphemes that only exist in one form (`ck`, `ß`).
     */
    data class TargetLabel(val primary: String, val alternate: String?)

    fun targetLabel(target: Atom, mode: SymbolInWordMode): TargetLabel = when (mode) {
        // An uppercase syllable exists only because it happens to start a word —
        // not a second learnable glyph, so it is never shown (design doc §2).
        SymbolInWordMode.syllable -> TargetLabel(target.display, null)
        SymbolInWordMode.letter -> {
            val lower = target.display.lowercase()
            TargetLabel(target.display, lower.takeIf { it != target.display })
        }
    }

    fun buildRounds(pack: ContentPack, lesson: Lesson): List<SymbolInWordRound> {
        val specs = lesson.taskIds.mapNotNull { pack.tasks[it] }
        val focusLetterAtomIds = specs.filterIsInstance<LetterTraceSpec>()
            .flatMap { spec -> spec.rounds.map { it.atomId } }
            .distinct()
        val focusSyllableAtomIds = specs.filterIsInstance<SyllableMergeSpec>()
            .flatMap { spec -> spec.rounds.map { it.resultAtomId } }
            .toSet()
        val words = specs.filterIsInstance<WordBuildSpec>()
            .flatMap { it.rounds }
            .distinctBy { it.targetAtomId }

        val rounds = mutableListOf<SymbolInWordRound>()
        var focusCursor = 0
        words.forEach { word ->
            val wordAtom = pack.atoms[word.targetAtomId] ?: return@forEach
            val graphemes = WordGraphemes.split(pack, lesson.index, wordAtom.display)
            if (graphemes.size < MinSegments) return@forEach

            // The index that drives alternation counts *produced* rounds, not words
            // looked at — a skipped word must not flip the next word's mode.
            val wantsSyllable = rounds.size % 2 == 1
            val built = (if (wantsSyllable) syllableRound(pack, wordAtom, word, focusSyllableAtomIds) else null)
                ?: letterRound(pack, wordAtom, graphemes, focusLetterAtomIds, focusCursor)
                ?: return@forEach

            rounds += built.round
            if (built.usedFocusIndex != null) {
                focusCursor = (built.usedFocusIndex + 1) % focusLetterAtomIds.size
            }
        }
        return rounds
    }

    private data class Built(val round: SymbolInWordRound, val usedFocusIndex: Int?)

    private fun letterRound(
        pack: ContentPack,
        wordAtom: Atom,
        graphemes: List<String>,
        focusLetterAtomIds: List<String>,
        focusCursor: Int,
    ): Built? {
        if (focusLetterAtomIds.isEmpty()) return null
        // Rotate so a lesson with two focus letters practices both instead of
        // hammering the first one in every word.
        val rotated = focusLetterAtomIds.indices.map { (focusCursor + it) % focusLetterAtomIds.size }
        val focusIndex = rotated.firstOrNull { index ->
            val display = pack.atoms[focusLetterAtomIds[index]]?.display
            display != null && graphemes.any { it.equals(display, ignoreCase = true) }
        } ?: return null

        val targetAtomId = focusLetterAtomIds[focusIndex]
        val display = pack.atom(targetAtomId).display
        val hits = graphemes.indices.filter { graphemes[it].equals(display, ignoreCase = true) }
        val template = if (hits.size > 1) PromptLetterMany else PromptLetterOne
        return Built(
            SymbolInWordRound(
                promptTts = template.format(display, wordAtom.display),
                wordAtomId = wordAtom.id,
                targetAtomId = targetAtomId,
                mode = SymbolInWordMode.letter,
                segments = graphemes,
                targetIndices = hits,
            ),
            usedFocusIndex = focusIndex,
        )
    }

    private fun syllableRound(
        pack: ContentPack,
        wordAtom: Atom,
        word: WordBuildRound,
        focusSyllableAtomIds: Set<String>,
    ): Built? {
        if (word.blocks.size < MinSegments) return null
        // word_build blocks are not reliable syllables ("Hä·u·s·e·r", "Ha·l·l·o"),
        // so only blocks backed by an actual syllable atom may be hunted — asking
        // for "die Silbe l im Wort Hallo" would be nonsense.
        val syllableBlocks = word.blocks.filter { pack.atoms[it.atomId]?.kind == AtomKind.syllable }
        if (syllableBlocks.isEmpty()) return null

        val targetBlock = syllableBlocks.firstOrNull { it.atomId in focusSyllableAtomIds }
            ?: syllableBlocks.first()
        val target = pack.atom(targetBlock.atomId)
        val segments = word.blocks.map { it.display }
        val hits = segments.indices.filter { segments[it].equals(target.display, ignoreCase = true) }
        val template = if (hits.size > 1) PromptSyllableMany else PromptSyllableOne
        return Built(
            SymbolInWordRound(
                promptTts = template.format(target.display, wordAtom.display),
                wordAtomId = wordAtom.id,
                targetAtomId = targetBlock.atomId,
                mode = SymbolInWordMode.syllable,
                segments = segments,
                targetIndices = hits,
            ),
            usedFocusIndex = null,
        )
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*SymbolInWordDerivationTest*'
```

Expected: PASS.

Wenn eine Erwartung an eine konkrete Lektion abweicht, vergleiche zuerst mit der Anhang-Tabelle des Specs (§Anhang) — sie ist aus dem echten Content simuliert und die verbindliche Referenz.

- [ ] **Step 5: Add two appendix spot-checks to the test file**

Der Spec-Anhang ist die verbindliche Referenz. Zwei Lektionen, die je eine eigene Eigenschaft festhalten, kommen als Tests dazu (nicht als Sichtprüfung):

```kotlin
    @Test
    fun lessonThirteenRepeatsItsOnlyFocusGraphemeAndThenTakesTheSyllable() {
        // L13 has a single focus grapheme, so the rotation has nothing to rotate to.
        // Not a bug — the lesson is literally called "Sch (Der Dreifachlaut)".
        assertEquals(
            listOf("letter-sch", "letter-sch", "letter-sch", "schu"),
            rounds("l13").map { it.targetAtomId },
        )
    }

    @Test
    fun lessonSixteenHuntsBothItsDigraphsIncludingTheOneSoundWordSegmentsCannotSplit() {
        assertEquals(listOf("letter-ck", "letter-pf", "letter-pf"), rounds("l16").map { it.targetAtomId })
        assertEquals(listOf("A", "pf", "e", "l"), rounds("l16")[1].segments)
    }
```

Auch hier gilt: stimmt eine Atom-ID nicht, korrigiere die ID im Test, nicht die erwartete Semantik.

```bash
./gradlew :app:testDebugUnitTest --tests '*SymbolInWordDerivationTest*'
```

Expected: PASS.

- [ ] **Step 6: Run the full suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/SymbolInWordDerivation.kt app/src/test/java/app/abcvorschule/content/SymbolInWordDerivationTest.kt
git commit -m "$(cat <<'EOF'
feat(content): Zielwahl und Modus-Alternierung des Wort-Detektivs

Eine Runde pro eingeführtem Wort, Modus wechselt zwischen Buchstabe und
Silbe. Der Alternierungsindex zählt produzierte Runden, nicht betrachtete
Wörter — ein per Guard verworfenes Wort darf den Modus des nächsten nicht
umkippen.

Silben-Ziele nur aus Blöcken, die ein Atom mit kind=syllable referenzieren:
word_build-Blöcke sind keine verlässlichen Silben ("Ha·l·l·o"), und "finde
die Silbe l im Wort Hallo" wäre Unsinn.

Kommt kein Fokus-Graphem im Wort vor, fällt die Runde weg statt auf ein
Ziel ohne Atom-ID auszuweichen — ein nullbares targetAtomId wäre ein
untestbarer Defensivpfad im Datenmodell.

Der Gate-Test prüft über alle autorierten Lektionen, dass jede Runde einen
Treffer hat; er fängt das Entfernen der Lektionsbeschränkung in
WordGraphemes.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 4: SymbolInWordInsertion und die Session-Verdrahtung

**Files:**
- Create: `app/src/main/java/app/abcvorschule/session/SymbolInWordInsertion.kt`
- Modify: `app/src/main/java/app/abcvorschule/session/SessionViewModel.kt:159-164`
- Test: `app/src/test/java/app/abcvorschule/session/SymbolInWordInsertionTest.kt`

**Interfaces:**
- Consumes: `SymbolInWordDerivation.buildRounds(pack, lesson)` (Task 3); `ScheduledTrainer`, `WordBuildSpec`
- Produces: `SymbolInWordInsertion.insertSymbolInWord(trainers: List<ScheduledTrainer>, pack: ContentPack, lesson: Lesson): List<ScheduledTrainer>`

**Muster:** `session/SymbolHuntInsertion.kt` lesen — dieselbe Form (`indexOfLast { … }`, `add(index + 1, trainer)`), nur ein Trainer statt zwei.

- [ ] **Step 1: Write the failing test**

Datei `app/src/test/java/app/abcvorschule/session/SymbolInWordInsertionTest.kt`:

```kotlin
package app.abcvorschule.session

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.SentenceOrderSpec
import app.abcvorschule.content.SymbolInWordSpec
import app.abcvorschule.content.WordBuildSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolInWordInsertionTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun scheduled(lessonId: String) =
        pack.tasksOf(pack.lesson(lessonId)).map { ScheduledTrainer(spec = it) }

    private fun insert(lessonId: String) = SymbolInWordInsertion.insertSymbolInWord(
        scheduled(lessonId),
        pack,
        pack.lesson(lessonId),
    )

    @Test
    fun theDetectiveLandsRightAfterTheLastWordBuilder() {
        val result = insert("l03")
        val detectiveIndex = result.indexOfFirst { it.spec is SymbolInWordSpec }
        val lastWordBuild = result.indexOfLast { it.spec is WordBuildSpec }
        assertTrue("no detective inserted", detectiveIndex >= 0)
        assertEquals(lastWordBuild + 1, detectiveIndex)
    }

    @Test
    fun theDetectiveComesBeforeTheSentenceArchitect() {
        val result = insert("l03")
        val detectiveIndex = result.indexOfFirst { it.spec is SymbolInWordSpec }
        val firstSentence = result.indexOfFirst { it.spec is SentenceOrderSpec }
        if (firstSentence >= 0) {
            assertTrue("detective must precede sentence_order", detectiveIndex < firstSentence)
        }
    }

    @Test
    fun exactlyOneDetectiveIsInserted() {
        assertEquals(1, insert("l03").count { it.spec is SymbolInWordSpec })
    }

    @Test
    fun theOriginalTrainersKeepTheirOrder() {
        val original = scheduled("l03").map { it.spec.id }
        val kept = insert("l03").filter { it.spec !is SymbolInWordSpec }.map { it.spec.id }
        assertEquals(original, kept)
    }

    @Test
    fun theSpecIdIsDerivedFromTheLesson() {
        val detective = insert("l03").first { it.spec is SymbolInWordSpec }
        assertEquals("l03:symbol_in_word", detective.spec.id)
    }

    @Test
    fun aLessonWithoutWordBuildGetsNoDetective() {
        val lesson = pack.lesson("l01").let { base ->
            base.copy(taskIds = base.taskIds.filter { pack.tasks[it] !is WordBuildSpec })
        }
        val trainers = lesson.taskIds.map { ScheduledTrainer(spec = pack.task(it)) }
        val result = SymbolInWordInsertion.insertSymbolInWord(trainers, pack, lesson)
        assertTrue(result.none { it.spec is SymbolInWordSpec })
    }

    @Test
    fun everyAuthoredLessonGetsExactlyOneDetectiveWithAtLeastOneRound() {
        pack.authoredLessons.forEach { lesson ->
            val result = insert(lesson.id)
            val detectives = result.filter { it.spec is SymbolInWordSpec }
            assertEquals("lesson ${lesson.id}", 1, detectives.size)
            assertTrue(
                "lesson ${lesson.id} detective has no rounds",
                (detectives.single().spec as SymbolInWordSpec).rounds.isNotEmpty(),
            )
        }
    }

    @Test
    fun theDetectiveAndTheHuntsCoexistInEitherInsertionOrder() {
        val base = scheduled("l03")
        val huntFirst = SymbolInWordInsertion.insertSymbolInWord(
            SymbolHuntInsertion.insertSymbolHunts(base, pack, "l03", pack.lesson("l03").index),
            pack,
            pack.lesson("l03"),
        )
        val detectiveFirst = SymbolHuntInsertion.insertSymbolHunts(
            SymbolInWordInsertion.insertSymbolInWord(base, pack, pack.lesson("l03")),
            pack,
            "l03",
            pack.lesson("l03").index,
        )
        assertEquals(huntFirst.map { it.spec.id }, detectiveFirst.map { it.spec.id })
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*SymbolInWordInsertionTest*'
```

Expected: Compile error — `Unresolved reference: SymbolInWordInsertion`.

- [ ] **Step 3: Write the implementation**

Datei `app/src/main/java/app/abcvorschule/session/SymbolInWordInsertion.kt`:

```kotlin
package app.abcvorschule.session

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Lesson
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.content.SymbolInWordSpec
import app.abcvorschule.content.WordBuildSpec

/**
 * Splices the Wort-Detektiv into a lesson's scheduled trainer list at runtime —
 * no JSON authoring, no ContentValidator involvement (design doc §1). It sits
 * right after the last word_build trainer, so the child hunts a symbol in a word
 * it has just finished building.
 *
 * A lesson with no word_build trainer, or one whose derived rounds all fall away
 * (single-grapheme word, no focus grapheme present), gets no detective at all —
 * the same silent degradation as [SymbolHuntInsertion].
 *
 * Order-independent with respect to [SymbolHuntInsertion]: the hunts land after
 * letter_trace and syllable_merge, which both rank before word_build, so neither
 * insertion can move the other's anchor.
 */
object SymbolInWordInsertion {
    fun insertSymbolInWord(
        trainers: List<ScheduledTrainer>,
        pack: ContentPack,
        lesson: Lesson,
    ): List<ScheduledTrainer> {
        val anchor = trainers.indexOfLast { it.spec is WordBuildSpec }
        if (anchor < 0) return trainers
        val rounds = SymbolInWordDerivation.buildRounds(pack, lesson)
        if (rounds.isEmpty()) return trainers
        val detective = ScheduledTrainer(
            spec = SymbolInWordSpec(id = "${lesson.id}:symbol_in_word", rounds = rounds),
        )
        return trainers.toMutableList().apply { add(anchor + 1, detective) }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*SymbolInWordInsertionTest*'
```

Expected: PASS.

- [ ] **Step 5: Wire it into SessionViewModel**

In `SessionViewModel.kt` den bestehenden Insertion-Aufruf (Zeile ~159) ersetzen:

```kotlin
                val trainers = SymbolInWordInsertion.insertSymbolInWord(
                    SymbolHuntInsertion.insertSymbolHunts(
                        pack.tasksOf(lesson).map { schedule(it) },
                        pack,
                        lesson.id,
                        lesson.index,
                    ),
                    pack,
                    lesson,
                )
```

- [ ] **Step 6: Run the full suite**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Expected: PASS. Achte besonders auf `LessonSessionTest` — es zählt möglicherweise Trainer pro Lektion und muss dann um den Detektiv erweitert werden. Falls ein Test wegen der gestiegenen Trainer-Zahl fehlschlägt, **passe den Test an** (die neue Zahl ist das gewollte Verhalten) und erkläre die Änderung in der Commit-Message.

`SessionProgression.resumeSafe` fängt gespeicherte Sessions ab, deren Trainer-Anzahl sich seit dem Snapshot geändert hat — genau dieser Fall. Kein Extra-Code nötig, aber verifiziere, dass `resumeSafe` beim Öffnen einer alten Session auf Trainer 0 zurückfällt statt in eine verschobene Position zu springen.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/abcvorschule/session app/src/test/java/app/abcvorschule/session
git commit -m "$(cat <<'EOF'
feat(session): Wort-Detektiv nach dem letzten Wort-Bauer einfügen

Das Kind sucht das Symbol in einem Wort, das es gerade fertig gebaut hat.
Reihenfolge-unabhängig zu SymbolHuntInsertion: die Jagden ankern an
letter_trace und syllable_merge, die beide vor word_build rangieren, also
kann keine Insertion den Anker der anderen verschieben — ein Test hält das
fest.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 5: ResolveGate und SymbolInWordProgress

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/ResolveGate.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntProgress.kt:36`
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SymbolInWordProgress.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/SymbolInWordProgressTest.kt`

**Interfaces:**
- Consumes: `SymbolInWordRound` (Task 2)
- Produces:
  - `ResolveGate.Threshold: Int`
  - `data class SymbolInWordState(segmentCount: Int, targetIndices: Set<Int>, collected: List<Int>, consecutiveMisses: Int, reportedMissThisRound: Boolean, wrongIndex: Int?, wrongNonce: Int)`

**Warum `collected` eine `List` und kein `Set` ist:** die Flug-Animation in Task 7 muss wissen, *welches* Segment gerade eingesammelt wurde, um von dort zu starten. Ein `Set` verliert die Reihenfolge — tippt das Kind erst Index 2 und dann Index 0, wäre das letzte Element eines sortierten Sets die 2, obwohl der Flug bei der 0 beginnen muss. Die Liste erhält die Tipp-Reihenfolge, und `collected.last()` ist damit immer das gerade getroffene Segment.
  - `enum class SymbolInWordTapOutcome { Collected, RoundComplete, Miss, MissAlreadyReported, Ignored }`
  - `data class SymbolInWordTapResult(state: SymbolInWordState, outcome: SymbolInWordTapOutcome)`
  - `SymbolInWordProgress.initialState(round): SymbolInWordState`
  - `SymbolInWordProgress.tap(state, index): SymbolInWordTapResult`
  - `SymbolInWordProgress.resolveAvailable(state): Boolean`
  - `SymbolInWordProgress.resolve(state): SymbolInWordState`
  - `SymbolInWordState.remainingSlots: Int`

**Warum `wrongNonce`:** die Dreh-Animation muss auch dann neu starten, wenn das Kind zweimal dasselbe falsche Segment tippt. Ein reiner `wrongIndex` würde sich nicht ändern und Compose würde die Animation nicht neu auslösen. Der Nonce zählt bei jedem Fehltipp hoch und ist der Animations-Key.

- [ ] **Step 1: Write the failing test**

Datei `app/src/test/java/app/abcvorschule/ui/exercise/SymbolInWordProgressTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import app.abcvorschule.content.SymbolInWordMode
import app.abcvorschule.content.SymbolInWordRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolInWordProgressTest {
    /** "Papa" hunting P: hits at 0 and 2. */
    private val papa = SymbolInWordRound(
        promptTts = "Finde alle Buchstaben - P - im Wort - Papa.",
        wordAtomId = "papa",
        targetAtomId = "letter-p",
        mode = SymbolInWordMode.letter,
        segments = listOf("P", "a", "p", "a"),
        targetIndices = listOf(0, 2),
    )

    /** "Oma" hunting O: a single hit at 0. */
    private val oma = SymbolInWordRound(
        promptTts = "Finde den Buchstaben - O - im Wort - Oma.",
        wordAtomId = "oma",
        targetAtomId = "letter-o",
        mode = SymbolInWordMode.letter,
        segments = listOf("O", "m", "a"),
        targetIndices = listOf(0),
    )

    @Test
    fun initialStateHasEverySlotEmpty() {
        val state = SymbolInWordProgress.initialState(papa)
        assertEquals(setOf(0, 2), state.targetIndices)
        assertTrue(state.collected.isEmpty())
        assertEquals(2, state.remainingSlots)
    }

    @Test
    fun aCorrectTapCollectsTheSegment() {
        val result = SymbolInWordProgress.tap(SymbolInWordProgress.initialState(papa), 0)
        assertEquals(SymbolInWordTapOutcome.Collected, result.outcome)
        assertEquals(listOf(0), result.state.collected)
        assertEquals(1, result.state.remainingSlots)
    }

    @Test
    fun theLastCorrectTapCompletesTheRound() {
        var state = SymbolInWordProgress.initialState(papa)
        state = SymbolInWordProgress.tap(state, 2).state
        val result = SymbolInWordProgress.tap(state, 0)
        assertEquals(SymbolInWordTapOutcome.RoundComplete, result.outcome)
        assertEquals(0, result.state.remainingSlots)
    }

    @Test
    fun collectedKeepsTapOrderSoTheFlightKnowsWhereItStarted() {
        // Tapping the later hit first must not reorder history: the flight
        // animation starts from collected.last().
        var state = SymbolInWordProgress.initialState(papa)
        state = SymbolInWordProgress.tap(state, 2).state
        assertEquals(2, state.collected.last())
        state = SymbolInWordProgress.tap(state, 0).state
        assertEquals(listOf(2, 0), state.collected)
    }

    @Test
    fun aSingleHitRoundCompletesOnTheFirstCorrectTap() {
        val result = SymbolInWordProgress.tap(SymbolInWordProgress.initialState(oma), 0)
        assertEquals(SymbolInWordTapOutcome.RoundComplete, result.outcome)
    }

    @Test
    fun tappingAnAlreadyCollectedSegmentIsANoOp() {
        var state = SymbolInWordProgress.initialState(papa)
        state = SymbolInWordProgress.tap(state, 0).state
        val result = SymbolInWordProgress.tap(state, 0)
        assertEquals(SymbolInWordTapOutcome.Ignored, result.outcome)
        assertEquals(listOf(0), result.state.collected)
        assertEquals(0, result.state.consecutiveMisses)
    }

    @Test
    fun anOutOfBoundsIndexIsIgnored() {
        val state = SymbolInWordProgress.initialState(papa)
        assertEquals(SymbolInWordTapOutcome.Ignored, SymbolInWordProgress.tap(state, 9).outcome)
        assertEquals(SymbolInWordTapOutcome.Ignored, SymbolInWordProgress.tap(state, -1).outcome)
    }

    @Test
    fun aWrongTapLosesNoProgress() {
        var state = SymbolInWordProgress.initialState(papa)
        state = SymbolInWordProgress.tap(state, 0).state
        val result = SymbolInWordProgress.tap(state, 1)
        assertEquals(SymbolInWordTapOutcome.Miss, result.outcome)
        assertEquals(listOf(0), result.state.collected)
        assertEquals(1, result.state.remainingSlots)
    }

    @Test
    fun onlyTheFirstMissOfARoundIsReported() {
        var state = SymbolInWordProgress.initialState(papa)
        assertEquals(SymbolInWordTapOutcome.Miss, SymbolInWordProgress.tap(state, 1).outcome)
        state = SymbolInWordProgress.tap(state, 1).state
        assertEquals(SymbolInWordTapOutcome.MissAlreadyReported, SymbolInWordProgress.tap(state, 3).outcome)
    }

    @Test
    fun everyWrongTapBumpsTheNonceSoTheSpinRestarts() {
        var state = SymbolInWordProgress.initialState(papa)
        state = SymbolInWordProgress.tap(state, 1).state
        val first = state.wrongNonce
        state = SymbolInWordProgress.tap(state, 1).state
        assertEquals(1, state.wrongNonce - first)
        assertEquals(1, state.wrongIndex)
    }

    @Test
    fun consecutiveMissesKeepCountingAfterReportingStops() {
        // Reporting is for adaptivity, counting is for the resolve gate — two jobs.
        var state = SymbolInWordProgress.initialState(papa)
        repeat(3) { state = SymbolInWordProgress.tap(state, 1).state }
        assertEquals(3, state.consecutiveMisses)
        assertTrue(state.reportedMissThisRound)
    }

    @Test
    fun aCorrectTapResetsTheConsecutiveMissCount() {
        var state = SymbolInWordProgress.initialState(papa)
        repeat(3) { state = SymbolInWordProgress.tap(state, 1).state }
        state = SymbolInWordProgress.tap(state, 0).state
        assertEquals(0, state.consecutiveMisses)
    }

    @Test
    fun resolveUnlocksOnlyAfterTheSharedThreshold() {
        var state = SymbolInWordProgress.initialState(papa)
        repeat(ResolveGate.Threshold - 1) { state = SymbolInWordProgress.tap(state, 1).state }
        assertFalse(SymbolInWordProgress.resolveAvailable(state))
        state = SymbolInWordProgress.tap(state, 1).state
        assertTrue(SymbolInWordProgress.resolveAvailable(state))
    }

    @Test
    fun resolveFillsEverySlot() {
        val resolved = SymbolInWordProgress.resolve(SymbolInWordProgress.initialState(papa))
        assertEquals(listOf(0, 2), resolved.collected)
        assertEquals(0, resolved.remainingSlots)
    }

    @Test
    fun theHuntSharesTheSameResolveThreshold() {
        assertEquals(ResolveGate.Threshold, SymbolHuntProgress.ResolveThreshold)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*SymbolInWordProgressTest*'
```

Expected: Compile error — `Unresolved reference: SymbolInWordProgress`.

- [ ] **Step 3: Extract the shared threshold**

Datei `app/src/main/java/app/abcvorschule/ui/exercise/ResolveGate.kt`:

```kotlin
package app.abcvorschule.ui.exercise

/**
 * When "Zeig mir" appears in the two symbol-hunting trainers: after this many
 * *consecutive* misses, reset by any correct tap. Shared so the two trainers
 * cannot drift apart — a child who learns the button appears "after a while"
 * should meet the same patience everywhere.
 *
 * Deliberately not shared with the Spurensucher, which counts cumulative
 * off-road excursions rather than consecutive misses and only reuses the number.
 */
object ResolveGate {
    const val Threshold = 6
}
```

In `SymbolHuntProgress.kt` die Konstante delegieren (der öffentliche Name bleibt, damit bestehende Tests unverändert laufen):

```kotlin
    const val ResolveThreshold = ResolveGate.Threshold
```

- [ ] **Step 4: Write the progress logic**

Datei `app/src/main/java/app/abcvorschule/ui/exercise/SymbolInWordProgress.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import app.abcvorschule.content.SymbolInWordRound

/**
 * Tap state of one Wort-Detektiv round (design doc §6). Indices address
 * [SymbolInWordRound.segments]; the round itself is immutable, so only the
 * bookkeeping lives here.
 */
data class SymbolInWordState(
    val segmentCount: Int,
    val targetIndices: Set<Int>,
    /** Hits in the order the child found them. A Set would lose that order, and the
     * flight animation needs to know which segment was just collected. */
    val collected: List<Int> = emptyList(),
    val consecutiveMisses: Int = 0,
    val reportedMissThisRound: Boolean = false,
    /** Segment that was tapped wrong last, for the spin animation. */
    val wrongIndex: Int? = null,
    /**
     * Bumped on every wrong tap. The animation is keyed on this rather than on
     * [wrongIndex] alone, so tapping the *same* wrong segment twice replays the
     * spin instead of sitting still.
     */
    val wrongNonce: Int = 0,
) {
    val remainingSlots: Int get() = targetIndices.size - collected.size
}

enum class SymbolInWordTapOutcome { Collected, RoundComplete, Miss, MissAlreadyReported, Ignored }

data class SymbolInWordTapResult(val state: SymbolInWordState, val outcome: SymbolInWordTapOutcome)

/**
 * A wrong tap costs nothing — no slot, no points, no reshuffle (unlike the
 * Buchstaben-Jagd, where reshuffling the scatter field is the feedback; here the
 * word must stay put, because its letter order is the whole point).
 *
 * Two independent counters, same split as [SymbolHuntProgress]: reporting stops
 * after the first miss of a round so a child tapping through an eight-segment
 * word cannot wreck the atom's statistics, while the consecutive-miss count keeps
 * running because it only gates "Zeig mir".
 */
object SymbolInWordProgress {
    fun initialState(round: SymbolInWordRound): SymbolInWordState = SymbolInWordState(
        segmentCount = round.segments.size,
        targetIndices = round.targetIndices.toSet(),
    )

    fun tap(state: SymbolInWordState, index: Int): SymbolInWordTapResult {
        if (index !in 0 until state.segmentCount || index in state.collected) {
            return SymbolInWordTapResult(state, SymbolInWordTapOutcome.Ignored)
        }
        if (index in state.targetIndices) {
            val collected = state.collected + index
            val next = state.copy(collected = collected, consecutiveMisses = 0, wrongIndex = null)
            val outcome = if (collected.size >= state.targetIndices.size) {
                SymbolInWordTapOutcome.RoundComplete
            } else {
                SymbolInWordTapOutcome.Collected
            }
            return SymbolInWordTapResult(next, outcome)
        }
        val alreadyReported = state.reportedMissThisRound
        val next = state.copy(
            consecutiveMisses = state.consecutiveMisses + 1,
            reportedMissThisRound = true,
            wrongIndex = index,
            wrongNonce = state.wrongNonce + 1,
        )
        val outcome = if (alreadyReported) {
            SymbolInWordTapOutcome.MissAlreadyReported
        } else {
            SymbolInWordTapOutcome.Miss
        }
        return SymbolInWordTapResult(next, outcome)
    }

    fun resolveAvailable(state: SymbolInWordState): Boolean =
        state.consecutiveMisses >= ResolveGate.Threshold

    /** Resolve: drop every target into its slot, award nothing. */
    fun resolve(state: SymbolInWordState): SymbolInWordState =
        state.copy(collected = state.targetIndices.sorted(), wrongIndex = null)
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*SymbolInWordProgressTest*' --tests '*SymbolHuntProgressTest*'
```

Expected: PASS (beide Klassen).

- [ ] **Step 6: Run the full suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/ResolveGate.kt app/src/main/java/app/abcvorschule/ui/exercise/SymbolInWordProgress.kt app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntProgress.kt app/src/test/java/app/abcvorschule/ui/exercise/SymbolInWordProgressTest.kt
git commit -m "$(cat <<'EOF'
feat(trainer): Tap-Logik des Wort-Detektivs, Resolve-Schwelle geteilt

Ein Fehltipp kostet nichts und mischt vor allem nicht das Feld — anders als
in der Jagd, wo das Neumischen das Feedback ist. Hier muss das Wort stehen
bleiben, weil seine Buchstabenreihenfolge die Aufgabe ist.

wrongNonce zählt bei jedem Fehltipp hoch, damit die Dreh-Animation auch beim
zweiten Tipp auf dasselbe falsche Segment neu startet; ein reiner wrongIndex
würde sich nicht ändern.

ResolveGate hält die Schwelle für beide Jagd-Trainer an einer Stelle, damit
sie nicht auseinanderdriften.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 6: WordFrameSizing — Zeilenumbruch für lange Wörter

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/WordFrameSizing.kt`
- Modify: `app/src/test/java/app/abcvorschule/ui/exercise/WordFrameSizingTest.kt`

**Interfaces:**
- Consumes: nichts
- Produces:
  - `WordFrameSizing.maxPerRow(available: Float): Int`
  - `WordFrameSizing.rowCount(available: Float, segmentCount: Int): Int`
  - `WordFrameSizing.segmentsPerRow(available: Float, segmentCount: Int): Int`

**Regel:** so viele Segmente pro Reihe, wie bei `MinFrameDp` + `MinGapDp` passen. Reichen sie nicht, wird umgebrochen und die Segmente werden gleichmäßig auf die Reihen verteilt — **nie** unter `MinFrameDp` geschrumpft. Bei 396dp nutzbarer Breite ergibt das 6 pro Reihe: `Häuser` (6 Segmente) bleibt einzeilig, `Xylophon` (8) bricht in zwei Reihen à 4.

- [ ] **Step 1: Write the failing test**

An `app/src/test/java/app/abcvorschule/ui/exercise/WordFrameSizingTest.kt` anhängen (bestehende Tests unverändert lassen):

```kotlin
    // --- row wrapping for long words ----------------------------------------

    /** ExerciseStage caps content at 420dp and pads 12dp per side. */
    private val stageWidth = 396f

    @Test
    fun sixSegmentsStayOnOneRow() {
        // "Häuser" -> H·ä·u·s·e·r, the longest word in the current content.
        assertEquals(1, WordFrameSizing.rowCount(stageWidth, 6))
        assertEquals(6, WordFrameSizing.segmentsPerRow(stageWidth, 6))
    }

    @Test
    fun eightSegmentsWrapIntoTwoBalancedRows() {
        // "Xylophon" -> X·y·l·o·p·h·o·n. Tappability beats staying on one line.
        assertEquals(2, WordFrameSizing.rowCount(stageWidth, 8))
        assertEquals(4, WordFrameSizing.segmentsPerRow(stageWidth, 8))
    }

    @Test
    fun sevenSegmentsWrapAndTheFirstRowTakesTheExtra() {
        assertEquals(2, WordFrameSizing.rowCount(stageWidth, 7))
        assertEquals(4, WordFrameSizing.segmentsPerRow(stageWidth, 7))
    }

    @Test
    fun frameWidthNeverDropsBelowTheTouchFloorAfterWrapping() {
        val perRow = WordFrameSizing.segmentsPerRow(stageWidth, 8)
        assertTrue(WordFrameSizing.frameWidthDp(stageWidth, perRow) >= WordFrameSizing.MinFrameDp)
    }

    @Test
    fun aSingleSegmentNeedsOneRow() {
        assertEquals(1, WordFrameSizing.rowCount(stageWidth, 1))
        assertEquals(1, WordFrameSizing.segmentsPerRow(stageWidth, 1))
    }

    @Test
    fun anEmptyWordDoesNotDivideByZero() {
        assertEquals(1, WordFrameSizing.rowCount(stageWidth, 0))
        assertEquals(1, WordFrameSizing.segmentsPerRow(stageWidth, 0))
    }

    @Test
    fun aVeryNarrowStageStillFitsOneSegmentPerRow() {
        assertEquals(1, WordFrameSizing.maxPerRow(20f))
        assertEquals(4, WordFrameSizing.rowCount(20f, 4))
    }
```

Nötige Imports im Test prüfen: `org.junit.Assert.assertEquals`, `org.junit.Assert.assertTrue`.

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*WordFrameSizingTest*'
```

Expected: Compile error — `Unresolved reference: rowCount`.

- [ ] **Step 3: Write the implementation**

An `WordFrameSizing` anhängen:

```kotlin
    /**
     * How many segments fit in one row at the touch-target floor. At least one, so
     * an absurdly narrow stage degrades to one segment per row instead of zero.
     */
    fun maxPerRow(available: Float): Int {
        val perSegment = MinFrameDp + MinGapDp
        return (((available + MinGapDp) / perSegment).toInt()).coerceAtLeast(1)
    }

    /**
     * Rows needed for [segmentCount] segments. The Wort-Detektiv wraps long words
     * ("Xylophon" -> X·y·l·o·p·h·o·n) instead of shrinking below [MinFrameDp]:
     * a preschooler has to be able to hit the segment, and a word on two lines is
     * still readable while a 40dp target is not hittable.
     */
    fun rowCount(available: Float, segmentCount: Int): Int {
        if (segmentCount <= 0) return 1
        val perRow = maxPerRow(available)
        return ((segmentCount + perRow - 1) / perRow).coerceAtLeast(1)
    }

    /**
     * Segments per row, balanced across [rowCount] rows so two rows read as one
     * word broken in half rather than a full row plus an orphan. An uneven count
     * puts the extra segment in the earlier row.
     */
    fun segmentsPerRow(available: Float, segmentCount: Int): Int {
        if (segmentCount <= 0) return 1
        val rows = rowCount(available, segmentCount)
        return ((segmentCount + rows - 1) / rows).coerceAtLeast(1)
    }
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*WordFrameSizingTest*'
```

Expected: PASS.

Prüfe die Arithmetik bei Zweifel: `maxPerRow(396) = floor((396+4)/60) = floor(6.67) = 6`. `rowCount(396, 8) = ceil(8/6) = 2`. `segmentsPerRow(396, 8) = ceil(8/2) = 4`. `segmentsPerRow(396, 7) = ceil(7/2) = 4`.

- [ ] **Step 5: Run the full suite**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/WordFrameSizing.kt app/src/test/java/app/abcvorschule/ui/exercise/WordFrameSizingTest.kt
git commit -m "$(cat <<'EOF'
feat(trainer): Zeilenumbruch statt Schrumpfen bei langen Wörtern

Der Wort-Detektiv bricht ab sieben Segmenten in zwei ausgeglichene Reihen um,
statt unter die 56dp-Trefferfläche zu gehen. Ein Vorschulkind muss das Segment
treffen können; ein zweizeiliges Wort bleibt lesbar, ein 40dp-Ziel bleibt
untreffbar.

Im aktuellen Content greift der Umbruch nie ("Häuser" ist mit sechs Segmenten
das längste Wort) — er ist der Pfad für Wörter wie "Xylophon".

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 7: Der Screen

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SymbolInWordTrainer.kt` (Platzhalter-Body aus Task 2 ersetzen)

**Interfaces:**
- Consumes: `SymbolInWordProgress`, `SymbolInWordState`, `SymbolInWordTapOutcome`, `ResolveGate` (Task 5); `SymbolInWordDerivation.targetLabel` (Task 3); `WordFrameSizing.segmentsPerRow`/`frameWidthDp`/`glyphSp` (Task 6); `ExerciseStage`, `TaskPromptChrome`, `AbcResolveButton` (vorhanden)
- Produces: nichts für spätere Tasks — die Signatur aus Task 2 bleibt unverändert

**Kein Unit-Test:** das Projekt hat keinen Compose-Testrunner (kein `androidTest`-Verzeichnis, alle Tests sind reines JUnit). Die gesamte Entscheidungslogik liegt bereits getestet in Tasks 1–6; dieser Task verdrahtet sie nur. Verifikation über Build plus die manuelle Checkliste in Step 4.

**Referenz-Implementierung:** `ui/exercise/SymbolHuntTrainer.kt` lesen. Sie zeigt das Muster für Round-Key-basiertes `remember`, `ExerciseStage`-Nutzung, Haptik, Celebration-Hold und Auto-Advance. Übernimm es, statt es neu zu erfinden.

- [ ] **Step 1: Replace the placeholder body**

Vollständiger Inhalt von `app/src/main/java/app/abcvorschule/ui/exercise/SymbolInWordTrainer.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.SoftCoral
import app.abcvorschule.ui.theme.SoftGold
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky
import kotlinx.coroutines.delay

/** Segment colours cycle; the palette only marks boundaries, it carries no meaning,
 * so repeating it on an eight-segment word is harmless. */
private val SegmentPalette = listOf(SoftMint, SoftCoral, SoftSky, SoftGold, SoftSand)

/** A collected segment stays visible but spent — this is the "completed colour"
 * and the "no longer tappable" affordance in one treatment. */
private val CollectedSegmentAlpha = 0.35f

/** How long a wrong segment spins around its own centre. */
private const val SpinMs = 450

/** How long a collected glyph travels from the word down onto its slot. The flight
 * is the causal link for a child who cannot read: "I tapped that, and that moved
 * there." */
private const val FlightMs = 350

/** Celebration before handing off, matching SymbolHuntTrainer's battery hold so
 * both hunts feel the same. */
private const val CelebrationHoldMs = 900L

/** ExerciseStage caps its content at 420dp and pads 12dp per side. */
private const val StageContentDp = 396f

/**
 * Wort-Detektiv: find the hunted letter or syllable inside a word the lesson just
 * built (design doc §5/§6).
 *
 * The word is rendered as plain coloured glyphs without frames — it must read as a
 * word, not as a tray. The placeholder strokes underneath collect the hits; they
 * are receipts, not answer options, which is why they are bare strokes rather than
 * the Wort-Bauer's rounded 22dp slots.
 *
 * All decisions (segmentation, targets, hit indices, tap outcomes, row wrapping)
 * are made in the unit-tested pure layers; this file only draws and animates.
 */
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
    val roundKey = "$roundIndex-${round.wordAtomId}-${round.targetAtomId}"
    var state by remember(roundKey) { mutableStateOf(SymbolInWordProgress.initialState(round)) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    var complete by remember(roundKey) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val target = pack.atoms[round.targetAtomId]
    val label = target?.let { SymbolInWordDerivation.targetLabel(it, round.mode) }
    val scaffold = scaffoldFor(round.targetAtomId)

    fun handleTap(index: Int) {
        if (resolved || complete) return
        onSpeak(round.segments.getOrNull(index) ?: return)
        val result = SymbolInWordProgress.tap(state, index)
        state = result.state
        when (result.outcome) {
            SymbolInWordTapOutcome.Miss -> {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onResult(false, false, listOf(round.targetAtomId))
            }
            SymbolInWordTapOutcome.MissAlreadyReported ->
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            SymbolInWordTapOutcome.RoundComplete -> complete = true
            SymbolInWordTapOutcome.Collected,
            SymbolInWordTapOutcome.Ignored,
            -> Unit
        }
    }

    // The full set of slots IS the success signal, so a "Weiter" tap would only add
    // a dead end for a child who cannot read the button. The delay sits in front of
    // onResult because reporting starts the spoken success phase, which must not
    // talk over the celebration.
    LaunchedEffect(complete) {
        if (!complete) return@LaunchedEffect
        delay(CelebrationHoldMs)
        onResult(true, false, listOf(round.targetAtomId))
    }

    // Positions are captured in window space and differenced against the wrapping
    // Box, because a flight crosses ExerciseStage's two separate Columns and there
    // is no shared layout node to animate inside.
    var rootOffset by remember(roundKey) { mutableStateOf(Offset.Zero) }
    val segmentCenters = remember(roundKey) { mutableMapOf<Int, Offset>() }
    val slotCenters = remember(roundKey) { mutableMapOf<Int, Offset>() }

    Box(modifier = modifier.onGloballyPositioned { rootOffset = it.positionInWindow() }) {
        ExerciseStage(
            prompt = {
                TaskPromptChrome(
                    title = null,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeakPrompt = onSpeakPrompt,
                )
                if (label != null) {
                    TargetLabelRow(
                        label = label,
                        onClick = { onSpeak(target.display) },
                    )
                }
                WordSegments(
                    round = round,
                    state = state,
                    enabled = !complete && !resolved,
                    onTap = ::handleTap,
                    onSegmentPlaced = { index, center -> segmentCenters[index] = center },
                )
            },
            answers = {
                SlotRow(
                    round = round,
                    state = state,
                    label = label,
                    showSilhouette = scaffold == ScaffoldLevel.Beginner,
                    celebrate = complete,
                    onSlotPlaced = { ordinal, center -> slotCenters[ordinal] = center },
                )
                if (SymbolInWordProgress.resolveAvailable(state) && !resolved && !complete) {
                    AbcResolveButton(
                        onClick = {
                            resolved = true
                            state = SymbolInWordProgress.resolve(state)
                            onResult(false, true, listOf(round.targetAtomId))
                        },
                    )
                }
            },
        )
    }
}

/** The hunted symbol, as a case pair ("P / p") for letters and a single lowercase
 * form for syllables (design doc §2). */
@Composable
private fun TargetLabelRow(
    label: SymbolInWordDerivation.TargetLabel,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.clickable { onClick() }.testTag("detective_target"),
    ) {
        Text(text = label.primary, fontSize = AbcDimens.letterSp, color = SoftSand)
        if (label.alternate != null) {
            // A separator, not something to read: half size and dimmed so the two
            // letters dominate.
            Text(
                text = "/",
                fontSize = AbcDimens.letterSp / 2,
                color = MutedText.copy(alpha = 0.45f),
            )
            Text(text = label.alternate, fontSize = AbcDimens.letterSp, color = SoftSand)
        }
    }
}

/** The word as tappable coloured glyphs, wrapped into balanced rows when a word is
 * too long to keep 56dp targets on one line. */
@Composable
private fun WordSegments(
    round: SymbolInWordRound,
    state: SymbolInWordState,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onSegmentPlaced: (Int, Offset) -> Unit,
) {
    val perRow = WordFrameSizing.segmentsPerRow(StageContentDp, round.segments.size)
    val frameWidth = WordFrameSizing.frameWidthDp(StageContentDp, perRow)
    val glyphSp = WordFrameSizing.glyphSp(frameWidth, round.segments.maxOfOrNull { it.length } ?: 1)
    val gap = WordFrameSizing.gapDp(StageContentDp, perRow)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(gap.dp),
    ) {
        round.segments.withIndex().chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                row.forEach { (index, segment) ->
                    SegmentGlyph(
                        segment = segment,
                        index = index,
                        state = state,
                        frameWidthDp = frameWidth,
                        glyphSp = glyphSp,
                        enabled = enabled,
                        onTap = onTap,
                        onPlaced = onSegmentPlaced,
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentGlyph(
    segment: String,
    index: Int,
    state: SymbolInWordState,
    frameWidthDp: Float,
    glyphSp: Float,
    enabled: Boolean,
    onTap: (Int) -> Unit,
    onPlaced: (Int, Offset) -> Unit,
) {
    val collected = index in state.collected
    val isWrong = state.wrongIndex == index
    // An Animatable driven off the nonce, not animateFloatAsState off a target
    // value: the spin must replay when the child taps the *same* wrong segment
    // twice, and a target value derived from state would be unchanged in that case.
    // snapTo(0f) afterwards leaves the glyph upright rather than at a multiple of
    // 360° that grows all round.
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(state.wrongNonce) {
        if (isWrong && state.wrongNonce > 0) {
            rotation.snapTo(0f)
            rotation.animateTo(360f, tween(durationMillis = SpinMs))
            rotation.snapTo(0f)
        }
    }
    Box(
        modifier = Modifier
            .width(frameWidthDp.dp)
            .height(AbcDimens.kidTouch)
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.size
                onPlaced(
                    index,
                    coordinates.positionInWindow() + Offset(bounds.width / 2f, bounds.height / 2f),
                )
            }
            .clickable(enabled = enabled && !collected) { onTap(index) }
            .testTag("detective_segment_$index"),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = segment,
            fontSize = glyphSp.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (collected) MutedText.copy(alpha = CollectedSegmentAlpha) else SegmentPalette[index % SegmentPalette.size],
            modifier = Modifier.rotate(rotation.value),
        )
    }
}

/** One bare stroke per hit, filled with the collected symbol in SoftGold — the same
 * colour as stars and points, so a filled slot reads as "earned". */
@Composable
private fun SlotRow(
    round: SymbolInWordRound,
    state: SymbolInWordState,
    label: SymbolInWordDerivation.TargetLabel?,
    showSilhouette: Boolean,
    celebrate: Boolean,
    onSlotPlaced: (Int, Offset) -> Unit,
) {
    val glow = if (celebrate) {
        val transition = rememberInfiniteTransition(label = "detective_slot_glow")
        val animated by transition.animateFloat(
            initialValue = 0.5f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 500, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "detective_slot_glow_value",
        )
        animated
    } else {
        1f
    }
    // Estimated from the same glyph-advance fraction WordFrameSizing uses, so a
    // three-letter target like "Sch" gets a visibly wider stroke than "e" — the
    // stroke's width is what tells a child "Sch is one thing, not three".
    val slotWidth = (AbcDimens.syllableSp.value * WordFrameSizing.GlyphAspect *
        (label?.primary?.length ?: 1)).coerceAtLeast(40f).dp
    Row(
        modifier = Modifier.fillMaxWidth().testTag("detective_slots"),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
    ) {
        val total = round.targetIndices.size
        repeat(total) { ordinal ->
            val filled = ordinal < state.collected.size
            Box(
                modifier = Modifier
                    .width(slotWidth)
                    .height(56.dp)
                    .onGloballyPositioned { coordinates ->
                        onSlotPlaced(
                            ordinal,
                            coordinates.positionInWindow() +
                                Offset(coordinates.size.width / 2f, coordinates.size.height / 2f),
                        )
                    },
                contentAlignment = Alignment.BottomCenter,
            ) {
                if (filled && label != null) {
                    Text(
                        text = label.primary,
                        fontSize = AbcDimens.syllableSp,
                        color = SoftGold,
                        modifier = Modifier.alpha(if (celebrate) glow else 1f),
                    )
                } else if (showSilhouette && label != null) {
                    // Scaffold "Beginner": the target sits in the slot as a silhouette
                    // (Prinzip 6 — Silhouette vs. Lücke, per slot rather than globally).
                    Text(
                        text = label.primary,
                        fontSize = AbcDimens.syllableSp,
                        color = SoftSand.copy(alpha = 0.18f),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(
                            color = if (filled) SoftGold else MutedText,
                            shape = RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
    }
}
```

**Der Flug ist in dieser Fassung noch nicht animiert** — `segmentCenters`/`slotCenters` werden gemessen, aber der fliegende Glyph fehlt. Das ist bewusst der letzte Schritt, weil er ohne die gemessenen Positionen nicht baubar ist. Step 2 ergänzt ihn.

- [ ] **Step 2: Add the flying glyph overlay**

Im `Box`, **nach** dem `ExerciseStage`-Aufruf (damit es darüber liegt), einfügen:

```kotlin
        // The collected glyph travels from its place in the word onto its slot. It
        // is drawn here, above ExerciseStage, because the two endpoints live in the
        // stage's two separate Columns and no layout node contains both.
        val flight = state.collected.size - 1
        if (flight >= 0 && !resolved) {
            val from = segmentCenters[state.collected.lastOrNull()]
            val to = slotCenters[flight]
            if (from != null && to != null && label != null) {
                val progress by animateFloatAsState(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = FlightMs),
                    label = "detective_flight_$flight",
                )
                val density = LocalDensity.current
                val current = Offset(
                    x = from.x + (to.x - from.x) * progress,
                    y = from.y + (to.y - from.y) * progress,
                ) - rootOffset
                Text(
                    text = label.primary,
                    fontSize = AbcDimens.syllableSp,
                    color = SoftGold,
                    modifier = Modifier
                        .offset(
                            x = with(density) { current.x.toDp() },
                            y = with(density) { current.y.toDp() },
                        )
                        .alpha(1f - progress * 0.15f),
                )
            }
        }
```

`import androidx.compose.ui.layout.positionInWindow` ergänzen, falls der Compiler es verlangt (in neueren Compose-Versionen ist `positionInWindow()` eine Methode auf `LayoutCoordinates` und braucht keinen Extra-Import).

**Wenn die Flug-Animation sich als fragil erweist** (falsche Positionen bei Rotation, Ruckeln, oder `animateFloatAsState` startet nicht neu pro Treffer): implementiere sie stattdessen als `Animatable`, das in einem `LaunchedEffect(state.collected.size)` von 0 auf 1 läuft. **Lass sie nicht weg** — sie ist laut Spec §5 der Grund, warum die Striche die Batterie ersetzen. Wenn du sie nicht zum Laufen bringst, halte das als offenen Punkt fest und melde es, statt still darauf zu verzichten.

- [ ] **Step 3: Build**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

Häufige Fehler an dieser Stelle:
- `positionInWindow()` nicht auflösbar → `androidx.compose.ui.layout.positionInWindow` importieren.
- `Offset` minus `Offset` nicht auflösbar → `androidx.compose.ui.geometry.Offset` ist importiert; der Operator existiert.
- `chunked` auf `withIndex()` → `withIndex()` liefert ein `Iterable<IndexedValue<String>>`; `.toList().chunked(perRow)` falls der Compiler meckert.

- [ ] **Step 4: Verify the full suite still passes**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS.

- [ ] **Step 5: Manual verification checklist**

Die Logik ist unit-getestet, das Verhalten am Gerät nicht. Notiere zu jedem Punkt „ok" oder was abweicht:

1. Lektion 3 (`P & T`) starten und bis nach dem Wort-Bauer durchspielen. Der Detektiv erscheint mit dem Wort `Papa`.
2. Das Zielsymbol zeigt `P / p`, der Schrägstrich ist erkennbar kleiner und blasser.
3. Zwei Striche stehen unten.
4. Tipp auf `a` → das `a` dreht sich einmal komplett, nichts füllt sich, es gibt Haptik.
5. Zweiter Tipp auf dasselbe `a` → es dreht sich **wieder** (der Nonce arbeitet).
6. Tipp auf das große `P` → wird vorgesprochen, dimmt ab, ein goldenes `P` liegt auf dem ersten Strich.
7. Erneuter Tipp auf das gedimmte `P` → nichts passiert.
8. Tipp auf das kleine `p` → zweiter Strich füllt sich, die Striche pulsieren golden, dann spricht die App „Papa" und der Stern erscheint.
9. Sechs Fehltipps in Folge → „Zeig mir" erscheint; Tipp darauf füllt beide Striche ohne Punkte.
10. Lektion 2 prüfen: zweite Runde ist `Mimi` mit Ziel `mi` (klein, kein Paar) und zwei Strichen.
11. Zurück/Weiter-Chevrons funktionieren in beide Richtungen aus dem Detektiv heraus.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SymbolInWordTrainer.kt
git commit -m "$(cat <<'EOF'
feat(trainer): Screen des Wort-Detektivs

Das Wort steht als farbige Glyphen ohne Rahmen — es soll wie ein Wort
aussehen, nicht wie ein Tray. Die Striche darunter sammeln die Treffer ein;
sie sind Quittungen, keine Wahloptionen, deshalb bloße Grundstriche statt der
gerundeten 22dp-Schablonen des Wort-Bauers.

Der Flug vom Wort auf den Strich liegt als Overlay über ExerciseStage, weil
seine beiden Endpunkte in dessen zwei getrennten Columns leben und kein
Layout-Knoten beide enthält. Positionen werden in Fensterkoordinaten gemessen
und gegen die umschließende Box verrechnet.

Alle Entscheidungen liegen in den unit-getesteten reinen Schichten; diese
Datei zeichnet und animiert nur.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

### Task 8: Dokumentation nachziehen

**Files:**
- Modify: `docs/PRODUCT_PRINCIPLES.md` (§3, §9, Ableitungstabelle)
- Modify: `AGENTS.md` (Abschnitt „Kind-UI-Regeln & Trainer-Typen")

**Interfaces:** keine

`AGENTS.md` verlangt, dass der Agent die Doku von sich aus mitzieht, wenn sich UX-Regeln oder Trainer-Typen ändern. Beides trifft zu.

- [ ] **Step 1: Extend PRODUCT_PRINCIPLES §3**

Nach dem Absatz über die Buchstaben-/Silben-Jagd (Zeile ~47) einfügen:

```markdown
Ebenfalls abgeleitet und nicht autoriert: der **Wort-Detektiv** direkt nach dem letzten
Wort-Bauer — „Finde den Buchstaben / die Silbe im Wort". Eine Runde pro eingeführtem Wort,
der Modus wechselt zwischen Buchstabe und Silbe. Das Wort steht in farbige Segmente
zerlegt da, jedes antippbar; Treffer wandern auf Platzhalter-Striche im Antwortbereich, ein
Fehltipp dreht das Segment einmal um seinen Mittelpunkt und kostet nichts. Der
Buchstaben-Modus zeigt das Ziel als Formenpaar (`P / p`), damit „finde alle P" in „Papa"
nicht schwerer ist als es aussieht; Silben stehen nur klein.
```

- [ ] **Step 2: Extend PRODUCT_PRINCIPLES §9**

Nach der bestehenden Jagd-Ausnahme (Zeile ~143) einfügen:

```markdown
- Ausnahme Wort-Detektiv: der Antwortbereich trägt **Quittungs-Striche statt Wahloptionen**.
  Sie sind bloße Grundstriche ohne Rahmen und ohne Tray — die einzige Symbolquelle ist das
  Wort im Aufgabenblock. Damit sind sie von den Schablonen des Wort-Bauers unterscheidbar.
```

- [ ] **Step 3: Extend the derivation table at the end of PRODUCT_PRINCIPLES**

Zwei Zeilen anhängen:

```markdown
| Zeigt ein abgeleiteter Trainer ein Graphem, das die Lektion noch nicht kennt?  | Nein → Graphem-Tabelle ist lektionsbeschränkt |
| Verlangt der Wort-Detektiv einen Tipp auf eine Form, die er nicht zeigt?       | Nein → Buchstaben als Paar `P / p`  |
```

- [ ] **Step 4: Update AGENTS.md**

Den Stichpunkt zur Jagd erweitern:

```markdown
- **Buchstaben-/Silben-Jagd**: Optional bis zu 2× pro Lektion, keine separaten Autorierungen — wird zur Laufzeit aus letter_trace/syllable_merge abgeleitet (`SymbolHuntInsertion`). Batterie voll → kurze Feier, dann automatisch weiter — kein „Weiter"-Button, das Kind kann ihn nicht lesen.
- **Wort-Detektiv**: „Finde den Buchstaben / die Silbe im Wort", ebenfalls abgeleitet (`SymbolInWordInsertion`), eine Runde pro `word_build`-Wort, direkt nach dem letzten Wort-Bauer. Grapheme kommen aus `WordGraphemes` — pack-abgeleitet und auf bereits eingeführte Lektionen beschränkt, sonst würde „Nest" in L07 zu `N·e·st` verschmelzen und das gesuchte `S` unantippbar machen.
```

- [ ] **Step 5: Verify no stale claim remains**

```bash
grep -n "Sechs Trainer-Typen\|sechs Trainer" docs/PRODUCT_PRINCIPLES.md AGENTS.md
```

Die „sechs Trainer-Typen" beziehen sich auf die **autorierten** Typen und bleiben korrekt — Jagd und Detektiv sind abgeleitete Zusätze, keine siebten und achten autorierten Typen. Prüfe, dass der Text das an jeder Fundstelle klar sagt; wenn eine Stelle „sechs" ohne diese Einschränkung behauptet, präzisiere sie.

- [ ] **Step 6: Commit**

```bash
git add docs/PRODUCT_PRINCIPLES.md AGENTS.md
git commit -m "$(cat <<'EOF'
docs: Wort-Detektiv in Produktprinzipien und Agent-Guide

Zweiter abgeleiteter Zusatz-Trainer neben der Jagd, plus die dritte Ausnahme
von Prinzip 9: der Antwortbereich trägt hier Quittungs-Striche statt
Wahloptionen.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>
EOF
)"
```

---

## Nach dem letzten Task

1. `./gradlew :app:assembleDebug :app:testDebugUnitTest` — beide grün.
2. Code-Review über die Branch-Diffs (`superpowers:requesting-code-review`).
3. `superpowers:verification-before-completion`.

Offene Punkte, die bewusst **nicht** Teil dieses Plans sind:

- Keine Compose-UI-Tests. Das Projekt hat keinen Compose-Testrunner; ihn einzuführen wäre ein eigenes Vorhaben, nicht ein Nebenprodukt dieses Trainers.
- Keine Änderung an `SoundWordSegments`. Der Auditive Finder färbt damit nur drei Waggons; die neue Tabelle abzuziehen hätte dort keinen Gegenwert und würde fremde Tests anfassen.
- Kein Locale-Schalter in `WordGraphemes`. Die Tabelle ist schon pack-abgeleitet und damit implizit sprachgebunden — ein expliziter Locale-Parameter wäre Abstraktion für einen zweiten Pack, den es nicht gibt.
