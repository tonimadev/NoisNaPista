package com.ipirangatech.fidd.core.model

import kotlinx.serialization.Serializable

/** One city's row in the pothole ranking. [rank] is this city's 1-based position for whichever
 * [CityRankingSortBy] metric it was fetched with — null for a city with zero reported potholes
 * (excluded from the ranked set entirely, so it has no well-defined position). */
@Serializable
data class CityRanking(
    val ibgeCode: Int,
    val name: String,
    val state: String,
    val totalPotholes: Long,
    val fixedPotholes: Long,
    val recurrenceCount: Long,
    val rank: Int?
)

/** The ranking is never fetched in full (potentially thousands of cities) — only the best and
 * worst [top]/[bottom] slices, plus [totalCities] so the UI can say "posição X de [totalCities]"
 * even for a city that falls in neither slice (see the separately-fetched "minha cidade" row). */
@Serializable
data class CityRankingList(
    val totalCities: Int,
    val top: List<CityRanking>,
    val bottom: List<CityRanking>
)
