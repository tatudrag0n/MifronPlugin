#!/usr/bin/env python3
"""Derive a relic texture from a vanilla item's STRUCTURE.

The user's call: trace vanilla structure. So we inherit from the vanilla sprite

  - the silhouette (alpha mask), pixel for pixel
  - the light/shade structure (which pixels are lit, which are shadowed)
  - the outline position

and replace

  - the material (steel -> rusted iron, paper -> leather, ...)
  - the hue (vanilla's hue is thrown away; our ramp's hue is used, with the
    shadow-goes-violet / highlight-goes-warm shift baked into palette.py)
  - a secondary material for the warm (wood/leather) parts, so a blade and its
    wooden grip stay visually separate instead of collapsing into one metal

Accents (gems / ember / rarity colour) are placed on the brightest lit pixels so
they read as glow rather than as stickers.

Usage:
  py trace.py --source iron_sword --primary rusted --secondary wood \
              --accent ember --out out/decayed_sword.png
  py trace.py --batch trace-map.txt --outdir out/
"""
import argparse
import colorsys
import os
import sys

from PIL import Image

import palette as P

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass

# luminance below this is treated as the vanilla outline (#181818-ish)
OUTLINE_MAX = 46
# warm hue window: wood, leather, gold, rust
WARM = ((0, 55), (330, 360))


def lum(p):
    return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]


def hue_sat(p):
    h, l, s = colorsys.rgb_to_hls(p[0] / 255, p[1] / 255, p[2] / 255)
    return h * 360.0, s


def is_warm(p):
    h, s = hue_sat(p)
    if s < 0.22:
        return False
    return any(lo <= h <= hi for lo, hi in WARM)


def quantile_levels(values, k):
    """Return k-1 cut points so each band holds a comparable share of pixels."""
    if not values:
        return []
    v = sorted(values)
    cuts = []
    n = len(v)
    for i in range(1, k):
        cuts.append(v[min(n - 1, int(n * i / k))])
    return cuts


def bucket(x, cuts):
    for i, c in enumerate(cuts):
        if x < c:
            return i
    return len(cuts)


def trace(src_path, primary, secondary, accent, accent_frac=0.0):
    im = Image.open(src_path).convert("RGBA")
    if im.size != (16, 16):
        raise SystemExit(f"{src_path}: expected 16x16, got {im.size}")
    src = im.load()

    body, warm = [], []
    outline = []
    for y in range(16):
        for x in range(16):
            r, g, b, a = src[x, y]
            if a < 8:
                continue
            p = (r, g, b)
            L = lum(p)
            if L < OUTLINE_MAX:
                outline.append((x, y))
            elif secondary and is_warm(p):
                warm.append((x, y, L))
            else:
                body.append((x, y, L))

    # 4 tone steps per material: dark / base / light / highlight
    steps = ["dark", "base", "light", "highlight"]
    body_cuts = quantile_levels([L for _, _, L in body], len(steps))
    warm_cuts = quantile_levels([L for _, _, L in warm], len(steps))

    out = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    dst = out.load()

    def put(x, y, hexv):
        h = hexv.lstrip("#")
        dst[x, y] = (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)

    for (x, y) in outline:
        put(x, y, P.ramp(primary, "outline"))

    brightest = []
    for (x, y, L) in body:
        lvl = bucket(L, body_cuts)
        put(x, y, P.ramp(primary, steps[lvl]))
        if lvl == len(steps) - 1:
            brightest.append((x, y, L))

    for (x, y, L) in warm:
        lvl = bucket(L, warm_cuts)
        put(x, y, P.ramp(secondary, steps[lvl]))

    # accent on the hottest lit pixels of the main body, so it reads as glow
    if accent and accent_frac > 0:
        n = max(1, int(len(brightest) * accent_frac))
        for (x, y, _) in sorted(brightest, key=lambda t: -t[2])[:n]:
            put(x, y, P.ramp(accent, "highlight"))
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--source")
    ap.add_argument("--primary")
    ap.add_argument("--secondary")
    ap.add_argument("--accent")
    ap.add_argument("--accent-frac", type=float, default=0.0)
    ap.add_argument("--vanilla-dir", default=r"C:\root\repos\MifronPlugin\_review\vanilla")
    ap.add_argument("--out")
    ap.add_argument("--batch", help="map file: id|source|primary|secondary|accent|frac")
    ap.add_argument("--outdir")
    a = ap.parse_args()

    if a.batch:
        if not a.outdir:
            raise SystemExit("--batch needs --outdir")
        os.makedirs(a.outdir, exist_ok=True)
        ok = miss = 0
        with open(a.batch, encoding="utf-8") as fh:
            for raw in fh:
                line = raw.strip()
                if not line or line.startswith("#"):
                    continue
                parts = [p.strip() for p in line.split("|")]
                if len(parts) < 5:
                    print("  bad line:", line)
                    continue
                rid, src, pri, sec, acc = parts[:5]
                frac = float(parts[5]) if len(parts) > 5 and parts[5] else 0.0
                p = os.path.join(a.vanilla_dir, src + ".png")
                if not os.path.exists(p):
                    print(f"  MISSING source for {rid}: {src}")
                    miss += 1
                    continue
                img = trace(p, pri, sec or None, acc or None, frac)
                img.save(os.path.join(a.outdir, rid + ".png"))
                ok += 1
        print(f"traced {ok}, missing {miss}")
        return 0

    if not a.source or not a.out:
        raise SystemExit("need --source and --out, or --batch/--outdir")
    p = os.path.join(a.vanilla_dir, a.source + ".png")
    if not os.path.exists(p):
        raise SystemExit("missing vanilla source: " + p)
    img = trace(p, a.primary, a.secondary, a.accent, a.accent_frac)
    os.makedirs(os.path.dirname(a.out) or ".", exist_ok=True)
    img.save(a.out)
    print("wrote", a.out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
