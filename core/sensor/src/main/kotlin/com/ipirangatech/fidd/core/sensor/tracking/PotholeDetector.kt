package com.ipirangatech.fidd.core.sensor.tracking

import com.ipirangatech.fidd.core.location.LocationProvider
import com.ipirangatech.fidd.core.model.AccelerationSample
import com.ipirangatech.fidd.core.model.LocationPoint
import com.ipirangatech.fidd.core.model.Pothole
import com.ipirangatech.fidd.core.model.RotationRateSample
import com.ipirangatech.fidd.core.model.SensorWindow
import com.ipirangatech.fidd.core.model.SensorWindowSample
import com.ipirangatech.fidd.core.sensor.MotionSensor
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
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class PotholeDetector
    @Inject
    constructor(
        private val motionSensor: MotionSensor,
        private val locationProvider: LocationProvider,
    ) {
        // SupervisorJob is recreated per detection run so a stopped detector can be
        // restarted cleanly; nothing here launches until startDetection() is called,
        // otherwise the location request would fire (and can throw SecurityException)
        // before the user has granted the location permission.
        private var scope: CoroutineScope? = null

        // One id per startDetection()..stopDetection() run ("viagem"), stamped onto every Pothole
        // detected in that run so the debug/classification screen can group them by trip.
        private var sessionId: String? = null
        private val _potholes = MutableSharedFlow<Pothole>()
        val potholes: SharedFlow<Pothole> = _potholes.asSharedFlow()

        // One captured raw sensor burst per detection, for later offline labeling and model
        // training — purely local, never sent to the backend (see PotholeRepository.saveSensorWindow).
        private val _sensorWindows = MutableSharedFlow<SensorWindow>()
        val sensorWindows: SharedFlow<SensorWindow> = _sensorWindows.asSharedFlow()

        private val _currentLocation = MutableStateFlow<LocationPoint?>(null)
        val currentLocation: StateFlow<LocationPoint?> = _currentLocation.asStateFlow()

        // Every axis of the live raw accelerometer reading — the Home screen's sensor panel shows all
        // three for context, even though only the gravity-corrected vertical component (below) drives
        // detection.
        private val _currentAcceleration = MutableStateFlow(AccelerationSample(0f, 0f, 0f, 0L))
        val currentAcceleration: StateFlow<AccelerationSample> = _currentAcceleration.asStateFlow()

        // The actual magnitude PotholeDetector triggers on — see computeVerticalAcceleration() below.
        // Exposed so the Home screen can show/color the same number that decides "this is a pothole",
        // instead of a raw axis that's no longer necessarily the relevant one.
        private val _currentVerticalAcceleration = MutableStateFlow(0f)
        val currentVerticalAcceleration: StateFlow<Float> = _currentVerticalAcceleration.asStateFlow()

        // Latest gravity vector (device frame, m/s²) from the fused gravity sensor. Null until the
        // first reading arrives, or forever null on a device without a gyroscope — the trigger below
        // falls back to the raw accelerometer's Z axis in that case (same "fail open" philosophy as
        // the speed gate: don't lose real detections just because a secondary sensor is unavailable).
        private val latestGravity = MutableStateFlow<AccelerationSample?>(null)

        // Latest gyroscope rotation rate (rad/s). Null the same way — the handling gate below simply
        // never fires while it's unknown, rather than blocking detections.
        private val latestRotationRate = MutableStateFlow<RotationRateSample?>(null)

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
            sessionId = UUID.randomUUID().toString()
            _isTracking.value = true

            detectionScope.launch {
                locationProvider.getLocationUpdates()
                    .catch { /* permission revoked or provider error; keep last known location */ }
                    .collect { _currentLocation.value = it }
            }

            detectionScope.launch {
                motionSensor.getGravityUpdates()
                    .catch { }
                    .collect { latestGravity.value = it }
            }

            detectionScope.launch {
                motionSensor.getRotationRateUpdates()
                    .catch { }
                    .collect { latestRotationRate.value = it }
            }

            detectionScope.launch {
                motionSensor.getAccelerationUpdates()
                    .catch { }
                    .collect { sample ->
                        _currentAcceleration.value = sample
                        recordSample(sample)

                        val impactMagnitude = computeVerticalAcceleration(sample, latestGravity.value)
                        _currentVerticalAcceleration.value = impactMagnitude

                        val withinCooldown =
                            lastTriggerNanos?.let { sample.timestampNanos - it < COOLDOWN_NANOS } == true
                        if (impactMagnitude > IMPACT_THRESHOLD && !withinCooldown) { // Limiar de impacto
                            _currentLocation.value?.let { location ->
                                // Parado (celular sendo pego/manuseado, carro estacionado) gera o
                                // mesmo pico de aceleração vertical que um buraco real — sem GPS
                                // indicando movimento, esse impacto quase certamente não veio da
                                // pista. Um fix sem leitura de velocidade (speed == null) não bloqueia
                                // a detecção, para não perder buracos reais por falha pontual do GPS.
                                val isStationary = location.speed?.let { it < MIN_SPEED_MPS } == true
                                // O celular sendo girado na mão produz uma taxa de rotação muito maior
                                // do que qualquer buraco transmitido pela suspensão — sem leitura de
                                // giroscópio (rotationRate == null), também não bloqueia a detecção.
                                val isLikelyHandling =
                                    latestRotationRate.value?.let {
                                        rotationMagnitude(it) > HANDLING_ROTATION_THRESHOLD_RAD_S
                                    } == true
                                if (!isStationary && !isLikelyHandling) {
                                    lastTriggerNanos = sample.timestampNanos
                                    val pothole =
                                        Pothole(
                                            id = UUID.randomUUID().toString(),
                                            location = location,
                                            severity = impactMagnitude,
                                            timestamp = System.currentTimeMillis(),
                                            sessionId = sessionId,
                                        )
                                    _potholes.emit(pothole)
                                    launch { captureWindow(pothole.id, sample.timestampNanos) }
                                }
                            }
                        }
                    }
            }
        }

        /** The vertical component of the *linear* (gravity-removed) acceleration, in the phone's real
         * world frame — not just the raw accelerometer's Z axis, which is only "vertical" if the
         * phone happens to be mounted flat. Projects (rawAccel - gravity) onto the gravity direction,
         * so a bump registers the same whether the phone is lying flat on a dash mount or propped up
         * at an angle. Falls back to the old raw-Z reading when gravity is unknown (sensor missing or
         * not yet warmed up) rather than blocking detection. */
        private fun computeVerticalAcceleration(
            sample: AccelerationSample,
            gravity: AccelerationSample?,
        ): Float {
            if (gravity == null) return abs(sample.z)
            val gravityMagnitude = sqrt(gravity.x * gravity.x + gravity.y * gravity.y + gravity.z * gravity.z)
            if (gravityMagnitude < MIN_GRAVITY_MAGNITUDE) return abs(sample.z)
            val gx = gravity.x / gravityMagnitude
            val gy = gravity.y / gravityMagnitude
            val gz = gravity.z / gravityMagnitude
            val linearX = sample.x - gravity.x
            val linearY = sample.y - gravity.y
            val linearZ = sample.z - gravity.z
            return abs(linearX * gx + linearY * gy + linearZ * gz)
        }

        private fun rotationMagnitude(rotation: RotationRateSample): Float =
            sqrt(rotation.x * rotation.x + rotation.y * rotation.y + rotation.z * rotation.z)

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
        private suspend fun captureWindow(
            potholeId: String,
            triggerTimestampNanos: Long,
        ) {
            delay(POST_WINDOW_MILLIS.milliseconds)
            val windowStart = triggerTimestampNanos - PRE_WINDOW_NANOS
            val windowEnd = triggerTimestampNanos + POST_WINDOW_MILLIS * 1_000_000L
            val samples =
                bufferMutex.withLock { recentSamples.toList() }
                    .filter { it.timestampNanos in windowStart..windowEnd }
                    .map {
                        SensorWindowSample(
                            offsetMs = (it.timestampNanos - triggerTimestampNanos) / 1_000_000,
                            x = it.x,
                            y = it.y,
                            z = it.z,
                        )
                    }
            _sensorWindows.emit(SensorWindow(potholeId, samples))
        }

        fun stopDetection() {
            scope?.cancel()
            scope = null
            _isTracking.value = false
            _currentLocation.value = null
            _currentAcceleration.value = AccelerationSample(0f, 0f, 0f, 0L)
            _currentVerticalAcceleration.value = 0f
            latestGravity.value = null
            latestRotationRate.value = null
            lastTriggerNanos = null
            // recentSamples is left as-is (no lock taken here, since a plain function shouldn't
            // block on a mutex) — the timestamp-based pruning in recordSample() naturally discards
            // every stale sample the moment a new detection run starts producing fresh ones.
        }

        private companion object {
            const val IMPACT_THRESHOLD = 15f

            // ~5 km/h: acima do ruído típico de um fix de GPS parado, abaixo de qualquer velocidade
            // real de condução — filtra o carro parado/celular manuseado sem cortar tráfego lento.
            const val MIN_SPEED_MPS = 1.4f

            // Sanity floor before treating a gravity reading as usable — a near-zero magnitude means
            // the virtual sensor hasn't produced a real fix yet (e.g. right after registration).
            const val MIN_GRAVITY_MAGNITUDE = 1f

            // Conservative starting point, not yet validated against a labeled dataset the way
            // IMPACT_THRESHOLD/MIN_SPEED_MPS were (see the 2026-09-17 analysis) — picked high on
            // purpose so it only screens out obvious phone-in-hand rotation, not borderline cases,
            // until real driving data shows what a suspension-transmitted bump actually looks like on
            // the gyroscope.
            const val HANDLING_ROTATION_THRESHOLD_RAD_S = 12f
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
