package com.ipirangatech.fidd.core.network

import com.squareup.moshi.JsonClass

/** Matches the backend's CityRankingResponse. */
@JsonClass(generateAdapter = true)
data class CityRankingResponseDto(
    val ibgeCode: Int,
    val name: String,
    val state: String,
    val totalPotholes: Long,
    val fixedPotholes: Long,
    val recurrenceCount: Long,
    val rank: Int? = null
)

/** Matches the backend's CityRankingListResponse — GET /api/v1/cities/ranking never returns the
 * full (potentially thousands-of-cities) list, only these top/bottom slices. */
@JsonClass(generateAdapter = true)
data class CityRankingListResponseDto(
    val totalCities: Int,
    val top: List<CityRankingResponseDto>,
    val bottom: List<CityRankingResponseDto>
)
