#!/usr/bin/env python3
"""Blow up 16x16 relic textures big, on both dark and light backgrounds.

Judging a 16x16 sprite needs (a) a big nearest-neighbour view and (b) two
backgrounds, because an item must read on the light inventory panel AND on
darker UI.  Also shows a 1:1 row so real Minecraft scale is checked.

Usage: py preview.py <png-dir-or-> out.png [names...]
"""
import argparse
import os
import sys

from PIL import Image, ImageDraw, ImageFont


def load_font(sz):
    for nm in ("consola.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(nm, sz)
        except OSError:
            pass
    return ImageFont.load_default()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("srcdir")
    ap.add_argument("out")
    ap.add_argument("names", nargs="*")
    ap.add_argument("--big", type=int, default=320)
    args = ap.parse_args()

    if args.names:
        files = [n + (".png" if not n.endswith(".png") else "") for n in args.names]
    else:
        files = sorted(f for f in os.listdir(args.srcdir) if f.lower().endswith(".png"))
    files = [f for f in files if os.path.exists(os.path.join(args.srcdir, f))]

    big = args.big
    small = 16 * 4
    gap = 18
    labelh = 34
    # width: for each item -> big dark | big light | small 1:1
    item_w = big * 2 + small + gap * 3
    W = item_w + gap
    H = len(files) * (labelh + big + gap) + gap
    if H > 6000:
        # stack two per row instead
        pass

    sheet = Image.new("RGBA", (W, H), (18, 18, 22, 255))
    d = ImageDraw.Draw(sheet)
    f = load_font(15)

    y = gap
    for fn in files:
        im = Image.open(os.path.join(args.srcdir, fn)).convert("RGBA")
        name = os.path.splitext(fn)[0]
        d.text((gap, y), name, fill=(240, 240, 240, 255), font=f)
        y += labelh - 10

        # dark panel
        pd = Image.new("RGBA", (big, big), (26, 26, 30, 255))
        # light panel
        pl = Image.new("RGBA", (big, big), (208, 208, 208, 255))

        z = big // im.width
        up = im.resize((im.width * z, im.height * z), Image.NEAREST)
        pd.alpha_composite(up, ((big - up.width) // 2, (big - up.height) // 2))
        pl.alpha_composite(up, ((big - up.width) // 2, (big - up.height) // 2))

        x = gap
        sheet.alpha_composite(pd, (x, y)); x += big + gap
        sheet.alpha_composite(pl, (x, y)); x += big + gap

        # 1:1 -> 4x for visibility of true density
        s = im.resize((small, small), Image.NEAREST)
        sb = Image.new("RGBA", (small, small), (70, 70, 78, 255))
        sb.alpha_composite(s, (0, 0))
        sheet.alpha_composite(sb, (x, y))
        for bx, by in ((x, y),):
            d.rectangle([bx, by, bx + small - 1, by + small - 1], outline=(110, 110, 120, 255))
        y += big + gap

    sheet.save(args.out)
    print(f"wrote {args.out} ({W}x{H}) items={len(files)}")


if __name__ == "__main__":
    raise SystemExit(main())
