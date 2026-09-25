package com.ipirangatech.fidd.core.network

import com.squareup.moshi.JsonClass

/** Matches the backend's PotholeReadingRequest (POST /api/v1/potholes). */
@JsonClass(generateAdapter = true)
data class PotholeReadingRequestDto(
    val clientId: String,
    val latitude: Double,
    val longitude: Double,
    val severity: Float,
    val timestamp: Long,
)

/** Matches the backend's PotholeResponse — the public, anonymous shape of a pothole. */
@JsonClass(generateAdapter = true)
data class PotholeResponseDto(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val severity: Float,
    val status: String,
    val readingCount: Int,
    val distinctReporterCount: Int,
    val createdAt: String,
)

/** Matches the backend's PotholeBatchRequest (POST /api/v1/potholes/batch). */
@JsonClass(generateAdapter = true)
data class PotholeReadingBatchRequestDto(
    val readings: List<PotholeReadingRequestDto>,
) {
    companion object {
        /** Mirrors the backend's `noisnapista.batch.max-size` default. */
        const val MAX_SIZE = 100
    }
}

/** One per submitted reading: [pothole] on success, [error] when the server rejected that reading. */
@JsonClass(generateAdapter = true)
data class PotholeBatchItemResponseDto(
    val clientId: String,
    val pothole: PotholeResponseDto? = null,
    val error: String? = null,
)
