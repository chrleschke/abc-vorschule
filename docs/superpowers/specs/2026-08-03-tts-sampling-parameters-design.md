# TTS-Sampling-Parameter: Token-Limit, Sub-Talker, Parameter-Registry — Design

Datum: 2026-08-03
Basis: `tools/tts/ttskit/` (FastAPI + Vanilla-JS), aufbauend auf
`docs/superpowers/specs/2026-08-02-tts-ui-redesign-design.md`.

## Problem

Beim Aussprechen einzelner Buchstaben erfindet das Modell gelegentlich ganze Sätze
dazu. Der Instruktionstext des `phoneme`-Profils sagt ausdrücklich „Kein Satz, kein
Zusatz, nur der Laut" — das Modell hält sich nicht immer daran. Solche Aufnahmen
müssen derzeit von Hand herausgehört und verworfen werden.

Dazu zwei Nebenfragen:

1. Muss der Service neu gestartet werden, wenn TTS-Parameter im UI geändert werden
   oder unterschiedliche Profile verwendet werden?
2. Welche Parameter bietet das Modell überhaupt, die das UI heute nicht anfasst?

## Befund 1: Kein Neustart nötig

Die Aufteilung ist bereits sauber und muss nicht angefasst werden:

- Das Modell wird genau einmal geladen (`create_app()` → `Engine.load()`). Der Load
  hängt ausschließlich an `checkpoint` und `device` (`engine.py`) — beides ist im UI
  nicht editierbar.
- Sampling-Werte reisen pro Aufruf mit: `generate_custom_voice(..., **profile.sampling)`.
  Nichts davon ist im Modellzustand verankert.
- Der Server liest `profiles.json` bei jedem Request neu (`load_context()` →
  `Profiles.load()`). Gespeichert heißt ab dem nächsten Generate wirksam. Profilwechsel
  genauso: jede Generierung schlägt `ctx.profiles.profiles[clip.profile]` frisch nach.

Ein Neustart ist nur für Checkpoint oder Device nötig.

**Bekannte Inkonsistenz, hier nicht behoben:** `api_candidates` liest das Profil *vor*
dem Enqueue, `api_render` erst im Worker. Ein Save, während ein Job in der Queue hängt,
wirkt daher je nach Endpunkt unterschiedlich. Kosmetisch; kein Teil dieses Vorhabens.

## Befund 2: Verfügbare Parameter

Aus `_merge_generate_kwargs` (`qwen_tts/inference/qwen3_tts_model.py`). Die effektiven
Defaults kommen **nicht** aus den Hard-Defaults der Library, sondern aus
`generation_config.json` des Checkpoints — `cached_file(..., "generation_config.json")`
in `modeling_qwen3_tts.py` lädt sie, und `pick()` bevorzugt sie.

| Parameter | Checkpoint-Default | Profil heute | im UI? |
|---|---|---|---|
| `temperature` | 0.9 | 0.6 | ja |
| `top_k` | 50 | 30 | ja |
| `top_p` | 1.0 | 0.9 | ja |
| `repetition_penalty` | 1.05 | 1.05 | ja |
| `max_new_tokens` | **8192** | — | nein |
| `subtalker_temperature` | 0.9 | — | nein |
| `subtalker_top_k` | 50 | — | nein |
| `subtalker_top_p` | 1.0 | — | nein |
| `do_sample` | true | — | nein |
| `subtalker_dosample` | true | — | nein |

### Die Token-Sekunden-Kopplung

`max_new_tokens` begrenzt die Codec-Frames, die der Talker emittiert. Der 12-Hz-Tokenizer
hat `decode_upsample_rate = 1920` bei 24 kHz Ausgabe:

    1920 / 24000 = 0,08 s  →  **1 Token = 80 ms Audio**

Der Checkpoint-Default von 8192 entspricht damit **655 Sekunden** — praktisch unbegrenzt.
Eine Untergrenze ist nicht zu befürchten: `min_new_tokens: 2` ist in der Library fest
verdrahtet, und der Talker stoppt regulär früher über `codec_eos_token_id`.

### Warum das Limit trotzdem hilft

