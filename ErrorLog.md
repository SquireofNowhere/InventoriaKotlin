# 🛠️ Inventoria Error Log & Resolution History

This document tracks significant bugs encountered during development and the technical solutions implemented to resolve them.

---

## 🐞 1. Task Desync During Rapid Operations
**Status:** ✅ Resolved

### 📝 Problem
Starting and stopping a task segment in rapid succession (under 1 second) caused multiple devices to desync. One device would show the task stopped, while others would show it still running or "ghost" restarted.

### 🔍 Root Cause
- **Clock Precision**: `System.currentTimeMillis()` can return the same value for operations happening in the same millisecond. Sync logic cannot order these correctly.
- **Atomic Split**: The "Stop Task" action involved two separate database calls (Complete Task + End Session), creating a race condition window.

### 🛠️ Final Fix
- **Monotonic Timestamps**: Implemented a monotonic counter in `TaskRepository.kt` that guarantees a strictly increasing `updatedAt` value for every operation, even if they occur in the same millisecond.
- **Atomic Transactions**: Created a `@Transaction` method `stopTaskAndSession` in `TaskDao` to ensure both state changes are committed as a single unit.

---

## 🐞 2. Link Persistence & "Zombie" Data
**Status:** ✅ Resolved

### 📝 Problem
Removing links between items would work locally, but the links would reappear after an app restart or a cloud sync.

### 🔍 Root Cause
- **Merging vs Overwriting**: The sync engine used `updateChildren` when pushing to Firebase. This merged local lists with cloud data, making deletions impossible (the cloud would just add the "missing" link back to the local device).

### 🛠️ Final Fix
- **Full State Overwrite**: Switched to `setValue()` for all sync nodes in `FirebaseSyncRepository.kt`. This ensures that if a link is deleted locally, it is removed from the cloud, and subsequently from all other devices.

---

## 🐞 3. Disappearing Items & Self-Parenting Loops
**Status:** ✅ Resolved

### 📝 Problem
Certain items (like a "Bag") would disappear from the inventory list, and opening their details would crash the app.

### 🔍 Root Cause
- **Logical Circularity**: An item was set as its own physical parent (`parentId == id`). This caused the recursive location resolver and hierarchy builder to enter an infinite loop.

### 🛠️ Final Fix
- **Sanitization Guard**: Added repository-level protection in `InventoryRepository.kt` that forces `parentId` to `null` if an item tries to parent itself.
- **Recursion Ticker**: Added a `visited` set to all recursive functions in the ViewModels and Repository to safely break out of any loops caused by "bad" data in the database.

---

## 🐞 4. Drag-and-Drop Ejection
**Status:** ✅ Resolved

### 📝 Problem
Dragging an item that was part of a linked group out of a container would sometimes "eject" it into root space while its linked followers stayed in the container, or vice versa.

### 🔍 Root Cause
- **Follower Application**: The drag-and-drop handler was only updating the `parentId` of the specific item being dragged, ignoring its logical followers.

### 🛠️ Final Fix
- **Recursive Move**: Updated `moveItem` in `InventoryListViewModel.kt` to explicitly request a follower update (`applyToFollowers = true`). Now, moving a leader item physically moves the entire logical group.

---

## 🐞 5. Multi-Device "Sync-Back" Race Condition
**Status:** ✅ Resolved

### 📝 Problem
Actions on Device A would briefly reflect on Device B, but then Device B would "sync back" its old state, overriding the new change.

### 🔍 Root Cause
- **Sync Echo**: Device B would apply a cloud change, which would trigger a local database observer, which Device B would then mistake for a "new local change" and push back to the cloud.

### 🛠️ Final Fix
- **Atomic Ignore Counter**: Replaced the boolean sync guard with an `AtomicInteger`. When a cloud update starts, the counter increments; local changes are ignored until the counter returns to zero.
- **Flow Throttling**: Used `collectLatest` on the Firebase listener to ensure only the absolute latest cloud state is processed, canceling any overlapping stale pulls.

---

## 🐞 6. Task Cross-Device State Mismatch
**Status:** ✅ Resolved

### 📝 Problem
Closing the app on one device and making task changes on another resulted in tasks not syncing or showing outdated states.

### 🔍 Root Cause
- **Firebase Race Conditions:** `ref.setValue()` on the task node was overwriting the entire task list, potentially deleting tasks that hadn't synced to the local device yet.
- **Clock Skew:** If a device's clock was behind, its "new" changes had lower timestamps than existing cloud data, causing them to be ignored.

### 🛠️ Final Fix
- **Atomic Node Updates**: Switched to `ref.updateChildren()` in `FirebaseSyncRepository.kt` to update only modified tasks rather than overwriting the whole collection.
- **Version Seeding**: Updated `TaskRepository.kt` to seed its internal clock from the highest timestamp in the database, ensuring strictly monotonic versioning regardless of system clock skew.

---

## 🐞 7. Zombie Data Recovery After Deletion
**Status:** ✅ Resolved

### 📝 Problem
Deleting a task on one device would work, but the task would "reappear" when another device synced. This happened because the cloud node for the task was simply missing, and the other device treated its local copy as a "new addition" to the cloud.

### 🔍 Root Cause
- **Absence of Proof**: There was no timestamped record of a deletion. The sync engine couldn't distinguish between a task that was *deleted* and one that was *never uploaded*.

### 🛠️ Final Fix
- **Soft Delete (Tombstones)**: Implemented an `isDeleted` flag in the data models. Deletions are now timestamped state changes that sync to all devices.
- **Auto-Purge**: Added a 24-hour background cleanup job that physically removes these "tombstone" records from the local database once they have had sufficient time to propagate across all devices.

