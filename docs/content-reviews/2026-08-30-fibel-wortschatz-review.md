# Content-Review: Erstlese-Wortschatz gegen den ausgelieferten Lernpfad

Stand: 2026-08-30 · Vergleichsgrundlage: `~/Downloads/fibel-wortschatz.md` (Vorschlagsliste)
gegen `app/src/main/assets/content/` (atoms, tasks, sentences, finales, lessons).

## Was verglichen wurde

Der Pack enthält **271 Atome** (39 Buchstaben, 39 Silben, 193 Wort-/Bildatome) und
226 Tasks in 26 Lektionen (18 autoriert + 8 Wiederholungen). Verglichen wurden alle
Wort-tragenden Stellen: `word_build`-Ziele, `syllable_merge`-Ergebnisse,
`letter_trace.rewardTts` („M wie Mond"), `count_add`-Ikonen, `sentence_order`-Sätze,
`sentence_picture`-Sätze und -Karten sowie die 18 Finale-Sätze.

Ergebnis in Zahlen: von den ~230 Vorschlagswörtern stehen **64 im Pack**, **rund 125
fehlen**. Umgekehrt stehen **116 Pack-Wörter nicht auf der Liste** — die sind aber nicht
per se falsch, siehe Befund 1.

---

## Befund 1 — Der Lernpfad hat zwei Klassen von Wort-Slots, und die Liste passt nur auf eine

Das ist der wichtigste Punkt für alles Weitere.

**Gebundene Slots** (`word_build`, `syllable_merge`, `sentence_order`): Hier erzwingt der
Validator, dass jedes Graphem schon eingeführt ist. In L05 stehen exakt `M A I O P T L H F U`
zur Verfügung — deshalb heißt das Wort dort „Ufo" und nicht „Handy". „Gebräuchlichkeit"
ist hier das **zweite** Kriterium; Lautgetreuheit und Schreibbarkeit sind das erste.
Ein großer Teil der Liste (Handy, Rucksack, Zahnbürste, Kopfhörer, Regenbogen …) ist in
den ersten zwölf Lektionen schlicht nicht darstellbar.

**Freie Slots** (`letter_trace.rewardTts` + `rewardEmoji`, `count_add`-Ikonen,
`sentence_picture`-Karten, Finale-Bilder, Pfad-Schilder): Hier gilt keine
Graphem-Beschränkung. Das Kind **hört und sieht** das Wort, es muss es nicht bauen.
Genau hier greift die Liste vollständig — und genau hier ist Modernisierung billig.

**Konsequenz für den Umbau:** Nicht „Wortschatz austauschen", sondern die freien Slots
mit der Liste bespielen und die gebundenen Slots nur dort anfassen, wo ein Wort wirklich
schief steht *und* eine schreibbare Alternative existiert.

---

## Befund 2 — 60 von 193 Wort- und Bildatomen erreichen kein Kind

Sie stehen in `atoms.json`, werden aber von keinem Task, Satz oder Finale referenziert:

> Affe, Bahn, Blatt, Blitz, Eimer, Erdbeere, Feder, Feuer, Gabel, Gras, Hahn, Hexe,
> Himmel, Igel, Insel, Krokodil, Kuh, Lampe, Löffel, Markt, Milch, Nanu, Nilpferd, Nuss,
> Ofen, Pflanze, Pilz, Pinguin, Pizza, Polizei, Quark, Quelle, Rad, Raupe, Salami, Salat,
> Tag, Tiger, Tisch, Tram, Tür, Uhu, Wespe, Xylofon, Yacht, Zahn
> (+ 14 Funktions-/Farbwörter: bei, es, und, gut, hol, ruf, singt, wind, spielen, lachen,
> grün, lila, orange, braun)
> (+ 10 Silben: ruh, sa, ka, kei, wie, mau, h, zu, que, gu)

Darunter sind **genau die Alltagswörter, die auf der Vorschlagsliste stehen**: Tür, Tisch,
Lampe, Rad, Milch, Pizza, Kuh, Affe, Igel, Uhu, Pinguin. Der Pack hat sie schon — er
zeigt sie nur nie.

**Das ist der billigste Hebel im ganzen Review.** Eine `sentence_picture`-Falschkarte
(`wrongAtomIds`) auszutauschen kostet **null Audio** — nur das Emoji wechselt. Dieselbe
Bewegung erhöht nebenbei die Bildvielfalt der Pfad-Schilder, die aus denselben Atomen
abgeleitet werden.

Gegenprobe zur Streichung: Nicht alle 60 sind Rettungskandidaten. `Nanu`, `Tram`,
`Quelle`, `Insel`, `Markt`, `Ofen`, `Salami`, `Xylofon`, `Yacht` würde ich **löschen**
statt aktivieren (siehe Challenge unten).

---

## Befund 3 — In der Besetzung kommt kein Kind vor

Die handelnden Figuren des gesamten Lernpfads sind: **Mama, Papa, Oma, Opa, Tom** und die
Katze **Mimi**, dazu Fantasiefiguren (Pirat, Hexe, Clown, Drache, Dino). Häufigkeiten in
den Satz-Versteher-Karten: Tom 18×, Oma 14×, Opa 13×, Mama 11×.

Es gibt **kein** `Kind`, `Baby`, `Junge`, `Mädchen`, `Freund`, `Freundin`, `Familie`,
keine Geschwister, keine Kita, keine Gleichaltrigen. Für eine App für 4–7-Jährige ist das
die auffälligste inhaltliche Lücke — die Welt besteht aus Erwachsenen und Großeltern.

Die Namensliste löst das teilweise, und sie ist schreibbar früher als gedacht:

| Name | ab Lektion | Zerlegung |
|---|---|---|
| **Mia** | **L02** | m·i·a |
| Anna | L06 | a·n·n·a |
| Noah | L06 | n·o·a·h |
| Emma | L07 | e·m·m·a |
| Mateo | L07 | m·a·t·e·o |
| Lennard | L08 | l·e·n·n·a·r·d |
| Alex | L18 | a·l·e·x (X ist die letzte Lektion) |

**Konkret:** `Mimi` in L02/L20 (`word_build`, Blöcke `mi`+`mi`) ist der einzige echte
Kandidat für einen Tausch im gebundenen Slot. `Mia` (`Mi`+`a`) ist genauso früh
schreibbar, ist seit Jahren ein Top-3-Mädchenname und macht aus einer Katze ein Kind.
Preis: Der Doppelsilben-Reiz von `mi`+`mi` geht verloren, die Verschmelzung `Mi`+`a`
ist etwas anspruchsvoller. Das ist der einzige Punkt in diesem Review, an dem ich eine
echte Abwägung sehe, keine klare Empfehlung.

`Kind` (L08), `Hand` (L08), `Baby` (L18) sind als **Bildwörter** dagegen sofort und
folgenlos einsetzbar.

---

## Befund 4 — Die Dingwelt ist die von 1985, nicht die von heute

Kein einziges Atom für: **Handy, Kopfhörer, Foto, Akku, Rucksack, Helm, Roller,
Zahnbürste, Seife, Pflaster, Müll, Kita, Dusche, Rutsche, Schaukel, Regen, Schnee, Wind,
Regenbogen, Uhr, Nudeln, Hose, Tasche, Tasse, Hand, Auge, Mund, Blume, Torte, Party**.

Stattdessen trägt der Pack: `Ofen`, `Markt`, `Tram`, `Radio`, `Klavier`, `Salami`,
`Quelle`, `Insel`, `Ähre`, `Yacht`, `Xylofon`.

Bemerkenswert: Was der Pack **an** Modernem hat, ist gut gewählt — `Paket` 📦 (L03,
Belohnungswort für P) ist heute alltagsnäher als es 1985 war, `Ampel`, `Polizei`, `Taxi`,
`Bus`, `Fahrrad`, `Flugzeug` sitzen richtig. Es fehlt kein Gefühl für Aktualität, es
fehlt schlicht die Ergänzung.

Schreibbarkeit der wichtigsten Nachrücker (relevant nur, falls sie über Bildwort hinaus
sollen):

| ab | Wörter |
|---|---|
| L05 | Muh, Foto |
| L06 | Uhr, Huhn |
| L07 | Hose, Tasse, Roller, Helm, Reh, Esel, Tonne, Torte |
| L08 | Kind, Hand, Mund, Kita, Nudeln, Akku, Delfin, Onkel |
| L09 | Wind, Seife, Wurm |
| L10 | Regen, Mädchen |
| L11 | Miau, Auge, Blume, Banane, Regenbogen |
| L13 | Rutsche, Dusche, Schnee, Schaukel, Schwein, Tschüss |
| L16 | Socke, Rucksack, Kopfhörer |
| L18 | Handy, Baby, Party, Yoga, Box |

---

## Befund 5 — Adjektive sind fast nur Farben, und der Satz-Architekt wiederholt ein Muster sechsmal

Adjektive im Pack: `groß`, `gut` und die Farben `rot blau grün gelb orange lila braun`
(vier davon ungenutzt). Von den 30 Adjektiven der Liste fehlen 28.

Das schlägt direkt auf `sentences.json` durch. Von 26 Sätzen folgen **sechs** dem Muster
„Das X ist \<Farbe\>":

> Das Taxi ist gelb. · Das Auto ist gelb. · Der Kreis ist blau. · Das Quadrat ist gelb. ·
> Das Dreieck ist rot. · Das Herz ist rot.

Dazu vier Mal „Das ist mein X" / „X ist da". Der Satz-Architekt trainiert damit vor allem
eine Schablone, nicht Sprache.

Die Liste liefert genau die fehlenden Gegensatzpaare, die schon ab L07/L08 schreibbar
sind: **klein** (L08), **laut/leise** (L09/L09), **müde** (L08), **kalt/warm** (L08/L11),
**nass** (L07), **hell/dunkel** (L10/L10), **voll/leer** (L11/L07), **neu** (L09),
**schnell** (L13). Ein Satz wie „Der Fisch ist klein." kostet dasselbe wie „Das Taxi ist
gelb." und sagt mehr.

---

## Befund 6 — Verben stehen fast nur in gebeugter Einzelform

Lesbare Verben im Pack: `gehen`, `spielen`/`spielt`, `lachen`, `schwimmt`, `fliegt`,
`singt`, `läuft`, `ruf`/`ruft`, `hol` — davon **fünf ungenutzt**. Die Liste schlägt 35 vor.

Die alltagsnächsten, die in den freien Satz-Versteher-Slot sofort passen (dort ist
Beugung ausdrücklich erlaubt): **essen, trinken, schlafen, malen, helfen, teilen,
aufräumen, üben, warten, klettern**. `schlafen` und `essen` kommen in den
`sentence_picture`-Sätzen schon vor („Der Bär hat im Bett geschlafen") — nur nicht als
Atom im Satz-Architekt.

---

## Challenge gegen den Pack — was ich streichen würde

Sortiert nach Überzeugung. Alle Streichkandidaten sind **freie Slots oder tote Atome**;
kein Vorschlag zwingt zu einem Umbau des Buchstabenpfads.

| Wort | Wo | Warum raus | Ersatz |
|---|---|---|---|
| **Ähre** | L12 `letter_trace` „Ä wie Ähre" 🌾 | Ein Vorschulkind kennt keine Ähre. Das Wort trägt den Ä-Merksatz der Lektion. | **„Ä wie Äpfel"** 🍎 — Atom `Äpfel` existiert bereits und wird in L12 ohnehin gezeigt |
| **Yacht** | L18 „Y wie Yacht" ⛵, Atom tot | Rarissimum, dazu orthografisch irreführend (Y als /j/) | **„Y wie Yoga"** 🧘 — in fast jeder Kita präsent |
| **Weg** | L09 „W wie Weg" 🛣️ | Als Bild kaum fassbar; 🛣️ liest sich für ein Kind nicht als „Weg" | **„W wie Wolke"** ☁️ oder **„W wie Wasser"** 💧 (beide Atome existieren) |
| **Sack** | L16 „ck wie Sack" 🛍️ | Isoliert kaum gebräuchlich, das Emoji zeigt eine Einkaufstüte | **„ck wie Socke"** 🧦 oder **„ck wie Rucksack"** 🎒 (beide auf der Liste, beide L16-schreibbar) |
| **Dose** | L08 „D wie Dose" 🥫 + `word_build` | Konserve ist im Kinderalltag randständig | Belohnungswort **„D wie Dino"** 🦕 (Atom existiert). Das `word_build`-Wort `Dose` würde ich **lassen** — lautgetreu, und `Dino` bräuchte ein neues Silbenpaar |
| **Nanu** | Atom, tot | Veraltete Interjektion | ersatzlos löschen; die Liste liefert **ups, hey, okay, tschüss, miau, muh, wuff** |
| **Tram** | Atom, tot | Regionalismus (CH/München). Kinder sagen „Straßenbahn" | löschen, `Bus`/`Zug`/`Bahn` decken es ab |
| **Quelle, Insel, Markt, Ofen, Salami** | Atome, tot | Randständig, nie gezeigt | löschen statt aktivieren |
| **Tal** | 4× `sentence_picture` (L05, L14) | Kein Alltagswort, und als Bildkarte nicht darstellbar | Sätze „Ein Ufo ist im Tal gelandet" / „Der Zug fuhr durch das Tal" → `Park`, `Straße`, `Strand` (Atome existieren). **Kostet zwei neue Aufnahmen** — nur machen, wenn die Sätze ohnehin angefasst werden |
| **Xylofon** | L18 „X wie Xylofon" 🎵, Atom tot | Grenzfall: Kita-Instrument, aber Schreibweise und Emoji tragen nicht | halten, aber Atom löschen; L26 hat mit „X wie Taxi" 🚕 schon die bessere Variante |
| **Radio, Klavier** | je 1× Bildkarte | Grenzfälle, keine Priorität | belassen |

**Nicht** streichen würde ich, obwohl nicht auf der Liste: `Ufo`, `Dino`, `Pirat`, `Hexe`,
`Drache`, `Clown`, `Zebra`, `Giraffe`, `Krokodil`, `Nilpferd`, `Elefant`, `Tiger`. Das
sind keine veralteten Wörter, sondern die motivationsstärkste Kategorie dieser
Altersgruppe. Die Liste unterschätzt sie.

---

## Challenge gegen die Liste — was ich nicht übernehmen würde

1. **Zahlwörter als Lesewörter** (eins … zehn, null, erste/zweite).
   Widerspricht PRODUCT_PRINCIPLES §3/§7: Rechnen läuft bewusst **ohne Wörter zum Lesen
   oder Schreiben**, Mengen kommen als Icon + Ziffer, Singular/Plural nur gesprochen. Die
   Zahlwörter *hört* das Kind in jeder Lektion — sie zusätzlich als Graphemketten
   einzuführen, kippt ein bewusst gesetztes Prinzip.

2. **`böse`** (und das Paar `lieb`/`böse`).
   §10 und das Feedback-Design vermeiden Bestrafung, Rot und Wertung durchgängig. Ein
   moralisches Etikett als Lesewort passt nicht dazu und ist obendrein die altmodischste
   Formulierung der Liste. Besser: **müde**, **fröhlich**, **traurig**, **wütend** —
   Zustände statt Urteile.

3. **Doppelte Genusformen** (`Freund`/`Freundin`, `Arzt`/`Ärztin`).
   Für ein Kind, das noch nicht liest, verdoppelt das die Last ohne Erkenntnisgewinn —
   die Motivation dahinter lässt sich über die **Bilder** einlösen (Ärztin als Bildkarte),
   nicht über zwei Lesewörter. Eine Form pro Begriff genügt.

4. **`Auflauf`** 🍴 und **`Tonne`** 🗑️.
   `Auflauf` ist kein Kinderwort (und mehrdeutig: Auflauf = Menschenauflauf). `Tonne`
   trägt in der Liste dasselbe Emoji wie `Müll` und ist ohne Kompositum („Mülltonne")
   mehrdeutig. **`Akku`** 🔋 ist als Bild abstrakt — `Handy` deckt das Feld besser ab.

5. **`Esel`, `Ziege`, `Wurm`, `Reh`** sind unbedenklich, aber nicht alltagsnäher als die
   Tiere, die schon im Pack liegen. Kein Grund, dafür etwas zu verdrängen — reine
   Auffüllkandidaten.

6. **Datenqualität der Liste:** `Buch` und `Nudeln` stehen doppelt, `Tisch` und `Schaukel`
   haben kein Emoji (im Pack ist `Tisch` deshalb ein Atom ohne Emoji und daher als
   Bildkarte unbrauchbar), bei den Zahlwörtern steht `n` statt `neun`.

---

## Umbauvorschlag, nach Kosten sortiert

Die Reihenfolge ist wichtig, weil jede Textänderung eine neue TTS-Aufnahme plus
Kuratierung kostet (`tools/tts/`, Batch-Lauf 25–40 min, danach Anhören). Der Pack hat
heute **732 Clips**.

### Stufe 1 — kostet kein Audio

* **Falschkarten im Satz-Versteher austauschen** (`wrongAtomIds`): nur Emoji, kein Ton.
  Damit lassen sich `Tür`, `Tisch`*, `Lampe`, `Rad`, `Milch`, `Pizza`, `Kuh`, `Affe`,
  `Igel`, `Uhu`, `Pinguin`, `Gabel`, `Löffel`, `Zahn`, `Eimer`, `Nuss`, `Erdbeere`,
  `Salat`, `Feuer`, `Hexe` in Umlauf bringen. (*`Tisch` braucht vorher ein Emoji.)
* **Tote Atome löschen**: Nanu, Tram, Quelle, Insel, Markt, Ofen, Salami, Xylofon, Yacht.
* **Neue Bildatome anlegen** (Emoji + `gender`/`nounClass`), noch ohne sie zu verwenden:
  Handy 📱, Kopfhörer 🎧, Rucksack 🎒, Helm ⛑️, Roller 🛴, Zahnbürste 🪥, Seife 🧼,
  Pflaster 🩹, Kita 🏫, Regen 🌧️, Schnee ❄️, Regenbogen 🌈, Rutsche 🛝, Uhr ⏰, Hose 👖,
  Tasche 👜, Tasse ☕, Hand ✋, Auge 👁️, Mund 👄, Blume 🌸, Nudeln 🍝, Torte 🎂, Kind 🧒,
  Baby 👶, Freund 🧑‍🤝‍🧑, Schwein 🐖, Huhn 🐔, Socke 🧦.
  Der Validator verlangt Genus + Nomenklasse; `ContentValidatorTest` fängt Auslassungen.

### Stufe 2 — eine kurze Aufnahme pro Änderung

* **Belohnungswörter im Spurensucher** (`rewardTts`, ein Satz von vier Wörtern):
  Ä wie Äpfel · Y wie Yoga · W wie Wolke · ck wie Socke · D wie Dino.
  Fünf Aufnahmen, fünf modernisierte Merksätze — das beste Verhältnis im ganzen Umbau.
* **`count_add`-Ikonen** dort auffrischen, wo dasselbe Motiv viermal läuft (Ameise 4×,
  Oma 4×, Tomate 4×, Rose 4×, Fisch 4×, Taxi 4×). Achtung: Der Prompt nennt das Motiv
  („Drei Ameisen und zwei Ameisen …"), jede Änderung ist also eine neue Aufnahme.

### Stufe 3 — Sätze, nur wo es sich lohnt

Hier gilt die Vorgabe „keine unnötigen Satzänderungen". Ich sehe genau drei begründete
Eingriffe:

1. **Die sechs Farb-Schablonensätze aufbrechen.** Nicht alle — zwei bis drei durch
   Gegensatzpaare ersetzen („Der Fisch ist klein.", „Das Wasser ist kalt.").
   `s-das-auto-ist-gelb` und `s-taxi-ist-gelb` sind fast identisch; einer kann weg.
2. **Die beiden `Tal`-Sätze** in L05/L14 auf `Park` / `Strand` umschreiben.
3. **Ein bis zwei Satz-Versteher-Runden pro Phase** auf Alltagsszenen umstellen, in denen
   ein **Kind** handelt statt Oma/Opa/Tom — das ist die inhaltliche Kernkorrektur aus
   Befund 3 und die einzige, die ohne neue Sätze nicht geht.

### Stufe 4 — offene Abwägung, Entscheidung beim Nutzer

* **`Mimi` → `Mia`** in L02/L20 (`word_build`, `sentence_picture`, Finale `f-l02`).
  Betrifft 5 Referenzen und ~4 Aufnahmen. Inhaltlich der stärkste Einzelgewinn
  (erstes Kind im Lernpfad, ab der zweiten Lektion), pädagogisch der einzige echte
  Verlust (die Doppelsilbe `mi`+`mi`).
* **Lautwörter als frühe Bauwörter**: `Muh` (ab L05), `Miau` (ab L11), `Wuff` (ab L09).
  Lautgetreu, kurz, hochvertraut — genau das, was ein Fibelanfang braucht, und auf der
  Liste. Würde je einen neuen `word_build`-Task bedeuten, also echte Neuautorierung.

---

## Was der Umbau nicht ändern sollte

* Die **Buchstabenreihenfolge** der 18 Lektionen. Sie ist der Grund für fast jede
  „veraltet" wirkende Wortwahl der ersten Hälfte und funktioniert.
* Die **Fantasietiere und -figuren**. Sie sind nicht der Altbestand, sie sind der Motor.
* Die **Rechnen-Regel ohne Lesewörter** (§3/§7).
* Die **wortgleiche Satz-Versteher-Instruktion** — sie hat genau eine Aufnahme, und der
  Validator hält das fest.
