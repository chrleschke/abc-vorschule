# Satz-Versteher (`sentence_picture`) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Siebter autorierter Trainer-Typ „Satz-Versteher“: Satz anhören, eine von zwei Bildkarten antippen — Hörverstehen für Plural/Partizip II/Präteritum, positioniert zwischen Satz-Architekt und Rechnen.

**Architecture:** Neuer `TaskSpec` (`SentencePictureSpec`/`SentencePictureRound`) im sealed Content-Schema; das Hinzufügen bricht absichtlich jeden Dispatch (`kind`, `rounds`, `scoredAtomIds`, `SuccessSpeech`, `TrainerHost`, `ContentValidator`) — deshalb landen Schema, alle Zweige und die Trainer-UI in **einer** Task, flankiert von zwei reinen Helper-Objekten (`SentencePictureSpeech`, `SentencePictureSides`), die die testbare Logik tragen. Content (18 Tasks × 4 Runden), TTS-Extraktion und Doku folgen als eigene Tasks.

**Tech Stack:** Kotlin, Jetpack Compose, kotlinx.serialization, JUnit4 (JVM-Tests laden das ausgelieferte Content-Pack), Python (tools/tts, pytest).

**Spec:** `docs/superpowers/specs/2026-08-07-satz-versteher-design.md`

## Global Constraints

- Instruktions-String überall exakt: `"Ordne das richtige Bild zu."` — gesprochen nur vor Runde 1 eines Tasks.
- `sentence_picture` rangiert in `ContentValidator.TrainerOrder` zwischen `sentence_order` und `count_add`; Lektionen starten weiter mit `sound_position`, enden mit `count_add`.
- Karten: 1–3 Atom-IDs, Wiederholung erlaubt (Menge); jedes Atom braucht ein nicht-leeres Emoji; die zusammengesetzten Emoji-Strings beider Karten müssen sich unterscheiden.
- Sätze: 4–8 Wörter, kein „Ordne“ im `promptTts`, Aussagesätze (kein „?“ am Ende).
- 3–6 Runden pro Task (autoriert: genau 4).
- Kind-UI-Regeln: kein lesbarer Satztext bei verfügbarem TTS; Miss = gesprochenes Feedback (Satz erneut) + `nudge`-Haptik, nie rot; richtige Karte bestätigt grün.
- Lock-Muster wie überall: `enabled = !interactionLocked`, Opacity 0.5↔1.0 mit `tween(200)`.
- Tests laden den ausgelieferten Pack — Content-Fixtures nie duplizieren, sondern `pack.copy(...)` mutieren.
- Unit-Tests: `./gradlew :app:testDebugUnitTest` · Build: `./gradlew :app:assembleDebug`.

---

### Task 1: Helper `SentencePictureSides` + `SentencePictureCardSizing`

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureSides.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/SentencePictureSidesTest.kt`

**Interfaces:**
- Produces: `SentencePictureSides.correctOnLeft(seed: Int): Boolean` · `SentencePictureCardSizing.emojiSp(atomCount: Int): Float`
- Consumes: nichts.

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SentencePictureSidesTest {

    @Test
    fun sideIsDeterministicPerSeed() {
        (-5..5).forEach { seed ->
            assertEquals(
                SentencePictureSides.correctOnLeft(seed),
                SentencePictureSides.correctOnLeft(seed),
            )
        }
    }

    @Test
    fun sidesAreRoughlyBalancedOverManySeeds() {
        // Sätze liefern beliebige String-Hashes; über viele Seeds darf keine
        // Seite dominieren, sonst lernt das Kind "immer links tippen".
        val left = (0 until 1000).count { SentencePictureSides.correctOnLeft("Satz $it".hashCode()) }
        assertTrue("left=$left of 1000", left in 300..700)
    }

    @Test
    fun emojiShrinksWithMoreAtoms() {
        assertTrue(
            SentencePictureCardSizing.emojiSp(1) > SentencePictureCardSizing.emojiSp(2) &&
                SentencePictureCardSizing.emojiSp(2) > SentencePictureCardSizing.emojiSp(3),
        )
    }
}
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.SentencePictureSidesTest"`
Expected: FAIL (unresolved reference `SentencePictureSides`).

- [ ] **Step 3: Implementierung**

```kotlin
package app.abcvorschule.ui.exercise

/**
 * Welche Seite die richtige Karte des Satz-Verstehers bekommt. Deterministisch
 * aus einem Runden-Seed (Hash des Satzes), damit die Zuordnung über
 * Recompositions stabil bleibt und kein Autoren-Bias entsteht.
 *
 * [TrayOrder.arrange] ist hier bewusst NICHT wiederverwendet: es garantiert
 * "nie exakt die Lösungsreihenfolge" und würde bei zwei Karten die richtige
 * systematisch auf eine Seite legen.
 */
object SentencePictureSides {
    fun correctOnLeft(seed: Int): Boolean {
        var h = seed * 0x2545F491
        h = h xor (h ushr 13)
        return (h and 1) == 0
    }
}

/** Emoji-Größe der Bildkarten, gestaffelt nach Atomzahl (1–3, siehe Validator). */
object SentencePictureCardSizing {
    fun emojiSp(atomCount: Int): Float = when {
        atomCount <= 1 -> 72f
        atomCount == 2 -> 56f
        else -> 44f
    }
}
```

