#!/usr/bin/env python3
"""Measure how the silhouette boundary is handled -- the defining Minecraft tell.

Vanilla items carry a dark edge on the shadow side (bottom/right). Textures
without it read as "vector art / mobile icon" rather than Minecraft.

Reports: share of boundary pixels that are dark, share on the shadow side
specifically, and how many steps of tone each pixel's neighbours jump by.
"""
import os
import sys

from PIL import Image

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass


def lum(p):
    return 0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]


def measure(path):
    im = Image.open(path).convert("RGBA")
    if im.size != (16, 16):
        return None
    px = im.load()

    def solid(x, y):
        return 0 <= x < 16 and 0 <= y < 16 and px[x, y][3] >= 8

    edge, shadow_edge = [], []
    for y in range(16):
        for x in range(16):
            if not solid(x, y):
                continue
            # is this pixel on the silhouette boundary?
            if not (solid(x - 1, y) and solid(x + 1, y) and solid(x, y - 1) and solid(x, y + 1)):
                edge.append(lum(px[x, y]))
                # shadow side = open to the right or below
                if not solid(x + 1, y) or not solid(x, y + 1):
                    shadow_edge.append(lum(px[x, y]))

    if not edge:
        return None
    n = len(edge)
    dark = sum(1 for v in edge if v < 70) / n
    vdark = sum(1 for v in edge if v < 45) / n
    sh = sum(1 for v in shadow_edge if v < 70) / len(shadow_edge) if shadow_edge else 0.0
    return {"edge": n, "edge_dark": dark, "edge_verydark": vdark, "shadow_dark": sh}


def agg(files, root):
    rows = [r for r in (measure(os.path.join(root, f)) for f in files) if r]
    if not rows:
        return None, 0
    out = {k: sum(r[k] for r in rows) / len(rows) for k in rows[0]}
    return out, len(rows)


def main():
    vdir, mdir = sys.argv[1], sys.argv[2]
    v, vn = agg([f for f in os.listdir(vdir) if f.endswith(".png")], vdir)
    m, mn = agg([f for f in os.listdir(mdir) if f.endswith(".png")], mdir)
    print(f"{'metric':18s} {'vanilla':>8s} {'mine':>8s} {'delta':>8s}")
    print("-" * 48)
    for k in ("edge_dark", "edge_verydark", "shadow_dark"):
        print(f"{k:18s} {v[k]:8.3f} {m[k]:8.3f} {m[k]-v[k]:+8.3f}")
    print(f"\nvanilla {vn} items, mine {mn} items")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
