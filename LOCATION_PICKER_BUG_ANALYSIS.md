# 🐛 Location Picker Flow Analysis - CRITICAL BUG FOUND!

## Scenario: User Editing Item with Location "home"

Let's trace what happens when a user edits an item that has location = "home" and tries to use the location picker.

---

## 📍 Current Flow (Step-by-Step)

### **Step 1: Navigate to Edit Screen**

```
User clicks edit on item with:
  - id: 123
  - name: "Laptop"
  - location: "home"  ← Current value
  - quantity: 5
```

**Navigation:**
```kotlin
// From ItemDetailScreen
onEditItem = { id -> navController.navigate("edit_item/$id") }

// Result: Navigates to "edit_item/123"
```

**Route Matches:**
```kotlin
composable("edit_item/{itemId}") { backStackEntry ->
    val itemId = backStackEntry.arguments?.getString("itemId")  // "123"
    AddEditItemScreen(
        onNavigateBack = { navController.popBackStack() },
        onPickLocation = { 
            navController.navigate("location_picker/edit_item_$itemId") 
            // Will become: "location_picker/edit_item_123"
        }
    )
}
```

---

### **Step 2: AddEditItemViewModel Initializes**

```kotlin
// AddEditItemViewModel.kt - init block

init {
    val itemId: Long? = savedStateHandle.get<String>("itemId")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("itemId")

    if (itemId != null && itemId != -1L && itemId != 0L) {
        viewModelScope.launch {
            repository.getItemById(itemId)?.let { item ->
                currentItemId = item.id              // 123
                name = item.name                      // "Laptop"
                quantity = item.quantity.toString()   // "5"
                location = item.location              // "home" ← HERE!
                price = item.price?.toString() ?: ""
                category = item.category ?: ""
                description = item.description ?: ""
                
                // Tries to parse "home" as coordinates
                currentLocationGeoPoint = parseLocation(item.location)
                // parseLocation("home") returns null (not valid coords)
                
                // ... loads custom fields
            }
        }
    }
}
```

**Current State:**
```
ViewModel State:
  location = "home"               ← Text shown in field
  currentLocationGeoPoint = null  ← No valid GeoPoint
```

---

### **Step 3: AddEditItemScreen Renders**

```kotlin
// AddEditItemScreen.kt lines 97-112

Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(8.dp)
) {
    OutlinedTextField(
        value = viewModel.location,     // Shows "home"
        onValueChange = { viewModel.location = it },
        label = { Text("Location *") },
        modifier = Modifier.weight(1f)
    )
    
    IconButton(onClick = onPickLocation) {  // ← User clicks HERE
        Icon(Icons.Default.LocationOn, contentDescription = "Pick on Map")
    }
}
```

**User sees:**
```
┌─────────────────────────────────────┐
│ Location *                          │
│ home                           📍    │ ← User clicks pin icon
└─────────────────────────────────────┘
```

---

### **Step 4: Navigate to Location Picker**

**Execution:**
```kotlin
// From AddEditItemScreen
onPickLocation = { 
    navController.navigate("location_picker/edit_item_123") 
}
```

**Route Matches:**
```kotlin
composable("location_picker/{origin}") { backStackEntry ->
    val origin = backStackEntry.arguments?.getString("origin")
    // origin = "edit_item_123"
    
    LocationPickerScreen(
        onLocationSelected = { geoPoint, address ->
            navController.previousBackStackEntry?.savedStateHandle?.set("selected_location", geoPoint)
            navController.previousBackStackEntry?.savedStateHandle?.set("selected_address", address)
            navController.popBackStack()
        },
        onNavigateBack = { navController.popBackStack() }
    )
}
```

---

### **Step 5: LocationPickerScreen Initializes**

```kotlin
// LocationPickerScreen.kt

@Composable
fun LocationPickerScreen(
    initialLocation: GeoPoint? = null,  // ← Notice: No parameter passed!
    onLocationSelected: (GeoPoint, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    // ...
    val defaultLocation = GeoPoint(-26.2041, 28.0473) // Johannesburg
    
    var markerPosition by remember { 
        mutableStateOf(initialLocation ?: defaultLocation) 
        // initialLocation is NULL, so uses Johannesburg default
    }
    
    // Map centers on Johannesburg, NOT on user's current location "home"
}
```

**Problem #1: initialLocation is never passed from InventoriaApp.kt**

