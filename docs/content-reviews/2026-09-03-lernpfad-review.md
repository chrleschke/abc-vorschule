# Content-Review: Lernpfad auf Plausibilität und Reihenfolge

Stand: 2026-09-03 · Geprüft: alle 34 Lektionen in `app/src/main/assets/content/`
(lessons, tasks, atoms, sentences, finales), Trainer für Trainer in Ablaufreihenfolge.

Prüfkriterien, wie vom Nutzer vorgegeben:

1. **Kausalität der Reihenfolge** — kommt jeder Baustein, bevor eine Runde ihn braucht,
   und stimmt die Schwierigkeitskurve innerhalb einer Phase?
2. **Plausibilität der Items** — ist jede Aufgabe das, was ihr Prompt behauptet
   (eine Silbe ist eine Silbe, ein Wort-Bauer hat etwas zu bauen, eine Rechenszene
   stimmt)?
3. **Sätze** — sinnvoll und lebensnah, **außer** wo Quatsch das Mittel ist: der
   Finale-Satz (lustig, nicht beliebig) und der Satz-Versteher, der kurios sein darf und
   dessen Vergangenheitsformen Absicht sind. Beides habe ich im ersten Durchgang falsch
   verstanden, siehe Befund 3.
4. **Wortschatz** — keine Wörter, die aus der Zeit gefallen sind.

Alle Befunde sind umgesetzt; 716 Unit-Tests grün. **Befund 3 wurde nach Rückmeldung des
Nutzers am 03.09. weitgehend zurückgenommen** — die Begründung steht dort und die Regel
dahinter jetzt in PRODUCT_PRINCIPLES §3.6.

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

## Befund 3 — Sätze (nach Nutzer-Korrektur größtenteils zurückgenommen)

Der erste Durchgang dieses Reviews hat elf Satz-Versteher-Runden als „unreal" und zehn
weitere als „falsche Zeitform" umgeschrieben. **Beides war falsch, und beides ist
zurückgenommen.** Die Korrektur kam am 03.09. vom Nutzer und ist jetzt in
PRODUCT_PRINCIPLES §3.6 dokumentiert, damit sie nicht wieder passiert:

1. **Der Satz-Versteher darf kurios sein.** Pony im Taxi, Frosch auf dem Fahrrad, Lama
   mit Hut, Krokodil, das den Keks frisst, Drache über dem Park — Kinder mögen Drachen,
   Krokodile und Kekse, und genau dafür schauen sie sich die Aufgabe an. Die Grenze ist
   Cartoon-Logik, nicht Alltagsrealismus. Die Sachlichkeitsregel, die ich hier angewandt
   habe, gilt für **Rechen-Szenen** (§3.7), nicht für Bildkarten.
