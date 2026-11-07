package com.salsalvia.subsurf.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

interface SubsurfApi {

    @GET("api/v1/health")
    suspend fun health(): HealthResponse

    @POST("api/v1/regions/validate")
    suspend fun validate(@Body body: RegionRequest): ValidationResponse

    @POST("api/v1/regions/catalog-search")
    suspend fun catalogSearch(@Body body: CatalogRequest): CatalogResponse

    @POST("api/v1/regions/terrain-indicators")
    suspend fun terrainIndicators(@Body body: RegionRequest): TerrainResponse
}

object ApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(150, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        })
        .build()

    private val cache = HashMap<String, SubsurfApi>()

    @Synchronized
    fun api(baseUrl: String): SubsurfApi {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return cache.getOrPut(normalized) {
            Retrofit.Builder()
                .baseUrl(normalized)
                .client(client)
                .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
                .build()
                .create(SubsurfApi::class.java)
        }
    }
}
