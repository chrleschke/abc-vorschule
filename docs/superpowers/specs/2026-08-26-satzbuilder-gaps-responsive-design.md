# Satz-Architekt: Pegs passen sich dem Screen an, Fill-Morph beim Einrasten

Datum: 2026-08-26 · Status: implementation-ready

## Problem

Beim Satz-Architekten (Trainer 5) lag das letzte Wort teilweise außerhalb der
Bühne. Ein Peg jenseits des Bildschirmrands nimmt keine Karte an — die Runde war
dann nicht lösbar.

Die Ursache ist keine Randbedingung, sondern der Normalfall. `SentenceOrderTrainer`
legte die Pegs in eine **einzelne, nicht umbrechende `Row`** und holte die Breite
aus `WordFrameSizing` — einer Rechnung, die für den *Wort-Bauer* gebaut ist, wo ein
Rahmen **einen Buchstaben** hält. Auf ganze Wörter angewandt kettet sie drei Fehler
aneinander:

1. **Uniforme Breite.** Alle Pegs bekamen die Breite des längsten Wortes der Runde.
   „ist" zahlte für „schwimmt".
2. **Der `MinGlyphSp`-Floor greift immer.** Ein Achtzeichen-Wort in einem
   gleichverteilten Rahmen ergibt rechnerisch ~12sp, also gewinnt der 20sp-Floor.
3. **`fittedFrameWidthDp` weitet dann den Rahmen** auf die Glyphbreite — bei einem
   Wort ist das ein Vielfaches der verteilten Breite.

Nachgerechnet gegen die 396dp, die `ExerciseStage` maximal hergibt, mit dem
tatsächlich autorierten Content des Satz-Architekten:

| Satz | alte Reihenbreite @ font_scale 1.0 | @ 1.3 |
| --- | --- | --- |
| der Fisch schwimmt | 418dp | 521dp |
| Oma hat einen Hut | 388dp | 474dp |
| wir gehen den Weg | 388dp | 474dp |
| das ist mein Papa | 372dp | 400dp |
| Mama ist im Haus | 372dp | 400dp |
| hier sind Häuser | 331dp | 409dp |
| die Spinne spielt | 331dp | 409dp |

Bei `font_scale 1.3` — der Einstellung des Testgeräts — lief **jeder** mehrwortige
Satz über. Weil `Arrangement.spacedBy(…, CenterHorizontally)` den Überlauf auf beide
Seiten verteilt, verschwanden erstes *und* letztes Wort.

## Entscheidungen

**Kein Umbruch.** Ein Satz steht in einer Zeile. Die Alternative (zweite Zeile, um
den Glyphen über der 20sp-Lesbarkeitsschwelle zu halten) wurde bewusst verworfen:
sie kostet Höhe im Aufgabenblock, den `ExerciseStage` weder scrollt noch clippt.

**Per-Wort-Silhouette.** Jeder Peg ist so breit wie sein eigenes Wort. Das ist der
Löwenanteil der zurückgewonnenen Breite und gleichzeitig didaktisch erwünscht:
Längen-Matching ist eine Vorlese-Vorstufe.

**Squish-Settle statt Polygon-Morph.** Der Shape-Morph beim Füllen wird mit
`graphicsLayer` und `drawBehind` gebaut, nicht mit `androidx.graphics.shapes`. Eine
blobbige Zielform würde das Wort im Peg beschneiden, und das Wort ist die Aufgabe.

## Entwurf

### `SentencePegSizing` (neu, Compose-frei)

Eigenes Objekt neben `WordFrameSizing`, weil die Semantik anders ist: ganze Wörter
statt Buchstaben, hugsende statt verteilende Rahmen. Verbindliche Rangfolge:

1. **Die Reihe passt** — auf jeder Breite, bei jeder Systemschriftgröße.
2. **Jeder Peg bleibt tippbar** (`MinPegWidthDp` = 56dp).
3. **Jeder Peg trägt sein eigenes Wort.**
4. **Der Glyph nimmt, was übrig ist**, gedeckelt bei 46dp.

