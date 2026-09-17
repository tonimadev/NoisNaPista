package digital.tonima.noisnapista.core.data

import digital.tonima.noisnapista.core.model.CityRanking
import digital.tonima.noisnapista.core.model.CityRankingList
import digital.tonima.noisnapista.core.model.CityRankingSortBy

interface CityRepository {
    /** Top/bottom slice of the national city ranking, sorted by [sortBy] — never the whole list. */
    suspend fun fetchRanking(sortBy: CityRankingSortBy): Result<CityRankingList>

    /** The ranking row (with its own rank for [sortBy], wherever it falls) for whichever city is
     * nearest to (lat, lon) — see [digital.tonima.noisnapista.core.location.LocationProvider
     * .getCurrentLocation]. */
    suspend fun fetchNearestCity(lat: Double, lon: Double, sortBy: CityRankingSortBy): Result<CityRanking>
}
