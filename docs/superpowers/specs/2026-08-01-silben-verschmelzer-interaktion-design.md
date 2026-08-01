# Silben-Verschmelzer — Interaktions-Redesign „Magnet-Buchstaben"

Status: `design-approved` (autonome Session, Entscheidung dokumentiert statt erfragt)
Datum: 2026-08-01

Die Anweisung des Trainers lautet **„Schiebe m und a zusammen. Welche Silbe entsteht?"** —
aber das, was das Kind sieht, ist ein abstrakter `→|`-Button im Antwortbereich. Der
vorhandene Drag auf der linken Kachel ist unsichtbar: nichts signalisiert, dass die Kachel
schiebbar ist oder wohin sie soll. Ein Kind, das nicht lesen kann, findet den Drag nur
durch Zufall; der Button löst die Aufgabe mit einem Tipp und ohne die versprochene
Schiebe-Erfahrung.

## 1. Problem im Detail

- **Keine Affordanz:** Die zwei Kacheln sehen aus wie statische Anzeigen. Kein Pfeil,
  keine Spur, keine Bewegung lädt zum Schieben ein.
- **Nur die linke Kachel ist ziehbar.** Kinder greifen aber auch das `a` — das tut nichts.
- **Der `→|`-Button** ist Anweisungs-Chrome, das Lesen bzw. Symbolverständnis voraussetzt
  (Verstoß gegen Prinzipien §2) und die eigentliche Interaktion kannibalisiert.
- **Kein Erlebnis unterwegs:** Während des Ziehens passiert außer einem Border-Glow nichts;
  der Moment des Verschmelzens ist stumm bis das Framework die Silbe spricht.

## 2. Betrachtete Ansätze

| Ansatz | Idee | Bewertung |
| --- | --- | --- |
| **A — Magnet-Buchstaben** (gewählt) | Beide Kacheln schiebbar, bewegen sich symmetrisch aufeinander zu; sichtbare Schiebespur; Magnet-Schnappen kurz vor der Mitte; Tipp = vorlesen + anstupsen | Macht „zusammenschieben" wörtlich erlebbar, minimaler neuer Code, kein neues Art-Asset |
| B — Schiebe-Schiene mit Griff | Ein Slider-Track unter den Kacheln, Kind zieht einen Griff | Mechanisch statt magisch; der Griff ist wieder ein abstraktes UI-Element, das nicht „zwei Laute treffen sich" erzählt |
| C — Zug-Kupplung | Waggons koppeln (Anschluss ans Lok-Motiv von Trainer 1) | Thematisch hübsch, aber deutlicher Grafikaufwand und verwässert das Lok-Motiv des Auditiven Finders; YAGNI |

## 3. Konzept „Magnet-Buchstaben"

### Interaktion

- **Beide Kacheln sind schiebbar.** Ein gemeinsamer Fortschritt `fraction ∈ [0, 1]`
  bewegt beide symmetrisch zur Mitte: schiebt das Kind das `m` nach rechts, kommt ihm
  das `a` spiegelbildlich entgegen — die Buchstaben „wollen" zueinander (Magnet-Metapher).
  Zieht das Kind zurück, weicht auch die andere Kachel zurück (bis `fraction = 0`).
- **Magnet-Schnappen:** Lässt das Kind bei ≥ 60 % los, ziehen sich die Kacheln federnd
  ganz zusammen und verschmelzen. Darunter gleiten beide federnd zurück — keine Strafe
  (Prinzipien §2, Snap-back). Erreicht der Drag 100 %, verschmilzt es sofort beim Kontakt.
- **Tipp = vorlesen + anstupsen:** Ein Tipp auf eine Kachel spricht ihren Laut (Prinzip §7:
  antippbare Items werden vorgelesen — `m` gedehnt via `stretchTts`, `a` als Vokal) **und**
  stupst sie einen Schritt (30 %) zur Mitte. Zwei Tipps erreichen die Magnetzone — der
  motorisch einfache Weg für Kinder, die den Drag (noch) nicht können. **Der `→|`-Button
  entfällt ersatzlos**; der Antwortbereich bleibt leer (wie beim Wort-Detektiv erlaubt,
  die Aufgabe trägt sich selbst).

### Sichtbare Einladung zum Schieben

- **Schiebespur:** Zwischen den Kacheln liegt eine gepunktete Spur (Canvas). Auf ihr
  wandern von beiden Seiten kleine Punkte zur Mitte (Endlos-Animation) — die Richtung
  ist ohne ein Wort Text klar. Die Spur verblasst proportional zum Fortschritt und
  verschwindet mit dem Verschmelzen.
