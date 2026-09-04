# Laut-Fresser — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ein dritter abgeleiteter Trainer „Laut-Fresser": zwei Monster mit je einem Laut auf dem Bauch, das Kind zieht Bildkarten zu dem Monster, dessen Laut es im Wort hört.

**Architecture:** Wie Jagd und Wort-Detektiv wird der Trainer zur Laufzeit aus dem Pack abgeleitet (`SoundPairs` = kuratierte Paar-Tabelle, `SoundFeederDerivation` = Zuordnung Lektion → Paar → Karten, `SoundFeederInsertion` = Einschub hinter der Buchstaben-Jagd). Zustandsautomat (`SoundFeederProgress`), Größen (`SoundFeederSizing`) und Sprache (`SoundFeederSpeech`) sind Compose-frei und JVM-testbar; der Screen (`SoundFeederTrainer`, `FeederCreature`) zeichnet nur. Die Monster-Stimme ist ein neuer `VoiceStyle`-Parameter der Sprachausgabe (Tonhöhe je Äußerung), kein neuer Sprecher.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), kotlinx.serialization, JUnit 4 (`org.junit.Assert.*`), Gradle; Python/pytest für `tools/tts`.

**Spec:** [`docs/superpowers/specs/2026-09-04-laut-fresser-design.md`](../specs/2026-09-04-laut-fresser-design.md)

## Global Constraints

- **Worktree ohne `local.properties`:** jedem Gradle-Aufruf `ANDROID_HOME=$HOME/Library/Android/sdk` voranstellen. Unit-Tests: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest`; Build: `… :app:assembleDebug`. Einzelne Testklasse: `--tests 'app.abcvorschule.content.SoundPairsTest'`.
- **Die Unit-Tests laden den ausgelieferten Pack** (`ContentRepository.fromClasspath().load()`, `src/main/assets` ist Test-Resource-Root). Nie eine zweite Content-Kopie anlegen; synthetische Fälle über `pack.copy(...)` oder handgebaute `Atom`/`Lesson`.
- **Keine Strafen, Audio-First** (PRODUCT_PRINCIPLES §2, §7). Kein Text, den das Kind lesen müsste — Ausnahme: das Wort unter dem Emoji, wenn kein deutsches TTS läuft.
- **Nur die Rechenaufgabe fragt.** Alle Ansagen sind Aufforderungen und enden auf einen Punkt.
- **Neue gesprochene Strings** kommen nach `tools/tts/extra-strings.json`, sonst gibt es keinen Clip. Feste Strings, keine Interpolation.
- **Prompt-Konvention** (§7): einzelne Buchstaben als eigene Clip-Teile (Lemma), nie in einen Satz interpoliert.
- **Farben mit Rolle bleiben bei ihrer Rolle:** `LeafGreen` = richtig, `StarGold` = Belohnung, `ClayRed` = Fehler. Die Fresser tragen `SkyBlue` (links) und `SunCoral` (rechts), Bauchfleck `Cream`, Glyph `WarmInk`.
- **font_scale 1.3** ist das Testgerät. Jede Größenrechnung nimmt `fontScale` als Parameter und wird im Test bei 1.0 **und** 1.3 geprüft.
- **Neuer `TrainerKind` bricht `when`-Exhaustiveness absichtlich** — jede betroffene Stelle bekommt in diesem Plan einen ausdrücklichen Zweig; nie mit `else` abkürzen.
- **Geteilter Emulator** (AGENTS.md): vor `installDebug`/`connectedDebugAndroidTest` Lock nehmen (`mkdir /tmp/abc-emulator.lock`), `ANDROID_SERIAL=emulator-5554` setzen, nur die eigene Testklasse laufen lassen, Lock danach entfernen.
- **Commits** enden mit `Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>`. Ein Commit pro Task.
- Kommentare erklären das **Warum**, auf Deutsch oder Englisch (beides im Repo etabliert).

## File Structure

| Datei | Verantwortung |
| --- | --- |
| `content/TaskSpecs.kt` *(ändern)* | `SoundFeederSpec`, `SoundFeederRound`, `SoundFeederCard`, `FeederSide`, `TrainerKind.sound_feeder`; `kind`/`rounds`/`scoredAtomIds` |
| `content/ContentValidator.kt` *(ändern)* | abgeleiteter Typ darf nicht autoriert sein |
| `content/SoundPairs.kt` *(neu)* | Paar-Tabelle in drei Stufen, Ausschlussliste, Graphem-Segmentierung, Anlaut/Enthält, Kartentauglichkeit, Emoji-Cluster-Zählung |
| `content/SoundFeederDerivation.kt` *(neu)* | Lektion → Paar (Stufen, „am längsten nicht gespielt", Wiederholung), Vorrat, Kartenwahl mit Rotation und Zwillingen |
| `content/SoundFeederSpeech.kt` *(neu)* | Ansage, Fress-, Miss- und Schluss-Sequenz als `SpokenPart`s |
| `speech/VoiceStyle.kt` *(neu)* | `VoiceStyle` (Tonhöhe) und `SpokenPart` |
| `speech/SpeechController.kt`, `speech/ClipPlayer.kt` *(ändern)* | Tonhöhe je Äußerung, Sequenz aus `SpokenPart`s |
| `session/SoundFeederInsertion.kt` *(neu)* | Einschub nach der Buchstaben-Jagd |
| `session/SessionTrainers.kt` *(ändern)* | Einhängen des Einschubs |
| `session/SuccessSpeech.kt`, `session/SessionViewModel.kt` *(ändern)* | leere Erfolgs-Teile, leere Prompt-Teile, kein generischer Miss-Cue |
| `ui/exercise/SoundFeederProgress.kt` *(neu)* | Zustandsautomat der Runde |
| `ui/exercise/SoundFeederSizing.kt` *(neu)* | Figur-, Karten-, Glyph- und Stapelmaße |
| `ui/exercise/FeederCreature.kt` *(neu)* | die Monster-Figur als Canvas plus Animator |
| `ui/exercise/SoundFeederTrainer.kt` *(neu)* | der Screen |
| `ui/exercise/TrainerHost.kt` *(ändern)* | Dispatch, zwei neue Callbacks |
| `ui/shell/TaskShell.kt`, `MainActivity.kt` *(ändern)* | Callbacks durchreichen |
| `assets/content/atoms.json` *(ändern)* | zehn neue Atome |
| `tools/tts/extra-strings.json`, `profiles.json`, `ttskit/extract.py`, `ttskit/export.py` *(ändern)* | Strings, Profil `monster`, Feldzuordnung |
| `androidTest/…/SoundFeederShotTest.kt` *(neu)* | Layout-Screenshots bei 1.0 und 1.3 |
| Doku: `PRODUCT_PRINCIPLES.md`, `AGENTS.md`, `README.md`, `tools/tts/README.md` | Regeln nachziehen |

---

### Task 1: Content-Modell und alle `when`-Verzweigungen

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/TaskSpecs.kt`
- Modify: `app/src/main/java/app/abcvorschule/content/ContentValidator.kt` (bei `is SymbolInWordSpec ->`, ca. Zeile 426)
- Modify: `app/src/main/java/app/abcvorschule/session/SuccessSpeech.kt`
- Modify: `app/src/main/java/app/abcvorschule/session/SessionViewModel.kt` (`currentPromptParts`, ca. Zeile 324; `afterAttempt`, ca. Zeile 560)
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt`
- Test: `app/src/test/java/app/abcvorschule/content/SoundFeederSpecTest.kt`

**Interfaces:**
- Produces: `enum class FeederSide { left, right }`; `SoundFeederCard(atomId: String, side: FeederSide)`; `SoundFeederRound(promptTts, leftAtomId, rightAtomId, cards)` mit `atomIdFor(side)`; `SoundFeederSpec(id, rounds)`; `TrainerKind.sound_feeder`.

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class SoundFeederSpecTest {
    private val cards = listOf(
        SoundFeederCard("sonne", FeederSide.left),
        SoundFeederCard("salat", FeederSide.left),
        SoundFeederCard("schuh", FeederSide.right),
        SoundFeederCard("schaf", FeederSide.right),
    )
    private val round = SoundFeederRound(
        promptTts = "Füttere die Laut-Fresser.",
        leftAtomId = "letter-s",
        rightAtomId = "letter-sch",
        cards = cards,
    )
    private val spec = SoundFeederSpec(id = "l13:sound_feeder", rounds = listOf(round))

    @Test
    fun kindMapsToTheNewTrainer() {
        assertEquals(TrainerKind.sound_feeder, spec.kind)
    }

    @Test
    fun roundsAreReachableThroughTheSealedAccessor() {
        assertEquals(1, spec.roundCount)
        assertEquals(round, spec.round(0))
    }

    @Test
    fun scoresAgainstBothSounds() {
        assertEquals(listOf("letter-s", "letter-sch"), round.scoredAtomIds())
    }

    @Test
    fun sideResolvesToItsSoundAtom() {
        assertEquals("letter-s", round.atomIdFor(FeederSide.left))
        assertEquals("letter-sch", round.atomIdFor(FeederSide.right))
    }

    @Test
    fun theNewKindIsNotPartOfTheAuthoredTrainerOrder() {
        assertFalse(ContentValidator.TrainerOrder.contains(TrainerKind.sound_feeder))
    }

    @Test
    fun aRoundNeedsTwoCardsPerSide() {
        assertThrows(IllegalArgumentException::class.java) {
            round.copy(cards = cards.filter { it.side == FeederSide.left } + cards[2])
        }
    }

    @Test
    fun aRoundRejectsDuplicateCardsAndIdenticalSides() {
        assertThrows(IllegalArgumentException::class.java) {
            round.copy(cards = cards + SoundFeederCard("sonne", FeederSide.left))
        }
        assertThrows(IllegalArgumentException::class.java) {
            round.copy(rightAtomId = "letter-s")
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag sehen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.content.SoundFeederSpecTest'`
Expected: Compile-Fehler „Unresolved reference: SoundFeederCard".

- [ ] **Step 3: Modell in `TaskSpecs.kt` ergänzen**

Nach dem `SymbolInWordRound`-Block (vor `data class TasksFile`) einfügen und `TrainerKind` um `sound_feeder` erweitern:

```kotlin
// --- Abgeleitet: Laut-Fresser ----------------------------------------------

/** Welcher der beiden Fresser — links trägt [SoundFeederRound.leftAtomId]. */
@Serializable
enum class FeederSide { left, right }

/** Eine Bildkarte der Runde: das Wort-Atom (Emoji + Lemma) und der Fresser, dem es gehört. */
@Serializable
data class SoundFeederCard(val atomId: String, val side: FeederSide)

/**
 * Never appears in authored JSON — SoundFeederInsertion derives instances at runtime
 * from the pack (design doc §2). `@Serializable` for the same reason as
 * [SymbolHuntSpec]: every member of the sealed hierarchy needs it to compile.
 */
@Serializable
@SerialName("sound_feeder")
data class SoundFeederSpec(
    override val id: String,
    val rounds: List<SoundFeederRound>,
) : TaskSpec

/**
 * One feeding session: two sound atoms and up to seven picture cards, each already
 * resolved to its side. The screen makes no decisions (design doc §4).
 */
@Serializable
data class SoundFeederRound(
    override val promptTts: String,
    val leftAtomId: String,
    val rightAtomId: String,
    val cards: List<SoundFeederCard>,
) : TrainerRound {
    init {
        require(leftAtomId != rightAtomId) { "SoundFeederRound needs two different sounds" }
        require(cards.count { it.side == FeederSide.left } >= 2) { "SoundFeederRound needs 2 left cards" }
        require(cards.count { it.side == FeederSide.right } >= 2) { "SoundFeederRound needs 2 right cards" }
        require(cards.map { it.atomId }.toSet().size == cards.size) { "SoundFeederRound has a duplicate card" }
    }

    fun atomIdFor(side: FeederSide): String =
        if (side == FeederSide.left) leftAtomId else rightAtomId
}
```

In `TaskSpec.kind`, `TaskSpec.rounds` und `TrainerRound.scoredAtomIds()` je einen Zweig ergänzen:

```kotlin
        is SoundFeederSpec -> TrainerKind.sound_feeder
// …
        is SoundFeederSpec -> rounds
// …
    // Beide Laute werden geübt — die Karten sind nur, wo sie sich verstecken.
    is SoundFeederRound -> listOf(leftAtomId, rightAtomId)
```

- [ ] **Step 4: Die übrigen Verzweigungen**

`ContentValidator.kt`, hinter dem `is SymbolInWordSpec ->`-Zweig:

```kotlin
                is SoundFeederSpec ->
                    issues += ValidationIssue(
                        "task $id is a derived trainer (${spec.kind}) and must not appear in authored content",
                    )
```

`SuccessSpeech.partsForRound`, vor `else -> emptyList()`:

```kotlin
        // Die Fresser haben beim letzten Rülpsen schon gesprochen (design doc §6).
        is SoundFeederRound -> emptyList()
```

`SessionViewModel.currentPromptParts`, vor `else ->`:

```kotlin
            // Der Trainer spricht Ansage und Vorstellung selbst, synchron zu seinen
            // Figuren (design doc §5) — die Bühne darf hier nichts sprechen.
            is SoundFeederRound -> emptyList()
```

`SessionViewModel.afterAttempt`, die Zeile `it is SymbolHuntRound || it is SymbolInWordRound` erweitern zu
`it is SymbolHuntRound || it is SymbolInWordRound || it is SoundFeederRound` — der Fresser sagt selbst „Bäh!" und wiederholt das Wort; der generische Miss-Cue würde ihn abwürgen.

`TrainerHost.kt`, am Ende des `when(round)`: vorläufiger Zweig, den Task 11 ersetzt:

```kotlin
        // Wird in SoundFeederTrainer (Task 11 des Plans) ersetzt — bis dahin nur
        // damit die sealed-Verzweigung kompiliert.
        is SoundFeederRound -> Unit
```

- [ ] **Step 5: Alle Unit-Tests laufen lassen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest`
Expected: grün, inklusive `SoundFeederSpecTest`. Fällt `ContentValidatorTest` oder eine andere Klasse wegen einer weiteren nicht-exhaustiven `when` um, dort ebenfalls einen ausdrücklichen Zweig ergänzen.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/TaskSpecs.kt app/src/main/java/app/abcvorschule/content/ContentValidator.kt app/src/main/java/app/abcvorschule/session/SuccessSpeech.kt app/src/main/java/app/abcvorschule/session/SessionViewModel.kt app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt app/src/test/java/app/abcvorschule/content/SoundFeederSpecTest.kt
git commit -m "feat(content): SoundFeederSpec — Modell des Laut-Fressers

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 2: Zehn neue Atome

**Files:**
- Modify: `app/src/main/assets/content/atoms.json`
- Test: `app/src/test/java/app/abcvorschule/content/SoundFeederAtomsTest.kt`

**Interfaces:**
- Produces: Atome `turm`, `wurm`, `kanne`, `tanne`, `wanne`, `moehre`, `muetze`, `pfanne`, `pfeil`, `ziege` (`kind: other`, `nounClass: thing`).

Hinweis: `LessonCoverageTest.noAtomSitsInThePackWithoutEverBeingShown` wird nach diesem Task **rot** und bleibt es bis Task 4 (dort zählt der Test die Fresser-Karten mit). Das ist beabsichtigt; der Commit dieses Tasks darf mit genau diesem einen roten Test erfolgen — in der Commit-Message benennen.

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SoundFeederAtomsTest {
    private val pack = ContentRepository.fromClasspath().load()

    private val expected = mapOf(
        "turm" to Triple("Turm", "🗼", Gender.m),
        "wurm" to Triple("Wurm", "🪱", Gender.m),
        "kanne" to Triple("Kanne", "🫖", Gender.f),
        "tanne" to Triple("Tanne", "🌲", Gender.f),
        "wanne" to Triple("Wanne", "🛁", Gender.f),
        "moehre" to Triple("Möhre", "🥕", Gender.f),
        "muetze" to Triple("Mütze", "🧢", Gender.f),
        "pfanne" to Triple("Pfanne", "🍳", Gender.f),
        "pfeil" to Triple("Pfeil", "🏹", Gender.m),
        "ziege" to Triple("Ziege", "🐐", Gender.f),
    )

    @Test
    fun theTenFeederAtomsExistAsPictureNouns() {
        expected.forEach { (id, triple) ->
            val (display, emoji, gender) = triple
            val atom = pack.atoms[id]
            assertNotNull("atom $id missing", atom)
            assertEquals(display, atom!!.display)
            assertEquals(display, atom.lemma)
            assertEquals(emoji, atom.emoji)
            assertEquals(AtomKind.other, atom.kind)
            assertEquals(NounClass.thing, atom.nounClass)
            assertEquals(gender, atom.gender)
            assertNotNull("atom $id needs a pluralDisplay", atom.pluralDisplay)
        }
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag sehen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.content.SoundFeederAtomsTest'`
Expected: FAIL „atom turm missing".

- [ ] **Step 3: Atome anhängen**

