package digital.tonima.noisnapista.feature.map.impl

import android.content.Context
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapEffect
import com.google.maps.android.compose.MapsComposeExperimentalApi
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.clustering.Clustering
import com.google.maps.android.compose.clustering.rememberClusterManager
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberUpdatedMarkerState
import digital.tonima.noisnapista.core.model.GeoBounds
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import java.util.Date

@OptIn(MapsComposeExperimentalApi::class)
@Composable
fun MapScreen(
    viewModel: MapViewModel,
    modifier: Modifier = Modifier
) {
    val potholes by viewModel.potholes.collectAsStateWithLifecycle()
    val communityPotholes by viewModel.communityPotholes.collectAsStateWithLifecycle()
    val currentLocation by viewModel.currentLocation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(-23.5505, -46.6333), 10f)
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val youAreHereLabel = stringResource(R.string.map_marker_you_are_here)

    LaunchedEffect(Unit) {
        viewModel.uiEffect.collect { effect ->
            when (effect) {
                is MapUiEffect.ShowMessage -> snackbarHostState.showSnackbar(context.getString(effect.messageRes))
            }
        }
    }

    // Mesmo comportamento que o mini-mapa da Home tinha: recentra a câmera a cada novo fix de
    // GPS recebido enquanto o rastreamento está ativo.
    LaunchedEffect(currentLocation) {
        currentLocation?.let {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(it.latitude, it.longitude),
                16f
            )
        }
    }

    // Busca só os buracos da área visível, cada vez que a câmera para de se mover. `projection`
    // é nula até o mapa carregar, então a primeira busca sai assim que ele fica pronto.
    LaunchedEffect(cameraPositionState) {
        snapshotFlow {
            if (cameraPositionState.isMoving) null
            else cameraPositionState.projection?.visibleRegion?.latLngBounds
        }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { viewModel.onViewportChanged(it.toGeoBounds()) }
    }

    val pendingStatusFallback = stringResource(R.string.map_status_pending_fallback)
    val mapItems = remember(potholes, communityPotholes, pendingStatusFallback) {
        // Uma detecção própria já sincronizada também volta na lista da comunidade — mostra só
        // o pino próprio, que tem mais detalhe.
        val ownServerIds = potholes.mapNotNullTo(HashSet()) { it.serverId }
        potholes.map { pothole ->
            PotholeMapItem(
                key = "local-${pothole.id}",
                position = LatLng(pothole.location.latitude, pothole.location.longitude),
                title = context.getString(R.string.map_marker_own_format, pothole.severity.toString()),
                snippet = context.getString(R.string.map_marker_detected_at_format, Date(pothole.timestamp).toString()),
                hue = severityHue(pothole.severity)
            )
        } + communityPotholes.filter { it.serverId !in ownServerIds }.map { pothole ->
            PotholeMapItem(
                key = "community-${pothole.serverId}",
                position = LatLng(pothole.location.latitude, pothole.location.longitude),
                title = context.getString(R.string.map_marker_community_format, pothole.status ?: pendingStatusFallback),
                snippet = context.getString(R.string.map_report_count_format, pothole.distinctReporterCount),
                // Violet marks these as community-sourced pins, distinct from this device's own detections.
                hue = BitmapDescriptorFactory.HUE_VIOLET
            )
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
        GoogleMap(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            cameraPositionState = cameraPositionState,
            // Sobe os controles do próprio mapa (zoom, logo do Google) acima do FAB de atualizar,
            // que ocupa o mesmo canto inferior direito: 56dp do FAB + 16dp de margem + folga.
            contentPadding = PaddingValues(bottom = 80.dp)
        ) {
            currentLocation?.let {
                Marker(
                    state = rememberUpdatedMarkerState(position = LatLng(it.latitude, it.longitude)),
                    title = youAreHereLabel,
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }
            // Agrupa pinos próximos em um único marcador com contagem: milhares de Markers
            // individuais travam o mapa quando se afasta o zoom.
            val clusterManager = rememberClusterManager<PotholeMapItem>()
            MapEffect(clusterManager) { map ->
                clusterManager ?: return@MapEffect
                clusterManager.renderer = PotholeClusterRenderer(context, map, clusterManager)
                clusterManager.setOnClusterClickListener { cluster ->
                    map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(cluster.position, map.cameraPosition.zoom + 2f)
                    )
                    true
                }
            }
            clusterManager?.let { Clustering(items = mapItems, clusterManager = it) }
        }
    }
}

private data class PotholeMapItem(
    val key: String,
    override val position: LatLng,
    override val title: String,
    override val snippet: String,
    val hue: Float
) : ClusterItem {
    override val zIndex: Float? get() = null
}

/** The default cluster bubbles, but individual pins keep the severity / community colors. */
private class PotholeClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<PotholeMapItem>
) : DefaultClusterRenderer<PotholeMapItem>(context, map, clusterManager) {
    override fun onBeforeClusterItemRendered(item: PotholeMapItem, markerOptions: MarkerOptions) {
        super.onBeforeClusterItemRendered(item, markerOptions)
        markerOptions.icon(BitmapDescriptorFactory.defaultMarker(item.hue))
    }

    override fun onClusterItemUpdated(item: PotholeMapItem, marker: com.google.android.gms.maps.model.Marker) {
        super.onClusterItemUpdated(item, marker)
        marker.setIcon(BitmapDescriptorFactory.defaultMarker(item.hue))
    }
}

private fun LatLngBounds.toGeoBounds() = GeoBounds(
    minLatitude = southwest.latitude,
    minLongitude = southwest.longitude,
    maxLatitude = northeast.latitude,
    maxLongitude = northeast.longitude
)

private fun severityHue(severity: Float): Float = when {
    severity > 20f -> BitmapDescriptorFactory.HUE_RED
    severity > 10f -> BitmapDescriptorFactory.HUE_ORANGE
    else -> BitmapDescriptorFactory.HUE_YELLOW
}
