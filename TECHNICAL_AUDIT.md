# 🔍 Inventoria Technical Audit: Dead Code, Ghost Features & Unfinished Migrations

This document provides a comprehensive audit of the codebase, identifying broken logic, unfinished features, and architectural anti-patterns.

---

## 🚫 Definitely Broken / Never Wired

### 1. Collection Quick Actions (Stubs)
The `quickEquipCollection` and `quickPackCollection` methods in `CollectionsViewModel` are empty stubs containing only a comment.
- **Missing**: These were intended to allow users to equip or pack an entire collection from the main list screen without opening the collection detail. The logic exists in the detail view but was never ported to the summary level.

### 2. FileInventoryRepository (Dead Code)
**Status:** ✅ Resolved
`FileInventoryRepository.kt` contained a CSV-based inventory management system that predates the Room database implementation. It has been successfully removed from the codebase.

---

## 👻 Incomplete Features / Data Model Ghosts

### 3. InventoryItem.tags (Incomplete Migration) (Resolved)
- **Status**: ✅ Resolved — removed
- The `InventoryItem` model contained both a `tags: List<String>` field and a `category: String` field. All filtering, searching, and the `getParsedTags()` helper operated solely on the `category` string (comma-split into pseudo-tags); `tags` was never populated or read anywhere, indicating a list-based tagging migration that was abandoned midway. Decided not to finish the migration (the comma-hack already works and nothing needs real structured multi-tag support) — removed the dead `tags` field instead. Room DB bumped to version 9.

### 4. Barcode & SKU Support (Headless Feature)
The Room entity for `InventoryItem` includes fields for `barcode` and `sku`, and `InventoryDao.searchItems()` is programmed to search them.
- **The Gap**: There is no UI for scanning barcodes, no input fields in `AddEditItemScreen`, and no display of these values in `ItemDetailScreen`. The data layer is fully prepared, but the UI is non-existent.

### 5. SyncStatusIndicator (Orphaned Component) (Resolved)
- **Status**: ✅ Resolved
- A fully functional and animated `SyncStatusIndicator` component existed, and `InventoryListViewModel` correctly exposed the `syncStatus` as a `StateFlow`, but the component was never actually placed within any screen's composable tree.
- **Fix**: Since no screen shares a common `Scaffold`/top bar (each of the ~6 top-level screens manages its own independently), wiring it into one screen wouldn't have made it visible everywhere. Instead, added a small `SyncStatusViewModel` (just re-exposes `FirebaseSyncRepository.syncStatus`) and render it once in `InventoriaApp()` as a floating pill (`Surface` + `SyncStatusIndicator`) anchored top-end, above the `NavHost` content — visible on every screen without touching any of them individually. Hidden on the one route that already hides the tab bar (`item_location_map`), for consistency.

---

## 🏛️ Anti-Patterns & Decompilation Artifacts

