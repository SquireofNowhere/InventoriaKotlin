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

### 3. InventoryItem.tags (Incomplete Migration)
The `InventoryItem` model contains both a `tags: List<String>` field and a `category: String` field. 
- **The Gap**: All filtering, searching, and the `getParsedTags()` helper function operate solely on the `category` string. The `tags` list is never populated or used. This indicates a planned migration to a list-based tagging system that was abandoned midway.

### 4. Barcode & SKU Support (Headless Feature)
The Room entity for `InventoryItem` includes fields for `barcode` and `sku`, and `InventoryDao.searchItems()` is programmed to search them.
- **The Gap**: There is no UI for scanning barcodes, no input fields in `AddEditItemScreen`, and no display of these values in `ItemDetailScreen`. The data layer is fully prepared, but the UI is non-existent.

### 5. SyncStatusIndicator (Orphaned Component)
A fully functional and animated `SyncStatusIndicator` component exists, and `InventoryListViewModel` correctly exposes the `syncStatus` as a `StateFlow`.
- **The Gap**: This component is never actually placed within any screen's composable tree. It is ready to use but effectively invisible to the user.

---

## 🏛️ Anti-Patterns & Decompilation Artifacts

### 6. Reactive Deadlocks in Collection Detail
In `CollectionDetailViewModel.observeItems()`, the code calls `.first()` (a suspend function) inside a `combine()` flow transform.
- **Risk**: This creates hidden coroutines that can cause deadlocks or stale reads. Since collection readiness is already computed via a separate dedicated flow, this manual lookup is redundant and dangerous.

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
- **Interruption tracking (Inner Tasks)**: pausing a task can start a linked "inner task" (`Task.interruptedGroupId` points at the paused session's `groupId`) that starts running immediately and is auto-stopped when the original session resumes (`TaskTrackerViewModel.pauseResumeTask`/`stopTask`, via shared `resumeSession()`/`findActiveInterruptionFor()` helpers). `Task.countsForStreak` (default `false`) excludes interruptions from the streak lookback unless explicitly opted in, so an involuntary break doesn't cost an existing streak.
- **Manual time edits**: editing a completed segment's start/end time (`TaskRepository.updateSegmentTime`) recomputes `score` the same way, since it's duration-dependent and would otherwise go stale — see [ErrorLog.md #25](ErrorLog.md).

### 14. Active-Session Metrics Gap (Resolved)
- **Status**: ✅ Resolved — see [ErrorLog.md #24](ErrorLog.md)
- All score/breakdown `StateFlow`s in `TaskTrackerViewModel` (today/lifetime, personal/social/total) previously derived only from `_completedSessions` (sessions where every row has `isSessionActive = false`), so an already-finished, already-paused segment of a still-in-progress session didn't count toward metrics until the whole session was eventually stopped. `allFinishedTasks` now combines `_completedSessions` with the finished (`isRunning = false`) segments still sitting inside `_activeSessions`. `ProductivityStatsScreen` had an independent copy of the same bug (its own `completedSessions`-only derivation) and was fixed the same way.

---
*Audit Conducted: 2026-08-08 (sections 1-11 from 2026-03-25, sections 12-14 added)*
