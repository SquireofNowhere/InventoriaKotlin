# Inventoria MCP server

Lets an MCP client (Claude Code, Claude Desktop, anything that speaks the Model Context Protocol)
read and edit your whole vault: items and containers, collections, todos, tracked tasks, Task
Types and schedule blocks.

It is a headless process that talks to the same Firebase Realtime Database as the phone and the web
app, through the REST client in `:shared`. It never touches the phone's local database, so edits
show up on the phone at its next sync, exactly like edits from the web app.

## How it signs in

The server becomes its own Firebase user (anonymous) and **joins your vault with an invite code**, the
same way a second phone does. That means:

- no rule changes and no Google OAuth setup;
- it gets what a joiner gets under `database.rules.json`: read and write on the nine data nodes, and
  nothing else. It cannot delete your account, mint invite codes or add other people;
- you can cut it off any time from the app's sharing list, and the code that let it in is dead after
  24 hours anyway.

## Setup

1. Build the jar (no Android SDK needed):

   ```bash
   ./gradlew -Pinventoria.webOnly=true :mcpServer:fatJar
   ```

   It lands in `mcpServer/build/libs/inventoria-mcp-all.jar`.

2. In the app, generate an invite code (the sharing section of Settings).

3. Join, once, from the repo root so the Firebase settings are read from `.env`
   (`FIREBASE_WEB_API_KEY` and `FIREBASE_DATABASE_URL`; they are remembered afterwards):

   ```bash
   java -jar mcpServer/build/libs/inventoria-mcp-all.jar join ABC123
   ```

   `status` shows whether it can read the vault; `leave` forgets it locally.

4. Register it with your client. For Claude Code:

   ```bash
   claude mcp add inventoria -- java -jar /absolute/path/to/inventoria-mcp-all.jar
   ```

   For a JSON config (Claude Desktop and others):

   ```json
   { "mcpServers": { "inventoria": { "command": "java", "args": ["-jar", "/absolute/path/to/inventoria-mcp-all.jar"] } } }
   ```

Sign-in and the joined vault live in `~/.inventoria-mcp/` (override with `INVENTORIA_MCP_HOME`).
Dates are read in the machine's time zone; set `INVENTORIA_TZ` (e.g. `Europe/Berlin`) if it differs
from your phone's.

## Tools

| Area | Tools |
|---|---|
| Overview | `vault_summary`, `read_node` (raw JSON escape hatch) |
| Items | `list_items`, `get_item`, `create_item`, `update_item` (incl. `quantity_delta`), `move_item`, `set_equipped`, `delete_item`, `link_items`, `unlink_items` |
| Photos | `add_item_image` (file or base64, uploaded to Storage), `remove_item_image`, `set_item_profile_picture` |
| Collections | `list_collections`, `get_collection`, `create_collection`, `update_collection`, `delete_collection`, `set_collection_item`, `remove_collection_item` |
| Todos | `list_todos`, `get_todo`, `create_todo`, `update_todo`, `set_todo_state`, `delete_todo` |
| Planning | `list_schedule_blocks`, `create_schedule_block`, `update_schedule_block`, `delete_schedule_block`, `list_task_types`, `create_task_type`, `rename_task_type`, `delete_task_type` |
| Tracked time | `list_tasks`, `update_task` (name, Task Type), `delete_task`, `create_task` (back-fill a finished segment) |
| Timers | `list_active_timers`, `start_timer`, `pause_timer`, `resume_timer`, `stop_timer` |
| Reports | `time_report` (by day, Task Type, Kind or name), `todo_stats` |
| Bulk | `bulk_update_todos`, `bulk_move_items`, `batch_create` |
| Backup | `export_vault`, `import_vault` (additive only) |
| Undo | `list_deleted`, `restore` |
| Settings | `get_settings`, `set_username` |

## Rules the server follows

These mirror what the phone's sync depends on (see `Vault.kt`):

- every write bumps `updatedAt`, always past the row's old value, because the phone settles a
  conflict with an unsent local edit by comparing the two;
- **deletes are tombstones** (`isDeleted`), never removals. They sync to every device and can be
  undone with `restore`. There is deliberately no hard delete;
- edits send only the changed fields, so they cannot overwrite something another device wrote;
- the tools that have app-side invariants follow them: `move_item` carries linked items along and
  refuses cycles, `set_todo_state` cascades to sub-todos the way the Todos tab does, `set_equipped`
  remembers and restores the previous container;
- new items and collections get millisecond-timestamp ids. The phone numbers its own rows 1, 2, 3...,
  so this avoids a collision with a row the phone made offline and has not synced yet.

## Timers, photos and backups

- **Timers** use the phone's own session rules (ported from `TaskRepository`): a pause closes the
  running segment and a resume opens a new one, stopping freezes the score and ends the session, and
  an interruption resumes what it interrupted. Scores come from `Scoring` in `:shared`, the same
  formula the app uses. A timer started here appears on the phone at its next sync and its elapsed
  time is right immediately, but the foreground-service notification only appears once the app is
  opened. Stopping a timer does not tick off the todo it came from.
- **Photos** are uploaded to this server's *own* Storage folder (`users/<its uid>/item_images`), the
  only place `storage.rules` lets it write, and the tokenized URL is added to the item, exactly as a
  second phone would. This needs `FIREBASE_STORAGE_BUCKET` (same `.env` as the others). Removing a
  photo drops its URL from the item; the file stays in Storage.
- **Export / import** read and write only the `exports/` folder next to the sign-in files. Import is
  additive: a row whose id already exists, even as a deleted tombstone, is never overwritten.

## What it can't do (yet)

- **Phone-only settings** (focus, notification style, penalties...). Only the username syncs, so
  `get_settings` has nothing else to show and there is nothing else to set. Syncing more would be an
  app change.
- **Delete photo files from Storage**, or edit a running timer's start time.
- Reads refetch the nodes they need on every call; fine for a personal vault, not tuned for huge ones.

## Development

```bash
./gradlew -Pinventoria.webOnly=true :shared:jvmTest :mcpServer:test
```

The tests cover the protocol layer, the score and streak rules, and the report maths, without any network.
