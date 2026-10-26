# Agent listener

Lets Claude answer Inventoria, on your own Claude login. `listener.py` runs on your PC, watches the
vault in Firebase, and starts one `claude -p` run for each request. Two things send it a request:

- **The in-app chat** (Today's top bar, or Settings > Claude). You type a message, the listener
  answers it using the Inventoria tools (so it can look things up and make changes), and the reply
  appears as Claude's bubble.
- **A todo titled `@claude ...`**, written anywhere. The reply goes into the todo's description and
  the todo is ticked off.

```bash
python tools/agent-listener/listener.py            # run it for real
python tools/agent-listener/listener.py --dry-run  # only say what it would do
```

Needs Python 3 (standard library only), the `claude` CLI on `PATH`, and the Inventoria MCP server
already set up (see `mcpServer/README.md`): the listener borrows its sign-in from
`~/.inventoria-mcp/`, so it gets no access the MCP server does not already have.

## One-time setup for the chat

The chat lives in `users/<uid>/agent_chat`. The app (the vault owner) can always write there, but the
listener signs in as a joiner, which only has write access to nodes named in `database.rules.json`.
That file now has an `agent_chat` entry, but **rules are not deployed from the repo**: paste the
current `database.rules.json` into the Firebase console (Realtime Database > Rules) and publish.
Until you do, the chat sends but never gets an answer, and the listener logs a 401.
(`@claude` todos need no rule change.)

## What a run can and cannot do

- Only the Inventoria MCP tools are loaded (`--strict-mcp-config`), so no shell, files, Mixar or
  anything else.
- The delete tools and `import_vault` are denied. Edits are otherwise unrestricted: a request can
  create, change and move things, and start or stop timers.
- Anyone who can write to your vault (the app, the web app, anyone you invited) can start a run.
- Runs go one at a time, and are cut off after 10 minutes (`--timeout`).

## How the chat works

Each message is `{role, text, createdAt, status}` under a push-id key. The app writes a user message
with `status: "pending"`. The listener sets it to `working`, runs Claude on the last 20 messages,
writes the reply as a message keyed `<question key>~r` (which sorts straight after its question),
and sets the question to `done` (or `error`). If the listener dies mid-run, the next start marks the
stuck message `error` so the app stops waiting. The app shows a hint if a message sits at `pending`
for a minute: the listener is probably not running.

## Details for the todo trigger

- Each todo runs once. Handled ids are kept in `~/.inventoria-mcp/agent-listener-state.json`. The
  first ever start marks any existing `@claude` todos as handled so old ones don't fire.
- It waits 5 seconds after a todo appears (`--settle`) so a title still being typed is not run
  half-written. Only an open (not completed, not deleted) todo whose title starts with `@claude`
  triggers.
- Both streams reconnect on their own, and every 50 minutes to renew the Firebase token.

## Which Claude account it runs on

By default every run uses your **subscription login**, not an API key. A shell that has
`ANTHROPIC_API_KEY` set makes `claude -p` bill that key instead (an exhausted key answers "Credit
balance is too low"), and `~/.claude/settings.json` can carry an `apiKeyHelper` or proxy that does the
same. So the listener drops `ANTHROPIC_API_KEY` / `ANTHROPIC_AUTH_TOKEN` from the run's environment and
passes `--setting-sources project` to skip your user settings.

That needs a subscription login on this machine, once: run `claude auth login` in a terminal, or
`claude setup-token` and set the token it prints as `CLAUDE_CODE_OAUTH_TOKEN` before starting the
listener. Pass `--use-api-key` to turn all of this off and run on whatever `claude` is set up with.

A failed run's reason is shown in the chat bubble and the listener log.

The chat round trip's last step (a finished answer reaching the app) has not been confirmed on a
subscription login yet: runs so far failed on credit before that point.
