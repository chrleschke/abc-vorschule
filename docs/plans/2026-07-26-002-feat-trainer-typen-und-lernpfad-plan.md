# Neue Trainer-Typen + Fibel-Lernpfad — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ersetze die bestehenden Lese-/Sprech-Trainer durch die fünf didaktischen Trainer des Vorschul-Konzepts, behalte „Rechnen" als sechsten Trainer in *jeder* Lektion, und mache einen lektionsbasierten Fibel-Lernpfad zum Startscreen der App.

**Architecture:** Der Content-Graph wird auf ein Schema v2 umgestellt: ein polymorpher `TaskSpec` pro Trainer-Typ (statt eines Feld-Sammelbeckens `TaskTemplate`), jeder Spec enthält 1..n *Runden*. Lektionen (`lessons.json`) bündeln genau sechs Tasks in fester Trainer-Reihenfolge. Der Pfad-Screen ist der neue App-Einstieg; Tippen auf einen freigeschalteten Lektionsknoten startet dessen Sechs-Trainer-Session. Domänen-Rotation, Sprech-Domäne und der atomweise Scheduler entfallen.

**Tech Stack:** Kotlin 2.1.20, Jetpack Compose (BOM 2025.12.00), kotlinx.serialization 1.8.1 (sealed polymorphism mit `@JsonClassDiscriminator`), DataStore Preferences, JUnit4. minSdk 26, compileSdk 36, dark-only.

## Global Constraints

Diese gelten für **jede** Task. Quelle: [`docs/PRODUCT_PRINCIPLES.md`](../PRODUCT_PRINCIPLES.md), [`AGENTS.md`](../../AGENTS.md) und die Nutzerentscheidungen dieser Session.

- **Prosa in diesem Plan ist deutsch; Code-Identifier, Kommentare und Log-Texte sind englisch.** TTS-/UI-Texte für das Kind sind deutsch.
- **Das Kind kann nicht lesen.** Keine Anweisungs-Headlines, keine Erklärtexte als Chrome. Handlung wird über Icon, Layout und TTS erklärt. Lesbarer Text ist nur erlaubt, wo Buchstaben/Silben/Wörter *die Lernaufgabe selbst* sind.
- **Buttons enthalten niemals Emojis** — nur ASCII oder Canvas/Vektor-Icons aus `ui/components/AbcIcons.kt`. „Weiter" rechts ausgerichtet.
- **Fehlerfeedback wird gesprochen, nie als Fehlersatz angezeigt.**
- **Erfolgspipeline bleibt unverändert:** Antwort vorsprechen → Stern (`SuccessBurst`) → erst dann weiter (`SuccessPhase.SpeakAnswer` → `ShowBurst` → `Idle`).
- **Distraktoren nur aus bereits geübten, echten Atomen**, max. 2 pro Runde, Tray ≤ 5 Kacheln. Erste Begegnung mit neuem Stoff bleibt distraktorfrei. Distraktoren sind ab Schema v2 **im Content autoriert**, nicht mehr zur Laufzeit gewürfelt.
- **Drag & Drop committet nur bei echtem Zonentreffer** (Hit-Testing); daneben losgelassen wird straflos zurückgeschnappt. Jede Drag-Interaktion hat eine Tap-to-place-Alternative (R15).
- **Dark-only**, Farben ausschließlich aus `ui/theme/Color.kt` (`NightInk`, `NightPanel`, `NightElevated`, `SoftMint`, `SoftCoral`, `SoftSand`, `SoftSky`, `MutedText`).
- **Maße aus `ui/theme/Dimens.kt`** (`AbcDimens.kidTouch = 80.dp` ist die Mindest-Touchfläche für Kinder-Controls).
- **Offline:** keine Netz-Abhängigkeit, kein neues Gradle-Dependency ohne Not. Dieser Plan fügt **keine** neuen Dependencies hinzu.
- **Kein Emoji in `letter_trace`/`syllable_merge`/`word_build`-Kacheln** (Puzzle-Teile ohne Icons).
- Test-Kommando: `./gradlew :app:testDebugUnitTest` · Build: `./gradlew :app:assembleDebug`

## Abweichungen vom bisherigen Plan-Artefakt

Der vorherige Plan [`2026-07-26-001-feat-abc-vorschul-app-plan.md`](2026-07-26-001-feat-abc-vorschul-app-plan.md) wird durch diesen Plan in folgenden Punkten überschrieben. Task 12 dokumentiert das im alten Artefakt als Addendum A2:

| Alt | Neu | Grund |
|-----|-----|-------|
| R5 (cloze/word-order als Lese-Mechanik) | Fünf neue Trainer-Typen | Nutzerentscheidung: „Entferne die alten trainer typen, außer Rechnen" |
| R6 (Sprech-Items mit „Sprich mit!"-Cue, `speech_cloze`) | **Zurückgezogen.** Sprech-Domäne entfällt komplett; TTS bleibt für Prompts/Vorlesen | Nutzerentscheidung in dieser Session |
| R16 / F1 (5er-Mix mit Domänen-Rotation) | Lektions-Session mit genau 6 Trainern in fester didaktischer Reihenfolge | Konzept Teil 1: „Jede Lektion besteht aus exakt 5 Trainern" + Rechnen |
| Key Decision „kein Pfad-Screen" / Addendum A1 „geplant, nicht implementiert" | Pfad-Screen ist implementierter App-Einstieg | Nutzerauftrag „Berücksichtige Plan für Path on homescreen" |
| A1 Open Question „Math/Speech passen nicht in den linearen Pfad" | **Gelöst:** Rechnen ist Trainer 6 in *jeder* Lektion, kontextspezifische Icons, keine Lesewörter. Kein separater Rechen-Strang, kein „Freies Üben" | Nutzerentscheidung in dieser Session |
| Konzept Teil 1: „exakt 5 Trainer" | 6 Trainer (5 didaktische + Rechnen) | Nutzerentscheidung: „füge es in jede lektion mit ein, für mehr Abwechslung" |
| Konzept Teil 1 Trainer 5 Variante B (Mathe-Plural mit Wortkarten „Haus"/„Häuser") | Variante B entfällt; Trainer 5 ist immer Satz-Architekt. Der Plural wird im Rechen-Trainer **nur gesprochen** | Nutzerentscheidung: „Es gibt hier keine Wörter zum lesen/schreiben" im Rechnen |
| Konzept Teil 2 Lektion 5 Ziel-Wort „Ufo (U-fo)" | Im Wort-Bauer durch `Tor`/`rot` ersetzt; `Ufo` bleibt Bild-/Hörwort | „F" ist in Lektion 5 noch nicht eingeführt — der Wort-Bauer darf keine unbekannten Graphem-Kacheln zeigen |

**Content-Umfang dieses Durchgangs (Nutzerentscheidung):** Alle sechs Trainer-Engines vollständig; Content vollständig für Lektionen 1–6 (Phase 1+2). Lektionen 7–16 sind als `status: "planned"`-Knoten im Pfad sichtbar und gesperrt — reines Content-Nachziehen, ohne weiteren Code.

---

## File Structure

### Neu

| Datei | Verantwortung |
|-------|---------------|
| `app/src/main/assets/content/lessons.json` | 16 Lektionen: Reihenfolge, Phase, Knoten-Label, Status, Task-IDs |
| `app/src/main/java/.../content/TaskSpecs.kt` | Polymorphe `TaskSpec`-Hierarchie + Runden-Typen (ein Typ pro Trainer) |
| `app/src/main/java/.../content/LessonModels.kt` | `Lesson`, `LessonStatus`, `LessonsFile`, `TrainerKind` |
| `app/src/main/java/.../progress/LessonGating.kt` | `LessonState`-Ableitung, Freischaltregel, `nextPlayable` |
| `app/src/main/java/.../ui/path/PathGeometry.kt` | Reine S-Kurven-Geometrie (JVM-testbar, keine Compose-Typen) |
| `app/src/main/java/.../ui/path/PathScreen.kt` | Pfad-Screen: Kurve, Knoten, Zustände, Tap-Handling |
| `app/src/main/java/.../ui/exercise/drag/DragHitTest.kt` | Reines Hit-Testing Karte→Zone (größte Überlappung) |
| `app/src/main/java/.../ui/exercise/drag/DragField.kt` | `DragFieldState`, `DragCard`, `DropZone` — geteilte Drag-Primitive |
| `app/src/main/java/.../ui/exercise/OrderedPlacement.kt` | Geteilte Platzierungs-Logik für Wort-Bauer + Satz-Architekt |
| `app/src/main/java/.../ui/exercise/SoundPositionTrainer.kt` | Trainer 1: Lokomotive mit drei Waggons |
| `app/src/main/java/.../ui/exercise/TraceGeometry.kt` | Trainer 2: reine Strich-/Stern-/Korridor-Geometrie |
| `app/src/main/java/.../ui/exercise/LetterTraceTrainer.kt` | Trainer 2: Buchstaben-Straße, Fahrzeug, Sterne |
| `app/src/main/java/.../ui/exercise/SyllableMergeTrainer.kt` | Trainer 3: Eisschollen, Dehnton, Verschmelzung |
| `app/src/main/java/.../ui/exercise/WordBuildTrainer.kt` | Trainer 4: Bild + Schablonen-Rahmen + Silbenklötze |
| `app/src/main/java/.../ui/exercise/SentenceOrderTrainer.kt` | Trainer 5: Wäscheleine mit Wortschildern |
| `app/src/main/java/.../ui/exercise/TrainerHost.kt` | Dispatch `TaskSpec` → Trainer-Composable |

### Geändert

| Datei | Änderung |
|-------|----------|
| `content/ContentModels.kt` | Schema v2: `Domain`/`TaskType`/`TaskTemplate` und alle `compose*`/`tier*`-Helfer raus; `AtomKind.digraph`, `GlyphStroke`, `Atom.strokes` rein; `ContentPack` trägt Lektionen und `tasks` als Map |
| `content/ContentRepository.kt` | Lädt `lessons.json`; polymorphes `Json` mit `classDiscriminator = "trainer"` |
| `content/ContentValidator.kt` | Neue Regeln (Trainer-Reihenfolge pro Lektion, Strichdaten, Block-Konkatenation, Summen) |
| `progress/ProgressModels.kt` | `taskStats` rein, `packIntroCompleted` raus; `SessionSnapshot` auf `lessonId`/`trainerIndex`/`roundIndex` |
| `progress/ProgressRepository.kt` | `markPackIntroCompleted()` raus, `recordTaskAttempt`-Pfad über Engine |
| `progress/ProgressionEngine.kt` | `mathKey(CountAddRound)`, `recordTaskAttempt`; `TaskTemplate`-Import raus |
| `session/SessionModels.kt` | `AppScreen.Path`; `roundIndex`; `ScheduledTask` → `ScheduledTrainer`; `Domain` raus |
| `session/SessionViewModel.kt` | Lektions-Session statt Mix; Runden-Fortschritt; Pfad-Navigation |
| `ui/shell/TaskShell.kt` | Rendert Pfad **oder** Practice; `TrainerHost` statt drei Domänen-Zweige; Runden-Punkte-Anzeige |
| `ui/exercise/MathExercise.kt` | Auf `CountAddRound`; Icon aus `iconAtomId`; Ziffern-Fallback lokal erzeugt |
| `ui/exercise/VisualQuantityBoard.kt` | Signatur auf Runde + Scaffold |
| `ui/exercise/MathHinting.kt` | `usesNumberPad` auf `ScaffoldLevel` |
| `MainActivity.kt` | Back-Handling für Pfad-Ebene |
| `res/values/strings.xml` | A11y-`contentDescription`s für Waggons/Knoten/Trainer |
| `README.md`, `AGENTS.md`, `docs/PRODUCT_PRINCIPLES.md` | Neues Trainer- und Pfad-Modell |

### Gelöscht

`ui/exercise/ReadingExercise.kt` · `ui/exercise/SpeechExercise.kt` · `ui/exercise/DragSlotBoard.kt` · `ui/exercise/ScaffoldMapping.kt` · `session/DistractorPicker.kt` · `session/SessionScheduler.kt` · `app/src/test/.../session/SessionSchedulerTest.kt` · `app/src/test/.../session/DistractorPickerTest.kt` · `app/src/test/.../ui/exercise/ScaffoldMappingTest.kt` · `app/src/test/.../content/ComposePartsForTest.kt`

---

## Task 1: Content-Schema v2, Validator und Lektion 1

**Files:**
- Create: `app/src/main/java/app/abcvorschule/content/TaskSpecs.kt`
- Create: `app/src/main/java/app/abcvorschule/content/LessonModels.kt`
- Create: `app/src/main/assets/content/lessons.json`
- Modify: `app/src/main/java/app/abcvorschule/content/ContentModels.kt` (komplett ersetzen)
- Modify: `app/src/main/java/app/abcvorschule/content/ContentValidator.kt` (komplett ersetzen)
- Modify: `app/src/main/java/app/abcvorschule/content/ContentRepository.kt`
- Modify: `app/src/main/assets/content/pack.manifest.json`, `atoms.json`, `sentences.json`, `tasks.json` (komplett ersetzen)
- Modify: `app/src/test/resources/content/pack.manifest.json`, `atoms.json`, `sentences.json`, `tasks.json` (Kopien der Assets), Create: `app/src/test/resources/content/lessons.json`
- Test: `app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt` (ersetzen), `ContentRepositoryTest.kt` (ersetzen)
- Delete: `app/src/test/java/app/abcvorschule/content/ComposePartsForTest.kt`

**Interfaces:**
- Consumes: nichts (erste Task).
- Produces:
  - `enum class AtomKind { letter, digraph, syllable, word, other }`
  - `enum class SoundSlot { start, middle, end }`
  - `data class GlyphStroke(val points: List<List<Double>>)`
  - `data class Atom(id, lemma, display, emoji, kind, pluralDisplay, pluralHighlight, strokes)`
  - `data class Sentence(id, atomIds, tts, displayOverride)`
  - `sealed interface TaskSpec { val id: String }` mit `SoundPositionSpec`, `LetterTraceSpec`, `SyllableMergeSpec`, `WordBuildSpec`, `SentenceOrderSpec`, `CountAddSpec`
  - Runden: `SoundPositionRound`, `LetterTraceRound`, `SyllableMergeRound`, `WordBuildRound`, `SentenceOrderRound`, `CountAddRound`, alle `: TrainerRound` mit `val promptTts: String`
  - `data class WordBlock(val atomId: String, val display: String)`
  - `enum class TrainerKind { sound_position, letter_trace, syllable_merge, word_build, sentence_order, count_add }`, `val TaskSpec.kind: TrainerKind`, `val TaskSpec.roundCount: Int`, `fun TaskSpec.round(index: Int): TrainerRound`
  - `fun CountAddRound.spokenAnswer(icon: Atom?): String` — "1 Ameise" / "2 Ameisen"
  - `enum class LessonStatus { authored, planned }`
  - `data class Lesson(id, index, phase, title, nodeLabel, status, focusAtomIds, taskIds)`
  - `data class ContentPack(manifest, atoms, sentences, tasks: Map<String, TaskSpec>, lessons: List<Lesson>)` mit `atom(id)`, `sentence(id)`, `task(id)`, `lesson(id)`, `tasksOf(lesson)`, `authoredLessons`
  - `object ContentValidator { fun validate(pack): List<ValidationIssue>; fun requireValid(pack): ContentPack; const val TrainerOrder: List<TrainerKind> }`

---

- [ ] **Step 1: Failing Test für Schema und Validator schreiben**

Ersetze `app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt` vollständig:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentValidatorTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun issuesOf(mutate: (ContentPack) -> ContentPack): List<String> =
        ContentValidator.validate(mutate(pack)).map { it.message }

    @Test
    fun shippedPackIsValid() {
        assertEquals(emptyList<ValidationIssue>(), ContentValidator.validate(pack))
    }

    @Test
    fun trainerOrderIsTheSixDidacticTrainers() {
        assertEquals(
            listOf(
                TrainerKind.sound_position,
                TrainerKind.letter_trace,
                TrainerKind.syllable_merge,
                TrainerKind.word_build,
                TrainerKind.sentence_order,
                TrainerKind.count_add,
            ),
            ContentValidator.TrainerOrder,
        )
    }

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

    @Test
    fun danglingAtomReferenceIsRejected() {
        val broken = pack.tasks.values.filterIsInstance<WordBuildSpec>().first().let { spec ->
            spec.copy(
                rounds = spec.rounds.map { it.copy(targetAtomId = "does-not-exist") },
            )
        }
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("does-not-exist") })
    }

    @Test
    fun traceRoundWithoutStrokeDataIsRejected() {
        val strippedAtoms = pack.atoms.mapValues { (_, atom) -> atom.copy(strokes = emptyList()) }
        val issues = issuesOf { it.copy(atoms = strippedAtoms) }
        assertTrue(issues.any { it.contains("has no strokes") })
    }

    @Test
    fun wordBuildBlocksMustSpellTheTargetDisplay() {
        val spec = pack.tasks.values.filterIsInstance<WordBuildSpec>().first()
        val broken = spec.copy(
            rounds = spec.rounds.map { round ->
                round.copy(blocks = round.blocks.reversed() + WordBlock("letter-m", "X"))
            },
        )
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("do not spell") })
    }

    @Test
    fun countAddSumMustMatchAnswer() {
        val spec = pack.tasks.values.filterIsInstance<CountAddSpec>().first()
        val broken = spec.copy(rounds = spec.rounds.map { it.copy(answer = it.answer + 1) })
        val issues = issuesOf { it.copy(tasks = it.tasks + (broken.id to broken)) }
        assertTrue(issues.any { it.contains("answer") })
    }

    @Test
    fun lessonIndicesAreContiguousFromOne() {
        assertEquals((1..pack.lessons.size).toList(), pack.lessons.map { it.index })
    }

    @Test
    fun glyphStrokePointsStayInsideUnitBox() {
        pack.atoms.values.filter { it.strokes.isNotEmpty() }.forEach { atom ->
            atom.strokes.forEach { stroke ->
                assertTrue("stroke of ${atom.id} needs >= 2 points", stroke.points.size >= 2)
                stroke.points.forEach { p ->
                    assertEquals("point of ${atom.id} needs x,y", 2, p.size)
                    assertTrue("${atom.id} x out of range", p[0] in 0.0..1.0)
                    assertTrue("${atom.id} y out of range", p[1] in 0.0..1.0)
                }
            }
        }
    }
}
```

Ersetze `app/src/test/java/app/abcvorschule/content/ContentRepositoryTest.kt` vollständig:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentRepositoryTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun packLoadsAllSixteenLessons() {
        assertEquals(16, pack.lessons.size)
        assertEquals(6, pack.authoredLessons.size)
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
        assertEquals(6, lesson.taskIds.size)
    }

    @Test
    fun polymorphicTasksDeserializeToTheirTrainerType() {
        val kinds = pack.tasksOf(pack.lesson("l01")).map { it.kind }
        assertEquals(ContentValidator.TrainerOrder, kinds)
        assertTrue(pack.task("l01-t1") is SoundPositionSpec)
        assertTrue(pack.task("l01-t6") is CountAddSpec)
    }

    @Test
    fun atomsAreSharedAcrossTrainers() {
        // AE6 in new clothes: one atom, one emoji, reused by several trainers.
        val ma = pack.atom("ma")
        assertEquals("ma", ma.display)
        val merge = pack.task("l01-t3") as SyllableMergeSpec
        val build = pack.task("l01-t4") as WordBuildSpec
        assertEquals("ma", merge.rounds.first().resultAtomId)
        assertTrue(build.rounds.first().blocks.any { it.atomId == "ma" })
    }

    @Test
    fun traceRoundsResolveStrokeDataFromAtoms() {
        val trace = pack.task("l01-t2") as LetterTraceSpec
        trace.rounds.forEach { round ->
            val atom = pack.atom(round.atomId)
            assertTrue("${atom.id} needs strokes", atom.strokes.isNotEmpty())
        }
        assertNotNull(pack.atom("letter-a").strokes.firstOrNull())
    }

    @Test
    fun countAddRoundsUseLessonContextIcons() {
        val math = pack.task("l01-t6") as CountAddSpec
        math.rounds.forEach { round ->
            assertTrue(round.iconAtomId in pack.atoms.keys)
            assertTrue(pack.atom(round.iconAtomId).emoji.isNotBlank())
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.content.*"`
Expected: Compile-Fehler — `TrainerKind`, `WordBuildSpec`, `LessonStatus`, `authoredLessons`, `tasksOf` existieren nicht.

- [ ] **Step 3: `ContentModels.kt` ersetzen**

```kotlin
package app.abcvorschule.content

import kotlinx.serialization.Serializable

@Serializable
data class PackManifest(
    val schemaVersion: Int,
    val packId: String,
    val title: String,
    val locale: String = "de",
)

@Serializable
enum class AtomKind {
    /** Single grapheme, upper/lower case pair (M, A). */
    letter,

    /** Multi-letter grapheme spoken as one sound (Ei, Au, Sch, ck). */
    digraph,
    syllable,
    word,

    /** Picture-only vocabulary used for listening/counting, never read or spelled. */
    other,
}

/** Where a phoneme sits inside a spoken word. */
@Serializable
enum class SoundSlot {
    start,
    middle,
    end,
}

/**
 * One pen stroke of a glyph, as normalized points in a 0..1 box, y pointing down.
 * Stroke order and point order encode the writing direction taught in Trainer 2.
 */
@Serializable
data class GlyphStroke(val points: List<List<Double>>)

@Serializable
data class Atom(
    val id: String,
    val lemma: String,
    val display: String,
    val emoji: String,
    val kind: AtomKind = AtomKind.word,
    val pluralDisplay: String? = null,
    val pluralHighlight: String? = null,
    /** Uppercase glyph strokes; required for atoms used by a letter_trace round. */
    val strokes: List<GlyphStroke> = emptyList(),
)

@Serializable
data class AtomsFile(val atoms: List<Atom>)

@Serializable
data class Sentence(
    val id: String,
    val atomIds: List<String>,
    val tts: String,
    /** Rendered word forms when they differ from the atom display (inflection, punctuation). */
    val displayOverride: List<String>? = null,
)

@Serializable
data class SentencesFile(val sentences: List<Sentence>)

data class ContentPack(
    val manifest: PackManifest,
    val atoms: Map<String, Atom>,
    val sentences: Map<String, Sentence>,
    val tasks: Map<String, TaskSpec>,
    val lessons: List<Lesson>,
) {
    val authoredLessons: List<Lesson> = lessons.filter { it.status == LessonStatus.authored }

    fun atom(id: String): Atom = atoms.getValue(id)

    fun sentence(id: String): Sentence = sentences.getValue(id)

    fun task(id: String): TaskSpec = tasks.getValue(id)

    fun lesson(id: String): Lesson = lessons.first { it.id == id }

    fun tasksOf(lesson: Lesson): List<TaskSpec> = lesson.taskIds.map { task(it) }

    /** Rendered words of a sentence, aligned with [Sentence.atomIds]. */
    fun sentenceWords(sentence: Sentence): List<String> =
        sentence.displayOverride ?: sentence.atomIds.map { atom(it).display }
}
```

- [ ] **Step 4: `TaskSpecs.kt` anlegen**

```kotlin
package app.abcvorschule.content

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

/** One playable step inside a trainer. Every round carries its own spoken prompt. */
interface TrainerRound {
    val promptTts: String
}

/**
 * A trainer instance in a lesson. One spec = one screen type played over 1..n rounds;
 * the JSON discriminator is the trainer name, so content stays readable.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("trainer")
sealed interface TaskSpec {
    val id: String
}

enum class TrainerKind {
    sound_position,
    letter_trace,
    syllable_merge,
    word_build,
    sentence_order,
    count_add,
}

// --- Trainer 1: Auditiver Finder --------------------------------------------

@Serializable
@SerialName("sound_position")
data class SoundPositionSpec(
    override val id: String,
    /** Spoken form of the hunted phoneme, e.g. "Mmm". */
    val phonemeTts: String,
    val rounds: List<SoundPositionRound>,
) : TaskSpec

@Serializable
data class SoundPositionRound(
    override val promptTts: String,
    /** Picture-word the child sorts; rendered as emoji only, never as text. */
    val atomId: String,
    val slot: SoundSlot,
    /** Segmented re-reading used on a miss, e.g. "A - Mmm - eise". */
    val missTts: String,
) : TrainerRound

// --- Trainer 2: Visueller Spurensucher --------------------------------------

@Serializable
@SerialName("letter_trace")
data class LetterTraceSpec(
    override val id: String,
    val rounds: List<LetterTraceRound>,
) : TaskSpec

@Serializable
data class LetterTraceRound(
    override val promptTts: String,
    /** Atom carrying the [Atom.strokes] to trace. */
    val atomId: String,
    /** Uppercase glyph drawn as the road, e.g. "A". */
    val glyph: String,
    /** Spoken reward after the glyph is complete, e.g. "A wie Ampel". */
    val rewardTts: String,
    /** Emoji the road morphs into. Reward visual only — never a button label. */
    val rewardEmoji: String,
) : TrainerRound

// --- Trainer 3: Silben-Verschmelzer -----------------------------------------

@Serializable
@SerialName("syllable_merge")
data class SyllableMergeSpec(
    override val id: String,
    val rounds: List<SyllableMergeRound>,
) : TaskSpec

@Serializable
data class SyllableMergeRound(
    override val promptTts: String,
    val leftAtomId: String,
    val leftDisplay: String,
    val rightAtomId: String,
    val rightDisplay: String,
    val resultAtomId: String,
    val resultDisplay: String,
    /** Stretched consonant played while dragging, e.g. "Mmmmm". */
    val stretchTts: String,
) : TrainerRound

// --- Trainer 4: Wort-Bauer --------------------------------------------------

@Serializable
data class WordBlock(val atomId: String, val display: String)

@Serializable
@SerialName("word_build")
data class WordBuildSpec(
    override val id: String,
    val rounds: List<WordBuildRound>,
) : TaskSpec

@Serializable
data class WordBuildRound(
    override val promptTts: String,
    val targetAtomId: String,
    /** Solution blocks in reading order; their displays must spell the target. */
    val blocks: List<WordBlock>,
    /** Extra tray tiles from already-practiced atoms. Empty on first encounter. */
    val distractors: List<WordBlock> = emptyList(),
) : TrainerRound

// --- Trainer 5: Satz-Architekt ----------------------------------------------

@Serializable
@SerialName("sentence_order")
data class SentenceOrderSpec(
    override val id: String,
    val rounds: List<SentenceOrderRound>,
) : TaskSpec

@Serializable
data class SentenceOrderRound(
    override val promptTts: String,
    val sentenceId: String,
    /** Illustration anchoring the sentence; emoji of this atom. */
    val illustrationAtomId: String? = null,
    val distractors: List<WordBlock> = emptyList(),
    /**
     * Words the child recognizes as a whole picture-word although its graphemes
     * are not taught yet (the curriculum does this for "Tor" in lesson 3, before
     * R is introduced). Documents the exception instead of hiding it.
     */
    val holisticAtomIds: List<String> = emptyList(),
) : TrainerRound

// --- Trainer 6: Rechnen -----------------------------------------------------

@Serializable
@SerialName("count_add")
data class CountAddSpec(
    override val id: String,
    val rounds: List<CountAddRound>,
) : TaskSpec

/**
 * Pure quantity arithmetic. No words are shown or built here — singular/plural
 * lives in [promptTts] only, and the counted objects come from the lesson's own
 * picture vocabulary so the icons stay in context.
 */
@Serializable
data class CountAddRound(
    override val promptTts: String,
    val iconAtomId: String,
    val left: Int,
    val right: Int,
    val answer: Int,
    val operation: String = "add",
    val difficultyBand: String? = null,
) : TrainerRound

@Serializable
data class TasksFile(val tasks: List<TaskSpec>)

val TaskSpec.kind: TrainerKind
    get() = when (this) {
        is SoundPositionSpec -> TrainerKind.sound_position
        is LetterTraceSpec -> TrainerKind.letter_trace
        is SyllableMergeSpec -> TrainerKind.syllable_merge
        is WordBuildSpec -> TrainerKind.word_build
        is SentenceOrderSpec -> TrainerKind.sentence_order
        is CountAddSpec -> TrainerKind.count_add
    }

val TaskSpec.rounds: List<TrainerRound>
    get() = when (this) {
        is SoundPositionSpec -> rounds
        is LetterTraceSpec -> rounds
        is SyllableMergeSpec -> rounds
        is WordBuildSpec -> rounds
        is SentenceOrderSpec -> rounds
        is CountAddSpec -> rounds
    }

val TaskSpec.roundCount: Int get() = rounds.size

fun TaskSpec.round(index: Int): TrainerRound? = rounds.getOrNull(index)

/**
 * Spoken answer for a counting round: "1 Ameise" / "2 Ameisen" — never a bare
 * number. Rechnen shows no words, so the plural is carried entirely by speech.
 */
fun CountAddRound.spokenAnswer(icon: Atom?): String {
    val noun = when {
        icon == null -> ""
        answer == 1 -> icon.display
        else -> icon.pluralDisplay ?: icon.display
    }
    return "$answer $noun".trim()
}

/** Atom ids a round scores against, used for per-atom stats and scaffolds. */
fun TrainerRound.scoredAtomIds(): List<String> = when (this) {
    is SoundPositionRound -> listOf(atomId)
    is LetterTraceRound -> listOf(atomId)
    is SyllableMergeRound -> listOf(leftAtomId, rightAtomId, resultAtomId).distinct()
    is WordBuildRound -> (blocks.map { it.atomId } + targetAtomId).distinct()
    is SentenceOrderRound -> emptyList() // filled by the trainer from the sentence
    else -> emptyList()
}
```

