"use strict";

const state = {
  clips: [], profiles: {}, engine: {}, orphans: [], selected: null,
  jobs: { running: null, queued: 0 }, limits: { maxCandidates: 16 },
  voices: [], languages: [], paramsOpen: false,
  // Batch-Auswahl und aufgeklappter Profil-Editor überleben jedes refresh().
  selectedKeys: new Set(), profileEditOpen: false,
};
const el = (id) => document.getElementById(id);

const STATUS_LABELS = { missing: "fehlt", rendered: "fertig" };

// Sprachen, in denen eine ostasiatische Stimme zu Hause ist. Nur außerhalb
// davon ist ihre Herkunft ein Hinweis wert.
const EAST_ASIAN = ["chinese", "japanese", "korean"];

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

// ------------------------------------------------------------- Stimmen

const voiceOf = (name) => state.voices.find((v) => v.name === name);

// Die Herkunft steht immer hinter dem Namen: `language` setzt zwar die
// Phonologie, das Speaker-Embedding bringt aber den Akzent seiner Kernsprache
// mit. Bei einem einzelnen Laut fehlt der Kontext, der das ausgleicht — und
// genau dort fällt es auf.
function voiceOptions(selected) {
  const known = state.voices.map((v) =>
    `<option value="${escapeHtml(v.name)}" ${v.name === selected ? "selected" : ""}>` +
    `${escapeHtml(v.name)} (${escapeHtml(v.origin)})</option>`).join("");
  // Eine von Hand eingetragene Stimme, die die Tabelle nicht kennt, darf beim
  // Aufklappen nicht stillschweigend zu einer anderen werden.
  return voiceOf(selected) || !selected
    ? known
    : `<option value="${escapeHtml(selected)}" selected>` +
      `${escapeHtml(selected)} (unbekannte Herkunft)</option>${known}`;
}

function languageOptions(selected) {
  return state.languages.map((name) =>
    `<option value="${escapeHtml(name)}" ${name === selected ? "selected" : ""}>` +
    `${escapeHtml(name)}</option>`).join("");
}

function accentBadge(speaker, language) {
  const voice = voiceOf(speaker);
  if (!voice) {
    return '<span class="chip warn-chip" title="Diese Stimme kennt die ' +
      'Stimmtabelle nicht — Herkunft und Eignung sind unbekannt.">⚠️ unbekannte Stimme</span>';
  }
  if (voice.european || EAST_ASIAN.includes(language)) return "";
  return `<span class="chip warn-chip" title="Die Sprache setzt die Phonologie, ` +
    `die Stimme bringt trotzdem den Akzent ihrer Kernsprache mit. Bei ganzen ` +
    `Sätzen gleicht der Kontext das weitgehend aus, bei einem einzelnen Laut ` +
    `nicht — dort schlägt der Akzent voll durch.">⚠️ Stimme ist ` +
    `${escapeHtml(voice.origin)}, gesprochen wird ${escapeHtml(language)}</span>`;
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

// --------------------------------------------------- lokale UI-Einstellungen

// Nur Ansichts-Zustand liegt im localStorage (Filter, Auswahl, Anzahl).
// Alles Inhaltliche — Bewertungen, Locks, Profile — liegt beim Server in
// Dateien und übersteht damit auch einen Browser- oder Rechnerwechsel.
function readLocal(key, fallback) {
  try {
    const raw = localStorage.getItem(key);
    return raw === null ? fallback : JSON.parse(raw);
  } catch {
    return fallback;
  }
}

const writeLocal = (key, value) =>
  localStorage.setItem(key, JSON.stringify(value));

function candidateCount() {
  const max = state.limits.maxCandidates || 16;
  const stored = Number(readLocal("ttsCandCount", 4));
  const fallback = Number.isFinite(stored) && stored >= 1 ? Math.round(stored) : 4;
  return Math.min(max, Math.max(1, fallback));
}

const useKnownSeeds = () => readLocal("ttsUseKnownSeeds", false) === true;

// Eigener Zähler für den Batch-Lauf: klein gehalten, weil er über mehrere
// ausgewählte Clips hinweg multipliziert — anders als „Generate“,
// das nur einen einzigen Clip trifft.
function batchCount() {
  const max = state.limits.maxCandidates || 16;
  const stored = Number(readLocal("ttsBatchCount", 2));
  const fallback = Number.isFinite(stored) && stored >= 1 ? Math.round(stored) : 2;
  return Math.min(max, Math.max(1, fallback));
}

function persistViewState() {
  writeLocal("ttsView", {
    search: el("search").value,
    profile: el("filter-profile").value,
    status: el("filter-status").value,
    selected: state.selected,
  });
  writeLocal("ttsSelection", [...state.selectedKeys]);
}

function restoreViewState() {
  const view = readLocal("ttsView", {});
  el("search").value = view.search || "";
  el("filter-profile").value = view.profile || "";
  el("filter-status").value = view.status || "";
  state.selectedKeys = new Set(readLocal("ttsSelection", []));
  const live = new Set(state.clips.map((c) => c.key));
  if (view.selected && live.has(view.selected)) state.selected = view.selected;
}

// ------------------------------------------------------------------- Daten

let restored = false;

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
  if (!restored) {
    // Erst nach dem ersten /api/state: vorher gibt es weder Profil-Optionen
    // noch Clip-Keys, gegen die sich die gespeicherte Ansicht prüfen ließe.
    restored = true;
    restoreViewState();
  }
  // Clips können nach einer Content-Änderung verschwinden — die Auswahl
  // eines Batch-Laufs darf dann keine toten Keys mehr enthalten.
  const live = new Set(state.clips.map((c) => c.key));
  state.selectedKeys = new Set([...state.selectedKeys].filter((k) => live.has(k)));

  if (!state.engine.loaded) {
    showBanner(`Engine offline: ${state.engine.error || "unbekannt"}`, "warn");
  }
  renderList();
  if (state.selected) withPreservedInput(() => renderDetail(state.selected));
  if (state.paramsOpen) renderParams();
}

