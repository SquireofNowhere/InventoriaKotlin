package com.inventoria.app.ui.screens.map

import android.Manifest
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
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun InventoryMapScreen(
    onItemClick: (Long) -> Unit,
    viewModel: InventoryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val defaultLocation = GeoPoint(-26.2041, 28.0473) // Johannesburg

    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    // Create MapView once and remember it
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(10.0)
            controller.setCenter(defaultLocation)
        }
    }

    // Create and remember MyLocationOverlay
    val myLocationOverlay = remember {
        val provider = GpsMyLocationProvider(context)
        MyLocationNewOverlay(provider, mapView).apply {
            enableMyLocation()
        }
    }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            if (!mapView.overlays.contains(myLocationOverlay)) {
                mapView.overlays.add(myLocationOverlay)
            }
        }
    }

    // Lifecycle handling for MapView
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
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
                    if (locationPermissionState.status.isGranted) {
                        myLocationOverlay.enableFollowLocation()
                        val myLocation = myLocationOverlay.myLocation
                        if (myLocation != null) {
                            mapView.controller.setZoom(15.0) // Approximately 1.5km across
                            mapView.controller.animateTo(myLocation)
                        } else {
                            // If location not yet found, at least enable following
                            myLocationOverlay.enableFollowLocation()
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
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView }
            )
            
            // Re-render markers when items change
            LaunchedEffect(uiState.filteredItems) {
                // Clear all except myLocationOverlay if it exists
                val overlaysToRemove = mapView.overlays.filter { it != myLocationOverlay }
                mapView.overlays.removeAll(overlaysToRemove)
                
                uiState.filteredItems.forEach { item ->
                    val coords = parseLocationToGeoPoint(item.location)
                    if (coords != null) {
                        val marker = Marker(mapView)
                        marker.position = coords
                        marker.title = item.name
                        marker.snippet = "Qty: ${item.quantity} | ${item.location}"
                        marker.setOnMarkerClickListener { m, _ ->
                            onItemClick(item.id)
                            true
                        }
                        mapView.overlays.add(marker)
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
            GeoPoint(parts[0].trim().toDouble(), parts[1].trim().toDouble())
        } else null
    } catch (e: Exception) {
        null
    }
}
