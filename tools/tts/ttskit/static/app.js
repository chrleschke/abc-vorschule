"use strict";

const state = {
  clips: [], profiles: {}, engine: {}, orphans: [], selected: null,
  jobs: { running: null, queued: 0 }, limits: { maxCandidates: 16 },
  voices: [], languages: [], paramsOpen: false,
  samplingSpec: [], secondsPerToken: 0.08,
  // Batch-Auswahl und aufgeklappter Profil-Editor überleben jedes refresh().
  selectedKeys: new Set(), profileEditOpen: false,
  // Laufende Detail-Aktion (👍, Produktion, 👎) — Buttons zeigen Pending/disabled.
  actionPending: null,
  // Kandidaten-Anzahl beim letzten Öffnen — Basis für ungelesene Badges in der Liste.
  lastAcknowledgedCount: {},
  // Erzeugung angefordert oder laufend (Generate, Batch-Lauf, Warteschlange).
  generatingKeys: new Set(),
  batchGeneratingKeys: new Set(),
};
const el = (id) => document.getElementById(id);

const STATUS_LABELS = { missing: "fehlt", rendered: "fertig" };

// Sprachen, in denen eine ostasiatische Stimme zu Hause ist. Nur außerhalb
// davon ist ihre Herkunft ein Hinweis wert.
const EAST_ASIAN = ["chinese", "japanese", "korean"];

