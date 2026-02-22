# 🚀 Quick Start Guide - Inventoria Android

Get up and running with Inventoria in 5 minutes!

## Prerequisites Checklist

- [ ] Android Studio (Latest version recommended)
- [ ] JDK 17 or higher
- [ ] Android device or emulator (API 24+)
- [ ] 4GB+ RAM available

## Installation Steps

### 1. Open the Project (2 minutes)

```bash
# If you haven't cloned yet:
git clone <repository-url>
cd inventoria-android

# Open in Android Studio:
# File → Open → Select inventoria-android folder
```

### 2. Sync Dependencies (1 minute)

Android Studio will automatically start syncing Gradle. If not:
- Click the elephant icon (🐘) in the toolbar
- Or: File → Sync Project with Gradle Files

Wait for the sync to complete. You'll see "BUILD SUCCESSFUL" in the Build tab.

### 3. Set Up an Emulator (1 minute) - Skip if using real device

1. Click the Device Manager icon in the toolbar
2. Click "Create Device"
3. Select "Pixel 7" or any modern device
4. Select System Image: "API 34" or latest
5. Click "Finish"

### 4. Run the App! (1 minute)

1. Select your device/emulator from the dropdown
2. Click the green Run button (▶️) or press `Shift + F10`
3. Wait for the build to complete
4. Watch the beautiful splash screen appear! 🎉

## First Time Experience

### What You'll See:

1. **Splash Screen** (2-3 seconds)
   - Purple gradient background
   - Inventoria logo
   - Smooth animations

2. **Dashboard**
   - Welcome card with purple gradient
   - Statistics cards (Total Items, Value, Stock status)
   - Quick action buttons
   - Recent items list

3. **Bottom Navigation**
   - Dashboard (🏠)
   - Inventory (📦)
   - Settings (⚙️)

## Testing the App

### Sample Data

The app comes with 2 sample items pre-loaded:
1. **Mouse** - Qty: 1, Location: My Room 414
2. **Controller** - Qty: 0, Location: My Room 414

### Try These Actions:

1. **View Dashboard Statistics**
   - See the total items count
   - Check the total value
   - View low stock alerts

2. **Navigate to Inventory**
   - Tap the "Inventory" button in bottom nav
   - Browse the item list
   - Use the search bar

3. **Switch Themes**
   - Open your device Settings
   - Toggle Dark Mode
   - See the app adapt with purple theme!

## Customization Quick Tips

### Change Primary Color

Edit: `app/src/main/java/com/inventoria/app/ui/theme/Color.kt`
```kotlin
val PurplePrimary = Color(0xFF8B5CF6)  // Change this hex value
```

### Add Sample Data

Edit: `app/src/main/java/com/inventoria/app/data/local/InventoryManagement.kt`
```kotlin
Instances = listOf(
    InventoryItem(
        name = "New Item",
        quantity = 10,
        location = "Warehouse A"
    )
)
```

### Modify Splash Duration

Edit: `app/src/main/java/com/inventoria/app/ui/splash/SplashActivity.kt`
```kotlin
delay(2500)  // Change milliseconds
```

## Troubleshooting

### Build Fails?

1. **Clean & Rebuild**
   ```
   Build → Clean Project
   Build → Rebuild Project
   ```

2. **Invalidate Caches**
   ```
   File → Invalidate Caches → Invalidate and Restart
   ```

3. **Check Gradle JDK**
   ```
   File → Settings → Build Tools → Gradle
   Gradle JDK: Select version 17 or higher
   ```

### App Crashes?

1. **Check Logcat**
   - Open Logcat tab at bottom
   - Filter by "Error" or "inventoria"
   - Look for stack traces

2. **Common Issues**
   - Database not initialized: Rebuild project
   - Theme issues: Clear app data and restart

### Emulator Slow?

1. **Enable Hardware Acceleration**
   - Tools → AVD Manager → Edit Device
   - Graphics: Hardware - GLES 2.0

2. **Allocate More RAM**
   - Edit Device → Show Advanced Settings
   - RAM: 2048 MB or higher

## Next Steps

Now that you're up and running:

1. ✅ Explore the codebase
2. ✅ Read [README.md](README.md) for detailed documentation
3. ✅ Check [MIGRATION_GUIDE.md](MIGRATION_GUIDE.md) if coming from C#
4. ✅ Start adding features!

## Keyboard Shortcuts

### Android Studio Essentials

- `Shift + F10` - Run app
- `Ctrl + Shift + A` - Find action
- `Alt + Enter` - Quick fix
- `Ctrl + B` - Go to definition
- `Shift + Shift` - Search everywhere

### Emulator

- `Ctrl + M` - Menu
- `Ctrl + H` - Home
- `Ctrl + B` - Back
- `Ctrl + Up/Down` - Volume

## Getting Help

- **Documentation**: Check the `README.md`
- **Issues**: GitHub Issues tab
- **Android Docs**: [developer.android.com](https://developer.android.com)
- **Compose Docs**: [developer.android.com/jetpack/compose](https://developer.android.com/jetpack/compose)

---

**You're all set! Enjoy building with Inventoria! 💜**