The LocationPickerScreen is called like this:
```kotlin
LocationPickerScreen(
    onLocationSelected = { ... },
    onNavigateBack = { ... }
)
// Missing: initialLocation = viewModel.currentLocationGeoPoint
```

**Result:**
- User had item at "home"
- Map opens centered on Johannesburg (-26.2041, 28.0473)
- User has to manually find their actual location
- Poor UX! 😞

---

### **Step 6: User Picks New Location**

```
User interaction:
1. Taps on map at new location
2. Marker moves to: GeoPoint(-26.1234, 28.5678)
3. Card at bottom shows: "Lat: -26.1234, Lng: 28.5678"
4. User taps checkmark (✓) in top bar
```

**Execution:**
```kotlin
// LocationPickerScreen.kt lines 113-120

actions = {
    IconButton(onClick = {
        onLocationSelected(
            markerPosition,                                    // GeoPoint(-26.1234, 28.5678)
            "${markerPosition.latitude}, ${markerPosition.longitude}"  // "-26.1234, 28.5678"
        )
        onNavigateBack()
    }) {
        Icon(Icons.Default.Check, contentDescription = "Confirm")
    }
}
```

**This triggers the lambda from InventoriaApp.kt:**
```kotlin
onLocationSelected = { geoPoint, address ->
    // Save to previous screen's SavedStateHandle
    navController.previousBackStackEntry?.savedStateHandle?.set("selected_location", geoPoint)
    navController.previousBackStackEntry?.savedStateHandle?.set("selected_address", address)
    // geoPoint = GeoPoint(-26.1234, 28.5678)
    // address = "-26.1234, 28.5678"
    
    navController.popBackStack()
}
```

**SavedStateHandle now contains:**
```
previousBackStackEntry.savedStateHandle:
  "selected_location" → GeoPoint(-26.1234, 28.5678)
  "selected_address"  → "-26.1234, 28.5678"
```

---

### **Step 7: Return to AddEditItemScreen**

```
Navigation pops back to: "edit_item/123"
```

**Problem #2: ViewModel NEVER retrieves the saved location!**

The AddEditItemViewModel has access to SavedStateHandle:
```kotlin
@HiltViewModel
class AddEditItemViewModel @Inject constructor(
    private val repository: InventoryRepository,
    savedStateHandle: SavedStateHandle,  // ← Has this!
    @ApplicationContext private val context: Context
)
```

**But it NEVER checks for returned data:**
```kotlin
init {
    val itemId: Long? = savedStateHandle.get<String>("itemId")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("itemId")
    
    // Gets itemId from savedStateHandle ✅
    
    // ❌ NEVER checks for:
    // val selectedLocation = savedStateHandle.get<GeoPoint>("selected_location")
    // val selectedAddress = savedStateHandle.get<String>("selected_address")
    
    if (itemId != null) {
        // Load item...
    }
}
```

**Result:**
```
User's perspective:
1. Opens edit screen: location = "home"
2. Clicks map picker icon
3. Picks new location on map
4. Returns to edit screen: location STILL = "home" ❌❌❌
5. User is confused! The location didn't change!
```

---

## 🔴 THE BUG: Data is Saved but Never Retrieved!

### What Happens:

```
┌─────────────────────────┐
│  AddEditItemScreen      │
│  location = "home"      │
└────────────┬────────────┘
             │ navigate("location_picker/edit_item_123")
             ↓
┌─────────────────────────────┐
│  LocationPickerScreen       │
│  User picks new location    │
│  GeoPoint(-26.1234, 28.5678)│
└────────────┬────────────────┘
             │ onLocationSelected(geoPoint, address)
             │ savedStateHandle.set("selected_location", geoPoint)
             │ savedStateHandle.set("selected_address", address)
             │ popBackStack()
             ↓
┌─────────────────────────┐
│  AddEditItemScreen      │
│  location = "home"      │ ← STILL "home"! Data not retrieved!
│                         │
│  ❌ ViewModel never     │
│     checks savedState   │
└─────────────────────────┘
```

---

## 💡 THE FIX

### Solution 1: Observe SavedStateHandle in ViewModel

Add this to AddEditItemViewModel:

