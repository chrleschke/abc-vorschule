# TTS-Web-UI Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Das Qwen-TTS-Web-Interface bekommt sichtbaren Job-Fortschritt, verständliche Pool/Lock/Produktions-Aktionen mit Zustandsanzeige, eine einstellbare Kandidatenanzahl, „veraltet" statt „stale", Kandidaten-Bewertung (👍/👎) mit Direkt-Übernahme in Produktion sowie ein globales TTS-Parameter-Panel.

**Architecture:** Backend (FastAPI, `tools/tts/ttskit/server.py`) bekommt zwei neue Endpoints (Promote, Kandidat löschen) und liefert Kandidaten mit Frische-Information; `render.py` schreibt beim Sampling einen Fingerprint-Sidecar pro Kandidat, damit Promote verifizieren kann, dass die gehörte Aufnahme den aktuellen Einstellungen entspricht. Frontend (`static/`) wird überarbeitet: Fortschrittskomponente aus SSE-Events, Banner für Meldungen, neue Kandidaten-Karten, Parameter-Overlay.

**Tech Stack:** Python 3 / FastAPI / pytest (Interpreter: `~/qwen-tts-test/.venv/bin/python`), Vanilla JS/HTML/CSS ohne Build-Schritt.

Spec: `docs/superpowers/specs/2026-08-02-tts-ui-redesign-design.md`

## Global Constraints

- Tests laufen mit `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q` (ohne Modell; `TTS_SMOKE` NICHT setzen).
- UI-Texte sind deutsch; `stale` heißt in der UI überall **„veraltet"**, `missing` „fehlt", `rendered` „fertig", ein Lock heißt „festgelegt". Die API-Statuswerte (`missing`/`stale`/`rendered`) bleiben unverändert englisch.
- `profiles.json` / `locks.json` niemals per Skript überschreiben — nur über die bestehenden Store-Klassen.
- Kommentar-Stil des Bestands beibehalten: Kommentare nur für Constraints, die der Code nicht zeigt.
- Commits auf dem aktuellen Branch (`claude/qwen-tts-ui-redesign-c4748e`), Commit-Messages deutsch im bestehenden Stil (`feat(tts): …`), mit `Co-Authored-By: Claude Fable 5 <noreply@anthropic.com>`.

---

### Task 1: Fingerprint-Sidecars für Kandidaten (`render.py`)

**Files:**
- Modify: `tools/tts/ttskit/render.py`
- Test: `tools/tts/tests/test_render.py`

**Interfaces:**
- Consumes: `plan.fingerprint(clip, profile)`, `dataclasses.replace` (Clip ist ein Dataclass, `models.py`), `Paths.candidates`.
- Produces (Task 2 verlässt sich darauf):
  - `sample_candidates(...)` schreibt zusätzlich `out/candidates/{key}/{seed}.json` mit `{"fingerprint": "<16-hex>"}`.
  - `candidate_fingerprint(paths: Paths, clip_key: str, seed: int) -> str | None` — Sidecar lesen, `None` bei fehlender/kaputter Datei.
  - `candidate_infos(paths: Paths, clip: Clip, profile: Profile) -> list[dict]` — pro Kandidat `{"seed": int, "fresh": bool | None}`; `fresh` = Sidecar-Fingerprint == aktueller Fingerprint für diesen Seed, `None` ohne Sidecar.

- [ ] **Step 1: Failing Tests schreiben** — ans Ende von `tools/tts/tests/test_render.py` (bestehende Fixtures/Muster der Datei zuerst lesen und wiederverwenden; dort gibt es bereits einen FakeEngine und Paths auf `tmp_path`):

```python
def test_sample_candidates_writes_fingerprint_sidecar(tmp_path):
    # Fixtures analog zu den bestehenden sample_candidates-Tests der Datei
    # aufbauen (FakeEngine, Paths(root=tmp_path), ein Clip, ein Profil).
    from dataclasses import replace
    import json as jsonlib
    from ttskit.plan import fingerprint
    from ttskit.render import sample_candidates

    # clip, profile, engine, paths wie im bestehenden Test der Datei
    written = sample_candidates(clip, profile, engine, paths, [11, 22])
    assert written == [11, 22]
    for seed in (11, 22):
        meta = jsonlib.loads(
            (paths.candidates / clip.key / f"{seed}.json").read_text(encoding="utf-8"))
        assert meta["fingerprint"] == fingerprint(replace(clip, seed=seed), profile)


def test_candidate_infos_reports_freshness(tmp_path):
    from ttskit.render import candidate_infos, sample_candidates

    sample_candidates(clip, profile, engine, paths, [11])
    # Alt-Kandidat ohne Sidecar (Bestand aus der Zeit vor den Sidecars):
    (paths.candidates / clip.key / "99.wav").write_bytes(b"RIFF")

    infos = candidate_infos(paths, clip, profile)
    assert {i["seed"]: i["fresh"] for i in infos} == {11: True, 99: None}

    profile.instruct = "Ganz anders."
    infos = candidate_infos(paths, clip, profile)
    assert {i["seed"]: i["fresh"] for i in infos} == {11: False, 99: None}
```

- [ ] **Step 2: Tests laufen lassen, Fehlschlag verifizieren**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_render.py -q`
Expected: die zwei neuen Tests FAILEN (Sidecar fehlt / `candidate_infos` existiert nicht).

- [ ] **Step 3: Implementieren** — in `tools/tts/ttskit/render.py`:

Imports ergänzen: `import json`, `from dataclasses import dataclass, field, replace`, `from .plan import fingerprint, status_of` (fingerprint kommt dazu).

In `sample_candidates` direkt nach `write_wav(...)` / `written.append(seed)`:

```python
            meta_path = paths.candidates / clip.key / f"{seed}.json"
            meta_path.write_text(json.dumps(
                {"fingerprint": fingerprint(replace(clip, seed=seed), profile)},
            ) + "\n", encoding="utf-8")
```

Neue Funktionen neben `candidate_seeds`:

```python
def candidate_fingerprint(paths: Paths, clip_key: str, seed: int) -> str | None:
    """Fingerprint, unter dem ein Kandidat erzeugt wurde — None, wenn unbekannt.

    Kandidaten aus der Zeit vor den Sidecars haben keine Metadatei; eine
    kaputte Datei behandeln wir genauso, statt die ganze State-Antwort zu
    reißen: 'unbekannt' ist hier eine legitime Antwort.
    """
    path = Path(paths.candidates) / clip_key / f"{seed}.json"
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return None
    value = raw.get("fingerprint") if isinstance(raw, dict) else None
    return value if isinstance(value, str) else None


