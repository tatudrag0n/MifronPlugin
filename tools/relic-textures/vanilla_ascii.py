#!/usr/bin/env python3
"""Dump a 16x16 vanilla (or any) item texture as readable ASCII, so its
proportions can be studied when designing a relic silhouette.

Each distinct colour becomes a single character, darkest -> lightest.
A legend maps characters back to hex. Transparent is '.'.

Usage:
    py vanilla_ascii.py <assets/item dir> iron_sword totem_of_undying ...
    py vanilla_ascii.py <dir> --all --only sword,axe,bow
"""
import argparse
import os
import sys

from PIL import Image

ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"


def lum(rgb):
    return 0.2126 * rgb[0] + 0.7152 * rgb[1] + 0.0722 * rgb[2]


def dump(path):
    im = Image.open(path).convert("RGBA")
    px = im.load()
    w, h = im.size

    colors = {}
    for y in range(h):
        for x in range(w):
            r, g, b, a = px[x, y]
            if a < 8:
                continue
            colors.setdefault((r, g, b), 0)
            colors[(r, g, b)] += 1

    order = sorted(colors, key=lum)
    ch = {c: ALPHABET[i] for i, c in enumerate(order[: len(ALPHABET)])}

    print(f"### {os.path.basename(path)}  ({w}x{h}, {len(order)} colours)")
    for y in range(h):
        row = ""
        for x in range(w):
            r, g, b, a = px[x, y]
            row += "." if a < 8 else ch[(r, g, b)]
        print("   " + row)
    legend = "  ".join(f"{ch[c]}=#{c[0]:02x}{c[1]:02x}{c[2]:02x}" for c in order)
    print("   legend: " + legend)
    print()
    return len(order)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("srcdir")
    ap.add_argument("names", nargs="*")
    ap.add_argument("--all", action="store_true")
    ap.add_argument("--only")
    args = ap.parse_args()

    if args.all:
        files = sorted(f for f in os.listdir(args.srcdir) if f.endswith(".png"))
        if args.only:
            keys = [k.strip() for k in args.only.split(",")]
            files = [f for f in files if any(k in f for k in keys)]
        files = files[:40]
    else:
        files = [n if n.endswith(".png") else n + ".png" for n in args.names]

    n = 0
    for f in files:
        p = os.path.join(args.srcdir, f)
        if not os.path.exists(p):
            print(f"!! missing {f}", file=sys.stderr)
            continue
        n += dump(p)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
