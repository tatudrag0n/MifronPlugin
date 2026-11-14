#!/usr/bin/env python3
"""Minimal MCP client for the Blockbench bridge (streamable-http at /bb-mcp).

DSH's MCP client had the server disconnected at startup and does not re-register
its tools, so we speak JSON-RPC to the bridge ourselves.

Usage:
  py bb_mcp.py --list                      list tool names + descriptions
  py bb_mcp.py --call <tool> [json-args]   call a tool, print the result
"""
import argparse
import json
import sys
import urllib.request

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
except (AttributeError, ValueError):
    pass

URL = "http://127.0.0.1:3000/bb-mcp"
PROTO = "2024-11-05"


class MCP:
    def __init__(self, url=URL):
        self.url = url
        self.sid = None
        self.rid = 0
        self._post({
            "jsonrpc": "2.0", "id": 0, "method": "initialize",
            "params": {"protocolVersion": PROTO, "capabilities": {},
                       "clientInfo": {"name": "dsh-relic", "version": "1.0"}},
        })
        self._notify({"jsonrpc": "2.0", "method": "notifications/initialized", "params": {}})

    def _headers(self):
        h = {"Content-Type": "application/json",
             "Accept": "application/json, text/event-stream"}
        if self.sid:
            h["Mcp-Session-Id"] = self.sid
        return h

    def _post(self, payload, raw=False):
        data = json.dumps(payload).encode("utf-8")
        req = urllib.request.Request(self.url, data=data, headers=self._headers(), method="POST")
        with urllib.request.urlopen(req, timeout=60) as r:
            sid = r.headers.get("Mcp-Session-Id") or r.headers.get("mcp-session-id")
            if sid:
                self.sid = sid
            body = r.read().decode("utf-8", "replace")
        if raw:
            return body
        # streamable-http may answer as SSE ("data: {...}")
        for line in body.splitlines():
            line = line.strip()
            if line.startswith("data:"):
                body = line[5:].strip()
                break
        return json.loads(body) if body.strip() else {}

    def _notify(self, payload):
        try:
            self._post(payload)
        except Exception:
            pass

    def list_tools(self):
        self.rid += 1
        return self._post({"jsonrpc": "2.0", "id": self.rid, "method": "tools/list", "params": {}})

    def call(self, name, args):
        self.rid += 1
        return self._post({"jsonrpc": "2.0", "id": self.rid, "method": "tools/call",
                           "params": {"name": name, "arguments": args}})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--call")
    ap.add_argument("args", nargs="?")
    ap.add_argument("--url", default=URL)
    a = ap.parse_args()

    m = MCP(a.url)

    if a.list:
        r = m.list_tools()
        tools = (r.get("result") or {}).get("tools") or []
        if not tools:
            print(json.dumps(r, ensure_ascii=False)[:800])
            return 1
        for t in tools:
            desc = (t.get("description") or "").replace("\n", " ")[:90]
            print(f"{t['name']:28s} {desc}")
        print(f"\n{len(tools)} tools")
        return 0

    if a.call:
        args = json.loads(a.args) if a.args else {}
        r = m.call(a.call, args)
        res = r.get("result", r)
        content = res.get("content") if isinstance(res, dict) else None
        if content:
            for c in content:
                t = c.get("type")
                if t == "text":
                    print(c.get("text", ""))
                elif t == "image":
                    import base64
                    import os
                    data = c.get("data") or ""
                    ext = "png" if (c.get("mimeType") or "").endswith("png") else "bin"
                    out = os.path.join(os.getcwd(), f"bb_shot.{ext}")
                    with open(out, "wb") as fh:
                        fh.write(base64.b64decode(data))
                    print(f"[image saved] {out}")
                else:
                    print(f"[{t}]")
        else:
            print(json.dumps(res, ensure_ascii=False, indent=2)[:4000])
        return 0

    ap.print_help()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
