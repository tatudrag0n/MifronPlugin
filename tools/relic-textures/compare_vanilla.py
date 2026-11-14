#!/usr/bin/env python3
"""Compare relic textures against real vanilla Minecraft item textures.

The question "does it look like Minecraft?" is measurable along a few axes:
palette size, saturation, brightness, how hard the ramp steps are, and whether
the silhouette is carried by a dark outline.

Usage: py compare_vanilla.py <vanilla-dir> <mine-dir>
"""
import colorsys
import os
import sys
from collections import Counter

from PIL import Image

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass


def stats(path):
    im = Image.open(path).convert("RGBA")
    if im.size != (16, 16):
        return None
    px = list(im.getdata())
    solid = [(r, g, b) for (r, g, b, a) in px if a >= 8]
    if len(solid) < 12:
        return None

    hues, sats, vals, lums = [], [], [], []
    for (r, g, b) in solid:
        h, l, s = colorsys.rgb_to_hls(r / 255, g / 255, b / 255)
        hues.append(h * 360)
        sats.append(s)
        vals.append(max(r, g, b) / 255)
        lums.append(0.2126 * r + 0.7152 * g + 0.0722 * b)

    n = len(solid)
    mean_lum = sum(lums) / n
    # hard ramp: share of pixels near the 2 extreme ends vs middle
    lo = sum(1 for v in lums if v < mean_lum - 25)
    hi = sum(1 for v in lums if v > mean_lum + 25)
    mid = n - lo - hi
    return {
        "colours": len(set(solid)),
        "sat": sum(sats) / n,
        "val": sum(vals) / n,
        "lum": mean_lum,
        "dark_frac": sum(1 for v in lums if v < 60) / n,
        "mid_frac": mid / n,
        "cov": n / 256.0,
        # luminance spread across the item
        "spread": (max(lums) - min(lums)) / 255.0,
    }


def agg(files, root):
    rows = []
    for f in files:
        s = stats(os.path.join(root, f))
        if s:
            rows.append(s)
    if not rows:
        return None, 0
    keys = rows[0].keys()
    out = {k: sum(r[k] for r in rows) / len(rows) for k in keys}
    return out, len(rows)


def main():
    vdir, mdir = sys.argv[1], sys.argv[2]
    vfiles = [f for f in os.listdir(vdir) if f.endswith(".png")]
    mfiles = [f for f in os.listdir(mdir) if f.endswith(".png")]

    v, vn = agg(vfiles, vdir)
    m, mn = agg(mfiles, mdir)
    if not v or not m:
        print("not enough 16x16 samples", vn, mn)
        return 1

    print(f"{'metric':22s} {'vanilla':>9s} {'mine':>9s} {'delta':>9s}  meaning")
    print("-" * 78)
    rows = [
        ("colours", "色数。多いほど滑らかな陰影＝非マイクラ"),
        ("sat", "彩度。低いと濁って見える"),
        ("val", "明度(最大成分)"),
        ("lum", "輝度平均"),
        ("dark_frac", "暗い画素の割合(輪郭/影)"),
        ("mid_frac", "中間調の割合。高いと『のっぺり』"),
        ("spread", "明暗の振れ幅。小さいと平坦"),
        ("cov", "16x16 のうち不透明な割合"),
    ]
    for k, note in rows:
        d = m[k] - v[k]
        flag = ""
        if k == "colours" and d > 2:
            flag = " ⚠多すぎ"
        if k == "sat" and d < -0.08:
            flag = " ⚠彩度低い"
        if k == "lum" and abs(d) > 18:
            flag = " ⚠明るさ乖離"
        if k == "mid_frac" and d > 0.10:
            flag = " ⚠中間調多すぎ"
        if k == "spread" and d < -0.05:
            flag = " ⚠平坦"
        print(f"{k:22s} {v[k]:9.3f} {m[k]:9.3f} {d:+9.3f}  {note}{flag}")
    print(f"\nvanilla samples: {vn}   mine: {mn}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
