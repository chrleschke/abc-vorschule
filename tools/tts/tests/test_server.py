import json
import shutil
from pathlib import Path

import numpy as np
import pytest
from fastapi.testclient import TestClient

from ttskit.paths import Paths
from ttskit.server import create_app


class FakeEngine:
    def __init__(self):
        self.loaded = True
        self.load_error = None
        self.device = "fake"
        self.calls = []

    def load(self):
        pass

    def validate(self, profiles):
        return []

    def generate(self, text, profile, seed):
        self.calls.append((text, seed))
        rng = np.random.default_rng(abs(hash((text, seed))) % (2 ** 32))
        return rng.standard_normal(2400).astype(np.float32) * 0.5, 24000


@pytest.fixture
def client(tmp_path, content_dir):
    root = tmp_path / "ttsroot"
    (root / "content").mkdir(parents=True)
    for f in content_dir.iterdir():
        shutil.copy(f, root / "content" / f.name)
    (root / "extra-strings.json").write_text(
        json.dumps({"version": 1, "strings": [], "templates": []}), encoding="utf-8")
    paths = Paths(root=root, content_dir=root / "content")
    app = create_app(paths, engine=FakeEngine())
    with TestClient(app) as c:
        c.paths = paths
        yield c


def wait_for_idle(client, timeout=10.0):
    import time
    deadline = time.time() + timeout
    while time.time() < deadline:
        if client.get("/api/jobs").json()["running"] is None:
            return
        time.sleep(0.02)
    raise AssertionError("job did not finish")


def test_state_lists_clips_with_status(client):
    body = client.get("/api/state").json()
    assert body["engine"]["loaded"] is True
    assert set(body["profiles"]) >= {"word", "phoneme", "prompt"}
    assert body["clips"], "expected clips from the fixture content"
    first = body["clips"][0]
    assert set(first) >= {"key", "profile", "text", "status", "seed", "locked", "itemIds"}
    assert first["status"] == "missing"


def test_updating_a_profile_persists_to_disk(client):
    response = client.put("/api/profiles/prompt", json={"instruct": "Neu und anders."})
    assert response.status_code == 200
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    assert raw["profiles"]["prompt"]["instruct"] == "Neu und anders."


def test_updating_an_unknown_profile_is_404(client):
    assert client.put("/api/profiles/nope", json={"instruct": "x"}).status_code == 404


def test_adding_and_removing_pool_seeds(client):
    assert client.post("/api/profiles/prompt/pool", json={"seed": 4242}).status_code == 200
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    assert 4242 in raw["profiles"]["prompt"]["seedPool"]

    # adding twice must not duplicate
    client.post("/api/profiles/prompt/pool", json={"seed": 4242})
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    assert raw["profiles"]["prompt"]["seedPool"].count(4242) == 1

    assert client.delete("/api/profiles/prompt/pool/4242").status_code == 200
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    assert 4242 not in raw["profiles"]["prompt"]["seedPool"]


def test_candidates_are_generated_and_listed(client):
    key = client.get("/api/state").json()["clips"][0]["key"]
    assert client.post(f"/api/clips/{key}/candidates", json={"n": 3}).status_code == 202
    wait_for_idle(client)
    # /api/state is where the UI reads candidate seeds from — there is no
    # separate listing route.
    clip = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    seeds = [c["seed"] for c in clip["candidates"]]
    assert len(seeds) == 3
    audio = client.get(f"/candidates/{key}/{seeds[0]}.wav")
    assert audio.status_code == 200
    assert audio.content[:4] == b"RIFF"


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


def test_locking_a_clip_changes_its_seed(client):
    clip = client.get("/api/state").json()["clips"][0]
    key, before = clip["key"], clip["seed"]
    assert client.post(f"/api/clips/{key}/lock", json={"seed": before + 1}).status_code == 200

    after = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert after["seed"] == before + 1
    assert after["locked"] is True

    raw = json.loads(client.paths.locks.read_text(encoding="utf-8"))
    assert raw["locks"][key]["sourceText"] == clip["text"]

    assert client.delete(f"/api/clips/{key}/lock").status_code == 200
    restored = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert restored["locked"] is False