// textContent → innerHTML ersetzt & < >, aber keine Anführungszeichen. Fast
// jeder Einsatzort hier ist ein Attribut (value="…", title="…"), und ohne die
// beiden letzten Ersetzungen bricht ein Wert mit " aus seinem Attribut aus,
// statt darin zu landen. Im Textfluss sind sie unschädlich.
function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML.replace(/"/g, "&quot;").replace(/'/g, "&#39;");
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
const useTopSeeds = () => readLocal("ttsUseTopSeeds", false) === true;

//: Nur für die Beschriftung. Die Auswahl selbst trifft der Server
//: (plan.TOP_SEED_LIMIT) — hier steht die Zahl, die dort steht.
const TOP_SEED_LIMIT = 10;

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
  ensureClipBaselines();
  if (state.selected) acknowledgeClip(state.selected);

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
    const generating = isClipGenerating(clip.key);
    const unseen = unseenCount(clip.key);
    row.innerHTML = `
      <input type="checkbox" class="sel" ${state.selectedKeys.has(clip.key) ? "checked" : ""}
             title="Für den Batch-Lauf auswählen" />
      <span class="chip">${clip.profile}</span>
      <span class="text">
        <span class="row-source">${escapeHtml(clip.sourceText)}</span>
        ${spoken ? `<span class="row-tts">🔊 ${escapeHtml(clip.text)}</span>` : ""}
      </span>
      ${ownVoice ? `<span class="chip changed" title="Eigene Stimme: ${escapeHtml(clip.speaker)}">🎙</span>` : ""}
      <span class="row-indicators">
        ${generating
          ? '<span class="spinner row-spinner" title="Erzeugung läuft …"></span>'
          : unseen > 0
            ? `<span class="badge-unseen" title="Neue Aufnahmen zum Anhören">${unseen}</span>`
            : ""}
      </span>
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
  acknowledgeClip(key);
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

// Die Erklärungstexte, Wertebereiche und Typen kommen aus der Registry im
// Server (store.SAMPLING_SPEC), nicht aus einer zweiten Liste hier. Vorher
// rendete das Panel aus Object.keys(profile.sampling) — ein Parameter, der
// in profiles.json noch nicht stand, war damit unsichtbar.
// Die Legende der Dauer-Gruppe sagt bewusst etwas anderes als das Feldlabel
// („Maximale Dauer") — sonst stünde dieselbe Wortgruppe zweimal übereinander.
const GROUP_LABELS = {
  duration: "Dauer — harter Schnitt (wie lang es werden darf)",
  talker: "Sampling — Haupt-Talker (was gesagt wird)",
  subtalker: "Feinstruktur — Sub-Talker (wie es klingt)",
};

const specFor = (key) => state.samplingSpec.find((p) => p.key === key);

const tokensToSeconds = (tokens) =>
  (Number(tokens) * state.secondsPerToken).toFixed(2);

// Zahlen im Fließtext des Panels werden deutsch geschrieben: die Hilfetexte
// aus der Registry tun das auch („0,3–0,6"), ein Dezimalpunkt daneben liest
// sich falsch. In den `value`/`min`/`max`/`step`-Attributen bleibt der Punkt —
// dort erwartet <input type="number"> ihn.
const germanNumber = (value) => String(value).replace(".", ",");

// Nachkommastellen für einen Registry-Wert: mindestens eine, sobald der
// Parameter überhaupt Kommazahlen erlaubt. JSON wirft die Null hinter 1.0 weg,
// sonst stünde da „Bereich 0,05–1." und „Voreinstellung 1." — beides sieht wie
// ein abgebrochener Satz aus.
function paramNumber(spec, value) {
  if (spec.integer) return germanNumber(value);
  const text = String(Number(value));
  const decimals = text.includes(".") ? text.split(".")[1].length : 0;
  return germanNumber(Number(value).toFixed(Math.max(1, decimals)));
}

const secondsLabel = (tokens) => germanNumber(tokensToSeconds(tokens));

// Ein Feld pro deklariertem Parameter. Die Dauer wird in Sekunden
// eingegeben und in Tokens gespeichert — data-unit markiert das für
// readProfileForm.
function paramFieldHtml(profile, spec) {
  const stored = profile.sampling[spec.key];
  const absent = stored === undefined || stored === null;
  // Fehlt der Schlüssel im Profil-Eintrag, steht die Voreinstellung im Feld —
  // nicht nichts. Das Panel rendert aus der Registry, damit ein neuer
  // Parameter auch an Profilen erscheint, die ihn noch nicht in profiles.json
  // haben; ein leeres Feld wäre aber unspeicherbar (readProfileForm verlangt
  // eine Zahl), womit sich an so einem Profil nicht einmal die Instruktion
  // ändern ließe. Der Hilfetext nennt die Voreinstellung ohnehin. Nur die
  // nullable Dauer bleibt leer: dort heißt leer „unbegrenzt".
  const value = absent && !spec.nullable ? spec.default : stored;
  const missing = value === undefined || value === null;
  const seconds = spec.group === "duration";
  const shown = missing ? "" : (seconds ? tokensToSeconds(value) : value);
  const attrs = seconds
    // Jeder legale Wert ist ein ganzzahliges Vielfaches von secondsPerToken.
    // Genau das ist die Schrittweite — mit step="0.1" liefen die Pfeile auf
    // 0,16 / 0,26 / 0,36 und jeder runde Sekundenwert war ein Step-Mismatch.
    ? `data-unit="seconds" step="${state.secondsPerToken}" ` +
      `min="${tokensToSeconds(spec.minimum)}" ` +
      `max="${tokensToSeconds(spec.maximum)}" placeholder="unbegrenzt"`
    : `step="${spec.step}" min="${spec.minimum}" max="${spec.maximum}"`;
  const range = seconds
    ? `${secondsLabel(spec.minimum)}–${secondsLabel(spec.maximum)} s`
    : `${paramNumber(spec, spec.minimum)}–${paramNumber(spec, spec.maximum)}`;
  const suffix = seconds
    ? `<span class="muted small" data-tokens-for="${escapeHtml(spec.key)}">${
        missing ? "unbegrenzt" : `= ${escapeHtml(String(value))} Tokens`}</span>`
    : "";
  // Label, Schlüssel und Wert laufen genau wie `help` durch escapeHtml.
  // Profile.from_dict prüft die Sampling-Werte inzwischen beim Laden, ein
  // handgeschriebener String kommt hier also nicht mehr an — aber
  // profiles.json ist eine dokumentierte Handbearbeitungs-Fläche, und
  // voiceOptions und profileFormHtml halten es für ihre Konfigurationsdaten
  // genauso.
  return `
    <div class="param-row">
      <label class="param">
        <span>${escapeHtml(spec.label)}${seconds ? " (Sekunden, vor Trim)" : ""}</span>
        <input type="number" data-param="${escapeHtml(spec.key)}" ${attrs}
               value="${escapeHtml(String(shown))}" />
        ${suffix}
      </label>
      <p class="param-help muted small">${escapeHtml(spec.help)}
        <b>Bereich ${range}.</b>${spec.default === null ? ""
          : ` Voreinstellung ${paramNumber(spec, spec.default)}.`}</p>
    </div>`;
}

function profileFormHtml(name) {
  const profile = state.profiles[name];
  const groups = ["duration", "talker", "subtalker"].map((group) => {
    const fields = state.samplingSpec.filter((p) => p.group === group);
    if (fields.length === 0) return "";
    return `
      <fieldset class="param-group">
        <legend>${GROUP_LABELS[group]}</legend>
        ${fields.map((spec) => paramFieldHtml(profile, spec)).join("")}
      </fieldset>`;
  }).join("");
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
    ${groups}
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
    const key = input.dataset.param;
    const spec = specFor(key);
    const raw = input.value.trim();
    if (raw === "") {
      // Number("") ist 0 — ein geleertes Feld darf nicht stillschweigend
      // als 0 gespeichert werden. Bei der Dauer heißt leer „unbegrenzt",
      // was der Server als null-Löschung entgegennimmt.
      if (!spec.nullable) {
        throw new Error(`„${spec.label}“ ist leer oder keine Zahl`);
      }
      sampling[key] = null;
      return;
    }
    const entered = Number(raw);
    if (Number.isNaN(entered)) {
      throw new Error(`„${spec.label}“ ist leer oder keine Zahl`);
    }
    // Die Dauer wird in Sekunden eingegeben, gespeichert werden Tokens.
    const value = input.dataset.unit === "seconds"
      ? Math.round(entered / state.secondsPerToken)
      : entered;
    if (value < spec.minimum || value > spec.maximum) {
      const shown = input.dataset.unit === "seconds"
        ? `${secondsLabel(spec.minimum)}–${secondsLabel(spec.maximum)} s`
        : `${paramNumber(spec, spec.minimum)}–${paramNumber(spec, spec.maximum)}`;
      throw new Error(`„${spec.label}“ muss im Bereich ${shown} liegen`);
    }
    sampling[key] = spec.integer ? Math.round(value) : value;
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

// Die Eingabe steht in Sekunden, gespeichert werden Tokens. Die Ableitung
// muss beim Tippen sichtbar sein, sonst überrascht der Rundungssprung:
// 2,05 s ergeben 26 Tokens und zeigen nach dem Speichern 2,08 s.
function wireDurationField(container) {
  container.querySelectorAll('[data-unit="seconds"]').forEach((input) => {
    const readout = container.querySelector(
      `[data-tokens-for="${input.dataset.param}"]`);
    if (!readout) return;
    const update = () => {
      const raw = input.value.trim();
      if (raw === "") {
        readout.textContent = "unbegrenzt";
        return;
      }
      const seconds = Number(raw);
      if (Number.isNaN(seconds)) {
        readout.textContent = "keine Zahl";
        return;
      }
      const tokens = Math.round(seconds / state.secondsPerToken);
      readout.textContent = `= ${tokens} Tokens (${secondsLabel(tokens)} s)`;
    };
    input.oninput = update;
    update();
  });
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

function isActionPending(type, clipKey, seed) {
  const pending = state.actionPending;
  if (!pending || pending.clipKey !== clipKey) return false;
  if (type && pending.type !== type) return false;
  if (seed !== undefined && pending.seed !== seed) return false;
  return true;
}

function globalCandidateActionBusy(clipKey) {
  const pending = state.actionPending;
  if (!pending || pending.clipKey !== clipKey) return false;
  return ["deleteAll", "clearProduction", "promote", "generate"].includes(pending.type);
}

function candidateRowBusy(clipKey, seed) {
  if (globalCandidateActionBusy(clipKey)) return true;
  return isActionPending("rate", clipKey, seed) || isActionPending("discard", clipKey, seed);
}

function candidateDiscardDisabled(clip, cand) {
  return candidateRowBusy(clip.key, cand.seed)
    || (clip.status === "rendered" && clip.seed === cand.seed);
}

async function refreshAfterDetailAction() {
  state.actionPending = null;
  await refresh();
}

function candidateJobRunning(clipKey) {
  return state.jobs.running === `candidates:${clipKey}`;
}

function ensureClipBaselines() {
  state.clips.forEach((clip) => {
    if (state.lastAcknowledgedCount[clip.key] === undefined) {
      state.lastAcknowledgedCount[clip.key] = clip.candidates.length;
    }
  });
}

function acknowledgeClip(key) {
  const clip = state.clips.find((c) => c.key === key);
  if (clip) state.lastAcknowledgedCount[key] = clip.candidates.length;
}

function unseenCount(key) {
  if (key === state.selected) return 0;
  const clip = state.clips.find((c) => c.key === key);
  if (!clip) return 0;
  const ack = state.lastAcknowledgedCount[key];
  if (ack === undefined) return 0;
  return Math.max(0, clip.candidates.length - ack);
}

function isClipGenerating(key) {
  if (candidateJobRunning(key)) return true;
  if (isActionPending("generate", key)) return true;
  if (state.generatingKeys.has(key)) return true;
  if (state.batchGeneratingKeys.has(key)) return true;
  return false;
}

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
  // Ein gelöschtes Feld kommt von der API als fehlender Schlüssel (undefined),
  // aus einer handbearbeiteten profiles.json aber als null — beides heißt
  // „unbegrenzt".
  const maxDuration = profile.sampling.max_new_tokens === undefined
      || profile.sampling.max_new_tokens === null
    ? "unbegrenzt"
    : secondsLabel(profile.sampling.max_new_tokens) + " s";
  // Auch hier deutsch, sonst stünden „0.6" und „2,00 s" in derselben Zeile.
  // Fehlt der Schlüssel im Profil-Eintrag, steht ein Strich da statt
  // „undefined" — die Voreinstellung greift dann im Modell.
  const temperature = profile.sampling.temperature === undefined
      || profile.sampling.temperature === null
    ? "—"
    : germanNumber(profile.sampling.temperature);
  return `
    <div class="card profile-card">
      <div class="profile-summary">
        <div class="summary-text">
          <b>⚙️ Profil „${clip.profile}“ — ${escapeHtml(profile.label)}</b>
          <span class="muted">gilt für alle ${profileClipCount(clip.profile)} Clips dieses Profils</span>
          <div class="muted small">
            Stimme ${escapeHtml(profile.speaker)} · Sprache ${escapeHtml(profile.language)}
            · temperature ${temperature}
            · max ${maxDuration}
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
  const isProduction = clip.status === "rendered" && clip.seed === cand.seed;
  const pendingRate = isActionPending("rate", clip.key, cand.seed);
  const pendingPromote = isActionPending("promote", clip.key, cand.seed);
  const pendingDiscard = isActionPending("discard", clip.key, cand.seed);
  const rowBusy = candidateRowBusy(clip.key, cand.seed);
  const discardDisabled = candidateDiscardDisabled(clip, cand);
  const promoteBusy = globalCandidateActionBusy(clip.key)
    || isActionPending("promote", clip.key);
  const classes = [cand.good ? "good" : "", cand.fresh === false ? "outdated" : "",
                    isProduction ? "production" : "",
                    pendingPromote ? "pending-promote" : ""].join(" ").trim();
  const src = cand.isProductionOnly
    ? `/audio/${encoded}.wav`
    : `/candidates/${encoded}/${cand.seed}.wav`;
  const rateLabel = pendingRate ? "…" : (cand.good ? "✓" : "👍");
  const discardLabel = pendingDiscard ? "…" : "👎";
  return `
    <tr class="${classes}" data-cand-seed="${cand.seed}">
      <td class="center production-cell">
        <label class="production-pick ${pendingPromote ? "pending" : ""}"
               title="${isProduction
                 ? "Diese Aufnahme ist die Produktion"
                 : "Genau diese Aufnahme wird sofort die Produktions-Audio, der Seed wird festgelegt."}">
          <input type="radio" name="production" data-promote="${cand.seed}"
                 ${isProduction ? "checked" : ""}
                 ${promoteBusy ? "disabled" : ""} />
          <span class="production-label">${isProduction ? "✓ fertig" : "wählen"}</span>
        </label>
      </td>
      <td class="cand-audio-cell"><audio controls preload="metadata" src="${src}"
                 data-index="${index}" ${isProduction ? "data-current-production" : ""}></audio></td>
      <td class="nowrap">
        ${cand.isProductionOnly ? "" : `
        <button data-rate="${cand.seed}"
                class="icon ${cand.good ? "active" : ""} ${pendingRate ? "pending" : ""}"
                ${rowBusy ? "disabled" : ""}
                aria-pressed="${cand.good ? "true" : "false"}"
                title="${cand.good
                  ? "Bewertung zurücknehmen — der Seed verlässt auch den Seed-Pool des Profils"
                  : "Klingt gut — Bewertung wird gespeichert und der Seed in den Seed-Pool des Profils aufgenommen"}">${rateLabel}</button>
        <button data-discard="${cand.seed}"
                class="icon ${pendingDiscard ? "pending" : ""}"
                ${discardDisabled ? "disabled" : ""}
                title="${clip.status === "rendered" && clip.seed === cand.seed
                  ? "Produktion kann nicht gelöscht werden — „Keine Produktion“ nutzen"
                  : "Klingt schlecht — Probeaufnahme löschen"}">${discardLabel}</button>`}
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

