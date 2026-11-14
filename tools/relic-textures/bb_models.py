#!/usr/bin/env python3
"""Build Minecraft Java item models in Blockbench and export them to the pack.

"Anvil and grindstone are 3D objects" -- vanilla renders those item icons from a
3D block model, which is exactly why a flat 16x16 cube-face trace looked like a
box. So build real geometry in Blockbench and export the model JSON.

Each model uses one 16x16 texture split into 4 detail quadrants, and cubes map
their faces to a quadrant by role:

    up   -> LIGHT    sides -> MID    down -> DARK    accent cubes -> ACCENT

Usage:
  py bb_models.py --model ancient_anvil   # build + export one
  py bb_models.py --all                   # all models below
"""
import argparse
import base64
import io
import json
import os
import sys

from PIL import Image, ImageDraw

import bb_mcp

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass

REPO = r"C:\root\repos\MifronPlugin"
TEXDIR = os.path.join(REPO, r"resourcepack\mifron-relics\assets\mifron\textures\item\relic")
MODDIR = os.path.join(REPO, r"resourcepack\mifron-relics\assets\mifron\models\item\relic")

# ---------------------------------------------------------------- texture
# quadrant origin -> (base, edge) ; quadrant size 8x8
QUAD = {
    "LIGHT": ((0, 0), "#a8949c", "#8a7a82"),
    "MID": ((8, 0), "#736068", "#5a4a52"),
    "DARK": ((0, 8), "#4a3c44", "#332830"),
    "ACCENT": ((8, 8), "#e8b000", "#a06a00"),
}
UV = {
    "LIGHT": [0, 0, 8, 8],
    "MID": [8, 0, 16, 8],
    "DARK": [0, 8, 8, 16],
    "ACCENT": [8, 8, 16, 16],
}


