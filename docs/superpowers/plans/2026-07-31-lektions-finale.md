# Lektions-Finale Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Nach dem echten Abschluss einer Lektion hört das Kind einen kurzen, lustigen Einzeiler aus dem Vokabular dieser Lektion und sieht dessen Nomen als Bildreihe; die Punktezeile verschwindet, die Erfolgsmeldung wird Header, der Stern wird gedämpfter Hintergrund.

**Architecture:** Ein neuer Content-Typ `LessonFinale` in `finales.json`, per `Lesson.finaleId` referenziert (18 Sätze für 26 Lektionen). Der `ContentValidator` erzwingt die Redaktionsregeln beim Laden. `SessionUiState.completedFinaleId` unterscheidet echten Abschluss vom Abbruch. Alle Layout- und Ableitungslogik liegt in reinen Kotlin-Objekten ohne Compose-Abhängigkeit — das Repo hat **keine** androidTests, JVM-Unit-Tests sind die einzige automatisierte Absicherung.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), kotlinx.serialization, JUnit 4, Gradle.

**Spec:** `docs/superpowers/specs/2026-07-31-lektions-finale-design.md`
**Produktprinzipien:** `docs/PRODUCT_PRINCIPLES.md` Abschnitte 4, 5, 12

## Global Constraints

- **Sprache:** App-Inhalte und Doku deutsch. Code-Kommentare deutsch oder englisch, konsistent mit der jeweiligen Datei (bestehende Kommentare sind englisch).
- **Tests:** `./gradlew :app:testDebugUnitTest` — muss nach jeder Task grün sein.
- **Build:** `./gradlew :app:assembleDebug`
- **Content-Pack doppelt pflegen:** `app/src/main/assets/content/` (App) und `app/src/test/resources/content/` (JVM-Tests) sind **manuelle Kopien**, kein Gradle-Sync. Jede Content-Änderung muss in **beide** Verzeichnisse. `ContentRepository.fromClasspath()` liest die Test-Kopie.
- **`schemaVersion` bleibt 2.** `finaleId` ist optional (`String? = null`), alte Packs laden weiter.
- **Keine Emojis in Buttons** (Prinzip 2). Emojis nur als Content-Bilder in der Finale-Bildreihe.
- **Reine Logik gehört in Compose-freie Objekte**, damit sie testbar bleibt — Vorbild: `content/LessonEmojis.kt`, `ui/exercise/WordFrameSizing.kt`.
- **Commits:** Dieses Repo committet **nur auf explizite Nutzerfreigabe** (`AGENTS.md`). Die Commit-Schritte unten sind vorbereitet; wer den Plan ausführt, führt sie nur aus, wenn der Nutzer Commits freigegeben hat. Andernfalls Änderungen lokal stehen lassen.
- **Branch:** `feat/lektions-finale` (existiert bereits).

---

### Task 1: Content-Typ `LessonFinale` und Laden

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/ContentModels.kt` (nach `SentencesFile`, ca. Zeile 68; sowie `ContentPack`)
- Modify: `app/src/main/java/app/abcvorschule/content/LessonModels.kt` (`Lesson`)
- Modify: `app/src/main/java/app/abcvorschule/content/ContentRepository.kt:33-44` (`parsePack`)
- Create: `app/src/main/assets/content/finales.json`
- Create: `app/src/test/resources/content/finales.json`
- Test: `app/src/test/java/app/abcvorschule/content/ContentRepositoryTest.kt`

**Interfaces:**
- Consumes: `ContentPack`, `Atom`, `Lesson` (bestehend)
- Produces:
  - `data class LessonFinale(val id: String, val text: String, val tts: String, val pictureAtomIds: List<String>)`
  - `data class FinalesFile(val finales: List<LessonFinale>)`
  - `ContentPack.finales: Map<String, LessonFinale>` (5. Konstruktorparameter, **vor** `lessons`)
  - `ContentPack.finale(id: String): LessonFinale`
  - `Lesson.finaleId: String?`

In dieser Task enthält `finales.json` nur `f-l01`, und keine Lektion trägt ein `finaleId`. Das Pack bleibt valide, weil `finaleId` optional ist und die Validierung erst in Task 3 dazukommt. Task 2 füllt den Content.

- [ ] **Step 1: `finales.json` in beiden Verzeichnissen anlegen**

Identischer Inhalt in `app/src/main/assets/content/finales.json` **und** `app/src/test/resources/content/finales.json`:

```json
{
  "finales": [
    {
      "id": "f-l01",
      "text": "Mama Maus mampft einen dicken Apfel!",
      "tts": "Mama Maus mampft einen dicken Apfel!",
      "pictureAtomIds": ["mama", "maus", "apfel"]
    }
  ]
}
```

- [ ] **Step 2: Den fehlschlagenden Test schreiben**

An `app/src/test/java/app/abcvorschule/content/ContentRepositoryTest.kt` anhängen (innerhalb der Klasse):

```kotlin
    @Test
    fun finalesAreParsedFromTheirOwnFile() {
        val pack = ContentRepository.fromClasspath().load()
        val finale = pack.finale("f-l01")
        assertEquals("Mama Maus mampft einen dicken Apfel!", finale.text)
        assertEquals(listOf("mama", "maus", "apfel"), finale.pictureAtomIds)
    }

    @Test
    fun finalePicturesResolveToAtomsWithEmojis() {
        val pack = ContentRepository.fromClasspath().load()
        val emojis = pack.finale("f-l01").pictureAtomIds.map { pack.atom(it).emoji }
        assertEquals(listOf("👩", "🐭", "🍎"), emojis)
    }
```

Falls `assertEquals` in der Datei noch nicht importiert ist: `import org.junit.Assert.assertEquals` ergänzen.

- [ ] **Step 3: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests '*ContentRepositoryTest*'
```

Erwartet: Kompilierfehler — `finale` und `LessonFinale` existieren nicht.

- [ ] **Step 4: Modelle ergänzen**

In `ContentModels.kt` direkt nach `data class SentencesFile(...)` einfügen:

```kotlin
/**
 * Der Belohnungssatz einer Lektion: wird beim Abschluss vorgelesen und als Bildreihe
 * visualisiert. Anders als [Sentence] ist er nicht baubar — er enthält bewusst Wörter
 * außerhalb des Atom-Graphen (Verben, Adjektive), weil sie nie gelesen oder gebaut
 * werden müssen. Nur die bildtragenden Nomen sind Atome.
 */
@Serializable
data class LessonFinale(
    val id: String,
    /** Schriftbild für den mitlesenden Erwachsenen. */
    val text: String,
    /** Was TTS spricht; kann von [text] abweichen (Betonung, Satzzeichen). */
    val tts: String,
    /** Nomen-Atome in Satzreihenfolge; jedes muss ein Emoji tragen. */
    val pictureAtomIds: List<String>,
)

@Serializable
data class FinalesFile(val finales: List<LessonFinale>)
```

