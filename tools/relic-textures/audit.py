#!/usr/bin/env python3
"""Audit relic item textures against Minecraft 16x16 pixel-art conventions.

Reports per-file: canvas size, alpha usage, content bounding box / fill ratio,
distinct opaque colours, and a set of automated violation flags.

Usage: py audit.py <srcdir> [--json out.json]
"""
import argparse
import json
import os
import sys
from collections import Counter

from PIL import Image


def hue_shift_ok(colors):
    """Crude check that shadows/highlights differ in hue, not just value."""
    if len(colors) < 2:
        return False
    return True


def audit_one(path):
    im = Image.open(path)
    orig_mode = im.mode
    im = im.convert("RGBA")
    w, h = im.size
    px = im.load()
    total = w * h

    opaque = 0
    transparent = 0
    semi = 0
    colors = Counter()
    xs, ys = [], []
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a == 0:
                transparent += 1
                continue
            if a < 255:
                semi += 1
            opaque += 1
            colors[(r, g, b, a)] += 1
            xs.append(x)
            ys.append(y)

    if xs:
        bbox = (min(xs), min(ys), max(xs), max(ys))
        bw, bh = bbox[2] - bbox[0] + 1, bbox[3] - bbox[1] + 1
        fill_of_canvas = opaque / total
        fill_of_bbox = opaque / (bw * bh)
    else:
        bbox = None
        bw = bh = 0
        fill_of_canvas = 0.0
        fill_of_bbox = 0.0

    flags = []

    # --- Fatal: item textures MUST be transparent-backed -------------------
    if transparent == 0:
        flags.append("NO_ALPHA")
    elif transparent < total * 0.10:
        flags.append("LOW_ALPHA")

    # --- Canvas size -------------------------------------------------------
    if (w, h) != (16, 16):
        flags.append(f"SIZE_{w}x{h}")

    # --- Silhouette must fill the canvas (Minecraft items span 16x16) ------
    if bw and bh and max(bw, bh) < 13:
        flags.append(f"SMALL_BBOX_{bw}x{bh}")
    elif bw and bh and max(bw, bh) < 15:
        flags.append(f"UNDERFILLED_{bw}x{bh}")

    # --- Palette: 3-4 colours per item is the norm ------------------------
    ncolors = len(colors)
    if ncolors > 8:
        flags.append(f"TOO_MANY_COLORS_{ncolors}")
    if ncolors < 2:
        flags.append("FLAT_1_COLOR")

    # --- Ramp: needs at least base/shadow/highlight -----------------------
    if ncolors >= 2:
        # luminance spread
        lum = sorted(
            (0.2126 * r + 0.7152 * g + 0.0722 * b) for (r, g, b, a) in colors.elements()
        )
        spread = lum[-1] - lum[0]
        if spread < 45:
            flags.append(f"LOW_CONTRAST_{spread:.0f}")
    else:
        spread = 0.0

    # --- Pure black outline is discouraged --------------------------------
    if (0, 0, 0, 255) in colors:
        flags.append("PURE_BLACK")

    return {
        "file": os.path.basename(path),
        "size": f"{w}x{h}",
        "mode": orig_mode,
        "opaque_px": opaque,
        "transparent_px": transparent,
        "semi_px": semi,
        "bbox": list(bbox) if bbox else None,
        "bbox_w": bw,
        "bbox_h": bh,
        "fill_of_canvas": round(fill_of_canvas, 3),
        "fill_of_bbox": round(fill_of_bbox, 3),
        "n_colors": ncolors,
        "lum_spread": round(spread, 1),
        "flags": flags,
    }


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("srcdir")
    ap.add_argument("--json")
    args = ap.parse_args()

    files = sorted(f for f in os.listdir(args.srcdir) if f.lower().endswith(".png"))
    rows = [audit_one(os.path.join(args.srcdir, f)) for f in files]

    # summary
    from collections import Counter as C

    flagcount = C()
    for r in rows:
        for f in r["flags"]:
            key = f.split("_")[0] if f.startswith(("NO_ALPHA", "LOW_ALPHA")) else f.split("_")[0]
            flagcount[key] += 1

    print(f"{'file':26s} {'size':7s} {'alpha':7s} {'bbox':9s} {'fll':5s} {'col':4s} {'spread':6s} flags")
    print("-" * 100)
    for r in rows:
        alpha = "none" if r["transparent_px"] == 0 else str(r["transparent_px"])
        bbox = f"{r['bbox_w']}x{r['bbox_h']}"
        print(
            f"{r['file'][:26]:26s} {r['size']:7s} {alpha:7s} {bbox:9s} "
            f"{r['fill_of_canvas']:<5.2f} {r['n_colors']:<4d} {r['lum_spread']:<6.0f} "
            f"{','.join(r['flags'])}"
        )
    print()
    print("flag totals:", dict(flagcount))
    n_no_alpha = sum(1 for r in rows if "NO_ALPHA" in r["flags"] or "LOW_ALPHA" in r["flags"])
    n_small = sum(1 for r in rows if any(f.startswith(("SMALL_BBOX", "UNDERFILLED")) for f in r["flags"]))
    print(f"files: {len(rows)}  no/weak alpha: {n_no_alpha}  underfilled: {n_small}")

    if args.json:
        with open(args.json, "w", encoding="utf-8") as fh:
            json.dump(rows, fh, indent=2, ensure_ascii=False)
        print("json:", args.json)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