```kotlin
init {
    val itemId: Long? = savedStateHandle.get<String>("itemId")?.toLongOrNull()
        ?: savedStateHandle.get<Long>("itemId")

    if (itemId != null && itemId != -1L && itemId != 0L) {
        viewModelScope.launch {
            repository.getItemById(itemId)?.let { item ->
                currentItemId = item.id
                name = item.name
                quantity = item.quantity.toString()
                location = item.location
                price = item.price?.toString() ?: ""
                category = item.category ?: ""
                description = item.description ?: ""
                currentLocationGeoPoint = parseLocation(item.location)
                
                customFields.clear()
                item.customFields.forEach { (key, value) ->
                    customFields.add(CustomField(key, value))
                }
            }
        }
    } else {
        getCurrentLocation(isManual = false)
    }
    
    // ✅ ADD THIS: Listen for location picker results
    viewModelScope.launch {
        savedStateHandle.getStateFlow<GeoPoint?>("selected_location", null)
            .collect { geoPoint ->
                if (geoPoint != null) {
                    val address = savedStateHandle.get<String>("selected_address") ?: ""
                    updateLocation(geoPoint, address)
                    
                    // Clear the saved state so it doesn't trigger again
                    savedStateHandle.remove<GeoPoint>("selected_location")
                    savedStateHandle.remove<String>("selected_address")
                }
            }
    }
}
```

### Solution 2: Pass Initial Location to Picker

Update InventoriaApp.kt:

```kotlin
composable("edit_item/{itemId}") { backStackEntry ->
    val itemId = backStackEntry.arguments?.getString("itemId") ?: return@composable
    
    // ✅ ADD: Get the saved location if returning from picker
    val savedLocation = backStackEntry.savedStateHandle.get<GeoPoint>("selected_location")
    
    AddEditItemScreen(
        onNavigateBack = { navController.popBackStack() },
        onPickLocation = { 
            navController.navigate("location_picker/edit_item_$itemId") 
        }
    )
}

// Later in LocationPickerScreen composable:
composable("location_picker/{origin}") { backStackEntry ->
    val origin = backStackEntry.arguments?.getString("origin") ?: "unknown"
    
    // ✅ ADD: Get current location from previous screen's ViewModel
    // This is tricky because we need to access the ViewModel
    // Easier solution: Pass it through navigation arguments
    
    LocationPickerScreen(
        initialLocation = /* need to get from AddEditItemViewModel */,
        onLocationSelected = { geoPoint, address ->
            navController.previousBackStackEntry?.savedStateHandle?.set("selected_location", geoPoint)
            navController.previousBackStackEntry?.savedStateHandle?.set("selected_address", address)
            navController.popBackStack()
        },
        onNavigateBack = { navController.popBackStack() }
    )
}
```

---

## 📊 Comparison: Current vs Fixed Flow

### Current Flow (BROKEN):

```
Edit Item (location="home")
    ↓
Pick Location (map shows Johannesburg)
    ↓
User picks (-26.1234, 28.5678)
    ↓
savedStateHandle.set("selected_location", geoPoint)
savedStateHandle.set("selected_address", address)
    ↓
Edit Item (location STILL = "home") ❌
    ↓
User saves item with "home" ← Wrong location!
```

### Fixed Flow (with Solution 1):

```
Edit Item (location="home")
    ↓
Pick Location (map shows Johannesburg)
    ↓
User picks (-26.1234, 28.5678)
    ↓
savedStateHandle.set("selected_location", geoPoint)
savedStateHandle.set("selected_address", address)
    ↓
ViewModel observes savedStateHandle changes ✅
ViewModel.updateLocation(geoPoint, "-26.1234, 28.5678") ✅
    ↓
Edit Item (location = "-26.1234, 28.5678") ✅
    ↓
User saves item with new coordinates ✓
```

---

## 🎯 Root Cause Analysis

### Why This Bug Exists:

1. **SavedStateHandle Pattern Incomplete**
   - Navigation correctly saves data to SavedStateHandle ✅
   - ViewModel never retrieves data from SavedStateHandle ❌

2. **Missing Communication Bridge**
   - LocationPickerScreen → SavedStateHandle: Works ✅
   - SavedStateHandle → ViewModel: Broken ❌

3. **No Initial Location Passed**
   - ViewModel has `currentLocationGeoPoint`
   - LocationPickerScreen never receives it
   - Map always starts at default (Johannesburg)

### Impact:

- **Severity**: HIGH
- **User Experience**: BROKEN
- **Data Integrity**: COMPROMISED (wrong locations saved)
- **Frequency**: 100% (happens every time picker is used)

