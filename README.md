# Inventoria (Kotlin)

Modern Inventory & Task Tracking Management for Android. Built with Jetpack Compose, Room, and Firebase.

## 🌟 Comprehensive Feature List

### 📦 Inventory Management
*   **Deep Item Tracking**: Manage items with names, quantities, text locations, and GPS coordinates.
*   **Hierarchical Containers**: Organize items within other items (e.g., a "Toolbox" containing "Wrenches").
*   **Equipment System**: 
    *   **Equip/Unequip**: Mark items as "on person" which removes them from their container.
    *   **Smart Repack**: Automatically remembers the last container an item was in for easy return.
*   **Bulk Operations**: 
    *   **Selection Mode**: Long-press to select multiple items.
    *   **Smart Merge**: Combine multiple similar entries into one, summing quantities and merging metadata.
    *   **Bulk Delete**: Efficiently remove multiple items at once.
*   **Multi-Media Support**:
    *   **Image Gallery**: Attach multiple photos to any item.
    *   **Background Sync**: Photos upload to Firebase Storage in the background to keep the UI snappy.
    *   **Thumbnail Selection**: Choose a primary profile picture from the gallery.
*   **Barcoding**: Track items via SKU or Barcode for quick identification.
*   **Financials**: Track unit prices and view total inventory value on the dashboard.

