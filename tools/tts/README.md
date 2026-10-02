# Qwen-TTS Audio-Pipeline

Erzeugt aus dem Content-Pack der App Sprachaufnahmen mit lokalem Qwen3-TTS.
**Die App wird davon nicht berührt** — hier entsteht nur ein Audio-Paket unter `out/`.

Design: `docs/superpowers/specs/2026-08-02-qwen-tts-audio-pipeline-design.md`

## Voraussetzung

Alles läuft mit dem Interpreter aus dem Qwen-venv:

```bash
alias tts="~/qwen-tts-test/.venv/bin/python $(git rev-parse --show-toplevel)/tools/tts/tts"
```

Die Mikrofon-Aufnahme-Kette (Resampling, Auto-Trim, Tonhöhen-Verschiebung) braucht
zusätzlich `scipy` und `librosa` im selben venv.

## Quickstart: Web-Interface

```bash
cd /Users/cleschke/projects/abc-vorschul-app
./start-tts-ui.sh
# Öffnet http://127.0.0.1:8420 in einem Browser
```

Das Skript `start-tts-ui.sh` im Project Root startet das Web-Interface direkt — keine
weiteren Befehle nötig. `tts extract` und `tts status` laufen bereits beim Start.
Beenden mit **Ctrl-C** im Terminal; offene Browser-Tabs blockieren den Exit nicht mehr.

## Ein Text, ein Clip

`audio/index.json` schlüsselt nach **Text**, nicht nach Fundstelle. Zwei Stellen mit
demselben Wortlaut teilen sich also automatisch eine Aufnahme — die 34 Lektionen mit
„Ordne das richtige Bild zu." brauchen genau einen Clip.

Daneben trägt `index.json` einen Block `variants`: Aufnahmen desselben Textes in einer
anderen Stimme, adressiert über `ClipIndex.lookup(text, variant)` statt über den
normalen Text-Schlüssel. `monster`-Clips landen unter `variants.monster` — und
zusätzlich unter `clips`, wenn kein anderes Profil denselben Text schon dort belegt.

Der Haken war das **Profil**: derselbe Text unter zwei Profilen sind zwei Renders, zwei
Kuratierungen — und in der App gewinnt nur einer (`export._collision_winner`). Der
Satz-Architekt stellt seinen Satz als `promptTts` in die Runde, während derselbe Wortlaut
als `tts` am Satz in `sentences.json` steht: „Tom singt." lief einmal als `prompt` und
einmal als `sentence`.