- [ ] **Step 5: `ContentPack` erweitern**

`ContentPack` bekommt den neuen Parameter **vor** `lessons`, damit der positionsbasierte Aufruf in `parsePack()` eindeutig bleibt:

```kotlin
data class ContentPack(
    val manifest: PackManifest,
    val atoms: Map<String, Atom>,
    val sentences: Map<String, Sentence>,
    val tasks: Map<String, TaskSpec>,
    val finales: Map<String, LessonFinale>,
    val lessons: List<Lesson>,
) {
```

Und neben `fun sentence(...)` die Zugriffsfunktion ergänzen:

```kotlin
    fun finale(id: String): LessonFinale = finales.getValue(id)
```

- [ ] **Step 6: `Lesson.finaleId` ergänzen**

In `LessonModels.kt`, in `data class Lesson`, nach `taskIds`:

```kotlin
    /**
     * Belohnungssatz beim Abschluss (`finales.json`). Ein Verweis statt eines
     * Inline-Objekts, damit Wiederholungslektionen den Satz ihrer Basis-Lektion
     * teilen, statt ihn zu duplizieren.
     */
    val finaleId: String? = null,
```

- [ ] **Step 7: Laden in `parsePack()`**

In `ContentRepository.kt`, nach der `tasks`-Zeile:

```kotlin
        val finales = json.decodeFromString<FinalesFile>(read("content/finales.json")).finales
            .associateBy { it.id }
```

Und den Rückgabeaufruf anpassen:

```kotlin
        return ContentPack(manifest, atoms, sentences, tasks, finales, lessons)
```

- [ ] **Step 8: Tests laufen lassen**

```bash
./gradlew :app:testDebugUnitTest
```

Erwartet: PASS, inklusive der beiden neuen Tests. Sollten andere Suites brechen, weil sie `ContentPack(...)` positionsbasiert konstruieren, den neuen Parameter dort mit `emptyMap()` ergänzen.

- [ ] **Step 9: Commit (nur bei Nutzerfreigabe)**

```bash
git add app/src/main/java/app/abcvorschule/content app/src/main/assets/content/finales.json app/src/test/resources/content/finales.json app/src/test/java/app/abcvorschule/content/ContentRepositoryTest.kt
git commit -m "feat(content): add LessonFinale type and load finales.json"
```

---

### Task 2: Die 18 Finale-Sätze und ihre Zuordnung

**Files:**
- Modify: `app/src/main/assets/content/finales.json` (+ Kopie in `app/src/test/resources/content/`)
- Modify: `app/src/main/assets/content/atoms.json` (+ Kopie) — Atom `kuchen`
- Modify: `app/src/main/assets/content/lessons.json` (+ Kopie) — `finaleId` an allen 26 Lektionen
- Create: `app/src/test/java/app/abcvorschule/content/LessonFinaleTest.kt`

**Interfaces:**
- Consumes: `LessonFinale`, `ContentPack.finale`, `Lesson.finaleId` (Task 1)
- Produces: Ein vollständiges Content-Pack, in dem jede der 26 Lektionen auf ein Finale auflöst. Task 3 validiert diese Zusagen, Task 5 rendert sie.

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