Am Ende der `atoms`-Liste in `atoms.json` (Stil wie `{"id": "kuchen", …}`; IDs ohne Umlaute wie `loewe`, `kueken`):

```json
    { "id": "turm",   "lemma": "Turm",   "display": "Turm",   "emoji": "🗼", "kind": "other", "pluralDisplay": "Türme",   "gender": "m", "nounClass": "thing" },
    { "id": "wurm",   "lemma": "Wurm",   "display": "Wurm",   "emoji": "🪱", "kind": "other", "pluralDisplay": "Würmer",  "gender": "m", "nounClass": "thing" },
    { "id": "kanne",  "lemma": "Kanne",  "display": "Kanne",  "emoji": "🫖", "kind": "other", "pluralDisplay": "Kannen",  "gender": "f", "nounClass": "thing" },
    { "id": "tanne",  "lemma": "Tanne",  "display": "Tanne",  "emoji": "🌲", "kind": "other", "pluralDisplay": "Tannen",  "gender": "f", "nounClass": "thing" },
    { "id": "wanne",  "lemma": "Wanne",  "display": "Wanne",  "emoji": "🛁", "kind": "other", "pluralDisplay": "Wannen",  "gender": "f", "nounClass": "thing" },
    { "id": "moehre", "lemma": "Möhre",  "display": "Möhre",  "emoji": "🥕", "kind": "other", "pluralDisplay": "Möhren",  "gender": "f", "nounClass": "thing" },
    { "id": "muetze", "lemma": "Mütze",  "display": "Mütze",  "emoji": "🧢", "kind": "other", "pluralDisplay": "Mützen",  "gender": "f", "nounClass": "thing" },
    { "id": "pfanne", "lemma": "Pfanne", "display": "Pfanne", "emoji": "🍳", "kind": "other", "pluralDisplay": "Pfannen", "gender": "f", "nounClass": "thing" },
    { "id": "pfeil",  "lemma": "Pfeil",  "display": "Pfeil",  "emoji": "🏹", "kind": "other", "pluralDisplay": "Pfeile",  "gender": "m", "nounClass": "thing" },
    { "id": "ziege",  "lemma": "Ziege",  "display": "Ziege",  "emoji": "🐐", "kind": "other", "pluralDisplay": "Ziegen",  "gender": "f", "nounClass": "thing" }
```

JSON-Gültigkeit prüfen: `python3 -c "import json; json.load(open('app/src/main/assets/content/atoms.json'))"`.

- [ ] **Step 4: Tests laufen lassen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest`
Expected: `SoundFeederAtomsTest` grün; **einzig** `LessonCoverageTest.noAtomSitsInThePackWithoutEverBeingShown` rot mit genau den zehn IDs. Jeder andere rote Test ist ein echter Fehler.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/assets/content/atoms.json app/src/test/java/app/abcvorschule/content/SoundFeederAtomsTest.kt
git commit -m "feat(content): zehn Bildatome für den Laut-Fresser (Turm, Wurm, Kanne, Tanne, …)

LessonCoverageTest.noAtomSitsInThePackWithoutEverBeingShown ist bis zur
Fresser-Ableitung absichtlich rot: die Atome bekommen ihren Auftritt dort.

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 3: `SoundPairs` — Tabelle, Segmentierung, Kartentauglichkeit

**Files:**
- Create: `app/src/main/java/app/abcvorschule/content/SoundPairs.kt`
- Test: `app/src/test/java/app/abcvorschule/content/SoundPairsTest.kt`

**Interfaces:**
- Produces:
  - `enum class SoundPairTier { Contrast, WarmUp, Vowel }`
  - `data class SoundPair(val left: String, val right: String, val tier: SoundPairTier)` mit `val anywhere: Boolean` (true für `Vowel`)
  - `object SoundPairs { val table: List<SoundPair>; val excludedWords: Set<String>; val units: List<String>; fun segments(word): List<String>; fun startsWith(word, grapheme): Boolean; fun contains(word, grapheme): Boolean; fun rest(word): String; fun emojiClusterCount(emoji): Int; fun isCardWorthy(atom: Atom): Boolean; fun letterAtom(pack, grapheme): Atom?; fun introductionIndex(pack, grapheme): Int? }`

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundPairsTest {
    private val pack = ContentRepository.fromClasspath().load()

    // --- Segmentierung -------------------------------------------------------

    @Test
    fun multiLetterUnitsStayTogether() {
        assertEquals(listOf("sch", "u", "h"), SoundPairs.segments("Schuh"))
        assertEquals(listOf("st", "e", "r", "n"), SoundPairs.segments("Stern"))
        assertEquals(listOf("pf", "e", "r", "d"), SoundPairs.segments("Pferd"))
        assertEquals(listOf("f", "eu", "e", "r"), SoundPairs.segments("Feuer"))
        assertEquals(listOf("b", "ie", "n", "e"), SoundPairs.segments("Biene"))
    }

    @Test
    fun onsetIsTheFirstSegmentOnly() {
        assertTrue(SoundPairs.startsWith("Sonne", "S"))
        assertFalse(SoundPairs.startsWith("Schuh", "S"))
        assertFalse(SoundPairs.startsWith("Stern", "S"))
        assertFalse(SoundPairs.startsWith("Pferd", "P"))
        assertTrue(SoundPairs.startsWith("Pferd", "Pf"))
        assertTrue(SoundPairs.startsWith("Eule", "Eu"))
    }

    @Test
    fun containsSeesEveryPosition() {
        assertTrue(SoundPairs.contains("Zahnbürste", "S"))
        assertTrue(SoundPairs.contains("Fußball", "S")) // ß zählt als S
        assertTrue(SoundPairs.contains("Nuss", "S"))
        assertTrue(SoundPairs.contains("Sonnenblume", "B"))
        assertFalse(SoundPairs.contains("Feuer", "U")) // eu ist kein u
        assertFalse(SoundPairs.contains("Eis", "I")) // ei ist kein i
        assertTrue(SoundPairs.contains("Biene", "I")) // ie ist ein i
        assertTrue(SoundPairs.contains("Kopfhörer", "Ö"))
        assertTrue(SoundPairs.contains("Lampe", "L")) // Anlaut zählt auch als enthalten
        assertFalse(SoundPairs.contains("Stern", "S")) // Anlaut-st ist [ʃt]
        assertFalse(SoundPairs.contains("Spinne", "S"))
        assertFalse(SoundPairs.contains("Schuh", "S")) // sch enthält kein s
        assertFalse(SoundPairs.contains("Pferd", "P"))
    }

    @Test
    fun restDropsTheOnsetSegment() {
        assertEquals(SoundPairs.rest("Fisch"), SoundPairs.rest("Tisch"))
        assertEquals(SoundPairs.rest("Kanne"), SoundPairs.rest("Tanne"))
        assertEquals("erd", SoundPairs.rest("Pferd"))
    }

    // --- Kartentauglichkeit --------------------------------------------------

    @Test
    fun emojiClustersAreCountedAsAChildSeesThem() {
        assertEquals(1, SoundPairs.emojiClusterCount("🐜"))
        assertEquals(1, SoundPairs.emojiClusterCount("🏘️")) // Variation Selector
        assertEquals(1, SoundPairs.emojiClusterCount("👦🏽")) // Hautton
        assertEquals(1, SoundPairs.emojiClusterCount("🐻‍❄️")) // ZWJ-Sequenz
        assertEquals(1, SoundPairs.emojiClusterCount("🧑‍🤝‍🧑"))
        assertEquals(2, SoundPairs.emojiClusterCount("🌳🌳"))
        assertEquals(2, SoundPairs.emojiClusterCount("🥚🥚"))
        assertEquals(0, SoundPairs.emojiClusterCount(""))
    }

    @Test
    fun onlyPictureNounsWithOneEmojiQualify() {
        assertTrue(SoundPairs.isCardWorthy(pack.atom("ameise")))
        assertTrue(SoundPairs.isCardWorthy(pack.atom("mama")))
        assertTrue(SoundPairs.isCardWorthy(pack.atom("tom"))) // Name ist ein Substantiv
        assertFalse(SoundPairs.isCardWorthy(pack.atom("gelb")))
        assertFalse(SoundPairs.isCardWorthy(pack.atom("ich")))
        assertFalse(SoundPairs.isCardWorthy(pack.atom("baeume")))
        assertFalse(SoundPairs.isCardWorthy(pack.atom("letter-s")))
        assertFalse(SoundPairs.isCardWorthy(pack.atom("giraffe")))
    }

    // --- Tabelle und Pack-Bezug ---------------------------------------------

    @Test
    fun everyPairInTheTableNamesTwoLetterAtomsOfThePack() {
        SoundPairs.table.forEach { pair ->
            assertNotNull("no letter atom for ${pair.left}", SoundPairs.letterAtom(pack, pair.left))
            assertNotNull("no letter atom for ${pair.right}", SoundPairs.letterAtom(pack, pair.right))
        }
    }

    @Test
    fun onlyVowelPairsMatchAnywhereInTheWord() {
        assertTrue(SoundPairs.table.filter { it.tier == SoundPairTier.Vowel }.all { it.anywhere })
        assertTrue(SoundPairs.table.filter { it.tier != SoundPairTier.Vowel }.none { it.anywhere })
    }

    @Test
    fun introductionIndexIsTheFirstLessonThatFocusesTheGrapheme() {
        assertEquals(7, SoundPairs.introductionIndex(pack, "S"))
        assertEquals(13, SoundPairs.introductionIndex(pack, "Sch"))
        assertEquals(17, SoundPairs.introductionIndex(pack, "St"))
        assertEquals(null, SoundPairs.introductionIndex(pack, "Ng"))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag sehen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.content.SoundPairsTest'`
Expected: Compile-Fehler „Unresolved reference: SoundPairs".

- [ ] **Step 3: `SoundPairs.kt` schreiben**

```kotlin
package app.abcvorschule.content

/**
 * Kuratiertes Wissen für den Laut-Fresser (design doc §3/§4): welche Lautpaare
 * Vorschulkinder verwechseln, in welcher Stufe sie gespielt werden, und wie ein
 * geschriebenes Wort für das *Hören* zerlegt wird. Bewusst nicht aus dem Pack
 * abgeleitet — welche Laute sich ähnlich anhören, steht nirgends im Content.
 */
enum class SoundPairTier {
    /** Echte Verwechslungen (Fox-Boyer, H-LAD). Der Kern des Trainers. */
    Contrast,

    /** Leichte Kontraste für L03–L07, damit das Kind Monster und Geste kennt, bevor
     * das erste schwere Paar kommt. */
    WarmUp,

    /** Vokale sind der Silbenkern und überall hörbar — sie werden nicht am Anlaut,
     * sondern irgendwo im Wort gesucht. */
    Vowel,
}

/** Grapheme als [Atom.display] der Buchstaben-Atome (`S`, `Sch`, `Ei`). */
data class SoundPair(val left: String, val right: String, val tier: SoundPairTier) {
    val anywhere: Boolean get() = tier == SoundPairTier.Vowel
    val graphemes: List<String> get() = listOf(left, right)
    override fun toString(): String = "$left/$right"
}

object SoundPairs {
    val table: List<SoundPair> = listOf(
        SoundPair("S", "Sch", SoundPairTier.Contrast),
        SoundPair("S", "Z", SoundPairTier.Contrast),
        SoundPair("F", "W", SoundPairTier.Contrast),
        SoundPair("W", "B", SoundPairTier.Contrast),
        SoundPair("B", "P", SoundPairTier.Contrast),
        SoundPair("D", "T", SoundPairTier.Contrast),
        SoundPair("G", "K", SoundPairTier.Contrast),
        SoundPair("K", "T", SoundPairTier.Contrast),
        SoundPair("M", "N", SoundPairTier.Contrast),
        SoundPair("L", "R", SoundPairTier.Contrast),
        SoundPair("St", "Sp", SoundPairTier.Contrast),
        SoundPair("Pf", "F", SoundPairTier.Contrast),
        SoundPair("P", "T", SoundPairTier.WarmUp),
        SoundPair("L", "H", SoundPairTier.WarmUp),
        SoundPair("F", "T", SoundPairTier.WarmUp),
        SoundPair("S", "T", SoundPairTier.WarmUp),
        SoundPair("Ei", "Au", SoundPairTier.Vowel),
        SoundPair("Ei", "Eu", SoundPairTier.Vowel),
        SoundPair("Ö", "Ü", SoundPairTier.Vowel),
        SoundPair("I", "O", SoundPairTier.Vowel),
    )

    /** „Giraffe" schwankt zwischen [g] und [ʒ] — der Buchstabe auf dem Bauch darf
     * nichts versprechen, was das Ohr nicht hält. */
    val excludedWords: Set<String> = setOf("Giraffe")

    /** Mehrzeichen-Einheiten, längste zuerst; unabhängig von der Lektion, weil hier
     * gehört und nicht gelesen wird (anders als [WordGraphemes]). */
    val units: List<String> = listOf("sch", "äu", "ei", "au", "eu", "ie", "ch", "ck", "pf", "qu", "st", "sp", "ß")

    /** Kleingeschriebene Segmente, longest-match-first. */
    fun segments(word: String): List<String> {
        val lower = word.lowercase()
        val result = mutableListOf<String>()
        var index = 0
        while (index < lower.length) {
            val unit = units.firstOrNull { lower.startsWith(it, index) }
            if (unit == null) {
                result += lower[index].toString()
                index += 1
            } else {
                result += unit
                index += unit.length
            }
        }
        return result
    }

    fun startsWith(word: String, grapheme: String): Boolean =
        segments(word).firstOrNull() == grapheme.lowercase()

    /**
     * Kommt der Laut irgendwo im Wort vor? `S` schließt `ß` (und damit `ss`) ein und
     * ein `st`/`sp`, das **nicht** am Wortanfang steht — dort ist es [ʃt]/[ʃp]
     * („Stern"), mitten im Wort aber [st] („Zahnbürste"). `I` schließt das lange `ie`
     * ein. Diphthonge sind eigene Segmente, also ist das `u` in „Feuer" kein U, und
     * `sch`/`pf`/`ch`/`ck` enthalten weder S noch P noch C.
     */
    fun contains(word: String, grapheme: String): Boolean {
        val segs = segments(word)
        return when (grapheme.lowercase()) {
            "s" -> segs.any { it == "s" || it == "ß" } || segs.drop(1).any { it == "st" || it == "sp" }
            "i" -> segs.any { it == "i" || it == "ie" }
            else -> segs.contains(grapheme.lowercase())
        }
    }

    /** Das Wort ohne sein Anlaut-Segment — gleicher Rest heißt Minimalpaar. */
    fun rest(word: String): String = segments(word).drop(1).joinToString("")

    /**
     * Wie viele Bilder ein Kind sieht. Varianten-Selektoren, Hauttöne und
     * Tastenkappen zählen nicht; ein Zeichen hinter einem ZWJ gehört zum
     * vorigen Bild (🐻‍❄️ ist ein Eisbär, keine zwei Bilder). `BreakIterator` ist
     * hier keine Option — die JVM-Variante der Unit-Tests kennt keine erweiterten
     * Grapheme-Cluster.
     */
    fun emojiClusterCount(emoji: String): Int {
        var count = 0
        var previous = -1
        emoji.codePoints().forEach { cp ->
            val modifier = cp == 0x200D || cp == 0xFE0F || cp == 0xFE0E || cp == 0x20E3 ||
                cp in 0x1F3FB..0x1F3FF || cp in 0xE0020..0xE007F
            if (!modifier && previous != 0x200D) count += 1
            previous = cp
        }
        return count
    }

    /** Substantiv mit genau einem Bild, nicht ausgeschlossen (design doc §4). */
    fun isCardWorthy(atom: Atom): Boolean =
        (atom.kind == AtomKind.word || atom.kind == AtomKind.other) &&
            atom.nounClass != null &&
            emojiClusterCount(atom.emoji) == 1 &&
            atom.display !in excludedWords

    fun letterAtom(pack: ContentPack, grapheme: String): Atom? =
        pack.atoms.values.firstOrNull { it.kind == AtomKind.letter && it.display == grapheme }

    /** Index der ersten Lektion, die das Graphem als Fokus führt; null = nie. */
    fun introductionIndex(pack: ContentPack, grapheme: String): Int? =
        pack.lessons
            .filter { lesson -> lesson.focusAtomIds.any { pack.atoms[it]?.display == grapheme } }
            .minOfOrNull { it.index }
}
```

- [ ] **Step 4: Test laufen lassen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.content.SoundPairsTest'`
Expected: PASS. Schlägt `emojiClustersAreCountedAsAChildSeesThem` bei `🏘️` fehl, prüfen, ob das Emoji im Test wirklich `U+1F3D8 U+FE0F` ist (Editor-Kopie kann den Selektor verlieren).

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/SoundPairs.kt app/src/test/java/app/abcvorschule/content/SoundPairsTest.kt
git commit -m "feat(content): SoundPairs — Paar-Tabelle und Hör-Segmentierung für den Laut-Fresser

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 4: `SoundFeederDerivation` — Zuordnung und Karten

