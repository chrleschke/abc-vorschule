"""Batch rendering and candidate sampling.

The engine is injected so the whole batch logic is testable with a fake —
loading 4 GB of weights to check a loop would be absurd.
"""

from __future__ import annotations

import fnmatch
import json
import os
import secrets
import tempfile
from dataclasses import dataclass, field, replace
from datetime import datetime, timezone
from pathlib import Path
from typing import Any, Callable, Iterable, Protocol

import numpy as np

from .audio import postprocess, write_wav
from .models import Clip
from .paths import Paths
from .plan import effective_profile, fingerprint, resolve_seed, status_of, top_seeds
from .store import CURATED_FILES_LOCK, Lock, Locks, Profile, Profiles, RenderState


class SupportsGenerate(Protocol):
    def generate(self, text: str, profile: Profile, seed: int) -> tuple[np.ndarray, int]: ...


@dataclass
class Progress:
    index: int
    total: int
    clip_key: str
    status: str
    message: str = ""


@dataclass
class RenderReport:
    rendered: int = 0
    #: Alles Übersprungene, auch das aus `protected`.
    skipped: int = 0
    failed: list[tuple[str, str]] = field(default_factory=list)
    #: (clipKey, Grund) für Clips, die `render_clips` absichtlich nicht anfasst,
    #: obwohl sie lokal fehlen — siehe `render_protection`. Einzeln aufgeführt,
    #: damit die CLI sagt, *warum* ein fehlender Clip nicht gerendert wurde.
    protected: list[tuple[str, str]] = field(default_factory=list)
    #: Clips, die ein Abbruch nicht mehr (ganz) drankommen ließ. Kein
    #: Fehlschlag: vorher landete der Rest eines abgebrochenen Laufs unter
    #: `failed`, und die UI meldete „fehlgeschlagen", wo nur jemand Stopp drückte.
    cancelled: int = 0


def _is_mic_seed(seed: int) -> bool:
    # Lokaler Import: mic.py zieht scipy, und `render` braucht es sonst nie.
    from .mic import MIC_SEED_MIN

    return seed >= MIC_SEED_MIN


def render_protection(clip: Clip, profile: Profile, paths: Paths, *,
                      force: bool = False) -> str | None:
    """Warum `tts render` diesen Clip nicht neu erzeugen darf — None, wenn er darf.

    `out/` ist gitignored. Auf einem frischen Checkout fehlt darum jede
    Produktions-WAV, auch die der längst committeten, von Hand kuratierten
    Clips — und `render` hätte sie alle mit Qwen neu gewürfelt, worauf der
    nächste Export die committete .ogg überschrieb:

    * **Mikrofon** (Profil-Quelle `mic` oder ein Pseudo-Seed ab
      `mic.MIC_SEED_MIN`): ein Qwen-Render ist dort nie die Aufnahme, auch nicht
      mit `--force`. Die Monster-Reaktionen entstehen über die Kandidaten im
      Web-Interface.
    * **Gelockt und schon exportiert**: die .ogg in den App-Assets ist die
      freigegebene Fassung, womöglich geschnitten oder per Kandidat bestätigt —
      ein Neu-Render mit demselben Seed wäre bestenfalls gleich, meistens
      nicht. Hier hilft `--force`, wenn man es wirklich will.
    """
    if profile.source == "mic":
        return "Profil nimmt per Mikrofon auf — nie mit Qwen gerendert (Web-Interface nutzen)"
    if candidate_meta(paths, clip.key, clip.seed).get("source") == "mic":
        return "Produktion ist eine Mikrofon-Aufnahme — nie mit Qwen gerendert"
    # Auf einem frischen Checkout fehlt das Sidecar; dann bleibt nur der Bereich.
    # Nur am Lock: ein ungelockter Clip ohne Seed-Pool bekommt seinen Seed aus
    # dem Hash (`plan.resolve_seed`, bis 2**31) und landet damit gelegentlich im
    # selben Bereich, ohne je eine Aufnahme gewesen zu sein. Und nur ohne lokale
    # Produktions-WAV: liegt sie da und hat kein Mikrofon-Sidecar, ist sie ein
    # Qwen-Render mit Hash-Seed — `--force` muss sie neu erzeugen können.
    if (clip.locked and _is_mic_seed(clip.seed)
            and not (paths.audio / f"{clip.key}.wav").exists()):
        return "Lock-Seed liegt im Mikrofon-Bereich — nie mit Qwen gerendert"
    if force or not clip.locked:
        return None
    # Lokaler Import: export importiert render auf Modulebene.
    from .export import asset_name

    if (Path(paths.app_audio_dir) / asset_name(clip.key)).exists():
        return "gelockt und schon in die App exportiert — committete Datei bleibt (--force rendert neu)"
    return None


