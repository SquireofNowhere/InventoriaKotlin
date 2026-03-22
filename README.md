# Inventoria (Kotlin)

Modern Inventory & Task Tracking Management for Android. Built with Jetpack Compose, Room, and Firebase.

## 🌟 Comprehensive Feature List

### 📦 Inventory Management
*   **Deep Item Tracking**: Manage items with names, quantities, text locations, and GPS coordinates.
*   **Hierarchical Containers**: Organize items within other items (e.g., a "Toolbox" containing "Wrenches").
*   **Equipment System**: 
    *   **Equip/Unequip**: Mark items as "on person" which removes them from their container.
    *   **Smart Repack**: Automatically remembers the last container an item was in for easy return.
*   **Bulk Operations**: 
    *   **Selection Mode**: Long-press to select multiple items.
    *   **Smart Merge**: Combine multiple similar entries into one, summing quantities and merging metadata.
    *   **Bulk Delete**: Efficiently remove multiple items at once.
*   **Multi-Media Support**:
    *   **Image Gallery**: Attach multiple photos to any item.
    *   **Background Sync**: Photos upload to Firebase Storage in the background to keep the UI snappy.
    *   **Thumbnail Selection**: Choose a primary profile picture from the gallery.
*   **Barcoding**: Track items via SKU or Barcode for quick identification.
*   **Financials**: Track unit prices and view total inventory value on the dashboard.

### ⏱️ Task & Productivity Tracking
*   **Session-Based Tracking**: Group related tasks into sessions for better organization.
*   **Productivity Gamification**:
    *   **Task Kinds**: Assign "Kinds" (Graphite, Lavender, Peacock, etc.) which represent different productivity levels and categories (Personal, Social, Neutral).
    *   **Scoring**: Each task kind has a productivity value that contributes to your daily score.
*   **Active Monitoring**: 
    *   **Foreground Service**: Keep timers running accurately even when the app is in the background.
    *   **Live Editing**: Instant saving of task names and notes during active sessions.
*   **Productivity Dashboard**: Visualize your productivity trends and task history.
*   **Calendar Integration**: Sync tasks with the system calendar; identify "Inventoria" tasks via smart description tags.

### 🎒 Collections & Readiness
*   **Project-Based Collections**: Create custom sets of items (e.g., "Emergency Kit", "Photography Gear").
*   **Readiness Checklist**: 
    *   Real-time status tracking: Available, Packed, or Equipped.
    *   Percentage-based readiness indicators for each collection.
*   **Collection Presets**: Specific types like TRAVEL_KIT, OUTFIT, and WORK_GEAR.

### 🗺️ Map & Location Features
*   **OSMDroid Integration**: Interactive map for picking and viewing item locations.
*   **Resolved Locations**: Smart logic to inherit location data from parent containers if not explicitly set.
*   **User Proximity**: Track your current location relative to your stored items.

### ☁️ Sync & Security
*   **Real-time Cloud Sync**: Firebase Realtime Database ensures data is identical across all your devices.
*   **Conflict Resolution**: High-precision timestamping (`updatedAt`) to handle offline edits.
*   **Google Authentication**: Securely sign in and back up your data to the cloud.
*   **Soft Deletion**: All data is soft-deleted first, allowing for recovery or cleanup during sync.

### ⚙️ Customization & Localization
*   **Flexible Currency**: Automatically detects local currency or allows manual override (USD, EUR, GBP, etc.).
*   **Custom Fields**: Add arbitrary key-value metadata to any inventory item.
*   **Modern Material 3 UI**: Clean, responsive interface with Dark Mode support and dynamic theming.

## 🛠️ Tech Stack
- **UI**: Jetpack Compose (Material 3)
- **Database**: Room (Local), Firebase Realtime Database (Cloud Sync)
- **Storage**: Firebase Storage (Images)
- **DI**: Hilt (Dependency Injection)
- **Asynchronous**: Kotlin Coroutines & Flow
- **Maps**: OSMDroid (Location picking)
- **Architecture**: MVVM with Repository Pattern
