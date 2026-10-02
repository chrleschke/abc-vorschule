# Bilderbuch (Satz-Versteher) und Nebelring (Startbildschirm)

Stand 2026-10-02 · Mockups: https://claude.ai/artifact/1XCBLa5F3cjqZx6LYeEQd9
(Reihe „Satz-Versteher: Bilderbuch statt Bühne“, Boards 1–3; Reihe „Startbildschirm v2“, Board R1c)

Zwei unabhängige Änderungen aus einem Nutzertest:

1. **Satz-Versteher:** Ein Tester dachte, er müsse die Karten auf die Bühne des
   Puppentheaters legen. Die Bühne verspricht eine Ablagefläche, die es nicht gibt.
2. **Startbildschirm:** Die ABC-Klötze lesen sich als Spielzeug zum Antippen, nicht als
   Einstieg. Die Stecknadel („du bist hier“) sagt einem Vorschulkind nichts.

Verworfen (nicht wieder vorschlagen): Play-Knopf über dem Schild, zeigende Hand,
fester Weiter-Knopf unten, glänzender/plastischer Planetenring, punktiger Staubring,
Fortschrittsring mit Segmenten, Ringe unter allen Schildern.

---

## Teil A — Satz-Versteher als Bilderbuch

### Bild

- **Welt „Leseecke“** ersetzt „Puppentheater“ (`TrainerWorld`, gezeichnet, keine
  Bilddatei): dunkler Raum (Nachtblau → warmes Dunkelviolett), warmer Lampenschein oben
  rechts, unten ein Holztisch ab der Höhe der Kartenreihe. Ruhige Umgebungsbewegung
  höchstens: der Lampenschein atmet sehr langsam (≥ 7 s). Vorhang, Lambrequin,
  Lichterkette und Bühnenbretter entfallen.
- **Das Buch** füllt den Aufgabenblock über den Karten. Aufgeschlagen, von der linken
  Seite ist nur ein schmaler Streifen am linken Rand sichtbar (Rest außerhalb des Bilds),
  Falz mit weichem Schatten, Einband als dunkelroter Rand, rechts zwei, drei Linien
  Seitendicke. Die rechte Seite ist die Licht-Insel (§10 „Lerninhalt nur auf Licht“).
- **Auf der rechten Seite**, oben: ein **leerer Bildrahmen** (gestrichelt, warmes
  Papierbeige, abgerundet), darin ein blasser Stern. Darunter **Wort-Balken ohne
  Buchstaben**: ein abgerundeter Balken pro Wort des Satzes, Länge nach Wortlänge
  (geklemmt), umbrechend in Zeilen, die auf die Seite passen.
- **Ohne deutsches TTS** steht statt der Balken der echte Satz als Text auf der Seite
  (WarmInk). Das ersetzt den bisherigen Fallback-Text (§7, darf nicht wegfallen).
- **Die zwei Bildkarten** bleiben wie sie sind (helle Fläche, Holzrahmen,
  `LeafGreen` bei richtig) und liegen auf dem Tisch statt auf Bühnenbrettern.

### Ablauf

- **Vorlesen:** Solange der Satz gesprochen wird, leuchten die Wort-Balken nacheinander
  in `SunCoral` auf. Ist die Clip-Dauer im Audio-Layer verfügbar, wird sie nach
  Zeichenzahl auf die Wörter verteilt; sonst glimmt die ganze Balkengruppe ruhig, solange
  gesprochen wird — keine erfundene Wort-Synchronisation.
- **Antwort = Tippen** (unverändert). Kein Ziehen.
- **Richtig:** Die getippte Karte fliegt in einem Bogen in den Bildrahmen und schrumpft
  dabei auf Rahmengröße; der Rahmen wird durchgezogen `LeafGreen`, das Bild steht nun im
  Buch. Die falsche Karte blendet aus. Danach das bestehende Erfolgs-Vorsprechen.
- **Falsch:** Karte wackelt (bestehende Schüttel-Animation), der Satz wird erneut
  vorgelesen (bestehendes Verhalten), die Balken leuchten erneut.
- **„Zeig mir“** nach 2 Misses (unverändert): die richtige Karte fliegt wie oben ein.
- **Nächste Runde:** Die Seite blättert um (≈ 600 ms, Seite klappt um den Falz nach
  links), darunter liegt eine neue Seite mit leerem Rahmen; dann erscheinen die neuen
  Karten. Runde 1 beginnt mit aufgeschlagenem Buch, ohne Blättern.
- **„Bewegung reduzieren“:** Flug → Überblenden in den Rahmen; Umblättern → Überblenden.

### Unverändert

Instruktion einmal vor Runde 1, `AnswerAnchor.BelowCenter`, Seitenwahl
`SentencePictureSides`, Scoring, Testtags der Karten (`sentence_picture_card_correct`/
`_wrong`, `sentence_picture_cards`, `sentence_picture_fallback_text`).

