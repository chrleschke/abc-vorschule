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
        : profile.seedPool.map((seed) =>
            `<span class="chip">${seed}
              <a href="#" data-panel-unpool="${seed}" data-profile-name="${name}"
                 title="aus dem Pool entfernen">×</a></span>`).join(" ")}</p>
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
      const value = Number(input.value);
      // Number("") ist 0 — ein geleertes Feld darf nicht stillschweigend als
      // 0 gespeichert werden.
      if (input.value.trim() === "" || Number.isNaN(value)) {
        throw new Error(`Sampling-Parameter „${input.dataset.param}“ ist leer oder keine Zahl`);
      }
      sampling[input.dataset.param] = value;
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
  panel.querySelectorAll("[data-panel-unpool]").forEach((link) => {
    link.onclick = guard(async (event) => {
      event.preventDefault();
      const name = link.dataset.profileName;
      const seed = link.dataset.panelUnpool;
      await api(`/api/profiles/${name}/pool/${seed}`, { method: "DELETE" });
      // refresh() ruft renderParams() selbst wieder auf, solange das Panel
      // offen ist — kein zusätzlicher renderParams()-Aufruf nötig.
      await refresh();
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

// state.jobs.queued kommt nur über refresh() rein (kein eigenes Polling) —
// das ist beim Anhängen an den Job-Text ausreichend aktuell.
function queueSuffix() {
  return state.jobs.queued > 0 ? ` · ${state.jobs.queued} in Warteschlange` : "";
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
  el("job-text").textContent = `läuft: ${jobLabel(name)}${queueSuffix()}`;
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
    (event.status === "failed" ? " · ⚠️" : "") +
    queueSuffix();
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
  } else if ("running" in event) {
    if (event.running) {
      // Initialframe des SSE-Streams: es läuft bereits ein Job.
      setJobRunning(event.running);
    } else if (job.name) {
      // Reconnect nach Verbindungsabriss: der Job endete, während wir
      // getrennt waren. Ohne diesen Zweig bliebe die Kopfzeile für immer auf
      // "läuft" stehen (Spinner+Abbrechen nie zurückgesetzt). Der Guard auf
      // job.name verhindert einen unnötigen Doppel-Refresh beim allerersten
      // Initialframe direkt nach dem Seitenladen.
      setJobIdle("");
      refresh().catch(showError);
    }
  }
};

refresh().catch(showError);
