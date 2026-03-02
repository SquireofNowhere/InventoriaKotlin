# 🛠️ Inventoria Error Log & Resolution History

This document tracks significant bugs encountered during development and the technical solutions implemented to resolve them.

---

## 🐞 1. Navigation Backstack & State Restoration Crash
**Status:** ✅ Resolved

### 📝 Problem
The application would crash when navigating from a deep-linked screen (e.g., Dashboard -> Item Detail -> Map) back to a primary tab via the Bottom Navigation bar.

### 🔍 Root Cause
1.  **Tab Hierarchy Conflict:** Sub-screens like `Item Detail` and `Map` weren't logically grouped under their parent tabs (Dashboard/Inventory), causing the UI to lose focus and trigger redundant navigation calls.
2.  **Map State Serialization:** `osmdroid`'s `MapView` contains a complex state that is not easily serializable. When Compose Navigation attempted to `saveState` on a backstack that included a MapView, it triggered a crash or an infinite restoration loop.

### 🛠️ Final Fix
-   **Implemented "Clean-Slate" Navigation:** Modified `InventoriaApp.kt` to explicitly disable `saveState` and `restoreState` when switching primary tabs. 
-   **Stack Clearing:** Used `popUpTo(startDestination) { saveState = false }` to ensure every tab click starts from a fresh root screen, preventing the restoration of unstable map states.
-   **Logical Origin Tracking:** Added an `origin` parameter to detail routes to ensure the correct bottom navigation tab remains highlighted regardless of the navigation path.

---

## 🐞 2. Map Initialization Crash (Device Specific)
**Status:** ✅ Resolved

### 📝 Problem
The Map tab worked perfectly on a tablet but crashed instantly upon opening on certain phone hardware.

### 🔍 Root Cause
-   **Missing User Agent:** `osmdroid` requires an explicit User Agent string to fetch tiles. Some devices/Android versions fail silently or crash if `Configuration.getInstance().userAgentValue` is not set.
-   **Context Loading:** The library requires `Configuration.getInstance().load()` to be called with a valid context before the `MapView` is instantiated.

### 🛠️ Final Fix
-   **Explicit Configuration:** Added a `LaunchedEffect(Unit)` in `InventoryMapScreen.kt` to load the `osmdroid` configuration and set the `userAgentValue` to the application's package name before any map UI is rendered.

---

## 🐞 3. Recursive Location Resolution (StackOverflowError)
**Status:** ✅ Resolved

### 📝 Problem
The app would crash (process ended abruptly) when receiving its first GPS location fix, or even when opening the Inventory list with an empty database.

### 🔍 Root Cause
1.  **Circular Dependencies:** The logic to "resolve" an item's location (checking if it's inside a container, which is inside another container, etc.) was recursive. A data loop (A in B, B in A) caused an infinite loop.
2.  **UI Recursion:** The `InventoryItemRow` was also recursive for displaying nested storage. Without a depth limit, any data corruption could crash the UI thread.
3.  **Threading Violations:** The GPS "first fix" callback was attempting to update the repository from a background thread without a proper coroutine context.

### 🛠️ Final Fix
-   **Cycle Detection:** Added a `visited` set to the recursive location resolver in `InventoryRepository.kt` to detect and break circular dependencies.
-   **UI Depth Guard:** Added a `depth > 20` hard limit to `InventoryItemRow` in `InventoryListScreen.kt` to prevent UI thread stack overflows.
-   **Coroutine Safety:** Wrapped repository updates in `viewModelScope.launch` within the `InventoryListViewModel` to ensure all state changes happen on the correct thread.
-   **Flow Robustness:** Added `.catch` operators to the data flows in the ViewModel to prevent a single calculation error from killing the entire application process.

---
*Last Updated: 2026-02-27*