def _select(clips: Iterable[Clip], only: str | None, profile: str | None) -> list[Clip]:
    out = list(clips)
    if profile:
        out = [c for c in out if c.profile == profile]
    if only:
        # fnmatchcase, not fnmatch: clip keys and item ids are case-sensitive
        # identifiers, and fnmatch would normalise them on case-insensitive
        # filesystems like the default macOS one.
        out = [c for c in out if fnmatch.fnmatchcase(c.key, only)
               or any(fnmatch.fnmatchcase(i, only) for i in c.item_ids)]
    return out


def render_clips(
    clips: Iterable[Clip],
    profiles: Profiles,
    engine: SupportsGenerate | None,
    state: RenderState,
    paths: Paths,
    *,
    force: bool = False,
    only: str | None = None,
    profile: str | None = None,
    dry_run: bool = False,
    progress: Callable[[Progress], None] | None = None,
    cancel: Callable[[], bool] | None = None,
) -> RenderReport:
    selected = _select(clips, only, profile)
    report = RenderReport()

    todo: list[tuple[Clip, Profile]] = []
    for clip in selected:
        prof = profiles.profiles[clip.profile]
        # `status_of` owns the "already rendered" predicate — re-deriving it
        # here once made `status` and `render` two expressions for one truth.
        # Erst danach der Schutz: „bewusst nicht gerendert" soll nur nennen,
        # was lokal fehlt, sonst stehen dort Hunderte fertige Clips.
        if status_of(clip, paths.audio) == "rendered" and not force:
            report.skipped += 1
            continue
        reason = render_protection(clip, prof, paths, force=force)
        if reason is not None:
            report.skipped += 1
            report.protected.append((clip.key, reason))
            continue
        todo.append((clip, prof))

    if dry_run:
        report.rendered = len(todo)
        return report

    # `engine` is legitimately None for a dry run, which returned above. Assert
    # rather than trust the ordering: a future reordering must fail here and not
    # as an AttributeError deep inside the loop.
    assert engine is not None, "render_clips needs an engine unless dry_run=True"

    total = len(todo)
    for index, (clip, prof) in enumerate(todo, start=1):
        if cancel is not None and cancel():
            report.cancelled = total - index + 1
            break
        try:
            # `clip.text`, nicht der Entwurf: hier entsteht die Produktion, und
            # deren Fingerprint (Export) rechnet mit dem Produktionstext.
            wav, sample_rate = engine.generate(
                clip.text, effective_profile(clip, prof), clip.seed)
            wav = postprocess(wav, sample_rate, trim=prof.trim, normalize=prof.normalize)
            write_wav(paths.audio / f"{clip.key}.wav", wav, sample_rate)
            state.failures.pop(clip.key, None)
            # Written per clip, not at the end: an aborted half-hour run
            # must not throw away the work it already did.
            state.save(paths.render_state)
            report.rendered += 1
            status = "ok"
            message = ""
        except Exception as exc:  # noqa: BLE001 - reported, batch continues
            message = f"{type(exc).__name__}: {exc}"
            report.failed.append((clip.key, message))
            # Persisted too, so `tts status` can still name the failure after
            # the process is gone — an in-memory report helps nobody tomorrow.
            state.failures[clip.key] = message
            state.save(paths.render_state)
            status = "failed"
        if progress is not None:
            progress(Progress(index=index, total=total, clip_key=clip.key,
                              status=status, message=message))
    return report


