package digital.tonima.noisnapista.feature.tracker.impl

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import digital.tonima.noisnapista.core.data.PotholeRepository
import digital.tonima.noisnapista.core.location.LocationProvider
import digital.tonima.noisnapista.core.model.LocationPoint
import digital.tonima.noisnapista.core.model.Pothole
import digital.tonima.noisnapista.core.sensor.tracking.PotholeDetector
import digital.tonima.noisnapista.core.sensor.tracking.TrackingService
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TrackerUiState(
    val isTracking: Boolean = false,
    val detectedPotholes: List<Pothole> = emptyList(),
    val currentLocation: LocationPoint? = null,
    val locationPermissionGranted: Boolean = false,
    val notificationPermissionGranted: Boolean = false,
    /** Live raw accelerometer Z-axis magnitude (m/s²), for the on-screen sensor bar. */
    val sensorIntensity: Float = 0f
)

sealed interface TrackerUiIntent {
    data object StartTracking : TrackerUiIntent
    data object StopTracking : TrackerUiIntent
    data class TogglePermission(val permission: PermissionType, val granted: Boolean) : TrackerUiIntent

    enum class PermissionType {
        LOCATION, NOTIFICATION
    }
}

sealed interface TrackerUiEffect {
    data class ShowError(@StringRes val messageRes: Int) : TrackerUiEffect
    data class NavigateTo(val route: String) : TrackerUiEffect
}

@HiltViewModel
class TrackerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val potholeDetector: PotholeDetector,
    private val locationProvider: LocationProvider,
    private val repository: PotholeRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(TrackerUiState())
    val uiState: StateFlow<TrackerUiState> = _uiState.asStateFlow()

    private val _uiEffect = Channel<TrackerUiEffect>()
    val uiEffect = _uiEffect.receiveAsFlow()

    init {
        viewModelScope.launch {
            potholeDetector.potholes.collect { pothole ->
                repository.savePothole(pothole)
            }
        }
        viewModelScope.launch {
            potholeDetector.sensorWindows.collect { window ->
                repository.saveSensorWindow(window)
            }
        }
        viewModelScope.launch {
            repository.getActivePotholes().collect { potholes ->
                _uiState.update { it.copy(detectedPotholes = potholes) }
            }
        }
        viewModelScope.launch {
            potholeDetector.currentLocation.collect { location ->
                _uiState.update { it.copy(currentLocation = location) }
            }
        }
        viewModelScope.launch {
            potholeDetector.currentZAxis.collect { z ->
                _uiState.update { it.copy(sensorIntensity = z) }
            }
        }
        // Fonte de verdade de isTracking: nunca setada "otimisticamente" a partir do clique do
        // usuário, e sim sempre espelhando o PotholeDetector (singleton no processo). Isso
        // corrige a tela voltando pro estado default depois de minimizar/reabrir o app — se o
        // processo foi recriado (ou o TrackingService reiniciou sozinho via START_STICKY)
        // enquanto o rastreamento seguia ativo em segundo plano, este ViewModel novo já nasce
        // com o valor real em vez de false.
        viewModelScope.launch {
            potholeDetector.isTracking.collect { tracking ->
                _uiState.update { it.copy(isTracking = tracking) }
            }
        }
    }

    fun onIntent(intent: TrackerUiIntent) {
        when (intent) {
            TrackerUiIntent.StartTracking -> startTracking()
            TrackerUiIntent.StopTracking -> stopTracking()
            is TrackerUiIntent.TogglePermission -> {
                _uiState.update {
                    when (intent.permission) {
                        TrackerUiIntent.PermissionType.LOCATION -> it.copy(locationPermissionGranted = intent.granted)
                        TrackerUiIntent.PermissionType.NOTIFICATION -> it.copy(notificationPermissionGranted = intent.granted)
                    }
                }
                if (intent.permission == TrackerUiIntent.PermissionType.LOCATION && intent.granted) {
                    fetchLocationPreview()
                }
            }
        }
    }

    private fun startTracking() {
        if (!_uiState.value.locationPermissionGranted) {
            viewModelScope.launch {
                _uiEffect.send(TrackerUiEffect.ShowError(R.string.tracker_location_permission_required))
            }
            return
        }

        val intent = Intent(context, TrackingService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        // isTracking não é setado aqui: o collector de potholeDetector.isTracking acima reflete
        // assim que TrackingService.onCreate() chamar startDetection() de verdade.
    }

    // Um fix único e sob demanda (sem stream contínuo) para mostrar "você está aqui" no mapa
    // assim que a permissão é concedida — antes disso, currentLocation só existia enquanto o
    // TrackingService estava de fato rodando, deixando o mapa em branco até o usuário apertar
    // "Iniciar". Ignorado se o rastreamento real já começou nesse meio-tempo, para não sobrepor
    // um fix antigo por cima do stream contínuo do PotholeDetector.
    private fun fetchLocationPreview() {
        if (_uiState.value.isTracking) return
        viewModelScope.launch {
            val location = locationProvider.getCurrentLocation() ?: return@launch
            if (!_uiState.value.isTracking) {
                _uiState.update { it.copy(currentLocation = location) }
            }
        }
    }

    private fun stopTracking() {
        val intent = Intent(context, TrackingService::class.java)
        context.stopService(intent)
        // idem: isTracking/currentLocation são atualizados pelos collectors quando
        // TrackingService.onDestroy() chamar stopDetection().
    }
}
