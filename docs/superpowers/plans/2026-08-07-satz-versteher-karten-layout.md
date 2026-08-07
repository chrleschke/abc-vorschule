# Satz-Versteher — Kartenlayout und Antwort-Feedback (Implementation Plan)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Bildkarten des Satz-Verstehers rücken knapp unter die Bildschirmmitte, die Emojis werden deutlich größer, der graue Kartengrund fällt weg, ein Fehltipp wackelt die getippte Karte, und ein Treffer zieht die richtige Karte groß in die Mitte, solange der Satz erneut vorgelesen wird.

**Architecture:** Drei Ebenen, in dieser Reihenfolge gebaut. (1) Compose-freie Rechenlogik in `SentencePictureSides.kt` — die Schüttelkurve als neues Objekt, ein `baseScale`-Parameter an der bestehenden Emoji-Sizing-Funktion. Das Repo hat **keine** androidTests, also ist alles Prüfbare hier und nur hier. (2) `ExerciseStage` bekommt einen opt-in `answerAnchor`-Parameter mit `Bottom` als Vorbelegung, damit die zehn anderen Aufrufer sich nicht bewegen. (3) `SentencePictureTrainer` verdrahtet beides und spaltet das heutige Einzelsignal `(solvedCorrect || resolved)` in `highlight` (Rahmen, auch beim Auflösen) und `celebrate` (Vergrößerung, nur bei eigenem Treffer).

**Tech Stack:** Kotlin, Jetpack Compose (`animateFloatAsState`, `graphicsLayer`, `BoxWithConstraints`, Row-`weight`), JUnit4 (JVM-Tests, laden das ausgelieferte Content-Pack).

**Spec:** `docs/superpowers/specs/2026-08-07-satz-versteher-karten-layout-design.md`

## Global Constraints

