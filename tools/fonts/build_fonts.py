#!/usr/bin/env python3
"""Baut die gebündelten Schriften der App (PRODUCT_PRINCIPLES §10, „Schrift").

Quellen (alle SIL Open Font License 1.1) — einmal herunterladen, nicht ins Repo legen:

  Andika Regular/Bold  https://github.com/google/fonts/raw/main/ofl/andika/Andika-Regular.ttf
                       https://github.com/google/fonts/raw/main/ofl/andika/Andika-Bold.ttf
  Baloo 2 (variabel)   https://github.com/google/fonts/raw/main/ofl/baloo2/Baloo2%5Bwght%5D.ttf
  Noto Color Emoji     https://github.com/googlefonts/noto-emoji/raw/main/2D/fonts/NotoColorEmoji.ttf
                       (CBDT-Bitmaps — die COLRv1-Fassung rendert erst ab Android 13)

Braucht fontTools (`pip install fonttools`). Aufruf:

  python3 tools/fonts/build_fonts.py \\
      --andika-regular Andika-Regular.ttf --andika-bold Andika-Bold.ttf \\
      --baloo Baloo2[wght].ttf --emoji NotoColorEmoji.ttf

Ergebnis unter app/src/main/res/font/:

  silbo_fibel_regular.ttf / silbo_fibel_bold.ttf
      Andika, gekürzt auf Latin, **umbenannt** in „Silbo Fibel": Andika trägt die
      Reserved Font Names „Andika" und „SIL", und eine veränderte Fassung darf sie nach
      der OFL nicht weiterführen. Verändert ist neben dem Kürzen genau ein Zeichen: das
      große **I** ist ein schlichter Strich wie in der deutschen Fibel und im
      Spurensucher, statt Andikas I mit Querstrichen. Unterscheidbar vom kleinen l bleibt
      es trotzdem — das l hat in Andika einen Bogen am Fuß.
  baloo2.ttf
      Baloo 2, gekürzt auf Latin, Name unverändert (keine Reserved Font Names).
  silbo_emoji.ttf
      Noto Color Emoji, gekürzt auf genau die Emojis, die der Content-Pack zeigt, plus
      [CODE_EMOJI]. Ein neues Emoji im Pack ohne Neubau fällt auf die System-Schrift
      zurück — `EmojiFontCoverageTest` meldet das.

Das Skript prüft am Ende, dass jedes Nicht-Emoji-Zeichen des Packs in Silbo Fibel liegt.
"""

import argparse
import glob
import json
import os
import sys
import unicodedata

from fontTools import subset
from fontTools.pens.ttGlyphPen import TTGlyphPen
from fontTools.ttLib import TTFont

