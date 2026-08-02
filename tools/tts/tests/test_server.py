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
    seeds = client.get(f"/api/clips/{key}/candidates").json()["seeds"]
    assert len(seeds) == 3
    audio = client.get(f"/candidates/{key}/{seeds[0]}.wav")
    assert audio.status_code == 200
    assert audio.content[:4] == b"RIFF"


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