// renderDetail() ersetzt die komplette Detailsicht. Ohne das hier verliert man
// mitten im Tippen einer Aussprache seinen Text, sobald irgendein SSE-Ereignis
// ein refresh() auslöst — und Aussprachen tippt man mit Pausen zum Nachdenken.
function withPreservedInput(render) {
  const active = document.activeElement;
  const editing = active && active.id && el("detail").contains(active) &&
    ["INPUT", "TEXTAREA"].includes(active.tagName);
  const snapshot = editing
    ? { id: active.id, value: active.value,
        start: active.selectionStart, end: active.selectionEnd }
    : null;
  render();
  if (snapshot === null) return;
  const restoredInput = el(snapshot.id);
  if (!restoredInput) return;
  restoredInput.value = snapshot.value;
  restoredInput.focus();
  if (snapshot.start !== null && restoredInput.setSelectionRange) {
    restoredInput.setSelectionRange(snapshot.start, snapshot.end);
  }
}

function visibleClips() {
  const needle = el("search").value.toLowerCase();
  const profile = el("filter-profile").value;
  const status = el("filter-status").value;
  return state.clips.filter((clip) => {
    if (profile && clip.profile !== profile) return false;
    if (status === "locked" ? !clip.locked : status && clip.status !== status) return false;
    // Beide Texte durchsuchen: wer nach dem Satz aus der App sucht, findet den
    // Clip sonst nicht mehr, sobald eine eigene Aussprache hinterlegt ist.
    if (needle && !clip.text.toLowerCase().includes(needle) &&
        !clip.sourceText.toLowerCase().includes(needle)) return false;
    return true;
  });
}

// -------------------------------------------------- Liste und Batch-Auswahl

function updateBatchUi() {
  const count = state.selectedKeys.size;
  el("sel-count").textContent = count ? `${count} ausgewählt` : "nichts ausgewählt";
  el("btn-render").textContent = count ? `▶ Batch-Lauf (${count})` : "▶ Batch-Lauf";
  el("btn-render").disabled = count === 0;
}

function setSelection(keys) {
  state.selectedKeys = new Set(keys);
  persistViewState();
  renderList();
}

