# ABC-Vorschul App — Produktprinzipien

Dieses Dokument ist die verbindliche Quelle für Produkt- und UX-Grundsätze.
Bei Konflikten mit Implementierungsdetails oder älteren Planabschnitten gelten diese Prinzipien
(und aktuelle Nutzerentscheidungen in der Session), sofern sie nicht ausdrücklich revidiert wurden.

## 1. Für wen die App da ist

- Primäre Nutzer: Kinder im Vorschulalter (ca. 4–7 Jahre).
- Eltern steuern nur selten und immer hinter derselben Kindersicherung: Hilfestufe und Freigabe der
  Lektionsreihenfolge — nicht den Lerninhalt im Alltag.
- Die App ist kostenlos, werbefrei und ohne Monetarisierung in der Produktidentität.
- Offline nach Installation: Kernpraxis braucht kein Netz.



## 2. Kind-zentrierte Oberfläche

- **Das Kind kann (noch) nicht esen.** UI-Steuerung muss über Bild, Icon, Layout und Audio verständlich sein.
- Lesbare Labels sind erlaubt, wo sie Erwachsenen helfen oder wo Buchstaben/Wörter *die Lernaufgabe selbst* sind — nicht als Anweisungschrome (Pack-Titel, lange Erklärtexte).
- Handlungs-Buttons (z. B. **Weiter**): Text optional, immer klares **Vektor-/ASCII-Icon** (keine Emojis in Buttons). 
- Helles, warmes, ruhiges UI (Creme statt Weiß — augenfreundlich); weiches Feedback statt Strafe oder Drucksprache.
- Distraktoren nur aus **echten, bereits geübten Atomen** (max. 2 pro Aufgabe, Tray ≤ 5 Kacheln) — nie erfundene „Fake-Antworten“. Falsche Kachel oder falsche Platzierung ist einfach falsch (gesprochenes Feedback). Die erste Begegnung mit neuem Stoff bleibt distraktorfrei.
- Ausnahme Buchstaben-/Silben-Jagd: Streufeld statt Distraktor-Budget (bis zu 6 Distraktor-Kacheln, teils wiederholt) — die Übung braucht mehr Ablenker als eine autorierte Tray-Aufgabe.
- Drag & Drop committet nur bei echtem Slot-Treffer (Hit-Testing); daneben losgelassene Kacheln schnappen ohne Strafe zurück.
- Safe-Area: Inhalt unter Status-/Nav-Leisten und über Home-Indikator halten; unten extra Abstand.



## 3. Lernprogression (Fibel-Lernpfad)

Der Lehrplan besteht aus 18 Lektionen in fünf Phasen (Fibel-Reihenfolge). Jede Lektion führt die sechs Trainer-**Typen** unten in fester Rangfolge durch — ein Typ kann sich wiederholen oder ganz fehlen (z. B. keine Satzrunde in einer Lektion), die Reihenfolge geht aber nie zurück; jede Lektion beginnt mit dem Auditiven Finder und endet mit Rechnen:

