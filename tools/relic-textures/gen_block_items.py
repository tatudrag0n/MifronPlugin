#!/usr/bin/env python3
"""Build 3D relic items from the VANILLA block model + the VANILLA texture.

Previous attempt generated solid-colour quadrants, which is why the 3D models
looked far worse than the flat sprites: no texture detail at all. The fix is to
reuse vanilla wholesale -- resolve the vanilla model's parent chain to get the
real geometry, UVs and display transforms, reuse the real block textures, and
only remap their palette onto Mifron's material ramps.

So: vanilla geometry + vanilla UV + vanilla detail, Mifron materials.

Usage: py gen_block_items.py [--only anvil,grindstone,...]
"""
import argparse
import colorsys
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

# vanilla block model -> (relic id, primary, secondary, accent)
BLOCK_ITEMS = {
    "anvil":         ("ancient_anvil", "netherite", "gold", "gold"),
    "grindstone":    ("enchgrind",     "steel",     "wood", "violet"),
    "lightning_rod": ("lightning",     "copper",    "gold", "gold"),
    "heavy_core":    ("giantcore",     "netherite", "ember", "ember"),
}

OUTLINE_MAX = 46
WARM = ((0, 55), (330, 360))
STEPS = ["dark", "base", "light", "highlight"]


def lum(p):
    return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]


def is_warm(p):
    h, l, s = colorsys.rgb_to_hls(p[0] / 255, p[1] / 255, p[2] / 255)
    if s < 0.22:
        return False
    h *= 360.0
    return any(lo <= h <= hi for lo, hi in WARM)


def quantile_cuts(vals, k):
    if not vals:
        return []
    v = sorted(vals)
    return [v[min(len(v) - 1, int(len(v) * i / k))] for i in range(1, k)]


def bucket(x, cuts):
    for i, c in enumerate(cuts):
        if x < c:
            return i
    return len(cuts)


def remap(im, primary, secondary):
    """Recolour a texture onto our ramps, keeping its detail exactly where it is."""
    im = im.convert("RGBA")
    w, h = im.size
    src = im.load()
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    dst = out.load()

    body, warm = [], []
    for y in range(h):
        for x in range(w):
            r, g, b, a = src[x, y]
            if a < 8:
                continue
            p = (r, g, b)
            L = lum(p)
            if L < OUTLINE_MAX:
                dst[x, y] = (*_rgb(P.ramp(primary, "outline")), 255)
            elif secondary and is_warm(p):
                warm.append((x, y, L))
            else:
                body.append((x, y, L))

    bc = quantile_cuts([L for _, _, L in body], len(STEPS))
    wc = quantile_cuts([L for _, _, L in warm], len(STEPS))
    for (x, y, L) in body:
        dst[x, y] = (*_rgb(P.ramp(primary, STEPS[bucket(L, bc)])), 255)
    for (x, y, L) in warm:
        dst[x, y] = (*_rgb(P.ramp(secondary, STEPS[bucket(L, wc)])), 255)
    return out


def _rgb(hexv):
    h = hexv.lstrip("#")
    return int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16)


def resolve_model(zf, path, depth=0):
    """Resolve the parent chain to the effective elements/textures/display."""
    if depth > 8:
        raise SystemExit("parent chain too deep at " + path)
    try:
        raw = zf.read(path)
    except KeyError:
        return {"elements": [], "textures": {}, "display": {}}
    m = json.loads(raw)
    parent_path = m.get("parent")
    if parent_path and parent_path.startswith("minecraft:"):
        parent_path = "assets/minecraft/models/" + parent_path.split(":", 1)[1] + ".json"
    elif parent_path and not parent_path.startswith("builtin/"):
        parent_path = "assets/minecraft/models/" + parent_path + ".json"
    else:
        parent_path = None

    base = {"elements": [], "textures": {}, "display": {}}
    if parent_path:
        base = resolve_model(zf, parent_path, depth + 1)

    textures = dict(base.get("textures", {}))
    textures.update(m.get("textures", {}))
    elements = m.get("elements", base.get("elements", []))
    display = m.get("display") or base.get("display") or {}
    gui_light = m.get("gui_light") or base.get("gui_light")
    out = {"elements": elements, "textures": textures, "display": display}
    if gui_light:
        out["gui_light"] = gui_light
    return out


def tex_path(ref):
    if ref.startswith("minecraft:"):
        ref = ref.split(":", 1)[1]
    if ref.startswith("block/"):
        return "assets/minecraft/textures/" + ref + ".png"
    return "assets/minecraft/textures/" + ref + ".png"


def build(zf, block_model, relic_id, primary, secondary, accent):
    m = resolve_model(zf, f"assets/minecraft/models/block/{block_model}.json")
    if not m["elements"]:
        print(f"  {relic_id}: no elements resolved, skipping")
        return False

    textures = {}
    used_slots = []
    for slot, ref in m["textures"].items():
        if not isinstance(ref, str):
            continue
        # '#something' is a model variable supplied by the blockstate, so there
        # is no file to read. Fall back to the block's own texture name.
        if ref.startswith("#"):
            ref = f"block/{block_model}"
        elif ":" in ref and not ref.startswith("minecraft:"):
            continue
        try:
            data = zf.read(tex_path(ref))
        except KeyError:
            print(f"  {relic_id}: texture missing {ref}")
            continue
        im = Image.open(__import__("io").BytesIO(data))
        out = remap(im, primary, secondary)
        name = f"{relic_id}_{slot}"
        out.save(os.path.join(TEXDIR, name + ".png"))
        textures[slot] = "mifron:item/relic/" + name
        used_slots.append(slot)

    if not used_slots:
        print(f"  {relic_id}: no textures written")
        return False
    if "particle" in textures:
        textures["particle"] = textures[used_slots[0]]

    model = {
        "textures": textures,
        "elements": m["elements"],
    }
    if m.get("gui_light"):
        model["gui_light"] = m["gui_light"]
    if m.get("display"):
        model["display"] = m["display"]

    with open(os.path.join(MODDIR, relic_id + ".json"), "w", encoding="utf-8") as fh:
        json.dump(model, fh, indent=2, ensure_ascii=False)
    print(f"  {relic_id}: {len(m['elements'])} elements, textures {used_slots}")
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--only")
    a = ap.parse_args()
    only = set(a.only.split(",")) if a.only else None

    with zipfile.ZipFile(JAR) as zf:
        for bm, (rid, pri, sec, acc) in BLOCK_ITEMS.items():
            if only and bm not in only:
                continue
            print("building", rid, "from", bm)
            build(zf, bm, rid, pri, sec, acc)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
