# Offene Issues (README) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the five open issues listed at the end of `README.md` — number-pad reset, drag & drop background animation, letter-trace star density and glyph shapes, Buchstaben-Jagd auto-proceed, and responsive Wort-Bauer frames.

**Architecture:** Each issue is a self-contained fix in one trainer, except the drag & drop bug, which has a single shared root cause in `ui/exercise/drag/DragField.kt` and is therefore fixed once for all three drag trainers. Following this repo's established pattern, every fix that has decidable logic gets that logic extracted into a **pure Kotlin object** (no Compose imports) which is TDD'd under `app/src/test/...`; the `@Composable` wiring is then a thin, mechanical call site verified by the README smoke script.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), JUnit 4 (pure JVM unit tests, no Robolectric/Compose-UI-test in this project), Gradle, Python 3 for the offline glyph contact sheet.

## Global Constraints

Copied verbatim from `docs/PRODUCT_PRINCIPLES.md` and `AGENTS.md`. Every task's requirements implicitly include this section.

- **Das Kind kann (noch) nicht lesen.** UI-Steuerung muss über Bild, Icon, Layout und Audio verständlich sein. Kein neuer Erklär- oder Erfolgstext für das Kind.
- Handlungs-Buttons: **keine Emojis** — nur ASCII oder Canvas/SVG-Vektoren.
- Feedback bei Fehlern (besonders Rechnen): **vorsprechen**, nicht als Fehler-Satz anzeigen.
- Korrekte Antwort bestätigt sich **grün**; falsche Antwort wird **nicht** rot markiert; Auflösen ist nicht grün.
- Lob aus `PraisePhrases` gilt **nur für Rechnen** und **nur gesprochen**.
- Bei Erfolg: Antwort vorsprechen → Stern im oberen Drittel → erst danach nächste Aufgabe.
- Drag & Drop committet nur bei echtem Slot-Treffer (Hit-Testing); daneben losgelassene Kacheln schnappen ohne Strafe zurück.
- Tap-Alternative zum Ziehen ist Pflicht (R15) — darf durch keine Änderung brechen.
- Mindest-Touch-Target: `AbcDimens.kidTouch` = 80.dp; harte Untergrenze laut Design-Spec 56.dp.
- Dark-only UI. Farben ausschließlich aus `ui/theme/Color.kt`.
- Tests: `./gradlew :app:testDebugUnitTest` · Build: `./gradlew :app:assembleDebug`
- Doku-Pflicht (AGENTS.md Schritt 7): Ändert sich eine UX-/Content-Regel, ziehen `docs/PRODUCT_PRINCIPLES.md`, `AGENTS.md` und `README.md` mit.
- Commit-Konvention (siehe `git log`): `type(scope): kleingeschriebene beschreibung`, z. B. `fix(math): …`.

## File Structure

| Datei | Verantwortung | Task |
|-------|---------------|------|
| `app/src/main/java/app/abcvorschule/ui/exercise/NumberPad.kt` | Zahlenfeld; bekommt neuen `resetToken`-Parameter | 1 |
| `app/src/main/java/app/abcvorschule/ui/exercise/NumberPadInput.kt` | **neu** — reine Eingabe-/Reset-Logik | 1 |
| `app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt` | Ruft `NumberPad` mit dem Token auf | 1 |
| `app/src/main/java/app/abcvorschule/ui/exercise/drag/DragField.kt` | `DragCard`-Modifier-Reihenfolge + Lift | 2 |
| `app/src/main/java/app/abcvorschule/ui/exercise/TraceGeometry.kt` | Sternanzahl proportional zur Stroke-Länge | 3 |
| `tools/render_glyphs.py` | **neu** — Kontaktbogen aus `atoms.json` | 4 |
| `app/src/main/assets/content/atoms.json` | Korrigierte `strokes` | 4 |
| `app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntTrainer.kt` | Auto-Proceed statt Weiter-Button | 5 |
| `app/src/main/java/app/abcvorschule/ui/exercise/WordFrameSizing.kt` | **neu** — reine Rahmen-/Schriftgrößen-Mathematik | 6 |
| `app/src/main/java/app/abcvorschule/ui/exercise/WordBuildTrainer.kt` | Rahmen nutzen die berechnete Breite | 6 |
| `app/src/main/java/app/abcvorschule/ui/exercise/SentenceOrderTrainer.kt` | Pegs nutzen dieselbe berechnete Breite (Scope-Erweiterung, User-bestätigt) | 6 |

---

## Task 1: Zahlenpad löscht die vorherige Eingabe

**Root cause (verifiziert):** In `NumberPad.kt:52` steht `var value by remember { mutableStateOf("") }` — **ohne Key**. `NumberPad` bleibt über Runden hinweg an derselben Stelle im Composition-Baum, also überlebt der getippte Text sowohl (a) eine neue Rechenrunde als auch (b) einen Fehlversuch. Das Kind muss von Hand löschen.

**Fix:** Ein `resetToken`-Parameter, der sich bei neuer Runde **und** bei jedem Fehlversuch ändert. Bei einer *richtigen* Antwort ändert er sich nicht — die grüne Bestätigung muss die getippte Zahl stehen lassen.

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/NumberPadInput.kt`
- Create: `app/src/test/java/app/abcvorschule/ui/exercise/NumberPadInputTest.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/NumberPad.kt:46-94`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt:97`

