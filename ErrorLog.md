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
*Last Updated: 2026-03-23*