Hinweis: `SentenceOrderRound.scoredAtomIds()` bleibt leer, weil die Atom-IDs erst über `pack.sentence(sentenceId).atomIds` auflösbar sind — das macht `SessionViewModel` in Task 3.

- [ ] **Step 5: `LessonModels.kt` anlegen**

```kotlin
package app.abcvorschule.content

import kotlinx.serialization.Serializable

@Serializable
enum class LessonStatus {
    /** Fully authored: six trainers, playable. */
    authored,

    /** Declared in the Fibel order, content still to come. Shown locked on the path. */
    planned,
}

@Serializable
data class Lesson(
    val id: String,
    /** 1-based position in the Fibel order; also the path node order. */
    val index: Int,
    /** Curriculum phase 1..5, used for path grouping only. */
    val phase: Int,
    /** Parent-facing label, e.g. "M & A". Never read aloud to the child as an instruction. */
    val title: String,
    /** Minimal path node label: a letter, a syllable, or a word pair. */
    val nodeLabel: String,
    val status: LessonStatus,
    val focusAtomIds: List<String> = emptyList(),
    /** Exactly the six trainers in ContentValidator.TrainerOrder when authored. */
    val taskIds: List<String> = emptyList(),
)

@Serializable
data class LessonsFile(val lessons: List<Lesson>)
```

- [ ] **Step 6: `ContentValidator.kt` ersetzen**

```kotlin
package app.abcvorschule.content

data class ValidationIssue(val message: String)

class ContentValidationException(val issues: List<ValidationIssue>) :
    IllegalArgumentException(issues.joinToString("\n") { it.message })

object ContentValidator {
    /** Fixed didactic sequence every authored lesson must follow. */
    val TrainerOrder: List<TrainerKind> = listOf(
        TrainerKind.sound_position,
        TrainerKind.letter_trace,
        TrainerKind.syllable_merge,
        TrainerKind.word_build,
        TrainerKind.sentence_order,
        TrainerKind.count_add,
    )

    private const val MinSoundPositionRounds = 2

    /** Authored-distractor budget: preschoolers must be able to scan the tray. */
    private const val MaxDistractorsPerRound = 2
    private const val MaxWordTrayTiles = 5
    private const val MaxSentenceTrayTiles = 6

    fun validate(pack: ContentPack): List<ValidationIssue> {
        val issues = mutableListOf<ValidationIssue>()
        val atomIds = pack.atoms.keys

        if (pack.manifest.schemaVersion < 2) {
            issues += ValidationIssue("schemaVersion must be >= 2")
        }

        fun requireAtom(where: String, id: String) {
            if (id !in atomIds) issues += ValidationIssue("$where references missing atom $id")
        }

        pack.atoms.values.forEach { atom ->
            atom.strokes.forEachIndexed { i, stroke ->
                if (stroke.points.size < 2) {
                    issues += ValidationIssue("atom ${atom.id} stroke $i needs at least 2 points")
                }
                stroke.points.forEach { p ->
                    if (p.size != 2) {
                        issues += ValidationIssue("atom ${atom.id} stroke $i has a non-2D point")
                    } else if (p[0] !in 0.0..1.0 || p[1] !in 0.0..1.0) {
                        issues += ValidationIssue("atom ${atom.id} stroke $i leaves the unit box")
                    }
                }
            }
        }

        pack.sentences.values.forEach { sentence ->
            if (sentence.atomIds.isEmpty()) {
                issues += ValidationIssue("sentence ${sentence.id} has no atoms")
            }
            sentence.atomIds.forEach { requireAtom("sentence ${sentence.id}", it) }
            sentence.displayOverride?.let { override ->
                if (override.size != sentence.atomIds.size) {
                    issues += ValidationIssue(
                        "sentence ${sentence.id} displayOverride size must match atomIds",
                    )
                }
            }
        }

        pack.tasks.forEach { (id, spec) ->
            if (spec.id != id) {
                issues += ValidationIssue("task key $id does not match spec id ${spec.id}")
            }
            if (spec.roundCount == 0) {
                issues += ValidationIssue("task $id has no rounds")
            }
            spec.rounds.forEach { round ->
                if (round.promptTts.isBlank()) {
                    issues += ValidationIssue("task $id has a round without promptTts")
                }
            }
            when (spec) {
                is SoundPositionSpec -> {
                    if (spec.rounds.size < MinSoundPositionRounds) {
                        issues += ValidationIssue(
                            "task $id needs at least $MinSoundPositionRounds rounds to be failable",
                        )
                    }
                    spec.rounds.forEach { requireAtom("task $id", it.atomId) }
                }
                is LetterTraceSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.atomId)
                    val atom = pack.atoms[round.atomId]
                    if (atom != null && atom.strokes.isEmpty()) {
                        issues += ValidationIssue("task $id traces ${atom.id} which has no strokes")
                    }
                    if (round.glyph.isBlank()) {
                        issues += ValidationIssue("task $id has a trace round without a glyph")
                    }
                }
                is SyllableMergeSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.leftAtomId)
                    requireAtom("task $id", round.rightAtomId)
                    requireAtom("task $id", round.resultAtomId)
                    val spelled = round.leftDisplay + round.rightDisplay
                    val expected = pack.atoms[round.resultAtomId]?.display
                    if (expected != null && !spelled.equals(expected, ignoreCase = true)) {
                        issues += ValidationIssue(
                            "task $id merge parts '$spelled' do not spell result '$expected'",
                        )
                    }
                }
                is WordBuildSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.targetAtomId)
                    if (round.blocks.isEmpty()) {
                        issues += ValidationIssue("task $id has a word_build round without blocks")
                    }
                    (round.blocks + round.distractors).forEach { requireAtom("task $id", it.atomId) }
                    val spelled = round.blocks.joinToString("") { it.display }
                    val expected = pack.atoms[round.targetAtomId]?.display
                    if (expected != null && spelled != expected) {
                        issues += ValidationIssue(
                            "task $id blocks '$spelled' do not spell target '$expected'",
                        )
                    }
                    val duplicate = round.distractors.map { it.display }
                        .intersect(round.blocks.map { it.display }.toSet())
                    if (duplicate.isNotEmpty()) {
                        issues += ValidationIssue(
                            "task $id distractors duplicate solution blocks $duplicate",
                        )
                    }
                    if (round.distractors.size > MaxDistractorsPerRound) {
                        issues += ValidationIssue(
                            "task $id has ${round.distractors.size} distractors; max is $MaxDistractorsPerRound",
                        )
                    }
                    val tray = round.blocks.size + round.distractors.size
                    if (tray > MaxWordTrayTiles) {
                        issues += ValidationIssue(
                            "task $id tray holds $tray tiles; max is $MaxWordTrayTiles",
                        )
                    }
                }
                is SentenceOrderSpec -> spec.rounds.forEach { round ->
                    val sentence = pack.sentences[round.sentenceId]
                    if (sentence == null) {
                        issues += ValidationIssue("task $id references missing sentence ${round.sentenceId}")
                    }
                    round.illustrationAtomId?.let { requireAtom("task $id", it) }
                    round.distractors.forEach { requireAtom("task $id", it.atomId) }
                    round.holisticAtomIds.forEach { holistic ->
                        requireAtom("task $id", holistic)
                        if (sentence != null && holistic !in sentence.atomIds) {
                            issues += ValidationIssue(
                                "task $id marks $holistic holistic but the sentence does not use it",
                            )
                        }
                    }
                    if (round.distractors.size > MaxDistractorsPerRound) {
                        issues += ValidationIssue(
                            "task $id has ${round.distractors.size} distractors; max is $MaxDistractorsPerRound",
                        )
                    }
                    val tray = (sentence?.atomIds?.size ?: 0) + round.distractors.size
                    if (tray > MaxSentenceTrayTiles) {
                        issues += ValidationIssue(
                            "task $id tray holds $tray tiles; max is $MaxSentenceTrayTiles",
                        )
                    }
                }
                is CountAddSpec -> spec.rounds.forEach { round ->
                    requireAtom("task $id", round.iconAtomId)
                    if (round.left < 0 || round.right < 0) {
                        issues += ValidationIssue("task $id has a negative operand")
                    }
                    // Only addition exists in v1; an unknown operation must not slip
                    // past the sum check unvalidated.
                    if (round.operation != "add") {
                        issues += ValidationIssue(
                            "task $id uses unsupported operation '${round.operation}'",
                        )
                    } else if (round.left + round.right != round.answer) {
                        issues += ValidationIssue(
                            "task $id answer ${round.answer} does not match ${round.left}+${round.right}",
                        )
                    }
                }
            }
        }

        val lessonIds = mutableSetOf<String>()
        pack.lessons.forEach { lesson ->
            if (!lessonIds.add(lesson.id)) {
                issues += ValidationIssue("duplicate lesson id ${lesson.id}")
            }
            lesson.focusAtomIds.forEach { requireAtom("lesson ${lesson.id}", it) }
            val missing = lesson.taskIds.filter { it !in pack.tasks }
            if (missing.isNotEmpty()) {
                issues += ValidationIssue("lesson ${lesson.id} references missing tasks $missing")
            }
            when (lesson.status) {
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
                LessonStatus.planned -> {
                    if (lesson.taskIds.isNotEmpty()) {
                        issues += ValidationIssue("planned lesson ${lesson.id} must not hold tasks")
                    }
                }
            }
        }
        if (pack.lessons.map { it.index } != (1..pack.lessons.size).toList()) {
            issues += ValidationIssue("lesson indices must be contiguous starting at 1")
        }
        if (pack.authoredLessons.isEmpty()) {
            issues += ValidationIssue("pack needs at least one authored lesson")
        }

        val referenced = pack.lessons.flatMap { it.taskIds }.toSet()
        (pack.tasks.keys - referenced).forEach {
            issues += ValidationIssue("task $it is not referenced by any lesson")
        }
        return issues
    }

    fun requireValid(pack: ContentPack): ContentPack {
        val issues = validate(pack)
        if (issues.isNotEmpty()) throw ContentValidationException(issues)
        return pack
    }
}
```

- [ ] **Step 7: `ContentRepository.kt` auf polymorphes JSON und Lektionen umstellen**

```kotlin
package app.abcvorschule.content

import android.content.Context
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import java.io.InputStream

class ContentRepository(
    private val openAsset: (String) -> InputStream,
) {
    @OptIn(ExperimentalSerializationApi::class)
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        // Trainer name is the discriminator so tasks.json reads like the curriculum.
        classDiscriminator = "trainer"
    }

    @Volatile
    private var cached: ContentPack? = null

    fun load(): ContentPack {
        cached?.let { return it }
        val pack = ContentValidator.requireValid(parsePack())
        cached = pack
        return pack
    }

    fun clearCache() {
        cached = null
    }

    private fun parsePack(): ContentPack {
        val manifest = json.decodeFromString<PackManifest>(read("content/pack.manifest.json"))
        val atoms = json.decodeFromString<AtomsFile>(read("content/atoms.json")).atoms
            .associateBy { it.id }
        val sentences = json.decodeFromString<SentencesFile>(read("content/sentences.json")).sentences
            .associateBy { it.id }
        val tasks = json.decodeFromString<TasksFile>(read("content/tasks.json")).tasks
            .associateBy { it.id }
        val lessons = json.decodeFromString<LessonsFile>(read("content/lessons.json")).lessons
            .sortedBy { it.index }
        return ContentPack(manifest, atoms, sentences, tasks, lessons)
    }

    private fun read(path: String): String =
        openAsset(path).bufferedReader().use { it.readText() }

    companion object {
        fun fromContext(context: Context): ContentRepository =
            ContentRepository { path -> context.assets.open(path) }

        fun fromClasspath(
            classLoader: ClassLoader = Thread.currentThread().contextClassLoader!!,
        ): ContentRepository = ContentRepository { path ->
            classLoader.getResourceAsStream(path)
                ?: error("Missing classpath resource: $path")
        }
    }
}
```

- [ ] **Step 8: `pack.manifest.json` ersetzen**

`app/src/main/assets/content/pack.manifest.json`:

```json
{
  "schemaVersion": 2,
  "packId": "fibel-v3",
  "title": "Fibel-Lernpfad",
  "locale": "de"
}
```

- [ ] **Step 9: `atoms.json` mit Lektion-1-Atomen und Strichdaten ersetzen**

`app/src/main/assets/content/atoms.json` — Task 12 ergänzt hier die Lektionen 2–6.

```json
{
  "atoms": [
    {
      "id": "letter-m", "lemma": "M", "display": "M", "emoji": "", "kind": "letter",
      "strokes": [
        { "points": [[0.12, 0.92], [0.12, 0.08], [0.5, 0.62], [0.88, 0.08], [0.88, 0.92]] }
      ]
    },
    {
      "id": "letter-a", "lemma": "A", "display": "A", "emoji": "", "kind": "letter",
      "strokes": [
        { "points": [[0.5, 0.08], [0.14, 0.92]] },
        { "points": [[0.5, 0.08], [0.86, 0.92]] },
        { "points": [[0.26, 0.66], [0.74, 0.66]] }
      ]
    },
    { "id": "ma", "lemma": "ma", "display": "ma", "emoji": "", "kind": "syllable" },
    { "id": "mama", "lemma": "Mama", "display": "Mama", "emoji": "👩", "kind": "word" },
    { "id": "ameise", "lemma": "Ameise", "display": "Ameise", "emoji": "🐜", "kind": "other", "pluralDisplay": "Ameisen" },
    { "id": "maus", "lemma": "Maus", "display": "Maus", "emoji": "🐭", "kind": "other", "pluralDisplay": "Mäuse" },
    { "id": "baum", "lemma": "Baum", "display": "Baum", "emoji": "🌳", "kind": "other", "pluralDisplay": "Bäume" }
  ]
}
```

- [ ] **Step 10: `sentences.json` ersetzen**

`app/src/main/assets/content/sentences.json`:

```json
{
  "sentences": [
    { "id": "s-mama", "atomIds": ["mama"], "tts": "Mama." }
  ]
}
```

- [ ] **Step 11: `tasks.json` mit den sechs Trainern von Lektion 1 ersetzen**

`app/src/main/assets/content/tasks.json`:

```json
{
  "tasks": [
    {
      "trainer": "sound_position",
      "id": "l01-t1",
      "phonemeTts": "Mmm",
      "rounds": [
        {
          "promptTts": "Wir suchen das Mmm. Wo versteckt sich das Mmm?",
          "atomId": "ameise",
          "slot": "middle",
          "missTts": "A - Mmm - eise. Hörst du das Mmm in der Mitte?"
        },
        {
          "promptTts": "Wo versteckt sich das Mmm?",
          "atomId": "maus",
          "slot": "start",
          "missTts": "Mmm - aus. Hörst du das Mmm am Anfang?"
        },
        {
          "promptTts": "Wo versteckt sich das Mmm?",
          "atomId": "baum",
          "slot": "end",
          "missTts": "Bau - Mmm. Hörst du das Mmm am Ende?"
        }
      ]
    },
    {
      "trainer": "letter_trace",
      "id": "l01-t2",
      "rounds": [
        {
          "promptTts": "Spure das große A nach und sammle alle Sterne.",
          "atomId": "letter-a",
          "glyph": "A",
          "rewardTts": "A wie Ampel.",
          "rewardEmoji": "🚦"
        },
        {
          "promptTts": "Spure das große M nach und sammle alle Sterne.",
          "atomId": "letter-m",
          "glyph": "M",
          "rewardTts": "M wie Mond.",
          "rewardEmoji": "🌙"
        }
      ]
    },
    {
      "trainer": "syllable_merge",
      "id": "l01-t3",
      "rounds": [
        {
          "promptTts": "Lass die Buchstaben zusammenrutschen, damit sie ein Lied singen.",
          "leftAtomId": "letter-m",
          "leftDisplay": "m",
          "rightAtomId": "letter-a",
          "rightDisplay": "a",
          "resultAtomId": "ma",
          "resultDisplay": "ma",
          "stretchTts": "Mmmmm"
        }
      ]
    },
    {
      "trainer": "word_build",
      "id": "l01-t4",
      "rounds": [
        {
          "promptTts": "Kannst du das Wort Mama bauen? Suche die passenden Bausteine.",
          "targetAtomId": "mama",
          "blocks": [
            { "atomId": "ma", "display": "Ma" },
            { "atomId": "ma", "display": "ma" }
          ]
        }
      ]
    },
    {
      "trainer": "sentence_order",
      "id": "l01-t5",
      "rounds": [
        {
          "promptTts": "Mama. Hänge das Wort zum Bild.",
          "sentenceId": "s-mama",
          "illustrationAtomId": "mama"
        }
      ]
    },
    {
      "trainer": "count_add",
      "id": "l01-t6",
      "rounds": [
        {
          "promptTts": "Eine Ameise und eine Ameise. Wie viele Ameisen?",
          "iconAtomId": "ameise",
          "left": 1,
          "right": 1,
          "answer": 2
        },
        {
          "promptTts": "Zwei Ameisen und eine Ameise. Wie viele Ameisen?",
          "iconAtomId": "ameise",
          "left": 2,
          "right": 1,
          "answer": 3
        }
      ]
    }
  ]
}
```

- [ ] **Step 12: `lessons.json` mit allen 16 Lektionen anlegen**

`app/src/main/assets/content/lessons.json` — Lektion 1 `authored`, 2–6 werden in Task 12 auf `authored` umgestellt, 7–16 bleiben `planned`.

```json
{
  "lessons": [
    {
      "id": "l01", "index": 1, "phase": 1, "title": "M & A", "nodeLabel": "M a",
      "status": "authored", "focusAtomIds": ["letter-m", "letter-a"],
      "taskIds": ["l01-t1", "l01-t2", "l01-t3", "l01-t4", "l01-t5", "l01-t6"]
    },
    { "id": "l02", "index": 2, "phase": 1, "title": "I & O", "nodeLabel": "I o", "status": "planned" },
    { "id": "l03", "index": 3, "phase": 2, "title": "P & T", "nodeLabel": "P t", "status": "planned" },
    { "id": "l04", "index": 4, "phase": 2, "title": "L & H", "nodeLabel": "L h", "status": "planned" },
    { "id": "l05", "index": 5, "phase": 2, "title": "U & R", "nodeLabel": "U r", "status": "planned" },
    { "id": "l06", "index": 6, "phase": 2, "title": "N, S, E, D", "nodeLabel": "N s e d", "status": "planned" },
    { "id": "l07", "index": 7, "phase": 3, "title": "K & Ei", "nodeLabel": "K ei", "status": "planned" },
    { "id": "l08", "index": 8, "phase": 3, "title": "W, Ch, G", "nodeLabel": "W ch g", "status": "planned" },
    { "id": "l09", "index": 9, "phase": 3, "title": "Au & B", "nodeLabel": "Au b", "status": "planned" },
    { "id": "l10", "index": 10, "phase": 3, "title": "Umlaut-Rätsel", "nodeLabel": "ä ö ü", "status": "planned" },
    { "id": "l11", "index": 11, "phase": 4, "title": "Sch", "nodeLabel": "Sch", "status": "planned" },
    { "id": "l12", "index": 12, "phase": 4, "title": "J, Z, Eu, ß, V", "nodeLabel": "J z eu ß v", "status": "planned" },
    { "id": "l13", "index": 13, "phase": 5, "title": "Ä, Ö, Ü", "nodeLabel": "Ä Ö Ü", "status": "planned" },
    { "id": "l14", "index": 14, "phase": 5, "title": "ck & Pf", "nodeLabel": "ck pf", "status": "planned" },
    { "id": "l15", "index": 15, "phase": 5, "title": "St & Sp", "nodeLabel": "St sp", "status": "planned" },
    { "id": "l16", "index": 16, "phase": 5, "title": "C, Y, X, Qu", "nodeLabel": "C y x qu", "status": "planned" }
  ]
}
```

- [ ] **Step 13: Test-Resources spiegeln und alten Test löschen**

```bash
cp app/src/main/assets/content/pack.manifest.json app/src/test/resources/content/pack.manifest.json
cp app/src/main/assets/content/atoms.json app/src/test/resources/content/atoms.json
cp app/src/main/assets/content/sentences.json app/src/test/resources/content/sentences.json
cp app/src/main/assets/content/tasks.json app/src/test/resources/content/tasks.json
cp app/src/main/assets/content/lessons.json app/src/test/resources/content/lessons.json
rm app/src/test/java/app/abcvorschule/content/ComposePartsForTest.kt
```

- [ ] **Step 14: Content-Tests laufen lassen und grün bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.content.*"`
Expected: PASS. Der Rest des Moduls kompiliert an dieser Stelle noch **nicht** (`SessionScheduler`, `ReadingExercise` usw. referenzieren die entfernten Typen) — das ist erwartet und wird in Task 2/3 aufgelöst. Kompiliert `:app:compileDebugKotlin` nicht, ist das hier kein Blocker; die Content-Tests laufen über `compileDebugUnitTestKotlin` nur, wenn das Hauptmodul kompiliert. **Deshalb gilt: Task 1, 2 und 3 werden zusammen committet** (siehe Step 15) — führe Step 14 erst nach Task 3 als Gesamtlauf aus und notiere hier nur, dass die Content-Tests fachlich fertig sind.

- [ ] **Step 15: Zwischenstand festhalten (kein Commit)**

Kein Commit am Ende dieser Task: das Modul ist erst nach Task 3 wieder kompilierbar. Lege stattdessen die Änderungen bereit:

```bash
git add app/src/main/java/app/abcvorschule/content app/src/main/assets/content app/src/test/resources/content app/src/test/java/app/abcvorschule/content
git status --short
```

---

## Task 2: Fortschrittsmodell v2 und Lektions-Freischaltung

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/progress/ProgressModels.kt`
- Modify: `app/src/main/java/app/abcvorschule/progress/ProgressRepository.kt`
- Modify: `app/src/main/java/app/abcvorschule/progress/ProgressionEngine.kt`
- Create: `app/src/main/java/app/abcvorschule/progress/LessonGating.kt`
- Test: `app/src/test/java/app/abcvorschule/progress/ProgressionEngineTest.kt` (ersetzen), `app/src/test/java/app/abcvorschule/progress/LessonGatingTest.kt` (neu)

**Interfaces:**
- Consumes: `ContentPack`, `Lesson`, `LessonStatus`, `CountAddRound`, `TaskSpec` aus Task 1.
- Produces:
  - `data class LearnerProgress(parentMode, points, atomStats, mathStats, taskStats, unfinishedSession)`
  - `data class SessionSnapshot(lessonId, trainerIndex, roundIndex, pointsEarned, packId)`
  - `ProgressionEngine.recordTaskAttempt(progress, taskId, outcome): LearnerProgress`
  - `ProgressionEngine.mathKey(round: CountAddRound): String`
  - `enum class LessonState { Planned, Locked, Available, InProgress, Mastered }`
  - `object LessonGating { fun states(pack, progress): Map<String, LessonState>; fun stateOf(pack, progress, lessonId): LessonState; fun isPlayable(state): Boolean; fun nextPlayable(pack, progress): Lesson? }`

---

- [ ] **Step 1: Failing Test für Freischaltung schreiben**

Neu `app/src/test/java/app/abcvorschule/progress/LessonGatingTest.kt`:

```kotlin
package app.abcvorschule.progress

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.LessonStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonGatingTest {
    private val pack: ContentPack = ContentRepository.fromClasspath().load()
    private val first = pack.authoredLessons.first()

    private fun mastering(lessonId: String): LearnerProgress {
        val lesson = pack.lesson(lessonId)
        return LearnerProgress(
            taskStats = lesson.taskIds.associateWith { SkillStats(attempts = 1, correct = 1) },
        )
    }

    @Test
    fun firstLessonIsAvailableOnAFreshInstall() {
        assertEquals(
            LessonState.Available,
            LessonGating.stateOf(pack, LearnerProgress(), first.id),
        )
    }

    @Test
    fun plannedLessonsReportPlannedRegardlessOfProgress() {
        val planned = pack.lessons.first { it.status == LessonStatus.planned }
        assertEquals(
            LessonState.Planned,
            LessonGating.stateOf(pack, mastering(first.id), planned.id),
        )
        assertFalse(LessonGating.isPlayable(LessonState.Planned))
    }

    @Test
    fun touchedButUnfinishedLessonIsInProgress() {
        val progress = LearnerProgress(
            taskStats = mapOf(first.taskIds.first() to SkillStats(attempts = 2, correct = 0)),
        )
        assertEquals(LessonState.InProgress, LessonGating.stateOf(pack, progress, first.id))
    }

    @Test
    fun lessonIsMasteredOnlyWhenEveryTrainerWasSolvedOnce() {
        val partial = LearnerProgress(
            taskStats = first.taskIds.dropLast(1)
                .associateWith { SkillStats(attempts = 1, correct = 1) },
        )
        assertEquals(LessonState.InProgress, LessonGating.stateOf(pack, partial, first.id))
        assertEquals(LessonState.Mastered, LessonGating.stateOf(pack, mastering(first.id), first.id))
    }

    @Test
    fun nextAuthoredLessonUnlocksOnlyAfterThePreviousIsMastered() {
        val second = pack.authoredLessons.getOrNull(1) ?: return
        assertEquals(
            LessonState.Locked,
            LessonGating.stateOf(pack, LearnerProgress(), second.id),
        )
        assertEquals(
            LessonState.Available,
            LessonGating.stateOf(pack, mastering(first.id), second.id),
        )
    }

    @Test
    fun masteredLessonStaysPlayableForReview() {
        assertTrue(LessonGating.isPlayable(LessonState.Mastered))
        assertTrue(LessonGating.isPlayable(LessonState.Available))
        assertTrue(LessonGating.isPlayable(LessonState.InProgress))
        assertFalse(LessonGating.isPlayable(LessonState.Locked))
    }

    @Test
    fun nextPlayableIsTheFirstUnmasteredAuthoredLesson() {
        assertEquals(first.id, LessonGating.nextPlayable(pack, LearnerProgress())?.id)
        val second = pack.authoredLessons.getOrNull(1)
        if (second != null) {
            assertEquals(second.id, LessonGating.nextPlayable(pack, mastering(first.id))?.id)
        }
    }

    @Test
    fun statesCoverEveryLesson() {
        val states = LessonGating.states(pack, LearnerProgress())
        assertEquals(pack.lessons.size, states.size)
    }
}
```

- [ ] **Step 2: Failing Test für Engine v2 schreiben**

Ersetze `app/src/test/java/app/abcvorschule/progress/ProgressionEngineTest.kt` vollständig:

```kotlin
package app.abcvorschule.progress

import app.abcvorschule.content.CountAddRound
import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionEngineTest {
    private fun repeatOutcome(
        start: LearnerProgress,
        atomId: String,
        outcome: AttemptOutcome,
        times: Int,
    ): LearnerProgress {
        var progress = start
        repeat(times) { progress = ProgressionEngine.recordAtomAttempt(progress, atomId, outcome) }
        return progress
    }

    @Test
    fun autoUpgradesOneAtomWithoutTouchingAnother() {
        // AE2 equivalent: per-atom scaffolds, not one global flag.
        val progress = repeatOutcome(LearnerProgress(), "haus", AttemptOutcome.Correct, 3)
        assertEquals(ScaffoldLevel.Advanced, ProgressionEngine.scaffoldForAtom(progress, "haus"))
        assertEquals(ScaffoldLevel.Beginner, ProgressionEngine.scaffoldForAtom(progress, "mama"))
    }

    @Test
    fun firstTimeAutoDefaultsToBeginner() {
        assertEquals(
            ScaffoldLevel.Beginner,
            ProgressionEngine.scaffoldForAtom(LearnerProgress(), "unseen"),
        )
    }

    @Test
    fun forcedParentModeFreezesAutoStreaks() {
        // AE3: forced Advanced ignores a miss streak until Auto returns.
        val forced = LearnerProgress(parentMode = ParentMode.Advanced)
        val missed = repeatOutcome(forced, "haus", AttemptOutcome.Miss, 5)
        assertEquals(ScaffoldLevel.Advanced, ProgressionEngine.scaffoldForAtom(missed, "haus"))
        assertEquals(0, missed.atomStats.getValue("haus").consecutiveMiss)

        // AE8: back on Auto, progression resumes from the stored stats.
        val backOnAuto = missed.copy(parentMode = ParentMode.Auto)
        assertEquals(ScaffoldLevel.Beginner, ProgressionEngine.scaffoldForAtom(backOnAuto, "haus"))
    }

    @Test
    fun resolveCountsTowardDownshiftButNeverTowardMastery() {
        // AE5: resolve is a miss for the streak, never a success.
        val advanced = repeatOutcome(LearnerProgress(), "haus", AttemptOutcome.Correct, 3)
        val resolved = repeatOutcome(advanced, "haus", AttemptOutcome.Resolve, 3)
        val stats = resolved.atomStats.getValue("haus")
        assertEquals(3, stats.correct)
        assertEquals(3, stats.resolves)
        assertEquals(0, stats.consecutiveCorrect)
        assertEquals(ScaffoldLevel.Beginner, ProgressionEngine.scaffoldForAtom(resolved, "haus"))
    }

    @Test
    fun taskStatsTrackTrainerCompletionSeparatelyFromAtoms() {
        val progress = ProgressionEngine.recordTaskAttempt(
            LearnerProgress(),
            "l01-t4",
            AttemptOutcome.Correct,
        )
        assertEquals(1, progress.taskStats.getValue("l01-t4").correct)
        assertEquals(emptyMap<String, SkillStats>(), progress.atomStats)
    }

    @Test
    fun mathKeyIsDerivedFromOperationBandAndOperands() {
        val round = CountAddRound(
            promptTts = "x",
            iconAtomId = "ameise",
            left = 2,
            right = 1,
            answer = 3,
        )
        assertEquals("add|easy|2+1", ProgressionEngine.mathKey(round))
        assertEquals(
            "add|hard|9+8",
            ProgressionEngine.mathKey(round.copy(left = 9, right = 8, answer = 17)),
        )
        assertEquals(
            "add|custom|2+1",
            ProgressionEngine.mathKey(round.copy(difficultyBand = "custom")),
        )
    }

    @Test
    fun pointsNeverGoNegative() {
        assertEquals(0, ProgressionEngine.awardPoints(LearnerProgress(), -5).points)
    }
}
```

- [ ] **Step 3: Tests laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL — `recordTaskAttempt`, `LessonGating`, `mathKey(CountAddRound)`, `taskStats` fehlen.

- [ ] **Step 4: `ProgressModels.kt` anpassen**

Ersetze `SessionSnapshot` und `LearnerProgress`; `ParentMode`, `ScaffoldLevel`, `SkillStats` und `AttemptOutcome` bleiben unverändert.

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

@Serializable
data class LearnerProgress(
    val parentMode: ParentMode = ParentMode.Auto,
    val points: Int = 0,
    /** Per-atom stats drive per-slot scaffolds. */
    val atomStats: Map<String, SkillStats> = emptyMap(),
    /** Per-fact stats drive the Rechnen scaffold (visual choices vs. number entry). */
    val mathStats: Map<String, SkillStats> = emptyMap(),
    /** Per-trainer stats drive lesson state on the path. */
    val taskStats: Map<String, SkillStats> = emptyMap(),
    val unfinishedSession: SessionSnapshot? = null,
)
```