**Files:**
- Create: `app/src/main/java/app/abcvorschule/content/SoundFeederDerivation.kt`
- Modify: `app/src/test/java/app/abcvorschule/content/LessonCoverageTest.kt` (`noAtomSitsInThePackWithoutEverBeingShown`)
- Test: `app/src/test/java/app/abcvorschule/content/SoundFeederDerivationTest.kt`

**Interfaces:**
- Consumes: `SoundPairs.*`, `SoundFeederRound`, `SoundFeederCard`, `FeederSide` (Task 1, 3).
- Produces: `object SoundFeederDerivation { const val Prompt; const val FirstLessonIndex = 3; const val MaxCards = 7; const val MinPerSide = 2; const val MinSupply = 6; const val MaxTwinPairs = 2; data class Assignment(val pair: SoundPair, val replay: Boolean); data class Supply(val left: List<Atom>, val right: List<Atom>); fun supply(pack, pair): Supply; fun assignments(pack): Map<String, Assignment>; fun derive(pack): Map<String, SoundFeederRound>; fun buildRounds(pack, lesson): List<SoundFeederRound>; fun shownAtomIds(pack): Set<String>; internal fun splitCounts(total, leftSupply, rightSupply): Pair<Int, Int> }`

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundFeederDerivationTest {
    private val pack = ContentRepository.fromClasspath().load()
    private val rounds = SoundFeederDerivation.derive(pack)
    private val assignments = SoundFeederDerivation.assignments(pack)

    private fun pairOf(lessonId: String) = assignments.getValue(lessonId).pair.toString()

    // --- Snapshot der Zuordnung (design doc Anhang) --------------------------

    @Test
    fun theAssignmentMatchesTheDesignAppendix() {
        val expected = mapOf(
            "l03" to "P/T", "l04" to "L/H", "l05" to "F/T", "l06" to "M/N", "l07" to "S/T",
            "l08" to "D/T", "l09" to "F/W", "l10" to "G/K", "l11" to "W/B", "l12" to "Ö/Ü",
            "l13" to "S/Sch", "l14" to "S/Z", "l15" to "B/P", "l16" to "Pf/F", "l17" to "St/Sp",
            "l18" to "K/T", "l19" to "M/N", "l20" to "I/O", "l21" to "D/T", "l22" to "Ei/Au",
            "l23" to "S/Sch", "l24" to "St/Sp", "l25" to "Ö/Ü", "l26" to "L/R", "l27" to "S/Sch",
            "l28" to "G/K", "l29" to "W/B", "l30" to "Ei/Eu", "l31" to "M/N", "l32" to "S/Z",
            "l33" to "B/P", "l34" to "L/R",
        )
        assertEquals(expected, assignments.mapValues { it.value.pair.toString() })
    }

    @Test
    fun theFirstTwoLessonsHaveNoFeeder() {
        assertNull(assignments["l01"])
        assertNull(assignments["l02"])
        assertTrue(SoundFeederDerivation.buildRounds(pack, pack.lesson("l01")).isEmpty())
    }

    @Test
    fun replaysAreMarked() {
        assertTrue(assignments.getValue("l15").replay)
        assertTrue(assignments.getValue("l18").replay)
        assertTrue(assignments.getValue("l26").replay)
        assertTrue(assignments.values.count { it.replay } == 3)
    }

    @Test
    fun theLeastRecentlyPlayedPairWinsAmongPlayedCandidates() {
        // L21 (P & T) könnte B/P, D/T oder K/T spielen; B/P lief in L15, D/T in L08.
        assertEquals("D/T", pairOf("l21"))
        // L30 (Ei) könnte Ei/Au oder Ei/Eu; Ei/Au lief in L22.
        assertEquals("Ei/Eu", pairOf("l30"))
    }

    // --- Karten -------------------------------------------------------------

    @Test
    fun everyCardSitsOnTheSideWhoseSoundItCarriesAndNeverCarriesTheOther() {
        rounds.forEach { (lessonId, round) ->
            val pair = assignments.getValue(lessonId).pair
            round.cards.forEach { card ->
                val word = pack.atom(card.atomId).display
                val own = if (card.side == FeederSide.left) pair.left else pair.right
                val other = if (card.side == FeederSide.left) pair.right else pair.left
                val matches = if (pair.anywhere) SoundPairs.contains(word, own) else SoundPairs.startsWith(word, own)
                assertTrue("$lessonId: $word is not a $own word", matches)
                assertTrue("$lessonId: $word contains the partner $other", !SoundPairs.contains(word, other))
            }
        }
    }

    @Test
    fun everyCardIsAPictureNounWithOneEmojiAndNoEmojiRepeatsInARound() {
        rounds.forEach { (lessonId, round) ->
            val emojis = round.cards.map { pack.atom(it.atomId).emoji }
            assertEquals("$lessonId repeats an emoji", emojis.size, emojis.toSet().size)
            round.cards.forEach { assertTrue("$lessonId: ${it.atomId}", SoundPairs.isCardWorthy(pack.atom(it.atomId))) }
        }
    }

    @Test
    fun roundsHaveAtMostSevenCardsAndAtLeastTwoPerSide() {
        rounds.forEach { (lessonId, round) ->
            assertTrue("$lessonId has ${round.cards.size} cards", round.cards.size <= SoundFeederDerivation.MaxCards)
            FeederSide.entries.forEach { side ->
                assertTrue("$lessonId: too few $side cards", round.cards.count { it.side == side } >= 2)
            }
        }
        assertEquals(6, rounds.getValue("l17").cards.size) // St/Sp: 4 + 2 im Vorrat
    }

    @Test
    fun twinsSitNextToEachOther() {
        val l05 = rounds.getValue("l05").cards.map { pack.atom(it.atomId).display }
        val fisch = l05.indexOf("Fisch")
        val tisch = l05.indexOf("Tisch")
        assertTrue("Fisch/Tisch missing in $l05", fisch >= 0 && tisch >= 0)
        assertEquals(1, kotlin.math.abs(fisch - tisch))
        val l18 = rounds.getValue("l18").cards.map { pack.atom(it.atomId).display }
        assertEquals(1, kotlin.math.abs(l18.indexOf("Kanne") - l18.indexOf("Tanne")))
    }

    @Test
    fun derivationIsDeterministicButRotatesBetweenLessons() {
        assertEquals(rounds, SoundFeederDerivation.derive(pack))
        val l13 = rounds.getValue("l13").cards.map { it.atomId }.toSet()
        val l23 = rounds.getValue("l23").cards.map { it.atomId }.toSet()
        assertNotEquals(l13, l23)
    }

    @Test
    fun everyNewAtomEndsUpOnACard() {
        val shown = SoundFeederDerivation.shownAtomIds(pack)
        listOf("turm", "wurm", "kanne", "tanne", "wanne", "moehre", "muetze", "pfanne", "pfeil", "ziege")
            .forEach { assertTrue("$it never shown", it in shown) }
    }

    @Test
    fun theRoundCarriesTheLetterAtomsAndThePrompt() {
        val round = rounds.getValue("l13")
        assertEquals("letter-s", round.leftAtomId)
        assertEquals("letter-sch", round.rightAtomId)
        assertEquals(SoundFeederDerivation.Prompt, round.promptTts)
        // Aufforderung, keine Frage — nur Rechnen fragt (§7).
        assertTrue(SoundFeederDerivation.Prompt.endsWith("."))
    }

    @Test
    fun splitCountsKeepBothSidesAtTwoAndFollowTheSupply() {
        assertEquals(2 to 5, SoundFeederDerivation.splitCounts(7, 6, 18))
        assertEquals(4 to 3, SoundFeederDerivation.splitCounts(7, 9, 8))
        assertEquals(5 to 2, SoundFeederDerivation.splitCounts(7, 17, 2))
        assertEquals(4 to 2, SoundFeederDerivation.splitCounts(6, 4, 2))
    }

    @Test
    fun aPairWithoutEnoughWordsIsNeverAssigned() {
        // Synthetisch: alle Z-Wörter bis auf eines entfernen — S/Z darf dann nirgends
        // mehr gewählt werden.
        val zWords = pack.atoms.values.filter { SoundPairs.isCardWorthy(it) && SoundPairs.startsWith(it.display, "Z") }
        val thinned = pack.copy(atoms = pack.atoms - zWords.drop(1).map { it.id })
        assertTrue(SoundFeederDerivation.assignments(thinned).values.none { it.pair.toString() == "S/Z" })
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag sehen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.content.SoundFeederDerivationTest'`
Expected: Compile-Fehler „Unresolved reference: SoundFeederDerivation".

- [ ] **Step 3: `SoundFeederDerivation.kt` schreiben**

```kotlin
package app.abcvorschule.content

import kotlin.math.floor
import kotlin.random.Random

/**
 * Reine Ableitung des Laut-Fressers (design doc §3/§4): welche Lektion welches
 * Paar spielt und welche Karten sie zieht. Läuft über **alle** Lektionen in
 * Index-Reihenfolge, weil zwei Entscheidungen von der Vorgeschichte abhängen —
 * „am längsten nicht gespielt" und die Rotation durch den Wortvorrat. Nichts hier
 * ist zufällig außer der Kartenreihenfolge, und die ist aus der Lektions-ID gesät.
 */
object SoundFeederDerivation {
    const val Prompt = "Füttere die Laut-Fresser."

    /** L01 und L02 bleiben frei — dort lernt das Kind noch die App. */
    const val FirstLessonIndex = 3
    const val MaxCards = 7
    const val MinPerSide = 2
    const val MinSupply = 6
    const val MaxTwinPairs = 2

    data class Assignment(val pair: SoundPair, val replay: Boolean)

    /** Kartentaugliche Wörter je Seite, alphabetisch — die Reihenfolge, durch die rotiert wird. */
    data class Supply(val left: List<Atom>, val right: List<Atom>) {
        val total: Int get() = left.size + right.size
        val sufficient: Boolean
            get() = left.size >= MinPerSide && right.size >= MinPerSide && total >= MinSupply
    }

    fun supply(pack: ContentPack, pair: SoundPair): Supply {
        val worthy = pack.atoms.values.filter { SoundPairs.isCardWorthy(it) }
        fun side(own: String, other: String) = worthy
            .filter { atom ->
                val hit = if (pair.anywhere) SoundPairs.contains(atom.display, own) else SoundPairs.startsWith(atom.display, own)
                hit && !SoundPairs.contains(atom.display, other)
            }
            .sortedBy { it.display }
        return Supply(side(pair.left, pair.right), side(pair.right, pair.left))
    }

    fun assignments(pack: ContentPack): Map<String, Assignment> = walk(pack).mapValues { it.value.first }

    fun derive(pack: ContentPack): Map<String, SoundFeederRound> = walk(pack).mapValues { it.value.second }

    fun buildRounds(pack: ContentPack, lesson: Lesson): List<SoundFeederRound> =
        listOfNotNull(derive(pack)[lesson.id])

    /** Alle Atome, die irgendeine Lektion auf eine Karte legt — für „Kein Atom ohne Auftritt". */
    fun shownAtomIds(pack: ContentPack): Set<String> =
        derive(pack).values.flatMap { round -> round.cards.map { it.atomId } }.toSet()

    /**
     * Proportional zum Vorrat, aber jede Seite behält mindestens [MinPerSide] —
     * sonst könnte das Kind die letzten Karten abzählen statt hinzuhören.
     */
    internal fun splitCounts(total: Int, leftSupply: Int, rightSupply: Int): Pair<Int, Int> {
        val raw = floor(total * leftSupply.toFloat() / (leftSupply + rightSupply) + 0.5f).toInt()
        val left = raw.coerceIn(MinPerSide, total - MinPerSide)
        return left to (total - left)
    }

    private fun walk(pack: ContentPack): Map<String, Pair<Assignment, SoundFeederRound>> {
        val supplies = SoundPairs.table.associateWith { supply(pack, it) }
        val introduced = SoundPairs.table.flatMap { it.graphemes }.distinct()
            .associateWith { SoundPairs.introductionIndex(pack, it) }
        val lastPlayed = mutableMapOf<SoundPair, Int>()
        val sidePlays = mutableMapOf<String, Int>()
        val result = linkedMapOf<String, Pair<Assignment, SoundFeederRound>>()

        pack.lessons.sortedBy { it.index }.forEach { lesson ->
            if (lesson.index < FirstLessonIndex) return@forEach
            val focus = lesson.focusAtomIds.mapNotNull { pack.atoms[it]?.display }.toSet()
            fun eligible(pair: SoundPair) =
                pair.graphemes.all { g -> introduced[g]?.let { it <= lesson.index } == true } &&
                    supplies.getValue(pair).sufficient
            // Nie gespielt sortiert vor allem Gespielten (0 < jeder Lektionsindex),
            // dann das am längsten Zurückliegende, dann die Tabellenreihenfolge.
            val byRecency = compareBy<SoundPair>({ lastPlayed[it] ?: 0 }, { SoundPairs.table.indexOf(it) })

            var replay = false
            val pick = SoundPairTier.entries.firstNotNullOfOrNull { tier ->
                SoundPairs.table
                    .filter { it.tier == tier && it.graphemes.any { g -> g in focus } && eligible(it) }
                    .minWithOrNull(byRecency)
            } ?: run {
                replay = true
                SoundPairs.table
                    .filter { it.tier == SoundPairTier.Contrast && eligible(it) }
                    .minWithOrNull(byRecency)
            } ?: return@forEach

            lastPlayed[pick] = lesson.index
            val round = buildRound(pack, lesson, pick, supplies.getValue(pick), sidePlays)
            result[lesson.id] = Assignment(pick, replay) to round
        }
        return result
    }

    /**
     * Zwillinge zuerst, dann der Rest per Rotation: jede Seite beginnt dort im
     * alphabetischen Vorrat, wo dieser Laut zuletzt aufgehört hat — über alle Paare
     * hinweg, in denen er vorkommt. So kommt jedes T-Wort einmal dran, obwohl kein
     * einzelnes Paar den Vorrat ausschöpft (design doc §4).
     */
    private fun buildRound(
        pack: ContentPack,
        lesson: Lesson,
        pair: SoundPair,
        supply: Supply,
        sidePlays: MutableMap<String, Int>,
    ): SoundFeederRound {
        val total = minOf(MaxCards, supply.total)
        val (leftCount, rightCount) = splitCounts(total, supply.left.size, supply.right.size)
        val usedEmojis = mutableSetOf<String>()
        val usedIds = mutableSetOf<String>()
        val units = mutableListOf<List<SoundFeederCard>>()

        fun take(atom: Atom, side: FeederSide): SoundFeederCard? {
            if (atom.id in usedIds || atom.emoji in usedEmojis) return null
            usedIds += atom.id
            usedEmojis += atom.emoji
            return SoundFeederCard(atom.id, side)
        }

        if (!pair.anywhere) {
            val twins = supply.left.flatMap { l ->
                supply.right.filter { r -> SoundPairs.rest(l.display) == SoundPairs.rest(r.display) }.map { l to it }
            }
            for ((l, r) in twins.take(MaxTwinPairs)) {
                if (l.id in usedIds || r.id in usedIds || l.emoji in usedEmojis || r.emoji in usedEmojis) continue
                val a = take(l, FeederSide.left) ?: continue
                val b = take(r, FeederSide.right) ?: continue
                units += listOf(a, b)
            }
        }

        fun fill(words: List<Atom>, side: FeederSide, count: Int, grapheme: String) {
            var have = units.flatten().count { it.side == side }
            val plays = sidePlays[grapheme] ?: 0
            val offset = if (words.isEmpty()) 0 else (plays * count) % words.size
            var step = 0
            while (have < count && step < words.size) {
                val atom = words[(offset + step) % words.size]
                step += 1
                val card = take(atom, side) ?: continue
                units += listOf(card)
                have += 1
            }
            sidePlays[grapheme] = plays + 1
        }
        fill(supply.left, FeederSide.left, leftCount, pair.left)
        fill(supply.right, FeederSide.right, rightCount, pair.right)

        // Nur die Reihenfolge ist zufällig — und die ist aus der Lektions-ID gesät,
        // damit ein Kind, das wiederholt, dasselbe Spiel sieht.
        val rng = Random(lesson.id.hashCode())
        val cards = units.shuffled(rng).flatMap { unit ->
            if (unit.size == 2 && rng.nextBoolean()) unit.reversed() else unit
        }
        return SoundFeederRound(
            promptTts = Prompt,
            leftAtomId = SoundPairs.letterAtom(pack, pair.left)?.id ?: error("no letter atom for ${pair.left}"),
            rightAtomId = SoundPairs.letterAtom(pack, pair.right)?.id ?: error("no letter atom for ${pair.right}"),
            cards = cards,
        )
    }
}
```

- [ ] **Step 4: Derivation-Test laufen lassen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.content.SoundFeederDerivationTest'`
Expected: PASS. Weicht der Snapshot ab, **zuerst** die Ableitung gegen §3 des Specs prüfen (Stufenreihenfolge, `lastPlayed[it] ?: 0`, Tabellenreihenfolge), erst dann den Snapshot anfassen — er ist aus einer Simulation über den echten Pack entstanden.

- [ ] **Step 5: „Kein Atom ohne Auftritt" um die Fresser-Karten erweitern**

In `LessonCoverageTest.noAtomSitsInThePackWithoutEverBeingShown`, im `buildSet`, hinter `pack.lessons.forEach { addAll(it.focusAtomIds) }`:

```kotlin
            // Der Laut-Fresser ist abgeleitet: seine Karten stehen in keinem Task,
            // sind aber genauso ein Auftritt (design doc §4).
            addAll(SoundFeederDerivation.shownAtomIds(pack))
```

- [ ] **Step 6: Alle Unit-Tests laufen lassen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest`
Expected: alles grün — auch `LessonCoverageTest` wieder.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/SoundFeederDerivation.kt app/src/test/java/app/abcvorschule/content/SoundFeederDerivationTest.kt app/src/test/java/app/abcvorschule/content/LessonCoverageTest.kt
git commit -m "feat(content): SoundFeederDerivation — Paar je Lektion, Karten per Rotation und Zwillingen

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 5: Einschub in die Lektion

**Files:**
- Create: `app/src/main/java/app/abcvorschule/session/SoundFeederInsertion.kt`
- Modify: `app/src/main/java/app/abcvorschule/session/SessionTrainers.kt`
- Test: `app/src/test/java/app/abcvorschule/session/SoundFeederInsertionTest.kt`
- Modify: `app/src/test/java/app/abcvorschule/session/SessionTrainersTest.kt` (ein Test dazu)

**Interfaces:**
- Consumes: `SoundFeederDerivation.buildRounds(pack, lesson)`, `SoundFeederSpec`.
- Produces: `object SoundFeederInsertion { fun insertSoundFeeder(trainers: List<ScheduledTrainer>, pack: ContentPack, lesson: Lesson): List<ScheduledTrainer> }`; Spec-ID `"${lesson.id}:sound_feeder"`.

- [ ] **Step 1: Failing Tests schreiben**

```kotlin
package app.abcvorschule.session

import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.LetterTraceSpec
import app.abcvorschule.content.SoundFeederSpec
import app.abcvorschule.content.SymbolHuntMode
import app.abcvorschule.content.SymbolHuntSpec
import app.abcvorschule.content.SyllableMergeSpec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundFeederInsertionTest {
    private val pack = ContentRepository.fromClasspath().load()

    private fun scheduled(lessonId: String) =
        pack.tasksOf(pack.lesson(lessonId)).map { ScheduledTrainer(spec = it) }

    private fun withHunts(lessonId: String) =
        SymbolHuntInsertion.insertSymbolHunts(scheduled(lessonId), pack, lessonId, pack.lesson(lessonId).index)

    private fun insert(lessonId: String) =
        SoundFeederInsertion.insertSoundFeeder(withHunts(lessonId), pack, pack.lesson(lessonId))

    private fun isLetterHunt(trainer: ScheduledTrainer) =
        trainer.spec is SymbolHuntSpec && trainer.spec.id.endsWith(":symbol_hunt:${SymbolHuntMode.letter.name}")

    @Test
    fun theFeederLandsRightAfterTheLetterHunt() {
        val result = insert("l13")
        val feeder = result.indexOfFirst { it.spec is SoundFeederSpec }
        val hunt = result.indexOfLast { isLetterHunt(it) }
        assertTrue("no letter hunt in l13", hunt >= 0)
        assertEquals(hunt + 1, feeder)
    }

    @Test
    fun theFeederComesBeforeTheFirstSyllableMergeInEveryLessonThatHasBoth() {
        var checked = 0
        pack.authoredLessons.forEach { lesson ->
            val result = insert(lesson.id)
            val feeder = result.indexOfFirst { it.spec is SoundFeederSpec }
            val merge = result.indexOfFirst { it.spec is SyllableMergeSpec }
            if (feeder >= 0 && merge >= 0) {
                assertTrue("${lesson.id}: feeder $feeder must precede merge $merge", feeder < merge)
                checked++
            }
        }
        assertTrue(checked > 0)
    }

    @Test
    fun withoutAHuntTheFeederFollowsTheLastTrace() {
        val base = scheduled("l13")
        val result = SoundFeederInsertion.insertSoundFeeder(base, pack, pack.lesson("l13"))
        val feeder = result.indexOfFirst { it.spec is SoundFeederSpec }
        assertEquals(result.indexOfLast { it.spec is LetterTraceSpec } + 1, feeder)
    }

    @Test
    fun lessonsOneAndTwoGetNoFeederEveryOtherLessonExactlyOne() {
        pack.authoredLessons.forEach { lesson ->
            val count = insert(lesson.id).count { it.spec is SoundFeederSpec }
            assertEquals(lesson.id, if (lesson.index < 3) 0 else 1, count)
        }
    }

    @Test
    fun theSpecIdIsDerivedFromTheLesson() {
        assertEquals("l13:sound_feeder", insert("l13").first { it.spec is SoundFeederSpec }.spec.id)
    }

    @Test
    fun theOriginalTrainersKeepTheirOrder() {
        val original = withHunts("l13").map { it.spec.id }
        assertEquals(original, insert("l13").filter { it.spec !is SoundFeederSpec }.map { it.spec.id })
    }
}
```

In `SessionTrainersTest` ergänzen:

```kotlin
    @Test
    fun theFeederSitsBetweenLetterHuntAndSyllableMergeAndCarriesScaffoldsForBothSounds() {
        val assembled = assemble("l13")
        val feederIndex = assembled.indexOfFirst { it.spec is SoundFeederSpec }
        assertTrue(feederIndex > 0)
        assertTrue(assembled[feederIndex - 1].spec is SymbolHuntSpec)
        assertTrue(assembled.drop(feederIndex + 1).any { it.spec is SyllableMergeSpec })
        val feeder = assembled[feederIndex]
        (feeder.spec as SoundFeederSpec).rounds.single().let { round ->
            assertEquals(ScaffoldLevel.Advanced, feeder.scaffolds[round.leftAtomId])
            assertEquals(ScaffoldLevel.Advanced, feeder.scaffolds[round.rightAtomId])
        }
    }
```

(Imports `SoundFeederSpec`, `SymbolHuntSpec`, `SyllableMergeSpec` aus `app.abcvorschule.content` ergänzen.)

- [ ] **Step 2: Tests laufen lassen, Fehlschlag sehen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.session.SoundFeederInsertionTest' --tests 'app.abcvorschule.session.SessionTrainersTest'`
Expected: Compile-Fehler „Unresolved reference: SoundFeederInsertion".

- [ ] **Step 3: Einschub schreiben**

```kotlin
package app.abcvorschule.session

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.Lesson
import app.abcvorschule.content.LetterTraceSpec
import app.abcvorschule.content.SoundFeederDerivation
import app.abcvorschule.content.SoundFeederSpec
import app.abcvorschule.content.SymbolHuntMode
import app.abcvorschule.content.SymbolHuntSpec

/**
 * Schiebt den Laut-Fresser zur Laufzeit hinter die Buchstaben-Jagd (design doc §2):
 * das Kind hat den neuen Buchstaben gerade nachgezeichnet und gejagt, jetzt hört
 * es ihn gegen einen Bekannten, bevor es ihn in Silben verbaut. Ohne Jagd hängt
 * er am letzten Spurensucher. Ergibt die Ableitung nichts (L01, L02), passiert
 * nichts — dieselbe stille Degradation wie beim Wort-Detektiv.
 */
object SoundFeederInsertion {
    fun insertSoundFeeder(
        trainers: List<ScheduledTrainer>,
        pack: ContentPack,
        lesson: Lesson,
    ): List<ScheduledTrainer> {
        val letterHuntSuffix = ":symbol_hunt:${SymbolHuntMode.letter.name}"
        val hunt = trainers.indexOfLast { it.spec is SymbolHuntSpec && it.spec.id.endsWith(letterHuntSuffix) }
        val anchor = if (hunt >= 0) hunt else trainers.indexOfLast { it.spec is LetterTraceSpec }
        if (anchor < 0) return trainers
        val rounds = SoundFeederDerivation.buildRounds(pack, lesson)
        if (rounds.isEmpty()) return trainers
        val feeder = ScheduledTrainer(spec = SoundFeederSpec(id = "${lesson.id}:sound_feeder", rounds = rounds))
        return trainers.toMutableList().apply { add(anchor + 1, feeder) }
    }
}
```

`SessionTrainers.assemble` ergänzen (Kommentar oben um den Fresser erweitern):

```kotlin
        val withHunts = SymbolHuntInsertion.insertSymbolHunts(authored, pack, lesson.id, lesson.index)
        val withFeeder = SoundFeederInsertion.insertSoundFeeder(withHunts, pack, lesson)
        val withDetective = SymbolInWordInsertion.insertSymbolInWord(withFeeder, pack, lesson)
        return withDetective.map { schedule(it.spec) }
```

- [ ] **Step 4: Alle Unit-Tests laufen lassen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest`
Expected: grün, inklusive `SessionTrainersTest.everyTrainerCarriesAScaffoldForEveryAtomItScores` (deckt die neuen gescorten Atome automatisch ab) und `LessonSessionTest`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/session/SoundFeederInsertion.kt app/src/main/java/app/abcvorschule/session/SessionTrainers.kt app/src/test/java/app/abcvorschule/session/SoundFeederInsertionTest.kt app/src/test/java/app/abcvorschule/session/SessionTrainersTest.kt
git commit -m "feat(session): Laut-Fresser hinter der Buchstaben-Jagd eingeschoben

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 6: Monster-Stimme in der Sprachausgabe

**Files:**
- Create: `app/src/main/java/app/abcvorschule/speech/VoiceStyle.kt`
- Modify: `app/src/main/java/app/abcvorschule/speech/ClipPlayer.kt`
- Modify: `app/src/main/java/app/abcvorschule/speech/SpeechController.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt` (`TrainerCallbacks`)
- Modify: `app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt` (Parameter von `TaskShell` und `PracticeBody`, Konstruktion der `TrainerCallbacks`)
- Modify: `app/src/main/java/app/abcvorschule/MainActivity.kt` (Aufruf von `TaskShell`)
- Test: `app/src/test/java/app/abcvorschule/speech/VoiceStyleTest.kt`

**Interfaces:**
- Produces: `enum class VoiceStyle(val pitch: Float) { Normal(1f), MonsterLow(0.75f), MonsterHigh(1.3f) }`; `data class SpokenPart(val text: String, val voice: VoiceStyle = VoiceStyle.Normal)`;
  `SpeechController.speak(text, channel = Primary, voice = Normal)`; `speakAndAwait(text, channel, timeoutMs, voice)`; `speakAndAwaitSequence(parts: List<SpokenPart>, timeoutMs = 10_000L, onPartComplete: ((Int) -> Unit)? = null)` (Überladung; die `List<String>`-Fassung bleibt);
  `TrainerCallbacks.onSpeakParts: suspend (List<SpokenPart>) -> Unit` und `TrainerCallbacks.onSpeakFeedbackVoiced: (SpokenPart) -> Unit`, beide mit Default `{}` bzw. `{ }`.

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package app.abcvorschule.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStyleTest {
    @Test
    fun normalIsExactlyOne() {
        assertEquals(1f, VoiceStyle.Normal.pitch)
    }

    @Test
    fun theTwoMonstersSitOnEitherSideOfNormal() {
        assertTrue(VoiceStyle.MonsterLow.pitch < 1f)
        assertTrue(VoiceStyle.MonsterHigh.pitch > 1f)
        // Android akzeptiert Tonhöhen in [0.5, 2.0]; darüber hinaus klingt nichts mehr wie Sprache.
        VoiceStyle.entries.forEach { assertTrue(it.pitch in 0.5f..2f) }
    }

    @Test
    fun aSpokenPartDefaultsToTheNormalVoice() {
        assertEquals(VoiceStyle.Normal, SpokenPart("Sonne").voice)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag sehen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.speech.VoiceStyleTest'`
Expected: Compile-Fehler „Unresolved reference: VoiceStyle".

- [ ] **Step 3: `VoiceStyle.kt`**

```kotlin
package app.abcvorschule.speech

/**
 * Wie eine Äußerung klingt. Die Monster-Stimmen des Laut-Fressers sind **nur
 * Tonhöhe** auf den kuratierten Clips (design doc §7): Artikulation und Dauer
 * bleiben, ein Kind, das gerade S gegen Sch lernt, hört ein sauberes „sss" — nur
 * tiefer oder höher. Zwei Werte, weil zwei Fresser unterscheidbar sein müssen.
 */
enum class VoiceStyle(val pitch: Float) {
    Normal(1f),
    MonsterLow(0.75f),
    MonsterHigh(1.3f),
}

/** Ein Teil einer gesprochenen Sequenz mit seiner Stimme. */
data class SpokenPart(val text: String, val voice: VoiceStyle = VoiceStyle.Normal)
```

- [ ] **Step 4: `ClipPlayer.play` bekommt eine Tonhöhe**

Signatur ändern zu `fun play(file: String, pitch: Float = 1f, onComplete: () -> Unit): Boolean` und im `setOnPreparedListener`:

```kotlin
            mp.setOnPreparedListener { prepared ->
                // Zwischenzeitliches stop()/play() hat den Player abgelöst — ein
                // nachlaufendes onPrepared darf dann nichts mehr anwerfen.
                if (player !== prepared) return@setOnPreparedListener
                if (pitch != 1f) {
                    // Erst im Prepared-Zustand erlaubt; setPlaybackParams startet die
                    // Wiedergabe selbst, start() danach ist idempotent. Schlägt es
                    // fehl (alter Codec), spielt der Clip eben in normaler Stimme.
                    runCatching {
                        prepared.playbackParams = PlaybackParams().setPitch(pitch).setSpeed(1f)
                    }
                }
                prepared.start()
            }
```

Import `android.media.PlaybackParams`.

- [ ] **Step 5: `SpeechController` reicht die Stimme durch**

- `fun speak(text: String, channel: SpeechChannel = SpeechChannel.Primary, voice: VoiceStyle = VoiceStyle.Normal)` — ruft `playClip(text, channel, voice, …)` und `enqueueTts(text, channel, id, voice)`.
- `suspend fun speakAndAwait(text, channel = Primary, timeoutMs = 10_000L, voice: VoiceStyle = VoiceStyle.Normal)` → `awaitSpeak(text, channel, timeoutMs, voice)`.
- `private suspend fun awaitSpeak(text, channel, timeoutMs, voice: VoiceStyle): Long` — beide inneren Aufrufe bekommen `voice`.
- `private fun enqueueTts(text, channel, id, voice: VoiceStyle): Boolean` — direkt vor dem `when (channel)`:

```kotlin
        // Je Äußerung gesetzt, nie zurückgesetzt: TextToSpeech kopiert die Tonhöhe
        // beim speak()-Aufruf in die Anfrage, spätere Aufrufe setzen sie neu. So kann
        // eine Monster-Ansage nie in die nächste normale Ansage „hineinlecken".
        engine.setPitch(voice.pitch)
```

- `private fun playClip(text, channel, voice: VoiceStyle, onComplete)` → `clipPlayers.getValue(channel).play(entry.file, voice.pitch) { … }`.
- Bestehende `speakAndAwaitSequence(texts: List<String>, …)` delegiert an die neue Fassung:

```kotlin
    suspend fun speakAndAwaitSequence(
        texts: List<String>,
        timeoutMs: Long = 10_000L,
        onPartComplete: ((index: Int) -> Unit)? = null,
    ) = speakAndAwaitSequence(texts.map { SpokenPart(it) }, timeoutMs, onPartComplete)

    /** Wie oben, aber jeder Teil bringt seine Stimme mit (Laut-Fresser, design doc §7). */
    suspend fun speakAndAwaitSequence(
        parts: List<SpokenPart>,
        timeoutMs: Long = 10_000L,
        onPartComplete: ((index: Int) -> Unit)? = null,
    ) {
        parts.withIndex().filter { it.value.text.isNotBlank() }.forEach { (index, part) ->
            val generation = awaitSpeak(part.text, SpeechChannel.Primary, timeoutMs, part.voice)
            if (primaryGeneration.get() != generation) return
            onPartComplete?.invoke(index)
        }
    }
```

Achtung `MainActivity`: `onSpeakPromptSequence = speech::speakAndAwaitSequence` ist nach der Überladung mehrdeutig — durch ein Lambda ersetzen: `onSpeakPromptSequence = { texts -> speech.speakAndAwaitSequence(texts) }`. Ebenso `onSpeak = speech::speak` bleibt gültig (Defaults werden adaptiert); falls der Compiler meckert: `onSpeak = { text -> speech.speak(text) }`.

- [ ] **Step 6: Callbacks durchreichen**

`TrainerCallbacks` um zwei Felder ergänzen:

```kotlin
    /** Primär-Kanal, Sequenz mit Stimme je Teil — der Laut-Fresser spricht Ansage,
     * Fressen und Spucken selbst (design doc §5/§7). */
    val onSpeakParts: suspend (List<SpokenPart>) -> Unit = {},
    /** Feedback-Kanal mit Stimme: Tipp auf einen Fresser. */
    val onSpeakFeedbackVoiced: (SpokenPart) -> Unit = {},
```

`TaskShell(...)` und `PracticeBody(...)` bekommen die Parameter `onSpeakParts: suspend (List<SpokenPart>) -> Unit` und `onSpeakFeedbackVoiced: (SpokenPart) -> Unit`, reichen sie durch und setzen sie in `TrainerCallbacks(...)`. `MainActivity` liefert:

```kotlin
        onSpeakParts = { parts -> speech.speakAndAwaitSequence(parts) },
        onSpeakFeedbackVoiced = { part -> speech.speak(part.text, channel = SpeechChannel.Feedback, voice = part.voice) },
```

- [ ] **Step 7: Bauen und Tests laufen lassen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: Build grün, `VoiceStyleTest` grün. Instrumentierte Tests, die `TrainerCallbacks(...)` positional konstruieren, gibt es nicht (nur `TaskShell` baut es) — die Defaults schützen trotzdem.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/abcvorschule/speech app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt app/src/main/java/app/abcvorschule/MainActivity.kt app/src/test/java/app/abcvorschule/speech/VoiceStyleTest.kt
git commit -m "feat(speech): VoiceStyle — Tonhöhe je Äußerung für die Monster-Stimme

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 7: `SoundFeederSpeech`

**Files:**
- Create: `app/src/main/java/app/abcvorschule/content/SoundFeederSpeech.kt`
- Test: `app/src/test/java/app/abcvorschule/content/SoundFeederSpeechTest.kt`

**Interfaces:**
- Consumes: `SpokenPart`, `VoiceStyle` (Task 6), `SoundFeederRound`, `SoundFeederCard`, `FeederSide`.
- Produces: `object SoundFeederSpeech { const val Yuck = "Bäh!"; const val Content = "Mmmmh!"; fun voiceFor(side): VoiceStyle; fun soundPart(round, side, pack): SpokenPart; fun wordPart(card, pack): SpokenPart; fun introParts(round, pack): List<SpokenPart>; fun eatParts(round, card, pack): List<SpokenPart>; fun missParts(round, card, wrongSide, pack): List<SpokenPart>; fun finishParts(round, pack): List<SpokenPart> }`

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package app.abcvorschule.content

import app.abcvorschule.speech.SpokenPart
import app.abcvorschule.speech.VoiceStyle
import org.junit.Assert.assertEquals
import org.junit.Test

class SoundFeederSpeechTest {
    private val pack = ContentRepository.fromClasspath().load()
    private val round = SoundFeederDerivation.derive(pack).getValue("l13") // S/Sch
    private val sonne = SoundFeederCard("sonne", FeederSide.left)

    @Test
    fun theIntroIsThePromptThenEachMonsterInItsOwnVoice() {
        assertEquals(
            listOf(
                SpokenPart("Füttere die Laut-Fresser."),
                SpokenPart("S", VoiceStyle.MonsterLow),
                SpokenPart("Sch", VoiceStyle.MonsterHigh),
            ),
            SoundFeederSpeech.introParts(round, pack),
        )
    }

    @Test
    fun eatingSpeaksTheSoundInMonsterVoiceThenTheBareWord() {
        // Ohne Artikel: „Sss … die Sonne" würde Laut und Wort trennen (design doc §6).
        assertEquals(
            listOf(SpokenPart("S", VoiceStyle.MonsterLow), SpokenPart("Sonne")),
            SoundFeederSpeech.eatParts(round, sonne, pack),
        )
    }

    @Test
    fun spittingSaysYuckInTheWrongMonstersVoiceThenRepeatsTheWord() {
        assertEquals(
            listOf(SpokenPart("Bäh!", VoiceStyle.MonsterHigh), SpokenPart("Sonne")),
            SoundFeederSpeech.missParts(round, sonne, wrongSide = FeederSide.right, pack = pack),
        )
    }

    @Test
    fun theFinishIsBothBurpsThenContentment() {
        assertEquals(
            listOf(
                SpokenPart("S", VoiceStyle.MonsterLow),
                SpokenPart("Sch", VoiceStyle.MonsterHigh),
                SpokenPart("Mmmmh!", VoiceStyle.MonsterLow),
            ),
            SoundFeederSpeech.finishParts(round, pack),
        )
    }

    @Test
    fun anUnknownAtomFallsBackToItsId() {
        assertEquals(SpokenPart("nirgends"), SoundFeederSpeech.wordPart(SoundFeederCard("nirgends", FeederSide.left), pack))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag sehen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.content.SoundFeederSpeechTest'`
Expected: Compile-Fehler „Unresolved reference: SoundFeederSpeech".

- [ ] **Step 3: Implementieren**

```kotlin
package app.abcvorschule.content

import app.abcvorschule.speech.SpokenPart
import app.abcvorschule.speech.VoiceStyle

/**
 * Was der Laut-Fresser wann sagt (design doc §5–§7). Laute und Wörter sind die
 * kuratierten Lemma-Clips; nur die Stimme wechselt. „Bäh!" und „Mmmmh!" sind die
 * einzigen Monster-eigenen Strings (extra-strings.json, Profil `monster`).
 */
object SoundFeederSpeech {
    const val Yuck = "Bäh!"
    const val Content = "Mmmmh!"

    fun voiceFor(side: FeederSide): VoiceStyle =
        if (side == FeederSide.left) VoiceStyle.MonsterLow else VoiceStyle.MonsterHigh

    private fun lemma(pack: ContentPack, atomId: String): String =
        pack.atoms[atomId]?.lemma?.takeIf { it.isNotBlank() }
            ?: pack.atoms[atomId]?.display?.takeIf { it.isNotBlank() }
            ?: atomId

    fun soundPart(round: SoundFeederRound, side: FeederSide, pack: ContentPack): SpokenPart =
        SpokenPart(lemma(pack, round.atomIdFor(side)), voiceFor(side))

    fun wordPart(card: SoundFeederCard, pack: ContentPack): SpokenPart =
        SpokenPart(lemma(pack, card.atomId))

    /** Ansage, dann stellt sich links vor, dann rechts — statt einer Frage (§7). */
    fun introParts(round: SoundFeederRound, pack: ContentPack): List<SpokenPart> = listOf(
        SpokenPart(round.promptTts),
        soundPart(round, FeederSide.left, pack),
        soundPart(round, FeederSide.right, pack),
    )

    /** „Sss … Sonne." — Laut in Monster-Stimme, Wort normal, ohne Artikel. */
    fun eatParts(round: SoundFeederRound, card: SoundFeederCard, pack: ContentPack): List<SpokenPart> =
        listOf(soundPart(round, card.side, pack), wordPart(card, pack))

    fun missParts(round: SoundFeederRound, card: SoundFeederCard, wrongSide: FeederSide, pack: ContentPack): List<SpokenPart> =
        listOf(SpokenPart(Yuck, voiceFor(wrongSide)), wordPart(card, pack))

    /** Beide rülpsen ihren Laut, dann ein gemeinsames „Mmmmh!". */
    fun finishParts(round: SoundFeederRound, pack: ContentPack): List<SpokenPart> = listOf(
        soundPart(round, FeederSide.left, pack),
        soundPart(round, FeederSide.right, pack),
        SpokenPart(Content, VoiceStyle.MonsterLow),
    )
}
```

- [ ] **Step 4: Test laufen lassen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.content.SoundFeederSpeechTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/SoundFeederSpeech.kt app/src/test/java/app/abcvorschule/content/SoundFeederSpeechTest.kt
git commit -m "feat(content): SoundFeederSpeech — Ansage, Fressen, Spucken, Sattwerden

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 8: `SoundFeederProgress` — der Zustandsautomat

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SoundFeederProgress.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/SoundFeederProgressTest.kt`

**Interfaces:**
- Produces: `data class SoundFeederState(cards, nextIndex = 0, missesOnCard = 0, reportedMissThisRound = false, wrongSide: FeederSide? = null, wrongNonce = 0)` mit `current`, `remaining`, `eaten`, `hintActive`, `eatenOn(side)`; `enum class SoundFeederDropOutcome { Eaten, RoundComplete, Miss, MissAlreadyReported, Ignored }`; `data class SoundFeederDropResult(state, outcome)`; `object SoundFeederProgress { const val HintAfterMisses = 2; fun initialState(round): SoundFeederState; fun drop(state, side: FeederSide?): SoundFeederDropResult }`

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package app.abcvorschule.ui.exercise

import app.abcvorschule.content.FeederSide
import app.abcvorschule.content.SoundFeederCard
import app.abcvorschule.content.SoundFeederRound
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundFeederProgressTest {
    private val round = SoundFeederRound(
        promptTts = "Füttere die Laut-Fresser.",
        leftAtomId = "letter-s",
        rightAtomId = "letter-sch",
        cards = listOf(
            SoundFeederCard("sonne", FeederSide.left),
            SoundFeederCard("schuh", FeederSide.right),
            SoundFeederCard("salat", FeederSide.left),
            SoundFeederCard("schaf", FeederSide.right),
        ),
    )
    private val start = SoundFeederProgress.initialState(round)

    @Test
    fun theFirstCardIsUpAndNothingIsEaten() {
        assertEquals("sonne", start.current?.atomId)
        assertEquals(4, start.remaining)
        assertEquals(0, start.eaten)
        assertFalse(start.hintActive)
    }

    @Test
    fun theRightMonsterEatsTheCardAndTheNextOneComesUp() {
        val result = SoundFeederProgress.drop(start, FeederSide.left)
        assertEquals(SoundFeederDropOutcome.Eaten, result.outcome)
        assertEquals("schuh", result.state.current?.atomId)
        assertEquals(1, result.state.eatenOn(FeederSide.left))
        assertEquals(0, result.state.eatenOn(FeederSide.right))
    }

    @Test
    fun theWrongMonsterReportsAMissOnceAndKeepsTheCard() {
        val first = SoundFeederProgress.drop(start, FeederSide.right)
        assertEquals(SoundFeederDropOutcome.Miss, first.outcome)
        assertEquals("sonne", first.state.current?.atomId)
        assertEquals(FeederSide.right, first.state.wrongSide)
        assertEquals(1, first.state.wrongNonce)

        val second = SoundFeederProgress.drop(first.state, FeederSide.right)
        assertEquals(SoundFeederDropOutcome.MissAlreadyReported, second.outcome)
        assertEquals(2, second.state.wrongNonce)
    }

    @Test
    fun aMissOnALaterCardIsNotReportedAgainEither() {
        val eaten = SoundFeederProgress.drop(start, FeederSide.left).state
        val miss = SoundFeederProgress.drop(eaten, FeederSide.left) // schuh gehört rechts
        assertEquals(SoundFeederDropOutcome.Miss, miss.outcome)
        val eatenAgain = SoundFeederProgress.drop(miss.state, FeederSide.right).state
        val missAgain = SoundFeederProgress.drop(eatenAgain, FeederSide.right) // salat gehört links
        assertEquals(SoundFeederDropOutcome.MissAlreadyReported, missAgain.outcome)
    }

    @Test
    fun theHintAppearsOnTheSecondMissOfTheSameCardAndClearsWhenItIsEaten() {
        val once = SoundFeederProgress.drop(start, FeederSide.right).state
        assertFalse(once.hintActive)
        val twice = SoundFeederProgress.drop(once, FeederSide.right).state
        assertTrue(twice.hintActive)
        val eaten = SoundFeederProgress.drop(twice, FeederSide.left).state
        assertFalse(eaten.hintActive)
        assertEquals(0, eaten.missesOnCard)
    }

    @Test
    fun theLastCardCompletesTheRound() {
        var state = start
        listOf(FeederSide.left, FeederSide.right, FeederSide.left).forEach {
            state = SoundFeederProgress.drop(state, it).state
        }
        val last = SoundFeederProgress.drop(state, FeederSide.right)
        assertEquals(SoundFeederDropOutcome.RoundComplete, last.outcome)
        assertNull(last.state.current)
        assertEquals(0, last.state.remaining)
    }

    @Test
    fun droppingOutsideBothMonstersOrAfterTheEndChangesNothing() {
        val outside = SoundFeederProgress.drop(start, null)
        assertEquals(SoundFeederDropOutcome.Ignored, outside.outcome)
        assertEquals(start, outside.state)
        var state = start
        repeat(4) { state = SoundFeederProgress.drop(state, round.cards[it].side).state }
        assertEquals(SoundFeederDropOutcome.Ignored, SoundFeederProgress.drop(state, FeederSide.left).outcome)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag sehen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.ui.exercise.SoundFeederProgressTest'`
Expected: Compile-Fehler.

- [ ] **Step 3: Implementieren**

```kotlin
package app.abcvorschule.ui.exercise

import app.abcvorschule.content.FeederSide
import app.abcvorschule.content.SoundFeederCard
import app.abcvorschule.content.SoundFeederRound

/**
 * Zustand einer Fresser-Runde (design doc §6). Die Runde selbst ist unveränderlich;
 * hier steht nur, wie weit das Kind ist und wie oft es bei der aktuellen Karte
 * danebenlag.
 */
data class SoundFeederState(
    val cards: List<SoundFeederCard>,
    val nextIndex: Int = 0,
    val missesOnCard: Int = 0,
    val reportedMissThisRound: Boolean = false,
    /** Fresser, der zuletzt gespuckt hat — für die Schüttel-Animation. */
    val wrongSide: FeederSide? = null,
    /** Zählt jeden Fehlgriff, damit derselbe Fresser zweimal hintereinander schütteln kann. */
    val wrongNonce: Int = 0,
) {
    val current: SoundFeederCard? get() = cards.getOrNull(nextIndex)
    val remaining: Int get() = cards.size - nextIndex
    val eaten: Int get() = nextIndex
    val hintActive: Boolean get() = missesOnCard >= SoundFeederProgress.HintAfterMisses
    fun eatenOn(side: FeederSide): Int = cards.take(nextIndex).count { it.side == side }
}

enum class SoundFeederDropOutcome { Eaten, RoundComplete, Miss, MissAlreadyReported, Ignored }

data class SoundFeederDropResult(val state: SoundFeederState, val outcome: SoundFeederDropOutcome)

/**
 * Ein Fehlgriff kostet nichts; der erste der Runde wird einmal gemeldet
 * (`reportedMissThisRound`, wie in der Jagd), damit ein Kind, das rät, die
 * Statistik beider Laute nicht ruiniert. Der Hinweis hängt an der Karte, nicht an
 * der Runde: beim zweiten Fehlgriff bei *derselben* Karte reißt der richtige
 * Fresser das Maul auf. Kein „Zeig mir" — bei zwei Zielen ist das die Auflösung.
 */
object SoundFeederProgress {
    const val HintAfterMisses = 2

    fun initialState(round: SoundFeederRound): SoundFeederState = SoundFeederState(cards = round.cards)

    fun drop(state: SoundFeederState, side: FeederSide?): SoundFeederDropResult {
        val card = state.current ?: return SoundFeederDropResult(state, SoundFeederDropOutcome.Ignored)
        if (side == null) return SoundFeederDropResult(state, SoundFeederDropOutcome.Ignored)
        if (side == card.side) {
            val next = state.copy(nextIndex = state.nextIndex + 1, missesOnCard = 0, wrongSide = null)
            val outcome = if (next.current == null) SoundFeederDropOutcome.RoundComplete else SoundFeederDropOutcome.Eaten
            return SoundFeederDropResult(next, outcome)
        }
        val alreadyReported = state.reportedMissThisRound
        val next = state.copy(
            missesOnCard = state.missesOnCard + 1,
            reportedMissThisRound = true,
            wrongSide = side,
            wrongNonce = state.wrongNonce + 1,
        )
        val outcome = if (alreadyReported) SoundFeederDropOutcome.MissAlreadyReported else SoundFeederDropOutcome.Miss
        return SoundFeederDropResult(next, outcome)
    }
}
```

- [ ] **Step 4: Test laufen lassen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.ui.exercise.SoundFeederProgressTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SoundFeederProgress.kt app/src/test/java/app/abcvorschule/ui/exercise/SoundFeederProgressTest.kt
git commit -m "feat(exercise): SoundFeederProgress — Drop, Miss, Hinweis, Fertig

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 9: `SoundFeederSizing`

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SoundFeederSizing.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/SoundFeederSizingTest.kt`

**Interfaces:**
- Consumes: `TaskPromptSizing.pictureSp(fontScale)`.
- Produces: `object SoundFeederSizing { const val StageContentDp = 396f; const val CreatureGapDp = 16f; const val MinCreatureDp = 120f; const val MaxCreatureDp = 176f; const val CreatureAspect = 1.1f; const val BellyWidthFraction = 0.78f; const val GlyphAdvanceEm = 0.62f; const val MaxBellyGlyphSp = 36f; const val MinBellyGlyphSp = 16f; const val MinCardDp = 120f; const val CardEmojiFactor = 1.5f; const val PileCardWidthDp = 22f; const val PileCardHeightDp = 30f; const val PileStepDp = 4f; fun creatureWidthDp(stageWidthDp): Float; fun creatureHeightDp(widthDp): Float; fun bellyGlyphSp(labelChars, creatureWidthDp, fontScale): Float; fun cardSizeDp(fontScale): Float; fun pileWidthDp(count): Float; fun labelChars(primary: String, alternate: String?): Int }`

- [ ] **Step 1: Failing Test schreiben**

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundFeederSizingTest {
    @Test
    fun twoCreaturesAndTheGapFitTheNarrowestStage() {
        // 320dp-Gerät: ExerciseStage lässt 296dp, zwei Figuren plus 16dp Lücke.
        val width = SoundFeederSizing.creatureWidthDp(296f)
        assertTrue(2 * width + SoundFeederSizing.CreatureGapDp <= 296f)
        assertTrue(width >= SoundFeederSizing.MinCreatureDp)
    }

    @Test
    fun creaturesStopGrowingOnWideStages() {
        assertEquals(SoundFeederSizing.MaxCreatureDp, SoundFeederSizing.creatureWidthDp(396f))
        assertEquals(SoundFeederSizing.MaxCreatureDp, SoundFeederSizing.creatureWidthDp(800f))
    }

    @Test
    fun theBellyGlyphNeverLeavesTheBellyEvenForSchAtFontScaleOnePointThree() {
        val width = SoundFeederSizing.creatureWidthDp(296f)
        val chars = SoundFeederSizing.labelChars("Sch", "sch") // "Sch / sch"
        listOf(1f, 1.3f).forEach { scale ->
            val sp = SoundFeederSizing.bellyGlyphSp(chars, width, scale)
            val renderedWidthDp = chars * SoundFeederSizing.GlyphAdvanceEm * sp * scale
            assertTrue("scale $scale: $renderedWidthDp dp on ${width * SoundFeederSizing.BellyWidthFraction}", renderedWidthDp <= width * SoundFeederSizing.BellyWidthFraction + 0.01f)
            assertTrue(sp >= SoundFeederSizing.MinBellyGlyphSp)
        }
    }

    @Test
    fun aSingleLetterGetsTheFullGlyphSize() {
        assertEquals(SoundFeederSizing.MaxBellyGlyphSp, SoundFeederSizing.bellyGlyphSp(SoundFeederSizing.labelChars("ß", null), 176f, 1f))
    }

    @Test
    fun labelCharsCountsBothFormsAndTheSeparator() {
        assertEquals(5, SoundFeederSizing.labelChars("S", "s")) // "S / s"
        assertEquals(9, SoundFeederSizing.labelChars("Sch", "sch"))
        assertEquals(2, SoundFeederSizing.labelChars("ck", null))
    }

    @Test
    fun theCardStaysAtOneAndAHalfKidTouchAtEverySystemFontScale() {
        // TaskPromptSizing.pictureSp deckelt die *effektive* Bildgröße auf 84dp: die
        // Karte ist bei 1.0, 1.3 und 2.0 praktisch gleich groß (Ganzzahl-Kürzung
        // nimmt bei 1.3 ein paar Zehntel) und nie kleiner als 120dp.
        listOf(1f, 1.3f, 2f).forEach { scale ->
            val size = SoundFeederSizing.cardSizeDp(scale)
            assertTrue("scale $scale: $size", size >= SoundFeederSizing.MinCardDp && size <= 130f)
        }
    }

    @Test
    fun thePileShrinksToNothing() {
        assertEquals(0f, SoundFeederSizing.pileWidthDp(0))
        assertEquals(SoundFeederSizing.PileCardWidthDp, SoundFeederSizing.pileWidthDp(1))
        assertEquals(SoundFeederSizing.PileCardWidthDp + 5 * SoundFeederSizing.PileStepDp, SoundFeederSizing.pileWidthDp(6))
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag sehen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.ui.exercise.SoundFeederSizingTest'`
Expected: Compile-Fehler.

- [ ] **Step 3: Implementieren**

```kotlin
package app.abcvorschule.ui.exercise

/**
 * Maße des Laut-Fressers, Compose-frei (design doc §8). Alles, was mit der
 * Systemschriftgröße wächst, nimmt `fontScale` als Parameter — das Testgerät steht
 * auf 1.3, und ein Bauch-Glyph, der bei 1.0 passt, darf bei 1.3 nicht aus der
 * Figur laufen.
 */
object SoundFeederSizing {
    /** ExerciseStage deckelt auf 420dp und polstert 12dp je Seite. */
    const val StageContentDp = 396f
    const val CreatureGapDp = 16f
    const val MinCreatureDp = 120f
    const val MaxCreatureDp = 176f
    /** Höhe = Breite × Aspekt: ein Fresser ist etwas höher als breit. */
    const val CreatureAspect = 1.1f
    /** Anteil der Figurbreite, den der Bauchfleck einnimmt. */
    const val BellyWidthFraction = 0.78f
    /** Vorschub eines Buchstabens als Vielfaches der Schriftgröße (Näherung wie
     * `SentencePictureCardSizing.EmojiAdvanceEm`). */
    const val GlyphAdvanceEm = 0.62f
    const val MaxBellyGlyphSp = 36f
    const val MinBellyGlyphSp = 16f
    /** 1,5 × `AbcDimens.kidTouch` (80dp) — eine Karte, die ein Kind sicher greift. */
    const val MinCardDp = 120f
    const val CardEmojiFactor = 1.5f
    const val PileCardWidthDp = 22f
    const val PileCardHeightDp = 30f
    const val PileStepDp = 4f

    fun creatureWidthDp(stageWidthDp: Float): Float =
        ((stageWidthDp.coerceAtMost(StageContentDp) - CreatureGapDp) / 2f)
            .coerceIn(MinCreatureDp, MaxCreatureDp)

    fun creatureHeightDp(widthDp: Float): Float = widthDp * CreatureAspect

    /** "S / s" → 5, "Sch / sch" → 9, "ck" → 2. */
    fun labelChars(primary: String, alternate: String?): Int =
        primary.length + (alternate?.let { it.length + 3 } ?: 0)

    /** So groß wie möglich, aber der Bauch bleibt die Grenze — in dp, also mit fontScale. */
    fun bellyGlyphSp(labelChars: Int, creatureWidthDp: Float, fontScale: Float): Float {
        val budget = creatureWidthDp * BellyWidthFraction
        val fits = budget / (labelChars.coerceAtLeast(1) * GlyphAdvanceEm * fontScale)
        return fits.coerceIn(MinBellyGlyphSp, MaxBellyGlyphSp)
    }

    /** Karte um das gedeckelte Aufgabenbild herum (`TaskPromptSizing.pictureSp`). */
    fun cardSizeDp(fontScale: Float): Float =
        (TaskPromptSizing.pictureSp(fontScale) * fontScale * CardEmojiFactor).coerceAtLeast(MinCardDp)

    fun pileWidthDp(count: Int): Float =
        if (count <= 0) 0f else PileCardWidthDp + (count - 1) * PileStepDp
}
```

- [ ] **Step 4: Test laufen lassen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests 'app.abcvorschule.ui.exercise.SoundFeederSizingTest'`
Expected: PASS. Schlägt `theCardIsAtLeast…` bei 2.0 fehl, `TaskPromptSizing.pictureSp` nachlesen — der Deckel liegt dort; `cardSizeDp` darf das Ergebnis nur mit `fontScale` multiplizieren, wenn `pictureSp` den *effektiven* Wert bereits deckelt. Passt es nicht, `fontScale` aus `cardSizeDp` herausnehmen und den Test entsprechend fassen.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SoundFeederSizing.kt app/src/test/java/app/abcvorschule/ui/exercise/SoundFeederSizingTest.kt
git commit -m "feat(exercise): SoundFeederSizing — Figur, Karte, Bauch-Glyph, Futterhaufen

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 10: `FeederCreature` — die Figur

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/FeederCreature.kt`

**Interfaces:**
- Consumes: `SoundFeederSizing`, `SymbolInWordDerivation.TargetLabel`, `IconSpeaker`, Farben `Cream`, `WarmInk`.
- Produces:
  - `class FeederCreatureAnimator { val mouth: Animatable<Float, …>; val wobble: Animatable<Float, …>; val scale: Animatable<Float, …>; suspend fun wiggle(); suspend fun chew(); suspend fun spit(); suspend fun fill(); suspend fun hover(on: Boolean) }` mit `companion object { const val IdleMouth = 0.35f; const val HoverMouth = 0.8f; const val FullScale = 1.15f }`
  - `@Composable fun rememberFeederCreatureAnimator(key: Any?): FeederCreatureAnimator`
  - `@Composable fun FeederCreature(label: SymbolInWordDerivation.TargetLabel, color: Color, animator: FeederCreatureAnimator, hint: Boolean, speaking: Boolean, widthDp: Float, enabled: Boolean, onTap: () -> Unit, testTag: String, modifier: Modifier = Modifier)`

Kein Unit-Test (reine Darstellung); der Shot-Test in Task 13 zeigt sie.

- [ ] **Step 1: Datei schreiben**

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.ui.components.IconSpeaker
import app.abcvorschule.ui.theme.Cream
import app.abcvorschule.ui.theme.WarmInk
import kotlinx.coroutines.delay

/**
 * Bewegungszustand eines Fressers. Drei Animatables statt animateFloatAsState:
 * Kauen und Schütteln sind Sequenzen, die bei jedem Auslöser von vorn laufen
 * müssen, auch wenn die vorige noch läuft.
 */
class FeederCreatureAnimator {
    val mouth = Animatable(IdleMouth)
    val wobble = Animatable(0f)
    val scale = Animatable(1f)

    /** Vorstellen und Antippen: dreimal hin und her, ±6°. */
    suspend fun wiggle() {
        repeat(3) {
            wobble.animateTo(6f, tween(75))
            wobble.animateTo(-6f, tween(75))
        }
        wobble.animateTo(0f, tween(60))
    }

    /** Fressen: Maul weit auf, zweimal zu und auf, dann Ruhe. Der Bauch wackelt mit. */
    suspend fun chew() {
        mouth.animateTo(1f, tween(120))
        repeat(2) {
            mouth.animateTo(0.1f, tween(110))
            scale.animateTo(1.04f, tween(110))
            mouth.animateTo(0.6f, tween(110))
            scale.animateTo(1f, tween(110))
        }
        mouth.animateTo(IdleMouth, tween(150))
    }

    /** Spucken: Maul zu, schütteln, Maul wieder auf. */
    suspend fun spit() {
        mouth.animateTo(0f, tween(90))
        repeat(2) {
            wobble.animateTo(-8f, tween(60))
            wobble.animateTo(8f, tween(60))
        }
        wobble.animateTo(0f, tween(60))
        delay(120)
        mouth.animateTo(IdleMouth, tween(200))
    }

    /** Satt: kugelrund, leicht federnd. */
    suspend fun fill() {
        scale.animateTo(FullScale, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        mouth.animateTo(0.15f, tween(200))
    }

    suspend fun hover(on: Boolean) {
        mouth.animateTo(if (on) HoverMouth else IdleMouth, tween(120))
    }

    companion object {
        const val IdleMouth = 0.35f
        const val HoverMouth = 0.8f
        const val FullScale = 1.15f
    }
}

@Composable
fun rememberFeederCreatureAnimator(key: Any?): FeederCreatureAnimator = remember(key) { FeederCreatureAnimator() }

/**
 * Ein Laut-Fresser (design doc §5): runder Körper, offenes Maul oben, zwei Augen,
 * Bauchfleck mit dem Laut in beiden Formen, Speaker-Icon in der Ecke. Canvas statt
 * Emoji oder Bitmap — Buttons und Figuren zeichnet die App selbst (§10), und nur
 * so folgt das Maul dem Drag.
 *
 * Tipp auf die ganze Figur spricht den Laut; das kleine Speaker-Icon ist nur der
 * Hinweis darauf, kein eigener Knopf mit eigener Trefferfläche.
 */
@Composable
fun FeederCreature(
    label: SymbolInWordDerivation.TargetLabel,
    color: Color,
    animator: FeederCreatureAnimator,
    hint: Boolean,
    speaking: Boolean,
    widthDp: Float,
    enabled: Boolean,
    onTap: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val heightDp = SoundFeederSizing.creatureHeightDp(widthDp)
    val fontScale = LocalDensity.current.fontScale
    val glyphSp = SoundFeederSizing.bellyGlyphSp(
        SoundFeederSizing.labelChars(label.primary, label.alternate),
        widthDp,
        fontScale,
    )
    // Der Hinweis pulsiert das Maul zwischen weit und ganz weit — nur solange er
    // aktiv ist, sonst tickt hier keine Endlos-Animation.
    val hintMouth = if (hint) {
        val transition = rememberInfiniteTransition(label = "feeder_hint")
        val pulse by transition.animateFloat(
            initialValue = 0.6f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(420, easing = LinearEasing), RepeatMode.Reverse),
            label = "feeder_hint_mouth",
        )
        pulse
    } else {
        null
    }
    val mouthOpen = hintMouth ?: animator.mouth.value

    Box(
        modifier = modifier
            .width(widthDp.dp)
            .height(heightDp.dp)
            .graphicsLayer {
                rotationZ = animator.wobble.value
                scaleX = animator.scale.value
                scaleY = animator.scale.value
            }
            .clickable(enabled = enabled) { onTap() }
            .testTag(testTag),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            // Körper: Ellipse, unten etwas breiter — sitzt, statt zu schweben.
            drawOval(color = color, topLeft = Offset(0f, h * 0.12f), size = Size(w, h * 0.88f))
            // Maul: dunkle Ellipse am Kopf, Höhe nach Öffnungsgrad.
            val mouthW = w * 0.62f
            val mouthH = h * (0.06f + 0.26f * mouthOpen)
            drawOval(
                color = WarmInk,
                topLeft = Offset((w - mouthW) / 2f, h * 0.30f - mouthH / 2f),
                size = Size(mouthW, mouthH),
            )
            // Augen: zwei weiße Kreise mit Pupille, über dem Maul.
            listOf(0.34f, 0.66f).forEach { cx ->
                drawCircle(color = Cream, radius = w * 0.085f, center = Offset(w * cx, h * 0.17f))
                drawCircle(color = WarmInk, radius = w * 0.04f, center = Offset(w * cx, h * 0.18f))
            }
            // Bauchfleck: helle Ellipse, darauf steht der Laut (Text unten).
            val bellyW = w * SoundFeederSizing.BellyWidthFraction
            drawOval(
                color = Cream,
                topLeft = Offset((w - bellyW) / 2f, h * 0.50f),
                size = Size(bellyW, h * 0.40f),
            )
        }
        // Der Laut in beiden Formen, wie beim Detektiv: "S / s".
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (heightDp * 0.14f).dp)
                .height((heightDp * 0.32f).dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = buildString {
                    append(label.primary)
                    label.alternate?.let { append(" / ").append(it) }
                },
                fontSize = glyphSp.sp,
                fontWeight = FontWeight.SemiBold,
                color = WarmInk,
                maxLines = 1,
                softWrap = false,
            )
        }
        IconSpeaker(
            tint = if (enabled) Cream else Cream.copy(alpha = 0.5f),
            speaking = speaking,
            size = 22.dp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-6).dp, y = (heightDp * 0.30f).dp),
        )
    }
}
```

Der Trenner „/" steht hier bewusst in `WarmInk` mit im Text und nicht gedämpft wie beim Detektiv: auf dem kleinen hellen Bauch wäre ein halbtransparenter Schrägstrich nicht mehr zu sehen. `WarmMuted` wird in dieser Datei deshalb **nicht** importiert.

- [ ] **Step 2: Bauen**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug`
Expected: grün.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/FeederCreature.kt
git commit -m "feat(exercise): FeederCreature — die Monster-Figur als Canvas mit Animator

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 11: `SoundFeederTrainer` — der Screen

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/SoundFeederTrainer.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt` (Platzhalter-Zweig aus Task 1 ersetzen)

**Interfaces:**
- Consumes: `SoundFeederProgress`, `SoundFeederSizing`, `SoundFeederSpeech`, `FeederCreature`, `rememberFeederCreatureAnimator`, `DragFieldState`/`DragCard`/`DropZone`, `ExerciseStage`, `TaskPromptChrome`, `HuntCelebration.HoldMs`, `SymbolInWordDerivation.targetLabel(atom, SymbolInWordMode.letter)`, `SpeechClipText.forAtom`.
- Produces: `@Composable fun SoundFeederTrainer(round: SoundFeederRound, roundIndex: Int, pack: ContentPack, ttsAvailable: Boolean, speaking: Boolean, interactionLocked: Boolean = false, onSpeakParts: suspend (List<SpokenPart>) -> Unit, onSpeakFeedback: (String) -> Unit, onSpeakFeedbackVoiced: (SpokenPart) -> Unit, onResult: (Boolean, Boolean, List<String>) -> Unit, modifier: Modifier = Modifier)`. Kein `onSpeakPrompt`: die Bühne hat für diese Runde keine Prompt-Teile (Task 1), der Speaker-Tipp spielt die trainereigene Vorstellung. Test-Tags: `feeder_card`, `feeder_pile`, `feeder_left`, `feeder_right`, `feeder_stage`.

- [ ] **Step 1: Screen schreiben**

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.FeederSide
import app.abcvorschule.content.SoundFeederRound
import app.abcvorschule.content.SoundFeederSpeech
import app.abcvorschule.content.SymbolInWordDerivation
import app.abcvorschule.content.SymbolInWordMode
import app.abcvorschule.speech.SpokenPart
import app.abcvorschule.ui.exercise.drag.DragCard
import app.abcvorschule.ui.exercise.drag.DropZone
import app.abcvorschule.ui.exercise.drag.rememberDragFieldState
import app.abcvorschule.ui.rewards.LocalAbcHaptics
import app.abcvorschule.ui.theme.SkyBlue
import app.abcvorschule.ui.theme.SunCoral
import app.abcvorschule.ui.theme.WarmInk
import app.abcvorschule.ui.theme.WarmMuted
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Feste Farben je Seite — in jeder Lektion dieselben zwei Figuren (design doc §5).
 * Weder LeafGreen (richtig) noch StarGold (Belohnung): keine Seite darf „richtig" aussehen. */
private val LeftCreatureColor = SkyBlue
private val RightCreatureColor = SunCoral

private const val ZoneLeft = "feeder_left"
private const val ZoneRight = "feeder_right"
private const val CardKey = "feeder_card"

private enum class FeederPhase { Intro, Playing, Busy, Done }

/**
 * Laut-Fresser (design doc §5/§6): eine Bildkarte oben, zwei Fresser unten. Alle
 * Entscheidungen fallen in [SoundFeederProgress]; dieser Screen zeichnet, bewegt
 * und spricht — und zwar selbst, samt Ansage, damit Wackeln und Laut zusammenfallen.
 */
@Composable
fun SoundFeederTrainer(
    round: SoundFeederRound,
    roundIndex: Int,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    interactionLocked: Boolean = false,
    onSpeakParts: suspend (List<SpokenPart>) -> Unit,
    onSpeakFeedback: (String) -> Unit,
    onSpeakFeedbackVoiced: (SpokenPart) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.leftAtomId}-${round.rightAtomId}"
    var state by remember(roundKey) { mutableStateOf(SoundFeederProgress.initialState(round)) }
    var phase by remember(roundKey) { mutableStateOf(FeederPhase.Intro) }
    val scope = rememberCoroutineScope()
    val haptics = LocalAbcHaptics.current
    val scoredIds = remember(roundKey) { listOf(round.leftAtomId, round.rightAtomId) }
    val leftAnimator = rememberFeederCreatureAnimator(roundKey + "L")
    val rightAnimator = rememberFeederCreatureAnimator(roundKey + "R")
    val dragState = rememberDragFieldState(roundKey, state.nextIndex)
    val leftLabel = remember(roundKey) {
        SymbolInWordDerivation.targetLabel(pack.atom(round.leftAtomId), SymbolInWordMode.letter)
    }
    val rightLabel = remember(roundKey) {
        SymbolInWordDerivation.targetLabel(pack.atom(round.rightAtomId), SymbolInWordMode.letter)
    }
    val density = LocalDensity.current
    val fontScale = density.fontScale
    val cardSize = SoundFeederSizing.cardSizeDp(fontScale)
    val cardSizePx = with(density) { cardSize.dp.toPx() }
    // Pro Karte ein frisches Animatable bei 0: die nächste Karte ist unsichtbar, bis
    // presentCard() sie aufploppt — die gefressene verschwindet damit im selben
    // Frame, in dem der Zustand weiterrückt, ohne eigene Ausblend-Animation.
    val cardPop = remember(roundKey, state.nextIndex) { Animatable(0f) }
    val cardBounce = remember(roundKey) { Animatable(0f) }
    val enabled = phase == FeederPhase.Playing && !interactionLocked
    val interactionOpacity by animateFloatAsState(
        targetValue = if (interactionLocked) 0.5f else 1f,
        animationSpec = tween(200),
        label = "feeder_lock_opacity",
    )

    fun animatorFor(side: FeederSide) = if (side == FeederSide.left) leftAnimator else rightAnimator

    // Die ganze Vorstellung: Ansage, dann wackelt links und spricht, dann rechts.
    // Der Trainer spricht selbst (design doc §5), weil nur er weiß, welche Figur
    // gerade dran ist. Auch der Speaker-Tipp spielt genau diese Sequenz.
    suspend fun introduce() {
        val parts = SoundFeederSpeech.introParts(round, pack)
        if (ttsAvailable) {
            scope.launch { delay(900); leftAnimator.wiggle() }
            scope.launch { delay(1800); rightAnimator.wiggle() }
            onSpeakParts(parts)
        } else {
            leftAnimator.wiggle()
            rightAnimator.wiggle()
        }
    }

    suspend fun presentCard() {
        cardPop.snapTo(0f)
        cardPop.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessMediumLow))
        val card = state.current ?: return
        if (ttsAvailable) onSpeakParts(listOf(SoundFeederSpeech.wordPart(card, pack)))
    }

    LaunchedEffect(roundKey) {
        phase = FeederPhase.Intro
        introduce()
        presentCard()
        phase = FeederPhase.Playing
    }

    // Der Hinweis nach dem zweiten Fehlgriff: der richtige Fresser summt seinen Laut.
    LaunchedEffect(roundKey, state.hintActive, state.nextIndex) {
        val card = state.current ?: return@LaunchedEffect
        if (state.hintActive && ttsAvailable) onSpeakFeedbackVoiced(SoundFeederSpeech.soundPart(round, card.side, pack))
    }

    fun handleDrop(zoneKey: String?) {
        if (!enabled) return
        val side = when (zoneKey) {
            ZoneLeft -> FeederSide.left
            ZoneRight -> FeederSide.right
            else -> null
        }
        val card = state.current ?: return
        val result = SoundFeederProgress.drop(state, side)
        if (result.outcome == SoundFeederDropOutcome.Ignored) return
        phase = FeederPhase.Busy
        state = result.state
        scope.launch {
            when (result.outcome) {
                SoundFeederDropOutcome.Eaten, SoundFeederDropOutcome.RoundComplete -> {
                    haptics.tick()
                    // Die Karte ist mit dem Zustandswechsel schon weg (siehe cardPop);
                    // der Fresser kaut und spricht Laut + Wort.
                    val chewing = launch { animatorFor(card.side).chew() }
                    if (ttsAvailable) onSpeakParts(SoundFeederSpeech.eatParts(round, card, pack))
                    chewing.join()
                    if (result.outcome == SoundFeederDropOutcome.RoundComplete) {
                        phase = FeederPhase.Done
                        haptics.celebrate()
                        val filling = launch { leftAnimator.fill(); rightAnimator.fill() }
                        if (ttsAvailable) onSpeakParts(SoundFeederSpeech.finishParts(round, pack))
                        filling.join()
                        delay(HuntCelebration.HoldMs)
                        onResult(true, false, scoredIds)
                    } else {
                        presentCard()
                        phase = FeederPhase.Playing
                    }
                }
                SoundFeederDropOutcome.Miss, SoundFeederDropOutcome.MissAlreadyReported -> {
                    haptics.nudge()
                    if (result.outcome == SoundFeederDropOutcome.Miss) onResult(false, false, scoredIds)
                    val wrong = side ?: FeederSide.left
                    val spitting = launch { animatorFor(wrong).spit() }
                    launch {
                        cardBounce.snapTo(1f)
                        cardBounce.animateTo(0f, spring(dampingRatio = 0.35f, stiffness = Spring.StiffnessMedium))
                    }
                    if (ttsAvailable) onSpeakParts(SoundFeederSpeech.missParts(round, card, wrong, pack))
                    spitting.join()
                    phase = FeederPhase.Playing
                }
                SoundFeederDropOutcome.Ignored -> phase = FeederPhase.Playing
            }
        }
    }

    // Maul folgt dem Finger: die Karte liegt über einer Zone → dieser Fresser öffnet.
    val hoverSide = dragState.draggingKey?.let { key ->
        // DragFieldState kennt die Zonen nur intern; die Nähe wird über den Drag-Offset
        // gegen die Kartenposition geschätzt: links der Mitte → links, sonst rechts,
        // sobald die Karte nach unten in den Antwortblock gezogen wurde.
        if (key != CardKey) null else dragState.dragOffset.takeIf { it.y > cardSizePx }?.let { o ->
            if (o.x < 0f) FeederSide.left else FeederSide.right
        }
    }
    LaunchedEffect(hoverSide) {
        leftAnimator.hover(hoverSide == FeederSide.left)
        rightAnimator.hover(hoverSide == FeederSide.right)
    }

    ExerciseStage(
        modifier = modifier.testTag("feeder_stage"),
        promptChrome = {
            TaskPromptChrome(
                title = null,
                ttsAvailable = ttsAvailable,
                speaking = speaking,
                onSpeakPrompt = {
                    if (phase == FeederPhase.Playing) scope.launch { phase = FeederPhase.Intro; introduce(); phase = FeederPhase.Playing }
                },
            )
        },
        prompt = {
            // Hält seine größte Höhe: nach der letzten Karte bleibt die Fläche stehen,
            // die Fresser rücken nicht nach oben (§9).
            Box(
                modifier = Modifier.fillMaxWidth().height((cardSize + 24f).dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val card = state.current
                    if (card != null && phase != FeederPhase.Done) {
                        DragCard(
                            state = dragState,
                            key = CardKey,
                            enabled = enabled,
                            onTap = { onSpeakFeedback(pack.atom(card.atomId).lemma) },
                            onDropped = ::handleDrop,
                            modifier = Modifier
                                .graphicsLayer {
                                    val pop = cardPop.value
                                    scaleX = pop
                                    scaleY = pop
                                    translationX = cardBounce.value * 14f
                                }
                                .alpha(interactionOpacity),
                        ) {
                            FeederCard(
                                emoji = pack.atom(card.atomId).emoji,
                                wordText = if (ttsAvailable) null else pack.atom(card.atomId).display,
                                sizeDp = cardSize,
                                fontScale = fontScale,
                            )
                        }
                    } else {
                        Spacer(Modifier.size(cardSize.dp))
                    }
                    Spacer(Modifier.width(18.dp))
                    FoodPile(remaining = (state.remaining - 1).coerceAtLeast(0))
                }
            }
        },
        answers = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val creatureWidth = SoundFeederSizing.creatureWidthDp(maxWidth.value)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(SoundFeederSizing.CreatureGapDp.dp, Alignment.CenterHorizontally),
                ) {
                    listOf(
                        Triple(FeederSide.left, ZoneLeft, leftLabel),
                        Triple(FeederSide.right, ZoneRight, rightLabel),
                    ).forEach { (side, zone, label) ->
                        DropZone(state = dragState, key = zone, enabled = enabled, onTap = {}) {
                            FeederCreature(
                                label = label,
                                color = if (side == FeederSide.left) LeftCreatureColor else RightCreatureColor,
                                animator = animatorFor(side),
                                hint = state.hintActive && state.current?.side == side,
                                speaking = speaking,
                                widthDp = creatureWidth,
                                enabled = phase != FeederPhase.Intro,
                                onTap = {
                                    scope.launch { animatorFor(side).wiggle() }
                                    onSpeakFeedbackVoiced(SoundFeederSpeech.soundPart(round, side, pack))
                                },
                                testTag = zone,
                            )
                        }
                    }
                }
            }
        },
    )
}

/** Die Bildkarte: Emoji im Rahmen der Satz-Versteher-Karten; ohne TTS steht das Wort darunter. */
@Composable
private fun FeederCard(emoji: String, wordText: String?, sizeDp: Float, fontScale: Float) {
    Column(
        modifier = Modifier
            .size(sizeDp.dp)
            .border(3.dp, WarmMuted.copy(alpha = 0.9f), RoundedCornerShape(22.dp))
            .testTag("feeder_card"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = emoji, fontSize = TaskPromptSizing.pictureSp(fontScale).sp, textAlign = TextAlign.Center)
        if (wordText != null) {
            Text(text = wordText, style = MaterialTheme.typography.titleLarge, color = WarmInk)
        }
    }
}

/** Der Futterhaufen: ein verdeckter Kartenrahmen je ausstehender Karte, versetzt gestapelt. */
@Composable
private fun FoodPile(remaining: Int) {
    Box(
        modifier = Modifier
            .width(SoundFeederSizing.pileWidthDp(remaining).coerceAtLeast(1f).dp)
            .height((SoundFeederSizing.PileCardHeightDp + remaining * SoundFeederSizing.PileStepDp).dp)
            .testTag("feeder_pile"),
    ) {
        repeat(remaining) { index ->
            Box(
                modifier = Modifier
                    .offset(x = (index * SoundFeederSizing.PileStepDp).dp, y = ((remaining - 1 - index) * SoundFeederSizing.PileStepDp).dp)
                    .size(SoundFeederSizing.PileCardWidthDp.dp, SoundFeederSizing.PileCardHeightDp.dp)
                    .border(2.dp, WarmMuted.copy(alpha = 0.7f), RoundedCornerShape(6.dp)),
            )
        }
    }
}
```

