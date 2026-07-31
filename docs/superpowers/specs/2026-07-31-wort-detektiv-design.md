# Wort-Detektiv — Design

Status: `design-approved`
Datum: 2026-07-31

Ein siebter Trainer-Typ: **„Finde den Buchstaben / die Silbe im Wort."** Er läuft direkt
nachdem die Lektion ein Wort eingeführt hat, und verlangt vom Kind, das gesuchte Symbol
in genau diesem Wort wiederzufinden.

Das ist der Transfer, der nach dem Wort-Bauer fehlt: dort *baut* das Kind das Wort aus
vorgegebenen Klötzen, hier muss es ein Symbol in einem fertigen Wort **erkennen** — ohne
Schablone, die die Position schon verrät.

## 1. Einordnung

- **Kind:** `symbol_in_word`, deutscher Name **Wort-Detektiv**.
- **Position:** direkt nach dem letzten `word_build`-Trainer der Lektion, also zwischen
  Wort-Bauer (4) und Satz-Architekt (5).
- **Herkunft:** vollständig zur Laufzeit abgeleitet, wie die Buchstaben-/Silben-Jagd.
  Kein autorierter Content, kein `ContentValidator`-Eingriff, keine 65 neuen Task-Blöcke.
  Greift automatisch in allen autorierten Lektionen und in jeder neuen.