function renderList() {
  const list = el("list");
  const clips = visibleClips();
  list.innerHTML = "";
  clips.forEach((clip) => {
    const row = document.createElement("div");
    row.className = "row" + (clip.key === state.selected ? " active" : "");
    const spoken = clip.text !== clip.sourceText;
    const ownVoice = clip.speaker !== state.profiles[clip.profile].speaker;
    row.innerHTML = `
      <input type="checkbox" class="sel" ${state.selectedKeys.has(clip.key) ? "checked" : ""}
             title="Für den Batch-Lauf auswählen" />
      <span class="chip">${clip.profile}</span>
      <span class="text">
        <span class="row-source">${escapeHtml(clip.sourceText)}</span>
        ${spoken ? `<span class="row-tts">🔊 ${escapeHtml(clip.text)}</span>` : ""}
      </span>
      ${ownVoice ? `<span class="chip changed" title="Eigene Stimme: ${escapeHtml(clip.speaker)}">🎙</span>` : ""}
      ${clip.locked ? '<span class="chip locked" title="Seed ist festgelegt">📌</span>' : ""}
      <span class="chip ${clip.status}">${STATUS_LABELS[clip.status] || clip.status}</span>`;
    const checkbox = row.querySelector(".sel");
    checkbox.onclick = (event) => {
      event.stopPropagation();
      if (checkbox.checked) state.selectedKeys.add(clip.key);
      else state.selectedKeys.delete(clip.key);
      persistViewState();
      updateBatchUi();
    };
    row.onclick = () => select(clip.key);
    list.appendChild(row);
  });
  document.title = `Qwen-TTS (${clips.length})`;
  updateBatchUi();
}

function select(key) {
  state.selected = key;
  persistViewState();
  renderList();
  renderDetail(key);
  const active = document.querySelector(".row.active");
  if (active) active.scrollIntoView({ block: "nearest" });
}

// ------------------------------------------------- Profil-Formular (geteilt)

// Ein Formular, zwei Orte: die Zusammenfassung oben in der Detailsicht und
// das ⚙️-Panel mit allen Profilen. Vorher gab es beides doppelt — mit leicht
// unterschiedlichem Verhalten.

const profileClipCount = (name) =>
  state.clips.filter((c) => c.profile === name).length;

const SAMPLING_HINTS = {
  temperature: "Höher = mehr Variation zwischen Seeds, niedriger = gleichmäßiger",
  top_k: "Nur die k wahrscheinlichsten Tokens werden gezogen",
  top_p: "Nucleus-Sampling: kumulierte Wahrscheinlichkeitsmasse",
  repetition_penalty: "Bestraft Wiederholungen (>1 = weniger Wiederholung)",
};

function profileFormHtml(name) {
  const profile = state.profiles[name];
  const sampling = Object.keys(profile.sampling).sort().map((param) => `
    <label class="param">
      <span title="${SAMPLING_HINTS[param] || ""}">${param}</span>
      <input type="number" step="any" data-param="${param}"
             value="${profile.sampling[param]}" />
    </label>`).join("");
  return `
    <p class="voice-line">
      <label>Stimme
        <select data-speaker>${voiceOptions(profile.speaker)}</select></label>
      <label>Sprache
        <select data-language>${languageOptions(profile.language)}</select></label>
      ${accentBadge(profile.speaker, profile.language)}
    </p>
    <label class="muted small">Instruktion — Sprechanweisung für alle Clips dieses Profils</label>
    <textarea data-instruct>${escapeHtml(profile.instruct)}</textarea>
    <div class="params-grid">${sampling}</div>
    <p>
      <label><input type="checkbox" data-trim ${profile.trim ? "checked" : ""} />
        Stille am Anfang/Ende wegschneiden (trim)</label>
      <label><input type="checkbox" data-norm ${profile.normalize ? "checked" : ""} />
        Lautstärke normalisieren</label>
    </p>
    <p class="muted small">Seed-Pool
      <span title="Clips ohne Lock bekommen ihre Seeds automatisch aus diesem Pool zugeteilt. 👍 an einer Probeaufnahme nimmt ihren Seed hier auf.">ⓘ</span>:
      ${profile.seedPool.length === 0 ? "leer — 👍 an einer Probeaufnahme füllt ihn"
        : profile.seedPool.map((seed) =>
            `<span class="chip">${seed}
              <a href="#" data-unpool="${seed}" data-pool-profile="${name}"
                 title="aus dem Pool entfernen">×</a></span>`).join(" ")}
    </p>`;
}

function readProfileForm(container) {
  const sampling = {};
  container.querySelectorAll("[data-param]").forEach((input) => {
    const value = Number(input.value);
    // Number("") ist 0 — ein geleertes Feld darf nicht stillschweigend als
    // 0 gespeichert werden.
    if (input.value.trim() === "" || Number.isNaN(value)) {
      throw new Error(`Sampling-Parameter „${input.dataset.param}“ ist leer oder keine Zahl`);
    }
    sampling[input.dataset.param] = value;
  });
  return {
    instruct: container.querySelector("[data-instruct]").value,
    speaker: container.querySelector("[data-speaker]").value,
    language: container.querySelector("[data-language]").value,
    sampling,
    trim: container.querySelector("[data-trim]").checked,
    normalize: container.querySelector("[data-norm]").checked,
  };
}

