package com.inventoria.app.ui.screens.map

import android.Manifest
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.preference.PreferenceManager
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.inventoria.app.R
import com.inventoria.app.ui.screens.inventory.InventoryListViewModel
import com.inventoria.app.ui.theme.PurplePrimary
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

    val streetLevelZoom = 18.5

    LaunchedEffect(Unit) {
        Configuration.getInstance().load(context, PreferenceManager.getDefaultSharedPreferences(context))
        Configuration.getInstance().userAgentValue = context.packageName
    }

    val locationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_FINE_LOCATION
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
        val provider = GpsMyLocationProvider(context)
        MyLocationNewOverlay(provider, mapView).apply {
            setEnableAutoStop(false)
        }
    }

    // Move listener setup here to ensure myLocationOverlay is already defined
    LaunchedEffect(mapView, myLocationOverlay) {
        mapView.addMapListener(DelayedMapListener(object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                // When user scrolls, disable follow-me mode
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
                val loc = myLocationOverlay.myLocation
                if (loc != null) {
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
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Inventory Map", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (locationPermissionState.status.isGranted) {
                        val myLocation = myLocationOverlay.myLocation
                        if (myLocation != null) {
                            // Just animate to the location, don't lock it
                            mapView.controller.setZoom(streetLevelZoom)
                            mapView.controller.animateTo(myLocation)
                        } 
                    } else {
                        locationPermissionState.launchPermissionRequest()
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "Find Me")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView }
            )
            
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(PurplePrimary))
                    Text(
                        text = "Inventory Items",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            LaunchedEffect(uiState.filteredItems) {
                try {
                    val existingMarkers = mapView.overlays.filterIsInstance<Marker>()
                    existingMarkers.forEach { it.closeInfoWindow() }
                    mapView.overlays.removeAll(existingMarkers)

                    val markerDrawable = ContextCompat.getDrawable(context, R.drawable.map_marker_dot)
                    
                    val itemsToShow = uiState.filteredItems.filter { !it.equipped && it.parentId == null }

                    itemsToShow.forEach { item ->
                        val lat = item.latitude
                        val lon = item.longitude
                        val coords = if (lat != null && lon != null) GeoPoint(lat, lon) else parseLocationToGeoPoint(item.location)
                        
                        if (coords != null) {
                            val itemId = item.id
                            val marker = Marker(mapView).apply {
                                position = coords
                                title = item.name
                                snippet = "Qty: ${item.quantity}\n${item.location}"
                                icon = markerDrawable
                                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                            }
                            
                            marker.infoWindow = object : MarkerInfoWindow(org.osmdroid.library.R.layout.bonuspack_bubble, mapView) {
                                override fun onOpen(item: Any?) {
                                    super.onOpen(item)
                                    view.setOnClickListener {
                                        onItemClick(itemId)
                                        close()
                                    }
                                }
                            }

                            if (isZoomedIn) {
                                marker.showInfoWindow()
                            }

                            marker.setOnMarkerClickListener { m, _ ->
                                if (m.isInfoWindowShown) {
                                    onItemClick(itemId)
                                } else {
                                    m.showInfoWindow()
                                }
                                true
                            }
                            mapView.overlays.add(marker)
                        }
                    }
                    mapView.invalidate()
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
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = PurplePrimary
                )
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