### Tests

- Reine Funktion für das Balken-Layout (Wörter → Balkenlängen → Zeilen bei gegebener
  Seitenbreite), mit Unit-Tests: Satzzeichen zählen nicht, lange Wörter werden geklemmt,
  kein Balken ragt über die Seite.
- Bestehende Satz-Versteher-Tests bleiben grün.

---

## Teil B — Nebelring statt Brett und Stecknadel

### Bild

- **Brett und Pfosten entfallen bei allen Schildern.** Die Klotztürme stehen frei am Weg.
  Zustand lesen Kinder weiter an Stern-Badge (fertig), Schloss und dunklen Klötzen
  (gesperrt). Was die bisherige Brettkontur farblich kodierte, darf nicht ersatzlos
  verloren gehen — falls sie Information trägt, die sonst nirgends steht, wandert sie in
  die Badges.
- **Die Stecknadel (`PathHereMarker`) entfällt.** Ihre Rolle übernimmt der Ring.
- **Nur das aktuelle Schild** (das bisherige Ziel der Stecknadel) bekommt den
  **Nebelring** als Sockel: eine flache Ellipse (Breite : Höhe ≈ 6,5 : 1), etwa
  Turmbreite + 2 × 20 dp breit, mittig unter der Unterkante der Klötze. Die hintere Hälfte
  liegt hinter den Klötzen, die vordere davor/darunter — er wirkt wie Boden, nicht wie
  ein Rahmen um die Klötze.

### Aufbau des Rings (Canvas, keine Assets)

1. **Dunst:** flacher radialer Verlauf, warm (`#FFE2A8`, α ≈ 0,2 innen → 0 außen),
   etwas größer als der Ring.
2. **Nebelband:** 8–12 weiche Flecken (Kreise mit radialem Verlauf, `#FFE7B8`/`#FFD27A`,
   α 0,15–0,35) auf der Ellipse, zwei Gruppen treiben gegenläufig (Umlauf ≈ 30 s / 44 s).
   Kein `RenderEffect`/Blur — Weichheit kommt aus den Verläufen.
3. **Partikel:** 40–80 kleine Körner (1–2 dp, hell, α 0,5–0,9) auf drei leicht
   verschiedenen Ellipsen, Umlauf 24 s / 36 s gegenläufig, in **einem** `drawPoints`
   gebündelt.
4. **Puls:** alle ≈ 3,2 s eine Nebelwelle, die sich von 1 → 1,5 aufweitet und dabei
   von α 0,5 → 0 verblasst; das Band atmet in derselben Periode leicht (α 0,7 ↔ 1).

Eine gemeinsame Uhr (`rememberWorldSeconds`), Invalidierung nur in der Draw-Phase.
**„Bewegung reduzieren“:** stehender Dunst und stehende Körner, kein Puls.

### Verhalten

- **Wechsel des aktuellen Schilds** (nach einer gelösten Lektion, bisher der Hüpfer der
  Stecknadel): der Ring verblasst am alten Schild und wächst am neuen auf. Die
  bestehende Marker-Index-Animation darf als Taktgeber dienen.
- Gesperrte und noch nicht erreichte Schilder bekommen nie einen Ring.
- Tippen, Scroll-Fokus und Drehwürfel bleiben unverändert.

### Performance

Vorher/Nachher am Moto (`adb shell dumpsys gfxinfo app.silbo.abcvorschule reset`, dann
10 s Pfad mit Scrollen, dann `dumpsys gfxinfo`): Anteil ruckelnder Frames darf nicht
spürbar steigen.

### Tests

- Bestehende Pfad-Tests (`PathGeometryTest`, `PathSignLayoutTest`, `PathMarkerGeometryTest`,
  …) anpassen, wo sie Brett, Pfosten oder Stecknadel voraussetzen; Geometrie des Rings
  (Größe/Lage relativ zum Turm) als reine Funktion mit Unit-Test.
- `PathGeometry.DefaultMargin`/`PathMarkerDimens.Headroom` prüfen: ohne Stecknadel
  braucht das erste Schild weniger Kopfraum.

---

## Doku

`PRODUCT_PRINCIPLES.md` §9/§10 (Puppentheater → Leseecke, Schild ohne Brett, Nebelring
statt Stecknadel), `AGENTS.md`-Kurzfassung, falls betroffen.

## Bekannte Kollision

Ungecommittet im Hauptverzeichnis (`main`) liegen fremde Änderungen einer anderen Session an
`PathSignNode.kt`, `PathScreen.kt`, `PathFocus.kt` (Drehwürfel folgt Scroll-Fokus). Teil B
berührt dieselben Dateien; beim späteren Zusammenführen sind Konflikte zu erwarten.
