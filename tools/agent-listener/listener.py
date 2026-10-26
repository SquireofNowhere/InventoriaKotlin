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
import re
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
# The listener's heartbeat lives beside the messages (it has no `text`, so the app skips it as one).
# '_' sorts after every push id, so it never lands inside a conversation's history.
LISTENER_KEY = "_listener"
HEARTBEAT_SECONDS = 20
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


# The MCP server jar is built for this Java (mcpServer/build.gradle.kts: jvmToolchain(21)). A `java`
# first on PATH that is older makes it die with UnsupportedClassVersionError, which Claude only
# reports as the server "failing to connect".
MIN_JAVA = 21
_java_cache = {}


def java_major(java):
    """The major version of a `java` executable (8 for 1.8), or 0 if it will not say."""
    try:
        result = subprocess.run([java, "-version"], capture_output=True, text=True, timeout=20)
    except (OSError, subprocess.SubprocessError):
        return 0
    match = re.search(r'version "(\d+)(?:\.(\d+))?', (result.stderr or "") + (result.stdout or ""))
    if not match:
        return 0
    major = int(match.group(1))
    return int(match.group(2) or 0) if major == 1 else major


def java_candidates():
    """Places a suitable Java might be, best guesses first. INVENTORIA_JAVA wins if set."""
    found = []
    for path in (os.environ.get("INVENTORIA_JAVA"), shutil.which("java")):
        if path:
            found.append(path)
    java_home = os.environ.get("JAVA_HOME")
    if java_home:
        found.append(str(Path(java_home) / "bin" / "java"))
    roots = [
        Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Android" / "Android Studio" / "jbr",
        *sorted((Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Java").glob("*"), reverse=True),
        *sorted((Path(os.environ.get("ProgramFiles", r"C:\Program Files")) / "Eclipse Adoptium").glob("*"), reverse=True),
        *sorted((Path.home() / ".gradle" / "jdks").glob("*"), reverse=True),
    ]
    for root in roots:
        for name in ("java.exe", "java"):
            if (root / "bin" / name).exists():
                found.append(str(root / "bin" / name))
                break
    return found


def find_java():
    """(path, None) for the first Java that is MIN_JAVA or newer, else (None, why-not). Cached."""
    if "result" in _java_cache:
        return _java_cache["result"]
    seen = []
    for candidate in dict.fromkeys(java_candidates()):
        major = java_major(candidate)
        seen.append(f"{candidate} (Java {major or '?'})")
        if major >= MIN_JAVA:
            _java_cache["result"] = (candidate, None)
            return _java_cache["result"]
    reason = (
        f"The Inventoria MCP server needs Java {MIN_JAVA} or newer, and none was found"
        + (f" (looked at: {'; '.join(seen)})" if seen else "")
        + ". Install one, or set INVENTORIA_JAVA to its java executable."
    )
    return None, reason  # not cached: installing Java should not need a restart


def invoke_claude(home, prompt, timeout, on_event=None):
    """One locked-down `claude -p` run. Returns (ok, stdout, error_text)."""
    claude = shutil.which("claude")
    if not claude:
        return False, "", "`claude` is not on PATH"
    # Only the Inventoria server, so a run cannot reach Mixar, Flow, REA or anything else.
    jar = os.environ.get("INVENTORIA_MCP_JAR") or str(
        Path(__file__).resolve().parents[2] / "mcpServer" / "build" / "libs" / "inventoria-mcp-all.jar"
    )
    java, java_problem = find_java()
    if not java:
        # Without this the server dies at startup, Claude runs with no tools, and the user's chat
        # gets a puzzling "I couldn't check" instead of the actual reason.
        return False, "", java_problem
    mcp_config = home / "agent-listener-mcp.json"
    mcp_config.write_text(json.dumps({
        "mcpServers": {"inventoria": {"command": java, "args": ["-jar", jar]}}
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
        if on_event:
            result = run_streaming(command, home, clean_env(), timeout, on_event)
        else:
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


# What a tool call looks like to someone watching the chat. Anything not listed falls back to a
# phrase built from the tool's own name, so a new tool still reads sensibly.
TOOL_LABELS = {
    "vault_summary": ("looking", "Looking over your vault"),
    "time_report": ("looking", "Building a time report"),
    "todo_stats": ("looking", "Counting up your todos"),
    "export_vault": ("looking", "Reading the whole vault"),
    "bulk_update_todos": ("changing", "Updating several todos"),
    "bulk_move_items": ("changing", "Moving several items"),
    "batch_create": ("changing", "Creating several things"),
    "set_todo_state": ("changing", "Ticking a todo"),
    "set_equipped": ("changing", "Changing what's equipped"),
    "add_item_image": ("changing", "Adding a photo"),
    "remove_item_image": ("changing", "Removing a photo"),
    "set_item_profile_picture": ("changing", "Setting a photo"),
}
READ_VERBS = ("list", "get", "read", "search", "find")
WRITE_VERBS = {
    "create": "Creating", "update": "Updating", "set": "Changing", "move": "Moving", "link": "Linking",
    "unlink": "Unlinking", "add": "Adding", "remove": "Removing", "start": "Starting", "pause": "Pausing",
    "resume": "Resuming", "stop": "Stopping", "rename": "Renaming", "restore": "Restoring",
    "import": "Importing", "delete": "Deleting",
}


def describe_tool(tool_name):
    """(phase, label) for a tool call: 'looking' for reads, 'changing' for writes."""
    if "inventoria" not in tool_name:
        # Claude's own plumbing (it looks tools up with ToolSearch before using them): not something
        # the user asked for, so it reads as plain thinking.
        return "thinking", None
    name = tool_name.rsplit("__", 1)[-1]
    if name in TOOL_LABELS:
        return TOOL_LABELS[name]
    verb, _, rest = name.partition("_")
    noun = rest.replace("_", " ") or "things"
    if verb in READ_VERBS:
        return "looking", f"Looking up {noun}"
    if verb in WRITE_VERBS:
        return "changing", f"{WRITE_VERBS[verb]} {noun}"
    return "looking", f"Using {name.replace('_', ' ')}"


def describe_event(event):
    """(phase, label-or-None) for one stream-json event from `claude -p`, or None if it says nothing
    about what Claude is doing. Phases: thinking, looking, changing, writing."""
    kind = event.get("type")
    if kind == "system":
        return ("thinking", None) if event.get("subtype") == "init" else None
    if kind == "user":
        # A tool has answered; Claude is reading the result and deciding what next.
        return "thinking", None
    if kind != "assistant":
        return None
    found = None
    for block in (event.get("message") or {}).get("content") or []:
        block_type = block.get("type")
        if block_type == "tool_use":
            found = describe_tool(block.get("name", ""))
        elif found is None and block_type == "thinking":
            found = ("thinking", None)
        elif found is None and block_type == "text":
            found = ("writing", None)
    return found


def make_progress_reporter(write_activity):
    """Turns stream events into `activity` updates on the chat message, skipping repeats and keeping
    a burst of tool calls from becoming a burst of writes. A change of phase always goes through; a
    new label inside the same phase waits at least a second."""
    state = {"last": None, "at": 0.0}

    def report(event):
        if event.get("type") == "system" and event.get("subtype") == "init":
            # The first thing a run says is which MCP servers started. If ours did not, Claude carries
            # on without tools and answers "I couldn't check" -- say why here, where the user can see it.
            for server in event.get("mcp_servers") or []:
                if server.get("status") != "connected":
                    log(f"MCP server {server.get('name')!r} did not start ({server.get('status')}); "
                        f"this run has no vault tools. Check the Java version (needs {MIN_JAVA}+) and the jar.")
        described = describe_event(event)
        if described is None or described == state["last"]:
            return
        now = time.time()
        if state["last"] and described[0] == state["last"][0] and now - state["at"] < 1.0:
            return
        state["last"], state["at"] = described, now
        try:
            write_activity(*described)
        except Exception as error:  # progress is a courtesy; a failed write must never fail the run
            log(f"Could not report progress: {error}")

    return report


def run_streaming(command, cwd, env, timeout, on_event):
    """Like subprocess.run(capture_output=True) for a `claude -p` command, but reads its
    stream-json output as it arrives and hands each event to `on_event`. The returned
    CompletedProcess carries the final reply as stdout, so callers treat it like a plain run."""
    process = subprocess.Popen(
        command + ["--output-format", "stream-json", "--verbose"], cwd=cwd, env=env,
        stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True, encoding="utf-8", errors="replace",
    )
    timed_out = threading.Event()

    def kill():
        timed_out.set()
        process.kill()

    timer = threading.Timer(timeout, kill)
    timer.start()
    errors = []
    reader = threading.Thread(target=lambda: errors.append(process.stderr.read()), daemon=True)
    reader.start()
    final = None
    stray = []
    try:
        for line in process.stdout:
            line = line.strip()
            if not line:
                continue
            try:
                event = json.loads(line)
            except ValueError:
                stray.append(line)
                continue
            if isinstance(event, dict):
                if event.get("type") == "result":
                    final = event
                try:
                    on_event(event)
                except Exception as error:
                    log(f"Could not handle a Claude event: {error}")
        process.wait()
    finally:
        timer.cancel()
    reader.join(timeout=5)
    if timed_out.is_set():
        raise subprocess.TimeoutExpired(command, timeout)
    returncode = process.returncode
    reply = (final or {}).get("result") or ""
    if final and final.get("is_error") and returncode == 0:
        returncode = 1
    # No result event at all (a crash, an unsupported flag): the raw output is the best explanation.
    return subprocess.CompletedProcess(command, returncode, reply or "\n".join(stray), "".join(errors))


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
        # null deletes a key, so the progress line goes away with the wait.
        vault.write(
            f"{CHAT_NODE}/{user_id}", {"status": status, "activity": None, "activityText": None}, method="PATCH"
        )

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
                    vault.write(
                        f"{CHAT_NODE}/{message_id}", {"status": "working", "activity": "starting"}, method="PATCH"
                    )
                except urllib.error.HTTPError as error:
                    log(f"Could not claim chat message {message_id} (HTTP {error.code}). If this is 401, the "
                        f"{CHAT_NODE} rule from database.rules.json is not in the Firebase console yet.")
                    continue
                log(f"Answering chat message {message_id}: {message.get('text')!r}")
                with run_lock:
                    progress = make_progress_reporter(
                        lambda phase, label, key=message_id: vault.write(
                            f"{CHAT_NODE}/{key}", {"activity": phase, "activityText": label}, method="PATCH"
                        )
                    )
                    ok, out, err = invoke_claude(
                        home, chat_prompt(chat_history(chat, message_id)), args.timeout, on_event=progress
                    )
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

    def heartbeat():
        """Tells the app this listener is alive, so it can tell 'nobody is there' from 'still working'."""
        failing = False
        while not stop.is_set():
            try:
                vault.write(
                    f"{CHAT_NODE}/{LISTENER_KEY}",
                    {"at": {".sv": "timestamp"}, "busy": bool(in_flight)},
                    method="PATCH",
                )
                failing = False
            except Exception as error:
                if not failing:  # once per outage, not once per beat
                    log(f"Could not send the heartbeat: {error}")
                failing = True
            stop.wait(HEARTBEAT_SECONDS)

    if not args.dry_run:
        threading.Thread(target=heartbeat, daemon=True).start()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        stop.set()
        if not args.dry_run:
            try:  # so the app says "not connected" at once instead of after the heartbeat times out
                vault.write(f"{CHAT_NODE}/{LISTENER_KEY}", {"at": 0, "busy": False}, method="PATCH")
            except Exception:
                pass
        log("Stopped")


if __name__ == "__main__":
    sys.exit(main())
