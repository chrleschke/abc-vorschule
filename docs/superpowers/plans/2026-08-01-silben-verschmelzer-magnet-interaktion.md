# Silben-Verschmelzer „Magnet-Buchstaben" Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die Schiebe-Interaktion des Silben-Verschmelzers kindgerecht machen: beide Kacheln schiebbar (symmetrisch aufeinander zu), sichtbare Schiebespur, Idle-Anstupser, Magnet-Schnappen, Tipp = vorlesen + anstupsen; der abstrakte `→|`-Button entfällt.

**Architecture:** `MergeProgress` bleibt das reine, unit-getestete Logik-Objekt (Fortschritts-Fraction, Drag-Akkumulation von beiden Seiten, Tipp-Schritt, Magnetzone). `SyllableMergeTrainer` treibt daraus ein `Animatable` (Drag = `snapTo`, Magnet/Rückgleiten/Anstupser = `animateTo` mit Spring) und zeichnet die Schiebespur als Canvas hinter den Kacheln. Kein Content-/Schema-/Validator-Eingriff.

**Tech Stack:** Kotlin, Jetpack Compose (Animatable, rememberInfiniteTransition, Canvas), JUnit4.

**Spec:** `docs/superpowers/specs/2026-08-01-silben-verschmelzer-interaktion-design.md`

## Global Constraints

- Produktprinzipien §2/§7: kein Lese-Chrome, antippbare Items werden vorgelesen, Snap-back ohne Strafe, ruhiges dunkles UI.
- Erfolgsfluss unverändert: `onResult(true, false, scoredIds)` genau einmal beim Verschmelzen; das Framework spricht `resultDisplay` und zeigt den Stern — der Trainer spricht die Silbe nicht selbst.
- Konstanten: `AttractFraction = 0.6f`, `TapStep = 0.3f`, `CommitFraction = 0.98f` (Kontakt beim Drag), `FloeGap = 120.dp`, Idle-Anstupser 10.dp nach 3500 ms.
- Verifikation: `./gradlew :app:testDebugUnitTest :app:assembleDebug` grün.

---

