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
tts status                         # Überblick: fehlt / stale / fertig / Pools / Locks
                                   # plus Fehlschläge des letzten Laufs und leere Texte
tts sample --profile prompt -n 8   # 8 Seeds an 3 Beispielen des Profils ausprobieren
tts web                            # Kuratieren unter http://127.0.0.1:8420
tts render                         # Batch-Lauf über alles, inkrementell, ca. 25–40 Minuten
tts export                         # bestätigte, fertige Clips nach app/.../assets/audio/
```

Typisch: einmal `sample` pro Profil, im Web-Interface Kandidaten anhören und mit 👍
bewerten — 👍 speichert die Bewertung **und** nimmt den Seed automatisch in den
Seed-Pool des Profils auf (👎 löscht die Probeaufnahme und räumt den Pool wieder auf).
Einzelne schlechte Clips mit „🎲 Kandidaten würfeln" (Anzahl einstellbar, 1–16) neu
erzeugen: die Probeaufnahmen stehen als Tabelle, neueste zuerst, mit Erzeugungszeitpunkt,
Stimme und Text — so bleiben mehrere Würfel-Runden auseinanderhaltbar. Der
Radio-Button „Produktion" übernimmt genau eine Aufnahme sofort als Produktions-Audio
und lockt ihren Seed (kein Re-Render, kein erneutes Anhören nötig).

Erzeugt wird über den **Batch-Lauf**: links in der Liste Clips ankreuzen (einzeln
oder über „Sichtbare / Alle / Keine"), Anzahl Beispiele pro Clip einstellen (Default 2),
dann „▶ Batch-Lauf" in der Kopfzeile — angefasst wird nur, was fehlt oder veraltet ist.
Der Lauf erzeugt Kandidaten wie „🎲 Kandidaten würfeln", aber für alle ausgewählten Clips
auf einmal; er schreibt nie direkt in die Produktion. Die Entwürfe stehen danach in
derselben Kandidaten-Tabelle wie jede andere Probeaufnahme — dort per Radio-Button
„Produktion" bestätigen. Ohne Bestätigung bleibt der Clip „fehlt"/„veraltet"; eine
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
pro Profil macht alle seine Clips veraltet und wird deshalb rückgefragt; ein Wechsel pro
Clip trifft nur diesen einen.

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
Neustart überleben. Der Produktions-Radio-Button markiert einen Clip nur dann als
fertig, wenn der Fingerprint noch den aktuellen Einstellungen entspricht — sonst wird
die Aufnahme zwar übernommen und der Seed gelockt, der Clip bleibt aber „veraltet"
(in der UI) bzw. `stale`.

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
Die 18 Clips stehen momentan auf `stale`, weil die Nachbearbeitungs-Version in den
Fingerprint aufgenommen wurde (siehe unten) — der nächste Lauf erzeugt sie neu.

## Nachbearbeitung ändern

Trim-Schwellwert, Trim-Polster und Normalisierungsziel sind Konstanten in
`ttskit/audio.py`. Sie fließen über `POSTPROCESS_VERSION` in den Render-Fingerprint ein:
**wer eine dieser Konstanten ändert, muss `POSTPROCESS_VERSION` hochzählen.** Sonst hält
`render` alle Clips für aktuell, rendert nichts neu, und unter `out/audio/` liegen zwei
Nachbearbeitungs-Generationen nebeneinander, die man nur noch am Klang unterscheidet.

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
- **Instruktion ändern setzt das ganze Profil auf stale.** Speichert man die Instruktion
  eines Profils, werden alle seine Clips `stale`; der nächste `render`-Lauf erzeugt sie
  komplett neu. Das ist so gewollt, aber gut zu wissen, bevor man eine Instruktion mal
  eben nebenbei anpasst.
- **Dateirechte wechseln auf 0600.** Nach einem Save bekommen `profiles.json` und
  `locks.json` die Rechte `0600` (Folge der atomaren Schreibimplementierung über
  `tempfile.mkstemp`), während Git sie mit `0644` auscheckt. Für ein Einzelnutzer-Tool
  harmlos; Git verfolgt den Unterschied ohnehin nicht.

## Bekannte Lücke

Die Sprechtexte von Symbol-Jagd und Wort-Detektiv sind Templates
(`"Finde den Buchstaben - %s - im Wort - %s."`), die erst zur Laufzeit befüllt werden.
Sie sind nicht abgedeckt; `tts status` weist darauf hin. Geplant ist, sämtliche
Kombinationen vollständig vorzurendern statt Clips zur Laufzeit aus Fragmenten
zusammenzusetzen — aneinandergehängte Sprachfragmente klingen abgehackt. „📦 In App
exportieren" (oder `tts export`) schreibt alle bestätigten, fertig gerenderten Clips als
OGG/Opus nach `app/src/main/assets/audio/` zusammen mit einer `index.json`; die App spielt
diese Clips ab und fällt für alles andere weiterhin auf Android-TTS zurück, sodass die
Abdeckung nicht vollständig sein muss.
