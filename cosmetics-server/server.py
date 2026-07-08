#!/usr/bin/env python3
"""
Tweaky cosmetics server.

Run:      python server.py            (needs: pip install aiohttp)
Config:   config.json    (auto-created next to this file; holds port + admin_token)
          PORT / ADMIN_TOKEN environment variables override it (used on Render etc.)
Storage:  cosmetics.json (auto-created; uuid -> {playerName, name, sizeX, sizeY, sizeZ})

Built on aiohttp so the same port serves BOTH the websocket and plain HTTP — health checks
(GET/HEAD from Render, uptime pingers, browsers) get a 200 instead of handshake errors.

Protocol (JSON text frames, "type" field):
  C2S  {"type":"hello", "uuid":"...", "name":"..."}          identify; server replies with cosmetics
  S2C  {"type":"cosmetics", "data":{uuid:{...}}}              full map, broadcast on every change
  S2C  {"type":"message", "text":"..."}                       chat message from the server panel
  S2C  {"type":"ping"}                                        keep-alive heartbeat (clients ignore)
  C2S  {"type":"admin", "token":"...", "action":"name|size|remove|list|restore", ...}
  S2C  {"type":"admin_result", "ok":bool, "message":"..."}

Admin from in-game: enable Cosmetics in Tweaky, paste the admin token into the "Admin Key"
setting, then use /tweaky cosmetics. Or type commands right here in this console.
"""

import asyncio
import json
import os
import base64
import re
import secrets
import sys
import urllib.error
import urllib.request
from pathlib import Path

try:
    from aiohttp import WSMsgType, web
except ImportError:
    sys.exit("The 'aiohttp' package is missing. Install it with:  pip install aiohttp")

HERE = Path(__file__).resolve().parent
CONFIG_FILE = HERE / "config.json"
DATA_FILE = HERE / "cosmetics.json"

UUID_RE = re.compile(r"^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")
NAME_RE = re.compile(r"^\w{1,16}$")

# ---------------------------------------------------------------------------- state

config: dict = {}
cosmetics: dict[str, dict] = {}   # dashed-lowercase uuid -> entry
clients: set = set()              # connected mod clients (post-hello)
client_info: dict = {}            # ws -> {"uuid": ..., "name": ...} for panel messaging
name_cache: dict[str, str] = {}   # lowercase name -> uuid (from hellos + lookups)


def load_config() -> None:
    global config
    if CONFIG_FILE.exists():
        config = json.loads(CONFIG_FILE.read_text(encoding="utf-8"))
    changed = False
    if "port" not in config:
        config["port"] = 8765
        changed = True
    if "admin_token" not in config:
        config["admin_token"] = secrets.token_hex(16)
        changed = True
    if changed:
        CONFIG_FILE.write_text(json.dumps(config, indent=2), encoding="utf-8")

    # Cloud hosts (Render etc.) configure via environment instead of files — env wins when set.
    if os.environ.get("PORT"):
        config["port"] = int(os.environ["PORT"])
    if os.environ.get("ADMIN_TOKEN"):
        config["admin_token"] = os.environ["ADMIN_TOKEN"]


def load_cosmetics() -> None:
    global cosmetics
    from_github = github_load()
    if from_github is not None:
        cosmetics = from_github
        DATA_FILE.write_text(json.dumps(cosmetics, indent=2), encoding="utf-8")
    elif DATA_FILE.exists():
        cosmetics = json.loads(DATA_FILE.read_text(encoding="utf-8"))
    for uuid, entry in cosmetics.items():
        if entry.get("playerName"):
            name_cache[entry["playerName"].lower()] = uuid


def save_cosmetics() -> None:
    DATA_FILE.write_text(json.dumps(cosmetics, indent=2), encoding="utf-8")
    schedule_github_push()


# ------------------------------------------------------------------- github persistence
# Render's free tier wipes the disk on every restart/sleep, so cosmetics.json alone can't
# survive there. When GITHUB_TOKEN is set, cosmetics are mirrored to the GitHub repo on a
# SEPARATE branch (default "data") — pushes there don't trigger Render's auto-deploy (it only
# watches the deployed branch), and the file is re-fetched on every boot.
#
# Setup (one-time): create a fine-grained PAT with Contents read+write on the repo, then set
# GITHUB_TOKEN in Render's environment. Optional: GITHUB_REPO (owner/name), GITHUB_BRANCH.

