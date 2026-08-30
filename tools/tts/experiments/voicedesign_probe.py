"""Taugt der VoiceDesign-Checkpoint als Ersatz für `sohee`?

Kein Teil der Pipeline — ein Versuchsaufbau, der eine einzige Frage
beantworten soll: **bleibt eine über Text beschriebene Stimme über hunderte
Clips hinweg dieselbe Person?** Die Aussprache ist das Motiv (deutsche
Einzellaute aus einem koreanischen Speaker-Embedding sind das Problem), aber
das Risiko liegt woanders. `sohee` ist ein festes Embedding: derselbe Vektor
bei jedem Clip, Identität garantiert. VoiceDesign hat keinen solchen Anker —
die Stimme entsteht bei jeder Generierung neu aus der Beschreibung. Driftet
sie zwischen zwei Clips, ist der Checkpoint für einen 900-Clip-Korpus
unbrauchbar, egal wie gut ein einzelnes „Sack" klingt.

Drei Blöcke, in der Reihenfolge ihrer Gefährlichkeit:

A  Identität über verschiedene Texte — eine Beschreibung, ein Seed, viele
   Texte. Klingt Block A nach mehreren Personen, ist hier Schluss.
B  Identität über Seeds — eine Beschreibung, ein Text, viele Seeds. Zeigt,
   wie weit der Seed die Person verschiebt und ob Seed-Locks das auffangen
   können.
C  Aussprache im direkten Vergleich — die Problemwörter, einmal `sohee` über
   CustomVoice (Ist-Zustand), einmal VoiceDesign. Erst hier geht es um das,
   weswegen die Frage überhaupt aufkam.

Aufruf:

    ~/qwen-tts-test/.venv/bin/python tools/tts/experiments/voicedesign_probe.py

Ergebnis: `tools/tts/out/voicedesign-probe/` mit WAVs und einer `index.html`
zum Durchhören. Die Pipeline, `profiles.json` und `locks.json` werden nicht
angefasst.
"""

from __future__ import annotations

import html
import json
import sys
import time
from pathlib import Path

TOOL_ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(TOOL_ROOT))

CUSTOM_VOICE = "Qwen/Qwen3-TTS-12Hz-1.7B-CustomVoice"
VOICE_DESIGN = "Qwen/Qwen3-TTS-12Hz-1.7B-VoiceDesign"

OUT = TOOL_ROOT / "out" / "voicedesign-probe"

#: Aus dem Profil `word` in profiles.json übernommen, damit der Vergleich mit
#: dem Ist-Zustand nicht an unterschiedlichen Sampling-Werten hängt.
SAMPLING: dict[str, object] = {
    "temperature": 0.55,
    "top_k": 40,
    "top_p": 0.8,
    "repetition_penalty": 1.05,
    "subtalker_temperature": 0.45,
    "subtalker_top_k": 50,
    "subtalker_top_p": 1,
}

#: Die Instruktion des `word`-Profils, aber ohne die Akzent-Abwehr
#: („kein englisch! kein koreanisch!"). Wenn VoiceDesign trägt, ist die
#: überflüssig — und ob sie überflüssig ist, will dieser Versuch wissen.
WORD_STYLE = ("Sprich das einzelne Wort klar und freundlich, in ruhigem Tempo, "
              "mit freundlicher Betonung. Keine Übertreibung, keine Frage-Melodie.")

#: Stimmbeschreibungen, die auf `sohee`s Tonlage zielen (Modellkarte: „Warm
#: Korean female voice with rich emotion") — nur eben deutsch statt koreanisch.
#: Englisch formuliert, weil die Beispiele der Modellkarte englisch sind; eine
#: deutsche Variante läuft als Kontrolle mit, um genau das zu prüfen.
DESIGNS: dict[str, str] = {
    "warm_de": (
        "A warm, gentle young German female voice, native German speaker, "
        "medium-high pitch, soft and friendly timbre, calm and clear delivery, "
        "speaking to a small child."),
    "bright_de": (
        "A bright, friendly young German female voice, native German speaker, "
        "clear articulation, medium pitch, patient and encouraging tone, "
        "like a kindergarten teacher."),
    "warm_de_german_prompt": (
        "Eine warme, sanfte junge deutsche Frauenstimme, Muttersprachlerin, "
        "mittelhohe Stimmlage, freundlicher Klang, ruhige und deutliche "
        "Aussprache, spricht mit einem kleinen Kind."),
}

