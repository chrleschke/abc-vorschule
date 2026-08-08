# Qwen-TTS Audio-Pipeline

Erzeugt aus dem Content-Pack der App Sprachaufnahmen mit lokalem Qwen3-TTS.
**Die App wird davon nicht berührt** — hier entsteht nur ein Audio-Paket unter `out/`.

Design: `docs/superpowers/specs/2026-08-02-qwen-tts-audio-pipeline-design.md`

## Voraussetzung

Alles läuft mit dem Interpreter aus dem Qwen-venv:

```bash
alias tts="~/qwen-tts-test/.venv/bin/python $(git rev-parse --show-toplevel)/tools/tts/tts"
```

## Quickstart: Web-Interface

```bash
cd /Users/cleschke/projects/abc-vorschul-app
./start-tts-ui.sh
# Öffnet http://127.0.0.1:8420 in einem Browser
```

Das Skript `start-tts-ui.sh` im Project Root startet das Web-Interface direkt — keine
weiteren Befehle nötig. `tts extract` und `tts status` laufen bereits beim Start.
Beenden mit **Ctrl-C** im Terminal; offene Browser-Tabs blockieren den Exit nicht mehr.

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

Typisch: einmal `sample` pro Profil, im Web-Interface Kandidaten anhören und mit 👍
bewerten — 👍 speichert die Bewertung **und** nimmt den Seed automatisch in den
Seed-Pool des Profils auf (👎 löscht die Probeaufnahme und räumt den Pool wieder auf).
Einzelne schlechte Clips mit „🎲 Generate" (Anzahl einstellbar, 1–16) neu
erzeugen: die Probeaufnahmen stehen als Tabelle, neueste zuerst, mit Erzeugungszeitpunkt,
Stimme und Text — so bleiben mehrere Würfel-Runden auseinanderhaltbar. Der
Radio-Button „Produktion" übernimmt genau eine Aufnahme sofort als Produktions-Audio
und lockt ihren Seed (kein Re-Render, kein erneutes Anhören nötig).

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
Häkchen werden ignoriert). Leer = wie bisher. Ungültige Werte werden abgewiesen.

