# 📦 Inventoria - Professional Inventory Management

**Inventoria** is a modern, high-performance Android application designed to streamline inventory tracking. Built with a focus on usability and aesthetics, it features a custom **Purple Sheen** theme and leverages the latest Android development practices.

## 📱 Features

### 📊 Intelligent Dashboard
- **Dynamic Statistics**: Track total item count and total inventory value at a glance.
- **Quick Actions**: Rapidly add items or jump to the full inventory list.
- **Recent Activity**: Scrollable list of recently modified items for quick access.
- **Visual Feedback**: Shimmer loading effects and smooth transitions.

### 📋 Advanced Inventory Control
- **Full CRUD Support**: Add, edit, view, and delete inventory items with ease.
- **Smart Organization**: Categorize items, set quantities, and track pricing.
- **Custom Fields**: Add flexible key-value pairs to items for specific tracking needs.
- **Equip System**: Mark items as "Equipped" (e.g., in a mobile kit) or stored in containers.

### 🗺️ Geospatial Tracking
- **Interactive Map**: Built with **osmdroid**, allowing you to see exactly where your items are located.
- **Location Picker**: Precision location selection for item entry.
- **Visual Markers**: Custom markers with info windows for item details.

### ⏱️ Task Management
- **Integrated Task Tracker**: Manage inventory-related tasks with a built-in timer.
- **Productivity Focus**: Keep track of time spent on restocking or audits.

### 🎨 Premium UI/UX
- **Jetpack Compose**: 100% declarative UI for a fluid experience.
- **Material 3**: Utilizing the latest design components and principles.
- **Dual Theme**: Comprehensive support for Light and Dark modes.
- **Lottie Animations**: Engaging visual feedback for a polished feel.

---

## 🏗️ Architecture & Tech Stack

The project follows **Clean Architecture** and **MVVM** principles to ensure scalability and maintainability.

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Dependency Injection**: Hilt
- **Persistence**: Room Database (SQLite)
- **Async & Streams**: Coroutines & Flow
- **Navigation**: Jetpack Navigation Compose
- **Maps**: OpenStreetMap (osmdroid)
- **Images/Animations**: Lottie, Shimmer, Vector Drawables

---

## 📁 Project Structure

```
app/src/main/java/com/inventoria/app/
├── data/
│   ├── local/          # Room DB, DAOs, and Type Converters
│   ├── model/          # InventoryItem entities and UI state models
│   └── repository/     # Data sources and business logic
├── di/                 # Hilt Modules for Dependency Injection
├── ui/
│   ├── main/           # MainActivity and Navigation Graph (InventoriaApp.kt)
│   ├── screens/        # Feature screens (Dashboard, Inventory, Map, Tasks, Settings)
│   ├── splash/         # Animated Splash Screen
│   └── theme/          # Purple Sheen Theme, Colors, and Typography
└── InventoriaApplication.kt # Hilt Application Class
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Iguana (or newer)
- JDK 17
- Android SDK 24+ (Android 7.0+)

### Setup
1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/InventoriaKotlin.git
   ```
2. **Open in Android Studio**:
   Select the `InventoriaKotlin` folder and wait for Gradle sync.
3. **Run**:
   Deploy to an emulator or physical device.

---

## 🛑 Known Issues

### 🐛 Navigation Crash (Priority: High)
There is a known crash that occurs when navigating via the bottom navigation bar under specific conditions:
1. Open **Dashboard**.
2. Click an item to open **Item Detail**.
3. Click the location to open the **Inventory Map**.
4. Click **Dashboard** in the bottom navigation bar.
5. **Result**: The application crashes.

*Note: This is currently under investigation and appears related to backstack state restoration when navigating away from the Map screen while it was launched from a detail view.*

---

## 🎯 Roadmap
- [ ] **Barcode Integration**: Native scanner for faster item management.
- [ ] **Data Export**: Export inventory lists to CSV or PDF.
- [ ] **Cloud Sync**: Optional Firebase/Supabase integration.
- [ ] **Advanced Filtering**: Filter by valuation range or low-stock status.

---
*Made with 💜 and Jetpack Compose*
