# Rechnen ohne Raten — Zahlen-Eingabe ab 11 und Zähl-Hilfe

**Datum:** 2026-08-26
**Status:** design, freigegeben
**Betrifft:** Trainer 7 (Rechnen), `ui/exercise/Math*`, `progress/ProgressionEngine`

## Problem

Das Kind rät im Rechen-Trainer. Der Grund steckt in der Mechanik, nicht im Kind:

- Die visuelle Mengenaufgabe zeigt **genau 3 Kacheln** — Raten trifft mit 33 %.
- Die Distraktoren sind bewusst *nah* (`answer-1`, `answer`, `answer+1`,
  `MathHinting.threeChoices`), ein Fehlgriff bekommt also „du bist nah dran".
- Ein Fehlversuch kostet nichts: gesprochenes Feedback, beliebig viele weitere
  Versuche, nach 2 Misses erscheint „Auflösen".

Dreimal tippen löst jede Aufgabe. Rechnen lohnt sich nicht.

Die Zahlen-Eingabe (`NumberPad`) existiert bereits, hängt aber allein an
`ScaffoldLevel.Advanced` — also am Eltern-Modus bzw. an 3× richtig in Folge pro
Fakt. Sie greift damit nie dort, wo Raten am meisten schadet: bei den schweren
Aufgaben.

## Ziel

Bei Ergebnissen über 10 wird die Antwort **eingetippt** statt aus drei Kacheln
gewählt. Ein Kind, das nicht rechnen kann, darf trotzdem nicht steckenbleiben —
es bekommt nach zwei echten Fehlversuchen eine **Zähl-Hilfe**, in der es die
Rechnung mit dem Finger ausführt.

Nicht-Ziel: Strafen. Es gibt weiterhin keine roten Markierungen, keinen
Punktabzug, keine Versuchsbegrenzung (PRODUCT_PRINCIPLES §8).

## Entscheidung 1 — Wann getippt wird

> Zahlen-Eingabe, wenn `ScaffoldLevel.Advanced`
> **oder** (`answer > 10` **und** `ParentMode != Beginner`).

- `answer > 10` ist genau das Schwierigkeitsband `hard`/`expert`
  (`ProgressionEngine.bandFor`: easy ≤5, medium ≤10, hard ≤20, expert ≤30).
- Das Gate prüft den **Eltern-Modus**, nicht das abgeleitete `ScaffoldLevel`.
  Das ist der Kern der Entscheidung: der Default ist `ParentMode.Auto`, und dort
  startet ein frisches Kind auf `ScaffoldLevel.Beginner`. Gegen das Scaffold
  geprüft würde die Regel beim Normalnutzer also nie greifen.
- Wer im Eltern-Sheet ausdrücklich „Mit Hilfe" (`ParentMode.Beginner`) wählt,
  behält überall die Kacheln. Ausdrückliche Elternentscheidung schlägt
  Aufgabenschwere — das ist die einzige Ausnahme.

Effekt: Rate-Trefferquote fällt bei schweren Aufgaben von 33 % auf ~3 %.

## Entscheidung 2 — Die Zähl-Hilfe

Nach 2 Fehlversuchen wird der **Aufgabenbereich antippbar**. Das Kind führt die
Rechnung mit dem Finger aus, ein Zähler läuft mit, und der erreichte Wert steht
live im Eingabefeld — das Kind drückt nur noch Absenden.

