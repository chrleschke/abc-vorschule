# Splash-Screen Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Kaltstart führt ohne sichtbaren Zwischenzustand vom Launcher-Icon in die App — ein definierter Splash, hell oder dunkel je nach Systemmodus, der stehen bleibt, bis es etwas anzuzeigen gibt.

**Architecture:** `androidx.core:core-splashscreen` ersetzt die Systemvorgabe. Ein Splash-Theme in `values/` trägt Icon und hellen Grund, ein Aufsatz in `values-night/` nur den dunklen. `MainActivity` hält den Splash über `setKeepOnScreenCondition`, bis Compose meldet, dass Pack oder Fehlermeldung stehen, und blendet ihn dann in 300 ms aus. Der bisherige „Silbo …"-Ladeplatzhalter in `TaskShell` entfällt ersatzlos, weil er nie mehr sichtbar wird.

**Tech Stack:** Kotlin, Jetpack Compose, `androidx.core:core-splashscreen` 1.0.1, Android-Ressourcen-Qualifier (`values-night`), JUnit 4.

**Spec:** `docs/superpowers/specs/2026-09-14-splash-screen-design.md`

## Global Constraints

- `minSdk = 26`, `targetSdk = 36`, `compileSdk = 36` — nichts davon anfassen.
- **Die App selbst bleibt hell.** `Theme.AbcVorschule` wird in `values-night/` **nicht** überschrieben, `forceDarkAllowed=false` bleibt. Nur das Splash-Theme ist modusabhängig.
- **Kein Kotlin-Pendant für den Nachtton.** `#16222F` steht ausschließlich in `values-night/themes.xml`. `Color.kt` hat unreferenzierte Nachtkonstanten schon einmal abgeräumt (siehe Kommentar bei `SoftSand`) — keine neue anlegen.
- **Kommentare auf Deutsch, im Stil des Bestands:** sie begründen *warum*, nicht *was*. Vorlage ist der Kopfkommentar in `app/src/main/res/values/themes.xml`.
- **Testnamen:** camelCase ohne Backticks, `org.junit.Assert.*`, JUnit 4 — wie in `app/src/test/java/app/abcvorschule/session/SuccessSpeechTest.kt`.
- Der Splash-Grund im Hellen ist `#FFF8F9F8` — hex-gleich mit `PaperCenter` in `Color.kt` und mit `android:windowBackground` in `values/themes.xml`. Weichen die drei voneinander ab, blitzt beim Start eine fremde Fläche auf.

## File Structure

| Datei | Verantwortung |
| --- | --- |
| `gradle/libs.versions.toml` | Version und Koordinate von `core-splashscreen` |
| `app/build.gradle.kts` | Abhängigkeit deklarieren |
| `app/src/main/res/values/themes.xml` | Splash-Basis: Icon, `postSplashScreenTheme`, heller Grund |
| `app/src/main/res/values-night/themes.xml` *(neu)* | allein der dunkle Grund |
| `app/src/main/AndroidManifest.xml` | Launcher-Activity startet mit dem Splash-Theme |
| `app/src/main/java/app/abcvorschule/session/SessionModels.kt` | `hasShowableContent` — die Haltebedingung als reine Funktion |
| `app/src/test/java/app/abcvorschule/session/SplashHandoffTest.kt` *(neu)* | deckt die Haltebedingung ab, insbesondere den Fehlerfall |
| `app/src/main/java/app/abcvorschule/MainActivity.kt` | `installSplashScreen()`, Haltebedingung, Austritts-Animation |
| `app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt` | Ladezweig ohne Text |

---

### Task 1: Haltebedingung als reine Funktion