def candidate_infos(paths: Paths, clip: Clip, profile: Profile) -> list[dict]:
    """Kandidaten-Seeds plus Frische: entspricht der Sidecar-Fingerprint noch
    den aktuellen Einstellungen? None = Alt-Kandidat ohne Sidecar."""
    infos = []
    for seed in candidate_seeds(paths, clip.key):
        recorded = candidate_fingerprint(paths, clip.key, seed)
        current = fingerprint(replace(clip, seed=seed), profile)
        infos.append({"seed": seed,
                      "fresh": None if recorded is None else recorded == current})
    return infos
```

- [ ] **Step 4: Tests laufen lassen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_render.py -q`
Expected: PASS (alle, auch die bestehenden).

- [ ] **Step 5: Commit**

```bash
git add tools/tts/ttskit/render.py tools/tts/tests/test_render.py
git commit -m "feat(tts): Kandidaten bekommen Fingerprint-Sidecars und Frische-Info"
```

---

### Task 2: Promote- und Lösch-Endpoints, State-Erweiterung (`server.py`)

**Files:**
- Modify: `tools/tts/ttskit/server.py`
- Test: `tools/tts/tests/test_server.py`

**Interfaces:**
- Consumes (aus Task 1): `candidate_infos(paths, clip, profile)`, `candidate_fingerprint(paths, clip_key, seed)`.
- Produces (Task 3 verlässt sich darauf):
  - `/api/state`: `clips[*].candidates` ist jetzt `[{"seed": int, "fresh": bool|null}, …]`; neu `"limits": {"maxCandidates": 16}` auf oberster Ebene.
  - `POST /api/clips/{key}/promote` Body `{"seed": int}` → `{"ok": "promoted", "verified": bool}`; 404 bei unbekanntem Clip/Kandidat. Kopiert die Kandidaten-WAV atomar nach `out/audio/{key}.wav`, setzt das Lock auf den Seed (bestehende Lock-Felder `profile`/`textOverride`/`note` bleiben erhalten) und markiert den Clip nur bei verifiziertem Fingerprint als gerendert.
  - `DELETE /api/clips/{key}/candidates/{seed}` → `{"ok": "deleted"}`; 404 wenn es die WAV nicht gibt; löscht WAV + Sidecar.

- [ ] **Step 1: Failing Tests schreiben** — in `tools/tts/tests/test_server.py` (Muster `client`-Fixture, `wait_for_idle`):

```python
def test_state_reports_candidate_freshness_and_limits(client):
    body = client.get("/api/state").json()
    assert body["limits"]["maxCandidates"] == 16
    key = body["clips"][0]["key"]
    client.post(f"/api/clips/{key}/candidates", json={"n": 2})
    wait_for_idle(client)
    clip = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert len(clip["candidates"]) == 2
    assert all(c["fresh"] is True for c in clip["candidates"])

    client.put(f"/api/profiles/{clip['profile']}", json={"instruct": "Neu."})
    clip = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert all(c["fresh"] is False for c in clip["candidates"])


def test_promote_copies_audio_locks_seed_and_marks_rendered(client):
    key = client.get("/api/state").json()["clips"][0]["key"]
    client.post(f"/api/clips/{key}/candidates", json={"n": 1})
    wait_for_idle(client)
    clip = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    seed = clip["candidates"][0]["seed"]

    response = client.post(f"/api/clips/{key}/promote", json={"seed": seed})
    assert response.status_code == 200
    assert response.json() == {"ok": "promoted", "verified": True}

    clip = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert clip["locked"] is True
    assert clip["seed"] == seed
    assert clip["status"] == "rendered"
    audio = client.paths.audio / f"{key}.wav"
    candidate = client.paths.candidates / key / f"{seed}.wav"
    assert audio.read_bytes() == candidate.read_bytes()


def test_promote_without_sidecar_still_locks_but_stays_stale(client):
    key = client.get("/api/state").json()["clips"][0]["key"]
    (client.paths.candidates / key).mkdir(parents=True)
    # Alt-Kandidat aus der Zeit vor den Sidecars: nur die WAV liegt da.
    (client.paths.candidates / key / "777.wav").write_bytes(b"RIFFfake")

    response = client.post(f"/api/clips/{key}/promote", json={"seed": 777})
    assert response.status_code == 200
    assert response.json() == {"ok": "promoted", "verified": False}

    clip = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert clip["locked"] is True and clip["seed"] == 777
    assert clip["status"] == "stale"


def test_promote_preserves_existing_lock_fields(client):
    import json as jsonlib
    key = client.get("/api/state").json()["clips"][0]["key"]
    client.post(f"/api/clips/{key}/lock",
                json={"seed": 1, "textOverride": "mmmmm", "note": "Handarbeit"})
    client.post(f"/api/clips/{key}/candidates", json={"n": 1})
    wait_for_idle(client)
    clip = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    seed = clip["candidates"][0]["seed"]

    assert client.post(f"/api/clips/{key}/promote", json={"seed": seed}).status_code == 200
    lock = jsonlib.loads(client.paths.locks.read_text(encoding="utf-8"))["locks"][key]
    assert lock["seed"] == seed
    assert lock["textOverride"] == "mmmmm"
    assert lock["note"] == "Handarbeit"


def test_promote_unknown_candidate_is_404(client):
    key = client.get("/api/state").json()["clips"][0]["key"]
    assert client.post(f"/api/clips/{key}/promote", json={"seed": 424242}).status_code == 404


def test_deleting_a_candidate_removes_wav_and_sidecar(client):
    key = client.get("/api/state").json()["clips"][0]["key"]
    client.post(f"/api/clips/{key}/candidates", json={"n": 1})
    wait_for_idle(client)
    clip = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    seed = clip["candidates"][0]["seed"]

    assert client.delete(f"/api/clips/{key}/candidates/{seed}").status_code == 200
    assert not (client.paths.candidates / key / f"{seed}.wav").exists()
    assert not (client.paths.candidates / key / f"{seed}.json").exists()
    clip = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert clip["candidates"] == []
    assert client.delete(f"/api/clips/{key}/candidates/{seed}").status_code == 404
```

Außerdem den bestehenden Test `test_candidates_are_generated_and_listed` an die neue Objektform anpassen (`clip["candidates"]` enthält Dicts; Seeds via `[c["seed"] for c in …]`).

- [ ] **Step 2: Tests laufen lassen, Fehlschlag verifizieren**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/test_server.py -q`
Expected: neue Tests FAILEN (404-Routen fehlen, `limits` fehlt, Kandidatenform alt).

- [ ] **Step 3: Implementieren** — in `tools/tts/ttskit/server.py`:

Imports anpassen:

```python
import os
import tempfile
from dataclasses import dataclass, field, replace