### ⏱️ Task & Productivity Tracking
*   **Session-Based Tracking**: Group related tasks into sessions for better organization.
*   **Productivity Gamification**:
    *   **Task Kinds**: Assign "Kinds" (Graphite, Lavender, Peacock, etc.) which represent different productivity levels and categories (Personal, Social, Neutral).
    *   **Momentum Scoring**: A session's points are `kind's productivity value × session length in minutes × momentum multiplier`, frozen the moment the segment finishes so historical totals don't drift if the formula is tuned later. The multiplier compounds with a streak of consecutive same-kind completed sessions (10%/session for productive kinds, 15%/session for draining ones — a steeper escape rate — capped at 2.5x), and resets whenever a different kind is completed. See [TECHNICAL_AUDIT.md](TECHNICAL_AUDIT.md#13-momentum-based-scoring--interruption-tracking) for the full mechanism.
*   **Interruption Tracking (Inner Tasks)**: Pausing a task can start a linked "inner task" (e.g. pausing "Coding" to get water starts "Get Water") that tracks the interruption's own time, auto-stopped the moment you resume the original — no manual stop/start bookkeeping. Starts immediately (with a live timer) rather than waiting on a name; a one-time popup explains the feature the first time you pause, and it's a toggle in Settings after that. Off by default, interruptions don't break an existing momentum streak — opt an individual interruption in via a toggle on its popup or its session card if you want it to count.
*   **Active Monitoring**: 
    *   **Foreground Service**: Keep timers running accurately even when the app is in the background.
    *   **Live Editing**: Instant saving of task names and notes during active sessions.
*   **Productivity Dashboard**: Visualize your productivity trends and task history.
*   **Calendar Integration**: Sync tasks with the system calendar; identify "Inventoria" tasks via smart description tags.
*   **Segmented Sessions**: Sessions spanning multiple calendar days show a per-day percentage breakdown (e.g., "0.4% of Today - 5.3% of 25 Feb"). Multi-segment sessions can be "Flattened" in the details dialog to merge all segments into one continuous block (irreversible).
*   **Automatic Cleanup**: Tasks saved to the calendar are soft-deleted from the local database after 24 hours, keeping the UI clean while preserving the data in Google Calendar.

### 🎒 Collections & Readiness
*   **Project-Based Collections**: Create custom sets of items (e.g., "Emergency Kit", "Photography Gear").
*   **Readiness Checklist**: 
    *   Real-time status tracking: Available, Packed, or Equipped.
    *   Percentage-based readiness indicators for each collection.
*   **Collection Presets**: Specific types like TRAVEL_KIT, OUTFIT, and WORK_GEAR.
*   **Multi-Select Delete**: Long-press (or tap in selection mode) to select collections on the Collections screen, with a Select All toggle and bulk delete.
*   **Add Items Picker**: Tapping items in a collection's "Add Items" picker only stages the change locally (checkmark updates instantly, nothing is written) until confirmed with the checkmark in the top bar. Leaving with unsaved picks prompts Save / Cancel / Delete (discard).

### 🗺️ Map & Location Features
*   **OSMDroid Integration**: Interactive map for picking and viewing item locations.
*   **Resolved Locations**: Smart logic to inherit location data from parent containers if not explicitly set.
*   **User Proximity**: Track your current location relative to your stored items.

### ☁️ Sync & Security
*   **Real-time Cloud Sync**: Firebase Realtime Database ensures data is identical across all your devices.
*   **Incremental Merging**: Only locally-changed (dirty) records are pushed, so simultaneous device usage doesn't overwrite concurrent remote edits. See [TECHNICAL_AUDIT.md](TECHNICAL_AUDIT.md#11-the-isdirty-incremental-merge-pattern) for the full mechanism.
*   **Conflict Resolution**: High-precision timestamping (`updatedAt`) handles offline edits and ensures the latest version prevails.
*   **Google Authentication**: Securely sign in and back up your data to the cloud.
*   **Collaborative Syncing (Invite Codes)**: Generate an invite code to let another account read and write to your database, or paste one to sync with someone else's — useful for shared households/inventories.
    *   **Local / Google / External-Sync are mutually exclusive states**: you can't be signed into Google and connected to someone else's database at the same time — each path is blocked in the UI with an explanation of which state to clear first, and Settings shows a single always-accurate status banner (with the actual UID involved, masked by default with a reveal toggle) instead of scattered, potentially-contradictory indicators.
    *   **Connected Devices list**: Settings shows every account currently synced to *your* database (read from `sharedWith`), each with a Revoke button — previously this was invisible even though the data existed.
*   **Soft Deletion**: All data (items, tasks, collections, collection items, and item links) is soft-deleted first, so a deletion is itself synced data that converges correctly on every device rather than a one-off action only the deleting device knows about.
*   **Live Sync Status**: A small pill in the top-right corner of every screen shows sync state at a glance (syncing / synced / error) — powered by a component that existed for a while but was never actually placed anywhere.

### ⚙️ Customization & Localization
*   **Flexible Currency**: Automatically detects local currency or allows manual override (USD, EUR, GBP, etc.).
*   **Custom Fields**: Add arbitrary key-value metadata to any inventory item.
*   **Modern Material 3 UI**: Clean, responsive interface with Dark Mode support and dynamic theming.

## 🚀 Upcoming Features (TODO)
*   **Productivity Pie Chart**: Add a circular visualization to the daily productivity card in the Tasks screen. This chart should outline the full 24 hours of the day and visually represent how time was spent across different task kinds.

## 🛠️ Tech Stack
- **UI**: Jetpack Compose (Material 3)
- **Database**: Room (Local), Firebase Realtime Database (Cloud Sync)
- **Storage**: Firebase Storage (Images)
- **DI**: Hilt (Dependency Injection)
- **Asynchronous**: Kotlin Coroutines & Flow
- **Maps**: OSMDroid (Location picking)
- **Architecture**: MVVM with Repository Pattern

## 🗑️ Data Management & Account Deletion

To permanently wipe all your data and start fresh, the app supports complete account deletion.

### Automated Deletion (In-App)
The "Delete Account" button in Settings calls `deleteUserAccount()`, which automatically:
1. Delete your entire user branch (`users/{uid}`) from the **Firebase Realtime Database**.
2. Delete all your uploaded images (`users/{uid}/item_images`) from **Firebase Storage**.
3. Delete your **Firebase Authentication** record.
4. Log you out of the Google Client.

### Manual Deletion (Fallback / Hard Reset)
To force the app to start a new database manually:
1. **Clear Local App Data:**
   - Go to your device's **Settings > Apps > Inventoria > Storage & cache** and tap **Clear storage**.
2. **Delete Cloud Data (Firebase Console):**
   - Open your project in the [Firebase Console](https://console.firebase.google.com/).
   - Navigate to **Realtime Database**, find the `users` node, and delete your specific user ID (UID) node.
3. **Delete Cloud Images:**
   - Navigate to **Storage** and delete the folder corresponding to your UID.
4. **Restart Fresh:**
   - Open the app again and sign in. A new, empty database will be initialized.

**Warning for local (anonymous) accounts**: unlike a Google account, a local account has no external credential to recover with. Clearing local app data (or reinstalling) permanently orphans that identity and everything under it — there is no password, email, or recovery flow, by design of Firebase Anonymous Authentication. If you're on a local account and want to survive a data wipe, sign in with Google first.
