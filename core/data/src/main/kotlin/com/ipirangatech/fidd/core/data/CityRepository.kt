package com.ipirangatech.fidd.core.data

import com.ipirangatech.fidd.core.model.CityRanking
import com.ipirangatech.fidd.core.model.CityRankingList
import com.ipirangatech.fidd.core.model.CityRankingSortBy

interface CityRepository {
    /** Top/bottom slice of the national city ranking, sorted by [sortBy] — never the whole list. */
    suspend fun fetchRanking(sortBy: CityRankingSortBy): Result<CityRankingList>

    /** The ranking row (with its own rank for [sortBy], wherever it falls) for whichever city is
     * nearest to (lat, lon) — see [com.ipirangatech.fidd.core.location.LocationProvider
     * .getCurrentLocation]. */
    suspend fun fetchNearestCity(
        lat: Double,
        lon: Double,
        sortBy: CityRankingSortBy,
    ): Result<CityRanking>
}