GH_TOKEN = os.environ.get("GITHUB_TOKEN", "")
GH_REPO = os.environ.get("GITHUB_REPO", "Xmogus1/tweaky")
GH_BRANCH = os.environ.get("GITHUB_BRANCH", "data")
GH_PATH = "cosmetics.json"

_gh_sha: str | None = None
_gh_push_task: asyncio.Task | None = None


def _gh_request(method: str, url: str, payload: dict | None = None) -> tuple[int, dict | None]:
    req = urllib.request.Request(url, method=method, headers={
        "Accept": "application/vnd.github+json",
        "User-Agent": "TweakyCosmeticsServer/1.0",
        **({"Authorization": f"Bearer {GH_TOKEN}"} if GH_TOKEN else {}),
    })
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    if data:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, data=data, timeout=15) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, None
    except Exception:
        return 0, None


def github_load() -> dict | None:
    """Fetches cosmetics from the data branch at boot. None = not configured / not available."""
    if not GH_TOKEN:
        return None
    global _gh_sha
    status, body = _gh_request("GET", f"https://api.github.com/repos/{GH_REPO}/contents/{GH_PATH}?ref={GH_BRANCH}")
    if status == 200 and body:
        _gh_sha = body.get("sha")
        try:
            data = json.loads(base64.b64decode(body.get("content", "")).decode("utf-8"))
            print(f"GitHub persistence: loaded {len(data)} cosmetic(s) from {GH_REPO}@{GH_BRANCH}")
            return data if isinstance(data, dict) else None
        except Exception:
            return None
    if status == 404:
        _ensure_branch()
        print(f"GitHub persistence: no saved data yet on {GH_REPO}@{GH_BRANCH} (starting fresh)")
        return {}
    print(f"GitHub persistence: load failed (HTTP {status}) — check GITHUB_TOKEN permissions")
    return None


def _ensure_branch() -> None:
    """Creates the data branch off the default branch if it doesn't exist yet."""
    status, _ = _gh_request("GET", f"https://api.github.com/repos/{GH_REPO}/branches/{GH_BRANCH}")
    if status == 200:
        return
    status, repo = _gh_request("GET", f"https://api.github.com/repos/{GH_REPO}")
    default = (repo or {}).get("default_branch", "main")
    status, ref = _gh_request("GET", f"https://api.github.com/repos/{GH_REPO}/git/ref/heads/{default}")
    sha = ((ref or {}).get("object") or {}).get("sha")
    if sha:
        _gh_request("POST", f"https://api.github.com/repos/{GH_REPO}/git/refs",
                    {"ref": f"refs/heads/{GH_BRANCH}", "sha": sha})


def schedule_github_push() -> None:
    """Debounced (3s) push so a burst of grants becomes one commit."""
    if not GH_TOKEN:
        return
    global _gh_push_task
    try:
        loop = asyncio.get_running_loop()
    except RuntimeError:
        return
    if _gh_push_task and not _gh_push_task.done():
        _gh_push_task.cancel()
    _gh_push_task = loop.create_task(_delayed_github_push())


async def _delayed_github_push() -> None:
    try:
        await asyncio.sleep(3)
    except asyncio.CancelledError:
        return
    await asyncio.to_thread(_github_push)


def _github_push() -> None:
    global _gh_sha
    content = base64.b64encode(json.dumps(cosmetics, indent=2).encode("utf-8")).decode("ascii")

    def attempt() -> int:
        payload = {"message": "update cosmetics", "content": content, "branch": GH_BRANCH}
        if _gh_sha:
            payload["sha"] = _gh_sha
        status, body = _gh_request("PUT", f"https://api.github.com/repos/{GH_REPO}/contents/{GH_PATH}", payload)
        if status in (200, 201) and body:
            globals()["_gh_sha"] = (body.get("content") or {}).get("sha")
        return status

    status = attempt()
    if status in (409, 422):
        # stale sha — refetch and retry once
        s, body = _gh_request("GET", f"https://api.github.com/repos/{GH_REPO}/contents/{GH_PATH}?ref={GH_BRANCH}")
        _gh_sha = (body or {}).get("sha")
        status = attempt()
    if status not in (200, 201):
        print(f"GitHub persistence: push failed (HTTP {status})")


