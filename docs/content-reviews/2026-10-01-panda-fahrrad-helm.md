# Content-Review: Panda, Fahrrad und Helm

Stand: 2026-10-01 · Geprüft gegen `app/src/main/assets/content/` (Stand `a9a08e0`) und die
Laut-Fresser-Ableitung (`SoundFeederDerivation`, einmal über den ganzen Pack ausgegeben).

Frage des Nutzers: Bekommen wir „Panda", „Fahrrad" und „Helm" im Lernpfad unter?

Kurz gesagt: **Fahrrad ist schon gut untergebracht, Helm nur am Rand, Panda gar nicht.**
Alle drei lassen sich ab L07 bzw. L08 schreiben. Gebaut werden sollten aber nur Panda und
Helm, denn Fahrrad ist für den Wort-Bauer zu schwer.

**Umgesetzt am 2026-10-01** (Entscheidungen des Nutzers):

- Panda: Wort-Bauer `Pan + da` in L21 (`l21-t8c`, nach Pizza). **Der Merksatz bleibt
  „P wie Papa."**, denn das ist Fibel-Sprache.
- Helm: Wort-Bauer `H + e + l + m` in L07 (`l07-t8a`). Er steht **nach der zweiten
  Rose-Runde, nicht direkt nach Nest** (siehe unten).
- Fahrrad bleibt unverändert.
- `silbo_emoji.ttf` wurde nur um 🐼 ergänzt, aus derselben Noto-Version 2.057 (+2 KB).
  Fibel- und Baloo-Schrift sind unverändert. 801 Unit-Tests grün; beide Runden habe ich
  als Standbild auf dem Motorola angesehen.
- Audio im TTS-UI kuratiert und exportiert (`e0070bd`): „Panda", „der Panda", „der Helm",
  „Baue das Wort Panda.", „Baue das Wort Helm.".

**Warum Helm nicht direkt nach Nest steht:** Der Wort-Detektiv wechselt pro Wort zwischen
Buchstaben- und Silbenmodus. Auf dem zweiten Platz hätte Helm den Silbenplatz bekommen,
den bisher Rose belegt (`se`). Helm hat keine Fokus-Silbe und fällt auf den
Buchstabenmodus zurück, und L07 hätte damit keine einzige Silbenrunde mehr gehabt.
`SymbolInWordDerivationTest` hat das gemeldet. Nach der zweiten Rose-Runde sucht der
Detektiv E in Helm, und Rose behält ihre Silbenrunde.

---

## Befund 1: Heutiger Stand

