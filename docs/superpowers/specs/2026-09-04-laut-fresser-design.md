# Laut-Fresser — Design

Status: `design-approved`
Datum: 2026-09-04

Ein dritter abgeleiteter Zusatz-Trainer: **„Füttere die Laut-Fresser."** Zwei hungrige
Wesen stehen unten auf der Bühne, jedes trägt einen Laut auf dem Bauch. Oben erscheinen
nacheinander Bildkarten; das Kind hört das Wort und zieht die Karte zu dem Fresser, dessen
Laut es darin hört. „Sonne" gehört zu **S**, „Schuh" zu **Sch**.

Das ist die Hör-Übung, die der App seit dem Ende des Auditiven Finders fehlt — ohne dessen
Fehler. Der Finder fragte **wo** im Wort ein Laut sitzt (Anfang/Mitte/Ende) und überforderte
die jungen Tester; bei Wörtern mit wiederholtem Laut war sein Scoring mehrdeutig. Der
Laut-Fresser fragt nur noch **welcher** von zwei Lauten — die Unterscheidung, an der
Vorschulkinder wirklich arbeiten.

## 1. Didaktischer Hintergrund

Die Erwerbsdaten für das Deutsche (Fox-Boyer) benennen, welche Laute spät kommen und welche
Vereinfachungen bis ins Vorschulalter physiologisch sind:

| Prozess | Beispiel | überwunden bis |
| --- | --- | --- |
| Vorverlagerung `sch` → `s` | „Sal" für Schal | 4;11 |
| Vorverlagerung `k`/`g` → `t`/`d` | „Tamm" für Kamm | 3;5 |
| Erwerb von `z` (ts), `ch` (ich) | „Suk" für Zug | 3;6–3;11 |
| Erwerb von `sch` | | 4;0–4;5 |
| Reduktion `pf` → `f` | „Ferd" für Pferd | 3;0–3;5 |

Der Heidelberger Lautdifferenzierungstest prüft dazu die Stimmhaftigkeitspaare (`d/t`,
`g/k`, `b/p`) und Paare mit unterschiedlicher Bildestelle (`t/k`, `m/n`). Daraus folgt die
Kontrast-Tabelle in §3.

Zwei Befunde prägen die Form:

- **Der Anlaut ist die hörbarste Position.** Inlaute sind für Vorschulkinder oft „fast
  unmöglich". Konsonantenpaare werden deshalb nur am Wortanfang gespielt.
- **Vokale sind der Silbenkern** und überall im Wort hörbar; für sie gilt die
  Anlaut-Beschränkung nicht. Weil die Frage nie „wo" lautet, wiederholt das nicht den Fehler
  des Finders.
- **Minimalpaare** (Fisch/Tisch, Kanne/Tanne) sind das schärfste Material: alles ist gleich
  außer dem Laut. Sie sind der Kern der Minimalpaar-Therapie und werden bevorzugt gezogen
  (§4).

## 2. Einordnung

- **Kind:** `sound_feeder`, deutscher Name **Laut-Fresser**.
- **Herkunft:** vollständig zur Laufzeit abgeleitet, wie Jagd und Wort-Detektiv. Kein
  autorierter Task-Block, kein `ContentValidator`-Eingriff. Der `ContentValidator` prüft nur
  autorierte `taskIds`, und `sound_feeder` steht wie `symbol_hunt` nicht in `TrainerOrder`.
- **Position:** direkt nach der Buchstaben-Jagd, also vor dem ersten Silben-Verschmelzer.
  Das Kind hat den neuen Buchstaben eben nachgezeichnet und „S wie Sonne" gehört; jetzt hört
  es ihn gegen einen Bekannten, bevor es ihn in Silben verbaut. Hören kommt vor Bauen.
  Anker: der Index nach dem letzten `SymbolHuntSpec` im Buchstaben-Modus, sonst nach dem
  letzten `LetterTraceSpec`. Der Einschub läuft in `SessionTrainers.assemble` **nach** den
  Jagden und **vor** dem Detektiv; keiner der drei Einschübe verschiebt den Anker eines
  anderen (Jagden hängen an Trace/Merge, der Detektiv an Word-Build, beide liegen hinter
  bzw. vor dem Fresser-Anker).
- **Umfang:** ein Trainer pro Lektion, **eine Runde**, ein Lautpaar, bis zu 7 Karten.
  Lektionen 1 und 2 bleiben frei — dort lernt das Kind noch die App.
- **Neuer `TrainerKind`** bricht absichtlich die `when`-Exhaustiveness in `TaskSpec.kind`,
  `TaskSpec.rounds`, `TrainerRound.scoredAtomIds`, `SuccessSpeech.partsForRound`,
  `TrainerHost` und den Prompt-Teilen in `SessionViewModel` — die Compile-Time-Sicherheit,
  die der Sealed-Kommentar in `content/TaskSpecs.kt` verspricht.

