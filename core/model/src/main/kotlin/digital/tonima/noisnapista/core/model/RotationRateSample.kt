package digital.tonima.noisnapista.core.model

/**
 * One raw gyroscope reading (rad/s per axis) — used to tell a real road bump, transmitted through
 * the suspension, apart from the phone itself being picked up or rotated by hand, which spins much
 * faster. Purely an in-memory streaming type, like [AccelerationSample].
 */
data class RotationRateSample(
    val x: Float,
    val y: Float,
    val z: Float,
    val timestampNanos: Long
)
