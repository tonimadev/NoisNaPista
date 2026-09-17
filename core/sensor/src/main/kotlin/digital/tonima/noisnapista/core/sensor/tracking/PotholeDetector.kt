package digital.tonima.noisnapista.core.sensor.tracking

import digital.tonima.noisnapista.core.location.LocationProvider
import digital.tonima.noisnapista.core.model.AccelerationSample
import digital.tonima.noisnapista.core.model.LocationPoint
import digital.tonima.noisnapista.core.model.Pothole
import digital.tonima.noisnapista.core.model.SensorWindow
import digital.tonima.noisnapista.core.model.SensorWindowSample
import digital.tonima.noisnapista.core.sensor.AccelerometerSensor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PotholeDetector @Inject constructor(
    private val accelerometerSensor: AccelerometerSensor,
    private val locationProvider: LocationProvider
) {
    // SupervisorJob is recreated per detection run so a stopped detector can be
    // restarted cleanly; nothing here launches until startDetection() is called,
    // otherwise the location request would fire (and can throw SecurityException)
    // before the user has granted the location permission.
    private var scope: CoroutineScope? = null
    private val _potholes = MutableSharedFlow<Pothole>()
    val potholes: SharedFlow<Pothole> = _potholes.asSharedFlow()

    // One captured raw sensor burst per detection, for later offline labeling and model
    // training — purely local, never sent to the backend (see PotholeRepository.saveSensorWindow).
    private val _sensorWindows = MutableSharedFlow<SensorWindow>()
    val sensorWindows: SharedFlow<SensorWindow> = _sensorWindows.asSharedFlow()

    private val _currentLocation = MutableStateFlow<LocationPoint?>(null)
    val currentLocation: StateFlow<LocationPoint?> = _currentLocation.asStateFlow()

    private val _currentZAxis = MutableStateFlow(0f)
    val currentZAxis: StateFlow<Float> = _currentZAxis.asStateFlow()

    // Fonte de verdade de "detecção ativa", independente de qualquer estado de UI: reflete se
    // startDetection()/stopDetection() foi chamado (por TrackingService), não se algum
    // ViewModel *acha* que está rastreando. Como PotholeDetector é @Singleton no processo do
    // app, isso sobrevive a uma Activity/ViewModel recriada (ex: processo voltou do background)
    // enquanto o serviço em foreground continua rodando — inclusive num restart automático via
    // START_STICKY, quando TrackingService.onCreate() chama startDetection() de novo antes de
    // qualquer tela existir.
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    // Rolling buffer feeding the "before the impact" half of every captured window. Guarded by a
    // mutex since it's written from the sensor-collection coroutine and read from each
    // short-lived captureWindow() coroutine concurrently.
    private val bufferMutex = Mutex()
    private val recentSamples = ArrayDeque<AccelerationSample>()

    // A single real bump stays above the threshold for several consecutive samples (at ~50Hz,
    // easily 30-70+ readings), and the suspension keeps bouncing back above the threshold for a
    // couple more seconds after that — real collected data showed the same physical bump logged
    // as 2-3 separate detections 1.6-3.1s apart. COOLDOWN_NANOS collapses all of that into one.
    private var lastTriggerNanos: Long? = null

    fun startDetection() {
        if (scope != null) return // already running
        val detectionScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = detectionScope
        _isTracking.value = true

        detectionScope.launch {
            locationProvider.getLocationUpdates()
                .catch { /* permission revoked or provider error; keep last known location */ }
                .collect { _currentLocation.value = it }
        }

        detectionScope.launch {
            accelerometerSensor.getAccelerationUpdates()
                .catch { }
                .collect { sample ->
                    _currentZAxis.value = kotlin.math.abs(sample.z)
                    recordSample(sample)

                    val withinCooldown = lastTriggerNanos?.let { sample.timestampNanos - it < COOLDOWN_NANOS } == true
                    if (kotlin.math.abs(sample.z) > IMPACT_THRESHOLD && !withinCooldown) { // Limiar de impacto
                        _currentLocation.value?.let { location ->
                            lastTriggerNanos = sample.timestampNanos
                            val pothole = Pothole(
                                id = UUID.randomUUID().toString(),
                                location = location,
                                severity = kotlin.math.abs(sample.z),
                                timestamp = System.currentTimeMillis()
                            )
                            _potholes.emit(pothole)
                            launch { captureWindow(pothole.id, sample.timestampNanos) }
                        }
                    }
                }
        }
    }

    private suspend fun recordSample(sample: AccelerationSample) {
        bufferMutex.withLock {
            recentSamples.addLast(sample)
            val cutoff = sample.timestampNanos - BUFFER_RETENTION_NANOS
            while (recentSamples.isNotEmpty() && recentSamples.first().timestampNanos < cutoff) {
                recentSamples.removeFirst()
            }
        }
    }

    /** Waits for the "after the impact" half to accumulate, then emits the full window. */
    private suspend fun captureWindow(potholeId: String, triggerTimestampNanos: Long) {
        delay(POST_WINDOW_MILLIS)
        val windowStart = triggerTimestampNanos - PRE_WINDOW_NANOS
        val windowEnd = triggerTimestampNanos + POST_WINDOW_MILLIS * 1_000_000L
        val samples = bufferMutex.withLock { recentSamples.toList() }
            .filter { it.timestampNanos in windowStart..windowEnd }
            .map {
                SensorWindowSample(
                    offsetMs = (it.timestampNanos - triggerTimestampNanos) / 1_000_000,
                    x = it.x,
                    y = it.y,
                    z = it.z
                )
            }
        _sensorWindows.emit(SensorWindow(potholeId, samples))
    }

    fun stopDetection() {
        scope?.cancel()
        scope = null
        _isTracking.value = false
        _currentLocation.value = null
        _currentZAxis.value = 0f
        lastTriggerNanos = null
        // recentSamples is left as-is (no lock taken here, since a plain function shouldn't
        // block on a mutex) — the timestamp-based pruning in recordSample() naturally discards
        // every stale sample the moment a new detection run starts producing fresh ones.
    }

    private companion object {
        const val IMPACT_THRESHOLD = 15f
        const val PRE_WINDOW_NANOS = 2_000_000_000L // 2s of context before the impact
        const val POST_WINDOW_MILLIS = 1_000L // 1s after the impact
        const val BUFFER_RETENTION_NANOS = PRE_WINDOW_NANOS + POST_WINDOW_MILLIS * 1_000_000L + 500_000_000L
        // 4s: covers the suspension-bounce clusters (1.6-3.1s apart) seen in the first labeled
        // dataset without swallowing genuinely distinct potholes, which showed up no closer than
        // ~5.8s apart. Not a perfect fix — a real string of close-together potholes on a rough
        // stretch inside this window still collapses to one detection — but that failure mode is
        // the same thing ROUGH_ROAD already exists to label, whereas the bounce-duplicate failure
        // mode was actively inflating the false-positive count in every session so far.
        const val COOLDOWN_NANOS = 4_000_000_000L
    }
}
