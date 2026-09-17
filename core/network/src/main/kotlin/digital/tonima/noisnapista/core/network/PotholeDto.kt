package digital.tonima.noisnapista.core.network

import com.squareup.moshi.JsonClass

/** Matches the backend's PotholeReadingRequest (POST /api/v1/potholes). */
@JsonClass(generateAdapter = true)
data class PotholeReadingRequestDto(
    val clientId: String,
    val latitude: Double,
    val longitude: Double,
    val severity: Float,
    val timestamp: Long
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
    val createdAt: String
)
