package com.inventoria.app.ui.screens.map

import android.Manifest
import android.preference.PreferenceManager
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.inventoria.app.ui.screens.inventory.InventoryListViewModel
import org.osmdroid.config.Configuration
import org.osmdroid.events.DelayedMapListener
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.infowindow.MarkerInfoWindow
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun InventoryMapScreen(
    onItemClick: (Long) -> Unit,
    initialLocation: Pair<Double, Double>? = null,
    viewModel: InventoryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val defaultLocation = GeoPoint(-26.2041, 28.0473)

    Log.d("InventoryMap", "InventoryMapScreen: Composition started")

    // Important for osmdroid: Initialize configuration
    LaunchedEffect(Unit) {
        try {
            Log.d("InventoryMap", "LaunchedEffect[Unit]: Initializing osmdroid config")
            Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
            Configuration.getInstance().userAgentValue = context.packageName
            Log.d("InventoryMap", "LaunchedEffect[Unit]: Config initialized for ${context.packageName}")
        } catch (e: Exception) {
            Log.e("InventoryMap", "LaunchedEffect[Unit]: Error initializing config", e)
        }
    }

    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    var currentZoom by remember { mutableStateOf(10.0) }
    val isZoomedIn = currentZoom > 15.0

    val mapView = remember {
        try {
            Log.d("InventoryMap", "remember: Creating MapView")
            MapView(context).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                setMultiTouchControls(true)
                controller.setZoom(10.0)
                controller.setCenter(defaultLocation)
                
                addMapListener(DelayedMapListener(object : MapListener {
                    override fun onScroll(event: ScrollEvent?): Boolean = false
                    override fun onZoom(event: ZoomEvent?): Boolean {
                        event?.let { currentZoom = it.zoomLevel }
                        return true
                    }
                }, 100))
                Log.d("InventoryMap", "remember: MapView created and listener added")
            }
        } catch (e: Exception) {
            Log.e("InventoryMap", "remember: Error creating MapView", e)
            throw e // Re-throw to see the stack trace in Logcat
        }
    }

    val myLocationOverlay = remember {
        try {
            Log.d("InventoryMap", "remember: Creating MyLocationNewOverlay")
            val provider = GpsMyLocationProvider(context)
            MyLocationNewOverlay(provider, mapView).apply {
                Log.d("InventoryMap", "remember: MyLocationNewOverlay created")
            }
        } catch (e: Exception) {
            Log.e("InventoryMap", "remember: Error creating myLocationOverlay", e)
            throw e
        }
    }

    LaunchedEffect(initialLocation) {
        Log.d("InventoryMap", "LaunchedEffect[initialLocation]: initialLocation=$initialLocation")
        initialLocation?.let { (lat, lon) ->
            mapView.controller.setZoom(17.0)
            mapView.controller.animateTo(GeoPoint(lat, lon))
        }
    }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        Log.d("InventoryMap", "LaunchedEffect[permission]: isGranted=${locationPermissionState.status.isGranted}")
        if (locationPermissionState.status.isGranted) {
            try {
                myLocationOverlay.enableMyLocation()
                myLocationOverlay.runOnFirstFix {
                    val loc = myLocationOverlay.myLocation
                    Log.d("InventoryMap", "runOnFirstFix: location=$loc")
                    if (loc != null) {
                        viewModel.updateUserLocation(loc.latitude, loc.longitude)
                    }
                }
            } catch (e: Exception) {
                Log.e("InventoryMap", "LaunchedEffect[permission]: Error enabling location", e)
            }
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            val loc = myLocationOverlay.myLocation
            if (loc != null) {
                viewModel.updateUserLocation(loc.latitude, loc.longitude)
            }
            kotlinx.coroutines.delay(5000)
        }
    }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            if (!mapView.overlays.contains(myLocationOverlay)) {
                Log.d("InventoryMap", "Adding myLocationOverlay to mapView")
                mapView.overlays.add(myLocationOverlay)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        Log.d("InventoryMap", "DisposableEffect: Adding lifecycle observer")
        val observer = LifecycleEventObserver { _, event ->
            Log.d("InventoryMap", "Lifecycle Event: $event")
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    mapView.onResume()
                    if (locationPermissionState.status.isGranted) {
                        myLocationOverlay.enableMyLocation()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    mapView.onPause()
                    myLocationOverlay.disableMyLocation()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            Log.d("InventoryMap", "DisposableEffect: Disposing")
            lifecycleOwner.lifecycle.removeObserver(observer)
            mapView.onDetach()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Inventory Map", fontWeight = FontWeight.Bold) })
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    Log.d("InventoryMap", "FAB Clicked: Permission granted=${locationPermissionState.status.isGranted}")
                    if (locationPermissionState.status.isGranted) {
                        myLocationOverlay.enableFollowLocation()
                        val myLocation = myLocationOverlay.myLocation
                        if (myLocation != null) {
                            mapView.controller.setZoom(15.0)
                            mapView.controller.animateTo(myLocation)
                        } else {
                            mapView.controller.setZoom(15.0)
                        }
                    } else {
                        locationPermissionState.launchPermissionRequest()
                    }
                },
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Log.d("InventoryMap", "Rendering AndroidView")
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { 
                    Log.d("InventoryMap", "AndroidView: Factory called")
                    mapView 
                }
            )
            
            LaunchedEffect(uiState.filteredItems) {
                Log.d("InventoryMap", "LaunchedEffect[filteredItems]: Item count=${uiState.filteredItems.size}")
                try {
                    val existingMarkers = mapView.overlays.filterIsInstance<Marker>()
                    existingMarkers.forEach { it.closeInfoWindow() }
                    mapView.overlays.removeAll(existingMarkers)
                    
                    val itemsToShow = uiState.filteredItems.filter { !it.isEquipped && it.parentId == null }
                    Log.d("InventoryMap", "Processing ${itemsToShow.size} markers")

                    itemsToShow.forEach { item ->
                        val coords = if (item.latitude != null && item.longitude != null) {
                            GeoPoint(item.latitude, item.longitude)
                        } else {
                            parseLocationToGeoPoint(item.location)
                        }
                        
                        if (coords != null) {
                            val marker = Marker(mapView)
                            marker.position = coords
                            marker.title = item.name
                            marker.snippet = "Qty: ${item.quantity}\n${item.location}"
                            
                            marker.infoWindow = object : MarkerInfoWindow(org.osmdroid.library.R.layout.bonuspack_bubble, mapView) {
                                override fun onOpen(item: Any?) {
                                    super.onOpen(item)
                                    view.setOnClickListener {
                                        onItemClick(item as? Long ?: 0L) // Fixed potentially unsafe cast
                                        close()
                                    }
                                }
                            }

                            if (isZoomedIn) {
                                marker.showInfoWindow()
                            }

                            marker.setOnMarkerClickListener { m, _ ->
                                if (m.isInfoWindowShown) {
                                    onItemClick(item.id)
                                } else {
                                    m.showInfoWindow()
                                }
                                true
                            }
                            mapView.overlays.add(marker)
                        }
                    }
                    mapView.invalidate()
                    Log.d("InventoryMap", "Markers updated and map invalidated")
                } catch (e: Exception) {
                    Log.e("InventoryMap", "Error processing markers", e)
                }
            }

            LaunchedEffect(isZoomedIn) {
                mapView.overlays.filterIsInstance<Marker>().forEach { marker ->
                    if (isZoomedIn) {
                        marker.showInfoWindow()
                    } else {
                        marker.closeInfoWindow()
                    }
                }
                mapView.invalidate()
            }
            
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

private fun parseLocationToGeoPoint(location: String): GeoPoint? {
    return try {
        val parts = location.split(",")
        if (parts.size == 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lon = parts[1].trim().toDoubleOrNull()
            if (lat != null && lon != null) GeoPoint(lat, lon) else null
        } else null
    } catch (e: Exception) {
        Log.e("InventoryMap", "parseLocationToGeoPoint: Error parsing '$location'", e)
        null
    }
}
