# TTS-Web-UI Redesign — Design

Datum: 2026-08-02
Basis: `tools/tts/ttskit/` (FastAPI-Server + Vanilla-JS-Frontend), siehe
`docs/superpowers/specs/2026-08-02-qwen-tts-audio-pipeline-design.md`.

## Problem

Das Web-Interface unter `tools/tts/ttskit/static/` funktioniert, aber die UX hat Lücken:

1. **Keine sichtbaren Statusmeldungen.** Läuft ein Job (Kandidaten, Render), gibt es nur
   eine kleine Textzeile rechts oben. Kein Progress-Balken, kein Loader, keine Restzeit,
   kein Hinweis, dass ein Klick auf „4 Kandidaten" überhaupt etwas ausgelöst hat.
2. **„Pool" und „Lock" sind unverständlich.** Die Buttons `✓ Pool` und `📌 Lock` erklären
   weder vorher, was passieren wird, noch zeigt die UI hinterher, was passiert ist
   (Kandidaten-Karten zeigen nicht, ob ihr Seed im Pool oder gelockt ist).
3. **Kandidatenanzahl ist hart auf 4 verdrahtet.** Der Server akzeptiert `n` bis 16,
   das Frontend sendet immer 4.
4. **„stale" ist unübersetzt.**
5. **Kandidaten-Auswertung ist unklar.** Man kann Kandidaten nur anhören; es fehlt
   „gut/schlecht"-Bewertung und vor allem ein direkter Weg „genau diese Aufnahme geht
   in Produktion" — ohne dass sie neu gerendert und erneut angehört (QA) werden muss.
6. **Kein globales Parameter-Interface.** Sampling-Parameter (temperature, top_k, top_p,
   repetition_penalty), trim und normalize sind nur per API/Datei änderbar, obwohl
   `PUT /api/profiles/{name}` sie längst unterstützt.

## Zielbild (Nutzersicht)

