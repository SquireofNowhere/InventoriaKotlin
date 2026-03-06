# 📦 Inventoria - Professional Inventory Management

**Inventoria** is a modern, high-performance Android application designed to streamline inventory tracking with logical grouping and physical hierarchy. Built with a focus on usability and aesthetics, it features a custom **Purple Sheen** theme and leverages the latest Android development practices.

## 📱 Features

### 📊 Intelligent Dashboard
- **Dynamic Statistics**: Track total item count and total inventory value at a glance.
- **Recent Activity**: Access recently modified items directly from the dashboard.
- **Visual Feedback**: Shimmer loading effects and professional Material 3 transitions.

### 📋 Smart Inventory & Hierarchy
- **Dynamic Storage**: Items can be nested inside other items (e.g., a "Hammer" inside a "Toolbox").
- **Equip & Repack**: Mark items as "Equipped" to follow your live GPS location. The system remembers where items were pulled from and offers to automatically repack them later.
- **Advanced Organization**: Group items by Category, Collection, or Location with collapsible headers.
- **Mutual Following**: Items can be linked together logically. If one item in a linked group moves physically (is equipped or stored), all linked items automatically follow.

### 🛡️ Kits & Collections
- **Unified Hierarchy**: View collections with the same tree-view depth as the main inventory.
- **Readiness Tracking**: Real-time metrics showing what percentage of a kit is available, packed, or equipped.
- **Mass Operations**: One-tap "Equip All" or "Pack All" for entire collections.

### ⏱️ Task Tracker
- **Segmented Sessions**: Track complex activities as grouped segments.
- **Precision Sync**: Atomic monotonic timestamping ensures rapid start/stop actions sync perfectly across multiple devices.
- **Productivity Scoring**: Integrated scoring for Personal, Social, and Neutral task categories.

### 🗺️ Geospatial Tracking
- **Interactive Map**: Visualize item locations globally using **osmdroid**.
- **Context-Aware Location**: Equipped items dynamically inherit the user's current coordinates.

### ☁️ Real-time Cloud Sync
- **Firebase Integration**: Latest-only bi-directional synchronization ensures your data is safe and accessible across devices.
- **Automatic Tab Sync**: Triggers a full data refresh every time you switch between app sections.
- **Offline First**: Work locally with seamless background syncing when a connection is available.

---

## 🏗️ Architecture & Tech Stack

The project follows **Clean Architecture** and **MVVM** principles to ensure scalability and maintainability.

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (100% Declarative)
- **DI**: Hilt
- **Persistence**: Room Database (v14 with Upsert and Transaction support)
- **Cloud**: Firebase Realtime Database
- **Maps**: OpenStreetMap (osmdroid)

---

## 📁 Project Structure

```
app/src/main/java/com/inventoria/app/
├── data/
│   ├── local/          # Room DB, DAOs (Inventory, Collection, Task)
│   ├── model/          # Entities (InventoryItem, Collection, Task)
│   └── repository/     # Logic for Location, Sync, and Linked Groups
├── di/                 # Hilt Modules
├── ui/
│   ├── main/           # Navigation Graph (InventoriaApp.kt)
│   ├── screens/        # Dashboard, Inventory, Collections, Tasks, Map
│   └── theme/          # Custom Purple Theme system
└── InventoriaApplication.kt
```

---

## 🛡️ Robustness Features
- **Circular Dependency Protection**: Automatically detects and heals self-parenting loops or recursive storage conflicts.
- **Monotonic Sync**: Guarantees correct ordering of operations even when actions happen in the same millisecond.
- **Tactile UI**: Integrated haptic feedback for drag-and-drop and state transitions.

---

## 🎯 Roadmap
- [ ] **Barcode Integration**: Native scanner for rapid entry.
- [ ] **Data Export**: Export to CSV/PDF.
- [ ] **Wear OS**: "Ready Check" companion for kits on your wrist.

---
*Made with 💜 and Jetpack Compose*
