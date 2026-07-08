# Tweaky Cosmetics Server

Gives players custom **names** and **model sizes** that everyone running Tweaky (with Cosmetics enabled) sees live.

## Run it

```
pip install aiohttp
python server.py
```

Hosted deployment: currently live on Render (free) at `wss://tweaky-cosmetics.onrender.com` — deployed from GitHub (server.py + requirements.txt), `ADMIN_TOKEN` set as an environment variable, `PORT` provided by Render.

## Surviving host restarts (GitHub persistence)

Render's free tier wipes the disk on every restart. With `GITHUB_TOKEN` set, the server mirrors
cosmetics to the repo's `data` branch (created automatically) and reloads them on boot — commits
there do NOT trigger Render deploys (it only watches `main`). Setup:
1. GitHub → Settings → Developer settings → Fine-grained tokens → Generate new token:
   Repository access = only `tweaky`, Permissions → **Contents: Read and write**.
2. Render dashboard → the service → Environment → add `GITHUB_TOKEN` = that token.
Optional env: `GITHUB_REPO` (default `Xmogus1/tweaky`), `GITHUB_BRANCH` (default `data`).

NOTE: the repo must stay **public** for the mod's Auto Update feature (release downloads are
anonymous). GitHub persistence works either way (token-authenticated).

First run creates `config.json` (port + a random **admin token**) and `cosmetics.json` (storage).
The admin token is printed at startup — keep it secret; anyone with it can grant cosmetics.

## Connect the mod

In Tweaky: **Visual → Cosmetics** → enable it, set **Server Address**:
- same PC: `ws://localhost:8765`
- friends: `ws://YOUR_IP:8765` (port-forward 8765) or your VPS/domain

## Give cosmetics

**In-game (recommended):** paste the admin token into the Cosmetics **Admin Key** setting, then:

```
/tweaky cosmetics name Steve &6&lKing Steve
/tweaky cosmetics size Steve 2
/tweaky cosmetics size Steve -1        (upside down)
/tweaky cosmetics remove Steve
/tweaky cosmetics list
```

**Or in this server's console:** `name Steve &6King Steve`, `size Steve 2`, `size Steve 1 2 1`, `remove Steve`, `list`, `token`.

Per-axis sizes also work in-game: `/tweaky cosmetics size Steve 2 1 2` (x y z).

## Message players from the panel

```
online                      who's connected
msg Steve &eHey you!        message one player (shows in their chat)
msgall &aServer restarting  message everyone
```


Changes broadcast instantly to every connected client. Player names are resolved to UUIDs via the Mojang API, so it works for players who never connected.