- Jede laufende Aktion ist sichtbar: Spinner + Fortschrittsbalken + „x/y" + geschätzte
  Restzeit in der Kopfzeile; im Detailbereich zeigt die Kandidaten-Karte des betroffenen
  Clips einen Lade-Zustand. Warteschlange („1 Job wartet") ist sichtbar.
- Buttons sagen, was sie tun: „＋ In Seed-Pool" (Profil-weit), „📌 Diesen Seed festlegen"
  (Clip-weit), „🚀 In Produktion übernehmen" (Aufnahme direkt übernehmen). Jede Karte
  zeigt den Ist-Zustand ihres Seeds: `im Pool`, `festgelegt`, `aktiver Seed`.
  Ein kurzer Hilfetext in der Kandidaten-Karte erklärt die drei Aktionen.
- Anzahl Kandidaten ist ein Zahlenfeld (1–16, merkt sich den letzten Wert lokal).
- `stale` heißt überall **„veraltet"** (Filter, Chips, Meldungen).
- Kandidaten lassen sich bewerten: 👍 markiert gut (visuell hervorgehoben, lokal
  gemerkt), 👎 sortiert aus (löscht die Kandidaten-Datei serverseitig, Karte
  verschwindet). „🚀 In Produktion übernehmen" befördert die gehörte WAV-Datei direkt
  zur Produktions-Audio und lockt den Seed — kein Re-Render, kein erneutes QA.
- Kopfzeilen-Button „⚙️ TTS-Parameter" öffnet ein Panel mit allen Profilen: Instruktion,
  Sampling-Parameter, trim/normalize, Seed-Pool — editier- und speicherbar, mit Warnung
  „Speichern macht Clips dieses Profils veraltet". Zusätzlich „Sampling auf alle Profile
  übertragen".

## Architektur-Entscheidungen

### A. „In Produktion übernehmen" = Promote-Endpoint mit Fingerprint-Sidecar

Der entscheidende neue Flow. Damit „keine erneute QA nötig" auch stimmt, muss die
Produktions-Datei **bit-identisch** die Datei sein, die man angehört hat — kopieren,
nicht neu rendern.

- `sample_candidates()` schreibt neben jeder `candidates/{key}/{seed}.wav` eine
  Sidecar-Datei `{seed}.json` mit dem Render-Fingerprint (`plan.fingerprint` über den
  Clip mit diesem Seed), unter dem der Kandidat erzeugt wurde.
- Neuer Endpoint `POST /api/clips/{key}/promote` mit Body `{"seed": <int>}`:
  1. 404, wenn Clip oder Kandidaten-WAV fehlt.
  2. Kopiert `candidates/{key}/{seed}.wav` → `out/audio/{key}.wav`.
  3. Setzt/aktualisiert das Lock auf diesen Seed. Ein bestehendes Lock behält
     `profile`, `textOverride`, `note` (anders als `api_lock`, das überschreibt).
  4. Vergleicht den Sidecar-Fingerprint mit dem aktuellen Fingerprint (nach Lock).
     Bei Übereinstimmung: `render-state.entries[key] = fingerprint`, Failure-Eintrag
     löschen → Status `fertig`. Bei fehlendem/abweichendem Sidecar (Profil wurde seit
     dem Würfeln geändert, oder Alt-Kandidat ohne Sidecar): Audio und Lock werden
     trotzdem übernommen, der State-Eintrag bleibt unangetastet → Status `veraltet`,
     Response meldet `"verified": false` und die UI erklärt warum.
- `/api/state` liefert Kandidaten als Objekte `{"seed": n, "fresh": true|false|null}`
  statt nackter Seed-Liste (`fresh` = Sidecar-Fingerprint gleich aktuellem Fingerprint,
  `null` = kein Sidecar). Die UI graut nicht-frische Kandidaten aus:
  „mit älteren Einstellungen erzeugt".

Verworfen: Promote ohne Verifikation (setzt bei zwischenzeitlich geänderten
Profil-Parametern einen falschen „fertig"-Status); Promote per Re-Render (widerspricht
„kein erneutes QA", Restrisiko nichtdeterministischer Backends).

### B. „Aussortieren" = Kandidat serverseitig löschen

Neuer Endpoint `DELETE /api/clips/{key}/candidates/{seed}` löscht WAV + Sidecar
(404, wenn nicht vorhanden). 👎 in der UI ruft ihn auf. 👍 („gut") ist reine
Hör-Notiz während des Vergleichens und landet in `localStorage`
(`ttsGood:{clipKey}` = Array von Seeds) — Einzelnutzer-Tool, kein Server-State nötig.

### C. Fortschritt clientseitig aus SSE ableiten

Die SSE-Events (`render`/`candidate` mit `index`/`total`, `job-start`, `job-summary`,
`job-done`, `job-error`) reichen aus. Das Frontend berechnet die Restzeit selbst
(gleitender Durchschnitt der Event-Abstände × verbleibende Items). Kein neues
Server-Feature; `/api/state.jobs.queued` wird für die Warteschlangen-Anzeige genutzt.
Der Fortschrittsbereich der Kopfzeile wird eine feste Komponente:
Spinner + `<div class="bar">` + Textzeile; Abbrechen-Button nur sichtbar, wenn ein
Job läuft oder wartet.

### D. Parameter-Panel als Overlay, Speichern über bestehenden Endpoint

`PUT /api/profiles/{name}` kann bereits `instruct`, `sampling`, `trim`, `normalize`.
Das Panel (Overlay über der Detailspalte, Toggle in der Kopfzeile) rendert pro Profil
eine Karte mit diesen Feldern plus Seed-Pool-Anzeige (mit Entfernen-Links wie bisher).
„Auf alle Profile übertragen" schickt dieselben Sampling-Werte nacheinander an alle
Profile (Client-Schleife; Atomicität ist für ein Einzelnutzer-Tool irrelevant).
`MAX_CANDIDATES` wandert in die `/api/state`-Antwort (`limits.maxCandidates`), damit
das Zahlenfeld die Server-Grenze kennt statt sie zu duplizieren.

## Komponenten und Änderungen

| Datei | Änderung |
| --- | --- |
| `ttskit/render.py` | `sample_candidates` schreibt Fingerprint-Sidecar; neue Helper `candidate_infos(paths, clip, profile)` |
| `ttskit/server.py` | `POST /api/clips/{key}/promote`, `DELETE /api/clips/{key}/candidates/{seed}`, `candidates` als Objektliste, `limits` in `/api/state` |
| `static/index.html` | Kopfzeile mit Fortschrittskomponente, Kandidaten-Zahlenfeld, Parameter-Button, deutsches `stale`→`veraltet` |
| `static/app.js` | Fortschritt/ETA aus SSE, Kandidaten-Karten (Zustands-Chips, 👍/👎, Promote), Parameter-Panel, Hilfetexte, localStorage für Anzahl + 👍 |
| `static/style.css` | Fortschrittsbalken, Spinner, Karten-Zustände (gut/ausgegraut), Overlay-Panel |
| `tests/test_render.py` | Sidecar wird geschrieben, `candidate_infos` |
| `tests/test_server.py` | Promote (frisch → fertig; ohne Sidecar → veraltet + Lock trotzdem; unbekannter Seed → 404; bestehendes Lock behält Felder), Delete-Kandidat, State-Form |
| `tools/tts/README.md` | Neue Buttons/Flows, Promote-Semantik, Parameter-Panel |

## Fehlerbehandlung

- Promote/Delete validieren Clip-Key (bestehendes `clip_by_key`) und Datei-Existenz →
  saubere 404 mit deutscher Meldung statt Traceback.
- Promote schreibt Audio-Kopie atomar (temp + `os.replace`, wie `_write_json`) —
  `render_clips` könnte theoretisch parallel laufen, und die App-Seite darf nie eine
  halbe WAV sehen.
- UI: Fehlgeschlagene Requests laufen weiter über `guard()`/`showError`, aber Fehler
  erscheinen als abweisbare Banner-Zeile statt den Fortschrittsbereich zu überschreiben.

## Tests

Bestehende Muster aus `tests/test_server.py` (FakeEngine, `wait_for_idle`) weiterverwenden.
Kein Modell nötig. Frontend bleibt wie bisher ohne JS-Test-Harness (Einzelnutzer-Tool);
Verifikation über Server-Tests + manuellen Smoke im Browser.

## Nicht-Ziele

- Kein `textOverride`/`note`-Editor im Lock (bleibt Handarbeit in `locks.json`).
- Kein Server-seitiger Bewertungs-Store, keine Mehrbenutzer-Fähigkeit.
- Keine Änderung an Engine, CLI, Extract oder Render-Semantik (Fingerprint-Format
  bleibt identisch; Sidecars sind additiv).
