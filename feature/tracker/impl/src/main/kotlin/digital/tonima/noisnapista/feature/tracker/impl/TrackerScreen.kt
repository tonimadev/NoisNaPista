package digital.tonima.noisnapista.feature.tracker.impl

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
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
    modifier: Modifier = Modifier,
    // Falso no painel esquerdo do layout de tela expandida (ver MainActivity), onde o mapa
    // completo já aparece ao lado em MapScreen — evitar duas cópias do mesmo mapa e usar o
    // espaço liberado para o histórico de atividade recente.
    showEmbeddedMap: Boolean = true
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

    // Um único toque em "Iniciar" deve bastar mesmo quando faltam duas permissões: o clique liga
    // este flag e este efeito reage a cada permissão concedida, pedindo a próxima ou (quando não
    // falta mais nenhuma) disparando o start de verdade — sem isso, cada permissão concedida só
    // avançava um passo e exigia um novo clique manual do usuário para o passo seguinte.
    var pendingStartAfterPermissions by remember { mutableStateOf(false) }
    LaunchedEffect(
        pendingStartAfterPermissions,
        locationPermissionsState.allPermissionsGranted,
        notificationPermissionState?.status?.isGranted
    ) {
        if (!pendingStartAfterPermissions) return@LaunchedEffect
        if (!locationPermissionsState.allPermissionsGranted) {
            locationPermissionsState.launchMultiplePermissionRequest()
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && notificationPermissionState?.status?.isGranted == false) {
            notificationPermissionState.launchPermissionRequest()
        } else {
            pendingStartAfterPermissions = false
            viewModel.onIntent(TrackerUiIntent.StartTracking)
        }
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

            SensorReadingPanel(
                x = uiState.sensorX,
                y = uiState.sensorY,
                z = uiState.sensorZ,
                verticalIntensity = uiState.sensorIntensity,
                isTracking = uiState.isTracking
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (showEmbeddedMap) {
                HomeMap(
                    potholes = uiState.detectedPotholes,
                    currentLocation = uiState.currentLocation
                )

                Spacer(modifier = Modifier.height(12.dp))
            }

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
                        pendingStartAfterPermissions = true
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

            RecentActivityList(
                potholes = uiState.detectedPotholes,
                modifier = Modifier.weight(1f)
            )
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

// Same 15 m/s² line PotholeDetector uses to decide "this is a pothole" — the header status text
// turns red at exactly the value that would trigger a detection. That value is the
// gravity-corrected vertical acceleration (see PotholeDetector.computeVerticalAcceleration), not
// simply abs(z) — a bump can land mostly on the raw X or Y axis if the phone isn't mounted flat,
// so the X/Y/Z chips and gizmo below are shown as fixed-color raw context only.
private const val IMPACT_THRESHOLD = 15f
private const val WARNING_THRESHOLD = 10f
private const val MAX_SENSOR_SCALE = 30f

private val AXIS_X_COLOR = Color(0xFF4FC3F7)
private val AXIS_Y_COLOR = Color(0xFF81C784)
private val AXIS_Z_COLOR = Color(0xFFBA68C8)

// Fixed isometric projection directions for a simple 3-axis "gizmo": Z straight up, X/Y splayed
// 30° down to either side — the classic isometric-cube look, cheap to draw with plain lines
// instead of pulling in a 3D rendering library for three numbers.
private val AXIS_X_DIR = Offset(0.866f, 0.5f)
private val AXIS_Y_DIR = Offset(-0.866f, 0.5f)
private val AXIS_Z_DIR = Offset(0f, -1f)

@Composable
private fun SensorReadingPanel(x: Float, y: Float, z: Float, verticalIntensity: Float, isTracking: Boolean) {
    val intensityColor by animateColorAsState(
        targetValue = when {
            !isTracking -> MaterialTheme.colorScheme.onSurfaceVariant
            verticalIntensity >= IMPACT_THRESHOLD -> MaterialTheme.colorScheme.error
            verticalIntensity >= WARNING_THRESHOLD -> MaterialTheme.colorScheme.primary
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
                    verticalIntensity >= IMPACT_THRESHOLD -> stringResource(R.string.tracker_impact_label)
                    else -> stringResource(R.string.tracker_sensor_value_format, verticalIntensity)
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = intensityColor
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            AxisReadingChip(stringResource(R.string.tracker_axis_x), x, AXIS_X_COLOR)
            AxisReadingChip(stringResource(R.string.tracker_axis_y), y, AXIS_Y_COLOR)
            AxisReadingChip(stringResource(R.string.tracker_axis_z), z, AXIS_Z_COLOR)
        }

        Spacer(modifier = Modifier.height(4.dp))

        SensorAxisGizmo(x = x, y = y, z = z)

        Text(
            text = stringResource(R.string.tracker_z_only_detection_caption),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

@Composable
private fun AxisReadingChip(axisLabel: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(RoundedCornerShape(50)).background(color))
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = stringResource(R.string.tracker_axis_value_format, axisLabel, value),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Draws raw X/Y/Z as three spikes from a shared origin along fixed isometric directions — a live
 * "3D" readout of the raw vector, instead of the three axes overlapping as line traces over time
 * (compare the debug/classification screen's post-hoc SensorWindowChart, which plots history and
 * so needs that line-chart shape; this widget only ever shows the current instant). Context only:
 * the value that actually decides "this is a pothole" is shown, dynamically colored, in the
 * header above — see SensorReadingPanel. */
@Composable
private fun SensorAxisGizmo(x: Float, y: Float, z: Float, modifier: Modifier = Modifier) {
    val guideColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
    val originColor = MaterialTheme.colorScheme.onSurfaceVariant

    Canvas(modifier = modifier.fillMaxWidth().height(150.dp)) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val guideLength = size.minDimension / 2.1f
        val scale = guideLength / MAX_SENSOR_SCALE

        fun drawGuide(direction: Offset) {
            drawLine(
                color = guideColor,
                start = center,
                end = center + direction * guideLength,
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
            )
        }
        drawGuide(AXIS_X_DIR)
        drawGuide(AXIS_Y_DIR)
        drawGuide(AXIS_Z_DIR)

        fun drawReading(direction: Offset, value: Float, color: Color) {
            val length = value.coerceIn(-MAX_SENSOR_SCALE, MAX_SENSOR_SCALE) * scale
            val tip = center + direction * length
            drawLine(color = color, start = center, end = tip, strokeWidth = 6.dp.toPx(), cap = StrokeCap.Round)
            drawCircle(color = color, radius = 5.dp.toPx(), center = tip)
        }
        drawReading(AXIS_X_DIR, x, AXIS_X_COLOR)
        drawReading(AXIS_Y_DIR, y, AXIS_Y_COLOR)
        drawReading(AXIS_Z_DIR, z, AXIS_Z_COLOR)

        drawCircle(color = originColor, radius = 4.dp.toPx(), center = center)
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
private fun RecentActivityList(potholes: List<Pothole>, modifier: Modifier = Modifier) {
    if (potholes.isEmpty()) {
        Text(
            stringResource(R.string.common_no_pothole_detected),
            modifier = modifier,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR")) }

    LazyColumn(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(potholes, key = { it.id }) { pothole ->
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
