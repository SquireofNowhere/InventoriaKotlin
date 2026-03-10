# Inventoria (Kotlin)

Modern Inventory & Task Tracking Management for Android. Built with Jetpack Compose, Room, and Firebase.

## 🚀 Recent Feature Updates

### 📋 Task Tracker Improvements
- **Live Text Editing**: Improved the Session Detail and Task Detail dialogs to save text data (names/descriptions) instantly on keyboard "Done" or focus loss.
- **Keyboard Integration**: Automatic keyboard dismissal and focus clearing for a smoother editing experience.

### 📦 Inventory Management
- **Selection Mode & Bulk Actions**: 
    - **Long-press** items to enter selection mode.
    - **Bulk Delete**: Remove multiple items at once from the inventory list.
    - **Smart Merge**: Combine multiple items into one. Merges descriptions, custom fields, and **sums up all quantities** into a single new entry.
- **Advanced Equipment Logic**: 
    - Dedicated "Equip" system that removes items from containers when they are on your person.
    - **Smart Repack**: When unequipping, the app offers to return the item to its last known container automatically.
- **Custom Location Entry**: Manually type or paste any address in the add/edit screen if map picking is not preferred.

### 🖼️ Multi-Image System
- **Gallery Support**: Upload and store multiple high-resolution photos per item.
- **Background Uploads**: Photos now upload in the background after saving an item, ensuring the UI remains fast and responsive.
- **Profile Picture Selection**: Choose a specific photo from an item's gallery to serve as its primary thumbnail in the inventory list.
- **Status Indicators**: Real-time loading spinners and error icons directly on thumbnails during the upload process.

### ⚙️ Settings & Localization
- **Localization Category**:
    - **Auto-Currency Detection**: Automatically sets the currency symbol ($ , €, £, etc.) based on your device locale.
    - **Manual Override**: Choose any global currency code manually from a list.
- **Account Sync**: Google Sign-In support for cross-device inventory synchronization.

## 🛠️ Tech Stack
- **UI**: Jetpack Compose (Material 3)
- **Database**: Room (Local), Firebase Realtime Database (Cloud Sync)
- **Storage**: Firebase Storage (Images)
- **DI**: Hilt (Dependency Injection)
- **Asynchronous**: Kotlin Coroutines & Flow
- **Maps**: OSMDroid (Location picking)
