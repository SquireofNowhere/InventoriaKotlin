# Inventoria Project Guide

## Project Overview

**Inventoria** is an Android application for inventory and task tracking with physical hierarchy, logical linking, and multi-device sync. Built with Jetpack Compose, Room, Firebase, and Hilt.

- **Purpose**: Manage items, collections, tasks, and locations with a focus on real-world container relationships and cross-device synchronization.
- **Tech Stack**: Kotlin, Jetpack Compose (Material 3), Room, Firebase (Realtime Database, Storage, Auth), Hilt, Coroutines/Flow, OSMDroid.
- **Architecture**: MVVM + Repository pattern; unidirectional data flow; DI via Hilt; navigation with Compose Navigation.

## Getting Started

### Prerequisites
- Android Studio Hedgehog (2023.1.1) or later
- JDK 17
- Android SDK with API level 34 (compile/target) and minimum SDK 24
- Google Firebase project (for backend)
  - Realtime Database
  - Storage
  - Authentication (Google Sign‑In)
- Keystore for release builds (see `DEPLOYMENT.md`)

### Installation
1. Clone the repository.
2. Ensure `google-services.json` is placed in `app/` (download from Firebase console).
3. Configure Firebase credentials in `app/src/main/java/com/inventoria/app/InventoriaApplication.kt` (the Realtime Database URL is already set).
4. Create `keystore.properties` for release signing (see `DEPLOYMENT.md`). The file is gitignored.
5. Open the project in Android Studio and sync Gradle.

### Running the App
- Debug: Run the `app` module on an emulator or device.
- Release: Generate an App Bundle with `./gradlew bundleRelease`.

### Running Tests
- Unit tests: `./gradlew test`
- Instrumented tests: `./gradlew connectedAndroidTest`

## Project Structure

```
app/
├── src/
│   ├── main/
│   │   ├── java/com/inventoria/app/
│   │   │   ├── data/                    # Data layer
│   │   │   │   ├── local/              # Room DB, DAOs, converters
│   │   │   │   ├── model/              # Data entities (InventoryItem, Collection, Task, ItemLink)
│   │   │   │   ├── repository/         # Repositories (Inventory, Collection, FirebaseSync, etc.)
│   │   │   │   └── TaskRepository.kt
│   │   │   ├── di/                     # Hilt modules (DatabaseModule, FirebaseModule, RepositoryModule)
│   │   │   ├── ui/                    # UI layer (Compose)
│   │   │   │   ├── main/              # MainActivity, InventoriaApp (NavHost)
│   │   │   │   ├── screens/           # Feature screens (inventory, collections, dashboard, task, map, settings)
│   │   │   │   ├── components/        # Reusable UI components (SyncStatusIndicator, InventoryDialogs)
│   │   │   │   └── theme/             # Material 3 theme (Color, Type, Shape, Theme)
│   │   │   └── InventoriaApplication.kt
│   │   ├── res/                       # Android resources (drawables, values, layouts, xml)
│   │   └── AndroidManifest.xml
│   ├── test/                          # Unit tests
│   └── androidTest/                   # Instrumentation tests
├── build.gradle.kts                   # App module build configuration
└── proguard-rules.pro                 # Release obfuscation (if enabled)

.gradle/
build.gradle.kts                      # Root build configuration
settings.gradle.kts                   # Project settings
gradle/libs.versions.toml             # Version catalog
DEPLOYMENT.md                         # Release publishing guide
TASK_MIGRATION_GUIDE.md               # Task system migration notes
.continue/rules/CONTINUE.md           # This file
```

## Development Workflow

### Coding Standards
- Kotlin official code style (`kotlin.code.style=official` in `gradle.properties`).
- Use meaningful names; prefer immutable `val`s.
- Compose: state hoisting, remember only what's necessary, side‑effects in `LaunchedEffect`/`DisposableEffect`.
- ViewModels expose `StateFlow<T>`; collect with `collectAsState()` in Composable.

### Testing
- Unit tests for ViewModels, Repositories (mocked), and utility functions.
- Instrumented tests for UI flows and Room database operations.
- Aim for high coverage of business logic; UI tests for critical paths.

### Build & Deploy
- Debug builds: `./gradlew assembleDebug`
- Release builds: `./gradlew bundleRelease` (App Bundle) or `assembleRelease` (APK).
- See `DEPLOYMENT.md` for full release checklist and Play Store steps.

### Contribution Guidelines
1. Create a feature branch from `main`.
2. Follow the architecture: UI → ViewModel → Repository → Data sources.
3. Use Hilt for DI; do not manually instantiate repositories in ViewModels.
4. Keep Compousables small and reusable; extract logic into ViewModels.
5. Ensure existing tests pass and add tests for new business logic.
6. Update documentation (including this file) when adding features or changing flows.

