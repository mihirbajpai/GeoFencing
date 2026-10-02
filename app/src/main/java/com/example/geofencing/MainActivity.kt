package com.example.geofencing

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.geofencing.location.LocationTrackingService
import com.example.geofencing.shared.location.createLocationProvider
import com.example.geofencing.shared.storage.createLocalStore
import com.example.geofencing.shared.viewmodel.MapViewModel
import com.example.geofencing.ui.MapScreen
import com.example.geofencing.ui.theme.GeoFencingTheme
import com.example.geofencing.util.Permissions

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val locations = createLocationProvider(applicationContext)

        setContent {
            GeoFencingTheme {
                val vm = viewModel { MapViewModel(createLocalStore(applicationContext), locations) }
                val state by vm.uiState.collectAsStateWithLifecycle()

                var hasLocation by remember { mutableStateOf(locations.hasPermission()) }
                val permissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { hasLocation = locations.hasPermission() }
                val requestPermissions = { permissionLauncher.launch(Permissions.required) }

                LaunchedEffect(state.needsName, hasLocation) {
                    when {
                        state.needsName -> Unit
                        hasLocation -> LocationTrackingService.start(this@MainActivity)
                        else -> requestPermissions()
                    }
                }

                MapScreen(state, vm, hasLocation, requestPermissions)
            }
        }
    }
}
