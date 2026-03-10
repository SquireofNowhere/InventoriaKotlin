package com.inventoria.app.ui.screens.map

import android.content.Context
import android.graphics.drawable.Drawable
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.inventoria.app.R
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
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun InventoryMapScreen(
    onItemClick: (Long) -> Unit,
    initialLocation: Pair<Double, Double>? = null,
    viewModel: InventoryListViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val defaultLocation = GeoPoint(-26.2041, 28.0473)
    val streetLevelZoom = 18.5

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val locationPermissionState = rememberPermissionState(
        android.Manifest.permission.ACCESS_FINE_LOCATION
    )

    var currentZoom by remember { mutableDoubleStateOf(10.0) }
    val isZoomedIn by remember { derivedStateOf { currentZoom > 16.0 } }

    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(10.0)
            controller.setCenter(defaultLocation)
        }
    }

    val myLocationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            setEnableAutoStop(false)
        }
    }

    LaunchedEffect(mapView, myLocationOverlay) {
        mapView.addMapListener(DelayedMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                myLocationOverlay.disableFollowLocation()
                return true
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                event?.let { currentZoom = it.zoomLevel }
                return true
            }
        }, 100))
    }

    LaunchedEffect(initialLocation) {
        initialLocation?.let { (lat, lon) ->
            mapView.controller.setZoom(streetLevelZoom)
            mapView.controller.animateTo(GeoPoint(lat, lon))
        }
    }

    LaunchedEffect(locationPermissionState.status.isGranted) {
        if (locationPermissionState.status.isGranted) {
            myLocationOverlay.enableMyLocation()
            if (!mapView.overlays.contains(myLocationOverlay)) {
                mapView.overlays.add(myLocationOverlay)
            }
            myLocationOverlay.runOnFirstFix {
                myLocationOverlay.myLocation?.let { loc ->
                    viewModel.updateUserLocation(loc.latitude, loc.longitude)
                }
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (locationPermissionState.status.isGranted) {
                        myLocationOverlay.myLocation?.let { loc ->
                            mapView.controller.setZoom(streetLevelZoom)
                            mapView.controller.animateTo(loc)
                        }
                    } else {
                        locationPermissionState.launchPermissionRequest()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                factory = { mapView },
                modifier = Modifier.fillMaxSize()
            )

            if (uiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = com.inventoria.app.ui.theme.PurplePrimary
                )
            }

            // Sync Markers with UI State
            val itemsToShow = remember(uiState.filteredItems) {
                uiState.filteredItems.filter { !it.equipped && it.parentId == null }
            }

            val markerDrawable = remember { ContextCompat.getDrawable(context, R.drawable.map_marker_dot) }

            LaunchedEffect(itemsToShow, isZoomedIn) {
                // Clear existing markers
                val existingMarkers = mapView.overlays.filterIsInstance<Marker>()
                existingMarkers.forEach { it.closeInfoWindow() }
                mapView.overlays.removeAll(existingMarkers)

                // Add new markers
                itemsToShow.forEach { item ->
                    val point = if (item.latitude != null && item.longitude != null) {
                        GeoPoint(item.latitude!!, item.longitude!!)
                    } else {
                        parseLocationToGeoPoint(item.location)
                    }

                    if (point != null) {
                        val marker = Marker(mapView)
                        marker.position = point
                        marker.title = item.name
                        marker.snippet = "Qty: ${item.quantity}\n${item.location}"
                        marker.setIcon(markerDrawable)
                        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        
                        marker.setOnMarkerClickListener { m, _ ->
                            if (m.isInfoWindowShown) {
                                onItemClick(item.id)
                            } else {
                                m.showInfoWindow()
                            }
                            true
                        }
                        
                        mapView.overlays.add(marker)
                        if (isZoomedIn) {
                            marker.showInfoWindow()
                        }
                    }
                }
                mapView.invalidate()
            }
        }
    }
}

private fun parseLocationToGeoPoint(location: String): GeoPoint? {
    if (location.isEmpty()) return null
    return try {
        val parts = location.split(",")
        if (parts.size != 2) return null
        val lat = parts[0].trim().toDoubleOrNull()
        val lon = parts[1].trim().toDoubleOrNull()
        if (lat == null || lon == null) null
        else GeoPoint(lat, lon)
    } catch (e: Exception) {
        Log.e("InventoryMap", "parseLocationToGeoPoint: Error parsing '$location'", e)
        null
    }
}