## Key Concepts

### Domain Terminology
- **Item**: Physical/virtual asset (`InventoryItem`). Supports hierarchical nesting via `parentId`.
- **Collection**: User-defined grouping of items (many‑to‑many via `CollectionDao`).
- **Link**: Logical relationship (`ItemLink`): `followerId` follows `leaderId` for location inheritance.
- **Storage**: Boolean flag indicating container items (e.g., boxes, bags).
- **Equip**: Items marked as "on your person"; unequipping can auto‑return to last container.
- **Task**: Productivity tracking with categories (Peacock, Lavender, Tomato, etc.).

### Core Abstractions
- **UiState**: Immutable data class representing screen state (e.g., `InventoryUiState`).
- **Repository**: Single source of truth; abstracts local (Room) and remote (Firebase) data.
- **ViewModel**: Holds UI state and business logic; exposes `StateFlow` and user actions.
- **Navigation**: `Screen` sealed class defines routes; `InventoriaApp` hosts `NavHost`.

### Design Patterns
- Unidirectional Data Flow (Compose + StateFlow).
- Repository pattern with combined local/remote data sources.
- Dependency Injection with Hilt.
- Sealed classes for navigation and task categories.

## Common Tasks

### Adding a new screen
1. Create a `@Composable` in `ui/screens/<feature>/`.
2. Create a corresponding `ViewModel` (Hilt‑annotated) and UI state data class.
3. Add a route to the `Screen` sealed class in `ui/main/InventoriaApp.kt`.
4. Add a `composable` entry in the `NavHost`.
5. Pass callbacks for navigation (e.g., `onNavigateBack`, `onItemClick`).

### Modifying the inventory list
- The inventory uses a complex `combine` in `InventoryListViewModel` to merge items, collections, links, and settings. Keep changes localized to methods like `filterAndSortItems`, `buildHierarchy`, and `resolveLocations`.
- Drag‑and‑drop logic lives in `InventoryListScreen` and `InventoryItemRow`. The `onDrag` handlers call `viewModel.moveItem` or `viewModel.linkItem`.

### Working with Room migrations
- By default the app uses `fallbackToDestructiveMigration()`. For non‑destructive migrations, add `addMigrations(...)` in `DatabaseModule.kt` and update `InventoryDatabase` version.
- See `TASK_MIGRATION_GUIDE.md` for an example.

### Enabling/disabling features with settings
Settings are stored via `DataStore` in `SettingsRepository`. Read/set values in ViewModels by injecting `SettingsRepository`. Example: `settingsRepository.saveInventorySort(option.name)`.

## Troubleshooting

### Common Issues
- **Firebase initialization fails**: Ensure `google-services.json` is present and the `google-services` plugin is applied. Check the database URL in `InventoriaApplication.kt`.
- **Compose compiler mismatch**: Use the compiler version from `libs.versions.toml`; align `composeOptions.kotlinCompilerExtensionVersion`.
- **Hilt annotation processing errors**: Ensure `kapt.use.worker.api=false` in `gradle.properties` and correct `kapt` dependencies.
- **Room schema export errors**: Add `kapt` options if needed; ensure all entities are annotated with `@Entity`.
- **Drag‑and‑drop not working**: The gesture detection uses `awaitPointerEventScope` with a long‑press threshold. Check `viewConfiguration.longPressTimeoutMillis` and `touchSlop`.
- **Sync not triggering**: `MainActivity` triggers `inventoryViewModel.triggerManualSync()` in `onStart` and `onStop`. Ensure `FirebaseSyncRepository` is correctly set up.

### Debugging Tips
- Use `Log.d/e` with consistent tags (e.g., `"InventoryListViewModel"`).
- For Compose UI issues, enable debugging with `androidx.compose.ui:ui-tooling` and use the Layout Inspector.
- Inspect Room databases with `adb shell` and `sqlite3` or Android Studio's Database Inspector.
- Monitor Firebase Realtime Database rules and data structure.

## References

- [Android Developers – Jetpack Compose](https://developer.android.com/jetpack/compose)
- [Android Developers – Room](https://developer.android.com/training/data-storage/room)
- [Android Developers – Hilt](https://developer.android.com/training/dependency-injection/hilt-android)
- [Firebase Realtime Database](https://firebase.google.com/docs/database)
- [OSMDroid Wiki](https://github.com/osmdroid/osmdroid/wiki)
- [Compose Navigation](https://developer.android.com/jetpack/compose/navigation)
- Project‑specific docs: `DEPLOYMENT.md`, `TASK_MIGRATION_GUIDE.md`, `README.md`

---

**Note**: Some sections (e.g., exact Firebase rules, internal APIs) are omitted for brevity. Assume information is accurate unless marked otherwise. Please update this guide when architecture or workflows change.