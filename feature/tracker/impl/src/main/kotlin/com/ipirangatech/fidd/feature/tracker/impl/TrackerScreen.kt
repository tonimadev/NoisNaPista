package com.ipirangatech.fidd.feature.tracker.impl

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import com.ipirangatech.fidd.core.model.LocationPoint
import com.ipirangatech.fidd.core.model.Pothole
import com.ipirangatech.fidd.core.ui.PotholeMarker
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val SAO_PAULO = LatLng(-23.5505, -46.6333)

/** Which explanation (if any) is shown before/after the system location prompt. */
private enum class LocationPermissionDialog {
    /** Before the system prompt: why location is needed, so the prompt isn't a surprise. */
    RATIONALE,

    /** The user granted only approximate location — too coarse to pin a pothole to a lane. */
    PRECISE_NEEDED,

    /** Android won't show the prompt anymore ("não perguntar novamente" or two denials): the only
     * way forward is the app's system settings page. */
    BLOCKED,
}

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun TrackerScreen(
    viewModel: TrackerViewModel,
    onNavigateToMap: () -> Unit = {},
    modifier: Modifier = Modifier,
    // Falso no painel esquerdo do layout de tela expandida (ver MainActivity), onde o mapa
    // completo já aparece ao lado em MapScreen — evitar duas cópias do mesmo mapa e usar o
    // espaço liberado para o histórico de atividade recente.
    showEmbeddedMap: Boolean = true,
    // Banner de anúncio montado pelo app (null = sem anúncio: comprou "Remover anúncios" ou a
    // detecção está ativa). A feature só reserva o lugar; não depende do SDK de anúncios.
    adBanner: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // O resultado do pedido de localização é tratado num LaunchedEffect (abaixo) e não direto no
    // callback, porque decidir entre "negou" e "bloqueou de vez" precisa ler shouldShowRationale
    // do próprio locationPermissionsState — que ainda não existe dentro do seu inicializador.
    var locationRequestResult by remember { mutableStateOf<Map<String, Boolean>?>(null) }
    val locationPermissionsState =
        rememberMultiplePermissionsState(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
        ) { result -> locationRequestResult = result }
    val fineLocationGranted =
        locationPermissionsState.permissions
            .first { it.permission == Manifest.permission.ACCESS_FINE_LOCATION }
            .status.isGranted

    // Notificação é opcional: sem ela o serviço roda igual, só não aparece o aviso. Por isso o
    // start acontece qualquer que seja a resposta — antes, negar a notificação travava o botão.
    val notificationPermissionState =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            rememberPermissionState(Manifest.permission.POST_NOTIFICATIONS) {
                viewModel.onIntent(TrackerUiIntent.StartTracking)
            }
        } else {
            null
        }
    var askedNotificationThisSession by rememberSaveable { mutableStateOf(false) }
    var permissionDialog by rememberSaveable { mutableStateOf<LocationPermissionDialog?>(null) }

    fun startDetection() {
        viewModel.onIntent(TrackerUiIntent.TogglePermission(TrackerUiIntent.PermissionType.LOCATION, true))
        if (notificationPermissionState != null &&
            !notificationPermissionState.status.isGranted &&
            !askedNotificationThisSession
        ) {
            askedNotificationThisSession = true
            notificationPermissionState.launchPermissionRequest()
        } else {
            viewModel.onIntent(TrackerUiIntent.StartTracking)
        }
    }

    LaunchedEffect(fineLocationGranted) {
        viewModel.onIntent(
            TrackerUiIntent.TogglePermission(TrackerUiIntent.PermissionType.LOCATION, fineLocationGranted),
        )
    }

    LaunchedEffect(notificationPermissionState?.status?.isGranted) {
        viewModel.onIntent(
            TrackerUiIntent.TogglePermission(
                TrackerUiIntent.PermissionType.NOTIFICATION,
                notificationPermissionState?.status?.isGranted ?: true,
            ),
        )
    }

    LaunchedEffect(locationRequestResult) {
        val result = locationRequestResult ?: return@LaunchedEffect
        locationRequestResult = null
        when {
            result[Manifest.permission.ACCESS_FINE_LOCATION] == true -> startDetection()
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true ->
                permissionDialog = LocationPermissionDialog.PRECISE_NEEDED
            // Negou, mas o Android ainda deixa perguntar de novo: respeita o "não" — o cartão de
            // status continua explicando o que falta, e um novo toque pergunta outra vez.
            locationPermissionsState.shouldShowRationale -> Unit
            // Sem rationale logo após uma negação = o sistema nem mostrou o pedido (bloqueado).
            else -> permissionDialog = LocationPermissionDialog.BLOCKED
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

    permissionDialog?.let { dialog ->
        LocationPermissionAlert(
            dialog = dialog,
            onConfirm = {
                permissionDialog = null
                val canAskAgain =
                    dialog == LocationPermissionDialog.RATIONALE ||
                        (
                            dialog == LocationPermissionDialog.PRECISE_NEEDED &&
                                locationPermissionsState.shouldShowRationale
                        )
                if (canAskAgain) {
                    locationPermissionsState.launchMultiplePermissionRequest()
                } else {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }
            },
            onDismiss = { permissionDialog = null },
        )
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
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Column {
                            Text(stringResource(R.string.tracker_app_title), fontWeight = FontWeight.Bold)
                            Text(
                                stringResource(R.string.tracker_app_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        // Uma única lista rolável: antes era uma Column fixa (sensores + mapa + botão) em que, em
        // telas baixas, o botão principal ficava espremido e a lista de atividade sumia.
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "status") {
                DetectionStatusCard(
                    isTracking = uiState.isTracking,
                    locationGranted = fineLocationGranted,
                    potholeCount = uiState.detectedPotholes.size,
                    onToggle = {
                        when {
                            uiState.isTracking -> viewModel.onIntent(TrackerUiIntent.StopTracking)
                            fineLocationGranted -> startDetection()
                            else -> permissionDialog = LocationPermissionDialog.RATIONALE
                        }
                    },
                )
            }

            if (!uiState.hasStartedDetectionBefore && !uiState.isTracking) {
                item(key = "how_it_works") { HowItWorksCard() }
            }

            if (showEmbeddedMap) {
                item(key = "map") {
                    HomeMap(
                        potholes = uiState.detectedPotholes,
                        currentLocation = uiState.currentLocation,
                    )
                }
            }

            item(key = "sensor_details") {
                SensorDetailsSection(
                    isTracking = uiState.isTracking,
                    currentLocation = uiState.currentLocation,
                    x = uiState.sensorX,
                    y = uiState.sensorY,
                    z = uiState.sensorZ,
                    verticalIntensity = uiState.sensorIntensity,
                )
            }

            // Depois do status/sensores: longe do botão de iniciar/parar, para não virar clique
            // acidental (política do AdMob) nem tirar o foco do que importa.
            if (adBanner != null) {
                item(key = "ad") { adBanner() }
            }

            item(key = "recent_title") {
                Text(
                    text = stringResource(R.string.tracker_recent_activity_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                )
            }

            if (uiState.detectedPotholes.isEmpty()) {
                item(key = "recent_empty") {
                    Text(
                        stringResource(R.string.common_no_pothole_detected),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(uiState.detectedPotholes, key = { it.id }) { pothole ->
                    RecentPotholeItem(pothole)
                }
            }
        }
    }
}

/**
 * The one thing on Home the user must act on: detection is either off (and nothing gets mapped)
 * or on. Replaces the old "GPS: Inativo | Sensores de Movimento: Desligados" line + a lone
 * "Iniciar Rastreamento" button at the bottom — which read like a debug panel and made
 * "rastreamento" sound like the app tracking the user rather than the road.
 */
@Composable
private fun DetectionStatusCard(
    isTracking: Boolean,
    locationGranted: Boolean,
    potholeCount: Int,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isTracking) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DetectionIndicator(isTracking)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text =
                        stringResource(
                            if (isTracking) R.string.tracker_status_on_title else R.string.tracker_status_off_title,
                        ),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text =
                    stringResource(
                        when {
                            isTracking -> R.string.tracker_status_on_body
                            locationGranted -> R.string.tracker_status_off_body
                            else -> R.string.tracker_status_off_needs_location_body
                        },
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
            if (potholeCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = pluralStringResource(R.plurals.tracker_pothole_count_format, potholeCount, potholeCount),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (isTracking) {
                // Contorno, não vermelho: pausar não é destrutivo, e um botão de "perigo" gritando
                // na tela enquanto a pessoa dirige chama atenção à toa.
                OutlinedButton(onClick = onToggle, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Icon(Icons.Rounded.Pause, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.tracker_stop_button), fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = onToggle,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.tracker_start_button), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DetectionIndicator(isTracking: Boolean) {
    val pulseAlpha =
        if (isTracking) {
            val transition = rememberInfiniteTransition(label = "detectionPulse")
            transition.animateFloat(
                initialValue = 1f,
                targetValue = 0.3f,
                animationSpec = infiniteRepeatable(tween(durationMillis = 900), RepeatMode.Reverse),
                label = "detectionPulseAlpha",
            ).value
        } else {
            1f
        }
    Box(
        modifier =
            Modifier
                .size(12.dp)
                .alpha(pulseAlpha)
                .clip(CircleShape)
                .background(
                    if (isTracking) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                ),
    )
}

/** First-run hint, shown until the user turns detection on for the first time. */
@Composable
private fun HowItWorksCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.tracker_how_it_works_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            listOf(
                R.string.tracker_how_it_works_step1,
                R.string.tracker_how_it_works_step2,
                R.string.tracker_how_it_works_step3,
            ).forEachIndexed { index, stepRes ->
                Row {
                    Text(
                        text = "${index + 1}.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(20.dp),
                    )
                    Text(text = stringResource(stepRes), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun LocationPermissionAlert(
    dialog: LocationPermissionDialog,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val (titleRes, bodyRes, confirmRes) =
        when (dialog) {
            LocationPermissionDialog.RATIONALE ->
                Triple(
                    R.string.tracker_permission_rationale_title,
                    R.string.tracker_permission_rationale_body,
                    R.string.tracker_permission_rationale_confirm,
                )
            LocationPermissionDialog.PRECISE_NEEDED ->
                Triple(
                    R.string.tracker_permission_precise_title,
                    R.string.tracker_permission_precise_body,
                    R.string.tracker_permission_precise_confirm,
                )
            LocationPermissionDialog.BLOCKED ->
                Triple(
                    R.string.tracker_permission_blocked_title,
                    R.string.tracker_permission_blocked_body,
                    R.string.tracker_permission_open_settings,
                )
        }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.LocationOn, contentDescription = null) },
        title = { Text(stringResource(titleRes)) },
        text = { Text(stringResource(bodyRes)) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(stringResource(confirmRes)) } },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
            ) { Text(stringResource(R.string.tracker_permission_not_now)) }
        },
    )
}

@Composable
private fun HomeMap(
    potholes: List<Pothole>,
    currentLocation: LocationPoint?,
) {
    val context = LocalContext.current
    val youAreHereLabel = stringResource(R.string.tracker_marker_you_are_here)
    val cameraPositionState =
        rememberCameraPositionState {
            position = CameraPosition.fromLatLngZoom(SAO_PAULO, 14f)
        }

    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.position =
                CameraPosition.fromLatLngZoom(
                    LatLng(it.latitude, it.longitude),
                    16f,
                )
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp)),
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = false),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false),
        ) {
            currentLocation?.let {
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(it.latitude, it.longitude)),
                    title = youAreHereLabel,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE),
                )
            }
            potholes.forEach { pothole ->
                val craterColor = PotholeMarker.severityColor(pothole.severity)
                Marker(
                    state =
                        rememberUpdatedMarkerState(
                            position = LatLng(pothole.location.latitude, pothole.location.longitude),
                        ),
                    title =
                        stringResource(
                            R.string.tracker_marker_pothole_severity_format,
                            pothole.severity.toString(),
                        ),
                    icon =
                        remember(craterColor) {
                            BitmapDescriptorFactory.fromBitmap(PotholeMarker.bitmap(context, craterColor))
                        },
                    anchor = Offset(0.5f, 0.5f),
                )
            }
        }
    }
}

@Composable
private fun RecentPotholeItem(pothole: Pothole) {
    val timeFormat = remember { SimpleDateFormat("dd/MM 'às' HH:mm", Locale.forLanguageTag("pt-BR")) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Spacer(modifier = Modifier.size(12.dp))
            Column {
                Text(
                    text =
                        stringResource(
                            R.string.tracker_recent_item_title_format,
                            timeFormat.format(Date(pothole.timestamp)),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
                // Mesmas faixas de cor dos pinos no mapa (HomeMap/MapScreen): >20 forte, >10 médio.
                Text(
                    text =
                        stringResource(
                            when {
                                pothole.severity > 20f -> R.string.tracker_severity_high
                                pothole.severity > 10f -> R.string.tracker_severity_medium
                                else -> R.string.tracker_severity_low
                            },
                        ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
