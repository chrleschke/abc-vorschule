# Pfad-Screen kindgerecht — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Pfad-Screen liest sich als natürlicher Weg durch eine Nachtlandschaft — gepunktete, weich geschwungene Trittspuren zwischen Wegweiser-Schildern, die drei Emojis der jeweiligen Lektion zeigen.

**Architecture:** Sämtliche Geometrie- und Auswahl-Logik liegt in reinen Kotlin-Objekten (`PathGeometry`, `PathTrail`, `LessonEmojis`) ohne Compose- oder `android.graphics`-Abhängigkeit; die Compose-Ebenen (`PathBackground`, `PathSignNode`) zeichnen nur noch. Das Repo hat **keine** androidTests — JVM-Unit-Tests sind die einzige automatisierte Absicherung, deshalb muss jede Entscheidung, die man testen können soll, in den reinen Objekten landen.

**Tech Stack:** Kotlin, Jetpack Compose (Canvas, graphicsLayer), JUnit 4 mit `org.junit.Assert`, Gradle.

**Spec:** `docs/superpowers/specs/2026-07-31-pfad-screen-kindgerecht-design.md`

## Global Constraints

- **Dark-only bleibt Prinzip.** Keine Hintergrundfläche heller als `NightElevated` (#223247), kein reines Weiß.
- **Kontrast** Schildtext zu Schildfläche mindestens 4.5:1.
- **Kein Zufall zur Laufzeit.** Jede „organische" Variation (Kurven-Versatz, Abstände, Schild-Neigung, Punktgrößen, Sternpositionen) ist deterministisch aus dem Index abgeleitet. Zweimal dieselbe Eingabe → zweimal dasselbe Bild.
- **Kind liest nicht.** Keine neuen Textlabels. Das Graphem auf dem Schild ist der einzige Text und bleibt in `MaterialTheme.typography.titleLarge`.
- **Touch-Ziel** jedes anklickbaren Knotens ≥ `AbcDimens.kidTouch` (80.dp).
- **`onLockedTap` bleibt unverändert** — ein Tipp auf einen gesperrten Knoten ist nie ein stummes No-Op.
- **Bestehende testTags bleiben:** `path_scroll` und `path_node_$label`.
- Tests: `./gradlew :app:testDebugUnitTest` · Build: `./gradlew :app:assembleDebug`
- Commit-Präfixe wie im Repo üblich (`feat(path):`, `test(path):`, `docs(path):`).

## File Structure

| Datei | Verantwortung |
| --- | --- |
| `ui/path/PathGeometry.kt` (ändern) | Knotenpositionen + `PathNoise`; rein |
| `ui/path/PathTrail.kt` (neu) | Catmull-Rom-Spline + Trittspuren-Punkte; rein |
| `content/LessonEmojis.kt` (neu) | Drei Emojis je Lektion; rein |
| `ui/theme/Colors.kt` (ändern) | Nacht- und Holztöne |
| `ui/path/PathBackground.kt` (neu) | Verlauf, Sterne, Hügel, Parallaxe |
| `ui/path/PathSignNode.kt` (neu) | Wegweiser-Schild inkl. Zustände |
| `ui/path/PathScreen.kt` (ändern) | Nur noch Komposition der Ebenen |
| `session/SessionViewModel.kt` (ändern) | `lessonEmojis()`-Accessor |
| `ui/shell/TaskShell.kt` (ändern) | Neuer Parameter durchreichen |

Reihenfolge: erst die drei reinen Objekte (Tasks 1–3, vollständig testgetrieben), dann die Compose-Ebenen (Tasks 4–5), dann die Verdrahtung (Task 6), dann Doku (Task 7). Nach Task 3 ist noch nichts sichtbar verändert; nach Task 6 ist das Feature komplett.

---

### Task 1: `PathGeometry` — organische Kurve

Heute rastet die Kurve auf exakt vier Positionen (Periode 4) mit konstantem y-Abstand. Neu: Periode 3.7, deterministischer Versatz proportional zur Amplitude, y-Abstand ±8 %.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/path/PathGeometry.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/path/PathGeometryTest.kt` (existiert, wird umgeschrieben)

**Interfaces:**
- Consumes: nichts
- Produces:
  - `data class PathPoint(val x: Float, val y: Float)` — unverändert
  - `PathGeometry.points(count: Int, width: Float, spacing: Float = DefaultSpacing, margin: Float = DefaultMargin): List<PathPoint>`
  - `PathGeometry.contentHeight(count: Int, spacing: Float = DefaultSpacing, margin: Float = DefaultMargin): Float`
  - `PathGeometry.DefaultSpacing = 168f`, `PathGeometry.DefaultMargin = 132f`
  - `internal object PathNoise { fun signed(index: Int, salt: Int): Float }` — Rückgabe in (−1f, 1f), von Task 2 und Task 5 mitbenutzt

- [ ] **Step 1: Bestehende Tests umschreiben**

Ersetze den Inhalt von `app/src/test/java/app/abcvorschule/ui/path/PathGeometryTest.kt` vollständig. Drei Tests bleiben inhaltlich erhalten (`emptyPathHasNoPoints`, `amplitudeStaysInsideTheMargins`, `narrowScreenCollapsesToAStraightLine`), drei werden durch neue Invarianten ersetzt.

```kotlin
package app.abcvorschule.ui.path

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathGeometryTest {
    private val width = 1000f
    private val spacing = 168f
    private val margin = 132f

    private fun points(count: Int) = PathGeometry.points(count, width, spacing, margin)

    @Test
    fun emptyPathHasNoPoints() {
        assertEquals(emptyList<PathPoint>(), points(0))
    }

    @Test
    fun yGrowsStrictlyMonotonically() {
        val p = points(26)
        assertEquals(margin, p[0].y, 0.01f)
        p.zipWithNext { a, b ->
            assertTrue("y must grow: ${a.y} -> ${b.y}", b.y > a.y)
        }
    }

    @Test
    fun verticalGapsStayWithinEightPercentOfNominalSpacing() {
        points(26).zipWithNext { a, b ->
            val gap = b.y - a.y
            assertTrue("gap $gap too small", gap >= spacing * 0.92f - 0.01f)
            assertTrue("gap $gap too large", gap <= spacing * 1.08f + 0.01f)
        }
    }

    @Test
    fun curveSwingsToBothSidesOfCenter() {
        val center = width / 2f
        val xs = points(16).map { it.x }
        assertTrue("must swing left", xs.any { it < center - 10f })
        assertTrue("must swing right", xs.any { it > center + 10f })
    }

    @Test
    fun pointsAreDeterministic() {
        assertEquals(points(26), points(26))
    }

    @Test
    fun amplitudeStaysInsideTheMargins() {
        points(26).forEach {
            assertTrue("x=${it.x} left of margin", it.x >= margin - 0.01f)
            assertTrue("x=${it.x} right of margin", it.x <= width - margin + 0.01f)
        }
    }

    @Test
    fun narrowScreenCollapsesToAStraightLine() {
        // Amplitude 0 must swallow the organic jitter too, otherwise nodes would
        // wander off a screen that has no room to swing.
        val p = PathGeometry.points(4, width = 120f, spacing = spacing, margin = margin)
        assertEquals(1, p.map { it.x }.distinct().size)
    }

    @Test
    fun contentHeightIsLastNodePlusMargin() {
        assertEquals(2 * margin, PathGeometry.contentHeight(1, spacing, margin), 0.01f)
        assertEquals(0f, PathGeometry.contentHeight(0, spacing, margin), 0.01f)
        assertEquals(
            points(26).last().y + margin,
            PathGeometry.contentHeight(26, spacing, margin),
            0.01f,
        )
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathGeometryTest*"`
Expected: FAIL — `verticalGapsStayWithinEightPercentOfNominalSpacing` und `contentHeightIsLastNodePlusMargin` scheitern, weil die alte Implementierung konstante Abstände liefert und `contentHeight` eine eigene Formel benutzt. (`yGrowsStrictlyMonotonically` und `curveSwingsToBothSides` gehen zufällig schon durch — das ist in Ordnung.)

- [ ] **Step 3: `PathGeometry.kt` neu schreiben**

```kotlin
package app.abcvorschule.ui.path

import kotlin.math.PI
import kotlin.math.sin

/** Node center in path-content pixels, y growing downwards. */
data class PathPoint(val x: Float, val y: Float)

/**
 * Deterministic pseudo-noise. The path must look hand-drawn but never move
 * between two recompositions, so nothing here uses Random or any state — the
 * same (index, salt) always yields the same value in (-1f, 1f).
 */
internal object PathNoise {
    fun signed(index: Int, salt: Int): Float {
        var h = index * 374761393 + salt * 668265263
        h = (h xor (h shr 13)) * 1274126177
        h = h xor (h shr 16)
        return (h % 1000) / 1000f
    }
}

/**
 * Winding trail: nodes stack top-down while x swings across the screen. The
 * period is deliberately not a whole number of nodes and both axes carry a small
 * index-derived offset, so the curve never rasters onto a handful of exact
 * positions the way a plain sine does. Pure math so the layout is unit-testable
 * without Compose.
 */
object PathGeometry {
    const val DefaultSpacing = 168f
    const val DefaultMargin = 132f

    /** Nodes per full left-right-left swing. Non-integer on purpose. */
    private const val Period = 3.7

    private const val XJitterFraction = 0.06f
    private const val YJitterFraction = 0.08f

    /**
     * y of every node. Shared by [points] and [contentHeight] — with variable
     * spacing the two would otherwise drift apart and the scroll area would cut
     * the last nodes off.
     */
    private fun yOffsets(count: Int, spacing: Float, margin: Float): List<Float> {
        var y = margin
        return (0 until count).map { index ->
            if (index > 0) y += spacing * (1f + YJitterFraction * PathNoise.signed(index, salt = 7))
            y
        }
    }

    fun points(
        count: Int,
        width: Float,
        spacing: Float = DefaultSpacing,
        margin: Float = DefaultMargin,
    ): List<PathPoint> {
        if (count <= 0) return emptyList()
        val center = width / 2f
        val amplitude = (center - margin).coerceAtLeast(0f)
        val ys = yOffsets(count, spacing, margin)
        return (0 until count).map { index ->
            val swing = sin(index * 2.0 * PI / Period).toFloat()
            val jitter = XJitterFraction * PathNoise.signed(index, salt = 3)
            // Jitter is scaled by amplitude, not added in pixels: on a screen too
            // narrow to swing (amplitude 0) every node must sit dead center.
            PathPoint(
                x = center + amplitude * (swing + jitter).coerceIn(-1f, 1f),
                y = ys[index],
            )
        }
    }

    fun contentHeight(
        count: Int,
        spacing: Float = DefaultSpacing,
        margin: Float = DefaultMargin,
    ): Float = if (count <= 0) 0f else yOffsets(count, spacing, margin).last() + margin
}
```

- [ ] **Step 4: Tests laufen lassen, grün bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathGeometryTest*"`
Expected: PASS, 8 Tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/path/PathGeometry.kt app/src/test/java/app/abcvorschule/ui/path/PathGeometryTest.kt
git commit -m "feat(path): give the path curve an organic, deterministic shape"
```

---

### Task 2: `PathTrail` — Spline und Trittspuren

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/path/PathTrail.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/path/PathTrailTest.kt`

**Interfaces:**
- Consumes: `PathPoint`, `PathNoise.signed(index, salt)` aus Task 1
- Produces:
  - `data class TrailDot(val x: Float, val y: Float, val radius: Float, val walked: Boolean)`
  - `PathTrail.polyline(points: List<PathPoint>, samplesPerSegment: Int = SamplesPerSegment): List<PathPoint>`
  - `PathTrail.dots(polyline: List<PathPoint>, walkedUpTo: Int, samplesPerSegment: Int = SamplesPerSegment, spacing: Float = DefaultDotSpacing, radius: Float = DefaultDotRadius): List<TrailDot>`
  - `PathTrail.SamplesPerSegment = 24`, `PathTrail.DefaultDotSpacing = 18f`, `PathTrail.DefaultDotRadius = 4f`

- [ ] **Step 1: Failing test schreiben**

Erstelle `app/src/test/java/app/abcvorschule/ui/path/PathTrailTest.kt`:

```kotlin
package app.abcvorschule.ui.path

import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PathTrailTest {
    private val nodes = PathGeometry.points(count = 8, width = 1000f)

    @Test
    fun polylineOfFewerThanTwoNodesIsReturnedUnchanged() {
        assertEquals(emptyList<PathPoint>(), PathTrail.polyline(emptyList()))
        val single = listOf(PathPoint(10f, 10f))
        assertEquals(single, PathTrail.polyline(single))
    }

    @Test
    fun splinePassesExactlyThroughEveryNode() {
        // Sample s = 0 of segment i is node i by construction; the very last
        // entry is the last node. If this breaks, signs no longer sit on the trail.
        val line = PathTrail.polyline(nodes)
        nodes.forEachIndexed { index, node ->
            val sampled = line[index * PathTrail.SamplesPerSegment]
            assertEquals("node $index x", node.x, sampled.x, 0.01f)
            assertEquals("node $index y", node.y, sampled.y, 0.01f)
        }
        assertEquals(nodes.last(), line.last())
    }

    @Test
    fun splineIsSmootherThanTheStraightPolygon() {
        // A spline detours around the corners, so it must be strictly longer than
        // the straight connection — that is what "rounded" means numerically.
        fun length(p: List<PathPoint>) =
            p.zipWithNext { a, b -> hypot(b.x - a.x, b.y - a.y) }.sum()
        assertTrue(length(PathTrail.polyline(nodes)) > length(nodes))
    }

    @Test
    fun dotsAreEvenlySpacedAlongTheTrail() {
        val dots = PathTrail.dots(PathTrail.polyline(nodes), walkedUpTo = -1, spacing = 18f)
        assertTrue("expected a good number of dots, got ${dots.size}", dots.size > 40)
        dots.zipWithNext { a, b ->
            val d = hypot(b.x - a.x, b.y - a.y)
            assertTrue("dot gap $d off nominal 18", d in 16.2f..19.8f)
        }
    }

    @Test
    fun dotRadiusVariesButStaysNearNominal() {
        val dots = PathTrail.dots(PathTrail.polyline(nodes), walkedUpTo = -1, radius = 4f)
        assertTrue("radius must vary", dots.map { it.radius }.distinct().size > 1)
        dots.forEach {
            assertTrue("radius ${it.radius} off nominal 4", it.radius in 3.4f..4.6f)
        }
    }

    @Test
    fun dotsBeforeTheReachedNodeAreMarkedWalked() {
        val line = PathTrail.polyline(nodes)
        val dots = PathTrail.dots(line, walkedUpTo = 3)
        assertTrue("walked dots must exist", dots.any { it.walked })
        assertTrue("unwalked dots must exist", dots.any { !it.walked })
        // walked must be a prefix: once the flag flips it never flips back.
        val firstUnwalked = dots.indexOfFirst { !it.walked }
        assertTrue(dots.drop(firstUnwalked).none { it.walked })
    }

    @Test
    fun nothingReachedMeansNothingWalked() {
        val dots = PathTrail.dots(PathTrail.polyline(nodes), walkedUpTo = -1)
        assertTrue(dots.none { it.walked })
        val fromZero = PathTrail.dots(PathTrail.polyline(nodes), walkedUpTo = 0)
        assertTrue(fromZero.none { it.walked })
    }

    @Test
    fun tooFewNodesProduceNoDots() {
        assertEquals(emptyList<TrailDot>(), PathTrail.dots(emptyList(), walkedUpTo = 0))
        assertEquals(
            emptyList<TrailDot>(),
            PathTrail.dots(listOf(PathPoint(1f, 1f)), walkedUpTo = 0),
        )
    }

    @Test
    fun dotsAreDeterministic() {
        val line = PathTrail.polyline(nodes)
        assertEquals(PathTrail.dots(line, walkedUpTo = 2), PathTrail.dots(line, walkedUpTo = 2))
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathTrailTest*"`
Expected: FAIL — Kompilierfehler „Unresolved reference: PathTrail".

- [ ] **Step 3: `PathTrail.kt` schreiben**

```kotlin
package app.abcvorschule.ui.path

import kotlin.math.hypot

/** One footprint dot on the trail, in path-content pixels. */
data class TrailDot(
    val x: Float,
    val y: Float,
    val radius: Float,
    /** True for dots the child has already walked past — drawn warm, not dimmed. */
    val walked: Boolean,
)

/**
 * The dotted trail between path nodes: a Catmull-Rom spline sampled into a
 * polyline, then covered in evenly spaced footprint dots.
 *
 * Deliberately plain Kotlin — no android.graphics.PathMeasure, which does not
 * exist in JVM unit tests, and no PathEffect dashing, which reads as a technical
 * dashed line rather than as footprints.
 */
object PathTrail {
    const val SamplesPerSegment = 24
    const val DefaultDotSpacing = 18f
    const val DefaultDotRadius = 4f
    private const val RadiusJitterFraction = 0.15f

    /**
     * Catmull-Rom spline through every node. The first and last node are mirrored
     * outwards to give the end segments a tangent, so the trail does not start or
     * stop with a kink.
     */
    fun polyline(
        points: List<PathPoint>,
        samplesPerSegment: Int = SamplesPerSegment,
    ): List<PathPoint> {
        if (points.size < 2) return points
        val out = ArrayList<PathPoint>((points.size - 1) * samplesPerSegment + 1)
        for (i in 0 until points.size - 1) {
            val p0 = points.getOrNull(i - 1) ?: mirror(points[0], points[1])
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points.getOrNull(i + 2)
                ?: mirror(points[points.lastIndex], points[points.lastIndex - 1])
            for (s in 0 until samplesPerSegment) {
                out += interpolate(p0, p1, p2, p3, s.toFloat() / samplesPerSegment)
            }
        }
        out += points.last()
        return out
    }

    /**
     * Footprint dots at a constant arc-length [spacing]. Spacing is measured along
     * the curve, not per sample — otherwise dots would bunch up in the bends.
     *
     * [walkedUpTo] is the index of the last node the child has reached; -1 means
     * none. Dots on earlier segments come back with `walked = true`.
     */
    fun dots(
        polyline: List<PathPoint>,
        walkedUpTo: Int,
        samplesPerSegment: Int = SamplesPerSegment,
        spacing: Float = DefaultDotSpacing,
        radius: Float = DefaultDotRadius,
    ): List<TrailDot> {
        if (polyline.size < 2 || spacing <= 0f) return emptyList()
        val walkedSamples = if (walkedUpTo <= 0) 0 else walkedUpTo * samplesPerSegment
        val out = ArrayList<TrailDot>()
        var carry = 0f
        for (i in 0 until polyline.size - 1) {
            val a = polyline[i]
            val b = polyline[i + 1]
            val segment = hypot(b.x - a.x, b.y - a.y)
            if (segment <= 0f) continue
            var travelled = spacing - carry
            while (travelled <= segment) {
                val t = travelled / segment
                out += TrailDot(
                    x = a.x + (b.x - a.x) * t,
                    y = a.y + (b.y - a.y) * t,
                    radius = radius * (1f + RadiusJitterFraction * PathNoise.signed(out.size, salt = 11)),
                    walked = i < walkedSamples,
                )
                travelled += spacing
            }
            carry = segment - (travelled - spacing)
        }
        return out
    }

    private fun mirror(anchor: PathPoint, other: PathPoint) =
        PathPoint(x = 2 * anchor.x - other.x, y = 2 * anchor.y - other.y)

    private fun interpolate(
        p0: PathPoint,
        p1: PathPoint,
        p2: PathPoint,
        p3: PathPoint,
        t: Float,
    ): PathPoint {
        val t2 = t * t
        val t3 = t2 * t
        fun axis(a: Float, b: Float, c: Float, d: Float) = 0.5f * (
            2f * b +
                (-a + c) * t +
                (2f * a - 5f * b + 4f * c - d) * t2 +
                (-a + 3f * b - 3f * c + d) * t3
            )
        return PathPoint(
            x = axis(p0.x, p1.x, p2.x, p3.x),
            y = axis(p0.y, p1.y, p2.y, p3.y),
        )
    }
}
```

- [ ] **Step 4: Tests laufen lassen, grün bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*PathTrailTest*"`
Expected: PASS, 9 Tests.

Falls `dotsAreEvenlySpacedAlongTheTrail` knapp scheitert: die Abstände werden als Sehne über gesampelte Teilstücke gemessen und liegen in Kurven minimal unter dem Sollwert. Erhöhe dann `SamplesPerSegment` auf 32 statt die Toleranz aufzuweichen — die Toleranz ist die Aussage des Tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/path/PathTrail.kt app/src/test/java/app/abcvorschule/ui/path/PathTrailTest.kt
git commit -m "feat(path): add spline trail with evenly spaced footprint dots"
```

---

### Task 3: `LessonEmojis` — drei Emojis je Lektion

**Files:**
- Create: `app/src/main/java/app/abcvorschule/content/LessonEmojis.kt`
- Test: `app/src/test/java/app/abcvorschule/content/LessonEmojisTest.kt`

**Interfaces:**
- Consumes: `ContentPack`, `Lesson`, `SoundPositionSpec`, `WordBuildSpec`, `CountAddSpec`, `SentenceOrderSpec` — alle vorhanden in `app/src/main/java/app/abcvorschule/content/`
- Produces: `LessonEmojis.forLesson(pack: ContentPack, lesson: Lesson, limit: Int = DefaultLimit): List<String>`, `LessonEmojis.DefaultLimit = 3`

**Hintergrund für den Implementierer:** Es liegt nahe, das vorhandene `TrainerRound.scoredAtomIds()` zu benutzen. Das ist falsch: es liefert für `SentenceOrderRound` und `CountAddRound` bewusst leere Listen, und für `letter_trace`/`syllable_merge` Buchstaben- und Silben-Atome, deren `emoji`-Feld im Content durchweg `""` ist (alle 39 Buchstaben-Atome). Deshalb die explizite Quellenreihenfolge unten.

- [ ] **Step 1: Failing test schreiben**

Tests im Repo laden das echte Content-Pack über `ContentRepository.fromClasspath().load()` (siehe `SymbolHuntDerivationTest`). Erstelle `app/src/test/java/app/abcvorschule/content/LessonEmojisTest.kt`:

```kotlin
package app.abcvorschule.content

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonEmojisTest {
    private val pack = ContentRepository.fromClasspath().load()

    @Test
    fun firstLessonShowsItsOwnPictureWords() {
        // l01 (M & A) hunts the sound in ameise / maus / baum.
        assertEquals(
            listOf("🐜", "🐭", "🌳"),
            LessonEmojis.forLesson(pack, pack.lesson("l01")),
        )
    }

    @Test
    fun everyAuthoredLessonYieldsThreeEmojis() {
        pack.authoredLessons.forEach { lesson ->
            val emojis = LessonEmojis.forLesson(pack, lesson)
            assertEquals("lesson ${lesson.id}", 3, emojis.size)
            assertTrue("lesson ${lesson.id} has a blank emoji", emojis.none { it.isBlank() })
        }
    }

    @Test
    fun emojisAreDeduplicatedByGlyphNotByAtomId() {
        // dach and haus both carry the same house glyph; a sign must never show
        // the same picture twice even when two different atoms supply it.
        pack.authoredLessons.forEach { lesson ->
            val emojis = LessonEmojis.forLesson(pack, lesson)
            assertEquals("lesson ${lesson.id} repeats a glyph", emojis.size, emojis.distinct().size)
        }
    }

    @Test
    fun theLimitIsHonoured() {
        assertEquals(2, LessonEmojis.forLesson(pack, pack.lesson("l01"), limit = 2).size)
        assertEquals(emptyList<String>(), LessonEmojis.forLesson(pack, pack.lesson("l01"), limit = 0))
    }

    @Test
    fun resultIsStableAcrossCalls() {
        pack.authoredLessons.forEach { lesson ->
            assertEquals(
                LessonEmojis.forLesson(pack, lesson),
                LessonEmojis.forLesson(pack, lesson),
            )
        }
    }

    @Test
    fun plannedLessonWithoutTasksYieldsNothing() {
        // No lesson in the shipped pack is `planned`, so this case only exists
        // synthetically — the sign then shows the lock glyph and an empty (but
        // still space-reserving) emoji row.
        val planned = Lesson(
            id = "l99",
            index = 99,
            phase = 7,
            title = "Noch nicht geschrieben",
            nodeLabel = "?",
            status = LessonStatus.planned,
        )
        assertEquals(emptyList<String>(), LessonEmojis.forLesson(pack, planned))
    }

    @Test
    fun unknownTaskIdsAreSkippedInsteadOfThrowing() {
        val broken = pack.lesson("l01").copy(taskIds = listOf("does-not-exist"))
        assertEquals(emptyList<String>(), LessonEmojis.forLesson(pack, broken))
    }
}
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*LessonEmojisTest*"`
Expected: FAIL — Kompilierfehler „Unresolved reference: LessonEmojis".

- [ ] **Step 3: `LessonEmojis.kt` schreiben**

```kotlin
package app.abcvorschule.content

/**
 * The picture words a path signpost shows for a lesson: at most three emojis,
 * drawn from the lesson's own vocabulary.
 *
 * Deterministic by design — no Random, no shuffling. The same lesson always
 * yields the same emojis in the same order, so the path does not rearrange
 * itself between two launches.
 */
object LessonEmojis {
    const val DefaultLimit = 3

    fun forLesson(pack: ContentPack, lesson: Lesson, limit: Int = DefaultLimit): List<String> {
        if (limit <= 0) return emptyList()
        val specs = lesson.taskIds.mapNotNull { pack.tasks[it] }
        val chosen = LinkedHashSet<String>()
        for (atomId in sourceAtomIds(specs)) {
            val emoji = pack.atoms[atomId]?.emoji.orEmpty()
            // Dedupe on the glyph, not the atom id: `dach` and `haus` share one
            // house emoji, and two identical pictures read as a bug.
            if (emoji.isNotBlank()) chosen += emoji
            if (chosen.size == limit) break
        }
        return chosen.toList()
    }

    /**
     * Atom ids in the order a sign should prefer them — the trainers whose atoms
     * actually carry a picture. letter_trace and syllable_merge are skipped: their
     * atoms are letters and syllables, and those have no emoji in the content.
     * letter_trace's own `rewardEmoji` is left out on purpose too — it is the
     * trainer's reward and should not be spoiled on the path.
     */
    private fun sourceAtomIds(specs: List<TaskSpec>): List<String> =
        specs.filterIsInstance<SoundPositionSpec>().flatMap { spec -> spec.rounds.map { it.atomId } } +
            specs.filterIsInstance<WordBuildSpec>().flatMap { spec -> spec.rounds.map { it.targetAtomId } } +
            specs.filterIsInstance<CountAddSpec>().flatMap { spec -> spec.rounds.map { it.iconAtomId } } +
            specs.filterIsInstance<SentenceOrderSpec>()
                .flatMap { spec -> spec.rounds.mapNotNull { it.illustrationAtomId } }
}
```

- [ ] **Step 4: Tests laufen lassen, grün bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "*LessonEmojisTest*"`
Expected: PASS, 7 Tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/content/LessonEmojis.kt app/src/test/java/app/abcvorschule/content/LessonEmojisTest.kt
git commit -m "feat(content): derive three signpost emojis per lesson"
```

---

### Task 4: Farbtokens und `PathBackground`

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/theme/Colors.kt`
- Create: `app/src/main/java/app/abcvorschule/ui/path/PathBackground.kt`

**Interfaces:**
- Consumes: nichts aus Tasks 1–3
- Produces:
  - Farbtokens `NightDeep`, `NightHorizon`, `WoodDark`, `WoodMid`, `WoodWarm`, `WoodPost` (Holztöne werden erst in Task 5 benutzt, gehören aber in einen Commit mit den anderen Tokens)
  - `@Composable fun PathBackground(scrollOffset: () -> Int, modifier: Modifier = Modifier)`

Diese Task hat keinen Unit-Test: sie zeichnet nur, und das Repo hat keine androidTests. Abgesichert wird sie über den Build und den manuellen Smoke-Schritt in Task 6.

- [ ] **Step 1: Farbtokens ergänzen**

An `app/src/main/java/app/abcvorschule/ui/theme/Colors.kt` anhängen:

```kotlin
/** Night sky gradient: deepest at the top, warmer towards the horizon. */
val NightDeep = Color(0xFF080E18)
val NightHorizon = Color(0xFF16283A)

/**
 * Signpost woods. Kept dark enough that SoftSand lettering stays above 4.5:1 on
 * every one of them — the path is looked at in a dark room.
 */
val WoodDark = Color(0xFF2A2018)
val WoodMid = Color(0xFF4A3728)
val WoodWarm = Color(0xFF6B4E34)
val WoodPost = Color(0xFF33261B)
```

- [ ] **Step 2: `PathBackground.kt` schreiben**

```kotlin
package app.abcvorschule.ui.path

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import app.abcvorschule.ui.theme.NightDeep
import app.abcvorschule.ui.theme.NightElevated
import app.abcvorschule.ui.theme.NightHorizon
import app.abcvorschule.ui.theme.NightInk
import app.abcvorschule.ui.theme.NightPanel
import app.abcvorschule.ui.theme.SoftSand
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

private const val StarCount = 40
private const val StarSeed = 42

/** Star position in 0..1 screen fractions plus its twinkle phase. */
private data class Star(val fx: Float, val fy: Float, val radius: Float, val phase: Float)

/**
 * The night landscape behind the path: a vertical gradient, a fixed star field
 * and three layers of hills that drift slowly as the child scrolls.
 *
 * [scrollOffset] is passed as a lambda, not a value: it is read inside
 * graphicsLayer, so scrolling moves the hills without recomposing anything.
 */
@Composable
fun PathBackground(scrollOffset: () -> Int, modifier: Modifier = Modifier) {
    // Fixed seed: the sky must look scattered but must not re-scatter itself on
    // every recomposition.
    val stars = remember {
        val random = Random(StarSeed)
        List(StarCount) { index ->
            Star(
                fx = random.nextFloat(),
                // Stars only in the upper two thirds — below that are the hills.
                fy = random.nextFloat() * 0.66f,
                radius = 1f + random.nextFloat(),
                phase = index * 0.37f,
            )
        }
    }

    // One transition drives all stars; each star reads it at its own phase offset
    // instead of owning an animation of its own.
    val transition = rememberInfiniteTransition(label = "sky")
    val twinkle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4200), RepeatMode.Reverse),
        label = "sky_twinkle",
    )

    // Everything is wrapped in a Box of its own: the layers must stack on top of
    // each other, not depend on the caller happening to be a Box.
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to NightDeep,
                    0.55f to NightInk,
                    1f to NightHorizon,
                ),
            )
            stars.forEach { star ->
                val twinkleAlpha =
                    0.10f + 0.15f * abs(sin((twinkle + star.phase) * PI.toFloat()))
                drawCircle(
                    color = SoftSand.copy(alpha = twinkleAlpha),
                    radius = star.radius.dp.toPx(),
                    center = Offset(star.fx * size.width, star.fy * size.height),
                )
            }
        }

        // Hills sit in their own layers so each can drift at its own parallax factor.
        HillBand(color = NightPanel, alpha = 0.5f, baseFraction = 0.72f, amplitude = 34f, parallax = 0.05f, scrollOffset = scrollOffset)
        HillBand(color = NightPanel, alpha = 0.7f, baseFraction = 0.82f, amplitude = 46f, parallax = 0.10f, scrollOffset = scrollOffset)
        HillBand(color = NightElevated, alpha = 0.9f, baseFraction = 0.92f, amplitude = 28f, parallax = 0.15f, scrollOffset = scrollOffset, trees = true)
    }
}

