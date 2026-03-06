# Project Continuation Guide - Inventoria Android

## App Overview
**Inventoria** is a professional Android inventory management app built with Kotlin, Jetpack Compose, and Firebase. It focuses on physical nesting (containers) and logical linking (mutual followers).

### Architecture Highlights
1. **Repository Pattern**: Centralizes complex logic for location resolution and group movement in `InventoryRepository`.
2. **Monotonic Sync**: `TaskRepository` uses atomic counters to ensure rapid state changes (Start/Stop) are correctly ordered across devices.
3. **Tab-Triggered Sync**: Navigation triggers a cloud refresh on every tab switch to maintain data parity.
4. **Hierarchical Collections**: Collections mirror the main inventory's tree view while strictly enforcing membership rules.

### Key Entry Points
- **UI Logic**: `InventoriaApp.kt` (Navigation & Tab-Sync)
- **Inventory Engine**: `InventoryRepository.kt` (Nesting & Link logic)
- **Cloud Engine**: `FirebaseSyncRepository.kt` (Concurrency & Pull optimization)

### Technical Guardrails
- **Circular Dependency Detection**: Prevents infinite loops in storage hierarchies.
- **Physical Priority**: Storage relationships always override logical links during movement conflicts.
- **Tactile Feedback**: Haptics integrated into all drag-and-drop operations.
