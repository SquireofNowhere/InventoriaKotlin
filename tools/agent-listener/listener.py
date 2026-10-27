#!/usr/bin/env python3
"""Answers Claude requests from Inventoria, using your own Claude login.

Two things start a run:
  * The in-app chat. Every chat in the app is its own conversation, and gets its own long-lived
    Claude session here (a `claude -p` process in streaming-input mode: that chat's "terminal").
    Messages are fed to it as they arrive, so it keeps its context and its loaded tools between
    messages. After an hour with no message in that chat the session closes; the next message
    restarts it where it left off (`--resume`). How full each chat's context is gets written back
    so the app can say when a new chat is due.
  * A todo titled "@claude ...". A one-off run: the reply goes into the todo's description and the
    todo is ticked off.

How it works
  * Signs in with the credentials the Inventoria MCP server already keeps in ~/.inventoria-mcp/
    (config.json, session.json, vault.json) -- the same anonymous, invite-joined Firebase user.
  * Streams the vault's `todos` and the chat's `agent_chat/chats` and `agent_chat/messages` nodes
    over Firebase's server-sent-events REST API. No polling.
  * Every run is locked down to the Inventoria MCP tools (the delete tools and import_vault are
    denied; no shell, no file access).

The chat's `agent_chat` node needs the rule in database.rules.json pasted into the Firebase console
(rules are not deployed from the repo), or the replies are refused.

A todo is handled once, remembered in agent-listener-state.json next to the credentials; the first
ever start marks existing "@claude" todos as handled. A chat message's own `status` field records
where it is (pending -> working -> done/error), so nothing is remembered for chat messages.

Usage:  python listener.py [--dry-run] [--idle-minutes N] [--max-sessions N] [--timeout SECONDS]
        python listener.py --selftest     (talk to a real Claude session once and print what it says)
  --dry-run   print what would run, never start Claude or change anything
Stdlib only. Stop with Ctrl+C.
"""
import argparse
import copy
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import threading
import time
import traceback
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime
from pathlib import Path
from queue import Empty, Queue

TRIGGER = "@claude"
CHAT_NODE = "agent_chat"
# Where the chats' own details (title, state, context use) and their messages live under CHAT_NODE.
CHATS_KEY = "chats"
MESSAGES_KEY = "messages"
# The models and effort levels this Claude offers, as it reports them, for the app's pickers.
MODELS_KEY = "_models"
# How often the list is read again (it changes when Claude is updated, not often).
CATALOG_REFRESH_SECONDS = 6 * 60 * 60
CATALOG_RETRY_SECONDS = 5 * 60
# How often waiting messages are looked for again, in case an update missed one.
RESCAN_SECONDS = 20
# How much of an earlier conversation is replayed to a session that could not be resumed.
CHAT_CONTEXT_MESSAGES = 20
# A chat's session closes after this long with no message in it, and a message restarts it.
DEFAULT_IDLE_MINUTES = 60
# Each live session is a `claude` process plus its own MCP server, so only so many stay open at once;
# the least recently used idle one is closed to make room (its chat resumes on its next message).
DEFAULT_MAX_SESSIONS = 3
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
    # A run that could post into a chat would be asking Claude to answer itself, and every post
    # spends the user's Claude usage; reconfiguring a chat's model from inside it makes no sense either.
    "mcp__inventoria__send_chat_message",
    "mcp__inventoria__set_chat_model",
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

    def read(self, node):
        """The node's current value, straight from the database."""
        with urllib.request.urlopen(self.url(node, self.id_token()), timeout=30) as response:
            return json.load(response)

    def write(self, node, value, method="PUT"):
        """PUT replaces the node, PATCH merges into it. Raises HTTPError (401 = a rule refused it)."""
        body = json.dumps(value).encode("utf-8")
        request = urllib.request.Request(
            self.url(node, self.id_token()), data=body, method=method,
            headers={"Content-Type": "application/json"},
        )
        with urllib.request.urlopen(request, timeout=30) as response:
            response.read()


def merge_into(target, data):
    """Applies a patch's `data` to the dict `target`. A key can be a path ("chat/message/status"):
    that is how Firebase delivers a multi-location update, such as the app sending a message and
    touching its chat in one write. Taking such a key literally files the change under a name no
    reader looks for, so a message the app sent while the listener was running was never seen."""
    for key, value in (data or {}).items():
        if "/" in key:
            apply_event(target, key, value, merge=False)
        elif value is None:
            target.pop(key, None)
        else:
            target[key] = value


def apply_event(mirror, path, data, merge):
    """Apply a put/patch event to the local copy of a node. `path` is relative to that node."""
    parts = [p for p in path.split("/") if p]
    if not parts:
        if merge:
            merge_into(mirror, data)
        else:
            mirror.clear()
            mirror.update(data or {})
        return
    node = mirror
    for part in parts[:-1]:
        child = node.get(part)
        if not isinstance(child, dict):
            if data is None and not merge:
                return  # deleting something that was never there
            child = {}
            node[part] = child
        node = child
    last = parts[-1]
    if merge:
        existing = node.get(last)
        if not isinstance(existing, dict):
            existing = {}
        merge_into(existing, data)
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
                    try:
                        if first:
                            first = False
                            on_snapshot(mirror)
                        else:
                            on_change(mirror)
                    except Exception:
                        # Whatever the handler tripped on, the stream must carry on: a dead stream
                        # silently stops every later message from being answered.
                        log(f"Handling an update on {node} failed:\n{traceback.format_exc()}")
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


