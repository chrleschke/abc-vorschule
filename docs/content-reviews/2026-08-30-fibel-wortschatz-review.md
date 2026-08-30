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
`Quelle`, `Insel`, `Markt`, `Ofen`, `Xylofon`, `Yacht` würde ich **löschen**
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

Stattdessen trägt der Pack: `Ofen`, `Markt`, `Tram`, `Radio`, `Klavier`,
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
| **Quelle, Insel, Markt, Ofen** | Atome, tot | Randständig, nie gezeigt | löschen statt aktivieren |
| ~~Salami~~ | Atom, tot | *Nutzerentscheidung: bleibt.* Salami gehört zur Pizza, und die lieben die Kinder | aktiviert statt gelöscht — L21 zeigt beide zusammen |
| **Tal** | 4× `sentence_picture` (L05, L14) | Kein Alltagswort, und als Bildkarte nicht darstellbar | Sätze „Ein Ufo ist im Tal gelandet" / „Der Zug fuhr durch das Tal" → `Park`, `Straße`, `Strand` (Atome existieren). **Kostet zwei neue Aufnahmen** — nur machen, wenn die Sätze ohnehin angefasst werden |
| **Xylofon** | L18 „X wie Xylofon" 🎵, Atom tot | Grenzfall: Kita-Instrument, aber Schreibweise und Emoji tragen nicht | halten, aber Atom löschen; L26 hat mit „X wie Taxi" 🚕 schon die bessere Variante |
| **Radio, Klavier** | je 1× Bildkarte | Grenzfälle, keine Priorität | belassen |

**Nicht** streichen würde ich, obwohl nicht auf der Liste: `Ufo`, `Dino`, `Pirat`, `Hexe`,
`Drache`, `Clown`, `Zebra`, `Giraffe`, `Krokodil`, `Nilpferd`, `Elefant`, `Tiger`. Das
sind keine veralteten Wörter, sondern die motivationsstärkste Kategorie dieser
Altersgruppe. Die Liste unterschätzt sie.

---

## Challenge gegen die Liste — was ich nicht übernehmen würde

1. **Zahlwörter als Lesewörter** (eins … zehn, null, erste/zweite). *— vom Nutzer bestätigt.*
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
* **Tote Atome löschen**: Nanu, Tram, Quelle, Insel, Markt, Ofen, Xylofon, Yacht, Tal, wind.
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


---

# Nachtrag — Entscheidungen und Umsetzung (30.08.2026)

Der Review lag dem Nutzer vor; die folgenden Entscheidungen kamen zurück und sind
umgesetzt. Zwei Randbedingungen des Reviews sind damit hinfällig: **Audio ist keine
Kostenbremse mehr** (die Aufnahmen werden nachgezogen), und **mehr Lektionen sind ein
ausdrückliches Ziel** — das Kind ist regelmäßig schneller durch den Pfad als neuer Inhalt
nachkommt.

## Entscheidungen

| Punkt | Entscheidung |
|---|---|
| Zahlwörter als Lesewörter | **Raus** — bestätigt, kommen nicht in den Pack |
| Salami | **Bleibt** — gehört zur Pizza; beide sind jetzt in L21 aktiv statt tot |
| Katze **Pepe** | **Neu**, ab L07 (`Pe`+`pe`) — ein Doppelsilbenwort genau dort, wo das E ankommt |
| Mimi | **Wird Mia** (L02/L20) — Pepe übernimmt die Katzenrolle, und Mia ist ab der zweiten Lektion das erste Kind im Pfad |
| Neue Lektionen | **Beides**: L19–L26 ausgebaut *und* eine neue Phase 8 mit acht Lektionen |
| Umfang | Voll durch — Wortschatz-Umbau und alle neuen Lektionen in einem Durchgang |

## Was jetzt im Pack steht

**Pfad:** 26 → **34 Lektionen**, 226 → **332 Tasks**, 18 → **26 Finale-Sätze**
(l19–l26 erben ihren Satz weiterhin von der Basis-Lektion). Atome: 271 → **314**.

**Phase 8 — zusammengesetzte Wörter (L27–L34).** Nach L18 sind alle 39 Grapheme
eingeführt; neuer Stoff kann also nicht aus Buchstaben kommen. Die neue Phase lehrt
stattdessen: *lange Wörter sind aus kurzen gebaut, die du schon kennst.* Der
Silben-Verschmelzer schiebt dafür zwei ganze Wörter zusammen („Schiebe Hand und Schuh
zusammen“), der Wort-Bauer setzt sie als zwei Blöcke. Sechzehn Komposita, jedes aus zwei
Teilen, die das Kind vorher selbst gebaut hat:

| Lektion | Wörter |
|---|---|
| L27 | Handschuh · Hausschuh |
| L28 | Apfelkuchen · Brotdose |
| L29 | Regenjacke · Sonnenblume |
| L30 | Fußball · Baumhaus |
| L31 | Eisbär · Vogelnest |
| L32 | Schneemann · Schneeball |
| L33 | Schultasche · Schulbus |
| L34 | Mülltonne · Taschenlampe |

**L19–L26 sind keine Stummel mehr.** Jede Wiederholung hat jetzt ein drittes, modernes
Bauwort (Maus, Milch, Pizza, Auto, Tisch, Stuhl, Tür, Kuh) und den fehlenden
Satz-Versteher mit vier Runden — vorher endeten sie nach zwei Wörtern und einem Satz.

**Belohnungswörter modernisiert:** Ä wie **Äpfel** (statt Ähre) · W wie **Wolke** (statt
Weg) · D wie **Dino** (statt Dose) · ck wie **Rucksack** (statt Sack) · Y wie **Yoga**
(statt Yacht).

**Neue Bildwörter im Umlauf:** Handy, Kopfhörer, Helm, Roller, Zahnbürste, Seife,
Pflaster, Uhr, Hose, Socke, Tasse, Nudeln, Torte, Rutsche, Dusche, Regenbogen, Foto,
Schwein, Huhn, Kind, Baby, Freund, Hand, Regen, Schnee, Blume, Tasche, Müll, Tonne, Mann.

**Reaktiviert statt gelöscht** (lagen tot im Pack, stehen jetzt auf Bildkarten oder als
Rechen-Ikone): Tür, Tisch, Lampe, Milch, Pizza, Salami, Kuh, Igel, Pinguin, Gabel,
Löffel, Eimer, Gras, **Krokodil**.

Das Krokodil 🐊 auf Wunsch des Nutzers nachgezogen, an sechs Stellen: **L08** hatte drei
Keks-Runden hintereinander — die dritte ist jetzt „Das Krokodil hat den Keks gefressen",
und die Rechen-Ikone der Lektion wechselt mit, womit das Tier auch auf dem Pfad-Schild
steht (🥫 🍪 🐊). Dazu als Gegenkarte in L14, L18, L23 und L31, überall dort, wo ein
anderes Tier die plausible Verwechslung ist.

Von den 60 toten Wort-/Bildatomen sind damit **36 übrig** — darunter 12 Funktions- und
Farbwörter, für die es keinen Satz gibt, und Tiere wie Tiger und Nilpferd, die auf eine
spätere Zoo-Runde warten. Sie sind bewusst weder gelöscht noch erzwungen platziert; wer
die Regel „kein Atom ohne Auftritt" ernst nimmt, räumt sie im nächsten Durchgang ab.

Nicht aufgenommen, obwohl auf der Liste: **Auge** und **Mund** — es gab keine Runde, in
die sie ohne Verrenkung passten, und ein neues totes Atom wäre genau der Fehler, den
Befund 2 beschreibt.

**Gelöscht:** Nanu, Tram, Quelle, Insel, Markt, Ofen, Xylofon, Yacht, Tal, wind, Mimi,
Kreis, Quadrat, Dreieck — dazu fünf nie benutzte Schablonensätze („Das Quadrat ist gelb.“).

## Was offen bleibt

**Audio.** Der Pack braucht rund **420 neue Clips** (Prompts, Belohnungssätze,
Satz-Versteher-Sätze, Finale-Sätze, Wort- und Artikelaufnahmen). `tools/tts/` findet sie
selbst über `tts extract` / `tts status`; kuratiert wird wie gewohnt im Web-Interface.
Bis dahin spricht die App die neuen Texte über Android-TTS.

**Sichtprüfung.** Unit-Tests und Build sind grün, am Gerät ist die neue Phase noch nicht
gelaufen — besonders der Wort-Bauer mit zwei langen Blöcken („Taschen“ + „lampe“) und die
Wort-Verschmelzung sind Kandidaten für Layout-Überraschungen bei font_scale 1.3.
