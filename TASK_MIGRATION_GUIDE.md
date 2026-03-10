# Task System Migration Guide

This guide explains how to migrate your existing task tracking data to the new Nature-Branded Productivity System.

## ⚠️ Important Precautions
1. **Backup Your Data**: Ensure your Firebase data is synced before starting.
2. **Version Sync**: All devices should be updated to the latest version to avoid enum mismatch errors in Firebase.

## 🛠 Step-by-Step Migration

### 1. Update the Data Model
The `TaskKind` enum has been completely redesigned. Replace your existing `Task.kt` with the new version containing `TaskCategory` and the 11 new `TaskKind` values (Graphite, Grape, Tomato, etc.).

### 2. Database Version Bump
Increment the version in `InventoryDatabase.kt` to the latest version (e.g., from `7` to `8` or higher).

### 3. Add Migration Logic (Optional)
If you wish to preserve local data without a full reset, add a migration in `DatabaseModule.kt`:

```kotlin
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(database: SupportSQLiteDatabase) {
        // Migration logic for enum changes if stored as strings
        // Mapping example:
        // 'BIG_PRODUCTIVE' -> 'PEACOCK'
        // 'SMALL_PRODUCTIVE' -> 'LAVENDER'
        // 'NEUTRAL_WAITING' -> 'GRAPHITE'
        // 'FREE_TIME' -> 'GRAPE'
        // 'SMALL_WASTE' -> 'TANGERINE'
        // 'BIG_WASTE' -> 'TOMATO'
    }
}
```

*Note: The current implementation uses `fallbackToDestructiveMigration()`, which is safer for major enum renames. Local tasks will be cleared, but synced tasks will reappear as they are updated.*

### 4. UI Component Updates
The task system now uses a **Segmented Session** architecture. 
- Individual tasks are grouped into sessions via `groupId`.
- Single tasks are displayed as simple cards, while multi-segment sessions are displayed as expandable cards.
- Percentage logic now correctly calculates duration breakdowns across multiple days (e.g., "0.4% of Today - 5.3% of 25 Feb").

### 5. Scoring & Categories
The system now automatically calculates scores based on task categories:
- **Productivity Tasks**: Positive scoring (e.g., Peacock +3, Lavender +2).
- **Social Tasks**: Positive and negative scoring (e.g., Sage +2, Banana -2).
- **Default Tasks**: Neutral scoring (0).

---

## 🗺 Mapping Reference for Old Data
If you see old data in Firebase, here is how it maps to the new system:

| Old Value | New Value | Reason |
|-----------|-----------|--------|
| BIG_PRODUCTIVE | PEACOCK | High impact work |
| SMALL_PRODUCTIVE | LAVENDER | Growth/Skill building |
| NEUTRAL_WAITING | GRAPHITE | Neutral transitions |
| FREE_TIME | GRAPE | Relaxation |
| SMALL_WASTE | TANGERINE | Minor distractions |
| BIG_WASTE | TOMATO | Major time drains |

## 🆕 New Features Documentation

### Session View
Sessions that span midnight or cover multiple calendar days will now show a detailed percentage breakdown per day in the format: `X.X% of [Day] - Y.Y% of [Previous Day]`.

### Flattening Sessions
You can now "Flatten" a session in the details dialog. This merges all segments into a single continuous task block, removing gaps between segments. Note: This action is irreversible.

### Automatic Cleanup
Tasks saved to the calendar are automatically soft-deleted from the local database after 24 hours to keep the UI clean while preserving the data in your Google Calendar.