def build_texture(ov=None):
    """16x16 with four 8x8 quadrants: solid fill plus a 1px darker edge."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    for name, ((ox, oy), base, edge) in QUAD.items():
        b = (ov or {}).get(name, base)
        d.rectangle([ox, oy, ox + 7, oy + 7], fill=b)
        d.rectangle([ox, oy, ox + 7, oy + 7], outline=edge)
        # a little hand-drawn grit so faces are not flat vector fills
        d.point([(ox + 2, oy + 3), (ox + 5, oy + 2), (ox + 4, oy + 5), (ox + 3, oy + 6)],
                fill=edge)
    return im


# ---------------------------------------------------------------- geometry
# cube = (name, from, to, role) ; role: light/mid/dark/accent
MODELS = {
    "ancient_anvil": {
        "tex_override": {"ACCENT": "#e8b000"},
        "cubes": [
            ("base",  [2, 0, 2],  [14, 3, 14], "mid"),
            ("waist", [5, 3, 5],  [11, 7, 11], "dark"),
            ("top",   [1, 7, 2],  [15, 10, 14], "light"),
            ("horn",  [-2, 7, 5], [1, 10, 11], "mid"),
            ("inlay", [2, 10, 8], [14, 11, 12], "accent"),
        ],
    },
    "enchgrind": {
        "tex_override": {"ACCENT": "#6b3f1f", "LIGHT": "#c8d2e0"},
        "cubes": [
            ("base",   [1, 0, 2],  [15, 2, 9],  "accent"),
            ("post_l", [1, 2, 3],  [3, 11, 8],  "accent"),
            ("post_r", [13, 2, 3], [15, 11, 8], "accent"),
            ("wheel",  [3, 2, 3],  [13, 10, 8], "light"),
            ("axle",   [7, 10, 4], [9, 11, 7],  "mid"),
        ],
    },
    "giantcore": {
        "tex_override": {"MID": "#4a3c44", "LIGHT": "#736068", "ACCENT": "#d63a18"},
        "cubes": [
            ("shell",    [1, 1, 1],  [15, 15, 15], "mid"),
            ("vent_w",   [1, 5, 1],  [2, 11, 15],  "accent"),
            ("vent_e",   [14, 5, 1], [15, 11, 15], "accent"),
            ("vent_top", [5, 15, 5], [11, 16, 11], "accent"),
        ],
    },
}

DISPLAY = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0],
                              "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0],
                              "scale": [0.4, 0.4, 0.4]},
}


def call(m):
    r = m.call("risky_eval", {"code": None})  # placeholder, replaced below
    return r


def eval_js(code):
    m = bb_mcp.MCP()
    r = m.call("risky_eval", {"code": code})
    res = r.get("result", r)
    if isinstance(res, dict) and res.get("isError"):
        raise SystemExit("risky_eval error: " + json.dumps(res, ensure_ascii=False)[:600])
    for c in ((res or {}).get("content") or []):
        if c.get("type") == "text":
            return c.get("text", "")
    return ""


def export_model(name, spec):
    tex = build_texture(spec.get("tex_override"))
    buf = io.BytesIO()
    tex.save(buf, format="PNG")
    url = "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode()

    cubes_js = []
    for (cname, frm, to, role) in spec["cubes"]:
        if role == "accent":
            faces = {f: "ACCENT" for f in ("north", "east", "south", "west", "up", "down")}
        else:
            faces = {
                "up": "LIGHT", "down": "DARK",
                "north": "MID", "east": "MID", "south": "MID", "west": "MID",
            }
            if role == "light":
                faces = {k: "LIGHT" for k in faces}
            elif role == "dark":
                faces = {k: "DARK" for k in faces}
        uv = {f: UV[q] for f, q in faces.items()}
        cubes_js.append({
            "name": cname, "from": frm, "to": to,
            "uv": uv,
        })

    payload = json.dumps({"tex": url, "name": name, "cubes": cubes_js})
    payload = payload.replace("/", "\\u002f")

    js = (
        "(async () => {"
        "const P = " + payload + ";"
        "Cube.all.slice().forEach(function(c){ c.remove() });"
        "Texture.all.slice().forEach(function(t){ t.remove() });"
        "let tex = new Texture({name: P.name, width: 16, height: 16});"
        "await tex.fromDataURL(P.tex);"
        "tex.add(false);"
        "const made = [];"
        "for (const cd of P.cubes) {"
        "  let c = new Cube({name: cd.name, from: cd.from, to: cd.to}).init();"
        "  c.autouv = 0;"
        "  for (const f in cd.uv) {"
        "    if (c.faces[f]) { c.faces[f].uv = cd.uv[f].slice(); c.faces[f].texture = 0; }"
        "  }"
        "  made.push(c.name);"
        "}"
        "return {cubes: made.length, tex: Texture.all.length};"
        "})()"
    )
    print("  build:", eval_js(js))

    js2 = "(async () => { const out = Codecs.java_block.compile({}); return out })()"
    raw = eval_js(js2)
    try:
        model = json.loads(raw)
    except json.JSONDecodeError:
        print("  export raw:", raw[:400])
        return 1
    # compile() hands back a JSON string, which arrives here as a JSON string
    # literal, so decode once more.
    if isinstance(model, str):
        model = json.loads(model)
    # Blockbench-only keys that do not belong in a Java item model
    for k in ("format_version", "credit"):
        model.pop(k, None)
    for el in model.get("elements", []):
        el.pop("name", None)

    model.setdefault("textures", {})
    model["textures"]["particle"] = "mifron:item/relic/" + name
    for k, v in list(model["textures"].items()):
        model["textures"][k] = "mifron:item/relic/" + name
    model["display"] = DISPLAY

    os.makedirs(MODDIR, exist_ok=True)
    with open(os.path.join(MODDIR, name + ".json"), "w", encoding="utf-8") as fh:
        json.dump(model, fh, indent=2, ensure_ascii=False)
    tex.save(os.path.join(TEXDIR, name + ".png"))
    print(f"  wrote model {name}.json ({len(model.get('elements', []))} elements) + {name}.png")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model")
    ap.add_argument("--all", action="store_true")
    a = ap.parse_args()
    names = list(MODELS) if a.all else [a.model]
    for n in names:
        if n not in MODELS:
            raise SystemExit("unknown model: " + n)
        print("model:", n)
        export_model(n, MODELS[n])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