`packIntroCompleted` entfällt. Alte DataStore-Payloads bleiben lesbar, weil `Json { ignoreUnknownKeys = true }` bereits gesetzt ist; der alte `unfinishedSession` mit `taskIds` fällt auf Defaults zurück und wird beim ersten Start durch `packId`-Vergleich verworfen.

- [ ] **Step 5: `ProgressionEngine.kt` anpassen**

Ersetze den `TaskTemplate`-Import und die `mathKey`-Überladung; der `apply`/`nextScaffold`-Kern bleibt unverändert.

```kotlin
package app.abcvorschule.progress

import app.abcvorschule.content.CountAddRound

object ProgressionEngine {
    const val UpThreshold = 3
    const val DownThreshold = 3

    fun scaffoldForAtom(progress: LearnerProgress, atomId: String): ScaffoldLevel =
        scaffoldFor(progress.parentMode, progress.atomStats[atomId])

    fun scaffoldForMath(progress: LearnerProgress, mathKey: String): ScaffoldLevel =
        scaffoldFor(progress.parentMode, progress.mathStats[mathKey])

    private fun scaffoldFor(mode: ParentMode, stats: SkillStats?): ScaffoldLevel =
        when (mode) {
            ParentMode.Beginner -> ScaffoldLevel.Beginner
            ParentMode.Advanced -> ScaffoldLevel.Advanced
            ParentMode.Auto -> stats?.autoScaffold ?: ScaffoldLevel.Beginner
        }

    fun mathKey(operation: String, left: Int, right: Int, band: String?): String {
        val bandPart = band ?: bandFor(left + right)
        return "$operation|$bandPart|$left+$right"
    }

    fun mathKey(round: CountAddRound): String = mathKey(
        operation = round.operation,
        left = round.left,
        right = round.right,
        band = round.difficultyBand,
    )

    fun bandFor(sum: Int): String = when {
        sum <= 5 -> "easy"
        sum <= 10 -> "medium"
        else -> "hard"
    }

    fun recordAtomAttempt(
        progress: LearnerProgress,
        atomId: String,
        outcome: AttemptOutcome,
    ): LearnerProgress {
        val updated = apply(progress.atomStats[atomId] ?: SkillStats(), outcome, progress.parentMode)
        return progress.copy(atomStats = progress.atomStats + (atomId to updated))
    }

    fun recordMathAttempt(
        progress: LearnerProgress,
        mathKey: String,
        outcome: AttemptOutcome,
    ): LearnerProgress {
        val updated = apply(progress.mathStats[mathKey] ?: SkillStats(), outcome, progress.parentMode)
        return progress.copy(mathStats = progress.mathStats + (mathKey to updated))
    }

    /** Trainer-level tally; feeds LessonGating, never scaffold selection. */
    fun recordTaskAttempt(
        progress: LearnerProgress,
        taskId: String,
        outcome: AttemptOutcome,
    ): LearnerProgress {
        val updated = apply(progress.taskStats[taskId] ?: SkillStats(), outcome, progress.parentMode)
        return progress.copy(taskStats = progress.taskStats + (taskId to updated))
    }

    fun awardPoints(progress: LearnerProgress, amount: Int): LearnerProgress =
        progress.copy(points = progress.points + amount.coerceAtLeast(0))

    fun masteryScore(attempts: Int, correct: Int): Double {
        if (attempts == 0) return 0.0
        return correct.toDouble() / attempts.toDouble()
    }

    fun masteryScore(stats: SkillStats): Double = masteryScore(stats.attempts, stats.correct)

    // apply(...) and nextScaffold(...) stay exactly as they are today.
}
```

- [ ] **Step 6: `LessonGating.kt` anlegen**

```kotlin
package app.abcvorschule.progress

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Lesson
import app.abcvorschule.content.LessonStatus

enum class LessonState {
    /** Declared in the Fibel order, content not authored yet. */
    Planned,

    /** Previous lesson not mastered yet. */
    Locked,

    /** Unlocked, never attempted. */
    Available,

    /** Attempted, at least one trainer still unsolved. */
    InProgress,

    /** Every trainer solved at least once. Stays replayable for review. */
    Mastered,
}

/**
 * Lesson unlocking derived entirely from stored task stats — no extra persistence.
 * An authored lesson opens once the previous authored lesson is mastered.
 */
object LessonGating {
    fun isPlayable(state: LessonState): Boolean =
        state == LessonState.Available || state == LessonState.InProgress ||
            state == LessonState.Mastered

    fun isMastered(lesson: Lesson, progress: LearnerProgress): Boolean =
        lesson.taskIds.isNotEmpty() &&
            lesson.taskIds.all { (progress.taskStats[it]?.correct ?: 0) > 0 }

    fun isTouched(lesson: Lesson, progress: LearnerProgress): Boolean =
        lesson.taskIds.any { (progress.taskStats[it]?.attempts ?: 0) > 0 }

    fun stateOf(pack: ContentPack, progress: LearnerProgress, lessonId: String): LessonState =
        states(pack, progress).getValue(lessonId)

    fun states(pack: ContentPack, progress: LearnerProgress): Map<String, LessonState> {
        var previousMastered = true
        return pack.lessons.associate { lesson ->
            val state = when {
                lesson.status == LessonStatus.planned -> LessonState.Planned
                !previousMastered -> LessonState.Locked
                isMastered(lesson, progress) -> LessonState.Mastered
                isTouched(lesson, progress) -> LessonState.InProgress
                else -> LessonState.Available
            }
            if (lesson.status == LessonStatus.authored) {
                previousMastered = state == LessonState.Mastered
            }
            lesson.id to state
        }
    }

    /** The lesson the path should highlight: first authored lesson not yet mastered. */
    fun nextPlayable(pack: ContentPack, progress: LearnerProgress): Lesson? {
        val states = states(pack, progress)
        return pack.authoredLessons.firstOrNull {
            states[it.id] == LessonState.Available || states[it.id] == LessonState.InProgress
        } ?: pack.authoredLessons.lastOrNull { isPlayable(states.getValue(it.id)) }
    }
}
```

- [ ] **Step 7: `ProgressRepository.kt` anpassen**

Entferne `markPackIntroCompleted()`; alles andere bleibt. Der DataStore-Key bleibt `learner_progress_v1`, weil das Schema rückwärtskompatibel dekodiert.

```kotlin
    suspend fun setParentMode(mode: ParentMode): LearnerProgress =
        update { it.copy(parentMode = mode) }

    suspend fun saveSession(snapshot: SessionSnapshot?): LearnerProgress =
        update { it.copy(unfinishedSession = snapshot) }
```

- [ ] **Step 8: Zwischenstand festhalten (kein Commit)**

Das Modul kompiliert weiterhin nicht (Session/UI referenzieren gelöschte Typen) — Auflösung in Task 3.

```bash
git add app/src/main/java/app/abcvorschule/progress app/src/test/java/app/abcvorschule/progress
git status --short
```

---

