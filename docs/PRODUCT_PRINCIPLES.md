# ABC-Vorschul App — Produktprinzipien

Dieses Dokument ist die verbindliche Quelle für Produkt- und UX-Grundsätze.
Bei Konflikten mit Implementierungsdetails oder älteren Planabschnitten gelten diese Prinzipien
(und aktuelle Nutzerentscheidungen in der Session), sofern sie nicht ausdrücklich revidiert wurden.

## 1. Für wen die App da ist

- Primäre Nutzer: Kinder im Vorschulalter (ca. 4–7 Jahre).
- Eltern steuern nur selten (Hilfestufe hinter Kindersicherung), nicht den Lerninhalt im Alltag.
- Die App ist kostenlos, werbefrei und ohne Monetarisierung in der Produktidentität.
- Offline nach Installation: Kernpraxis braucht kein Netz.

## 2. Kind-zentrierte Oberfläche

- **Das Kind kann (noch) nicht zuverlässig lesen.** UI-Steuerung muss über Bild, Icon, Layout und Audio verständlich sein.
- Lesbare Labels sind erlaubt, wo sie Erwachsenen helfen oder wo Buchstaben/Wörter *die Lernaufgabe selbst* sind — nicht als Anweisungschrome (Pack-Titel, lange Erklärtexte).
- Handlungs-Buttons (z. B. **Weiter**): Text optional, immer klares **Vektor-/ASCII-Icon** (keine Emojis in Buttons). **Weiter** rechts ausrichten.
- Dunkles, ruhiges UI; weiches Feedback statt Strafe oder Drucksprache.
- Distraktoren nur aus **echten, bereits geübten Atomen** (max. 2 pro Aufgabe, Tray ≤ 5 Kacheln) — nie erfundene „Fake-Antworten“. Falsche Kachel oder falsche Platzierung ist einfach falsch (gesprochenes Feedback). Die erste Begegnung mit neuem Stoff bleibt distraktorfrei.
- Drag & Drop committet nur bei echtem Slot-Treffer (Hit-Testing); daneben losgelassene Kacheln schnappen ohne Strafe zurück.
- Safe-Area: Inhalt unter Status-/Nav-Leisten und über Home-Indikator halten; unten extra Abstand.

## 3. Lernprogression (Fibel-Lernpfad)

Der Lehrplan besteht aus 16 Lektionen in fünf Phasen (Fibel-Reihenfolge).
Jede Lektion führt **genau sechs Trainer in fester Reihenfolge** durch:

1. **Auditiver Finder** — Laut im gesprochenen Wort verorten (Lok mit Anfang/Mitte/Ende-Waggon).
2. **Visueller Spurensucher** — Graphem nachspuren, Sterne in Strichreihenfolge sammeln.
3. **Silben-Verschmelzer** — Konsonant auf Vokal ziehen, Silbe entsteht.
4. **Wort-Bauer** — Silben-/Buchstabenklötze in Schablonen unter dem Bild.
5. **Satz-Architekt** — Wortschilder an die Wäscheleine; Einwort-Runden sind Wort-Bild-Zuordnung.
6. **Rechnen** — reine Mengen-Arithmetik in *jeder* Lektion, Icons aus dem Wortschatz
   derselben Lektion. **Keine Wörter zum Lesen oder Schreiben**; Singular/Plural nur gesprochen.

Reihenfolge-Regeln, die Content und Validator erzwingen:

- Der Wort-Bauer zeigt nie ein Graphem oder eine Silbe, die noch nicht eingeführt wurde.
- Satzrunden nutzen nur gebaute Wörter, kleingeschriebene Funktionswörter oder ausdrücklich
  als `holisticAtomIds` markierte Ganzwort-Bilder (so führt die Fibel z. B. „Tor" vor dem R ein).
- Lektion *n* wird erst frei, wenn Lektion *n−1* gemeistert ist (jeder Trainer mindestens einmal richtig).
- Gemeisterte Lektionen bleiben zum Wiederholen antippbar.

## 4. Content-Graph

- Atome (Buchstabe / Silbe / Wort + Emoji) sind wiederverwendbar über alle sechs Trainer einer Lektion.
- Tasks referenzieren Atom-IDs; Validierung verhindert tote Referenzen.
- Orthografie: Silben eher klein; zusammengesetzte Wörter/Sätze korrekt großgeschrieben.
- Rechnen nutzt die Bild-Ikonen derselben Lektion; Details zu Singular/Plural siehe Abschnitt 8.

## 5. Session-Modell

- **Pfad-Screen ist der Einstieg** (winkende S-Kurve, ein Knoten pro Lektion, Label = Graphem).
  Gesperrte und noch nicht autorierte Knoten reagieren auf Tippen mit einem gesprochenen Hinweis —
  niemals mit einem stummen No-Op.
- Tippen auf einen freigeschalteten Knoten startet die Sechs-Trainer-Session dieser Lektion.
- Kein Domänen-Mix, keine Zufallsrotation: die Trainer-Reihenfolge ist didaktisch fix.
- Vor/Zurück zwischen Runden ist **immer** möglich, unabhängig von Punkten/Fortschritt.
- Fortschritt speichern nach jeder Antwort; unfertige Lektion wird beim Öffnen fortgesetzt.
- Back in der Übung → Belohnungszusammenfassung (oder direkt zum Pfad, wenn noch keine Punkte);
  Back auf dem Pfad verlässt die App, ohne Fortschritt zu löschen.

## 6. Hilfestufen

- Parent-Gate (langer Druck auf ⋯): **Auto / Mit Hilfe / Ohne Hilfe**.
- Auto passt Gerüste sanft an; erzwungene Stufen frieren Auto-Streaks ein.
- Gerüste pro Atom/Slot (Silhouette vs. Lücke), nicht global starr über die ganze Aufgabe.

## 7. Sprache & Audio

- App und Inhalte deutsch.
- System-TTS für Prompts; Speaker (Vektor-Icon) **im Aufgabenbereich**, mittig über dem Aufgabentitel.
- Tippen auf Aufgaben-Items (Buchstaben, Silben, Wörter, Antwortkacheln) liest sie vor.
- Bei Erfolg: Antwort vorsprechen → Stern im oberen Drittel → erst danach nächste Aufgabe (Audio abwarten).
- Wenn kein deutsches TTS: visuelle Fallbacks, Aufgabe bleibt spielbar.
- Keine reinen „Sag …“-Screens mit nur Weiter-Button; jeder Trainer ist interaktiv (Ziehen/Tippen mit Tap-Alternative), auch wenn TTS den Prompt trägt. Es gibt keinen eigenen Sprech-Trainer.
- Wort-Bauer (Trainer 4): Prompt „Bilde das Wort …“; Silben-/Buchstabenklötze in Schablonen tragen die Aufgabe, keine zusätzliche Lese-Titelzeile.
- Feedback bei Fehlern (besonders Rechnen): **vorsprechen**, nicht als Fehler-Satz anzeigen.

## 8. Mathematik-Visuals

- Mengen als Bilder/Emojis, sinnvoll gruppiert (Subitizing: Paare + Rest, z. B. 5 = 2+2+1).
- Zahl unter der Bildgruppe anzeigen.
- Aufgabe oben, Antwortwahl unten; Bilder in der Aufgabe ausreichend groß.
- Visuelle Additionsaufgaben: genau **3** Antwortoptionen.
- Rechnen „Ohne Hilfe“ (Zahlen-Eingabe): Antwortfeld nutzt die **System-Tastatur im Zahlenmodus** (kein Custom-Nummernblock) plus ein CTA-Absenden-Button mit Pfeil-Icon.
- Rechnen läuft in **jeder** Lektion, mit den Bild-Ikonen derselben Lektion (kontextnah, aber
  nicht zwingend — Kinder erkennen die Icons ohnehin).
- Im Rechen-Trainer gibt es **keine Wörter zum Lesen oder Schreiben**. Singular/Plural wird
  ausschließlich im gesprochenen Prompt geübt.

## 9. Layout-Grundform der Übungen

- **Chrome oben:** Parent-Gate · Punkte · Speaker; darunter zentriert Zurück/Weiter-Chevrons; darunter Status-/Fortschrittsbalken.
- **Prompt/Aufgabe:** oberer Block, zentriert, mit Luft zu den Rändern (kein Kleben am Screenrand).
- **Antworten:** unterer Block, zentriert (Kacheln, Mengenwahl, Ziffernblock).
- Keine doppelte Aufgabe+Vorschau desselben Tokens.

## 10. Design-System

- Gemeinsame Komponenten unter `ui/components/` (`AbcContinueButton`, `AbcSpeakerButton`, `AbcNavChevron`, `AbcProgressBar`, Vektor-Icons inkl. `IconStar`).
- Buttons: keine Emojis — nur ASCII oder Canvas/SVG-Vektoren. Punkte-/Erfolgs-Symbol ist der Vektor-Stern `IconStar`, kein Text-Asterisk.
- Übungen nutzen `ExerciseStage` für klare Trennung Aufgabenblock / Antwortblock.

## 11. Was bewusst nicht in v1 gehört

- Werbung, IAP, Pflicht-Accounts, Kinderprofile.
- Mikrofon-Bewertung, Schreib-/Trace-Modus (über Trainer 2 hinaus), Eltern-Dashboard.
- Englisch oder Mehrsprachigkeit als Produktkern.
- Sprech-Trainer mit „Sprich mit!"-Cue (zurückgezogen — das didaktische Konzept kennt keinen
  eigenen Sprech-Trainer; TTS bleibt für Prompts und Vorlesen).
- Lese-Cloze/Wortfolge als eigenständige Trainer (durch Trainer 1–5 ersetzt).

## Ableitung für Agenten und Reviews

Wenn eine Änderung vorgeschlagen wird, prüfen:

| Frage | Erwartung |
|-------|-----------|
| Muss das Kind Text lesen, um zu handeln? | Nein → Icon/Audio/Layout |
| Überspringt der Content Buchstaben/Silben? | Nein → Fibel-Reihenfolge |
| Sind Distraktoren erfunden statt bereits geübt? | Nein — nur bekannte Atome |
| Kann die Aufgabe überhaupt fehlschlagen (Signal für Adaptivität)? | Ja — sonst Distraktoren/Slots prüfen |
| Bleibt Offline-Kern erhalten? | Ja |
| Ist das UI ruhig und kindgerecht? | Ja |
| Fehlerfeedback für Vorschulkinder? | Audio, kein Lesesatz |
| Buttons mit Emoji? | Nein → Vektor/ASCII |
| Zeigt der Wort-Bauer ein noch nicht eingeführtes Graphem? | Nein → Fibel-Reihenfolge |
| Enthält der Rechen-Trainer Lesewörter? | Nein → nur Icons und Ziffern |
| Hat jede autorierte Lektion genau die sechs Trainer in Reihenfolge? | Ja → Validator prüft das |

Siehe auch [`AGENTS.md`](../AGENTS.md) für den Arbeitsprozess und Dokumentationspflichten.
