#!/usr/bin/env python3
"""Side-by-side before/after: old shipped textures vs v9, in Minecraft scale.

Old row is what players saw (opaque black background = black squares).
New row is the v9 build.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

OLD = r"C:\root\repos\MifronPlugin\_review\deployed\assets\mifron\textures\item\relic"
NEW = r"C:\root\repos\MifronPlugin\_review\live\assets\mifron\textures\item\relic"
OUT = r"C:\root\repos\MifronPlugin\_review\before-after.png"

ORDER = [
    "decayed_sword", "giantcore", "nightclock", "fireball", "bell", "magnet",
    "time_clock", "reflect", "totem", "lightning", "hookshot", "enchgrind",
    "jetpack", "hellfire_book", "noahs_ark", "chain", "wither", "blink",
]


def font(sz):
    for nm in ("consola.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(nm, sz)
        except OSError:
            pass
    return ImageFont.load_default()


def main():
    cell = 76
    lab = 16
    cols = 9
    rows = (len(ORDER) + cols - 1) // cols
    pad = 10
    W = pad + cols * (cell + pad)
    H = pad + 26 + 2 * (rows * (cell + lab) + pad + 20)
    sheet = Image.new("RGBA", (W, H), (22, 22, 26, 255))
    d = ImageDraw.Draw(sheet)
    f = font(14)
    fs = font(12)

    y = pad
    d.text((pad, y), "BEFORE  - shipped pack (sha1 e776e851): 33/33 opaque, black squares", fill=(255, 140, 140, 255), font=f)
    y += 26
    for i, nm in enumerate(ORDER):
        p = os.path.join(OLD, nm + ".png")
        cx = pad + (i % cols) * (cell + pad)
        cy = y + (i // cols) * (cell + lab)
        slot = Image.new("RGBA", (cell, cell), (232, 232, 232, 255))
        if os.path.exists(p):
            im = Image.open(p).convert("RGBA")
            z = (cell - 6) // im.width
            slot.alpha_composite(im.resize((im.width * z, im.height * z), Image.NEAREST),
                                 ((cell - im.width * z) // 2, (cell - im.height * z) // 2))
        sheet.alpha_composite(slot, (cx, cy))
        d.rectangle([cx, cy, cx + cell - 1, cy + cell - 1], outline=(60, 60, 70, 255))
        d.text((cx + 1, cy + cell + 1), nm[:13], fill=(190, 190, 195, 255), font=fs)

    y += rows * (cell + lab) + pad + 20
    d.text((pad, y), "AFTER  - v9 (sha1 8b9a2baf): transparent, fills canvas, one shared palette", fill=(150, 255, 170, 255), font=f)
    y += 26
    for i, nm in enumerate(ORDER):
        p = os.path.join(NEW, nm + ".png")
        cx = pad + (i % cols) * (cell + pad)
        cy = y + (i // cols) * (cell + lab)
        slot = Image.new("RGBA", (cell, cell), (232, 232, 232, 255))
        if os.path.exists(p):
            im = Image.open(p).convert("RGBA")
            z = (cell - 6) // im.width
            slot.alpha_composite(im.resize((im.width * z, im.height * z), Image.NEAREST),
                                 ((cell - im.width * z) // 2, (cell - im.height * z) // 2))
        sheet.alpha_composite(slot, (cx, cy))
        d.rectangle([cx, cy, cx + cell - 1, cy + cell - 1], outline=(60, 60, 70, 255))
        d.text((cx + 1, cy + cell + 1), nm[:13], fill=(190, 190, 195, 255), font=fs)

    sheet.save(OUT)
    print("wrote", OUT, sheet.size)


if __name__ == "__main__":
    main()
