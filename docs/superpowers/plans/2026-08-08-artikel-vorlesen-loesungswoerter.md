# Artikel beim Vorlesen der Lösungswörter — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das Erfolgs-Vorsprechen nennt bei Substantiven den Artikel („Baue das Wort Haus" → „das Haus"), gespeist aus Genus-/Nomenklassen-Daten am Atom.

**Architecture:** Drei neue optionale Felder am `Atom` (`gender`, `nounClass`, `articleSpeechOverride`) tragen die Daten. Eine reine Funktion `AtomArticleSpeech.forAtom` leitet daraus den Sprechtext ab; nur `SuccessSpeech` ruft sie auf. `ContentValidator` erzwingt Vollständigkeit. Die TTS-Pipeline spiegelt die Ableitung in `extract.py` und rendert die 85 erreichbaren Clips in einem neuen Profil `article_word`.

**Tech Stack:** Kotlin + kotlinx.serialization (App), JUnit (Tests), Python 3 (`tools/tts`, pytest).

**Spec:** [`docs/superpowers/specs/2026-08-08-artikel-vorlesen-loesungswoerter-design.md`](../specs/2026-08-08-artikel-vorlesen-loesungswoerter-design.md)

## Global Constraints

- Arbeitsverzeichnis ist der Worktree `/Users/cleschke/projects/abc-vorschul-app/.claude/worktrees/artikel-vorlesen-loesungswoerter-57d572`. Alle Pfade sind relativ dazu.
- App-Tests: `./gradlew :app:testDebugUnitTest`. Build: `./gradlew :app:assembleDebug`. Gradle braucht `ANDROID_HOME` als Präfix (siehe Task-Schritte).
- Unit-Tests laden das **ausgelieferte** Content-Pack aus `app/src/main/assets`. Es gibt keine zweite Fixture-Kopie. Content-Varianten im Test immer über `pack.copy(...)` bauen, **nie** eine Pack-Datei duplizieren.
- TTS-Tests: `~/qwen-tts-test/.venv/bin/python -m pytest tools/tts/tests/ -v` — **nicht** das System-Python.
- `tools/tts/profiles.json` und `tools/tts/locks.json` enthalten kuratierte Handarbeit. Nur additiv anfassen, nie neu schreiben.
- Deutsche Fachbegriffe in Doku und Commit-Messages, englische Bezeichner im Code — wie im Bestand.
- Jeder Commit endet mit `Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>`.

## File Structure

| Datei | Verantwortung | Task |
|---|---|---|
| `app/src/main/java/app/abcvorschule/content/ContentModels.kt` | `Gender`, `NounClass`, drei neue `Atom`-Felder | 1 |
| `app/src/main/java/app/abcvorschule/content/AtomArticleSpeech.kt` *(neu)* | Ableitung Atom → Artikel-Sprechtext. Einzige Wahrheitsquelle der Regel auf Kotlin-Seite | 1 |
| `app/src/test/java/app/abcvorschule/content/AtomArticleSpeechTest.kt` *(neu)* | Ableitungstabelle | 1 |
| `app/src/main/java/app/abcvorschule/session/SuccessSpeech.kt` | Drei Zweige nutzen die Ableitung | 2 |
| `app/src/test/java/app/abcvorschule/session/SuccessSpeechTest.kt` | Artikel-Zweige + Nicht-Artikel-Zweige | 2 |
| `app/src/main/java/app/abcvorschule/content/ContentValidator.kt` | Konsistenz- und Vollständigkeitsregeln | 3, 4 |
| `app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt` | Regeln gegen mutierte Packs und gegen das Pack | 3, 4 |
| `app/src/main/assets/content/atoms.json` | Genus-/Nomenklassen-Daten für 152 Substantive | 4 |
| `tools/tts/profiles.json` | Profil `article_word` | 5 |
| `tools/tts/ttskit/extract.py` | `articleTts`-Items, gespiegelte Ableitung | 5 |
| `tools/tts/tests/test_extract.py` | Reichweite, Ableitung, Namen-Ausschluss | 5 |
| `docs/PRODUCT_PRINCIPLES.md`, `tools/tts/README.md`, `AGENTS.md` | Regeln dokumentiert | 6 |

---

