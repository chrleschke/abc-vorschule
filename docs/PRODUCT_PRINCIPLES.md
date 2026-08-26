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

Der Lehrplan besteht aus 18 Lektionen in fünf Phasen (Fibel-Reihenfolge). Jede Lektion führt die sieben Trainer-**Typen** unten in fester Rangfolge durch — ein Typ kann sich wiederholen oder ganz fehlen (z. B. keine Satzrunde in einer Lektion), die Reihenfolge geht aber nie zurück; jede Lektion beginnt mit dem Auditiven Finder und endet mit Rechnen:

1. **Auditiver Finder** — Laut im gesprochenen Wort verorten (Lok mit Anfang/Mitte/Ende-Waggon). Der gesuchte Laut steht groß über dem Zug; darunter zeigt das Bildwort seine Graphemgruppen in den drei Waggonfarben.
   **Derzeit pausiert** (`PausedTrainerKinds` in `TaskSpecs.kt`): das Anfang/Mitte/Ende-Konzept
   überforderte junge Tester, und das Scoring ist bei Wörtern mit wiederholtem Phonem mehrdeutig
   („Erdbeere"). Content und Code bleiben unangetastet im Repo; eine Session überspringt den Typ
   einfach und beginnt zur Laufzeit mit dem Spurensucher. Die Rangfolge unten beschreibt weiterhin
   den autorierten Content.
2. **Visueller Spurensucher** — Graphem nachzeichnen („Zeichne das große T nach …"), gelbe Sterne in
  Strichreihenfolge sammeln. Nur der aktive Balken zeigt leuchtende Sterne, kommende Balken blass;
  eingesammelte Sterne verschwinden. Der aktive Balken liegt zuoberst und alle Sterne liegen über
  allen Balken — kein späterer Balken darf den aktuellen Pfad oder seine Sterne verdecken; ist ein
  Balken fertig, rückt der nächste nach oben. Fertige Balken füllen sich ease-in ein, das Fahrzeug springt an
  den Startpunkt des nächsten Balkens **und bleibt dort stehen, bis das Kind den Balken wirklich dort
  beginnt**: ein Finger, der weiter auf dem Balken liegt als der nächste Stern (plus Sammelradius),
  zählt nicht und zieht das Fahrzeug nicht mit — er ist aber nicht „neben der Straße", also gibt es
  dafür keinen Rüttel-Impuls. Sonst rutscht die Ziehbewegung z. B. nach dem E von „Ei" direkt unten
  in die i-Straße und der Startpunkt sitzt am Fuß des i statt an seinem Kopf. Nach dem letzten
  Stern hält der fertige Buchstabe eine halbe
  Sekunde, dann folgt die Belohnungsseite: Bild groß, darunter die Wortzeile („**T** wie Tomate")
  mit fettem Graphem. Kein zusätzlicher Buchstaben-Text unter dem Pfad.
  Umlaut-Pünktchen und andere kurze Diakritika (Strichlänge < ~12 % der Glyphenbox) werden
  dünner gezeichnet als die Hauptbalken — sonst machen die runden Straßenkappen aus einem
  kurzen Tick einen dicken Blob, der den Buchstabenkörper frisst. Sterne werden entlang der
  Mittellinie eingesammelt (Korridor reicht seitlich); ein schneller Wisch, der zwischen zwei
  Pointer-Samples über einen Stern springt, zählt trotzdem.
3. **Silben-Verschmelzer** — beide Laut-Kacheln sind schiebbar und wandern symmetrisch
  aufeinander zu (Magnet-Metapher); eine gepunktete Schiebespur mit einwärts laufender
  Lichtwelle und ein Idle-„Atmen" laden ohne Text zum Schieben ein. Ab 60 % Nähe schnappen
  die Kacheln zusammen, darunter gleiten sie straflos zurück. Ein Tipp auf eine Kachel
  liest ihren Laut vor **und** stupst sie einen Schritt (30 %) näher — zwei Tipps
  verschmelzen, es gibt keinen separaten Bestätigungs-Button.
4. **Wort-Bauer** — Silben-/Buchstabenklötze in Schablonen unter dem Bild.
5. **Satz-Architekt** — Wortschilder an die Wäscheleine; Einwort-Runden sind Wort-Bild-Zuordnung.
6. **Satz-Versteher** — „Ordne das richtige Bild zu": ein Satz mit bewusst
  schwieriger Grammatik (Plural, Partizip II, Präteritum — auch kombiniert) wird
  vorgelesen, das Kind tippt eine von zwei Bildkarten (Emoji-Reihen, 1–3 Bilder;
  Wiederholung desselben Bildes drückt Menge aus). Die Instruktion kommt **einmal**
  vor Runde 1, danach trägt jeder Satz die Aufgabe allein. Tippen ist die Antwort;
  ein Miss liest den Satz erneut vor, nach 2 Misses gibt es „Zeig mir". Die Sätze
  leben im Task selbst (nicht in `sentences.json`) und dürfen wie die Finale-Sätze
  flektierte Formen und freie Verben nutzen — nur die Karten-Nomen sind Atome mit
  Emoji. Die Instruktion ist über alle Lektionen **wortgleich** (Validator prüft das),
  damit sie nur eine einzige Aufnahme braucht. Redaktionsregeln:
  - 4–8 Wörter, ein Hauptsatz, Wörter der Lektion, Cartoon-Logik (realistischer als
    die Finale-Sätze).
  - **Die falsche Karte tauscht eine Kategorie, nicht bloß die Anzahl.** Anderes Tier,
    anderes Kleidungsstück, anderer Akteur, anderes Objekt, anderer Ort. Zwei Äpfel
    gegen einen Apfel ist zu wenig Unterschied — das Kind rät die Menge, statt den
    Satz zu verstehen. Auch die Beispielobjekte und Tätigkeiten selbst wechseln, statt
    dieselbe Handlung durch die Lektionen zu tragen.
  - **Ein Plural im Satz braucht nicht zwingend zwei Bilder.** „Die Wolken zogen über
    das Haus" ist die sprachlich wertvollere Form und darf auf einer Karte mit einer
    Wolke stehen — bei unscharfen Mengen (Wolken, Sand, Sterne) zählt niemand nach.
    Die Doppelung ist ein Mittel, keine Pflicht.
  - **Zeitform folgt dem Bild, nicht dem Lernziel.** Beschreibt der Satz einen Zustand,
    der auf der Karte zu sehen ist, steht er im Präsens („Zwei Eulen sitzen auf dem
    Baum"). Partizip II und Präteritum kommen dort, wo sie ohnehin natürlich klingen
    („Der Bär hat im Bett geschlafen", „Das Pferd sprang über das Tor") — nie erzwungen.
    „Zwei Vögel saßen auf dem Baum" ist der Fehlerfall: die Vergangenheitsform sagt, dass
    sie weg sind, das Bild zeigt sie aber.
  - **Kartenoptik und Feedback.** Die Bildkarten sind Rahmen ohne Füllfläche (der
    graue `CreamElevated`-Grund verdunkelte die Emojis, ohne die Kartengrenze
    sichtbarer zu machen), stehen mit ihrer Oberkante knapp unterhalb der
    Bildschirmmitte, und die Emoji-Reihe füllt die Karte so weit die Breite es
    zulässt. Ein Fehltipp wird **bewegt** quittiert, nicht gefärbt: die getippte
    Karte wackelt (`SentencePictureCardShake`), dazu `nudge`-Haptik und der Satz
    erneut — kein Rot, §8 und §10 gelten unverändert. Ein Treffer zieht die
    richtige Karte groß in die Bildschirmmitte und hält sie dort, solange der Satz
    wiederholt wird; die andere Karte blendet aus. **Auflösen („Zeig mir")
    markiert nur, es feiert nicht.**
7. **Rechnen** — reine Mengen-Arithmetik in *jeder* Lektion, Icons aus dem Wortschatz
  derselben Lektion. **Keine Wörter zum Lesen oder Schreiben**; Singular/Plural nur gesprochen.

Zusätzlich, bis zu zweimal pro Lektion und ohne eigenen autorierten Content: eine **Buchstaben-Jagd** direkt nach dem Spurensucher und eine **Silben-Jagd** direkt nach dem Silben-Verschmelzer — jeweils nur, wenn die Lektion den entsprechenden Trainer führt und mindestens ein bereits bekanntes Vergleichssymbol existiert. Kind tippt alle Vorkommen des gesuchten Symbols in einem verstreuten Feld an; Treffer füllen eine Batterie, Fehltipp mischt neu ohne Batterieverlust.

Ebenfalls abgeleitet und nicht autoriert: der **Wort-Detektiv** direkt nach dem letzten
Wort-Bauer — „Finde den Buchstaben / den Laut / die Silbe im Wort". Eine Runde pro eingeführtem Wort,
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
- Satzrunden (Satz-Architekt) nutzen nur gebaute Wörter, kleingeschriebene Funktionswörter oder ausdrücklich als `holisticAtomIds` markierte Ganzwort-Bilder (so führt die Fibel z. B. „Tor" vor dem R ein). Der **Satz-Versteher ist davon ausgenommen** — wie die Finale-Sätze lebt er von grammatischer Freiheit (Hör-Trainer, keine Bauen-Anforderung).
- Gemeisterte Lektionen bleiben zum Wiederholen antippbar.



## 4. Content-Graph

- Atome (Buchstabe / Silbe / Wort + Emoji) sind wiederverwendbar über alle sieben Trainer-Typen einer Lektion.
- Atom-Emojis werden auch außerhalb der Trainer verwendet: die Pfad-Schilder zeigen drei
  Emojis je Lektion, abgeleitet aus sound_position → word_build → count_add → sentence_order
  (deterministisch, über den Emoji-Glyph dedupliziert). `letter_trace.rewardEmoji` bleibt
  bewusst außen vor — er ist die Belohnung des Trainers und wird nicht vorweggenommen.
- Tasks referenzieren Atom-IDs; Validierung verhindert tote Referenzen.
- **Glyphen-Strichenden treffen sich exakt oder deutlich nicht.** Ein Querstrich, der den Stamm
  kappt, teilt dessen y-Wert (E/F/Ei/Eu/Pf: obere Balken auf `0.08` wie der Stamm, untere auf
  `0.92`); ein Beinahe-Treffer von 0.02 liest sich als Wackler in der Buchstabenform. Getestet in
  `GlyphLetterformTest` gegen das Pack, nicht gegen abgeschriebene Zahlen.
- **Runde Bögen (U/Ü/O/…)** brauchen genug Stützpunkte — grobe Polygone lesen sich eckig, weil
  die Straße mit `lineTo` gezeichnet wird. Umlaut-Punkte sind kurze senkrechte Ticks oben mit
  Abstand zum Körper; die Zeichenbreite für kurze Striche skaliert die App herunter
  (`TraceProgress.ShortStrokeWidthScale`). Grobe Bögen (B/C/D/G/P/R/S/…) werden zur Laufzeit
  in `TraceGeometry.refineStroke` selektiv mit Kreisbögen verdichtet — spitze pädagogische
  Ecken (M/W/N) und lange Gerade (J-Stamm, U-Beine) bleiben unangetastet.
- **Mehrzeichen-Grapheme (Au, Ei, Sch, …)** teilen dieselbe 1×1-Autorenbox, wirken dort aber
  gestreckt und zu dick. Zur Laufzeit setzt `TraceProgress.fitFor(lemma)` sie kompakter:
  vertikal zur Mitte gestaucht (Di-/Trigraph-Höhenfaktor) und mit dünnerem Straßenkorridor.
  Einzelbuchstaben (inkl. Ä/Ö/Ü) bleiben unverändert; die Breite kommt aus dem Lemma
  (Leerzeichen ignoriert, damit `S t` als Digraph zählt).
- Orthografie: Silben eher klein; zusammengesetzte Wörter/Sätze korrekt großgeschrieben.
- **Genus und Nomenklasse am Atom.** Jedes Substantiv-Atom trägt `gender` (`m`/`f`/`n`) und
  `nounClass` (`thing` / `person` / `name`); `articleSpeechOverride` überschreibt den
  abgeleiteten Sprechtext, wo die Regel nicht greift (Plural-Atome wie `Häuser` nehmen „die",
  unabhängig vom Genus des Singulars). Nicht-Substantive — Funktionswörter, Verben,
  Adjektive, Buchstaben, Silben — tragen die Felder nicht. `ContentValidator` erzwingt, dass
  jedes vom Erfolgs-Vorsprechen erreichbare Atom klassifiziert ist oder ausdrücklich in
  `ArticleFreeSpeechAtomIds` steht.
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
- **„Du bist hier“-Marker**: über dem Schild der aktuellen Lektion steht eine Pin-Nadel
  (`SunCoral` mit Cream-Kontur), die sanft auf und ab wippt — das Kind soll auf einem Screen
  voller Wegweiser ohne Text erkennen, welches Schild dran ist. Der Pfad scrollt beim Öffnen
  automatisch zu diesem Schild; wer selbst weiterscrollt, wird nicht zurückgerissen.
- **Nach dem Abschluss animiert der Fortschritt**: der Marker hüpft in einem Bogen vom gerade
  geschafften Schild zum nächsten, und die Trittspuren dazwischen werden dabei warm. Woher das
  Kind kam und was jetzt dran ist, wird also gezeigt statt geschrieben. Der Sprung läuft genau
  einmal pro Rückkehr auf den Pfad.
- Mit der Eltern-Freigabe der Reihenfolge bleiben **noch nicht erreichte** gesperrte Schilder
  abgedunkelt und behalten ihre Silhouetten, verlieren aber Schloss und „später“-Hinweis und sind
  antippbar. Noch nicht autorierte Lektionen bleiben in jedem Fall gesperrt — sie haben keinen Inhalt.
- **Eigener Fortschritt schlägt die Reihenfolgesperre**: was das Kind in einer Lektion getan hat,
  ist die stärkere Aussage. Eine frei gespielte Lektion zeigt danach ihren echten Zustand
  (angefangen bzw. geschafft mit Stern), bleibt antippbar und schaltet die folgende Lektion frei —
  auch wenn die Eltern-Freigabe später wieder ausgeht. „Gesperrt“ heißt damit: in der
  Fibel-Reihenfolge noch nicht erreicht **und** hier noch nichts getan. Der Marker folgt trotzdem
  weiter der Fibel-Reihenfolge (erste nicht gemeisterte Lektion) und springt nicht zum Ausflug
  voraus.
- Tippen auf ein freigeschaltetes Schild startet die Trainer-Session dieser Lektion — die
  sieben autorierten Typen (Abschnitt 3), ergänzt um etwaige abgeleitete Zusatz-Trainer
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

- Parent-Gate (langer Druck auf ⋯) öffnet das Sheet „Eltern“. Der ⋯ ist ein **schwebender
  runder Knopf oben rechts auf dem Pfad-Screen**, kein Bar-Element — und in der Lektion gibt es
  ihn nicht: dort führt der Weg zu den Einstellungen über das Verlassen der Lektion. Das Sheet
  bietet die Hilfestufe (**Auto / Mit Hilfe / Ohne Hilfe**) und die Freigabe „Reihenfolge frei
  wählbar“, die die Fortschrittssperre des Pfades aufhebt. In Debug-Builds zusätzlich
  „TTS Debug“ am Ende des Sheets (Entwickler-Werkzeug, nicht für Release).
- Auto passt Gerüste sanft an; erzwungene Stufen frieren Auto-Streaks ein.
- Gerüste pro Atom/Slot (Silhouette vs. Lücke), nicht global starr über die ganze Aufgabe.

## 7. Sprache & Audio

- App und Inhalte deutsch. Sprache ist bei jedem TTS Aufruf eindeutet angegeben. 
- System-TTS für Prompts; Speaker (Vektor-Icon) **im Aufgabenbereich**, mittig über dem Aufgabentitel.
- Tippen auf Aufgaben-Items (Buchstaben, Silben, Wörter, Antwortkacheln) liest sie vor.
- Bei Erfolg: Antwort vorsprechen → Stern im oberen Drittel → erst danach nächste Aufgabe (Audio abwarten).
- **Die Antwort nennt den Artikel, die Aufgabe nicht.** Ist das Lösungswort ein Substantiv,
  spricht das Erfolgs-Vorsprechen es mit Artikel („Baue das Wort Haus" → „das Haus") —
  Gegenstände und Tiere mit dem bestimmten (der/die/das), Personenbezeichnungen mit dem
  unbestimmten (ein/eine), Namen ohne. Neutrum-Personen bekommen „das": „ein Opa" und
  „ein Kind" wären sonst nicht unterscheidbar. Betroffen sind Wort-Bauer, Wort-Detektiv und
  Auditiver Finder (`SuccessSpeech`). **Nicht** betroffen: Prompts, das Antippen von Items,
  `missTts`, Rechnen („zwei Ameisen" — vor einer Zahl steht kein Artikel) und ganze Sätze,
  die ihre Artikel schon tragen. Abgeleitet wird in `AtomArticleSpeech`; `tools/tts` spiegelt
  die Regel, damit vorproduzierte Clips denselben Text tragen.
- Lob (**nur Rechnen, nur gesprochen**): ein zufälliges Wort oder ein kurzer Ausruf aus
  `PraisePhrases` steht vor der Antwort („Ausgezeichnet! zwei Ameisen"), damit die Menge das Letzte
  bleibt, was das Kind hört. Nie als Text anzeigen — das Kind kann nicht lesen. Auflösen
  („Zeig mir") bekommt kein Lob. Jeder Eintrag ist eine eigene Äußerung und bringt seine
  Satzzeichen selbst mit („Bäääm! Volltreffer!"); zwei Einträge dürfen sich nicht nur durch
  Satzzeichen oder Groß-/Kleinschreibung unterscheiden, sonst kuratiert und rendert die
  TTS-Pipeline denselben Clip zweimal.
- Wenn kein deutsches TTS: visuelle Fallbacks, Aufgabe bleibt spielbar.
- Wort-Bauer (Trainer 4): Prompt „Baue das Wort ….“ (ohne Tray-Instruktion); Silben-/Buchstabenklötze in Schablonen tragen die Aufgabe, keine zusätzliche Lese-Titelzeile.
- Satz-Architekt (Trainer 5): Mehrwort-Prompt = Satztext ohne „Ordne die Wörter…“; Einwort-Bild-Zuordnung behält „Ordne das Wort … dem Bild zu.“
- Feedback bei Fehlern (besonders Rechnen): **vorsprechen**, nicht als Fehler-Satz anzeigen.

### TTS-Grenzen und Autorierungs-Konventionen

- **„Buchstabe" nur für echte Einzelbuchstaben.** Mehrzeichen-Grapheme (`Sch`, `Sp`, `St`, `Ch`,
  `Au`, `Ei`, `Eu`, `Pf`, `Qu`, `ck`, `ks` …) sind kein „Buchstabe" — das Wort ist fachlich falsch
  und für Vorschulkinder irreführend. Prompts, die einen solchen Mehrzeichen-Laut ansprechen,
  heißen „…den Laut …" statt „…den Buchstaben …" (Auditiver Finder, Spurensucher,
  Buchstaben-/Silben-Jagd, Wort-Detektiv). Umlaute (`Ä`, `Ö`, `Ü`) und `ß` bleiben „Buchstabe" — sie sind je ein
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
- **Der Feldname wählt das Synthese-Profil.** `tools/tts` leitet aus dem JSON-Feldnamen
  ab, wie ein Text gesprochen wird (`ttskit/extract.py`, `profiles.json`). `promptTts`
  ist die „Aufgaben-Frage" *mit fragender Betonung am Satzende* — richtig für „Baue das
  Wort …", falsch für jeden Aussagesatz. Deshalb heißt der Rundentext des Satz-Verstehers
  im JSON `sentenceTts` (Profil „Einfacher Satz", natürliche Satzmelodie), während seine
  Aufgabenansage `instructionTts` bleibt und als `prompt` läuft. Wer künftig einen
  Trainer autoriert, dessen Rundentext eine Aussage ist, benennt das Feld genauso —
  ein `@SerialName` hält die Kotlin-Seite bei `promptTts`, damit `TrainerRound` einheitlich bleibt.
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
  - **Über** der Matrix steht die Rechenaufgabe als Ziffern-Zeile („3 × 4", `headlineMedium`), **links vor jeder Reihe** ihre Zeilennummer (1…n, `labelLarge`, `WarmMuted`, feste Gutter-Breite). Ziffern darf das Kind sehen — sie sind selbst Lerninhalt und stehen ohnehin unter jeder Mengengruppe. Die Zeilennummern behalten volle Deckkraft, auch neben Geisterreihen: sie sind die Zählhilfe, kein Teil des Platzhalters.
  - Weil die Matrix ihre Aufgabe selbst beschriftet, unterdrückt die Zahlen-Eingabe ihre symbolische Zeile („5 × 6 = ?") bei Multiplikation — sonst stünde dieselbe Aufgabe zweimal im Aufgabenblock (§9).
- **Plausibilität der Malaufgaben (Content-Regel):** Ein Malnehmen-Objekt muss ein **einzeln zählbares Ding** sein, das in echten, gleich großen Reihen vorkommt — Eier im Karton, Dosen im Regal, Fenster am Haus, Taxis am Flughafen, Omas im Chor. Nicht erlaubt: Abstrakta ohne Stückzahl („vier Reihen mit je vier **Wegen**"), abgetrennte Körperteile („achtzehn **Füße**"), Dinge, die es nie in Reihen gibt („neun **Tore**"), und Massen von angstbesetzten Tieren („dreißig **Spinnen**"). Prüffrage wie bei den Finale-Bildern: Würde dieses Bild in einem Kinderbuch stehen? Der gesprochene Prompt nennt zusätzlich den **Ort** der Reihen („… stehen im Regal") — er macht die Anordnung erst glaubhaft. Pro Lektion trägt Rechnen **ein** Icon (Test `LessonCoverageTest`), also gilt die Wahl auch für die Plus-/Minus-Runden derselben Lektion. Gleiches Icon **und** gleiches Raster darf sich über den Lehrplan nicht wortgleich wiederholen.
- Korrekte Antwort bestätigt sich **grün** (Kachel bzw. Zahlenfeld), solange sie vorgesprochen wird.
  Falsche Antwort wird **nicht** rot markiert — Miss bleibt gesprochenes Feedback. Auflösen ist nicht grün.
- **Eingabeart:** Zahlen-Eingabe bei fortgeschrittenem Scaffold **oder** sobald das Ergebnis über 10 liegt (Band `hard`/`expert`) — außer die Eltern haben ausdrücklich „Mit Hilfe“ (`ParentMode.Beginner`) gewählt, dann bleiben überall die drei Kacheln. Die Regel prüft den Eltern-Modus, nicht das abgeleitete Scaffold: im Default `Auto` startet ein frisches Kind auf `Beginner`, gegen das Scaffold geprüft liefe sie beim Normalnutzer ins Leere. Grund: drei Kacheln mit Nachbar-Distraktoren machen Raten zur billigsten Strategie. Regel in `MathHinting.inputFor`.
- Das Antwortfeld nutzt die **System-Tastatur im Zahlenmodus** (kein Custom-Nummernblock) plus einen CTA-Absenden-Button mit Pfeil-Icon.
- **Zähl-Hilfe (nur Tipp-Modus):** nach 2 Fehlversuchen wird der Aufgabenbereich antippbar und **ersetzt** die Aufgabenvisualisierung (§9: Aufgabe nie zweimal). Beide Operanden teilen sich **ein** Fünfer-Feld, darüber die Aufgabe als Ziffernzeile (»15 − 6 = ?«). Der **Rahmen um die letzten `right` Objekte** markiert überall die zweite Zahl; was mit ihr passiert, entscheidet die Rechenart — bei Minus geht sie weg und **nur sie ist antippbar** (Zähler läuft rückwärts), bei Plus kommt sie dazu und alles ist antippbar. **Malnehmen zählt reihenweise**: ein Tipp macht eine ganze Geisterreihe echt und der Zähler springt in Schritten (5, 10, 15, 20) — Objekte einzeln anzutippen wäre Zählen in Einerschritten, also gerade nicht Multiplikation. Die Matrix ist dabei größer als im Prompt, weil sie den Aufgabenblock für sich hat. Die **nächste offene Einheit pulsiert** und führt durch die Aufgabe (bei Minus von hinten, weil rückwärts gezählt wird); erledigte gerahmte Zellen bekommen einen helleren Rahmen. Jeder Tipp spricht die erreichte Zahl mit, auf dem eigenen Kanal `SpeechChannel.Counting`, der eine laufende Ansage überlagern darf statt sie abzuwürgen. **Gesprochene Zahlen stehen als Wort, nie als Ziffer mit Punkt** — „8." ist im Deutschen die Ordinalzahl und wird „achte" gelesen (`GermanNumberWord`); wo ein Satzende nötig ist, trennt ein Komma — der Puls ist die Anleitung, der gesprochene Cue nur die Verstärkung, weil eine Ansage ohne Stimme oder Clip nicht ankommt. Der Zähler wird ab dem ersten Tipp ins Antwortfeld gespiegelt; die System-Tastatur klappt ein, solange die Hilfe offen ist. **In der Zähl-Hilfe werden Mengen ab 11 ausgeschrieben** (Fünferzeilen) — die einzige Ausnahme zur Symbol-ab-11-Regel oben, und sie gilt nie im Aufgaben-Prompt. Der „Auflösen“-Knopf erscheint im Tipp-Modus erst nach 4 Fehlversuchen (Kachel-Modus unverändert 2). Der Fehlversuch, der die Hilfe aufklappt, spricht **nur die Zählanweisung** — „probier es noch mal“ wäre dort die falsche Auskunft. Und eine mit der Zähl-Hilfe erreichte Antwort wird **bestätigt, aber nicht gelobt** (wie beim Auflösen); Punkte gibt es weiterhin, denn ein Punktabzug wäre eine Strafe.
- Rechnen läuft in **jeder** Lektion, mit den Bild-Ikonen derselben Lektion (kontextnah, aber  
nicht zwingend — Kinder erkennen die Icons ohnehin)



## 9. Layout-Grundform der Übungen

- **Chrome oben (nativ):** eine durchsichtige M3-Top-App-Bar. Links das Schließen-X, im Titel-Slot
  der elternseitige Lektionstitel und rechts Stern + Punkte. Kein Overflow-Menü in der Lektion.
  Darunter **eine** Zeile: Rückfall-Chevrons an den Rändern, dazwischen die Fortschrittskette.
  Der Speaker sitzt nicht im Chrome, sondern gemäß §7 im Aufgabenbereich.
- **Fortschritt ist eine Segmentkette** (`AbcSegmentedProgress`): ein Segment je Trainer, das
  laufende füllt sich nach Runden-Anteil. Sie ersetzt Balken, Textlabel „3/8" und Runden-Punkte —
  das Kind liest das Label ohnehin nicht.
- **Vor/Zurück ist ein Rückfallweg, kein Angebot**: die Chevrons haben kein Button-Gehäuse mehr,
  nur den gedämpften Glyph (Trefferfläche bleibt 48 dp). Vorwärts kommt das Kind durch Lösen.
- **Schutzbereiche sind durchsichtig:** kein globales `safeDrawing`-Padding auf der Wurzel —
  Hintergrund und Pfad-Landschaft laufen unter Status- und Nav-Bar durch, jedes Element
  konsumiert seinen Inset selbst. Die Unterkante des Aufgabenbereichs nutzt `safeDrawing`
  (nicht nur `navigationBars`), damit die System-Zahlentastatur den Block weiter hochschiebt.
- **Prompt/Aufgabe:** oberer Block, zentriert, mit Luft zu den Rändern (kein Kleben am Screenrand).
- **Antworten:** unterer Block, zentriert (Kacheln, Mengenwahl, Ziffernblock).
- Keine doppelte Aufgabe+Vorschau desselben Tokens.
- Ausnahme Buchstaben-/Silben-Jagd: Kacheln verstreuen sich über den gesamten Aufgabenbereich statt in einer geordneten Antwortliste; die Batterie bleibt im Antwortbereich unten.
- Ausnahme Wort-Detektiv: der Antwortbereich trägt **Quittungs-Striche statt Wahloptionen**.
  Sie sind bloße Grundstriche ohne Rahmen und ohne Tray — die einzige Symbolquelle ist das
  Wort im Aufgabenblock. Damit sind sie von den Schablonen des Wort-Bauers unterscheidbar.
- Ausnahme Satz-Versteher: der Antwortblock beginnt bei **52 % der Bühnenhöhe**
  statt am unteren Rand (`ExerciseStage(answerAnchor = AnswerAnchor.BelowCenter)`).
  Sein Aufgabenblock trägt nur den Speaker — kein Titel, keine Kacheln, kein Wort
  (**Ausnahme:** ohne deutsches TTS steht dort der Satz als Text, damit ein
  Erwachsener vorlesen kann — der visuelle Fallback aus §7; er darf nicht als
  Verstoß gegen „kein Wort" gelöscht werden) — und am unteren Rand verdeckt die
  tippende Hand genau die Bildkarten, die die ganze Aufgabe sind. Die 52 % sind
  eine **Untergrenze für den Antwortblock**, keine feste Höhe für den
  Aufgabenblock: der Antwortblock darf über die Marke hinaus nach oben wachsen,
  wenn Karten, „Zeig mir" und Systemschriftgröße mehr Platz brauchen. Für alle
  anderen Übungen bleibt `AnswerAnchor.Bottom` die Vorbelegung und damit die
  Grundform.



## 10. Design-System

- Gemeinsame Komponenten unter `ui/components/` (`AbcContinueButton`, `AbcSpeakerButton`, `AbcNavChevron`, `AbcSegmentedProgress`, Vektor-Icons inkl. `IconStar`) und `ui/shell/AbcTopBar`.
- Buttons: keine Emojis — nur ASCII oder Canvas/SVG-Vektoren. Punkte-/Erfolgs-Symbol ist der Vektor-Stern `IconStar`, kein Text-Asterisk.
- Übungen nutzen `ExerciseStage` für klare Trennung Aufgabenblock / Antwortblock.
  Der Parameter `answerAnchor` ist mit `Bottom` vorbelegt; `BelowCenter` ist die
  eine benannte Ausnahme (§9, Satz-Versteher).
- Farbrollen (verbindlich): `StarGold` = Sterne/Punkte/Belohnung (`StarGoldDeep` als Kontur-/
  Tiefton für den Stern-Glyph auf hellem Grund), `LeafGreen` = richtig/erledigt, `SkyBlue` =
  Fortschritt/aktiv, `SunCoral` = Handlungs-CTA (auch der „Du bist hier“-Marker auf dem Pfad —
  er sagt „hier tippen“), `ClayRed` = Fehlertext (Erwachsene).
  `LeafGreenLight`/`SkyBlueLight` sind helle Ring-Varianten ausschließlich für Akzente AUF
  dunklen Flächen (Holzschilder auf dem Pfad) — nie als Fläche oder Akzent auf Cream.
  Eine Bedeutung pro Farbe — Sterne und Progress greifen nie auf `primary` zu.
  Benannte Ausnahme: die **warm gelaufenen Trittspuren** des Pfades nutzen ein transparentes
  StarGold (§5 verlangt „wärmer", und ein kaltes SkyBlue widerspräche dem) — das ist eine
  Landschafts-Färbung, kein Präzedenzfall für „Gold = Fortschritt" im UI-Chrome.
- Haptik-Vokabular `AbcHaptics` (tick/success/celebrate/nudge): tick = kleiner Sammel-Erfolg
  (Trace-Stern, Jagd-Treffer, Einrasten), success = Aufgabe richtig, celebrate = Lektions-/
  Batterie-Feier, nudge = sanfte Korrektur. Haptik ergänzt Ton, ersetzt ihn nie.
- Erfolgsmomente: SuccessBurst (Gold-Stern + Funken), Gold-Puls an der Segmentgrenze je Trainer,
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
| Hält jede autorierte Lektion die sieben Trainer-Typen in nicht-fallender Rangfolge (Start Auditiver Finder, Ende Rechnen)? | Ja → Validator prüft das             |
| Zeigt der Satz-Versteher zwei ununterscheidbare Karten oder liest sich seine Instruktion in jedem Satz wieder? | Nein → Validator prüft beides |
| Unterscheiden sich die beiden Karten nur in der Anzahl desselben Bildes?      | Nein → Kategorie tauschen (Tier, Objekt, Akteur, Ort) |
| Steht ein Satz-Versteher-Satz im Präteritum, obwohl die Karte den Zustand zeigt? | Nein → Präsens; Vergangenheit nur wo sie natürlich klingt |
| Nutzen alle Satz-Versteher dieselbe Instruktion (eine Aufnahme)?              | Ja → Validator prüft das |
| Ist ein neuer Finale-Satz länger als 7 Wörter oder eine Mini-Geschichte?     | Nein → Abschnitt 12, Validator prüft |
| Wäre das Bild des Finale-Satzes in einem Kinderbuch denkbar?                 | Ja — sonst AI-Slop                   |
| Zeigt der End-Screen eine Punktezahl?                                        | Nein → Punkte leben im Chrome/Pfad   |
| Sieht eine geschaffte Lektion danach noch gesperrt aus (frei gewählte Reihenfolge)? | Nein → eigener Fortschritt schlägt die Sperre |
| Erkennt das Kind ohne Text, welches Schild jetzt dran ist?                   | Ja → wippender Marker + Auto-Scroll  |
| Zeigt ein abgeleiteter Trainer ein Graphem, das die Lektion noch nicht kennt?  | Nein → Graphem-Tabelle ist lektionsbeschränkt |
| Verlangt der Wort-Detektiv einen Tipp auf eine Form, die er nicht zeigt?       | Buchstaben nein → Paar `P / p`; Silben zeigen nur die Kleinform, der Treffer darf die Großform sein |


Siehe auch `[AGENTS.md](../AGENTS.md)` für den Arbeitsprozess und Dokumentationspflichten.