- **Umfang:** eine Runde pro eingeführtem Wort, dedupliziert über `targetAtomId`
  (L05 baut „Hut" in zwei `word_build`-Tasks — das gibt eine Runde, nicht zwei).
  Ergibt real 1–4 Runden pro Lektion, meist 2–3.

Der `ContentValidator` braucht keine Änderung: er prüft nur autorierte `taskIds`, und
`symbol_in_word` steht wie `symbol_hunt` nicht in `TrainerOrder`. Der neue `TrainerKind`
bricht dagegen absichtlich die `when`-Exhaustiveness in `TaskSpec.kind`, `TaskSpec.rounds`,
`TrainerRound.scoredAtomIds` und `TrainerHost` — genau die Compile-Time-Sicherheit, die
der Sealed-Kommentar in `content/TaskSpecs.kt` verspricht.

## 2. Zwei Modi

| Modus | Zerlegung des Wortes | Prompt-TTS |
| --- | --- | --- |
| **Buchstabe** | alle Grapheme (§3) | `O·m·a` → „Finde den Buchstaben – O – im Wort – Oma." |
| | | `P·a·p·a` → „Finde **alle** Buchstaben – P – im Wort – Papa." |
| **Silbe** | die autorierten `word_build`-Blöcke | `Ro·se` → „Finde die Silbe – se – im Wort – Rose." |
| | | (Mehrzahl-Vorlage; mit aktuellem Content ungenutzt, siehe §4) |

Die Beispiele sind echte abgeleitete Runden (Anhang), keine erfundenen Illustrationen.
Insbesondere ergibt `Oma` unter der Alternierung aus §4 eine Buchstaben-Runde auf `O`,
nicht die Silben-Runde auf `ma` — die Silben-Runde der Lektion trägt `Mimi`.

### Anzeige des Zielsymbols

**Buchstaben-Modus zeigt beide Formen: `P / p`.** So lernt das Kind im selben Moment, wie
Groß- und Kleinform aussehen, in dem es beide im Wort finden soll — und die Aufgabe ist
nicht mehr schwerer als sie aussieht (vgl. §4).

Regel: `Atom.display` und `Atom.display.lowercase()`, getrennt durch einen Schrägstrich,
**wenn sie sich unterscheiden** — sonst nur die eine Form. Das trifft von allein das
Richtige, weil der Content nur-klein geschriebene Grapheme auch so autoriert:

| Fokus-Atom | Anzeige | |
| --- | --- | --- |
| `P`, `Sch`, `Ei`, `Au`, `Qu`, `Pf`, `St`, `Sp`, `Ch`, `Ä`, `X`, `Y` | `P / p`, `Sch / sch`, … | beide Formen kommen im Wortschatz vor |
| `ck`, `ß` | `ck`, `ß` | `display` ist schon klein; eine Form `Ck` existiert im Deutschen nicht |

Der Schrägstrich ist kein Lesezeichen, sondern ein Trenner: `MutedText` @ 45%, halbe
Glyphgröße, damit die beiden Buchstaben dominieren.

**Silben-Modus zeigt nur die Kleinform**, aus `Atom.display` des Silben-Atoms (`ma`, `mi`,
`schu`) — **nicht** aus `WordBlock.display`. Bei Buchstaben ist das Formenpaar echter
Fibel-Inhalt; bei Silben wäre eine Großform eine erfundene Variante, die nur existiert,
weil die Silbe zufällig am Wortanfang steht. Der Silben-Verschmelzer erzeugt Silben
ebenfalls klein — der Trainer bleibt damit konsistent zum restlichen Content-Graph.

Das gesuchte **Wort** kommt aus `Atom.display` des `targetAtomId` (bei allen Atomen
identisch mit `lemma`, also auch für TTS gültig).

In beiden Modi bleibt der Treffervergleich case-insensitive: `Mi` im Wort `Mi·mi` ist ein
Treffer für das Ziel `mi`, `p` in `P·a·p·a` einer für `P / p`.

Der Silben-Modus nutzt bewusst die autorierten Blöcke statt einer Silbentrennung: sie sind
genau die Klötze, die das Kind im Wort-Bauer eben in die Hand genommen hat.

Er ist aber nur zulässig, wenn ein Block ein Atom mit `kind: syllable` referenziert. Die
`word_build`-Blöcke sind **keine verlässlichen Silben** — `Häuser` → `Hä·u·s·e·r`,
`Hallo` → `Ha·l·l·o`, `Qualle` → `Qua·l·le`. „Finde die Silbe **l** im Wort Hallo" wäre
Unsinn. `Atom.kind` trennt das sauber, ohne dass irgendwo Silbenlogik geraten werden muss.

## 3. Graphem-Tabelle: aus dem Pack, lektionsbeschränkt

Die multi-letter Grapheme werden **aus den `letter`-Atomen des Content-Packs** abgeleitet,
deren `display` länger als ein Zeichen ist — und nur aus Lektionen mit `index <= aktuelle
Lektion`. Das Pack liefert damit: `Ei, Ch, Au, Sch, Eu, ck, Pf, St, Sp, Qu`. Matching ist
case-insensitive und longest-match-first.

**Die Lektionsbeschränkung ist keine Feinheit, sondern Korrektheitsbedingung.** L07 baut
„Nest", das Fokus-Graphem ist `S`. Mit einer globalen Tabelle würde `st` zu *einem*
Segment verschmelzen (`N·e·st`) — das `S` wäre nicht mehr antippbar und die Runde
unlösbar. `St` wird erst in L17 eingeführt, also splittet L07 korrekt `N·e·s·t`, während
L17 „Stern" als `St·e·r·n` zeigt. Dasselbe Muster trägt `Sp` (L07 „Nest" vs. L17
„Spinne") und `ck` (L08 „Keks" vs. L16 „Sack").

Warum abgeleitet statt hartkodiert wie in `SoundWordSegments`:

- `Erdbeere` → `E·r·d·b·e·e·r·e`, weil `ee` kein Atom des Packs ist. Ein Kind, das „alle E"
  sucht, tippt zwei separate Buchstaben statt eines verschmolzenen `ee`-Blocks.
- `Apfel` → `A·pf·e·l`. Die hartkodierte Liste in `SoundWordSegments` kennt kein `pf` und
  hätte in L16 (Fokus `Pf`) eine unlösbare Runde erzeugt.
- Ein spanisches Pack, das `ll` als Atom autoriert, bekommt `ll` als eine Einheit ohne
  Codeänderung — und `l` bleibt in Wörtern ohne `ll` ein einzelner Buchstabe.

Neue Datei `content/WordGraphemes.kt`. **`SoundWordSegments` bleibt unangetastet:** der
Auditive Finder färbt damit nur drei Waggons ein, dort ist die Tabelle unkritisch, und
eine Änderung würde seine Tests ohne Gegenwert mit anfassen.

## 4. Zielwahl — deterministisch, kein Zufall

Eingabe: die `word_build`-Rounds der Lektion in Autorierungsreihenfolge, dedupliziert
über `targetAtomId`.

**Guard vor allem anderen:** Wörter, deren Buchstaben-Split weniger als 2 Segmente
ergibt, fallen weg. Sonst entsteht in L22 die Runde „Finde Ei im Wort Ei" — das Wort ist
die Antwort, das Kind kann nicht danebentippen, die Runde trägt nichts bei.

**Derselbe Guard gilt für die fertige Runde: sind *alle* Segmente Treffer, wird sie nicht
gestellt.** Das ist derselbe Defekt in anderer Verkleidung. `Mimi` → `Mi·mi` mit dem Ziel
`mi` hat zwei Treffer bei zwei Segmenten: das Kind kann nicht danebentippen, kein Fehltipp
kann für die Adaptivität gemeldet werden, und „Zeig mir" ist unerreichbar. Die Prüfzeile
„Kann die Aufgabe überhaupt fehlschlagen?" in den Produktprinzipien verlangt das Gegenteil.

Eine so verworfene Silben-Runde fällt in den Buchstaben-Modus, und das Ergebnis ist
didaktisch besser: L02 stellt statt `mi` in `Mi·mi` dann **alle I in `M·i·m·i`** — zwei
Treffer bei vier Segmenten, am Fokus-Graphem der Lektion, und fehlschlagbar. Bleibt auch
im Buchstaben-Modus jedes Segment ein Treffer, fällt die Runde ganz weg.

Folge fürs Vokabular: mit dem aktuellen Content bleibt die Mehrzahl-Vorlage
„Finde alle Silben …" ungenutzt — kein Wort hat zwei gleiche Silben *und* eine dritte, die
keine ist. Die Vorlage bleibt trotzdem stehen; sie ist für künftigen Content korrekt.

Dann, über die verbleibenden Wörter mit Index `i` (0-basiert). **`i` zählt produzierte
Runden, nicht betrachtete Wörter** — ein per Guard verworfenes Wort darf den Modus des
nächsten nicht umkippen. Mit dem aktuellen Content fallen beide Zählweisen nur bei L22
auseinander, aber die Regel ist die Absicht, nicht der Zufall.

**Gerades `i` → Buchstaben-Modus.**
Ziel ist das nächste Fokus-Graphem der Lektion, rotierend über die Buchstaben-Runden:
die `letter_trace`-Glyphen in Autorierungsreihenfolge, ab der Position nach dem letzten
verwendeten, zyklisch. Der Rotationszeiger ist pro Lektion, nicht global. Übersprungen
werden Grapheme, die im Wort **nicht als Segment vorkommen**.

Kommt **kein** Fokus-Graphem im Wort vor, **fällt die Runde weg.** Kein Rückfall auf
„das erste Segment des Wortes": ein Ziel ohne Fokus-Atom hätte keine Atom-ID zum Scoren,
und ein nullbares `targetAtomId` wäre ein untestbarer Defensivpfad im Datenmodell für
einen Fall, den der aktuelle Content nie erzeugt. Bleibt dadurch keine Runde übrig, wird
der Trainer gar nicht eingefügt — dieselbe stille Degradierung wie bei einer Lektion
ohne `letter_trace` in `SymbolHuntInsertion`.

**Ungerades `i` → Silben-Modus,** wenn das Wort einen Block mit `kind: syllable` hat.
Ziel bevorzugt die Fokus-Silbe der Lektion (`syllable_merge.resultAtomId`), sonst der
erste Silben-Block. Ohne Silben-Block fällt die Runde in den Buchstaben-Modus.

**Zusätzliche Bedingung: der Block muss so heißen wie sein Atom.** Stimmt
`WordBlock.display` nicht (case-insensitiv) mit `Atom.display` überein, fällt die Runde in
den Buchstaben-Modus. Der Content hat genau einen solchen Fall: `l17-t8` autoriert
`{atomId: "spi", display: "Spin"}` — vier Buchstaben unter einem dreibuchstabigen
Silben-Atom. Ohne diese Bedingung stünde `spi` als Ziel über einem Segment `Spin`, und ein
Kind, das nicht liest, müsste etwas antippen, das anders aussieht als das Gesuchte. Da das
Label laut §2 aus dem Atom kommt und nicht aus dem Block, ist die Runde in diesem Fall
nicht ehrlich beschriftbar — also wird sie nicht gestellt. L17 spielt stattdessen eine
Buchstaben-Runde auf `Sp` in `Sp·i·n·n·e`.

Daraus folgt die Invariante, die die Aufgabe überhaupt visuell lösbar macht und die ein
Test festhalten muss: **das angezeigte Zielsymbol kommt in jeder Runde wörtlich (bis auf
Groß-/Kleinschreibung) als Segment vor.** Sie ist stärker als „`targetIndices` ist nicht
leer" — ein Treffer, den das Kind nicht als das Gesuchte erkennt, ist keiner.

**Treffer sind immer *alle* Vorkommen des Ziels**, case-insensitive verglichen.

### Groß- und Kleinschreibung ist Absicht

`Papa` → „Finde alle Buchstaben **P**" verlangt Tipps auf `P` *und* `p`. Das ist
didaktisch gewollt: dass Groß- und Kleinform derselbe Buchstabe sind, ist Fibel-Inhalt.

Damit die Aufgabe aber nicht schwerer ist als sie aussieht, **zeigt der Screen beide
Formen als Paar** (`P / p`, §2). Das Kind muss die Gleichsetzung nicht mitbringen — es
lernt sie in genau dem Moment, in dem es sie braucht.

Betroffen sind real: `Mama`/M (2), `Papa`/P (2), `Keks`/K (2), `Mimi`/I (2).

## 5. Screen

Echte Runde aus L03: `Papa`, Ziel `P`, zwei Treffer.

```
        ⌂ ⋯    ★ 12               ← bestehendes TaskShell-Chrome
             ‹     ›
        ▁▁▁▁▁▁▁▁▁▁▁▁▁▁

               (())                ← Speaker, AbcSpeakerButton
                                      via TaskPromptChrome(title = null)

             P / p                 ← Zielsymbol als Formenpaar, 54sp

          P   a   p   a            ← das Wort, Segment = eigene Farbe, klickbar

             __   __               ← ein Platzhalter-Strich je Treffer
```

**Speaker** über allem im Aufgabenbereich, per `TaskPromptChrome(title = null, …)` —
identisch zur Buchstaben-Jagd, konform zu Prinzip 7 („Speaker mittig über dem
Aufgabentitel"). Er wiederholt den Prompt-TTS.

**Zielsymbol** darunter, `AbcDimens.letterSp` (54sp), `SoftSand`, als Formenpaar nach der
Regel aus §2. Antippbar → wird vorgelesen (Prinzip 7), gesprochen wird dabei die
Atom-Form einmal, nicht „P Schrägstrich p".

**Das Wort** als farbige Glyphen **ohne Rahmen** — es soll wie ein Wort aussehen, nicht
wie ein Tray. Farben zyklisch aus `SoftMint · SoftCoral · SoftSky · SoftGold · SoftSand`;
periodische Wiederholung ist ausdrücklich erlaubt. Bei 8 Segmenten (`Xylophon`) wiederholt
sich die Palette also — kein Problem, weil die Farbe nur Segmentgrenzen markiert und keine
Bedeutung trägt.

Jedes Segment hat eine unsichtbare Trefferfläche. Breite und Glyphgröße kommen aus
`WordFrameSizing` (84 → 56dp Rahmen, 46 → 20sp Glyph). Reicht die Breite nicht, bricht die
Zeile in zwei ausgeglichene Reihen, statt unter die 56dp-Trefferfläche zu schrumpfen:
**Klickbarkeit vor Einzeiligkeit.**

**Die Umbruchschwelle hängt an der Gerätebreite, nicht an einer Segmentzahl.** Bei 56dp
Mindestbreite plus 4dp Lücke passen sechs Segmente erst ab etwa 356dp nutzbarer
Bühnenbreite — die erreicht nur ein Gerät ab ~420dp. Auf einem Pixel 7 (393dp) bleiben
329dp, also fünf Segmente pro Reihe, und `Häuser` (`H·ä·u·s·e·r`, 6 Segmente) bricht dort
um. Der zweizeilige Fall ist damit **kein Zukunftspfad, sondern Alltag**. Eine umgebrochene
Reihe ist deshalb 64dp hoch statt 80dp (`WordFrameSizing.rowHeightDp`) — immer noch über
dem 56dp-Boden, aber 32dp sparsamer pro Wort.

**Bekannte Restlücke, bewusst offen:** auf der 640dp-Höhenklasse reicht das noch nicht.
`ExerciseStage` clippt seinen Aufgabenblock nicht und scrollt nicht, sondern Compose
klemmt die Höhe der *letzten* Kind-Komponente — also des Wortes. In L12 („Häuser",
6 Segmente) rendert die zweite Reihe dort ~30dp hoch, mit abgeschnittenen Glyphen und
Trefferflächen unter dem 56dp-Boden; erscheint zusätzlich „Zeig mir", verschwindet sie
ganz. Der Trainer braucht ~674dp Gerätehöhe (mit „Zeig mir" ~744dp), vorher waren es
~706dp/~776dp. Betroffen ist eine Lektion auf einer kurzen Gerätequelle; die saubere
Lösung (Aufgabenblock gegen die gemessene Resthöhe dimensionieren oder `ExerciseStage`
scrollen lassen) betrifft **alle** Trainer und gehört in eine eigene Änderung —
siehe `docs/residual-review-findings/feat-wort-detektiv.md`.

**Die Platzhalter-Striche** im Antwortblock: ein 3dp-Strich in `MutedText`, so viele wie
es Treffer gibt, Breite aus der Zeichenzahl des Zielsymbols geschätzt (`GlyphAspect`, die
Konvention von `WordFrameSizing`), mindestens 40dp. Eine echte Textmessung wäre genauer,
aber der Strich muss nur *relativ* stimmen — `Sch` sichtbar breiter als `e` —, und die
Schätzung nutzt dieselbe Konstante, gegen die der Wort-Bauer seine Rahmen schon rechnet. Keine Rahmen,
keine Füllung, nichts von dort wegziehbar.

Warum Striche statt der Batterie aus der Jagd:

1. Sie skalieren ehrlich von 1 bis x. Ein einzelner Strich liest sich als „hier fehlt
   eins"; eine einzelne Batteriezelle als Ein/Aus-Lampe. Bei kurzen Wörtern ist ein
   Treffer der Normalfall.
2. Der Strich trägt die **Breite des Graphems**. Damit sagt das Layout vorsprachlich:
   `Sch` ist *ein* Ding, nicht drei. Die Batterie kann das nicht ausdrücken — und genau
   das ist der Fall, der für ein späteres spanisches `ll` sauber sein muss.
3. Die Flugbewegung (§6) ist die Begründung: ein Kind, das nicht liest, sieht „ich habe
   *das* getippt, und *das* ist dorthin gewandert".
4. Dass die Anzahl der Striche verrät, wie viele zu finden sind, ist gewollt — dieselbe
   Information, die die Batterie gab, und das einzige Signal, an dem ein Kind merkt, dass
   es fertig ist.

**Abgrenzung zu den Schablonen des Wort-Bauers:** der nutzt gerundete 22dp-Rahmen mit
Border und ein Tray darunter. Hier gibt es nur Grundstriche, kein Tray, und die einzige
Quelle für Symbole ist das Wort selbst. Die Striche sind Quittungen, keine Wahloptionen.

**Hilfestufen** (Prinzip 6, `scaffoldFor(atomId)`):

- `ScaffoldLevel.Beginner` → das Zielsymbol liegt als Silhouette (18% Alpha) im Strich
- `ScaffoldLevel.Advanced` → nackter Strich

## 6. Interaktion

Ein Tipp auf ein bereits eingesammeltes Segment ist vollständig wirkungslos — kein Treffer,
kein Fehltipp, und auch keine Sprachausgabe. Es ist kein antippbares Item mehr (Prinzip 7
gilt für handlungsfähige Items), sondern die Quittung, dass es schon gefunden wurde.

**Richtiger Tipp**

1. Segment wird vorgesprochen.
2. Glyph im Wort dimmt an seiner Stelle auf `MutedText` @ 35% und ist nicht mehr
   klickbar — das ist gleichzeitig die Completed-Farbe und die „schon erledigt"-Anzeige.
3. Eine Kopie fliegt in ~350ms auf den nächsten freien Strich und bleibt dort in
   `SoftGold` liegen — dieselbe Farbe wie Stern und Punkte: „verdient".

**Was genau fliegt: die Form des Zielsymbols, nicht die des angetippten Segments.**
Beide unterscheiden sich nur in der Groß-/Kleinschreibung (die Invariante aus §4 garantiert
das), aber die Striche sind die Quittung auf *eine* Frage. „Finde alle **P**" soll unten als
zwei P enden, nicht als `P` und `p`; und in `Mimi` darf unter dem Label `mi` kein `Mi`
liegen, sonst widerspricht die Quittung der Aufgabe. Für Buchstaben ist das zusätzlich
genau die Lektion, die das Formenpaar `P / p` sowieso vermittelt: beide Vorkommen sind
dasselbe P.

Die fliegende Kopie wird in der Glyphgröße des Segments gestartet und auf die Größe des
Strichs interpoliert, damit sie beim Abflug nicht größer ist als das Zeichen, aus dem sie
kommt — bei langen Wörtern sind die Segmente kleiner als die Striche.

**Falscher Tipp**

1. Segment wird vorgesprochen. Das *ist* das Fehlerfeedback — kein Lesesatz
   (Prinzip 7), und das Kind hört den Unterschied zum gesuchten Laut.
2. Das Segment dreht sich einmal um 360° um seinen eigenen Mittelpunkt, ~450ms. Wird die
   Drehung durch einen Tipp auf ein *anderes* falsches Segment unterbrochen, muss das erste
   in seine Ausgangslage zurückgesetzt werden — sonst bleibt es schief stehen.
3. `HapticFeedbackType.LongPress`, wie in der Jagd.
4. Nichts geht verloren: kein Strich, keine Punkte, keine Strafe.

**Runde vollständig** (letzter Strich gefüllt)

Die Striche pulsieren ~900ms golden (`CelebrationHoldMs`, dieselbe Dauer wie die
Batterie-Feier der Jagd), dann `onResult(correct = true)` → die bestehende
Erfolgspipeline: Antwort vorsprechen → Stern → nächste Runde. **Kein „Weiter"-Button** —
das Kind kann ihn nicht lesen.

**„Zeig mir"**

Nach 6 aufeinanderfolgenden Fehltipps (`ResolveThreshold`, geteilt mit der Jagd) taucht
`AbcResolveButton` auf. Tippen darauf setzt alle Ziele **gedimmt und ohne Flug** in ihre
Striche — `MutedText`, nicht `SoftGold` — und meldet
`onResult(correct = false, resolved = true)`: keine Punkte, und die aufgelöste Antwort
trägt nicht die Belohnungsfarbe (Prinzip 8, „Auflösen ist nicht grün").

**Ohne Flug ist Absicht.** Der Flug ist die Choreografie des Verdienens (§5, Punkt 3);
ihn beim Auflösen abzuspielen würde genau das Gefühl erzeugen, das Prinzip 8 der
Auflösung verweigert. Die Antwort erscheint einfach, sie wird nicht errungen.

**Scoring:** `scoredAtomIds()` liefert die Ziel-Atom-ID, wie bei `SymbolHuntRound`.
Nur der erste Fehltipp einer Runde wird für Adaptivität gemeldet — sonst würde ein Kind,
das sich durch ein 8-Segment-Wort tippt, seine Statistik für dieses Atom ruinieren.
Die Zählung der **aufeinanderfolgenden** Fehltipps läuft davon unabhängig weiter, auch
nachdem das Melden gestoppt hat: sie steuert nur die Resolve-Freigabe, nicht die
Statistik. Genau diese Trennung macht `SymbolHuntProgress` über die beiden Outcomes
`Miss` und `MissAlreadyReported`; sie wird hier übernommen.

**Koordinatenraum für den Flug:** `ExerciseStage` legt Aufgaben- und Antwortblock in zwei
getrennte `Column`s, ein Flug muss über beide hinweg animieren. Der Trainer wickelt
`ExerciseStage` daher in eine `Box`; Segment- und Strichpositionen werden per
`onGloballyPositioned`/`positionInWindow` gemessen und gegen die Position der Box
verrechnet. Der fliegende Glyph ist ein Overlay-Kind dieser Box.

## 7. Aufteilung

| Datei | Zweck | Compose-frei |
| --- | --- | --- |
| `content/WordGraphemes.kt` | Pack-abgeleitete Graphem-Tabelle, `split(word, lessonIndex)` | ja |
| `content/SymbolInWordDerivation.kt` | Guard, Modus-Alternierung, Zielwahl, Round-Bau | ja |
| `session/SymbolInWordInsertion.kt` | Einfügen nach dem letzten `word_build` | ja |
| `ui/exercise/SymbolInWordProgress.kt` | Tap-Logik, Trefferzustand, Resolve-Freigabe | ja |
| `ui/exercise/SymbolInWordTrainer.kt` | Screen, Farben, Animationen | nein |
| `content/TaskSpecs.kt` | `SymbolInWordSpec`/`SymbolInWordRound`, Enum, `when`-Zweige | ja |
| `session/SessionViewModel.kt` | zweiter Insertion-Aufruf | — |

`SymbolHuntInsertion` bleibt unverändert; `SessionViewModel` ruft beide Insertions
hintereinander. Die Einfügepositionen sind voneinander unabhängig, weil `word_build`
im `TrainerOrder` immer hinter `letter_trace` und `syllable_merge` liegt — die
Reihenfolge der beiden Aufrufe ist damit irrelevant.

Die vier Compose-freien Kernstücke folgen derselben Trennung wie
`SymbolHuntProgress`/`SymbolHuntTrainer`: die Logik ist ohne Compose-Testrunner prüfbar,
der Screen enthält keine Entscheidungen.

## 8. Tests

**`WordGraphemes`**

- `Erdbeere` → 8 Segmente (`ee` bleibt getrennt)
- `Nest` in L07 → `N·e·s·t`; `Stern` in L17 → `St·e·r·n` (die Lektionsbeschränkung)
- `Apfel` → `A·pf·e·l`
- `Häuser` → `H·ä·u·s·e·r` (`äu` ist kein Atom, `Au` matcht `äu` nicht)
- longest-match: `Sch` vor `S` in `Schaf`

**`SymbolInWordDerivation`**

- **Über alle autorierten Lektionen: jede abgeleitete Runde ist lösbar** — das Ziel kommt
  mindestens einmal als Segment vor. Dieser Test fängt genau den `Nest`/`st`-Fehler aus §3,
  falls jemand später die Lektionsbeschränkung entfernt.
- Modus-Alternierung: gerade Indizes Buchstabe, ungerade Silbe wenn möglich
- Fallback in den Buchstaben-Modus ohne Silben-Block (`Tom`, `Hut`)
- Fokus-Rotation: L01 ergibt `M` dann `A`, nicht zweimal `M`
- Rotation überspringt im Wort fehlende Grapheme: L06 „Tor" ergibt `R`, nicht `N`
- Guard: L22 „Ei" erzeugt keine Runde
- Dedup: L05 „Hut" erzeugt eine Runde
- Wort ohne Fokus-Graphem erzeugt keine Runde; bleibt keine übrig, wird kein Trainer
  eingefügt (synthetische Lektion, da der echte Content den Fall nicht hat)
- Mehrfachtreffer: `Papa`/P = 2, `Mimi`/mi = 2
- Anzeige des Zielsymbols: `P` → `P / p`, `Sch` → `Sch / sch`, `ck` → `ck` (einzeln),
  `ß` → `ß` (einzeln), Silbe `mi` → `mi` (nie `Mi`)

**`SymbolInWordProgress`**

- Treffer auf einem bereits erledigten Segment ist ein No-Op, kein zweiter Strich
- Fehltipp verliert keinen Fortschritt
- nur der erste Fehltipp einer Runde wird gemeldet
- Resolve nach 6 aufeinanderfolgenden Fehltipps, Reset bei jedem Treffer
- letzter Treffer ergibt `RoundComplete`

**`WordFrameSizing`**

- Umbruchgrenze: 6 Segmente bleiben einzeilig, 8 (`Xylophon`) brechen um

## 9. Was bewusst nicht drin ist

- **Kein Drag & Drop.** Das Kind tippt, das Symbol fliegt selbst. Ziehen wäre die Geste
  des Wort-Bauers und des Silben-Verschmelzers; hier ist die Aufgabe Erkennen, nicht
  Platzieren.
- **Keine Distraktoren.** Die falschen Antworten sind die anderen Segmente des Wortes —
  echte, bereits geübte Atome, wie Prinzip 2 verlangt. Nichts wird erfunden, nichts
  zusätzlich eingestreut.
- **Keine Batterie.** Siehe §5.
- **Kein Umsortieren der Palette pro Runde.** Die Farbfolge ist fix, damit ein Kind, das
  eine Lektion wiederholt, dasselbe Bild sieht.

## 10. Doku-Folgeänderungen

- `docs/PRODUCT_PRINCIPLES.md` §3: den Wort-Detektiv als zweiten abgeleiteten
  Zusatz-Trainer neben der Jagd aufnehmen, inklusive der Regel „eine Runde pro
  eingeführtem Wort".
- `docs/PRODUCT_PRINCIPLES.md` §9: der Antwortblock trägt hier Quittungs-Striche statt
  Wahloptionen — dritte Ausnahme neben der Jagd, ausdrücklich benannt.
- `AGENTS.md`: Trainer-Typen-Kurzfassung ergänzen.

## Anhang: abgeleitete Runden über den aktuellen Content

Simuliert aus `tasks.json`/`atoms.json`/`lessons.json`. Dient als Fixture-Referenz für
die Derivation-Tests. `B` = Buchstaben-Modus, `S` = Silben-Modus.

| Lektion | Runden (Modus · Ziel · Zerlegung · Treffer) |
| --- | --- |
| L01 M & A | B `M` `M·a·m·a` 2 · B `A` `m·a` 1 |
| L02 I & O | B `O` `O·m·a` 1 · B `I` `M·i·m·i` 2 |
| L03 P & T | B `P` `P·a·p·a` 2 · S `pa` `O·pa` 1 · B `T` `T·o·m` 1 |
| L04 L & H | B `L` `L·a·m·a` 1 · B `H` `H·a·l·l·o` 1 |
| L05 F & U | B `U` `H·u·t` 1 · B `F` `U·f·o` 1 |
| L06 R & N | B `R` `r·o·t` 1 · B `R` `T·o·r` 1 |
| L07 S & E | B `S` `N·e·s·t` 1 · S `se` `Ro·se` 1 |
| L08 D & K | B `D` `D·o·s·e` 1 · B `K` `K·e·k·s` 2 |
| L09 Ei & W | B `Ei` `Ei·s` 1 · B `Ei` `K·l·ei·d` 1 · B `W` `W·o·l·k·e` 1 |
| L10 G & Ch | B `G` `W·e·g` 1 · B `Ch` `D·a·ch` 1 · B `Ch` `i·ch` 1 |
| L11 Au & B | B `Au` `H·au·s` 1 · B `B` `B·au·m` 1 |
| L12 Umlaute | B `Ä` `H·ä·u·s·e·r` 1 · B `Ä` `B·ä·u·m·e` 1 · B `Ü` `R·ü·b·e` 1 |
| L13 Sch | B `Sch` `Sch·u·h` 1 · B `Sch` `F·i·sch` 1 · B `Sch` `Sch·a·f` 1 · S `schu` `Schu·le` 1 |
| L14 J, Z & Eu | B `Z` `Z·e·b·r·a` 1 · B `Eu` `Eu·l·e` 1 |
| L15 ß & V | B `ß` `F·u·ß` 1 · S `vo` `Vo·gel` 1 · B `V` `V·a·s·e` 1 |
| L16 ck & Pf | B `ck` `S·a·ck` 1 · B `Pf` `A·pf·e·l` 1 · B `Pf` `Pf·e·r·d` 1 |
| L17 St & Sp | B `St` `St·e·r·n` 1 · B `Sp` `Sp·i·n·n·e` 1 |
| L18 C, Y, X & Qu | B `Qu` `Qu·a·l·l·e` 1 · B `X` `T·a·x·i` 1 · B `Y` `P·o·n·y` 1 |
| L19 M & A Wdh. | B `M` `M·a·m·a` 2 · B `A` `a·m` 1 |
| L20 I & O Wdh. | B `O` `O·m·a` 1 · B `I` `M·i·m·i` 2 |
| L21 P & T Wdh. | B `P` `P·a·p·a` 2 · S `to` `To·m` 1 |
| L22 Ei & Au Wdh. | „Ei" fällt per Guard weg · B `Au` `B·au·m` 1 |
| L23 Sch & Ch Wdh. | B `Sch` `Sch·u·h` 1 · S `fi` `Fi·sch` 1 |
| L24 St & Sp Wdh. | B `St` `St·e·r·n` 1 · S `spi` `Spi·n·n·e` 1 |
| L25 Ö & Ü Wdh. | B `Ö` `L·ö·w·e` 1 · S `rü` `Rü·b·e` 1 |
| L26 Qu & X Wdh. | B `Qu` `Qu·a·l·l·e` 1 · S `ta` `Ta·x·i` 1 |

L13 ergibt dreimal `Sch` in Folge, weil die Lektion nur ein Fokus-Graphem führt und die
Rotation nichts hat, worauf sie wechseln könnte. Das ist kein Fehler, sondern die
Lektion: L13 heißt „Sch (Der Dreifachlaut)".

L17 bekommt zwei Buchstaben-Runden statt einer Silben-Runde, weil `l17-t8` seinen
`spi`-Block als „Spin" schreibt — siehe die Block-Bedingung in §4. L24 („St & Sp
Wiederholung") autoriert denselben Block als „Spi" und spielt darum die Silben-Runde.
