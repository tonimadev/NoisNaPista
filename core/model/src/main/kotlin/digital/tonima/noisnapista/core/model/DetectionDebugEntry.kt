package digital.tonima.noisnapista.core.model

/**
 * Everything relevant about one detection for the debug/labeling screen: the aggregate
 * [Pothole] record plus its raw captured [sensorWindow] (null for the brief window between the
 * trigger and captureWindow()'s delayed emit — see PotholeDetector) and the manual classification
 * assigned so far.
 */
data class DetectionDebugEntry(
    val pothole: Pothole,
    val sensorWindow: SensorWindow?,
    val label: DetectionLabel,
    val note: String
) {
    /** Peak absolute Z-axis reading across the whole captured window, not just the trigger
     * sample — useful context when deciding whether a reading was a real pothole. */
    val peakAbsZ: Float?
        get() = sensorWindow?.samples?.maxOfOrNull { kotlin.math.abs(it.z) }
}