function wirePoolLinks(container) {
  container.querySelectorAll("[data-unpool]").forEach((link) => {
    link.onclick = guard(async (event) => {
      event.preventDefault();
      await api(`/api/profiles/${link.dataset.poolProfile}/pool/${link.dataset.unpool}`,
                { method: "DELETE" });
      await refresh();
    });
  });
}

const saveProfileFrom = (container, name) => guard(async () => {
  await put(`/api/profiles/${name}`, readProfileForm(container));
  await refresh();
  showBanner(`Profil „${name}“ gespeichert — neue Kandidaten verwenden ab sofort ` +
    `diese Einstellungen. Bereits produzierte Clips bleiben unverändert.`, "ok");
});

// ------------------------------------------------------------- Detailsicht

function seedOrigin(clip, profile) {
  if (clip.locked) return "festgelegt per Lock";
  if (profile.seedPool.includes(clip.seed)) return "automatisch aus dem Seed-Pool";
  return "automatisch gewürfelt (Pool ist leer)";
}

function formatWhen(iso) {
  if (!iso) return "—";
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return "—";
  return date.toLocaleDateString("de-DE", { day: "2-digit", month: "2-digit" }) +
    " " + date.toLocaleTimeString("de-DE", { hour: "2-digit", minute: "2-digit" });
}

// Die Kurzfassung der globalen Einstellungen: erst der Klick auf „Bearbeiten“
// klappt das Formular auf — die Detailsicht bleibt so oben ruhig.
function profileSummaryCard(clip, profile) {
  const instructShort = profile.instruct.length > 90
    ? profile.instruct.slice(0, 90) + "…" : profile.instruct;
  return `
    <div class="card profile-card">
      <div class="profile-summary">
        <div class="summary-text">
          <b>⚙️ Profil „${clip.profile}“ — ${escapeHtml(profile.label)}</b>
          <span class="muted">gilt für alle ${profileClipCount(clip.profile)} Clips dieses Profils</span>
          <div class="muted small">
            Stimme ${escapeHtml(profile.speaker)} · Sprache ${escapeHtml(profile.language)}
            · temperature ${profile.sampling.temperature}
            · „${escapeHtml(instructShort)}“
          </div>
        </div>
        <button id="btn-profile-toggle">${state.profileEditOpen ? "Schließen" : "Bearbeiten"}</button>
      </div>
      ${state.profileEditOpen ? `
        <div id="profile-form" class="profile-form">
          ${profileFormHtml(clip.profile)}
          <p>
            <button id="btn-profile-save" class="primary">Speichern</button>
            <button id="btn-profile-reset"
                    title="Verwirft die Änderungen im Formular">Zurücksetzen</button>
            <span class="muted small">Speichern wirkt sich auf neue Kandidaten aus —
              bereits produzierte Clips dieses Profils bleiben unverändert.</span>
          </p>
        </div>` : ""}
    </div>`;
}

function candidateRow(clip, cand, index) {
  const encoded = encodeURIComponent(clip.key);
  // Nicht `clip.locked && ...`: auch ein noch unbestätigter Batch-Entwurf ist
  // bereits die Aufnahme, die die App gerade ausliefert — das Radio zeigt das,
  // unabhängig davon, ob der Seed schon per Lock geschützt ist.
  const isProduction = clip.seed === cand.seed;
  const classes = [cand.good ? "good" : "", cand.fresh === false ? "outdated" : "",
                    isProduction ? "production" : ""].join(" ").trim();
  const src = cand.isProductionOnly
    ? `/audio/${encoded}.wav`
    : `/candidates/${encoded}/${cand.seed}.wav`;
  return `
    <tr class="${classes}">
      <td class="center">
        <input type="radio" name="production" data-promote="${cand.seed}"
               ${isProduction ? "checked" : ""}
               title="Genau diese Aufnahme wird sofort die Produktions-Audio, der Seed wird festgelegt." />
      </td>
      <td><audio controls preload="metadata" src="${src}"
                 data-index="${index}" ${isProduction ? "data-current-production" : ""}></audio></td>
      <td class="nowrap">
        ${cand.isProductionOnly ? "" : `
        <button data-rate="${cand.seed}" class="icon ${cand.good ? "active" : ""}"
                title="${cand.good
                  ? "Bewertung zurücknehmen — der Seed verlässt auch den Seed-Pool des Profils"
                  : "Klingt gut — Bewertung wird gespeichert und der Seed in den Seed-Pool des Profils aufgenommen"}">👍</button>
        <button data-discard="${cand.seed}" class="icon"
                title="Klingt schlecht — Probeaufnahme löschen">👎</button>`}
      </td>
      <td class="mono nowrap">${cand.seed}</td>
      <td class="nowrap muted" title="Zeitpunkt der Erzeugung">${formatWhen(cand.createdAt)}</td>
      <td class="nowrap">${cand.speaker ? escapeHtml(cand.speaker) : '<span class="muted">—</span>'}</td>
      <td class="text-cell" title="${escapeHtml(cand.text || "")}">
        ${cand.text ? escapeHtml(cand.text) : '<span class="muted">—</span>'}
        ${cand.fresh === false
          ? '<span class="chip warn-chip" title="Mit älteren Einstellungen erzeugt — zum Vergleich mit einem neuen Versuch.">⚠️ alt</span>' : ""}
      </td>
    </tr>`;
}

