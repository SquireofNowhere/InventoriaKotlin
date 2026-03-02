# Task System Migration Guide

This guide explains how to migrate your existing task tracking data to the new Nature-Branded Productivity System.

## ⚠️ Important Precautions
1. **Backup Your Data**: Ensure your Firebase data is synced before starting.
2. **Version Sync**: All devices should be updated to the latest version to avoid enum mismatch errors in Firebase.

## 🛠 Step-by-Step Migration

### 1. Update the Data Model
The `TaskKind` enum has been completely redesigned. Replace your existing `Task.kt` with the new version containing `TaskCategory` and the 11 new `TaskKind` values (Graphite, Grape, Tomato, etc.).

### 2. Database Version Bump
Increment the version in `InventoryDatabase.kt` from `7` to `8`.

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
Replace standard dropdowns with `TaskKindDropdownMenu` to give users the grouped categories and descriptions.

### 5. Verify Scoring
Ensure the `ProductivityScoreCard` is visible on the Task screen to show the new calculated scores.

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