def claude_command():
    """The `claude` executable as an argv prefix. INVENTORIA_CLAUDE_CMD overrides it (for tests)."""
    override = os.environ.get("INVENTORIA_CLAUDE_CMD")
    if override:
        return shlex.split(override)
    claude = shutil.which("claude")
    return [claude] if claude else None


def write_mcp_config(home):
    """Writes the MCP config that loads only the Inventoria server and returns (path, None), or
    (None, why-not). Only that server, so a run cannot reach Mixar, Flow, REA or anything else."""
    jar = os.environ.get("INVENTORIA_MCP_JAR") or str(
        Path(__file__).resolve().parents[2] / "mcpServer" / "build" / "libs" / "inventoria-mcp-all.jar"
    )
    java, java_problem = find_java()
    if not java:
        # Without this the server dies at startup, Claude runs with no tools, and the user's chat
        # gets a puzzling "I couldn't check" instead of the actual reason.
        return None, java_problem
    path = home / "agent-listener-mcp.json"
    path.write_text(json.dumps({
        "mcpServers": {"inventoria": {"command": java, "args": ["-jar", jar]}}
    }), encoding="utf-8")
    return path, None


def locked_down_flags(mcp_config):
    flags = [
        "--strict-mcp-config", "--mcp-config", str(mcp_config),
        "--allowedTools", "mcp__inventoria",
        "--disallowedTools", *DENIED_TOOLS,
    ]
    if not USE_API_KEY:
        # Skip ~/.claude/settings.json: it can carry an apiKeyHelper or a proxy URL that would send
        # the run to an API key instead of the subscription login.
        flags += ["--setting-sources", "project"]
    return flags


def invoke_claude(home, prompt, timeout):
    """One locked-down, one-off `claude -p` run. Returns (ok, stdout, error_text)."""
    claude = claude_command()
    if not claude:
        return False, "", "`claude` is not on PATH"
    mcp_config, problem = write_mcp_config(home)
    if not mcp_config:
        return False, "", problem
    command = claude + ["-p", prompt, *locked_down_flags(mcp_config), "--no-session-persistence"]
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


# --- per-chat sessions --------------------------------------------------------------------------

EOF = object()  # put on a session's event queue when its output ends


def kill_tree(process):
    """Stops a `claude` process and what it started (its MCP server): on Windows a plain kill()
    leaves the children running."""
    if process.poll() is not None:
        return
    if os.name == "nt":
        subprocess.run(["taskkill", "/PID", str(process.pid), "/T", "/F"], capture_output=True)
    else:
        process.kill()


class ClaudeSession:
    """One long-lived `claude -p` process in streaming-input mode: a single chat's own "terminal".
    Messages go in as JSON lines on stdin and events come back as JSON lines on stdout, so the
    process keeps its context (and its loaded MCP server) from one message to the next."""

    def __init__(self, command, cwd, env):
        self.process = subprocess.Popen(
            command, cwd=cwd, env=env, stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.PIPE,
            text=True, encoding="utf-8", errors="replace", bufsize=1,
        )
        self.events = Queue()
        self.stray = []   # stdout lines that were not JSON: often the real error text
        self.errors = []  # stderr lines
        threading.Thread(target=self._read_stdout, daemon=True).start()
        threading.Thread(target=self._read_stderr, daemon=True).start()

    def _read_stdout(self):
        try:
            for line in self.process.stdout:
                line = line.strip()
                if not line:
                    continue
                try:
                    event = json.loads(line)
                except ValueError:
                    self.stray.append(line)
                    continue
                if isinstance(event, dict):
                    self.events.put(event)
        except (ValueError, OSError):
            pass  # close() shut the pipe under us
        self.events.put(EOF)

    def _read_stderr(self):
        try:
            self.errors.extend(self.process.stderr)
        except (ValueError, OSError):
            pass

    def send(self, text):
        line = json.dumps({"type": "user", "message": {"role": "user", "content": text}})
        self.process.stdin.write(line + "\n")
        self.process.stdin.flush()

    def alive(self):
        return self.process.poll() is None

    def failure_text(self):
        """Why the process ended, as best it can be told: stderr, else stray stdout, else its exit code."""
        text = "".join(self.errors).strip() or "\n".join(self.stray).strip()
        if text:
            return text[-400:]
        code = self.process.poll()
        return f"exit code {code}" if code is not None else "no reason given"

    def close(self):
        """Ends the session: closing stdin is how a streaming `claude -p` is told it is done."""
        try:
            self.process.stdin.close()
        except Exception:
            pass
        try:
            self.process.wait(timeout=8)
        except subprocess.TimeoutExpired:
            pass
        kill_tree(self.process)
        try:
            self.process.wait(timeout=5)
        except subprocess.TimeoutExpired:
            pass
        for pipe in (self.process.stdout, self.process.stderr):
            try:
                pipe.close()
            except Exception:
                pass


def run_turn(session, text, on_event, timeout, stop=None):
    """Feeds one user message to `session` and waits for that turn's result event. Every event on the
    way goes to `on_event`. Returns (result_event, None), or (None, why-it-failed) when the session
    ended, hung past `timeout`, or the listener was stopped."""
    try:
        session.send(text)
    except (OSError, ValueError) as error:
        return None, session.failure_text() if not session.alive() else f"could not reach the session ({error})"
    deadline = time.time() + timeout
    while True:
        if stop is not None and stop.is_set():
            return None, "the listener was stopped"
        remaining = deadline - time.time()
        if remaining <= 0:
            return None, f"timed out after {timeout}s"
        try:
            event = session.events.get(timeout=min(remaining, 1))
        except Empty:
            continue
        if event is EOF:
            return None, session.failure_text()
        on_event(event)
        if event.get("type") == "result":
            return event, None


