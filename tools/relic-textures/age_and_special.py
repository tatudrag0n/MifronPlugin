#!/usr/bin/env python3
"""Age the flat relic sprites, and author the three special-craft item textures.

Aging:  a mild desaturate + warm shadow tint + deterministic edge wear, so the
        relics read as old but the 16x16 silhouette stays intact.
Special: derive from vanilla structure (bow / crossbow_standby / spyglass) with
        the same palette remap used for the relics, plus an accent for the lens
        or mechanism.

Writes PNGs into the resource pack, plus model/items JSON for the special items.
"""
import colorsys
import json
import os
import random
import sys

from PIL import Image

import palette as P

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass

REPO = r"C:\root\repos\MifronPlugin"
PACK = os.path.join(REPO, "resourcepack", "mifron-relics", "assets", "mifron")
RELIC_TEX = os.path.join(PACK, "textures", "item", "relic")
RELIC_MOD = os.path.join(PACK, "models", "item", "relic")
VANILLA = os.path.join(REPO, "_review", "vanilla")

STEPS = ["dark", "base", "light", "highlight"]
OUTLINE_MAX = 30
WARM = ((0, 55), (330, 360))

SPECIALS = {
    "sniper_bow":         dict(src="bow",              primary="steel",  secondary="wood", accent="lapis",  frac=0.10),
    "repeating_crossbow": dict(src="crossbow_standby", primary="steel",  secondary="wood", accent="ember",  frac=0.08),
    "night_vision_scope": dict(src="spyglass",         primary="copper", secondary="wood", accent="poison", frac=0.14),
}


def rgb(hexv):
    h = hexv.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def lum(p):
    return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]


def is_warm(p):
    h, l, s = colorsys.rgb_to_hls(p[0] / 255, p[1] / 255, p[2] / 255)
    if s < 0.22:
        return False
    return any(lo <= h * 360 <= hi for lo, hi in WARM)


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


# ----------------------------------------------------------------- aging
def age(im, seed):
    """Mild desaturate + warm shadows + edge wear. Never removes pixels."""
    im = im.convert("RGBA")
    w, h = im.size
    px = im.load()
    rnd = random.Random(seed)

    opaque = [(x, y) for y in range(h) for x in range(w) if px[x, y][3] >= 8]
    if not opaque:
        return im

    def solid(x, y):
        return 0 <= x < w and 0 <= y < h and px[x, y][3] >= 8

    # edge pixels read as chipped when darkened
    edge = [(x, y) for (x, y) in opaque
            if not (solid(x - 1, y) and solid(x + 1, y) and solid(x, y - 1) and solid(x, y + 1))]

    wear = set()
    if edge:
        n = max(2, int(len(edge) * 0.22))
        wear = set(rnd.sample(edge, min(n, len(edge))))

    for (x, y) in opaque:
        r, g, b, a = px[x, y]
        hh, ll, ss = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        # desaturate ~12%
        s2 = ss * 0.88
        # warm the shadows a touch (old metal/wood yellows)
        if ll < 0.42:
            r2, g2, b2 = colorsys.hls_to_rgb(hh, ll, s2)
            r, g, b = (min(255, int(r2 * 255) + 7),
                       min(255, int(g2 * 255) + 3),
                       min(255, int(b2 * 255) - 4))
        else:
            r2, g2, b2 = colorsys.hls_to_rgb(hh, ll, s2)
            r, g, b = int(r2 * 255), int(g2 * 255), int(b2 * 255)
        if (x, y) in wear:
            r, g, b = int(r * 0.78), int(g * 0.76), int(b * 0.74)
        px[x, y] = (max(0, min(255, r)), max(0, min(255, g)), max(0, min(255, b)), a)
    return im


# --------------------------------------------------------------- specials
def build_special(name, spec):
    src = Image.open(os.path.join(VANILLA, spec["src"] + ".png")).convert("RGBA")
    if src.size != (16, 16):
        src = src.resize((16, 16), Image.NEAREST)
    w, h = src.size
    px = src.load()

    body, warm, out = [], [], []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 8:
                continue
            p = (r, g, b)
            L = lum(p)
            if L < OUTLINE_MAX:
                out.append((x, y))
            elif is_warm(p):
                warm.append((x, y, L))
            else:
                body.append((x, y, L))

    bc = cuts([L for _, _, L in body], len(STEPS))
    wc = cuts([L for _, _, L in warm], len(STEPS))

    dst = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = dst.load()
    for (x, y) in out:
        d[x, y] = (*rgb(P.ramp(spec["primary"], "outline")), 255)

    brightest = []
    for (x, y, L) in body:
        lvl = bucket(L, bc)
        d[x, y] = (*rgb(P.ramp(spec["primary"], STEPS[lvl])), 255)
        if lvl == len(STEPS) - 1:
            brightest.append((x, y, L))
    for (x, y, L) in warm:
        d[x, y] = (*rgb(P.ramp(spec["secondary"], STEPS[bucket(L, wc)])), 255)

    if spec["accent"] and spec["frac"] > 0 and brightest:
        n = max(1, int(len(brightest) * spec["frac"]))
        for (x, y, _) in sorted(brightest, key=lambda t: -t[2])[:n]:
            d[x, y] = (*rgb(P.ramp(spec["accent"], "highlight")), 255)
    return dst


def write_special_jsons(name):
    items_dir = os.path.join(PACK, "items", "special")
    mod_dir = os.path.join(PACK, "models", "item", "special")
    os.makedirs(items_dir, exist_ok=True)
    os.makedirs(mod_dir, exist_ok=True)
    with open(os.path.join(items_dir, name + ".json"), "w", encoding="utf-8") as fh:
        json.dump({"model": {"type": "minecraft:model", "model": "mifron:item/special/" + name}},
                  fh, indent=2)
    with open(os.path.join(mod_dir, name + ".json"), "w", encoding="utf-8") as fh:
        json.dump({"parent": "minecraft:item/generated",
                   "textures": {"layer0": "mifron:item/special/" + name}}, fh, indent=2)


def main():
    # which relic textures are flat sprites (model with no elements)
    flat = []
    for f in sorted(os.listdir(RELIC_MOD)):
        if not f.endswith(".json"):
            continue
        m = json.load(open(os.path.join(RELIC_MOD, f), encoding="utf-8"))
        if not m.get("elements"):
            flat.append(f[:-5])
    print(f"flat sprites: {len(flat)}")

    aged = 0
    for name in flat:
        p = os.path.join(RELIC_TEX, name + ".png")
        if not os.path.exists(p):
            continue
        im = Image.open(p).convert("RGBA")
        if im.size != (16, 16):
            continue
        age(im, seed=name).save(p)
        aged += 1
    print(f"aged: {aged}")

    sp_dir = os.path.join(PACK, "textures", "item", "special")
    os.makedirs(sp_dir, exist_ok=True)
    for name, spec in SPECIALS.items():
        img = build_special(name, spec)
        img.save(os.path.join(sp_dir, name + ".png"))
        write_special_jsons(name)
        print(f"special: {name} <- {spec['src']}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