#: Die Wörter, an denen sich `sohee` festfährt, plus zwei Kontrollen.
#: `max_new_tokens` wie im jeweils zuständigen Profil (1 Token = 80 ms).
TEXTS: tuple[tuple[str, str, int], ...] = (
    ("sack", "Sack", 25),
    ("sonne", "Sonne", 25),
    ("zaun", "Zaun", 25),
    ("schuh", "Schuh", 25),
    ("erdbeere", "die Erdbeere", 35),
    ("laut_m", "mmmmm", 25),
    ("laut_s", "ssssss", 25),
    ("satz", "Finde den Buchstaben M im Wort Maus.", 60),
)

#: Block A hält den Seed fest und variiert den Text — die Frage ist, ob über
#: die Texte hinweg dieselbe Person spricht.
IDENTITY_SEED = 20250830

#: Block B hält den Text fest und variiert den Seed.
IDENTITY_TEXT = ("sack", "Sack", 25)
SEEDS = (11, 4711, 20250830, 99999991, 1234567)


def _load(checkpoint: str):
    import torch
    from qwen_tts import Qwen3TTSModel

    device = "mps" if torch.backends.mps.is_available() else "cpu"
    print(f"  lade {checkpoint} auf {device} …", flush=True)
    started = time.time()
    model = Qwen3TTSModel.from_pretrained(
        checkpoint, device_map=device, dtype=torch.bfloat16,
        attn_implementation="sdpa")
    print(f"  geladen in {time.time() - started:.0f}s", flush=True)
    return model


def _render(fn, path: Path, seed: int, **kwargs) -> float:
    """Eine Generierung, seed-reproduzierbar und nachbearbeitet wie in der Pipeline."""
    # Erst hier importiert, damit `index` die Seite auch dort neu schreiben
    # kann, wo torch und soundfile nicht installiert sind. Die Aufnahmen
    # anzusehen soll nicht am Generierungs-Stack haengen — der war waehrend
    # dieser Arbeit schon einmal weg.
    import torch

    from ttskit.audio import postprocess, write_wav

    torch.manual_seed(seed)
    started = time.time()
    wavs, sample_rate = fn(**kwargs)
    took = time.time() - started
    wav = postprocess(wavs[0], sample_rate, trim=True, normalize=True)
    write_wav(path, wav, sample_rate)
    print(f"    {path.name}  {len(wav) / sample_rate:.2f}s Audio  "
          f"({took:.1f}s Rechenzeit)", flush=True)
    return took


def run_reference(rows: list[dict]) -> None:
    """Block C, erste Hälfte: der Ist-Zustand mit `sohee`."""
    print("\n[Referenz] CustomVoice / sohee — der Ist-Zustand", flush=True)
    model = _load(CUSTOM_VOICE)
    for slug, text, max_new in TEXTS:
        path = OUT / "reference" / f"{slug}.wav"
        _render(model.generate_custom_voice, path, IDENTITY_SEED,
                text=text, speaker="sohee", language="german",
                instruct=WORD_STYLE + " Sprich mit tiefer Stimme.",
                max_new_tokens=max_new, **SAMPLING)
        rows.append({"block": "C", "group": "sohee (Ist-Zustand)", "slug": slug,
                     "text": text, "seed": IDENTITY_SEED,
                     "path": str(path.relative_to(OUT))})

    # Block B braucht eine Nulllinie, sonst sagt "3,2 Halbtoene ueber fuenf
    # Seeds" nichts: dieselben Seeds, derselbe Text, festes Embedding. Was
    # hier an Streuung uebrig bleibt, macht das Sampling — nicht die
    # wechselnde Identitaet.
    slug, text, max_new = IDENTITY_TEXT
    print(f"\n  B-Referenz: sohee ueber dieselben Seeds — {text!r}", flush=True)
    for seed in SEEDS:
        path = OUT / "B_seeds_referenz" / f"{slug}_{seed}.wav"
        _render(model.generate_custom_voice, path, seed,
                text=text, speaker="sohee", language="german",
                instruct=WORD_STYLE + " Sprich mit tiefer Stimme.",
                max_new_tokens=max_new, **SAMPLING)
        rows.append({"block": "B", "group": f"sohee, Seed {seed}",
                     "slug": slug, "text": text, "seed": seed,
                     "path": str(path.relative_to(OUT))})
    del model


