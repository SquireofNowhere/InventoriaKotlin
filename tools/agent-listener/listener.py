#!/usr/bin/env python3
"""Answers Claude requests from Inventoria, using your own Claude login.

Two things start a run:
  * The in-app chat. The app writes a message to `agent_chat`; this answers it and writes the
    reply back, which the app shows as Claude's bubble.
  * A todo titled "@claude ...". The reply goes into the todo's description and it is ticked off.

How it works
  * Signs in with the credentials the Inventoria MCP server already keeps in ~/.inventoria-mcp/
    (config.json, session.json, vault.json) -- the same anonymous, invite-joined Firebase user.
  * Streams the vault's `todos` and `agent_chat` nodes over Firebase's server-sent-events REST API.
    No polling.
  * Each trigger starts one `claude -p` run, one at a time, locked down to the Inventoria MCP tools
    (the delete tools and import_vault are denied; no shell, no file access).

The chat's `agent_chat` node needs the rule in database.rules.json pasted into the Firebase console
(rules are not deployed from the repo), or the replies are refused.

A todo is handled once, remembered in agent-listener-state.json next to the credentials; the first
ever start marks existing "@claude" todos as handled. A chat message's own `status` field records
where it is (pending -> working -> done/error), so nothing is remembered for chat.

Usage:  python listener.py [--dry-run] [--settle SECONDS] [--timeout SECONDS]
  --dry-run   print what would run, never start Claude or change anything
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
CHAT_NODE = "agent_chat"
# How much of the conversation a chat run is shown.
CHAT_CONTEXT_MESSAGES = 20
# A Firebase ID token lasts an hour; reconnect (and refresh) well inside that.
RECONNECT_AFTER_SECONDS = 50 * 60
# Tools a triggered run may never use, whatever the text says.
DENIED_TOOLS = [
    "mcp__inventoria__delete_item",
    "mcp__inventoria__delete_collection",
    "mcp__inventoria__delete_todo",
    "mcp__inventoria__delete_task",
    "mcp__inventoria__delete_task_type",
    "mcp__inventoria__delete_schedule_block",
    "mcp__inventoria__import_vault",
]

# One Claude run at a time, whichever trigger started it.
run_lock = threading.Lock()

# Set by --use-api-key: run on whatever `claude` is configured with (an API key, a router) instead of
# forcing the subscription login.
USE_API_KEY = False


def log(message):
    print(f"{datetime.now():%Y-%m-%d %H:%M:%S}  {message}", flush=True)


def home_dir():
    return Path(os.environ.get("INVENTORIA_MCP_HOME") or Path.home() / ".inventoria-mcp")


def read_json(path):
    with open(path, encoding="utf-8") as handle:
        return json.load(handle)


class Vault:
    """Just enough Firebase REST to stream a node and write to it, with a refreshing ID token."""

    def __init__(self, home):
        config = read_json(home / "config.json")
        self.api_key = config["FIREBASE_WEB_API_KEY"]
        self.db_url = config["FIREBASE_DATABASE_URL"].rstrip("/")
        self.owner = read_json(home / "vault.json")["ownerUid"]
        self.session_file = home / "session.json"
        self._token = None
        self._token_at = 0.0
        self._lock = threading.Lock()

    def id_token(self, fresh=False):
        with self._lock:
            if fresh or self._token is None or time.time() - self._token_at > 45 * 60:
                # The refresh token does not change on refresh, so read it each time (the MCP server
                # may have re-signed-in) and keep the short-lived ID token to ourselves.
                refresh = read_json(self.session_file)["refreshToken"]
                body = urllib.parse.urlencode({"grant_type": "refresh_token", "refresh_token": refresh}).encode()
                request = urllib.request.Request(
                    f"https://securetoken.googleapis.com/v1/token?key={self.api_key}", data=body
                )
                with urllib.request.urlopen(request, timeout=30) as response:
                    self._token = json.load(response)["id_token"]
                self._token_at = time.time()
            return self._token

    def url(self, node, token):
        return f"{self.db_url}/users/{self.owner}/{node}.json?auth={urllib.parse.quote(token)}"

    def write(self, node, value, method="PUT"):
        """PUT replaces the node, PATCH merges into it. Raises HTTPError (401 = a rule refused it)."""
        body = json.dumps(value).encode("utf-8")
        request = urllib.request.Request(
            self.url(node, self.id_token()), data=body, method=method,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            response.read()


def apply_event(mirror, path, data, merge):
    """Apply a put/patch event to the local copy of a node. `path` is relative to that node."""
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


def stream_once(vault, node, on_snapshot, on_change, stop):
    """Hold one SSE connection to `node` open until it drops or the token is about to expire."""
    request = urllib.request.Request(
        vault.url(node, vault.id_token(fresh=True)), headers={"Accept": "text/event-stream"}
    )
    mirror = {}
    started = time.time()
    first = True
    with urllib.request.urlopen(request, timeout=90) as response:
        log(f"Listening on {node}")
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
                    log(f"Stream on {node} ended: {event}")
                    return
            # keep-alive events carry "null" data; the read timeout above catches a dead socket.


def stream_forever(vault, node, on_snapshot, on_change, stop):
    backoff = 2
    while not stop.is_set():
        try:
            started = time.time()
            stream_once(vault, node, on_snapshot, on_change, stop)
            if time.time() - started < 10:  # ended at once (revoked auth, say): don't spin
                time.sleep(backoff)
                backoff = min(backoff * 2, 120)
            else:
                backoff = 2
        except (urllib.error.URLError, OSError, ValueError) as error:
            log(f"Connection problem on {node} ({error}); retrying in {backoff}s")
            time.sleep(backoff)
            backoff = min(backoff * 2, 120)


# --- running Claude ---------------------------------------------------------------------------

def clean_env():
    """The environment for the Claude run: ours, minus the markers of a *parent* Claude Code
    session. Started from inside one, this process inherits its CLAUDE_CODE_* session credentials
    and the child would try to use them instead of your own login. ANTHROPIC_* and
    CLAUDE_CONFIG_DIR are kept on purpose: they are how your own setup (a proxy, a custom config
    directory) tells `claude` where to go. Run the listener from a normal terminal and this is a
    no-op."""
    drop = ("CLAUDECODE", "CLAUDE_CODE_", "CLAUDE_AGENT_", "CLAUDE_EFFORT", "CLAUDE_PID", "CLAUDE_PREVIEW_")
    # CLAUDE_CODE_OAUTH_TOKEN (from `claude setup-token`) is how a subscription is passed to a
    # headless run, so it is the one CLAUDE_CODE_ variable that stays.
    env = {
        key: value for key, value in os.environ.items()
        if key == "CLAUDE_CODE_OAUTH_TOKEN" or not key.startswith(drop)
    }
    if not USE_API_KEY:
        # An API key in the environment takes priority over a subscription login and bills the
        # key's own credit, which is not what this is for.
        env.pop("ANTHROPIC_API_KEY", None)
        env.pop("ANTHROPIC_AUTH_TOKEN", None)
    return env


def invoke_claude(home, prompt, timeout):
    """One locked-down `claude -p` run. Returns (ok, stdout, error_text)."""
    claude = shutil.which("claude")
    if not claude:
        return False, "", "`claude` is not on PATH"
    # Only the Inventoria server, so a run cannot reach Mixar, Flow, REA or anything else.
    jar = os.environ.get("INVENTORIA_MCP_JAR") or str(
        Path(__file__).resolve().parents[2] / "mcpServer" / "build" / "libs" / "inventoria-mcp-all.jar"
    )
    mcp_config = home / "agent-listener-mcp.json"
    mcp_config.write_text(json.dumps({
        "mcpServers": {"inventoria": {"command": shutil.which("java") or "java", "args": ["-jar", jar]}}
    }), encoding="utf-8")
    command = [
        claude, "-p", prompt,
        "--strict-mcp-config", "--mcp-config", str(mcp_config),
        "--allowedTools", "mcp__inventoria",
        "--disallowedTools", *DENIED_TOOLS,
        "--no-session-persistence",
    ]
    if not USE_API_KEY:
        # Skip ~/.claude/settings.json: it can carry an apiKeyHelper or a proxy URL that would send
        # the run to an API key instead of the subscription login.
        command += ["--setting-sources", "project"]
    try:
        # cwd is the credentials folder, not a repo, so no project CLAUDE.md or settings load.
        result = subprocess.run(
            command, cwd=home, env=clean_env(), capture_output=True, text=True, timeout=timeout
        )
    except subprocess.TimeoutExpired:
        return False, "", f"timed out after {timeout}s"
    out = (result.stdout or "").strip()
    # `claude -p` prints many failures (auth, limits) on stdout, so a failed run's reason can be
    # in either stream.
    detail = (result.stderr or "").strip() or out
    return result.returncode == 0, out, detail or f"exit code {result.returncode}"


def todo_prompt(todo_id, todo):
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


def chat_prompt(history):
    lines = []
    for message in history:
        who = "User" if message.get("role") == "user" else "Claude"
        lines.append(f"{who}: {message.get('text', '')}")
    transcript = "\n".join(lines)
    return f"""You are Claude, chatting with the user inside their Inventoria app, on their phone.