Neue Datei `app/src/test/java/app/abcvorschule/content/LessonFinaleTest.kt`:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonFinaleTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun everyAuthoredLessonResolvesToAFinale() {
        pack.authoredLessons.forEach { lesson ->
            val id = lesson.finaleId
            assertTrue("lesson ${lesson.id} needs a finaleId", !id.isNullOrBlank())
            assertTrue("lesson ${lesson.id} finaleId $id is unknown", id in pack.finales)
        }
    }

    @Test
    fun eighteenFinalesCoverTwentySixLessons() {
        assertEquals(18, pack.finales.size)
        assertEquals(26, pack.authoredLessons.size)
    }

    @Test
    fun repeatLessonsShareTheFinaleOfTheirBaseLesson() {
        // L19-L26 wiederholen frühere Lektionen und erben deren Satz.
        assertEquals("f-l01", pack.lesson("l19").finaleId)
        assertEquals("f-l02", pack.lesson("l20").finaleId)
        assertEquals("f-l03", pack.lesson("l21").finaleId)
        assertEquals("f-l11", pack.lesson("l22").finaleId)
        assertEquals("f-l13", pack.lesson("l23").finaleId)
        assertEquals("f-l17", pack.lesson("l24").finaleId)
        assertEquals("f-l12", pack.lesson("l25").finaleId)
        assertEquals("f-l18", pack.lesson("l26").finaleId)
    }

    @Test
    fun everySentenceHoldsFourToSevenWords() {
        pack.finales.values.forEach { finale ->
            val words = finale.text.trim().split(Regex("\\s+")).size
            assertTrue(
                "finale ${finale.id} has $words words, expected 4..7: ${finale.text}",
                words in 4..7,
            )
        }
    }

    @Test
    fun everyFinaleHoldsTwoToFourDistinctPictures() {
        pack.finales.values.forEach { finale ->
            val emojis = finale.pictureAtomIds.map { pack.atom(it).emoji }
            assertTrue(
                "finale ${finale.id} has ${emojis.size} pictures, expected 2..4",
                emojis.size in 2..4,
            )
            emojis.forEach {
                assertTrue("finale ${finale.id} has a picture atom without emoji", it.isNotBlank())
            }
            assertEquals(
                "finale ${finale.id} shows the same glyph twice",
                emojis.size,
                emojis.distinct().size,
            )
        }
    }

    @Test
    fun everyFinaleIsReferencedBySomeLesson() {
        val referenced = pack.lessons.mapNotNull { it.finaleId }.toSet()
        assertEquals(emptySet<String>(), pack.finales.keys - referenced)
    }

    @Test
    fun kuchenIsPictureOnlyVocabulary() {
        // "Kuchen" trägt nur ein Bild im Finale von L05 — es wird nie gelesen oder gebaut.
        val kuchen = pack.atom("kuchen")
        assertEquals("🍰", kuchen.emoji)
        assertEquals(AtomKind.other, kuchen.kind)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests '*LessonFinaleTest*'
```

Erwartet: FAIL — 17 Finales fehlen, kein `finaleId` gesetzt, `kuchen` existiert nicht.

- [ ] **Step 3: Atom `kuchen` ergänzen**

In `atoms.json` (beide Kopien) einen Eintrag im `atoms`-Array ergänzen, im Stil des bestehenden `ameise`-Eintrags. `kind: "other"` bedeutet Bild-Vokabular: nie gelesen, nie gebaut, nur gezeigt. Keine `strokes`, weil das Atom nie nachgespurt wird.

```json
    {
      "id": "kuchen",
      "lemma": "Kuchen",
      "display": "Kuchen",
      "emoji": "🍰",
      "kind": "other",
      "pluralDisplay": "Kuchen"
    }
```

- [ ] **Step 4: Alle 18 Finales schreiben**

`finales.json` (beide Kopien) vollständig ersetzen:

```json
{
  "finales": [
    { "id": "f-l01", "text": "Mama Maus mampft einen dicken Apfel!", "tts": "Mama Maus mampft einen dicken Apfel!", "pictureAtomIds": ["mama", "maus", "apfel"] },
    { "id": "f-l02", "text": "Oma und Mimi fliegen im Ufo!", "tts": "Oma und Mimi fliegen im Ufo!", "pictureAtomIds": ["oma", "mimi", "ufo"] },
    { "id": "f-l03", "text": "Papa und Opa tanzen auf dem Tisch!", "tts": "Papa und Opa tanzen auf dem Tisch!", "pictureAtomIds": ["papa", "opa"] },
    { "id": "f-l04", "text": "Das Lama hat einen roten Hut!", "tts": "Das Lama hat einen roten Hut!", "pictureAtomIds": ["lama", "hut"] },
    { "id": "f-l05", "text": "Der Fuchs futtert den Kuchen!", "tts": "Der Fuchs futtert den Kuchen!", "pictureAtomIds": ["fuchs", "kuchen"] },
    { "id": "f-l06", "text": "Deine Nase ist rot wie eine Rose.", "tts": "Deine Nase ist rot wie eine Rose.", "pictureAtomIds": ["nase", "rose"] },
    { "id": "f-l07", "text": "Der Fuchs schläft im Nest!", "tts": "Der Fuchs schläft im Nest!", "pictureAtomIds": ["fuchs", "nest"] },
    { "id": "f-l08", "text": "Die Katze klaut den Keks!", "tts": "Die Katze klaut den Keks!", "pictureAtomIds": ["katze", "keks"] },
    { "id": "f-l09", "text": "Der Wal will mein Eis!", "tts": "Der Wal will mein Eis!", "pictureAtomIds": ["wal", "eis"] },
    { "id": "f-l10", "text": "Die Giraffe angelt auf dem Dach!", "tts": "Die Giraffe angelt auf dem Dach!", "pictureAtomIds": ["giraffe", "dach"] },
    { "id": "f-l11", "text": "Der Ball saust aus dem Haus!", "tts": "Der Ball saust aus dem Haus!", "pictureAtomIds": ["ball", "haus"] },
    { "id": "f-l12", "text": "Der Löwe klaut die Rübe!", "tts": "Der Löwe klaut die Rübe!", "pictureAtomIds": ["loewe", "ruebe"] },
    { "id": "f-l13", "text": "Das Schaf steckt im Schuh!", "tts": "Das Schaf steckt im Schuh!", "pictureAtomIds": ["schaf", "schuh"] },
    { "id": "f-l14", "text": "Das Zebra jongliert mit dem Jojo!", "tts": "Das Zebra jongliert mit dem Jojo!", "pictureAtomIds": ["zebra", "jojo"] },
    { "id": "f-l15", "text": "Der Vogel klaut die Vase!", "tts": "Der Vogel klaut die Vase!", "pictureAtomIds": ["vogel", "vase"] },
    { "id": "f-l16", "text": "Das Pferd knackt den Sack!", "tts": "Das Pferd knackt den Sack!", "pictureAtomIds": ["pferd", "sack"] },
    { "id": "f-l17", "text": "Eine Spinne bewundert sich im Spiegel!", "tts": "Eine Spinne bewundert sich im Spiegel!", "pictureAtomIds": ["spinne", "spiegel"] },
    { "id": "f-l18", "text": "Die Qualle quetscht sich ins Taxi!", "tts": "Die Qualle quetscht sich ins Taxi!", "pictureAtomIds": ["qualle", "taxi"] }
  ]
}
```

Hinweise zum Content, damit spätere Änderungen die Regeln nicht brechen:
- **L03** zeigt nur zwei Bilder: für „Tisch" gibt es kein brauchbares Emoji (🪑 ist ein Stuhl), und `tisch` trägt in `atoms.json` bewusst `emoji: ""`.
- **L02** nutzt `mimi`, nicht `katze` — beide sind 🐱, und zwei identische Glyphen lesen sich als Bug.
- **L06** weicht vom Konzeptpapier ab („Die rote Nase rennt weg!" war der dort verworfene AI-Slop-Typ).
- **`dach` und `haus`** sind beide 🏠, treten aber nie im selben Finale auf.

- [ ] **Step 5: `finaleId` an alle 26 Lektionen**

In `lessons.json` (beide Kopien) trägt jede Lektion ein `finaleId`. L01–L18 folgen ihrem Index, L19–L26 erben:

| Lektion | `finaleId` | | Lektion | `finaleId` |
| --- | --- | --- | --- | --- |
| l01 | `f-l01` | | l14 | `f-l14` |
| l02 | `f-l02` | | l15 | `f-l15` |
| l03 | `f-l03` | | l16 | `f-l16` |
| l04 | `f-l04` | | l17 | `f-l17` |
| l05 | `f-l05` | | l18 | `f-l18` |
| l06 | `f-l06` | | l19 (M & A) | `f-l01` |
| l07 | `f-l07` | | l20 (I & O) | `f-l02` |
| l08 | `f-l08` | | l21 (P & T) | `f-l03` |
| l09 | `f-l09` | | l22 (Ei & Au) | `f-l11` |
| l10 | `f-l10` | | l23 (Sch & Ch) | `f-l13` |
| l11 | `f-l11` | | l24 (St & Sp) | `f-l17` |
| l12 | `f-l12` | | l25 (Ö & Ü) | `f-l12` |
| l13 | `f-l13` | | l26 (Qu & X) | `f-l18` |

Beispiel für den Eintrag von l01 (`finaleId` nach `status`, vor `focusAtomIds`):

```json
    {
      "id": "l01",
      "index": 1,
      "phase": 1,
      "title": "M & A (Die Ur-Silbe)",
      "nodeLabel": "M a",
      "status": "authored",
      "finaleId": "f-l01",
      "focusAtomIds": ["letter-m", "letter-a"],
      "taskIds": ["l01-t1", "l01-t2", "l01-t3", "l01-t4", "l01-t5", "l01-t6", "l01-t7", "l01-t8", "l01-t9", "l01-t10"]
    },
```

Die bestehende Formatierung der Datei beibehalten (die Datei ist mehrzeilig eingerückt) — nur das Feld ergänzen, nicht die Datei umformatieren.

- [ ] **Step 6: Kopien abgleichen**

Die drei geänderten Dateien müssen in beiden Verzeichnissen identisch sein:

```bash
cd ~/Projects/abc-vorschul-app
for f in atoms.json lessons.json finales.json; do
  cmp -s "app/src/main/assets/content/$f" "app/src/test/resources/content/$f" && echo "$f OK" || echo "$f DIFFERS"
done
```

Erwartet: dreimal `OK`. Bei `DIFFERS` die Main-Fassung nach `app/src/test/resources/content/` kopieren.

- [ ] **Step 7: Tests laufen lassen**

```bash
./gradlew :app:testDebugUnitTest
```

Erwartet: PASS, alle sieben Tests in `LessonFinaleTest` grün.

- [ ] **Step 8: Commit (nur bei Nutzerfreigabe)**

```bash
git add app/src/main/assets/content app/src/test/resources/content app/src/test/java/app/abcvorschule/content/LessonFinaleTest.kt
git commit -m "feat(content): author 18 finale sentences and wire finaleId on all 26 lessons"
```

---

### Task 3: Validator erzwingt die Redaktionsregeln

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/ContentValidator.kt` (Konstanten bei ca. Zeile 30; neue Prüfblöcke; Lektions-Prüfung bei ca. Zeile 224)
- Test: `app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt`

**Interfaces:**
- Consumes: `LessonFinale`, `ContentPack.finales`, `Lesson.finaleId` (Tasks 1–2)
- Produces:
  - `ContentValidator.MinFinalePictures = 2`, `MaxFinalePictures = 4` (internal/private — Tests prüfen über Meldungen, nicht über die Konstanten)
  - `MinFinaleWords = 4`, `MaxFinaleWords = 7`
  - Sechs neue Meldungstypen, wortgleich wie in den Tests unten

Warum überhaupt im Validator, wenn `LessonFinaleTest` dasselbe prüft? Der Test schützt das **ausgelieferte** Pack. Der Validator schützt **jedes** Pack, das jemand später hinzufügt, und schlägt beim Laden fehl statt beim Rendern — Fail-Fast statt halb gerenderter Screen.

- [ ] **Step 1: Die fehlschlagenden Tests schreiben**

An `ContentValidatorTest.kt` anhängen (innerhalb der Klasse). Der bestehende Helfer `issuesOf { ... }` mutiert das geladene Pack und gibt die Meldungen zurück.

```kotlin
    @Test
    fun authoredLessonWithoutFinaleIsAnIssue() {
        val issues = issuesOf { p ->
            p.copy(lessons = p.lessons.map { if (it.id == "l01") it.copy(finaleId = null) else it })
        }
        assertTrue(issues.toString(), issues.any { it.contains("l01") && it.contains("finaleId") })
    }

    @Test
    fun unknownFinaleReferenceIsAnIssue() {
        val issues = issuesOf { p ->
            p.copy(lessons = p.lessons.map { if (it.id == "l01") it.copy(finaleId = "f-nope") else it })
        }
        assertTrue(issues.toString(), issues.any { it.contains("f-nope") })
    }

    @Test
    fun finalePictureMustExistAsAnAtom() {
        val issues = issuesOf { p ->
            p.copy(finales = p.finales + ("f-l01" to p.finale("f-l01").copy(pictureAtomIds = listOf("mama", "ghost"))))
        }
        assertTrue(issues.toString(), issues.any { it.contains("ghost") })
    }

    @Test
    fun finalePictureMustCarryAnEmoji() {
        // `tisch` exists but carries emoji "" on purpose — there is no usable table emoji.
        val issues = issuesOf { p ->
            p.copy(finales = p.finales + ("f-l01" to p.finale("f-l01").copy(pictureAtomIds = listOf("mama", "tisch"))))
        }
        assertTrue(issues.toString(), issues.any { it.contains("tisch") && it.contains("emoji") })
    }

    @Test
    fun finaleMustNotRepeatTheSameGlyph() {
        // `katze` and `mimi` are both 🐱 — two identical pictures read as a bug.
        val issues = issuesOf { p ->
            p.copy(finales = p.finales + ("f-l01" to p.finale("f-l01").copy(pictureAtomIds = listOf("katze", "mimi"))))
        }
        assertTrue(issues.toString(), issues.any { it.contains("f-l01") && it.contains("glyph") })
    }

    @Test
    fun finaleNeedsTwoToFourPictures() {
        val tooFew = issuesOf { p ->
            p.copy(finales = p.finales + ("f-l01" to p.finale("f-l01").copy(pictureAtomIds = listOf("mama"))))
        }
        assertTrue(tooFew.toString(), tooFew.any { it.contains("f-l01") && it.contains("pictures") })

        val tooMany = issuesOf { p ->
            p.copy(
                finales = p.finales + (
                    "f-l01" to p.finale("f-l01")
                        .copy(pictureAtomIds = listOf("mama", "maus", "apfel", "oma", "hut"))
                    ),
            )
        }
        assertTrue(tooMany.toString(), tooMany.any { it.contains("f-l01") && it.contains("pictures") })
    }

    @Test
    fun finaleTextNeedsFourToSevenWords() {
        val tooLong = issuesOf { p ->
            p.copy(
                finales = p.finales + (
                    "f-l01" to p.finale("f-l01")
                        .copy(text = "Mama Maus mampft einen ganz besonders dicken roten Apfel!")
                    ),
            )
        }
        assertTrue(tooLong.toString(), tooLong.any { it.contains("f-l01") && it.contains("words") })

        val tooShort = issuesOf { p ->
            p.copy(finales = p.finales + ("f-l01" to p.finale("f-l01").copy(text = "Mama mampft!")))
        }
        assertTrue(tooShort.toString(), tooShort.any { it.contains("f-l01") && it.contains("words") })
    }

    @Test
    fun unreferencedFinaleIsAnIssue() {
        val issues = issuesOf { p ->
            p.copy(
                finales = p.finales + (
                    "f-orphan" to LessonFinale(
                        id = "f-orphan",
                        text = "Der Fuchs klaut den Keks!",
                        tts = "Der Fuchs klaut den Keks!",
                        pictureAtomIds = listOf("fuchs", "keks"),
                    )
                    ),
            )
        }
        assertTrue(issues.toString(), issues.any { it.contains("f-orphan") && it.contains("not referenced") })
    }
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests '*ContentValidatorTest*'
```

Erwartet: FAIL — acht neue Tests schlagen fehl, weil der Validator die Regeln noch nicht kennt. `shippedPackIsValid` muss weiterhin grün sein.

- [ ] **Step 3: Konstanten ergänzen**

In `ContentValidator.kt` nach `MaxSentenceTrayTiles`:

```kotlin
    /** Redaktionsregeln für Finale-Sätze, siehe PRODUCT_PRINCIPLES.md Abschnitt 12. */
    private const val MinFinalePictures = 2
    private const val MaxFinalePictures = 4
    private const val MinFinaleWords = 4
    private const val MaxFinaleWords = 7
```

- [ ] **Step 4: Finale-Prüfblock einfügen**

In `validate()` direkt **nach** dem `pack.sentences.values.forEach { ... }`-Block einfügen:

```kotlin
        pack.finales.values.forEach { finale ->
            val count = finale.pictureAtomIds.size
            if (count !in MinFinalePictures..MaxFinalePictures) {
                issues += ValidationIssue(
                    "finale ${finale.id} holds $count pictures; expected " +
                        "$MinFinalePictures..$MaxFinalePictures",
                )
            }
            val words = finale.text.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
            if (words !in MinFinaleWords..MaxFinaleWords) {
                issues += ValidationIssue(
                    "finale ${finale.id} holds $words words; expected " +
                        "$MinFinaleWords..$MaxFinaleWords",
                )
            }
            if (finale.tts.isBlank()) {
                issues += ValidationIssue("finale ${finale.id} has no tts")
            }
            finale.pictureAtomIds.forEach { id ->
                requireAtom("finale ${finale.id}", id)
                if (pack.atoms[id]?.emoji.isNullOrBlank()) {
                    issues += ValidationIssue("finale ${finale.id} picture $id carries no emoji")
                }
            }
            // Dedupe on the glyph, not the atom id: `katze` and `mimi` share one cat
            // emoji, and two identical pictures read as a bug — same rule as LessonEmojis.
            val glyphs = finale.pictureAtomIds.mapNotNull { pack.atoms[it]?.emoji }
                .filter { it.isNotBlank() }
            if (glyphs.size != glyphs.distinct().size) {
                issues += ValidationIssue("finale ${finale.id} shows the same glyph twice")
            }
        }
```

- [ ] **Step 5: Lektions-Prüfung ergänzen**

Im `when (lesson.status)`-Block, im Zweig `LessonStatus.authored`, direkt nach der `focusAtomIds.isEmpty()`-Prüfung:

```kotlin
                    val finaleId = lesson.finaleId
                    if (finaleId.isNullOrBlank()) {
                        issues += ValidationIssue("authored lesson ${lesson.id} needs a finaleId")
                    } else if (finaleId !in pack.finales) {
                        issues += ValidationIssue(
                            "lesson ${lesson.id} references missing finale $finaleId",
                        )
                    }
```

- [ ] **Step 6: Unreferenzierte Finales melden**

Direkt neben dem bestehenden „task is not referenced"-Block am Ende von `validate()`:

```kotlin
        val referencedFinales = pack.lessons.mapNotNull { it.finaleId }.toSet()
        (pack.finales.keys - referencedFinales).forEach {
            issues += ValidationIssue("finale $it is not referenced by any lesson")
        }
```

- [ ] **Step 7: Tests laufen lassen**

```bash
./gradlew :app:testDebugUnitTest
```

Erwartet: PASS. `shippedPackIsValid` bestätigt, dass der Content aus Task 2 alle neuen Regeln erfüllt.

- [ ] **Step 8: Commit (nur bei Nutzerfreigabe)**

```bash
git add app/src/main/java/app/abcvorschule/content/ContentValidator.kt app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt
git commit -m "feat(content): validate finale editorial rules on load"
```

---

### Task 4: Abschluss vom Abbruch unterscheiden

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/ContentModels.kt` (`ContentPack`, Accessor)
- Modify: `app/src/main/java/app/abcvorschule/session/SessionModels.kt` (`SessionUiState`, ca. Zeile 86)
- Modify: `app/src/main/java/app/abcvorschule/session/SessionViewModel.kt` (`advance()` bei ca. Zeile 452, `backToPath()` bei ca. Zeile 175)
- Test: `app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt`

**Interfaces:**
- Consumes: `Lesson.finaleId` (Task 1)
- Produces:
  - `ContentPack.finaleIdOf(lessonId: String): String?` — null bei unbekannter Lektion oder fehlendem Feld
  - `SessionUiState.completedFinaleId: String?` — von Task 6 gelesen, um zwischen Finale- und Abbruch-Variante zu wählen

Der Screen erscheint an **zwei** Stellen: `advance()` setzt ihn bei `step == null` (echter Abschluss), `onBackPressed()` setzt ihn bei `sessionPoints > 0` (Abbruch). Nur der erste Pfad setzt `completedFinaleId`. `backToPath()` räumt es auf, damit ein späterer Abbruch den Satz nicht erbt.

**Was hier automatisiert getestet wird — und was nicht.** `LessonSessionTest` prüft in diesem Repo ausschließlich reine Funktionen (`SessionProgression`, `ProgressionEngine`); es existiert **kein** Aufbau für `SessionViewModel`-Tests (kein `Dispatchers.setMain`, kein Fake-`ProgressRepository` für das ViewModel). Diese Task baut deshalb keinen ViewModel-Test, sondern zieht die Ableitung in eine reine Funktion und testet die. Die Verdrahtung selbst — dass `advance()` setzt und `onBackPressed()` nicht — wird im Smoke-Test in Task 7 abgedeckt (Schritte 1–10). Wer diesen Plan ausführt, soll das nicht stillschweigend anders lösen: entweder so, oder mit dem Nutzer klären, ob ein ViewModel-Testaufbau erwünscht ist.

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

An `app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt` anhängen (innerhalb der Klasse; `pack` ist dort bereits als Feld vorhanden):

```kotlin
    @Test
    fun everyAuthoredLessonYieldsItsFinaleId() {
        pack.authoredLessons.forEach { lesson ->
            assertEquals(
                "lesson ${lesson.id}",
                lesson.finaleId,
                pack.finaleIdOf(lesson.id),
            )
        }
    }

    @Test
    fun finaleIdOfAnUnknownLessonIsNull() {
        // A stale resume snapshot must not crash the finish transition.
        assertNull(pack.finaleIdOf("l99"))
    }

    @Test
    fun repeatLessonYieldsTheFinaleOfItsBaseLesson() {
        assertEquals("f-l01", pack.finaleIdOf("l19"))
    }
```

`assertEquals` und `assertNull` sind in der Datei bereits importiert.

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests '*LessonSessionTest*'
```

Erwartet: Kompilierfehler — `finaleIdOf` existiert nicht.

- [ ] **Step 3: Accessor auf `ContentPack`**

In `ContentModels.kt`, neben `fun lesson(id: String)`:

```kotlin
    /**
     * Das Finale einer Lektion, oder null. Bewusst tolerant, wo [lesson] wirft: ein
     * veralteter Resume-Snapshot darf den Abschluss-Übergang nicht zum Absturz bringen.
     */
    fun finaleIdOf(lessonId: String): String? =
        lessons.firstOrNull { it.id == lessonId }?.finaleId
```

- [ ] **Step 4: Feld in `SessionUiState`**

In `SessionModels.kt`, in `data class SessionUiState`, nach `sessionPoints`:

```kotlin
    /**
     * Das Finale der abgeschlossenen Lektion — gesetzt **nur** beim echten Abschluss.
     * Ein Abbruch mit Punkten zeigt dieselbe Route, aber ohne Satz: der Satz belohnt
     * Durchhalten und nutzt sich sonst ab (PRODUCT_PRINCIPLES.md Abschnitt 5).
     */
    val completedFinaleId: String? = null,
```

- [ ] **Step 5: In `advance()` setzen**

In `SessionViewModel.advance()`, im `if (step == null)`-Zweig, das `copy(...)` erweitern:

```kotlin
        if (step == null) {
            progressRepository.saveSession(null)
            val finaleId = state.lessonId?.let { pack.finaleIdOf(it) }
            _ui.update {
                it.copy(
                    screen = AppScreen.RewardSummary,
                    completedFinaleId = finaleId,
                    speakCue = null,
                    successPhase = SuccessPhase.Idle,
                    successSpeakText = null,
                    points = progress.points,
                )
            }
            return
        }
```

`state` ist die lokale Kopie vom Anfang der Funktion — sie hält `lessonId` noch, weil erst `backToPath()` es leert.

- [ ] **Step 6: In `backToPath()` zurücksetzen**

In `SessionViewModel.backToPath()`, im `copy(...)` neben `lessonId = null`:

```kotlin
                    completedFinaleId = null,
```

Der Abbruchpfad in `onBackPressed()` braucht **keine** Änderung: er setzt nur `screen`, und `completedFinaleId` ist dort noch `null`, weil `openLesson` einen frischen `SessionUiState` erzeugt.

- [ ] **Step 7: Tests laufen lassen**

```bash
./gradlew :app:testDebugUnitTest
```

Erwartet: PASS.

- [ ] **Step 8: Commit (nur bei Nutzerfreigabe)**

```bash
git add app/src/main/java/app/abcvorschule/content/ContentModels.kt app/src/main/java/app/abcvorschule/session app/src/test/java/app/abcvorschule/session/LessonSessionTest.kt
git commit -m "feat(session): carry completedFinaleId only on a genuine lesson finish"
```

---

### Task 5: Layout-Logik als reine Funktionen

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/shell/FinaleLayout.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/shell/FinaleLayoutTest.kt`

**Interfaces:**
- Consumes: `ContentPack`, `LessonFinale`, `Atom` (Tasks 1–2)
- Produces:
  - `data class FinalePicture(val atomId: String, val emoji: String, val lemma: String)`
  - `FinaleLayout.picturesOf(pack: ContentPack, finale: LessonFinale): List<FinalePicture>`
  - `FinaleLayout.pictureSizeSp(count: Int): Int`
  - `FinaleLayout.revealDelayMillis(index: Int): Long`

Compose lässt sich in diesem Repo nicht automatisiert testen (keine androidTests). Deshalb wandert alles, was eine Entscheidung trifft, in ein Compose-freies Objekt — dasselbe Muster wie `ui/exercise/WordFrameSizing.kt` und `content/LessonEmojis.kt`. Task 6 rendert nur noch.

- [ ] **Step 1: Den fehlschlagenden Test schreiben**

Neue Datei `app/src/test/java/app/abcvorschule/ui/shell/FinaleLayoutTest.kt`:

```kotlin
package app.abcvorschule.ui.shell

import app.abcvorschule.content.ContentRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FinaleLayoutTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun picturesKeepSentenceOrderAndCarryTheSpokenWord() {
        val pictures = FinaleLayout.picturesOf(pack, pack.finale("f-l01"))
        assertEquals(listOf("mama", "maus", "apfel"), pictures.map { it.atomId })
        assertEquals(listOf("👩", "🐭", "🍎"), pictures.map { it.emoji })
        assertEquals(listOf("Mama", "Maus", "Apfel"), pictures.map { it.lemma })
    }

    @Test
    fun picturesSkipAtomsWithoutAnEmoji() {
        // Defensive: the validator rejects such content, but a half-rendered row
        // would be worse than a shorter one.
        val finale = pack.finale("f-l01").copy(pictureAtomIds = listOf("mama", "tisch", "apfel"))
        assertEquals(listOf("mama", "apfel"), FinaleLayout.picturesOf(pack, finale).map { it.atomId })
    }

    @Test
    fun picturesSkipUnknownAtoms() {
        val finale = pack.finale("f-l01").copy(pictureAtomIds = listOf("mama", "ghost"))
        assertEquals(listOf("mama"), FinaleLayout.picturesOf(pack, finale).map { it.atomId })
    }

    @Test
    fun everyShippedFinaleRendersAllItsPictures() {
        pack.finales.values.forEach { finale ->
            assertEquals(
                "finale ${finale.id} loses a picture",
                finale.pictureAtomIds.size,
                FinaleLayout.picturesOf(pack, finale).size,
            )
        }
    }

    @Test
    fun fourPicturesShrinkSoTheRowStillFitsANarrowScreen() {
        assertEquals(64, FinaleLayout.pictureSizeSp(2))
        assertEquals(64, FinaleLayout.pictureSizeSp(3))
        assertEquals(52, FinaleLayout.pictureSizeSp(4))
    }

    @Test
    fun pictureSizeStaysSaneOutsideTheAuthoredRange() {
        assertEquals(64, FinaleLayout.pictureSizeSp(0))
        assertEquals(52, FinaleLayout.pictureSizeSp(9))
    }

    @Test
    fun picturesRevealLeftToRight() {
        assertEquals(0L, FinaleLayout.revealDelayMillis(0))
        assertEquals(180L, FinaleLayout.revealDelayMillis(1))
        assertEquals(360L, FinaleLayout.revealDelayMillis(2))
    }

    @Test
    fun revealDelayNeverGoesNegative() {
        assertTrue(FinaleLayout.revealDelayMillis(-1) >= 0L)
    }
}
```

- [ ] **Step 2: Test laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests '*FinaleLayoutTest*'
```

