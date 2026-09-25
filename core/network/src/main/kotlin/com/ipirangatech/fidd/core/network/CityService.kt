package com.ipirangatech.fidd.core.network

import retrofit2.http.GET
import retrofit2.http.Query

interface CityService {
    @GET("api/v1/cities/ranking")
    suspend fun getRanking(
        @Query("sortBy") sortBy: String,
    ): CityRankingListResponseDto

    @GET("api/v1/cities/nearest")
    suspend fun getNearestCity(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        @Query("sortBy") sortBy: String,
    ): CityRankingResponseDto
}
