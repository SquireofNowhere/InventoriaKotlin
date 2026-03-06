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
- **Atomic Node Updates:** Switched to `ref.updateChildren()` in `FirebaseSyncRepository.kt` to update only modified tasks rather than overwriting the whole collection.
- **Version Seeding:** Updated `TaskRepository.kt` to seed its internal clock from the highest timestamp in the database, ensuring strictly monotonic versioning regardless of system clock skew.

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
*Last Updated: 2024-05-21*
