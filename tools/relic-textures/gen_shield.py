#!/usr/bin/env python3
"""Build the relic shield (reflect) as a 3D item from vanilla textures.

Vanilla shields are drawn by a special renderer (models/item/shield.json is
builtin/entity with no elements), so there is no vanilla geometry to inherit.
Instead take the display transforms from the vanilla shield model and build a
tapered plate, reusing vanilla block textures remapped to Mifron materials:
dark oak planks -> leather, iron block -> steel.
"""
import io
import json
import os
import sys
import zipfile

from PIL import Image

import palette as P

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass

REPO = r"C:\root\repos\MifronPlugin"
JAR = os.path.expandvars(r"%APPDATA%\.minecraft\versions\26.1.2\26.1.2.jar")
MODDIR = os.path.join(REPO, r"resourcepack\mifron-relics\assets\mifron\models\item\relic")
TEXDIR = os.path.join(REPO, r"resourcepack\mifron-relics\assets\mifron\textures\item\relic")

ID = "reflect"
WOOD_SRC = "assets/minecraft/textures/block/dark_oak_planks.png"
IRON_SRC = "assets/minecraft/textures/block/iron_block.png"

DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, 90, 0], "translation": [10, 6, -4], "scale": [1, 1, 1]},
    "thirdperson_lefthand": {"rotation": [0, 90, 0], "translation": [10, 6, 12], "scale": [1, 1, 1]},
    "firstperson_righthand": {"rotation": [0, 180, 5], "translation": [-10, 1.75, -10], "scale": [1.25, 1.25, 1.25]},
    "firstperson_lefthand": {"rotation": [0, 180, 5], "translation": [10, 0, -10], "scale": [1.25, 1.25, 1.25]},
    "gui": {"rotation": [15, -25, -5], "translation": [2, 3, 0], "scale": [0.65, 0.65, 0.65]},
    "fixed": {"rotation": [0, 180, 0], "translation": [-4.5, 4.5, -5], "scale": [0.55, 0.55, 0.55]},
    "ground": {"rotation": [0, 0, 0], "translation": [2, 4, 2], "scale": [0.25, 0.25, 0.25]},
}


def remap(im, primary, secondary):
    """Same palette mapping as gen_block_items: keep detail, swap materials."""
    import colorsys
    im = im.convert("RGBA")
    w, h = im.size
    src = im.load()
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dst = out.load()
    steps = ["dark", "base", "light", "highlight"]

    def L(p):
        return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]

    body, warm = [], []
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a < 8:
                continue
            p = (r, g, b)
            v = L(p)
            if v < 46:
                hx = P.ramp(primary, "outline")
                dst[x, y] = (*_rgb(hx), 255)
                continue
            hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
            warmflag = ss >= 0.22 and (hh * 360 <= 55 or hh * 360 >= 330)
            (warm if warmflag else body).append((x, y, v))

    def cuts(vals, k):
        if not vals:
            return []
        v = sorted(vals)
        return [v[min(len(v) - 1, int(len(v) * i / k))] for i in range(1, k)]

    def bucket(x, c):
        for i, cc in enumerate(c):
            if x < cc:
                return i
        return len(c)

    for grp, mat in ((body, primary), (warm, secondary or primary)):
        c = cuts([v for _, _, v in grp], len(steps))
        for (x, y, v) in grp:
            dst[x, y] = (*_rgb(P.ramp(mat, steps[bucket(v, c)])), 255)
    return out


def _rgb(hexv):
    h = hexv.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def box(x1, y1, z1, x2, y2, z2, tex, uv=None):
    uv = uv or [0, 0, 16, 16]
    faces = {f: {"uv": uv, "texture": tex} for f in
             ("north", "east", "south", "west", "up", "down")}
    return {"from": [x1, y1, z1], "to": [x2, y2, z2], "faces": faces}


def main():
    with zipfile.ZipFile(JAR) as zf:
        wood = Image.open(io.BytesIO(zf.read(WOOD_SRC)))
        iron = Image.open(io.BytesIO(zf.read(IRON_SRC)))

    w = remap(wood, "leather", "wood")
    i = remap(iron, "steel", "steel")
    w.save(os.path.join(TEXDIR, ID + "_face.png"))
    i.save(os.path.join(TEXDIR, ID + "_boss.png"))

    elements = [
        box(3, 8, 7, 13, 15, 9, "#face"),
        box(4, 4, 7, 12, 8, 9, "#face"),
        box(6, 0, 7, 10, 4, 9, "#face"),
        box(7, 7, 6, 9, 10, 7, "#boss"),
    ]
    model = {
        "textures": {
            "face": "mifron:item/relic/" + ID + "_face",
            "boss": "mifron:item/relic/" + ID + "_boss",
            "particle": "mifron:item/relic/" + ID + "_face",
        },
        "elements": elements,
        "gui_light": "front",
        "display": DISPLAY,
    }
    with open(os.path.join(MODDIR, ID + ".json"), "w", encoding="utf-8") as fh:
        json.dump(model, fh, indent=2, ensure_ascii=False)
    print(f"wrote {ID}.json ({len(elements)} elements) + {ID}_face/_boss.png")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