def normalize_models(raw):
    """The `models` list from Claude's initialize reply, as the app wants it: what to pass to --model
    (`value`), what to show, and the effort levels that model takes (none if it has no effort setting)."""
    models = []
    for entry in raw or []:
        if not isinstance(entry, dict) or not entry.get("value"):
            continue
        efforts = entry.get("supportedEffortLevels") if entry.get("supportsEffort") else []
        models.append({
            "value": str(entry["value"]),
            "name": str(entry.get("displayName") or entry["value"]),
            "description": str(entry.get("description") or ""),
            "resolved": str(entry.get("resolvedModel") or ""),
            "efforts": [str(level) for level in (efforts or [])],
        })
    return models


def fetch_catalog(home, timeout=60):
    """Asks a throwaway Claude session which models (and effort levels) it offers, so nothing here
    has to be kept in step with what Claude supports. Returns (models, claude_version). The session
    loads no MCP server and takes no message: the answer comes with its start-up handshake."""
    claude = claude_command()
    if not claude:
        raise RuntimeError("`claude` is not on PATH")
    command = claude + [
        "-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose", "--strict-mcp-config",
    ]
    if not USE_API_KEY:
        command += ["--setting-sources", "project"]
    session = ClaudeSession(command, home, clean_env())
    try:
        session.process.stdin.write(json.dumps({
            "type": "control_request", "request_id": "inventoria-models", "request": {"subtype": "initialize"},
        }) + "\n")
        session.process.stdin.flush()
        deadline = time.time() + timeout
        while time.time() < deadline:
            try:
                event = session.events.get(timeout=1)
            except Empty:
                continue
            if event is EOF:
                raise RuntimeError(session.failure_text())
            if event.get("type") != "control_response":
                continue
            response = event.get("response") or {}
            if response.get("subtype") == "error":
                raise RuntimeError(str(response.get("error") or "Claude refused the request"))
            body = response.get("response") or {}
            models = normalize_models(body.get("models"))
            if not models:
                raise RuntimeError("Claude's reply listed no models")
            return models, body.get("claude_code_version")
        raise RuntimeError(f"no answer within {timeout}s")
    finally:
        session.close()


def default_window(model):
    """The context window to assume when Claude has not said: the 1M variants say so in their name."""
    return 1_000_000 if model and re.search(r"1m\b", str(model).lower()) else 200_000


class ContextMeter:
    """Tracks how full a session's context is, from the usage Claude reports. The size of the context
    is the size of the last prompt it was sent plus the reply it just wrote (that reply is part of the
    next prompt) -- not the sum over a turn's calls, which counts the same text several times."""

    def __init__(self, previous=None):
        previous = previous or {}
        self.tokens = int(previous.get("contextTokens") or 0)
        self.window = int(previous.get("contextWindow") or 0)
        self.model = previous.get("model")

    def observe(self, event):
        kind = event.get("type")
        if kind == "system" and event.get("subtype") == "init":
            self.model = event.get("model") or self.model
        elif kind == "assistant" and not event.get("parent_tool_use_id"):
            usage = (event.get("message") or {}).get("usage") or {}
            used = sum(int(usage.get(key) or 0) for key in (
                "input_tokens", "cache_creation_input_tokens", "cache_read_input_tokens", "output_tokens"))
            if used:
                self.tokens = used
        elif kind == "result":
            for info in (event.get("modelUsage") or {}).values():
                window = int((info or {}).get("contextWindow") or 0)
                if window:
                    self.window = max(self.window, window)

    def snapshot(self):
        window = self.window or default_window(self.model)
        percent = min(100, round(100 * self.tokens / window)) if window and self.tokens else 0
        fields = {"contextTokens": self.tokens, "contextWindow": window, "contextPercent": percent}
        if self.model:
            fields["model"] = self.model
        return fields


SERVER_TIME = {".sv": "timestamp"}  # Firebase fills this in with its own clock


