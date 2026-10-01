#!/usr/bin/env python3
"""Regenerate the curved glyph strokes in atoms.json from exact geometry.

The trace road is drawn with lineTo between authored points, and it is wide: a
16-point O showed its corners on the outer edge of the road. Every curved stroke
is therefore described here as ellipse arcs, Bézier curves and straight runs, and
sampled densely enough (<= MAX_STEP_DEG of turn and <= MAX_CHORD per vertex) that
the polyline reads as a curve at any glyph size.

Straight-only glyphs (M, A, T, ...) and straight strokes (stems, bars, umlaut
ticks, the R leg, the Q tail) are not touched. Compound graphemes (Ch, Sch, St,
Au, ...) reuse the single letter's curve, squeezed into their x range, exactly as
the hand-authored versions did.

Run from the repo root:  python3 tools/glyph_curves.py
Then review:             python3 tools/render_glyphs.py
"""
import json
import math

ATOMS = "app/src/main/assets/content/atoms.json"

MAX_STEP_DEG = 5.0  # turn between consecutive chords
MAX_CHORD = 0.03  # glyph-box fraction
CORNER_DEG = 20.0  # a single fine step turning more than this is a real corner
DECIMALS = 4


# --- primitives --------------------------------------------------------------
# Unit space, y grows downwards. Angles in degrees, screen orientation: 0 = right,
# 90 = down, so increasing angles run clockwise on screen.


def ellipse(cx, cy, rx, ry, start, end):
    """Arc from angle [start] to [end] (either direction), finely sampled."""
    n = max(2, math.ceil(abs(end - start) * 8))
    return [
        (cx + rx * math.cos(t), cy + ry * math.sin(t))
        for t in (math.radians(start + (end - start) * i / n) for i in range(n + 1))
    ]


def bezier(p0, p1, p2, p3):
    """Cubic Bézier, finely sampled."""
    n = 1200
    out = []
    for i in range(n + 1):
        t = i / n
        u = 1 - t
        out.append((
            u**3 * p0[0] + 3 * u * u * t * p1[0] + 3 * u * t * t * p2[0] + t**3 * p3[0],
            u**3 * p0[1] + 3 * u * u * t * p1[1] + 3 * u * t * t * p2[1] + t**3 * p3[1],
        ))
    out[0], out[-1] = p0, p3
    return out


def heading(a, b):
    return math.degrees(math.atan2(b[1] - a[1], b[0] - a[0]))


def turn_between(h1, h2):
    return abs((h2 - h1 + 180) % 360 - 180)


def sample(fine):
    """Thin a finely sampled stroke to the output polyline.

    A vertex is kept where the tangent has turned MAX_STEP_DEG since the last kept
    one, where the chord would exceed MAX_CHORD, and at deliberate corners (a turn
    above CORNER_DEG within one fine step: the G spur, the ß waist). Sampling by
    tangent rather than by ellipse parameter keeps squeezed compound curves (Sch)
    as smooth as the full-width letter.
    """
    keep = [fine[0]]
    last_heading = heading(fine[0], fine[1])
    for i in range(1, len(fine) - 1):
        h_in, h_out = heading(fine[i - 1], fine[i]), heading(fine[i], fine[i + 1])
        corner = turn_between(h_in, h_out) > CORNER_DEG
        nxt = fine[i + 1]
        if (
            corner
            or turn_between(last_heading, h_out) >= MAX_STEP_DEG
            or math.dist(keep[-1], nxt) > MAX_CHORD and math.dist(fine[i], nxt) < MAX_CHORD
        ):
            keep.append(fine[i])
            last_heading = h_out
    keep.append(fine[-1])
    return keep


def line(a, b):
    return [a, b]


def join(*parts):
    """Concatenate pieces whose ends meet; the shared point is kept once."""
    out = list(parts[0])
    for part in parts[1:]:
        assert math.dist(out[-1], part[0]) < 1e-9, (out[-1], part[0])
        out.extend(part[1:])
    return out


def squeeze(pts, src, dst):
    """Map x from the single letter's range [src] into a compound's range [dst]."""
    (s0, s1), (d0, d1) = src, dst
    k = (d1 - d0) / (s1 - s0)
    return [(d0 + (x - s0) * k, y) for x, y in pts]


# --- letters -----------------------------------------------------------------


def o_loop(cx=0.5, cy=0.5, rx=0.36, ry=0.42):
    # Starts at the top and runs counter-clockwise, like the hand-written O. The
    # closing point is set to the start exactly, because TraceProgress detects a
    # closed stroke by first == last.
    pts = ellipse(cx, cy, rx, ry, -90, -450)
    pts[-1] = pts[0]
    return pts


def c_arc(x0, x_end):
    """C from the upper terminal (45°) round the left side to the lower one."""
    s45 = math.sqrt(0.5)
    rx = (x_end - x0) / (1 + s45)
    return ellipse(x0 + rx, 0.5, rx, 0.42, -45, -315)


def u_curve(x0, x1, top=0.08, bowl_top=0.62, bottom=0.91):
    cx, rx, ry = (x0 + x1) / 2, (x1 - x0) / 2, bottom - bowl_top
    return join(
        line((x0, top), (x0, bowl_top)),
        ellipse(cx, bowl_top, rx, ry, 180, 0),
        line((x1, bowl_top), (x1, top)),
    )


def bowl(stem_x, top, bottom, flat_to, rx):
    """P/B/R/D bowl: out from the stem, half-ellipse, back to the stem."""
    cy, ry = (top + bottom) / 2, (bottom - top) / 2
    return join(
        line((stem_x, top), (flat_to, top)),
        ellipse(flat_to, cy, rx, ry, -90, 90),
        line((flat_to, bottom), (stem_x, bottom)),
    )