def render_batch_candidates(
    clips: Iterable[Clip],
    profiles: Profiles,
    engine: SupportsGenerate | None,
    state: RenderState,
    paths: Paths,
    locks: Locks,
    *,
    count: int,
    force: bool = False,
    only: str | None = None,
    profile: str | None = None,
    dry_run: bool = False,
    progress: Callable[[Progress], None] | None = None,
    clip_start: Callable[[str], None] | None = None,
    clip_done: Callable[[str], None] | None = None,
    cancel: Callable[[], bool] | None = None,
    refresh: Callable[[str], tuple[Clip, Profile, Locks] | None] | None = None,
) -> RenderReport:
    """Batch-Lauf im Web-Interface: erzeugt pro Clip `count` Kandidaten statt
    direkt eine Produktions-Datei zu schreiben.

    Anders als `render_clips` (der finale, inkrementelle CLI-Lauf, der
    weiterhin unverändert direkt in die Produktions-Datei schreibt) bleibt
    Produktion hier immer ein bewusster Schritt in der Kandidaten-Liste: ein
    unbestätigter Treffer würde sonst beim nächsten Profil- oder Pool-Wechsel
    stillschweigend durch einen anderen Seed ersetzt, ohne dass ihn je jemand
    gehört hat.

    `clip_start(key)` und `clip_done(key)` melden jeden Clip einzeln, beim
    Anfangen und beim Fertigwerden. `progress` kann das nicht leisten: es
    feuert erst *nach* jedem Kandidaten und zählt nur Einheiten über den
    ganzen Lauf, aus denen ein Client die Clip-Grenze bloß erraten könnte —
    der erste Kandidat eines Clips wäre damit nirgends sichtbar. Das
    Web-Interface braucht beide Momente aber genau: um zu zeigen, wo der Lauf
    steht, und um einen fertigen Clip sofort zum Abhören freizugeben, während
    der Lauf weitergeht.

    `refresh(key)` liefert Clip, Profil und Locks so, wie sie *jetzt* auf der
    Platte stehen (None: den Clip gibt es nicht mehr). Ein Lauf dauert Minuten
    bis Stunden, und währenddessen wird weiter kuratiert — ohne Nachladen
    erzeugte er die späten Clips mit dem Text, der Stimme und dem Profil vom
    Start des Laufs, also mit einer Aussprache, die längst korrigiert war.
    Ein inzwischen bestätigter Clip wird übersprungen wie beim Start.
    """
    selected = _select(clips, only, profile)
    report = RenderReport()

    todo: list[Clip] = []
    for clip in selected:
        if status_of(clip, paths.audio) == "rendered" and not force:
            report.skipped += 1
            continue
        todo.append(clip)

    if dry_run:
        report.rendered = len(todo)
        return report

    # `engine` is legitimately None for a dry run, which returned above. Assert
    # rather than trust the ordering: a future reordering must fail here and not
    # as an AttributeError deep inside the loop.
    assert engine is not None, "render_batch_candidates needs an engine unless dry_run=True"

    total = len(todo)
    units_done = 0
    total_units = total * count
    for index, clip in enumerate(todo, start=1):
        if cancel is not None and cancel():
            report.cancelled += total - index + 1
            break
        prof = profiles.profiles[clip.profile]
        clip_locks = locks
        if refresh is not None:
            fresh = refresh(clip.key)
            if fresh is None or (status_of(fresh[0], paths.audio) == "rendered"
                                 and not force):
                report.skipped += 1
                units_done += count
                # Die UI führt den Clip seit dem Start als „kommt noch dran".
                if clip_done is not None:
                    clip_done(clip.key)
                continue
            clip, prof, clip_locks = fresh
        if clip_start is not None:
            clip_start(clip.key)
        seeds = seeds_for_candidates(
            count=count, clip=clip, profile=prof, paths=paths, locks=clip_locks,
            use_top_seeds=True)

        base = units_done
        tally = {"attempted": 0, "failed": 0}

        def on_candidate(p: Progress, clip: Clip = clip, base: int = base,
                         tally: dict = tally) -> None:
            tally["attempted"] += 1
            if p.status == "failed":
                tally["failed"] += 1
            if progress is not None:
                progress(Progress(index=base + p.index, total=total_units,
                                  clip_key=clip.key, status=p.status, message=p.message))

        sample_candidates(clip, prof, engine, paths, seeds,
                          progress=on_candidate, cancel=cancel)
        units_done += len(seeds)
        if tally["failed"]:
            report.failed.append((
                clip.key,
                f"{tally['failed']} von {len(seeds)} Kandidaten fehlgeschlagen"))
        elif tally["attempted"] < len(seeds):
            # Mitten im Clip abgebrochen: was fehlt, hat niemand versucht.
            report.cancelled += 1
        else:
            report.rendered += 1
        # Auch ein fehlgeschlagener Clip ist abgearbeitet: bliebe er aus, wäre
        # er in der UI für den Rest des Laufs „erzeugt gerade". Was schief ging,
        # sagt am Ende `job-summary`.
        if clip_done is not None:
            clip_done(clip.key)
    return report


def seeds_for_candidates(
    *,
    count: int,
    clip: Clip,
    profile: Profile,
    paths: Paths,
    locks: Locks,
    use_top_seeds: bool = False,
    use_known_seeds: bool = False,
) -> list[int]:
    """Seeds für neue Kandidaten — dieselbe Logik wie „🎲 Generate" in der UI."""
    rendered = set(candidate_seeds(paths, clip.key))
    # Der Produktions-Seed auch dann, wenn lokal kein Kandidat dazu liegt (frischer
    # Checkout, Nachbau-Eintrag): ein neuer Wurf darauf läge neben einer
    # Produktion, die anders klingt, unter demselben Seed (siehe sample_candidates).
    if (Path(paths.audio) / f"{clip.key}.wav").exists():
        rendered.add(clip.seed)
    top = top_seeds(locks, clip.profile) if use_top_seeds else []
    if top:
        return pooled_seeds(count, top, exclude=rendered)
    if use_known_seeds and profile.seed_pool:
        return pooled_seeds(count, profile.seed_pool, exclude=rendered)
    return random_seeds(count, exclude=rendered | set(profile.seed_pool))