class ChatWorker(threading.Thread):
    """Everything for one chat: its message queue, its session, and its idle timer. Messages in a
    chat are answered strictly in order; different chats run side by side."""

    def __init__(self, manager, chat_id):
        super().__init__(daemon=True, name=f"chat-{chat_id}")
        self.manager = manager
        self.chat_id = chat_id
        self.queue = Queue()
        self.lock = threading.Lock()  # held for a whole turn, and while the session opens or closes
        self.session = None
        self.last_active = time.time()
        self.busy = False
        self.resume_id = None   # the Claude session id to pick this chat back up with
        self.meter = ContextMeter()
        self._hydrated = False
        self._resumed = False   # this session was started with --resume
        self._spoke = False     # ... and has produced at least one event
        self._primed = True     # false: the next message needs the earlier conversation attached
        self._applied = (None, None)  # (model, effort) the open session was started with
        self._title_tries = 0   # times this worker has tried to name the chat

    # -- the thread --
    def run(self):
        try:
            self._loop()
        except BaseException:
            log(f"Chat {self.chat_id}: its worker stopped unexpectedly:\n{traceback.format_exc()}")
            raise

    def _loop(self):
        manager = self.manager
        while not manager.stop.is_set():
            try:
                message_id = self.queue.get(timeout=1)
            except Empty:
                self._close_if_idle()
                if self.session is None and manager.retire(self):
                    return
                continue
            self._handle(message_id)
        with self.lock:
            self._close_session("the listener stopped")

    def _close_if_idle(self):
        if self.session is None or time.time() - self.last_active < self.manager.idle_seconds:
            return
        if self.lock.acquire(blocking=False):
            try:
                self._close_session("idle")
            finally:
                self.lock.release()

    def try_close_for_room(self):
        """Called by another chat that needs a slot. Only an idle session is given up."""
        if not self.lock.acquire(blocking=False):
            return False
        try:
            if self.session is None or self.busy:
                return False
            self._close_session("to make room for another chat")
            return True
        finally:
            self.lock.release()

    # -- one message --
    def _handle(self, message_id):
        manager = self.manager
        message = manager.message(self.chat_id, message_id)
        if not is_chat_trigger(message):
            log(f"Chat {self.chat_id}: skipping {message_id}, it is no longer waiting "
                f"(status {(message or {}).get('status')!r})")
            manager.done(self.chat_id, message_id)
            return
        with self.lock:
            self.busy = True
            try:
                self._answer(message_id, message)
            except Exception as error:  # one bad message must not kill the chat's thread
                log(f"Chat {self.chat_id}: unexpected error on {message_id}: {error!r}")
            finally:
                self.busy = False
                self.last_active = time.time()
                manager.done(self.chat_id, message_id)

    def _hydrate(self):
        """Learns what the database remembers of this chat. Waits for the chats' first snapshot, or a
        restarted listener would not know which session to resume."""
        if self._hydrated:
            return
        self.manager.chats_ready.wait(15)
        meta = self.manager.chat_meta(self.chat_id)
        self.resume_id = meta.get("sessionId")
        self.meter = ContextMeter(meta)
        self._hydrated = True

    def _answer(self, message_id, message):
        manager = self.manager
        cid = self.chat_id
        self._hydrate()
        text = str(message.get("text", ""))
        if manager.dry_run:
            log(f"[dry run] would answer chat {cid} message {message_id}: {text!r}")
            return
        claim = {"status": "working", "activity": "starting"}
        if self.session is None:
            claim["activityText"] = "Reopening this chat" if self.resume_id else "Starting this chat"
        try:
            manager.write_message(cid, message_id, claim)
        except urllib.error.HTTPError as error:
            log(f"Could not claim chat message {message_id} (HTTP {error.code}). If this is 401, the "
                f"{CHAT_NODE} rule from database.rules.json is not in the Firebase console yet.")
            return
        log(f"Answering chat {cid} message {message_id}: {text!r}")

        wanted = self._wanted()
        if self.session is not None and wanted != self._applied:
            # The user picked another model or effort. Both are fixed when a session starts, so the
            # session is closed and reopened with --resume: the conversation carries over.
            log(f"Chat {cid}: switching from {self._applied} to {wanted}")
            try:
                manager.write_message(cid, message_id, {"activity": "starting", "activityText": "Switching model"})
            except Exception:
                pass
            self._close_session("model or effort changed")
            self.meter.window = 0  # the new model may have a different context window
            self.meter.model = None

        progress = make_progress_reporter(
            lambda phase, label: manager.write_message(cid, message_id, {"activity": phase, "activityText": label})
        )
        result, failure = None, None
        for attempt in (1, 2):
            if self.session is None:
                failure = self._open_session()
                if failure:
                    break
            prompt = text
            if not self._primed:
                prompt = priming_text(manager.history(cid, message_id)[:-1], text)
                self._primed = True
            result, failure = run_turn(
                self.session, prompt, lambda event: self._on_event(event, progress), manager.timeout, manager.stop
            )
            if result is not None:
                break
            resume_failed = self._resumed and not self._spoke
            self._discard_session(failure)
            if resume_failed and attempt == 1:
                # The saved session is gone or unreadable: start over, handing the transcript back.
                log(f"Chat {cid}: could not resume session {self.resume_id}; starting fresh with the transcript")
                self.resume_id = None
                continue
            break

        status = "error"
        if result is not None and not result.get("is_error"):
            reply_text = str(result.get("result") or "").strip()
            if reply_text:
                status = "done"
            else:
                reply_text = "Sorry, I had no answer for that."
        else:
            reason = str((result or {}).get("result") or failure or "no reply")
            log(f"Chat {cid}: run failed: {reason[:400]!r}")
            reply_text = f"Sorry, I couldn't answer that: {reason[:200]}"
        try:
            manager.reply(cid, message_id, reply_text, status)
        except urllib.error.URLError as error:
            log(f"Could not write the reply to {message_id}: {error}")
        self._publish_state()
        if status == "done":
            self._maybe_title(text, reply_text)

    def _maybe_title(self, question, answer):
        """Names the chat from what it is about, once, unless it already has a name it was given
        (`titleSource` is "auto" once the listener named it, "user" once the user did). The app starts
        a chat off with its first line cut short; this replaces that with something like claude.ai
        would write. It runs beside the chat, not in it, so the next message is not kept waiting."""
        manager = self.manager
        if manager.dry_run or self._title_tries >= 2:
            return
        if manager.fresh_meta(self.chat_id).get("titleSource"):
            return
        self._title_tries += 1
        threading.Thread(target=self._write_title, args=(question, answer), daemon=True,
                         name=f"title-{self.chat_id}").start()

    def _write_title(self, question, answer):
        manager = self.manager
        title = generate_title(manager.home, question, answer)
        if not title:
            return
        # The user may have renamed the chat while this was being written; theirs wins.
        if manager.fresh_meta(self.chat_id).get("titleSource"):
            return
        log(f"Chat {self.chat_id}: titled {title!r}")
        manager.write_meta(self.chat_id, {"title": title, "titleSource": "auto"})

    def _on_event(self, event, progress):
        self._spoke = True
        self.meter.observe(event)
        session_id = event.get("session_id")
        if session_id:
            self.resume_id = session_id
        progress(event)

    # -- the session --
    def _wanted(self):
        """The (model, effort) this chat's choice comes to, checked against what Claude offers."""
        # Read from the database, not the mirror: the chat's details and its messages come in on separate
        # streams, so a choice made just before a message may not have reached the mirror yet.
        meta = self.manager.fresh_meta(self.chat_id)
        return self.manager.resolve_choice(meta.get("modelChoice"), meta.get("effortChoice"))

    def _open_session(self):
        """Starts this chat's `claude` process. Returns why it could not, or None."""
        manager = self.manager
        claude = claude_command()
        if not claude:
            return "`claude` is not on PATH"
        mcp_config, problem = write_mcp_config(manager.home)
        if not mcp_config:
            return problem
        manager.make_room(self)
        command = claude + [
            "-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose",
            *locked_down_flags(mcp_config),
            # One line: a newline inside an argument does not survive every launcher on Windows.
            "--append-system-prompt", " ".join(CHAT_SYSTEM_PROMPT.split()),
        ]
        model, effort = self._wanted()
        if model:
            command += ["--model", model]
        if effort:
            command += ["--effort", effort]
        if self.resume_id:
            command += ["--resume", self.resume_id]
        try:
            self.session = ClaudeSession(command, manager.home, clean_env())
        except OSError as error:
            return f"could not start Claude: {error}"
        self._applied = (model, effort)
        self._resumed = bool(self.resume_id)
        self._spoke = False
        self._primed = bool(self.resume_id)  # a resumed session remembers; a fresh one is told
        log(f"Chat {self.chat_id}: opened a session"
            + (f" (resuming {self.resume_id})" if self.resume_id else "")
            + (f", model {model}" if model else "") + (f", effort {effort}" if effort else ""))
        manager.write_meta(self.chat_id, {
            "state": "live", "closedReason": None, "idleMinutes": manager.idle_minutes,
            "lastActiveAt": SERVER_TIME, "appliedModel": model, "appliedEffort": effort,
        })
        return None

    def _discard_session(self, why):
        session, self.session = self.session, None
        if session is not None:
            session.close()
        log(f"Chat {self.chat_id}: the session ended ({str(why)[:200]})")
        self._publish_state()

    def _close_session(self, reason):
        session, self.session = self.session, None
        if session is None:
            return
        session.close()
        log(f"Chat {self.chat_id}: closed its session ({reason})")
        self.manager.write_meta(self.chat_id, {
            "state": "closed", "closedReason": reason, **self._context_fields(),
        })

    def _context_fields(self):
        fields = self.meter.snapshot()
        if self.resume_id:
            fields["sessionId"] = self.resume_id
        return fields

    def _publish_state(self):
        """What the app shows about this chat after a turn: is it live, and how full is its context."""
        self.manager.write_meta(self.chat_id, {
            "state": "live" if self.session is not None else "closed",
            "idleMinutes": self.manager.idle_minutes,
            "lastActiveAt": SERVER_TIME,
            **self._context_fields(),
        })