ROOT = os.path.normpath(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", ".."))
CONTENT = os.path.join(ROOT, "app", "src", "main", "assets", "content")
FONT_OUT = os.path.join(ROOT, "app", "src", "main", "res", "font")
EMOJI_LIST = os.path.join(ROOT, "tools", "fonts", "emoji-glyphs.txt")

LATIN = "U+0020-007E,U+00A0-00FF,U+1E9E,U+2013-2014,U+2018-201E,U+2022,U+2026,U+2212,U+00D7,U+00F7"

# Emojis, die der Code selbst zeichnet statt der Pack (Platzhalter, Tests, Schloss).
CODE_EMOJI = "❄🍎🐻🔒"


def content_text():
    return "".join(open(f, encoding="utf-8").read() for f in sorted(glob.glob(os.path.join(CONTENT, "*.json"))))


def is_emoji_char(c):
    o = ord(c)
    return o in (0x200D, 0xFE0F, 0x20E3) or 0x1F000 <= o <= 0x1FAFF or 0x2600 <= o <= 0x27BF or \
        0x2300 <= o <= 0x23FF or 0x2B00 <= o <= 0x2BFF or 0x1F3FB <= o <= 0x1F3FF or o == 0x00A9 or o == 0x00AE


def plain_capital_i(font, stem_left, stem_right, side_bearing):
    """Ersetzt das große I durch einen schlichten Strich in Stammbreite und rückt die
    Akzent-Komposita (Í, Î, …) auf die neue Mitte."""
    glyf, hmtx = font["glyf"], font["hmtx"]
    cap = 1460
    old_adv, _ = hmtx["I"]
    width = stem_right - stem_left
    new_adv = width + 2 * side_bearing
    pen = TTGlyphPen(font.getGlyphSet())
    pen.moveTo((side_bearing, 0))
    pen.lineTo((side_bearing, cap))
    pen.lineTo((side_bearing + width, cap))
    pen.lineTo((side_bearing + width, 0))
    pen.closePath()
    glyph = pen.glyph()
    glyph.recalcBounds(glyf)
    glyf["I"] = glyph
    hmtx["I"] = (new_adv, side_bearing)
    shift = (old_adv - new_adv) / 2
    for name in font.getGlyphOrder():
        g = glyf[name]
        if not g.isComposite() or not any(c.glyphName == "I" for c in g.components):
            continue
        for c in g.components:
            if c.glyphName != "I":
                c.x = int(round(c.x - shift))
        g.recalcBounds(glyf)
        hmtx[name] = (new_adv, g.xMin)
    return new_adv


def rename(font, family, style):
    ps = family.replace(" ", "") + "-" + style
    note = " Modified for Silbo: Latin subset, plain capital I. Derived from Andika (SIL OFL 1.1)."
    for rec in font["name"].names:
        if rec.nameID in (1, 16):
            rec.string = family
        elif rec.nameID in (2, 17):
            rec.string = style
        elif rec.nameID == 3:
            rec.string = f"{ps};Silbo"
        elif rec.nameID == 4:
            rec.string = f"{family} {style}"
        elif rec.nameID == 6:
            rec.string = ps
        elif rec.nameID == 5:
            rec.string = rec.toUnicode() + note
    # Marken- und Herstellerangaben der Vorlage passen auf die Ableitung nicht mehr.
    font["name"].names = [r for r in font["name"].names if r.nameID not in (7, 8, 9, 11, 12, 18, 19, 21, 22)]


def subset_font(src, dst, unicodes=None, text=None, keep_names=False):
    opts = subset.Options()
    opts.layout_features = ["*"]
    opts.name_IDs = ["*"]
    opts.name_languages = ["*"]
    opts.notdef_outline = True
    opts.glyph_names = True
    font = subset.load_font(src, opts)
    s = subset.Subsetter(opts)
    s.populate(unicodes=subset.parse_unicodes(unicodes) if unicodes else [], text=text or "")
    s.subset(font)
    return font


def tighten_line_metrics(font):
    """Zeilenhöhe auf das, was nach dem Kürzen noch drin ist.

    Andika reserviert Platz für gestapelte vietnamesische Akzente (Ober-/Unterlänge
    1,22 / 0,39 em — eine Zeile 1,61 em hoch, Roboto liegt bei ~1,17). In Latin reicht
    der höchste Glyph (Å, Ú) bis ~1,01 em und das g bis −0,25 em. Mit den Originalwerten
    wären alle Texte der App gut ein Drittel höher geworden, Kacheln und Pegs mit ihnen.
    """
    glyf = font["glyf"]
    boxes = [glyf[n] for n in font.getGlyphOrder() if glyf[n].numberOfContours != 0 and hasattr(glyf[n], "yMax")]
    ascent = max(g.yMax for g in boxes) + 10
    descent = min(g.yMin for g in boxes) - 10
    hhea, os2 = font["hhea"], font["OS/2"]
    hhea.ascent, hhea.descent, hhea.lineGap = ascent, descent, 0
    os2.sTypoAscender, os2.sTypoDescender, os2.sTypoLineGap = ascent, descent, 0
    os2.usWinAscent, os2.usWinDescent = ascent, -descent


def build_fibel(src, dst, style, stem, bearing):
    font = subset_font(src, dst, unicodes=LATIN)
    plain_capital_i(font, stem[0], stem[1], bearing)
    tighten_line_metrics(font)
    rename(font, "Silbo Fibel", style)
    font.save(dst)
    return font


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--andika-regular", required=True)
    ap.add_argument("--andika-bold", required=True)
    ap.add_argument("--baloo", required=True)
    ap.add_argument("--emoji", required=True)
    a = ap.parse_args()
    os.makedirs(FONT_OUT, exist_ok=True)

    # Stammbreite aus Andikas eigenem I (Regular 380–570, Bold 370–655), Vorbreite wie
    # beim H, damit das I zwischen Großbuchstaben nicht gedrängt steht.
    regular = build_fibel(a.andika_regular, os.path.join(FONT_OUT, "silbo_fibel_regular.ttf"), "Regular", (380, 570), 160)
    build_fibel(a.andika_bold, os.path.join(FONT_OUT, "silbo_fibel_bold.ttf"), "Bold", (370, 655), 150)

    baloo = subset_font(a.baloo, None, unicodes=LATIN)
    baloo.save(os.path.join(FONT_OUT, "baloo2.ttf"))

    text = content_text()
    emoji = "".join(sorted({c for c in text if is_emoji_char(c)} | set(CODE_EMOJI)))
    os.makedirs(os.path.dirname(EMOJI_LIST), exist_ok=True)
    emoji_font = subset_font(a.emoji, None, text=emoji)
    emoji_font.save(os.path.join(FONT_OUT, "silbo_emoji.ttf"))
    # Welche Codepoints die Emoji-Schrift trägt — gelesen von EmojiFontCoverageTest.
    with open(EMOJI_LIST, "w", encoding="utf-8") as f:
        f.write("# Erzeugt von tools/fonts/build_fonts.py — nicht von Hand ändern.\n")
        for c in emoji:
            if ord(c) in (0x200D, 0xFE0F, 0x20E3) or 0x1F3FB <= ord(c) <= 0x1F3FF:
                continue
            f.write(f"{ord(c):X}\n")

    cmap = regular.getBestCmap()
    letters = {c for c in text if not is_emoji_char(c) and unicodedata.category(c)[0] in "LNPSZ" and c not in "\n\t"}
    missing = sorted(c for c in letters if ord(c) not in cmap)
    for name in ("silbo_fibel_regular.ttf", "silbo_fibel_bold.ttf", "baloo2.ttf", "silbo_emoji.ttf"):
        print(f"{name:26s} {os.path.getsize(os.path.join(FONT_OUT, name)):8d} B")
    if missing:
        print("Zeichen des Packs fehlen in Silbo Fibel:", " ".join(f"{c} U+{ord(c):04X}" for c in missing))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