def dash_uuid(raw: str) -> str:
    raw = raw.replace("-", "").lower()
    return f"{raw[0:8]}-{raw[8:12]}-{raw[12:16]}-{raw[16:20]}-{raw[20:32]}"


def cosmetics_payload() -> str:
    return json.dumps({"type": "cosmetics", "data": cosmetics})


async def _safe_send(ws, payload: str) -> bool:
    try:
        await ws.send_str(payload)
        return True
    except Exception:
        return False


def broadcast_all(payload: str) -> None:
    for ws in list(clients):
        asyncio.ensure_future(_safe_send(ws, payload))


def broadcast_cosmetics() -> None:
    if clients:
        broadcast_all(cosmetics_payload())


def default_entry(player_name: str) -> dict:
    return {"playerName": player_name, "name": "", "sizeX": 1.0, "sizeY": 1.0, "sizeZ": 1.0,
            "offsetX": 0.0, "offsetY": 0.0, "offsetZ": 0.0}


# ---------------------------------------------------------------------- name resolution

def mojang_lookup(name: str) -> tuple[str, str] | None:
    """Blocking Mojang API lookup: name -> (dashed uuid, correctly-cased name). None if unknown."""
    try:
        req = urllib.request.Request(
            f"https://api.mojang.com/users/profiles/minecraft/{name}",
            headers={"User-Agent": "TweakyCosmeticsServer/1.0"},
        )
        with urllib.request.urlopen(req, timeout=10) as resp:
            if resp.status != 200:
                return None
            data = json.loads(resp.read().decode("utf-8"))
            return dash_uuid(data["id"]), data["name"]
    except Exception:
        return None


async def resolve_target(target: str) -> tuple[str, str] | None:
    """Resolve a name-or-uuid to (uuid, playerName). Checks known data first, then Mojang."""
    target = target.strip()
    if UUID_RE.match(target):
        uuid = target.lower()
        entry = cosmetics.get(uuid)
        return uuid, (entry.get("playerName", "") if entry else "")

    if not NAME_RE.match(target):
        return None

    cached = name_cache.get(target.lower())
    if cached:
        entry = cosmetics.get(cached)
        proper = entry.get("playerName") if entry and entry.get("playerName") else target
        return cached, proper

    result = await asyncio.to_thread(mojang_lookup, target)
    if result:
        name_cache[result[1].lower()] = result[0]
    return result


# ----------------------------------------------------------------------------- actions

async def do_name(target: str, value: str) -> tuple[bool, str]:
    resolved = await resolve_target(target)
    if not resolved:
        return False, f"Unknown player: {target}"
    uuid, player_name = resolved
    entry = cosmetics.setdefault(uuid, default_entry(player_name))
    if player_name:
        entry["playerName"] = player_name
    entry["name"] = value
    save_cosmetics()
    broadcast_cosmetics()
    return True, f"Custom name for {player_name or uuid} set to: {value}"


async def do_size(target: str, sx: float, sy: float, sz: float) -> tuple[bool, str]:
    for v in (sx, sy, sz):
        if not (-10.0 <= v <= 10.0) or v == 0:
            return False, "Sizes must be in [-10, 10] and not 0."
    resolved = await resolve_target(target)
    if not resolved:
        return False, f"Unknown player: {target}"
    uuid, player_name = resolved
    entry = cosmetics.setdefault(uuid, default_entry(player_name))
    if player_name:
        entry["playerName"] = player_name
    entry["sizeX"], entry["sizeY"], entry["sizeZ"] = float(sx), float(sy), float(sz)
    save_cosmetics()
    broadcast_cosmetics()
    return True, f"Size for {player_name or uuid} set to {sx} x {sy} x {sz}"