---

## ✅ Recommended Implementation

### Complete Fix (Best Practice):

```kotlin
// AddEditItemViewModel.kt

@HiltViewModel
class AddEditItemViewModel @Inject constructor(
    private val repository: InventoryRepository,
    private val savedStateHandle: SavedStateHandle,  // Make it a val, not just a param
    @ApplicationContext private val context: Context
) : ViewModel() {

    // ... existing vars ...

    init {
        val itemId: Long? = savedStateHandle.get<String>("itemId")?.toLongOrNull()
            ?: savedStateHandle.get<Long>("itemId")

        // Load existing item if editing
        if (itemId != null && itemId != -1L && itemId != 0L) {
            loadItem(itemId)
        } else {
            getCurrentLocation(isManual = false)
        }
        
        // Listen for location picker results
        observeLocationPickerResults()
    }
    
    private fun loadItem(itemId: Long) {
        viewModelScope.launch {
            repository.getItemById(itemId)?.let { item ->
                currentItemId = item.id
                name = item.name
                quantity = item.quantity.toString()
                location = item.location
                price = item.price?.toString() ?: ""
                category = item.category ?: ""
                description = item.description ?: ""
                currentLocationGeoPoint = parseLocation(item.location)
                
                customFields.clear()
                item.customFields.forEach { (key, value) ->
                    customFields.add(CustomField(key, value))
                }
            }
        }
    }
    
    private fun observeLocationPickerResults() {
        viewModelScope.launch {
            // Use StateFlow to observe changes
            savedStateHandle.getStateFlow<GeoPoint?>("selected_location", null)
                .collect { geoPoint ->
                    if (geoPoint != null) {
                        val address = savedStateHandle.get<String>("selected_address") ?: ""
                        updateLocation(geoPoint, address)
                        
                        // Clear after consuming
                        savedStateHandle.remove<GeoPoint>("selected_location")
                        savedStateHandle.remove<String>("selected_address")
                    }
                }
        }
    }

    fun updateLocation(geoPoint: GeoPoint, text: String) {
        location = text
        currentLocationGeoPoint = geoPoint
    }

    // ... rest of the code ...
}
```

### And in InventoriaApp.kt:

```kotlin
composable("location_picker/{origin}") { backStackEntry ->
    val origin = backStackEntry.arguments?.getString("origin") ?: "unknown"
    
    // Try to get initial location from the calling screen
    val initialLocation = navController.previousBackStackEntry
        ?.savedStateHandle
        ?.get<GeoPoint>("current_location")
    
    LocationPickerScreen(
        initialLocation = initialLocation,  // Now passed!
        onLocationSelected = { geoPoint, address ->
            navController.previousBackStackEntry?.savedStateHandle?.set("selected_location", geoPoint)
            navController.previousBackStackEntry?.savedStateHandle?.set("selected_address", address)
            navController.popBackStack()
        },
        onNavigateBack = { navController.popBackStack() }
    )
}

composable("edit_item/{itemId}") { backStackEntry ->
    val itemId = backStackEntry.arguments?.getString("itemId") ?: return@composable
    
    // Get the ViewModel to access current location
    val viewModel: AddEditItemViewModel = hiltViewModel()
    
    // Before navigating to picker, save current location
    LaunchedEffect(viewModel.currentLocationGeoPoint) {
        viewModel.currentLocationGeoPoint?.let { geoPoint ->
            backStackEntry.savedStateHandle["current_location"] = geoPoint
        }
    }
    
    AddEditItemScreen(
        onNavigateBack = { navController.popBackStack() },
        onPickLocation = { 
            navController.navigate("location_picker/edit_item_$itemId") 
        },
        viewModel = viewModel  // Pass explicitly
    )
}
```

---

## 📝 Summary

**Current State:**
- Location picker saves data ✅
- ViewModel never retrieves data ❌
- User experience is broken ❌

**What needs to be fixed:**
1. ViewModel must observe SavedStateHandle for location results
2. LocationPickerScreen should receive initial location
3. Proper cleanup of SavedStateHandle after consuming data

**Priority:** 🔴 CRITICAL - This breaks a core feature!

---

**Analysis Date**: February 23, 2026  
**Bug Severity**: HIGH  
**Status**: CONFIRMED & DOCUMENTED
