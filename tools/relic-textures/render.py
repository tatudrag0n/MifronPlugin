#!/usr/bin/env python3
"""Render (and validate) Mifron relic textures from 16x16 ASCII specs.

A spec is a small text file:

    title: アルテミス
    desc: 追尾する三本の矢を放つ聖弓
    rarity: RARE
    primary: steel      # blade / main body
    accent: lapis       # gem / energy / rarity signal
    trim: gold          # metal fittings (optional)
    ---
    ................
    ..............1.
    ...
    (16 rows of exactly 16 characters)

Character -> colour map
    .  transparent
    #  primary outline      A  accent outline      W  trim outline
    1  primary dark         a  accent dark         w  trim dark
    2  primary base         b  accent base         x  trim base
    3  primary light        c  accent light        y  trim light
    4  primary highlight    d  accent highlight    z  trim highlight

Usage
    py render.py --spec-dir specs --out-dir <textures dir>
    py render.py --spec-dir specs --check            # validate only
    py render.py --spec-dir specs --sheet out.png    # contact sheet
"""
from __future__ import annotations

import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFont

import palette as P

# Windows のコンソール既定コードページ (cp932) では日本語タイトルが
# UnicodeEncodeError になる。出力だけ UTF-8 に固定して落ちないようにする。
try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass

SIZE = 16

# char -> (layer, role)
CHARMAP = {
    ".": None,
    "#": ("primary", "outline"),
    "1": ("primary", "dark"),
    "2": ("primary", "base"),
    "3": ("primary", "light"),
    "4": ("primary", "highlight"),
    "A": ("accent", "outline"),
    "a": ("accent", "dark"),
    "b": ("accent", "base"),
    "c": ("accent", "light"),
    "d": ("accent", "highlight"),
    "W": ("trim", "outline"),
    "w": ("trim", "dark"),
    "x": ("trim", "base"),
    "y": ("trim", "light"),
    "z": ("trim", "highlight"),
}


class SpecError(Exception):
    pass


def parse_spec(path):
    meta, grid = {}, []
    with open(path, encoding="utf-8") as fh:
        lines = fh.read().splitlines()
    body = False
    for raw in lines:
        line = raw.rstrip("\n")
        if not body:
            if line.strip() == "---":
                body = True
                continue
            if not line.strip() or line.lstrip().startswith("#"):
                continue
            if ":" in line:
                k, v = line.split(":", 1)
                meta[k.strip()] = v.strip()
            continue
        if not line.strip():
            continue
        grid.append(line)

    name = os.path.splitext(os.path.basename(path))[0]
    meta.setdefault("title", name)
    meta.setdefault("rarity", "COMMON")
    if "primary" not in meta:
        raise SpecError(f"{name}: missing 'primary:'")
    meta.setdefault("accent", P.RARITY_ACCENT.get(meta["rarity"], ("bone",))[0])

    if len(grid) != SIZE:
        raise SpecError(f"{name}: expected {SIZE} rows, got {len(grid)}")
    for i, row in enumerate(grid):
        if len(row) != SIZE:
            raise SpecError(f"{name}: row {i} has {len(row)} chars, expected {SIZE}: {row!r}")
        for ch in row:
            if ch not in CHARMAP:
                raise SpecError(f"{name}: row {i} illegal char {ch!r}")
    return name, meta, grid


def to_image(meta, grid):
    im = Image.new("RGBA", (SIZE, SIZE), (0, 0, 0, 0))
    px = im.load()
    for y, row in enumerate(grid):
        for x, ch in enumerate(row):
            spec = CHARMAP[ch]
            if spec is None:
                continue
            layer, role = spec
            mat = meta.get(layer)
            if mat is None:
                raise SpecError(f"char {ch!r} needs '{layer}:' in header")
            hexv = P.ramp(mat, role)
            h = hexv.lstrip("#")
            px[x, y] = (int(h[0:2], 16), int(h[2:4], 16), int(h[4:6], 16), 255)
    return im