def run_voice_design(rows: list[dict]) -> None:
    print("\n[VoiceDesign] alle drei Blöcke", flush=True)
    model = _load(VOICE_DESIGN)

    # Block A — eine Beschreibung, ein Seed, viele Texte.
    for design, description in DESIGNS.items():
        print(f"\n  A: Identität über Texte — {design}", flush=True)
        for slug, text, max_new in TEXTS:
            path = OUT / "A_texte" / design / f"{slug}.wav"
            _render(model.generate_voice_design, path, IDENTITY_SEED,
                    text=text, instruct=f"{description} {WORD_STYLE}",
                    language="german", max_new_tokens=max_new, **SAMPLING)
            rows.append({"block": "A", "group": design, "slug": slug,
                         "text": text, "seed": IDENTITY_SEED,
                         "path": str(path.relative_to(OUT))})

    # Block B — eine Beschreibung, ein Text, viele Seeds.
    design = "warm_de"
    slug, text, max_new = IDENTITY_TEXT
    print(f"\n  B: Identität über Seeds — {design} / {text!r}", flush=True)
    for seed in SEEDS:
        path = OUT / "B_seeds" / f"{slug}_{seed}.wav"
        _render(model.generate_voice_design, path, seed,
                text=text, instruct=f"{DESIGNS[design]} {WORD_STYLE}",
                language="german", max_new_tokens=max_new, **SAMPLING)
        rows.append({"block": "B", "group": f"{design}, Seed {seed}",
                     "slug": slug, "text": text, "seed": seed,
                     "path": str(path.relative_to(OUT))})
    del model


def write_index(rows: list[dict]) -> Path:
    """Eine Seite zum Durchhören — Block A als Tabelle, weil dort der Vergleich
    zeilenweise (gleicher Text, andere Beschreibung) passiert."""
    by_block: dict[str, list[dict]] = {}
    for row in rows:
        by_block.setdefault(row["block"], []).append(row)

    def player(row: dict) -> str:
        return f'<audio controls preload="none" src="{html.escape(row["path"])}"></audio>'

    titles = {
        "A": ("A — Identität über verschiedene Texte",
              "Eine Beschreibung, ein Seed, acht Texte. Spricht in einer Spalte "
              "durchgehend dieselbe Person? Wenn nein, ist VoiceDesign für den "
              "Korpus erledigt, egal wie gut einzelne Wörter klingen."),
        "B": ("B — Identität über Seeds",
              "Eine Beschreibung, ein Text, fünf Seeds. Wie weit verschiebt der "
              "Seed die Person? Weit heißt: jeder Clip braucht ein Seed-Lock, "
              "sonst driftet der Korpus."),
        "C": ("C — Aussprache im Vergleich",
              "Der Ist-Zustand mit sohee. Gegenhören gegen die Spalte warm_de "
              "aus Block A — dasselbe Wort, derselbe Seed, dieselben "
              "Sampling-Werte."),
    }

    parts = ["<h1>VoiceDesign-Probe</h1>",
             "<p>Erzeugt von <code>tools/tts/experiments/voicedesign_probe.py</code>. "
             "Nichts davon fasst die Pipeline an.</p>"]

    # Block A: Texte als Zeilen, Beschreibungen als Spalten.
    a_rows = by_block.get("A", [])
    if a_rows:
        heading, why = titles["A"]
        groups = sorted({r["group"] for r in a_rows})
        parts.append(f"<h2>{heading}</h2><p>{why}</p><table><tr><th>Text</th>"
                     + "".join(f"<th>{html.escape(g)}</th>" for g in groups) + "</tr>")
        for slug, text, _ in TEXTS:
            cells = []
            for group in groups:
                match = next((r for r in a_rows
                              if r["slug"] == slug and r["group"] == group), None)
                cells.append(f"<td>{player(match) if match else '—'}</td>")
            parts.append(f"<tr><th>{html.escape(text)}</th>{''.join(cells)}</tr>")
        parts.append("</table>")

    for block in ("B", "C"):
        block_rows = by_block.get(block, [])
        if not block_rows:
            continue
        heading, why = titles[block]
        parts.append(f"<h2>{heading}</h2><p>{why}</p><table>"
                     "<tr><th>Text</th><th>Variante</th><th>Aufnahme</th></tr>")
        for row in block_rows:
            parts.append(f"<tr><th>{html.escape(row['text'])}</th>"
                         f"<td>{html.escape(row['group'])}</td>"
                         f"<td>{player(row)}</td></tr>")
        parts.append("</table>")

    parts.append("<h2>Die Beschreibungen</h2><dl>")
    for name, description in DESIGNS.items():
        parts.append(f"<dt><code>{html.escape(name)}</code></dt>"
                     f"<dd>{html.escape(description)}</dd>")
    parts.append("</dl>")

    style = ("body{font:16px/1.5 system-ui;margin:2rem auto;max-width:70rem;padding:0 1rem}"
             "table{border-collapse:collapse;margin:1rem 0;width:100%}"
             "th,td{border:1px solid #ccc;padding:.4rem .6rem;text-align:left;"
             "vertical-align:middle}"
             "audio{height:2rem;max-width:14rem}"
             "dt{margin-top:.6rem;font-weight:600}dd{margin:0 0 .2rem 1.2rem;color:#444}"
             "h2{margin-top:2rem}p{color:#333}")
    page = (f"<!doctype html><meta charset='utf-8'><title>VoiceDesign-Probe</title>"
            f"<style>{style}</style>" + "".join(parts))
    index = OUT / "index.html"
    index.write_text(page, encoding="utf-8")
    return index