Die Zähl-Hilfe **ersetzt die Aufgabenvisualisierung an Ort und Stelle**, sie
kommt nicht als zusätzlicher Block darunter. Sonst stünde dieselbe Aufgabe
zweimal auf dem Schirm (PRODUCT_PRINCIPLES §9), und auf einem Telefon wäre für
beides ohnehin kein Platz. Die symbolische Ziffernzeile („15 − 6 = ?") und der
Speaker bleiben, wo sie sind.

Grundregeln:

- Jedes Objekt trägt einen Haken-Zustand. Ein zweiter Tipp nimmt den Tipp
  zurück (Verzähler bleibt korrigierbar, keine Sackgasse).
- Der Zähler ist eine mitlaufende Ziffer im Haus-Stil (Zahl unter der
  Mengengruppe, §8).
- Der Zählerwert wird in das Antwortfeld gespiegelt — **erst ab dem ersten
  Tipp**. Einen zweiten, eigenen Zähler-Text zeigt die Zähl-Hilfe **nicht**:
  dieselbe Zahl zweimal im selben Bild ist genau das, was §9 verbietet, und der
  Platz fehlt dem Feld. Vorher bleibt das Feld leer: bei Plus stünde sonst sofort eine 0 im
  Feld, bei Minus sofort der linke Operand. Technisch heißt das
  `countedValue: Int?` = `null`, solange nichts angetippt wurde.
- Das Kind kann den Wert von Hand überschreiben; Absenden ist jederzeit
  möglich, nie erzwungen.

### Pro Rechenart

Die Geste **ist** die Rechenart — die drei Operationen fühlen sich bewusst
unterschiedlich an:

**Plus („7 + 8")** — einsammeln.
Ein Feld mit 15 Objekten, alle antippbar. Die letzten 8 sind gerahmt — das sind
die, die dazukommen. Der Zähler startet bei 0 und läuft durch: 1…15.

Der Rahmen bedeutet überall dasselbe: *das ist die zweite Zahl*. Was mit ihr
passiert, entscheidet die Rechenart — bei Minus geht sie weg (und nur sie ist
antippbar), bei Plus kommt sie dazu (und alles ist antippbar).

**Minus („15 − 6")** — wegnehmen.
**Ein** Feld mit 15 Objekten, Zähler startet bei 15. Die **letzten 6 sind
gerahmt** — das sind die, die weggehen — und **nur sie sind antippbar**.

- Tipp auf ein gerahmtes Objekt → es verblasst, Zähler 15 → 14 → …
- Ein Tipp auf ein bleibendes Objekt tut nichts. Kein Fehler, keine Meldung:
  es ist schlicht nicht Teil der Aufgabe.
- Nach 6 Tipps steht der Zähler auf 9, die 9 steht im Feld. Was ungerahmt und
  voll deckend stehen bleibt, ist die Antwort — auf einen Blick abzählbar.

Der Rahmen ist der Kern dieser Variante: das Kind muss *nicht* mitzählen, wie
viele es schon weggenommen hat — die Struktur trägt die Zahl, und es kann gar
nicht zu viel wegnehmen. Ohne sie müsste es zwei Zahlen gleichzeitig verfolgen
(„wie viele weg" und „wie viele übrig"), und das ist im Vorschulalter zu viel.

Verworfene Alternative 1: *Rest zählen* — die App streicht die 6 durch, das Kind
zählt die verbliebenen 9 aufwärts. Abgelehnt, weil dann die App die Subtraktion
ausführt und das Kind nur noch abzählt. Rückwärtszählen ist zudem selbst eine
Vorschul-Fähigkeit, die hier trainiert werden soll.

Verworfene Alternative 2: *Weg-Zone* — die weggenommenen Objekte wandern in eine
eigene Zone mit `right` leeren Plätzen. Gebaut, am Gerät verworfen: die Bewegung
in eine zweite Zone ist für ein Vorschulkind zu viel auf einmal, die Zone
brauchte eine eigene Ziffer, die sich mit allen anderen Zahlen im Bild stapelte,
und sie verdoppelte die Zeilenzahl des Feldes.

**Malnehmen („4 × 5")** — Raster füllen.
Dieselbe Matrix wie im Prompt, aber **die Reihe ist die Einheit**, nicht die
Zelle: ein Tipp macht eine ganze Geisterreihe echt, und der Zähler springt in
Schritten — 5, 10, 15, 20.

Zwanzig Objekte einzeln anzutippen trainiert Zählen in *Einerschritten*, also
genau das, was Multiplikation nicht ist; reihenweise ist es Zählen in Schritten,
und das ist die Sache selbst. Nebenbei fällt damit die Tipp-Flut weg, die 30
Einzelzellen bedeuten würden.

Anders als bei Plus und Minus wird eine Einheit hier beim Antippen **echt**,
statt zu verblassen: Malnehmen ist Auffüllen, und die Geisterreihen aus §8 sind
genau das, was das Kind vervollständigen soll. Die Zeilennummern behalten dabei
volle Deckkraft — sie sind die Zählhilfe, kein Teil des Platzhalters (§8).

Die Matrix wird in der Zähl-Hilfe **deutlich größer** als im Prompt
(`CountingField.matrixEmojiSizeSp` statt `MultiplicationMatrix.emojiSizeSp`):
dort teilt sie sich den Platz mit dem Antwortbereich, hier hat sie den
Aufgabenblock für sich, und jede Reihe muss mit dem Finger zu treffen sein.

### Mengen ab 11 materialisieren sich

Heute gilt: ab 11 steht ein einzelnes Symbol mit der Ziffer, und sobald *ein*
Operand ≥ 11 ist, kippt die ganze Runde in diesen Modus
(`QuantityRepresentation.forceSymbolicFor`). Bei „18 − 4" gäbe es also nichts
zum Antippen.

In der Zähl-Hilfe wird die Menge deshalb **aufgeklappt**: 18 echte Objekte in
Zeilen. Das bricht PRODUCT_PRINCIPLES §8 („ab 11 nur Symbol + Ziffer") bewusst
auf — aber ausschließlich in der Zähl-Hilfe nach zwei Fehlversuchen, **nie** im
Aufgaben-Prompt. Der Prompt-Grund für §8 (keine Emoji-Wand als Aufgabe) gilt
dort weiter; hier ist die Menge nicht Aufgabe, sondern Werkzeug.

Gebündelt wird in **Fünfern**, nicht in Paaren (`QuantityGrouping.clusters`) —
die Fünferbündelung ist die Struktur, die das Kind für den Zahlenraum 20/30
ohnehin braucht. Beide Operanden teilen sich **ein** Feld; zwei getrennte Blöcke
kosteten bis zu sieben Zeilen und drückten das Emoji auf 20sp, gemeinsam sind es
höchstens sechs und mindestens 24sp. Der Rahmen trägt die Gruppierung dabei
genauso gut wie ein Zeilenumbruch.

### Aufgabe und Anleitung

Über dem Feld steht die Aufgabe als **Ziffernzeile** („15 − 6 = ?"), solange die
Zähl-Hilfe offen ist; die symbolische Zeile von `MathExercise` schweigt dann.
Ohne sie verlöre besonders Minus seine Aufgabe ganz, sobald der gesprochene
Prompt verklungen ist — dort steht ja kein zweiter Mengenblock mehr. Dieselbe
Begründung, aus der die Multiplikationsmatrix ihre Gleichung längst selbst
schreibt (§8).

Die **nächste offene Einheit pulsiert** sanft in der Deckkraft und führt so durch
die Aufgabe. Der Puls, nicht der Ton, ist die eigentliche Anleitung: eine
Ansage, die nur bei vorhandener TTS-Stimme oder gerendertem Clip ankommt, wäre
keine.

Bei Minus läuft der Puls **von hinten nach vorne**: weggenommen wird vom Ende
der Menge, und der Zähler zählt rückwärts. Ein Puls, der vorne anfinge, liefe
der Zahl entgegen.

Eine **erledigte** gerahmte Zelle bekommt einen deutlich helleren Rahmen. Bliebe
er gleich dunkel, sähe ein schon abgezähltes Objekt genauso „dran" aus wie ein
offenes, und der Rahmen verlöre genau die Information, für die er da ist.

### Audio

- **Beim Aufklappen:** ein gesprochener Cue als Verstärkung — „Tippe auf die
  Bilder, um sie zu zählen." bzw. „… um sie wegzunehmen.". Kinder lesen nicht
  (§Audio-First).
- **Pro Tipp:** die erreichte Zahl wird mitgesprochen — Mitzählen ist der Kern
  der Übung, und ohne Stimme zählt das Kind stumm. Dafür bekommt die Zähl-Hilfe
  einen **eigenen Kanal** `SpeechChannel.Counting` mit eigenem Clip-Player: die
  Zahl darf eine laufende Ansage **überlagern**, statt sie abzuwürgen oder von
  ihr abgewürgt zu werden. Auf `Feedback` ginge das nicht — `speak` ruft dort
  `stopOutput(channel)` vor dem Enqueue, und bei jedem Tipp eine Zahl heißt, dass
  sich die Zahlen gegenseitig zerschnitten.
- Die Zahlen 1…30 stehen als `"n."` in `extra-strings.json` und teilen sich die
  Clips mit dem Miss-Echo in `SessionViewModel`, das dieselbe Form spricht.
- Zusätzlich **Haptik** pro Tipp, damit die Rückmeldung auch ohne Ton ankommt.

### Tastatur

Die Zahlen-Eingabe holt beim Aufbau selbst den Fokus und zeigt die
System-Tastatur (`NumberPad`, `LaunchedEffect(enabled)`). Die würde das Zählfeld
verdecken. Beim Aufklappen der Zähl-Hilfe wird die Tastatur deshalb
**eingeklappt**; ein Tipp ins Eingabefeld holt sie zurück.

## Entscheidung 3 — Die Eskalationsleiter

| Fehlversuche | Was passiert |
| ------------ | ------------ |
| 0–1 | Nur gesprochenes Feedback (`MathHinting.missFeedback`), wie heute |
| 2 | Zähl-Hilfe klappt auf |
| 4 | „Auflösen"-Knopf erscheint |

Heute erscheint „Auflösen" bereits bei 2 Fehlversuchen. Er rückt nach hinten,
damit die Hilfe nicht übersprungen werden kann — ein echter Ausweg bleibt aber
erhalten. Gilt nur im Tipp-Modus; der Kachel-Modus behält 2.

## Architektur

### Neue Grenze zwischen Fortschritt und UI

`ScheduledTrainer.mathScaffolds: Map<String, ScaffoldLevel>` wird zu
`mathInputs: Map<String, MathInputMode>` mit `enum class MathInputMode { Tiles, Typed }`.

Begründung: `mathScaffolds` hat genau einen Konsumenten (`MathExercise`, dort
allein für `usesNumberPad`), also kein Ripple. Die UI bekommt damit *was sie
zeigen soll* statt roher Fortschrittsdaten, und die Regel lebt an genau einer
Stelle. Der Cache-Zeitpunkt bleibt wie dokumentiert (`SessionModels.kt`):
pro Runde beim Scheduling, damit ein Moduswechsel ab der nächsten Runde greift.

### Reine, testbare Regeln

- `MathHinting.inputFor(scaffold, parentMode, answer): MathInputMode` — die
  Entscheidung aus Abschnitt 1. Compose-frei, erweitert `MathHintingTest`,
  ersetzt das heutige `usesNumberPad(scaffold)`. Gibt direkt den Modus zurück
  statt eines Boolean, damit die Regel und der Map-Wert in
  `ScheduledTrainer.mathInputs` denselben Typ sprechen.
  Konstante `TypedAnswerFrom = 11` mit eigener Bedeutung (nicht
  `QuantityRepresentation.SymbolicFrom` mitbenutzen, auch wenn der Wert
  zufällig gleich ist).
- Die Miss-Schwellen der Eskalationsleiter werden ebenfalls Konstanten in
  `MathHinting` (`CountingAidFromMisses = 2`, `ResolveFromMissesTyped = 4`),
  damit die Leiter an einer Stelle steht und testbar bleibt.
- Neu `CountingField.kt` — reine Layout- und Zustandslogik, unit-getestet:
  - Fünferreihen-Aufteilung einer Menge bis 30
  - Emoji-Größe nach Gesamtmenge (Muster: `MultiplicationMatrix.emojiSizeSp`,
    `QuantityGrouping.promptEmojiSizeSp`)
  - Startzustand je Operation (Plus/Mal: alles offen, Zähler 0; Minus: alles
    angehakt, Zähler `left`, Weg-Zone mit `right` Plätzen)
  - Tipp-Reduktion: Zustand + Tipp → neuer Zustand + Zählerwert, inkl.
    Deckel bei `right` weggenommenen Objekten

### Compose

- Neu `CountingAid.kt` — rendert `CountingField` und meldet den Zählerwert nach
  oben. Kein eigener Fachzustand.
- `NumberPad` bekommt `countedValue: Int?`; ein `LaunchedEffect` spiegelt ihn
  ins Feld. Die `resetToken`-Logik bleibt unangetastet (sie behebt einen
  dokumentierten Bug). Der Effekt muss auch auf `resetToken` reagieren, sonst
  steht das Feld nach einem Miss leer, während die Haken noch gesetzt sind.
- `MathExercise` bekommt `input: MathInputMode` statt `scaffold: ScaffoldLevel`
  und hält den Zähl-Hilfe-Zustand pro `roundKey`.

### Größen

Alles gegen **font_scale 1.3** auslegen (Testgerät). Worst Case: 30 Objekte in
6 Fünferreihen plus Weg-Zone. Bestehende Deckel bleiben: `MaxMathQuantity` 30,
Matrix max. 5×6.

## Testplan

Unit (JVM):

- `MathHintingTest`: Tipp-Modus für alle Kombinationen aus `ScaffoldLevel`,
  `ParentMode` und Ergebnis 10/11 — insbesondere `Auto` + Ergebnis 11 → `Typed`
  und `Beginner` + Ergebnis 30 → `Tiles`.
- `CountingFieldTest`: Fünferreihen für 1…30; Startzustände je Operation;
  Zähler nach n Tipps; Deckel bei `right`; Rücknahme durch zweiten Tipp;
  Endwert entspricht `MathOperation.answer` für alle drei Operationen.

Das Projekt hat **keinen `androidTest`-Source-Set** — die Testkultur sind reine
JVM-Unit-Tests auf Compose-freier Logik (`app/src/test/...`). Dieses Design hält
sich daran: alles Prüfbare lebt in `MathHinting`, `CountingField` und
`CountingState`, die Compose-Schicht bleibt eine dünne Darstellung ohne eigenen
Fachzustand. Ein Instrumentierungs-Source-Set nur für dieses Feature
aufzumachen, wäre eine Projektentscheidung, die hier nicht hingehört.

Manueller Smoke-Test (`./gradlew :app:installDebug`, Gerät auf font_scale 1.3):

1. Lektion mit Ergebnis > 10 im Default-Modus `Auto` → Zahlenfeld, keine Kacheln.
2. Zwei falsche Eingaben → Zähl-Hilfe klappt auf, Tastatur klappt ein,
   „Auflösen" ist noch **nicht** da; nach zwei weiteren erst.
3. Minus: alle Objekte angehakt, Weg-Zone zeigt `right` leere Plätze, ein Tipp
   über den Deckel hinaus ändert den Zähler nicht.
4. Malnehmen: Geisterreihen sind echt und antippbar.
5. Eltern-Sheet auf „Mit Hilfe" → dieselbe Aufgabe zeigt wieder drei Kacheln.
6. Größter Fall (Ergebnis 30) läuft nicht aus dem Aufgabenblock.

## Doku-Folgeänderungen (verbindlich, siehe AGENTS.md Schritt 7)

- `PRODUCT_PRINCIPLES.md` §8: Regel „Ergebnis > 10 → Zahlen-Eingabe, außer
  Eltern-Modus Beginner", die Zähl-Hilfe als dokumentierte Ausnahme zur
  Symbol-ab-11-Regel, die neue Eskalationsleiter.
- `AGENTS.md`, Kurzfassung „Rechnen": „3 Optionen (visuell) oder
  System-Zahlentastatur" an die neue Regel anpassen.

## Bewusst nicht drin

- **Kachel-Modus bleibt unverändert.** Ergebnisse ≤ 10 und der Eltern-Modus
  „Mit Hilfe" behalten das heutige Verhalten inklusive „Auflösen" nach zwei
  Fehlversuchen. Dort ist Raten weiterhin billig — ein eigener Schnitt.
- **Die anderen Trainer.** Auch dort wird geraten, aber „Antwortraum
  vergrößern" ist dort kein verfügbarer Hebel (eine Silbe schreibt man nicht
  auf einer Tastatur). Das braucht ein eigenes Design.
- **Automatischer Einzähler beim Aufklappen** (Zähler läuft bei Minus von selbst
  1…15 hoch, bevor das Kind handelt). Erwogen und verworfen: eine Animation, die
  das Kind aussitzen muss, bevor es endlich handeln darf — die Ausgangsmenge
  steht ohnehin als Ziffer da.
- **Zählen pro Tipp vorsprechen.** Didaktisch reizvoll, technisch unzuverlässig
  (siehe Audio oben). Falls später gewünscht, bräuchte es einen eigenen
  Kurz-Clip-Kanal ohne `stopOutput`.
