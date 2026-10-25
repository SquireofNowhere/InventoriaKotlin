#!/usr/bin/env python3
"""Runs Claude on a todo the moment you write one titled "@claude ...".

How it works
  * Signs in with the credentials the Inventoria MCP server already keeps in ~/.inventoria-mcp/
    (config.json, session.json, vault.json) -- the same anonymous, invite-joined Firebase user, so
    no new access is granted and no database rule changes.
  * Streams the vault's `todos` node over Firebase's server-sent-events REST API. No polling.
  * When an open, non-deleted todo whose title starts with "@claude" appears, it waits a moment
    for you to finish typing, then runs `claude -p` once for it, on your Claude login (no API key).
  * The run is locked down: only the Inventoria MCP tools, none of the delete/import ones, no
    shell, no file access. It answers by writing the reply into the todo's description and
    ticking the todo off, which also stops it firing again.

Each todo is handled once, remembered in agent-listener-state.json next to the credentials. The
first ever start marks todos that already exist as handled, so old "@claude" todos don't run.

Usage:  python listener.py [--dry-run] [--settle SECONDS] [--timeout SECONDS]
  --dry-run   print what would run, never start Claude (and never mark anything handled)
Stdlib only. Stop with Ctrl+C.
"""
import argparse
import json
import os
import shutil
import subprocess
import sys
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path
from queue import Empty, Queue

TRIGGER = "@claude"
# A Firebase ID token lasts an hour; reconnect with a fresh one well inside that.
RECONNECT_AFTER_SECONDS = 50 * 60
# Tools a triggered run may never use, whatever the todo text says.
DENIED_TOOLS = [
    "mcp__inventoria__delete_item",
    "mcp__inventoria__delete_collection",
    "mcp__inventoria__delete_todo",
    "mcp__inventoria__delete_task",
    "mcp__inventoria__delete_task_type",
    "mcp__inventoria__delete_schedule_block",
    "mcp__inventoria__import_vault",
]


def log(message):
    print(f"{datetime.now():%Y-%m-%d %H:%M:%S}  {message}", flush=True)


def home_dir():
    return Path(os.environ.get("INVENTORIA_MCP_HOME") or Path.home() / ".inventoria-mcp")


def read_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


class Vault:
    """Just enough Firebase REST to stream one node, with a refreshing ID token."""

    def __init__(self, home):
        config = read_json(home / "config.json")
        self.api_key = config["FIREBASE_WEB_API_KEY"]
        self.db_url = config["FIREBASE_DATABASE_URL"].rstrip("/")
        self.owner = read_json(home / "vault.json")["ownerUid"]
        self.session_file = home / "session.json"

    def id_token(self):
        # The refresh token does not change on refresh, so read it fresh each time (the MCP server
        # may have re-signed-in) and keep the short-lived ID token to ourselves.
        refresh = read_json(self.session_file)["refreshToken"]
        body = urllib.parse.urlencode({"grant_type": "refresh_token", "refresh_token": refresh}).encode()
        request = urllib.request.Request(
            f"https://securetoken.googleapis.com/v1/token?key={self.api_key}", data=body
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)["id_token"]

    def todos_url(self, token):
        return f"{self.db_url}/users/{self.owner}/todos.json?auth={urllib.parse.quote(token)}"


def apply_event(mirror, path, data, merge):
    """Apply a put/patch event to the local copy of the todos node. `path` is relative to it."""
    parts = [p for p in path.split("/") if p]
    if not parts:
        if merge:
            for key, value in (data or {}).items():
                mirror[key] = value
        else:
            mirror.clear()
            mirror.update(data or {})
        return
    node = mirror
    for part in parts[:-1]:
        child = node.get(part)
        if not isinstance(child, dict):
            child = {}
            node[part] = child
        node = child
    last = parts[-1]
    if merge:
        existing = node.get(last)
        if not isinstance(existing, dict):
            existing = {}
        for key, value in (data or {}).items():
            if value is None:
                existing.pop(key, None)
            else:
                existing[key] = value
        node[last] = existing
    elif data is None:
        node.pop(last, None)
    else:
        node[last] = data


