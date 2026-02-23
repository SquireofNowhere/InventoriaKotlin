# 📦 Inventoria - Professional Inventory Management

**Inventoria** is a modern, feature-rich Android application built to simplify inventory tracking. Designed with a stunning **Purple Sheen** aesthetic and powered by the latest Android technologies, it provides a seamless experience for managing stock, costs, and locations.

## ✨ Key Features

### 📊 Intelligent Dashboard
- **Real-time Statistics**: Instantly view total item count, inventory valuation, and stock health.
- **Stock Alerts**: Automatic tracking of low-stock and out-of-stock items.
- **Recent Activity**: Quick access to the most recently added or updated items.

### 📋 Inventory Management
- **Full CRUD Operations**: Easily add, view, edit, and delete inventory items.
- **Smart Search**: Find items instantly by name, category, or description.
- **Detailed Tracking**: Record specific details including price, location, categories, and minimum stock levels.
- **Reactive Updates**: Powered by Kotlin Flow, ensuring the UI always reflects the latest database state.

### 🎨 Premium Design
- **Purple Sheen Theme**: A beautiful, custom-crafted purple color palette.
- **Light & Dark Mode**: Seamlessly adapts to your system theme for comfortable use day or night.
- **Modern UI Components**: Built entirely with **Jetpack Compose** and **Material 3**.
- **Visual Feedback**: Smooth transitions, shimmer effects, and Lottie animations.

## 🏗️ Architecture & Tech Stack

Inventoria follows **Clean Architecture** principles and the **MVVM** pattern, ensuring a scalable and maintainable codebase.

- **Language**: [Kotlin](https://kotlinlang.org/)
- **UI Framework**: [Jetpack Compose](https://developer.android.com/jetpack/compose) (Material 3)
- **Dependency Injection**: [Hilt](https://dagger.dev/hilt/)
- **Local Database**: [Room](https://developer.android.com/training/data-storage/room) (SQLite)
- **Asynchronous Flow**: [Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html) & [Flow](https://kotlinlang.org/docs/flow.html)
- **Navigation**: [Jetpack Navigation Compose](https://developer.android.com/jetpack/compose/navigation)
- **Image/Animation**: [Lottie](https://airbnb.io/lottie/) & Shimmer effects

## 📁 Project Structure

```
app/src/main/java/com/inventoria/app/
├── data/
│   ├── local/          # Room Database, DAOs, and Type Converters
│   ├── model/          # InventoryItem entities and UI state models
│   └── repository/     # Data sources (Room & File-based implementations)
├── di/                 # Hilt Modules for Dependency Injection
├── ui/
│   ├── main/           # MainActivity and Navigation Graph
│   ├── screens/        # Feature screens (Dashboard, Inventory, Detail, Add/Edit)
│   ├── splash/         # Animated Splash Screen
│   └── theme/          # Custom Theme, Colors, and Typography
└── InventoriaApplication.kt # Hilt Application Class
```

## 🚀 Getting Started

### Prerequisites
- Android Studio Iguana (or newer)
- JDK 17
- Android SDK 24+ (Android 7.0+)

### Setup
1. **Clone the repository**:
   ```bash
   git clone https://github.com/yourusername/inventoria-kotlin.git
   ```
2. **Open in Android Studio**:
   Select the `InventoriaKotlin` folder.
3. **Sync & Run**:
   Let Gradle sync finish, then click **Run** (▶️) to deploy to your device or emulator.

## 🎯 Roadmap
- [ ] **Barcode Scanning**: Integrated scanner for faster item entry.
- [ ] **Export/Import**: Support for CSV and PDF reporting.
- [ ] **Cloud Sync**: Firebase integration for multi-device support.
- [ ] **Advanced Analytics**: Interactive charts for stock trends and value history.

## 📄 License
This project is licensed under the MIT License.

---
*Made with 💜 and Jetpack Compose*