## 3. Welches Paar spielt eine Lektion

Die Paar-Tabelle ist kuratiertes didaktisches Wissen und steht im Code
(`content/SoundPairs.kt`), so wie die Graphem-Tabelle des Detektivs aus dem Pack kommt. Sie
hat drei Stufen; eine Lektion nimmt die erste Stufe, die etwas hergibt:

| Stufe | Paare (Tabellenreihenfolge) | Position im Wort |
| --- | --- | --- |
| **Kontrast** | S/Sch · S/Z · F/W · W/B · B/P · D/T · G/K · K/T · M/N · L/R · St/Sp · Pf/F | Anlaut |
| **Aufwärmen** | P/T · L/H · F/T · S/T | Anlaut |
| **Vokal** | Ei/Au · Ei/Eu · Ö/Ü · I/O | irgendwo |

**Auswahlregel** (`SoundFeederDerivation.assignments(pack)`, eine reine Funktion über die
Lektionen in Index-Reihenfolge, damit „schon gespielt" ohne Persistenz entscheidbar ist):

1. **Kandidat** ist ein Paar, wenn (a) einer seiner Laute Fokus-Atom der Lektion ist,
   (b) beide Laute bis einschließlich dieser Lektion eingeführt sind (`Lesson.index` des
   ersten `focusAtomIds`-Auftritts) und (c) der Wortvorrat reicht (§4: je Seite ≥ 2, zusammen
   ≥ 6).
2. **Kontrast** vor **Aufwärmen** vor **Vokal**. Innerhalb einer Stufe gewinnt das noch nie
   gespielte Paar in Tabellenreihenfolge; sind alle gespielt, das am längsten nicht
   gespielte (kleinster Lektionsindex des letzten Einsatzes), bei Gleichstand die Tabelle.
3. **Wiederholung:** Hat keine Stufe einen Kandidaten mit Fokus-Bezug, spielt die Lektion
   ein Kontrast-Paar, dessen beide Laute eingeführt sind — das am längsten nicht gespielte.
   Lieber S/Sch noch einmal mit anderen Karten als eine Lektion ohne Hör-Übung.
4. Bleibt nichts (nur L01/L02 per Untergrenze), gibt es keinen Fresser — dieselbe stille
   Degradation wie beim Detektiv.

Die Aufwärm-Paare sind keine echten Verwechslungen. Ihr Zweck: Ein Vierjähriges lernt in
L03 bis L07 die Monster und die Geste an leichten Kontrasten, statt in L08 zum ersten Mal
einem neuen Spiel **und** einem schweren Paar zu begegnen. Die Vokalpaare Ö/Ü und I/O sind
für deutschsprachige Kinder leicht, für Kinder mit anderer Erstsprache schwer — sie sind als
Lückenfüller ihrer Lektionen richtig, nicht als Kern.

Die vollständige Zuordnung über den heutigen Pack steht im Anhang und ist als Snapshot-Test
festgehalten.

## 4. Welche Karten

Wörter kommen aus dem Pack, nicht aus Autorierung. Ein Atom ist **kartentauglich**, wenn
es `kind` `word` oder `other` hat, ein `nounClass` trägt (also ein Substantiv ist —
Farbwörter, Verben, `ich`, `am` fallen weg), genau ein Emoji zeigt (`Bäume`, `Äpfel`,
`Eier`, `Häuser` mit zwei Glyphen fallen weg) und nicht auf der Ausschlussliste steht.

**Ausschlussliste** (`SoundPairs.excludedWords`): `Giraffe` — die Aussprache des `G`
schwankt zwischen [g] und [ʒ], das Kind darf nicht raten müssen, was der Buchstabe auf dem
Bauch bedeutet.

**Zuordnung zu einer Seite** über die Graphem-Segmentierung des Wortes (`sch`, `äu`, `ei`,
`au`, `eu`, `ie`, `ch`, `ck`, `pf`, `qu`, `st`, `sp`, `ß` als Einheiten, längster Treffer
zuerst — dieselbe Logik wie `WordGraphemes`, aber ohne Lektionsbeschränkung, denn hier wird
gehört, nicht gelesen):

- **Konsonantenpaar:** das erste Segment ist der Laut. `St`, `Sp`, `Sch` sind damit
  automatisch kein `S`-Anlaut, `Pf` kein `P`.
- **Vokalpaar:** der Laut ist irgendein Segment. `ie` zählt als `I`; `ei`/`eu`/`äu`/`au`
  zählen **nicht** als `e`, `i`, `u` oder `a`.
- **Der Partnerlaut kommt nirgends im Wort vor.** Für `S` zählen auch `ß`, `ss` und ein
  `st`/`sp`, das **nicht** am Wortanfang steht — dort ist es [ʃt] („Stern"), mitten im Wort
  aber [st] („Zahnbürste"). `sch`, `pf`, `ch`, `ck` enthalten dagegen weder S noch P noch C.
  „Zahnbürste" fliegt aus S/Z, „Sonnenblume" aus W/B, „Lennard" aus L/R. Die Karte darf dem
  Kind den anderen Laut nicht mitten im Wort vorspielen.

**Kartenzahl und Mischung:**

- Bis zu **7 Karten**, weniger nur wenn der Vorrat kleiner ist (St/Sp hat heute 4 + 2).
- **Jede Seite mindestens 2 Karten**, der Rest nach Vorrat; ungleiche Verteilung ist
  erwünscht — sonst könnte das Kind die letzten Karten abzählen.
- **Zwillinge zuerst:** Zwei Wörter des Vorrats, die nach dem Anlaut-Segment denselben
  Rest haben (`Fisch`/`Tisch`, `Sonne`/`Tonne`, `Kanne`/`Tanne`), werden bevorzugt gezogen
  und **direkt hintereinander** gelegt (Reihenfolge innerhalb des Paars gemischt). Der
  Fresser links kaut noch „Kanne", da kommt „Tanne" für rechts. Höchstens zwei Zwillingspaare
  pro Runde, damit die Runde nicht zur Reimübung wird.
- **Rotation statt Zufall.** Der Vorrat jeder Seite ist alphabetisch sortiert; jeder Laut
  führt über die Lektionen hinweg einen Zähler, wie oft er schon Karten gestellt hat, und die
  nächste Runde beginnt im Vorrat dort, wo die letzte aufgehört hat. So wandert das `T` durch
  P/T, F/T, S/T, D/T und K/T einmal durch alle 17 T-Wörter, und **jedes kartentaugliche Atom,
  das zu einem gespielten Paar gehört, kommt irgendwann auf eine Karte** — die Bedingung, die
  der bestehende Test „Kein Atom ohne Auftritt" (`LessonCoverageTest`) an die zehn neuen
  Atome stellt. Nur die **Reihenfolge** der Karten in der Runde ist gemischt, deterministisch
  aus der Lektions-ID. Eine wiederholte Lektion zeigt dieselben Karten (ein Kind, das
  wiederholt, sieht dasselbe Spiel); L23 zeigt für S/Sch andere Karten als L13.
- Keine Karte doppelt, keine Karte, deren Emoji ein anderes der Runde wiederholt (`Haus` 🏠
  und `Dach` 🏠 nie zusammen).

Der `S`-Anlaut ist im Deutschen stimmhaft ([z] in „Sonne"), der `S`-Laut-Clip spricht das
stimmlose [s]. Das ist die Konvention aller Anlauttabellen und bleibt so; der Fresser
erfindet keine eigene Lautung.

### Neue Atome

Zehn neue `other`-Atome erweitern den Vorrat dort, wo er dünn ist (`W`, `Pf`, `Ö/Ü`, `Z`)
oder ein Minimalpaar liefert. Jedes braucht Emoji, Genus, `nounClass: thing`,
`pluralDisplay` und einen kuratierten Wort-Clip aus der TTS-Pipeline:

| Atom | Emoji | Genus | Zweck |
| --- | --- | --- | --- |
| Turm | 🗼 | m | T-Vorrat; Zwilling zu Wurm (T/W ist kein Paar, aber der Rest ist nützlich) |
| Wurm | 🪱 | m | W-Vorrat (heute 4 Wörter) |
| Wanne | 🛁 | f | W-Vorrat; Zwilling zu Kanne/Tanne/Pfanne |
| Kanne | 🫖 | f | Zwilling zu Tanne — das klassische Lautdifferenzierungs-Item |
| Tanne | 🌲 | f | Zwilling zu Kanne, T-Vorrat |
| Pfanne | 🍳 | f | Pf-Vorrat (heute 2) |
| Pfeil | 🏹 | m | Pf-Vorrat; auch Ei-Vorrat |
| Möhre | 🥕 | f | Ö-Vorrat (heute 3) |
| Mütze | 🧢 | f | Ü-Vorrat |
| Ziege | 🐐 | f | Z-Vorrat |

Für „Wiege" gibt es kein brauchbares Emoji; das Paar Wiege/Ziege entfällt. Die Atome haben
sonst keinen Leser — Rechnen und Satz-Versteher referenzieren sie nicht, das ist in Ordnung
(`other` ist „Bild-Vokabular, nie gelesen oder buchstabiert").

## 5. Der Bildschirm

`ExerciseStage` mit `AnswerAnchor.Bottom`, Speaker im `promptChrome` wie überall.

**Aufgabenblock (oben, zentriert):**

- Die **aktuelle Bildkarte**: ein großes Emoji auf Papiergrund mit dem Rahmen der
  Satz-Versteher-Karten (`WarmMuted` @ 0.9, kein Füllton), Größe nach Bühnenbreite
  gerechnet wie `SentencePictureCardSizing`. Sie ploppt auf (Skalierung 0 → 1 mit Überschwung)
  und spricht dabei ihr Wort. Tipp wiederholt das Wort (Feedback-Kanal, würgt keine laufende
  Ansage ab).
- Rechts daneben, kleiner, der **Futterhaufen**: ein Stapel verdeckter Karten (bloße
  Rahmen mit versetzten Kanten), einer je noch ausstehender Karte. Er schrumpft mit jeder
  gefressenen Karte und ist der Rundenfortschritt, den das Kind ohne Zahl lesen kann.
- Der Block hält seine größte Höhe (`holdTallest`-Prinzip aus §9): ist die letzte Karte
  weg, bleibt die Fläche stehen, die Fresser rücken nicht nach oben.

**Antwortblock (unten):**

- **Zwei Laut-Fresser**, links und rechts, jeder mindestens `kidTouch × 2` breit. Ein
  Fresser ist eine runde, weiche Figur (Canvas, keine Emojis, kein Bild-Asset) mit großem
  offenem Maul oben, zwei Augen und dem Laut groß auf dem Bauch. Der Laut zeigt wie beim
  Detektiv **beide Formen** (`S / s`, `Sch / sch`), nur eine bei `ck`/`ß`; Vokalpaare ebenso
  (`Ei / ei`, `Ö / ö`).
- Ein kleines **Speaker-Icon** (`AbcSpeakerButton`-Glyph) sitzt am Fresser. Tipp auf Icon
  **oder** Figur spielt den Laut in Monster-Stimme (§7) und lässt die Figur kurz wackeln.
- Die Fresser sind in jeder Lektion **dieselben zwei Figuren** in zwei festen Farben (links
  und rechts, aus den Rollenfarben in PRODUCT_PRINCIPLES §10 — keine der beiden darf die
  Erfolgs-/Fehlerrolle tragen, sonst liest das Kind „grün = richtig" in eine Seite). Nur der
  Buchstabe wechselt. Wiedererkennen ist Teil des Spaßes.
- Beide Figuren sind `DropZone`s des vorhandenen `DragField`; die Karte ist die einzige
  `DragCard`. Loslassen außerhalb beider Zonen: Snap-back ohne Ton.

**Einstieg einer Runde:**

1. Ansage „Füttere die Laut-Fresser." (Primär-Kanal, kuratierter Prompt-Clip).
2. Der linke Fresser wackelt und macht seinen Laut, dann der rechte. Kein Fragesatz — die
   Rechenaufgabe bleibt die einzige Frage in der App (§7). Die Vorstellung ersetzt die Frage
   „Hörst du S oder Sch?" und zeigt zugleich, wer wofür steht.
3. Die erste Karte ploppt auf und spricht ihr Wort.

Die Interaktion ist bis zum Ende der Vorstellung gesperrt: ein Kind, das schon zieht,
während die Fresser sich vorstellen, hätte die Zuordnung nie gehört. **Der Trainer spricht
seine Ansage selbst**, nicht die Bühne (`TaskShell`): `currentPromptParts` liefert für den
Fresser eine leere Liste, damit die Bühne sofort entsperrt und nichts doppelt spricht, und
der Trainer hält eine eigene Sperre, bis Ansage, beide Vorstellungen und das erste Wort
durch sind. Nur so lässt sich das Wackeln der Figur mit ihrem Laut synchronisieren, und nur
so spielt ein Tipp auf den Speaker die **ganze** Vorstellung noch einmal, nicht nur den Satz.
Die Jagd spricht ihre Tipps aus demselben Grund selbst.

**Ohne deutsches TTS** steht das Wort als Text unter dem Emoji, damit ein Erwachsener
vorlesen kann — der visuelle Fallback aus §7. Ein Hörspiel ohne Ton ist sonst unspielbar;
der Text ist kein Verstoß gegen „das Kind liest nicht" und darf nicht als solcher entfernt
werden.

## 6. Fressen, Spucken, Sattwerden

- **Nähe:** Während die Karte gezogen wird, öffnet der Fresser, über dem sie gerade liegt,
  sein Maul weiter (Hover-Zustand aus dem Drag-Offset gegen die Zonen-Bounds). Beide Fresser
  öffnen sich gleich — die Nähe verrät nichts über richtig oder falsch.
- **Richtiger Fresser:** Die Karte skaliert ins Maul (≈ 250 ms), das Maul schließt sich, der
  Bauch wackelt zweimal (Kauen). Dazu **Laut in Monster-Stimme, dann Wort in normaler
  Stimme**: „Sss … Sonne." Das Wort kommt **ohne Artikel** — die Kopplung Laut → Wort ist
  der Lernmoment, ein Artikel dazwischen („Sss … die Sonne") würde ihn zerschneiden. Das ist
  eine benannte Ausnahme der Artikel-Regel aus §7, wie der Silben-Verschmelzer. Erst nach
  dem Wort ploppt die nächste Karte.
- **Falscher Fresser:** Das Maul klappt zu, die Figur schüttelt sich, „Bäh!" in
  Monster-Stimme. Die Karte hüpft zurück in die Mitte (Feder-Animation) und spricht ihr Wort
  noch einmal. Keine Strafe, kein rotes Blinken. Der **erste Fehlgriff der Runde** wird
  einmal als Miss gemeldet (`onResult(false, false, …)`), weitere nicht — dasselbe
  `reportedMissThisRound`-Muster wie in der Jagd.
- **Zweiter Fehlgriff bei derselben Karte:** Der richtige Fresser reißt das Maul weit auf,
  pulsiert und summt seinen Laut. Ein „Zeig mir" gibt es nicht — bei zwei Zielen ist der
  Hinweis bereits die Lösung. Der Hinweis bleibt, bis die Karte gefressen ist.
- **Letzte Karte weg:** Beide Fresser werden kugelrund (Skalierung 1 → 1.15), rülpsen
  nacheinander je ihren Laut in Monster-Stimme, der Futterhaufen ist leer. Nach
  `HuntCelebration.HoldMs` meldet der Trainer `onResult(true, false, [lautA, lautB])` und
  die normale Erfolgs-Pipeline mit Stern läuft. `SuccessSpeech` liefert für die Runde
  **keine** weiteren Teile — die Fresser haben schon gesprochen; die Pipeline verträgt eine
  leere Liste (`else -> emptyList()` heute für unbekannte Runden).
- **Scoring:** `scoredAtomIds` sind die beiden Laut-Atome. Sie haben Scaffolds über die
  bestehende `SessionTrainers`-Invariante; der Trainer liest sie nicht (kein Hilfestufen-Modus
  — es gibt nichts wegzublenden).
- **Chevrons** wechseln die Runde wie überall; da der Trainer eine Runde hat, überspringen
  sie ihn ganz.

## 7. Audio und Monster-Sprache

**Anforderung des Nutzers: Wir brauchen Monster-Sprache.** Umsetzung in zwei Schichten, weil
die Aussprache der Engpass der App ist und ein Kind, das genau diesen Kontrast lernt, ein
verzerrtes „sss" schlechter trifft als ein sauberes.

1. **Laute und Wörter bleiben die kuratierten Clips**, in Monster-Stimme **zur Laufzeit**
   verwandelt: der linke Fresser spielt tiefer (Tonhöhe ≈ 0.75), der rechte höher (≈ 1.3),
   Tempo unverändert. Artikulation und Dauer bleiben, nur die Tonhöhe kippt — zwei
   unterscheidbare Monster ohne eine neue Aufnahme. Technisch: `MediaPlayer.setPlaybackParams`
   mit `setPitch` (API 23+, minSdk ist 26) auf dem Clip-Pfad, `TextToSpeech.setPitch` auf dem
   Fallback-Pfad. Die TTS-Tonhöhe ist **engineweit** und muss nach jeder Äußerung auf 1.0
   zurück, sonst spricht die nächste Ansage im Monsterton.
   `SpeechController.speak`/`speakAndAwait` bekommen dafür einen Parameter
   `voice: VoiceStyle = Normal` (`Normal`, `MonsterLow`, `MonsterHigh`); `TrainerCallbacks`
   reicht ihn durch. Das Wort nach dem Laut läuft **normal**.
2. **Echte Monster-Sprache für die Reaktionen**, die nichts lehren müssen: „Bäh!" beim
   Ausspucken und „Mmmmh!" nach dem letzten Rülpser. Mehr Reaktions-Strings gibt es bewusst
   nicht — jede weitere Äußerung pro Karte verlängert die Runde, und das Rülpsen am Ende
   **ist** der eigene Laut in Monster-Stimme, kein extra Text. Die beiden Strings kommen in
   `tools/tts/extra-strings.json` mit `"field": "monsterTts"`, und die Pipeline bekommt ein Profil **`monster`** in
   `profiles.json` (eigene Sprechanweisung: knurrig, gutmütig, kurz; Sprecher offen — sohee
   soll ihre Tonlage als Lehrerin behalten, ein anderer Qwen-Sprecher ist für die Monster
   ausdrücklich erlaubt). Ob Qwen ein glaubwürdiges Monster liefert, entscheidet eine
   Kuratierungsrunde im TTS-Interface. **Die App muss ohne diese Clips spielen:** fehlt der
   Clip, spricht die System-TTS den String mit Monster-Tonhöhe.

Weitere Strings in `extra-strings.json`: die Ansage „Füttere die Laut-Fresser."
(`promptTts`). Buchstaben-Laute (`phoneme`-Profil über `Atom.lemma`) und Wörter
(`word`-Profil) haben ihre Clips; die zehn neuen Atome bekommen ihre Wort-Clips im selben
Pipeline-Lauf wie die Monster-Reaktionen.

Kanäle: Ansage und Fress-Sequenz („Sss … Sonne") laufen auf `Primary` als Sequenz
(`speakAndAwaitSequence`); Karten-Tipp und Fresser-Tipp auf `Feedback`, damit sie eine
laufende Ansage nicht abwürgen. Der Miss-Ton „Bäh!" plus Wortwiederholung ebenfalls
`Primary`, weil danach ohnehin nichts anderes spricht.

## 8. Technik

Compose-freie Kerne, damit die Logik ohne Emulator prüfbar ist, Screens ohne Entscheidungen:

| Datei | Aufgabe |
| --- | --- |
| `content/SoundPairs.kt` | die drei Stufen der Paar-Tabelle, Ausschlussliste, Segmentierung für Anlaut/Enthalten, Kartentauglichkeit |
| `content/SoundFeederDerivation.kt` | `assignments(pack)`: Lektion → Paar; `buildRound(pack, lesson, pair)`: Kartenwahl, Zwillinge, Mischung → `SoundFeederRound` |
| `content/TaskSpecs.kt` | `SoundFeederSpec(id, rounds)`, `SoundFeederRound(promptTts, leftAtomId, rightAtomId, cards: List<SoundFeederCard(atomId, side)>)`, `TrainerKind.sound_feeder`, Erweiterung von `kind`/`rounds`/`scoredAtomIds` |
| `session/SoundFeederInsertion.kt` | Anker nach der Buchstaben-Jagd, Einhängen in `SessionTrainers.assemble` |
| `session/SuccessSpeech.kt` | leere Teile für `SoundFeederRound` |
| `content/SoundFeederSpeech.kt` | Ansage-Teile, Fress-Sequenz (Laut, Wort), Miss-Sequenz („Bäh!", Wort) |
| `ui/exercise/SoundFeederProgress.kt` | Zustandsautomat: `drop(side)` → `Eaten`, `RoundComplete`, `Miss`, `MissAlreadyReported`, `Hint`; hält Kartenindex, `missesOnCard`, `reportedMissThisRound` |
| `ui/exercise/SoundFeederTrainer.kt` | Screen: Stage, Karte, Futterhaufen, zwei `FeederCreature`s, Drag-Verdrahtung, Sequenz-Steuerung |
| `ui/exercise/FeederCreature.kt` | die Figur als Canvas: Körper, Maul (Öffnungsgrad 0..1), Augen, Bauch-Glyph, Speaker-Icon; Zustände idle/hover/chew/spit/hint/full |
| `ui/exercise/SoundFeederSizing.kt` | Karten- und Figurgrößen aus Bühnenbreite und `fontScale` |
| `ui/exercise/TrainerHost.kt` | Dispatch |
| `speech/SpeechController.kt`, `ClipPlayer.kt` | `VoiceStyle` und Tonhöhe |
| `assets/content/atoms.json` | die zehn neuen Atome |
| `tools/tts/extra-strings.json`, `profiles.json`, `ttskit/extract.py` | Ansage, Monster-Strings, Profil `monster`, Feldzuordnung `monsterTts → monster` |

`SoundFeederRound` erfüllt `TrainerRound` (`promptTts` = „Füttere die Laut-Fresser.") und
validiert im `init`: beide Seiten ≥ 2 Karten, keine Karte doppelt, `leftAtomId ≠ rightAtomId`.

Systemschriftgröße: Figur- und Kartenmaße rechnen mit `fontScale`; das Testgerät steht auf
1.3, und ein Bauch-Glyph, der bei 1.0 passt, darf bei 1.3 nicht aus der Figur laufen
(Deckelung wie `TaskPromptChrome.capEffectiveSize`).

## 9. Tests

**`SoundPairsTest`**

- Segmentierung: `Schuh` beginnt mit `sch`, nicht `s`; `Stern` mit `st`; `Pferd` mit `pf`
- Enthält: `Zahnbürste` enthält `S`; `Fußball` enthält `S` (ß); `Feuer` enthält kein `u`
  als Vokal, aber `eu`; `Biene` enthält `I` (ie)
- Kartentauglichkeit: `gelb`, `ich`, `Bäume` sind es nicht; `Ameise`, `Mama` sind es;
  `Giraffe` ist ausgeschlossen

**`SoundFeederDerivationTest`**

- **Snapshot der Zuordnung über alle 34 Lektionen** (Anhang) — bricht, wenn Content oder
  Tabelle die Reihenfolge verschieben, und zwingt zur bewussten Aktualisierung
- L01/L02 ohne Fresser; L03 P/T; L06 M/N vor L/R; L15 Wiederholung B/P; L21 wählt D/T,
  weil B/P in L15 lief; L30 wählt Ei/Eu, weil Ei/Au in L22 lief
- Jede Karte jeder Runde: Laut auf der richtigen Seite, Partnerlaut nirgends im Wort,
  Emoji genau eine Glyphe, keine Emoji-Doppelung in der Runde
- Jede Seite ≥ 2, Runde ≤ 7 Karten, St/Sp ergibt 6
- Zwillinge liegen nebeneinander: L05 F/T enthält `Fisch`/`Tisch` benachbart; höchstens
  zwei Zwillingspaare
- Deterministisch: zweimal ableiten ergibt dieselbe Runde; L13 und L23 (beide S/Sch) ergeben
  verschiedene Kartenmengen
- Jedes der zehn neuen Atome liegt in mindestens einer Lektion auf einer Karte (die
  Rotation aus §4 garantiert das; `LessonCoverageTest` zählt die Fresser-Karten als Auftritt)
- Vorrat-Guard: eine synthetische Tabelle mit einem Paar, das nur 1 Wort auf einer Seite
  hat, wird nie gewählt

**`SoundFeederProgressTest`**

- richtiger Drop entfernt die Karte und setzt `missesOnCard` zurück
- falscher Drop meldet `Miss` einmal pro Runde, danach `MissAlreadyReported`
- zweiter Fehlgriff derselben Karte liefert `Hint`; der Hinweis verschwindet mit dem Fressen
- letzter richtiger Drop ergibt `RoundComplete`
- Drop außerhalb beider Zonen ändert nichts

**`SoundFeederSpeechTest`**

- Ansage-Teile: „Füttere die Laut-Fresser." und danach die beiden Laut-Lemmata mit
  Stimmen links/rechts
- Fress-Sequenz: Laut (Monster) dann Wort (normal), **ohne Artikel**
- Miss-Sequenz: „Bäh!" dann Wort

**`SessionTrainersTest`** (bestehend) deckt Scaffolds für die neuen gescorten Atome ab;
ein Fall prüft die Position: der Fresser steht nach der Buchstaben-Jagd und vor dem ersten
`syllable_merge`.

**`VoiceStyleTest`**: die Monster-Tonhöhen liegen beidseits von 1.0, `Normal` ist genau 1.0.
Die Tonhöhe wird **je Äußerung** gesetzt (Clip-Player pro Wiedergabe, TTS-Engine vor jedem
`speak`), nie als Zustand zurückgesetzt — es gibt also keinen „vergessenen Reset", den ein
Test fangen müsste; `SpeechController` selbst braucht Android und bleibt untestbar.

**Instrumentiert:** `SoundFeederShotTest` rendert Bühne mit Karte, Haufen und beiden Fressern
bei 320/360/411 dp Breite und `fontScale` 1.0 und 1.3 nach `filesDir/feedershots` (Weg A
in README). Kein Assertion-Test — er zeigt, ob der Bauch-Glyph bleibt, wo er hingehört.

## 10. Was bewusst nicht drin ist

- **Kein Position-Konzept.** Nie „Anfang, Mitte, Ende". Das war der Fehler des Finders.
- **Kein dritter Fresser** („keins von beiden"). Jede Karte gehört zu genau einer Seite;
  die Ableitung garantiert das.
- **Kein „Zeig mir".** Der Hinweis nach dem zweiten Fehlgriff ist bei zwei Zielen die
  Auflösung.
- **Keine Batterie, keine Zahl.** Der Futterhaufen ist der Fortschritt.
- **Keine Hilfestufen.** Es gibt nichts, das die Aufgabe leichter machen könnte, ohne sie
  zu lösen.
- **Keine Monster-Stimme für Wörter.** Die Aussprache bleibt sauber, siehe §7.
- **Kein T/W-Paar.** Turm/Wurm wäre ein hübscher Zwilling, T/W aber keine bekannte
  Verwechslung; beide Atome nützen trotzdem dem T- und W-Vorrat.

## 11. Doku-Folgeänderungen

- `docs/PRODUCT_PRINCIPLES.md` §3: der Laut-Fresser als dritter abgeleiteter Zusatz-Trainer
  mit Paar-Stufen, Auswahlregel und Position nach der Jagd; Verweis auf den Finder als
  Vorgänger und warum die Frage jetzt „welcher" statt „wo" lautet.
- §7: die Fress-Sequenz als Artikel-Ausnahme; die Monster-Stimme (Tonhöhe zur Laufzeit,
  Profil `monster` nur für Reaktionen); der Text-Fallback der Karte ohne TTS.
- §9: Layout-Ausnahme — zwei Drop-Zonen unten, Karte plus Futterhaufen oben, Aufgabenblock
  hält seine Höhe.
- §10: Rollenfarben der beiden Fresser benennen.
- `AGENTS.md`: Trainer-Typen-Kurzfassung ergänzen.
- `tools/tts/README.md`: Profil `monster` und Feld `monsterTts`.

## Anhang: Zuordnung über den heutigen Pack

Simuliert aus `atoms.json`/`lessons.json` mit den zehn neuen Atomen. Vorrat = tauglich
gefilterte Wörter links + rechts; die Runde zieht daraus bis zu 7.

| Lektion | Fokus | Stufe | Paar | Vorrat |
| --- | --- | --- | --- | --- |
| L01 | M A | — | kein Fresser | |
| L02 | I O | — | kein Fresser | |
| L03 | P T | Aufwärmen | P/T | 8 + 16 |
| L04 | L H | Aufwärmen | L/H | 5 + 13 |
| L05 | F U | Aufwärmen | F/T | 12 + 17 (Fisch/Tisch) |
| L06 | R N | Kontrast | M/N | 8 + 6 |
| L07 | S E | Aufwärmen | S/T | 8 + 16 (Sonne/Tonne) |
| L08 | D K | Kontrast | D/T | 5 + 17 |
| L09 | Ei W | Kontrast | F/W | 13 + 6 |
| L10 | G Ch | Kontrast | G/K | 2 + 12 |
| L11 | Au B | Kontrast | W/B | 6 + 18 |
| L12 | Ä Ö Ü Äu | Vokal | Ö/Ü | 4 + 6 |
| L13 | Sch | Kontrast | S/Sch | 9 + 8 |
| L14 | J Z Eu | Kontrast | S/Z | 9 + 7 |
| L15 | ß V | Wiederholung | B/P | 18 + 10 |
| L16 | ck Pf | Kontrast | Pf/F | 4 + 13 |
| L17 | St Sp | Kontrast | St/Sp | 4 + 2 |
| L18 | C Y X Qu | Wiederholung | K/T | 11 + 17 (Kanne/Tanne) |
| L19 | M A | Kontrast | M/N | 8 + 6 |
| L20 | I O | Vokal | I/O | 21 + 35 |
| L21 | P T | Kontrast | D/T (B/P lief in L15) | 5 + 17 |
| L22 | Ei Au | Vokal | Ei/Au | 10 + 8 |
| L23 | Sch Ch | Kontrast | S/Sch | 9 + 8 |
| L24 | St Sp | Kontrast | St/Sp | 4 + 2 |
| L25 | Ö Ü | Vokal | Ö/Ü | 4 + 6 |
| L26 | Qu X | Wiederholung | L/R | 4 + 7 |
| L27 | H Sch | Kontrast | S/Sch | 9 + 8 |
| L28 | B K | Kontrast | G/K (am längsten nicht gespielt) | 2 + 12 |
| L29 | ß B | Kontrast | W/B | 6 + 18 |
| L30 | Ei V | Vokal | Ei/Eu (Ei/Au lief in L22) | 10 + 4 |
| L31 | Sch M | Kontrast | M/N | 8 + 6 |
| L32 | R S | Kontrast | S/Z | 9 + 7 |
| L33 | T B | Kontrast | B/P | 18 + 10 |
| L34 | Ü L | Kontrast | L/R | 4 + 7 |

Ab L15 hängt die Zuordnung an der „am längsten nicht gespielt"-Regel und ist im
Snapshot-Test festgehalten; wer Tabelle oder Content ändert, aktualisiert den Snapshot
bewusst. Ei/Eu — das eigentlich wichtige Vokalpaar (Feier/Feuer) — wird mit Eule, Feuer,
Flugzeug und Freund gerade eben spielbar und landet in L30 mit 2 Eu-Karten gegen 5
Ei-Karten; jedes weitere Eu-Substantiv mit Emoji verbessert die Runde.

Quellen: Fox-Boyer, Kindliche Ausspracheentwicklung (annette-fox-boyer.de); Fox-Boyer et
al. 2020, Phonologische Prozesse; Heidelberger Lautdifferenzierungstest (Testzentrale);
ALF Hannover, Übungen zur Lautidentifikation; Springer HNO 2005, Stabilität der
Lautdiskriminationsfähigkeit im Vorschulalter.
