# Zehnerfeld für die Addition — Design

**Datum:** 2026-08-30
**Betrifft:** Trainer 7 (Rechnen), **nur Addition im Tipp-Modus**. Minus und Malnehmen bleiben unverändert.

## Problem

Ein Kind, das `16 + 8` lösen soll, hat keine Strategie für den Zehnerübergang. Der Screen hilft ihm dabei heute nicht:

1. **Im Aufgaben-Prompt gibt es keine Struktur.** Ab 11 zeigt `QuantityRepresentation` nur noch *ein* Symbol plus Ziffer (§8). Bei genau den Aufgaben mit Zehnerübergang sieht das Kind also zwei nackte Ziffern.
2. **Die Zähl-Hilfe kommt zu spät.** Sie klappt erst nach zwei Fehlversuchen auf (`MathHinting.CountingAidFromMisses`). Wer keine Strategie hat, rät also erst zweimal und darf *dann* rechnen.
3. **Fünferzeilen zeigen nie einen Zehner.** `CountingField.RowSize = 5` bündelt in Fünfern. Der Zehner — die Einheit, an der die ganze Strategie hängt — kommt im Bild nicht vor.
4. **Die Zahl entsteht nicht beim Kind.** Der gezählte Wert wird ins Antwortfeld gespiegelt (`NumberPadInput.mirroredValue`). Der Schritt vom Bild zur geschriebenen Zahl — genau der geübt werden soll — findet nicht statt.
5. **Beim Aufschreiben fehlt die Stellenwert-Ordnung.** Das Kind fragt bei „20": *welche Ziffer kommt zuerst?* Ein einzelnes Textfeld beantwortet das nicht.

## Lösung in einem Satz

Die Additionsaufgabe steht von Anfang an als **Zehnerfeld** auf dem Schirm: die erste Zahl als echte Bilder, die zweite als Platzhalter, die das Kind einzeln antippt und damit füllt. Volle Zehner rasten sichtbar und hörbar ein. Darunter steht die Aufgabe in Stellenwert-Schreibweise mit **getrenntem Zehner- und Einerfeld**.

## Der Screen

Für `16 + 8`, Legende `●` echtes Bild · `○` Platzhalter · `◉` pulsiert.

### Rundenstart

```
    ● ● ● ● ●   ● ● ● ● ●        10
    ● ● ● ● ●   ● ◉ ○ ○ ○
    ○ ○ ○ ○

              1  6
           +     8
           ─────────
            ┌───┐┌───┐        ➜
            │   ││   │
            └───┘└───┘
              ▲ Fokus
```

Die 16 steht fertig da, die 8 liegt als vier Platzhalter in der angebrochenen Zeile und vier in der nächsten. Vor dem ersten Tipp ist damit sichtbar, dass die zweite Zeile nur noch vier Plätze frei hat: die Zerlegung `8 = 4 + 4`, ohne ein Wort Erklärung.

### Nach vier Tipps — der Zehner rastet ein

```
   ┌──────────────────────────┐
   │ ● ● ● ● ●   ● ● ● ● ●    │   10
   ├──────────────────────────┤
   │ ● ● ● ● ●   ● ● ● ● ●    │   20   🔊 „zwanzig"
   └──────────────────────────┘
     ◉ ○ ○ ○
```

### Alles angetippt

```
   ┌──────────────────────────┐
   │ ● ● ● ● ●   ● ● ● ● ●    │   10
   ├──────────────────────────┤
   │ ● ● ● ● ●   ● ● ● ● ●    │   20
   └──────────────────────────┘
     ● ● ● ●

              1  6
           +     8
           ─────────
            ┌───┐┌───┐
            │ 2 ││ 4 │
            └───┘└───┘
```

Zwei gerahmte Zehner → Zehnerfeld, vier lose → Einerfeld. Die Reihenfolge im Bild ist die Reihenfolge im Eingabefeld.

## Regeln im Einzelnen

### Zehnerfeld (Bild)

