package com.ipirangatech.fidd.core.testing

import com.ipirangatech.fidd.core.data.CityRepository
import com.ipirangatech.fidd.core.model.CityRanking
import com.ipirangatech.fidd.core.model.CityRankingList
import com.ipirangatech.fidd.core.model.CityRankingSortBy

class FakeCityRepository : CityRepository {
    var rankingResult: suspend (CityRankingSortBy) -> Result<CityRankingList> =
        { Result.success(CityRankingList(totalCities = 0, top = emptyList(), bottom = emptyList())) }
    var nearestResult: suspend (Double, Double, CityRankingSortBy) -> Result<CityRanking> =
        { _, _, _ -> Result.failure(IllegalStateException("not stubbed")) }

    val rankingRequests = mutableListOf<CityRankingSortBy>()
    val nearestRequests = mutableListOf<Triple<Double, Double, CityRankingSortBy>>()

    override suspend fun fetchRanking(sortBy: CityRankingSortBy): Result<CityRankingList> {
        rankingRequests += sortBy
        return rankingResult(sortBy)
    }

    override suspend fun fetchNearestCity(lat: Double, lon: Double, sortBy: CityRankingSortBy): Result<CityRanking> {
        nearestRequests += Triple(lat, lon, sortBy)
        return nearestResult(lat, lon, sortBy)
    }
}