async def do_offset(target: str, ox: float, oy: float, oz: float) -> tuple[bool, str]:
    for v in (ox, oy, oz):
        if not (-10.0 <= v <= 10.0):
            return False, "Offsets must be in [-10, 10]."
    resolved = await resolve_target(target)
    if not resolved:
        return False, f"Unknown player: {target}"
    uuid, player_name = resolved
    entry = cosmetics.setdefault(uuid, default_entry(player_name))
    if player_name:
        entry["playerName"] = player_name
    entry["offsetX"], entry["offsetY"], entry["offsetZ"] = float(ox), float(oy), float(oz)
    save_cosmetics()
    broadcast_cosmetics()
    return True, f"Offset for {player_name or uuid} set to {ox} / {oy} / {oz}"


async def do_remove(target: str) -> tuple[bool, str]:
    resolved = await resolve_target(target)
    if not resolved:
        return False, f"Unknown player: {target}"
    uuid, player_name = resolved
    if cosmetics.pop(uuid, None) is None:
        return False, f"{player_name or uuid} has no cosmetics."
    save_cosmetics()
    broadcast_cosmetics()
    return True, f"Removed cosmetics from {player_name or uuid}"


async def do_msg(target: str, text: str) -> str:
    """Sends a chat message to one connected player (by name or uuid)."""
    t = target.strip().lower()
    payload = json.dumps({"type": "message", "text": text})
    sent = 0
    for ws, info in list(client_info.items()):
        if info.get("name", "").lower() == t or info.get("uuid", "") == t:
            if await _safe_send(ws, payload):
                sent += 1
    return f"Sent to {sent} client(s)." if sent else f"{target} is not connected."


def do_msgall(text: str) -> str:
    """Sends a chat message to every connected player."""
    if not clients:
        return "Nobody is connected."
    broadcast_all(json.dumps({"type": "message", "text": text}))
    return f"Sent to {len(clients)} client(s)."


def do_online() -> str:
    if not client_info:
        return "Nobody is connected."
    lines = [f"Online ({len(client_info)}):"]
    for info in client_info.values():
        lines.append(f"- {info.get('name') or '?'} ({info.get('uuid')})")
    return "\n".join(lines)


def do_list() -> tuple[bool, str]:
    if not cosmetics:
        return True, "No cosmetics set."
    lines = ["Cosmetics:"]
    for uuid, e in cosmetics.items():
        bits = []
        if e.get("name"):
            bits.append(f"name=\"{e['name']}\"")
        if any(e.get(k, 1.0) != 1.0 for k in ("sizeX", "sizeY", "sizeZ")):
            bits.append(f"size={e.get('sizeX', 1.0)}x{e.get('sizeY', 1.0)}x{e.get('sizeZ', 1.0)}")
        if any(e.get(k, 0.0) != 0.0 for k in ("offsetX", "offsetY", "offsetZ")):
            bits.append(f"offset={e.get('offsetX', 0.0)}/{e.get('offsetY', 0.0)}/{e.get('offsetZ', 0.0)}")
        lines.append(f"- {e.get('playerName') or uuid}: {', '.join(bits) or '(empty)'}")
    return True, "\n".join(lines)


# ---------------------------------------------------------------------------- websocket