### 6. Reactive Deadlocks in Collection Detail (Resolved)
- **Status**: ✅ Resolved — see [ErrorLog.md #31](ErrorLog.md)
- `CollectionDetailViewModel.observeItems()` called `.first()` (a suspend function) inside a `combine()` flow transform to read the collection's items — a one-time snapshot that only re-ran when one of the *other* combined flows emitted, not when the collection's own items changed. This predicted risk ("stale reads") materialized concretely: saving from the Add Items picker didn't refresh the collection detail screen until leaving and re-entering (which recreates the ViewModel). Fixed by subscribing to `getItemsForCollection(id)` as its own live flow via `flatMapLatest`, matching the pattern already used by `collectionWithItems`/`readiness` in the same class.

### 7. Package Name Mismatch in Tests
The instrumented test in `ExampleInstrumentedTest.kt` asserts that the package name is `com.example.inventoria_kotlin`.
- **Status**: The actual project package is `com.inventoria.app`. This test will always fail until updated to match the current project structure.

---

## ✅ Resolved & Implemented Features

### 8. Custom Username on Splash
- **Implemented**: `SplashScreenContent` now reads `customUsername` from `SettingsRepository`.
- **Logic**: Greets the user with "Welcome back, [Name]" if a custom name or Google display name is available.

### 9. Automatic Splash Navigation
- **Implemented**: The splash screen now automatically navigates to the main screen if any account (Google or Local Anonymous) exists, eliminating the need for returning users to press a button.

### 10. Local Account Management
- **Implemented**: `SettingsScreen` now allows local account users to set a custom display name, which is persisted and shown on the splash screen.

## 🔄 Sync Architecture & Incremental Merging Strategy (New)

### 11. The "isDirty" Incremental Merge Pattern
- **Status**: ✅ Implemented (Replaces Destructive "Overwrite All" Strategy)
- **Background**: The app previously suffered from a "Simultaneous Online" bug. Because Room `Flow`s emit immediately upon connection, opening the app would trigger an instant push of the *entire* local database to Firebase via `setValue()`, destroying any remote changes made by other devices before they could be pulled.
- **Solution (`isDirty` flag)**: 
    - **Models**: All syncable models (`InventoryItem`, `Task`, `InventoryCollection`, `InventoryCollectionItem`, `ItemLink`) now possess an `isDirty: Boolean = false` property. This property is annotated with `@get:Exclude` and `@set:Exclude` so it is strictly local to the device and never sent to Firebase.
    - **Repositories**: Whenever a user modifies data locally (insert, update, equip, pack, etc.), the Repositories (`InventoryRepository`, `TaskRepository`, `CollectionRepository`) explicitly create a copy of the item with `.copy(isDirty = true)`.
    - **FirebaseSyncRepository**: The sync engine now *only* listens to flows of dirty items (e.g., `inventoryDao.getDirtyItemsFlow()`).
    - **Merge vs Overwrite**: Pushes now use `ref.updateChildren(updates)` rather than `ref.setValue()`. This ensures the app only merges its specific local modifications into the cloud, leaving other concurrent edits untouched. Upon a successful push, the items are marked clean (`isDirty = 0`) via the DAOs.
    - **Pull Logic**: When pulling remote data down to the device, the records are inserted with the default `isDirty = false` state, preventing infinite sync loops.

### 12. Collaborative Sync (Invite Codes) Security Model
- **Status**: ✅ Implemented (was previously non-functional end-to-end — see [ErrorLog.md #21](ErrorLog.md))
- **Background**: The invite-code UI, `manualSyncId` redirect, and `sharedWith` registration were all fully built client-side, but the Firebase Realtime Database security rules never granted any cross-account access — every join silently failed at the registration-write step, not just at read time.
- **Current state**: Rules now grant access to accounts listed in an owner's `sharedWith`, gated so only a genuinely valid invite code can self-register there. `SettingsScreen` also enforces local/Google/external-sync as mutually exclusive states and shows a live "Connected Devices" list with revoke, both previously missing.
- **Known gap**: the rules fix only covers Realtime Database. Firebase Storage (item images) uses a separate rules language with no access to RTDB data, so a joined account still can't see the owner's images without a further fix (e.g. syncing `sharedWith` into Auth custom claims via a Cloud Function).

### 13. Momentum-Based Scoring & Interruption Tracking
- **Status**: ✅ Implemented
- **Background**: `Task.score` was previously a computed property (`kind.productivityValue`) — a flat value regardless of how long a task actually ran, so a 5-minute Peacock session scored identically to a 3-hour one.
- **Scoring model**: `score` is now a stored, frozen `Int` column: `kind.productivityValue × segment-duration-in-minutes × momentum multiplier`, rounded. The multiplier is `min(2.5, (1 + rate)^streak)`, where `rate` is 0.15/session for kinds with a negative `productivityValue` (a steeper "escape" rate for draining kinds) or 0.10 for positive ones, and `streak` is the count of consecutive same-`TaskKind` completed sessions immediately preceding this one (`TaskRepository.getStreakCountForKind`, backed by `TaskDao.getRecentCompletedTasks` — fully-stopped sessions only, `isSessionActive = 0`, deduped to one entry per `groupId`, capped at a 20-session lookback).
- **Per-segment freezing, not per-session**: because pause/resume can split one session into several `Task` rows sharing a `groupId`, each segment gets its own frozen score the moment *it* individually finishes (`TaskRepository.pauseSegment()` on pause, `stopTaskAndSession()` on final stop) — not just the session's last segment. This is also what makes item 14 below correct.
- **Interruption tracking (Inner Tasks)**: pausing a task can start a linked "inner task" (`Task.interruptedGroupId` points at the paused session's `groupId`) that starts running immediately and is auto-stopped when the original session resumes (`TaskTrackerViewModel.pauseResumeTask`/`stopTask`, via shared `resumeSession()` helper). `Task.countsForStreak` (default `false`) excludes interruptions from the streak lookback unless explicitly opted in, so an involuntary break doesn't cost an existing streak.
- **Interruption chains**: an interruption can itself be interrupted, forming a stack of paused sessions with only the deepest one actually running. Directly stopping or resuming any session in the stack must collapse everything above it, not just the immediate interrupter — `findInterruptionSessionFor()` finds the session interrupting a given `groupId` by its first segment's `interruptedGroupId` (not just a currently-*running* one, since an intermediate interrupter is often itself paused), and `stopActiveInterruptionChain()` walks that recursively, deepest first. Shared by both `stopTask()` and `pauseResumeTask()`'s resume path — see [ErrorLog.md #34](ErrorLog.md) and [#35](ErrorLog.md) for the two bugs this replaced.
- **Manual time edits**: editing a completed segment's start/end time (`TaskRepository.updateSegmentTime`) recomputes `score` the same way, since it's duration-dependent and would otherwise go stale — see [ErrorLog.md #25](ErrorLog.md).
- **Daily dampening for time-tracked scores**: per-task `score` (above) is unchanged and still frozen at completion time. What changed is aggregation: `TaskTrackerViewModel.personalScoreToday`/`socialScoreToday` no longer sum today's raw `Task.score` directly -- each category's raw total is passed through `dampen(raw)`, a diminishing curve toward +/-5 (`5 * (1 - e^(-|raw|/15))`, sign-preserving), before being combined with Todo contributions (below). This exists because the un-dampened linear formula let a single long session dominate an entire day's score (e.g. ~1200 points from one Peacock session at 3 productivity x ~400 minutes). `totalScoreToday` is just `personalScoreToday + socialScoreToday` (NEUTRAL-kind items always contribute 0). Only *today's* aggregation is dampened -- `*Lifetime` totals remain the plain historical sum, since re-deriving a capped lifetime total would require bucketing the entire history by day on every recompute.
- **Todo Tasks integration**: a `Todo` in `state == TodoState.COMPLETE` (`TodoRepository`/`TodoViewModel`, `com.inventoria.app.ui.screens.todo`) contributes its full, undamped `kind.productivityValue` to that day's score in its category -- bypassing `dampen()` entirely, so todos keep the "natural" scoring the original linear formula was designed around, while free-form time-tracking gets the dampening treatment. A `Todo` not in the COMPLETE state past its `deadline` subtracts `min(daysOverdue, 5)` from that day's category score for as long as it stays open (computed live off `deadline`, not stored/incremented by a job). `Task.originTodoId` links a session back to the todo that started it (via a todo's Start button, `TodoViewModel.startTaskFromTodo`) so `TaskTrackerViewModel.stopTask()` can prompt a "Complete or still ongoing?" check-in when that session ends.
- **Todo tri-state completion & cascade**: `Todo.state` is `INCOMPLETE`/`IN_PROGRESS`/`COMPLETE`, not a boolean. `TodoRepository.setStateWithCascade(id, complete)` sets the target directly to COMPLETE or INCOMPLETE, then walks its whole descendant subtree pushing every currently-INCOMPLETE descendant to IN_PROGRESS (on complete) or reverting every currently-IN_PROGRESS descendant back to INCOMPLETE (on un-complete) -- a descendant the user completed themselves is left alone either way. A parent with a genuine mix of complete/incomplete direct children additionally *displays* as IN_PROGRESS regardless of its own stored state (`TodoViewModel.buildTodoTree`'s `effectiveState`, never written back). `TodoScreen` renders this with Compose Material 3's `TriStateCheckbox`. Deadline-less children inherit their day section from the nearest dated ancestor (`TodoViewModel.effectiveSectionDay`, walking up `parentTodoId`), including following an overdue ancestor into Today; parenting can also be set by dragging one todo's row onto another (`TodoScreen`'s drag handle + `onGloballyPositioned` bounds tracking), guarded by the same `invalidParentIds()` cycle check the add/edit dialog's parent picker uses.

### 14. Active-Session Metrics Gap (Resolved)
- **Status**: ✅ Resolved — see [ErrorLog.md #24](ErrorLog.md)
- All score/breakdown `StateFlow`s in `TaskTrackerViewModel` (today/lifetime, personal/social/total) previously derived only from `_completedSessions` (sessions where every row has `isSessionActive = false`), so an already-finished, already-paused segment of a still-in-progress session didn't count toward metrics until the whole session was eventually stopped. `allFinishedTasks` now combines `_completedSessions` with the finished (`isRunning = false`) segments still sitting inside `_activeSessions`. `ProductivityStatsScreen` had an independent copy of the same bug (its own `completedSessions`-only derivation) and was fixed the same way.

### 15. Collections All Shared Primary Key 0 (Resolved)
- **Status**: ✅ Resolved — see [ErrorLog.md #26](ErrorLog.md)
- `InventoryCollection.id` was a bare `@PrimaryKey` with no `autoGenerate = true`, and `AddEditCollectionViewModel` inserted new collections with `id = 0`. Every collection ever created collided on the same primary key, and the entire "Add Items" flow treats `id != 0L` as its collection-picker-mode sentinel, so it silently never activated. Fixed by making the primary key auto-generate.
- Fixing that surfaced a follow-on bug (runaway duplicate collections on every sync) — see #16 below.

### 16. Deletes Never Sync to Firebase (Items, Tasks, Links, and — until now — Collections) (Resolved)
- **Status**: ✅ Resolved for real — see [ErrorLog.md #33](ErrorLog.md). Superseded three earlier attempts (#27, #29, #30/#32) that patched the symptom instead of the cause.
- **The gap, and why it only ever hit collections/links/collection-items**: `InventoryItem` and `Task` both delete via a soft-delete (`isDeleted = 1, isDirty = 1` on the existing row), which rides the normal "isDirty incremental merge" push (#11) automatically — the deletion itself is just synced data, so it converges correctly no matter how many devices or sync passes touch it. `InventoryCollection`, `InventoryCollectionItem`, and `ItemLink`, by contrast, used hard `@Delete`/`DELETE` queries with no tombstone row left behind at all, so the sync engine had no way to know a removal had ever happened.
- **Why the earlier fire-and-forget `removeValue()` patches (#27/#29/#30/#32) were never going to be enough**: each one only protected *the device performing the delete* from racing its own reads. It did nothing for a **second device** with its own already-synced local copy of the same row — that device's own periodic/reactive push would faithfully re-upload its still-present local data to Firebase, resurrecting the delete, with no way for the deleting device to know or prevent it. This is what "perfect syncing" actually requires: the deletion itself has to be data that propagates, not a one-off side-effecting network call.
- **Final fix**: converted all three entities to the same soft-delete pattern Item/Task already used correctly — added `isDeleted` to `InventoryCollection`/`InventoryCollectionItem`/`ItemLink`, converted their `@Delete` DAO methods to `UPDATE ... SET isDeleted = 1, isDirty = 1`, filtered every UI/business-logic-facing query with `isDeleted = 0` (added an unfiltered `*ForSyncList()` variant for sync's own full-table pushes), and removed the entire `pendingCollectionDeletes`/`pendingLinkDeletes` guard system and the special-cased `deleteCollectionRemote`/`deleteLinkRemote` functions — no longer needed once deletion is just a normal, idempotent, tombstoned write. Added `purgeOldDeletedCollections`/`purgeOldDeletedCollectionItems`/`purgeOldDeletedLinks` (24h retention, matching `Task`'s existing purge pattern) so tombstones don't accumulate forever.

---
*Audit Conducted: 2026-08-08 (sections 1-11 from 2026-03-25, sections 12-14 added, sections 15-16 added, section 16 rewritten)*