def random_seeds(n: int, exclude: set[int] | None = None) -> list[int]:
    # Der Bereich ab MIC_SEED_MIN gehört den Mikrofon-Aufnahmen (mic.py) —
    # ein Qwen-Kandidat darf nie mit einer Aufnahme kollidieren.
    from .mic import MIC_SEED_MIN

    blocked = set(exclude or ())
    out: list[int] = []
    while len(out) < n:
        candidate = secrets.randbelow(MIC_SEED_MIN)
        if candidate in blocked:
            continue
        blocked.add(candidate)
        out.append(candidate)
    return out


def pooled_seeds(n: int, pool: list[int], exclude: set[int] | None = None) -> list[int]:
    """`n` Seeds, zufällig aus `pool` gezogen, ohne die aus `exclude`.

    Für „bekannte Seeds verwenden“: der Pool sammelt die 👍-Seeds eines Profils,
    klingt also erprobt. Reicht er nicht für `n` (leer, oder alles schon als
    Kandidat vorhanden), wird mit frischen Zufalls-Seeds aufgefüllt statt
    weniger als bestellt zu liefern — sonst würde die Anzahl im UI stillschweigend
    schrumpfen.
    """
    blocked = set(exclude or ())
    available = [seed for seed in dict.fromkeys(pool) if seed not in blocked]
    rng = secrets.SystemRandom()
    chosen = rng.sample(available, min(n, len(available)))
    if len(chosen) < n:
        chosen += random_seeds(n - len(chosen), exclude=blocked | set(pool))
    return chosen


#: Sidecar-Felder, die ein Mensch gesetzt hat und die ein neuer Wurf auf
#: demselben Seed übernimmt. Bewusst nicht `trim`: der Schnitt gehörte zur
#: vorigen Aufnahme (siehe unten).
CURATED_SIDECAR_FIELDS = ("rating",)


def sample_candidates(
    clip: Clip,
    profile: Profile,
    engine: SupportsGenerate,
    paths: Paths,
    seeds: list[int],
    progress: Callable[[Progress], None] | None = None,
    cancel: Callable[[], bool] | None = None,
) -> list[int]:
    """Kandidaten erzeugen; gibt die geschriebenen Seeds zurück.

    Zwei Seeds werden übersprungen (Progress-Status "skipped"), statt sie zu
    überschreiben: eine Mikrofon-Aufnahme unter demselben Pseudo-Seed, und der
    Produktions-Seed, solange die Produktion liegt — der neue Kandidat sähe aus
    wie die Produktion, klänge aber anders, und Promote hielte ihn für „schon
    Produktion".
    """
    written: list[int] = []
    production = Path(paths.audio) / f"{clip.key}.wav"
    for index, seed in enumerate(seeds, start=1):
        if cancel is not None and cancel():
            break
        if candidate_meta(paths, clip.key, seed).get("source") == "mic":
            status, message = "skipped", f"Seed {seed} ist eine Mikrofon-Aufnahme"
        elif seed == clip.seed and production.exists():
            status, message = ("skipped", f"Seed {seed} ist die Produktion — "
                               "erst „Keine Produktion\u201c, dann neu würfeln")
        else:
            try:
                # Der Entwurf, nicht der Produktionstext: Probeaufnahmen sind
                # genau dafür da, einen neuen Text auszuprobieren.
                wav, sample_rate = engine.generate(
                    clip.generation_text, effective_profile(clip, profile), seed)
                wav = postprocess(wav, sample_rate, trim=profile.trim,
                                  normalize=profile.normalize)
                with CURATED_FILES_LOCK:
                    write_wav(paths.candidates / clip.key / f"{seed}.wav", wav, sample_rate)
                    # Ein neuer Wurf auf demselben Seed ersetzt auch einen alten
                    # Schnitt: das gesicherte Original gehört zur vorigen Aufnahme.
                    _orig_path(paths, clip.key, seed).unlink(missing_ok=True)
                    # Das Sidecar hält fest, WOMIT die Probeaufnahme entstand. Ohne
                    # Zeitpunkt, Stimme und Text mischen sich in der UI die Batches
                    # verschiedener Sessions zu einer unentwirrbaren Liste.
                    meta = {
                        "fingerprint": fingerprint(
                            replace(clip, seed=seed, text=clip.generation_text), profile),
                        "createdAt": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                        "speaker": clip.speaker,
                        # Steht hier, damit Promote genau diesen Text als
                        # Produktionstext übernimmt (server.api_promote).
                        "text": clip.generation_text,
                    }
                    # Erst hier, unter der Sperre, gelesen: ein 👍 während der
                    # Generierung gilt mit. Ohne Übernahme verlor ein neuer Wurf
                    # auf einem 👍-Seed die Bewertung, während der Seed im Pool
                    # blieb — und 👎 räumte ihn danach nicht mehr ab.
                    previous = candidate_meta(paths, clip.key, seed)
                    for key in CURATED_SIDECAR_FIELDS:
                        if key in previous:
                            meta[key] = previous[key]
                    _write_meta(paths, clip.key, seed, meta)
                written.append(seed)
                status, message = "ok", ""
            except Exception as exc:  # noqa: BLE001
                status, message = "failed", f"{type(exc).__name__}: {exc}"
        if progress is not None:
            progress(Progress(index=index, total=len(seeds), clip_key=clip.key,
                              status=status, message=message))
    return written


