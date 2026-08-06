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

## Ablauf

```bash
tts extract                        # Content-JSON → out/manifest.json
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

Erzeugt wird über den **Batch-Lauf**: links in der Liste Clips ankreuzen (einzeln
oder über „Sichtbare / Alle / Keine"), Anzahl Beispiele pro Clip einstellen (Default 2),
dann „▶ Batch-Lauf" in der Kopfzeile — angefasst wird nur, was noch fehlt.
Der Lauf erzeugt Kandidaten wie „🎲 Generate", aber für alle ausgewählten Clips
auf einmal; er schreibt nie direkt in die Produktion. Die Entwürfe stehen danach in
derselben Kandidaten-Tabelle wie jede andere Probeaufnahme — dort per Radio-Button
„Produktion" bestätigen. Ohne Bestätigung bleibt der Clip „fehlt"; eine
Festlegung fällt von selbst weg, sobald keine Aufnahme des Clips mehr übrig ist (keine
eigene „Lock entfernen"-Aktion nötig). Da viel von Hand korrigiert wird, gibt es bewusst
keinen „finalen Lauf" über alles mehr; die Auswahl bestimmt den Umfang.

Die Detailsicht zeigt oben eine Zusammenfassung der Profil-Einstellungen (Stimme,
Sprache, Instruktion, Sampling) — „Bearbeiten" klappt das Formular auf. Dieselben
Einstellungen aller Profile auf einmal gibt es über „⚙️ TTS-Parameter" in der
Kopfzeile; Stimme und Aussprache zusätzlich pro Clip in der Detailsicht. Bewertungen,
Locks und Profile liegen in Dateien (Sidecar-JSONs, `locks.json`, `profiles.json`) und
überleben damit Server- und Browser-Neustart; Filter und Batch-Auswahl merkt sich der
Browser lokal.

**Mit `phoneme` anfangen.** Das ist Absicht: mit 37 Clips ist es das kleinste Profil und
zugleich das riskanteste — gefragt ist der *Lautwert*, nicht der Buchstabenname („mmmmm",
nicht „Em"). Klappt das per Instruktion nicht, greift die Aussprache-Eingabe als
Notausgang. Das weiß man dann nach ein paar Minuten und nicht nach 25–40 Minuten Rendern.

## Aussprache und Stimme

Die Detailsicht zeigt zwei Texte getrennt untereinander, weil sie verschiedene Dinge sind:

- **Satz** — was im Content-Pack steht und in der App zu lesen ist. Nur lesbar.
- **TTS-Version** — genau der Text, der ans Modell geht. Editierbar.

Solange beide gleich sind, steht „identisch mit dem Satz" daran. Sobald man die
TTS-Version ändert, wird sie farbig abgesetzt („eigene Aussprache") und taucht auch in
der Liste als zweite Zeile auf. Gespeichert wird sie als `textOverride` im Lock; der Satz
selbst bleibt unangetastet. Weil ein Lock zwingend einen Seed braucht, nagelt Speichern
den aktuellen Seed mit fest.

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

## Umfang

`tts extract` liest aktuell 893 Items — 891 Strings aus dem Content-Pack plus 2
`ui:`-Einträge aus `extra-strings.json` — und bündelt sie zu 694 Clips (identischer
Text im selben Profil kollabiert in einen Clip):

| Profil | Clips |
| --- | --- |
| word | 260 |
| prompt | 223 |
| miss | 81 |
| reward | 47 |
| phoneme | 37 |
| sentence | 26 |
| finale | 18 |
| ui | 2 |
| **gesamt** | **694** |

Ein voller `render`-Lauf dauert ungefähr 25–40 Minuten — je nach Profilmix. Ein kurzer
Satz braucht ~2,4 s, die 18 langen `finale`-Sätze im Schnitt ~3,2 s; die 260 einzelnen
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
| `locks.json` | ja | pro Clip festgenagelte Seeds — **kuratierte Entscheidungen** |
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
