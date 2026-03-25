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

### 7. Import Repetition in TaskTimerService
`TaskTimerService.kt` contains `import java.util.*` repeated five times in succession at the top of the file.
- **Origin**: This is a classic artifact of APK decompilation, indicating that this specific file was reconstructed from bytecode and not fully cleaned up.

### 8. Package Name Mismatch in Tests
The instrumented test in `ExampleInstrumentedTest.kt` asserts that the package name is `com.example.inventoria_kotlin`.
- **Status**: The actual project package is `com.inventoria.app`. This test will always fail until updated to match the current project structure.

---

## ✅ Resolved & Implemented Features

### 9. Custom Username on Splash
- **Implemented**: `SplashScreenContent` now reads `customUsername` from `SettingsRepository`.
- **Logic**: Greets the user with "Welcome back, [Name]" if a custom name or Google display name is available.

### 10. Automatic Splash Navigation
- **Implemented**: The splash screen now automatically navigates to the main screen if any account (Google or Local Anonymous) exists, eliminating the need for returning users to press a button.

### 11. Local Account Management
- **Implemented**: `SettingsScreen` now allows local account users to set a custom display name, which is persisted and shown on the splash screen.

## 🔄 Sync Architecture & Incremental Merging Strategy (New)

### 12. The "isDirty" Incremental Merge Pattern
- **Status**: ✅ Implemented (Replaces Destructive "Overwrite All" Strategy)
- **Background**: The app previously suffered from a "Simultaneous Online" bug. Because Room `Flow`s emit immediately upon connection, opening the app would trigger an instant push of the *entire* local database to Firebase via `setValue()`, destroying any remote changes made by other devices before they could be pulled.
- **Solution (`isDirty` flag)**: 
    - **Models**: All syncable models (`InventoryItem`, `Task`, `InventoryCollection`, `InventoryCollectionItem`, `ItemLink`) now possess an `isDirty: Boolean = false` property. This property is annotated with `@get:Exclude` and `@set:Exclude` so it is strictly local to the device and never sent to Firebase.
    - **Repositories**: Whenever a user modifies data locally (insert, update, equip, pack, etc.), the Repositories (`InventoryRepository`, `TaskRepository`, `CollectionRepository`) explicitly create a copy of the item with `.copy(isDirty = true)`.
    - **FirebaseSyncRepository**: The sync engine now *only* listens to flows of dirty items (e.g., `inventoryDao.getDirtyItemsFlow()`).
    - **Merge vs Overwrite**: Pushes now use `ref.updateChildren(updates)` rather than `ref.setValue()`. This ensures the app only merges its specific local modifications into the cloud, leaving other concurrent edits untouched. Upon a successful push, the items are marked clean (`isDirty = 0`) via the DAOs.
    - **Pull Logic**: When pulling remote data down to the device, the records are inserted with the default `isDirty = false` state, preventing infinite sync loops.

---
*Audit Conducted: 2026-03-25*
