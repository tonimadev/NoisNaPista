package digital.tonima.noisnapista.feature.tracker.impl

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import digital.tonima.noisnapista.core.model.LocationPoint
import digital.tonima.noisnapista.core.model.Pothole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SAO_PAULO = LatLng(-23.5505, -46.6333)

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    viewModel: TrackerViewModel,
    onNavigateToMap: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val locationPermissionsState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
    )

    val notificationPermissionState = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        null
    }

    LaunchedEffect(locationPermissionsState.allPermissionsGranted) {
        viewModel.onIntent(
            TrackerUiIntent.TogglePermission(
                TrackerUiIntent.PermissionType.LOCATION,
                locationPermissionsState.allPermissionsGranted
            )
        )
    }

    LaunchedEffect(notificationPermissionState?.status?.isGranted) {
        viewModel.onIntent(
            TrackerUiIntent.TogglePermission(
                TrackerUiIntent.PermissionType.NOTIFICATION,
                notificationPermissionState?.status?.isGranted ?: true
            )
        )
    }

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is TrackerUiEffect.ShowError -> {
                    Toast.makeText(context, context.getString(effect.messageRes), Toast.LENGTH_SHORT).show()
                }
                is TrackerUiEffect.NavigateTo -> {
                    // TODO: Implement navigation if needed
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Column {
                            Text(stringResource(R.string.tracker_app_title), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.tracker_app_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            StatusRow(isTracking = uiState.isTracking)

            Spacer(modifier = Modifier.height(12.dp))

            SensorIntensityBar(intensity = uiState.sensorIntensity, isTracking = uiState.isTracking)

            Spacer(modifier = Modifier.height(12.dp))

            HomeMap(
                potholes = uiState.detectedPotholes,
                currentLocation = uiState.currentLocation
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = stringResource(R.string.tracker_current_location_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = uiState.currentLocation?.let {
                    stringResource(R.string.tracker_lat_lon_format, it.latitude, it.longitude)
                } ?: stringResource(R.string.tracker_waiting_gps),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            TrackingButton(
                isTracking = uiState.isTracking,
                onClick = {
                    if (uiState.isTracking) {
                        viewModel.onIntent(TrackerUiIntent.StopTracking)
                    } else {
                        if (!locationPermissionsState.allPermissionsGranted) {
                            locationPermissionsState.launchMultiplePermissionRequest()
                        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && notificationPermissionState?.status?.isGranted == false) {
                            notificationPermissionState.launchPermissionRequest()
                        } else {
                            viewModel.onIntent(TrackerUiIntent.StartTracking)
                        }
                    }
                }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = stringResource(R.string.tracker_recent_activity_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            )

            RecentActivityList(potholes = uiState.detectedPotholes.take(5))
        }
    }
}

@Composable
private fun StatusRow(isTracking: Boolean) {
    val statusColor = if (isTracking) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row {
        Text(
            text = stringResource(R.string.tracker_gps_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(if (isTracking) R.string.tracker_gps_active else R.string.tracker_gps_inactive),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
        Text(
            text = stringResource(R.string.tracker_sensors_label),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = stringResource(if (isTracking) R.string.tracker_sensors_on else R.string.tracker_sensors_off),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = statusColor
        )
    }
}

// Same 15 m/s² line PotholeDetector uses to decide "this is a pothole" — the bar turns red at
// exactly the value that would trigger a detection, so it's a direct visual explanation of why.
private const val IMPACT_THRESHOLD = 15f
private const val WARNING_THRESHOLD = 10f
private const val MAX_SENSOR_SCALE = 30f

@Composable
private fun SensorIntensityBar(intensity: Float, isTracking: Boolean) {
    val fraction by animateFloatAsState(
        targetValue = if (isTracking) (intensity / MAX_SENSOR_SCALE).coerceIn(0f, 1f) else 0f,
        label = "sensorIntensityFraction"
    )
    val barColor by animateColorAsState(
        targetValue = when {
            !isTracking -> MaterialTheme.colorScheme.onSurfaceVariant
            intensity >= IMPACT_THRESHOLD -> MaterialTheme.colorScheme.error
            intensity >= WARNING_THRESHOLD -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.tertiary
        },
        label = "sensorIntensityColor"
    )

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tracker_sensor_reading_label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when {
                    !isTracking -> stringResource(R.string.common_placeholder_dash)
                    intensity >= IMPACT_THRESHOLD -> stringResource(R.string.tracker_impact_label)
                    else -> stringResource(R.string.tracker_sensor_value_format, intensity)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .clip(RoundedCornerShape(7.dp))
                    .background(barColor)
            )
        }
    }
}

@Composable
private fun HomeMap(potholes: List<Pothole>, currentLocation: LocationPoint?) {
    val youAreHereLabel = stringResource(R.string.tracker_marker_you_are_here)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(SAO_PAULO, 14f)
    }

    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(it.latitude, it.longitude),
                16f
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .clip(RoundedCornerShape(16.dp))
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
        ) {
            currentLocation?.let {
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(it.latitude, it.longitude)),
                    title = youAreHereLabel,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
            potholes.forEach { pothole ->
                val hue = when {
                    pothole.severity > 20f -> BitmapDescriptorFactory.HUE_RED
                    pothole.severity > 10f -> BitmapDescriptorFactory.HUE_ORANGE
                    else -> BitmapDescriptorFactory.HUE_YELLOW
                }
                Marker(
                    state = rememberUpdatedMarkerState(
                        position = LatLng(pothole.location.latitude, pothole.location.longitude)
                    ),
                    title = stringResource(R.string.tracker_marker_pothole_severity_format, pothole.severity.toString()),
                    icon = BitmapDescriptorFactory.defaultMarker(hue)
                )
            }
        }
    }
}

@Composable
private fun TrackingButton(isTracking: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isTracking) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            contentColor = if (isTracking) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Text(
            stringResource(if (isTracking) R.string.tracker_stop_button else R.string.tracker_start_button),
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun RecentActivityList(potholes: List<Pothole>) {
    if (potholes.isEmpty()) {
        Text(
            stringResource(R.string.common_no_pothole_detected),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        potholes.forEach { pothole ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = stringResource(
                            R.string.tracker_recent_item_format,
                            timeFormat.format(Date(pothole.timestamp)),
                            pothole.location.latitude,
                            pothole.location.longitude
                        ),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}