from .plan import fingerprint, orphan_locks, status_of
from .render import (
    candidate_fingerprint, candidate_infos, candidate_seeds, random_seeds,
    render_clips, sample_candidates,
)
from .store import BASE_SAMPLING, Lock, Locks, Profiles, RenderState
```

Modul-Helper (unter `MAX_CANDIDATES`):

```python
def _copy_atomic(src: Path, dst: Path) -> None:
    """Wie store._write_json: die App-Seite darf nie eine halbe WAV sehen,
    falls parallel ein Render-Lauf oder ein zweiter Promote schreibt."""
    dst.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_name = tempfile.mkstemp(dir=dst.parent, prefix=f".{dst.name}.", suffix=".tmp")
    try:
        with os.fdopen(fd, "wb") as f:
            f.write(src.read_bytes())
        os.replace(tmp_name, dst)
    except BaseException:
        try:
            os.unlink(tmp_name)
        except OSError:
            pass
        raise
```

In `api_state`: `"candidates": candidate_infos(paths, clip, profile)` statt `candidate_seeds(...)`, und in der Antwort oben `"limits": {"maxCandidates": MAX_CANDIDATES},` ergänzen.

In `api_candidates`: `existing = {c["seed"] for c in candidate_infos(paths, clip, profile)} | set(profile.seed_pool)` — oder `candidate_seeds` weiterverwenden (bleibt exportiert); Letzteres ist einfacher, so lassen.

Neue Endpoints (nach `api_unlock`):

```python
    @app.post("/api/clips/{key}/promote")
    def api_promote(key: str, body: dict = Body(...)) -> dict[str, Any]:
        ctx, clip = clip_by_key(key)
        seed = int(body["seed"])
        source = paths.candidates / key / f"{seed}.wav"
        if not source.exists():
            raise HTTPException(status_code=404,
                                detail=f"kein Kandidat mit Seed {seed} für {key!r}")

        # Lock zuerst: anders als api_lock bleiben vorhandene kuratierte
        # Felder (profile, textOverride, note) erhalten — Promote entscheidet
        # nur über den Seed, nicht über den Rest der Hörarbeit.
        locks = Locks.load(paths.locks)
        existing = locks.get(key)
        locks.set(key, Lock(
            seed=seed,
            profile=existing.profile if existing else None,
            text_override=existing.text_override if existing else None,
            note=existing.note if existing else None,
            source_text=clip.source_text,
        ))
        locks.save(paths.locks)

        _copy_atomic(source, paths.audio / f"{key}.wav")

        # Nur wenn der Kandidat nachweislich mit den aktuellen Einstellungen
        # erzeugt wurde, gilt der Clip als gerendert. Sonst bleibt er
        # "stale" und der nächste Lauf rendert ihn mit dem gelockten Seed neu.
        profile = ctx.profiles.profiles[clip.profile]
        target = fingerprint(replace(clip, seed=seed), profile)
        verified = candidate_fingerprint(paths, key, seed) == target
        if verified:
            render_state = RenderState.load(paths.render_state)
            render_state.entries[key] = target
            render_state.failures.pop(key, None)
            render_state.save(paths.render_state)
        return {"ok": "promoted", "verified": verified}

    @app.delete("/api/clips/{key}/candidates/{seed}")
    def api_delete_candidate(key: str, seed: int) -> dict[str, str]:
        clip_by_key(key)
        wav = paths.candidates / key / f"{seed}.wav"
        if not wav.exists():
            raise HTTPException(status_code=404,
                                detail=f"kein Kandidat mit Seed {seed} für {key!r}")
        wav.unlink()
        (paths.candidates / key / f"{seed}.json").unlink(missing_ok=True)
        return {"ok": "deleted"}
```

Achtung: `replace` braucht das `Clip`-Dataclass; `clip` kommt aus `clip_by_key` und reflektiert bereits ein evtl. vorhandenes Lock (Profil/Text). Da Promote diese Felder beibehält, ist `replace(clip, seed=seed)` genau der Zustand nach dem neuen Lock.

- [ ] **Step 4: Tests laufen lassen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q`
Expected: PASS (gesamte Suite).

- [ ] **Step 5: Commit**

```bash
git add tools/tts/ttskit/server.py tools/tts/tests/test_server.py
git commit -m "feat(tts): Promote in Produktion und Kandidaten-Löschen als API"
```

---

### Task 3: Frontend-Redesign (`static/index.html`, `static/app.js`, `static/style.css`)

**Files:**
- Modify: `tools/tts/ttskit/static/index.html` (komplett ersetzen)
- Modify: `tools/tts/ttskit/static/app.js` (komplett ersetzen)
- Modify: `tools/tts/ttskit/static/style.css` (komplett ersetzen)

**Interfaces:**
- Consumes (aus Task 2): `/api/state` mit `limits.maxCandidates` und `candidates: [{seed, fresh}]`; `POST /api/clips/{key}/promote`; `DELETE /api/clips/{key}/candidates/{seed}`; bestehende Endpoints unverändert.
- Produces: nichts für weitere Tasks; Endzustand der UI.

Es gibt keinen JS-Test-Harness (bewusst, Einzelnutzer-Tool). Verifikation: Syntax-Check mit `node --check`, Server-Suite grün, danach manueller Smoke (Task 4).

- [ ] **Step 1: `index.html` ersetzen** — kompletter neuer Inhalt:

```html
<!doctype html>
<html lang="de">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>Qwen-TTS Pipeline</title>
    <link rel="stylesheet" href="/style.css" />
  </head>
  <body>
    <header>
      <h1>Qwen-TTS</h1>
      <input id="search" type="search" placeholder="Text suchen …" />
      <select id="filter-profile"><option value="">Alle Profile</option></select>
      <select id="filter-status">
        <option value="">Alle Status</option>
        <option value="missing">fehlt</option>
        <option value="stale">veraltet</option>
        <option value="rendered">fertig</option>
        <option value="locked">festgelegt</option>
      </select>
      <button id="btn-params"
              title="Sampling, Trim/Normalisierung und Instruktionen aller Profile bearbeiten">
        ⚙️ TTS-Parameter</button>
      <button id="btn-render" class="primary"
              title="Rendert alle fehlenden und veralteten Clips (inkrementell)">
        Finalen Lauf starten</button>
      <div id="job">
        <span id="job-spinner" class="spinner hidden"></span>
        <div id="job-bar-track" class="hidden"><div id="job-bar"></div></div>
        <span id="job-text"></span>
        <button id="btn-cancel" class="hidden">Abbrechen</button>
      </div>
    </header>
    <div id="banner" class="hidden">
      <span id="banner-text"></span>
      <button id="banner-close" title="Meldung schließen">×</button>
    </div>
    <main>
      <div id="list"></div>
      <div id="detail">
        <p class="muted">Links einen Clip wählen.
          Tasten: j/k blättern, Leertaste spielt, 1–9 spielen Kandidaten.</p>
      </div>
    </main>
    <div id="params-panel" class="hidden"></div>
    <script src="/app.js"></script>
  </body>
</html>
```

