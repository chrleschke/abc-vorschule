# Splash-Screen: definierter Start statt Systemvorgabe

## Problem

Der Kaltstart zeigt heute zwei Zwischenzustände, die keiner entworfen hat:

1. **Ein schwarzer Screen.** Die App definiert keinen Splash — weder
   `androidx.core:core-splashscreen` noch die `windowSplashScreen*`-Attribute
   ab API 31. Was das System mangels Vorgabe malt, hängt am Gerät; auf einem
   Motorola edge 60 pro im Dark Mode ist es schwarz.
2. **Ein kurzer weißer Screen mit „Silbo …".** Das ist kein Splash, sondern der
   Ladezweig `!state.ready || pack == null` in `TaskShell.kt` — ein
   Textplatzhalter auf dem Papiergrund, sichtbar solange das Content-Pack lädt.

Beides zusammen liest sich als Stottern: schwarz, kurz weiß mit Text, dann die
App.

## Ziel

Ein Start, der aus dem Launcher-Icon in die App führt, ohne sichtbaren
Zwischenzustand. Der Splash trägt das App-Logo und passt sich Hell/Dunkel an.

## Randbedingungen aus dem Bestand

- `minSdk = 26`, `targetSdk = 36` — `core-splashscreen` deckt den ganzen Bereich
  ab und backportet das Android-12-Verhalten nach unten.
- **Die App selbst kennt keinen Dark Mode.** `forceDarkAllowed=false`
  (`values-v29/themes.xml`), Papiergrund `PaperCenter #F8F9F8`, Landschaft und
  Karten sind gegen diesen hellen Grund kalibriert. Das bleibt so.
- **Die Nachtpalette ist abgeräumt.** `Color.kt` dokumentiert, dass `NightInk`,
  `NightPanel`, `NightElevated`, `NightDeep`, `NightHorizon`, `SoftMint`,
  `SoftCoral`, `SoftSky`, `SoftGold` und `MutedText` als unreferenziert entfernt
  wurden; einzig `SoftSand` blieb, weil `PathSignNode` es noch braucht. Für den
  dunklen Splash kommt genau **ein** neuer Ton dazu.
- `MainActivity` ruft `enableEdgeToEdge()` und versteckt die Systemleisten. Der
  Splash läuft davor und ist davon unberührt.

## Entwurf

### Logo

Das Launcher-Adaptive-Icon selbst — Taglandschaft mit ABC-Schild
(`mipmap-anydpi-v26/ic_launcher.xml`), vom System kreisrund maskiert. Genau das
Bild, das der Nutzer gerade angetippt hat. Kein neues Asset, keine zweite
Wahrheit, die beim nächsten Icon-Wechsel auseinanderläuft.

### Grundfarben

| Modus | Ressource | Grund | Herkunft |
| --- | --- | --- | --- |
| Hell | `values/themes.xml` | `#F8F9F8` | `PaperCenter` — der Ton, in dem der radiale Papiergrund der App startet |
| Dunkel | `values-night/themes.xml` | `#16222F` | Nachtfassung von `DaySkyTop #9CCAEE` |

Im Hellen ist der Splash-Grund **hex-identisch** mit dem App-Grund in der Mitte.
Splash und erste App-Fläche sind dieselbe Farbe; der Übergang ist nicht
wahrnehmbar, die Experience wächst aus dem Splash heraus.

Im Dunkeln steht das helle Icon auf Nachthimmel. Dafür braucht es den Übergang
unten.

Der Nachtton bekommt **kein** Kotlin-Pendant in `Color.kt`. Er hat außerhalb des
Themes keinen Aufrufer, und genau solche unreferenzierten Nachtkonstanten hat
`Color.kt` schon einmal abgeräumt. Stattdessen steht der Hex-Wert in der
`themes.xml` mit einem Kommentar zur Herkunft — dieselbe Handhabung, die der
helle Grund dort heute schon hat.

### Haltebedingung

```
setKeepOnScreenCondition { state.error == null && !state.ready }
```

Der Splash bleibt stehen, bis das Content-Pack geladen ist. Damit entfällt der
weiße „Silbo …"-Zustand ersatzlos — er wird nicht umgestaltet, er wird nie
sichtbar.

