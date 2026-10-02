package com.example.geofencing.ui

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.geofencing.shared.geo.bounds
import com.example.geofencing.shared.model.MemberStatus
import com.example.geofencing.shared.viewmodel.FenceDraft
import com.example.geofencing.shared.viewmodel.MapUiState
import com.example.geofencing.shared.viewmodel.MapViewModel
import com.example.geofencing.shared.viewmodel.MemberUi
import com.example.geofencing.ui.theme.DraftFenceColor
import com.example.geofencing.ui.theme.FenceColor
import com.example.geofencing.ui.theme.InsideColor
import com.example.geofencing.ui.theme.OutsideColor
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.Circle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import kotlin.math.roundToInt

/** Main screen: map with the fence, admin controls and the member list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(
    state: MapUiState,
    vm: MapViewModel,
    hasLocation: Boolean,
    onRequestPermission: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var showPin by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            vm.messageShown()
        }
    }

    if (state.needsName) NameDialog(onSave = vm::saveName)
    if (showPin) AdminPinDialog(onSubmit = vm::unlockAdmin, onDismiss = { showPin = false })
    if (showDelete) ConfirmDeleteDialog(
        onConfirm = vm::deleteFence,
        onDismiss = { showDelete = false })

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GeoFencing") },
                actions = {
                    if (state.isAdmin) TextButton(onClick = vm::exitAdmin) { Text("Exit admin") }
                    else TextButton(onClick = { showPin = true }) { Text("Admin") }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            FenceMap(state, vm, hasLocation, Modifier.weight(1f))
            Surface(tonalElevation = 3.dp) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (!hasLocation) PermissionBanner(onRequestPermission)
                    val draft = state.draft
                    if (draft != null) {
                        DraftEditor(draft, vm, hasLocation)
                    } else {
                        FenceSummary(state)
                        if (state.isAdmin) AdminActions(state, vm, onDelete = { showDelete = true })
                    }
                    HorizontalDivider()
                    MemberList(state.members)
                }
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
private fun FenceMap(
    state: MapUiState,
    vm: MapViewModel,
    hasLocation: Boolean,
    modifier: Modifier
) {
    val camera = rememberCameraPositionState()
    var loaded by remember { mutableStateOf(false) }
    var centered by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(loaded, state.fence?.version, hasLocation) {
        if (!loaded) return@LaunchedEffect
        val fence = state.fence
        val update = if (fence != null && state.draft == null) {
            val b = fence.bounds()
            CameraUpdateFactory.newLatLngBounds(
                LatLngBounds(
                    LatLng(b.south, b.west),
                    LatLng(b.north, b.east)
                ), 120
            )
        } else if (!centered && hasLocation) {
            vm.myLocation()?.let { CameraUpdateFactory.newLatLngZoom(LatLng(it.lat, it.lng), 16f) }
        } else null
        update ?: return@LaunchedEffect
        centered = true
        camera.animate(update, 800)
    }

    GoogleMap(
        modifier = modifier.fillMaxWidth(),
        cameraPositionState = camera,
        properties = MapProperties(isMyLocationEnabled = hasLocation),
        uiSettings = MapUiSettings(
            myLocationButtonEnabled = hasLocation,
            zoomControlsEnabled = false
        ),
        onMapLoaded = { loaded = true },
        onMapClick = { vm.setDraftCenter(it.latitude, it.longitude) },
    ) {
        val draft = state.draft
        val fence = state.fence
        if (draft != null) {
            val lat = draft.lat
            val lng = draft.lng
            if (lat != null && lng != null) FenceCircle(
                LatLng(lat, lng),
                draft.radiusMeters,
                DraftFenceColor
            )
        } else if (fence != null) {
            FenceCircle(LatLng(fence.lat, fence.lng), fence.radiusMeters, FenceColor)
        }
    }
}

@Composable
private fun FenceCircle(center: LatLng, radius: Double, color: Color) {
    Circle(
        center = center,
        radius = radius,
        fillColor = color.copy(alpha = 0.18f),
        strokeColor = color,
        strokeWidth = 4f,
    )
    Marker(state = rememberUpdatedMarkerState(center))
}

@Composable
private fun PermissionBanner(onGrant: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            "Location permission is needed for tracking.",
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onGrant) { Text("Grant") }
    }
}

@Composable
private fun FenceSummary(state: MapUiState) {
    val fence = state.fence
    Text(
        if (fence == null) "No fence yet. Ask the admin to create one."
        else "Fence radius: ${fence.radiusMeters.roundToInt()} m, checked every ${state.intervalSec}s"
    )
}

@Composable
private fun DraftEditor(draft: FenceDraft, vm: MapViewModel, hasLocation: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            if (draft.lat == null) "Tap the map to place the center" else "Tap the map to move the center",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = vm::useMyLocation, enabled = hasLocation) { Text("Use my location") }
    }
    Text("Radius: ${draft.radiusMeters.roundToInt()} m")
    Slider(
        value = draft.radiusMeters.toFloat(),
        onValueChange = { vm.setDraftRadius(it.toDouble()) },
        valueRange = vm.radiusRange.start.toFloat()..vm.radiusRange.endInclusive.toFloat(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::saveDraft, enabled = draft.lat != null) { Text("Save fence") }
        OutlinedButton(onClick = vm::cancelDraft) { Text("Cancel") }
    }
}

@Composable
private fun AdminActions(state: MapUiState, vm: MapViewModel, onDelete: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::startDraft) { Text(if (state.fence == null) "Create fence" else "Edit fence") }
        OutlinedButton(onClick = onDelete, enabled = state.fence != null) { Text("Delete fence") }
    }
    var interval by remember(state.intervalSec) { mutableStateOf(state.intervalSec.toString()) }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = interval,
            onValueChange = { if (it.length <= 3 && it.all(Char::isDigit)) interval = it },
            label = { Text("Location interval (sec)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = { vm.setInterval(interval) },
            enabled = interval != state.intervalSec.toString()
        ) {
            Text("Set")
        }
    }
}

@Composable
private fun MemberList(members: List<MemberUi>) {
    Text("Members (${members.size})", style = MaterialTheme.typography.titleSmall)
    Column(
        Modifier
            .heightIn(max = 160.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        members.forEach { m ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    if (m.isMe) "${m.name} (you)" else m.name,
                    fontWeight = if (m.isMe) FontWeight.SemiBold else null,
                    modifier = Modifier.weight(1f),
                )
                StatusChip(m.status)
            }
        }
    }
}

@Composable
private fun StatusChip(status: MemberStatus) {
    val (label, color) = when (status) {
        MemberStatus.INSIDE -> "Inside" to InsideColor
        MemberStatus.OUTSIDE -> "Outside" to OutsideColor
        MemberStatus.UNKNOWN -> "Unknown" to Color.Gray
    }
    Text(
        label,
        color = color,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 2.dp),
    )
}