1. **Auditiver Finder** — Laut im gesprochenen Wort verorten (Lok mit Anfang/Mitte/Ende-Waggon). Der gesuchte Laut steht groß über dem Zug; darunter zeigt das Bildwort seine Graphemgruppen in den drei Waggonfarben.
2. **Visueller Spurensucher** — Graphem nachzeichnen („Zeichne das große T nach …"), gelbe Sterne in
  Strichreihenfolge sammeln. Nur der aktive Balken zeigt leuchtende Sterne, kommende Balken blass;
  eingesammelte Sterne verschwinden. Der aktive Balken liegt zuoberst und alle Sterne liegen über
  allen Balken — kein späterer Balken darf den aktuellen Pfad oder seine Sterne verdecken; ist ein
  Balken fertig, rückt der nächste nach oben. Fertige Balken füllen sich ease-in ein, das Fahrzeug springt an
  den Startpunkt des nächsten Balkens. Nach dem letzten Stern hält der fertige Buchstabe eine halbe
  Sekunde, dann folgt die Belohnungsseite: Bild groß, darunter die Wortzeile („**T** wie Tomate")
  mit fettem Graphem. Kein zusätzlicher Buchstaben-Text unter dem Pfad.
3. **Silben-Verschmelzer** — beide Laut-Kacheln sind schiebbar und wandern symmetrisch
  aufeinander zu (Magnet-Metapher); eine gepunktete Schiebespur mit einwärts laufender
  Lichtwelle und ein Idle-„Atmen" laden ohne Text zum Schieben ein. Ab 60 % Nähe schnappen
  die Kacheln zusammen, darunter gleiten sie straflos zurück. Ein Tipp auf eine Kachel
  liest ihren Laut vor **und** stupst sie einen Schritt (30 %) näher — zwei Tipps
  verschmelzen, es gibt keinen separaten Bestätigungs-Button.
4. **Wort-Bauer** — Silben-/Buchstabenklötze in Schablonen unter dem Bild.
5. **Satz-Architekt** — Wortschilder an die Wäscheleine; Einwort-Runden sind Wort-Bild-Zuordnung.
6. **Rechnen** — reine Mengen-Arithmetik in *jeder* Lektion, Icons aus dem Wortschatz
  derselben Lektion. **Keine Wörter zum Lesen oder Schreiben**; Singular/Plural nur gesprochen.

Zusätzlich, bis zu zweimal pro Lektion und ohne eigenen autorierten Content: eine **Buchstaben-Jagd** direkt nach dem Spurensucher und eine **Silben-Jagd** direkt nach dem Silben-Verschmelzer — jeweils nur, wenn die Lektion den entsprechenden Trainer führt und mindestens ein bereits bekanntes Vergleichssymbol existiert. Kind tippt alle Vorkommen des gesuchten Symbols in einem verstreuten Feld an; Treffer füllen eine Batterie, Fehltipp mischt neu ohne Batterieverlust.

Ebenfalls abgeleitet und nicht autoriert: der **Wort-Detektiv** direkt nach dem letzten
Wort-Bauer — „Finde den Buchstaben / die Silbe im Wort". Eine Runde pro eingeführtem Wort,
der Modus wechselt zwischen Buchstabe und Silbe, mit Rückfall auf Buchstabe, wenn die
Silbe nicht sauber benannt werden kann (z. B. wenn der autorierte Wort-Bauer-Block anders
geschrieben ist als sein Silben-Atom). Das Wort steht in farbige Segmente zerlegt da, jedes
antippbar; Treffer wandern auf Platzhalter-Striche im Antwortbereich, ein Fehltipp dreht das
Segment einmal um seinen Mittelpunkt und kostet nichts. Der Buchstaben-Modus zeigt das Ziel
als Formenpaar (`P / p`), damit „finde alle P" in „Papa" nicht schwerer ist als es aussieht;
Silben stehen nur klein. Details und Beispiele:
[Wort-Detektiv-Design](superpowers/specs/2026-07-31-wort-detektiv-design.md).

Reihenfolge-Regeln, die Content und Validator erzwingen:

- Der Wort-Bauer zeigt nie ein Graphem oder eine Silbe, die noch nicht eingeführt wurde.
- Satzrunden nutzen nur gebaute Wörter, kleingeschriebene Funktionswörter oder ausdrücklich als `holisticAtomIds` markierte Ganzwort-Bilder (so führt die Fibel z. B. „Tor" vor dem R ein).
- Gemeisterte Lektionen bleiben zum Wiederholen antippbar.



## 4. Content-Graph

- Atome (Buchstabe / Silbe / Wort + Emoji) sind wiederverwendbar über alle sechs Trainer-Typen einer Lektion.
- Atom-Emojis werden auch außerhalb der Trainer verwendet: die Pfad-Schilder zeigen drei
  Emojis je Lektion, abgeleitet aus sound_position → word_build → count_add → sentence_order
  (deterministisch, über den Emoji-Glyph dedupliziert). `letter_trace.rewardEmoji` bleibt
  bewusst außen vor — er ist die Belohnung des Trainers und wird nicht vorweggenommen.
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

- **Pfad-Screen ist der Einstieg**: ein gepunkteter Trittspuren-Weg durch eine Taglandschaft
  (Himmelsverlauf, Sonne und Wolken, grüne Hügel mit Parallaxe — helles Warmer-Tag-Theme). Ein Wegweiser-Schild pro
  Lektion, Label = Graphem, darunter drei Emojis aus dem Bildwortschatz der Lektion.
  Der bereits zurückgelegte Teil des Weges ist wärmer gezeichnet als der Rest.
  Gesperrte Schilder zeigen ihre Emojis nur als Silhouette.
Gesperrte und noch nicht autorierte Schilder reagieren auf Tippen mit einem gesprochenen Hinweis —
niemals mit einem stummen No-Op.
- Mit der Eltern-Freigabe der Reihenfolge bleiben gesperrte Schilder abgedunkelt und behalten ihre
  Silhouetten, verlieren aber Schloss und „später“-Hinweis und sind antippbar. Noch nicht autorierte
  Lektionen bleiben in jedem Fall gesperrt — sie haben keinen Inhalt.
- Tippen auf ein freigeschaltetes Schild startet die Trainer-Session dieser Lektion — die
  sechs autorierten Typen (Abschnitt 3), ergänzt um etwaige abgeleitete Zusatz-Trainer
  (Jagd, Wort-Detektiv).
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

- Parent-Gate (langer Druck auf ⋯) öffnet das Sheet „Eltern“ mit genau zwei Einstellungen:
  Abschnitt „Hilfestufe“ (**Auto / Mit Hilfe / Ohne Hilfe**) und die Freigabe
  „Reihenfolge frei wählbar“, die die Fortschrittssperre des Pfades aufhebt.
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

### TTS-Grenzen und Autorierungs-Konventionen

- **„Buchstabe" nur für echte Einzelbuchstaben.** Mehrzeichen-Grapheme (`Sch`, `Sp`, `St`, `Ch`,
  `Au`, `Ei`, `Eu`, `Pf`, `Qu`, `ck`, `ks` …) sind kein „Buchstabe" — das Wort ist fachlich falsch
  und für Vorschulkinder irreführend. Prompts, die einen solchen Mehrzeichen-Laut ansprechen,
  heißen „…den Laut …" statt „…den Buchstaben …" (Auditiver Finder, Spurensucher,
  Buchstaben-/Silben-Jagd). Umlaute (`Ä`, `Ö`, `Ü`) und `ß` bleiben „Buchstabe" — sie sind je ein
  einzelnes Zeichen. Silben (`kind: syllable`, z. B. `ma`, `sp`, `st` als verschmolzenes Ergebnis
  im Silben-Verschmelzer) heißen weiterhin „Silbe", nie „Laut" oder „Buchstabe".
- **Einzelbuchstaben-Betonung:** Steht ein einzelner Buchstabe/Graphem als eigenständiges Wort in einem
  TTS-String (z. B. „der Buchstabe M", „Hörst du das M …", „M wie Mond"), wird er mit Gedankenstrichen
  isoliert: `- M` wenn danach nur noch Satzzeichen folgt, `M -` wenn er einen Satz eröffnet, `- M -`
  wenn er mittendrin steht (z. B. „Wo hörst du den Buchstaben - M? Am Anfang, in der Mitte oder am
  Ende.", „Zeichne den Buchstaben - M - nach …", „M - wie Mond."). Grund: ohne die kurze Pause
  verschluckt die System-TTS den Buchstabennamen im Wortfluss oder betont ihn falsch. Gilt für alle
  Trainer-`promptTts`/`rewardTts`, die einen einzelnen Buchstaben ansprechen (Auditiver Finder,
  Spurensucher, Buchstaben-Jagd) — nicht für Silben-Verschmelzer (dort werden Laute bewusst
  aneinandergezogen) oder für Ganzwörter.
- **Letzte Frage ohne Fragezeichen:** Endet ein `promptTts`/`missTts` mit einer Frage, verliert nur die
  **letzte** Frage in diesem String ihr Fragezeichen (z. B. „Wo hörst du den Buchstaben - M? Am Anfang,
  in der Mitte oder am Ende." — die erste Frage behält ihr „?", die zweite/letzte nicht). Grund: ein
  Fragezeichen am Stringende lässt die System-TTS die Stimme am letzten Wort hochziehen, was bei
  Vorschulkindern falsch/unruhig klingt; ohne „?" klingt der Satz natürlich aus.
- **Auditiver Finder liest ganze Wörter, nicht buchstabiert.** Das `missTts` einer verpassten Runde
  nennt das Wort am Stück („Ameise.", nicht „A - M - eise."). Eine buchstabierte/segmentierte
  Wiederholung wird von der System-TTS Buchstabe für Buchstabe vorgelesen und ist für Vorschulkinder
  unverständlich.
- **`Sch`, `sp`, `st` sind bekannte, aber akzeptierte TTS-Lücken.** Die System-TTS spricht diese
  Zischlaut-Cluster nicht korrekt (kein sauberes „Sch"-/„Schp"-/„Scht"-Phonem, eher buchstabiert oder
  verschluckt) und lässt sich dafür auch nicht zuverlässig durch Schreibweisen-Tricks korrigieren.
  Didaktisch sind sie trotzdem nicht aus dem Lehrplan streichbar (feste Laut-Buchstaben-Gruppen der
  Fibel-Reihenfolge, Abschnitt 3). Das ist eine bekannte, akzeptierte Einschränkung der Sprachausgabe —
  kein offener Bug.
- **Kein verwaistes `br`-Silben-Atom.** Ein `br`-Atom in `atoms.json` wurde entfernt: die System-TTS
  sprach es als getrennte Buchstaben „b" + „r" statt als verschmolzenen Laut, ohne dass sich das per
  Text korrigieren ließ, und es war ohnehin von keiner Aufgabe referenziert (kein `syllable_merge`,
  `word_build` o. ä. nutzte die ID). Ersatzlos entfernt statt „repariert".

## 8. Mathematik-Visuals

- Mengen bis 10 als Bilder/Emojis, sinnvoll gruppiert (Subitizing: Paare + Rest, z. B. 5 = 2+2+1). Ab 11 steht ein einzelnes Bildsymbol mit der Zahl für die Menge.
- Zahl unter der Bildgruppe anzeigen.
- Aufgabe oben, Antwortwahl unten; Bilder in der Aufgabe ausreichend groß.
- Visuelle Mengenaufgaben: genau **3** Antwortoptionen; gleiche Dimensionen der buttons. Situationen bleiben gesprochen, konkret und kindernah.
- **Progression (bewusst steil):** Zahlenraum 10 schon in Lektion 1, Wegnehmen ab Lektion 2, Zahlenraum 20 ab Lektion 3, Malnehmen ab Lektion 6, Zahlenraum **30** ab Lektion 9. Der Validator deckelt Operanden und Ergebnis bei 30 (`MaxMathQuantity`). Schwierigkeitsbänder: easy ≤5, medium ≤10, hard ≤20, expert ≤30.
- **Multiplikations-Matrix:** „4 mal 5" wird als Matrix gezeichnet — `left` Reihen × `right` Spalten. Nur die **erste Reihe** zeigt die echten Objekte („je 5"), alle weiteren Reihen zeigen geisterhafte Platzhalter (gleiches Emoji, stark transparent). So lernen Kinder Multiplikation als zweidimensionale Fläche, nicht als Additionskette. Prompts sprechen die Struktur mit („Vier Reihen mit je fünf …"). Grid-Deckel für Lesbarkeit: max. 5 Reihen × 6 Spalten (validator-geprüft, `MultiplicationMatrix`).
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
- Ausnahme Wort-Detektiv: der Antwortbereich trägt **Quittungs-Striche statt Wahloptionen**.
  Sie sind bloße Grundstriche ohne Rahmen und ohne Tray — die einzige Symbolquelle ist das
  Wort im Aufgabenblock. Damit sind sie von den Schablonen des Wort-Bauers unterscheidbar.



## 10. Design-System

- Gemeinsame Komponenten unter `ui/components/` (`AbcContinueButton`, `AbcSpeakerButton`, `AbcNavChevron`, `AbcProgressBar`, Vektor-Icons inkl. `IconStar`).
- Buttons: keine Emojis — nur ASCII oder Canvas/SVG-Vektoren. Punkte-/Erfolgs-Symbol ist der Vektor-Stern `IconStar`, kein Text-Asterisk.
- Übungen nutzen `ExerciseStage` für klare Trennung Aufgabenblock / Antwortblock.
- Farbrollen (verbindlich): `StarGold` = Sterne/Punkte/Belohnung (`StarGoldDeep` als Kontur-/
  Tiefton für den Stern-Glyph auf hellem Grund), `LeafGreen` = richtig/erledigt, `SkyBlue` =
  Fortschritt/aktiv, `SunCoral` = Handlungs-CTA, `ClayRed` = Fehlertext (Erwachsene).
  `LeafGreenLight`/`SkyBlueLight` sind helle Ring-Varianten ausschließlich für Akzente AUF
  dunklen Flächen (Holzschilder auf dem Pfad) — nie als Fläche oder Akzent auf Cream.
  Eine Bedeutung pro Farbe — Sterne und Progress greifen nie auf `primary` zu.
- Haptik-Vokabular `AbcHaptics` (tick/success/celebrate/nudge): tick = kleiner Sammel-Erfolg
  (Trace-Stern, Jagd-Treffer, Einrasten), success = Aufgabe richtig, celebrate = Lektions-/
  Batterie-Feier, nudge = sanfte Korrektur. Haptik ergänzt Ton, ersetzt ihn nie.
- Erfolgsmomente: SuccessBurst (Gold-Stern + Funken), Gold-Puls der Progress-Bar je Trainer,
  Konfetti auf dem End-Screen.



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
Random, kein Shuffle — nach demselben Muster wie `content/LessonEmojis.kt`, das die
Emojis der Pfad-Schilder genauso deterministisch aus dem Lektions-Vokabular ableitet und
ebenfalls auf dem Glyph statt der Atom-ID dedupliziert.

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
| Nutzt ein Stern/Progress `primary` statt der Farbrolle?             | Nein                                  |
| Fehlerfeedback für Vorschulkinder?                                  | Audio, kein Lesesatz                 |
| Buttons mit Emoji?                                                  | Nein → Vektor/ASCII                  |
| Zeigt der Wort-Bauer ein noch nicht eingeführtes Graphem?           | Nein → Fibel-Reihenfolge             |
| Enthält der Rechen-Trainer Lesewörter?                              | Nein → nur Icons und Ziffern         |
| Hält jede autorierte Lektion die sechs Trainer-Typen in nicht-fallender Rangfolge (Start Auditiver Finder, Ende Rechnen)? | Ja → Validator prüft das             |
| Ist ein neuer Finale-Satz länger als 7 Wörter oder eine Mini-Geschichte?     | Nein → Abschnitt 12, Validator prüft |
| Wäre das Bild des Finale-Satzes in einem Kinderbuch denkbar?                 | Ja — sonst AI-Slop                   |
| Zeigt der End-Screen eine Punktezahl?                                        | Nein → Punkte leben im Chrome/Pfad   |
| Zeigt ein abgeleiteter Trainer ein Graphem, das die Lektion noch nicht kennt?  | Nein → Graphem-Tabelle ist lektionsbeschränkt |
| Verlangt der Wort-Detektiv einen Tipp auf eine Form, die er nicht zeigt?       | Buchstaben nein → Paar `P / p`; Silben zeigen nur die Kleinform, der Treffer darf die Großform sein |


Siehe auch `[AGENTS.md](../AGENTS.md)` für den Arbeitsprozess und Dokumentationspflichten.