You can read and edit their vault (todos, items, collections, timers, the schedule) with the
Inventoria tools. When a question is about their data, look it up with the tools rather than
guessing, and when they ask for a change, make it and say what you did. Deleting and importing are
not available to you.

Write for a small chat bubble: short, plain text, no markdown headings or tables.

The conversation so far, oldest first:
{transcript}

Reply to the last User message only."""


# --- the two triggers ---------------------------------------------------------------------------

def is_todo_trigger(todo):
    if not isinstance(todo, dict) or todo.get("isDeleted"):
        return False
    if todo.get("state", "INCOMPLETE") != "INCOMPLETE":
        return False
    return str(todo.get("title", "")).strip().lower().startswith(TRIGGER)


def is_chat_trigger(message):
    return isinstance(message, dict) and message.get("role") == "user" and message.get("status") == "pending"


def chat_history(mirror, upto_id):
    """The messages up to and including `upto_id`, oldest first, capped. Push ids sort by time."""
    ordered = [mirror[key] for key in sorted(mirror) if isinstance(mirror[key], dict) and key <= upto_id]
    return ordered[-CHAT_CONTEXT_MESSAGES:]


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--settle", type=float, default=5.0, help="seconds to wait after a todo appears")
    parser.add_argument("--timeout", type=int, default=600, help="seconds one Claude run may take")
    parser.add_argument("--use-api-key", action="store_true",
                        help="use the environment's ANTHROPIC_API_KEY / user settings instead of the subscription login")
    args = parser.parse_args()
    global USE_API_KEY
    USE_API_KEY = args.use_api_key

    home = home_dir()
    vault = Vault(home)
    stop = threading.Event()

    # ---- todos ----
    state_file = home / "agent-listener-state.json"
    first_run = not state_file.exists()
    handled = set(read_json(state_file).get("handled", [])) if not first_run else set()
    todo_lock = threading.Lock()
    todo_queue = Queue()
    todo_queued = set()
    todos = {}

    def save_state():
        state_file.write_text(json.dumps({"handled": sorted(handled)}), encoding="utf-8")

    def consider_todos(mirror):
        for todo_id, todo in list(mirror.items()):
            if is_todo_trigger(todo):
                with todo_lock:
                    if todo_id in handled or todo_id in todo_queued:
                        continue
                    todo_queued.add(todo_id)
                todo_queue.put((todo_id, time.time() + args.settle))

    def on_todos_snapshot(mirror):
        nonlocal first_run
        todos.clear()
        todos.update(mirror)
        if first_run and not args.dry_run:
            # Don't run whatever "@claude" todos were lying around before this was switched on.
            for todo_id, todo in mirror.items():
                if is_todo_trigger(todo):
                    handled.add(todo_id)
            first_run = False
            save_state()
            log(f"First start: {len(handled)} existing @claude todo(s) marked as already handled")
        consider_todos(mirror)

    def on_todos_change(mirror):
        todos.clear()
        todos.update(mirror)
        consider_todos(mirror)

    def todo_worker():
        while not stop.is_set():
            try:
                todo_id, due = todo_queue.get(timeout=1)
            except Empty:
                continue
            time.sleep(max(0, due - time.time()))
            todo = todos.get(todo_id)
            with todo_lock:
                todo_queued.discard(todo_id)
            if not is_todo_trigger(todo):
                continue  # edited away or already done during the settle wait
            if args.dry_run:
                log(f"[dry run] would run Claude for todo {todo_id}: {todo.get('title')!r}")
                continue
            with todo_lock:
                handled.add(todo_id)
                save_state()
            log(f"Running Claude for todo {todo_id}: {todo.get('title')!r}")
            with run_lock:
                ok, out, err = invoke_claude(home, todo_prompt(todo_id, todo), args.timeout)
            log(f"Todo {todo_id}: {'ok' if ok else 'FAILED'} - {(out if ok else err)[:400]!r}")

    # ---- chat ----
    chat_queue = Queue()
    chat_queued = set()
    in_flight = set()
    chat_lock = threading.Lock()
    chat = {}

    def consider_chat(mirror):
        for message_id, message in list(mirror.items()):
            if is_chat_trigger(message):
                with chat_lock:
                    if message_id in chat_queued or message_id in in_flight:
                        continue
                    chat_queued.add(message_id)
                chat_queue.put(message_id)

    def reply(user_id, text, status):
        """Write Claude's bubble, then close the user's message. The reply's key sorts straight
        after its question and before any later one ('~' is not in the push-id alphabet)."""
        vault.write(f"{CHAT_NODE}/{user_id}~r", {
            "role": "assistant", "text": text, "createdAt": int(time.time() * 1000), "replyTo": user_id,
        })
        vault.write(f"{CHAT_NODE}/{user_id}", {"status": status}, method="PATCH")

    def recover_stuck(mirror):
        # A message left "working" by a listener that died mid-run would spin in the app forever.
        for message_id, message in list(mirror.items()):
            if isinstance(message, dict) and message.get("role") == "user" and message.get("status") == "working":
                with chat_lock:
                    if message_id in in_flight:
                        continue
                if args.dry_run:
                    log(f"[dry run] would mark stuck message {message_id} as failed")
                    continue
                try:
                    reply(message_id, "The listener stopped while working on this. Please ask again.", "error")
                    log(f"Marked stuck chat message {message_id} as failed")
                except urllib.error.URLError as error:
                    log(f"Could not recover chat message {message_id}: {error}")

    def on_chat_snapshot(mirror):
        chat.clear()
        chat.update(mirror)
        recover_stuck(mirror)
        consider_chat(mirror)

    def on_chat_change(mirror):
        chat.clear()
        chat.update(mirror)
        consider_chat(mirror)

    def chat_worker():
        while not stop.is_set():
            try:
                message_id = chat_queue.get(timeout=1)
            except Empty:
                continue
            with chat_lock:
                chat_queued.discard(message_id)
                message = chat.get(message_id)
                if not is_chat_trigger(message):
                    continue
                in_flight.add(message_id)
            try:
                if args.dry_run:
                    log(f"[dry run] would answer chat message {message_id}: {message.get('text')!r}")
                    continue
                try:
                    vault.write(f"{CHAT_NODE}/{message_id}", {"status": "working"}, method="PATCH")
                except urllib.error.HTTPError as error:
                    log(f"Could not claim chat message {message_id} (HTTP {error.code}). If this is 401, the "
                        f"{CHAT_NODE} rule from database.rules.json is not in the Firebase console yet.")
                    continue
                log(f"Answering chat message {message_id}: {message.get('text')!r}")
                with run_lock:
                    ok, out, err = invoke_claude(home, chat_prompt(chat_history(chat, message_id)), args.timeout)
                if ok and out:
                    text, status = out, "done"
                else:
                    log(f"Chat run failed: {err[:400]!r}")
                    text, status = f"Sorry, I couldn't answer that: {(err or 'no reply')[:200]}", "error"
                try:
                    reply(message_id, text, status)
                except urllib.error.URLError as error:
                    log(f"Could not write the reply to {message_id}: {error}")
            finally:
                with chat_lock:
                    in_flight.discard(message_id)

    for target in (todo_worker, chat_worker):
        threading.Thread(target=target, daemon=True).start()
    for node, snapshot, change in (
        ("todos", on_todos_snapshot, on_todos_change),
        (CHAT_NODE, on_chat_snapshot, on_chat_change),
    ):
        threading.Thread(
            target=stream_forever, args=(vault, node, snapshot, change, stop), daemon=True
        ).start()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        stop.set()
        log("Stopped")


if __name__ == "__main__":
    sys.exit(main())