- [ ] **Step 4: Test laufen lassen — muss grün sein**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.SentencePictureSidesTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureSides.kt app/src/test/java/app/abcvorschule/ui/exercise/SentencePictureSidesTest.kt
git commit -m "feat(satz-versteher): deterministic card sides + emoji sizing helpers"
```

---

### Task 2: Schema, Dispatch-Zweige, Validator und Trainer-UI

Das sealed-Schema erzwingt, dass alles Folgende in einer kompilierenden Einheit landet.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/TaskSpecs.kt` (Enum, Spec, Round, `kind`, `rounds`, `scoredAtomIds`)
- Create: `app/src/main/java/app/abcvorschule/content/SentencePictureSpeech.kt`
- Modify: `app/src/main/java/app/abcvorschule/content/ContentValidator.kt` (TrainerOrder + Regeln)
- Modify: `app/src/main/java/app/abcvorschule/session/SuccessSpeech.kt`
- Modify: `app/src/main/java/app/abcvorschule/session/SessionViewModel.kt` (`currentPromptParts`, `missCueForCurrent`)
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt`
- Test: `app/src/test/java/app/abcvorschule/content/SentencePictureSpeechTest.kt`
- Test: `app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt` (erweitern)
- Test: `app/src/test/java/app/abcvorschule/session/SuccessSpeechTest.kt` (erweitern)

**Interfaces:**
- Consumes: `SentencePictureSides.correctOnLeft(seed)`, `SentencePictureCardSizing.emojiSp(count)` (Task 1); bestehende Bausteine `ExerciseStage`, `TaskPromptChrome`, `AbcResolveButton`, `LocalAbcHaptics`, Farben (`CreamElevated`, `WarmMuted`, `LeafGreen`, `Cream`), `AbcDimens.kidTouch`.
- Produces:
  - `TrainerKind.sentence_picture`
  - `data class SentencePictureSpec(override val id: String, val instructionTts: String, val rounds: List<SentencePictureRound>) : TaskSpec` mit `@SerialName("sentence_picture")`
  - `data class SentencePictureRound(override val promptTts: String, val correctAtomIds: List<String>, val wrongAtomIds: List<String>) : TrainerRound`
  - `SentencePictureSpeech.promptParts(spec: SentencePictureSpec?, round: SentencePictureRound, roundIndex: Int): List<String>`
  - `@Composable fun SentencePictureTrainer(round, roundIndex, pack, ttsAvailable, speaking, interactionLocked, onSpeakPrompt, onResult, modifier)`

- [ ] **Step 1: Failing Tests schreiben**

`app/src/test/java/app/abcvorschule/content/SentencePictureSpeechTest.kt`:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Test

class SentencePictureSpeechTest {

    private val round = SentencePictureRound(
        promptTts = "Tom hat Opa gerufen.",
        correctAtomIds = listOf("tom", "opa"),
        wrongAtomIds = listOf("tom", "oma"),
    )
    private val spec = SentencePictureSpec(
        id = "l03-sp1",
        instructionTts = "Ordne das richtige Bild zu.",
        rounds = listOf(round),
    )

    @Test
    fun firstRoundSpeaksInstructionThenSentence() {
        assertEquals(
            listOf("Ordne das richtige Bild zu.", "Tom hat Opa gerufen."),
            SentencePictureSpeech.promptParts(spec, round, roundIndex = 0),
        )
    }

    @Test
    fun laterRoundsSpeakOnlyTheSentence() {
        assertEquals(
            listOf("Tom hat Opa gerufen."),
            SentencePictureSpeech.promptParts(spec, round, roundIndex = 1),
        )
    }

    @Test
    fun missingSpecStillSpeaksTheSentence() {
        assertEquals(
            listOf("Tom hat Opa gerufen."),
            SentencePictureSpeech.promptParts(null, round, roundIndex = 0),
        )
    }

    @Test
    fun scoredAtomsAreTheCorrectCardDeduplicated() {
        val plural = round.copy(correctAtomIds = listOf("ei", "ei"))
        assertEquals(listOf("ei"), plural.scoredAtomIds())
    }
}
```

In `ContentValidatorTest.kt` ergänzen (Muster `packWithLessonKinds` existiert dort schon; die neuen Tests bauen sich einen minimalen Spec und hängen ihn an eine bestehende Lektion an — Vorlage ist `sentenceOrderTrayOverflowIsRejected`, das genauso einen Task in `pack.tasks` ersetzt/ergänzt und die Lektion darauf zeigen lässt):

```kotlin
    // --- sentence_picture -----------------------------------------------------

    private fun sentencePictureSpec(
        rounds: List<SentencePictureRound>,
        instruction: String = "Ordne das richtige Bild zu.",
    ) = SentencePictureSpec(id = "l01-spx", instructionTts = instruction, rounds = rounds)

    private fun validSentencePictureRound() = SentencePictureRound(
        promptTts = "Oma hat Mama gerufen.",
        correctAtomIds = listOf("oma", "mama"),
        wrongAtomIds = listOf("opa", "mama"),
    )

    /** Hängt den Spec vor das Rechnen der ersten autorierten Lektion. */
    private fun packWithSentencePicture(spec: SentencePictureSpec): ContentPack {
        val pack = ContentRepository.fromClasspath().load()
        val lesson = pack.authoredLessons.first()
        val countIndex = lesson.taskIds.indexOfFirst { pack.tasks.getValue(it) is CountAddSpec }
        val taskIds = lesson.taskIds.toMutableList().apply { add(countIndex, spec.id) }
        return pack.copy(
            tasks = pack.tasks + (spec.id to spec),
            lessons = pack.lessons.map { if (it.id == lesson.id) it.copy(taskIds = taskIds) else it },
        )
    }

    @Test
    fun sentencePictureRanksBetweenSentenceOrderAndCountAdd() {
        assertEquals(
            listOf(
                TrainerKind.sound_position,
                TrainerKind.letter_trace,
                TrainerKind.syllable_merge,
                TrainerKind.word_build,
                TrainerKind.sentence_order,
                TrainerKind.sentence_picture,
                TrainerKind.count_add,
            ),
            ContentValidator.TrainerOrder,
        )
    }

    @Test
    fun validSentencePictureTaskPasses() {
        val rounds = List(4) { validSentencePictureRound() }
        val issues = ContentValidator.validate(packWithSentencePicture(sentencePictureSpec(rounds)))
        assertTrue(issues.joinToString { it.message }, issues.isEmpty())
    }

    @Test
    fun sentencePictureBlankInstructionIsRejected() {
        val rounds = List(4) { validSentencePictureRound() }
        val issues = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(rounds, instruction = " ")),
        )
        assertTrue(issues.any { "instructionTts" in it.message })
    }

    @Test
    fun sentencePictureNeedsThreeToSixRounds() {
        val tooFew = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(List(2) { validSentencePictureRound() })),
        )
        assertTrue(tooFew.any { "rounds" in it.message })
        val tooMany = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(List(7) { validSentencePictureRound() })),
        )
        assertTrue(tooMany.any { "rounds" in it.message })
    }

    @Test
    fun sentencePictureSentenceNeedsFourToEightWords() {
        val short = validSentencePictureRound().copy(promptTts = "Oma ruft.")
        val issues = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(List(4) { short })),
        )
        assertTrue(issues.any { "words" in it.message })
    }

    @Test
    fun sentencePictureInstructionInsideSentenceIsRejected() {
        val round = validSentencePictureRound().copy(promptTts = "Ordne das Bild der Oma zu.")
        val issues = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(List(4) { round })),
        )
        assertTrue(issues.any { "Ordne" in it.message })
    }

    @Test
    fun sentencePictureCardsNeedOneToThreeExistingEmojiAtoms() {
        val emptyCard = validSentencePictureRound().copy(wrongAtomIds = emptyList())
        assertTrue(
            ContentValidator.validate(
                packWithSentencePicture(sentencePictureSpec(List(4) { emptyCard })),
            ).any { "card" in it.message },
        )
        val fourAtoms = validSentencePictureRound()
            .copy(correctAtomIds = listOf("oma", "oma", "oma", "oma"))
        assertTrue(
            ContentValidator.validate(
                packWithSentencePicture(sentencePictureSpec(List(4) { fourAtoms })),
            ).any { "card" in it.message },
        )
        val missingAtom = validSentencePictureRound().copy(correctAtomIds = listOf("gibtsnicht"))
        assertTrue(
            ContentValidator.validate(
                packWithSentencePicture(sentencePictureSpec(List(4) { missingAtom })),
            ).any { "missing atom" in it.message },
        )
        // "ist" existiert, trägt aber kein Emoji.
        val noEmoji = validSentencePictureRound().copy(correctAtomIds = listOf("ist"))
        assertTrue(
            ContentValidator.validate(
                packWithSentencePicture(sentencePictureSpec(List(4) { noEmoji })),
            ).any { "emoji" in it.message },
        )
    }

    @Test
    fun sentencePictureIdenticalCardsAreRejected() {
        val same = validSentencePictureRound().copy(
            correctAtomIds = listOf("oma", "mama"),
            wrongAtomIds = listOf("oma", "mama"),
        )
        val issues = ContentValidator.validate(
            packWithSentencePicture(sentencePictureSpec(List(4) { same })),
        )
        assertTrue(issues.any { "indistinguishable" in it.message })
    }
```

