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

- **Das Kind kann (noch) nicht esen.** UI-Steuerung muss über Bild, Icon, Layout und Audio verständlich sein.
- Lesbare Labels sind erlaubt, wo sie Erwachsenen helfen oder wo Buchstaben/Wörter *die Lernaufgabe selbst* sind — nicht als Anweisungschrome (Pack-Titel, lange Erklärtexte).
- Handlungs-Buttons (z. B. **Weiter**): Text optional, immer klares **Vektor-/ASCII-Icon** (keine Emojis in Buttons). 
- Dunkles, ruhiges UI; weiches Feedback statt Strafe oder Drucksprache.
- Distraktoren nur aus **echten, bereits geübten Atomen** (max. 2 pro Aufgabe, Tray ≤ 5 Kacheln) — nie erfundene „Fake-Antworten“. Falsche Kachel oder falsche Platzierung ist einfach falsch (gesprochenes Feedback). Die erste Begegnung mit neuem Stoff bleibt distraktorfrei.
- Ausnahme Buchstaben-/Silben-Jagd: Streufeld statt Distraktor-Budget (bis zu 6 Distraktor-Kacheln, teils wiederholt) — die Übung braucht mehr Ablenker als eine autorierte Tray-Aufgabe.
- Drag & Drop committet nur bei echtem Slot-Treffer (Hit-Testing); daneben losgelassene Kacheln schnappen ohne Strafe zurück.
- Safe-Area: Inhalt unter Status-/Nav-Leisten und über Home-Indikator halten; unten extra Abstand.



## 3. Lernprogression (Fibel-Lernpfad)

Der Lehrplan besteht aus 18 Lektionen in fünf Phasen (Fibel-Reihenfolge). Jede Lektion führt die sechs Trainer-**Typen** unten in fester Rangfolge durch — ein Typ kann sich wiederholen oder ganz fehlen (z. B. keine Satzrunde in einer Lektion), die Reihenfolge geht aber nie zurück; jede Lektion beginnt mit dem Auditiven Finder und endet mit Rechnen:

