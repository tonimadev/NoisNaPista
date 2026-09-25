package com.ipirangatech.fidd.feature.map.impl

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ipirangatech.fidd.core.data.PotholeRepository
import com.ipirangatech.fidd.core.location.LocationProvider
import com.ipirangatech.fidd.core.model.GeoBounds
import com.ipirangatech.fidd.core.model.LocationPoint
import com.ipirangatech.fidd.core.model.Pothole
import com.ipirangatech.fidd.core.sensor.tracking.PotholeDetector
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface MapUiEffect {
    data class ShowMessage(@StringRes val messageRes: Int) : MapUiEffect
    data class CenterOn(val location: LocationPoint) : MapUiEffect
}

@HiltViewModel
class MapViewModel @Inject constructor(
    private val repository: PotholeRepository,
    private val locationProvider: LocationProvider,
    private val potholeDetector: PotholeDetector
) : ViewModel() {
    val potholes: StateFlow<List<Pothole>> = repository.getActivePotholes()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    // PotholeDetector.currentLocation só tem valor enquanto o rastreamento está ativo (ver
    // startDetection/stopDetection). Antes do usuário apertar "Iniciar", cai para este fix único
    // buscado em init, assim "você está aqui" já aparece no mapa sem esperar o rastreamento
    // começar; uma vez que o stream real emite, ele passa a ter prioridade.
    private val _previewLocation = MutableStateFlow<LocationPoint?>(null)
    val currentLocation: StateFlow<LocationPoint?> = combine(
        potholeDetector.currentLocation,
        _previewLocation
    ) { trackingLocation, previewLocation -> trackingLocation ?: previewLocation }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _communityPotholes = MutableStateFlow<List<Pothole>>(emptyList())
    val communityPotholes: StateFlow<List<Pothole>> = _communityPotholes.asStateFlow()

    private val _uiEffect = Channel<MapUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    // Only the visible area is fetched, so there's nothing to load until the map reports its
    // first viewport (see onViewportChanged).
    private var lastViewport: GeoBounds? = null
    private var fetchJob: Job? = null

    init {
        viewModelScope.launch {
            _previewLocation.value = locationProvider.getCurrentLocation()
        }
    }

    /** Botão de recentralizar: com o rastreamento ativo usa o fix mais recente dele; sem
     * rastreamento, busca um fix novo (o de init pode estar velho se o usuário se moveu). */
    fun recenter() {
        viewModelScope.launch {
            val location = potholeDetector.currentLocation.value
                ?: locationProvider.getCurrentLocation()?.also { _previewLocation.value = it }
            _uiEffect.send(
                if (location != null) MapUiEffect.CenterOn(location)
                else MapUiEffect.ShowMessage(R.string.map_location_unavailable)
            )
        }
    }

    /** Called by the screen whenever the camera settles. Silent on failure — an unreachable
     * backend shouldn't pop an error on every pan; the device's own pins still render fine. */
    fun onViewportChanged(bounds: GeoBounds) {
        lastViewport = bounds
        refreshCommunityPotholes(notifyOnFailure = false)
    }

    fun refreshCommunityPotholes(notifyOnFailure: Boolean = true) {
        val bounds = lastViewport ?: return
        // A newer viewport supersedes an in-flight fetch for an old one, so a slow response for
        // where the user *was* can never overwrite the pins for where they are now.
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            repository.fetchCommunityPotholes(bounds)
                .onSuccess { _communityPotholes.value = it }
                .onFailure { error ->
                    // Superseded by a newer viewport (see fetchJob) — not a real failure.
                    if (error is CancellationException) return@launch
                    Log.e(TAG, "Failed to fetch community potholes", error)
                    if (notifyOnFailure) {
                        _uiEffect.send(MapUiEffect.ShowMessage(R.string.map_community_refresh_failed))
                    }
                }
            // Keeps the previous list on failure — the device's own locally-detected potholes
            // still render regardless.
        }
    }

    private companion object {
        const val TAG = "MapViewModel"
    }
}
