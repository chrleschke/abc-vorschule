# Plan 003 — Spurenzeichner-Belohnung und Rechen-Bestätigung

Status: `implementation-ready`
Datum: 2026-07-26

## Problem

Vier konkrete Schwächen im ausgelieferten Stand:

1. Der Trainer-2-Prompt sagt „Spure … nach" — ein Wort, das Eltern beim Vorlesen stolpern lässt
   und nicht der Alltagssprache entspricht.
2. Der Spurenzeichner widerspricht sich selbst: der Prompt verspricht **Sterne**, gezeichnet werden
   **Punkte**. Dazu zeigt er den Buchstaben nochmal als Text unter dem Pfad (doppelte
   Aufgabe+Vorschau, Prinzip 9), das rote Fahrzeug bleibt nach einem fertigen Balken liegen statt
   zum nächsten Startpunkt zu springen, fertige Balken bekommen kein Abschluss-Feedback, und die
   Belohnung ist nur ein Emoji ohne die Buchstabe-Wort-Verknüpfung, die didaktisch der Punkt ist.
3. Der Rechen-Trainer bestätigt eine korrekte Antwort nicht visuell — das Kind sieht nicht, *was*
   es angetippt hat, während die Antwort vorgesprochen wird.
4. Das gesprochene Erfolgs-Feedback ist über eine Lektion hinweg identisch und nutzt sich ab.

## Entscheidungen (Session 2026-07-26)

| Frage | Entscheidung |
|-------|--------------|
| Erfolgstexte gesprochen oder angezeigt? | **Nur gesprochen** — Prinzip 2, das Kind kann nicht lesen |
| Lob in allen Trainern oder nur Rechnen? | **Nur Rechnen** |
| Sterne kommender Balken | **Blass sichtbar**, nicht versteckt — das Ziel bleibt erkennbar |
| Grüne Bestätigung | **Antwortkacheln und Zahleneingabe** |
| Anzahl Lobwörter | **20** |
| Anführungszeichen in der Wortzeile | **Nein** — `T wie Tomate`, Satzzeichen sind Rauschen für Leseanfänger |

## 1. Wording (Content)

`"Spure das große X nach und sammle alle Sterne."` → `"Zeichne das große X nach und sammle alle Sterne."`

28 `promptTts`-Vorkommen, gespiegelt in `app/src/main/assets/content/tasks.json` und
`app/src/test/resources/content/tasks.json`. Kein Schema-Wechsel.

## 2. Spurenzeichner (`ui/exercise/LetterTraceTrainer.kt`)

**a) Glyph-Text unter der Canvas entfällt.** Der blasse `Text(round.glyph)` wird gelöscht.

**b) Gelbe Vektor-Sterne statt Kreise.** Neue reine Geometrie `TraceGeometry.starPoints(center,
outerRadius, innerRadius, spikes)` → 2·spikes Vertices, erster Zacken oben (−90°). Neue
Themenfarbe `SoftGold = 0xFFF2C14E`.

- Aktiver Balken: volles `SoftGold`; der nächste einzusammelnde Stern etwas größer.
- Kommende Balken: `SoftGold` mit alpha ≈ 0.28.
- Eingesammelte Sterne: nicht gezeichnet — die Balkenfüllung trägt den Fortschritt.

**c) Haptik pro Stern.** `HapticFeedbackType.TextHandleMove` (kurzer Tick) beim Einsammeln,
bewusst anders als das `LongPress`-Rumpeln beim Korridor-Verlassen.

**d) Fahrzeug springt zum nächsten Balken.** Wechselt `update.state.strokeIndex`, wird `vehicle`
auf `strokes[neuerIndex].first()` gesetzt statt auf die Fingerposition.

**e) Fertige Balken füllen sich ease-in.** Ein einziges `animateFloatAsState` auf
`state.strokeIndex.toFloat()` mit `tween(360, easing = EaseIn)`; pro Balken `i` ist die Füllung
`(animiert − i).coerceIn(0f, 1f)`. Damit bleibt die Zahl der Animations-Aufrufe unabhängig von der
Strichzahl des Glyphen. Gefüllt wird die dunkle Fahrbahn nach `SoftMint`.

**f) 500 ms Standbild.** Der letzte Stern setzt nur `done = true`. Ein `LaunchedEffect(done)`
wartet `TraceRewardHoldMs = 500`, **dann** erst `reward = true` und `onResult(true, false, …)` —
der Delay muss vor `onResult` liegen, weil `onResult` die `SpeakAnswer`-Phase startet.

**g) Belohnungsseite.** Statt Canvas: Emoji (108.sp) und darunter die Wortzeile `T wie Tomate`,
Graphem fett (`AnnotatedString` + `FontWeight.Bold`). Das Wort liefert die reine Funktion
`TraceReward.wordOf(rewardTts)` („T wie Tomate." → „Tomate"), Fallback ist der rohe `rewardTts`.
Kein Content-Schema-Wechsel.

## 3. Rechen-Trainer

**Kacheln** (`VisualQuantityBoard`): neuer Parameter `solved: Int?`. Die getroffene Kachel wird
`SoftMint` (Hintergrund), ihre Zahl `NightInk` für Kontrast. Falsche Wahl bleibt unverändert:
gesprochenes Feedback, kein Rot.

**Zahleneingabe** (`NumberPad`): neuer Parameter `solved: Boolean`. Rand und Zifferntext werden
`SoftMint`, das Feld wird `readOnly`, und die System-Tastatur wird geschlossen — sonst verdeckt
die IME genau die Bestätigung, die sie zeigen soll. Ruhezustand-Rand wird `SoftSky`, damit Grün
überhaupt unterscheidbar ist.

`MathExercise` braucht dafür einen eigenen `solved`-State: `locked` wird auch beim Auflösen true,
und ein Resolve darf nicht grün leuchten.

**Lob** (`ui/rewards/PraisePhrases.kt`): 20 Wendungen, `pick(random: Random = Random.Default)` für
deterministische Tests. `SessionViewModel.successSpeakTextForCurrent(praise: Boolean)` setzt für
`CountAddRound` bei `praise = true` das Lob voran: `"Super! zwei Ameisen"`. Die Menge bleibt das
Letzte, was das Kind hört. Der Resolve-Pfad ruft mit `praise = false` auf.

## 4. Tests

- `TraceGeometryTest`: `starPoints` — Vertexzahl, abwechselnde Radien, erster Zacken oben.
- `TraceRewardTest`: Parse inkl. Fallback bei abweichendem Autorenmuster.
- `PraisePhrasesTest`: 20 Einträge, keine Duplikate, kein Satzzeichen am Ende, `pick` im Bereich.
- Bestehende `TraceProgressTest` / `TraceGeometryTest` bleiben grün — die Fortschrittslogik ändert
  sich nicht.

## 5. Doku

- `docs/PRODUCT_PRINCIPLES.md`: Trainer-2-Belohnungsseite, gelbe Sterne, Balkenfüllung; Rechnen
  mit grüner Bestätigung und gesprochenem Lob.
- `AGENTS.md`: Kurzregeln für Trainer 2 und Rechnen nachziehen.