def validate(name, meta, grid):
    """Return (violations, warnings). Empty violations == pass."""
    v = []
    warns = []
    cells = [(x, y, grid[y][x]) for y in range(SIZE) for x in range(SIZE)]
    solid = [(x, y, c) for (x, y, c) in cells if c != "."]
    if not solid:
        return ["EMPTY"], warns

    xs = [x for x, _, _ in solid]
    ys = [y for _, y, _ in solid]
    bw, bh = max(xs) - min(xs) + 1, max(ys) - min(ys) + 1

    # 1) silhouette must fill the canvas -- Minecraft items are big
    if max(bw, bh) < 14:
        v.append(f"SMALL_SILHOUETTE bbox {bw}x{bh} (need >=14 on long axis)")

    # 2) must actually be a silhouette, not a filled square
    fill = len(solid) / (bw * bh)
    if fill > 0.95:
        v.append(f"FILLED_SQUARE {fill:.2f} of bbox opaque")

    # 3) palette discipline
    used = {}
    for x, y, ch in solid:
        used.setdefault(ch, 0)
        used[ch] += 1
    ncols = len({(layer_role := CHARMAP[ch]) for _, _, ch in solid})
    if ncols > 9:
        v.append(f"TOO_MANY_TONES {ncols} (want <=9 incl. outlines)")
    if "4" not in used and "d" not in used and "z" not in used:
        v.append("NO_HIGHLIGHT (need at least one highlight pixel)")
    for hi in ("4", "d", "z"):
        if used.get(hi, 0) > 6:
            v.append(f"TOO_MUCH_HIGHLIGHT {hi}={used[hi]} (keep <=6)")

    # 4) outline: warnings only, but the set should be consistent.
    #    Vanilla swords legitimately carry no outline, so this is not fatal.
    n_outline = sum(used.get(c, 0) for c in "#AW")
    if n_outline == 0:
        warns.append("no_outline")

    # 5) stray pixels: a solid pixel with no solid 4-neighbour is dirt
    solidset = {(x, y) for x, y, _ in solid}
    strays = [
        (x, y)
        for (x, y) in solidset
        if not any((x + dx, y + dy) in solidset for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
    ]
    if strays:
        v.append(f"STRAY_PIXELS {len(strays)} at {strays[:6]}")

    # 6) coverage: kind-aware. Weapons are legitimately thin; objects are bulky.
    cov = len(solid) / (SIZE * SIZE)
    kind = meta.get("kind", "object")
    cov_min = {"weapon": 0.10, "tool": 0.18, "armor": 0.20, "object": 0.34}.get(kind, 0.34)
    if cov < cov_min:
        v.append(f"LOW_COVERAGE {cov:.2f} ({kind} wants >={cov_min:.2f})")
    if cov > 0.92:
        v.append(f"OVERFULL {cov:.2f} (want <=0.92)")

    # 7) hug the canvas: content should reach near all four sides
    if min(xs) > 2 and min(ys) > 2 and max(xs) < 13 and max(ys) < 13:
        v.append("CENTRED_IN_BUBBLE (content does not reach canvas edge)")
    return v, warns


def make_sheet(specs, out, cell=104, cols=6):
    names = sorted(specs)
    rows = (len(names) + cols - 1) // cols
    lab = 20
    W, H = cols * cell, rows * (cell + lab)
    sheet = Image.new("RGBA", (W, H), (26, 26, 30, 255))
    d = ImageDraw.Draw(sheet)
    try:
        f = ImageFont.truetype("consola.ttf", 12)
    except OSError:
        f = ImageFont.load_default()
    for i, nm in enumerate(names):
        im = specs[nm]
        z = (cell - 8) // im.width
        big = im.resize((im.width * z, im.height * z), Image.NEAREST)
        cx, cy = (i % cols) * cell, (i // cols) * (cell + lab)
        sheet.alpha_composite(big, (cx + (cell - big.width) // 2, cy + (cell - big.height) // 2))
        d.rectangle([cx, cy, cx + cell - 1, cy + cell - 1], outline=(64, 64, 74, 255))
        d.text((cx + 3, cy + cell + 3), nm[:18], fill=(220, 220, 220, 255), font=f)
    sheet.save(out)
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--spec-dir", required=True)
    ap.add_argument("--out-dir")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("--sheet")
    ap.add_argument("--quiet", action="store_true")
    args = ap.parse_args()

    specs = sorted(f for f in os.listdir(args.spec_dir) if f.endswith(".spec"))
    if not specs:
        print("no .spec files found in", args.spec_dir, file=sys.stderr)
        return 1

    images, failures, errors = {}, 0, 0
    for fn in specs:
        path = os.path.join(args.spec_dir, fn)
        try:
            name, meta, grid = parse_spec(path)
            img = to_image(meta, grid)
            viol, warns = validate(name, meta, grid)
        except SpecError as e:
            print(f"ERROR  {e}")
            errors += 1
            continue
        images[name] = img
        if viol:
            failures += 1
            print(f"FAIL   {name:24s} {meta.get('rarity','?'):10s}")
            for item in viol:
                print(f"         - {item}")
        elif not args.quiet:
            w = f"  (warn: {','.join(warns)})" if warns else ""
            print(f"ok     {name:24s} {meta.get('rarity','?'):10s} {meta.get('title','')}{w}")

        if args.out_dir:
            os.makedirs(args.out_dir, exist_ok=True)
            img.save(os.path.join(args.out_dir, name + ".png"))

    if args.sheet:
        make_sheet(images, args.sheet)
        print(f"sheet  {args.sheet}")

    print(f"\n{len(specs)} specs: {len(specs)-failures-errors} pass, {failures} fail, {errors} error")
    return 0 if (failures == 0 and errors == 0) else 2


if __name__ == "__main__":
    raise SystemExit(main())
