# Visuelles Konzept-Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Die App von dark-only auf ein helles, warmes, kinderfreundliches Theme umstellen, semantische Farbrollen einführen (Gold=Belohnung, Grün=richtig, Blau=Fortschritt, Koralle=CTA), ein Haptik-Vokabular verdrahten und mehr Erfolgsmomente (Partikel-Burst, Progress-Puls, Konfetti) einbauen.

**Architecture:** Ein einziges helles `lightColorScheme` in `ui/theme/`; semantische Farbkonstanten werden direkt (nicht über `primary`) referenziert, wo die Bedeutung zählt. Neues Modul `AbcHaptics` (Vibrator-Wrapper hinter CompositionLocal). Effekt-Komponenten (Burst-Partikel, Konfetti) als Canvas-Composables in `ui/rewards/` mit deterministisch testbarer Geometrie in reinen Funktionen.

**Tech Stack:** Kotlin, Jetpack Compose (Material3), Android Vibrator/VibrationEffect (minSdk 26), JUnit-Unit-Tests unter `app/src/test/`.

**Spec:** `docs/superpowers/specs/2026-08-01-visual-concept-redesign-design.md`

## Global Constraints

- Kein reines Weiß (`#FFFFFF`) und kein reines Schwarz als Fläche — Creme/warme Tinte.
- Sterne/Punkte/Belohnung sind **immer** `StarGold`; „richtig/erledigt" ist **immer** `LeafGreen`; Progress-Füllung ist **immer** `SkyBlue`; CTA ist `SunCoral`. Nie `primary` für Sterne oder Progress verwenden.
- Text-Kontrast ≥ 4.5:1 gegen seine Fläche (WarmInk/WarmMuted auf Creme-Tönen erfüllen das).
- Buttons: keine Emojis, nur Vektor/ASCII (bestehende Regel).
- Kind-Feedback bleibt Audio-first; Haptik ergänzt Ton, ersetzt ihn nie.
- Layouts/Größen nicht ändern (Testgerät läuft font_scale 1.3) — nur Farben, Effekte, Haptik.
- minSdk 26: `VibrationEffect.createPredefined` nur ab API 29 → Fallback `createOneShot`/`createWaveform`.
- Nach jedem Task: `./gradlew :app:testDebugUnitTest` grün, Commit auf diesem Branch.
- Neue Farbnamen exakt wie hier definiert (Task 1 „Produces") — alle späteren Tasks verwenden sie namensgleich.

---

### Task 1: Theme-Fundament „Warmer Tag"

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/theme/Color.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/theme/Theme.kt`
- Modify: `app/src/main/res/values/themes.xml`
- Modify: `app/src/main/res/values/colors.xml`
- Modify: `app/src/main/res/drawable/ic_launcher_foreground.xml`
- Modify: `app/src/main/java/app/abcvorschule/MainActivity.kt` (enableEdgeToEdge, ~Zeile 26)
- Modify: `app/src/main/java/app/abcvorschule/ui/components/AbcButtons.kt` (Progress-Bar Zeilen ~163–187, Speaker `Color.Gray` ~Zeile 105)
- Modify: `app/src/main/java/app/abcvorschule/ui/exercise/MathExercise.kt:21` (toter `SoftSand`-Import)

**Interfaces:**
- Produces (Farbkonstanten in `ui/theme/Color.kt`, von allen späteren Tasks per Name verwendet):
  - `val Cream = Color(0xFFFBF3E4)` (background)
  - `val CreamPanel = Color(0xFFF4E8D0)` (surface)
  - `val CreamElevated = Color(0xFFE9DBBD)` (surfaceVariant, leere Slots, Progress-Track)
  - `val WarmInk = Color(0xFF3D3427)` (Primärtext/onBackground/onSurface)
  - `val WarmMuted = Color(0xFF7C6F5A)` (Sekundärtext/onSurfaceVariant)
  - `val StarGold = Color(0xFFF0A818)` (Sterne/Punkte/Belohnung)
  - `val LeafGreen = Color(0xFF4E9B5E)` (richtig/erledigt; M3 primary)
  - `val SkyBlue = Color(0xFF4E8FC7)` (Fortschritt/aktiv; M3 secondary)
  - `val SunCoral = Color(0xFFE8794A)` (CTA/Lok; M3 tertiary)
  - `val ClayRed = Color(0xFFC4553F)` (M3 error, Fehlertext für Erwachsene)
  - Wood-Konstanten (`WoodDark/Mid/Warm` + Shades) und `SoftSand` (Schild-Schrift) bleiben unverändert bestehen.
  - Die alten Night-/Soft-Konstanten (`NightInk`, `NightPanel`, `NightElevated`, `NightDeep`, `NightHorizon`, `SoftMint`, `SoftCoral`, `SoftSky`, `SoftGold`, `MutedText`) **bleiben in diesem Task erhalten** (Trainer referenzieren sie noch); Entfernung in Task 10.

- [ ] **Step 1: Neue Palette in Color.kt ergänzen**

Oben in `Color.kt` die neuen Konstanten mit Doku einfügen (alte Konstanten stehen lassen). Kontrast-Kommentar: WarmInk auf Cream ≈ 9.9:1, WarmMuted auf Cream ≈ 4.6:1. Die Wood-Kontrastdoku (SoftSand-Schrift auf Brett) bleibt gültig — Schrift steht auf dem Brett, nicht auf dem Himmel; den einleitenden Satz „the path is looked at in a dark room" streichen/anpassen.

- [ ] **Step 2: Theme.kt auf lightColorScheme umstellen**

```kotlin
private val LightColors = lightColorScheme(
    primary = LeafGreen,
    onPrimary = Cream,
    secondary = SkyBlue,
    onSecondary = Cream,
    tertiary = SunCoral,
    onTertiary = Cream,
    error = ClayRed,
    onError = Cream,
    background = Cream,
    onBackground = WarmInk,
    surface = CreamPanel,
    onSurface = WarmInk,
    surfaceVariant = CreamElevated,
    onSurfaceVariant = WarmMuted,
    outline = WarmMuted,
    scrim = Color(0x66000000),
)
```

`AbcTheme` nutzt `LightColors`; Typografie unverändert.

- [ ] **Step 3: themes.xml und Edge-to-Edge**

`themes.xml`: `statusBarColor`/`navigationBarColor`/`windowBackground` auf `#FFFBF3E4`; `android:forceDarkAllowed` auf `false`; `android:windowLightStatusBar=true` ergänzen (und `windowLightNavigationBar` in einem `values-v27`-Overlay oder direkt, da minSdk 26 → Attribut erst ab 27: nutze `values-v27/themes.xml` mit vollständiger Theme-Kopie oder setze es nur programmatisch). Einfachste robuste Lösung: in `MainActivity.kt` `enableEdgeToEdge(statusBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT), navigationBarStyle = SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT))` — das überschreibt zur Laufzeit die XML-Werte und stellt dunkle Icons sicher.

- [ ] **Step 4: Launcher-Icon**

`colors.xml`: `ic_launcher_background` auf `#FBF3E4`. `ic_launcher_foreground.xml`: `#7EC8A3` → `#E8794A` (SunCoral), `#F2E8CF` → `#3D3427` (WarmInk).

- [ ] **Step 5: AbcProgressBar entkoppeln + Grau fixen**

In `AbcProgressBar` (AbcButtons.kt ~163–187): Track `Color(0xFF2A3A4F)` → `CreamElevated`, Füllung `Color(0xFF7EC8A3)` → `SkyBlue` (Imports aus `ui.theme`). Speaker-Icon-Tint `Color.Gray` (~Zeile 105) → `MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)` (wie Zeile 133). Toten `SoftSand`-Import in `MathExercise.kt:21` entfernen.

- [ ] **Step 6: Build + Tests**

Run: `./gradlew :app:testDebugUnitTest :app:assembleDebug`
Expected: grün. (App sieht übergangsweise gemischt aus — Trainer folgen in Task 3–5.)

- [ ] **Step 7: Commit**

```bash
git add -A && git commit -m "feat(theme): helles Warmer-Tag-Theme mit semantischen Farbrollen"
```

---

### Task 2: AbcHaptics-Modul

**Files:**
- Create: `app/src/main/java/app/abcvorschule/ui/rewards/AbcHaptics.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/rewards/AbcHapticsPatternTest.kt`

**Interfaces:**
- Produces:
  - `interface AbcHaptics { fun tick(); fun success(); fun celebrate(); fun nudge() }`
  - `val LocalAbcHaptics: androidx.compose.runtime.ProvidableCompositionLocal<AbcHaptics>` (Default: No-Op)
  - `fun rememberAbcHaptics(): AbcHaptics` — @Composable Factory über `LocalContext`
  - Reine, testbare Muster-Funktion: `object HapticPatterns { fun timingsFor(verb: HapticVerb): LongArray; fun amplitudesFor(verb: HapticVerb): IntArray }` mit `enum class HapticVerb { Tick, Success, Celebrate, Nudge }`
- Consumes: nichts aus anderen Tasks.

- [ ] **Step 1: Failing Test für die Muster schreiben**

```kotlin
package app.abcvorschule.ui.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AbcHapticsPatternTest {
    @Test
    fun `tick ist ein einzelner kurzer puls`() {
        assertEquals(1, HapticPatterns.timingsFor(HapticVerb.Tick).size)
        assertTrue(HapticPatterns.timingsFor(HapticVerb.Tick).first() in 15L..40L)
    }

    @Test
    fun `success ist ein doppelpuls`() {
        // waveform: [puls, pause, puls]
        assertEquals(3, HapticPatterns.timingsFor(HapticVerb.Success).size)
    }

    @Test
    fun `celebrate ist ein dreifachpuls unter 500ms gesamt`() {
        val t = HapticPatterns.timingsFor(HapticVerb.Celebrate)
        assertEquals(5, t.size) // puls,pause,puls,pause,puls
        assertTrue(t.sum() <= 500L)
    }

    @Test
    fun `amplituden passen zur laenge der timings`() {
        HapticVerb.entries.forEach { v ->
            assertEquals(
                HapticPatterns.timingsFor(v).size,
                HapticPatterns.amplitudesFor(v).size,
            )
        }
    }

    @Test
    fun `nudge ist schwaecher als tick`() {
        assertTrue(
            HapticPatterns.amplitudesFor(HapticVerb.Nudge).max() <
                HapticPatterns.amplitudesFor(HapticVerb.Tick).max(),
        )
    }
}
```

- [ ] **Step 2: Test läuft rot**

Run: `./gradlew :app:testDebugUnitTest --tests "app.abcvorschule.ui.rewards.AbcHapticsPatternTest"`
Expected: FAIL (HapticPatterns unbekannt).

- [ ] **Step 3: Implementierung**

```kotlin
package app.abcvorschule.ui.rewards

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Haptik-Vokabular der App. Vier Verben, damit jede Stelle dieselbe Sprache
 * spricht: tick = kleiner Sammel-Erfolg, success = Aufgabe richtig,
 * celebrate = Lektions-/Batterie-Feier, nudge = sanfte Korrektur.
 * Haptik ergänzt den Ton (Blips/Chime), sie ersetzt ihn nie.
 */
interface AbcHaptics {
    fun tick()
    fun success()
    fun celebrate()
    fun nudge()
}

enum class HapticVerb { Tick, Success, Celebrate, Nudge }

/**
 * Reine Muster-Definition, getrennt vom Vibrator, damit sie unit-testbar ist.
 * timings/amplitudes sind createWaveform-kompatibel (erste Zelle ist ein Puls,
 * kein Delay — daher ungerade Länge: puls[,pause,puls...]).
 */
object HapticPatterns {
    fun timingsFor(verb: HapticVerb): LongArray = when (verb) {
        HapticVerb.Tick -> longArrayOf(25)
        HapticVerb.Success -> longArrayOf(35, 70, 55)
        HapticVerb.Celebrate -> longArrayOf(45, 90, 45, 90, 90)
        HapticVerb.Nudge -> longArrayOf(20)
    }

    fun amplitudesFor(verb: HapticVerb): IntArray = when (verb) {
        HapticVerb.Tick -> intArrayOf(180)
        HapticVerb.Success -> intArrayOf(160, 0, 220)
        HapticVerb.Celebrate -> intArrayOf(150, 0, 190, 0, 255)
        HapticVerb.Nudge -> intArrayOf(90)
    }
}

private class VibratorAbcHaptics(private val vibrator: Vibrator) : AbcHaptics {
    override fun tick() = play(HapticVerb.Tick)
    override fun success() = play(HapticVerb.Success)
    override fun celebrate() = play(HapticVerb.Celebrate)
    override fun nudge() = play(HapticVerb.Nudge)

    private fun play(verb: HapticVerb) {
        runCatching {
            val timings = HapticPatterns.timingsFor(verb)
            val amplitudes = HapticPatterns.amplitudesFor(verb)
            val effect = if (vibrator.hasAmplitudeControl()) {
                if (timings.size == 1) {
                    VibrationEffect.createOneShot(timings[0], amplitudes[0])
                } else {
                    // createWaveform(timings, amplitudes, -1) erwartet Delay-first;
                    // unsere Muster sind Puls-first, daher führende 0 einfügen.
                    VibrationEffect.createWaveform(
                        longArrayOf(0, *timings),
                        intArrayOf(0, *amplitudes),
                        -1,
                    )
                }
            } else {
                VibrationEffect.createOneShot(
                    timings.sum(),
                    VibrationEffect.DEFAULT_AMPLITUDE,
                )
            }
            vibrator.vibrate(effect)
        }
    }
}

private object NoOpAbcHaptics : AbcHaptics {
    override fun tick() = Unit
    override fun success() = Unit
    override fun celebrate() = Unit
    override fun nudge() = Unit
}

val LocalAbcHaptics = compositionLocalOf<AbcHaptics> { NoOpAbcHaptics }

@Composable
fun rememberAbcHaptics(): AbcHaptics {
    val context = LocalContext.current
    return remember(context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
                ?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() == true) VibratorAbcHaptics(vibrator) else NoOpAbcHaptics
    }
}
```

In `MainActivity.kt` (setContent-Block): `AbcTheme { ... }` um `CompositionLocalProvider(LocalAbcHaptics provides rememberAbcHaptics()) { ... }` ergänzen (rememberAbcHaptics innerhalb der Composition aufrufen).

- [ ] **Step 4: Tests grün**

Run: `./gradlew :app:testDebugUnitTest`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A && git commit -m "feat(haptik): AbcHaptics-Vokabular (tick/success/celebrate/nudge) mit CompositionLocal"
```

---

### Task 3: Rekalibrierung Chrome & einfache Screens

**Files:**
- Modify: `ui/shell/TaskShell.kt`, `ui/shell/ParentSheet.kt`, `ui/shell/ParentGate.kt`, `ui/shell/RewardSummaryScreen.kt`, `ui/exercise/TaskPromptChrome.kt`, `ui/exercise/NumberPad.kt`, `ui/exercise/VisualQuantityBoard.kt`, `ui/exercise/MathExercise.kt`, `ui/debug/TtsDebugScreen.kt`
  (alle unter `app/src/main/java/app/abcvorschule/`)

**Interfaces:**
- Consumes: Farbkonstanten aus Task 1.
- Produces: nichts Neues — reine Umfärbung.

Mapping (Fundstellen aus dem Farb-Inventar; beim Umsetzen die Datei lesen und alle Vorkommen konsistent behandeln):

| Stelle | Alt | Neu |
| --- | --- | --- |
| TaskShell.kt:67 Hintergrund | `NightInk` | `MaterialTheme.colorScheme.background` |
| TaskShell.kt:224,229 Punktestern+Zahl | `colorScheme.primary` | `StarGold` (Stern) / `WarmInk` (Zahl) |
| TaskShell.kt:80 Fehlertext | `colorScheme.tertiary` | `colorScheme.error` |
| TaskShell.kt:323 Runden-Dots | `SoftMint` / `MutedText.copy(0.3f)` | `LeafGreen` / `WarmMuted.copy(alpha = 0.35f)` |
| MathExercise.kt:85 Rechen-Prompt | `colorScheme.primary` | `WarmInk` (Aufgabe ist Text, kein Erfolgs-Signal) |
| NumberPad.kt:91–94 | `SoftMint`/`SoftSky`+`SoftSand` | gelöst: `LeafGreen`, neutral: `SkyBlue`; Text `WarmInk` |
| NumberPad.kt:101,107 Absenden | `primary`/`onPrimary` | `SunCoral`-Container, `Cream`-Icon (CTA-Rolle) |
| VisualQuantityBoard.kt:80 Kacheln | `SoftMint`(korrekt)/`NightElevated` | `LeafGreen` / `CreamElevated` |
| VisualQuantityBoard.kt:94 Zahl auf grüner Kachel | `NightInk` | `Cream` |
| VisualQuantityBoard.kt:114,176 Text | `SoftSand` | `WarmInk` |
| TaskPromptChrome.kt:42 | `SoftSand`/`MutedText.copy(0.35f)` | `WarmInk` / `WarmMuted.copy(alpha = 0.5f)` |
| RewardSummaryScreen.kt:178 Hintergrundstern | `primary.copy(alpha=0.12f)` | `StarGold.copy(alpha = 0.15f)` |
| ParentSheet/ParentGate | `onSurface`-Nutzung bleibt; prüfen, dass Checkbox/Radio mit neuem Scheme lesbar sind | ggf. nichts zu tun |
| TtsDebugScreen.kt (92,102,117–127,141,195,201,204,227–237,252,255) | Night*/Soft* | sinngemäß: Hintergrund `background`, Karten `surface`, Text `WarmInk`/`WarmMuted`, Border `SkyBlue.copy(alpha=0.5f)`, „bearbeitet" `StarGold` |

- [ ] **Step 1: Mapping umsetzen** (Dateien lesen, alle Night*/Soft*-Vorkommen in diesen Dateien ersetzen — auch nicht in der Tabelle gelistete, sinngemäß nach den Farbrollen aus den Global Constraints)
- [ ] **Step 2: Build + Tests** — `./gradlew :app:testDebugUnitTest :app:assembleDebug`, Expected: grün
- [ ] **Step 3: Commit** — `git add -A && git commit -m "feat(theme): Chrome, Rechnen und Shell auf Warmer-Tag-Palette"`

---

### Task 4: Rekalibrierung Trace, Lok & Silben

**Files:**
- Modify: `ui/exercise/LetterTraceTrainer.kt`, `ui/exercise/SoundPositionTrainer.kt`, `ui/exercise/SyllableMergeTrainer.kt`, `ui/exercise/TraceReward.kt` (falls Farben), jeweils unter `app/src/main/java/app/abcvorschule/`

**Interfaces:** Consumes Task-1-Farben. Produces nichts Neues.

Mapping:

| Stelle | Alt | Neu |
| --- | --- | --- |
| LetterTrace :313 Straßenband | `SoftMint`(aktiv)/`SoftSand`(inaktiv) | aktiv `SkyBlue`, inaktiv `WarmMuted` |
| LetterTrace :317 Band-Alpha | `0.30f/0.16f` auf dunkel | `0.45f/0.22f` auf hell (dunklere Bänder brauchen mehr Deckkraft, damit die Straße auf Creme lesbar bleibt) |
| LetterTrace :328 Füll-Lerp | `lerp(NightInk, SoftMint, fill)` | `lerp(CreamElevated, LeafGreen, fill)` |
| LetterTrace :351 Sterne | `SoftGold` aktiv / `.copy(0.28f)` inaktiv | `StarGold` aktiv / `StarGold.copy(alpha = 0.35f)` inaktiv |
| LetterTrace :359 Fahrzeug | `SoftCoral` | `SunCoral` |
| LetterTrace :218 Text | `SoftSand` | `WarmInk` |
| SoundPosition :119,190 Text | `SoftSand` | `WarmInk` |
| SoundPosition :168 Bildkarte | `SoftMint.copy(0.3f)`/`NightElevated` | `LeafGreen.copy(alpha = 0.25f)` / `CreamElevated` |
| SoundPosition :189–191 Waggons | `SoftCoral`/?/`SoftSky` | Waggonfarben: `SunCoral`, `StarGold`, `SkyBlue` (drei klar unterscheidbare, warme Töne) |
| SoundPosition :220–247 Aktiv-Washes | `SoftMint`-Washes | `LeafGreen`-Washes, Alphas +0.05–0.1 erhöhen |
| SoundPosition :266–287 Lok | `SoftCoral`-Körper, `SoftSand`-Räder | Körper `SunCoral`, Räder `WarmInk` |
| SoundPosition :292 Dampf | `Color.White.copy(0.35f*steam)` | `WarmMuted.copy(alpha = 0.4f * steam)` (warmgrauer Dampf, auf hell sichtbar) |
| SyllableMerge :304 Wellenpunkte | `SoftSky.copy(fade*(0.18f+0.55f*h))` | `SkyBlue.copy(alpha = fade * (0.30f + 0.55f * h))` |
| SyllableMerge :325 Scholle | `SoftMint.copy(0.22f)`/`NightElevated` | `LeafGreen.copy(alpha = 0.25f)` / `CreamElevated` |
| SyllableMerge :330 Glow | `(SoftMint|SoftSky).copy(glow)` | `(LeafGreen|SkyBlue).copy(glow)` |
| SyllableMerge :339 Text | `SoftMint`/`SoftSand` | `LeafGreen` / `WarmInk` |

- [ ] **Step 1: Mapping umsetzen** (Dateien vollständig lesen; alle weiteren Night*/Soft*-Vorkommen in diesen Dateien sinngemäß nach Farbrollen ersetzen; Code-Kommentare, die Dunkel-Kalibrierung begründen, mit anpassen)
- [ ] **Step 2: Build + Tests** — `./gradlew :app:testDebugUnitTest :app:assembleDebug`, Expected: grün
- [ ] **Step 3: Commit** — `git add -A && git commit -m "feat(theme): Trace, Auditiver Finder und Silben-Verschmelzer auf helle Palette"`

---

### Task 5: Rekalibrierung Wort/Satz/Jagd/Detektiv

**Files:**
- Modify: `ui/exercise/WordBuildTrainer.kt`, `ui/exercise/SentenceOrderTrainer.kt`, `ui/exercise/SymbolHuntTrainer.kt`, `ui/exercise/SymbolInWordTrainer.kt` unter `app/src/main/java/app/abcvorschule/`

**Interfaces:** Consumes Task-1-Farben. Produces nichts Neues.

Mapping:

| Stelle | Alt | Neu |
| --- | --- | --- |
| WordBuild :205 Karte | `SoftMint`(platziert)/`NightElevated` | `LeafGreen` / `CreamElevated` |
| WordBuild :214 Text auf platzierter Karte | `NightInk` | `Cream` |
| WordBuild :252 Slot gefüllt/leer | `SoftMint.copy(0.22f)`/`NightElevated` | `LeafGreen.copy(alpha = 0.22f)` / `CreamElevated` |
| WordBuild :257 Slot-Border | `SoftMint.copy(0.7f)`/`SoftSand.copy(0.35f)` | `LeafGreen.copy(alpha = 0.7f)` / `WarmMuted.copy(alpha = 0.5f)` |
| WordBuild :267–278 Text/Unterstriche | `SoftSand`(+0.45f) | `WarmInk` / `WarmMuted` |
| SentenceOrder :233,242 Karten | wie WordBuild-Karten | `LeafGreen`/`CreamElevated`, Text auf grün `Cream` |
| SentenceOrder :282–313 Pegs/Unterstriche | Mint/Sand-Washes | wie WordBuild-Slots (`LeafGreen`-Wash, `WarmMuted`-Border) |
| SentenceOrder :159,300,306 Texte | `SoftSand` | `WarmInk` |
| SentenceOrder :168 | `MutedText.copy(0.5f)` | `WarmMuted.copy(alpha = 0.6f)` |
| SymbolHunt :55 TilePalette | `SoftCoral/SoftSky/SoftGold/...` | `SunCoral, SkyBlue, StarGold, LeafGreen` (kräftige, unterscheidbare Kacheltöne) |
| SymbolHunt :197 Kachelfüllung | `color.copy(0.22f)` | `color.copy(alpha = 0.30f)` |
| SymbolHunt :206 Glyph | `SoftSand` | `WarmInk` |
| SymbolHunt :251–253 Batterie | `SoftGold`(celebrate)/`SoftMint`(gefüllt)/`NightElevated`(leer) | `StarGold` / `LeafGreen` / `CreamElevated` |
| SymbolInWord :66 Segment-Palette | Soft-Palette | `SunCoral, SkyBlue, LeafGreen, StarGold` |
| SymbolInWord :328,553 fliegendes/gelandetes Glyph | `SoftGold`(+`MutedText`) | `StarGold` (+`WarmMuted`) |
| SymbolInWord :373,382,600,609 Texte/Silhouette | `SoftSand`(0.18f Silhouette) | `WarmInk`; Silhouette `WarmInk.copy(alpha = 0.15f)` |
| SymbolInWord :380,506 gedämpft | `MutedText`-Alphas | `WarmMuted`, Alphas ~+0.1 |

- [ ] **Step 1: Mapping umsetzen** (Dateien vollständig lesen; restliche Night*/Soft*-Vorkommen sinngemäß ersetzen; Kommentare mitziehen)
- [ ] **Step 2: Build + Tests** — `./gradlew :app:testDebugUnitTest :app:assembleDebug`, Expected: grün
- [ ] **Step 3: Commit** — `git add -A && git commit -m "feat(theme): Wort-Bauer, Satz-Architekt, Jagd und Detektiv auf helle Palette"`

---

### Task 6: Pfad als Taglandschaft

**Files:**
- Modify: `ui/path/PathBackground.kt`, `ui/path/PathScreen.kt`, `ui/path/PathSignNode.kt` unter `app/src/main/java/app/abcvorschule/`
- Modify: `ui/theme/Color.kt` (Himmel-/Hügel-Konstanten ergänzen)

**Interfaces:**
- Consumes: Task-1-Farben.
- Produces (in `Color.kt`, nur vom Pfad genutzt):
  - `val DaySkyTop = Color(0xFF9CCAEE)` — Himmel oben
  - `val DaySkyMid = Color(0xFFBFDDF2)` — Himmel Mitte
  - `val DayHorizon = Color(0xFFF7E7C3)` — warmes Licht am Horizont
  - `val HillFar = Color(0xFFB5CF9F)`, `val HillMid = Color(0xFF93BE7E)`, `val HillNear = Color(0xFF6FA85E)` — Hügelbänder hinten→vorn
  - `val TreeCrown = Color(0xFF4E8747)`, `val TreeTrunk = Color(0xFF6B4E34)` (= WoodWarm-Ton)
  - `val CloudWhite = Color(0xFFFDF9EF)` — Wolken (fast-weißes Creme, bewusst die hellste Fläche der App)
  - `val SunGlow = Color(0xFFF7CE73)` — Sonne

Umsetzung:

- Himmel-Gradient (PathBackground.kt:138–140): `0f→DaySkyTop, 0.55f→DaySkyMid, 1f→DayHorizon`.
- Nachtsterne (PathBackground.kt:147–150) ersetzen durch: eine Sonne (Kreis `SunGlow` mit weichem `SunGlow.copy(alpha=0.35f)`-Halo, Position oben rechts im oberen Drittel) und 3–5 weiche Wolken (je 2–3 überlappende `CloudWhite.copy(alpha=0.9f)`-Kreise/abgeflachte Ovale an den bisherigen Stern-Ankerpositionen, damit die Parallaxe-Logik unverändert bleibt). Twinkle-Animation kann als sehr langsames Wolken-Drift-Alpha (0.85–0.95) weiterverwendet oder entfernt werden — keine neue Animationsinfrastruktur bauen.
- Hügelbänder (PathBackground.kt:169–171): `NightPanel/NightElevated`+Alphas → `HillFar/HillMid/HillNear` mit Alpha 1f (Tiefe kommt aus den Tonwerten, nicht mehr aus Transparenz über dunklem Grund); Kommentar anpassen.
- Bäume (PathBackground.kt:233, TreeAlpha:79): Silhouette → `TreeCrown` (Krone) + `TreeTrunk` (Stamm), Alpha 1f; der 1.23:1-Kommentar entfällt.
- Trittspuren (PathScreen.kt:150–152): begangen `StarGold.copy(alpha = 0.8f)` (warm-golden), kommend `WarmInk.copy(alpha = 0.18f)`.
- PathScreen Punkte-Header (:74,79): Stern `StarGold`, Zahl `WarmInk`.
- PathSignNode: Bretter/Pfosten/Nägel unverändert (Wood*). Ring Mastered/Available (:104) `LeafGreen`, InProgress (:105) `SkyBlue`, Locked-Ring/Texte (:106,110): `WarmMuted`-Alphas auf hellem Grund nachjustieren (Silhouetten-Emojis :229 `alpha 0.18f` → `0.35f`, sonst auf hellem Holz unsichtbar; konkret am Gerät/Screenshot prüfen). Stern auf gemeistertem Schild (:173) `StarGold`.
- Kommentare in `Color.kt` zu Wood (Zeile 21–24): „the path is looked at in a dark room" → Umfeld-Beschreibung aktualisieren (heller Tag, Schrift-auf-Brett-Kontraste unverändert gültig).

- [ ] **Step 1: Farben ergänzen + PathBackground umbauen** (Sonne/Wolken statt Sterne, Hügel, Bäume)
- [ ] **Step 2: PathScreen + PathSignNode anpassen**
- [ ] **Step 3: Build + Tests** — `./gradlew :app:testDebugUnitTest :app:assembleDebug`, Expected: grün (PathGeometry-Tests unverändert — Geometrie nicht anfassen)
- [ ] **Step 4: Commit** — `git add -A && git commit -m "feat(pfad): freundliche Taglandschaft mit Sonne, Wolken und grünen Hügeln"`

---

### Task 7: Haptik verdrahten

**Files:**
- Modify: `ui/exercise/LetterTraceTrainer.kt` (:138 nudge, :150 tick), `ui/exercise/SymbolHuntTrainer.kt` (:99–105), `ui/exercise/SymbolInWordTrainer.kt` (:190–194), `ui/shell/TaskShell.kt` (:115,:184 nudge-Migration; SuccessBurst-Stelle :146), `ui/rewards/SuccessEffects.kt` (success bei Trigger), `ui/shell/RewardSummaryScreen.kt` (celebrate beim Erscheinen), Drag-Commit-Stellen in `ui/exercise/drag/` bzw. WordBuild/SentenceOrder (Einrasten), `ui/exercise/SyllableMergeTrainer.kt` (Schnapp → success)

**Interfaces:**
- Consumes: `LocalAbcHaptics`, `AbcHaptics`-Verben aus Task 2.
- Produces: nichts Neues.

Verdrahtungsregeln:

| Ereignis | Verb |
| --- | --- |
| Trace-Stern eingesammelt (LetterTrace :150, ersetzt `TextHandleMove`) | `tick()` |
| Trace off-road (:138), Hunt-Fehltipp, Detektiv-Fehltipp, Parent-Gate-LongPress | `nudge()` (ersetzt `HapticFeedbackType.LongPress`) |
| Hunt-Treffer, Detektiv-Treffer, Drag-Einrasten (WordBuild/SentenceOrder-Commit) | `tick()` |
| Silben-Schnapp (Merge ausgelöst) | `success()` |
| `SuccessBurst`-Trigger (in `SuccessEffects.kt` `LaunchedEffect`, neben `playSuccessChime()`) | `success()` |
| RewardSummaryScreen erscheint (einmalig, `LaunchedEffect(Unit)`) | `celebrate()` |
| Hunt-Batterie voll (Celebrate-Zustand) | `celebrate()` |

Muster: `val haptics = LocalAbcHaptics.current` in der Composable, Aufruf an der Ereignisstelle. In `SuccessEffects.kt` hat `SuccessBurst` Zugriff auf Composition → `LocalAbcHaptics.current` vor dem `LaunchedEffect` lesen. Bestehende `LocalHapticFeedback`-Nutzungen an den genannten Stellen vollständig durch `AbcHaptics` ersetzen (eine Sprache, nicht zwei) — die Imports mit aufräumen.

- [ ] **Step 1: Verdrahten gemäß Tabelle** (jede Datei lesen, Ereignisstellen identifizieren, ersetzen/ergänzen)
- [ ] **Step 2: Build + Tests** — `./gradlew :app:testDebugUnitTest :app:assembleDebug`, Expected: grün
- [ ] **Step 3: Commit** — `git add -A && git commit -m "feat(haptik): Erfolgs- und Sammel-Haptik in allen Trainern verdrahtet"`

---

### Task 8: SuccessBurst mit Gold-Stern und Partikeln

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/rewards/SuccessEffects.kt`
- Create: `app/src/main/java/app/abcvorschule/ui/rewards/BurstGeometry.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/rewards/BurstGeometryTest.kt`

**Interfaces:**
- Consumes: `StarGold`, `SunCoral`, `SkyBlue` (Task 1), `LocalAbcHaptics` (Task 2, bereits in Task 7 verdrahtet).
- Produces: `object BurstGeometry { fun sparkOffsets(count: Int, progress: Float, radiusPx: Float): List<androidx.compose.ui.geometry.Offset> }` — radiale Funken-Positionen, progress 0..1.

- [ ] **Step 1: Failing Test**

```kotlin
package app.abcvorschule.ui.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class BurstGeometryTest {
    @Test
    fun `liefert count offsets gleichmaessig auf dem kreis`() {
        val offsets = BurstGeometry.sparkOffsets(count = 8, progress = 1f, radiusPx = 100f)
        assertEquals(8, offsets.size)
        offsets.forEach { o ->
            assertEquals(100f, hypot(o.x, o.y), 0.6f)
        }
    }

    @Test
    fun `progress skaliert den radius`() {
        val half = BurstGeometry.sparkOffsets(count = 4, progress = 0.5f, radiusPx = 100f)
        half.forEach { o -> assertEquals(50f, hypot(o.x, o.y), 0.6f) }
    }

    @Test
    fun `progress null haelt alle funken im zentrum`() {
        BurstGeometry.sparkOffsets(count = 6, progress = 0f, radiusPx = 100f)
            .forEach { o -> assertTrue(hypot(o.x, o.y) < 0.001f) }
    }
}
```

- [ ] **Step 2: Test rot** — `./gradlew :app:testDebugUnitTest --tests "*BurstGeometryTest"`, Expected: FAIL

- [ ] **Step 3: Implementierung**

`BurstGeometry.kt`:

```kotlin
package app.abcvorschule.ui.rewards

import androidx.compose.ui.geometry.Offset
import kotlin.math.cos
import kotlin.math.sin

/** Radiale Funken-Positionen für den Erfolgs-Burst; rein und testbar. */
object BurstGeometry {
    fun sparkOffsets(count: Int, progress: Float, radiusPx: Float): List<Offset> {
        val r = radiusPx * progress.coerceIn(0f, 1f)
        return List(count) { i ->
            // -90° Start, damit der erste Funke nach oben fliegt.
            val angle = -Math.PI / 2 + 2 * Math.PI * i / count
            Offset((cos(angle) * r).toFloat(), (sin(angle) * r).toFloat())
        }
    }
}
```

`SuccessEffects.kt` — `SuccessBurst` erweitern:
- Stern-Tint: `MaterialTheme.colorScheme.primary` → `StarGold` (Import aus `ui.theme`).
- Neuer `burst`-Animatable (0f→1f, `tween(600, easing = FastOutSlowInEasing)`), gestartet im selben `LaunchedEffect` parallel zu scale/alpha.
- Hinter dem `IconStar` ein `Canvas` in derselben Box (Größe ~200.dp): für `BurstGeometry.sparkOffsets(count = 8, progress = burst.value, radiusPx = size.minDimension / 2f)` je Funke ein kleiner Kreis (`radius = 5.dp.toPx() * (1f - burst.value)`), Farben abwechselnd `StarGold`, `SunCoral`, `SkyBlue`, Alpha `(1f - burst.value)`.
- Timing-Kontrakt unverändert: `onFinished` erst nach Exit-Animation (bestehende Struktur beibehalten).

- [ ] **Step 4: Trace-Stern-Funke (Spec §5.2)**

In `LetterTraceTrainer.kt`: beim Einsammeln eines Sterns (`update.collectedStar`, ~Zeile 144) die Einsammel-Position merken (`var spark by remember(roundKey) { mutableStateOf<Pair<TracePoint, Long>?>(null) }` — Position + Inkrement-Key). Über dem `TraceCanvas` (in derselben Box) ein kleines Overlay-Composable `TraceStarSpark(spark)`: pro neuem Key ein `Animatable` 0f→1f (`tween(400)`), zeichnet per `Canvas` an der Position 5 Funken aus `BurstGeometry.sparkOffsets(count = 5, progress = value, radiusPx = 18.dp.toPx())` als `StarGold`-Kreise (`radius = 3.dp.toPx() * (1f - value)`, `alpha = 1f - value`), danach nichts mehr. Keine Layout-Änderung — reines Draw-Overlay.

- [ ] **Step 5: Tests grün + Build** — `./gradlew :app:testDebugUnitTest :app:assembleDebug`, Expected: PASS
- [ ] **Step 6: Commit** — `git add -A && git commit -m "feat(erfolg): SuccessBurst mit Gold-Stern, Funken-Partikeln und Trace-Stern-Funke"`

---

### Task 9: Lebendige Progress-Bar + End-Screen-Konfetti

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/components/AbcButtons.kt` (`AbcProgressBar`)
- Modify: `app/src/main/java/app/abcvorschule/ui/shell/RewardSummaryScreen.kt`
- Create: `app/src/main/java/app/abcvorschule/ui/rewards/ConfettiGeometry.kt`
- Test: `app/src/test/java/app/abcvorschule/ui/rewards/ConfettiGeometryTest.kt`

**Interfaces:**
- Consumes: Task-1-Farben.
- Produces:
  - `data class ConfettiPiece(val xFraction: Float, val delayFraction: Float, val fallSpeed: Float, val drift: Float, val colorIndex: Int, val sizeFraction: Float)`
  - `object ConfettiGeometry { fun pieces(count: Int, seed: Long): List<ConfettiPiece>; fun yFraction(piece: ConfettiPiece, progress: Float): Float }`

- [ ] **Step 1: Failing Test**

```kotlin
package app.abcvorschule.ui.rewards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfettiGeometryTest {
    @Test
    fun `gleicher seed liefert identische stuecke`() {
        assertEquals(
            ConfettiGeometry.pieces(count = 40, seed = 7L),
            ConfettiGeometry.pieces(count = 40, seed = 7L),
        )
    }

    @Test
    fun `stuecke starten oberhalb des screens und fallen durch`() {
        val p = ConfettiGeometry.pieces(count = 20, seed = 1L)
        p.forEach { piece ->
            assertTrue(ConfettiGeometry.yFraction(piece, progress = 0f) < 0f)
            assertTrue(ConfettiGeometry.yFraction(piece, progress = 1f) > 1f)
        }
    }

    @Test
    fun `werte liegen in gueltigen bereichen`() {
        ConfettiGeometry.pieces(count = 30, seed = 3L).forEach { piece ->
            assertTrue(piece.xFraction in 0f..1f)
            assertTrue(piece.colorIndex in 0..3)
            assertTrue(piece.delayFraction in 0f..0.5f)
        }
    }
}
```

- [ ] **Step 2: Test rot** — `./gradlew :app:testDebugUnitTest --tests "*ConfettiGeometryTest"`, Expected: FAIL

- [ ] **Step 3: Implementierung**

`ConfettiGeometry.kt`:

```kotlin
package app.abcvorschule.ui.rewards

import kotlin.random.Random

data class ConfettiPiece(
    val xFraction: Float,
    val delayFraction: Float,
    val fallSpeed: Float,
    val drift: Float,
    val colorIndex: Int,
    val sizeFraction: Float,
)

/**
 * Deterministische Konfetti-Verteilung (seed-basiert, damit testbar und
 * resume-stabil). progress 0..1 überstreicht die gesamte Animationsdauer;
 * yFraction < 0 heißt "noch über dem Screen", > 1 "unten raus".
 */
object ConfettiGeometry {
    fun pieces(count: Int, seed: Long): List<ConfettiPiece> {
        val rnd = Random(seed)
        return List(count) {
            ConfettiPiece(
                xFraction = rnd.nextFloat(),
                delayFraction = rnd.nextFloat() * 0.5f,
                fallSpeed = 1.1f + rnd.nextFloat() * 0.9f,
                drift = (rnd.nextFloat() - 0.5f) * 0.25f,
                colorIndex = rnd.nextInt(4),
                sizeFraction = 0.6f + rnd.nextFloat() * 0.8f,
            )
        }
    }

    fun yFraction(piece: ConfettiPiece, progress: Float): Float {
        val local = ((progress - piece.delayFraction) / (1f - piece.delayFraction))
        // Start knapp über dem Screen (-0.1), Ende sicher darunter.
        return -0.1f + local * piece.fallSpeed * 1.3f
    }
}
```

(Hinweis: `yFraction(progress=1f) > 1f` verlangt `fallSpeed*1.3 - 0.1 > 1` → min fallSpeed 1.1 · 1.3 = 1.43 ✓.)

`RewardSummaryScreen.kt`: hinter dem bestehenden Inhalt (z-unterste Ebene der Box/des Screens) ein `ConfettiOverlay`-Composable (private in derselben Datei): `LaunchedEffect(Unit)` animiert `progress` 0→1 über `tween(2200, easing = LinearEasing)` einmalig; `Canvas(fillMaxSize)` zeichnet für jedes Stück (count=40, seed=42) ein kleines abgerundetes Rechteck (`rotate` um `drift * 360 * progress`), x = `(xFraction + drift * progress).coerceIn(0f,1f) * width`, y = `yFraction(...) * height`, Farbe aus `listOf(StarGold, SunCoral, SkyBlue, LeafGreen)[colorIndex]`, nur zeichnen wenn `yFraction in -0.1f..1.1f`. Nach `progress == 1f` nichts mehr zeichnen (Recomposition-günstig: `if (progress < 1f)`).

`AbcProgressBar` (AbcButtons.kt): Füll-Fraction über `animateFloatAsState(targetValue = fraction, animationSpec = tween(450))` animieren. Gold-Puls: `LaunchedEffect(index)` startet bei jeder Index-Erhöhung einen `pulse`-Animatable 1f→0f (`tween(500)`); im Canvas an der Füllkante einen Kreis `radius = size.height * (0.8f + 0.6f * (1f - pulse))`, Farbe `StarGold.copy(alpha = 0.6f * pulse)` zeichnen, wenn `pulse > 0f`.

- [ ] **Step 4: Tests grün + Build** — `./gradlew :app:testDebugUnitTest :app:assembleDebug`, Expected: PASS
- [ ] **Step 5: Commit** — `git add -A && git commit -m "feat(erfolg): animierte Progress-Bar mit Gold-Puls und End-Screen-Konfetti"`

---

### Task 10: Alt-Farben ausbauen & Doku

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/ui/theme/Color.kt` (Night*/Soft*-Leichen entfernen)
- Modify: `docs/PRODUCT_PRINCIPLES.md` (§2, §5, §10)
- Modify: `AGENTS.md` (Technik-Kurzüberblick, Kind-UI-Kurzfassung)

**Interfaces:** Consumes alles Vorherige; Produces finale Doku.

- [ ] **Step 1: Verwaiste Farbkonstanten entfernen**

`grep -rn "NightInk\|NightPanel\|NightElevated\|NightDeep\|NightHorizon\|SoftMint\|SoftCoral\|SoftSky\|SoftGold\|MutedText\|SoftSand" app/src/main/java` — jede Konstante ohne verbleibende Verwendung aus `Color.kt` löschen; verbleibende Verwendungen sind Fehler aus Task 3–6 und werden dort-artig ersetzt (gleiches Rollen-Mapping). `SoftSand` bleibt, falls die Schild-Schrift sie weiter nutzt.

- [ ] **Step 2: PRODUCT_PRINCIPLES.md aktualisieren**

- §2: „Dunkles, ruhiges UI; weiches Feedback…" → „Helles, warmes, ruhiges UI (Creme statt Weiß — augenfreundlich); weiches Feedback statt Strafe oder Drucksprache."
- §5: „Nachtlandschaft (Verlauf, Sterne, Hügel mit Parallaxe — dark-only bleibt Prinzip)" → „Taglandschaft (Himmelsverlauf, Sonne und Wolken, grüne Hügel mit Parallaxe — helles Warmer-Tag-Theme)". „wärmer gezeichnet" für den begangenen Weg bleibt (golden).
- §10 Design-System ergänzen:

```markdown
- Farbrollen (verbindlich): `StarGold` = Sterne/Punkte/Belohnung, `LeafGreen` = richtig/erledigt,
  `SkyBlue` = Fortschritt/aktiv, `SunCoral` = Handlungs-CTA, `ClayRed` = Fehlertext (Erwachsene).
  Eine Bedeutung pro Farbe — Sterne und Progress greifen nie auf `primary` zu.
- Haptik-Vokabular `AbcHaptics` (tick/success/celebrate/nudge): tick = kleiner Sammel-Erfolg
  (Trace-Stern, Jagd-Treffer, Einrasten), success = Aufgabe richtig, celebrate = Lektions-/
  Batterie-Feier, nudge = sanfte Korrektur. Haptik ergänzt Ton, ersetzt ihn nie.
- Erfolgsmomente: SuccessBurst (Gold-Stern + Funken), Gold-Puls der Progress-Bar je Trainer,
  Konfetti auf dem End-Screen.
```

- Review-Tabelle: Zeile „Ist das UI ruhig und kindgerecht?" bleibt; neue Zeile „Nutzt ein Stern/Progress `primary` statt der Farbrolle? → Nein".

- [ ] **Step 3: AGENTS.md aktualisieren**

„Kotlin + Jetpack Compose, dark-only" → „Kotlin + Jetpack Compose, helles Warmer-Tag-Theme (Farbrollen + Haptik-Vokabular siehe PRODUCT_PRINCIPLES §10)". In der Kurzfassung der Kind-UI-Regeln ggf. „Dunkles, ruhiges UI"-Formulierungen anpassen.

- [ ] **Step 4: Build + Tests** — `./gradlew :app:testDebugUnitTest :app:assembleDebug`, Expected: grün
- [ ] **Step 5: Commit** — `git add -A && git commit -m "docs+theme: Alt-Palette entfernt, Prinzipien auf Warmer-Tag-Konzept aktualisiert"`

---

### Task 11: Verifikation am Gerät/Emulator (Screenshots)

**Files:** keine Code-Änderungen erwartet; Fixes fließen als Follow-up-Commits.

- [ ] **Step 1: Debug-Build installieren** (falls Emulator/Gerät via adb erreichbar; sonst Schritt dokumentiert überspringen)
- [ ] **Step 2: Screenshots**: Pfad-Screen, Trace-Trainer, Rechnen, Wort-Bauer, End-Screen — prüfen: Kontraste (font_scale 1.3!), Stern-über-Progress-Trennung (gold auf blau), Silhouetten gesperrter Schilder sichtbar, Dampf sichtbar, Statusbar-Icons dunkel.
- [ ] **Step 3: Gefundene Kontrast-/Sichtbarkeitsfehler fixen + committen**