def candidate_seeds(paths: Paths, clip_key: str) -> list[int]:
    directory = Path(paths.candidates) / clip_key
    if not directory.exists():
        return []
    return sorted(int(p.stem) for p in directory.glob("*.wav") if p.stem.isdigit())


def candidate_meta(paths: Paths, clip_key: str, seed: int) -> dict[str, Any]:
    """Sidecar-Metadaten eines Kandidaten — {} wenn unbekannt.

    Kandidaten aus der Zeit vor den Sidecars haben keine Metadatei; eine
    kaputte Datei behandeln wir genauso, statt die ganze State-Antwort zu
    reißen: 'unbekannt' ist hier eine legitime Antwort.
    """
    path = Path(paths.candidates) / clip_key / f"{seed}.json"
    try:
        raw = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError):
        return {}
    return raw if isinstance(raw, dict) else {}


def candidate_fingerprint(paths: Paths, clip_key: str, seed: int) -> str | None:
    value = candidate_meta(paths, clip_key, seed).get("fingerprint")
    return value if isinstance(value, str) else None


def production_fingerprint(paths: Paths, clip: Clip, profile: Profile) -> str:
    """Fingerprint der Produktion für Export und Promote.

    Für Qwen-Clips `plan.fingerprint`; für eine Mikrofon-Aufnahme der Hash der
    bearbeiteten Audiodatei aus dem Sidecar — nur der ändert sich, wenn jemand
    neu schneidet oder pitcht, und nur dann soll der Export neu encodieren.
    """
    meta = candidate_meta(paths, clip.key, clip.seed)
    if meta.get("source") == "mic" and isinstance(meta.get("fingerprint"), str):
        return meta["fingerprint"]
    base = fingerprint(clip, profile)
    # Ein Schnitt in der Wellenform ändert die Datei, aber nicht die
    # Profil-Einstellungen — ohne ihn im Fingerprint hielte der Export die
    # Produktion für unverändert und die App behielte die ungeschnittene Fassung.
    trim = meta.get("trim")
    if isinstance(trim, dict):
        return f"{base}~{float(trim['start']):.3f}-{float(trim['end']):.3f}"
    return base


#: Kürzester Rest, der nach einem Schnitt bleiben muss (Sekunden).
MIN_TRIMMED_SECONDS = 0.05
#: Weiche Kanten an beiden Schnittstellen, gegen Knackser (Sekunden).
TRIM_FADE_SECONDS = 0.005


def _orig_path(paths: Paths, clip_key: str, seed: int) -> Path:
    return Path(paths.candidates) / clip_key / f"{seed}.orig.wav"


def candidate_original_path(paths: Paths, clip_key: str, seed: int) -> Path | None:
    """Die ungeschnittene Fassung eines geschnittenen Kandidaten, sonst None."""
    path = _orig_path(paths, clip_key, seed)
    trimmed = isinstance(candidate_meta(paths, clip_key, seed).get("trim"), dict)
    return path if trimmed and path.exists() else None