Erzeugt wird über den **Batch-Lauf**: links in der Liste Clips ankreuzen (einzeln
oder über „Sichtbare / Alle / Keine"), Anzahl Beispiele pro Clip einstellen (Default 2),
dann „▶ Batch-Lauf" in der Kopfzeile — angefasst wird nur, was noch fehlt.
Der Batch-Lauf nutzt immer die **Top-Seeds**-Logik (wie „Use top seeds" bei Generate):
zufällig gezogen aus den am häufigsten gelockten Seeds des Profils; hat das Profil
noch keine Locks, fällt er wie Generate auf frische Zufalls-Seeds zurück.
In der Clip-Liste zeigt ein **Spinner** pro Zeile, solange für diesen Clip Kandidaten
erzeugt werden (Generate, Batch-Lauf oder Warteschlange); ist er fertig und der Clip
noch nicht geöffnet, erscheint eine **Zahl** — wie viele neue Aufnahmen seit dem
letzten Öffnen dazukamen (beim Anklicken verschwindet sie, auch ohne Anhören). Das
📌-Symbol für festgelegte Seeds entfällt in der Liste (in der Detailsicht bleibt es).
Der Lauf erzeugt Kandidaten wie „🎲 Generate", aber für alle ausgewählten Clips
auf einmal; er schreibt nie direkt in die Produktion. Die Entwürfe stehen danach in
derselben Kandidaten-Tabelle wie jede andere Probeaufnahme — dort per Radio-Button
„Produktion" bestätigen. Ohne Bestätigung bleibt der Clip „fehlt"; eine
Festlegung fällt von selbst weg, sobald keine Aufnahme des Clips mehr übrig ist (keine
eigene „Lock entfernen"-Aktion nötig). Da viel von Hand korrigiert wird, gibt es bewusst
keinen „finalen Lauf" über alles mehr; die Auswahl bestimmt den Umfang.

Die Detailsicht ist auf **Erzeugen und Bestätigen** ausgerichtet: ganz oben steht der
Satz aus dem Content-Pack als Titel; in der Hauptkarte folgen TTS-Textfeld (Auto-Save),
Profil- und Stimmenwahl, Generate und Kandidaten-Tabelle (👍/Produktion); „Alle löschen"
entfernt nur ungeschützte Probeaufnahmen (ohne 👍, ohne Produktion); „Keine Produktion"
hebt eine bestätigte Aufnahme wieder auf; darunter die
Profil-Zusammenfassung (Bearbeiten klappt das Formular auf). Bewertungen,
Locks und Profile liegen in Dateien (Sidecar-JSONs, `locks.json`, `profiles.json`) und
überleben damit Server- und Browser-Neustart; Filter und Batch-Auswahl merkt sich der
Browser lokal.

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
`POST /api/clips/{key}/lock`; entspricht der Text wieder dem Satz, wird die
eigene Aussprache (`textOverride`) gelöscht. Gespeichert wird als `textOverride`
im Lock; der Satz selbst bleibt unangetastet. Weil ein Lock zwingend einen Seed
braucht, nagelt Speichern den aktuellen Seed mit fest. Weicht der TTS-Text vom
Satz ab, erscheint in der Clip-Liste eine zweite Zeile.

Die **Stimme** ist an drei Stellen wählbar — pro Clip in der Detailsicht, pro Profil in
der Profilkarte darunter und in „⚙️ TTS-Parameter". Hinter jedem Namen steht die Herkunft
der Stimme:

| Stimme | Herkunft |
| --- | --- |
| `serena`, `vivian` | westlich, weiblich |
| `ryan`, `aiden` | westlich, männlich |
| `sohee` | koreanisch |
| `ono_anna` | japanisch |
| `uncle_fu` | chinesisch |
| `eric` | chinesisch, Sichuan-Dialekt |
| `dylan` | chinesisch, Peking-Dialekt |

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

## Profil-Zuordnung

`FIELD_TO_PROFILE` in `ttskit/extract.py` mappt logische Felder auf Synthese-Profile.
**Lemma ist speziell:** Atome mit `kind: letter` oder `kind: syllable` landen im
Profil `phoneme` (Lautwert), alle anderen Lemmata im Profil `word`. Damit kollidieren
Buchstaben wie `M` und Silben wie `ma` nicht doppelt mit `phonemeTts`/`stretchTts` —
identischer Text im selben Profil wird zu einem Clip zusammengefasst.

Das Profil `article_word` trägt die Lösungswörter **mit Artikel** („das Haus"), die das
Erfolgs-Vorsprechen nennt. Es ist bewusst nicht `word`: dessen `max_new_tokens: 25` (≈ 2,0 s)
schneidet „die Erdbeere" — der längste der 85 Artikel-Texte — ab, und die Instruktion muss
ausdrücklich verlangen, Artikel und Nomen als eine Einheit zu sprechen — abgesetzt klingt es
wie zwei aneinandergehängte Clips.
Ein Artikel-Item entsteht nur für Atome, die `SuccessSpeech` erreichen kann
(`word_build.targetAtomId` ∪ `sound_position.atomId`); die übrigen klassifizierten
Substantive stünden sonst dauerhaft als „fehlt" in `tts status` und würden echte Lücken
verdecken.

Beim Export (`ttskit/export.py`) darf derselbe gesprochene Text trotzdem nur einmal
im Index stehen. Gewinnt bei Kollisionen zuerst die pädagogisch passende Variante
(kurzer Satz-Architekt-Text → `sentence`, Buchstaben-Laut → `phoneme`, …); danach
verified Audio (Fingerprint = letzter Export); sonst `PROFILE_PRIORITY`
(`phoneme` vor `word`, sonst wie gehabt).

Bestehende `word:*`-Locks und Kandidaten-Ordner für Buchstaben/Silben einmalig
umziehen: `tts migrate-locks` (siehe Ablauf oben). Clips mit Produktions-WAV aber
ohne Lock (typisch nach Batch-`render`): `tts wire-locks`, danach `tts export`.

## Umfang

`tts extract` liest aktuell 1174 Items — 1126 Strings aus dem Content-Pack plus 48
Einträge aus `extra-strings.json` (davon 3 ohne eigenes `field`, die damit auf das
Profil `ui` fallen) — und bündelt sie zu 926 Clips (identischer Text im selben Profil
kollabiert in einen Clip):

| Profil | Clips |
| --- | --- |
| word | 244 |
| prompt | 231 |
| sentence | 98 |
| reward | 86 |
| article_word | 85 |
| miss | 81 |
| phoneme | 80 |
| finale | 18 |
| ui | 3 |
| **gesamt** | **926** |

Ein voller `render`-Lauf dauert ungefähr 25–40 Minuten — je nach Profilmix. Ein kurzer
Satz braucht ~2,4 s, die 18 langen `finale`-Sätze im Schnitt ~3,2 s; die 244 einzelnen
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
| `locks.json` | ja | pro Clip festgenagelte Seeds, TTS-Overrides, optional `generateSeed` — **kuratierte Entscheidungen** |
| `extra-strings.json` | ja | hartkodierte Kotlin-Strings |
| `out/` | nein | Manifest, Render-State, Audio, Kandidaten — jederzeit neu erzeugbar |

`profiles.json` und `locks.json` nie automatisiert überschreiben: darin steckt Hörarbeit.

Kandidaten unter `out/candidates/` tragen seit dem UI-Redesign eine Sidecar-Datei
`{seed}.json` mit dem Erzeugungs-Fingerprint — inzwischen zusätzlich mit
Erzeugungszeitpunkt, Stimme, Text und der 👍-Bewertung (`rating: "good"`), damit die
Kandidaten-Tabelle mehrere Würfel-Runden auseinanderhalten kann und Bewertungen einen
Neustart überleben. Der Produktions-Radio-Button übernimmt die Aufnahme immer sofort
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

`textOverride` und `speaker` schickt das Web-Interface selbst (siehe „Aussprache und
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
  also nicht ohne Seed-Pinning ausdrücken. Wie das sauber gelöst wird, ist noch offen.
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
