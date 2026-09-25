package com.ipirangatech.fidd.core.data

import com.ipirangatech.fidd.core.model.CityRanking
import com.ipirangatech.fidd.core.model.CityRankingList
import com.ipirangatech.fidd.core.model.CityRankingSortBy
import com.ipirangatech.fidd.core.network.CityRankingListResponseDto
import com.ipirangatech.fidd.core.network.CityRankingResponseDto
import com.ipirangatech.fidd.core.network.CityService
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CityRepositoryImpl
    @Inject
    constructor(
        private val cityService: CityService,
    ) : CityRepository {
        override suspend fun fetchRanking(sortBy: CityRankingSortBy): Result<CityRankingList> =
            runCatching { cityService.getRanking(sortBy.toQueryParam()).toExternalModel() }

        override suspend fun fetchNearestCity(
            lat: Double,
            lon: Double,
            sortBy: CityRankingSortBy,
        ): Result<CityRanking> =
            runCatching { cityService.getNearestCity(lat, lon, sortBy.toQueryParam()).toExternalModel() }
    }

private fun CityRankingSortBy.toQueryParam(): String =
    when (this) {
        CityRankingSortBy.POTHOLES -> "potholes"
        CityRankingSortBy.FIXED -> "fixed"
        CityRankingSortBy.RECURRENCE -> "recurrence"
    }

fun CityRankingResponseDto.toExternalModel() =
    CityRanking(
        ibgeCode = ibgeCode,
        name = name,
        state = state,
        totalPotholes = totalPotholes,
        fixedPotholes = fixedPotholes,
        recurrenceCount = recurrenceCount,
        rank = rank,
    )

fun CityRankingListResponseDto.toExternalModel() =
    CityRankingList(
        totalCities = totalCities,
        top = top.map { it.toExternalModel() },
        bottom = bottom.map { it.toExternalModel() },
    )