`extract.reads_as_bare_sentence` entscheidet das jetzt an der Quelle: ein `promptTts`, das
keine Aufgabenansage ist (kein „Baue das Wort", „Zeichne den", „Ordne " …)
und auf `.`/`!`/`?` endet, bekommt gleich das Profil `sentence`. Das ist auch inhaltlich
richtig — die fragende Prompt-Melodie wäre für einen Aussagesatz ohnehin falsch. Dieselbe
Funktion benutzt `export._pedagogical_winner`, damit es eine Wahrheit gibt.

Wer neuen Content schreibt, prüft das mit
`tests/test_extract.py::test_no_text_is_rendered_under_two_profiles_by_accident`: erlaubt
ist genau eine Doppelung, der Laut `Ei` und das Wort „Ei" — die klingen gleich.

Zweite Falle derselben Art: `stretchTts` läuft im Profil `phoneme` (es trägt den Laut, der
beim Ziehen gedehnt wird). Steht dort ein ganzes Wort, kollidiert es mit dem Wortclip —
und `phoneme` gewinnt, das Kind hörte also die Lautdehnung, wo das Wort gemeint war.

Wort-Bauer-Bausteine, deren Aufschrift nicht ihr Atom ist („Bär" = B + „är", das Atom von
„är" ist `letter-ae`), sprechen in der App ihre Aufschrift (`SpeechClipText.forWordBlock`).
Seit Oktober 2026 extrahiert `extract.word_block_speech` genau diese Aufschriften als Feld
`blockTts` (Profil `phoneme`). Vorher tauchten sie im TTS-UI nicht auf, und die App fiel
für sie auf Android-TTS zurück. Gibt es den Text schon als anderen Clip (Groß/klein egal,
wie `ClipIndex.lookup`), entsteht kein zweiter.

## Ablauf

```bash
tts extract                        # Content-JSON → out/manifest.json
tts migrate-locks                  # word:*-Locks für Buchstaben/Silben → phoneme:*
tts wire-locks                     # Produktions-WAV ohne Lock → Lock (Batch-Nachzügler)
tts status                         # Überblick: fehlt / fertig / Pools / Locks
                                   # plus Fehlschläge des letzten Laufs und leere Texte
tts sample --profile prompt -n 8   # 8 Seeds an 3 Beispielen des Profils ausprobieren
tts web                            # Kuratieren unter http://127.0.0.1:8420
tts render                         # Batch-Lauf über alles, inkrementell, ca. 25–40 Minuten
tts export                         # bestätigte, fertige Clips nach app/.../assets/audio/
```

**`tts render` lässt Kuratiertes liegen.** `out/` ist gitignored; auf einem frischen
Checkout fehlt jede Produktions-WAV, auch die der längst exportierten Clips. Damit der
nächste Export keine committete `.ogg` durch einen neuen Qwen-Wurf ersetzt, überspringt
`render` (Begründung pro Grund in der Ausgabe, „bewusst nicht gerendert"):

- Mikrofon-Clips — Profil mit `source: "mic"`, Mikrofon-Sidecar oder (ohne lokale
  Produktions-WAV, also ohne Sidecar zum Nachsehen) Lock-Seed ab `mic.MIC_SEED_MIN` —
  **immer**, auch mit `--force`. Eine lokale Qwen-WAV mit Hash-Seed in diesem Bereich
  rendert `--force` dagegen neu;
- gelockte Clips, deren Asset schon unter `app/src/main/assets/audio/` liegt — außer mit
  `--force`, das weiterhin bewusst alles (Nicht-Mikrofon) neu rendert.

`render` schreibt die Produktion und spricht darum den Produktionstext (`textOverride`),
nie einen Entwurf (siehe „Aussprache und Stimme").

Typisch: einmal `sample` pro Profil, im Web-Interface Kandidaten anhören und mit 👍
bewerten — 👍 speichert die Bewertung **und** nimmt den Seed automatisch in den
Seed-Pool des Profils auf (👎 löscht die Probeaufnahme und räumt den Pool wieder auf —
außer ein anderer Clip desselben Profils hält den Seed noch mit 👍; dasselbe beim
Zurücknehmen von 👍). Ein neuer Wurf auf einem schon vorhandenen Seed behält dessen 👍.
Einzelne schlechte Clips mit „🎲 Generate" (Anzahl einstellbar, 1–16) neu
erzeugen: die Probeaufnahmen stehen als Tabelle, neueste zuerst, mit Erzeugungszeitpunkt,
Stimme und Text — so bleiben mehrere Würfel-Runden auseinanderhaltbar. Der
Schalter in der Spalte **„Auswahl"** übernimmt genau eine Aufnahme sofort als
Produktions-Audio und lockt ihren Seed (kein Re-Render, kein erneutes Anhören nötig);
die ausgewählte noch einmal angeklickt hebt die Auswahl wieder auf (einen eigenen Knopf
„Keine Produktion" gibt es nicht mehr). Gibt es die Aufnahme nur als Produktion, ohne
eigene Probeaufnahme, fragt das Abwählen nach — es löscht dann die einzige Kopie.

Woher die Seeds beim Würfeln kommen, entscheiden zwei Häkchen neben „🎲 Generate" —
angehakt wird höchstens eines, das UI hakt das andere ab:

| Häkchen | Quelle | Leer? |
| ------- | ------ | ----- |
| *(keins)* | frische Zufalls-Seeds, Pool ausgenommen | — |
| `Use known seeds` | Seed-Pool des Profils (die 👍-Bewertungen) | Zufalls-Seeds |
| `Use top seeds` | die am häufigsten **gelockten** Seeds des Profils, zufällig gezogen | Zufalls-Seeds |

„Top-Seeds" zählt echte Produktions-Entscheidungen statt Bewertungen: derselbe Seed
unter mehreren Locks desselben Profils hat mehrfach überzeugt. Genommen werden die
besten `TOP_SEED_LIMIT` (10) Ränge; punktgleiche Seeds am Schnitt kommen alle mit,
denn zwischen ihnen gibt es keinen Grund zu wählen. Beide Häkchen zusammen wären
bedeutungslos — Top-Seeds gewinnen —, deshalb schließen sie sich im UI aus.
Reicht die Quelle nicht für die bestellte Anzahl, wird mit Zufalls-Seeds aufgefüllt,
statt stillschweigend weniger zu liefern.

**Fester Seed:** Im Feld „Fester Seed" (Clip-Details, neben Generate) kann ein Wert
0–2147483647 eingetragen werden — gespeichert in `locks.json` als `generateSeed`.
Solange er gesetzt ist, erzeugt Generate **nur diesen einen** Seed (Anzahl und beide
Häkchen werden ignoriert). Leer = wie bisher. Ungültige Werte werden abgewiesen, ein
fester Seed gleich dem Produktions-Seed mit HTTP 409 (der neue Wurf läge sonst unter
demselben Seed wie die Produktion, klänge aber anders — erst in „Auswahl" abwählen).

**Stille wegschneiden (Trim):** Im Web-Interface das Häkchen „Stille trimmen" neben
Generate — ein globaler Schalter wie „Use top seeds" (im Browser gemerkt, gilt für jedes
Generate und den Batch-Lauf, bis man ihn ändert; nie gesetzt = Vorgabe des Profils). Im
Profil-Formular steht Trim deshalb nicht mehr. `trim` im Profil ist nur noch die Vorgabe. Generate
(`POST /api/clips/{key}/candidates`) und Batch-Lauf (`POST /api/render`) nehmen ein
optionales `"trim": true|false` für genau diesen Wurf; fehlt es (oder `null`), gilt das
Profil, alles andere ist HTTP 422. Was tatsächlich galt, steht im Sidecar als
`trimSilence` und in `/api/state` pro Kandidat (`null` bei Kandidaten von vorher).
„⚠️ alt" und das `verified` von Promote rechnen mit diesem Wert
statt mit dem Profil — ein bewusst ungeschnittener Wurf ist also nicht „alt". Der Export
braucht ihn nicht: er geht nach dem Inhalt der Produktions-WAV (siehe „Export: wann wird
neu encodiert?"). `tts render` schneidet
weiter nach Profil. Nicht zu verwechseln mit dem Schnitt in der Wellenform (Sidecar
`trim`, siehe unten).

Generate und Batch-Lauf lösen Clip (Text, Stimme, Profil) und Seeds erst auf, wenn der
Job **läuft**, nicht beim Einreihen — wer hinter einem langen Lauf weiter kuratiert,
bekommt den aktuellen Stand. Ist das Modell nicht geladen, antworten beide sofort mit
**HTTP 503** und dem Ladefehler im `detail` (statt eines Jobs, der jeden Kandidaten
einzeln scheitern lässt); Mikrofon-Aufnahmen, Kuratieren und Export gehen weiter. Ein
abgebrochener Job meldet den Rest in `job-summary` als `cancelled`, nicht als `failed`.

Erzeugt wird über den **Batch-Lauf**: links in der Liste Clips ankreuzen (einzeln
oder über „Alle / Keine", Shift-Klick wählt einen Bereich), Anzahl Beispiele pro Clip
einstellen (Default 2),
dann „▶ Batch-Lauf" in der Kopfzeile — angefasst wird nur, was noch fehlt.
Der Batch-Lauf nutzt immer die **Top-Seeds**-Logik (wie „Use top seeds" bei Generate):
zufällig gezogen aus den am häufigsten gelockten Seeds des Profils; hat das Profil
noch keine Locks, fällt er wie Generate auf frische Zufalls-Seeds zurück.
In der Clip-Liste zeigt ein **Spinner** pro Zeile, solange für genau diesen Clip
Kandidaten erzeugt werden (eigenes Generate oder der Clip, an dem der Batch-Lauf
gerade arbeitet); ein blasses **⏳** heißt „kommt im Batch-Lauf noch dran". Ist der
Clip fertig und noch nicht geöffnet, erscheint eine **Zahl** — wie viele neue
Aufnahmen seit dem letzten Öffnen dazukamen (beim Anklicken verschwindet sie, auch
ohne Anhören). Das 📌-Symbol für festgelegte Seeds entfällt in der Liste (in der
Detailsicht bleibt es).

**Review während der Lauf läuft:** der Server meldet jeden Clip einzeln, sobald er
fertig ist — die UI lädt dann sofort nach. Ein abgearbeiteter Clip ist also
mitten im Lauf schon anzuhören, zu bewerten, zu bestätigen und zu löschen, und sein
„🎲 Generate" ist wieder klickbar: die Anfrage reiht sich hinter dem Lauf in die
Warteschlange ein (der Knopf sagt dann „⏳ In der Warteschlange …"). Genau der Fall,
wenn alle Aufnahmen eines Clips nichts geworden sind und man sofort neue braucht.
Blockiert ist nur der Clip, an dem der Lauf gerade arbeitet. Das Nachladen lässt eine
gerade abgespielte Aufnahme in Ruhe: die Detailsicht wird nur neu gezeichnet, wenn
sich an ihrem Clip wirklich etwas geändert hat.

Der Lauf erzeugt Kandidaten wie „🎲 Generate", aber für alle ausgewählten Clips
auf einmal; er schreibt nie direkt in die Produktion. Die Entwürfe stehen danach in
derselben Kandidaten-Tabelle wie jede andere Probeaufnahme — dort in der Spalte
„Auswahl" bestätigen. Ohne Bestätigung bleibt der Clip „fehlt"; eine
Festlegung ohne Hörarbeit fällt von selbst weg, sobald die Aufnahme ihres Seeds gelöscht
ist und keine andere mehr übrig (keine eigene „Lock entfernen"-Aktion nötig). Wer auf
einem frischen Checkout neu gewürfelte Kandidaten wieder löscht, behält Lock und Export.
Da viel von Hand korrigiert wird, gibt es bewusst keinen „finalen Lauf" über alles mehr;
die Auswahl bestimmt den Umfang.

Die Detailsicht ist auf **Erzeugen und Bestätigen** ausgerichtet: ganz oben steht der
Satz aus dem Content-Pack als Titel; in der Hauptkarte folgen TTS-Textfeld (Auto-Save),
Profil-, Stimmen- und Sprachwahl, Generate (mit „Stille trimmen") und Kandidaten-Tabelle
(Auswahl/👍); „🧹 Aufräumen" links unter der Tabelle entfernt ohne Rückfrage alle
ungeschützten Probeaufnahmen (ohne 👍, nicht ausgewählt); Abwählen in „Auswahl"
hebt eine bestätigte Aufnahme wieder auf — auch für den Export: ein Lock ohne Hörarbeit
fällt weg, ein kuratierter (Aussprache, Stimme, Profil, Notiz, fester Seed) bleibt mit
`"cleared": true` stehen, gilt nicht mehr als gelockt, und `tts export` entfernt seine
`.ogg` samt Index-Eintrag; erst ein neuer Promote gibt ihn wieder frei. Darunter die
Profil-Zusammenfassung (Bearbeiten klappt das Formular auf). Bewertungen,
Locks und Profile liegen in Dateien (Sidecar-JSONs, `locks.json`, `profiles.json`) und
überleben damit Server- und Browser-Neustart; Filter, Batch-Auswahl und die Breite der
Clip-Liste (Trenner zwischen den Spalten ziehen, Doppelklick setzt zurück) merkt sich der
Browser lokal.

**Wellenform und Schnitt:** Jede Zeile der Kandidaten-Tabelle zeigt die Wellenform der
Aufnahme; ein Klick springt an die Stelle und spielt ab. Die orangen Griffe an Anfang und
Ende schneiden vorne und hinten — beim Loslassen ist der Schnitt gespeichert, ohne eigenen
Knopf (`PUT /api/clips/{key}/candidates/{seed}/trim`). Verlustfrei: der erste Schnitt sichert
die Aufnahme als `candidates/<key>/<seed>.orig.wav`, jeder weitere geht wieder vom Original aus,
und die Wellenform zeigt das Original mit blass abgesetzten Rändern — ein Griff lässt sich also
auch zurückziehen, ganz an den Rand gezogen kehrt das Original zurück. Ist der Kandidat die
Produktion, zieht `out/audio/<key>.wav` mit; der Schnitt steht im Sidecar (`trim`), und weil
er die Produktions-WAV ändert, ändert er auch deren Export-Fingerprint (`wav:`) — der nächste
Export encodiert neu. Ein neuer Wurf auf
demselben Seed verwirft den Schnitt samt Original. Mikrofon-Aufnahmen schneidet weiter ✂.

**Tastatur:** `j`/`k` blättern, Leertaste spielt die Produktion (ohne Produktion die
erste Aufnahme) bzw. hält an, `1`–`9` spielen Kandidaten — immer nur eine Aufnahme
zugleich. **Esc** verlässt ein Textfeld; vorher landete das nächste `j` als Buchstabe in
der Aussprache und wurde 600 ms später gespeichert. Die Kürzel gelten auch, solange
Radio, Häkchen oder Knopf den Fokus haben; hat der Auswahl-Schalter nach einem Klick den
Fokus, spielt die Leertaste trotzdem ab, statt die Auswahl wieder aufzuheben.
Cmd/Ctrl/Alt-Kombinationen bleiben dem Browser.

**Clip-Liste:** Beim Hovern erscheint ▶ — spielt die Produktion, sonst die neueste
Aufnahme, ohne den Clip zu öffnen. Die Batch-Auswahl gilt nur für Sichtbares: „Alle"
nimmt die gefilterten Clips, und ein Filterwechsel wählt Unsichtbares ab.

**Wenn etwas noch speichert:** Ein noch nicht gespeicherter Text oder fester Seed wird
vor Generate, Produktion, Löschen und Batch-Lauf sofort gespeichert — Generate erzeugt
also mit dem gerade getippten Text. Geänderte Profil-Formulare (⚙️ und Profilkarte)
überstehen das Nachladen während eines Batch-Laufs. Export, Batch-Lauf und
„Übernehmen" im Aufnahme-Editor sind gesperrt, bis ihre Anfrage durch ist. Reißt die
Verbindung zum Server ab, sagt das ein Banner; nach einem Server-Neustart räumt die
Oberfläche Warteschlangen-Anzeigen ab, die es nicht mehr gibt.

**Wichtig:** Instruktion/Sampling im Profil zu ändern, wirkt sich **nicht** auf schon
gerenderte oder bestätigte Clips aus — nur auf künftige Generierungen. Details dazu und
warum das Absicht ist: „Profil-Updates und bestätigter Content" unten.

**Mit `phoneme` anfangen.** Das ist Absicht: mit 80 Clips ist es überschaubar und
zugleich das riskanteste Profil — gefragt ist der *Lautwert*, nicht der Buchstabenname
(„mmmmm", nicht „Em"). Klappt das per Instruktion nicht, greift die Aussprache-Eingabe
als Notausgang. Das weiß man dann nach ein paar Minuten und nicht nach 25–40 Minuten
Rendern.

## Aussprache und Stimme

In der Detailsicht steht der **Satz** aus dem Content-Pack ganz oben als Titel; in der
Hauptkarte darunter das **TTS-Textfeld** — genau der Text, der ans Modell geht. Änderungen
speichert die Oberfläche nach kurzer Pause automatisch (600 ms Debounce) über
`POST /api/clips/{key}/lock` mit `{"draftText": …}`. Der Lock trägt zwei Texte:

| Feld | Bedeutung |
| --- | --- |
| `textOverride` | Text der **bestätigten Produktion** (fehlt = der Satz). Steckt in deren Render-Fingerprint („⚠️ alt", `verified`). |
| `draftText` | **Entwurf** für neue Aufnahmen — Generate, Batch-Lauf und `tts sample` sprechen `draftText ?? textOverride ?? Satz`. |

Ein Probesatz im TTS-Feld ändert so nie die schon freigegebene Aufnahme (Fingerprint,
Export, „⚠️ alt"). **Promote** übernimmt als `textOverride` den Text, mit dem der
Kandidat entstand (Sidecar `text`; fehlt er — Nachbau-Eintrag, Mikrofon —, bleibt der
bisherige) und streicht einen Entwurf, der ihm gleicht. Ein Entwurf gleich dem
Produktionstext oder leer wird gar nicht erst gespeichert. „⚠️ alt" vergleicht jeden
Kandidaten mit den heutigen Einstellungen für *seinen eigenen* Text — ein neuer Entwurf
macht also nichts alt; welcher Text gesprochen ist, zeigt die Text-Spalte. `/api/state`
liefert pro Clip `text` (Produktion), `draftText` (oder `null`) und `generationText`.
`textOverride` bleibt über die API schreibbar (Handarbeit, Altbestand); Locks ohne
`draftText` verhalten sich wie bisher.

`seed` ist bei `/lock` **optional** und ändert den Lock-Seed nur, wenn er im Body steht;
sonst bleibt der des Locks, ohne Lock wird der aufgelöste Seed des Clips festgenagelt.
Vorher konnte ein verspätetes Autosave mit dem Seed von vor einem Klick einen gerade
gemachten Promote zurücknehmen. Weicht der TTS-Text vom Satz ab, erscheint in der
Clip-Liste eine zweite Zeile.

Die **Stimme** ist an drei Stellen wählbar — pro Clip in der Detailsicht, pro Profil in
der Profilkarte darunter und in „⚙️ TTS-Parameter". Hinter jedem Namen steht die Herkunft
der Stimme:

| Stimme | Herkunft |
| --- | --- |
| `ryan`, `aiden` | englisch, männlich |
| `serena`, `vivian` | chinesisch, weiblich |
| `sohee` | koreanisch, weiblich |
| `ono_anna` | japanisch, weiblich |
| `uncle_fu` | chinesisch, männlich |
| `eric` | chinesisch, Sichuan-Dialekt |
| `dylan` | chinesisch, Peking-Dialekt |

**Eine europäische Frauenstimme gibt es nicht.** Der Checkpoint kennt neun
Stimmen; europäisch sind nur `ryan` und `aiden`, beide männlich. `serena` und
`vivian` standen hier bis August 2026 als „westlich, weiblich" — falsch, beide
sind chinesisch (Modellkarte: „Warm, gentle young female voice", Chinese). Wer
für Deutsch eine Frauenstimme braucht, wählt deshalb zwangsläufig einen Akzent.

**Der VoiceDesign-Checkpoint ist als Ausweg geprüft und verworfen.** Am
2026-08-30 gegen `sohee` gehalten: Stimme aus einer Textbeschreibung statt aus
einem festen Embedding (`Qwen3-TTS-12Hz-1.7B-VoiceDesign`, in `qwen-tts` über
`generate_voice_design`). Zwei Runden, fünf Stimmbeschreibungen, zuletzt fair
verglichen — `sohee` mit seinen handoptimierten `textOverride`-Werten gegen
VoiceDesign mit bloßem Satzpunkt. Ergebnis nach Gehör: **keine Verbesserung
der Aussprache.** Die Messung sprach dafür (Ausschussquote 1 % gegen 26 %,
und mit einer als „Erzieherin, mittleren Alters" beschriebenen Stimme 175 Hz
gegen 220 Hz), das Ohr dagegen — deutsche Problemwörter wie „Zaun" und
„Schuh" saßen weiterhin nicht, teils mit englischer Aussprache, und die
Stimmidentität wanderte zwei- bis zweieinhalbmal so weit wie bei einem festen
Embedding.

Die Versuchsskripte lagen unter `tools/tts/experiments/` und sind wieder
entfernt; wer nachsehen will, findet sie samt Messwerten in der History
(`git log --oneline --diff-filter=D -- tools/tts/experiments`). Bleibt als
Rest die Erkenntnis, dass MLX-Ports von Qwen3-TTS hier nichts ändern — es ist
derselbe Checkpoint in einem anderen Format, und das Problem ist die
Aussprache, nicht das Tempo.

Das ist keine Kosmetik. `language` setzt im Modell ein Sprach-Token (`german` → 2053) und
steuert damit die Phonologie; das Speaker-Embedding bringt trotzdem den Akzent seiner
Kernsprache mit. Bei einem ganzen Satz gleicht der Kontext das weitgehend aus, bei einem
einzelnen Laut gibt es keinen Kontext — dort schlägt der Akzent voll durch. Genau deshalb
klangen die `phoneme`-Clips mit der Voreinstellung `sohee` asiatisch, obwohl `german`
korrekt gesetzt war. Lässt eine nicht-europäische Stimme europäischen Text sprechen,
warnt die Oberfläche an Ort und Stelle.

Die Stimmtabelle steht in `ttskit/voices.py` und wird von `tests/test_voices.py` gegen die
Modell-Config im HF-Cache abgeglichen, damit sie nicht auseinanderläuft. Ein Stimmwechsel
pro Profil gilt für neue Kandidaten aller seiner Clips; ein Wechsel pro Clip trifft nur
diesen einen.

Die **Sprache** lässt sich genauso pro Clip überschreiben — etwa für ein Lehnwort, das mit
`german` eingedeutscht klingt: `POST /api/clips/{key}/lock` mit `{"language": "english"}`,
`null` (oder die Sprache des Profils) heißt „wie im Profil" und wird nicht gespeichert.
Erlaubt sind die Namen aus `languages` in `/api/state` (`voices.LANGUAGES`), sonst HTTP 422;
ein Tippfehler in `locks.json` bricht beim Laden mit Datei und Schlüssel ab. `/api/state`
liefert pro Clip die wirksame `language`. Generate, Batch-Lauf, `tts sample` und
`tts render` sprechen damit; im Fingerprint steht die wirksame Sprache, ohne Override also
dieselbe wie bisher — vorhandene Fingerprints bleiben gültig, nur die Kandidaten dieses
einen Clips werden bei einem Wechsel „⚠️ alt".

Ein **erster** Lock auf einem ungelockten Clip, der nur Hörarbeit trägt (Entwurf, Stimme,
Sprache, Profil), ist keine Freigabe: er steht mit `"cleared": true` in `locks.json`, der
Clip bleibt „fehlt". Bleibt davon nichts übrig (Sprache des Profils, leerer Entwurf),
entsteht gar kein Lock. Wer `seed` oder `textOverride` ausdrücklich schickt, legt wie
bisher fest.

## Mikrofon-Aufnahmen

Jeder Clip kann statt aus Qwen aus dem **Mikrofon** kommen: Umschalter „Quelle:
🎲 TTS | 🎙 Mikrofon" in der Detailsicht, vorbelegt aus `source` im Profil (`monster`
steht auf `mic`), pro Clip im Browser gemerkt. „● Aufnehmen" nimmt mono ohne
Rauschunterdrückung auf (AudioWorklet, max. 30 s); „■ Stopp" lädt die Aufnahme hoch
und öffnet den **Editor**: Wellenform der ganzen Aufnahme, weggeschnittene Ränder grau,
der automatische Stille-Schnitt als gestrichelte Linien, zwei ziehbare Griffe,
Tonhöhe in Halbtönen (Default `micPitchSemitones` des Profils, Tempo bleibt —
`librosa.effects.pitch_shift`), Normalisieren auf −1 dBFS. „▶ Anhören" spielt die
Bearbeitung; bei `monster` zusätzlich „▶ Laufzeit links/rechts" mit dem App-Pitch für Aufnahmen (je ±1 Halbstufe,
`mic.APP_MONSTER_PITCH`, Spiegel von `VoiceStyle.variantPitch`). „Übernehmen" schreibt den Kandidaten.

Eine Aufnahme **ist ein Kandidat**: „Auswahl", 👍/👎, „🧹 Aufräumen" und Export
funktionieren unverändert. Ihr „Seed" ist ein Pseudo-Seed ≥ 1 900 000 000
(`mic.MIC_SEED_MIN`; Qwen-Zufalls-Seeds bleiben darunter). Dateien unter
`out/candidates/<key>/`: `<seed>.raw.wav` (Rohaufnahme, 24 kHz mono, bleibt),
`<seed>.wav` (bearbeitet), `<seed>.json` mit `source: "mic"`, `edit`, `autoTrim` und
`fingerprint: "mic:<sha>"` der bearbeiteten Datei — nur der steuert das Re-Encoding im
Export. ✂ in der Kandidaten-Zeile öffnet den Editor erneut; ist die Aufnahme gerade
Produktion, zieht `out/audio/<key>.wav` mit. Auto-Trim rechnet relativ zum Rauschboden
(`mic.auto_trim`), anders als `audio.trim_silence` für Qwen-Ausgaben. Wurde die
`<seed>.raw.wav` von Hand gelöscht, meldet ✂ nur einen 404-Banner — es gibt kein eigenes
Flag dafür (Design-Doc §7 „Rohaufnahme fehlt" wurde darauf vereinfacht).

## Profil-Zuordnung

`FIELD_TO_PROFILE` in `ttskit/extract.py` mappt logische Felder auf Synthese-Profile.
**Lemma ist speziell:** Atome mit `kind: letter` oder `kind: syllable` landen im
Profil `phoneme` (Lautwert), alle anderen Lemmata im Profil `word`. Damit kollidieren
Buchstaben wie `M` und Silben wie `ma` nicht doppelt mit `phonemeTts`/`stretchTts` —
identischer Text im selben Profil wird zu einem Clip zusammengefasst.

Das Profil `monster` ist die Stimme des Laut-Fressers. Seine zwei Reaktionen („Bäh!",
„Mmmmh!", Feld `monsterTts` in `extra-strings.json`) sind Qwen-Clips mit `uncle_fu`.
Die *Laute* dagegen — ein Clip pro Graphem der `SoundPairs`-Tabelle
(`app/…/content/SoundPairs.kt`, per Regex gelesen) mit dem **Lemma** als Text („S",
„Sch") — werden **per Mikrofon aufgenommen** (siehe „Mikrofon-Aufnahmen"). Bis
September 2026 trugen die Buchstaben-Atome dafür eine Fake-Aussprache `soundTts`
(„sss", „schhh"); weder Qwen noch die Android-TTS sprachen sie brauchbar, und die
TTS liest „S" ohnehin besser als „sss". Im Index landen `monster`-Clips unter
`variants.monster`; die App sucht dort, sobald sie mit Monster-Stimme spricht, und
legt für diese Aufnahmen nur noch **eine Halbstufe** Laufzeit-Tonhöhe obendrauf (links
×0,944, rechts ×1,059, `VoiceStyle.variantPitch`); die volle Verschiebung 0.75/1.3 gilt
weiter für normale Clips und Android-TTS. Grund (2026-09-05, gemessen): Pitch-Shift
verschiebt bei Reibelauten das ganze Rauschspektrum — ein S bei −2 HS im Editor plus ×0.75
landete mit dem Schwerpunkt (4,8 kHz) genau auf dem originalen Sch. Der Editor bietet dafür
einen **Hochpass 120 Hz** (Butterworth 2. Ordnung, nullphasig) gegen Nahbesprechungs-Bass
und Brummen unter dem Laut; er geht wie Schnitt und Pitch in den Fingerprint ein.

Das Profil `article_word` trägt die Lösungswörter **mit Artikel** („das Haus"), die das
Erfolgs-Vorsprechen nennt. Es ist bewusst nicht `word`: dessen `max_new_tokens: 25` (≈ 2,0 s)
schneidet „die Erdbeere" — der längste der Artikel-Texte — ab, und die Instruktion muss
ausdrücklich verlangen, Artikel und Nomen als eine Einheit zu sprechen — abgesetzt klingt es
wie zwei aneinandergehängte Clips.
Ein Artikel-Item entsteht nur für Atome, die `SuccessSpeech` erreichen kann
(`word_build.targetAtomId`); die übrigen klassifizierten
Substantive stünden sonst dauerhaft als „fehlt" in `tts status` und würden echte Lücken
verdecken.

**Rechnen: Einleitung „Wie viel ist" + ganze Aufgabe als ein Clip**
(`MathPromptSpeech.taskText`, Profil `math`, einzeln im TTS-UI kuratiert). `extract`
baut pro `count_add`-Runde aus `left`/`right`/`operation` den Text „fünf plus zwanzig"
(`math_task_text`, Zahlwörter wie `GermanNumberWord`) im Feld `mathTaskTts`; gleiche
Aufgaben kollabieren zu einem Clip. Die Einleitung ist `mathPromptIntro` in
`extra-strings.json`, ebenfalls `mathTaskTts`. Der `promptTts`-Satz selbst wird nicht
extrahiert, und die Antwort (Lob + Zahlwort, `SuccessSpeech`) nutzt die
`countingNumber*`-Clips. Warum nicht Zahl · „plus" · Zahl aus Einzelclips: Qwen war in
Betonung und Stimme zu uneinheitlich, das Zusammengesetzte klang wie Stückwerk.

Beim Export (`ttskit/export.py`) darf derselbe gesprochene Text trotzdem nur einmal
im Index stehen. Gewinnt bei Kollisionen zuerst die pädagogisch passende Variante
(kurzer Satz-Architekt-Text → `sentence`, Buchstaben-Laut → `phoneme`, …); danach
verified Audio (Fingerprint = letzter Export); sonst `PROFILE_PRIORITY`
(`phoneme` vor `word`, sonst wie gehabt).

Der Verlierer einer solchen Kollision wird gar nicht erst encodiert, und der
Export räumt das Zielverzeichnis gegen den Index auf: eine `.ogg`, auf die kein
Index-Eintrag zeigt, findet die App nie und wandert nur ins APK. Der Lock des
Verlierers bleibt bestehen — ändert sich die pädagogische Regel, ist der Clip
mit demselben Seed wieder da.

Bestehende `word:*`-Locks und Kandidaten-Ordner für Buchstaben/Silben einmalig
umziehen: `tts migrate-locks` (siehe Ablauf oben). Clips mit Produktions-WAV aber
ohne Lock (typisch nach Batch-`render`): `tts wire-locks`, danach `tts export`.

## Umfang

`tts extract` liest die Strings des Content-Packs plus die Einträge aus
`extra-strings.json` (einige ohne eigenes `field` — die fallen auf das Profil `ui`) und
bündelt sie zu Clips: identischer Text im selben Profil kollabiert in einen. **Stand:
siehe `tts status`.** Hier stehen bewusst keine Zahlen mehr — jede neue Lektion, jedes
neue Atom und jeder neue Trainer verschiebt sie, und eine Zahl in der README ist nach
dem nächsten Content-Commit still falsch.

Der Größe nach tragen `word`, `prompt`, `sentence`, `reward`, `article_word`, `miss`
und `phoneme` den Löwenanteil; `finale` und `ui` sind eine Handvoll Clips. `monster`
liegt bei 26 Laut-Clips (ein Graphem je `SoundPairs`-Eintrag) plus 2 Reaktionen — mehr
als „eine Handvoll", aber weiterhin klein gegen die Text-Profile.

Ein voller `render`-Lauf dauert ungefähr 25–40 Minuten — je nach Profilmix. Ein kurzer
Satz braucht ~2,4 s, ein langer `finale`-Satz im Schnitt ~3,2 s; die einzelnen
`word`-Clips sind deutlich schneller. Eine einzelne Zahl wäre hier irreführend.

## Seeds

Die `qwen_tts`-API kennt keinen Seed-Parameter; Reproduzierbarkeit entsteht über
`torch.manual_seed()` unmittelbar vor der Generierung, bei Batchgröße 1. Empirisch
verifiziert — dreimal unabhängig während der Entwicklung reproduziert: gleicher Seed
→ bit-identisches float32-Audio auf `mps`/`bfloat16`. Abgesichert durch
`tests/test_engine.py`, hinter `TTS_SMOKE=1`.

Der Seed eines Clips wird so bestimmt:

```
seed = locks[clipKey].seed
       ?? seedPool[ sha256(clipKey + poolSalt) % len(seedPool) ]
       ?? sha256(clipKey + poolSalt) % 2**31        # Pool leer
```

Damit streuen die Clips über die kuratierten Seeds, bleiben aber über Läufe hinweg
identisch. `poolSalt` in `profiles.json` hochzählen würfelt bewusst alles neu.

## Dateien

| Datei | Im Git? | Inhalt |
| --- | --- | --- |
| `profiles.json` | ja | Instruktionen, Sampling, Seed-Pools — **kuratierte Entscheidungen** |
| `locks.json` | ja | pro Clip festgenagelte Seeds, Produktionstext `textOverride`, Entwurf `draftText`, optional `speaker`, `language`, `generateSeed` und `cleared` — **kuratierte Entscheidungen** |
| `extra-strings.json` | ja | hartkodierte Kotlin-Strings |
| `out/` | nein | Manifest, Render-State, Audio, Kandidaten — jederzeit neu erzeugbar |

`profiles.json` und `locks.json` nie automatisiert überschreiben: darin steckt Hörarbeit.

Kandidaten unter `out/candidates/` tragen seit dem UI-Redesign eine Sidecar-Datei
`{seed}.json` mit dem Erzeugungs-Fingerprint — inzwischen zusätzlich mit
Erzeugungszeitpunkt, Stimme, Text, Stille-Wegschneiden (`trimSilence`) und der
👍-Bewertung (`rating: "good"`), damit die
Kandidaten-Tabelle mehrere Würfel-Runden auseinanderhalten kann und Bewertungen einen
Neustart überleben. Der Auswahl-Schalter übernimmt die Aufnahme immer sofort
als Produktion und lockt den Seed; passt der Sidecar-Fingerprint nicht mehr zu den
aktuellen Einstellungen, markiert die Zeile das nur mit dem Hinweis-Chip „⚠️ alt" —
rein informativ, der Clip gilt trotzdem als fertig. Ein späteres Profil-Update
invalidiert nie bereits bestätigten Content (siehe „Profil-Updates und bestätigter
Content" unten).

Beide Dateien werden beim Laden geprüft. Ein Tippfehler — ein Lock auf ein Profil, das es
nicht gibt; ein Lock ohne `seed`; ein fehlendes `label` — bricht mit einer Meldung ab, die
Datei und Schlüssel nennt, statt später als nackter `KeyError` aufzuschlagen. Insbesondere
ersetzt eine leere oder abgeschnittene `profiles.json` **nicht** stillschweigend alle
kuratierten Seed-Pools durch die Defaults, sondern ist ein Fehler. Wer wirklich zurück auf
die Defaults will, löscht die Datei.

`textOverride`, `speaker` und `language` schickt das Web-Interface selbst (siehe „Aussprache und
Stimme" unten); `note` setzt man weiterhin per Hand:

```json
{ "version": 1, "locks": {
  "phoneme:9f2c1a7b4e08": { "seed": 991, "speaker": "serena", "textOverride": "mmmmm",
                            "note": "sprach sonst 'Em'", "sourceText": "M" }
}}
```

`POST /api/clips/{key}/lock` fasst nur die Felder an, die im Body stehen; `null` löscht
ein Feld ausdrücklich. Sonst würde ein Stimmwechsel die von Hand eingetippte Aussprache
mitlöschen — die UI bearbeitet beides an getrennten Stellen.

Aktuell liegen unter `out/audio/` bereits 18 gerenderte `finale`-Clips sowie ein
`candidates/`-Verzeichnis aus der Entwicklung (probeweise gewürfelte Kandidaten-Seeds).
Beides ist jederzeit löschbar; `tts render` bzw. `tts sample` erzeugen es bei Bedarf neu.

## Maximale Dauer (`max_new_tokens`)

Das Modell erfindet beim Aussprechen einzelner Buchstaben gelegentlich ganze
Sätze dazu. `max_new_tokens` deckelt die Aufnahme-Länge und macht solche
Ausrutscher hörbar kaputt statt unauffällig falsch.

- **1 Token = 80 ms Audio.** Hergeleitet aus dem 12-Hz-Tokenizer:
  `decode_upsample_rate 1920` bei 24 kHz, also `1920 / 24000`.
- Der Wert gilt für die **Rohgenerierung**, bevor `trim_silence` die Stille am
  Anfang und Ende wegschneidet. Die fertige Datei ist entsprechend kürzer.
- Es ist ein **harter Schnitt**: das Modell will weitersprechen und wird mitten
  drin gekappt. Erfundene Sätze werden also nicht verhindert, sondern fallen
  beim Kuratieren sofort auf.
- Fehlt der Schlüssel in `profiles.json`, gilt der Checkpoint-Default von 8192
  Tokens ≈ 655 s, also praktisch unbegrenzt.

In „⚙️ TTS-Parameter" wird der Wert in Sekunden eingegeben; gespeichert werden
Tokens.

## Wertebereiche

Alle Sampling-Parameter samt Grenzen, Typ und Erklärungstext stehen in
`SAMPLING_SPEC` in `ttskit/store.py` — eine Stelle, aus der sowohl die
Server-Prüfung als auch „⚙️ TTS-Parameter" gespeist werden. Ein neuer Parameter
braucht dort einen Eintrag und sonst nichts.

Geprüft wird auf beiden Wegen: beim Speichern über das Panel (HTTP 422 mit
Wertebereich in der Meldung) **und** beim Laden von `profiles.json`, weil die
Datei von Hand bearbeitet wird. Ein handgeschriebenes `max_new_tokens: 1` oder
ein nachträglich eingetragenes `do_sample: false` bricht darum mit einer Meldung
ab, die Datei, Profil und Parameter nennt, statt still leere oder immer gleiche
Audios zu erzeugen. Ein *unvollständiger* `sampling`-Block bleibt erlaubt — ein
fehlender Schlüssel heißt „Modell-Default", und das Panel füllt das Feld dann
mit der Voreinstellung aus der Registry.

`do_sample` und `subtalker_dosample` sind bewusst **nicht** editierbar:
greedy Generierung macht den Seed wirkungslos, womit Seed-Pool und
Kandidaten-Kuratierung ihren Sinn verlieren.

Weil `sampling` in den Fingerprint eines Clips eingeht, hat das Hinzufügen
dieser Parameter den Fingerprint jedes Profils verändert: vorhandene
Probeaufnahmen tragen in der Kandidaten-Tabelle deshalb jetzt den Hinweis-Chip
„⚠️ alt" („Mit älteren Einstellungen erzeugt"), obwohl drei der vier neuen Werte
die Checkpoint-Defaults des Modells sind und am Klang nichts ändern. Das ist
kein Grund, Kandidaten neu zu würfeln.

## Neustart nötig?

Nein. Das Modell wird einmal beim Serverstart geladen und hängt nur an
Checkpoint und Device. Sampling-Werte reisen pro Aufruf mit, und der Server
liest `profiles.json` bei jedem Request neu — gespeichert heißt ab der
nächsten Generierung wirksam. Ein Neustart ist nur für einen anderen
Checkpoint oder ein anderes Device nötig.

## Profil-Updates und bestätigter Content

`tts status`, `render` und `export` kennen nur zwei Zustände: `missing` (keine Datei
unter `out/audio/`) und `rendered` (Datei liegt da). Es gibt bewusst keinen dritten,
automatisch erkannten Zustand mehr für „Einstellungen haben sich seither geändert" —
eine Instruktion anpassen, einen Seed in den Pool aufnehmen oder Trim/Normalisierung
verändern sind **immer nur Verbesserungen für künftige Renders**, nie ein Seiteneffekt,
der bereits gerenderten oder gar bestätigten (gelockten) Content unbemerkt entwertet.
Wer eine bestehende Aufnahme wirklich neu erzeugen will, tut das bewusst: `tts render
--force` (ggf. mit `--only`) oder die Datei unter `out/audio/` löschen.

Instruktion und Sampling-Parameter werden in der Praxis nur angefasst, wenn man mit
dem aktuellen Ergebnis nicht weiterkommt — nicht routinemäßig. Ein bereits verifizierter
(gehörter, bestätigter) Clip ist mit **seinen** damaligen Einstellungen korrekt und bleibt
es auch nach einer späteren Profil-Änderung; er muss dafür nicht neu gerendert werden.
Änderungen an Profil-Werten sind also gezielt und wirken nur nach vorn, nie rückwirkend
auf schon abgenommene Arbeit — deshalb bleibt „ich ändere die Instruktion und höre keinen
Unterschied" am erwarteten Verhalten, solange man nicht bewusst `--force` neu rendert
(oder die Kandidaten-/Promote-Route über „🎲 Generate" nutzt).

Das gilt auch für `POSTPROCESS_VERSION` (Trim-Schwellwert, Trim-Polster,
Normalisierungsziel in `ttskit/audio.py`): eine Änderung wirkt nur auf Clips, die
danach neu gerendert werden, nie rückwirkend auf vorhandene Dateien.

### Export: wann wird neu encodiert?

Und auch nicht auf die App-Assets: wer Profil-Einstellungen ändert, bekommt beim nächsten
Export **keine** neu encodierten Dateien. `index.json` merkt sich pro Clip einen
Fingerprint der **Produktions-Audio selbst** (`export.export_fingerprint`), und nur wenn der
sich ändert (oder die `.ogg` fehlt), wird neu encodiert:

| Clip | Fingerprint | ändert sich durch |
| --- | --- | --- |
| Qwen | `wav:<sha>` über Samples + Rate von `out/audio/<key>.wav` | neuen Wurf übernehmen, Schnitt in der Wellenform, `tts render --force` |
| Mikrofon | `mic:<sha>` aus dem Sidecar (`mic.fingerprint_of`) | ✂ neu schneiden/pitchen, neue Aufnahme übernehmen |

Instruktion, Sampling, Seed-Pool, ein Entwurf im TTS-Feld — nichts davon ändert eine
vorhandene WAV, also auch nichts am Export. Das musste so sein: OGG/Opus-Bytes sind pro
Encode verschieden (zufällige Bitstream-Seriennummer), ein unnötiger Re-Encode erzeugt also
hunderte Diffs ohne hörbaren Unterschied. Bis Oktober 2026 stand im Index der
Render-Fingerprint (`plan.fingerprint`, mit der *heutigen* Profil-Instruktion und
-Sampling) — eine Änderung an `profiles.json` encodierte damit 302 unveränderte Clips neu.

**Einmal-Migration.** Index-Einträge mit einem Fingerprint ohne `wav:`/`mic:` (Altformat)
werden nicht blind neu encodiert: der Export encodiert die Produktions-WAV im Speicher,
decodiert beides und vergleicht (Länge ± 30 ms, Korrelation ≥ 0.98; dieselbe WAV ergibt
bit-gleiche Samples, ein neuer Wurf liegt bei ≤ 0.2). Klingt die committete `.ogg` gleich,
bleibt sie liegen und nur ihr Index-Eintrag bekommt den `wav:`-Fingerprint
(`ExportReport.migrated`, in CLI und Banner als „nur im Index umgestellt"). Der erste Export
nach der Umstellung ändert deshalb fast nur `index.json` und dauert einmalig rund eine Minute.
Gelockte Clips ohne lokale WAV (frischer Checkout) behalten ihren Alt-Eintrag, bis die WAV da ist.

## Tests

```bash
cd tools/tts
~/qwen-tts-test/.venv/bin/python -m pytest tests/ -v            # ohne Modell
TTS_SMOKE=1 ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -v # mit Modell
```

## Bekannte Einschränkungen

- **Jeder Override erzwingt ein Seed-Lock.** Ändert man im Web-Interface das Profil, die
  Stimme oder die Aussprache eines Clips, entsteht automatisch ein *Lock*, das den zu
  diesem Zeitpunkt aufgelösten Seed festnagelt — der kann ein ungeprüfter Hash-Fallback
  sein. Ursache: `store.Lock` verlangt zwingend einen `seed`, ein Override lässt sich
  also nicht ohne Seed-Pinning ausdrücken (die API nimmt `seed` inzwischen optional
  und pinnt dann selbst). Wie das sauber gelöst wird, ist noch offen.
- **„Festlegung (Lock) entfernen" entfernt alles.** Der Knopf löscht den ganzen
  Lock-Eintrag, also auch eine eigene Aussprache und eine eigene Stimme, nicht nur den
  Seed. Der Tooltip sagt es, der Knopftext nicht.
- **Dateirechte wechseln auf 0600.** Nach einem Save bekommen `profiles.json` und
  `locks.json` die Rechte `0600` (Folge der atomaren Schreibimplementierung über
  `tempfile.mkstemp`), während Git sie mit `0644` auscheckt. Für ein Einzelnutzer-Tool
  harmlos; Git verfolgt den Unterschied ohnehin nicht.
- **Kein Lernen aus bewährten Werten außer Seeds.** „Top-Seeds" (siehe oben) wertet
  gelockte Produktions-Entscheidungen aus, aber nur für den Seed. Für Instruktion und
  Sampling-Parameter gibt es keine Entsprechung: welche Formulierung oder welcher
  Parameterwert über mehrere Profile/Clips hinweg tatsächlich zu einer Bestätigung
  geführt hat, wird nirgends erfasst oder vorgeschlagen — jede Anpassung stützt sich
  allein auf Erinnerung und erneutes Anhören. Idee für später: analog zu Top-Seeds
  auswerten, welche Instruktions- bzw. Sampling-Werte bei gelockten Clips gehäuft
  auftreten, und das beim Bearbeiten eines Profils als Vorschlag anzeigen.

## Bekannte Lücke

Die Sprechtexte von Symbol-Jagd und Wort-Detektiv sind Templates
(z. B. `"Finde den Buchstaben - %s - im Wort - %s."` bzw. `"Finde den Laut - %s - im Wort - %s."`
für Mehrzeichen-Grapheme), die erst zur Laufzeit befüllt werden.
Sie sind nicht abgedeckt; `tts status` weist darauf hin. Geplant ist, sämtliche
Kombinationen vollständig vorzurendern statt Clips zur Laufzeit aus Fragmenten
zusammenzusetzen — aneinandergehängte Sprachfragmente klingen abgehackt. „📦 In App
exportieren" (oder `tts export`) schreibt alle bestätigten, fertig gerenderten Clips als
OGG/Opus nach `app/src/main/assets/audio/` zusammen mit einer `index.json`; die App spielt
diese Clips ab und fällt für alles andere weiterhin auf Android-TTS zurück, sodass die
Abdeckung nicht vollständig sein muss.