---

## 🐞 8. UI Feedback Lag in Task Details
**Status:** ✅ Resolved

### 📝 Problem
Changing a task's type (e.g., from Neutral to Social) in the detail dialog would sync correctly to the database, but the dialog itself wouldn't update its colors or icons until it was closed and reopened.

### 🔍 Root Cause
- **Stateless Dialog**: The dialog was using a static `Task` object passed at the moment of opening, rather than observing the live state from the ViewModel's session flows.

### 🛠️ Final Fix
- **Reactive Referencing**: Updated the detail dialog trigger to use a derived "live" reference. The UI now looks up the latest version of the specific task ID from the active session state, ensuring instantaneous visual feedback for all property changes.

---

## 🐞 9. Item Overriding on Creation
**Status:** ✅ Resolved

### 📝 Problem
Adding a new item in the inventory screen would often override the last created item instead of creating a new entry.

### 🔍 Root Cause
- **Static Default ID**: The `AddEditItemViewModel` was initializing new items with an ID of `0L`. Since the database used `OnConflictStrategy.REPLACE` and the ID wasn't auto-generating, every new "unsaved" item shared the same key, causing overwrites.

### 🛠️ Final Fix
- **Dynamic ID Generation**: Updated `onSaveClick` to generate a unique ID using `System.currentTimeMillis()` for all new items, ensuring they occupy unique rows in the database immediately.

---

## 🐞 10. Task Detail Data Loss & Keyboard issues
**Status:** ✅ Resolved

### 📝 Problem
Pressing "Done" on the keyboard or tapping outside an active text field in the Task Detail dialog would close the keyboard but fail to save the name change.

### 🔍 Root Cause
- **Missing Action Handlers**: The `KeyboardActions` for "Done" only cleared focus but didn't trigger the ViewModel's update functions. Tap-to-clear logic was inconsistent.

### 🛠️ Final Fix
- **Explicit Save Triggers**: Added explicit update calls to `KeyboardActions(onDone = { ... })`.
- **Dismiss Guard**: Added a save check to the `onDismissRequest` of all detail dialogs to catch any uncommitted edits before the UI closes.

---

## 🐞 11. Database Schema Mismatch Crash
**Status:** ✅ Resolved

### 📝 Problem
The app would crash immediately upon startup or during account initialization with a `java.lang.IllegalStateException: Room cannot verify the data integrity.`

### 🔍 Root Cause
- **Version Mismatch**: Recent changes to the `InventoryItem` model (adding gallery support) changed the underlying database schema without an accompanying increment to the Room database version number.

### 🛠️ Final Fix
- **Version Bump**: Incremented the database version from `1` to `2` in `InventoryDatabase.kt`.
- **Destructive Migration**: Enabled `.fallbackToDestructiveMigration()` in the Hilt Database module to allow the app to reconstruct the local database automatically following schema changes.

---

## 🐞 12. Global Image Overwrite Bug
**Status:** ✅ Resolved

### 📝 Problem
Every time a new picture was added to an item, it would overwrite every other picture in the entire user's inventory.

### 🔍 Root Cause
- **Path Collision**: `FirebaseStorageRepository.kt` was using the original system-provided filename (e.g., `temp_capture.jpg`) as the destination path. Multiple items or photos sharing the same generic filename would overwrite each other in the cloud.

### 🛠️ Final Fix
- **UUID Filenames**: Updated the storage repository to generate a unique, random UUID prefixed with a timestamp for every single upload. This guarantees that every image occupies a unique path in Firebase Storage, preventing any accidental overwrites.

---

## 🐞 13. UI Freezing & Large Image Upload Delay
**Status:** ✅ Resolved

### 📝 Problem
The app would become unresponsive ("frozen") when saving an item with multiple photos while it waited for the uploads to complete.

### 🔍 Root Cause
- **Blocking Sequential Logic**: The ViewModel was uploading images sequentially within the main save flow before navigating back, making the user wait for network completion.

### 🛠️ Final Fix
- **Background Upload Flow**: Overhauled image management to use a local `pendingImages` list. The app now saves text data and navigates the user back to the list immediately, while a background coroutine handles the actual cloud uploads.
- **Progress Tracking**: Added an `ImageUpload` helper class to track `isUploading` and `isError` states, providing real-time feedback (spinners/error icons) on thumbnails while the background process runs.

---

## 🐞 14. Context Menu Suppressed by Drag Gestures
**Status:** ✅ Resolved

### 📝 Problem
Holding down an item in the inventory list to open the context menu would often fail. The drag-and-drop feature worked, but the long-press for the menu was unreliable.

### 🔍 Root Cause
- **Gesture Collision**: The `detectDragGesturesAfterLongPress` modifier on the list was consuming the long-press event. The logic to trigger the menu in `onDragEnd` only worked if the user released the touch perfectly still, which rarely happened in practice.

### 🛠️ Final Fix
- **Combined Clickable**: Implemented `.combinedClickable` on the individual item rows. By explicitly defining `onLongClick` at the row level, the menu is triggered immediately upon the long-press threshold being met, regardless of minor pointer movements, while still allowing the parent `pointerInput` to detect dragging.

---

## 🐞 15. Tombstone Overwrite During Sync Pull
**Status:** ✅ Resolved

### 📝 Problem
Deleting a task locally would correctly set `isDeleted = 1`. However, if the sync pull triggered before the deletion push reached Firebase, the local "tombstone" would be blindly overwritten by the cloud's active state (`isDeleted = 0`), causing the task to "reappear" instantly.

