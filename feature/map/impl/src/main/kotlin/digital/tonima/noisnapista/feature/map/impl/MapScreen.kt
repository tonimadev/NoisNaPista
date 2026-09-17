package digital.tonima.noisnapista.feature.map.impl

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import java.util.Date

@Composable
fun MapScreen(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier
) {
    val potholes by viewModel.potholes.collectAsStateWithLifecycle()
    val communityPotholes by viewModel.communityPotholes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-23.5505, -46.6333), 10f)
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is MapUiEffect.ShowMessage -> snackbarHostState.showSnackbar(context.getString(effect.messageRes))
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.refreshCommunityPotholes() }) {
                Icon(Icons.Rounded.Refresh, contentDescription = stringResource(R.string.map_refresh_community_cd))
            }
        }
    ) { padding ->
        val pendingStatusFallback = stringResource(R.string.map_status_pending_fallback)
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            cameraPositionState = cameraPositionState
        ) {
            potholes.forEach { pothole ->
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(pothole.location.latitude, pothole.location.longitude)),
                    title = stringResource(R.string.map_marker_own_format, pothole.severity.toString()),
                    snippet = stringResource(R.string.map_marker_detected_at_format, Date(pothole.timestamp).toString()),
                    icon = BitmapDescriptorFactory.defaultMarker(severityHue(pothole.severity))
                )
            }
            communityPotholes.forEach { pothole ->
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(pothole.location.latitude, pothole.location.longitude)),
                    title = stringResource(R.string.map_marker_community_format, pothole.status ?: pendingStatusFallback),
                    snippet = stringResource(R.string.map_report_count_format, pothole.distinctReporterCount),
                    // Violet marks these as community-sourced pins, distinct from this device's own detections.
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_VIOLET)
                )
            }
        }
    }
}

private fun severityHue(severity: Float): Float = when {
    severity > 20f -> BitmapDescriptorFactory.HUE_RED
    severity > 10f -> BitmapDescriptorFactory.HUE_ORANGE
    else -> BitmapDescriptorFactory.HUE_YELLOW
}
