# Graphem-Einheiten im Wort-Bauer — Design

**Status:** implementation-ready · **Datum:** 2026-09-02

## Problem

Der Wort-Bauer zerschneidet Diphthonge. „Bäume" wird als `Bä + u + m + e`
angeboten, „Häuser" als `Hä + u + s + e + r` — obwohl `au` seit l11 ein eigener
Baustein ist (`H + au + s`, `B + au + m`). Ein Kind, das die Kacheln lautiert,
liest `bä-u-me`. Die Silbe „Bä" existiert im Deutschen nicht.

Ursache: für `äu` fehlt das Atom. Der Pack führt `Ei`, `Au`, `Eu`, `Sch`, `Ch`,
`ck`, `Pf`, `St`, `Sp`, `Qu` als mehrbuchstabige Grapheme — `Äu` nicht. Ein
Test schreibt den Zustand sogar fest:
`WordGraphemesTest.umlautPlusVowelIsNotFusedBecauseAuDoesNotMatchAeu`.

Drei weitere Familien desselben Defekts (Vollständige Liste aus einem Scan über
alle 103 Wort-Bauer-Runden und 56 Verschmelzer-Runden):

1. **Diphthong zerschnitten** — Häuser, Bäume, Maus (`Ma + u + s`, wobei „Ma"
   am Silben-Atom `ma` hängt; in „Maus" steckt keine Silbe „ma").
2. **Vokal + Dehnungs-h zerschnitten** — Schuh (`Schu + h`, 2×), Stuhl
   (`Stu + hl`), Kuh (`K + u + h`). Ein Baustein „h" nach dem Vokal behauptet
   den Laut /h/, den das stumme h nicht hat.
3. **Silbe an der falschen Silbengrenze** — Apfel `A + pfe + l`. „Apfel" trennt
   `Ap-fel`; p und f liegen in verschiedenen Silben. „pfe" ist im Pack kein
   Atom, sondern ein Schnipsel am Graphem-Atom `Pf`, und käme in keinem
   weiteren Wort der Fibel vor. Dasselbe in Pferd (`Pfe + r + d`).
4. **Silbe ohne Wort** — `pfa` (l16-t5, `pf + a`). Die einzige Silbe im Pack,
   deren Buchstabenfolge in *keinem* Wort auftaucht: nicht in einem gebauten,
   nicht in einem Bildwort. Die zwölf anderen Verschmelzer-Silben ohne
   Baustein-Auftritt sitzen mindestens in einem Wort, das das Kind sieht
   (`fu` → Fuß, `scha` → Schaf, `mo` → Mond, `rü` → grün). `pfa` ist reine
   Kombinatorik aus „welche zwei Grapheme führt l16 ein".

## Entscheidung

### 1. `Äu` wird das 40. Graphem