Die eine Stelle, an der man sich hier vertun kann, ist der Fehlerfall: `ready` bleibt bei einem Ladefehler dauerhaft `false`, also hinge ein reiner `!ready`-Test für immer im Splash. Deshalb wandert die Entscheidung in eine testbare Funktion, bevor irgendetwas verdrahtet wird.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/session/SessionModels.kt` (nach dem `SessionUiState`-Block, der bei Zeile 88 beginnt und mit seinem `}` endet)
- Test: `app/src/test/java/app/abcvorschule/session/SplashHandoffTest.kt` (neu)

**Interfaces:**
- Consumes: `SessionUiState` aus derselben Datei — alle Parameter haben Defaults, `SessionUiState()` ist gültig.
- Produces: `fun SessionUiState.hasShowableContent(packLoaded: Boolean): Boolean` — von Task 3 in `AbcApp` aufgerufen.

- [ ] **Step 1: Write the failing test**

Neue Datei `app/src/test/java/app/abcvorschule/session/SplashHandoffTest.kt`:

```kotlin
package app.abcvorschule.session

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Die Übergabe vom Splash an die App. Der Splash liegt über allem, bis diese
 * Bedingung kippt — sie ist damit die einzige Stelle, an der ein Startbild für
 * immer stehenbleiben könnte.
 */
class SplashHandoffTest {

    @Test
    fun frischerStartHaeltDenSplash() {
        assertFalse(SessionUiState().hasShowableContent(packLoaded = false))
    }

    @Test
    fun readyOhnePackHaeltDenSplash() {
        assertFalse(SessionUiState(ready = true).hasShowableContent(packLoaded = false))
    }

    @Test
    fun packOhneReadyHaeltDenSplash() {
        assertFalse(SessionUiState(ready = false).hasShowableContent(packLoaded = true))
    }

    @Test
    fun readyMitPackGibtFrei() {
        assertTrue(SessionUiState(ready = true).hasShowableContent(packLoaded = true))
    }

