# 📦 Inventoria - Professional Inventory Management

**Inventoria** is a modern, high-performance Android application designed to streamline inventory tracking. Built with a focus on usability and aesthetics, it features a custom **Purple Sheen** theme and leverages the latest Android development practices.

## 📱 Features

### 📊 Intelligent Dashboard
- **Dynamic Statistics**: Track total item count and total inventory value at a glance.
- **Recent Activity**: Access recently modified items directly from the dashboard.
- **Visual Feedback**: Shimmer loading effects and professional Material 3 transitions.

### 📋 Smart Inventory & Hierarchy
- **Dynamic Storage**: Items can be nested inside other items (e.g., a "Hammer" inside a "Toolbox").
- **Equip & Repack**: Mark items as "Equipped" to follow your live GPS location. The system remembers where items were pulled from and offers to automatically repack them later.
- **Advanced Organization**: Group items by Category, Collection, or Location with collapsible headers.
- **Multi-Classification**: Items can belong to multiple categories simultaneously.

### 🛡️ Kits & Collections
- **Readiness Tracking**: Real-time metrics showing what percentage of a kit is available, packed, or equipped.
- **Mass Operations**: One-tap "Equip All" or "Pack All" for entire collections.

### 🗺️ Geospatial Tracking
- **Interactive Map**: Visualize item locations globally using **osmdroid**.
- **Context-Aware Location**: Equipped items dynamically inherit the user's current coordinates.

### ☁️ Real-time Cloud Sync
- **Firebase Integration**: Bi-directional synchronization ensures your data is safe and accessible across devices.
- **Offline First**: Work locally with seamless background syncing when a connection is available.

---

## 🏗️ Architecture & Tech Stack

The project follows **Clean Architecture** and **MVVM** principles to ensure scalability and maintainability.

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (100% Declarative)
- **DI**: Hilt
- **Persistence**: Room Database (v14 with Upsert support)
- **Cloud**: Firebase Realtime Database
- **Maps**: OpenStreetMap (osmdroid)

---

## 📁 Project Structure

```
app/src/main/java/com/inventoria/app/
├── data/
│   ├── local/          # Room DB, DAOs (Inventory, Collection, Task)
│   ├── model/          # Entities (InventoryItem, Collection, Task)
│   └── repository/     # Logic for Location, Batching, and Sync
├── di/                 # Hilt Modules
├── ui/
│   ├── main/           # Navigation Graph (InventoriaApp.kt)
│   ├── screens/        # Dashboard, Inventory, Collections, Tasks, Map
│   └── theme/          # Custom Purple Theme system
└── InventoriaApplication.kt
```

---

## 🛡️ Robustness Features
- **Circular Safety**: Prevents infinite loops in nested storage hierarchies.
- **Batch Optimization**: Updates multiple items in a single transaction with geocoding caching.
- **Migration Safety**: Automatic schema evolution with Firebase property mapping.

---

## 🎯 Roadmap
- [ ] **Barcode Integration**: Native scanner for rapid entry.
- [ ] **Data Export**: Export to CSV/PDF.
- [ ] **Photo Attachments**: Visual item tracking.
- [ ] **Calendar Integration**: Sync with Google Calendar to track daily/monthly/yearly task statistics without requiring local storage.

---
*Made with 💜 and Jetpack Compose*