Das Limit ist ein **harter Schnitt, kein Hinweis**. Das Modell will den erfundenen Satz
weitersprechen und wird mitten drin gekappt — die Aufnahme endet abrupt, unter Umständen
mitten im Wort. Erfundene Sätze werden also nicht verhindert, sondern *offensichtlich
kaputt*: sie fliegen beim Kuratieren nach einer Sekunde raus, statt dass man acht
Sekunden zuhören muss, um das Problem zu erkennen.

Das Limit gilt für die **Rohgenerierung vor dem Trim**. `trim_silence` schneidet danach
führende und schließende Stille weg, die fertige Datei ist also kürzer als der
eingestellte Wert. Um wie viel, lässt sich aus den vorhandenen Dateien nicht messen —
sie sind alle bereits getrimmt. Das UI beschriftet das Feld deshalb ausdrücklich als
„maximale Rohdauer (vor Trim)" statt eine unbelegte Reserve einzurechnen.

### Nicht aufgenommen: die beiden Booleans

`do_sample` und `subtalker_dosample` bleiben außen vor. `do_sample: false` macht die
Generierung greedy; damit wird der Seed bedeutungslos, „Generate" liefert N identische
Kandidaten und die gesamte Seed-Pool-/Kuratierungs-Mechanik des Werkzeugs kollabiert.
Ein Footgun ohne Gegenwert.

## Datengrundlage für die Limits

Gemessen wurden die Dauern der **validierten** Aufnahmen: gelockte Produktionsdateien
unter `out/audio/` plus Kandidaten mit `rating: "good"` im Sidecar. Die erste Messung
über *alle* Dateien war irreführend — sie enthielt die verworfenen Ausreißer.

| Profil | n | median | p90 | max validiert | max alle |
|---|---|---|---|---|---|
| `phoneme` | 50 | 0,50 | 0,79 | **2,70** | 6,57 |
| `sentence` | 25 | 1,26 | 1,70 | **1,81** | 6,50 |
| `prompt` | 286 | 5,30 | 6,67 | 7,93 | 8,56 |
| `finale` | 25 | 1,93 | 2,43 | 2,87 | 2,87 |
| `word`, `miss`, `reward`, `ui` | 0 | — | — | — | — |

Der Vergleich der letzten beiden Spalten ist der Beleg für die Diagnose: bei `phoneme`
und `sentence` liegen die verworfenen Aufnahmen 3,5–4× über den validierten, bei
`finale` und `prompt` ist kein Unterschied. Genau die Signatur eines Modells, das
gelegentlich einen Satz dazuerfindet — und zwar nur bei kurzen Zieltexten.

Bei `phoneme` ist die Verteilung zweigipfelig: 48 der 50 validierten Werte liegen unter
1,0 s, die restlichen zwei bei 2,70 s (dieselbe Aufnahme doppelt gezählt — Produktions-
datei plus ihr 👍-Kandidat, also ein einzelner Clip).

## A. Parameter-Registry auf dem Server

`BASE_SAMPLING` in `store.py` ist heute ein nackter Wert-Dict. Es wird zu einer
deklarativen Spec — eine Liste von Einträgen mit:

- `key` — der Kwarg-Name für `generate_custom_voice`
- `label`, `group` — Anzeigename und Gruppe (`talker` / `subtalker` / `duration`)
- `default` — der Wert für neue Profile
- `minimum`, `maximum`, `step` — Wertebereich
- `integer` — ob nur Ganzzahlen erlaubt sind
- `nullable` — ob `null` den Key löschen darf (nur `max_new_tokens`)
- `help` — der deutsche Erklärungstext

`BASE_SAMPLING` bleibt daraus abgeleitet, als
`{e.key: e.default for e in SAMPLING_SPEC if e.default is not None}`. `max_new_tokens`
ist der einzige Eintrag ohne `default` — sein Wert ist profilabhängig (Abschnitt B) und
sein Fehlen heißt „unbegrenzt". `Profile.from_dict` und `plan.fingerprint` bleiben
dadurch unverändert.

### Wertebereiche und Erklärungstexte