Neues Atom `letter-aeu` (`lemma`/`display` „Äu", `kind: letter`), Striche aus
`letter-ae` in die linke Hälfte skaliert (dieselbe Transformation, die
`letter-au` für das A benutzt) plus dem u aus `letter-au`.

Eingeführt in **l12** („Umlaut-Rätsel"), wo es thematisch hingehört: die Punkte
verändern den Laut — a→ä, o→ö, u→ü, **au→äu**. `focusAtomIds` wird
`[ä, ö, ü, äu]`, neue Trace-Runde `l12-t5b` *vor* den Wort-Bauer-Runden:

```
t3 Ä → t4 Ö → t5 Ü → t5b Äu → [Buchstaben-Jagd] → t6 Häuser → t7 Bäume
```

Prompt „Zeichne den **Laut** - Äu - nach…" (Diphthonge sagen „Laut", nicht
„Buchstabe" — wie l09/l14/l31), Merksatz „Äu - wie **in** Häuser." 🏘️.

Das repariert **zwei** Trainer: `WordGraphemes` leitet seine Digraph-Tabelle aus
den Trace-Runden ab, also zerlegt auch der Wort-Detektiv „Häuser" künftig in
`H·äu·s·e·r` und fragt „Finde den Laut - Äu - im Wort - Häuser." Die
Buchstaben-Jagd von l12 bekommt automatisch eine vierte Runde.

### 2. Bausteine

| Runde | vorher | nachher |
| --- | --- | --- |
| l12-t6 Häuser | `Hä + u + s + e + r` | `H + äu + s + e + r` |
| l12-t7 Bäume | `Bä + u + m + e` | `B + äu + m + e` |
| l19-t8b Maus | `Ma + u + s` | `M + au + s` |
| l13-t5 / l23-t7 Schuh | `Schu + h` | `Sch + uh` |
| l24-t8b Stuhl | `Stu + hl` | `St + uh + l` |
| l26-t8b Kuh | `K + u + h` | `K + uh` |
| l16-t7 Apfel | `A + pfe + l` | `A + pf + e + l` |
| l16-t8 Pferd | `Pfe + r + d` | `Pf + e + r + d` |

`uh` ist kein Graphem, sondern Vokal + Dehnungs-h; die Kachel hängt am Atom
`letter-u` und spricht wie die übrigen Schnipsel über Android-TTS „uh" (/uː/) —
richtig, statt das stumme h als /h/ zu behaupten.

Apfel/Pferd bekommen bewusst *nicht* die linguistisch korrekte Trennung
`Ap + fel`: die würde das Graphem zerschneiden, das l16 gerade einführt.
`A + pf + e + l` ist zugleich genau das, was der Wort-Detektiv in derselben
Lektion zeigt.

### 3. l16 verliert seinen Verschmelzer

`pfa` wird gelöscht und `l16-t5` ersatzlos entfernt. Für l16 gibt es keine
ehrliche Silbe: `ck` kann im Deutschen keine Silbe anfangen (es steht immer nach
kurzem Vokal — „So-cke" ist eine Trennregel, keine Lautstruktur), und `pf`
bildet mit keinem Vokal eine offene Silbe, die in einem Fibel-Wort vorkommt
(„Pfanne" trennt `Pfan-ne`, „Pfad" und „Pferd" sind einsilbig).

Damit steht l16 wie **l12**, das nie einen Verschmelzer hatte — der Zustand ist
etabliert, kein Loch. Die Silben-Jagd von l16 fällt mit weg; sie leitet sich aus
dem Verschmelzer ab. Das Graphem `Pf` verliert nichts: l16 zeichnet es nach und
baut Apfel und Pferd damit.

Verworfene Alternativen:

- **`ck + e = cke`** — würde in Socke (dieselbe Lektion) und Jacke landen und
  zwei Schnipsel-Kacheln richtig ankern. Aber „cke" ist keine lesbare Silbe:
  keine deutsche Silbe beginnt mit `ck`.
- **`p + f = pf`** — lautlich wahr (zwei Laute werden zu einem, genau das ist
  die Affrikate) und mit Präzedenz (l24 verschmilzt `s + t = st` und
  `s + p = sp`, ebenfalls zu einem Graphem statt zu einer Silbe). Verworfen,
  weil der Trainer nach einer *Silbe* fragt; die zwei l24-Runden sind damit die
  letzten, die „Welche Silbe entsteht" zu einem Laut sagen — eigener Durchgang.
- **`pf + au = Pfau`** — echtes einsilbiges Wort mit starkem Bild, aber ein Wort
  als Verschmelzungsergebnis ist die Phase-8-Form mitten in Phase 5.

### 4. Validator-Regel

`ContentValidator` lehnt künftig jeden Blockschnitt ab, der mitten durch eine
Graphem-Einheit läuft — für `word_build`-Bausteine **und**
`syllable_merge`-Teile. Die Einheiten werden aus dem Pack abgeleitet (alle
mehrbuchstabigen `letter`-Atome, wie `WordGraphemes.table` es tut), plus
Vokal + Dehnungs-h und Doppelvokal.

**Ausgenommen: `Pf`, `St`, `Sp`.** Die Trennlinie ist der Laut, nicht die
Buchstabenzahl: *ein* Zeichen für *einen* Laut bleibt ganz (Sch, Ch, ck, Qu,
Ei, Au, Eu, Äu); ein Cluster aus zwei Lauten darf an der Silbengrenze
auseinandergehen (Ap-fel, Wes-pe, bes-te) — und tut es in Apfel auch.

## Neue Aufnahmen

Drei Texte brauchen Kuratierung und laufen bis dahin über Android-TTS:

- `Äu` (Phonem, Atom-Lemma)
- die zwei Sätze von `l12-t5b` (Prompt und Merksatz)

Der Clip `fa` (bisheriges Lemma von `pfa`) wird verwaist.

## Bewusst offen

- **l12 lehrt ä und ö, zeigt sie aber in keinem Wort.** Küken trägt das ü,
  Häuser und Bäume tragen das äu. Bisher tauchte „ä" nur auf, weil der
  Diphthong falsch zerschnitten war. Das zu schließen bräuchte neue Wörter
  (Dächer aus Dach, Löwe von l25 vorgezogen) — eigener Durchgang.
- **Die Schnipsel-Kacheln** („gel" am Atom G, „Ster" am St, „Bro" am B, und neu
  „uh" am U) bleiben: 60 Bausteine tragen einen Silbenschnipsel als Beschriftung
  und hängen an einem Einzelbuchstaben-Atom. `SpeechClipText.forWordBlock` hält
  sie davon ab, den falschen Laut in kuratierter Stimme zu sprechen; sie laufen
  über Android-TTS. Eigene Baustelle.
- **`cke` als Silben-Atom** würde Socke und Jacke richtig ankern und dem
  Wort-Detektiv in l27 „Finde die Silbe - cke - im Wort - Jacke." erlauben.
  Ausgesetzt, weil `cke` als *lesbare* Silbe nicht taugt (keine deutsche Silbe
  beginnt mit `ck`) — der Anker wäre richtig, die Silbe nicht.
- **l24s zwei Verschmelzer-Runden** (`s + t = st`, `s + p = sp`) fragen „Welche
  Silbe entsteht", produzieren aber einen Laut.