- **Zehn Objekte pro Zeile**, nach dem fünften eine breitere Lücke („Kraft der Fünf").
- Die Objekte des **ersten Summanden sind echt**, die des **zweiten Platzhalter** — gleiches Emoji, stark transparent. Dieselbe Geister-Logik, die `MultiplicationMatrixGrid` schon benutzt (`MultiplicationMatrix.GhostAlpha`); ein Tipp macht einen Platzhalter echt, exakt wie eine Matrixreihe.
- **Antippbar ist immer nur der nächste Platzhalter**, und er pulsiert. Damit kann sich das Kind nicht verzählen, und es gibt keinen Fehltipp.
- **Kein Widerruf.** Ein zweiter Tipp auf ein bereits gefülltes Objekt tut nichts. Das ist nur zusammen mit der Regel darüber vertretbar: Widerruf zu sperren, während man überall hintippen kann, wäre eine Falle. Weil nur ein Objekt gleichzeitig antippbar ist, ist ein Rückweg schlicht nicht nötig.
- **Trefferfläche vor Bildgröße:** zehn Objekte nebeneinander drücken das Emoji auf ~20–22sp (≈30dp Zelle) — zu klein für einen Kinderfinger. Weil zu jedem Zeitpunkt **genau eine** Zelle antippbar ist, darf ihre Trefferfläche über die Nachbarn hinausragen: das aktive Objekt bekommt eine zentrierte Trefferfläche von `AbcDimens.kidTouch / 2` (40dp), mindestens aber Zellgröße. Überlappung ist folgenlos, weil kein Nachbar auf Tipps reagiert.
- **Volle Zehner rasten ein:** eine Zeile, deren zehn Objekte alle echt sind, bekommt einen Rahmen; aufeinanderfolgende volle Zeilen bilden einen zusammenhängenden Block. Rechts an jeder vollen Zeile steht ihr **laufender Stand** (`10`, `20`, `30`) in einer festen Rinne, nach dem Muster von `MultiplicationMatrix.RowLabelGutterDp`.
- Die letzte, angebrochene Zeile bleibt ohne Rahmen und ohne Marke — sie sind die Einer.

### Stimme

- Gesprochen wird **nur beim Einrasten eines Zehners**: der erreichte Stand als Wort (`GermanNumberWord.of(20)` → „zwanzig"), auf `SpeechChannel.Counting`, der eine laufende Ansage überlagern darf.
- Jeder andere Tipp ist stumm. Das ist die bewusste Abkehr vom Mitzählen in Einerschritten: gezählt wird in Zehnern, alles andere sieht das Kind.
- Haptik: `tick()` bei jedem Tipp, `nudge()` beim vollen Zehner.

### Stellenwert-Eingabe

- Die Aufgabe steht **untereinander**, Zehner über Zehner, Einer über Einer, mit Summenstrich. Keine „Z/E"-Kopfzeile: die Ausrichtung ist die Erklärung, und eine Beschriftung, die ein Vorschulkind nicht liest, ist Dekoration.
- Die Eingabefelder stehen **in denselben Spalten** wie die Ziffern darüber.
- **Zwei Felder, wenn das Ergebnis ≥ 10 ist, sonst eines.** Das verrät nichts: die Gesamtzahl der Objekte steht ohnehin im Bild, die Felderzahl folgt also nur dem, was das Kind schon sieht.
- **Fokus startet im Zehnerfeld.** Nach der Zehnerziffer springt der Fokus selbsttätig ins Einerfeld.
- **Tipp auf ein gefülltes Feld markiert seinen Inhalt**, die nächste Ziffer überschreibt ihn — kein Löschen nötig.
- Rücktaste im leeren Einerfeld springt zurück in den Zehner.
- Der Absenden-Pfeil ist erst aktiv, wenn alle Felder gefüllt sind.
- **Die System-Tastatur bleibt zu, bis das Kind ein Feld antippt.** Sonst verdeckt sie genau das Zehnerfeld, an dem es rechnen soll — dieselbe Regel, die heute schon für die offene Zähl-Hilfe gilt.
- **Nichts wird gespiegelt.** Der gezählte Stand landet nicht im Antwortfeld; die Zahl schreibt das Kind selbst. Ohne diese Änderung wäre die Aufgabe weg: acht Tipps, und die 24 stünde fertig da.

### Fehlversuche, Lob, Auflösen

- Es gibt in diesem Modus **keine aufklappende Zähl-Hilfe mehr** — das Bild ist von Anfang an da. `MathAttempt.aided` bleibt darum `false`, und eine richtige Antwort wird wie bisher gelobt (`MathHinting.praises`). Das Zehnerfeld ist die Darstellung der Aufgabe, keine Hilfestufe.
- Nach **2 Fehlversuchen** spricht die App den vorhandenen kuratierten Cue `MathHinting.CountingAidCueCollect` („Tippe auf die Bilder, um sie zu zählen.") — sie zeigt auf die Interaktion, statt eine neue Ansicht aufzuklappen.
- Der **Auflösen-Knopf** erscheint unverändert nach 4 Fehlversuchen (`ResolveFromMissesTyped`).

### Was unberührt bleibt

- **Minus und Malnehmen**: identisches Verhalten wie heute — Fünferzeilen, Zähl-Hilfe nach zwei Fehlversuchen, Spiegelung ins Feld, Mitzählen bei jedem Tipp.
- **Kachel-Modus** (`MathInputMode.Tiles`, kleine Zahlen bzw. `ParentMode.Beginner`): unverändert `VisualQuantityBoard`.
- Punkte, Fortschritt, Erfolgs-Zeremonie, Audio-Lock.

## Bausteine

Alle Rechen- und Zustandsregeln bleiben Compose-frei und damit als JVM-Test prüfbar — wie `CountingField`/`CountingState` heute.

| Datei | Aufgabe |
| --- | --- |
| `TenFrame.kt` (neu) | Layout-Regeln: Zeilen zu zehn, Fünfer-Lücke, Emoji-Größe aus der **gemessenen** Breite, Trefferfläche, Rinnenbreite, laufende Stände. |
| `TenFrameState.kt` (neu) | Tipp-Zustand: gegeben/Platzhalter, nächster Index, Einweg-Tipps, „welcher Zehner ist gerade voll geworden". |
| `TenFrameBoard.kt` (neu) | Compose-Darstellung: Zeilen, Rahmen um volle Zehner, Puls, Standmarken. |
| `PlaceValueInput.kt` (neu) | Eingaberegeln Compose-frei: Ziffern pro Feld, Feldzahl aus dem Ergebnis, Fokuswechsel, Zusammensetzen zur Zahl. |
| `PlaceValueAnswer.kt` (neu) | Compose: Stellenwert-Notation, ein oder zwei Ziffernfelder, Absenden-Pfeil. |
| `MathExercise.kt` | Verzweigung: `usePad && operation == Add` → neuer Screen, sonst alles wie gehabt. |
| `PRODUCT_PRINCIPLES.md` §8 | Die neuen Regeln festhalten, die alten als „gilt für Minus/Malnehmen" abgrenzen. |

`CountingField`, `CountingState`, `CountingAid`, `NumberPad` und `VisualQuantityBoard` bleiben unverändert — sie tragen weiter Minus und Malnehmen.

## Tests

**JVM (Pflicht):**
- `TenFrame`: Zeilen summieren sich auf die Gesamtmenge; keine Zeile länger als zehn; Emoji-Größe bleibt für jede zulässige Runde (Summe ≤ 30) bei jeder Breite ab 320dp innerhalb der Schranken; Trefferfläche nie unter Zellgröße.
- `TenFrameState`: vor dem ersten Tipp ist nichts gefüllt; nur der nächste Platzhalter ist antippbar; ein zweiter Tipp auf ein gefülltes Objekt ändert nichts; nach `right` Tipps ist alles echt; das Einrasten meldet genau dann einen Zehner, wenn eine Zeile voll wird, und für jede Aufgabe genau so oft, wie es volle Zehner gibt.
- `PlaceValueInput`: Feldzahl aus dem Ergebnis; Zusammensetzen; Überschreiben statt Anhängen; Absenden erst bei vollständiger Eingabe.

**Instrumentiert:** ein Stabilitäts-/Shot-Test nach dem Muster von `WordBuildStageStabilityTest`, der die Bühne bei `font_scale 1.3` und schmalem Gerät nicht überlaufen lässt.

## Bewusst nicht dabei

- Minus im Zehnerfeld (kommt, wenn Addition sich bewährt).
- Halbschriftliche Zwischenschritte („16 + 4 = 20, 20 + 4 = 24") als Text.
- Übertrags-Kästchen der schriftlichen Addition — das ist Klasse 3.
- Animiertes Zusammenrücken der Zehnerzeile; der Rahmen genügt.