def test_lock_with_text_override_changes_spoken_text_not_key(client):
    clip = client.get("/api/state").json()["clips"][0]
    key = clip["key"]
    client.post(f"/api/clips/{key}/lock",
                json={"seed": 5, "textOverride": "anders gesprochen"})
    after = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert after["key"] == key
    assert after["text"] == "anders gesprochen"
    assert after["sourceText"] == clip["text"]


def test_render_job_produces_audio(client):
    assert client.post("/api/render", json={"profile": "finale"}).status_code == 202
    wait_for_idle(client)
    clips = [c for c in client.get("/api/state").json()["clips"] if c["profile"] == "finale"]
    assert clips and all(c["status"] == "rendered" for c in clips)
    audio = client.get(f"/audio/{clips[0]['key']}.wav")
    assert audio.status_code == 200
    assert audio.content[:4] == b"RIFF"


def test_unknown_clip_audio_is_404(client):
    assert client.get("/audio/prompt:ffffffffffff.wav").status_code == 404


def test_audio_path_traversal_is_rejected(client):
    assert client.get("/audio/..%2F..%2Fprofiles.json.wav").status_code == 404


def test_only_one_job_runs_at_a_time(client):
    key = client.get("/api/state").json()["clips"][0]["key"]
    client.post(f"/api/clips/{key}/candidates", json={"n": 2})
    second = client.post("/api/render", json={})
    assert second.status_code in (202, 409)
    wait_for_idle(client, timeout=20)


def test_index_is_served(client):
    response = client.get("/")
    assert response.status_code == 200
    assert "<html" in response.text.lower()


def test_events_route_is_wired_to_the_stream_generator(client, monkeypatch):
    """The direct-generator test below cannot see whether `/events` still
    reaches it — httpx can never drive an infinite SSE body. Assert the wiring
    separately, on the registered route."""
    from starlette.responses import StreamingResponse

    from ttskit import server

    route = next(r for r in client.app.routes if getattr(r, "path", None) == "/events")
    unpatched = route.endpoint()
    assert isinstance(unpatched, StreamingResponse)
    assert unpatched.media_type == "text/event-stream"

    seen = []

    def recorder(jobs):
        # Deliberately not a generator function: a generator body would not run
        # until the first `next()`, and the call is what we want to observe.
        seen.append(jobs)
        return iter(["data: {}\n\n"])

    monkeypatch.setattr(server, "_event_stream", recorder)
    route.endpoint()
    assert seen == [client.app.state.jobs], "/events must stream _event_stream(jobs)"


def test_lock_with_an_unknown_profile_is_rejected_and_nothing_breaks(client):
    """The original repro: a typo'd profile used to persist and then brick
    `status`, `extract`, `render` and /api/state all at once."""
    key = client.get("/api/state").json()["clips"][0]["key"]
    response = client.post(f"/api/clips/{key}/lock",
                           json={"seed": 1, "profile": "tippfehler"})
    assert response.status_code == 422
    detail = response.json()["detail"]
    assert "tippfehler" in detail
    assert "phoneme" in detail, "the valid options must be named"

    assert not client.paths.locks.exists() or "tippfehler" not in \
        client.paths.locks.read_text(encoding="utf-8")
    assert client.get("/api/state").status_code == 200


def test_a_hand_edited_lock_naming_a_missing_profile_says_which_key(client):
    key = client.get("/api/state").json()["clips"][0]["key"]
    client.paths.locks.write_text(json.dumps({"version": 1, "locks": {
        key: {"seed": 1, "profile": "tippfehler"},
    }}), encoding="utf-8")
    response = client.get("/api/state")
    assert response.status_code == 500
    detail = response.json()["detail"]
    assert "locks.json" in detail
    assert key in detail
    assert "tippfehler" in detail


def test_lock_with_a_known_profile_is_accepted(client):
    key = next(c["key"] for c in client.get("/api/state").json()["clips"]
               if c["profile"] != "word")
    assert client.post(f"/api/clips/{key}/lock",
                       json={"seed": 1, "profile": "word"}).status_code == 200
    after = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert after["profile"] == "word"


