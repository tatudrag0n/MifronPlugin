#!/usr/bin/env python3
"""Render a Java item model JSON as an isometric preview (flat-shaded faces).

Blockbench's own 3D view can't be captured reliably when its window is behind,
so shade each element face from the average colour of its UV region in the
model texture. Enough to judge whether the geometry reads as the object.

Usage: py render_model.py <model.json> <out.png> [--size 420]
"""
import json
import math
import os
import sys

from PIL import Image, ImageDraw

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass

REPO = r"C:\root\repos\MifronPlugin"
TEXDIR = os.path.join(REPO, r"resourcepack\mifron-relics\assets\mifron\textures\item\relic")

C30, S30 = math.cos(math.radians(30)), math.sin(math.radians(30))


def proj(p, scale, ox, oy):
    x, y, z = p
    sx = (x - z) * C30
    sy = -(y) + (x + z) * S30
    return (ox + sx * scale, oy + sy * scale)


def face_colour(tex, uv, shade):
    x1, y1, x2, y2 = [int(round(v)) for v in uv]
    x1, x2 = sorted((max(0, min(15, x1)), max(0, min(16, x2))))
    y1, y2 = sorted((max(0, min(15, y1)), max(0, min(16, y2))))
    if x2 <= x1 or y2 <= y1:
        return (200, 200, 200)
    px = tex.load()
    rs = gs = bs = n = 0
    for yy in range(y1, y2):
        for xx in range(x1, x2):
            r, g, b, a = px[xx, yy]
            if a < 8:
                continue
            rs += r; gs += g; bs += b; n += 1
    if not n:
        return (200, 200, 200)
    return (int(rs / n * shade), int(gs / n * shade), int(bs / n * shade))


def boxes(model):
    out = []
    for el in model.get("elements", []):
        f, t = el.get("from"), el.get("to")
        if not f or not t:
            continue
        out.append((f, t, el.get("faces", {})))
    return out


FACES = {
    "up":    ((0, 1, 0), lambda f, t: [(f[0], t[1], f[2]), (t[0], t[1], f[2]), (t[0], t[1], t[2]), (f[0], t[1], t[2])], 1.00),
    "south": ((0, 0, 1), lambda f, t: [(f[0], f[1], t[2]), (t[0], f[1], t[2]), (t[0], t[1], t[2]), (f[0], t[1], t[2])], 0.82),
    "east":  ((1, 0, 0), lambda f, t: [(t[0], f[1], f[2]), (t[0], f[1], t[2]), (t[0], t[1], t[2]), (t[0], t[1], f[2])], 0.66),
}


def render(model_path, out_path, size=420, texname=None):
    model = json.load(open(model_path, encoding="utf-8"))
    name = texname or os.path.splitext(os.path.basename(model_path))[0]
    tp = os.path.join(TEXDIR, name + ".png")
    if not os.path.exists(tp):
        raise SystemExit("texture not found: " + tp)
    tex = Image.open(tp).convert("RGBA")

    bs = boxes(model)
    if not bs:
        raise SystemExit("no elements in " + model_path)

    # fit
    pts = []
    for (f, t, _) in bs:
        for x in (f[0], t[0]):
            for y in (f[1], t[1]):
                for z in (f[2], t[2]):
                    pts.append(proj((x, y, z), 1, 0, 0))
    xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
    w = max(xs) - min(xs); h = max(ys) - min(ys)
    scale = (size - 40) / max(w, h)
    ox = 20 - min(xs) * scale
    oy = 20 - min(ys) * scale

    im = Image.new("RGBA", (size, size), (28, 28, 32, 255))
    d = ImageDraw.Draw(im)

    # painter's algorithm: farther (smaller x+y+z) first
    bs.sort(key=lambda b: b[0][0] + b[0][1] + b[0][2])

    for (f, t, faces) in bs:
        for fname, (_, corners, shade) in FACES.items():
            fd = faces.get(fname)
            if not fd:
                continue
            uv = fd.get("uv")
            col = face_colour(tex, uv, shade) if uv else (180, 180, 180)
            quad = [proj(p, scale, ox, oy) for p in corners(f, t)]
            d.polygon(quad, fill=col + (255,))
            d.line(quad + [quad[0]], fill=(20, 20, 24, 255), width=2)

    im.save(out_path)
    print("wrote", out_path, im.size, f"({len(bs)} elements)")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if len(a) < 2:
        raise SystemExit(__doc__)
    raise SystemExit(render(a[0], a[1]))
