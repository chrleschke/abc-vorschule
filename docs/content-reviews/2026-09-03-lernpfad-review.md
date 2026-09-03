# Content-Review: Lernpfad auf Plausibilität und Reihenfolge

Stand: 2026-09-03 · Geprüft: alle 34 Lektionen in `app/src/main/assets/content/`
(lessons, tasks, atoms, sentences, finales), Trainer für Trainer in Ablaufreihenfolge.

Prüfkriterien, wie vom Nutzer vorgegeben:

1. **Kausalität der Reihenfolge** — kommt jeder Baustein, bevor eine Runde ihn braucht,
   und stimmt die Schwierigkeitskurve innerhalb einer Phase?
2. **Plausibilität der Items** — ist jede Aufgabe das, was ihr Prompt behauptet
   (eine Silbe ist eine Silbe, ein Wort-Bauer hat etwas zu bauen, eine Rechenszene
   stimmt)?
3. **Sätze** — Satz-Architekt und Satz-Versteher lebensnah und sinnvoll; **nur der
   Finale-Satz darf unreal sein**, und dann lustig, nicht beliebig.
4. **Wortschatz** — keine Wörter, die aus der Zeit gefallen sind.

Alle Befunde sind umgesetzt; Validator und `LessonCoverageTest` sind grün.

---

## Befund 1 — Reihenfolge und Kausalität

**Grapheme (l01–l18) und Wiederholungen (l19–l26): in Ordnung.** Kein Wort-Bauer, kein
Verschmelzer, kein Satz-Architekt greift auf ein Graphem, das noch nicht dran war;
Funktionswörter (`einen`, `da`, `hat`) sind per Regel ausgenommen. Die
Buchstabenreihenfolge selbst bleibt unangetastet (Entscheidung aus dem Review vom
30.08.) — anzumerken bleibt, dass das E als häufigster Buchstabe erst in l07 kommt.

**Phase 8 war kausal richtig, aber in der Schwierigkeit gemischt.** Komposita mit
Fugen-n (Sonne**n**blume, Tasche**n**lampe) oder gekürztem Erstglied (Schul-tasche)
verlangen mehr als solche, deren Teile unverändert aneinanderstoßen (Hand+schuh). Die
Sonnenblume stand an dritter Stelle, vor fünf einfachen Komposita. **Neue Reihenfolge:**

| | vorher | jetzt |
|---|---|---|
| l27 | Handschuh · Hausschuh | Handschuh · Hausschuh |
| l28 | Apfelkuchen · Brotdose | Apfelkuchen · Brotdose |
| l29 | Regenjacke · Sonnenblume | **Fußball · Baumhaus** |
| l30 | Fußball · Baumhaus | **Eisbär · Vogelnest** |
| l31 | Eisbär · Vogelnest | **Schneemann · Schneeball** |
| l32 | Schneemann · Schneeball | **Regenjacke · Sonnenblume** |
| l33 | Schultasche · Schulbus | Schultasche · Schulbus |
| l34 | Mülltonne · Taschenlampe | Mülltonne · Taschenlampe |

Task- und Finale-IDs sind mitgewandert (`l29-*` ist jetzt Fußball). Die TTS-Locks
schlüsseln nach Text, nicht nach ID — dort ändert sich nichts. Abhängigkeiten geprüft:
Ball (l29) vor Schneeball (l31), Jacke (l27) vor Regenjacke (l32).

