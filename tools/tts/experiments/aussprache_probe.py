"""Runde zwei: Aussprache fair verglichen — jede Stimme mit dem Text, der zu ihr passt.

Runde eins (`voicedesign_probe.py`) hat beiden Seiten den Rohtext gegeben und
damit `sohee` schlechter dargestellt, als es in Produktion läuft: dort tragen
221 Clips eine handoptimierte Aussprache. Der Vergleich war schief.

Die Overrides zeigen, wogegen sie geschrieben wurden. 106 von 156 hängen nur
ein Satzzeichen an — das hindert das Modell am Weiterreden. Der Rest ist fast
durchweg Akzent-Abwehr gegen das koreanische Embedding:

    Auslaut hart      Zug -> Zugk.      Weg -> Weegk.     Sack -> Sackg.
    Sp/St -> Schp     Spinne -> Schpinne.   Strand -> Schtrand.
    Qu -> Kw          Quelle -> Kwelle  Quark -> Kwark!
    Dehnung           Wal -> Wahl.      den -> dehn.      Schuh -> Schu.

Daraus folgt die eigentliche These dieser Runde: **wenn VoiceDesign deutsche
Phonologie mitbringt, wird die zweite Gruppe überflüssig — und zwar
schädlich.** `Sackg.` an eine deutsche Stimme gegeben, sagt vermutlich hörbar
„Sackg". Die Overrides mitzunehmen wäre also genauso unfair wie sie
wegzulassen. Deshalb drei Textvarianten je Wort:

    roh          "Sack"     wie im Content-Pack
    punkt        "Sack."    nur der Anti-Weiterreden-Trick
    produktion   "Sackg."   der echte Override aus locks.json

Zu prüfen ist: **VoiceDesign + `punkt` gegen sohee + `produktion`.** Kommt das
gleich raus, spart der Wechsel rund 150 handgeschriebene Umschreibungen.

Dazu zwei neue Stimmbeschreibungen. Der Befund aus Runde eins war bei allen
drei Varianten derselbe — „zu jung", `bright_de` sogar „Micky Maus" —, und das
deckt sich mit der Messung: die englisch formulierten Beschreibungen landeten
bei 300 und 352 Hz, `sohee` liegt bei 213 Hz. Die neuen zielen deshalb
ausdrücklich auf eine ältere, tiefere Stimme.

Aufruf:

    ~/qwen-tts-test/.venv/bin/python tools/tts/experiments/aussprache_probe.py

Ergebnis: `tools/tts/out/aussprache-probe/` mit `index.html`. Die Pipeline,
`profiles.json` und `locks.json` werden nur gelesen, nie geschrieben.
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

OUT = TOOL_ROOT / "out" / "aussprache-probe"
LOCKS = TOOL_ROOT / "locks.json"

SAMPLING: dict[str, object] = {
    "temperature": 0.55, "top_k": 40, "top_p": 0.8, "repetition_penalty": 1.05,
    "subtalker_temperature": 0.45, "subtalker_top_k": 50, "subtalker_top_p": 1,
}

WORD_STYLE = ("Sprich das einzelne Wort klar und freundlich, in ruhigem Tempo, "
              "mit freundlicher Betonung. Keine Übertreibung, keine Frage-Melodie.")

#: Die Instruktion des `word`-Profils **mit** Akzent-Abwehr — so läuft sohee
#: heute. Für VoiceDesign wäre sie sinnlos: dort gibt es keinen koreanischen
#: Akzent abzuwehren, die Stimme wird ja beschrieben.
SOHEE_STYLE = (WORD_STYLE + " keine lange Betonung. Keine stöhnen! Kein zischen! "
               "Kein zisch-s! kein englisch! kein koreanisch! Sprich schnell. "
               "Sprich mit tiefer Stimme.")

#: Neu in dieser Runde: ältere, tiefere Stimmen. Deutsch formuliert, weil in
#: Runde eins nur die deutsche Beschreibung sohees Lage überhaupt traf
#: (195 Hz gegen 213 Hz; die englischen lagen bei 300 und 352 Hz).
DESIGNS: dict[str, str] = {
    "erzieherin": (
        "Eine ruhige deutsche Frauenstimme mittleren Alters, Muttersprachlerin, "
        "tiefe bis mittlere Stimmlage, warm und geduldig, deutliche Aussprache, "
        "eine erfahrene Erzieherin, die einem Vorschulkind ein Wort vorspricht. "
        "Keine hohe Stimme, nicht kindlich, nicht aufgeregt."),
    "erzaehlerin": (
        "Eine warme, tiefe deutsche Frauenstimme, Muttersprachlerin, Mitte "
        "vierzig, ruhig und gelassen, klare und sorgfältige Aussprache, wie "
        "eine Hörbuchsprecherin für Kinderbücher. Tiefe Stimmlage, kein "
        "Singsang, keine Mädchenstimme."),
    # Der beste Kandidat aus Runde eins, unverändert als Vergleichspunkt.
    "warm_de_german_prompt": (
        "Eine warme, sanfte junge deutsche Frauenstimme, Muttersprachlerin, "
        "mittelhohe Stimmlage, freundlicher Klang, ruhige und deutliche "
        "Aussprache, spricht mit einem kleinen Kind."),
}

#: Wörter, an denen sich der Akzent zeigt — je eines pro Override-Muster, plus
#: die beiden, die in Runde eins gar nicht klappten (Zaun, Schuh).
#: `None` heißt: für dieses Wort gibt es keinen Override in locks.json.
WORDS: tuple[tuple[str, str], ...] = (
    ("sack", "Sack"),        # Auslaut hart      -> Sackg.
    ("schuh", "Schuh"),      # stummes h         -> Schu.
    ("spinne", "Spinne"),    # Sp -> Schp        -> Schpinne.
    ("zug", "Zug"),          # Auslaut hart      -> Zugk.
    ("quelle", "Quelle"),    # Qu -> Kw          -> Kwelle
    ("zaun", "Zaun"),        # ohne Override — in Runde eins gescheitert
)

MAX_NEW_TOKENS = 25
SEEDS = (20250830, 4711)


def production_overrides() -> dict[str, str]:
    """Die echten `textOverride`-Werte aus locks.json, nach Quelltext.

    Gelesen statt abgeschrieben: eine Kopie im Skript wäre schon beim
    nächsten Kuratieren veraltet und würde einen Vergleich vortäuschen,
    den es nicht mehr gibt.
    """
    locks = json.loads(LOCKS.read_text(encoding="utf-8"))["locks"]
    found: dict[str, str] = {}
    for lock in locks.values():
        source, override = (lock.get("sourceText") or "").strip(), lock.get("textOverride")
        if override and source and source not in found:
            found[source] = override
    return found


def variants(word: str, overrides: dict[str, str]) -> list[tuple[str, str]]:
    out = [("roh", word), ("punkt", f"{word}.")]
    real = overrides.get(word)
    if real and real not in (word, f"{word}."):
        out.append(("produktion", real))
    return out


def _load(checkpoint: str):
    import torch
    from qwen_tts import Qwen3TTSModel

    device = "mps" if torch.backends.mps.is_available() else "cpu"
    print(f"  lade {checkpoint.rsplit('/', 1)[-1]} auf {device} …", flush=True)
    started = time.time()
    model = Qwen3TTSModel.from_pretrained(
        checkpoint, device_map=device, dtype=torch.bfloat16,
        attn_implementation="sdpa")
    print(f"  geladen in {time.time() - started:.0f}s", flush=True)
    return model


def _render(fn, path: Path, seed: int, **kwargs) -> float:
    import torch

    from ttskit.audio import postprocess, write_wav

    torch.manual_seed(seed)
    wavs, sample_rate = fn(**kwargs)
    wav = postprocess(wavs[0], sample_rate, trim=True, normalize=True)
    write_wav(path, wav, sample_rate)
    return len(wav) / sample_rate


def run(rows: list[dict], overrides: dict[str, str]) -> None:
    cap = MAX_NEW_TOKENS * 0.08

    print("\n[sohee] CustomVoice — der Ist-Zustand", flush=True)
    model = _load(CUSTOM_VOICE)
    for slug, word in WORDS:
        line = []
        for variant, text in variants(word, overrides):
            for seed in SEEDS:
                path = OUT / "sohee" / variant / f"{slug}_{seed}.wav"
                dur = _render(model.generate_custom_voice, path, seed,
                              text=text, speaker="sohee", language="german",
                              instruct=SOHEE_STYLE,
                              max_new_tokens=MAX_NEW_TOKENS, **SAMPLING)
                rows.append({"voice": "sohee", "variant": variant, "slug": slug,
                             "word": word, "text": text, "seed": seed,
                             "duration": round(dur, 2), "capped": dur >= 0.9 * cap,
                             "path": str(path.relative_to(OUT))})
                line.append(f"{variant[:4]}/{seed % 10000}{'✗' if dur >= 0.9 * cap else '·'}")
        print(f"    {word:<8} {' '.join(line)}", flush=True)
    del model

    print("\n[VoiceDesign]", flush=True)
    model = _load(VOICE_DESIGN)
    for design, description in DESIGNS.items():
        print(f"\n  {design}", flush=True)
        for slug, word in WORDS:
            line = []
            for variant, text in variants(word, overrides):
                for seed in SEEDS:
                    path = OUT / design / variant / f"{slug}_{seed}.wav"
                    dur = _render(model.generate_voice_design, path, seed,
                                  text=text, instruct=f"{description} {WORD_STYLE}",
                                  language="german",
                                  max_new_tokens=MAX_NEW_TOKENS, **SAMPLING)
                    rows.append({"voice": design, "variant": variant, "slug": slug,
                                 "word": word, "text": text, "seed": seed,
                                 "duration": round(dur, 2), "capped": dur >= 0.9 * cap,
                                 "path": str(path.relative_to(OUT))})
                    line.append(f"{variant[:4]}/{seed % 10000}"
                                f"{'✗' if dur >= 0.9 * cap else '·'}")
            print(f"    {word:<8} {' '.join(line)}", flush=True)
    del model


def write_index(rows: list[dict]) -> Path:
    """Eine Tabelle je Wort: Zeilen sind Stimmen, Spalten sind Textvarianten.

    So liegt der Vergleich, auf den es ankommt, nebeneinander in einer Zeile
    (dieselbe Stimme, anderer Text) und die Alternative untereinander in einer
    Spalte (derselbe Text, andere Stimme).
    """
    voices = ["sohee"] + list(DESIGNS)
    variant_order = ["roh", "punkt", "produktion"]
    parts = [
        "<h1>Aussprache-Probe</h1>",
        "<p>Jede Stimme mit jedem Text. Der entscheidende Vergleich steht "
        "diagonal: <b>sohee / produktion</b> gegen <b>eine VoiceDesign-Stimme / "
        "punkt</b>. Kommt das gleich raus, sind rund 150 handgeschriebene "
        "Umschreibungen überflüssig.</p>",
        "<p><span class='cap'>gekappt</span> = die Aufnahme lief in "
        "<code>max_new_tokens</code>, also der Ausschuss-Fall.</p>",
    ]

    for slug, word in WORDS:
        mine = [r for r in rows if r["slug"] == slug]
        present = [v for v in variant_order if any(r["variant"] == v for r in mine)]
        texts = {r["variant"]: r["text"] for r in mine}
        parts.append(f"<h2>{html.escape(word)}</h2><table><tr><th>Stimme</th>"
                     + "".join(f"<th>{v}<br><code>{html.escape(texts[v])}</code></th>"
                               for v in present) + "</tr>")
        for voice in voices:
            cells = []
            for variant in present:
                clips = [r for r in mine if r["voice"] == voice and r["variant"] == variant]
                inner = "".join(
                    f"<div class='{'cap' if c['capped'] else ''}'>"
                    f"<audio controls preload='none' src='{html.escape(c['path'])}'></audio>"
                    f"</div>" for c in clips)
                cells.append(f"<td>{inner or '—'}</td>")
            parts.append(f"<tr><th>{html.escape(voice)}</th>{''.join(cells)}</tr>")
        parts.append("</table>")

    parts.append("<h2>Die Stimmbeschreibungen</h2><dl>")
    for name, description in DESIGNS.items():
        parts.append(f"<dt><code>{html.escape(name)}</code></dt>"
                     f"<dd>{html.escape(description)}</dd>")
    parts.append("</dl>")

    style = ("body{font:16px/1.5 system-ui;margin:2rem auto;max-width:76rem;padding:0 1rem}"
             "table{border-collapse:collapse;margin:.5rem 0 2rem;width:100%}"
             "th,td{border:1px solid #ccc;padding:.4rem .6rem;text-align:left;"
             "vertical-align:top}"
             "th code{font-weight:400;color:#666}"
             "audio{height:2rem;max-width:13rem;display:block;margin:.15rem 0}"
             ".cap audio{outline:2px solid #c0392b;border-radius:1rem}"
             "span.cap{color:#c0392b;font-weight:600}"
             "dt{margin-top:.6rem;font-weight:600}dd{margin:0 0 .2rem 1.2rem;color:#444}"
             "h2{margin-top:2rem;border-top:1px solid #eee;padding-top:1rem}")
    (OUT / "index.html").write_text(
        f"<!doctype html><meta charset='utf-8'><title>Aussprache-Probe</title>"
        f"<style>{style}</style>" + "".join(parts), encoding="utf-8")
    return OUT / "index.html"


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    overrides = production_overrides()
    print(f"{len(overrides)} Overrides aus locks.json gelesen; für diese Runde:")
    for _, word in WORDS:
        print(f"    {word:<8} -> {overrides.get(word) or '(keiner)'}")

    rows: list[dict] = []
    started = time.time()
    if len(sys.argv) > 1 and sys.argv[1] == "index":
        rows = json.loads((OUT / "rows.json").read_text(encoding="utf-8"))
    else:
        try:
            run(rows, overrides)
        finally:
            if rows:
                (OUT / "rows.json").write_text(
                    json.dumps(rows, ensure_ascii=False, indent=2), encoding="utf-8")
    if not rows:
        return 1

    index = write_index(rows)
    capped = sum(1 for r in rows if r["capped"])
    print(f"\n{len(rows)} Aufnahmen in {time.time() - started:.0f}s, "
          f"davon {capped} gekappt")
    print(f"Anhören: open {index}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
