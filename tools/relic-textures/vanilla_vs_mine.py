#!/usr/bin/env python3
"""Pair each relic with the vanilla item it derives from, side by side, big.

This is the decisive test for "does it look like Minecraft": put the real thing
next to ours at identical scale and background.
"""
import os
import sys

from PIL import Image, ImageDraw, ImageFont

V = r"C:\root\repos\MifronPlugin\_review\vanilla"
M = r"C:\root\repos\MifronPlugin\_review\live\assets\mifron\textures\item\relic"
OUT = r"C:\root\repos\MifronPlugin\_review\vanilla-vs-mine.png"

PAIRS = [
    ("iron_sword", "decayed_sword"),
    ("golden_sword", "excalibur_replica"),
    ("mace", "antigravity_mace"),
    ("bow", "artemis"),
    ("totem_of_undying", "totem"),
    ("clock", "time_clock"),
    ("bell", "bell"),
    ("fire_charge", "fireball"),
    ("heart_of_the_sea", "seaswim"),
    ("nether_star", "shrinkstar"),
    ("shield", "reflect"),
    ("lodestone", "magnet"),
    ("spyglass", "vision"),
    ("lightning_rod", "lightning"),
    ("bundle", "infinite_bundle"),
    ("wither_skeleton_skull", "wither"),
    ("dragon_breath", "dragonbreath"),
    ("enchanted_book", "hellfire_book"),
    ("rabbit_foot", "dashfoot"),
    ("iron_boots", "skates"),
    ("grindstone", "enchgrind"),
    ("chain", "chain"),
    ("fishing_rod", "hookshot"),
    ("heavy_core", "giantcore"),
    ("anvil", "ancient_anvil"),
    ("netherite_chestplate", "ancient_slash_armor"),
    ("ender_eye", "blink"),
    ("echo_shard", "resonance"),
    ("pale_oak_boat", "noahs_ark"),
    ("elytra", "jetpack"),
]


def font(sz):
    for nm in ("consola.ttf", "arial.ttf"):
        try:
            return ImageFont.truetype(nm, sz)
        except OSError:
            pass
    return ImageFont.load_default()


def main():
    cell = 72
    lab = 15
    pairw = cell * 2 + 10
    cols = 8
    rows = (len(PAIRS) + cols - 1) // cols
    pad = 12
    W = pad + cols * (pairw + pad)
    H = pad + 24 + rows * (cell + lab + 6) + pad
    sheet = Image.new("RGBA", (W, H), (24, 24, 28, 255))
    d = ImageDraw.Draw(sheet)
    f = font(13)
    fs = font(10)
    d.text((pad, 4), "vanilla Minecraft (left)  |  mine v9 (right)", fill=(235, 235, 235, 255), font=f)

    for i, (vn, mn) in enumerate(PAIRS):
        cx = pad + (i % cols) * (pairw + pad)
        cy = 24 + pad + (i // cols) * (cell + lab + 6)
        for j, (root, nm) in enumerate(((V, vn), (M, mn))):
            slot = Image.new("RGBA", (cell, cell), (206, 206, 206, 255))
            p = os.path.join(root, nm + ".png")
            if os.path.exists(p):
                im = Image.open(p).convert("RGBA")
                z = (cell - 4) // im.width
                slot.alpha_composite(im.resize((im.width * z, im.height * z), Image.NEAREST),
                                     ((cell - im.width * z) // 2, (cell - im.height * z) // 2))
            sheet.alpha_composite(slot, (cx + j * (cell + 10), cy))
            d.rectangle([cx + j * (cell + 10), cy, cx + j * (cell + 10) + cell - 1, cy + cell - 1],
                        outline=(80, 80, 90, 255))
        d.text((cx, cy + cell + 1), mn[:20], fill=(200, 200, 205, 255), font=fs)

    sheet.save(OUT)
    print("wrote", OUT, sheet.size, "pairs", len(PAIRS))


if __name__ == "__main__":
    main()