### Task 1: MergeProgress-Logik (TDD)

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SyllableMergeTrainer.kt:39-53` (nur das `MergeProgress`-Objekt)
- Test: `app/src/test/java/app/abcvorschule/ui/exercise/MergeProgressTest.kt` (ersetzen)

**Interfaces:**
- Produces:
  - `MergeProgress.applyDrag(fraction: Float, deltaPx: Float, travelPx: Float, fromRightTile: Boolean): Float`
  - `MergeProgress.stepped(fraction: Float): Float`
  - `MergeProgress.isContact(fraction: Float): Boolean`
  - `MergeProgress.shouldAttract(fraction: Float): Boolean`
  - `MergeProgress.glow(fraction: Float): Float` (unverändert)
  - Konstanten `CommitFraction = 0.98f`, `AttractFraction = 0.6f`, `TapStep = 0.3f`
- Entfällt: `MergeProgress.fraction(...)`, `MergeProgress.isMerged(...)` (keine Nutzer außer Trainer + Test).

- [ ] **Step 1: Failing Tests schreiben** — `MergeProgressTest.kt` komplett ersetzen:

```kotlin
package app.abcvorschule.ui.exercise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MergeProgressTest {
    @Test
    fun draggingTheConsonantRightwardsClosesTheGap() {
        assertEquals(0.5f, MergeProgress.applyDrag(0f, 50f, 100f, fromRightTile = false), 0.001f)
        assertEquals(0.75f, MergeProgress.applyDrag(0.5f, 25f, 100f, fromRightTile = false), 0.001f)
    }

    @Test
    fun draggingTheVowelLeftwardsClosesTheGapToo() {
        assertEquals(0.5f, MergeProgress.applyDrag(0f, -50f, 100f, fromRightTile = true), 0.001f)
    }

    @Test
    fun draggingAwayFromTheMiddleOpensTheGapAndClamps() {
        assertEquals(0.25f, MergeProgress.applyDrag(0.5f, -25f, 100f, fromRightTile = false), 0.001f)
        assertEquals(0f, MergeProgress.applyDrag(0.2f, -80f, 100f, fromRightTile = false), 0.001f)
        assertEquals(1f, MergeProgress.applyDrag(0.9f, 80f, 100f, fromRightTile = false), 0.001f)
    }

    @Test
    fun zeroTravelDoesNotDivideByZero() {
        assertEquals(0.4f, MergeProgress.applyDrag(0.4f, 30f, 0f, fromRightTile = false), 0.001f)
    }

    @Test
    fun twoTapsReachTheMagnetZone() {
        val afterFirst = MergeProgress.stepped(0f)
        assertFalse(MergeProgress.shouldAttract(afterFirst))
        val afterSecond = MergeProgress.stepped(afterFirst)
        assertTrue(MergeProgress.shouldAttract(afterSecond))
    }

    @Test
    fun steppingNeverOvershoots() {
        assertEquals(1f, MergeProgress.stepped(0.9f), 0.001f)
    }

    @Test
    fun releaseAttractsOnlyInsideTheMagnetZone() {
        assertFalse(MergeProgress.shouldAttract(0f))
        assertFalse(MergeProgress.shouldAttract(0.59f))
        assertTrue(MergeProgress.shouldAttract(MergeProgress.AttractFraction))
        assertTrue(MergeProgress.shouldAttract(1f))
    }

    @Test
    fun contactRequiresTheTilesToActuallyTouch() {
        assertFalse(MergeProgress.isContact(0.9f))
        assertTrue(MergeProgress.isContact(MergeProgress.CommitFraction))
        assertTrue(MergeProgress.isContact(1f))
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

- [ ] **Step 2: Tests laufen lassen, Fehlschlag bestätigen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.MergeProgressTest"`
Expected: Kompilierfehler (`applyDrag` unbekannt) — zählt als Fail.

- [ ] **Step 3: `MergeProgress` implementieren** (im Trainer-File, Objekt ersetzen):

```kotlin
object MergeProgress {
    /** Contact while dragging: the tiles physically touch and merge immediately. */
    const val CommitFraction = 0.98f

    /** Release at or past this and the magnet pulls the tiles the rest of the way. */
    const val AttractFraction = 0.6f

    /** One tap nudges the tiles this much closer; two taps reach the magnet zone. */
    const val TapStep = 0.3f

    /** Shared closing progress: either tile drags it, the right one with inverted sign. */
    fun applyDrag(fraction: Float, deltaPx: Float, travelPx: Float, fromRightTile: Boolean): Float {
        if (travelPx <= 0f) return fraction
        val towardsMiddle = if (fromRightTile) -deltaPx else deltaPx
        return (fraction + towardsMiddle / travelPx).coerceIn(0f, 1f)
    }

    fun stepped(fraction: Float): Float = (fraction + TapStep).coerceIn(0f, 1f)

    fun isContact(fraction: Float): Boolean = fraction >= CommitFraction

    fun shouldAttract(fraction: Float): Boolean = fraction >= AttractFraction

    /** Visual stand-in for the intensifying sound: 0.25 at rest, 1.0 on contact. */
    fun glow(fraction: Float): Float = (0.25f + 0.75f * fraction.coerceIn(0f, 1f))
}
```

(Der restliche Trainer kompiliert danach noch nicht gegen die neue API — Task 2 baut ihn um; für den Test-Lauf reicht es, wenn Task 1+2 zusammen committet werden **oder** der Trainer in Task 1 minimal mitgezogen wird. Vorgehen: Task 1 und 2 in einem Branch-Zustand entwickeln, Tests nach Task 2 grün laufen lassen, dann **ein** Commit pro Task in Reihenfolge — Task-1-Commit enthält Logik + Tests + den umgebauten Trainer-Rumpf nur, falls sonst nicht kompilierbar; praktisch: beide Tasks bauen, dann zwei Commits mit `git add -p` ist hier Overkill → **ein gemeinsamer Feature-Commit nach Task 2 ist akzeptiert**.)

- [ ] **Step 4: weiter mit Task 2, dann Tests grün laufen lassen**

### Task 2: SyllableMergeTrainer-UI

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/SyllableMergeTrainer.kt` (kompletter Umbau unterhalb von `MergeProgress`)

**Interfaces:**
- Consumes: `MergeProgress` aus Task 1; `ExerciseStage`, `TaskPromptChrome`, `Floe`-Stil (SoftMint/SoftSky/NightElevated), `SyllableFrameSizing`, `AbcDimens.letterFrame`.
- Produces: unveränderte Composable-Signatur `SyllableMergeTrainer(round, roundIndex, ttsAvailable, speaking, onSpeakPrompt, onSpeak, onResult, modifier)`. TestTags `merge_left`, `merge_right`, `merge_result` bleiben; `merge_join` entfällt.

- [ ] **Step 1: Trainer umbauen** — Zielzustand des Files (nach `MergeProgress` aus Task 1):

```kotlin
private val FloeGap = 120.dp
private val IdleNudge = 10.dp
private const val IdleNudgeDelayMs = 3_500L
private const val TrackWaveMs = 1_400

@Composable
fun SyllableMergeTrainer(
    round: SyllableMergeRound,
    roundIndex: Int,
    ttsAvailable: Boolean,
    speaking: Boolean,
    onSpeakPrompt: () -> Unit,
    onSpeak: (String) -> Unit,
    onResult: (correct: Boolean, resolved: Boolean, atomIds: List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val roundKey = "$roundIndex-${round.leftAtomId}-${round.rightAtomId}"
    val density = LocalDensity.current
    // Each tile travels half the gap, so they meet in the middle; the dragged
    // tile stays 1:1 under the finger while its partner mirrors the motion.
    val tileTravelPx = with(density) { (FloeGap / 2).toPx() }
    val idleNudgePx = with(density) { IdleNudge.toPx() }
    val scope = rememberCoroutineScope()
    val fraction = remember(roundKey) { Animatable(0f) }
    val idleNudge = remember(roundKey) { Animatable(0f) }
    var merged by remember(roundKey) { mutableStateOf(false) }
    var dragging by remember(roundKey) { mutableStateOf(false) }
    var interactions by remember(roundKey) { mutableIntStateOf(0) }
    val glow by animateFloatAsState(
        targetValue = if (merged) 1f else MergeProgress.glow(fraction.value),
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

    fun settle() {
        dragging = false
        interactions++
        if (merged) return
        scope.launch {
            if (MergeProgress.shouldAttract(fraction.value)) {
                fraction.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                commit()
            } else {
                // No penalty: a short pull just glides back.
                fraction.animateTo(
                    0f,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                )
            }
        }
    }

    fun speakTile(fromRightTile: Boolean) {
        onSpeak(if (fromRightTile) round.rightDisplay else round.stretchTts)
    }

    // Tap = read the sound aloud and nudge the tiles one step closer: the
    // motor-skill-friendly path to the merge, replacing the old "→|" button.
    fun nudgeTap(fromRightTile: Boolean) {
        interactions++
        speakTile(fromRightTile)
        if (merged) return
        scope.launch {
            idleNudge.snapTo(0f)
            val target = MergeProgress.stepped(fraction.value)
            if (MergeProgress.shouldAttract(target)) {
                fraction.animateTo(1f, spring(stiffness = Spring.StiffnessMedium))
                commit()
            } else {
                fraction.animateTo(
                    target,
                    spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                )
            }
        }
    }

    fun Modifier.mergeDrag(fromRightTile: Boolean): Modifier = pointerInput(roundKey) {
        detectDragGestures(
            onDragStart = {
                dragging = true
                scope.launch { idleNudge.snapTo(0f) }
                speakTile(fromRightTile)
            },
            onDrag = { change, amount ->
                change.consume()
                scope.launch {
                    fraction.snapTo(
                        MergeProgress.applyDrag(fraction.value, amount.x, tileTravelPx, fromRightTile),
                    )
                }
                if (MergeProgress.isContact(fraction.value)) {
                    dragging = false
                    commit()
                }
            },
            onDragEnd = { settle() },
            onDragCancel = { settle() },
        )
    }

    // Invitation to slide: after a quiet moment the tiles breathe towards each
    // other once, and keep reminding until the child takes over.
    LaunchedEffect(roundKey, merged, dragging, interactions) {
        if (merged || dragging) return@LaunchedEffect
        while (true) {
            delay(IdleNudgeDelayMs)
            idleNudge.animateTo(1f, spring(stiffness = Spring.StiffnessLow))
            idleNudge.animateTo(
                0f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
            )
        }
    }

    val resultScale = remember(roundKey) { Animatable(0.6f) }
    LaunchedEffect(merged) {
        if (merged) {
            resultScale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
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
            if (merged) {
                Floe(
                    label = round.resultDisplay,
                    glow = 1f,
                    frozen = true,
                    onTap = { onSpeak(round.resultDisplay) },
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = resultScale.value
                            scaleY = resultScale.value
                        }
                        .testTag("merge_result"),
                )
            } else {
                val inwardPx = fraction.value * tileTravelPx + idleNudge.value * idleNudgePx
                Box(contentAlignment = Alignment.Center) {
                    MergeTrack(
                        progress = fraction.value,
                        modifier = Modifier
                            .width(FloeGap)
                            .height(AbcDimens.letterFrame),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Floe(
                            label = round.leftDisplay,
                            glow = glow,
                            frozen = false,
                            onTap = { nudgeTap(fromRightTile = false) },
                            modifier = Modifier
                                .offset { IntOffset(inwardPx.roundToInt(), 0) }
                                .mergeDrag(fromRightTile = false)
                                .testTag("merge_left"),
                        )
                        Spacer(Modifier.width(FloeGap))
                        Floe(
                            label = round.rightDisplay,
                            glow = glow,
                            frozen = false,
                            onTap = { nudgeTap(fromRightTile = true) },
                            modifier = Modifier
                                .offset { IntOffset(-inwardPx.roundToInt(), 0) }
                                .mergeDrag(fromRightTile = true)
                                .testTag("merge_right"),
                        )
                    }
                }
            }
        },
        answers = {},
    )
}

/**
 * Dotted slide track between the tiles. A soft brightness wave travels from
 * both edges towards the middle — the wordless "push them together" cue —
 * and the whole track fades out as the tiles approach each other.
 */
@Composable
private fun MergeTrack(progress: Float, modifier: Modifier = Modifier) {
    val phase by rememberInfiniteTransition(label = "merge_track")
        .animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(TrackWaveMs, easing = LinearEasing)),
            label = "merge_track_phase",
        )
    Canvas(modifier) {
        val fade = (1f - progress).coerceIn(0f, 1f)
        if (fade <= 0f) return@Canvas
        val dotRadius = 3.dp.toPx()
        val dotCount = 7
        val stepX = size.width / (dotCount + 1)
        val centerY = size.height / 2f
        for (i in 1..dotCount) {
            val x = stepX * i
            // 0 at the edges, 1 in the middle — the wave chases this value.
            val toMiddle = 1f - kotlin.math.abs(x - size.width / 2f) / (size.width / 2f)
            val highlight = (1f - kotlin.math.abs(toMiddle - phase) * 3f).coerceIn(0f, 1f)
            drawCircle(
                color = SoftSky.copy(alpha = fade * (0.18f + 0.55f * highlight)),
                radius = dotRadius * (0.8f + 0.4f * highlight),
                center = Offset(x, centerY),
            )
        }
    }
}
```

`Floe` bleibt unverändert. Nicht mehr benötigte Imports entfernen (`clickable` bleibt — `Floe` nutzt es; `defaultMinSize`, `background`/`RoundedCornerShape` für den Button, `MaterialTheme`, `Text`-Button-Teile prüfen), neue Imports ergänzen: `Animatable`, `Spring`, `spring`, `tween`, `LinearEasing`, `infiniteRepeatable`, `rememberInfiniteTransition`, `animateFloat`, `Canvas`, `Offset`, `graphicsLayer`, `rememberCoroutineScope`, `mutableIntStateOf`, `kotlinx.coroutines.delay`, `kotlinx.coroutines.launch`, `Box`/`Alignment` (schon da).

- [ ] **Step 2: Unit-Tests laufen lassen**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.exercise.MergeProgressTest"`
Expected: PASS (alle 9 Tests).

- [ ] **Step 3: Vollständige Test-Suite + Build**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/app/abcvorschule/ui/exercise/SyllableMergeTrainer.kt app/src/test/java/app/abcvorschule/ui/exercise/MergeProgressTest.kt
git commit -m "feat(exercise): Silben-Verschmelzer als Magnet-Buchstaben-Interaktion"
```

### Task 3: Doku nachziehen

**Files:**
- Modify: `docs/PRODUCT_PRINCIPLES.md:43` (Trainer-3-Beschreibung in §3)

**Interfaces:** keine.

- [ ] **Step 1: §3 Punkt 3 ersetzen**

Alt:
```
3. **Silben-Verschmelzer** — Konsonant auf Vokal ziehen, Silbe entsteht.
```

Neu:
```
3. **Silben-Verschmelzer** — beide Laut-Kacheln sind schiebbar und wandern symmetrisch
  aufeinander zu (Magnet-Metapher); eine gepunktete Schiebespur mit einwärts laufender
  Lichtwelle und ein Idle-„Atmen" laden ohne Text zum Schieben ein. Ab 60 % Nähe schnappen
  die Kacheln zusammen, darunter gleiten sie straflos zurück. Ein Tipp auf eine Kachel
  liest ihren Laut vor **und** stupst sie einen Schritt (30 %) näher — zwei Tipps
  verschmelzen, es gibt keinen separaten Bestätigungs-Button.
```

- [ ] **Step 2: Commit**

```bash
git add docs/PRODUCT_PRINCIPLES.md
git commit -m "docs(prinzipien): Silben-Verschmelzer-Beschreibung an Magnet-Interaktion angepasst"
```

## Self-Review

- Spec-Abdeckung: beidseitiger Drag ✓ (Task 1/2), Spur ✓ (MergeTrack), Idle-Anstupser ✓, Magnet ✓ (settle/nudgeTap), Tap = vorlesen + anstupsen ✓, Button weg ✓ (answers = {}), Erfolgsfluss unverändert ✓ (commit → onResult), Scale-in der Ergebnis-Kachel ✓, Doku ✓ (Task 3).
- Keine Platzhalter; Typen konsistent (`applyDrag`/`stepped`/`shouldAttract`/`isContact` identisch in Test, Logik und UI).