`solve(availableDp, words)` liefert eine Glyphgröße für den ganzen Satz (gemischte
Größen lesen sich nicht als Satz) und eine Liste Peg-Breiten. Erst mit dem bequemen
12dp-Abstand; nur wenn der Glyph dann unter 20dp fiele, wird auf 4dp gezogen — die
gleiche Rangfolge wie `WordFrameSizing.gapDp` („Rahmen gewinnen über Weißraum").

Der Kern ist ein **Water-filling-Solve**: der Glyph wird gegen die freie Breite
gelöst, dann wird der erste Peg, der unter 56dp fiele, auf den Boden festgenagelt
und der Rest neu gelöst. Ohne diesen Schritt wäre der Fit gelogen — „im" nachträglich
von 41dp auf 56dp anzuheben holt 15dp aus dem Nichts, und das ist wieder Überlauf.
Die Schleife ist monoton und endet nach höchstens einem Durchlauf je Wort.

**Skalenunabhängigkeit.** `glyphSp(glyphDp, fontScale)` teilt durch `fontScale`
(bestehendes Muster in `WordFrameSizing.glyphSp` und `FinaleLayout.capEffectiveSize`).
Damit ist die *gerenderte* Reihenbreite bei `font_scale` 1.0, 1.3 und 2.0 dieselbe,
statt bei jeder Stufe weiter über den Rand zu wachsen. Das ist der Grund, warum die
Ein-Zeilen-Entscheidung überhaupt tragfähig ist.

**Kein Glyph-Floor im Layout.** Ein unerreichbarer Peg ist eine kaputte Aufgabe, eine
kleine Schrift nur eine unschöne. Statt das zu verstecken, hält `ReadableGlyphDp`
(18dp auf `ReferenceWidthDp` = 348dp) die Grenze fest und ein Test prüft den echten
Pack dagegen.

**Eine Reißleine.** Fünf Pegs à 56dp plus 4dp Abstand brauchen 296dp; ein 320dp-Gerät
stellt 256dp bereit. Dort schrumpft die ganze Reihe gleichmäßig auf ~48dp je Peg —
weiter über Androids eigenem 48dp-Minimum und vor allem vollständig sichtbar.

Ergebnis für den autorierten Content (alle Werte gerenderte dp):

| Satz | 396dp | 296dp |
| --- | --- | --- |
| der Fisch schwimmt | 28,1 | 20,8 |
| Oma hat einen Hut | 29,4 | 21,8 |
| hier sind Häuser | 32,1 | 22,2 |
| Mama (Einwort) | 46,0 | 46,0 |

Alles über der 20sp-Schwelle: für den Satz-Architekten kostet die Ein-Zeilen-
Entscheidung nichts.

### Fill-Morph (Squish-Settle)

Eine Feder von 0 nach 1 mit `dampingRatio = 0.42`, `Spring.StiffnessMediumLow`.
`settle - 1` ist damit ein gedämpfter Wackler: startet bei −1, schwingt über 0
hinaus, läuft auf 0 aus. Daraus abgeleitet:

- `scaleX` = 1 + 0,09 · Wackler — startet 0,91, federt über 1,0: das horizontale
  Wabbeln.
- `scaleY` = 1 − 0,05 · Wackler — gegenläufig, damit es als *Quetschung* liest und
  nicht als Zoom.
- Eckradius = 16dp − 8dp · Wackler, Boden 12dp — der eigentliche Shape-Morph.

Gelesen wird die Feder ausschließlich in der **Zeichenphase** (`graphicsLayer`,
`drawBehind`), wie in `PathHereMarker`: eine Federphase darf nicht 300ms lang
rekomponieren, und die von `DropZone` registrierten Bounds dürfen nicht zittern,
sonst wandert das Ziel unter dem Finger weg. Weil `drawBehind` den Hintergrund
selbst zeichnet, ersetzt es `Modifier.background` **und** `Modifier.border`; der Rand
wird um seine halbe Strichbreite eingerückt, damit er wie `border` innen sitzt.

Nur die eigene Tat federt (`morphOnFill = !resolved`). Nach „Auflösen" fallen alle
Pegs gleichzeitig — fünf Wackler im Chor wären eine Feier für etwas, das das Kind
nicht geschafft hat.

### Der fertige Satz

`AnimatedContent` zeigt nach dem letzten Peg den Satz als Textzeile. Die stand in
`headlineSmall` und lief selbst über: „Oma hat einen Hut" braucht dort bei
`font_scale 1.3` mehr als die Bühne hergibt. Sie wird jetzt mit
`completedGlyphDp` gegen die gemessene Breite gelöst.

## Tests

- `SentencePegSizingTest` (Unit): Fit auf vier Breiten für **jeden** Satz im Pack;
  Trefferflächen-Boden; Silhouette; Abstand gibt vor dem Glyphen nach;
  Skalenunabhängigkeit; die Autorierungs-Grenze; der fertige Satz.
- `SentenceOrderPegBoundsTest` (instrumentiert): misst die **gerenderten** Peg-Bounds
  gegen die Bühnenkante, über 4 Sätze × 3 Breiten × 3 Systemschriftgrößen. Nötig,
  weil `GlyphAspect` eine Schätzung der echten Zeichenbreite ist — nur ein
  gemessener Lauf zeigt, ob sie reicht.
- `SentenceOrderPegShotTest` / `SentenceOrderMorphShotTest` (instrumentiert):
  behaupten nichts, rendern PNGs für den menschlichen Blick — Layout in mehreren
  Breiten, und ein Filmstreifen des Morphs bei angehaltener Testuhr.

## Nebenbefund

Espresso kam transitiv in Version 3.5.0 über `androidx.test.ext:junit` herein. Liegt
es im Klassenpfad, ruft `ComposeTestRule.waitForIdle()` `Espresso.onIdle()` auf, und
dessen `InputManagerEventInjectionStrategy` sucht
`android.hardware.input.InputManager.getInstance` — ab API 36 nicht mehr vorhanden.
Jeder instrumentierte Test starb beim ersten `waitForIdle`. Espresso ist deshalb
ausdrücklich auf 3.7.0 angehoben, obwohl kein Test es direkt benutzt.