@Composable
private fun HillBand(
    color: Color,
    alpha: Float,
    baseFraction: Float,
    amplitude: Float,
    parallax: Float,
    scrollOffset: () -> Int,
    trees: Boolean = false,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = -scrollOffset() * parallax }
            .drawBehind {
                val base = size.height * baseFraction
                val hill = Path().apply {
                    moveTo(0f, size.height)
                    lineTo(0f, base)
                    var x = 0f
                    while (x <= size.width) {
                        lineTo(x, base - amplitude * sin(x / size.width * 3.4f))
                        x += size.width / 24f
                    }
                    lineTo(size.width, size.height)
                    close()
                }
                drawPath(hill, color = color.copy(alpha = alpha))
                if (trees) {
                    listOf(0.14f, 0.31f, 0.68f, 0.86f).forEach { fx ->
                        val tx = size.width * fx
                        val ty = base - amplitude * sin(tx / size.width * 3.4f)
                        val tree = Path().apply {
                            moveTo(tx, ty - 42f)
                            lineTo(tx - 16f, ty + 4f)
                            lineTo(tx + 16f, ty + 4f)
                            close()
                        }
                        drawPath(tree, color = NightInk.copy(alpha = 0.85f))
                    }
                }
            },
    )
}
```

- [ ] **Step 3: Build prüfen**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. `PathBackground` ist noch nicht eingebunden, muss aber kompilieren.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/theme/Colors.kt app/src/main/java/app/abcvorschule/ui/path/PathBackground.kt
git commit -m "feat(path): add night landscape background with parallax hills"
```