Erwartet: Kompilierfehler — `FinaleLayout` existiert nicht.

- [ ] **Step 3: `FinaleLayout` implementieren**

Neue Datei `app/src/main/java/app/abcvorschule/ui/shell/FinaleLayout.kt`:

```kotlin
package app.abcvorschule.ui.shell

import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.LessonFinale

/** Ein Bild der Finale-Reihe: Glyph zum Zeigen, Lemma zum Vorsprechen. */
data class FinalePicture(
    val atomId: String,
    val emoji: String,
    val lemma: String,
)

/**
 * Ableitung und Maße der Finale-Bildreihe. Compose-frei, damit die Entscheidungen
 * testbar bleiben — das Repo hat keine androidTests.
 *
 * Deterministisch wie [app.abcvorschule.content.LessonEmojis]: kein Random, keine
 * Sortierung. Die Reihenfolge ist die des Satzes, weil sie den Satz erzählt.
 */
object FinaleLayout {
    private const val BaseSizeSp = 64
    private const val CrowdedSizeSp = 52
    private const val CrowdedFrom = 4
    private const val RevealStepMillis = 180L

    /**
     * Bilder in Satzreihenfolge. Atome ohne Emoji oder ohne Eintrag im Pack werden
     * übersprungen: der Validator lehnt solchen Content ab, aber eine Lücke in der
     * Reihe ist besser als ein leerer Platzhalter.
     */
    fun picturesOf(pack: ContentPack, finale: LessonFinale): List<FinalePicture> =
        finale.pictureAtomIds.mapNotNull { id ->
            val atom = pack.atoms[id] ?: return@mapNotNull null
            if (atom.emoji.isBlank()) return@mapNotNull null
            FinalePicture(atomId = id, emoji = atom.emoji, lemma = atom.lemma)
        }

    /** Vier Bilder brauchen weniger Breite pro Stück, damit die Reihe nicht umbricht. */
    fun pictureSizeSp(count: Int): Int =
        if (count >= CrowdedFrom) CrowdedSizeSp else BaseSizeSp

    /** Staffelung der Einblendung — führt den Blick von links nach rechts. */
    fun revealDelayMillis(index: Int): Long =
        (index.coerceAtLeast(0)) * RevealStepMillis
}
```

