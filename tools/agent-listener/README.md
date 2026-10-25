# Agent listener

Write a todo titled `@claude ...` in Inventoria (phone, web app or anywhere) and Claude acts on it.
`listener.py` streams the vault's `todos` node from Firebase, and when one appears it runs
`claude -p` once for it on your own Claude login. The answer is written into the todo's description
and the todo is ticked off.

```bash
python tools/agent-listener/listener.py            # run it for real
python tools/agent-listener/listener.py --dry-run  # only say what it would run
```

Needs Python 3 (standard library only), the `claude` CLI on `PATH`, and the Inventoria MCP server
already set up (see `mcpServer/README.md`): the listener borrows its sign-in from
`~/.inventoria-mcp/`, so it gets no access the MCP server does not already have, and no database
rule changes are needed.

## What a run can and cannot do

- Only the Inventoria MCP tools are loaded (`--strict-mcp-config`), so no shell, files, Mixar or
  anything else.
- The delete tools and `import_vault` are denied. Edits are otherwise unrestricted: a `@claude`
  todo can create, change and move things, and start or stop timers.
- Anyone who can write to your vault (the app, the web app, anyone you invited) can start a run.
  The todo text is treated as the user's request.

## Details

- Each todo runs once. Handled ids are kept in `~/.inventoria-mcp/agent-listener-state.json`. The
  first ever start marks any existing `@claude` todos as handled so old ones don't fire.
- It waits 5 seconds after a todo appears (`--settle`) so a title still being typed is not run
  half-written, and runs one at a time. A run is cut off after 10 minutes (`--timeout`).
- Only an open (not completed, not deleted) todo whose title starts with `@claude` triggers.
- Reconnects on its own, and every 50 minutes to renew the Firebase token.

## Known limit

`claude -p` has to be able to sign in from a detached process. It was **not** confirmed to work from
the machine this was written on, where `~/.claude/settings.json` routes `claude` through a local
claude-code-router (`apiKeyHelper` plus `ANTHROPIC_BASE_URL`): started from inside a Claude Code
session it failed with "Invalid API key", and started with that session's variables removed it
hung. Run `claude -p "say ok"` in a normal terminal first; if that works, so will the listener when
started from the same terminal.