Die `help`-Texte sind der eigentliche Liefergegenstand des Vorhabens und deshalb hier
wörtlich festgehalten. `min`/`max` sind die Grenzen der Bereichsprüfung, nicht bloß
UI-Hinweise.

| Key | Gruppe | Default | min–max | Schritt | Ganzzahl |
|---|---|---|---|---|---|
| `max_new_tokens` | duration | *(keiner)* | 2–8192 | 1 | ja |
| `temperature` | talker | 0.6 | 0.1–2.0 | 0.05 | nein |
| `top_k` | talker | 30 | 1–100 | 1 | ja |
| `top_p` | talker | 0.9 | 0.05–1.0 | 0.05 | nein |
| `repetition_penalty` | talker | 1.05 | 1.0–2.0 | 0.01 | nein |
| `subtalker_temperature` | subtalker | 0.9 | 0.1–2.0 | 0.05 | nein |
| `subtalker_top_k` | subtalker | 50 | 1–100 | 1 | ja |
| `subtalker_top_p` | subtalker | 1.0 | 0.05–1.0 | 0.05 | nein |

Die Untergrenze 2 bei `max_new_tokens` ist keine Wahl: `min_new_tokens: 2` ist in der
Library fest verdrahtet, ein kleinerer Deckel widerspräche ihr. Die Obergrenze 8192 ist
der Checkpoint-Default — mehr anzubieten wäre ohne Belegung sinnlos.

Die Defaults für die vier bestehenden Talker-Parameter sind bewusst die heutigen Werte
aus `BASE_SAMPLING` (0.6 / 30 / 0.9 / 1.05), nicht die abweichenden Checkpoint-Defaults
(0.9 / 50 / 1.0 / 1.05). Ein neues Profil klingt so wie die bestehenden.

**Erklärungstexte:**

- `max_new_tokens` — „Deckelt, wie lang die Aufnahme werden darf. 1 Token = 80 ms, der
  Wert gilt vor dem Wegschneiden der Stille. Harter Schnitt: erfindet das Modell einen
  Satz dazu, bricht die Aufnahme mitten drin ab — sie ist dann hörbar kaputt statt
  unauffällig falsch. Leer = unbegrenzt (655 s)."
- `temperature` — „Wie stark das Modell vom wahrscheinlichsten Klang abweicht. Niedrig
  (0,3–0,6) = gleichmäßig und vorhersagbar, Seeds klingen ähnlich. Hoch (1,0–1,5) = mehr
  Variation zwischen Seeds, aber auch mehr Ausrutscher. Über 1,5 wird es unbrauchbar."
- `top_k` — „Nur die k wahrscheinlichsten Fortsetzungen kommen überhaupt in Frage.
  Klein (10–30) = enger und sicherer, groß (50–100) = mehr Spielraum. 1 macht die
  Generierung deterministisch und den Seed damit wirkungslos."
- `top_p` — „Nucleus-Sampling: es werden nur so viele Fortsetzungen betrachtet, wie
  zusammen diesen Anteil der Wahrscheinlichkeit ausmachen. 1,0 = keine Begrenzung,
  0,9 = das unwahrscheinlichste Zehntel fällt weg. Wirkt in dieselbe Richtung wie top_k,
  nur relativ statt als feste Anzahl."
- `repetition_penalty` — „Bestraft schon verwendete Klang-Tokens. 1,0 = keine Strafe.
  Über 1,0 verringert Stottern und hängende Silben, zu hoch (über ~1,3) macht die
  Sprechmelodie unruhig, weil das Modell natürliche Wiederholungen vermeidet."
- `subtalker_temperature` — „Wie oben, aber für die akustische Feinstruktur (Timbre,
  Rauschen) statt für den Sprachinhalt. Niedriger = sauberere, gleichmäßigere Stimme;
  höher = lebendiger, aber mit mehr Artefakten. Ändert nicht, *was* gesagt wird."
- `subtalker_top_k` — „Auswahlbreite für die Feinstruktur. Kleinere Werte glätten
  Artefakte, gehen aber auf Kosten der Klangfülle."
- `subtalker_top_p` — „Nucleus-Sampling für die Feinstruktur. 1,0 ist der
  Checkpoint-Default; absenken vor allem dann, wenn Aufnahmen rau oder verrauscht klingen."

