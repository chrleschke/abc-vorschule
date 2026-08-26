# Native-Android-Navigation — Design

Datum: 2026-08-26
Status: approved

## Problem

Das Chrome der App ist eigengebaut: eine selbstgebaute Kopfzeile aus drei
gleichgewichtigen Kästchen (Parent-Gate · Punkte · Schließen), darunter zwei
Chevron-Buttons in vollem Gehäuse, darunter Fortschrittsbalken, Textlabel „3/8"
und Runden-Punkte. Das sind fünf gestapelte Chrome-Zeilen über der eigentlichen
Aufgabe. Dazu kommt: die Wurzel-Box zieht `WindowInsets.safeDrawing` als Padding,
also liegt über und unter dem Inhalt ein Cream-Band statt der Landschaft.

Ziel ist ein Chrome, das aussieht wie eine native Android-App: eine Top App Bar,
die auf dem Pfad die Sterne trägt und in der Lektion nur den Zurück-Pfeil, ⋯ als
schwebender Button, Schutzbereiche transparent, und Vor/Zurück auf das reduziert,
was es tatsächlich ist — ein Fallback, kein Angebot ans Kind.

## Entscheidungen

1. **Header nativ.** M3 `TopAppBar`, transparent, ohne Titel; Sterne im
   Titel-Slot des Pfads, in der Lektion mittig unter dem Fortschritt.
2. **⋯ schwebend oben rechts**, nur auf dem Pfad-Screen. In der Lektion gar nicht.
3. **Schutzbereiche transparent.** Hintergrund bzw. Landschaft laufen bis an die
   physischen Kanten.
4. **Chevrons entkleidet.** Kein Button-Gehäuse mehr, nur der Glyph, gedämpft,
   links und rechts unter dem Header. Trefferfläche bleibt kindtauglich.
5. **Ein Fortschrittselement statt drei.** Segmentierte Bar: ein Segment je
   Trainer, Füllung im laufenden Segment nach Runden-Anteil.

## 1. Schutzbereiche

`windowInsetsPadding(WindowInsets.safeDrawing)` fällt aus der Wurzel-Box in
`TaskShell` weg. Stattdessen konsumiert jedes Element seinen Inset selbst:

- Top App Bar: `statusBars` (M3 `TopAppBar` macht das über seine `windowInsets`
  von allein).
- Content-Unterkante: `safeDrawing.only(Bottom)` + `AbcDimens.screenBottomExtra` —
  `safeDrawing` statt `navigationBars`, weil dort auch die System-Zahlentastatur
  hochkommt (§8) und den Aufgabenblock weiter hochschieben muss.
- Pfad: `PathBackground` zeichnet bis an die Kanten; der Scroll-Bereich füllt den
  ganzen Screen und legt die Leiste per `Alignment.TopStart` darüber, der
  Scroll-**Inhalt** hält oben `statusBars + TopBarHeight` und unten den
  Nav-Bar-Inset frei. Nicht als Zeile unter der Leiste: dann ist die Oberkante des
  Scroll-Bereichs eine Schnittkante mitten im Bild, und die Schilder werden dort
  abgeschnitten, obwohl der Hintergrund weiterläuft. So laufen sie stattdessen
  unter der durchsichtigen Leiste durch und enden erst an der physischen Kante.
  Weil die Knoten-y damit um den freigehaltenen Platz versetzt im Scroll-Inhalt
  liegen, rechnet `AutoScrollToHead` diesen Versatz auf jedes Scroll-Ziel auf —
  sonst parkt jedes Ziel um Status-Bar plus Leiste zu hoch.

Der dunkle Nav-Bar-Scrim für API 26 in `MainActivity` bleibt unverändert — er
hält dort die weißen System-Icons lesbar und hat mit dem Cream-Band nichts zu
tun.

## 2. Header

Neu: `ui/shell/AbcTopBar.kt`, ein Wrapper um M3 `TopAppBar` mit
`containerColor = Color.Transparent`.

| Screen | Navigations-Icon | Titel-Slot |
| --- | --- | --- |
| Pfad | keins | Stern + Punktezahl, rechtsbündig, mit `TopBarFloatingActionReserve` am Ende für den schwebenden ⋯ (Knopfbreite + Randabstand + Luft, nicht geschätzt) |
| Lektion | Pfeil nach links als nackter `IconButton` (kein gefüllter Kreis — er wäre der einzige Knopfkasten in einer durchsichtigen Leiste), verlässt die Lektion direkt zum Pfad | leer |

Der Stern behält auf dem Pfad seinen `WarmInk`-Outline: dort steht er über dem
Himmel, nicht über Cream (Begründung steht am Aufrufort in `PathScreen`).

Zurück-**Pfeil**, kein X: die Lektion ist ein Ziel, das man verlässt, kein Dialog,
den man schließt — das ist die Material-Regel für das Navigations-Icon einer Top
App Bar. Der Glyph liegt als `IconArrowBack` neben den anderen selbstgezeichneten
Icons; der Chevron bleibt der Rundennavigation.

