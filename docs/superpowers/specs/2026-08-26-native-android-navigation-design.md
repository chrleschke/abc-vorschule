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

Ziel ist ein Chrome, das aussieht wie eine native Android-App: Top App Bar mit
Titel und Sternen, ⋯ als schwebender Button, Schutzbereiche transparent, und
Vor/Zurück auf das reduziert, was es tatsächlich ist — ein Fallback, kein
Angebot ans Kind.

## Entscheidungen

1. **Header nativ.** M3 `TopAppBar`, transparent, Sterne im Titel-Slot.
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
- Pfad: `PathBackground` zeichnet bis an die Kanten; der Scroll-Inhalt bekommt
  oben und unten Content-Padding, damit nichts unter den Bars klebt, die
  Landschaft aber durchläuft.

Der dunkle Nav-Bar-Scrim für API 26 in `MainActivity` bleibt unverändert — er
hält dort die weißen System-Icons lesbar und hat mit dem Cream-Band nichts zu
tun.

## 2. Header

Neu: `ui/shell/AbcTopBar.kt`, ein Wrapper um M3 `TopAppBar` mit
`containerColor = Color.Transparent`.

| Screen | Navigations-Icon | Titel-Slot |
| --- | --- | --- |
| Pfad | keins | Stern + Punktezahl, rechtsbündig, mit `TopBarFloatingActionReserve` am Ende für den schwebenden ⋯ (Knopfbreite + Randabstand + Luft, nicht geschätzt) |
| Lektion | X als nackter `IconButton` (kein gefüllter Kreis — er wäre der einzige Knopfkasten in einer durchsichtigen Leiste), verlässt die Lektion direkt zum Pfad | Lektionstitel links, Stern + Punkte rechts |

Der Stern behält auf dem Pfad seinen `WarmInk`-Outline: dort steht er über dem
Himmel, nicht über Cream (Begründung steht am Aufrufort in `PathScreen`).

Der Lektionstitel ist `Lesson.title` — das elternseitige Label („M & A"). Es ist
keine Anweisung ans Kind und wird nicht vorgesprochen.

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
| `ui/shell/AbcTopBar.kt` | neu; Navigations-Icon als nackter `IconButton`, `TopBarFloatingActionReserve` aus der Knopfbreite gerechnet |
| `ui/components/AbcSegmentedProgress.kt` | neu, inkl. reiner Füll-Mathematik |
| `ui/shell/ParentGate.kt` | rund, schwebend |
| `ui/components/AbcButtons.kt` | `AbcNavChevron` gehäuselos, `AbcProgressBar` entfällt |
| `ui/shell/TaskShell.kt` | Wurzel ohne `safeDrawing`, Chrome neu aufgebaut |
| `ui/path/PathScreen.kt` | Kopfzeile → `AbcTopBar`, ⋯ schwebend, Insets als Content-Padding |
| `session/SessionModels.kt` | `trainerProgressLabel` entfällt |
| `docs/PRODUCT_PRINCIPLES.md` | §9 „Chrome oben" und §10 nachziehen |

## Prüfpunkte

- Prüfung bei `font_scale` 1.3, nicht nur 1.0 — die Titelzeile trägt jetzt Titel
  **und** Sterne in einer Reihe.
- Stern auf dem Pfad-Himmel weiterhin lesbar (Outline `WarmInk`).
- Lang-Druck-Gate auf dem schwebenden ⋯ funktioniert unverändert, auch mit
  TalkBack.
- Landschaft läuft sichtbar unter Status- und Nav-Bar durch, kein Cream-Band.
- Kein Inhalt liegt unbedienbar unter den System-Bars.
