# Migration Guide: C# Inventoria → Android Kotlin Inventoria

This guide helps you understand how the C# desktop application has been transformed into a modern Android Kotlin application.

## Architecture Comparison

### C# Version
```
Inventoria C#/
├── Program.cs          # Entry point
├── Menus.cs           # Console menu system
├── ItemManagement.cs  # Item CRUD operations
└── InventoryManagment.cs  # Inventory collection management
```

### Android Kotlin Version
```
app/src/main/java/com/inventoria/app/
├── InventoriaApplication.kt          # Application entry point
├── data/
│   ├── model/InventoryItem.kt       # Replaces C# Item dictionary
│   ├── local/InventoryDao.kt        # Database operations
│   ├── local/InventoryDatabase.kt   # Room database
│   └── repository/InventoryRepository.kt  # Data layer
├── ui/
│   ├── main/                         # Replaces Menus.cs
│   ├── screens/dashboard/            # Main dashboard
│   └── screens/inventory/            # Inventory management
└── di/                               # Dependency injection
```

## Key Concept Mappings

### 1. Data Models

**C# (Dictionary-based):**
```csharp
Dictionary<string, object?> Item = new Dictionary<string, object?>
{
    {"Name", "Mouse"},
    {"Quantity", 1},
    {"Location", "My Room 414"},
    {"Price", 199.99}
};
```

**Kotlin (Type-safe data class):**
```kotlin
@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val quantity: Int,
    val location: String,
    val price: Double? = null,
    val customFields: Map<String, String> = emptyMap()
)
```

### 2. Data Storage

**C#:**
- In-memory List<Dictionary>
- Lost on application close

**Kotlin:**
- Room Database (SQLite)
- Persistent storage
- Reactive with Flow

### 3. User Interface

**C#:**
```csharp
Console.WriteLine("Main Menu");
Console.WriteLine("1. Inventory");
var input = Console.ReadKey(true).KeyChar;
```

**Kotlin (Jetpack Compose):**
```kotlin
@Composable
fun DashboardScreen() {
    Column {
        Text("Dashboard", style = MaterialTheme.typography.headlineLarge)
        StatisticsCards()
        RecentItems()
    }
}
```

### 4. Operations

| C# Method | Kotlin Equivalent |
|-----------|-------------------|
| `CreateItem()` | `repository.insertItem(item)` |
| `DisplayAllItems()` | `LazyColumn { items(list) { ... } }` |
| `DeleteInstance(index)` | `repository.deleteItem(item)` |
| `UpdateInstance(index, item)` | `repository.updateItem(item)` |
| `FetchInventoryItem(index)` | `repository.getItemById(id)` |

### 5. Menu System

**C# Switch-based Menus:**
```csharp
switch (input) {
    case '1':
        ShowInventoryMenu();
        break;
    case '0':
        return;
}
```

**Kotlin Navigation:**
```kotlin
NavHost(navController, startDestination = "dashboard") {
    composable("dashboard") { DashboardScreen() }
    composable("inventory") { InventoryListScreen() }
    composable("settings") { SettingsScreen() }
}
```

## Feature Enhancements

### What's New in Android Version

1. **Visual Design**
   - Beautiful purple gradient theme
   - Material Design 3 components
   - Smooth animations and transitions
   - Light/dark mode support

2. **User Experience**
   - Touch-based navigation
   - Swipe gestures
   - Pull-to-refresh
   - Real-time search

3. **Data Management**
   - Persistent database storage
   - Real-time updates with Flow
   - Type-safe operations
   - Transaction support

4. **Modern Features**
   - Dashboard with statistics
   - Quick actions
   - Category filtering
   - Low stock alerts

## Data Migration

If you want to migrate data from the C# version:

### Step 1: Export from C# (You would need to add this)

Add to your C# project:
```csharp
public void ExportToJson(string filePath)
{
    var json = JsonSerializer.Serialize(Instances);
    File.WriteAllText(filePath, json);
}
```

### Step 2: Import to Android

```kotlin
suspend fun importFromJson(jsonString: String) {
    val gson = Gson()
    val listType = object : TypeToken<List<InventoryItem>>() {}.type
    val items: List<InventoryItem> = gson.fromJson(jsonString, listType)
    repository.insertItems(items)
}
```

## Custom Fields Migration

### C# Approach:
```csharp
// Any key-value can be added
Item["CustomField"] = "CustomValue";
```

### Kotlin Approach:
```kotlin
// Predefined fields + custom fields map
val item = InventoryItem(
    name = "Mouse",
    quantity = 1,
    customFields = mapOf("CustomField" to "CustomValue")
)
```

## Best Practices in the New Version

1. **Type Safety**
   - Use data classes instead of dictionaries
   - Leverage Kotlin's null safety
   - Compile-time error checking

2. **Reactive Programming**
   - Use Flow for real-time updates
   - Collect state in composables
   - Automatic UI updates

3. **Separation of Concerns**
   - Repository for data access
   - ViewModel for business logic
   - Composables for UI only

4. **Dependency Injection**
   - Hilt for automatic dependency management
   - Easy testing and mocking
   - Clean architecture

## Next Steps

1. **Build & Run** the Android app
2. **Explore** the dashboard and navigation
3. **Test** CRUD operations (coming soon)
4. **Customize** colors and themes
5. **Add** new features as needed

## Questions?

Refer to:
- [README.md](README.md) for setup instructions
- [Android Developer Docs](https://developer.android.com)
- [Jetpack Compose Docs](https://developer.android.com/jetpack/compose)
- [Kotlin Docs](https://kotlinlang.org/docs/home.html)

---

Happy coding! 🚀