- [ ] **Step 2: `TrainerHost` verdrahten**

Den Platzhalter `is SoundFeederRound -> Unit` ersetzen:

```kotlin
        is SoundFeederRound -> SoundFeederTrainer(
            round = round,
            roundIndex = roundIndex,
            pack = pack,
            ttsAvailable = ttsAvailable,
            speaking = speaking,
            interactionLocked = interactionLocked,
            onSpeakParts = callbacks.onSpeakParts,
            onSpeakFeedback = callbacks.onSpeakFeedback,
            onSpeakFeedbackVoiced = callbacks.onSpeakFeedbackVoiced,
            onResult = callbacks.onResult,
            modifier = modifier.fillMaxSize(),
        )
```

- [ ] **Step 3: Bauen und Unit-Tests**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: grün.

- [ ] **Step 4: Sichtprüfung am Emulator (Lock beachten)**

```bash
LOCK=/tmp/abc-emulator.lock; mkdir "$LOCK" 2>/dev/null && basename "$PWD" > "$LOCK/owner" && echo frei || echo "belegt von $(cat $LOCK/owner)"
```

Nur bei „frei": `ANDROID_SERIAL=emulator-5554 ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:installDebug`, App öffnen, Lektion 13 (Sch) starten, nach der Buchstaben-Jagd den Fresser spielen. Prüfen: Ansage → beide wackeln und sprechen → Karte ploppt und spricht; richtiger Drop kaut und spricht „Sch … Schuh"; falscher spuckt „Bäh!" und wiederholt; zweiter Fehlgriff pulsiert das richtige Maul; letzte Karte → beide satt, Rülpser, Stern. Screenshot: `adb -s emulator-5554 exec-out screencap -p > /tmp/feeder.png` und ansehen. Danach `rm -rf /tmp/abc-emulator.lock`.