function candidatesTableHtml(clip) {
  if (clip.candidates.length === 0) {
    return `<p id="candidates-empty" class="muted">Noch keine Aufnahme. „🎲 Generate“ oder ` +
      `„▶ Batch-Lauf“ erzeugt welche.</p>`;
  }
  return `<div class="cand-scroll"><table class="cand-table">
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
    </table></div>`;
}

function deletableCandidates(clip) {
  return clip.candidates.filter((c) =>
    !c.isProductionOnly
    && !(clip.status === "rendered" && c.seed === clip.seed)
    && !c.good);
}

function hasProduction(clip) {
  return clip.status === "rendered" || clip.locked;
}

function detailTitleHtml(clip) {
  return `<h2 class="detail-title">${escapeHtml(clip.sourceText)}</h2>`;
}

function candidatesCardHtml(clip, profile, max, poolSize, topSize) {
  const ownVoice = clip.speaker !== profile.speaker;
  const genRunning = candidateJobRunning(clip.key);
  const genQueued = isActionPending("generate", clip.key);
  const generating = isClipGenerating(clip.key);
  const deletableCount = deletableCandidates(clip).length;
  const deleteBusy = globalCandidateActionBusy(clip.key)
    || isActionPending("deleteAll", clip.key);
  const fixedSeedActive = clip.generateSeed != null;
  return `
    <div class="card card-primary" id="candidates-card">
      <h3 class="card-title">Aufnahmen erzeugen &amp; bestätigen</h3>
      <div class="clip-head compact">
        <span class="mono muted">${clip.key}</span>
        <span class="chip ${clip.status}">${STATUS_LABELS[clip.status] || clip.status}</span>
        ${clip.locked ? '<span class="chip locked">📌 festgelegt</span>' : ""}
      </div>
      <textarea id="tts-text" class="tts-text-input" rows="2" spellcheck="false"
                title="Text, der ans Modell geht — wird automatisch gespeichert">${escapeHtml(clip.text)}</textarea>
      <span id="tts-text-status" class="tts-text-status muted small" aria-live="polite"></span>
      <p class="voice-line">
        Profil:
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
      <div class="generate-row">
        <button id="btn-candidates" class="primary ${generating ? "pending" : ""}"
                ${generating ? "disabled" : ""}>
          ${genRunning ? "⏳ Erzeuge …" : generating ? "⏳ Starte …" : "🎲 Generate"}</button>
        <input id="cand-count" type="number" min="1" max="${max}"
               value="${candidateCount()}"
               ${generating || fixedSeedActive ? "disabled" : ""}
               title="Anzahl der Probeaufnahmen (1–${max})" /> Stück
        <label class="inline fixed-seed-label"
               title="Nur dieser Seed beim Erzeugen — leer = wie bisher">
          Fester Seed
          <input id="cand-fixed-seed" type="number" min="0" max="${MAX_RANDOM_SEED}"
                 value="${clip.generateSeed ?? ""}"
                 placeholder="Zufall"
                 ${generating ? "disabled" : ""} />
        </label>
        <span id="cand-fixed-seed-status" class="fixed-seed-status muted small"
              aria-live="polite"></span>
        <label id="cand-known" class="inline"
               title="Zieht die Seeds zufällig aus dem Seed-Pool von „${escapeHtml(clip.profile)}“ ${
                 poolSize
                   ? `(${poolSize} gespeichert) statt neue zu erzeugen`
                   : "— der ist gerade leer, es werden also Zufalls-Seeds erzeugt"}">
          <input id="cand-known-seeds" type="checkbox" ${useKnownSeeds() ? "checked" : ""}
                 ${generating || fixedSeedActive ? "disabled" : ""} />
          Use known seeds
          <span class="muted small">${poolSize
            ? `(${poolSize} im Pool)`
            : "(Pool leer — es kommen Zufalls-Seeds)"}</span>
        </label>
        <label id="cand-top" class="inline"
               title="Zieht die Seeds zufällig aus den bestätigten Top-Seeds von „${escapeHtml(clip.profile)}“ ${
                 topSize
                   ? `(${topSize} Seeds auf den besten ${TOP_SEED_LIMIT} Rängen, `
                     + "Punktgleiche zählen mit) — schlägt „Use known seeds“"
                   : "— es gibt noch keine Locks für dieses Profil, es werden also Zufalls-Seeds erzeugt"}">
          <input id="cand-top-seeds" type="checkbox" ${useTopSeeds() ? "checked" : ""}
                 ${generating || fixedSeedActive ? "disabled" : ""} />
          Use top seeds
          <span class="muted small">${topSize
            ? `(${topSize} Top-Seeds)`
            : "(keine Locks — es kommen Zufalls-Seeds)"}</span>
        </label>
        <span id="cand-progress" class="muted small"></span>
      </div>
      <details class="help">
        <summary>Was bedeuten die Spalten?</summary>
        <ul class="small">
          <li><b>Produktion</b> — es kann nur eine geben: die Auswahl übernimmt genau
            diese Aufnahme sofort als Produktions-Audio und legt ihren Seed fest.</li>
          <li><b>👍 / ✓</b> — klingt gut: Bewertung wird gespeichert und der Seed automatisch
            in den Seed-Pool des Profils „${clip.profile}“ aufgenommen.</li>
          <li><b>👎</b> — klingt schlecht: Probeaufnahme löschen (nimmt einen
            👍-Seed auch wieder aus dem Pool).</li>
          <li><b>Alle löschen</b> — entfernt alle Probeaufnahmen ohne 👍 und ohne
            Produktion; bewertete und bestätigte Aufnahmen bleiben.</li>
          <li><b>Keine Produktion</b> — hebt die bestätigte Aufnahme auf; Kandidaten
            und 👍 bleiben erhalten.</li>
          <li><b>Erzeugt / Stimme / Text</b> — womit die Aufnahme entstand.</li>
        </ul>
      </details>
      <div class="candidates-toolbar">
        <button id="btn-delete-all-candidates"
                class="${isActionPending("deleteAll", clip.key) ? "pending" : ""}"
                ${deletableCount === 0 || deleteBusy ? "disabled" : ""}
                title="Löscht alle Probeaufnahmen ohne 👍 und ohne Produktion">
          ${isActionPending("deleteAll", clip.key)
            ? "⏳ Lösche …"
            : deletableCount
              ? `Alle löschen (${deletableCount})`
              : "Alle löschen"}</button>
      </div>
      <div id="candidates-body">${candidatesTableHtml(clip)}</div>
      ${hasProduction(clip) ? `
      <div class="candidates-footer">
        <button id="btn-clear-production"
                class="${isActionPending("clearProduction", clip.key) ? "pending" : ""}"
                ${globalCandidateActionBusy(clip.key) ? "disabled" : ""}
                title="Hebt die Produktions-Auswahl auf — Kandidaten und 👍 bleiben">
          ${isActionPending("clearProduction", clip.key)
            ? "⏳ Hebe auf …"
            : "Keine Produktion"}</button>
      </div>` : ""}
    </div>`;
}

function syncCandidatesBody(clip) {
  const body = el("candidates-body");
  if (!body) return;
  body.innerHTML = candidatesTableHtml(clip);
  wireCandidateHandlers(clip);
}

function wireCandidateHandlers(clip) {
  const encoded = encodeURIComponent(clip.key);
  el("detail").querySelectorAll("[data-rate]").forEach((button) => {
    button.onclick = guard(async () => {
      if (globalCandidateActionBusy(clip.key)) return;
      const seed = Number(button.dataset.rate);
      const cand = clip.candidates.find((c) => c.seed === seed);
      const good = !(cand && cand.good);
      state.actionPending = { type: "rate", clipKey: clip.key, seed };
      if (cand) cand.good = good;
      syncCandidatesBody(clip);
      try {
        await put(`/api/clips/${encoded}/candidates/${seed}/rating`, { good });
        await refreshAfterDetailAction();
        showBanner(good
          ? `Seed ${seed} als gut markiert und in den Seed-Pool von „${clip.profile}“ ` +
            `aufgenommen.`
          : `Bewertung zurückgenommen — Seed ${seed} ist wieder aus dem Pool von ` +
            `„${clip.profile}“ entfernt.`, "ok");
      } catch (error) {
        await refreshAfterDetailAction();
        throw error;
      }
    });
  });
  el("detail").querySelectorAll("[data-discard]").forEach((button) => {
    button.onclick = guard(async () => {
      if (globalCandidateActionBusy(clip.key)) return;
      const seed = Number(button.dataset.discard);
      if (clip.status === "rendered" && clip.seed === seed) return;
      state.actionPending = { type: "discard", clipKey: clip.key, seed };
      syncCandidatesBody(clip);
      try {
        await api(`/api/clips/${encoded}/candidates/${seed}`, { method: "DELETE" });
        await refreshAfterDetailAction();
        showBanner(`Probeaufnahme Seed ${seed} gelöscht.`, "ok");
      } catch (error) {
        await refreshAfterDetailAction();
        throw error;
      }
    });
  });
  el("detail").querySelectorAll("[data-promote]").forEach((radio) => {
    radio.onclick = guard(async (event) => {
      if (globalCandidateActionBusy(clip.key)) {
        event.preventDefault();
        return;
      }
      const seed = Number(radio.dataset.promote);
      if (clip.seed === seed) {
        showBanner("Diese Aufnahme ist bereits die Produktion.", "info");
        return;
      }
      state.actionPending = { type: "promote", clipKey: clip.key, seed };
      syncCandidatesBody(clip);
      try {
        const result = await post(`/api/clips/${encoded}/promote`, { seed });
        await refreshAfterDetailAction();
        showBanner(result.verified
          ? "In Produktion übernommen — der Clip ist fertig."
          : "Übernommen und Seed festgelegt. Hinweis: die Aufnahme entstand mit " +
            "älteren Einstellungen und ließ sich nicht verifizieren.",
          result.verified ? "ok" : "info");
      } catch (error) {
        await refreshAfterDetailAction();
        throw error;
      }
    });
  });
}

function wireDeleteAllCandidates(clip) {
  const button = el("btn-delete-all-candidates");
  if (!button) return;
  const encoded = encodeURIComponent(clip.key);
  button.onclick = guard(async () => {
    if (globalCandidateActionBusy(clip.key)) return;
    const deletable = deletableCandidates(clip);
    if (deletable.length === 0) {
      showBanner("Nichts löschbar — alle Aufnahmen sind bewertet oder Produktion.", "info");
      return;
    }
    if (deletable.length > 1 &&
        !confirm(`${deletable.length} löschbare Probeaufnahmen entfernen? ` +
                 "Bewertete und Produktion bleiben erhalten.")) {
      return;
    }
    state.actionPending = { type: "deleteAll", clipKey: clip.key };
    try {
      const result = await api(`/api/clips/${encoded}/candidates`, { method: "DELETE" });
      await refreshAfterDetailAction();
      if (result.deleted === 0) {
        showBanner("Nichts gelöscht — alle Aufnahmen sind geschützt.", "info");
      } else {
        const skipped = result.skipped
          ? `, ${result.skipped} geschützt belassen`
          : "";
        showBanner(`${result.deleted} Probeaufnahme(n) gelöscht${skipped}.`, "ok");
      }
    } catch (error) {
      await refreshAfterDetailAction();
      throw error;
    }
  });
}

function wireClearProduction(clip) {
  const button = el("btn-clear-production");
  if (!button) return;
  const encoded = encodeURIComponent(clip.key);
  button.onclick = guard(async () => {
    if (globalCandidateActionBusy(clip.key)) return;
    if (!confirm("Produktion aufheben? Kandidaten und 👍-Bewertungen bleiben erhalten.")) {
      return;
    }
    state.actionPending = { type: "clearProduction", clipKey: clip.key };
    renderDetail(clip.key);
    try {
      await post(`/api/clips/${encoded}/clear-production`, {});
      await refreshAfterDetailAction();
      showBanner("Produktion aufgehoben — keine Aufnahme ist mehr bestätigt.", "ok");
    } catch (error) {
      await refreshAfterDetailAction();
      throw error;
    }
  });
}

const TEXT_SAVE_DELAY_MS = 600;
const textSaveTimers = new Map();
const fixedSeedSaveTimers = new Map();
const MAX_RANDOM_SEED = 2147483647;

function parseFixedSeedInput(input) {
  const raw = input?.value.trim();
  if (!raw) return { value: null, invalid: false };
  const n = Number(raw);
  if (!Number.isInteger(n) || n < 0 || n > MAX_RANDOM_SEED) {
    return { value: null, invalid: true };
  }
  return { value: n, invalid: false };
}

function setGenerateOptionsDisabled(disabled) {
  const count = el("cand-count");
  const known = el("cand-known-seeds");
  const top = el("cand-top-seeds");
  if (count) count.disabled = disabled;
  if (known) known.disabled = disabled;
  if (top) top.disabled = disabled;
}

function wireTtsTextAutosave(clip) {
  const input = el("tts-text");
  const status = el("tts-text-status");
  if (!input) return;
  const encoded = encodeURIComponent(clip.key);
  let lastSaved = clip.text;

  const setStatus = (message) => {
    if (status) status.textContent = message;
  };

  const persist = guard(async () => {
    const text = input.value.trim();
    if (text === lastSaved) return;
    if (!text) {
      setStatus("Leer — nicht gespeichert");
      return;
    }
    setStatus("Speichere …");
    const body = text === clip.sourceText
      ? { seed: clip.seed, textOverride: null }
      : { seed: clip.seed, textOverride: text };
    await post(`/api/clips/${encoded}/lock`, body);
    lastSaved = text;
    await refresh();
    setStatus("Gespeichert");
    window.setTimeout(() => {
      if (status && status.textContent === "Gespeichert") setStatus("");
    }, 1500);
  });

  const scheduleSave = () => {
    setStatus("");
    const pending = textSaveTimers.get(clip.key);
    if (pending) window.clearTimeout(pending);
    textSaveTimers.set(clip.key, window.setTimeout(() => {
      textSaveTimers.delete(clip.key);
      persist();
    }, TEXT_SAVE_DELAY_MS));
  };

  input.oninput = scheduleSave;
  if (input.value.trim() !== lastSaved.trim()) scheduleSave();
}

function wireFixedSeedAutosave(clip) {
  const input = el("cand-fixed-seed");
  const status = el("cand-fixed-seed-status");
  if (!input) return;
  const encoded = encodeURIComponent(clip.key);
  let lastSaved = clip.generateSeed ?? null;

  const setStatus = (message) => {
    if (status) status.textContent = message;
  };

  const updateGenerateOptions = () => {
    const parsed = parseFixedSeedInput(input);
    setGenerateOptionsDisabled(parsed.value != null && !parsed.invalid);
  };

  const persist = guard(async () => {
    const parsed = parseFixedSeedInput(input);
    if (parsed.invalid) {
      setStatus(`Ungültig — 0 bis ${MAX_RANDOM_SEED}`);
      return;
    }
    if (parsed.value === lastSaved) return;
    setStatus("Speichere …");
    await post(`/api/clips/${encoded}/lock`, {
      seed: clip.seed,
      generateSeed: parsed.value,
    });
    lastSaved = parsed.value;
    await refresh();
    setStatus("Gespeichert");
    window.setTimeout(() => {
      if (status && status.textContent === "Gespeichert") setStatus("");
    }, 1500);
  });

  const scheduleSave = () => {
    updateGenerateOptions();
    const parsed = parseFixedSeedInput(input);
    if (parsed.invalid) {
      setStatus(`Ungültig — 0 bis ${MAX_RANDOM_SEED}`);
      return;
    }
    setStatus("");
    const pending = fixedSeedSaveTimers.get(clip.key);
    if (pending) window.clearTimeout(pending);
    fixedSeedSaveTimers.set(clip.key, window.setTimeout(() => {
      fixedSeedSaveTimers.delete(clip.key);
      persist();
    }, TEXT_SAVE_DELAY_MS));
  };

  input.oninput = scheduleSave;
  updateGenerateOptions();
  const initial = parseFixedSeedInput(input).value;
  if (initial !== lastSaved) scheduleSave();
}

function renderDetail(key) {
  const clip = state.clips.find((c) => c.key === key);
  if (!clip) return;
  const profile = state.profiles[clip.profile];
  const encoded = encodeURIComponent(clip.key);
  const max = state.limits.maxCandidates || 16;
  const poolSize = profile.seedPool.length;
  const topSize = (state.topSeeds?.[clip.profile] || []).length;

  el("detail").innerHTML = `
    ${detailTitleHtml(clip)}
    ${candidatesCardHtml(clip, profile, max, poolSize, topSize)}
    ${profileSummaryCard(clip, profile)}`;

  // ---- Profil-Zusammenfassung (unten, sekundär)
  el("btn-profile-toggle").onclick = () => {
    state.profileEditOpen = !state.profileEditOpen;
    renderDetail(key);
  };
  const form = el("profile-form");
  if (form) {
    el("btn-profile-save").onclick = saveProfileFrom(form, clip.profile);
    el("btn-profile-reset").onclick = () => renderDetail(key);
    wirePoolLinks(form);
    wireDurationField(form);
  }

  // ---- Generate
  const btnGenerate = el("btn-candidates");
  btnGenerate.onclick = guard(async () => {
    if (candidateJobRunning(clip.key)) return;
    const fixedInput = el("cand-fixed-seed");
    const fixedParsed = parseFixedSeedInput(fixedInput);
    if (fixedParsed.invalid) {
      const seedStatus = el("cand-fixed-seed-status");
      if (seedStatus) {
        seedStatus.textContent = `Ungültig — 0 bis ${MAX_RANDOM_SEED}`;
      }
      return;
    }
    const count = Math.min(max, Math.max(1, Number(el("cand-count").value) || 4));
    writeLocal("ttsCandCount", count);
    const known = el("cand-known-seeds").checked;
    const top = el("cand-top-seeds").checked;
    const body = { n: count, useKnownSeeds: known, useTopSeeds: top };
    if (fixedParsed.value != null) body.fixedSeed = fixedParsed.value;
    state.actionPending = { type: "generate", clipKey: clip.key };
    state.generatingKeys.add(clip.key);
    renderList();
    btnGenerate.disabled = true;
    btnGenerate.classList.add("pending");
    btnGenerate.textContent = "⏳ Starte …";
    try {
      await post(`/api/clips/${encoded}/candidates`, body);
      el("cand-progress").textContent = state.jobs.running
        ? "eingereiht — wartet auf den laufenden Job …" : "eingereiht …";
    } finally {
      state.actionPending = null;
      if (state.selected === clip.key) renderDetail(clip.key);
    }
  });
  el("cand-count").onchange = () => {
    writeLocal("ttsCandCount", Number(el("cand-count").value));
  };
  el("cand-known-seeds").onchange = (event) => {
    writeLocal("ttsUseKnownSeeds", event.target.checked);
    if (event.target.checked) {
      el("cand-top-seeds").checked = false;
      writeLocal("ttsUseTopSeeds", false);
    }
  };
  el("cand-top-seeds").onchange = (event) => {
    writeLocal("ttsUseTopSeeds", event.target.checked);
    if (event.target.checked) {
      el("cand-known-seeds").checked = false;
      writeLocal("ttsUseKnownSeeds", false);
    }
  };
  el("clip-profile").onchange = guard(async (event) => {
    await post(`/api/clips/${encoded}/lock`,
               { seed: clip.seed, profile: event.target.value });
    await refresh();
    showBanner(`Profil gewechselt — Seed ${clip.seed} wurde dabei festgelegt (Lock).`, "info");
  });

  wireTtsTextAutosave(clip);
  wireFixedSeedAutosave(clip);

  el("clip-speaker").onchange = guard(async (event) => {
    const speaker = event.target.value;
    await post(`/api/clips/${encoded}/lock`, { seed: clip.seed, speaker });
    await refresh();
    const origin = voiceOf(speaker)?.origin || "unbekannt";
    showBanner(`Stimme dieses Clips: ${speaker} (${origin}). ` +
      `Seed ${clip.seed} wurde dabei festgelegt (Lock).`, "ok");
  });

  wireCandidateHandlers(clip);
  wireDeleteAllCandidates(clip);
  wireClearProduction(clip);

  // Generate-Button während laufendem Job aktuell halten (SSE triggert refresh).
  if (candidateJobRunning(clip.key)) {
    const progress = el("cand-progress");
    if (progress && !progress.textContent) {
      progress.textContent = "Erzeuge Probeaufnahmen …";
    }
  }
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
                title="Nur die Sampling-Werte dieser Karte auf alle Profile übertragen — die maximale Dauer bleibt ausgenommen, die gilt pro Profil">
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
      <p class="muted small">Die maximale Dauer ist ein harter Schnitt, kein
        Hinweis: läuft das Modell in einen erfundenen Satz, bricht die Aufnahme
        mitten drin ab — hörbar kaputt statt unauffällig falsch. Der Wert gilt
        für die Rohgenerierung, also bevor die Stille am Anfang und Ende
        weggeschnitten wird; die fertige Datei ist entsprechend kürzer.
        Intern zählt das Modell in Tokens à 80 ms.</p>
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
    wireDurationField(card);
    card.querySelector("[data-save-all]").onclick = guard(async () => {
      // Ohne das Dauer-Limit: es ist bewusst pro Profil verschieden (phoneme
      // 2,00 s, prompt 10,00 s), und es mitzuübertragen würde genau den Sinn
      // des Limits aufheben — ein leeres Feld löschte es sogar überall. Das
      // „Speichern" dieser Karte speichert die Dauer weiterhin mit.
      const sampling = Object.fromEntries(
        Object.entries(readProfileForm(card).sampling).filter(([key]) => {
          const spec = specFor(key);
          return !spec || spec.group !== "duration";
        }));
      for (const profileName of Object.keys(state.profiles)) {
        await put(`/api/profiles/${profileName}`, { sampling });
      }
      await refresh();
      showBanner("Sampling-Werte auf alle Profile übertragen (ohne die maximale " +
        "Dauer) — gilt ab sofort für neue Kandidaten.", "ok");
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
  keys.forEach((k) => state.batchGeneratingKeys.add(k));
  renderList();
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
    if (event.job?.startsWith("candidates:")) {
      state.generatingKeys.delete(event.job.slice("candidates:".length));
    }
    if (event.job?.startsWith("render:")) {
      state.batchGeneratingKeys.clear();
    }
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
    if (event.job?.startsWith("candidates:")) {
      state.generatingKeys.delete(event.job.slice("candidates:".length));
    }
    if (event.job?.startsWith("render:")) {
      state.batchGeneratingKeys.clear();
    }
    setJobIdle("");
    showBanner(event.message, "warn");
    refresh().catch(showError);
  } else if (event.type === "job-start") {
    lastSummary = null;
    if (event.job?.startsWith("candidates:")) {
      state.generatingKeys.add(event.job.slice("candidates:".length));
    }
    setJobRunning(event.job);
    renderList();
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