- [ ] **Step 4: Tests laufen lassen**

```bash
./gradlew :app:testDebugUnitTest
```

Erwartet: PASS, alle acht Tests grün.

- [ ] **Step 5: Commit (nur bei Nutzerfreigabe)**

```bash
git add app/src/main/java/app/abcvorschule/ui/shell/FinaleLayout.kt app/src/test/java/app/abcvorschule/ui/shell/FinaleLayoutTest.kt
git commit -m "feat(ui): add compose-free finale layout logic"
```

---

### Task 6: End-Screen neu bauen

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/shell/RewardSummaryScreen.kt` (vollständig ersetzen)
- Modify: `app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt:92-98` (Aufruf)

**Interfaces:**
- Consumes: `FinaleLayout`, `FinalePicture` (Task 5), `SessionUiState.completedFinaleId` (Task 4), `ContentPack.finale` (Task 1)
- Produces: `RewardSummaryScreen(finale, pack, ttsAvailable, speaking, onSpeak, onContinue, modifier)` — `finale == null` rendert die schlanke Abbruch-Variante.

Diese Task hat keine automatisierten Tests: sie ist reines Compose-Rendering, und alle Entscheidungen liegen bereits in `FinaleLayout`. Die Absicherung ist der Smoke-Test in Task 7.

- [ ] **Step 1: `RewardSummaryScreen.kt` ersetzen**

```kotlin
package app.abcvorschule.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.R
import app.abcvorschule.content.ContentPack
import app.abcvorschule.content.LessonFinale
import app.abcvorschule.ui.components.AbcContinueButton
import app.abcvorschule.ui.components.AbcSpeakerButton
import app.abcvorschule.ui.components.IconStar
import kotlinx.coroutines.delay

