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


def textured_quad(im, quad, tex, uv, shade, flipv=True):
    """Fill a projected parallelogram with the texture region, nearest-neighbour.

    An isometric projection of an axis-aligned rectangle is a parallelogram, so
    the inverse mapping is a 2x2 solve rather than full homography.
    """
    x1, y1, x2, y2 = [int(round(v)) for v in uv]
    x1, x2 = max(0, x1), min(tex.width, max(x2, x1 + 1))
    y1, y2 = max(0, y1), min(tex.height, max(y2, y1 + 1))

    (ax, ay), (bx, by), (cx, cy), (dx, dy) = quad
    # p00 = a, p10 = b, p01 = d  (a->b is u, a->d is v)
    e1 = (bx - ax, by - ay)
    e2 = (dx - ax, dy - ay)
    det = e1[0] * e2[1] - e1[1] * e2[0]
    if abs(det) < 1e-9:
        return
    xs = [p[0] for p in quad]
    ys = [p[1] for p in quad]
    px = tex.load()
    tw, th = tex.width, tex.height

    for sy in range(int(min(ys)), int(max(ys)) + 1):
        for sx in range(int(min(xs)), int(max(xs)) + 1):
            rx, ry = sx - ax, sy - ay
            u = (rx * e2[1] - ry * e2[0]) / det
            v = (e1[0] * ry - e1[1] * rx) / det
            if u < 0 or u > 1 or v < 0 or v > 1:
                continue
            vv = 1.0 - v if flipv else v
            tx = int(x1 + u * (x2 - x1))
            ty = int(y1 + vv * (y2 - y1))
            tx = min(tw - 1, max(0, tx))
            ty = min(th - 1, max(0, ty))
            r, g, b, a = px[tx, ty]
            if a < 8:
                continue
            im.putpixel((sx, sy), (int(r * shade), int(g * shade), int(b * shade), 255))


def render(model_path, out_path, size=420, texname=None):
    model = json.load(open(model_path, encoding="utf-8"))
    name = texname or os.path.splitext(os.path.basename(model_path))[0]

    # resolve each texture slot to an image; faces reference slots via "#slot"
    slot_imgs = {}
    for slot, ref in (model.get("textures") or {}).items():
        if not isinstance(ref, str):
            continue
        fn = ref.split("/")[-1].split(":")[-1]
        p = os.path.join(TEXDIR, fn + ".png")
        if not os.path.exists(p):
            p = os.path.join(TEXDIR, name + ".png")
        if os.path.exists(p):
            slot_imgs["#" + slot] = Image.open(p).convert("RGBA")
    if not slot_imgs:
        raise SystemExit("no textures resolved for " + model_path)
    default_img = list(slot_imgs.values())[0]

    def img_for(fd):
        t = fd.get("texture") if isinstance(fd, dict) else None
        return slot_imgs.get(t, default_img)

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
            if not uv:
                continue
            quad = [proj(p, scale, ox, oy) for p in corners(f, t)]
            textured_quad(im, quad, img_for(fd), uv, shade)
            d.line(quad + [quad[0]], fill=(18, 18, 22, 255), width=1)

    im.save(out_path)
    print("wrote", out_path, im.size, f"({len(bs)} elements)")
    return 0


if __name__ == "__main__":
    a = sys.argv[1:]
    if len(a) < 2:
        raise SystemExit(__doc__)
    raise SystemExit(render(a[0], a[1]))
