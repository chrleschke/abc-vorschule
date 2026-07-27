#!/usr/bin/env python3
"""Render every authored glyph in atoms.json to one PNG contact sheet.

Run from the repo root:  python3 tools/render_glyphs.py
Output:                  build/glyphs.png
"""
import json
import math
import os

from PIL import Image, ImageDraw

CELL = 180
PAD = 14
COLS = 6
STAR_SPACING_FRACTION = 0.28  # TraceProgress.StarSpacingFraction
MIN_STARS, MAX_STARS = 1, 10


def polyline_length(points):
    return sum(
        math.hypot(b[0] - a[0], b[1] - a[1]) for a, b in zip(points, points[1:])
    )


def star_count(length, box):
    spacing = box * STAR_SPACING_FRACTION
    if spacing <= 0:
        return MIN_STARS
    return max(MIN_STARS, min(MAX_STARS, int(length / spacing)))


def point_at(points, fraction):
    total = polyline_length(points)
    if total <= 0:
        return points[0]
    target = fraction * total
    walked = 0.0
    for a, b in zip(points, points[1:]):
        seg = math.hypot(b[0] - a[0], b[1] - a[1])
        if walked + seg >= target:
            t = 0 if seg <= 0 else (target - walked) / seg
            return (a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t)
        walked += seg
    return points[-1]


def main():
    # atoms.json is an AtomsFile object: {"atoms": [...]}, not a bare array.
    with open("app/src/main/assets/content/atoms.json", encoding="utf-8") as fh:
        atoms = json.load(fh)["atoms"]
    glyphs = [a for a in atoms if a.get("strokes")]
    rows = (len(glyphs) + COLS - 1) // COLS
    img = Image.new("RGB", (COLS * CELL, rows * CELL), (18, 20, 26))
    draw = ImageDraw.Draw(img)
    box = CELL - 2 * PAD

    for i, atom in enumerate(glyphs):
        ox = (i % COLS) * CELL + PAD
        oy = (i // COLS) * CELL + PAD
        draw.rectangle([ox, oy, ox + box, oy + box], outline=(52, 56, 66))
        draw.text((ox + 2, oy + 2), f"{atom['id']} {atom.get('display','')}", fill=(150, 155, 170))
        for stroke in atom["strokes"]:
            pts = [(ox + p[0] * box, oy + p[1] * box) for p in stroke["points"]]
            draw.line(pts, fill=(226, 220, 200), width=7, joint="curve")
            # Start of the stroke: where the child's vehicle is placed.
            draw.ellipse(
                [pts[0][0] - 5, pts[0][1] - 5, pts[0][0] + 5, pts[0][1] + 5],
                fill=(240, 120, 100),
            )
            n = star_count(polyline_length(pts), box)
            for s in range(1, n + 1):
                sx, sy = point_at(pts, s / n)
                draw.ellipse([sx - 4, sy - 4, sx + 4, sy + 4], fill=(240, 200, 90))

    os.makedirs("build", exist_ok=True)
    img.save("build/glyphs.png")
    print(f"wrote build/glyphs.png — {len(glyphs)} glyphs")


if __name__ == "__main__":
    main()