private val BackgroundStarSize = 280.dp
private const val BackgroundStarAlpha = 0.12f

/**
 * Der End-Screen einer Lektion, in zwei Varianten:
 *
 * - [finale] gesetzt (echter Abschluss): Bildreihe, Satz und Speaker über einem
 *   gedämpften Hintergrundstern. Der Satztext richtet sich an den mitlesenden
 *   Erwachsenen — die einzige bewusste Ausnahme von „das Kind kann nicht lesen"
 *   (PRODUCT_PRINCIPLES.md Abschnitt 12), weil keine Handlung am Text hängt.
 * - [finale] null (Abbruch mit Punkten): nur Erfolgs-Header, Stern und Weiter.
 *
 * Zeigt bewusst **keine** Punktezahl: die steht im Übungs-Chrome und auf dem Pfad.
 */
@Composable
fun RewardSummaryScreen(
    finale: LessonFinale?,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var popped by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { popped = true }
    val scale by animateFloatAsState(
        targetValue = if (popped) 1f else 0.7f,
        animationSpec = tween(500),
        label = "reward-scale",
    )

    // Den Satz einmal beim Erscheinen sprechen, wie die Prompt-Ansage in der Übung.
    LaunchedEffect(finale?.id, ttsAvailable) {
        val text = finale?.tts ?: return@LaunchedEffect
        if (ttsAvailable) onSpeak(text)
    }

    Box(modifier = modifier.fillMaxSize()) {
        IconStar(
            tint = MaterialTheme.colorScheme.primary.copy(alpha = BackgroundStarAlpha),
            size = BackgroundStarSize,
            modifier = Modifier
                .align(Alignment.Center)
                .scale(scale),
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.reward_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )

            if (finale == null) {
                Spacer(Modifier.height(1.dp))
            } else {
                FinaleBody(
                    finale = finale,
                    pack = pack,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeak = onSpeak,
                )
            }

            AbcContinueButton(
                onClick = onContinue,
                centered = true,
            )
        }
    }
}

