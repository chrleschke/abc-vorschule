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
tts render                         # finaler Lauf, inkrementell, ca. 25–40 Minuten
```

Typisch: einmal `sample` pro Profil, im Web-Interface die guten Seeds mit
„＋ In Seed-Pool" sammeln, dann `render`. Einzelne schlechte Clips im Web-Interface
mit „🎲 Kandidaten würfeln" (Anzahl einstellbar, 1–16) neu erzeugen, mit 👍/👎
vorsortieren und dann entweder per „📌 Seed festlegen" fürs nächste Rendern locken —
oder per „🚀 In Produktion" die gehörte Aufnahme direkt als Produktions-Audio
übernehmen (kopiert die WAV, lockt den Seed, kein Re-Render und kein erneutes
Anhören nötig). Sampling-Parameter, Trim/Normalisierung und Instruktionen aller
Profile sind über „⚙️ TTS-Parameter" in der Kopfzeile editierbar.

**Mit `phoneme` anfangen.** Das ist Absicht: mit 37 Clips ist es das kleinste Profil und
zugleich das riskanteste — gefragt ist der *Lautwert*, nicht der Buchstabenname („mmmmm",
nicht „Em"). Klappt das per Instruktion nicht, greift `textOverride` im Lock als
Notausgang. Das weiß man dann nach ein paar Minuten und nicht nach 25–40 Minuten Rendern.

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

Kandidaten unter `out/candidates/` tragen seit dem UI-Redesign eine Sidecar-Datei `{seed}.json` mit dem Erzeugungs-Fingerprint. „🚀 In Produktion" markiert einen Clip nur dann als fertig, wenn dieser Fingerprint noch den aktuellen Einstellungen entspricht — sonst wird die Aufnahme zwar übernommen und der Seed gelockt, der Clip bleibt aber „veraltet" (in der UI) bzw. `stale`.

Beide Dateien werden beim Laden geprüft. Ein Tippfehler — ein Lock auf ein Profil, das es
nicht gibt; ein Lock ohne `seed`; ein fehlendes `label` — bricht mit einer Meldung ab, die
Datei und Schlüssel nennt, statt später als nackter `KeyError` aufzuschlagen. Insbesondere
ersetzt eine leere oder abgeschnittene `profiles.json` **nicht** stillschweigend alle
kuratierten Seed-Pools durch die Defaults, sondern ist ein Fehler. Wer wirklich zurück auf
die Defaults will, löscht die Datei.

`textOverride` und `note` in `locks.json` setzt man per Hand — das Web-Interface schickt
beide (noch) nicht:

```json
{ "version": 1, "locks": {
  "phoneme:9f2c1a7b4e08": { "seed": 991, "textOverride": "mmmmm",
                            "note": "sprach sonst 'Em'", "sourceText": "M" }
}}
```

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

- **Profil-Override erzwingt ein Seed-Lock.** Ändert man im Web-Interface das Profil
  eines Clips, entsteht automatisch ein *Lock*, das den zu diesem Zeitpunkt aufgelösten
  Seed festnagelt — der kann ein ungeprüfter Hash-Fallback sein. Ursache: `store.Lock`
  verlangt zwingend einen `seed`, ein Profil-Override lässt sich also nicht ohne
  Seed-Pinning ausdrücken. Wie das sauber gelöst wird, ist noch offen.
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
zusammenzusetzen — aneinandergehängte Sprachfragmente klingen abgehackt. Das lohnt sich
erst, sobald die App-Integration steht; bis dahin fällt die App für fehlende Clips auf
System-TTS zurück, sodass die Abdeckung nicht vollständig sein muss.
