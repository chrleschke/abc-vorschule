# Qwen-TTS Audio-Pipeline

Erzeugt aus dem Content-Pack der App Sprachaufnahmen mit lokalem Qwen3-TTS.
**Die App wird davon nicht berührt** — hier entsteht nur ein Audio-Paket unter `out/`.

Design: `docs/superpowers/specs/2026-08-02-qwen-tts-audio-pipeline-design.md`

## Voraussetzung

Alles läuft mit dem Interpreter aus dem Qwen-venv:

```bash
alias tts="~/qwen-tts-test/.venv/bin/python $(git rev-parse --show-toplevel)/tools/tts/tts"
```

## Ablauf

```bash
tts extract                        # Content-JSON → out/manifest.json
tts status                         # Überblick: fehlt / stale / fertig / Pools / Locks
tts sample --profile prompt -n 8   # 8 Seeds an 3 Beispielen des Profils ausprobieren
tts web                            # Kuratieren unter http://127.0.0.1:8420
tts render                         # finaler Lauf, inkrementell, ca. 25–40 Minuten
```

Typisch: einmal `sample` pro Profil, im Web-Interface die guten Seeds mit „✓ Pool"
sammeln, dann `render`. Einzelne schlechte Clips im Web-Interface mit „🎲 4 Kandidaten"
neu würfeln und den besten per „📌 Lock" festnageln.

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

Aktuell liegen unter `out/audio/` bereits 18 gerenderte `finale`-Clips sowie ein
`candidates/`-Verzeichnis aus der Entwicklung (probeweise gewürfelte Kandidaten-Seeds).
Beides ist jederzeit löschbar; `tts render` bzw. `tts sample` erzeugen es bei Bedarf neu.

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