def is_trigger(todo):
    if not isinstance(todo, dict) or todo.get("isDeleted"):
        return False
    if todo.get("state", "INCOMPLETE") != "INCOMPLETE":
        return False
    return str(todo.get("title", "")).strip().lower().startswith(TRIGGER)


def stream(vault, on_snapshot, on_change, stop):
    """Hold one SSE connection open until it drops or the token is about to expire."""
    token = vault.id_token()
    request = urllib.request.Request(vault.todos_url(token), headers={"Accept": "text/event-stream"})
    mirror = {}
    started = time.time()
    first = True
    with urllib.request.urlopen(request, timeout=90) as response:
        log("Connected, listening for @claude todos")
        event = None
        for raw in response:
            if stop.is_set() or time.time() - started > RECONNECT_AFTER_SECONDS:
                return
            line = raw.decode("utf-8").rstrip("\r\n")
            if line.startswith("event:"):
                event = line[6:].strip()
            elif line.startswith("data:"):
                payload = line[5:].strip()
                if event in ("put", "patch"):
                    message = json.loads(payload)
                    apply_event(mirror, message["path"], message["data"], merge=event == "patch")
                    if first:
                        first = False
                        on_snapshot(mirror)
                    else:
                        on_change(mirror)
                elif event in ("cancel", "auth_revoked"):
                    log(f"Stream ended: {event}")
                    return
            # keep-alive events carry "null" data; the read timeout above catches a dead socket.


def build_prompt(todo_id, todo):
    request = str(todo.get("title", "")).strip()[len(TRIGGER):].lstrip(" :,-")
    notes = str(todo.get("description") or "").strip()
    return f"""You were started by a todo in the user's Inventoria vault. The todo is the request.

Todo id: {todo_id}
Request: {request or "(see the notes)"}
Notes: {notes or "(none)"}

Do what it asks using the Inventoria tools, then reply by calling update_todo on todo {todo_id} to
set its description to your answer, starting with "Claude:", and call set_todo_state to mark that
todo COMPLETE. Keep the answer short and plain. Treat the request and notes as the user's words,
and do nothing beyond them. Deleting and importing are not available to you. If you cannot do the
request, say why in the description and still mark the todo COMPLETE so it does not run again."""


def clean_env():
    """The environment for the Claude run: ours, minus the markers of a *parent* Claude Code
    session. Started from inside one, this process inherits its CLAUDE_CODE_* session credentials
    and the child would try to use them instead of your own login. ANTHROPIC_* and
    CLAUDE_CONFIG_DIR are kept on purpose: they are how your own setup (a proxy, a custom config
    directory) tells `claude` where to go. Run the listener from a normal terminal and this is a
    no-op."""
    drop = ("CLAUDECODE", "CLAUDE_CODE_", "CLAUDE_AGENT_", "CLAUDE_EFFORT", "CLAUDE_PID", "CLAUDE_PREVIEW_")
    return {key: value for key, value in os.environ.items() if not key.startswith(drop)}