Achtung: der bestehende Test `trainerOrderIsTheSixDidacticTrainers` wird durch
`sentencePictureRanksBetweenSentenceOrderAndCountAdd` ersetzt (löschen).

In `SuccessSpeechTest.kt` ergänzen:

```kotlin
    @Test
    fun sentencePictureSuccessRepeatsTheSentence() {
        val round = SentencePictureRound(
            promptTts = "Tom hat Opa gerufen.",
            correctAtomIds = listOf("tom", "opa"),
            wrongAtomIds = listOf("tom", "oma"),
        )
        assertEquals(
            listOf("Tom hat Opa gerufen."),
            SuccessSpeech.partsForRound(round, pack, praise = false),
        )
    }
```

- [ ] **Step 2: Tests laufen lassen — müssen fehlschlagen (Kompilierfehler)**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.content.SentencePictureSpeechTest"`
Expected: FAIL (unresolved reference `SentencePictureRound`).

- [ ] **Step 3: Schema in `TaskSpecs.kt`**

`TrainerKind` erweitern (vor `count_add` einsortieren):

```kotlin
enum class TrainerKind {
    sound_position,
    letter_trace,
    syllable_merge,
    word_build,
    sentence_order,
    sentence_picture,
    count_add,
    symbol_hunt,
    symbol_in_word,
}
```

Nach dem Satz-Architekt-Block (`// --- Trainer 5 ...`) einfügen:

```kotlin
// --- Trainer 6: Satz-Versteher ------------------------------------------------

/**
 * Hörverstehen auf Satzebene: ein Satz mit bewusst schwieriger Grammatik
 * (Plural, Partizip II, Präteritum) wird vorgelesen, das Kind tippt eine von
 * zwei Bildkarten. Die Sätze leben im Round selbst statt in sentences.json —
 * wie die Finale-Sätze enthalten sie flektierte Formen außerhalb des
 * Atom-Graphen, die nie gebaut oder gelesen werden.
 */
@Serializable
@SerialName("sentence_picture")
data class SentencePictureSpec(
    override val id: String,
    /** Einmalige Aufgabenansage, gesprochen nur vor Runde 1. */
    val instructionTts: String,
    val rounds: List<SentencePictureRound>,
) : TaskSpec

@Serializable
data class SentencePictureRound(
    /** Der Satz selbst — Ansage, Erfolgs-Echo und Miss-Wiederholung zugleich. */
    override val promptTts: String,
    /** Passende Karte: 1..3 Atom-IDs als Emoji-Reihe; Wiederholung = Menge (🍎🍎). */
    val correctAtomIds: List<String>,
    /** Unpassende Karte — Kontrast in genau der geprüften Dimension. */
    val wrongAtomIds: List<String>,
) : TrainerRound
```

Die drei Dispatches in derselben Datei erweitern:

```kotlin
// in TaskSpec.kind:
        is SentencePictureSpec -> TrainerKind.sentence_picture
// in TaskSpec.rounds:
        is SentencePictureSpec -> rounds
// in TrainerRound.scoredAtomIds():
    is SentencePictureRound -> correctAtomIds.distinct()
```

- [ ] **Step 4: `SentencePictureSpeech.kt` anlegen**

```kotlin
package app.abcvorschule.content

/**
 * Ansage-Teile des Satz-Verstehers. Die Instruktion kommt nur vor Runde 1 —
 * danach trägt der Satz allein die Aufgabe. Getrennte Teile halten die
 * Audio-Clips wiederverwendbar (eine Instruktions-Aufnahme für alle Lektionen).
 */
object SentencePictureSpeech {
    fun promptParts(
        spec: SentencePictureSpec?,
        round: SentencePictureRound,
        roundIndex: Int,
    ): List<String> = listOfNotNull(
        spec?.instructionTts?.takeIf { it.isNotBlank() && roundIndex == 0 },
        round.promptTts.takeIf { it.isNotBlank() },
    )
}
```

- [ ] **Step 5: `ContentValidator.kt` erweitern**

`TrainerOrder` um den neuen Kind ergänzen:

```kotlin
    val TrainerOrder: List<TrainerKind> = listOf(
        TrainerKind.sound_position,
        TrainerKind.letter_trace,
        TrainerKind.syllable_merge,
        TrainerKind.word_build,
        TrainerKind.sentence_order,
        TrainerKind.sentence_picture,
        TrainerKind.count_add,
    )
```

Konstanten bei den anderen Redaktionsregeln:

```kotlin
    /** Redaktionsregeln Satz-Versteher, siehe Design-Spec 2026-08-07. */
    private const val MinSentencePictureRounds = 3
    private const val MaxSentencePictureRounds = 6
    private const val MinSentencePictureWords = 4
    private const val MaxSentencePictureWords = 8
    private const val MaxSentencePictureCardAtoms = 3
```

Neuer `when`-Zweig im Task-Loop (zwischen `SentenceOrderSpec` und `CountAddSpec`):