def s_curve():
    # Upper bowl slightly narrower than the lower one, diagonal spine; tangents
    # match at every joint (top, left, spine, right, bottom).
    top, left, spine = (0.5, 0.08), (0.25, 0.29), (0.5, 0.49)
    right, bottom = (0.76, 0.71), (0.49, 0.92)
    spine_dir = (0.14, 0.04)
    return join(
        bezier((0.76, 0.19), (0.71, 0.11), (0.61, 0.08), top),
        bezier(top, (0.36, 0.08), (0.25, 0.17), left),
        bezier(left, (0.25, 0.41), (spine[0] - spine_dir[0], spine[1] - spine_dir[1]), spine),
        bezier(spine, (spine[0] + spine_dir[0] * 1.15, spine[1] + spine_dir[1] * 1.15), (0.76, 0.60), right),
        bezier(right, (0.76, 0.84), (0.64, 0.92), bottom),
        bezier(bottom, (0.36, 0.92), (0.26, 0.87), (0.22, 0.80)),
    )


S_RANGE = (0.2, 0.78)  # x extent the compound S's were squeezed from
C_RANGE = (0.16, 0.78)


def g_strokes():
    cx, cy, rx, ry = 0.5, 0.5, 0.35, 0.42
    arc = ellipse(cx, cy, rx, ry, -40, -320)  # upper terminal round to lower right
    end = arc[-1]
    # The spur stays under TraceGeometry.RefineMinChord (0.19), or refineStroke
    # would bow the deliberate corner at its foot into an arc.
    spur_top = (end[0], 0.59)
    return [
        join(arc, line(end, spur_top)),
        line(spur_top, (0.54, 0.59)),
    ]


def j_curve():
    return join(
        line((0.62, 0.1), (0.62, 0.68)),
        ellipse(0.44, 0.68, 0.18, 0.23, 0, 155),
    )


def eszett():
    stem_x = 0.28
    arch = ellipse(0.49, 0.26, 0.21, 0.18, 180, 360)  # up the stem, over the top
    into_waist = ellipse(0.53, 0.26, 0.17, 0.22, 0, 80)
    waist = into_waist[-1]
    # Cusp at the waist, then the belly out to the right and round to the bottom.
    rx, ry = 0.17, 0.21
    belly = ellipse(waist[0], waist[1] + ry, rx, ry, -90, 110)
    belly[0] = waist
    return join(line((stem_x, 0.92), (stem_x, 0.26)), arch, into_waist, belly)


# --- assembly ------------------------------------------------------------------


def curves():
    s = s_curve()
    c = c_arc(*C_RANGE)
    p = lambda stem, flat, rx: bowl(stem, 0.08, 0.52, flat, rx)  # noqa: E731
    return {
        "letter-o": {0: o_loop()},
        "letter-oe": {0: o_loop(cy=0.57, rx=0.33, ry=0.37)},
        "letter-qu": {0: o_loop(cx=0.25, rx=0.166, ry=0.42), 2: u_curve(0.627, 0.903)},
        "letter-c": {0: c},
        "letter-ch": {0: c_arc(0.09, 0.363)},
        "letter-ck": {0: c_arc(0.09, 0.363)},
        "letter-sch": {0: squeeze(s, S_RANGE, (0.056, 0.218)), 1: c_arc(0.402, 0.563)},
        "letter-s": {0: s},
        "letter-st": {0: squeeze(s, S_RANGE, (0.088, 0.343))},
        "letter-sp": {0: squeeze(s, S_RANGE, (0.088, 0.343)), 2: p(0.621, 0.75, 0.12)},
        "letter-g": dict(enumerate(g_strokes())),
        "letter-j": {0: j_curve()},
        "letter-sz": {0: eszett()},
        "letter-u": {0: u_curve(0.18, 0.82)},
        "letter-ue": {0: u_curve(0.18, 0.82, top=0.2, bowl_top=0.68, bottom=0.94)},
        "letter-au": {3: u_curve(0.614, 0.876)},
        "letter-eu": {4: u_curve(0.614, 0.876)},
        "letter-aeu": {5: u_curve(0.614, 0.876, top=0.2, bowl_top=0.68, bottom=0.94)},
        "letter-p": {1: p(0.22, 0.52, 0.24)},
        "letter-pf": {1: p(0.117, 0.225, 0.13)},
        "letter-r": {1: bowl(0.22, 0.08, 0.5, 0.5, 0.24)},
        "letter-b": {
            1: bowl(0.22, 0.08, 0.5, 0.52, 0.22),
            2: bowl(0.22, 0.5, 0.92, 0.54, 0.22),
        },
        "letter-d": {1: bowl(0.22, 0.08, 0.92, 0.40, 0.38)},
    }


def rounded(pts):
    out = [[round(x, DECIMALS), round(y, DECIMALS)] for x, y in sample(pts)]
    if pts[0] == pts[-1]:
        out[-1] = out[0]
    return out


def main():
    with open(ATOMS, encoding="utf-8") as fh:
        data = json.load(fh)
    by_id = {a["id"]: a for a in data["atoms"]}
    table = curves()
    for atom_id, strokes in table.items():
        atom = by_id[atom_id]
        for index, pts in strokes.items():
            old = atom["strokes"][index]["points"]
            new = rounded(pts)
            moved = math.dist(old[0], new[0])
            if moved > 0.02:
                print(f"note: {atom_id}[{index}] start moved by {moved:.3f}")
            atom["strokes"][index]["points"] = new
    with open(ATOMS, "w", encoding="utf-8") as fh:
        fh.write(json.dumps(data, indent=2, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
