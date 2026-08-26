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
Beide Gruppen bleiben stehen und werden antippbar. Der Zähler startet bei 0 und
läuft über beide Gruppen durch: 1…7 links, weiter 8…15 rechts.

**Minus („15 − 6")** — wegnehmen.
Das Bild wechselt bewusst gegenüber dem Prompt: **ein** Feld mit 15 Objekten,
alle angehakt, Zähler startet bei 15. Daneben eine **Weg-Zone mit genau `right`
leeren Plätzen** (hier 6), unter ihr die Ziffer 6.

- Tipp auf ein Objekt → es wandert in die Weg-Zone, Haken weg, Zähler 15 → 14 → …
- Sind alle 6 Plätze belegt, nimmt ein weiterer Tipp nichts mehr weg
  (kurzes Haptik-Nein, kein Fehler, keine Meldung).
- Nach 6 Tipps steht der Zähler auf 9, die 9 steht im Feld.

Die leeren Plätze sind der Kern dieser Variante: das Kind muss *nicht*
mitzählen, wie viele es schon weggenommen hat — die Struktur trägt die Zahl.
Ohne sie müsste das Kind zwei Zahlen gleichzeitig verfolgen („wie viele weg"
und „wie viele übrig"), und das ist im Vorschulalter zu viel.

Verworfene Alternative: *Rest zählen* — die App streicht die 6 durch, das Kind
zählt die verbliebenen 9 aufwärts. Abgelehnt, weil dann die App die Subtraktion
ausführt und das Kind nur noch abzählt. Rückwärtszählen ist zudem selbst eine
Vorschul-Fähigkeit, die hier trainiert werden soll.

**Malnehmen („4 × 5")** — Raster füllen.
Dieselbe Matrix wie im Prompt, aber die Geisterreihen
(`MultiplicationMatrix.GhostAlpha`) werden **echt** und alle Zellen antippbar.
Der Zähler läuft zeilenweise 1…20. Das ist genau der Schritt, den das Kind
vorher im Kopf nicht geschafft hat.

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
ohnehin braucht. Ab einer Gruppengröße von 11 laufen die Zeilen auf **zehn**
Objekte, gezeichnet als zwei Fünfer mit breiterer Lücke, wie an einem
Rechenrahmen. Fünferzeilen überall wären konsequenter, türmen den authorierten
Content aber unbrauchbar hoch: „30 − 17" steht so im Pack und wäre zehn Zeilen,
mit einem Emoji, das auf 14sp schrumpfen müsste. Die Zeilenbreite gilt
rundenweit, nicht je Gruppe, sonst stünde bei „15 + 4" eine Zehnerzeile über
einer Fünferzeile.

### Audio

- **Beim Aufklappen:** ein gesprochener Cue („Zähl mit — tippe jedes … an" bzw.
  „Nimm sechs … weg"). Kinder lesen nicht (§Audio-First).
- **Pro Tipp:** nur Haptik, **kein** Sprechen. Schnell aufeinanderfolgende
  Äußerungen würgen sich in `SpeechController` gegenseitig ab — auch auf
  `SpeechChannel.Feedback`, das vor dem Enqueue `stopOutput(channel)` ruft.
- **Beim letzten Tipp:** einmal die Gesamtzahl sprechen („Neun!"). Eine einzelne
  Äußerung, unkritisch, und der Payoff-Moment.

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