class ChatManager:
    """Owns the chats' workers and mirrors of `agent_chat/chats` (each chat's details) and
    `agent_chat/messages` (what was said, per chat). A chat gets a worker the first time one of its
    messages needs answering, and loses it once its session has closed and nothing is waiting."""

    def __init__(self, vault, home, args, stop):
        self.vault = vault
        self.home = home
        self.stop = stop
        self.dry_run = args.dry_run
        self.timeout = args.timeout
        self.idle_minutes = args.idle_minutes
        self.idle_seconds = args.idle_minutes * 60
        self.max_sessions = max(1, args.max_sessions)
        self.lock = threading.Lock()
        self.workers = {}
        self.pending = set()  # (chat, message) queued or being answered
        self.messages = {}    # chat id -> {message id -> message}
        self.chats = {}       # chat id -> details
        self.chats_ready = threading.Event()
        self.catalog = {}     # model value -> {value, name, description, resolved, efforts}

    # -- what Claude offers --
    def set_catalog(self, models):
        self.catalog = {model["value"]: model for model in models}

    def resolve_choice(self, model_choice, effort_choice):
        """(model, effort) to start a session with, from what the user picked. None means "leave it to
        Claude's default". A pick is checked against the catalog read from Claude itself: an unknown
        model, or an effort the model does not take, is dropped rather than passed on to fail. With no
        catalog yet (it could not be read) the pick is passed through as it is."""
        model = model_choice if isinstance(model_choice, str) and model_choice not in ("", "default") else None
        if model and self.catalog and model not in self.catalog:
            log(f"Ignoring unknown model choice {model!r}")
            model = None
        effort = effort_choice if isinstance(effort_choice, str) and effort_choice else None
        if effort and self.catalog:
            option = self.catalog.get(model or "default") or {}
            if effort not in option.get("efforts", []):
                log(f"Ignoring effort {effort!r}: not offered for {model or 'the default model'}")
                effort = None
        return model, effort

    # -- reading what the database says --
    def chat_meta(self, chat_id):
        return dict(self.chats.get(chat_id) or {})

    def fresh_meta(self, chat_id):
        """A chat's details as the database has them right now, or the mirror's if it can't be reached."""
        try:
            meta = self.vault.read(f"{CHAT_NODE}/{CHATS_KEY}/{chat_id}")
            if isinstance(meta, dict):
                return meta
        except Exception as error:
            log(f"Could not read chat {chat_id}'s details ({error}); using the last known")
        return self.chat_meta(chat_id)

    def message(self, chat_id, message_id):
        return (self.messages.get(chat_id) or {}).get(message_id)

    def history(self, chat_id, upto_id):
        return chat_history(self.messages.get(chat_id) or {}, upto_id)

    # -- writing to it --
    def _write(self, node, fields, method="PATCH"):
        self.vault.write(node, fields, method=method)

    def write_meta(self, chat_id, fields):
        """Details are a courtesy to the app: a failed write is logged, never fatal."""
        if self.dry_run:
            return
        try:
            self._write(f"{CHAT_NODE}/{CHATS_KEY}/{chat_id}", fields)
        except Exception as error:
            log(f"Could not update chat {chat_id}'s details: {error}")

    def write_message(self, chat_id, message_id, fields):
        self._write(f"{CHAT_NODE}/{MESSAGES_KEY}/{chat_id}/{message_id}", fields)

    def reply(self, chat_id, message_id, text, status):
        """Writes Claude's bubble, then closes the user's message. The reply's key sorts straight
        after its question and before any later one ('~' is not in the push-id alphabet)."""
        base = f"{CHAT_NODE}/{MESSAGES_KEY}/{chat_id}"
        self._write(f"{base}/{message_id}~r", {
            "role": "assistant", "text": text, "createdAt": int(time.time() * 1000), "replyTo": message_id,
        }, method="PUT")
        # null deletes a key, so the progress line goes away with the wait.
        self._write(f"{base}/{message_id}", {"status": status, "activity": None, "activityText": None})

    # -- what the streams report --
    def on_chats(self, mirror):
        self.chats = copy.deepcopy(mirror)
        first = not self.chats_ready.is_set()
        self.chats_ready.set()
        if first and not self.dry_run:
            # Whatever was "live" belonged to a listener that is gone: its processes died with it.
            for chat_id, meta in self.chats.items():
                if isinstance(meta, dict) and meta.get("state") in ("live", "starting"):
                    self.write_meta(chat_id, {"state": "closed", "closedReason": "the listener restarted"})

    def on_messages(self, mirror, first=False):
        self.messages = copy.deepcopy(mirror)
        if first:
            self._recover_stuck()
        self._consider()

    def _consider(self):
        for chat_id, messages in list(self.messages.items()):
            if not isinstance(messages, dict):
                continue
            for message_id in sorted(messages):
                if is_chat_trigger(messages[message_id]):
                    self.submit(chat_id, message_id)

    def _recover_stuck(self):
        # A message left "working" by a listener that died mid-run would spin in the app forever.
        for chat_id, messages in list(self.messages.items()):
            if not isinstance(messages, dict):
                continue
            for message_id, message in list(messages.items()):
                stuck = (isinstance(message, dict) and message.get("role") == "user"
                         and message.get("status") == "working")
                with self.lock:
                    if not stuck or (chat_id, message_id) in self.pending:
                        continue
                if self.dry_run:
                    log(f"[dry run] would mark stuck message {message_id} as failed")
                    continue
                try:
                    self.reply(chat_id, message_id, "The listener stopped while working on this. Please ask again.", "error")
                    log(f"Marked stuck chat message {message_id} as failed")
                except urllib.error.URLError as error:
                    log(f"Could not recover chat message {message_id}: {error}")

    # -- workers --
    def submit(self, chat_id, message_id):
        with self.lock:
            key = (chat_id, message_id)
            if key in self.pending:
                return
            worker = self.workers.get(chat_id)
            if worker is not None and not worker.is_alive():
                # Its thread ended without retiring (it crashed). Anything it had queued or was
                # answering will never be finished, so forget that and let a new worker take over; the
                # still-pending messages are picked up again from the mirror.
                log(f"Chat {chat_id}: replacing a worker that is no longer running")
                self.pending = {pending for pending in self.pending if pending[0] != chat_id}
                orphan = worker.session
                worker.session = None
                if orphan is not None:
                    threading.Thread(target=orphan.close, daemon=True).start()
                worker = None
            self.pending.add(key)
            if worker is None:
                worker = ChatWorker(self, chat_id)
                self.workers[chat_id] = worker
                worker.start()
            log(f"Chat {chat_id}: queued message {message_id}")
            worker.queue.put(message_id)

    def rescan(self):
        """Queues any waiting message that is not already queued. Updates do this as they arrive; this
        is the safety net for the one that, for whatever reason, did not."""
        before = len(self.pending)
        self._consider()
        if len(self.pending) > before:
            log(f"Rescan queued {len(self.pending) - before} message(s) the updates had not")

    def done(self, chat_id, message_id):
        with self.lock:
            self.pending.discard((chat_id, message_id))

    def retire(self, worker):
        """A worker with no session and nothing queued goes away. Checked under the same lock as
        submit(), so a message can never be queued to a worker that is leaving."""
        with self.lock:
            if worker.queue.empty() and self.workers.get(worker.chat_id) is worker:
                del self.workers[worker.chat_id]
                return True
            return False

    def make_room(self, requester):
        """Before opening a session: if too many are open, close the least recently used idle ones."""
        with self.lock:
            live = [w for w in self.workers.values() if w is not requester and w.session is not None]
        excess = len(live) + 1 - self.max_sessions
        for worker in sorted(live, key=lambda w: w.last_active):
            if excess <= 0:
                break
            if worker.try_close_for_room():
                excess -= 1

    def any_busy(self):
        with self.lock:
            return any(w.busy for w in self.workers.values())

    def shutdown(self):
        """Closes every session, so no `claude` process (or its MCP server) is left behind."""
        self.stop.set()
        with self.lock:
            workers = list(self.workers.values())
        for worker in workers:
            worker.join(timeout=20)


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


