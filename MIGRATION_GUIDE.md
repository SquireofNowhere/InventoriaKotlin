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
│   ├── model/
│   │   ├── InventoryItem.kt         # Replaces C# Item dictionary
│   │   └── Collection.kt            # Kits and grouping logic
│   ├── local/
│   │   ├── InventoryDao.kt          # CRUD with @Upsert for sync safety
│   │   └── InventoryDatabase.kt     # Room database (v14)
│   └── repository/
│       ├── InventoryRepository.kt    # Location resolution & batch ops
│       └── FirebaseSyncRepository.kt # Cloud bi-sync
└── ui/
    ├── main/                         # Navigation & App Scaffold
    ├── screens/dashboard/            # Stats & Recent items
    ├── screens/inventory/            # Sort/Group/Hierarchical list
    └── screens/collections/          # Kit management & Readiness
```

## Key Concept Mappings

### 1. Data Models

**C# (Dictionary-based):**
```csharp
Dictionary<string, object?> Item = new Dictionary<string, object?>
{
    {"Name", "Mouse"},
    {"Quantity", 1},
    {"Location", "My Room 414"}
};
```

**Kotlin (Type-safe data class):**
```kotlin
@Entity(tableName = "inventory_items")
data class InventoryItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    var name: String = "",
    var quantity: Int = 1,
    var location: String = "",
    var parentId: Long? = null,      // For nested storage
    var equipped: Boolean = false,   // For GPS tracking
    var lastParentId: Long? = null   // For intelligent repacking
)
```

### 2. Data Storage & Sync

**C#:**
- In-memory List<Dictionary>
- Lost on application close

**Kotlin:**
- **Local**: Room Database (SQLite) for offline-first speed.
- **Cloud**: Firebase Realtime Database for cross-device sync.
- **Bi-directional Sync**: Changes on one device reflect on others automatically.

### 3. User Interface

**C#:**
- Console-based menus.

**Kotlin (Jetpack Compose):**
- Modern, touch-first Material 3 UI.
- **Purple Sheen Theme**: Custom professional design.
- **Dynamic Grouping**: Visualize inventory by Category, Collection, or Location.

## Major Feature Enhancements

### 1. Dynamic Hierarchy
- **C#**: Flat list of items.
- **Android**: Items can be nested inside other items. A "Toolbox" can contain a "Hammer". Moving the toolbox moves all items inside it.

### 2. Equip & Repack System
- Items can be "Equipped" to the user.
- Equipped items inherit the user's live physical location via GPS.
- **Intelligent Repacking**: The app remembers which bag or box an item was pulled from and offers to return it there with one tap.

### 3. Kits & Readiness
- Group items into "Collections" (e.g., "Hiking Trip", "Daily Carry").
- Real-time "Readiness" percentages show what you have available, what is packed, and what is currently equipped.

### 4. Advanced Sorting & Grouping
- **Smart Grouping**: Group items by classification with collapsible headers.
- **Pinned Headers**: "With You" is always at the top; "Uncategorized" at the bottom.
- **Multi-Classification**: Items can belong to multiple categories simultaneously.

## Deployment & Safety

1. **Circular Safety**: The Android version prevents users from accidentally packing a container into itself (e.g., Bag A inside Bag B inside Bag A).
2. **Batch Optimization**: Updating 50 items at once (like "Equip All") is optimized to perform a single database transaction and a single GPS lookup.
3. **Migration Safety**: Uses `@Upsert` to prevent foreign key data loss during cloud synchronization.

---

**Made with 💜 and Jetpack Compose**
**Reflecting v1.14 Updates**
