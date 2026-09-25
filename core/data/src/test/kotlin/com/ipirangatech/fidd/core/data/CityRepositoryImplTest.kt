package com.ipirangatech.fidd.core.data

import com.ipirangatech.fidd.core.model.CityRanking
import com.ipirangatech.fidd.core.model.CityRankingSortBy
import com.ipirangatech.fidd.core.network.CityRankingListResponseDto
import com.ipirangatech.fidd.core.network.CityRankingResponseDto
import com.ipirangatech.fidd.core.network.CityService
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CityRepositoryImplTest {
    private val row = CityRankingResponseDto(3550308, "São Paulo", "SP", 10, 2, 1, rank = 3)

    private class RecordingCityService(
        private val fail: Boolean = false,
        private val row: CityRankingResponseDto,
    ) : CityService {
        val sortParams = mutableListOf<String>()

        override suspend fun getRanking(sortBy: String): CityRankingListResponseDto {
            sortParams += sortBy
            if (fail) error("offline")
            return CityRankingListResponseDto(
                totalCities = 5,
                top = listOf(row),
                bottom = listOf(row.copy(rank = null)),
            )
        }

        override suspend fun getNearestCity(
            lat: Double,
            lon: Double,
            sortBy: String,
        ): CityRankingResponseDto {
            sortParams += sortBy
            if (fail) error("offline")
            return row
        }
    }

    @Test
    fun `each sort metric maps to the backend query value`() =
        runTest {
            val service = RecordingCityService(row = row)
            val repo = CityRepositoryImpl(service)

            CityRankingSortBy.entries.forEach { repo.fetchRanking(it) }

            assertEquals(listOf("potholes", "fixed", "recurrence"), service.sortParams)
        }

    @Test
    fun `ranking and nearest city are mapped to the domain model`() =
        runTest {
            val repo = CityRepositoryImpl(RecordingCityService(row = row))

            val ranking = repo.fetchRanking(CityRankingSortBy.POTHOLES).getOrThrow()
            val nearest = repo.fetchNearestCity(-23.5, -46.6, CityRankingSortBy.FIXED).getOrThrow()

            val expected = CityRanking(3550308, "São Paulo", "SP", 10, 2, 1, rank = 3)
            assertEquals(5, ranking.totalCities)
            assertEquals(listOf(expected), ranking.top)
            assertEquals(listOf(expected.copy(rank = null)), ranking.bottom)
            assertEquals(expected, nearest)
        }

    @Test
    fun `service errors become failed results`() =
        runTest {
            val repo = CityRepositoryImpl(RecordingCityService(fail = true, row = row))

            assertTrue(repo.fetchRanking(CityRankingSortBy.RECURRENCE).isFailure)
            assertTrue(repo.fetchNearestCity(0.0, 0.0, CityRankingSortBy.POTHOLES).isFailure)
        }
}
