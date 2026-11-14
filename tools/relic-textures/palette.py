#!/usr/bin/env python3
"""Palette definitions for Mifron relic textures.

Every material is a 4-step ramp (dark / base / light / highlight) plus an
outline. Ramps bake in the community hue-shift rule:

  * shadows drift toward blue-violet  (cooler hue, desaturated)
  * highlights drift toward yellow    (warmer hue)

so value changes never look "milky".
"""

# name -> dict(outline, dark, base, light, highlight)
MATERIALS = {
    "steel": {           # 鋼・鉄  (刃物、鎧、機械)
        "outline": "#242a36",
        "dark": "#4a5568",
        "base": "#8a94a8",
        "light": "#c8d2e0",
        "highlight": "#f4f8ff",
    },
    "rusted": {          # 朽ちた鋼 (錆・経年)
        "outline": "#2a1e14",
        "dark": "#5a4530",
        "base": "#8a6b46",
        "light": "#b89a70",
        "highlight": "#e0cba0",
    },
    "netherite": {       # ネザライト (最硬・暗黒)
        "outline": "#150f13",
        "dark": "#2d232a",
        "base": "#4a3c44",
        "light": "#736068",
        "highlight": "#a8949c",
    },
    "wood": {            # 木材
        "outline": "#2f1a0e",
        "dark": "#6b3f1f",
        "base": "#a0662f",
        "light": "#c98d4a",
        "highlight": "#e8bc80",
    },
    "bone": {            # 骨・白磁
        "outline": "#4a4234",
        "dark": "#8e8472",
        "base": "#c8bfab",
        "light": "#ece5d6",
        "highlight": "#ffffff",
    },
    "gold": {            # 金・真鍮
        "outline": "#4a2c00",
        "dark": "#a06a00",
        "base": "#e8b000",
        "light": "#ffd84a",
        "highlight": "#fff8c8",
    },
    "copper": {          # 銅 (緑青を伴う)
        "outline": "#3a1e10",
        "dark": "#8a4420",
        "base": "#c26a32",
        "light": "#e89a5c",
        "highlight": "#ffd0a0",
    },
    "leather": {         # 革
        "outline": "#26140c",
        "dark": "#4a2a18",
        "base": "#7a4a28",
        "light": "#a8703c",
        "highlight": "#c89058",
    },
    # ---- accents: gem / energy / rarity signal ----
    "lapis": {           # ラピス・蒼 (RARE 系シグナル)
        "outline": "#062638",
        "dark": "#0e4a6b",
        "base": "#1f7fa8",
        "light": "#3fc8de",
        "highlight": "#a8f4ff",
    },
    "violet": {          # 紫 (EPIC 系シグナル)
        "outline": "#26103a",
        "dark": "#4a1f6b",
        "base": "#7a3db8",
        "light": "#b07ae8",
        "highlight": "#e8ccff",
    },
    "ember": {           # 獄炎・赤橙 (LEGENDARY 系シグナル)
        "outline": "#3a0a08",
        "dark": "#8a1a10",
        "base": "#d63a18",
        "light": "#ff7a30",
        "highlight": "#ffd84a",
    },
    "poison": {          # 毒・緑
        "outline": "#0c2a10",
        "dark": "#1e5c20",
        "base": "#3f9a2e",
        "light": "#7ad04a",
        "highlight": "#c8f08a",
    },
    "frost": {           # 氷・水
        "outline": "#0c2c3a",
        "dark": "#1e5c72",
        "base": "#3f9ec2",
        "light": "#7ad4e8",
        "highlight": "#d6f8ff",
    },
    "void": {            # 次元・虚無
        "outline": "#140c1e",
        "dark": "#2c1a3e",
        "base": "#4a2e66",
        "light": "#6e4a92",
        "highlight": "#9e78c8",
    },
    "shadow": {          # 凋零・闇
        "outline": "#0e0e12",
        "dark": "#22222a",
        "base": "#3a3a46",
        "light": "#5a5a68",
        "highlight": "#82828f",
    },
    "pale": {            # 淡い白 (幽体)
        "outline": "#3a4650",
        "dark": "#76909f",
        "base": "#a8c0cc",
        "light": "#d2e4ea",
        "highlight": "#f4ffff",
    },
}

# Rarity -> (primary accent material, secondary accent material)
RARITY_ACCENT = {
    "COMMON": ("bone", "bone"),
    "UNCOMMON": ("poison", "copper"),
    "RARE": ("lapis", "frost"),
    "EPIC": ("violet", "void"),
    "LEGENDARY": ("ember", "gold"),
}

HEX_ORDER = ["outline", "dark", "base", "light", "highlight"]


def resolve(material):
    if material not in MATERIALS:
        raise KeyError(f"unknown material {material!r}; have {sorted(MATERIALS)}")
    return MATERIALS[material]


def ramp(material, role):
    return resolve(material)[role]


if __name__ == "__main__":
    for m in sorted(MATERIALS):
        r = MATERIALS[m]
        print(f"{m:11s} " + "  ".join(f"{r[k]:8s}" for k in HEX_ORDER))
