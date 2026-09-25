package com.ipirangatech.fidd.core.model

import kotlinx.serialization.Serializable

/**
 * One reading inside a captured pre/post-impact sensor burst, timed relative to the moment the
 * detection threshold was crossed ([offsetMs] negative = before impact, positive = after).
 */
@Serializable
data class SensorWindowSample(
    val offsetMs: Long,
    val x: Float,
    val y: Float,
    val z: Float
)

/**
 * A short burst of raw accelerometer samples captured around one pothole detection — the raw
 * material for later offline labeling and model training. Purely local: this is never sent to
 * the backend, only [Pothole] (position + severity) is.
 */
@Serializable
data class SensorWindow(
    val potholeId: String,
    val samples: List<SensorWindowSample>
)