Die Fehlerbedingung ist bewusst **kein Timeout**: schlägt das Laden fehl, bleibt
`ready` dauerhaft `false`, und ein reiner `!ready`-Test hinge für immer im
Splash. Mit `state.error == null` gibt der Splash in genau dem Moment frei, in
dem der bestehende `error`-Zweig in `TaskShell` etwas anzuzeigen hat.

### Austritt

`setOnExitAnimationListener` blendet die Splash-Ebene in 300 ms aus.

- Hell: unsichtbar, weil darunter dieselbe Farbe liegt.
- Dunkel: der Nachthimmel blendet in den Papiergrund über, statt hart
  umzuschlagen.

### Style-Aufbau

Der dunkle Grund kann **nicht** einfach als gleichnamiger Style in
`values-night/` stehen: ein Style in einem Qualifier-Ordner ersetzt den
gleichnamigen aus `values/` vollständig, Item für Item wird nichts gemischt.
Ohne Basis müsste der Nacht-Aufsatz Icon, `postSplashScreenTheme` und alles
Weitere wiederholen — und liefe beim nächsten Zusatz auseinander.

Deshalb dieselbe Zweiteilung, die `themes.xml` für den API-29-Aufsatz schon
verwendet und dort auch begründet:

- `Base.Theme.AbcVorschule.Splash` in `values/`, explizites
  `parent="Theme.SplashScreen"` — trägt Icon, `postSplashScreenTheme` und den
  hellen Grund.
- `Theme.AbcVorschule.Splash` in `values/` — leer, erbt alles.
- `Theme.AbcVorschule.Splash` in `values-night/` — erbt von der Basis und
  überschreibt allein `windowSplashScreenBackground`.

Das explizite `parent` an der Basis ist Pflicht: bei einem gepunkteten Namen
leitet Android den Elternstyle sonst aus dem Namenspräfix ab, und
`Base.Theme.AbcVorschule.Splash` erbte damit vom App-Theme statt von
`Theme.SplashScreen`.

### Was sich nicht ändert

`Theme.AbcVorschule` bleibt hell, auch unter Nacht-Qualifier. Nur das
Splash-Theme ist modusabhängig. Damit ist kein `values-night-v29` nötig — der
API-29-Aufsatz betrifft ausschließlich `forceDarkAllowed` am App-Theme, und das
gilt in beiden Modi unverändert.

Der Ladezweig in `TaskShell` bleibt als Struktur bestehen (Compose braucht einen
Zweig), rendert aber nur noch den leeren Papiergrund ohne Text.

## Betroffene Stellen

- `gradle/libs.versions.toml`, `app/build.gradle.kts` — Abhängigkeit
  `androidx.core:core-splashscreen`.
- `app/src/main/res/values/themes.xml` — `Theme.AbcVorschule.Splash` mit hellem
  Grund, Icon und `postSplashScreenTheme`.
- `app/src/main/res/values-night/themes.xml` (neu) — allein der dunkle Grund,
  geerbt von der Basis (siehe Style-Aufbau).
- `app/src/main/AndroidManifest.xml` — Activity startet mit dem Splash-Theme.
- `app/src/main/java/app/abcvorschule/MainActivity.kt` — `installSplashScreen()`
  vor `super.onCreate()`, Haltebedingung, Austritts-Animation.
- `app/src/main/java/app/abcvorschule/ui/shell/TaskShell.kt` — Ladezweig ohne
  Text.

## Prüfung

Automatisiert ist hier wenig zu holen: der Splash ist Systemoberfläche, die
weder Compose-Tests noch Unit-Tests sehen. Die Prüfung ist entsprechend:

1. `./gradlew assembleDebug lint` läuft durch.
2. Kaltstart auf dem Gerät in **beiden** Modi
   (`adb shell cmd uimode night yes` / `no`, davor jeweils
   `adb shell am force-stop app.silbo.abcvorschule`), je ein Screenshot.
3. Sichtprüfung: zwischen Splash und Pfad-Screen liegt kein weißer und kein
   schwarzer Zwischenframe, und der Text „Silbo …" erscheint nicht mehr.
4. Fehlerfall: Der Splash gibt frei, wenn das Laden scheitert — nachstellbar,
   indem das Content-Asset im Build unlesbar gemacht wird; erwartet wird der
   `error`-Zweig, nicht ein stehender Splash.

Schriftskalierung ist hier ohne Belang — der Splash trägt keinen Text mehr.