### 🔍 Root Cause
- **Blind Replace**: Pull methods in `FirebaseSyncRepository.kt` were using `OnConflictStrategy.REPLACE` without checking timestamps. This allowed older cloud data to overwrite newer local states.

### 🛠️ Final Fix
- **Timestamp Filtering**: Updated all `pull` methods (`pullTasksFromFirebase`, `pullItemsFromFirebase`, etc.) to perform a per-record `updatedAt` comparison. A cloud record is now only inserted into the local database if it is strictly newer than the existing local version.
- **DAO Extensions**: Added `getLink` and `getCollectionItem` methods to `ItemLinkDao` and `CollectionDao` respectively to support these lookups for models with composite keys.

---

## 🐞 16. Deletions Not Propagating Across Devices
**Status:** ✅ Resolved

### 📝 Problem
Deleting an item or task on one device would not propagate to other devices. The deleted record would disappear locally but remain on other devices because the cloud node push omitted soft-deleted records.

### 🔍 Root Cause
- **Omission from Sync Flow**: The DAO queries used by the sync engine (`getAllTasks()` and `getAllItems()`) explicitly filtered out records where `isDeleted = 1`. Therefore, when a record was marked as deleted, it fell out of the synchronization stream and the cloud was never informed of the deletion.

### 🛠️ Final Fix
- **Sync-Specific Queries**: Created `getAllTasksForSync()` and `getAllItemsForSync()` in the DAOs that query all records regardless of their `isDeleted` status (ordering by `updatedAt DESC`).
- **Sync Repository Update**: Switched `FirebaseSyncRepository` to use these new sync-specific queries. Now, when a record is soft-deleted locally, the updated record (with `isDeleted = true`) is pushed to Firebase and correctly processed by other devices during their pull cycles.

---

## 🐞 17. Google Sign-In Failure (Error 12500)
**Status:** 🚨 Unresolved / Investigation Required

### 📝 Problem
Attempting to sign in with Google fails immediately, returning an `ApiException: 12500` error code. This prevents users from accessing cloud sync features and backing up their data.

### 🔍 Root Cause
- **Configuration Mismatch**: Error 12500 is a generic "Internal Error" from Google Play Services, frequently caused by missing SHA-1 fingerprints in the Firebase Console or a misconfigured OAuth consent screen.
- **Client ID Issues**: The `web_client_id` used for the sign-in request might not match the one configured for the current environment in the Google Cloud Console.

### 🛠️ Proposed Fix (Pending)
- **Certificate Verification**: Ensure that the SHA-1 certificates for both debug and release builds are added to the Firebase project settings.
- **Client ID Check**: Double-check the `google-services.json` file and verify that the correct client ID is being passed to the `GoogleSignInOptions`.
- **OAuth Console**: Verify that the OAuth consent screen is configured and published in the Google Cloud Console.

---

## 🐞 18. Task Segment Update Target Mismatch
**Status:** 🚨 Unresolved / Investigation Required

### 📝 Problem
In running sessions, when a user changes the type (TaskKind) of the currently running segment, the update is incorrectly applied to the most recent *completed* segment in that session instead of the active one.

### 🔍 Root Cause
- **Index/Targeting Logic**: The `ActiveSessionCard` uses a `refTask` (calculated as `activeSegment?.task ?: session.segments.firstOrNull()`) to populate the `TaskKindDropdownMenu`. When `onUpdateKind` is fired, it calls `viewModel.updateSessionKind(session.groupId, it)`.
- **Session-Wide vs. Segment-Specific**: The `updateSessionKind` method currently updates the *entire session's* default kind or targets the wrong record in the DAO because it doesn't specifically distinguish between the "active" segment and the "history" segments within that group.

### 🛠️ Proposed Fix (Pending)
- **Specific Targeting**: Ensure `updateSessionKind` specifically targets the task ID of the active segment if one exists, rather than applying a blanket update to the `groupId`.
- **UI State Verification**: Verify that the `TaskKindDropdownMenu` in `ActiveSessionCard` is correctly passing the intent to update the *running* task specifically.

---

## 🐞 19. Realtime Listener Errors Crashed the Whole App
**Status:** ✅ Resolved