| Wort | Atom | Wo es heute vorkommt | Audio |
|---|---|---|---|
| **Fahrrad** 🚲 | `fahrrad`, `kind: other` | Satz-Versteher L12 als richtige Karte („Der Frosch ist mit dem Fahrrad gefahren."), L22 und L29 als Gegenkarte zum Roller, Laut-Fresser L05 (F/T) und L16 (Pf/F) | Wort und Satz vorhanden |
| **Helm** ⛑️ | `helm`, `kind: other` | Satz-Versteher L22 als richtige Karte („Mia hat ihren Helm aufgesetzt."), L31 als Gegenkarte zum Hut. Im Laut-Fresser **nie gezogen**: L04 (L/H) wählt Hand, Handy, Hahn, Hase und Handschuh | Wort und Satz vorhanden |
| **Panda** 🐼 | – | nirgends, auch nicht in der Emoji-Schrift | – |

Das Fahrrad kommt fünfmal vor, der Helm zweimal und der Panda nie. Das Kind hört und sieht
alle drei Wörter, die schon da sind, aber es baut keines davon.

## Befund 2: Schreibbarkeit

| Wort | Grapheme | schreibbar ab | Schwierigkeit |
|---|---|---|---|
| **Helm** | H·e·l·m | **L07** (E) | leicht: eine geschlossene Silbe aus vier Einzellauten, dasselbe Muster wie *Nest* und *Keks* |
| **Panda** | P·a·n·d·a | **L08** (D) | leicht: zwei Silben, *Pan·da*, wie *Piz·za*, ohne Dehnungs-h und ohne Doppelkonsonant |
| **Fahrrad** | F·ah·r·r·a·d | L08 (D) | **schwer**: Dehnungs-h (`ah` bleibt eine Einheit), Doppel-r über der Silbengrenze, und das End-d klingt wie *t* (Auslautverhärtung) |

Das Fahrrad gehört nicht nach L08. Dort ist D der Fokus und der Laut-Fresser spielt D/T,
und ein Wort, dessen *d* wie *t* klingt, ist genau die Verwechslung, die diese Lektion
auseinanderhalten soll.

## Befund 3: Vorschläge

### Panda: L21 (P & T, Wiederholung)

- *(Vom Nutzer verworfen: „P wie Papa." bleibt.)* **Merksatz „P wie Panda." 🐼** statt „P wie Papa." 👨. Papa ist in L21 ohnehin ein
  Bauwort, der Merksatz wiederholt ihn also nur. Der Merksatz ist ein freier Slot, und
  weil der Pfad-Würfel das Merksatz-Bild auf seiner Seitenfläche zeigt (Drehwürfel), sieht
  das Kind den Panda dann schon auf dem Startbildschirm.
  *Was dagegen spricht:* Die Wiederholungen L20 und L21 nehmen für ihre Merksätze bewusst
  Familienwörter (O wie Oma, T wie Tom). Wer dieses Muster behalten will, lässt den
  Merksatz stehen und nimmt nur den Wort-Bauer.
- **Wort-Bauer „Pan + da"** als vierte Wort-Bauer-Runde nach Pizza (Blöcke
  `letter-p:"Pan"`, `letter-d:"da"`, dieselbe Form wie `Piz + za`). Papa, Tom und Pizza
  bleiben. Der Wort-Detektiv bekommt damit eine Runde mehr („Finde P in Panda").
- Im **Laut-Fresser** wird der Panda von selbst zur P-Karte (B/P in L15 und L33, P/T in
  L03). Das ist zulässig, weil weder B noch T im Wort vorkommt.
- Bewusst nicht: keine fünfte Satz-Versteher-Runde, weil L21 mit fünf Runden schon am
  Maximum ist. Und kein „Pandabär" in Phase 8, denn 🐼 wäre dann zweimal dasselbe Bild
  (Doppelgänger-Regel).

### Helm: L07 (S & E)

- **Wort-Bauer „H + e + l + m"**, im Vorschlag direkt nach *Nest* (N + e + s + t), umgesetzt nach der zweiten Rose-Runde (siehe oben). Beide Runden haben
  dasselbe Muster, das E ist in beiden das neue Graphem, und der Wort-Detektiv findet E in
  Helm. Es wäre die fünfte Wort-Bauer-Runde in L07 (L32 und L34 haben schon fünf).
  Das Atom wechselt von `other` auf `word`.
- Danach baut L22 („Mia hat ihren Helm aufgesetzt.") auf ein Wort auf, das das Kind
  selbst gebaut hat.
- *Alternative:* L27 hat H als Fokus („H wie Hand"). Phase 8 handelt aber von Komposita,
  und dort wäre der Helm ein Fremdkörper.
- **Bildfrage:** ⛑️ ist der Rettungshelm, weiß mit rotem Kreuz. Ein Emoji für einen
  Fahrradhelm gibt es nicht, 🪖 ist ein Militärhelm und scheidet aus. Als Bild für „Helm"
  ist ⛑️ für ein Kind erkennbar. Weil der Helm im Wort-Bauer aber groß über den Kacheln
  steht, wäre das vor dem Umbau einen Blick am Gerät wert.

### Fahrrad: so lassen, wie es ist

Das Fahrrad ist mit fünf Auftritten schon das am besten platzierte der drei Wörter. Bauen
sollte das Kind es nicht (siehe Befund 2). Wenn es doch gebaut werden soll, kommen zwei
Wege in Frage:

- **Wort-Bauer „Fahr + rad" in L29** (Fußball & Baumhaus), wo es thematisch ums Spielen
  draußen geht und der Satz-Versteher den Roller schon gegen das Fahrrad stellt. Weil
  Fahrrad weder ß noch B enthält, lässt der Wort-Detektiv das Wort aus. Das schadet
  nicht, bringt aber auch nichts.
- **Abgelehnt: Fahrrad als Kompositum in Phase 8.** „fahr" ist kein Wort, das das Kind
  gebaut hat, und es lässt sich nicht zeigen. „Rad" teilt sich mit dem Fahrrad das Emoji
  🚲 und wurde deshalb schon im August gelöscht. Dasselbe gilt für einen „Fahrradhelm":
  er bräuchte ein eigenes Bild, und das gibt es nicht.

## Was eine Umsetzung kostet

- **Emoji-Schrift:** 🐼 ist neu, deshalb muss die Schrift mit `tools/fonts/build_fonts.py`
  neu gebaut werden (wenige KB, `EmojiFontCoverageTest`).
- **Audio** (neue Aufnahmen mit `tts extract` / `tts status`): „Panda", „P - wie Panda.",
  „Baue das Wort Panda.", „Baue das Wort Helm.", die Artikelformen „der Panda" und
  „der Helm" für das Erfolgs-Vorsprechen sowie die beiden neuen Wort-Detektiv-Ansagen.
  Fahrrad bleibt unverändert und kostet nichts.
- **Tests:** Der Laut-Fresser zieht seine Karten der Reihe nach aus einem alphabetischen
  Vorrat. Ein neues P-Wort verschiebt deshalb, welche Karten spätere B/P- und P/T-Runden
  zeigen. Die Paar-Zuordnung (der Snapshot) bleibt dieselbe, die Kartenlisten ändern sich.
  `LessonCoverageTest` (keine Atome ohne Auftritt, Grapheme, Merksatz-Anlaut) muss grün
  bleiben.
- **Fortschritt:** Bestehende Spielstände sind nicht betroffen, denn es kommen nur neue
  Task-IDs hinzu (`l07-t8a`, `l21-t8c`).
