#!/usr/bin/env python3
"""Build a labeled contact sheet from the relic textures so art quality can be judged visually.

Usage:
  py contact_sheet.py <srcdir> <out.png> [--cols N] [--zoom N] [--bg dark|light|checker]
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFont


def font(size):
    for name in ("consola.ttf", "arial.ttf", "DejaVuSansMono.ttf"):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("srcdir")
    ap.add_argument("out")
    ap.add_argument("--cols", type=int, default=6)
    ap.add_argument("--zoom", type=int, default=6)
    ap.add_argument("--bg", default="dark")
    ap.add_argument("--cell", type=int, default=128)
    args = ap.parse_args()

    names = sorted(
        f for f in os.listdir(args.srcdir) if f.lower().endswith(".png")
    )
    if not names:
        print("no png found in", args.srcdir, file=sys.stderr)
        return 1

    cell = args.cell
    labelh = 18
    cols = min(args.cols, len(names))
    rows = (len(names) + cols - 1) // cols
    W, H = cols * cell, rows * (cell + labelh)

    if args.bg == "light":
        bg = (235, 235, 235, 255)
        fg = (20, 20, 20, 255)
    elif args.bg == "checker":
        bg = None
        fg = (20, 20, 20, 255)
    else:
        bg = (28, 28, 32, 255)
        fg = (225, 225, 225, 255)

    sheet = Image.new("RGBA", (W, H), bg if bg else (128, 128, 128, 255))
    if args.bg == "checker":
        d = ImageDraw.Draw(sheet)
        s = 8
        for y in range(0, H, s):
            for x in range(0, W, s):
                if (x // s + y // s) % 2 == 0:
                    d.rectangle([x, y, x + s - 1, y + s - 1], fill=(200, 200, 200, 255))
                else:
                    d.rectangle([x, y, x + s - 1, y + s - 1], fill=(160, 160, 160, 255))

    draw = ImageDraw.Draw(sheet)
    f = font(13)

    for i, nm in enumerate(names):
        im = Image.open(os.path.join(args.srcdir, nm)).convert("RGBA")
        # integer nearest-neighbour upscale keeps pixels crisp
        z = max(1, (cell - 8) // max(im.width, im.height))
        z = min(z, args.zoom * 4)
        big = im.resize((im.width * z, im.height * z), Image.NEAREST)
        cx = (i % cols) * cell
        cy = (i // cols) * (cell + labelh)
        ox = cx + (cell - big.width) // 2
        oy = cy + (cell - big.height) // 2
        sheet.alpha_composite(big, (ox, oy))
        # frame
        draw.rectangle([cx, cy, cx + cell - 1, cy + cell - 1], outline=(70, 70, 80, 255))
        lab = nm[:-4]
        if len(lab) > 20:
            lab = lab[:19] + "…"
        draw.text((cx + 3, cy + cell + 2), f"{i+1:02d} {lab}", fill=fg, font=f)

    sheet.save(args.out)
    print(f"wrote {args.out}  {W}x{H}  ({len(names)} tiles)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