**Rechnen in l01** hatte eine Wegnehmen-Runde, obwohl §8 „Wegnehmen ab Lektion 2"
festlegt. Jetzt zwei Plus-Runden (3+2, 4+3). Malnehmen ab l06 ist dokumentierte
Entscheidung („bewusst steil") und bleibt.

**l03 hatte als einzige Basis-Lektion keinen Satz-Architekten** und sprang vom
Wort-Bauer zum Satz-Versteher. Jetzt wie l01/l02 eine Einwort-Runde („Papa").

## Befund 2 — Items, die nicht waren, was ihr Prompt behauptete

| Wo | Problem | Jetzt |
|---|---|---|
| l24 Verschmelzer | `s + t = st`, `s + p = sp` fragten „Welche Silbe entsteht" — das Ergebnis ist ein Laut, keine Silbe | Runden bleiben (sie zeigen, aus welchen Buchstaben St und Sp bestehen — wie Sch, Ch, ck); Prompt fragt „Welcher Laut entsteht." |
| l25 Verschmelzer | `r + ü = rü` — die Silbe kam in keinem Wort der Lektion vor (Rübe ist raus) | `k + ü = kü`, und Küken wird direkt daraus gebaut (`Kü + ken`) |
| l19 Wort-Bauer | „Baue das Wort am" mit **einem** Block — nichts zu bauen | **Ameise** = `A + mei + se`, drei Silben, nutzt die l09-Silbe `mei` |
| l22 Wort-Bauer | „Baue das Wort Ei" mit einem Block | **Eimer** = `Ei + m + e + r` — das Belohnungswort der Lektion |
| l10 Wort-Bauer | `Weg` 🛣️ — Emoji ist eine Autobahn (dieselbe wie `Straße`), Wort als Bild nicht fassbar | **Regen** = `Re + gen` — nutzt die frisch verschmolzene Silbe `ge`; Satz „Der Regen ist kalt." |
| Silbenschnitte | `Ze+b+ra`, `Bal+l`, `Bro+t`, `Bu+s`, `Schn+ee`, `Ster+n` | `Ze+bra`, `Ba+ll`, `B+r+o+t`, `B+u+s`, `Sch+n+ee`, `St+e+r+n` — Silbengrenze oder Einzellaute, nie dazwischen |
| l30 (jetzt l29) Spurensucher | „Zeichne den **Laut** ß" — ß ist ein Buchstabe (l15 sagt es richtig) | „Zeichne den Buchstaben ß" |
| l12 Satz-Architekt | „Hier sind Häuser" mit Bild 🏠 (Einzahl) | Bild 🏘️ |
| l12 Titel | „Umlaut-Rätsel (Mathematisch)" | „Ä, Ö & Ü (Die Umlaute)" |

**Merksätze in Phase 8** nannten das Kompositum („H wie Handschuh", „B wie Brotdose",
„Sch wie in Hausschuh"), obwohl §3 Regel 1 das kurze Wort verlangt. Jetzt: H wie Hand ·
Sch wie Schuh · B wie Brot · R wie Regen · S wie Sonne · ß wie in Fuß · B wie Baum · Ei
wie Eis · V wie Vogel · Sch wie Schnee · Ü wie in Müll. Dazu l18 „X wie Xylofon" 🎵 —
das Emoji zeigte eine Note, kein Instrument — jetzt „X wie in Hexe" 🧙.

## Befund 3 — Sätze, die unreal waren, ohne Finale zu sein

Der Satz-Versteher trug an elf Stellen Cartoon-Logik, die dem Kind eine falsche Welt
erzählt: **Pony fährt Taxi, Frosch fährt Fahrrad, Lama ruft Hallo, Lama frisst einen
Hut, Oma reitet auf dem Lama, Zebra findet ein Jojo, Schaf bekommt neue Schuhe,
Krokodil frisst Keks, Dino frisst Kuchen, Drache fliegt über den Park, Ufo landet im
Park / zwei Ufos am Himmel.** Alle ersetzt durch Szenen aus dem Kinderalltag oder aus
dem Sachbuch:

> Tom spielt mit dem Dino. · Mia fährt mit dem Fahrrad. · Papa ruft ein Taxi. · Tom ruft
> laut Hallo. · Das Lama frisst Gras. · Oma streichelt das Lama. · Mia spielt mit dem
> Jojo. · Das Schaf frisst einen Apfel. · Das Krokodil hat viele Zähne. · Opa geht mit
> dem Hund in den Park. · Der Fuchs sitzt unter dem Baum. · Zwei Flugzeuge fliegen am
> Himmel.

Pirat und Hexe (l08) bleiben — Spiel- und Märchenfiguren, keine Tiere, die Menschen
spielen. Das Ufo bleibt gebundenes Bauwort (l05) und Finale-Bild (l02).

**Zeitform folgt dem Bild** (§3.6) war an neun Stellen verletzt — Präteritum bei einer
Karte, die den Zustand zeigt: „Zwei Lamas liefen", „Der Drache flog", „Die Eule flog",
„Aus dem Haus kamen", „Der Zug fuhr", „Die Eule fing", „Opa trug", „Tom warf", „Das
Taxi hielt", „Mama malte". Jetzt Präsens oder natürliches Perfekt („Die Eule hat eine
Maus gefangen"). „Das Pferd sprang über das Tor" bleibt — ausdrücklich als natürlicher
Fall in §3 genannt.

**Satz-Architekt:** „Die Spinne spielt." (l17, l24) ist kein Satz über Spinnen; jetzt
„Der Stern ist hell." und „Die Spinne ist klein.". „Lama ist da." (l07, vor dem D von
„da") wird „Tom hat eine Rose." — vier Wörter, alle aus gelehrten Graphemen. Neu in l33:
„Wir gehen zur Schule." (bringt `gehen` zurück in Umlauf, neues Funktionswort `zur`). In l22
weicht „Das ist mein Ei." dem neuen Bauwort: „Der Eimer ist voll.".

**Finale:** „Mama Maus mampft einen dicken Apfel" zeigte 👩🐭🍎 — Mama Maus ist eine
Figur, die Bilder zeigten zwei. Jetzt „Die Maus mampft Mamas Apfel!". „Deine Nase ist
rot wie eine Rose." war der einzige Finale-Satz ohne Handlung und ohne Witz — jetzt „Die
Rose kitzelt Opas Nase!". „Die Schultasche fährt im Schulbus mit!" war zu alltäglich
für eine Belohnung — jetzt „Der Schulbus trägt eine Schultasche!".

## Befund 4 — Gegenkarten und Rechenszenen

**Sechs Gegenkarten tauschten Anzahl und Kategorie zugleich** (Regel: nur die
Kategorie): zwei Kekse gegen ein Eis (l08), zwei Tauben gegen eine Eule (l10), zwei
Bäume gegen einen Busch und zwei Mäuse gegen einen Dino (l11), zwei Äpfel gegen eine
Banane (l12), zwei Eulen gegen eine Maus (l14), zwei Vögel im Sand gegen einen im
Wasser (l15). Jetzt gleiche Anzahl, andere Kategorie.

**Hahn 🐓 gegen Huhn 🐔** (l16) sind für ein Kind dieselbe Karte — jetzt Hahn gegen
Ente. **Apfelkuchen 🥧 gegen Torte 🎂** und **Kuchen 🍰 gegen Torte 🎂** (l28) ebenso —
der Apfelkuchen steht jetzt gegen Pizza, und die Kuchen-Runde weicht einer, in der die
Torte die *richtige* Karte ist („Mia bekommt eine Torte zum Geburtstag").

**Rechnen:** „Sieben Eis … Wie viele Eis bleiben?" (l09) ist kein Deutsch — jetzt
sieben Eier im Korb (Ei ist ohnehin das Graphem der Lektion). „Fünf Omas und vier Omas"
(l02) zählt jetzt Igel, das Belohnungstier der Lektion. „Acht Tische im Café" (l23)
stehen jetzt in der Kita.

## Befund 5 — Wortschatz aus der Zeit

Wenig zu tun, der Umbau vom 30.08. hat das Gröbste erledigt. Raus sind jetzt noch
**Radio** 📻 (zweimal Gegenkarte; ein Kind von 2026 kennt Kopfhörer und Handy, kein
Radio), **Zucker** 🍬 (das Emoji ist ein Bonbon, die Runde „Der Zucker steckt in der
Dose" ergab kein Bild — jetzt „Die Socke liegt unter dem Bett"), **Weg** (siehe oben)
und **Drache** 🐉 (einziger Auftritt war die unreale Park-Runde). Gelöscht wurden
außerdem `spielt` und `rü`. Neu: `eine`, `zur`, `kü`.
Atome: 298 → **295**, weiterhin keines ohne Auftritt.

Bewusst **nicht** angefasst: Hut, Rose, Dose, Vase, Uhu — klassischer Fibel-Wortschatz,
nicht veraltet; Ufo, Pirat, Hexe, Dino — Motivationsfiguren, an den richtigen Stellen.

## Was offen bleibt

- **Audio:** Jede geänderte Zeile ist eine neue Aufnahme — rund 60 Prompts, Sätze und
  Merksätze plus die neuen Wörter (Ameise, Eimer, Regen als Bauwort; `eine`, `zur`, `kü`). `tts extract` / `tts status` finden sie.
- **Sichtprüfung am Gerät:** Ameise (`A + mei + se`) und Eimer (vier Kacheln) im
  Wort-Bauer, Sonnenblume neu in l32 — Layout bei font_scale 1.3 nicht geprüft.
- **Fortschritt bestehender Installationen:** Die Phase-8-IDs sind umbenannt; ein Kind,
  das schon in l29–l32 war, sieht dort neuen Inhalt unter altem Fortschritt.