async def run_admin_action(msg: dict) -> tuple[bool, str]:
    """Shared by the websocket admin packets AND the web panel. Token already validated."""
    action = msg.get("action", "")
    target = str(msg.get("target", ""))

    if action == "restore":
        # Admin client pushes its local backup after the server lost its data
        # (free cloud hosts wipe the disk on every restart). Merge, never clobber.
        data = msg.get("data")
        if not isinstance(data, dict):
            return False, "Bad restore data."
        added = 0
        for uuid, entry in data.items():
            if not UUID_RE.match(str(uuid)) or not isinstance(entry, dict):
                continue
            u = str(uuid).lower()
            if u in cosmetics:
                continue
            try:
                cosmetics[u] = {
                    "playerName": str(entry.get("playerName", "")),
                    "name": str(entry.get("name", "")),
                    "sizeX": float(entry.get("sizeX", 1.0)),
                    "sizeY": float(entry.get("sizeY", 1.0)),
                    "sizeZ": float(entry.get("sizeZ", 1.0)),
                    "offsetX": float(entry.get("offsetX", 0.0)),
                    "offsetY": float(entry.get("offsetY", 0.0)),
                    "offsetZ": float(entry.get("offsetZ", 0.0)),
                }
                added += 1
            except (TypeError, ValueError):
                continue
        if added:
            save_cosmetics()
            broadcast_cosmetics()
        return True, f"Restored {added} cosmetic(s)."

    if action == "name":
        return await do_name(target, str(msg.get("value", "")))
    if action == "size":
        try:
            sx = float(msg.get("sizeX", 1.0))
            sy = float(msg.get("sizeY", 1.0))
            sz = float(msg.get("sizeZ", 1.0))
        except (TypeError, ValueError):
            return False, "Bad size values."
        return await do_size(target, sx, sy, sz)
    if action == "offset":
        try:
            ox = float(msg.get("offsetX", 0.0))
            oy = float(msg.get("offsetY", 0.0))
            oz = float(msg.get("offsetZ", 0.0))
        except (TypeError, ValueError):
            return False, "Bad offset values."
        return await do_offset(target, ox, oy, oz)
    if action == "remove":
        return await do_remove(target)
    if action == "list":
        return do_list()
    if action == "online":
        return True, do_online()
    if action == "msg":
        return True, await do_msg(target, str(msg.get("value", "")))
    if action == "msgall":
        return True, do_msgall(str(msg.get("value", "")))
    return False, f"Unknown action: {action}"


async def handle_admin(ws, msg: dict) -> None:
    token = str(msg.get("token", ""))
    if not secrets.compare_digest(token, config["admin_token"]):
        ok, message = False, "Invalid admin key."
    else:
        ok, message = await run_admin_action(msg)
    await _safe_send(ws, json.dumps({"type": "admin_result", "ok": ok, "message": message}))


async def websocket_handler(request: web.Request) -> web.WebSocketResponse:
    # aiohttp-level ping every 55s stops proxies (Render's LB, Cloudflare) killing idle sockets.
    ws = web.WebSocketResponse(heartbeat=55)
    await ws.prepare(request)

    identified = False
    try:
        async for frame in ws:
            if frame.type != WSMsgType.TEXT:
                continue
            try:
                msg = json.loads(frame.data)
            except (json.JSONDecodeError, TypeError):
                continue
            if not isinstance(msg, dict):
                continue

            msg_type = msg.get("type")
            if msg_type == "hello":
                uuid = str(msg.get("uuid", ""))
                name = str(msg.get("name", ""))
                if UUID_RE.match(uuid):
                    uuid = uuid.lower()
                    if NAME_RE.match(name):
                        name_cache[name.lower()] = uuid
                        # keep stored playerName fresh (handles name changes)
                        if uuid in cosmetics:
                            cosmetics[uuid]["playerName"] = name
                    client_info[ws] = {"uuid": uuid, "name": name}
                    if not identified:
                        identified = True
                        clients.add(ws)
                        print(f"[+] {name or uuid} connected ({len(clients)} online)")
                    await _safe_send(ws, cosmetics_payload())
            elif msg_type == "admin":
                await handle_admin(ws, msg)
    finally:
        if identified:
            clients.discard(ws)
            info = client_info.pop(ws, None)
            who = (info or {}).get("name") or (info or {}).get("uuid") or "client"
            print(f"[-] {who} disconnected ({len(clients)} online)")
    return ws


async def root_handler(request: web.Request):
    """One route for everything: websocket upgrades go to the socket, anything else
    (Render health checks — GET or HEAD — uptime pingers, browsers, scanners) gets a 200."""
    if request.headers.get("Upgrade", "").lower() == "websocket":
        return await websocket_handler(request)
    return web.Response(text="Tweaky cosmetics server online\n")


# ---------------------------------------------------------------------------- web panel
# Browser admin UI at /panel — no length limits, works from anywhere (Render's shell is paid,
# and Minecraft chat caps commands at 256 chars, too short for gradient-JSON names).