def main() -> int:
    """Ohne Argument beide Haelften; `reference` bzw. `voicedesign` nur eine.

    Ein Teillauf wirft die andere Haelfte nicht weg — vorhandene Zeilen werden
    ueber den Pfad zusammengefuehrt, damit die Seite alles zeigt.
    """
    which = sys.argv[1] if len(sys.argv) > 1 else "both"
    if which not in ("both", "reference", "voicedesign", "index"):
        print(f"unbekannt: {which!r} — erlaubt: both, reference, voicedesign, index")
        return 2

    # `index` schreibt nur die Seite neu, ohne eine einzige Aufnahme zu
    # erzeugen. Ohne diesen Weg blieb die Seite auf dem Stand des letzten
    # Laufs stehen, waehrend rows.json laengst mehr kannte — genau so fehlte
    # Block A auf der Seite, obwohl alle 24 WAVs auf der Platte lagen.
    if which == "index":
        rows = json.loads((OUT / "rows.json").read_text(encoding="utf-8"))
        index = write_index(rows)
        print(f"{len(rows)} Aufnahmen auf der Seite\nAnhoeren: open {index}")
        return 0

    OUT.mkdir(parents=True, exist_ok=True)
    rows: list[dict] = []
    previous = OUT / "rows.json"
    if which != "both" and previous.exists():
        # Uebernommen wird, was der Lauf *nicht* neu erzeugt. Der Blockname
        # taugt dafuer nicht: Block B entsteht in beiden Haelften. Also am
        # Verzeichnis entscheiden, denn das gehoert eindeutig zu einer.
        mine = {"reference": ("reference/", "B_seeds_referenz/"),
                "voicedesign": ("A_texte/", "B_seeds/")}[which]
        rows = [r for r in json.loads(previous.read_text(encoding="utf-8"))
                if not r["path"].startswith(mine)]
        print(f"uebernehme {len(rows)} vorhandene Aufnahmen aus {previous.name}")

    started = time.time()
    try:
        if which in ("both", "reference"):
            run_reference(rows)
        if which in ("both", "voicedesign"):
            run_voice_design(rows)
    finally:
        # Auch ein abgebrochener Lauf soll anhörbar sein, was er geschafft hat.
        if rows:
            (OUT / "rows.json").write_text(
                json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
            index = write_index(rows)
            print(f"\n{len(rows)} Aufnahmen in {time.time() - started:.0f}s")
            print(f"Anhören: open {index}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
