# Silbo (ABC Vorschule) — Produktprinzipien

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
- Drag & Drop committet nur bei echtem Slot-Treffer (Hit-Testing); daneben oder in den falschen Slot losgelassene Kacheln fliegen ohne Strafe **federnd** an ihren Platz zurück (`DragCard`, Feder `AbcMotion.Soft`) — ein Teleport zurück liest sich wie ein Fehler der App, ein Rückflug wie „noch mal". Aufheben und Absetzen federn ebenfalls (Lift 1,08). Ein Fehlgriff gibt in jedem Trainer `nudge`-Haptik.
- Safe-Area: Inhalt unter Status-/Nav-Leisten und über Home-Indikator halten; unten extra Abstand.



## 3. Lernprogression (Fibel-Lernpfad)

Der Lehrplan besteht aus 34 Lektionen in acht Phasen (Fibel-Reihenfolge): 18 Basis-Lektionen führen die 40 Grapheme ein (Phase 1–5), 8 Wiederholungen vertiefen sie (Phase 6–7), und 8 Lektionen der **Phase 8** bringen keine neuen Grapheme mehr, sondern zusammengesetzte Wörter (siehe unten). Jede Lektion führt die sechs Trainer-**Typen** unten in fester Rangfolge durch — ein Typ kann sich wiederholen oder ganz fehlen (z. B. keine Satzrunde in einer Lektion), die Reihenfolge geht aber nie zurück; jede Lektion beginnt mit dem Visuellen Spurensucher und endet mit Rechnen.
Die Nummerierung beginnt bei 2, weil Trainer 1 (Auditiver Finder, Anfang/Mitte/Ende) entfernt wurde;
die verbliebenen Nummern bleiben, wie Code und Design-Dokumente sie nennen:

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
  **Der Merksatz muss halten, was er behauptet.** „X wie Y" heißt: Y fängt mit X an.
  „M wie Schneemann" tut das nicht — ein Kind, das gerade M lernt, hört am Wortanfang ein
  Sch. Zwei Regeln, von `LessonCoverageTest.everyMerksatzNamesAWordThatReallyStartsWithItsGrapheme`
  erzwungen:
  1. Gibt es ein Wort, das mit dem Graphem **anfängt**, nimm es — und das kurze, nicht das
     Kompositum: „B wie Bus", nicht „B wie Schulbus".
  2. Kann das Graphem gar kein deutsches Wort anfangen (ß, ck, Ch, Ö, X) oder gibt die
     Lektion keins her, heißt es „wie **in**": „Ü wie in Küken", „ck wie in Socke".
     Das Wort bleibt wahr, und das Kind hört, wo der Laut wirklich sitzt.
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
  Die `illustrationAtomId` darf dem Satz nicht widersprechen: „Das Auto ist blau" neben
  einem roten 🚗 bringt einem Kind, das gerade Farben lernt, das Gegenteil bei. Bei
  Farbsätzen zeigt das Bild deshalb die **Farbe** (🟦), nicht das Nomen — sie ist ohnehin
  das neue Wort der Runde.
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
  - Vier Runden je Lektion sind die Regel; eine fünfte ist erlaubt und trägt dort,
    wo sie steht, ein sonst bildloses Wort als *richtige* Karte ein (Nashorn,
    Flamingo, Tiger, Nilpferd, Affe, Raupe, Pilz …). `ContentValidator` lässt 3–6 zu,
    `LessonCoverageTest` hält 4–5 fest.
  - 4–8 Wörter, ein Hauptsatz, Wörter der Lektion, Cartoon-Logik (realistischer als
    die Finale-Sätze).
  - **Kurios ist erlaubt, und zwar ausdrücklich.** Ein Pony, das Taxi fährt, ein Frosch
    auf dem Fahrrad, ein Lama mit Hut, ein Krokodil, das den Keks frisst, ein Drache
    über dem Park: genau die Bilder, für die Kinder dieser Altersgruppe die Aufgabe
    überhaupt anschauen. Die Grenze ist Cartoon-Logik (§12), nicht Alltagsrealismus —
    und „lebensnah" ist **keine** Anforderung an diesen Trainer. Im September 2026 hat
    ein Review elf solcher Runden gegen Alltagsszenen getauscht und musste komplett
    zurückgenommen werden. Wer hier aufräumen will, hat den Trainer missverstanden;
    die Sachlichkeitsregel gilt für **Rechen-Szenen** (§3.7), nicht für die Bildkarten.
  - **Die falsche Karte tauscht eine Kategorie, nicht bloß die Anzahl.** Anderes Tier,
    anderes Kleidungsstück, anderer Akteur, anderes Objekt, anderer Ort. Zwei Äpfel
    gegen einen Apfel ist zu wenig Unterschied — das Kind rät die Menge, statt den
    Satz zu verstehen. Auch die Beispielobjekte und Tätigkeiten selbst wechseln, statt
    dieselbe Handlung durch die Lektionen zu tragen.
  - **Ein Plural im Satz braucht nicht zwingend zwei Bilder.** „Die Wolken zogen über
    das Haus" ist die sprachlich wertvollere Form und darf auf einer Karte mit einer
    Wolke stehen — bei unscharfen Mengen (Wolken, Sand, Sterne) zählt niemand nach.
    Die Doppelung ist ein Mittel, keine Pflicht.
  - **Die Vergangenheitsform ist der Zweck, nicht ein Versehen.** Perfekt, Präteritum
    und Partizip II stehen hier mit Absicht: dieser Trainer ist die einzige Stelle im
    Pfad, an der ein Kind sie überhaupt zu hören bekommt, und das ist sein Lernziel
    („Die Eule fing eine Maus", „Der Zug fuhr durch den Schnee", „Die Maus hat den Käse
    geklaut"). Eine bestehende Runde **nie** ins Präsens umschreiben.
    Die einzige Grenze ist der Fall, den der Nutzer 2026-08 gemeldet hat: eine Form, die
    behauptet, die Situation sei vorbei, während die Karte sie zeigt („Zwei Vögel saßen
    auf dem Baum" neben zwei sitzenden Vögeln). Das ist ein Einzelfall-Veto gegen eine
    unglückliche Form, **keine** Regel „Zustand → Präsens"; als solche gelesen hat sie
    im September 2026 zehn Sätze zu Unrecht ins Präsens gezogen.
  - **Kartenoptik und Feedback.** Die Bildkarten sind Rahmen ohne Füllfläche (der
    `CreamElevated`-Grund verdunkelte die Emojis, ohne die Kartengrenze
    sichtbarer zu machen), stehen mit ihrer Oberkante knapp unterhalb der
    Bildschirmmitte, und die Emoji-Reihe füllt die Karte so weit die Breite es
    zulässt. Ein Fehltipp wird **bewegt** quittiert, nicht gefärbt: die getippte
    Karte wackelt (`SentencePictureCardShake`), dazu `nudge`-Haptik und der Satz
    erneut — kein Rot, §8 und §10 gelten unverändert. Ein Treffer zieht die
    richtige Karte groß in die Bildschirmmitte und hält sie dort, solange der Satz
    wiederholt wird; die andere Karte blendet aus. **Auflösen („Zeig mir")
    markiert nur, es feiert nicht.**
7. **Rechnen** — reine Arithmetik mit Zahlentürmen in *jeder* Lektion (Abschnitt 8).
  **Keine Wörter zum Lesen oder Schreiben**, keine Bilder. Seit September 2026 gibt es keine
  Sachaufgaben mehr: die Ansage fragt nur die Rechnung, die Antwort ist die Zahl. Die früheren
  Regeln für Szene, Icon und Plural (`iconAtomId`, „18 Pizza") sind damit mitsamt dem Feld
  entfallen.

Zusätzlich, bis zu zweimal pro Lektion und ohne eigenen autorierten Content: eine **Buchstaben-Jagd** direkt nach dem Spurensucher und eine **Silben-Jagd** direkt nach dem Silben-Verschmelzer — jeweils nur, wenn die Lektion den entsprechenden Trainer führt und mindestens ein bereits bekanntes Vergleichssymbol existiert. Kind tippt alle Vorkommen des gesuchten Symbols in einem verstreuten Feld an; Jeder Treffer fliegt als Perle in die Herzmuschel am Boden der Tiefsee (siehe §10, „Nachtwelten"), ein Fehltipp mischt neu, ohne dass eine Perle verloren geht. Das Mischen ist **Absicht gegen Raten** (ohne es tippen Kinder alle Kugeln der Reihe nach an) und bleibt; seit September 2026 **hüpfen** die Kugeln dabei in kleinen, versetzten Bögen an ihre neuen Plätze statt zu teleportieren, und das Feld nimmt keine Tipps an, bis die letzte gelandet ist (~650 ms, `HuntShuffleHop`). Die Bewegung bremst Durchtippen zusätzlich, ohne zu strafen.

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

Dritter abgeleiteter Zusatz-Trainer: der **Laut-Fresser** direkt nach der Buchstaben-Jagd
(ohne Jagd nach dem letzten Spurensucher), ab Lektion 3 — „Füttere die Laut-Fresser." Zwei
Figuren tragen je einen Laut auf dem Bauch (`S`, `Sch` — nur die Großform, denn diese
Laute stehen immer am Anfang eines Substantivs; allein die Vokalpaare, die `anywhere`
im Wort suchen, zeigen beide Formen: `Ei / ei`), oben erscheinen
nacheinander bis zu sieben Bildkarten; das Kind hört das Wort und zieht die Karte zu dem
Fresser, dessen Laut es hört. Gefragt wird nur **welcher** Laut, nie **wo** im Wort — das
Anfang/Mitte/Ende-Konzept hat den früheren Auditiven Finder gekostet. Welche Lektion
welches Paar spielt, entscheidet eine kuratierte Tabelle in drei Stufen (`SoundPairs`):
**Kontrast** (S/Sch, S/Z, F/W, W/B, B/P, D/T, G/K, K/T, M/N, L/R, St/Sp, Pf/F — echte
Verwechslungen nach Fox-Boyer und H-LAD, nur am Anlaut), **Aufwärmen** (P/T, L/H, F/T, S/T
für L03–L07, damit das Kind Monster und Geste an leichten Kontrasten lernt) und **Vokal**
(Ei/Au, Ei/Eu, Ö/Ü, I/O — irgendwo im Wort). Ein Paar ist Kandidat, wenn ein Laut Fokus
der Lektion ist, beide eingeführt sind und der Pack je Seite mindestens zwei und zusammen
mindestens sechs Karten hergibt; nie gespielt schlägt gespielt, sonst das am längsten
Zurückliegende. Ohne Kandidaten wiederholt die Lektion ein bekanntes Kontrast-Paar. Karten
sind Substantive mit genau einem Emoji, der Partnerlaut kommt nirgends im Wort vor (auch
nicht als `ß`), und für S zählt auch ein `st`/`sp`, das nicht am Wortanfang steht;
Minimalpaare (Fisch/Tisch, Kanne/Tanne) werden bevorzugt und liegen nebeneinander, und die
Karten rotieren über die Lektionen durch den alphabetischen Vorrat. Richtiger Fresser kaut
und spricht Laut und Wort, falscher spuckt („Bäh!") und die Karte hüpft zurück, beim
zweiten Fehlgriff derselben Karte pulsiert das richtige Maul — kein „Zeig mir". Details
und Zuordnung aller Lektionen:
[Laut-Fresser-Design](superpowers/specs/2026-09-04-laut-fresser-design.md).

Reihenfolge-Regeln, die Content und Validator erzwingen:

- Der Wort-Bauer zeigt nie ein Graphem oder eine Silbe, die noch nicht eingeführt wurde.
- **Graphem-Einheiten bleiben ganz.** Kein Baustein-Schnitt (Wort-Bauer *und*
  Silben-Verschmelzer) läuft mitten durch ein Zeichen, das einen Laut trägt: `Sch`, `Ch`,
  `ck`, `Qu`, `Ei`, `Au`, `Eu`, `Äu`, dazu Vokal + Dehnungs-h (`uh` in Kuh) und Doppelvokal
  (`ee` in Schnee). „Bäume" als `Bä + u + m + e` stand bis September 2026 im Pack — ein
  Kind, das die Kacheln lautiert, liest `bä-u-me`, und die Silbe „Bä" gibt es nicht.
  Ausgenommen sind `Pf`, `St`, `Sp`: sie tragen *zwei* Laute, die in verschiedene Silben
  fallen dürfen („Apfel" trennt `Ap-fel`, „Wespe" `Wes-pe`). Die Trennlinie ist also der
  Laut, nicht die Buchstabenzahl. `ContentValidator` erzwingt es und leitet die Einheiten
  aus dem Pack ab, damit ein neues Graphem-Atom sie automatisch mitnimmt.
- **Eine Silbe wird nur eingeführt, wenn sie in einem Wort vorkommt.** Der
  Silben-Verschmelzer darf keine Silbe erfinden, die die Kombinatorik der Fokus-Grapheme
  hergibt, das Deutsche aber nicht: l16 verschmolz `pf + a = pfa` — die einzige Silbe des
  Packs, deren Buchstabenfolge in *keinem* Wort stand, auch in keinem Bildwort. `ck` kann
  im Deutschen gar keine Silbe anfangen (es steht immer nach kurzem Vokal, „So-cke" ist
  eine Trennregel), und `pf` bildet mit keinem Vokal eine offene Silbe, die in einem
  Fibel-Wort vorkommt. Deshalb hat l16 seit September 2026 **keinen Verschmelzer** — wie
  l12 nie einen hatte. Ein Trainer, der nichts Echtes zu zeigen hat, fällt aus.
- Satzrunden (Satz-Architekt) nutzen nur gebaute Wörter, kleingeschriebene Funktionswörter oder ausdrücklich als `holisticAtomIds` markierte Ganzwort-Bilder (so führt die Fibel z. B. „Tor" vor dem R ein). Der **Satz-Versteher ist davon ausgenommen** — wie die Finale-Sätze lebt er von grammatischer Freiheit (Hör-Trainer, keine Bauen-Anforderung).
- Gemeisterte Lektionen bleiben zum Wiederholen antippbar.

### Phase 8 — zusammengesetzte Wörter (l27–l34)

Nach l18 sind alle 40 Grapheme eingeführt; Nachschub kann also nicht mehr aus neuen
Buchstaben kommen. Phase 8 lehrt stattdessen ein eigenes Prinzip: **lange Wörter sind
aus kurzen gebaut, die das Kind schon kennt.** Sie nutzt dieselben sechs Trainer-Typen,
aber zwei davon in erweiterter Bedeutung:

- **Silben-Verschmelzer** schiebt zwei *ganze Wörter* zusammen statt zweier Laute
  („Schiebe Hand und Schuh zusammen. Welches Wort entsteht.“). Die Magnet-Metapher trägt
  das unverändert; nur der Prompt fragt nach dem Wort, nicht nach der Silbe. Das Ergebnis
  ist ein Wort-Atom, kein Silben-Atom — deshalb leitet sich hier **keine Silben-Jagd** ab
  (`SymbolHuntDerivation` findet nichts zu jagen), der Wort-Detektiv fällt auf den
  Buchstaben-Modus zurück.
- **Wort-Bauer** darf ein **bereits gebautes Wort als Baustein** wiederverwenden
  (`Fuß` + `ball`). Das ist die einzige Erweiterung der Regel „nie ein untaugliches
  Graphem": ein Wort zählt als eingeführt, sobald eine frühere Runde es gebaut hat.
  Innerhalb einer Lektion gilt das erst *nach* seiner eigenen Runde — jede Phase-8-Lektion
  baut die fehlenden Einzelteile zuerst (`Hand`, `Jacke`), dann das Kompositum.
  `LessonCoverageTest.wordBuilderNeverOffersAnUntaughtGrapheme` hält beides fest.

Der Spurensucher bleibt Pflicht (jede Lektion beginnt damit) und zeichnet ein Graphem
nach, das in den Wörtern der Lektion wirklich vorkommt — sonst findet der Wort-Detektiv
keinen Treffer.
Sein Merksatz nennt den **Bestandteil**, nicht das Kompositum („H wie Hand", nicht „H wie
Handschuh") — Regel 1 oben gilt hier unverändert.

**Reihenfolge der Phase 8:** erst die Komposita, deren Teile unverändert aneinanderstoßen
(Hand+schuh, Apfel+kuchen, Fuß+ball, Eis+bär, Schnee+mann), danach die mit Fugenelement
oder gekürztem Erstglied (Sonne**n**+blume, Tasche**n**+lampe, Schul+tasche) — dort muss
das Kind hören, dass das erste Wort sich beim Zusammenschieben verändert, und das ist
der schwierigere Schritt. Seit September 2026 also: Handschuh/Hausschuh →
Apfelkuchen/Brotdose → Fußball/Baumhaus → Eisbär/Vogelnest → Schneemann/Schneeball →
Regenjacke/Sonnenblume → Schultasche/Schulbus → Mülltonne/Taschenlampe. Jedes Teilwort
ist gebaut, bevor ein Kompositum es braucht (Ball vor Schneeball, Jacke vor Regenjacke).

### Redaktionsregel Wortschatz

Der Bildwortschatz ist der Teil des Contents, der am schnellsten altert. Zwei Regeln:

- **Freie Slots gehören dem Alltag des Kindes.** Belohnungswörter des Spurensuchers,
  Rechen-Ikonen, Bildkarten und Finale-Bilder unterliegen **keiner** Graphem-Beschränkung —
  dort steht kein Wort, das ein heutiges Vorschulkind nicht kennt. Gebundene Slots
  (`word_build`, `syllable_merge`, `sentence_order`) folgen zuerst der Schreibbarkeit;
  dass „Ufo“ in l05 steht, ist eine Folge des Buchstabenpfads, kein Redaktionsfehler.
- **Kein Atom ohne Auftritt.** Ein Atom, das kein Task, kein Satz und kein Finale
  referenziert, wird gelöscht oder eingebaut — gepflegt wird es nicht. Der Pack trug im
  August 2026 60 solcher Karteileichen; die Hälfte davon waren genau die Alltagswörter,
  die vermeintlich fehlten. Der Stand ist seither **null**, und
  `LessonCoverageTest.noAtomSitsInThePackWithoutEverBeingShown` hält ihn dort. Ein
  neues Atom kommt also zusammen mit der Runde, die es zeigt, nicht auf Vorrat.
- **Emojis kommen aus der App, nicht vom Gerät** (seit September 2026). Jeder `Text`, der ein
  Bild zeigt, setzt `fontFamily = SilboEmoji` (`res/font/silbo_emoji.ttf`): Noto Color Emoji
  2.057 (OFL, CBDT — rendert ab API 26; die COLRv1-Fassung erst ab Android 13), gekürzt auf
  genau die Emojis des Packs samt Hautton- und ZWJ-Ligaturen, ~750 KB. Damit sieht jedes Bild
  auf jedem Telefon gleich aus, und ein Glyph, der jünger ist als das Gerät, ist trotzdem zu
  sehen — bis dahin rendert 🫜 (Unicode 15.1) unter Android 15 als leeres Kästchen, und die
  Regel war „im Zweifel den älteren Glyphen nehmen". Die Grenze liegt jetzt bei der
  gebündelten Noto-Version, nicht beim Gerät. **Ein neues Emoji im Pack verlangt einen
  Neubau** (`tools/fonts/build_fonts.py`); ohne ihn fällt Android auf die Systemschrift
  zurück, und `EmojiFontCoverageTest` wird rot. Kein `androidx.emoji2`: dessen Metadaten
  überleben das Kürzen der Schrift nicht.
- **Emoji-Doppelgänger sind keine zwei Karten.** Zwei Atome mit demselben Glyphen
  (Uhu/Eule 🦉, Wespe/Biene 🐝, Rad/Fahrrad 🚲, Pflanze/Gras 🌱) sehen für ein Kind
  gleich aus; das zweite wurde gelöscht statt platziert. Die Regel gilt für Dinge und
  Tiere, die ohnehin dasselbe Bild wären.
- **Mehrere Kinder sind ausdrücklich erwünscht — die Doppelgänger-Regel ist kein Deckel
  auf einen Jungen.** Figuren, die dasselbe Basis-Emoji brauchen, unterscheidet die
  **Hautton-Variante**: Tom bleibt 👦 (die neutrale gelbe Grundform), Mateo ist 👦🏽,
  Lennard 👦🏼. Damit sind sie drei unterscheidbare Karten und dürfen auch zusammen auf
  einer stehen („Mateo und Lennard sind ins Baumhaus geklettert" → 👦🏽👦🏼🛖). Die
  Modifikatoren sind Unicode 8.0 (2015), liegen also weit unter der Altersgrenze aus der
  Redaktionsregel Wortschatz und rendern ab `minSdk` 26 überall. Im September 2026 hatte
  ein Review daraus fälschlich geschlossen, ein zweiter Junge sei unmöglich, und die
  Namen nur in den Satztext geschrieben.



## 4. Content-Graph

- Atome (Buchstabe / Silbe / Wort + Emoji) sind wiederverwendbar über alle sechs Trainer-Typen einer Lektion.
- **Pfad-Schilder** (`content/LessonSign.kt`, seit Oktober 2026): ein ABC-Klotz pro Fokus-Laut
  der Lektion (`focusAtomIds`), in der **Großform** des Lauts (`M`, `Sch`, `Ei`; `ck` und `ß`
  bleiben klein, es gibt sie nicht groß). Auf der Würfelseite steht das **Anlautbild aus dem
  Spurensucher** derselben Lektion (`letter_trace.rewardEmoji`: „M wie Mond" → 🌙) — das Kind
  hört genau dieses Paar nach dem Nachzeichnen wieder. Das kehrt die frühere Regel um, das
  Belohnungsbild nicht vorwegzunehmen: es ist jetzt der Anker. Die Klotzfarbe hängt am Laut
  (Reihenfolge der Einführung im Pack), nicht am Platz — `M` ist überall derselbe Klotz.
  Wiederholungslektionen (Label endet auf „+") tragen statt des „+" ein ↻-Abzeichen.
  Verworfen: „M a" (erster Laut groß, Rest klein — las sich wie ein Wort, das keins ist) und
  drei Bildwörter aus späteren Trainern (👩 🐜 ☀️), die keinen Laut benannten.
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
- **Der gezeichnete Glyph ist größer als sein Kasten:** die Straße ist ein Band um die Bahn
  und steht an jeder Kante um den Korridor über die 1×1-Box hinaus. Der Kasten wird deshalb
  so gedeckelt, dass Kasten **plus** Band in den gemessenen Platz passt — sonst lag das Band
  im Speaker darüber (Compose beschneidet ein Canvas nicht auf seine eigene Größe).
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

- **Pfad-Screen ist der Einstieg**: ein gepunkteter Trittspuren-Weg durch eine **Abendlandschaft**
  (Himmel von Nachtblau über Pflaume zu warmem Orange, tief stehende Sonne hinter den Hügeln,
  Sterne, drei dunkle Hügelbänder mit Bäumen als Silhouetten und Parallaxe; bis September 2026
  ein heller Tag). Hinter den Hügeln steigen **ständig Himmelslaternen** auf (`SkyLanterns`):
  im Mittel alle 10 s eine, jede braucht 90–150 s bis über den oberen Rand und pendelt dabei
  leicht; ferne sind kleiner, langsamer und blasser. Sie sind reine Stimmung, ohne Aufgabe, und
  stehen bei „Bewegung reduzieren" still — der Himmel ist dann trotzdem voll. Erreichbare
  Schilder leuchten warm, Stern und Punktestand stehen hell auf dem Himmel. Ein Schild pro
  Lektion: **ABC-Klötze auf einem Brett** (`PathSignNode`), ein Klotz pro Laut (§4); ein oder
  zwei Laute stehen nebeneinander, drei oder vier werden zu einem Turm gestapelt (zweite
  Reihe obendrauf, das Brett bleibt am Weg). Die Klötze sind **Würfel**: vorn der Buchstabe,
  an der Seite das Anlautbild. Nur **ein** Schild dreht seine Würfel: das offene, dessen
  Knoten der Fokuslinie am nächsten liegt (`PathFocus.turningIndex`, dieselbe Linie bei 42 %
  der Höhe, auf der der Pfad das aktuelle Schild parkt). Nach dem Öffnen ist das also das
  aktuelle Schild; scrollt das Kind, geht die Drehung an das Schild über, das dort zur Ruhe
  kommt. Gesperrte und geplante Schilder drehen nie. Das Schild im Fokus dreht kurz nach
  dem Öffnen (1,4 s) bzw. nach dem Hinscrollen (0,7 s) und dann alle ~9 s einmal
  nacheinander zum Bild, hält 1,6 s und dreht zurück („M … wie Mond" ohne Worte); verliert
  es den Fokus mitten in der Drehung, dreht es weich zurück. Alle anderen Schilder zeigen
  nur Buchstaben; so bleibt der Pfad ruhig. Die Drehung ersetzt den früher atmenden Ring des
  aktuellen Schilds und steht bei „Bewegung reduzieren" still. Der bereits zurückgelegte Teil des Weges ist wärmer
  gezeichnet als der Rest. Gesperrte Schilder zeigen dunkle Klötze mit gedämpften, aber
  lesbaren Buchstaben und ein Schloss; Bilder zeigen sie nicht.
Gesperrte und noch nicht autorierte Schilder reagieren auf Tippen mit einem gesprochenen Hinweis —
niemals mit einem stummen No-Op.
- **„Du bist hier“-Marker**: über dem Schild der aktuellen Lektion steht eine Pin-Nadel
  (`SunCoral` mit heller Kontur in `Cream`), die sanft auf und ab wippt — das Kind soll auf einem Screen
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
- **Freischalten heißt durchgespielt, nicht fehlerfrei.** Die nächste Lektion öffnet, sobald in
  der vorherigen jeder spielbare Trainer zu Ende gebracht wurde — allein gelöst **oder** über
  „Zeig mir" aufgelöst (`LessonGating.isCompleted`). Der Stern am Schild (`Mastered`) verlangt
  weiterhin, dass jeder Trainer allein gelöst wurde, hält den Pfad aber nicht mehr auf. Grund:
  183 der 226 spielbaren Trainer haben genau eine Runde — ein einziges „Zeig mir" sperrte dort
  die Folgelektion dauerhaft, obwohl die Lektion samt Feier zu Ende gespielt war. Das traf
  genau das Kind, das die Hilfe gebraucht hat, war für es nicht erkennbar und ist damit eine
  Strafe (§8). Eine autorierte Lektion, deren Trainer alle pausiert sind, reicht den Stand
  ihres Vorgängers durch, statt die Kette zu kappen.
- Tippen auf ein freigeschaltetes Schild startet die Trainer-Session dieser Lektion — die
  sechs autorierten Typen (Abschnitt 3), ergänzt um etwaige abgeleitete Zusatz-Trainer
  (Jagd, Wort-Detektiv).
- Kein Domänen-Mix, keine Zufallsrotation: die Trainer-Reihenfolge ist didaktisch fix.
- Vor/Zurück zwischen Runden ist **immer** möglich, unabhängig von Punkten/Fortschritt.
- Fortschritt speichern nach jeder Antwort; unfertige Lektion wird beim Öffnen fortgesetzt.
- Back in der Übung und der Zurück-Pfeil verlassen die Lektion **direkt** zum Pfad, ohne
  End-Screen — unabhängig von den Punkten. Der Zurück-Pfeil oben links verlangt dafür
  **Gedrückthalten** (0,8 s, `AbcHoldToExitButton`): ein Ring in `SunCoral` füllt sich,
  ein kurzer Tipp lässt den Pfeil nur wackeln. Er sitzt dort, wo die Hand beim Halten des
  Telefons aufliegt, und eine Rückfrage-Box kann ein Vorschulkind nicht lesen. TalkBack
  behält die direkte Aktion; die System-Zurück-Geste bleibt unverändert (sie ist im
  Vollbild ohnehin Elternweg).
- **Der End-Screen erscheint nur beim echten Lektionsabschluss**, mit Finale (Bildreihe + Satz
  + Speaker, Abschnitt 12). Der Satz belohnt damit Durchhalten und nutzt sich nicht ab.
- Der End-Screen kennt zusätzlich eine **schlanke Variante** ohne Bildreihe und Satz. Sie ist
  ein Defensivpfad für den Fall, dass sich kein Finale auflösen lässt — der Validator verbietet
  das für autorierte Lektionen, also praktisch unerreichbar, aber ein reduzierter Screen ist
  besser als ein leerer oder ein Absturz.
- Der End-Screen zeigt **keine Punktezahl**. Punkte stehen im Übungs-Chrome und auf dem Pfad.

## 6. Hilfestufen

- Parent-Gate (langer Druck auf das Drei-Punkte-Menü) öffnet das Sheet „Eltern“. Es ist ein
  **schwebender runder Knopf oben rechts auf dem Pfad-Screen**, kein Bar-Element, und trägt das
  **native Overflow-Icon — drei senkrechte Punkte** (`Icons.Rounded.MoreVert`), keinen getippten
  ⋯-Glyph: Erwachsene erkennen daran ohne Text, dass hier ein Menü liegt, und ein Vektor wächst
  nicht mit der Schriftskalierung aus dem 48-dp-Knopf heraus. In der Lektion gibt es ihn nicht: dort führt der Weg zu den Einstellungen über das Verlassen der Lektion. Das Sheet
  bietet die Hilfestufe (**Auto / Mit Hilfe / Ohne Hilfe**) und die Freigabe „Reihenfolge frei
  wählbar“, die die Fortschrittssperre des Pfades aufhebt — sonst nichts. Kein Entwickler-Eintrag,
  auch nicht in Debug-Builds: die frühere Seite „TTS Debug“ ist entfernt, die Clip-Texte entstehen
  in `tools/tts`.
- Ein **kurzer Tipp** auf das Drei-Punkte-Menü öffnet nichts, sondern zeigt den nativen Toast
  „Lange drücken für das Eltern-Menü“ (`R.string.parent_gate_hint`). Die Kindersicherung bleibt
  damit unangetastet, aber der Knopf antwortet auf die häufigste Fehlbedienung statt stumm zu
  bleiben. Ein abgebrochener Druck (Finger wandert weg) bleibt stumm.
- Auto passt Gerüste sanft an; erzwungene Stufen frieren Auto-Streaks ein.
- Gerüste pro Atom/Slot (Silhouette vs. Lücke), nicht global starr über die ganze Aufgabe.

## 7. Sprache & Audio

- App und Inhalte deutsch. Sprache ist bei jedem TTS Aufruf eindeutet angegeben. 
- System-TTS für Prompts; Speaker (Vektor-Icon) **im Aufgabenbereich**, mittig am Kopf der
  Bühne (`ExerciseStage(promptChrome = …)`) und damit in **jedem** Trainer auf derselben
  Höhe, direkt unter der Fortschrittszeile — nicht als erstes Kind des zentrierten
  Aufgabenblocks, denn dort wanderte er mit dessen Inhaltshöhe (gemessen: 203dp in der
  Jagd, 305dp im Spurensucher, 425dp im Silben-Verschmelzer). Ein Aufgabentitel steht
  weiterhin unter ihm. Ausgenommen ist der End-Screen, der seinen eigenen Speaker beim
  Finale-Satz trägt (Abschnitt 12).
- Tippen auf Aufgaben-Items (Buchstaben, Silben, Wörter, Antwortkacheln) liest sie vor.
- Bei Erfolg: Antwort vorsprechen → Stern im oberen Drittel → erst danach nächste Aufgabe (Audio abwarten).
- **Die Antwort nennt den Artikel, die Aufgabe nicht.** Ist das Lösungswort ein Substantiv,
  spricht das Erfolgs-Vorsprechen es mit Artikel („Baue das Wort Haus" → „das Haus") —
  Gegenstände und Tiere mit dem bestimmten (der/die/das), Personenbezeichnungen mit dem
  unbestimmten (ein/eine), Namen ohne. Neutrum-Personen bekommen „das": „ein Opa" und
  „ein Kind" wären sonst nicht unterscheidbar. Betroffen sind Wort-Bauer und Wort-Detektiv
  (`SuccessSpeech`). **Nicht** betroffen: Prompts, das Antippen von Items,
  `missTts`, Rechnen (die Antwort ist ein nacktes Zahlwort), ganze Sätze,
  die ihre Artikel schon tragen, und die Fress-Sequenz des Laut-Fressers („Sch … Schuh"),
  weil ein Artikel zwischen Laut und Wort genau die Kopplung zerschnitte, die der Trainer
  lehrt. Abgeleitet wird in `AtomArticleSpeech`; `tools/tts` spiegelt
  die Regel, damit vorproduzierte Clips denselben Text tragen.
- Lob (**nur Rechnen, nur gesprochen**): ein zufälliges Wort oder ein kurzer Ausruf aus
  `PraisePhrases` steht vor der Antwort („Ausgezeichnet! fünf"), damit die Zahl das Letzte
  bleibt, was das Kind hört. Die Zahl als Wort (`GermanNumberWord`), nicht als Ziffer: „fünf"
  ist ein Clip des Zählkanals, „5" fiel direkt nach dem kuratierten Lob auf Android-TTS zurück.
  Nie als Text anzeigen — das Kind kann nicht lesen. Auflösen
  („Zeig mir") bekommt kein Lob. Jeder Eintrag ist eine eigene Äußerung und bringt seine
  Satzzeichen selbst mit („Bäääm! Volltreffer!"); zwei Einträge dürfen sich nicht nur durch
  Satzzeichen oder Groß-/Kleinschreibung unterscheiden, sonst kuratiert und rendert die
  TTS-Pipeline denselben Clip zweimal.
- Wenn kein deutsches TTS: visuelle Fallbacks, Aufgabe bleibt spielbar.
- Wort-Bauer (Trainer 4): Prompt „Baue das Wort ….“ (ohne Tray-Instruktion); Silben-/Buchstabenklötze in Schablonen tragen die Aufgabe, keine zusätzliche Lese-Titelzeile.
- Satz-Architekt (Trainer 5): Mehrwort-Prompt = Satztext ohne „Ordne die Wörter…“; Einwort-Bild-Zuordnung behält „Ordne das Wort … dem Bild zu.“
- **Monster-Stimme (Laut-Fresser):** Wörter bleiben kuratierte, normale Clips — nur die
  Tonhöhe kippt zur Laufzeit (`VoiceStyle`: links tiefer 0.75, rechts höher 1.3), die
  Artikulation ist der Engpass der App und darf nicht leiden. Die *Laute* bekommen
  nur eine Halbstufe Laufzeit-Tonhöhe obendrauf (`VoiceStyle.variantPitch`, ×0.944/×1.059 —
  die Aufnahme ist schon Monster, und ×0.75 schob ein S spektral bis ans Sch), aber eine
  eigene Quelle: Der Fresser spricht das
  Lemma des Graphems („S", „Sch") aus einer **von Hand aufgenommenen** Variante
  `monster` im Clip-Index (Mikrofon-Aufnahme im Qwen-Web-Interface, Spec
  `2026-09-05-lautfresser-mikrofon-aufnahme-design.md`), während Jagd, Wort-Detektiv und
  Spurensucher beim Buchstabennamen-Clip bleiben. „Bäh!" und „Mmmmh!" bleiben Qwen-Clips
  im TTS-Profil `monster` (Stimme `uncle_fu`) und werden ebenfalls mit der Laufzeit-
  Tonhöhe der jeweiligen Seite gesprochen. Ohne Aufnahme fällt der Laut auf denselben
  Buchstabennamen-Clip bzw. Android-TTS mit dem Lemma zurück — keine Fake-Aussprache
  mehr (`soundTts` entfiel im September 2026). Der Trainer spricht seine Ansage und
  Vorstellung selbst (`currentPromptParts` ist für ihn leer), damit Wackeln und Laut
  zusammenfallen. Ohne deutsches TTS steht das Wort als Text unter dem Emoji — ein
  Hörspiel ist sonst unspielbar.
- Feedback bei Fehlern: **vorsprechen** (oder ein Klang), nie als Fehler-Satz anzeigen.
  **Rechnen spricht bei einem Fehlversuch nichts** — nur `Sfx.Blocked` und `nudge`-Haptik,
  auch nicht den generischen Hinweis „Probiere eine andere Antwort" (seit September 2026;
  vorher Echo plus Hinweis, „sieben, Du bist nah dran, denk noch einmal nach").
- **Ansage-Sperre: ruhen statt dimmen** (`PromptRest`, seit September 2026). Solange die
  Rundenansage ihren Freigabe-Punkt nicht erreicht hat, nimmt die Aufgabe keine Tipps an —
  der gesuchte Buchstabe steckt nur in der Stimme, jeder Tipp davor wäre Raten. Sie ist dabei
  aber nicht mehr auf 50 % gedimmt (das sah kaputt aus), sondern **ruht**: kaum gedämpft
  (10 %), 3 % abgesunken. In der Jagd liegt das ganze Feld wie in der Ferne — 12 % kleiner,
  jede Blase noch einmal 25 % kleiner und zu 45 % ins Meer verblasst, **ohne Ring, ohne Leuchten und ohne Buchstaben**. Beim
  Freigabe-Punkt **wacht** sie auf: ein ruhiges Heranschwimmen über 1,1 s mit Ease, keine Feder — das Feld kommt heran, Ringe, Leuchten und
  Buchstaben erscheinen. Beim Sperren springt der Wert sofort auf „ruht", damit die Buchstaben
  der neuen Runde keinen Frame lang sichtbar sind.
  **Ein Tipp in der Ruhe bekommt eine Antwort**: ein kleiner heller Ring an der Tippstelle, ein
  leises „Blubb" (`Sfx.Blubb`, 520–1000 Hz — tiefer geben Handy-Lautsprecher nichts wieder —, mit 0,6 gespielt, damit es die Ansage nicht übertönt), der
  Lautsprecher leuchtet einmal auf, und in der Jagd bläht sich die gedrückte Blase wie gewohnt und wackelt dann (einsammeln lässt sie sich erst wach) — höchstens alle
  220 ms, kein Fehler, kein Mischen. Maßgeblich ist das Aufsetzen des Fingers: eine in der Ruhe
  gedrückte Blase wackelt auch dann nur, wenn erst nach der Freigabe losgelassen wird
  (`HuntPressLatch.pressedResting`). Ausgenommen ist der Lautsprecher selbst (über seine Lage,
  `LocalSpeakerBounds`): er spielt die Ansage auch während der Sperre ab. Während er spricht,
  atmet er sanft mit. Die Herzmuschel ruht mit dem Feld und lugt erst nach der Freigabe.
- **Geräusche (`AbcSfx`, `assets/sfx/`).** Elf kurze, synthetisierte Klänge, erzeugt von
  `tools/sfx/generate_sfx.py` (keine Aufnahmen, keine Fremdlizenz; Ogg/Opus wie die Clips,
  zusammen ~35 KB, Spitze −8 dBFS und mit 0,7 gespielt — leiser als die Stimme). **Eine Tat,
  ein Klang**, wie beim Haptik-Vokabular: `pop` Jagd-Kugel eingesammelt · `snap` Einrasten
  (Wort-Bauer, Satz-Architekt, Silben-Verschmelzer) · `boing` Karte federt zurück · `whoosh`/
  `ding` Erfolgs-Stern fliegt los/landet im Punktestand · `chime` Runde geschafft · `fanfare`
  Lektion geschafft (der Finale-Satz beginnt 0,7 s danach) · `tap` Ziffernblock · `blip`
  Spurensucher-Stern (Tonleiter über die Abspielrate) · `shuffle` Jagd mischt · `blocked`
  gesperrt, Fehlversuch im Rechnen. Kein Klang klingt nach „falsch" — auch `boing` und `blocked` sind weich.
  **Tief und warm, nicht hell:** Grundtöne 150–800 Hz, reine Sinus-Töne mit höchstens einer
  leisen Oktave, keine Glocken-Obertöne, Tiefpass 3 kHz; der Generator bricht ab, wenn ein
  Klang im Mittel über 1,1 kHz liegt. Die erste Fassung (Glöckchen bis 2,7 kHz) war laut
  Nutzer „schnell nervig für die Eltern". `chime` ist deshalb wieder genau das frühere
  Sinus-Arpeggio C5–E5–G5–C6. **Keine
  Musik, keine Dauergeräusche** (Cognitive Load). Ist ein Clip nicht ladbar, fallen `chime`,
  `blip` und `blocked` auf die frühere Sinus-Synthese zurück, damit ein Tipp nie stumm bleibt.

### TTS-Grenzen und Autorierungs-Konventionen

- **„Buchstabe" nur für echte Einzelbuchstaben.** Mehrzeichen-Grapheme (`Sch`, `Sp`, `St`, `Ch`,
  `Au`, `Ei`, `Eu`, `Äu`, `Pf`, `Qu`, `ck`, `ks` …) sind kein „Buchstabe" — das Wort ist fachlich falsch
  und für Vorschulkinder irreführend. Prompts, die einen solchen Mehrzeichen-Laut ansprechen,
  heißen „…den Laut …" statt „…den Buchstaben …" (Spurensucher,
  Buchstaben-/Silben-Jagd, Wort-Detektiv). Umlaute (`Ä`, `Ö`, `Ü`) und `ß` bleiben „Buchstabe" — sie sind je ein
  einzelnes Zeichen; `Äu` ist einer der vier Diphthonge und heißt „Laut". Silben (`kind: syllable`, z. B. `ma`, `sp`, `st` als verschmolzenes Ergebnis
  im Silben-Verschmelzer) heißen weiterhin „Silbe", nie „Laut" oder „Buchstabe".
- **Einzelbuchstaben-Betonung:** Steht ein einzelner Buchstabe/Graphem als eigenständiges Wort in einem
  TTS-String (z. B. „der Buchstabe M", „Hörst du das M …", „M wie Mond"), wird er mit Gedankenstrichen
  isoliert: `- M` wenn danach nur noch Satzzeichen folgt, `M -` wenn er einen Satz eröffnet, `- M -`
  wenn er mittendrin steht (z. B. „Finde den Buchstaben - M - im Wort - Mama.",
  „Zeichne den Buchstaben - M - nach …", „M - wie Mond."). Grund: ohne die kurze Pause
  verschluckt die System-TTS den Buchstabennamen im Wortfluss oder betont ihn falsch. Gilt für alle
  Trainer-`promptTts`/`rewardTts`, die einen einzelnen Buchstaben ansprechen (Spurensucher,
  Buchstaben-Jagd, Wort-Detektiv) — nicht für Silben-Verschmelzer (dort werden Laute bewusst
  aneinandergezogen) oder für Ganzwörter.
- **Die Rechenaufgabe endet auf „?" — und sie ist die einzige Frage.** Alle anderen Prompts
  sind Aufforderungen („Baue das Wort …", „Finde alle …") und enden auf einen Punkt. Gesprochen
  wird der Text aber nicht als Ganzes, sondern als Einleitung + Aufgabe (`MathPromptSpeech`,
  Abschnitt 8); der `promptTts` („Wie viel ist drei plus zwei?") bleibt die lesbare Fassung im
  Content und muss zu `left`/`right`/`operation` passen (`MathPromptSpeechTest`).
- **Wortwiederholungen werden am Stück gelesen, nicht buchstabiert.** Nennt ein Trainer ein Wort
  erneut, dann am Stück („Ameise.", nicht „A - M - eise."). Eine buchstabierte/segmentierte
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
- **Zahlentürme (seit September 2026, `NumberTowers`).** Die Aufgabe steht nicht mehr als
  Emoji-Gruppen da (bei 9 − 6 fünfzehn Hüte in Zweierpaaren — unruhig, und ab etwa fünf
  Dingen zählen Vorschulkinder einzeln ab), sondern als **Figuren aus Blöcken**, nach dem
  Prinzip von Numberblocks, aber mit Silbos eigenem Gesicht und ohne feste Farbe je Zahl.
  - **Säulen zu je fünf:** sieben ist „eine volle Säule und zwei", zehn sind zwei volle
    Säulen mit hellem Rahmen (der Zehner), 13 ist ein Zehner und drei — bis 30 ohne
    Symbol-Trick. Farbe nach **Rolle**: die erste Zahl Honig, die zweite Himmelblau.
  - **Schritt 1, Tippen:** tippt das Kind auf die Aufgabe (oder einen Turm), springen die
    Blöcke — bei Plus die blauen einzeln auf den Honig-Turm (sie bleiben blau: man sieht die
    4 und die 3 in der 7), bei Minus die weggenommenen oben herunter auf die gestrichelten
    blauen Plätze über der rechten Zahl, die **von Anfang an** dastehen; ihre alten Plätze
    bleiben als Geisterblöcke, die erste Zahl bleibt sichtbar. Bis zum Tipp hüpfen die
    springbereiten Blöcke alle 4,5 s kurz, als Einladung. Ein Tipp auf einen Turm sagt
    außerdem seine Zahl (Zählkanal, `GermanNumberWord`).
  - **Schritt 2, richtige Antwort:** erst jetzt verschmelzen die Figuren endgültig — Blau
    wird Honig (Plus), Geister und Weggenommenes gehen (Minus), die Türme rücken zusammen
    (Mal) — und die fertige Figur bekommt ein **großes Gesicht** über ihr größtes voll
    gefülltes Rechteck (`NumberTowerGeometry.faceRect`). Ohne vorherigen Tipp läuft erst der
    Sprung, dann das Verschmelzen.
  - **Malnehmen:** „3 mal 4" sind drei gleiche Vierertürme, jeder mit eigenem kleinen Gesicht,
    die nacheinander hereinfallen; nach der Antwort rücken sie zusammen, und aus den kleinen
    Gesichtern wird ein großes.
  - **Antwortkacheln zeigen nur die Zahl** — kleine Türme darin machten es zu leicht,
    brachten eine dritte Farbe und mehr Unruhe. Die Platte unter den Türmen trägt die Aufgabe
    als Ziffern (`4 + 3`); eine zweite Ziffernzeile gibt es nicht mehr.
  - **Kein Auflösen, keine Zähl-Hilfe** (entfernt September 2026): die Türme lassen sich
    jederzeit antippen und springen — das ist die Hilfe, und sie ist immer da.
  - **Nur Zahlen, keine Sachaufgaben:** Da keine Bilder mehr zu sehen sind, fragt die Ansage
    nur die Rechnung („Wie viel ist vier plus drei?"), Rechenrunden tragen kein `iconAtomId`
    mehr. Rechnen läuft in **jeder** Lektion.
  - **Die Ansage:** Einleitung „Wie viel ist" + ganze Aufgabe als ein Clip („vier plus drei",
    `MathPromptSpeech.taskText`, Profil `math`, einzeln im TTS-UI kuratiert), gebaut aus
    `left`/`right`/`operation`, nicht aus dem Text. Zahl · „plus" · Zahl aus Einzelclips
    zusammenzusetzen klang zu uneinheitlich — Qwen trifft Betonung und Stimme dabei nicht gleich.

- Rechenaufgaben im Kachel-Modus: genau **3** Antwortoptionen, gleich groß, nur die Zahl. Die
  drei Kacheln stehen in **einer** Reihe und behalten ihre volle Trefferfläche; wird es eng,
  gibt der Abstand nach (`MathBoardSizing`). Die Türme nehmen gut zwei Fünftel der Bühne,
  im Ziffernblock-Modus gut ein Drittel. Geprüft von `MathBoardBoundsTest` (gerendertes
  Layout auf drei Breiten, drei Höhen, drei Schriftgrößen) und `NumberTowerGeometryTest`
  (jede Aufgabe bis 30 passt auf die schmalste Bühne).
- **Progression (bewusst steil):** Zahlenraum 10 schon in Lektion 1, Wegnehmen ab Lektion 2, Zahlenraum 20 ab Lektion 3, Malnehmen ab Lektion 6, Zahlenraum **30** ab Lektion 9. Der Validator deckelt Operanden und Ergebnis bei 30 (`MaxMathQuantity`). Schwierigkeitsbänder: easy ≤5, medium ≤10, hard ≤20, expert ≤30.
- Korrekte Antwort bestätigt sich **grün** (Kachel bzw. Zahlenfeld), solange sie vorgesprochen wird.
  Falsche Antwort wird **nicht** rot markiert und **nicht** kommentiert: ein Fehlversuch ist
  nur ein Klang (`Sfx.Blocked`) mit `nudge`-Haptik, ohne Sprache — kein Echo der getippten
  Zahl, kein „nah dran", kein generischer Miss-Hinweis.
- **Eingabeart:** Zahlen-Eingabe bei fortgeschrittenem Scaffold **oder** sobald das Ergebnis über 10 liegt (Band `hard`/`expert`) — außer die Eltern haben ausdrücklich „Mit Hilfe“ (`ParentMode.Beginner`) gewählt, dann bleiben überall die drei Kacheln. Die Regel prüft den Eltern-Modus, nicht das abgeleitete Scaffold: im Default `Auto` startet ein frisches Kind auf `Beginner`, gegen das Scaffold geprüft liefe sie beim Normalnutzer ins Leere. Grund: drei Kacheln mit Nachbar-Distraktoren machen Raten zur billigsten Strategie. Regel in `MathHinting.inputFor`.
- **Kinder-Ziffernblock statt System-Tastatur** (`NumberPad`, seit September 2026; revidiert
  die frühere Regel „System-Tastatur im Zahlenmodus, kein Custom-Nummernblock"). Oben das
  Antwortfeld (blasses „?", solange leer) zwischen Löschen (Icon) und Absenden (SunCoral,
  Pfeil-Icon), darunter zwei Fünferreihen Ziffern 1–5 / 6–0 — dieselbe Gliederung wie die
  Fünfersäulen der Zahlentürme. Tasten 56–72 dp (`NumberPadInput.keySizeDp`: fünf je Reihe
  passen auf 320 dp), federn beim Drücken ein, geben `tick` und **sprechen die Zahl, die jetzt
  im Feld steht** („eins", dann „zwölf") auf dem Zählkanal. Eine führende Null wird ersetzt,
  mehr als drei Ziffern gibt es nicht. Grund: die System-Tastatur kam in fast der Hälfte der
  Rechenrunden, war Erwachsenen-UI (Komma, Minus, „Fertig"), klappte von selbst auf und
  verdeckte die Aufgabe.



## 9. Layout-Grundform der Übungen

- **Chrome oben (nativ):** eine durchsichtige M3-Top-App-Bar. In der Lektion trägt sie den
  Zurück-Pfeil (nach links, nicht X — die Lektion ist ein Ziel, das man verlässt, kein
  Dialog) und mittig den Punktestand: kein Lektionstitel (Elterntext an der Stelle, an der
  das Kind zuerst hinsieht), kein Overflow-Menü. Auf dem Pfad trägt sie **links in der Ecke**
  Stern + Punkte — nicht rechts: rechtsbündig wanderte der Stern bei jeder zusätzlichen Ziffer
  nach links, der Punktestand verschob sich beim Zählen. Links steht der Stern fest und nur die
  Zahl wächst nach rechts, weg vom schwebenden Drei-Punkte-Knopf am anderen Ende derselben
  Zeile. Beide sitzen auf der Mittelachse der Titelzeile und auf derselben Randachse
  (`AbcDimens.screenHorizontal`); der Knopf-Versatz ist aus Leistenhöhe und Knopfgröße
  gerechnet (`TopBarFloatingActionTop`), nicht geschätzt.
  Darunter **eine** Zeile: Rückfall-Chevrons an den Rändern, dazwischen die Fortschrittskette.
  Der Speaker sitzt nicht im Chrome, sondern gemäß §7 im Aufgabenbereich.
- **Der Punktestand steht in der Lektion mittig in der Kopfzeile** (`AbcStarCount`), auf
  derselben Achse, auf der am Ende des Trainers der große Stern hochkommt (`SuccessBurst`) —
  die Punkte wachsen dort, wo der Stern landet, statt in einer Ecke der Leiste. Mittig heißt
  hier auf der **Bildschirmmitte**, nicht mittig im Titel-Slot: der beginnt erst hinter dem
  Zurück-Pfeil. Dass er dafür als eigene Lage über der Leiste liegt statt im Titel-Slot, ist
  der Preis; die gemeinsame Achse mit dem Stern ist ihn wert.
- **Was der Punktestand unter dem Fortschritt freigegeben hat, bekommt die Bühne nur zu
  zwei Dritteln** (`SpeakerFollowFraction` in `TaskShell`): rückte der Speaker die volle
  Höhe nach, klebte er an der Fortschrittszeile.
- **Fortschritt ist eine Segmentkette** (`AbcSegmentedProgress`): ein Segment je Trainer, das
  laufende füllt sich nach Runden-Anteil. Sie ersetzt Balken, Textlabel „3/8" und Runden-Punkte —
  das Kind liest das Label ohnehin nicht.
- **Vor/Zurück ist ein Rückfallweg, kein Angebot**: die Chevrons haben kein Button-Gehäuse mehr,
  nur den gedämpften Glyph (Trefferfläche bleibt 48 dp). Vorwärts kommt das Kind durch Lösen.
- **Vollbild, und die Wischgesten gehören der App:** Status- und Navigationsleiste sind
  versteckt (Sticky Immersive, `MainActivity.hideSystemBars`) — ein Randwisch holt sie kurz
  zurück, statt sofort Zurück oder Home auszulösen; ein Kind darf sich nicht aus der Lektion
  wischen. Darauf setzt `systemGestureExclusion()` über die **ganze Übungsfläche** auf: ein
  Ziehen, das nah am Rand beginnt (der Silben-Verschmelzer verlangt genau das), bleibt bei der
  App. Beides gehört zusammen — ohne Vollbild deckelt Android den Ausschluss bei 200 dp je
  Bildschirmkante und die Fläche wäre nur zum Teil geschützt. Der Weg für Eltern nach draußen
  bleibt: Leisten hervorwischen, dann normal navigieren.
- **Schutzbereiche sind durchsichtig:** kein globales `safeDrawing`-Padding auf der Wurzel —
  Hintergrund und Pfad-Landschaft laufen unter Status- und Nav-Bar durch, jedes Element
  konsumiert seinen Inset selbst. Die Unterkante des Aufgabenbereichs nutzt `safeDrawing`
  (nicht nur `navigationBars`), damit eine Tastatur den Block weiter hochschiebt — seit dem Kinder-Ziffernblock kommt in der Kind-UI keine mehr vor, die Regel bleibt als Absicherung.
  Die Kopfzeile nimmt aus demselben Grund `safeDrawing` statt der M3-Vorgabe `systemBars`: im
  Vollbild ist der Status-Bar-Inset null, ein Display-Ausschnitt bleibt aber bestehen.
  **Voraussetzung dafür ist `android:windowSoftInputMode="adjustResize"` im Manifest.** Ohne die
  Zeile steht der Modus auf `UNSPECIFIED` und Android wählt selbst — auf dem Motorola-Testgerät
  (Android 16) wählt es Pan und schiebt beim Aufklappen der Zahlentastatur das **ganze Fenster**
  hoch, bis Kopfzeile, Punktestand und Fortschrittszeile aus dem Bild wandern. Die Kopfzeile
  bleibt nur stehen, wenn die Tastatur ausschließlich als Inset ankommt. Auf dem Emulator
  (Android 17) fällt das nicht auf — dort blieb das Fenster von sich aus stehen, der Bug war
  **nur auf dem Gerät** zu sehen.
- **Der Pfad scrollt unter der Leiste durch, nicht unter ihr entlang:** der Scroll-Bereich füllt
  den ganzen Screen und hält oben nur so viel Platz frei, wie Status-Bar plus Leiste hoch sind.
  Stünde er als eigene Zeile unter der Leiste, schnitte seine Oberkante die Schilder mitten im
  Bild ab — eine Kante, die der durchgehende Hintergrund nicht erklärt.
- **Speaker-Kopfzeile:** ganz oben in der Bühne, feste Höhe über alle Trainer (§7).
- **Prompt/Aufgabe:** Block darunter, zentriert, mit Luft zu den Rändern (kein Kleben am Screenrand).
- **Antworten:** unterer Block, zentriert (Kacheln, Mengenwahl, Ziffernblock).
- **Der Aufgabenblock steht still, während die Runde läuft.** Er ist in dem
  zentriert, was der Antwortblock übrig lässt, also verschiebt ihn jede
  Höhenänderung unten — im Wort-Bauer wanderte das Wort dadurch durch drei
  senkrechte Positionen: Tray voll, Tray leer (alle Bausteine sitzen), Rahmen zu
  fertigem Wort. Wer einen Block im Lauf einer Runde leer laufen oder schrumpfen
  lässt, hält dessen größte Höhe fest (`holdTallest` in `WordBuildTrainer`,
  gemessen statt gerechnet — Zeilenumbruch des Trays, Zeilenhöhe und
  Systemschriftgröße kennt die Geometrie nicht). Das Kind soll die Lösung im Blick
  behalten, nicht dem Wort nachsehen.
- Keine doppelte Aufgabe+Vorschau desselben Tokens.
- Ausnahme Buchstaben-/Silben-Jagd: Kacheln verstreuen sich über den gesamten Aufgabenbereich statt in einer geordneten Antwortliste; die Herzmuschel liegt im Antwortbereich unten.
- Ausnahme Wort-Detektiv: der Antwortbereich trägt **Quittungs-Striche statt Wahloptionen**.
  Sie sind bloße Grundstriche ohne Rahmen und ohne Tray — die einzige Symbolquelle ist das
  Wort im Aufgabenblock. Damit sind sie von den Schablonen des Wort-Bauers unterscheidbar.
- Ausnahme Laut-Fresser: der Antwortblock trägt **zwei Drop-Zonen** (die Fresser) statt
  Kacheln, der Aufgabenblock die aktuelle Bildkarte und rechts daneben den Futterhaufen als
  Rundenfortschritt; er hält seine Höhe, wenn die letzte Karte gefressen ist. Weil das Kind
  die Karte nach unten zu den Fressern zieht, zeichnet der Aufgabenblock über dem
  Antwortblock (`ExerciseStage(promptAboveAnswers = true)`) — Wort-Bauer und Satz-Architekt
  ziehen aufwärts und bleiben bei der Vorbelegung `false`.
- **Satz-Architekt: die Peg-Reihe bricht nie um.** Ein Satz steht immer in *einer*
  Zeile. Damit das auf jeder Breite und bei jeder Systemschriftgröße gilt, ist die
  Rangfolge in `SentencePegSizing` verbindlich: (1) die Reihe passt, (2) jeder Peg
  bleibt tippbar (56dp), (3) jeder Peg ist so breit wie **sein eigenes** Wort, nicht
  wie das längste der Runde, (4) der Glyph nimmt, was übrig ist. Punkt 3 gibt der
  leeren Lücke die **Silhouette** ihres Wortes — Längen-Matching ist erwünscht, eine
  Vorlese-Vorstufe. Punkt 4 heißt: **kein Glyph-Floor**; auf schmalen Geräten fällt
  die Schrift unter die 20sp aus §10, weil ein unerreichbarer Peg eine kaputte
  Aufgabe ist und eine kleine Schrift nur eine unschöne. Autorierungs-Grenze
  (`SentencePegSizing.ReadableGlyphDp`, 18dp auf 348dp Referenzbreite) wird von
  `SentencePegSizingTest` gegen den Pack geprüft — ein zu langer neuer Satz bricht
  den Test, nicht den Bildschirm. Einzige Stelle, an der der 56dp-Boden nachgibt:
  fünf Pegs passen auf einem 320dp-Gerät physikalisch nicht in eine Zeile, dann
  schrumpft die ganze Reihe gleichmäßig auf rund 48dp je Peg.
- Ausnahme Satz-Versteher: der Antwortblock beginnt bei **52 % der Bühnenhöhe**
  statt am unteren Rand (`ExerciseStage(answerAnchor = AnswerAnchor.BelowCenter)`).
  Sein Aufgabenblock ist leer — kein Titel, keine Kacheln, kein Wort; der Speaker
  steht wie überall in der Kopfzeile darüber
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
- **Schrift (verbindlich).** Zwei gebündelte Schriften, erzeugt von `tools/fonts/build_fonts.py`
  (Quellen und Befehl im Skriptkopf), Lizenztexte unter `assets/licenses/`:
  - **Silbo Fibel** (`SilboFibel`, `res/font/silbo_fibel_*.ttf`) für alles, was Lerninhalt ist —
    Buchstaben, Silben, Wörter, Ziffern, Rechenzeichen. Abgeleitet von Andika (SIL OFL), auf Latin
    gekürzt und **umbenannt**, weil Andika „Andika" und „SIL" als Reserved Font Names führt.
    Einstöckiges a und g wie in der Schule, l mit Bogen am Fuß, und das große **I** ist ein
    schlichter Strich wie in der Fibel und im Spurensucher (Andikas I hat Querstriche). So ist ein
    I nie mit einem l zu verwechseln — das Pfad-Schild „I o" las sich in Roboto als „lo". Die
    Zeilenmaße sind auf die Latin-Glyphen zusammengezogen (1,25 em statt Andikas 1,61 em mit
    Platz für vietnamesische Doppelakzente), sonst wäre jeder Text ein Drittel höher geworden.
  - **Baloo 2** (`SilboUi`, `res/font/baloo2.ttf`, variabel) für UI-Beschriftung: Punktestand,
    Knöpfe, Eltern-Bereich, Jubel-Titel — dieselbe runde Schrift wie Store-Grafik und Icon.
  - Rollen: `display*`/`headline*`/`body*` = Silbo Fibel, `title*`/`label*` = Baloo 2. Wer
    Lerninhalt in eine Titel-Rolle setzt (Pfad-Schild, Fresser-Fallbackwort, Zeilennummern der
    Malmatrix), überschreibt die Familie dort ausdrücklich. Keine Systemschrift mehr: bis
    September 2026 lief alles in Roboto (doppelstöckiges a/g, I = l) und System-Serif.
  - Die Strichdaten des Spurensuchers sind gegen Silbo Fibel geprüft und decken sich mit den
    Großbuchstaben; ein neues Graphem wird gegen die Schrift gezeichnet, nicht gegen Roboto.
- Buttons: keine Emojis — nur ASCII oder Canvas/SVG-Vektoren. Punkte-/Erfolgs-Symbol ist der Vektor-Stern `IconStar`, kein Text-Asterisk. Icons zeichnet die App selbst (`ui/components/AbcIcons.kt`); die eine Ausnahme ist das **native Overflow-Icon** der Elterntür (`Icons.Rounded.MoreVert` aus `material-icons-core`, ausdrücklich im Version-Catalog deklariert) — an ihm sollen Erwachsene ein Menü erkennen, und das leistet nur das Systemzeichen.
- Übungen nutzen `ExerciseStage` für klare Trennung Speaker-Kopfzeile / Aufgabenblock /
  Antwortblock. Der Speaker geht in den Slot `promptChrome`, nie in `prompt` — nur der
  Slot garantiert die feste Höhe aus §7.
  Der Parameter `answerAnchor` ist mit `Bottom` vorbelegt; `BelowCenter` ist die
  eine benannte Ausnahme (§9, Satz-Versteher). Zweite benannte Ausnahme ist
  `promptAboveAnswers`, mit `false` vorbelegt und nur beim Laut-Fresser `true`
  (§9), weil dort abwärts statt aufwärts gezogen wird.
- **Papiergrund (verbindlich).** Der Trainer-Grund ist eine Fläche des Babbel GDS,
  Familie `paper green`, und er ist **kein Volltonfeld**, sondern ein radialer Verlauf von
  `PaperCenter` (#F8F9F8) in der Mitte nach `PaperEdge` (#C5CDC9) an den Rändern —
  Lichtpunkt auf 42 % der Höhe, Radius 78 % der Höhe, gezeichnet in `TaskShell`.
  Die frühere flache Cream-Fläche (#FBF3E4) ist damit abgelöst; die Konstantennamen mit
  `Cream`-Präfix sind geblieben, tragen aber Papiertöne.
  Zwei Regeln hängen daran und dürfen nicht stillschweigend gebrochen werden:
  1. `PaperEdge` wird **nicht** dunkler. Kacheln der Jagd streuen bis in die Ecken, und
     dort muss der Grund die 3:1 für UI-Bauteile noch tragen: bei `green 400` sind es
     3.04:1, eine Stufe tiefer nur noch 2.85:1.
  2. Kleiner Fließtext in `WarmMuted` gehört in die Bildmitte, nicht an den Rand — auf
     `PaperEdge` liegt er bei 3.03:1 und damit unter der 4.5:1 für Kleintext.
  Die Ringe der Jagd sind gegen diesen Grund kalibriert (`TilePalette` in
  `SymbolHuntTrainer`, rund 11 % dunkler als die App-Akzente): schwächster Ring 3.83:1,
  gemessen gegen den Grund am Ort der jeweiligen Kachel. Wer den Grund ändert, ändert
  die Ringe mit. Die verworfene `paper blue`-Fassung und die ursprünglichen Cream-Werte
  stehen als Kommentar in `Color.kt`.
- Farbrollen (verbindlich): `StarGold` = Sterne/Punkte/Belohnung (`StarGoldDeep` als Kontur-/
  Tiefton für den Stern-Glyph auf hellem Grund), `LeafGreen` = richtig/erledigt, `SkyBlue` =
  Fortschritt/aktiv, `SunCoral` = Handlungs-CTA (auch der „Du bist hier“-Marker auf dem Pfad —
  er sagt „hier tippen“), `ClayRed` = Fehlertext (Erwachsene).
  `LeafGreenLight`/`SkyBlueLight` sind helle Ring-Varianten ausschließlich für Akzente AUF
  dunklen Flächen (Bretter der Pfad-Schilder, ↻-Abzeichen) — nie als Fläche oder Akzent auf
  dem Papiergrund. Die fünf Klotztöne der Pfad-Schilder (`SignBlockTones`) tragen keine
  Bedeutung, sie unterscheiden nur Laute; Schrift darauf in `WoodDark` liegt über 8:1.
  Eine Bedeutung pro Farbe — Sterne und Progress greifen nie auf `primary` zu.
  Benannte Ausnahme: die **warm gelaufenen Trittspuren** des Pfades nutzen ein transparentes
  StarGold (§5 verlangt „wärmer", und ein kaltes SkyBlue widerspräche dem) — das ist eine
  Landschafts-Färbung, kein Präzedenzfall für „Gold = Fortschritt" im UI-Chrome.
  Die beiden Laut-Fresser tragen `SkyBlue` (links) und `SunCoral` (rechts) — bewusst weder
  `LeafGreen` noch `StarGold`, damit keine Seite ‚richtig' aussieht. Bauch und Glyph sind
  eine helle bzw. dunkle Stufe der Körperfarbe (`FeederPalette`, Glyph ≥ 3:1 auf dem Bauch),
  ebenso Licht und Schatten ihres Verlaufs: die Figur ist ein Körper, keine Collage aus
  Fremdfarben.
- Haptik-Vokabular `AbcHaptics` (tick/success/celebrate/nudge): tick = kleiner Sammel-Erfolg
  (Trace-Stern, Jagd-Treffer, Einrasten), success = Aufgabe richtig, celebrate = Lektions-/
  Muschel-Feier, nudge = sanfte Korrektur. Haptik ergänzt Ton, ersetzt ihn nie.
  Der `SuccessBurst` am Trainer-Ende vibriert **nicht**: er folgt oft direkt auf den
  Trainer-eigenen Puls, und zwei Vibrationen hintereinander sind zu viel — dort trägt
  der Chime allein.
- **Motion-Tokens (`AbcMotion` in `ui/theme/Motion.kt`).** Jede Animation wählt ihre
  Dauer, Easing und Feder aus einer festen Palette: sechs Dauerstufen (90 · 170 · 260 ·
  360 · 450 · 600 ms, Faktor ~1,4), drei Schleifen-Perioden (Puls, Atmen, Wippen), vier
  Easings (Enter/Exit/Linger/Fill) und sieben benannte Federn (Settle, Soft, Bouncy,
  Glide, Pop, Wobble, Snap). Federn für alles, was das Kind anfasst, Tweens für alles,
  was nur passiert. Eine neue Feder am Aufrufort bricht `AbcMotionTest`; Figurenspiel
  (Kau-/Spuck-Rhythmus der Fresser, Halte-Kurve des Jagd-Druckmorphs) darf eigene Dauern
  tragen und begründet sie dort. Bewegung bleibt Antwort auf eine Tat — die Palette ist
  keine Einladung, Bühnen beim Laden aufzubauen oder dauerhaft zu animieren. Benannte
  Ausnahme: die Würfel-Drehung des aktuellen Pfad-Schilds (`SignTurnChoreo`, Feder `Soft`),
  vom Nutzer ausdrücklich gewählt; sie ersetzt den Atem-Ring dort, statt eine Schleife
  hinzuzufügen.
- **Der Erfolgs-Stern fliegt in den Punktestand** (`StarFlight`, `SuccessBurst`, gezeichnet
  mit `drawGlowStar`). Seit September 2026 ein Stern mit Licht statt eines flachen Sterns
  mit dunkler Kontur: gerundete Spitzen, innen hell und außen bernstein, Glanzpunkt, weicher
  Schein. Er steigt unter dem Lautsprecher leicht gedreht auf und federt ein (`Settle`), ein
  Lichtring läuft aus, vier Glanzfunken blitzen versetzt (statt acht bunter Punkte), ein
  Glanzstreifen zieht über ihn. Nach dem Stehen (450 ms) fliegt er im Bogen mit Funkenspur
  hinauf, dreht sich einmal und schrumpft auf die Größe des kleinen Sterns; erst beim
  Einschlag springt die Zahl in der Kopfzeile, der Zähler wird gestaucht (breiter und
  flacher, Feder `Bouncy`) und es funkelt kurz um ihn. Bis dahin zeigt die Kopfzeile den alten Stand,
  obwohl der Punkt schon verbucht ist — ein Kind, das nicht zählt, sieht so, *wohin* sein
  Stern geht. Ohne gelegten Punktestand (letzte Runde vor dem End-Screen) schrumpft der
  Stern wie früher an Ort und Stelle.
- Erfolgsmomente: SuccessBurst (leuchtender Stern + Glanzfunken, ohne Haptik), Gold-Puls an
  der Segmentgrenze je Trainer, das Sternbild auf dem End-Screen.
- **Lektions-Ende: das Sternbild** (`RewardSummaryScreen`, `FinaleConstellation`). Der
  End-Screen spielt am Abendhimmel des Pfads (ohne Sonne und Laternen — dort gehört der Himmel
  dem Sternbild, und eine helle Scheibe neben dem Lautsprecher läse sich wie ein Knopf). Die
  Sterne fliegen einzeln von oben an ihren Platz und bilden einen **Buchstaben**, Linien
  zeichnen ihn in Schreibrichtung nach, dann leuchtet er einmal auf. Der Buchstabe passt zum
  Finale-Satz **und** wurde in der Lektion geübt: der erste Anfangsbuchstabe eines Satzworts,
  der ein geübter Einzelbuchstabe ist, sonst ein geübter Buchstabe, der im Satz vorkommt,
  sonst der Anfangsbuchstabe des ersten Bild-Nomens (Nutzerentscheidung). „Geübt" heißt: in
  der gerade gespielten Lektion, gesucht über ihre ID — acht Finales gehören zwei Lektionen
  (f-l18: l18 und l26), die erste Lektion zum Finale wäre die falsche. Die Sterne kommen
  aus den Spurdaten des Buchstabens (vereinfacht, Ecken bleiben, gemeinsame Enden werden ein
  Stern). Bilder und Satz liegen auf einer hellen Karte, die Bilder hüpfen einmal, während der
  Satz gesprochen wird. Nach dem Aufbau funkeln die Sterne leise weiter (Größe ±8 %, Drehung
  ±10°, ab und zu ein Glanzlicht); ein angetippter Stern springt größer und schaukelt aus — auch beim Nachzeichnen des Buchstabens mit dem Finger, dann mit aufsteigenden Tönen wie die Sterne im Spurensucher.
  Unten stehen Lautsprecher und ein großer runder grüner Pfeil (88 dp, `AbcContinueButton`)
  nebeneinander. „Super gemacht!" steht oben, für den Erwachsenen.
  **Kein Konfetti** mehr: die Quadrate in vier Rollenfarben verwässerten Grün und Gold.
- **Shape-Morph beim Einrasten (Squish-Settle).** Rastet ein Wort in einen Peg des
  Satz-Architekten **oder ein Baustein in einen Rahmen des Wort-Bauers**, quetscht
  das Bauteil horizontal und federt in Form zurück: `scaleX`
  0,91 → über 1,0 → 1,0, `scaleY` gegenläufig 1,05 → 1,0, Eckradius +8dp über den
  Ruheradius des Bauteils (Peg 16dp, Rahmen 22dp) und zurück, eine
  Feder mit `dampingRatio = 0.42` und `Spring.StiffnessMediumLow`, synchron zur
  `tick`-Haptik. Dieselbe Tat bekommt dieselbe Antwort, darum liegen Werte und
  Feder **einmal** in `SlotFillMorph`/`rememberSlotFillSettle` und nicht als Kopie
  je Trainer; Filmstreifen zum Beurteilen in `SentenceOrderMorphShotTest` und
  `WordBuildMorphShotTest`. Das ist die Material-3-Expressive-Idee „Form folgt Zustand" mit
  Bordmitteln — **kein** `androidx.graphics.shapes`-Polygon-Morph, weil eine blobbige
  Zielform das Wort im Peg beschneiden würde. Gegenläufiges Y ist Pflicht, sonst
  liest die Bewegung als Zoom statt als Quetschung. Nur die eigene Tat federt: nach
  „Auflösen" fallen alle Pegs still, ein Chor aus Wacklern wäre eine Feier für etwas,
  das das Kind nicht geschafft hat. Gelesen wird die Feder in der **Zeichenphase**
  (`graphicsLayer` / `drawBehind`), damit 300ms Bewegung nicht 300ms rekomponieren
  und die registrierten Drop-Zonen nicht unter dem Finger wandern.
- **Ein Trainer, der geladen wird, animiert nichts** — er ist einfach da. Bewegung
  ist Antwort auf eine Tat des Kindes; eine Bühne, die sich beim Laden selbst
  aufbaut, liest als Fehler. Praktische Falle dabei: `AnimatedContent` merkt sich
  seinen Zustand in einem ungekeyten `remember`, und der Aufrufort überlebt den
  Rundenwechsel, solange zwei Trainer desselben Typs aufeinander folgen (Wort-Bauer
  36× im Pack). Der Rundenschlüssel muss deshalb auch die Transition keyen
  (`key(roundKey)` in `WordBuildTrainer`), sonst spielt die neue Runde den Eintritt
  ihrer leeren Platzhalter ab. Gegen die Rückkehr steht
  `WordBuildRoundSwitchTest`.
- **Druck-Morph der Jagd-Kacheln (Aufblähen, Plopp, Wegploppen).** Eine Kachel der
  Buchstaben-/Silben-Jagd ist eine weiche Kugel: Schattierung im Kreis (heller Kern oben
  links, satter Rand, Glanzpunkt, Innenschatten am Rand) statt flacher Fläche. Unter dem
  Finger bläht sie sich auf — Feder auf +6 %, dann verzögert weiter bis zum harten Deckel
  **+10 %**, wo sie stillsteht. Länger Drücken wächst also immer langsamer und nie weiter:
  unbegrenztes Wachsen verdeckt die Nachbarkacheln (der Streuabstand ist nur 0,22 der
  kurzen Feldseite) und belohnt Draufhalten statt Suchen. Beim Loslassen kollabiert der
  Kreis in 90ms unter seinen Ruhedurchmesser und ploppt federnd in Form zurück; die
  **eingesammelte** Kachel kollabiert genauso, verlässt das Feld aber ploppend (170ms auf
  Maßstab und Deckkraft null) statt schlagartig zu verschwinden. Der Innenschatten nimmt
  mit dem Druck zu — dadurch liest die Bewegung als eingedrückte Kugel und nicht als Zoom
  (gleiche Pflicht wie das gegenläufige `scaleY` oben). Unangetastet bleiben der Rand
  (3dp volldeckend, er trägt die 3:1-Grenze für UI-Bauteile) und die mittlere Deckkraft
  der Wäsche (0,22, wie vorher die flache Fläche). Die Ripple auf der Kachel entfällt: der
  Morph *ist* die Druckantwort. Werte und Begründung in `HuntTileMorph`, Filmstreifen zum
  Beurteilen in `SymbolHuntMorphShotTest`.

- **Nachtwelten (seit September 2026).** Jeder Trainer bekommt eine eigene, **dunkle** Welt,
  immer dieselbe — das Kind erkennt die Aufgabe am Ort, bevor es die Ansage hört
  (`TrainerWorld`): Jagd → **Tiefsee**, Spurensucher → **Dschungel bei Nacht**,
  Rechnen → **Nacht am Waldrand**, Wort-Detektiv → **Dachboden**, Wort-Bauer → **Werkstatt**,
  Satz-Architekt → **Garten**, Satz-Versteher → **Puppentheater**, Laut-Fresser → **Pilzhöhle**,
  Silben-Verschmelzer → **Waldlichtung**. Regeln:
  1. **Lerninhalt nur auf Licht.** Buchstaben, Silben, Wörter und Ziffern stehen in Tinte auf
     einer hellen Fläche — Blase, Weg, Karte, Klotz (≥ 7:1). Der Hintergrund trägt nie
     Lerninhalt; die Kontrastrechnungen der Lernschrift gelten deshalb unverändert.
  2. **Ruhige Mitte durch Tiefe**, nicht durch Leere: Bilder sind asymmetrisch, die Mitte liegt
     weit weg im Dunkel oder Nebel. Bilddateien kommen abgedunkelt und mit beruhigter Mitte
     eingebacken als WebP (Dschungel: 57 KB statt 1,9 MB PNG).
  3. **Kopfzeile in Creme** (`ChromeColors`/`NightChrome` über `LocalChromeColors`): Pfeil und
     Zahl Creme, Fortschritt `SkyBlueLight`, Chevrons Creme 60 %, der Lautsprecher wird ein
     heller Knopf mit Leuchtring — auf Papier bleibt alles wie bisher (`PaperChrome`).
  4. **Die Welt atmet, der Inhalt steht.** Umgebungsbewegung ist erlaubt, wenn sie sehr langsam
     ist (Zyklen ab ~8 s), kontrastarm und nie auf den Licht-Inseln; bei „Bewegung reduzieren"
     (`ANIMATOR_DURATION_SCALE` 0) steht alles still. Die Weltuhr (`rememberWorldSeconds`)
     läuft über `withInfiniteAnimationFrameMillis`, damit Compose-Tests nicht auf Ruhe warten.
  - **Tiefsee** (Jagd): ohne Bilddatei gezeichnet (`DeepSeaLight`, `WorldBackground`) — Verlauf,
    sieben Lichtstrahlen, deren Winkel, Breite, Länge, Helligkeit und **Lichtkern** (die hellste
    Stelle im Querschnitt) an eigenen Sinuswellen mit 19–47 s Periode hängen, wie Licht unter
    einer leicht bewegten Wasseroberfläche; Blasen steigen 0,4–1 % der Höhe pro Sekunde. Die
    Jagd-Kugeln sind **helle Luftblasen** mit farbigem Ring (`TilePalette`, helle Stufen, auf dem
    Meer ≥ 3,9:1) und Leuchten.
  - **Herzmuschel statt Batterie** (`CockleShell`, `PearlFlight`): von oben gesehen, gerippt.
    Jeder Treffer fliegt **im selben Frame** als Perle in der Farbe seines Blasenrings in die
    Muschel, die dafür aufklappt (Deckel nach hinten über das Scharnier, innen Perlmutt mit
    einer Mulde je gesuchtem Symbol), und federt danach zu. Die Ringfarben haben damit eine
    Aufgabe: „das ist deine Perle". Nach 6 s ohne Tipp lugt die Muschel halb auf, danach alle
    10 s — damit das Kind sieht, wie viele Perlen es hat. Voll: sie bleibt offen und leuchtet,
    dann geht es automatisch weiter. Nach „Zeig mir" zeigt sie nur die selbst gefangenen Perlen.
    Muschel und Perlmutt tragen Sand- und Rosatöne — weder Gold (Stern) noch Grün (richtig).
  - **Licht-Inseln** (`ui/world/LightIsland.kt`): `lightIsland` (Karte), `lightPlate` (Teller
    unter einem Bild), `lightPool` (weicher Lichtfleck). Sie werden **um** das Bauteil gezeichnet,
    nicht als Hülle — Layout und Größenrechnungen der Trainer bleiben unberührt. Eine Insel
    steht in der Modifier-Kette **vor** jeder Deckkraft (`alpha`), sonst zeichnet sie in deren
    Ebene und wird an den Kanten des Bauteils eckig abgeschnitten.
  - **Nacht am Waldrand** (Rechnen, gezeichnet): kein Mond (eine helle Scheibe neben dem
    Lautsprecher las sich wie ein zweiter Knopf), Sterne (wenige
    funkeln mit 8–14 s), Hügel und Bäume unten. Die Zahlentürme stehen auf ihrer hellen Platte,
    Antwortkacheln und Ziffernblock sind ohnehin hell.
  - **Dachboden** (Wort-Detektiv, gezeichnet): Dachbalken, rundes Fenster, Mondstrahl mit
    langsam treibendem Staub (17–31 s). Das Wort liegt im Lichtfleck, das Zielpaar („P / p")
    auf einer hellen Pille; gefundene Buchstaben fliegen und landen in `StarGold` (auf Dunkel
    ≈ 8:1), die Quittungs-Striche sind Creme.
  - **Werkstatt** (Wort-Bauer, gezeichnet): dunkles Holz mit Maserung, warmer Lampenkegel, der
    ganz langsam atmet (9 s). Das Bild steht auf einem Teller, die Bausteine sind Ahorn-Klötze
    mit Schatten, das fertige Wort liegt auf einer Karte.
  - **Antippen macht Freude, auch ohne Aufgabe.** Was ein Kind in einer Welt anfasst, antwortet
    mit einer kleinen, weichen Bewegung — nie mit Stern oder Ton, und nie so, dass es die Aufgabe
    stört: Himmelslaternen auf dem Pfad fliegen einen Looping (`LanternLoops`, 2,8 s, Drehung um
    die eigene Mitte), Karten auf der Wäscheleine schaukeln an ihrer Klammer, die Laut-Fresser
    stauchen sich und schaukeln aus. Tipps auf die Welt selbst (`WorldTaps`: was kein Bauteil der
    Aufgabe verbraucht hat und kein Ziehen war) beantwortet der Hintergrund: in der Tiefsee
    steigen Bläschen vom Finger auf, auf der Lichtung weichen Glühwürmchen aus und kehren zurück,
    im Theater bauscht sich der Vorhang auf der Seite des Tipps, auf dem Dachboden wirbelt Staub,
    in der Werkstatt schwingt die Lampe, in der Pilzhöhle flammt der nächste Leuchtpilz auf und
    stößt Sporen aus. Alles klingt in 2–5 s aus; bei „Bewegung reduzieren" passiert nichts.
  - **Garten in der blauen Stunde** (Satz-Architekt, gezeichnet): Sterne, ein warmer
    Horizontstreifen, Hügel mit runden Baumgruppen. Oben steht das Bild auf einem Teller,
    darunter spannt sich eine **Wäscheleine** über die ganze Breite zwischen zwei Pfosten
    (`ClothesLine`); jeder Peg hängt mit einer Holzklammer an seinem Punkt der durchhängenden
    Leine, leere Plätze sind gestrichelte, fast durchsichtige Umrisse. Der Durchhang verschiebt
    den ganzen Peg samt Tipp- und Ablagefläche (Versatz beim Platzieren, nicht im
    `graphicsLayer`), sonst nähme ein mittlerer Peg Tipps bis 14 dp über seinem Bild an. Eine frisch aufgehängte
    Karte schwingt wie ein Pendel nach (8°, langsame Feder `Glide`; 4° auf `Wobble` las sich
    wie Zittern), jeder Tipp stößt sie erneut an. Bei langen Sätzen berühren sich Nachbarn
    dabei kurz, das ist an der Leine erlaubt. Lange Sätze dürfen über die Pfosten hinaus bis 8 dp vor den Bildschirmrand
    hängen (`SentencePegSizing.solveOnLine`): nur wenn der Glyph auf der Bühne unter 20 dp
    fiele, sonst bleibt die Reihe auf der Bühne. „der Schneemann ist groß" kommt so auf einem
    360-dp-Gerät auf rund 18 statt 15 dp.
  - **Puppentheater** (Satz-Versteher, gezeichnet): dunkler Bühnenraum, roter Samtvorhang
    links und rechts, der ganz leicht atmet (7 s), ein schmaler Lambrequin unter dem
    Lautsprecher, darüber eine Lichterkette, deren Birnen langsam glimmen (6 s). Die zwei
    Bildkarten sind **gerahmte Bilder** (helle Fläche, Holzrahmen, richtig = `LeafGreen`) und
    stehen auf Bühnenbrettern, die der Trainer unter die Kartenreihe zeichnet.
  - **Pilzhöhle** (Laut-Fresser): `world_cave.webp` (30 KB), vom Nutzer generiert, abgedunkelt
    und mit beruhigter Mitte eingebacken. Die zwei Fresser sind **neu gezeichnet**
    (`FeederCreature`, `FeederShape`): **Pilli** (links, blau) rund mit zwei Öhrchen, **Kora**
    (rechts, koralle) tropfenförmig mit Blattspross — zwei Silhouetten, die ein Kind auch ohne
    Farbe unterscheidet. Volumen durch Licht oben links und Lichtkante, Augen mit zwei
    Glanzpunkten, die zur Karte hinaufschauen und zeitversetzt blinzeln (5,5 / 6,7 s), Wangen,
    ein Maul mit Zunge, Füße und Ärmchen; die Figuren atmen leicht (3,6 s). Beim Antippen
    stauchen sie sich kurz und schaukeln auf einer weichen Feder um die Füße aus (kein
    Tween-Zickzack), beim Spucken schütteln sie den Kopf auf einer schnelleren Feder. Sie
    stehen 40 dp über dem unteren Bühnenrand; durch die Höhle schweben langsam Leuchtsporen
    (türkis, wenige bernstein, Bahnen 28–52 s), vor allem an den Rändern und unten. Der Bauchfleck ist
    schmaler als beim alten Geisterkörper (62 % der Breite, Glyph-Boden 10 sp).
  - **Waldlichtung** (Silben-Verschmelzer, gezeichnet): Glühwürmchen treiben auf Bahnen von
    20–40 s und glimmen langsam, unten Gras als Silhouette. Die Silben sind die **zwei hellsten
    Lichter**: heller Kern, weicher Hof, der mit dem Zieh-Fortschritt heller wird (trägt die
    Verstärkung, die das TTS nicht kann); die Spur dazwischen sind wandernde Funken. Das
    verschmolzene Licht bekommt den grünen Rand „richtig".
  - **Dschungel** (Spurensucher): `world_jungle.webp` aus dem Capybara-Experiment des Nutzers.
    Der Weg ist hell (`RoadLight`, fertig `RoadDone`) mit dunklem Schattensaum gegen das Bild; das
    Fahrzeug ist ein **Leuchtkäfer** (Glühwürmchen sind Käfer und leben im Regenwald), der zum
    nächsten Stern schaut. Im Bild treiben blinkende Glühwürmchen (kurz hell, lange dunkel,
    Bahnen 26–46 s), nur am Rand und unten, nie über dem Weg. Der Käfer ist von oben gezeichnet mit sechs Beinen, orangefarbenem Halsschild mit
    dunklem Fleck, hell gerandeten Flügeldecken und leuchtendem Hinterleib — ohne Beine las er
    sich auf dem Kopf stehend wie ein Mensch von hinten. Im Stand tasten die Fühler, die Beine
    treten leicht, der Hinterleib glimmt; fährt er, laufen die Beine im Dreifußgang mit. Die **Sterne bleiben** — „Punkte" wären schwerer zu erklären. Die
    Belohnung („M wie Mond") steht auf einer hellen Karte.
- **Name: „Silbo", Store-Titel „Silbo – ABC Vorschule".** Ein Kunstname aus der Silbe, weil
  die Silbe die Mechanik ist, die das Kind in den Lektionen erlebt (Verschmelzer, Jagd,
  Wort-Bauer) — Pfad und Hügel sind nur Start-Screen-Motive und taugen deshalb nicht als
  Namensquelle. Unter dem Icon steht nur „Silbo" (`app_name`): kurz genug für jede
  Schriftskalierung. Der beschreibende Teil „ABC Vorschule" lebt ausschließlich im Store-Titel
  (Play Console, Gedankenstrich, Leerzeichen statt Bindestrich, damit „ABC" und „Vorschule" als
  eigene Suchbegriffe zählen) und passt zum „ABC" auf dem Schild des Icons. Verworfen:
  beschreibende Namen („ABC Vorschule" allein: generisch, nicht schützbar, deckelt die App auf
  Vorschule, obwohl L30+ Erstklässler-Stoff ist), Landschaftsnamen (Lesehügel, Buchstabenpfad),
  lateinische Diminutive (Literula, Vokula). Gleichnamige Apps anderer Branchen existieren
  (Geldtransfer, Klinik-SaaS) — keine im Kinder- oder Lernbereich.
- **Launcher-Icon = der Pfad, verkleinert.** Ein Adaptive Icon aus drei Vektor-Ebenen
  (`res/drawable/ic_launcher_*.xml`): hinten die Taglandschaft des Pfad-Screens (Himmel,
  Sonne, Hügel, Baum aus den `PathBackground`-Farben), vorn das Holzschild aus
  `PathSignNode` mit „ABC" in SoftSand auf WoodMid, hand-genagelt schräg, Stern der
  geschafften Lektion an der Ecke; dazu eine Monochrom-Ebene für Themed Icons (Brett und
  Pfosten, Buchstaben ausgestanzt). Kein eigenes Marken-Motiv, keine Fremdfarben: das
  Icon zeigt genau das, was das Kind nach dem Tippen sieht. Die Buchstaben sind Baloo 2
  ExtraBold (OFL) als Pfade; Erzeugung im Kommentar des Vordergrund-Drawables. Verworfen:
  ein „A mit Schallwellen" (austauschbar) und drei bunte Jagd-Kugeln A/B/C (bricht die
  Farbrollen, zerfällt bei 48 dp).



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
Random, kein Shuffle — nach demselben Muster wie `content/LessonSign.kt`, das die
Klötze und Bilder der Pfad-Schilder genauso deterministisch aus der Lektion ableitet.

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
| Hält jede autorierte Lektion die sechs Trainer-Typen in nicht-fallender Rangfolge (Start Visueller Spurensucher, Ende Rechnen)? | Ja → Validator prüft das             |
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