## Task 3: Lektions-Session, Trainer-Host und Abbau der alten Trainer

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/session/SessionModels.kt`
- Modify: `app/src/main/java/app/abcvorschule/session/SessionViewModel.kt`
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/MathHinting.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Delete: `app/src/main/java/app/abcvorschule/session/SessionScheduler.kt`, `session/DistractorPicker.kt`, `ui/exercise/ReadingExercise.kt`, `ui/exercise/SpeechExercise.kt`, `ui/exercise/DragSlotBoard.kt`, `ui/exercise/ScaffoldMapping.kt`
- Delete: `app/src/test/java/app/abcvorschule/session/SessionSchedulerTest.kt`, `session/DistractorPickerTest.kt`, `ui/exercise/ScaffoldMappingTest.kt`
- Test: `app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt` (neu)

**Interfaces:**
- Consumes: alles aus Task 1 und 2.
- Produces:
  - `sealed interface AppScreen { Path; Practice; RewardSummary }` (`Pause` entfällt — Back auf der Practice-Ebene führt jetzt zurück zum Pfad)
  - `data class ScheduledTrainer(spec: TaskSpec, scaffolds: Map<String, ScaffoldLevel>, mathScaffolds: Map<String, ScaffoldLevel>)` — `mathScaffolds` is keyed by `ProgressionEngine.mathKey(round)`, one entry per `CountAddRound`, so each arithmetic fact keeps its own scaffold
  - `data class SessionUiState(screen, lessonId, trainers, trainerIndex, roundIndex, points, sessionPoints, ready, showDifficultySheet, speakCue, successPhase, successSpeakText, error)` mit `current`, `currentRound`, `trainerProgressLabel`, `roundProgressLabel`, `canGoPrevious`, `canGoNext`
  - `SessionViewModel`: `openLesson(lessonId)`, `backToPath()`, `submitRoundResult(correct, resolved, atomIds)`, `submitMathResult(distance, resolved, correct)`, `scaffoldFor(atomId)`, `lessonStates()`, `pathLessons()`
  - `object SessionProgression { fun next(trainerIndex, roundIndex, roundCounts): SessionStep? }` — reine Fortschrittsrechnung
  - `@Composable fun TrainerHost(...)`

---

- [ ] **Step 1: Failing Test für die Session-Fortschrittsrechnung schreiben**

Neu `app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt`:

```kotlin
package app.abcvorschule.session

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.roundCount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LessonSessionTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun lessonSessionIsTheSixTrainersInAuthoredOrder() {
        val lesson = pack.authoredLessons.first()
        assertEquals(lesson.taskIds, pack.tasksOf(lesson).map { it.id })
        assertEquals(6, pack.tasksOf(lesson).size)
    }

    @Test
    fun progressionWalksEveryRoundOfEveryTrainerInOrder() {
        val counts = listOf(3, 2, 1, 1, 1, 2)
        val visited = mutableListOf<Pair<Int, Int>>()
        var step: SessionStep? = SessionStep(0, 0)
        while (step != null) {
            visited += step.trainerIndex to step.roundIndex
            step = SessionProgression.next(step.trainerIndex, step.roundIndex, counts)
        }
        assertEquals(counts.sum(), visited.size)
        assertEquals(0 to 0, visited.first())
        assertEquals(0 to 1, visited[1])
        assertEquals(0 to 2, visited[2])
        assertEquals(1 to 0, visited[3])
        assertEquals(5 to 1, visited.last())
    }

    @Test
    fun progressionEndsAfterTheLastRoundOfTheLastTrainer() {
        assertNull(SessionProgression.next(5, 1, listOf(1, 1, 1, 1, 1, 2)))
    }

    @Test
    fun progressionSkipsEmptyTrainers() {
        // Defensive: a trainer with zero rounds must not stall the session.
        assertEquals(SessionStep(2, 0), SessionProgression.next(0, 0, listOf(1, 0, 1)))
    }

    @Test
    fun roundCountsMatchTheAuthoredPack() {
        val counts = pack.tasksOf(pack.authoredLessons.first()).map { it.roundCount }
        assertEquals(6, counts.size)
        assertEquals(emptyList<Int>(), counts.filter { it <= 0 })
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:compileDebugUnitTestKotlin`
Expected: FAIL — `SessionStep`/`SessionProgression` fehlen, und die alten Dateien referenzieren gelöschte Typen.

- [ ] **Step 3: Alte Trainer und den Mix-Scheduler löschen**

```bash
git rm app/src/main/java/app/abcvorschule/session/SessionScheduler.kt \
       app/src/main/java/app/abcvorschule/session/DistractorPicker.kt \
       app/src/main/java/app/abcvorschule/ui/exercise/ReadingExercise.kt \
       app/src/main/java/app/abcvorschule/ui/exercise/SpeechExercise.kt \
       app/src/main/java/app/abcvorschule/ui/exercise/DragSlotBoard.kt \
       app/src/main/java/app/abcvorschule/ui/exercise/ScaffoldMapping.kt \
       app/src/test/java/app/abcvorschule/session/SessionSchedulerTest.kt \
       app/src/test/java/app/abcvorschule/session/DistractorPickerTest.kt \
       app/src/test/java/app/abcvorschule/ui/exercise/ScaffoldMappingTest.kt
```

- [ ] **Step 4: `SessionModels.kt` ersetzen**

```kotlin
package app.abcvorschule.session

import app.abcvorschule.content.TaskSpec
import app.abcvorschule.content.TrainerRound
import app.abcvorschule.content.round
import app.abcvorschule.progress.ScaffoldLevel

sealed interface AppScreen {
    /** Fibel path — the app's entry screen. */
    data object Path : AppScreen
    data object Practice : AppScreen
    data object RewardSummary : AppScreen
}

enum class SuccessPhase {
    Idle,
    SpeakAnswer,
    ShowBurst,
}

/** Position inside a lesson session: which trainer, which round. */
data class SessionStep(val trainerIndex: Int, val roundIndex: Int)

/** Pure walk through a lesson's trainers and their rounds. */
object SessionProgression {
    fun next(trainerIndex: Int, roundIndex: Int, roundCounts: List<Int>): SessionStep? {
        val current = roundCounts.getOrNull(trainerIndex) ?: return null
        if (roundIndex + 1 < current) return SessionStep(trainerIndex, roundIndex + 1)
        var t = trainerIndex + 1
        while (t < roundCounts.size) {
            if (roundCounts[t] > 0) return SessionStep(t, 0)
            t++
        }
        return null
    }

    fun previous(trainerIndex: Int, roundIndex: Int, roundCounts: List<Int>): SessionStep? {
        if (roundIndex > 0) return SessionStep(trainerIndex, roundIndex - 1)
        var t = trainerIndex - 1
        while (t >= 0) {
            if (roundCounts[t] > 0) return SessionStep(t, roundCounts[t] - 1)
            t--
        }
        return null
    }
}

data class ScheduledTrainer(
    val spec: TaskSpec,
    /** Per-atom scaffold for slot-based trainers (word_build, sentence_order). */
    val scaffolds: Map<String, ScaffoldLevel> = emptyMap(),
    /**
     * Rechnen scaffold per arithmetic fact, keyed by [ProgressionEngine.mathKey].
     * A count_add trainer holds several facts with independent mastery, so one
     * scaffold for the whole trainer would drive the wrong UI for later rounds.
     * Cached at schedule time so a mid-round parent-mode change only takes
     * effect from the next trainer on (F7).
     */
    val mathScaffolds: Map<String, ScaffoldLevel> = emptyMap(),
)

data class SessionUiState(
    val screen: AppScreen = AppScreen.Path,
    val lessonId: String? = null,
    val trainers: List<ScheduledTrainer> = emptyList(),
    val trainerIndex: Int = 0,
    val roundIndex: Int = 0,
    val points: Int = 0,
    val sessionPoints: Int = 0,
    val ready: Boolean = false,
    val showDifficultySheet: Boolean = false,
    /** Spoken-only miss/hint text — never rendered as chrome. */
    val speakCue: String? = null,
    val successPhase: SuccessPhase = SuccessPhase.Idle,
    val successSpeakText: String? = null,
    val error: String? = null,
) {
    val current: ScheduledTrainer? = trainers.getOrNull(trainerIndex)
    val currentRound: TrainerRound? = current?.spec?.round(roundIndex)
    private val roundCounts: List<Int> = trainers.map { it.spec.rounds.size }

    /** "3/6" — which of the six trainers the child is on. */
    val trainerProgressLabel: String =
        if (trainers.isEmpty()) "" else "${trainerIndex + 1}/${trainers.size}"

    /** Rounds inside the current trainer, for the sub-progress dots. */
    val roundCount: Int = roundCounts.getOrElse(trainerIndex) { 0 }

    val canGoPrevious: Boolean =
        SessionProgression.previous(trainerIndex, roundIndex, roundCounts) != null
    val canGoNext: Boolean =
        SessionProgression.next(trainerIndex, roundIndex, roundCounts) != null
}
```

- [ ] **Step 5: `SessionViewModel.kt` ersetzen**

```kotlin
package app.abcvorschule.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.content.CountAddSpec
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.Lesson
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.content.TaskSpec
import app.abcvorschule.content.WordBuildRound
import app.abcvorschule.content.rounds
import app.abcvorschule.content.scoredAtomIds
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

    init {
        viewModelScope.launch { bootstrap() }
    }

    private suspend fun bootstrap() {
        runCatching {
            pack = withContext(Dispatchers.IO) { contentRepository.load() }
            progress = progressRepository.current()
            val snapshot = progress.unfinishedSession
            val resumable = snapshot != null &&
                snapshot.packId == pack.manifest.packId &&
                pack.lessons.any { it.id == snapshot.lessonId }
            if (resumable) {
                openLesson(snapshot!!.lessonId, snapshot.trainerIndex, snapshot.roundIndex, snapshot.pointsEarned)
            } else {
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

    /** Spoken cue for a locked/planned node — a tap must always produce feedback. */
    fun lockedLessonCue(): String = "Das üben wir später."

    fun openLesson(
        lessonId: String,
        trainerIndex: Int = 0,
        roundIndex: Int = 0,
        sessionPoints: Int = 0,
    ) {
        viewModelScope.launch {
            progress = progressRepository.current()
            val lesson = pack.lessons.firstOrNull { it.id == lessonId } ?: return@launch
            if (!LessonGating.isPlayable(LessonGating.stateOf(pack, progress, lessonId))) return@launch
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
        }
    }

    fun backToPath() {
        viewModelScope.launch {
            progressRepository.saveSession(null)
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
        return if (round is CountAddRound) "${round.left} + ${round.right} = ?" else round.promptTts
    }

    fun successSpeakTextForCurrent(): String = when (val round = _ui.value.currentRound) {
        // Speak the counted objects, not a bare number: "zwei Ameisen".
        is CountAddRound -> round.spokenAnswer(pack.atoms[round.iconAtomId])
        is SyllableMergeRound -> round.resultDisplay
        is WordBuildRound -> pack.atoms[round.targetAtomId]?.display ?: round.promptTts
        is SentenceOrderRound -> pack.sentence(round.sentenceId).tts
        is LetterTraceRound -> round.rewardTts
        is SoundPositionRound -> pack.atoms[round.atomId]?.lemma ?: round.promptTts
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
                backToPath()
            }
            false
        }
        AppScreen.RewardSummary -> {
            backToPath()
            false
        }
    }

    fun continueAfterSummary() {
        backToPath()
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
                    speakCue = speakOverride ?: missCueForCurrent(),
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
```

- [ ] **Step 6: `TrainerHost.kt` mit Platzhaltern anlegen**

Die echten Trainer folgen in Task 6–11. Platzhalter halten den Build grün und machen die Session sofort durchspielbar.

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.content.TrainerRound
import app.abcvorschule.content.WordBuildRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.session.ScheduledTrainer
import app.abcvorschule.ui.components.AbcContinueButton

/** Callbacks every trainer reports through, so the ViewModel owns all sequencing. */
data class TrainerCallbacks(
    val onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    val onMathResult: (distance: Int?, resolved: Boolean, correct: Boolean) -> Unit,
    val onSpeak: (String) -> Unit,
    val onSpeakPrompt: () -> Unit,
)

/** Dispatches a scheduled trainer's current round to its screen. */
@Composable
fun TrainerHost(
    trainer: ScheduledTrainer,
    round: TrainerRound,
    pack: ContentPack,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    callbacks: TrainerCallbacks,
    modifier: Modifier = Modifier,
) {
    when (round) {
        is SoundPositionRound -> TrainerPlaceholder("Trainer 1", modifier, callbacks)
        is LetterTraceRound -> TrainerPlaceholder("Trainer 2", modifier, callbacks)
        is SyllableMergeRound -> TrainerPlaceholder("Trainer 3", modifier, callbacks)
        is WordBuildRound -> TrainerPlaceholder("Trainer 4", modifier, callbacks)
        is SentenceOrderRound -> TrainerPlaceholder("Trainer 5", modifier, callbacks)
        is CountAddRound -> MathExercise(
            trainer = trainer,
            round = round,
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
        else -> TrainerPlaceholder("Trainer", modifier, callbacks)
    }
}

/** Temporary scaffolding, replaced trainer-by-trainer in later tasks. */
@Composable
private fun TrainerPlaceholder(
    label: String,
    modifier: Modifier,
    callbacks: TrainerCallbacks,
) {
    ExerciseStage(
        modifier = modifier.fillMaxSize(),
        prompt = {
            Text(label, style = MaterialTheme.typography.headlineMedium)
        },
        answers = {
            AbcContinueButton(onClick = { callbacks.onResult(true, false, emptyList()) })
        },
    )
}
```

- [ ] **Step 7: `MathExercise.kt` und `VisualQuantityBoard.kt` auf die Runde umstellen**

`MathExercise.kt` vollständig:

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.unit.dp
import app.abcvorschule.content.CountAddRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.session.ScheduledTrainer
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.theme.SoftSand

/**
 * Trainer 6 — Rechnen. Pure quantity arithmetic: emoji groups and numerals only,
 * never words to read or build. Singular/plural lives in the spoken prompt.
 */
@Composable
fun MathExercise(
    trainer: ScheduledTrainer,
    round: CountAddRound,
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
    val roundKey = "${trainer.spec.id}#${round.left}+${round.right}"
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var locked by remember(roundKey) { mutableStateOf(false) }
    val usePad = MathHinting.usesNumberPad(scaffold)
    val choices = remember(roundKey) { MathHinting.threeChoices(round.answer).shuffled() }

    fun handleGuess(guess: Int) {
        if (locked) return
        if (guess == round.answer) {
            locked = true
            onResult(0, false, true)
        } else {
            onSpeak(guess.toString())
            misses += 1
            onResult(MathHinting.distance(round.answer, guess), false, false)
        }
    }

    fun resolve() {
        if (locked) return
        locked = true
        onResult(null, true, false)
    }

    if (usePad) {
        ExerciseStage(
            modifier = modifier.fillMaxSize(),
            prompt = {
                TaskPromptChrome(
                    title = null,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeakPrompt = onSpeakPrompt,
                )
                if (showSymbolPrompt) {
                    Text(
                        text = "${round.left} + ${round.right} = ?",
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuantityCluster(emoji = icon, count = round.left, emojiSizeSp = 40)
                    Text("+", style = MaterialTheme.typography.displayMedium, color = SoftSand)
                    QuantityCluster(emoji = icon, count = round.right, emojiSizeSp = 40)
                }
            },
            answers = {
                NumberPad(onSubmit = { handleGuess(it) })
                if (misses >= 2) {
                    AbcResolveButton(onClick = ::resolve)
                }
            },
        )
    } else {
        VisualQuantityBoard(
            emoji = icon,
            left = round.left,
            right = round.right,
            choices = choices,
            onChoose = { handleGuess(it) },
            missCount = misses,
            onResolve = ::resolve,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = onSpeakPrompt,
            modifier = modifier.fillMaxSize(),
        )
    }
}
```

`VisualQuantityBoard.kt` bleibt inhaltlich unverändert (Signatur passt schon).

In `MathHinting.kt` `usesNumberPad` ersetzen:

```kotlin
    /** Advanced = type the result; Beginner = pick from three labeled quantities. */
    fun usesNumberPad(scaffold: ScaffoldLevel): Boolean = scaffold == ScaffoldLevel.Advanced
```

und `import app.abcvorschule.progress.ScaffoldLevel` ergänzen. Passe `MathHintingTest.kt` an:

```kotlin
    @Test
    fun numberPadOnlyOnAdvancedScaffold() {
        assertTrue(MathHinting.usesNumberPad(ScaffoldLevel.Advanced))
        assertFalse(MathHinting.usesNumberPad(ScaffoldLevel.Beginner))
    }
```

(Der bestehende `usesNumberPad`-Test mit zwei Booleans wird durch diesen ersetzt; alle anderen Tests der Datei bleiben.)

- [ ] **Step 8: `TaskShell.kt` auf Pfad + Trainer-Host umstellen**

Ersetze `PracticeBody`s Domänen-`when` durch `TrainerHost`, die Chevrons auf `goPreviousRound`/`goNextRound`, das Progress-Label auf `trainerProgressLabel`, und ergänze den Pfad-Zweig. Der Pfad-Screen selbst kommt in Task 4 — hier zunächst ein `PathScreen`-Aufruf, der in Task 4 angelegt wird; bis dahin genügt dieser Zwischenstand:

```kotlin
            state.screen == AppScreen.Path -> {
                // Task 4 replaces this with PathScreen(...).
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val first = viewModel.highlightedLessonId()
                    AbcContinueButton(
                        onClick = { first?.let(viewModel::openLesson) },
                        label = "Start",
                        centered = true,
                        enabled = first != null,
                    )
                }
            }
```

Weitere Änderungen in `PracticeBody`:

```kotlin
    val task = state.current
    val round = state.currentRound
    // ... chrome unchanged (parent gate, points, chevrons, progress bar) ...
    AbcNavChevron(
        forward = false,
        enabled = state.canGoPrevious && state.successPhase == SuccessPhase.Idle,
        onClick = viewModel::goPreviousRound,
        contentDescription = stringResource(R.string.nav_back),
    )
    // ...
    AbcProgressBar(index = state.trainerIndex, total = state.trainers.size)
    Text(text = state.trainerProgressLabel, /* unchanged styling */)
    // ...
    if (task != null && round != null) {
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            TrainerHost(
                trainer = task,
                round = round,
                pack = pack,
                scaffoldFor = viewModel::scaffoldFor,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                callbacks = TrainerCallbacks(
                    onResult = viewModel::submitRoundResult,
                    onMathResult = viewModel::submitMathResult,
                    onSpeak = onSpeak,
                    onSpeakPrompt = speakPrompt,
                ),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
```

Der `AppScreen.Pause`-Zweig wird gelöscht; `LaunchedEffect(task?.template?.id, ...)` wird zu `LaunchedEffect(task?.spec?.id, state.roundIndex, ttsAvailable)`, damit jede Runde ihren Prompt spricht.

- [ ] **Step 9: `strings.xml` ergänzen**

`pause_resume` entfällt; A11y-Beschreibungen kommen dazu:

```xml
    <string name="nav_back">Zurück</string>
    <string name="nav_forward">Weiter</string>
    <string name="wagon_start">Anfang</string>
    <string name="wagon_middle">Mitte</string>
    <string name="wagon_end">Ende</string>
    <string name="lesson_locked">Noch gesperrt</string>
    <string name="lesson_available">Bereit</string>
    <string name="lesson_mastered">Geschafft</string>
    <string name="path_node">Lektion</string>
```

- [ ] **Step 10: Kompilieren und alle Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS — Content-, Progress-, Gating-, Session-, MathHinting- und QuantityGrouping-Tests grün.

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 11: Commit (Tasks 1–3 zusammen)**

```bash
git add -A
git commit -m "feat(content): schema v2 with per-trainer specs, lessons and lesson gating

Replace the mixed-domain task template with a polymorphic TaskSpec per trainer,
introduce lessons.json as the Fibel order, derive lesson unlocking from task
stats, and run sessions as a lesson's six trainers instead of a domain rotation.
Removes the reading/speech cloze trainers, the mix scheduler and the runtime
distractor picker; trainer screens land in follow-up commits.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 4: Pfad-Screen als App-Einstieg

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/path/PathGeometry.kt`
- Create: `app/src/main/java/app/abcvorschule/ui/path/PathScreen.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt` (Platzhalter aus Task 3 Step 8 durch `PathScreen` ersetzen)
- Test: `app/src/test/java/app/abcvorschule/ui/path/PathGeometryTest.kt`

**Interfaces:**
- Consumes: `Lesson`, `LessonStatus` (Task 1); `LessonState`, `LessonGating` (Task 2); `SessionViewModel.pathLessons()`, `lessonStates()`, `highlightedLessonId()`, `openLesson(String)`, `lockedLessonCue()` (Task 3).
- Produces:
  - `data class PathPoint(val x: Float, val y: Float)`
  - `object PathGeometry { const val DefaultSpacing: Float; const val DefaultMargin: Float; fun points(count: Int, width: Float, spacing: Float, margin: Float): List<PathPoint>; fun contentHeight(count: Int, spacing: Float, margin: Float): Float }`
  - `@Composable fun PathScreen(lessons: List<Lesson>, states: Map<String, LessonState>, highlightedLessonId: String?, points: Int, onOpenLesson: (String) -> Unit, onLockedTap: () -> Unit, onParentGateUnlocked: () -> Unit, modifier: Modifier)`

---

- [ ] **Step 1: Failing Test für die Pfad-Geometrie schreiben**

Neu `app/src/test/java/app/abcvorschule/ui/path/PathGeometryTest.kt`:

```kotlin
package app.abcvorschule.ui.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathGeometryTest {
    private val width = 1000f
    private val spacing = 140f
    private val margin = 96f

    private fun points(count: Int) = PathGeometry.points(count, width, spacing, margin)

    @Test
    fun emptyPathHasNoPoints() {
        assertEquals(emptyList<PathPoint>(), points(0))
    }

    @Test
    fun nodesAreStackedTopDownAtConstantSpacing() {
        val p = points(4)
        assertEquals(margin, p[0].y, 0.01f)
        assertEquals(margin + spacing, p[1].y, 0.01f)
        assertEquals(margin + 3 * spacing, p[3].y, 0.01f)
    }

    @Test
    fun curveStartsCenteredAndSwingsRightThenLeft() {
        val p = points(5)
        val center = width / 2f
        assertEquals(center, p[0].x, 0.01f)
        assertTrue("node 1 swings right", p[1].x > center)
        assertEquals(center, p[2].x, 0.01f)
        assertTrue("node 3 swings left", p[3].x < center)
        assertEquals(center, p[4].x, 0.01f)
    }

    @Test
    fun amplitudeStaysInsideTheMargins() {
        points(16).forEach {
            assertTrue("x=${it.x} left of margin", it.x >= margin - 0.01f)
            assertTrue("x=${it.x} right of margin", it.x <= width - margin + 0.01f)
        }
    }

    @Test
    fun narrowScreenCollapsesToAStraightLine() {
        val p = PathGeometry.points(4, width = 120f, spacing = spacing, margin = margin)
        assertEquals(p.map { it.x }.distinct().size, 1)
    }

    @Test
    fun contentHeightLeavesMarginAtBothEnds() {
        assertEquals(2 * margin, PathGeometry.contentHeight(1, spacing, margin), 0.01f)
        assertEquals(
            2 * margin + 15 * spacing,
            PathGeometry.contentHeight(16, spacing, margin),
            0.01f,
        )
        assertEquals(0f, PathGeometry.contentHeight(0, spacing, margin), 0.01f)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.path.*"`
Expected: FAIL — `PathGeometry` existiert nicht.

- [ ] **Step 3: `PathGeometry.kt` anlegen**

```kotlin
package app.abcvorschule.ui.path

import kotlin.math.PI
import kotlin.math.sin

/** Node center in path-content pixels, y growing downwards. */
data class PathPoint(val x: Float, val y: Float)

/**
 * Calm winding S-curve: nodes stack top-down at constant spacing while x swings
 * center → right → center → left with a period of four nodes. Pure math so the
 * layout is unit-testable without Compose.
 */
object PathGeometry {
    const val DefaultSpacing = 140f
    const val DefaultMargin = 96f

    fun points(
        count: Int,
        width: Float,
        spacing: Float = DefaultSpacing,
        margin: Float = DefaultMargin,
    ): List<PathPoint> {
        if (count <= 0) return emptyList()
        val center = width / 2f
        val amplitude = (center - margin).coerceAtLeast(0f)
        return (0 until count).map { index ->
            PathPoint(
                x = center + amplitude * sin(index * PI / 2.0).toFloat(),
                y = margin + index * spacing,
            )
        }
    }

    fun contentHeight(
        count: Int,
        spacing: Float = DefaultSpacing,
        margin: Float = DefaultMargin,
    ): Float = if (count <= 0) 0f else 2 * margin + (count - 1) * spacing
}
```

- [ ] **Step 4: Test laufen lassen und grün bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.path.*"`
Expected: PASS

- [ ] **Step 5: `PathScreen.kt` anlegen**

```kotlin
package app.abcvorschule.ui.path

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.abcvorschule.R
import app.abcvorschule.content.Lesson
import app.abcvorschule.progress.LessonGating
import app.abcvorschule.progress.LessonState
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.shell.ParentGateButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.NightPanel
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky

private val NodeSize = 92.dp

/**
 * Fibel path: the app's start screen. Node labels stay minimal (a letter, a
 * syllable, a grapheme) — never an instruction the child would have to read.
 */
@Composable
fun PathScreen(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    highlightedLessonId: String?,
    points: Int,
    onOpenLesson: (String) -> Unit,
    onLockedTap: () -> Unit,
    onParentGateUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AbcDimens.screenHorizontal, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ParentGateButton(onUnlocked = onParentGateUnlocked)
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconStar(tint = MaterialTheme.colorScheme.primary, size = 22.dp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "$points",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.size(48.dp))
        }

        val density = LocalDensity.current
        val spacingPx = with(density) { PathGeometry.DefaultSpacing.dp.toPx() }
        val marginPx = with(density) { PathGeometry.DefaultMargin.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .testTag("path_scroll"),
        ) {
            var widthPx = 0f
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(
                        with(density) {
                            PathGeometry.contentHeight(lessons.size, spacingPx, marginPx).toDp()
                        },
                    ),
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    widthPx = size.width
                    val pts = PathGeometry.points(lessons.size, size.width, spacingPx, marginPx)
                    pts.zipWithNext { a, b ->
                        drawLine(
                            color = MutedText.copy(alpha = 0.22f),
                            start = Offset(a.x, a.y),
                            end = Offset(b.x, b.y),
                            strokeWidth = 10f,
                            cap = StrokeCap.Round,
                        )
                    }
                }
                BoxWithNodes(
                    lessons = lessons,
                    states = states,
                    highlightedLessonId = highlightedLessonId,
                    spacingPx = spacingPx,
                    marginPx = marginPx,
                    onOpenLesson = onOpenLesson,
                    onLockedTap = onLockedTap,
                )
            }
        }
    }
}

@Composable
private fun BoxWithNodes(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    highlightedLessonId: String?,
    spacingPx: Float,
    marginPx: Float,
    onOpenLesson: (String) -> Unit,
    onLockedTap: () -> Unit,
) {
    val density = LocalDensity.current
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
        val widthPx = with(density) { maxWidth.toPx() }
        val nodeHalf = with(density) { (NodeSize / 2).toPx() }
        val pts = PathGeometry.points(lessons.size, widthPx, spacingPx, marginPx)
        lessons.forEachIndexed { index, lesson ->
            val point = pts.getOrNull(index) ?: return@forEachIndexed
            val state = states[lesson.id] ?: LessonState.Locked
            PathNode(
                label = lesson.nodeLabel,
                state = state,
                highlighted = lesson.id == highlightedLessonId,
                modifier = Modifier.offset(
                    x = with(density) { (point.x - nodeHalf).toDp() },
                    y = with(density) { (point.y - nodeHalf).toDp() },
                ),
                onClick = {
                    if (LessonGating.isPlayable(state)) onOpenLesson(lesson.id) else onLockedTap()
                },
            )
        }
    }
}

@Composable
private fun PathNode(
    label: String,
    state: LessonState,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playable = LessonGating.isPlayable(state)
    val fill = when (state) {
        LessonState.Mastered -> SoftMint
        LessonState.Available, LessonState.InProgress -> NightElevated
        LessonState.Locked, LessonState.Planned -> NightPanel
    }
    val ring: Color = when (state) {
        LessonState.Mastered -> SoftMint
        LessonState.Available -> SoftMint
        LessonState.InProgress -> SoftSky
        LessonState.Locked, LessonState.Planned -> MutedText.copy(alpha = 0.28f)
    }
    val labelColor = when (state) {
        LessonState.Mastered -> NightInk
        LessonState.Available, LessonState.InProgress -> SoftSand
        LessonState.Locked, LessonState.Planned -> MutedText.copy(alpha = 0.45f)
    }
    val ringAlpha = if (highlighted) {
        val transition = rememberInfiniteTransition(label = "node_pulse")
        val pulse by transition.animateFloat(
            initialValue = 0.45f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "node_pulse_alpha",
        )
        pulse
    } else {
        1f
    }
    val stateDesc = stringResource(
        when (state) {
            LessonState.Mastered -> R.string.lesson_mastered
            LessonState.Available, LessonState.InProgress -> R.string.lesson_available
            LessonState.Locked, LessonState.Planned -> R.string.lesson_locked
        },
    )
    val nodeDesc = stringResource(R.string.path_node)

    Box(
        modifier = modifier
            .size(NodeSize)
            .background(fill, CircleShape)
            .border(width = 4.dp, color = ring.copy(alpha = ring.alpha * ringAlpha), shape = CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$nodeDesc $label, $stateDesc" }
            .testTag("path_node_$label"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = labelColor,
                modifier = if (playable) Modifier else Modifier.alpha(0.75f),
            )
            if (state == LessonState.Mastered) {
                IconStar(tint = NightInk, size = 18.dp)
            }
        }
    }
}
```

Hinweis für den Implementierer: entferne beim Ausschreiben jeden Import, den die fertige Datei nicht mehr braucht (u. a. `Stroke` und `Dp`, falls die Canvas-Signatur sie nicht nutzt). Keine ungenutzten Imports und keine Alias-Platzhalter committen.

- [ ] **Step 6: `TaskShell.kt` verdrahten**

Ersetze den Platzhalter-Zweig aus Task 3 Step 8:

```kotlin
            state.screen == AppScreen.Path -> {
                PathScreen(
                    lessons = viewModel.pathLessons(),
                    states = viewModel.lessonStates(),
                    highlightedLessonId = viewModel.highlightedLessonId(),
                    points = state.points,
                    onOpenLesson = { viewModel.openLesson(it) },
                    onLockedTap = { if (ttsAvailable) onSpeak(viewModel.lockedLessonCue()) },
                    onParentGateUnlocked = viewModel::openDifficultySheet,
                    modifier = Modifier.fillMaxSize(),
                )
            }
```

- [ ] **Step 7: Build und Tests**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: beides grün.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/path app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt app/src/test/java/app/abcvorschule/ui/path
git commit -m "feat(path): make the Fibel lesson path the app entry screen

Nodes follow the curriculum order with derived Locked/Available/InProgress/
Mastered states and minimal letter labels; tapping a locked node speaks a cue
instead of doing nothing.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 5: Geteilte Drag-Primitive und Platzierungslogik

Vier der sechs Trainer (1, 3, 4, 5) sind Drag-&-Drop mit Tap-Alternative. Diese Task zieht die Mechanik aus dem gelöschten `DragSlotBoard` als wiederverwendbare, testbare Primitive heraus.

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/drag/DragHitTest.kt`
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/drag/DragField.kt`
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/OrderedPlacement.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/drag/DragHitTestTest.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/OrderedPlacementTest.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/drag/DragFieldStateTest.kt`

**Interfaces:**
- Consumes: nichts aus dem bestehenden Code — diese Primitive sind eigenständig und werden erst von Task 6/8/9/10 konsumiert.
- Produces:
  - `data class DragRect(left: Float, top: Float, right: Float, bottom: Float)` mit `width`, `height`, `area`
  - `object DragHitTest { const val MinCommitPx = 24f; fun overlapArea(a, b): Float; fun bestZone(card: DragRect, zones: Map<String, DragRect>): String?; fun shouldCommit(dragDistancePx: Float): Boolean }`
  - `class DragFieldState` mit `selectedKey`, `draggingKey`, `dragOffset`, `putCard(key, Rect)`, `putZone(key, Rect)`, `removeCard(key)`, `removeZone(key)`, `endDrag(key): String?`, `select(key)`, `reset()`
  - `@Composable fun rememberDragFieldState(vararg keys: Any?): DragFieldState`
  - `@Composable fun DragCard(state: DragFieldState, key: String, onTap: () -> Unit, onDropped: (zoneKey: String?) -> Unit, modifier: Modifier, content: @Composable BoxScope.() -> Unit)`
  - `@Composable fun DropZone(state: DragFieldState, key: String, onTap: () -> Unit, modifier: Modifier, content: @Composable BoxScope.() -> Unit)`
  - `object OrderedPlacement { fun isCorrectPlacement(index: Int, display: String, solution: List<String>): Boolean; fun isSolved(placed: Map<Int, String>, solution: List<String>): Boolean; fun nextEmptyIndex(placed: Map<Int, String>, size: Int): Int? }`

---

- [ ] **Step 1: Failing Test für das Hit-Testing schreiben**

Neu `app/src/test/java/app/abcvorschule/ui/exercise/drag/DragHitTestTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise.drag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DragHitTestTest {
    private fun rect(l: Float, t: Float, r: Float, b: Float) = DragRect(l, t, r, b)

    @Test
    fun disjointRectsDoNotOverlap() {
        assertEquals(0f, DragHitTest.overlapArea(rect(0f, 0f, 10f, 10f), rect(20f, 20f, 30f, 30f)), 0.01f)
    }

    @Test
    fun overlapAreaIsTheIntersectionArea() {
        val a = rect(0f, 0f, 10f, 10f)
        val b = rect(5f, 5f, 15f, 15f)
        assertEquals(25f, DragHitTest.overlapArea(a, b), 0.01f)
    }

    @Test
    fun droppingNowhereHitsNoZone() {
        val zones = mapOf("z1" to rect(100f, 100f, 150f, 150f))
        assertNull(DragHitTest.bestZone(rect(0f, 0f, 40f, 40f), zones))
    }

    @Test
    fun straddlingTwoZonesPicksTheLargerOverlap() {
        // A wrong slot must still be a real miss, so we resolve to exactly one zone.
        val zones = mapOf(
            "left" to rect(0f, 0f, 100f, 100f),
            "right" to rect(100f, 0f, 200f, 100f),
        )
        assertEquals("right", DragHitTest.bestZone(rect(70f, 10f, 170f, 90f), zones))
        assertEquals("left", DragHitTest.bestZone(rect(30f, 10f, 130f, 90f), zones))
    }

    @Test
    fun tinyDragsDoNotCommit() {
        assertFalse(DragHitTest.shouldCommit(0f))
        assertFalse(DragHitTest.shouldCommit(DragHitTest.MinCommitPx))
        assertTrue(DragHitTest.shouldCommit(DragHitTest.MinCommitPx + 1f))
    }

    @Test
    fun emptyZoneMapIsSafe() {
        assertNull(DragHitTest.bestZone(rect(0f, 0f, 10f, 10f), emptyMap()))
    }
}
```

- [ ] **Step 2: Failing Test für die Platzierungslogik schreiben**

Neu `app/src/test/java/app/abcvorschule/ui/exercise/OrderedPlacementTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderedPlacementTest {
    private val mama = listOf("Ma", "ma")
    private val sentence = listOf("Oma", "ist", "da")

    @Test
    fun blockIsCorrectOnlyAtItsOwnIndex() {
        assertTrue(OrderedPlacement.isCorrectPlacement(0, "Ma", mama))
        assertFalse(OrderedPlacement.isCorrectPlacement(1, "Ma", mama))
        assertTrue(OrderedPlacement.isCorrectPlacement(1, "ma", mama))
    }

    @Test
    fun indexOutsideTheSolutionIsNeverCorrect() {
        assertFalse(OrderedPlacement.isCorrectPlacement(5, "Ma", mama))
        assertFalse(OrderedPlacement.isCorrectPlacement(-1, "Ma", mama))
    }

    @Test
    fun repeatedBlocksAreAcceptedAtEveryMatchingIndex() {
        // Mama needs "ma" twice; a repeated syllable must not confuse the check.
        val placed = mapOf(0 to "Ma", 1 to "ma")
        assertTrue(OrderedPlacement.isSolved(placed, mama))
    }

    @Test
    fun partialPlacementIsNotSolved() {
        assertFalse(OrderedPlacement.isSolved(mapOf(0 to "Oma"), sentence))
        assertFalse(OrderedPlacement.isSolved(mapOf(0 to "Oma", 2 to "da"), sentence))
        assertTrue(OrderedPlacement.isSolved(mapOf(0 to "Oma", 1 to "ist", 2 to "da"), sentence))
    }

    @Test
    fun wrongContentIsNotSolvedEvenWhenAllSlotsAreFull() {
        assertFalse(OrderedPlacement.isSolved(mapOf(0 to "ma", 1 to "Ma"), mama))
    }

    @Test
    fun nextEmptyIndexWalksLeftToRight() {
        assertEquals(0, OrderedPlacement.nextEmptyIndex(emptyMap(), 3))
        assertEquals(1, OrderedPlacement.nextEmptyIndex(mapOf(0 to "Oma"), 3))
        assertEquals(2, OrderedPlacement.nextEmptyIndex(mapOf(0 to "Oma", 1 to "ist"), 3))
        assertNull(OrderedPlacement.nextEmptyIndex(mapOf(0 to "a", 1 to "b", 2 to "c"), 3))
    }
}
```

- [ ] **Step 3: Tests laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.*"`
Expected: FAIL — `DragHitTest`, `DragRect` und `OrderedPlacement` existieren nicht.

- [ ] **Step 4: `DragHitTest.kt` anlegen**

```kotlin
package app.abcvorschule.ui.exercise.drag

/** Axis-aligned rectangle in root coordinates. Compose-free so it stays unit-testable. */
data class DragRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = (right - left).coerceAtLeast(0f)
    val height: Float get() = (bottom - top).coerceAtLeast(0f)
    val area: Float get() = width * height
}

/**
 * Drop resolution for preschool drag & drop: a card commits only when it actually
 * lands on a zone, and a card straddling two zones resolves to the one it covers
 * most — so a wrong slot is a real miss and a drop into empty space snaps back.
 */
object DragHitTest {
    /** Below this travel the gesture was a tap, not a drag. */
    const val MinCommitPx = 24f

    fun overlapArea(a: DragRect, b: DragRect): Float {
        val w = (minOf(a.right, b.right) - maxOf(a.left, b.left)).coerceAtLeast(0f)
        val h = (minOf(a.bottom, b.bottom) - maxOf(a.top, b.top)).coerceAtLeast(0f)
        return w * h
    }

    fun bestZone(card: DragRect, zones: Map<String, DragRect>): String? = zones
        .mapValues { (_, zone) -> overlapArea(card, zone) }
        .filterValues { it > 0f }
        .maxByOrNull { it.value }
        ?.key

    fun shouldCommit(dragDistancePx: Float): Boolean = dragDistancePx > MinCommitPx
}
```

- [ ] **Step 5: `OrderedPlacement.kt` anlegen**

```kotlin
package app.abcvorschule.ui.exercise

/**
 * Shared placement rules for the ordered trainers (word_build frames and the
 * sentence_order clothesline). Compares displays, so a repeated syllable such as
 * "ma" in Mama is accepted at every index where the solution expects it.
 */
object OrderedPlacement {
    fun isCorrectPlacement(index: Int, display: String, solution: List<String>): Boolean =
        solution.getOrNull(index) == display

    fun isSolved(placed: Map<Int, String>, solution: List<String>): Boolean =
        solution.isNotEmpty() &&
            solution.indices.all { placed[it] == solution[it] }

    fun nextEmptyIndex(placed: Map<Int, String>, size: Int): Int? =
        (0 until size).firstOrNull { placed[it] == null }
}
```

- [ ] **Step 6: `DragField.kt` anlegen**

```kotlin
package app.abcvorschule.ui.exercise.drag

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt

/**
 * Drag/tap state for one exercise board. Bounds live in plain maps because they
 * are only read when a gesture ends — they must never drive recomposition.
 */
class DragFieldState {
    var selectedKey by mutableStateOf<String?>(null)
        private set
    var draggingKey by mutableStateOf<String?>(null)
        private set
    var dragOffset by mutableStateOf(Offset.Zero)
        private set

    private val cards = mutableMapOf<String, Rect>()
    private val zones = mutableMapOf<String, Rect>()

    fun putCard(key: String, bounds: Rect) {
        cards[key] = bounds
    }

    fun putZone(key: String, bounds: Rect) {
        zones[key] = bounds
    }

    /**
     * Bounds must not outlive their composable. A trainer that stops composing a
     * filled slot or a placed tile would otherwise leave a phantom landing zone
     * behind, and a later drop would resolve against something nobody can see.
     */
    fun removeCard(key: String) {
        cards.remove(key)
    }

    fun removeZone(key: String) {
        zones.remove(key)
    }

    fun select(key: String?) {
        selectedKey = key
    }

    fun startDrag(key: String) {
        draggingKey = key
        selectedKey = key
        dragOffset = Offset.Zero
    }

    fun drag(delta: Offset) {
        dragOffset += delta
    }

    /** @return the zone the card landed on, or null when it should snap back. */
    fun endDrag(key: String): String? {
        val travelled = dragOffset.getDistance()
        val bounds = cards[key]
        val hit = if (bounds != null && DragHitTest.shouldCommit(travelled)) {
            DragHitTest.bestZone(bounds.toDragRect(), zones.mapValues { it.value.toDragRect() })
        } else {
            null
        }
        draggingKey = null
        dragOffset = Offset.Zero
        return hit
    }

    fun reset() {
        selectedKey = null
        draggingKey = null
        dragOffset = Offset.Zero
        cards.clear()
        zones.clear()
    }
}

private fun Rect.toDragRect() = DragRect(left, top, right, bottom)

@Composable
fun rememberDragFieldState(vararg keys: Any?): DragFieldState =
    remember(*keys) { DragFieldState() }

/**
 * A draggable answer tile with a mandatory tap-to-place alternative (R15).
 * [onDropped] receives the resolved zone key, or null when the tile snapped back.
 */
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
        modifier = modifier
            .zIndex(if (dragging) 1f else 0f)
            .offset {
                val o = if (dragging) state.dragOffset else Offset.Zero
                IntOffset(o.x.roundToInt(), o.y.roundToInt())
            }
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
        modifier = modifier
            .onGloballyPositioned { state.putZone(key, it.boundsInRoot()) }
            .clickable { onTap() },
        contentAlignment = Alignment.Center,
        content = content,
    )
}
```

- [ ] **Step 7: Tests laufen lassen und grün bestätigen**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: beides grün.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/drag app/src/main/java/app/abcvorschule/ui/exercise/OrderedPlacement.kt app/src/test/java/app/abcvorschule/ui/exercise
git commit -m "feat(exercise): extract shared drag primitives and ordered placement

DragHitTest resolves a straddling tile to the zone it covers most so a wrong
slot stays a real miss; DragField carries the drag/tap state every new trainer
reuses, and OrderedPlacement holds the frame rules for word and sentence order.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 6: Trainer 1 — Auditiver Finder (Lokomotive)

**Didaktisches Ziel:** Das Kind isoliert einen Laut im gesprochenen Wort und verortet ihn (Anlaut / Inlaut / Auslaut).

**UX:** Lok-Kopf + drei Waggons als Drop-Zonen (rot = Anfang, sandgelb = Mitte, blau = Ende). Das Bild-Wort erscheint als große Emoji-Karte unten und wird in den passenden Waggon gezogen (oder angetippt und dann der Waggon angetippt). Treffer: Waggon leuchtet mintfarben, Karte springt hinein, Lok stößt Dampf aus. Fehler: Karte schnappt zurück, das Wort wird segmentiert nachgesprochen (`missTts` — schon in `SessionViewModel.missCueForCurrent()` verdrahtet).

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SoundPositionTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt` (Platzhalter „Trainer 1" ersetzen)
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/SoundPositionLogicTest.kt`

**Interfaces:**
- Consumes: `SoundPositionRound`, `SoundSlot`, `Atom` (Task 1); `DragFieldState`, `DragCard`, `DropZone`, `rememberDragFieldState` (Task 5); `TrainerCallbacks` (Task 3).
- Produces:
  - `object SoundPositionLogic { val SlotOrder: List<SoundSlot>; fun isCorrect(round: SoundPositionRound, slot: SoundSlot): Boolean; fun slotKey(slot: SoundSlot): String; fun slotFromKey(key: String): SoundSlot? }`
  - `@Composable fun SoundPositionTrainer(round: SoundPositionRound, atom: Atom, ttsAvailable: Boolean, speaking: Boolean, onSpeakPrompt: () -> Unit, onSpeak: (String) -> Unit, onResult: (Boolean, Boolean, List<String>) -> Unit, modifier: Modifier)`

---

- [ ] **Step 1: Failing Test schreiben**

Neu `app/src/test/java/app/abcvorschule/ui/exercise/SoundPositionLogicTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SoundSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundPositionLogicTest {
    private val middleRound = SoundPositionRound(
        promptTts = "Wo versteckt sich das Mmm?",
        atomId = "ameise",
        slot = SoundSlot.middle,
        missTts = "A - Mmm - eise.",
    )

    @Test
    fun wagonsRunFrontToBack() {
        assertEquals(
            listOf(SoundSlot.start, SoundSlot.middle, SoundSlot.end),
            SoundPositionLogic.SlotOrder,
        )
    }

    @Test
    fun onlyTheAuthoredSlotIsCorrect() {
        assertTrue(SoundPositionLogic.isCorrect(middleRound, SoundSlot.middle))
        assertFalse(SoundPositionLogic.isCorrect(middleRound, SoundSlot.start))
        assertFalse(SoundPositionLogic.isCorrect(middleRound, SoundSlot.end))
    }

    @Test
    fun startAndEndRoundsAreDistinguished() {
        val start = middleRound.copy(atomId = "maus", slot = SoundSlot.start)
        val end = middleRound.copy(atomId = "baum", slot = SoundSlot.end)
        assertTrue(SoundPositionLogic.isCorrect(start, SoundSlot.start))
        assertFalse(SoundPositionLogic.isCorrect(start, SoundSlot.end))
        assertTrue(SoundPositionLogic.isCorrect(end, SoundSlot.end))
    }

    @Test
    fun slotKeysRoundTrip() {
        SoundSlot.entries.forEach { slot ->
            assertEquals(slot, SoundPositionLogic.slotFromKey(SoundPositionLogic.slotKey(slot)))
        }
        assertNull(SoundPositionLogic.slotFromKey("not-a-wagon"))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.SoundPositionLogicTest"`
Expected: FAIL — `SoundPositionLogic` existiert nicht.

- [ ] **Step 3: `SoundPositionTrainer.kt` anlegen**

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.R
import app.abcvorschule.content.Atom
import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SoundSlot
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.exercise.drag.DragCard
import app.abcvorschule.ui.exercise.drag.DropZone
import app.abcvorschule.ui.exercise.drag.rememberDragFieldState
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.SoftCoral
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky

object SoundPositionLogic {
    /** Front to back: locomotive head, middle wagon, last wagon. */
    val SlotOrder: List<SoundSlot> = listOf(SoundSlot.start, SoundSlot.middle, SoundSlot.end)

    fun isCorrect(round: SoundPositionRound, slot: SoundSlot): Boolean = round.slot == slot

    fun slotKey(slot: SoundSlot): String = "wagon-${slot.name}"

    fun slotFromKey(key: String): SoundSlot? =
        SoundSlot.entries.firstOrNull { slotKey(it) == key }
}

private val WagonSize = 96.dp

/**
 * Trainer 1 — Auditiver Finder. The child hears a phoneme and a picture word and
 * drops the picture into the wagon matching the sound's position. Only the
 * picture is shown; the word itself is never written.
 */
@Composable
fun SoundPositionTrainer(
    round: SoundPositionRound,
    atom: Atom,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "${round.atomId}-${round.slot}"
    val field = rememberDragFieldState(roundKey)
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var landedSlot by remember(roundKey) { mutableStateOf<SoundSlot?>(null) }
    var revealed by remember(roundKey) { mutableStateOf(false) }
    val cardKey = "picture-${round.atomId}"

    fun place(slot: SoundSlot) {
        if (landedSlot != null || revealed) return
        field.select(null)
        if (SoundPositionLogic.isCorrect(round, slot)) {
            landedSlot = slot
            onResult(true, false, listOf(round.atomId))
        } else {
            misses += 1
            onResult(false, false, listOf(round.atomId))
        }
    }

    val steam by animateFloatAsState(
        targetValue = if (landedSlot != null) 1f else 0f,
        label = "steam",
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
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                LocomotiveHead(steam = steam)
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
            }
        },
        answers = {
            if (landedSlot == null && !revealed) {
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
                            color = if (field.selectedKey == cardKey) SoftMint.copy(alpha = 0.3f) else NightElevated,
                            shape = RoundedCornerShape(24.dp),
                        )
                        .testTag("sound_card"),
                ) {
                    Text(text = atom.emoji, fontSize = 56.sp)
                }
            }
            if (misses >= 2 && landedSlot == null && !revealed) {
                AbcResolveButton(
                    onClick = {
                        revealed = true
                        onResult(false, true, listOf(round.atomId))
                    },
                )
            }
        },
    )
}

@Composable
private fun Wagon(
    slot: SoundSlot,
    filledEmoji: String?,
    revealed: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: app.abcvorschule.ui.exercise.drag.DragFieldState,
) {
    val accent = when (slot) {
        SoundSlot.start -> SoftCoral
        SoundSlot.middle -> SoftSand
        SoundSlot.end -> SoftSky
    }
    val desc = stringResource(
        when (slot) {
            SoundSlot.start -> R.string.wagon_start
            SoundSlot.middle -> R.string.wagon_middle
            SoundSlot.end -> R.string.wagon_end
        },
    )
    val border = when {
        filledEmoji != null || revealed -> SoftMint
        armed -> accent
        else -> accent.copy(alpha = 0.55f)
    }
    DropZone(
        state = registerWith,
        key = SoundPositionLogic.slotKey(slot),
        onTap = onTap,
        modifier = Modifier
            .size(WagonSize)
            .background(
                color = if (filledEmoji != null) SoftMint.copy(alpha = 0.18f) else NightElevated,
                shape = RoundedCornerShape(18.dp),
            )
            .border(4.dp, border, RoundedCornerShape(18.dp))
            .semantics { contentDescription = desc }
            .testTag("wagon_${slot.name}"),
    ) {
        if (filledEmoji != null) {
            Text(text = filledEmoji, fontSize = 44.sp)
        } else {
            // Position cue without text: one, two or three dots along the wagon.
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(SoundPositionLogic.SlotOrder.indexOf(slot) + 1) {
                    Box(
                        Modifier
                            .size(10.dp)
                            .background(accent.copy(alpha = 0.7f), RoundedCornerShape(5.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun LocomotiveHead(steam: Float) {
    Canvas(
        Modifier
            .width(72.dp)
            .height(WagonSize),
    ) {
        val w = size.width
        val h = size.height
        // Body
        drawRoundRect(
            color = SoftCoral,
            topLeft = Offset(w * 0.05f, h * 0.35f),
            size = Size(w * 0.9f, h * 0.5f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f),
        )
        // Cab
        drawRoundRect(
            color = SoftCoral.copy(alpha = 0.85f),
            topLeft = Offset(w * 0.45f, h * 0.15f),
            size = Size(w * 0.45f, h * 0.28f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.08f),
        )
        // Chimney
        drawRoundRect(
            color = SoftCoral,
            topLeft = Offset(w * 0.14f, h * 0.18f),
            size = Size(w * 0.16f, h * 0.2f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.04f),
        )
        // Wheels
        drawCircle(color = SoftSand, radius = w * 0.14f, center = Offset(w * 0.28f, h * 0.88f))
        drawCircle(color = SoftSand, radius = w * 0.14f, center = Offset(w * 0.7f, h * 0.88f))
        // Steam puffs grow when the child got it right.
        if (steam > 0f) {
            listOf(0.34f, 0.22f, 0.1f).forEachIndexed { i, y ->
                drawCircle(
                    color = Color.White.copy(alpha = 0.35f * steam),
                    radius = w * (0.09f + i * 0.03f) * steam,
                    center = Offset(w * (0.22f - i * 0.03f), h * y),
                )
            }
        }
    }
}
```

- [ ] **Step 4: `TrainerHost.kt` verdrahten**

```kotlin
        is SoundPositionRound -> SoundPositionTrainer(
            round = round,
            atom = pack.atom(round.atomId),
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
```

- [ ] **Step 5: Tests und Build laufen lassen**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: beides grün.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SoundPositionTrainer.kt app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt app/src/test/java/app/abcvorschule/ui/exercise/SoundPositionLogicTest.kt
git commit -m "feat(exercise): add Trainer 1, the auditive sound-position finder

Locomotive with three wagons for Anlaut/Inlaut/Auslaut; the picture word is
dropped or tapped into a wagon and a miss replays the segmented word aloud.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 7: Trainer 2 — Visueller Spurensucher (Buchstaben-Straße)

**Didaktisches Ziel:** Laut ↔ Graphem verknüpfen und die Schreibrichtung motorisch erfahren.

**UX:** Der Buchstabe ist eine breite hohle Straße aus den `Atom.strokes`. Sterne liegen der Reihe nach im Straßenverlauf. Das Kind zieht ein Fahrzeug mit dem Finger; verlässt es den Korridor, stoppt es und vibriert kurz. Jeder eingesammelte Stern klingt eine Tonleiterstufe höher. Ist der Buchstabe fertig, verwandelt sich die Straße kurz in das Belohnungs-Emoji und `rewardTts` wird gesprochen.

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/TraceGeometry.kt`
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/LetterTraceTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/rewards/SuccessEffects.kt` (aufsteigenden Stern-Ton ergänzen)
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/TraceGeometryTest.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/TraceProgressTest.kt`

**Interfaces:**
- Consumes: `LetterTraceRound`, `Atom`, `GlyphStroke` (Task 1); `TrainerCallbacks` (Task 3).
- Produces:
  - `data class TracePoint(val x: Float, val y: Float)`
  - `object TraceGeometry { fun toPixels(strokes: List<GlyphStroke>, boxSize: Float, origin: TracePoint): List<List<TracePoint>>; fun polylineLength(points: List<TracePoint>): Float; fun pointAtFraction(points: List<TracePoint>, fraction: Float): TracePoint; fun starPositions(points: List<TracePoint>, count: Int): List<TracePoint>; fun distanceToSegment(p: TracePoint, a: TracePoint, b: TracePoint): Float; fun distanceToPolyline(p: TracePoint, points: List<TracePoint>): Float }`
  - `data class TraceState(val strokeIndex: Int = 0, val starIndex: Int = 0)`
  - `data class TraceUpdate(val state: TraceState, val collectedStar: Boolean, val offCorridor: Boolean, val glyphDone: Boolean)`
  - `object TraceProgress { const val StarsPerStroke = 4; const val CorridorFraction = 0.16f; const val StarHitFraction = 0.12f; fun update(state: TraceState, finger: TracePoint, strokes: List<List<TracePoint>>, stars: List<List<TracePoint>>, boxSize: Float): TraceUpdate }`
  - `fun playStarBlip(step: Int)` in `ui/rewards/SuccessEffects.kt`
  - `@Composable fun LetterTraceTrainer(round: LetterTraceRound, atom: Atom, ttsAvailable: Boolean, speaking: Boolean, onSpeakPrompt: () -> Unit, onSpeak: (String) -> Unit, onResult: (Boolean, Boolean, List<String>) -> Unit, modifier: Modifier)`

---

- [ ] **Step 1: Failing Test für die Geometrie schreiben**

Neu `app/src/test/java/app/abcvorschule/ui/exercise/TraceGeometryTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import app.abcvorschule.content.GlyphStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceGeometryTest {
    private val horizontal = listOf(TracePoint(0f, 0f), TracePoint(100f, 0f))
    private val elbow = listOf(TracePoint(0f, 0f), TracePoint(0f, 100f), TracePoint(100f, 100f))

    @Test
    fun normalizedStrokesScaleIntoTheGlyphBox() {
        val strokes = TraceGeometry.toPixels(
            strokes = listOf(GlyphStroke(listOf(listOf(0.0, 0.0), listOf(1.0, 0.5)))),
            boxSize = 200f,
            origin = TracePoint(10f, 20f),
        )
        assertEquals(1, strokes.size)
        assertEquals(TracePoint(10f, 20f), strokes[0][0])
        assertEquals(TracePoint(210f, 120f), strokes[0][1])
    }

    @Test
    fun polylineLengthSumsEverySegment() {
        assertEquals(100f, TraceGeometry.polylineLength(horizontal), 0.01f)
        assertEquals(200f, TraceGeometry.polylineLength(elbow), 0.01f)
        assertEquals(0f, TraceGeometry.polylineLength(listOf(TracePoint(1f, 1f))), 0.01f)
    }

    @Test
    fun pointAtFractionWalksTheWholePolyline() {
        assertEquals(TracePoint(0f, 0f), TraceGeometry.pointAtFraction(elbow, 0f))
        assertEquals(TracePoint(0f, 100f), TraceGeometry.pointAtFraction(elbow, 0.5f))
        assertEquals(TracePoint(100f, 100f), TraceGeometry.pointAtFraction(elbow, 1f))
        // Out-of-range fractions clamp instead of throwing.
        assertEquals(TracePoint(0f, 0f), TraceGeometry.pointAtFraction(elbow, -1f))
        assertEquals(TracePoint(100f, 100f), TraceGeometry.pointAtFraction(elbow, 2f))
    }

    @Test
    fun starsAreEvenlySpacedAndEndAtTheStrokeEnd() {
        val stars = TraceGeometry.starPositions(horizontal, 4)
        assertEquals(4, stars.size)
        assertEquals(25f, stars[0].x, 0.5f)
        assertEquals(100f, stars.last().x, 0.5f)
        assertTrue(stars.zipWithNext().all { (a, b) -> b.x > a.x })
    }

    @Test
    fun starCountBelowOneYieldsTheStrokeEndOnly() {
        assertEquals(listOf(TracePoint(100f, 0f)), TraceGeometry.starPositions(horizontal, 0))
    }

    @Test
    fun distanceToSegmentClampsToTheEndpoints() {
        val a = TracePoint(0f, 0f)
        val b = TracePoint(100f, 0f)
        assertEquals(0f, TraceGeometry.distanceToSegment(TracePoint(50f, 0f), a, b), 0.01f)
        assertEquals(10f, TraceGeometry.distanceToSegment(TracePoint(50f, 10f), a, b), 0.01f)
        assertEquals(20f, TraceGeometry.distanceToSegment(TracePoint(-20f, 0f), a, b), 0.01f)
        assertEquals(30f, TraceGeometry.distanceToSegment(TracePoint(130f, 0f), a, b), 0.01f)
    }

    @Test
    fun degenerateSegmentFallsBackToPointDistance() {
        val a = TracePoint(5f, 5f)
        assertEquals(5f, TraceGeometry.distanceToSegment(TracePoint(5f, 10f), a, a), 0.01f)
    }

    @Test
    fun distanceToPolylineTakesTheNearestSegment() {
        assertEquals(2f, TraceGeometry.distanceToPolyline(TracePoint(98f, 98f), elbow), 0.01f)
        assertEquals(3f, TraceGeometry.distanceToPolyline(TracePoint(3f, 40f), elbow), 0.01f)
    }
}
```

- [ ] **Step 2: Failing Test für den Trace-Fortschritt schreiben**

Neu `app/src/test/java/app/abcvorschule/ui/exercise/TraceProgressTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TraceProgressTest {
    private val boxSize = 200f

    // Two strokes shaped like a T: horizontal bar, then the vertical stem.
    private val strokes = listOf(
        listOf(TracePoint(0f, 0f), TracePoint(100f, 0f)),
        listOf(TracePoint(50f, 0f), TracePoint(50f, 100f)),
    )
    private val stars = strokes.map { TraceGeometry.starPositions(it, 2) }

    private fun update(state: TraceState, p: TracePoint) =
        TraceProgress.update(state, p, strokes, stars, boxSize)

    @Test
    fun stayingInTheCorridorIsNotFlaggedOffRoad() {
        val result = update(TraceState(), TracePoint(10f, 4f))
        assertFalse(result.offCorridor)
        assertFalse(result.collectedStar)
    }

    @Test
    fun leavingTheCorridorIsFlaggedAndDoesNotAdvance() {
        val result = update(TraceState(), TracePoint(10f, boxSize))
        assertTrue(result.offCorridor)
        assertEquals(TraceState(0, 0), result.state)
    }

    @Test
    fun touchingTheNextStarCollectsItInOrder() {
        val first = update(TraceState(), stars[0][0])
        assertTrue(first.collectedStar)
        assertEquals(TraceState(0, 1), first.state)
    }

    @Test
    fun skippingAheadToALaterStarDoesNotAdvance() {
        // Only the next star counts, so the child cannot shortcut the stroke.
        val result = update(TraceState(), stars[0][1])
        assertFalse(result.collectedStar)
        assertEquals(TraceState(0, 0), result.state)
    }

    @Test
    fun finishingAStrokeMovesToTheNextStrokeAtStarZero() {
        var state = TraceState(0, 1)
        val result = update(state, stars[0][1])
        assertTrue(result.collectedStar)
        assertEquals(TraceState(1, 0), result.state)
        assertFalse(result.glyphDone)
    }

    @Test
    fun collectingTheLastStarOfTheLastStrokeCompletesTheGlyph() {
        val result = update(TraceState(1, 1), stars[1][1])
        assertTrue(result.collectedStar)
        assertTrue(result.glyphDone)
    }

    @Test
    fun updatesAfterCompletionAreInert() {
        val done = TraceState(strokes.size, 0)
        val result = update(done, TracePoint(0f, 0f))
        assertTrue(result.glyphDone)
        assertFalse(result.collectedStar)
        assertFalse(result.offCorridor)
    }

    @Test
    fun corridorScalesWithTheGlyphBox() {
        // The same finger offset is inside a big glyph and outside a small one.
        val offset = TracePoint(10f, 20f)
        assertFalse(TraceProgress.update(TraceState(), offset, strokes, stars, 400f).offCorridor)
        assertTrue(TraceProgress.update(TraceState(), offset, strokes, stars, 60f).offCorridor)
    }
}
```

- [ ] **Step 3: Tests laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.Trace*"`
Expected: FAIL — `TracePoint`, `TraceGeometry`, `TraceProgress` existieren nicht.

- [ ] **Step 4: `TraceGeometry.kt` anlegen**

```kotlin
package app.abcvorschule.ui.exercise

import app.abcvorschule.content.GlyphStroke
import kotlin.math.hypot

/** A point in glyph-box pixels, y growing downwards. */
data class TracePoint(val x: Float, val y: Float)

/** Pure polyline maths for the letter road: scaling, star placement, corridor distance. */
object TraceGeometry {
    fun toPixels(
        strokes: List<GlyphStroke>,
        boxSize: Float,
        origin: TracePoint,
    ): List<List<TracePoint>> = strokes.map { stroke ->
        stroke.points.map { p ->
            TracePoint(
                x = origin.x + (p.getOrElse(0) { 0.0 }).toFloat() * boxSize,
                y = origin.y + (p.getOrElse(1) { 0.0 }).toFloat() * boxSize,
            )
        }
    }

    fun polylineLength(points: List<TracePoint>): Float =
        points.zipWithNext().fold(0f) { acc, (a, b) -> acc + hypot(b.x - a.x, b.y - a.y) }

    fun pointAtFraction(points: List<TracePoint>, fraction: Float): TracePoint {
        if (points.isEmpty()) return TracePoint(0f, 0f)
        if (points.size == 1) return points[0]
        val total = polylineLength(points)
        if (total <= 0f) return points[0]
        val target = (fraction.coerceIn(0f, 1f)) * total
        var walked = 0f
        points.zipWithNext().forEach { (a, b) ->
            val segment = hypot(b.x - a.x, b.y - a.y)
            if (walked + segment >= target) {
                val t = if (segment <= 0f) 0f else (target - walked) / segment
                return TracePoint(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
            }
            walked += segment
        }
        return points.last()
    }

    /** [count] stars spread over the stroke; the last one always sits at the stroke end. */
    fun starPositions(points: List<TracePoint>, count: Int): List<TracePoint> {
        if (count < 1) return listOf(points.lastOrNull() ?: TracePoint(0f, 0f))
        return (1..count).map { i -> pointAtFraction(points, i.toFloat() / count) }
    }

    fun distanceToSegment(p: TracePoint, a: TracePoint, b: TracePoint): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val lengthSquared = dx * dx + dy * dy
        if (lengthSquared <= 0f) return hypot(p.x - a.x, p.y - a.y)
        val t = (((p.x - a.x) * dx + (p.y - a.y) * dy) / lengthSquared).coerceIn(0f, 1f)
        return hypot(p.x - (a.x + t * dx), p.y - (a.y + t * dy))
    }

    fun distanceToPolyline(p: TracePoint, points: List<TracePoint>): Float = when {
        points.isEmpty() -> Float.MAX_VALUE
        points.size == 1 -> hypot(p.x - points[0].x, p.y - points[0].y)
        else -> points.zipWithNext().minOf { (a, b) -> distanceToSegment(p, a, b) }
    }
}

/** Which stroke and which star of that stroke the child is on. */
data class TraceState(val strokeIndex: Int = 0, val starIndex: Int = 0)

data class TraceUpdate(
    val state: TraceState,
    val collectedStar: Boolean,
    val offCorridor: Boolean,
    val glyphDone: Boolean,
)

/**
 * Stroke-order enforcement: the finger must stay inside a corridor around the
 * current stroke, and only the *next* star counts — so the glyph cannot be
 * shortcut and the writing direction is actually practiced.
 */
object TraceProgress {
    const val StarsPerStroke = 4

    /** Corridor half-width as a fraction of the glyph box. */
    const val CorridorFraction = 0.16f

    /** Star pick-up radius as a fraction of the glyph box. */
    const val StarHitFraction = 0.12f

    fun update(
        state: TraceState,
        finger: TracePoint,
        strokes: List<List<TracePoint>>,
        stars: List<List<TracePoint>>,
        boxSize: Float,
    ): TraceUpdate {
        if (state.strokeIndex >= strokes.size) {
            return TraceUpdate(state, collectedStar = false, offCorridor = false, glyphDone = true)
        }
        val stroke = strokes[state.strokeIndex]
        val corridor = boxSize * CorridorFraction
        if (TraceGeometry.distanceToPolyline(finger, stroke) > corridor) {
            return TraceUpdate(state, collectedStar = false, offCorridor = true, glyphDone = false)
        }
        val target = stars.getOrNull(state.strokeIndex)?.getOrNull(state.starIndex)
            ?: return TraceUpdate(state, collectedStar = false, offCorridor = false, glyphDone = false)
        val hit = TraceGeometry.distanceToPolyline(finger, listOf(target)) <= boxSize * StarHitFraction
        if (!hit) {
            return TraceUpdate(state, collectedStar = false, offCorridor = false, glyphDone = false)
        }
        val lastStarOfStroke = state.starIndex + 1 >= (stars[state.strokeIndex].size)
        val next = if (lastStarOfStroke) {
            TraceState(state.strokeIndex + 1, 0)
        } else {
            TraceState(state.strokeIndex, state.starIndex + 1)
        }
        return TraceUpdate(
            state = next,
            collectedStar = true,
            offCorridor = false,
            glyphDone = next.strokeIndex >= strokes.size,
        )
    }
}
```

- [ ] **Step 5: Aufsteigenden Stern-Ton in `SuccessEffects.kt` ergänzen**

Am Dateiende von `app/src/main/java/app/abcvorschule/ui/rewards/SuccessEffects.kt`, unterhalb von `playSuccessChime()`:

```kotlin
/** One rising scale step per collected trace star (C major, wrapping after an octave). */
fun playStarBlip(step: Int) {
    val scale = listOf(523.25, 587.33, 659.25, 698.46, 783.99, 880.0, 987.77, 1046.50)
    val freq = scale[step.coerceAtLeast(0) % scale.size]
    playTone(listOf(freq), noteMs = 70, gapMs = 0)
}

/**
 * [gapMs] defaults to buildArpeggio's original spacing so routing the existing
 * success chime through this shared path does not change how it sounds.
 */
private fun playTone(freqsHz: List<Double>, noteMs: Int, gapMs: Int = 15) {
    runCatching {
        val samples = buildArpeggio(freqsHz, noteMs = noteMs, gapMs = gapMs)
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(SampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()
        track.write(samples, 0, samples.size)
        track.setNotificationMarkerPosition(samples.size)
        track.setPlaybackPositionUpdateListener(
            object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack?) {
                    runCatching { track.release() }
                }

                override fun onPeriodicNotification(t: AudioTrack?) = Unit
            },
        )
        track.play()
    }
}
```

Ändere zusätzlich `playSuccessChime()` so, dass es `playTone(notes, noteMs = 90)` aufruft, damit die AudioTrack-Verdrahtung nur einmal existiert. Der `gapMs`-Default von 15 ms muss dabei erhalten bleiben — sonst laufen die vier Töne des bestehenden Erfolgs-Chimes ohne Pause ineinander.

- [ ] **Step 6: `LetterTraceTrainer.kt` anlegen**

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.Atom
import app.abcvorschule.content.LetterTraceRound
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.rewards.playStarBlip
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.SoftCoral
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand

private val GlyphBox = 260.dp

/**
 * Trainer 2 — Visueller Spurensucher. The glyph is a hollow road built from the
 * atom's authored strokes; the vehicle only advances while the finger stays in
 * the corridor, so the writing direction is what is actually practiced.
 */
@Composable
fun LetterTraceTrainer(
    round: LetterTraceRound,
    atom: Atom,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = round.atomId
    var state by remember(roundKey) { mutableStateOf(TraceState()) }
    var vehicle by remember(roundKey) { mutableStateOf<TracePoint?>(null) }
    var starsCollected by remember(roundKey) { mutableIntStateOf(0) }
    var offRoadCount by remember(roundKey) { mutableIntStateOf(0) }
    var wasOffCorridor by remember(roundKey) { mutableStateOf(false) }
    var done by remember(roundKey) { mutableStateOf(false) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    val haptics = LocalHapticFeedback.current

    val morph by animateFloatAsState(
        targetValue = if (done || resolved) 1f else 0f,
        label = "glyph_morph",
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
            Box(
                modifier = Modifier.size(GlyphBox),
                contentAlignment = Alignment.Center,
            ) {
                if (morph < 1f) {
                    TraceCanvas(
                        atom = atom,
                        state = state,
                        vehicle = vehicle,
                        onFinger = { finger, boxSize, strokes, stars ->
                            if (done || resolved) return@TraceCanvas
                            val update = TraceProgress.update(state, finger, strokes, stars, boxSize)
                            if (update.offCorridor) {
                                // Edge-triggered: one short nudge per excursion, never one per
                                // pointer sample. Otherwise the device buzzes continuously and a
                                // single stray drag exhausts the resolve threshold at once.
                                if (!wasOffCorridor) {
                                    wasOffCorridor = true
                                    offRoadCount += 1
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                }
                                return@TraceCanvas
                            }
                            wasOffCorridor = false
                            vehicle = finger
                            if (update.collectedStar) {
                                playStarBlip(starsCollected)
                                starsCollected += 1
                                state = update.state
                            }
                            if (update.glyphDone) {
                                done = true
                                onResult(true, false, listOf(atom.id))
                            }
                        },
                        modifier = Modifier
                            .size(GlyphBox)
                            .testTag("trace_canvas_${atom.id}"),
                    )
                } else {
                    // The road briefly becomes the object the letter stands for.
                    Text(text = round.rewardEmoji, fontSize = 108.sp)
                }
            }
            Text(
                text = round.glyph,
                fontSize = 28.sp,
                color = MutedText.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        answers = {
            // Repeated off-road nudges make the resolve available, matching R10.
            if (offRoadCount >= 6 && !done && !resolved) {
                AbcResolveButton(
                    onClick = {
                        resolved = true
                        onResult(false, true, listOf(atom.id))
                    },
                )
            }
        },
    )
}

@Composable
private fun TraceCanvas(
    atom: Atom,
    state: TraceState,
    vehicle: TracePoint?,
    onFinger: (
        finger: TracePoint,
        boxSize: Float,
        strokes: List<List<TracePoint>>,
        stars: List<List<TracePoint>>,
    ) -> Unit,
    modifier: Modifier = Modifier,
) {
    Canvas(
        modifier = modifier.pointerInput(atom.id) {
            androidx.compose.foundation.gestures.detectDragGestures(
                onDrag = { change, _ ->
                    change.consume()
                    val box = size.width.toFloat().coerceAtMost(size.height.toFloat())
                    val strokes = TraceGeometry.toPixels(atom.strokes, box, TracePoint(0f, 0f))
                    val stars = strokes.map {
                        TraceGeometry.starPositions(it, TraceProgress.StarsPerStroke)
                    }
                    onFinger(
                        TracePoint(change.position.x, change.position.y),
                        box,
                        strokes,
                        stars,
                    )
                },
            )
        },
    ) {
        val box = size.minDimension
        val strokes = TraceGeometry.toPixels(atom.strokes, box, TracePoint(0f, 0f))
        val stars = strokes.map { TraceGeometry.starPositions(it, TraceProgress.StarsPerStroke) }
        val corridor = box * TraceProgress.CorridorFraction

        strokes.forEachIndexed { index, stroke ->
            val path = Path().apply {
                moveTo(stroke.first().x, stroke.first().y)
                stroke.drop(1).forEach { lineTo(it.x, it.y) }
            }
            val active = index == state.strokeIndex
            val outer = if (index < state.strokeIndex) SoftMint else SoftSand
            // Hollow road: a wide light band with a dark inner lane.
            drawPath(
                path = path,
                color = outer.copy(alpha = if (active) 0.30f else 0.16f),
                style = Stroke(
                    width = corridor * 2f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            drawPath(
                path = path,
                color = NightInk,
                style = Stroke(
                    width = corridor * 1.25f,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
            stars.getOrNull(index)?.forEachIndexed { starIndex, star ->
                val collected = index < state.strokeIndex ||
                    (index == state.strokeIndex && starIndex < state.starIndex)
                val next = index == state.strokeIndex && starIndex == state.starIndex
                drawCircle(
                    color = when {
                        collected -> SoftMint
                        next -> SoftSand
                        else -> SoftSand.copy(alpha = 0.35f)
                    },
                    radius = if (next) box * 0.035f else box * 0.025f,
                    center = Offset(star.x, star.y),
                )
            }
        }
        val car = vehicle ?: strokes.firstOrNull()?.firstOrNull()
        if (car != null) {
            drawCircle(color = SoftCoral, radius = box * 0.055f, center = Offset(car.x, car.y))
        }
    }
}
```

- [ ] **Step 7: `TrainerHost.kt` verdrahten**

```kotlin
        is LetterTraceRound -> LetterTraceTrainer(
            round = round,
            atom = pack.atom(round.atomId),
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
```

- [ ] **Step 8: Tests und Build laufen lassen**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: beides grün.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/TraceGeometry.kt app/src/main/java/app/abcvorschule/ui/exercise/LetterTraceTrainer.kt app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt app/src/main/java/app/abcvorschule/ui/rewards/SuccessEffects.kt app/src/test/java/app/abcvorschule/ui/exercise/TraceGeometryTest.kt app/src/test/java/app/abcvorschule/ui/exercise/TraceProgressTest.kt
git commit -m "feat(exercise): add Trainer 2, the letter-trace road

Glyph strokes come from the content pack, stars must be collected in stroke
order, and leaving the corridor stops the vehicle with a haptic nudge instead of
scoring a miss. Adds a rising scale blip per star.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 8: Trainer 3 — Silben-Verschmelzer (Eisschollen)

**Didaktisches Ziel:** Die Synthese-Hürde nehmen — Konsonant und Vokal zu einer fließenden Silbe zusammenziehen.

**UX:** Zwei Eisschollen mit Abstand; links der Konsonant, rechts der Vokal. Das Kind zieht die linke Kachel nach rechts. Beim Ziehstart wird `stretchTts` gesprochen; je näher die Kacheln kommen, desto stärker glühen sie (visuelle Entsprechung des lauter werdenden Tons — System-TTS kann keinen kontinuierlich gedehnten Laut liefern). Bei Kontakt frieren die Schollen zusammen, die neue Silbe erscheint groß, und die Erfolgspipeline sprich `resultDisplay` und zeigt den Stern.

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SyllableMergeTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/MergeProgressTest.kt`

**Interfaces:**
- Consumes: `SyllableMergeRound` (Task 1); `TrainerCallbacks` (Task 3).
- Produces:
  - `object MergeProgress { const val CommitFraction = 0.88f; fun fraction(currentX: Float, startX: Float, targetX: Float): Float; fun isMerged(fraction: Float): Boolean; fun glow(fraction: Float): Float }`
  - `@Composable fun SyllableMergeTrainer(round: SyllableMergeRound, ttsAvailable: Boolean, speaking: Boolean, onSpeakPrompt: () -> Unit, onSpeak: (String) -> Unit, onResult: (Boolean, Boolean, List<String>) -> Unit, modifier: Modifier)`

---

- [ ] **Step 1: Failing Test schreiben**

Neu `app/src/test/java/app/abcvorschule/ui/exercise/MergeProgressTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeProgressTest {
    @Test
    fun fractionIsZeroAtTheStartAndOneAtTheTarget() {
        assertEquals(0f, MergeProgress.fraction(100f, 100f, 400f), 0.001f)
        assertEquals(1f, MergeProgress.fraction(400f, 100f, 400f), 0.001f)
        assertEquals(0.5f, MergeProgress.fraction(250f, 100f, 400f), 0.001f)
    }

    @Test
    fun draggingBackwardsOrPastTheTargetClamps() {
        assertEquals(0f, MergeProgress.fraction(0f, 100f, 400f), 0.001f)
        assertEquals(1f, MergeProgress.fraction(900f, 100f, 400f), 0.001f)
    }

    @Test
    fun zeroLengthTravelDoesNotDivideByZero() {
        assertEquals(0f, MergeProgress.fraction(100f, 100f, 100f), 0.001f)
    }

    @Test
    fun mergeCommitsOnlyCloseToTheVowel() {
        assertFalse(MergeProgress.isMerged(0f))
        assertFalse(MergeProgress.isMerged(0.7f))
        assertTrue(MergeProgress.isMerged(MergeProgress.CommitFraction))
        assertTrue(MergeProgress.isMerged(1f))
    }

    @Test
    fun glowRampsUpButNeverExceedsFull() {
        assertTrue(MergeProgress.glow(0f) < MergeProgress.glow(0.5f))
        assertTrue(MergeProgress.glow(0.5f) < MergeProgress.glow(1f))
        assertEquals(1f, MergeProgress.glow(1f), 0.001f)
        assertTrue(MergeProgress.glow(0f) >= 0f)
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.MergeProgressTest"`
Expected: FAIL — `MergeProgress` existiert nicht.

- [ ] **Step 3: `SyllableMergeTrainer.kt` anlegen**

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.abcvorschule.content.SyllableMergeRound
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky
import kotlin.math.roundToInt

object MergeProgress {
    /** How close to the vowel the consonant must get before the floes freeze together. */
    const val CommitFraction = 0.88f

    fun fraction(currentX: Float, startX: Float, targetX: Float): Float {
        val travel = targetX - startX
        if (travel == 0f) return 0f
        return ((currentX - startX) / travel).coerceIn(0f, 1f)
    }

    fun isMerged(fraction: Float): Boolean = fraction >= CommitFraction

    /** Visual stand-in for the intensifying sound: 0.25 at rest, 1.0 on contact. */
    fun glow(fraction: Float): Float = (0.25f + 0.75f * fraction.coerceIn(0f, 1f))
}

private val FloeGap = 96.dp

/**
 * Trainer 3 — Silben-Verschmelzer. Dragging the consonant towards the vowel ramps
 * up the glow and merges both tiles into one syllable. System TTS cannot stretch a
 * phoneme continuously, so the stretched sound plays once on drag start and the
 * intensification is carried visually.
 */
@Composable
fun SyllableMergeTrainer(
    round: SyllableMergeRound,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "${round.leftAtomId}-${round.rightAtomId}"
    val density = LocalDensity.current
    val travelPx = with(density) { FloeGap.toPx() }
    var dragX by remember(roundKey) { mutableFloatStateOf(0f) }
    var merged by remember(roundKey) { mutableStateOf(false) }
    val fraction = MergeProgress.fraction(dragX, 0f, travelPx)
    val glow by animateFloatAsState(
        targetValue = if (merged) 1f else MergeProgress.glow(fraction),
        label = "merge_glow",
    )
    val scoredIds = remember(roundKey) {
        listOf(round.leftAtomId, round.rightAtomId, round.resultAtomId).distinct()
    }

    fun commit() {
        if (merged) return
        merged = true
        onResult(true, false, scoredIds)
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
            if (merged) {
                Floe(
                    label = round.resultDisplay,
                    glow = 1f,
                    frozen = true,
                    onTap = { onSpeak(round.resultDisplay) },
                    modifier = Modifier.testTag("merge_result"),
                )
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Floe(
                        label = round.leftDisplay,
                        glow = glow,
                        frozen = false,
                        onTap = { onSpeak(round.stretchTts) },
                        modifier = Modifier
                            .offset { IntOffset(dragX.roundToInt(), 0) }
                            .pointerInput(roundKey) {
                                detectDragGestures(
                                    onDragStart = { onSpeak(round.stretchTts) },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragX = (dragX + amount.x).coerceIn(0f, travelPx)
                                    },
                                    onDragEnd = {
                                        if (MergeProgress.isMerged(
                                                MergeProgress.fraction(dragX, 0f, travelPx),
                                            )
                                        ) {
                                            commit()
                                        } else {
                                            // No penalty: a short pull just slides back.
                                            dragX = 0f
                                        }
                                    },
                                    onDragCancel = { dragX = 0f },
                                )
                            }
                            .testTag("merge_left"),
                    )
                    Spacer(Modifier.width(FloeGap))
                    Floe(
                        label = round.rightDisplay,
                        glow = glow,
                        frozen = false,
                        onTap = { onSpeak(round.rightDisplay) },
                        modifier = Modifier.testTag("merge_right"),
                    )
                }
            }
        },
        answers = {
            // Tap-to-place alternative to the drag (R15): tapping the vowel pulls
            // the consonant across without a gesture.
            if (!merged) {
                Box(
                    modifier = Modifier
                        .defaultMinSize(minWidth = AbcDimens.kidTouch, minHeight = AbcDimens.kidTouch)
                        .background(NightElevated, RoundedCornerShape(22.dp))
                        .clickable {
                            dragX = travelPx
                            commit()
                        }
                        .testTag("merge_join"),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "→|",
                        style = androidx.compose.material3.MaterialTheme.typography.headlineMedium,
                        color = SoftSand,
                    )
                }
            }
        },
    )
}

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
            .size(AbcDimens.letterFrame)
            .background(
                color = if (frozen) SoftMint.copy(alpha = 0.22f) else NightElevated,
                shape = RoundedCornerShape(26.dp),
            )
            .border(
                width = 4.dp,
                color = (if (frozen) SoftMint else SoftSky).copy(alpha = glow),
                shape = RoundedCornerShape(26.dp),
            )
            .clickable(onClick = onTap),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = AbcDimens.letterSp,
            color = if (frozen) SoftMint else SoftSand,
        )
    }
}
```

- [ ] **Step 4: `TrainerHost.kt` verdrahten**

```kotlin
        is SyllableMergeRound -> SyllableMergeTrainer(
            round = round,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
```

- [ ] **Step 5: Tests und Build laufen lassen**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: beides grün.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SyllableMergeTrainer.kt app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt app/src/test/java/app/abcvorschule/ui/exercise/MergeProgressTest.kt
git commit -m "feat(exercise): add Trainer 3, the syllable merger

Two floes freeze together once the consonant is dragged close enough to the
vowel; a short pull slides back without penalty and a tap-to-join button covers
the non-drag path.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 9: Trainer 4 — Wort-Bauer (Schablonen und Silbenklötze)

**Didaktisches Ziel:** Erste Wörter aus bereits verschmolzenen Silben und Einzelbuchstaben zusammensetzen.

**UX:** Oben das Bild (Emoji des Ziel-Atoms) groß. Darunter leere quadratische Rahmen, im Beginner-Gerüst mit blasser Silhouette der erwarteten Silbe, im Advanced-Gerüst leer. Unten die Klötze (Lösungssilben + autorierte Distraktoren, Tray ≤ 5). Ein Klotz in einem Rahmen wird vorgelesen; sind alle Rahmen korrekt, liest die Erfolgspipeline das ganze Wort und der Stern erscheint.

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/WordBuildTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/WordBuildTrayTest.kt`

**Interfaces:**
- Consumes: `WordBuildRound`, `WordBlock`, `Atom` (Task 1); `OrderedPlacement`, `DragCard`, `DropZone`, `rememberDragFieldState` (Task 5); `ScaffoldLevel`, `TrainerCallbacks`.
- Produces:
  - `object WordBuildTray { const val MaxTrayTiles = 5; fun tiles(round: WordBuildRound, placedDisplays: List<String>): List<WordBlock>; fun frameKey(index: Int): String; fun frameIndex(key: String): Int? }`
  - `@Composable fun WordBuildTrainer(round: WordBuildRound, target: Atom, scaffoldFor: (String) -> ScaffoldLevel, ttsAvailable: Boolean, speaking: Boolean, onSpeakPrompt: () -> Unit, onSpeak: (String) -> Unit, onResult: (Boolean, Boolean, List<String>) -> Unit, modifier: Modifier)`

---

- [ ] **Step 1: Failing Test schreiben**

Neu `app/src/test/java/app/abcvorschule/ui/exercise/WordBuildTrayTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import app.abcvorschule.content.WordBlock
import app.abcvorschule.content.WordBuildRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WordBuildTrayTest {
    private val mama = WordBuildRound(
        promptTts = "Baue Mama.",
        targetAtomId = "mama",
        blocks = listOf(WordBlock("ma", "Ma"), WordBlock("ma", "ma")),
    )

    @Test
    fun freshTrayHoldsEverySolutionBlock() {
        assertEquals(listOf("Ma", "ma"), WordBuildTray.tiles(mama, emptyList()).map { it.display })
    }

    @Test
    fun placedBlocksLeaveTheTray() {
        assertEquals(listOf("ma"), WordBuildTray.tiles(mama, listOf("Ma")).map { it.display })
        assertTrue(WordBuildTray.tiles(mama, listOf("Ma", "ma")).isEmpty())
    }

    @Test
    fun repeatedBlockIsRemovedOnlyOncePerPlacement() {
        val mimi = mama.copy(
            targetAtomId = "mimi",
            blocks = listOf(WordBlock("mi", "Mi"), WordBlock("mi", "mi")),
        )
        assertEquals(listOf("mi"), WordBuildTray.tiles(mimi, listOf("Mi")).map { it.display })
    }

    @Test
    fun identicalDisplaysLeaveTheTrayOneAtATime() {
        // The Mama/Mimi fixtures differ in case ("Ma" vs "ma"), so they cannot tell a
        // remove-one implementation from a remove-every-match one. Two blocks spelling
        // the *same* display can: placing one must leave exactly one behind.
        val doubled = mama.copy(
            blocks = listOf(WordBlock("ba", "ba"), WordBlock("ba", "ba")),
        )
        assertEquals(listOf("ba"), WordBuildTray.tiles(doubled, listOf("ba")).map { it.display })
        assertTrue(WordBuildTray.tiles(doubled, listOf("ba", "ba")).isEmpty())
    }

    @Test
    fun distractorsAreAppendedAndTrayStaysSmall() {
        val withDistractors = mama.copy(
            distractors = listOf(
                WordBlock("mi", "Mi"),
                WordBlock("letter-o", "O"),
                WordBlock("letter-a", "A"),
            ),
        )
        val tiles = WordBuildTray.tiles(withDistractors, emptyList())
        assertTrue("tray must stay scannable", tiles.size <= WordBuildTray.MaxTrayTiles)
        assertEquals(listOf("Ma", "ma", "Mi", "O", "A"), tiles.map { it.display })
    }

    @Test
    fun frameKeysRoundTrip() {
        assertEquals(0, WordBuildTray.frameIndex(WordBuildTray.frameKey(0)))
        assertEquals(3, WordBuildTray.frameIndex(WordBuildTray.frameKey(3)))
        assertNull(WordBuildTray.frameIndex("wagon-start"))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.WordBuildTrayTest"`
Expected: FAIL — `WordBuildTray` existiert nicht.

- [ ] **Step 3: `WordBuildTrainer.kt` anlegen**

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.Atom
import app.abcvorschule.content.WordBlock
import app.abcvorschule.content.WordBuildRound
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.exercise.drag.DragCard
import app.abcvorschule.ui.exercise.drag.DropZone
import app.abcvorschule.ui.exercise.drag.rememberDragFieldState
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand

/**
 * Frames sit in one row and a word can need four of them (N·e·s·t), so they are
 * deliberately narrower than [AbcDimens.letterFrame], which is sized for a single
 * standalone glyph and would overflow a narrow screen here.
 */
private val WordFrameMin = 84.dp

object WordBuildTray {
    /** Preschoolers must be able to scan the whole tray at a glance. */
    const val MaxTrayTiles = 5

    fun tiles(round: WordBuildRound, placedDisplays: List<String>): List<WordBlock> {
        val remaining = round.blocks.toMutableList()
        placedDisplays.forEach { display ->
            val hit = remaining.indexOfFirst { it.display == display }
            if (hit >= 0) remaining.removeAt(hit)
        }
        if (remaining.isEmpty()) return emptyList()
        return (remaining + round.distractors).take(MaxTrayTiles)
    }

    fun frameKey(index: Int): String = "frame-$index"

    fun frameIndex(key: String): Int? = key.removePrefix("frame-").toIntOrNull()
        ?.takeIf { key.startsWith("frame-") }
}

/**
 * Trainer 4 — Wort-Bauer. The picture anchors the meaning, the frames carry the
 * per-atom scaffold (silhouette vs. empty), and only authored blocks are offered.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WordBuildTrainer(
    round: WordBuildRound,
    target: Atom,
    scaffoldFor: (String) -> ScaffoldLevel,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "${round.targetAtomId}-${round.blocks.size}"
    val field = rememberDragFieldState(roundKey)
    val placed = remember(roundKey) { mutableStateMapOf<Int, String>() }
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    val solution = remember(roundKey) { round.blocks.map { it.display } }
    val scoredIds = remember(roundKey) {
        (round.blocks.map { it.atomId } + round.targetAtomId).distinct()
    }
    val tiles = WordBuildTray.tiles(round, placed.values.toList())

    fun place(index: Int, block: WordBlock) {
        if (resolved || placed[index] != null) return
        field.select(null)
        if (OrderedPlacement.isCorrectPlacement(index, block.display, solution)) {
            placed[index] = block.display
            onSpeak(block.display)
            if (OrderedPlacement.isSolved(placed.toMap(), solution)) {
                onResult(true, false, scoredIds)
            }
        } else {
            misses += 1
            onResult(false, false, listOf(block.atomId))
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
            Text(text = target.emoji, fontSize = 84.sp)
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                solution.forEachIndexed { index, expected ->
                    if (index > 0) {
                        Text(
                            text = "·",
                            style = MaterialTheme.typography.displayLarge,
                            color = SoftSand.copy(alpha = 0.55f),
                        )
                    }
                    val filled = if (resolved) expected else placed[index]
                    val atomId = round.blocks[index].atomId
                    Frame(
                        expected = expected,
                        filled = filled,
                        showSilhouette = scaffoldFor(atomId) == ScaffoldLevel.Beginner,
                        armed = field.selectedKey != null && filled == null,
                        onTap = {
                            val selected = field.selectedKey
                            val block = tiles.firstOrNull { blockKey(it) == selected }
                            if (block != null) place(index, block)
                            if (filled != null) onSpeak(filled)
                        },
                        registerWith = field,
                        index = index,
                    )
                }
            }
        },
        answers = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.testTag("word_tray"),
            ) {
                if (!resolved) {
                    tiles.forEach { block ->
                        val key = blockKey(block)
                        DragCard(
                            state = field,
                            key = key,
                            onTap = {
                                field.select(key)
                                onSpeak(block.display)
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
                                    color = if (field.selectedKey == key) SoftMint else NightElevated,
                                    shape = RoundedCornerShape(22.dp),
                                )
                                .padding(horizontal = 18.dp, vertical = 12.dp)
                                .testTag("block_${block.display}"),
                        ) {
                            Text(
                                text = block.display,
                                fontSize = AbcDimens.syllableSp,
                                color = if (field.selectedKey == key) NightInk else SoftSand,
                            )
                        }
                    }
                }
            }
            if (misses >= 2 && !resolved) {
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

private fun blockKey(block: WordBlock): String = "block-${block.atomId}-${block.display}"

@Composable
private fun Frame(
    expected: String,
    filled: String?,
    showSilhouette: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: app.abcvorschule.ui.exercise.drag.DragFieldState,
    index: Int,
) {
    DropZone(
        state = registerWith,
        key = WordBuildTray.frameKey(index),
        onTap = onTap,
        modifier = Modifier
            .defaultMinSize(minWidth = WordFrameMin, minHeight = WordFrameMin)
            .background(
                color = if (armed) SoftMint.copy(alpha = 0.22f) else NightElevated,
                shape = RoundedCornerShape(22.dp),
            )
            .border(
                width = 3.dp,
                color = if (filled != null) SoftMint.copy(alpha = 0.7f) else SoftSand.copy(alpha = 0.35f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .testTag("frame_$index"),
    ) {
        when {
            filled != null -> Text(text = filled, fontSize = AbcDimens.syllableSp, color = SoftSand)
            showSilhouette -> Text(
                text = expected,
                fontSize = AbcDimens.syllableSp,
                color = SoftSand,
                modifier = Modifier.alpha(0.22f),
            )
            else -> Text(
                text = "_",
                fontSize = AbcDimens.syllableSp,
                color = SoftSand.copy(alpha = 0.45f),
            )
        }
    }
}
```

- [ ] **Step 4: `TrainerHost.kt` verdrahten**

```kotlin
        is WordBuildRound -> WordBuildTrainer(
            round = round,
            target = pack.atom(round.targetAtomId),
            scaffoldFor = scaffoldFor,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            onSpeakPrompt = callbacks.onSpeakPrompt,
            onSpeak = callbacks.onSpeak,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
```

- [ ] **Step 5: Tests und Build laufen lassen**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: beides grün.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/WordBuildTrainer.kt app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt app/src/test/java/app/abcvorschule/ui/exercise/WordBuildTrayTest.kt
git commit -m "feat(exercise): add Trainer 4, the word builder

Picture-anchored frames with per-atom silhouette scaffolds and an authored block
tray capped at five tiles; a repeated syllable leaves the tray once per placement.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 10: Trainer 5 — Satz-Architekt (Wäscheleine)

**Didaktisches Ziel:** Wörter zu einem grammatikalisch korrekten Satz anordnen. Bei Einwort-Runden (Lektion 1/2) degeneriert der Trainer zur Wort-Bild-Zuordnung — dieselbe Mechanik, eine Klammer.

**UX:** Oben das Illustrations-Emoji, darunter eine gezeichnete Wäscheleine mit hängenden Klammern als Drop-Zonen. Unten die Wortschilder. Jedes berührte Schild wird vorgelesen. Ist der Satz vollständig korrekt, liest die Erfolgspipeline den ganzen Satz in einem Rutsch und der Stern erscheint.

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SentenceOrderTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt` (letzten Platzhalter und den `else`-Zweig entfernen)
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/SentenceOrderTrayTest.kt`

**Interfaces:**
- Consumes: `SentenceOrderRound`, `Sentence`, `WordBlock` (Task 1); `OrderedPlacement`, Drag-Primitive (Task 5); `ContentPack.sentenceWords` (Task 1).
- Produces:
  - `object SentenceOrderTray { const val MaxTrayTiles = 6; fun cards(words: List<String>, atomIds: List<String>, distractors: List<WordBlock>, placedDisplays: List<String>): List<WordBlock>; fun pegKey(index: Int): String; fun pegIndex(key: String): Int? }`
  - `@Composable fun SentenceOrderTrainer(round: SentenceOrderRound, words: List<String>, atomIds: List<String>, illustrationEmoji: String?, scaffoldFor: (String) -> ScaffoldLevel, ttsAvailable: Boolean, speaking: Boolean, onSpeakPrompt: () -> Unit, onSpeak: (String) -> Unit, onResult: (Boolean, Boolean, List<String>) -> Unit, modifier: Modifier)`

---

- [ ] **Step 1: Failing Test schreiben**

Neu `app/src/test/java/app/abcvorschule/ui/exercise/SentenceOrderTrayTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import app.abcvorschule.content.WordBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SentenceOrderTrayTest {
    private val words = listOf("Oma", "ist", "da")
    private val atomIds = listOf("oma", "ist", "da")

    @Test
    fun trayIsShuffleSafeAndCarriesOneCardPerWord() {
        val cards = SentenceOrderTray.cards(words, atomIds, emptyList(), emptyList())
        assertEquals(words.toSet(), cards.map { it.display }.toSet())
        assertEquals(atomIds.toSet(), cards.map { it.atomId }.toSet())
    }

    @Test
    fun hungCardsLeaveTheTray() {
        val cards = SentenceOrderTray.cards(words, atomIds, emptyList(), listOf("Oma"))
        assertEquals(listOf("ist", "da"), cards.map { it.display })
    }

    @Test
    fun distractorsAreOfferedOnceEveryWordIsStillAvailable() {
        val cards = SentenceOrderTray.cards(
            words,
            atomIds,
            listOf(WordBlock("mama", "Mama")),
            emptyList(),
        )
        assertTrue(cards.any { it.display == "Mama" })
        assertTrue(cards.size <= SentenceOrderTray.MaxTrayTiles)
    }

    @Test
    fun singleWordRoundDegeneratesToPictureMatching() {
        val cards = SentenceOrderTray.cards(listOf("Mama"), listOf("mama"), emptyList(), emptyList())
        assertEquals(1, cards.size)
        assertEquals("Mama", cards.single().display)
    }

    @Test
    fun repeatedWordStaysAvailableUntilBothCopiesAreHung() {
        val repeated = listOf("Mama", "ist", "Mama")
        val ids = listOf("mama", "ist", "mama")
        val cards = SentenceOrderTray.cards(repeated, ids, emptyList(), listOf("Mama"))
        assertEquals(listOf("ist", "Mama"), cards.map { it.display })
    }

    @Test
    fun pegKeysRoundTrip() {
        assertEquals(0, SentenceOrderTray.pegIndex(SentenceOrderTray.pegKey(0)))
        assertEquals(2, SentenceOrderTray.pegIndex(SentenceOrderTray.pegKey(2)))
        assertNull(SentenceOrderTray.pegIndex("frame-1"))
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.SentenceOrderTrayTest"`
Expected: FAIL — `SentenceOrderTray` existiert nicht.

- [ ] **Step 3: `SentenceOrderTrainer.kt` anlegen**

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.SentenceOrderRound
import app.abcvorschule.content.WordBlock
import app.abcvorschule.progress.ScaffoldLevel
import app.abcvorschule.ui.components.AbcResolveButton
import app.abcvorschule.ui.exercise.drag.DragCard
import app.abcvorschule.ui.exercise.drag.DropZone
import app.abcvorschule.ui.exercise.drag.rememberDragFieldState
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand

object SentenceOrderTray {
    /** A sentence can need more cards than a word, but the tray stays scannable. */
    const val MaxTrayTiles = 6

    fun cards(
        words: List<String>,
        atomIds: List<String>,
        distractors: List<WordBlock>,
        placedDisplays: List<String>,
    ): List<WordBlock> {
        val remaining = words.mapIndexed { index, word ->
            WordBlock(atomId = atomIds.getOrElse(index) { word }, display = word)
        }.toMutableList()
        placedDisplays.forEach { display ->
            val hit = remaining.indexOfFirst { it.display == display }
            if (hit >= 0) remaining.removeAt(hit)
        }
        if (remaining.isEmpty()) return emptyList()
        return (remaining + distractors).take(MaxTrayTiles)
    }

    fun pegKey(index: Int): String = "peg-$index"

    fun pegIndex(key: String): Int? =
        if (key.startsWith("peg-")) key.removePrefix("peg-").toIntOrNull() else null
}

/**
 * Trainer 5 — Satz-Architekt. Word cards are hung on a clothesline in reading
 * order. A one-word round is the same mechanic with a single peg, which is how the
 * curriculum introduces word-to-picture matching in the first lessons.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceOrderTrainer(
    round: SentenceOrderRound,
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
    val roundKey = round.sentenceId
    val field = rememberDragFieldState(roundKey)
    val placed = remember(roundKey) { mutableStateMapOf<Int, String>() }
    var misses by remember(roundKey) { mutableIntStateOf(0) }
    var resolved by remember(roundKey) { mutableStateOf(false) }
    val scoredIds = remember(roundKey) { atomIds.distinct() }
    val cards = SentenceOrderTray.cards(words, atomIds, round.distractors, placed.values.toList())

    fun place(index: Int, card: WordBlock) {
        if (resolved || placed[index] != null) return
        field.select(null)
        if (OrderedPlacement.isCorrectPlacement(index, card.display, words)) {
            placed[index] = card.display
            onSpeak(card.display)
            if (OrderedPlacement.isSolved(placed.toMap(), words)) {
                onResult(true, false, scoredIds)
            }
        } else {
            misses += 1
            onResult(false, false, listOf(card.atomId))
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
            if (!illustrationEmoji.isNullOrBlank()) {
                Text(text = illustrationEmoji, fontSize = 84.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(
                    Modifier
                        .fillMaxWidth()
                        .height(12.dp),
                ) {
                    // A gently sagging line, drawn rather than iconified.
                    drawLine(
                        color = MutedText.copy(alpha = 0.5f),
                        start = Offset(0f, size.height * 0.2f),
                        end = Offset(size.width, size.height * 0.2f),
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    words.forEachIndexed { index, expected ->
                        val filled = if (resolved) expected else placed[index]
                        val atomId = atomIds.getOrElse(index) { expected }
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
                        )
                    }
                }
            }
        },
        answers = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.testTag("sentence_tray"),
            ) {
                if (!resolved) {
                    cards.forEach { card ->
                        val key = cardKey(card)
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
                                    color = if (field.selectedKey == key) SoftMint else NightElevated,
                                    shape = RoundedCornerShape(18.dp),
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                                .testTag("card_${card.display}"),
                        ) {
                            Text(
                                text = card.display,
                                style = MaterialTheme.typography.headlineSmall,
                                color = if (field.selectedKey == key) NightInk else SoftSand,
                            )
                        }
                    }
                }
            }
            if (misses >= 2 && !resolved) {
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

private fun cardKey(card: WordBlock): String = "card-${card.atomId}-${card.display}"

@Composable
private fun Peg(
    index: Int,
    expected: String,
    filled: String?,
    showGhost: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: app.abcvorschule.ui.exercise.drag.DragFieldState,
) {
    DropZone(
        state = registerWith,
        key = SentenceOrderTray.pegKey(index),
        onTap = onTap,
        modifier = Modifier
            .defaultMinSize(minWidth = 76.dp, minHeight = 64.dp)
            .background(
                color = if (armed) SoftMint.copy(alpha = 0.22f) else NightElevated,
                shape = RoundedCornerShape(16.dp),
            )
            .border(
                width = 3.dp,
                color = if (filled != null) SoftMint.copy(alpha = 0.7f) else SoftSand.copy(alpha = 0.32f),
                shape = RoundedCornerShape(16.dp),
            )
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag("peg_$index"),
    ) {
        when {
            filled != null -> Text(
                text = filled,
                style = MaterialTheme.typography.headlineSmall,
                color = SoftSand,
            )
            showGhost -> Text(
                text = expected,
                style = MaterialTheme.typography.headlineSmall,
                color = SoftSand,
                modifier = Modifier.alpha(0.22f),
            )
            else -> Text(
                text = "_",
                style = MaterialTheme.typography.headlineSmall,
                color = SoftSand.copy(alpha = 0.45f),
            )
        }
    }
}
```

- [ ] **Step 4: `TrainerHost.kt` fertigstellen**

Ersetze den letzten Platzhalter und entferne `TrainerPlaceholder` sowie den `else`-Zweig — mit allen sechs Runden-Typen ist das `when` erschöpfend:

```kotlin
        is SentenceOrderRound -> {
            val sentence = pack.sentence(round.sentenceId)
            SentenceOrderTrainer(
                round = round,
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

Damit `SentenceOrderRound`-Atom-IDs auch im Fortschritt landen, muss `TrainerHost` sie an `onResult` durchreichen — das erledigt bereits der Trainer selbst über `scoredIds`. `TrainerRound.scoredAtomIds()` in `TaskSpecs.kt` bleibt für Satzrunden leer und wird nur noch vom `SessionViewModel.schedule()` über `sentenceAtomIds()` ergänzt.

- [ ] **Step 5: Tests und Build laufen lassen**

Run: `./gradlew :app:testDebugUnitTest && ./gradlew :app:assembleDebug`
Expected: beides grün. Prüfe zusätzlich, dass `TrainerHost` keinen `else`-Zweig mehr braucht (Kotlin meldet sonst „when is not exhaustive").

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SentenceOrderTrainer.kt app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt app/src/test/java/app/abcvorschule/ui/exercise/SentenceOrderTrayTest.kt
git commit -m "feat(exercise): add Trainer 5, the sentence architect

Word cards hang on a clothesline in reading order with per-atom ghost scaffolds;
a one-word round is the same mechanic and covers the curriculum's early
word-to-picture matching. Completes the six-trainer host.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 11: Content für Lektionen 2–6 (Phase 1 + 2)

Reines Autoren — kein neuer Code. Nach dieser Task sind sechs Lektionen spielbar und alle sechs Trainer-Engines mit echtem Fibel-Stoff belegt.

**Files:**
- Modify: `app/src/main/assets/content/atoms.json` (12 Buchstaben mit Strichdaten, 10 Silben, Wörter, Bildwörter)
- Modify: `app/src/main/assets/content/sentences.json`
- Modify: `app/src/main/assets/content/tasks.json` (30 neue Tasks: 5 Lektionen × 6 Trainer)
- Modify: `app/src/main/assets/content/lessons.json` (l02–l06 auf `authored`)
- Modify: `app/src/test/resources/content/*.json` (Spiegelung)
- Test: `app/src/test/java/app/abcvorschule/content/LessonCoverageTest.kt` (neu)

**Interfaces:**
- Consumes: Schema und Validator aus Task 1. Keine neuen Typen.
- Produces: keine Code-Interfaces; `pack.authoredLessons.size == 6`.

### Ziel-Wörter je Lektion (aus Teil 2 des didaktischen Konzepts)

| Lektion | Fokus | Verschmelzen | Wort-Bauer | Satz-Architekt | Rechnen-Icon |
|---------|-------|--------------|------------|----------------|--------------|
| l02 | I, O | M+I→mi, M+O→mo | Oma, Mimi | „Oma." | 👵 Oma |
| l03 | P, T | P+A→pa, T+O→to | Papa, Opa | „Papa am Tor." | 🍅 Tomate |
| l04 | L, H | L+A→la, H+O→ho | Lama | „Hallo Lama!" | 🦙 Lama |
| l05 | U, R | R+O→ro, R+U→ru | Tor, rot | „Tom ruft Opa." | ⏰ Uhr |
| l06 | N, S, E, D | S+E→se, D+O→do | Dose, Nest | „Oma ist da." · „Das Lama ist rot." | 🌹 Rose |

**Bewusste Abweichung:** Das Konzept nennt für Lektion 5 „Ufo (U-fo)" als Wort-Bauer-Ziel. „F" wird erst später eingeführt, und der Wort-Bauer darf keine unbekannten Graphem-Kacheln zeigen — deshalb baut l05 `Tor` und `rot`. `Ufo` bleibt Bild-/Hörwort.

---

- [ ] **Step 1: Failing Test für die Lektionsabdeckung schreiben**

Neu `app/src/test/java/app/abcvorschule/content/LessonCoverageTest.kt`:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonCoverageTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun phaseOneAndTwoAreAuthored() {
        assertEquals(
            listOf("l01", "l02", "l03", "l04", "l05", "l06"),
            pack.authoredLessons.map { it.id },
        )
    }

    @Test
    fun lessonsSevenToSixteenStayPlanned() {
        val planned = pack.lessons.filter { it.status == LessonStatus.planned }
        assertEquals(10, planned.size)
        assertTrue(planned.all { it.taskIds.isEmpty() })
    }

    @Test
    fun everyFocusGraphemeHasTraceStrokesAndATraceRound() {
        pack.authoredLessons.forEach { lesson ->
            val traced = (pack.tasksOf(lesson).first { it.kind == TrainerKind.letter_trace }
                as LetterTraceSpec).rounds.map { it.atomId }
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
            assertEquals("lesson ${lesson.id}", 1, math.size)
            assertTrue("lesson ${lesson.id} needs at least two sums", math.single().rounds.size >= 2)
        }
    }

    @Test
    fun rechnenIconsComeFromTheLessonsOwnVocabulary() {
        pack.authoredLessons.forEach { lesson ->
            val math = pack.tasksOf(lesson).filterIsInstance<CountAddSpec>().single()
            val icons = math.rounds.map { it.iconAtomId }.distinct()
            assertEquals("lesson ${lesson.id} should stay on one icon", 1, icons.size)
            assertTrue(pack.atom(icons.single()).emoji.isNotBlank())
        }
    }

    @Test
    fun rechnenIconsCarryAPluralFormForTheSpokenAnswer() {
        // The success readout says "2 Ameisen", so every counted atom needs a plural.
        pack.authoredLessons.forEach { lesson ->
            val math = pack.tasksOf(lesson).filterIsInstance<CountAddSpec>().single()
            math.rounds.forEach { round ->
                val icon = pack.atom(round.iconAtomId)
                assertTrue(
                    "${icon.id} needs pluralDisplay for the spoken answer",
                    !icon.pluralDisplay.isNullOrBlank(),
                )
                assertEquals(
                    "${round.answer} ${icon.pluralDisplay}",
                    round.spokenAnswer(icon),
                )
            }
        }
    }

    @Test
    fun wordBuilderNeverOffersAnUntaughtGrapheme() {
        // A block may only use graphemes/syllables introduced in this or an earlier lesson.
        val introduced = mutableSetOf<String>()
        pack.authoredLessons.forEach { lesson ->
            introduced += lesson.focusAtomIds
            val merges = (pack.tasksOf(lesson).first { it.kind == TrainerKind.syllable_merge }
                as SyllableMergeSpec).rounds
            introduced += merges.map { it.resultAtomId }
            val build = pack.tasksOf(lesson).first { it.kind == TrainerKind.word_build }
                as WordBuildSpec
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

    @Test
    fun sentenceRoundsOnlyUseWordsThatWereBuiltOrIntroduced() {
        val known = mutableSetOf<String>()
        pack.authoredLessons.forEach { lesson ->
            val build = pack.tasksOf(lesson).first { it.kind == TrainerKind.word_build }
                as WordBuildSpec
            known += build.rounds.map { it.targetAtomId }
            val sentences = pack.tasksOf(lesson).first { it.kind == TrainerKind.sentence_order }
                as SentenceOrderSpec
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

    @Test
    fun firstEncounterOfANewWordStaysDistractorFree() {
        val build = pack.tasksOf(pack.lesson("l01")).first { it.kind == TrainerKind.word_build }
            as WordBuildSpec
        assertTrue(build.rounds.all { it.distractors.isEmpty() })
    }
}
```

- [ ] **Step 2: Test laufen lassen und Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.content.LessonCoverageTest"`
Expected: FAIL — nur `l01` ist `authored`.

- [ ] **Step 3: `atoms.json` vollständig ersetzen**

`app/src/main/assets/content/atoms.json`:

```json
{
  "atoms": [
    { "id": "letter-m", "lemma": "M", "display": "M", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.12,0.92],[0.12,0.08],[0.5,0.62],[0.88,0.08],[0.88,0.92]] } ] },
    { "id": "letter-a", "lemma": "A", "display": "A", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.5,0.08],[0.14,0.92]] }, { "points": [[0.5,0.08],[0.86,0.92]] }, { "points": [[0.26,0.66],[0.74,0.66]] } ] },
    { "id": "letter-i", "lemma": "I", "display": "I", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.5,0.08],[0.5,0.92]] } ] },
    { "id": "letter-o", "lemma": "O", "display": "O", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.500,0.080],[0.362,0.112],[0.245,0.203],[0.167,0.339],[0.140,0.500],[0.167,0.661],[0.245,0.797],[0.362,0.888],[0.500,0.920],[0.638,0.888],[0.755,0.797],[0.833,0.661],[0.860,0.500],[0.833,0.339],[0.755,0.203],[0.638,0.112],[0.500,0.080]] } ] },
    { "id": "letter-p", "lemma": "P", "display": "P", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.22,0.92],[0.22,0.08]] }, { "points": [[0.22,0.08],[0.62,0.10],[0.76,0.22],[0.76,0.38],[0.62,0.50],[0.22,0.52]] } ] },
    { "id": "letter-t", "lemma": "T", "display": "T", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.14,0.10],[0.86,0.10]] }, { "points": [[0.5,0.10],[0.5,0.92]] } ] },
    { "id": "letter-l", "lemma": "L", "display": "L", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.24,0.08],[0.24,0.92],[0.80,0.92]] } ] },
    { "id": "letter-h", "lemma": "H", "display": "H", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.18,0.08],[0.18,0.92]] }, { "points": [[0.82,0.08],[0.82,0.92]] }, { "points": [[0.18,0.50],[0.82,0.50]] } ] },
    { "id": "letter-u", "lemma": "U", "display": "U", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.18,0.08],[0.18,0.62],[0.26,0.82],[0.40,0.91],[0.60,0.91],[0.74,0.82],[0.82,0.62],[0.82,0.08]] } ] },
    { "id": "letter-r", "lemma": "R", "display": "R", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.22,0.92],[0.22,0.08]] }, { "points": [[0.22,0.08],[0.62,0.10],[0.74,0.22],[0.74,0.36],[0.62,0.48],[0.22,0.50]] }, { "points": [[0.44,0.50],[0.80,0.92]] } ] },
    { "id": "letter-n", "lemma": "N", "display": "N", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.16,0.92],[0.16,0.08],[0.84,0.92],[0.84,0.08]] } ] },
    { "id": "letter-s", "lemma": "S", "display": "S", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.78,0.18],[0.66,0.09],[0.44,0.08],[0.28,0.15],[0.26,0.30],[0.38,0.42],[0.60,0.52],[0.74,0.64],[0.72,0.82],[0.54,0.92],[0.32,0.91],[0.20,0.80]] } ] },
    { "id": "letter-e", "lemma": "E", "display": "E", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.22,0.08],[0.22,0.92]] }, { "points": [[0.22,0.10],[0.78,0.10]] }, { "points": [[0.22,0.50],[0.68,0.50]] }, { "points": [[0.22,0.92],[0.78,0.92]] } ] },
    { "id": "letter-d", "lemma": "D", "display": "D", "emoji": "", "kind": "letter",
      "strokes": [ { "points": [[0.22,0.92],[0.22,0.08]] }, { "points": [[0.22,0.08],[0.56,0.10],[0.74,0.26],[0.78,0.50],[0.74,0.74],[0.56,0.90],[0.22,0.92]] } ] },

    { "id": "ma", "lemma": "ma", "display": "ma", "emoji": "", "kind": "syllable" },
    { "id": "mi", "lemma": "mi", "display": "mi", "emoji": "", "kind": "syllable" },
    { "id": "mo", "lemma": "mo", "display": "mo", "emoji": "", "kind": "syllable" },
    { "id": "pa", "lemma": "pa", "display": "pa", "emoji": "", "kind": "syllable" },
    { "id": "to", "lemma": "to", "display": "to", "emoji": "", "kind": "syllable" },
    { "id": "la", "lemma": "la", "display": "la", "emoji": "", "kind": "syllable" },
    { "id": "ho", "lemma": "ho", "display": "ho", "emoji": "", "kind": "syllable" },
    { "id": "ro", "lemma": "ro", "display": "ro", "emoji": "", "kind": "syllable" },
    { "id": "ru", "lemma": "ru", "display": "ru", "emoji": "", "kind": "syllable" },
    { "id": "se", "lemma": "se", "display": "se", "emoji": "", "kind": "syllable" },
    { "id": "do", "lemma": "do", "display": "do", "emoji": "", "kind": "syllable" },

    { "id": "mama", "lemma": "Mama", "display": "Mama", "emoji": "👩", "kind": "word" },
    { "id": "oma", "lemma": "Oma", "display": "Oma", "emoji": "👵", "kind": "word", "pluralDisplay": "Omas", "pluralHighlight": "s" },
    { "id": "mimi", "lemma": "Mimi", "display": "Mimi", "emoji": "🐱", "kind": "word" },
    { "id": "papa", "lemma": "Papa", "display": "Papa", "emoji": "👨", "kind": "word" },
    { "id": "opa", "lemma": "Opa", "display": "Opa", "emoji": "👴", "kind": "word" },
    { "id": "tom", "lemma": "Tom", "display": "Tom", "emoji": "👦", "kind": "word" },
    { "id": "tor", "lemma": "Tor", "display": "Tor", "emoji": "🥅", "kind": "word", "pluralDisplay": "Tore", "pluralHighlight": "e" },
    { "id": "rot", "lemma": "rot", "display": "rot", "emoji": "🟥", "kind": "word" },
    { "id": "lama", "lemma": "Lama", "display": "Lama", "emoji": "🦙", "kind": "word", "pluralDisplay": "Lamas", "pluralHighlight": "s" },
    { "id": "hallo", "lemma": "Hallo", "display": "Hallo", "emoji": "👋", "kind": "word" },
    { "id": "dose", "lemma": "Dose", "display": "Dose", "emoji": "🥫", "kind": "word", "pluralDisplay": "Dosen", "pluralHighlight": "n" },
    { "id": "nest", "lemma": "Nest", "display": "Nest", "emoji": "🪺", "kind": "word", "pluralDisplay": "Nester", "pluralHighlight": "er" },
    { "id": "rose", "lemma": "Rose", "display": "Rose", "emoji": "🌹", "kind": "word", "pluralDisplay": "Rosen", "pluralHighlight": "n" },
    { "id": "ist", "lemma": "ist", "display": "ist", "emoji": "", "kind": "word" },
    { "id": "am", "lemma": "am", "display": "am", "emoji": "", "kind": "word" },
    { "id": "da", "lemma": "da", "display": "da", "emoji": "", "kind": "word" },
    { "id": "das", "lemma": "das", "display": "das", "emoji": "", "kind": "word" },
    { "id": "ruft", "lemma": "ruft", "display": "ruft", "emoji": "", "kind": "word" },

    { "id": "ameise", "lemma": "Ameise", "display": "Ameise", "emoji": "🐜", "kind": "other", "pluralDisplay": "Ameisen" },
    { "id": "maus", "lemma": "Maus", "display": "Maus", "emoji": "🐭", "kind": "other", "pluralDisplay": "Mäuse" },
    { "id": "baum", "lemma": "Baum", "display": "Baum", "emoji": "🌳", "kind": "other", "pluralDisplay": "Bäume" },
    { "id": "igel", "lemma": "Igel", "display": "Igel", "emoji": "🦔", "kind": "other", "pluralDisplay": "Igel" },
    { "id": "ofen", "lemma": "Ofen", "display": "Ofen", "emoji": "🔥", "kind": "other", "pluralDisplay": "Öfen" },
    { "id": "radio", "lemma": "Radio", "display": "Radio", "emoji": "📻", "kind": "other", "pluralDisplay": "Radios" },
    { "id": "sonne", "lemma": "Sonne", "display": "Sonne", "emoji": "☀️", "kind": "other", "pluralDisplay": "Sonnen" },
    { "id": "tomate", "lemma": "Tomate", "display": "Tomate", "emoji": "🍅", "kind": "other", "pluralDisplay": "Tomaten" },
    { "id": "ente", "lemma": "Ente", "display": "Ente", "emoji": "🦆", "kind": "other", "pluralDisplay": "Enten" },
    { "id": "hut", "lemma": "Hut", "display": "Hut", "emoji": "🎩", "kind": "other", "pluralDisplay": "Hüte" },
    { "id": "salat", "lemma": "Salat", "display": "Salat", "emoji": "🥗", "kind": "other", "pluralDisplay": "Salate" },
    { "id": "insel", "lemma": "Insel", "display": "Insel", "emoji": "🏝️", "kind": "other", "pluralDisplay": "Inseln" },
    { "id": "uhr", "lemma": "Uhr", "display": "Uhr", "emoji": "⏰", "kind": "other", "pluralDisplay": "Uhren" },
    { "id": "karotte", "lemma": "Karotte", "display": "Karotte", "emoji": "🥕", "kind": "other", "pluralDisplay": "Karotten" },
    { "id": "nase", "lemma": "Nase", "display": "Nase", "emoji": "👃", "kind": "other", "pluralDisplay": "Nasen" },
    { "id": "haus", "lemma": "Haus", "display": "Haus", "emoji": "🏠", "kind": "other", "pluralDisplay": "Häuser" },
    { "id": "paket", "lemma": "Paket", "display": "Paket", "emoji": "📦", "kind": "other", "pluralDisplay": "Pakete" },
    { "id": "mond", "lemma": "Mond", "display": "Mond", "emoji": "🌙", "kind": "other", "pluralDisplay": "Monde" },
    { "id": "ampel", "lemma": "Ampel", "display": "Ampel", "emoji": "🚦", "kind": "other", "pluralDisplay": "Ampeln" }
  ]
}
```

- [ ] **Step 4: `sentences.json` vollständig ersetzen**

`app/src/main/assets/content/sentences.json`:

```json
{
  "sentences": [
    { "id": "s-mama", "atomIds": ["mama"], "tts": "Mama." },
    { "id": "s-oma", "atomIds": ["oma"], "tts": "Oma." },
    { "id": "s-papa-am-tor", "atomIds": ["papa", "am", "tor"], "tts": "Papa am Tor." },
    { "id": "s-hallo-lama", "atomIds": ["hallo", "lama"], "tts": "Hallo Lama!" },
    { "id": "s-tom-ruft-opa", "atomIds": ["tom", "ruft", "opa"], "tts": "Tom ruft Opa." },
    { "id": "s-oma-ist-da", "atomIds": ["oma", "ist", "da"], "tts": "Oma ist da." },
    { "id": "s-das-lama-ist-rot", "atomIds": ["das", "lama", "ist", "rot"], "tts": "Das Lama ist rot." }
  ]
}
```

- [ ] **Step 5: `lessons.json` — l02–l06 auf `authored` heben**

Ersetze die fünf Zeilen l02–l06; l01 und l07–l16 bleiben unverändert:

```json
    {
      "id": "l02", "index": 2, "phase": 1, "title": "I & O", "nodeLabel": "I o",
      "status": "authored", "focusAtomIds": ["letter-i", "letter-o"],
      "taskIds": ["l02-t1", "l02-t2", "l02-t3", "l02-t4", "l02-t5", "l02-t6"]
    },
    {
      "id": "l03", "index": 3, "phase": 2, "title": "P & T", "nodeLabel": "P t",
      "status": "authored", "focusAtomIds": ["letter-p", "letter-t"],
      "taskIds": ["l03-t1", "l03-t2", "l03-t3", "l03-t4", "l03-t5", "l03-t6"]
    },
    {
      "id": "l04", "index": 4, "phase": 2, "title": "L & H", "nodeLabel": "L h",
      "status": "authored", "focusAtomIds": ["letter-l", "letter-h"],
      "taskIds": ["l04-t1", "l04-t2", "l04-t3", "l04-t4", "l04-t5", "l04-t6"]
    },
    {
      "id": "l05", "index": 5, "phase": 2, "title": "U & R", "nodeLabel": "U r",
      "status": "authored", "focusAtomIds": ["letter-u", "letter-r"],
      "taskIds": ["l05-t1", "l05-t2", "l05-t3", "l05-t4", "l05-t5", "l05-t6"]
    },
    {
      "id": "l06", "index": 6, "phase": 2, "title": "N, S, E, D", "nodeLabel": "N s e d",
      "status": "authored",
      "focusAtomIds": ["letter-n", "letter-s", "letter-e", "letter-d"],
      "taskIds": ["l06-t1", "l06-t2", "l06-t3", "l06-t4", "l06-t5", "l06-t6"]
    },
```

- [ ] **Step 6: `l01-t2` Reihenfolge an `focusAtomIds` anpassen**

`LessonCoverageTest.everyFocusGraphemeHasTraceStrokesAndATraceRound` verlangt, dass der Trace-Trainer genau die Fokus-Graphem in der Reihenfolge von `focusAtomIds` durchläuft. In `tasks.json` bei `l01-t2` die beiden Runden tauschen, sodass **M vor A** kommt (`focusAtomIds` von l01 ist `["letter-m","letter-a"]`):

```json
    {
      "trainer": "letter_trace",
      "id": "l01-t2",
      "rounds": [
        {
          "promptTts": "Spure das große M nach und sammle alle Sterne.",
          "atomId": "letter-m",
          "glyph": "M",
          "rewardTts": "M wie Mond.",
          "rewardEmoji": "🌙"
        },
        {
          "promptTts": "Spure das große A nach und sammle alle Sterne.",
          "atomId": "letter-a",
          "glyph": "A",
          "rewardTts": "A wie Ampel.",
          "rewardEmoji": "🚦"
        }
      ]
    },
```

- [ ] **Step 7: Lektion 2 (I & O) in `tasks.json` ergänzen**

```json
    {
      "trainer": "sound_position", "id": "l02-t1", "phonemeTts": "Ooo",
      "rounds": [
        { "promptTts": "Wir suchen das Ooo. Wo versteckt sich das Ooo?", "atomId": "ofen", "slot": "start", "missTts": "Ooo - fen. Hörst du das Ooo am Anfang?" },
        { "promptTts": "Wo versteckt sich das Ooo?", "atomId": "radio", "slot": "end", "missTts": "Radi - Ooo. Hörst du das Ooo am Ende?" },
        { "promptTts": "Wo versteckt sich das Ooo?", "atomId": "sonne", "slot": "middle", "missTts": "S - Ooo - nne. Hörst du das Ooo in der Mitte?" }
      ]
    },
    {
      "trainer": "letter_trace", "id": "l02-t2",
      "rounds": [
        { "promptTts": "Spure das große I nach und sammle alle Sterne.", "atomId": "letter-i", "glyph": "I", "rewardTts": "I wie Igel.", "rewardEmoji": "🦔" },
        { "promptTts": "Spure das große O nach und sammle alle Sterne.", "atomId": "letter-o", "glyph": "O", "rewardTts": "O wie Ofen.", "rewardEmoji": "🔥" }
      ]
    },
    {
      "trainer": "syllable_merge", "id": "l02-t3",
      "rounds": [
        { "promptTts": "Lass die Buchstaben zusammenrutschen.", "leftAtomId": "letter-m", "leftDisplay": "m", "rightAtomId": "letter-i", "rightDisplay": "i", "resultAtomId": "mi", "resultDisplay": "mi", "stretchTts": "Mmmmm" },
        { "promptTts": "Und jetzt mit dem O.", "leftAtomId": "letter-m", "leftDisplay": "m", "rightAtomId": "letter-o", "rightDisplay": "o", "resultAtomId": "mo", "resultDisplay": "mo", "stretchTts": "Mmmmm" }
      ]
    },
    {
      "trainer": "word_build", "id": "l02-t4",
      "rounds": [
        { "promptTts": "Kannst du das Wort Oma bauen?", "targetAtomId": "oma",
          "blocks": [ { "atomId": "letter-o", "display": "O" }, { "atomId": "ma", "display": "ma" } ],
          "distractors": [ { "atomId": "mi", "display": "Mi" } ] },
        { "promptTts": "Die Katze heißt Mimi. Baue Mimi.", "targetAtomId": "mimi",
          "blocks": [ { "atomId": "mi", "display": "Mi" }, { "atomId": "mi", "display": "mi" } ],
          "distractors": [ { "atomId": "ma", "display": "Ma" } ] }
      ]
    },
    {
      "trainer": "sentence_order", "id": "l02-t5",
      "rounds": [
        { "promptTts": "Oma. Hänge das Wort zum Bild.", "sentenceId": "s-oma", "illustrationAtomId": "oma",
          "distractors": [ { "atomId": "mama", "display": "Mama" } ] }
      ]
    },
    {
      "trainer": "count_add", "id": "l02-t6",
      "rounds": [
        { "promptTts": "Eine Oma und zwei Omas. Wie viele Omas?", "iconAtomId": "oma", "left": 1, "right": 2, "answer": 3 },
        { "promptTts": "Zwei Omas und zwei Omas. Wie viele Omas?", "iconAtomId": "oma", "left": 2, "right": 2, "answer": 4 }
      ]
    },
```

- [ ] **Step 8: Lektion 3 (P & T) in `tasks.json` ergänzen**

```json
    {
      "trainer": "sound_position", "id": "l03-t1", "phonemeTts": "T",
      "rounds": [
        { "promptTts": "Wir suchen das T. Wo versteckt sich das T?", "atomId": "tomate", "slot": "start", "missTts": "T - omate. Hörst du das T am Anfang?" },
        { "promptTts": "Wo versteckt sich das T?", "atomId": "ente", "slot": "middle", "missTts": "En - T - e. Hörst du das T in der Mitte?" },
        { "promptTts": "Wo versteckt sich das T?", "atomId": "hut", "slot": "end", "missTts": "Hu - T. Hörst du das T am Ende?" }
      ]
    },
    {
      "trainer": "letter_trace", "id": "l03-t2",
      "rounds": [
        { "promptTts": "Spure das große P nach und sammle alle Sterne.", "atomId": "letter-p", "glyph": "P", "rewardTts": "P wie Paket.", "rewardEmoji": "📦" },
        { "promptTts": "Spure das große T nach und sammle alle Sterne.", "atomId": "letter-t", "glyph": "T", "rewardTts": "T wie Tomate.", "rewardEmoji": "🍅" }
      ]
    },
    {
      "trainer": "syllable_merge", "id": "l03-t3",
      "rounds": [
        { "promptTts": "Lass die Buchstaben zusammenrutschen.", "leftAtomId": "letter-p", "leftDisplay": "p", "rightAtomId": "letter-a", "rightDisplay": "a", "resultAtomId": "pa", "resultDisplay": "pa", "stretchTts": "P" },
        { "promptTts": "Und jetzt T und O.", "leftAtomId": "letter-t", "leftDisplay": "t", "rightAtomId": "letter-o", "rightDisplay": "o", "resultAtomId": "to", "resultDisplay": "to", "stretchTts": "T" }
      ]
    },
    {
      "trainer": "word_build", "id": "l03-t4",
      "rounds": [
        { "promptTts": "Kannst du das Wort Papa bauen?", "targetAtomId": "papa",
          "blocks": [ { "atomId": "pa", "display": "Pa" }, { "atomId": "pa", "display": "pa" } ],
          "distractors": [ { "atomId": "ma", "display": "Ma" } ] },
        { "promptTts": "Und jetzt das Wort Opa.", "targetAtomId": "opa",
          "blocks": [ { "atomId": "letter-o", "display": "O" }, { "atomId": "pa", "display": "pa" } ],
          "distractors": [ { "atomId": "ma", "display": "Ma" } ] }
      ]
    },
    {
      "trainer": "sentence_order", "id": "l03-t5",
      "rounds": [
        { "promptTts": "Papa am Tor. Hänge die Wörter auf.", "sentenceId": "s-papa-am-tor", "illustrationAtomId": "tor",
          "distractors": [ { "atomId": "mama", "display": "Mama" } ],
          "holisticAtomIds": ["tor"] }
      ]
    },
    {
      "trainer": "count_add", "id": "l03-t6",
      "rounds": [
        { "promptTts": "Eine Tomate und drei Tomaten. Wie viele Tomaten?", "iconAtomId": "tomate", "left": 1, "right": 3, "answer": 4 },
        { "promptTts": "Drei Tomaten und zwei Tomaten. Wie viele Tomaten?", "iconAtomId": "tomate", "left": 3, "right": 2, "answer": 5 }
      ]
    },
```

- [ ] **Step 9: Lektion 4 (L & H) in `tasks.json` ergänzen**

```json
    {
      "trainer": "sound_position", "id": "l04-t1", "phonemeTts": "Lll",
      "rounds": [
        { "promptTts": "Wir suchen das Lll. Wo versteckt sich das Lll?", "atomId": "lama", "slot": "start", "missTts": "Lll - ama. Hörst du das Lll am Anfang?" },
        { "promptTts": "Wo versteckt sich das Lll?", "atomId": "salat", "slot": "middle", "missTts": "Sa - Lll - at. Hörst du das Lll in der Mitte?" },
        { "promptTts": "Wo versteckt sich das Lll?", "atomId": "insel", "slot": "end", "missTts": "Inse - Lll. Hörst du das Lll am Ende?" }
      ]
    },
    {
      "trainer": "letter_trace", "id": "l04-t2",
      "rounds": [
        { "promptTts": "Spure das große L nach und sammle alle Sterne.", "atomId": "letter-l", "glyph": "L", "rewardTts": "L wie Lama.", "rewardEmoji": "🦙" },
        { "promptTts": "Spure das große H nach und sammle alle Sterne.", "atomId": "letter-h", "glyph": "H", "rewardTts": "H wie Hut.", "rewardEmoji": "🎩" }
      ]
    },
    {
      "trainer": "syllable_merge", "id": "l04-t3",
      "rounds": [
        { "promptTts": "Lass die Buchstaben zusammenrutschen.", "leftAtomId": "letter-l", "leftDisplay": "l", "rightAtomId": "letter-a", "rightDisplay": "a", "resultAtomId": "la", "resultDisplay": "la", "stretchTts": "Llllll" },
        { "promptTts": "Das H ist nur ein Hauch. H und O.", "leftAtomId": "letter-h", "leftDisplay": "h", "rightAtomId": "letter-o", "rightDisplay": "o", "resultAtomId": "ho", "resultDisplay": "ho", "stretchTts": "Hhh" }
      ]
    },
    {
      "trainer": "word_build", "id": "l04-t4",
      "rounds": [
        { "promptTts": "Kannst du das Wort Lama bauen?", "targetAtomId": "lama",
          "blocks": [ { "atomId": "la", "display": "La" }, { "atomId": "ma", "display": "ma" } ],
          "distractors": [ { "atomId": "pa", "display": "Pa" }, { "atomId": "ho", "display": "ho" } ] }
      ]
    },
    {
      "trainer": "sentence_order", "id": "l04-t5",
      "rounds": [
        { "promptTts": "Hallo Lama! Hänge die Wörter auf.", "sentenceId": "s-hallo-lama", "illustrationAtomId": "lama",
          "distractors": [ { "atomId": "mama", "display": "Mama" } ],
          "holisticAtomIds": ["hallo"] }
      ]
    },
    {
      "trainer": "count_add", "id": "l04-t6",
      "rounds": [
        { "promptTts": "Ein Lama und ein Lama. Wie viele Lamas?", "iconAtomId": "lama", "left": 1, "right": 1, "answer": 2 },
        { "promptTts": "Vier Lamas und zwei Lamas. Wie viele Lamas?", "iconAtomId": "lama", "left": 4, "right": 2, "answer": 6 }
      ]
    },
```

- [ ] **Step 10: Lektion 5 (U & R) in `tasks.json` ergänzen**

```json
    {
      "trainer": "sound_position", "id": "l05-t1", "phonemeTts": "Rrr",
      "rounds": [
        { "promptTts": "Wir suchen das Rrr. Wo versteckt sich das Rrr?", "atomId": "rose", "slot": "start", "missTts": "Rrr - ose. Hörst du das Rrr am Anfang?" },
        { "promptTts": "Wo versteckt sich das Rrr?", "atomId": "karotte", "slot": "middle", "missTts": "Ka - Rrr - otte. Hörst du das Rrr in der Mitte?" },
        { "promptTts": "Wo versteckt sich das Rrr?", "atomId": "uhr", "slot": "end", "missTts": "Uh - Rrr. Hörst du das Rrr am Ende?" }
      ]
    },
    {
      "trainer": "letter_trace", "id": "l05-t2",
      "rounds": [
        { "promptTts": "Spure das große U nach und sammle alle Sterne.", "atomId": "letter-u", "glyph": "U", "rewardTts": "U wie Uhr.", "rewardEmoji": "⏰" },
        { "promptTts": "Spure das große R nach und sammle alle Sterne.", "atomId": "letter-r", "glyph": "R", "rewardTts": "R wie Rose.", "rewardEmoji": "🌹" }
      ]
    },
    {
      "trainer": "syllable_merge", "id": "l05-t3",
      "rounds": [
        { "promptTts": "Lass die Buchstaben zusammenrutschen.", "leftAtomId": "letter-r", "leftDisplay": "r", "rightAtomId": "letter-o", "rightDisplay": "o", "resultAtomId": "ro", "resultDisplay": "ro", "stretchTts": "Rrrrr" },
        { "promptTts": "Und jetzt R und U.", "leftAtomId": "letter-r", "leftDisplay": "r", "rightAtomId": "letter-u", "rightDisplay": "u", "resultAtomId": "ru", "resultDisplay": "ru", "stretchTts": "Rrrrr" }
      ]
    },
    {
      "trainer": "word_build", "id": "l05-t4",
      "rounds": [
        { "promptTts": "Kannst du das Wort Tor bauen?", "targetAtomId": "tor",
          "blocks": [ { "atomId": "letter-t", "display": "T" }, { "atomId": "letter-o", "display": "o" }, { "atomId": "letter-r", "display": "r" } ],
          "distractors": [ { "atomId": "letter-u", "display": "u" } ] },
        { "promptTts": "Und jetzt das Wort rot.", "targetAtomId": "rot",
          "blocks": [ { "atomId": "letter-r", "display": "r" }, { "atomId": "letter-o", "display": "o" }, { "atomId": "letter-t", "display": "t" } ],
          "distractors": [ { "atomId": "letter-u", "display": "u" } ] }
      ]
    },
    {
      "trainer": "sentence_order", "id": "l05-t5",
      "rounds": [
        { "promptTts": "Tom ruft Opa. Hänge die Wörter auf.", "sentenceId": "s-tom-ruft-opa", "illustrationAtomId": "tom",
          "distractors": [ { "atomId": "mama", "display": "Mama" } ],
          "holisticAtomIds": ["tom"] }
      ]
    },
    {
      "trainer": "count_add", "id": "l05-t6",
      "rounds": [
        { "promptTts": "Zwei Uhren und drei Uhren. Wie viele Uhren?", "iconAtomId": "uhr", "left": 2, "right": 3, "answer": 5 },
        { "promptTts": "Fünf Uhren und zwei Uhren. Wie viele Uhren?", "iconAtomId": "uhr", "left": 5, "right": 2, "answer": 7 }
      ]
    },
```

- [ ] **Step 11: Lektion 6 (N, S, E, D) in `tasks.json` ergänzen**

```json
    {
      "trainer": "sound_position", "id": "l06-t1", "phonemeTts": "Sss",
      "rounds": [
        { "promptTts": "Wir suchen das Sss. Wo versteckt sich das Sss?", "atomId": "sonne", "slot": "start", "missTts": "Sss - onne. Hörst du das Sss am Anfang?" },
        { "promptTts": "Wo versteckt sich das Sss?", "atomId": "nase", "slot": "middle", "missTts": "Na - Sss - e. Hörst du das Sss in der Mitte?" },
        { "promptTts": "Wo versteckt sich das Sss?", "atomId": "haus", "slot": "end", "missTts": "Hau - Sss. Hörst du das Sss am Ende?" }
      ]
    },
    {
      "trainer": "letter_trace", "id": "l06-t2",
      "rounds": [
        { "promptTts": "Spure das große N nach und sammle alle Sterne.", "atomId": "letter-n", "glyph": "N", "rewardTts": "N wie Nase.", "rewardEmoji": "👃" },
        { "promptTts": "Spure das große S nach und sammle alle Sterne.", "atomId": "letter-s", "glyph": "S", "rewardTts": "S wie Sonne.", "rewardEmoji": "☀️" },
        { "promptTts": "Spure das große E nach und sammle alle Sterne.", "atomId": "letter-e", "glyph": "E", "rewardTts": "E wie Ente.", "rewardEmoji": "🦆" },
        { "promptTts": "Spure das große D nach und sammle alle Sterne.", "atomId": "letter-d", "glyph": "D", "rewardTts": "D wie Dose.", "rewardEmoji": "🥫" }
      ]
    },
    {
      "trainer": "syllable_merge", "id": "l06-t3",
      "rounds": [
        { "promptTts": "Lass die Buchstaben zusammenrutschen.", "leftAtomId": "letter-s", "leftDisplay": "s", "rightAtomId": "letter-e", "rightDisplay": "e", "resultAtomId": "se", "resultDisplay": "se", "stretchTts": "Sssss" },
        { "promptTts": "Und jetzt D und O.", "leftAtomId": "letter-d", "leftDisplay": "d", "rightAtomId": "letter-o", "rightDisplay": "o", "resultAtomId": "do", "resultDisplay": "do", "stretchTts": "D" }
      ]
    },
    {
      "trainer": "word_build", "id": "l06-t4",
      "rounds": [
        { "promptTts": "Kannst du das Wort Dose bauen?", "targetAtomId": "dose",
          "blocks": [ { "atomId": "do", "display": "Do" }, { "atomId": "se", "display": "se" } ],
          "distractors": [ { "atomId": "ro", "display": "ro" }, { "atomId": "ma", "display": "Ma" } ] },
        { "promptTts": "Und jetzt das Wort Nest.", "targetAtomId": "nest",
          "blocks": [ { "atomId": "letter-n", "display": "N" }, { "atomId": "letter-e", "display": "e" }, { "atomId": "letter-s", "display": "s" }, { "atomId": "letter-t", "display": "t" } ],
          "distractors": [ { "atomId": "letter-o", "display": "o" } ] }
      ]
    },
    {
      "trainer": "sentence_order", "id": "l06-t5",
      "rounds": [
        { "promptTts": "Oma ist da. Hänge die Wörter auf.", "sentenceId": "s-oma-ist-da", "illustrationAtomId": "oma",
          "distractors": [ { "atomId": "mama", "display": "Mama" } ] },
        { "promptTts": "Das Lama ist rot. Hänge die Wörter auf.", "sentenceId": "s-das-lama-ist-rot", "illustrationAtomId": "lama",
          "distractors": [ { "atomId": "papa", "display": "Papa" } ] }
      ]
    },
    {
      "trainer": "count_add", "id": "l06-t6",
      "rounds": [
        { "promptTts": "Eine Rose und vier Rosen. Wie viele Rosen?", "iconAtomId": "rose", "left": 1, "right": 4, "answer": 5 },
        { "promptTts": "Vier Rosen und vier Rosen. Wie viele Rosen?", "iconAtomId": "rose", "left": 4, "right": 4, "answer": 8 }
      ]
    }
```

- [ ] **Step 12: Test-Resources spiegeln**

```bash
cp app/src/main/assets/content/atoms.json     app/src/test/resources/content/atoms.json
cp app/src/main/assets/content/sentences.json app/src/test/resources/content/sentences.json
cp app/src/main/assets/content/tasks.json     app/src/test/resources/content/tasks.json
cp app/src/main/assets/content/lessons.json   app/src/test/resources/content/lessons.json
```

- [ ] **Step 13: Alle Tests laufen lassen und grün bestätigen**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS. Der Validator prüft dabei automatisch, dass jede Wort-Bauer-Kachelfolge das Zielwort buchstabiert, jede Verschmelzung die Ergebnissilbe ergibt und jede Summe stimmt — Autorenfehler in den 30 neuen Tasks fallen hier auf.

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 14: Commit**

```bash
git add app/src/main/assets/content app/src/test/resources/content app/src/test/java/app/abcvorschule/content/LessonCoverageTest.kt
git commit -m "feat(content): author Fibel lessons 2-6 across all six trainers

Adds twelve graphemes with stroke data, ten syllables, the phase 1+2 target words
and sentences, and one Rechnen trainer per lesson using that lesson's own picture
icons. Lesson 5 builds Tor/rot instead of Ufo because F is not taught yet.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Task 12: Dokumentation, Aufräumen und Gesamtverifikation

`AGENTS.md` verpflichtet den Agenten, Doku bei geänderten UX-/Content-/Progressions-Regeln von sich aus mitzuziehen. Dieser Umbau ändert alle drei.

**Files:**
- Modify: `docs/PRODUCT_PRINCIPLES.md`
- Modify: `AGENTS.md`
- Modify: `README.md`
- Modify: `docs/plans/2026-07-26-001-feat-abc-vorschul-app-plan.md` (Addendum A2)
- Modify: `docs/residual-review-findings/feat-abc-vorschul-app.md`
- Modify: `.cursor/rules/*` (falls dort Trainer-/Content-Regeln stehen)

**Interfaces:** keine.

---

- [ ] **Step 1: Restbestände der alten Trainer suchen**

```bash
git grep -n -E "speech_cloze|sentence_cloze|DragSlotBoard|ScaffoldMapping|DistractorPicker|SessionScheduler|packIntroCompleted|Sprich mit|composeParts|tierRank|TaskTemplate" -- app docs README.md AGENTS.md .cursor || echo "clean"
```
Expected: nur noch Treffer in `docs/plans/2026-07-26-001-…-plan.md` (historisches Artefakt) und im neuen Plan. Jeder Treffer in `app/`, `README.md`, `AGENTS.md` oder `.cursor/` ist aufzuräumen.

- [ ] **Step 2: `docs/PRODUCT_PRINCIPLES.md` aktualisieren**

Abschnitt 3 („Lernprogression (Fibel)") durch die Trainer- und Lektionslogik ersetzen:

```markdown
## 3. Lernprogression (Fibel-Lernpfad)

Der Lehrplan besteht aus 16 Lektionen in fünf Phasen (Fibel-Reihenfolge).
Jede Lektion führt **genau sechs Trainer in fester Reihenfolge** durch:

1. **Auditiver Finder** — Laut im gesprochenen Wort verorten (Lok mit Anfang/Mitte/Ende-Waggon).
2. **Visueller Spurensucher** — Graphem nachspuren, Sterne in Strichreihenfolge sammeln.
3. **Silben-Verschmelzer** — Konsonant auf Vokal ziehen, Silbe entsteht.
4. **Wort-Bauer** — Silben-/Buchstabenklötze in Schablonen unter dem Bild.
5. **Satz-Architekt** — Wortschilder an die Wäscheleine; Einwort-Runden sind Wort-Bild-Zuordnung.
6. **Rechnen** — reine Mengen-Arithmetik in *jeder* Lektion, Icons aus dem Wortschatz
   derselben Lektion. **Keine Wörter zum Lesen oder Schreiben**; Singular/Plural nur gesprochen.

Reihenfolge-Regeln, die Content und Validator erzwingen:

- Der Wort-Bauer zeigt nie ein Graphem oder eine Silbe, die noch nicht eingeführt wurde.
- Satzrunden nutzen nur gebaute Wörter, kleingeschriebene Funktionswörter oder ausdrücklich
  als `holisticAtomIds` markierte Ganzwort-Bilder (so führt die Fibel z. B. „Tor" vor dem R ein).
- Lektion *n* wird erst frei, wenn Lektion *n−1* gemeistert ist (jeder Trainer mindestens einmal richtig).
- Gemeisterte Lektionen bleiben zum Wiederholen antippbar.
```

Abschnitt 5 („Session-Modell") ersetzen:

```markdown
## 5. Session-Modell

- **Pfad-Screen ist der Einstieg** (winkende S-Kurve, ein Knoten pro Lektion, Label = Graphem).
  Gesperrte und noch nicht autorierte Knoten reagieren auf Tippen mit einem gesprochenen Hinweis —
  niemals mit einem stummen No-Op.
- Tippen auf einen freigeschalteten Knoten startet die Sechs-Trainer-Session dieser Lektion.
- Kein Domänen-Mix, keine Zufallsrotation: die Trainer-Reihenfolge ist didaktisch fix.
- Vor/Zurück zwischen Runden ist **immer** möglich, unabhängig von Punkten/Fortschritt.
- Fortschritt speichern nach jeder Antwort; unfertige Lektion wird beim Öffnen fortgesetzt.
- Back in der Übung → Belohnungszusammenfassung (oder direkt zum Pfad, wenn noch keine Punkte);
  Back auf dem Pfad verlässt die App, ohne Fortschritt zu löschen.
```

Abschnitt 8 („Mathematik-Visuals") um die Nutzerentscheidung ergänzen:

```markdown
- Rechnen läuft in **jeder** Lektion, mit den Bild-Ikonen derselben Lektion (kontextnah, aber
  nicht zwingend — Kinder erkennen die Icons ohnehin).
- Im Rechen-Trainer gibt es **keine Wörter zum Lesen oder Schreiben**. Singular/Plural wird
  ausschließlich gesprochen geübt — im Prompt *und* in der vorgesprochenen Antwort:
  „zwei Ameisen", nicht nur „zwei". Bei Ergebnis 1 die Singularform.
```

Abschnitt 11 („Was bewusst nicht in v1 gehört") ergänzen:

```markdown
- Sprech-Trainer mit „Sprich mit!"-Cue (zurückgezogen — das didaktische Konzept kennt keinen
  eigenen Sprech-Trainer; TTS bleibt für Prompts und Vorlesen).
- Lese-Cloze/Wortfolge als eigenständige Trainer (durch Trainer 1–5 ersetzt).
```

Und die Ableitungstabelle am Dateiende erweitern:

```markdown
| Zeigt der Wort-Bauer ein noch nicht eingeführtes Graphem? | Nein → Fibel-Reihenfolge |
| Enthält der Rechen-Trainer Lesewörter? | Nein → nur Icons und Ziffern |
| Hat jede autorierte Lektion genau die sechs Trainer in Reihenfolge? | Ja → Validator prüft das |
```

- [ ] **Step 3: `AGENTS.md` „Kind-UI-Regeln (Kurz)" aktualisieren**

Ersetze die veralteten Zeilen (Buchstaben-Frames, Wort-Spell, Lückentext-Sätze, Antwort-Tray) durch:

```markdown
- Sechs Trainer pro Lektion in fester Reihenfolge: Auditiver Finder · Spurensucher · Verschmelzer · Wort-Bauer · Satz-Architekt · Rechnen.
- Pfad-Screen ist der Einstieg; gesperrte Knoten antworten mit gesprochenem Hinweis, nie stumm.
- Trainer 1: Lok mit Anfang/Mitte/Ende-Waggon; Miss spielt das segmentierte Wort (`missTts`).
- Trainer 2: Straße aus autorierten `Atom.strokes`; Sterne nur in Strichreihenfolge; Korridor-Verlassen stoppt das Fahrzeug (haptisch), zählt aber nicht als Fehlversuch.
- Trainer 3: Verschmelzen erst nahe am Vokal; kurzer Zug rutscht straffrei zurück; Tap-Alternative Pflicht.
- Trainer 4/5: Rahmen bzw. Wäscheleine tragen das Gerüst pro Atom (Silhouette/Ghost vs. leer); Distraktoren sind **im Content autoriert**, Tray ≤ 5 (Wort) bzw. ≤ 6 (Satz).
- Rechnen: 3 Antworten (visuell) bzw. System-Zahlentastatur + CTA-Absenden-Pfeil; in jeder Lektion; keine Lesewörter; Miss-Feedback nur gesprochen.
- Drag committet nur bei echtem Zonentreffer (größte Überlappung), sonst Snap-back.
- Vor/Zurück zwischen Runden ist immer aktiv, unabhängig von Punkten/Fortschritt.
- Aufgabe oben mittig, Antworten unten mittig (`ExerciseStage` / Design-Komponenten).
```

Ergänze unter „Technik-Kurzüberblick":

```markdown
- Content-Schema v2: ein polymorpher `TaskSpec` pro Trainer (`trainer`-Diskriminator), Lektionen in `lessons.json`
- Lektions-Freischaltung wird aus `taskStats` abgeleitet (`progress/LessonGating.kt`) — keine Extra-Persistenz
```

- [ ] **Step 4: `README.md` aktualisieren**

Ersetze das Content-/Session-Kapitel und das manuelle Smoke-Skript:

```markdown
## Content-Pack (Schema v2)

`app/src/main/assets/content/`

| Datei | Inhalt |
|-------|--------|
| `pack.manifest.json` | `schemaVersion`, `packId`, Titel, Locale |
| `atoms.json` | Buchstaben (mit Strichdaten für den Spurensucher), Silben, Wörter, Bildwörter |
| `sentences.json` | Sätze als Atom-Folgen |
| `tasks.json` | Ein Eintrag pro Trainer, `trainer`-Feld als Typ-Diskriminator, 1..n Runden |
| `lessons.json` | 16 Lektionen in Fibel-Reihenfolge; `authored` = spielbar, `planned` = Knoten gesperrt |

Autoriert: Lektionen 1–6 (Phase 1+2). Lektionen 7–16 sind als gesperrte Pfad-Knoten angelegt und
brauchen nur noch Content — keinen Code.

Der Validator lehnt ein Pack ab, wenn eine autorierte Lektion nicht genau die sechs Trainer in
Reihenfolge enthält, Kachelfolgen das Zielwort nicht buchstabieren, eine Summe nicht stimmt,
Strichdaten fehlen oder Referenzen ins Leere zeigen.

## Offline-Smoke-Skript (manuell)

1. `./gradlew :app:installDebug`, Gerät in den Flugmodus.
2. App öffnen → **Pfad-Screen** erscheint, Lektion 1 pulsiert, Lektionen 2–16 sind gesperrt.
3. Gesperrten Knoten antippen → gesprochener Hinweis, kein stummes No-Op.
4. **Drag-/Tap-Koexistenz prüfen** (statisch nicht verifizierbar): in einem Drag-Trainer
   erst eine Kachel *antippen* (muss sie auswählen und vorlesen, nicht als Drag zählen),
   dann eine Kachel *ziehen* (muss ziehen, nicht als Tap feuern), und beim Ziehen prüfen,
   dass die Kachel **über** den Zielrahmen gezeichnet wird (z-Order).
5. Lektion 1 öffnen und alle sechs Trainer durchspielen:
   Waggon-Zuordnung · Buchstaben nachspuren (Korridor verlassen → Fahrzeug stoppt) ·
   Silbe verschmelzen · Mama bauen · Wortschild aufhängen · zwei Rechenaufgaben.
6. Bei jeder richtigen Antwort: Antwort wird vorgesprochen → Stern oben → dann nächste Runde.
7. Eine Rechenaufgabe zweimal falsch beantworten → gesprochener Hinweis, danach **Auflösen** nutzen:
   keine Punkte, Session läuft weiter.
8. Langer Druck auf ⋯ → Hilfestufe **Ohne Hilfe** erzwingen → nächste Rechenrunde zeigt die
   System-Zahlentastatur; **Mit Hilfe** → drei visuelle Antworten.
9. Mitten in der Lektion App killen und neu öffnen → dieselbe Lektion, dieselbe Runde.
10. Lektion beenden → Belohnungszusammenfassung → Weiter → zurück auf dem Pfad, Lektion 1
   als gemeistert markiert, Lektion 2 freigeschaltet.
11. Back auf dem Pfad verlässt die App; erneutes Öffnen zeigt den Fortschritt unverändert.
```

- [ ] **Step 5: Addendum A2 an den alten Plan anhängen**

An `docs/plans/2026-07-26-001-feat-abc-vorschul-app-plan.md` anhängen:

```markdown
---

## Addendum A2 (2026-07-26): Trainer-Neuschnitt und implementierter Lernpfad

**Status:** Umgesetzt durch [`2026-07-26-002-feat-trainer-typen-und-lernpfad-plan.md`](2026-07-26-002-feat-trainer-typen-und-lernpfad-plan.md).
Dieses Artefakt bleibt als Produkt-Contract gültig, mit folgenden ausdrücklichen Änderungen:

- **R5 ersetzt.** Cloze/Wortfolge als Lese-Mechanik weicht den fünf didaktischen Trainern
  (Auditiver Finder, Visueller Spurensucher, Silben-Verschmelzer, Wort-Bauer, Satz-Architekt).
  Gerüste pro Atom, „nur Lösungskacheln" und die Tap-Alternative bleiben unverändert gültig.
- **R6 zurückgezogen.** Die Sprech-Domäne inklusive „Sprich mit!"-Cue und `speech_cloze` entfällt.
  Das didaktische Konzept kennt keinen eigenen Sprech-Trainer; System-TTS bleibt für Prompts,
  Vorlesen und Fehler-Feedback. AE7 entfällt damit ebenfalls.
- **R16 / F1 ersetzt.** Statt eines domänenrotierenden Fünfer-Mix läuft pro Lektion eine
  Session aus genau sechs Trainern in fester didaktischer Reihenfolge. Punktevergabe,
  Resolve-Semantik (R10), Fortschrittsspeicherung und die Belohnungszusammenfassung bleiben.
- **R4 präzisiert.** Die Fibel-Reihenfolge lebt jetzt in `lessons.json` (16 Lektionen, 5 Phasen)
  statt in atomaren Prerequisites; `Atom.prerequisites` ist entfallen. Die Reihenfolge wird
  durch Lektions-Freischaltung plus Validator-Regeln erzwungen.
- **Addendum A1 umgesetzt und in einem Punkt korrigiert.** Der Pfad ist der Einstieg, wie in A1
  entschieden — aber die Knoten sind **Lektionen**, nicht einzelne Lese-Atome. Damit ist A1s
  blockierende offene Frage („Math und Speech passen nicht in einen linearen Atom-Pfad") gelöst:
  Rechnen ist Trainer 6 in *jeder* Lektion, Sprechen entfällt. Kein „Freies Üben"-Zweitmodus,
  kein separater Rechen-Strang.
- **Rechnen ohne Lesewörter.** Nutzerentscheidung: Trainer 5 Variante B (Mathe-Plural mit
  „Haus"/„Häuser"-Wortkarten) aus dem didaktischen Konzept wird **nicht** gebaut. Der
  Rechen-Trainer zeigt nur Icons und Ziffern; Singular/Plural wird gesprochen geübt.
- **AE-Abdeckung neu:** AE1/AE2 → Wort-Bauer- und Satz-Architekt-Gerüste; AE3/AE8 →
  `ProgressionEngineTest`; AE5 → Resolve-Semantik in allen Trainern; AE6 → geteilte Atome
  über Trainer hinweg; AE7 entfällt; AE9 → `SessionSnapshot(lessonId, trainerIndex, roundIndex)`;
  AE10 → `LessonCoverageTest` + Lektions-Freischaltung; AE11 → Ziffern-Prompt im Rechen-Trainer;
  AE12 → unverändert offline.
```

- [ ] **Step 6: Residuen-Notiz aktualisieren**

An `docs/residual-review-findings/feat-abc-vorschul-app.md` anhängen:

```markdown
## Residuen aus dem Trainer-/Pfad-Umbau (2026-07-26)

| Severity | Area | Note |
|----------|------|------|
| P2 | Content | Lektionen 7–16 sind als gesperrte Pfad-Knoten angelegt, aber noch nicht autoriert (Nutzerentscheidung: Engines zuerst) |
| P2 | Trainer 3 | System-TTS kann einen Laut nicht kontinuierlich dehnen; der Dehnton spielt einmal beim Ziehstart, die Intensivierung ist visuell |
| P2 | Trainer 2 | Strichdaten sind handautoriert pro Graphem; für Lektionen 7–16 müssen `Sch`, `St`, `Qu`, `ß` und die Umlaute noch ergänzt werden |
| P3 | Trainer 2 | Kein „Straße wird zur Rakete"-Morph-Video, nur ein kurzer Emoji-Reveal |
| P3 | Testing | Weiterhin kein DataStore-Round-Trip-Test und keine Compose-UI-Tests für die Drag-Commits |
| P3 | Content | `Atom.pluralHighlight` wird von keinem Trainer mehr gerendert (Rechnen zeigt bewusst keine Wörter) |
```

- [ ] **Step 7: Cursor-Rules abgleichen**

```bash
ls .cursor/rules 2>/dev/null && git grep -n -E "cloze|Sprich mit|DragSlotBoard|Domänen|domain" -- .cursor || echo "no cursor rules to update"
```
Jede Regel, die Trainer-Typen, Domänen oder Session-Mix beschreibt, auf das Sechs-Trainer-/Lektionsmodell umschreiben (gleicher Wortlaut wie in `AGENTS.md` Step 3).

- [ ] **Step 8: Gesamtverifikation**

```bash
./gradlew :app:testDebugUnitTest
```
Expected: PASS. Erwartete Testklassen: `ContentValidatorTest`, `ContentRepositoryTest`, `LessonCoverageTest`, `ProgressionEngineTest`, `LessonGatingTest`, `LessonSessionTest`, `PathGeometryTest`, `DragHitTestTest`, `OrderedPlacementTest`, `SoundPositionLogicTest`, `TraceGeometryTest`, `TraceProgressTest`, `MergeProgressTest`, `WordBuildTrayTest`, `SentenceOrderTrayTest`, `MathHintingTest`, `QuantityGroupingTest`.

```bash
./gradlew :app:assembleDebug
```
Expected: BUILD SUCCESSFUL.

Qualitäts-Gates prüfen:

```bash
grep -n "uses-permission" app/src/main/AndroidManifest.xml || echo "no permissions - ok"
git grep -n -iE "admob|ads|billing|analytics|firebase" -- app/build.gradle.kts gradle/libs.versions.toml || echo "no ad/monetization deps - ok"
```
Expected: keine Netz-/Werbe-/Billing-Abhängigkeit; `INTERNET` darf nicht auftauchen.

- [ ] **Step 9: Offline-Smoke-Skript auf einem Gerät/Emulator abarbeiten**

Führe die zehn Schritte aus README Step 4 aus. Notiere Abweichungen in
`docs/residual-review-findings/feat-abc-vorschul-app.md`, statt sie stillschweigend zu lassen.

- [ ] **Step 10: Commit**

```bash
git add docs README.md AGENTS.md .cursor
git commit -m "docs: record the six-trainer lesson model and the implemented path

Rewrites the Fibel progression, session and math sections of the product
principles, refreshes the agent UI rules and the offline smoke script, and adds
Addendum A2 to the original plan documenting that R5/R6/R16 were replaced or
retracted by explicit user decisions.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Verification Contract

- **Unit:** `./gradlew :app:testDebugUnitTest` — Content-Schema/Validator, Lektionsabdeckung,
  Progression, Lektions-Freischaltung, Session-Fortschritt, Pfad-Geometrie, Drag-Hit-Testing,
  Platzierung, Trace-Geometrie und -Fortschritt, Merge-Fortschritt, Trays, Rechen-Hinweise.
- **Build:** `./gradlew :app:assembleDebug`
- **Manuell/Smoke:** Zehn-Schritte-Skript aus `README.md` (Flugmodus, alle sechs Trainer,
  Parent-Gate, Resolve, Kill/Resume, Pfad-Freischaltung).
- **Quality Gates:** keine Netz-Permission, keine Werbe-/Billing-/Analytics-Dependency,
  dark-only erzwungen, keine Emojis in Buttons, keine Anweisungs-Chrome für das Kind.

## Definition of Done

- Tasks 1–12 abgeschlossen, jeweilige Verifikation erfüllt.
- Alte Trainer-Typen (Lese-Cloze, Satz-Cloze, Sprech-Cloze) samt Mix-Scheduler und
  Laufzeit-Distraktoren aus dem Code entfernt; „Rechnen" erhalten und in jeder Lektion vorhanden.
- Alle sechs Trainer-Engines implementiert und über `TrainerHost` erschöpfend verdrahtet
  (kein `else`-Zweig).
- Pfad-Screen ist der App-Einstieg mit 16 Knoten, davon 6 spielbar.
- Content für Lektionen 1–6 validiert; 7–16 als gesperrte Knoten deklariert.
- `docs/PRODUCT_PRINCIPLES.md`, `AGENTS.md`, `README.md`, Addendum A2 und die Residuen-Notiz
  entsprechen dem Ist-Stand.
- Keine blockierende offene Frage.

## Self-Review

**Spec-Abdeckung (Konzept Teil 1 — Trainer):**

| Konzept | Task |
|---------|------|
| Trainer 1 Auditiver Finder (Lok, 3 Waggons, Drag, Segment-Feedback) | Task 6 |
| Trainer 2 Visueller Spurensucher (Straße, Sterne, Strichrichtung, Korridor, Haptik, Ton, Reveal) | Task 7 |
| Trainer 3 Silben-Verschmelzer (zwei Schollen, Dehnton, Verschmelzung) | Task 8 |
| Trainer 4 Wort-Bauer (Bild, Schablonen, Silbenklötze) | Task 9 |
| Trainer 5 Satz-Architekt (Wäscheleine, Vorlesen pro Wort, Satz am Ende) | Task 10 |
| Trainer 5 Variante B Mathe-Plural mit Wortkarten | **Bewusst nicht gebaut** — Nutzerentscheidung „keine Wörter zum lesen/schreiben" im Rechnen; siehe Abweichungstabelle |
| Rechnen in jeder Lektion, kontextnahe Icons | Task 3 (Engine) + Task 11 (Content) |
| Voice-Over erklärt jede Interaktion, kein Lesezwang | Global Constraints + `promptTts` pro Runde |

**Spec-Abdeckung (Konzept Teil 2 — Lernpfad):**

| Konzept | Task |
|---------|------|
| 16 Lektionen, 5 Phasen, Fibel-Reihenfolge | Task 1 (`lessons.json`, alle 16 deklariert) |
| Lektionen 1–6 mit Ziel-Wörtern und Sätzen | Task 1 (l01) + Task 11 (l02–l06) |
| Lektionen 7–16 | Als `planned`-Knoten deklariert; Content-Nachzug ohne Code |
| Pfad auf dem Homescreen (Addendum A1) | Task 4 |
| Meilenstein-Satz „Oma ist da." / „Das Lama ist rot." | Task 11, l06-t5 |
| Ziel-Wort „Ufo (U-fo)" in Lektion 5 | Ersetzt durch `Tor`/`rot`, weil F noch nicht eingeführt ist — dokumentiert in Task 11 |
| „Tor" in Lektion 3 vor dem R | Über `holisticAtomIds` ausdrücklich als Ganzwort-Bild markiert |

**Platzhalter-Scan:** Kein „TBD"/„TODO"/„implement later"/„similar to Task N". Der eine bewusste
Platzhalter — `TrainerPlaceholder` in Task 3 — hält den Build zwischen den Trainer-Tasks grün und
wird in Task 10 Step 4 restlos entfernt (ausdrücklich verifiziert über das erschöpfende `when`).

**Typkonsistenz:** `TaskSpec`/`TrainerRound`-Namen, `ScheduledTrainer`, `SessionStep`,
`SessionProgression`, `LessonState`, `LessonGating`, `DragFieldState`, `DragRect`, `TracePoint`,
`TraceState`, `TraceUpdate`, `OrderedPlacement`, `WordBuildTray`, `SentenceOrderTray` und
`PathPoint`/`PathGeometry` sind über alle Tasks identisch benannt. `MathHinting.usesNumberPad`
wechselt in Task 3 von zwei Booleans auf `ScaffoldLevel` — der zugehörige Test wird dort
mitgeändert. `ProgressionEngine.mathKey` nimmt ab Task 2 `CountAddRound` statt `TaskTemplate`.

**Bekannte Reihenfolge-Abhängigkeit:** Tasks 1–3 werden gemeinsam committet, weil das Modul
zwischen dem Schema-Umbau und dem Session-Rewrite nicht kompilierbar ist. Ab Task 4 ist nach
jeder Task Build und Testsuite grün.
