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
- Lesbare Labels sind erlaubt, wo sie Erwachsenen helfen oder wo Buchstaben/Wörter *die Lernaufgabe selbst* sind — nicht als Anweisungschrome („Sprich mit!“, Pack-Titel, lange Erklärtexte).
- Handlungs-Buttons (z. B. **Weiter**): Text optional, immer klares **Vektor-/ASCII-Icon** (keine Emojis in Buttons). **Weiter** rechts ausrichten.
- Dunkles, ruhiges UI; weiches Feedback statt Strafe oder Drucksprache.
- Distraktoren nur aus **echten, bereits geübten Atomen** (max. 2 pro Aufgabe, Tray ≤ 5 Kacheln) — nie erfundene „Fake-Antworten“. Falsche Kachel oder falsche Platzierung ist einfach falsch (gesprochenes Feedback). Die erste Begegnung mit neuem Stoff bleibt distraktorfrei.
- Drag & Drop committet nur bei echtem Slot-Treffer (Hit-Testing); daneben losgelassene Kacheln schnappen ohne Strafe zurück.
- Safe-Area: Inhalt unter Status-/Nav-Leisten und über Home-Indikator halten; unten extra Abstand.

## 3. Lernprogression (Fibel)

Reihenfolge beim Lesen:

1. **Buchstaben** — Groß und Klein als zwei Frames (`[O]` / `[o]`) mit zwei Antwort-Teilen; TTS: „Finde den Buchstaben {a}.“ (nicht „Aa“ vorlesen). Die Silhouette in beiden Frames wird **immer** gezeigt (auch nach Scaffold-Aufstieg) — Groß/Klein-Zuordnung ist Positionswissen, kein Skill, das ausgeblendet werden soll.
2. **Silben** — aus bekannten Buchstaben (z. B. M + A → `ma`); groß und gut tippbar.
3. **Wörter aus Silben bauen** — sichtbar zusammensetzen (`Ma ⋅ ma` → Mama, `O ⋅ ma` → Oma); Prompt z. B. „Bilde das Wort Mama“.
4. **Ganzwort** — erst nachdem das Wort über Silben eingeführt wurde (wo Compose-Aufgaben existieren).
5. **Einfache Sätze** — erst wenn die beteiligten Atome angeboten wurden.

Scheduler und Content-Pack müssen diese Reihenfolge erzwingen. Keine Abkürzung zu „Mama“ ohne Buchstaben-/Silbenfundament.
Der Scheduler darf Buchstaben nicht dauerhaft priorisieren, wenn alle Buchstaben-Aufgaben schon angeboten wurden (sonst verhungern Silben/Compose).

## 4. Content-Graph

- Atome (Buchstabe / Silbe / Wort + Emoji) sind wiederverwendbar über Domänen.
- Tasks referenzieren Atom-IDs; Validierung verhindert tote Referenzen.
- Orthografie: Silben eher klein; zusammengesetzte Wörter/Sätze korrekt großgeschrieben.
- Math nutzt dieselben Bildanker (Singular/Plural) wo sinnvoll — viele Aufgaben mit **1**, damit Singular/Plural im Prompt geübt wird.

## 5. Session-Modell

- Kurzer Mix (~5 Aufgaben) über Lesen / Sprechen / Rechnen.
- Kein Pfad-/Landkartenscreen in v1. *(Ein Start-Index/Pfad-Screen wird für v2 geplant — siehe `docs/plans/`; bis zur Entscheidung gilt diese Zeile weiter.)*
- Vor/Zurück zwischen Aufgaben ist **immer** möglich (unabhängig von Punkten/Fortschritt) — Navigation ist nie an Scoring gekoppelt.
- Fortschritt speichern nach Antworten; unfertige Session fortsetzbar.
- Belohnungszusammenfassung mit Continue; keine Bestrafung beim Abbrechen.

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
- Keine reinen „Sag …“-Screens mit nur Weiter-Button; Sprech-/Wortaufgaben sind immer interaktiv (Cloze/Buchstabieren).
- Wort-Buchstabieren (z. B. Haus): Titel = Wort; Frames/Antworten pro Buchstabe; Prompt „Bilde das Wort …“.
- Feedback bei Fehlern (besonders Rechnen): **vorsprechen**, nicht als Fehler-Satz anzeigen.

## 8. Mathematik-Visuals

- Mengen als Bilder/Emojis, sinnvoll gruppiert (Subitizing: Paare + Rest, z. B. 5 = 2+2+1).
- Zahl unter der Bildgruppe anzeigen.
- Aufgabe oben, Antwortwahl unten; Bilder in der Aufgabe ausreichend groß.
- Visuelle Additionsaufgaben: genau **3** Antwortoptionen.
- Rechnen „Ohne Hilfe“ (Zahlen-Eingabe): Antwortfeld nutzt die **System-Tastatur im Zahlenmodus** (kein Custom-Nummernblock) plus ein CTA-Absenden-Button mit Pfeil-Icon.

## 9. Layout-Grundform der Übungen

- **Chrome oben:** Parent-Gate · Punkte · Speaker; darunter zentriert Zurück/Weiter-Chevrons; darunter Status-/Fortschrittsbalken.
- **Prompt/Aufgabe:** oberer Block, zentriert, mit Luft zu den Rändern (kein Kleben am Screenrand).
- **Antworten:** unterer Block, zentriert (Kacheln, Mengenwahl, Ziffernblock).
- Buchstaben-/Silben-Trainer: ausgegrautes „ABC“ als dezentes Branding (kein ablenkendes Icon); Puzzle-Teile **ohne** Emoji-Icons.
- Keine doppelte Aufgabe+Vorschau desselben Tokens (kein „Oo“ über einem zweiten „Oo“).
- Lückentext-Sätze: die Antwortlücken sitzen **inline im Satztext** (an der Wortposition), nicht als separate Kachelreihe darunter — sonst ist für das Kind nicht erkennbar, wohin die Antwort gehört.

## 10. Design-System

- Gemeinsame Komponenten unter `ui/components/` (`AbcContinueButton`, `AbcSpeakerButton`, `AbcNavChevron`, `AbcProgressBar`, Vektor-Icons inkl. `IconStar`).
- Buttons: keine Emojis — nur ASCII oder Canvas/SVG-Vektoren. Punkte-/Erfolgs-Symbol ist der Vektor-Stern `IconStar`, kein Text-Asterisk.
- Übungen nutzen `ExerciseStage` für klare Trennung Aufgabenblock / Antwortblock.

## 11. Was bewusst nicht in v1 gehört

- Werbung, IAP, Pflicht-Accounts, Kinderprofile.
- Mikrofon-Bewertung, Schreib-/Trace-Modus, Eltern-Dashboard.
- Englisch oder Mehrsprachigkeit als Produktkern.

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

Siehe auch [`AGENTS.md`](../AGENTS.md) für den Arbeitsprozess und Dokumentationspflichten.