def test_unknown_sampling_keys_are_rejected(client):
    response = client.put("/api/profiles/word", json={"sampling": {"nicht_existent": 99}})
    assert response.status_code == 422
    assert "nicht_existent" in response.json()["detail"]
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8")) \
        if client.paths.profiles.exists() else {}
    assert "nicht_existent" not in json.dumps(raw)


def test_known_sampling_keys_are_merged(client):
    assert client.put("/api/profiles/word",
                      json={"sampling": {"temperature": 0.8}}).status_code == 200
    raw = json.loads(client.paths.profiles.read_text(encoding="utf-8"))
    sampling = raw["profiles"]["word"]["sampling"]
    assert sampling["temperature"] == 0.8
    assert sampling["top_k"] == 30, "the untouched keys must survive"


def test_non_numeric_sampling_values_are_rejected(client):
    response = client.put("/api/profiles/word", json={"sampling": {"temperature": "heiß"}})
    assert response.status_code == 422
    assert "temperature" in response.json()["detail"]


def test_a_non_string_instruct_is_rejected(client):
    response = client.put("/api/profiles/word", json={"instruct": {"a": 1}})
    assert response.status_code == 422
    assert not client.paths.profiles.exists(), "nothing may have been persisted"


def test_candidate_count_is_clamped(client):
    key = client.get("/api/state").json()["clips"][0]["key"]
    assert client.post(f"/api/clips/{key}/candidates",
                       json={"n": 100000}).status_code == 202
    wait_for_idle(client, timeout=30)
    clip = next(c for c in client.get("/api/state").json()["clips"] if c["key"] == key)
    assert len(clip["candidates"]) == 16


def test_a_render_where_everything_fails_does_not_report_success(tmp_path, content_dir):
    """Engine offline is the realistic case: Engine.load swallows exceptions so
    the server can start model-less, and the render button is never disabled."""
    import shutil as _shutil

    from ttskit.paths import Paths as _Paths
    from ttskit.server import create_app as _create_app

    class BrokenEngine(FakeEngine):
        def __init__(self):
            super().__init__()
            self.loaded = False
            self.load_error = "RuntimeError: kein Modell"

        def generate(self, text, profile, seed):
            raise RuntimeError("Engine not loaded — call load() first.")

    root = tmp_path / "broken"
    (root / "content").mkdir(parents=True)
    for f in content_dir.iterdir():
        _shutil.copy(f, root / "content" / f.name)
    paths = _Paths(root=root, content_dir=root / "content")
    app = _create_app(paths, engine=BrokenEngine())

    with TestClient(app) as c:
        jobs = app.state.jobs
        seen = []
        original_publish = jobs.publish
        jobs.publish = lambda event: (seen.append(event), original_publish(event))[1]

        assert c.post("/api/render", json={"profile": "finale"}).status_code == 202
        wait_for_idle(c)

    summary = next(e for e in seen if e["type"] == "job-summary")
    assert summary["rendered"] == 0
    assert summary["failed"] > 0, "a total failure must be counted, not swallowed"
    assert seen[-1]["type"] == "job-done", "job-summary must precede job-done"


def test_a_successful_render_publishes_its_counts(client):
    jobs = client.app.state.jobs
    seen = []
    original_publish = jobs.publish
    jobs.publish = lambda event: (seen.append(event), original_publish(event))[1]

    client.post("/api/render", json={"profile": "finale"})
    wait_for_idle(client)

    summary = next(e for e in seen if e["type"] == "job-summary")
    assert summary["failed"] == 0
    assert summary["rendered"] > 0


def test_events_streams_the_initial_status_frame(client):
    # httpx's ASGITransport drives the whole ASGI app to completion before
    # returning any response (see httpx.ASGITransport.handle_async_request),
    # so a genuinely infinite SSE generator can never be exercised through a
    # real request/response round trip — it would hang forever. Instead, call
    # the generator that backs `/events` directly, which is exactly what a
    # client of that endpoint would receive as the first framed chunk.
    from ttskit.server import _event_stream

    jobs = client.app.state.jobs
    gen = _event_stream(jobs)
    try:
        frame = next(gen)
        assert frame.startswith("data:")
        payload = json.loads(frame[len("data:"):])
        assert "running" in payload
        assert "queued" in payload
    finally:
        gen.close()