CHAT_SYSTEM_PROMPT = """You are Claude, chatting with the user inside their Inventoria app, on their phone.
You can read and edit their vault (todos, items, collections, timers, the schedule) with the
Inventoria tools. When a question is about their data, look it up with the tools rather than
guessing, and when they ask for a change, make it and say what you did. Deleting and importing are
not available to you.

Write for a phone screen: short and plain. The app draws markdown, so bold, short lists and inline
code are fine; keep headings and tables to a minimum. Whenever you name a todo, item, collection,
tracked task or schedule block that the tools returned, make its name a markdown link to the `link`
field the tool gave it, like [Buy milk](inventoria://todo/ID): the app shows it as a button that
opens it. Always write it in that markdown form, [name](link), and never as a bare address in the
text. Use only links the tools returned, never ones you made up."""

# The name a chat gets, the way claude.ai titles a conversation. Asked of a throwaway Claude with no
# tools, on a small model: it is a few words, and must not hold up the next message.
TITLE_MODEL = "haiku"
TITLE_PROMPT = """Give this chat a short title of two to six words, the way claude.ai names a conversation: what it is about, in plain words. No quotes, no trailing punctuation. Reply with the title only.

The user wrote:
{question}

Claude answered:
{answer}"""


def clean_title(raw):
    """A model's answer as a chat title: its first line, without labels, quotes or markdown, kept short."""
    line = ""
    for candidate in str(raw or "").splitlines():
        if candidate.strip():
            line = candidate.strip()
            break
    line = re.sub(r"^(title|name)\s*:\s*", "", line, flags=re.IGNORECASE)
    line = line.strip("\"'`*#_ \t“”‘’").rstrip(".:;,!? ")
    line = " ".join(line.split())
    return line if len(line) <= 60 else line[:60].rstrip() + "…"