Weicht etwas ab, hier korrigieren — typische Stellen: die Hover-Schätzung (Vorzeichen von `dragOffset.x`, Schwelle `cardSizePx`), das Timing der Wackler in `introduce()` (an die reale Clip-Länge der Ansage anpassen), und ob das Wort nach dem Aufploppen wirklich erst spricht, wenn die Karte steht.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SoundFeederTrainer.kt app/src/main/java/app/abcvorschule/ui/exercise/TrainerHost.kt
git commit -m "feat(exercise): SoundFeederTrainer — Karte füttern, kauen, spucken, satt

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 12: TTS-Pipeline — Strings und Profil `monster`

**Files:**
- Modify: `tools/tts/extra-strings.json`
- Modify: `tools/tts/profiles.json`
- Modify: `tools/tts/ttskit/extract.py` (`FIELD_TO_PROFILE`)
- Modify: `tools/tts/ttskit/export.py` (`PROFILE_PRIORITY`)
- Modify: `tools/tts/README.md` (Abschnitt „Profil-Zuordnung")
- Test: `tools/tts/tests/test_extract.py`

**Interfaces:**
- Produces: Feld `monsterTts` → Profil `monster`; Strings `feederPrompt` („Füttere die Laut-Fresser.", `promptTts`), `feederYuck` („Bäh!", `monsterTts`), `feederContent` („Mmmmh!", `monsterTts`).

- [ ] **Step 1: Failing Test schreiben** (an `test_extract.py` anhängen)

```python
def test_extra_strings_include_feeder_strings(content_dir):
    from ttskit.paths import Paths
    import json

    extra = json.loads(Paths().extra_strings.read_text())
    by_id = {i.id: i for i in extract_items(content_dir, extra_strings=extra)}
    assert by_id["ui:feederPrompt"].text == "Füttere die Laut-Fresser."
    assert profile_for_item(by_id["ui:feederPrompt"]) == "prompt"
    assert by_id["ui:feederYuck"].text == "Bäh!"
    assert by_id["ui:feederContent"].text == "Mmmmh!"
    for key in ("ui:feederYuck", "ui:feederContent"):
        assert by_id[key].field == "monsterTts"
        assert profile_for_item(by_id[key]) == "monster"


def test_monster_profile_exists_and_is_exportable():
    import json
    from ttskit.paths import Paths
    from ttskit.export import PROFILE_PRIORITY

    profiles = json.loads(Paths().profiles.read_text())["profiles"]
    assert "monster" in profiles
    assert profiles["monster"]["language"] == "german"
    assert "monster" in PROFILE_PRIORITY
```

Falls `Paths()` kein `profiles`-Attribut hat, den Pfad wie `extra_strings` in `ttskit/paths.py` ergänzen (`self.root / "profiles.json"`), sofern es ihn nicht unter anderem Namen gibt (`grep -n "profiles" tools/tts/ttskit/paths.py`).

- [ ] **Step 2: Test laufen lassen, Fehlschlag sehen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_extract.py -q -k "feeder or monster"`
Expected: FAIL (KeyError `ui:feederPrompt`).

- [ ] **Step 3: Strings, Feld, Profil, Priorität**

`extra-strings.json`, drei Einträge anhängen:

```json
    {
      "id": "feederPrompt",
      "text": "Füttere die Laut-Fresser.",
      "field": "promptTts",
      "note": "SoundFeederDerivation.Prompt — Ansage des Laut-Fressers"
    },
    {
      "id": "feederYuck",
      "text": "Bäh!",
      "field": "monsterTts",
      "note": "SoundFeederSpeech.Yuck — der falsche Fresser spuckt aus"
    },
    {
      "id": "feederContent",
      "text": "Mmmmh!",
      "field": "monsterTts",
      "note": "SoundFeederSpeech.Content — beide sind satt"
    }
```

`extract.py`, in `FIELD_TO_PROFILE`: `"monsterTts": "monster",` mit Kommentar: „Reaktionen der Laut-Fresser — die einzigen Strings, die nicht sauber artikuliert sein müssen; Laute und Wörter laufen in normaler Stimme und werden erst in der App per Tonhöhe zum Monster."

`export.py`: `PROFILE_PRIORITY` um `"monster"` (am Ende, hinter `"ui"`) ergänzen.

`profiles.json`, neues Profil `monster` (Sampling wie `reward` kopieren, eigener leerer `seedPool`):

```json
    "monster": {
      "label": "Monster-Reaktion",
      "speaker": "sohee",
      "language": "german",
      "instruct": "Sprich wie ein kleines, gutmütiges Monster, das gerade etwas gefressen hat: knurrig, tief, kurz, mit Freude. Kein echtes Wort, nur der Ausruf. Übertreibe die Stimme, bleibe aber deutlich. Innerhalb von 1 Sekunde. Niemals englische Aussprache.",
      "sampling": { … wie reward … },
      "seedPool": [],
      "trim": true,
      "normalize": true
    }
```

Ob Qwen mit sohee ein glaubwürdiges Monster liefert, entscheidet die Kuratierung im TTS-Interface — ein anderer Sprecher ist dort ausdrücklich erlaubt (`speaker` im Lock). Das ist eine offene Aufgabe für den Nutzer, kein Blocker dieses Plans; die App spielt auch ohne die Clips (Android-TTS mit Monster-Tonhöhe).

- [ ] **Step 4: Alle Pipeline-Tests laufen lassen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest -q`
Expected: grün. Meckert ein Test über einen unbekannten Profilnamen oder eine fehlende Seed-Pool-Länge, dessen Erwartung lesen und das Profil entsprechend vervollständigen (nicht den Test lockern).

- [ ] **Step 5: README-Absatz**

In `tools/tts/README.md` unter „Profil-Zuordnung" anhängen:

> Das Profil `monster` trägt nur die zwei Reaktionen des Laut-Fressers („Bäh!", „Mmmmh!", Feld `monsterTts` in `extra-strings.json`). Laute und Wörter des Trainers sind die normalen `phoneme`-/`word`-Clips — die Monster-Stimme entsteht in der App per Tonhöhe (`VoiceStyle`), damit die Artikulation sauber bleibt. Ein anderer Sprecher als sohee ist für `monster` erlaubt.

- [ ] **Step 6: Commit**

```bash
git add tools/tts/extra-strings.json tools/tts/profiles.json tools/tts/ttskit/extract.py tools/tts/ttskit/export.py tools/tts/README.md tools/tts/tests/test_extract.py
git commit -m "feat(tts): Laut-Fresser-Strings und Profil monster für die Reaktionen

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 13: Shot-Test bei 1.0 und 1.3

**Files:**
- Create: `app/src/androidTest/java/app/abcvorschule/ui/exercise/SoundFeederShotTest.kt`
- Modify: `README.md` (Tabelle der Shot-Tests, Zeile `SoundFeederShotTest | filesDir/feedershots | run-as (A)`)

- [ ] **Step 1: Test schreiben** (Muster `WordBuildMorphShotTest`)

```kotlin
package app.abcvorschule.ui.exercise

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.abcvorschule.content.Atom
import app.abcvorschule.content.AtomKind
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.FeederSide
import app.abcvorschule.content.Gender
import app.abcvorschule.content.NounClass
import app.abcvorschule.content.PackManifest
import app.abcvorschule.content.SoundFeederCard
import app.abcvorschule.content.SoundFeederRound
import app.abcvorschule.ui.theme.AbcTheme
import app.abcvorschule.ui.theme.Cream
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Standbilder des Laut-Fressers in drei Breiten und zwei Systemschriftgrößen. Kein
 * Assertion-Test: er zeigt, ob der Bauch-Glyph „Sch / sch" bei font_scale 1.3 in
 * der Figur bleibt und ob Karte, Haufen und Fresser zusammen auf 320dp passen.
 */
@RunWith(AndroidJUnit4::class)
class SoundFeederShotTest {
    @get:Rule
    val rule = createComposeRule()

    private fun noun(id: String, display: String, emoji: String) = Atom(
        id = id, lemma = display, display = display, emoji = emoji, kind = AtomKind.other,
        gender = Gender.f, nounClass = NounClass.thing,
    )

    private val pack = ContentPack(
        manifest = PackManifest(schemaVersion = 1, packId = "test", title = "Test Pack"),
        atoms = listOf(
            Atom(id = "letter-s", lemma = "S", display = "S", emoji = "", kind = AtomKind.letter),
            Atom(id = "letter-sch", lemma = "Sch", display = "Sch", emoji = "", kind = AtomKind.letter),
            noun("sonne", "Sonne", "☀️"), noun("salat", "Salat", "🥗"),
            noun("schuh", "Schuh", "👟"), noun("schaf", "Schaf", "🐑"),
        ).associateBy { it.id },
        sentences = emptyMap(),
        tasks = emptyMap(),
        finales = emptyMap(),
        lessons = emptyList(),
    )

    private val round = SoundFeederRound(
        promptTts = "Füttere die Laut-Fresser.",
        leftAtomId = "letter-s",
        rightAtomId = "letter-sch",
        cards = listOf(
            SoundFeederCard("sonne", FeederSide.left), SoundFeederCard("schuh", FeederSide.right),
            SoundFeederCard("salat", FeederSide.left), SoundFeederCard("schaf", FeederSide.right),
        ),
    )

    @Test
    fun captureStages() {
        val dir = File(InstrumentationRegistry.getInstrumentation().targetContext.filesDir, "feedershots").apply { mkdirs() }
        listOf(320, 360, 411).forEach { width ->
            listOf(1.0f, 1.3f).forEach { scale ->
                rule.setContent {
                    val base = LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(base.density, fontScale = scale)) {
                        AbcTheme {
                            Box(Modifier.size(width.dp, 640.dp).background(Cream).testTag("shot")) {
                                SoundFeederTrainer(
                                    round = round, roundIndex = 0, pack = pack,
                                    ttsAvailable = false, speaking = false,
                                    onSpeakParts = {}, onSpeakFeedback = {},
                                    onSpeakFeedbackVoiced = {}, onResult = { _, _, _ -> },
                                )
                            }
                        }
                    }
                }
                rule.mainClock.advanceTimeBy(3_000)
                val bitmap = rule.onNodeWithTag("shot").captureToImage().asAndroidBitmap()
                File(dir, "feeder-${width}dp-${scale}.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
            }
        }
    }
}
```

Hinweis: `createComposeRule` erlaubt `setContent` nur einmal pro Test — falls der Lauf daran scheitert, jede Kombination in eine eigene `@Test`-Methode auslagern (sechs Methoden, gemeinsame private `capture(width, scale)`).

- [ ] **Step 2: Laufen lassen (Emulator-Lock!)**

```bash
LOCK=/tmp/abc-emulator.lock; mkdir "$LOCK" 2>/dev/null && basename "$PWD" > "$LOCK/owner" && echo frei || echo "belegt von $(cat $LOCK/owner)"
```

Dann Weg A aus der README: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest`, beide APKs mit `adb -s emulator-5554 install -r …` installieren, `adb -s emulator-5554 shell am instrument -w -e class app.abcvorschule.ui.exercise.SoundFeederShotTest app.abcvorschule.test/androidx.test.runner.AndroidJUnitRunner`, PNGs per `run-as` abholen (Befehl in der README, Ordner `files/feedershots`) und **ansehen**: Bauch-Glyph in der Figur, keine abgeschnittene Karte, Haufen sichtbar. Danach `rm -rf /tmp/abc-emulator.lock`.

- [ ] **Step 3: README-Tabelle ergänzen** — Zeile `| SoundFeederShotTest | filesDir/feedershots | run-as (A) |` unter `WordBuildMorphShotTest`.

- [ ] **Step 4: Commit**

```bash
git add app/src/androidTest/java/app/abcvorschule/ui/exercise/SoundFeederShotTest.kt README.md
git commit -m "test(exercise): SoundFeederShotTest — Bühne bei 320/360/411dp und font_scale 1.0/1.3

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 14: Doku nachziehen

**Files:**
- Modify: `docs/PRODUCT_PRINCIPLES.md` (§3 hinter dem Wort-Detektiv-Absatz; §7; §9; §10)
- Modify: `AGENTS.md` (Kurzfassung Trainer-Typen)
- Modify: `docs/superpowers/specs/2026-09-04-laut-fresser-design.md` (Status → `implemented`)

- [ ] **Step 1: §3 — neuer Absatz nach „[Wort-Detektiv-Design](…)"**

> Dritter abgeleiteter Zusatz-Trainer: der **Laut-Fresser** direkt nach der Buchstaben-Jagd (ohne Jagd nach dem letzten Spurensucher), ab Lektion 3 — „Füttere die Laut-Fresser." Zwei Figuren tragen je einen Laut auf dem Bauch (`S / s`, `Sch / sch`), oben erscheinen nacheinander bis zu sieben Bildkarten; das Kind hört das Wort und zieht die Karte zu dem Fresser, dessen Laut es hört. Gefragt wird nur **welcher** Laut, nie **wo** im Wort — das Anfang/Mitte/Ende-Konzept hat den früheren Auditiven Finder gekostet. Welche Lektion welches Paar spielt, entscheidet eine kuratierte Tabelle in drei Stufen (`SoundPairs`): **Kontrast** (S/Sch, S/Z, F/W, W/B, B/P, D/T, G/K, K/T, M/N, L/R, St/Sp, Pf/F — echte Verwechslungen nach Fox-Boyer und H-LAD, nur am Anlaut), **Aufwärmen** (P/T, L/H, F/T, S/T für L03–L07, damit das Kind Monster und Geste an leichten Kontrasten lernt) und **Vokal** (Ei/Au, Ei/Eu, Ö/Ü, I/O — irgendwo im Wort). Ein Paar ist Kandidat, wenn ein Laut Fokus der Lektion ist, beide eingeführt sind und der Pack je Seite mindestens zwei und zusammen mindestens sechs Karten hergibt; nie gespielt schlägt gespielt, sonst das am längsten Zurückliegende. Ohne Kandidaten wiederholt die Lektion ein bekanntes Kontrast-Paar. Karten sind Substantive mit genau einem Emoji, der Partnerlaut kommt nirgends im Wort vor (auch nicht als `ß`), Minimalpaare (Fisch/Tisch, Kanne/Tanne) werden bevorzugt und liegen nebeneinander, und die Karten rotieren über die Lektionen durch den alphabetischen Vorrat. Richtiger Fresser kaut und spricht Laut und Wort, falscher spuckt („Bäh!") und die Karte hüpft zurück, beim zweiten Fehlgriff derselben Karte pulsiert das richtige Maul — kein „Zeig mir". Details und Zuordnung aller Lektionen: [Laut-Fresser-Design](superpowers/specs/2026-09-04-laut-fresser-design.md).

- [ ] **Step 2: §7 — zwei Punkte**

Beim Artikel-Punkt („**Nicht** betroffen: …") ergänzen: „… und die Fress-Sequenz des Laut-Fressers („Sch … Schuh"), weil ein Artikel zwischen Laut und Wort genau die Kopplung zerschnitte, die der Trainer lehrt."

Neuer Punkt:

> - **Monster-Stimme (Laut-Fresser):** Laute und Wörter bleiben die kuratierten Clips, nur die Tonhöhe kippt zur Laufzeit (`VoiceStyle`: links tiefer, rechts höher) — die Artikulation ist der Engpass der App und darf nicht leiden. Echte Monster-Sprache gibt es nur für „Bäh!" und „Mmmmh!" (TTS-Profil `monster`). Der Trainer spricht seine Ansage und Vorstellung selbst (`currentPromptParts` ist für ihn leer), damit Wackeln und Laut zusammenfallen. Ohne deutsches TTS steht das Wort als Text unter dem Emoji — ein Hörspiel ist sonst unspielbar.

- [ ] **Step 3: §9 — Layout-Ausnahme**

> - Ausnahme Laut-Fresser: der Antwortblock trägt **zwei Drop-Zonen** (die Fresser) statt Kacheln, der Aufgabenblock die aktuelle Bildkarte und rechts daneben den Futterhaufen als Rundenfortschritt; er hält seine Höhe, wenn die letzte Karte gefressen ist.

- [ ] **Step 4: §10 — Farben**

Beim Rollen-Absatz ergänzen: „Die beiden Laut-Fresser tragen `SkyBlue` (links) und `SunCoral` (rechts) mit einem `Cream`-Bauchfleck — bewusst weder `LeafGreen` noch `StarGold`, damit keine Seite ‚richtig' aussieht."

- [ ] **Step 5: AGENTS.md** — in der Kurzfassung nach dem Wort-Detektiv-Punkt:

> - **Laut-Fresser**: „Füttere die Laut-Fresser" — dritter abgeleiteter Trainer (`SoundFeederInsertion`), direkt nach der Buchstaben-Jagd, ab L03. Zwei Monster mit je einem Laut, Bildkarten per Drag zum richtigen; Paar-Tabelle in `SoundPairs` (Kontrast/Aufwärmen/Vokal), Zuordnung und Karten in `SoundFeederDerivation` (deterministisch, Snapshot-Test). Fragt „welcher Laut", nie „wo". Monster-Stimme = Tonhöhe (`VoiceStyle`), Reaktionen im TTS-Profil `monster`.

Und im Kopfsatz „abgeleitete Zusatz-Trainer (Jagd, Wort-Detektiv, siehe unten)" → „(Jagd, Wort-Detektiv, Laut-Fresser, siehe unten)".

- [ ] **Step 6: Spec-Status** auf `implemented` setzen, dann Commit

```bash
git add docs/PRODUCT_PRINCIPLES.md AGENTS.md docs/superpowers/specs/2026-09-04-laut-fresser-design.md
git commit -m "docs: Laut-Fresser in Prinzipien (§3, §7, §9, §10) und Agent-Kurzfassung

Co-Authored-By: Claude Fable 5.1 <noreply@anthropic.com>"
```

---

### Task 15: Abschluss — Verifikation und Merge

- [ ] **Step 1: Volle Verifikation**

Run: `ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug` und `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest -q`
Expected: alles grün. Ausgabe der letzten Zeilen in die Zusammenfassung übernehmen — keine Erfolgsmeldung ohne Ausgabe.

- [ ] **Step 2: Skill `superpowers:finishing-a-development-branch`** aufrufen; laut Nutzer-Memory wird fertige Arbeit ohne Rückfrage nach `main` gemergt (Merge-Commit wie die bisherigen `merge: …`-Commits, z. B. `merge: Laut-Fresser — Hör-Trainer für schwierige Lautpaare`).

- [ ] **Step 3: Offene Punkte an den Nutzer melden**

- Die drei neuen Strings und die zehn neuen Wort-Clips brauchen einen Pipeline-Lauf (`tts extract`, Rendern, Kuratieren, `tts export`); bis dahin spricht sie die System-TTS.
- Ob das Profil `monster` mit sohee überzeugt oder ein anderer Sprecher her muss, entscheidet sich beim Kuratieren.
- Die Wackel-Zeiten in `introduce()` (900/1800 ms) sind gegen die Clip-Längen der kuratierten Ansage zu prüfen.