- [ ] **Step 2: `app.js` ersetzen** — kompletter neuer Inhalt:

```javascript
"use strict";

const state = {
  clips: [], profiles: {}, engine: {}, orphans: [], selected: null,
  jobs: { running: null, queued: 0 }, limits: { maxCandidates: 16 },
  paramsOpen: false,
};
const el = (id) => document.getElementById(id);

const STATUS_LABELS = { missing: "fehlt", stale: "veraltet", rendered: "fertig" };

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

// ---------------------------------------------------------------- Meldungen

// Fehler und Bestätigungen haben ein eigenes Banner unter der Kopfzeile,
// damit sie den Job-Fortschritt rechts oben nicht mehr überschreiben.
function showBanner(text, kind) {
  el("banner").className = kind || "info";
  el("banner-text").textContent = text;
}

function showError(error) {
  showBanner(`Fehler: ${String((error && error.message) || error)}`, "warn");
}

// Every click handler below is a bare `async` function and `api()` throws on
// a non-OK response. Without this wrapper a failed request produced an
// unhandled rejection in the console and nothing at all in the UI.
const guard = (fn) => (...args) => Promise.resolve()
  .then(() => fn(...args))
  .catch(showError);

async function api(path, options) {
  const response = await fetch(path, options);
  if (!response.ok) {
    const detail = await response.text();
    throw new Error(`${response.status}: ${detail}`);
  }
  return response.status === 204 ? null : response.json();
}

const post = (path, body) =>
  api(path, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body || {}),
  });

const put = (path, body) =>
  api(path, {
    method: "PUT",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });

// ------------------------------------------------------ lokale Hör-Notizen

// 👍 ist eine reine Hör-Notiz beim Vergleichen — bewusst nur localStorage,
// kein Server-State (Einzelnutzer-Tool).
function goodSeeds(clipKey) {
  try {
    return new Set(JSON.parse(localStorage.getItem(`ttsGood:${clipKey}`) || "[]"));
  } catch {
    return new Set();
  }
}

function toggleGood(clipKey, seed) {
  const seeds = goodSeeds(clipKey);
  if (seeds.has(seed)) seeds.delete(seed); else seeds.add(seed);
  localStorage.setItem(`ttsGood:${clipKey}`, JSON.stringify([...seeds]));
}

function candidateCount() {
  const max = state.limits.maxCandidates || 16;
  const stored = Number(localStorage.getItem("ttsCandCount"));
  const fallback = Number.isFinite(stored) && stored >= 1 ? Math.round(stored) : 4;
  return Math.min(max, Math.max(1, fallback));
}

// ------------------------------------------------------------------- Daten

async function refresh() {
  const data = await api("/api/state");
  Object.assign(state, data);

  const select = el("filter-profile");
  if (select.options.length <= 1) {
    Object.keys(state.profiles).sort().forEach((name) => {
      const option = document.createElement("option");
      option.value = name;
      option.textContent = `${name} — ${state.profiles[name].label}`;
      select.appendChild(option);
    });
  }
  if (!state.engine.loaded) {
    showBanner(`Engine offline: ${state.engine.error || "unbekannt"}`, "warn");
  }
  renderList();
  if (state.selected) renderDetail(state.selected);
  if (state.paramsOpen) renderParams();
}

function visibleClips() {
  const needle = el("search").value.toLowerCase();
  const profile = el("filter-profile").value;
  const status = el("filter-status").value;
  return state.clips.filter((clip) => {
    if (profile && clip.profile !== profile) return false;
    if (status === "locked" ? !clip.locked : status && clip.status !== status) return false;
    if (needle && !clip.text.toLowerCase().includes(needle)) return false;
    return true;
  });
}

function renderList() {
  const list = el("list");
  const clips = visibleClips();
  list.innerHTML = "";
  clips.forEach((clip) => {
    const row = document.createElement("div");
    row.className = "row" + (clip.key === state.selected ? " active" : "");
    row.innerHTML = `
      <span class="chip">${clip.profile}</span>
      <span class="text">${escapeHtml(clip.text)}</span>
      ${clip.locked ? '<span class="chip locked" title="Seed ist festgelegt">📌</span>' : ""}
      <span class="chip ${clip.status}">${STATUS_LABELS[clip.status] || clip.status}</span>`;
    row.onclick = () => select(clip.key);
    list.appendChild(row);
  });
  document.title = `Qwen-TTS (${clips.length})`;
}

function select(key) {
  state.selected = key;
  renderList();
  renderDetail(key);
  const active = document.querySelector(".row.active");
  if (active) active.scrollIntoView({ block: "nearest" });
}

// ------------------------------------------------------------- Detailsicht

function seedOrigin(clip, profile) {
  if (clip.locked) return "festgelegt per Lock";
  if (profile.seedPool.includes(clip.seed)) return "automatisch aus dem Seed-Pool";
  return "automatisch gewürfelt (Pool ist leer)";
}

function candidateCard(clip, cand, index, good) {
  const profile = state.profiles[clip.profile];
  const encoded = encodeURIComponent(clip.key);
  const inPool = profile.seedPool.includes(cand.seed);
  const isActive = clip.seed === cand.seed;
  const isLockedSeed = clip.locked && isActive;
  const classes = ["candidate",
    good.has(cand.seed) ? "good" : "",
    cand.fresh === false ? "outdated" : ""].join(" ");
  return `
    <div class="${classes}">
      <h4>${index < 9 ? `[${index + 1}] ` : ""}Seed ${cand.seed}
        ${isLockedSeed ? '<span class="chip locked">📌 festgelegt</span>'
          : isActive ? '<span class="chip">aktueller Seed</span>' : ""}
        ${inPool ? '<span class="chip pool">im Pool</span>' : ""}
      </h4>
      ${cand.fresh === false
        ? '<p class="muted small">⚠️ mit älteren Einstellungen erzeugt — ' +
          'Übernahme landet als „veraltet“</p>' : ""}
      <audio controls src="/candidates/${encoded}/${cand.seed}.wav"
             data-index="${index}"></audio>
      <p class="cand-actions">
        <button data-good="${cand.seed}"
                title="Hör-Notiz: klingt gut (nur lokal gemerkt)">
          ${good.has(cand.seed) ? "👍 gut" : "👍"}</button>
        <button data-discard="${cand.seed}"
                title="Klingt schlecht: Probeaufnahme löschen">👎</button>
        <button data-pool="${cand.seed}"
                title="${inPool
                  ? "Seed wieder aus dem Pool des Profils entfernen"
                  : `Seed in den Pool des Profils „${clip.profile}“ aufnehmen — ` +
                    "Clips ohne Lock bekommen ihre Seeds automatisch aus dem Pool"}">
          ${inPool ? "− Aus Seed-Pool" : "＋ In Seed-Pool"}</button>
        <button data-lock="${cand.seed}" ${isLockedSeed ? "disabled" : ""}
                title="Nur dieser Clip verwendet ab jetzt genau diesen Seed. Die Produktions-Audio entsteht beim nächsten finalen Lauf.">
          📌 Seed festlegen</button>
        <button data-promote="${cand.seed}" class="primary"
                title="Genau diese Aufnahme wird sofort die Produktions-Audio, der Seed wird festgelegt. Kein neues Rendern, kein erneutes Anhören nötig.">
          🚀 In Produktion</button>
      </p>
    </div>`;
}

function renderDetail(key) {
  const clip = state.clips.find((c) => c.key === key);
  if (!clip) return;
  const profile = state.profiles[clip.profile];
  const encoded = encodeURIComponent(clip.key);
  const good = goodSeeds(clip.key);
  const max = state.limits.maxCandidates || 16;

  el("detail").innerHTML = `
    <div class="card">
      <div class="mono muted">${clip.key}</div>
      <h2 style="margin:6px 0">${escapeHtml(clip.text)}</h2>
      ${clip.text !== clip.sourceText
        ? `<p class="muted">Original: ${escapeHtml(clip.sourceText)}</p>` : ""}
      <p class="muted">${clip.itemIds.length} Stelle(n) · Felder: ${clip.fields.join(", ")}
        ${clip.lessons.length ? " · Lektionen: " + clip.lessons.join(", ") : ""}</p>
      <p>Profil:
        <select id="clip-profile"
                title="Achtung: Profilwechsel legt den aktuellen Seed als Lock fest">
          ${Object.keys(state.profiles).sort().map((n) =>
            `<option value="${n}" ${n === clip.profile ? "selected" : ""}>${n}</option>`).join("")}
        </select>
        · Seed <span class="mono">${clip.seed}</span>
        <span class="muted">(${seedOrigin(clip, profile)})</span>
        · Status <span class="chip ${clip.status}">${STATUS_LABELS[clip.status] || clip.status}</span>
      </p>
      ${clip.status !== "missing"
        ? `<audio controls src="/audio/${encoded}.wav" id="main-audio"></audio>` +
          (clip.status === "stale"
            ? '<p class="muted small">Diese Aufnahme ist veraltet — Text, Seed oder ' +
              'Einstellungen haben sich seit dem Rendern geändert. Der nächste finale ' +
              'Lauf ersetzt sie.</p>' : "")
        : '<p class="muted">Noch nicht gerendert.</p>'}
      ${clip.locked
        ? '<p><button id="btn-unlock" title="Festgelegten Seed wieder freigeben — der Clip bekommt dann automatisch einen Seed aus dem Pool">Festlegung (Lock) entfernen</button></p>' : ""}
    </div>

    <div class="card">
      <h3 style="margin-top:0">Kandidaten
        <span class="muted normal">— Probeaufnahmen mit zufälligen Seeds</span></h3>
      <p>
        <button id="btn-candidates" class="primary">🎲 Kandidaten würfeln</button>
        <input id="cand-count" type="number" min="1" max="${max}"
               value="${candidateCount()}"
               title="Anzahl der Probeaufnahmen (1–${max})" /> Stück
        <span id="cand-progress" class="muted small"></span>
      </p>
      <details class="help">
        <summary>Was bedeuten die Aktionen?</summary>
        <ul class="small">
          <li><b>👍 / 👎</b> — Bewertung beim Anhören: 👍 hebt gute Kandidaten hervor
            (nur lokale Notiz), 👎 löscht die Probeaufnahme.</li>
          <li><b>＋ In Seed-Pool</b> — wirkt aufs ganze Profil „${clip.profile}“:
            alle Clips ohne Lock bekommen ihre Seeds automatisch aus diesem Pool.</li>
          <li><b>📌 Seed festlegen</b> — wirkt nur auf diesen Clip (Lock):
            er verwendet ab jetzt genau diesen Seed; gerendert wird beim nächsten
            finalen Lauf.</li>
          <li><b>🚀 In Produktion</b> — übernimmt genau diese Aufnahme sofort als
            Produktions-Audio und legt den Seed fest. Kein neues Rendern, kein
            erneutes Anhören nötig.</li>
        </ul>
      </details>
      <div class="candidates">
        ${clip.candidates.length === 0
          ? '<p class="muted">Noch keine. „🎲 Kandidaten würfeln“ erzeugt Probeaufnahmen.</p>' : ""}
        ${clip.candidates.map((cand, index) => candidateCard(clip, cand, index, good)).join("")}
      </div>
    </div>

    <div class="card">
      <h3 style="margin-top:0">Profil „${clip.profile}“ — ${profile.label}</h3>
      <textarea id="profile-instruct">${escapeHtml(profile.instruct)}</textarea>
      <p>
        <button id="btn-save-profile" class="primary">Instruktion speichern</button>
        <span class="muted small">Speichern macht alle Clips dieses Profils veraltet —
          der nächste finale Lauf rendert sie neu.</span>
      </p>
      <p class="muted small">Seed-Pool des Profils
        <span title="Clips ohne Lock bekommen ihre Seeds automatisch aus diesem Pool zugeteilt">ⓘ</span>:
        ${profile.seedPool.length === 0 ? "leer" : profile.seedPool.map((seed) =>
          `<span class="chip">${seed}
            <a href="#" data-unpool="${seed}" title="aus dem Pool entfernen">×</a></span>`).join(" ")}
      </p>
    </div>`;

  el("btn-candidates").onclick = guard(async () => {
    const count = Math.min(max, Math.max(1, Number(el("cand-count").value) || 4));
    localStorage.setItem("ttsCandCount", String(count));
    await post(`/api/clips/${encoded}/candidates`, { n: count });
    el("cand-progress").textContent = state.jobs.running
      ? "eingereiht — wartet auf den laufenden Job …" : "eingereiht …";
  });
  el("cand-count").onchange = () => {
    localStorage.setItem("ttsCandCount", el("cand-count").value);
  };
  const unlock = el("btn-unlock");
  if (unlock) {
    unlock.onclick = guard(async () => {
      await api(`/api/clips/${encoded}/lock`, { method: "DELETE" });
      await refresh();
      showBanner("Festlegung entfernt — der Clip bekommt seinen Seed wieder automatisch.", "ok");
    });
  }
  el("clip-profile").onchange = guard(async (event) => {
    await post(`/api/clips/${encoded}/lock`,
               { seed: clip.seed, profile: event.target.value });
    await refresh();
    showBanner(`Profil gewechselt — Seed ${clip.seed} wurde dabei festgelegt (Lock).`, "info");
  });
  el("btn-save-profile").onclick = guard(async () => {
    await put(`/api/profiles/${clip.profile}`,
              { instruct: el("profile-instruct").value });
    await refresh();
    showBanner(`Instruktion gespeichert — Clips des Profils „${clip.profile}“ sind jetzt veraltet.`, "ok");
  });
  el("detail").querySelectorAll("[data-good]").forEach((button) => {
    button.onclick = () => {
      toggleGood(clip.key, Number(button.dataset.good));
      renderDetail(clip.key);
    };
  });
  el("detail").querySelectorAll("[data-discard]").forEach((button) => {
    button.onclick = guard(async () => {
      await api(`/api/clips/${encoded}/candidates/${button.dataset.discard}`,
                { method: "DELETE" });
      await refresh();
    });
  });
  el("detail").querySelectorAll("[data-pool]").forEach((button) => {
    button.onclick = guard(async () => {
      const seed = Number(button.dataset.pool);
      const inPool = profile.seedPool.includes(seed);
      if (inPool) {
        await api(`/api/profiles/${clip.profile}/pool/${seed}`, { method: "DELETE" });
      } else {
        await post(`/api/profiles/${clip.profile}/pool`, { seed });
      }
      await refresh();
      showBanner(inPool
        ? `Seed ${seed} aus dem Pool von „${clip.profile}“ entfernt.`
        : `Seed ${seed} in den Pool von „${clip.profile}“ aufgenommen — Clips ohne ` +
          `Lock verteilen sich automatisch auf die Pool-Seeds.`, "ok");
    });
  });
  el("detail").querySelectorAll("[data-lock]").forEach((button) => {
    button.onclick = guard(async () => {
      await post(`/api/clips/${encoded}/lock`, { seed: Number(button.dataset.lock) });
      await refresh();
      showBanner(`Seed ${button.dataset.lock} für diesen Clip festgelegt — die ` +
        `Produktions-Audio entsteht beim nächsten finalen Lauf.`, "ok");
    });
  });
  el("detail").querySelectorAll("[data-promote]").forEach((button) => {
    button.onclick = guard(async () => {
      const result = await post(`/api/clips/${encoded}/promote`,
                                { seed: Number(button.dataset.promote) });
      await refresh();
      showBanner(result.verified
        ? "In Produktion übernommen — der Clip ist fertig, kein neues Rendern nötig."
        : "Übernommen und Seed festgelegt — aber die Aufnahme entstand mit älteren " +
          "Einstellungen, der Clip bleibt „veraltet“, bis neu gerendert wird.",
        result.verified ? "ok" : "info");
    });
  });
  el("detail").querySelectorAll("[data-unpool]").forEach((link) => {
    link.onclick = guard(async (event) => {
      event.preventDefault();
      await api(`/api/profiles/${clip.profile}/pool/${link.dataset.unpool}`,
                { method: "DELETE" });
      await refresh();
    });
  });
}

// --------------------------------------------------------- Parameter-Panel

const SAMPLING_HINTS = {
  temperature: "Höher = mehr Variation zwischen Seeds, niedriger = gleichmäßiger",
  top_k: "Nur die k wahrscheinlichsten Tokens werden gezogen",
  top_p: "Nucleus-Sampling: kumulierte Wahrscheinlichkeitsmasse",
  repetition_penalty: "Bestraft Wiederholungen (>1 = weniger Wiederholung)",
};

function paramsCard(name) {
  const profile = state.profiles[name];
  const sampling = Object.keys(profile.sampling).sort().map((param) => `
    <label class="param">
      <span title="${SAMPLING_HINTS[param] || ""}">${param}</span>
      <input type="number" step="any" data-param="${param}"
             value="${profile.sampling[param]}" />
    </label>`).join("");
  return `
    <div class="card" data-profile="${name}">
      <h3 style="margin-top:0">${name} — ${escapeHtml(profile.label)}</h3>
      <div class="params-grid">${sampling}</div>
      <p>
        <label><input type="checkbox" data-trim ${profile.trim ? "checked" : ""} />
          Stille am Anfang/Ende wegschneiden (trim)</label>
        <label><input type="checkbox" data-norm ${profile.normalize ? "checked" : ""} />
          Lautstärke normalisieren</label>
      </p>
      <textarea data-instruct>${escapeHtml(profile.instruct)}</textarea>
      <p class="muted small">Seed-Pool: ${profile.seedPool.length === 0 ? "leer"
        : profile.seedPool.join(", ")}</p>
      <p>
        <button data-save class="primary">Speichern</button>
        <button data-save-all
                title="Nur die Sampling-Werte dieser Karte auf alle Profile übertragen">
          Sampling auf alle Profile übertragen</button>
      </p>
    </div>`;
}

function renderParams() {
  const panel = el("params-panel");
  if (!state.paramsOpen) {
    panel.classList.add("hidden");
    return;
  }
  panel.classList.remove("hidden");
  panel.innerHTML = `
    <div class="card">
      <h2 style="margin-top:0">⚙️ TTS-Parameter <button id="btn-params-close"
        style="float:right">Schließen</button></h2>
      <p class="muted small">Jede Änderung an Instruktion, Sampling, Trim oder
        Normalisierung macht alle Clips des jeweiligen Profils <b>veraltet</b> —
        der nächste finale Lauf rendert sie neu.</p>
    </div>
    ${Object.keys(state.profiles).sort().map(paramsCard).join("")}`;

  el("btn-params-close").onclick = () => {
    state.paramsOpen = false;
    renderParams();
  };

  const readSampling = (card) => {
    const sampling = {};
    card.querySelectorAll("[data-param]").forEach((input) => {
      sampling[input.dataset.param] = Number(input.value);
    });
    return sampling;
  };

  panel.querySelectorAll("[data-save]").forEach((button) => {
    button.onclick = guard(async () => {
      const card = button.closest("[data-profile]");
      const name = card.dataset.profile;
      await put(`/api/profiles/${name}`, {
        instruct: card.querySelector("[data-instruct]").value,
        sampling: readSampling(card),
        trim: card.querySelector("[data-trim]").checked,
        normalize: card.querySelector("[data-norm]").checked,
      });
      await refresh();
      showBanner(`Profil „${name}“ gespeichert — geänderte Clips sind jetzt veraltet.`, "ok");
    });
  });
  panel.querySelectorAll("[data-save-all]").forEach((button) => {
    button.onclick = guard(async () => {
      const sampling = readSampling(button.closest("[data-profile]"));
      for (const name of Object.keys(state.profiles)) {
        await put(`/api/profiles/${name}`, { sampling });
      }
      await refresh();
      showBanner("Sampling-Werte auf alle Profile übertragen — betroffene Clips sind jetzt veraltet.", "ok");
    });
  });
}

// -------------------------------------------------------- Job-Fortschritt

const job = { name: null, startedAt: null };

function jobLabel(name) {
  if (!name) return "";
  if (name.startsWith("candidates:")) {
    const key = name.slice("candidates:".length);
    const clip = state.clips.find((c) => c.key === key);
    const text = clip ? `„${clip.text.slice(0, 30)}${clip.text.length > 30 ? "…" : ""}“` : key;
    return `Kandidaten für ${text}`;
  }
  if (name.startsWith("render:")) return `Finaler Lauf (${name.slice("render:".length)})`;
  return name;
}

function formatEta(ms) {
  const seconds = Math.round(ms / 1000);
  if (seconds < 90) return `~${seconds} s`;
  return `~${Math.round(seconds / 60)} min`;
}

function setJobIdle(text) {
  job.name = null;
  el("job-spinner").classList.add("hidden");
  el("job-bar-track").classList.add("hidden");
  el("btn-cancel").classList.add("hidden");
  el("job-text").textContent = text || "";
}

function setJobRunning(name) {
  job.name = name;
  job.startedAt = Date.now();
  el("job-spinner").classList.remove("hidden");
  el("job-bar-track").classList.remove("hidden");
  el("job-bar").style.width = "0%";
  el("btn-cancel").classList.remove("hidden");
  el("job-text").textContent = `läuft: ${jobLabel(name)}`;
}

function onProgress(event) {
  el("job-bar").style.width = `${Math.round((event.index / event.total) * 100)}%`;
  const elapsed = Date.now() - (job.startedAt || Date.now());
  const remaining = event.index > 0
    ? (elapsed / event.index) * (event.total - event.index) : null;
  el("job-text").textContent =
    `${jobLabel(job.name)} · ${event.index}/${event.total}` +
    (remaining !== null && event.index < event.total
      ? ` · noch ${formatEta(remaining)}` : "") +
    (event.status === "failed" ? " · ⚠️" : "");
  if (event.type === "candidate" && event.clipKey === state.selected) {
    const inline = el("cand-progress");
    if (inline) inline.textContent = `erzeuge Probeaufnahme ${event.index}/${event.total} …`;
  }
}

// ------------------------------------------------------------------ Events

document.addEventListener("keydown", (event) => {
  if (["INPUT", "TEXTAREA", "SELECT"].includes(event.target.tagName)) return;
  const clips = visibleClips();
  const current = clips.findIndex((c) => c.key === state.selected);

  if (event.key === "j" && current < clips.length - 1) {
    select(clips[current + 1].key);
  } else if (event.key === "k" && current > 0) {
    select(clips[current - 1].key);
  } else if (event.key === " ") {
    event.preventDefault();
    const audio = el("main-audio");
    if (audio) {
      audio.currentTime = 0;
      audio.play();
    }
  } else if (/^[1-9]$/.test(event.key)) {
    const audio = el("detail").querySelector(`audio[data-index="${Number(event.key) - 1}"]`);
    if (audio) {
      audio.currentTime = 0;
      audio.play();
    }
  }
});

["search", "filter-profile", "filter-status"].forEach((id) => {
  el(id).addEventListener("input", renderList);
});

el("banner-close").onclick = () => {
  el("banner").className = "hidden";
};

el("btn-params").onclick = () => {
  state.paramsOpen = !state.paramsOpen;
  renderParams();
};

el("btn-render").onclick = guard(async () => {
  const profile = el("filter-profile").value || null;
  const label = profile ? `Profil „${profile}“` : "alle Profile";
  if (!confirm(`Finalen Lauf für ${label} starten? Gerendert wird nur, was fehlt oder veraltet ist.`)) return;
  await post("/api/render", { profile });
});

el("btn-cancel").onclick = guard(() => post("/api/jobs/cancel", {}));

// The last `job-summary` of the running job. render_clips keeps going after a
// failed clip, so "job-done" on its own says nothing about success.
let lastSummary = null;

const events = new EventSource("/events");
events.onmessage = (message) => {
  const event = JSON.parse(message.data);
  if (event.type === "render" || event.type === "candidate") {
    onProgress(event);
  } else if (event.type === "job-summary") {
    lastSummary = event;
  } else if (event.type === "job-done") {
    const summary = lastSummary;
    lastSummary = null;
    if (summary && summary.failed > 0) {
      setJobIdle("");
      showBanner(`${summary.failed} von ${summary.failed + summary.rendered} ` +
        `fehlgeschlagen! ${summary.rendered} erzeugt, ${summary.skipped} übersprungen.`, "warn");
    } else if (summary) {
      setJobIdle(`fertig — ${summary.rendered} erzeugt, ${summary.skipped} übersprungen`);
    } else {
      setJobIdle("fertig");
    }
    refresh().catch(showError);
  } else if (event.type === "job-error") {
    lastSummary = null;
    setJobIdle("");
    showBanner(event.message, "warn");
    refresh().catch(showError);
  } else if (event.type === "job-start") {
    lastSummary = null;
    setJobRunning(event.job);
  } else if ("running" in event && event.running) {
    // Initialframe des SSE-Streams: es läuft bereits ein Job.
    setJobRunning(event.running);
  }
};

refresh().catch(showError);
```

