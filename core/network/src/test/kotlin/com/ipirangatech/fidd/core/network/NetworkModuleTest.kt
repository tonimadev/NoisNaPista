package com.ipirangatech.fidd.core.network

import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.logging.HttpLoggingInterceptor
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/**
 * Exercises the Retrofit interfaces + Moshi DTOs against a fake server, pinning the wire contract
 * with the NoisNaPistaBackend (paths, query names, the reporter-token header, JSON field names).
 */
class NetworkModuleTest {
    private val server = MockWebServer()
    private lateinit var potholeService: PotholeService
    private lateinit var cityService: CityService

    private val potholeJson =
        """{"id":"srv-1","latitude":-23.5,"longitude":-46.6,"severity":18.5,"status":"PENDING",""" +
            """"readingCount":2,"distinctReporterCount":1,"createdAt":"2026-09-01T12:00:00Z"}"""
    private val cityJson =
        """{"ibgeCode":3550308,"name":"São Paulo","state":"SP","totalPotholes":10,"fixedPotholes":2,""" +
            """"recurrenceCount":1,"rank":4}"""

    @Before
    fun setUp() {
        server.start()
        val retrofit =
            Retrofit.Builder()
                .baseUrl(server.url("/"))
                .client(NetworkModule.provideOkHttpClient())
                .addConverterFactory(MoshiConverterFactory.create())
                .build()
        potholeService = NetworkModule.providePotholeService(retrofit)
        cityService = NetworkModule.provideCityService(retrofit)
    }

    @After
    fun tearDown() {
        server.close()
    }

    private fun enqueueJson(body: String) {
        server.enqueue(
            MockResponse.Builder().code(200).addHeader("Content-Type", "application/json").body(body).build(),
        )
    }

    @Test
    fun `okhttp client uses 10s timeouts and logs through an interceptor`() {
        val client = NetworkModule.provideOkHttpClient()

        assertEquals(10_000, client.connectTimeoutMillis)
        assertEquals(10_000, client.readTimeoutMillis)
        assertEquals(10_000, client.writeTimeoutMillis)
        assertTrue(client.interceptors.any { it is HttpLoggingInterceptor })
    }

    @Test
    fun `production retrofit points at the deployed backend`() {
        val retrofit = NetworkModule.provideRetrofit(NetworkModule.provideOkHttpClient())

        assertEquals("https", retrofit.baseUrl().scheme)
        assertTrue(retrofit.baseUrl().host.endsWith("fidd.com.br"))
    }

    @Test
    fun `submitReading posts the reading with the reporter token header`() =
        runTest {
            enqueueJson(potholeJson)

            val response =
                potholeService.submitReading(
                    "token-1",
                    PotholeReadingRequestDto("c1", -23.5, -46.6, 18.5f, 1000L),
                )

            val request = server.takeRequest()
            assertEquals("POST", request.method)
            assertEquals("/api/v1/potholes", request.url.encodedPath)
            assertEquals("token-1", request.headers[REPORTER_TOKEN_HEADER])
            val body = request.body!!.utf8()
            assertTrue(body, body.contains("\"clientId\":\"c1\"") && body.contains("\"timestamp\":1000"))
            assertEquals(
                PotholeResponseDto("srv-1", -23.5, -46.6, 18.5f, "PENDING", 2, 1, "2026-09-01T12:00:00Z"),
                response,
            )
        }

    @Test
    fun `submitReadings posts a batch and parses per-item results`() =
        runTest {
            enqueueJson(
                """[{"clientId":"c1","pothole":$potholeJson},{"clientId":"c2","error":"timestamp is too old"}]""",
            )

            val results =
                potholeService.submitReadings(
                    "token-1",
                    PotholeReadingBatchRequestDto(listOf(PotholeReadingRequestDto("c1", 0.0, 0.0, 16f, 1L))),
                )

            val request = server.takeRequest()
            assertEquals("/api/v1/potholes/batch", request.url.encodedPath)
            assertTrue(request.body!!.utf8().startsWith("{\"readings\":["))
            assertEquals("srv-1", results[0].pothole?.id)
            assertNull(results[0].error)
            assertNull(results[1].pothole)
            assertEquals("timestamp is too old", results[1].error)
            assertEquals(100, PotholeReadingBatchRequestDto.MAX_SIZE)
        }

    @Test
    fun `getPotholes sends the viewport as query params`() =
        runTest {
            enqueueJson("[$potholeJson]")

            val result =
                potholeService.getPotholes(
                    minLat = -24.0,
                    minLon = -47.0,
                    maxLat = -23.0,
                    maxLon = -46.0,
                    limit = 50,
                )

            val url = server.takeRequest().url
            assertEquals("/api/v1/potholes", url.encodedPath)
            assertEquals("-24.0", url.queryParameter("minLat"))
            assertEquals("-47.0", url.queryParameter("minLon"))
            assertEquals("-23.0", url.queryParameter("maxLat"))
            assertEquals("-46.0", url.queryParameter("maxLon"))
            assertEquals("50", url.queryParameter("limit"))
            assertEquals(1, result.size)
        }

    @Test
    fun `getNearbyPotholes omits optional params when not given`() =
        runTest {
            enqueueJson("[]")

            potholeService.getNearbyPotholes(latitude = -23.5, longitude = -46.6)

            val url = server.takeRequest().url
            assertEquals("/api/v1/potholes/nearby", url.encodedPath)
            assertEquals("-23.5", url.queryParameter("lat"))
            assertEquals("-46.6", url.queryParameter("lon"))
            assertNull(url.queryParameter("radiusMeters"))
            assertNull(url.queryParameter("limit"))
        }

    @Test
    fun `castFixVote and deletePothole address the pothole by id with the token`() =
        runTest {
            enqueueJson(potholeJson)
            server.enqueue(MockResponse.Builder().code(204).build())

            potholeService.castFixVote("srv-1", "token-1")
            potholeService.deletePothole("srv-1", "token-1")

            val vote = server.takeRequest()
            assertEquals("POST", vote.method)
            assertEquals("/api/v1/potholes/srv-1/fix-votes", vote.url.encodedPath)
            assertEquals("token-1", vote.headers[REPORTER_TOKEN_HEADER])
            val delete = server.takeRequest()
            assertEquals("DELETE", delete.method)
            assertEquals("/api/v1/potholes/srv-1", delete.url.encodedPath)
            assertEquals("token-1", delete.headers[REPORTER_TOKEN_HEADER])
        }

    @Test
    fun `city ranking and nearest city parse the backend shapes`() =
        runTest {
            enqueueJson("""{"totalCities":2,"top":[$cityJson],"bottom":[${cityJson.replace(",\"rank\":4", "")}]}""")
            enqueueJson(cityJson)

            val ranking = cityService.getRanking("fixed")
            val nearest = cityService.getNearestCity(-23.5, -46.6, "potholes")

            val rankingUrl = server.takeRequest().url
            assertEquals("/api/v1/cities/ranking", rankingUrl.encodedPath)
            assertEquals("fixed", rankingUrl.queryParameter("sortBy"))
            val nearestUrl = server.takeRequest().url
            assertEquals("/api/v1/cities/nearest", nearestUrl.encodedPath)
            assertEquals("-23.5", nearestUrl.queryParameter("lat"))
            assertEquals("potholes", nearestUrl.queryParameter("sortBy"))
            assertEquals(2, ranking.totalCities)
            assertEquals(4, ranking.top.single().rank)
            assertNull(ranking.bottom.single().rank)
            assertEquals(CityRankingResponseDto(3550308, "São Paulo", "SP", 10, 2, 1, 4), nearest)
        }
}