```kotlin
                is SentencePictureSpec -> {
                    if (spec.instructionTts.isBlank()) {
                        issues += ValidationIssue("task $id needs an instructionTts")
                    }
                    if (spec.rounds.size !in MinSentencePictureRounds..MaxSentencePictureRounds) {
                        issues += ValidationIssue(
                            "task $id holds ${spec.rounds.size} rounds; expected " +
                                "$MinSentencePictureRounds..$MaxSentencePictureRounds",
                        )
                    }
                    spec.rounds.forEach { round ->
                        val words = round.promptTts.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
                        if (words !in MinSentencePictureWords..MaxSentencePictureWords) {
                            issues += ValidationIssue(
                                "task $id sentence holds $words words; expected " +
                                    "$MinSentencePictureWords..$MaxSentencePictureWords",
                            )
                        }
                        if ("Ordne" in round.promptTts) {
                            issues += ValidationIssue(
                                "task $id sentence must not repeat the 'Ordne' instruction",
                            )
                        }
                        listOf("correct" to round.correctAtomIds, "wrong" to round.wrongAtomIds)
                            .forEach { (label, ids) ->
                                if (ids.size !in 1..MaxSentencePictureCardAtoms) {
                                    issues += ValidationIssue(
                                        "task $id $label card holds ${ids.size} atoms; " +
                                            "expected 1..$MaxSentencePictureCardAtoms",
                                    )
                                }
                                ids.forEach { atomId ->
                                    requireAtom("task $id", atomId)
                                    val atom = pack.atoms[atomId]
                                    if (atom != null && atom.emoji.isBlank()) {
                                        issues += ValidationIssue(
                                            "task $id $label card atom $atomId carries no emoji",
                                        )
                                    }
                                }
                            }
                        // Beide Karten müssen unterscheidbar sein, sonst kann die
                        // Aufgabe nicht fehlschlagen (Prüffrage der Prinzipien).
                        fun glyphs(ids: List<String>) =
                            ids.joinToString("") { pack.atoms[it]?.emoji.orEmpty() }
                        if (glyphs(round.correctAtomIds) == glyphs(round.wrongAtomIds)) {
                            issues += ValidationIssue("task $id cards are indistinguishable")
                        }
                    }
                }
```

- [ ] **Step 6: Sprach- und Session-Zweige**

`SuccessSpeech.partsForRound` (Import ergänzen):

```kotlin
        is SentencePictureRound -> listOfNotNull(round.promptTts.takeIf { it.isNotBlank() })
```

`SessionViewModel.currentPromptParts()` — neuer Zweig vor `else`:

```kotlin
            is SentencePictureRound -> SentencePictureSpeech.promptParts(
                _ui.value.current?.spec as? app.abcvorschule.content.SentencePictureSpec,
                round,
                _ui.value.roundIndex,
            )
```

`SessionViewModel.missCueForCurrent()` — der Satz wird als Korrektur erneut vorgelesen:

```kotlin
    private fun missCueForCurrent(): String = when (val round = _ui.value.currentRound) {
        is SoundPositionRound -> round.missTts
        is SentencePictureRound -> round.promptTts
        else -> "Probiere eine andere Antwort"
    }
```

(Imports `SentencePictureRound`/`SentencePictureSpeech` oben in der Datei ergänzen, dann qualifizierten Namen im Cast kürzen.)

- [ ] **Step 7: Trainer-UI `SentencePictureTrainer.kt`**

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.SentencePictureRound
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.CreamElevated
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted

/**
 * Trainer 6 — Satz-Versteher. Ein Satz mit schwieriger Grammatik wird
 * vorgelesen; das Kind tippt eine von zwei Bildkarten. Tippen ist die Antwort
 * (wie beim Auditiven Finder) — die Karten tragen keine Wörter, also gibt es
 * kein Vorlese-Echo. Ein Miss liest den Satz erneut (missCueForCurrent).
 */
@Composable
fun SentencePictureTrainer(
    round: SentencePictureRound,
    roundIndex: Int,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakPrompt: () -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.promptTts}"
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    var solvedCorrect by remember(roundKey) { mutableStateOf(false) }
    val haptics = LocalAbcHaptics.current
    val scoredIds = remember(roundKey) { round.correctAtomIds.distinct() }
    val correctOnLeft = remember(roundKey) {
        SentencePictureSides.correctOnLeft(round.promptTts.hashCode())
    }
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "sentence_picture_lock_opacity",
    )

    fun choose(correct: Boolean) {
        if (resolved || solvedCorrect) return
        if (correct) {
            solvedCorrect = true
            haptics.success()
            onResult(true, false, scoredIds)
        } else {
            misses += 1
            haptics.nudge()
            onResult(false, false, scoredIds)
        }
    }

    ExerciseStage(
        modifier = modifier,
        prompt = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = onSpeakPrompt,
            )
            if (!ttsAvailable) {
                // Ohne deutsches TTS liest ein Erwachsener vor — die eine
                // Situation, in der der Satz als Text erscheinen muss.
                Text(
                    text = round.promptTts,
                    style = MaterialTheme.typography.headlineSmall,
                    color = WarmInk,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.testTag("sentence_picture_fallback_text"),
                )
            }
        },
        answers = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sentence_picture_cards"),
            ) {
                val leftIsCorrect = correctOnLeft
                PictureCard(
                    atomIds = if (leftIsCorrect) round.correctAtomIds else round.wrongAtomIds,
                    pack = pack,
                    highlight = (solvedCorrect || resolved) && leftIsCorrect,
                    enabled = !interactionLocked && !solvedCorrect && !resolved,
                    opacity = interactionOpacity,
                    onTap = { choose(leftIsCorrect) },
                    testTag = if (leftIsCorrect) "card_correct" else "card_wrong",
                    modifier = Modifier.weight(1f),
                )
                PictureCard(
                    atomIds = if (leftIsCorrect) round.wrongAtomIds else round.correctAtomIds,
                    pack = pack,
                    highlight = (solvedCorrect || resolved) && !leftIsCorrect,
                    enabled = !interactionLocked && !solvedCorrect && !resolved,
                    opacity = interactionOpacity,
                    onTap = { choose(!leftIsCorrect) },
                    testTag = if (leftIsCorrect) "card_wrong" else "card_correct",
                    modifier = Modifier.weight(1f),
                )
            }
            if (misses >= 2 && !resolved && !solvedCorrect) {
                AbcResolveButton(
                    onClick = {
                        resolved = true
                        onResult(false, true, scoredIds)
                    },
                )
            }
        },
    )
}

/**
 * Bestätigungs-Grün der gewählten Karte — dieselbe dunklere LeafGreen-Variante
 * wie SentenceOrderTrainer.PegBorderGreen (voll-opakes LeafGreen erreicht auf
 * CreamElevated nur 2.87:1).
 */
private val CardBorderGreen = androidx.compose.ui.graphics.Color(0xFF3A7A44)

