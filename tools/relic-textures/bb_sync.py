#!/usr/bin/env python3
"""Round-trip 16x16 relic textures through the running Blockbench instance.

Blockbench becomes the authoring surface: textures live in a .bbmodel project,
can be painted by hand in Paint mode, and are pulled back out into the resource
pack as PNGs.

  py bb_sync.py push  <png-dir> [names...]   resource pack -> Blockbench
  py bb_sync.py pull  <png-dir>              Blockbench -> resource pack
  py bb_sync.py list                          what Blockbench currently holds

Needs Blockbench running with the bb-mcp plugin (listens on 3000/bb-mcp).
"""
import argparse
import base64
import json
import os
import sys

import bb_mcp

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass

CHUNK = 6


def call(js):
    m = bb_mcp.MCP()
    r = m.call("risky_eval", {"code": js})
    res = r.get("result", r)
    if isinstance(res, dict) and res.get("isError"):
        raise SystemExit("risky_eval error: " + json.dumps(res, ensure_ascii=False)[:500])
    content = (res or {}).get("content") or []
    for c in content:
        if c.get("type") == "text":
            return c.get("text", "")
    return ""


def push(texdir, names):
    if not names:
        names = sorted(f[:-4] for f in os.listdir(texdir) if f.endswith(".png"))
    total = 0
    for i in range(0, len(names), CHUNK):
        batch = names[i:i + CHUNK]
        payload = []
        for n in batch:
            p = os.path.join(texdir, n + ".png")
            if not os.path.exists(p):
                print("  skip (missing)", n)
                continue
            url = "data:image/png;base64," + base64.b64encode(open(p, "rb").read()).decode()
            payload.append({"name": n, "url": url})
        if not payload:
            continue
        # The bridge rejects any code containing '//', and base64 contains '/',
        # so escape every slash as a JSON \u002f escape. JS parses it back to '/'.
        arr_json = json.dumps(payload).replace("/", "\\u002f")
        js = (
            "(async () => {"
            "const P = " + arr_json + ";"
            "const made = [];"
            "for (const t of P) {"
            "  let tex = Texture.all.find(function(x){ return x.name === t.name });"
            "  if (!tex) { tex = new Texture({name: t.name, width: 16, height: 16}); }"
            "  await tex.fromDataURL(t.url);"
            "  tex.add(false);"
            "  made.push(tex.name);"
            "}"
            "return {made: made.length, total: Texture.all.length};"
            "})()"
        )
        out = call(js)
        print(f"  pushed {batch[0]}..{batch[-1]} -> {out}")
        total += len(payload)
    print(f"pushed {total} textures")


def pull(texdir, only=None):
    js = (
        "(async () => {"
        "const out = [];"
        "for (const t of Texture.all) { out.push({name: t.name, url: t.getDataURL()}); }"
        "return out;"
        "})()"
    )
    raw = call(js)
    # the bridge returns the JSON as text
    try:
        arr = json.loads(raw)
    except json.JSONDecodeError:
        print("could not parse:", raw[:300])
        return 1
    n = 0
    for item in arr:
        nm = item.get("name")
        if only and nm not in only:
            continue
        url = item.get("url", "")
        if "," in url and url.startswith("data:"):
            url = url.split(",", 1)[1]
        data = base64.b64decode(url)
        os.makedirs(texdir, exist_ok=True)
        with open(os.path.join(texdir, nm + ".png"), "wb") as fh:
            fh.write(data)
        n += 1
    print(f"pulled {n} textures -> {texdir}")
    return 0


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("action", choices=["push", "pull", "list"])
    ap.add_argument("texdir", nargs="?")
    ap.add_argument("names", nargs="*")
    a = ap.parse_args()

    if a.action == "list":
        print(call("({count: Texture.all.length, names: Texture.all.map(function(t){return t.name}).sort()})"))
        return 0
    if a.action == "push":
        push(a.texdir, a.names)
        return 0
    return pull(a.texdir)


if __name__ == "__main__":
    raise SystemExit(main())