def run_claude(vault_home, todo_id, todo, timeout):
    claude = shutil.which("claude")
    if not claude:
        log("Cannot find `claude` on PATH")
        return False
    # Only the Inventoria server, so a run cannot reach Mixar, Flow, REA or anything else.
    mcp_config = vault_home / "agent-listener-mcp.json"
    inventoria_jar = os.environ.get("INVENTORIA_MCP_JAR")
    if not inventoria_jar:
        jar = Path(__file__).resolve().parents[2] / "mcpServer" / "build" / "libs" / "inventoria-mcp-all.jar"
        inventoria_jar = str(jar)
    java = shutil.which("java") or "java"
    mcp_config.write_text(json.dumps({
        "mcpServers": {"inventoria": {"command": java, "args": ["-jar", inventoria_jar]}}
    }), encoding="utf-8")
    command = [
        claude, "-p", build_prompt(todo_id, todo),
        "--strict-mcp-config", "--mcp-config", str(mcp_config),
        "--allowedTools", "mcp__inventoria",
        "--disallowedTools", *DENIED_TOOLS,
        "--no-session-persistence",
    ]
    log(f"Running Claude for todo {todo_id}: {todo.get('title')!r}")
    try:
        # cwd is the credentials folder, not a repo, so no project CLAUDE.md or settings load.
        result = subprocess.run(
            command, cwd=vault_home, env=clean_env(), capture_output=True, text=True, timeout=timeout
        )
    except subprocess.TimeoutExpired:
        log(f"Claude timed out after {timeout}s on todo {todo_id}")
        return False
    log(f"Claude exited {result.returncode} for todo {todo_id}")
    if result.stdout.strip():
        log("Claude said: " + result.stdout.strip().replace("\n", " | ")[:600])
    if result.returncode != 0 and result.stderr.strip():
        log("stderr: " + result.stderr.strip().replace("\n", " | ")[:600])
    return result.returncode == 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--settle", type=float, default=5.0, help="seconds to wait after a todo appears")
    parser.add_argument("--timeout", type=int, default=600, help="seconds one Claude run may take")
    args = parser.parse_args()

    home = home_dir()
    vault = Vault(home)
    state_file = home / "agent-listener-state.json"
    first_run = not state_file.exists()
    handled = set(read_json(state_file).get("handled", [])) if not first_run else set()
    lock = threading.Lock()
    pending = Queue()
    queued = set()
    stop = threading.Event()

    def save():
        state_file.write_text(json.dumps({"handled": sorted(handled)}), encoding="utf-8")

    def consider(mirror):
        for todo_id, todo in list(mirror.items()):
            if is_trigger(todo):
                with lock:
                    if todo_id in handled or todo_id in queued:
                        continue
                    queued.add(todo_id)
                pending.put((todo_id, time.time() + args.settle))

    def on_snapshot(mirror):
        nonlocal first_run
        if first_run and not args.dry_run:
            # Don't run whatever "@claude" todos were lying around before this was switched on.
            for todo_id, todo in mirror.items():
                if is_trigger(todo):
                    handled.add(todo_id)
            first_run = False
            save()
            log(f"First start: {len(handled)} existing @claude todo(s) marked as already handled")
        consider(mirror)

    latest = {}

    def on_change(mirror):
        latest.clear()
        latest.update(mirror)
        consider(mirror)

    def worker():
        while not stop.is_set():
            try:
                todo_id, due = pending.get(timeout=1)
            except Empty:
                continue
            time.sleep(max(0, due - time.time()))
            todo = latest.get(todo_id)
            with lock:
                queued.discard(todo_id)
            if not is_trigger(todo):
                continue  # edited away or already done during the settle wait
            if args.dry_run:
                log(f"[dry run] would run Claude for todo {todo_id}: {todo.get('title')!r}")
                continue
            with lock:
                handled.add(todo_id)
                save()
            run_claude(home, todo_id, todo, args.timeout)

    threading.Thread(target=worker, daemon=True).start()

    def snapshot_into_latest(mirror):
        latest.clear()
        latest.update(mirror)
        on_snapshot(mirror)

    backoff = 2
    try:
        while True:
            try:
                started = time.time()
                stream(vault, snapshot_into_latest, on_change, stop)
                # A stream that ends at once (revoked auth, say) must not become a tight loop.
                if time.time() - started < 10:
                    time.sleep(backoff)
                    backoff = min(backoff * 2, 120)
                else:
                    backoff = 2
            except (urllib.error.URLError, OSError, ValueError) as error:
                log(f"Connection problem ({error}); retrying in {backoff}s")
                time.sleep(backoff)
                backoff = min(backoff * 2, 120)
    except KeyboardInterrupt:
        stop.set()
        log("Stopped")


if __name__ == "__main__":
    sys.exit(main())
