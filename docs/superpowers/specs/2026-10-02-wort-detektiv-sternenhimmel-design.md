# Wort-Detektiv unter dem Sternenhimmel

Stand 2026-10-02 · Mockups: https://claude.ai/artifact/1XCBLa5F3cjqZx6LYeEQd9
(Reihe „Wort-Detektiv, Runde 2", Boards **E4** und **E4b**). Ergänzt
`2026-07-31-wort-detektiv-design.md`; wo sich beide widersprechen, gilt dieses Dokument.

## Problem (Nutzertest)

- Der Dachboden ist als Szene nicht erkennbar und hat keinen Bezug zur Aufgabe.
- Die Segmente des Worts tragen reihum Farben (`SegmentPalette`) ohne Bedeutung.
- Das Wort ist auseinandergezogen: jedes Graphem ist eine eigene ≥ 56 dp breite
  Tipp-Fläche mit Lücke, das Wort liest sich nicht mehr als Wort.
- Lange Wörter brechen nach Segmentanzahl um (`WordFrameSizing.segmentsPerRow`), also
  mitten in Silben.

## Entscheidungen des Nutzers

- **Tippen bleibt** die Interaktion. Verworfen: springende/ziehbare Lupe, Laut-Vorschau
  pro Graphem (kurze Clips sind eine Schwachstelle), Farb-Vorschau.
- **Szene: Sternenhimmel mit Deko-Teleskop** (passt zu „Finden"; Teleskop ohne Funktion).
  Wiederholte Motive sind Konsistenz, kein Problem.
- **Helle Schrift auf Dunkel** ist für das Wort erlaubt — als benannte Ausnahme zu §10
  „Lerninhalt nur auf Licht".
- **Gefunden** = Graphem wird golden und **1–3 Sterne rahmen es** grob ein.
- **Fortschritt** = Stern-Silhouetten unter dem Wort, eine pro Treffer; bei einem
  Treffer **fliegt ein Stern vom Buchstaben** in die nächste Silhouette und füllt sie.

## Bild

- **Welt `Stars`** ersetzt `Attic` (gezeichnet, keine Bilddatei): Nachthimmel
  (Nachtblau → Violett unten), zarte Milchstraße als diagonales Band, wenige funkelnde
  Sterne (ruhig, ≥ 4 s Perioden), zwei, drei schwache Sternbild-Linien, ein dunkler
  Horizont unten, **Teleskop auf Dreibein unten links** als Silhouette. Tap auf die Welt
  (WorldTaps): ein paar Sterne in der Nähe funkeln kurz auf. Bei „Bewegung reduzieren"
  steht alles.
- **Kein Stern berührt das Teleskop** (Nutzerentscheidung 2026-10-02): seine Ecke unten
  links ist eine sternfreie Zone (`StarsScene`, 92 × 170 dp) — keine Himmelssterne, keine
  Sternbild-Linie, kein Funkeln beim Antippen. Auch der Inhalt bleibt draußen: die
  Silhouetten-Reihe hält die Zone links und rechts frei (schrumpft notfalls), und der
  fliegende Stern holt nach rechts aus, vom Teleskop weg.
- **Ruhige Zone:** hinter dem Wort ein weicher dunkler Radialfleck, damit nichts durch die
  Buchstaben flimmert.
- **Das Wort** steht als **ein Wort**: normale Laufweite, eine Farbe — warmes Creme
  (≈ `#F6EBCF`) mit sanftem Schimmer (Glow), fett, groß. Kein `SegmentPalette` mehr.
- **Der gesuchte Laut oben** (`TargetLabelRow`, z. B. „e · E", „Sch") ebenfalls in
  Leuchtschrift statt auf der hellen Pille. Antippen spricht ihn weiterhin.

## Tippen und Layout des Worts

- Jedes Segment bleibt einzeln antippbar, aber die **Tipp-Fläche wächst in die Höhe,
  nicht in die Breite**: Breite = Glyphenbreite des Segments (benachbarte Flächen dürfen
  sich berühren), Höhe ≥ 56 dp. Ein Segment ist mindestens so breit, dass ein schmales
  „i"/„l" noch sicher triffbar ist (Mindestbreite festlegen, Richtwert ≥ 32 dp — schmale
  Segmente bekommen unsichtbaren Zusatzrand, ohne die sichtbare Laufweite zu ändern).
- **Kein Umbruch nach Segmentanzahl.** Die Schriftgröße wird so gewählt, dass das Wort in
  eine Zeile passt (Unter-/Obergrenze festlegen). Passt es auch bei Mindestgröße nicht,
  wird **an einer Silbengrenze** in zwei Zeilen getrennt, mit Trennstrich am Zeilenende.
  Silbengrenzen kommen aus dem Content (Silben-Atome/Wort-Bauer-Blöcke) — prüfen, was das
  Pack hergibt; ohne verlässliche Silbengrenze lieber kleiner setzen als falsch trennen.
- Reine, Compose-freie Layout-Funktion (Schriftgröße, Zeilen, Trennstelle) mit
  Unit-Tests, im Stil der vorhandenen `*Sizing.kt`.

## Ablauf

- **Treffer:** Segment wird golden (`#FFD27A`-Familie, Glow), **2 Sterne** (ein Buchstabe)
  bzw. **3 Sterne** (Mehrzeichen-Graphem wie „Sch", „ei", oder Silbe) erscheinen um das
  Segment wie ein kleines Sternbild, verbunden durch eine zarte gestrichelte Linie,
  leicht versetzt funkelnd. Gleichzeitig **fliegt ein Stern** vom Segment in einem Bogen
  in die nächste leere Silhouette und füllt sie (Landung mit kleinem Aufleuchten).
  Bestehende Treffer-Sprache/-Klänge bleiben.
- **Fehltipp:** wie bisher (kurzes Drehen/Wackeln des Segments, bestehende Rückmeldung).
  Keine Sterne.
- **Fortschritt:** unter dem Wort eine Reihe **Stern-Silhouetten** (gestrichelter Umriss,
  fast transparent), eine pro gesuchtem Vorkommen. Ersetzt die heutigen Strich-Slots
  (`SlotRow`) samt fliegendem Buchstaben. Sie stehen im Aufgabenblock direkt unter dem
  Wort (wie im Mockup), nicht im Antwortblock am unteren Rand — dort lagen sie auf dem
  Horizont, und der Stern hätte einen halben Bildschirm zu fliegen. Der Antwortblock
  bleibt leer.
- **Alle gefunden:** bestehende Erfolgs-Zeremonie.
- **„Zeig mir" entfällt** (Nutzerentscheidung 2026-10-02, wie zuvor beim Satz-Versteher):
  das Wort ist die ganze Auswahl, jeder Fehltipp spricht sein Segment vor, und das Kind
  tippt weiter, bis es alle Sterne gefunden hat. Damit gibt es weder Auflösen noch einen
  gedämpften „aufgelöst"-Zustand; `SymbolInWordProgress` zählt keine
  aufeinanderfolgenden Fehltipps mehr.
- **„Bewegung reduzieren":** Stern-Flug → Silhouette füllt sich direkt; kein Funkeln.

## Kontrast

Creme auf dem dunklen Fleck ≈ 17:1, Gold ≈ 13:1 — über der 7:1-Grenze. Gegen
Überstrahlung: warmes statt reinweißes Licht, fette Schrift, ruhiger Fleck, Glow nur
dezent. Auf dem Moto bei gedimmter Helligkeit prüfen.

## Doku

`PRODUCT_PRINCIPLES.md` §10: Dachboden → Sternenhimmel; **benannte Ausnahme** „Wort-Detektiv:
großes Wort in warmem Creme auf ruhigem Dunkel" (kleine Schrift und Kacheln bleiben Tinte
auf Licht); Farbe nur mit Bedeutung (gold = gefunden). §3/§9 wo der Wort-Detektiv
beschrieben ist (Slots → Stern-Silhouetten, Layout ohne Segment-Umbruch). `AGENTS.md`
Kurzfassung, falls betroffen.

## Tests

- Unit-Tests für die Layout-Funktion (eine Zeile wenn möglich, Trennung nur an
  Silbengrenze, Mindest-Tippbreite, „Sch" als ein Segment).
- Bestehende Wort-Detektiv-Tests (Progress, Derivation, Speech) bleiben grün; Testtags
  der Segmente (`detective_segment_$index`) bleiben.
- Instrumentierter Screenshot-Test für die Szene (ohne Lektionen durchzuspielen).

## Umsetzung (2026-10-02)

- **Layout:** `ui/exercise/WordDetectiveLayout.kt` (Compose-frei, `WordDetectiveLayoutTest`).
  Wortgröße 72 dp, Trennschwelle 40 dp, Untergrenze 28 dp — gerendert in dp, also
  unabhängig von der Systemschriftgröße. Eingabe sind die gemessenen Vorschübe je Segment
  (`TextMeasurer`), nicht geschätzte Zeichenzahlen. Gesuchtes Symbol fest 38 dp.
- **Tipp-Fläche:** Höhe ≥ 56 dp, **Mindestbreite 36 dp** (Richtwert war ≥ 32 dp; 36 dp ist
  die mittlere Segmentbreite des längsten Pack-Worts „Taschenlampe" auf dem Moto, nur
  „i"/„l"/„t" müssen leihen). Die Zusatzbreite schmaler Segmente kommt von den Nachbarn
  und vom Bühnenrand (isotone Regression, kleinste Verschiebung der Grenzen); jede Fläche
  enthält immer die Mitte ihrer eigenen Glyphe — das geht vor der Mindestbreite. Passen
  die Flächen nicht in eine Zeile, zählt das wie ein zu breites Wort (Trennung, sonst
  schmalere Flächen).
- **Trennstellen** (`SymbolInWordRound.breakBefore`, `SymbolInWordDerivation.breakBefore`):
  das Pack hat keine Silbentrennung, und die Wort-Bauer-Blöcke sind keine verlässlichen
  Silben (`Ba·ll`, `Ma·nn`, `Son·ne` unter `letter-s`). Verlässlich sind nur die Fuge
  zwischen zwei Wort-Blöcken (`kind` word/other: `Sonnen-blume`, `Taschen-lampe`,
  `Schul-tasche`, `Schnee-mann`) und die zwischen zwei Silben-Blöcken, die so heißen wie
  ihr Atom (`Ma·ma`). Alles andere trennt nie. Auf dem Moto (370 dp Bühne) passt jedes
  Pack-Wort in eine Zeile; auf 360 dp trennen „Taschenlampe", „Sonnenblume",
  „Apfelkuchen", „Regenjacke", „Mülltonne" an ihrer Fuge.
- **Höhe:** Symbol, Wort und Silhouetten sind eine Gruppe (`verticalFit`); auf kurzen
  Geräten gibt erst die Luft nach, dann die Silhouetten (bis 36 dp), zuletzt die
  Wortgröße — die 56 dp Tipp-Höhe nie.
- **Rahmen-Sterne** (`frameStars`): Lagen aus den Mockups in em relativ zu Glyphkante und
  Grundlinie — 1 Buchstabe: oben links + unten rechts; Mehrzeichen-Graphem/Silbe: oben
  links, oben rechts, unten mittig, gestrichelt verbunden. Auf der oberen von zwei Zeilen
  gespiegelt, damit kein Stern in die zweite Zeile fällt. Gezeichnet mit `drawGlint`,
  Silhouette und fliegender Stern mit `roundedStarPath`/`drawGlowStar`, Bogen mit
  `StarFlight.offset(swaySign = -1)`.
- **Hilfestufe „Beginner"** (Prinzip 6) bleibt: das gesuchte Segment liegt blass in der
  leeren Silhouette, wie vorher im Strich.
- **Beibehalten:** der Schrägstrich im Formenpaar (`P / p`); das Mockup „Tomate" zeigt
  `t · T` — das wäre eine Änderung von `SymbolInWordDerivation.targetLabel` und ist offen.
- **Standbilder:** `WordDetectiveShotTest` (kurzes/langes Wort, „Sch", Flug, alles
  gefunden, 360 dp Breite, 360 × 640 dp).