### 📝 Problem
The app would crash a few seconds after launch — long enough to show the dashboard, then die — whenever a Firebase Realtime Database listener hit any error (most commonly Permission Denied from a bad sync target, see #21).

### 🔍 Root Cause
`FirebaseSyncRepository.setupNodeSync()`'s `callbackFlow` closed with the exception on `onCancelled` (`close(error.toException())`), and the `firebaseFlow.collect { ... }` coroutine consuming it had no `try/catch` — unlike every other sync path in the same file, which does catch. The exception propagated uncaught to `InventoriaApplication`'s global handler, which logs "CRITICAL CRASH" and calls `System.exit(1)`.

### 🛠️ Final Fix
Wrapped the collect in `try/catch`, surfacing listener failures via `SyncStatus.Error` instead of letting them crash the process. Also hardened `setupSettingsSync`'s username listener the same way — it had no `finally` at all before this.

---

## 🐞 20. Flow Mode's Auto-Start Hung Forever After Any Sync Error
**Status:** ✅ Resolved

### 📝 Problem
Once a permission error occurred anywhere in the sync path, Flow Mode's "start the next task automatically" behavior stopped working for the rest of the app's lifetime — it would just hang indefinitely after stopping a task.

### 🔍 Root Cause
All 5 `pull*FromFirebase` functions increment `syncIgnoreCount` and decrement it in a `finally { delay(1000); syncIgnoreCount.decrementAndGet() }`. `syncOnAppOpen()` runs all 5 in parallel inside one `coroutineScope { ... awaitAll() }` — if any one fails, `coroutineScope` cancels its siblings. A sibling already suspended inside that `delay(1000)` gets cancelled immediately and never reaches the decrement, leaking the counter upward permanently. `TaskTrackerViewModel.stopTask()`'s Flow Mode logic explicitly does `while (syncRepository.isSyncing()) delay(100)` before starting the next task — once the counter leaks above zero, that loop never exits.

### 🛠️ Final Fix
Wrapped each `delay(1000); syncIgnoreCount.decrementAndGet()` (and the equivalent path in the username listener) in `withContext(NonCancellable) { ... }`, so the decrement always runs even when the enclosing scope is cancelled.

---

## 🐞 21. Invite Code / Collaborative Sync Never Actually Worked
**Status:** ✅ Resolved (requires the Firebase Console rules change below to be deployed)

### 📝 Problem
The whole invite-code feature (generate a code, have another account join your database) was fully built client-side — but not one part of it ever actually granted cross-account access. Anyone who "joined" via a code just got silently stuck with permission-denied errors on everything.

### 🔍 Root Cause
The deployed Firebase Realtime Database security rules were:
```json
{ "rules": { "users": { "$uid": {
  ".read": "$uid === auth.uid",
  ".write": "$uid === auth.uid"
} } } }
```
This only ever granted access to the literal owner. `FirebaseAuthRepository.linkToUser()` writes `users/{ownerUid}/sharedWith/{joinerUid}` to register a join — but that write itself requires `auth.uid === ownerUid`, which a joiner never satisfies, so it was **always** silently denied. Confirmed directly: after one device set `manualSyncId` to another account, that account's own "Connected to your database" list stayed at zero — proof the registration write never landed, not just that reads were blocked. The `invites` node (mapping a code to its owner) had **no rule at all**, meaning even generating a code failed by default-deny.

### 🛠️ Final Fix
Updated the rules (in the Firebase Console — not part of this repo) to:
```json
{
  "rules": {
    "invites": {
      "$code": {
        ".read": "auth != null",
        ".write": "auth != null && (!data.exists() || data.val() === auth.uid)"
      }
    },
    "users": {
      "$uid": {
        ".read": "$uid === auth.uid || (auth != null && root.child('users').child($uid).child('sharedWith').child(auth.uid).exists())",
        ".write": "$uid === auth.uid || (auth != null && root.child('users').child($uid).child('sharedWith').child(auth.uid).exists())",
        "sharedWith": {
          "$joinerUid": {
            ".write": "$uid === auth.uid || ($joinerUid === auth.uid && root.child('invites').child(newData.val()).val() === $uid)"
          }
        }
      }
    }
  }
}
```
Owners keep full self-access; anyone listed in `sharedWith` gets access to the owner's whole subtree; a joiner can self-register into `sharedWith` only with a code that genuinely maps back to that owner. **Known gap**: this only fixes Realtime Database — Firebase Storage (images) uses a separate rules language that can't reference RTDB data, so a joiner still can't see the owner's images without a further fix (likely a Cloud Function syncing `sharedWith` into Auth custom claims).

---

## 🐞 22. OutOfMemoryError on Low-RAM Devices from a Bloated Local Sync Cache
**Status:** ✅ Resolved (per-device; root cause fixed by #21)

### 📝 Problem
On a Galaxy A22 (much less heap than the tablet used for most testing), the app crashed on launch with an `OutOfMemoryError`, before the UI ever settled.

### 🔍 Root Cause
```
Caused by: java.lang.OutOfMemoryError: Failed to allocate a 56 byte allocation...
  at com.google.firebase.database.core.Repo.restoreWrites(Repo.java:273)
  at com.google.firebase.database.core.Repo.deferredInitialization(Repo.java:228)
```
Because of #21, every write to the bad sync target failed and got retried by Firebase's local offline-persistence layer, which queues unacknowledged writes. That queue grew unbounded on disk since nothing ever succeeded to clear it. On startup, Firebase restores that queue into memory — on a device with a much smaller heap limit, the already-bloated queue blew the heap before the app could finish initializing.

### 🛠️ Final Fix
`adb shell pm clear com.inventoria.app` (or uninstall/reinstall) to wipe the bloated local cache. This is a symptom, not the cause — without #21's rules fix, a device could accumulate the same bloat again from any other invalid sync target.

---

## 🐞 23. Fresh Anonymous Account Couldn't Write Its Own Data
**Status:** ✅ Resolved

### 📝 Problem
Right after `pm clear` created a brand-new anonymous account, writes to that account's *own* data (e.g. generating an invite code) failed with Permission Denied — even though the Firebase Rules Simulator confirmed the exact same write should be allowed for that UID.

### 🔍 Root Cause
Diagnosed by temporarily logging the SDK's auth state and forcing an ID token refresh right before the failing write. The UID and auth state were already correct locally; the forced refresh succeeded and returned a valid token, and the write succeeded immediately after. Ruled out first: rules logic (Simulator said allowed), Firebase App Check (unenforced on this project), device clock skew (verified in sync with host machine). Conclusion: a freshly-issued anonymous session's ID token can briefly lag behind Firebase's backend recognizing it as valid — a narrow propagation-consistency window most visible right after a brand-new account is created.

### 🛠️ Final Fix
`FirebaseAuthRepository.getOrCreateUserId()` — the single point every other operation (image uploads, `syncOnAppOpen()`, invite code generation) gets its UID from — now forces `getIdToken(true)` once, right after a fresh `signInAnonymously()` succeeds, before returning. Fixes it at the source instead of needing the same guard at every call site.

---

## 🐞 24. Paused Segments of an Active Session Excluded From Metrics
**Status:** ✅ Resolved

### 📝 Problem
Pausing a task mid-session (e.g. a lunch break) didn't count that already-worked time toward Today's Productivity or lifetime totals — the numbers only updated once the *whole session* was eventually stopped, sometimes hours or days later. Confirmed live during momentum-scoring testing: pausing a ~17-minute Coding segment jumped Today's Productivity from 17 to 54 pts *before* the session was ever stopped, proving the fix; it would previously have stayed at 17 until Stop was pressed.

### 🔍 Root Cause
Every score/breakdown `StateFlow` in `TaskTrackerViewModel` (`personalScoreToday`, `totalScoreLifetime`, `scoreBreakdownToday`, etc.) derived only from `_completedSessions` — the set of groups where *every* row has `isSessionActive = false`. A session that's paused but not finally stopped still has `isSessionActive = true` on its rows (only `stopTaskAndSession` flips that, not pausing), so its already-finished segments were invisible to every metric, even though the same segments were already visible in the UI (inside the active session card's expandable "Previous segments" list). `ProductivityStatsScreen` had an entirely separate, independent copy of the same bug (`completedSessions.flatten()` as its own `allTasks` source).

### 🛠️ Final Fix
Added `TaskTrackerViewModel.allFinishedTasks`, combining `_completedSessions.flatten()` with the finished (`isRunning = false`) segments still sitting inside `_activeSessions` (`active.flatMap { it.segments }`). All score/breakdown `StateFlow`s now derive from this instead. `ProductivityStatsScreen` was switched to collect the same exposed `allFinishedTasks` instead of its own buggy derivation.

---

## 🐞 25. Score Went Stale After Manually Editing a Segment's Time
**Status:** ✅ Resolved

### 📝 Problem
Editing a completed segment's start/end time from the Task Detail dialog changed its duration but left its `score` untouched — found by cross-checking a segment's stored `score` against what the momentum formula predicted for its duration during testing (#13 in TECHNICAL_AUDIT.md) and finding a mismatch traceable to an earlier time edit.

### 🔍 Root Cause
`TaskTrackerViewModel.updateSegmentTime()` called `repository.updateTask(task.copy(startTime = start, endTime = end, duration = end - start))` directly — recalculating `duration` but never touching `score`, which is duration-dependent under the new momentum scoring model (previously harmless, since the old flat `score` was duration-independent and this gap didn't exist).

### 🛠️ Final Fix
Added `TaskRepository.updateSegmentTime(task, start, end)`, which recomputes `score` via the same `computeFrozenScore()` used by pause/stop, using the current streak state (there's no way to reconstruct exactly what the streak looked like at the segment's original completion time, so this is the same best-effort the initial freeze already relies on). `TaskTrackerViewModel.updateSegmentTime()` now calls this instead of building the update inline.

---

## 🐞 26. Every Collection Got Primary Key 0, Breaking "Add Items"
**Status:** ✅ Resolved

### 📝 Problem
"Adding items into a collection doesn't work" — reported twice, still broken on v1.51 despite a prior fix (wiring up `InventoryListScreen`'s tap-to-toggle in collection-picker mode). Live-reproduced by relaunching the app and stepping through Collections → a collection → Add Items on the physical tablet: the screen that opened was the plain "Inventory" screen (title "Inventory", "+" FAB visible), not "Collection Items" — meaning picker mode never activated at all.

### 🔍 Root Cause
`InventoryCollection.id` was declared `@PrimaryKey` **without** `autoGenerate = true`. `AddEditCollectionViewModel.onSave()` builds new collections with `id = id ?: 0L`, so every collection ever created (not just this user's test one) got inserted with `id = 0` — confirmed directly in the Room DB (`SELECT id, name FROM InventoryCollection` returned `(0, 'test things')`). Since `CollectionDao.insertCollection` uses `OnConflictStrategy.REPLACE`, creating a second collection would have silently overwritten the first (same colliding id). Separately, the entire add-items flow (`InventoryListScreen`'s `isCollectionPickerMode`, the nav route's `fromCollection` arg default) uses `id != 0L` as its "is a real collection" sentinel — so a collection whose actual id *is* 0 is indistinguishable from "no collection selected" and always fell through to the plain Inventory screen.

### 🛠️ Final Fix
Added `autoGenerate = true` to `InventoryCollection`'s `@PrimaryKey` (`Collection.kt`) so Room assigns real, unique, non-zero ids going forward. Bumped `InventoryDatabase` to version 7 (relies on the existing `fallbackToDestructiveMigration()`, same as prior schema bumps this session — local data resets, cloud data unaffected).

---

## 🐞 27. Collections Kept Duplicating on Every Sync
**Status:** ✅ Resolved

### 📝 Problem
Right after the #26 fix (autoGenerate primary keys), creating a collection resulted in it appearing 5 times in the Collections list. Confirmed live by pulling the Room DB directly: 5 rows, all named "test things", with real but distinct auto-generated ids (4-8).

### 🔍 Root Cause
Firebase collection sync uses a **persistent live listener** (`addValueEventListener`) on the whole `collections` node — it fires on *every* write to *any* child, including the app's own writes. Before #26, every collection was pushed to Firebase under key `"0"` (since local id was always 0), so one stale legacy node (`collections/0`, payload `id: 0`) was sitting in the cloud. After #26 made `id = 0` mean "please auto-generate" locally, `pullCollectionsFromFirebase` kept handing that same id-0 payload to `collectionDao.insertCollection()` — and because Room can't REPLACE-match an autoGenerate id of 0 against anything, **every pull inserted a brand new row** with a fresh id, which was then pushed back to Firebase under *that* key, which retriggered the live listener, which pulled again (the original `collections/0` node was never removed) — an unbounded feedback loop, only interrupted by the burst of initial post-migration sync events settling down (5 rows by the time it was checked; would very likely have resumed on the next full resync, e.g. next app restart).

A second, compounding gap: collection deletion (`CollectionRepository.deleteCollection`) never pushed a deletion to Firebase at all — it only removed the local row. That meant even deleting the duplicates by hand would not have stuck; the next `collections` sync would have pulled the same (now non-zero, "legitimate"-looking) duplicate nodes straight back down.

### 🛠️ Final Fix
- `FirebaseSyncRepository.pullCollectionsFromFirebase` now derives each collection's id from the Firebase **key** (not the payload), and treats key `"0"` as unaddressable legacy data: it removes that node from Firebase once instead of ever inserting from it, which stops the loop at its source.
- Added `FirebaseSyncRepository.deleteCollectionRemote(id)` and wired it into `CollectionRepository.deleteCollection`, so deleting a collection now actually removes it from Firebase instead of only the local row. (Item/task/link deletion still don't push to Firebase either — same gap, out of scope here; see [TECHNICAL_AUDIT.md #16](TECHNICAL_AUDIT.md).)

---

## 🐞 28. Collection Delete Felt Laggy / "Didn't Delete Immediately"
**Status:** ✅ Resolved

### 📝 Problem
Reported right after the multi-select delete feature shipped: deleting collections didn't remove them from the list right away.

### 🔍 Root Cause
Two related issues, both from the #27 fix that made collection deletes push a removal to Firebase:
1. `CollectionsViewModel.deleteSelectedCollections()` looped over selected ids with a plain `forEach`, calling the suspend `CollectionRepository.deleteCollection()` for each one in sequence. Each call's local delete is instant, but the function didn't return until it *also* awaited the Firebase `removeValue()` network round-trip — so with multiple collections selected, every subsequent item's instant local delete was stuck waiting behind the previous item's network call.
2. Separately, `CollectionDetailViewModel.deleteCollection()`'s single-collection delete button only calls `onDeleted()` (navigate back) *after* `collectionRepository.deleteCollection()` fully returns — which included that same Firebase await, so even a single delete could sit unresponsive for a moment on a slow connection.

### 🛠️ Final Fix
`FirebaseSyncRepository.deleteCollectionRemote()` is no longer `suspend` — it fires the Firebase removal on the repository's own background scope instead of making callers await it, since local-first deletion should never be gated on a network round-trip. `CollectionRepository.deleteCollection()` now returns as soon as the local row is gone. Also parallelized `deleteSelectedCollections()`'s per-id calls with `async`/`awaitAll` as defense in depth for any future case where a delete path does need to wait on something.

---

## 🐞 29. Unlinking Items Never Synced Either
**Status:** ✅ Resolved

### 📝 Problem
Follow-up to #27/#28: asked whether items and tasks had the same "delete doesn't sync" gap as collections. They don't — both use a soft-delete (`isDeleted`/`isDirty` flag) that rides the normal sync automatically. But checking surfaced a real instance of the same underlying gap in a different feature: removing a link between two items (`ItemDetailScreen`'s unlink button) only ever deleted the local `ItemLink` row.

### 🔍 Root Cause
`ItemLinkDao.removeLink` is a hard `DELETE FROM ItemLink WHERE ...` with no `isDirty`-flaggable tombstone possible (the row is just gone), same category of gap as #27's collections bug. `InventoryRepository.removeLink` never told Firebase about the removal at all.

### 🛠️ Final Fix
Added `FirebaseSyncRepository.deleteLinkRemote(followerId, leaderId)` (same fire-and-forget-on-repositoryScope pattern as `deleteCollectionRemote`, keyed the same way the existing link push already does: `"${followerId}_${leaderId}"`), called from `InventoryRepository.removeLink` right after the local delete.

---

## 🐞 30. Multi-Select Delete Only Ever Deleted One Collection
**Status:** ✅ Resolved

### 📝 Problem
Asked to test multi-select delete with all 19 duplicate "test things" collections (leftover from #27) selected. Confirmed via direct DB inspection: after confirming "Delete 19 Collections?", exactly 18 remained — only one was actually gone. Repeated the exact same test on the remaining 18: again exactly one deleted, 17 remained. No crash, no exception anywhere in logcat — the deletes were silently being undone, not failing.

### 🔍 Root Cause
A race between local delete and Firebase's **live** listener. `FirebaseSyncRepository` uses `addValueEventListener` on the whole `collections` node, which fires on *every* write to *any* child — including the app's own writes, and including each of the N `deleteCollectionRemote()` calls' own `removeValue()` as it individually lands server-side. Each such event delivers a full snapshot of the *current* server state, which — while N-1 of the N deletes are still in flight — still contains most of the collections being deleted. Since the local row for each is already gone (local delete happens first and is fast), `pullCollectionsFromFirebase` reads that as "local doesn't have this, must be new" and reinserts it — undoing the delete that was already in progress. With many concurrent deletes, this fires repeatedly; only whichever collection's own removal happens to be the last thing to settle survives.

### 🛠️ Final Fix
Added `pendingCollectionDeletes`/`pendingLinkDeletes` (`ConcurrentHashMap.newKeySet()`) to `FirebaseSyncRepository`: `deleteCollectionRemote`/`deleteLinkRemote` register the id/key before starting the Firebase removal and unregister it once the removal settles (success or failure). `pullCollectionsFromFirebase`/`pullLinksFromFirebase` skip reinserting anything currently in these sets, closing the race window. This is the same class of bug as #27 (a pull racing ahead of an in-flight local-first mutation) but on the delete side rather than the create side.

**Follow-up (still #30):** the first version of this fix removed the id from the pending set the instant `removeValue()`'s own `Task` resolved. Retested live with a real batch delete (all 19 duplicates): they all vanished, then **all** came back about a second later — worse than the original partial-resurrection symptom, because the guard window was too short. `removeValue()` completing doesn't guarantee every listener echo of the pre-delete state has already been delivered; a stale `onDataChange` can land slightly after. This is the exact same straggler-event problem the pre-existing `delay(1000)` before decrementing `syncIgnoreCount` (elsewhere in this file) already guards against. Applied the same pattern here: hold each id/key in the pending set for `delay(2000)` after the removal settles, instead of clearing it immediately.

---

## 🐞 31. Collection Detail Didn't Refresh After Saving From Add Items
**Status:** ✅ Resolved

### 📝 Problem
After confirming picks in the new Add Items save flow, the collection detail screen still showed the old item list — only leaving and re-entering the screen showed the change.

### 🔍 Root Cause
Exactly the risk flagged (but never actually hit until now) in [TECHNICAL_AUDIT.md #6](TECHNICAL_AUDIT.md): `CollectionDetailViewModel.observeItems()` read `collectionRepository.getItemsForCollection(id)` with `.first()` *inside* a `combine()` lambda instead of as one of the combined flows. A `.first()` call takes a one-time snapshot at the moment the surrounding `combine` block happens to run — it does not itself trigger re-runs when the flow it reads from changes. Since none of the *other* flows being combined (`_collectionId`, all items, all links, expanded ids) change when a collection's items are added/removed, the block simply never re-ran after a save. Leaving and re-entering worked only because it recreates the ViewModel, forcing a fresh read from scratch.

### 🛠️ Final Fix
`observeItems()` now subscribes to `getItemsForCollection(id)` as its own live flow via `_collectionId.filterNotNull().flatMapLatest { ... }` and includes it as a 5th flow in the `combine()` call, so the block correctly re-runs whenever the collection's items actually change — matching the pattern already used correctly by `collectionWithItems` and `readiness` elsewhere in the same class.

---

## 🐞 32. Mass Delete Still Partially Resurrected — A Second, Different Race
**Status:** ✅ Resolved

### 📝 Problem
Asked to go test collections again. Live on-device: 15 duplicate "test things" selected and deleted, deliberately keeping one real collection unselected. Only 4 of the 15 were actually gone afterward — 11 came back, stable (not still churning) after several seconds' wait.

### 🔍 Root Cause
A completely different race from #30/#32's pull-side one. `TaskTimerService.startSyncLoop()` calls `FirebaseSyncRepository.triggerFullSync()` every 30 seconds *while any task is actively running* (and one had been running throughout this whole testing session) — this reads `collectionDao.getAllCollectionsList()`/`itemLinkDao.getAllLinksList()` (every row currently in the local table, not just dirty ones) and pushes all of it to Firebase via `updateChildren`. If that read happens a moment before a concurrent `deleteCollectionRemote`/`deleteLinkRemote` call's local delete removes the row, the periodic sync faithfully re-uploads the row to Firebase — actively re-creating the exact node the delete was trying to remove, independent of and unprotected by the `pendingCollectionDeletes`/`pendingLinkDeletes` guard from #30, since that guard was only ever checked on the *pull* (reinsert) side, never on any *push* path.

### 🛠️ Final Fix
`pushCollectionsToFirebase`/`pushLinksToFirebase` now also filter out anything currently in `pendingCollectionDeletes`/`pendingLinkDeletes` before building the Firebase update, so a stale read from any push path (the reactive dirty-flow push or the periodic full sync) can no longer re-upload a collection/link that's mid-delete. Item/task deletes were never at risk here since they're soft-deletes (the row stays present locally with `isDeleted=true`, so even a stale full push just correctly re-affirms that tombstone state rather than resurrecting anything).

**Follow-up (superseded by #33):** even after this fix, testing kept turning up more resurrected duplicates with *no delete in progress at all*. Traced to a second physical device (the user's phone, also synced to the same account) that still had its own local copy of the duplicate collections from before any of these fixes existed — its own periodic sync doesn't know or care what the tablet just deleted, so it kept re-uploading its own stale local rows regardless of any in-memory guard on the tablet. That's a structural limitation of a guard that only lives in one device's process memory, not a bug in the guard itself — see #33 for the real fix.

---

## 🐞 33. The Real Fix: Deletes Needed Tombstones, Not Guards
**Status:** ✅ Resolved

### 📝 Problem
User: "take note of this syncing issue and how better to make sure it stops happening... we want perfect syncing." After #27 → #29 → #30/#32, each fix closed one specific race but the underlying design — hard-deleting collections/links locally, then trying to also tell Firebase about it via a special-cased side-effecting call — kept producing a new race, because *any* device with its own independent local copy of the row could undo the deletion by simply existing and syncing normally. Confirmed live: the user's phone (a second physical device, `SM-A226B`, also signed into the same account) still had its own local copy of the duplicate collections and kept them alive in Firebase indefinitely, completely unaffected by anything running on the tablet.

### 🔍 Root Cause
`InventoryItem` and `Task` never had this problem because they delete via soft-delete (`isDeleted = 1, isDirty = 1` on the existing row) — the deletion *is* synced data, riding the same "isDirty incremental merge" push every other edit uses. Any device, no matter how stale its local copy, converges to the correct state the next time it syncs, because the tombstone itself is what's being merged (last-write-wins on `updatedAt`, same as any other field). `InventoryCollection`, `InventoryCollectionItem`, and `ItemLink` never got this treatment — they used hard `@Delete`/`DELETE` queries, so there was no synced representation of "this was deleted" at all, only an out-of-band `removeValue()` call that only the deleting device knew to make, and only once.

### 🛠️ Final Fix
Converted all three entities to the same soft-delete pattern Item/Task already used correctly:
- Added `isDeleted: Boolean = false` to `InventoryCollection`, `InventoryCollectionItem`, `ItemLink`.
- `CollectionDao.deleteCollection`/`removeItemFromCollection` and `ItemLinkDao.removeLink` are now `UPDATE ... SET isDeleted = 1, isDirty = 1` instead of `@Delete`/`DELETE`.
- Every UI- and business-logic-facing query (`getAllCollections`, `getCollectionsWithCounts`, `getItemsForCollection`, `getAllLinksFlow`, `getLinksForItemFlow`, etc.) now filters `isDeleted = 0`. Room's `@Relation`/`@Junction` (used by `getCollectionWithItems`/`getCollectionReadiness`) can't filter the junction table declaratively, so that one's filtered in Kotlin after the query returns instead.
- Sync's own full-table pushes need the *unfiltered* view (tombstones included, or they'd never reach Firebase) — added `getAllCollectionsForSyncList()`/`getAllCollectionItemsForSyncList()`/`getAllLinksForSyncList()` for that, used only by `FirebaseSyncRepository.triggerFullSync()`.
- Removed the entire `pendingCollectionDeletes`/`pendingLinkDeletes` guard system and `deleteCollectionRemote`/`deleteLinkRemote` from `FirebaseSyncRepository` — not needed anymore, since deletion is no longer a special side-effecting call racing against reads, just a normal write that converges like every other field.
- Added `purgeOldDeletedCollections`/`purgeOldDeletedCollectionItems`/`purgeOldDeletedLinks` (24h retention) so tombstones eventually get cleaned up, matching `Task.purgeOldDeletedTasks`'s existing pattern; wired into periodic cleanup loops in `CollectionsViewModel` and `InventoryListViewModel`, mirroring `TaskTrackerViewModel.startPeriodicCleanup()`.
- Room DB bumped to version 8 (new `isDeleted` columns), relies on the existing `fallbackToDestructiveMigration()`.

This closes the entire chain of collection/link duplication and resurrection bugs (#26 through #32) at the architectural root rather than patching each new race as it was discovered.

---

## 🐞 34. Stopping a Paused Interruption Left Its Own Sub-Interruption Running and Its Parent Paused
**Status:** ✅ Resolved

### 📝 Problem
Reported repro: start "Cooking", interrupt with "Interruption 1", interrupt *that* with "Interruption 2", then stop Interruption 1. Expected: Cooking resumes and Interruption 2 also stops. Actual: Cooking stayed paused and Interruption 2 kept running.

### 🔍 Root Cause
Two compounding gaps in `TaskTrackerViewModel.stopTask()`:
1. It read the session-to-resume as `session.activeSegment?.task?.interruptedGroupId` — but the session being stopped (Interruption 1) had no `activeSegment`, since it was itself paused by Interruption 2 sitting on top of it. The lookup silently evaluated to `null`, skipping the "resume parent" step entirely.
2. There was no logic at all to cascade-stop whatever was actively interrupting the session being stopped, so Interruption 2 was simply left running with nothing left to eventually return to.

### 🛠️ Final Fix
`interruptedGroupId` is now read from `session.activeSegment?.task ?: session.segments.firstOrNull()`, so it's found regardless of whether the session being stopped is currently running or already paused. Added `stopActiveInterruptionChain(groupId, now)`, called before the target session is stopped, which recursively stops whatever is interrupting it (deepest first) before it resumes its own parent. See #35 — the first version of this helper only checked *currently-running* segments, which turned out to be too narrow for the resume path.

---

## 🐞 35. Resuming a Task Directly Didn't Collapse a Multi-Level Interruption Chain On Top Of It
**Status:** ✅ Resolved

### 📝 Problem
Follow-up to #34, reported immediately after verifying it: start a task, interrupt it with Interruption 1, interrupt *that* with Interruption 2, then resume the original task directly (not by stopping an interruption). Expected the whole chain to collapse — both interruptions stop, the original task resumes. Instead only the original task resumed; both interruptions were left dangling.

### 🔍 Root Cause
`pauseResumeTask()`'s RESUMING branch used `findActiveInterruptionFor()`, which only matches a session with a currently **running** `activeSegment`. Interruption 1 was paused (because Interruption 2 was running on top of it), so it was invisible to that lookup — the search for "what's interrupting the task I'm resuming" came back empty, and neither interruption got stopped. This is the same category of gap as #34, one level further down the chain: the fix in #34 also relied on this same active-segment-only lookup (`findActiveInterruptionFor` inside `stopActiveInterruptionChain`), so it only ever worked when the *immediate* interrupter happened to be running — not when the chain was more than one level deep with an intermediate pause.

### 🛠️ Final Fix
Replaced the active-segment-only lookup with `findInterruptionSessionFor(groupId)`, which finds the session interrupting `groupId` by checking `activeSegment?.task ?: segments.firstOrNull()` — so it matches regardless of whether that interrupting session currently has a running segment or is itself paused by a further interruption. `stopActiveInterruptionChain` was generalized to walk sessions (not just active `RunningTaskUI`s) via this helper, so it now correctly collapses a chain of any depth in one pass. Reused from both `stopTask()` (#34) and `pauseResumeTask()`'s resume branch, so both entry points share one correct implementation instead of two divergent ones.

---
*Last Updated: 2026-08-09*