- **Idle-Anstupser:** Nach ein paar Sekunden ohne Interaktion „atmen" die Kacheln kurz
  aufeinander zu und zurück (wenige dp). Wiederholt sich, bis das Kind eingreift.
- **Glow bleibt:** Der Rahmen-Glow intensiviert sich weiter mit der Nähe
  (bestehende `MergeProgress.glow`-Logik).

### Verschmelzen & Erfolg

- Beim Kontakt erscheint die Ergebnis-Kachel (`ma`, mint, „gefroren") mit federndem
  Scale-in (Overshoot) an der Stelle, wo sich die Kacheln getroffen haben — Mitte.
- Danach übernimmt der bestehende Erfolgsfluss des Frameworks: Silbe wird vorgesprochen,
  Stern-Burst, weiter (`onResult(correct = true)` unverändert, keine Doppel-Audio im Trainer).
- Die Ergebnis-Kachel bleibt tappbar und spricht die Silbe (unverändert).

### Audio

| Moment | Ton |
| --- | --- |
| Drag-Start auf `m` | `stretchTts` (gedehnter Konsonant, „mmm") — wie bisher |
| Drag-Start auf `a` | Vokal (`rightDisplay`) |
| Tipp auf Kachel | wie Drag-Start derselben Kachel (plus Anstupser) |
| Verschmelzen | nichts Neues im Trainer — Framework spricht `resultDisplay` |

System-TTS kann kein Phonem kontinuierlich dehnen; die Intensivierung bleibt visuell
(Glow + Nähe), der gedehnte Laut spielt einmal am Gestenstart. Das ist die bestehende,
bewusste Einschränkung.

## 4. Technik

- **`MergeProgress` bleibt reine, testbare Logik** und wächst um:
  - `applyDrag(fraction, deltaPx, travelPx, fromRightTile)` — beidseitige Delta-Akkumulation
    mit Clamping (rechte Kachel invertiert das Vorzeichen).
  - `TapStep = 0.3f` und `stepped(fraction)` für den Tipp-Anstupser.
  - `AttractFraction = 0.6f` und `shouldAttract(fraction)` — Magnetzone beim Loslassen.
  - `CommitFraction` behält die Bedeutung „Kontakt beim Drag" (≈ 0.98 statt 0.88 — der
    Commit während des Ziehens ist jetzt echter Kontakt, das Loslassen regelt der Magnet).
  - `glow` unverändert.
- **UI (`SyllableMergeTrainer`):** `fraction` wird ein `Animatable` — Drag nutzt `snapTo`,
  Magnet/Zurückgleiten/Anstupser nutzen `animateTo` mit Spring. Offsets:
  Konsonant `+fraction · travel/2`, Vokal `−fraction · travel/2`.
  Schiebespur und Idle-Anstupser über `rememberInfiniteTransition` bzw. `LaunchedEffect`
  mit Delay; beide pausieren während Drag/nach Merge.
- **Keine Content-, Schema- oder Validator-Änderung.** `promptTts` („Schiebe … zusammen")
  passt jetzt erst recht.
- **Tests:** `MergeProgressTest` wächst um beidseitiges `applyDrag` (Vorzeichen, Clamping),
  `stepped`-Schrittkette (zwei Tipps erreichen die Magnetzone), `shouldAttract`-Schwelle.
  Compose-Verhalten (Animationen) bleibt wie im Projekt üblich untestbar-schlank.

## 5. Prinzipien-Check

| Frage | Antwort |
| --- | --- |
| Muss das Kind Text lesen, um zu handeln? | Nein — Spur, Bewegung und Audio tragen die Interaktion; der `→|`-Button verschwindet |
| Drag committet nur bei echtem Treffer, Snap-back ohne Strafe? | Ja — Magnetzone beim Loslassen, sonst federndes Zurückgleiten |
| Antippbare Items werden vorgelesen? | Ja — Tipp spricht den Laut und stupst zusätzlich an |
| Ruhiges, weiches UI? | Ja — Spur/Atmen sind dezent, kein Blinken, dark-only bleibt |
| Kann die Aufgabe fehlschlagen? | Nein — wie bisher beim Verschmelzer akzeptiert (Erst-Begegnung, distraktorfrei) |

## 6. Doku-Folgeänderung

`docs/PRODUCT_PRINCIPLES.md` §3, Punkt 3 beschreibt den Trainer neu: beide Kacheln
schiebbar, Magnet-Schnappen, Tipp stupst an — statt „Konsonant auf Vokal ziehen".