@Composable
private fun PictureCard(
    atomIds: List<String>,
    pack: ContentPack,
    highlight: Boolean,
    enabled: Boolean,
    opacity: Float,
    onTap: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val emojis = atomIds.joinToString("") { pack.atoms[it]?.emoji.orEmpty() }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .defaultMinSize(minHeight = AbcDimens.kidTouch * 2)
            .alpha(opacity)
            .background(color = CreamElevated, shape = RoundedCornerShape(22.dp))
            .border(
                width = if (highlight) 4.dp else 3.dp,
                color = if (highlight) CardBorderGreen else WarmMuted.copy(alpha = 0.9f),
                shape = RoundedCornerShape(22.dp),
            )
            .clickable(enabled = enabled, onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 18.dp)
            .testTag(testTag),
    ) {
        Text(
            text = emojis,
            fontSize = SentencePictureCardSizing.emojiSp(atomIds.size).sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}
```

- [ ] **Step 8: `TrainerHost.kt` Dispatch**

Import `app.abcvorschule.content.SentencePictureRound` ergänzen; neuer Zweig zwischen `SentenceOrderRound` und `CountAddRound`:

```kotlin
        is SentencePictureRound -> SentencePictureTrainer(
            round = round,
            roundIndex = roundIndex,
            pack = pack,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            interactionLocked = interactionLocked,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
```

- [ ] **Step 9: Alle Unit-Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — insbesondere die neuen `SentencePictureSpeechTest`-, Validator- und SuccessSpeech-Fälle; `shippedPackIsValid` bleibt grün (der Pack enthält noch keinen `sentence_picture`-Task).

- [ ] **Step 10: Commit**

```bash
git add -A app/src
git commit -m "feat(satz-versteher): sentence_picture schema, validator rules, speech wiring, trainer UI"
```

---

### Task 3: Content — 18 Tasks, Lessons-Verdrahtung, Coverage-Test

**Files:**
- Modify: `app/src/main/assets/content/tasks.json` (18 neue Tasks, JSON unten vollständig)
- Modify: `app/src/main/assets/content/lessons.json` (je Basis-Lektion l01–l18 die neue Task-ID direkt **vor** der ersten `count_add`-ID einfügen)
- Modify: `app/src/main/assets/content/pack.manifest.json` (`packId`: `fibel-v3` → `fibel-v4`, Lektionsstruktur ändert sich)
- Test: `app/src/test/java/app/abcvorschule/content/LessonCoverageTest.kt` (erweitern)

**Interfaces:**
- Consumes: Schema aus Task 2 (`sentence_picture`-Discriminator, Feldnamen `instructionTts`, `promptTts`, `correctAtomIds`, `wrongAtomIds`).
- Produces: Task-IDs `l01-sp1` … `l18-sp1` (referenziert aus `lessons.json`).

- [ ] **Step 1: Failing Coverage-Test schreiben**

In `LessonCoverageTest.kt` ergänzen (Imports: `SentencePictureSpec`):

```kotlin
    @Test
    fun satzVersteherRunsOnceInEveryBaseLessonWithFourRounds() {
        pack.authoredLessons.filter { it.index <= 18 }.forEach { lesson ->
            val specs = pack.tasksOf(lesson).filterIsInstance<SentencePictureSpec>()
            assertEquals("lesson ${lesson.id}", 1, specs.size)
            assertEquals("lesson ${lesson.id}", 4, specs.single().rounds.size)
            assertEquals(
                "lesson ${lesson.id}",
                "Ordne das richtige Bild zu.",
                specs.single().instructionTts,
            )
        }
    }
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.content.LessonCoverageTest"`
Expected: FAIL (`lesson l01 expected:<1> but was:<0>`).

- [ ] **Step 3: Content einpflegen**

Alle 18 Tasks ans Ende des `tasks`-Arrays in `tasks.json` anhängen (Feld `instructionTts` überall `"Ordne das richtige Bild zu."`). Vollständiger Inhalt:

```json
{ "trainer": "sentence_picture", "id": "l01-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Zwei Ameisen sind zu Mama gekrabbelt.", "correctAtomIds": ["ameise", "ameise", "mama"], "wrongAtomIds": ["ameise", "mama"] },
  { "promptTts": "Mama hat die Ameise gefunden.", "correctAtomIds": ["mama", "ameise"], "wrongAtomIds": ["oma", "ameise"] },
  { "promptTts": "Mama malte am Morgen eine Sonne.", "correctAtomIds": ["mama", "sonne"], "wrongAtomIds": ["mama", "herz"] },
  { "promptTts": "Oma hat Mama gerufen.", "correctAtomIds": ["oma", "mama"], "wrongAtomIds": ["opa", "mama"] }
] },
{ "trainer": "sentence_picture", "id": "l02-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Opa hat Oma einen Kuss gegeben.", "correctAtomIds": ["opa", "oma"], "wrongAtomIds": ["opa", "mama"] },
  { "promptTts": "Mimi ist auf das Sofa gesprungen.", "correctAtomIds": ["mimi", "sofa"], "wrongAtomIds": ["hund", "sofa"] },
  { "promptTts": "Zwei Omas saßen auf der Bank.", "correctAtomIds": ["oma", "oma"], "wrongAtomIds": ["oma"] },
  { "promptTts": "Oma hat Mimi gesucht und gefunden.", "correctAtomIds": ["oma", "mimi"], "wrongAtomIds": ["opa", "mimi"] }
] },
{ "trainer": "sentence_picture", "id": "l03-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Tom hat Opa gerufen.", "correctAtomIds": ["tom", "opa"], "wrongAtomIds": ["tom", "oma"] },
  { "promptTts": "Papa hat zwei Tomaten geschnitten.", "correctAtomIds": ["papa", "tomate", "tomate"], "wrongAtomIds": ["papa", "tomate"] },
  { "promptTts": "Opa trug einen Topf voll Suppe.", "correctAtomIds": ["opa", "suppe"], "wrongAtomIds": ["opa", "tee"] },
  { "promptTts": "Tom warf den Ball zu Papa.", "correctAtomIds": ["tom", "ball", "papa"], "wrongAtomIds": ["tom", "ball", "opa"] }
] },
{ "trainer": "sentence_picture", "id": "l04-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Das Lama hat laut Hallo gerufen.", "correctAtomIds": ["lama", "hallo"], "wrongAtomIds": ["pferd", "hallo"] },
  { "promptTts": "Zwei Lamas liefen über die Wiese.", "correctAtomIds": ["lama", "lama"], "wrongAtomIds": ["lama"] },
  { "promptTts": "Das Lama hat einen Hut gefressen.", "correctAtomIds": ["lama", "hut"], "wrongAtomIds": ["lama", "keks"] },
  { "promptTts": "Oma hat dem Lama gewinkt.", "correctAtomIds": ["oma", "hallo", "lama"], "wrongAtomIds": ["oma", "hallo", "pferd"] }
] },
{ "trainer": "sentence_picture", "id": "l05-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Ein Ufo ist im Tal gelandet.", "correctAtomIds": ["ufo", "tal"], "wrongAtomIds": ["ufo", "haus"] },
  { "promptTts": "Papa hat zwei Hüte gekauft.", "correctAtomIds": ["papa", "hut", "hut"], "wrongAtomIds": ["papa", "hut"] },
  { "promptTts": "Der Wind trug Omas Hut davon.", "correctAtomIds": ["oma", "hut"], "wrongAtomIds": ["oma", "schuh"] },
  { "promptTts": "Drei Ufos flogen am Himmel.", "correctAtomIds": ["ufo", "ufo", "ufo"], "wrongAtomIds": ["ufo", "ufo"] }
] },
{ "trainer": "sentence_picture", "id": "l06-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Tom hat den Ball ins Tor geworfen.", "correctAtomIds": ["tom", "ball", "tor"], "wrongAtomIds": ["tom", "ball", "nest"] },
  { "promptTts": "Oma hat zwei Rosen bekommen.", "correctAtomIds": ["oma", "rose", "rose"], "wrongAtomIds": ["oma", "rose"] },
  { "promptTts": "Der Clown hatte eine rote Nase.", "correctAtomIds": ["clown", "nase", "rot"], "wrongAtomIds": ["clown", "nase", "blau"] },
  { "promptTts": "Ein Vogel saß auf dem Tor.", "correctAtomIds": ["vogel", "tor"], "wrongAtomIds": ["vogel", "baum"] }
] },
{ "trainer": "sentence_picture", "id": "l07-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Im Nest lag ein kleiner Vogel.", "correctAtomIds": ["nest", "vogel"], "wrongAtomIds": ["nest", "maus"] },
  { "promptTts": "Die Sonne schien auf die Rosen.", "correctAtomIds": ["sonne", "rose", "rose"], "wrongAtomIds": ["wolke", "rose", "rose"] },
  { "promptTts": "Tom hat im Sand ein Nest gefunden.", "correctAtomIds": ["tom", "sand", "nest"], "wrongAtomIds": ["tom", "sand", "ball"] },
  { "promptTts": "Oma hat die Rosen gegossen.", "correctAtomIds": ["oma", "rose", "rose"], "wrongAtomIds": ["opa", "rose", "rose"] }
] },
{ "trainer": "sentence_picture", "id": "l08-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "In der Dose waren zwei Kekse.", "correctAtomIds": ["dose", "keks", "keks"], "wrongAtomIds": ["dose", "keks"] },
  { "promptTts": "Der Hund hat einen Keks geklaut.", "correctAtomIds": ["hund", "keks"], "wrongAtomIds": ["katze", "keks"] },
  { "promptTts": "Tom aß den letzten Keks.", "correctAtomIds": ["tom", "keks"], "wrongAtomIds": ["tom", "eis"] },
  { "promptTts": "Die Dosen standen im Regal.", "correctAtomIds": ["dose", "dose"], "wrongAtomIds": ["dose"] }
] },
{ "trainer": "sentence_picture", "id": "l09-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Die Wolken zogen über das Haus.", "correctAtomIds": ["wolke", "wolke", "haus"], "wrongAtomIds": ["wolke", "haus"] },
  { "promptTts": "Mama hat ein neues Kleid genäht.", "correctAtomIds": ["mama", "kleid"], "wrongAtomIds": ["mama", "jacke"] },
  { "promptTts": "Das Eis ist in der Sonne geschmolzen.", "correctAtomIds": ["eis", "sonne"], "wrongAtomIds": ["eis", "wolke"] },
  { "promptTts": "Tom hat zwei Eier gefunden.", "correctAtomIds": ["tom", "eier"], "wrongAtomIds": ["tom", "ei"] }
] },
{ "trainer": "sentence_picture", "id": "l10-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Die Giraffe hat aus dem Sack gefressen.", "correctAtomIds": ["giraffe", "sack"], "wrongAtomIds": ["pferd", "sack"] },
  { "promptTts": "Auf dem Dach saßen zwei Tauben.", "correctAtomIds": ["dach", "taube", "taube"], "wrongAtomIds": ["dach", "taube"] },
  { "promptTts": "Opa hat mir ein Buch geschenkt.", "correctAtomIds": ["opa", "buch"], "wrongAtomIds": ["opa", "paket"] },
  { "promptTts": "Der Drache flog über den Weg.", "correctAtomIds": ["drache", "weg"], "wrongAtomIds": ["drache", "park"] }
] },
{ "trainer": "sentence_picture", "id": "l11-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Vor dem Haus standen zwei Bäume.", "correctAtomIds": ["haus", "baeume"], "wrongAtomIds": ["haus", "baum"] },
  { "promptTts": "Der Bär hat im Haus geschlafen.", "correctAtomIds": ["baer", "haus"], "wrongAtomIds": ["baer", "baum"] },
  { "promptTts": "Auf dem Baum saß eine Eule.", "correctAtomIds": ["baum", "eule"], "wrongAtomIds": ["baum", "vogel"] },
  { "promptTts": "Aus dem Haus kamen zwei Mäuse.", "correctAtomIds": ["haus", "maus", "maus"], "wrongAtomIds": ["haus", "maus"] }
] },
{ "trainer": "sentence_picture", "id": "l12-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Hier stehen viele Häuser.", "correctAtomIds": ["haeusser"], "wrongAtomIds": ["haus"] },
  { "promptTts": "Der Frosch hat zwei Rüben geerntet.", "correctAtomIds": ["frosch", "ruebe", "ruebe"], "wrongAtomIds": ["frosch", "ruebe"] },
  { "promptTts": "Die Äpfel fielen vom Baum.", "correctAtomIds": ["aepfel", "baum"], "wrongAtomIds": ["apfel", "baum"] },
  { "promptTts": "Die Mäuse aßen den Käse auf.", "correctAtomIds": ["maus", "maus", "kaese"], "wrongAtomIds": ["maus", "kaese"] }
] },
{ "trainer": "sentence_picture", "id": "l13-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Das Schaf hat neue Schuhe bekommen.", "correctAtomIds": ["schaf", "schuh", "schuh"], "wrongAtomIds": ["schaf", "hut"] },
  { "promptTts": "Zwei Fische schwammen im Fluss.", "correctAtomIds": ["fisch", "fisch"], "wrongAtomIds": ["fisch"] },
  { "promptTts": "Tom ist in die Schule gegangen.", "correctAtomIds": ["tom", "schule"], "wrongAtomIds": ["tom", "haus"] },
  { "promptTts": "Oma hat einen Schuh verloren.", "correctAtomIds": ["oma", "schuh"], "wrongAtomIds": ["oma", "schuh", "schuh"] }
] },
{ "trainer": "sentence_picture", "id": "l14-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Das Zebra hat ein Jojo gefunden.", "correctAtomIds": ["zebra", "jojo"], "wrongAtomIds": ["zebra", "ball"] },
  { "promptTts": "Zwei Eulen saßen auf dem Baum.", "correctAtomIds": ["eule", "eule", "baum"], "wrongAtomIds": ["eule", "baum"] },
  { "promptTts": "Der Zug ist durch das Tal gefahren.", "correctAtomIds": ["zug", "tal"], "wrongAtomIds": ["auto", "tal"] },
  { "promptTts": "Die Eule fing eine Maus.", "correctAtomIds": ["eule", "maus"], "wrongAtomIds": ["katze", "maus"] }
] },
{ "trainer": "sentence_picture", "id": "l15-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Der Vogel ist gegen die Vase geflogen.", "correctAtomIds": ["vogel", "vase"], "wrongAtomIds": ["vogel", "lampe"] },
  { "promptTts": "Papa ist in die Vase getreten.", "correctAtomIds": ["papa", "fuss", "vase"], "wrongAtomIds": ["papa", "fuss", "ball"] },
  { "promptTts": "In der Vase standen zwei Rosen.", "correctAtomIds": ["vase", "rose", "rose"], "wrongAtomIds": ["vase", "rose"] },
  { "promptTts": "Zwei Vögel badeten im Sand.", "correctAtomIds": ["vogel", "vogel", "sand"], "wrongAtomIds": ["vogel", "sand"] }
] },
{ "trainer": "sentence_picture", "id": "l16-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Das Pferd hat zwei Äpfel gefressen.", "correctAtomIds": ["pferd", "aepfel"], "wrongAtomIds": ["pferd", "apfel"] },
  { "promptTts": "Opa trug einen schweren Sack.", "correctAtomIds": ["opa", "sack"], "wrongAtomIds": ["opa", "paket"] },
  { "promptTts": "Das Pferd ist über das Tor gesprungen.", "correctAtomIds": ["pferd", "tor"], "wrongAtomIds": ["pferd", "haus"] },
  { "promptTts": "Die Äpfel lagen im Sack.", "correctAtomIds": ["aepfel", "sack"], "wrongAtomIds": ["aepfel", "eimer"] }
] },
{ "trainer": "sentence_picture", "id": "l17-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Die Spinne saß auf dem Spiegel.", "correctAtomIds": ["spinne", "spiegel"], "wrongAtomIds": ["spinne", "fenster"] },
  { "promptTts": "Am Himmel standen viele Sterne.", "correctAtomIds": ["stern", "stern", "stern"], "wrongAtomIds": ["stern"] },
  { "promptTts": "Tom ist über den Stuhl gestolpert.", "correctAtomIds": ["tom", "stuhl"], "wrongAtomIds": ["tom", "sofa"] },
  { "promptTts": "Zwei Spinnen krabbelten über die Straße.", "correctAtomIds": ["spinne", "spinne", "strasse"], "wrongAtomIds": ["spinne", "strasse"] }
] },
{ "trainer": "sentence_picture", "id": "l18-sp1", "instructionTts": "Ordne das richtige Bild zu.", "rounds": [
  { "promptTts": "Das Pony ist mit dem Taxi gefahren.", "correctAtomIds": ["pony", "taxi"], "wrongAtomIds": ["pony", "bus"] },
  { "promptTts": "Zwei Quallen schwammen im Wasser.", "correctAtomIds": ["qualle", "qualle"], "wrongAtomIds": ["qualle"] },
  { "promptTts": "Der Clown hat Xylofon gespielt.", "correctAtomIds": ["clown", "xylofon"], "wrongAtomIds": ["clown", "radio"] },
  { "promptTts": "Das Taxi hielt an der Ampel.", "correctAtomIds": ["taxi", "ampel"], "wrongAtomIds": ["bus", "ampel"] }
] }
```

In `lessons.json` je Basis-Lektion die neue ID direkt vor die erste `count_add`-Task-ID setzen (welche ID das ist, aus `tasks.json` ablesen — meist die letzte, l18 führt zwei `count_add`-Tasks am Ende):

- l01: `... , "l01-t9", "l01-sp1", "l01-t10"]` (l01-t10 ist count_add)
- analog l02–l17: `"lXX-sp1"` vor `"lXX-t10"`
- l18: `"l18-sp1"` vor `"l18-t11"`

In `pack.manifest.json`: `"packId": "fibel-v4"`. Danach `grep -rn "fibel-v3" app/src tools` — verbliebene Verweise (z. B. in Tests) mitziehen.

- [ ] **Step 4: Alle Unit-Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — `shippedPackIsValid`, `LessonCoverageTest` (neu + `sentenceRoundsOnlyUseWordsThatWereBuiltOrIntroduced` unverändert grün, der Test liest nur `SentenceOrderSpec`), `SessionTrainersTest` (Scaffold-Invariante über die echte Zusammenstellung).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/content app/src/test
git commit -m "feat(satz-versteher): author 18 sentence_picture tasks, wire into base lessons, bump pack to fibel-v4"
```

---

### Task 4: TTS-Pipeline — `instructionTts` extrahieren

**Files:**
- Modify: `tools/tts/ttskit/extract.py`
- Modify: `tools/tts/audit_missing_audio.py` (nur falls es Task-Felder whitelistet — prüfen; es liest Runden generisch über `promptTts`)
- Test: `tools/tts/tests/` (bestehende Extract-Tests erweitern; Datei per `grep -rln "extract_items" tools/tts/tests` finden)

**Interfaces:**
- Consumes: `tasks.json` mit Task-Feld `instructionTts` (Task 3).
- Produces: Items mit `field="instructionTts"`, Profil `prompt`, ID-Format `task:{task_id}:instructionTts`.

- [ ] **Step 1: Failing Test schreiben**

In der bestehenden Extract-Testdatei (Muster der Nachbartests übernehmen — sie bauen ein Mini-Content-Verzeichnis als Fixture) ergänzen:

```python
def test_instruction_tts_is_extracted_with_prompt_profile(tmp_path_pack):
    # tmp_path_pack: bestehende Fixture, die ein content-Verzeichnis baut.
    # Dort in tasks.json einen Task ergänzen:
    # {"trainer": "sentence_picture", "id": "l01-sp1",
    #  "instructionTts": "Ordne das richtige Bild zu.",
    #  "rounds": [{"promptTts": "Oma hat Mama gerufen.",
    #              "correctAtomIds": ["oma"], "wrongAtomIds": ["mama"]}]}
    items = extract_items(tmp_path_pack)
    by_id = {item.id: item for item in items}
    instruction = by_id["task:l01-sp1:instructionTts"]
    assert instruction.text == "Ordne das richtige Bild zu."
    assert profile_for_item(instruction) == "prompt"
    assert "task:l01-sp1:round:0:promptTts" in by_id
```

- [ ] **Step 2: Test laufen lassen — muss fehlschlagen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests -k instruction -v` (falls das venv fehlt: `python3 -m pytest tests -k instruction -v` — die Extract-Tests brauchen kein TTS-Modell)
Expected: FAIL (KeyError `task:l01-sp1:instructionTts`).

- [ ] **Step 3: Implementierung**

In `extract.py` — `FIELD_TO_PROFILE` ergänzen:

```python
    "instructionTts": "prompt",
```

Im Task-Loop direkt nach dem `phonemeTts`-Block:

```python
        if "instructionTts" in task:
            add(f"task:{task_id}:instructionTts", task["instructionTts"], "instructionTts",
                "tasks.json", lesson, f"{task_id} · instructionTts")
```

`audit_missing_audio.py` prüfen: wenn es Task-Level-Felder kennt (wie `phonemeTts`), `instructionTts` dort analog ergänzen; behandelt es nur Runden-Felder, denselben Task-Level-Block nachziehen.

- [ ] **Step 4: Tests laufen lassen**

Run: `cd tools/tts && python3 -m pytest tests -v`
Expected: PASS (alle, nicht nur der neue).

- [ ] **Step 5: Commit**

```bash
git add tools/tts
git commit -m "feat(tts): extract sentence_picture instructionTts with prompt profile"
```

---

### Task 5: Doku — Produktprinzipien und Agent-Guide

**Files:**
- Modify: `docs/PRODUCT_PRINCIPLES.md` (§3-Liste, §7, Review-Tabelle)
- Modify: `AGENTS.md` (Kurzfassung „Sechs autorierte Trainer-Typen“)

**Interfaces:** keine (reine Doku).

- [ ] **Step 1: `docs/PRODUCT_PRINCIPLES.md` anpassen**

- §3 Einleitungssatz: „die sechs Trainer-**Typen**“ → „die sieben Trainer-**Typen**“.
- In der nummerierten Liste zwischen „5. **Satz-Architekt**“ und „6. **Rechnen**“ einfügen und Rechnen zu „7.“ umnummerieren:

```markdown
6. **Satz-Versteher** — „Ordne das richtige Bild zu": ein Satz mit bewusst
  schwieriger Grammatik (Plural, Partizip II, Präteritum — auch kombiniert) wird
  vorgelesen, das Kind tippt eine von zwei Bildkarten (Emoji-Reihen, 1–3 Bilder;
  Wiederholung desselben Bildes drückt Menge aus). Die Instruktion kommt **einmal**
  vor Runde 1, danach trägt jeder Satz die Aufgabe allein. Tippen ist die Antwort;
  ein Miss liest den Satz erneut vor, nach 2 Misses gibt es „Zeig mir". Die Sätze
  leben im Task selbst (nicht in `sentences.json`) und dürfen wie die Finale-Sätze
  flektierte Formen und freie Verben nutzen — nur die Karten-Nomen sind Atome mit
  Emoji. Redaktionsregeln: 4–8 Wörter, mindestens eine schwierige Form, Wörter der
  Lektion, Cartoon-Logik (realistischer als die Finale-Sätze), die falsche Karte
  unterscheidet sich genau in der geprüften Dimension (Menge, Akteur oder Objekt).
```

- §3 Reihenfolge-Regeln („Satzrunden nutzen nur gebaute Wörter …“): Hinweis ergänzen, dass der Satz-Versteher davon ausgenommen ist (Hör-Trainer, Finale-Freiheit).
- Review-Tabelle am Ende, Zeile anpassen/ergänzen:
  - „Hält jede autorierte Lektion die **sechs** Trainer-Typen …“ → „… **sieben** Trainer-Typen …“
  - Neue Zeile: `| Zeigt der Satz-Versteher zwei ununterscheidbare Karten oder liest sich seine Instruktion in jedem Satz wieder? | Nein → Validator prüft beides |`

- [ ] **Step 2: `AGENTS.md` anpassen**

Kurzfassung: „**Sechs autorierte Trainer-Typen** pro Lektion in fester Rangfolge (Auditiver Finder → Rechnen)“ → „**Sieben autorierte Trainer-Typen** pro Lektion in fester Rangfolge (Auditiver Finder → … → Satz-Versteher → Rechnen)“.

- [ ] **Step 3: Commit**

```bash
git add docs/PRODUCT_PRINCIPLES.md AGENTS.md
git commit -m "docs(satz-versteher): document seventh trainer type in principles and agent guide"
```

---

### Task 6: End-Verifikation

**Files:** keine neuen.

- [ ] **Step 1: Voller Testlauf**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 2: Build**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Smoke-Check Content**

Run: `python3 - <<'EOF'`-Einzeiler, der `tasks.json` lädt und für jeden `sentence_picture`-Task prüft: 4 Runden, Wortzahlen 4–8, alle Atom-IDs existieren in `atoms.json` und tragen Emojis, Karten-Emoji-Strings verschieden. (Doppelt zum Validator — bestätigt, dass die Unit-Tests wirklich den ausgelieferten Pack gesehen haben.)

- [ ] **Step 4: Commit (falls Restdiffs)**

```bash
git status --short
```
Expected: leer; sonst aufräumen und committen.

## Self-Review-Ergebnis

- Spec-Abdeckung: Schema (§3→Task 2), Ablauf/Speech (§4→Task 2), UI (§5→Task 2), Sides-Helper (§5→Task 1), Scoring (§6→Task 2, packId-Bump→Task 3), Content (§7→Task 3), Validator (§8→Task 2), TTS (§9→Task 4), Tests (§10→Tasks 1–3), Doku (§11→Task 5). Vollständig.
- Typkonsistenz: `SentencePictureSpec(id, instructionTts, rounds)`, `SentencePictureRound(promptTts, correctAtomIds, wrongAtomIds)`, `SentencePictureSpeech.promptParts(spec, round, roundIndex)`, `SentencePictureSides.correctOnLeft(seed)`, `SentencePictureCardSizing.emojiSp(count)` — in allen Tasks gleich verwendet.