    /**
     * Der Grund, warum der Fehlerzweig überhaupt in die Bedingung gehört: hier
     * bleibt `ready` dauerhaft false, und ohne diesen Fall sähe das Kind nie die
     * Meldung, die TaskShell dafür bereithält — nur den Splash.
     */
    @Test
    fun ladefehlerGibtFreiObwohlReadyFalseBleibt() {
        val state = SessionUiState(ready = false, error = "Inhalt konnte nicht geladen werden")
        assertTrue(state.hasShowableContent(packLoaded = false))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*SplashHandoffTest*'
```

Erwartet: Kompilierfehler, `unresolved reference: hasShowableContent`.

- [ ] **Step 3: Write minimal implementation**

In `app/src/main/java/app/abcvorschule/session/SessionModels.kt`, direkt hinter die schließende Klammer der `SessionUiState`-Deklaration:

```kotlin
/**
 * Steht etwas Zeigbares — darf der Splash also abtreten?
 *
 * Spiegelt die Verzweigung in `TaskShell`: ein Fehler führt dort in den
 * Meldungszweig, alles andere braucht sowohl [SessionUiState.ready] als auch ein
 * geladenes Pack. Der Fehlerfall muss mit hinein, weil `ready` bei einem
 * Ladefehler dauerhaft `false` bleibt — ein reiner `ready`-Test ließe den Splash
 * für immer stehen, und die Meldung erschiene nie.
 *
 * @param packLoaded ob `SessionViewModel.contentPack()` schon ein Pack liefert.
 */
fun SessionUiState.hasShowableContent(packLoaded: Boolean): Boolean =
    error != null || (ready && packLoaded)
```

- [ ] **Step 4: Run test to verify it passes**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest --tests '*SplashHandoffTest*'
```

Erwartet: BUILD SUCCESSFUL, 5 Tests grün.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/app/abcvorschule/session/SessionModels.kt app/src/test/java/app/abcvorschule/session/SplashHandoffTest.kt
git commit -m "feat(splash): Haltebedingung als reine Funktion, Fehlerfall inklusive"
```

---

### Task 2: Splash-Themes und Abhängigkeit

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts` (Block `dependencies {`, ab Zeile 116)
- Modify: `app/src/main/res/values/themes.xml`
- Create: `app/src/main/res/values-night/themes.xml`
- Modify: `app/src/main/AndroidManifest.xml`

**Interfaces:**
- Produces: Style `@style/Theme.AbcVorschule.Splash` — von Task 3 vorausgesetzt, weil `installSplashScreen()` ohne dieses Theme zur Laufzeit wirkungslos bleibt.

- [ ] **Step 1: Abhängigkeit deklarieren**

In `gradle/libs.versions.toml`, im `[versions]`-Block hinter `graphicsShapes = "1.1.0"`:

```toml
splashscreen = "1.0.1"
```

Im `[libraries]`-Block hinter der `androidx-graphics-shapes`-Zeile:

```toml
androidx-core-splashscreen = { group = "androidx.core", name = "core-splashscreen", version.ref = "splashscreen" }
```

In `app/build.gradle.kts`, im `dependencies`-Block hinter `implementation(libs.androidx.graphics.shapes)`:

```kotlin
    // Backportet den Android-12-Splash bis API 21 herunter und macht ihn
    // überhaupt erst steuerbar: ohne definierten Splash malt jedes System
    // selbst, was auf einem Motorola edge 60 pro im Dark Mode ein schwarzer
    // Screen war.
    implementation(libs.androidx.core.splashscreen)
```

- [ ] **Step 2: Splash-Basis in values/themes.xml anlegen**

In `app/src/main/res/values/themes.xml`, vor dem schließenden `</resources>` und hinter der bestehenden `Theme.AbcVorschule`-Zeile:

```xml
    <!--
      Splash. Bis hierher malte das System selbst, weil nichts vorgegeben war —
      auf einem Motorola edge 60 pro im Dark Mode schwarz. Der Grund ist jetzt
      festgelegt, und zwar auf PaperCenter (#F8F9F8): denselben Ton, in dem der
      radiale Papiergrund der App startet und den auch android:windowBackground
      oben trägt. Splash und erste App-Fläche sind damit hex-gleich, der Übergang
      ist nicht wahrnehmbar. Weicht einer der drei Werte ab, blitzt beim Start
      eine fremde Fläche auf.

      Das Icon ist bewusst das Launcher-Icon selbst und keine eigene Zeichnung:
      es zeigt genau das Symbol, das eben angetippt wurde, und läuft beim
      nächsten Icon-Wechsel nicht davon.

      Warum eine Basis und nicht ein einzelner Style: values-night/ überschreibt
      den Grund, und ein gleichnamiger Style in einem Qualifier-Ordner ersetzt
      den aus values/ vollständig — Item für Item wird nichts gemischt, dieselbe
      Falle wie beim v29-Aufsatz. Ohne Basis müsste der Nacht-Aufsatz Icon und
      postSplashScreenTheme wiederholen.

      Das ausdrückliche parent ist Pflicht: bei einem gepunkteten Namen leitet
      Android den Elternstyle sonst aus dem Präfix ab, und
      Base.Theme.AbcVorschule.Splash erbte dann von Base.Theme.AbcVorschule
      statt von Theme.SplashScreen.
    -->
    <style name="Base.Theme.AbcVorschule.Splash" parent="Theme.SplashScreen">
        <item name="windowSplashScreenBackground">#FFF8F9F8</item>
        <item name="windowSplashScreenAnimatedIcon">@mipmap/ic_launcher</item>
        <item name="postSplashScreenTheme">@style/Theme.AbcVorschule</item>
    </style>

    <style name="Theme.AbcVorschule.Splash" parent="Base.Theme.AbcVorschule.Splash" />
```

- [ ] **Step 3: Nacht-Aufsatz anlegen**

Neue Datei `app/src/main/res/values-night/themes.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!--
      Im Dark Mode ist allein der Grund ein anderer; Icon und
      postSplashScreenTheme kommen unverändert aus der Basis in values/.

      #16222F ist die Nachtfassung von DaySkyTop (#9CCAEE, Color.kt) — derselbe
      Blauton, auf die Helligkeit eines Nachthimmels gezogen. Bewusst kein reines
      Schwarz: das helle Landschafts-Icon soll auf einer Fläche stehen, die zur
      App gehört, nicht auf der Abwesenheit einer Farbe.

      Kein Kotlin-Pendant in Color.kt. Die Farbe hat außerhalb dieses Themes
      keinen Aufrufer, und genau solche unreferenzierten Nachtkonstanten hat
      Color.kt schon einmal abgeräumt (siehe Kommentar bei SoftSand).

      Theme.AbcVorschule wird hier absichtlich NICHT überschrieben: die App
      selbst kennt keinen Dark Mode, ihre Kontraste sind alle gegen den
      Papiergrund gerechnet. Den Helligkeitssprung beim Eintritt nimmt der
      300-ms-Crossfade in MainActivity auf.
    -->
    <style name="Theme.AbcVorschule.Splash" parent="Base.Theme.AbcVorschule.Splash">
        <item name="windowSplashScreenBackground">#FF16222F</item>
    </style>
</resources>
```

- [ ] **Step 4: Launcher-Activity auf das Splash-Theme setzen**

In `app/src/main/AndroidManifest.xml` die Zeile

```xml
            android:theme="@style/Theme.AbcVorschule">
```

am `<activity android:name=".MainActivity" …>` ersetzen durch:

```xml
            android:theme="@style/Theme.AbcVorschule.Splash">
```

Das `android:theme` am `<application>`-Tag bleibt unverändert `@style/Theme.AbcVorschule` — nur die gestartete Activity braucht den Splash, und `postSplashScreenTheme` schaltet sie danach ohnehin zurück.

- [ ] **Step 5: Build und Lint**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug :app:lintDebug
```

Erwartet: BUILD SUCCESSFUL. Bricht es mit `resource style/Theme.SplashScreen not found`, fehlt die Abhängigkeit aus Step 1.

- [ ] **Step 6: Prüfen, dass beide Modi denselben Style auflösen**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk $(ls -d $HOME/Library/Android/sdk/build-tools/* | tail -1)/aapt2 dump resources app/build/outputs/apk/debug/app-debug.apk | grep -A3 "Theme.AbcVorschule.Splash"
```

Erwartet: zwei Konfigurationen des Styles — eine ohne Qualifier, eine mit `night`.

- [ ] **Step 7: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/res/values/themes.xml app/src/main/res/values-night/themes.xml app/src/main/AndroidManifest.xml
git commit -m "feat(splash): Splash-Theme mit Launcher-Icon, hell und dunkel"
```

---

### Task 3: Splash halten, ausblenden, Platzhalter entfernen

Diese drei Änderungen gehören zusammen und dürfen nicht einzeln landen: ohne die Haltebedingung würde der geleerte Platzhalter als kurzer leerer Frame aufblitzen — statt „Silbo …" sähe man dann nichts, was nicht besser wäre.

**Files:**
- Modify: `app/src/main/java/app/abcvorschule/MainActivity.kt`
- Modify: `app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt:122-135` (der `!state.ready || pack == null`-Zweig)

**Interfaces:**
- Consumes: `SessionUiState.hasShowableContent(packLoaded: Boolean): Boolean` aus Task 1; `@style/Theme.AbcVorschule.Splash` aus Task 2.
- Consumes: `SessionViewModel.contentPack(): ContentPack?` — liefert `null`, solange das Pack nicht geladen ist.
- Produces: `AbcApp(onFinish: () -> Unit, onContentReady: () -> Unit)` — erweiterte Signatur.

- [ ] **Step 1: Import und Splash-Installation in MainActivity**

In `app/src/main/java/app/abcvorschule/MainActivity.kt` bei den Imports, alphabetisch hinter `androidx.core.view.WindowInsetsControllerCompat`:

```kotlin
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
```

In der Klasse `MainActivity`, direkt vor `override fun onCreate`:

```kotlin
    /**
     * Vom Compose-Baum gesetzt, sobald etwas Zeigbares steht — bis dahin bleibt
     * der Splash über der App. Ein einfaches `var` genügt: gelesen wird es im
     * Pre-Draw-Listener des Main-Threads, geschrieben aus der Composition, die
     * ebenfalls dort läuft.
     */
    private var contentReady = false
```

Die ersten Zeilen von `onCreate` — `installSplashScreen()` **muss** vor `super.onCreate()` stehen, sonst hat das System das Startfenster schon aufgebaut:

```kotlin
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        // Nicht „bis geladen", sondern „bis es etwas zu zeigen gibt": bei einem
        // Ladefehler bleibt `ready` dauerhaft false, und ein reiner ready-Test
        // ließe den Splash für immer stehen. hasShowableContent nimmt den
        // Fehlerzweig deshalb mit.
        splashScreen.setKeepOnScreenCondition { !contentReady }
        // Im Hellen ist dieser Crossfade unsichtbar — darunter liegt dieselbe
        // Farbe. Er ist für den Dark Mode da: dort blendet der Nachthimmel auf
        // den Papiergrund über, statt hart umzuschlagen.
        splashScreen.setOnExitAnimationListener { splashProvider ->
            splashProvider.view.animate()
                .alpha(0f)
                .setDuration(SPLASH_FADE_MILLIS)
                .withEndAction { splashProvider.remove() }
                .start()
        }
        super.onCreate(savedInstanceState)
```

Der Rest von `onCreate` bleibt unverändert bis zum `setContent`-Block.

- [ ] **Step 2: Konstante und erweiterten AbcApp-Aufruf ergänzen**

Den `setContent`-Block in `onCreate` ersetzen durch:

```kotlin
        setContent {
            AbcTheme {
                CompositionLocalProvider(LocalAbcHaptics provides rememberAbcHaptics()) {
                    AbcApp(
                        onFinish = { finish() },
                        onContentReady = { contentReady = true },
                    )
                }
            }
        }
```

Am Dateiende, hinter der `MainActivity`-Klasse und vor `@Composable fun AbcApp`:

```kotlin
/**
 * Dauer des Übergangs vom Splash in die App. Lang genug, dass der Sprung vom
 * Nachthimmel auf den Papiergrund im Dark Mode als Blende gelesen wird und nicht
 * als Umschalten; kurz genug, dass niemand darauf wartet.
 */
private const val SPLASH_FADE_MILLIS = 300L
```

- [ ] **Step 3: AbcApp meldet Bereitschaft**

Die Signatur von `AbcApp` erweitern:

```kotlin
@Composable
fun AbcApp(onFinish: () -> Unit = {}, onContentReady: () -> Unit = {}) {
```

Und in `AbcApp` direkt hinter der bestehenden Zeile `val pack = viewModel.contentPack()` einfügen:

```kotlin
    // Freigabe für den Splash, sobald TaskShell etwas anderes als den leeren
    // Grund zeichnen würde. Die Bedingung spiegelt dessen Verzweigung — sie
    // liegt in SessionModels.kt, damit sie ohne Gerät prüfbar ist.
    val showable = state.hasShowableContent(packLoaded = pack != null)
    LaunchedEffect(showable) {
        if (showable) onContentReady()
    }
```

Import ergänzen, alphabetisch bei den `app.abcvorschule.session`-Imports:

```kotlin
import app.abcvorschule.session.hasShowableContent
```

- [ ] **Step 4: Ladeplatzhalter in TaskShell leeren**

In `app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt` den kompletten Zweig

```kotlin
            !state.ready || pack == null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Silbo", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
```

ersetzen durch:

```kotlin
            !state.ready || pack == null -> {
                // Absichtlich leer. Über dieser Fläche liegt noch der Splash —
                // MainActivity hält ihn, bis Pack oder Fehlermeldung stehen, und
                // beides führt in einen anderen Zweig. Sichtbar würde hier also
                // nur der Papiergrund des umschließenden Box, und der ist
                // derselbe Ton wie der Splash-Grund im Hellen. Ein Platzhalter
                // („Silbo …", bis 2026-09) konnte nur noch als Aufblitzen
                // erscheinen.
            }
```

- [ ] **Step 5: Kompilieren und ungenutzte Importe prüfen**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:assembleDebug :app:lintDebug
```

Erwartet: BUILD SUCCESSFUL. Meldet Lint `UnusedImport` in `TaskShell.kt`, die betreffenden Importe entfernen — mit `grep -n "Spacer\|MaterialTheme.typography\|Arrangement.Center" app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt` erst prüfen, ob sie nicht doch noch andere Aufrufer in der Datei haben.

- [ ] **Step 6: Bestehende Tests laufen lassen**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:testDebugUnitTest
```

Erwartet: BUILD SUCCESSFUL, keine Regression in den 87 vorhandenen Testdateien.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/app/abcvorschule/MainActivity.kt app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt
git commit -m "feat(splash): Splash haelt bis zum Inhalt, blendet aus, Platzhalter entfaellt"
```

---

### Task 4: Prüfung auf dem Gerät

Der Splash ist Systemoberfläche — weder Compose-Tests noch Unit-Tests sehen ihn. Diese Prüfung ist die einzige, die den eigentlichen Fehler abdeckt, und sie ist deshalb kein optionaler Abschluss.

**Files:** keine — reine Verifikation.

**Vorbedingung:** Gerät angeschlossen. Es hängt in diesem Setup doppelt am ADB, also immer mit `-s` arbeiten:

```bash
ANDROID_HOME=$HOME/Library/Android/sdk $HOME/Library/Android/sdk/platform-tools/adb devices
```

Die folgenden Schritte nutzen `$DEV` für die gewählte Seriennummer und `$ADB` für den ADB-Pfad:

```bash
export ADB=$HOME/Library/Android/sdk/platform-tools/adb
export DEV=<seriennummer aus adb devices>
```

- [ ] **Step 1: Installieren**

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :app:installDebug
```

- [ ] **Step 2: Kaltstart im Dark Mode**

```bash
$ADB -s $DEV shell cmd uimode night yes
$ADB -s $DEV shell am force-stop app.silbo.abcvorschule
$ADB -s $DEV shell am start -n app.silbo.abcvorschule/.MainActivity
$ADB -s $DEV exec-out screencap -p > /tmp/splash-dark.png
```

Den Screencap sofort nach dem Start absetzen, damit er den Splash trifft. Erwartet: dunkelblauer Grund `#16222F` mit dem runden Landschafts-Icon — **kein Schwarz**.

- [ ] **Step 3: Kaltstart im Light Mode**

```bash
$ADB -s $DEV shell cmd uimode night no
$ADB -s $DEV shell am force-stop app.silbo.abcvorschule
$ADB -s $DEV shell am start -n app.silbo.abcvorschule/.MainActivity
$ADB -s $DEV exec-out screencap -p > /tmp/splash-light.png
```

Erwartet: Papiergrund mit demselben Icon.

- [ ] **Step 4: Zwischenframe-Prüfung per Bildschirmaufnahme**

```bash
$ADB -s $DEV shell am force-stop app.silbo.abcvorschule
$ADB -s $DEV shell screenrecord --time-limit 6 /sdcard/start.mp4 &
sleep 1
$ADB -s $DEV shell am start -n app.silbo.abcvorschule/.MainActivity
sleep 6
$ADB -s $DEV pull /sdcard/start.mp4 /tmp/start.mp4
```

Aufnahme durchsehen. Erwartet: Splash, Blende, Pfad-Screen. **Nicht** erwartet: ein weißer Frame, ein schwarzer Frame oder der Text „Silbo".

- [ ] **Step 5: Fehlerfall — der Splash muss freigeben**

Nachstellen, indem das Content-Asset im Build unbrauchbar gemacht wird. Erst den Pfad finden:

```bash
grep -rn "assets.open\|fromClasspath\|\.json" app/src/main/java/app/abcvorschule/content/ContentRepository.kt | head
```

Die dort geladene Datei unter `app/src/main/assets/` temporär durch `{` ersetzen (Original vorher an einen Ort außerhalb des Repos kopieren), neu bauen und installieren, dann starten.

Erwartet: Der Splash gibt frei und die Fehlermeldung aus dem `error`-Zweig steht auf dem Papiergrund. **Nicht** erwartet: ein stehender Splash.

Danach das Original zurückspielen und mit `git status` prüfen, dass der Baum sauber ist.

- [ ] **Step 6: Ergebnis festhalten**

Beide Screenshots und den Befund aus Step 4 und 5 im Abschlussbericht nennen. Bleibt in Step 2 trotz allem ein schwarzer Frame stehen, ist das ein Motorola-Gametime- oder Launcher-Effekt außerhalb der App — dann Screenshot samt Zeitpunkt melden, statt weiter am Theme zu drehen.