@Composable
private fun FinaleBody(
    finale: LessonFinale,
    pack: ContentPack,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeak: (String) -> Unit,
) {
    val pictures = FinaleLayout.picturesOf(pack, finale)
    val sizeSp = FinaleLayout.pictureSizeSp(pictures.size).sp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            pictures.forEachIndexed { index, picture ->
                var shown by remember(finale.id, picture.atomId) { mutableStateOf(false) }
                LaunchedEffect(finale.id, picture.atomId) {
                    delay(FinaleLayout.revealDelayMillis(index))
                    shown = true
                }
                AnimatedVisibility(
                    visible = shown,
                    enter = fadeIn(tween(260)) + scaleIn(tween(260), initialScale = 0.6f),
                ) {
                    Text(
                        text = picture.emoji,
                        fontSize = sizeSp,
                        // Tippen liest das Wort vor (Prinzip 7).
                        modifier = Modifier.clickable(enabled = ttsAvailable) {
                            onSpeak(picture.lemma)
                        },
                    )
                }
            }
        }

        Text(
            text = finale.text,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )

        AbcSpeakerButton(
            enabled = ttsAvailable,
            speaking = speaking,
            onClick = { onSpeak(finale.tts) },
        )
    }
}
```

- [ ] **Step 2: Aufruf in `TaskShell.kt` anpassen**

Den Block bei `state.screen == AppScreen.RewardSummary` ersetzen:

```kotlin
            state.screen == AppScreen.RewardSummary -> {
                RewardSummaryScreen(
                    finale = state.completedFinaleId?.let { pack.finales[it] },
                    pack = pack,
                    ttsAvailable = ttsAvailable,
                    speaking = speaking,
                    onSpeak = onSpeak,
                    onContinue = viewModel::continueAfterSummary,
                )
            }