### Task 1: Datenmodell und Ableitungsregel

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/ContentModels.kt` (Enums nach `AtomKind`, Felder in `data class Atom`)
- Create: `app/src/main/java/app/abcvorschule/content/AtomArticleSpeech.kt`
- Test: `app/src/test/java/app/abcvorschule/content/AtomArticleSpeechTest.kt`

**Interfaces:**
- Consumes: `Atom`, `AtomKind` aus `ContentModels.kt`
- Produces:
  - `enum class Gender { m, f, n }`
  - `enum class NounClass { thing, person, properName }` (JSON-Wert bleibt `"name"` via `@SerialName`)
  - `Atom.gender: Gender?`, `Atom.nounClass: NounClass?`, `Atom.articleSpeechOverride: String?`
  - `object AtomArticleSpeech { fun forAtom(atom: Atom?): String? }`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/abcvorschule/content/AtomArticleSpeechTest.kt`:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AtomArticleSpeechTest {

    private fun atom(
        display: String,
        gender: Gender? = null,
        nounClass: NounClass? = null,
        override: String? = null,
    ) = Atom(
        id = display.lowercase(),
        lemma = display,
        display = display,
        emoji = "",
        kind = AtomKind.word,
        gender = gender,
        nounClass = nounClass,
        articleSpeechOverride = override,
    )

    @Test
    fun `thing takes the definite article`() {
        assertEquals("das Haus", AtomArticleSpeech.forAtom(atom("Haus", Gender.n, NounClass.thing)))
        assertEquals("die Maus", AtomArticleSpeech.forAtom(atom("Maus", Gender.f, NounClass.thing)))
        assertEquals("der Baum", AtomArticleSpeech.forAtom(atom("Baum", Gender.m, NounClass.thing)))
    }

    @Test
    fun `person takes the indefinite article for m and f`() {
        assertEquals("eine Oma", AtomArticleSpeech.forAtom(atom("Oma", Gender.f, NounClass.person)))
        assertEquals("ein Opa", AtomArticleSpeech.forAtom(atom("Opa", Gender.m, NounClass.person)))
    }

    @Test
    fun `neuter person takes the definite article`() {
        // "ein Opa" und "ein Kind" klingen gleich — beim Neutrum trägt nur "das"
        // das Genus eindeutig. Siehe Spec, Abschnitt 1.
        assertEquals("das Kind", AtomArticleSpeech.forAtom(atom("Kind", Gender.n, NounClass.person)))
    }

    @Test
    fun `a name is spoken bare`() {
        assertEquals("Tom", AtomArticleSpeech.forAtom(atom("Tom", nounClass = NounClass.properName)))
    }

    @Test
    fun `the override wins over the derived form`() {
        val haeuser = atom("Häuser", Gender.n, NounClass.thing, override = "die Häuser")
        assertEquals("die Häuser", AtomArticleSpeech.forAtom(haeuser))
    }

    @Test
    fun `an unclassified atom has no article speech`() {
        assertNull(AtomArticleSpeech.forAtom(atom("ist")))
        assertNull(AtomArticleSpeech.forAtom(null))
    }

    @Test
    fun `a classified atom without gender has no article speech`() {
        // Der Validator verhindert diesen Zustand im Pack; die Ableitung
        // darf daran trotzdem nicht abstürzen oder "null Haus" liefern.
        assertNull(AtomArticleSpeech.forAtom(atom("Haus", gender = null, nounClass = NounClass.thing)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*AtomArticleSpeechTest*'
```

Erwartet: Kompilierfehler — `Gender`, `NounClass`, `AtomArticleSpeech` und die drei `Atom`-Parameter gibt es noch nicht.

- [ ] **Step 3: Add the enums and the atom fields**

In `ContentModels.kt`, direkt nach dem `AtomKind`-Enum einfügen:

```kotlin
/** Grammatisches Genus eines Substantiv-Atoms. */
@Serializable
enum class Gender { m, f, n }

/** Wie ein Substantiv beim Vorsprechen seinen Artikel bekommt. */
@Serializable
enum class NounClass {
    /** Gegenstand, Tier, Pflanze, Abstraktum — bestimmter Artikel. */
    thing,

    /** Personenbezeichnung (Oma, Opa, Clown) — unbestimmter Artikel, Neutrum ausgenommen. */
    person,

    /**
     * Eigenname (Tom, Mimi) — kein Artikel.
     *
     * Heißt in Kotlin `properName`, weil `name` mit `kotlin.Enum.name` kollidiert;
     * der JSON-Wert bleibt `"name"`. Gleiches `@SerialName`-Muster wie bei
     * `sentenceTts`/`promptTts` in `TaskSpecs.kt`.
     */
    @SerialName("name")
    properName,
}
```

`@SerialName` braucht den Import `kotlinx.serialization.SerialName` — prüfe, ob `ContentModels.kt` ihn schon hat, und ergänze ihn sonst.

In `data class Atom`, nach `pluralHighlight`, vor `strokes`:

```kotlin
    /** Genus; gesetzt für [NounClass.thing] und [NounClass.person], null bei Namen. */
    val gender: Gender? = null,
    /** Gesetzt an jedem Substantiv-Atom; null heißt „kein Substantiv". */
    val nounClass: NounClass? = null,
    /** Fertiger Artikel-Sprechtext, wenn die Ableitung nicht passt (Plural-Atome). */
    val articleSpeechOverride: String? = null,
```

- [ ] **Step 4: Write the derivation**

Create `app/src/main/java/app/abcvorschule/content/AtomArticleSpeech.kt`:

```kotlin
package app.abcvorschule.content

/**
 * Wie ein Substantiv-Atom beim Erfolgs-Vorsprechen mit Artikel klingt.
 *
 * Einzige Wahrheitsquelle der Regel auf Kotlin-Seite; `tools/tts/ttskit/extract.py`
 * spiegelt sie, damit die vorproduzierten Clips denselben Text tragen.
 *
 * Gibt `null` zurück, wenn das Atom kein Substantiv ist — der Aufrufer bleibt
 * dann bei seinem bisherigen Sprechtext.
 */
object AtomArticleSpeech {

    fun forAtom(atom: Atom?): String? {
        if (atom == null) return null
        atom.articleSpeechOverride?.takeIf { it.isNotBlank() }?.let { return it }
        val nounClass = atom.nounClass ?: return null
        val display = atom.display.takeIf { it.isNotBlank() } ?: return null
        if (nounClass == NounClass.properName) return display
        val gender = atom.gender ?: return null
        return "${article(nounClass, gender)} $display"
    }

    /**
     * Personen bekommen den unbestimmten Artikel — außer im Neutrum: „ein Opa"
     * und „ein Kind" klingen gleich, obwohl das eine maskulin und das andere
     * neutrum ist. Nur „das" trägt das Genus dort eindeutig.
     */
    private fun article(nounClass: NounClass, gender: Gender): String = when {
        nounClass == NounClass.person && gender == Gender.m -> "ein"
        nounClass == NounClass.person && gender == Gender.f -> "eine"
        gender == Gender.m -> "der"
        gender == Gender.f -> "die"
        else -> "das"
    }
}
```

- [ ] **Step 5: Run test to verify it passes**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*AtomArticleSpeechTest*'
```

Erwartet: PASS, 7 Tests.

- [ ] **Step 6: Run the whole app test suite**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest
```

Erwartet: PASS. Die neuen `Atom`-Felder haben Defaults, bestehende Tests und das Pack bleiben unberührt.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/ContentModels.kt \
        app/src/main/java/app/abcvorschule/content/AtomArticleSpeech.kt \
        app/src/test/java/app/abcvorschule/content/AtomArticleSpeechTest.kt
git commit -m "feat(content): Genus und Nomenklasse am Atom, Artikel-Ableitung

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 2: Erfolgs-Vorsprechen nutzt den Artikel

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/session/SuccessSpeech.kt`
- Test: `app/src/test/java/app/abcvorschule/session/SuccessSpeechTest.kt`

**Interfaces:**
- Consumes: `AtomArticleSpeech.forAtom(atom: Atom?): String?` aus Task 1
- Produces: keine neuen Signaturen; `SuccessSpeech.partsForRound` behält ihre Signatur

**Kontext für den Implementierer:** `partsForRound` liefert eine Liste von Sprechtexten, die nacheinander abgespielt werden. Jeder Eintrag wird über `ClipIndex` als Text-Schlüssel gesucht; fehlt der Clip, spricht Android-TTS den String. Es gibt also keinen zusätzlichen Verdrahtungsschritt.

Prüfe zuerst, ob `app/src/test/java/app/abcvorschule/session/SuccessSpeechTest.kt` schon existiert. Falls ja, hänge die Tests dort an und übernimm den vorhandenen Stil zum Bauen von Runden und Pack. Falls nein, lege die Datei mit dem folgenden Inhalt an.

- [ ] **Step 1: Write the failing test**

```kotlin
package app.abcvorschule.session

import app.abcvorschule.content.Atom
import app.abcvorschule.content.AtomKind
import app.abcvorschule.content.ContentRepository
import app.abcvorschule.content.Gender
import app.abcvorschule.content.NounClass
import app.abcvorschule.content.SoundPositionRound
import app.abcvorschule.content.SymbolInWordRound
import app.abcvorschule.content.WordBuildRound
import org.junit.Assert.assertEquals
import org.junit.Test

class SuccessSpeechArticleTest {

    /** Das ausgelieferte Pack, mit einem kontrollierten Test-Atom ergänzt. */
    private val pack = ContentRepository.loadFromClasspath().let { loaded ->
        val haus = Atom(
            id = "test-haus",
            lemma = "Haus",
            display = "Haus",
            emoji = "🏠",
            kind = AtomKind.word,
            gender = Gender.n,
            nounClass = NounClass.thing,
        )
        loaded.copy(atoms = loaded.atoms + (haus.id to haus))
    }

    @Test
    fun `word build speaks the article with the target word`() {
        val round = wordBuildRound(targetAtomId = "test-haus")
        assertEquals(listOf("das Haus"), SuccessSpeech.partsForRound(round, pack, praise = false))
    }

    @Test
    fun `symbol in word speaks the article with the word`() {
        val round = symbolInWordRound(wordAtomId = "test-haus")
        assertEquals(listOf("das Haus"), SuccessSpeech.partsForRound(round, pack, praise = false))
    }

    @Test
    fun `sound position speaks the article with the word`() {
        val round = soundPositionRound(atomId = "test-haus")
        assertEquals(listOf("das Haus"), SuccessSpeech.partsForRound(round, pack, praise = false))
    }

    @Test
    fun `an unclassified target keeps its bare display`() {
        val round = wordBuildRound(targetAtomId = "ich")
        assertEquals(listOf("ich"), SuccessSpeech.partsForRound(round, pack, praise = false))
    }
}
```

Die drei Hilfsfunktionen `wordBuildRound`, `symbolInWordRound`, `soundPositionRound` baust du
mit den Pflichtfeldern der jeweiligen Runden-Datenklasse aus `content/TaskSpecs.kt`. Lies die
Klassen dort und setze nur die Pflichtfelder; alles andere hat Defaults. Falls im Bestand
schon Runden-Builder für Tests existieren (suche mit `grep -rn "WordBuildRound(" app/src/test`),
benutze die statt neuer.

Prüfe außerdem den Namen der Lade-Funktion: `grep -n "fun load" app/src/main/java/app/abcvorschule/content/ContentRepository.kt` und such dir das Muster aus einem bestehenden Test (`grep -rln "ContentRepository" app/src/test`).

- [ ] **Step 2: Run test to verify it fails**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*SuccessSpeechArticleTest*'
```

Erwartet: FAIL — erwartet `das Haus`, bekommen `Haus`.

- [ ] **Step 3: Use the derivation in the three branches**

In `SuccessSpeech.kt` den Import ergänzen:

```kotlin
import app.abcvorschule.content.AtomArticleSpeech
```

Und die drei Zweige ersetzen:

```kotlin
        is WordBuildRound -> listOfNotNull(
            pack.atoms[round.targetAtomId]?.let { AtomArticleSpeech.forAtom(it) ?: it.display }
                ?: round.promptTts.takeIf { it.isNotBlank() },
        )
        …
        is SoundPositionRound -> listOfNotNull(
            pack.atoms[round.atomId]?.let { AtomArticleSpeech.forAtom(it) ?: it.lemma }
                ?: round.promptTts.takeIf { it.isNotBlank() },
        )
        is SymbolInWordRound -> listOfNotNull(
            pack.atoms[round.wordAtomId]?.let { AtomArticleSpeech.forAtom(it) ?: it.display }
                ?: round.promptTts.takeIf { it.isNotBlank() },
        )
```

Ein Kommentar über dem `WordBuildRound`-Zweig hält die Regel fest:

```kotlin
        // §7: Die Antwort nennt bei Substantiven den Artikel ("das Haus"), die
        // Aufgabe nicht ("Baue das Wort Haus"). Nicht-Substantive bleiben nackt.
```

**Nicht anfassen:** `CountAddRound` (Zahl statt Artikel), `SyllableMergeRound` (nur Silben), `SentenceOrderRound`, `SentencePictureRound`, `LetterTraceRound`, `SymbolHuntRound` (Graphem).

- [ ] **Step 4: Run test to verify it passes**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*SuccessSpeechArticleTest*'
```

Erwartet: PASS, 4 Tests.

- [ ] **Step 5: Add the negative tests**

Ergänze in derselben Testklasse, dass die anderen Zweige **nicht** anfassen. Baue je eine
`CountAddRound` (mit `iconAtomId = "ameise"`, `answer = 2`) und eine `SymbolHuntRound`
(mit `targetAtomId = "letter-m"`) und prüfe, dass die Ausgabe kein „der/die/das/ein/eine"
als eigenes Wort am Anfang trägt:

```kotlin
    @Test
    fun `count add keeps number and noun without an article`() {
        val round = countAddRound(iconAtomId = "ameise", answer = 2)
        assertEquals(listOf("2 Ameisen"), SuccessSpeech.partsForRound(round, pack, praise = false))
    }

    @Test
    fun `symbol hunt speaks the bare grapheme`() {
        val round = symbolHuntRound(targetAtomId = "letter-m")
        assertEquals(listOf("M"), SuccessSpeech.partsForRound(round, pack, praise = false))
    }
```

Passe die erwarteten Strings an, was die bestehenden Atome tatsächlich liefern — lies
`ameise` und `letter-m` aus `app/src/main/assets/content/atoms.json` und `CountAddRound.spokenAnswer`
in `TaskSpecs.kt`, statt die Werte zu raten.

- [ ] **Step 6: Run the whole app test suite**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest
```

Erwartet: PASS. Noch trägt kein Pack-Atom `nounClass`, das Verhalten ist also unverändert.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/abcvorschule/session/SuccessSpeech.kt \
        app/src/test/java/app/abcvorschule/session/SuccessSpeechArticleTest.kt
git commit -m "feat(session): Erfolgs-Vorsprechen nennt den Artikel des Lösungsworts

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 3: Konsistenzregeln im Validator

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/content/ContentValidator.kt`
- Test: `app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt`

**Interfaces:**
- Consumes: `Gender`, `NounClass`, `Atom.gender`, `Atom.nounClass`, `Atom.articleSpeechOverride` aus Task 1
- Produces: keine neuen öffentlichen Signaturen; die Regeln laufen innerhalb von `ContentValidator.validate`

**Kontext:** Diese vier Regeln fallen auf dem aktuellen Pack nicht an — noch trägt kein Atom die Felder. Sie werden gegen mutierte Packs getestet. Die Vollständigkeitsregel kommt in Task 4 dazu.

- [ ] **Step 1: Write the failing tests**

Hänge an `ContentValidatorTest.kt` an. Übernimm den vorhandenen Stil, wie dort ein mutiertes
Pack gebaut wird (`grep -n "copy(" app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt`)
und wie die Issues geprüft werden.

```kotlin
    private fun packWithAtom(atom: Atom) =
        pack.copy(atoms = pack.atoms + (atom.id to atom))

    private fun testAtom(
        id: String = "test-atom",
        display: String = "Testwort",
        gender: Gender? = null,
        nounClass: NounClass? = null,
        override: String? = null,
        pluralDisplay: String? = null,
    ) = Atom(
        id = id, lemma = display, display = display, emoji = "", kind = AtomKind.word,
        pluralDisplay = pluralDisplay,
        gender = gender, nounClass = nounClass, articleSpeechOverride = override,
    )

    @Test
    fun `thing without gender is an issue`() {
        val issues = ContentValidator.validate(packWithAtom(testAtom(nounClass = NounClass.thing)))
        assertTrue(issues.any { "test-atom" in it.message && "gender" in it.message })
    }

    @Test
    fun `person without gender is an issue`() {
        val issues = ContentValidator.validate(packWithAtom(testAtom(nounClass = NounClass.person)))
        assertTrue(issues.any { "test-atom" in it.message && "gender" in it.message })
    }

    @Test
    fun `gender without noun class is an issue`() {
        val issues = ContentValidator.validate(packWithAtom(testAtom(gender = Gender.f)))
        assertTrue(issues.any { "test-atom" in it.message && "nounClass" in it.message })
    }

    @Test
    fun `a name with a gender is an issue`() {
        val issues = ContentValidator.validate(
            packWithAtom(testAtom(gender = Gender.m, nounClass = NounClass.properName)),
        )
        assertTrue(issues.any { "test-atom" in it.message })
    }

    @Test
    fun `a plural atom without an override is an issue`() {
        val singular = testAtom(id = "test-sing", display = "Testding", pluralDisplay = "Testdinge",
            gender = Gender.n, nounClass = NounClass.thing)
        val plural = testAtom(id = "test-plur", display = "Testdinge",
            gender = Gender.n, nounClass = NounClass.thing)
        val mutated = pack.copy(
            atoms = pack.atoms + (singular.id to singular) + (plural.id to plural),
        )
        val issues = ContentValidator.validate(mutated)
        assertTrue(issues.any { "test-plur" in it.message && "articleSpeechOverride" in it.message })
    }

    @Test
    fun `a self plural needs no override`() {
        // "Eimer" ist sein eigener Plural — das ist kein Plural-Atom.
        val selfPlural = testAtom(id = "test-self", display = "Testeimer",
            pluralDisplay = "Testeimer", gender = Gender.m, nounClass = NounClass.thing)
        val issues = ContentValidator.validate(packWithAtom(selfPlural))
        assertTrue(issues.none { "test-self" in it.message })
    }

    @Test
    fun `a plural atom with an override is fine`() {
        val singular = testAtom(id = "test-sing", display = "Testding", pluralDisplay = "Testdinge",
            gender = Gender.n, nounClass = NounClass.thing)
        val plural = testAtom(id = "test-plur", display = "Testdinge", gender = Gender.n,
            nounClass = NounClass.thing, override = "die Testdinge")
        val mutated = pack.copy(
            atoms = pack.atoms + (singular.id to singular) + (plural.id to plural),
        )
        val issues = ContentValidator.validate(mutated)
        assertTrue(issues.none { "test-plur" in it.message })
    }
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*ContentValidatorTest*'
```

Erwartet: die fünf Positiv-Regeln schlagen fehl (keine Issues erzeugt), die zwei Negativ-Tests sind grün.

- [ ] **Step 3: Implement the rules**

In `ContentValidator.validate`, im vorhandenen `pack.atoms.values.forEach { atom -> … }`-Block
(nach der `display.isBlank()`-Prüfung, vor der Stroke-Schleife) ergänzen:

```kotlin
            if (atom.nounClass != null && atom.nounClass != NounClass.properName && atom.gender == null) {
                issues += ValidationIssue(
                    "atom ${atom.id} has nounClass ${atom.nounClass} but no gender",
                )
            }
            if (atom.gender != null && atom.nounClass == null) {
                issues += ValidationIssue("atom ${atom.id} has a gender but no nounClass")
            }
            if (atom.nounClass == NounClass.properName && atom.gender != null) {
                issues += ValidationIssue(
                    "atom ${atom.id} is a name and must not carry a gender",
                )
            }
```

Danach, **außerhalb** der Atom-Schleife, die Plural-Regel — sie braucht den Blick über alle Atome:

```kotlin
        // Plural-Atome ("Häuser") nehmen im Deutschen "die", unabhängig vom Genus des
        // Singulars; die Ableitung kann das nicht wissen und braucht einen Override.
        // Selbst-Plurale ("Eimer" → "Eimer") sind keine eigenen Plural-Atome.
        val pluralDisplays: Set<String> = pack.atoms.values
            .mapNotNull { singular ->
                singular.pluralDisplay?.takeIf { it != singular.display }
            }
            .toSet()
        pack.atoms.values.forEach { atom ->
            if (atom.display in pluralDisplays &&
                atom.nounClass != null &&
                atom.articleSpeechOverride.isNullOrBlank()
            ) {
                issues += ValidationIssue(
                    "atom ${atom.id} is a plural form and needs an articleSpeechOverride",
                )
            }
        }
```

Ergänze die Imports für `Gender` und `NounClass`, falls die Datei sie nicht schon über das
Package sieht (gleiches Package → kein Import nötig).

- [ ] **Step 4: Run tests to verify they pass**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*ContentValidatorTest*'
```

Erwartet: PASS.

- [ ] **Step 5: Run the whole app test suite**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest
```

Erwartet: PASS — das ausgelieferte Pack trägt noch keine der Felder.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/ContentValidator.kt \
        app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt
git commit -m "feat(content): Validator prüft Genus-, Nomenklassen- und Plural-Konsistenz

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 4: Content-Daten anreichern und Vollständigkeit erzwingen

**Files:**
- Modify: `app/src/main/assets/content/atoms.json` (152 Atome bekommen Felder)
- Modify: `app/src/main/java/app/abcvorschule/content/ContentValidator.kt` (Vollständigkeitsregel)
- Test: `app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt`

**Interfaces:**
- Consumes: alles aus Task 1 und 3
- Produces: `ContentValidator.ArticleFreeSpeechAtomIds: Set<String>` — die bewusst artikellosen erreichbaren Atome

**Kontext:** Der fehlschlagende Test ist hier die Vollständigkeitsregel gegen das ausgelieferte Pack; die „Implementierung" sind die Daten. `atoms.json` ist mit `json.dumps(..., ensure_ascii=False, indent=2) + "\n"` **byte-identisch** round-trip-fähig — ein Python-Patch-Skript erzeugt also einen minimalen Diff. Schreib das Skript in den Scratchpad, nicht ins Repo; committet wird nur `atoms.json`.

- [ ] **Step 1: Write the failing test**

Hänge an `ContentValidatorTest.kt` an:

```kotlin
    @Test
    fun `every reachable noun atom in the shipped pack is classified`() {
        val issues = ContentValidator.validate(pack)
        assertEquals(emptyList<ValidationIssue>(), issues)
    }
```

Falls es diesen „das Pack ist sauber"-Test schon gibt, reicht er — dann nur prüfen, dass er
existiert, und mit Step 2 weitermachen.

- [ ] **Step 2: Implement the completeness rule**

In `ContentValidator`, als Feld neben den anderen Konstanten:

```kotlin
    /**
     * Erreichbare Atome, die bewusst ohne Artikel gesprochen werden: Interjektion,
     * Pronomen, Adjektiv, Präposition. Alles andere Erreichbare muss eine
     * [NounClass] tragen, sonst fiele es still auf die artikellose Form zurück.
     */
    val ArticleFreeSpeechAtomIds: Set<String> = setOf("hallo", "ich", "rot", "am")
```

Und in `validate`, nach der Plural-Regel:

```kotlin
        // Reichweite des Erfolgs-Vorsprechens: nur diese Atome werden je mit Artikel
        // gesprochen (Wort-Detektiv leitet sich aus word_build ab, ist also enthalten).
        val speechReachable: Set<String> = pack.tasks.values.flatMap { spec ->
            when (spec) {
                is WordBuildSpec -> spec.rounds.map { it.targetAtomId }
                is SoundPositionSpec -> spec.rounds.map { it.atomId }
                else -> emptyList()
            }
        }.toSet()
        speechReachable.forEach { id ->
            if (id in ArticleFreeSpeechAtomIds) return@forEach
            val atom = pack.atoms[id] ?: return@forEach
            if (atom.nounClass == null) {
                issues += ValidationIssue(
                    "atom $id is spoken as a success answer but has no nounClass " +
                        "(add gender + nounClass, or list it in ArticleFreeSpeechAtomIds)",
                )
            }
        }
```

Prüfe die Klassennamen der Specs mit
`grep -n "class WordBuildSpec\|class SoundPositionSpec" app/src/main/java/app/abcvorschule/content/TaskSpecs.kt`
und passe sie an, falls sie anders heißen.

- [ ] **Step 3: Run test to verify it fails**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*ContentValidatorTest*'
```

Erwartet: FAIL mit **87** Issues der Form `atom haus is spoken as a success answer but has no nounClass`.

87, nicht 85: erreichbar sind 91 Atome, davon stehen 4 in `ArticleFreeSpeechAtomIds`. Die
beiden Namen (`tom`, `mimi`) brauchen sehr wohl eine `nounClass` (`properName`) und werden
deshalb mitgemeldet — sie erzeugen nur später keinen *Clip*, weil ihr Sprechtext dem
`display` entspricht. 85 ist die Clip-Zahl in Task 5, nicht die Befundzahl hier.

- [ ] **Step 4: Write the enrichment script**

Lege `<scratchpad>/enrich_atoms.py` an (Scratchpad-Pfad steht in deinem Systemkontext).
Die Tabelle unten ist vollständig — sie deckt alle 152 Substantiv-Atome ab. Alle nicht
gelisteten Atome (Buchstaben, Silben, Funktionswörter, Verben, Adjektive, Interjektionen)
bleiben unverändert.

```python
#!/usr/bin/env python3
"""Trägt gender / nounClass / articleSpeechOverride in atoms.json nach."""
import json
from pathlib import Path

PATH = Path("app/src/main/assets/content/atoms.json")

# id -> (gender, nounClass)
THINGS = {
    "aepfel": "m", "affe": "m", "ameise": "f", "ampel": "f", "apfel": "m", "auto": "n",
    "baer": "m", "baeume": "m", "bahn": "f", "ball": "m", "banane": "f", "baum": "m",
    "bett": "n", "biene": "f", "blatt": "n", "blitz": "m", "brokkoli": "m", "brot": "n",
    "buch": "n", "bus": "m", "busch": "m", "dach": "n", "dino": "m", "dose": "f",
    "drache": "m", "dreieck": "n", "ei": "n", "eier": "n", "eimer": "m", "eis": "n",
    "elefant": "m", "ente": "f", "erdbeere": "f", "eule": "f", "fahrrad": "n",
    "feder": "f", "fenster": "n", "feuer": "n", "fisch": "m", "flugzeug": "n",
    "frosch": "m", "fuchs": "m", "fuss": "m", "gabel": "f", "giraffe": "f", "gras": "n",
    "haeusser": "n", "hahn": "m", "hase": "m", "haus": "n", "herz": "n", "himmel": "m",
    "hund": "m", "hut": "m", "igel": "m", "insel": "f", "jacke": "f", "jojo": "n",
    "kaese": "m", "katze": "f", "keks": "m", "klavier": "n", "kleid": "n", "kreis": "m",
    "krokodil": "n", "kuchen": "m", "kuh": "f", "lama": "n", "lampe": "f",
    "loeffel": "m", "loewe": "m", "markt": "m", "maus": "f", "milch": "f", "mond": "m",
    "nase": "f", "nest": "n", "nilpferd": "n", "nuss": "f", "ofen": "m", "ohr": "n",
    "paket": "n", "park": "m", "pferd": "n", "pflanze": "f", "pilz": "m",
    "pinguin": "m", "pizza": "f", "polizei": "f", "pony": "n", "quadrat": "n",
    "qualle": "f", "quark": "m", "quelle": "f", "rad": "n", "radio": "n", "raupe": "f",
    "rose": "f", "ruebe": "f", "sack": "m", "salami": "f", "salat": "m", "sand": "m",
    "schaf": "n", "schuh": "m", "schule": "f", "sofa": "n", "sonne": "f",
    "spiegel": "m", "spinne": "f", "stern": "m", "strand": "m", "strasse": "f",
    "stuhl": "m", "suppe": "f", "tag": "m", "tal": "n", "taube": "f", "taxi": "n",
    "tee": "m", "tiger": "m", "tisch": "m", "tomate": "f", "tor": "n", "tram": "f",
    "tropfen": "m", "tuer": "f", "ufo": "n", "uhu": "m", "vase": "f", "vogel": "m",
    "wal": "m", "wasser": "n", "weg": "m", "wespe": "f", "wolke": "f", "xylofon": "n",
    "yacht": "f", "zahn": "m", "zebra": "n", "zitrone": "f", "zucker": "m", "zug": "m",
}

PERSONS = {
    "clown": "m", "hexe": "f", "mama": "f", "oma": "f", "opa": "m", "papa": "m",
    "pirat": "m",
}

NAMES = ["mimi", "tom"]

# Plural-Atome: "die" unabhängig vom Genus des Singulars.
OVERRIDES = {
    "haeusser": "die Häuser",
    "baeume": "die Bäume",
    "aepfel": "die Äpfel",
    "eier": "die Eier",
}


def main() -> None:
    raw = PATH.read_text(encoding="utf-8")
    data = json.loads(raw)
    seen = set()

    for atom in data["atoms"]:
        aid = atom["id"]
        if aid in THINGS:
            atom["gender"] = THINGS[aid]
            atom["nounClass"] = "thing"
            seen.add(aid)
        elif aid in PERSONS:
            atom["gender"] = PERSONS[aid]
            atom["nounClass"] = "person"
            seen.add(aid)
        elif aid in NAMES:
            atom["nounClass"] = "name"
            seen.add(aid)
        if aid in OVERRIDES:
            atom["articleSpeechOverride"] = OVERRIDES[aid]

    expected = set(THINGS) | set(PERSONS) | set(NAMES)
    missing = expected - seen
    if missing:
        raise SystemExit(f"ids not found in atoms.json: {sorted(missing)}")

    PATH.write_text(json.dumps(data, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"classified {len(seen)} atoms, {len(OVERRIDES)} overrides")


if __name__ == "__main__":
    main()
```

- [ ] **Step 5: Run the script and check the diff**

```bash
python3 <scratchpad>/enrich_atoms.py && git diff --stat app/src/main/assets/content/atoms.json
```

Erwartet: `classified 152 atoms, 4 overrides`.

**Feldreihenfolge geht vor Diff-Reinheit.** `atoms.json` wird von Hand autoriert; die drei
neuen Felder stehen deshalb bei **jedem** Atom an derselben Stelle — nach `pluralHighlight`,
vor `strokes`, also in derselben Reihenfolge wie in der Kotlin-`Atom`-Klasse. Dass die bis
dahin letzte Zeile eines Atoms dabei ein Komma bekommt und im Diff als geändert erscheint,
ist erwartetes Rauschen und **kein** Grund, die Felder woanders einzufügen.

Prüfe stattdessen, dass nur Kommas gewandert sind — jede entfernte Zeile muss sich in einer
hinzugefügten Zeile plus Komma wiederfinden:

```bash
git diff app/src/main/assets/content/atoms.json | grep '^-' | grep -v '^---' | sed 's/^-//;s/,$//' | sort > /tmp/removed.txt
git diff app/src/main/assets/content/atoms.json | grep '^+' | grep -v '^+++' | sed 's/^+//;s/,$//' | sort > /tmp/added.txt
comm -23 /tmp/removed.txt /tmp/added.txt
```

Gibt `comm` etwas aus, ist echter Inhalt verlorengegangen: nicht committen, sondern melden.
Zusätzlich muss ein Round-Trip byte-identisch sein:

```bash
python3 -c "
import json
p='app/src/main/assets/content/atoms.json'
raw=open(p,encoding='utf-8').read()
assert json.dumps(json.loads(raw),ensure_ascii=False,indent=2)+'\n'==raw, 'Format abgewichen'
print('round-trip ok')
"
```

- [ ] **Step 6: Run the validator test**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*ContentValidatorTest*'
```

Erwartet: PASS. Schlägt die Plural- oder Konsistenzregel an, nenne das betroffene Atom und
korrigiere Tabelle oder Override — rate nicht.

- [ ] **Step 7: Spot-check the derived speech**

Ergänze in `AtomArticleSpeechTest.kt` einen Test gegen das ausgelieferte Pack, der die
didaktisch heiklen Fälle festnagelt:

```kotlin
    @Test
    fun `the shipped pack derives the expected article forms`() {
        val pack = ContentRepository.loadFromClasspath()
        fun speech(id: String) = AtomArticleSpeech.forAtom(pack.atoms[id])

        assertEquals("das Haus", speech("haus"))
        assertEquals("die Maus", speech("maus"))
        assertEquals("der Baum", speech("baum"))
        assertEquals("das Lama", speech("lama"))
        assertEquals("das Pony", speech("pony"))
        assertEquals("die Häuser", speech("haeusser"))
        assertEquals("die Bäume", speech("baeume"))
        assertEquals("eine Oma", speech("oma"))
        assertEquals("ein Opa", speech("opa"))
        assertEquals("Tom", speech("tom"))
        assertNull(speech("ich"))
        assertNull(speech("letter-m"))
    }
```

Ergänze den Import für `ContentRepository` und passe den Namen der Lade-Funktion an das an,
was `ContentRepository.kt` tatsächlich anbietet.

- [ ] **Step 8: Run the whole app test suite**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest
```

Erwartet: PASS.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/assets/content/atoms.json \
        app/src/main/java/app/abcvorschule/content/ContentValidator.kt \
        app/src/test/java/app/abcvorschule/content/ContentValidatorTest.kt \
        app/src/test/java/app/abcvorschule/content/AtomArticleSpeechTest.kt
git commit -m "feat(content): Genus und Nomenklasse für 152 Substantiv-Atome

Vollständigkeitsregel im Validator: jedes vom Erfolgs-Vorsprechen
erreichbare Atom ist klassifiziert oder steht in ArticleFreeSpeechAtomIds.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 5: TTS-Pipeline — Profil und Extraktion

**Files:**
- Modify: `tools/tts/profiles.json` (Profil `article_word` ergänzen, **additiv**)
- Modify: `tools/tts/ttskit/extract.py`
- Test: `tools/tts/tests/test_extract.py`

**Interfaces:**
- Consumes: `atoms.json` mit `gender`/`nounClass`/`articleSpeechOverride` aus Task 4
- Produces:
  - `extract.article_speech(atom: dict) -> str | None` — Spiegel von `AtomArticleSpeech.forAtom`
  - `FIELD_TO_PROFILE["articleTts"] == "article_word"`
  - Items mit `id = "atom:{atomId}:articleTts"`, `field = "articleTts"`

- [ ] **Step 1: Write the failing tests**

Hänge an `tools/tts/tests/test_extract.py` an. Sieh dir zuerst an, wie die bestehenden Tests
ihr Content-Verzeichnis bekommen (`grep -n "content_dir\|tmp_path\|CONTENT" tools/tts/tests/test_extract.py`)
und folge dem Muster.

```python
def test_article_speech_mirrors_the_kotlin_rule():
    from ttskit.extract import article_speech

    assert article_speech({"display": "Haus", "gender": "n", "nounClass": "thing"}) == "das Haus"
    assert article_speech({"display": "Maus", "gender": "f", "nounClass": "thing"}) == "die Maus"
    assert article_speech({"display": "Baum", "gender": "m", "nounClass": "thing"}) == "der Baum"
    assert article_speech({"display": "Oma", "gender": "f", "nounClass": "person"}) == "eine Oma"
    assert article_speech({"display": "Opa", "gender": "m", "nounClass": "person"}) == "ein Opa"
    assert article_speech({"display": "Kind", "gender": "n", "nounClass": "person"}) == "das Kind"
    assert article_speech({"display": "Tom", "nounClass": "name"}) == "Tom"
    assert article_speech({"display": "Häuser", "gender": "n", "nounClass": "thing",
                           "articleSpeechOverride": "die Häuser"}) == "die Häuser"
    assert article_speech({"display": "ist"}) is None
    assert article_speech({"display": "Haus", "nounClass": "thing"}) is None


def test_article_items_cover_only_reachable_atoms():
    items = extract_items(CONTENT_DIR)
    by_id = {i.id: i for i in items if i.field == "articleTts"}

    # word_build-Ziel und sound_position-Wort → Clip
    assert by_id["atom:haus:articleTts"].text == "das Haus"
    assert by_id["atom:ameise:articleTts"].text == "die Ameise"
    # klassifiziert, aber nie vorgesprochen → kein Clip
    assert "atom:banane:articleTts" not in by_id
    # Name: Sprechtext == display, wäre ein Duplikat des word-Clips
    assert "atom:tom:articleTts" not in by_id
    # kein Substantiv
    assert "atom:ich:articleTts" not in by_id
    assert len(by_id) == 85


def test_article_items_use_the_article_word_profile():
    from ttskit.extract import profile_for_item

    items = [i for i in extract_items(CONTENT_DIR) if i.field == "articleTts"]
    assert items
    assert all(profile_for_item(i) == "article_word" for i in items)
```

- [ ] **Step 2: Run tests to verify they fail**

```bash
~/qwen-tts-test/.venv/bin/python -m pytest tools/tts/tests/test_extract.py -v
```

Erwartet: FAIL — `article_speech` existiert nicht.

- [ ] **Step 3: Implement the mirror and the extraction**

In `tools/tts/ttskit/extract.py`, `FIELD_TO_PROFILE` ergänzen:

```python
    "articleTts": "article_word",
```

Und nach `_spoken_answer` — direkt daneben, weil es dieselbe Rolle spielt:

```python
_DEFINITE = {"m": "der", "f": "die", "n": "das"}
_INDEFINITE = {"m": "ein", "f": "eine"}


def article_speech(atom: dict) -> str | None:
    """Mirror AtomArticleSpeech.forAtom in AtomArticleSpeech.kt.

    Personen bekommen den unbestimmten Artikel — außer im Neutrum, wo nur "das"
    das Genus eindeutig trägt ("ein Opa" und "ein Kind" klingen sonst gleich).
    """
    override = (atom.get("articleSpeechOverride") or "").strip()
    if override:
        return override
    noun_class = atom.get("nounClass")
    if not noun_class:
        return None
    display = (atom.get("display") or "").strip()
    if not display:
        return None
    if noun_class == "name":
        return display
    gender = atom.get("gender")
    if not gender:
        return None
    if noun_class == "person" and gender in _INDEFINITE:
        return f"{_INDEFINITE[gender]} {display}"
    return f"{_DEFINITE[gender]} {display}"


def _speech_reachable_atom_ids(tasks: list[dict]) -> set[str]:
    """Atome, die SuccessSpeech je mit Artikel spricht.

    Spiegelt SuccessSpeech.partsForRound: Wort-Bauer und Auditiver Finder.
    Der Wort-Detektiv leitet sich zur Laufzeit aus den word_build-Wörtern ab
    (SymbolInWordDerivation) und ist damit enthalten.
    """
    reachable: set[str] = set()
    for task in tasks:
        trainer = task.get("trainer")
        for round_ in task.get("rounds", []):
            if trainer == "word_build" and round_.get("targetAtomId"):
                reachable.add(round_["targetAtomId"])
            elif trainer == "sound_position" and round_.get("atomId"):
                reachable.add(round_["atomId"])
    return reachable
```

In `extract_items`, in der Atom-Schleife (nach dem `lemma`-`add`):

```python
    reachable = _speech_reachable_atom_ids(tasks)

    for atom in sorted(atoms, key=lambda a: a["id"]):
        add(f"atom:{atom['id']}:lemma", …)          # unverändert

        if atom["id"] not in reachable:
            continue
        speech = article_speech(atom)
        # Gleicher Text wie das Lemma (Namen) → kein zweiter Clip.
        if not speech or speech == atom.get("display"):
            continue
        add(f"atom:{atom['id']}:articleTts", speech, "articleTts", "atoms.json", None,
            f"{atom.get('display', atom['id'])} (Artikel)")
```

`reachable` muss **vor** der Schleife berechnet werden; `tasks` ist an der Stelle schon geladen.
Prüfe die Reihenfolge im Bestand und zieh die Zeile notfalls hoch.

- [ ] **Step 4: Add the profile**

`tools/tts/profiles.json` ist kuratiert — nur den einen Block ergänzen, nichts anderes
anfassen. Übernimm die `sampling`-Werte von `word` und setze `max_new_tokens` auf 35:

```json
    "article_word": {
      "label": "Artikel + Wort",
      "speaker": "sohee",
      "language": "german",
      "instruct": "Sprich Artikel und Nomen als eine einzige Einheit, ohne Pause nach dem Artikel. Klar und freundlich, in ruhigem Tempo. Keine Frage-Melodie, keine Übertreibung, keine lange Betonung. Kein Stöhnen, kein Zischen. Kein Englisch, kein Koreanisch. Sprich mit tiefer Stimme.",
      "sampling": {
        "temperature": 0.55,
        "top_k": 40,
        "top_p": 0.8,
        "repetition_penalty": 1.05,
        "subtalker_temperature": 0.45,
        "subtalker_top_k": 50,
        "subtalker_top_p": 1,
        "max_new_tokens": 35
      },
      "seedPool": []
    }
```

Prüfe, ob `PROFILE_PRIORITY` in `tools/tts/ttskit/export.py` eine vollständige Profil-Liste
erwartet (`grep -n "PROFILE_PRIORITY" tools/tts/ttskit/export.py`). Falls ja, `article_word`
dort direkt **hinter** `word` einsortieren — ein Artikel-Clip soll nie einen Einzelwort-Clip
im Index verdrängen.

- [ ] **Step 5: Run tests to verify they pass**

```bash
~/qwen-tts-test/.venv/bin/python -m pytest tools/tts/tests/ -v
```

Erwartet: PASS, inklusive der bestehenden Tests. Weicht die Zahl 85 ab, gib die tatsächliche
Zahl und die Differenzliste aus — nicht die Assertion auf den Ist-Wert biegen, ohne die
Abweichung zu erklären.

- [ ] **Step 6: Check the extraction end to end**

```bash
~/qwen-tts-test/.venv/bin/python tools/tts/tts extract && ~/qwen-tts-test/.venv/bin/python tools/tts/tts status
```

Erwartet: `article_word` taucht mit 85 Clips auf, alle als „fehlt". Die Zahlen der anderen
Profile bleiben unverändert (vorher: word 260, prompt 223, miss 81, reward 47, phoneme 37,
sentence 26, finale 18, ui 2).

- [ ] **Step 7: Commit**

```bash
git add tools/tts/ttskit/extract.py tools/tts/profiles.json tools/tts/tests/test_extract.py
git commit -m "feat(tts): Profil article_word und articleTts-Extraktion

85 Clips für die vom Erfolgs-Vorsprechen erreichbaren Substantive.
Ableitung spiegelt AtomArticleSpeech.kt, wie _spoken_answer spokenAnswer spiegelt.

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

### Task 6: Dokumentation

**Files:**
- Modify: `docs/PRODUCT_PRINCIPLES.md` (§4 Content-Graph, §7 Sprache & Audio)
- Modify: `tools/tts/README.md` (Profil-Zuordnung, Umfangstabelle)
- Modify: `AGENTS.md` (Kurzfassung Kind-UI-Regeln)

**Interfaces:**
- Consumes: das fertige Verhalten aus Task 1–5
- Produces: nichts Ausführbares

- [ ] **Step 1: Update PRODUCT_PRINCIPLES §4**

In den Abschnitt „4. Content-Graph" als neuen Aufzählungspunkt nach dem Orthografie-Punkt:

```markdown
- **Genus und Nomenklasse am Atom.** Jedes Substantiv-Atom trägt `gender` (`m`/`f`/`n`) und
  `nounClass` (`thing` / `person` / `name`); `articleSpeechOverride` überschreibt den
  abgeleiteten Sprechtext, wo die Regel nicht greift (Plural-Atome wie `Häuser` nehmen „die",
  unabhängig vom Genus des Singulars). Nicht-Substantive — Funktionswörter, Verben,
  Adjektive, Buchstaben, Silben — tragen die Felder nicht. `ContentValidator` erzwingt, dass
  jedes vom Erfolgs-Vorsprechen erreichbare Atom klassifiziert ist oder ausdrücklich in
  `ArticleFreeSpeechAtomIds` steht.
```

- [ ] **Step 2: Update PRODUCT_PRINCIPLES §7**

In den Abschnitt „7. Sprache & Audio", direkt nach dem Punkt „Bei Erfolg: Antwort
vorsprechen …":

```markdown
- **Die Antwort nennt den Artikel, die Aufgabe nicht.** Ist das Lösungswort ein Substantiv,
  spricht das Erfolgs-Vorsprechen es mit Artikel („Baue das Wort Haus" → „das Haus") —
  Gegenstände und Tiere mit dem bestimmten (der/die/das), Personenbezeichnungen mit dem
  unbestimmten (ein/eine), Namen ohne. Neutrum-Personen bekommen „das": „ein Opa" und
  „ein Kind" wären sonst nicht unterscheidbar. Betroffen sind Wort-Bauer, Wort-Detektiv und
  Auditiver Finder (`SuccessSpeech`). **Nicht** betroffen: Prompts, das Antippen von Items,
  `missTts`, Rechnen („zwei Ameisen" — vor einer Zahl steht kein Artikel) und ganze Sätze,
  die ihre Artikel schon tragen. Abgeleitet wird in `AtomArticleSpeech`; `tools/tts` spiegelt
  die Regel, damit vorproduzierte Clips denselben Text tragen.
```

- [ ] **Step 3: Update tools/tts/README.md**

Im Abschnitt „Profil-Zuordnung" nach dem Lemma-Absatz ergänzen:

```markdown
Das Profil `article_word` trägt die Lösungswörter **mit Artikel** („das Haus"), die das
Erfolgs-Vorsprechen nennt. Es ist bewusst nicht `word`: dessen `max_new_tokens: 25` (≈ 2,0 s)
schneidet „eine Erdbeere" ab, und die Instruktion muss ausdrücklich verlangen, Artikel und
Nomen als eine Einheit zu sprechen — abgesetzt klingt es wie zwei aneinandergehängte Clips.
Ein Artikel-Item entsteht nur für Atome, die `SuccessSpeech` erreichen kann
(`word_build.targetAtomId` ∪ `sound_position.atomId`); die übrigen klassifizierten
Substantive stünden sonst dauerhaft als „fehlt" in `tts status` und würden echte Lücken
verdecken.
```

Und die Umfangstabelle aktualisieren: `article_word | 85`, Gesamtzahl auf 779, die
Item-Zahl im Einleitungssatz entsprechend anheben. Nimm die tatsächlichen Zahlen aus
`tts status` (Task 5, Step 6), nicht die hier geschätzten.

- [ ] **Step 4: Update AGENTS.md**

Im Abschnitt „Kind-UI-Regeln & Trainer-Typen" bei den Kernpunkten ergänzen:

```markdown
- **Artikel im Erfolgs-Vorsprechen**: Substantiv-Lösungswörter werden mit Artikel
  vorgesprochen („das Haus"), Aufgabenstellungen nicht. Genus und Nomenklasse stehen am
  Atom, Regel in `AtomArticleSpeech`, Details in PRODUCT_PRINCIPLES §4/§7.
```

- [ ] **Step 5: Verify the build**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Erwartet: PASS.

- [ ] **Step 6: Commit**

```bash
git add docs/PRODUCT_PRINCIPLES.md tools/tts/README.md AGENTS.md
git commit -m "docs: Artikel-Regel im Erfolgs-Vorsprechen dokumentiert

Co-Authored-By: Claude Opus 5 <noreply@anthropic.com>"
```

---

## Nach dem Plan: Audio-Kuratierung

Die 85 `article_word`-Clips sind nach Task 5 als „fehlt" sichtbar, aber noch nicht produziert.
Das ist Handarbeit am Web-Interface und **nicht Teil dieses Plans**:

```bash
./start-tts-ui.sh
```

Dort das Profil `article_word` filtern, Batch-Lauf über alle 85 mit 2 Beispielen, anhören,
per Radio-Button „Produktion" bestätigen, dann `tts export`. Bis dahin spricht Android-TTS
„das Haus" — die Funktion ist ab Task 4 vollständig, nur die Stimme ist noch die des Systems.