---

### Task 5: `PathSignNode` — das Wegweiser-Schild

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/path/PathSignNode.kt`

**Interfaces:**
- Consumes: `PathNoise.signed(index, salt)` (Task 1), Holztöne (Task 4), `LessonState` und `LessonGating.isPlayable` (vorhanden), `IconStar` aus `ui/components/AbcIcons.kt` (vorhanden, Signatur `IconStar(tint: Color, modifier: Modifier = Modifier, size: Dp = 28.dp)`)
- Produces:
  - `PathSignDimens.BoardWidth = 136.dp`, `PathSignDimens.BoardHeight = 86.dp`, `PathSignDimens.PostHeight = 30.dp`, `PathSignDimens.TotalHeight = 116.dp`
  - `@Composable fun PathSignNode(label: String, emojis: List<String>, state: LessonState, highlighted: Boolean, index: Int, onClick: () -> Unit, modifier: Modifier = Modifier)`

**Wichtig — Ankerpunkt:** Der Geometrie-Punkt ist die **Pfostenbasis**, nicht die Brettmitte. Sonst läuft der Weg quer durchs Brett. Task 6 versetzt den Knoten deshalb um die volle Schildhöhe nach oben. `PathSignNode` selbst zeichnet einfach von oben nach unten: Brett, dann Pfosten.

- [ ] **Step 1: `PathSignNode.kt` schreiben**

```kotlin
package app.abcvorschule.ui.path

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.abcvorschule.R
import app.abcvorschule.progress.LessonGating
import app.abcvorschule.progress.LessonState
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.SoftGold
import app.abcvorschule.ui.theme.SoftMint
import app.abcvorschule.ui.theme.SoftSand
import app.abcvorschule.ui.theme.SoftSky
import app.abcvorschule.ui.theme.WoodDark
import app.abcvorschule.ui.theme.WoodMid
import app.abcvorschule.ui.theme.WoodPost
import app.abcvorschule.ui.theme.WoodWarm

