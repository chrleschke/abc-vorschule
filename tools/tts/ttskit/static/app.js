"use strict";

const state = { clips: [], profiles: {}, engine: {}, orphans: [], selected: null };
const el = (id) => document.getElementById(id);

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
    el("progress").innerHTML =
      `<span class="warn">Engine offline: ${state.engine.error || "unbekannt"}</span>`;
  }
  renderList();
  if (state.selected) renderDetail(state.selected);
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
    row.dataset.key = clip.key;
    row.innerHTML = `
      <span class="chip">${clip.profile}</span>
      <span class="text">${escapeHtml(clip.text)}</span>
      ${clip.locked ? '<span class="chip locked">📌</span>' : ""}
      <span class="chip ${clip.status}">${clip.status}</span>`;
    row.onclick = () => select(clip.key);
    list.appendChild(row);
  });
  document.title = `Qwen-TTS (${clips.length})`;
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text;
  return div.innerHTML;
}

function select(key) {
  state.selected = key;
  renderList();
  renderDetail(key);
  const active = document.querySelector(".row.active");
  if (active) active.scrollIntoView({ block: "nearest" });
}

function renderDetail(key) {
  const clip = state.clips.find((c) => c.key === key);
  if (!clip) return;
  const profile = state.profiles[clip.profile];
  const encoded = encodeURIComponent(clip.key);

  el("detail").innerHTML = `
    <div class="card">
      <div class="mono muted">${clip.key}</div>
      <h2 style="margin:6px 0">${escapeHtml(clip.text)}</h2>
      ${clip.text !== clip.sourceText
        ? `<p class="muted">Original: ${escapeHtml(clip.sourceText)}</p>` : ""}
      <p class="muted">${clip.itemIds.length} Stelle(n) · Felder: ${clip.fields.join(", ")}
        ${clip.lessons.length ? " · Lektionen: " + clip.lessons.join(", ") : ""}</p>
      <p>Profil:
        <select id="clip-profile">
          ${Object.keys(state.profiles).sort().map((n) =>
            `<option value="${n}" ${n === clip.profile ? "selected" : ""}>${n}</option>`).join("")}
        </select>
        · Seed <span class="mono">${clip.seed}</span>
        ${clip.locked ? '<span class="chip locked">gelockt</span>' : ""}
      </p>
      ${clip.status === "rendered"
        ? `<audio controls src="/audio/${encoded}.wav" id="main-audio"></audio>`
        : '<p class="muted">Noch nicht gerendert.</p>'}
      <p>
        <button id="btn-candidates" class="primary">🎲 4 Kandidaten</button>
        ${clip.locked ? '<button id="btn-unlock">Lock entfernen</button>' : ""}
      </p>
    </div>

    <div class="card">
      <h3 style="margin-top:0">Kandidaten</h3>
      <div class="candidates">
        ${clip.candidates.length === 0 ? '<p class="muted">Noch keine.</p>' : ""}
        ${clip.candidates.map((seed, index) => `
          <div class="candidate">
            <h4>${index < 9 ? `[${index + 1}] ` : ""}Seed ${seed}</h4>
            <audio controls src="/candidates/${encoded}/${seed}.wav"
                   data-index="${index}"></audio>
            <p>
              <button data-pool="${seed}">✓ Pool</button>
              <button data-lock="${seed}">📌 Lock</button>
            </p>
          </div>`).join("")}
      </div>
    </div>

    <div class="card">
      <h3 style="margin-top:0">Profil „${clip.profile}" — ${profile.label}</h3>
      <textarea id="profile-instruct">${escapeHtml(profile.instruct)}</textarea>
      <p>
        <button id="btn-save-profile" class="primary">Instruktion speichern</button>
        <span class="muted">Speichern macht alle Clips dieses Profils stale.</span>
      </p>
      <p class="muted">Seed-Pool:
        ${profile.seedPool.length === 0 ? "leer" : profile.seedPool.map((seed) =>
          `<span class="chip">${seed}
            <a href="#" data-unpool="${seed}" title="entfernen">×</a></span>`).join(" ")}
      </p>
    </div>`;

  el("btn-candidates").onclick = async () => {
    await post(`/api/clips/${encoded}/candidates`, { n: 4 });
  };
  const unlock = el("btn-unlock");
  if (unlock) {
    unlock.onclick = async () => {
      await api(`/api/clips/${encoded}/lock`, { method: "DELETE" });
      await refresh();
    };
  }
  el("clip-profile").onchange = async (event) => {
    await post(`/api/clips/${encoded}/lock`,
               { seed: clip.seed, profile: event.target.value });
    await refresh();
  };
  el("btn-save-profile").onclick = async () => {
    await put(`/api/profiles/${clip.profile}`,
              { instruct: el("profile-instruct").value });
    await refresh();
  };
  el("detail").querySelectorAll("[data-pool]").forEach((button) => {
    button.onclick = async () => {
      await post(`/api/profiles/${clip.profile}/pool`,
                 { seed: Number(button.dataset.pool) });
      await refresh();
    };
  });
  el("detail").querySelectorAll("[data-lock]").forEach((button) => {
    button.onclick = async () => {
      await post(`/api/clips/${encoded}/lock`, { seed: Number(button.dataset.lock) });
      await refresh();
    };
  });
  el("detail").querySelectorAll("[data-unpool]").forEach((link) => {
    link.onclick = async (event) => {
      event.preventDefault();
      await api(`/api/profiles/${clip.profile}/pool/${link.dataset.unpool}`,
                { method: "DELETE" });
      await refresh();
    };
  });
}

function playCandidate(index) {
  const audio = el("detail").querySelector(`audio[data-index="${index}"]`);
  if (audio) {
    audio.currentTime = 0;
    audio.play();
  }
}

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
    playCandidate(Number(event.key) - 1);
  }
});

["search", "filter-profile", "filter-status"].forEach((id) => {
  el(id).addEventListener("input", renderList);
});

el("btn-render").onclick = async () => {
  const profile = el("filter-profile").value || null;
  const label = profile ? `Profil „${profile}"` : "alle Profile";
  if (!confirm(`Finalen Lauf für ${label} starten?`)) return;
  await post("/api/render", { profile });
};

el("btn-cancel").onclick = () => post("/api/jobs/cancel", {});

const events = new EventSource("/events");
events.onmessage = (message) => {
  const event = JSON.parse(message.data);
  if (event.type === "render" || event.type === "candidate") {
    el("progress").textContent =
      `${event.index}/${event.total} · ${event.status} ${event.message || ""}`;
  } else if (event.type === "job-done") {
    el("progress").textContent = "fertig";
    refresh();
  } else if (event.type === "job-error") {
    el("progress").innerHTML = `<span class="warn">${escapeHtml(event.message)}</span>`;
    refresh();
  } else if (event.type === "job-start") {
    el("progress").textContent = `läuft: ${event.job}`;
  }
};

refresh();