Kein Lektionstitel: `Lesson.title` („M & A") ist ein elternseitiges Label an genau
der Stelle, an der das Kind zuerst hinsieht. Es hilft dem Kind nicht und wird nicht
vorgesprochen — also steht es dort nicht.

Der Punktestand steht in der Lektion **nicht** in der Leiste, sondern mittig unter
dem Fortschritt (§4), auf derselben Achse, auf der am Ende des Trainers der große
Stern hochkommt. In einer Ecke der Leiste war er eine Ecke von vielen; unter dem
Fortschritt wächst er dort, wo der Stern landet. Beide Orte teilen sich dasselbe
Element `ui/components/AbcStarCount.kt`, damit er nicht an einem Ort mitwächst und
am anderen nicht.

Keine `actions`, kein Overflow-Menü.

## 3. ⋯ als schwebender Button

`ParentGateButton` wird rund (CircleShape, leichter Schatten) und hängt per
`Alignment.TopEnd` an der Wurzel-Box des Pfad-Screens, über dem Status-Bar-Inset
plus 8 dp. Long-Press-Gate, Haptik und die TalkBack-Semantik bleiben unverändert
— die Kindersicherung wird durch den Umzug nicht weicher.

In der Lektion wird er nicht gerendert. Der einzige Weg ins Eltern-Sheet führt
damit über den Pfad.

## 4. Fortschritt und Chevrons

Eine Zeile direkt unter dem Header:

```
‹  ▮▮▮▮▯▯▯▯  ›
```

**Chevrons** ohne Gehäuse: nur der Vektor-Glyph in `WarmMuted`, aktiv bei ~55 %
Deckkraft, inaktiv bei ~20 %. Trefferfläche bleibt 48 dp, unsichtbar. Optisch
sind sie kein Angebot mehr, sondern eine Randnotiz — der Weg vorwärts ist das
Lösen der Aufgabe.

**Segment-Bar** (`ui/components/AbcSegmentedProgress.kt`): ein Segment je
Trainer, Lücken dazwischen und runde Enden nach der aktuellen M3-Spec für
lineare Indikatoren. Erledigte Segmente `SkyBlue`, das laufende füllt sich nach
Runden-Anteil, kommende `WarmMuted` bei 35 %. Der Gold-Puls beim Trainerwechsel
(§10) wandert aus `AbcProgressBar` mit.

Ersetzt ersatzlos: `AbcProgressBar`, das Textlabel `trainerProgressLabel` und
`RoundProgressDots`. Das Label liest das Kind nicht, und die Segmentzahl sagt
dasselbe.

Die Füll-Mathematik (Segment-Index → Füllanteil) lebt als reine Funktion
`SegmentedProgress.fillOf` neben der Composable und bekommt einen JVM-Test:
Rundenanteil im laufenden Segment, ein einziger Trainer, Runden-Zahl 0, und ein
Rundenindex jenseits der Rundenzahl. Der Puls selbst bleibt ungetestet — er
hängt an Compose-Animationsstate, den ein reiner JVM-Test nicht erreicht.

## 5. Betroffene Dateien

| Datei | Änderung |
| --- | --- |
| `ui/shell/AbcTopBar.kt` | neu; Navigations-Icon als nackter `IconButton` (Zurück-Pfeil), kein Titel, Punkte optional, `TopBarFloatingActionReserve` aus der Knopfbreite gerechnet, `TopBarHeight` für den Pfad-Overlay |
| `ui/components/AbcStarCount.kt` | neu; Stern + Zahl als ein Element für Leiste und Lektion |
| `ui/components/AbcIcons.kt` | `IconArrowBack` dazu |
| `ui/components/AbcSegmentedProgress.kt` | neu, inkl. reiner Füll-Mathematik |
| `ui/shell/ParentGate.kt` | rund, schwebend |
| `ui/components/AbcButtons.kt` | `AbcNavChevron` gehäuselos, `AbcProgressBar` entfällt |
| `ui/shell/TaskShell.kt` | Wurzel ohne `safeDrawing`, Chrome neu aufgebaut |
| `ui/path/PathScreen.kt` | Kopfzeile → `AbcTopBar`, ⋯ schwebend, Insets als Content-Padding |
| `session/SessionModels.kt` | `trainerProgressLabel` entfällt |
| `docs/PRODUCT_PRINCIPLES.md` | §9 „Chrome oben" und §10 nachziehen |

## Prüfpunkte

- Prüfung bei `font_scale` 1.3, nicht nur 1.0.
- Erstes Schild auf dem Pfad vollständig sichtbar, inklusive Marker darüber; beim
  Scrollen wird ein Schild erst an der Bildschirmkante beschnitten, nicht an einer
  Kante mitten im Bild.
- Punktestand in der Lektion auf der Achse des großen Sterns aus `SuccessBurst`.
- Stern auf dem Pfad-Himmel weiterhin lesbar (Outline `WarmInk`).
- Lang-Druck-Gate auf dem schwebenden ⋯ funktioniert unverändert, auch mit
  TalkBack.
- Landschaft läuft sichtbar unter Status- und Nav-Bar durch, kein Cream-Band.
- Kein Inhalt liegt unbedienbar unter den System-Bars.