- **Kein Rot für Kinder.** Der Fehltipp wird *bewegt* quittiert, nie gefärbt. `ClayRed` bleibt Erwachsenen-Fehlertext (PRODUCT_PRINCIPLES §10), §8 („Falsche Antwort wird **nicht** rot markiert") gilt unverändert für alle Trainer. Keine neue Farbe, keine neue Farbrolle.
- **Emojis bleiben in *einer* Zeile.** `maxLines = 1` und `softWrap = false` am Emoji-`Text` bleiben stehen — sie sind der Riegel gegen den Bug, bei dem das letzte Emoji unsichtbar wurde und 16 Runden zwei identische Karten zeigten. Kein Grid, kein Umbruch.
- **Kein sichtbarer Satztext, solange TTS spricht.** Der Fallback-`Text` mit `testTag("sentence_picture_fallback_text")` bleibt genau wie er ist, inklusive `if (!ttsAvailable)`-Bedingung.
- **`ExerciseStage`-Vorbelegung ist `AnswerAnchor.Bottom`.** Die zehn bestehenden Aufrufer (`LetterTraceTrainer`, `MathExercise`, `SentenceOrderTrainer`, `SoundPositionTrainer`, `SyllableMergeTrainer`, `SymbolHuntTrainer`, `SymbolInWordTrainer`, `VisualQuantityBoard`, `WordBuildTrainer` und der Satz-Versteher selbst vor Task 4) werden **nicht** angefasst und dürfen sich visuell nicht bewegen.
- **Neue Basisgrößen der Emojis:** `110f` / `76f` / `56f` für 1 / 2 / 3 Atome. **Karten-Innenabstand horizontal 4dp**, **Kartenabstand in der Reihe 8dp**, vertikaler Innenabstand bleibt 18dp.
- **Schüttel-Kennwerte:** `AmplitudeDp = 12f`, `DurationMs = 420`, `Cycles = 2.5f`.
- **Erfolgs-Kennwerte:** Fortschritt 0f→1f in 360ms mit `FastOutSlowInEasing`; Gewinner-`weight` 1f→3f, Verlierer-`weight` 1f→0.001f und Alpha 1f→0f; `baseScale` 1f→1.6f.
- **Auflösen („Zeig mir") feiert nicht.** Kein `celebrate`, keine Vergrößerung — §8 sagt „Auflösen ist nicht grün", und die Feier gehört dem Kind, das selbst gelöst hat.
- Unverändert: `MinEmojiSp`, `EmojiAdvanceEm`, das fontScale-Zurückgeben, das Abrunden, `SentencePictureSides.correctOnLeft` und seine vier Tests, Scoring, `misses`-Zählung, „Zeig mir" nach zwei Misses, das `interactionLocked`-Muster (Opacity 0.5↔1.0, `tween(200)`).
- Tests: `./gradlew :app:testDebugUnitTest` · Build: `./gradlew :app:assembleDebug`. Beides muss nach **jeder** Task grün sein.
- Content-Fixtures nie duplizieren — Tests laden den ausgelieferten Pack, bei Bedarf `pack.copy(...)` mutieren.

## File Structure

| Datei | Verantwortung | Task |
| --- | --- | --- |
| `app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureSides.kt` | Compose-freie Rechenlogik des Trainers: Seitenwahl (bestehend), Emoji-Größe (erweitert um `baseScale`), Schüttelkurve (neu) | 1, 2 |
| `app/src/test/java/app/abcvorschule/ui/exercise/SentencePictureSidesTest.kt` | JVM-Tests für ebendiese Logik | 1, 2 |
| `app/src/main/java/app/abcvorschule/ui/exercise/ExerciseStage.kt` | Layout-Grundform aller Übungen; neu: `AnswerAnchor` | 3 |
| `app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureTrainer.kt` | Bühne und Karten des Satz-Verstehers; Verdrahtung von allem oben | 4, 5, 6 |
| `docs/PRODUCT_PRINCIPLES.md` | §6, §9, §10 an den Ist-Stand ziehen | 7 |

Sechs Code-Tasks in aufsteigender Abhängigkeit, dann Doku. Task 1 und 2 sind reine Logik mit Tests und blockieren niemanden außer sich selbst. Task 3 ist ein isolierter Layout-Parameter. Task 4–6 ändern dieselbe Datei und **müssen** deshalb sequentiell laufen.

---

### Task 1: Schüttelkurve `SentencePictureCardShake`

Reine Mathematik, kein Compose. Die Kurve muss testbar sein, weil das Repo keine androidTests hat — eine in der Composable versteckte Animation wäre unprüfbar.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureSides.kt` (neues Objekt am Dateiende anhängen)
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/SentencePictureSidesTest.kt` (Tests am Dateiende anhängen)

**Interfaces:**
- Consumes: nichts.
- Produces: `SentencePictureCardShake.offsetDp(progress: Float): Float` · `SentencePictureCardShake.AmplitudeDp: Float` (12f) · `SentencePictureCardShake.DurationMs: Int` (420) · `SentencePictureCardShake.Cycles: Float` (2.5f)

- [ ] **Step 1: Failing Tests schreiben**

An das Ende von `app/src/test/java/app/abcvorschule/ui/exercise/SentencePictureSidesTest.kt` anhängen, **innerhalb** der bestehenden `class SentencePictureSidesTest { … }` (also vor deren schließender Klammer):

```kotlin
    @Test
    fun shakeStartsAndEndsAtRest() {
        // Der Versatz wird über graphicsLayer gezeichnet: bleibt am Ende etwas
        // stehen, sitzt die Karte für den Rest der Runde schief.
        assertEquals(0f, SentencePictureCardShake.offsetDp(0f), 0.001f)
        assertEquals(0f, SentencePictureCardShake.offsetDp(1f), 0.001f)
    }

    @Test
    fun shakeNeverLeavesItsAmplitude() {
        // 12dp ist der Abstand, den die Karte zur Nachbarkarte hat (8dp Lücke,
        // graphicsLayer clippt nicht) — mehr würde sichtbar überlappen.
        (0..100).forEach { i ->
            val p = i / 100f
            val offset = SentencePictureCardShake.offsetDp(p)
            assertTrue(
                "offset $offset at progress $p exceeds ${SentencePictureCardShake.AmplitudeDp}dp",
                kotlin.math.abs(offset) <= SentencePictureCardShake.AmplitudeDp + 0.001f,
            )
        }
    }

    @Test
    fun shakeOscillatesAsOftenAsCyclesPromises() {
        // 2.5 Zyklen einer Sinuskurve haben 5 Halbwellen, also 4 Nulldurchgänge
        // im offenen Intervall. Weniger wäre ein Ausschlag statt eines Wackelns.
        val samples = (1..999).map { SentencePictureCardShake.offsetDp(it / 1000f) }
        val signChanges = (1 until samples.size).count { i ->
            samples[i - 1] < 0f && samples[i] > 0f || samples[i - 1] > 0f && samples[i] < 0f
        }
        assertEquals(4, signChanges)
    }

    @Test
    fun shakeAmplitudeDecaysSoTheCardComesToRest() {
        // Ohne abklingende Hüllkurve stoppt die Karte mitten im vollen Ausschlag
        // — das liest sich wie ein Ruck, nicht wie ein Auslaufen. Verglichen
        // werden die Extrema der ersten und der letzten Halbwelle.
        fun peakBetween(from: Float, to: Float): Float =
            (0..200).map { from + (to - from) * it / 200f }
                .maxOf { kotlin.math.abs(SentencePictureCardShake.offsetDp(it)) }

        val firstHalfWave = peakBetween(0f, 0.2f)
        val lastHalfWave = peakBetween(0.8f, 1f)
        assertTrue(
            "first peak $firstHalfWave should be clearly larger than last $lastHalfWave",
            firstHalfWave > lastHalfWave * 1.5f,
        )
    }

    @Test
    fun shakeClampsProgressOutsideTheUnitInterval() {
        // animateFloatAsState kann bei Spring-Overshoot über 1f laufen; ein
        // Aufruf mit 1.05f darf keinen Sprung erzeugen.
        assertEquals(0f, SentencePictureCardShake.offsetDp(-0.5f), 0.001f)
        assertEquals(0f, SentencePictureCardShake.offsetDp(1.5f), 0.001f)
    }
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.SentencePictureSidesTest"
```

Erwartet: Kompilierfehler „Unresolved reference: SentencePictureCardShake".

- [ ] **Step 3: Objekt implementieren**

An das **Ende** von `app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureSides.kt` anhängen (die bestehenden Objekte `SentencePictureSides` und `SentencePictureCardSizing` unverändert lassen). Der Import `kotlin.math.PI` und `kotlin.math.sin` kommt oben zu `kotlin.math.floor` dazu:

```kotlin
/**
 * Das Wackeln der falsch getippten Bildkarte. Compose-frei, damit die Kurve
 * prüfbar ist — dasselbe Muster wie `BurstGeometry.sparkOffsets`, denn das Repo
 * hat keine androidTests.
 *
 * Bewusst *nur* Bewegung, keine Farbe: die Produktprinzipien markieren eine
 * falsche Antwort für Kinder nicht rot (§8), und `ClayRed` ist die Fehlerfarbe
 * für Erwachsenentext (§10). Ein Fehltipp ist hier kein Fehler, der benannt
 * wird, sondern eine Karte, die nicht nachgibt.
 */
object SentencePictureCardShake {
    /**
     * Größter horizontaler Ausschlag. Die Nachbarkarte ist nur 8dp entfernt und
     * `graphicsLayer` clippt nicht — 12dp überlappt sie also sichtbar, und genau
     * das macht das Wackeln auch am Rand des Blickfelds erkennbar. Mehr würde
     * die Karten verschmelzen lassen.
     */
    const val AmplitudeDp = 12f

    /** Kurz genug, dass die Wiederholung des Satzes nicht darauf warten muss. */
    const val DurationMs = 420

    /** 2.5 Zyklen = 5 Halbwellen: hin, zurück, hin, zurück, aus. */
    const val Cycles = 2.5f

    /**
     * Horizontaler Versatz in dp für [progress] 0f..1f. Sinus mit linear
     * abklingender Hüllkurve: die Karte läuft aus statt abrupt zu stoppen, und
     * sie endet exakt auf ihrer Ausgangsposition (`offsetDp(1f) == 0f`) — sonst
     * bliebe sie für den Rest der Runde schief stehen.
     *
     * Außerhalb von 0..1 geklemmt: `animateFloatAsState` kann überschwingen.
     */
    fun offsetDp(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val decay = 1f - p
        return (AmplitudeDp * decay * sin(2.0 * PI * Cycles * p)).toFloat()
    }
}
```

- [ ] **Step 4: Tests laufen lassen, grün bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.SentencePictureSidesTest"
```

Erwartet: PASS, alle bestehenden Tests der Klasse weiter grün.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureSides.kt app/src/test/java/app/abcvorschule/ui/exercise/SentencePictureSidesTest.kt
git commit -m "feat(satz-versteher): schüttelkurve für die falsch getippte bildkarte"
```

---

### Task 2: Emoji-Größen — neue Basiswerte und `baseScale`

Zwei Änderungen an einer Funktion: die Basisstaffelung steigt, und ein vorbelegter `baseScale`-Faktor erlaubt der Erfolgsanimation, die Basis zu heben, ohne den Breitendeckel zu umgehen.

Warum `baseScale` überhaupt nötig ist: bei der Erfolgsanimation wird die Karte fast bühnenbreit, aber auf dieser Breite bindet weiter die *Basisgröße*, nicht der Deckel — die Verbreiterung allein würde das Emoji also nicht wachsen lassen.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureSides.kt` (`SentencePictureCardSizing.emojiSp`, Zeilen 60–76)
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/SentencePictureSidesTest.kt` (zwei bestehende Tests anpassen, zwei neue)

**Interfaces:**
- Consumes: nichts.
- Produces: `SentencePictureCardSizing.emojiSp(atomCount: Int, contentWidthDp: Float, fontScale: Float, baseScale: Float = 1f): Float`

- [ ] **Step 1: Bestehende Tests auf die neuen Basiswerte ziehen, neue Tests schreiben**

In `app/src/test/java/app/abcvorschule/ui/exercise/SentencePictureSidesTest.kt` **ersetzen**:

```kotlin
    @Test
    fun emojiSizeIsUnchangedAtFontScaleOneWhenThereIsRoom() {
        // Kein-Regressions-Anker: auf einer breiten Karte bei fontScale 1.0 bleiben
        // die ursprünglichen 72/56/44sp stehen.
        val wide = 400f
        assertEquals(72f, SentencePictureCardSizing.emojiSp(1, wide, 1f), 0f)
        assertEquals(56f, SentencePictureCardSizing.emojiSp(2, wide, 1f), 0f)
        assertEquals(44f, SentencePictureCardSizing.emojiSp(3, wide, 1f), 0f)
    }
```

durch:

```kotlin
    @Test
    fun emojiSizeIsTheFullBaseAtFontScaleOneWhenThereIsRoom() {
        // Kein-Regressions-Anker für die *Staffelung*, nicht für die Zahlen von
        // gestern: auf einer Karte, die breit genug ist, greift die Basis.
        // 400dp reicht dafür bei 1 und 2 Emojis; bei 3 deckelt schon hier die
        // Breite (400 / 3.6 = 111), deshalb steht der 3er-Wert weiter unten in
        // emojiRowFitsTheNarrowestSupportedCard…
        val wide = 400f
        assertEquals(110f, SentencePictureCardSizing.emojiSp(1, wide, 1f), 0f)
        assertEquals(76f, SentencePictureCardSizing.emojiSp(2, wide, 1f), 0f)
    }
```

Ebenfalls **ersetzen** (nur die Breitenkonstante und ihr Kommentar ändern sich):

```kotlin
    @Test
    fun emojiRowFitsTheNarrowestSupportedCardForEveryValidatorPermittedCount() {
        // 320dp Gerät − 2 × 20dp AbcDimens.screenHorizontal = 280dp für die Reihe,
        // − 14dp Kartenabstand, / 2 Karten = 133dp Karte, − 2 × 10dp Karteninnen-
        // abstand = 113dp Inhaltsbreite. Der Validator erlaubt 1..3 Atome je Karte;
        // ein Test, der nur 2 prüft, würde die 3-Atom-Karten übersehen — genau die,
        // bei denen vorher das letzte Emoji verschwand.
        val narrowestCardContentWidthDp = 113f
```

durch:

```kotlin
    @Test
    fun emojiRowFitsTheNarrowestSupportedCardForEveryValidatorPermittedCount() {
        // 320dp Gerät − 2 × 20dp AbcDimens.screenHorizontal = 280dp für die Reihe,
        // − 8dp Kartenabstand, / 2 Karten = 136dp Karte, − 2 × 4dp Karteninnen-
        // abstand = 128dp Inhaltsbreite. Der Validator erlaubt 1..3 Atome je Karte;
        // ein Test, der nur 2 prüft, würde die 3-Atom-Karten übersehen — genau die,
        // bei denen vorher das letzte Emoji verschwand.
        val narrowestCardContentWidthDp = 128f
```

Und am Ende der Klasse **anhängen**:

```kotlin
    @Test
    fun baseScaleRaisesTheBaseButNotAboveTheWidthCap() {
        // Die Erfolgsanimation zieht die Karte fast bühnenbreit; dort bindet
        // weiter die Basisgröße, nicht der Deckel — ohne baseScale würde das
        // Emoji also gar nicht wachsen.
        val wide = 400f
        assertEquals(76f * 1.6f, SentencePictureCardSizing.emojiSp(2, wide, 1f, 1.6f), 1f)

        // Auf einer schmalen Karte bleibt der Deckel die Obergrenze: baseScale
        // darf ihn nicht aushebeln, sonst kehrt der Überlauf-Bug zurück.
        val narrow = 128f
        val capped = SentencePictureCardSizing.emojiSp(3, narrow, 1f, 1.6f)
        val renderedDp = 3 * capped * SentencePictureCardSizing.EmojiAdvanceEm
        assertTrue("row renders ${renderedDp}dp into a ${narrow}dp card", renderedDp <= narrow)
    }

    @Test
    fun baseScaleOfOneMatchesTheCallWithoutIt() {
        // Der Normalpfad darf sich durch den neuen Parameter nicht verschieben.
        listOf(1, 2, 3).forEach { count ->
            listOf(128f, 173f, 400f).forEach { width ->
                assertEquals(
                    SentencePictureCardSizing.emojiSp(count, width, 1f),
                    SentencePictureCardSizing.emojiSp(count, width, 1f, 1f),
                    0f,
                )
            }
        }
    }
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.SentencePictureSidesTest"
```

Erwartet: Kompilierfehler (vierter Parameter existiert nicht) bzw. FAIL mit „expected:110.0 but was:72.0".

- [ ] **Step 3: `emojiSp` erweitern**

In `SentencePictureCardSizing` die Funktion `emojiSp` **ersetzen**:

```kotlin
    /**
     * @param contentWidthDp Breite *innerhalb* der Karteninnenabstände.
     * @param fontScale System-Schriftskalierung ([androidx.compose.ui.unit.Density.fontScale]).
     * @param baseScale Faktor auf die Basisgröße, den die Erfolgsanimation von 1f
     *   auf 1.6f fährt. Er hebt die Basis, **nicht** den Breitendeckel: die Karte
     *   wird beim Feiern echt breiter gemessen, aber ohne diesen Faktor bliebe die
     *   Basis die bindende Grenze und das Emoji würde nicht wachsen.
     */
    fun emojiSp(
        atomCount: Int,
        contentWidthDp: Float,
        fontScale: Float,
        baseScale: Float = 1f,
    ): Float {
        val count = atomCount.coerceAtLeast(1)
        // Die 2er- und 3er-Werte liegen bewusst über dem, was ein 411dp-Telefon
        // durchlässt (dort deckelt die Breite auf ~72 bzw. ~48sp). Auf breiteren
        // Geräten darf die Karte den Gewinn mitnehmen; der Deckel unten ist die
        // Instanz, die Überlauf verhindert, nicht diese Staffelung.
        val base = when {
            count <= 1 -> 110f
            count == 2 -> 76f
            else -> 56f
        } * baseScale.coerceAtLeast(0f)
        val widthCap = contentWidthDp / (count * EmojiAdvanceEm)
        // Emojis sind Bilder, keine Prosa — dieselbe Begründung wie
        // FinaleLayout.capEffectiveSize: sie geben ihre fontScale-Vergrößerung als
        // Erstes wieder ab, damit die *gerenderte* Reihe (sp × fontScale) nie breiter
        // wird als das dp-Budget der Karte.
        val scaled = minOf(base, widthCap) / maxOf(fontScale, 1f)
        // Abrunden statt runden: die Näherung darf nie über das Budget rutschen,
        // dieselbe Konvention wie die Ganzzahl-Kürzung in FinaleLayout.
        return floor(scaled).coerceAtLeast(MinEmojiSp)
    }
```

Außerdem im KDoc-Block über `object SentencePictureCardSizing` den Satz „vorher setzte die Karte ihre Emoji-Reihe fest in 72/56/44sp" auf „110/76/56sp" ziehen, damit der Kommentar nicht auf Zahlen zeigt, die es nicht mehr gibt.

- [ ] **Step 4: Tests laufen lassen, grün bestätigen**

```bash
./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.SentencePictureSidesTest"
```

Erwartet: PASS. Insbesondere müssen `emojiShrinksWithMoreAtoms`, `emojiSizeStaysPositiveForDegenerateInput` und `emojiSizeNeverGrowsWithFontScale` unverändert grün bleiben.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureSides.kt app/src/test/java/app/abcvorschule/ui/exercise/SentencePictureSidesTest.kt
git commit -m "feat(satz-versteher): größere emoji-basiswerte und ein baseScale für die feier"
```

---

### Task 3: `ExerciseStage.answerAnchor`

Ein opt-in Layout-Modus. Nach dieser Task ist er noch von niemandem benutzt — der Compiler und `assembleDebug` sind die Zusage, dass die zehn bestehenden Aufrufer unberührt sind.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/ExerciseStage.kt` (ganze Datei)

**Interfaces:**
- Consumes: nichts.
- Produces: `enum class AnswerAnchor { Bottom, BelowCenter }` · `ExerciseStage(modifier, answerAnchor = AnswerAnchor.Bottom, prompt, answers)` · `ExerciseStage.PromptHeightFraction` ist **privat**, keine Zusage nach außen.

- [ ] **Step 1: Datei ersetzen**

`app/src/main/java/app/abcvorschule/ui/exercise/ExerciseStage.kt` vollständig durch Folgendes ersetzen:

```kotlin
package app.abcvorschule.ui.exercise

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.AbcDimens

/** Wo der Antwortblock einer Übung sitzt. */
enum class AnswerAnchor {
    /** Am unteren Rand, mit Luft darunter — die Grundform (PRODUCT_PRINCIPLES §9). */
    Bottom,

    /**
     * Oberkante des Antwortblocks knapp unter der Bildschirmmitte. Für Übungen,
     * deren Aufgabenblock fast leer ist: der Satz-Versteher trägt dort nur den
     * Speaker (kein Titel, keine Kacheln, kein Wort), und am unteren Rand
     * verdeckt die tippende Hand dann die Bildkarten, die den ganzen Inhalt der
     * Aufgabe ausmachen.
     */
    BelowCenter,
}

/**
 * Anteil der Bühnenhöhe, den der Aufgabenblock im [AnswerAnchor.BelowCenter]-Modus
 * bekommt. Eine feste Bruchhöhe und *kein* zweites `weight`: Gewichte teilen den
 * Restraum nach Abzug der Antworten auf, und die Kartenhöhe des Satz-Verstehers
 * wächst mit der Emoji-Größe — die Oberkante würde also mit jeder Größenänderung
 * wandern, bei hohen Karten sogar über die Mitte hinaus, also in die
 * Gegenrichtung. 0.52 ist von der Antworthöhe unabhängig und hält die Zusage
 * „knapp unterhalb der Mitte" wörtlich.
 */
private const val PromptHeightFraction = 0.52f

/**
 * Prompt/task block in the upper area; answers anchored near the bottom with breathing room.
 * Content is width-capped so nothing hugs the screen edges.
 */
@Composable
fun ExerciseStage(
    modifier: Modifier = Modifier,
    answerAnchor: AnswerAnchor = AnswerAnchor.Bottom,
    prompt: @Composable ColumnScope.() -> Unit,
    answers: @Composable ColumnScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val promptHeight = maxHeight * PromptHeightFraction
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = when (answerAnchor) {
                    AnswerAnchor.Bottom -> Modifier.weight(1f)
                    AnswerAnchor.BelowCenter -> Modifier.height(promptHeight)
                }.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 420.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(AbcDimens.blockGap),
                    content = prompt,
                )
            }
            Column(
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
                content = answers,
            )
        }
    }
}
```

Zwei Dinge, die dabei bewusst so bleiben: `Bottom` benutzt weiter `weight(1f)` (identisches Verhalten zu heute), und der Antwortblock behält in **beiden** Modi seine 8dp Bodenluft und die 420dp-Breitenkappe.

- [ ] **Step 2: Build und Tests laufen lassen**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Erwartet: BUILD SUCCESSFUL. Es gibt keinen neuen Test — die Zusage „`Bottom` verhält sich wie heute" trägt das Default-Argument, und dass kein Aufrufer angefasst wurde, zeigt `git diff --stat`.

- [ ] **Step 3: Prüfen, dass wirklich kein Aufrufer angefasst wurde**

```bash
git diff --stat
```

Erwartet: genau eine geänderte Datei, `ExerciseStage.kt`.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/ExerciseStage.kt
git commit -m "feat(exercise-stage): answerAnchor für antwortblöcke knapp unter der mitte"
```

---

### Task 4: Karte ohne Fläche, Karten weiter oben, mehr Breitenbudget

Die drei rein optischen Punkte in einem Schritt — sie hängen an denselben Zeilen und wären getrennt nicht sinnvoll prüfbar.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureTrainer.kt`

**Interfaces:**
- Consumes: `AnswerAnchor.BelowCenter` (Task 3) · `SentencePictureCardSizing.emojiSp(...)` (Task 2)
- Produces: `PictureCard` ohne `background`-Modifier; `CardPaddingHorizontalDp = 4f`; Kartenabstand 8dp; `CardBorderGreen` gelöscht.

- [ ] **Step 1: `ExerciseStage`-Aufruf auf `BelowCenter` umstellen**

In `SentencePictureTrainer`:

```kotlin
    ExerciseStage(
        modifier = modifier,
        prompt = {
```

wird zu:

```kotlin
    ExerciseStage(
        modifier = modifier,
        // Der Aufgabenblock trägt hier nur den Speaker — am unteren Rand
        // verdeckt die tippende Hand sonst genau die Bildkarten, die die
        // ganze Aufgabe sind.
        answerAnchor = AnswerAnchor.BelowCenter,
        prompt = {
```

- [ ] **Step 2: Kartenabstand von 14dp auf 8dp**

```kotlin
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
```

wird zu:

```kotlin
            Row(
                // 8dp statt 14dp: die Lücke ist reines Breitenbudget, das den
                // Emojis fehlt. Zwei Karten mit deutlichem Rahmen brauchen
                // keinen breiten Graben, um auseinandergehalten zu werden.
                horizontalArrangement = Arrangement.spacedBy(CardGapDp.dp),
```

- [ ] **Step 3: Konstanten anpassen, `CardBorderGreen` löschen**

Den Block

```kotlin
/**
 * Bestätigungs-Grün der gewählten Karte — dieselbe dunklere LeafGreen-Variante
 * wie SentenceOrderTrainer.PegBorderGreen (voll-opakes LeafGreen erreicht auf
 * CreamElevated nur 2.87:1).
 */
private val CardBorderGreen = Color(0xFF3A7A44)

/** Innenabstand der Karte je Seite; zugleich der Abzug für die Emoji-Breitenrechnung. */
private const val CardPaddingHorizontalDp = 10f
```

**ersetzen** durch:

```kotlin
/**
 * Innenabstand der Karte je Seite; zugleich der Abzug für die Emoji-
 * Breitenrechnung. 4dp statt vormals 10dp: ohne Füllfläche muss der Rahmen keine
 * Fläche mehr einfassen, und jedes eingesparte dp landet direkt im Breitendeckel
 * der Emoji-Reihe — bei drei Emojis ist die Breite die bindende Grenze.
 */
private const val CardPaddingHorizontalDp = 4f

/** Abstand der beiden Karten in der Reihe, ebenfalls Breitenbudget der Emojis. */
private const val CardGapDp = 8f
```

Den Import `androidx.compose.ui.graphics.Color` entfernen, wenn er danach unbenutzt ist (der Compiler warnt; `assembleDebug` ist die Kontrolle).

- [ ] **Step 4: Fläche entfernen, Rollenfarbe benutzen**

In `PictureCard` den Modifier-Block

```kotlin
                .alpha(opacity)
                .background(color = CreamElevated, shape = RoundedCornerShape(22.dp))
                .border(
                    width = if (highlight) 4.dp else 3.dp,
                    color = if (highlight) CardBorderGreen else WarmMuted.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(22.dp),
                )
```

**ersetzen** durch:

```kotlin
                .alpha(opacity)
                // Keine Füllfläche: CreamElevated auf Cream ist nur 1.22:1 — als
                // Kartengrenze kaum sichtbar, aber genug, um die Emojis
                // abzudunkeln. Die Grenze wandert auf den Rahmen, wo sie mit
                // 4.45:1 (WarmMuted auf Cream) tatsächlich zu sehen ist, und die
                // Bilder stehen auf der hellsten Fläche der Übung.
                //
                // Damit fällt auch die Sonderfarbe weg, die es nur wegen der
                // Füllung gab: LeafGreen erreichte auf CreamElevated bloß 2.87:1,
                // auf Cream sind es 3.5:1 — die Rollenfarbe „richtig" aus §10
                // gilt hier wieder direkt.
                .border(
                    width = if (highlight) 4.dp else 3.dp,
                    color = if (highlight) LeafGreen else WarmMuted.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(22.dp),
                )
```

Importe entsprechend anpassen: `androidx.compose.foundation.background` und `app.abcvorschule.ui.theme.CreamElevated` fallen weg, `app.abcvorschule.ui.theme.LeafGreen` kommt dazu.

- [ ] **Step 5: Build und Tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Erwartet: BUILD SUCCESSFUL, keine Warnung über unbenutzte Importe in `SentencePictureTrainer.kt`.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureTrainer.kt
git commit -m "feat(satz-versteher): karten unter die mitte, rahmen statt grauer fläche"
```

---

### Task 5: Fehltipp schüttelt die getippte Karte

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureTrainer.kt`

**Interfaces:**
- Consumes: `SentencePictureCardShake.offsetDp/AmplitudeDp/DurationMs` (Task 1)
- Produces: `PictureCard(..., shakeTick: Int, ...)` — ein Zähler, dessen Erhöhung eine Schüttelrunde auslöst.

- [ ] **Step 1: Zustand im Trainer ergänzen**

Neben `misses` merkt sich der Trainer, welche Seite zuletzt falsch getippt wurde. Ein Zähler statt eines Bool, damit zwei Fehltipps auf dieselbe Karte auch zwei Schüttler auslösen — ein Bool wäre beim zweiten Tap schon `true` und würde nichts neu starten.

In `SentencePictureTrainer` nach

```kotlin
    var resolved by remember(roundKey) { mutableStateOf(false) }
    var solvedCorrect by remember(roundKey) { mutableStateOf(false) }
```

einfügen:

```kotlin
    // Welche Karte zuletzt falsch getippt wurde und wie oft überhaupt schon
    // falsch getippt wurde. Der Zähler ist der Auslöser der Schüttel-Animation:
    // ein Bool wäre beim zweiten Fehltipp auf dieselbe Karte schon true und
    // würde keine neue Runde starten.
    var wrongTick by remember(roundKey) { mutableIntStateOf(0) }
    var wrongOnLeft by remember(roundKey) { mutableStateOf(false) }
```

- [ ] **Step 2: `choose` erweitert**

```kotlin
        } else {
            misses += 1
            haptics.nudge()
            onResult(false, false, scoredIds)
        }
```

wird zu:

```kotlin
        } else {
            misses += 1
            wrongOnLeft = tappedLeft
            wrongTick += 1
            haptics.nudge()
            onResult(false, false, scoredIds)
        }
```

Dazu bekommt `choose` einen zweiten Parameter, weil die Funktion bisher nur weiß *ob* richtig, nicht *wo* getippt wurde:

```kotlin
    fun choose(correct: Boolean) {
```

wird zu:

```kotlin
    fun choose(correct: Boolean, tappedLeft: Boolean) {
```

und die beiden Aufrufstellen in den `PictureCard`-Aufrufen:

```kotlin
                    onTap = { choose(leftIsCorrect) },
```
→
```kotlin
                    onTap = { choose(leftIsCorrect, tappedLeft = true) },
```

```kotlin
                    onTap = { choose(!leftIsCorrect) },
```
→
```kotlin
                    onTap = { choose(!leftIsCorrect, tappedLeft = false) },
```

- [ ] **Step 3: `shakeTick` an die Karten durchgeben**

Beim linken `PictureCard`-Aufruf ergänzen:

```kotlin
                    shakeTick = if (wrongOnLeft) wrongTick else 0,
```

Beim rechten:

```kotlin
                    shakeTick = if (!wrongOnLeft) wrongTick else 0,
```

- [ ] **Step 4: `PictureCard` schüttelt**

Signatur um `shakeTick: Int` erweitern (nach `opacity`), und im Rumpf vor dem `BoxWithConstraints` die Animation anlegen:

```kotlin
    // Ein Animatable statt animateFloatAsState: die Schüttelrunde muss bei jedem
    // neuen Tick von vorn beginnen, auch wenn die vorige noch läuft.
    val shake = remember { Animatable(0f) }
    LaunchedEffect(shakeTick) {
        if (shakeTick == 0) return@LaunchedEffect
        shake.snapTo(0f)
        shake.animateTo(1f, tween(durationMillis = SentencePictureCardShake.DurationMs))
        shake.snapTo(0f)
    }
```

Und am inneren `Box`-Modifier, direkt **vor** `.alpha(opacity)`:

```kotlin
                // graphicsLayer statt offset: eine reine Zeichenoperation, die
                // kein Neu-Layout der Reihe auslöst und die Nachbarkarte
                // deshalb nicht mitverschiebt.
                .graphicsLayer {
                    translationX = SentencePictureCardShake.offsetDp(shake.value).dp.toPx()
                }
```

Neue Importe: `androidx.compose.animation.core.Animatable`, `androidx.compose.runtime.LaunchedEffect`, `androidx.compose.ui.graphics.graphicsLayer`.

- [ ] **Step 5: Build und Tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Erwartet: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureTrainer.kt
git commit -m "feat(satz-versteher): fehltipp wackelt die getippte karte"
```

---

### Task 6: Treffer zieht die Karte groß in die Mitte

Hier wird das heutige Einzelsignal `(solvedCorrect || resolved)` gespalten: `highlight` steuert den Rahmen (auch beim Auflösen), `celebrate` die Vergrößerung (nur bei eigenem Treffer). §8 sagt „Auflösen ist nicht grün" — und die Feier gehört dem Kind, das selbst gelöst hat.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureTrainer.kt`

**Interfaces:**
- Consumes: `SentencePictureCardSizing.emojiSp(..., baseScale)` (Task 2)
- Produces: `PictureCard(..., celebrateProgress: Float, ...)` — 0f im Normalfall und beim Auflösen, 0f→1f nur bei eigenem Treffer.

- [ ] **Step 1: Gewicht animieren, `celebrate` durchgeben**

Der Row-Block wird so umgebaut, dass beide Karten ein animiertes Gewicht bekommen. `celebrateProgress` liegt im Trainer, weil beide Karten denselben Wert brauchen:

```kotlin
            // Ein Fortschritt für beide Karten: die richtige wächst, die falsche
            // verschwindet. Bei fast null Gewicht schrumpft der Slot der
            // Verliererkarte mit; die 8dp Lücke bleibt, die Gewinnerkarte landet
            // also 4dp neben der optischen Mitte — unter der Wahrnehmungsschwelle
            // und billiger als eine zusätzlich animierte Arrangement-Lücke.
            val celebrateProgress by animateFloatAsState(
                targetValue = if (solvedCorrect) 1f else 0f,
                animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                label = "sentence_picture_celebrate",
            )
            val correctWeight = 1f + 2f * celebrateProgress
            val wrongWeight = (1f - celebrateProgress).coerceAtLeast(0.001f)
```

Drei Dinge hängen dann an `celebrateProgress`: das `weight` der Karte, ihre
`opacity` (die Verliererkarte blendet aus) und der `celebrateProgress`, den sie
selbst weitergibt. Beide Aufrufe vollständig — die Bedingung `leftIsCorrect`
entscheidet in jeder Zeile, welche der beiden Karten die richtige ist:

```kotlin
                PictureCard(
                    atomIds = if (leftIsCorrect) round.correctAtomIds else round.wrongAtomIds,
                    pack = pack,
                    highlight = (solvedCorrect || resolved) && leftIsCorrect,
                    celebrateProgress = if (leftIsCorrect) celebrateProgress else 0f,
                    enabled = !interactionLocked && !solvedCorrect && !resolved,
                    opacity = interactionOpacity *
                        if (leftIsCorrect) 1f else (1f - celebrateProgress),
                    shakeTick = if (wrongOnLeft) wrongTick else 0,
                    onTap = { choose(leftIsCorrect, tappedLeft = true) },
                    testTag = if (leftIsCorrect) "sentence_picture_card_correct" else "sentence_picture_card_wrong",
                    modifier = Modifier.weight(if (leftIsCorrect) correctWeight else wrongWeight),
                )
                PictureCard(
                    atomIds = if (leftIsCorrect) round.wrongAtomIds else round.correctAtomIds,
                    pack = pack,
                    highlight = (solvedCorrect || resolved) && !leftIsCorrect,
                    celebrateProgress = if (leftIsCorrect) 0f else celebrateProgress,
                    enabled = !interactionLocked && !solvedCorrect && !resolved,
                    opacity = interactionOpacity *
                        if (leftIsCorrect) (1f - celebrateProgress) else 1f,
                    shakeTick = if (!wrongOnLeft) wrongTick else 0,
                    onTap = { choose(!leftIsCorrect, tappedLeft = false) },
                    testTag = if (leftIsCorrect) "sentence_picture_card_wrong" else "sentence_picture_card_correct",
                    modifier = Modifier.weight(if (leftIsCorrect) wrongWeight else correctWeight),
                )
```

Beachten: `highlight` bleibt `(solvedCorrect || resolved)` — der grüne Rahmen erscheint auch beim Auflösen. `celebrateProgress` hängt allein an `solvedCorrect` und ist beim Auflösen 0f. Das ist die Spaltung, um die es in dieser Task geht.

Neue Importe: `androidx.compose.animation.core.FastOutSlowInEasing`.

- [ ] **Step 2: `PictureCard` nimmt `celebrateProgress` und skaliert die Emojis**

Signatur um `celebrateProgress: Float` erweitern. In der Größenrechnung:

```kotlin
        val emojiSp = SentencePictureCardSizing.emojiSp(atomIds.size, contentWidthDp, fontScale)
```

wird zu:

```kotlin
        // baseScale statt graphicsLayer-Skalierung: eine hochgezogene Bitmap wäre
        // bei einem Glyphen, der die halbe Bühne füllt und dort mehrere hundert
        // Millisekunden steht, sichtbar weich. Über das Row-Gewicht wird die Karte
        // echt breiter gemessen, und dieselbe Funktion rechnet die Emoji-Größe für
        // die neue Breite — der Glyph wird in Endgröße gerastert.
        //
        // Die Verbreiterung allein reicht dafür nicht: auf der breiten Karte
        // bindet weiter die Basisgröße, nicht der Deckel. Erst baseScale hebt sie.
        val emojiSp = SentencePictureCardSizing.emojiSp(
            atomCount = atomIds.size,
            contentWidthDp = contentWidthDp,
            fontScale = fontScale,
            baseScale = 1f + CelebrateBaseScaleGain * celebrateProgress,
        )
```

Dazu die Konstante zu den anderen privaten Konstanten der Datei:

```kotlin
/** Zuwachs der Emoji-Basisgröße, wenn die richtige Karte in die Mitte wächst. */
private const val CelebrateBaseScaleGain = 0.6f
```

- [ ] **Step 3: Build und Tests**

```bash
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Erwartet: BUILD SUCCESSFUL.

- [ ] **Step 4: Manuelle Sichtprüfung auf dem Gerät/Emulator**

Eine Lektion bis zum Satz-Versteher spielen und prüfen:
1. Kartenoberkante sitzt knapp unter der Bildschirmmitte.
2. Emojis sind deutlich größer als vorher, kein grauer Kartengrund.
3. Falsche Karte antippen → **diese** Karte wackelt, kein Rot, Satz wird erneut vorgelesen.
4. Richtige Karte antippen → grüner Rahmen sofort, Karte wächst in die Mitte, die andere verschwindet; der Zustand hält, während der Satz wiederholt wird, und noch über den Sternflug.
5. „Zeig mir" nach zwei Misses → grüner Rahmen an der richtigen Karte, **keine** Vergrößerung.
6. Mit System-Schriftgröße auf 1.3 wiederholen: keine 3-Emoji-Reihe läuft über, kein Emoji fehlt.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SentencePictureTrainer.kt
git commit -m "feat(satz-versteher): treffer zieht die karte groß in die mitte"
```

---

### Task 7: Doku

**Files:**
- Modify: `docs/PRODUCT_PRINCIPLES.md` (§6 Satz-Versteher, §9 Layout-Grundform, §10 Design-System)

**Interfaces:**
- Consumes: den Ist-Stand nach Task 6.
- Produces: nichts im Code.

- [ ] **Step 1: §6 — Kartenoptik und Feedback ergänzen**

In der Aufzählung „6. **Satz-Versteher**" nach dem Punkt über die Zeitform einen neuen Unterpunkt anhängen:

```markdown
  - **Kartenoptik und Feedback.** Die Bildkarten sind Rahmen ohne Füllfläche (der
    graue `CreamElevated`-Grund verdunkelte die Emojis, ohne die Kartengrenze
    sichtbarer zu machen), stehen mit ihrer Oberkante knapp unterhalb der
    Bildschirmmitte, und die Emoji-Reihe füllt die Karte so weit die Breite es
    zulässt. Ein Fehltipp wird **bewegt** quittiert, nicht gefärbt: die getippte
    Karte wackelt (`SentencePictureCardShake`), dazu `nudge`-Haptik und der Satz
    erneut — kein Rot, §8 und §10 gelten unverändert. Ein Treffer zieht die
    richtige Karte groß in die Bildschirmmitte und hält sie dort, solange der Satz
    wiederholt wird; die andere Karte blendet aus. **Auflösen („Zeig mir")
    markiert nur, es feiert nicht.**
```

- [ ] **Step 2: §9 — die Layout-Ausnahme benennen**

Nach dem Punkt „Ausnahme Wort-Detektiv: …" anhängen:

```markdown
- Ausnahme Satz-Versteher: der Antwortblock beginnt bei **52 % der Bühnenhöhe**
  statt am unteren Rand (`ExerciseStage(answerAnchor = AnswerAnchor.BelowCenter)`).
  Sein Aufgabenblock trägt nur den Speaker — kein Titel, keine Kacheln, kein Wort —
  und am unteren Rand verdeckt die tippende Hand genau die Bildkarten, die die
  ganze Aufgabe sind. Für alle anderen Übungen bleibt `AnswerAnchor.Bottom` die
  Vorbelegung und damit die Grundform.
```

- [ ] **Step 3: §10 — `ExerciseStage`-Zeile präzisieren**

```markdown
- Übungen nutzen `ExerciseStage` für klare Trennung Aufgabenblock / Antwortblock.
```

wird zu:

```markdown
- Übungen nutzen `ExerciseStage` für klare Trennung Aufgabenblock / Antwortblock.
  Der Parameter `answerAnchor` ist mit `Bottom` vorbelegt; `BelowCenter` ist die
  eine benannte Ausnahme (§9, Satz-Versteher).
```

- [ ] **Step 4: Prüfen, dass §8 und §10 zum Rot **nicht** angefasst wurden**

```bash
grep -n "nicht.*rot markiert" docs/PRODUCT_PRINCIPLES.md
grep -n "ClayRed" docs/PRODUCT_PRINCIPLES.md
```

Erwartet: beide Fundstellen unverändert. Die Session-Entscheidung war „kein Rot" — die Regeln bleiben also, wie sie sind, und dieser Schritt ist der Beweis, dass niemand sie im Vorbeigehen aufgeweicht hat.

- [ ] **Step 5: Commit**

```bash
git add docs/PRODUCT_PRINCIPLES.md
git commit -m "docs: kartenoptik und feedback des satz-verstehers, ExerciseStage.answerAnchor"
```

---

## Verifikation am Ende

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Beides grün, und die manuelle Sichtprüfung aus Task 6 Step 4 einmal vollständig durchlaufen — sie ist der einzige Weg, das Layout zu prüfen: das Repo hat keine androidTests und keine Screenshot-Tests.
