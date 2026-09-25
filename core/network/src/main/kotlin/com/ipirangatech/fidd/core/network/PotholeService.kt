package com.ipirangatech.fidd.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Every write call carries the anonymous, device-local reporter token as a header — see
 * [com.ipirangatech.fidd.core.data.ReporterIdentityProvider]. The server never stores it
 * raw, only a one-way hash, and it's the only way to prove "I reported this" for delete/votes. */
const val REPORTER_TOKEN_HEADER = "X-Reporter-Token"

interface PotholeService {
    @POST("api/v1/potholes")
    suspend fun submitReading(
        @Header(REPORTER_TOKEN_HEADER) reporterToken: String,
        @Body request: PotholeReadingRequestDto
    ): PotholeResponseDto

    /** Up to [PotholeReadingBatchRequestDto.MAX_SIZE] readings in one round trip; one result per
     * reading, in order. */
    @POST("api/v1/potholes/batch")
    suspend fun submitReadings(
        @Header(REPORTER_TOKEN_HEADER) reporterToken: String,
        @Body request: PotholeReadingBatchRequestDto
    ): List<PotholeBatchItemResponseDto>

    /** Map viewport query. The server caps the result (most-corroborated first) — [limit] can
     * only lower that cap. */
    @GET("api/v1/potholes")
    suspend fun getPotholes(
        @Query("minLat") minLat: Double,
        @Query("minLon") minLon: Double,
        @Query("maxLat") maxLat: Double,
        @Query("maxLon") maxLon: Double,
        @Query("limit") limit: Int? = null
    ): List<PotholeResponseDto>

    /** Potholes around a point, nearest first — for list screens rather than the map. */
    @GET("api/v1/potholes/nearby")
    suspend fun getNearbyPotholes(
        @Query("lat") latitude: Double,
        @Query("lon") longitude: Double,
        @Query("radiusMeters") radiusMeters: Double? = null,
        @Query("limit") limit: Int? = null
    ): List<PotholeResponseDto>

    @POST("api/v1/potholes/{id}/fix-votes")
    suspend fun castFixVote(
        @Path("id") id: String,
        @Header(REPORTER_TOKEN_HEADER) reporterToken: String
    ): PotholeResponseDto

    @DELETE("api/v1/potholes/{id}")
    suspend fun deletePothole(
        @Path("id") id: String,
        @Header(REPORTER_TOKEN_HEADER) reporterToken: String
    )
}