def generate_title(home, question, answer, timeout=60):
    """Asks a throwaway Claude (no tools, no MCP server) for a title for a chat that has just had its
    first answer. Returns the cleaned title, or "" if it could not get one."""
    claude = claude_command()
    if not claude:
        return ""
    prompt = TITLE_PROMPT.format(question=str(question)[:1500], answer=str(answer)[:1500])
    command = claude + [
        "-p", prompt, "--model", TITLE_MODEL, "--no-session-persistence", "--strict-mcp-config", "--tools", "",
    ]
    if not USE_API_KEY:
        command += ["--setting-sources", "project"]
    try:
        result = subprocess.run(
            command, cwd=home, env=clean_env(), capture_output=True, text=True, encoding="utf-8",
            errors="replace", timeout=timeout, stdin=subprocess.DEVNULL,
        )
    except (subprocess.TimeoutExpired, OSError) as error:
        log(f"Could not title a chat: {error}")
        return ""
    if result.returncode != 0:
        log(f"Could not title a chat: {(result.stderr or result.stdout or '').strip()[:200]!r}")
        return ""
    return clean_title(result.stdout)


def priming_text(history, text):
    """The first message to a session that has no memory of this chat -- a new one, or one that could
    not be resumed -- so it carries the earlier conversation along."""
    if not history:
        return text
    lines = []
    for message in history:
        who = "User" if message.get("role") == "user" else "Claude"
        lines.append(f"{who}: {message.get('text', '')}")
    transcript = "\n".join(lines)
    return (
        "This chat already has history; your own memory of it was lost, so here it is, oldest first:\n"
        f"{transcript}\n\n"
        f"The user's new message (reply to this one only):\n{text}"
    )


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