**Interfaces:**
- Consumes: nichts aus früheren Tasks.
- Produces: `NumberPadInput.sanitize(raw: String): String`, `NumberPadInput.resetToken(roundKey: String, misses: Int): String`, `NumberPadInput.MaxDigits: Int`. `NumberPad` bekommt den neuen Pflichtparameter `resetToken: String`.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/abcvorschule/ui/exercise/NumberPadInputTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class NumberPadInputTest {
    @Test
    fun sanitizeKeepsDigitsOnly() {
        assertEquals("12", NumberPadInput.sanitize("1a2"))
        assertEquals("", NumberPadInput.sanitize("-,."))
    }

    @Test
    fun sanitizeCapsAtMaxDigits() {
        assertEquals("123", NumberPadInput.sanitize("123456"))
        assertEquals(3, NumberPadInput.MaxDigits)
    }

    @Test
    fun tokenChangesOnEveryMissSoTheFieldClears() {
        val first = NumberPadInput.resetToken("r1", 0)
        val afterMiss = NumberPadInput.resetToken("r1", 1)
        assertNotEquals(first, afterMiss)
    }

    @Test
    fun tokenChangesOnANewRound() {
        assertNotEquals(
            NumberPadInput.resetToken("r1", 0),
            NumberPadInput.resetToken("r2", 0),
        )
    }

    @Test
    fun tokenIsStableWhileNothingChanged() {
        // A correct answer leaves roundKey and misses untouched, so the green
        // confirmation keeps showing the number the child actually typed.
        assertEquals(
            NumberPadInput.resetToken("r1", 2),
            NumberPadInput.resetToken("r1", 2),
        )
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*NumberPadInputTest*'
```

Expected: FAIL — `Unresolved reference: NumberPadInput`.

- [ ] **Step 3: Write minimal implementation**

Create `app/src/main/java/app/abcvorschule/ui/exercise/NumberPadInput.kt`:

```kotlin
package app.abcvorschule.ui.exercise

/**
 * Input rules for the numeric answer field, kept Compose-free so they stay
 * unit-testable. The reset token is the whole fix for the "previous answer stays
 * in the field" bug: the field is remembered against this token, so a new round
 * and every wrong try clear it, while a correct answer deliberately does not.
 */
object NumberPadInput {
    /** Answers in this curriculum never exceed three digits. */
    const val MaxDigits = 3

    fun sanitize(raw: String): String = raw.filter(Char::isDigit).take(MaxDigits)

    fun resetToken(roundKey: String, misses: Int): String = "$roundKey#$misses"
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*NumberPadInputTest*'
```

Expected: PASS.

- [ ] **Step 5: Wire the token into `NumberPad`**

In `app/src/main/java/app/abcvorschule/ui/exercise/NumberPad.kt`, change the signature and the `value` state.

Replace lines 45-52 (from `@Composable` down to and including the `var value` line):

```kotlin
@Composable
fun NumberPad(
    onSubmit: (Int) -> Unit,
    /** Changing this clears the field — a new round, or another wrong try. */
    resetToken: String,
    modifier: Modifier = Modifier,
    /** True once the typed number turned out to be the answer — the field confirms in green. */
    solved: Boolean = false,
) {
    var value by remember(resetToken) { mutableStateOf("") }
```

Then replace the `onValueChange` lambda on line 77 so the digit rule lives in one place:

```kotlin
            onValueChange = { input -> value = NumberPadInput.sanitize(input) },
```

- [ ] **Step 6: Pass the token from `MathExercise`**

In `app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt`, replace line 97:

```kotlin
                NumberPad(
                    onSubmit = { handleGuess(it) },
                    resetToken = NumberPadInput.resetToken(roundKey, misses),
                    solved = solved != null,
                )
```

- [ ] **Step 7: Build and run the full suite**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL, all tests pass.

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/NumberPadInput.kt app/src/test/java/app/abcvorschule/ui/exercise/NumberPadInputTest.kt app/src/main/java/app/abcvorschule/ui/exercise/NumberPad.kt app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt
git commit -m "fix(math): clear the number field on a new round and after each miss"
```

---

## Task 2: Drag & Drop — Hintergrund wandert nicht mit

**Root cause (verifiziert):** In `DragField.kt:121-128` lautet die Modifier-Kette

```
<caller modifier: defaultMinSize → background → padding → testTag> → zIndex → offset → …
```

In Compose umschließt ein früherer Modifier den späteren. `background` liegt also **außerhalb** von `offset`: Der Hintergrund wird an der ursprünglichen Position gezeichnet, nur der Inhalt (der `Text`) wird verschoben. Verschärfend kommt hinzu, dass `startDrag` (`DragField.kt:65-69`) auch `selectedKey` setzt — die Kachel gilt damit als ausgewählt, ihr Text wechselt auf `NightInk` (schwarz, `WordBuildTrainer.kt:187` / `SentenceOrderTrainer.kt:216`), während die zugehörige `SoftMint`-Fläche zurückbleibt. Ergebnis: schwarzer Text auf dunklem Hintergrund, praktisch unsichtbar — exakt das gemeldete Symptom.

**Fix:** `zIndex` und `offset` **vor** den Aufrufer-Modifier ziehen, damit Hintergrund, Border und Padding mitwandern. Zusätzlich ein sanftes Anheben (Scale) während des Ziehens, damit die gezogene Kachel sichtbar über den Zielfeldern schwebt (README-Smoke-Schritt 4).

**Warum ohne Unit-Test:** Modifier-Reihenfolge ist reine Compose-Layout-Semantik. Dieses Projekt hat weder Robolectric noch `compose-ui-test-junit4`; beides für einen Fünf-Zeilen-Fix einzuführen wäre unverhältnismäßig. Die Verifikation ist deshalb Build + bestehende Suite + der manuelle Smoke-Schritt unten. Das ist eine bewusste Abweichung vom TDD-Standard dieses Plans.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/drag/DragField.kt:100-144`

**Interfaces:**
- Consumes: nichts aus früheren Tasks.
- Produces: keine Signaturänderung. `DragCard` behält `(state, key, onTap, onDropped, modifier, content)`; nur das interne Verhalten ändert sich. Alle drei Aufrufer (`WordBuildTrainer`, `SentenceOrderTrainer`, plus künftige) profitieren unverändert.

- [ ] **Step 1: Add the lift constant**

In `app/src/main/java/app/abcvorschule/ui/exercise/drag/DragField.kt`, insert directly above `@Composable fun DragCard(` (i.e. above line 108, after the KDoc block ending on line 107):

```kotlin
/** Slight enlargement while a tile is airborne, so it reads as lifted off the board. */
private const val DragLiftScale = 1.08f
```

- [ ] **Step 2: Add the required imports**

In the same file, add to the import block (keep it alphabetically sorted with the existing imports):

```kotlin
import androidx.compose.ui.graphics.graphicsLayer
```

- [ ] **Step 3: Reorder the modifier chain**

Replace the whole `Box(...)` inside `DragCard` — `DragField.kt:121-143`, from `Box(` down to the closing `)` after `content = content,` — with:

```kotlin
    Box(
        // zIndex/offset/scale sit BEFORE the caller's modifier on purpose: a later
        // `offset` would only move the content, leaving the caller's background and
        // border painted at the tile's resting position — which made the dragged
        // tile look like bare (near-black) text floating over the board.
        modifier = Modifier
            .zIndex(if (dragging) 1f else 0f)
            .offset {
                val o = if (dragging) state.dragOffset else Offset.Zero
                IntOffset(o.x.roundToInt(), o.y.roundToInt())
            }
            .graphicsLayer {
                val scale = if (dragging) DragLiftScale else 1f
                scaleX = scale
                scaleY = scale
            }
            .then(modifier)
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
```

- [ ] **Step 4: Build and run the full suite**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. `DragFieldStateTest` and `DragHitTestTest` still pass — hit-testing is unchanged because `onGloballyPositioned` still sits after `offset`, so `boundsInRoot()` keeps reporting the dragged position.

- [ ] **Step 5: Manual verification (README smoke step 4)**

```bash
./gradlew :app:installDebug
```

Play Lektion 1 to the Wort-Bauer and the Satz-Architekt. For each, confirm:
1. Dragging a tile moves its **mint background and border along with the text** — no bare text.
2. The dragged tile is drawn **above** the target frames, never beneath them.
3. The tap alternative still works: tap a tile, then tap a frame.
4. Releasing a tile away from any frame snaps it back without a penalty.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/drag/DragField.kt
git commit -m "fix(drag): move the tile background with the dragged tile"
```

---

## Task 3: Buchstaben nachspuren — Sternabstand folgt der Strichlänge

**Root cause (verifiziert):** `TraceProgress.StarsPerStroke = 4` (`TraceGeometry.kt:110`) vergibt **jedem** Strich genau vier Sterne, unabhängig von seiner Länge. Der Sterne-Fangradius ist `StarHitFraction = 0.12` der Glyphenbox (`TraceGeometry.kt:116`).

Gegen die echten Daten in `atoms.json` gerechnet:
- Die Umlautpunkte von `letter-ae`, `letter-oe`, `letter-ue` sind Striche der Länge `hypot(0.06, 0.06) ≈ 0.085` Box. Vier Sterne auf 0.085 Box liegen ~0.021 auseinander — bei einem Fangradius von 0.12 überlappen alle vier vollständig. Sie sind einzeln nicht ansteuerbar und werden in aufeinanderfolgenden Pointer-Samples sofort eingesammelt.
- `letter-o` ist ein geschlossener Kreis der Länge ≈ 2.2 Box. Vier Sterne liegen dort ~0.55 Box auseinander — riesige Lücken ohne Zwischenziel.

**Fix:** Sternanzahl proportional zur Strichlänge, mit garantiertem Mindestabstand > Fangradius. Damit werden Umlautpunkte zu einem Stern und der O-Kreis bekommt genug.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/TraceGeometry.kt:109-117`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/LetterTraceTrainer.kt:233`
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/TraceProgressTest.kt` (anfügen)
- Test: `app/src/test/java/app/abcvorschule/content/GlyphStarSpacingTest.kt` (neu)

**Interfaces:**
- Consumes: `TraceGeometry.polylineLength(points: List<TracePoint>): Float` und `TraceGeometry.starPositions(points: List<TracePoint>, count: Int): List<TracePoint>` (beide existieren bereits).
- Produces: `TraceProgress.starCountFor(strokeLength: Float, boxSize: Float): Int`, `TraceProgress.MinStars: Int`, `TraceProgress.MaxStars: Int`, `TraceProgress.StarSpacingFraction: Float`. `TraceProgress.StarsPerStroke` **entfällt** — jede Referenz darauf muss ersetzt werden (heute genau eine: `LetterTraceTrainer.kt:233`).

- [ ] **Step 1: Write the failing unit test for the new rule**

Append to `app/src/test/java/app/abcvorschule/ui/exercise/TraceProgressTest.kt`, inside the existing class body:

```kotlin
    @Test
    fun aVeryShortStrokeGetsASingleStar() {
        // The umlaut ticks of Ä/Ö/Ü are ~0.085 of the glyph box. Four stars there
        // would all sit inside one another's pick-up radius.
        val stars = TraceProgress.starCountFor(strokeLength = 0.085f * 200f, boxSize = 200f)
        assertEquals(1, stars)
    }

    @Test
    fun aLongClosedStrokeGetsManyStars() {
        // letter-o is a closed loop roughly 2.2 box lengths around.
        val stars = TraceProgress.starCountFor(strokeLength = 2.2f * 200f, boxSize = 200f)
        assertTrue("expected more than the old fixed 4, was $stars", stars > 4)
        assertTrue("must stay bounded, was $stars", stars <= TraceProgress.MaxStars)
    }

    @Test
    fun starCountNeverDropsBelowOneEvenForADegenerateStroke() {
        assertEquals(TraceProgress.MinStars, TraceProgress.starCountFor(0f, 200f))
    }

    @Test
    fun consecutiveStarsStayFurtherApartThanThePickUpRadius() {
        val stroke = listOf(TracePoint(0f, 0f), TracePoint(200f, 0f))
        val boxSize = 200f
        val length = TraceGeometry.polylineLength(stroke)
        val stars = TraceGeometry.starPositions(stroke, TraceProgress.starCountFor(length, boxSize))
        val radius = boxSize * TraceProgress.StarHitFraction
        stars.zipWithNext().forEach { (a, b) ->
            val gap = hypot(b.x - a.x, b.y - a.y)
            assertTrue("stars $a and $b are only $gap apart (radius $radius)", gap > radius)
        }
    }
```

Ensure the file's imports include `org.junit.Assert.assertTrue` and `kotlin.math.hypot`; add whichever is missing.

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*TraceProgressTest*'
```

Expected: FAIL — `Unresolved reference: starCountFor`.

- [ ] **Step 3: Write the implementation**

In `app/src/main/java/app/abcvorschule/ui/exercise/TraceGeometry.kt`, replace lines 109-117 (the `object TraceProgress {` header through the `StarHitFraction` constant, keeping `fun update` untouched):

```kotlin
object TraceProgress {
    /** Even the shortest tick (an umlaut dot) is worth exactly one star. */
    const val MinStars = 1

    /** Upper bound so a long closed loop stays a game, not a chore. */
    const val MaxStars = 10

    /**
     * Nominal gap between two stars, as a fraction of the glyph box. Deliberately
     * larger than [StarHitFraction]: two stars closer than the pick-up radius sit
     * inside one another and cannot be aimed at separately, which is what made the
     * umlaut ticks of Ä/Ö/Ü collect all four of their stars in a single swipe.
     */
    const val StarSpacingFraction = 0.28f

    /** Corridor half-width as a fraction of the glyph box. */
    const val CorridorFraction = 0.16f

    /** Star pick-up radius as a fraction of the glyph box. */
    const val StarHitFraction = 0.12f

    /** Stars scale with how much road there actually is to drive. */
    fun starCountFor(strokeLength: Float, boxSize: Float): Int {
        if (boxSize <= 0f) return MinStars
        val spacing = boxSize * StarSpacingFraction
        if (spacing <= 0f) return MinStars
        return (strokeLength / spacing).toInt().coerceIn(MinStars, MaxStars)
    }
```

- [ ] **Step 4: Update the one call site**

In `app/src/main/java/app/abcvorschule/ui/exercise/LetterTraceTrainer.kt`, replace line 233:

```kotlin
    val stars = strokes.map {
        TraceGeometry.starPositions(it, TraceProgress.starCountFor(TraceGeometry.polylineLength(it), boxSize))
    }
```

- [ ] **Step 5: Run tests to verify they pass**

```bash
./gradlew :app:testDebugUnitTest --tests '*TraceProgressTest*'
```

Expected: PASS.

- [ ] **Step 6: Add a regression test against the real content pack**

Create `app/src/test/java/app/abcvorschule/content/GlyphStarSpacingTest.kt`. It loads the shipped pack the same way `ContentRepositoryTest` and `ContentValidatorTest` already do — via `ContentRepository.fromClasspath().load()`, whose `pack.atoms` is a `Map<String, Atom>`.

```kotlin
package app.abcvorschule.content

import app.abcvorschule.ui.exercise.TraceGeometry
import app.abcvorschule.ui.exercise.TracePoint
import app.abcvorschule.ui.exercise.TraceProgress
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

/**
 * Every authored glyph must be traceable: no two consecutive stars may sit inside
 * one another's pick-up radius, or the child cannot aim at them one by one.
 */
class GlyphStarSpacingTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun everyAuthoredStrokeKeepsItsStarsApart() {
        val boxSize = 260f // GlyphBox in LetterTraceTrainer
        val radius = boxSize * TraceProgress.StarHitFraction
        val offenders = mutableListOf<String>()

        pack.atoms.values.filter { it.strokes.isNotEmpty() }.forEach { atom ->
            TraceGeometry.toPixels(atom.strokes, boxSize, TracePoint(0f, 0f))
                .forEachIndexed { index, stroke ->
                    val count = TraceProgress.starCountFor(
                        TraceGeometry.polylineLength(stroke),
                        boxSize,
                    )
                    TraceGeometry.starPositions(stroke, count)
                        .zipWithNext()
                        .forEach { (a, b) ->
                            val gap = hypot(b.x - a.x, b.y - a.y)
                            if (gap <= radius) {
                                offenders += "${atom.id} stroke $index: gap $gap <= radius $radius"
                            }
                        }
                }
        }

        assertTrue(offenders.joinToString("\n"), offenders.isEmpty())
    }
}
```

- [ ] **Step 7: Run the new pack test**

```bash
./gradlew :app:testDebugUnitTest --tests '*GlyphStarSpacingTest*'
```

Expected: PASS.

- [ ] **Step 8: Full suite and build**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. `TraceGeometryTest` is unaffected — `starPositions` itself did not change.

- [ ] **Step 9: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/TraceGeometry.kt app/src/main/java/app/abcvorschule/ui/exercise/LetterTraceTrainer.kt app/src/test/java/app/abcvorschule/ui/exercise/TraceProgressTest.kt app/src/test/java/app/abcvorschule/content/GlyphStarSpacingTest.kt
git commit -m "fix(trace): scale star count with stroke length instead of a fixed four"
```

---

## Task 4: Buchstaben nachspuren — Glyphenformen prüfen und korrigieren

Der Issue-Text („Die Pfade sind nicht immer korrekt") nennt keine konkreten Buchstaben. Dieser Task macht das Problem erst **sichtbar und entscheidbar**, statt zu raten: ein Kontaktbogen rendert alle 39 authored Glyphen aus `atoms.json`, danach werden nur die tatsächlich falschen korrigiert.

Der Verdacht aus der Datenprüfung: Mehr-Graphem-Atome quetschen zwei bis drei Buchstaben in dieselbe Einheitsbox und verzerren sie dabei stark. Beispiel `letter-sch` — das S belegt nur x 0.056…0.218 (0.16 breit) bei 0.84 Höhe, ein Seitenverhältnis von etwa 1:5. Das ist zu bestätigen oder zu widerlegen, nicht vorab zu fixen.

**Files:**
- Create: `tools/render_glyphs.py`
- Modify: `app/src/main/assets/content/atoms.json` (nur die im Kontaktbogen als falsch bestätigten Atome)

**Interfaces:**
- Consumes: `TraceProgress.starCountFor` aus Task 3 (der Kontaktbogen markiert Sternpositionen mit derselben Regel).
- Produces: keine Kotlin-API. `tools/render_glyphs.py` schreibt `build/glyphs.png`.

- [ ] **Step 1: Write the contact-sheet renderer**

Create `tools/render_glyphs.py`:

```python
#!/usr/bin/env python3
"""Render every authored glyph in atoms.json to one PNG contact sheet.

Run from the repo root:  python3 tools/render_glyphs.py
Output:                  build/glyphs.png
"""
import json
import math
import os

from PIL import Image, ImageDraw

CELL = 180
PAD = 14
COLS = 6
STAR_SPACING_FRACTION = 0.28  # TraceProgress.StarSpacingFraction
MIN_STARS, MAX_STARS = 1, 10


def polyline_length(points):
    return sum(
        math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(points, points[1:])
    )


def star_count(length, box):
    spacing = box * STAR_SPACING_FRACTION
    if spacing <= 0:
        return MIN_STARS
    return max(MIN_STARS, min(MAX_STARS, int(length / spacing)))


def point_at(points, fraction):
    total = polyline_length(points)
    if total <= 0:
        return points[0]
    target = fraction * total
    walked = 0.0
    for a, b in zip(points, points[1:]):
        seg = math.hypot(b[0] - a[0], b[1] - a[1])
        if walked + seg >= target:
            t = 0 if seg <= 0 else (target - walked) / seg
            return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        walked += seg
    return points[-1]


def main():
    # atoms.json is an AtomsFile object: {"atoms": [...]}, not a bare array.
    with open("app/src/main/assets/content/atoms.json", encoding="utf-8") as fh:
        atoms = json.load(fh)["atoms"]
    glyphs = [a for a in atoms if a.get("strokes")]
    rows = (len(glyphs) + COLS - 1) // COLS
    img = Image.new("RGB", (COLS * CELL, rows * CELL), (18, 20, 26))
    draw = ImageDraw.Draw(img)
    box = CELL - 2 * PAD

    for i, atom in enumerate(glyphs):
        ox = (i % COLS) * CELL + PAD
        oy = (i // COLS) * CELL + PAD
        draw.rectangle([ox, oy, ox + box, oy + box], outline=(52, 56, 66))
        draw.text((ox + 2, oy + 2), f"{atom['id']} {atom.get('display','')}", fill=(150, 155, 170))
        for stroke in atom["strokes"]:
            pts = [(ox + p[0] * box, oy + p[1] * box) for p in stroke["points"]]
            draw.line(pts, fill=(226, 220, 200), width=7, joint="curve")
            # Start of the stroke: where the child's vehicle is placed.
            draw.ellipse(
                [pts[0][0] - 5, pts[0][1] - 5, pts[0][0] + 5, pts[0][1] + 5],
                fill=(240, 120, 100),
            )
            n = star_count(polyline_length(pts), box)
            for s in range(1, n + 1):
                sx, sy = point_at(pts, s / n)
                draw.ellipse([sx - 4, sy - 4, sx + 4, sy + 4], fill=(240, 200, 90))

    os.makedirs("build", exist_ok=True)
    img.save("build/glyphs.png")
    print(f"wrote build/glyphs.png — {len(glyphs)} glyphs")


if __name__ == "__main__":
    main()
```

- [ ] **Step 2: Render the contact sheet**

```bash
python3 -m pip install --quiet Pillow && python3 tools/render_glyphs.py
```

Expected: `wrote build/glyphs.png — 39 glyphs`.

- [ ] **Step 3: Review the sheet and write down the verdict**

Open `build/glyphs.png` and check every cell against three questions:
1. **Reads as the letter?** Would a literate adult name the glyph correctly without the label?
2. **Start point sane?** Is the red dot where German handwriting starts that letter (usually top, left-to-right)?
3. **Multi-glyph atoms** (`Ch`, `Sch`, `ck`, `St`, `Sp`, `Pf`, `Ei`, `Eu`, `Au`, `Qu`): is each sub-letter still recognisable, or squeezed into a sliver?

Record the failing atom ids in the commit message. **Change nothing that passes** — the point of the sheet is to keep this fix scoped to real defects.

- [ ] **Step 4: Fix only the flagged atoms**

Edit the `strokes` of the flagged atoms in `app/src/main/assets/content/atoms.json`. Rules that the existing `ContentValidator` already enforces (`ContentValidator.kt:45-53`) and that must keep holding:
- every stroke has **≥ 2 points**;
- every point is exactly 2D;
- every coordinate stays inside `0.0…1.0`.

Additional conventions to respect, taken from the glyphs that already look right:
- Vertical stems run **top → bottom** (e.g. `letter-i`: `[0.5,0.08] → [0.5,0.92]`).
- Horizontal bars run **left → right** (e.g. `letter-t` stroke 0).
- Keep the drawn body within `0.08…0.92` so the corridor never touches the box edge.
- For multi-glyph atoms, give each sub-letter at least **0.26 box width**; drop the horizontal gap between sub-letters to `0.04` before you narrow a letter further.

- [ ] **Step 5: Re-render and compare**

```bash
python3 tools/render_glyphs.py
```

Open `build/glyphs.png` again. Every previously flagged cell must now pass all three questions from Step 3, and no previously-good cell may have regressed.

- [ ] **Step 6: Run the content and geometry tests**

```bash
./gradlew :app:testDebugUnitTest
```

Expected: PASS — in particular `ContentValidatorTest`, `ContentRepositoryTest`, `LessonCoverageTest` and the `GlyphStarSpacingTest` from Task 3.

- [ ] **Step 7: Commit**

```bash
git add tools/render_glyphs.py app/src/main/assets/content/atoms.json
git commit -m "fix(content): correct the glyph stroke paths flagged by the contact sheet"
```

---

## Task 5: Buchstaben-Jagd — Auto-Proceed statt Weiter-Button

**Heutiges Verhalten** (`SymbolHuntTrainer.kt:104, 148-153`): Batterie voll → `batteryFull = true` → das Streufeld blendet über 400 ms aus, die Batterie pulsiert golden → das Kind muss **Weiter** tippen → erst dann `onResult(true, …)`.

**Gefordert:** Weiter-Button weg, automatisch weiter, Erfolgs-Message.

**Wichtig zur „Erfolgs-Message":** Ein *Text* wäre ein Verstoß gegen Prinzip 2 („Das Kind kann (noch) nicht lesen") und gegen Prinzip 7 („Lob … nie als Text anzeigen"). Die prinzipienkonforme Entsprechung existiert bereits: `onResult(true, …)` startet die gemeinsame Erfolgs-Pipeline, also gesprochene Antwort + `SuccessBurst`-Stern (`TaskShell.kt:136`). Dieser Task lässt die Feier-Animation zu Ende laufen und übergibt dann automatisch — die Erfolgsmeldung ist damit **gesprochen + Stern**, nicht geschrieben.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntTrainer.kt:40, 104, 111-115, 148-153`
- Modify: `README.md` (Smoke-Skript, Schritt 4)
- Modify: `AGENTS.md:66` (die Regel nennt heute ausdrücklich einen „Weiter"-Button)

**Interfaces:**
- Consumes: nichts aus früheren Tasks.
- Produces: keine öffentliche API-Änderung. `SymbolHuntTrainer` behält seine Signatur.

- [ ] **Step 1: Add the celebration constant**

In `app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntTrainer.kt`, insert below the `TilePalette` declaration (after line 54):

```kotlin
/**
 * How long the full battery celebrates before the round hands off. Covers the
 * 400 ms field fade plus roughly one pulse of the golden battery, so the child
 * sees the win land instead of the screen cutting away mid-animation. Mirrors
 * LetterTraceTrainer's RewardHoldMs, which solves the same problem.
 */
private const val CelebrationHoldMs = 900L
```

- [ ] **Step 2: Auto-advance once the battery is full**

In the same file, insert immediately after the `fieldAlpha` block (after line 115, before `ExerciseStage(`):

```kotlin
    // Auto-proceed: the battery filling up IS the success signal, so a "Weiter"
    // tap only added a dead end for a child who cannot read the button. The delay
    // sits in front of onResult because reporting the result starts the spoken
    // success phase, which must not talk over the celebration.
    LaunchedEffect(batteryFull) {
        if (!batteryFull) return@LaunchedEffect
        delay(CelebrationHoldMs)
        onResult(true, false, listOf(round.targetAtomId))
    }
```

- [ ] **Step 3: Remove the Weiter button**

In the same file, delete lines 148-153 — the whole block:

```kotlin
            if (batteryFull) {
                AbcContinueButton(
                    onClick = { onResult(true, false, listOf(round.targetAtomId)) },
                    centered = true,
                )
            }
```

- [ ] **Step 4: Fix the imports**

In the same file:
- **Remove** `import app.abcvorschule.ui.components.AbcContinueButton` (line 40) — now unused.
- **Add** `import androidx.compose.runtime.LaunchedEffect` and `import kotlinx.coroutines.delay`, keeping the import block sorted.

- [ ] **Step 5: Build and run the full suite**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL with no unused-import warning. `SymbolHuntProgressTest`, `SymbolHuntLayoutTest`, `SymbolHuntInsertionTest` and `SymbolHuntSpecTest` are untouched — the pure progress logic did not change.

- [ ] **Step 6: Manual verification**

```bash
./gradlew :app:installDebug
```

Play Lektion 1 to the Buchstaben-Jagd (it follows the Spurensucher). Confirm:
1. The last correct tap fills the battery, the field fades, the battery pulses gold.
2. **No Weiter button appears.**
3. After roughly a second the round advances on its own into the spoken success + star.
4. The **Auflösen** path still works: tap six wrong tiles in a row, use Auflösen, and confirm it does *not* auto-advance as a success.

- [ ] **Step 7: Update the docs (AGENTS.md rule + README smoke script)**

Note: `AGENTS.md` and `README.md` were restructured in commit `c21f9f3` (after this plan
was drafted) — AGENTS.md's trainer rules are now a short summary that links to
`docs/PRODUCT_PRINCIPLES.md` for detail, and no longer mentions a "Weiter"-button at all.
`docs/PRODUCT_PRINCIPLES.md` never described a Weiter-button either — its Buchstaben-/Silben-Jagd
paragraph (§3, the sentence starting "Zusätzlich, bis zu zweimal pro Lektion…") only says
"Treffer füllen eine Batterie, Fehltipp mischt neu ohne Batterieverlust", which already holds
after this fix. So there is nothing to correct — only a clarifying auto-proceed clause to add
so future agents don't reintroduce a button.

In `AGENTS.md`, in the Buchstaben-/Silben-Jagd bullet, replace:

> `- **Buchstaben-/Silben-Jagd**: Optional bis zu 2× pro Lektion, keine separaten Autorierungen — wird zur Laufzeit aus letter_trace/syllable_merge abgeleitet (`SymbolHuntInsertion`).`

with:

> `- **Buchstaben-/Silben-Jagd**: Optional bis zu 2× pro Lektion, keine separaten Autorierungen — wird zur Laufzeit aus letter_trace/syllable_merge abgeleitet (`SymbolHuntInsertion`). Batterie voll → kurze Feier, dann automatisch weiter — kein „Weiter"-Button, das Kind kann ihn nicht lesen.`

In `README.md`, extend the smoke script's step 4 to cover the hunt's auto-proceed behaviour. Replace the line:

> `   Auditiver Finder (Waggon-Zuordnung) · Visueller Spurensucher (Buchstaben nachspuren) ·`
> `   optional Buchstaben-Jagd · Silben-Verschmelzer · optional Silben-Jagd ·`

with:

> `   Auditiver Finder (Waggon-Zuordnung) · Visueller Spurensucher (Buchstaben nachspuren) ·`
> `   optional Buchstaben-Jagd (Batterie voll → Feier, automatisch weiter, kein Weiter-Button) ·`
> `   Silben-Verschmelzer · optional Silben-Jagd ·`

- [ ] **Step 8: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SymbolHuntTrainer.kt AGENTS.md README.md
git commit -m "feat(symbol-hunt): auto-proceed after the battery fills instead of a weiter button"
```

---

## Task 6: Wort-Bauer & Satz-Architekt — Rahmen/Pegs skalieren mit der Länge

**Scope decision (confirmed with the human partner during pre-flight):** the README issue names only
"Word Builder", but the same fixed-width-in-a-non-wrapping-`Row` bug exists in `SentenceOrderTrainer`
(Trainer 5, Satz-Architekt) — its `Peg` uses a fixed `minWidth = 76.dp` (`SentenceOrderTrainer.kt:251`)
in a plain `Row` with no wrap. It doesn't visibly overflow with today's content (longest sentence is 4
words), but a 360 dp phone is already short by the same kind of margin this task fixes for Wort-Bauer.
The human partner asked to fix both in this task, sharing the same `WordFrameSizing` object.

**Root cause (verifiziert durch Nachrechnen):** `WordFrameMin = 84.dp` (`WordBuildTrainer.kt:46`) ist fix, die Rahmen liegen in einer `Row` mit `Arrangement.spacedBy(12.dp)` (`WordBuildTrainer.kt:130`), und `ExerciseStage` deckelt die Inhaltsbreite auf `420.dp` minus `12.dp` Padding je Seite = **396.dp nutzbar** (`ExerciseStage.kt:39-41`).

Das längste authored Wort ist **„Häuser"** mit **5** Blöcken (`['Hä','u','s','e','r']`):

```
5 × 84 + 4 × 12 = 420 + 48 = 468 dp   >   396 dp   → 72 dp Überlauf
```

Auf einem 360-dp-Telefon ist es schlimmer: abzüglich `AbcDimens.screenHorizontal` (20 dp je Seite) und der 12 dp Stage-Padding bleiben ~296 dp, dort überläuft schon **„Nest"** mit 4 Blöcken (372 dp).

Für den Satz-Architekt gilt dieselbe Rechnung mit anderen Zahlen: der längste authored Satz hat 4 Wörter
(z. B. `['das','ist','mein','Papa']`), Pegs sind `minWidth = 76.dp` in einer `Row` mit
`Arrangement.spacedBy(10.dp)` (`SentenceOrderTrainer.kt:160-161`): `4 × 76 + 3 × 10 = 334 dp`. Das passt
noch in die 396 dp Stage, überläuft aber die ~296 dp eines 360-dp-Telefons um ~38 dp — derselbe Fehler,
nur (noch) nicht durch den längsten Content vollständig ausgereizt.

**Fix:** Rahmen-/Peg-Breite und Schriftgröße aus der tatsächlich verfügbaren Breite berechnen, in beiden
Trainern über dasselbe `WordFrameSizing`.

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/exercise/WordFrameSizing.kt`
- Create: `app/src/test/java/app/abcvorschule/ui/exercise/WordFrameSizingTest.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/WordBuildTrainer.kt:44-46, 129-151, 207-250`
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SentenceOrderTrainer.kt:160-182, 236-283`

**Interfaces:**
- Consumes: nichts aus früheren Tasks.
- Produces: `WordFrameSizing.gapDp(available: Float, frameCount: Int): Float`, `WordFrameSizing.frameWidthDp(available: Float, frameCount: Int): Float`, `WordFrameSizing.glyphSp(frameWidthDp: Float, longestDisplayChars: Int): Float`, and the constants `MaxFrameDp`, `MinFrameDp`, `MaxGapDp`, `MinGapDp`, `FramePaddingDp`, `MaxGlyphSp`, `MinGlyphSp`, `GlyphAspect`. All values are plain `Float` dp/sp magnitudes — the same Compose-free convention `TraceGeometry` and `SymbolHuntLayout` already use, so the maths stays unit-testable. Both `WordBuildTrainer` and `SentenceOrderTrainer` consume the exact same four functions/constants — nothing sentence-specific is added to `WordFrameSizing`.

**Why the gap has to adapt too:** the touch-target floor and the frame count fight each other. Five frames at the 56 dp hit-box minimum already need 280 dp; with the current 12 dp gaps that is 328 dp, more than the ~296 dp a 360 dp phone offers. Shrinking the gap to 4 dp when — and only when — the frames would otherwise breach the floor makes the worst real case ("Häuser", 5 blocks) fit exactly.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/java/app/abcvorschule/ui/exercise/WordFrameSizingTest.kt`:

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordFrameSizingTest {
    /** ExerciseStage caps content at 420.dp and pads 12.dp per side. */
    private val stageWidth = 396f

    /** A 360.dp phone, minus AbcDimens.screenHorizontal and the stage padding. */
    private val narrowPhone = 296f

    /** The longest authored word: Häuser = Hä | u | s | e | r. */
    private val longestWordFrames = 5

    private fun rowWidth(available: Float, frameCount: Int): Float =
        WordFrameSizing.frameWidthDp(available, frameCount) * frameCount +
            WordFrameSizing.gapDp(available, frameCount) * (frameCount - 1)

    @Test
    fun fiveFramesFitTheStageInsteadOfOverflowing() {
        val used = rowWidth(stageWidth, longestWordFrames)
        assertTrue("row needs $used dp of $stageWidth dp", used <= stageWidth)
    }

    @Test
    fun fiveFramesAlsoFitANarrowPhone() {
        val used = rowWidth(narrowPhone, longestWordFrames)
        assertTrue("row needs $used dp of $narrowPhone dp", used <= narrowPhone)
    }

    @Test
    fun everyAuthoredWordLengthFitsBothWidths() {
        (1..longestWordFrames).forEach { count ->
            listOf(stageWidth, narrowPhone).forEach { available ->
                val used = rowWidth(available, count)
                assertTrue("$count frames need $used dp of $available dp", used <= available)
            }
        }
    }

    @Test
    fun theGapOnlyTightensWhenTheFramesWouldBreachTheFloor() {
        // Roomy: keep the comfortable gap.
        assertEquals(WordFrameSizing.MaxGapDp, WordFrameSizing.gapDp(stageWidth, 3), 0.01f)
        // Tight: give the space to the frames instead.
        assertEquals(WordFrameSizing.MinGapDp, WordFrameSizing.gapDp(narrowPhone, 5), 0.01f)
    }

    @Test
    fun shortWordsKeepTheComfortableMaximumWidth() {
        assertEquals(WordFrameSizing.MaxFrameDp, WordFrameSizing.frameWidthDp(stageWidth, 2), 0.01f)
    }

    @Test
    fun framesNeverShrinkBelowTheTouchTargetFloor() {
        // Absurdly narrow: we clamp at the hit-box minimum and accept the overflow
        // rather than rendering frames a child cannot hit.
        val width = WordFrameSizing.frameWidthDp(available = 100f, frameCount = 5)
        assertEquals(WordFrameSizing.MinFrameDp, width, 0.01f)
        assertTrue("floor must clear the 56dp spec minimum", WordFrameSizing.MinFrameDp >= 56f)
    }

    @Test
    fun aDegenerateFrameCountFallsBackToTheMaximum() {
        assertEquals(WordFrameSizing.MaxFrameDp, WordFrameSizing.frameWidthDp(stageWidth, 0), 0.01f)
    }

    @Test
    fun glyphShrinksForAMultiCharacterBlockInANarrowFrame() {
        val wide = WordFrameSizing.glyphSp(WordFrameSizing.MaxFrameDp, longestDisplayChars = 1)
        val narrow = WordFrameSizing.glyphSp(50f, longestDisplayChars = 3)
        assertTrue("$narrow should be smaller than $wide", narrow < wide)
    }

    @Test
    fun glyphNeverLeavesTheLegibleRange() {
        val tiny = WordFrameSizing.glyphSp(WordFrameSizing.MinFrameDp, longestDisplayChars = 3)
        assertTrue(tiny >= WordFrameSizing.MinGlyphSp)
        val huge = WordFrameSizing.glyphSp(400f, longestDisplayChars = 1)
        assertEquals(WordFrameSizing.MaxGlyphSp, huge, 0.01f)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
./gradlew :app:testDebugUnitTest --tests '*WordFrameSizingTest*'
```

Expected: FAIL — `Unresolved reference: WordFrameSizing`.

- [ ] **Step 3: Write the implementation**

Create `app/src/main/java/app/abcvorschule/ui/exercise/WordFrameSizing.kt`:

```kotlin
package app.abcvorschule.ui.exercise

/**
 * Frame and glyph sizing for the Wort-Bauer, in plain dp/sp magnitudes so the
 * maths stays unit-testable (same Compose-free convention as [TraceGeometry]).
 *
 * A fixed frame width overflowed the stage as soon as a word needed five blocks
 * ("Häuser": 5 x 84 + 4 x 12 = 468 dp against 396 dp of usable width), and worse
 * on a narrow phone. Frames therefore shrink to fit, down to a floor that still
 * clears the 56 dp hit-box minimum from the design spec.
 */
object WordFrameSizing {
    /** Comfortable width when the word is short enough to afford it. */
    const val MaxFrameDp = 84f

    /** Floor: the design spec's hard hit-box minimum. Never go below this. */
    const val MinFrameDp = 56f

    /** Preferred horizontal gap between two frames. */
    const val MaxGapDp = 12f

    /** Tightened gap, used only to keep the frames above [MinFrameDp]. */
    const val MinGapDp = 4f

    /** Padding inside a frame, per side. */
    const val FramePaddingDp = 8f

    /** Matches AbcDimens.syllableSp — the size a single glyph gets when there is room. */
    const val MaxGlyphSp = 46f

    /** Below this a preschooler cannot read the block reliably. */
    const val MinGlyphSp = 20f

    /** Rough advance width of one glyph, as a fraction of its font size. */
    const val GlyphAspect = 0.62f

    /**
     * Frames win over whitespace: the gap only tightens once the comfortable gap
     * would squeeze the frames below the touch-target floor.
     */
    fun gapDp(available: Float, frameCount: Int): Float {
        if (frameCount <= 1) return MaxGapDp
        val perFrameAtMaxGap = (available - MaxGapDp * (frameCount - 1)) / frameCount
        return if (perFrameAtMaxGap >= MinFrameDp) MaxGapDp else MinGapDp
    }

    fun frameWidthDp(available: Float, frameCount: Int): Float {
        if (frameCount <= 0) return MaxFrameDp
        val gaps = gapDp(available, frameCount) * (frameCount - 1)
        val perFrame = (available - gaps) / frameCount
        return perFrame.coerceIn(MinFrameDp, MaxFrameDp)
    }

    fun glyphSp(frameWidthDp: Float, longestDisplayChars: Int): Float {
        val chars = longestDisplayChars.coerceAtLeast(1)
        val usable = (frameWidthDp - 2 * FramePaddingDp).coerceAtLeast(1f)
        return (usable / (chars * GlyphAspect)).coerceIn(MinGlyphSp, MaxGlyphSp)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

```bash
./gradlew :app:testDebugUnitTest --tests '*WordFrameSizingTest*'
```

Expected: PASS.

- [ ] **Step 5: Delete the fixed frame constant**

In `app/src/main/java/app/abcvorschule/ui/exercise/WordBuildTrainer.kt`, delete lines 41-46 — the KDoc block and the `private val WordFrameMin = 84.dp` declaration. `WordFrameSizing` now carries that comment.

- [ ] **Step 6: Measure the available width and size the frames**

In the same file, replace the `Row { … }` in the `prompt` lambda — lines 129-151, from `Row(` through its closing `)` — with:

```kotlin
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val frameWidth = WordFrameSizing.frameWidthDp(maxWidth.value, solution.size)
                val gap = WordFrameSizing.gapDp(maxWidth.value, solution.size)
                val glyphSp = WordFrameSizing.glyphSp(
                    frameWidth,
                    solution.maxOfOrNull { it.length } ?: 1,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(
                        gap.dp,
                        Alignment.CenterHorizontally,
                    ),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    solution.forEachIndexed { index, expected ->
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
                            frameWidthDp = frameWidth,
                            glyphSp = glyphSp,
                        )
                    }
                }
            }
```

- [ ] **Step 7: Make `Frame` accept the computed sizes**

In the same file, replace the whole `private fun Frame(...)` composable — lines 207-250 — with:

```kotlin
@Composable
private fun Frame(
    expected: String,
    filled: String?,
    showSilhouette: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: DragFieldState,
    index: Int,
    frameWidthDp: Float,
    glyphSp: Float,
) {
    DropZone(
        state = registerWith,
        key = WordBuildTray.frameKey(index),
        onTap = onTap,
        modifier = Modifier
            .defaultMinSize(minWidth = frameWidthDp.dp, minHeight = frameWidthDp.dp)
            .background(
                color = if (armed) SoftMint.copy(alpha = 0.22f) else NightElevated,
                shape = RoundedCornerShape(22.dp),
            )
            .border(
                width = 3.dp,
                color = if (filled != null) SoftMint.copy(alpha = 0.7f) else SoftSand.copy(alpha = 0.35f),
                shape = RoundedCornerShape(22.dp),
            )
            .padding(
                horizontal = WordFrameSizing.FramePaddingDp.dp,
                vertical = WordFrameSizing.FramePaddingDp.dp,
            )
            .testTag("frame_$index"),
    ) {
        when {
            filled != null -> Text(text = filled, fontSize = glyphSp.sp, color = SoftSand, maxLines = 1)
            showSilhouette -> Text(
                text = expected,
                fontSize = glyphSp.sp,
                color = SoftSand,
                maxLines = 1,
                modifier = Modifier.alpha(0.22f),
            )
            else -> Text(
                text = "_",
                fontSize = glyphSp.sp,
                color = SoftSand.copy(alpha = 0.45f),
                maxLines = 1,
            )
        }
    }
}
```

- [ ] **Step 8: Fix the imports**

In `WordBuildTrainer.kt`:
- **Add** `import androidx.compose.foundation.layout.BoxWithConstraints`.
- **Keep** `import androidx.compose.ui.unit.dp` and `import androidx.compose.ui.unit.sp` — both are still used.
- `AbcDimens` stays imported: the tray tiles (`AbcDimens.tileMinWidth`, `AbcDimens.kidTouch`, `AbcDimens.syllableSp`) are unchanged, because `FlowRow` already wraps them.

- [ ] **Step 9: Apply the same fix to the Satz-Architekt (`SentenceOrderTrainer`)**

In `app/src/main/java/app/abcvorschule/ui/exercise/SentenceOrderTrainer.kt`, replace the `Column(horizontalAlignment = Alignment.CenterHorizontally) { … }` block inside `prompt` — lines 145-183, from `Column(horizontalAlignment` through the closing `}` right before the `},` that ends the `prompt` lambda — with:

```kotlin
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
                BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                    val pegWidth = WordFrameSizing.frameWidthDp(maxWidth.value, words.size)
                    val gap = WordFrameSizing.gapDp(maxWidth.value, words.size)
                    val glyphSp = WordFrameSizing.glyphSp(
                        pegWidth,
                        words.maxOfOrNull { it.length } ?: 1,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(gap.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth(),
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
                                pegWidthDp = pegWidth,
                                glyphSp = glyphSp,
                            )
                        }
                    }
                }
            }
```

- [ ] **Step 10: Make `Peg` accept the computed sizes**

In the same file, replace the whole `private fun Peg(...)` composable — lines 236-283 — with:

```kotlin
@Composable
private fun Peg(
    index: Int,
    expected: String,
    filled: String?,
    showGhost: Boolean,
    armed: Boolean,
    onTap: () -> Unit,
    registerWith: app.abcvorschule.ui.exercise.drag.DragFieldState,
    pegWidthDp: Float,
    glyphSp: Float,
) {
    DropZone(
        state = registerWith,
        key = SentenceOrderTray.pegKey(index),
        onTap = onTap,
        modifier = Modifier
            .defaultMinSize(minWidth = pegWidthDp.dp, minHeight = 64.dp)
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
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = glyphSp.sp),
                color = SoftSand,
                maxLines = 1,
            )
            showGhost -> Text(
                text = expected,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = glyphSp.sp),
                color = SoftSand,
                maxLines = 1,
                modifier = Modifier.alpha(0.22f),
            )
            else -> Text(
                text = "_",
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = glyphSp.sp),
                color = SoftSand.copy(alpha = 0.45f),
                maxLines = 1,
            )
        }
    }
}
```

- [ ] **Step 11: Fix the imports in `SentenceOrderTrainer.kt`**

- **Add** `import androidx.compose.foundation.layout.BoxWithConstraints`.
- **Add** `import androidx.compose.ui.unit.sp` (the file already imports `androidx.compose.ui.unit.dp`).
- Everything else the file already imports (`Arrangement`, `Alignment`, `Canvas`, `Offset`, `StrokeCap`, `MutedText`, etc.) stays as-is.

- [ ] **Step 12: Build and run the full suite**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. `WordBuildTrayTest`, `OrderedPlacementTest` and `SentenceOrderTrayTest` still pass — tray composition and placement logic are untouched in both trainers.

- [ ] **Step 13: Manual verification**

```bash
./gradlew :app:installDebug
```

Reach a Wort-Bauer round with a long word (**Häuser**, 5 blocks, is the worst case; **Nest**/**Keks** are 4) and a Satz-Architekt round with the longest sentence (4 words, e.g. **„Das ist mein Papa"**). Confirm for both:
1. All frames/pegs are visible with no horizontal clipping.
2. The glyph/word inside each frame is not truncated.
3. Frames/pegs are still comfortably tappable.
4. Both the drag and the tap path still place a block/card.

Repeat on a narrow device — create a 360×640 emulator if none is configured.

- [ ] **Step 14: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/WordFrameSizing.kt app/src/test/java/app/abcvorschule/ui/exercise/WordFrameSizingTest.kt app/src/main/java/app/abcvorschule/ui/exercise/WordBuildTrainer.kt app/src/main/java/app/abcvorschule/ui/exercise/SentenceOrderTrainer.kt
git commit -m "fix(word-build,sentence-order): scale frames and pegs to the available width"
```

---

## Task 7: README aufräumen

Close out the issue list the plan was written against.

**Files:**
- Modify: `README.md` (the `## Open Issues` section — line numbers have shifted since this plan
  was drafted due to an intervening docs commit; locate the section by its heading, not by line
  number)

**Interfaces:**
- Consumes: the completed Tasks 1-6.
- Produces: nothing.

- [ ] **Step 1: Verify every issue is actually closed**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Then walk the full README smoke script (steps 1-10) once on a device.

- [ ] **Step 2: Replace the Open Issues section**

In `README.md`, delete the `## Open Issues` heading and its five bullets (currently the last
section in the file). If any issue was only partially addressed, keep it as a bullet with a note
on what remains — do not silently drop it.

- [ ] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: clear the closed items from the open issues list"
```

---

## Notes for the implementer

**Two issues turned out to share no code but one habit.** The number-pad bug (Task 1) and the drag bug (Task 2) are both "state or paint survives where it shouldn't". If you touch another trainer and see `remember { }` without a key, or a caller-supplied `modifier` applied before a layout modifier, look twice.

**Task 4 is a judgement task, not a mechanical one.** It deliberately produces evidence before it changes anything. If the contact sheet shows all 39 glyphs are fine, the correct outcome is to commit only `tools/render_glyphs.py` and say so — the issue text is a report, not a proof.

**Verification honesty.** Tasks 2, 5 and 6 change Compose layout and animation, which this project has no automated coverage for. Their manual steps are the actual verification, not a formality. Report what you observed, including anything that still looks off.
