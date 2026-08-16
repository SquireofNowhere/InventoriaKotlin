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
*   **Financials**: Track unit prices and view total inventory value on the dashboard.
*   **Sorting, Grouping & Filtering**: Sort by name, recency, quantity or price; cycle grouping through None → Category → Location → Collection with a single top-bar button; and filter from a bottom sheet whose semantics are adjustable — "Hard Filter" requires an item to match every selected tag rather than any, and "Invert Filter" turns the selection into an exclusion. Active sort/group/filter state appears as chips under the search box, and tapping a chip clears that one setting.
*   **Item Linking**: Drag one item onto a non-container item to link them as leader/follower. A follower with no location of its own inherits its leader's, and the relationship is shown on both items as "Follows this item" / "Leads this item".

### ⏱️ Task & Productivity Tracking
*   **Session-Based Tracking**: Group related tasks into sessions for better organization.
*   **Productivity Gamification**:
    *   **Task Kinds**: Assign "Kinds" (Graphite, Lavender, Peacock, etc.) which represent different productivity levels and categories (Personal, Social, Neutral).
    *   **Momentum Scoring**: A session's points are `kind's productivity value × session length in minutes × momentum multiplier`, frozen the moment the segment finishes so historical totals don't drift if the formula is tuned later. The multiplier compounds with a streak of consecutive same-kind completed sessions (10%/session for productive kinds, 15%/session for draining ones — a steeper escape rate — capped at 2.5x), and resets whenever a different kind is completed. Today's Personal/Social score cards squash each category's *combined* time-tracked total through a diminishing curve toward ±5 points, so no single long session (or pile of short ones) can dominate the day the way an unbounded sum could — raw effort differences between sessions still show up, they're just compressed under a low ceiling. Completed **Todo Tasks** (see below) bypass this entirely and add their full, undamped value on top. See [TECHNICAL_AUDIT.md](TECHNICAL_AUDIT.md#13-momentum-based-scoring--interruption-tracking) for the full mechanism.
*   **Todo Tasks**: A separate "Todos" tab for real to-do items, distinct from time-tracked sessions. Each todo has an optional deadline — a date, plus an optional time of day that shows as "Due HH:MM" on the row and turns bold red once that minute passes on the day it's due. The date is kept separate from the time internally so day-grouping and the overdue penalty keep working in whole days. An incomplete todo whose deadline has passed automatically carries over into Today's list (same item, no duplication) with an escalating "Overdue by N days" penalty subtracted from that day's score, capped at 5 points/day. Each day section shows an "X% Done" progress bar for whatever was actually due that day. A Start button on any todo kicks off a real tracked session seeded with its title and Kind; when that session stops, a "Complete or still ongoing?" prompt ties the result back to the todo.
    *   **Hierarchy**: Unlimited-depth parent/child nesting (GitHub-Projects-style sub-todos with indentation and a "X/Y sub-todos complete" readout on the parent), settable either from the add/edit dialog's parent picker or by dragging one todo onto another. A deadline-less child is grouped under wherever its nearest dated ancestor lands (including following it into Today if that ancestor is overdue) rather than falling into the generic "No Deadline" section.
    *   **Tri-State Completion**: Beyond just Incomplete/Complete, a todo can be "In Progress" — completing a parent cascades every currently-incomplete descendant to In Progress (never straight to Complete, since they weren't individually verified); un-completing a parent reverts whichever descendants are In Progress back to Incomplete. A parent with a genuine mix of complete/incomplete direct children displays as In Progress automatically. Rendered with Material 3's `TriStateCheckbox`.
    *   **Priority (A1–C3)**: Each todo can be given a Franklin-Covey-style priority — a letter tier (A/B/C) nested with a number sub-rank (1–3), one ordered scale from A1 (highest) to C3 (lowest), shown as a small badge on the row when set. Optional **Procrastination Penalty** (off by default, configurable in Settings) docks a flat, configurable number of points whenever a todo is completed at or below a chosen cutoff tier (or with no priority set at all), and separately whenever a time-tracked task of a Settings-flagged Kind is completed — the two triggers are independent since a spontaneous tracked session has nothing to prioritize in advance. Both penalties are derived live from today's already-completed items each time the score recomputes, the same way the overdue-todo penalty already works, rather than being frozen onto the item at completion time — so changing the settings later never leaves past completions looking stale.
*   **Interruption Tracking (Inner Tasks)**: Pausing a task can start a linked "inner task" (e.g. pausing "Coding" to get water starts "Get Water") that tracks the interruption's own time, auto-stopped the moment you resume the original — no manual stop/start bookkeeping. Starts immediately (with a live timer) rather than waiting on a name; a one-time popup explains the feature the first time you pause, and it's a toggle in Settings after that. Off by default, interruptions don't break an existing momentum streak — opt an individual interruption in via a toggle on its popup or its session card if you want it to count. The naming popup has the same autocomplete-from-past-task-names and Kind (category) picker as a regular task, and picking a suggestion carries over that task's Kind too. An interruption can itself be interrupted (a chain), and Active Sessions renders that as an indented hierarchy — each nested card shows which task it's interrupting — instead of a flat list of unrelated-looking cards.
*   **Active Monitoring**: 
    *   **Foreground Service**: Keep timers running accurately even when the app is in the background.
    *   **Live Editing**: Instant saving of task names and notes during active sessions.
*   **Task Types**: A user-managed activity label sitting one level above a task's name — the middle tier between free text and a Kind. "Eating with V" (home-made, Blueberry +1) and "Eating at a restaurant" (takeout, Tangerine −1) are the same *type* with different names and different values. Managed in Settings → Task Types (add, rename, delete; deleting keeps the tasks and only drops the label). Tasks reference types by id, so a rename propagates through all history. Todos carry a type too, and it's stamped onto the session a todo starts.
*   **Learned Autofill**: Typing a task name offers Task Types first, then names you've used before. A name suggestion carries its wording, its *most commonly used* Kind and the type that name has settled on — the mode across that name's whole history rather than whatever you last stopped it as, so one retag can't redefine what a name means. Names recorded before Task Types existed keep suggesting no type until enough newly-typed instances outvote the untyped ones.
*   **Activity Grouping**: Completed sessions sharing a name and a type are shown as one card reading "N sittings", on both Recent Sessions and Task History. The grouping happens when the list is drawn — each sitting stays its own session underneath, so streaks and totals still count them individually. Kind is deliberately *not* part of the identity, so lunch in and lunch out stay one activity. Anything that would reach across sittings — deleting the card, or renaming/retagging from a detail dialog — first asks "change all N / just this one / cancel".
*   **Flow Mode**: With it on, stopping a task immediately starts a fresh one (the Stop button becomes "Stop & Continue", with a one-second countdown banner you can pre-empt), so a day of back-to-back work has no untracked gaps.
*   **Timers & Alarms**: An alarm icon on the Tasks screen opens a surface that drives the device clock app — preset and custom countdown timers labelled with whatever task is running, alarms for todos due today or tomorrow that carry a time, and the next alarm set on the device. Android exposes no read access to alarms or timers and no way to cancel another app's, so editing and stopping hand off to the clock app; the screen says so rather than implying otherwise.
*   **Productivity Stats**: Four tabs — Impact (lifetime totals per Kind, tap for that Kind's task list), Ledger (every scored task with a running balance), By Type (average points per Task Type), and Today, which draws the dampening curve by sampling the real function with your own totals marked on it and lists every term of the day's arithmetic: raw tracked, dampened, what the curve absorbed, todo contributions and each penalty.
*   **24-Hour Pie Chart**: The daily productivity card shows a circular breakdown of today's tasks by kind, mapped onto their actual time-of-day position around the full 24-hour clock face, with the center showing what percentage of today has been tracked so far. The full "Daily Overview" dialog adds a color legend so each kind on the ring is identifiable at a glance.
*   **Calendar Integration**: Sync tasks with the system calendar; identify "Inventoria" tasks via smart description tags.
*   **Task History as a Day Tracker**: The Task History screen is organized into day sections (Today, Yesterday, then by date) like a calendar day view — each day header shows the total time tracked plus a mini 24-hour timeline bar with a colored segment per task, positioned and sized by when it actually happened. Individual task rows show their clock time in a left-hand gutter. A toggle in the top bar switches between the session-grouped view and a flat list of every individual completed segment ordered purely by start time; both are broken down by day the same way. Persisted across app restarts.
*   **Segmented Sessions**: Sessions spanning multiple calendar days show a per-day percentage breakdown (e.g., "0.4% of Today - 5.3% of 25 Feb"). Multi-segment sessions can be "Flattened" in the details dialog to merge all segments into one continuous block (irreversible), or a single segment can be "Split" at a chosen point in time into two — e.g. you fall asleep while "Coding" is still running, so you split it into "Coding" (start → the time you passed out) and a brand new, independent task picking up from there. The new task's name auto-fills the same way a fresh task would (e.g. "Task 7").
*   **Calendar Hand-Off**: Once a segment is saved to the calendar, the calendar's own copy of it takes over the UI — `TaskTrackerViewModel.observeTasks()` reads back Inventoria-tagged events every 30s and drops any local row whose id matches one of them (unless it's still running), so the entry is shown from the calendar rather than duplicated. The task detail dialog shows an "Auto-delete in…" countdown off `savedToCalendarAt`, but **nothing currently acts on it** — no code soft-deletes calendar-saved tasks, and `purgeOldDeletedTasks` only hard-deletes rows already flagged `isDeleted = 1`. The local row therefore stays in Room indefinitely, just hidden behind the calendar copy.

### 🎒 Collections & Readiness
*   **Project-Based Collections**: Create custom sets of items (e.g., "Emergency Kit", "Photography Gear").
*   **Readiness Checklist**: 
    *   Real-time status tracking: Available, Packed, or Equipped.
    *   Percentage-based readiness indicators for each collection.
*   **Collection Presets**: Labels like TRAVEL_KIT, OUTFIT and WORK_GEAR, chosen when creating a collection. Purely descriptive — no behaviour is attached to the choice.
*   **Multi-Select Delete**: Long-press (or tap in selection mode) to select collections on the Collections screen, with a Select All toggle and bulk delete.
*   **Add Items Picker**: Tapping items in a collection's "Add Items" picker only stages the change locally (checkmark updates instantly, nothing is written) until confirmed with the checkmark in the top bar. Leaving with unsaved picks prompts Save / Cancel / Delete (discard).

### 🗺️ Map & Location Features
*   **OSMDroid Integration**: Interactive map for picking and viewing item locations.
*   **Resolved Locations**: Smart logic to inherit location data from parent containers if not explicitly set — falling back through the item's own coordinates, then its container's, then a linked leader's, and finally "Equipped (On Person)".

### ☁️ Sync & Security
*   **Real-time Cloud Sync**: Firebase Realtime Database ensures data is identical across all your devices.
*   **Incremental Merging**: Only locally-changed (dirty) records are pushed, so simultaneous device usage doesn't overwrite concurrent remote edits. See [TECHNICAL_AUDIT.md](TECHNICAL_AUDIT.md#11-the-isdirty-incremental-merge-pattern) for the full mechanism.
*   **Conflict Resolution**: High-precision timestamping (`updatedAt`) handles offline edits and ensures the latest version prevails.
*   **Google Authentication**: Securely sign in and back up your data to the cloud.
*   **Collaborative Syncing (Invite Codes)**: Generate an invite code to let another account read and write to your database, or paste one to sync with someone else's — useful for shared households/inventories.
    *   **Local / Google / External-Sync are mutually exclusive states**: you can't be signed into Google and connected to someone else's database at the same time — each path is blocked in the UI with an explanation of which state to clear first, and Settings shows a single always-accurate status banner (with the actual UID involved, masked by default with a reveal toggle) instead of scattered, potentially-contradictory indicators.
    *   **Connected Devices list**: Settings shows every account currently synced to *your* database (read from `sharedWith`), each with a Revoke button — previously this was invisible even though the data existed.
*   **Soft Deletion**: All data (items, tasks, collections, collection items, and item links) is soft-deleted first, so a deletion is itself synced data that converges correctly on every device rather than a one-off action only the deleting device knows about.
*   **Live Sync Status**: A small pill shows sync state at a glance (syncing / synced / error), rendered in the top bar of the **Dashboard** and of the **Map** screen's default (non-location-picking) state — the two screens that own a `CenterAlignedTopAppBar` with a free title slot. It reads `FirebaseSyncRepository.syncStatus` via `SyncStatusViewModel`. Note that the status flow is only driven by item pushes, `triggerFullSync()`, `syncOnAppOpen()` and listener failures — a sync that only pushes tasks, todos, collections, links or task types leaves the pill idle.

### ⚙️ Customization & Localization
*   **Flexible Currency**: Automatically detects local currency or allows manual override (USD, EUR, GBP, etc.).
*   **Custom Fields**: Add arbitrary key-value metadata to any inventory item.
*   **Modern Material 3 UI**: Clean, responsive interface with a Dark Mode toggle. (Wallpaper-based dynamic colour is deliberately switched off so the app's look stays consistent.)
*   **In-App How To**: Settings → Help opens a browsable manual — one category per area of the app, articles with numbered steps, a drawn diagram per step and a closing note on why each feature behaves as it does, plus a search box. Task Tracking is documented in full; the remaining areas are listed but not yet written. Features that exist in name only get explicit "Coming soon" entries rather than instructions for something that doesn't work.

## 🚀 Upcoming Features (TODO)
*   **Interruption Task Grouping Choice**: Let the user choose how interruption ("Inner") tasks are grouped when displayed — e.g. nested under the task they paused, grouped by kind, or shown ungrouped — similar to the existing grouping-choice pattern used for inventory (`GroupOption`).
*   **Barcodes & SKUs**: `InventoryItem` carries `barcode`/`sku` fields and search already matches on them, but no screen offers a place to enter one and there is no scanner.
*   **Write the remaining How To articles**: Task Tracking is complete; the other ten areas are listed in the in-app guide but not yet written.
*   **Wire up or remove three dead affordances**: the "Enable Notifications" toggle persists but nothing reads it; a collection's icon/colour circle is tappable with no picker behind it; and the "Auto-delete in…" countdown on calendar-saved tasks is displayed but nothing acts on it.
*   **Local Data Migration Prompt on Sign-In**: When a user signs into Google while they have local-only (anonymous-account) inventory and tasks, prompt them to migrate that local data up into their new Firebase-synced account instead of silently orphaning it.
*   **Shift App Focus to Task Management**: The app currently opens on the inventory-focused Dashboard (`NavHost`'s `startDestination = Screen.Dashboard.route` in `InventoriaApp.kt`). Re-orient what the user sees first toward task/todo management instead — a navigation/default-screen change, not a change to any underlying inventory or task mechanics.
*   **Repeating Todos**: Let a todo repeat (daily, weekly, or some other interval) instead of being one-off — completing it should set up the next occurrence rather than the todo just being done forever.

## ⚙️ Known Optimization Opportunities
Found during a dead-code/performance audit; documented here rather than fixed immediately so they aren't lost. Ranked by impact.
*   **`TaskDetailDialog`'s live Point Calculation re-queries the DB every second.** While the dialog is open on a running task, the "Point Calculation" section's `LaunchedEffect(liveDuration, task.kind)` re-keys every 1s tick, and `previewScore` → `computeFrozenScore` → `getStreakCountForKind` (`TaskRepository.kt`) does a full unindexed 100-row query + Kotlin-side filter/distinct/take every time — even though the streak/multiplier can't actually change while the dialog just sits there ticking, only the cheap `productivityValue * minutes * multiplier` arithmetic needs to redo each second. Fix direction: split scoring into a suspend "get momentum multiplier for this Kind" step (keyed on `task.kind`, not `liveDuration`) and a pure, non-suspend "compute score from a known multiplier" step Compose can call every tick with no DB hit.
*   **`splitSegment` queries the streak-lookback table twice per split** — once per half, via two independent `previewScore` calls that both fetch the same underlying `getRecentCompletedTasks(100)` list (only the per-kind streak count derived from it differs). One shared fetch could serve both. Lower urgency than the above since a split is a one-off action, not a per-second loop.
*   **`TaskRepository.updateTask` does an unconditional `getTaskById` read before every write** to diff via `hasMeaningfulChanges`, even though nearly every caller already holds the pre-edit `Task` in hand. Fixing this touches most of the method's many call sites, so it's a bigger, riskier change worth a dedicated pass.
*   **Four lifetime score flows + three "today" score flows each independently re-scan the full `allFinishedTasks` list** (`TaskTrackerViewModel.kt`) on every task DB write, each redoing its own `getTodayStart()`/filter/sum or groupBy instead of sharing one upstream pass. Real but low-urgency at personal-app task-history sizes.
*   **`deleteSelectedTasks()` / `saveSelectedTasksToCalendar()`** re-flatten `completedSessions` from scratch on every iteration of their `forEach`, making bulk actions O(selected × total) instead of O(selected) with a single lookup map built up front.
*   **Todo feature, low priority**: `undatedTodoEntries` and `todoSections` (`TodoViewModel.kt`) are two independent `StateFlow.map` chains that each independently recompute child counts/section-day over the full todo list per Room emission — could share one pass.

## 🔧 Building

Two files are required and are deliberately **not** in the repository, since this repo is public
and they identify the Firebase project (see [SECURITY.md](SECURITY.md)):

1. **`.env`** in the repo root — copy `.env.example` and fill in the three values from your Firebase
   console. They are read at build time into `BuildConfig`.
2. **`app/google-services.json`** — download from Firebase Console → Project settings → Your apps.
   The Google Services Gradle plugin fails the build without it.

Then build as normal from Android Studio. `minSdk` 24, `compileSdk`/`targetSdk` 34, JDK 21.

### Database schemas

Room exports a JSON schema per database version to `app/schemas/`. **These are build outputs that
must be committed** — they are the record of the schema at each version, and the only thing a
migration can be checked against. After a build that bumps the database version, commit the new
file alongside the migration.

Changing the version in `InventoryDatabase` without adding a matching `Migration` in
`DatabaseModule` now fails at startup instead of quietly recreating the database. That is
deliberate: see the comments there.

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
The "Delete Account" button in Settings (labelled "Wipe Local Account Data" when you're on a local account) removes the account from both ends:
1. Delete your entire user branch (`users/{uid}`) from the **Firebase Realtime Database**.
2. Delete all your uploaded images (`users/{uid}/item_images`) from **Firebase Storage**.
3. Delete your **Firebase Authentication** record.
4. Log you out of the Google Client.
5. Wipe this device: the whole Room database, every stored preference, and the camera's scratch files.
6. Restart the app at the splash screen, where you pick the account that replaces it.

The button is withheld entirely while an external account is connected via an invite code. Delete
always acts on this device's *own* account, so there it would destroy the database that is not on
screen while leaving the visible one untouched — clear the sync connection first.

Step 5 only runs if the remote steps succeeded. If they failed you're still signed in, and wiping the device would simply hand the next sync an empty database to re-fill from the cloud.

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