def selftest(home, timeout):
    """Talks to one real Claude session, twice, with no database involved. Shows whether the login
    works, whether a session keeps its memory between messages, and what the context meter reads."""
    claude = claude_command()
    if not claude:
        print("`claude` is not on PATH")
        return 1
    mcp_config, problem = write_mcp_config(home)
    if not mcp_config:
        print(problem)
        return 1
    command = claude + [
        "-p", "--input-format", "stream-json", "--output-format", "stream-json", "--verbose",
        *locked_down_flags(mcp_config), "--append-system-prompt", " ".join(CHAT_SYSTEM_PROMPT.split()),
    ]
    models = []
    try:
        models, version = fetch_catalog(home)
        print(f"Claude {version} offers {len(models)} models:")
        for model in models:
            efforts = ", ".join(model["efforts"]) or "no effort setting"
            print(f"  {model['value']:<8} {model['name']} ({model['resolved']}) - efforts: {efforts}")
        print()
    except Exception as error:
        print(f"Could not read the model list: {error}\n")
    session = ClaudeSession(command, home, clean_env())
    meter = ContextMeter()
    code = 0
    try:
        for question in (
            "Reply with the single word: pineapple. Do not use any tools.",
            "Which single word did I just ask you to reply with? Answer with that word only.",
        ):
            print(f"You:    {question}", flush=True)
            result, failure = run_turn(session, question, meter.observe, timeout)
            if result is None or result.get("is_error"):
                print(f"FAILED: {(result or {}).get('result') or failure}")
                code = 1
                break
            print(f"Claude: {result.get('result')}")
            print(f"        session {result.get('session_id')}, context {meter.snapshot()}", flush=True)
            session_id = result.get("session_id")
    finally:
        session.close()
    if code != 0:
        return code
    print("OK: the session answered twice and kept its memory between messages.")

    # The session has been closed, as the idle timer does. A later message reopens it with --resume.
    print("\nClosed that session. Reopening it with --resume, as the next message in an idle chat would...",
          flush=True)
    resumed = ClaudeSession(command + ["--resume", session_id], home, clean_env())
    try:
        question = "Which single word did I first ask you to reply with? Answer with that word only."
        print(f"You:    {question}", flush=True)
        result, failure = run_turn(resumed, question, meter.observe, timeout)
        if result is None or result.get("is_error"):
            print(f"FAILED to resume: {(result or {}).get('result') or failure}")
            return 1
        print(f"Claude: {result.get('result')}")
        print(f"        session {result.get('session_id')}, context {meter.snapshot()}", flush=True)
    finally:
        resumed.close()
    print("OK: a closed session came back with its conversation.")

    # What a chat's model and effort choice does: the same conversation, reopened on other settings,
    # taken from the catalog Claude itself reported above.
    alternatives = [m for m in models if m["value"] != "default" and m["efforts"]]
    if not alternatives:
        print("\nNo second model with an effort setting was listed, so the model switch was not tried.")
        return 0
    alt = alternatives[0]
    effort = alt["efforts"][0]
    print(f"\nReopening it again on {alt['name']} with {effort} effort (--model {alt['value']} --effort {effort})...",
          flush=True)
    switched_meter = ContextMeter()
    switched = ClaudeSession(command + ["--model", alt["value"], "--effort", effort, "--resume", session_id],
                             home, clean_env())
    try:
        question = "Which single word did I first ask you to reply with? Answer with that word only."
        print(f"You:    {question}", flush=True)
        result, failure = run_turn(switched, question, switched_meter.observe, timeout)
        if result is None or result.get("is_error"):
            print(f"FAILED to switch: {(result or {}).get('result') or failure}")
            return 1
        print(f"Claude: {result.get('result')}")
        used = ", ".join((result.get("modelUsage") or {}).keys())
        print(f"        session {result.get('session_id')}, ran on: {used or switched_meter.model}", flush=True)
    finally:
        switched.close()
    print(f"OK: the conversation carried over to {alt['name']} (expected {alt['resolved']}).")
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--settle", type=float, default=5.0, help="seconds to wait after a todo appears")
    parser.add_argument("--timeout", type=int, default=600, help="seconds one Claude run may take")
    parser.add_argument("--use-api-key", action="store_true",
                        help="use the environment's ANTHROPIC_API_KEY / user settings instead of the subscription login")
    parser.add_argument("--idle-minutes", type=int, default=DEFAULT_IDLE_MINUTES,
                        help="minutes with no message before a chat's session closes (a new message reopens it)")
    parser.add_argument("--max-sessions", type=int, default=DEFAULT_MAX_SESSIONS,
                        help="most chat sessions open at once; the least recently used idle one is closed to make room")
    parser.add_argument("--selftest", action="store_true",
                        help="talk to one real Claude session twice and print what it says, then exit")
    args = parser.parse_args()
    global USE_API_KEY
    USE_API_KEY = args.use_api_key
    if args.selftest:
        return selftest(home_dir(), args.timeout)

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

    # ---- chat: one Claude session per chat (see ChatManager) ----
    manager = ChatManager(vault, home, args, stop)

    for target in (todo_worker,):
        threading.Thread(target=target, daemon=True).start()
    for node, snapshot, change in (
        ("todos", on_todos_snapshot, on_todos_change),
        (f"{CHAT_NODE}/{CHATS_KEY}", manager.on_chats, manager.on_chats),
        (f"{CHAT_NODE}/{MESSAGES_KEY}", lambda mirror: manager.on_messages(mirror, first=True), manager.on_messages),
    ):
        threading.Thread(
            target=stream_forever, args=(vault, node, snapshot, change, stop), daemon=True
        ).start()

    def rescan_chats():
        while not stop.wait(RESCAN_SECONDS):
            try:
                manager.rescan()
            except Exception:
                log(f"Rescan failed:\n{traceback.format_exc()}")

    def publish_catalog():
        """Reads which models and effort levels Claude offers, and keeps the app's pickers in step."""
        while not stop.is_set():
            wait = CATALOG_REFRESH_SECONDS
            try:
                models, version = fetch_catalog(home)
                manager.set_catalog(models)
                vault.write(
                    f"{CHAT_NODE}/{MODELS_KEY}",
                    {"at": SERVER_TIME, "claudeVersion": version, "models": models},
                    method="PUT",
                )
                log(f"Published {len(models)} models: " + ", ".join(model["value"] for model in models))
            except Exception as error:
                log(f"Could not read or publish the model list ({error}); trying again in "
                    f"{CATALOG_RETRY_SECONDS // 60} minutes")
                wait = CATALOG_RETRY_SECONDS
            stop.wait(wait)

    def heartbeat():
        """Tells the app this listener is alive, so it can tell 'nobody is there' from 'still working'."""
        failing = False
        while not stop.is_set():
            try:
                vault.write(
                    f"{CHAT_NODE}/{LISTENER_KEY}",
                    {"at": {".sv": "timestamp"}, "busy": manager.any_busy()},
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
        threading.Thread(target=publish_catalog, daemon=True).start()
        threading.Thread(target=rescan_chats, daemon=True).start()

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        log("Stopping: closing every chat's session")
        manager.shutdown()  # sets `stop` and waits for each session to close
        if not args.dry_run:
            try:  # so the app says "not connected" at once instead of after the heartbeat times out
                vault.write(f"{CHAT_NODE}/{LISTENER_KEY}", {"at": 0, "busy": False}, method="PATCH")
            except Exception:
                pass
        log("Stopped")


if __name__ == "__main__":
    sys.exit(main())