PANEL_HTML = """<!DOCTYPE html>
<html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>Tweaky Cosmetics Panel</title>
<style>
  body{font-family:system-ui,sans-serif;background:#111318;color:#e8e8e8;max-width:640px;margin:24px auto;padding:0 14px}
  h1{font-size:1.3em;color:#7aa2ff} h2{font-size:1em;margin:18px 0 6px;color:#9db4d0}
  input,textarea{width:100%;box-sizing:border-box;background:#1c2027;color:#e8e8e8;border:1px solid #333;border-radius:6px;padding:8px;margin:3px 0;font-family:inherit}
  textarea{min-height:64px;font-family:ui-monospace,monospace;font-size:.9em}
  button{background:#2b57c4;color:#fff;border:0;border-radius:6px;padding:8px 14px;margin:4px 4px 4px 0;cursor:pointer}
  button:hover{background:#3968e0} .row{display:flex;gap:8px} .row>*{flex:1}
  pre{background:#0b0d10;border:1px solid #262a31;border-radius:6px;padding:10px;white-space:pre-wrap;word-break:break-word;min-height:80px}
  small{color:#8a93a3}
</style></head><body>
<h1>Tweaky Cosmetics Panel</h1>
<h2>Admin key</h2>
<input id="token" type="password" placeholder="admin token">
<h2>Custom name <small>(&amp; codes, &amp;#hex, or gradient JSON — any length)</small></h2>
<input id="nameTarget" placeholder="player name or uuid">
<textarea id="nameValue" placeholder='&#123;"version":5,"text":"...","colors":[...]&#125; or &amp;6&amp;lName'></textarea>
<button onclick="api('name',{target:v('nameTarget'),value:v('nameValue')})">Set name</button>
<h2>Size</h2>
<div class="row"><input id="sizeTarget" placeholder="player"><input id="sx" placeholder="x" value="1"><input id="sy" placeholder="y" value="1"><input id="sz" placeholder="z" value="1"></div>
<button onclick="api('size',{target:v('sizeTarget'),sizeX:+v('sx')||1,sizeY:+v('sy')||1,sizeZ:+v('sz')||1})">Set size</button>
<h2>Offset <small>(+y = up; x/z follow the body)</small></h2>
<div class="row"><input id="offTarget" placeholder="player"><input id="ox" placeholder="x" value="0"><input id="oy" placeholder="y" value="0"><input id="oz" placeholder="z" value="0"></div>
<button onclick="api('offset',{target:v('offTarget'),offsetX:+v('ox')||0,offsetY:+v('oy')||0,offsetZ:+v('oz')||0})">Set offset</button>
<h2>Message players <small>(leave player empty = everyone)</small></h2>
<input id="msgTarget" placeholder="player (optional)">
<textarea id="msgValue" placeholder="&amp;eHello! (any length)"></textarea>
<button onclick="v('msgTarget')?api('msg',{target:v('msgTarget'),value:v('msgValue')}):api('msgall',{value:v('msgValue')})">Send</button>
<h2>Manage</h2>
<div class="row"><input id="removeTarget" placeholder="player"><button onclick="api('remove',{target:v('removeTarget')})">Remove cosmetics</button></div>
<button onclick="api('list',{})">List cosmetics</button>
<button onclick="api('online',{})">Who's online</button>
<h2>Output</h2>
<pre id="out"></pre>
<script>
const v = id => document.getElementById(id).value.trim();
document.getElementById('token').value = localStorage.tweakyToken || '';
async function api(action, extra) {
  const token = v('token');
  localStorage.tweakyToken = token;
  const out = document.getElementById('out');
  try {
    const res = await fetch('panel/api', {method:'POST', headers:{'Content-Type':'application/json'},
      body: JSON.stringify(Object.assign({token, action}, extra))});
    const j = await res.json();
    out.textContent = (j.ok ? 'OK  ' : 'ERR ') + j.message + '\\n' + '-'.repeat(40) + '\\n' + out.textContent;
  } catch (e) { out.textContent = 'ERR ' + e + '\\n' + out.textContent; }
}
</script>
</body></html>"""


async def panel_page(request: web.Request) -> web.Response:
    return web.Response(text=PANEL_HTML, content_type="text/html")


async def panel_api(request: web.Request) -> web.Response:
    try:
        msg = await request.json()
        if not isinstance(msg, dict):
            raise ValueError
    except Exception:
        return web.json_response({"ok": False, "message": "Bad request."})

    if not secrets.compare_digest(str(msg.get("token", "")), config["admin_token"]):
        return web.json_response({"ok": False, "message": "Invalid admin key."})

    ok, message = await run_admin_action(msg)
    return web.json_response({"ok": ok, "message": message})