- [ ] **Step 3: `style.css` ersetzen** — kompletter neuer Inhalt:

```css
:root {
  --bg: #fdfaf5; --panel: #fff; --line: #e6ded2; --text: #2f2a24;
  --muted: #857a6c; --accent: #c4622d; --ok: #4f7a3f; --warn: #b8862b;
  --stale: #9a5b8f;
}
* { box-sizing: border-box; }
body {
  margin: 0; font: 15px/1.5 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
  background: var(--bg); color: var(--text); height: 100vh; display: flex;
  flex-direction: column;
}
header {
  display: flex; gap: 12px; align-items: center; padding: 10px 16px;
  border-bottom: 1px solid var(--line); background: var(--panel); flex-wrap: wrap;
}
header h1 { font-size: 16px; margin: 0 12px 0 0; }
main { flex: 1; display: grid; grid-template-columns: minmax(320px, 1fr) 1.4fr; min-height: 0; }
#list { overflow-y: auto; border-right: 1px solid var(--line); }
#detail { overflow-y: auto; padding: 20px; }
.row {
  padding: 8px 14px; border-bottom: 1px solid var(--line); cursor: pointer;
  display: flex; gap: 10px; align-items: baseline;
}
.row:hover { background: #f6f0e7; }
.row.active { background: #f0e5d6; box-shadow: inset 3px 0 0 var(--accent); }
.row .text { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.chip {
  font-size: 11px; padding: 1px 7px; border-radius: 10px; border: 1px solid var(--line);
  color: var(--muted); white-space: nowrap;
}
.chip.missing { color: var(--muted); }
.chip.rendered { color: var(--ok); border-color: var(--ok); }
.chip.stale { color: var(--stale); border-color: var(--stale); }
.chip.locked { color: var(--accent); border-color: var(--accent); }
.chip.pool { color: var(--ok); border-color: var(--ok); }
.card {
  background: var(--panel); border: 1px solid var(--line); border-radius: 8px;
  padding: 14px; margin-bottom: 14px;
}
.candidates { display: flex; gap: 12px; flex-wrap: wrap; }
.candidate { border: 1px solid var(--line); border-radius: 8px; padding: 10px; min-width: 230px; max-width: 320px; }
.candidate h4 { margin: 0 0 6px; font-size: 13px; font-family: ui-monospace, monospace; }
.candidate audio { width: 100%; }
.candidate.good { border-color: var(--ok); box-shadow: 0 0 0 1px var(--ok); }
.candidate.outdated { opacity: 0.6; }
.cand-actions { display: flex; gap: 6px; flex-wrap: wrap; margin: 8px 0 0; }
button {
  font: inherit; padding: 5px 11px; border-radius: 6px; border: 1px solid var(--line);
  background: var(--panel); cursor: pointer;
}
button:hover { background: #f6f0e7; }
button:disabled { opacity: 0.5; cursor: default; }
button.primary { background: var(--accent); color: #fff; border-color: var(--accent); }
input, select, textarea {
  font: inherit; padding: 5px 8px; border: 1px solid var(--line); border-radius: 6px;
  background: var(--panel); color: inherit;
}
input[type="number"] { width: 70px; }
input[type="checkbox"] { width: auto; }
textarea { width: 100%; min-height: 90px; resize: vertical; }
.muted { color: var(--muted); font-size: 13px; }
.muted.normal { font-weight: normal; }
.small { font-size: 12px; }
.mono { font-family: ui-monospace, SFMono-Regular, monospace; font-size: 12px; }
.warn { color: var(--warn); }
.hidden { display: none !important; }

/* Job-Fortschritt in der Kopfzeile */
#job {
  display: flex; align-items: center; gap: 8px; flex: 1;
  justify-content: flex-end; min-width: 240px;
}
#job-text { color: var(--muted); font-size: 13px; text-align: right; }
#job-bar-track {
  width: 140px; height: 8px; border-radius: 4px; background: #efe8dc;
  overflow: hidden; flex: none;
}
#job-bar { height: 100%; width: 0; background: var(--accent); transition: width 0.3s; }
.spinner {
  width: 14px; height: 14px; flex: none; border: 2px solid var(--line);
  border-top-color: var(--accent); border-radius: 50%;
  animation: spin 0.8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* Meldungs-Banner unter der Kopfzeile */
#banner {
  display: flex; gap: 12px; align-items: center; padding: 8px 16px;
  border-bottom: 1px solid var(--line); font-size: 14px;
}
#banner.warn { background: #fbeeda; color: #7a5a1d; }
#banner.ok { background: #e9f2e4; color: #3c5e30; }
#banner.info { background: #efe9f3; color: #5f4a68; }
#banner button { margin-left: auto; border: none; background: none; font-size: 16px; }

/* Hilfetexte */
details.help { margin: 4px 0 10px; }
details.help summary { cursor: pointer; color: var(--muted); font-size: 13px; }
details.help ul { margin: 6px 0; padding-left: 20px; }

/* Parameter-Overlay */
#params-panel {
  position: fixed; top: 0; right: 0; bottom: 0; width: min(560px, 92vw);
  overflow-y: auto; background: var(--bg); border-left: 1px solid var(--line);
  padding: 16px; box-shadow: -6px 0 18px rgba(0, 0, 0, 0.10); z-index: 10;
}
.params-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 8px 16px; }
.param { display: flex; align-items: center; justify-content: space-between; gap: 8px; }
.param span { font-size: 13px; color: var(--muted); }
```

