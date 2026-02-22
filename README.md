# 📦 Inventoria - Android Inventory Management App

A beautiful, modern Android inventory management application built with Kotlin and Jetpack Compose, featuring a stunning purple theme that adapts seamlessly between light and dark modes.

## ✨ Features

### Current Features
- **Beautiful Purple Theme** - Elegant purple gradient design with shimmer effects
- **Light & Dark Mode Support** - Seamlessly adapts to system theme
- **Animated Splash Screen** - Eye-catching intro with smooth animations
- **Dashboard Overview** - Quick stats and insights at a glance
- **Room Database** - Local data persistence
- **Modern Architecture** - MVVM with Clean Architecture principles
- **Dependency Injection** - Hilt for clean, testable code

### Coming Soon
- ✅ Add/Edit/Delete Inventory Items
- ✅ Barcode Scanner Integration
- ✅ Search & Filter Functionality
- ✅ Export to CSV/PDF
- ✅ Multi-language Support
- ✅ Cloud Sync (Firebase)
- ✅ Analytics & Reports

## 🎨 Design Philosophy

Inventoria features a **purple sheen theme** that:
- Provides excellent contrast in both light and dark modes
- Uses gradient backgrounds for visual appeal
- Implements shimmer effects for an interactive feel
- Follows Material Design 3 guidelines
- Offers smooth animations and transitions

### Color Palette

**Light Mode:**
- Primary: `#8B5CF6` (Purple)
- Background: `#F8FAFC` (Light Gray)
- Surface: `#FFFFFF` (White)

**Dark Mode:**
- Primary: `#A78BFA` (Light Purple)
- Background: `#0F172A` (Dark Blue)
- Surface: `#1E293B` (Dark Gray)

## 🏗️ Architecture

The app follows **Clean Architecture** with **MVVM** pattern:

```
app/
├── data/
│   ├── local/           # Room Database & DAOs
│   ├── model/           # Data models & entities
│   └── repository/      # Repository implementations
├── di/                  # Hilt dependency injection modules
├── ui/
│   ├── main/           # Main activity & navigation
│   ├── screens/        # Feature screens
│   │   ├── dashboard/
│   │   ├── inventory/
│   │   └── settings/
│   ├── splash/         # Splash screen
│   └── theme/          # Compose theme & colors
└── InventoriaApplication.kt
```

## 🛠️ Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Architecture:** MVVM + Clean Architecture
- **Dependency Injection:** Hilt
- **Database:** Room
- **Async:** Kotlin Coroutines & Flow
- **Navigation:** Jetpack Navigation Compose
- **Build System:** Gradle (Kotlin DSL)

### Key Dependencies

```kotlin
// Jetpack Compose
androidx.compose:compose-bom:2023.10.01
androidx.compose.material3:material3

// Architecture Components
androidx.lifecycle:lifecycle-viewmodel-ktx
androidx.navigation:navigation-compose

// Room Database
androidx.room:room-runtime
androidx.room:room-ktx

// Hilt
com.google.dagger:hilt-android
androidx.hilt:hilt-navigation-compose

// Coroutines
org.jetbrains.kotlinx:kotlinx-coroutines-android
```

## 📱 Screenshots

### Splash Screen
Beautiful purple gradient with shimmer effect and smooth animations.

### Dashboard
- Total items count
- Total inventory value
- Low stock alerts
- Out of stock warnings
- Recent items list
- Quick action buttons

### Inventory List
- Search functionality
- Filter by category
- Swipe actions
- Item details

## 🚀 Getting Started

### Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK (API 24+)
- Gradle 8.2+

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/yourusername/inventoria-android.git
   cd inventoria-android
   ```

2. **Open in Android Studio**
   - Open Android Studio
   - Select "Open an Existing Project"
   - Navigate to the cloned directory
   - Click "OK"

3. **Sync Gradle**
   - Android Studio will automatically sync Gradle
   - If not, click "File" → "Sync Project with Gradle Files"

4. **Run the app**
   - Connect an Android device or start an emulator
   - Click the "Run" button (▶️) or press `Shift + F10`

### Build Variants

```bash
# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease

# Run tests
./gradlew test

# Run instrumented tests
./gradlew connectedAndroidTest
```

## 📐 Project Structure

### Data Layer

**Models:**
```kotlin
@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long,
    val name: String,
    val quantity: Int,
    val location: String,
    val price: Double? = null,
    val customFields: Map<String, String>,
    // ... more fields
)
```

**Repository:**
```kotlin
@Singleton
class InventoryRepository @Inject constructor(
    private val inventoryDao: InventoryDao
) {
    fun getAllItems(): Flow<List<InventoryItem>>
    suspend fun insertItem(item: InventoryItem): Long
    // ... more operations
}
```

### UI Layer

**ViewModel:**
```kotlin
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: InventoryRepository
) : ViewModel() {
    val uiState: StateFlow<DashboardUiState>
    // ... state management
}
```

**Screen:**
```kotlin
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    // ... UI composition
}
```

## 🎯 Roadmap

### Version 1.0 (Current)
- [x] Basic project structure
- [x] Database setup
- [x] Dashboard with statistics
- [x] Purple theme implementation
- [x] Splash screen animation
- [ ] Inventory CRUD operations
- [ ] Search & filter

### Version 1.1 (Next)
- [ ] Barcode scanner
- [ ] Export functionality
- [ ] Categories management
- [ ] Advanced filtering
- [ ] Sorting options

### Version 2.0 (Future)
- [ ] Cloud synchronization
- [ ] Multi-device support
- [ ] Analytics dashboard
- [ ] Notification system
- [ ] Widgets

## 🤝 Contributing

Contributions are welcome! Please follow these steps:

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

### Code Style

- Follow [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html)
- Use meaningful variable and function names
- Add comments for complex logic
- Write unit tests for new features

## 📄 License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

## 🙏 Acknowledgments

- Material Design 3 Guidelines
- Jetpack Compose Documentation
- Android Architecture Components
- The Kotlin and Android Community

## 📧 Contact

Project Link: [https://github.com/yourusername/inventoria-android](https://github.com/yourusername/inventoria-android)

---

**Made with 💜 and Jetpack Compose**