# ------------------------------------------------------------------------------ console

HELP = """Console commands:
  name <player> <custom name...>   set a custom name (& color codes, &#RRGGBB hex)
  size <player> <s> | <x> <y> <z>  set model size (negative = upside down)
  offset <player> <up> | <x> <y> <z>  offset the model (+y = up; x/z follow the body)
  remove <player>                  remove a player's cosmetics
  list                             list everyone with cosmetics
  online                           list connected players
  msg <player> <text...>           send a chat message to one connected player
  msgall <text...>                 send a chat message to everyone connected
  token                            print the admin token
  help                             this help"""


async def console() -> None:
    while True:
        try:
            line = (await asyncio.to_thread(input, "> ")).strip()
        except EOFError:
            # No stdin (running headless / as a service): keep serving forever without a console.
            await asyncio.get_running_loop().create_future()
            return
        except KeyboardInterrupt:
            return
        if not line:
            continue
        parts = line.split()
        cmd = parts[0].lower()
        try:
            if cmd == "name" and len(parts) >= 3:
                _, message = await do_name(parts[1], " ".join(parts[2:]))
            elif cmd == "size" and len(parts) == 3:
                s = float(parts[2])
                _, message = await do_size(parts[1], s, s, s)
            elif cmd == "size" and len(parts) == 5:
                _, message = await do_size(parts[1], float(parts[2]), float(parts[3]), float(parts[4]))
            elif cmd == "offset" and len(parts) == 3:
                _, message = await do_offset(parts[1], 0.0, float(parts[2]), 0.0)
            elif cmd == "offset" and len(parts) == 5:
                _, message = await do_offset(parts[1], float(parts[2]), float(parts[3]), float(parts[4]))
            elif cmd == "remove" and len(parts) == 2:
                _, message = await do_remove(parts[1])
            elif cmd == "list":
                _, message = do_list()
            elif cmd == "online":
                message = do_online()
            elif cmd == "msg" and len(parts) >= 3:
                message = await do_msg(parts[1], " ".join(parts[2:]))
            elif cmd == "msgall" and len(parts) >= 2:
                message = do_msgall(" ".join(parts[1:]))
            elif cmd == "token":
                message = f"Admin token: {config['admin_token']}"
            else:
                message = HELP
        except ValueError:
            message = "Bad number."
        print(message)


# --------------------------------------------------------------------------------- main

async def heartbeat() -> None:
    """Periodic app-level ping so hosts that count websocket messages as activity stay awake."""
    interval = int(os.environ.get("HEARTBEAT_SECONDS", "240"))
    while True:
        await asyncio.sleep(interval)
        if clients:
            broadcast_all(json.dumps({"type": "ping"}))


async def main() -> None:
    load_config()
    load_cosmetics()
    port = int(config["port"])
    print(f"Tweaky cosmetics server on port {port}  ({len(cosmetics)} cosmetic(s) loaded)")
    print("GitHub persistence: " + (f"ON ({GH_REPO}@{GH_BRANCH})" if GH_TOKEN else "OFF (set GITHUB_TOKEN to survive host restarts)"))
    print("Web admin panel: /panel (use the admin token below)")
    print(f"Admin token: {config['admin_token']}" + ("  (from ADMIN_TOKEN env)" if os.environ.get("ADMIN_TOKEN") else ""))
    print("  -> paste it into Tweaky's Cosmetics 'Admin Key' setting to use /tweaky cosmetics")
    print("Type 'help' for console commands.\n")

    app = web.Application()
    app.router.add_get("/panel", panel_page)
    app.router.add_post("/panel/api", panel_api)
    app.router.add_route("*", "/{tail:.*}", root_handler)
    runner = web.AppRunner(app)
    await runner.setup()
    site = web.TCPSite(runner, "0.0.0.0", port)
    await site.start()

    beat = asyncio.create_task(heartbeat())
    try:
        await console()
    finally:
        beat.cancel()
        await runner.cleanup()


if __name__ == "__main__":
    try:
        asyncio.run(main())
    except KeyboardInterrupt:
        pass