- [ ] **Step 4: Syntax-Check und Server-Suite**

Run: `node --check tools/tts/ttskit/static/app.js && cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q`
Expected: kein Syntaxfehler, Suite PASS.

- [ ] **Step 5: Commit**

```bash
git add tools/tts/ttskit/static/
git commit -m "feat(tts): Web-UI mit Fortschritt, klaren Aktionen, Bewertung und Parameter-Panel"
```

---

### Task 4: README aktualisieren und Gesamtverifikation

**Files:**
- Modify: `tools/tts/README.md`

**Interfaces:**
- Consumes: Endzustand aus Task 1–3.
- Produces: nichts — Doku und Abschluss.

- [ ] **Step 1: README anpassen** — im Abschnitt „Ablauf" den Kuratier-Satz aktualisieren:

Alt:

> Typisch: einmal `sample` pro Profil, im Web-Interface die guten Seeds mit „✓ Pool"
> sammeln, dann `render`. Einzelne schlechte Clips im Web-Interface mit „🎲 4 Kandidaten"
> neu würfeln und den besten per „📌 Lock" festnageln.

Neu:

> Typisch: einmal `sample` pro Profil, im Web-Interface die guten Seeds mit
> „＋ In Seed-Pool" sammeln, dann `render`. Einzelne schlechte Clips im Web-Interface
> mit „🎲 Kandidaten würfeln" (Anzahl einstellbar, 1–16) neu erzeugen, mit 👍/👎
> vorsortieren und dann entweder per „📌 Seed festlegen" fürs nächste Rendern locken —
> oder per „🚀 In Produktion" die gehörte Aufnahme direkt als Produktions-Audio
> übernehmen (kopiert die WAV, lockt den Seed, kein Re-Render und kein erneutes
> Anhören nötig). Sampling-Parameter, Trim/Normalisierung und Instruktionen aller
> Profile sind über „⚙️ TTS-Parameter" in der Kopfzeile editierbar.

Zusätzlich unter „Dateien" nach dem `locks.json`-Hinweis einen Satz ergänzen:

> Kandidaten unter `out/candidates/` tragen seit dem UI-Redesign eine
> Sidecar-Datei `{seed}.json` mit dem Erzeugungs-Fingerprint. „🚀 In Produktion"
> markiert einen Clip nur dann als fertig, wenn dieser Fingerprint noch den
> aktuellen Einstellungen entspricht — sonst wird die Aufnahme zwar übernommen
> und der Seed gelockt, der Clip bleibt aber „veraltet" (in der UI) bzw. `stale`.

- [ ] **Step 2: Gesamte Suite + JS-Syntax final laufen lassen**

Run: `cd tools/tts && ~/qwen-tts-test/.venv/bin/python -m pytest tests/ -q && node --check ttskit/static/app.js`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add tools/tts/README.md
git commit -m "docs(tts): README an das neue Web-UI angepasst"
```
