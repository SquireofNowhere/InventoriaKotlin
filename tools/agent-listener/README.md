# Agent listener

Lets Claude answer Inventoria, on your own Claude login. `listener.py` runs on your PC and watches the
vault in Firebase. Two things send it a request:

- **The in-app chat** (Today's top bar, or Settings > Claude). Every chat is its own conversation with
  its own Claude session here. You type a message, Claude answers using the Inventoria tools (so it can
  look things up and make changes), and the reply appears as Claude's bubble.
- **A todo titled `@claude ...`**, written anywhere. A one-off run: the reply goes into the todo's
  description and the todo is ticked off.

```bash
python tools/agent-listener/listener.py              # run it for real
python tools/agent-listener/listener.py --dry-run    # only say what it would do
python tools/agent-listener/listener.py --selftest   # a real session: login, the model list, resume, switching model
python -m unittest tools/agent-listener/test_listener.py   # the session logic, against a fake claude
```

Options: `--idle-minutes N` (default 60), `--max-sessions N` (default 3), `--timeout SECONDS` (default 600
per message), `--use-api-key` (see below).

Needs Python 3 (standard library only), the `claude` CLI on `PATH`, and the Inventoria MCP server
already set up (see `mcpServer/README.md`): the listener borrows its sign-in from
`~/.inventoria-mcp/`, so it gets no access the MCP server does not already have.

The MCP server jar is built for Java 21, so the listener looks for a Java 21 or newer rather than
trusting whichever `java` is first on `PATH` (it checks `INVENTORIA_JAVA`, `PATH`, `JAVA_HOME`, Android
Studio's bundled JDK and the usual install folders). With an older Java the server dies on start and
Claude can only say it "failed to connect"; if no suitable Java exists the chat now says so plainly.
Set `INVENTORIA_JAVA` to a `java` executable to choose one yourself.

## One-time setup for the chat

The chat lives in `users/<uid>/agent_chat`. The app (the vault owner) can always write there, but the
listener signs in as a joiner, which only has write access to nodes named in `database.rules.json`.
That file has an `agent_chat` entry, and it covers everything beneath it, but **rules are not deployed
from the repo**: paste the current `database.rules.json` into the Firebase console (Realtime Database >
Rules) and publish. Until you do, the chat sends but never gets an answer, and the listener logs a 401.
(`@claude` todos need no rule change.)

## What a run can and cannot do

- Only the Inventoria MCP tools are loaded (`--strict-mcp-config`), so no shell, files, Mixar or
  anything else.
- The delete tools and `import_vault` are denied. Edits are otherwise unrestricted: a request can
  create, change and move things, and start or stop timers.
- Anyone who can write to your vault (the app, the web app, anyone you invited) can start a run.

## Chats: one Claude session each

A chat's session is a long-lived `claude -p --input-format stream-json --output-format stream-json`
process: that chat's own "terminal". Messages go in as JSON lines and events come back, so the process
keeps its conversation and its loaded MCP server between messages instead of starting cold each time.

- **Per chat.** Different chats run side by side, each in its own process; messages within a chat are
  answered strictly in order.
- **Closes when idle.** After `--idle-minutes` (an hour) with no message in that chat, the session
  closes. Each message in the chat resets the clock.
- **A message restarts it.** The next message in a chat whose session has closed reopens it with
  `--resume <session id>`, so Claude remembers everything. (`--selftest` proves this against a real
  Claude.) If the saved session can't be resumed, a fresh one starts and is handed the last 20
  messages as a transcript. The listener restarting does the same: nothing in a chat is lost.
- **At most `--max-sessions` open at once** (each is a `claude` process plus a Java MCP server). Opening
  another closes the least recently used idle one, which resumes on its next message.
- **Ctrl+C** closes every session cleanly and marks them closed for the app.

### Model and effort, per chat

Each chat can run on its own model and effort level, picked in the app (the tune icon on a chat).
**Nothing is hard-coded**: at start-up, and every six hours, the listener opens a throwaway session with
no tools and asks Claude's start-up handshake (`initialize`) what it offers, then publishes the answer
to `agent_chat/_models`: each model's `value` (what `--model` takes), `name`, `description`, `resolved`
id and the `efforts` that model supports (empty for a model with no effort setting). The app's pickers
are drawn from that list, and the effort choices follow the model picked. Right now that is 13 models,
and the older ones take fewer effort levels than the newer.

The pick is stored on the chat as `modelChoice` / `effortChoice` (absent = Claude's default). Both are
fixed when a session starts, so before every message the listener reads the chat's current choice
straight from the database (the chat's details and its messages arrive on separate streams, so the
mirror can lag a choice made just before sending). If it differs from what the open session was
started with, the session is closed and reopened with `--resume --model X --effort Y`: the
conversation carries over (`--selftest` proves this on a real Claude). The choice is checked against the
catalog first: a model Claude no longer lists, or an effort that model doesn't take, is dropped rather
than passed on to fail. The chat's details get `appliedModel` / `appliedEffort` once it is in force.
A choice made before a chat's first message is held by the app and stored with that message.

### What the app is told

Each chat has details at `agent_chat/chats/<chat id>`, its messages at `agent_chat/messages/<chat id>`:

| `chats/<id>` field | written by | meaning |
|---|---|---|
| `title`, `createdAt`, `updatedAt` | app | the chat, titled from its first message |
| `state` | listener | `live` (session open) or `closed` |
| `contextTokens`, `contextWindow`, `contextPercent` | listener | how full the context is, after every message |
| `lastActiveAt`, `idleMinutes` | listener | server time of the last activity, and the idle limit, so the app can show when it closes |
| `sessionId`, `model`, `closedReason` | listener | what to resume; the model Claude reports it is on; why it closed (`idle`, `to make room…`, `the listener stopped`) |
| `modelChoice`, `effortChoice` | app | the model and effort picked for this chat |
| `appliedModel`, `appliedEffort` | listener | the choice the open session was started with |

The context figure is the size of the last prompt Claude was sent plus the reply it just wrote (not the
sum over a turn's calls, which counts the same text more than once), against the window Claude
reports. The app shows it as a bar on every chat and in the list, calls a chat "getting full" at 60%
and "almost full" at 85%, and offers a **New chat** button, so you know when a new chat is due.

Each message is `{role, text, createdAt, status}` under a push-id key. The app writes a user message
with `status: "pending"`. The listener sets it to `working`, feeds it to the chat's session, writes the
reply as a message keyed `<question key>~r` (which sorts straight after its question), and sets the
question to `done` (or `error`). If the listener dies mid-run, the next start marks the stuck message
`error` so the app stops waiting. A chat appears in the app's list once its first message is sent;
deleting a chat removes its details and messages (a session still open for it just idles out).

### What the chat shows while it waits

The app tells the user where their message is, so a long wait never looks like a broken app:

- **Sending / Sent**: the app knows the moment the server confirms its write. With no connection it says
  so and that the message will go out later; if the server refuses it (a missing rule) the text is
  given back with the reason.
- **Claude's PC is online**: the listener writes a heartbeat to `agent_chat/_listener`
  (`{at: <server time>, busy}`) every 20 seconds. The line under the chat's title reads online / busy /
  not connected, and a message that is still unclaimed after a few seconds says which, with what to
  check. On a clean stop (Ctrl+C) the listener zeroes the beat so the app says "not connected" at once.
- **What Claude is doing**: each stream event is turned into an `activity` (`starting`, `thinking`,
  `looking`, `changing`, `writing`) and an `activityText` such as "Looking up todos" or "Reopening this
  chat" on the message. They are cleared when the reply is written. Writes are skipped when nothing
  changed, and a new label inside the same phase waits a second.
- **Stalls**: a run whose heartbeat stops mid-way shows "Lost contact with Claude's PC" rather than a
  spinner that never ends, and a long run shows how long it has been going.

## Details for the todo trigger

- Each todo runs once. Handled ids are kept in `~/.inventoria-mcp/agent-listener-state.json`. The
  first ever start marks any existing `@claude` todos as handled so old ones don't fire.
- It waits 5 seconds after a todo appears (`--settle`) so a title still being typed is not run
  half-written. Only an open (not completed, not deleted) todo whose title starts with `@claude`
  triggers.
- All three streams (todos, chats, messages) reconnect on their own, and every 50 minutes to renew the
  Firebase token.

## Which Claude account it runs on

By default every run uses your **subscription login**, not an API key. A shell that has
`ANTHROPIC_API_KEY` set makes `claude -p` bill that key instead (an exhausted key answers "Credit
balance is too low"), and `~/.claude/settings.json` can carry an `apiKeyHelper` or proxy that does the
same. So the listener drops `ANTHROPIC_API_KEY` / `ANTHROPIC_AUTH_TOKEN` from the run's environment and
passes `--setting-sources project` to skip your user settings.

That needs a subscription login on this machine: run `claude auth login` in a terminal, or
`claude setup-token` and set the token it prints as `CLAUDE_CODE_OAUTH_TOKEN` before starting the
listener. Pass `--use-api-key` to turn all of this off and run on whatever `claude` is set up with.

A failed run's reason is shown in the chat bubble and the listener log.

## Verified

`--selftest` against a real Claude (subscription login): two messages in one session, which kept its
memory; then the session was closed and reopened with `--resume`, and it still remembered. The context
readings (tokens, window, model) came from the real events. The session logic around it (idle close,
resume, the transcript fallback, making room, crashes, ordering, the context figures) is covered by
`test_listener.py` against `fake_claude.py`, which speaks the same protocol. Not yet exercised end to
end: the app's new screens, which need a build.