2. **Die Vergangenheitsform ist der Zweck des Trainers.** Perfekt, Präteritum und
   Partizip II stehen dort mit Absicht: es ist die einzige Stelle im Pfad, an der ein
   Kind sie zu hören bekommt. Ich hatte eine engere Vorgabe von 2026-08 („Zwei Vögel
   saßen auf dem Baum" neben zwei sitzenden Vögeln) als Generalregel gelesen und zehn
   Sätze zu Unrecht ins Präsens gezogen.

**Alle 44 Satz-Versteher-Runden stehen damit wieder im Originalwortlaut.** Erhalten
bleiben nur Änderungen, die keinen Satz anfassen (Befund 4) und diese eine Ausnahme:

* **l28 „Der Kuchen ist für Oma." ✔🍰 ✘🎂** — eine Torte *ist* ein Kuchen, die falsche
  Karte war also nicht falsch. Jetzt „Mia hat eine Torte zum Geburtstag bekommen."
  ✔👧🎂 ✘👧🍪, im Perfekt wie die Nachbarrunden.

**Namen: Mateo und Lennard.** Der Pfad lief fast ausschließlich über Tom (18 Karten).
Vier Runden in späteren Lektionen tragen jetzt die beiden anderen Namen — früh bleibt es
bei Tom, das ist dort sinnvoll. Beide stehen **nur im Satz, nie auf einer Karte**: 👦
gehört Tom, und zwei Jungen mit demselben Glyphen sind für ein Kind dieselbe Karte
(§3 „Emoji-Doppelgänger"). Die Karten der vier Runden sind unberührt:

| | vorher | jetzt |
|---|---|---|
| l20 | Die Uhr liegt auf dem Sofa. | Lennard hat die Uhr auf das Sofa gelegt. |
| l22 | Der Roller steht vor dem Haus. | Mateos Roller stand vor dem Haus. |
| l29 | Zwei Kinder klettern ins Baumhaus. | Mateo und Lennard sind ins Baumhaus geklettert. |
| l33 | In der Schultasche liegt ein Buch. | Mateo hat sein Buch eingepackt. |

Erster Versuch für l29 war „Lennards Ball rollte über die Straße."; das hat
`SentencePictureSidesTest` abgefangen — die Seite der richtigen Karte hängt am Satz-Hash,
und der neue Wortlaut hätte alle vier Runden der Lektion auf dieselbe Seite gelegt („immer
rechts tippen"). Deshalb tragen die Namen die Baumhaus-Runde, die schon auf dieser Seite
lag, und der Ball-Satz bleibt unverändert.

**Satz-Architekt** (vom Nutzer abgesegnet): „Die Spinne spielt." (l17, l24) ist kein Satz
über Spinnen; jetzt „Der Stern ist hell." und „Die Spinne ist klein.". „Lama ist da."
(l07) wird „Tom hat eine Rose.", „Wir gehen den Weg." (l10) wird „Der Regen ist kalt.",
„Das ist mein Ei." (l22) wird „Der Eimer ist voll." — die drei folgen den neuen
Bauwörtern. Neu dazu: l03 bekommt überhaupt erst eine Satzrunde („Papa."), l33 eine
zweite („Wir gehen zur Schule.").

**Finale:** „Mama Maus mampft einen dicken Apfel!" bleibt — es ist der Lieblingssatz des
Kindes. Berechtigt war nur die Bildkritik: Mama Maus ist **eine** Figur, die Reihe zeigte
👩🐭🍎 also zwei. Jetzt 🐭🍎. „Die Schultasche fährt im Schulbus mit!" (l33) war weder
lustig noch ein Bild; mein erster Ersatz („Der Schulbus trägt eine Schultasche!") war es
genauso wenig. Jetzt: **„Die Schultasche frisst meine Brotdose!"** 🎒🍱 — eine Handlung
aus der Quatsch-Familie (klauen, mampfen, stecken) und ein Bild, das jedes Kind kennt,
das schon mal eine zerdrückte Brotdose ausgepackt hat. „Deine Nase ist rot wie eine
Rose." (l06) war der einzige Finale-Satz ohne Handlung und ohne Witz — jetzt „Die Rose
kitzelt Opas Nase!".

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

Hier war fast nichts zu tun, der Umbau vom 30.08. hat das Gröbste erledigt. Mein erster
Durchgang hatte **Radio** 📻, **Zucker** 🍬 und **Drache** 🐉 gestrichen; alle drei sind
zurück. Radio war im August ausdrücklich als Grenzfall zum Belassen entschieden, und
Zucker und Drache hingen an Sätzen, die nach der Nutzer-Korrektur wieder gelten. Was
tatsächlich weg ist:

* **`spielt`** — hing nur an „Die Spinne spielt." (Satz-Architekt, abgesegnet).
* **`rü`** — Silben-Atom, das kein Verschmelzer mehr erzeugt, seit Rübe raus ist.
* **`Weg`** bleibt als Atom, weil die Drachen-Runde es als Gegenkarte braucht; als
  Bauwort ist es durch **Regen** ersetzt (🛣️ war eine Autobahn, dieselbe wie `Straße`).

Neu: `eine`, `zur`, `kü`. Atome: 298 → **299**, weiterhin keines ohne Auftritt.

Bewusst **nicht** angefasst: Hut, Rose, Dose, Vase, Uhu — klassischer Fibel-Wortschatz,
nicht veraltet; Ufo, Pirat, Hexe, Dino, Drache — Motivationsfiguren, an den richtigen
Stellen.

## Was offen bleibt

- **Audio:** Jede geänderte Zeile ist eine neue Aufnahme — rund 60 Prompts, Sätze und
  Merksätze plus die neuen Wörter (Ameise, Eimer, Regen als Bauwort; `eine`, `zur`, `kü`). `tts extract` / `tts status` finden sie.
- **Sichtprüfung am Gerät:** Ameise (`A + mei + se`) und Eimer (vier Kacheln) im
  Wort-Bauer, Sonnenblume neu in l32 — Layout bei font_scale 1.3 nicht geprüft.
- **Fortschritt bestehender Installationen:** Die Phase-8-IDs sind umbenannt; ein Kind,
  das schon in l29–l32 war, sieht dort neuen Inhalt unter altem Fortschritt.
