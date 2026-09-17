package digital.tonima.noisnapista.core.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/** Every write call carries the anonymous, device-local reporter token as a header — see
 * [digital.tonima.noisnapista.core.data.ReporterIdentityProvider]. The server never stores it
 * raw, only a one-way hash, and it's the only way to prove "I reported this" for delete/votes. */
const val REPORTER_TOKEN_HEADER = "X-Reporter-Token"

interface PotholeService {
    @POST("api/v1/potholes")
    suspend fun submitReading(
        @Header(REPORTER_TOKEN_HEADER) reporterToken: String,
        @Body request: PotholeReadingRequestDto
    ): PotholeResponseDto

    @GET("api/v1/potholes")
    suspend fun getPotholes(
        @Query("minLat") minLat: Double? = null,
        @Query("minLon") minLon: Double? = null,
        @Query("maxLat") maxLat: Double? = null,
        @Query("maxLon") maxLon: Double? = null
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