/** Deliberately not named PathSignNode — a sibling object and composable with the
 *  same name compiles, but reads like a typo at every call site. */
object PathSignDimens {
    val BoardWidth = 136.dp
    val BoardHeight = 86.dp
    val PostHeight = 30.dp

    /** Board plus post — the whole thing is one touch target. */
    val TotalHeight = BoardHeight + PostHeight
}

/**
 * A lesson as a wooden signpost standing on the trail: the grapheme large, three
 * of the lesson's own picture words below it. Locked signs keep the emojis as
 * near-invisible silhouettes — enough to make a child curious, not enough to
 * give anything away.
 */
@Composable
fun PathSignNode(
    label: String,
    emojis: List<String>,
    state: LessonState,
    highlighted: Boolean,
    index: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playable = LessonGating.isPlayable(state)
    val board = when (state) {
        LessonState.Mastered -> WoodWarm
        LessonState.Available, LessonState.InProgress -> WoodMid
        LessonState.Locked, LessonState.Planned -> WoodDark
    }
    val ring: Color = when (state) {
        LessonState.Mastered, LessonState.Available -> SoftMint
        LessonState.InProgress -> SoftSky
        LessonState.Locked, LessonState.Planned -> MutedText.copy(alpha = 0.28f)
    }
    val labelColor = when (state) {
        LessonState.Mastered, LessonState.Available, LessonState.InProgress -> SoftSand
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

    Column(
        modifier = modifier
            // A hand-nailed sign is never perfectly straight. Deterministic, so it
            // does not re-tilt on recomposition.
            .graphicsLayer { rotationZ = 3f * PathNoise.signed(index, salt = 5) }
            .clickable(onClick = onClick)
            .semantics { contentDescription = "$nodeDesc $label, $stateDesc" }
            .testTag("path_node_$label"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .width(PathSignDimens.BoardWidth)
                .height(PathSignDimens.BoardHeight)
                .background(board, RoundedCornerShape(14.dp))
                .border(
                    width = 4.dp,
                    color = ring.copy(alpha = ring.alpha * ringAlpha),
                    shape = RoundedCornerShape(14.dp),
                ),
        ) {
            Nail(Modifier.align(Alignment.TopStart).padding(10.dp))
            if (state == LessonState.Mastered) {
                IconStar(
                    tint = SoftGold,
                    size = 16.dp,
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            } else {
                Nail(Modifier.align(Alignment.TopEnd).padding(10.dp))
            }

            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleLarge,
                    color = labelColor,
                )
                // Fixed height even when empty, so authored and planned signs stay
                // the same size and the path does not jump.
                Row(
                    modifier = Modifier
                        .height(22.dp)
                        // Without this TalkBack reads "mouse tree ant" into the
                        // middle of the state announcement.
                        .clearAndSetSemantics {},
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    emojis.forEach { emoji ->
                        Text(
                            text = emoji,
                            fontSize = 16.sp,
                            color = Color.Unspecified,
                            modifier = Modifier.graphicsLayer {
                                alpha = if (playable) 1f else 0.18f
                            },
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .width(10.dp)
                .height(PathSignDimens.PostHeight)
                .background(WoodPost, RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp)),
        )
    }
}

@Composable
private fun Nail(modifier: Modifier = Modifier) {
    Box(modifier.size(6.dp).background(WoodPost, CircleShape))
}
```

- [ ] **Step 2: Build prüfen**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL. Falls `R.string.path_node` / `lesson_mastered` / `lesson_available` / `lesson_locked` nicht auflösen: sie werden heute schon von `PathScreen.kt` benutzt, existieren also — dann stimmt ein Import nicht.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/path/PathSignNode.kt
git commit -m "feat(path): add wooden signpost node with lesson emojis"
```

---

### Task 6: `PathScreen` verdrahten

Hier wird das Feature zum ersten Mal sichtbar: Hintergrund, Trail und Schilder ersetzen Kreise und gerade Linien, und die Emoji-Map kommt aus dem ViewModel.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/path/PathScreen.kt`
- Modify: `app/src/main/java/app/abcvorschule/session/SessionViewModel.kt` (nach `highlightedLessonId()`, ca. Zeile 96)
- Modify: `app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt:100-114`

**Interfaces:**
- Consumes: alles aus Tasks 1–5
- Produces: `PathScreen(..., emojisByLessonId: Map<String, List<String>>, ...)`, `SessionViewModel.lessonEmojis(): Map<String, List<String>>`

- [ ] **Step 1: ViewModel-Accessor ergänzen**

In `SessionViewModel.kt` direkt nach `highlightedLessonId()` einfügen:

```kotlin
    /** Signpost emojis per lesson id — derived once, the pack does not change. */
    fun lessonEmojis(): Map<String, List<String>> =
        if (this::pack.isInitialized) {
            pack.lessons.associate { it.id to LessonEmojis.forLesson(pack, it) }
        } else {
            emptyMap()
        }
```

Import ergänzen: `import app.abcvorschule.content.LessonEmojis`

- [ ] **Step 2: `PathScreen.kt` neu schreiben**

Ersetze den Inhalt vollständig. `PathNode` entfällt (ersetzt durch `PathSignNode` aus Task 5), `NodeSize` entfällt.

```kotlin
package app.abcvorschule.ui.path

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.abcvorschule.content.Lesson
import app.abcvorschule.progress.LessonGating
import app.abcvorschule.progress.LessonState
import app.abcvorschule.ui.components.IconStar
import app.abcvorschule.ui.shell.ParentGateButton
import app.abcvorschule.ui.theme.AbcDimens
import app.abcvorschule.ui.theme.MutedText
import app.abcvorschule.ui.theme.SoftSand

/**
 * Fibel path: the app's start screen. A dotted trail winds through a night
 * landscape from signpost to signpost. Node labels stay minimal (a letter, a
 * syllable, a grapheme) — never an instruction the child would have to read.
 */
@Composable
fun PathScreen(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    emojisByLessonId: Map<String, List<String>>,
    highlightedLessonId: String?,
    points: Int,
    onOpenLesson: (String) -> Unit,
    onLockedTap: () -> Unit,
    onParentGateUnlocked: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()

    Box(modifier.fillMaxSize()) {
        PathBackground(scrollOffset = { scrollState.value })

        Column(Modifier.fillMaxSize()) {
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
                    .verticalScroll(scrollState)
                    .testTag("path_scroll"),
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(
                            with(density) {
                                PathGeometry.contentHeight(lessons.size, spacingPx, marginPx).toDp()
                            },
                        ),
                ) {
                    val widthPx = with(density) { maxWidth.toPx() }
                    val nodePoints = PathGeometry.points(lessons.size, widthPx, spacingPx, marginPx)
                    val walkedUpTo = lessons.indexOfLast {
                        val state = states[it.id]
                        state == LessonState.Mastered || state == LessonState.InProgress
                    }
                    val dots = PathTrail.dots(
                        polyline = PathTrail.polyline(nodePoints),
                        walkedUpTo = walkedUpTo,
                        spacing = with(density) { PathTrail.DefaultDotSpacing.dp.toPx() },
                        radius = with(density) { PathTrail.DefaultDotRadius.dp.toPx() },
                    )

                    Canvas(Modifier.fillMaxSize()) {
                        dots.forEach { dot ->
                            drawCircle(
                                color = if (dot.walked) {
                                    SoftSand.copy(alpha = 0.45f)
                                } else {
                                    MutedText.copy(alpha = 0.16f)
                                },
                                radius = dot.radius,
                                center = Offset(dot.x, dot.y),
                            )
                        }
                    }

                    PathSigns(
                        lessons = lessons,
                        states = states,
                        emojisByLessonId = emojisByLessonId,
                        highlightedLessonId = highlightedLessonId,
                        points = nodePoints,
                        onOpenLesson = onOpenLesson,
                        onLockedTap = onLockedTap,
                    )
                }
            }
        }
    }
}

@Composable
private fun PathSigns(
    lessons: List<Lesson>,
    states: Map<String, LessonState>,
    emojisByLessonId: Map<String, List<String>>,
    highlightedLessonId: String?,
    points: List<PathPoint>,
    onOpenLesson: (String) -> Unit,
    onLockedTap: () -> Unit,
) {
    val density = LocalDensity.current
    val halfWidth = with(density) { (PathSignDimens.BoardWidth / 2).toPx() }
    // The geometry point is where the post meets the ground, so the sign is drawn
    // fully above it and the trail passes below the board instead of through it.
    val fullHeight = with(density) { PathSignDimens.TotalHeight.toPx() }

    lessons.forEachIndexed { index, lesson ->
        val point = points.getOrNull(index) ?: return@forEachIndexed
        val state = states[lesson.id] ?: LessonState.Locked
        PathSignNode(
            label = lesson.nodeLabel,
            emojis = emojisByLessonId[lesson.id].orEmpty(),
            state = state,
            highlighted = lesson.id == highlightedLessonId,
            index = index,
            modifier = Modifier.offset(
                x = with(density) { (point.x - halfWidth).toDp() },
                y = with(density) { (point.y - fullHeight).toDp() },
            ),
            onClick = {
                if (LessonGating.isPlayable(state)) onOpenLesson(lesson.id) else onLockedTap()
            },
        )
    }
}
```

- [ ] **Step 3: `TaskShell.kt` anpassen**

In `TaskShell.kt` den `PathScreen(`-Aufruf (aktuell Zeile 100) um einen Parameter ergänzen — direkt nach `states = viewModel.lessonStates(),`:

```kotlin
                    emojisByLessonId = viewModel.lessonEmojis(),
```

- [ ] **Step 4: Build und komplette Testsuite**

Run: `./gradlew :app:assembleDebug :app:testDebugUnitTest`
Expected: BUILD SUCCESSFUL, alle Tests grün.

- [ ] **Step 5: Manueller Smoke-Test**

Es gibt keine androidTests — das visuelle Ergebnis lässt sich nur so prüfen. App auf Emulator oder Gerät starten und abhaken:

- Der Pfad scrollt über alle 26 Lektionen ruckelfrei; die Hügel driften langsamer als der Pfad.
- Kein Schild überlappt ein anderes, auch nicht auf einem schmalen Gerät (z.B. 360dp breit).
- Der Trail läuft unter den Schildern durch, nicht quer durchs Brett.
- Der zurückgelegte Teil des Trails ist sichtbar wärmer als der Rest.
- Gesperrte Schilder: Emojis nur als Silhouette erkennbar, nicht entzifferbar.
- Tippen auf ein gesperrtes Schild spricht weiterhin „Das üben wir später."
- Der pulsierende Rahmen sitzt auf der nächsten spielbaren Lektion.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/path/PathScreen.kt app/src/main/java/app/abcvorschule/session/SessionViewModel.kt app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt
git commit -m "feat(path): render the path as a night trail with signposts"
```

---

### Task 7: Doku nachziehen

`AGENTS.md` verlangt, dass UX-Regeländerungen in den Prinzipien landen.

**Files:**
- Modify: `docs/PRODUCT_PRINCIPLES.md` (§4 Content-Graph, §5 Session-Modell)

- [ ] **Step 1: §5 anpassen**

Ersetze in `docs/PRODUCT_PRINCIPLES.md` die Zeile

```
- **Pfad-Screen ist der Einstieg** (winkende S-Kurve, ein Knoten pro Lektion, Label = Graphem).
```

durch

```
- **Pfad-Screen ist der Einstieg**: ein gepunkteter Trittspuren-Weg durch eine Nachtlandschaft
  (Verlauf, Sterne, Hügel mit Parallaxe — dark-only bleibt Prinzip). Ein Wegweiser-Schild pro
  Lektion, Label = Graphem, darunter drei Emojis aus dem Bildwortschatz der Lektion.
  Der bereits zurückgelegte Teil des Weges ist wärmer gezeichnet als der Rest.
  Gesperrte Schilder zeigen ihre Emojis nur als Silhouette.
```

- [ ] **Step 2: §4 ergänzen**

Ergänze in `docs/PRODUCT_PRINCIPLES.md` §4 (Content-Graph) nach der ersten Zeile:

```
- Atom-Emojis werden auch außerhalb der Trainer verwendet: die Pfad-Schilder zeigen drei
  Emojis je Lektion, abgeleitet aus sound_position → word_build → count_add → sentence_order
  (deterministisch, über den Emoji-Glyph dedupliziert). `letter_trace.rewardEmoji` bleibt
  bewusst außen vor — er ist die Belohnung des Trainers und wird nicht vorweggenommen.
```

- [ ] **Step 3: Commit**

```bash
git add docs/PRODUCT_PRINCIPLES.md
git commit -m "docs: describe the night-trail path screen in the principles"
```

---

## Nicht in diesem Plan

**Auto-Scroll zur aktuellen Lektion.** Der Pfad ist mit 26 Lektionen mehrere Screens lang und startet immer oben; das Kind muss selbst zum pulsierenden Schild scrollen. Das ist eine bestehende Lücke, die dieses Redesign weder verursacht noch behebt, und sie ändert Verhalten (Scrollposition, Rücksprung aus einer Lektion) statt nur Optik. Gehört in ein eigenes Ticket.