function renderDetail(key) {
  const clip = state.clips.find((c) => c.key === key);
  if (!clip) return;
  const profile = state.profiles[clip.profile];
  const encoded = encodeURIComponent(clip.key);
  const max = state.limits.maxCandidates || 16;
  const poolSize = profile.seedPool.length;

  const spoken = clip.text !== clip.sourceText;
  const ownVoice = clip.speaker !== profile.speaker;

  el("detail").innerHTML = `
    ${profileSummaryCard(clip, profile)}

    <div class="card">
      <div class="clip-head">
        <span class="mono muted">${clip.key}</span>
        <span class="chip ${clip.status}">${STATUS_LABELS[clip.status] || clip.status}</span>
        ${clip.locked ? '<span class="chip locked">📌 festgelegt</span>' : ""}
      </div>

      <div class="pron">
        <div class="pron-block">
          <div class="pron-head"><span class="pron-icon">📖</span>Satz
            <span class="muted normal">— so steht er im Content-Pack der App</span></div>
          <div class="pron-source">${escapeHtml(clip.sourceText)}</div>
        </div>
        <div class="pron-join">${spoken
          ? "wird ausgesprochen als ↓"
          : "geht unverändert ans Modell ↓"}</div>
        <div class="pron-block ${spoken ? "changed" : ""}">
          <div class="pron-head"><span class="pron-icon">🔊</span>TTS-Version
            <span class="muted normal">— genau dieser Text geht ans Modell</span>
            ${spoken
              ? '<span class="chip changed">eigene Aussprache</span>'
              : '<span class="chip">identisch mit dem Satz</span>'}
          </div>
          <textarea id="tts-text" class="pron-input" rows="2"
                    spellcheck="false">${escapeHtml(clip.text)}</textarea>
          <p class="pron-actions">
            <button id="btn-save-text" class="primary"
                    title="Speichert diesen Text als Aussprache dieses Clips">
              Speichern</button>
            <button id="btn-reset-text" ${spoken ? "" : "disabled"}
                    title="Verwirft die eigene Aussprache — gesprochen wird wieder der Satz">
              Zurücksetzen</button>
          </p>
          <p class="muted small">Ändert nur den Klang: in der App steht weiter der Satz
            von oben. Speichern legt dabei den aktuellen Seed fest (Lock) — anhören und
            neue Aufnahmen lohnen sich über „🎲 Generate“.</p>
          <details class="help">
            <summary>Wie schreibt man eine Aussprache auf?</summary>
            <ul class="small">
              <li><b>Laut statt Buchstabenname</b> — „M“ wird gern als „Em“ gelesen.
                Ausgeschrieben als „Mmmmm“ kommt der Lautwert.</li>
              <li><b>Satzzeichen steuern die Melodie</b> — Punkt beruhigt, Fragezeichen
                hebt, Ausrufezeichen betont.</li>
              <li><b>Komma setzt eine Pause</b>, Bindestrich trennt Silben: „Ma-ma“.</li>
              <li><b>Alles ausschreiben</b>, was keine Buchstabenfolge ist: „5“ → „fünf“,
                „z. B.“ → „zum Beispiel“.</li>
              <li>Nichts davon ist garantiert — jede Änderung muss gehört werden.
                Nach dem Speichern „Generate“ drücken und vergleichen.</li>
            </ul>
          </details>
        </div>
      </div>

      <p class="muted">${clip.itemIds.length} Stelle(n) · Felder: ${clip.fields.join(", ")}
        ${clip.lessons.length ? " · Lektionen: " + clip.lessons.join(", ") : ""}</p>
      <p class="voice-line">Profil:
        <select id="clip-profile"
                title="Achtung: Profilwechsel legt den aktuellen Seed als Lock fest">
          ${Object.keys(state.profiles).sort().map((n) =>
            `<option value="${n}" ${n === clip.profile ? "selected" : ""}>${n}</option>`).join("")}
        </select>
        · Stimme
        <select id="clip-speaker"
                title="Stimme nur für diesen Clip — überschreibt die des Profils">
          ${voiceOptions(clip.speaker)}
        </select>
        ${ownVoice
          ? `<span class="chip changed" title="Das Profil „${escapeHtml(clip.profile)}“ ` +
            `spricht sonst mit ${escapeHtml(profile.speaker)}">nur für diesen Clip</span>`
          : ""}
        ${accentBadge(clip.speaker, profile.language)}
      </p>
      <p class="muted small">Seed <span class="mono">${clip.seed}</span>
        <span>(${seedOrigin(clip, profile)})</span>
        · Sprache ${escapeHtml(profile.language)} (aus dem Profil)</p>
    </div>

    <div class="card">
      <h3 style="margin-top:0">Aufnahmen
        <span class="muted normal">— Probeaufnahmen und aktuelle Produktion, neueste zuerst</span></h3>
      <p>
        <button id="btn-candidates" class="primary">🎲 Generate</button>
        <input id="cand-count" type="number" min="1" max="${max}"
               value="${candidateCount()}"
               title="Anzahl der Probeaufnahmen (1–${max})" /> Stück
        <label id="cand-known" class="inline"
               title="Zieht die Seeds zufällig aus dem Seed-Pool von „${escapeHtml(clip.profile)}“ ${
                 poolSize
                   ? `(${poolSize} gespeichert) statt neue zu erzeugen`
                   : "— der ist gerade leer, es werden also Zufalls-Seeds erzeugt"}">
          <input id="cand-known-seeds" type="checkbox" ${useKnownSeeds() ? "checked" : ""} />
          Use known seeds
          <span class="muted small">${poolSize
            ? `(${poolSize} im Pool)`
            : "(Pool leer — es kommen Zufalls-Seeds)"}</span>
        </label>
        <span id="cand-progress" class="muted small"></span>
      </p>
      <details class="help">
        <summary>Was bedeuten die Spalten?</summary>
        <ul class="small">
          <li><b>Produktion</b> — es kann nur eine geben: die Auswahl übernimmt genau
            diese Aufnahme sofort als Produktions-Audio und legt ihren Seed fest.
            Die Festlegung entfällt von selbst, sobald keine Aufnahme dieses Clips
            mehr übrig ist — dafür gibt es keinen eigenen Knopf.</li>
          <li><b>👍</b> — klingt gut: Bewertung wird gespeichert und der Seed automatisch
            in den Seed-Pool des Profils „${clip.profile}“ aufgenommen (Clips ohne Lock
            bekommen ihre Seeds aus diesem Pool).</li>
          <li><b>👎</b> — klingt schlecht: Probeaufnahme löschen (nimmt einen
            👍-Seed auch wieder aus dem Pool).</li>
          <li><b>Erzeugt / Stimme / Text</b> — womit die Aufnahme entstand.
            So bleiben mehrere Generate- und Batch-Läufe auseinanderhaltbar.</li>
        </ul>
      </details>
      ${clip.candidates.length === 0
        ? '<p class="muted">Noch keine Aufnahme. „🎲 Generate“ oder ' +
          '„▶ Batch-Lauf“ erzeugt welche.</p>'
        : `<div class="cand-scroll"><table class="cand-table">
            <thead><tr>
              <th title="Genau eine Aufnahme kann Produktion sein">Produktion</th>
              <th>Anhören</th>
              <th>Bewertung</th>
              <th>Seed</th>
              <th>Erzeugt</th>
              <th>Stimme</th>
              <th>Text</th>
            </tr></thead>
            <tbody>
              ${clip.candidates.map((cand, index) => candidateRow(clip, cand, index)).join("")}
            </tbody>
          </table></div>`}
    </div>`;

  // ---- Profil-Zusammenfassung (global)
  el("btn-profile-toggle").onclick = () => {
    state.profileEditOpen = !state.profileEditOpen;
    renderDetail(key);
  };
  const form = el("profile-form");
  if (form) {
    el("btn-profile-save").onclick = saveProfileFrom(form, clip.profile);
    el("btn-profile-reset").onclick = () => renderDetail(key);
    wirePoolLinks(form);
  }

  // ---- Clip (lokal)
  el("btn-candidates").onclick = guard(async () => {
    const count = Math.min(max, Math.max(1, Number(el("cand-count").value) || 4));
    writeLocal("ttsCandCount", count);
    const known = el("cand-known-seeds").checked;
    await post(`/api/clips/${encoded}/candidates`, { n: count, useKnownSeeds: known });
    el("cand-progress").textContent = state.jobs.running
      ? "eingereiht — wartet auf den laufenden Job …" : "eingereiht …";
  });
  el("cand-count").onchange = () => {
    writeLocal("ttsCandCount", Number(el("cand-count").value));
  };
  el("cand-known-seeds").onchange = (event) => {
    writeLocal("ttsUseKnownSeeds", event.target.checked);
  };
  el("clip-profile").onchange = guard(async (event) => {
    await post(`/api/clips/${encoded}/lock`,
               { seed: clip.seed, profile: event.target.value });
    await refresh();
    showBanner(`Profil gewechselt — Seed ${clip.seed} wurde dabei festgelegt (Lock).`, "info");
  });

  // Aussprache: der eigentliche Grund für die getrennte Darstellung oben.
  // Beides geht über denselben Lock-Endpunkt, der nur benannte Felder anfasst
  // — Stimme, Profil und Notiz dieses Clips bleiben also unberührt.
  el("btn-save-text").onclick = guard(async () => {
    const text = el("tts-text").value.trim();
    if (!text) {
      showBanner("Leerer Text ergibt leere Audio — zum Verwerfen „Zurücksetzen“ nehmen.", "warn");
      return;
    }
    if (text === clip.sourceText) {
      // Denselben Text als Override zu speichern, sähe in der Liste aus wie
      // eine Abweichung, wäre aber keine.
      await post(`/api/clips/${encoded}/lock`, { seed: clip.seed, textOverride: null });
      await refresh();
      showBanner("Text entspricht dem Satz — keine eigene Aussprache hinterlegt.", "info");
      return;
    }
    await post(`/api/clips/${encoded}/lock`, { seed: clip.seed, textOverride: text });
    await refresh();
    showBanner(`Aussprache gespeichert: „${text}“. Zum Anhören und Bestätigen ` +
      `„Generate“ drücken.`, "ok");
  });
  el("btn-reset-text").onclick = guard(async () => {
    await post(`/api/clips/${encoded}/lock`, { seed: clip.seed, textOverride: null });
    await refresh();
    showBanner(`Eigene Aussprache verworfen — gesprochen wird wieder „${clip.sourceText}“.`, "ok");
  });

  el("clip-speaker").onchange = guard(async (event) => {
    const speaker = event.target.value;
    await post(`/api/clips/${encoded}/lock`, { seed: clip.seed, speaker });
    await refresh();
    showBanner(`Stimme dieses Clips: ${speaker} (${voiceOf(speaker).origin}). ` +
      `Seed ${clip.seed} wurde dabei festgelegt (Lock).`, "ok");
  });

  // ---- Kandidaten-Tabelle
  el("detail").querySelectorAll("[data-rate]").forEach((button) => {
    button.onclick = guard(async () => {
      const seed = Number(button.dataset.rate);
      const cand = clip.candidates.find((c) => c.seed === seed);
      const good = !(cand && cand.good);
      await put(`/api/clips/${encoded}/candidates/${seed}/rating`, { good });
      await refresh();
      showBanner(good
        ? `Seed ${seed} als gut markiert und in den Seed-Pool von „${clip.profile}“ ` +
          `aufgenommen — Clips ohne Lock verteilen sich automatisch auf die Pool-Seeds.`
        : `Bewertung zurückgenommen — Seed ${seed} ist wieder aus dem Pool von ` +
          `„${clip.profile}“ entfernt.`, "ok");
    });
  });
  el("detail").querySelectorAll("[data-discard]").forEach((button) => {
    button.onclick = guard(async () => {
      await api(`/api/clips/${encoded}/candidates/${button.dataset.discard}`,
                { method: "DELETE" });
      await refresh();
    });
  });
  el("detail").querySelectorAll("[data-promote]").forEach((radio) => {
    radio.onchange = guard(async () => {
      const result = await post(`/api/clips/${encoded}/promote`,
                                { seed: Number(radio.dataset.promote) });
      await refresh();
      showBanner(result.verified
        ? "In Produktion übernommen — der Clip ist fertig."
        : "Übernommen und Seed festgelegt. Hinweis: die Aufnahme entstand mit " +
          "älteren Einstellungen und ließ sich nicht verifizieren.",
        result.verified ? "ok" : "info");
    });
  });
}

// --------------------------------------------------------- Parameter-Panel

function paramsCard(name) {
  const profile = state.profiles[name];
  return `
    <div class="card" data-profile="${name}">
      <h3 style="margin-top:0">${name} — ${escapeHtml(profile.label)}
        <span class="muted normal">— ${profileClipCount(name)} Clips</span></h3>
      ${profileFormHtml(name)}
      <p>
        <button data-save class="primary">Speichern</button>
        <button data-reset title="Verwirft die Änderungen in dieser Karte">Zurücksetzen</button>
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
      <p class="muted small">Jede Änderung an Stimme, Sprache, Instruktion, Sampling,
        Trim oder Normalisierung gilt für neue Kandidaten dieses Profils.
        Bereits produzierte Clips bleiben unverändert, bis man sie bewusst
        neu würfelt und übernimmt.</p>
      <p class="muted small">Hinter jeder Stimme steht ihre Herkunft. Die Sprache setzt
        die Phonologie, die Stimme bringt trotzdem den Akzent ihrer Kernsprache mit —
        bei ganzen Sätzen kaum hörbar, bei einem einzelnen Laut deutlich.</p>
    </div>
    ${Object.keys(state.profiles).sort().map(paramsCard).join("")}`;

  el("btn-params-close").onclick = () => {
    state.paramsOpen = false;
    renderParams();
  };

  panel.querySelectorAll("[data-profile]").forEach((card) => {
    const name = card.dataset.profile;
    card.querySelector("[data-save]").onclick = saveProfileFrom(card, name);
    card.querySelector("[data-reset]").onclick = () => renderParams();
    wirePoolLinks(card);
    card.querySelector("[data-save-all]").onclick = guard(async () => {
      const sampling = readProfileForm(card).sampling;
      for (const profileName of Object.keys(state.profiles)) {
        await put(`/api/profiles/${profileName}`, { sampling });
      }
      await refresh();
      showBanner("Sampling-Werte auf alle Profile übertragen — gilt ab sofort für neue Kandidaten.", "ok");
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
  if (name.startsWith("render:")) return `Batch-Lauf (${name.slice("render:".length)})`;
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
    const audio = el("detail").querySelector("audio[data-current-production]");
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
  el(id).addEventListener("input", () => {
    persistViewState();
    renderList();
  });
});

el("banner-close").onclick = () => {
  el("banner").className = "hidden";
};

el("btn-params").onclick = () => {
  state.paramsOpen = !state.paramsOpen;
  renderParams();
};

// Batch-Auswahl in der Liste
el("sel-visible").onclick = () => setSelection(visibleClips().map((c) => c.key));
el("sel-all").onclick = () => setSelection(state.clips.map((c) => c.key));
el("sel-none").onclick = () => setSelection([]);

el("batch-count").value = batchCount();
el("batch-count").onchange = () => {
  writeLocal("ttsBatchCount", Number(el("batch-count").value));
  el("batch-count").value = batchCount();
};

el("btn-render").onclick = guard(async () => {
  const keys = [...state.selectedKeys];
  if (keys.length === 0) return;
  const n = batchCount();
  if (!confirm(`Batch-Lauf für ${keys.length} ausgewählte Clips starten? ` +
               `Erzeugt wird nur, was noch fehlt — fertige Clips ` +
               `werden übersprungen. Pro Clip entstehen ${n} Kandidaten, die du ` +
               `danach in der Liste als Produktion bestätigst.`)) return;
  await post("/api/render", { keys, n });
});

el("btn-export").onclick = guard(async () => {
  const report = await api("/api/export", { method: "POST" });
  const parts = [`${report.exported.length} Clips in die App exportiert`];
  if (report.unchanged.length) parts.push(`${report.unchanged.length} unverändert`);
  if (report.removed.length) parts.push(`${report.removed.length} nicht mehr benötigte entfernt`);
  if (report.skipped.length) {
    parts.push(`${report.skipped.length} übersprungen: ` +
      report.skipped.map((s) => `${s.key} (${s.reason})`).join(", "));
  }
  parts.push(...report.warnings);
  showBanner(parts.join(" — "), report.skipped.length || report.warnings.length
    ? "warn" : "info");
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