```

`pack` ist in diesem `when`-Zweig garantiert nicht null: der Zweig `!state.ready || pack == null` steht davor und fängt das ab. `pack.finales[it]` statt `pack.finale(it)`, damit eine unbekannte ID die schlanke Variante zeigt statt zu crashen.

- [ ] **Step 3: Kompilieren**

```bash
./gradlew :app:assembleDebug
```

Erwartet: BUILD SUCCESSFUL. Häufige Stolperstellen:
- `AbcContinueButton` hat den Parameter `centered` — Signatur in `ui/components/AbcButtons.kt` prüfen, falls der Compiler klagt.
- Wenn `IconStar` kein `modifier` vor `size` erwartet: die Signatur ist `IconStar(tint, modifier, size)`, benannte Argumente verwenden.

- [ ] **Step 4: Tests laufen lassen**

```bash
./gradlew :app:testDebugUnitTest
```

Erwartet: PASS — unverändert, diese Task berührt keine getestete Logik.

- [ ] **Step 5: Commit (nur bei Nutzerfreigabe)**

```bash
git add app/src/main/java/app/abcvorschule/ui/shell
git commit -m "feat(ui): show the lesson finale on the end screen, drop the points line"
```

---

### Task 7: Verifikation und Doku-Abgleich

**Files:**
- Modify: `README.md` (nur wenn dort Content-Dateien aufgezählt werden)
- Modify: `AGENTS.md` (nur wenn der Technik-Kurzüberblick die Content-Dateien aufzählt)
- Modify: `docs/PRODUCT_PRINCIPLES.md` (nur bei Abweichungen zur Umsetzung)

**Interfaces:**
- Consumes: alles aus Tasks 1–6
- Produces: verifizierter, dokumentierter Stand.

`docs/PRODUCT_PRINCIPLES.md` wurde beim Schreiben der Spec bereits ergänzt (Abschnitte 4, 5, 11, 12 und die Review-Tabelle). Diese Task prüft nur, ob die Umsetzung davon abweicht.

- [ ] **Step 1: Volle Test-Suite**

```bash
./gradlew :app:testDebugUnitTest
```

Erwartet: PASS, keine übersprungenen Suites.

- [ ] **Step 2: Voller Build**

```bash
./gradlew :app:assembleDebug
```

Erwartet: BUILD SUCCESSFUL.

- [ ] **Step 3: Content-Kopien final abgleichen**

```bash
cd ~/Projects/abc-vorschul-app
for f in atoms.json lessons.json finales.json sentences.json tasks.json pack.manifest.json; do
  cmp -s "app/src/main/assets/content/$f" "app/src/test/resources/content/$f" && echo "$f OK" || echo "$f DIFFERS"
done
```

Erwartet: sechsmal `OK`. Ein `DIFFERS` bedeutet, dass die App anderen Content lädt als die Tests prüfen.

- [ ] **Step 4: Smoke-Test auf dem Gerät oder Emulator**

Lektion 1 vollständig durchspielen und prüfen:

1. Nach der letzten Rechen-Runde erscheint der End-Screen.
2. „Super gemacht!" steht **oben**, nicht mittig.
3. Es steht **keine** Punktezeile („+1 · Gesamt 134") auf dem Screen.
4. Ein großer, gedämpfter Stern liegt hinter dem Inhalt.
5. Drei Bilder erscheinen nacheinander von links: 👩 🐭 🍎.
6. Der Satz „Mama Maus mampft einen dicken Apfel!" wird gesprochen und steht als Text da.
7. Der Speaker-Button wiederholt den Satz.
8. Tippen auf 🍎 sagt „Apfel".
9. Weiter führt zurück auf den Pfad.

Dann Lektion 2 öffnen, **eine** Aufgabe richtig lösen, Hardware-Back drücken:

10. Der End-Screen zeigt nur Header, Stern und Weiter — **kein** Satz, **keine** Bilder.

- [ ] **Step 5: Doku prüfen**

```bash
cd ~/Projects/abc-vorschul-app
grep -n "sentences.json\|atoms.json\|Content-Schema" README.md AGENTS.md
```

Wo Content-Dateien aufgezählt werden, `finales.json` ergänzen. Wenn die Umsetzung von den Abschnitten 4, 5 oder 12 in `docs/PRODUCT_PRINCIPLES.md` abweicht, dort nachziehen — nicht umgekehrt die Prinzipien an einen Zufall der Implementierung anpassen.

- [ ] **Step 6: Offene Reste notieren**

Falls etwas bewusst offen bleibt (z. B. kein Tisch-Emoji für L03), eine kurze Notiz in `docs/residual-review-findings/feat-lektions-finale.md` anlegen — so verlangt es `AGENTS.md`.

- [ ] **Step 7: Commit (nur bei Nutzerfreigabe)**

```bash
git add README.md AGENTS.md docs
git commit -m "docs: record finale content rules and residual findings"
```

---

## Was dieser Plan bewusst nicht baut

Aus der Spec, Abschnitt 2 — falls jemand versucht ist, „schnell noch" mitzunehmen:

- keine animierte Finale-Szene („Quatsch-Maschine": tippbarer Wal, der Wasser spritzt),
- keine Sprachaufnahme, keine Aussprachebewertung,
- kein Sammelalbum / „Quatsch-Buch",
- keine wortsynchrone Bildeinblendung zum TTS-Audio (System-TTS liefert Wortgrenzen nur über `UtteranceProgressListener.onRangeStart` ab API 26, mit unzuverlässigem Timing).