1. **Auditiver Finder** — Laut im gesprochenen Wort verorten (Lok mit Anfang/Mitte/Ende-Waggon). Der gesuchte Laut steht groß über dem Zug; darunter zeigt das Bildwort seine Graphemgruppen in den drei Waggonfarben.
2. **Visueller Spurensucher** — Graphem nachzeichnen („Zeichne das große T nach …"), gelbe Sterne in
  Strichreihenfolge sammeln. Nur der aktive Balken zeigt leuchtende Sterne, kommende Balken blass;
  eingesammelte Sterne verschwinden. Fertige Balken füllen sich ease-in ein, das Fahrzeug springt an
  den Startpunkt des nächsten Balkens. Nach dem letzten Stern hält der fertige Buchstabe eine halbe
  Sekunde, dann folgt die Belohnungsseite: Bild groß, darunter die Wortzeile („**T** wie Tomate")
  mit fettem Graphem. Kein zusätzlicher Buchstaben-Text unter dem Pfad.
3. **Silben-Verschmelzer** — Konsonant auf Vokal ziehen, Silbe entsteht.
4. **Wort-Bauer** — Silben-/Buchstabenklötze in Schablonen unter dem Bild.
5. **Satz-Architekt** — Wortschilder an die Wäscheleine; Einwort-Runden sind Wort-Bild-Zuordnung.
6. **Rechnen** — reine Mengen-Arithmetik in *jeder* Lektion, Icons aus dem Wortschatz
  derselben Lektion. **Keine Wörter zum Lesen oder Schreiben**; Singular/Plural nur gesprochen.

Zusätzlich, bis zu zweimal pro Lektion und ohne eigenen autorierten Content: eine **Buchstaben-Jagd** direkt nach dem Spurensucher und eine **Silben-Jagd** direkt nach dem Silben-Verschmelzer — jeweils nur, wenn die Lektion den entsprechenden Trainer führt und mindestens ein bereits bekanntes Vergleichssymbol existiert. Kind tippt alle Vorkommen des gesuchten Symbols in einem verstreuten Feld an; Treffer füllen eine Batterie, Fehltipp mischt neu ohne Batterieverlust.

Reihenfolge-Regeln, die Content und Validator erzwingen:

- Der Wort-Bauer zeigt nie ein Graphem oder eine Silbe, die noch nicht eingeführt wurde.
- Satzrunden nutzen nur gebaute Wörter, kleingeschriebene Funktionswörter oder ausdrücklich als `holisticAtomIds` markierte Ganzwort-Bilder (so führt die Fibel z. B. „Tor" vor dem R ein).
- Gemeisterte Lektionen bleiben zum Wiederholen antippbar.



## 4. Content-Graph

- Atome (Buchstabe / Silbe / Wort + Emoji) sind wiederverwendbar über alle sechs Trainer-Typen einer Lektion.
- Tasks referenzieren Atom-IDs; Validierung verhindert tote Referenzen.
- Orthografie: Silben eher klein; zusammengesetzte Wörter/Sätze korrekt großgeschrieben.
- Rechnen nutzt die Bild-Ikonen derselben Lektion; Details zu Singular/Plural siehe Abschnitt 8.
- Jede autorierte Lektion verweist über `finaleId` auf einen **Finale-Satz** in
  `finales.json` — den Belohnungssatz des End-Screens (Abschnitt 12). Wiederholungslektionen
  teilen den Satz ihrer Basis-Lektion, statt ihn zu duplizieren.
- Finale-Sätze sind ein **eigener Content-Typ**, nicht `Sentence`: sie enthalten bewusst
  Verben und Adjektive außerhalb des Atom-Graphen, weil sie nie gebaut oder gelesen werden
  müssen. Nur die bildtragenden Nomen sind Atome.



## 5. Session-Modell

- **Pfad-Screen ist der Einstieg** (winkende S-Kurve, ein Knoten pro Lektion, Label = Graphem).
Gesperrte und noch nicht autorierte Knoten reagieren auf Tippen mit einem gesprochenen Hinweis —
niemals mit einem stummen No-Op.
- Tippen auf einen freigeschalteten Knoten startet die Sechs-Trainer-Typen-Session dieser Lektion.
- Kein Domänen-Mix, keine Zufallsrotation: die Trainer-Reihenfolge ist didaktisch fix.
- Vor/Zurück zwischen Runden ist **immer** möglich, unabhängig von Punkten/Fortschritt.
- Fortschritt speichern nach jeder Antwort; unfertige Lektion wird beim Öffnen fortgesetzt.
- Back in der Übung und der Schließen-Button verlassen die Lektion **direkt** zum Pfad, ohne
  End-Screen — unabhängig von den Punkten.
- **Der End-Screen erscheint nur beim echten Lektionsabschluss**, mit Finale (Bildreihe + Satz
  + Speaker, Abschnitt 12). Der Satz belohnt damit Durchhalten und nutzt sich nicht ab.
- Der End-Screen kennt zusätzlich eine **schlanke Variante** ohne Bildreihe und Satz. Sie ist
  ein Defensivpfad für den Fall, dass sich kein Finale auflösen lässt — der Validator verbietet
  das für autorierte Lektionen, also praktisch unerreichbar, aber ein reduzierter Screen ist
  besser als ein leerer oder ein Absturz.
- Der End-Screen zeigt **keine Punktezahl**. Punkte stehen im Übungs-Chrome und auf dem Pfad.

## 6. Hilfestufen

- Parent-Gate (langer Druck auf ⋯): **Auto / Mit Hilfe / Ohne Hilfe**.
- Auto passt Gerüste sanft an; erzwungene Stufen frieren Auto-Streaks ein.
- Gerüste pro Atom/Slot (Silhouette vs. Lücke), nicht global starr über die ganze Aufgabe.

## 7. Sprache & Audio

- App und Inhalte deutsch. Sprache ist bei jedem TTS Aufruf eindeutet angegeben. 
- System-TTS für Prompts; Speaker (Vektor-Icon) **im Aufgabenbereich**, mittig über dem Aufgabentitel.
- Tippen auf Aufgaben-Items (Buchstaben, Silben, Wörter, Antwortkacheln) liest sie vor.
- Bei Erfolg: Antwort vorsprechen → Stern im oberen Drittel → erst danach nächste Aufgabe (Audio abwarten).
- Lob (**nur Rechnen, nur gesprochen**): ein zufälliges Wort aus `PraisePhrases` steht vor der Antwort
  („Ausgezeichnet! zwei Ameisen"), damit die Menge das Letzte bleibt, was das Kind hört. Nie als Text
  anzeigen — das Kind kann nicht lesen. Auflösen („Zeig mir") bekommt kein Lob.
- Wenn kein deutsches TTS: visuelle Fallbacks, Aufgabe bleibt spielbar.
- Wort-Bauer (Trainer 4): Prompt „Bilde das Wort …“; Silben-/Buchstabenklötze in Schablonen tragen die Aufgabe, keine zusätzliche Lese-Titelzeile.
- Feedback bei Fehlern (besonders Rechnen): **vorsprechen**, nicht als Fehler-Satz anzeigen.

## 8. Mathematik-Visuals

- Mengen bis 10 als Bilder/Emojis, sinnvoll gruppiert (Subitizing: Paare + Rest, z. B. 5 = 2+2+1). Ab 11 steht ein einzelnes Bildsymbol mit der Zahl für die Menge.
- Zahl unter der Bildgruppe anzeigen.
- Aufgabe oben, Antwortwahl unten; Bilder in der Aufgabe ausreichend groß.
- Visuelle Mengenaufgaben: genau **3** Antwortoptionen; gleiche Dimensionen der buttons. Die Progression führt von Plus über Wegnehmen zu einfachen gleichen Gruppen (Malnehmen); Situationen bleiben gesprochen, konkret und kindernah.
- Korrekte Antwort bestätigt sich **grün** (Kachel bzw. Zahlenfeld), solange sie vorgesprochen wird.
  Falsche Antwort wird **nicht** rot markiert — Miss bleibt gesprochenes Feedback. Auflösen ist nicht grün.
- Rechnen „Ohne Hilfe“ (Zahlen-Eingabe): Antwortfeld nutzt die **System-Tastatur im Zahlenmodus** (kein Custom-Nummernblock) plus ein CTA-Absenden-Button mit Pfeil-Icon.
- Rechnen läuft in **jeder** Lektion, mit den Bild-Ikonen derselben Lektion (kontextnah, aber  
nicht zwingend — Kinder erkennen die Icons ohnehin)



## 9. Layout-Grundform der Übungen

- **Chrome oben:** Parent-Gate · Punkte · Speaker; darunter zentriert Zurück/Weiter-Chevrons; darunter Status-/Fortschrittsbalken.
- **Prompt/Aufgabe:** oberer Block, zentriert, mit Luft zu den Rändern (kein Kleben am Screenrand).
- **Antworten:** unterer Block, zentriert (Kacheln, Mengenwahl, Ziffernblock).
- Keine doppelte Aufgabe+Vorschau desselben Tokens.
- Ausnahme Buchstaben-/Silben-Jagd: Kacheln verstreuen sich über den gesamten Aufgabenbereich statt in einer geordneten Antwortliste; die Batterie bleibt im Antwortbereich unten.



## 10. Design-System

- Gemeinsame Komponenten unter `ui/components/` (`AbcContinueButton`, `AbcSpeakerButton`, `AbcNavChevron`, `AbcProgressBar`, Vektor-Icons inkl. `IconStar`).
- Buttons: keine Emojis — nur ASCII oder Canvas/SVG-Vektoren. Punkte-/Erfolgs-Symbol ist der Vektor-Stern `IconStar`, kein Text-Asterisk.
- Übungen nutzen `ExerciseStage` für klare Trennung Aufgabenblock / Antwortblock.



## 11. Was bewusst nicht in v1 gehört

- Werbung, IAP, Pflicht-Accounts, Kinderprofile.
- Mikrofon-Bewertung, Eltern-Dashboard.
- Englisch oder Mehrsprachigkeit als Produktkern.
- Sprech-Trainer mit „Sprich mit!"-Cue.
- Lese-Cloze/Wortfolge als eigenständige Trainer
- Animierte Finale-Szene („Quatsch-Maschine": tippbarer Wal, der Wasser spritzt),
  Sammelalbum für Finale-Sätze, wortsynchrone Bildeinblendung zum TTS-Audio.



## 12. Finale-Sätze (Lesson-End)

Nach dem Abschluss einer Lektion hört das Kind einen kurzen, lustigen Einzeiler aus dem
Vokabular genau dieser Lektion. Die Nomen des Satzes stehen darüber als Bildreihe in
Satzreihenfolge (Rebus). Der Satz steht zusätzlich als Text da — **nicht für das Kind,
sondern für den Erwachsenen daneben.** Das ist die eine bewusste Ausnahme von Abschnitt 2:
keine Handlung hängt an diesem Text, und die Wort-Bild-Kopplung entsteht ohnehin über
Bild und Audio.

### Warum Bilder statt Graphem-Icons

Die Bildreihe zeigt die **Nomen des Satzes**, nicht die Fokus-Grapheme der Lektion.
`letter-*`-Atome tragen kein Emoji, und `letter_trace.rewardEmoji` ist die Belohnung des
Spurensuchers und wird nicht vorweggenommen (siehe Abschnitt 4).

Welche Nomen ein Bild bekommen, ist **redaktionell autoriert** (`pictureAtomIds`), nicht
aus dem Text abgeleitet. Automatisches Wort→Atom-Matching scheitert an Flexion („roten
Hut"), an geteilten Glyphen (`dach` und `haus` sind beide 🏠) und an der Frage, welches
Nomen ein Bild verdient. `pictureAtomIds` ist eine fest autorierte Reihenfolge — kein
Random, kein Shuffle — nach demselben Compose-freien, testbaren Muster wie
`ui/exercise/WordFrameSizing.kt` und `ui/exercise/SyllableFrameSizing.kt`.

### Redaktionsregeln für neue Sätze

- **Kurz:** 4 bis 7 Wörter. *Vom `ContentValidator` erzwungen.*
- **Ein Bild, eine Handlung.** Keine Mini-Geschichte, kein zweiter Nebensatz.
- **Komisch durch Handlung**, nicht durch Wortwahl: klauen, mampfen, stecken, knacken,
  bewundern, jonglieren.
- **Cartoon-Logik statt Surrealismus.** Ein Tier mit Hut oder ein Tier, das etwas
  Alltägliches tut, ist verständlich. Eine Nase, die wegläuft, ist es nicht.
- **Kein AI-Slop:** keine Ansammlung seltener Wörter, keine Situation, deren einziger
  Zweck maximale Absurdität ist. Prüffrage: Würde das Bild in einem Kinderbuch stehen?
- **Adjektive sparsam** — nur wenn sie für Bild oder Laut etwas leisten („dicker Apfel",
  „roter Hut").
- **Reim und Alliteration sind erlaubt, nie Pflicht.** Klang darf helfen, aber nie den
  Satz erzwingen.
- **Die bildtragenden Nomen tragen die Fokus-Grapheme der Lektion.** Das ist die eigentliche
  Anforderung, nicht Herkunft aus dem Trainer-Vokabular: L10 („G & Ch") nimmt *Giraffe* und
  *Dach*, L14 („J, Z & Eu") *Zebra* und *Jojo*, L17 („St & Sp") *Spinne* und *Spiegel*. Ein
  Nomen darf dafür **neu** sein und ausschließlich im Finale vorkommen — `kuchen` 🍰 ist genau
  so ein Atom (`kind: other`, nie gelesen, nie gebaut). Verben und Adjektive sind ohnehin frei,
  weil sie nie gelesen werden.
  Grund für die Freiheit: der Satz ist eine Belohnung, keine Übung. Er muss den Laut der
  Lektion hörbar machen und ein Bild erzeugen — nicht die Kacheln des Wort-Bauers wiederholen.
  Die strengen Reihenfolge-Regeln aus Abschnitt 3 gelten für Trainer-Content, nicht hier.
- **Mindestens zwei bildtragende Nomen, maximal vier.** *Vom `ContentValidator` erzwungen.*
- **Kein Nomen doppelt bebildern**, wenn zwei Atome denselben Emoji-Glyph teilen
  (`katze` und `mimi` sind beide 🐱 → nur eines).
- **Ein Nomen ohne brauchbares Emoji bekommt kein Bild.** Zwei Bilder sind erlaubt; ein
  schlecht passendes Emoji ist schlimmer als eines weniger (für „Tisch" gibt es keins —
  🪑 ist ein Stuhl).

### Audio

- Der Satz wird beim Erscheinen des Screens vorgelesen; ein Speaker-Button wiederholt ihn.
- Tippen auf ein Bild spricht sein Wort (Abschnitt 7: antippbare Items werden vorgelesen).
- Ohne deutsches TTS bleibt der Screen vollständig, der Speaker ist deaktiviert.



## Ableitung für Agenten und Reviews

Wenn eine Änderung vorgeschlagen wird, prüfen:


| Frage                                                               | Erwartung                            |
| ------------------------------------------------------------------- | ------------------------------------ |
| Muss das Kind Text lesen, um zu handeln?                            | Nein → Icon/Audio/Layout             |
| Überspringt der Content Buchstaben/Silben?                          | Nein → Fibel-Reihenfolge             |
| Sind Distraktoren erfunden statt bereits geübt?                     | Nein — nur bekannte Atome            |
| Kann die Aufgabe überhaupt fehlschlagen (Signal für Adaptivität)?   | Ja — sonst Distraktoren/Slots prüfen |
| Bleibt Offline-Kern erhalten?                                       | Ja                                   |
| Ist das UI ruhig und kindgerecht?                                   | Ja                                   |
| Fehlerfeedback für Vorschulkinder?                                  | Audio, kein Lesesatz                 |
| Buttons mit Emoji?                                                  | Nein → Vektor/ASCII                  |
| Zeigt der Wort-Bauer ein noch nicht eingeführtes Graphem?           | Nein → Fibel-Reihenfolge             |
| Enthält der Rechen-Trainer Lesewörter?                              | Nein → nur Icons und Ziffern         |
| Hält jede autorierte Lektion die sechs Trainer-Typen in nicht-fallender Rangfolge (Start Auditiver Finder, Ende Rechnen)? | Ja → Validator prüft das             |
| Ist ein neuer Finale-Satz länger als 7 Wörter oder eine Mini-Geschichte?     | Nein → Abschnitt 12, Validator prüft |
| Wäre das Bild des Finale-Satzes in einem Kinderbuch denkbar?                 | Ja — sonst AI-Slop                   |
| Zeigt der End-Screen eine Punktezahl?                                        | Nein → Punkte leben im Chrome/Pfad   |


Siehe auch `[AGENTS.md](../AGENTS.md)` für den Arbeitsprozess und Dokumentationspflichten.