Die Spec geht über `/api/state` als `samplingSpec` ans UI. Das ist der Kern des Umbaus:
das Panel rendert danach aus der **Deklaration** statt aus `Object.keys(profile.sampling)`
(`app.js`). Nur so erscheinen neue Parameter überhaupt an Profilen, in deren
`profiles.json`-Eintrag sie noch nicht stehen.

Validierung in `api_update_profile` (`server.py`), aufbauend auf der bestehenden
Whitelist:

- **Bereichsprüfung gegen `minimum`/`maximum`.** Heute gilt nur „ist eine Zahl" —
  `temperature: 50` oder `top_p: 3` landen ungeprüft in der git-verwalteten
  `profiles.json` und produzieren danach still unbrauchbare Audios.
- **Ganzzahl-Prüfung** für `top_k`, `subtalker_top_k`, `max_new_tokens`.
- **`null` löscht** — nur bei `nullable`-Parametern, also `max_new_tokens`. Fehlender
  Key heißt „unbegrenzt, Checkpoint-Default".
- Booleans bleiben abgelehnt wie bisher.

Die Fehlermeldungen nennen weiterhin Parameter und erlaubten Bereich, damit das UI sie
unverändert anzeigen kann.

## B. Werte ab Werk in `profiles.json`

`max_new_tokens` pro Profil, plus die drei `subtalker_*` auf den Checkpoint-Defaults
(0.9 / 50 / 1.0). Letztere sind damit sichtbar und editierbar, ohne den Klang zu ändern.

Zu ändern sind **zwei** Orte: `DEFAULT_PROFILES` in `store.py` (für einen Neuaufsatz) und
die real vorhandene `tools/tts/profiles.json`. Die bestehende Datei erbt nichts von
`BASE_SAMPLING` — `Profile.from_dict` nimmt den Default nur, wenn `sampling` ganz fehlt.
`_profile()` in `store.py` bekommt dafür ein Argument für den Tokenwert.

Maßgeblich ist die Tokenzahl; die Sekundenangabe ist das, was das UI daraus anzeigt.

| Profil | Tokens | angezeigt | Grundlage |
|---|---|---|---|
| `phoneme` | 25 | 2,00 s | Vorgabe; 48/50 validierte unter 1,0 s |
| `word` | 38 | 3,04 s | Vorgabe (3 s) |
| `sentence` | 50 | 4,00 s | 2,2× über längster validierter (1,81 s) |
| `finale` | 63 | 5,04 s | 1,7× über längster validierter (2,87 s) |
| `prompt` | 125 | 10,00 s | Vorgabe; längste validierte 7,93 s |
| `miss` | 75 | 6,00 s | keine Daten — Schätzung |
| `reward` | 63 | 5,04 s | keine Daten — Schätzung |
| `ui` | 75 | 6,00 s | keine Daten — Schätzung |

Bei `phoneme` fällt der eine validierte 2,70-s-Clip unter das Limit. Das ist bewusst in
Kauf genommen: er ist der einzige Ausreißer unter 50, und das Limit gilt ohnehin vor dem
Trim. Sollte er neu gewürfelt werden müssen, ist er einzeln über das Panel freizugeben.

`miss`, `reward` und `ui` haben keine einzige validierte Aufnahme; ihre Werte sind
Schätzungen aus der Rolle des Profils (kurze Ansagen, Feedback-Sätze) und im Panel
jederzeit nachzuziehen.

Bestätigter Content wird davon nicht berührt: `plan.status_of` entscheidet allein anhand
der Dateiexistenz, ob ein Clip gerendert ist. Der Fingerprint treibt nur das informative
„verified"-Häkchen an Kandidaten.

## C. Der Settings-Screen

Das ⚙️-Panel (`renderParams` / `profileFormHtml` in `app.js`) bekommt drei Gruppen statt
eines flachen Grids:

1. **Maximale Dauer** — eigenes Feld, Eingabe in Sekunden (`step="0.1"`), direkt daneben
   der live abgeleitete Tokenwert („= 25 Tokens"), leer = unbegrenzt. Beschriftung
   „maximale Rohdauer (vor Trim)".
2. **Sampling (Haupt-Talker)** — `temperature`, `top_k`, `top_p`, `repetition_penalty`.
3. **Feinstruktur (Sub-Talker)** — `subtalker_temperature`, `subtalker_top_k`,
   `subtalker_top_p`.

Jeder Parameter bekommt eine **Erklärungszeile unter dem Feld** statt nur ein
`title`-Tooltip: was er tut, Wertebereich, Default, und woran die Wirkung zu hören ist.
Die Texte kommen aus dem `help`-Feld der Registry — `SAMPLING_HINTS` in `app.js` entfällt.

Oben im Panel ein kurzer Referenzblock: die 80-ms-Kopplung, dass das Limit ein harter
Schnitt ist und dass es vor dem Trim greift.

### Sekunden ↔ Tokens

Die Eingabe erfolgt in Sekunden, `profiles.json` speichert Tokens. Beim Lesen des
Formulars: `tokens = Math.round(seconds / 0.08)`. Beim Rendern:
`seconds = tokens * 0.08`. Die Ableitung steht sichtbar neben dem Feld, damit der
Rundungssprung nicht überrascht — 2,05 s eingetippt ergibt 26 Tokens und zeigt nach dem
Speichern 2,08 s. Der zweite Durchgang ist stabil.

Ein leeres Feld sendet `max_new_tokens: null` und löscht den Key. `readProfileForm`
wirft heute bei jedem leeren Feld einen Fehler; diese Prüfung wird pro Parameter am
`nullable`-Flag der Registry entschieden statt pauschal.

### Was in der Kurzfassung steht

`profileSummaryCard` in der Detailsicht zeigt heute „Stimme … · Sprache … · temperature …".
Die maximale Dauer kommt dazu, weil sie die auffälligste Eigenschaft eines Profils ist:
„… · max 2,0 s".

## Nicht Teil dieses Vorhabens

- **Kappung sichtbar machen.** Erwogen war, die Roh-Framezahl ins Kandidaten-Sidecar zu
  schreiben und gekappte Aufnahmen mit „Limit erreicht" zu markieren. Verworfen: eine
  abrupt endende Aufnahme ist beim Anhören ohnehin sofort erkennbar.
- `do_sample` / `subtalker_dosample` (siehe oben).
- Die Profil-Lesezeitpunkt-Inkonsistenz zwischen `api_candidates` und `api_render`.
- Checkpoint- und Device-Auswahl im UI — die einzigen Werte, die tatsächlich einen
  Neustart bräuchten.

## Tests

- `store.py`: `BASE_SAMPLING` ist aus der Registry abgeleitet, behält die vier bisherigen
  Schlüssel mit unveränderten Werten (0.6 / 30 / 0.9 / 1.05), ergänzt die drei
  `subtalker_*` und enthält `max_new_tokens` **nicht**; `DEFAULT_PROFILES` trägt pro
  Profil den Tokenwert aus der Tabelle.
- Die real vorhandene `tools/tts/profiles.json` enthält nach der Änderung für jedes der
  acht Profile die drei `subtalker_*` und den Tokenwert aus der Tabelle — geprüft durch
  einen Test, der die ausgelieferte Datei lädt, nicht nur die Defaults.
- `server.py`, `PUT /api/profiles/{name}`: Wert unter `minimum` → 422; über `maximum`
  → 422; Fließkommazahl für einen `integer`-Parameter → 422; `max_new_tokens: null`
  löscht den Key; `null` bei einem nicht-`nullable`-Parameter → 422; unbekannter
  Parameter → 422 wie bisher; Bool → 422 wie bisher.
- `/api/state` liefert `samplingSpec` mit allen acht Einträgen samt `help`-Text.
- Bestandsschutz: ein Profil, dessen `profiles.json`-Eintrag `max_new_tokens` nicht
  enthält, lädt ohne Fehler und rendert mit dem Checkpoint-Default.
- `engine.generate` gibt `max_new_tokens` unverändert an `generate_custom_voice` durch
  (der bestehende `**profile.sampling`-Pfad, mit einem Fake-Modell geprüft).
