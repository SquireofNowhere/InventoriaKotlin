package com.inventoria.app.ui.screens.map

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.inventoria.app.ui.screens.inventory.InventoryListViewModel
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryMapScreen(
    onItemClick: (Long) -> Unit,
    viewModel: InventoryListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val defaultLocation = GeoPoint(-26.2041, 28.0473) // Johannesburg

    // Create MapView once and remember it
    val mapView = remember {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(10.0)
            controller.setCenter(defaultLocation)
        }
    }

    // Lifecycle handling for MapView
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
            TopAppBar(title = { Text("Inventory Map", fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { mapView }
            )
            
            // Re-render markers when items change
            LaunchedEffect(uiState.filteredItems) {
                mapView.overlays.clear()
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