def trim_candidate(paths: Paths, clip: Clip, seed: int, start: float, end: float) -> dict[str, Any]:
    """Probeaufnahme vorne und hinten beschneiden — verlustfrei.

    Beim ersten Schnitt wird die Aufnahme als `<seed>.orig.wav` gesichert; jeder
    weitere Schnitt geht wieder vom Original aus, man kann also auch zurückziehen.
    Umfasst der Schnitt die volle Länge, fällt er weg und das Original kehrt
    zurück. Ist der Kandidat die Produktion, zieht die Produktions-Datei mit.
    Mikrofon-Aufnahmen haben ihren eigenen Editor (Rohdatei + Edit) und werden
    hier abgewiesen.
    """
    import soundfile as sf

    directory = Path(paths.candidates) / clip.key
    wav_path = directory / f"{seed}.wav"
    if not wav_path.exists():
        raise FileNotFoundError(seed)
    meta = candidate_meta(paths, clip.key, seed)
    if meta.get("source") == "mic":
        raise ValueError("Mikrofon-Aufnahmen werden über ✂ geschnitten")
    orig = _orig_path(paths, clip.key, seed)
    if not (orig.exists() and isinstance(meta.get("trim"), dict)):
        orig.write_bytes(wav_path.read_bytes())

    data, sr = sf.read(orig, dtype="float32")
    duration = len(data) / sr
    start, end = float(start), float(end)
    if not (0.0 <= start < end <= duration + 1e-6):
        raise ValueError(f"Schnitt 0 ≤ Anfang < Ende ≤ {duration:.3f} s verletzt "
                         f"(Anfang {start:.3f}, Ende {end:.3f})")
    if end - start < MIN_TRIMMED_SECONDS:
        raise ValueError(f"Es müssen mindestens {MIN_TRIMMED_SECONDS * 1000:.0f} ms bleiben")

    tolerance = 1.0 / sr
    if start <= tolerance and end >= duration - tolerance:
        wav_path.write_bytes(orig.read_bytes())
        orig.unlink()
        meta = update_candidate_meta(paths, clip.key, seed, trim=None)
    else:
        piece = np.array(data[int(round(start * sr)):int(round(end * sr))], dtype=np.float32)
        fade = min(int(sr * TRIM_FADE_SECONDS), len(piece) // 2)
        if fade > 0:
            ramp = np.linspace(0.0, 1.0, fade, dtype=np.float32)
            if start > tolerance:
                piece[:fade] *= ramp
            if end < duration - tolerance:
                piece[-fade:] *= ramp[::-1]
        write_wav(wav_path, piece, sr)
        meta = update_candidate_meta(paths, clip.key, seed, trim={
            "start": round(start, 4), "end": round(end, 4), "duration": round(duration, 4)})

    production = Path(paths.audio) / f"{clip.key}.wav"
    if clip.seed == seed and production.exists():
        tmp = production.with_suffix(".wav.tmp")
        tmp.write_bytes(wav_path.read_bytes())
        os.replace(tmp, production)
    return meta


def update_candidate_meta(paths: Paths, clip_key: str, seed: int,
                          **changes: Any) -> dict[str, Any]:
    """Einzelne Sidecar-Felder setzen (None löscht ein Feld ausdrücklich).

    Der Rest der Metadaten — allen voran der Erzeugungs-Fingerprint — bleibt
    unangetastet: eine Bewertung darf einen Kandidaten nicht "frisch" oder
    "veraltet" machen.
    """
    with CURATED_FILES_LOCK:
        meta = candidate_meta(paths, clip_key, seed)
        for key, value in changes.items():
            if value is None:
                meta.pop(key, None)
            else:
                meta[key] = value
        _write_meta(paths, clip_key, seed, meta)
    return meta


def _write_meta(paths: Paths, clip_key: str, seed: int, meta: dict[str, Any]) -> None:
    """Sidecar atomar schreiben — der Server liest es parallel für /api/state."""
    path = Path(paths.candidates) / clip_key / f"{seed}.json"
    path.parent.mkdir(parents=True, exist_ok=True)
    payload = json.dumps(meta, ensure_ascii=False) + "\n"
    fd, tmp_name = tempfile.mkstemp(
        dir=path.parent, prefix=f".{path.name}.", suffix=".tmp")
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as f:
            f.write(payload)
        os.replace(tmp_name, path)
    except BaseException:
        try:
            os.unlink(tmp_name)
        except OSError:
            pass
        raise


def candidate_infos(paths: Paths, clip: Clip, profile: Profile) -> list[dict]:
    """Kandidaten mit allem, was die UI zum Auseinanderhalten braucht:
    Frische (fresh: None = Alt-Kandidat ohne Sidecar), Erzeugungszeitpunkt,
    Stimme und Text zur Erzeugungszeit sowie die gespeicherte Bewertung.

    Neueste zuerst — genau deshalb steht der Zeitpunkt im Sidecar. Kandidaten
    ohne Zeitstempel (vor den Metadaten erzeugt) landen am Ende.
    """
    infos = []
    for seed in candidate_seeds(paths, clip.key):
        meta = candidate_meta(paths, clip.key, seed)
        recorded = meta.get("fingerprint")
        recorded = recorded if isinstance(recorded, str) else None
        # Frisch heißt: mit den heutigen Einstellungen (Profil, Stimme, Seed)
        # für *seinen eigenen* Text erzeugt. Gegen den aktuellen Entwurf
        # verglichen, würde jeder Tastendruck im TTS-Feld alle Kandidaten auf
        # „⚠️ alt" kippen; welcher Text gesprochen ist, zeigt die Text-Spalte.
        own_text = meta.get("text") if isinstance(meta.get("text"), str) else clip.generation_text
        current = fingerprint(replace(clip, seed=seed, text=own_text), profile)
        is_mic = meta.get("source") == "mic"
        infos.append({
            "seed": seed,
            # Eine Aufnahme kann nicht „veraltet" sein — sie hängt an keiner
            # Profil-Einstellung. Immer frisch, damit kein „⚠️ alt" erscheint.
            "fresh": True if is_mic else (None if recorded is None else recorded == current),
            "createdAt": meta.get("createdAt"),
            "speaker": meta.get("speaker"),
            "text": meta.get("text"),
            "good": meta.get("rating") == "good",
            "mic": is_mic,
            # {start, end, duration} in Sekunden des Originals, sonst None.
            "trim": meta.get("trim") if isinstance(meta.get("trim"), dict) else None,
        })
    infos.sort(key=lambda info: (info["createdAt"] or "", info["seed"]), reverse=True)
    return infos


def clip_audio_list(paths: Paths, clip: Clip, profile: Profile) -> list[dict]:
    """Kandidaten UND — falls keiner von ihnen der aktuellen Produktion
    entspricht — ein Nachbau-Eintrag für die Produktions-Datei selbst.

    Vor dem Umbau auf Batch-Kandidaten schrieb ein Batch-Lauf direkt in die
    Produktions-Datei, ohne je einen Kandidaten anzulegen; dasselbe gilt für
    den finalen `tts render` auf der Kommandozeile. Ohne diesen Nachbau
    verschwände so eine Aufnahme aus der Web-UI, sobald die eigene
    Anzeige über der Kandidaten-Tabelle wegfällt — dabei ist die Tabelle
    jetzt die einzige Stelle, an der man sie noch anhören und bewusst
    festlegen kann.

    Kein `fresh`-Feld für diesen Eintrag: er IST die aktuelle Produktion,
    ein späteres Profil-Update darf sie nicht nachträglich als veraltet
    zeigen (siehe `plan.status_of`).
    """
    infos = candidate_infos(paths, clip, profile)
    if any(info["seed"] == clip.seed for info in infos):
        return infos
    audio_path = Path(paths.audio) / f"{clip.key}.wav"
    if not audio_path.exists():
        return infos
    created = datetime.fromtimestamp(
        audio_path.stat().st_mtime, timezone.utc).isoformat(timespec="seconds")
    infos.append({
        "seed": clip.seed,
        "createdAt": created,
        "speaker": clip.speaker,
        "text": clip.text,
        "good": False,
        "isProductionOnly": True,
        "mic": False,
    })
    infos.sort(key=lambda info: (info["createdAt"] or "", info["seed"]), reverse=True)
    return infos


def candidate_is_protected(paths: Paths, clip: Clip, seed: int, meta: dict) -> bool:
    """Probeaufnahmen, die „Alle löschen" und 👎 nie anfassen dürfen."""
    if meta.get("rating") == "good":
        return True
    production = paths.audio / f"{clip.key}.wav"
    return seed == clip.seed and production.exists()


def deletable_candidate_seeds(paths: Paths, clip: Clip) -> tuple[list[int], int]:
    """Seeds unter `candidates/` ohne Produktion und ohne 👍."""
    deletable: list[int] = []
    protected = 0
    for seed in candidate_seeds(paths, clip.key):
        meta = candidate_meta(paths, clip.key, seed)
        if candidate_is_protected(paths, clip, seed, meta):
            protected += 1
        else:
            deletable.append(seed)
    return deletable, protected


def seed_rated_good_elsewhere(paths: Paths, profile_name: str, seed: int,
                              exclude_key: str) -> bool:
    """Hält ein anderer Clip desselben Profils diesen Seed noch mit 👍?

    Der Seed-Pool gehört dem Profil, die Bewertung dem einzelnen Kandidaten.
    Ein 👎 (oder das Zurücknehmen von 👍) an *einem* Clip nahm den Seed bisher
    aus dem Pool, obwohl ein anderer Clip ihn weiter für gut befand — der Pool
    vergaß eine Bewertung, die in dessen Sidecar noch stand.

    Gesucht wird über die Kandidaten-Ordner statt über die Clip-Liste, damit
    die Helfer ohne Content-Pack auskommen; das Profil eines Ordners ist das,
    mit dem wirklich synthetisiert wird (Lock-Override vor Key-Präfix, wie in
    `plan.top_seeds`).
    """
    locks = Locks.load(paths.locks)
    for meta_path in Path(paths.candidates).glob(f"*/{seed}.json"):
        key = meta_path.parent.name
        if key == exclude_key or not meta_path.with_suffix(".wav").exists():
            continue
        lock = locks.get(key)
        effective = lock.profile if lock and lock.profile else key.split(":", 1)[0]
        if effective != profile_name:
            continue
        if candidate_meta(paths, key, seed).get("rating") == "good":
            return True
    return False


def release_pool_seed(paths: Paths, profile_name: str, seed: int, exclude_key: str) -> bool:
    """Seed aus dem Pool nehmen — außer ein anderer Clip des Profils hält ihn mit 👍.

    True, wenn der Seed wirklich entfernt wurde.
    """
    with CURATED_FILES_LOCK:
        if seed_rated_good_elsewhere(paths, profile_name, seed, exclude_key):
            return False
        profiles = Profiles.load(paths.profiles)
        profile = profiles.profiles[profile_name]
        if seed not in profile.seed_pool:
            return False
        profile.seed_pool = [s for s in profile.seed_pool if s != seed]
        profiles.save(paths.profiles)
        return True


def delete_candidate_wav(paths: Paths, clip: Clip, seed: int) -> None:
    """Eine Probeaufnahme entfernen — Pool, Produktion und Lock wie im UI-👎."""
    with CURATED_FILES_LOCK:
        wav = paths.candidates / clip.key / f"{seed}.wav"
        if not wav.exists():
            raise FileNotFoundError(seed)

        rated_good = candidate_meta(paths, clip.key, seed).get("rating") == "good"

        wav.unlink()
        (paths.candidates / clip.key / f"{seed}.json").unlink(missing_ok=True)
        (paths.candidates / clip.key / f"{seed}.raw.wav").unlink(missing_ok=True)
        _orig_path(paths, clip.key, seed).unlink(missing_ok=True)

        # Nach dem Löschen: der eigene Sidecar zählt dann nicht mehr mit.
        if rated_good:
            release_pool_seed(paths, clip.profile, seed, exclude_key=clip.key)

        production = paths.audio / f"{clip.key}.wav"
        if clip.seed == seed and production.exists():
            production.unlink()

        if not candidate_seeds(paths, clip.key) and not production.exists():
            locks = Locks.load(paths.locks)
            lock = locks.get(clip.key)
            # Nur wenn der gelöschte Seed der Lock-Seed war. Auf einem frischen
            # Checkout gibt es Lock und exportierte .ogg, aber keine lokale WAV:
            # löscht man dort den letzten neu gewürfelten Kandidaten, fiel der
            # Lock mit — und der nächste Export entfernte die committete Datei.
            if lock is not None and lock.seed == seed and not lock.curated:
                locks.remove(clip.key)
                locks.save(paths.locks)


def clear_production(paths: Paths, clip: Clip) -> bool:
    """Produktions-Audio aufheben — 👍-Bewertungen und Kandidaten bleiben.

    Ein Lock ohne Hörarbeit fällt weg. Ein kuratierter (Aussprache, Stimme,
    Profil, Notiz, fester Seed) bleibt, wird aber als `cleared` markiert: der
    Export liefert den Clip dann nicht mehr aus, auch nicht die schon
    committete .ogg. Ein Promote macht die Markierung rückgängig.

    False, wenn es nichts aufzuheben gab.
    """
    with CURATED_FILES_LOCK:
        production = paths.audio / f"{clip.key}.wav"
        locks = Locks.load(paths.locks)
        lock = locks.get(clip.key)
        had_production = production.exists()

        if not had_production and (lock is None or lock.cleared):
            return False

        if had_production:
            production.unlink()

        if lock is not None:
            if lock.curated:
                profiles = Profiles.load(paths.profiles)
                auto = resolve_seed(clip.key, clip.profile, profiles, Locks())
                locks.set(clip.key, replace(
                    lock, seed=auto, source_text=clip.source_text, cleared=True))
            else:
                locks.remove(clip.key)
            locks.save(paths.locks)

        return True
