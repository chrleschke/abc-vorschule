# Pfad-Screen kindgerecht — Design

Datum: 2026-07-31
Status: entwurf (Review durch Nutzer ausstehend)

## 1. Problem

Der Pfad-Screen ist der Einstieg der App und damit der Bildschirm, den das Kind am
häufigsten sieht. Aktuell besteht er aus:

- Knoten als schlichte Kreise (`PathScreen.kt`, `NodeSize = 92.dp`),
- geraden Linien zwischen den Knoten (`drawLine`, `MutedText` bei Alpha 0.22),
- einer Sinus-Kurve mit Periode 4, die hart auf exakt Mitte/rechts/Mitte/links rastet
  (`PathGeometry.points`),
- einem Hintergrund aus reiner Fläche `NightInk` (#0E1624).

Das liest sich als Diagramm, nicht als Weg. Ein Vorschulkind, das nicht lesen kann,
bekommt keinen Anhaltspunkt, *wohin* der Weg führt und *was* in einer Lektion vorkommt.

## 2. Ziel

Der Pfad soll als natürlicher Weg durch eine Nachtlandschaft lesbar sein: gepunktete,
weich geschwungene Trittspuren zwischen Wegweiser-Schildern, auf denen neben dem
Graphem drei Emojis aus der jeweiligen Lektion stehen.

Nicht-Ziele: kein Tag/Nacht-Wechsel, keine Landmark-Varianten pro Phase, keine
Bitmap-Assets, keine Tier- oder Wolkenanimationen.

## 3. Entscheidungen

| Frage | Entscheidung | Begründung |
| --- | --- | --- |
| Hintergrund | Nachtlandschaft, dark-only bleibt Prinzip | App wird abends genutzt; helle Flächen blenden und würden vom Rest der App abweichen |
| Knotenform | Durchgängig Holzschild am Pfosten | Eine Vektorform, konsistent lesbar, kein Asset-Aufwand |
| Emojis | 3 pro Schild, gesperrt als gedimmte Silhouette | Macht neugierig, verrät nichts Lesbares |

## 4. Architektur

Fünf neue Dateien, zwei geänderte. Die gesamte Geometrie- und Auswahl-Logik liegt in
reinen Kotlin-Objekten ohne Compose- oder `android.graphics`-Abhängigkeit — das Repo
hat **keine** androidTests, JVM-Unit-Tests sind die einzige automatisierte Absicherung.

```
ui/path/PathScreen.kt        (geändert)  Komposition der Ebenen
ui/path/PathGeometry.kt      (geändert)  Knotenpositionen, organischer
ui/path/PathTrail.kt         (neu, rein) Spline + Trittspuren-Punkte
ui/path/PathBackground.kt    (neu)       Verlauf, Sterne, Hügel
ui/path/PathSignNode.kt      (neu)       Wegweiser-Schild
ui/theme/Colors.kt           (geändert)  Nacht- und Holztöne
content/LessonEmojis.kt      (neu, rein) 3 Emojis je Lektion
```

### 4.1 Ebenen in `PathScreen`

Heute liegt der Hintergrund implizit im Theme und der Scroll-Container füllt den Screen.
Neu wird der Hintergrund ein eigener, **nicht mitscrollender** Layer:

```
Box(fillMaxSize)
├── PathBackground(scrollOffsetProvider = { scrollState.value })   // fix, Parallaxe intern
└── Column
    ├── Kopfzeile (ParentGate, Punkte)  — unverändert
    └── Box(verticalScroll, testTag "path_scroll")
        └── BoxWithConstraints
            ├── Canvas: Trittspuren
            └── PathSignNode je Lektion
```

`PathBackground` bekommt den Scroll-Offset als Lambda, nicht als Wert — gelesen wird er
erst im `graphicsLayer`-Block, damit Scrollen keine Recomposition auslöst.

### 4.2 `PathBackground`

Drei Schichten in einem einzigen `Canvas`:

1. **Vertikaler Verlauf** `NightDeep` → `NightInk` → `NightHorizon`. Der wärmere Ton
   liegt unten, damit der Horizont unter den Hügeln sitzt.
2. **Sterne**: 40 Punkte, Positionen aus `Random(42)` in `remember` erzeugt — deterministisch,
   damit nichts bei Recomposition springt. Radius 1–2dp, `SoftSand` bei Alpha 0.10–0.25.
   Das Funkeln kommt aus **einer** `rememberInfiniteTransition`; die Phase jedes Sterns ist
   `index * 0.37f` versetzt, statt pro Stern eine eigene Animation zu starten.
3. **Hügel**: drei gestaffelte Bézier-Bänder in `NightPanel` / `NightElevated` bei Alpha
   0.5 / 0.7 / 0.9, auf dem vordersten Band einige schlichte Nadelbaum-Dreiecke.
   Parallaxe: `translationY = -scrollOffset * 0.15f` je Band, hinterstes Band 0.05f.

**Augenfreundlichkeit** ist hier prüfbar definiert: keine Hintergrundfläche heller als
`NightElevated` (#223247), kein reines Weiß, Schildtext zu Schildfläche mindestens 4.5:1.

### 4.3 `PathGeometry` — organischer

Heute: `x = center + amplitude * sin(index * PI / 2)`, konstantes `spacing`. Das rastet
auf vier exakte Positionen und wirkt maschinell.

Neu:

- Periode 3.7 statt 4 (`sin(index * 2 * PI / 3.7)`), damit sich die Kurve nicht alle vier
  Knoten exakt wiederholt.
- Zusätzlicher deterministischer Versatz je Index, **proportional zur Amplitude**
  (±6 % der Amplitude). Die Proportionalität ist wichtig: auf sehr schmalen Screens ist
  die Amplitude 0, und der bestehende Test `narrowScreenCollapsesToAStraightLine`
  erwartet dann genau eine x-Position.
- y-Abstand variiert deterministisch um ±8 % um `spacing`.
- `DefaultSpacing` steigt 140f → 168f und `DefaultMargin` 96f → 132f, weil ein Schild
  breiter und höher ist als ein 92dp-Kreis.

**Konsistenz-Falle:** `contentHeight` berechnet heute `2 * margin + (count - 1) * spacing`.
Mit variablem Abstand stimmt das nicht mehr mit `points()` überein, und der Scrollbereich
würde am Ende abreißen. Beide Funktionen teilen sich deshalb einen neuen privaten Helfer
`yOffsets(count, spacing, margin): List<Float>`; `contentHeight` ist dann
`yOffsets.last() + margin`.

### 4.4 `PathTrail` — gepunkteter, weicher Weg

Reine Kotlin-Mathematik, kein `android.graphics.PathMeasure` (das gibt es im JVM-Test nicht):

- `PathTrail.polyline(points, samplesPerSegment = 24): List<PathPoint>` — Catmull-Rom-Spline
  durch alle Knoten, selbst implementiert und in Stützpunkte gesampelt. Endpunkte werden
  gespiegelt, damit auch das erste und letzte Segment eine Tangente hat.
- `PathTrail.dots(polyline, spacingPx, radiusPx): List<TrailDot>` — verteilt Punkte entlang
  der **Bogenlänge** (nicht je Stützpunkt, sonst stauen sie sich in Kurven). Abstand 18dp,
  Radius 4dp, Radius variiert deterministisch um ±15 % je Index.

Gezeichnet wird in einem `Canvas` unter den Schildern. Punkte **vor** dem letzten
erreichten Knoten warm (`SoftSand`, Alpha 0.45), danach gedimmt (`MutedText`, Alpha 0.16).
Der zurückgelegte Weg wird damit ohne ein einziges Wort sichtbar.

Der Grenzindex kommt aus den `LessonState`s: der höchste Index, dessen Lektion
`Mastered` oder `InProgress` ist. Gibt es keinen, ist der ganze Trail gedimmt.

### 4.5 `PathSignNode` — Wegweiser

Vektorform, keine Assets:

- **Brett** 136 × 86dp, `RoundedCornerShape(14.dp)`, deterministische Neigung −3°…+3°
  je Index über `graphicsLayer { rotationZ = … }` für Handgemacht-Optik.
- **Pfosten** 10dp breit, 30dp hoch, unter dem Brett, in `WoodPost`.
- **Zwei Nagelpunkte** in den oberen Brettecken.

**Ankerpunkt:** Der Geometrie-Punkt ist die **Pfostenbasis**, nicht die Brettmitte. Das
Schild steht damit auf dem Weg, statt dass der Weg quer durchs Brett läuft. Konkret wird
das Schild um `boardHeight / 2 + postHeight` nach oben versetzt gezeichnet. Die heutige
Zentrierung (`point.y - nodeHalf`) wird entsprechend ersetzt; Breite und Höhe brauchen
getrennte Halbwerte, `NodeSize` als eine Zahl entfällt.

Inhalt: Graphem in `titleLarge` (Größe unverändert), darunter eine Reihe mit drei Emojis
à 16sp. Die Emoji-Reihe hat **feste Höhe**, auch wenn sie leer ist — sonst springt die
Schildgröße zwischen autorierten und geplanten Lektionen.

Zustände:

| State | Brett | Kontur | Extra |
| --- | --- | --- | --- |
| `Mastered` | `WoodWarm` | `SoftMint` | Goldstern (`SoftGold`) oben rechts |
| `Available` | `WoodMid` | `SoftMint` | Puls bei `highlighted` (bestehende Logik) |
| `InProgress` | `WoodMid` | `SoftSky` | — |
| `Locked` / `Planned` | `WoodDark` | `MutedText` @ 0.28 | Schloss-Glyph, Emojis @ Alpha 0.18 |

Das Label bleibt in allen Zuständen `SoftSand`, gesperrt gedimmt auf `MutedText` @ 0.45 —
identisch zum heutigen Verhalten. Kontrast `SoftSand` auf `WoodWarm` ≈ 6.8:1.

**Touch-Ziel**: `clickable` umschließt Brett **und** Pfosten, damit ~116dp Höhe — deutlich
über `AbcDimens.kidTouch` (80dp). `onLockedTap` mit gesprochenem Hinweis bleibt unverändert;
ein Tipp ist nie ein stummes No-Op.

**Accessibility**: `contentDescription` bleibt `"$nodeDesc $label, $stateDesc"`. Die
Emoji-Reihe bekommt `clearAndSetSemantics {}`, sonst liest TalkBack „Maus Baum Ameise"
mitten in die Zustandsansage. `testTag("path_node_$label")` bleibt erhalten.

### 4.6 `LessonEmojis` — welche drei Emojis

`LessonEmojis.forLesson(pack: ContentPack, lesson: Lesson, limit: Int = 3): List<String>`

Nicht über `TrainerRound.scoredAtomIds()`: das liefert für `SentenceOrderRound` und
`CountAddRound` bewusst leere Listen und für `letter_trace` Buchstaben-Atome, deren
`emoji` im Content leer ist (alle 39 Buchstaben-Atome haben `""`). Stattdessen eine
explizite Quellenreihenfolge, die den Bildwortschatz der Lektion trifft:

1. `SoundPositionRound.atomId` — die Bildwörter der Lektion, im Trainer ohnehin nur als
   Emoji gerendert
2. `WordBuildRound.targetAtomId` — die gebauten Zielwörter
3. `CountAddRound.iconAtomId` — die Rechen-Ikonen derselben Lektion
4. `SentenceOrderRound.illustrationAtomId` — die Satz-Illustration

`LetterTraceRound.rewardEmoji` ist ein direkter Emoji-String und wäre eine fünfte Quelle,
bleibt aber außen vor: er ist der Belohnungs-Effekt des Trainers und soll auf dem Schild
nicht vorweggenommen werden.

Innerhalb jeder Quelle gilt die autorierte Reihenfolge (`pack.tasksOf(lesson)`), also kein
Zufall. Aufgelöst wird über `pack.atoms`, leere Emojis werden übersprungen.

**Dedupliziert wird über den Emoji-String, nicht über die Atom-ID.** `dach` und `haus`
tragen beide 🏠; zwei identische Häuser auf einem Schild sähen nach Fehler aus. Im
aktuellen Content-Pack (v7) greift diese Regel bei **keiner** der 26 Lektionen — sie ist
eine Absicherung für künftigen Content, nicht die Korrektur eines bestehenden Fehlers.

Gegen das echte Pack durchgerechnet ergibt die Regel u.a.:
`l01 M a` → 🐜🐭🌳 · `l05 F u` → 🦊🐘🦉 · `l13 Sch` → 🏫🐟👟 · `l17 St sp` → ⭐🪟🕷️ ·
`l26 Qu x+` → 🪼💧🚕. Alle 26 Lektionen liefern drei Emojis; keine läuft leer.

Lektionen mit `status = planned` haben keine `taskIds` und liefern eine leere Liste; das
Schild zeigt dann nur das Schloss. Im aktuellen Pack ist keine Lektion `planned`, der
Fall wird also nur durch einen synthetischen Unit-Test abgedeckt.

Berechnet wird die Map einmal beim Laden des Packs im ViewModel. `PathScreen` bekommt
sie als zusätzlichen Parameter `emojisByLessonId: Map<String, List<String>>` und bleibt
damit so zustandslos wie heute. `TaskShell.kt:100` wird entsprechend erweitert.

## 5. Farbtokens (neu in `ui/theme/Colors.kt`)

```kotlin
val NightDeep    = Color(0xFF080E18)  // Verlauf oben
val NightHorizon = Color(0xFF16283A)  // Verlauf unten, wärmer
val WoodDark     = Color(0xFF2A2018)  // gesperrtes Brett
val WoodMid      = Color(0xFF4A3728)  // verfügbares Brett
val WoodWarm     = Color(0xFF6B4E34)  // gemeistertes Brett
val WoodPost     = Color(0xFF33261B)  // Pfosten, alle Zustände
```

`SoftGold` existiert bereits und wird für den Meister-Stern wiederverwendet.

## 6. Tests

Alle neu als JVM-Unit-Tests unter `app/src/test/java/app/abcvorschule/`:

- **`ui/path/PathTrailTest`** — Spline läuft durch jeden Knoten (Stützpunkt bei t=0 jedes
  Segments == Knoten); Punktabstände entlang der Bogenlänge gleichmäßig ±10 %; Aufteilung
  walked/unwalked am erwarteten Index; 0 und 1 Knoten liefern leere Punktliste.
- **`content/LessonEmojisTest`** — höchstens 3; über den Emoji-String dedupliziert
  (Fall `dach`/`haus`); Reihenfolge über zwei Aufrufe stabil; `planned`-Lektion ohne
  `taskIds` liefert leer; Lektion ohne jedes Emoji liefert leer statt zu werfen.
- **`ui/path/PathGeometryTest`** — angepasst. Erhalten bleiben
  `emptyPathHasNoPoints`, `amplitudeStaysInsideTheMargins`,
  `narrowScreenCollapsesToAStraightLine`. Ersetzt werden
  `nodesAreStackedTopDownAtConstantSpacing` → *y wächst streng monoton*,
  `curveStartsCenteredAndSwingsRightThenLeft` → *Kurve wechselt die Seite und bleibt
  deterministisch*, `contentHeightLeavesMarginAtBothEnds` → *`contentHeight` == letztes
  y + margin*, geprüft gegen `points()` statt gegen eine eigene Formel.

Nicht automatisiert absicherbar (keine androidTests im Repo): das visuelle Ergebnis.
Dafür ein manueller Smoke-Schritt — `./gradlew :app:assembleDebug`, App starten,
Pfad über alle 26 Lektionen scrollen, prüfen: kein Ruckeln beim Scrollen, Schilder
überlappen an keiner Stelle, gesperrte Emojis sind nicht entzifferbar.

## 7. Doku-Folgeänderungen

- `docs/PRODUCT_PRINCIPLES.md` §5: „winkende S-Kurve, ein Knoten pro Lektion, Label =
  Graphem" → Nachtlandschaft, Wegweiser-Schilder, gepunkteter Trail, drei Lektions-Emojis.
- `docs/PRODUCT_PRINCIPLES.md` §4 ergänzen: Atom-Emojis werden auch außerhalb der Trainer
  verwendet (Pfad-Schilder).

## 8. Bewusst nicht enthalten

- Landmark-Varianten pro Phase, Tag/Nacht-Wechsel, Bitmap-Assets, Tier-/Wolkenanimationen.
- **Auto-Scroll zur aktuellen Lektion.** Mit 26 Lektionen ist der Pfad mehrere Screens
  lang und startet immer oben; das Kind muss selbst zum pulsierenden Knoten scrollen.
  Das ist eine bestehende Lücke, die dieses Redesign nicht verursacht und nicht behebt —
  sie gehört in ein eigenes, kleines Ticket, weil sie Verhalten ändert (Scrollposition,
  Rücksprung aus einer Lektion), nicht nur Optik.
